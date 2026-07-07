package com.soymods.oreveil.compat;

import java.util.logging.Logger;
import org.bukkit.Bukkit;

public final class ServerCompatibilityFactory {
    private ServerCompatibilityFactory() {
    }

    public static ServerCompatibility detect(Logger logger) {
        ServerCompatibility compatibility = new ModernServerCompatibility();
        logger.info("Oreveil compatibility adapter selected: " + compatibility.adapterName()
            + " on " + Bukkit.getName() + " " + Bukkit.getBukkitVersion() + ".");
        return compatibility;
    }
}
