package me.moneysmp.commands;

import me.moneysmp.managers.TierManager;
import me.moneysmp.models.PlayerTier;
import me.moneysmp.util.PlayerLookup;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** /tier set <player> <A|B|C|D> and /tier remove <player>. Works for players who have never joined. */
public class TierCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final TierManager tierManager;

    public TierCommand(JavaPlugin plugin, TierManager tierManager) {
        this.plugin = plugin;
        this.tierManager = tierManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("moneysmp.admin")) {
            sender.sendMessage("§cOnly server admins can set player tiers!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage:\n/tier set <player> <A|B|C|D>\n/tier remove <player>");
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /tier set <player> <A|B|C|D>  (captains are S tier automatically)");
                return true;
            }
            PlayerTier tier;
            try {
                tier = PlayerTier.valueOf(args[2].toUpperCase());
                if (tier == PlayerTier.NONE || tier == PlayerTier.CAPTAIN) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cInvalid Tier! Use A, B, C or D. (S tier = captains, set by /team create)");
                return true;
            }
            PlayerLookup.resolve(plugin, args[1], result -> {
                if (!result.found()) { sender.sendMessage(PlayerLookup.failMessage(result, args[1])); return; }
                tierManager.setTier(result.uuid(), tier);
                sender.sendMessage("§aAssigned " + result.name() + " to Tier " + tier.name());
                saveAndSync();
            });
        } else if (args[0].equalsIgnoreCase("remove")) {
            PlayerLookup.resolve(plugin, args[1], result -> {
                if (!result.found()) { sender.sendMessage(PlayerLookup.failMessage(result, args[1])); return; }
                tierManager.removeTier(result.uuid());
                sender.sendMessage("§aRemoved tier from " + result.name());
                saveAndSync();
            });
        } else {
            sender.sendMessage("§cUsage:\n/tier set <player> <A|B|C|D>\n/tier remove <player>");
        }
        return true;
    }

    private void saveAndSync() {
        if (plugin instanceof me.moneysmp.MoneySMP smp) {
            if (smp.getDataManager() != null) smp.getDataManager().saveData();
            if (smp.getWebServerManager() != null) smp.getWebServerManager().requestPush();
        }
    }
}
