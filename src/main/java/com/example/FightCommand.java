// Update to src/main/java/com/example/FightCommand.java (add 'list' subcommand for all players; send join msg to added player)
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /fight handler: Team add/remove, start/end, etc. 'list' open to all (no perm).
 * Sends team join message to player on add.
 */
public class FightCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final FightManager fightManager;
    private final ConfigManager configManager;

    public FightCommand(PlayerLocs plugin, FightManager fightManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.fightManager = fightManager;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§e/fight <team1|team2|start|end|clear|scores|status|reload|toggle [combat|fight]|list|config>");
            return true;
        }
        String subCmd = args[0].toLowerCase();

        // 'list' subcommand: Open to all players, lists teams
        if (subCmd.equals("list")) {
            String team1List = getTeamPlayerNames(fightManager.getTeam1Players());
            String team2List = getTeamPlayerNames(fightManager.getTeam2Players());
            sender.sendMessage("§eCurrent Teams:");
            sender.sendMessage("§aTeam1: §f" + (team1List.isEmpty() ? "Empty" : team1List));
            sender.sendMessage("§cTeam2: §f" + (team2List.isEmpty() ? "Empty" : team2List));
            return true;
        }

        // Other subcommands require permission
        if (!sender.hasPermission("fight.use")) {
            sender.sendMessage("§cNo permission! (But you can use /fight list)");
            return true;
        }

        switch (subCmd) {
            case "team1", "team2" -> handleTeamCmd(sender, args, subCmd.equals("team1"));
            case "start" -> {
                fightManager.startFight();
                sender.sendMessage("§aFight started! (10min default)");
            }
            case "end" -> {
                fightManager.endCurrentFight();
                sender.sendMessage("§cFight ended manually.");
            }
            case "clear" -> {
                fightManager.clearTeams();
                sender.sendMessage("§eTeams cleared.");
            }
            case "scores" -> {
                var scores = fightManager.getCurrentScores();
                sender.sendMessage("§eCurrent scores: §aT1 [" + scores.team1Score() + "] §cvs §aT2 [" + scores.team2Score() + "]");
            }
            case "status" -> sender.sendMessage(String.format("§eCombat: %s | Fight: %s",
                    configManager.isCombatTrackingEnabled() ? "§aON" : "§cOFF",
                    configManager.isFightModeEnabled() ? "§aON" : "§cOFF"));
            case "reload" -> {
                configManager.reload();
                sender.sendMessage("§aConfig reloaded!");
            }
            case "toggle" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e/toggle <combat|fight>");
                    return true;
                }
                String feature = args[1].toLowerCase();
                boolean enabled;
                String path = feature.equals("combat") ? "enableCombatTracking" : "enableFightMode";
                enabled = !plugin.getConfig().getBoolean(path, true);
                plugin.getConfig().set(path, enabled);
                plugin.saveConfig();
                configManager.reload();
                sender.sendMessage("§e" + feature + " §a" + (enabled ? "enabled" : "disabled"));
            }
            case "config" -> {
                if (args.length < 3) {
                    sender.sendMessage("§e/fight config <setting> <value>");
                    return true;
                }
                String setting = args[1];
                String valueStr = args[2];
                try {
                    if (setting.equalsIgnoreCase("fightDefaultDurationSeconds") || setting.equalsIgnoreCase("escrowTimeoutSeconds")) {
                        plugin.getConfig().set(setting, Long.parseLong(valueStr));
                    } else if (setting.equalsIgnoreCase("autoFight.hitCount") || setting.equalsIgnoreCase("autoFight.timeWindowSeconds") ||
                               setting.equalsIgnoreCase("scoring.blockHitterPoints") || setting.equalsIgnoreCase("scoring.blockBlockerPoints") ||
                               setting.equalsIgnoreCase("scoring.killBasePoints") || setting.equalsIgnoreCase("scoring.armorBonusPerTier") ||
                               setting.equalsIgnoreCase("scoring.weakWeaponBonusPerTier")) {
                        plugin.getConfig().set(setting, Integer.parseInt(valueStr));
                    } else if (setting.equalsIgnoreCase("scoring.hitDamageMultiplier")) {
                        plugin.getConfig().set(setting, Double.parseDouble(valueStr));
                    } else if (setting.equalsIgnoreCase("fight.penaltyMode")) {
                        plugin.getConfig().set(setting, valueStr.toUpperCase());
                    } else if (setting.equalsIgnoreCase("fight.broadcast")) {
                        plugin.getConfig().set(setting, Boolean.parseBoolean(valueStr));
                    } else if (setting.equalsIgnoreCase("fight.KeepInventoryDuringFight") || setting.equalsIgnoreCase("fight.KeepInventoryFightEnd")) {
                        plugin.getConfig().set(setting, Boolean.parseBoolean(valueStr));
                    } else {
                        sender.sendMessage("§cUnknown or unsupported setting: " + setting);
                        return true;
                    }
                    plugin.saveConfig();
                    configManager.reload();
                    
                    if (setting.equalsIgnoreCase("fight.broadcast")) {
                        fightManager.updateBossBarPlayers();
                    }
                    
                    sender.sendMessage("§aSuccessfully updated " + setting + " to " + valueStr);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid number format for value: " + valueStr);
                }
            }
            default -> sender.sendMessage("§cUnknown subcommand: " + subCmd);
        }
        return true;
    }

    private void handleTeamCmd(CommandSender sender, String[] args, boolean isTeam1) {
        if (args.length < 3 || (!args[1].equalsIgnoreCase("add") && !args[1].equalsIgnoreCase("remove"))) {
            sender.sendMessage("§eUsage: /fight " + (isTeam1 ? "team1" : "team2") + " <add|remove> <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found: " + args[2]);
            return;
        }
        if (args[1].equalsIgnoreCase("add")) {
            if (isTeam1) {
                fightManager.addToTeam1(target);
                target.sendMessage("§aYou are now on §aTeam1 §awith: §f" + getTeamPlayerNames(fightManager.getTeam1Players()));
            } else {
                fightManager.addToTeam2(target);
                target.sendMessage("§aYou are now on §cTeam2 §awith: §f" + getTeamPlayerNames(fightManager.getTeam2Players()));
            }
            sender.sendMessage("§aAdded §e" + target.getName() + " §ato " + (isTeam1 ? "Team1" : "Team2"));
        } else {
            if (isTeam1) fightManager.removeFromTeam1(target);
            else fightManager.removeFromTeam2(target);
            sender.sendMessage("§cRemoved §e" + target.getName() + " §cfrom " + (isTeam1 ? "Team1" : "Team2"));
        }
    }

    private String getTeamPlayerNames(Set<UUID> team) {
        return team.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .collect(Collectors.joining(", "));
    }
}