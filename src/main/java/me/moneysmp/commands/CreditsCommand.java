package me.moneysmp.commands;

import me.moneysmp.managers.CreditManager;
import me.moneysmp.util.PlayerLookup;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * /credits give|take|set <player> <amount> [draft|game]   and   /credits balance [player]
 * Works for online, offline and never-joined players (looked up by name, Mojang lookup off the main thread).
 */
public class CreditsCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final CreditManager creditManager;

    public CreditsCommand(JavaPlugin plugin, CreditManager creditManager) {
        this.plugin = plugin;
        this.creditManager = creditManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("moneysmp.admin")) {
            sender.sendMessage("§cOnly server admins can manage player credits!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("balance")) {
            if (args.length < 2) {
                if (sender instanceof Player p) {
                    showBalance(sender, p.getUniqueId(), p.getName());
                } else {
                    sender.sendMessage("§cUsage: /credits balance <player>");
                }
                return true;
            }
            PlayerLookup.resolve(plugin, args[1], result -> {
                if (!result.found()) { sender.sendMessage(PlayerLookup.failMessage(result, args[1])); return; }
                showBalance(sender, result.uuid(), result.name() != null ? result.name() : args[1]);
            });
            return true;
        }

        if (args.length < 3 || !(sub.equals("give") || sub.equals("add") || sub.equals("take")
                || sub.equals("remove") || sub.equals("set"))) {
            sendHelp(sender);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount < 0) {
                sender.sendMessage("§cAmount must be positive!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount!");
            return true;
        }

        String type = (args.length >= 4) ? args[3].toLowerCase() : "game";

        PlayerLookup.resolve(plugin, args[1], result -> {
            if (!result.found()) { sender.sendMessage(PlayerLookup.failMessage(result, args[1])); return; }
            change(sender, sub, type, amount, result.uuid(), result.name() != null ? result.name() : args[1]);
        });
        return true;
    }

    private void showBalance(CommandSender sender, UUID id, String name) {
        int draft = creditManager.getDraftCredits(id);
        int game = creditManager.getGameCredits(id);
        sender.sendMessage("§6[Credits] §e" + name + " §8| §fDraft: §a" + draft + " §8| §fGame: §b" + game);
    }

    private void change(CommandSender sender, String sub, String type, int amount, UUID id, String name) {
        switch (sub) {
            case "give", "add" -> {
                if (type.equals("draft")) {
                    creditManager.setDraftCredits(id, creditManager.getDraftCredits(id) + amount);
                    sender.sendMessage("§aGave " + amount + " Draft Credits to " + name + "!");
                } else {
                    creditManager.addGameCredits(id, amount);
                    sender.sendMessage("§aGave " + amount + " Game Credits to " + name + "!");
                }
            }
            case "take", "remove" -> {
                if (type.equals("draft")) {
                    creditManager.removeDraftCredits(id, amount);
                    sender.sendMessage("§cTook " + amount + " Draft Credits from " + name + "!");
                } else {
                    creditManager.removeGameCredits(id, amount);
                    sender.sendMessage("§cTook " + amount + " Game Credits from " + name + "!");
                }
            }
            case "set" -> {
                if (type.equals("draft")) {
                    creditManager.setDraftCredits(id, amount);
                    sender.sendMessage("§aSet " + name + "'s Draft Credits to " + amount + "!");
                } else {
                    creditManager.setGameCredits(id, amount);
                    sender.sendMessage("§aSet " + name + "'s Game Credits to " + amount + "!");
                }
            }
            default -> { sendHelp(sender); return; }
        }
        if (plugin instanceof me.moneysmp.MoneySMP smp) {
            if (smp.getDataManager() != null) smp.getDataManager().saveData();
            if (smp.getWebServerManager() != null) smp.getWebServerManager().requestPush();
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l[Credits Management]");
        sender.sendMessage("§e/credits give <player> <amount> [draft|game]");
        sender.sendMessage("§e/credits take <player> <amount> [draft|game]");
        sender.sendMessage("§e/credits set <player> <amount> [draft|game]");
        sender.sendMessage("§e/credits balance [player]");
    }
}
