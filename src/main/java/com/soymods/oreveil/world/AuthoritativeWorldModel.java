package com.soymods.oreveil.world;

import com.soymods.oreveil.config.OreveilConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.Plugin;

/**
 * Owns the server-authoritative record of hidden ore state.
 *
 * Real protected ores are hidden by the obfuscation pipeline; their fake material
 * is resolved deterministically by HostBlockResolver and needs no per-block storage.
 *
 * Salt blocks are positions that hold a host material (stone/deepslate/etc.) on the
 * server but are shown to clients as a fake ore, seeded deterministically per chunk
 * so X-ray users encounter convincing but non-existent ore signals.
 */
public final class AuthoritativeWorldModel {

    private static final EnumSet<Material> SALT_HOSTS = EnumSet.of(
        Material.STONE,
        Material.DEEPSLATE,
        Material.NETHERRACK,
        Material.END_STONE
    );

    private final Logger logger;
    private final Plugin plugin;
    private OreveilConfig config;

    // Per-chunk salt state: chunkKey → (packed local block pos → fake ore material)
    private final Map<ChunkKey, Map<Integer, Material>> saltCache = new HashMap<>();
    private final Map<ChunkKey, Map<Integer, Material>> protectedOreCache = new HashMap<>();
    private final Map<ChunkKey, Set<Integer>> exposedProtectedOreCache = new HashMap<>();
    private static final BlockFace[] CARDINAL_FACES = {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
    };

    private record ChunkKey(UUID worldId, int x, int z) {}

    public record CacheStats(
        int protectedOreChunks,
        int protectedOreBlocks,
        int saltChunks,
        int saltBlocks,
        Map<Material, Integer> saltBlocksByType
    ) {
    }

    private record SaltOreRule(Material material, World.Environment environment, int minY, int maxY, int weight) {}

    private static final List<SaltOreRule> SALT_ORE_RULES = List.of(
        new SaltOreRule(Material.COAL_ORE, World.Environment.NORMAL, 0, 192, 24),
        new SaltOreRule(Material.DEEPSLATE_COAL_ORE, World.Environment.NORMAL, -64, 16, 10),
        new SaltOreRule(Material.COPPER_ORE, World.Environment.NORMAL, -16, 112, 22),
        new SaltOreRule(Material.DEEPSLATE_COPPER_ORE, World.Environment.NORMAL, -64, 16, 8),
        new SaltOreRule(Material.IRON_ORE, World.Environment.NORMAL, -24, 96, 20),
        new SaltOreRule(Material.DEEPSLATE_IRON_ORE, World.Environment.NORMAL, -64, 16, 14),
        new SaltOreRule(Material.GOLD_ORE, World.Environment.NORMAL, -64, 32, 8),
        new SaltOreRule(Material.DEEPSLATE_GOLD_ORE, World.Environment.NORMAL, -64, 16, 8),
        new SaltOreRule(Material.REDSTONE_ORE, World.Environment.NORMAL, -64, 16, 7),
        new SaltOreRule(Material.DEEPSLATE_REDSTONE_ORE, World.Environment.NORMAL, -64, 16, 10),
        new SaltOreRule(Material.LAPIS_ORE, World.Environment.NORMAL, -64, 64, 5),
        new SaltOreRule(Material.DEEPSLATE_LAPIS_ORE, World.Environment.NORMAL, -64, 16, 6),
        new SaltOreRule(Material.DIAMOND_ORE, World.Environment.NORMAL, -64, 16, 2),
        new SaltOreRule(Material.DEEPSLATE_DIAMOND_ORE, World.Environment.NORMAL, -64, 16, 3),
        new SaltOreRule(Material.EMERALD_ORE, World.Environment.NORMAL, 16, 256, 1),
        new SaltOreRule(Material.DEEPSLATE_EMERALD_ORE, World.Environment.NORMAL, -64, 16, 1),
        new SaltOreRule(Material.NETHER_QUARTZ_ORE, World.Environment.NETHER, 10, 118, 28),
        new SaltOreRule(Material.NETHER_GOLD_ORE, World.Environment.NETHER, 10, 118, 16),
        new SaltOreRule(Material.ANCIENT_DEBRIS, World.Environment.NETHER, 8, 24, 1)
    );

    public AuthoritativeWorldModel(Plugin plugin, Logger logger, OreveilConfig config) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
    }

    public void start() {
        int indexed = seedAllLoadedChunks();
        logger.info(
            "Authoritative world model initialized; indexed "
                + indexed
                + " already-loaded chunks"
                + (config.saltedDistributionEnabled() ? " with salted distribution." : ".")
        );
    }

    public void stop() {
        saltCache.clear();
        protectedOreCache.clear();
        exposedProtectedOreCache.clear();
        logger.info("Authoritative world model stopped.");
    }

    public void reload(OreveilConfig newConfig) {
        saltCache.clear();
        protectedOreCache.clear();
        exposedProtectedOreCache.clear();
        this.config = newConfig;
        int indexed = seedAllLoadedChunks();
        logger.info(
            "Authoritative world model reloaded; indexed "
                + indexed
                + " chunks"
                + (newConfig.saltedDistributionEnabled() ? " with salted distribution." : ".")
        );
    }

    public boolean isProtectedOre(Material material) {
        return config.protectedOres().contains(material);
    }

    // -------------------------------------------------------------------------
    // Chunk lifecycle
    // -------------------------------------------------------------------------

    public void populateChunk(Chunk chunk) {
        ChunkKey key = keyOf(chunk);
        if (protectedOreCache.containsKey(key) && (!config.saltedDistributionEnabled() || saltCache.containsKey(key))) {
            return;
        }

        ChunkOreScan scan = scanProtectedOres(chunk);
        protectedOreCache.put(key, scan.ores());
        exposedProtectedOreCache.put(key, scan.exposed());
        if (!config.saltedDistributionEnabled()) {
            saltCache.remove(key);
            return;
        }

        Map<Integer, Material> salt = new HashMap<>();
        generateSalt(chunk, salt);
        saltCache.put(key, salt);
    }

    public void evictChunk(Chunk chunk) {
        ChunkKey key = keyOf(chunk);
        saltCache.remove(key);
        protectedOreCache.remove(key);
        exposedProtectedOreCache.remove(key);
    }

    // -------------------------------------------------------------------------
    // Block queries
    // -------------------------------------------------------------------------

    /** Returns the fake ore material this position should display as, or null if not a salt block. */
    public Material getSaltMaterial(Block block) {
        Map<Integer, Material> salt = saltCache.get(keyOf(block.getChunk()));
        if (salt == null) {
            return null;
        }
        return salt.get(packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF));
    }

    /** Returns all salt block positions in the given chunk (used for chunk priming on player join). */
    public List<Block> getSaltBlocksInChunk(Chunk chunk) {
        Map<Integer, Material> salt = saltCache.get(keyOf(chunk));
        if (salt == null || salt.isEmpty()) {
            return List.of();
        }
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        List<Block> blocks = new ArrayList<>(salt.size());
        for (int localKey : salt.keySet()) {
            int[] loc = unpackLocal(localKey);
            blocks.add(world.getBlockAt(baseX + loc[0], loc[1], baseZ + loc[2]));
        }
        return blocks;
    }

    /** Returns cached protected ore positions in the given chunk. */
    public List<Block> getProtectedOreBlocksInChunk(Chunk chunk) {
        Map<Integer, Material> ores = protectedOreCache.get(keyOf(chunk));
        if (ores == null || ores.isEmpty()) {
            return List.of();
        }

        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        List<Block> blocks = new ArrayList<>(ores.size());
        for (int localKey : ores.keySet()) {
            int[] loc = unpackLocal(localKey);
            blocks.add(world.getBlockAt(baseX + loc[0], loc[1], baseZ + loc[2]));
        }
        return blocks;
    }

    public Map<Integer, Material> getProtectedOreEntriesInChunk(UUID worldId, int chunkX, int chunkZ) {
        Map<Integer, Material> ores = protectedOreCache.get(new ChunkKey(worldId, chunkX, chunkZ));
        return ores == null || ores.isEmpty() ? Map.of() : Map.copyOf(ores);
    }

    public Map<Integer, Material> getBuriedProtectedOreEntriesInChunk(UUID worldId, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
        Map<Integer, Material> ores = protectedOreCache.get(key);
        if (ores == null || ores.isEmpty()) {
            return Map.of();
        }
        if (!config.revealOnExposure()) {
            return Map.copyOf(ores);
        }

        Set<Integer> exposed = exposedProtectedOreCache.get(key);
        if (exposed == null || exposed.isEmpty()) {
            return Map.copyOf(ores);
        }

        Map<Integer, Material> buried = new HashMap<>();
        for (Map.Entry<Integer, Material> entry : ores.entrySet()) {
            if (!exposed.contains(entry.getKey())) {
                buried.put(entry.getKey(), entry.getValue());
            }
        }
        return buried.isEmpty() ? Map.of() : Map.copyOf(buried);
    }

    public String describeChunkRewriteState(Block block) {
        ChunkKey key = keyOf(block.getChunk());
        int packed = packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
        Material saltMaterial = getSaltMaterial(block);
        if (saltMaterial != null) {
            return "fake salt: " + saltMaterial.name();
        }
        Map<Integer, Material> ores = protectedOreCache.get(key);
        if (ores == null || !ores.containsKey(packed)) {
            return "not cached";
        }
        if (!config.revealOnExposure()) {
            return "hidden: exposure reveal off";
        }
        Set<Integer> exposed = exposedProtectedOreCache.get(key);
        return exposed != null && exposed.contains(packed) ? "kept visible: exposed" : "hidden: buried";
    }

    public String describeBlockClassification(Block block) {
        Material saltMaterial = getSaltMaterial(block);
        if (saltMaterial != null) {
            return "fake " + saltMaterial.name() + " over " + block.getType().name();
        }

        if (!isProtectedOre(block.getType())) {
            return "normal " + block.getType().name();
        }

        ChunkKey key = keyOf(block.getChunk());
        int packed = packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
        Set<Integer> exposed = exposedProtectedOreCache.get(key);
        return exposed != null && exposed.contains(packed)
            ? "real exposed " + block.getType().name()
            : "real buried " + block.getType().name();
    }

    public Map<Integer, Material> getSaltEntriesInChunk(UUID worldId, int chunkX, int chunkZ) {
        Map<Integer, Material> salt = saltCache.get(new ChunkKey(worldId, chunkX, chunkZ));
        return salt == null || salt.isEmpty() ? Map.of() : Map.copyOf(salt);
    }

    /** Returns all salt blocks across all currently cached chunks (used for pre-reload drain). */
    public List<Block> collectAllSaltBlocks() {
        List<Block> result = new ArrayList<>();
        for (Map.Entry<ChunkKey, Map<Integer, Material>> entry : saltCache.entrySet()) {
            ChunkKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.worldId());
            if (world == null) {
                continue;
            }
            int baseX = key.x() << 4;
            int baseZ = key.z() << 4;
            for (int localKey : entry.getValue().keySet()) {
                int[] loc = unpackLocal(localKey);
                result.add(world.getBlockAt(baseX + loc[0], loc[1], baseZ + loc[2]));
            }
        }
        return result;
    }

    public CacheStats cacheStats() {
        int cachedProtectedOres = 0;
        for (Map<Integer, Material> ores : protectedOreCache.values()) {
            cachedProtectedOres += ores.size();
        }

        int cachedSaltBlocks = 0;
        Map<Material, Integer> saltByType = new java.util.EnumMap<>(Material.class);
        for (Map<Integer, Material> salt : saltCache.values()) {
            cachedSaltBlocks += salt.size();
            for (Material material : salt.values()) {
                saltByType.merge(material, 1, Integer::sum);
            }
        }

        return new CacheStats(
            protectedOreCache.size(),
            cachedProtectedOres,
            saltCache.size(),
            cachedSaltBlocks,
            Map.copyOf(saltByType)
        );
    }

    /** Removes a block from all caches after the server removes it. */
    public void invalidateBlock(Block block) {
        removeCachedBlock(block);
    }

    /** Updates cached state after a block has been placed or changed in-place. */
    public void refreshBlock(Block block) {
        ChunkKey key = keyOf(block.getChunk());
        int packed = packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
        Map<Integer, Material> salt = saltCache.get(keyOf(block.getChunk()));
        if (salt != null) {
            salt.remove(packed);
        }

        Map<Integer, Material> ores = protectedOreCache.computeIfAbsent(key, ignored -> new HashMap<>());
        if (isProtectedOre(block.getType())) {
            ores.put(packed, block.getType());
        } else {
            ores.remove(packed);
        }
        refreshExposure(block);
        for (BlockFace face : CARDINAL_FACES) {
            refreshExposure(block.getRelative(face));
        }
    }

    // -------------------------------------------------------------------------
    // Salt generation
    // -------------------------------------------------------------------------

    private void generateSalt(Chunk chunk, Map<Integer, Material> salt) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        World.Environment env = world.getEnvironment();

        List<SaltOreRule> candidates = saltOreCandidates(env);
        if (candidates.isEmpty()) {
            return;
        }

        // Deterministic seed derived from world seed, chunk coordinates, and a server-private secret.
        long seed = world.getSeed()
            ^ ((long) chunk.getX() * 341873128712L)
            ^ ((long) chunk.getZ() * 132897987541L)
            ^ Long.rotateLeft(config.saltSecret(), 17);
        Random rng = new Random(seed);

        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        int attempts = config.saltDensity();

        for (int i = 0; i < attempts; i++) {
            int lx = rng.nextInt(16);
            int y = minY + rng.nextInt(maxY - minY);
            int lz = rng.nextInt(16);

            Block block = world.getBlockAt(baseX + lx, y, baseZ + lz);
            Material type = block.getType();

            if (!SALT_HOSTS.contains(type)) {
                continue;
            }
            if (config.protectedOres().contains(type) || isExposed(block)) {
                continue;
            }

            Material saltOre = pickSaltOre(type, y, candidates, rng);
            if (saltOre != null) {
                salt.put(packLocal(lx, y, lz), saltOre);
            }
        }
    }

    private List<SaltOreRule> saltOreCandidates(World.Environment env) {
        return SALT_ORE_RULES.stream()
            .filter(rule -> rule.environment() == env)
            .filter(rule -> config.protectedOres().contains(rule.material()))
            .toList();
    }

    private static Material pickSaltOre(Material host, int y, List<SaltOreRule> candidates, Random rng) {
        List<SaltOreRule> matching = candidates.stream()
            .filter(rule -> y >= rule.minY() && y <= rule.maxY())
            .filter(rule -> matchesHost(rule.material(), host))
            .toList();
        if (matching.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        for (SaltOreRule rule : matching) {
            totalWeight += rule.weight();
        }
        int roll = rng.nextInt(Math.max(1, totalWeight));
        for (SaltOreRule rule : matching) {
            roll -= rule.weight();
            if (roll < 0) {
                return rule.material();
            }
        }
        return matching.getLast().material();
    }

    private static boolean matchesHost(Material ore, Material host) {
        String name = ore.name();
        return switch (host) {
            case DEEPSLATE -> name.startsWith("DEEPSLATE_");
            case NETHERRACK -> name.startsWith("NETHER_") || name.equals("ANCIENT_DEBRIS");
            case END_STONE -> !name.startsWith("DEEPSLATE_") && !name.startsWith("NETHER_") && !name.equals("ANCIENT_DEBRIS");
            default -> !name.startsWith("DEEPSLATE_") && !name.startsWith("NETHER_") && !name.equals("ANCIENT_DEBRIS");
        };
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int seedAllLoadedChunks() {
        int count = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                populateChunk(chunk);
                count++;
            }
        }
        return count;
    }

    private static ChunkKey keyOf(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    private ChunkOreScan scanProtectedOres(Chunk chunk) {
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        Map<Integer, Material> ores = new HashMap<>();
        Set<Integer> exposed = new java.util.HashSet<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    Material type = block.getType();
                    if (isProtectedOre(type)) {
                        int packed = packLocal(x, y, z);
                        ores.put(packed, type);
                        if (isExposed(block)) {
                            exposed.add(packed);
                        }
                    }
                }
            }
        }

        return new ChunkOreScan(ores, exposed);
    }

    private void refreshExposure(Block block) {
        ChunkKey key = keyOf(block.getChunk());
        int packed = packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
        Set<Integer> exposed = exposedProtectedOreCache.computeIfAbsent(key, ignored -> new java.util.HashSet<>());
        if (isProtectedOre(block.getType()) && isExposed(block)) {
            exposed.add(packed);
        } else {
            exposed.remove(packed);
        }
    }

    private boolean isExposed(Block block) {
        for (BlockFace face : CARDINAL_FACES) {
            Material neighbor = safeNeighborType(block, face);
            if (neighbor == null) {
                continue;
            }
            if (config.revealAdjacentMaterials().contains(neighbor)
                || config.revealTransparentMaterials().contains(neighbor)
                || (config.revealNextToNonOccludingBlocks() && neighbor.isBlock() && !neighbor.isOccluding())) {
                return true;
            }
        }
        return false;
    }

    private Material safeNeighborType(Block block, BlockFace face) {
        World world = block.getWorld();
        int x = block.getX() + face.getModX();
        int y = block.getY() + face.getModY();
        int z = block.getZ() + face.getModZ();
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return Material.AIR;
        }
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return world.getBlockAt(x, y, z).getType();
    }

    private void removeCachedBlock(Block block) {
        ChunkKey key = keyOf(block.getChunk());
        int packed = packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
        Map<Integer, Material> salt = saltCache.get(key);
        if (salt != null) {
            salt.remove(packed);
        }
        Map<Integer, Material> ores = protectedOreCache.get(key);
        if (ores != null) {
            ores.remove(packed);
        }
        Set<Integer> exposed = exposedProtectedOreCache.get(key);
        if (exposed != null) {
            exposed.remove(packed);
        }
    }

    // lx/lz: 0–15 (4 bits each), y offset by 2048 to handle negative heights
    private static int packLocal(int lx, int y, int lz) {
        return (lx & 0xF) | ((lz & 0xF) << 4) | ((y + 2048) << 8);
    }

    private static int[] unpackLocal(int packed) {
        return new int[]{packed & 0xF, (packed >> 8) - 2048, (packed >> 4) & 0xF};
    }

    private record ChunkOreScan(Map<Integer, Material> ores, Set<Integer> exposed) {}
}
