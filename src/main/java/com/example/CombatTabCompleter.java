// Add new class src/main/java/com/example/CombatTabCompleter.java
package com.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab for /combat: Subcmds, players, modes, timespans.
 */
public class CombatTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.addAll(List.of("lookup", "delete"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("lookup")) {
                for (Player p : sender.getServer().getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            } else if (args[0].equalsIgnoreCase("delete")) {
                completions.addAll(List.of("all"));
                for (Player p : sender.getServer().getOnlinePlayers()) {
                    completions.add(p.getName());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("lookup")) {
                completions.addAll(List.of("recent", "full"));
            } else if (args[0].equalsIgnoreCase("delete")) {
                completions.addAll(List.of("5d", "30d"));  // Examples
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("lookup")) {
            completions.add("10");  // Limit example
        }
        return completions;
    }
}