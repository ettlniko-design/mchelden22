package com.nachbau.helden.listener;

import com.nachbau.helden.HeldenPlugin;
import com.nachbau.helden.state.PluginStateManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Beschleunigt den Laubzerfall nach dem Faellen eines Baums, wenn
 * fastLeafDecay aktiv ist. Prueft nicht-persistentes Laub in der Naehe und
 * laesst es mit kurzer Verzoegerung natuerlich zerfallen.
 */
public final class LeafDecayEventListener implements Listener {

    private final Random random = new Random();

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!PluginStateManager.getServerState().fastLeafDecay) return;

        Block block = event.getBlock();
        String name = block.getType().name();
        if (!name.endsWith("_LOG") && !name.endsWith("_WOOD")) return;

        checkLeaves(block);
    }

    @EventHandler
    public void onLeavesDecay(LeavesDecayEvent event) {
        // Vanilla-Verhalten bleibt unangetastet, wird hier nur zur
        // Vollstaendigkeit registriert (falls spaeter eigene Regeln noetig sind).
    }

    private void checkLeaves(Block startBlock) {
        List<Block> toCheck = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 4; y++) {
                for (int z = -4; z <= 4; z++) {
                    Block neighbor = startBlock.getRelative(x, y, z);
                    BlockData data = neighbor.getBlockData();
                    if (data instanceof Leaves leaves && !leaves.isPersistent()
                            && neighbor.getLocation().distance(startBlock.getLocation()) <= 4) {
                        toCheck.add(neighbor);
                    }
                }
            }
        }

        for (Block leafBlock : toCheck) {
            int delay = 10 + random.nextInt(30);
            Bukkit.getScheduler().runTaskLater(HeldenPlugin.instance, () -> {
                BlockData currentLeaves = leafBlock.getBlockData();
                if (currentLeaves instanceof Leaves leaves && !leaves.isPersistent()) {
                    leafBlock.breakNaturally();
                }
            }, delay);
        }
    }
}
