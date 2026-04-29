package com.soymods.oreveil.config;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.World;

public record OreveilConfig(
    boolean obfuscationEnabled,
    boolean revealOnExposure,
    boolean revealNextToNonOccludingBlocks,
    int revealProximityBlocks,
    int liveSyncRadiusBlocks,
    int initialSyncChunkRadius,
    boolean experimentalChunkRewrite,
    boolean saltedDistributionEnabled,
    int saltDensity,
    String worldModelMode,
    String transportMode,
    EnumSet<Material> protectedOres,
    EnumSet<Material> revealAdjacentMaterials,
    EnumSet<Material> revealTransparentMaterials,
    EnumMap<World.Environment, Material> dimensionDefaults,
    EnumMap<Material, Material> oreOverrides,
    boolean verboseLogging
) {
    public Material resolveDimensionDefault(World.Environment environment) {
        return dimensionDefaults.getOrDefault(environment, Material.STONE);
    }

    public Material resolveOreOverride(Material material) {
        return oreOverrides.get(material);
    }

    public Map<World.Environment, Material> dimensionDefaultsView() {
        return Map.copyOf(dimensionDefaults);
    }

    public Map<Material, Material> oreOverridesView() {
        return Map.copyOf(oreOverrides);
    }
}
