package com.nachbau.helden.combat;

import com.nachbau.helden.state.PlayerData;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Verwaltet aktive Kampf-Begegnungen. Struktur (Felder/Methoden) 1:1 aus dem
 * Original uebernommen (Konstantenpool-Analyse von CombatManager.class).
 *
 * COMBAT_ADD_TICKS war im Original ein hartcodierter int-Konstantenwert, der
 * sich ueber die Stringtabelle allein nicht auslesen liess - 300 Ticks (15s)
 * ist eine plausible Annahme, kein belegter Originalwert.
 */
public final class CombatManager {

    private static final int COMBAT_ADD_TICKS = 300;

    private final List<Encounter> activeEncounters = new ArrayList<>();

    public static final class Encounter {
        public final Set<UUID> participants = new HashSet<>();
        public final Set<UUID> notifiedParticipants = new HashSet<>();
        public final Map<UUID, Integer> enderpearlsUsed = new HashMap<>();
        public final Map<UUID, Integer> cobwebsUsed = new HashMap<>();
        public int ticksRemaining;
        public int highestTicks;
        public int lastNotifiedSeconds = -1;
        public BossBar bossBar;

        public Encounter() {
            this.bossBar = Bukkit.createBossBar("Im Kampf!", BarColor.RED, BarStyle.SOLID);
        }
    }

    public void onPlayerHit(Player player, Player opponent) {
        Encounter e = findEncounter(player.getUniqueId(), opponent.getUniqueId());
        boolean isIncrease = e != null;
        if (e == null) {
            e = new Encounter();
            e.participants.add(player.getUniqueId());
            e.participants.add(opponent.getUniqueId());
            activeEncounters.add(e);
        }

        e.ticksRemaining = COMBAT_ADD_TICKS;
        e.highestTicks = Math.max(e.highestTicks, e.ticksRemaining);

        for (UUID uuid : e.participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            e.bossBar.addPlayer(p);

            PlayerData pData = PluginStateManager.getPlayerData(uuid);
            if (!pData.combatChatMode) continue;

            if (isIncrease) {
                p.sendMessage(ChatUtil.getPrefix().append(
                        Component.text("Combat Zeit erhöht auf " + (COMBAT_ADD_TICKS / 20) + "s!")
                                .color(NamedTextColor.GOLD)));
            }
        }

        if (!isIncrease) {
            player.sendMessage(ChatUtil.getPrefix().append(
                    ((TextComponent) Component.text("Du hast ")).color(NamedTextColor.GOLD)
                            .append(Component.text(opponent.getName()).color(NamedTextColor.RED))
                            .append(Component.text(" angegriffen!").color(NamedTextColor.GOLD))));
            opponent.sendMessage(ChatUtil.getPrefix().append(
                    ((TextComponent) Component.text("Du wurdest von ")).color(NamedTextColor.GOLD)
                            .append(Component.text(player.getName()).color(NamedTextColor.RED))
                            .append(Component.text(" angegriffen!").color(NamedTextColor.GOLD))));
        }

        // Trident-Cooldown zuruecksetzen, damit Riptide nicht als Flucht-Exploit
        // aus dem Kampf genutzt werden kann.
        player.setCooldown(Material.TRIDENT, COMBAT_ADD_TICKS);
        opponent.setCooldown(Material.TRIDENT, COMBAT_ADD_TICKS);

        updateBossBar(e);
    }

    public void tick() {
        Iterator<Encounter> it = activeEncounters.iterator();
        while (it.hasNext()) {
            Encounter e = it.next();
            e.ticksRemaining--;

            if (e.ticksRemaining <= 0) {
                e.bossBar.removePlayer(null);
                for (UUID uuid : e.participants) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) e.bossBar.removePlayer(p);
                }
                it.remove();
                continue;
            }

            int seconds = (int) Math.ceil(e.ticksRemaining / 20.0);
            if (seconds != e.lastNotifiedSeconds) {
                e.lastNotifiedSeconds = seconds;
                updateBossBar(e);

                for (UUID uuid : e.participants) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    PlayerData pData = PluginStateManager.getPlayerData(uuid);
                    if (pData.combatChatMode && seconds % 5 == 0) {
                        p.sendMessage(ChatUtil.getPrefix().append(
                                Component.text("Noch " + seconds + " Sekunden im Kampf!")
                                        .color(NamedTextColor.GOLD)));
                    }
                }
            }
        }
    }

    private void updateBossBar(Encounter e) {
        int seconds = (int) Math.ceil(e.ticksRemaining / 20.0);
        e.bossBar.setTitle("§cIm Kampf! §7(" + seconds + "s übrig)");
        e.bossBar.setProgress(Math.max(0.0, Math.min(1.0, (double) e.ticksRemaining / e.highestTicks)));
    }

    public boolean isInCombat(UUID uuid) {
        return activeEncounters.stream().anyMatch(e -> e.participants.contains(uuid));
    }

    public Encounter getEncounter(UUID uuid) {
        return activeEncounters.stream().filter(e -> e.participants.contains(uuid)).findFirst().orElse(null);
    }

    public void removePlayerFromCombat(UUID uuid) {
        for (Encounter e : activeEncounters) {
            e.participants.remove(uuid);
        }
        activeEncounters.removeIf(e -> e.participants.isEmpty());
    }

    /** Wenn ein Spieler mitten im Kampf disconnected, gilt das als Kampftod. */
    public void onLogout(Player player) {
        UUID uuid = player.getUniqueId();
        Encounter e = getEncounter(uuid);
        if (e == null) return;

        UUID opponentUuid = e.participants.stream()
                .filter(u -> !u.equals(uuid))
                .findFirst().orElse(null);

        String opponentName = "einem Gegner";
        if (opponentUuid != null) {
            OfflinePlayer opp = Bukkit.getOfflinePlayer(opponentUuid);
            if (opp.getName() != null) opponentName = opp.getName();
        }

        Bukkit.broadcast(ChatUtil.getPrefix().append(
                ((TextComponent) Component.text(player.getName())).color(NamedTextColor.GREEN)
                        .append(Component.text(" hat sich im Kampf gegen ").color(NamedTextColor.GRAY))
                        .append(Component.text(opponentName).color(NamedTextColor.GREEN))
                        .append(Component.text(" ausgeloggt und somit ein Herz verloren.").color(NamedTextColor.GRAY))));

        removePlayerFromCombat(uuid);
        // Health auf 0 zwingt den normalen Death-Event-Ablauf (Herzverlust,
        // Inventarregel, Bann-Check) - genau wie im Original.
        player.setHealth(0.0);
    }

    private Encounter findEncounter(UUID a, UUID b) {
        return activeEncounters.stream()
                .filter(e -> e.participants.contains(a) || e.participants.contains(b))
                .findFirst().orElse(null);
    }
}
