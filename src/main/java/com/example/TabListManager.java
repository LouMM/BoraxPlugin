// Add new class src/main/java/com/example/TabListManager.java
package com.example;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Customizes player list names: Adds ping, level, wins/losses, join time, bully/team icon.
 * Updates every 30s; computes bully from records (non-fight kills > fight kills).
 */
public class TabListManager implements Listener {
    private final JavaPlugin plugin;
    private final PersistenceManager persistenceManager;
    private final CombatCache combatCache;
    private final Map<UUID, Long> joinTimes = new HashMap<>();

    public TabListManager(JavaPlugin plugin, PersistenceManager persistenceManager, CombatCache combatCache) {
        this.plugin = plugin;
        this.persistenceManager = persistenceManager;
        this.combatCache = combatCache;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Update tab every 30s
        new BukkitRunnable() {
            @Override
            public void run() {
                updateAllTabNames();
            }
        }.runTaskTimer(plugin, 0L, 600L);  // 30s
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        joinTimes.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        updateTabName(event.getPlayer());
    }

    private void updateAllTabNames() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateTabName(p);
        }
    }

    private void updateTabName(Player player) {
        UUID uuid = player.getUniqueId();
        WinsLosses wl = persistenceManager.getWinsLosses(uuid);
        int ping = player.getPing();
        int level = player.getLevel();
        long joinTime = joinTimes.getOrDefault(uuid, System.currentTimeMillis());
        String timeAgo = formatTimeAgo(System.currentTimeMillis() - joinTime);

        // Compute bully: Load full records for accuracy
        List<CombatRecord> records = persistenceManager.loadDiskRecordsForPlayer(uuid);
        records.addAll(combatCache.getRecentHitsByAttacker(uuid, Integer.MAX_VALUE));
        long fightKills = records.stream().filter(r -> r.isFatalKill() && r.fightSessionId() != null).count();
        long nonFightKills = records.stream().filter(r -> r.isFatalKill() && r.fightSessionId() == null).count();
        String icon = nonFightKills > fightKills ? "☠" : "★";

        Component nameComponent = Component.text(player.getName(), NamedTextColor.GREEN)
                .append(Component.text(" [Ping:" + ping + "ms] ", NamedTextColor.GRAY))
                .append(Component.text(" Lvl:" + level + " ", NamedTextColor.GOLD))
                .append(Component.text(" W:" + wl.wins() + " L:" + wl.losses() + " ", NamedTextColor.AQUA))
                .append(Component.text(" Joined:" + timeAgo + " ", NamedTextColor.YELLOW))
                .append(Component.text(icon, NamedTextColor.RED));

        player.playerListName(nameComponent);
    }

    private String formatTimeAgo(long ms) {
        long seconds = ms / 1000;
        if (seconds < 60) return seconds + "s";
        long mins = seconds / 60;
        if (mins < 60) return mins + "m";
        long hours = mins / 60;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }
}