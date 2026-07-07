package com.soymods.oreveil.command;

import com.soymods.oreveil.bootstrap.OreveilPlugin;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.config.OreveilWorldGenerationConfig;
import com.soymods.oreveil.config.XrayProfile;
import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.ObfuscationMetrics;
import com.soymods.oreveil.obfuscation.transport.TransportMode;
import com.soymods.oreveil.world.AuthoritativeWorldModel;
import com.soymods.oreveil.world.OreveilWorldGenerationService;
import com.soymods.oreveil.world.OreveilWorldGenerationService.WorldRegenerationResult;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class OreveilCommand implements CommandExecutor, TabCompleter {
    private static final TextColor BASE = NamedTextColor.GRAY;
    private static final TextColor STATUS = TextColor.color(0x7EA7FF);
    private static final TextColor ORES = TextColor.color(0x57D18C);
    private static final TextColor CONTROLS = TextColor.color(0xF4B942);
    private static final TextColor WORLD = TextColor.color(0xE56B6F);
    private static final TextColor ERROR = TextColor.color(0xFF6B6B);
    private static final TextColor ACTIVE = NamedTextColor.WHITE;
    private static final TextColor MUTED = TextColor.color(0x9A9A9A);

    private final OreveilPlugin plugin;

    public OreveilCommand(OreveilPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                plugin.openAdminGui(player);
            } else {
                sendHelp(sender, label, args);
            }
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "gui", "menu" -> handleGui(sender);
            case "help" -> {
                sendHelp(sender, label, args);
                yield true;
            }
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender);
            case "status" -> handleStatus(sender, label);
            case "diagnostics", "diag" -> handleDiagnostics(sender);
            case "settings" -> handleSettings(sender, label, args);
            case "get" -> handleGet(sender, label, args);
            case "explain" -> handleExplain(sender, label, args);
            case "ores" -> {
                sendOreMenu(sender);
                yield true;
            }
            case "ore" -> handleOreToggle(sender, label, args);
            case "exposure" -> handleExposure(sender, label, args);
            case "host" -> handleHost(sender, label, args);
            case "toggle" -> handleToggle(sender, label, args);
            case "set" -> handleSet(sender, label, args);
            case "transport" -> handleTransport(sender, label, args);
            case "profile" -> handleProfile(sender, label, args);
            case "world" -> handleWorld(sender, label, args);
            default -> {
                sendError(sender, "Unknown subcommand. Use /" + label + " help.");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of(
                "help",
                "gui",
                "reload",
                "inspect",
                "status",
                "diagnostics",
                "settings",
                "get",
                "explain",
                "ores",
                "ore",
                "exposure",
                "host",
                "toggle",
                "set",
                "transport",
                "profile",
                "world"
            ));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return filter(args[1], List.of("settings", "exposure", "host", "ores", "profile", "world", "diagnostics"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ore")) {
            return filter(args[1], List.of("add", "remove", "toggle"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ore") && List.of("add", "remove", "toggle").contains(args[1].toLowerCase(Locale.ROOT))) {
            return filter(args[2], plugin.candidateOreMaterials().stream().map(Enum::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("exposure")) {
            return filter(args[1], List.of("status", "adjacent", "transparent"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("exposure") && isExposureList(args[1])) {
            return filter(args[2], List.of("add", "remove", "toggle"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("exposure") && isExposureList(args[1])) {
            return filter(args[3], allBlockMaterialNames());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("host")) {
            return filter(args[1], List.of("status", "default", "override"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("host") && args[1].equalsIgnoreCase("default")) {
            return filter(args[2], Arrays.stream(World.Environment.values()).map(Enum::name).toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("host") && args[1].equalsIgnoreCase("default")) {
            return filter(args[3], allBlockMaterialNames());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("host") && args[1].equalsIgnoreCase("override")) {
            return filter(args[2], plugin.candidateOreMaterials().stream().map(Enum::name).toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("host") && args[1].equalsIgnoreCase("override")) {
            return filter(args[3], List.of("clear", "STONE", "DEEPSLATE", "NETHERRACK", "END_STONE"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return filter(args[1], ConfigSetting.booleanSettings().stream().map(ConfigSetting::id).toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("get") || args[0].equalsIgnoreCase("explain"))) {
            return filter(args[1], ConfigSetting.settingIds());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            ConfigSetting setting = ConfigSetting.fromId(args[1]);
            if (setting == null) {
                return List.of();
            }
            return filter(args[2], setting.suggestions());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("settings")) {
            return filter(args[1], ConfigSetting.sectionIds());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("transport")) {
            return filter(args[1], Arrays.stream(TransportMode.values()).map(Enum::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("profile")) {
            return filter(args[1], XrayProfile.configNames());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("world")) {
            return filter(args[1], List.of("status", "target", "seed", "create", "regenerate", "delete", "tp", "default"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("seed")) {
            return filter(args[2], List.of("random", "keep"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("tp")) {
            return filter(args[2], teleportableWorldTargets());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("delete")) {
            return filter(args[2], deletableWorldTargets());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("default")) {
            return filter(args[2], defaultableWorldTargets());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("regenerate")) {
            return filter(args[2], List.of("confirm", "keep"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("regenerate")) {
            return filter(args[3], List.of("confirm"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("world") && args[1].equalsIgnoreCase("delete")) {
            return filter(args[3], List.of("confirm"));
        }
        return List.of();
    }

    private boolean handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "Only players can open the Oreveil GUI. Use /oreveil help for command tools.");
            return true;
        }

        plugin.openAdminGui(player);
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        OreveilConfig config = plugin.reloadOreveilConfig();
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Reloaded ", BASE)
                .append(highlight(plugin.obfuscationService().transportName(), STATUS))
                .append(Component.text(" transport with ", BASE))
                .append(highlight(String.valueOf(config.protectedOres().size()), ORES))
                .append(Component.text(" protected ores and a ", BASE))
                .append(highlight(String.valueOf(config.liveSyncRadiusBlocks()), CONTROLS))
                .append(Component.text(" block sync radius.", BASE))
        );
        return true;
    }

    private boolean handleInspect(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "Only players can use /oreveil inspect.");
            return true;
        }

        Block block = player.getTargetBlockExact(8);
        if (block == null) {
            sendError(sender, "No target block within 8 blocks.");
            return true;
        }

        ExposureService exposureService = plugin.exposureService();
        boolean protectedOre = exposureService.isProtectedOre(block.getType());
        boolean exposed = protectedOre && exposureService.isLegitimatelyExposed(block);
        Material visibleMaterial = plugin.obfuscationService().getClientVisibleMaterial(block, player);
        List<String> reasons = protectedOre ? new ArrayList<>(exposureService.describeExposure(block)) : List.of();
        String classification = plugin.worldModel().describeBlockClassification(block);

        sendDivider(sender);
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Inspecting ", BASE)
                .append(highlight(block.getType().name(), STATUS))
                .append(Component.text(" at ", BASE))
                .append(highlight(block.getX() + ", " + block.getY() + ", " + block.getZ(), STATUS))
                .append(Component.text(".", BASE))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Protected: ", BASE)
                .append(highlight(String.valueOf(protectedOre), protectedOre ? ORES : MUTED))
                .append(Component.text("  Exposed: ", BASE))
                .append(highlight(String.valueOf(exposed), exposed ? ORES : MUTED))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Classification: ", BASE)
                .append(highlight(classification, classification.startsWith("fake") ? ORES : STATUS))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Client sees ", BASE)
                .append(highlight(visibleMaterial.name(), STATUS))
                .append(Component.text(".", BASE))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Chunk rewrite: ", BASE)
                .append(highlight(plugin.worldModel().describeChunkRewriteState(block), STATUS))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Exposure reasons: ", BASE)
                .append(highlight(reasons.isEmpty() ? "none" : String.join("; ", reasons), reasons.isEmpty() ? MUTED : STATUS))
        );
        sendDivider(sender);
        return true;
    }

    private boolean handleStatus(CommandSender sender, String label) {
        OreveilConfig config = plugin.oreveilConfig();
        sendDivider(sender);
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Runtime: ", BASE)
                .append(highlight(onOff(config.obfuscationEnabled()), config.obfuscationEnabled() ? STATUS : MUTED))
                .append(Component.text("  Transport: ", BASE))
                .append(highlight(plugin.obfuscationService().transportName(), STATUS))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Protected ores: ", BASE)
                .append(highlight(String.valueOf(config.protectedOres().size()), ORES))
                .append(Component.text("  Reveal: ", BASE))
                .append(highlight(onOff(config.revealOnExposure()), config.revealOnExposure() ? ORES : MUTED))
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text("Xray profile: ", BASE)
                .append(highlight(config.xrayProfile().configName(), WORLD))
                .append(Component.text("  Salted distribution: ", BASE))
                .append(highlight(onOff(config.saltedDistributionEnabled()), config.saltedDistributionEnabled() ? WORLD : MUTED))
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            worldSummary(config.worldGeneration(), label)
        );
        if (sender instanceof Player) {
            sendMessage(sender, "Oreveil", CONTROLS, Component.text("Use /" + label + " to open the admin GUI. Advanced commands remain available with /" + label + " help.", BASE));
        } else {
            sendMessage(sender, "Oreveil", CONTROLS, Component.text("Use /" + label + " help for advanced commands.", BASE));
        }
        sendDivider(sender);
        return true;
    }

    private boolean handleDiagnostics(CommandSender sender) {
        OreveilConfig config = plugin.oreveilConfig();
        ObfuscationMetrics.Snapshot metrics = plugin.obfuscationService().metricsSnapshot();
        AuthoritativeWorldModel.CacheStats cacheStats = plugin.cacheStats();

        sendDivider(sender);
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Transport: ", BASE)
                .append(highlight(plugin.obfuscationService().transportName(), STATUS))
                .append(Component.text("  Chunk delivery handled: ", BASE))
                .append(highlight(String.valueOf(plugin.obfuscationService().handlesChunkDelivery()), STATUS))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Packet rewrites: block=", BASE)
                .append(highlight(String.valueOf(metrics.blockChangePacketsRewritten()), STATUS))
                .append(Component.text("  multi=", BASE))
                .append(highlight(String.valueOf(metrics.multiBlockPacketsRewritten()), STATUS))
                .append(Component.text("  multi entries=", BASE))
                .append(highlight(String.valueOf(metrics.multiBlockEntriesRewritten()), STATUS))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Chunk priming: packets=", BASE)
                .append(highlight(String.valueOf(metrics.chunkPacketsPrimed()), STATUS))
                .append(Component.text("  corrections=", BASE))
                .append(highlight(String.valueOf(metrics.chunkPrimeCorrectionsSent()), STATUS))
                .append(Component.text("  synthetic sends=", BASE))
                .append(highlight(String.valueOf(metrics.syntheticBlockChangesSent()), STATUS))
        );
        sendMessage(
            sender,
            "Status",
            metrics.chunkRewriteFailures() == 0 ? STATUS : ERROR,
            Component.text("Chunk rewrites: packets=", BASE)
                .append(highlight(String.valueOf(metrics.chunkPacketsRewritten()), STATUS))
                .append(Component.text("  entries=", BASE))
                .append(highlight(String.valueOf(metrics.chunkBlockEntriesRewritten()), STATUS))
                .append(Component.text("  failures=", BASE))
                .append(highlight(
                    String.valueOf(metrics.chunkRewriteFailures()),
                    metrics.chunkRewriteFailures() == 0 ? STATUS : ERROR
                ))
        );
        sendMessage(
            sender,
            "Status",
            metrics.multiBlockRewriteFailures() == 0 ? STATUS : ERROR,
            Component.text("ProtocolLib rewrite failures: ", BASE)
                .append(highlight(
                    String.valueOf(metrics.multiBlockRewriteFailures()),
                    metrics.multiBlockRewriteFailures() == 0 ? STATUS : ERROR
                ))
        );
        sendMessage(
            sender,
            "Ores",
            ORES,
            Component.text("Cache: ore chunks=", BASE)
                .append(highlight(String.valueOf(cacheStats.protectedOreChunks()), ORES))
                .append(Component.text("  ore blocks=", BASE))
                .append(highlight(String.valueOf(cacheStats.protectedOreBlocks()), ORES))
                .append(Component.text("  salt chunks=", BASE))
                .append(highlight(String.valueOf(cacheStats.saltChunks()), ORES))
                .append(Component.text("  salt blocks=", BASE))
                .append(highlight(String.valueOf(cacheStats.saltBlocks()), ORES))
        );
        sendMessage(
            sender,
            "Ores",
            ORES,
            Component.text("Xray profile: ", BASE)
                .append(highlight(config.xrayProfile().configName(), ORES))
                .append(Component.text("  salt budget=", BASE))
                .append(highlight(String.valueOf(config.xrayProfile().effectiveSaltBudget(config.saltDensity())), ORES))
                .append(Component.text("  rare cap=", BASE))
                .append(highlight(String.valueOf(config.xrayProfile().maxRareOreBlocks(config.xrayProfile().effectiveSaltBudget(config.saltDensity()))), ORES))
                .append(Component.text(" from baseline ", BASE))
                .append(highlight(String.valueOf(config.saltDensity()), ORES))
        );
        sendMessage(
            sender,
            "Ores",
            ORES,
            Component.text("Fake ore types: ", BASE)
                .append(highlight(formatMaterialCounts(cacheStats.saltBlocksByType()), ORES))
        );
        sendDivider(sender);
        return true;
    }

    private boolean handleProfile(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendDivider(sender);
            sendMessage(sender, "World", WORLD, profileControl(plugin.oreveilConfig().xrayProfile(), label));
            sendMessage(
                sender,
                "World",
                WORLD,
                Component.text("Use ", BASE)
                    .append(highlight("/" + label + " profile <preset>", WORLD))
                    .append(Component.text(" or ", BASE))
                    .append(highlight("/" + label + " set xray_profile <preset>", WORLD))
                    .append(Component.text(".", BASE))
            );
            sendDivider(sender);
            return true;
        }

        XrayProfile profile = XrayProfile.parse(args[1]);
        if (profile == null) {
            sendError(sender, "Unknown profile " + args[1] + ". Options: " + String.join(", ", XrayProfile.configNames()) + ".");
            return true;
        }

        OreveilConfig config = plugin.setStringSetting(ConfigSetting.XRAY_PROFILE.path(), profile.configName());
        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text("Xray profile is now ", BASE)
                .append(highlight(config.xrayProfile().configName(), WORLD))
                .append(Component.text(" with salt budget ", BASE))
                .append(highlight(String.valueOf(config.xrayProfile().effectiveSaltBudget(config.saltDensity())), WORLD))
                .append(Component.text(".", BASE))
        );
        return handleStatus(sender, label);
    }

    private boolean handleWorld(CommandSender sender, String label, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            sendWorldStatus(sender, label);
            return true;
        }

        if (args[1].equalsIgnoreCase("target")) {
            if (args.length < 3) {
                sendError(sender, "Use /" + label + " world target <name>.");
                return true;
            }
            String target = args[2].trim();
            if (target.isEmpty()) {
                sendError(sender, "Target world name cannot be blank.");
                return true;
            }
            plugin.setStringSetting("world-generation.target-world", target);
            sendMessage(
                sender,
                "World",
                WORLD,
                Component.text("Managed world target is now ", BASE)
                    .append(highlight(target, WORLD))
                    .append(Component.text(".", BASE))
            );
            sendWorldStatus(sender, label);
            return true;
        }

        if (args[1].equalsIgnoreCase("seed")) {
            if (args.length < 3) {
                sendError(sender, "Use /" + label + " world seed <random|keep|value>.");
                return true;
            }

            if (args[2].equalsIgnoreCase("random")) {
                plugin.setNullableLongSetting("world-generation.seed", null);
                sendMessage(sender, "World", WORLD, Component.text("Managed world seed mode is now random.", BASE));
                sendWorldStatus(sender, label);
                return true;
            }

            if (args[2].equalsIgnoreCase("keep")) {
                Long configuredSeed = plugin.oreveilConfig().worldGeneration().configuredSeed();
                sendMessage(
                    sender,
                    "World",
                    WORLD,
                    Component.text("Managed world seed is ", BASE)
                        .append(highlight(configuredSeed == null ? "random" : String.valueOf(configuredSeed), WORLD))
                        .append(Component.text(".", BASE))
                );
                return true;
            }

            long seed;
            try {
                seed = Long.parseLong(args[2]);
            } catch (NumberFormatException ignored) {
                sendError(sender, args[2] + " is not a valid seed.");
                return true;
            }

            plugin.setNullableLongSetting("world-generation.seed", seed);
            sendMessage(
                sender,
                "World",
                WORLD,
                Component.text("Managed world seed is now ", BASE)
                    .append(highlight(String.valueOf(seed), WORLD))
                    .append(Component.text(".", BASE))
            );
            sendWorldStatus(sender, label);
            return true;
        }

        if (args[1].equalsIgnoreCase("create")) {
            Long seedOverride = args.length >= 3 ? parseSeedArg(sender, args[2]) : null;
            if (args.length >= 3 && seedOverride == null && !args[2].equalsIgnoreCase("random")) {
                return true;
            }

            runWorldOperation(
                sender,
                "Creating managed world",
                listener -> plugin.worldGenerationService().createManagedWorldAsync(seedOverride, 3, listener)
            );
            return true;
        }

        if (args[1].equalsIgnoreCase("regenerate")) {
            if (!lastArgIsConfirm(args)) {
                sendError(sender, "Use /" + label + " world regenerate [seed] confirm.");
                return true;
            }

            Long seedOverride = null;
            if (args.length == 4) {
                seedOverride = parseSeedArg(sender, args[2]);
                if (seedOverride == null && !args[2].equalsIgnoreCase("random") && !args[2].equalsIgnoreCase("keep")) {
                    return true;
                }
                if (args[2].equalsIgnoreCase("keep")) {
                    seedOverride = plugin.oreveilConfig().worldGeneration().configuredSeed();
                }
            }

            Long finalSeedOverride = seedOverride;
            runWorldOperation(
                sender,
                "Regenerating managed world",
                listener -> plugin.worldGenerationService().regenerateManagedWorldAsync(finalSeedOverride, 3, listener)
            );
            return true;
        }

        if (args[1].equalsIgnoreCase("delete")) {
            if (args.length == 2) {
                sendDeleteWorldPrompt(sender, label);
                return true;
            }

            if (args.length == 3) {
                sendDeleteConfirmation(sender, label, resolveWorldTarget(args[2]));
                return true;
            }

            if (!args[3].equalsIgnoreCase("confirm")) {
                sendError(sender, "Use /" + label + " world delete <name> confirm.");
                return true;
            }

            String worldName = resolveWorldTarget(args[2]);
            WorldRegenerationResult result = plugin.worldGenerationService().deleteWorld(worldName);
            sendWorldResult(sender, result);
            return true;
        }

        if (args[1].equalsIgnoreCase("default")) {
            if (args.length < 3) {
                sendMessage(
                    sender,
                    "World",
                    WORLD,
                    Component.text("Server default world: ", BASE)
                        .append(highlight(plugin.worldGenerationService().currentDefaultWorldName(), WORLD))
                        .append(Component.text(". Use /" + label + " world default <name> to change it for the next restart.", BASE))
                );
                return true;
            }

            String worldName = resolveWorldTarget(args[2]);
            WorldRegenerationResult result = plugin.worldGenerationService().setDefaultWorld(worldName);
            sendWorldResult(sender, result);
            return true;
        }

        if (args[1].equalsIgnoreCase("tp")) {
            if (!(sender instanceof Player player)) {
                sendError(sender, "Only players can teleport to worlds.");
                return true;
            }
            if (args.length < 3) {
                sendError(sender, "Use /" + label + " world tp <name>.");
                return true;
            }

            String worldName = resolveWorldTarget(args[2]);
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                sendError(sender, "World " + worldName + " is not loaded.");
                return true;
            }

            player.teleportAsync(world.getSpawnLocation());
            sendMessage(
                sender,
                "World",
                WORLD,
                Component.text("Teleporting to ", BASE)
                    .append(highlight(world.getName(), WORLD))
                    .append(Component.text(".", BASE))
            );
            return true;
        }

        sendError(sender, "Unknown world subcommand. Use /" + label + " world status.");
        return true;
    }

    private boolean handleOreToggle(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !List.of("add", "remove", "toggle").contains(args[1].toLowerCase(Locale.ROOT))) {
            sendError(sender, "Use /" + label + " ore <add|remove|toggle> <ore>.");
            return true;
        }

        Material material = Material.matchMaterial(args[2], false);
        if (material == null || !material.isBlock()) {
            sendError(sender, "Unknown block material " + args[2] + ".");
            return true;
        }

        boolean enabledBefore = plugin.oreveilConfig().protectedOres().contains(material);
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("toggle")) {
            plugin.toggleGlobalProtectedOre(material);
        } else {
            plugin.setMaterialListEntry("protected-ores", material, action.equals("add"));
        }
        boolean enabledAfter = action.equals("toggle") ? !enabledBefore : action.equals("add");
        sendMessage(
            sender,
            "Ores",
            ORES,
            Component.text(enabledAfter ? "Now hiding " : "Stopped hiding ", BASE)
                .append(highlight(material.name(), enabledAfter ? ACTIVE : MUTED))
                .append(Component.text(".", BASE))
        );
        sendOreMenu(sender);
        return true;
    }

    private boolean handleExposure(CommandSender sender, String label, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            sendExposureStatus(sender, label);
            return true;
        }

        if (!isExposureList(args[1]) || args.length < 4 || !List.of("add", "remove", "toggle").contains(args[2].toLowerCase(Locale.ROOT))) {
            sendError(sender, "Use /" + label + " exposure <adjacent|transparent> <add|remove|toggle> <material>.");
            return true;
        }

        Material material = parseBlockMaterial(sender, args[3]);
        if (material == null) {
            return true;
        }

        String path = exposurePath(args[1]);
        String action = args[2].toLowerCase(Locale.ROOT);
        boolean containedBefore = exposureContains(args[1], material);
        if (action.equals("toggle")) {
            plugin.toggleMaterialListEntry(path, material);
        } else {
            plugin.setMaterialListEntry(path, material, action.equals("add"));
        }
        boolean containedAfter = action.equals("toggle") ? !containedBefore : action.equals("add");

        sendMessage(
            sender,
            "Exposure",
            CONTROLS,
            Component.text(containedAfter ? "Added " : "Removed ", BASE)
                .append(highlight(material.name(), containedAfter ? CONTROLS : MUTED))
                .append(Component.text(containedAfter ? " to " : " from ", BASE))
                .append(highlight(args[1].toLowerCase(Locale.ROOT), CONTROLS))
                .append(Component.text(" exposure.", BASE))
        );
        sendExposureStatus(sender, label);
        return true;
    }

    private boolean handleHost(CommandSender sender, String label, String[] args) {
        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            sendHostStatus(sender, label);
            return true;
        }

        if (args[1].equalsIgnoreCase("default")) {
            if (args.length < 4) {
                sendError(sender, "Use /" + label + " host default <environment> <material>.");
                return true;
            }

            World.Environment environment = parseEnvironment(sender, args[2]);
            Material material = parseBlockMaterial(sender, args[3]);
            if (environment == null || material == null) {
                return true;
            }

            plugin.setConfigMapEntry("host-blocks.dimension-defaults", environment.name(), material.name());
            sendMessage(
                sender,
                "Host",
                WORLD,
                Component.text("Default host for ", BASE)
                    .append(highlight(environment.name(), WORLD))
                    .append(Component.text(" is now ", BASE))
                    .append(highlight(material.name(), WORLD))
                    .append(Component.text(".", BASE))
            );
            sendHostStatus(sender, label);
            return true;
        }

        if (args[1].equalsIgnoreCase("override")) {
            if (args.length < 4) {
                sendError(sender, "Use /" + label + " host override <ore> <material|clear>.");
                return true;
            }

            Material ore = parseBlockMaterial(sender, args[2]);
            if (ore == null) {
                return true;
            }

            if (args[3].equalsIgnoreCase("clear")) {
                plugin.clearConfigMapEntry("host-blocks.ore-overrides", ore.name());
                sendMessage(
                    sender,
                    "Host",
                    WORLD,
                    Component.text("Cleared host override for ", BASE)
                        .append(highlight(ore.name(), WORLD))
                        .append(Component.text(".", BASE))
                );
                sendHostStatus(sender, label);
                return true;
            }

            Material host = parseBlockMaterial(sender, args[3]);
            if (host == null) {
                return true;
            }

            plugin.setConfigMapEntry("host-blocks.ore-overrides", ore.name(), host.name());
            sendMessage(
                sender,
                "Host",
                WORLD,
                Component.text("Host override for ", BASE)
                    .append(highlight(ore.name(), WORLD))
                    .append(Component.text(" is now ", BASE))
                    .append(highlight(host.name(), WORLD))
                    .append(Component.text(".", BASE))
            );
            sendHostStatus(sender, label);
            return true;
        }

        sendError(sender, "Unknown host subcommand. Use /" + label + " host status.");
        return true;
    }

    private boolean handleToggle(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Use /" + label + " toggle <setting>.");
            return true;
        }

        ConfigSetting setting = ConfigSetting.fromId(args[1]);
        if (setting == null || setting.type() != SettingType.BOOLEAN) {
            sendError(sender, "Unknown toggle " + args[1] + ".");
            return true;
        }

        OreveilConfig config = plugin.toggleBooleanSetting(setting.path());
        boolean value = (Boolean) setting.read(config);
        sendMessage(
            sender,
            setting.sectionTitle(),
            setting.sectionColor(),
            Component.text(setting.displayName() + " is now ", BASE)
                .append(highlight(onOff(value), value ? setting.sectionColor() : MUTED))
                .append(Component.text(".", BASE))
        );
        return handleStatus(sender, label);
    }

    private boolean handleSet(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sendError(sender, "Use /" + label + " set <setting> <value>.");
            return true;
        }

        ConfigSetting setting = ConfigSetting.fromId(args[1]);
        if (setting == null) {
            sendError(sender, "Unknown setting " + args[1] + ".");
            return true;
        }

        Object value = setting.parse(args[2]);
        if (value == ConfigSetting.INVALID_VALUE) {
            sendError(sender, args[2] + " is not valid for " + setting.id() + ".");
            return true;
        }

        OreveilConfig config = applySetting(setting, value);
        Object savedValue = setting.read(config);
        sendMessage(
            sender,
            setting.sectionTitle(),
            setting.sectionColor(),
            Component.text("Set ", BASE)
                .append(highlight(setting.displayName(), setting.sectionColor()))
                .append(Component.text(" to ", BASE))
                .append(highlight(setting.format(savedValue), setting.sectionColor()))
                .append(Component.text(".", BASE))
        );
        return handleStatus(sender, label);
    }

    private boolean handleSettings(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendDivider(sender);
            sendMessage(sender, "Settings", CONTROLS, Component.text("Sections: ", BASE).append(settingSectionLinks(label)));
            sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " get <setting>", CONTROLS, "Shows one current value."));
            sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " explain <setting>", CONTROLS, "Shows details for one setting."));
            sendDivider(sender);
            return true;
        }

        String section = args[1].toLowerCase(Locale.ROOT);
        List<ConfigSetting> settings = ConfigSetting.bySection(section);
        if (settings.isEmpty()) {
            sendError(sender, "Unknown settings section " + args[1] + ".");
            return true;
        }

        OreveilConfig config = plugin.oreveilConfig();
        sendDivider(sender);
        for (ConfigSetting setting : settings) {
            Object value = setting.read(config);
            Component row = Component.text(setting.displayName() + ": ", BASE)
                .append(highlight(setting.format(value), setting.sectionColor()))
                .append(Component.text("  ", BASE))
                .append(action(
                    "/" + label + " explain " + setting.id(),
                    "explain",
                    MUTED,
                    "Show valid values and config path."
                ));
            if (setting.type() == SettingType.BOOLEAN) {
                row = row.append(Component.text("  ", BASE))
                    .append(action(
                        "/" + label + " toggle " + setting.id(),
                        ((Boolean) value) ? "disable" : "enable",
                        setting.sectionColor(),
                        "Toggle " + setting.displayName().toLowerCase(Locale.ROOT) + "."
                    ));
            }
            sendMessage(sender, setting.sectionTitle(), setting.sectionColor(), row);
        }
        sendDivider(sender);
        return true;
    }

    private boolean handleGet(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Use /" + label + " get <setting>.");
            return true;
        }

        ConfigSetting setting = ConfigSetting.fromId(args[1]);
        if (setting == null) {
            sendError(sender, "Unknown setting " + args[1] + ".");
            return true;
        }

        Object value = setting.read(plugin.oreveilConfig());
        sendMessage(
            sender,
            setting.sectionTitle(),
            setting.sectionColor(),
            Component.text(setting.displayName() + ": ", BASE)
                .append(highlight(setting.format(value), setting.sectionColor()))
                .append(Component.text("  Path: ", BASE))
                .append(highlight(setting.path(), MUTED))
        );
        return true;
    }

    private boolean handleExplain(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Use /" + label + " explain <setting>.");
            return true;
        }

        ConfigSetting setting = ConfigSetting.fromId(args[1]);
        if (setting == null) {
            sendError(sender, "Unknown setting " + args[1] + ".");
            return true;
        }

        sendDivider(sender);
        sendMessage(sender, setting.sectionTitle(), setting.sectionColor(), Component.text(setting.displayName(), setting.sectionColor()));
        sendMessage(sender, setting.sectionTitle(), setting.sectionColor(), Component.text(setting.description(), BASE));
        sendMessage(
            sender,
            setting.sectionTitle(),
            setting.sectionColor(),
            Component.text("Current: ", BASE)
                .append(highlight(setting.format(setting.read(plugin.oreveilConfig())), setting.sectionColor()))
                .append(Component.text("  Path: ", BASE))
                .append(highlight(setting.path(), MUTED))
        );
        sendMessage(
            sender,
            setting.sectionTitle(),
            setting.sectionColor(),
            Component.text("Set with ", BASE)
                .append(action(
                    "/" + label + " set " + setting.id() + " " + setting.exampleValue(),
                    "/" + label + " set " + setting.id() + " " + setting.exampleValue(),
                    setting.sectionColor(),
                    "Click to run an example value."
                ))
        );
        sendDivider(sender);
        return true;
    }

    private OreveilConfig applySetting(ConfigSetting setting, Object value) {
        return switch (setting.type()) {
            case BOOLEAN -> plugin.setBooleanSetting(setting.path(), (Boolean) value);
            case INTEGER -> plugin.setIntegerSetting(setting.path(), (Integer) value);
            case DOUBLE -> plugin.setDoubleSetting(setting.path(), (Double) value);
            case LONG -> plugin.setNullableLongSetting(setting.path(), (Long) value);
            case STRING, ENUM -> plugin.setStringSetting(setting.path(), (String) value);
        };
    }

    private boolean handleTransport(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Use /" + label + " transport <auto|block_update_sync|protocollib>.");
            return true;
        }

        TransportMode mode;
        try {
            mode = TransportMode.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            sendError(sender, "Unknown transport mode " + args[1] + ".");
            return true;
        }

        plugin.setTransportMode(mode);
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Transport set to ", BASE)
                .append(highlight(mode.name(), STATUS))
                .append(Component.text(".", BASE))
        );
        return handleStatus(sender, label);
    }

    private void sendOreMenu(CommandSender sender) {
        List<Material> active = plugin.oreveilConfig().protectedOres().stream().sorted(Comparator.comparing(Enum::name)).toList();
        sendDivider(sender);
        sendMessage(sender, "Ores", ORES, Component.text("Click an ore to toggle whether Oreveil hides it.", BASE));
        sendMessage(sender, "Ores", ORES, Component.text("White means selected. Gray means ignored.", BASE));

        Component row = Component.empty();
        int count = 0;
        for (Material material : plugin.candidateOreMaterials()) {
            boolean enabled = active.contains(material);
            Component ore = Component.text(material.name(), enabled ? ACTIVE : BASE)
                .clickEvent(ClickEvent.runCommand("/oreveil ore toggle " + material.name()))
                .hoverEvent(HoverEvent.showText(
                    Component.text(enabled ? "Click to stop hiding " : "Click to start hiding ", BASE)
                        .append(Component.text(material.name(), enabled ? MUTED : ORES))
                ));

            if (count > 0) {
                row = row.append(Component.text("  •  ", MUTED));
            }
            row = row.append(ore);
            count++;

            if (count == 3) {
                sender.sendMessage(row);
                row = Component.empty();
                count = 0;
            }
        }

        if (!row.equals(Component.empty())) {
            sender.sendMessage(row);
        }
        sendDivider(sender);
    }

    private void sendExposureStatus(CommandSender sender, String label) {
        OreveilConfig config = plugin.oreveilConfig();
        sendDivider(sender);
        sendMessage(
            sender,
            "Exposure",
            CONTROLS,
            Component.text("Adjacent: ", BASE)
                .append(highlight(String.valueOf(config.revealAdjacentMaterials().size()), CONTROLS))
                .append(Component.text(" materials  ", BASE))
                .append(action(
                    "/" + label + " exposure adjacent add AIR",
                    "add",
                    CONTROLS,
                    "Use /" + label + " exposure adjacent add <material>."
                ))
                .append(Component.text("  ", BASE))
                .append(action(
                    "/" + label + " exposure adjacent remove AIR",
                    "remove",
                    MUTED,
                    "Use /" + label + " exposure adjacent remove <material>."
                ))
        );
        sendMaterialPreview(sender, "Exposure", CONTROLS, config.revealAdjacentMaterials().stream().sorted(Comparator.comparing(Enum::name)).toList());
        sendMessage(
            sender,
            "Exposure",
            CONTROLS,
            Component.text("Transparent: ", BASE)
                .append(highlight(String.valueOf(config.revealTransparentMaterials().size()), CONTROLS))
                .append(Component.text(" materials  ", BASE))
                .append(action(
                    "/" + label + " exposure transparent add GLASS",
                    "add",
                    CONTROLS,
                    "Use /" + label + " exposure transparent add <material>."
                ))
                .append(Component.text("  ", BASE))
                .append(action(
                    "/" + label + " exposure transparent remove GLASS",
                    "remove",
                    MUTED,
                    "Use /" + label + " exposure transparent remove <material>."
                ))
        );
        sendMaterialPreview(sender, "Exposure", CONTROLS, config.revealTransparentMaterials().stream().sorted(Comparator.comparing(Enum::name)).toList());
        sendDivider(sender);
    }

    private void sendHostStatus(CommandSender sender, String label) {
        OreveilConfig config = plugin.oreveilConfig();
        sendDivider(sender);
        for (World.Environment environment : World.Environment.values()) {
            Material material = config.resolveDimensionDefault(environment);
            sendMessage(
                sender,
                "Host",
                WORLD,
                Component.text(environment.name() + ": ", BASE)
                    .append(highlight(material.name(), WORLD))
                    .append(Component.text("  ", BASE))
                    .append(action(
                        "/" + label + " host default " + environment.name() + " " + material.name(),
                        "set",
                        WORLD,
                        "Use /" + label + " host default " + environment.name() + " <material>."
                    ))
            );
        }
        List<Material> overrides = config.oreOverridesView().keySet().stream().sorted(Comparator.comparing(Enum::name)).toList();
        sendMessage(
            sender,
            "Host",
            WORLD,
            Component.text("Ore overrides: ", BASE)
                .append(highlight(String.valueOf(overrides.size()), WORLD))
                .append(Component.text("  ", BASE))
                .append(action(
                    "/" + label + " host override ANCIENT_DEBRIS NETHERRACK",
                    "add",
                    WORLD,
                    "Use /" + label + " host override <ore> <material>."
                ))
        );
        for (Material ore : overrides.stream().limit(8).toList()) {
            Material host = config.resolveOreOverride(ore);
            sendMessage(
                sender,
                "Host",
                WORLD,
                Component.text(ore.name() + ": ", BASE)
                    .append(highlight(host.name(), WORLD))
                    .append(Component.text("  ", BASE))
                    .append(action(
                        "/" + label + " host override " + ore.name() + " clear",
                        "clear",
                        MUTED,
                        "Clear this ore-specific host override."
                    ))
            );
        }
        if (overrides.size() > 8) {
            sendMessage(sender, "Host", WORLD, Component.text("Showing 8 of " + overrides.size() + " overrides.", BASE));
        }
        sendDivider(sender);
    }

    private void sendHelp(CommandSender sender, String label, String[] args) {
        if (args.length >= 2) {
            sendHelpTopic(sender, label, args[1]);
            return;
        }

        sendDivider(sender);
        sendMessage(sender, "Oreveil", CONTROLS, Component.text("Advanced command tools. Players can use /" + label + " to open the GUI.", BASE));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label, STATUS, "Opens the admin GUI for players."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " status", STATUS, "Shows a compact runtime summary."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " diagnostics", STATUS, "Shows packet rewrite and cache counters."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " inspect", STATUS, "Inspects the targeted block."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " profile <preset>", WORLD, "Changes the fake-ore behavior preset."));
        sendMessage(sender, "Ores", ORES, commandLine("/" + label + " ores", ORES, "Shows the advanced clickable ore selector."));
        sendMessage(sender, "Ores", ORES, commandLine("/" + label + " ore <add|remove|toggle> <ore>", ORES, "Edits protected ore materials."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " exposure", CONTROLS, "Edits exposure material lists."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " host", WORLD, "Edits host block defaults and overrides."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " settings [section]", CONTROLS, "Browses editable config compactly."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " get <setting>", CONTROLS, "Shows one current config value."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " explain <setting>", CONTROLS, "Explains one setting without dumping everything."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " toggle <setting>", CONTROLS, "Toggles a boolean setting."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " set <setting> <value>", CONTROLS, "Sets an editable scalar setting."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " transport <mode>", STATUS, "Changes the active transport mode."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world status", WORLD, "Shows the managed world panel."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world tp <name>", WORLD, "Teleports you to a loaded world."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world delete [name]", WORLD, "Picks a world and asks before deleting it."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world default <name>", WORLD, "Sets the server default world for the next restart."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " help <topic>", CONTROLS, "Shows focused examples."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " reload", CONTROLS, "Reloads config and runtime state."));
        sendDivider(sender);
    }

    private void sendHelpTopic(CommandSender sender, String label, String rawTopic) {
        switch (rawTopic.toLowerCase(Locale.ROOT)) {
            case "settings" -> sendSettingsHelp(sender, label);
            case "exposure" -> sendExposureHelp(sender, label);
            case "host" -> sendHostHelp(sender, label);
            case "ores", "ore" -> sendOresHelp(sender, label);
            case "profile", "profiles" -> sendProfileHelp(sender, label);
            case "world" -> sendWorldHelp(sender, label);
            case "diagnostics", "diag" -> sendDiagnosticsHelp(sender, label);
            default -> {
                sendError(sender, "Unknown help topic " + rawTopic + ".");
                sendMessage(sender, "Oreveil", CONTROLS, Component.text("Topics: settings, exposure, host, ores, profile, world, diagnostics.", BASE));
            }
        }
    }

    private void sendSettingsHelp(CommandSender sender, String label) {
        sendDivider(sender);
        sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " settings", CONTROLS, "Lists editable setting sections."));
        sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " settings controls", CONTROLS, "Shows compact controls for sync and reveal settings."));
        sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " settings world", WORLD, "Shows compact controls for world-model and managed-world settings."));
        sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " get live_sync_radius", CONTROLS, "Shows one current value and config path."));
        sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " explain salt_density", CONTROLS, "Shows one setting's meaning and example command."));
        sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " set xray_profile aggressive", WORLD, "Changes the fake-ore behavior preset."));
        sendMessage(sender, "Settings", CONTROLS, commandLine("/" + label + " set live_sync_radius 96", CONTROLS, "Sets one scalar value."));
        sendDivider(sender);
    }

    private void sendExposureHelp(CommandSender sender, String label) {
        sendDivider(sender);
        sendMessage(sender, "Exposure", CONTROLS, commandLine("/" + label + " exposure", CONTROLS, "Shows adjacent and transparent material counts."));
        sendMessage(sender, "Exposure", CONTROLS, commandLine("/" + label + " exposure adjacent add WATER", CONTROLS, "Makes a neighboring material reveal protected ore."));
        sendMessage(sender, "Exposure", CONTROLS, commandLine("/" + label + " exposure adjacent remove LAVA", CONTROLS, "Removes a neighboring reveal material."));
        sendMessage(sender, "Exposure", CONTROLS, commandLine("/" + label + " exposure transparent toggle GLASS", CONTROLS, "Toggles a transparent reveal material."));
        sendMessage(sender, "Exposure", CONTROLS, commandLine("/" + label + " toggle non_occluding_reveal", CONTROLS, "Toggles the broader non-occluding rule."));
        sendDivider(sender);
    }

    private void sendHostHelp(CommandSender sender, String label) {
        sendDivider(sender);
        sendMessage(sender, "Host", WORLD, commandLine("/" + label + " host", WORLD, "Shows dimension defaults and ore override count."));
        sendMessage(sender, "Host", WORLD, commandLine("/" + label + " host default NORMAL STONE", WORLD, "Sets the Overworld fallback host block."));
        sendMessage(sender, "Host", WORLD, commandLine("/" + label + " host default NETHER NETHERRACK", WORLD, "Sets the Nether fallback host block."));
        sendMessage(sender, "Host", WORLD, commandLine("/" + label + " host override ANCIENT_DEBRIS NETHERRACK", WORLD, "Sets an ore-specific host block."));
        sendMessage(sender, "Host", WORLD, commandLine("/" + label + " host override DIAMOND_ORE clear", WORLD, "Removes an ore-specific override."));
        sendDivider(sender);
    }

    private void sendOresHelp(CommandSender sender, String label) {
        sendDivider(sender);
        sendMessage(sender, "Ores", ORES, commandLine("/" + label + " ores", ORES, "Opens the clickable ore selector."));
        sendMessage(sender, "Ores", ORES, commandLine("/" + label + " ore add DIAMOND_ORE", ORES, "Starts hiding an ore material."));
        sendMessage(sender, "Ores", ORES, commandLine("/" + label + " ore remove COPPER_ORE", ORES, "Stops hiding an ore material."));
        sendMessage(sender, "Ores", ORES, commandLine("/" + label + " ore toggle ANCIENT_DEBRIS", ORES, "Toggles one ore material."));
        sendDivider(sender);
    }

    private void sendProfileHelp(CommandSender sender, String label) {
        sendDivider(sender);
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " profile", WORLD, "Shows clickable xray profile presets."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " profile balanced", WORLD, "Uses the baseline fake-ore budget."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " profile aggressive", WORLD, "Raises fake-ore pressure for xray users."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " set xray_profile performance", WORLD, "Uses the generic settings command for the same preset."));
        sendDivider(sender);
    }

    private void sendWorldHelp(CommandSender sender, String label) {
        sendDivider(sender);
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world status", WORLD, "Shows managed-world state and actions."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world target oreveil", WORLD, "Sets the managed world name."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world seed random", WORLD, "Clears the configured managed-world seed."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world create", WORLD, "Creates the managed world if needed."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world regenerate confirm", WORLD, "Regenerates after explicit confirmation."));
        sendMessage(sender, "World", WORLD, commandLine("/" + label + " world default managed", WORLD, "Sets the server default world for next restart."));
        sendDivider(sender);
    }

    private void sendDiagnosticsHelp(CommandSender sender, String label) {
        sendDivider(sender);
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " status", STATUS, "Shows compact live controls."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " inspect", STATUS, "Explains the targeted block's visibility state."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " diagnostics", STATUS, "Shows packet rewrite, priming, and cache counters."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " transport AUTO", STATUS, "Uses ProtocolLib when available and falls back otherwise."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " reload", STATUS, "Reloads config and runtime state."));
        sendDivider(sender);
    }

    private void sendWorldStatus(CommandSender sender, String label) {
        OreveilWorldGenerationConfig worldGeneration = plugin.oreveilConfig().worldGeneration();
        sendDivider(sender);
        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text("Managed target: ", BASE)
                .append(highlight(worldGeneration.targetWorldName(), WORLD))
                .append(Component.text("  Default: ", BASE))
                .append(highlight(plugin.worldGenerationService().currentDefaultWorldName(), WORLD))
                .append(Component.text("  Generation: ", BASE))
                .append(highlight(onOff(worldGeneration.enabled()), worldGeneration.enabled() ? WORLD : MUTED))
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text("Environment: ", BASE)
                .append(highlight(worldGeneration.environment().name(), WORLD))
                .append(Component.text("  Structures: ", BASE))
                .append(highlight(onOff(worldGeneration.generateStructures()), worldGeneration.generateStructures() ? WORLD : MUTED))
                .append(Component.text("  Experimental: ", BASE))
                .append(highlight(onOff(worldGeneration.experimental()), worldGeneration.experimental() ? WORLD : MUTED))
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text("Seed: ", BASE)
                .append(highlight(worldGeneration.configuredSeed() == null ? "random" : String.valueOf(worldGeneration.configuredSeed()), WORLD))
                .append(Component.text("  Backup: ", BASE))
                .append(highlight(onOff(worldGeneration.backupOnRegenerate()), worldGeneration.backupOnRegenerate() ? WORLD : MUTED))
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text("Custom world generation is currently treated as experimental.", BASE)
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text("Actions: ", BASE)
                .append(action("/" + label + " world create", "create", WORLD, "Create the managed world if it does not exist."))
                .append(Component.text("  •  ", MUTED))
                .append(action(
                    "/" + label + " world regenerate confirm",
                    "regenerate",
                    WORLD,
                    "Regenerate the managed world using the configured or random seed."
                ))
                .append(Component.text("  •  ", MUTED))
                .append(action("/" + label + " world tp managed", "teleport", WORLD, "Teleport to the managed world if it is loaded."))
                .append(Component.text("  •  ", MUTED))
                .append(action("/" + label + " world delete managed", "delete", ERROR, "Pick and confirm deleting the managed world."))
                .append(Component.text("  •  ", MUTED))
                .append(action("/" + label + " world default managed", "set default", WORLD, "Use the managed world as the server default on the next restart."))
                .append(Component.text("  •  ", MUTED))
                .append(action("/" + label + " world seed random", "random seed", MUTED, "Switch the managed world back to random seeds."))
        );
        sendDivider(sender);
    }

    private Component toggleControl(ConfigSetting setting, boolean value, String label) {
        return Component.text(setting.displayName() + ": ", BASE)
            .append(highlight(onOff(value), value ? setting.sectionColor() : MUTED))
            .append(Component.text("  ", BASE))
            .append(action(
                "/" + label + " toggle " + setting.id(),
                value ? "disable" : "enable",
                setting.sectionColor(),
                "Toggle " + setting.displayName().toLowerCase(Locale.ROOT) + "."
            ));
    }

    private Component numericControl(ConfigSetting setting, int value, String label) {
        int step = (int) setting.step();
        return Component.text(setting.displayName() + ": ", BASE)
            .append(highlight(String.valueOf(value), setting.sectionColor()))
            .append(Component.text("  ", BASE))
            .append(action(
                "/" + label + " set " + setting.id() + " " + setting.clamp(value - step),
                "-" + step,
                MUTED,
                "Lower " + setting.displayName().toLowerCase(Locale.ROOT) + "."
            ))
            .append(Component.text(" ", BASE))
            .append(action(
                "/" + label + " set " + setting.id() + " " + setting.clamp(value + step),
                "+" + step,
                setting.sectionColor(),
                "Raise " + setting.displayName().toLowerCase(Locale.ROOT) + "."
            ))
            .append(Component.text("  ", BASE))
            .append(action(
                "/" + label + " set " + setting.id() + " " + value,
                "set",
                setting.sectionColor(),
                "Use /" + label + " set " + setting.id() + " <value> for a custom number."
            ));
    }

    private Component settingSectionLinks(String label) {
        Component row = Component.empty();
        for (String section : ConfigSetting.sectionIds()) {
            if (!row.equals(Component.empty())) {
                row = row.append(Component.text("  •  ", MUTED));
            }
            row = row.append(action(
                "/" + label + " settings " + section,
                section,
                CONTROLS,
                "Show " + section + " settings."
            ));
        }
        return row;
    }

    private Component worldSummary(OreveilWorldGenerationConfig worldGeneration, String label) {
        return Component.text("Managed world: ", BASE)
            .append(highlight(worldGeneration.targetWorldName(), WORLD))
            .append(Component.text("  Experimental: ", BASE))
            .append(highlight(onOff(worldGeneration.experimental()), worldGeneration.experimental() ? WORLD : MUTED))
            .append(Component.text("  ", BASE))
            .append(action("/" + label + " world status", "open", WORLD, "Open the managed world panel."));
    }

    private void sendDeleteWorldPrompt(CommandSender sender, String label) {
        List<String> worlds = deletableWorldTargets();
        if (worlds.isEmpty()) {
            sendError(sender, "There are no deletable worlds available.");
            return;
        }

        sendDivider(sender);
        sendMessage(sender, "World", WORLD, Component.text("Pick a world to delete.", BASE));
        for (String worldName : worlds) {
            sendMessage(
                sender,
                "World",
                WORLD,
                action(
                    "/" + label + " world delete " + worldName,
                    worldName,
                    WORLD,
                    "Select " + worldName + " and show the delete confirmation."
                )
            );
        }
        sendDivider(sender);
    }

    private void sendDeleteConfirmation(CommandSender sender, String label, String worldName) {
        if (!deletableWorldTargets().stream().anyMatch(name -> name.equalsIgnoreCase(worldName))) {
            sendError(sender, "World " + worldName + " is not available for deletion.");
            return;
        }

        sendDivider(sender);
        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text("Delete ", BASE)
                .append(highlight(worldName, ERROR))
                .append(Component.text("? This removes the world folder.", BASE))
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            action(
                "/" + label + " world delete " + worldName + " confirm",
                "confirm delete",
                ERROR,
                "Delete " + worldName + "."
            ).append(Component.text("  •  ", MUTED))
                .append(action("/" + label + " world delete", "pick another", WORLD, "Choose a different world."))
        );
        sendDivider(sender);
    }

    private void sendWorldResult(CommandSender sender, WorldRegenerationResult result) {
        if (!result.success()) {
            sendError(sender, result.message());
            return;
        }

        sendMessage(
            sender,
            "World",
            WORLD,
            Component.text(result.message(), BASE)
        );
    }

    private void runWorldOperation(
        CommandSender sender,
        String initialMessage,
        java.util.function.Consumer<OreveilWorldGenerationService.WorldOperationListener> operation
    ) {
        WorldProgressFeedback feedback = new WorldProgressFeedback(sender);
        feedback.stage(initialMessage + "...");
        operation.accept(feedback);
    }

    private boolean lastArgIsConfirm(String[] args) {
        return args.length >= 3 && args[args.length - 1].equalsIgnoreCase("confirm");
    }

    private Long parseSeedArg(CommandSender sender, String raw) {
        if (raw.equalsIgnoreCase("random")) {
            return null;
        }

        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ignored) {
            sendError(sender, raw + " is not a valid seed.");
            return null;
        }
    }

    private Component transportPicker(OreveilConfig config, String label) {
        Component row = Component.empty();
        TransportMode selected = TransportMode.fromConfig(config.transportMode());
        for (TransportMode mode : TransportMode.values()) {
            if (!row.equals(Component.empty())) {
                row = row.append(Component.text("  •  ", MUTED));
            }

            boolean active = mode == selected;
            row = row.append(Component.text(mode.name(), active ? ACTIVE : STATUS)
                .clickEvent(ClickEvent.runCommand("/" + label + " transport " + mode.name()))
                .hoverEvent(HoverEvent.showText(
                    Component.text(active ? "Current transport mode." : "Switch transport to ", BASE)
                        .append(Component.text(mode.name(), active ? ACTIVE : STATUS))
                )));
        }
        return row;
    }

    private Component profileControl(XrayProfile selected, String label) {
        Component row = Component.text(ConfigSetting.XRAY_PROFILE.displayName() + ": ", BASE)
            .append(highlight(selected.configName(), WORLD))
            .append(Component.text("  ", BASE));
        boolean first = true;
        for (XrayProfile profile : XrayProfile.values()) {
            if (!first) {
                row = row.append(Component.text("  •  ", MUTED));
            }
            first = false;

            boolean active = profile == selected;
            row = row.append(Component.text(profile.configName(), active ? ACTIVE : WORLD)
                .clickEvent(ClickEvent.runCommand("/" + label + " profile " + profile.configName()))
                .hoverEvent(HoverEvent.showText(
                    Component.text(active ? "Current xray profile. " : "Switch xray profile to ", BASE)
                        .append(Component.text(profile.displayName(), active ? ACTIVE : WORLD))
                        .append(Component.text(
                            " (budget "
                                + Math.round(profile.saltDensityMultiplier() * 100.0D)
                                + "%, common "
                                + Math.round(profile.commonOreWeightMultiplier() * 100.0D)
                                + "%, rare "
                                + Math.round(profile.rareOreWeightMultiplier() * 100.0D)
                                + "%, veins "
                                + Math.round(profile.veinSizeMultiplier() * 100.0D)
                                + "%).",
                            BASE
                        ))
                )));
        }
        return row;
    }

    private Component action(String command, String text, TextColor color, String hover) {
        return Component.text(text, color)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, BASE)));
    }

    private void sendMaterialPreview(CommandSender sender, String title, TextColor accent, List<Material> materials) {
        String preview = materials.stream()
            .limit(10)
            .map(Enum::name)
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
        if (materials.size() > 10) {
            preview = preview + ", +" + (materials.size() - 10) + " more";
        }
        sendMessage(sender, title, accent, Component.text(preview, BASE));
    }

    private boolean isExposureList(String raw) {
        return raw.equalsIgnoreCase("adjacent") || raw.equalsIgnoreCase("transparent");
    }

    private String exposurePath(String raw) {
        return raw.equalsIgnoreCase("adjacent")
            ? "exposure.reveal-adjacent-materials"
            : "exposure.reveal-transparent-materials";
    }

    private boolean exposureContains(String raw, Material material) {
        OreveilConfig config = plugin.oreveilConfig();
        return raw.equalsIgnoreCase("adjacent")
            ? config.revealAdjacentMaterials().contains(material)
            : config.revealTransparentMaterials().contains(material);
    }

    private Material parseBlockMaterial(CommandSender sender, String raw) {
        Material material = Material.matchMaterial(raw, false);
        if (material == null || !material.isBlock()) {
            sendError(sender, "Unknown block material " + raw + ".");
            return null;
        }
        return material;
    }

    private World.Environment parseEnvironment(CommandSender sender, String raw) {
        try {
            return World.Environment.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            sendError(sender, "Unknown world environment " + raw + ".");
            return null;
        }
    }

    private List<String> allBlockMaterialNames() {
        return Arrays.stream(Material.values())
            .filter(Material::isBlock)
            .map(Enum::name)
            .toList();
    }

    private List<String> teleportableWorldTargets() {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            names.add(world.getName());
        }
        String managedTarget = plugin.oreveilConfig().worldGeneration().targetWorldName();
        if (Bukkit.getWorld(managedTarget) != null && !names.contains(managedTarget)) {
            names.add(managedTarget);
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private List<String> deletableWorldTargets() {
        List<String> names = new ArrayList<>();
        String primaryWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getName();

        File[] children = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (children != null) {
            Stream.of(children)
                .map(File::getName)
                .filter(name -> new File(Bukkit.getWorldContainer(), name + File.separator + "level.dat").exists())
                .filter(name -> !name.contains("_backup_"))
                .filter(name -> !name.equalsIgnoreCase("plugins"))
                .filter(name -> primaryWorld == null || !name.equalsIgnoreCase(primaryWorld))
                .forEach(names::add);
        }

        for (World world : Bukkit.getWorlds()) {
            if (primaryWorld != null && world.getName().equalsIgnoreCase(primaryWorld)) {
                continue;
            }
            if (!names.contains(world.getName())) {
                names.add(world.getName());
            }
        }

        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private List<String> defaultableWorldTargets() {
        List<String> names = new ArrayList<>();
        File[] children = Bukkit.getWorldContainer().listFiles(File::isDirectory);
        if (children != null) {
            Stream.of(children)
                .map(File::getName)
                .filter(name -> new File(Bukkit.getWorldContainer(), name + File.separator + "level.dat").exists())
                .filter(name -> !name.contains("_backup_"))
                .forEach(names::add);
        }

        for (World world : Bukkit.getWorlds()) {
            if (!names.contains(world.getName())) {
                names.add(world.getName());
            }
        }

        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private String resolveWorldTarget(String raw) {
        return raw.equalsIgnoreCase("managed")
            ? plugin.oreveilConfig().worldGeneration().targetWorldName()
            : raw;
    }

    private List<String> filter(String prefix, List<String> values) {
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
            .sorted()
            .toList();
    }

    private Component commandLine(String command, TextColor accent, String description) {
        return Component.text("- ", BASE)
            .append(highlight(command, accent))
            .append(Component.text(" " + description, BASE));
    }

    private Component prefix(String title, TextColor accent) {
        return Component.text("[", BASE)
            .append(Component.text(title, accent))
            .append(Component.text("] ", BASE));
    }

    private Component highlight(String text, TextColor color) {
        return Component.text(text, color);
    }

    private String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private String formatMaterialCounts(Map<Material, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }
        return counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
            .map(entry -> entry.getKey().name() + "=" + entry.getValue())
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    }

    private void sendDivider(CommandSender sender) {
        sender.sendMessage(Component.text("----------------------------------------", MUTED));
    }

    private void sendMessage(CommandSender sender, String title, TextColor accent, Component body) {
        sender.sendMessage(prefix(title, accent).append(body));
    }

    private void sendError(CommandSender sender, String message) {
        sendMessage(sender, "Error", ERROR, Component.text(message, BASE));
    }

    private final class WorldProgressFeedback implements OreveilWorldGenerationService.WorldOperationListener {
        private final CommandSender sender;

        private WorldProgressFeedback(CommandSender sender) {
            this.sender = sender;
        }

        @Override
        public void onStage(String message) {
            stage(message);
        }

        @Override
        public void onProgress(int completed, int total) {
            if (completed == 1 || completed == total || completed % Math.max(1, total / 4) == 0) {
                sendMessage(
                    sender,
                    "World",
                    WORLD,
                    Component.text("Spawn area progress: ", BASE)
                        .append(highlight(completed + "/" + total, WORLD))
                        .append(Component.text(".", BASE))
                );
            }
        }

        @Override
        public void onComplete(WorldRegenerationResult result) {
            sendWorldResult(sender, result);
        }

        private void stage(String message) {
            sendMessage(sender, "World", WORLD, Component.text(message, BASE));
        }
    }

    private enum SettingType {
        BOOLEAN,
        INTEGER,
        DOUBLE,
        LONG,
        STRING,
        ENUM
    }

    private enum ConfigSetting {
        OBFUSCATION_ENABLED(
            "obfuscation",
            "obfuscation.enabled",
            "Obfuscation",
            "World",
            WORLD,
            SettingType.BOOLEAN,
            0,
            0,
            0,
            List.of("on", "off"),
            List.of(),
            "Enables or disables Oreveil's runtime obfuscation layer.",
            OreveilConfig::obfuscationEnabled
        ),
        REVEAL_ON_EXPOSURE(
            "reveal_on_exposure",
            "obfuscation.reveal-on-exposure",
            "Reveal On Exposure",
            "Controls",
            CONTROLS,
            SettingType.BOOLEAN,
            0,
            0,
            0,
            List.of("on", "off"),
            List.of(),
            "Controls whether protected ores become visible after normal gameplay exposes them.",
            OreveilConfig::revealOnExposure
        ),
        NON_OCCLUDING_REVEAL(
            "non_occluding_reveal",
            "obfuscation.reveal-next-to-non-occluding-blocks",
            "Non-Occluding Reveal",
            "Controls",
            CONTROLS,
            SettingType.BOOLEAN,
            0,
            0,
            0,
            List.of("on", "off"),
            List.of(),
            "Treats non-occluding neighboring blocks as exposure sources.",
            OreveilConfig::revealNextToNonOccludingBlocks
        ),
        LIVE_SYNC_RADIUS(
            "live_sync_radius",
            "obfuscation.live-sync-radius-blocks",
            "Live Sync Radius",
            "Controls",
            CONTROLS,
            SettingType.INTEGER,
            16,
            256,
            16,
            List.of("48", "64", "96"),
            List.of(),
            "Radius in blocks for live resync around players as exposure changes.",
            OreveilConfig::liveSyncRadiusBlocks
        ),
        CHUNK_PRIME_RADIUS(
            "chunk_prime_radius",
            "obfuscation.initial-sync-chunk-radius",
            "Chunk Prime Radius",
            "Controls",
            CONTROLS,
            SettingType.INTEGER,
            0,
            8,
            1,
            List.of("0", "1", "2"),
            List.of(),
            "Chunk radius to prime around players when they join or change context.",
            OreveilConfig::initialSyncChunkRadius
        ),
        REVEAL_PROXIMITY(
            "reveal_proximity",
            "obfuscation.reveal-proximity-blocks",
            "Exposed Radius",
            "Controls",
            CONTROLS,
            SettingType.INTEGER,
            0,
            96,
            8,
            List.of("32", "48", "64"),
            List.of(),
            "Fallback radius for refreshing exposed cave ores while players move.",
            OreveilConfig::revealProximityBlocks
        ),
        SALTED_DISTRIBUTION(
            "salted_distribution",
            "world-model.salted-distribution",
            "Salted Distribution",
            "World",
            WORLD,
            SettingType.BOOLEAN,
            0,
            0,
            0,
            List.of("on", "off"),
            List.of(),
            "Enables server-private fake ore signals backed by the salt model.",
            OreveilConfig::saltedDistributionEnabled
        ),
        XRAY_PROFILE(
            "xray_profile",
            "world-model.xray-profile",
            "Xray Profile",
            "World",
            WORLD,
            SettingType.ENUM,
            0,
            0,
            0,
            XrayProfile.configNames(),
            XrayProfile.configNames(),
            "Preset controlling the fake ore density and performance tradeoff. Salt density remains the baseline.",
            config -> config.xrayProfile().configName()
        ),
        SALT_DENSITY(
            "salt_density",
            "world-model.salt-density",
            "Salt Density",
            "World",
            WORLD,
            SettingType.INTEGER,
            1,
            256,
            8,
            List.of("32", "64", "96"),
            List.of(),
            "Number of salt placement attempts per chunk.",
            OreveilConfig::saltDensity
        ),
        SALT_SECRET(
            "salt_secret",
            "world-model.salt-secret",
            "Salt Secret",
            "World",
            WORLD,
            SettingType.LONG,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            1,
            List.of("0", "123456789"),
            List.of(),
            "Server-private long used to make salted fake ore placement independent from the public seed.",
            OreveilConfig::saltSecret
        ),
        WORLD_GENERATION_ENABLED(
            "world_generation",
            "world-generation.enabled",
            "World Generation",
            "World",
            WORLD,
            SettingType.BOOLEAN,
            0,
            0,
            0,
            List.of("on", "off"),
            List.of(),
            "Enables Oreveil's managed world generation features.",
            config -> config.worldGeneration().enabled()
        ),
        WORLD_GENERATION_EXPERIMENTAL(
            "world_experimental",
            "world-generation.experimental",
            "World Experimental",
            "World",
            WORLD,
            SettingType.BOOLEAN,
            0,
            0,
            0,
            List.of("on", "off"),
            List.of(),
            "Allows experimental managed world generation behavior.",
            config -> config.worldGeneration().experimental()
        ),
        WORLD_TARGET(
            "world_target",
            "world-generation.target-world",
            "World Target",
            "World",
            WORLD,
            SettingType.STRING,
            0,
            0,
            0,
            List.of("oreveil"),
            List.of(),
            "Name of the managed world Oreveil creates, regenerates, and targets.",
            config -> config.worldGeneration().targetWorldName()
        ),
        WORLD_ENVIRONMENT(
            "world_environment",
            "world-generation.environment",
            "World Environment",
            "World",
            WORLD,
            SettingType.ENUM,
            0,
            0,
            0,
            List.of("NORMAL", "NETHER", "THE_END"),
            List.of("NORMAL", "NETHER", "THE_END"),
            "Bukkit environment used when creating the managed world.",
            config -> config.worldGeneration().environment().name()
        ),
        WORLD_BACKUP_ON_REGENERATE(
            "world_backup",
            "world-generation.backup-on-regenerate",
            "World Backup",
            "World",
            WORLD,
            SettingType.BOOLEAN,
            0,
            0,
            0,
            List.of("on", "off"),
            List.of(),
            "Backs up the managed world folder before regeneration.",
            config -> config.worldGeneration().backupOnRegenerate()
        ),
        WORLD_STRUCTURES(
            "world_structures",
            "world-generation.generate-structures",
            "World Structures",
            "World",
            WORLD,
            SettingType.BOOLEAN,
            0,
            0,
            0,
            List.of("on", "off"),
            List.of(),
            "Controls vanilla structure generation for the managed world.",
            config -> config.worldGeneration().generateStructures()
        ),
        WORLD_SEED(
            "world_seed",
            "world-generation.seed",
            "World Seed",
            "World",
            WORLD,
            SettingType.LONG,
            Long.MIN_VALUE,
            Long.MAX_VALUE,
            1,
            List.of("random", "123456789"),
            List.of(),
            "Optional managed world seed. Use random to clear the configured seed.",
            config -> config.worldGeneration().configuredSeed()
        ),
        ORE_REMIX_ATTEMPTS(
            "ore_remix_attempts",
            "world-generation.ore-remix-attempts-per-chunk",
            "Ore Remix Attempts",
            "World",
            WORLD,
            SettingType.INTEGER,
            0,
            128,
            4,
            List.of("12", "18", "24"),
            List.of(),
            "Number of managed-world ore remix attempts per chunk.",
            config -> config.worldGeneration().oreRemixAttemptsPerChunk()
        ),
        TERRAIN_TWEAK_ATTEMPTS(
            "terrain_tweak_attempts",
            "world-generation.terrain-adjustment-attempts-per-chunk",
            "Terrain Tweak Attempts",
            "World",
            WORLD,
            SettingType.INTEGER,
            0,
            64,
            2,
            List.of("4", "8", "12"),
            List.of(),
            "Number of managed-world terrain adjustment attempts per chunk.",
            config -> config.worldGeneration().terrainAdjustmentAttemptsPerChunk()
        ),
        RUIN_FRAGMENT_CHANCE(
            "ruin_fragment_chance",
            "world-generation.ruin-fragment-chance",
            "Ruin Fragment Chance",
            "World",
            WORLD,
            SettingType.DOUBLE,
            0.0D,
            1.0D,
            0.01D,
            List.of("0.0", "0.02", "0.05"),
            List.of(),
            "Chance from 0.0 to 1.0 for ruin fragments during managed world generation.",
            config -> config.worldGeneration().ruinFragmentChance()
        );

        private static final Object INVALID_VALUE = new Object();

        private final String id;
        private final String path;
        private final String displayName;
        private final String sectionTitle;
        private final TextColor sectionColor;
        private final SettingType type;
        private final double min;
        private final double max;
        private final double step;
        private final List<String> suggestions;
        private final List<String> options;
        private final String description;
        private final Function<OreveilConfig, Object> reader;

        ConfigSetting(
            String id,
            String path,
            String displayName,
            String sectionTitle,
            TextColor sectionColor,
            SettingType type,
            double min,
            double max,
            double step,
            List<String> suggestions,
            List<String> options,
            String description,
            Function<OreveilConfig, Object> reader
        ) {
            this.id = id;
            this.path = path;
            this.displayName = displayName;
            this.sectionTitle = sectionTitle;
            this.sectionColor = sectionColor;
            this.type = type;
            this.min = min;
            this.max = max;
            this.step = step;
            this.suggestions = suggestions;
            this.options = options;
            this.description = description;
            this.reader = reader;
        }

        String id() {
            return id;
        }

        String path() {
            return path;
        }

        String displayName() {
            return displayName;
        }

        String sectionTitle() {
            return sectionTitle;
        }

        TextColor sectionColor() {
            return sectionColor;
        }

        SettingType type() {
            return type;
        }

        double step() {
            return step;
        }

        String description() {
            return description;
        }

        Object read(OreveilConfig config) {
            return reader.apply(config);
        }

        List<String> suggestions() {
            return suggestions;
        }

        int clamp(int value) {
            return (int) Math.max(min, Math.min(max, value));
        }

        String exampleValue() {
            return suggestions.isEmpty() ? formatDefaultExample() : suggestions.get(0);
        }

        String format(Object value) {
            if (value == null) {
                return "random";
            }
            if (value instanceof Boolean booleanValue) {
                return booleanValue ? "on" : "off";
            }
            return String.valueOf(value);
        }

        Object parse(String raw) {
            return switch (type) {
                case BOOLEAN -> parseBoolean(raw);
                case INTEGER -> parseInteger(raw);
                case DOUBLE -> parseDouble(raw);
                case LONG -> parseLong(raw);
                case STRING -> raw.isBlank() ? INVALID_VALUE : raw;
                case ENUM -> parseEnum(raw);
            };
        }

        private Object parseBoolean(String raw) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "true", "on", "yes", "enable", "enabled" -> true;
                case "false", "off", "no", "disable", "disabled" -> false;
                default -> INVALID_VALUE;
            };
        }

        private Object parseInteger(String raw) {
            try {
                return clamp(Integer.parseInt(raw));
            } catch (NumberFormatException ignored) {
                return INVALID_VALUE;
            }
        }

        private Object parseDouble(String raw) {
            try {
                double value = Double.parseDouble(raw);
                return Math.max(min, Math.min(max, value));
            } catch (NumberFormatException ignored) {
                return INVALID_VALUE;
            }
        }

        private Object parseLong(String raw) {
            if (this == WORLD_SEED && raw.equalsIgnoreCase("random")) {
                return null;
            }
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                return INVALID_VALUE;
            }
        }

        private Object parseEnum(String raw) {
            for (String option : options) {
                if (option.equalsIgnoreCase(raw)) {
                    return option;
                }
            }
            return INVALID_VALUE;
        }

        private String formatDefaultExample() {
            return switch (type) {
                case BOOLEAN -> "on";
                case INTEGER -> String.valueOf((int) min);
                case DOUBLE -> String.valueOf(min);
                case LONG -> "0";
                case STRING -> "value";
                case ENUM -> options.isEmpty() ? "value" : options.get(0);
            };
        }

        static ConfigSetting fromId(String raw) {
            for (ConfigSetting value : values()) {
                if (value.id.equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            return null;
        }

        static List<ConfigSetting> booleanSettings() {
            return Arrays.stream(values())
                .filter(setting -> setting.type == SettingType.BOOLEAN)
                .toList();
        }

        static List<String> settingIds() {
            return Arrays.stream(values())
                .map(ConfigSetting::id)
                .sorted()
                .toList();
        }

        static List<String> sectionIds() {
            return Arrays.stream(values())
                .map(setting -> setting.sectionTitle.toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
        }

        static List<ConfigSetting> bySection(String section) {
            return Arrays.stream(values())
                .filter(setting -> setting.sectionTitle.equalsIgnoreCase(section))
                .toList();
        }
    }
}
