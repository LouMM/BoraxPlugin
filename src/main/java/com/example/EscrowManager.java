package com.example;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages item escrow for players who combat log during a fight.
 * Purpose: Sequester items on quit, release on timeout or command.
 * Pattern: Manager/Service with Bukkit Events.
 */
public class EscrowManager implements Listener {
    private final JavaPlugin plugin;
    private final FightManager fightManager;
    private final ConfigManager configManager;
    private final File file;
    private final Map<UUID, EscrowRecord> escrows = new ConcurrentHashMap<>();

    public EscrowManager(JavaPlugin plugin, FightManager fightManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.fightManager = fightManager;
        this.configManager = configManager;
        this.file = new File(plugin.getDataFolder(), "escrow.yml");
        load();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic check for expirations
        new BukkitRunnable() {
            @Override
            public void run() {
                checkExpirations();
            }
        }.runTaskTimer(plugin, 200L, 200L); // Every 10 seconds
    }

    /**
     * Record of sequestered items.
     */
    public static class EscrowRecord {
        public final UUID playerUuid;
        public final long expiryTime;
        public final ItemStack[] inventory;
        public final ItemStack[] enderChest;
        public boolean released;

        public EscrowRecord(UUID playerUuid, long expiryTime, ItemStack[] inventory, ItemStack[] enderChest, boolean released) {
            this.playerUuid = playerUuid;
            this.expiryTime = expiryTime;
            this.inventory = inventory;
            this.enderChest = enderChest;
            this.released = released;
        }

        public Map<String, Object> serialize() {
            Map<String, Object> map = new HashMap<>();
            map.put("playerUuid", playerUuid.toString());
            map.put("expiryTime", expiryTime);
            map.put("inventory", Arrays.asList(inventory));
            map.put("enderChest", Arrays.asList(enderChest));
            map.put("released", released);
            return map;
        }

        @SuppressWarnings("unchecked")
        public static EscrowRecord deserialize(Map<String, Object> map) {
            UUID uuid = UUID.fromString((String) map.get("playerUuid"));
            long expiry = ((Number) map.get("expiryTime")).longValue();
            boolean rel = (boolean) map.getOrDefault("released", false);
            
            List<ItemStack> invList = (List<ItemStack>) map.get("inventory");
            ItemStack[] inv = invList != null ? invList.toArray(new ItemStack[0]) : new ItemStack[0];
            
            List<ItemStack> enderList = (List<ItemStack>) map.get("enderChest");
            ItemStack[] ender = enderList != null ? enderList.toArray(new ItemStack[0]) : new ItemStack[0];
            
            return new EscrowRecord(uuid, expiry, inv, ender, rel);
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section != null) {
                    escrows.put(uuid, EscrowRecord.deserialize(section.getValues(false)));
                }
            } catch (Exception e) {
                TelemetryLogger.error("Loading escrow for " + key, e);
            }
        }
        TelemetryLogger.info("Loaded " + escrows.size() + " escrow records.");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, EscrowRecord> entry : escrows.entrySet()) {
            yaml.createSection(entry.getKey().toString(), entry.getValue().serialize());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            TelemetryLogger.error("Saving escrow.yml", e);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // If fight is active and player is participating
        if (fightManager.getCurrentSessionId() != null && fightManager.isParticipant(uuid)) {
            // If they already have an unreleased escrow, don't overwrite it with an empty inventory
            if (escrows.containsKey(uuid) && !escrows.get(uuid).released) {
                // Clear their current inventory so they don't keep new items either (penalty for logging again)
                player.getInventory().clear();
                player.getEnderChest().clear();
                return;
            }

            // Sequester items
            long timeoutMs = configManager.getEscrowTimeoutSeconds() * 1000L;
            long expiry = System.currentTimeMillis() + timeoutMs;

            ItemStack[] inv = player.getInventory().getContents().clone();
            ItemStack[] ender = player.getEnderChest().getContents().clone();

            EscrowRecord record = new EscrowRecord(uuid, expiry, inv, ender, false);
            escrows.put(uuid, record);

            player.getInventory().clear();
            player.getEnderChest().clear();
            
            save();
            TelemetryLogger.info("Sequestered items for " + player.getName() + " due to combat log.");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        EscrowRecord record = escrows.get(uuid);
        if (record != null) {
            if (player.isDead()) {
                player.sendMessage(ChatColor.YELLOW + "Your escrowed items will be returned when you respawn.");
                return;
            }

            if (record.released) {
                tryRestoreItems(player, record);
            } else if (System.currentTimeMillis() >= record.expiryTime) {
                record.released = true;
                tryRestoreItems(player, record);
            } else {
                long remaining = (record.expiryTime - System.currentTimeMillis()) / 1000;
                player.sendMessage(ChatColor.RED + "Your items are in escrow for combat logging!");
                player.sendMessage(ChatColor.RED + "They will be returned in " + remaining + " seconds.");
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        EscrowRecord record = escrows.get(uuid);
        if (record != null && (record.released || System.currentTimeMillis() >= record.expiryTime)) {
            record.released = true;
            // Delay restoration slightly to ensure player has fully respawned
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        tryRestoreItems(player, record);
                    }
                }
            }.runTaskLater(plugin, 10L);
        }
    }

    private void checkExpirations() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (EscrowRecord record : escrows.values()) {
            if (!record.released && now >= record.expiryTime) {
                record.released = true;
                changed = true;
                Player player = Bukkit.getPlayer(record.playerUuid);
                if (player != null && player.isOnline()) {
                    if (player.isDead()) {
                        player.sendMessage(ChatColor.YELLOW + "Your escrowed items will be returned when you respawn.");
                    } else {
                        tryRestoreItems(player, record);
                    }
                }
            }
        }
        if (changed) save();
    }

    private void tryRestoreItems(Player player, EscrowRecord record) {
        if (player.isDead() || player.getHealth() <= 0) {
            player.sendMessage(ChatColor.YELLOW + "Your escrowed items will be returned when you respawn.");
            return;
        }

        org.bukkit.block.Block block = player.getLocation().getBlock();
        boolean inLiquid = block.getType() == org.bukkit.Material.WATER || block.getType() == org.bukkit.Material.LAVA;
        boolean falling = player.getFallDistance() > 2.0f;

        if (inLiquid || falling) {
            player.sendMessage(ChatColor.YELLOW + "Waiting for a safe location to return your escrowed items...");
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        tryRestoreItems(player, record);
                    }
                }
            }.runTaskLater(plugin, 60L); // Retry in 3 seconds
            return;
        }

        restoreItems(player, record);
    }

    /**
     * Restores items to the player and removes the escrow record.
     * Synchronized to prevent race conditions (e.g., double release).
     */
    private synchronized void restoreItems(Player player, EscrowRecord record) {
        if (!escrows.containsKey(player.getUniqueId())) return; // Already restored
        
        List<ItemStack> leftovers = new ArrayList<>();

        // Restore Inventory (includes armor and offhand)
        ItemStack[] currentInv = player.getInventory().getContents();
        for (int i = 0; i < record.inventory.length; i++) {
            ItemStack item = record.inventory[i];
            if (item == null || item.getType().isAir()) continue;

            if (i < currentInv.length && (currentInv[i] == null || currentInv[i].getType().isAir())) {
                player.getInventory().setItem(i, item);
                currentInv[i] = item; // Update local array to reflect the change
            } else {
                leftovers.add(item);
            }
        }

        // Restore Ender Chest
        ItemStack[] currentEnder = player.getEnderChest().getContents();
        for (int i = 0; i < record.enderChest.length; i++) {
            ItemStack item = record.enderChest[i];
            if (item == null || item.getType().isAir()) continue;

            if (i < currentEnder.length && (currentEnder[i] == null || currentEnder[i].getType().isAir())) {
                player.getEnderChest().setItem(i, item);
                currentEnder[i] = item; // Update local array
            } else {
                leftovers.add(item);
            }
        }

        // Try to add leftovers to inventory
        if (!leftovers.isEmpty()) {
            HashMap<Integer, ItemStack> drops = player.getInventory().addItem(leftovers.toArray(new ItemStack[0]));
            // Try to add remaining to ender chest
            if (!drops.isEmpty()) {
                drops = player.getEnderChest().addItem(drops.values().toArray(new ItemStack[0]));
            }
            // Drop the rest on the ground
            for (ItemStack drop : drops.values()) {
                if (drop != null && !drop.getType().isAir()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }

        escrows.remove(player.getUniqueId());
        save();
        player.sendMessage(ChatColor.GREEN + "Your escrowed items have been returned.");
        TelemetryLogger.info("Restored escrowed items for " + player.getName());
    }

    /**
     * Force release an escrow for a player.
     */
    public synchronized boolean forceRelease(UUID uuid) {
        EscrowRecord record = escrows.get(uuid);
        if (record == null) return false;

        record.released = true;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            if (player.isDead()) {
                player.sendMessage(ChatColor.YELLOW + "Your escrowed items will be returned when you respawn.");
                save();
            } else {
                tryRestoreItems(player, record);
            }
        } else {
            save(); // Will be restored on next join
        }
        return true;
    }

    public EscrowRecord getRecord(UUID uuid) {
        return escrows.get(uuid);
    }
}
