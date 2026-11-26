package me.moneysmp.managers;

import me.moneysmp.MoneySMP;
import me.moneysmp.models.SMPPhase;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SMPGuiManager {
    public static final String GUI_TITLE = "§6§lMoney SMP Control Panel";

    private final JavaPlugin plugin;
    private final PhaseManager phaseManager;
    private final EventManager eventManager;
    private final TeamManager teamManager;
    private final EliminationManager eliminationManager;
    private final DataManager dataManager;
    private final BannedItemManager bannedItemManager;

    public SMPGuiManager(JavaPlugin plugin, PhaseManager phaseManager, EventManager eventManager,
                         TeamManager teamManager, EliminationManager eliminationManager, DataManager dataManager,
                         BannedItemManager bannedItemManager) {
        this.plugin = plugin;
        this.phaseManager = phaseManager;
        this.eventManager = eventManager;
        this.teamManager = teamManager;
        this.eliminationManager = eliminationManager;
        this.dataManager = dataManager;
        this.bannedItemManager = bannedItemManager;
    }

    public void openControlPanel(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Fill background
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null, false);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, filler);
        }

        SMPPhase currentPhase = phaseManager.getCurrentPhase();

        // Slot 10: Draft Phase
        inv.setItem(10, createPhaseItem(
                Material.GOLD_INGOT,
                "§e§lPhase: Draft",
                "§7Captains bid on players using draft credits.",
                currentPhase == SMPPhase.DRAFT
        ));

        // Slot 11: Grace Period
        inv.setItem(11, createPhaseItem(
                Material.SHIELD,
                "§a§lPhase: Grace Period",
                "§7PvP is disabled. Gather gear and build bases.",
                currentPhase == SMPPhase.GRACE
        ));

        // Slot 12: PvP Phase
        inv.setItem(12, createPhaseItem(
                Material.DIAMOND_SWORD,
                "§c§lPhase: PvP Enabled",
                "§7Combat enabled & eliminations active!",
                currentPhase == SMPPhase.PVP
        ));

        // Slot 13: Finale Showdown
        inv.setItem(13, createPhaseItem(
                Material.NETHERITE_SWORD,
                "§4§lPhase: Finale Showdown",
                "§7Final battle for victory with permadeath!",
                currentPhase == SMPPhase.FINALE
        ));

        // Slot 14: Banned Items Menu
        List<String> bannedLore = Arrays.asList(
                "§8-------------------------",
                "§7View and manage banned tournament items.",
                "§7Ender Pearls & restricted gear are locked.",
                "§7Strict combat balance enforced!",
                "§8-------------------------",
                "§c▶ Click: §fOpen Banned Items GUI"
        );
        inv.setItem(14, createItem(Material.BARRIER, "§c§lBanned Items List", bannedLore, true));

        // Slot 16: Tournament Stats / Overview
        boolean kothOn = plugin instanceof me.moneysmp.MoneySMP smpCp && smpCp.getCapturePointManager() != null && smpCp.getCapturePointManager().isRunning();
        boolean bingoOn = eventManager != null && eventManager.isBingoActive();
        int teamCount = teamManager != null ? teamManager.getTeams().size() : 0;
        int elimCount = eliminationManager != null ? eliminationManager.getEliminatedPlayers().size() : 0;

        List<String> statsLore = Arrays.asList(
                "§8-------------------------",
                "§7Current Phase: " + currentPhase.getDisplayName(),
                "§7Total Teams: §e" + teamCount,
                "§7Online Players: §a" + Bukkit.getOnlinePlayers().size(),
                "§7Eliminated Players: §c" + elimCount,
                "§7Capture Points: " + (kothOn ? "§aOpen" : "§7Closed"),
                "§7Bingo Event: " + (bingoOn ? "§aActive" : "§7Idle"),
                "§8-------------------------",
                "§eClick to refresh overview"
        );
        inv.setItem(16, createItem(Material.NETHER_STAR, "§6§lTournament Status", statsLore, true));

        // Slot 18: Capture Points (Season 2 - replaces KOTH and airdrops)
        List<String> cpLore = Arrays.asList(
                "§8-------------------------",
                "§7Status: " + (kothOn ? "§aPoints open" : "§cClosed"),
                "§7Opens 5 random saved points, 2 at a time.",
                "§7Save points with §e/cp set <name>",
                "§8-------------------------",
                "§e▶ Left-Click: §aStart today's capture points",
                "§c▶ Right-Click: §cClose all points"
        );
        inv.setItem(18, createItem(Material.WHITE_BANNER, "§b§lCapture Points", cpLore, kothOn));

        // Slot 22: Lockout Bingo
        List<String> bingoLore = Arrays.asList(
                "§8-------------------------",
                "§7Status: " + (bingoOn ? "§aActive (Running)" : "§cInactive"),
                "§7Interactive 5x5 Lockout Bingo challenge.",
                "§8-------------------------",
                "§e▶ Left-Click: §aStart Bingo Event",
                "§c▶ Right-Click: §cStop Bingo Event"
        );
        inv.setItem(22, createItem(Material.FILLED_MAP, "§d§lLockout Bingo", bingoLore, bingoOn));

        // Slot 23: Save Tournament Data
        List<String> saveLore = Arrays.asList(
                "§8-------------------------",
                "§7Forces immediate save of all",
                "§7teams, balances, and states to data.yml.",
                "§8-------------------------",
                "§a▶ Click to save data now"
        );
        inv.setItem(23, createItem(Material.ENDER_EYE, "§a§lSave Tournament Data", saveLore, false));

        // Slot 24: Reload Configuration
        List<String> reloadLore = Arrays.asList(
                "§8-------------------------",
                "§7Reloads config.yml",
                "§7without restarting the server.",
                "§8-------------------------",
                "§e▶ Click to reload configs"
        );
        inv.setItem(24, createItem(Material.REDSTONE_TORCH, "§c§lReload Configs", reloadLore, false));

        // Slot 25: RESET TOURNAMENT
        List<String> resetLore = Arrays.asList(
                "§8-------------------------",
                "§c§lDANGER: FULL SYSTEM RESET",
                "§7• Restores all players to Survival at spawn",
                "§7• Clears eliminations & revives spectators",
                "§7• Resets draft & game credits to starting values",
                "§7• Disbands all teams & clears player tiers",
                "§7• Stops Capture Points, Bingo & Draft events",
                "§7• Wipes and creates fresh data.yml",
                "§8-------------------------",
                "§c▶ Shift + Click: §4§lCONFIRM FULL RESET",
                "§7(Or run command: /smp reset confirm)"
        );
        inv.setItem(25, createItem(Material.TNT, "§c§lRESET TOURNAMENT", resetLore, true));

        // Slot 26: Close Menu
        List<String> closeLore = Collections.singletonList("§7Click to exit control panel.");
        inv.setItem(26, createItem(Material.BARRIER, "§c§lClose Menu", closeLore, false));

        player.openInventory(inv);
    }

    private ItemStack createPhaseItem(Material material, String name, String description, boolean active) {
        List<String> lore = new ArrayList<>();
        lore.add("§8-------------------------");
        if (active) {
            lore.add("§a✔ CURRENTLY ACTIVE");
        } else {
            lore.add("§7Status: §7Inactive");
        }
        lore.add(description);
        lore.add("§8-------------------------");
        if (!active) {
            lore.add("§e▶ Click to switch to this phase");
        }
        return createItem(material, name, lore, active);
    }

    private ItemStack createItem(Material material, String name, List<String> lore, boolean glowing) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            if (glowing) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public void handleClick(Player player, int rawSlot, ClickType clickType) {
        if (player == null || rawSlot < 0 || rawSlot >= 27) return;

        switch (rawSlot) {
            case 10: // Draft Phase
                phaseManager.setPhase(SMPPhase.DRAFT);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                openControlPanel(player);
                break;

            case 11: // Grace Period
                phaseManager.setPhase(SMPPhase.GRACE);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                openControlPanel(player);
                break;

            case 12: // PvP Phase
                phaseManager.setPhase(SMPPhase.PVP);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                openControlPanel(player);
                break;

            case 13: // Finale Showdown
                phaseManager.setPhase(SMPPhase.FINALE);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                openControlPanel(player);
                break;

            case 14: // Banned Items Menu
                if (bannedItemManager != null) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                    bannedItemManager.openBannedItemsGUI(player);
                }
                break;

            case 16: // Status Overview Refresh
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
                openControlPanel(player);
                break;

            case 18: // Capture Points
                if (plugin instanceof me.moneysmp.MoneySMP smpCp && smpCp.getCapturePointManager() != null) {
                    if (clickType.isRightClick()) {
                        smpCp.getCapturePointManager().stopDay(true);
                    } else {
                        String err = smpCp.getCapturePointManager().startDay();
                        if (err != null) player.sendMessage("§c" + err);
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                    openControlPanel(player);
                }
                break;

            case 22: // Lockout Bingo
                if (eventManager != null) {
                    if (clickType.isRightClick()) {
                        if (eventManager.isBingoActive()) {
                            eventManager.stopBingo();
                            player.sendMessage("§c[BINGO] Stopped Lockout Bingo!");
                        } else {
                            player.sendMessage("§c[BINGO] Lockout Bingo is not active!");
                        }
                    } else {
                        eventManager.startBingo();
                        player.sendMessage("§a[BINGO] Started Lockout Bingo!");
                    }
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                    openControlPanel(player);
                }
                break;

            case 23: // Force Save Data
                if (dataManager != null) {
                    dataManager.saveData();
                    player.sendMessage("§a[Polar Money SMP] All tournament data successfully saved to data.yml!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                }
                break;

            case 24: // Reload Configs (Owners only)
                if (!player.hasPermission(RankManager.OWNER_PERMISSION)) {
                    player.sendMessage("§cOnly Owners can reload the config.");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    break;
                }
                if (plugin != null) {
                    plugin.reloadConfig();
                }
                player.sendMessage("§a[Polar Money SMP] Configuration reloaded successfully!");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                break;

            case 25: // Reset Tournament (Owners only)
                if (!player.hasPermission(RankManager.OWNER_PERMISSION)) {
                    player.sendMessage("§cOnly Owners can reset the tournament.");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    break;
                }
                if (clickType.isShiftClick()) {
                    if (plugin instanceof MoneySMP smp) {
                        player.closeInventory();
                        smp.resetTournament(player);
                    }
                } else {
                    player.sendMessage("§c§l[RESET WARNING] §eShift-Click this button or type §f/smp reset confirm §eto completely reset the tournament!");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                }
                break;

            case 26: // Close Menu
                player.closeInventory();
                break;
        }
    }
}
