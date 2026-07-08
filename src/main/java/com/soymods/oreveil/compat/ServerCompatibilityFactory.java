package com.soymods.oreveil.compat;

import java.util.logging.Logger;
import org.bukkit.Bukkit;

public final class ServerCompatibilityFactory {
    private static final String CAVES_ADAPTER = "com.soymods.oreveil.compat.caves.CavesServerCompatibility";
    private static final String MODERN_ADAPTER = "com.soymods.oreveil.compat.modern.ModernServerCompatibility";

    private ServerCompatibilityFactory() {
    }

    public static ServerCompatibility detect(Logger logger) {
        String adapterClass = minecraftMajorVersion() >= 21 ? MODERN_ADAPTER : CAVES_ADAPTER;
        ServerCompatibility compatibility = load(adapterClass, logger);
        logger.info("Oreveil compatibility adapter selected: " + compatibility.adapterName()
            + " on " + Bukkit.getName() + " " + Bukkit.getBukkitVersion() + ".");
        return compatibility;
    }

    private static int minecraftMajorVersion() {
        String version = Bukkit.getMinecraftVersion();
        String[] parts = version.split("\\.");
        try {
            int release = Integer.parseInt(parts[0]);
            return release == 1 && parts.length > 1 ? Integer.parseInt(parts[1]) : release;
        } catch (NumberFormatException exception) {
            return 1;
        }
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
