package com.nachbau.helden.listener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Vanilla-Spectator kann normalerweise durch alle Bloecke fliegen (Noclip).
 * Fuer dieses Plugin ist das nicht erwuenscht: Spieler im Spectator-Modus
 * (z.B. nach dem Ausscheiden) sollen sich wie im Survival-Modus an fester
 * Materie stossen - sie duerfen weiter frei fliegen, aber nicht in/unter
 * Bloecke hinein.
 */
public final class SpectatorListener implements Listener {

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SPECTATOR) return;
        if (event.getTo() == null) return;

        Location to = event.getTo();
        Location from = event.getFrom();

        // Nur reagieren, wenn sich tatsaechlich der Block gewechselt hat -
        // spart unnoetige Pruefungen bei reinem Umsehen (Kopf drehen).
        if (to.getBlockX() == from.getBlockX()
                && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ()) {
            return;
        }

        if (istBlockiert(to)) {
            // Zurueck auf die alte Position setzen (Blickrichtung des neuen
            // Versuchs beibehalten, damit sich die Kamera nicht ruckartig
            // zurueckdreht).
            Location zurueck = from.clone();
            zurueck.setYaw(to.getYaw());
            zurueck.setPitch(to.getPitch());
            event.setTo(zurueck);
        }
    }

    private boolean istBlockiert(Location loc) {
        // Location liegt auf Fusshoehe - Kopf ist ein Block darueber.
        var fuesse = loc.getBlock();
        var kopf = loc.clone().add(0, 1, 0).getBlock();
        return istFest(fuesse.getType()) || istFest(kopf.getType());
    }

    private boolean istFest(org.bukkit.Material material) {
        return material.isSolid();
    }
}
