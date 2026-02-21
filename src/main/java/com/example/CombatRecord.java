// Update src/main/java/com/example/CombatRecord.java (implements ConfigurationSerializable for YAML)
package com.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PvP event record: Serializable for persistence.
 * Includes all data + timestamp for history/filtering.
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
) implements ConfigurationSerializable {

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("attackerUUID", attackerUUID.toString());
        map.put("attackerName", attackerName);
        map.put("victimUUID", victimUUID.toString());
        map.put("victimName", victimName);
        map.put("weaponMaterial", weaponMaterial.name());
        map.put("hitBodyPart", hitBodyPart);
        map.put("hitLocation", hitLocation.serialize());
        map.put("damageAmount", damageAmount);
        map.put("isFatalKill", isFatalKill);
        map.put("wasVictimBlocking", wasVictimBlocking);
        map.put("victimArmorTier", victimArmorTier);
        map.put("fightSessionId", fightSessionId != null ? fightSessionId.toString() : null);
        map.put("timestamp", timestamp);
        return map;
    }

    public static CombatRecord deserialize(Map<String, Object> map) {
        return new CombatRecord(
                UUID.fromString((String) map.get("attackerUUID")),
                (String) map.get("attackerName"),
                UUID.fromString((String) map.get("victimUUID")),
                (String) map.get("victimName"),
                Material.valueOf((String) map.get("weaponMaterial")),
                (String) map.get("hitBodyPart"),
                Location.deserialize((Map<String, Object>) map.get("hitLocation")),
                (double) map.get("damageAmount"),
                (boolean) map.get("isFatalKill"),
                (boolean) map.get("wasVictimBlocking"),
                (int) map.get("victimArmorTier"),
                map.get("fightSessionId") != null ? UUID.fromString((String) map.get("fightSessionId")) : null,
                (long) map.get("timestamp")
        );
    }
}