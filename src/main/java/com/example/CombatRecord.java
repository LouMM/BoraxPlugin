// Step 1: Update src/main/java/com/example/CombatRecord.java (add timestamp field for sorting kills/hits chronologically)
package com.example;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.UUID;

/**
 * Immutable PvP hit/kill record: Stores all data for caching, scoring, lookup.
 * Timestamp enables recent-first sorting; sessionId filters fight-only events.
 */
public record CombatRecord(
        UUID attackerUUID,
        String attackerName,
        UUID victimUUID,
        String victimName,
        Material weaponMaterial,
        String hitBodyPart,
        Location hitLocation,
        double damageAmount,
        boolean isFatalKill,
        boolean wasVictimBlocking,
        int victimArmorTier,
        UUID fightSessionId,
        long timestamp
) {}