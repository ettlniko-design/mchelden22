package com.nachbau.helden.listener;

import com.nachbau.helden.HeldenPlugin;
import com.nachbau.helden.state.PlayerData;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.state.ServerState;
import com.nachbau.helden.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Todeslogik 1:1 aus dem Original (DeathEventListener.class):
 * - Tod durch den eigenen Linked-Heart-Partner kostet kein Herz.
 * - Sonstiger PvP-Kampftod kostet ein Herz; bei 0 Herzen -> Spectator-Modus.
 * - Der Linked-Partner verliert bei JEDEM lebensrelevanten Tod ebenfalls ein Herz.
 * - Inventar-Behalt-Rate unterscheidet sich fuer Kampf- vs. Nicht-Kampftod.
 */
public final class DeathEventListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();

        if (HeldenPlugin.instance.duelManager.isInDuel(uuid)) {
            HeldenPlugin.instance.duelManager.endDuel(uuid);
        }

        PlayerData data = PluginStateManager.getPlayerData(uuid);
        Player killer = player.getKiller();
        boolean inCombat = HeldenPlugin.instance.combatManager.isInCombat(uuid);
        boolean isCombatDeath = killer != null || inCombat;

        handleDrops(player, isCombatDeath, event);

        if (killer != null && data.linkedPlayer != null && data.linkedPlayer.equals(killer.getUniqueId())) {
            // Tod durch den eigenen Linked-Heart-Partner: kein Herzverlust.
            Component overrideMsg = Component.text(player.getName()).color(NamedTextColor.RED)
                    .append(Component.text(" wurde von seinem Linked Heart Partner getötet! (Kein Herzverlust)")
                            .color(NamedTextColor.GRAY));
            event.deathMessage(overrideMsg);
            HeldenPlugin.instance.combatManager.removePlayerFromCombat(uuid);
            return;
        }

        HeldenPlugin.instance.combatManager.removePlayerFromCombat(uuid);

        if (killer == null) {
            // Natuerlicher Tod (kein Spielerkill) - kostet kein Herz.
            return;
        }

        data.lives--;
        PluginStateManager.save();

        boolean finalDeath = data.lives <= 0;

        Component vanillaMsg = ((TextComponent) Component.text(player.getName())).color(NamedTextColor.RED)
                .append(Component.text(" wurde im Kampf von ").color(NamedTextColor.GRAY))
                .append(Component.text(killer.getName()).color(NamedTextColor.GREEN))
                .append(finalDeath
                        ? Component.text(" getötet und ist damit endgültig ausgeschieden!").color(NamedTextColor.GRAY)
                        : Component.text(" getötet.").color(NamedTextColor.GRAY));
        event.deathMessage(vanillaMsg);

        // Linked-Partner verliert ebenfalls ein Herz.
        if (data.linkedPlayer != null) {
            PlayerData linkedData = PluginStateManager.getPlayerData(data.linkedPlayer);
            linkedData.lives--;
            PluginStateManager.save();
            Player linkedOnline = Bukkit.getPlayer(data.linkedPlayer);
            if (linkedOnline != null) {
                linkedOnline.sendMessage(ChatUtil.getPrefix().append(
                        Component.text("Dein verlinkter Spieler ist gestorben! Du hast ein Herz verloren.")
                                .color(NamedTextColor.RED)));
            }
            if (linkedData.lives <= 0) {
                Bukkit.getScheduler().runTask(HeldenPlugin.instance, () ->
                        scheide_aus(data.linkedPlayer, linkedOnline));
            }
        }

        if (finalDeath) {
            Bukkit.getScheduler().runTask(HeldenPlugin.instance, () -> scheide_aus(uuid, player));
        }
    }

    /** Versetzt einen Spieler, der auf 0 Herzen gefallen ist, in den Spectator-Modus. */
    private void scheide_aus(UUID uuid, Player onlinePlayer) {
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage(ChatUtil.getPrefix()
                    .append(Component.text("Du hast alle Herzen verloren und bist damit aus dem Projekt ")
                            .color(NamedTextColor.WHITE))
                    .append(Component.text("ausgeschieden!").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)));
            onlinePlayer.setGameMode(org.bukkit.GameMode.SPECTATOR);
        }
    }

    private void handleDrops(Player player, boolean isCombatDeath, PlayerDeathEvent event) {
        ServerState state = PluginStateManager.getServerState();
        int keepPercent = isCombatDeath ? state.deathInventoryCombatKeep : state.deathInventoryNonCombatKeep;
        keepPercent = Math.max(0, Math.min(100, keepPercent));

        if (keepPercent >= 100) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            return;
        }
        if (keepPercent <= 0) {
            event.setKeepInventory(false);
            return;
        }

        // Teilweise behalten: zufaellige Auswahl von Items bleibt im Inventar,
        // der Rest droppt normal.
        event.setKeepInventory(false);
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        Collections.shuffle(drops);
        int totalItems = drops.size();
        int itemsToDrop = totalItems - (int) Math.round(totalItems * (keepPercent / 100.0));

        List<ItemStack> behalten = new ArrayList<>(drops.subList(0, Math.max(0, totalItems - itemsToDrop)));
        event.getDrops().clear();
        event.getDrops().addAll(drops.subList(Math.max(0, totalItems - itemsToDrop), totalItems));

        Bukkit.getScheduler().runTask(HeldenPlugin.instance, () -> {
            for (ItemStack stack : behalten) {
                player.getInventory().addItem(stack);
            }
        });
    }
}
