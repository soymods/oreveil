package com.soymods.oreveil.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

final class OreveilConfigLoaderTest {
    @Test
    void parsesWorldGenerationSecret() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world-generation.secret", 987654321L);

        OreveilConfig config = new OreveilConfigLoader(Logger.getAnonymousLogger()).load(yaml);

        assertEquals(987654321L, config.worldGeneration().generationSecret());
    }

    @Test
    void clampsRiskyNumericSettingsToSafeBounds() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("obfuscation.exposed-ore-reveal-chunk-radius", -10);
        yaml.set("obfuscation.live-sync-radius-blocks", 1);
        yaml.set("obfuscation.initial-sync-chunk-radius", -1);
        yaml.set("world-model.salt-density", 0);
        yaml.set("world-generation.ore-remix-attempts-per-chunk", -5);
        yaml.set("world-generation.terrain-adjustment-attempts-per-chunk", -3);
        yaml.set("world-generation.ruin-fragment-chance", 2.5D);

        OreveilConfig config = new OreveilConfigLoader(Logger.getAnonymousLogger()).load(yaml);

        assertEquals(0, config.exposedOreRevealChunkRadius());
        assertEquals(16, config.liveSyncRadiusBlocks());
        assertEquals(0, config.initialSyncChunkRadius());
        assertEquals(1, config.saltDensity());
        assertEquals(0, config.worldGeneration().oreRemixAttemptsPerChunk());
        assertEquals(0, config.worldGeneration().terrainAdjustmentAttemptsPerChunk());
        assertEquals(1.0D, config.worldGeneration().ruinFragmentChance());
    }

    @Test
    void fallsBackToDefensiveDefaultsWhenSectionsAreMissing() {
        OreveilConfig config = new OreveilConfigLoader(Logger.getAnonymousLogger()).load(new YamlConfiguration());

        assertEquals(Material.STONE, config.resolveDimensionDefault(World.Environment.NORMAL));
        assertEquals(Material.NETHERRACK, config.resolveDimensionDefault(World.Environment.NETHER));
        assertEquals(Material.END_STONE, config.resolveDimensionDefault(World.Environment.THE_END));
        assertEquals("AUTO", config.transportMode());
        assertFalse(config.saltedDistributionEnabled());
        assertFalse(config.worldGeneration().enabled());
    }

    @Test
    void configuredSeedStillWinsOverRandomSeedMode() {
        OreveilWorldGenerationConfig config = new OreveilWorldGenerationConfig(
            true,
            "oreveil",
            org.bukkit.World.Environment.NORMAL,
            true,
            true,
            1234L,
            99L,
            28,
            0,
            0.0D
        );

        assertEquals(1234L, config.resolveSeed());
    }
}
