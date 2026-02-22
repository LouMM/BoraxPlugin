// Update src/main/java/com/example/KillListener.java (setKeepInventory for fight participants)
package com.example;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * PvP kills: Records fatal hit; sets keepInventory for fight participants.
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
        Player victim = event.getEntity();

        // Player-specific keepInventory for fight participants
        if (fightManager.isParticipant(victim.getUniqueId())) {
            if (fightManager.isApplyingDeathPenalty()) {
                if (configManager.isKeepInventoryFightEndEnabled()) {
                    event.setKeepInventory(true);
                    event.getDrops().clear();
                } else {
                    event.setKeepInventory(false);
                }
            } else if (fightManager.getCurrentSessionId() != null) {
                if (configManager.isKeepInventoryDuringFightEnabled()) {
                    event.setKeepInventory(true);
                    event.getDrops().clear();  // Ensure no drops
                }
            }
        }

        if (!configManager.isCombatTrackingEnabled()) return;
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        ItemStack weaponStack = killer.getInventory().getItemInMainHand();
        Material weapon = weaponStack.getType() == Material.AIR ? Material.AIR : weaponStack.getType();

        String hitBodyPart = "torso";
        double damage = 999.0;
        boolean wasVictimBlocking = false;
        int victimArmorTier = ScoringEngine.calculateAverageArmorTier(victim);

        UUID fightSessionId = fightManager.getCurrentSessionId();

        CombatRecord killRecord = new CombatRecord(
                killer.getUniqueId(), killer.getName(),
                victim.getUniqueId(), victim.getName(),
                weapon, hitBodyPart, victim.getLocation().clone(), damage, true, wasVictimBlocking, victimArmorTier, fightSessionId,
                System.currentTimeMillis()
        );

        combatCache.addRecord(killRecord);
    }
}