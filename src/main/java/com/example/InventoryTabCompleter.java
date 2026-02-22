// Add new class src/main/java/com/example/InventoryTabCompleter.java
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab for /inventory: Players (online and offline), all, worlds.
 */
public class InventoryTabCompleter implements TabCompleter {
    private final NameUuidManager nameUuidManager;

    public InventoryTabCompleter(NameUuidManager nameUuidManager) {
        this.nameUuidManager = nameUuidManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("all".startsWith(partial)) {
                completions.add("all");
            }
            for (String name : nameUuidManager.getAllKnownNames()) {
                if (name.toLowerCase().startsWith(partial)) {
                    completions.add(name);
                }
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            for (String world : List.of("world", "world_nether", "world_the_end")) {
                if (world.startsWith(partial)) {
                    completions.add(world);
                }
            }
        }
        return completions;
    }
}