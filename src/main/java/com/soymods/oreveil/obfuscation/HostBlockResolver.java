package com.soymods.oreveil.obfuscation;

import com.soymods.oreveil.config.OreveilConfig;
import org.bukkit.Material;
import org.bukkit.block.Block;

public final class HostBlockResolver {
    public Material resolve(Block block, OreveilConfig config) {
        Material explicitOverride = config.resolveOreOverride(block.getType());
        if (explicitOverride != null) {
            return explicitOverride;
        }

        return config.resolveDimensionDefault(block.getWorld().getEnvironment());
    }
}
