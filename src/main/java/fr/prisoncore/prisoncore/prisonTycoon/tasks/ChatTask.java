package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de récapitulatif minute dans le chat
 * OPTIMISÉ : Déclenche les auto-upgrades juste avant le récapitulatif pour inclure les améliorations
 */
public class ChatTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private long tickCount = 0;
    private int summaryCycles = 0;

    public ChatTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;

        try {
            // NOUVEAU : Traite TOUS les auto-upgrades AVANT les récapitulatifs
            AutoUpgradeTask.AutoUpgradeResult autoUpgradeResult = processAutoUpgrades();

            // Envoie les récapitulatifs (qui incluront maintenant les auto-upgrades)
            sendMinuteSummaries(autoUpgradeResult);
            summaryCycles++;

            // Reset des stats minute après envoi
            resetAllMinuteStats();

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans ChatTask:");
            e.printStackTrace();
        }
    }

    /**
     * NOUVEAU : Traite tous les auto-upgrades via l'AutoUpgradeTask
     */
    private AutoUpgradeTask.AutoUpgradeResult processAutoUpgrades() {
        if (plugin.getAutoUpgradeTask() != null) {
            return plugin.getAutoUpgradeTask().processAllAutoUpgrades();
        } else {
            return new AutoUpgradeTask.AutoUpgradeResult(0, 0, 0, 0);
        }
    }

    /**
     * MODIFIÉ : Envoie les récapitulatifs avec les résultats des auto-upgrades
     */
    private void sendMinuteSummaries(AutoUpgradeTask.AutoUpgradeResult autoUpgradeResult) {
        int summariesSent = 0;

        plugin.getPluginLogger().debug("Vérification récapitulatif minute pour " +
                plugin.getServer().getOnlinePlayers().size() + " joueurs (cycle #" + summaryCycles +
                ") - Auto-upgrades: " + autoUpgradeResult.getTotalUpgrades());

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (sendMinuteSummaryIfActive(player, autoUpgradeResult)) {
                summariesSent++;
            }
        }

        if (summariesSent > 0) {
            plugin.getPluginLogger().info("Récapitulatifs minute envoyés à " + summariesSent + " joueurs actifs" +
                    " (Auto-upgrades global: " + autoUpgradeResult.getTotalUpgrades() + " améliorations)");
        } else {
            plugin.getPluginLogger().debug("Aucun joueur actif cette minute (cycle #" + summaryCycles + ")");
        }
    }

    /**
     * MODIFIÉ : Envoie le récapitulatif avec prise en compte des auto-upgrades
     */
    private boolean sendMinuteSummaryIfActive(Player player, AutoUpgradeTask.AutoUpgradeResult globalAutoUpgrades) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Critères d'activité (auto-upgrades comptent maintenant)
        boolean hasBlockActivity = playerData.getLastMinuteBlocksMined() > 0 ||
                playerData.getLastMinuteBlocksDestroyed() > 0;
        boolean hasEconomicActivity = playerData.getLastMinuteCoins() > 0 ||
                playerData.getLastMinuteTokens() > 0 ||
                playerData.getLastMinuteExperience() > 0;
        boolean hasEnchantActivity = playerData.getLastMinuteGreedTriggers() > 0 ||
                playerData.getLastMinuteAutoUpgrades() > 0 ||
                playerData.getLastMinuteKeysObtained() > 0;

        boolean hasActivity = hasBlockActivity || hasEconomicActivity || hasEnchantActivity;

        plugin.getPluginLogger().debug("Activité pour " + player.getName() + ": " +
                "blocs=" + hasBlockActivity + " (" + playerData.getLastMinuteBlocksMined() + " minés, " +
                playerData.getLastMinuteBlocksDestroyed() + " détruits), " +
                "économie=" + hasEconomicActivity + " (" + playerData.getLastMinuteCoins() + "c, " +
                playerData.getLastMinuteTokens() + "t, " + playerData.getLastMinuteExperience() + "e), " +
                "enchants=" + hasEnchantActivity + " (" + playerData.getLastMinuteGreedTriggers() + " greeds, " +
                playerData.getLastMinuteAutoUpgrades() + " auto-upgrades)");

        if (!hasActivity) {
            plugin.getPluginLogger().debug("Aucune activité pour " + player.getName() + " cette minute");
            return false;
        }

        plugin.getPluginLogger().debug("Activité détectée pour " + player.getName() + ", génération du récapitulatif");

        // Génère et envoie le récapitulatif complet avec auto-upgrades
        String summary = generateCompleteSummary(playerData, globalAutoUpgrades);
        if (summary != null && !summary.isEmpty()) {
            player.sendMessage(summary);

            plugin.getPluginLogger().debug("Récapitulatif minute envoyé à " + player.getName() +
                    " (inclut " + playerData.getLastMinuteAutoUpgrades() + " auto-upgrades)");
            return true;
        }

        return false;
    }

    /**
     * Reset les stats minute pour tous les joueurs après envoi
     */
    private void resetAllMinuteStats() {
        int resetCount = 0;
        for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {
            playerData.resetLastMinuteStats();
            resetCount++;
        }

        plugin.getPluginLogger().debug("Stats minute reset pour " + resetCount + " joueurs");
    }

    /**
     * AMÉLIORÉ : Génère un récapitulatif avec auto-upgrades inclus
     */
    private String generateCompleteSummary(PlayerData playerData, AutoUpgradeTask.AutoUpgradeResult globalAutoUpgrades) {
        StringBuilder summary = new StringBuilder();

        // Variables pour le récapitulatif
        long blocksMined = playerData.getLastMinuteBlocksMined();
        long blocksDestroyed = playerData.getLastMinuteBlocksDestroyed();
        long blocksInventory = playerData.getLastMinuteBlocksAddedToInventory();
        long coinsGained = playerData.getLastMinuteCoins();
        long tokensGained = playerData.getLastMinuteTokens();
        long expGained = playerData.getLastMinuteExperience();
        int autoUpgrades = playerData.getLastMinuteAutoUpgrades();
        long greeds = playerData.getLastMinuteGreedTriggers();
        long keysObtained = playerData.getLastMinuteKeysObtained();

        // En-tête compact (ligne 1)
        summary.append("§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Titre compact (ligne 2)
        summary.append("\n§e📊 §lRÉCAP MINUTE §8• §7");

        // Ligne minage si applicable (ligne 3-4)
        if (blocksMined > 0 || blocksDestroyed > 0 || blocksInventory > 0) {
            summary.append("\n§b⛏️ §lMinage: §3").append(NumberFormatter.format(blocksMined)).append(" minés");

            if (blocksDestroyed > blocksMined) {
                summary.append(" §8+ §5").append(NumberFormatter.format(blocksDestroyed - blocksMined)).append(" détruits");
            }

            if (blocksInventory > 0) {
                summary.append("\n§6📦 §lInventaire: §e+").append(NumberFormatter.format(blocksInventory)).append(" blocs récupérés");
            }
        }

        // Ligne gains économiques (ligne 5)
        if (coinsGained > 0 || tokensGained > 0 || expGained > 0 || keysObtained > 0) {
            summary.append("\n§6💰 §lGains: ");
            boolean first = true;

            if (coinsGained > 0) {
                summary.append("§6+").append(NumberFormatter.format(coinsGained)).append(" coins");
                first = false;
            }
            if (tokensGained > 0) {
                if (!first) summary.append(" §8• ");
                summary.append("§e+").append(NumberFormatter.format(tokensGained)).append(" tokens");
                first = false;
            }
            if (expGained > 0) {
                if (!first) summary.append(" §8• ");
                summary.append("§a+").append(NumberFormatter.format(expGained)).append(" exp");

            }
            if (keysObtained > 0) {
                if (!first) summary.append(" §8• ");
                summary.append("§e").append(NumberFormatter.format(keysObtained)).append(" clé").append(keysObtained > 1 ? "s" : "");
            }
        }

        // NOUVEAU : Ligne enchantements avec auto-upgrades prioritaires (ligne 6)
        if (autoUpgrades > 0) {
            summary.append("\n§d✨ §lEnchants: ");
            summary.append("§a⚡").append(autoUpgrades).append(" auto-upgrade").append(autoUpgrades > 1 ? "s" : "");;
        }

        // Séparateur (ligne 8)
        summary.append("\n§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // AMÉLIORÉ : Message motivation avec mention auto-upgrades (ligne 9)
        summary.append("\n§7Continuez votre progression! §e⛏️ §7Total blocs minés: §b").append(NumberFormatter.format(playerData.getTotalBlocksMined()));

        if (autoUpgrades > 0) {
            summary.append(" §8• §a⚡ Auto-upgrades actifs!");
        }

        return summary.toString();
    }

    /**
     * Obtient les statistiques de la tâche
     */
    public ChatStats getStats() {
        return new ChatStats(
                tickCount,
                summaryCycles,
                plugin.getServer().getOnlinePlayers().size()
        );
    }

    /**
     * Statistiques de la ChatTask
     */
    public static class ChatStats {
        private final long totalTicks;
        private final int summaryCycles;
        private final int onlinePlayers;

        public ChatStats(long totalTicks, int summaryCycles, int onlinePlayers) {
            this.totalTicks = totalTicks;
            this.summaryCycles = summaryCycles;
            this.onlinePlayers = onlinePlayers;
        }

        public long getTotalTicks() { return totalTicks; }
        public int getSummaryCycles() { return summaryCycles; }
        public int getOnlinePlayers() { return onlinePlayers; }

        @Override
        public String toString() {
            return String.format("ChatStats{ticks=%d, cycles=%d, players=%d}",
                    totalTicks, summaryCycles, onlinePlayers);
        }
    }
}