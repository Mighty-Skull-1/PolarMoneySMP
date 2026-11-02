package me.moneysmp.commands;

import me.moneysmp.managers.CapturePointManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CapturePointCommand implements CommandExecutor {
    private final CapturePointManager cp;

    public CapturePointCommand(CapturePointManager cp) { this.cp = cp; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "set" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("§cStand where the point should be and run this in-game."); return true; }
                if (args.length < 2) { sender.sendMessage("§cUsage: /cp set <name>   e.g. /cp set alpha"); return true; }
                if (!args[1].matches("[A-Za-z0-9_-]{1,16}")) { sender.sendMessage("§cUse a short name with letters/numbers only."); return true; }
                boolean exists = cp.pointNames().contains(args[1].toLowerCase());
                cp.setPoint(args[1], p.getLocation());
                sender.sendMessage("§a[Capture] Point §f" + args[1] + " §a" + (exists ? "moved" : "saved") + " at your position. §7("
                        + cp.pointNames().size() + " saved)");
            }
            case "remove", "delete" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /cp remove <name>"); return true; }
                sender.sendMessage(cp.removePoint(args[1]) ? "§a[Capture] Removed " + args[1] + "." : "§cNo point called " + args[1] + ".");
            }
            case "list", "status" -> cp.sendList(sender);
            case "startday", "start" -> {
                String err = cp.startDay();
                if (err != null) sender.sendMessage("§c" + err);
            }
            case "open" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /cp open <name>"); return true; }
                String err = cp.openPoint(args[1]);
                if (err != null) sender.sendMessage("§c" + err);
            }
            case "stopday", "stop" -> {
                cp.stopDay(true);
                sender.sendMessage("§a[Capture] All points closed (no rewards for unfinished points).");
            }
            default -> {
                sender.sendMessage("§b§lCapture Points");
                sender.sendMessage("§e/cp set <name> §7- save a point where you stand (set up 8)");
                sender.sendMessage("§e/cp startday §7- open today's 5 random points, 2 at a time");
                sender.sendMessage("§e/cp list §7- saved points and today's progress");
                sender.sendMessage("§e/cp open <name> §7- open one specific point now");
                sender.sendMessage("§e/cp stopday §7- close everything");
                sender.sendMessage("§e/cp remove <name> §7- delete a saved point");
            }
        }
        return true;
    }
}
