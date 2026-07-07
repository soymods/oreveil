package com.soymods.oreveil.world;

import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.config.XrayProfile;
import com.soymods.oreveil.config.XrayProfile.OreRarity;
import java.util.ArrayList;
import java.util.Arrays;
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

    // Per-chunk block state is stored as sorted compact arrays rather than entry-heavy HashMaps.
    private final Map<ChunkKey, ChunkBlockData> saltCache = new HashMap<>();
    private final Map<ChunkKey, ChunkBlockData> protectedOreCache = new HashMap<>();
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

    private record SaltOreRule(
        Material material,
        World.Environment environment,
        int minY,
        int maxY,
        int weight,
        int veinMin,
        int veinMax
    ) {}

    private static final List<SaltOreRule> SALT_ORE_RULES = List.of(
        new SaltOreRule(Material.COAL_ORE, World.Environment.NORMAL, 0, 192, 24, 5, 13),
        new SaltOreRule(Material.DEEPSLATE_COAL_ORE, World.Environment.NORMAL, -64, 16, 10, 4, 10),
        new SaltOreRule(Material.COPPER_ORE, World.Environment.NORMAL, -16, 112, 22, 4, 10),
        new SaltOreRule(Material.DEEPSLATE_COPPER_ORE, World.Environment.NORMAL, -64, 16, 8, 3, 8),
        new SaltOreRule(Material.IRON_ORE, World.Environment.NORMAL, -24, 96, 20, 4, 9),
        new SaltOreRule(Material.DEEPSLATE_IRON_ORE, World.Environment.NORMAL, -64, 16, 14, 3, 8),
        new SaltOreRule(Material.GOLD_ORE, World.Environment.NORMAL, -64, 32, 8, 2, 7),
        new SaltOreRule(Material.DEEPSLATE_GOLD_ORE, World.Environment.NORMAL, -64, 16, 8, 2, 7),
        new SaltOreRule(Material.REDSTONE_ORE, World.Environment.NORMAL, -64, 16, 7, 3, 8),
        new SaltOreRule(Material.DEEPSLATE_REDSTONE_ORE, World.Environment.NORMAL, -64, 16, 10, 4, 9),
        new SaltOreRule(Material.LAPIS_ORE, World.Environment.NORMAL, -64, 64, 5, 2, 6),
        new SaltOreRule(Material.DEEPSLATE_LAPIS_ORE, World.Environment.NORMAL, -64, 16, 6, 2, 6),
        new SaltOreRule(Material.DIAMOND_ORE, World.Environment.NORMAL, -64, 16, 2, 1, 4),
        new SaltOreRule(Material.DEEPSLATE_DIAMOND_ORE, World.Environment.NORMAL, -64, 16, 3, 1, 5),
        new SaltOreRule(Material.EMERALD_ORE, World.Environment.NORMAL, 16, 256, 1, 1, 2),
        new SaltOreRule(Material.DEEPSLATE_EMERALD_ORE, World.Environment.NORMAL, -64, 16, 1, 1, 2),
        new SaltOreRule(Material.NETHER_QUARTZ_ORE, World.Environment.NETHER, 10, 118, 28, 5, 14),
        new SaltOreRule(Material.NETHER_GOLD_ORE, World.Environment.NETHER, 10, 118, 16, 3, 10),
        new SaltOreRule(Material.ANCIENT_DEBRIS, World.Environment.NETHER, 8, 24, 1, 1, 3)
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
        protectedOreCache.put(key, ChunkBlockData.from(scan.ores()));
        exposedProtectedOreCache.put(key, scan.exposed());
        if (!config.saltedDistributionEnabled()) {
            saltCache.remove(key);
            return;
        }

        Map<Integer, Material> salt = new HashMap<>();
        generateSalt(chunk, salt);
        saltCache.put(key, ChunkBlockData.from(salt));
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
        ChunkBlockData salt = saltCache.get(keyOf(block.getChunk()));
        if (salt == null) {
            return null;
        }
        return salt.get(packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF));
    }

    /** Returns all salt block positions in the given chunk (used for chunk priming on player join). */
    public List<Block> getSaltBlocksInChunk(Chunk chunk) {
        ChunkBlockData salt = saltCache.get(keyOf(chunk));
        if (salt == null || salt.isEmpty()) {
            return List.of();
        }
        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        return salt.toBlocks(world, baseX, baseZ);
    }

    /** Returns cached protected ore positions in the given chunk. */
    public List<Block> getProtectedOreBlocksInChunk(Chunk chunk) {
        ChunkBlockData ores = protectedOreCache.get(keyOf(chunk));
        if (ores == null || ores.isEmpty()) {
            return List.of();
        }

        World world = chunk.getWorld();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;
        return ores.toBlocks(world, baseX, baseZ);
    }

    public Map<Integer, Material> getProtectedOreEntriesInChunk(UUID worldId, int chunkX, int chunkZ) {
        ChunkBlockData ores = protectedOreCache.get(new ChunkKey(worldId, chunkX, chunkZ));
        return ores == null || ores.isEmpty() ? Map.of() : ores.asMap();
    }

    public Map<Integer, Material> getBuriedProtectedOreEntriesInChunk(UUID worldId, int chunkX, int chunkZ) {
        ChunkKey key = new ChunkKey(worldId, chunkX, chunkZ);
        ChunkBlockData ores = protectedOreCache.get(key);
        if (ores == null || ores.isEmpty()) {
            return Map.of();
        }
        if (!config.revealOnExposure()) {
            return ores.asMap();
        }

        Set<Integer> exposed = exposedProtectedOreCache.get(key);
        if (exposed == null || exposed.isEmpty()) {
            return ores.asMap();
        }

        return ores.asMapExcluding(exposed);
    }

    public String describeChunkRewriteState(Block block) {
        ChunkKey key = keyOf(block.getChunk());
        int packed = packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF);
        Material saltMaterial = getSaltMaterial(block);
        if (saltMaterial != null) {
            return "fake salt: " + saltMaterial.name();
        }
        ChunkBlockData ores = protectedOreCache.get(key);
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
        ChunkBlockData salt = saltCache.get(new ChunkKey(worldId, chunkX, chunkZ));
        return salt == null || salt.isEmpty() ? Map.of() : salt.asMap();
    }

    /** Returns all salt blocks across all currently cached chunks (used for pre-reload drain). */
    public List<Block> collectAllSaltBlocks() {
        List<Block> result = new ArrayList<>();
        for (Map.Entry<ChunkKey, ChunkBlockData> entry : saltCache.entrySet()) {
            ChunkKey key = entry.getKey();
            World world = plugin.getServer().getWorld(key.worldId());
            if (world == null) {
                continue;
            }
            int baseX = key.x() << 4;
            int baseZ = key.z() << 4;
            result.addAll(entry.getValue().toBlocks(world, baseX, baseZ));
        }
        return result;
    }

    public CacheStats cacheStats() {
        int cachedProtectedOres = 0;
        for (ChunkBlockData ores : protectedOreCache.values()) {
            cachedProtectedOres += ores.size();
        }

        int cachedSaltBlocks = 0;
        Map<Material, Integer> saltByType = new java.util.EnumMap<>(Material.class);
        for (ChunkBlockData salt : saltCache.values()) {
            cachedSaltBlocks += salt.size();
            salt.mergeMaterialCounts(saltByType);
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
        ChunkBlockData salt = saltCache.get(key);
        if (salt != null) {
            saltCache.put(key, salt.without(packed));
        }

        ChunkBlockData ores = protectedOreCache.getOrDefault(key, ChunkBlockData.EMPTY);
        if (isProtectedOre(block.getType())) {
            protectedOreCache.put(key, ores.with(packed, block.getType()));
        } else {
            protectedOreCache.put(key, ores.without(packed));
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
        XrayProfile profile = config.xrayProfile();
        int targetBlocks = profile.effectiveSaltBudget(config.saltDensity());
        int rareBlockLimit = profile.maxRareOreBlocks(targetBlocks);
        int rareBlocks = 0;
        int attempts = Math.max(1, targetBlocks / 3);

        for (int i = 0; i < attempts && salt.size() < targetBlocks; i++) {
            SaltOreRule rule = pickSaltOreRule(candidates, rng, profile, rareBlocks, rareBlockLimit);
            if (rule == null) {
                return;
            }
            int lx = rng.nextInt(16);
            int y = Math.max(minY, rule.minY()) + rng.nextInt(Math.max(1, Math.min(maxY - 1, rule.maxY()) - Math.max(minY, rule.minY()) + 1));
            int lz = rng.nextInt(16);

            rareBlocks = growSaltVein(world, baseX, baseZ, lx, y, lz, rule, rng, salt, targetBlocks, profile, rareBlocks, rareBlockLimit);
        }
    }

    private List<SaltOreRule> saltOreCandidates(World.Environment env) {
        return SALT_ORE_RULES.stream()
            .filter(rule -> rule.environment() == env)
            .filter(rule -> config.protectedOres().contains(rule.material()))
            .toList();
    }

    private static SaltOreRule pickSaltOreRule(
        List<SaltOreRule> candidates,
        Random rng,
        XrayProfile profile,
        int rareBlocks,
        int rareBlockLimit
    ) {
        if (candidates.isEmpty()) {
            return null;
        }

        int totalWeight = 0;
        for (SaltOreRule rule : candidates) {
            if (isRareSaltOre(rule.material()) && rareBlocks >= rareBlockLimit) {
                continue;
            }
            totalWeight += profile.effectiveWeight(rule.weight(), rarityOf(rule.material()));
        }
        if (totalWeight <= 0) {
            return null;
        }

        int roll = rng.nextInt(Math.max(1, totalWeight));
        for (SaltOreRule rule : candidates) {
            if (isRareSaltOre(rule.material()) && rareBlocks >= rareBlockLimit) {
                continue;
            }
            roll -= profile.effectiveWeight(rule.weight(), rarityOf(rule.material()));
            if (roll < 0) {
                return rule;
            }
        }
        return candidates.getLast();
    }

    private int growSaltVein(
        World world,
        int baseX,
        int baseZ,
        int startLx,
        int startY,
        int startLz,
        SaltOreRule rule,
        Random rng,
        Map<Integer, Material> salt,
        int targetBlocks,
        XrayProfile profile,
        int rareBlocks,
        int rareBlockLimit
    ) {
        int baseVeinSize = rule.veinMin() + rng.nextInt(Math.max(1, rule.veinMax() - rule.veinMin() + 1));
        int veinSize = profile.effectiveVeinSize(baseVeinSize);
        List<int[]> vein = new ArrayList<>();
        if (tryAddSaltBlock(world, baseX, baseZ, startLx, startY, startLz, rule, salt, vein, rareBlocks, rareBlockLimit)
            && isRareSaltOre(rule.material())) {
            rareBlocks++;
        }

        int guard = veinSize * 10;
        while (vein.size() < veinSize && salt.size() < targetBlocks && guard-- > 0) {
            int[] origin = vein.isEmpty()
                ? new int[]{startLx, startY, startLz}
                : vein.get(rng.nextInt(vein.size()));
            BlockFace face = CARDINAL_FACES[rng.nextInt(CARDINAL_FACES.length)];
            if (tryAddSaltBlock(
                world,
                baseX,
                baseZ,
                origin[0] + face.getModX(),
                origin[1] + face.getModY(),
                origin[2] + face.getModZ(),
                rule,
                salt,
                vein,
                rareBlocks,
                rareBlockLimit
            ) && isRareSaltOre(rule.material())) {
                rareBlocks++;
            }
        }
        return rareBlocks;
    }

    private boolean tryAddSaltBlock(
        World world,
        int baseX,
        int baseZ,
        int lx,
        int y,
        int lz,
        SaltOreRule rule,
        Map<Integer, Material> salt,
        List<int[]> vein,
        int rareBlocks,
        int rareBlockLimit
    ) {
        if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || y < rule.minY() || y > rule.maxY()) {
            return false;
        }
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            return false;
        }

        int packed = packLocal(lx, y, lz);
        if (salt.containsKey(packed)) {
            return false;
        }
        if (isRareSaltOre(rule.material()) && rareBlocks >= rareBlockLimit) {
            return false;
        }

        Block block = world.getBlockAt(baseX + lx, y, baseZ + lz);
        Material type = block.getType();
        if (!SALT_HOSTS.contains(type)
            || config.protectedOres().contains(type)
            || !matchesHost(rule.material(), type)
            || isExposed(block)) {
            return false;
        }

        salt.put(packed, rule.material());
        vein.add(new int[]{lx, y, lz});
        return true;
    }

    private static OreRarity rarityOf(Material material) {
        if (isRareSaltOre(material)) {
            return OreRarity.RARE;
        }
        return switch (material) {
            case COAL_ORE,
                DEEPSLATE_COAL_ORE,
                COPPER_ORE,
                DEEPSLATE_COPPER_ORE,
                IRON_ORE,
                DEEPSLATE_IRON_ORE,
                NETHER_QUARTZ_ORE -> OreRarity.COMMON;
            default -> OreRarity.NORMAL;
        };
    }

    private static boolean isRareSaltOre(Material material) {
        return switch (material) {
            case DIAMOND_ORE,
                DEEPSLATE_DIAMOND_ORE,
                EMERALD_ORE,
                DEEPSLATE_EMERALD_ORE,
                ANCIENT_DEBRIS -> true;
            default -> false;
        };
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
        ChunkBlockData salt = saltCache.get(key);
        if (salt != null) {
            saltCache.put(key, salt.without(packed));
        }
        ChunkBlockData ores = protectedOreCache.get(key);
        if (ores != null) {
            protectedOreCache.put(key, ores.without(packed));
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

    private record ChunkBlockData(int[] positions, Material[] materials) {
        private static final ChunkBlockData EMPTY = new ChunkBlockData(new int[0], new Material[0]);

        private ChunkBlockData {
            if (positions.length != materials.length) {
                throw new IllegalArgumentException("positions and materials must be the same length");
            }
        }

        static ChunkBlockData from(Map<Integer, Material> source) {
            if (source.isEmpty()) {
                return EMPTY;
            }

            int[] positions = source.keySet().stream().mapToInt(Integer::intValue).sorted().toArray();
            Material[] materials = new Material[positions.length];
            for (int i = 0; i < positions.length; i++) {
                materials[i] = source.get(positions[i]);
            }
            return new ChunkBlockData(positions, materials);
        }

        int size() {
            return positions.length;
        }

        boolean isEmpty() {
            return positions.length == 0;
        }

        Material get(int packed) {
            int index = Arrays.binarySearch(positions, packed);
            return index >= 0 ? materials[index] : null;
        }

        boolean containsKey(int packed) {
            return Arrays.binarySearch(positions, packed) >= 0;
        }

        Map<Integer, Material> asMap() {
            if (isEmpty()) {
                return Map.of();
            }

            Map<Integer, Material> map = new HashMap<>(positions.length);
            for (int i = 0; i < positions.length; i++) {
                map.put(positions[i], materials[i]);
            }
            return Map.copyOf(map);
        }

        Map<Integer, Material> asMapExcluding(Set<Integer> excludedPositions) {
            if (isEmpty()) {
                return Map.of();
            }

            Map<Integer, Material> map = new HashMap<>(positions.length);
            for (int i = 0; i < positions.length; i++) {
                if (!excludedPositions.contains(positions[i])) {
                    map.put(positions[i], materials[i]);
                }
            }
            return map.isEmpty() ? Map.of() : Map.copyOf(map);
        }

        List<Block> toBlocks(World world, int baseX, int baseZ) {
            if (isEmpty()) {
                return List.of();
            }

            List<Block> blocks = new ArrayList<>(positions.length);
            for (int packed : positions) {
                int[] loc = unpackLocal(packed);
                blocks.add(world.getBlockAt(baseX + loc[0], loc[1], baseZ + loc[2]));
            }
            return blocks;
        }

        void mergeMaterialCounts(Map<Material, Integer> counts) {
            for (Material material : materials) {
                counts.merge(material, 1, Integer::sum);
            }
        }

        ChunkBlockData with(int packed, Material material) {
            int index = Arrays.binarySearch(positions, packed);
            if (index >= 0) {
                Material[] nextMaterials = materials.clone();
                nextMaterials[index] = material;
                return new ChunkBlockData(positions, nextMaterials);
            }

            int insertionPoint = -index - 1;
            int[] nextPositions = new int[positions.length + 1];
            Material[] nextMaterials = new Material[materials.length + 1];
            System.arraycopy(positions, 0, nextPositions, 0, insertionPoint);
            System.arraycopy(materials, 0, nextMaterials, 0, insertionPoint);
            nextPositions[insertionPoint] = packed;
            nextMaterials[insertionPoint] = material;
            System.arraycopy(positions, insertionPoint, nextPositions, insertionPoint + 1, positions.length - insertionPoint);
            System.arraycopy(materials, insertionPoint, nextMaterials, insertionPoint + 1, materials.length - insertionPoint);
            return new ChunkBlockData(nextPositions, nextMaterials);
        }

        ChunkBlockData without(int packed) {
            int index = Arrays.binarySearch(positions, packed);
            if (index < 0) {
                return this;
            }
            if (positions.length == 1) {
                return EMPTY;
            }

            int[] nextPositions = new int[positions.length - 1];
            Material[] nextMaterials = new Material[materials.length - 1];
            System.arraycopy(positions, 0, nextPositions, 0, index);
            System.arraycopy(materials, 0, nextMaterials, 0, index);
            System.arraycopy(positions, index + 1, nextPositions, index, positions.length - index - 1);
            System.arraycopy(materials, index + 1, nextMaterials, index, materials.length - index - 1);
            return new ChunkBlockData(nextPositions, nextMaterials);
        }
    }

    private record ChunkOreScan(Map<Integer, Material> ores, Set<Integer> exposed) {}
}
