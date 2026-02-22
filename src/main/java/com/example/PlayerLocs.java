// Update src/main/java/com/example/PlayerLocs.java (add new commands, managers, tab completers, save names on join)
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;

/**
 * Main: Adds inventory/loc commands, name-uuid manager, tabs.
 */
public class PlayerLocs extends JavaPlugin implements Listener {
    private ConfigManager configManager;
    private CombatCache globalCombatCache;
    private ScoringEngine scoringEngine;
    private FightManager fightManager;
    private PersistenceManager persistenceManager;
    private TabListManager tabListManager;
    private NameUuidManager nameUuidManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        persistenceManager = new PersistenceManager(this);
        persistenceManager.load();
        globalCombatCache = new CombatCache();
        scoringEngine = new ScoringEngine(configManager);
        nameUuidManager = new NameUuidManager(this);
        fightManager = new FightManager(this, configManager, globalCombatCache, scoringEngine, persistenceManager);
        tabListManager = new TabListManager(this, persistenceManager, globalCombatCache);

        Objects.requireNonNull(getCommand("locs")).setExecutor(new LocationCommand(this, nameUuidManager));
        Objects.requireNonNull(getCommand("fight")).setExecutor(new FightCommand(this, fightManager, configManager));
        Objects.requireNonNull(getCommand("fight")).setTabCompleter(new FightTabCompleter());
        Objects.requireNonNull(getCommand("combat")).setExecutor(new CombatCommand(globalCombatCache, configManager, persistenceManager));
        Objects.requireNonNull(getCommand("combat")).setTabCompleter(new CombatTabCompleter());
        Objects.requireNonNull(getCommand("borax")).setExecutor(new BoraxCommand(this));
        Objects.requireNonNull(getCommand("inventory")).setExecutor(new InventoryCommand(this, nameUuidManager));
        Objects.requireNonNull(getCommand("inventory")).setTabCompleter(new InventoryTabCompleter());
        Objects.requireNonNull(getCommand("loc")).setExecutor(new LocationCommand(this, nameUuidManager));  // New alias or separate
        Objects.requireNonNull(getCommand("loc")).setTabCompleter(new LocationTabCompleter());

        getServer().getPluginManager().registerEvents(new HitListener(globalCombatCache, fightManager, configManager), this);
        getServer().getPluginManager().registerEvents(new KillListener(globalCombatCache, fightManager, configManager), this);
        getServer().getPluginManager().registerEvents(this, this);  // For join

        // Periodic save
        new BukkitRunnable() {
            @Override
            public void run() {
                persistenceManager.save(globalCombatCache);
                globalCombatCache.clearAfterPersist();
                nameUuidManager.save();
            }
        }.runTaskTimer(this, 6000L, 6000L);

        getLogger().info("PlayerLocs enabled!");
    }

    @Override
    public void onDisable() {
        fightManager.endCurrentFight();
        persistenceManager.save(globalCombatCache);
        nameUuidManager.save();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        nameUuidManager.addOrUpdate(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}