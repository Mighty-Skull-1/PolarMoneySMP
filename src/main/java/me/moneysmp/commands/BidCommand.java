package me.moneysmp.commands;

import me.moneysmp.managers.CreditManager;
import me.moneysmp.managers.DraftManager;
import me.moneysmp.managers.TeamManager;
import me.moneysmp.managers.TierManager;
import me.moneysmp.models.PlayerTier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import me.moneysmp.util.PlayerLookup;

import java.util.UUID;

/**
 * /bid nominate <player>  - admin starts an auction (captains get bid items, admin gets SOLD/CANCEL items)
 * /bid sold               - admin sells to the top bidder (same as the SOLD item)
 * /bid cancel             - admin cancels
 * /bid                    - captain backup: raises the top bid by the normal step
 */
public class BidCommand implements CommandExecutor {
    private final TeamManager teamManager;
    private final CreditManager creditManager;
    private final TierManager tierManager;
    private final DraftManager draftManager;
    private final JavaPlugin plugin;

    public BidCommand(JavaPlugin plugin, TeamManager teamManager, CreditManager creditManager, TierManager tierManager, DraftManager draftManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.creditManager = creditManager;
        this.tierManager = tierManager;
        this.draftManager = draftManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        if (sub.equals("cancel") || sub.equals("sold") || sub.equals("nominate")) {
            if (!sender.hasPermission("moneysmp.admin")) {
                sender.sendMessage("§cOnly server admins can do that!");
                return true;
            }
        }
        switch (sub) {
            case "cancel" -> {
                if (!draftManager.isDraftActive()) sender.sendMessage("§cNo auction is running.");
                else draftManager.cancelDraft();
                return true;
            }
            case "sold" -> {
                if (!draftManager.isDraftActive()) sender.sendMessage("§cNo auction is running.");
                else draftManager.sell();
                return true;
            }
            case "nominate" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /bid nominate <player>"); return true; }
                if (draftManager.isDraftActive()) {
                    sender.sendMessage("§cAn auction is already running! Sell or cancel it first.");
                    return true;
                }
                // Works for online, offline and never-joined players (Mojang lookup runs off the main thread).
                PlayerLookup.resolve(plugin, args[1], result -> {
                    if (!result.found()) { sender.sendMessage(PlayerLookup.failMessage(result, args[1])); return; }
                    nominate(sender, result.uuid(), result.name() != null ? result.name() : args[1]);
                });
                return true;
            }
            default -> {
                if (!(sender instanceof Player captain)) { sender.sendMessage("Only players can bid."); return true; }
                String err = draftManager.tryBid(captain, draftManager.getNextBid());
                if (err != null) captain.sendMessage("§c[Draft] " + err);
                return true;
            }
        }
    }

    private void nominate(CommandSender sender, UUID targetId, String targetName) {
        if (teamManager.getTeamByPlayer(targetId) != null) {
            sender.sendMessage("§c" + targetName + " is already on a team!");
            return;
        }
        PlayerTier tier = tierManager.getTier(targetId);
        if (tier == PlayerTier.NONE || tier == PlayerTier.CAPTAIN) {
            sender.sendMessage("§c" + targetName + " isn't in a draftable tier (A, B, C, D)! Use /tier set " + targetName + " <tier> first.");
            return;
        }
        Player admin = sender instanceof Player p && p.isOnline() ? p : null;
        if (!draftManager.startNomination(targetId, targetName, admin)) {
            sender.sendMessage("§cAn auction is already running! Sell or cancel it first.");
            return;
        }
        if (Bukkit.getPlayer(targetId) == null) {
            sender.sendMessage("§7" + targetName + " isn't online" + (Bukkit.getOfflinePlayer(targetId).hasPlayedBefore() ? "" : " and has never joined")
                    + " - that's fine, they'll be on their team when they log in.");
        }
        if (admin != null) {
            sender.sendMessage("§7You have the §aSOLD §7and §cCANCEL §7items in your hotbar (or use /bid sold).");
        }
    }
}
