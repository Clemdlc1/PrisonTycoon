package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.scheduler.BukkitRunnable; /**
 * Tâche de sauvegarde automatique
 *
 * Sauvegarde automatiquement toutes les données joueurs toutes les 5 minutes.
 */
public class AutoSaveTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private int saveCount = 0;

    public AutoSaveTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        try {
            saveCount++;

            plugin.getPlayerDataManager().saveAllPlayersAsync().thenRun(() -> {
                plugin.getPluginLogger().info("§7Sauvegarde automatique #" + saveCount + " terminée.");

                // Nettoyage du cache tous les 10 sauvegardes (50 minutes)
                if (saveCount % 10 == 0) {
                    plugin.getPlayerDataManager().cleanupCache();
                }
            });

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans AutoSaveTask:");
            e.printStackTrace();
        }
    }
}
