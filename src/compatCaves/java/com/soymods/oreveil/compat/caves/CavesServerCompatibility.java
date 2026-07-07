package com.soymods.oreveil.compat.caves;

import com.soymods.oreveil.compat.ServerCompatibility;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.obfuscation.ObfuscationMetrics;
import com.soymods.oreveil.obfuscation.transport.BlockUpdateSyncTransport;
import com.soymods.oreveil.obfuscation.transport.ObfuscationTransport;
import com.soymods.oreveil.obfuscation.transport.ProtocolLibTransport;
import com.soymods.oreveil.obfuscation.transport.TransportMode;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CavesServerCompatibility implements ServerCompatibility {
    @Override
    public String adapterName() {
        return "caves-1.18";
    }

    @Override
    public boolean isPluginPresent(String pluginName) {
        return Bukkit.getPluginManager().getPlugin(pluginName) != null;
    }

    @Override
    public int minBuildHeight(World world) {
        return world.getMinHeight();
    }

    @Override
    public int maxBuildHeight(World world) {
        return world.getMaxHeight();
    }

    @Override
    public void sendBlockChange(Player player, Block block, Material visibleMaterial) {
        player.sendBlockChange(block.getLocation(), visibleMaterial.createBlockData());
    }

    @Override
    public void allowSignEditor(Sign sign, UUID playerId) {
        sign.setEditable(true);
    }

    @Override
    public ObfuscationTransport createTransport(
        Plugin plugin,
        Logger logger,
        OreveilConfig config,
        AuthoritativeWorldModel worldModel,
        ObfuscationMetrics metrics
    ) {
        TransportMode mode = TransportMode.fromConfig(config.transportMode());
        boolean protocolLibPresent = isPluginPresent("ProtocolLib");

        if (mode == TransportMode.PROTOCOLLIB && protocolLibPresent) {
            return new ProtocolLibTransport(plugin, logger, worldModel, metrics, this);
        }

        if (mode == TransportMode.PROTOCOLLIB) {
            logger.warning("ProtocolLib transport requested but ProtocolLib is not installed. Falling back to block update sync.");
            return new BlockUpdateSyncTransport(plugin, logger, metrics, this);
        }

        if (mode == TransportMode.AUTO && protocolLibPresent) {
            return new ProtocolLibTransport(plugin, logger, worldModel, metrics, this);
        }

        return new BlockUpdateSyncTransport(plugin, logger, metrics, this);
    }
}
