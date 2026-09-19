package com.nachbau.helden.listener;

import com.nachbau.helden.state.PlayerData;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.state.ServerState;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

/**
 * Laeuft jeden Tick (1x/20 = 1s Rhythmus fuer Glow, jeden Tick fuer HUD).
 *
 * Die Herzen-/Partnerkopf-Anzeige ist KEIN Item und KEIN Skalieren der
 * Maxleben - es ist eine Actionbar, gerendert mit einem eigenen statischen
 * Resourcepack-Font (Namespace "minecrafthelden2"). Jeder Spieler bekommt
 * beim Linken ein eigenes Glyph (Unicode Private Use Area, siehe
 * GlyphAllocator) fest im Pack zugewiesen - kein Live-Server noetig, das
 * Pack wird per "/mchelden pack build" einmalig gebaut und extern gehostet.
 */
public final class PluginTask implements Runnable {

    private static final Key HEART_FONT = Key.key("minecrafthelden2", "hearts");
    private static final Key HEAD_FONT = Key.key("minecrafthelden2", "linked");

    private int glowTicks = 0;

    @Override
    public void run() {
        ServerState state = PluginStateManager.getServerState();

        aktualisiereNametagTeam(state);
        tickGlow(state);
        aktualisiereHud();
    }

    private void aktualisiereNametagTeam(ServerState state) {
        ScoreboardManager sm = Bukkit.getScoreboardManager();
        if (sm == null) return;
        Scoreboard board = sm.getMainScoreboard();

        Team team = board.getTeam("mchelden_hide");
        if (team == null) team = board.registerNewTeam("mchelden_hide");

        team.setOption(Team.Option.NAME_TAG_VISIBILITY,
                state.nametagsEnabled ? Team.OptionStatus.ALWAYS : Team.OptionStatus.NEVER);

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!team.hasEntry(p.getName())) team.addEntry(p.getName());
        }
    }

    private void tickGlow(ServerState state) {
        glowTicks++;
        if (glowTicks < state.glowIntervalTicks) return;
        glowTicks = 0;

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(Component.text("<< GLOW IN: " + (state.glowDurationTicks / 20) + "s >>")
                    .color(NamedTextColor.YELLOW)
                    .decorate(TextDecoration.BOLD));
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, state.glowDurationTicks, 0));
        }
    }

    private void aktualisiereHud() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = PluginStateManager.getPlayerData(p.getUniqueId());

            StringBuilder heartSymbols = new StringBuilder();
            for (int i = 0; i < Math.max(0, data.lives); i++) {
                heartSymbols.append('\u685C'); // 桜
            }

            Component ab = Component.text(heartSymbols.toString()).font(HEART_FONT);

            // "Auf dem Linked Heart" = letztes verbleibendes Leben:
            // ab hier wird der Kopf des Partners eingeblendet - jeder Spieler
            // hat sein eigenes Glyph im statischen Pack (GlyphAllocator).
            if (data.lives == 1 && data.linkedPlayer != null) {
                PlayerData partnerData = PluginStateManager.getPlayerData(data.linkedPlayer);
                if (partnerData.headGlyph != null) {
                    String glyph = new String(Character.toChars(partnerData.headGlyph));
                    ab = ab.append(Component.text(" "))
                            .append(Component.text(glyph).font(HEAD_FONT));
                }
            }

            p.sendActionBar(ab);
        }
    }
}
