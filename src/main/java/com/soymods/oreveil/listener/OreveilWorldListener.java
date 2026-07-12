package com.soymods.oreveil.listener;

import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.util.OreveilScheduler;
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
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import com.soymods.oreveil.util.BlockNeighborhoods;

public final class OreveilWorldListener implements Listener {
    private final Plugin plugin;
    private final NetworkObfuscationService obfuscationService;
    private final ExposureService exposureService;
    private final AuthoritativeWorldModel worldModel;
    private final OreveilScheduler scheduler;

    public OreveilWorldListener(
        Plugin plugin,
        NetworkObfuscationService obfuscationService,
        ExposureService exposureService,
        AuthoritativeWorldModel worldModel
    ) {
        this.plugin = plugin;
        this.obfuscationService = obfuscationService;
        this.exposureService = exposureService;
        this.worldModel = worldModel;
        this.scheduler = plugin instanceof com.soymods.oreveil.bootstrap.OreveilPlugin oreveilPlugin
            ? oreveilPlugin.scheduler()
            : new OreveilScheduler(plugin, plugin.getLogger());
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
        refreshExposureNextTick(BlockNeighborhoods.cardinalNeighborhood(event.getBlock(), 1));
        obfuscationService.syncAfterBreak(event.getBlock());
        syncRevealBoundary(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        worldModel.refreshBlock(event.getBlockPlaced());
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
        refreshNextTick(event.getToBlock());
        syncRevealBoundary(event.getToBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockForm(BlockFormEvent event) {
        refreshNextTick(event.getBlock());
        syncRevealBoundary(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockFade(BlockFadeEvent event) {
        refreshNextTick(event.getBlock());
        syncRevealBoundary(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockGrow(BlockGrowEvent event) {
        refreshNextTick(event.getBlock());
        syncRevealBoundary(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockSpread(BlockSpreadEvent event) {
        refreshNextTick(event.getBlock());
        syncRevealBoundary(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        refreshNextTick(event.getBlock());
        syncRevealBoundary(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Set<Block> impacted = new LinkedHashSet<>();
        impacted.add(event.getBlock());
        impacted.addAll(event.getBlocks());
        for (Block moved : event.getBlocks()) {
            impacted.add(moved.getRelative(event.getDirection()));
        }
        refreshNextTick(impacted);
        syncRevealBoundaries(impacted);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        Set<Block> impacted = new LinkedHashSet<>();
        impacted.add(event.getBlock());
        impacted.addAll(event.getBlocks());
        for (Block moved : event.getBlocks()) {
            impacted.add(moved.getRelative(event.getDirection()));
            impacted.add(moved.getRelative(event.getDirection().getOppositeFace()));
        }
        refreshNextTick(impacted);
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

    private void refreshNextTick(Block block) {
        scheduler.runAt(block.getLocation(), () -> worldModel.refreshBlock(block));
    }

    private void refreshNextTick(Iterable<Block> blocks) {
        Set<Block> snapshot = new LinkedHashSet<>();
        for (Block block : blocks) {
            snapshot.add(block);
        }
        if (snapshot.isEmpty()) {
            return;
        }

        for (Block block : snapshot) {
            scheduler.runAt(block.getLocation(), () -> worldModel.refreshBlock(block));
        }
    }

    private void refreshExposureNextTick(Iterable<Block> blocks) {
        Set<Block> snapshot = new LinkedHashSet<>();
        for (Block block : blocks) {
            snapshot.add(block);
        }
        if (snapshot.isEmpty()) {
            return;
        }

        for (Block block : snapshot) {
            scheduler.runAt(block.getLocation(), () -> worldModel.refreshExposureState(block));
        }
    }
}
