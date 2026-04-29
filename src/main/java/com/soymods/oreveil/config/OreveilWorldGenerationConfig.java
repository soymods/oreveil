package com.soymods.oreveil.config;

import org.bukkit.World;

public record OreveilWorldGenerationConfig(
    boolean enabled,
    boolean experimental,
    String targetWorldName,
    World.Environment environment,
    boolean backupOnRegenerate,
    boolean generateStructures,
    Long configuredSeed,
    int oreRemixAttemptsPerChunk,
    int terrainAdjustmentAttemptsPerChunk,
    double ruinFragmentChance
) {
    public long resolveSeed() {
        return configuredSeed != null ? configuredSeed.longValue() : System.nanoTime();
    }
}
