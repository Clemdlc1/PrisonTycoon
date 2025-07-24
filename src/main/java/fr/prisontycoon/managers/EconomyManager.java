package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire économique du plugin
 * CORRIGÉ : Synchronisation expérience custom/vanilla améliorée et automatique
 */
public class EconomyManager {

    // Limites de sécurité pour éviter les overflows
    private static final long MAX_CURRENCY_VALUE = Long.MAX_VALUE / 2;
    private static final long MIN_CURRENCY_VALUE = 0;
    private final PrisonTycoon plugin;
    // Cache pour les statistiques économiques
    private final Map<UUID, EconomicStats> playerStats;

    public EconomyManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerStats = new ConcurrentHashMap<>();

        plugin.getPluginLogger().info("§aEconomyManager initialisé.");
    }

    /**
     * Ajoute des coins à un joueur avec validation (TOTAL - toutes sources)
     */
    public boolean addCoins(Player player, long amount) {
        if (amount <= 0) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentCoins = playerData.getCoins();

        // Vérifie les limites
        if (currentCoins + amount > MAX_CURRENCY_VALUE) {
            player.sendMessage("§cLimite de coins atteinte! Maximum: " + NumberFormatter.format(MAX_CURRENCY_VALUE));
            return false;
        }

        playerData.addCoins(amount);
        updateStats(player.getUniqueId(), amount, 0, 0);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        return true;
    }

    /**
     * Ajoute des tokens à un joueur avec validation (TOTAL - toutes sources)
     */
    public boolean addTokens(Player player, long amount) {
        if (amount <= 0) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentTokens = playerData.getTokens();

        // Vérifie les limites
        if (currentTokens + amount > MAX_CURRENCY_VALUE) {
            player.sendMessage("§cLimite de tokens atteinte! Maximum: " + NumberFormatter.format(MAX_CURRENCY_VALUE));
            return false;
        }

        playerData.addTokens(amount);
        updateStats(player.getUniqueId(), 0, amount, 0);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        return true;
    }

    /**
     * CORRIGÉ : Ajoute de l'expérience avec synchronisation vanilla AUTOMATIQUE
     */
    public boolean addExperience(Player player, long amount) {
        if (amount <= 0) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentExp = playerData.getExperience();

        // Vérifie les limites
        if (currentExp + amount > MAX_CURRENCY_VALUE) {
            player.sendMessage("§cLimite d'expérience atteinte! Maximum: " + NumberFormatter.format(MAX_CURRENCY_VALUE));
            return false;
        }

        playerData.addExperience(amount);

        // CORRIGÉ : Synchronisation vanilla AUTOMATIQUE à chaque changement
        updateVanillaExpFromCustom(player, currentExp + amount);

        updateStats(player.getUniqueId(), 0, 0, amount);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        return true;
    }

    /**
     * CORRIGÉ : Met à jour l'expérience vanilla avec formule rééquilibrée et logging
     */
    public void updateVanillaExpFromCustom(Player player, long customExp) {
        if (customExp < 0) customExp = 0; // Sécurité pour ne jamais avoir d'expérience négative

        // NOUVEAU : Définissez ici la quantité d'expérience requise pour chaque niveau.
        // Augmentez cette valeur pour rendre la progression plus longue.
        final long EXP_PAR_NIVEAU = 5000;

        int vanillaLevel = (int) (customExp / EXP_PAR_NIVEAU);

        // Calcule l'EXP nécessaire pour le niveau actuel et le suivant.
        long expPourNiveauActuel = (long) vanillaLevel * EXP_PAR_NIVEAU;
        long expPourNiveauSuivant = (long) (vanillaLevel + 1) * EXP_PAR_NIVEAU;

        // Calcule la quantité d'EXP accumulée dans le niveau en cours.
        long expDansNiveauActuel = customExp - expPourNiveauActuel;
        long expTotalPourNiveau = expPourNiveauSuivant - expPourNiveauActuel; // Ceci sera toujours égal à EXP_PAR_NIVEAU

        // Calcule la progression en pourcentage pour la barre d'expérience.
        float progress = (expTotalPourNiveau > 0) ?
                Math.max(0.0f, Math.min(1.0f, (float) expDansNiveauActuel / expTotalPourNiveau)) : 0f;

        // On vérifie s'il y a un changement réel pour éviter de surcharger le serveur.
        boolean shouldUpdate = false;
        if (player.getLevel() != vanillaLevel) {
            shouldUpdate = true;
        } else if (Math.abs(player.getExp() - progress) > 0.01f) { // Comparaison avec une petite marge d'erreur
            shouldUpdate = true;
        }

        if (shouldUpdate) {
            try {
                // Limite le niveau à la valeur maximale gérée par Minecraft (21863).
                player.setLevel(Math.max(0, Math.min(21863, vanillaLevel)));
                player.setExp(Math.max(0.0f, Math.min(1.0f, progress)));

                // Message de log pour le débogage.
                plugin.getPluginLogger().debug("Sync exp pour " + player.getName() + ": " +
                        "custom=" + customExp + " -> vanilla=" + vanillaLevel + " (+" +
                        String.format("%.1f%%", progress * 100) + ") [" + expDansNiveauActuel + "/" + expTotalPourNiveau + "]");
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur sync exp pour " + player.getName() +
                        ": level=" + vanillaLevel + ", exp=" + progress + " - " + e.getMessage());
            }
        }
    }

    /**
     * NOUVEAU : Synchronise l'expérience vanilla pour tous les joueurs en ligne
     */
    public void syncAllVanillaExp() {
        int synced = 0;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                updateVanillaExpFromCustom(player, playerData.getExperience());
                synced++;
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur sync exp pour " + player.getName() + ": " + e.getMessage());
            }
        }

        if (synced > 0) {
            plugin.getPluginLogger().debug("Synchronisation expérience vanilla pour " + synced + " joueurs");
        }
    }

    /**
     * CORRIGÉ : Initialise l'expérience vanilla d'un joueur à la connexion avec vérification
     */
    public void initializeVanillaExp(Player player) {
        try {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            long customExp = playerData.getExperience();

            plugin.getPluginLogger().debug("Initialisation exp vanilla pour " + player.getName() +
                    ": custom=" + customExp);

            updateVanillaExpFromCustom(player, customExp);
        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur initialisation exp vanilla pour " + player.getName() + ":");
            e.printStackTrace();
        }
    }

    /**
     * Retire des tokens d'un joueur
     */
    public boolean removeTokens(Player player, long amount) {
        if (amount <= 0) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getTokens() < amount) {
            player.sendMessage("§cTokens insuffisants! Requis: " + NumberFormatter.format(amount));
            return false;
        }

        boolean success = playerData.removeTokens(amount);
        if (success) {
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        }

        return success;
    }

    /**
     * Met à jour les statistiques économiques d'un joueur
     */
    private void updateStats(UUID playerId, long coinsGained, long tokensGained, long expGained) {
        playerStats.computeIfAbsent(playerId, k -> new EconomicStats()).update(coinsGained, tokensGained, expGained);
    }

    /**
     * Obtient le solde complet d'un joueur
     */
    public EconomicBalance getBalance(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return new EconomicBalance(
                playerData.getCoins(),
                playerData.getTokens(),
                playerData.getExperience()
        );
    }

    /**
     * Calcule le classement économique des joueurs
     */
    public List<EconomicRanking> getTopPlayers(EconomicType type, int limit) {
        List<EconomicRanking> rankings = new ArrayList<>();

        for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {
            long value = switch (type) {
                case COINS -> playerData.getCoins();
                case TOKENS -> playerData.getTokens();
                case EXPERIENCE -> playerData.getExperience();
                case TOTAL_BLOCKS -> playerData.getTotalBlocksMined();
            };

            rankings.add(new EconomicRanking(playerData.getPlayerName(), value));
        }

        return rankings.stream()
                .sorted((a, b) -> Long.compare(b.value(), a.value()))
                .limit(limit)
                .toList();
    }

    /**
     * CORRIGÉ: Statistiques économiques globales avec distinction gains pioche
     */
    public Map<String, Object> getGlobalEconomicStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalCoins = 0;
        long totalTokens = 0;
        long totalExperience = 0;
        long totalCoinsViaPickaxe = 0;
        long totalTokensViaPickaxe = 0;
        long totalExperienceViaPickaxe = 0;
        long totalBlocksMined = 0;
        long totalGreedTriggers = 0;
        long totalKeysObtained = 0;
        int activePlayers = 0;

        for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {
            totalCoins += playerData.getCoins();
            totalTokens += playerData.getTokens();
            totalExperience += playerData.getExperience();
            totalCoinsViaPickaxe += playerData.getCoinsViaPickaxe();
            totalTokensViaPickaxe += playerData.getTokensViaPickaxe();
            totalExperienceViaPickaxe += playerData.getExperienceViaPickaxe();
            totalBlocksMined += playerData.getTotalBlocksMined();
            totalGreedTriggers += playerData.getTotalGreedTriggers();
            totalKeysObtained += playerData.getTotalKeysObtained();
            activePlayers++;
        }

        // Statistiques TOTALES
        stats.put("total-coins", totalCoins);
        stats.put("total-tokens", totalTokens);
        stats.put("total-experience", totalExperience);

        // NOUVEAU: Statistiques VIA PIOCHE
        stats.put("total-coins-via-pickaxe", totalCoinsViaPickaxe);
        stats.put("total-tokens-via-pickaxe", totalTokensViaPickaxe);
        stats.put("total-experience-via-pickaxe", totalExperienceViaPickaxe);

        // Autres statistiques
        stats.put("total-blocks-mined", totalBlocksMined);
        stats.put("total-greed-triggers", totalGreedTriggers);
        stats.put("total-keys-obtained", totalKeysObtained);
        stats.put("active-players", activePlayers);

        if (activePlayers > 0) {
            stats.put("average-coins", totalCoins / activePlayers);
            stats.put("average-tokens", totalTokens / activePlayers);
            stats.put("average-experience", totalExperience / activePlayers);
            stats.put("average-greeds", totalGreedTriggers / activePlayers);
        }

        return stats;
    }

    /**
     * Réinitialise les statistiques de la dernière minute pour tous les joueurs
     */
    public void resetAllLastMinuteStats() {
        for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {
            playerData.resetLastMinuteStats();
        }
    }

    // Classes utilitaires

    /**
     * Types de valeurs économiques pour les classements
     */
    public enum EconomicType {
        COINS,
        TOKENS,
        EXPERIENCE,
        TOTAL_BLOCKS
    }

    /**
         * Solde économique d'un joueur
         */
        public record EconomicBalance(long coins, long tokens, long experience) {

        @Override
            public String toString() {
                return String.format("Balance{coins=%s, tokens=%s, exp=%s}",
                        NumberFormatter.format(coins),
                        NumberFormatter.format(tokens),
                        NumberFormatter.format(experience));
            }
        }

    /**
     * Statistiques économiques d'un joueur
     */
    private static class EconomicStats {
        private long totalCoinsEarned;
        private long totalTokensEarned;
        private long totalExperienceEarned;
        private long lastUpdate;

        public EconomicStats() {
            this.lastUpdate = System.currentTimeMillis();
        }

        public void update(long coinsGained, long tokensGained, long expGained) {
            this.totalCoinsEarned += coinsGained;
            this.totalTokensEarned += tokensGained;
            this.totalExperienceEarned += expGained;
            this.lastUpdate = System.currentTimeMillis();
        }

        // Getters
        public long getTotalCoinsEarned() {
            return totalCoinsEarned;
        }

        public long getTotalTokensEarned() {
            return totalTokensEarned;
        }

        public long getTotalExperienceEarned() {
            return totalExperienceEarned;
        }

        public long getLastUpdate() {
            return lastUpdate;
        }
    }

    /**
         * Classement économique
         */
        public record EconomicRanking(String playerName, long value) {
    }
}