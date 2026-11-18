package me.moneysmp.managers;

import me.moneysmp.models.PlayerTier;
import me.moneysmp.models.SMPPhase;
import me.moneysmp.models.Team;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DataManager {
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final CreditManager creditManager;
    private final TierManager tierManager;
    private final PhaseManager phaseManager;
    private final EliminationManager eliminationManager;
    private final StatsManager statsManager;

    private final File dataFile;
    private FileConfiguration dataConfig;

    /** data.yml existed but couldn't be read: we NEVER save over it (that would wipe the tournament). */
    private boolean loadFailed = false;
    /** data.yml had teams when loaded: an empty save after that is refused unless it's a full reset. */
    private boolean hadTeamsOnLoad = false;
    private boolean resetInProgress = false;

    public DataManager(JavaPlugin plugin, TeamManager teamManager, CreditManager creditManager,
                       TierManager tierManager, PhaseManager phaseManager, EliminationManager eliminationManager,
                       StatsManager statsManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.creditManager = creditManager;
        this.tierManager = tierManager;
        this.phaseManager = phaseManager;
        this.eliminationManager = eliminationManager;
        this.statsManager = statsManager;

        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        loadData();
    }

    public void loadData() {
        if (!dataFile.exists()) {
            return;
        }

        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(dataFile);
        } catch (Exception e) {
            loadFailed = true;
            File broken = new File(plugin.getDataFolder(), "data-unreadable-" + stamp() + ".yml");
            try { java.nio.file.Files.copy(dataFile.toPath(), broken.toPath()); } catch (IOException ignored) {}
            plugin.getLogger().severe("==============================================================");
            plugin.getLogger().severe("data.yml could not be read: " + e.getMessage());
            plugin.getLogger().severe("NOTHING will be saved over it, so your tournament data is safe.");
            plugin.getLogger().severe("A copy is at " + broken.getName() + ". Restore a file from the backups folder and restart.");
            plugin.getLogger().severe("==============================================================");
            dataConfig = new YamlConfiguration();
            return;
        }
        dataConfig = loaded;
        backup();

        // Load Phase
        String phaseStr = dataConfig.getString("phase");
        if (phaseStr != null) {
            try {
                phaseManager.setPhaseSilently(SMPPhase.valueOf(phaseStr));
            } catch (IllegalArgumentException ignored) {}
        }

        // Load Tiers
        ConfigurationSection tiersSec = dataConfig.getConfigurationSection("tiers");
        if (tiersSec != null) {
            for (String key : tiersSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    PlayerTier tier = PlayerTier.valueOf(tiersSec.getString(key));
                    tierManager.setTier(uuid, tier);
                } catch (Exception ignored) {}
            }
        }

        // Load Draft Credits
        ConfigurationSection draftSec = dataConfig.getConfigurationSection("draft-credits");
        if (draftSec != null) {
            for (String key : draftSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    creditManager.setDraftCredits(uuid, draftSec.getInt(key));
                } catch (Exception ignored) {}
            }
        }

        // Load Game Credits
        ConfigurationSection gameSec = dataConfig.getConfigurationSection("game-credits");
        if (gameSec != null) {
            for (String key : gameSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    creditManager.setGameCredits(uuid, gameSec.getInt(key));
                } catch (Exception ignored) {}
            }
        }

        // Load Teams
        ConfigurationSection teamsSec = dataConfig.getConfigurationSection("teams");
        if (teamsSec != null) {
            for (String key : teamsSec.getKeys(false)) {
                String teamName = teamsSec.getString(key + ".name");
                String captainStr = teamsSec.getString(key + ".captain");
                if (teamName != null && captainStr != null) {
                    try {
                        UUID captainUuid = UUID.fromString(captainStr);
                        Team team = teamManager.createTeam(teamName, captainUuid);

                        List<String> memberList = teamsSec.getStringList(key + ".members");
                        for (String mStr : memberList) {
                            try {
                                UUID mUuid = UUID.fromString(mStr);
                                teamManager.addPlayerToTeam(team, mUuid);
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        // Load Eliminated Players
        List<String> elimList = dataConfig.getStringList("eliminated");
        for (String eStr : elimList) {
            try {
                eliminationManager.setEliminated(UUID.fromString(eStr), true);
            } catch (Exception ignored) {}
        }

        // Load PvP stats (kills / deaths)
        loadIntMap("kills", (uuid, value) -> statsManager.setKills(uuid, value));
        loadIntMap("deaths", (uuid, value) -> statsManager.setDeaths(uuid, value));

        hadTeamsOnLoad = !teamManager.getTeams().isEmpty();
        plugin.getLogger().info("Tournament data loaded successfully from data.yml (" + teamManager.getTeams().size() + " teams)");
    }

    public void saveData() {
        if (loadFailed) {
            plugin.getLogger().warning("Not saving: data.yml couldn't be read at startup, so saving would overwrite it. Fix data.yml and restart.");
            return;
        }
        if (hadTeamsOnLoad && teamManager.getTeams().isEmpty() && !resetInProgress) {
            plugin.getLogger().warning("Not saving: no teams in memory but data.yml has teams. Refusing to overwrite it.");
            return;
        }
        dataConfig = new YamlConfiguration();

        // Save Phase
        dataConfig.set("phase", phaseManager.getCurrentPhase().name());

        // Save Tiers
        for (Map.Entry<UUID, PlayerTier> entry : tierManager.getAllTiers().entrySet()) {
            dataConfig.set("tiers." + entry.getKey().toString(), entry.getValue().name());
        }

        // Save Draft Credits
        for (Map.Entry<UUID, Integer> entry : creditManager.getAllDraftCredits().entrySet()) {
            dataConfig.set("draft-credits." + entry.getKey().toString(), entry.getValue());
        }

        // Save Game Credits
        for (Map.Entry<UUID, Integer> entry : creditManager.getAllGameCredits().entrySet()) {
            dataConfig.set("game-credits." + entry.getKey().toString(), entry.getValue());
        }

        // Save Teams
        for (Team team : teamManager.getTeams()) {
            String key = "teams." + team.getName().toLowerCase();
            dataConfig.set(key + ".name", team.getName());
            dataConfig.set(key + ".captain", team.getCaptain().toString());

            List<String> memberUuids = new ArrayList<>();
            for (UUID mUuid : team.getMembers()) {
                memberUuids.add(mUuid.toString());
            }
            dataConfig.set(key + ".members", memberUuids);
        }

        // Save Eliminated Players
        List<String> elimList = new ArrayList<>();
        for (UUID uuid : eliminationManager.getEliminatedPlayers()) {
            elimList.add(uuid.toString());
        }
        dataConfig.set("eliminated", elimList);

        // Save PvP stats
        for (Map.Entry<UUID, Integer> entry : statsManager.getAllKills().entrySet()) {
            dataConfig.set("kills." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, Integer> entry : statsManager.getAllDeaths().entrySet()) {
            dataConfig.set("deaths." + entry.getKey().toString(), entry.getValue());
        }

        // Write to a temp file, then swap it in: a crash or reload mid-save can never leave a half-written data.yml.
        File tmp = new File(plugin.getDataFolder(), "data.yml.tmp");
        try {
            dataConfig.save(tmp);
            try {
                java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                java.nio.file.Files.move(tmp.toPath(), dataFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save tournament data to data.yml: " + e.getMessage());
        }
    }

    private void loadIntMap(String path, java.util.function.BiConsumer<UUID, Integer> setter) {
        ConfigurationSection sec = dataConfig.getConfigurationSection(path);
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                setter.accept(UUID.fromString(key), sec.getInt(key));
            } catch (Exception ignored) {}
        }
    }

    public void resetData() {
        backup(); // keep a copy of the tournament before a full reset, just in case
        if (dataFile.exists()) {
            dataFile.delete();
        }
        dataConfig = new YamlConfiguration();
        resetInProgress = true;
        loadFailed = false;
        saveData();
        resetInProgress = false;
        hadTeamsOnLoad = false;
    }

    /** Copies data.yml into plugins/WillyMoneySMP/backups/ (keeps the newest 20). Runs on every start/reload and before a reset. */
    private void backup() {
        if (!dataFile.exists()) return;
        try {
            File dir = new File(plugin.getDataFolder(), "backups");
            if (!dir.exists() && !dir.mkdirs()) return;
            java.nio.file.Files.copy(dataFile.toPath(), new File(dir, "data-" + stamp() + ".yml").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            File[] all = dir.listFiles((d, n) -> n.startsWith("data-") && n.endsWith(".yml"));
            if (all != null && all.length > 20) {
                java.util.Arrays.sort(all, java.util.Comparator.comparing(File::getName));
                for (int k = 0; k < all.length - 20; k++) all[k].delete();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't back up data.yml: " + e.getMessage());
        }
    }

    private static String stamp() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date());
    }
}
