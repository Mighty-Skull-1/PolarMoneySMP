package me.moneysmp.listeners;

import me.moneysmp.managers.DraftManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/** Makes the draft bid/SOLD items clickable and impossible to drop, move, place or keep. */
public class DraftItemListener implements Listener {
    private final JavaPlugin plugin;
    private final DraftManager draft;

    public DraftItemListener(JavaPlugin plugin, DraftManager draft) {
        this.plugin = plugin;
        this.draft = draft;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String action = draft.draftAction(e.getItem());
        if (action == null) return;
        e.setCancelled(true);
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(plugin, () -> draft.useItem(p, action));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlace(BlockPlaceEvent e) {
        if (draft.draftAction(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (draft.draftAction(e.getCurrentItem()) != null || draft.draftAction(e.getCursor()) != null) { e.setCancelled(true); return; }
        if (e.getHotbarButton() >= 0 && e.getWhoClicked() instanceof Player p
                && draft.draftAction(p.getInventory().getItem(e.getHotbarButton())) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (draft.draftAction(e.getOldCursor()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (draft.draftAction(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (draft.draftAction(e.getMainHandItem()) != null || draft.draftAction(e.getOffHandItem()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(it -> draft.draftAction(it) != null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        draft.takeItems(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // Leftovers from a crash/restart mid-auction get cleaned up; captains joining mid-auction get their items.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            if (!draft.isDraftActive()) draft.takeItems(p);
            else draft.giveItemsIfNeeded(p);
        }, 20L);
    }
}
