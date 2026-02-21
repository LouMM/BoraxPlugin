// Update to src/main/java/com/example/PlayerLocs.java (no changes needed, but confirm registrations match)
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
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        globalCombatCache = new CombatCache();
        scoringEngine = new ScoringEngine(configManager);
        fightManager = new FightManager(this, configManager, globalCombatCache, scoringEngine);

        Objects.requireNonNull(getCommand("locs")).setExecutor(new LocationCommand(this));

        Objects.requireNonNull(getCommand("fight")).setExecutor(new FightCommand(this, fightManager, configManager));
        Objects.requireNonNull(getCommand("fight")).setTabCompleter(new FightTabCompleter());

        Objects.requireNonNull(getCommand("combat")).setExecutor(new CombatCommand(globalCombatCache, configManager));

        getServer().getPluginManager().registerEvents(new HitListener(globalCombatCache, fightManager, configManager), this);
        getServer().getPluginManager().registerEvents(new KillListener(globalCombatCache, fightManager, configManager), this);

        getLogger().info("PlayerLocs enabled with combat/fight modules!");
    }

    @Override
    public void onDisable() {
        fightManager.endCurrentFight();
    }
}