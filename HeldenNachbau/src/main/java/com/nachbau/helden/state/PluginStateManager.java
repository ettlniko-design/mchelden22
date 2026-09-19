package com.nachbau.helden.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nachbau.helden.HeldenPlugin;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.util.UUID;

/**
 * Speichert/laedt den gesamten Zustand als state.json, genau wie im Original
 * (dort ueber Gson mit PrettyPrinting).
 */
public final class PluginStateManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static File file;
    private static ServerState state;

    private PluginStateManager() {
    }

    public static void init() {
        file = new File(HeldenPlugin.instance.getDataFolder(), "state.json");
        if (!file.exists()) {
            HeldenPlugin.instance.getDataFolder().mkdirs();
            state = new ServerState();
            save();
            return;
        }
        try (FileReader reader = new FileReader(file)) {
            state = GSON.fromJson(reader, ServerState.class);
            if (state == null) state = new ServerState();
        } catch (IOException e) {
            e.printStackTrace();
            state = new ServerState();
        }
    }

    public static ServerState getServerState() {
        return state;
    }

    public static PlayerData getPlayerData(UUID uuid) {
        return state.players.computeIfAbsent(uuid, k -> new PlayerData());
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(state, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
