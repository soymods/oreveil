package com.soymods.oreveil.listener;

import com.soymods.oreveil.world.OreveilWorldGenerationService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public final class OreveilWorldGenerationListener implements Listener {
    private final OreveilWorldGenerationService worldGenerationService;

    public OreveilWorldGenerationListener(OreveilWorldGenerationService worldGenerationService) {
        this.worldGenerationService = worldGenerationService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) {
            return;
        }

        if (!worldGenerationService.shouldMutateNewChunks(event.getWorld())) {
            return;
        }

        worldGenerationService.queueChunkMutation(event.getChunk());
    }
}
