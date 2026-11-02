package me.moneysmp.commands;

import me.moneysmp.managers.EventManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BingoCommand implements CommandExecutor {
    private final EventManager eventManager;

    public BingoCommand(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("start")) {
            if (!sender.hasPermission("moneysmp.admin")) {
                sender.sendMessage("§cAdmin only!");
                return true;
            }
            eventManager.startBingo();
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("stop")) {
            if (!sender.hasPermission("moneysmp.admin")) {
                sender.sendMessage("§cAdmin only!");
                return true;
            }
            eventManager.stopBingo();
            return true;
        }

        if (sender instanceof Player player) {
            // Locked until the event starts so nobody can study the board early (admins can still preview).
            if (!eventManager.isBingoActive() && !player.hasPermission("moneysmp.admin")) {
                player.sendMessage("§c[BINGO] Bingo hasn't started yet! The board opens for everyone at the same time when the event begins.");
                return true;
            }
            eventManager.openBingoGUI(player);
        } else {
            sender.sendMessage("§cOnly players can open the Bingo GUI!");
        }
        return true;
    }
}
