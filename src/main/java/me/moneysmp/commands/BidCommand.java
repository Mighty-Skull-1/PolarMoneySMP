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

    public BidCommand(TeamManager teamManager, CreditManager creditManager, TierManager tierManager, DraftManager draftManager) {
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
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cThat player isn't online!"); return true; }
                if (teamManager.getTeamByPlayer(target.getUniqueId()) != null) {
                    sender.sendMessage("§c" + target.getName() + " is already on a team!");
                    return true;
                }
                PlayerTier tier = tierManager.getTier(target.getUniqueId());
                if (tier == PlayerTier.NONE || tier == PlayerTier.CAPTAIN) {
                    sender.sendMessage("§cThat player isn't in a draftable tier (A, B, C, D)! Use /tier set first.");
                    return true;
                }
                if (!draftManager.startNomination(target, sender instanceof Player p ? p : null)) {
                    sender.sendMessage("§cAn auction is already running! Sell or cancel it first.");
                } else if (sender instanceof Player) {
                    sender.sendMessage("§7You have the §aSOLD §7and §cCANCEL §7items in your hotbar (or use /bid sold).");
                }
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
}
