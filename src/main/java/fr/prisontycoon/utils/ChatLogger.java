package fr.prisontycoon.utils;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ChatLogger {

    private final PrisonTycoon plugin;

    public ChatLogger(PrisonTycoon plugin) {
        this.plugin = plugin;
        createTable();
    }

    private void createTable() {
        String query = "CREATE TABLE IF NOT EXISTS chat_logs (" +
                "id SERIAL PRIMARY KEY," +
                "timestamp BIGINT NOT NULL," +
                "type VARCHAR(16) NOT NULL," +
                "player_name VARCHAR(16) NOT NULL," +
                "uuid VARCHAR(36) NOT NULL," +
                "raw_message TEXT NOT NULL," +
                "formatted_message TEXT" +
                ");";
        try (Connection conn = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create chat_logs table: " + e.getMessage());
        }
    }

    public void logChatMessage(Player player, String message, String formattedMessage) {
        log(LogType.CHAT, player.getName(), player.getUniqueId().toString(), message, formattedMessage);
    }

    public void logAdminAction(String admin, String action, String target, String details) {
        log(LogType.ADMIN, admin, "N/A", action + " on " + target, details);
    }

    public void logCommand(Player player, String command) {
        log(LogType.COMMAND, player.getName(), player.getUniqueId().toString(), command, "Command executed");
    }

    private void log(LogType type, String playerName, String uuid, String rawMessage, String formattedMessage) {
        String query = "INSERT INTO chat_logs (timestamp, type, player_name, uuid, raw_message, formatted_message) VALUES (?, ?, ?, ?, ?, ?)";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, System.currentTimeMillis());
                ps.setString(2, type.name());
                ps.setString(3, playerName);
                ps.setString(4, uuid);
                ps.setString(5, rawMessage);
                ps.setString(6, formattedMessage);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error writing to chat_logs: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        // No action needed for database logging
    }

    public int getTotalMessagesLogged() {
        String query = "SELECT COUNT(*) FROM chat_logs";
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting total messages logged: " + e.getMessage());
        }
        return 0;
    }

    public void cleanOldLogs(int daysToKeep) {
        long cutoff = System.currentTimeMillis() - (long) daysToKeep * 24 * 60 * 60 * 1000;
        String query = "DELETE FROM chat_logs WHERE timestamp < ?";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, cutoff);
                int deletedRows = ps.executeUpdate();
                if (deletedRows > 0) {
                    plugin.getLogger().info("Cleaned up " + deletedRows + " old chat logs.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error cleaning old chat logs: " + e.getMessage());
            }
        });
    }

    public enum LogType {
        CHAT, ADMIN, COMMAND
    }
}
