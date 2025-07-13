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
                updateActionBarStatus();
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
     * CORRIGÉ : Génère le message d'état pour abondance sans conflit cooldown
     */
    private String generateStatusMessage(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        StringBuilder status = new StringBuilder();

        // Vérifie si le joueur mine actuellement
        boolean currentlyMining = playerData.isCurrentlyMining();

        // NOUVEAU : Vérifie si la pioche est cassée
        boolean isPickaxeBroken = plugin.getEnchantmentManager().isPlayerPickaxeBroken(player);

        // HARMONISATION : Priorité aux messages temporaires de changement d'état
        if (player.hasMetadata("pickaxe_just_broken")) {
            // Message temporaire de casse (reste affiché 3 secondes)
            long brokenTime = player.getMetadata("pickaxe_just_broken").get(0).asLong();
            if (System.currentTimeMillis() - brokenTime < 3000) {
                return "§c💥 PIOCHE CASSÉE! Tous enchantements désactivés sauf Token Greed (90% malus)";
            } else {
                // Retire le metadata après 3 secondes
                player.removeMetadata("pickaxe_just_broken", plugin);
            }
        }

        if (player.hasMetadata("pickaxe_just_repaired")) {
            // Message temporaire de réparation (reste affiché 3 secondes)
            long repairedTime = player.getMetadata("pickaxe_just_repaired").get(0).asLong();
            if (System.currentTimeMillis() - repairedTime < 3000) {
                return "§a✅ Pioche réparée! Tous les enchantements sont actifs";
            } else {
                // Retire le metadata après 3 secondes
                player.removeMetadata("pickaxe_just_repaired", plugin);
            }
        }

        // Si la pioche est cassée (état permanent), affiche le message d'avertissement
        if (isPickaxeBroken) {
            return "§c💀 PIOCHE CASSÉE! Réparez-la pour retrouver ses capacités!";
        }

        // Reste du code existant pour les enchantements normaux...
        if (!currentlyMining) {
            return ""; // Pas de message si pas en train de miner
        }

        // États spéciaux (combustion, abondance, etc.)
        if (playerData.getCombustionLevel() > 0) {
            if (status.length() > 0) status.append(" §8| ");
            double multiplier = playerData.getCombustionMultiplier();
            status.append("§c🔥 Combustion: §6x").append(String.format("%.2f", multiplier));
        }

        if (playerData.isAbundanceActive()) {
            if (status.length() > 0) status.append(" §8| ");
            status.append("§6⭐ Abondance: §aACTIVE");
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