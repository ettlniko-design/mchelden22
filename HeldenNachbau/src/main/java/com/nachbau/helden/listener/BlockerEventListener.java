package com.nachbau.helden.listener;

import com.nachbau.helden.HeldenPlugin;
import com.nachbau.helden.combat.CombatManager;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.state.ServerState;
import com.nachbau.helden.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Alle "Blocker"-Schalter aus dem Original. Fuer blockNether/blockNetherite/
 * blockPunch/blockTotems konnte die exakte Originallogik nicht aus dem
 * Konstantenpool rekonstruiert werden (keine zugehoerigen String-Konstanten
 * gefunden) - die Umsetzung hier ist eine plausible, aber nicht garantiert
 * identische Naeherung.
 */
public final class BlockerEventListener implements Listener {

    private final NamespacedKey horseKey = new NamespacedKey(HeldenPlugin.instance, "HorseUUID");

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ServerState state = PluginStateManager.getServerState();
        if (!state.blockMending) return;

        ItemStack result = event.getResult();
        if (result == null || !result.hasItemMeta()) return;

        ItemMeta meta = result.getItemMeta();
        if (meta.hasEnchant(Enchantment.MENDING)) {
            meta.removeEnchant(Enchantment.MENDING);
            result.setItemMeta(meta);
            event.setResult(result);
            event.getInventory().setMaximumRepairCost(Integer.MAX_VALUE);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        ServerState state = PluginStateManager.getServerState();
        Player player = event.getPlayer();

        if (state.blockVillagerTrading
                && (event.getRightClicked() instanceof Villager || event.getRightClicked() instanceof WanderingTrader)) {
            event.setCancelled(true);
            return;
        }

        // Ziegenhorn an gezaehmtes Pferd -> bindet es dauerhaft an den Spieler
        PlayerInventory inv = player.getInventory();
        if (inv.getItemInMainHand().getType() == Material.GOAT_HORN
                && event.getRightClicked() instanceof Horse horse
                && horse.isTamed()
                && horse.getOwner() != null
                && horse.getOwner().getUniqueId().equals(player.getUniqueId())) {

            horse.getPersistentDataContainer().set(horseKey, PersistentDataType.STRING, horse.getUniqueId().toString());
            horse.customName(Component.text("Pferd").color(NamedTextColor.GOLD));
            player.sendMessage(ChatUtil.getPrefix()
                    .append(Component.text("Pferd an Horn gebunden!").color(NamedTextColor.GREEN)));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ServerState state = PluginStateManager.getServerState();
        CombatManager combat = HeldenPlugin.instance.combatManager;

        if (event.getItem() != null && event.getItem().getType() == Material.ENDER_PEARL
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            CombatManager.Encounter enc = combat.getEncounter(player.getUniqueId());
            if (enc != null) {
                UUID uuid = player.getUniqueId();
                int used = enc.enderpearlsUsed.getOrDefault(uuid, 0);
                if (used >= state.maxEnderpearls) {
                    player.sendMessage(ChatUtil.getPrefix()
                            .append(Component.text("Enderperlen Limit erreicht! (" + used + "/" + state.maxEnderpearls + ")")
                                    .color(NamedTextColor.RED)));
                    event.setCancelled(true);
                    player.setCooldown(Material.ENDER_PEARL, 20);
                    return;
                }
                enc.enderpearlsUsed.put(uuid, used + 1);
                player.sendMessage(ChatUtil.getPrefix()
                        .append(Component.text("Enderperle geworfen! (" + (used + 1) + "/" + state.maxEnderpearls + ")")
                                .color(NamedTextColor.GRAY)));
            }
        }

        if (event.getItem() != null && event.getItem().getType() == Material.COBWEB
                && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            CombatManager.Encounter enc = combat.getEncounter(player.getUniqueId());
            if (enc != null) {
                UUID uuid = player.getUniqueId();
                int used = enc.cobwebsUsed.getOrDefault(uuid, 0);
                if (used >= state.maxCobwebs) {
                    player.sendMessage(ChatUtil.getPrefix()
                            .append(Component.text("Spinnweben Limit erreicht! (" + used + "/" + state.maxCobwebs + ")")
                                    .color(NamedTextColor.RED)));
                    event.setCancelled(true);
                    return;
                }
                enc.cobwebsUsed.put(uuid, used + 1);
                player.sendMessage(ChatUtil.getPrefix()
                        .append(Component.text("Spinnwebe platziert! (" + (used + 1) + "/" + state.maxCobwebs + ")")
                                .color(NamedTextColor.GRAY)));
            }
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ServerState state = PluginStateManager.getServerState();
        if (state.blockOPGap && event.getItem().getType() == Material.ENCHANTED_GOLDEN_APPLE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        ServerState state = PluginStateManager.getServerState();
        if (state.blockFireworkCrossbow
                && event.getBow() != null && event.getBow().getType() == Material.CROSSBOW
                && event.getConsumable() != null && event.getConsumable().getType() == Material.FIREWORK_ROCKET) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent event) {
        ServerState state = PluginStateManager.getServerState();
        if (state.blockNether && event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatUtil.getPrefix()
                    .append(Component.text("Der Nether ist auf diesem Server deaktiviert.").color(NamedTextColor.RED)));
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        ServerState state = PluginStateManager.getServerState();
        if (state.blockPunch && event.getEnchantsToAdd().containsKey(Enchantment.PUNCH)) {
            event.getEnchantsToAdd().remove(Enchantment.PUNCH);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ServerState state = PluginStateManager.getServerState();
        ItemStack result = event.getResult();
        if (state.blockNetherite && result != null && result.getType().name().contains("NETHERITE")) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onResurrect(EntityResurrectEvent event) {
        ServerState state = PluginStateManager.getServerState();
        if (state.blockTotems && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }
}
