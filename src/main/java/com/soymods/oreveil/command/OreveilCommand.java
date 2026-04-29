package com.soymods.oreveil.command;

import com.soymods.oreveil.bootstrap.OreveilPlugin;
import com.soymods.oreveil.config.OreveilConfig;
import com.soymods.oreveil.exposure.ExposureService;
import java.util.ArrayList;
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
    private static final TextColor TOGGLES = TextColor.color(0xF4B942);
    private static final TextColor ERROR = TextColor.color(0xFF6B6B);
    private static final TextColor ACTIVE = NamedTextColor.WHITE;

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
            case "status" -> handleStatus(sender);
            case "ores" -> {
                sendOreMenu(sender);
                yield true;
            }
            case "ore" -> handleOreToggle(sender, label, args);
            default -> {
                sendError(sender, "Unknown subcommand. Use /" + label + " help.");
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("help", "reload", "inspect", "status", "ores", "ore"));
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("ore")) {
            return filter(args[1], List.of("toggle"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("ore") && args[1].equalsIgnoreCase("toggle")) {
            return filter(args[2], plugin.candidateOreMaterials().stream().map(Enum::name).toList());
        }
        return List.of();
    }

    private boolean handleReload(CommandSender sender) {
        OreveilConfig config = plugin.reloadOreveilConfig();
        sendSectionLine(
            sender,
            "Status",
            STATUS,
            "Reloaded. transport=" + plugin.obfuscationService().transportName()
                + ", protected ores=" + config.protectedOres().size()
                + ", radius=" + config.liveSyncRadiusBlocks()
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

        sender.sendMessage(sectionHeader("Status", STATUS));
        sender.sendMessage(line("Block", STATUS, block.getType() + " @ " + block.getX() + ", " + block.getY() + ", " + block.getZ()));
        sender.sendMessage(line("Protected", ORES, String.valueOf(protectedOre)));
        sender.sendMessage(line("Exposed", ORES, String.valueOf(exposed)));
        sender.sendMessage(line("Client View", STATUS, visibleMaterial.name()));
        sender.sendMessage(line("Reasons", STATUS, reasons.isEmpty() ? "none" : String.join("; ", reasons)));
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        OreveilConfig config = plugin.oreveilConfig();
        sender.sendMessage(sectionHeader("Status", STATUS));
        sender.sendMessage(line("Transport", STATUS, plugin.obfuscationService().transportName()));
        sender.sendMessage(line("Protected Ores", ORES, String.valueOf(config.protectedOres().size())));
        sender.sendMessage(line("Live Sync Radius", TOGGLES, String.valueOf(config.liveSyncRadiusBlocks())));
        sender.sendMessage(line("Chunk Prime Radius", TOGGLES, String.valueOf(config.initialSyncChunkRadius())));
        sender.sendMessage(line("Reveal On Exposure", STATUS, String.valueOf(config.revealOnExposure())));
        sender.sendMessage(line("Non-Occluding Reveal", STATUS, String.valueOf(config.revealNextToNonOccludingBlocks())));
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

        plugin.toggleGlobalProtectedOre(material);
        sendSectionLine(sender, "Ores", ORES, "Toggled " + material.name() + ".");
        sendOreMenu(sender);
        return true;
    }

    private void sendOreMenu(CommandSender sender) {
        List<Material> active = plugin.oreveilConfig().protectedOres().stream().sorted(java.util.Comparator.comparing(Enum::name)).toList();
        sender.sendMessage(sectionHeader("Ores", ORES));
        sender.sendMessage(Component.text("Click an ore to toggle whether Oreveil hides it.", BASE));

        Component row = Component.empty();
        int count = 0;
        for (Material material : plugin.candidateOreMaterials()) {
            boolean enabled = active.contains(material);
            Component ore = Component.text(material.name(), enabled ? ACTIVE : BASE)
                .clickEvent(ClickEvent.runCommand("/oreveil ore toggle " + material.name()))
                .hoverEvent(HoverEvent.showText(Component.text((enabled ? "Disable " : "Enable ") + material.name(), BASE)));

            if (count > 0) {
                row = row.append(Component.text("  •  ", TOGGLES));
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
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(sectionHeader("Oreveil", TOGGLES));
        sender.sendMessage(helpLine("/" + label + " status", "live plugin summary", STATUS));
        sender.sendMessage(helpLine("/" + label + " inspect", "inspect the targeted block", STATUS));
        sender.sendMessage(helpLine("/" + label + " ores", "open the clickable ore selector", ORES));
        sender.sendMessage(helpLine("/" + label + " reload", "reload config and runtime state", TOGGLES));
    }

    private List<String> filter(String prefix, List<String> values) {
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
            .sorted()
            .toList();
    }

    private Component sectionHeader(String title, TextColor accent) {
        return Component.text("[", BASE)
            .append(Component.text(title, accent))
            .append(Component.text("] ", BASE))
            .append(Component.text("Oreveil", BASE));
    }

    private Component line(String label, TextColor accent, String value) {
        return Component.text(label + ": ", accent).append(Component.text(value, BASE));
    }

    private Component helpLine(String command, String description, TextColor accent) {
        return Component.text(command, accent).append(Component.text(" - " + description, BASE));
    }

    private void sendSectionLine(CommandSender sender, String title, TextColor accent, String message) {
        sender.sendMessage(Component.text("[", BASE)
            .append(Component.text(title, accent))
            .append(Component.text("] ", BASE))
            .append(Component.text(message, BASE)));
    }

    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[", BASE)
            .append(Component.text("Error", ERROR))
            .append(Component.text("] ", BASE))
            .append(Component.text(message, BASE)));
    }
}
