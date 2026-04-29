package com.soymods.oreveil.listener;

import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.obfuscation.scan.ChunkObfuscationPrimer;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;

public final class OreveilPlayerListener implements Listener {
    private final Plugin plugin;
    private final ChunkObfuscationPrimer primer;
    private final NetworkObfuscationService obfuscationService;
    private final java.util.function.Supplier<OreveilConfig> configSupplier;

    public OreveilPlayerListener(
        Plugin plugin,
        ChunkObfuscationPrimer primer,
        NetworkObfuscationService obfuscationService,
        java.util.function.Supplier<OreveilConfig> configSupplier
    ) {
        this.plugin = plugin;
        this.primer = primer;
        this.obfuscationService = obfuscationService;
        this.configSupplier = configSupplier;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        schedulePrime(event.getPlayer(), event.getPlayer().getLocation(), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        schedulePrime(event.getPlayer(), event.getRespawnLocation(), 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        schedulePrime(event.getPlayer(), event.getPlayer().getLocation(), 10L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }

        if (crossesChunk(event.getFrom(), event.getTo()) || event.getFrom().getWorld() != event.getTo().getWorld()) {
            schedulePrime(event.getPlayer(), event.getTo(), 2L);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            return;
        }
        if (sameBlock(event.getFrom(), event.getTo())) {
            return;
        }

        OreveilConfig config = configSupplier.get();
        if (config.revealProximityBlocks() <= 0) {
            return;
        }

        int step = Math.max(2, Math.min(4, config.revealProximityBlocks() / 2));
        if (Math.abs(event.getFrom().getBlockX() - event.getTo().getBlockX()) < step
            && Math.abs(event.getFrom().getBlockY() - event.getTo().getBlockY()) < step
            && Math.abs(event.getFrom().getBlockZ() - event.getTo().getBlockZ()) < step) {
            return;
        }

        obfuscationService.syncNearbyExposedOres(event.getPlayer());
    }

    private void schedulePrime(Player player, Location center, long delayTicks) {
        if (configSupplier.get().initialSyncChunkRadius() <= 0) {
            return;
        }

        BukkitScheduler scheduler = plugin.getServer().getScheduler();
        scheduler.runTaskLater(plugin, () -> {
            if (!player.isOnline() || center.getWorld() == null || player.getWorld() != center.getWorld()) {
                return;
            }

            if (primerHandlesChunkDelivery()) {
                return;
            }

            primeLoadedChunks(player, center);
        }, delayTicks);
    }

    private void primeLoadedChunks(Player player, Location center) {
        OreveilConfig config = configSupplier.get();
        int radius = config.initialSyncChunkRadius();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (!world.isChunkLoaded(chunkX + dx, chunkZ + dz)) {
                    continue;
                }

                Chunk chunk = world.getChunkAt(chunkX + dx, chunkZ + dz);
                primer.primeChunk(player, chunk);
            }
        }
    }

    private boolean crossesChunk(Location from, Location to) {
        return (from.getBlockX() >> 4) != (to.getBlockX() >> 4)
            || (from.getBlockZ() >> 4) != (to.getBlockZ() >> 4);
    }

    private boolean sameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
            && from.getBlockY() == to.getBlockY()
            && from.getBlockZ() == to.getBlockZ();
    }

    private boolean primerHandlesChunkDelivery() {
        return plugin instanceof com.soymods.oreveil.bootstrap.OreveilPlugin oreveilPlugin
            && oreveilPlugin.obfuscationService().handlesChunkDelivery();
    }
}
