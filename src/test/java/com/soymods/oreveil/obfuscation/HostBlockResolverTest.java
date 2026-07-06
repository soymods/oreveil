package com.soymods.oreveil.obfuscation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.config.OreveilWorldGenerationConfig;
import java.util.EnumMap;
import java.util.EnumSet;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

final class HostBlockResolverTest {
    @Test
    void usesExplicitOreOverrideBeforeDimensionDefault() {
        HostBlockResolver resolver = new HostBlockResolver();

        assertEquals(
            Material.DEEPSLATE,
            resolver.resolve(Material.DEEPSLATE_DIAMOND_ORE, World.Environment.NORMAL, config())
        );
    }

    @Test
    void fallsBackToDimensionDefault() {
        HostBlockResolver resolver = new HostBlockResolver();

        assertEquals(
            Material.NETHERRACK,
            resolver.resolve(Material.NETHER_QUARTZ_ORE, World.Environment.NETHER, config())
        );
    }

    private static OreveilConfig config() {
        EnumMap<World.Environment, Material> defaults = new EnumMap<>(World.Environment.class);
        defaults.put(World.Environment.NORMAL, Material.STONE);
        defaults.put(World.Environment.NETHER, Material.NETHERRACK);
        defaults.put(World.Environment.THE_END, Material.END_STONE);

        EnumMap<Material, Material> overrides = new EnumMap<>(Material.class);
        overrides.put(Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE);

        return new OreveilConfig(
            true,
            true,
            true,
            6,
            64,
            0,
            false,
            64,
            0L,
            "AUTO",
            EnumSet.of(Material.DEEPSLATE_DIAMOND_ORE, Material.NETHER_QUARTZ_ORE),
            EnumSet.of(Material.AIR),
            EnumSet.noneOf(Material.class),
            defaults,
            overrides,
            new OreveilWorldGenerationConfig(false, false, "oreveil", World.Environment.NORMAL, true, true, null, 18, 8, 0.02D)
        );
    }
}
