package com.soymods.oreveil.exposure;

import java.util.logging.Logger;

/**
 * Centralizes reveal decisions so client visibility can remain strictly
 * exposure-driven instead of proximity-driven.
 */
public final class ExposureService {
    private final Logger logger;

    public ExposureService(Logger logger) {
        this.logger = logger;
    }

    public void start() {
        logger.info("Exposure service initialized.");
    }

    public void stop() {
        logger.info("Exposure service stopped.");
    }
}
