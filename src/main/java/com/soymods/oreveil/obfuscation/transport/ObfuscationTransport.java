package com.soymods.oreveil.obfuscation.transport;

import com.soymods.oreveil.config.OreveilConfig;
import java.util.function.BiFunction;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public interface ObfuscationTransport {
    void start(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver);

    void stop();

    void reload(OreveilConfig config, BiFunction<Block, Player, Material> materialResolver);

    void syncBlock(Block block, BiFunction<Block, Player, Material> materialResolver, int radiusBlocks);

    void syncBlockToPlayer(Player player, Block block, BiFunction<Block, Player, Material> materialResolver);

    boolean handlesChunkDelivery();

    String name();
}
