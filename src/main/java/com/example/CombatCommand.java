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
    private final NameUuidManager nameUuidManager;
    private final TabListManager tabListManager;

    public CombatCommand(CombatCache combatCache, ConfigManager configManager, PersistenceManager persistenceManager, NameUuidManager nameUuidManager, TabListManager tabListManager) {
        this.combatCache = combatCache;
        this.configManager = configManager;
        this.persistenceManager = persistenceManager;
        this.nameUuidManager = nameUuidManager;
        this.tabListManager = tabListManager;
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
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: delete <player/uuid|all|winslosses> [Xd e.g. 30d or player/all]").color(NamedTextColor.YELLOW));
                return true;
            }
            String target = args[1].toLowerCase();

            if (target.equals("winslosses")) {
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: delete winslosses <player/uuid|all>").color(NamedTextColor.YELLOW));
                    return true;
                }
                String wlTarget = args[2].toLowerCase();
                if (wlTarget.equals("all")) {
                    persistenceManager.resetAllWinsLosses();
                    tabListManager.updateAllTabNames();
                    sender.sendMessage(Component.text("Reset wins/losses for all players.").color(NamedTextColor.GREEN));
                } else {
                    UUID uuid = resolveUuid(wlTarget);
                    if (uuid == null) {
                        sender.sendMessage(Component.text("Player/UUID not found: " + wlTarget).color(NamedTextColor.RED));
                        return true;
                    }
                    persistenceManager.resetWinsLosses(uuid);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        tabListManager.updateTabName(p);
                    }
                    sender.sendMessage(Component.text("Reset wins/losses for " + wlTarget).color(NamedTextColor.GREEN));
                }
                return true;
            }

            long timespanMs = 0; // 0 means delete all
            if (args.length >= 3) {
                String timespanStr = args[2].toLowerCase();
                timespanMs = parseTimespan(timespanStr);
                if (timespanMs < 0) {
                    sender.sendMessage(Component.text("Invalid timespan: " + timespanStr).color(NamedTextColor.RED));
                    return true;
                }
            }

            if (target.equals("all")) {
                persistenceManager.deleteOldRecordsForAll(timespanMs);
                combatCache.deleteOldRecordsForAll(timespanMs);
                sender.sendMessage(Component.text("Deleted records for all players" + (timespanMs == 0 ? "." : " older than " + args[2])).color(NamedTextColor.GREEN));
            } else {
                UUID uuid = resolveUuid(target);
                if (uuid == null) {
                    sender.sendMessage(Component.text("Player/UUID not found: " + target).color(NamedTextColor.RED));
                    return true;
                }
                persistenceManager.deleteOldRecords(uuid, timespanMs);
                combatCache.deleteOldRecords(uuid, timespanMs);
                sender.sendMessage(Component.text("Deleted records for " + target + (timespanMs == 0 ? "." : " older than " + args[2])).color(NamedTextColor.GREEN));
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
            return nameUuidManager.getUuidFromName(input);
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