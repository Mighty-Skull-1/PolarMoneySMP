package me.moneysmp.managers;

import me.moneysmp.models.PlayerTier;
import me.moneysmp.models.Team;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Season 2 draft: no timer. When a player is nominated, every captain gets bid items in their hotbar
 * (+5 / +10 / +25) and the admin who nominated gets SOLD / CANCEL items. The auction ends only when an
 * admin clicks SOLD (or types /bid sold).
 */
public class DraftManager {
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final CreditManager creditManager;
    private final TierManager tierManager;
    private final NamespacedKey itemKey;

    private UUID nomineeUuid = null;
    private String nomineeName = null;
    private UUID highestBidderId = null;
    private String highestBidderName = null;
    private int highestBid = 0;
    private int taskId = -1;
    private int bidIncrement = 5;
    private UUID auctioneer = null;

    /** Hotbar slots we borrowed, so the player's own items come back afterwards. */
    private final Map<UUID, Map<Integer, ItemStack>> borrowed = new HashMap<>();

    private final Deque<String[]> recentPicks = new ArrayDeque<>();
    private static final int MAX_RECENT_PICKS = 12;
    private Runnable onChange = null;

    public DraftManager(JavaPlugin plugin, TeamManager teamManager, CreditManager creditManager, TierManager tierManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.creditManager = creditManager;
        this.tierManager = tierManager;
        this.itemKey = new NamespacedKey(plugin, "draft_item");
    }

    public boolean isDraftActive() { return nomineeUuid != null; }
    public UUID getCurrentNomineeUuid() { return nomineeUuid; }
    public String getCurrentNomineeName() { return nomineeName; }
    public Player getCurrentNominee() { return nomineeUuid != null ? Bukkit.getPlayer(nomineeUuid) : null; }
    public int getHighestBid() { return highestBid; }
    public String getHighestBidderName() { return highestBidderName; }
    public String getHighestBidderTeamName() {
        if (highestBidderId == null) return null;
        Team team = teamManager.getTeamByPlayer(highestBidderId);
        return team != null ? team.getName() : null;
    }
    public String getNomineeTierName() { return nomineeUuid != null ? tierManager.getTier(nomineeUuid).name() : null; }
    /** No timer in Season 2 - always 0 (the website shows "waiting for the auctioneer"). */
    public long getEndsAtMillis() { return 0L; }
    public int getAuctionSeconds() { return 0; }
    public int getBidIncrement() { return bidIncrement; }
    public int getNextBid() { return highestBid + bidIncrement; }
    public boolean isHighestBidder(Player player) { return player != null && player.getUniqueId().equals(highestBidderId); }
    public NamespacedKey getItemKey() { return itemKey; }
    public List<String[]> getRecentPicks() { return new ArrayList<>(recentPicks); }
    public void clearHistory() { recentPicks.clear(); notifyChange(); }
    public void setOnChange(Runnable onChange) { this.onChange = onChange; }

    private void notifyChange() {
        if (onChange != null) try { onChange.run(); } catch (Exception ignored) {}
    }

    private List<Integer> bidSteps() {
        List<Integer> steps = plugin.getConfig().getIntegerList("draft.bid-buttons");
        if (steps.isEmpty()) steps = List.of(5, 10, 25);
        return steps.size() > 3 ? steps.subList(0, 3) : steps;
    }

    // ---------------------------------------------------------------- auction flow
    public boolean startNomination(Player target, Player admin) {
        if (isDraftActive() || target == null) return false;
        nomineeUuid = target.getUniqueId();
        nomineeName = target.getName();
        highestBidderId = null;
        highestBidderName = null;
        highestBid = 0;
        bidIncrement = Math.max(1, plugin.getConfig().getInt("draft.bid-increment", 5));
        auctioneer = admin != null ? admin.getUniqueId() : null;

        PlayerTier tier = tierManager.getTier(target.getUniqueId());
        Bukkit.broadcastMessage("§6§l[DRAFT] §e" + nomineeName + " §7(" + tier.getPrefix() + "§7) is up for auction!");
        Bukkit.broadcastMessage("§7Captains: §eright-click the bid items in your hotbar §7to bid. No timer - the auctioneer calls SOLD.");

        for (Player p : Bukkit.getOnlinePlayers()) giveItemsIfNeeded(p);

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            String bar = "§6Bidding: §e" + nomineeName + " §8| §aTop bid: §f"
                    + (highestBidderName != null ? highestBidderName + " (" + highestBid + ")" : "none yet")
                    + " §8| §7Waiting for SOLD";
            for (Player online : Bukkit.getOnlinePlayers())
                online.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(bar));
        }, 0L, 20L);

        notifyChange();
        return true;
    }

    /** Tries a bid; returns null on success or the reason it failed. Used by the items and /bid. */
    public String tryBid(Player captain, int amount) {
        if (!isDraftActive()) return "There is no auction running.";
        Team team = teamManager.getTeamByPlayer(captain.getUniqueId());
        if (team == null || !team.isCaptain(captain.getUniqueId())) return "Only team captains can bid.";
        if (isHighestBidder(captain)) return "You're already the top bidder at " + highestBid + ".";
        if (amount <= highestBid) return "Bids must be higher than " + highestBid + ".";
        PlayerTier nomineeTier = tierManager.getTier(nomineeUuid);
        for (UUID memberId : team.getMembers()) {
            if (!memberId.equals(team.getCaptain()) && tierManager.getTier(memberId) == nomineeTier)
                return "Your team already has a Tier " + nomineeTier.name() + " player.";
        }
        int purse = creditManager.getDraftCredits(captain.getUniqueId());
        if (purse < amount) return "Not enough draft credits (you have " + purse + ").";

        highestBidderId = captain.getUniqueId();
        highestBidderName = captain.getName();
        highestBid = amount;
        Bukkit.broadcastMessage("§6[Draft] " + team.getColorCode() + captain.getName() + " §fbid §a" + amount + " §ffor " + nomineeName + "!");
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        notifyChange();
        return null;
    }

    /** Ends the auction: highest bidder gets the player. Admin-only (SOLD item or /bid sold). */
    public void sell() {
        if (!isDraftActive()) return;
        stopTask();
        if (highestBidderId != null) {
            Team team = teamManager.getTeamByPlayer(highestBidderId);
            if (team != null) {
                creditManager.removeDraftCredits(highestBidderId, highestBid);
                teamManager.addPlayerToTeam(team, nomineeUuid);
                creditManager.setGameCredits(nomineeUuid, plugin.getConfig().getInt("starting-game-credits", 100));
                Bukkit.broadcastMessage("§6§l[DRAFT SOLD] " + team.getColorCode() + highestBidderName + " §fwon §e" + nomineeName
                        + " §ffor §a" + highestBid + " Draft Credits!");
                for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                recentPicks.addFirst(new String[]{nomineeName, team.getName(), String.valueOf(highestBid)});
                while (recentPicks.size() > MAX_RECENT_PICKS) recentPicks.removeLast();
            } else {
                Bukkit.broadcastMessage("§c[DRAFT ERROR] The winning captain's team was not found - " + nomineeName + " stays unassigned.");
            }
        } else {
            Bukkit.broadcastMessage("§c[DRAFT] No bids for " + nomineeName + " - they stay unassigned.");
        }
        clearAuction();
    }

    public void cancelDraft() {
        boolean was = isDraftActive();
        stopTask();
        if (was) Bukkit.broadcastMessage("§c[DRAFT CANCELLED] The auction for " + nomineeName + " was cancelled.");
        clearAuction();
    }

    private void clearAuction() {
        boolean was = isDraftActive();
        nomineeUuid = null;
        nomineeName = null;
        highestBidderId = null;
        highestBidderName = null;
        highestBid = 0;
        auctioneer = null;
        for (Player p : Bukkit.getOnlinePlayers()) takeItems(p);
        if (was) notifyChange();
    }

    private void stopTask() {
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
    }

    // ---------------------------------------------------------------- items
    /** Gives bid items to captains and SOLD/CANCEL to the auctioneer (also used when they join mid-auction). */
    public void giveItemsIfNeeded(Player p) {
        if (!isDraftActive() || borrowed.containsKey(p.getUniqueId())) return;
        Team team = teamManager.getTeamByPlayer(p.getUniqueId());
        boolean captain = team != null && team.isCaptain(p.getUniqueId());
        boolean auctioneerHere = p.getUniqueId().equals(auctioneer) && !captain;
        if (!captain && !auctioneerHere) return;
        Map<Integer, ItemStack> saved = new HashMap<>();
        PlayerInventory inv = p.getInventory();
        if (captain) {
            List<Integer> steps = bidSteps();
            Material[] mats = {Material.LIME_CONCRETE, Material.YELLOW_CONCRETE, Material.GOLD_BLOCK};
            for (int i = 0; i < steps.size(); i++) {
                int slot = 6 + i;
                saved.put(slot, inv.getItem(slot));
                inv.setItem(slot, tagged(mats[i], "§a§lBID +" + steps.get(i), "bid:" + steps.get(i),
                        "§7Right-click to raise the top bid by §e" + steps.get(i)));
            }
        } else {
            saved.put(7, inv.getItem(7));
            saved.put(8, inv.getItem(8));
            inv.setItem(7, tagged(Material.EMERALD_BLOCK, "§a§lSOLD!", "sold", "§7Right-click to sell to the top bidder"));
            inv.setItem(8, tagged(Material.BARRIER, "§c§lCANCEL AUCTION", "cancel", "§7Right-click to cancel (player stays unassigned)"));
        }
        borrowed.put(p.getUniqueId(), saved);
    }

    /** Removes draft items and puts the player's own items back. */
    public void takeItems(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (draftAction(it) != null) inv.setItem(i, null);
        }
        Map<Integer, ItemStack> saved = borrowed.remove(p.getUniqueId());
        if (saved != null) {
            for (Map.Entry<Integer, ItemStack> e : saved.entrySet()) {
                if (e.getValue() == null) continue;
                if (inv.getItem(e.getKey()) == null) inv.setItem(e.getKey(), e.getValue());
                else inv.addItem(e.getValue()).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
            }
        }
    }

    private ItemStack tagged(Material m, String name, String action, String lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, action);
        it.setItemMeta(meta);
        return it;
    }

    /** "bid:5", "sold", "cancel" or null if this isn't a draft item. */
    public String draftAction(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public void useItem(Player p, String action) {
        if (action.startsWith("bid:")) {
            int step;
            try { step = Integer.parseInt(action.substring(4)); } catch (NumberFormatException e) { return; }
            String err = tryBid(p, highestBid + step);
            if (err != null) p.sendMessage("§c[Draft] " + err);
        } else if (action.equals("sold")) {
            if (p.hasPermission("moneysmp.admin")) sell();
        } else if (action.equals("cancel")) {
            if (p.hasPermission("moneysmp.admin")) cancelDraft();
        }
    }
}
