package me.moneysmp.managers;

import me.moneysmp.models.Team;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.*;

public class EventManager {
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final CreditManager creditManager;

    // Bingo State
    private final List<String> taskOrder = new ArrayList<>();
    private final Map<String, Team> bingoBoard = new LinkedHashMap<>();
    private final Map<String, Material> bingoIcons = new LinkedHashMap<>();
    private final Set<String> completedLines = new HashSet<>();
    private final List<Team> bingoWinnersOrder = new ArrayList<>();
    private boolean bingoActive = false;

    public EventManager(JavaPlugin plugin, TeamManager teamManager, CreditManager creditManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.creditManager = creditManager;
        setupBingoTasks();
    }

    private static Team topTeam(Map<Team, Integer> scores) {
        Team best = null;
        int max = 0;
        for (Map.Entry<Team, Integer> e : scores.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        }
        return best;
    }

    /**
     * Ranks teams by score (highest first) and pays each place's reward to that team's captain.
     * Teams with the same score share the place (and its reward). Teams with 0 get nothing.
     * Returns the chat lines describing the result.
     */
    private List<String> awardPlaces(Map<Team, Integer> scores, int[] rewards, String unit) {
        List<Map.Entry<Team, Integer>> sorted = new ArrayList<>();
        for (Map.Entry<Team, Integer> e : scores.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0 && teamManager.getTeamByName(e.getKey().getName()) != null) sorted.add(e);
        }
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        String[] names = {"1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th"};
        List<String> lines = new ArrayList<>();
        int place = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i == 0 || !sorted.get(i).getValue().equals(sorted.get(i - 1).getValue())) place = i;
            if (place >= rewards.length) break;
            Team team = sorted.get(i).getKey();
            int reward = rewards[place];
            creditManager.addGameCredits(team.getCaptain(), reward);
            lines.add("  §6" + names[Math.min(place, names.length - 1)] + ": " + team.getColorCode() + team.getName()
                    + " §7(" + sorted.get(i).getValue() + " " + unit + ") §a+" + reward + " Game Credits");
        }
        return lines;
    }


    // --- INTERACTIVE BINGO 5X5 GUI & AUTOMATION ---
    private void setupBingoTasks() {
        taskOrder.clear();
        bingoBoard.clear();
        bingoIcons.clear();

        addTask("Trade with a Villager", Material.EMERALD);
        addTask("Tame a Horse", Material.SADDLE);
        addTask("Craft Golden Carrots", Material.GOLDEN_CARROT);
        addTask("Eat Suspicious Stew", Material.SUSPICIOUS_STEW);
        addTask("Fill a Map", Material.FILLED_MAP);
        addTask("Mine Emerald Ore", Material.EMERALD_ORE);
        addTask("Obtain Heavy Core", Material.HEAVY_CORE);
        addTask("Obtain a Goat Horn", Material.GOAT_HORN);
        addTask("Find a Shipwreck", Material.OAK_BOAT);
        addTask("Obtain a Nautilus Shell", Material.NAUTILUS_SHELL);
        addTask("Get a Music Disc", Material.MUSIC_DISC_CAT);
        addTask("Find an Armor Trim", Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE);
        addTask("Kill a Warden", Material.SCULK_SHRIEKER);
        addTask("Brew Fire Resistance", Material.MAGMA_CREAM);
        addTask("Enchant a Diamond Sword", Material.DIAMOND_SWORD);
        addTask("Craft a Lodestone", Material.LODESTONE);
        addTask("Kill a Pillager Captain", Material.WHITE_BANNER);
        addTask("Craft Beacon", Material.BEACON);
        addTask("Wear Full Leather", Material.LEATHER_CHESTPLATE);
        addTask("Kill a Ghast", Material.GHAST_TEAR);
        addTask("Collect a Breeze Rod", Material.BREEZE_ROD);
        addTask("Get a Trident", Material.TRIDENT);
        addTask("Kill an Elder Guardian", Material.PRISMARINE_SHARD);
        addTask("Find an Ancient City", Material.SCULK_CATALYST);
        addTask("Kill a Witch", Material.CAULDRON);
    }

    private void addTask(String task, Material icon) {
        taskOrder.add(task);
        bingoBoard.put(task, null);
        bingoIcons.put(task, icon);
    }

    public void startBingo() {
        bingoActive = true;
        completedLines.clear();
        bingoWinnersOrder.clear();
        setupBingoTasks();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§6§lLOCKOUT BINGO", "§eAuto-tracking active! Type /bingo to view board", 10, 80, 20);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        Bukkit.broadcastMessage("§6§l[BINGO] §eInteractive 5x5 Lockout Bingo is now LIVE! Complete tasks automatically to claim them!");
        // Everyone sees the board at the same moment (nobody could peek before - /bingo is locked until now).
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SPECTATOR) openBingoGUI(p);
        }
        int[] r = bingoRewards();
        Bukkit.broadcastMessage("§6§l[BINGO REWARDS] §eMost squares when bingo ends wins! §e1st: §a+" + r[0] + " §8| §e2nd: §a+" + r[1]
                + " §8| §e3rd: §a+" + r[2] + " §8| §e4th: §a+" + r[3] + " §8| §e5th: §a+" + r[4]);
    }

    /** Saves a running bingo so a /reload or restart carries on where it left off. */
    public void saveState(org.bukkit.configuration.ConfigurationSection out) {
        out.set("active", bingoActive);
        if (!bingoActive) return;
        for (Map.Entry<String, Team> e : bingoBoard.entrySet()) {
            if (e.getValue() != null) out.set("claims." + taskOrder.indexOf(e.getKey()), e.getValue().getName());
        }
        out.set("lines", new ArrayList<>(completedLines));
        List<String> winners = new ArrayList<>();
        for (Team t : bingoWinnersOrder) winners.add(t.getName());
        out.set("winners", winners);
    }

    /** Restores a bingo saved by saveState, quietly (no "bingo started" announcements). */
    public void loadState(org.bukkit.configuration.ConfigurationSection in) {
        if (in == null || !in.getBoolean("active")) return;
        setupBingoTasks();
        completedLines.clear();
        bingoWinnersOrder.clear();
        org.bukkit.configuration.ConfigurationSection claims = in.getConfigurationSection("claims");
        if (claims != null) {
            for (String idx : claims.getKeys(false)) {
                try {
                    int i = Integer.parseInt(idx);
                    Team t = teamManager.getTeamByName(claims.getString(idx));
                    if (t != null && i >= 0 && i < taskOrder.size()) bingoBoard.put(taskOrder.get(i), t);
                } catch (NumberFormatException ignored) {}
            }
        }
        completedLines.addAll(in.getStringList("lines"));
        for (String n : in.getStringList("winners")) {
            Team t = teamManager.getTeamByName(n);
            if (t != null) bingoWinnersOrder.add(t);
        }
        bingoActive = true;
        plugin.getLogger().info("Restored running bingo after reload.");
    }

    /** Ends bingo and pays 1st-5th place by most squares claimed. */
    public void stopBingo() {
        stopBingo(true);
    }

    /** @param award false when the server is shutting down or the tournament is being reset. */
    public void stopBingo(boolean award) {
        if (!bingoActive) return;
        bingoActive = false;

        if (award) {
            Map<Team, Integer> counts = new HashMap<>();
            for (Team t : bingoBoard.values()) {
                if (t != null) counts.merge(t, 1, Integer::sum);
            }
            List<String> lines = awardPlaces(counts, bingoRewards(), "squares");
            if (lines.isEmpty()) {
                Bukkit.broadcastMessage("§c[BINGO] Bingo ended with no squares claimed - no rewards.");
            } else {
                Bukkit.broadcastMessage("§6§l[BINGO FINAL STANDINGS] §eMost squares claimed:");
                for (String line : lines) Bukkit.broadcastMessage(line);
                Team winner = topTeam(counts);
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendTitle("§6§lBINGO WINNER", winner.getColorCode() + winner.getName() + " §7(+" + bingoRewards()[0] + " Credits)", 10, 80, 20);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
            }
        }
        Bukkit.broadcastMessage("§c[BINGO] Lockout Bingo event has ended.");
    }

    private int[] bingoRewards() {
        return new int[]{
                plugin.getConfig().getInt("rewards.bingo-rewards.1st", 150),
                plugin.getConfig().getInt("rewards.bingo-rewards.2nd", 120),
                plugin.getConfig().getInt("rewards.bingo-rewards.3rd", 100),
                plugin.getConfig().getInt("rewards.bingo-rewards.4th", 50),
                plugin.getConfig().getInt("rewards.bingo-rewards.5th", 25)
        };
    }

    public boolean isBingoActive() {
        return bingoActive;
    }

    public boolean claimBingo(Player player, String taskName) {
        if (!bingoActive || player == null) return false;
        Team team = teamManager.getTeamByPlayer(player.getUniqueId());
        if (team == null) return false;

        for (Map.Entry<String, Team> entry : bingoBoard.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(taskName) && entry.getValue() == null) {
                entry.setValue(team);

                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendTitle(team.getColorCode() + team.getName() + " §fClaimed Task!", "§e" + entry.getKey() + " §7(Locked Out)", 5, 50, 15);
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
                }

                Bukkit.broadcastMessage("§6[BINGO] " + team.getColorCode() + team.getName() + " §ecompleted: §f" + entry.getKey() + "§e!");

                checkBingoLines(team, player);
                return true;
            }
        }
        return false;
    }

    private void checkBingoLines(Team team, Player player) {
        int[][] lines = {
                // Rows
                {0, 1, 2, 3, 4},
                {5, 6, 7, 8, 9},
                {10, 11, 12, 13, 14},
                {15, 16, 17, 18, 19},
                {20, 21, 22, 23, 24},
                // Columns
                {0, 5, 10, 15, 20},
                {1, 6, 11, 16, 21},
                {2, 7, 12, 17, 22},
                {3, 8, 13, 18, 23},
                {4, 9, 14, 19, 24},
                // Diagonals
                {0, 6, 12, 18, 24},
                {4, 8, 12, 16, 20}
        };

        String[] lineNames = {
                "Row 1", "Row 2", "Row 3", "Row 4", "Row 5",
                "Column 1", "Column 2", "Column 3", "Column 4", "Column 5",
                "Diagonal 1", "Diagonal 2"
        };

        for (int i = 0; i < lines.length; i++) {
            String lineKey = team.getName().toLowerCase() + "_" + lineNames[i];
            if (completedLines.contains(lineKey)) continue;

            boolean lineComplete = true;
            for (int idx : lines[i]) {
                if (idx >= taskOrder.size()) {
                    lineComplete = false;
                    break;
                }
                String task = taskOrder.get(idx);
                Team claimer = bingoBoard.get(task);
                if (claimer == null || !claimer.equals(team)) {
                    lineComplete = false;
                    break;
                }
            }

            if (lineComplete) {
                completedLines.add(lineKey);

                if (!bingoWinnersOrder.contains(team)) bingoWinnersOrder.add(team);
                // Lines are just for bragging now - rewards are paid when bingo ends, by most squares.
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendTitle("§6§lBINGO LINE!", team.getColorCode() + team.getName() + " §7completed " + lineNames[i], 10, 60, 20);
                    online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                }
                Bukkit.broadcastMessage("§6§l[BINGO LINE] " + team.getColorCode() + team.getName() + " §ecompleted §f" + lineNames[i] + "§e!");
            }
        }
    }

    private int completedLinesFor(Team team) {
        String prefix = team.getName().toLowerCase() + "_";
        int n = 0;
        for (String key : completedLines) if (key.startsWith(prefix)) n++;
        return n;
    }

    public String getTaskDescription(String task) {
        if (task == null) return "Complete the challenge";
        switch (task) {
            case "Mine Ancient Debris": return "Mine an Ancient Debris block in the Nether";
            case "Tame a Cat": return "Feed raw fish to a stray cat to tame it";
            case "Brew Invisibility": return "Brew and drink an Invisibility potion";
            case "Craft Diamond Block": return "Combine 9 Diamonds in a crafting table";
            case "Kill an Enderman": return "Slay an Enderman in combat";
            case "Eat Golden Apple": return "Consume a Golden or Enchanted Apple";
            case "Obtain Heavy Core": return "Find a Heavy Core in an ominous vault";
            case "Catch a Pufferfish": return "Catch a Pufferfish using a Fishing Rod";
            case "Ride a Strider": return "Saddle and ride a Strider over lava";
            case "Wear Full Iron": return "Equip a full set of Iron Armor";
            case "Smelt Netherite": return "Smelt Ancient Debris into Netherite Scrap";
            case "Tame a Wolf": return "Feed bones to a wild wolf to tame it";
            case "Find a Village": return "Locate a village and interact with a villager";
            case "Bake a Cake": return "Craft a Cake (wheat, sugar, milk, egg)";
            case "Craft Enchanting Table": return "Craft an Enchanting Table with obsidian & diamonds";
            case "Obtain Wither Skull": return "Slay a Wither Skeleton and loot its skull";
            case "Brew Strength II": return "Brew and drink a Strength II potion";
            case "Kill a Warden": return "Summon and defeat a Warden in the Deep Dark";
            case "Craft Beacon": return "Craft a Beacon using a Nether Star";
            case "Shear a Sheep": return "Use Shears to collect wool from a sheep";
            case "Find Desert Temple": return "Trigger the pressure plate in a Desert Pyramid";
            case "Kill Blazes": return "Slay a Blaze in a Nether Fortress";
            case "Mine 64 Coal": return "Gather a full stack of 64 Coal";
            case "Catch Enchanted Book": return "Reel in an Enchanted Book while fishing";
            case "75-Block Water Clutch": return "Survive a 75+ block fall by placing a water bucket before landing";
            default: return "Complete the survival challenge";
        }
    }

    public void openBingoGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§6§lLockout Bingo Board");

        // Fill background with border panes
        ItemStack outerFiller = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta ofMeta = outerFiller.getItemMeta();
        if (ofMeta != null) {
            ofMeta.setDisplayName(" ");
            outerFiller.setItemMeta(ofMeta);
        }

        ItemStack innerBorder = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta ibMeta = innerBorder.getItemMeta();
        if (ibMeta != null) {
            ibMeta.setDisplayName(" ");
            innerBorder.setItemMeta(ibMeta);
        }

        for (int i = 0; i < 54; i++) {
            inv.setItem(i, outerFiller);
        }

        // Left 2 columns and right 2 columns use innerBorder for dark contrast
        int[] sideSlots = {
                0, 1, 9, 10, 18, 19, 27, 28, 36, 37, 45, 46,
                7, 8, 16, 17, 25, 26, 34, 35, 43, 44, 52, 53
        };
        for (int s : sideSlots) {
            inv.setItem(s, innerBorder);
        }

        // Top Header (Slot 4)
        ItemStack header = new ItemStack(Material.NETHER_STAR);
        ItemMeta hMeta = header.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName("§6§lLockout Bingo Race");
            hMeta.setLore(Arrays.asList(
                    "§8--------------------------",
                    "§7Status: " + (bingoActive ? "§aActive (Live Race)" : "§cInactive"),
                    "§7Race across 25 survival challenges.",
                    "§7First team to complete a task locks it out!",
                    "§71st: §a+150 §8| §72nd: §a+120 §8| §73rd: §a+100",
                    "§74th: §a+50 §8| §75th: §a+25 Credits",
                    "§8--------------------------"
            ));
            hMeta.addEnchant(Enchantment.UNBREAKING, 1, true);
            hMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            header.setItemMeta(hMeta);
        }
        inv.setItem(4, header);

        // Sidebar Info: Your Team Progress (Slot 17)
        Team playerTeam = player != null ? teamManager.getTeamByPlayer(player.getUniqueId()) : null;
        int teamClaimedCount = 0;
        if (playerTeam != null) {
            for (Team t : bingoBoard.values()) {
                if (t != null && t.equals(playerTeam)) teamClaimedCount++;
            }
        }
        ItemStack teamInfo = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta tiMeta = teamInfo.getItemMeta();
        if (tiMeta != null) {
            tiMeta.setDisplayName("§e§lYour Team Progress");
            List<String> tiLore = new ArrayList<>();
            tiLore.add("§8--------------------------");
            tiLore.add("§7Team: " + (playerTeam != null ? playerTeam.getColorCode() + playerTeam.getName() : "§cNo Team"));
            tiLore.add("§7Tasks Claimed: §e" + teamClaimedCount + " §7/ 25");
            if (playerTeam != null && bingoWinnersOrder.contains(playerTeam)) {
                int place = bingoWinnersOrder.indexOf(playerTeam) + 1;
                tiLore.add("§7Lines completed: §6§l" + completedLinesFor(playerTeam));
            }
            tiLore.add("§8--------------------------");
            tiLore.add("§7Most squares when bingo ends wins!");
            tiMeta.setLore(tiLore);
            teamInfo.setItemMeta(tiMeta);
        }
        inv.setItem(17, teamInfo);

        // Sidebar Info: Leaderboard (Slot 26)
        ItemStack boardStats = new ItemStack(Material.FILLED_MAP);
        ItemMeta bsMeta = boardStats.getItemMeta();
        if (bsMeta != null) {
            bsMeta.setDisplayName("§6§lLive Standings & Podium");
            List<String> bsLore = new ArrayList<>();
            bsLore.add("§8--------------------------");
            if (!bingoWinnersOrder.isEmpty()) {
                bsLore.add("§a§lFirst to complete a line:");
                String[] places = {"1st", "2nd", "3rd", "4th", "5th"};
                for (int i = 0; i < bingoWinnersOrder.size() && i < places.length; i++) {
                    Team t = bingoWinnersOrder.get(i);
                    bsLore.add(" §6" + places[i] + ": " + t.getColorCode() + t.getName());
                }
                bsLore.add("§8--------------------------");
            }
            Map<Team, Integer> counts = new HashMap<>();
            for (Team t : bingoBoard.values()) {
                if (t != null) counts.put(t, counts.getOrDefault(t, 0) + 1);
            }
            if (counts.isEmpty()) {
                bsLore.add("§7No tasks claimed yet!");
            } else {
                bsLore.add("§eTasks Claimed:");
                List<Map.Entry<Team, Integer>> sorted = new ArrayList<>(counts.entrySet());
                sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
                for (Map.Entry<Team, Integer> entry : sorted) {
                    bsLore.add(" " + entry.getKey().getColorCode() + entry.getKey().getName() + "§7: §e" + entry.getValue() + " tasks");
                }
            }
            bsLore.add("§8--------------------------");
            bsMeta.setLore(bsLore);
            boardStats.setItemMeta(bsMeta);
        }
        inv.setItem(26, boardStats);

        // Sidebar Info: Line Rewards (Slot 35)
        ItemStack rewardInfo = new ItemStack(Material.GOLD_INGOT);
        ItemMeta riMeta = rewardInfo.getItemMeta();
        if (riMeta != null) {
            riMeta.setDisplayName("§a§lBingo Placement Rewards");
            riMeta.setLore(Arrays.asList(
                    "§8--------------------------",
                    "§e1st Place: §a+" + bingoRewards()[0] + " Credits",
                    "§e2nd Place: §a+" + bingoRewards()[1] + " Credits",
                    "§e3rd Place: §a+" + bingoRewards()[2] + " Credits",
                    "§e4th Place: §a+" + bingoRewards()[3] + " Credits",
                    "§e5th Place: §a+" + bingoRewards()[4] + " Credits",
                    "§8--------------------------",
                    "§7Paid to the Captain when bingo",
                    "§7ends: most squares claimed wins!",
                    "§8--------------------------"
            ));
            rewardInfo.setItemMeta(riMeta);
        }
        inv.setItem(35, rewardInfo);

        // Close Menu Button (Slot 53)
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = closeBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lClose Board");
            cMeta.setLore(Collections.singletonList("§7Click to exit."));
            closeBtn.setItemMeta(cMeta);
        }
        inv.setItem(53, closeBtn);

        // 5x5 Centered Board (Columns 2, 3, 4, 5, 6)
        int[] centeredSlots = {
                11, 12, 13, 14, 15,
                20, 21, 22, 23, 24,
                29, 30, 31, 32, 33,
                38, 39, 40, 41, 42,
                47, 48, 49, 50, 51
        };

        int index = 0;
        for (Map.Entry<String, Team> entry : bingoBoard.entrySet()) {
            if (index >= centeredSlots.length) break;
            int slot = centeredSlots[index++];

            String task = entry.getKey();
            Team claimer = entry.getValue();

            ItemStack item;
            if (claimer != null) {
                item = new ItemStack(claimer.getWoolColor());
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(claimer.getColorCode() + "§l" + task);
                    List<String> lore = new ArrayList<>();
                    lore.add("§8--------------------------");
                    lore.add("§c✖ LOCKED OUT");
                    lore.add("§7Claimed by: " + claimer.getColorCode() + claimer.getName());
                    lore.add("§7Objective: §8" + getTaskDescription(task));
                    lore.add("§8--------------------------");
                    meta.setLore(lore);
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);
                }
            } else {
                Material icon = bingoIcons.getOrDefault(task, Material.PAPER);
                item = new ItemStack(icon);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§e§l" + task);
                    List<String> lore = new ArrayList<>();
                    lore.add("§8--------------------------");
                    lore.add("§7Objective: §f" + getTaskDescription(task));
                    lore.add("§7Status: §a✔ Available to claim!");
                    lore.add("§8--------------------------");
                    lore.add("§eFirst team to complete this locks it out!");
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
            inv.setItem(slot, item);
        }

        player.openInventory(inv);
    }

    public Map<String, Team> getBingoBoard() { return bingoBoard; }
}
