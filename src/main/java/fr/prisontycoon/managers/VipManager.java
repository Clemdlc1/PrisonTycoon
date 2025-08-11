package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VipManager {

    private final PrisonTycoon plugin;
    private final Set<UUID> vipCache = ConcurrentHashMap.newKeySet();

    public VipManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        createTable();
        loadVips();
    }

    private void createTable() {
        String vipTable = "CREATE TABLE IF NOT EXISTS vips (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "player_name VARCHAR(16)," +
                "added_by VARCHAR(16)," +
                "added_at BIGINT" +
                ");";
        try (Connection conn = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = conn.prepareStatement(vipTable)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create vips table: " + e.getMessage());
        }
    }

    private void loadVips() {
        vipCache.clear();
        String query = "SELECT uuid FROM vips";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                vipCache.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading VIPs: " + e.getMessage());
        }
        plugin.getLogger().info("Loaded " + vipCache.size() + " VIPs from database.");
    }

    public void addVip(UUID uuid, Player target, Player addedBy) {
        String query = "INSERT INTO vips (uuid, player_name, added_by, added_at) VALUES (?, ?, ?, ?) ON CONFLICT (uuid) DO NOTHING";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, target.getName());
            ps.setString(3, addedBy.getName());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            vipCache.add(uuid);
            plugin.getPermissionManager().attachPermission(target, "specialmine.vip");
            plugin.getLogger().info("VIP added for " + target.getName());
        } catch (SQLException e) {
            plugin.getLogger().severe("Error adding VIP: " + e.getMessage());
        }
    }

    public void removeVip(Player player, String removedBy) {
        String query = "DELETE FROM vips WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, player.getUniqueId().toString());
            ps.executeUpdate();
            vipCache.remove(player.getUniqueId());
            plugin.getPermissionManager().detachPermission(player, "specialmine.vip");
            plugin.getLogger().info("VIP removed for " + player.getName());
        } catch (SQLException e) {
            plugin.getLogger().severe("Error removing VIP: " + e.getMessage());
        }
    }

    public boolean isVip(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        assert player != null;
        return plugin.getPermissionManager().hasPermission(player, "specialmine.vip");
    }

    public VipData getVipData(UUID uuid) {
        if (!isVip(uuid)) return null;
        String query = "SELECT * FROM vips WHERE uuid = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new VipData(
                        uuid,
                        rs.getString("player_name"),
                        rs.getString("added_by"),
                        rs.getLong("added_at")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting VIP data: " + e.getMessage());
        }
        return null;
    }

    public int getVipCount() {
        return vipCache.size();
    }

    public Set<UUID> getAllVips() {
        return new HashSet<>(vipCache);
    }

    public void reload() {
        loadVips();
        plugin.getLogger().info("VIP system reloaded.");
    }

    public void forcePlayerSync(Player player) {
        boolean isVipInCache = isVip(player.getUniqueId());
        boolean hasVipPermission = player.hasPermission("specialmine.vip");

        if (isVipInCache && !hasVipPermission) {
            plugin.getPermissionManager().attachPermission(player, "specialmine.vip");
            plugin.getLogger().info("Synchronized VIP status for " + player.getName() + ": Added missing permission.");
        } else if (!isVipInCache && hasVipPermission) {
            plugin.getPermissionManager().detachPermission(player, "specialmine.vip");
            plugin.getLogger().info("Synchronized VIP status for " + player.getName() + ": Removed extra permission.");
        }
    }

    public String getVipStatusDetailed(UUID uuid) {
        VipData data = getVipData(uuid);
        if (data != null) {
            return "§a✅ VIP Actif: §e" + data.playerName() + " §7(Ajouté par: " + data.addedBy() + " le " + new java.util.Date(data.addedAt()) + ")";
        } else {
            return "§c❌ Non VIP";
        }
    }

    public boolean checkVipConsistency(Player player) {
        boolean isVipInCache = isVip(player.getUniqueId());
        boolean hasVipPermission = player.hasPermission("specialmine.vip");
        return isVipInCache == hasVipPermission;
    }

    public void syncAllVipData() {
        // Sync online players
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            forcePlayerSync(player);
        }

        // Sync database with cache (add missing from DB to cache, remove from cache if not in DB and no permission)
        // This part is more complex and might require iterating through DB records
        // For now, we assume the cache is the source of truth for active VIPs
        // and permissions are synced based on cache.
        // A full DB sync would involve reading all DB entries and comparing with cache.
        // This simplified version focuses on ensuring online players have correct permissions.
    }

    public record VipData(UUID uuid, String playerName, String addedBy, long addedAt) {
    }
}
