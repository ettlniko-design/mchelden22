package com.nachbau.helden.command;

import com.nachbau.helden.HeldenPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class DuelCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl ist nur für Spieler!");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Component.text("Benutzung: /duel <Spieler> | accept | deny").color(NamedTextColor.RED));
            return true;
        }

        if (args[0].equalsIgnoreCase("accept")) {
            HeldenPlugin.instance.duelManager.acceptDuel(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("deny")) {
            HeldenPlugin.instance.duelManager.denyDuel(player);
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Spieler nicht gefunden!").color(NamedTextColor.RED));
            return true;
        }

        HeldenPlugin.instance.duelManager.requestDuel(player, target);
        return true;
    }
}
