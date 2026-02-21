// Step 8: Add new class src/main/java/com/example/FightCommand.java
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles /fight subcommands: team management, start/end, scores, config toggle/reload/status.
 * OP-only; tab-completes players for add/remove.
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
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fight.use")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e/fight <team1|team2|start|end|clear|scores|status|reload|toggle [combat|fight]>");
            return true;
        }
        String subCmd = args[0].toLowerCase();
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
            if (isTeam1) fightManager.addToTeam1(target);
            else fightManager.addToTeam2(target);
            sender.sendMessage("§aAdded §e" + target.getName() + " §ato " + (isTeam1 ? "Team1" : "Team2"));
        } else {
            if (isTeam1) fightManager.removeFromTeam1(target);
            else fightManager.removeFromTeam2(target);
            sender.sendMessage("§cRemoved §e" + target.getName() + " §cfrom " + (isTeam1 ? "Team1" : "Team2"));
        }
    }
}