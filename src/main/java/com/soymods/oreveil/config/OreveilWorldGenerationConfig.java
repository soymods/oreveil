package com.soymods.oreveil.config;

import java.security.SecureRandom;
import org.bukkit.World;

public record OreveilWorldGenerationConfig(
    boolean enabled,
    String targetWorldName,
    World.Environment environment,
    boolean backupOnRegenerate,
    boolean generateStructures,
    Long configuredSeed,
    long generationSecret,
    int oreRemixAttemptsPerChunk,
    int terrainAdjustmentAttemptsPerChunk,
    double ruinFragmentChance
) {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public long resolveSeed() {
        return configuredSeed != null ? configuredSeed.longValue() : SECURE_RANDOM.nextLong();
    }
}
