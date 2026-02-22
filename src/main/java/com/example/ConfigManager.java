// Step 5: Add new class src/main/java/com/example/ConfigManager.java
// Abstracts all settings to config.yml; overridable by OP commands or file edits + /fight reload
package com.example;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Centralized config loader: Provides getters for feature gates, timings, scoring heuristics.
 * Reloadable via command; defaults populated on first run.
 */
public class ConfigManager {
    private final JavaPlugin plugin;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    // Feature gates
    public boolean isCombatTrackingEnabled() {
        return plugin.getConfig().getBoolean("enableCombatTracking", true);
    }

    public boolean isFightModeEnabled() {
        return plugin.getConfig().getBoolean("enableFightMode", true);
    }

    // Fight settings
    public long getDefaultFightDurationSeconds() {
        return plugin.getConfig().getLong("fightDefaultDurationSeconds", 600L);
    }

    public long getEscrowTimeoutSeconds() {
        return plugin.getConfig().getLong("escrowTimeoutSeconds", 300L);
    }

    public int getAutoFightHitCount() {
        return plugin.getConfig().getInt("autoFight.hitCount", 3);
    }

    public int getAutoFightTimeWindowSeconds() {
        return plugin.getConfig().getInt("autoFight.timeWindowSeconds", 10);
    }

    public String getFightPenaltyMode() {
        return plugin.getConfig().getString("fight.penaltyMode", "STEAL").toUpperCase();
    }

    public boolean isFightBroadcastEnabled() {
        return plugin.getConfig().getBoolean("fight.broadcast", true);
    }

    public boolean isKeepInventoryDuringFightEnabled() {
        return plugin.getConfig().getBoolean("fight.KeepInventoryDuringFight", true);
    }

    public boolean isKeepInventoryFightEndEnabled() {
        return plugin.getConfig().getBoolean("fight.KeepInventoryFightEnd", false);
    }

    // Scoring heuristics
    public double getHitDamageMultiplier() {
        return plugin.getConfig().getDouble("scoring.hitDamageMultiplier", 2.0);
    }

    public int getBlockHitterPoints() {
        return plugin.getConfig().getInt("scoring.blockHitterPoints", 1);
    }

    public int getBlockBlockerPoints() {
        return plugin.getConfig().getInt("scoring.blockBlockerPoints", 5);
    }

    public int getKillBasePoints() {
        return plugin.getConfig().getInt("scoring.killBasePoints", 50);
    }

    public int getArmorBonusPerTier() {
        return plugin.getConfig().getInt("scoring.armorBonusPerTier", 10);
    }

    public int getWeakWeaponBonusPerTier() {
        return plugin.getConfig().getInt("scoring.weakWeaponBonusPerTier", 15);
    }

    // High-value items for end-game theft
    public List<String> getHighValueMaterials() {
        return plugin.getConfig().getStringList("highValueItems");
    }

    public boolean isHighValueMaterial(String materialName) {
        return getHighValueMaterials().stream()
                .anyMatch(mat -> mat.equalsIgnoreCase(materialName));
    }
}