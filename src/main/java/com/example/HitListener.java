// HitListener.java (fix isBlocked detection, body part approx using eye height)
package com.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Captures PvP hit events: Logs weapon, approx body part (head/torso/legs via relative hit Y), damage, block status, armor.
 * Only records if feature enabled and players involved.
 */
public class HitListener implements Listener {
    private final CombatCache combatCache;
    private final FightManager fightManager;
    private final ConfigManager configManager;

    // Map of VictimUUID -> Map of AttackerUUID -> List of timestamps
    private final Map<UUID, Map<UUID, List<Long>>> recentHits = new HashMap<>();

    public HitListener(CombatCache combatCache, FightManager fightManager, ConfigManager configManager) {
        this.combatCache = combatCache;
        this.fightManager = fightManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!configManager.isCombatTrackingEnabled()) return;
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) return;

        ItemStack weaponStack = attacker.getInventory().getItemInMainHand();
        Material weapon = weaponStack.getType() == Material.AIR ? Material.AIR : weaponStack.getType();

        double damage = event.getFinalDamage();

        Location hitLocation = victim.getLocation().clone();

        // Approx body part using attacker's eye Y relative to victim's feet
        double relY = (attacker.getEyeLocation().getY() - victim.getLocation().getY()) / victim.getHeight();
        String hitBodyPart = relY > 0.65 ? "head" : relY < 0.35 ? "legs" : "torso";  // Tuned thresholds for player model

        // Detect shield block: If final damage 0 and victim blocking (shields nullify melee damage)
        boolean wasVictimBlocking = (damage <= 0.0 && victim.isBlocking());

        int victimArmorTier = ScoringEngine.calculateAverageArmorTier(victim);

        UUID fightSessionId = fightManager.getCurrentSessionId();

        CombatRecord record = new CombatRecord(
                attacker.getUniqueId(), attacker.getName(),
                victim.getUniqueId(), victim.getName(),
                weapon, hitBodyPart, hitLocation, damage, false, wasVictimBlocking, victimArmorTier, fightSessionId,
                System.currentTimeMillis()
        );

        combatCache.addRecord(record);
        checkAutoFight(attacker, victim);
    }

    private void checkAutoFight(Player attacker, Player victim) {
        if (!configManager.isFightModeEnabled()) return;

        int requiredHits = configManager.getAutoFightHitCount();
        if (requiredHits <= 0) return; // Disabled

        long timeWindowMs = configManager.getAutoFightTimeWindowSeconds() * 1000L;
        long now = System.currentTimeMillis();

        UUID attackerId = attacker.getUniqueId();
        UUID victimId = victim.getUniqueId();

        recentHits.putIfAbsent(victimId, new HashMap<>());
        Map<UUID, List<Long>> attackers = recentHits.get(victimId);
        
        attackers.putIfAbsent(attackerId, new ArrayList<>());
        List<Long> hits = attackers.get(attackerId);
        
        hits.add(now);
        hits.removeIf(t -> now - t > timeWindowMs);

        if (hits.size() >= requiredHits) {
            hits.clear(); // Reset
            
            if (fightManager.getCurrentSessionId() == null) {
                fightManager.clearTeams();
                fightManager.addToTeam1(attacker);
                fightManager.addToTeam2(victim);
                fightManager.startFight();
                org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.RED + "⚔ Auto-Fight started between " + attacker.getName() + " and " + victim.getName() + "!");
            } else {
                // Fight already active, add them if not participating
                List<String> addedNames = new ArrayList<>();
                if (!fightManager.isParticipant(attackerId)) {
                    fightManager.addToTeam1(attacker);
                    attacker.sendMessage(org.bukkit.ChatColor.RED + "You were added to Team 1 in the ongoing fight!");
                    addedNames.add(attacker.getName());
                }
                if (!fightManager.isParticipant(victimId)) {
                    fightManager.addToTeam2(victim);
                    victim.sendMessage(org.bukkit.ChatColor.RED + "You were added to Team 2 in the ongoing fight!");
                    addedNames.add(victim.getName());
                }
                if (!addedNames.isEmpty()) {
                    org.bukkit.Bukkit.broadcastMessage(org.bukkit.ChatColor.RED + "⚔ " + String.join(" and ", addedNames) + " joined the ongoing fight!");
                }
            }
        }
    }
}