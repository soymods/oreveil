package com.soymods.oreveil.obfuscation.scan;

import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ChunkObfuscationPrimer {
    private final ExposureService exposureService;
    private final NetworkObfuscationService obfuscationService;
    private final AuthoritativeWorldModel worldModel;

    public ChunkObfuscationPrimer(
        ExposureService exposureService,
        NetworkObfuscationService obfuscationService,
        AuthoritativeWorldModel worldModel
    ) {
        this.exposureService = exposureService;
        this.obfuscationService = obfuscationService;
        this.worldModel = worldModel;
    }

    public void primeChunk(Player player, Chunk chunk) {
        for (Block block : worldModel.getProtectedOreBlocksInChunk(chunk)) {
            if (exposureService.isProtectedOre(block.getType())
                && obfuscationService.getClientVisibleMaterial(block, player) != block.getType()) {
                obfuscationService.syncBlockToPlayer(player, block);
            }
        }

        // Salt blocks are host-material positions (stone/deepslate/etc.) the client should see as fake ores
        for (Block saltBlock : worldModel.getSaltBlocksInChunk(chunk)) {
            obfuscationService.syncBlockToPlayer(player, saltBlock);
        }
    }
}
