package com.soymods.oreveil.world;

import java.util.logging.Logger;

/**
 * Owns the server-authoritative record of hidden ore state.
 * The scaffold keeps this intentionally small so generation, persistence,
 * and salted distribution logic can be added without rewriting plugin wiring.
 */
public final class AuthoritativeWorldModel {
    private final Logger logger;

    public AuthoritativeWorldModel(Logger logger) {
        this.logger = logger;
    }

    public void start() {
        logger.info("Authoritative world model initialized.");
    }

    public void stop() {
        logger.info("Authoritative world model stopped.");
    }
}
