package com.soymods.oreveil.obfuscation.transport;

import com.soymods.oreveil.config.OreveilConfig;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class TransportFactory {
    private TransportFactory() {
    }

    public static ObfuscationTransport create(Plugin plugin, Logger logger, OreveilConfig config) {
        TransportMode mode = TransportMode.fromConfig(config.transportMode());
        boolean protocolLibPresent = Bukkit.getPluginManager().getPlugin("ProtocolLib") != null;

        if (mode == TransportMode.PROTOCOLLIB && protocolLibPresent) {
            return new ProtocolLibTransport(plugin, logger);
        }

        if (mode == TransportMode.PROTOCOLLIB) {
            logger.warning("ProtocolLib transport requested but ProtocolLib is not installed. Falling back to block update sync.");
            return new BlockUpdateSyncTransport(plugin, logger);
        }

        if (mode == TransportMode.AUTO && protocolLibPresent) {
            return new ProtocolLibTransport(plugin, logger);
        }

        return new BlockUpdateSyncTransport(plugin, logger);
    }
}
