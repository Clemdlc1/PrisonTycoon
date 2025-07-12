package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Gestionnaire des notifications Greed avec file d'attente et cumul
 * NOUVEAU : Système intelligent pour éviter le spam et gérer plusieurs notifications
 */
public class NotificationManager {

    private final PrisonTycoon plugin;

    // File d'attente des notifications par joueur
    private final Map<UUID, Queue<GreedNotification>> playerNotificationQueues;

    // Cumul des notifications du même type par joueur
    private final Map<UUID, Map<String, GreedAccumulator>> playerAccumulators;

    // Dernière notification envoyée par joueur
    private final Map<UUID, Long> lastNotificationTime;

    // Configuration
    private static final long NOTIFICATION_COOLDOWN = 1000; // 1 seconde entre notifications
    private static final long ACCUMULATION_WINDOW = 3000; // 3 secondes pour cumuler même type
    private static final int MAX_QUEUE_SIZE = 10; // Maximum 10 notifications en attente

    public NotificationManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerNotificationQueues = new ConcurrentHashMap<>();
        this.playerAccumulators = new ConcurrentHashMap<>();
        this.lastNotificationTime = new ConcurrentHashMap<>();

        plugin.getPluginLogger().info("§aNotificationManager initialisé.");
    }

    /**
     * Ajoute une notification Greed à la file d'attente
     */
    public void queueGreedNotification(Player player, String greedType, long amount, String currency) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Récupère ou crée les structures pour ce joueur
        Queue<GreedNotification> queue = playerNotificationQueues.computeIfAbsent(playerId,
                k -> new ConcurrentLinkedQueue<>());
        Map<String, GreedAccumulator> accumulators = playerAccumulators.computeIfAbsent(playerId,
                k -> new ConcurrentHashMap<>());

        // Clé unique pour ce type de greed
        String key = greedType + "_" + currency;

        // Vérifie si on peut cumuler avec une notification récente du même type
        GreedAccumulator accumulator = accumulators.get(key);

        if (accumulator != null && (now - accumulator.getFirstTime()) < ACCUMULATION_WINDOW) {
            // Cumule avec la notification existante
            accumulator.addAmount(amount);
            plugin.getPluginLogger().debug("Greed cumulé pour " + player.getName() + ": " +
                    greedType + " +" + amount + " (total: " + accumulator.getTotalAmount() + ")");
        } else {
            // Crée un nouvel accumulateur
            accumulator = new GreedAccumulator(greedType, currency, amount, now);
            accumulators.put(key, accumulator);

            // Ajoute à la file d'attente si pas trop pleine
            if (queue.size() < MAX_QUEUE_SIZE) {
                queue.offer(new GreedNotification(key, accumulator));
                plugin.getPluginLogger().debug("Nouvelle notification Greed ajoutée pour " + player.getName() +
                        ": " + greedType + " +" + amount);
            } else {
                plugin.getPluginLogger().debug("File d'attente pleine pour " + player.getName() +
                        ", notification ignorée");
            }
        }
    }

    /**
     * Traite les notifications en attente pour un joueur
     */
    public void processNotifications(Player player) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Vérifie le cooldown
        Long lastTime = lastNotificationTime.get(playerId);
        if (lastTime != null && (now - lastTime) < NOTIFICATION_COOLDOWN) {
            return; // Trop tôt pour la prochaine notification
        }

        Queue<GreedNotification> queue = playerNotificationQueues.get(playerId);
        if (queue == null || queue.isEmpty()) {
            return; // Pas de notifications en attente
        }

        // Prend la première notification de la file
        GreedNotification notification = queue.poll();
        if (notification != null) {
            sendNotificationToPlayer(player, notification);
            lastNotificationTime.put(playerId, now);

            // Retire l'accumulateur traité
            Map<String, GreedAccumulator> accumulators = playerAccumulators.get(playerId);
            if (accumulators != null) {
                accumulators.remove(notification.getKey());
            }
        }
    }

    /**
     * Envoie la notification au joueur
     */
    private void sendNotificationToPlayer(Player player, GreedNotification notification) {
        GreedAccumulator accumulator = notification.getAccumulator();

        String colorCode = switch (accumulator.getCurrency()) {
            case "tokens" -> "§e";
            case "coins" -> "§6";
            case "XP" -> "§a";
            default -> {
                if (accumulator.getCurrency().startsWith("clé")) {
                    // Extrait la couleur de la clé
                    if (accumulator.getCurrency().contains("§")) {
                        yield accumulator.getCurrency().substring(0, 2);
                    }
                }
                yield "§f";
            }
        };

        String message;
        if (accumulator.getCount() > 1) {
            // Affichage cumulé
            message = "§l" + accumulator.getGreedType() + " §8x" + accumulator.getCount() + "! " +
                    colorCode + "+" + NumberFormatter.format(accumulator.getTotalAmount()) + " " +
                    accumulator.getCurrency();
        } else {
            // Affichage simple
            message = "§l" + accumulator.getGreedType() + "! " +
                    colorCode + "+" + NumberFormatter.format(accumulator.getTotalAmount()) + " " +
                    accumulator.getCurrency();
        }

        player.sendActionBar(message);

        plugin.getPluginLogger().debug("Notification envoyée à " + player.getName() + ": " + message);
    }

    /**
     * Nettoie les anciens accumulateurs expirés
     */
    public void cleanupExpiredAccumulators() {
        long now = System.currentTimeMillis();

        for (Map<String, GreedAccumulator> accumulators : playerAccumulators.values()) {
            accumulators.entrySet().removeIf(entry ->
                    (now - entry.getValue().getFirstTime()) > ACCUMULATION_WINDOW * 2);
        }
    }

    /**
     * Retire toutes les notifications d'un joueur
     */
    public void clearPlayerNotifications(UUID playerId) {
        playerNotificationQueues.remove(playerId);
        playerAccumulators.remove(playerId);
        lastNotificationTime.remove(playerId);
    }

    /**
     * Obtient les statistiques du système de notifications
     */
    public NotificationStats getStats() {
        int totalQueued = 0;
        int totalAccumulators = 0;

        for (Queue<GreedNotification> queue : playerNotificationQueues.values()) {
            totalQueued += queue.size();
        }

        for (Map<String, GreedAccumulator> accumulators : playerAccumulators.values()) {
            totalAccumulators += accumulators.size();
        }

        return new NotificationStats(
                playerNotificationQueues.size(),
                totalQueued,
                totalAccumulators
        );
    }

    // Classes internes

    /**
     * Notification Greed en attente
     */
    private static class GreedNotification {
        private final String key;
        private final GreedAccumulator accumulator;

        public GreedNotification(String key, GreedAccumulator accumulator) {
            this.key = key;
            this.accumulator = accumulator;
        }

        public String getKey() { return key; }
        public GreedAccumulator getAccumulator() { return accumulator; }
    }

    /**
     * Accumulateur pour les notifications du même type
     */
    private static class GreedAccumulator {
        private final String greedType;
        private final String currency;
        private long totalAmount;
        private int count;
        private final long firstTime;

        public GreedAccumulator(String greedType, String currency, long initialAmount, long firstTime) {
            this.greedType = greedType;
            this.currency = currency;
            this.totalAmount = initialAmount;
            this.count = 1;
            this.firstTime = firstTime;
        }

        public void addAmount(long amount) {
            this.totalAmount += amount;
            this.count++;
        }

        public String getGreedType() { return greedType; }
        public String getCurrency() { return currency; }
        public long getTotalAmount() { return totalAmount; }
        public int getCount() { return count; }
        public long getFirstTime() { return firstTime; }
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