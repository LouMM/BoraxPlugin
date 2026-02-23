// Add new class src/main/java/com/example/LocationTabCompleter.java
package com.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

/**
 * Tab for /loc: Players (online and offline).
 */
public class LocationTabCompleter implements TabCompleter {
    private final NameUuidManager nameUuidManager;

    public LocationTabCompleter(NameUuidManager nameUuidManager) {
        this.nameUuidManager = nameUuidManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("all-onlineonly".toLowerCase().startsWith(partial)) completions.add("All-OnlineOnly");
            if ("all".toLowerCase().startsWith(partial)) completions.add("All");
            for (String name : nameUuidManager.getAllKnownNames()) {
                if (name.toLowerCase().startsWith(partial)) {
                    completions.add(name);
                }
            }
        }
        return completions;
    }
}