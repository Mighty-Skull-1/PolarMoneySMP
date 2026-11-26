package me.moneysmp.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.moneysmp.managers.RankManager;
import me.moneysmp.models.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Gives ranked players their permissions on join, and shows [Owner] / [Admin] / [Sub] before their name in chat. */
public class RankListener implements Listener {
    private final RankManager rankManager;

    public RankListener(RankManager rankManager) {
        this.rankManager = rankManager;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        rankManager.apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        rankManager.forget(event.getPlayer());
    }

    /** Chat looks like "[Owner] <Name> message" - players without a rank keep the normal chat format. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Rank rank = rankManager.getRank(event.getPlayer().getUniqueId());
        if (rank == Rank.NONE) return;
        Component prefix = LegacyComponentSerializer.legacySection().deserialize(rank.getPrefix() + " ");
        event.renderer((source, displayName, message, viewer) -> Component.empty()
                .append(prefix)
                .append(Component.text("<"))
                .append(displayName)
                .append(Component.text("> "))
                .append(message));
    }
}
