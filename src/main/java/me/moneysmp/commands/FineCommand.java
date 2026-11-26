package me.moneysmp.commands;

import me.moneysmp.MoneySMP;
import me.moneysmp.managers.CreditManager;
import me.moneysmp.managers.TeamManager;
import me.moneysmp.models.Team;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * /fine <player|team> <amount> <reason...>  - takes credits off a team (fines come out of team money)
 * /fine history [player|team]               - recent fines
 * /fine undo                                - gives back the last fine
 *
 * A player's fine comes out of their own credits first; anything they can't cover comes from their
 * teammates (richest first), so the team total always drops by the full amount. A team fine comes
 * from the richest members first. Every fine is announced and logged to fines.yml.
 */
public class FineCommand implements CommandExecutor {
    private final MoneySMP plugin;
    private final TeamManager teamManager;
    private final CreditManager creditManager;
    private final File logFile;

    public FineCommand(MoneySMP plugin, TeamManager teamManager, CreditManager creditManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.creditManager = creditManager;
        this.logFile = new File(plugin.getDataFolder(), "fines.yml");
        // Check it can be read now, so a broken fines.yml is reported at startup (and never saved over).
        me.moneysmp.util.DataSafety.load(plugin, logFile);
    }

    /** True (and tells the sender) when fines.yml couldn't be read - fines are paused so the log isn't overwritten. */
    private boolean finesFileBroken(CommandSender sender) {
        if (!me.moneysmp.util.DataSafety.isUnreadable("fines.yml")) return false;
        sender.sendMessage("§cFines are paused: fines.yml couldn't be read, so nothing is saved to it (no fines are lost). "
                + "Fix it or restore it from the backups folder, then restart.");
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { help(sender); return true; }
        if (finesFileBroken(sender)) return true;
        if (args[0].equalsIgnoreCase("history")) { history(sender, args.length > 1 ? args[1] : null); return true; }
        if (args[0].equalsIgnoreCase("undo")) { undo(sender); return true; }
        if (args.length < 3) { help(sender); return true; }

        int amount;
        try { amount = Integer.parseInt(args[1]); } catch (NumberFormatException e) {
            sender.sendMessage("§cThe amount must be a number, e.g. /fine Steve 25 stream sniping");
            return true;
        }
        if (amount <= 0) { sender.sendMessage("§cThe amount must be more than 0."); return true; }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        // Target: a team name, or a player on a team (works while they're offline).
        Team team = teamManager.getTeamByName(args[0]);
        UUID playerId = null;
        String targetName;
        if (team != null) {
            targetName = team.getName();
        } else {
            playerId = findMember(args[0]);
            if (playerId == null) { sender.sendMessage("§c" + args[0] + " isn't a team or a player on a team."); return true; }
            team = teamManager.getTeamByPlayer(playerId);
            String n = me.moneysmp.util.PlayerNames.get(playerId);
            targetName = n != null ? n : args[0];
        }

        // Who pays: the fined player first, then teammates richest-first.
        List<UUID> payers = new ArrayList<>(team.getMembers());
        payers.sort((a, b) -> Integer.compare(creditManager.getGameCredits(b), creditManager.getGameCredits(a)));
        if (playerId != null) { payers.remove(playerId); payers.add(0, playerId); }

        int left = amount;
        Map<String, Integer> taken = new LinkedHashMap<>();
        for (UUID id : payers) {
            if (left <= 0) break;
            int has = creditManager.getGameCredits(id);
            int take = Math.min(has, left);
            if (take <= 0) continue;
            creditManager.setGameCredits(id, has - take);
            taken.put(id.toString(), take);
            left -= take;
        }
        int charged = amount - left;

        String who = playerId != null ? team.getColorCode() + targetName + " §7(" + team.getColorCode() + team.getName() + "§7)" : team.getColorCode() + team.getName();
        Bukkit.broadcastMessage("§c§l[FINE] " + who + " §chas been fined §f" + charged + " credits§c. §7Reason: §f" + reason);
        if (left > 0) sender.sendMessage("§7(The team only had " + charged + " credits to take, so the fine was capped.)");
        for (UUID id : team.getMembers()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendTitle("§c§lFINED", "§f-" + charged + " credits §7- " + reason, 5, 60, 15);
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            }
        }

        log(sender.getName(), team.getName(), playerId != null ? targetName : null, charged, reason, taken);
        if (plugin.getDataManager() != null) plugin.getDataManager().saveData();
        if (plugin.getWebServerManager() != null) plugin.getWebServerManager().requestPush();
        return true;
    }

    private UUID findMember(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null && teamManager.getTeamByPlayer(online.getUniqueId()) != null) return online.getUniqueId();
        for (Team t : teamManager.getTeams()) {
            for (UUID m : t.getMembers()) {
                String n = me.moneysmp.util.PlayerNames.get(m);
                if (n != null && n.equalsIgnoreCase(name)) return m;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- log
    /** The fine log, or an empty one if fines.yml became unreadable (it's then marked, and never saved over). */
    private YamlConfiguration readLog() {
        YamlConfiguration y = me.moneysmp.util.DataSafety.load(plugin, logFile);
        return y != null ? y : new YamlConfiguration();
    }

    private void log(String admin, String team, String player, int amount, String reason, Map<String, Integer> taken) {
        YamlConfiguration y = readLog();
        List<Map<?, ?>> list = new ArrayList<>(y.getMapList("fines"));
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
        e.put("by", admin);
        e.put("team", team);
        if (player != null) e.put("player", player);
        e.put("amount", amount);
        e.put("reason", reason);
        e.put("taken", taken);
        list.add(e);
        y.set("fines", list);
        me.moneysmp.util.DataSafety.saveAtomically(plugin, y, logFile);
    }

    private void history(CommandSender sender, String filter) {
        List<Map<?, ?>> list = readLog().getMapList("fines");
        List<Map<?, ?>> shown = new ArrayList<>();
        for (Map<?, ?> e : list) {
            if (filter == null || filter.equalsIgnoreCase(String.valueOf(e.get("team"))) || filter.equalsIgnoreCase(String.valueOf(e.get("player"))))
                shown.add(e);
        }
        if (shown.isEmpty()) { sender.sendMessage("§7[Fines] No fines" + (filter != null ? " for " + filter : "") + " yet."); return; }
        int total = 0;
        for (Map<?, ?> e : shown) total += ((Number) e.get("amount")).intValue();
        sender.sendMessage("§c§l[Fines] §7" + shown.size() + " fine(s)" + (filter != null ? " for §f" + filter : "") + "§7, total §f" + total + " credits§7:");
        for (Map<?, ?> e : shown.subList(Math.max(0, shown.size() - 10), shown.size())) {
            sender.sendMessage("§8" + e.get("time") + " §f" + (e.get("player") != null ? e.get("player") + " §7(" + e.get("team") + ")" : e.get("team"))
                    + " §c-" + e.get("amount") + " §7" + e.get("reason") + " §8by " + e.get("by"));
        }
    }

    private void undo(CommandSender sender) {
        YamlConfiguration y = readLog();
        List<Map<?, ?>> list = new ArrayList<>(y.getMapList("fines"));
        if (list.isEmpty()) { sender.sendMessage("§7[Fines] There's no fine to undo."); return; }
        Map<?, ?> last = list.remove(list.size() - 1);
        // Take it off the log first: if fines.yml can't be written, nothing is refunded (no double refunds later).
        y.set("fines", list);
        if (!me.moneysmp.util.DataSafety.saveAtomically(plugin, y, logFile)) {
            sender.sendMessage("§cCouldn't update fines.yml, so the fine was not refunded.");
            return;
        }
        Object takenObj = last.get("taken");
        if (takenObj instanceof Map<?, ?> taken) {
            for (Map.Entry<?, ?> e : taken.entrySet()) {
                try { creditManager.addGameCredits(UUID.fromString(e.getKey().toString()), ((Number) e.getValue()).intValue()); }
                catch (Exception ignored) {}
            }
        }
        Bukkit.broadcastMessage("§a[FINE REFUNDED] §f" + (last.get("player") != null ? last.get("player") : last.get("team"))
                + " §agot their §f" + last.get("amount") + " credits §aback §7(" + last.get("reason") + ")");
        if (plugin.getDataManager() != null) plugin.getDataManager().saveData();
        if (plugin.getWebServerManager() != null) plugin.getWebServerManager().requestPush();
    }

    private void help(CommandSender s) {
        s.sendMessage("§c§lFines");
        s.sendMessage("§e/fine <player> <amount> <reason> §7- fine a player (comes out of their team's money)");
        s.sendMessage("§e/fine <team> <amount> <reason> §7- fine a whole team");
        s.sendMessage("§e/fine history [player|team] §7- see recent fines");
        s.sendMessage("§e/fine undo §7- refund the last fine (if you made a mistake)");
    }
}
