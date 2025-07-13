package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de décroissance de la combustion
 * CORRIGÉ : Décroissance simple et fiable de 1 par seconde pour tous les joueurs
 */
public class CombustionDecayTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private long tickCount = 0;
    private int decayProcessed = 0;

    // Configuration depuis config.yml
    private static final int DECAY_PER_SECOND = 1; // Configurable dans le futur

    public CombustionDecayTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;

        try {
            int playersWithCombustion = 0;
            int totalDecay = 0;

            // Traite tous les joueurs en cache
            for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {

                // Vérifie si le joueur a l'enchantement combustion
                if (playerData.getEnchantmentLevel("combustion") > 0) {
                    long currentCombustion = playerData.getCombustionLevel();

                    if (currentCombustion > 0) {
                        // CORRIGÉ : Décroissance simple et fiable
                        long newCombustionLevel = Math.max(0, currentCombustion - DECAY_PER_SECOND);
                        playerData.setCombustionLevel(newCombustionLevel);

                        playersWithCombustion++;
                        totalDecay += (currentCombustion - newCombustionLevel);

                        // Marque les données comme modifiées si la combustion a changé
                        if (newCombustionLevel != currentCombustion) {
                            plugin.getPlayerDataManager().markDirty(playerData.getPlayerId());
                        }

                        plugin.getPluginLogger().debug("Combustion decay pour " + playerData.getPlayerName() +
                                ": " + currentCombustion + " → " + newCombustionLevel);
                    }
                }
            }

            decayProcessed++;

            // Log périodique pour le debug
            if (decayProcessed % 60 == 0) { // Toutes les minutes
                plugin.getPluginLogger().debug("CombustionDecay cycle #" + decayProcessed +
                        ": " + playersWithCombustion + " joueurs avec combustion, " +
                        totalDecay + " points décrément total");
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans CombustionDecayTask:");
            e.printStackTrace();
        }
    }
}