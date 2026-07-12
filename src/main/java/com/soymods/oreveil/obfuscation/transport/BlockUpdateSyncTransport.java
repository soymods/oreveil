package com.soymods.oreveil.obfuscation.transport;

import com.soymods.oreveil.compat.ServerCompatibility;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.obfuscation.ObfuscationMetrics;
import com.soymods.oreveil.util.OreveilScheduler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Fallback transport that pushes corrected block states with Bukkit APIs.
 * This does not rewrite chunk packets, but it gives Oreveil a live sync path
 * for block exposure changes until a packet adapter is introduced.
 */
public final class BlockUpdateSyncTransport implements ObfuscationTransport {
    private final Plugin plugin;
    private final Logger logger;
    private final ObfuscationMetrics metrics;
    private final ServerCompatibility compatibility;
    private final OreveilScheduler scheduler;
    private final Map<Material, BlockData> blockDataCache = new ConcurrentHashMap<>();
    private OreveilConfig config;

    public BlockUpdateSyncTransport(Plugin plugin, Logger logger, ObfuscationMetrics metrics, ServerCompatibility compatibility) {
        this.plugin = plugin;
        this.logger = logger;
        this.metrics = metrics;
        this.compatibility = compatibility;
        this.scheduler = plugin instanceof com.soymods.oreveil.bootstrap.OreveilPlugin oreveilPlugin
            ? oreveilPlugin.scheduler()
            : new OreveilScheduler(plugin, plugin.getLogger());
    }

    @Override
    public void start(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver) {
        this.config = config;
        prewarmBlockData(config);
        logger.info("Using block update sync transport.");
    }

    @Override
    public void stop() {
        this.config = null;
    }

    @Override
    public void reload(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver) {
        this.config = config;
        prewarmBlockData(config);
    }

    @Override
    public void syncBlock(Block block, BiFunction<Block, Player, Material> materialResolver, int radiusBlocks) {
        if (config == null || !plugin.isEnabled()) {
            return;
        }

        if (scheduler.isFolia()) {
            scheduler.runAt(block.getLocation(), () -> syncBlockFromRegion(block, materialResolver, radiusBlocks));
            return;
        }

        syncBlockFromRegion(block, materialResolver, radiusBlocks);
    }

    private void syncBlockFromRegion(Block block, BiFunction<Block, Player, Material> materialResolver, int radiusBlocks) {
        if (config == null || !plugin.isEnabled()) {
            return;
        }

        World world = block.getWorld();
        Location blockLocation = block.getLocation();
        double maxDistanceSquared = (double) radiusBlocks * radiusBlocks;

        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(blockLocation) <= maxDistanceSquared) {
                syncBlockToPlayer(player, block, materialResolver);
            }
        }
    }

    @Override
    public void syncBlockToPlayer(Player player, Block block, BiFunction<Block, Player, Material> materialResolver) {
        if (config == null || !player.isOnline() || player.getWorld() != block.getWorld()) {
            return;
        }

        if (scheduler.isFolia()) {
            scheduler.runAt(block.getLocation(), () -> syncBlockToPlayerFromRegion(player, block, materialResolver));
            return;
        }

        Material visibleMaterial = materialResolver.apply(block, player);
        compatibility.sendBlockChange(player, block, visibleMaterial);
        metrics.recordSyntheticBlockChange();
    }

    private void syncBlockToPlayerFromRegion(Player player, Block block, BiFunction<Block, Player, Material> materialResolver) {
        if (config == null || !player.isOnline() || player.getWorld() != block.getWorld()) {
            return;
        }

        Location blockLocation = block.getLocation();
        Material visibleMaterial = materialResolver.apply(block, player);
        BlockData visibleBlockData = blockData(visibleMaterial);
        scheduler.runFor(player, () -> {
            if (config == null || !player.isOnline() || player.getWorld() != blockLocation.getWorld()) {
                return;
            }
            player.sendBlockChange(blockLocation, visibleBlockData);
            metrics.recordSyntheticBlockChange();
        });
    }

    private void prewarmBlockData(OreveilConfig config) {
        blockData(Material.STONE);
        for (Material material : config.dimensionDefaults().values()) {
            blockData(material);
        }
        for (Material material : config.oreOverrides().values()) {
            blockData(material);
        }
        for (Material material : config.protectedOres()) {
            blockData(material);
        }
    }

    private BlockData blockData(Material material) {
        return blockDataCache.computeIfAbsent(material, Material::createBlockData);
    }

    @Override
    public boolean handlesChunkDelivery() {
        return false;
    }

    @Override
    public String name() {
        return config == null ? "BLOCK_UPDATE_SYNC" : config.transportMode();
    }
}
