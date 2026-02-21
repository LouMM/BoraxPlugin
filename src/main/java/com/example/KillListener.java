// Update to src/main/java/com/example/KillListener.java (add weapon null-check, consistent with HitListener)
package com.example;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Captures PvP kills: Logs as fatal record with killer's held weapon, death loc, armor at death.
 * Associates with active fight session if any.
 */
public class KillListener implements Listener {
    private final CombatCache combatCache;
    private final FightManager fightManager;
    private final ConfigManager configManager;

    public KillListener(CombatCache combatCache, FightManager fightManager, ConfigManager configManager) {
        this.combatCache = combatCache;
        this.fightManager = fightManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!configManager.isCombatTrackingEnabled()) return;
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;  // Ignore non-PvP or suicide

        ItemStack weaponStack = killer.getInventory().getItemInMainHand();
        Material weapon = weaponStack.getType() == Material.AIR ? Material.AIR : weaponStack.getType();

        Location hitLocation = victim.getLocation().clone();
        String hitBodyPart = "torso";  // Default for kills (no precise data)

        double damage = 999.0;  // Placeholder for lethal damage
        boolean wasVictimBlocking = false;  // Kills bypass blocks typically
        int victimArmorTier = ScoringEngine.calculateAverageArmorTier(victim);

        UUID fightSessionId = fightManager.getCurrentSessionId();

        CombatRecord killRecord = new CombatRecord(
                killer.getUniqueId(), killer.getName(),
                victim.getUniqueId(), victim.getName(),
                weapon, hitBodyPart, hitLocation, damage, true, wasVictimBlocking, victimArmorTier, fightSessionId,
                System.currentTimeMillis()
        );

        combatCache.addRecord(killRecord);
    }
}