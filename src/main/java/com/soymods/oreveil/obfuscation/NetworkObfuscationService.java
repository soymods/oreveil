package com.soymods.oreveil.obfuscation;

import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import java.util.logging.Logger;

/**
 * Placeholder entrypoint for chunk and block packet rewriting.
 * The actual packet hooks can later be backed by Paper internals or a protocol layer.
 */
public final class NetworkObfuscationService {
    private final Logger logger;
    private final AuthoritativeWorldModel worldModel;
    private final ExposureService exposureService;

    public NetworkObfuscationService(
        Logger logger,
        AuthoritativeWorldModel worldModel,
        ExposureService exposureService
    ) {
        this.logger = logger;
        this.worldModel = worldModel;
        this.exposureService = exposureService;
    }

    public void start() {
        logger.info(
            "Network obfuscation service initialized with world model="
                + worldModel.getClass().getSimpleName()
                + ", exposure service="
                + exposureService.getClass().getSimpleName()
                + '.'
        );
    }

    public void stop() {
        logger.info("Network obfuscation service stopped.");
    }
}
