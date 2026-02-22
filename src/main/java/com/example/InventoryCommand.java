package com.example;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /inventory command – works for online AND offline players.
 * Uses pure Mojang mappings (correct for Paper 1.21.11 dev bundle).
 */
public class InventoryCommand implements CommandExecutor {
    private final PlayerLocs plugin;
    private final NameUuidManager nameUuidManager;

    public InventoryCommand(PlayerLocs plugin, NameUuidManager nameUuidManager) {
        this.plugin = plugin;
        this.nameUuidManager = nameUuidManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("inventory.use")) {
            sender.sendMessage(ChatColor.RED + "No permission!");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /inventory <player/uuid/all> [world=world]");
            return true;
        }

        String targetStr = args[0];
        String worldName = args.length > 1 ? args[1] : "world";
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            sender.sendMessage(ChatColor.RED + "Invalid world: " + worldName);
            return true;
        }

        if (targetStr.equalsIgnoreCase("all")) {
            for (Player p : Bukkit.getOnlinePlayers()) displayInventory(sender, p);
        } else {
            UUID uuid = nameUuidManager.getUuidFromName(targetStr);
            if (uuid == null) {
                try { uuid = UUID.fromString(targetStr); }
                catch (IllegalArgumentException e) {
                    sender.sendMessage(ChatColor.RED + "Player/UUID not found: " + targetStr);
                    return true;
                }
            }
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) displayInventory(sender, player);
            else loadOfflineInventory(sender, uuid, worldName);
        }
        return true;
    }

    private void displayInventory(CommandSender sender, Player player) {
        sender.sendMessage(ChatColor.GOLD + "=== " + player.getName() + "'s Inventory ===");
        JsonObject json = new JsonObject();
        json.addProperty("avatar", player.getName());
        json.addProperty("name", player.getName());
        JsonArray invArray = new JsonArray();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                invArray.add(itemToJson(item));
                sendPrettyItem(sender, item, false);
            }
        }
        for (ItemStack item : player.getEnderChest()) {
            if (item != null && !item.getType().isAir()) {
                invArray.add(itemToJson(item, true));
                sendPrettyItem(sender, item, true);
            }
        }
        json.add("inventory", invArray);
        plugin.getLogger().info(json.toString());
    }

    private void loadOfflineInventory(CommandSender sender, UUID uuid, String worldName) {
        File playerDataFile = new File(Bukkit.getWorldContainer(), "world/playerdata/" + uuid + ".dat");
        if (!playerDataFile.exists()) {
            sender.sendMessage(ChatColor.RED + "No data file for " + uuid);
            return;
        }

        try {
            CompoundTag nbt = NbtIo.readCompressed(playerDataFile.toPath(), NbtAccounter.unlimitedHeap());
            String pName = nameUuidManager.getNameFromUuid(uuid);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            sender.sendMessage(ChatColor.GOLD + "=== Offline " + pName + "'s Inventory ===");

            JsonObject json = new JsonObject();
            json.addProperty("avatar", pName);
            json.addProperty("name", pName);
            JsonArray invArray = new JsonArray();

            // ---------- helper to convert vanilla item NBT -> Bukkit ItemStack ----------
            java.util.function.Function<CompoundTag, org.bukkit.inventory.ItemStack> nbtToBukkit =
                    itemNbt -> {
                        // Vanilla player.dat item layout: id (string), Count (byte), tag (compound), Slot (byte) etc.
                        String id = String.valueOf(itemNbt.getString("id"));
                        if (id == null || id.isEmpty()) return null;

                        Optional<Byte> count = itemNbt.getByte("Count");
                        //if (count <= 0) count = 1;

                        // Turn "minecraft:stone" into a Bukkit Material
                        Material mat = Material.matchMaterial(id.replace("minecraft:", ""));
                        if (mat == null || mat.isAir()) return null;

                        Map<String, Object> map = new HashMap<>();
                        map.put("type", mat.name());
                        map.put("amount", count);

                        // You can optionally read more from itemNbt.getCompound("tag") and add to map

                        return org.bukkit.inventory.ItemStack.deserialize(map);
                    };

            // ---------- Main inventory ----------
            Optional<ListTag> invOpt = nbt.getList("Inventory"); // Optional<ListTag> in your mappings
            if (invOpt.isPresent()) {
                ListTag invList = invOpt.get();

                for (int i = 0; i < invList.size(); i++) {
                    Optional<CompoundTag> itemOpt = invList.getCompound(i); // Optional<CompoundTag> in your mappings
                    if (itemOpt.isEmpty()) continue;
                    CompoundTag itemNbt = itemOpt.get();

                    org.bukkit.inventory.ItemStack item = nbtToBukkit.apply(itemNbt);

                    if (item != null && !item.getType().isAir()) {
                        invArray.add(itemToJson(item));
                        sendPrettyItem(sender, item, false);
                    }
                }
            }

            // ---------- Ender chest ----------
            Optional<ListTag> enderOpt = nbt.getList("EnderItems");
            if (enderOpt.isPresent()) {
                ListTag enderList = enderOpt.get();

                for (int i = 0; i < enderList.size(); i++) {
                    Optional<CompoundTag> itemOpt = enderList.getCompound(i);
                    if (itemOpt.isEmpty()) continue;
                    CompoundTag itemNbt = itemOpt.get();

                    org.bukkit.inventory.ItemStack item = nbtToBukkit.apply(itemNbt);

                    if (item != null && !item.getType().isAir()) {
                        invArray.add(itemToJson(item, true));
                        sendPrettyItem(sender, item, true);
                    }
                }
            }

            json.add("inventory", invArray);
            plugin.getLogger().info(json.toString());

        } catch (IOException e) {
            sender.sendMessage(ChatColor.RED + "Error loading data: " + e.getMessage());
        }
    }




    private void sendPrettyItem(CommandSender sender, ItemStack item, boolean isEnder) {
        String prefix = isEnder ? "§5[Ender] " : "§6[Inv] ";
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName() : item.getType().name();
        String enchantStr = (item.hasItemMeta() && item.getItemMeta().hasEnchants()) ? " §d(*Enchanted*)" : "";
        sender.sendMessage(prefix + "§8[§e" + item.getAmount() + "x§8] §b" + name + enchantStr);
    }

    private JsonObject itemToJson(ItemStack item, boolean ender) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", item.getType().name());
        obj.addProperty("amount", item.getAmount());
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            JsonArray enchants = new JsonArray();
            item.getItemMeta().getEnchants().forEach((ench, level) -> {
                JsonObject enchObj = new JsonObject();
                enchObj.addProperty("enchant", ench.getKey().getKey());
                enchObj.addProperty("level", level);
                enchants.add(enchObj);
            });
            obj.add("enchants", enchants);
        }
        obj.addProperty("ender", ender);
        return obj;
    }

    private JsonObject itemToJson(ItemStack item) {
        return itemToJson(item, false);
    }
}