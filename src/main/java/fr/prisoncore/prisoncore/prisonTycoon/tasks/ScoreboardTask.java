package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de mise à jour des scoreboards
 * NOUVEAU : Mise à jour automatique toutes les 20 secondes (400 ticks)
 */
public class ScoreboardTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private long tickCount = 0;
    private int updateCycles = 0;

    public ScoreboardTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;

        try {
            // Met à jour tous les scoreboards toutes les 20 secondes (400 ticks)
            if (tickCount % 400 == 0) {
                updateAllScoreboards();
                updateCycles++;
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans ScoreboardTask:");
            e.printStackTrace();
        }
    }

    /**
     * Met à jour tous les scoreboards
     */
    private void updateAllScoreboards() {
        int updatedCount = 0;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                plugin.getScoreboardManager().updateScoreboard(player);
                updatedCount++;
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur mise à jour scoreboard pour " + player.getName() + ": " + e.getMessage());
            }
        }

        plugin.getPluginLogger().debug("Scoreboards mis à jour: " + updatedCount + " joueurs (cycle #" + updateCycles + ")");
    }

    /**
     * Force une mise à jour immédiate de tous les scoreboards
     */
    public void forceUpdate() {
        updateAllScoreboards();
        plugin.getPluginLogger().debug("Mise à jour forcée des scoreboards");
    }

    /**
     * Obtient les statistiques de la tâche
     */
    public ScoreboardStats getStats() {
        return new ScoreboardStats(
                tickCount,
                updateCycles,
                plugin.getServer().getOnlinePlayers().size(),
                plugin.getScoreboardManager().getActiveScoreboards()
        );
    }

    /**
     * Statistiques de la ScoreboardTask
     */
    public static class ScoreboardStats {
        private final long totalTicks;
        private final int updateCycles;
        private final int onlinePlayers;
        private final int activeScoreboards;

        public ScoreboardStats(long totalTicks, int updateCycles, int onlinePlayers, int activeScoreboards) {
            this.totalTicks = totalTicks;
            this.updateCycles = updateCycles;
            this.onlinePlayers = onlinePlayers;
            this.activeScoreboards = activeScoreboards;
        }

        public long getTotalTicks() { return totalTicks; }
        public int getUpdateCycles() { return updateCycles; }
        public int getOnlinePlayers() { return onlinePlayers; }
        public int getActiveScoreboards() { return activeScoreboards; }

        @Override
        public String toString() {
            return String.format("ScoreboardStats{ticks=%d, cycles=%d, players=%d, scoreboards=%d}",
                    totalTicks, updateCycles, onlinePlayers, activeScoreboards);
        }
    }
}