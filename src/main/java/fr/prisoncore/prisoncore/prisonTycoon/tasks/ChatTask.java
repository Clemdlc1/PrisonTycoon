package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de récapitulatif minute dans le chat
 * CORRIGÉ : Affiche seulement les gains via pioche + auto-upgrades dans le summary
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
            sendMinuteSummaries();
            summaryCycles++;

            // NOUVEAU : Reset des stats minute après envoi
            resetAllMinuteStats();

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans ChatTask:");
            e.printStackTrace();
        }
    }

    /**
     * CORRIGÉ : Envoie les récapitulatifs minute à tous les joueurs actifs
     */
    private void sendMinuteSummaries() {
        int summariesSent = 0;

        plugin.getPluginLogger().debug("Vérification récapitulatif minute pour " +
                plugin.getServer().getOnlinePlayers().size() + " joueurs (cycle #" + summaryCycles + ")");

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (sendMinuteSummaryIfActive(player)) {
                summariesSent++;
            }
        }

        if (summariesSent > 0) {
            plugin.getPluginLogger().info("Récapitulatifs minute envoyés à " + summariesSent + " joueurs actifs");
        } else {
            plugin.getPluginLogger().debug("Aucun joueur actif cette minute (cycle #" + summaryCycles + ")");
        }
    }

    /**
     * CORRIGÉ : Envoie le récapitulatif minute si le joueur a eu de l'activité VIA PIOCHE
     */
    private boolean sendMinuteSummaryIfActive(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // CORRIGÉ : Critères basés sur les gains VIA PIOCHE et auto-upgrades
        boolean hasBlockActivity = playerData.getLastMinuteBlocksMined() > 0 ||
                playerData.getLastMinuteBlocksDestroyed() > 0;
        boolean hasPickaxeEconomicActivity = playerData.getLastMinuteCoinsViaPickaxe() > 0 ||
                playerData.getLastMinuteTokensViaPickaxe() > 0 ||
                playerData.getLastMinuteExperienceViaPickaxe() > 0;
        boolean hasEnchantActivity = playerData.getLastMinuteGreedTriggers() > 0 ||
                playerData.getLastMinuteAutoUpgrades() > 0 ||
                playerData.getLastMinuteKeysObtained() > 0;

        boolean hasActivity = hasBlockActivity || hasPickaxeEconomicActivity || hasEnchantActivity;

        plugin.getPluginLogger().debug("Activité pour " + player.getName() + ": " +
                "blocs=" + hasBlockActivity + " (" + playerData.getLastMinuteBlocksMined() + " minés, " +
                playerData.getLastMinuteBlocksDestroyed() + " détruits), " +
                "économie VIA PIOCHE=" + hasPickaxeEconomicActivity + " (" +
                playerData.getLastMinuteCoinsViaPickaxe() + "c, " +
                playerData.getLastMinuteTokensViaPickaxe() + "t, " +
                playerData.getLastMinuteExperienceViaPickaxe() + "e), " +
                "enchants=" + hasEnchantActivity + " (" + playerData.getLastMinuteGreedTriggers() + " greeds, " +
                playerData.getLastMinuteAutoUpgrades() + " auto-upgrades)");

        if (!hasActivity) {
            plugin.getPluginLogger().debug("Aucune activité VIA PIOCHE pour " + player.getName() + " cette minute");
            return false;
        }

        plugin.getPluginLogger().debug("Activité VIA PIOCHE détectée pour " + player.getName() + ", génération du récapitulatif");

        // Génère et envoie le récapitulatif complet
        String summary = generateCompleteSummary(playerData);
        if (summary != null && !summary.isEmpty()) {
            player.sendMessage(summary);

            plugin.getPluginLogger().debug("Récapitulatif minute envoyé à " + player.getName());
            return true;
        }

        return false;
    }

    /**
     * NOUVEAU : Reset les stats minute pour tous les joueurs après envoi
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
     * CORRIGÉ : Génère un récapitulatif basé sur les gains VIA PIOCHE uniquement
     */
    private String generateCompleteSummary(PlayerData playerData) {
        StringBuilder summary = new StringBuilder();

        // CORRIGÉ : Utilise les gains VIA PIOCHE uniquement
        long blocksMined = playerData.getLastMinuteBlocksMined();
        long blocksDestroyed = playerData.getLastMinuteBlocksDestroyed();
        long blocksInventory = playerData.getLastMinuteBlocksAddedToInventory();
        long coinsGained = playerData.getLastMinuteCoinsViaPickaxe();      // VIA PIOCHE
        long tokensGained = playerData.getLastMinuteTokensViaPickaxe();    // VIA PIOCHE
        long expGained = playerData.getLastMinuteExperienceViaPickaxe();   // VIA PIOCHE
        int autoUpgrades = playerData.getLastMinuteAutoUpgrades();
        long keysObtained = playerData.getLastMinuteKeysObtained();
        long greedTriggers = playerData.getLastMinuteGreedTriggers();

        // En-tête compact (ligne 1)
        summary.append("§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Titre compact (ligne 2)
        summary.append("\n§e📊 §lRÉCAP MINUTE VIA PIOCHE §8• §7");

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

        // Ligne gains économiques VIA PIOCHE (ligne 5)
        if (coinsGained > 0 || tokensGained > 0 || expGained > 0) {
            summary.append("\n§6💰 §lGains via Pioche: ");
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
                summary.append("§e").append(NumberFormatter.format(keysObtained)).append(" clés");
            }
        }

        // NOUVEAU : Ligne enchantements avec auto-upgrades (ligne 6)
        if (autoUpgrades > 0) {
            summary.append("\n§d✨ §lEnchantements: ");
            summary.append("§a").append(autoUpgrades).append(" auto-améliorations");

        }

        // Séparateur (ligne 8)
        summary.append("\n§7§m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

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