package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Gestionnaire amélioré des notifications avec support multi-types
 * NOUVEAU : Gère Greed, gains normaux, enchantements, clés, etc.
 */
public class NotificationManager {

    private final PrisonTycoon plugin;

    // File d'attente des notifications par joueur
    private final Map<UUID, Queue<GameNotification>> playerNotificationQueues;

    // Accumulateur de gains par joueur (pour regrouper les gains similaires)
    private final Map<UUID, GainAccumulator> playerGainAccumulators;

    // Dernière notification envoyée par joueur
    private final Map<UUID, Long> lastNotificationTime;

    // Configuration
    private static final long NOTIFICATION_COOLDOWN = 800; // 0.8 seconde entre notifications
    private static final long ACCUMULATION_WINDOW = 2500; // 2.5 secondes pour cumuler gains
    private static final int MAX_QUEUE_SIZE = 15; // Maximum 15 notifications en attente

    public NotificationManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerNotificationQueues = new ConcurrentHashMap<>();
        this.playerGainAccumulators = new ConcurrentHashMap<>();
        this.lastNotificationTime = new ConcurrentHashMap<>();

        plugin.getPluginLogger().info("§aNotificationManager amélioré initialisé.");
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
     * NOUVEAU : Ajoute une notification d'enchantement
     */
    public void queueEnchantmentNotification(Player player, String enchantName, int levelsGained) {
        queueNotification(player, new GameNotification(
                NotificationType.ENCHANTMENT,
                "§a⚡ " + enchantName + " §a+" + levelsGained + " niveau" + (levelsGained > 1 ? "x" : "") + "!",
                NotificationPriority.MEDIUM
        ));
    }

    /**
     * NOUVEAU : Ajoute une notification de clé
     */
    public void queueKeyNotification(Player player, String keyType, String keyColor) {
        queueNotification(player, new GameNotification(
                NotificationType.KEY,
                "§e🗝️ Clé " + keyColor + keyType + " §eobtenue!",
                NotificationPriority.HIGH
        ));
    }

    /**
     * NOUVEAU : Ajoute une notification d'état spécial
     */
    public void queueSpecialStateNotification(Player player, String stateName, String details) {
        queueNotification(player, new GameNotification(
                NotificationType.SPECIAL_STATE,
                "§6✨ " + stateName + " " + details,
                NotificationPriority.VERY_HIGH
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
     * Traite les notifications en attente pour un joueur
     */
    public void processNotifications(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Vérifie d'abord l'accumulateur de gains
        GainAccumulator accumulator = playerGainAccumulators.get(playerId);
        if (accumulator != null && accumulator.hasGains() &&
                now - accumulator.getStartTime() > ACCUMULATION_WINDOW) {

            queueNotification(player, createGainNotification(accumulator));
            playerGainAccumulators.remove(playerId);
        }

        // Vérifie le cooldown
        Long lastTime = lastNotificationTime.get(playerId);
        if (lastTime != null && (now - lastTime) < NOTIFICATION_COOLDOWN) {
            return;
        }

        Queue<GameNotification> queue = playerNotificationQueues.get(playerId);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        // Prend la notification de plus haute priorité
        GameNotification notification = getHighestPriorityNotification(queue);
        if (notification != null) {
            sendNotificationToPlayer(player, notification);
            lastNotificationTime.put(playerId, now);
        }
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

        String message = "§b⛏️ Gains: " + String.join("§7, ", parts);

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
                    if (currency.contains("§")) {
                        yield currency.substring(0, 2);
                    }
                }
                yield "§f";
            }
        };
    }

    /**
     * Nettoie les anciens accumulateurs expirés
     */
    public void cleanupExpiredAccumulators() {
        long now = System.currentTimeMillis();

        playerGainAccumulators.entrySet().removeIf(entry ->
                now - entry.getValue().getStartTime() > ACCUMULATION_WINDOW * 3);
    }

    /**
     * Retire toutes les notifications d'un joueur
     */
    public void clearPlayerNotifications(UUID playerId) {
        playerNotificationQueues.remove(playerId);
        playerGainAccumulators.remove(playerId);
        lastNotificationTime.remove(playerId);
    }

    /**
     * Obtient les statistiques du système de notifications
     */
    public NotificationStats getStats() {
        int totalQueued = 0;
        int totalAccumulators = playerGainAccumulators.size();

        for (Queue<GameNotification> queue : playerNotificationQueues.values()) {
            totalQueued += queue.size();
        }

        return new NotificationStats(
                playerNotificationQueues.size(),
                totalQueued,
                totalAccumulators
        );
    }

    // Classes internes

    /**
     * Notification de jeu améliorée
     */
    private static class GameNotification {
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
        private long coins;
        private long tokens;
        private long experience;
        private final long startTime;

        public GainAccumulator(long startTime) {
            this.startTime = startTime;
            this.coins = 0;
            this.tokens = 0;
            this.experience = 0;
        }

        public void addGains(long coins, long tokens, long experience) {
            this.coins += coins;
            this.tokens += tokens;
            this.experience += experience;
        }

        public boolean hasGains() {
            return coins > 0 || tokens > 0 || experience > 0;
        }

        public long getCoins() { return coins; }
        public long getTokens() { return tokens; }
        public long getExperience() { return experience; }
        public long getStartTime() { return startTime; }
    }

    /**
     * Types de notifications
     */
    public enum NotificationType {
        REGULAR_GAINS,
        GREED,
        ENCHANTMENT,
        KEY,
        SPECIAL_STATE,
        SPECIAL_EFFECT
    }

    /**
     * Priorités des notifications
     */
    public enum NotificationPriority {
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }

    /**
     * Statistiques du système de notifications
     */
    public static class NotificationStats {
        private final int activePlayerQueues;
        private final int totalQueuedNotifications;
        private final int totalActiveAccumulators;

        public NotificationStats(int activePlayerQueues, int totalQueuedNotifications, int totalActiveAccumulators) {
            this.activePlayerQueues = activePlayerQueues;
            this.totalQueuedNotifications = totalQueuedNotifications;
            this.totalActiveAccumulators = totalActiveAccumulators;
        }

        public int getActivePlayerQueues() { return activePlayerQueues; }
        public int getTotalQueuedNotifications() { return totalQueuedNotifications; }
        public int getTotalActiveAccumulators() { return totalActiveAccumulators; }

        @Override
        public String toString() {
            return String.format("NotificationStats{queues=%d, notifications=%d, accumulators=%d}",
                    activePlayerQueues, totalQueuedNotifications, totalActiveAccumulators);
        }
    }
}