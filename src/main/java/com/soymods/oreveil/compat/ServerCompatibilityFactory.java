package com.soymods.oreveil.compat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

public final class ServerCompatibilityFactory {
    private static final String MODERN_ADAPTER = "com.soymods.oreveil.compat.modern.ModernServerCompatibility";
    private static final String ADAPTER_RESOURCE = "oreveil-compat.properties";

    private ServerCompatibilityFactory() {
    }

    public static ServerCompatibility detect(Logger logger) {
        String adapterClass = adapterClass(logger);
        ServerCompatibility compatibility = load(adapterClass, logger);
        logger.info("Oreveil compatibility adapter selected: " + compatibility.adapterName()
            + " on " + Bukkit.getName() + " " + Bukkit.getBukkitVersion() + ".");
        return compatibility;
    }

    private static String adapterClass(Logger logger) {
        try (InputStream input = ServerCompatibilityFactory.class.getClassLoader().getResourceAsStream(ADAPTER_RESOURCE)) {
            if (input == null) {
                return MODERN_ADAPTER;
            }

            Properties properties = new Properties();
            properties.load(input);
            return properties.getProperty("adapterClass", MODERN_ADAPTER);
        } catch (IOException exception) {
            logger.warning("Could not read " + ADAPTER_RESOURCE + ": " + exception.getMessage()
                + ". Falling back to " + MODERN_ADAPTER + ".");
            return MODERN_ADAPTER;
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
