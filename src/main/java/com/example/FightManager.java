// Update to src/main/java/com/example/FightManager.java (fix scoring: only score if attacker and victim on opposite teams; persist bossbar by re-adding on player join/respawn)
package com.example;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Fight session manager: Teams, timer bossbar (persists for participants), cross-team scoring, end loot theft.
 * Adds bossbar on join/respawn for active fights.
 */
public class FightManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CombatCache combatCache;
    private final ScoringEngine scoringEngine;

    private final Set<UUID> team1Players = ConcurrentHashMap.newKeySet();
    private final Set<UUID> team2Players = ConcurrentHashMap.newKeySet();

    private UUID currentSessionId;
    private BossBar bossBar;
    private BukkitRunnable fightTask;
    private long fightEndTime;

    public FightManager(JavaPlugin plugin, ConfigManager configManager, CombatCache combatCache, ScoringEngine scoringEngine) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.combatCache = combatCache;
        this.scoringEngine = scoringEngine;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);  // Register self for join/respawn
    }

    public Set<UUID> getTeam1Players() {
        return team1Players;
    }

    public Set<UUID> getTeam2Players() {
        return team2Players;
    }

    public void addToTeam1(Player player) {
        team1Players.add(player.getUniqueId());
    }

    public void removeFromTeam1(Player player) {
        team1Players.remove(player.getUniqueId());
    }

    public void addToTeam2(Player player) {
        team2Players.add(player.getUniqueId());
    }

    public void removeFromTeam2(Player player) {
        team2Players.remove(player.getUniqueId());
    }

    public void clearTeams() {
        team1Players.clear();
        team2Players.clear();
    }

    public UUID getCurrentSessionId() {
        return currentSessionId;
    }

    public void startFight() {
        if (!configManager.isFightModeEnabled() || team1Players.isEmpty() || team2Players.isEmpty()) {
            plugin.getLogger().warning("Cannot start fight: Fight mode disabled or teams empty.");
            return;
        }
        currentSessionId = UUID.randomUUID();
        long durationSeconds = configManager.getDefaultFightDurationSeconds();
        fightEndTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        bossBar = Bukkit.createBossBar("FIGHT starting...", BarColor.RED, BarStyle.SOLID);
        updateBossBarPlayers();  // Add to participants only

        fightTask = new BukkitRunnable() {
            @Override
            public void run() {
                long remainingMs = fightEndTime - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    endCurrentFight();
                    cancel();
                    return;
                }
                var scores = scoringEngine.calculateSessionScores(combatCache.getRecordsMap(), currentSessionId, team1Players, team2Players);
                String team1Names = getPlayerNamesShort(team1Players);
                String team2Names = getPlayerNamesShort(team2Players);
                String timeStr = String.format("%02d:%02d", remainingMs / 60000, (remainingMs % 60000) / 1000);
                String title = String.format("§cFIGHT §f%s §a[%d] §cvs §f%s §a[%d] §e%s", team1Names, scores.team1Score(), team2Names, scores.team2Score(), timeStr);
                bossBar.setTitle(title);
                bossBar.setColor(scores.team1Score() > scores.team2Score() ? BarColor.GREEN : scores.team2Score() > scores.team1Score() ? BarColor.BLUE : BarColor.PURPLE);
                bossBar.setProgress(Math.min(1.0, remainingMs / (durationSeconds * 1000.0)));
            }
        };
        fightTask.runTaskTimer(plugin, 0L, 20L);  // Tick every second
    }

    private void updateBossBarPlayers() {
        if (bossBar == null) return;
        bossBar.removeAll();  // Clear first
        Set<UUID> allParticipants = new HashSet<>();
        allParticipants.addAll(team1Players);
        allParticipants.addAll(team2Players);
        for (UUID uuid : allParticipants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                bossBar.addPlayer(p);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (bossBar != null && isParticipant(event.getPlayer().getUniqueId())) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (bossBar != null && isParticipant(event.getPlayer().getUniqueId())) {
            bossBar.addPlayer(event.getPlayer());
        }
    }

    private boolean isParticipant(UUID uuid) {
        return team1Players.contains(uuid) || team2Players.contains(uuid);
    }

    private String getPlayerNamesShort(Set<UUID> uuids) {
        return uuids.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .map(Player::getName)
                .limit(3)
                .collect(Collectors.joining(", "));
    }

    public void endCurrentFight() {
        if (fightTask != null) {
            fightTask.cancel();
            fightTask = null;
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (currentSessionId == null) return;

        var finalScores = scoringEngine.calculateSessionScores(combatCache.getRecordsMap(), currentSessionId, team1Players, team2Players);
        Set<UUID> winners = new HashSet<>();
        Set<UUID> losers = new HashSet<>();
        String winnerMsg;
        if (finalScores.team1Score() > finalScores.team2Score()) {
            winners.addAll(team1Players);
            losers.addAll(team2Players);
            winnerMsg = "§aTeam1 wins!";
        } else if (finalScores.team2Score() > finalScores.team1Score()) {
            winners.addAll(team2Players);
            losers.addAll(team1Players);
            winnerMsg = "§aTeam2 wins!";
        } else {
            winnerMsg = "§eTie game!";
            clearSession();
            Bukkit.broadcastMessage(winnerMsg + " §fScores: T1 " + finalScores.team1Score() + " T2 " + finalScores.team2Score());
            return;
        }

        Bukkit.broadcastMessage(winnerMsg + " §fScores: T1 " + finalScores.team1Score() + " T2 " + finalScores.team2Score());

        List<UUID> winnerList = new ArrayList<>(winners);
        for (UUID loserUuid : losers) {
            Player loserP = Bukkit.getPlayer(loserUuid);
            if (loserP == null) continue;
            ItemStack stolenItem = findRandomHighValueItem(loserP);
            if (stolenItem != null) {
                loserP.getInventory().remove(stolenItem.clone());  // Remove one
                UUID winnerUuid = winnerList.get((int) (Math.random() * winnerList.size()));
                Player winnerP = Bukkit.getPlayer(winnerUuid);
                if (winnerP != null) {
                    winnerP.getInventory().addItem(stolenItem);
                    winnerP.sendMessage("§aStolen loot: §b" + stolenItem.getType().name());
                }
            }
        }

        clearSession();
    }

    private ItemStack findRandomHighValueItem(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        List<ItemStack> highValueItems = new ArrayList<>();
        for (ItemStack item : contents) {
            if (item != null && configManager.isHighValueMaterial(item.getType().name())) {
                highValueItems.add(item);
            }
        }
        return highValueItems.isEmpty() ? null : highValueItems.get((int) (Math.random() * highValueItems.size())).clone();
    }

    private void clearSession() {
        currentSessionId = null;
        fightEndTime = 0;
        team1Players.clear();
        team2Players.clear();
    }

    public ScorePair getCurrentScores() {
        if (currentSessionId == null) return new ScorePair(0, 0);
        return scoringEngine.calculateSessionScores(combatCache.getRecordsMap(), currentSessionId, team1Players, team2Players);
    }
}