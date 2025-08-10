package fr.prisontycoon.quests;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du collectionneur de blocs.
 * Sauvegarde par joueur une map Material->count et calcule les paliers.
 */
public class BlockCollectorManager {
    private final PrisonTycoon plugin;
    private final Gson gson = new Gson();
    private final Map<UUID, Map<Material, Long>> cache = new ConcurrentHashMap<>();
    private static final int MAX_TIERS = 100;

    public BlockCollectorManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public long add(Player player, Material mat, long amount) {
        if (mat == null || amount <= 0) return 0;
        Map<Material, Long> map = cache.computeIfAbsent(player.getUniqueId(), this::loadMap);
        long v = map.getOrDefault(mat, 0L) + amount;
        map.put(mat, v);
        return v;
    }

    public long get(Player player, Material mat) {
        Map<Material, Long> map = cache.computeIfAbsent(player.getUniqueId(), this::loadMap);
        return map.getOrDefault(mat, 0L);
    }

    public int getTierFor(Player player, Material mat) {
        long count = get(player, mat);
        // Courbe de progression: palier n requiert n^2 * base (base=50)
        int tier = 0;
        long base = 50;
        long remaining = count;
        for (int i = 1; i <= MAX_TIERS; i++) {
            long req = base * (long) i * (long) i;
            if (remaining >= req) { tier = i; remaining -= req; } else break;
        }
        return tier;
    }

    public long getProgressToNext(Player player, Material mat) {
        long count = get(player, mat);
        int tier = getTierFor(player, mat);
        long base = 50;
        long consumed = 0;
        for (int i = 1; i <= tier; i++) consumed += base * (long) i * (long) i;
        long currentIntoTier = count - consumed;
        long nextReq = tier >= MAX_TIERS ? 0 : base * (long) (tier + 1) * (long) (tier + 1);
        return Math.max(0, nextReq - Math.max(0, currentIntoTier));
    }

    public long getNextTierRequirement(int nextTier) {
        if (nextTier <= 0) return 0;
        return 50L * nextTier * nextTier;
    }

    public boolean claimTier(Player player, Material mat) {
        int tier = getTierFor(player, mat);
        // On ne reclasse pas; cliquer valide simplement la récompense du tier atteint non encore réclamée.
        // Pour simplicité: récompense = beacons = 5 * tier
        if (tier <= 0) return false;
        plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).addBeacons(5L * tier);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    public Map<Material, Long> getStats(UUID playerId) {
        return new HashMap<>(cache.computeIfAbsent(playerId, this::loadMap));
    }

    public List<Map.Entry<UUID, Long>> getTopFor(Material mat, int top) {
        // On scanne la base pour éviter d'avoir tout en mémoire (simple: lecture de toutes les stats)
        Map<UUID, Long> totals = new HashMap<>();
        String sql = "SELECT uuid, stats_json FROM player_block_stats";
        try (Connection c = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            Type type = new TypeToken<Map<String, Long>>(){}.getType();
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("uuid"));
                Map<String, Long> map = gson.fromJson(rs.getString("stats_json"), type);
                if (map == null) continue;
                Long v = map.get(mat.name());
                if (v != null && v > 0) totals.put(id, v);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getTopFor error: " + e.getMessage());
        }
        List<Map.Entry<UUID, Long>> list = new ArrayList<>(totals.entrySet());
        list.sort((a,b)->Long.compare(b.getValue(), a.getValue()));
        if (list.size() > top) return list.subList(0, top);
        return list;
    }

    public void save(UUID playerId) {
        Map<Material, Long> map = cache.get(playerId);
        if (map == null) return;
        Map<String, Long> s = new HashMap<>();
        for (var e : map.entrySet()) s.put(e.getKey().name(), e.getValue());
        String sql = """
                INSERT INTO player_block_stats(uuid, stats_json) VALUES(?,?)
                ON CONFLICT (uuid) DO UPDATE SET stats_json = EXCLUDED.stats_json
                """;
        try (Connection c = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, gson.toJson(s));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("save block stats error: " + e.getMessage());
        }
    }

    private Map<Material, Long> loadMap(UUID playerId) {
        String sql = "SELECT stats_json FROM player_block_stats WHERE uuid = ?";
        try (Connection c = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Type type = new TypeToken<Map<String, Long>>(){}.getType();
                    Map<String, Long> s = gson.fromJson(rs.getString("stats_json"), type);
                    Map<Material, Long> map = new ConcurrentHashMap<>();
                    if (s != null) for (var e : s.entrySet()) {
                        try { map.put(Material.valueOf(e.getKey()), e.getValue()); } catch (Exception ignored) {}
                    }
                    return map;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("load block stats error: " + e.getMessage());
        }
        return new ConcurrentHashMap<>();
    }
}


