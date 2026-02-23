// Update to src/main/java/com/example/FightTabCompleter.java (add 'list' to completions)
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab for /fight: Includes 'list' subcmd.
 */
public class FightTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("team1", "team2", "start", "end", "clear", "scores", "status", "reload", "toggle", "list", "config"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("team1") || args[0].equalsIgnoreCase("team2")) {
                completions.addAll(List.of("add", "remove"));
            } else if (args[0].equalsIgnoreCase("toggle")) {
                completions.addAll(List.of("combat", "fight"));
            } else if (args[0].equalsIgnoreCase("config")) {
                completions.addAll(List.of(
                        "fightDefaultDurationSeconds",
                        "escrowTimeoutSeconds",
                        "autoFight.hitCount",
                        "autoFight.timeWindowSeconds",
                        "fight.penaltyMode",
                        "fight.broadcast",
                        "fight.KeepInventoryDuringFight",
                        "fight.KeepInventoryFightEnd",
                        "scoring.hitDamageMultiplier",
                        "scoring.blockHitterPoints",
                        "scoring.blockBlockerPoints",
                        "scoring.killBasePoints",
                        "scoring.armorBonusPerTier",
                        "scoring.weakWeaponBonusPerTier"
                ));
            }
        } else if (args.length == 3) {
            if ((args[0].equalsIgnoreCase("team1") || args[0].equalsIgnoreCase("team2"))
                    && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                String partial = args[2].toLowerCase();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(partial)) {
                        completions.add(p.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("config")) {
                if (args[1].equalsIgnoreCase("fightDefaultDurationSeconds")) completions.add("600");
                else if (args[1].equalsIgnoreCase("escrowTimeoutSeconds")) completions.add("300");
                else if (args[1].equalsIgnoreCase("autoFight.hitCount")) completions.add("3");
                else if (args[1].equalsIgnoreCase("autoFight.timeWindowSeconds")) completions.add("10");
                else if (args[1].equalsIgnoreCase("fight.penaltyMode")) completions.addAll(List.of("STEAL", "DEATH", "NONE"));
                else if (args[1].equalsIgnoreCase("fight.broadcast")) completions.addAll(List.of("true", "false"));
                else if (args[1].equalsIgnoreCase("fight.KeepInventoryDuringFight") || args[1].equalsIgnoreCase("fight.KeepInventoryFightEnd")) completions.addAll(List.of("true", "false"));
                else if (args[1].equalsIgnoreCase("scoring.hitDamageMultiplier")) completions.add("2.0");
                else if (args[1].equalsIgnoreCase("scoring.blockHitterPoints")) completions.add("1");
                else if (args[1].equalsIgnoreCase("scoring.blockBlockerPoints")) completions.add("5");
                else if (args[1].equalsIgnoreCase("scoring.killBasePoints")) completions.add("50");
                else if (args[1].equalsIgnoreCase("scoring.armorBonusPerTier")) completions.add("10");
                else if (args[1].equalsIgnoreCase("scoring.weakWeaponBonusPerTier")) completions.add("15");
            }
        }
        return completions;
    }
}