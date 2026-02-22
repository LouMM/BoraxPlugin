// Update to src/main/java/com/example/CombatCommand.java (ensure resolveUuid method is included)
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * /combat lookup: Use full involving for disk.
 */
public class CombatCommand implements CommandExecutor {
    private final CombatCache combatCache;
    private final ConfigManager configManager;
    private final PersistenceManager persistenceManager;

    public CombatCommand(CombatCache combatCache, ConfigManager configManager, PersistenceManager persistenceManager) {
        this.combatCache = combatCache;
        this.configManager = configManager;
        this.persistenceManager = persistenceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("combat.use")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }
        if (!configManager.isCombatTrackingEnabled()) {
            sender.sendMessage(ChatColor.RED + "Combat tracking disabled!");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "/combat lookup <player/uuid> [recent|full] [limit=10] | delete <player/uuid|all> <Xd>");
            return true;
        }
        String subCmd = args[0].toLowerCase();
        if (subCmd.equals("lookup")) {
            if (args.length < 2) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: lookup <player/uuid> [recent|full] [limit]");
                return true;
            }
            UUID targetUuid = resolveUuid(args[1]);
            if (targetUuid == null) {
                sender.sendMessage(ChatColor.RED + "Player/UUID not found: " + args[1]);
                return true;
            }
            String mode = args.length > 2 ? args[2].toLowerCase() : "recent";
            int limit = args.length > 3 ? Integer.parseInt(args[3]) : 10;

            List<CombatRecord> records;
            if (mode.equals("full")) {
                records = persistenceManager.getFullRecordsInvolvingPlayer(targetUuid);
                records.addAll(combatCache.getRecordsInvolvingPlayer(targetUuid, Integer.MAX_VALUE));
                records.sort(Comparator.comparingLong(CombatRecord::timestamp).reversed());
                records = records.stream().distinct().limit(limit).toList();
            } else {
                records = combatCache.getRecordsInvolvingPlayer(targetUuid, limit);
            }

            sender.sendMessage(ChatColor.GOLD + "Combat for " + args[1] + " (" + mode + "):");
            if (records.isEmpty()) {
                sender.sendMessage(ChatColor.GRAY + "No records.");
                return true;
            }
            for (CombatRecord r : records) {
                boolean outgoing = r.attackerUUID().equals(targetUuid);
                String prefix = outgoing ? ChatColor.GREEN + "Hit " : ChatColor.RED + "Hit by ";
                String other = outgoing ? r.victimName() : r.attackerName();
                String kill = r.isFatalKill() ? ChatColor.DARK_RED + " [KILL]" : "";
                String msg = prefix + ChatColor.YELLOW + other + ChatColor.GREEN + " (" + r.damageAmount() + "dmg" + kill + ") " + ChatColor.BLUE + r.weaponMaterial().name()
                        + " at " + ChatColor.RED + r.hitLocation().getBlockX() + "," + r.hitLocation().getBlockY() + "," + r.hitLocation().getBlockZ()
                        + " (" + r.hitBodyPart() + ")";
                sender.sendMessage(msg);
            }
        } else if (subCmd.equals("delete")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: delete <player/uuid|all> <Xd e.g. 5d>");
                return true;
            }
            String target = args[1].toLowerCase();
            String timespanStr = args[2].toLowerCase();
            long timespanMs = parseTimespan(timespanStr);
            if (timespanMs <= 0) {
                sender.sendMessage(ChatColor.RED + "Invalid timespan: " + timespanStr);
                return true;
            }

            if (target.equals("all")) {
                persistenceManager.deleteOldRecordsForAll(timespanMs);
                sender.sendMessage(ChatColor.GREEN + "Deleted old records for all players.");
            } else {
                UUID uuid = resolveUuid(target);
                if (uuid == null) {
                    sender.sendMessage(ChatColor.RED + "Player/UUID not found: " + target);
                    return true;
                }
                persistenceManager.deleteOldRecords(uuid, timespanMs);
                sender.sendMessage(ChatColor.GREEN + "Deleted old records for " + target);
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown: " + subCmd);
        }
        return true;
    }

    private UUID resolveUuid(String input) {
        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException ignored) {
            Player p = Bukkit.getPlayer(input);
            return p != null ? p.getUniqueId() : null;
        }
    }

    private long parseTimespan(String str) {
        if (!str.endsWith("d")) return -1;
        try {
            int days = Integer.parseInt(str.substring(0, str.length() - 1));
            return (long) days * 86400000L;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}