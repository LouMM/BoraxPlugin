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
            completions.addAll(List.of("team1", "team2", "start", "end", "clear", "scores", "status", "reload", "toggle", "list"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("team1") || args[0].equalsIgnoreCase("team2")) {
                completions.addAll(List.of("add", "remove"));
            } else if (args[0].equalsIgnoreCase("toggle")) {
                completions.addAll(List.of("combat", "fight"));
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("team1") || args[0].equalsIgnoreCase("team2"))
                && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            String partial = args[2].toLowerCase();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}