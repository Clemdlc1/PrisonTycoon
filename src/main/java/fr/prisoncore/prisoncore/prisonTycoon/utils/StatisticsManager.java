package fr.prisoncore.prisoncore.prisonTycoon.utils;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.managers.EconomyManager;

import java.util.*;

/**
 * Gestionnaire de statistiques et métriques du plugin
 */
public class StatisticsManager {

    private final PrisonTycoon plugin;
    private final Map<String, Object> globalStats;
    private long pluginStartTime;

    public StatisticsManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.globalStats = new HashMap<>();
        this.pluginStartTime = System.currentTimeMillis();

        initializeStats();
    }

    /**
     * Initialise les statistiques
     */
    private void initializeStats() {
        globalStats.put("plugin-start-time", pluginStartTime);
        globalStats.put("total-commands-executed", 0L);
        globalStats.put("total-blocks-mined-globally", 0L);
        globalStats.put("total-enchantments-upgraded", 0L);
        globalStats.put("total-mines-generated", 0L);
        globalStats.put("total-pickaxes-created", 0L);
    }

    /**
     * Obtient toutes les statistiques globales
     */
    public Map<String, Object> getGlobalStatistics() {
        Map<String, Object> stats = new HashMap<>(globalStats);

        // Ajoute les statistiques calculées
        stats.put("plugin-uptime-ms", System.currentTimeMillis() - pluginStartTime);
        stats.put("online-players", plugin.getServer().getOnlinePlayers().size());
        stats.put("cached-players", plugin.getPlayerDataManager().getAllCachedPlayers().size());

        // Statistiques économiques
        var economicStats = plugin.getEconomyManager().getGlobalEconomicStats();
        stats.putAll(economicStats);

        // Statistiques des mines
        var mineStats = plugin.getMineManager().getMineStats();
        stats.putAll(mineStats);

        // Utilisation mémoire
        Runtime runtime = Runtime.getRuntime();
        stats.put("memory-used-mb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        stats.put("memory-total-mb", runtime.totalMemory() / 1024 / 1024);
        stats.put("memory-max-mb", runtime.maxMemory() / 1024 / 1024);

        return stats;
    }

    /**
     * Incrémente une statistique
     */
    public void incrementStat(String statName) {
        incrementStat(statName, 1L);
    }

    /**
     * Incrémente une statistique d'une valeur donnée
     */
    public void incrementStat(String statName, long value) {
        long currentValue = (Long) globalStats.getOrDefault(statName, 0L);
        globalStats.put(statName, currentValue + value);
    }

    /**
     * Définit une statistique
     */
    public void setStat(String statName, Object value) {
        globalStats.put(statName, value);
    }

    /**
     * Obtient les statistiques détaillées d'un joueur
     */
    public Map<String, Object> getPlayerDetailedStats(UUID playerId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        Map<String, Object> stats = new HashMap<>();

        // Statistiques économiques
        stats.put("coins", playerData.getCoins());
        stats.put("tokens", playerData.getTokens());
        stats.put("experience", playerData.getExperience());

        // Statistiques de minage
        stats.put("total-blocks-mined", playerData.getTotalBlocksMined());

        // Statistiques d'enchantements
        var enchantments = playerData.getEnchantmentLevels();
        stats.put("active-enchantments", enchantments.size());
        stats.put("enchantment-levels", enchantments);
        stats.put("auto-upgrade-enabled", playerData.getAutoUpgradeEnabled());

        // États actuels
        stats.put("combustion-level", playerData.getCombustionLevel());
        stats.put("combustion-multiplier", playerData.getCombustionMultiplier());
        stats.put("abundance-active", playerData.isAbundanceActive());

        // Statistiques de la dernière minute
        stats.put("last-minute-coins", playerData.getLastMinuteCoins());
        stats.put("last-minute-tokens", playerData.getLastMinuteTokens());
        stats.put("last-minute-experience", playerData.getLastMinuteExperience());
        stats.put("last-minute-auto-upgrades", playerData.getLastMinuteAutoUpgrades());

        return stats;
    }


    /**
     * Génère un rapport de performance
     */
    public String generatePerformanceReport() {
        StringBuilder report = new StringBuilder();

        report.append("§e📊 Rapport de Performance PrisonTycoon\n");
        report.append("§7" + "=".repeat(50) + "\n");

        var stats = getGlobalStatistics();

        // Temps de fonctionnement
        long uptimeMs = (Long) stats.get("plugin-uptime-ms");
        report.append("§7Temps de fonctionnement: §e").append(NumberFormatter.formatDuration(uptimeMs)).append("\n");

        // Joueurs
        report.append("§7Joueurs en ligne: §e").append(stats.get("online-players")).append("\n");
        report.append("§7Joueurs en cache: §e").append(stats.get("cached-players")).append("\n");

        // Économie
        report.append("§7Total coins: §6").append(NumberFormatter.format((Long) stats.get("total-coins"))).append("\n");
        report.append("§7Total tokens: §e").append(NumberFormatter.format((Long) stats.get("total-tokens"))).append("\n");

        // Mines
        report.append("§7Mines configurées: §a").append(stats.get("total-mines")).append("\n");
        report.append("§7Blocs minés: §b").append(NumberFormatter.format((Long) stats.get("total-blocks-mined"))).append("\n");

        // Mémoire
        long memUsed = (Long) stats.get("memory-used-mb");
        long memTotal = (Long) stats.get("memory-total-mb");
        double memPercent = (double) memUsed / memTotal * 100;
        report.append("§7Mémoire: §c").append(memUsed).append("MB§7/§e").append(memTotal)
                .append("MB §7(§c").append(String.format("%.1f%%", memPercent)).append("§7)\n");

        return report.toString();
    }

    /**
     * Classe pour le classement des joueurs
     */
    public static class PlayerRanking {
        private final String playerName;
        private final long value;

        public PlayerRanking(String playerName, long value) {
            this.playerName = playerName;
            this.value = value;
        }

        public String getPlayerName() { return playerName; }
        public long getValue() { return value; }
    }

    /**
     * Sauvegarde les statistiques
     */
    public void saveStatistics() {
        // Implémentation future pour sauvegarder les stats dans un fichier
        plugin.getPluginLogger().debug("Statistiques sauvegardées");
    }

    /**
     * Remet à zéro certaines statistiques
     */
    public void resetStatistics() {
        globalStats.put("total-commands-executed", 0L);
        globalStats.put("total-blocks-mined-globally", 0L);
        globalStats.put("total-enchantments-upgraded", 0L);
        globalStats.put("total-mines-generated", 0L);
        globalStats.put("total-pickaxes-created", 0L);

        plugin.getPluginLogger().info("§7Statistiques remises à zéro.");
    }
}