// Update to src/main/java/com/example/HitListener.java (fix isBlocked detection, body part approx using eye height)
package com.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Captures PvP hit events: Logs weapon, approx body part (head/torso/legs via relative hit Y), damage, block status, armor.
 * Only records if feature enabled and players involved.
 */
public class HitListener implements Listener {
    private final CombatCache combatCache;
    private final FightManager fightManager;
    private final ConfigManager configManager;

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
    }
}