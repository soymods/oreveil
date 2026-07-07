package com.soymods.oreveil.compat;

import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.obfuscation.ObfuscationMetrics;
import com.soymods.oreveil.obfuscation.transport.ObfuscationTransport;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public interface ServerCompatibility {
    String adapterName();

    boolean isPluginPresent(String pluginName);

    int minBuildHeight(World world);

    int maxBuildHeight(World world);

    void sendBlockChange(Player player, Block block, Material visibleMaterial);

    void allowSignEditor(Sign sign, UUID playerId);

    ObfuscationTransport createTransport(
        Plugin plugin,
        Logger logger,
        OreveilConfig config,
        AuthoritativeWorldModel worldModel,
        ObfuscationMetrics metrics
    );
}
