// Add new class src/main/java/com/example/LocationTabCompleter.java
package com.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab for /loc: Players.
 */
public class LocationTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (Player p : sender.getServer().getOnlinePlayers()) {
                completions.add(p.getName());
            }
        }
        return completions;
    }
}