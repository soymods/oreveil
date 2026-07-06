package com.soymods.oreveil.obfuscation;

import com.soymods.oreveil.config.OreveilConfig;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class HostBlockResolver {
    public Material resolve(Block block, OreveilConfig config) {
        Material explicitOverride = config.resolveOreOverride(block.getType());
        if (explicitOverride != null) {
            return explicitOverride;
        }

        return config.resolveDimensionDefault(block.getWorld().getEnvironment());
    }

    public Material resolve(Material ore, World.Environment environment, OreveilConfig config) {
        Material explicitOverride = config.resolveOreOverride(ore);
        if (explicitOverride != null) {
            return explicitOverride;
        }

        return config.resolveDimensionDefault(environment);
    }
}
