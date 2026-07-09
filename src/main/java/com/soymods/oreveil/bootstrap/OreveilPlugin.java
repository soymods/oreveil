package com.soymods.oreveil.bootstrap;

import com.soymods.oreveil.command.OreveilCommand;
import com.soymods.oreveil.compat.ServerCompatibility;
import com.soymods.oreveil.compat.ServerCompatibilityFactory;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.config.OreveilConfigLoader;
import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.listener.OreveilPlayerListener;
import com.soymods.oreveil.listener.OreveilWorldGenerationListener;
import com.soymods.oreveil.listener.OreveilWorldListener;
import com.soymods.oreveil.metrics.Metrics;
import com.soymods.oreveil.obfuscation.NetworkObfuscationService;
import com.soymods.oreveil.obfuscation.scan.ChunkObfuscationPrimer;
import com.soymods.oreveil.obfuscation.transport.TransportMode;
import com.soymods.oreveil.ui.OreveilAdminGui;
import com.soymods.oreveil.util.Materials;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import com.soymods.oreveil.world.OreveilWorldGenerationService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class OreveilPlugin extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 32491;
    private static final DateTimeFormatter CONFIG_BACKUP_SUFFIX = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<Material> ORE_CANDIDATES = Materials.existing(
        Material.COAL_ORE,
        Materials.DEEPSLATE_COAL_ORE,
        Materials.COPPER_ORE,
        Materials.DEEPSLATE_COPPER_ORE,
        Material.IRON_ORE,
        Materials.DEEPSLATE_IRON_ORE,
        Material.GOLD_ORE,
        Materials.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE,
        Materials.DEEPSLATE_REDSTONE_ORE,
        Material.EMERALD_ORE,
        Materials.DEEPSLATE_EMERALD_ORE,
        Material.LAPIS_ORE,
        Materials.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE,
        Materials.DEEPSLATE_DIAMOND_ORE,
        Material.NETHER_GOLD_ORE,
        Material.NETHER_QUARTZ_ORE,
        Material.ANCIENT_DEBRIS
    );

    private OreveilConfigLoader configLoader;
    private OreveilConfig oreveilConfig;
    private AuthoritativeWorldModel worldModel;
    private ExposureService exposureService;
    private NetworkObfuscationService obfuscationService;
    private OreveilWorldGenerationService worldGenerationService;
    private OreveilAdminGui adminGui;
    private ServerCompatibility compatibility;
    private Metrics metrics;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        this.configLoader = new OreveilConfigLoader(getLogger());
        this.oreveilConfig = configLoader.load(getConfig());
        this.compatibility = ServerCompatibilityFactory.detect(getLogger());
        this.worldModel = new AuthoritativeWorldModel(this, getLogger(), oreveilConfig, compatibility);
        this.exposureService = new ExposureService(getLogger(), oreveilConfig);
        this.obfuscationService = new NetworkObfuscationService(this, getLogger(), oreveilConfig, worldModel, exposureService, compatibility);
        this.worldGenerationService = new OreveilWorldGenerationService(this, getLogger(), oreveilConfig, compatibility);
        this.adminGui = new OreveilAdminGui(this, compatibility);
        this.worldGenerationService.setMutationSync(blocks -> {
            blocks.forEach(worldModel::refreshBlock);
            obfuscationService.resyncBlocks(blocks);
        });
        ChunkObfuscationPrimer chunkPrimer = new ChunkObfuscationPrimer(exposureService, obfuscationService, worldModel);

        worldModel.start();
        exposureService.start();
        worldGenerationService.start();
        obfuscationService.start();
        getServer().getPluginManager().registerEvents(new OreveilWorldListener(this, obfuscationService, exposureService, worldModel), this);
        getServer().getPluginManager().registerEvents(new OreveilWorldGenerationListener(worldGenerationService), this);
        getServer().getPluginManager().registerEvents(
            new OreveilPlayerListener(this, chunkPrimer, obfuscationService, this::oreveilConfig),
            this
        );
        getServer().getPluginManager().registerEvents(adminGui, this);
        registerCommands();

        // Handle late enable: sync any players already online when the plugin is loaded
        obfuscationService.resyncAllPlayers(Set.copyOf(ORE_CANDIDATES));
        obfuscationService.resyncNewSaltBlocks();

        getLogger().info("Oreveil enabled with " + oreveilConfig.protectedOres().size() + " protected ore materials.");
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
            metrics = null;
        }
        if (obfuscationService != null) {
            obfuscationService.stop();
        }
        if (worldGenerationService != null) {
            worldGenerationService.stop();
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
        return applyOreveilConfig(configLoader.load(getConfig()));
    }

    private OreveilConfig applyCurrentOreveilConfig() {
        return applyOreveilConfig(configLoader.load(getConfig()));
    }

    private OreveilConfig applyOreveilConfig(OreveilConfig newConfig) {
        OreveilConfig previousConfig = this.oreveilConfig;
        boolean rebuildWorldModel = requiresWorldModelRebuild(previousConfig, newConfig);
        boolean resyncVisibility = requiresVisibilityResync(previousConfig, newConfig);
        this.oreveilConfig = newConfig;

        List<Block> formerSalt = rebuildWorldModel ? worldModel.collectAllSaltBlocks() : List.of();

        if (rebuildWorldModel) {
            this.worldModel.reload(newConfig);
        } else {
            this.worldModel.updateConfig(newConfig);
        }
        this.exposureService.reload(newConfig);
        this.obfuscationService.reload(newConfig);
        this.worldGenerationService.reload(newConfig);

        if (resyncVisibility) {
            this.obfuscationService.resyncAllPlayers(Set.copyOf(ORE_CANDIDATES));
            this.obfuscationService.resyncBlocks(formerSalt);
            if (rebuildWorldModel) {
                this.obfuscationService.resyncNewSaltBlocks();
            }
        }

        return newConfig;
    }

    private static boolean requiresWorldModelRebuild(OreveilConfig oldConfig, OreveilConfig newConfig) {
        return oldConfig == null
            || oldConfig.saltedDistributionEnabled() != newConfig.saltedDistributionEnabled()
            || oldConfig.saltDensity() != newConfig.saltDensity()
            || oldConfig.saltSecret() != newConfig.saltSecret()
            || oldConfig.xrayProfile() != newConfig.xrayProfile()
            || !oldConfig.protectedOres().equals(newConfig.protectedOres())
            || oldConfig.revealNextToNonOccludingBlocks() != newConfig.revealNextToNonOccludingBlocks()
            || !oldConfig.revealAdjacentMaterials().equals(newConfig.revealAdjacentMaterials())
            || !oldConfig.revealTransparentMaterials().equals(newConfig.revealTransparentMaterials());
    }

    private static boolean requiresVisibilityResync(OreveilConfig oldConfig, OreveilConfig newConfig) {
        return oldConfig == null
            || requiresWorldModelRebuild(oldConfig, newConfig)
            || oldConfig.obfuscationEnabled() != newConfig.obfuscationEnabled()
            || oldConfig.revealOnExposure() != newConfig.revealOnExposure()
            || oldConfig.exposedOreRevealChunkRadius() != newConfig.exposedOreRevealChunkRadius()
            || !oldConfig.transportMode().equalsIgnoreCase(newConfig.transportMode())
            || !oldConfig.dimensionDefaults().equals(newConfig.dimensionDefaults())
            || !oldConfig.oreOverrides().equals(newConfig.oreOverrides());
    }

    public OreveilConfig resetOreveilConfigToDefaults() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (configFile.exists()) {
            File backupFile = new File(
                getDataFolder(),
                "config.yml.backup-" + CONFIG_BACKUP_SUFFIX.format(LocalDateTime.now())
            );
            try {
                Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Backed up Oreveil config to " + backupFile.getName() + ".");
            } catch (IOException exception) {
                getLogger().warning("Could not back up Oreveil config before reset: " + exception.getMessage());
            }
        }

        saveResource("config.yml", true);
        return reloadOreveilConfig();
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

    public AuthoritativeWorldModel worldModel() {
        return worldModel;
    }

    public OreveilWorldGenerationService worldGenerationService() {
        return worldGenerationService;
    }

    public AuthoritativeWorldModel.CacheStats cacheStats() {
        return worldModel.cacheStats();
    }

    public String compatibilityAdapterName() {
        return compatibility.adapterName();
    }

    public void openAdminGui(Player player) {
        adminGui.open(player);
    }

    public List<Material> candidateOreMaterials() {
        List<Material> ores = new ArrayList<>(ORE_CANDIDATES);
        ores.sort(Comparator.comparing(Enum::name));
        return ores;
    }

    public void toggleGlobalProtectedOre(Material material) {
        toggleMaterialListEntry("protected-ores", material);
    }

    public OreveilConfig toggleMaterialListEntry(String path, Material material) {
        List<String> values = new ArrayList<>(getConfig().getStringList(path));
        String target = material.name();
        if (!values.removeIf(entry -> entry.equalsIgnoreCase(target))) {
            values.add(target);
        }
        values.sort(String::compareToIgnoreCase);
        getConfig().set(path, values);
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig setMaterialListEntry(String path, Material material, boolean enabled) {
        List<String> values = new ArrayList<>(getConfig().getStringList(path));
        String target = material.name();
        values.removeIf(entry -> entry.equalsIgnoreCase(target));
        if (enabled) {
            values.add(target);
        }
        values.sort(String::compareToIgnoreCase);
        getConfig().set(path, values);
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig setConfigMapEntry(String path, String key, String value) {
        getConfig().set(path + "." + key, value);
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig clearConfigMapEntry(String path, String key) {
        getConfig().set(path + "." + key, null);
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig toggleBooleanSetting(String path) {
        getConfig().set(path, !getConfig().getBoolean(path, false));
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig setBooleanSetting(String path, boolean value) {
        getConfig().set(path, value);
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig setIntegerSetting(String path, int value) {
        getConfig().set(path, value);
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig setDoubleSetting(String path, double value) {
        getConfig().set(path, value);
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig setTransportMode(TransportMode mode) {
        getConfig().set("transport.mode", mode.name());
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig setStringSetting(String path, String value) {
        getConfig().set(path, value);
        saveConfig();
        return applyCurrentOreveilConfig();
    }

    public OreveilConfig setNullableLongSetting(String path, Long value) {
        getConfig().set(path, value);
        saveConfig();
        return applyCurrentOreveilConfig();
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
