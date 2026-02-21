// Add new record src/main/java/com/example/WinsLosses.java (simple serializable record)
package com.example;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-player wins/losses counter: Persisted separately as small.
 */
public record WinsLosses(int wins, int losses) implements ConfigurationSerializable {

    public WinsLosses incrementWins() {
        return new WinsLosses(wins + 1, losses);
    }

    public WinsLosses incrementLosses() {
        return new WinsLosses(wins, losses + 1);
    }

    @Override
    public @NonNull Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("wins", wins);
        map.put("losses", losses);
        return map;
    }

    public static WinsLosses deserialize(Map<String, Object> map) {
        return new WinsLosses((int) map.get("wins"), (int) map.get("losses"));
    }
}