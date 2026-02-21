// Step 4: Update src/main/java/com/example/ScoringEngine.java (complete armor tiers, add session-filtered scoring)
package com.example;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Heuristic scoring from CombatRecords: Rewards underdog weapons/armor kills, blocks, damage.
 * Filters to fight session; configurable via ConfigManager.
 */
public class ScoringEngine {
    private final ConfigManager config;

    public ScoringEngine(ConfigManager config) {
        this.config = config;
    }

    /**
     * Calculates team scores from cache records in specific fight session only.
     */
    public ScorePair calculateSessionScores(ConcurrentHashMap<UUID, Deque<CombatRecord>> recordsMap, UUID sessionId, Set<UUID> team1, Set<UUID> team2) {
        int team1Score = 0;
        int team2Score = 0;
        for (var entry : recordsMap.entrySet()) {
            UUID attackerId = entry.getKey();
            boolean attackerTeam1 = team1.contains(attackerId);
            for (CombatRecord record : entry.getValue()) {
                // Filter to this fight session
                if (record.fightSessionId() == null || !record.fightSessionId().equals(sessionId)) continue;
                boolean victimTeam1 = team1.contains(record.victimUUID());
                // Hit points to attacker
                int hitPoints = getHitPoints(record);
                if (attackerTeam1) team1Score += hitPoints; else team2Score += hitPoints;
                // Kill bonus to killer
                if (record.isFatalKill()) {
                    int killPoints = getKillPoints(record);
                    if (attackerTeam1) team1Score += killPoints; else team2Score += killPoints;
                }
                // Block bonuses
                if (record.wasVictimBlocking()) {
                    int hitterBonus = config.getBlockHitterPoints();
                    int blockerBonus = config.getBlockBlockerPoints();
                    if (attackerTeam1) team1Score += hitterBonus; else team2Score += hitterBonus;
                    if (victimTeam1) team1Score += blockerBonus; else team2Score += blockerBonus;
                }
            }
        }
        return new ScorePair(team1Score, team2Score);
    }

    private int getHitPoints(CombatRecord record) {
        return (int) (record.damageAmount() * config.getHitDamageMultiplier());
    }

    private int getKillPoints(CombatRecord record) {
        int base = config.getKillBasePoints();
        int armorBonus = record.victimArmorTier() * config.getArmorBonusPerTier();
        int weaponUnderdogBonus = (6 - getWeaponTier(record.weaponMaterial())) * config.getWeakWeaponBonusPerTier();
        return base + armorBonus + weaponUnderdogBonus;
    }

    private static int getWeaponTier(Material material) {
        return switch (material) {
            case STICK -> 1;
            case WOODEN_SWORD, WOODEN_AXE -> 1;
            case STONE_SWORD, STONE_AXE -> 2;
            case GOLDEN_SWORD, GOLDEN_AXE -> 2;
            case IRON_SWORD, IRON_AXE -> 3;
            case DIAMOND_SWORD, DIAMOND_AXE -> 5;
            case NETHERITE_SWORD, NETHERITE_AXE -> 6;
            default -> 0;  // Hand/air
        };
    }

    /**
     * Computes average armor tier (0-6) of victim at hit/kill time.
     */
    public static int calculateAverageArmorTier(Player player) {
        var armor = player.getInventory().getArmorContents();
        int totalTier = 0, count = 0;
        for (var piece : armor) {
            if (piece != null && piece.getType() != Material.AIR) {
                totalTier += getArmorTier(piece.getType());
                count++;
            }
        }
        return count > 0 ? totalTier / count : 0;
    }

    private static int getArmorTier(Material material) {
        return switch (material) {
            case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS, CARVED_PUMPKIN, PUMPKIN -> 1;
            case CHAINMAIL_HELMET, CHAINMAIL_CHESTPLATE, CHAINMAIL_LEGGINGS, CHAINMAIL_BOOTS -> 2;
            case IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS, GOLDEN_HELMET, GOLDEN_CHESTPLATE, GOLDEN_LEGGINGS, GOLDEN_BOOTS -> 3;
            case TURTLE_HELMET -> 4;
            case DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS -> 5;
            case NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS -> 6;
            default -> 0;
        };
    }
}