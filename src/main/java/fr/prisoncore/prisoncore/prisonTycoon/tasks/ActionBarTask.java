package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche d'affichage de l'état des enchantements dans l'Action Bar
 * MODIFIÉ : Affiche combustion et abondance au lieu des notifications Greed
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
            // Met à jour l'action bar toutes les 20 ticks (1 seconde)
            if (tickCount % 20 == 0) {
                updateActionBarStatus();
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans ActionBarTask:");
            e.printStackTrace();
        }
    }

    /**
     * Met à jour l'action bar avec l'état des enchantements actifs
     */
    private void updateActionBarStatus() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String statusMessage = generateStatusMessage(player);
            if (statusMessage != null && !statusMessage.isEmpty()) {
                player.sendActionBar(statusMessage);
            }
        }
    }

    /**
     * MODIFIÉ : Génère le message d'état pour un joueur avec conditions mining
     */
    private String generateStatusMessage(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        StringBuilder status = new StringBuilder();

        // NOUVEAU : Vérifie si le joueur mine actuellement
        boolean currentlyMining = playerData.isCurrentlyMining();

        // Combustion (si débloqué ET le joueur mine actuellement)
        int combustionLevel = playerData.getEnchantmentLevel("combustion");
        if (combustionLevel > 0 && currentlyMining) {
            long currentCombustion = playerData.getCombustionLevel();

            if (currentCombustion > 0) {
                double multiplier = playerData.getCombustionMultiplier();

                // Couleur selon le niveau de combustion
                String combustionColor = getCombustionColor(currentCombustion);

                status.append("§c🔥 Combustion: ")
                        .append(combustionColor)
                        .append(currentCombustion)
                        .append("§7/§e1000 ")
                        .append("§6(x")
                        .append(String.format("%.2f", multiplier))
                        .append(")");
            }
        }

        // MODIFIÉ : Abondance avec cooldown et condition mining
        int abundanceLevel = playerData.getEnchantmentLevel("abundance");
        if (abundanceLevel > 0 && currentlyMining) {
            // Si l'enchantement est débloqué ET que le joueur mine :

            if (playerData.isAbundanceActive()) {
                // Abondance est active
                if (status.length() > 0) {
                    status.append(" §8• ");
                }
                status.append("§6⭐ Abondance: §a✨ ACTIVE §7(x2 gains)");

            } else if (playerData.isAbundanceOnCooldown()) {
                // Abondance est en cooldown
                if (status.length() > 0) {
                    status.append(" §8• ");
                }
                long cooldownSeconds = playerData.getAbundanceCooldownSecondsLeft();
                long minutes = cooldownSeconds / 60;
                long seconds = cooldownSeconds % 60;

                status.append("§6⭐ Abondance: §c⏰ Cooldown ");
                if (minutes > 0) {
                    status.append(minutes).append("m ");
                }
                status.append(seconds).append("s");
            }
        }
        return status.toString();
    }

    /**
     * Retourne la couleur selon le niveau de combustion
     */
    private String getCombustionColor(long combustionLevel) {
        if (combustionLevel >= 750) return "§c"; // Rouge vif - très haut
        if (combustionLevel >= 500) return "§6"; // Orange - haut
        if (combustionLevel >= 250) return "§e"; // Jaune - moyen
        if (combustionLevel >= 100) return "§a"; // Vert - bas
        return "§7"; // Gris - très bas
    }

    /**
     * Obtient les statistiques de la tâche
     */
    public ActionBarStats getStats() {
        return new ActionBarStats(
                tickCount,
                plugin.getServer().getOnlinePlayers().size()
        );
    }

    /**
     * Statistiques de l'ActionBarTask
     */
    public static class ActionBarStats {
        private final long totalTicks;
        private final int onlinePlayers;

        public ActionBarStats(long totalTicks, int onlinePlayers) {
            this.totalTicks = totalTicks;
            this.onlinePlayers = onlinePlayers;
        }

        public long getTotalTicks() { return totalTicks; }
        public int getOnlinePlayers() { return onlinePlayers; }

        @Override
        public String toString() {
            return String.format("ActionBarStats{ticks=%d, players=%d}",
                    totalTicks, onlinePlayers);
        }
    }
}