// Update to src/main/java/com/example/CombatCommand.java (ensure resolveUuid method is included)
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

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
            sender.sendMessage(Component.text("No permission!").color(NamedTextColor.RED));
            return true;
        }
        if (!configManager.isCombatTrackingEnabled()) {
            sender.sendMessage(Component.text("Combat tracking disabled!").color(NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(Component.text("/combat lookup <player/uuid|all> [recent|full] [limit=10] | delete <player/uuid|all> <Xd>").color(NamedTextColor.YELLOW));
            return true;
        }
        String subCmd = args[0].toLowerCase();
        if (subCmd.equals("lookup")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: lookup <player/uuid|all> [recent|full] [limit]").color(NamedTextColor.YELLOW));
                return true;
            }
            
            String targetArg = args[1].toLowerCase();
            String mode = args.length > 2 ? args[2].toLowerCase() : "recent";
            int limit = args.length > 3 ? Integer.parseInt(args[3]) : 10;

            if (targetArg.equals("all")) {
                if (!sender.isOp()) {
                    sender.sendMessage(Component.text("Only OPs can lookup all players.").color(NamedTextColor.RED));
                    return true;
                }
                for (Player p : Bukkit.getOnlinePlayers()) {
                    sender.sendMessage(Component.text("--------------------------------------------------").color(NamedTextColor.DARK_GRAY));
                    printPlayerCombat(sender, p.getUniqueId(), p.getName(), mode, limit);
                }
                sender.sendMessage(Component.text("--------------------------------------------------").color(NamedTextColor.DARK_GRAY));
                return true;
            }

            UUID targetUuid = resolveUuid(args[1]);
            if (targetUuid == null) {
                sender.sendMessage(Component.text("Player/UUID not found: " + args[1]).color(NamedTextColor.RED));
                return true;
            }

            if (!sender.isOp() && sender instanceof Player p && !p.getUniqueId().equals(targetUuid)) {
                sender.sendMessage(Component.text("You can only lookup your own combat records.").color(NamedTextColor.RED));
                return true;
            }

            printPlayerCombat(sender, targetUuid, args[1], mode, limit);
        } else if (subCmd.equals("delete")) {
            if (!sender.isOp()) {
                sender.sendMessage(Component.text("Only OPs can delete combat records.").color(NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Component.text("Usage: delete <player/uuid|all> <Xd e.g. 5d>").color(NamedTextColor.YELLOW));
                return true;
            }
            String target = args[1].toLowerCase();
            String timespanStr = args[2].toLowerCase();
            long timespanMs = parseTimespan(timespanStr);
            if (timespanMs <= 0) {
                sender.sendMessage(Component.text("Invalid timespan: " + timespanStr).color(NamedTextColor.RED));
                return true;
            }

            if (target.equals("all")) {
                persistenceManager.deleteOldRecordsForAll(timespanMs);
                sender.sendMessage(Component.text("Deleted old records for all players.").color(NamedTextColor.GREEN));
            } else {
                UUID uuid = resolveUuid(target);
                if (uuid == null) {
                    sender.sendMessage(Component.text("Player/UUID not found: " + target).color(NamedTextColor.RED));
                    return true;
                }
                persistenceManager.deleteOldRecords(uuid, timespanMs);
                sender.sendMessage(Component.text("Deleted old records for " + target).color(NamedTextColor.GREEN));
            }
        } else {
            sender.sendMessage(Component.text("Unknown: " + subCmd).color(NamedTextColor.RED));
        }
        return true;
    }

    private void printPlayerCombat(CommandSender sender, UUID targetUuid, String targetName, String mode, int limit) {
        List<CombatRecord> records;
        if (mode.equals("full")) {
            records = persistenceManager.getFullRecordsInvolvingPlayer(targetUuid);
            records.addAll(combatCache.getRecordsInvolvingPlayer(targetUuid, Integer.MAX_VALUE));
            records.sort(Comparator.comparingLong(CombatRecord::timestamp).reversed());
            records = records.stream().distinct().limit(limit).toList();
        } else {
            records = combatCache.getRecordsInvolvingPlayer(targetUuid, limit);
        }

        // Avatar/Header formatting
        WinsLosses wl = persistenceManager.getWinsLosses(targetUuid);
        long fightKills = records.stream().filter(r -> r.isFatalKill() && r.attackerUUID().equals(targetUuid) && r.fightSessionId() != null).count();
        long nonFightKills = records.stream().filter(r -> r.isFatalKill() && r.attackerUUID().equals(targetUuid) && r.fightSessionId() == null).count();
        String icon = nonFightKills > fightKills ? "☠" : "★";

        Component header = Component.text("👤 " + targetName, NamedTextColor.GREEN)
                .append(Component.text(" | W:" + wl.wins() + " L:" + wl.losses(), NamedTextColor.AQUA))
                .append(Component.text(" | " + icon, NamedTextColor.RED))
                .append(Component.text(" (" + mode + "):", NamedTextColor.GOLD));
        sender.sendMessage(header);

        if (records.isEmpty()) {
            sender.sendMessage(Component.text("  No records.").color(NamedTextColor.GRAY));
            return;
        }
        for (CombatRecord r : records) {
            boolean outgoing = r.attackerUUID().equals(targetUuid);
            Component prefix = outgoing ? Component.text("  Hit ").color(NamedTextColor.GREEN) : Component.text("  Hit by ").color(NamedTextColor.RED);
            String other = outgoing ? r.victimName() : r.attackerName();
            Component kill = r.isFatalKill() ? Component.text(" [KILL]").color(NamedTextColor.DARK_RED) : Component.empty();
            Component msg = prefix.append(Component.text(other).color(NamedTextColor.YELLOW))
                    .append(Component.text(" (" + String.format("%.1f", r.damageAmount()) + "dmg").color(NamedTextColor.GREEN))
                    .append(kill)
                    .append(Component.text(") " + r.weaponMaterial().name()).color(NamedTextColor.GREEN))
                    .append(Component.text(" at " + r.hitLocation().getBlockX() + "," + r.hitLocation().getBlockY() + "," + r.hitLocation().getBlockZ()).color(NamedTextColor.RED))
                    .append(Component.text(" (" + r.hitBodyPart() + ")").color(NamedTextColor.RED));
            sender.sendMessage(msg);
        }
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