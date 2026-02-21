// Update src/main/java/com/example/PlayerLocs.java (add persistence, tab manager, periodic save)
package com.example;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

/**
 * Main: Adds persistence, tab manager; periodic save (5min) + clear memory.
 */
public class PlayerLocs extends JavaPlugin {
    private ConfigManager configManager;
    private CombatCache globalCombatCache;
    private ScoringEngine scoringEngine;
    private FightManager fightManager;
    private PersistenceManager persistenceManager;
    private TabListManager tabListManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        persistenceManager = new PersistenceManager(this);
        persistenceManager.load();
        globalCombatCache = new CombatCache();
        scoringEngine = new ScoringEngine(configManager);
        fightManager = new FightManager(this, configManager, globalCombatCache, scoringEngine, persistenceManager);
        tabListManager = new TabListManager(this, persistenceManager, globalCombatCache);

        Objects.requireNonNull(getCommand("locs")).setExecutor(new LocationCommand(this));
        Objects.requireNonNull(getCommand("fight")).setExecutor(new FightCommand(this, fightManager, configManager));
        Objects.requireNonNull(getCommand("fight")).setTabCompleter(new FightTabCompleter());
        Objects.requireNonNull(getCommand("combat")).setExecutor(new CombatCommand(globalCombatCache, configManager, persistenceManager));

        getServer().getPluginManager().registerEvents(new HitListener(globalCombatCache, fightManager, configManager), this);
        getServer().getPluginManager().registerEvents(new KillListener(globalCombatCache, fightManager, configManager), this);

        // Periodic save: Every 5min, save + clear memory
        new BukkitRunnable() {
            @Override
            public void run() {
                persistenceManager.save(globalCombatCache);
                globalCombatCache.clearAfterPersist();
            }
        }.runTaskTimer(this, 6000L, 6000L);  // Start after 5min, every 5min

        getLogger().info("PlayerLocs enabled!");
    }

    @Override
    public void onDisable() {
        fightManager.endCurrentFight();
        persistenceManager.save(globalCombatCache);
    }
}