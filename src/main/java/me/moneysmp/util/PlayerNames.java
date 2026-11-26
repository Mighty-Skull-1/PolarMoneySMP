package me.moneysmp.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers player names by UUID, saved in data.yml under "names".
 *
 * The server only knows the name of someone who has joined before. Players drafted (or tiered, or subbed in)
 * before they ever join would otherwise show up as a UUID, so we keep their name here. When a player joins,
 * their current name replaces the saved one (handles name changes).
 */
public final class PlayerNames {
    private static final Map<UUID, String> names = new ConcurrentHashMap<>();

    private PlayerNames() {}

    /** Saves (or updates) the name for this UUID. Returns true if it changed. */
    public static boolean remember(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return false;
        String old = names.put(uuid, name);
        return !name.equals(old);
    }

    /** Best known name: online player, then saved name, then the server's own cache. Null if unknown. */
    public static String get(UUID uuid) {
        if (uuid == null) return null;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        String saved = names.get(uuid);
        if (saved != null) return saved;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName();
    }

    /** Like {@link #get} but never null: falls back to the first 8 characters of the UUID. */
    public static String getOrShort(UUID uuid) {
        String n = get(uuid);
        return n != null ? n : (uuid != null ? uuid.toString().substring(0, 8) : "?");
    }

    /** UUID saved for this name (any capitalisation), or null. */
    public static UUID findSaved(String name) {
        if (name == null) return null;
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            if (e.getValue().equalsIgnoreCase(name)) return e.getKey();
        }
        return null;
    }

    public static Map<UUID, String> all() { return Collections.unmodifiableMap(names); }
}
