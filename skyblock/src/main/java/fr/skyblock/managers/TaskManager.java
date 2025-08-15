package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {

    private final CustomSkyblock plugin;
    private final List<BukkitTask> runningTasks = new ArrayList<>();

    public TaskManager(CustomSkyblock plugin) {
        this.plugin = plugin;
        startPeriodicTasks();
    }

    private void startPeriodicTasks() {
        // Sauvegarde automatique toutes les 30 minutes
        BukkitTask autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getLogger().info("Sauvegarde automatique en cours...");
                plugin.getDatabaseManager().saveAll();
                plugin.getDatabaseManager().clearCache();
                plugin.getEconomyManager().saveAllBalances();
                plugin.getLogger().info("Sauvegarde automatique terminée !");
            }
        }.runTaskTimerAsynchronously(plugin, 36000L, 36000L); // 30 minutes

        runningTasks.add(autoSaveTask);

        // Revenus passifs des îles toutes les heures
        if (plugin.getConfig().getBoolean("economy.passive-income", true)) {
            int incomeInterval = plugin.getConfig().getInt("economy.income-interval", 60) * 60 * 20; // Convertir en ticks

            BukkitTask incomeTask = new BukkitRunnable() {
                @Override
                public void run() {
                    plugin.getEconomyManager().processIslandIncome();
                }
            }.runTaskTimer(plugin, incomeInterval, incomeInterval);

            runningTasks.add(incomeTask);
        }

        // Nettoyage des données toutes les 10 minutes
        BukkitTask cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Nettoyer les caches si nécessaire
                cleanupCaches();

                // Nettoyer les mondes orphelins
                plugin.getWorldManager().cleanupMissingWorlds();
                plugin.getIslandManager().cleanupOrphanedIslands();
            }
        }.runTaskTimer(plugin, 12000L, 12000L); // 10 minutes

        runningTasks.add(cleanupTask);

        // Statistiques de serveur toutes les 5 minutes (si debug activé)
        if (plugin.getConfig().getBoolean("debug.enabled", false)) {
            BukkitTask statsTask = new BukkitRunnable() {
                @Override
                public void run() {
                    logServerStats();
                }
            }.runTaskTimer(plugin, 6000L, 6000L); // 5 minutes

            runningTasks.add(statsTask);
        }

        plugin.getLogger().info("Tâches périodiques démarrées (" + runningTasks.size() + " tâches)");
    }

    private void cleanupCaches() {
        // Nettoyer les caches si ils deviennent trop volumineux
        int totalIslands = plugin.getDatabaseManager().getTotalIslands();
        int totalPlayers = plugin.getDatabaseManager().getTotalPlayers();

        // Si plus de 1000 îles ou joueurs, nettoyer le cache
        if (totalIslands > 1000 || totalPlayers > 1000) {
            plugin.getDatabaseManager().clearCache();
            plugin.getLogger().info("Cache nettoyé - Îles: " + totalIslands + ", Joueurs: " + totalPlayers);
        }
    }

    private void logServerStats() {
        int totalIslands = plugin.getDatabaseManager().getTotalIslands();
        int totalPlayers = plugin.getDatabaseManager().getTotalPlayers();
        int activeIslands7d = plugin.getDatabaseManager().getActiveIslands(7);
        int activeIslands30d = plugin.getDatabaseManager().getActiveIslands(30);
        int islandWorlds = plugin.getWorldManager().getIslandWorldCount();

        plugin.getLogger().info("=== STATISTIQUES SKYBLOCK ===");
        plugin.getLogger().info("Total îles: " + totalIslands);
        plugin.getLogger().info("Total joueurs: " + totalPlayers);
        plugin.getLogger().info("Mondes d'îles: " + islandWorlds);
        plugin.getLogger().info("Îles actives (7j): " + activeIslands7d);
        plugin.getLogger().info("Îles actives (30j): " + activeIslands30d);

        // Statistiques mémoire
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        plugin.getLogger().info("Mémoire utilisée: " + usedMemory + "MB / " + totalMemory + "MB");
    }

    /**
     * Arrête toutes les tâches périodiques
     */
    public void stopAllTasks() {
        for (BukkitTask task : runningTasks) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        runningTasks.clear();
        plugin.getLogger().info("Toutes les tâches périodiques ont été arrêtées");
    }

    /**
     * Redémarre toutes les tâches périodiques
     */
    public void restartTasks() {
        stopAllTasks();
        startPeriodicTasks();
    }

    /**
     * Obtient le nombre de tâches en cours d'exécution
     */
    public int getRunningTasksCount() {
        return (int) runningTasks.stream().filter(task -> task != null && !task.isCancelled()).count();
    }
}