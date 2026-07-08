package com.soymods.oreveil.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Logger;
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
