// Update to src/main/java/com/example/PlayerLocs.java (fix registrations: remove extra 'this' param, add requireNonNull for safety)
package com.example;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * Entry point: Sets up config, caches, engines, managers; registers commands/events with feature gating.
 * Modules (combat/fight) are injectable/self-contained for portability.
 */
public class PlayerLocs extends JavaPlugin {
    private ConfigManager configManager;
    private CombatCache globalCombatCache;
    private ScoringEngine scoringEngine;
    private FightManager fightManager;

    @Override
    public void onEnable() {
        // Generate default config.yml if missing
        saveDefaultConfig();

        // Init core modules
        configManager = new ConfigManager(this);
        globalCombatCache = new CombatCache();
        scoringEngine = new ScoringEngine(configManager);
        fightManager = new FightManager(this, configManager, globalCombatCache, scoringEngine);

        // Register original locs command
        Objects.requireNonNull(getCommand("locs")).setExecutor(new LocationCommand(this));

        // Register fight command + tab completer
        Objects.requireNonNull(getCommand("fight")).setExecutor(new FightCommand(this, fightManager, configManager));
        Objects.requireNonNull(getCommand("fight")).setTabCompleter(new FightTabCompleter());

        // Register combat lookup command
        Objects.requireNonNull(getCommand("combat")).setExecutor(new CombatCommand(globalCombatCache, configManager));

        // Register listeners (gated inside)
        getServer().getPluginManager().registerEvents(new HitListener(globalCombatCache, fightManager, configManager), this);
        getServer().getPluginManager().registerEvents(new KillListener(globalCombatCache, fightManager, configManager), this);

        getLogger().info("PlayerLocs enabled with combat/fight modules!");
    }

    @Override
    public void onDisable() {
        // Safely end active fight
        fightManager.endCurrentFight();
    }
}