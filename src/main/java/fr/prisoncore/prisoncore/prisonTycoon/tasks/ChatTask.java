package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.commands.RankupCommand;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

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
            performAutoRankupIfEnabled(player);
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
     * NOUVEAU : Affichage détaillé des auto-upgrades avec nom d'enchantement et niveau
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
        long keysObtained = playerData.getLastMinuteKeysObtained();

        // NOUVEAU : Récupération des détails des auto-upgrades
        List<PlayerData.AutoUpgradeDetail> upgradeDetails = playerData.getLastMinuteAutoUpgradeDetails();

        // Calcul du pourcentage de remplissage de l'inventaire
        Player player = plugin.getServer().getPlayer(playerData.getPlayerId());
        int inventoryFillPercentage = 0;
        if (player != null) {
            int totalSlots = 36; // Slots principaux de l'inventaire
            int usedSlots = 0;
            for (int i = 0; i < totalSlots; i++) {
                if (player.getInventory().getItem(i) != null) {
                    usedSlots++;
                }
            }
            inventoryFillPercentage = (usedSlots * 100) / totalSlots;
        }


        // 1ère LIGNE : RECAP MINUTE + icône pioche
        summary.append("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        summary.append("\n§6⛏ §e§lRECAP MINUTE§r §6⛏");

        // 2ème LIGNE : Statistiques des blocs + % remplissage inventaire
        summary.append("\n§7┃ §b§lBlocs:§r ");

        // Blocs minés
        summary.append("§a").append(NumberFormatter.format(blocksMined)).append(" minés");

        // Blocs détruits (seulement si différent des blocs minés)
        if (blocksDestroyed > 0 && blocksDestroyed != blocksMined) {
            summary.append(" §8│ §c").append(NumberFormatter.format(blocksDestroyed)).append(" détruits");
        }

        // Blocs récupérés
        if (blocksInventory > 0) {
            summary.append(" §8│ §e").append(NumberFormatter.format(blocksInventory)).append(" récupérés");
        }

        // Pourcentage de remplissage inventaire
        String fillColor = inventoryFillPercentage >= 90 ? "§c" :
                inventoryFillPercentage >= 70 ? "§e" : "§a";
        summary.append(" §8│ §7Inventaire: ").append(fillColor).append(inventoryFillPercentage).append("%");


        // 3ème LIGNE : Gains de TOUS LES GREED (exp, coin, token, xp)
        boolean hasGreedGains = coinsGained > 0 || tokensGained > 0 || expGained > 0 || keysObtained > 0;

        if (hasGreedGains) {
            summary.append("\n§7┃ §d§lGreed Gains:§r ");
            boolean first = true;

            if (coinsGained > 0) {
                summary.append("§6+").append(NumberFormatter.format(coinsGained)).append(" coins");
                first = false;
            }

            if (tokensGained > 0) {
                if (!first) summary.append(" §8│ ");
                summary.append("§e+").append(NumberFormatter.format(tokensGained)).append(" tokens");
                first = false;
            }

            if (expGained > 0) {
                if (!first) summary.append(" §8│ ");
                summary.append("§a+").append(NumberFormatter.format(expGained)).append(" exp");
                first = false;
            }

            if (keysObtained > 0) {
                if (!first) summary.append(" §8│ ");
                summary.append("§b+").append(NumberFormatter.format(keysObtained)).append(" clés");
            }
        } else {
            summary.append("\n§7┃ §d§lGreed Gains:§r §8Aucun gain cette minute");
        }

        // 4ème LIGNE : Auto-améliorations avec détails des enchantements
        if (!upgradeDetails.isEmpty()) {
            summary.append("\n§7┃ §5§lAuto-Upgrades:§r ");

            if (upgradeDetails.size() == 1) {
                // Un seul enchantement amélioré - Affichage compact sur une ligne
                PlayerData.AutoUpgradeDetail detail = upgradeDetails.get(0);
                summary.append("§d").append(detail.getDisplayName())
                        .append(" §7(§a+").append(detail.getLevelsGained()).append("§7) ")
                        .append("§8→ §eNiv. ").append(detail.getNewLevel());
            } else {
                // Plusieurs enchantements améliorés
                int totalUpgrades = upgradeDetails.stream()
                        .mapToInt(PlayerData.AutoUpgradeDetail::getLevelsGained)
                        .sum();
                summary.append("§d").append(totalUpgrades).append(" améliorations effectuées");

                // Détails sur la ligne suivante si pas trop d'enchantements
                if (upgradeDetails.size() <= 3) {
                    summary.append("\n§7┃   §8• ");
                    for (int i = 0; i < upgradeDetails.size(); i++) {
                        PlayerData.AutoUpgradeDetail detail = upgradeDetails.get(i);
                        if (i > 0) summary.append(" §8• ");

                        // Format compact : Nom Niv.X
                        String shortName = detail.getDisplayName().length() > 12 ?
                                detail.getDisplayName().substring(0, 12) + "..." :
                                detail.getDisplayName();
                        summary.append("§d").append(shortName)
                                .append(" §eNiv.").append(detail.getNewLevel());
                    }
                } else {
                    // Trop d'enchantements, affichage condensé
                    summary.append("\n§7┃   §8(Voir votre interface pour les détails)");
                }
            }
        }

        // Séparateur de fin
        summary.append("\n§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        return summary.toString();
    }

    /**
     * NOUVEAU : Effectue l'auto-rankup VIP si activé et si le joueur a miné
     */
    private void performAutoRankupIfEnabled(Player player) {
            RankupCommand rankupCommand = plugin.getRankupCommand();
            if (rankupCommand.canAutoRankup(player)) {
                plugin.getPluginLogger().debug("Tentative d'auto-rankup pour " + player.getName());
                rankupCommand.performAutoRankup(player);
            }
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

        @Override
        public String toString() {
            return String.format("ChatStats{ticks=%d, cycles=%d, players=%d}",
                    totalTicks, summaryCycles, onlinePlayers);
        }
    }
}