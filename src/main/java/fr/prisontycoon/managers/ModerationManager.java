package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ModerationManager {

    private final PrisonTycoon plugin;
    private final Map<UUID, ModerationData> muteCache = new ConcurrentHashMap<>();
    private final Map<UUID, ModerationData> banCache = new ConcurrentHashMap<>();

    public ModerationManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        createTable();
        loadModerations();
    }

    private void createTable() {
        String moderationTable = "CREATE TABLE IF NOT EXISTS moderation (" +
                "id SERIAL PRIMARY KEY," +
                "uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(16)," +
                "type VARCHAR(16) NOT NULL," +
                "reason VARCHAR(255)," +
                "moderator VARCHAR(16)," +
                "start_time BIGINT," +
                "end_time BIGINT" +
                ");";
        try (Connection conn = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = conn.prepareStatement(moderationTable)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create moderation table: " + e.getMessage());
        }
    }

    private void loadModerations() {
        muteCache.clear();
        banCache.clear();
        String query = "SELECT * FROM moderation WHERE end_time > ? OR end_time = 0";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setLong(1, System.currentTimeMillis());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                ModerationData data = new ModerationData(
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getLong("end_time"),
                        rs.getString("reason"),
                        rs.getString("moderator"),
                        rs.getLong("start_time")
                );
                if ("MUTE".equalsIgnoreCase(rs.getString("type"))) {
                    muteCache.put(data.uuid(), data);
                } else if ("BAN".equalsIgnoreCase(rs.getString("type"))) {
                    banCache.put(data.uuid(), data);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading moderations: " + e.getMessage());
        }
        plugin.getLogger().info("Loaded " + muteCache.size() + " mutes and " + banCache.size() + " bans from database.");
    }

    public void mutePlayer(UUID uuid, String playerName, long endTime, String reason, String moderator) {
        ModerationData muteData = new ModerationData(uuid, playerName, endTime, reason, moderator, System.currentTimeMillis());
        String query = "INSERT INTO moderation (uuid, player_name, type, reason, moderator, start_time, end_time) VALUES (?, ?, 'MUTE', ?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, reason);
            ps.setString(4, moderator);
            ps.setLong(5, muteData.startTime());
            ps.setLong(6, endTime);
            ps.executeUpdate();
            muteCache.put(uuid, muteData);
            addSanctionToPlayer(uuid, "MUTE", reason, moderator, muteData.startTime(), endTime);
            plugin.getLogger().info("Player " + playerName + " muted.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error muting player: " + e.getMessage());
        }
    }

    public void unmutePlayer(UUID uuid, String moderator) {
        String query = "DELETE FROM moderation WHERE uuid = ? AND type = 'MUTE'";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            muteCache.remove(uuid);
            plugin.getLogger().info("Player " + uuid + " unmuted.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error unmuting player: " + e.getMessage());
        }
    }

    public void banPlayer(UUID uuid, String playerName, long endTime, String reason, String moderator) {
        ModerationData banData = new ModerationData(uuid, playerName, endTime, reason, moderator, System.currentTimeMillis());
        String query = "INSERT INTO moderation (uuid, player_name, type, reason, moderator, start_time, end_time) VALUES (?, ?, 'BAN', ?, ?, ?, ?)";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, reason);
            ps.setString(4, moderator);
            ps.setLong(5, banData.startTime());
            ps.setLong(6, endTime);
            ps.executeUpdate();
            banCache.put(uuid, banData);
            addSanctionToPlayer(uuid, "BAN", reason, moderator, banData.startTime(), endTime);
            plugin.getLogger().info("Player " + playerName + " banned.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error banning player: " + e.getMessage());
        }
    }

    public void unbanPlayer(UUID uuid, String moderator) {
        String query = "DELETE FROM moderation WHERE uuid = ? AND type = 'BAN'";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            banCache.remove(uuid);
            plugin.getLogger().info("Player " + uuid + " unbanned.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error unbanning player: " + e.getMessage());
        }
    }

    public boolean isMuted(UUID uuid) {
        ModerationData muteData = muteCache.get(uuid);
        if (muteData == null) return false;
        if (muteData.endTime() != 0 && System.currentTimeMillis() > muteData.endTime()) {
            unmutePlayer(uuid, "SYSTEM");
            return false;
        }
        return true;
    }

    public boolean isBanned(UUID uuid) {
        ModerationData banData = banCache.get(uuid);
        if (banData == null) return false;
        if (banData.endTime() != 0 && System.currentTimeMillis() > banData.endTime()) {
            unbanPlayer(uuid, "SYSTEM");
            return false;
        }
        return true;
    }

    public ModerationData getMuteData(UUID uuid) {
        return muteCache.get(uuid);
    }

    public ModerationData getBanData(UUID uuid) {
        return banCache.get(uuid);
    }

    public void addSanctionToPlayer(UUID playerId, String type, String reason, String moderator, long startTime, long endTime) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);
        data.addSanction(type, reason, moderator, startTime, endTime);
        plugin.getPlayerDataManager().markDirty(playerId);
    }

    public List<PlayerData.SanctionData> getPlayerSanctionHistory(UUID uuid) {
        return plugin.getPlayerDataManager().getPlayerData(uuid).getSanctionHistory();
    }

    public void reload() {
        loadModerations();
        plugin.getLogger().info("Moderation system reloaded.");
    }

    public record ModerationData(UUID uuid, String playerName, long endTime, String reason, String moderator,
                                 long startTime) {
        public boolean isPermanent() {
            return endTime == 0;
        }

        public long getRemainingTime() {
            if (isPermanent()) return -1;
            return Math.max(0, endTime - System.currentTimeMillis());
        }
    }

    public int getMutedPlayersCount() {
        return muteCache.size();
    }

    public int getBannedPlayersCount() {
        return banCache.size();
    }

    public void cleanupExpiredSanctions() {
        long currentTime = System.currentTimeMillis();
        // Clean up expired mutes
        muteCache.entrySet().removeIf(entry -> {
            ModerationData data = entry.getValue();
            if (data.endTime() != 0 && currentTime > data.endTime()) {
                plugin.getLogger().info("Cleaning up expired mute for " + data.playerName());
                deleteSanctionFromDatabase(data.uuid(), "MUTE");
                return true;
            }
            return false;
        });

        // Clean up expired bans
        banCache.entrySet().removeIf(entry -> {
            ModerationData data = entry.getValue();
            if (data.endTime() != 0 && currentTime > data.endTime()) {
                plugin.getLogger().info("Cleaning up expired ban for " + data.playerName());
                deleteSanctionFromDatabase(data.uuid(), "BAN");
                return true;
            }
            return false;
        });
    }

    private void deleteSanctionFromDatabase(UUID uuid, String type) {
        String query = "DELETE FROM moderation WHERE uuid = ? AND type = ?";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error deleting expired sanction from database: " + e.getMessage());
        }
    }
}