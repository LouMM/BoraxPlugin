// Update to src/main/java/com/example/PersistenceManager.java (fix name scope: use file.getName())
package com.example;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles persistence: Combat records per player YAML files, wins/losses in single file.
 * Loads on enable, saves periodically/on disable; merges in-memory to disk.
 */
public class PersistenceManager {
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File winsFile;
    private final Map<UUID, WinsLosses> winsLossesMap = new ConcurrentHashMap<>();
    private final Map<UUID, List<CombatRecord>> diskRecordsCache = new ConcurrentHashMap<>();  // Loaded on demand for full lookups

    public PersistenceManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "combat");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.winsFile = new File(plugin.getDataFolder(), "wins.yml");
    }

    public void load() {
        if (winsFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(winsFile);
            for (String key : yaml.getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section != null) {
                    winsLossesMap.put(uuid, WinsLosses.deserialize(section.getValues(false)));
                }
            }
        }
        plugin.getLogger().info("Loaded wins/losses for " + winsLossesMap.size() + " players.");
    }

    public void save(CombatCache inMemoryCache) {
        // Save wins
        YamlConfiguration winsYaml = new YamlConfiguration();
        for (var entry : winsLossesMap.entrySet()) {
            winsYaml.createSection(entry.getKey().toString(), entry.getValue().serialize());
        }
        try {
            winsYaml.save(winsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save wins.yml: " + e.getMessage());
        }

        // Merge and save records
        for (var entry : inMemoryCache.getRecordsMap().entrySet()) {
            UUID attacker = entry.getKey();
            List<CombatRecord> newRecords = new ArrayList<>(entry.getValue());
            if (newRecords.isEmpty()) continue;

            List<CombatRecord> diskRecords = loadDiskRecordsForPlayer(attacker);
            diskRecords.addAll(newRecords);
            diskRecords.sort(Comparator.comparingLong(CombatRecord::timestamp).reversed());  // Newest first
            saveDiskRecordsForPlayer(attacker, diskRecords);
        }
        plugin.getLogger().info("Saved persistence data.");
    }

    public List<CombatRecord> loadDiskRecordsForPlayer(UUID playerUUID) {
        File playerFile = getPlayerFile(playerUUID);
        if (!playerFile.exists()) return new ArrayList<>();

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(playerFile);
        List<CombatRecord> records = new ArrayList<>();
        List<Map<?, ?>> rawList = yaml.getMapList("records");
        for (Map<?, ?> raw : rawList) {
            records.add(CombatRecord.deserialize((Map<String, Object>) raw));
        }
        diskRecordsCache.put(playerUUID, records);
        return records;
    }

    private void saveDiskRecordsForPlayer(UUID playerUUID, List<CombatRecord> records) {
        File playerFile = getPlayerFile(playerUUID);
        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> serialized = records.stream().map(CombatRecord::serialize).toList();
        yaml.set("records", serialized);
        try {
            yaml.save(playerFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + playerFile.getName() + ": " + e.getMessage());
        }
    }

    private File getPlayerFile(UUID playerUUID) {
        return new File(dataFolder, playerUUID.toString() + ".yml");
    }

    public void deleteOldRecords(UUID playerUUID, long timespanMs) {
        long cutoff = System.currentTimeMillis() - timespanMs;
        List<CombatRecord> records = loadDiskRecordsForPlayer(playerUUID);
        records.removeIf(record -> record.timestamp() < cutoff);
        saveDiskRecordsForPlayer(playerUUID, records);
        diskRecordsCache.remove(playerUUID);
    }

    public void deleteOldRecordsForAll(long timespanMs) {
        for (File file : Objects.requireNonNull(dataFolder.listFiles((dir, name) -> name.endsWith(".yml")))) {
            String fileName = file.getName();
            UUID uuid;
            try {
                uuid = UUID.fromString(fileName.replace(".yml", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            deleteOldRecords(uuid, timespanMs);
        }
    }

    public Map<UUID, WinsLosses> getWinsLossesMap() {
        return winsLossesMap;
    }

    public WinsLosses getWinsLosses(UUID uuid) {
        return winsLossesMap.getOrDefault(uuid, new WinsLosses(0, 0));
    }

    public void updateWinsLosses(UUID uuid, WinsLosses updated) {
        winsLossesMap.put(uuid, updated);
    }
}