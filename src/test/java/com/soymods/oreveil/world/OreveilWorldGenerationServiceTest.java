package com.soymods.oreveil.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

final class OreveilWorldGenerationServiceTest {
    @Test
    void mapsVanillaOresBackToTheirHostFamily() {
        assertEquals(Material.STONE, OreveilWorldGenerationService.relocationHost(Material.DIAMOND_ORE));
        assertEquals(Material.DEEPSLATE, OreveilWorldGenerationService.relocationHost(Material.DEEPSLATE_DIAMOND_ORE));
        assertEquals(Material.NETHERRACK, OreveilWorldGenerationService.relocationHost(Material.NETHER_GOLD_ORE));
        assertEquals(Material.NETHERRACK, OreveilWorldGenerationService.relocationHost(Material.NETHER_QUARTZ_ORE));
        assertEquals(Material.NETHERRACK, OreveilWorldGenerationService.relocationHost(Material.ANCIENT_DEBRIS));
    }

    @Test
    void doesNotTreatHostBlocksAsOres() {
        assertNull(OreveilWorldGenerationService.relocationHost(Material.STONE));
        assertNull(OreveilWorldGenerationService.relocationHost(Material.DEEPSLATE));
        assertNull(OreveilWorldGenerationService.relocationHost(Material.NETHERRACK));
    }
}
