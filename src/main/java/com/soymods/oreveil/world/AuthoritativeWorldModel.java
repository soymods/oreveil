package com.soymods.oreveil.world;

import com.soymods.oreveil.config.OreveilConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    private record ChunkKey(UUID worldId, int x, int z) {}

    public AuthoritativeWorldModel(Plugin plugin, Logger logger, OreveilConfig config) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
    }

    public void start() {
        if (config.saltedDistributionEnabled()) {
            int seeded = seedAllLoadedChunks();
            logger.info("Authoritative world model initialized; seeded " + seeded + " already-loaded chunks.");
        } else {
            logger.info("Authoritative world model initialized (salted distribution disabled).");
        }
    }

    public void stop() {
        saltCache.clear();
        logger.info("Authoritative world model stopped.");
    }

    public void reload(OreveilConfig newConfig) {
        saltCache.clear();
        this.config = newConfig;
        if (newConfig.saltedDistributionEnabled()) {
            int seeded = seedAllLoadedChunks();
            logger.info("Authoritative world model reloaded; seeded " + seeded + " chunks.");
        } else {
            logger.info("Authoritative world model reloaded (salted distribution disabled).");
        }
    }

    public boolean isProtectedOre(Material material) {
        return config.protectedOres().contains(material);
    }

    // -------------------------------------------------------------------------
    // Chunk lifecycle
    // -------------------------------------------------------------------------

    public void populateChunk(Chunk chunk) {
        if (!config.saltedDistributionEnabled()) {
            return;
        }
        ChunkKey key = keyOf(chunk);
        if (saltCache.containsKey(key)) {
            return;
        }
        Map<Integer, Material> salt = new HashMap<>();
        generateSalt(chunk, salt);
        saltCache.put(key, salt);
    }

    public void evictChunk(Chunk chunk) {
        saltCache.remove(keyOf(chunk));
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

    /** Removes a block from the salt cache (called on block break/place so mined salt doesn't linger). */
    public void invalidateBlock(Block block) {
        Map<Integer, Material> salt = saltCache.get(keyOf(block.getChunk()));
        if (salt != null) {
            salt.remove(packLocal(block.getX() & 0xF, block.getY(), block.getZ() & 0xF));
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

        List<Material> candidates = saltOreCandidates(env);
        if (candidates.isEmpty()) {
            return;
        }

        // Deterministic seed derived from world seed + chunk coordinates
        long seed = world.getSeed()
            ^ ((long) chunk.getX() * 341873128712L)
            ^ ((long) chunk.getZ() * 132897987541L);
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
            // Don't place salt on real protected ores
            if (config.protectedOres().contains(type)) {
                continue;
            }

            Material saltOre = pickSaltOre(type, candidates, rng);
            if (saltOre != null) {
                salt.put(packLocal(lx, y, lz), saltOre);
            }
        }
    }

    private List<Material> saltOreCandidates(World.Environment env) {
        return config.protectedOres().stream()
            .filter(m -> matchesDimension(m, env))
            .toList();
    }

    private static boolean matchesDimension(Material ore, World.Environment env) {
        String name = ore.name();
        return switch (env) {
            case NETHER -> name.startsWith("NETHER_") || name.equals("ANCIENT_DEBRIS");
            case THE_END -> !name.startsWith("NETHER_") && !name.equals("ANCIENT_DEBRIS");
            default -> !name.startsWith("NETHER_") && !name.equals("ANCIENT_DEBRIS");
        };
    }

    private static Material pickSaltOre(Material host, List<Material> candidates, Random rng) {
        List<Material> matching = candidates.stream()
            .filter(m -> matchesHost(m, host))
            .toList();
        List<Material> pool = matching.isEmpty() ? candidates : matching;
        return pool.isEmpty() ? null : pool.get(rng.nextInt(pool.size()));
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

    // lx/lz: 0–15 (4 bits each), y offset by 2048 to handle negative heights
    private static int packLocal(int lx, int y, int lz) {
        return (lx & 0xF) | ((lz & 0xF) << 4) | ((y + 2048) << 8);
    }

    private static int[] unpackLocal(int packed) {
        return new int[]{packed & 0xF, (packed >> 8) - 2048, (packed >> 4) & 0xF};
    }
}
