package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Gestionnaire amélioré des notifications avec support multi-types et gestion de durée
 * NOUVEAU : Gère les notifications temporaires avec durée et priorité
 */
public class NotificationManager {

    private final PrisonTycoon plugin;

    // File d'attente des notifications par joueur
    private final Map<UUID, Queue<GameNotification>> playerNotificationQueues;

    // Accumulateur de gains par joueur (pour regrouper les gains similaires)
    private final Map<UUID, GainAccumulator> playerGainAccumulators;

    // Dernière notification envoyée par joueur
    private final Map<UUID, Long> lastNotificationTime;

    // NOUVEAU : Notifications temporaires avec durée (pour les notifications de durabilité)
    private final Map<UUID, TemporaryNotification> activeTemporaryNotifications;

    // Configuration
    private static final long ACCUMULATION_WINDOW = 2500; // 2.5 secondes pour cumuler gains
    private static final int MAX_QUEUE_SIZE = 15; // Maximum 15 notifications en attente

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
     * NOUVEAU : Ajoute une notification de gains réguliers (mining de base)
     */
    public void queueRegularGains(Player player, long coins, long tokens, long experience) {
        if (coins <= 0 && tokens <= 0 && experience <= 0) return;

        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Récupère ou crée l'accumulateur de gains
        GainAccumulator accumulator = playerGainAccumulators.computeIfAbsent(playerId,
                k -> new GainAccumulator(now));

        // Si la fenêtre d'accumulation est expirée, crée un nouveau
        if (now - accumulator.getStartTime() > ACCUMULATION_WINDOW) {
            // Envoie l'ancien accumulateur s'il a des gains
            if (accumulator.hasGains()) {
                queueNotification(player, createGainNotification(accumulator));
            }
            // Crée un nouvel accumulateur
            accumulator = new GainAccumulator(now);
            playerGainAccumulators.put(playerId, accumulator);
        }

        // Ajoute les gains à l'accumulateur
        accumulator.addGains(coins, tokens, experience);
    }

    /**
     * LEGACY : Ajoute une notification Greed (compatibilité)
     */
    public void queueGreedNotification(Player player, String greedType, long amount, String currency) {
        queueNotification(player, new GameNotification(
                NotificationType.GREED,
                "§l" + greedType + "! " + getColorForCurrency(currency) + "+" +
                        NumberFormatter.format(amount) + " " + currency,
                NotificationPriority.HIGH
        ));
    }

    /**
     * NOUVEAU : Ajoute une notification d'effet spécial (laser, explosion)
     */
    public void queueSpecialEffectNotification(Player player, String effectName, int blocksAffected) {
        queueNotification(player, new GameNotification(
                NotificationType.SPECIAL_EFFECT,
                "§d💥 " + effectName + "! §e" + blocksAffected + " blocs détruits!",
                NotificationPriority.HIGH
        ));
    }

    /**
     * Méthode centrale pour ajouter une notification
     */
    private void queueNotification(Player player, GameNotification notification) {
        UUID playerId = player.getUniqueId();

        Queue<GameNotification> queue = playerNotificationQueues.computeIfAbsent(playerId,
                k -> new ConcurrentLinkedQueue<>());

        // Vérifie la taille de la file
        if (queue.size() >= MAX_QUEUE_SIZE) {
            // Retire la plus ancienne notification de priorité normale/basse
            queue.removeIf(notif -> notif.getPriority().ordinal() <= NotificationPriority.MEDIUM.ordinal());
        }

        queue.offer(notification);
        plugin.getPluginLogger().debug("Notification ajoutée pour " + player.getName() +
                ": " + notification.getMessage());
    }

    /**
     * Récupère la notification de plus haute priorité
     */
    private GameNotification getHighestPriorityNotification(Queue<GameNotification> queue) {
        if (queue.isEmpty()) return null;

        GameNotification highest = null;
        for (GameNotification notification : queue) {
            if (highest == null || notification.getPriority().ordinal() > highest.getPriority().ordinal()) {
                highest = notification;
            }
        }

        if (highest != null) {
            queue.remove(highest);
        }
        return highest;
    }

    /**
     * Crée une notification de gains accumulés
     */
    private GameNotification createGainNotification(GainAccumulator accumulator) {
        List<String> parts = new ArrayList<>();

        if (accumulator.getCoins() > 0) {
            parts.add("§6+" + NumberFormatter.format(accumulator.getCoins()) + " coins");
        }
        if (accumulator.getTokens() > 0) {
            parts.add("§e+" + NumberFormatter.format(accumulator.getTokens()) + " tokens");
        }
        if (accumulator.getExperience() > 0) {
            parts.add("§a+" + NumberFormatter.format(accumulator.getExperience()) + " XP");
        }

        String message = "§b⛏ Gains: " + String.join("§7, ", parts);

        return new GameNotification(
                NotificationType.REGULAR_GAINS,
                message,
                NotificationPriority.LOW
        );
    }

    /**
     * Envoie la notification au joueur
     */
    private void sendNotificationToPlayer(Player player, GameNotification notification) {
        player.sendActionBar(notification.getMessage());
        plugin.getPluginLogger().debug("Notification envoyée à " + player.getName() +
                ": " + notification.getMessage());
    }

    /**
     * Obtient la couleur pour un type de monnaie
     */
    private String getColorForCurrency(String currency) {
        return switch (currency.toLowerCase()) {
            case "tokens" -> "§e";
            case "coins" -> "§6";
            case "xp", "experience" -> "§a";
            default -> {
                if (currency.startsWith("clé")) {
                    yield "§e";
                }
                yield "§f";
            }
        };
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
     * NOUVEAU : Classe pour les notifications temporaires avec durée
     */
    private static class TemporaryNotification {
        private final String message;
        private final long expiryTime;

        public TemporaryNotification(String message, long expiryTime) {
            this.message = message;
            this.expiryTime = expiryTime;
        }

        public String getMessage() { return message; }
        public long getExpiryTime() { return expiryTime; }
    }

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

        public NotificationType getType() { return type; }
        public String getMessage() { return message; }
        public NotificationPriority getPriority() { return priority; }
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

        public long getStartTime() { return startTime; }
        public long getCoins() { return coins; }
        public long getTokens() { return tokens; }
        public long getExperience() { return experience; }
    }
}