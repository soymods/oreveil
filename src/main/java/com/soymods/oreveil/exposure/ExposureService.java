package com.soymods.oreveil.exposure;

import com.soymods.oreveil.config.OreveilConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * Centralizes reveal decisions so client visibility can remain strictly
 * exposure-driven instead of proximity-driven.
 */
public final class ExposureService {
    private static final BlockFace[] CARDINAL_FACES = {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST,
    };

    private final Logger logger;
    private OreveilConfig config;

    public ExposureService(Logger logger, OreveilConfig config) {
        this.logger = logger;
        this.config = config;
    }

    public void start() {
        logger.info("Exposure service initialized.");
    }

    public void stop() {
        logger.info("Exposure service stopped.");
    }

    public void reload(OreveilConfig config) {
        this.config = config;
        logger.info("Exposure service configuration reloaded.");
    }

    public boolean isProtectedOre(Material material) {
        return config.protectedOres().contains(material);
    }

    public boolean isLegitimatelyExposed(Block block) {
        return !describeExposure(block).isEmpty();
    }

    public List<String> describeExposure(Block block) {
        if (!isProtectedOre(block.getType())) {
            return List.of();
        }

        List<String> reasons = new ArrayList<>();
        EnumSet<Material> revealAdjacent = config.revealAdjacentMaterials();
        EnumSet<Material> revealTransparent = config.revealTransparentMaterials();

        for (BlockFace face : CARDINAL_FACES) {
            Block neighbor = block.getRelative(face);
            Material neighborType = neighbor.getType();

            if (revealAdjacent.contains(neighborType)) {
                reasons.add(face.name() + " adjacent to " + neighborType);
                continue;
            }

            if (revealTransparent.contains(neighborType)) {
                reasons.add(face.name() + " adjacent to transparent " + neighborType);
                continue;
            }

            if (config.revealNextToNonOccludingBlocks() && neighborType.isBlock() && !neighborType.isOccluding()) {
                reasons.add(face.name() + " adjacent to non-occluding " + neighborType);
            }
        }

        return reasons;
    }
}
