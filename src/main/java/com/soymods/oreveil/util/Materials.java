package com.soymods.oreveil.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;

public final class Materials {
    public static final Material DEEPSLATE = optional("DEEPSLATE");
    public static final Material COPPER_ORE = optional("COPPER_ORE");
    public static final Material DEEPSLATE_COAL_ORE = optional("DEEPSLATE_COAL_ORE");
    public static final Material DEEPSLATE_COPPER_ORE = optional("DEEPSLATE_COPPER_ORE");
    public static final Material DEEPSLATE_IRON_ORE = optional("DEEPSLATE_IRON_ORE");
    public static final Material DEEPSLATE_GOLD_ORE = optional("DEEPSLATE_GOLD_ORE");
    public static final Material DEEPSLATE_REDSTONE_ORE = optional("DEEPSLATE_REDSTONE_ORE");
    public static final Material DEEPSLATE_EMERALD_ORE = optional("DEEPSLATE_EMERALD_ORE");
    public static final Material DEEPSLATE_LAPIS_ORE = optional("DEEPSLATE_LAPIS_ORE");
    public static final Material DEEPSLATE_DIAMOND_ORE = optional("DEEPSLATE_DIAMOND_ORE");
    public static final Material AMETHYST_SHARD = optional("AMETHYST_SHARD");
    public static final Material SPYGLASS = optional("SPYGLASS");
    public static final Material ECHO_SHARD = optional("ECHO_SHARD");

    private Materials() {
    }

    public static Material optional(String name) {
        return Material.matchMaterial(name);
    }

    public static Material optional(String name, Material fallback) {
        Material material = optional(name);
        return material == null ? fallback : material;
    }

    public static boolean is(Material material, String name) {
        return material != null && material.name().equals(name);
    }

    public static boolean isDeepslate(Material material) {
        return is(material, "DEEPSLATE");
    }

    public static boolean isDeepslateOre(Material material) {
        return material != null && material.name().startsWith("DEEPSLATE_");
    }

    public static List<Material> existing(Material... candidates) {
        List<Material> result = new ArrayList<>();
        for (Material candidate : candidates) {
            if (candidate != null) {
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }
}
