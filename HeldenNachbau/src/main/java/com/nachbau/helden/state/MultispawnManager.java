package com.nachbau.helden.state;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Verwaltet benannte Multispawn-Punkte ("world;x;y;z;yaw;pitch" als String,
 * wie im Original per String.split(";") geparst).
 */
public final class MultispawnManager {

    private MultispawnManager() {
    }

    public static void addSpawn(String name, Player player) {
        ServerState state = PluginStateManager.getServerState();
        Location loc = player.getLocation();
        String encoded = loc.getWorld().getName() + ";" + loc.getBlockX() + ";"
                + loc.getBlockY() + ";" + loc.getBlockZ() + ";"
                + loc.getYaw() + ";" + loc.getPitch();
        state.multispawns.put(name, encoded);
        PluginStateManager.save();
    }

    public static void deleteSpawn(String name) {
        PluginStateManager.getServerState().multispawns.remove(name);
        PluginStateManager.save();
    }

    public static boolean hasSpawn(String name) {
        return PluginStateManager.getServerState().multispawns.containsKey(name);
    }

    public static void tpToSpawn(String name, Player player) {
        String encoded = PluginStateManager.getServerState().multispawns.get(name);
        if (encoded == null) return;
        String[] parts = encoded.split(";");
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return;
        Location loc = new Location(world,
                Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
        player.teleport(loc);
    }

    /** Weist einen Spieler dauerhaft einem Multispawn zu (fuer Join/Respawn). */
    public static void joinSpawn(String name, Player player) {
        PlayerData data = PluginStateManager.getPlayerData(player.getUniqueId());
        data.assignedMultispawn = name;
        PluginStateManager.save();
        tpToSpawn(name, player);
    }

    public static String randomSpawnName() {
        var keys = PluginStateManager.getServerState().multispawns.keySet();
        if (keys.isEmpty()) return null;
        var list = new java.util.ArrayList<>(keys);
        return list.get(new java.util.Random().nextInt(list.size()));
    }
}
