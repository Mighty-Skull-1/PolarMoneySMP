package me.moneysmp.listeners;

import io.papermc.paper.event.player.PlayerTradeEvent;
import me.moneysmp.managers.EventManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

/**
 * Season 2 bingo board detection (25 tasks).
 * "Obtain/Get/Collect" tasks are checked by scanning inventories every 2 seconds (so chests, trades,
 * drops and fishing all count). "Find a Shipwreck / Ancient City" are checked by standing inside the structure.
 */
public class BingoEventListener implements Listener {
    private final JavaPlugin plugin;
    private final EventManager eventManager;

    public BingoEventListener(JavaPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        // Inventory + structure checks every 2 seconds while bingo is running.
        Bukkit.getScheduler().runTaskTimer(plugin, this::periodicChecks, 40L, 40L);
    }

    /**
     * What each player already had when bingo started (or when they were first seen during it).
     * Items brought in from before don't count - you have to get them during bingo.
     */
    private final java.util.Map<java.util.UUID, java.util.Set<String>> baseline = new java.util.HashMap<>();

    private void claim(Player p, String task) { eventManager.claimBingo(p, task); }

    // ------------------------------------------------------------ periodic checks
    private void periodicChecks() {
        if (!eventManager.isBingoActive()) { baseline.clear(); return; }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            scanInventory(p);
            checkStructures(p);
        }
    }

    private void scanInventory(Player p) {
        java.util.Set<String> now = heldTasks(p);
        java.util.Set<String> base = baseline.get(p.getUniqueId());
        if (base == null) { baseline.put(p.getUniqueId(), now); return; }   // first look: just remember what they had
        for (String task : now) if (!base.contains(task)) claim(p, task);
        base.retainAll(now);   // once they drop/use a starting item, getting one again counts
    }

    /** The "obtain / wear" tasks this player's inventory currently satisfies. */
    private java.util.Set<String> heldTasks(Player p) {
        java.util.Set<String> out = new java.util.HashSet<>();
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) {
            if (it == null) continue;
            Material m = it.getType();
            String n = m.name();
            if (m == Material.HEAVY_CORE) out.add("Obtain Heavy Core");
            else if (m == Material.GOAT_HORN) out.add("Obtain a Goat Horn");
            else if (m == Material.NAUTILUS_SHELL) out.add("Obtain a Nautilus Shell");
            else if (n.startsWith("MUSIC_DISC_")) out.add("Get a Music Disc");
            else if (n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) out.add("Find an Armor Trim");
            else if (m == Material.BREEZE_ROD) out.add("Collect a Breeze Rod");
            else if (m == Material.TRIDENT) out.add("Get a Trident");
            else if (m == Material.DIAMOND_SWORD && !it.getEnchantments().isEmpty()) out.add("Enchant a Diamond Sword");
        }
        if (is(inv.getHelmet(), Material.LEATHER_HELMET) && is(inv.getChestplate(), Material.LEATHER_CHESTPLATE)
                && is(inv.getLeggings(), Material.LEATHER_LEGGINGS) && is(inv.getBoots(), Material.LEATHER_BOOTS)) {
            out.add("Wear Full Leather");
        }
        return out;
    }

    private static boolean is(ItemStack it, Material m) { return it != null && it.getType() == m; }

    private void checkStructures(Player p) {
        Location l = p.getLocation();
        Chunk c = l.getChunk();
        try {
            if (inside(p, c, Structure.SHIPWRECK) || inside(p, c, Structure.SHIPWRECK_BEACHED)) claim(p, "Find a Shipwreck");
            if (inside(p, c, Structure.ANCIENT_CITY)) claim(p, "Find an Ancient City");
        } catch (Throwable ignored) {
            // structure lookup not available on this server version - the chest check below still works
        }
    }

    private boolean inside(Player p, Chunk c, Structure s) {
        for (GeneratedStructure g : p.getWorld().getStructures(c.getX(), c.getZ(), s)) {
            if (g.getBoundingBox().contains(p.getLocation().toVector())) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ events
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Material m = e.getBlock().getType();
        if (m == Material.EMERALD_ORE || m == Material.DEEPSLATE_EMERALD_ORE) claim(e.getPlayer(), "Mine Emerald Ore");
        if (m == Material.HEAVY_CORE) claim(e.getPlayer(), "Obtain Heavy Core");
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Material r = e.getRecipe().getResult().getType();
        if (r == Material.GOLDEN_CARROT) claim(p, "Craft Golden Carrots");
        if (r == Material.LODESTONE) claim(p, "Craft a Lodestone");
        if (r == Material.BEACON) claim(p, "Craft Beacon");
    }

    @EventHandler
    public void onTame(EntityTameEvent e) {
        if (e.getOwner() instanceof Player p && e.getEntity() instanceof Horse) claim(p, "Tame a Horse");
    }

    @EventHandler
    public void onTrade(PlayerTradeEvent e) {
        if (e.getVillager() instanceof Villager) claim(e.getPlayer(), "Trade with a Villager");
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == Material.SUSPICIOUS_STEW) claim(e.getPlayer(), "Eat Suspicious Stew");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // Fill a Map: right-click an empty map.
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && e.getItem() != null
                && e.getItem().getType() == Material.MAP) {
            claim(p, "Fill a Map");
        }
        // Opening a structure chest also counts as finding the structure (backup for the area check).
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && e.getClickedBlock().getState() instanceof org.bukkit.loot.Lootable lootable && lootable.getLootTable() != null) {
            String key = lootable.getLootTable().getKey().getKey();
            if (key.contains("shipwreck")) claim(p, "Find a Shipwreck");
            if (key.contains("ancient_city")) claim(p, "Find an Ancient City");
        }
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        if (e.getItem().getType() == Material.DIAMOND_SWORD) claim(e.getEnchanter(), "Enchant a Diamond Sword");
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getNewEffect() == null) return;
        if (e.getNewEffect().getType() != PotionEffectType.OOZING) return;
        EntityPotionEffectEvent.Cause c = e.getCause();
        if (c == EntityPotionEffectEvent.Cause.POTION_DRINK || c == EntityPotionEffectEvent.Cause.POTION_SPLASH
                || c == EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD) {
            claim(p, "Brew a Potion of Oozing");
        }
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        LivingEntity v = e.getEntity();
        if (v instanceof Warden) claim(k, "Kill a Warden");
        if (v instanceof Ghast) claim(k, "Kill a Ghast");
        if (v instanceof ElderGuardian) claim(k, "Kill an Elder Guardian");
        if (v instanceof Witch) claim(k, "Kill a Witch");
        if (v instanceof Raider r) {
            boolean banner = v.getEquipment() != null && v.getEquipment().getHelmet() != null
                    && v.getEquipment().getHelmet().getType() == Material.WHITE_BANNER;
            if (r.isPatrolLeader() || banner) claim(k, "Kill a Pillager Captain");
        }
    }
}
