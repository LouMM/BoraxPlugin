// Step 2: Update src/main/java/com/example/CombatCache.java (add timestamp sorting in lookups)
package com.example;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory cache: Attacker UUID -> Deque of recent records (newest first, auto-trim to 100).
 * Supports lookup by attacker or involving player (hits by/on them).
 */
public class CombatCache {
    private final ConcurrentHashMap<UUID, Deque<CombatRecord>> attackerToRecords = new ConcurrentHashMap<>();

    public void addRecord(CombatRecord record) {
        Deque<CombatRecord> records = attackerToRecords.computeIfAbsent(record.attackerUUID(), k -> new ConcurrentLinkedDeque<>());
        records.addFirst(record);  // Newest first
        trimToSize(records, 100);
    }

    private void trimToSize(Deque<CombatRecord> deque, int maxSize) {
        while (deque.size() > maxSize) {
            deque.removeLast();  // Remove oldest
        }
    }

    public List<CombatRecord> getRecentHitsByAttacker(UUID attackerUUID, int limit) {
        Deque<CombatRecord> records = attackerToRecords.get(attackerUUID);
        if (records == null) return List.of();
        return new ArrayList<>(records).stream()
                .limit(limit)  // Deque newest first, so limit gives recent
                .collect(Collectors.toList());
    }

    public List<CombatRecord> getRecordsInvolvingPlayer(UUID playerUUID, int limit) {
        List<CombatRecord> allRecords = new ArrayList<>();
        // Hits by this player
        allRecords.addAll(getRecentHitsByAttacker(playerUUID, limit * 2));  // Oversample
        // Hits on this player (scan attacker's records)
        for (var entry : attackerToRecords.entrySet()) {
            for (CombatRecord record : entry.getValue()) {
                if (record.victimUUID().equals(playerUUID)) {
                    allRecords.add(record);
                }
            }
        }
        // Sort newest first, dedup/trim
        allRecords.sort(Comparator.comparingLong(CombatRecord::timestamp).reversed());
        return allRecords.stream().distinct().limit(limit).collect(Collectors.toList());
    }

    public ConcurrentHashMap<UUID, Deque<CombatRecord>> getRecordsMap() {
        return attackerToRecords;
    }
}