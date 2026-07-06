package com.soymods.oreveil.obfuscation;

import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.transport.ObfuscationTransport;
import com.soymods.oreveil.obfuscation.transport.TransportFactory;
import com.soymods.oreveil.util.BlockNeighborhoods;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class NetworkObfuscationService {
    private final Logger logger;
    private OreveilConfig config;
    private final AuthoritativeWorldModel worldModel;
    private final ExposureService exposureService;
    private final HostBlockResolver hostBlockResolver;
    private final ObfuscationMetrics metrics;
    private ObfuscationTransport transport;
    private final Plugin plugin;
    private final Map<UUID, Set<RevealedBlockKey>> revealedExposedOresByPlayer = new ConcurrentHashMap<>();

    public NetworkObfuscationService(
        Plugin plugin,
        Logger logger,
        OreveilConfig config,
        AuthoritativeWorldModel worldModel,
        ExposureService exposureService
    ) {
        this.logger = logger;
        this.plugin = plugin;
        this.config = config;
        this.worldModel = worldModel;
        this.exposureService = exposureService;
        this.hostBlockResolver = new HostBlockResolver();
        this.metrics = new ObfuscationMetrics();
        this.transport = TransportFactory.create(plugin, logger, config, worldModel, metrics);
    }

    public void start() {
        transport.start(config, this::getClientVisibleMaterial);
        logger.info(
            "Network obfuscation service initialized with transport="
                + transport.name()
                + ", world model="
                + worldModel.getClass().getSimpleName()
                + ", exposure service="
                + exposureService.getClass().getSimpleName()
                + '.'
        );
    }

    public void stop() {
        transport.stop();
        logger.info("Network obfuscation service stopped.");
    }

    public void reload(OreveilConfig config) {
        boolean transportChanged = !this.config.transportMode().equalsIgnoreCase(config.transportMode());
        this.config = config;
        if (transportChanged) {
            transport.stop();
            transport = TransportFactory.create(plugin, logger, config, worldModel, metrics);
            transport.start(config, this::getClientVisibleMaterial);
        } else {
            transport.reload(config, this::getClientVisibleMaterial);
        }
        logger.info("Network obfuscation configuration reloaded.");
    }

    // -------------------------------------------------------------------------
    // Visibility resolution
    // -------------------------------------------------------------------------

    /**
     * Per-player visibility: buried ores stay hidden, while exposed cave ores can be
     * revealed from a larger gameplay-friendly radius.
     */
    public Material getClientVisibleMaterial(Block block, Player viewer) {
        if (!config.obfuscationEnabled()) {
            return block.getType();
        }

        Material saltMaterial = worldModel.getSaltMaterial(block);
        if (saltMaterial != null) {
            if (config.revealOnExposure() && exposureService.hasExposureFace(block)) {
                return block.getType();
            }
            return saltMaterial;
        }

        if (!worldModel.isProtectedOre(block.getType())) {
            return block.getType();
        }

        if (config.revealOnExposure() && exposureService.isLegitimatelyExposed(block)) {
            int radius = config.revealProximityBlocks();
            if (radius <= 0) {
                return block.getType();
            }
            if (viewer != null && isWithinDistance(viewer, block, radius)) {
                rememberRevealedOre(viewer, block);
                return block.getType();
            }
            if (viewer != null && config.keepRevealedExposedOres() && isKnownRevealedOre(viewer, block)) {
                return block.getType();
            }
        }

        if (viewer != null && !config.keepRevealedExposedOres()) {
            forgetRevealedOre(viewer, block);
        }
        return hostBlockResolver.resolve(block, config);
    }

    /** Player-unaware overload — used only for admin commands (inspect, status). */
    public Material getClientVisibleMaterial(Block block) {
        return getClientVisibleMaterial(block, null);
    }

    private static boolean isWithinDistance(Player player, Block block, int radius) {
        if (player.getWorld() != block.getWorld()) {
            return false;
        }
        Location loc = player.getEyeLocation();
        double dx = loc.getX() - (block.getX() + 0.5);
        double dy = loc.getY() - (block.getY() + 0.5);
        double dz = loc.getZ() - (block.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz <= (double) radius * radius;
    }

    // -------------------------------------------------------------------------
    // Block sync — all paths use per-player resolver
    // -------------------------------------------------------------------------

    public void syncBlock(Block block) {
        broadcastToNearbyPlayers(block);
    }

    public void syncAfterBreak(Block removedBlock) {
        for (BlockFace face : BlockFace.values()) {
            if (!face.isCartesian() || face == BlockFace.SELF) {
                continue;
            }

            Block neighbor = removedBlock.getRelative(face);
            Material saltMaterial = worldModel.getSaltMaterial(neighbor);
            boolean protectedOre = worldModel.isProtectedOre(neighbor.getType());
            if (!protectedOre && saltMaterial == null) {
                continue;
            }

            World world = neighbor.getWorld();
            Location loc = neighbor.getLocation();
            double maxDistSq = (double) config.liveSyncRadiusBlocks() * config.liveSyncRadiusBlocks();
            for (Player player : world.getPlayers()) {
                if (player.getLocation().distanceSquared(loc) <= maxDistSq) {
                    Material visible = getClientVisibleMaterialAfterBreak(neighbor, player, removedBlock, saltMaterial, protectedOre);
                    transport.syncBlockToPlayer(player, neighbor, (b, p) -> visible);
                }
            }
        }
    }

    public void syncRevealBoundary(Block origin) {
        syncProtectedOres(BlockNeighborhoods.cardinalNeighborhood(origin, 2));
    }

    public void syncRevealBoundaryNextTick(Block origin) {
        plugin.getServer().getScheduler().runTask(plugin, () -> syncRevealBoundary(origin));
    }

    public void syncProtectedOres(Iterable<Block> blocks) {
        for (Block block : blocks) {
            if (exposureService.isProtectedOre(block.getType()) || worldModel.getSaltMaterial(block) != null) {
                broadcastToNearbyPlayers(block);
            }
        }
    }

    public void syncBlockToPlayer(Player player, Block block) {
        transport.syncBlockToPlayer(player, block, this::getClientVisibleMaterial);
    }

    public void clearPlayerVisibility(Player player) {
        revealedExposedOresByPlayer.remove(player.getUniqueId());
    }

    public void syncNearbyExposedOres(Player player) {
        int radius = config.revealProximityBlocks();
        if (radius <= 0) {
            return;
        }

        syncPreviouslyRevealedOres(player);

        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null) {
            return;
        }

        int centerChunkX = origin.getBlockX() >> 4;
        int centerChunkZ = origin.getBlockZ() >> 4;
        int chunkRadius = Math.max(0, (radius + 15) >> 4);
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }
                for (Block block : worldModel.getProtectedOreBlocksInChunk(world.getChunkAt(chunkX, chunkZ))) {
                    if (exposureService.isLegitimatelyExposed(block) && isWithinDistance(player, block, radius)) {
                        transport.syncBlockToPlayer(player, block, this::getClientVisibleMaterial);
                    }
                }
            }
        }
    }

    private void syncPreviouslyRevealedOres(Player player) {
        Set<RevealedBlockKey> revealed = revealedExposedOresByPlayer.get(player.getUniqueId());
        if (revealed == null || revealed.isEmpty()) {
            return;
        }

        World world = player.getWorld();
        for (RevealedBlockKey key : List.copyOf(revealed)) {
            if (!key.worldId().equals(world.getUID())) {
                revealed.remove(key);
                continue;
            }
            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4) || !isWithinPlayerViewDistance(player, key)) {
                revealed.remove(key);
                continue;
            }
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            transport.syncBlockToPlayer(player, block, this::getClientVisibleMaterial);
        }
    }

    private void rememberRevealedOre(Player player, Block block) {
        revealedExposedOresByPlayer
            .computeIfAbsent(player.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet())
            .add(RevealedBlockKey.from(block));
    }

    private boolean isKnownRevealedOre(Player player, Block block) {
        Set<RevealedBlockKey> revealed = revealedExposedOresByPlayer.get(player.getUniqueId());
        return revealed != null && revealed.contains(RevealedBlockKey.from(block));
    }

    private void forgetRevealedOre(Player player, Block block) {
        Set<RevealedBlockKey> revealed = revealedExposedOresByPlayer.get(player.getUniqueId());
        if (revealed == null) {
            return;
        }
        revealed.remove(RevealedBlockKey.from(block));
        if (revealed.isEmpty()) {
            revealedExposedOresByPlayer.remove(player.getUniqueId());
        }
    }

    // -------------------------------------------------------------------------
    // Bulk resync (used after reload / ore toggle)
    // -------------------------------------------------------------------------

    public void resyncBlocks(List<Block> blocks) {
        for (Block block : blocks) {
            broadcastToNearbyPlayers(block);
        }
    }

    public void resyncNewSaltBlocks() {
        int viewDistance = plugin.getServer().getViewDistance();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            int chunkX = player.getLocation().getBlockX() >> 4;
            int chunkZ = player.getLocation().getBlockZ() >> 4;
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;
                    if (!world.isChunkLoaded(cx, cz)) {
                        continue;
                    }
                    for (Block saltBlock : worldModel.getSaltBlocksInChunk(world.getChunkAt(cx, cz))) {
                        transport.syncBlockToPlayer(player, saltBlock, this::getClientVisibleMaterial);
                    }
                }
            }
        }
    }

    public void resyncAllPlayers(Collection<Material> candidateMaterials) {
        Set<Material> candidates = candidateMaterials instanceof Set<Material> s ? s : Set.copyOf(candidateMaterials);
        int viewDistance = plugin.getServer().getViewDistance();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location loc = player.getLocation();
            World world = loc.getWorld();
            if (world == null) {
                continue;
            }
            int chunkX = loc.getBlockX() >> 4;
            int chunkZ = loc.getBlockZ() >> 4;
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    int cx = chunkX + dx;
                    int cz = chunkZ + dz;
                    if (!world.isChunkLoaded(cx, cz)) {
                        continue;
                    }
                    resyncChunkForPlayer(player, world.getChunkAt(cx, cz), candidates);
                }
            }
        }
    }

    private void resyncChunkForPlayer(Player player, Chunk chunk, Set<Material> candidates) {
        for (Block block : worldModel.getProtectedOreBlocksInChunk(chunk)) {
            if (candidates.contains(block.getType())) {
                transport.syncBlockToPlayer(player, block, this::getClientVisibleMaterial);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Misc
    // -------------------------------------------------------------------------

    public boolean handlesChunkDelivery() {
        return transport.handlesChunkDelivery();
    }

    public String transportName() {
        return transport.name();
    }

    public ObfuscationMetrics.Snapshot metricsSnapshot() {
        return metrics.snapshot();
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void broadcastToNearbyPlayers(Block block) {
        World world = block.getWorld();
        Location loc = block.getLocation();
        double maxDistSq = (double) config.liveSyncRadiusBlocks() * config.liveSyncRadiusBlocks();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= maxDistSq) {
                transport.syncBlockToPlayer(player, block, this::getClientVisibleMaterial);
            }
        }
    }

    private Material getClientVisibleMaterialAfterBreak(
        Block block,
        Player viewer,
        Block removedBlock,
        Material saltMaterial,
        boolean protectedOre
    ) {
        if (!config.obfuscationEnabled()) {
            return block.getType();
        }

        boolean exposedAfterBreak = config.revealOnExposure()
            && exposureService.hasExposureFaceAfterBreak(block, removedBlock);

        if (saltMaterial != null) {
            return exposedAfterBreak ? block.getType() : saltMaterial;
        }

        if (!protectedOre) {
            return block.getType();
        }

        if (exposedAfterBreak) {
            int radius = config.revealProximityBlocks();
            if (radius <= 0 || (viewer != null && isWithinDistance(viewer, block, radius))) {
                if (viewer != null) {
                    rememberRevealedOre(viewer, block);
                }
                return block.getType();
            }
            if (viewer != null && config.keepRevealedExposedOres() && isKnownRevealedOre(viewer, block)) {
                return block.getType();
            }
        }

        return hostBlockResolver.resolve(block, config);
    }

    private boolean isWithinPlayerViewDistance(Player player, RevealedBlockKey key) {
        Location loc = player.getLocation();
        int playerChunkX = loc.getBlockX() >> 4;
        int playerChunkZ = loc.getBlockZ() >> 4;
        int chunkRadius = plugin.getServer().getViewDistance() + 1;
        return Math.abs((key.x() >> 4) - playerChunkX) <= chunkRadius
            && Math.abs((key.z() >> 4) - playerChunkZ) <= chunkRadius;
    }

    private record RevealedBlockKey(UUID worldId, int x, int y, int z) {
        private static RevealedBlockKey from(Block block) {
            return new RevealedBlockKey(
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ()
            );
        }
    }
}
