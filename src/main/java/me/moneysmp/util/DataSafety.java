package me.moneysmp.util;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps the saved files safe across updates (see "Updating safely" in the README).
 *
 *  - When the plugin version changes, every data file is copied to
 *    backups/update_&lt;date&gt;_from-&lt;old&gt;_to-&lt;new&gt;/ before anything is loaded.
 *  - A file that can't be read is marked unreadable: it is never saved over, and admins are warned
 *    in the console and in chat (now, and every time an admin joins) until it's fixed and the server restarts.
 *  - {@link #saveAtomically} writes to a temp file and swaps it in, so a crash can't leave half a file.
 */
public final class DataSafety implements Listener {
    /** Every file that holds tournament data or settings. All of them are backed up on an update. */
    public static final List<String> DATA_FILES = List.of(
            "data.yml", "fines.yml", "events-state.yml", "banned-items.yml", "config.yml");
    private static final String VERSION_FILE = "last-version.txt";

    /** file name -> why it couldn't be read */
    private static final Map<String, String> unreadable = Collections.synchronizedMap(new LinkedHashMap<>());

    private DataSafety() {}

    /**
     * Call first thing in onEnable, before any file is loaded or changed.
     * If this is a different version from last time, copies all data files into a dated backup folder.
     */
    public static void backupIfNewVersion(JavaPlugin plugin) {
        File folder = plugin.getDataFolder();
        String now = plugin.getPluginMeta().getVersion();
        File versionFile = new File(folder, VERSION_FILE);
        String last = null;
        try {
            if (versionFile.exists()) last = Files.readString(versionFile.toPath(), StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {}
        if (now.equals(last)) return;

        boolean anyData = false;
        for (String name : DATA_FILES) if (new File(folder, name).exists()) anyData = true;
        if (anyData) {
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String from = (last == null || last.isEmpty()) ? "unknown" : last;
            File dir = new File(new File(folder, "backups"), "update_" + stamp + "_from-" + from + "_to-" + now);
            if (!dir.mkdirs() && !dir.isDirectory()) {
                plugin.getLogger().severe("Couldn't create backup folder " + dir.getPath() + " - will try again next start.");
                return; // don't record the new version, so the backup is retried
            }
            for (String name : DATA_FILES) {
                File f = new File(folder, name);
                if (!f.exists()) continue;
                try {
                    Files.copy(f.toPath(), new File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    plugin.getLogger().severe("Couldn't back up " + name + " before the update: " + e.getMessage()
                            + " - will try again next start.");
                    return;
                }
            }
            plugin.getLogger().info("New version " + now + " (was " + from + "): backed up your data to backups/" + dir.getName());
        }
        try {
            if (!folder.exists()) folder.mkdirs();
            Files.writeString(versionFile.toPath(), now + System.lineSeparator(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Couldn't write " + VERSION_FILE + ": " + e.getMessage());
        }
    }

    /**
     * Reads a YAML file. Returns null (and marks the file unreadable) if it exists but can't be read.
     * A missing file gives an empty config.
     */
    public static YamlConfiguration load(JavaPlugin plugin, File file) {
        YamlConfiguration y = new YamlConfiguration();
        if (!file.exists()) return y;
        try {
            y.load(file);
            return y;
        } catch (Exception e) {
            markUnreadable(plugin, file.getName(), e.getMessage());
            return null;
        }
    }

    /** Records that a file couldn't be read, warns the console and every online admin. */
    public static void markUnreadable(JavaPlugin plugin, String fileName, String error) {
        boolean first = unreadable.put(fileName, String.valueOf(error)) == null;
        if (!first) return;
        plugin.getLogger().severe("==============================================================");
        plugin.getLogger().severe(fileName + " could not be read: " + error);
        plugin.getLogger().severe("It will NOT be saved over, so nothing in it is lost. Saving to it is stopped.");
        plugin.getLogger().severe("Fix the file (or copy one back from the backups folder) and restart the server.");
        plugin.getLogger().severe("==============================================================");
        Runnable tell = () -> {
            for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("moneysmp.admin")) warn(p, fileName);
        };
        if (Bukkit.isPrimaryThread()) tell.run(); else Bukkit.getScheduler().runTask(plugin, tell);
    }

    public static boolean isUnreadable(String fileName) { return unreadable.containsKey(fileName); }

    /** Saves {@code y} to {@code file} via a temp file. Refuses (returns false) if the file was unreadable. */
    public static boolean saveAtomically(JavaPlugin plugin, org.bukkit.configuration.file.FileConfiguration y, File file) {
        if (isUnreadable(file.getName())) {
            plugin.getLogger().warning("Not saving " + file.getName() + ": it couldn't be read at startup, so saving would overwrite it.");
            return false;
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            y.save(tmp);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private static void warn(Player p, String fileName) {
        p.sendMessage("§4§l[Polar Money SMP] §c" + fileName + " couldn't be read, so the plugin is NOT saving to it "
                + "(nothing in it is lost). Check the console, fix the file or restore it from the backups folder, then restart.");
    }

    /** Listener: reminds admins about unreadable files when they join. */
    public static Listener joinWarnings() { return new DataSafety(); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (unreadable.isEmpty() || !p.hasPermission("moneysmp.admin")) return;
        synchronized (unreadable) {
            for (String name : unreadable.keySet()) warn(p, name);
        }
    }
}
