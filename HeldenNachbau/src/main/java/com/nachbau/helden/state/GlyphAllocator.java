package com.nachbau.helden.state;

import java.util.UUID;

/** Vergibt jedem Spieler einen eigenen Unicode-Codepoint (Private Use Area)
 *  fuer sein Kopf-Glyph im statischen HUD-Font. */
public final class GlyphAllocator {

    private GlyphAllocator() {
    }

    /** Gibt den zugewiesenen Codepoint zurueck, vergibt bei Bedarf einen neuen. */
    public static int glyphFor(UUID uuid) {
        PlayerData data = PluginStateManager.getPlayerData(uuid);
        if (data.headGlyph == null) {
            ServerState state = PluginStateManager.getServerState();
            data.headGlyph = state.nextGlyphCodepoint++;
            PluginStateManager.save();
        }
        return data.headGlyph;
    }
}
