package com.soymods.oreveil.world;

import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.config.OreveilWorldGenerationConfig;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class OreveilWorldGenerationService {
    private static final DateTimeFormatter BACKUP_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final int CHUNKS_PER_TICK = 2;

    private final Plugin plugin;
    private final Logger logger;
    private final Queue<QueuedChunk> queuedChunks = new ArrayDeque<>();
    private final Set<QueuedChunk> queuedChunkSet = new HashSet<>();
    private OreveilConfig config;
    private BukkitTask queueTask;
    private Consumer<List<Block>> mutationSync;

    public OreveilWorldGenerationService(Plugin plugin, Logger logger, OreveilConfig config) {
        this.plugin = plugin;
        this.logger = logger;
        this.config = config;
    }

    public void reload(OreveilConfig config) {
        this.config = config;
    }

    public void setMutationSync(Consumer<List<Block>> mutationSync) {
        this.mutationSync = mutationSync;
    }

    public void start() {
        if (queueTask != null) {
            return;
        }

        queueTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushQueuedChunks, 1L, 1L);
    }

    public void stop() {
        if (queueTask != null) {
            queueTask.cancel();
            queueTask = null;
        }
        queuedChunks.clear();
        queuedChunkSet.clear();
    }

    public OreveilWorldGenerationConfig settings() {
        return config.worldGeneration();
    }

    public boolean isManagedWorld(World world) {
        return world.getName().equalsIgnoreCase(settings().targetWorldName());
    }

    public boolean shouldMutateNewChunks(World world) {
        return settings().enabled() && settings().experimental() && isManagedWorld(world);
    }

    public void queueChunkMutation(Chunk chunk) {
        if (!shouldMutateNewChunks(chunk.getWorld())) {
            return;
        }

        QueuedChunk queuedChunk = new QueuedChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        if (queuedChunkSet.add(queuedChunk)) {
            queuedChunks.add(queuedChunk);
        }
    }

    public void mutateChunk(Chunk chunk) {
        if (!shouldMutateNewChunks(chunk.getWorld())) {
            return;
        }

        List<Block> changedBlocks = new ArrayList<>();
        OreveilWorldGenerationConfig settings = settings();
        if (settings.oreRemixAttemptsPerChunk() > 0) {
            mutateOreDistribution(chunk, settings, changedBlocks);
            seedExposedOre(chunk, settings, changedBlocks);
        }
        if (settings.terrainAdjustmentAttemptsPerChunk() > 0) {
            mutateSurfaceTerrain(chunk, settings, changedBlocks);
        }
        if (settings.ruinFragmentChance() > 0.0D) {
            placeRuinFragment(chunk, settings, changedBlocks);
        }

        if (!changedBlocks.isEmpty() && mutationSync != null) {
            mutationSync.accept(changedBlocks);
        }
    }

    public void createManagedWorldAsync(Long seedOverride, int preloadRadius, WorldOperationListener listener) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            listener.onStage("Creating managed world...");
            WorldRegenerationResult result = createManagedWorld(seedOverride);
            if (!result.success()) {
                listener.onComplete(result);
                return;
            }

            preloadSpawnArea(result.world(), preloadRadius, listener, result);
        });
    }

    public void regenerateManagedWorldAsync(Long seedOverride, int preloadRadius, WorldOperationListener listener) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            listener.onStage("Regenerating managed world...");
            WorldRegenerationResult result = regenerateManagedWorld(seedOverride);
            if (!result.success()) {
                listener.onComplete(result);
                return;
            }

            preloadSpawnArea(result.world(), preloadRadius, listener, result);
        });
    }

    public WorldRegenerationResult createManagedWorld(Long seedOverride) {
        OreveilWorldGenerationConfig settings = settings();
        if (!settings.experimental()) {
            return WorldRegenerationResult.failure("Custom world generation is experimental and currently disabled.");
        }
        if (!settings.enabled()) {
            return WorldRegenerationResult.failure("World generation is disabled in config.");
        }

        String targetWorldName = settings.targetWorldName();
        World existing = Bukkit.getWorld(targetWorldName);
        if (existing != null) {
            return WorldRegenerationResult.failure("Managed world " + targetWorldName + " already exists.");
        }

        long seed = seedOverride != null ? seedOverride.longValue() : settings.resolveSeed();
        World world = createWorld(targetWorldName, seed, settings);
        return WorldRegenerationResult.success(
            "Created managed world " + targetWorldName + " with seed " + seed + ".",
            world,
            seed
        );
    }

    public WorldRegenerationResult regenerateManagedWorld(Long seedOverride) {
        OreveilWorldGenerationConfig settings = settings();
        if (!settings.experimental()) {
            return WorldRegenerationResult.failure("Custom world generation is experimental and currently disabled.");
        }
        if (!settings.enabled()) {
            return WorldRegenerationResult.failure("World generation is disabled in config.");
        }

        String targetWorldName = settings.targetWorldName();
        String primaryWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getName();
        if (primaryWorld != null && primaryWorld.equalsIgnoreCase(targetWorldName)) {
            return WorldRegenerationResult.failure(
                "Refusing to regenerate the server's primary world live. Use a dedicated managed world instead."
            );
        }

        World loaded = Bukkit.getWorld(targetWorldName);
        if (loaded != null) {
            List<Player> occupants = loaded.getPlayers();
            if (!occupants.isEmpty()) {
                return WorldRegenerationResult.failure("Managed world " + targetWorldName + " still has players inside it.");
            }

            loaded.save();
            if (!Bukkit.unloadWorld(loaded, false)) {
                return WorldRegenerationResult.failure("Could not unload managed world " + targetWorldName + ".");
            }
        }

        File worldContainer = Bukkit.getWorldContainer();
        Path worldPath = worldContainer.toPath().resolve(targetWorldName);
        if (Files.exists(worldPath)) {
            try {
                archiveOrDelete(worldPath, settings.backupOnRegenerate());
            } catch (IOException exception) {
                logger.warning("Failed to rotate managed world " + targetWorldName + ": " + exception.getMessage());
                return WorldRegenerationResult.failure("Could not rotate the old managed world folder.");
            }
        }

        long seed = seedOverride != null ? seedOverride.longValue() : settings.resolveSeed();
        World world = createWorld(targetWorldName, seed, settings);
        return WorldRegenerationResult.success(
            "Regenerated managed world " + targetWorldName + " with seed " + seed + ".",
            world,
            seed
        );
    }

    public WorldRegenerationResult deleteWorld(String worldName) {
        String primaryWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getName();
        if (primaryWorld != null && primaryWorld.equalsIgnoreCase(worldName)) {
            return WorldRegenerationResult.failure("Refusing to delete the server's primary world.");
        }

        World loaded = Bukkit.getWorld(worldName);
        if (loaded != null) {
            List<Player> occupants = loaded.getPlayers();
            if (!occupants.isEmpty()) {
                return WorldRegenerationResult.failure("World " + worldName + " still has players inside it.");
            }

            loaded.save();
            if (!Bukkit.unloadWorld(loaded, false)) {
                return WorldRegenerationResult.failure("Could not unload world " + worldName + ".");
            }
        }

        Path worldPath = Bukkit.getWorldContainer().toPath().resolve(worldName);
        if (!Files.exists(worldPath)) {
            return WorldRegenerationResult.failure("World folder " + worldName + " does not exist.");
        }

        try {
            deleteRecursively(worldPath);
        } catch (IOException exception) {
            logger.warning("Failed to delete world " + worldName + ": " + exception.getMessage());
            return WorldRegenerationResult.failure("Could not remove world folder " + worldName + ".");
        }

        return WorldRegenerationResult.success("Deleted world " + worldName + ".", null, Long.MIN_VALUE);
    }

    public WorldRegenerationResult setDefaultWorld(String worldName) {
        Path worldPath = Bukkit.getWorldContainer().toPath().resolve(worldName);
        boolean exists = Files.exists(worldPath.resolve("level.dat")) || Bukkit.getWorld(worldName) != null;
        if (!exists) {
            return WorldRegenerationResult.failure("World " + worldName + " does not exist.");
        }

        Path propertiesPath = Bukkit.getWorldContainer().toPath().resolve("server.properties");
        if (!Files.exists(propertiesPath)) {
            return WorldRegenerationResult.failure("Could not find server.properties.");
        }

        try {
            List<String> lines = Files.readAllLines(propertiesPath);
            boolean replaced = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith("level-name=")) {
                    lines.set(i, "level-name=" + worldName);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lines.add("level-name=" + worldName);
            }
            Files.write(propertiesPath, lines, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            logger.warning("Failed to update server.properties level-name: " + exception.getMessage());
            return WorldRegenerationResult.failure("Could not update server.properties.");
        }

        return WorldRegenerationResult.success(
            "Server default world is now " + worldName + " for the next restart.",
            Bukkit.getWorld(worldName),
            Long.MIN_VALUE
        );
    }

    public String currentDefaultWorldName() {
        Path propertiesPath = Bukkit.getWorldContainer().toPath().resolve("server.properties");
        if (!Files.exists(propertiesPath)) {
            return Bukkit.getWorlds().isEmpty() ? "unknown" : Bukkit.getWorlds().get(0).getName();
        }

        try {
            for (String line : Files.readAllLines(propertiesPath)) {
                if (line.startsWith("level-name=")) {
                    String configured = line.substring("level-name=".length()).trim();
                    if (!configured.isEmpty()) {
                        return configured;
                    }
                }
            }
        } catch (IOException exception) {
            logger.warning("Failed to read server.properties level-name: " + exception.getMessage());
        }

        return Bukkit.getWorlds().isEmpty() ? "unknown" : Bukkit.getWorlds().get(0).getName();
    }

    private World createWorld(String name, long seed, OreveilWorldGenerationConfig settings) {
        WorldCreator creator = WorldCreator.name(name)
            .environment(settings.environment())
            .seed(seed)
            .generateStructures(settings.generateStructures())
            .keepSpawnLoaded(TriState.FALSE);
        World world = creator.createWorld();
        if (world == null) {
            throw new IllegalStateException("Bukkit returned null while creating world " + name + '.');
        }
        logger.info("Created Oreveil managed world " + name + " with seed=" + seed + ".");
        return world;
    }

    private void flushQueuedChunks() {
        for (int i = 0; i < CHUNKS_PER_TICK; i++) {
            QueuedChunk queuedChunk = queuedChunks.poll();
            if (queuedChunk == null) {
                return;
            }

            queuedChunkSet.remove(queuedChunk);
            World world = Bukkit.getWorld(queuedChunk.worldName());
            if (world == null || !world.isChunkLoaded(queuedChunk.chunkX(), queuedChunk.chunkZ())) {
                continue;
            }

            mutateChunk(world.getChunkAt(queuedChunk.chunkX(), queuedChunk.chunkZ()));
        }
    }

    private void preloadSpawnArea(
        World world,
        int radius,
        WorldOperationListener listener,
        WorldRegenerationResult result
    ) {
        int chunkRadius = Math.max(0, radius);
        int spawnChunkX = world.getSpawnLocation().getBlockX() >> 4;
        int spawnChunkZ = world.getSpawnLocation().getBlockZ() >> 4;
        List<int[]> chunks = new java.util.ArrayList<>();
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                chunks.add(new int[] {spawnChunkX + dx, spawnChunkZ + dz});
            }
        }

        if (chunks.isEmpty()) {
            listener.onComplete(result);
            return;
        }

        listener.onStage("Generating spawn area...");
        AtomicInteger completed = new AtomicInteger();
        int total = chunks.size();
        for (int[] coords : chunks) {
            world.getChunkAtAsync(coords[0], coords[1], true, chunk -> {
                int done = completed.incrementAndGet();
                listener.onProgress(done, total);
                if (done == total) {
                    listener.onComplete(result);
                }
            });
        }
    }

    private void archiveOrDelete(Path worldPath, boolean backup) throws IOException {
        if (!backup) {
            deleteRecursively(worldPath);
            return;
        }

        String backupName = worldPath.getFileName() + "_backup_" + BACKUP_SUFFIX.format(LocalDateTime.now());
        Files.move(worldPath, worldPath.resolveSibling(backupName), StandardCopyOption.ATOMIC_MOVE);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (var stream = Files.walk(path)) {
            stream.sorted((left, right) -> right.compareTo(left)).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private void mutateOreDistribution(Chunk chunk, OreveilWorldGenerationConfig settings, List<Block> changedBlocks) {
        Random random = seededRandom(chunk, "ore-remix");
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int i = 0; i < settings.oreRemixAttemptsPerChunk(); i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = minY + random.nextInt(Math.max(1, maxY - minY));
            Block block = world.getBlockAt(x, y, z);

            Material replacement = selectOreReplacement(block.getType(), y, world.getEnvironment(), random);
            if (replacement != null && replacement != block.getType()) {
                block.setType(replacement, false);
                changedBlocks.add(block);
            }
        }

    }

    private Material selectOreReplacement(Material current, int y, World.Environment environment, Random random) {
        if (environment == World.Environment.NETHER) {
            if (current == Material.NETHERRACK && random.nextDouble() < 0.35D) {
                return random.nextBoolean() ? Material.NETHER_QUARTZ_ORE : Material.NETHER_GOLD_ORE;
            }
            return null;
        }

        if (current == Material.STONE || current == Material.DEEPSLATE) {
            boolean deep = current == Material.DEEPSLATE || y < 0;
            if (random.nextDouble() < 0.22D) {
                return deep ? pickDeepOre(random, y) : pickSurfaceOre(random, y);
            }
            return null;
        }

        return null;
    }

    private Material selectExposedOreReplacement(Material current, int y, World.Environment environment, Random random) {
        if (environment == World.Environment.NETHER) {
            if (current == Material.NETHERRACK && random.nextDouble() < 0.25D) {
                return random.nextBoolean() ? Material.NETHER_QUARTZ_ORE : Material.NETHER_GOLD_ORE;
            }
            return null;
        }

        if (current != Material.STONE && current != Material.DEEPSLATE) {
            return null;
        }

        boolean deep = current == Material.DEEPSLATE || y < 0;
        return deep ? pickDeepOre(random, y) : pickSurfaceOre(random, y);
    }

    private boolean isOverworldOre(Material material) {
        return switch (material) {
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                COPPER_ORE, DEEPSLATE_COPPER_ORE,
                IRON_ORE, DEEPSLATE_IRON_ORE,
                GOLD_ORE, DEEPSLATE_GOLD_ORE,
                REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> true;
            default -> false;
        };
    }

    private boolean hasOpenNeighbor(Block block) {
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN)) {
            Material neighbor = block.getRelative(face).getType();
            if (neighbor.isAir() || neighbor == Material.WATER || neighbor == Material.LAVA) {
                return true;
            }
        }
        return false;
    }

    private Material pickSurfaceOre(Random random, int y) {
        if (y > 96) {
            return random.nextDouble() < 0.7D ? Material.COAL_ORE : Material.EMERALD_ORE;
        }
        if (y > 48) {
            return switch (random.nextInt(3)) {
                case 0 -> Material.COAL_ORE;
                case 1 -> Material.COPPER_ORE;
                default -> Material.IRON_ORE;
            };
        }
        if (y > 16) {
            return switch (random.nextInt(4)) {
                case 0 -> Material.IRON_ORE;
                case 1 -> Material.COPPER_ORE;
                case 2 -> Material.GOLD_ORE;
                default -> Material.LAPIS_ORE;
            };
        }
        return switch (random.nextInt(5)) {
            case 0 -> Material.GOLD_ORE;
            case 1 -> Material.REDSTONE_ORE;
            case 2 -> Material.LAPIS_ORE;
            case 3 -> Material.DIAMOND_ORE;
            default -> Material.IRON_ORE;
        };
    }

    private Material pickDeepOre(Random random, int y) {
        if (y < -32) {
            return switch (random.nextInt(4)) {
                case 0 -> Material.DEEPSLATE_DIAMOND_ORE;
                case 1 -> Material.DEEPSLATE_REDSTONE_ORE;
                case 2 -> Material.DEEPSLATE_GOLD_ORE;
                default -> Material.DEEPSLATE_LAPIS_ORE;
            };
        }
        return switch (random.nextInt(5)) {
            case 0 -> Material.DEEPSLATE_IRON_ORE;
            case 1 -> Material.DEEPSLATE_COPPER_ORE;
            case 2 -> Material.DEEPSLATE_GOLD_ORE;
            case 3 -> Material.DEEPSLATE_REDSTONE_ORE;
            default -> Material.DEEPSLATE_COAL_ORE;
        };
    }

    private void mutateSurfaceTerrain(Chunk chunk, OreveilWorldGenerationConfig settings, List<Block> changedBlocks) {
        // Intentionally subtle: leave this as a no-op placeholder until terrain tweaks
        // are cluster-based instead of random single-block edits.
    }

    private boolean isSurfaceMutable(Material material) {
        return switch (material) {
            case GRASS_BLOCK, DIRT, COARSE_DIRT, PODZOL, STONE, ANDESITE, DIORITE, GRANITE, SAND, RED_SAND, GRAVEL -> true;
            default -> false;
        };
    }

    private Material surfaceCapFor(Material material) {
        return switch (material) {
            case DIRT, COARSE_DIRT, PODZOL -> Material.GRASS_BLOCK;
            default -> material;
        };
    }

    private void seedExposedOre(Chunk chunk, OreveilWorldGenerationConfig settings, List<Block> changedBlocks) {
        Random random = seededRandom(chunk, "exposed-ore");
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        int attempts = Math.max(24, settings.oreRemixAttemptsPerChunk() * 3);
        int placements = 0;
        int placementCap = Math.max(6, settings.oreRemixAttemptsPerChunk() / 2);
        for (int i = 0; i < attempts && placements < placementCap; i++) {
            int x = baseX + random.nextInt(16);
            int z = baseZ + random.nextInt(16);
            int y = minY + random.nextInt(Math.max(1, maxY - minY));
            Block block = world.getBlockAt(x, y, z);
            if (!hasOpenNeighbor(block)) {
                continue;
            }

            Material replacement = selectExposedOreReplacement(block.getType(), y, world.getEnvironment(), random);
            if (replacement == null) {
                continue;
            }

            if (random.nextDouble() > exposedOreChance(replacement, y, world.getEnvironment())) {
                continue;
            }

            placeExposedCluster(block, replacement, random, changedBlocks);
            placements++;
        }
    }

    private double exposedOreChance(Material ore, int y, World.Environment environment) {
        if (environment == World.Environment.NETHER) {
            return 0.035D;
        }

        return switch (ore) {
            case COAL_ORE, DEEPSLATE_COAL_ORE -> 0.030D;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> 0.028D;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> 0.024D;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> y < 32 ? 0.020D : 0.010D;
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> 0.018D;
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> y < 0 ? 0.022D : 0.008D;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> y < -16 ? 0.016D : 0.006D;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> y > 80 ? 0.012D : 0.002D;
            default -> 0.0D;
        };
    }

    private void placeExposedCluster(Block origin, Material ore, Random random, List<Block> changedBlocks) {
        if (origin.getType() != ore) {
            origin.setType(ore, false);
            changedBlocks.add(origin);
        }
        int extra = 1 + random.nextInt(3);
        for (int i = 0; i < extra; i++) {
            Block neighbor = origin.getRelative(switch (random.nextInt(6)) {
                case 0 -> BlockFace.NORTH;
                case 1 -> BlockFace.SOUTH;
                case 2 -> BlockFace.EAST;
                case 3 -> BlockFace.WEST;
                case 4 -> BlockFace.UP;
                default -> BlockFace.DOWN;
            });

            if (!sameHostFamily(neighbor.getType(), ore)) {
                continue;
            }
            if (!hasOpenNeighbor(neighbor)) {
                continue;
            }
            if (neighbor.getType() != ore) {
                neighbor.setType(ore, false);
                changedBlocks.add(neighbor);
            }
        }
    }

    private boolean sameHostFamily(Material host, Material ore) {
        if (host == Material.NETHERRACK) {
            return ore == Material.NETHER_QUARTZ_ORE || ore == Material.NETHER_GOLD_ORE;
        }
        if (host == Material.DEEPSLATE) {
            return ore.name().startsWith("DEEPSLATE_");
        }
        if (host == Material.STONE) {
            return isOverworldOre(ore) && !ore.name().startsWith("DEEPSLATE_");
        }
        return false;
    }

    private void placeRuinFragment(Chunk chunk, OreveilWorldGenerationConfig settings, List<Block> changedBlocks) {
        Random random = seededRandom(chunk, "ruin-fragment");
        if (random.nextDouble() > settings.ruinFragmentChance()) {
            return;
        }

        World world = chunk.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        int x = (chunk.getX() << 4) + 2 + random.nextInt(12);
        int z = (chunk.getZ() << 4) + 2 + random.nextInt(12);
        Block top = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
        Block ground = top.getType().isAir() ? top.getRelative(BlockFace.DOWN) : top;
        Block anchor = ground.getRelative(BlockFace.UP);
        if (!anchor.getType().isAir()) {
            return;
        }

        BlockData mossy = Material.MOSSY_COBBLESTONE.createBlockData();
        BlockData cobble = Material.COBBLESTONE.createBlockData();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) == 2 && random.nextBoolean()) {
                    continue;
                }
                Block patch = world.getBlockAt(x + dx, anchor.getY(), z + dz);
                if (!patch.getType().isAir()) {
                    continue;
                }
                patch.setBlockData(random.nextBoolean() ? mossy : cobble, false);
                changedBlocks.add(patch);
            }
        }
    }

    private Random seededRandom(Chunk chunk, String salt) {
        long seed = chunk.getWorld().getSeed();
        long mixed = seed
            ^ ((long) chunk.getX() * 341873128712L)
            ^ ((long) chunk.getZ() * 132897987541L)
            ^ salt.toLowerCase(Locale.ROOT).hashCode();
        return new Random(mixed);
    }

    public record WorldRegenerationResult(boolean success, String message, World world, long seed) {
        public static WorldRegenerationResult success(String message, World world, long seed) {
            return new WorldRegenerationResult(true, message, world, seed);
        }

        public static WorldRegenerationResult failure(String message) {
            return new WorldRegenerationResult(false, message, null, Long.MIN_VALUE);
        }
    }

    public interface WorldOperationListener {
        void onStage(String message);

        void onProgress(int completed, int total);

        void onComplete(WorldRegenerationResult result);
    }

    private record QueuedChunk(String worldName, int chunkX, int chunkZ) {
    }
}
