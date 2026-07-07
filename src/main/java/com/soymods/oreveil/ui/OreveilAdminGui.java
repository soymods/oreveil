package com.soymods.oreveil.ui;

import com.soymods.oreveil.bootstrap.OreveilPlugin;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.config.OreveilWorldGenerationConfig;
import com.soymods.oreveil.config.XrayProfile;
import com.soymods.oreveil.obfuscation.ObfuscationMetrics;
import com.soymods.oreveil.obfuscation.transport.TransportMode;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import com.soymods.oreveil.world.OreveilWorldGenerationService.WorldRegenerationResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class OreveilAdminGui implements Listener {
    private static final TextColor TITLE = TextColor.color(0x7EA7FF);
    private static final TextColor ORES = TextColor.color(0x57D18C);
    private static final TextColor CONTROLS = TextColor.color(0xF4B942);
    private static final TextColor WORLD = TextColor.color(0xE56B6F);
    private static final TextColor MUTED = TextColor.color(0x9A9A9A);
    private static final TextColor ERROR = TextColor.color(0xFF6B6B);
    private static final int MATERIAL_PAGE_SIZE = 45;
    private static final int[] ORE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32};
    private static final List<Material> HOST_CHOICES = List.of(Material.STONE, Material.DEEPSLATE, Material.NETHERRACK, Material.END_STONE);

    private final OreveilPlugin plugin;
    private final Map<UUID, PendingSignInput> signInputs = new HashMap<>();

    public OreveilAdminGui(OreveilPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        open(player, Screen.MAIN);
    }

    private void open(Player player, Screen screen) {
        open(player, screen, 0);
    }

    private void open(Player player, Screen screen, int page) {
        open(player, screen, page, null);
    }

    private void open(Player player, Screen screen, int page, Material target) {
        OreveilGuiHolder holder = new OreveilGuiHolder(screen, Math.max(0, page), target);
        Inventory inventory = Bukkit.createInventory(holder, screen.size(), Component.text(screen.title(), TITLE));
        holder.setInventory(inventory);
        draw(inventory, screen, holder.page(), holder.target());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof OreveilGuiHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        handleClick(player, holder.screen(), holder.page(), holder.target(), event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof OreveilGuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        PendingSignInput input = signInputs.get(event.getPlayer().getUniqueId());
        if (input == null || !input.matches(event.getBlock())) {
            return;
        }

        event.setCancelled(true);
        signInputs.remove(event.getPlayer().getUniqueId());
        restoreSignInput(input);
        applySignInput(event.getPlayer(), input, event.getLine(0));
    }

    private void draw(Inventory inventory, Screen screen, int page, Material target) {
        inventory.clear();
        fill(inventory, screen.size());
        switch (screen) {
            case MAIN -> drawMain(inventory);
            case ORES -> drawOres(inventory);
            case EXPOSURE -> drawExposure(inventory);
            case EXPOSURE_ADJACENT -> drawExposureMaterials(inventory, ExposureKind.ADJACENT, page);
            case EXPOSURE_TRANSPARENT -> drawExposureMaterials(inventory, ExposureKind.TRANSPARENT, page);
            case RUNTIME -> drawRuntime(inventory);
            case PROFILE -> drawProfiles(inventory);
            case SYNC -> drawSync(inventory);
            case HOSTS -> drawHosts(inventory);
            case ORE_OVERRIDES -> drawOreOverrides(inventory);
            case HOST_OVERRIDE_PICK -> drawHostOverridePick(inventory, target);
            case DIAGNOSTICS -> drawDiagnostics(inventory);
            case WORLD -> drawWorld(inventory);
            case CONFIRM_CREATE -> drawConfirm(inventory, GuiAction.CREATE_WORLD);
            case CONFIRM_REGENERATE -> drawConfirm(inventory, GuiAction.REGENERATE_WORLD);
            case CONFIRM_DELETE -> drawConfirm(inventory, GuiAction.DELETE_WORLD);
            case CONFIRM_DEFAULT -> drawConfirm(inventory, GuiAction.SET_DEFAULT_WORLD);
        }
    }

    private void drawMain(Inventory inventory) {
        OreveilConfig config = plugin.oreveilConfig();
        inventory.setItem(10, item(Material.LEVER, "Runtime", CONTROLS,
            "Obfuscation: " + onOff(config.obfuscationEnabled()),
            "Reveal: " + onOff(config.revealOnExposure()),
            "Transport: " + plugin.obfuscationService().transportName()
        ));
        inventory.setItem(11, item(Material.DIAMOND_ORE, "Protected Ores", ORES,
            config.protectedOres().size() + " ore materials protected",
            "Click to choose which ores Oreveil hides."
        ));
        inventory.setItem(12, item(Material.AMETHYST_SHARD, "Xray Profile", WORLD,
            "Current: " + config.xrayProfile().displayName(),
            "Click to choose the fake-ore behavior preset."
        ));
        inventory.setItem(13, item(Material.REPEATER, "Sync Settings", CONTROLS,
            "Live radius: " + config.liveSyncRadiusBlocks(),
            "Prime radius: " + config.initialSyncChunkRadius()
        ));
        inventory.setItem(14, item(Material.WATER_BUCKET, "Exposure Rules", CONTROLS,
            "Adjacent: " + config.revealAdjacentMaterials().size() + " materials",
            "Transparent: " + config.revealTransparentMaterials().size() + " materials"
        ));
        inventory.setItem(15, item(Material.STONE, "Host Blocks", WORLD,
            "Overworld: " + config.resolveDimensionDefault(World.Environment.NORMAL).name(),
            "Nether: " + config.resolveDimensionDefault(World.Environment.NETHER).name(),
            "End: " + config.resolveDimensionDefault(World.Environment.THE_END).name()
        ));
        inventory.setItem(16, item(Material.ANVIL, "Ore Overrides", WORLD,
            config.oreOverridesView().size() + " configured",
            "Choose per-ore host block overrides."
        ));
        inventory.setItem(21, item(Material.SPYGLASS, "Diagnostics", TITLE,
            "Packet rewrite counters",
            "Cache and fake-ore index status"
        ));
        inventory.setItem(22, item(Material.CHEST, "Reload Config", TITLE,
            "Reload Oreveil config and runtime state."
        ));
        inventory.setItem(23, item(Material.MAP, "Managed World", WORLD,
            "Target: " + config.worldGeneration().targetWorldName(),
            "Generation: " + onOff(config.worldGeneration().enabled())
        ));
    }

    private void drawOres(Inventory inventory) {
        List<Material> ores = plugin.candidateOreMaterials();
        for (int index = 0; index < Math.min(ores.size(), ORE_SLOTS.length); index++) {
            Material material = ores.get(index);
            boolean protectedOre = plugin.oreveilConfig().protectedOres().contains(material);
            inventory.setItem(ORE_SLOTS[index], item(
                material,
                displayMaterial(material),
                protectedOre ? ORES : MUTED,
                protectedOre,
                "Status: " + (protectedOre ? "protected" : "ignored"),
                "Click to toggle this ore."
            ));
        }
        back(inventory, 49);
    }

    private void drawExposure(Inventory inventory) {
        OreveilConfig config = plugin.oreveilConfig();
        inventory.setItem(11, item(Material.WATER_BUCKET, "Adjacent Materials", CONTROLS,
            config.revealAdjacentMaterials().size() + " materials reveal protected ores",
            "when touching a protected ore.",
            "Click to edit this list."
        ));
        inventory.setItem(15, item(Material.GLASS, "Transparent Materials", CONTROLS,
            config.revealTransparentMaterials().size() + " materials reveal protected ores",
            "through transparency rules.",
            "Click to edit this list."
        ));
        back(inventory, 22);
    }

    private void drawExposureMaterials(Inventory inventory, ExposureKind kind, int page) {
        List<Material> materials = exposureMaterials(kind);
        int start = page * MATERIAL_PAGE_SIZE;
        for (int slot = 0; slot < MATERIAL_PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= materials.size()) {
                break;
            }

            Material material = materials.get(index);
            boolean selected = exposureSelected(kind, material);
            inventory.setItem(slot, item(
                iconForMaterial(material),
                displayMaterial(material),
                selected ? CONTROLS : MUTED,
                selected,
                "Status: " + (selected ? "reveals ore" : "ignored"),
                "Click to toggle this material."
            ));
        }

        back(inventory, 45);
        if (page > 0) {
            inventory.setItem(48, item(Material.ARROW, "Previous Page", TITLE, "Page " + page + "."));
        }
        inventory.setItem(49, item(Material.BOOK, kind.displayName(), CONTROLS,
            "Page " + (page + 1) + " of " + Math.max(1, (int) Math.ceil(materials.size() / (double) MATERIAL_PAGE_SIZE)),
            materials.size() + " block materials available."
        ));
        if (start + MATERIAL_PAGE_SIZE < materials.size()) {
            inventory.setItem(50, item(Material.ARROW, "Next Page", TITLE, "Page " + (page + 2) + "."));
        }
    }

    private void drawRuntime(Inventory inventory) {
        OreveilConfig config = plugin.oreveilConfig();
        inventory.setItem(10, toggle(Material.LEVER, "Obfuscation", config.obfuscationEnabled(), "Master runtime obfuscation switch."));
        inventory.setItem(11, toggle(Material.REDSTONE_TORCH, "Reveal On Exposure", config.revealOnExposure(), "Show protected ores after normal gameplay exposes them."));
        inventory.setItem(12, toggle(Material.GLASS, "Non-Occluding Reveal", config.revealNextToNonOccludingBlocks(), "Treat non-occluding neighbors as exposure sources."));
        inventory.setItem(14, toggle(Material.ECHO_SHARD, "Salted Distribution", config.saltedDistributionEnabled(), "Use server-private fake ore signals."));
        inventory.setItem(15, toggle(Material.GRASS_BLOCK, "Managed World Generation", config.worldGeneration().enabled(), "Enable Oreveil managed-world features."));
        inventory.setItem(16, item(Material.COMPARATOR, "Transport", TITLE,
            "Current: " + TransportMode.fromConfig(config.transportMode()).name(),
            "Click to cycle transport mode."
        ));
        back(inventory, 22);
    }

    private void drawProfiles(Inventory inventory) {
        int[] slots = {10, 12, 14, 16};
        XrayProfile selected = plugin.oreveilConfig().xrayProfile();
        for (int i = 0; i < XrayProfile.values().length; i++) {
            XrayProfile profile = XrayProfile.values()[i];
            boolean active = profile == selected;
            inventory.setItem(slots[i], item(
                profileIcon(profile),
                profile.displayName(),
                active ? WORLD : MUTED,
                active,
                "Status: " + (active ? "selected" : "available"),
                "Budget: " + Math.round(profile.saltDensityMultiplier() * 100.0D) + "%",
                "Common: " + Math.round(profile.commonOreWeightMultiplier() * 100.0D) + "%  Rare: " + Math.round(profile.rareOreWeightMultiplier() * 100.0D) + "%",
                "Click to select this profile."
            ));
        }
        back(inventory, 22);
    }

    private void drawSync(Inventory inventory) {
        OreveilConfig config = plugin.oreveilConfig();
        numericRow(inventory, 10, "Live Sync Radius", Material.REPEATER, config.liveSyncRadiusBlocks(), 16);
        numericRow(inventory, 19, "Chunk Prime Radius", Material.COMPARATOR, config.initialSyncChunkRadius(), 1);
        numericRow(inventory, 28, "Exposed Refresh Radius", Material.REDSTONE, config.revealProximityBlocks(), 8);
        back(inventory, 49);
    }

    private void drawHosts(Inventory inventory) {
        OreveilConfig config = plugin.oreveilConfig();
        host(inventory, 10, World.Environment.NORMAL, Material.GRASS_BLOCK, config.resolveDimensionDefault(World.Environment.NORMAL));
        host(inventory, 13, World.Environment.NETHER, Material.NETHERRACK, config.resolveDimensionDefault(World.Environment.NETHER));
        host(inventory, 16, World.Environment.THE_END, Material.END_STONE, config.resolveDimensionDefault(World.Environment.THE_END));
        inventory.setItem(22, item(Material.ANVIL, "Ore Overrides", MUTED,
            config.oreOverridesView().size() + " configured",
            "Click to edit per-ore host blocks."
        ));
        back(inventory, 31);
    }

    private void drawOreOverrides(Inventory inventory) {
        OreveilConfig config = plugin.oreveilConfig();
        List<Material> ores = plugin.candidateOreMaterials();
        for (int index = 0; index < Math.min(ores.size(), ORE_SLOTS.length); index++) {
            Material ore = ores.get(index);
            Material override = config.resolveOreOverride(ore);
            Material current = override != null ? override : config.resolveDimensionDefault(defaultEnvironmentForOre(ore));
            inventory.setItem(ORE_SLOTS[index], item(
                ore,
                displayMaterial(ore),
                override != null ? WORLD : MUTED,
                override != null,
                "Host: " + current.name(),
                "Mode: " + (override != null ? "ore override" : "dimension default"),
                "Click to edit."
            ));
        }
        back(inventory, 49);
    }

    private void drawHostOverridePick(Inventory inventory, Material ore) {
        if (ore == null) {
            inventory.setItem(13, item(Material.BARRIER, "No Ore Selected", ERROR, "Return and choose an ore first."));
            back(inventory, 22);
            return;
        }

        OreveilConfig config = plugin.oreveilConfig();
        Material override = config.resolveOreOverride(ore);
        Material inherited = config.resolveDimensionDefault(defaultEnvironmentForOre(ore));
        inventory.setItem(4, item(ore, displayMaterial(ore), WORLD,
            "Override: " + (override == null ? "none" : override.name()),
            "Default host: " + inherited.name()
        ));

        int[] slots = {10, 11, 12, 13};
        for (int i = 0; i < HOST_CHOICES.size(); i++) {
            Material host = HOST_CHOICES.get(i);
            boolean selected = host == override;
            inventory.setItem(slots[i], item(host, displayMaterial(host), selected ? WORLD : MUTED, selected,
                selected ? "Currently selected." : "Click to use this host block."
            ));
        }
        inventory.setItem(15, item(Material.BARRIER, "Clear Override", ERROR,
            "Use the dimension default instead."
        ));
        back(inventory, 22);
    }

    private void drawDiagnostics(Inventory inventory) {
        OreveilConfig config = plugin.oreveilConfig();
        ObfuscationMetrics.Snapshot metrics = plugin.obfuscationService().metricsSnapshot();
        AuthoritativeWorldModel.CacheStats cache = plugin.cacheStats();
        inventory.setItem(10, item(Material.SPYGLASS, "Transport", TITLE,
            "Active: " + plugin.obfuscationService().transportName(),
            "Chunk delivery: " + plugin.obfuscationService().handlesChunkDelivery()
        ));
        inventory.setItem(12, item(Material.COMPARATOR, "Packet Rewrites", TITLE,
            "Block packets: " + metrics.blockChangePacketsRewritten(),
            "Multi packets: " + metrics.multiBlockPacketsRewritten(),
            "Multi entries: " + metrics.multiBlockEntriesRewritten()
        ));
        inventory.setItem(14, item(Material.DIAMOND_ORE, "Ore Cache", ORES,
            "Ore chunks: " + cache.protectedOreChunks(),
            "Ore blocks: " + cache.protectedOreBlocks(),
            "Salt chunks: " + cache.saltChunks(),
            "Salt blocks: " + cache.saltBlocks()
        ));
        inventory.setItem(16, item(Material.AMETHYST_SHARD, "Xray Model", WORLD,
            "Profile: " + config.xrayProfile().configName(),
            "Salt budget: " + config.xrayProfile().effectiveSaltBudget(config.saltDensity()),
            "Rare cap: " + config.xrayProfile().maxRareOreBlocks(config.xrayProfile().effectiveSaltBudget(config.saltDensity()))
        ));
        inventory.setItem(22, item(Material.CHEST, "Refresh", TITLE, "Click to refresh diagnostics."));
        back(inventory, 26);
    }

    private void drawWorld(Inventory inventory) {
        OreveilWorldGenerationConfig world = plugin.oreveilConfig().worldGeneration();
        inventory.setItem(10, toggle(Material.GRASS_BLOCK, "World Generation", world.enabled(), "Enable managed-world features."));
        inventory.setItem(11, toggle(Material.TNT, "Experimental", world.experimental(), "Allow experimental managed-world behavior."));
        inventory.setItem(12, toggle(Material.CHEST, "Backup On Regenerate", world.backupOnRegenerate(), "Back up the world folder before regeneration."));
        inventory.setItem(13, toggle(Material.STRUCTURE_BLOCK, "Generate Structures", world.generateStructures(), "Use vanilla structure generation."));
        inventory.setItem(15, item(Material.MAP, "Target World", WORLD,
            world.targetWorldName(),
            "Use /oreveil world target <name> to change it."
        ));
        inventory.setItem(16, item(Material.ENDER_PEARL, "Teleport", WORLD,
            "Teleport to " + world.targetWorldName() + ".",
            "Requires the managed world to be loaded."
        ));
        inventory.setItem(19, item(Material.LIME_CONCRETE, "Create Managed World", ORES,
            "Create " + world.targetWorldName() + ".",
            "Requires generation and experimental mode."
        ));
        inventory.setItem(20, item(Material.ORANGE_CONCRETE, "Regenerate Managed World", CONTROLS,
            "Recreate " + world.targetWorldName() + ".",
            "Confirmation required."
        ));
        inventory.setItem(21, item(Material.RED_CONCRETE, "Delete Managed World", ERROR,
            "Delete " + world.targetWorldName() + ".",
            "Confirmation required."
        ));
        inventory.setItem(24, item(Material.COMPASS, "Set As Default", WORLD,
            "Use " + world.targetWorldName() + " as the server default",
            "after the next restart. Confirmation required."
        ));
        back(inventory, 22);
    }

    private void drawConfirm(Inventory inventory, GuiAction action) {
        OreveilWorldGenerationConfig world = plugin.oreveilConfig().worldGeneration();
        inventory.setItem(11, item(action.icon(), action.title(), action.color(),
            action.warning(world.targetWorldName()),
            "Click confirm to continue."
        ));
        inventory.setItem(15, item(Material.LIME_CONCRETE, "Confirm", ORES, true, action.confirmLore(world.targetWorldName())));
        inventory.setItem(22, item(Material.BARRIER, "Cancel", ERROR, "Return to managed-world controls."));
    }

    private void handleClick(Player player, Screen screen, int page, Material target, int slot) {
        switch (screen) {
            case MAIN -> handleMain(player, slot);
            case ORES -> handleOres(player, slot);
            case EXPOSURE -> handleExposure(player, slot);
            case EXPOSURE_ADJACENT -> handleExposureMaterials(player, ExposureKind.ADJACENT, page, slot);
            case EXPOSURE_TRANSPARENT -> handleExposureMaterials(player, ExposureKind.TRANSPARENT, page, slot);
            case RUNTIME -> handleRuntime(player, slot);
            case PROFILE -> handleProfiles(player, slot);
            case SYNC -> handleSync(player, slot);
            case HOSTS -> handleHosts(player, slot);
            case ORE_OVERRIDES -> handleOreOverrides(player, slot);
            case HOST_OVERRIDE_PICK -> handleHostOverridePick(player, target, slot);
            case DIAGNOSTICS -> {
                if (slot == 22) {
                    open(player, Screen.DIAGNOSTICS);
                } else if (slot == 26) {
                    open(player, Screen.MAIN);
                }
            }
            case WORLD -> handleWorld(player, slot);
            case CONFIRM_CREATE -> handleConfirm(player, slot, GuiAction.CREATE_WORLD);
            case CONFIRM_REGENERATE -> handleConfirm(player, slot, GuiAction.REGENERATE_WORLD);
            case CONFIRM_DELETE -> handleConfirm(player, slot, GuiAction.DELETE_WORLD);
            case CONFIRM_DEFAULT -> handleConfirm(player, slot, GuiAction.SET_DEFAULT_WORLD);
        }
    }

    private void handleMain(Player player, int slot) {
        switch (slot) {
            case 10 -> open(player, Screen.RUNTIME);
            case 11 -> open(player, Screen.ORES);
            case 12 -> open(player, Screen.PROFILE);
            case 13 -> open(player, Screen.SYNC);
            case 14 -> open(player, Screen.EXPOSURE);
            case 15 -> open(player, Screen.HOSTS);
            case 16 -> open(player, Screen.ORE_OVERRIDES);
            case 21 -> open(player, Screen.DIAGNOSTICS);
            case 22 -> {
                plugin.reloadOreveilConfig();
                feedback(player, "Oreveil config reloaded.");
                open(player, Screen.MAIN);
            }
            case 23 -> open(player, Screen.WORLD);
            default -> {
            }
        }
    }

    private void handleExposure(Player player, int slot) {
        switch (slot) {
            case 11 -> open(player, Screen.EXPOSURE_ADJACENT);
            case 15 -> open(player, Screen.EXPOSURE_TRANSPARENT);
            case 22 -> open(player, Screen.MAIN);
            default -> {
            }
        }
    }

    private void handleExposureMaterials(Player player, ExposureKind kind, int page, int slot) {
        if (slot == 45) {
            open(player, Screen.EXPOSURE);
            return;
        }
        if (slot == 48 && page > 0) {
            open(player, kind.screen(), page - 1);
            return;
        }

        List<Material> materials = exposureMaterials(kind);
        if (slot == 50 && (page + 1) * MATERIAL_PAGE_SIZE < materials.size()) {
            open(player, kind.screen(), page + 1);
            return;
        }
        if (slot < 0 || slot >= MATERIAL_PAGE_SIZE) {
            return;
        }

        int index = page * MATERIAL_PAGE_SIZE + slot;
        if (index >= materials.size()) {
            return;
        }

        Material material = materials.get(index);
        plugin.toggleMaterialListEntry(kind.path(), material);
        feedback(player, displayMaterial(material) + " toggled.");
        open(player, kind.screen(), page);
    }

    private void handleOres(Player player, int slot) {
        if (slot == 49) {
            open(player, Screen.MAIN);
            return;
        }
        int index = slotIndex(ORE_SLOTS, slot);
        List<Material> ores = plugin.candidateOreMaterials();
        if (index >= 0 && index < ores.size()) {
            plugin.toggleGlobalProtectedOre(ores.get(index));
            feedback(player, displayMaterial(ores.get(index)) + " toggled.");
            open(player, Screen.ORES);
        }
    }

    private void handleRuntime(Player player, int slot) {
        switch (slot) {
            case 10 -> plugin.toggleBooleanSetting("obfuscation.enabled");
            case 11 -> plugin.toggleBooleanSetting("obfuscation.reveal-on-exposure");
            case 12 -> plugin.toggleBooleanSetting("obfuscation.reveal-next-to-non-occluding-blocks");
            case 14 -> plugin.toggleBooleanSetting("world-model.salted-distribution");
            case 15 -> plugin.toggleBooleanSetting("world-generation.enabled");
            case 16 -> plugin.setTransportMode(nextTransport());
            case 22 -> {
                open(player, Screen.MAIN);
                return;
            }
            default -> {
                return;
            }
        }
        feedback(player, "Runtime setting updated.");
        open(player, Screen.RUNTIME);
    }

    private void handleProfiles(Player player, int slot) {
        int[] slots = {10, 12, 14, 16};
        int index = slotIndex(slots, slot);
        if (index >= 0 && index < XrayProfile.values().length) {
            plugin.setStringSetting("world-model.xray-profile", XrayProfile.values()[index].configName());
            feedback(player, "Xray profile set to " + XrayProfile.values()[index].displayName() + ".");
            open(player, Screen.PROFILE);
            return;
        }
        if (slot == 22) {
            open(player, Screen.MAIN);
        }
    }

    private void handleSync(Player player, int slot) {
        switch (slot) {
            case 10 -> adjustInteger("obfuscation.live-sync-radius-blocks", plugin.oreveilConfig().liveSyncRadiusBlocks(), -16, 16, 256);
            case 11 -> openSignInput(player, new PendingSignInput(
                "obfuscation.live-sync-radius-blocks",
                "Live Sync Radius",
                plugin.oreveilConfig().liveSyncRadiusBlocks(),
                16,
                256
            ));
            case 12 -> adjustInteger("obfuscation.live-sync-radius-blocks", plugin.oreveilConfig().liveSyncRadiusBlocks(), 16, 16, 256);
            case 19 -> adjustInteger("obfuscation.initial-sync-chunk-radius", plugin.oreveilConfig().initialSyncChunkRadius(), -1, 0, 8);
            case 20 -> openSignInput(player, new PendingSignInput(
                "obfuscation.initial-sync-chunk-radius",
                "Chunk Prime Radius",
                plugin.oreveilConfig().initialSyncChunkRadius(),
                0,
                8
            ));
            case 21 -> adjustInteger("obfuscation.initial-sync-chunk-radius", plugin.oreveilConfig().initialSyncChunkRadius(), 1, 0, 8);
            case 28 -> adjustInteger("obfuscation.reveal-proximity-blocks", plugin.oreveilConfig().revealProximityBlocks(), -8, 0, 96);
            case 29 -> openSignInput(player, new PendingSignInput(
                "obfuscation.reveal-proximity-blocks",
                "Exposed Refresh Radius",
                plugin.oreveilConfig().revealProximityBlocks(),
                0,
                96
            ));
            case 30 -> adjustInteger("obfuscation.reveal-proximity-blocks", plugin.oreveilConfig().revealProximityBlocks(), 8, 0, 96);
            case 49 -> {
                open(player, Screen.MAIN);
                return;
            }
            default -> {
                return;
            }
        }
        if (slot == 11 || slot == 20 || slot == 29) {
            return;
        }
        feedback(player, "Sync setting updated.");
        open(player, Screen.SYNC);
    }

    private void handleHosts(Player player, int slot) {
        switch (slot) {
            case 10 -> cycleHost(World.Environment.NORMAL);
            case 13 -> cycleHost(World.Environment.NETHER);
            case 16 -> cycleHost(World.Environment.THE_END);
            case 22 -> {
                open(player, Screen.ORE_OVERRIDES);
                return;
            }
            case 31 -> {
                open(player, Screen.MAIN);
                return;
            }
            default -> {
                return;
            }
        }
        feedback(player, "Host block updated.");
        open(player, Screen.HOSTS);
    }

    private void handleOreOverrides(Player player, int slot) {
        if (slot == 49) {
            open(player, Screen.MAIN);
            return;
        }

        int index = slotIndex(ORE_SLOTS, slot);
        List<Material> ores = plugin.candidateOreMaterials();
        if (index >= 0 && index < ores.size()) {
            open(player, Screen.HOST_OVERRIDE_PICK, 0, ores.get(index));
        }
    }

    private void handleHostOverridePick(Player player, Material ore, int slot) {
        if (slot == 22) {
            open(player, Screen.ORE_OVERRIDES);
            return;
        }
        if (ore == null) {
            return;
        }

        int[] slots = {10, 11, 12, 13};
        int index = slotIndex(slots, slot);
        if (index >= 0 && index < HOST_CHOICES.size()) {
            Material host = HOST_CHOICES.get(index);
            plugin.setConfigMapEntry("host-blocks.ore-overrides", ore.name(), host.name());
            feedback(player, displayMaterial(ore) + " host set to " + host.name() + ".");
            open(player, Screen.ORE_OVERRIDES);
            return;
        }
        if (slot == 15) {
            plugin.clearConfigMapEntry("host-blocks.ore-overrides", ore.name());
            feedback(player, displayMaterial(ore) + " override cleared.");
            open(player, Screen.ORE_OVERRIDES);
        }
    }

    private void handleWorld(Player player, int slot) {
        switch (slot) {
            case 10 -> plugin.toggleBooleanSetting("world-generation.enabled");
            case 11 -> plugin.toggleBooleanSetting("world-generation.experimental");
            case 12 -> plugin.toggleBooleanSetting("world-generation.backup-on-regenerate");
            case 13 -> plugin.toggleBooleanSetting("world-generation.generate-structures");
            case 16 -> {
                teleportManaged(player);
                return;
            }
            case 19 -> {
                open(player, Screen.CONFIRM_CREATE);
                return;
            }
            case 20 -> {
                open(player, Screen.CONFIRM_REGENERATE);
                return;
            }
            case 21 -> {
                open(player, Screen.CONFIRM_DELETE);
                return;
            }
            case 24 -> {
                open(player, Screen.CONFIRM_DEFAULT);
                return;
            }
            case 22 -> {
                open(player, Screen.MAIN);
                return;
            }
            default -> {
                return;
            }
        }
        feedback(player, "Managed-world setting updated.");
        open(player, Screen.WORLD);
    }

    private void handleConfirm(Player player, int slot, GuiAction action) {
        if (slot == 22) {
            open(player, Screen.WORLD);
            return;
        }
        if (slot != 15) {
            return;
        }

        switch (action) {
            case CREATE_WORLD -> runWorldOperation(player, listener -> plugin.worldGenerationService().createManagedWorldAsync(null, 3, listener));
            case REGENERATE_WORLD -> runWorldOperation(player, listener -> plugin.worldGenerationService().regenerateManagedWorldAsync(null, 3, listener));
            case DELETE_WORLD -> {
                WorldRegenerationResult result = plugin.worldGenerationService().deleteWorld(plugin.oreveilConfig().worldGeneration().targetWorldName());
                handleWorldResult(player, result);
            }
            case SET_DEFAULT_WORLD -> {
                WorldRegenerationResult result = plugin.worldGenerationService().setDefaultWorld(plugin.oreveilConfig().worldGeneration().targetWorldName());
                handleWorldResult(player, result);
            }
        }
    }

    private void numericRow(Inventory inventory, int startSlot, String title, Material icon, int value, int step) {
        inventory.setItem(startSlot, item(Material.RED_STAINED_GLASS_PANE, "-" + step, ERROR, "Lower " + title + "."));
        inventory.setItem(startSlot + 1, item(icon, title, CONTROLS, "Current: " + value, "Click for exact sign input."));
        inventory.setItem(startSlot + 2, item(Material.LIME_STAINED_GLASS_PANE, "+" + step, ORES, "Raise " + title + "."));
    }

    private void host(Inventory inventory, int slot, World.Environment environment, Material icon, Material current) {
        inventory.setItem(slot, item(icon, displayEnvironment(environment), WORLD,
            "Current: " + current.name(),
            "Click to cycle host block.",
            "Options: STONE, DEEPSLATE, NETHERRACK, END_STONE"
        ));
    }

    private ItemStack toggle(Material material, String name, boolean enabled, String description) {
        return item(material, name, enabled ? ORES : MUTED,
            enabled,
            "Status: " + onOff(enabled),
            description,
            "Click to toggle."
        );
    }

    private ItemStack item(Material material, String name, TextColor color, String... lore) {
        return item(material, name, color, false, lore);
    }

    private ItemStack item(Material material, String name, TextColor color, boolean active, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, color));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Component.text(line, NamedTextColor.GRAY));
        }
        meta.lore(lines);
        if (active) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private void fill(Inventory inventory, int size) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", MUTED);
        for (int slot = 0; slot < size; slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private void back(Inventory inventory, int slot) {
        inventory.setItem(slot, item(Material.ARROW, "Back", TITLE, "Return to the main Oreveil menu."));
    }

    private void adjustInteger(String path, int current, int delta, int min, int max) {
        int next = Math.max(min, Math.min(max, current + delta));
        plugin.setIntegerSetting(path, next);
    }

    private TransportMode nextTransport() {
        List<TransportMode> modes = Arrays.asList(TransportMode.values());
        TransportMode current = TransportMode.fromConfig(plugin.oreveilConfig().transportMode());
        return modes.get((modes.indexOf(current) + 1) % modes.size());
    }

    private void cycleHost(World.Environment environment) {
        Material current = plugin.oreveilConfig().resolveDimensionDefault(environment);
        int index = HOST_CHOICES.indexOf(current);
        Material next = HOST_CHOICES.get((Math.max(index, 0) + 1) % HOST_CHOICES.size());
        plugin.setConfigMapEntry("host-blocks.dimension-defaults", environment.name(), next.name());
    }

    private void openSignInput(Player player, PendingSignInput input) {
        PendingSignInput previous = signInputs.remove(player.getUniqueId());
        if (previous != null) {
            restoreSignInput(previous);
        }

        Block block = findSignInputBlock(player);
        if (block == null) {
            feedback(player, "No safe air block nearby for sign input.");
            open(player, Screen.SYNC);
            return;
        }

        input.attach(block, block.getBlockData());
        block.setType(Material.OAK_SIGN, false);
        if (!(block.getState() instanceof Sign sign)) {
            restoreSignInput(input);
            feedback(player, "Could not open sign input.");
            open(player, Screen.SYNC);
            return;
        }

        sign.setEditable(true);
        sign.setAllowedEditorUniqueId(player.getUniqueId());
        sign.setLine(0, String.valueOf(input.current()));
        sign.setLine(1, input.title());
        sign.setLine(2, "Min " + input.min() + " Max " + input.max());
        sign.setLine(3, "Edit line 1");
        sign.update(true, false);

        signInputs.put(player.getUniqueId(), input);
        player.closeInventory();
        player.openSign(sign);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            PendingSignInput pending = signInputs.get(player.getUniqueId());
            if (pending == input) {
                signInputs.remove(player.getUniqueId());
                restoreSignInput(input);
            }
        }, 600L);
    }

    private Block findSignInputBlock(Player player) {
        Block origin = player.getLocation().getBlock();
        int[][] offsets = {
            {0, 2, 0},
            {0, 3, 0},
            {1, 2, 0},
            {-1, 2, 0},
            {0, 2, 1},
            {0, 2, -1}
        };
        for (int[] offset : offsets) {
            Block candidate = origin.getRelative(offset[0], offset[1], offset[2]);
            if (candidate.isEmpty()) {
                return candidate;
            }
        }
        return null;
    }

    private void applySignInput(Player player, PendingSignInput input, String raw) {
        String value = raw == null || raw.isBlank() ? String.valueOf(input.current()) : raw.trim();
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            feedback(player, input.title() + " needs a whole number.");
            open(player, Screen.SYNC);
            return;
        }

        int clamped = Math.max(input.min(), Math.min(input.max(), parsed));
        plugin.setIntegerSetting(input.path(), clamped);
        feedback(player, input.title() + " set to " + clamped + ".");
        open(player, Screen.SYNC);
    }

    private void restoreSignInput(PendingSignInput input) {
        if (input.block() != null && input.originalData() != null) {
            input.block().setBlockData(input.originalData(), false);
        }
    }

    private World.Environment defaultEnvironmentForOre(Material material) {
        if (material.name().startsWith("NETHER_") || material == Material.ANCIENT_DEBRIS) {
            return World.Environment.NETHER;
        }
        return World.Environment.NORMAL;
    }

    private void teleportManaged(Player player) {
        String target = plugin.oreveilConfig().worldGeneration().targetWorldName();
        World world = Bukkit.getWorld(target);
        if (world == null) {
            feedback(player, "Managed world is not loaded: " + target);
            open(player, Screen.WORLD);
            return;
        }

        player.closeInventory();
        player.teleportAsync(world.getSpawnLocation());
        feedback(player, "Teleporting to " + world.getName() + ".");
    }

    private void runWorldOperation(Player player, java.util.function.Consumer<OreveilWorldOperationFeedback> operation) {
        player.closeInventory();
        OreveilWorldOperationFeedback feedback = new OreveilWorldOperationFeedback(player);
        operation.accept(feedback);
    }

    private void handleWorldResult(Player player, WorldRegenerationResult result) {
        if (!result.success()) {
            feedback(player, result.message());
            open(player, Screen.WORLD);
            return;
        }

        feedback(player, result.message());
        open(player, Screen.WORLD);
    }

    private void feedback(Player player, String message) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> feedback(player, message));
            return;
        }
        player.sendActionBar(Component.text(message, TITLE));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, 1.1F);
    }

    private int slotIndex(int[] slots, int slot) {
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private Material profileIcon(XrayProfile profile) {
        return switch (profile) {
            case VANILLA_FRIENDLY -> Material.EMERALD;
            case BALANCED -> Material.AMETHYST_SHARD;
            case AGGRESSIVE -> Material.REDSTONE;
            case PERFORMANCE -> Material.FEATHER;
        };
    }

    private List<Material> exposureMaterials(ExposureKind kind) {
        return Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .sorted(Comparator
                .comparing((Material material) -> !exposureSelected(kind, material))
                .thenComparing(Enum::name))
            .toList();
    }

    private boolean exposureSelected(ExposureKind kind, Material material) {
        OreveilConfig config = plugin.oreveilConfig();
        return switch (kind) {
            case ADJACENT -> config.revealAdjacentMaterials().contains(material);
            case TRANSPARENT -> config.revealTransparentMaterials().contains(material);
        };
    }

    private Material iconForMaterial(Material material) {
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            return Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        }
        if (material == Material.WATER) {
            return Material.WATER_BUCKET;
        }
        if (material == Material.LAVA) {
            return Material.LAVA_BUCKET;
        }
        return material.isItem() ? material : Material.STONE;
    }

    private String displayEnvironment(World.Environment environment) {
        return switch (environment) {
            case NORMAL -> "Overworld Host";
            case NETHER -> "Nether Host";
            case THE_END -> "End Host";
            default -> environment.name() + " Host";
        };
    }

    private String displayMaterial(Material material) {
        String lower = material.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (char c : lower.toCharArray()) {
            if (c == ' ') {
                capitalize = true;
                result.append(c);
            } else if (capitalize) {
                result.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private enum Screen {
        MAIN(27, "Oreveil"),
        ORES(54, "Oreveil - Protected Ores"),
        EXPOSURE(27, "Oreveil - Exposure"),
        EXPOSURE_ADJACENT(54, "Oreveil - Adjacent"),
        EXPOSURE_TRANSPARENT(54, "Oreveil - Transparent"),
        RUNTIME(27, "Oreveil - Runtime"),
        PROFILE(27, "Oreveil - Xray Profile"),
        SYNC(54, "Oreveil - Sync"),
        HOSTS(36, "Oreveil - Host Blocks"),
        ORE_OVERRIDES(54, "Oreveil - Ore Overrides"),
        HOST_OVERRIDE_PICK(27, "Oreveil - Host Override"),
        DIAGNOSTICS(27, "Oreveil - Diagnostics"),
        WORLD(27, "Oreveil - Managed World"),
        CONFIRM_CREATE(27, "Oreveil - Confirm Create"),
        CONFIRM_REGENERATE(27, "Oreveil - Confirm Regenerate"),
        CONFIRM_DELETE(27, "Oreveil - Confirm Delete"),
        CONFIRM_DEFAULT(27, "Oreveil - Confirm Default");

        private final int size;
        private final String title;

        Screen(int size, String title) {
            this.size = size;
            this.title = title;
        }

        int size() {
            return size;
        }

        String title() {
            return title;
        }
    }

    private enum ExposureKind {
        ADJACENT("Adjacent Materials", "exposure.reveal-adjacent-materials", Screen.EXPOSURE_ADJACENT),
        TRANSPARENT("Transparent Materials", "exposure.reveal-transparent-materials", Screen.EXPOSURE_TRANSPARENT);

        private final String displayName;
        private final String path;
        private final Screen screen;

        ExposureKind(String displayName, String path, Screen screen) {
            this.displayName = displayName;
            this.path = path;
            this.screen = screen;
        }

        String displayName() {
            return displayName;
        }

        String path() {
            return path;
        }

        Screen screen() {
            return screen;
        }
    }

    private enum GuiAction {
        CREATE_WORLD(Material.LIME_CONCRETE, "Create Managed World", ORES),
        REGENERATE_WORLD(Material.ORANGE_CONCRETE, "Regenerate Managed World", CONTROLS),
        DELETE_WORLD(Material.RED_CONCRETE, "Delete Managed World", ERROR),
        SET_DEFAULT_WORLD(Material.COMPASS, "Set Default World", WORLD);

        private final Material icon;
        private final String title;
        private final TextColor color;

        GuiAction(Material icon, String title, TextColor color) {
            this.icon = icon;
            this.title = title;
            this.color = color;
        }

        Material icon() {
            return icon;
        }

        String title() {
            return title;
        }

        TextColor color() {
            return color;
        }

        String warning(String worldName) {
            return switch (this) {
                case CREATE_WORLD -> "Create managed world " + worldName + ".";
                case REGENERATE_WORLD -> "Regenerate managed world " + worldName + ".";
                case DELETE_WORLD -> "Delete managed world " + worldName + ".";
                case SET_DEFAULT_WORLD -> "Set " + worldName + " as the default world.";
            };
        }

        String confirmLore(String worldName) {
            return switch (this) {
                case CREATE_WORLD -> "Create " + worldName + " now.";
                case REGENERATE_WORLD -> "This rotates the old world folder first.";
                case DELETE_WORLD -> "This removes the managed world folder.";
                case SET_DEFAULT_WORLD -> "This updates server.properties for restart.";
            };
        }
    }

    private static final class PendingSignInput {
        private final String path;
        private final String title;
        private final int current;
        private final int min;
        private final int max;
        private Block block;
        private BlockData originalData;

        private PendingSignInput(String path, String title, int current, int min, int max) {
            this.path = path;
            this.title = title;
            this.current = current;
            this.min = min;
            this.max = max;
        }

        private void attach(Block block, BlockData originalData) {
            this.block = block;
            this.originalData = originalData;
        }

        private boolean matches(Block block) {
            return this.block != null && this.block.getWorld().equals(block.getWorld()) && this.block.getLocation().equals(block.getLocation());
        }

        private String path() {
            return path;
        }

        private String title() {
            return title;
        }

        private int current() {
            return current;
        }

        private int min() {
            return min;
        }

        private int max() {
            return max;
        }

        private Block block() {
            return block;
        }

        private BlockData originalData() {
            return originalData;
        }
    }

    private final class OreveilWorldOperationFeedback implements com.soymods.oreveil.world.OreveilWorldGenerationService.WorldOperationListener {
        private final Player player;

        private OreveilWorldOperationFeedback(Player player) {
            this.player = player;
        }

        @Override
        public void onStage(String message) {
            feedback(player, message);
        }

        @Override
        public void onProgress(int completed, int total) {
            if (completed == 1 || completed == total || completed % Math.max(1, total / 4) == 0) {
                feedback(player, "Spawn area progress: " + completed + "/" + total + ".");
            }
        }

        @Override
        public void onComplete(WorldRegenerationResult result) {
            Bukkit.getScheduler().runTask(plugin, () -> handleWorldResult(player, result));
        }
    }

    private static final class OreveilGuiHolder implements InventoryHolder {
        private final Screen screen;
        private final Material target;
        private final int page;
        private Inventory inventory;

        private OreveilGuiHolder(Screen screen, int page, Material target) {
            this.screen = screen;
            this.page = page;
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private Screen screen() {
            return screen;
        }

        private int page() {
            return page;
        }

        private Material target() {
            return target;
        }
    }
}
