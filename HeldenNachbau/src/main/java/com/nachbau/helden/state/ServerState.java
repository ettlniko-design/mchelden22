package com.nachbau.helden.state;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-weiter Zustand. Feldnamen 1:1 aus dem Original (ServerState.class)
 * uebernommen, damit /mchelden-Unterbefehle exakt dieselben Schalter treffen.
 */
public class ServerState {

    public Map<UUID, PlayerData> players = new HashMap<>();
    public Map<String, String> multispawns = new HashMap<>();

    public boolean randomMultispawnMode = false;

    public int glowDurationTicks = 100;   // 5s
    public int glowIntervalTicks = 12000; // 10min

    public int maxEnderpearls = 3;
    public int maxCobwebs = 3;

    // Prozentsatz (0-100) des Inventars, der beim Tod behalten wird
    public int deathInventoryCombatKeep = 0;
    public int deathInventoryNonCombatKeep = 100;

    public boolean blockFireworkCrossbow = true;
    public boolean blockMending = true;
    public boolean blockNether = false;
    public boolean blockNetherite = false;
    public boolean blockOPGap = true;
    public boolean blockPunch = false;
    public boolean blockTotems = true;
    public boolean blockVillagerTrading = false;

    public boolean fastLeafDecay = false;
    public boolean nametagsEnabled = true;

    // ---- Statisches HUD-Resourcepack (Option 2: kein eigener Server) ----
    // Fest hinterlegte URL + SHA1 des extern gehosteten Packs (z.B. mc-packs.net).
    // Wird per /mchelden pack seturl <url> <sha1> gesetzt.
    public String packUrl;
    public String packSha1;
    // Naechster freier Private-Use-Area-Codepoint fuer neue Spieler-Glyphen.
    public int nextGlyphCodepoint = 0xE000;
}
