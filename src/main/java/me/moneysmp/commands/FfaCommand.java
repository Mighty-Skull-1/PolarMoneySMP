package me.moneysmp.commands;

import me.moneysmp.managers.FfaManager;
import me.moneysmp.managers.PhaseManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class FfaCommand implements CommandExecutor {
    private final FfaManager ffa;
    private final PhaseManager phaseManager;

    public FfaCommand(FfaManager ffa, PhaseManager phaseManager) {
        this.ffa = ffa;
        this.phaseManager = phaseManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "start" -> {
                if (phaseManager.getCurrentPhase() == me.moneysmp.models.SMPPhase.DRAFT) {
                    sender.sendMessage("§cThe draft is still running - finish it before the final fight.");
                    return true;
                }
                // The Team Deathmatch is the final fight: switch to the Finale phase (PvP on).
                me.moneysmp.models.SMPPhase before = phaseManager.getCurrentPhase();
                phaseManager.setPhaseSilently(me.moneysmp.models.SMPPhase.FINALE);
                String err = ffa.start();
                if (err != null) {
                    phaseManager.setPhaseSilently(before);
                    sender.sendMessage("§c" + err);
                }
            }
            case "status", "standings" -> ffa.sendStatus(sender);
            case "out" -> {
                if (args.length < 2) { sender.sendMessage("§cUsage: /ffa out <player>"); return true; }
                if (!ffa.isActive()) { sender.sendMessage("§cNo Team Deathmatch is running."); return true; }
                // Look the name up without asking Mojang on the main thread (that could freeze the server):
                // anyone standing in the FFA is online or has a saved name.
                org.bukkit.entity.Player online = Bukkit.getPlayerExact(args[1]);
                java.util.UUID targetId = online != null ? online.getUniqueId() : me.moneysmp.util.PlayerNames.findSaved(args[1]);
                if (targetId == null) {
                    OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(args[1]);
                    if (cached != null) targetId = cached.getUniqueId();
                }
                if (targetId == null || !ffa.isAlive(targetId)) { sender.sendMessage("§c" + args[1] + " isn't standing in this FFA."); return true; }
                ffa.knockOut(targetId, "removed by admin");
            }
            case "end" -> {
                if (!ffa.isActive()) { sender.sendMessage("§cNo Team Deathmatch is running."); return true; }
                ffa.finish();
            }
            case "stop", "cancel" -> {
                if (!ffa.isActive()) { sender.sendMessage("§cNo Team Deathmatch is running."); return true; }
                ffa.stop();
            }
            default -> {
                sender.sendMessage("§4§lFinal Fight - Team Deathmatch (/tdm or /ffa):");
                sender.sendMessage("§e/ffa start §7- everyone on a team who's online joins; last team standing wins (switches to the Finale phase)");
                sender.sendMessage("§e/ffa status §7- who's still standing and placings so far");
                sender.sendMessage("§e/ffa out <player> §7- knock out an AFK/inactive player");
                sender.sendMessage("§e/ffa end §7- end now, pay places (teams still in ranked by credits), crown the winner");
                sender.sendMessage("§e/ffa stop §7- cancel with no rewards");
            }
        }
        return true;
    }
}
