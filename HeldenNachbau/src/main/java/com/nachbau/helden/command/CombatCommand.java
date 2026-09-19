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

public final class CombatCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 1) {
            sender.sendMessage(Component.text("Benutzung: /combat <chat|bossbar>").color(NamedTextColor.RED));
            return true;
        }

        PlayerData data = PluginStateManager.getPlayerData(player.getUniqueId());

        if (args[0].equalsIgnoreCase("chat")) {
            data.combatChatMode = true;
            PluginStateManager.save();
            player.sendMessage(ChatUtil.getPrefix().append(
                    Component.text("Combat Benachrichtigungen werden nun im Chat angezeigt!").color(NamedTextColor.GREEN)));
        } else if (args[0].equalsIgnoreCase("bossbar")) {
            data.combatChatMode = false;
            PluginStateManager.save();
            player.sendMessage(ChatUtil.getPrefix().append(
                    Component.text("Combat Benachrichtigungen werden nun als Bossbar angezeigt!").color(NamedTextColor.GREEN)));
        } else {
            sender.sendMessage(Component.text("Benutzung: /combat <chat|bossbar>").color(NamedTextColor.RED));
        }
        return true;
    }
}
