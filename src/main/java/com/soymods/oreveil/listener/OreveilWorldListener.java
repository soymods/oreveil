package com.soymods.oreveil.listener;

import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import com.soymods.oreveil.util.BlockNeighborhoods;

public final class OreveilWorldListener implements Listener {
    private final NetworkObfuscationService obfuscationService;
    private final ExposureService exposureService;
    private final AuthoritativeWorldModel worldModel;

    public OreveilWorldListener(
        NetworkObfuscationService obfuscationService,
        ExposureService exposureService,
        AuthoritativeWorldModel worldModel
    ) {
        this.obfuscationService = obfuscationService;
        this.exposureService = exposureService;
        this.worldModel = worldModel;
    }

    // -------------------------------------------------------------------------
    // Chunk lifecycle
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        worldModel.populateChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        worldModel.evictChunk(event.getChunk());
    }

    // -------------------------------------------------------------------------
    // Block changes
    // -------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        worldModel.invalidateBlock(event.getBlock());
        syncRevealBoundary(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        worldModel.invalidateBlock(event.getBlockPlaced());
        syncRevealBoundary(event.getBlockPlaced());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().forEach(worldModel::invalidateBlock);
        syncRevealBoundaries(event.blockList());
        syncRevealBoundary(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().forEach(worldModel::invalidateBlock);
        syncRevealBoundaries(event.blockList());
        syncRevealBoundary(event.getLocation().getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFluidFlow(BlockFromToEvent event) {
        syncRevealBoundary(event.getToBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Set<Block> impacted = new LinkedHashSet<>();
        impacted.add(event.getBlock());
        impacted.addAll(event.getBlocks());
        syncRevealBoundaries(impacted);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Set<Block> impacted = new LinkedHashSet<>();
        impacted.add(event.getBlock());
        impacted.addAll(event.getBlocks());
        syncRevealBoundaries(impacted);
    }

    private void syncRevealBoundary(Block changedBlock) {
        obfuscationService.syncRevealBoundary(changedBlock);
        obfuscationService.syncRevealBoundaryNextTick(changedBlock);
    }

    private void syncRevealBoundaries(Iterable<Block> changedBlocks) {
        Set<Block> impacted = new LinkedHashSet<>();
        for (Block block : changedBlocks) {
            impacted.addAll(BlockNeighborhoods.cardinalNeighborhood(block, 2));
        }

        obfuscationService.syncProtectedOres(impacted);
        for (Block block : changedBlocks) {
            obfuscationService.syncRevealBoundaryNextTick(block);
        }
    }
}
