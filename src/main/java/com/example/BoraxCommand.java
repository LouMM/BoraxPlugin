// Add new class src/main/java/com/example/BoraxCommand.java
package com.example;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Objects;

/**
 * /borax help: Lists all plugin commands and usage.
 */
public class BoraxCommand implements CommandExecutor {
    private final PlayerLocs plugin;

    public BoraxCommand(PlayerLocs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("help")) {
            sender.sendMessage("§eUsage: /borax help");
            return true;
        }

        sender.sendMessage("§aBorax Plugin Commands:");
        sender.sendMessage("§e/loc §f- " + Objects.requireNonNull(plugin.getCommand("loc")).getDescription() + " §7" + Objects.requireNonNull(plugin.getCommand("loc")).getUsage());
        sender.sendMessage("§e/fight §f- " + Objects.requireNonNull(plugin.getCommand("fight")).getDescription() + " §7" + Objects.requireNonNull(plugin.getCommand("fight")).getUsage());
        sender.sendMessage("§e/combat §f- " + Objects.requireNonNull(plugin.getCommand("combat")).getDescription() + " §7" + Objects.requireNonNull(plugin.getCommand("combat")).getUsage());
        sender.sendMessage("§e/inventory §f- " + Objects.requireNonNull(plugin.getCommand("inventory")).getDescription() + " §7" + Objects.requireNonNull(plugin.getCommand("inventory")).getUsage());
        return true;
    }
}