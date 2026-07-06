package com.soymods.oreveil.obfuscation.transport;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedLevelChunkData;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.obfuscation.ObfuscationMetrics;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
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
 * ProtocolLib-backed transport that rewrites direct block updates and primes
 * delivered chunks with per-player correction after send.
 */
public final class ProtocolLibTransport implements ObfuscationTransport {
    private final BlockUpdateSyncTransport fallback;
    private final Plugin plugin;
    private final Logger logger;
    private final AuthoritativeWorldModel worldModel;
    private final ObfuscationMetrics metrics;
    private final ChunkPacketBlockRewriter chunkRewriter;
    private ProtocolManager protocolManager;
    private OreveilConfig config;
    private BiFunction<Block, Player, Material> materialResolver;
    private PacketAdapter packetAdapter;
    private boolean warnedMultiBlockRewriteFailure;
    private boolean warnedChunkRewriteFailure;

    public ProtocolLibTransport(
        Plugin plugin,
        Logger logger,
        AuthoritativeWorldModel worldModel,
        ObfuscationMetrics metrics
    ) {
        this.plugin = plugin;
        this.fallback = new BlockUpdateSyncTransport(plugin, logger, metrics);
        this.logger = logger;
        this.worldModel = worldModel;
        this.metrics = metrics;
        this.chunkRewriter = new ChunkPacketBlockRewriter(worldModel);
    }

    @Override
    public void start(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver) {
        this.config = config;
        this.materialResolver = materialResolver;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
        fallback.start(config, materialResolver);
        registerPacketListener();
        logger.info("ProtocolLib transport selected. Rewriting outbound block updates and priming chunk delivery.");
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
        metrics.recordBlockChangePacketRewrite();
    }

    private void rewriteMultiBlockChange(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet.getMultiBlockChangeInfoArrays().size() <= 0) {
            return;
        }

        try {
            MultiBlockChangeInfo[] changes = packet.getMultiBlockChangeInfoArrays().read(0);
            if (changes == null || changes.length == 0) {
                return;
            }

            Player player = event.getPlayer();
            World world = player.getWorld();
            int rewrittenEntries = 0;
            for (MultiBlockChangeInfo change : changes) {
                if (change == null) {
                    continue;
                }

                Block block = change.getLocation(world).getBlock();
                Material visibleMaterial = materialResolver.apply(block, player);
                if (visibleMaterial != change.getData().getType()) {
                    change.setData(WrappedBlockData.createData(visibleMaterial));
                    rewrittenEntries++;
                }
            }
            packet.getMultiBlockChangeInfoArrays().write(0, changes);
            metrics.recordMultiBlockPacketRewrite(rewrittenEntries);
        } catch (RuntimeException exception) {
            metrics.recordMultiBlockRewriteFailure();
            if (!warnedMultiBlockRewriteFailure) {
                warnedMultiBlockRewriteFailure = true;
                logger.warning("Could not rewrite a multi-block change packet: " + exception.getMessage());
            }
        }
    }

    private void handleChunkPacket(PacketEvent event) {
        rewriteChunkPacket(event);
        primeChunkAfterPacket(event);
    }

    private void rewriteChunkPacket(PacketEvent event) {
        PacketContainer packet = event.getPacket();
        if (packet.getIntegers().size() < 2 || packet.getLevelChunkData().size() <= 0) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();
        int chunkX = packet.getIntegers().read(0);
        int chunkZ = packet.getIntegers().read(1);
        WrappedLevelChunkData.ChunkData chunkData = packet.getLevelChunkData().read(0);
        if (chunkData == null || chunkData.getBuffer() == null) {
            return;
        }

        ChunkPacketBlockRewriter.RewriteResult result = chunkRewriter.rewrite(
            chunkData.getBuffer(),
            world.getUID(),
            world.getEnvironment(),
            chunkX,
            chunkZ,
            world.getMinHeight(),
            world.getMaxHeight(),
            config
        );

        if (result.failed()) {
            metrics.recordChunkRewriteFailure();
            if (!warnedChunkRewriteFailure) {
                warnedChunkRewriteFailure = true;
                logger.warning("Could not rewrite a chunk packet: " + result.failure().getMessage());
            }
            return;
        }

        if (result.changed()) {
            chunkData.setBuffer(result.bytes());
            packet.getLevelChunkData().write(0, chunkData);
            metrics.recordChunkPacketRewrite(result.rewrittenEntries());
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
            metrics.recordChunkPacketPrimed();
        });
    }

    private void primeChunkToPlayer(Player player, Chunk chunk) {
        for (Block block : worldModel.getProtectedOreBlocksInChunk(chunk)) {
            Material visibleMaterial = materialResolver.apply(block, player);
            if (visibleMaterial != block.getType()) {
                fallback.syncBlockToPlayer(player, block, (b, p) -> visibleMaterial);
                metrics.recordChunkPrimeCorrection();
            }
        }

        for (Block block : worldModel.getSaltBlocksInChunk(chunk)) {
            Material visibleMaterial = materialResolver.apply(block, player);
            if (visibleMaterial != block.getType()) {
                fallback.syncBlockToPlayer(player, block, (b, p) -> visibleMaterial);
                metrics.recordChunkPrimeCorrection();
            }
        }
    }
}
