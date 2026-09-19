package com.nachbau.helden;

import com.nachbau.helden.combat.CombatManager;
import com.nachbau.helden.command.CombatCommand;
import com.nachbau.helden.command.DuelCommand;
import com.nachbau.helden.command.LinkedHeartCommand;
import com.nachbau.helden.command.McHeldenCommand;
import com.nachbau.helden.duel.DuelManager;
import com.nachbau.helden.listener.BlockerEventListener;
import com.nachbau.helden.listener.CombatEventListener;
import com.nachbau.helden.listener.DeathEventListener;
import com.nachbau.helden.listener.LeafDecayEventListener;
import com.nachbau.helden.listener.PluginTask;
import com.nachbau.helden.state.PluginStateManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HeldenPlugin extends JavaPlugin {

    public static HeldenPlugin instance;

    public final CombatManager combatManager = new CombatManager();
    public final DuelManager duelManager = new DuelManager();

    @Override
    public void onEnable() {
        instance = this;

        PluginStateManager.init();

        getServer().getPluginManager().registerEvents(new CombatEventListener(), this);
        getServer().getPluginManager().registerEvents(new DeathEventListener(), this);
        getServer().getPluginManager().registerEvents(new BlockerEventListener(), this);
        getServer().getPluginManager().registerEvents(new LeafDecayEventListener(), this);

        getServer().getScheduler().runTaskTimer(this, new PluginTask(), 0L, 1L);
        getServer().getScheduler().runTaskTimer(this, combatManager::tick, 0L, 1L);
        getServer().getScheduler().runTaskTimer(this, duelManager::tick, 0L, 1L);

        McHeldenCommand mcHeldenCommand = new McHeldenCommand();
        var mchelden = getCommand("mchelden");
        if (mchelden != null) {
            mchelden.setExecutor(mcHeldenCommand);
            mchelden.setTabCompleter(mcHeldenCommand);
        }
        var duel = getCommand("duel");
        if (duel != null) duel.setExecutor(new DuelCommand());
        var linkedheart = getCommand("linkedheart");
        if (linkedheart != null) linkedheart.setExecutor(new LinkedHeartCommand());
        var combat = getCommand("combat");
        if (combat != null) combat.setExecutor(new CombatCommand());

        getLogger().info("MinecraftHelden Plugin Enabled!");
    }

    @Override
    public void onDisable() {
        PluginStateManager.save();
        getLogger().info("MinecraftHelden Plugin Disabled!");
    }
}
