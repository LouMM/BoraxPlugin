package com.example;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Persistence: GZIP-compressed YAML for records; wins separate.
 * Adds full involving lookup by scanning all files.
 */
public class PersistenceManager {
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File winsFile;
    private final Map<UUID, WinsLosses> winsLossesMap = new ConcurrentHashMap<>();

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

        // Merge/save records (GZIP)
        for (var entry : inMemoryCache.getRecordsMap().entrySet()) {
            UUID attacker = entry.getKey();
            List<CombatRecord> newRecords = new ArrayList<>(entry.getValue());
            if (newRecords.isEmpty()) continue;

            List<CombatRecord> diskRecords = loadDiskRecordsForPlayer(attacker);
            diskRecords.addAll(newRecords);
            diskRecords.sort(Comparator.comparingLong(CombatRecord::timestamp).reversed());
            saveDiskRecordsForPlayer(attacker, diskRecords);
        }
        plugin.getLogger().info("Saved persistence data.");
    }

    public List<CombatRecord> loadDiskRecordsForPlayer(UUID playerUUID) {
        File playerFile = getPlayerFile(playerUUID);
        if (!playerFile.exists()) return new ArrayList<>();

        try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(playerFile));
             InputStreamReader reader = new InputStreamReader(gis)) {

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(reader);
            List<CombatRecord> records = new ArrayList<>();
            List<Map<?, ?>> rawList = yaml.getMapList("records");

            for (Map<?, ?> raw : rawList) {
                records.add(CombatRecord.deserialize((Map<String, Object>) raw));
            }
            return records;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load " + playerFile.getName() + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveDiskRecordsForPlayer(UUID playerUUID, List<CombatRecord> records) {
        File playerFile = getPlayerFile(playerUUID);

        YamlConfiguration yaml = new YamlConfiguration();
        List<Map<String, Object>> serialized = records.stream()
                .map(CombatRecord::serialize)
                .toList();
        yaml.set("records", serialized);

        // Convert to string first (this is what YamlConfiguration.save(File) does internally)
        String yamlString = yaml.saveToString();

        try (FileOutputStream fos = new FileOutputStream(playerFile);
             GZIPOutputStream gos = new GZIPOutputStream(fos);
             OutputStreamWriter writer = new OutputStreamWriter(gos)) {

            writer.write(yamlString);
            writer.flush();           // Important for GZIP
            gos.finish();             // Finalize GZIP stream

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + playerFile.getName() + ": " + e.getMessage());
        }
    }

    private File getPlayerFile(UUID playerUUID) {
        return new File(dataFolder, playerUUID.toString() + ".gz");
    }

    public void deleteOldRecords(UUID playerUUID, long timespanMs) {
        long cutoff = timespanMs == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - timespanMs;
        
        for (File file : Objects.requireNonNull(dataFolder.listFiles((dir, name) -> name.endsWith(".gz")))) {
            String fileName = file.getName();
            UUID attackerUUID;
            try {
                attackerUUID = UUID.fromString(fileName.replace(".gz", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            
            List<CombatRecord> records = loadDiskRecordsForPlayer(attackerUUID);
            boolean changed = records.removeIf(record -> 
                (record.attackerUUID().equals(playerUUID) || record.victimUUID().equals(playerUUID)) 
                && record.timestamp() < cutoff
            );
            
            if (changed) {
                saveDiskRecordsForPlayer(attackerUUID, records);
            }
        }
    }

    public void deleteOldRecordsForAll(long timespanMs) {
        long cutoff = timespanMs == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - timespanMs;
        for (File file : Objects.requireNonNull(dataFolder.listFiles((dir, name) -> name.endsWith(".gz")))) {
            String fileName = file.getName();
            UUID uuid;
            try {
                uuid = UUID.fromString(fileName.replace(".gz", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            
            List<CombatRecord> records = loadDiskRecordsForPlayer(uuid);
            boolean changed = records.removeIf(record -> record.timestamp() < cutoff);
            
            if (changed) {
                saveDiskRecordsForPlayer(uuid, records);
            }
        }
    }

    public List<CombatRecord> getFullRecordsInvolvingPlayer(UUID targetUUID) {
        List<CombatRecord> involving = new ArrayList<>();
        for (File file : Objects.requireNonNull(dataFolder.listFiles((dir, name) -> name.endsWith(".gz")))) {
            String fileName = file.getName();
            UUID attackerUUID;
            try {
                attackerUUID = UUID.fromString(fileName.replace(".gz", ""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            List<CombatRecord> records = loadDiskRecordsForPlayer(attackerUUID);
            for (CombatRecord record : records) {
                if (record.attackerUUID().equals(targetUUID) || record.victimUUID().equals(targetUUID)) {
                    involving.add(record);
                }
            }
        }
        involving.sort(Comparator.comparingLong(CombatRecord::timestamp).reversed());
        return involving;
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

    public void resetWinsLosses(UUID uuid) {
        winsLossesMap.remove(uuid);
    }

    public void resetAllWinsLosses() {
        winsLossesMap.clear();
    }
}