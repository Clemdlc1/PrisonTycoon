package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire amélioré des notifications avec support multi-types et gestion de durée
 * NOUVEAU : Gère les notifications temporaires avec durée et priorité
 */
public class NotificationManager {

    // Configuration
    private final PrisonTycoon plugin;
    // File d'attente des notifications par joueur
    private final Map<UUID, Queue<GameNotification>> playerNotificationQueues;
    // Accumulateur de gains par joueur (pour regrouper les gains similaires)
    private final Map<UUID, GainAccumulator> playerGainAccumulators;
    // Dernière notification envoyée par joueur
    private final Map<UUID, Long> lastNotificationTime;
    // NOUVEAU : Notifications temporaires avec durée (pour les notifications de durabilité)
    private final Map<UUID, TemporaryNotification> activeTemporaryNotifications;

    public NotificationManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerNotificationQueues = new ConcurrentHashMap<>();
        this.playerGainAccumulators = new ConcurrentHashMap<>();
        this.lastNotificationTime = new ConcurrentHashMap<>();
        this.activeTemporaryNotifications = new ConcurrentHashMap<>();

        plugin.getPluginLogger().info("§aNotificationManager amélioré initialisé (avec notifications temporaires).");
    }

    /**
     * NOUVEAU : Ajoute une notification temporaire de durabilité avec durée spécifique
     */
    public void sendTemporaryDurabilityNotification(Player player, String message, long durationMs) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Crée une notification temporaire qui expire après durationMs
        TemporaryNotification tempNotif = new TemporaryNotification(message, now + durationMs);
        activeTemporaryNotifications.put(playerId, tempNotif);

        // Envoie immédiatement la notification
        player.sendActionBar(message);

        plugin.getPluginLogger().debug("Notification temporaire de durabilité envoyée à " + player.getName() +
                " pour " + durationMs + "ms: " + message);
    }

    /**
     * NOUVEAU : Vérifie si un joueur a une notification temporaire active
     */
    public boolean hasActiveTemporaryNotification(Player player) {
        UUID playerId = player.getUniqueId();
        TemporaryNotification tempNotif = activeTemporaryNotifications.get(playerId);

        if (tempNotif == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now > tempNotif.getExpiryTime()) {
            // La notification a expiré, la supprime
            activeTemporaryNotifications.remove(playerId);
            return false;
        }

        return true;
    }

    /**
     * NOUVEAU : Obtient le message de la notification temporaire active (si elle existe)
     */
    public String getActiveTemporaryNotificationMessage(Player player) {
        UUID playerId = player.getUniqueId();
        TemporaryNotification tempNotif = activeTemporaryNotifications.get(playerId);

        if (tempNotif == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        if (now > tempNotif.getExpiryTime()) {
            // La notification a expiré, la supprime
            activeTemporaryNotifications.remove(playerId);
            return null;
        }

        return tempNotif.getMessage();
    }

    /**
     * Nettoie les notifications temporaires expirées
     */
    public void cleanupExpiredTemporaryNotifications() {
        long now = System.currentTimeMillis();
        activeTemporaryNotifications.entrySet().removeIf(entry ->
                now > entry.getValue().getExpiryTime());
    }

    /**
     * Nettoie les données d'un joueur à la déconnexion
     */
    public void cleanupPlayerData(UUID playerId) {
        playerNotificationQueues.remove(playerId);
        playerGainAccumulators.remove(playerId);
        lastNotificationTime.remove(playerId);
        activeTemporaryNotifications.remove(playerId);
    }

    /**
     * Nettoie toutes les données
     */
    public void shutdown() {
        playerNotificationQueues.clear();
        playerGainAccumulators.clear();
        lastNotificationTime.clear();
        activeTemporaryNotifications.clear();
        plugin.getPluginLogger().info("§7NotificationManager arrêté et nettoyé.");
    }

    // Classes internes

    /**
     * Types de notifications
     */
    public enum NotificationType {
        REGULAR_GAINS, GREED, ENCHANTMENT, KEY, SPECIAL_STATE, SPECIAL_EFFECT
    }

    /**
     * Priorités des notifications
     */
    public enum NotificationPriority {
        LOW, MEDIUM, HIGH, VERY_HIGH
    }

    /**
     * NOUVEAU : Classe pour les notifications temporaires avec durée
     */
    private static class TemporaryNotification {
        private final String message;
        private final long expiryTime;

        public TemporaryNotification(String message, long expiryTime) {
            this.message = message;
            this.expiryTime = expiryTime;
        }

        public String getMessage() {
            return message;
        }

        public long getExpiryTime() {
            return expiryTime;
        }
    }

    /**
     * Notification de jeu
     */
    public static class GameNotification {
        private final NotificationType type;
        private final String message;
        private final NotificationPriority priority;

        public GameNotification(NotificationType type, String message, NotificationPriority priority) {
            this.type = type;
            this.message = message;
            this.priority = priority;
        }

        public NotificationType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public NotificationPriority getPriority() {
            return priority;
        }
    }

    /**
     * Accumulateur de gains
     */
    private static class GainAccumulator {
        private final long startTime;
        private long coins;
        private long tokens;
        private long experience;

        public GainAccumulator(long startTime) {
            this.startTime = startTime;
        }

        public void addGains(long coins, long tokens, long experience) {
            this.coins += coins;
            this.tokens += tokens;
            this.experience += experience;
        }

        public boolean hasGains() {
            return coins > 0 || tokens > 0 || experience > 0;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getCoins() {
            return coins;
        }

        public long getTokens() {
            return tokens;
        }

        public long getExperience() {
            return experience;
        }
    }
}