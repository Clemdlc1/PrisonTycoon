package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.scheduler.BukkitRunnable; /**
 * Tâche de décroissance de la combustion
 *
 * Fait décroître la combustion de tous les joueurs toutes les secondes
 * quand ils ne minent pas.
 */
public class CombustionDecayTask extends BukkitRunnable {

    private final PrisonTycoon plugin;

    public CombustionDecayTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        try {
            for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {
                if (playerData.getEnchantmentLevel("combustion") > 0) {
                    playerData.decayCombustion();
                }
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans CombustionDecayTask:");
            e.printStackTrace();
        }
    }
}
