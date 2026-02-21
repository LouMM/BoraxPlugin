package com.example;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocationCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Gson gson = new Gson();

    public LocationCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("playerlocs.use")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }

        List<Map<String, Object>> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location loc = p.getLocation();
            Map<String, Object> data = new HashMap<>();
            data.put("name", p.getName());
            data.put("uuid", p.getUniqueId().toString());
            data.put("world", loc.getWorld().getName());
            data.put("x", loc.getX());
            data.put("y", loc.getY());
            data.put("z", loc.getZ());
            data.put("yaw", loc.getYaw());
            data.put("pitch", loc.getPitch());
            players.add(data);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("players", players);
        payload.put("server_time", System.currentTimeMillis());
        String json = gson.toJson(payload);

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://httpbin.org/post"))  // <-- REPLACE WITH YOUR API URL
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            plugin.getLogger().info("§aAPI Response (" + res.statusCode() + "): " + res.body());
            sender.sendMessage("§aLocations sent! Check console.");
        } catch (Exception e) {
            plugin.getLogger().severe("§cAPI Error: " + e.getMessage());
            sender.sendMessage("§cError sending data.");
        }
        return true;
    }
}