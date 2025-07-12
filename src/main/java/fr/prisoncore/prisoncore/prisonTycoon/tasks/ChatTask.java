package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de récapitulatif minute dans le chat
 * CORRIGÉ : Récapitulatif bien envoyé basé sur les blocs minés
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
     * CORRIGÉ : Envoie le récapitulatif minute si le joueur a eu de l'activité
     */
    private boolean sendMinuteSummaryIfActive(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // CORRIGÉ : Critères plus larges pour l'activité
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
                "enchants=" + hasEnchantActivity + " (" + playerData.getLastMinuteGreedTriggers() + " greeds)");

        if (!hasActivity) {
            plugin.getPluginLogger().debug("Aucune activité pour " + player.getName() + " cette minute");
            return false;
        }

        plugin.getPluginLogger().debug("Activité détectée pour " + player.getName() + ", génération du récapitulatif");

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
     * CORRIGÉ : Génère un récapitulatif complet avec toutes les statistiques
     */
    private String generateCompleteSummary(PlayerData playerData) {
        StringBuilder summary = new StringBuilder();

        // En-tête stylé
        summary.append("\n§7§m                    §r §e📊 RÉCAPITULATIF MINUTE §7§m                    ");

        // Section Minage
        long blocksMined = playerData.getLastMinuteBlocksMined();
        long blocksDestroyed = playerData.getLastMinuteBlocksDestroyed();

        if (blocksMined > 0 || blocksDestroyed > 0) {
            summary.append("\n§6⛏️ §lMINAGE");
            if (blocksMined > 0) {
                summary.append("\n§7│ §bBlocs minés: §3+").append(NumberFormatter.format(blocksMined));
            }
            if (blocksDestroyed > blocksMined) {
                long specialDestroyed = blocksDestroyed - blocksMined;
                summary.append("\n§7│ §dBlocs détruits (laser/explosion): §5+").append(NumberFormatter.format(specialDestroyed));
            }
            if (blocksDestroyed > 0) {
                summary.append("\n§7│ §9Total blocs traités: §1").append(NumberFormatter.format(blocksDestroyed));
            }
        }

        // Section Gains économiques
        long coinsGained = playerData.getLastMinuteCoins();
        long tokensGained = playerData.getLastMinuteTokens();
        long expGained = playerData.getLastMinuteExperience();

        if (coinsGained > 0 || tokensGained > 0 || expGained > 0) {
            summary.append("\n");
            summary.append("\n§6💰 §lGAINS ÉCONOMIQUES");

            if (coinsGained > 0) {
                summary.append("\n§7│ §6Coins gagnés: §e+").append(NumberFormatter.format(coinsGained));
            }
            if (tokensGained > 0) {
                summary.append("\n§7│ §eTokens gagnés: §6+").append(NumberFormatter.format(tokensGained));
            }
            if (expGained > 0) {
                summary.append("\n§7│ §aExpérience gagnée: §2+").append(NumberFormatter.format(expGained));
            }
        }

        // Section Enchantements
        long greedTriggers = playerData.getLastMinuteGreedTriggers();
        int autoUpgrades = playerData.getLastMinuteAutoUpgrades();

        if (greedTriggers > 0 || autoUpgrades > 0) {
            summary.append("\n");
            summary.append("\n§d✨ §lENCHANTEMENTS");

            if (greedTriggers > 0) {
                summary.append("\n§7│ §5Déclenchements Greed: §d").append(NumberFormatter.format(greedTriggers));
            }
            if (autoUpgrades > 0) {
                summary.append("\n§7│ §bAuto-améliorations: §3").append(autoUpgrades);
            }
        }

        // Section Clés et bonus
        long keysObtained = playerData.getLastMinuteKeysObtained();

        if (keysObtained > 0) {
            summary.append("\n");
            summary.append("\n§e🗝️ §lBONUS SPÉCIAUX");
            summary.append("\n§7│ §eClés obtenues: §6").append(NumberFormatter.format(keysObtained));
        }

        // États spéciaux actifs
        if (playerData.getCombustionLevel() > 0 || playerData.isAbundanceActive()) {
            summary.append("\n");
            summary.append("\n§c🔥 §lÉTATS ACTIFS");

            if (playerData.getCombustionLevel() > 0) {
                double multiplier = playerData.getCombustionMultiplier();
                summary.append("\n§7│ §cCombustion: §6x").append(String.format("%.2f", multiplier))
                        .append(" §7(").append(playerData.getCombustionLevel()).append("/1000)");
            }

            if (playerData.isAbundanceActive()) {
                summary.append("\n§7│ §6⭐ Abondance: §aACTIVE §7(x2 gains)");
            }
        }

        // Séparateur entre activité et total
        summary.append("\n");
        summary.append("\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Section Total actuel (toujours affichée)
        summary.append("\n§6📊 §lTOTAL ACTUEL");
        summary.append("\n§7│ §6Coins: §e").append(NumberFormatter.format(playerData.getCoins()));
        summary.append("\n§7│ §eTokens: §6").append(NumberFormatter.format(playerData.getTokens()));
        summary.append("\n§7│ §aExpérience: §2").append(NumberFormatter.format(playerData.getExperience()));
        summary.append("\n§7│ §9Blocs minés: §1").append(NumberFormatter.format(playerData.getTotalBlocksMined()));
        summary.append("\n§7│ §dEnchantements: §5").append(playerData.getEnchantmentLevels().size());

        // Statistiques lifetime intéressantes
        if (playerData.getTotalGreedTriggers() > 0 || playerData.getTotalKeysObtained() > 0) {
            summary.append("\n§7│ §dTotal Greeds: §5").append(NumberFormatter.format(playerData.getTotalGreedTriggers()));
            if (playerData.getTotalKeysObtained() > 0) {
                summary.append("\n§7│ §eTotal clés: §6").append(NumberFormatter.format(playerData.getTotalKeysObtained()));
            }
        }

        // Pied de page
        summary.append("\n§7└ §7Continuez votre progression! ⛏️✨");
        summary.append("\n§7§m                                                  ");

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