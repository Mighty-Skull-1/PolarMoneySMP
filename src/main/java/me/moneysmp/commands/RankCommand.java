package me.moneysmp.commands;

import me.moneysmp.managers.RankManager;
import me.moneysmp.models.Rank;
import me.moneysmp.util.PlayerLookup;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;

/**
 * /rank set <player> owner|admin|sub|none   and   /rank list
 * Only ops and the console can use it - not admins, not owners.
 */
public class RankCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final RankManager rankManager;

    public RankCommand(JavaPlugin plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    public static boolean canUse(CommandSender sender) {
        return sender instanceof ConsoleCommandSender || sender.isOp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!canUse(sender)) {
            sender.sendMessage("§cOnly server operators (or the console) can set ranks.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            Map<UUID, Rank> all = rankManager.getAllRanks();
            if (all.isEmpty()) { sender.sendMessage("§7Nobody has a rank yet."); return true; }
            sender.sendMessage("§6§l[Ranks]");
            for (Rank r : new Rank[]{Rank.OWNER, Rank.ADMIN, Rank.SUB}) {
                for (Map.Entry<UUID, Rank> e : all.entrySet()) {
                    if (e.getValue() == r) sender.sendMessage(" " + r.getPrefix() + " §f" + me.moneysmp.util.PlayerNames.getOrShort(e.getKey()));
                }
            }
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("set")) {
            sender.sendMessage("§cUsage: /rank set <player> owner|admin|sub|none  §7or  §c/rank list");
            return true;
        }

        Rank rank;
        try {
            rank = Rank.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cRank must be owner, admin, sub or none.");
            return true;
        }

        // Works for offline players and players who have never joined.
        PlayerLookup.resolve(plugin, args[1], result -> {
            if (!result.found()) { sender.sendMessage(PlayerLookup.failMessage(result, args[1])); return; }
            String name = result.name() != null ? result.name() : args[1];
            rankManager.setRank(result.uuid(), rank);

            if (plugin instanceof me.moneysmp.MoneySMP smp) {
                if (smp.getDataManager() != null) smp.getDataManager().saveData();
                if (smp.getDisplayManager() != null) smp.getDisplayManager().updateAllDisplays();
            }

            if (rank == Rank.NONE) sender.sendMessage("§aRemoved " + name + "'s rank.");
            else sender.sendMessage("§a" + name + " is now " + rank.getPrefix() + "§a.");

            Player online = Bukkit.getPlayer(result.uuid());
            if (online != null && online != sender) {
                online.sendMessage(rank == Rank.NONE ? "§eYour rank was removed."
                        : "§aYou are now " + rank.getPrefix() + "§a on Polar Money SMP.");
            }
        });
        return true;
    }
}
