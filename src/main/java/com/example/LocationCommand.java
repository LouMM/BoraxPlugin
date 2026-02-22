package com.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * /loc command – works for online AND offline players using Mojang mappings.
 */
public class LocationCommand implements CommandExecutor {
    private final PlayerLocs plugin;
    private final NameUuidManager nameUuidManager;

    public LocationCommand(PlayerLocs plugin, NameUuidManager nameUuidManager) {
        this.plugin = plugin;
        this.nameUuidManager = nameUuidManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playerlocs.use")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }

        if (label.equalsIgnoreCase("locs")) {
            // your original /locs logic here
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /loc <player/uuid>");
            return true;
        }

        String targetStr = args[0];
        UUID uuid = nameUuidManager.getUuidFromName(targetStr);
        if (uuid == null) {
            try { uuid = UUID.fromString(targetStr); }
            catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.RED + "Player/UUID not found: " + targetStr);
                return true;
            }
        }

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            sender.sendMessage(ChatColor.GOLD + player.getName() + "'s Location: " + player.getLocation());
        } else {
            loadOfflineLocation(sender, uuid);
        }
        return true;
    }

    private void loadOfflineLocation(CommandSender sender, UUID uuid) {
        File playerDataFile = new File(Bukkit.getWorldContainer(), "world/playerdata/" + uuid + ".dat");
        if (!playerDataFile.exists()) {
            sender.sendMessage(ChatColor.RED + "No data file for " + uuid);
            return;
        }

        try {
            CompoundTag nbt = NbtIo.readCompressed(playerDataFile.toPath(), NbtAccounter.unlimitedHeap());

            Optional<ListTag> posOpt = nbt.getList("Pos");
            if (posOpt.isPresent()) {
                ListTag posList = posOpt.get();

                Optional<Double> x = posList.getDouble(0);  // [web:6][web:20]
                Optional<Double> y = posList.getDouble(1);  // [web:6][web:20]
                Optional<Double> z = posList.getDouble(2);  // [web:6][web:20]
                if(x.isPresent() && y.isPresent() && z.isPresent()) {
                    Double x_value = x.get();
                    Double y_value = y.get();
                    Double z_value = z.get();
                    Optional<String> s =  nbt.getString("Dimension");
                    if(s.isEmpty()) { return;}
                    String dimension = s.get();
                    String name = nameUuidManager.getNameFromUuid(uuid);

                    sender.sendMessage(ChatColor.GOLD + "=== Offline " + (name != null ? name : uuid) + " ===");
                    sender.sendMessage(ChatColor.YELLOW + "Location: " + String.format("%.2f, %.2f, %.2f", x_value, y_value, z_value));
                    sender.sendMessage(ChatColor.YELLOW + "Dimension: " + dimension.replace("minecraft:", ""));
                }
            }
        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Error reading NBT: " + e.getMessage());
        }
    }
}