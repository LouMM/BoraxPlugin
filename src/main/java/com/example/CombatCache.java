// Update src/main/java/com/example/CombatCache.java (trim after save; provide method to clear after persist)
package com.example;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * In-memory recent records: Trims to max per player; cleared/trimmed after persist.
 */
public class CombatCache {
    private final ConcurrentHashMap<UUID, Deque<CombatRecord>> attackerToRecords = new ConcurrentHashMap<>();
    private static final int MAX_PER_PLAYER = 50;

    public void addRecord(CombatRecord record) {
        Deque<CombatRecord> records = attackerToRecords.computeIfAbsent(record.attackerUUID(), k -> new ConcurrentLinkedDeque<>());
        records.addFirst(record);
        trimToSize(records);
    }

    private void trimToSize(Deque<CombatRecord> deque) {
        while (deque.size() > MAX_PER_PLAYER) {
            deque.removeLast();
        }
    }

    public List<CombatRecord> getRecentHitsByAttacker(UUID attackerUUID, int limit) {
        Deque<CombatRecord> records = attackerToRecords.get(attackerUUID);
        if (records == null) return List.of();
        return new ArrayList<>(records).stream().limit(limit).collect(Collectors.toList());
    }

    public List<CombatRecord> getRecordsInvolvingPlayer(UUID playerUUID, int limit) {
        List<CombatRecord> allRecords = new ArrayList<>();
        allRecords.addAll(getRecentHitsByAttacker(playerUUID, limit * 2));
        for (var entry : attackerToRecords.entrySet()) {
            for (CombatRecord record : entry.getValue()) {
                if (record.victimUUID().equals(playerUUID)) {
                    allRecords.add(record);
                }
            }
        }
        allRecords.sort(Comparator.comparingLong(CombatRecord::timestamp).reversed());
        return allRecords.stream().distinct().limit(limit).collect(Collectors.toList());
    }

    public ConcurrentHashMap<UUID, Deque<CombatRecord>> getRecordsMap() {
        return attackerToRecords;
    }

    /**
     * Clears in-memory after persist (user req: clear periodically to free VM).
     */
    public void clearAfterPersist() {
        attackerToRecords.clear();
    }
}