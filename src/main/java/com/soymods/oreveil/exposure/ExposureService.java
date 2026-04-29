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

    public boolean hasExposureFace(Block block) {
        return !describeExposureFaces(block).isEmpty();
    }

    public boolean hasExposureFaceAfterBreak(Block block, Block removedBlock) {
        return !describeExposureFacesAfterBreak(block, removedBlock).isEmpty();
    }

    public List<String> describeExposure(Block block) {
        if (!isProtectedOre(block.getType())) {
            return List.of();
        }

        return describeExposureFaces(block);
    }

    public List<String> describeExposureFaces(Block block) {
        return describeExposureFacesAfterBreak(block, null);
    }

    public List<String> describeExposureFacesAfterBreak(Block block, Block removedBlock) {
        List<String> reasons = new ArrayList<>();
        EnumSet<Material> revealAdjacent = config.revealAdjacentMaterials();
        EnumSet<Material> revealTransparent = config.revealTransparentMaterials();

        for (BlockFace face : CARDINAL_FACES) {
            Block neighbor = block.getRelative(face);
            Material neighborType = removedBlock != null && sameBlock(neighbor, removedBlock)
                ? Material.AIR
                : neighbor.getType();

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

    private static boolean sameBlock(Block left, Block right) {
        return left.getWorld().equals(right.getWorld())
            && left.getX() == right.getX()
            && left.getY() == right.getY()
            && left.getZ() == right.getZ();
    }
}
