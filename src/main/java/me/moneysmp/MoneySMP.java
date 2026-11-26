package me.moneysmp;

import me.moneysmp.commands.*;
import me.moneysmp.listeners.BannedItemListener;
import me.moneysmp.listeners.BingoEventListener;
import me.moneysmp.listeners.KillEventListener;
import me.moneysmp.managers.*;
import me.moneysmp.models.SMPPhase;
import me.moneysmp.tabcompleters.MoneySMPTabCompleter;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

public class MoneySMP extends JavaPlugin {
    private TeamManager teamManager;
    private CreditManager creditManager;
    private TierManager tierManager;
    private DraftManager draftManager;
    private EventManager eventManager;
    private PhaseManager phaseManager;
    private DisplayManager displayManager;
    private EliminationManager eliminationManager;
    private me.moneysmp.managers.FfaManager ffaManager;
    private me.moneysmp.managers.CapturePointManager capturePointManager;
    private DataManager dataManager;
    private BannedItemManager bannedItemManager;
    private SMPGuiManager smpGuiManager;
    private StatsManager statsManager;
    private me.moneysmp.managers.RankManager rankManager;
    private me.moneysmp.web.WebServerManager webServerManager;

    @Override
    public void onEnable() {
        // New version? Copy every data file to backups/ before anything is loaded or changed.
        me.moneysmp.util.DataSafety.backupIfNewVersion(this);
        saveDefaultConfig();
        if (checkConfigReadable()) {
            migrateConfig();
            addMissingConfigOptions();
        }

        int defaultGameCredits = getConfig().getInt("starting-game-credits", 100);

        this.teamManager = new TeamManager();
        this.creditManager = new CreditManager(defaultGameCredits);
        this.tierManager = new TierManager();
        this.phaseManager = new PhaseManager();
        this.eliminationManager = new EliminationManager(this);
        this.bannedItemManager = new BannedItemManager(this);
        this.statsManager = new StatsManager();
        this.rankManager = new me.moneysmp.managers.RankManager(this);
        this.dataManager = new DataManager(this, teamManager, creditManager, tierManager, phaseManager, eliminationManager, statsManager, rankManager);

        this.draftManager = new DraftManager(this, teamManager, creditManager, tierManager);
        this.eventManager = new EventManager(this, teamManager, creditManager);
        this.ffaManager = new me.moneysmp.managers.FfaManager(this, teamManager, creditManager);
        this.capturePointManager = new me.moneysmp.managers.CapturePointManager(this, teamManager, creditManager);
        this.displayManager = new DisplayManager(this, teamManager, creditManager, tierManager, phaseManager);
        this.smpGuiManager = new SMPGuiManager(this, phaseManager, eventManager, teamManager, eliminationManager, dataManager, bannedItemManager);
        this.webServerManager = new me.moneysmp.web.WebServerManager(this, teamManager, creditManager, tierManager,
                phaseManager, eliminationManager, eventManager, draftManager, statsManager);
        this.webServerManager.start();
        this.draftManager.setOnChange(() -> webServerManager.requestPush());

        MoneySMPTabCompleter tabCompleter = new MoneySMPTabCompleter(tierManager, teamManager, eventManager, bannedItemManager);
        tabCompleter.setDraftManager(draftManager);

        // --- Commands Registration ---
        getCommand("smp").setExecutor(new SMPCommand(this, phaseManager, eventManager, dataManager, smpGuiManager));
        getCommand("smp").setTabCompleter(tabCompleter);

        getCommand("team").setExecutor(new TeamCommand(this, teamManager, creditManager, tierManager, eliminationManager));
        getCommand("team").setTabCompleter(tabCompleter);

        getCommand("tier").setExecutor(new TierCommand(this, tierManager));
        getCommand("tier").setTabCompleter(tabCompleter);

        getCommand("bid").setExecutor(new BidCommand(this, teamManager, creditManager, tierManager, draftManager));
        getCommand("bid").setTabCompleter(tabCompleter);

        getCommand("tc").setExecutor(new TeamChatCommand(teamManager));

        AnnounceCommand announceCommand = new AnnounceCommand();
        getCommand("announce").setExecutor(announceCommand);
        getCommand("announce").setTabCompleter(tabCompleter);
        getCommand("titleannounce").setExecutor(announceCommand);
        getCommand("titleannounce").setTabCompleter(tabCompleter);


        getCommand("cp").setExecutor(new me.moneysmp.commands.CapturePointCommand(capturePointManager));
        getCommand("cp").setTabCompleter(tabCompleter);
        getCommand("fine").setExecutor(new me.moneysmp.commands.FineCommand(this, teamManager, creditManager));
        getCommand("fine").setTabCompleter(tabCompleter);

        getCommand("freefall").setExecutor(new FreeFallCommand(this, phaseManager, teamManager, creditManager));
        getCommand("freefall").setTabCompleter(tabCompleter);

        getCommand("ffa").setExecutor(new me.moneysmp.commands.FfaCommand(ffaManager, phaseManager));
        getCommand("ffa").setTabCompleter(tabCompleter);

        getCommand("bingo").setExecutor(new BingoCommand(eventManager));
        getCommand("bingo").setTabCompleter(tabCompleter);

        getCommand("credits").setExecutor(new CreditsCommand(this, creditManager));
        getCommand("credits").setTabCompleter(tabCompleter);

        getCommand("leaderboard").setExecutor(new LeaderboardCommand(teamManager, creditManager));

        getCommand("website").setExecutor(new WebsiteCommand(this));

        getCommand("rank").setExecutor(new me.moneysmp.commands.RankCommand(this, rankManager));
        getCommand("rank").setTabCompleter(tabCompleter);

        BannedItemsCommand bannedItemsCommand = new BannedItemsCommand(bannedItemManager);
        getCommand("banneditems").setExecutor(bannedItemsCommand);
        getCommand("banneditems").setTabCompleter(tabCompleter);

        // --- Listeners Registration ---
        getServer().getPluginManager().registerEvents(new KillEventListener(
                this, teamManager, creditManager, tierManager, phaseManager, eventManager, eliminationManager, smpGuiManager), this);
        getServer().getPluginManager().registerEvents(new BingoEventListener(this, eventManager), this);
        getServer().getPluginManager().registerEvents(new me.moneysmp.listeners.DraftItemListener(this, draftManager), this);
        getServer().getPluginManager().registerEvents(new BannedItemListener(bannedItemManager), this);
        getServer().getPluginManager().registerEvents(new me.moneysmp.listeners.RankListener(rankManager), this);
        getServer().getPluginManager().registerEvents(me.moneysmp.util.DataSafety.joinWarnings(), this);

        // --- Schedulers ---
        Bukkit.getScheduler().runTaskTimer(this, () -> displayManager.updateAllDisplays(), 0L, 20L);

        int autoSaveMinutes = getConfig().getInt("auto-save-interval-minutes", 5);
        long autoSaveTicks = Math.max(1, autoSaveMinutes) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(this, () -> dataManager.saveData(), autoSaveTicks, autoSaveTicks);

        // Carry on any bingo / capture points that were running before a /reload or restart.
        loadEventState();
        // After a /reload, players are already online: clean up leftover draft items and refresh their displays.
        for (org.bukkit.entity.Player online : Bukkit.getOnlinePlayers()) {
            try { draftManager.takeItems(online); } catch (Throwable ignored) {}
            try { rankManager.apply(online); } catch (Throwable ignored) {}
        }
        Bukkit.getScheduler().runTask(this, () -> { if (displayManager != null) displayManager.updateAllDisplays(); });

        getLogger().info("Polar Money SMP v" + getPluginMeta().getVersion() + " loaded successfully with full persistence and tournament systems!");
    }

    @Override
    public void onDisable() {
        // Reload-safe shutdown: every step is guarded so one problem can never stop the data from being saved.
        safely("save running events", this::saveEventState);
        safely("hide capture point bars", () -> { if (capturePointManager != null) capturePointManager.hideBars(); });
        safely("stop Team Deathmatch", () -> { if (ffaManager != null) ffaManager.stop(); });
        safely("cancel draft auction", () -> { if (draftManager != null) draftManager.cancelDraft(); });
        safely("save data", () -> { if (dataManager != null) dataManager.saveData(); });
        safely("stop web sync", () -> { if (webServerManager != null) webServerManager.stop(); });
        safely("remove rank permissions", () -> { if (rankManager != null) rankManager.removeAll(); });
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("Polar Money SMP v" + getPluginMeta().getVersion() + " disabled successfully and data saved!");
    }

    private void safely(String what, Runnable r) {
        try { r.run(); } catch (Throwable t) { getLogger().warning("While shutting down, couldn't " + what + ": " + t); }
    }

    /** Running bingo + capture points are written to events-state.yml so /reload (or a restart) carries on. */
    private void saveEventState() {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        if (eventManager != null) eventManager.saveState(y.createSection("bingo"));
        if (capturePointManager != null) capturePointManager.saveState(y.createSection("capture"));
        // Refuses if events-state.yml couldn't be read at startup (so it's never overwritten).
        me.moneysmp.util.DataSafety.saveAtomically(this, y, new java.io.File(getDataFolder(), "events-state.yml"));
    }

    private void loadEventState() {
        java.io.File f = new java.io.File(getDataFolder(), "events-state.yml");
        if (!f.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration y = me.moneysmp.util.DataSafety.load(this, f);
        if (y == null) return; // unreadable: keep the file as it is (admins have been warned)
        safely("restore bingo", () -> eventManager.loadState(y.getConfigurationSection("bingo")));
        safely("restore capture points", () -> capturePointManager.loadState(y.getConfigurationSection("capture")));
        f.delete();
    }

    /**
     * Bukkit quietly treats a broken config.yml as empty, and the next save would replace it with defaults.
     * We read it ourselves first; if it's broken, nothing is ever saved over it (see {@link #saveConfig()}).
     */
    private boolean checkConfigReadable() {
        java.io.File f = new java.io.File(getDataFolder(), "config.yml");
        return me.moneysmp.util.DataSafety.load(this, f) != null;
    }

    @Override
    public void saveConfig() {
        if (me.moneysmp.util.DataSafety.isUnreadable("config.yml")) {
            getLogger().warning("Not saving config.yml: it couldn't be read at startup, so saving would overwrite your settings.");
            return;
        }
        super.saveConfig();
    }

    /**
     * Adds options that are new in this version's config.yml (with their default values) to the server's
     * config.yml. Settings that are already there are never changed.
     */
    private void addMissingConfigOptions() {
        org.bukkit.configuration.Configuration defaults = getConfig().getDefaults();
        if (defaults == null) return;
        java.util.List<String> added = new java.util.ArrayList<>();
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path)) continue;
            if (getConfig().isSet(path)) continue;
            // Don't add a value inside something the server has set to a plain value (e.g. a list or number).
            int dot = path.lastIndexOf('.');
            if (dot > 0 && getConfig().isSet(path.substring(0, dot)) && !getConfig().isConfigurationSection(path.substring(0, dot))) continue;
            getConfig().set(path, defaults.get(path));
            added.add(path);
        }
        if (!added.isEmpty()) {
            saveConfig();
            getLogger().info("Added new config.yml option(s) with default values: " + String.join(", ", added)
                    + ". Your existing settings were not changed.");
        }
    }

    public DataManager getDataManager() { return dataManager; }
    public EliminationManager getEliminationManager() { return eliminationManager; }
    public me.moneysmp.managers.FfaManager getFfaManager() { return ffaManager; }
    public TierManager getTierManager() { return tierManager; }
    public me.moneysmp.managers.CapturePointManager getCapturePointManager() { return capturePointManager; }
    public SMPGuiManager getSmpGuiManager() { return smpGuiManager; }
    public BannedItemManager getBannedItemManager() { return bannedItemManager; }
    public StatsManager getStatsManager() { return statsManager; }
    public DisplayManager getDisplayManager() { return displayManager; }
    public me.moneysmp.managers.RankManager getRankManager() { return rankManager; }

    public void resetTournament(CommandSender sender) {
        // 1. Stop any ongoing draft auction or events
        if (draftManager != null) {
            draftManager.cancelDraft();
        }
        if (eventManager != null) {
            eventManager.stopBingo(false);
        }
        if (capturePointManager != null) capturePointManager.stopDay(false);

        // 2. Reset player gamemodes, health, hunger, inventories, and teleport to world spawn
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            org.bukkit.attribute.AttributeInstance maxHealth = maxHealthAttribute(player);
            player.setHealth(maxHealth != null ? maxHealth.getValue() : 20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setExhaustion(0.0f);
            player.setFireTicks(0);
            for (PotionEffect pe : player.getActivePotionEffects()) {
                player.removePotionEffect(pe.getType());
            }
            player.getInventory().clear();
            player.getEnderChest().clear();
            player.setExp(0.0f);
            player.setLevel(0);

            Location spawn = player.getWorld().getSpawnLocation();
            player.teleport(spawn);

            player.sendTitle("§c§lTOURNAMENT RESET", "§eTournament reset to the beginning!", 10, 80, 20);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
        }

        // 3. Clear eliminations and PvP stats
        if (eliminationManager != null) {
            eliminationManager.clearAll();
        }
        if (statsManager != null) {
            statsManager.clearAll();
        }
        if (draftManager != null) {
            draftManager.clearHistory();
        }

        // 4. Disband all teams
        if (teamManager != null) {
            teamManager.clearAllTeams();
        }

        // 5. Reset all player tiers
        if (tierManager != null) {
            tierManager.clearAll();
        }

        // 6. Reset all credits
        if (creditManager != null) {
            creditManager.resetAll();
        }

        // 7. Reset phase to DRAFT
        if (phaseManager != null) {
            phaseManager.setPhaseSilently(SMPPhase.DRAFT);
        }

        // 8. Wipe data.yml and save fresh state
        if (dataManager != null) {
            dataManager.resetData();
        }

        // 9. Update displays
        if (displayManager != null) {
            displayManager.updateAllDisplays();
        }

        // 10. Broadcast
        Bukkit.broadcastMessage("§c§l========================================");
        Bukkit.broadcastMessage("§c§l[Polar Money SMP] FULL TOURNAMENT RESET!");
        Bukkit.broadcastMessage("§e• All players restored to Survival mode at spawn.");
        Bukkit.broadcastMessage("§e• Eliminations cleared & all players revived.");
        Bukkit.broadcastMessage("§e• Tournament phase set to: " + SMPPhase.DRAFT.getDisplayName());
        Bukkit.broadcastMessage("§e• All teams disbanded & player tiers cleared.");
        Bukkit.broadcastMessage("§e• Credits reset to starting defaults.");
        Bukkit.broadcastMessage("§e• All events stopped & saved data wiped clean.");
        Bukkit.broadcastMessage("§c§l========================================");

        if (sender != null) {
            sender.sendMessage("§a[Polar Money SMP] Tournament successfully reset from the beginning!");
        }

        // 11. Update the website right away
        if (webServerManager != null) {
            webServerManager.requestPush();
        }
    }

    /** Max-health attribute that works on Paper 1.21.1 ("generic.max_health") and 1.21.3+ ("max_health"). */
    private static org.bukkit.attribute.AttributeInstance maxHealthAttribute(Player player) {
        for (String key : new String[]{"max_health", "generic.max_health"}) {
            try {
                Attribute attr = org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.minecraft(key));
                if (attr != null) return player.getAttribute(attr);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    /** Updates settings in older config.yml files to the current tournament rules. */
    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version < 2) {
            getConfig().set("draft.auction-seconds", 30);
            getConfig().set("draft.reset-timer-on-bid", false);
            getConfig().set("config-version", 2);
            saveConfig();
            getLogger().info("Updated config.yml: draft auctions are now 30 seconds with no timer reset.");
        }
        if (getConfig().getInt("config-version", 1) < 3) {
            if (getConfig().getInt("starting-draft-credits", 400) == 400) getConfig().set("starting-draft-credits", 300);
            getConfig().set("config-version", 3);
            saveConfig();
            getLogger().info("Updated config.yml: captains now start with 300 draft credits.");
        }
        if (getConfig().getInt("config-version", 1) < 4) {
            getConfig().set("rewards.koth.1st", 125);
            getConfig().set("rewards.koth.2nd", 100);
            getConfig().set("rewards.koth.3rd", 75);
            getConfig().set("rewards.koth.4th", 50);
            getConfig().set("rewards.koth.5th", 25);
            getConfig().set("koth.duration-seconds", 300);
            getConfig().set("koth.give-sword", true);
            getConfig().set("koth.sword-uses", 5);
            getConfig().set("config-version", 4);
            saveConfig();
            getLogger().info("Updated config.yml: KOTH is 5 minutes, pays 125/100/75/50/25, and gives Knockback II swords.");
        }
        if (getConfig().getInt("config-version", 1) < 7) {
            // Season 2: S/A/B/C/D kill rewards, 4 teams, capture points, no draft timer, new bounty order.
            getConfig().set("rewards.captain-kill", 30);
            getConfig().set("rewards.tier-a-kill", 25);
            getConfig().set("rewards.tier-b-kill", 20);
            getConfig().set("rewards.tier-c-kill", 15);
            getConfig().set("rewards.tier-d-kill", 10);
            getConfig().set("max-teams", 4);
            getConfig().set("draft.bid-buttons", java.util.List.of(5, 10, 25));
            getConfig().set("draft.auction-seconds", null);
            getConfig().set("draft.reset-timer-on-bid", null);
            getConfig().set("ffa.bounty-order", java.util.List.of("CAPTAIN", "D", "A", "C", "B"));
            getConfig().set("rewards.koth", null);
            getConfig().set("koth", null);
            if (!getConfig().isSet("capture-points.reward")) {
                getConfig().set("capture-points.reward", 75);
                getConfig().set("capture-points.radius", 6);
                getConfig().set("capture-points.control-seconds", 240);
                getConfig().set("capture-points.capture-seconds", 480);
                getConfig().set("capture-points.per-day", 5);
                getConfig().set("capture-points.max-active", 2);
                getConfig().set("capture-points.steal-drain-speed", 2);
            }
        }
        if (getConfig().getInt("config-version", 1) < 6) {
            if (!getConfig().isSet("ffa.kill-bonus")) getConfig().set("ffa.kill-bonus", 20);
            if (!getConfig().isSet("ffa.bounty-bonus")) getConfig().set("ffa.bounty-bonus", 20);
            if (!getConfig().isSet("ffa.bounty-rotate-seconds")) getConfig().set("ffa.bounty-rotate-seconds", 240);
            if (!getConfig().isSet("ffa.bounty-order")) getConfig().set("ffa.bounty-order", java.util.List.of("CAPTAIN", "C", "A", "B"));
        }
        if (getConfig().getInt("config-version", 1) < 5) {
            if (!getConfig().isSet("rewards.ffa.1st")) {
                getConfig().set("rewards.ffa.1st", 125);
                getConfig().set("rewards.ffa.2nd", 100);
                getConfig().set("rewards.ffa.3rd", 75);
                getConfig().set("rewards.ffa.4th", 50);
                getConfig().set("rewards.ffa.5th", 25);
            }
            getConfig().set("config-version", 5);
            saveConfig();
            getLogger().info("Updated config.yml: added Free For All rewards (125/100/75/50/25).");
        }
        if (getConfig().getInt("config-version", 1) < 6) {
            getConfig().set("config-version", 6);
            saveConfig();
            getLogger().info("Updated config.yml: Team Deathmatch kill bonus +20, bounty tier +20 rotating every 4 minutes.");
        }
        if (getConfig().getInt("config-version", 1) < 7) {
            getConfig().set("config-version", 7);
            saveConfig();
            getLogger().info("Updated config.yml for Season 2: S/A/B/C/D kill rewards 30/25/20/15/10, 4 teams, capture points, no draft timer.");
        }
        if (getConfig().getInt("config-version", 1) < 8) {
            // Capture points: one 12-minute capture in a 16 x 16 square.
            getConfig().set("capture-points.capture-seconds", 720);
            getConfig().set("capture-points.size", 16);
            getConfig().set("capture-points.control-seconds", null);
            getConfig().set("capture-points.radius", null);
            getConfig().set("config-version", 8);
            saveConfig();
            getLogger().info("Updated config.yml: capture points are one 12-minute capture in a 16x16 square.");
        }
        // The website moved from willy-money-smp-web to polar-money-smp-web (the old address is gone).
        // Only the exact old address is replaced; a custom website-url is left alone.
        String websiteUrl = getConfig().getString("website-url", "").trim();
        if (websiteUrl.replaceAll("/+$", "").equalsIgnoreCase("https://willy-money-smp-web.vercel.app")) {
            getConfig().set("website-url", "https://polar-money-smp-web.vercel.app/");
            saveConfig();
            getLogger().info("Updated config.yml: website-url is now https://polar-money-smp-web.vercel.app/ (the old willy address no longer works).");
        }
    }

    public me.moneysmp.web.WebServerManager getWebServerManager() {
        return webServerManager;
    }
}
