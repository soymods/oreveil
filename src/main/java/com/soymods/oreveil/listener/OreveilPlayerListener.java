package com.soymods.oreveil.listener;

import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.obfuscation.scan.ChunkObfuscationPrimer;
import com.soymods.oreveil.util.OreveilScheduler;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
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

public final class OreveilPlayerListener implements Listener {
    private static final int PRIME_CHUNKS_PER_PUMP = 12;

    private final Plugin plugin;
    private final ChunkObfuscationPrimer primer;
    private final NetworkObfuscationService obfuscationService;
    private final Supplier<OreveilConfig> configSupplier;
    private final OreveilScheduler scheduler;
    private final Map<UUID, PrimeState> primeStates = new HashMap<>();

    public OreveilPlayerListener(
        Plugin plugin,
        ChunkObfuscationPrimer primer,
        NetworkObfuscationService obfuscationService,
        Supplier<OreveilConfig> configSupplier
    ) {
        this.plugin = plugin;
        this.primer = primer;
        this.obfuscationService = obfuscationService;
        this.configSupplier = configSupplier;
        this.scheduler = plugin instanceof com.soymods.oreveil.bootstrap.OreveilPlugin oreveilPlugin
            ? oreveilPlugin.scheduler()
            : new OreveilScheduler(plugin, plugin.getLogger());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
        if (primerHandlesChunkDelivery()) {
            return;
        }

        scheduleChunkPrime(event.getPlayer(), event.getChunk(), 1L);
        scheduleChunkPrime(event.getPlayer(), event.getChunk(), 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        schedulePrime(event.getPlayer(), event.getPlayer().getLocation(), 10L);
        schedulePrime(event.getPlayer(), event.getPlayer().getLocation(), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        schedulePrime(event.getPlayer(), event.getRespawnLocation(), 10L);
        schedulePrime(event.getPlayer(), event.getRespawnLocation(), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        schedulePrime(event.getPlayer(), event.getPlayer().getLocation(), 10L);
        schedulePrime(event.getPlayer(), event.getPlayer().getLocation(), 20L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null) {
            return;
        }

        if (crossesChunk(event.getFrom(), event.getTo()) || event.getFrom().getWorld() != event.getTo().getWorld()) {
            schedulePrime(event.getPlayer(), event.getTo(), 2L);
            schedulePrime(event.getPlayer(), event.getTo(), 10L);
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
        if (crossesChunk(event.getFrom(), event.getTo())) {
            scheduleChunkPrime(event.getPlayer(), event.getTo(), 1L);
            scheduleChunkPrime(event.getPlayer(), event.getTo(), 4L);
            int fromChunkX = event.getFrom().getBlockX() >> 4;
            int fromChunkZ = event.getFrom().getBlockZ() >> 4;
            scheduleExposedOreRefresh(event.getPlayer(), fromChunkX, fromChunkZ, 1L);
            scheduleExposedOreRefresh(event.getPlayer(), fromChunkX, fromChunkZ, 4L);
        }
    }

    private void scheduleExposedOreRefresh(Player player, int fromChunkX, int fromChunkZ, long delayTicks) {
        scheduler.runForLater(player, () -> {
            if (player.isOnline()) {
                obfuscationService.syncNewlyNearbyExposedOres(player, fromChunkX, fromChunkZ);
            }
        }, delayTicks);
    }

    private void scheduleChunkPrime(Player player, Chunk chunk, long delayTicks) {
        if (primerHandlesChunkDelivery()) {
            return;
        }

        World targetWorld = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        Location chunkCenter = new Location(targetWorld, (chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
        scheduler.runAtLater(chunkCenter, () -> {
            if (!player.isOnline() || player.getWorld() != targetWorld || !targetWorld.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }
            primer.primeChunk(player, targetWorld.getChunkAt(chunkX, chunkZ));
        }, delayTicks);
    }

    private void scheduleChunkPrime(Player player, Location location, long delayTicks) {
        if (primerHandlesChunkDelivery() || location.getWorld() == null) {
            return;
        }

        World targetWorld = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        scheduler.runAtLater(location, () -> {
            if (!player.isOnline() || player.getWorld() != targetWorld || !targetWorld.isChunkLoaded(chunkX, chunkZ)) {
                return;
            }
            primer.primeChunk(player, targetWorld.getChunkAt(chunkX, chunkZ));
        }, delayTicks);
    }

    private void schedulePrime(Player player, Location center, long delayTicks) {
        if (effectivePrimeRadius() <= 0) {
            return;
        }

        scheduler.runForLater(player, () -> {
            if (!player.isOnline() || center.getWorld() == null || player.getWorld() != center.getWorld()) {
                return;
            }

            if (primerHandlesChunkDelivery()) {
                return;
            }

            queueLoadedChunks(player, center);
        }, delayTicks);
    }

    private void queueLoadedChunks(Player player, Location center) {
        int radius = effectivePrimeRadius();
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        int chunkX = center.getBlockX() >> 4;
        int chunkZ = center.getBlockZ() >> 4;
        PrimeState state = primeStates.computeIfAbsent(player.getUniqueId(), ignored -> new PrimeState());
        state.clear();
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int targetChunkX = chunkX + dx;
                    int targetChunkZ = chunkZ + dz;
                    if (world.isChunkLoaded(targetChunkX, targetChunkZ)) {
                        state.add(new PrimeChunk(world.getUID(), targetChunkX, targetChunkZ, center.getY()));
                    }
                }
            }
        }
        pumpPrimeQueue(player);
    }

    private void pumpPrimeQueue(Player player) {
        PrimeState state = primeStates.get(player.getUniqueId());
        if (state == null || state.pumping()) {
            return;
        }

        state.setPumping(true);
        scheduler.runFor(player, () -> pumpPrimeQueueNow(player));
    }

    private void pumpPrimeQueueNow(Player player) {
        PrimeState state = primeStates.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        if (!player.isOnline()) {
            primeStates.remove(player.getUniqueId());
            return;
        }

        try {
            World world = player.getWorld();
            int processed = 0;
            while (processed < PRIME_CHUNKS_PER_PUMP) {
                PrimeChunk target = state.poll();
                if (target == null) {
                    primeStates.remove(player.getUniqueId());
                    return;
                }
                if (world.getUID().equals(target.worldId()) && world.isChunkLoaded(target.chunkX(), target.chunkZ())) {
                    schedulePrimeChunk(player, world, target);
                    processed++;
                }
            }
        } finally {
            state.setPumping(false);
        }

        if (!state.isEmpty()) {
            scheduler.runForLater(player, () -> pumpPrimeQueue(player), 1L);
        }
    }

    private void schedulePrimeChunk(Player player, World world, PrimeChunk target) {
        Location chunkCenter = new Location(world, (target.chunkX() << 4) + 8, target.y(), (target.chunkZ() << 4) + 8);
        scheduler.runAt(chunkCenter, () -> {
            if (!player.isOnline() || player.getWorld() != world || !world.isChunkLoaded(target.chunkX(), target.chunkZ())) {
                return;
            }
            Chunk chunk = world.getChunkAt(target.chunkX(), target.chunkZ());
            primer.primeChunk(player, chunk);
        });
    }

    private int effectivePrimeRadius() {
        OreveilConfig config = configSupplier.get();
        int configuredRadius = config.initialSyncChunkRadius();
        if (primerHandlesChunkDelivery()) {
            return configuredRadius;
        }
        return Math.max(configuredRadius, plugin.getServer().getViewDistance());
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

    private record PrimeChunk(UUID worldId, int chunkX, int chunkZ, double y) {
        PrimeKey key() {
            return new PrimeKey(worldId, chunkX, chunkZ);
        }
    }

    private record PrimeKey(UUID worldId, int chunkX, int chunkZ) {
    }

    private static final class PrimeState {
        private final ArrayDeque<PrimeChunk> queue = new ArrayDeque<>();
        private final Set<PrimeKey> queued = new HashSet<>();
        private boolean pumping;

        void clear() {
            queue.clear();
            queued.clear();
        }

        void add(PrimeChunk chunk) {
            if (queued.add(chunk.key())) {
                queue.add(chunk);
            }
        }

        PrimeChunk poll() {
            PrimeChunk chunk = queue.poll();
            if (chunk != null) {
                queued.remove(chunk.key());
            }
            return chunk;
        }

        boolean isEmpty() {
            return queue.isEmpty();
        }

        boolean pumping() {
            return pumping;
        }

        void setPumping(boolean pumping) {
            this.pumping = pumping;
        }
    }
}
