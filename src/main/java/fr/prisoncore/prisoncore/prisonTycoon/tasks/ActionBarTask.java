package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de gestion des notifications Action Bar (Greed)
 * NOUVEAU : Se concentre uniquement sur l'affichage des notifications Greed
 */
public class ActionBarTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private long tickCount = 0;

    public ActionBarTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;

        try {
            // Traite les notifications Greed toutes les 10 ticks (0.5 seconde)
            if (tickCount % 10 == 0) {
                processGreedNotifications();
            }

            // Nettoie les accumulateurs expirés toutes les 60 secondes
            if (tickCount % 1200 == 0) {
                plugin.getNotificationManager().cleanupExpiredAccumulators();
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans ActionBarTask:");
            e.printStackTrace();
        }
    }

    /**
     * Traite les notifications Greed pour tous les joueurs en ligne
     */
    private void processGreedNotifications() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.getNotificationManager().processNotifications(player);
        }
    }

    /**
     * Obtient les statistiques de la tâche
     */
    public ActionBarStats getStats() {
        var notificationStats = plugin.getNotificationManager().getStats();
        return new ActionBarStats(
                tickCount,
                plugin.getServer().getOnlinePlayers().size(),
                notificationStats
        );
    }

    /**
     * Statistiques de l'ActionBarTask
     */
    public static class ActionBarStats {
        private final long totalTicks;
        private final int onlinePlayers;
        private final Object notificationStats;

        public ActionBarStats(long totalTicks, int onlinePlayers, Object notificationStats) {
            this.totalTicks = totalTicks;
            this.onlinePlayers = onlinePlayers;
            this.notificationStats = notificationStats;
        }

        public long getTotalTicks() { return totalTicks; }
        public int getOnlinePlayers() { return onlinePlayers; }
        public Object getNotificationStats() { return notificationStats; }

        @Override
        public String toString() {
            return String.format("ActionBarStats{ticks=%d, players=%d, notifications=%s}",
                    totalTicks, onlinePlayers, notificationStats);
        }
    }
}