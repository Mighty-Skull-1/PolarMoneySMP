package me.moneysmp.commands;

import me.moneysmp.managers.CreditManager;
import me.moneysmp.managers.EliminationManager;
import me.moneysmp.managers.TeamManager;
import me.moneysmp.managers.TierManager;
import me.moneysmp.models.PlayerTier;
import me.moneysmp.models.Team;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.UUID;

public class TeamCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final CreditManager creditManager;
    private final TierManager tierManager;
    private final EliminationManager eliminationManager;

    public TeamCommand(JavaPlugin plugin, TeamManager teamManager, CreditManager creditManager,
                       TierManager tierManager, EliminationManager eliminationManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.creditManager = creditManager;
        this.tierManager = tierManager;
        this.eliminationManager = eliminationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // --- /team list ---
        if (sub.equals("list")) {
            Collection<Team> teams = teamManager.getTeams();
            if (teams.isEmpty()) {
                sender.sendMessage("§cNo teams have been created yet!");
                return true;
            }

            sender.sendMessage("§6§m--------------------------------------------------");
            sender.sendMessage("§e§l         ★ POLAR MONEY SMP TEAMS ★");
            sender.sendMessage("§6§m--------------------------------------------------");
            for (Team team : teams) {
                Player cap = Bukkit.getPlayer(team.getCaptain());
                String capName = (cap != null) ? cap.getName() : me.moneysmp.util.PlayerNames.getOrShort(team.getCaptain()) + " §7(offline)";
                sender.sendMessage(team.getColorCode() + "• " + team.getName() +
                        " §8| §fCaptain: §e" + capName +
                        " §8| §fMembers: §a" + team.getMembers().size());
                StringBuilder roster = new StringBuilder();
                for (UUID m : team.getMembers()) {
                    if (m.equals(team.getCaptain())) continue;
                    if (roster.length() > 0) roster.append("§7, ");
                    roster.append(Bukkit.getPlayer(m) != null ? "§a" : "§7").append(me.moneysmp.util.PlayerNames.getOrShort(m));
                }
                if (roster.length() > 0) sender.sendMessage("   §8» " + roster);
            }
            sender.sendMessage("§6§m--------------------------------------------------");
            return true;
        }

        // --- /team info [teamName] ---
        if (sub.equals("info")) {
            Team targetTeam = null;
            if (args.length >= 2) {
                targetTeam = teamManager.getTeamByName(args[1]);
            } else if (sender instanceof Player player) {
                targetTeam = teamManager.getTeamByPlayer(player.getUniqueId());
            }

            if (targetTeam == null) {
                sender.sendMessage("§cTeam not found! Use /team info <teamName>");
                return true;
            }

            Player capPlayer = Bukkit.getPlayer(targetTeam.getCaptain());
            String capName = (capPlayer != null) ? "§a" + capPlayer.getName() : "§7" + me.moneysmp.util.PlayerNames.getOrShort(targetTeam.getCaptain()) + " (offline)";
            int draftPurse = creditManager.getDraftCredits(targetTeam.getCaptain());
            int maxDraft = plugin != null ? plugin.getConfig().getInt("starting-draft-credits", 300) : 300;

            sender.sendMessage("§6§m--------------------------------------------------");
            sender.sendMessage("§6Team: " + targetTeam.getColorCode() + "§l" + targetTeam.getName());
            sender.sendMessage("§fCaptain: §e★ " + capName);
            sender.sendMessage("§fDraft Purse: §a" + draftPurse + " / " + maxDraft + " Credits");
            sender.sendMessage("§fMembers (" + targetTeam.getMembers().size() + "):");

            for (UUID mUuid : targetTeam.getMembers()) {
                Player mPlayer = Bukkit.getPlayer(mUuid);
                String mName = (mPlayer != null) ? "§a" + mPlayer.getName() : "§7" + me.moneysmp.util.PlayerNames.getOrShort(mUuid);
                PlayerTier tier = tierManager.getTier(mUuid);
                String elimStatus = (eliminationManager != null && eliminationManager.isEliminated(mUuid)) ? " §c[ELIMINATED]" : "";
                sender.sendMessage(" §8- " + tier.getPrefix(targetTeam.getColorCode()) + " " + mName + elimStatus + " §7(" + creditManager.getGameCredits(mUuid) + " Credits)");
            }
            sender.sendMessage("§6§m--------------------------------------------------");
            return true;
        }

        // --- /team rename <newName> ---
        if (sub.equals("rename")) {
            if (!(sender instanceof Player captain)) {
                sender.sendMessage("Only players can rename teams.");
                return true;
            }

            Team team = teamManager.getTeamByPlayer(captain.getUniqueId());
            if (team == null || !team.isCaptain(captain.getUniqueId())) {
                captain.sendMessage("§cOnly team captains can rename their team!");
                return true;
            }

            if (args.length < 2) {
                captain.sendMessage("§cUsage: /team rename <newName>");
                return true;
            }

            String oldName = team.getName();
            String newName = args[1];

            if (teamManager.teamExists(newName)) {
                captain.sendMessage("§cA team with the name '" + newName + "' already exists!");
                return true;
            }

            teamManager.renameTeam(team, newName);
            Bukkit.broadcastMessage("§6[Polar Money SMP] Captain " + captain.getName() + " renamed " + oldName + " to §a" + newName + "§6!");
            triggerCloudSync();
            return true;
        }

        // Admin subcommands below
        if (!sender.hasPermission("moneysmp.admin")) {
            sender.sendMessage("§cOnly server admins can run this team command!");
            return true;
        }

        // --- /team create <teamName> <captain> ---
        if (sub.equals("create")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /team create <teamName> <captain>");
                return true;
            }
            String teamName = args[1];
            if (createTeamProblem(sender, teamName)) return true;

            // Captain can be online, offline or someone who has never joined (Mojang lookup runs off the main thread).
            me.moneysmp.util.PlayerLookup.resolve(plugin, args[2], result -> {
                if (!result.found()) { sender.sendMessage(me.moneysmp.util.PlayerLookup.failMessage(result, args[2])); return; }
                createTeam(sender, teamName, result.uuid(), result.name() != null ? result.name() : args[2]);
            });
        } else if (sub.equals("remove")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /team remove <player>");
                return true;
            }

            // Works for offline players too: find them among team members by name.
            UUID targetId = null;
            String targetName = args[1];
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null) { targetId = online.getUniqueId(); targetName = online.getName(); }
            else {
                targetId = findMember(args[1]);
                if (targetId != null) targetName = me.moneysmp.util.PlayerNames.getOrShort(targetId);
            }
            if (targetId == null) {
                sender.sendMessage("§c" + args[1] + " isn't on any team. Check the spelling with /team info <team>.");
                return true;
            }

            Team team = teamManager.getTeamByPlayer(targetId);
            if (team == null) {
                sender.sendMessage("§c" + targetName + " is not currently in any team.");
                return true;
            }

            if (team.isCaptain(targetId)) {
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    sender.sendMessage("§c" + targetName + " is the captain of " + team.getName() + " - removing them DISBANDS the whole team.");
                    sender.sendMessage("§7To replace a captain use §e/team sub " + targetName + " <newPlayer>§7. To really disband, type §c/team remove " + targetName + " confirm");
                    return true;
                }
                teamManager.disbandTeam(team);
                sender.sendMessage("§aCaptain " + targetName + " removed. Team " + team.getName() + " was disbanded.");
            } else {
                teamManager.removePlayerFromTeam(targetId);
                tierManager.removeTier(targetId);
                sender.sendMessage("§aRemoved " + targetName + " from " + team.getName() + (online == null ? " §7(they were offline)" : "") + "§a!");
                sender.sendMessage("§7Their credits no longer count for " + team.getName() + ". To keep them for the team, use §e/team sub§7 instead.");
            }
            if (plugin instanceof me.moneysmp.MoneySMP smp && smp.getDataManager() != null) smp.getDataManager().saveData();
            triggerCloudSync();
        } else if (sub.equals("sub") || sub.equals("swap") || sub.equals("substitute")) {
            substitute(sender, args);
        } else if (sub.equals("disband")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /team disband <teamName>");
                return true;
            }

            String teamName = args[1];
            Team team = teamManager.getTeamByName(teamName);
            if (team == null) {
                sender.sendMessage("§cTeam '" + teamName + "' not found!");
                return true;
            }

            teamManager.disbandTeam(team);
            sender.sendMessage("§aSuccessfully disbanded team " + teamName + "!");
            triggerCloudSync();
        } else {
            sendHelp(sender);
        }
        return true;
    }

    /**
     * /team sub <oldPlayer> <newPlayer>
     * The new player takes the old player's exact spot: same team, tier, Game Credits, kills and deaths
     * (and captain role + draft purse if the old player was captain). The old player is removed completely.
     * Works while the old player is offline. Team totals don't change. Saves straight away.
     */
    private void substitute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /team sub <player leaving> <player coming in>");
            sender.sendMessage("§7The new player keeps the old player's team, tier, credits, kills and deaths. Works if the old player is offline.");
            return;
        }
        if (plugin instanceof me.moneysmp.MoneySMP smpCheck && smpCheck.getFfaManager() != null && smpCheck.getFfaManager().isActive()) {
            sender.sendMessage("§cYou can't sub players during the Team Deathmatch - do it before or after.");
            return;
        }

        if (findMember(args[1]) == null) {
            sender.sendMessage("§c" + args[1] + " isn't on any team. Check the spelling with /team info <team>.");
            return;
        }
        // New player: online, offline or never joined (Mojang lookup runs off the main thread).
        me.moneysmp.util.PlayerLookup.resolve(plugin, args[2], result -> {
            if (!result.found()) { sender.sendMessage(me.moneysmp.util.PlayerLookup.failMessage(result, args[2])); return; }
            doSubstitute(sender, args[1], result.uuid(), result.name() != null ? result.name() : args[2]);
        });
    }

    /** Checks the team name and team limit for /team create. Returns true (and tells the sender) if it can't be made. */
    private boolean createTeamProblem(CommandSender sender, String teamName) {
        if (teamManager.teamExists(teamName)) {
            sender.sendMessage("§cA team named '" + teamName + "' already exists!");
            return true;
        }
        int maxTeams = plugin != null ? plugin.getConfig().getInt("max-teams", 4) : 4;
        if (teamManager.getTeams().size() >= maxTeams) {
            sender.sendMessage("§cThere are already " + maxTeams + " teams (max-teams in config.yml).");
            return true;
        }
        return false;
    }

    /**
     * Makes the team with this captain. Works for captains who have never joined: they're saved by UUID
     * (with their name), so they show in /team list and on the website now, and are captain when they first join.
     */
    private void createTeam(CommandSender sender, String teamName, UUID captainId, String captainName) {
        // Check again: things may have changed while we were asking Mojang.
        if (createTeamProblem(sender, teamName)) return;
        Team current = teamManager.getTeamByPlayer(captainId);
        if (current != null) {
            sender.sendMessage("§c" + captainName + " is already on " + current.getName() + "! Remove them first.");
            return;
        }

        int draftCredits = plugin != null ? plugin.getConfig().getInt("starting-draft-credits", 300) : 300;
        int gameCredits = plugin != null ? plugin.getConfig().getInt("starting-game-credits", 100) : 100;

        teamManager.createTeam(teamName, captainId);
        tierManager.setTier(captainId, PlayerTier.CAPTAIN);
        creditManager.setDraftCredits(captainId, draftCredits);
        creditManager.setGameCredits(captainId, gameCredits);

        boolean online = Bukkit.getPlayer(captainId) != null;
        sender.sendMessage("§aTeam " + teamName + " created with Captain " + captainName +
                " (" + draftCredits + " Draft / " + gameCredits + " Game Credits)!"
                + (online ? "" : " §7(" + captainName + " isn't online - they'll be captain when they join.)"));
        if (plugin instanceof me.moneysmp.MoneySMP smp) {
            if (smp.getDataManager() != null) smp.getDataManager().saveData();
            if (smp.getDisplayManager() != null) smp.getDisplayManager().updateAllDisplays();
        }
        triggerCloudSync();
    }

    /** Finds a team member by name (works for offline and never-joined players). */
    private UUID findMember(String name) {
        for (Team t : teamManager.getTeams()) {
            for (UUID m : t.getMembers()) {
                String n = me.moneysmp.util.PlayerNames.get(m);
                if (n != null && n.equalsIgnoreCase(name)) return m;
            }
        }
        return null;
    }

    private void doSubstitute(CommandSender sender, String oldInput, UUID newId, String newName) {
        // Look the old player up again: things may have changed while we were asking Mojang.
        UUID oldId = findMember(oldInput);
        if (oldId == null) {
            sender.sendMessage("§c" + oldInput + " isn't on any team any more.");
            return;
        }
        String oldName = me.moneysmp.util.PlayerNames.getOrShort(oldId);

        if (newId.equals(oldId)) { sender.sendMessage("§cThat's the same player."); return; }
        Team newCurrent = teamManager.getTeamByPlayer(newId);
        if (newCurrent != null) {
            sender.sendMessage("§c" + newName + " is already on " + newCurrent.getName() + ". Remove them first.");
            return;
        }

        Team team = teamManager.getTeamByPlayer(oldId);
        boolean wasCaptain = team.isCaptain(oldId);
        PlayerTier tier = tierManager.getTier(oldId);
        int game = creditManager.getGameCredits(oldId);
        int draft = creditManager.getDraftCredits(oldId);
        int replacedCredits = creditManager.hasGameCreditsEntry(newId) ? creditManager.getGameCredits(newId) : -1;
        boolean eliminated = eliminationManager != null && eliminationManager.isEliminated(oldId);
        int kills = 0, deaths = 0;
        me.moneysmp.managers.StatsManager stats = (plugin instanceof me.moneysmp.MoneySMP smpS) ? smpS.getStatsManager() : null;
        if (stats != null) { kills = stats.getKills(oldId); deaths = stats.getDeaths(oldId); }

        // Put the new player in.
        teamManager.addPlayerToTeam(team, newId);
        if (wasCaptain) team.setCaptain(newId);
        tierManager.setTier(newId, tier);
        creditManager.setGameCredits(newId, game);
        if (wasCaptain) creditManager.setDraftCredits(newId, draft);
        if (stats != null) {
            stats.clearPlayer(newId);
            if (kills > 0) stats.setKills(newId, kills);
            if (deaths > 0) stats.setDeaths(newId, deaths);
        }
        if (eliminationManager != null) eliminationManager.setEliminated(newId, eliminated);

        // Take the old player out completely.
        teamManager.removePlayerFromTeam(oldId);
        tierManager.removeTier(oldId);
        creditManager.clearPlayer(oldId);
        if (stats != null) stats.clearPlayer(oldId);
        if (eliminationManager != null) eliminationManager.setEliminated(oldId, false);

        // Save right away so nothing is lost if the server stops.
        if (plugin instanceof me.moneysmp.MoneySMP smp && smp.getDataManager() != null) smp.getDataManager().saveData();
        triggerCloudSync();

        Bukkit.broadcastMessage("§6§l[SUB] " + team.getColorCode() + newName + " §ereplaces " + team.getColorCode() + oldName
                + " §eon " + team.getColorCode() + team.getName() + (wasCaptain ? " §6as Captain" : "") + "§e!");
        sender.sendMessage("§aDone. " + newName + " now has " + oldName + "'s spot: tier " + tier.name() + ", " + game
                + " Game Credits, " + kills + " kills, " + deaths + " deaths" + (wasCaptain ? ", captain + " + draft + " draft purse" : "") + ".");
        if (replacedCredits >= 0) {
            sender.sendMessage("§7(" + newName + " had " + replacedCredits + " credits of their own from before - those were replaced, so the team total stays the same.)");
        }
        Player oldOnline = Bukkit.getPlayer(oldId);
        if (oldOnline != null) oldOnline.sendMessage("§eYou've been subbed out of " + team.getName() + " by an admin.");
        Player newOnline = Bukkit.getPlayer(newId);
        if (newOnline != null) newOnline.sendMessage("§aYou've been subbed into " + team.getColorCode() + team.getName() + "§a! You take over " + oldName + "'s spot.");
    }

    private void triggerCloudSync() {
        if (plugin instanceof me.moneysmp.MoneySMP smp && smp.getWebServerManager() != null) {
            smp.getWebServerManager().requestPush();
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l[Polar Money SMP Team Commands]");
        sender.sendMessage("§e/team list §7- View all registered tournament teams");
        sender.sendMessage("§e/team info [team] §7- View team roster, tiers, and credits");
        sender.sendMessage("§e/team rename <newName> §7- Captain only: Rename your team");
        if (sender.hasPermission("moneysmp.admin")) {
            sender.sendMessage("§c/team create <name> <captain> §7- Create new team");
            sender.sendMessage("§c/team remove <player> §7- Remove member or disband if captain");
            sender.sendMessage("§c/team sub <old> <new> §7- Swap a player out (works offline); new player keeps their spot, credits and stats");
            sender.sendMessage("§c/team disband <name> §7- Disband an existing team");
        }
    }
}
