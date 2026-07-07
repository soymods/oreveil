package com.soymods.oreveil.compat;

import java.util.logging.Logger;
import org.bukkit.Bukkit;

public final class ServerCompatibilityFactory {
    private static final String MODERN_ADAPTER = "com.soymods.oreveil.compat.modern.ModernServerCompatibility";

    private ServerCompatibilityFactory() {
    }

    public static ServerCompatibility detect(Logger logger) {
        ServerCompatibility compatibility = load(MODERN_ADAPTER, logger);
        logger.info("Oreveil compatibility adapter selected: " + compatibility.adapterName()
            + " on " + Bukkit.getName() + " " + Bukkit.getBukkitVersion() + ".");
        return compatibility;
    }

    private static ServerCompatibility load(String className, Logger logger) {
        try {
            Class<?> type = Class.forName(className);
            Object instance = type.getDeclaredConstructor().newInstance();
            if (instance instanceof ServerCompatibility compatibility) {
                return compatibility;
            }
            throw new IllegalStateException(className + " does not implement ServerCompatibility.");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            logger.severe("Could not load Oreveil compatibility adapter " + className + ": " + exception.getMessage());
            throw new IllegalStateException("Missing Oreveil compatibility adapter: " + className, exception);
        }
    }
}
