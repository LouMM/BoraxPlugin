// Update to src/main/java/com/example/FightManager.java (add import for Collectors)
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
import java.util.stream.Collectors;

/**
 * Fight session manager: Teams, bossbar (persists for participants), cross-team scoring, end loot theft.
 * Adds bossbar on join/respawn for active fights.
 */
public class FightManager implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final CombatCache combatCache;
    private final ScoringEngine scoringEngine;
    private final PersistenceManager persistenceManager;

    private final Set<UUID> team1Players = new HashSet<>();
    private final Set<UUID> team2Players = new HashSet<>();

    private UUID currentSessionId;
    private BossBar bossBar;
    private BukkitRunnable fightTask;
    private long fightEndTime;

    public FightManager(JavaPlugin plugin, ConfigManager configManager, CombatCache combatCache, ScoringEngine scoringEngine, PersistenceManager persistenceManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.combatCache = combatCache;
        this.scoringEngine = scoringEngine;
        this.persistenceManager = persistenceManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Set<UUID> getTeam1Players() {
        return Collections.unmodifiableSet(team1Players);
    }

    public Set<UUID> getTeam2Players() {
        return Collections.unmodifiableSet(team2Players);
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
            plugin.getLogger().warning("Cannot start fight: Disabled or empty teams.");
            return;
        }
        currentSessionId = UUID.randomUUID();
        long durationSeconds = configManager.getDefaultFightDurationSeconds();
        fightEndTime = System.currentTimeMillis() + (durationSeconds * 1000L);

        bossBar = Bukkit.createBossBar("FIGHT starting...", BarColor.RED, BarStyle.SOLID);
        updateBossBarPlayers();

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
        fightTask.runTaskTimer(plugin, 0L, 20L);
    }

    private String getPlayerNamesShort(Set<UUID> uuids) {
        return uuids.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).map(Player::getName).limit(3).collect(Collectors.joining(", "));
    }

    private void updateBossBarPlayers() {
        if (bossBar == null) return;
        bossBar.removeAll();
        Set<UUID> participants = new HashSet<>(team1Players);
        participants.addAll(team2Players);
        for (UUID uuid : participants) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) bossBar.addPlayer(p);
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

    public boolean isParticipant(UUID uuid) {
        return team1Players.contains(uuid) || team2Players.contains(uuid);
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
            String messageText = winnerMsg + " §fScores: T1 " + finalScores.team1Score() + " T2 " + finalScores.team2Score();
            Bukkit.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(messageText));
            return;
        }

        String messageText = winnerMsg + " §fScores: T1 " + finalScores.team1Score() + " T2 " + finalScores.team2Score();
        Bukkit.getServer().getOnlinePlayers().forEach(p -> p.sendMessage(messageText));

        // Update wins/losses
        for (UUID winner : winners) {
            WinsLosses current = persistenceManager.getWinsLosses(winner);
            persistenceManager.updateWinsLosses(winner, current.incrementWins());
        }
        for (UUID loser : losers) {
            WinsLosses current = persistenceManager.getWinsLosses(loser);
            persistenceManager.updateWinsLosses(loser, current.incrementLosses());
        }

        // --- UPDATED STEAL LOOT LOGIC ---
        for (UUID loserUuid : losers) {
            Player loserP = Bukkit.getPlayer(loserUuid);
            if (loserP == null) continue;

            ItemStack stolen = findRandomHighValueItem(loserP);
            if (stolen != null) {
                // Restrict theft to 1 item so we don't duplicate a stack of 64 to everyone
                stolen.setAmount(1);

                // Safely remove exactly 1 of that item from the loser
                loserP.getInventory().removeItem(stolen);
                loserP.sendMessage("§cYou lost a §b" + stolen.getType().name() + " §cto the winning team!");

                // Give a copy of that 1 item to EVERY player on the winning team
                for (UUID winnerUuid : winners) {
                    Player winnerP = Bukkit.getPlayer(winnerUuid);
                    if (winnerP != null) {
                        winnerP.getInventory().addItem(stolen.clone());
                        winnerP.sendMessage("§aLoot Share! You received a stolen §b" + stolen.getType().name() + " §afrom " + loserP.getName());
                    }
                }
            }
        }

        clearSession();
    }

    private ItemStack findRandomHighValueItem(Player player) {
        List<ItemStack> highValue = Arrays.stream(player.getInventory().getContents())
                .filter(item -> item != null && configManager.isHighValueMaterial(item.getType().name()))
                .toList();
        return highValue.isEmpty() ? null : highValue.get((int) (Math.random() * highValue.size())).clone();
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