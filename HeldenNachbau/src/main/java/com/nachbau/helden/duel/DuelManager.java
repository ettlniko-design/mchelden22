package com.nachbau.helden.duel;

import com.nachbau.helden.state.PlayerData;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Duell-System 1:1 aus dem Original: max. 2 gleichzeitige Duelle pro Spieler
 * (hostedDuels), Annahme per klickbarer Nachricht, automatisches Ende nach
 * 30 Minuten (DUEL_MAX_TICKS).
 */
public final class DuelManager {

    private static final int DUEL_MAX_TICKS = 36000; // 30 Minuten

    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final List<ActiveDuel> activeDuels = new ArrayList<>();

    public static final class ActiveDuel {
        public final UUID player1;
        public final UUID player2;
        public int ticksElapsed = 0;

        public ActiveDuel(UUID p1, UUID p2) {
            this.player1 = p1;
            this.player2 = p2;
        }
    }

    public void requestDuel(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(Component.text("Du kannst dich nicht selbst herausfordern!").color(NamedTextColor.RED));
            return;
        }

        PlayerData data = PluginStateManager.getPlayerData(sender.getUniqueId());
        if (data.hostedDuels >= 2) {
            sender.sendMessage(Component.text("Du hast bereits dein Limit von 2 Duellen erreicht!").color(NamedTextColor.RED));
            return;
        }

        if (isInDuel(sender.getUniqueId()) || isInDuel(target.getUniqueId())) {
            sender.sendMessage(Component.text("Einer von euch ist bereits in einem Duell!").color(NamedTextColor.RED));
            return;
        }

        pendingRequests.put(target.getUniqueId(), sender.getUniqueId());

        sender.sendMessage(ChatUtil.getPrefix().append(
                Component.text("Du hast " + target.getName() + " zu einem Duell herausgefordert.").color(NamedTextColor.GRAY)));

        Component accept = ((TextComponent) Component.text("[Akzeptieren]")).color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/duel accept"))
                .hoverEvent(HoverEvent.showText(Component.text("Klicke zum Akzeptieren")));
        Component deny = ((TextComponent) Component.text("[Ablehnen]")).color(NamedTextColor.RED)
                .clickEvent(ClickEvent.runCommand("/duel deny"))
                .hoverEvent(HoverEvent.showText(Component.text("Klicke zum Ablehnen")));

        target.sendMessage(ChatUtil.getPrefix().append(
                Component.text(sender.getName() + " hat dich zu einem Duell herausgefordert!\n").color(NamedTextColor.GRAY)));
        target.sendMessage(accept.append(Component.text(" ")).append(deny));
    }

    public void acceptDuel(Player player) {
        UUID senderUuid = pendingRequests.remove(player.getUniqueId());
        if (senderUuid == null) {
            player.sendMessage(Component.text("Du hast keine ausstehenden Duellanfragen!").color(NamedTextColor.RED));
            return;
        }

        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender == null) {
            player.sendMessage(Component.text("Der Spieler ist offline!").color(NamedTextColor.RED));
            return;
        }

        PlayerData senderData = PluginStateManager.getPlayerData(senderUuid);
        if (senderData.hostedDuels >= 2) {
            player.sendMessage(Component.text("Dieser Spieler hat sein Limit von 2 Duellen bereits erreicht!").color(NamedTextColor.RED));
            return;
        }

        senderData.hostedDuels++;
        PluginStateManager.getPlayerData(player.getUniqueId()).hostedDuels++;
        PluginStateManager.save();

        activeDuels.add(new ActiveDuel(senderUuid, player.getUniqueId()));

        Component msg = ChatUtil.getPrefix().append(
                ((TextComponent) Component.text("Das Duell mit ")).color(NamedTextColor.GOLD)
                        .append(Component.text(player.getName()).decorate(TextDecoration.BOLD))
                        .append(Component.text(" wurde akzeptiert.")));
        sender.sendMessage(msg);
        player.sendMessage(ChatUtil.getPrefix().append(
                Component.text("Das Duell mit " + sender.getName() + " wurde akzeptiert.").color(NamedTextColor.GOLD)));
    }

    public void denyDuel(Player player) {
        UUID senderUuid = pendingRequests.remove(player.getUniqueId());
        if (senderUuid == null) {
            player.sendMessage(Component.text("Du hast keine ausstehenden Duellanfragen!").color(NamedTextColor.RED));
            return;
        }
        player.sendMessage(ChatUtil.getPrefix().append(
                Component.text("Du hast die Duellanfrage abgelehnt.").color(NamedTextColor.GRAY)));
        Player sender = Bukkit.getPlayer(senderUuid);
        if (sender != null) {
            sender.sendMessage(ChatUtil.getPrefix().append(
                    Component.text("Das Duell mit " + player.getName() + " wurde abgelehnt.").color(NamedTextColor.GRAY)));
        }
    }

    public boolean isInDuel(UUID player) {
        return activeDuels.stream().anyMatch(d -> d.player1.equals(player) || d.player2.equals(player));
    }

    public boolean isDuelOpponent(UUID player, UUID other) {
        for (ActiveDuel d : activeDuels) {
            boolean p1 = d.player1.equals(player);
            boolean p2 = d.player2.equals(player);
            if (p1 && d.player2.equals(other)) return true;
            if (p2 && d.player1.equals(other)) return true;
        }
        return false;
    }

    public void endDuel(UUID player) {
        activeDuels.removeIf(d -> {
            if (d.player1.equals(player) || d.player2.equals(player)) {
                PluginStateManager.getPlayerData(d.player1).hostedDuels =
                        Math.max(0, PluginStateManager.getPlayerData(d.player1).hostedDuels - 1);
                PluginStateManager.getPlayerData(d.player2).hostedDuels =
                        Math.max(0, PluginStateManager.getPlayerData(d.player2).hostedDuels - 1);
                return true;
            }
            return false;
        });
        PluginStateManager.save();
    }

    public void tick() {
        Iterator<ActiveDuel> it = activeDuels.iterator();
        while (it.hasNext()) {
            ActiveDuel d = it.next();
            d.ticksElapsed++;
            if (d.ticksElapsed >= DUEL_MAX_TICKS) {
                Player p1 = Bukkit.getPlayer(d.player1);
                Player p2 = Bukkit.getPlayer(d.player2);
                Component msg = ChatUtil.getPrefix().append(
                        Component.text("Das Duell überschritt die maximale dauer von 30 Minuten und wurde deshalb ")
                                .color(NamedTextColor.GRAY)
                                .append(Component.text("Abgebrochen!").decorate(TextDecoration.BOLD)));
                if (p1 != null) p1.sendMessage(msg);
                if (p2 != null) p2.sendMessage(msg);

                PluginStateManager.getPlayerData(d.player1).hostedDuels =
                        Math.max(0, PluginStateManager.getPlayerData(d.player1).hostedDuels - 1);
                PluginStateManager.getPlayerData(d.player2).hostedDuels =
                        Math.max(0, PluginStateManager.getPlayerData(d.player2).hostedDuels - 1);
                it.remove();
            }
        }
    }
}
