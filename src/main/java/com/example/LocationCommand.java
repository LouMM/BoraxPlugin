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
        try {
            if (!sender.hasPermission("playerlocs.use")) {
                sender.sendMessage(ChatColor.RED + "No permission!");
                return true;
            }

            if (args.length < 1) {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /loc <player/uuid>");
                return true;
            }

            String targetStr = args[0];
            UUID uuid = nameUuidManager.getUuidFromName(targetStr);
            if (uuid == null) {
                try { 
                    uuid = UUID.fromString(targetStr); 
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Player/UUID not found: " + targetStr);
                    return true;
                }
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                String locMsg = String.format("%s's Location: %.2f, %.2f, %.2f in %s", 
                    player.getName(), player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(), player.getLocation().getWorld().getName());
                sender.sendMessage(ChatColor.GOLD + locMsg);
                TelemetryLogger.info("API/RCON Location Result: " + locMsg);
            } else {
                loadOfflineLocation(sender, uuid);
            }
        } catch (Exception e) {
            TelemetryLogger.error("LocationCommand execution", e);
            sender.sendMessage(ChatColor.RED + "An error occurred while executing the command.");
        }
        return true;
    }

    private void loadOfflineLocation(CommandSender sender, UUID uuid) {
        File playerDataFile = new File(Bukkit.getWorldContainer(), "world/playerdata/" + uuid + ".dat");
        if (!playerDataFile.exists()) {
            String msg = "No data file for " + uuid;
            sender.sendMessage(ChatColor.RED + msg);
            TelemetryLogger.warning("API/RCON Location Result: " + msg);
            return;
        }

        try {
            CompoundTag nbt = NbtIo.readCompressed(playerDataFile.toPath(), NbtAccounter.unlimitedHeap());

            ListTag posList = nbt.getListOrEmpty("Pos");
            if (!posList.isEmpty() && posList.size() >= 3) {
                double x_value = posList.getDoubleOr(0, 0.0);
                double y_value = posList.getDoubleOr(1, 0.0);
                double z_value = posList.getDoubleOr(2, 0.0);
                
                String dimension = nbt.getStringOr("Dimension", "minecraft:overworld");
                String name = nameUuidManager.getNameFromUuid(uuid);
                String displayName = name != null ? name : uuid.toString();

                String locMsg = String.format("Offline %s Location: %.2f, %.2f, %.2f in %s", 
                    displayName, x_value, y_value, z_value, dimension.replace("minecraft:", ""));
                
                sender.sendMessage(ChatColor.GOLD + locMsg);
                TelemetryLogger.info("API/RCON Location Result: " + locMsg);
            }
        } catch (IOException e) {
            TelemetryLogger.error("Reading NBT for offline player " + uuid, e);
            sender.sendMessage(ChatColor.RED + "Error reading NBT data.");
        } catch (Exception e) {
            TelemetryLogger.error("Unexpected error in loadOfflineLocation", e);
            sender.sendMessage(ChatColor.RED + "An unexpected error occurred.");
        }
    }
}