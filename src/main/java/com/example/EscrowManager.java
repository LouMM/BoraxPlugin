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
                restoreItems(player, record);
            } else if (System.currentTimeMillis() >= record.expiryTime) {
                record.released = true;
                restoreItems(player, record);
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
                        restoreItems(player, record);
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
                        restoreItems(player, record);
                    }
                }
            }
        }
        if (changed) save();
    }

    /**
     * Restores items to the player and removes the escrow record.
     * Synchronized to prevent race conditions (e.g., double release).
     */
    private synchronized void restoreItems(Player player, EscrowRecord record) {
        if (!escrows.containsKey(player.getUniqueId())) return; // Already restored
        
        // Add items back, dropping any that don't fit
        HashMap<Integer, ItemStack> leftoverInv = player.getInventory().addItem(record.inventory);
        for (ItemStack item : leftoverInv.values()) {
            if (item != null && !item.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        HashMap<Integer, ItemStack> leftoverEnder = player.getEnderChest().addItem(record.enderChest);
        for (ItemStack item : leftoverEnder.values()) {
            if (item != null && !item.getType().isAir()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
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
                restoreItems(player, record);
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
