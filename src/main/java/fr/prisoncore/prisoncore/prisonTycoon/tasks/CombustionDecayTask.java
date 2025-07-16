package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de décroissance de la combustion
 * CORRIGÉ : Décroissance simple et fiable de 1 par seconde pour tous les joueurs
 * MODIFIÉ : Intègre la réduction de décroissance via les cristaux
 */
public class CombustionDecayTask extends BukkitRunnable {

    // Configuration depuis config.yml
    private static final double DECAY_PER_SECOND = 1.0; // Base de décroissance
    private final PrisonTycoon plugin;
    private long tickCount = 0;
    private int decayProcessed = 0;

    public CombustionDecayTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;

        try {
            int playersWithCombustion = 0;
            long totalDecayPoints = 0;

            // Traite tous les joueurs en cache
            for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {

                // Vérifie si le joueur a l'enchantement combustion
                if (playerData.getEnchantmentLevel("combustion") > 0) {
                    long currentCombustion = playerData.getCombustionLevel();

                    if (currentCombustion > 0) {
                        // MODIFIÉ: Calcule la décroissance en appliquant les bonus des cristaux
                        double decayAmount = DECAY_PER_SECOND;
                        Player player = Bukkit.getPlayer(playerData.getPlayerId());

                        // Applique la réduction du cristal seulement si le joueur est en ligne
                        if (player != null && player.isOnline()) {
                            decayAmount = plugin.getCristalBonusHelper().applyCombustionDecayReduction(player, DECAY_PER_SECOND);
                        }

                        long finalDecay = Math.max(0, Math.round(decayAmount)); // Arrondi et s'assure que la décroissance n'est pas négative

                        long newCombustionLevel = Math.max(0, currentCombustion - finalDecay);
                        playerData.setCombustionLevel(newCombustionLevel);

                        playersWithCombustion++;
                        totalDecayPoints += (currentCombustion - newCombustionLevel);

                        // Marque les données comme modifiées si la combustion a changé
                        if (newCombustionLevel != currentCombustion) {
                            plugin.getPlayerDataManager().markDirty(playerData.getPlayerId());
                        }

                        plugin.getPluginLogger().debug("Combustion decay pour " + playerData.getPlayerName() +
                                ": " + currentCombustion + " → " + newCombustionLevel + " (decay: " + finalDecay + ")");
                    }
                }
            }

            decayProcessed++;

            // Log périodique pour le debug
            if (decayProcessed % 60 == 0) { // Toutes les minutes
                plugin.getPluginLogger().debug("CombustionDecay cycle #" + decayProcessed +
                        ": " + playersWithCombustion + " joueurs avec combustion, " +
                        totalDecayPoints + " points décrémentés au total");
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans CombustionDecayTask:");
            e.printStackTrace();
        }
    }
}