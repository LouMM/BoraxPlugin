package com.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EscrowCommand implements CommandExecutor, TabCompleter {
    private final EscrowManager escrowManager;
    private final NameUuidManager nameUuidManager;

    public EscrowCommand(EscrowManager escrowManager, NameUuidManager nameUuidManager) {
        this.escrowManager = escrowManager;
        this.nameUuidManager = nameUuidManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mcraft.escrow")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /escrow <player> <release|list>");
            return true;
        }

        String targetName = args[0];
        String action = args[1].toLowerCase();

        UUID targetUuid = nameUuidManager.getUuidFromName(targetName);
        if (targetUuid == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + targetName);
            return true;
        }

        EscrowManager.EscrowRecord record = escrowManager.getRecord(targetUuid);
        if (record == null) {
            sender.sendMessage(ChatColor.YELLOW + "No escrow record found for " + targetName + ".");
            return true;
        }

        if (action.equals("release")) {
            if (record.released) {
                sender.sendMessage(ChatColor.YELLOW + targetName + "'s items are already released (waiting for them to join).");
                return true;
            }
            boolean success = escrowManager.forceRelease(targetUuid);
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "Successfully released escrow for " + targetName + ".");
            } else {
                sender.sendMessage(ChatColor.RED + "Failed to release escrow for " + targetName + ".");
            }
        } else if (action.equals("list")) {
            sender.sendMessage(ChatColor.GOLD + "--- Escrow for " + targetName + " ---");
            sender.sendMessage(ChatColor.YELLOW + "Status: " + (record.released ? "Released (Pending Join)" : "Sequestered"));
            long remaining = (record.expiryTime - System.currentTimeMillis()) / 1000;
            if (!record.released && remaining > 0) {
                sender.sendMessage(ChatColor.YELLOW + "Time remaining: " + remaining + " seconds");
            }
            
            int invCount = countItems(record.inventory);
            int enderCount = countItems(record.enderChest);
            
            sender.sendMessage(ChatColor.AQUA + "Inventory Items: " + invCount);
            sender.sendMessage(ChatColor.AQUA + "Ender Chest Items: " + enderCount);
            sender.sendMessage(ChatColor.GOLD + "-----------------------------");
        } else {
            sender.sendMessage(ChatColor.RED + "Unknown action. Use 'release' or 'list'.");
        }

        return true;
    }

    private int countItems(ItemStack[] items) {
        int count = 0;
        if (items != null) {
            for (ItemStack item : items) {
                if (item != null && !item.getType().isAir()) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("mcraft.escrow")) {
            return completions;
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (String name : nameUuidManager.getAllKnownNames()) {
                if (name.toLowerCase().startsWith(partial)) {
                    completions.add(name);
                }
            }
        } else if (args.length == 2) {
            String partial = args[1].toLowerCase();
            if ("release".startsWith(partial)) completions.add("release");
            if ("list".startsWith(partial)) completions.add("list");
        }

        return completions;
    }
}
