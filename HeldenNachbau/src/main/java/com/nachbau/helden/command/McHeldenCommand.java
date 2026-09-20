package com.nachbau.helden.command;

import com.nachbau.helden.pack.StaticPackBuilder;
import com.nachbau.helden.state.GlyphAllocator;
import com.nachbau.helden.state.MultispawnManager;
import com.nachbau.helden.state.PlayerData;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.state.ServerState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class McHeldenCommand implements CommandExecutor, TabCompleter {

    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([a-zA-Z]+)");

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!sender.hasPermission("mchelden.admin")) {
            sender.sendMessage(Component.text("Keine Berechtigung!").color(NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            usage(sender);
            return true;
        }

        ServerState state = PluginStateManager.getServerState();

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "link" -> link(sender, state, args);
            case "hearts" -> hearts(sender, args);
            case "glow" -> glow(sender, state, args);
            case "multispawn" -> multispawn(sender, args);
            case "deathinventory" -> deathinventory(sender, state, args);
            case "blocker" -> blocker(sender, state, args);
            case "fastleafdecay" -> {
                if (args.length < 2) return true;
                state.fastLeafDecay = Boolean.parseBoolean(args[1]);
                PluginStateManager.save();
                sender.sendMessage(Component.text("Fast Leaf Decay auf " + args[1] + " gesetzt").color(NamedTextColor.GREEN));
            }
            case "nametags" -> {
                if (args.length < 2) return true;
                state.nametagsEnabled = Boolean.parseBoolean(args[1]);
                PluginStateManager.save();
                sender.sendMessage(Component.text("Nametags auf " + args[1] + " gesetzt").color(NamedTextColor.GREEN));
            }
            case "limits" -> limits(sender, state, args);
            case "mode" -> mode(sender, state, args);
            case "pack" -> pack(sender, args);
            default -> usage(sender);
        }
        return true;
    }

    private void usage(CommandSender sender) {
        sender.sendMessage(Component.text(
                "Benutzung: /mchelden <link|hearts|glow|multispawn|deathinventory|blocker|fastleafdecay|nametags|limits|mode|pack> ...")
                .color(NamedTextColor.RED));
    }

    // ---------------------------------------------------------- link

    private void link(CommandSender sender, ServerState state, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("random")) {
            List<UUID> allUuids = new ArrayList<>(state.players.keySet());
            Collections.shuffle(allUuids);

            if (allUuids.size() % 2 != 0) {
                sender.sendMessage(Component.text(
                        "Ungerade Spieleranzahl! Ein Spieler bleibt ohne Linked Heart. Bitte wende dich an einen Admin.")
                        .color(NamedTextColor.RED));
                UUID oddPlayer = allUuids.remove(allUuids.size() - 1);
                PlayerData oddData = PluginStateManager.getPlayerData(oddPlayer);
                oddData.linkedPlayer = null;
                oddData.linkedPlayerName = null;
            }

            for (int i = 0; i < allUuids.size(); i += 2) {
                UUID p1 = allUuids.get(i);
                UUID p2 = allUuids.get(i + 1);
                setLink(p1, p2);
            }
            PluginStateManager.save();
            sender.sendMessage(Component.text("Alle Spieler wurden in Zweier-Paare aufgeteilt!").color(NamedTextColor.GREEN));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text("Benutzung: /mchelden link <spieler1> <spieler2> | link random")
                    .color(NamedTextColor.RED));
            return;
        }

        OfflinePlayer p1 = Bukkit.getOfflinePlayer(args[1]);
        OfflinePlayer p2 = Bukkit.getOfflinePlayer(args[2]);
        if (p1.getName() == null || p2.getName() == null) {
            sender.sendMessage(Component.text("Spieler nicht gefunden!").color(NamedTextColor.RED));
            return;
        }

        setLink(p1.getUniqueId(), p2.getUniqueId());
        PluginStateManager.save();
        sender.sendMessage(Component.text("Spieler gelinkt an " + p2.getName()).color(NamedTextColor.GREEN));
        sender.sendMessage(Component.text(
                "Hinweis: falls einer der Koepfe noch nicht im Pack ist, /mchelden pack build ausfuehren und neu hochladen.")
                .color(NamedTextColor.GRAY));
    }

    private void setLink(UUID a, UUID b) {
        PlayerData data1 = PluginStateManager.getPlayerData(a);
        PlayerData data2 = PluginStateManager.getPlayerData(b);

        OfflinePlayer op1 = Bukkit.getOfflinePlayer(a);
        OfflinePlayer op2 = Bukkit.getOfflinePlayer(b);

        data1.linkedPlayer = b;
        data1.linkedPlayerName = op2.getName();
        data2.linkedPlayer = a;
        data2.linkedPlayerName = op1.getName();

        // Beide bekommen (falls noch nicht geschehen) ein eigenes Kopf-Glyph
        // zugewiesen, damit "pack build" sie mit aufnimmt.
        GlyphAllocator.glyphFor(a);
        GlyphAllocator.glyphFor(b);
    }

    // ---------------------------------------------------------- pack

    private void pack(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("Benutzung: /mchelden pack <build|seturl <url> <sha1>>")
                    .color(NamedTextColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("build")) {
            sender.sendMessage(Component.text("Baue Pack...").color(NamedTextColor.GRAY));
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(
                    com.nachbau.helden.HeldenPlugin.instance, () -> {
                        try {
                            StaticPackBuilder.BuildResult result = StaticPackBuilder.build();
                            sender.sendMessage(Component.text("Pack gebaut: " + result.file().getPath())
                                    .color(NamedTextColor.GREEN));
                            sender.sendMessage(Component.text("SHA1: " + result.sha1Hex()).color(NamedTextColor.GREEN));
                            sender.sendMessage(Component.text(result.headCount() + " Koepfe enthalten.")
                                    .color(NamedTextColor.GRAY));
                            sender.sendMessage(Component.text(
                                    "Lade diese Datei jetzt manuell z.B. bei mc-packs.net hoch und trag den Link ein mit:")
                                    .color(NamedTextColor.GRAY));
                            sender.sendMessage(Component.text(
                                    "/mchelden pack seturl <link> " + result.sha1Hex()).color(NamedTextColor.AQUA));
                        } catch (Exception e) {
                            sender.sendMessage(Component.text("Fehler beim Bauen: " + e.getMessage())
                                    .color(NamedTextColor.RED));
                        }
                    });
            return;
        }

        if (args[1].equalsIgnoreCase("seturl")) {
            if (args.length < 4) {
                sender.sendMessage(Component.text("Benutzung: /mchelden pack seturl <url> <sha1>")
                        .color(NamedTextColor.RED));
                return;
            }

            String url = args[2];
            String sha1 = args[3].trim().toLowerCase(Locale.ROOT);

            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                sender.sendMessage(Component.text(
                        "Die URL muss mit http:// oder https:// beginnen! Bekommen: " + url)
                        .color(NamedTextColor.RED));
                return;
            }

            if (!sha1.matches("[0-9a-f]{40}")) {
                sender.sendMessage(Component.text(
                        "Der SHA1 ist ungueltig - er muss genau 40 Hex-Zeichen lang sein (bekommen: "
                                + sha1.length() + " Zeichen: '" + sha1 + "'). "
                                + "Kopier ihn nochmal 1:1 aus der Ausgabe von /mchelden pack build.")
                        .color(NamedTextColor.RED));
                return;
            }

            ServerState state = PluginStateManager.getServerState();
            state.packUrl = url;
            state.packSha1 = sha1;
            PluginStateManager.save();
            sender.sendMessage(Component.text("Pack-URL gesetzt. Neue Spieler bekommen sie beim Join.")
                    .color(NamedTextColor.GREEN));
            return;
        }

        sender.sendMessage(Component.text("Benutzung: /mchelden pack <build|seturl <url> <sha1>>")
                .color(NamedTextColor.RED));
    }

    // ---------------------------------------------------------- hearts

    private void hearts(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Benutzung: /mchelden hearts <spieler> <anzahl>").color(NamedTextColor.RED));
            return;
        }
        OfflinePlayer p = Bukkit.getOfflinePlayer(args[1]);
        if (p.getName() == null) {
            sender.sendMessage(Component.text("Spieler nicht gefunden!").color(NamedTextColor.RED));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Keine gueltige Zahl.").color(NamedTextColor.RED));
            return;
        }

        PlayerData data = PluginStateManager.getPlayerData(p.getUniqueId());
        data.lives = amount;
        PluginStateManager.save();
        sender.sendMessage(Component.text(p.getName() + " hat " + amount + " Herzen").color(NamedTextColor.GREEN));

        if (amount <= 0) {
            sender.sendMessage(Component.text("Keine Herzen mehr!").color(NamedTextColor.RED));
            Player online = Bukkit.getPlayer(p.getUniqueId());
            if (online != null) {
                sender.sendMessage(Component.text("Ausgeschieden!").color(NamedTextColor.RED));
                online.sendMessage(Component.text("Du hast alle Herzen verloren und bist damit ausgeschieden!")
                        .color(NamedTextColor.RED));
                online.setGameMode(org.bukkit.GameMode.SPECTATOR);
            }
        }
    }

    // ---------------------------------------------------------- glow

    private void glow(CommandSender sender, ServerState state, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Benutzung: /mchelden glow <duration|interval> <zeit>").color(NamedTextColor.RED));
            return;
        }
        int ticks = parseTimeTicks(args[2]);
        if (args[1].equalsIgnoreCase("duration")) {
            state.glowDurationTicks = ticks;
            PluginStateManager.save();
            sender.sendMessage(Component.text("Glow Duration auf " + args[2] + " gesetzt").color(NamedTextColor.GREEN));
        } else if (args[1].equalsIgnoreCase("interval")) {
            state.glowIntervalTicks = ticks;
            PluginStateManager.save();
            sender.sendMessage(Component.text("Glow Interval auf " + args[2] + " gesetzt").color(NamedTextColor.GREEN));
        }
    }

    /** Parst Zeitangaben wie "10s", "5min", "2h", "1d" in Ticks (20/s). */
    private int parseTimeTicks(String value) {
        Matcher m = TIME_PATTERN.matcher(value);
        if (!m.matches()) return 0;
        int amount = Integer.parseInt(m.group(1));
        String unit = m.group(2).toLowerCase(Locale.ROOT);

        int secondsMultiplier = switch (unit) {
            case "sek", "s", "sec", "sekunden", "seconds" -> 1;
            case "min", "m", "minuten", "minutes" -> 60;
            case "h", "stunden", "hours" -> 3600;
            case "d", "days", "tage" -> 86400;
            default -> 1;
        };
        return amount * secondsMultiplier * 20;
    }

    // ---------------------------------------------------------- multispawn

    private void multispawn(CommandSender sender, String[] args) {
        if (args.length < 2) return;
        if (!(sender instanceof Player player) && !args[1].equalsIgnoreCase("delete")) {
            sender.sendMessage(Component.text("Nur fuer Spieler (ausser delete).").color(NamedTextColor.RED));
            return;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) return;
                MultispawnManager.addSpawn(args[2], (Player) sender);
                sender.sendMessage(Component.text("Multispawn hinzugefügt").color(NamedTextColor.GREEN));
            }
            case "delete" -> {
                if (args.length < 3) return;
                MultispawnManager.deleteSpawn(args[2]);
                sender.sendMessage(Component.text("Multispawn gelöscht").color(NamedTextColor.GREEN));
            }
            case "tp" -> {
                if (args.length < 3) return;
                MultispawnManager.tpToSpawn(args[2], (Player) sender);
            }
            case "join" -> {
                if (args.length < 4) return;
                Player target = Bukkit.getPlayer(args[3]);
                if (target == null) {
                    sender.sendMessage(Component.text("Spieler nicht gefunden!").color(NamedTextColor.RED));
                    return;
                }
                MultispawnManager.joinSpawn(args[2], target);
                sender.sendMessage(Component.text("Spieler zu Multispawn hinzugefügt").color(NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("Benutzung: /mchelden multispawn <add|delete|tp|join> ...")
                    .color(NamedTextColor.RED));
        }
    }

    // ---------------------------------------------------------- deathinventory

    private void deathinventory(CommandSender sender, ServerState state, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Benutzung: /mchelden deathinventory <combat|noncombat> <prozent>")
                    .color(NamedTextColor.RED));
            return;
        }
        int percent;
        try {
            percent = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Keine gueltige Zahl.").color(NamedTextColor.RED));
            return;
        }
        if (args[1].equalsIgnoreCase("combat")) {
            state.deathInventoryCombatKeep = percent;
        } else if (args[1].equalsIgnoreCase("noncombat")) {
            state.deathInventoryNonCombatKeep = percent;
        } else {
            return;
        }
        PluginStateManager.save();
        sender.sendMessage(Component.text("Death Inventory Keep Rate auf " + percent + "% gesetzt").color(NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------- blocker

    private void blocker(CommandSender sender, ServerState state, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Benutzung: /mchelden blocker <fireworkcrossbow|mending|nether|netherite|opgap|punch|totems|villagertrading> <true|false>")
                    .color(NamedTextColor.RED));
            return;
        }
        boolean enabled = Boolean.parseBoolean(args[2]);
        String typeLabel;

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "fireworkcrossbow" -> { state.blockFireworkCrossbow = enabled; typeLabel = "FireworkCrossbow"; }
            case "mending" -> { state.blockMending = enabled; typeLabel = "Mending"; }
            case "nether" -> { state.blockNether = enabled; typeLabel = "Nether"; }
            case "netherite" -> { state.blockNetherite = enabled; typeLabel = "Netherite"; }
            case "opgap" -> { state.blockOPGap = enabled; typeLabel = "OPGap"; }
            case "punch" -> { state.blockPunch = enabled; typeLabel = "Punch"; }
            case "totems" -> { state.blockTotems = enabled; typeLabel = "Totems"; }
            case "villagertrading" -> { state.blockVillagerTrading = enabled; typeLabel = "VillagerTrading"; }
            default -> {
                sender.sendMessage(Component.text("Unbekannter Blocker Typ!").color(NamedTextColor.RED));
                return;
            }
        }
        PluginStateManager.save();
        sender.sendMessage(Component.text("Blocker " + typeLabel + " auf " + enabled + " gesetzt").color(NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------- limits

    private void limits(CommandSender sender, ServerState state, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(Component.text("Benutzung: /mchelden limits <cobwebs|enderpearls> <anzahl>")
                    .color(NamedTextColor.RED));
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Keine gueltige Zahl.").color(NamedTextColor.RED));
            return;
        }
        String label;
        if (args[1].equalsIgnoreCase("cobwebs")) {
            state.maxCobwebs = amount;
            label = "Spinnweben";
        } else if (args[1].equalsIgnoreCase("enderpearls")) {
            state.maxEnderpearls = amount;
            label = "Enderperlen";
        } else {
            return;
        }
        PluginStateManager.save();
        sender.sendMessage(Component.text(label + " Limit auf " + amount + " gesetzt").color(NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------- mode

    private void mode(CommandSender sender, ServerState state, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("random")) {
            sender.sendMessage(Component.text("Benutzung: /mchelden mode random <true|false>").color(NamedTextColor.RED));
            return;
        }
        state.randomMultispawnMode = Boolean.parseBoolean(args[2]);
        PluginStateManager.save();
        sender.sendMessage(Component.text("Random Multispawn Mode auf " + args[2] + " gesetzt").color(NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------- tab complete

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, String[] args) {
        if (args.length == 1) {
            return filterStart(args[0], "link", "hearts", "glow", "multispawn", "deathinventory",
                    "blocker", "fastleafdecay", "nametags", "limits", "mode", "pack");
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "link" -> onlineNamesPlus(args[1], "random");
                case "glow" -> filterStart(args[1], "duration", "interval");
                case "multispawn" -> filterStart(args[1], "add", "delete", "tp", "join");
                case "deathinventory" -> filterStart(args[1], "combat", "noncombat");
                case "blocker" -> filterStart(args[1], "fireworkcrossbow", "mending", "nether", "netherite",
                        "opgap", "punch", "totems", "villagertrading");
                case "limits" -> filterStart(args[1], "cobwebs", "enderpearls");
                case "mode" -> filterStart(args[1], "random");
                case "pack" -> filterStart(args[1], "build", "seturl");
                case "fastleafdecay", "nametags" -> filterStart(args[1], "true", "false");
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3) {
            return switch (sub) {
                case "link" -> onlineNames(args[2]);
                case "multispawn" -> filterStart(args[2], "0", "1", "2", "3");
                case "blocker", "fastleafdecay", "nametags", "mode" -> filterStart(args[2], "true", "false");
                default -> Collections.emptyList();
            };
        }

        if (args.length == 4 && sub.equals("multispawn") && args[1].equalsIgnoreCase("join")) {
            return onlineNames(args[3]);
        }

        return Collections.emptyList();
    }

    private List<String> filterStart(String prefix, String... options) {
        return Arrays.stream(options)
                .filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private List<String> onlineNames(String prefix) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private List<String> onlineNamesPlus(String prefix, String... extra) {
        List<String> list = new ArrayList<>(onlineNames(prefix));
        for (String e : extra) {
            if (e.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) list.add(e);
        }
        return list;
    }
}
