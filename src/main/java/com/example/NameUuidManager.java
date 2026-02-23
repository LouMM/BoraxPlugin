// Update to src/main/java/com/example/NameUuidManager.java
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists UUID <-> Name map with a fallback to the server cache.
 */
public class NameUuidManager {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, String> uuidToName = new HashMap<>();
    private final Map<String, UUID> nameToUuid = new HashMap<>();

    public NameUuidManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "names.yml");
        load();
    }

    private void load() {
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String key : yaml.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String name = yaml.getString(key);
                    if (name != null) {
                        uuidToName.put(uuid, name);
                        nameToUuid.put(name.toLowerCase(), uuid);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID found in names.yml: " + key);
                }
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : uuidToName.entrySet()) {
            yaml.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save names.yml!", e);
        }
    }

    public void addOrUpdate(UUID uuid, String name) {
        String lowerName = name.toLowerCase();
        UUID existing = nameToUuid.get(lowerName);
        if (existing != null && !existing.equals(uuid)) {
            nameToUuid.remove(lowerName);
            uuidToName.remove(existing);
        }
        uuidToName.put(uuid, name);
        nameToUuid.put(lowerName, uuid);
    }

    public java.util.Set<UUID> getAllUuids() {
        return new java.util.HashSet<>(uuidToName.keySet());
    }

    public UUID getUuidFromName(String name) {
        // 1. Check our local fast cache first
        UUID localResult = nameToUuid.get(name.toLowerCase());
        if (localResult != null) {
            return localResult;
        }

        // 2. Fallback to server's internal usercache.json (non-blocking)
        OfflinePlayer offlinePlayer = Bukkit.getServer().getOfflinePlayerIfCached(name);
        if (offlinePlayer != null && (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline())) {
            // Add it to our local cache for next time
            addOrUpdate(offlinePlayer.getUniqueId(), offlinePlayer.getName());
            return offlinePlayer.getUniqueId();
        }

        return null; // Player truly not found
    }

    public String getNameFromUuid(UUID uuid) {
        // 1. Check local cache
        String localName = uuidToName.get(uuid);
        if (localName != null) {
            return localName;
        }

        // 2. Fallback to server cache
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() != null) {
            addOrUpdate(uuid, offlinePlayer.getName());
            return offlinePlayer.getName();
        }

        return "UnknownPlayer";
    }

    /**
     * Get all known player names for tab completion.
     * @return A set of all known player names.
     */
    public java.util.Set<String> getAllKnownNames() {
        return new java.util.HashSet<>(uuidToName.values());
    }
}