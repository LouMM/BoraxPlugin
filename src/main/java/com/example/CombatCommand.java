// Step 10: Add new class src/main/java/com/example/CombatCommand.java
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * /combat lookup <name/uuid> [limit=10]: Shows recent hits/kills by/on player.
 * Color-coded: green=your hits, red=hit on you.
 */
public class CombatCommand implements CommandExecutor {
    private final CombatCache combatCache;
    private final ConfigManager configManager;

    public CombatCommand(CombatCache combatCache, ConfigManager configManager) {
        this.combatCache = combatCache;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("combat.use")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }
        if (!configManager.isCombatTrackingEnabled()) {
            sender.sendMessage(ChatColor.RED + "Combat tracking disabled!");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "/combat lookup <player/uuid> [limit=10]");
            return true;
        }
        UUID targetUuid = null;
        try {
            targetUuid = UUID.fromString(args[1]);
        } catch (IllegalArgumentException ignored) {
            Player targetPlayer = Bukkit.getPlayer(args[1]);
            if (targetPlayer != null) {
                targetUuid = targetPlayer.getUniqueId();
            }
        }
        if (targetUuid == null) {
            sender.sendMessage(ChatColor.RED + "Player/UUID not found: " + args[1]);
            return true;
        }
        int limit = args.length > 2 ? Math.min(20, Integer.parseInt(args[2])) : 10;
        List<CombatRecord> records = combatCache.getRecordsInvolvingPlayer(targetUuid, limit);
        sender.sendMessage(ChatColor.GOLD + "Recent combat for " + (Bukkit.getPlayer(targetUuid) != null ? Bukkit.getPlayer(targetUuid).getName() : targetUuid) + ":");
        if (records.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "No recent records.");
            return true;
        }
        for (CombatRecord record : records) {
            String msg = getString(record, targetUuid);
            sender.sendMessage(msg);
        }
        return true;
    }

    private static @NonNull String getString(CombatRecord record, UUID targetUuid) {
        boolean isOutgoingHit = record.attackerUUID().equals(targetUuid);
        String prefix = isOutgoingHit ? ChatColor.GREEN + "Hit " : ChatColor.RED + "Hit by ";
        String victimOrAttacker = isOutgoingHit ? record.victimName() : record.attackerName();
        String killTag = record.isFatalKill() ? ChatColor.DARK_RED + " [KILL]" : "";
        return String.format("%s§e%s§a (%.1fdmg%s) §b%s §aat §c%.0f,%.0f,%.0f §a(%s body)%s",
                prefix, victimOrAttacker, record.damageAmount(), killTag, record.weaponMaterial().name(),
                record.hitLocation().getX(), record.hitLocation().getY(), record.hitLocation().getZ(),
                record.hitBodyPart(), ChatColor.RESET);
    }
}