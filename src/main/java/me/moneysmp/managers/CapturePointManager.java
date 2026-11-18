package me.moneysmp.managers;

import me.moneysmp.models.Team;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Capture Points (Season 2, replaces airdrops).
 *
 * - Up to 8 fixed points are saved with /cp set <name>.
 * - /cp startday picks 5 at random (capture-points.per-day). Only 2 are open at once (max-active);
 *   when one is captured the next one opens, until the day's points are all done.
 * - The team with the MOST players inside the zone makes progress. A tie for most = contested (frozen).
 *   Extra players don't speed it up.
 * - One continuous 12-minute capture inside a 16x16 square zone.
 * - If another team takes the majority, the old team's progress drains (2x speed). Once it hits 0 the new
 *   team starts its own progress from 0. Nobody inside = frozen.
 * - Completing the capture pays capture-points.reward (75) to the team captain and opens the next point.
 */
public class CapturePointManager {
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final CreditManager creditManager;

    private static class Point {
        final String name;
        final Location center;
        String owner = null;       // team name (lower case) making progress
        int progress = 0;          // seconds of progress (control + capture combined)
        boolean contested = false;
        BossBar bar;
        Point(String name, Location center) { this.name = name; this.center = center; }
    }

    private final Map<String, Point> active = new LinkedHashMap<>();
    private final Deque<String> todayQueue = new ArrayDeque<>();
    private int capturedToday = 0;
    private int plannedToday = 0;
    private int taskId = -1;

    public CapturePointManager(JavaPlugin plugin, TeamManager teamManager, CreditManager creditManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.creditManager = creditManager;
    }

    // ---------------------------------------------------------------- settings
    /** Zone is a square: size x size blocks centred on the point (default 16 x 16). */
    private double half() { return Math.max(2, plugin.getConfig().getInt("capture-points.size", 16)) / 2.0; }
    /** One continuous capture timer (default 12 minutes). */
    private int total() { return Math.max(10, plugin.getConfig().getInt("capture-points.capture-seconds", 720)); }
    private int reward() { return plugin.getConfig().getInt("capture-points.reward", 75); }
    private int perDay() { return Math.max(1, plugin.getConfig().getInt("capture-points.per-day", 5)); }
    private int maxActive() { return Math.max(1, plugin.getConfig().getInt("capture-points.max-active", 2)); }
    private int drainSpeed() { return Math.max(1, plugin.getConfig().getInt("capture-points.steal-drain-speed", 2)); }

    public boolean isRunning() { return !active.isEmpty(); }

    // ---------------------------------------------------------------- saved locations
    public Map<String, Location> savedPoints() {
        Map<String, Location> out = new LinkedHashMap<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("capture-points.points");
        if (sec == null) return out;
        for (String key : sec.getKeys(false)) {
            World w = Bukkit.getWorld(sec.getString(key + ".world", "world"));
            if (w == null) continue;
            out.put(key, new Location(w, sec.getDouble(key + ".x"), sec.getDouble(key + ".y"), sec.getDouble(key + ".z")));
        }
        return out;
    }

    public void setPoint(String name, Location loc) {
        String key = "capture-points.points." + name.toLowerCase(Locale.ROOT);
        plugin.getConfig().set(key + ".world", loc.getWorld().getName());
        plugin.getConfig().set(key + ".x", loc.getBlockX() + 0.5);
        plugin.getConfig().set(key + ".y", loc.getBlockY());
        plugin.getConfig().set(key + ".z", loc.getBlockZ() + 0.5);
        plugin.saveConfig();
    }

    public boolean removePoint(String name) {
        String key = "capture-points.points." + name.toLowerCase(Locale.ROOT);
        if (!plugin.getConfig().contains(key)) return false;
        plugin.getConfig().set(key, null);
        plugin.saveConfig();
        return true;
    }

    // ---------------------------------------------------------------- day control
    /** Picks today's points at random and opens the first ones. Returns null or an error. */
    public String startDay() {
        if (!active.isEmpty() || !todayQueue.isEmpty()) return "A capture day is already running. Use /cp stopday first.";
        List<String> names = new ArrayList<>(savedPoints().keySet());
        if (names.isEmpty()) return "No capture points saved yet. Stand on a spot and use /cp set <name>.";
        Collections.shuffle(names);
        int n = Math.min(perDay(), names.size());
        todayQueue.addAll(names.subList(0, n));
        capturedToday = 0;
        plannedToday = n;
        Bukkit.broadcastMessage("§b§l[CAPTURE POINTS] §e" + n + " capture points open today! §7Only " + maxActive()
                + " at a time - capture one and the next unlocks. §a+" + reward() + " credits §7per capture.");
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1.2f);
        fillActive();
        ensureTask();
        return null;
    }

    /** Opens one specific point right now (outside the daily rotation). */
    public String openPoint(String name) {
        Location loc = savedPoints().get(name.toLowerCase(Locale.ROOT));
        if (loc == null) return "No capture point called '" + name + "'. See /cp list.";
        if (active.containsKey(name.toLowerCase(Locale.ROOT))) return "That point is already open.";
        todayQueue.remove(name.toLowerCase(Locale.ROOT));
        open(name.toLowerCase(Locale.ROOT), loc);
        ensureTask();
        return null;
    }

    public void stopDay(boolean announce) {
        for (Point p : active.values()) if (p.bar != null) p.bar.removeAll();
        boolean was = !active.isEmpty() || !todayQueue.isEmpty();
        active.clear();
        todayQueue.clear();
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
        if (announce && was) Bukkit.broadcastMessage("§b§l[CAPTURE POINTS] §cAll capture points have been closed for today.");
    }

    private void fillActive() {
        Map<String, Location> saved = savedPoints();
        while (active.size() < maxActive() && !todayQueue.isEmpty()) {
            String name = todayQueue.poll();
            Location loc = saved.get(name);
            if (loc != null) open(name, loc);
        }
        if (active.isEmpty() && todayQueue.isEmpty() && plannedToday > 0) {
            Bukkit.broadcastMessage("§b§l[CAPTURE POINTS] §eAll " + plannedToday + " capture points for today are done! See you tomorrow.");
            plannedToday = 0;
            if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
        }
    }

    private void open(String name, Location loc) {
        Point p = new Point(name, loc);
        p.bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SEGMENTED_10);
        for (Player pl : Bukkit.getOnlinePlayers()) p.bar.addPlayer(pl);
        active.put(name, p);
        updateBar(p);
        Bukkit.broadcastMessage("§b§l[CAPTURE POINT OPEN] §f" + pretty(name) + " §7at §e" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + " §7- hold the " + (int) (half() * 2) + "x" + (int) (half() * 2) + " zone with the most players for §e" + fmt(total()) + " §7to capture it.");
    }

    private void ensureTask() {
        if (taskId == -1) taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    // ---------------------------------------------------------------- every second
    private void tick() {
        for (Point p : new ArrayList<>(active.values())) {
            tickPoint(p);
            particles(p);
        }
    }

    private void tickPoint(Point p) {
        Map<String, Integer> counts = new HashMap<>();
        double h = half();
        for (Player pl : p.center.getWorld().getPlayers()) {
            if (pl.getGameMode() == GameMode.SPECTATOR || pl.isDead()) continue;
            Location l = pl.getLocation();
            double dx = l.getX() - p.center.getX(), dz = l.getZ() - p.center.getZ(), dy = l.getY() - p.center.getY();
            if (Math.abs(dx) > h || Math.abs(dz) > h || dy < -3 || dy > 8) continue;      // only players physically inside count
            Team t = teamManager.getTeamByPlayer(pl.getUniqueId());
            if (t != null) counts.merge(t.getName().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        String leader = null;
        int best = 0;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); leader = e.getKey(); tie = false; }
            else if (e.getValue() == best) tie = true;
        }
        p.contested = tie && best > 0;
        if (leader == null || p.contested) { updateBar(p); return; }   // empty or tied: frozen

        if (p.owner == null || p.progress == 0) {
            if (!leader.equals(p.owner)) {
                p.owner = leader;
                Team t = teamManager.getTeamByName(leader);
                if (t != null) Bukkit.broadcastMessage("§b[Capture] " + t.getColorCode() + t.getName() + " §7is taking §f" + pretty(p.name) + "§7!");
            }
        }
        if (leader.equals(p.owner)) {
            p.progress++;
            if (p.progress >= total()) { captured(p); return; }
        } else {
            // Another team has the majority: drain the current owner's progress, then they start from zero.
            p.progress = Math.max(0, p.progress - drainSpeed());
            if (p.progress == 0) {
                Team old = teamManager.getTeamByName(p.owner);
                Team neu = teamManager.getTeamByName(leader);
                if (old != null && neu != null) Bukkit.broadcastMessage("§b[Capture] " + neu.getColorCode() + neu.getName()
                        + " §7knocked " + old.getColorCode() + old.getName() + " §7off §f" + pretty(p.name) + "§7!");
                p.owner = leader;
            }
        }
        updateBar(p);
    }

    private void captured(Point p) {
        Team t = teamManager.getTeamByName(p.owner);
        if (p.bar != null) p.bar.removeAll();
        active.remove(p.name);
        capturedToday++;
        if (t != null) {
            creditManager.addGameCredits(t.getCaptain(), reward());
            Bukkit.broadcastMessage("§b§l[CAPTURED] §f" + pretty(p.name) + " §ehas been captured by " + t.getColorCode() + t.getName()
                    + "§e! §a+" + reward() + " credits §7(" + capturedToday + "/" + Math.max(plannedToday, capturedToday) + " today)");
            for (Player pl : Bukkit.getOnlinePlayers()) {
                pl.sendTitle("§f" + pretty(p.name) + " §ecaptured", "§eby " + t.getColorCode() + t.getName() + " §a+" + reward(), 5, 50, 15);
                pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
            p.center.getWorld().spawnParticle(Particle.FIREWORK, p.center.clone().add(0, 2, 0), 120, 2, 2, 2, 0.15);
        }
        if (plugin instanceof me.moneysmp.MoneySMP smp && smp.getWebServerManager() != null) smp.getWebServerManager().requestPush();
        fillActive();
    }

    private void updateBar(Point p) {
        if (p.bar == null) return;
        Team t = p.owner == null ? null : teamManager.getTeamByName(p.owner);
        String where = "§7(" + p.center.getBlockX() + ", " + p.center.getBlockZ() + ")";
        String title;
        double prog;
        if (t == null || p.progress == 0) {
            title = "§f⚑ " + pretty(p.name) + " " + where + " §8| §7Neutral - stand on it with the most players";
            prog = 0;
            p.bar.setColor(BarColor.WHITE);
        } else {
            int left = total() - p.progress;
            prog = p.progress / (double) total();
            title = "§f⚑ " + pretty(p.name) + " " + where + " §8| " + t.getColorCode() + t.getName() + " "
                    + "§6CAPTURING" + " §f" + fmt(left)
                    + (p.contested ? " §c§lCONTESTED" : "");
            p.bar.setColor(barColor(t.getName()));
        }
        p.bar.setTitle(title);
        p.bar.setProgress(Math.max(0, Math.min(1, prog)));
        for (Player pl : Bukkit.getOnlinePlayers()) if (!p.bar.getPlayers().contains(pl)) p.bar.addPlayer(pl);
    }

    private void particles(Point p) {
        World w = p.center.getWorld();
        double h = half();
        Team t = p.owner == null ? null : teamManager.getTeamByName(p.owner);
        Color c = t == null ? Color.WHITE : dyeColor(t.getName());
        Particle.DustOptions dust = new Particle.DustOptions(c, 1.6f);
        double y0 = p.center.getY() + 0.3;
        for (double d = -h; d <= h; d += 1.0) {   // square outline
            w.spawnParticle(Particle.DUST, p.center.getX() + d, y0, p.center.getZ() - h, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, p.center.getX() + d, y0, p.center.getZ() + h, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, p.center.getX() - h, y0, p.center.getZ() + d, 1, 0, 0, 0, 0, dust);
            w.spawnParticle(Particle.DUST, p.center.getX() + h, y0, p.center.getZ() + d, 1, 0, 0, 0, 0, dust);
        }
        for (int y = 0; y < 12; y += 2) w.spawnParticle(Particle.DUST, p.center.clone().add(0, y, 0), 1, 0, 0, 0, 0, dust);
    }

    // ---------------------------------------------------------------- reload / restart
    /** Saves today's capture points (open points with their progress, and the ones still queued). */
    public void saveState(ConfigurationSection out) {
        out.set("queue", new ArrayList<>(todayQueue));
        out.set("planned", plannedToday);
        out.set("captured", capturedToday);
        for (Point p : active.values()) {
            out.set("active." + p.name + ".owner", p.owner);
            out.set("active." + p.name + ".progress", p.progress);
        }
    }

    /** Restores what saveState wrote, quietly, and carries on ticking. */
    public void loadState(ConfigurationSection in) {
        if (in == null) return;
        Map<String, Location> saved = savedPoints();
        todayQueue.clear();
        for (String n : in.getStringList("queue")) if (saved.containsKey(n)) todayQueue.add(n);
        plannedToday = in.getInt("planned");
        capturedToday = in.getInt("captured");
        ConfigurationSection act = in.getConfigurationSection("active");
        if (act != null) {
            for (String name : act.getKeys(false)) {
                Location loc = saved.get(name);
                if (loc == null) continue;
                Point p = new Point(name, loc);
                p.owner = act.getString(name + ".owner");
                p.progress = act.getInt(name + ".progress");
                p.bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SEGMENTED_10);
                active.put(name, p);
                updateBar(p);
            }
        }
        if (!active.isEmpty() || !todayQueue.isEmpty()) {
            ensureTask();
            plugin.getLogger().info("Restored " + active.size() + " open capture point(s) after reload.");
        }
    }

    /** Hides the boss bars without announcing anything (used when the plugin shuts down). */
    public void hideBars() {
        for (Point p : active.values()) if (p.bar != null) p.bar.removeAll();
    }

    // ---------------------------------------------------------------- info
    public void sendList(CommandSender s) {
        Map<String, Location> saved = savedPoints();
        s.sendMessage("§b§l[Capture Points] §7" + saved.size() + " saved (aim for 8):");
        for (Map.Entry<String, Location> e : saved.entrySet()) {
            Location l = e.getValue();
            Point p = active.get(e.getKey());
            String status = p != null ? "§aOPEN" : todayQueue.contains(e.getKey()) ? "§equeued" : "§7closed";
            s.sendMessage("  §f" + pretty(e.getKey()) + " §7(" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ") " + status);
        }
        if (plannedToday > 0) s.sendMessage("§7Today: §e" + capturedToday + "/" + plannedToday + " §7captured, §e" + todayQueue.size() + " §7still to open.");
    }

    public List<String> pointNames() { return new ArrayList<>(savedPoints().keySet()); }
    public int activeCount() { return active.size(); }

    private static String pretty(String n) { return n.isEmpty() ? n : Character.toUpperCase(n.charAt(0)) + n.substring(1); }
    private static String fmt(int secs) { return (secs / 60) + ":" + String.format("%02d", secs % 60); }

    private static BarColor barColor(String team) {
        String l = team.toLowerCase(Locale.ROOT);
        if (l.contains("red")) return BarColor.RED;
        if (l.contains("blue")) return BarColor.BLUE;
        if (l.contains("green")) return BarColor.GREEN;
        if (l.contains("yellow") || l.contains("gold")) return BarColor.YELLOW;
        if (l.contains("purple")) return BarColor.PURPLE;
        if (l.contains("pink")) return BarColor.PINK;
        return BarColor.WHITE;
    }

    private static Color dyeColor(String team) {
        String l = team.toLowerCase(Locale.ROOT);
        if (l.contains("red")) return Color.RED;
        if (l.contains("blue")) return Color.fromRGB(60, 120, 255);
        if (l.contains("green")) return Color.LIME;
        if (l.contains("yellow") || l.contains("gold")) return Color.YELLOW;
        if (l.contains("purple")) return Color.PURPLE;
        return Color.WHITE;
    }
}
