package com.soymods.oreveil.bootstrap;

import com.soymods.oreveil.command.OreveilCommand;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.config.OreveilConfigLoader;
import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.listener.OreveilPlayerListener;
import com.soymods.oreveil.listener.OreveilWorldListener;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.obfuscation.scan.ChunkObfuscationPrimer;
import com.soymods.oreveil.obfuscation.transport.TransportMode;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class OreveilPlugin extends JavaPlugin {
    private static final List<Material> ORE_CANDIDATES = List.of(
        Material.COAL_ORE,
        Material.DEEPSLATE_COAL_ORE,
        Material.COPPER_ORE,
        Material.DEEPSLATE_COPPER_ORE,
        Material.IRON_ORE,
        Material.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE,
        Material.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE,
        Material.DEEPSLATE_REDSTONE_ORE,
        Material.EMERALD_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.LAPIS_ORE,
        Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.NETHER_GOLD_ORE,
        Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS
    );

    private OreveilConfigLoader configLoader;
    private OreveilConfig oreveilConfig;
    private AuthoritativeWorldModel worldModel;
    private ExposureService exposureService;
    private NetworkObfuscationService obfuscationService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.configLoader = new OreveilConfigLoader(getLogger());
        this.oreveilConfig = configLoader.load(getConfig());
        this.worldModel = new AuthoritativeWorldModel(this, getLogger(), oreveilConfig);
        this.exposureService = new ExposureService(getLogger(), oreveilConfig);
        this.obfuscationService = new NetworkObfuscationService(this, getLogger(), oreveilConfig, worldModel, exposureService);
        ChunkObfuscationPrimer chunkPrimer = new ChunkObfuscationPrimer(exposureService, obfuscationService, worldModel);

        worldModel.start();
        exposureService.start();
        obfuscationService.start();
        getServer().getPluginManager().registerEvents(new OreveilWorldListener(obfuscationService, exposureService, worldModel), this);
        getServer().getPluginManager().registerEvents(new OreveilPlayerListener(this, chunkPrimer, this::oreveilConfig), this);
        registerCommands();

        // Handle late enable: sync any players already online when the plugin is loaded
        obfuscationService.resyncAllPlayers(Set.copyOf(ORE_CANDIDATES));
        obfuscationService.resyncNewSaltBlocks();

        getLogger().info("Oreveil enabled with " + oreveilConfig.protectedOres().size() + " protected ore materials.");
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

    public OreveilConfig reloadOreveilConfig() {
        reloadConfig();
        this.oreveilConfig = configLoader.load(getConfig());

        // Drain salt before clearing so we can send clients back the real block for removed entries
        List<Block> formerSalt = worldModel.collectAllSaltBlocks();

        this.worldModel.reload(oreveilConfig);
        this.exposureService.reload(oreveilConfig);
        this.obfuscationService.reload(oreveilConfig);

        // Sync real ores (handles protect/unprotect toggling)
        this.obfuscationService.resyncAllPlayers(Set.copyOf(ORE_CANDIDATES));
        // Reveal former salt positions that are no longer in the cache
        this.obfuscationService.resyncBlocks(formerSalt);
        // Push new salt positions to clients
        this.obfuscationService.resyncNewSaltBlocks();

        return oreveilConfig;
    }

    public OreveilConfig oreveilConfig() {
        return oreveilConfig;
    }

    public ExposureService exposureService() {
        return exposureService;
    }

    public NetworkObfuscationService obfuscationService() {
        return obfuscationService;
    }

    public List<Material> candidateOreMaterials() {
        List<Material> ores = new java.util.ArrayList<>(ORE_CANDIDATES);
        ores.sort(Comparator.comparing(Enum::name));
        return ores;
    }

    public void toggleGlobalProtectedOre(Material material) {
        List<String> ores = new ArrayList<>(getConfig().getStringList("protected-ores"));
        String target = material.name();
        if (!ores.removeIf(entry -> entry.equalsIgnoreCase(target))) {
            ores.add(target);
            ores.sort(String::compareToIgnoreCase);
        }
        getConfig().set("protected-ores", ores);
        saveConfig();
        reloadOreveilConfig();
    }

    public OreveilConfig toggleBooleanSetting(String path) {
        getConfig().set(path, !getConfig().getBoolean(path, false));
        saveConfig();
        return reloadOreveilConfig();
    }

    public OreveilConfig setBooleanSetting(String path, boolean value) {
        getConfig().set(path, value);
        saveConfig();
        return reloadOreveilConfig();
    }

    public OreveilConfig setIntegerSetting(String path, int value) {
        getConfig().set(path, value);
        saveConfig();
        return reloadOreveilConfig();
    }

    public OreveilConfig setTransportMode(TransportMode mode) {
        getConfig().set("transport.mode", mode.name());
        saveConfig();
        return reloadOreveilConfig();
    }

    private void registerCommands() {
        PluginCommand command = getCommand("oreveil");
        if (command == null) {
            getLogger().warning("Command registration skipped because plugin.yml is missing /oreveil.");
            return;
        }

        OreveilCommand executor = new OreveilCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
