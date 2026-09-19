package com.nachbau.helden.state;

import java.util.UUID;

/**
 * Datensatz eines Spielers. Feldnamen 1:1 aus dem Original-Plugin
 * uebernommen (Konstantenpool-Analyse der PlayerData.class).
 */
public class PlayerData {

    public int lives = 3;
    public UUID linkedPlayer;
    public String linkedPlayerName;
    public String lastKnownName;
    public int hostedDuels = 0;
    public String assignedMultispawn;
    public boolean combatChatMode = false; // false = Bossbar, true = Chat
    public int highestCombatTime = 0;

    /** Unicode-Codepoint (Private Use Area) fuer das eigene Kopf-Glyph im
     *  statischen HUD-Font. null = noch nicht ins Pack aufgenommen. */
    public Integer headGlyph;
}
