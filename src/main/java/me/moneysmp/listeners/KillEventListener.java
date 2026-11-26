package me.moneysmp.listeners;

import me.moneysmp.managers.*;
import me.moneysmp.models.PlayerTier;
import me.moneysmp.models.SMPPhase;
import me.moneysmp.models.Team;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KillEventListener implements Listener {
    private final JavaPlugin plugin;
    private final TeamManager teamManager;
    private final CreditManager creditManager;
    private final TierManager tierManager;
    private final PhaseManager phaseManager;
    private final EventManager eventManager;
    private final EliminationManager eliminationManager;
    private final SMPGuiManager smpGuiManager;


    public KillEventListener(JavaPlugin plugin, TeamManager teamManager, CreditManager creditManager,
                             TierManager tierManager, PhaseManager phaseManager, EventManager eventManager,
                             EliminationManager eliminationManager, SMPGuiManager smpGuiManager) {
        this.plugin = plugin;
        this.teamManager = teamManager;
        this.creditManager = creditManager;
        this.tierManager = tierManager;
        this.phaseManager = phaseManager;
        this.eventManager = eventManager;
        this.eliminationManager = eliminationManager;
        this.smpGuiManager = smpGuiManager;
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }

        if (attacker == null || attacker.equals(victim)) return;

        // GRACE PERIOD BLOCK
        if (!phaseManager.isPvPAllowed()) {
            event.setCancelled(true);
            attacker.sendMessage("§cPvP is disabled during " + phaseManager.getCurrentPhase().getDisplayName() + "!");
            return;
        }

        Team attackerTeam = teamManager.getTeamByPlayer(attacker.getUniqueId());
        Team victimTeam = teamManager.getTeamByPlayer(victim.getUniqueId());

        // FRIENDLY FIRE BLOCK
        if (attackerTeam != null && victimTeam != null && attackerTeam.equals(victimTeam)) {
            event.setCancelled(true);
            attacker.sendMessage("§cYou cannot hit your teammate!");
        }
    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        Team victimTeam = teamManager.getTeamByPlayer(victim.getUniqueId());

        // FFA event: dying only knocks you out of the FFA - never out of the tournament.
        me.moneysmp.managers.FfaManager ffa = (plugin instanceof me.moneysmp.MoneySMP smpF) ? smpF.getFfaManager() : null;
        boolean ffaDeath = ffa != null && ffa.isAlive(victim.getUniqueId());
        int tdmExtra = ffaDeath ? ffa.extraKillReward(victim.getUniqueId()) : 0;
        boolean tdmBounty = ffaDeath && ffa.isBounty(victim.getUniqueId());
        if (ffaDeath) ffa.knockOut(victim.getUniqueId(), null);

        // The final fight (Team Deathmatch) handles its own knock-outs above. Otherwise permanent
        // elimination only happens if elimination.enabled is turned on in config.yml.
        if (eliminationManager != null && !ffaDeath) {
            SMPPhase phase = phaseManager.getCurrentPhase();
            boolean permadeath = (phase == SMPPhase.PVP || phase == SMPPhase.FINALE)
                    && plugin.getConfig().getBoolean("elimination.enabled", false);
            if (permadeath) eliminationManager.eliminate(victim, victimTeam);
        }

        if (killer == null || killer.equals(victim)) return;

        Team killerTeam = teamManager.getTeamByPlayer(killer.getUniqueId());

        if (killerTeam != null && victimTeam != null && killerTeam.equals(victimTeam)) {
            killer.sendMessage("§c[Polar Money SMP] Team Kill! No credits rewarded.");
            return;
        }

        if (killerTeam == null || victimTeam == null) return;

        if (plugin instanceof me.moneysmp.MoneySMP smp && smp.getStatsManager() != null) {
            smp.getStatsManager().addKill(killer.getUniqueId());
            smp.getStatsManager().addDeath(victim.getUniqueId());
        }

        PlayerTier victimTier = tierManager.getTier(victim.getUniqueId());
        int rewardPoints = 10;

        if (victimTeam.isCaptain(victim.getUniqueId()) || victimTier == PlayerTier.CAPTAIN) {
            rewardPoints = plugin != null ? plugin.getConfig().getInt("rewards.captain-kill", 30) : 30;
        } else if (victimTier == PlayerTier.A) {
            rewardPoints = plugin != null ? plugin.getConfig().getInt("rewards.tier-a-kill", 25) : 25;
        } else if (victimTier == PlayerTier.B) {
            rewardPoints = plugin != null ? plugin.getConfig().getInt("rewards.tier-b-kill", 20) : 20;
        } else if (victimTier == PlayerTier.C) {
            rewardPoints = plugin != null ? plugin.getConfig().getInt("rewards.tier-c-kill", 15) : 15;
        } else if (victimTier == PlayerTier.D) {
            rewardPoints = plugin != null ? plugin.getConfig().getInt("rewards.tier-d-kill", 10) : 10;
        }

        rewardPoints += tdmExtra; // Team Deathmatch: +20 per kill, +20 more on the glowing bounty tier
        creditManager.addGameCredits(killer.getUniqueId(), rewardPoints);
        killer.sendMessage("§a+" + rewardPoints + " Game Credits for killing " + victim.getName() + " (" + victimTier.name() + " Tier)"
                + (tdmBounty ? " §6§l[BOUNTY]" : tdmExtra > 0 ? " §c[DEATHMATCH]" : "") + "§a!");
    }

    /** Eliminated players stay out even if they log out and back in. */
    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Players drafted / tiered / subbed in before they ever joined are already on their team by UUID.
        // Save their real name (also catches name changes) and show their team, tier and colour right away.
        UUID id = player.getUniqueId();
        Team team = teamManager.getTeamByPlayer(id);
        boolean tracked = team != null || tierManager.getTier(id) != PlayerTier.NONE;
        boolean nameChanged = me.moneysmp.util.PlayerNames.remember(id, player.getName());
        if (plugin instanceof me.moneysmp.MoneySMP smpJoin) {
            if (tracked && nameChanged && smpJoin.getDataManager() != null) smpJoin.getDataManager().saveData();
            if (tracked && nameChanged && smpJoin.getWebServerManager() != null) smpJoin.getWebServerManager().requestPush();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && smpJoin.getDisplayManager() != null) smpJoin.getDisplayManager().updateAllDisplays();
            });
        }
        if (team != null && !player.hasPlayedBefore()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage("§6§l[Polar Money SMP] §eWelcome! You're on " + team.getColorCode() + "§l" + team.getName()
                        + "§e as " + tierManager.getTier(id).getPrefix(team.getColorCode()) + "§e. Use §f/team info §eto see your team.");
            }, 40L);
        }

        if (eliminationManager != null && eliminationManager.isEliminated(player.getUniqueId())
                && player.getGameMode() != GameMode.SPECTATOR) {
            Bukkit.getScheduler().runTask(plugin, () -> player.setGameMode(GameMode.SPECTATOR));
        }
        if (plugin instanceof me.moneysmp.MoneySMP smp && smp.getFfaManager() != null) smp.getFfaManager().handleJoin(player);
    }

    /** Logging out during an FFA counts as being knocked out (no combat logging). */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (plugin instanceof me.moneysmp.MoneySMP smp && smp.getFfaManager() != null
                && smp.getFfaManager().isAlive(event.getPlayer().getUniqueId())) {
            smp.getFfaManager().knockOut(event.getPlayer().getUniqueId(), "logged out");
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (plugin instanceof me.moneysmp.MoneySMP smp && smp.getFfaManager() != null) {
            smp.getFfaManager().handleRespawn(player, event.getRespawnLocation());
        }
        if (eliminationManager != null && eliminationManager.isEliminated(player.getUniqueId())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage(phaseManager.getCurrentPhase() == SMPPhase.FINALE
                        ? "§c§l[ELIMINATED] §eYou're spectating for the rest of the tournament."
                        : "§c§l[ELIMINATED] §eYou are in Spectator mode for the remainder of the phase!");
            });
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.contains("Bingo")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 53 && event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
            return;
        }

        if (title.equals(SMPGuiManager.GUI_TITLE)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player && smpGuiManager != null) {
                smpGuiManager.handleClick(player, event.getRawSlot(), event.getClick());
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("Bingo") || title.equals(SMPGuiManager.GUI_TITLE) || title.equals(BannedItemManager.GUI_TITLE)) {
            event.setCancelled(true);
        }
    }
}
