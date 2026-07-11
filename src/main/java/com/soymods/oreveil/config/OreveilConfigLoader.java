package com.soymods.oreveil.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class OreveilConfigLoader {
    private final Logger logger;

    public OreveilConfigLoader(Logger logger) {
        this.logger = logger;
    }

    public OreveilConfig load(FileConfiguration config) {
        EnumSet<Material> protectedOres = parseMaterials(
            config.getStringList("protected-ores"),
            "protected-ores"
        );
        EnumSet<Material> revealAdjacentMaterials = parseMaterials(
            config.getStringList("exposure.reveal-adjacent-materials"),
            "exposure.reveal-adjacent-materials"
        );
        EnumSet<Material> revealTransparentMaterials = parseMaterials(
            config.getStringList("exposure.reveal-transparent-materials"),
            "exposure.reveal-transparent-materials"
        );

        OreveilConfig oreveilConfig = new OreveilConfig(
            config.getBoolean("obfuscation.enabled", true),
            config.getBoolean("obfuscation.reveal-on-exposure", true),
            config.getBoolean("obfuscation.reveal-next-to-non-occluding-blocks", true),
            Math.max(0, config.getInt("obfuscation.exposed-ore-reveal-chunk-radius", 2)),
            Math.max(0, config.getInt("obfuscation.exposed-ore-reveal-vertical-radius-blocks", 64)),
            Math.max(16, config.getInt("obfuscation.live-sync-radius-blocks", 64)),
            Math.max(0, config.getInt("obfuscation.initial-sync-chunk-radius", 0)),
            config.getBoolean("world-model.salted-distribution", false),
            XrayProfile.fromConfig(config.getString("world-model.xray-profile", "balanced")),
            Math.max(1, config.getInt("world-model.salt-density", 96)),
            parseLong(config.get("world-model.salt-secret"), "world-model.salt-secret", 0L),
            config.getString("transport.mode", "AUTO"),
            protectedOres,
            revealAdjacentMaterials,
            revealTransparentMaterials,
            parseDimensionDefaults(config.getConfigurationSection("host-blocks.dimension-defaults")),
            parseOreOverrides(config.getConfigurationSection("host-blocks.ore-overrides")),
            parseWorldGeneration(config.getConfigurationSection("world-generation"))
        );

        logger.info(
            "Loaded Oreveil config: protectedOres="
                + oreveilConfig.protectedOres().size()
                + ", hostOverrides="
                + oreveilConfig.oreOverrides().size()
                + ", transport="
                + oreveilConfig.transportMode()
                + ", managedWorld="
                + oreveilConfig.worldGeneration().targetWorldName()
        );
        if (oreveilConfig.saltedDistributionEnabled() && oreveilConfig.saltSecret() == 0L) {
            logger.warning(
                "world-model.salted-distribution is enabled with salt-secret=0. "
                    + "Set world-model.salt-secret to a private random long for seed-resilient fake ore placement."
            );
        }
        if (oreveilConfig.worldGeneration().enabled() && oreveilConfig.worldGeneration().generationSecret() == 0L) {
            logger.warning(
                "world-generation.enabled is true with secret=0. "
                    + "Set world-generation.secret to a private random long for seed-resilient managed ore remixing."
            );
        }

        return oreveilConfig;
    }

    private EnumSet<Material> parseMaterials(Collection<String> names, String path) {
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = parseMaterial(name, path);
            if (material != null) {
                materials.add(material);
            }
        }
        return materials;
    }

    private EnumMap<World.Environment, Material> parseDimensionDefaults(ConfigurationSection section) {
        EnumMap<World.Environment, Material> defaults = new EnumMap<>(World.Environment.class);
        if (section == null) {
            defaults.put(World.Environment.NORMAL, Material.STONE);
            defaults.put(World.Environment.NETHER, Material.NETHERRACK);
            defaults.put(World.Environment.THE_END, Material.END_STONE);
            return defaults;
        }

        for (String key : section.getKeys(false)) {
            World.Environment environment;
            try {
                environment = World.Environment.valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                logger.warning("Ignoring unknown world environment '" + key + "' at host-blocks.dimension-defaults.");
                continue;
            }

            Material material = parseMaterial(section.getString(key), "host-blocks.dimension-defaults." + key);
            if (material != null) {
                defaults.put(environment, material);
            }
        }

        return defaults;
    }

    private EnumMap<Material, Material> parseOreOverrides(ConfigurationSection section) {
        EnumMap<Material, Material> overrides = new EnumMap<>(Material.class);
        if (section == null) {
            return overrides;
        }

        for (String key : section.getKeys(false)) {
            Material ore = parseMaterial(key, "host-blocks.ore-overrides");
            Material replacement = parseMaterial(section.getString(key), "host-blocks.ore-overrides." + key);
            if (ore != null && replacement != null) {
                overrides.put(ore, replacement);
            }
        }

        return overrides;
    }

    private Material parseMaterial(String rawName, String path) {
        if (rawName == null || rawName.isBlank()) {
            logger.warning("Ignoring blank material entry at " + path + ".");
            return null;
        }

        Material material = Material.matchMaterial(rawName, false);
        if (material == null) {
            logger.warning("Ignoring unknown material '" + rawName + "' at " + path + ".");
            return null;
        }
        if (!material.isBlock()) {
            logger.warning("Ignoring non-block material '" + rawName + "' at " + path + ".");
            return null;
        }
        return material;
    }

    private long parseLong(Object rawValue, String path, long fallback) {
        if (rawValue == null) {
            return fallback;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        if (rawValue instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                logger.warning("Ignoring invalid long value '" + text + "' at " + path + ".");
            }
        }
        return fallback;
    }

    private OreveilWorldGenerationConfig parseWorldGeneration(ConfigurationSection section) {
        if (section == null) {
            return new OreveilWorldGenerationConfig(
                false,
                "oreveil",
                World.Environment.NORMAL,
                true,
                true,
                null,
                0L,
                28,
                0,
                0.0D
            );
        }

        String targetWorldName = section.getString("target-world", "oreveil");
        if (targetWorldName == null || targetWorldName.isBlank()) {
            targetWorldName = "oreveil";
        }

        World.Environment environment = World.Environment.NORMAL;
        String rawEnvironment = section.getString("environment", "NORMAL");
        if (rawEnvironment != null) {
            try {
                environment = World.Environment.valueOf(rawEnvironment.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                logger.warning("Ignoring unknown world-generation environment '" + rawEnvironment + "'. Falling back to NORMAL.");
            }
        }

        Object rawSeed = section.get("seed");
        Long configuredSeed = null;
        if (rawSeed instanceof Number number) {
            configuredSeed = number.longValue();
        } else if (rawSeed instanceof String text && !text.isBlank()) {
            try {
                configuredSeed = Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                logger.warning("Ignoring invalid world-generation seed '" + text + "'.");
            }
        }

        return new OreveilWorldGenerationConfig(
            section.getBoolean("enabled", false),
            targetWorldName,
            environment,
            section.getBoolean("backup-on-regenerate", true),
            section.getBoolean("generate-structures", true),
            configuredSeed,
            parseLong(section.get("secret"), "world-generation.secret", 0L),
            Math.max(0, section.getInt("ore-remix-attempts-per-chunk", 28)),
            Math.max(0, section.getInt("terrain-adjustment-attempts-per-chunk", 0)),
            Math.max(0.0D, Math.min(1.0D, section.getDouble("ruin-fragment-chance", 0.0D)))
        );
    }
}
