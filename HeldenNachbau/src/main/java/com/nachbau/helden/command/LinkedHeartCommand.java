package com.nachbau.helden.command;

import com.nachbau.helden.state.PlayerData;
import com.nachbau.helden.state.PluginStateManager;
import com.nachbau.helden.util.ChatUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Zeigt den Namen des Linked-Heart-Partners - aber nur, wenn der Spieler
 * selbst schon auf seinem letzten Herz ist (lives == 1). Das entspricht dem
 * Original: der Partner wird erst sichtbar, wenn es wirklich zaehlt.
 */
public final class LinkedHeartCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        PlayerData data = PluginStateManager.getPlayerData(player.getUniqueId());

        if (data.lives > 1) {
            player.sendMessage(ChatUtil.getPrefix().append(
                    Component.text("Du kannst dein Linked Heart Partner erst sehen wenn du auf dem Linked Heart bist!")
                            .color(NamedTextColor.RED)));
            return true;
        }

        if (data.linkedPlayer == null) {
            player.sendMessage(ChatUtil.getPrefix().append(
                    Component.text("Du hast keinen Linked Heart Partner!").color(NamedTextColor.RED)));
            return true;
        }

        String name = data.linkedPlayerName != null ? data.linkedPlayerName : "Unbekannt";
        player.sendMessage(ChatUtil.getPrefix().append(
                Component.text("Dein Linked Heart Partner ist ").color(NamedTextColor.GRAY)
                        .append(Component.text(name).color(NamedTextColor.GREEN))
                        .append(Component.text("!").color(NamedTextColor.GRAY))));
        return true;
    }
}
