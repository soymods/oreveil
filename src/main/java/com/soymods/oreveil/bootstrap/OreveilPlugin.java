package com.soymods.oreveil.bootstrap;

import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import org.bukkit.plugin.java.JavaPlugin;

public final class OreveilPlugin extends JavaPlugin {
    private AuthoritativeWorldModel worldModel;
    private ExposureService exposureService;
    private NetworkObfuscationService obfuscationService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.worldModel = new AuthoritativeWorldModel(getLogger());
        this.exposureService = new ExposureService(getLogger());
        this.obfuscationService = new NetworkObfuscationService(getLogger(), worldModel, exposureService);

        worldModel.start();
        exposureService.start();
        obfuscationService.start();

        getLogger().info("Oreveil scaffold enabled.");
    }

    @Override
    public void onDisable() {
        if (obfuscationService != null) {
            obfuscationService.stop();
        }
        if (exposureService != null) {
            exposureService.stop();
        }
        if (worldModel != null) {
            worldModel.stop();
        }
    }
}
