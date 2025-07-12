package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire économique du plugin
 * CORRIGÉ : Retrait formatActionBar et generateMinuteSummary (maintenant dans ScoreboardManager et ActionBarTask)
 */
public class EconomyManager {

    private final PrisonTycoon plugin;

    // Cache pour les statistiques économiques
    private final Map<UUID, EconomicStats> playerStats;

    // Limites de sécurité pour éviter les overflows
    private static final long MAX_CURRENCY_VALUE = Long.MAX_VALUE / 2;
    private static final long MIN_CURRENCY_VALUE = 0;

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
     * Ajoute de l'expérience avec mise à jour vanilla en temps réel (TOTAL - toutes sources)
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

        // Met à jour immédiatement l'expérience vanilla
        updateVanillaExpFromCustom(player, currentExp + amount);

        updateStats(player.getUniqueId(), 0, 0, amount);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        return true;
    }

    /**
     * Met à jour l'expérience vanilla basée sur l'expérience custom
     */
    public void updateVanillaExpFromCustom(Player player, long customExp) {
        // Formule améliorée : niveau = racine carrée de (exp custom / 1000)
        int vanillaLevel = (int) Math.sqrt(customExp / 1000.0);

        // Calcule la progression vers le niveau suivant
        long expForCurrentLevel = vanillaLevel * vanillaLevel * 1000L;
        long expForNextLevel = (vanillaLevel + 1) * (vanillaLevel + 1) * 1000L;
        long expInCurrentLevel = customExp - expForCurrentLevel;
        long expNeededForNext = expForNextLevel - expForCurrentLevel;

        float progress = expNeededForNext > 0 ? (float) expInCurrentLevel / expNeededForNext : 0f;
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        // Applique au joueur en évitant les changements inutiles
        if (player.getLevel() != vanillaLevel || Math.abs(player.getExp() - progress) > 0.01f) {
            player.setLevel(vanillaLevel);
            player.setExp(progress);

            plugin.getPluginLogger().debug("Vanilla exp update: " + vanillaLevel + " (+" +
                    String.format("%.1f%%", progress * 100) + ") from custom " + customExp);
        }
    }

    /**
     * Initialise l'expérience vanilla d'un joueur à la connexion
     */
    public void initializeVanillaExp(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        updateVanillaExpFromCustom(player, playerData.getExperience());
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

    // SUPPRIMÉ: formatActionBar() - maintenant dans ScoreboardManager pour notifications Greed
    // SUPPRIMÉ: generateMinuteSummary() - maintenant dans ActionBarTask.generateCompleteSummary()

    /**
     * Effectue une transaction entre joueurs (future fonctionnalité)
     */
    public boolean transferTokens(Player from, Player to, long amount) {
        if (amount <= 0) return false;

        PlayerData fromData = plugin.getPlayerDataManager().getPlayerData(from.getUniqueId());
        PlayerData toData = plugin.getPlayerDataManager().getPlayerData(to.getUniqueId());

        // Vérifie les fonds
        if (fromData.getTokens() < amount) {
            from.sendMessage("§cTokens insuffisants pour le transfert!");
            return false;
        }

        // Vérifie les limites du destinataire
        if (toData.getTokens() + amount > MAX_CURRENCY_VALUE) {
            from.sendMessage("§cLe destinataire a atteint la limite de tokens!");
            return false;
        }

        // Effectue la transaction (ces tokens ne comptent pas comme "via pioche")
        if (fromData.removeTokens(amount)) {
            toData.addTokens(amount);

            from.sendMessage("§a✅ " + NumberFormatter.format(amount) + " tokens transférés à " + to.getName());
            to.sendMessage("§a📥 " + NumberFormatter.format(amount) + " tokens reçus de " + from.getName());

            // Marque les deux joueurs comme modifiés
            plugin.getPlayerDataManager().markDirty(from.getUniqueId());
            plugin.getPlayerDataManager().markDirty(to.getUniqueId());

            return true;
        }

        return false;
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
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
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
     * Solde économique d'un joueur
     */
    public static class EconomicBalance {
        private final long coins;
        private final long tokens;
        private final long experience;

        public EconomicBalance(long coins, long tokens, long experience) {
            this.coins = coins;
            this.tokens = tokens;
            this.experience = experience;
        }

        public long getCoins() { return coins; }
        public long getTokens() { return tokens; }
        public long getExperience() { return experience; }

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
        public long getTotalCoinsEarned() { return totalCoinsEarned; }
        public long getTotalTokensEarned() { return totalTokensEarned; }
        public long getTotalExperienceEarned() { return totalExperienceEarned; }
        public long getLastUpdate() { return lastUpdate; }
    }

    /**
     * Classement économique
     */
    public static class EconomicRanking {
        private final String playerName;
        private final long value;

        public EconomicRanking(String playerName, long value) {
            this.playerName = playerName;
            this.value = value;
        }

        public String getPlayerName() { return playerName; }
        public long getValue() { return value; }
    }

    /**
     * Types de valeurs économiques pour les classements
     */
    public enum EconomicType {
        COINS,
        TOKENS,
        EXPERIENCE,
        TOTAL_BLOCKS
    }
}