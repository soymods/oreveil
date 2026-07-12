package com.soymods.oreveil.obfuscation;

import com.soymods.oreveil.compat.ServerCompatibility;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.transport.ObfuscationTransport;
import com.soymods.oreveil.util.BlockNeighborhoods;
import com.soymods.oreveil.util.OreveilScheduler;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
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
    private final ServerCompatibility compatibility;
    private final OreveilScheduler scheduler;

    public NetworkObfuscationService(
        Plugin plugin,
        Logger logger,
        OreveilConfig config,
        AuthoritativeWorldModel worldModel,
        ExposureService exposureService,
        ServerCompatibility compatibility
    ) {
        this.logger = logger;
        this.plugin = plugin;
        this.compatibility = compatibility;
        this.scheduler = plugin instanceof com.soymods.oreveil.bootstrap.OreveilPlugin oreveilPlugin
            ? oreveilPlugin.scheduler()
            : new OreveilScheduler(plugin, plugin.getLogger());
        this.config = config;
        this.worldModel = worldModel;
        this.exposureService = exposureService;
        this.hostBlockResolver = new HostBlockResolver();
        this.metrics = new ObfuscationMetrics();
        this.transport = compatibility.createTransport(plugin, logger, config, worldModel, metrics);
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
        if (!transport.handlesChunkDelivery() && config.initialSyncChunkRadius() == 0) {
            logger.info(
                "Chunk delivery is not intercepted and initial-sync-chunk-radius is 0; "
                    + "using fallback chunk priming around players to reduce initial ore leakage."
            );
        }
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
            transport = compatibility.createTransport(plugin, logger, config, worldModel, metrics);
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
     * Per-player visibility: buried ores stay hidden, while exposed cave ores are visible
     * in delivered chunks so caves do not pop based on short distance thresholds.
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

        if (config.revealOnExposure()
            && exposureService.isLegitimatelyExposed(block)
            && isWithinRevealRange(
                viewer,
                block,
                config.exposedOreRevealChunkRadius(),
                config.exposedOreRevealVerticalRadiusBlocks()
            )) {
            return block.getType();
        }

        return hostBlockResolver.resolve(block, config);
    }

    /** Player-unaware overload — used only for admin commands (inspect, status). */
    public Material getClientVisibleMaterial(Block block) {
        return getClientVisibleMaterial(block, null);
    }

    private static boolean isWithinRevealRange(Player player, Block block, int chunkRadius, int verticalRadiusBlocks) {
        if (player == null || player.getWorld() != block.getWorld() || chunkRadius < 0 || verticalRadiusBlocks < 0) {
            return false;
        }
        int playerChunkX = player.getLocation().getBlockX() >> 4;
        int playerChunkZ = player.getLocation().getBlockZ() >> 4;
        int blockChunkX = block.getX() >> 4;
        int blockChunkZ = block.getZ() >> 4;
        return Math.abs(playerChunkX - blockChunkX) <= chunkRadius
            && Math.abs(playerChunkZ - blockChunkZ) <= chunkRadius
            && Math.abs(player.getLocation().getBlockY() - block.getY()) <= verticalRadiusBlocks;
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

            broadcastToNearbyPlayers(neighbor, (block, player) ->
                getClientVisibleMaterialAfterBreak(block, player, removedBlock, saltMaterial, protectedOre)
            );
        }
    }

    public void syncRevealBoundary(Block origin) {
        syncProtectedOres(BlockNeighborhoods.cardinalNeighborhood(origin, 2));
    }

    public void syncRevealBoundaryNextTick(Block origin) {
        scheduler.runAt(origin.getLocation(), () -> syncRevealBoundary(origin));
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

    public void syncNewlyNearbyExposedOres(Player player, int fromChunkX, int fromChunkZ) {
        int radius = config.exposedOreRevealChunkRadius();
        if (radius <= 0) {
            return;
        }

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        int centerChunkX = player.getLocation().getBlockX() >> 4;
        int centerChunkZ = player.getLocation().getBlockZ() >> 4;
        int stepX = Integer.compare(centerChunkX, fromChunkX);
        int stepZ = Integer.compare(centerChunkZ, fromChunkZ);
        if (stepX != 0) {
            int edgeX = centerChunkX + stepX * radius;
            for (int dz = -radius; dz <= radius; dz++) {
                syncExposedOresInChunk(player, world, edgeX, centerChunkZ + dz);
            }
        }
        if (stepZ != 0) {
            int edgeZ = centerChunkZ + stepZ * radius;
            for (int dx = -radius; dx <= radius; dx++) {
                if (stepX != 0 && centerChunkX + dx == centerChunkX + stepX * radius) {
                    continue;
                }
                syncExposedOresInChunk(player, world, centerChunkX + dx, edgeZ);
            }
        }
    }

    private void syncExposedOresInChunk(Player player, World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        for (Block block : worldModel.getProtectedOreBlocksInChunk(world.getChunkAt(chunkX, chunkZ))) {
            if (exposureService.isLegitimatelyExposed(block)) {
                transport.syncBlockToPlayer(player, block, this::getClientVisibleMaterial);
            }
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
        if (scheduler.isFolia()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                scheduler.runFor(player, () -> resyncNewSaltBlocks(player));
            }
            return;
        }

        int viewDistance = plugin.getServer().getViewDistance();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            resyncNewSaltBlocks(player);
        }
    }

    public void resyncAllPlayers(Collection<Material> candidateMaterials) {
        Set<Material> candidates = candidateMaterials instanceof Set<Material> s ? s : Set.copyOf(candidateMaterials);
        if (scheduler.isFolia()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                scheduler.runFor(player, () -> resyncPlayerLoadedChunks(player, candidates));
            }
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            resyncPlayerLoadedChunks(player, candidates);
        }
    }

    private void resyncNewSaltBlocks(Player player) {
        int viewDistance = plugin.getServer().getViewDistance();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                int cx = chunkX + dx;
                int cz = chunkZ + dz;
                if (scheduler.isFolia()) {
                    scheduler.runAt(chunkCenter(world, cx, cz), () -> resyncSaltChunkForPlayer(player, world, cx, cz));
                } else {
                    resyncSaltChunkForPlayer(player, world, cx, cz);
                }
            }
        }
    }

    private void resyncSaltChunkForPlayer(Player player, World world, int chunkX, int chunkZ) {
        if (!player.isOnline() || player.getWorld() != world || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        for (Block saltBlock : worldModel.getSaltBlocksInChunk(world.getChunkAt(chunkX, chunkZ))) {
            transport.syncBlockToPlayer(player, saltBlock, this::getClientVisibleMaterial);
        }
    }

    private void resyncPlayerLoadedChunks(Player player, Set<Material> candidates) {
        int viewDistance = plugin.getServer().getViewDistance();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        for (int dx = -viewDistance; dx <= viewDistance; dx++) {
            for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                int cx = chunkX + dx;
                int cz = chunkZ + dz;
                if (scheduler.isFolia()) {
                    scheduler.runAt(chunkCenter(world, cx, cz), () -> resyncChunkForPlayer(player, world, cx, cz, candidates));
                } else {
                    resyncChunkForPlayer(player, world, cx, cz, candidates);
                }
            }
        }
    }

    private void resyncChunkForPlayer(Player player, World world, int chunkX, int chunkZ, Set<Material> candidates) {
        if (!player.isOnline() || player.getWorld() != world || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        resyncChunkForPlayer(player, world.getChunkAt(chunkX, chunkZ), candidates);
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
        broadcastToNearbyPlayers(block, this::getClientVisibleMaterial);
    }

    private void broadcastToNearbyPlayers(Block block, BiFunction<Block, Player, Material> materialResolver) {
        if (scheduler.isFolia()) {
            scheduler.runAt(block.getLocation(), () -> broadcastToNearbyPlayersFromRegion(block, materialResolver));
            return;
        }
        broadcastToNearbyPlayersFromRegion(block, materialResolver);
    }

    private void broadcastToNearbyPlayersFromRegion(
        Block block,
        BiFunction<Block, Player, Material> materialResolver
    ) {
        World world = block.getWorld();
        Location loc = block.getLocation();
        double maxDistSq = (double) config.liveSyncRadiusBlocks() * config.liveSyncRadiusBlocks();
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= maxDistSq) {
                transport.syncBlockToPlayer(player, block, materialResolver);
            }
        }
    }

    private static Location chunkCenter(World world, int chunkX, int chunkZ) {
        return new Location(world, (chunkX << 4) + 8, 0, (chunkZ << 4) + 8);
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
            return block.getType();
        }

        return hostBlockResolver.resolve(block, config);
    }
}
