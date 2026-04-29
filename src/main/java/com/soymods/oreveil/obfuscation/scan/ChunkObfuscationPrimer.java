package com.soymods.oreveil.obfuscation.scan;

import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import org.bukkit.Chunk;
import org.bukkit.World;
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
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        int baseX = chunk.getX() << 4;
        int baseZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = world.getBlockAt(baseX + x, y, baseZ + z);
                    if (exposureService.isProtectedOre(block.getType())) {
                        if (obfuscationService.getClientVisibleMaterial(block) != block.getType()) {
                            obfuscationService.syncBlockToPlayer(player, block);
                        }
                    }
                }
            }
        }

        // Salt blocks are host-material positions (stone/deepslate/etc.) the client should see as fake ores
        for (Block saltBlock : worldModel.getSaltBlocksInChunk(chunk)) {
            obfuscationService.syncBlockToPlayer(player, saltBlock);
        }
    }
}
