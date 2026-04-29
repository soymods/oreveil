package com.soymods.oreveil.obfuscation.transport;

import com.soymods.oreveil.config.OreveilConfig;
import java.util.function.BiFunction;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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
    private OreveilConfig config;

    public BlockUpdateSyncTransport(Plugin plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public void start(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver) {
        this.config = config;
        logger.info("Using block update sync transport.");
    }

    @Override
    public void stop() {
        this.config = null;
    }

    @Override
    public void reload(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver) {
        this.config = config;
    }

    @Override
    public void syncBlock(Block block, BiFunction<Block, Player, Material> materialResolver, int radiusBlocks) {
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

        Material visibleMaterial = materialResolver.apply(block, player);
        player.sendBlockChange(block.getLocation(), visibleMaterial.createBlockData());
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
