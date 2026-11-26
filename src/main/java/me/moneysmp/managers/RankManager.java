package me.moneysmp.managers;

import me.moneysmp.models.Rank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owner / Admin ranks by UUID (saved in data.yml under "ranks", works for offline players).
 *
 * Ranks are turned into permissions while the player is online:
 *  - Admin: moneysmp.admin  -> events, draft, fines, subs, teams, tiers, credits...
 *  - Owner: moneysmp.admin + moneysmp.owner -> everything, including reset / reload / config changes.
 * Ops already have both permissions by default. /rank itself is ops and console only.
 */
public class RankManager {
    public static final String ADMIN_PERMISSION = "moneysmp.admin";
    public static final String OWNER_PERMISSION = "moneysmp.owner";

    private final JavaPlugin plugin;
    private final Map<UUID, Rank> ranks = new HashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public RankManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Rank getRank(UUID uuid) { return ranks.getOrDefault(uuid, Rank.NONE); }
    public Map<UUID, Rank> getAllRanks() { return Collections.unmodifiableMap(ranks); }

    /** Sets the rank (NONE removes it) and updates the player's permissions if they're online. */
    public void setRank(UUID uuid, Rank rank) {
        if (uuid == null) return;
        if (rank == null || rank == Rank.NONE) ranks.remove(uuid);
        else ranks.put(uuid, rank);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) apply(online);
    }

    /** Used when loading data.yml (players aren't online yet). */
    public void loadRank(UUID uuid, Rank rank) {
        if (uuid != null && rank != null && rank != Rank.NONE) ranks.put(uuid, rank);
    }

    /** Gives the online player the permissions for their rank (call on join, on rank change, on enable). */
    public void apply(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try { player.removeAttachment(old); } catch (IllegalArgumentException ignored) {}
        }
        Rank rank = getRank(player.getUniqueId());
        if (rank != Rank.NONE) {
            PermissionAttachment att = player.addAttachment(plugin);
            att.setPermission(ADMIN_PERMISSION, true);
            if (rank == Rank.OWNER) att.setPermission(OWNER_PERMISSION, true);
            attachments.put(player.getUniqueId(), att);
        }
        player.recalculatePermissions();
        try { player.updateCommands(); } catch (Throwable ignored) {} // refresh tab-complete for commands
    }

    /** Forgets the attachment when the player leaves (Bukkit drops it on quit anyway). */
    public void forget(Player player) {
        attachments.remove(player.getUniqueId());
    }

    /** Removes every attachment (plugin disable / reload). */
    public void removeAll() {
        for (Map.Entry<UUID, PermissionAttachment> e : attachments.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) {
                try { p.removeAttachment(e.getValue()); } catch (IllegalArgumentException ignored) {}
            }
        }
        attachments.clear();
    }
}
