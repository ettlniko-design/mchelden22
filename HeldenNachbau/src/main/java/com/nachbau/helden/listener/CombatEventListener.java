package com.nachbau.helden.listener;

import com.nachbau.helden.HeldenPlugin;
import com.nachbau.helden.duel.DuelManager;
import com.nachbau.helden.pack.StaticPackBuilder;
import com.nachbau.helden.state.PlayerData;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.state.ServerState;
import com.nachbau.helden.state.MultispawnManager;
import com.nachbau.helden.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatEventListener implements Listener {

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player target)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            if (shooter instanceof Player p) attacker = p;
        }
        if (attacker == null || attacker.equals(target)) return;

        DuelManager duel = HeldenPlugin.instance.duelManager;
        boolean targetInDuel = duel.isInDuel(target.getUniqueId());
        boolean attackerInDuel = duel.isInDuel(attacker.getUniqueId());

        if (targetInDuel || attackerInDuel) {
            // Waehrend eines Duells duerfen nur die Duellanten sich treffen.
            if (!duel.isDuelOpponent(attacker.getUniqueId(), target.getUniqueId())) {
                attacker.sendActionBar(Component.text("Du kannst in dieses Duell nicht eingreifen!")
                        .color(NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }
        }

        HeldenPlugin.instance.combatManager.onPlayerHit(attacker, target);
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        HeldenPlugin.instance.getLogger().info(
                "ResourcePack status for " + event.getPlayer().getName() + ": " + event.getStatus());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        HeldenPlugin.instance.combatManager.onLogout(player);
        HeldenPlugin.instance.duelManager.endDuel(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        ServerState state = PluginStateManager.getServerState();
        PlayerData data = PluginStateManager.getPlayerData(player.getUniqueId());
        data.lastKnownName = player.getName();
        PluginStateManager.save();

        if (state.randomMultispawnMode && data.assignedMultispawn == null && !state.multispawns.isEmpty()) {
            String name = MultispawnManager.randomSpawnName();
            if (name != null) MultispawnManager.joinSpawn(name, player);
        }

        StaticPackBuilder.sendConfiguredPack(player);
    }
}
