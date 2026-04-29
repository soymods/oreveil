package com.soymods.oreveil.obfuscation.transport;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.comphenix.protocol.wrappers.WrappedLevelChunkData;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.obfuscation.chunk.ChunkPacketTransformer;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Placeholder for the real packet-backed transport.
 * The adapter boundary is in place so ProtocolLib interception can be added
 * without changing the rest of Oreveil's visibility model.
 */
public final class ProtocolLibTransport implements ObfuscationTransport {
    private final BlockUpdateSyncTransport fallback;
    private final Plugin plugin;
    private final Logger logger;
    private final ChunkPacketTransformer chunkPacketTransformer;
    private ProtocolManager protocolManager;
    private OreveilConfig config;
    private BiFunction<Block, Player, Material> materialResolver;
    private PacketAdapter packetAdapter;
    private boolean loggedUnsupportedMultiBlockChange;

    public ProtocolLibTransport(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.fallback = new BlockUpdateSyncTransport(plugin, logger);
        this.logger = logger;
        this.chunkPacketTransformer = new ChunkPacketTransformer(logger);
    }

    @Override
    public void start(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver) {
        this.config = config;
        this.materialResolver = materialResolver;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        fallback.start(config, materialResolver);
        registerPacketListener();
        logger.info(
            "ProtocolLib transport selected. Rewriting outbound block updates. "
                + (config.experimentalChunkRewrite()
                    ? "Experimental chunk rewrite is enabled."
                    : "Initial chunk rewrite is disabled; using post-send priming for chunk delivery.")
        );
    }

    @Override
    public void stop() {
        unregisterPacketListener();
        fallback.stop();
        this.config = null;
        this.materialResolver = null;
        this.protocolManager = null;
    }

    @Override
    public void reload(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver) {
        this.config = config;
        this.materialResolver = materialResolver;
        fallback.reload(config, materialResolver);
    }

    @Override
    public void syncBlock(Block block, BiFunction<Block, Player, Material> materialResolver, int radiusBlocks) {
        fallback.syncBlock(block, materialResolver, radiusBlocks);
    }

    @Override
    public void syncBlockToPlayer(Player player, Block block, BiFunction<Block, Player, Material> materialResolver) {
        fallback.syncBlockToPlayer(player, block, materialResolver);
    }

    @Override
    public boolean handlesChunkDelivery() {
        return true;
    }

    @Override
    public String name() {
        return "PROTOCOLLIB";
    }

    private void registerPacketListener() {
        if (protocolManager == null) {
            return;
        }

        packetAdapter = new PacketAdapter(
            plugin,
            ListenerPriority.NORMAL,
            PacketType.Play.Server.BLOCK_CHANGE,
            PacketType.Play.Server.MULTI_BLOCK_CHANGE,
            PacketType.Play.Server.MAP_CHUNK
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                if (config == null || materialResolver == null) {
                    return;
                }

                PacketType packetType = event.getPacketType();
                if (packetType == PacketType.Play.Server.BLOCK_CHANGE) {
                    rewriteBlockChange(event);
                    return;
                }

                if (packetType == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
                    rewriteMultiBlockChange(event);
                    return;
                }

                if (packetType == PacketType.Play.Server.MAP_CHUNK) {
                    handleChunkPacket(event);
                }
            }
        };

        protocolManager.addPacketListener(packetAdapter);
    }

    private void unregisterPacketListener() {
        if (protocolManager != null && packetAdapter != null) {
            protocolManager.removePacketListener(packetAdapter);
        }
        packetAdapter = null;
    }

    private void rewriteBlockChange(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        Player player = event.getPlayer();
        BlockPosition position = packet.getBlockPositionModifier().read(0);
        Block block = player.getWorld().getBlockAt(position.getX(), position.getY(), position.getZ());
        Material visibleMaterial = materialResolver.apply(block, player);
        packet.getBlockData().write(0, WrappedBlockData.createData(visibleMaterial));
    }

    private void rewriteMultiBlockChange(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet.getMultiBlockChangeInfoArrays().size() == 0) {
            logUnsupportedMultiBlockChangeOnce();
            return;
        }

        MultiBlockChangeInfo[] changes;
        try {
            changes = packet.getMultiBlockChangeInfoArrays().read(0);
        } catch (FieldAccessException exception) {
            logUnsupportedMultiBlockChangeOnce();
            return;
        }
        if (changes == null || changes.length == 0) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();
        for (MultiBlockChangeInfo change : changes) {
            Block block = world.getBlockAt(change.getLocation(world));
            Material visibleMaterial = materialResolver.apply(block, player);
            change.setData(WrappedBlockData.createData(visibleMaterial));
        }
        packet.getMultiBlockChangeInfoArrays().write(0, changes);
    }

    private void logUnsupportedMultiBlockChangeOnce() {
        if (loggedUnsupportedMultiBlockChange) {
            return;
        }

        loggedUnsupportedMultiBlockChange = true;
        logger.warning(
            "Skipping an unsupported MULTI_BLOCK_CHANGE packet layout on this server build. "
                + "Oreveil will continue using BLOCK_CHANGE rewriting and fallback sync for those updates."
        );
    }

    private void handleChunkPacket(PacketEvent event) {
        if (config.experimentalChunkRewrite()) {
            rewriteChunkPacket(event);
            return;
        }

        primeChunkAfterPacket(event);
    }

    private void rewriteChunkPacket(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet.getIntegers().size() < 2 || packet.getLevelChunkData().size() == 0) {
            return;
        }

        int chunkX = packet.getIntegers().read(0);
        int chunkZ = packet.getIntegers().read(1);

        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        WrappedLevelChunkData.ChunkData chunkData = packet.getLevelChunkData().read(0);
        // Use a player-bound resolver for the chunk rewrite so proximity rules are respected
        boolean rewritten = chunkPacketTransformer.rewriteChunkData(
            world, chunkX, chunkZ, chunkData, block -> materialResolver.apply(block, player)
        );
        if (rewritten) {
            packet.getLevelChunkData().write(0, chunkData);
        }
    }

    private void primeChunkAfterPacket(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet.getIntegers().size() < 2) {
            return;
        }

        int chunkX = packet.getIntegers().read(0);
        int chunkZ = packet.getIntegers().read(1);
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            World world = player.getWorld();
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }

            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            primeChunkToPlayer(player, chunk);
        });
    }

    private void primeChunkToPlayer(Player player, Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    if (block.getType().isAir()) {
                        continue;
                    }

                    Material visibleMaterial = materialResolver.apply(block, player);
                    if (visibleMaterial != block.getType()) {
                        fallback.syncBlockToPlayer(player, block, (b, p) -> visibleMaterial);
                    }
                }
            }
        }
    }
}
