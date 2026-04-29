package com.soymods.oreveil.command;

import com.soymods.oreveil.bootstrap.OreveilPlugin;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.exposure.ExposureService;
import com.soymods.oreveil.obfuscation.transport.TransportMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
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
            sendHelp(sender, label);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                sendHelp(sender, label);
                yield true;
            }
            case "reload" -> handleReload(sender);
            case "inspect" -> handleInspect(sender);
            case "status" -> handleStatus(sender, label);
            case "ores" -> {
                sendOreMenu(sender);
                yield true;
            }
            case "ore" -> handleOreToggle(sender, label, args);
            case "toggle" -> handleToggle(sender, label, args);
            case "set" -> handleSet(sender, label, args);
            case "transport" -> handleTransport(sender, label, args);
            default -> {
                sendError(sender, "Unknown subcommand. Use /" + label + " help.");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("help", "reload", "inspect", "status", "ores", "ore", "toggle", "set", "transport"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ore")) {
            return filter(args[1], List.of("toggle"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ore") && args[1].equalsIgnoreCase("toggle")) {
            return filter(args[2], plugin.candidateOreMaterials().stream().map(Enum::name).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            return filter(args[1], Arrays.stream(ToggleSetting.values()).map(ToggleSetting::id).toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return filter(args[1], Arrays.stream(IntSetting.values()).map(IntSetting::id).toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            IntSetting setting = IntSetting.fromId(args[1]);
            if (setting == null) {
                return List.of();
            }
            return filter(args[2], setting.suggestions());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("transport")) {
            return filter(args[1], Arrays.stream(TransportMode.values()).map(Enum::name).toList());
        }
        return List.of();
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
        Material visibleMaterial = plugin.obfuscationService().getClientVisibleMaterial(block);
        List<String> reasons = protectedOre ? new ArrayList<>(exposureService.describeExposure(block)) : List.of();

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
            Component.text("Client sees ", BASE)
                .append(highlight(visibleMaterial.name(), STATUS))
                .append(Component.text(".", BASE))
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
            Component.text("Transport: ", BASE)
                .append(transportPicker(config, label))
        );
        sendMessage(
            sender,
            "Status",
            STATUS,
            Component.text("Protected ores: ", BASE)
                .append(highlight(String.valueOf(config.protectedOres().size()), ORES))
                .append(Component.text("  Selector: ", BASE))
                .append(action("/" + label + " ores", "open", ORES, "Open the clickable ore selector."))
        );
        sendMessage(
            sender,
            "Controls",
            CONTROLS,
            numericControl(IntSetting.LIVE_SYNC_RADIUS, config.liveSyncRadiusBlocks(), label)
        );
        sendMessage(
            sender,
            "Controls",
            CONTROLS,
            numericControl(IntSetting.CHUNK_PRIME_RADIUS, config.initialSyncChunkRadius(), label)
        );
        sendMessage(
            sender,
            "Controls",
            CONTROLS,
            numericControl(IntSetting.REVEAL_PROXIMITY, config.revealProximityBlocks(), label)
        );
        sendMessage(
            sender,
            "Controls",
            CONTROLS,
            toggleControl(ToggleSetting.REVEAL_ON_EXPOSURE, config.revealOnExposure(), label)
        );
        sendMessage(
            sender,
            "Controls",
            CONTROLS,
            toggleControl(ToggleSetting.NON_OCCLUDING_REVEAL, config.revealNextToNonOccludingBlocks(), label)
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            toggleControl(ToggleSetting.OBFUSCATION_ENABLED, config.obfuscationEnabled(), label)
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            toggleControl(ToggleSetting.SALTED_DISTRIBUTION, config.saltedDistributionEnabled(), label)
        );
        sendMessage(
            sender,
            "World",
            WORLD,
            numericControl(IntSetting.SALT_DENSITY, config.saltDensity(), label)
        );
        sendDivider(sender);
        return true;
    }

    private boolean handleOreToggle(CommandSender sender, String label, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("toggle")) {
            sendError(sender, "Use /" + label + " ore toggle <ore>.");
            return true;
        }

        Material material = Material.matchMaterial(args[2], false);
        if (material == null || !material.isBlock()) {
            sendError(sender, "Unknown block material " + args[2] + ".");
            return true;
        }

        boolean enabledBefore = plugin.oreveilConfig().protectedOres().contains(material);
        plugin.toggleGlobalProtectedOre(material);
        sendMessage(
            sender,
            "Ores",
            ORES,
            Component.text(enabledBefore ? "Stopped hiding " : "Now hiding ", BASE)
                .append(highlight(material.name(), enabledBefore ? MUTED : ACTIVE))
                .append(Component.text(".", BASE))
        );
        sendOreMenu(sender);
        return true;
    }

    private boolean handleToggle(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sendError(sender, "Use /" + label + " toggle <setting>.");
            return true;
        }

        ToggleSetting setting = ToggleSetting.fromId(args[1]);
        if (setting == null) {
            sendError(sender, "Unknown toggle " + args[1] + ".");
            return true;
        }

        OreveilConfig config = plugin.toggleBooleanSetting(setting.path());
        boolean value = setting.read(config);
        sendMessage(
            sender,
            setting.sectionTitle(),
            setting.sectionColor(),
            Component.text(setting.displayName() + " is now ", BASE)
                .append(highlight(onOff(value), value ? setting.sectionColor() : MUTED))
                .append(Component.text(".", BASE))
        );
        return setting == ToggleSetting.SALTED_DISTRIBUTION || setting == ToggleSetting.OBFUSCATION_ENABLED
            ? handleStatus(sender, label)
            : handleStatus(sender, label);
    }

    private boolean handleSet(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sendError(sender, "Use /" + label + " set <setting> <value>.");
            return true;
        }

        IntSetting setting = IntSetting.fromId(args[1]);
        if (setting == null) {
            sendError(sender, "Unknown numeric setting " + args[1] + ".");
            return true;
        }

        int value;
        try {
            value = Integer.parseInt(args[2]);
        } catch (NumberFormatException ignored) {
            sendError(sender, args[2] + " is not a valid integer.");
            return true;
        }

        int clamped = setting.clamp(value);
        plugin.setIntegerSetting(setting.path(), clamped);
        sendMessage(
            sender,
            setting.sectionTitle(),
            setting.sectionColor(),
            Component.text("Set ", BASE)
                .append(highlight(setting.displayName(), setting.sectionColor()))
                .append(Component.text(" to ", BASE))
                .append(highlight(String.valueOf(clamped), setting.sectionColor()))
                .append(Component.text(".", BASE))
        );
        return handleStatus(sender, label);
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

    private void sendHelp(CommandSender sender, String label) {
        sendDivider(sender);
        sendMessage(sender, "Oreveil", CONTROLS, Component.text("Command overview.", BASE));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " status", STATUS, "Shows the live control panel."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " inspect", STATUS, "Inspects the targeted block."));
        sendMessage(sender, "Ores", ORES, commandLine("/" + label + " ores", ORES, "Opens the clickable ore selector."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " toggle <setting>", CONTROLS, "Toggles a boolean setting."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " set <setting> <value>", CONTROLS, "Sets a numeric setting."));
        sendMessage(sender, "Status", STATUS, commandLine("/" + label + " transport <mode>", STATUS, "Changes the active transport mode."));
        sendMessage(sender, "Controls", CONTROLS, commandLine("/" + label + " reload", CONTROLS, "Reloads config and runtime state."));
        sendDivider(sender);
    }

    private Component toggleControl(ToggleSetting setting, boolean value, String label) {
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

    private Component numericControl(IntSetting setting, int value, String label) {
        return Component.text(setting.displayName() + ": ", BASE)
            .append(highlight(String.valueOf(value), setting.sectionColor()))
            .append(Component.text("  ", BASE))
            .append(action(
                "/" + label + " set " + setting.id() + " " + setting.clamp(value - setting.step()),
                "-" + setting.step(),
                MUTED,
                "Lower " + setting.displayName().toLowerCase(Locale.ROOT) + "."
            ))
            .append(Component.text(" ", BASE))
            .append(action(
                "/" + label + " set " + setting.id() + " " + setting.clamp(value + setting.step()),
                "+" + setting.step(),
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

    private Component action(String command, String text, TextColor color, String hover) {
        return Component.text(text, color)
            .clickEvent(ClickEvent.runCommand(command))
            .hoverEvent(HoverEvent.showText(Component.text(hover, BASE)));
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

    private void sendDivider(CommandSender sender) {
        sender.sendMessage(Component.text("----------------------------------------", MUTED));
    }

    private void sendMessage(CommandSender sender, String title, TextColor accent, Component body) {
        sender.sendMessage(prefix(title, accent).append(body));
    }

    private void sendError(CommandSender sender, String message) {
        sendMessage(sender, "Error", ERROR, Component.text(message, BASE));
    }

    private enum ToggleSetting {
        OBFUSCATION_ENABLED("obfuscation", "obfuscation.enabled", "Obfuscation", "World", WORLD) {
            @Override
            boolean read(OreveilConfig config) {
                return config.obfuscationEnabled();
            }
        },
        REVEAL_ON_EXPOSURE("reveal_on_exposure", "obfuscation.reveal-on-exposure", "Reveal On Exposure", "Controls", CONTROLS) {
            @Override
            boolean read(OreveilConfig config) {
                return config.revealOnExposure();
            }
        },
        NON_OCCLUDING_REVEAL(
            "non_occluding_reveal",
            "obfuscation.reveal-next-to-non-occluding-blocks",
            "Non-Occluding Reveal",
            "Controls",
            CONTROLS
        ) {
            @Override
            boolean read(OreveilConfig config) {
                return config.revealNextToNonOccludingBlocks();
            }
        },
        SALTED_DISTRIBUTION("salted_distribution", "world-model.salted-distribution", "Salted Distribution", "World", WORLD) {
            @Override
            boolean read(OreveilConfig config) {
                return config.saltedDistributionEnabled();
            }
        };

        private final String id;
        private final String path;
        private final String displayName;
        private final String sectionTitle;
        private final TextColor sectionColor;

        ToggleSetting(String id, String path, String displayName, String sectionTitle, TextColor sectionColor) {
            this.id = id;
            this.path = path;
            this.displayName = displayName;
            this.sectionTitle = sectionTitle;
            this.sectionColor = sectionColor;
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

        abstract boolean read(OreveilConfig config);

        static ToggleSetting fromId(String raw) {
            for (ToggleSetting value : values()) {
                if (value.id.equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            return null;
        }
    }

    private enum IntSetting {
        LIVE_SYNC_RADIUS("live_sync_radius", "obfuscation.live-sync-radius-blocks", "Live Sync Radius", "Controls", CONTROLS, 16, 256, 16) {
            @Override
            List<String> suggestions() {
                return List.of("48", "64", "96");
            }
        },
        CHUNK_PRIME_RADIUS("chunk_prime_radius", "obfuscation.initial-sync-chunk-radius", "Chunk Prime Radius", "Controls", CONTROLS, 0, 8, 1) {
            @Override
            List<String> suggestions() {
                return List.of("0", "1", "2");
            }
        },
        REVEAL_PROXIMITY("reveal_proximity", "obfuscation.reveal-proximity-blocks", "Reveal Proximity", "Controls", CONTROLS, 0, 64, 2) {
            @Override
            List<String> suggestions() {
                return List.of("0", "6", "8");
            }
        },
        SALT_DENSITY("salt_density", "world-model.salt-density", "Salt Density", "World", WORLD, 1, 256, 8) {
            @Override
            List<String> suggestions() {
                return List.of("32", "64", "96");
            }
        };

        private final String id;
        private final String path;
        private final String displayName;
        private final String sectionTitle;
        private final TextColor sectionColor;
        private final int min;
        private final int max;
        private final int step;

        IntSetting(
            String id,
            String path,
            String displayName,
            String sectionTitle,
            TextColor sectionColor,
            int min,
            int max,
            int step
        ) {
            this.id = id;
            this.path = path;
            this.displayName = displayName;
            this.sectionTitle = sectionTitle;
            this.sectionColor = sectionColor;
            this.min = min;
            this.max = max;
            this.step = step;
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

        int step() {
            return step;
        }

        int clamp(int value) {
            return Math.max(min, Math.min(max, value));
        }

        abstract List<String> suggestions();

        static IntSetting fromId(String raw) {
            for (IntSetting value : values()) {
                if (value.id.equalsIgnoreCase(raw)) {
                    return value;
                }
            }
            return null;
        }
    }
}
