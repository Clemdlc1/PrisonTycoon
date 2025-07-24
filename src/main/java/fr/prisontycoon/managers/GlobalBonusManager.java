package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.cristaux.CristalType;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire unifié des bonus (cristaux, talents, boosts)
 * Version refactorisée avec types de bonus simplifiés
 */
public class GlobalBonusManager {

    private final PrisonTycoon plugin;

    public GlobalBonusManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * MÉTHODE PRINCIPALE : Calcule le multiplicateur total pour une catégorie de bonus
     */
    public double getTotalBonusMultiplier(Player player, BonusCategory category) {
        BonusSourceDetails details = getBonusSourcesDetails(player, category);
        return details.getTotalMultiplier();
    }

    /**
     * MÉTHODE UTILITAIRE : Obtient les détails complets des sources d'un bonus
     * Cette méthode simplifie l'affichage dans BoostGUI
     */
    public BonusSourceDetails getBonusSourcesDetails(Player player, BonusCategory category) {
        BonusSourceDetails details = new BonusSourceDetails();

        // 1. Bonus des cristaux
        double cristalBonus = getCristalBonus(player, category);
        details.setCristalBonus(cristalBonus);
        if (cristalBonus > 0) {
            details.addDetailedSource("Cristaux " + getCristalTypeName(category), cristalBonus);
        }

        // 2. Bonus des talents de métiers
        double professionBonus = getProfessionTalentBonus(player, category);
        details.setProfessionBonus(professionBonus);
        if (professionBonus > 0) {
            String activeProfession = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getActiveProfession();
            details.addDetailedSource("Talent " + (activeProfession != null ? activeProfession : "métier"), professionBonus);
        }

        // 3. Bonus des talents de prestige
        double prestigeBonus = getPrestigeTalentBonus(player, category);
        details.setPrestigeBonus(prestigeBonus);
        if (prestigeBonus > 0) {
            details.addDetailedSource("Talent Prestige", prestigeBonus);
        }

        // 4. Bonus des boosts temporaires
        double boostBonus = getTemporaryBoostBonus(player, category);
        details.setTemporaryBoostBonus(boostBonus);
        if (boostBonus > 0) {
            details.addDetailedSource("Boosts Temporaires", boostBonus);
        }

        return details;
    }

    /**
     * Obtient tous les bonus actifs d'un joueur
     */
    public Map<BonusCategory, BonusSourceDetails> getAllActiveBonuses(Player player) {
        Map<BonusCategory, BonusSourceDetails> bonuses = new HashMap<>();

        for (BonusCategory category : BonusCategory.values()) {
            BonusSourceDetails details = getBonusSourcesDetails(player, category);
            if (details.getTotalBonus() > 0) {
                bonuses.put(category, details);
            }
        }

        return bonuses;
    }

    /**
     * Applique le bonus Token sur une valeur de base
     */
    public long applyTokenBonus(Player player, long baseTokens) {
        if (baseTokens <= 0) return baseTokens;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.TOKEN_BONUS);
        return Math.round(baseTokens * multiplier);
    }

    /**
     * Applique le bonus Money sur une valeur de base
     */
    public long applyMoneyBonus(Player player, long baseMoney) {
        if (baseMoney <= 0) return baseMoney;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.MONEY_BONUS);
        return Math.round(baseMoney * multiplier);
    }

    // ========================================
    // MÉTHODES D'APPLICATION DES BONUS
    // ========================================

    /**
     * Applique le bonus Experience sur une valeur de base
     */
    public long applyExperienceBonus(Player player, long baseExp) {
        if (baseExp <= 0) return baseExp;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.EXPERIENCE_BONUS);
        return Math.round(baseExp * multiplier);
    }

    /**
     * Applique le bonus Sell sur un prix de vente
     */
    public double applySellBonus(Player player, double basePrice) {
        if (basePrice <= 0) return basePrice;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.SELL_BONUS);
        return basePrice * multiplier;
    }

    /**
     * Applique le bonus Fortune sur une valeur
     */
    public int applyFortuneBonus(Player player, int baseFortune) {
        if (baseFortune <= 0) return baseFortune;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.FORTUNE_BONUS);
        return Math.round(baseFortune * (float) multiplier);
    }

    /**
     * Applique le bonus Job XP sur les gains d'XP métier
     */
    public long applyJobXPBonus(Player player, long baseJobXP) {
        if (baseJobXP <= 0) return baseJobXP;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.JOB_XP_BONUS);
        return Math.round(baseJobXP * multiplier);
    }

    /**
     * Applique le multiplicateur de beacons
     */
    public double applyBeaconMultiplier(Player player, double baseEfficiency) {
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.BEACON_MULTIPLIER);
        return baseEfficiency * multiplier;
    }

    /**
     * Calcule le bonus des cristaux pour une catégorie
     */
    private double getCristalBonus(Player player, BonusCategory category) {
        if (plugin.getCristalManager() == null) return 0.0;

        CristalType cristalType = getCristalTypeForCategory(category);
        if (cristalType == null) return 0.0;

        return plugin.getCristalManager().getTotalCristalBonus(player, cristalType);
    }

    /**
     * Calcule le bonus des talents de métiers pour une catégorie
     */
    private double getProfessionTalentBonus(Player player, BonusCategory category) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null) return 0.0;

        double bonus = 0.0;

        switch (activeProfession) {
            case "mineur" -> {
                switch (category) {
                    case TOKEN_BONUS -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "token_greed");
                        if (talentLevel > 0) {
                            var profession = plugin.getProfessionManager().getProfession("mineur");
                            var talent = profession.getTalent("token_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    case EXPERIENCE_BONUS -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "exp_greed");
                        if (talentLevel > 0) {
                            var profession = plugin.getProfessionManager().getProfession("mineur");
                            var talent = profession.getTalent("exp_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    case MONEY_BONUS -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "money_greed");
                        if (talentLevel > 0) {
                            var profession = plugin.getProfessionManager().getProfession("mineur");
                            var talent = profession.getTalent("money_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                }
            }
            case "commercant" -> {
                if (category == BonusCategory.SELL_BONUS) {
                    int talentLevel = playerData.getTalentLevel("commercant", "sell_boost");
                    if (talentLevel > 0) {
                        var profession = plugin.getProfessionManager().getProfession("commercant");
                        var talent = profession.getTalent("sell_boost");
                        bonus += talent.getValueAtLevel(talentLevel);
                    }
                }
            }
            case "guerrier" -> {
                if (category == BonusCategory.BEACON_MULTIPLIER) {
                    int talentLevel = playerData.getTalentLevel("guerrier", "beacon_multiplier");
                    if (talentLevel > 0) {
                        var profession = plugin.getProfessionManager().getProfession("guerrier");
                        var talent = profession.getTalent("beacon_multiplier");
                        int multiplierValue = talent.getValueAtLevel(talentLevel);
                        bonus += (multiplierValue - 1) * 100; // x2 devient +100%
                    }
                }
            }
        }

        return bonus;
    }

    // ========================================
    // MÉTHODES PRIVÉES DE CALCUL DES SOURCES
    // ========================================

    /**
     * Calcule le bonus des talents de prestige pour une catégorie
     */
    private double getPrestigeTalentBonus(Player player, BonusCategory category) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Utilise getPrestigeTalents() pour obtenir tous les talents actifs
        Map<PrestigeTalent, Integer> talents = playerData.getPrestigeTalents();

        double bonus = 0.0;

        // Calcule le bonus selon la catégorie demandée
        switch (category) {
            case MONEY_BONUS -> {
                int level = talents.getOrDefault(PrestigeTalent.MONEY_GREED_BONUS, 0);
                bonus = level * 3.0; // +3% par niveau
            }
            case TOKEN_BONUS -> {
                int level = talents.getOrDefault(PrestigeTalent.TOKEN_GREED_BONUS, 0);
                bonus = level * 3.0; // +3% par niveau
            }
            case SELL_BONUS -> {
                int level = talents.getOrDefault(PrestigeTalent.SELL_PRICE_BONUS, 0);
                bonus = level * 3.0; // +3% par niveau
            }
            case OUTPOST_BONUS -> {
                int level = talents.getOrDefault(PrestigeTalent.OUTPOST_BONUS, 0);
                bonus = level * 3.0; // +3% par niveau
            }
            case TAX_REDUCTION -> {
                int level = talents.getOrDefault(PrestigeTalent.TAX_REDUCTION, 0);
                bonus = level * 1.0; // -1% par niveau (réduction)
            }
            case PVP_MERCHANT_REDUCTION -> {
                int level = talents.getOrDefault(PrestigeTalent.PVP_MERCHANT_REDUCTION, 0);
                bonus = level * 1.0; // -1% par niveau (réduction)
            }
        }

        return bonus;
    }

    /**
     * Calcule le bonus des boosts temporaires pour une catégorie
     */
    private double getTemporaryBoostBonus(Player player, BonusCategory category) {
        if (plugin.getBoostManager() == null) return 0.0;

        BoostType boostType = getBoostTypeForCategory(category);
        if (boostType == null) return 0.0;

        return plugin.getBoostManager().getTotalBoostBonus(player, boostType);
    }

    /**
     * Mappe une catégorie de bonus vers un type de cristal
     */
    private CristalType getCristalTypeForCategory(BonusCategory category) {
        return switch (category) {
            case TOKEN_BONUS -> CristalType.TOKEN_BOOST;
            case MONEY_BONUS -> CristalType.MONEY_BOOST;
            case EXPERIENCE_BONUS -> CristalType.XP_BOOST;
            case SELL_BONUS -> CristalType.SELL_BOOST;
            case FORTUNE_BONUS -> CristalType.MINERAL_GREED;
            default -> null;
        };
    }

    /**
     * Mappe une catégorie de bonus vers un type de boost
     */
    private BoostType getBoostTypeForCategory(BonusCategory category) {
        return switch (category) {
            case TOKEN_BONUS -> BoostType.TOKEN_GREED;
            case MONEY_BONUS -> BoostType.MONEY_GREED;
            case EXPERIENCE_BONUS -> BoostType.EXP_GREED;
            case SELL_BONUS -> BoostType.SELL_BOOST;
            case FORTUNE_BONUS -> BoostType.MINERAL_GREED;
            case JOB_XP_BONUS -> BoostType.JOB_XP_BOOST;
            default -> null;
        };
    }

    // ========================================
    // MÉTHODES UTILITAIRES DE MAPPING
    // ========================================

    /**
     * Obtient le nom du type de cristal pour l'affichage
     */
    private String getCristalTypeName(BonusCategory category) {
        CristalType cristalType = getCristalTypeForCategory(category);
        return cristalType != null ? cristalType.name() : "Unknown";
    }

    /**
     * Debug: Affiche tous les bonus actifs pour un joueur
     */
    public void debugBonuses(Player player) {
        plugin.getPluginLogger().info("=== Bonus Debug pour " + player.getName() + " ===");

        Map<BonusCategory, BonusSourceDetails> activeBonuses = getAllActiveBonuses(player);

        if (activeBonuses.isEmpty()) {
            plugin.getPluginLogger().info("Aucun bonus actif");
        } else {
            for (Map.Entry<BonusCategory, BonusSourceDetails> entry : activeBonuses.entrySet()) {
                BonusCategory category = entry.getKey();
                BonusSourceDetails details = entry.getValue();

                plugin.getPluginLogger().info(category.getDisplayName() + ": ×" +
                        String.format("%.3f", details.getTotalMultiplier()) +
                        " (+" + String.format("%.1f", details.getTotalBonus()) + "%)");

                // Détails des sources
                if (details.getCristalBonus() > 0) {
                    plugin.getPluginLogger().info("  - Cristaux: +" + String.format("%.1f", details.getCristalBonus()) + "%");
                }
                if (details.getProfessionBonus() > 0) {
                    plugin.getPluginLogger().info("  - Métiers: +" + String.format("%.1f", details.getProfessionBonus()) + "%");
                }
                if (details.getPrestigeBonus() > 0) {
                    plugin.getPluginLogger().info("  - Prestige: +" + String.format("%.1f", details.getPrestigeBonus()) + "%");
                }
                if (details.getTemporaryBoostBonus() > 0) {
                    plugin.getPluginLogger().info("  - Boosts: +" + String.format("%.1f", details.getTemporaryBoostBonus()) + "%");
                }
            }
        }
    }

    /**
     * NOUVEAU: Méthode utilitaire unifiée pour obtenir un bonus de prestige spécifique
     * Remplace les anciennes méthodes getPrestigeXxxBonus() de PlayerData
     */
    public double getPrestigeBonus(Player player, BonusCategory category) {
        return getPrestigeTalentBonus(player, category);
    }

    // ========================================
    // MÉTHODES DE DEBUG
    // ========================================

    /**
     * MIGRÉ: Calcule le bonus Money Greed total du prestige
     * Remplace PlayerData.getPrestigeMoneyGreedBonus()
     */
    public double getPrestigeMoneyGreedBonus(Player player) {
        return getPrestigeBonus(player, BonusCategory.MONEY_BONUS) / 100.0; // Retourne en multiplicateur (0.03 pour 3%)
    }

    // ========================================
    // MÉTHODES UTILITAIRES MIGRÉES DE PLAYERDATA
    // ========================================

    /**
     * MIGRÉ: Calcule le bonus Token Greed total du prestige
     * Remplace PlayerData.getPrestigeTokenGreedBonus()
     */
    public double getPrestigeTokenGreedBonus(Player player) {
        return getPrestigeBonus(player, BonusCategory.TOKEN_BONUS) / 100.0; // Retourne en multiplicateur (0.03 pour 3%)
    }

    /**
     * MIGRÉ: Calcule la réduction de taxe du prestige
     * Remplace PlayerData.getPrestigeTaxReduction()
     */
    public double getPrestigeTaxReduction(Player player) {
        double reduction = getPrestigeBonus(player, BonusCategory.TAX_REDUCTION) / 100.0; // Retourne en multiplicateur (0.01 pour 1%)
        return Math.min(reduction, 0.99); // Maximum 99% de réduction
    }

    /**
     * MIGRÉ: Calcule le bonus de prix de vente du prestige
     * Remplace PlayerData.getPrestigeSellBonus()
     */
    public double getPrestigeSellBonus(Player player) {
        return getPrestigeBonus(player, BonusCategory.SELL_BONUS) / 100.0; // Retourne en multiplicateur (0.03 pour 3%)
    }

    /**
     * NOUVEAU: Calcule le bonus d'avant-poste du prestige
     */
    public double getPrestigeOutpostBonus(Player player) {
        return getPrestigeBonus(player, BonusCategory.OUTPOST_BONUS) / 100.0;
    }

    /**
     * NOUVEAU: Calcule la réduction du marchand PvP du prestige
     */
    public double getPrestigePvpMerchantReduction(Player player) {
        return getPrestigeBonus(player, BonusCategory.PVP_MERCHANT_REDUCTION) / 100.0;
    }

    /**
     * NOUVEAU: Obtient tous les bonus de prestige d'un joueur
     * Méthode de commodité pour obtenir tous les bonus en une fois
     */
    public Map<String, Double> getAllPrestigeBonuses(Player player) {
        Map<String, Double> bonuses = new HashMap<>();

        bonuses.put("money_greed", getPrestigeMoneyGreedBonus(player));
        bonuses.put("token_greed", getPrestigeTokenGreedBonus(player));
        bonuses.put("tax_reduction", getPrestigeTaxReduction(player));
        bonuses.put("sell_bonus", getPrestigeSellBonus(player));
        bonuses.put("outpost_bonus", getPrestigeOutpostBonus(player));
        bonuses.put("pvp_merchant_reduction", getPrestigePvpMerchantReduction(player));

        return bonuses;
    }

    /**
     * Types de bonus unifiés - Un seul type par finalité
     */
    public enum BonusCategory {
        TOKEN_BONUS("Token Bonus", "💎", "§b", "Augmente les gains de tokens"),
        MONEY_BONUS("Money Bonus", "💰", "§6", "Augmente les gains de coins"),
        EXPERIENCE_BONUS("Experience Bonus", "⭐", "§a", "Augmente les gains d'expérience"),
        SELL_BONUS("Sell Bonus", "💸", "§e", "Augmente les prix de vente"),
        FORTUNE_BONUS("Fortune Bonus", "⛏️", "§9", "Augmente l'effet Fortune"),
        JOB_XP_BONUS("Job XP Bonus", "🔨", "§d", "Augmente les gains d'XP métier"),

        // Bonus spéciaux (sans équivalent boost)
        BEACON_MULTIPLIER("Beacon Multiplier", "🔥", "§c", "Multiplicateur de beacons"),
        TAX_REDUCTION("Tax Reduction", "💳", "§5", "Réduction des taxes"),
        OUTPOST_BONUS("Outpost Bonus", "🏰", "§3", "Bonus des avant-postes"),
        PVP_MERCHANT_REDUCTION("PvP Merchant Reduction", "⚔️", "§4", "Réduction prix marchand PvP");

        private final String displayName;
        private final String emoji;
        private final String color;
        private final String description;

        BonusCategory(String displayName, String emoji, String color, String description) {
            this.displayName = displayName;
            this.emoji = emoji;
            this.color = color;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmoji() {
            return emoji;
        }

        public String getColor() {
            return color;
        }

        public String getDescription() {
            return description;
        }

        public String getFormattedName() {
            return color + emoji + " " + displayName;
        }
    }

    /**
     * Détails des sources d'un bonus
     */
    public static class BonusSourceDetails {
        private double cristalBonus = 0.0;
        private double professionBonus = 0.0;
        private double prestigeBonus = 0.0;
        private double temporaryBoostBonus = 0.0;
        private final Map<String, Double> detailedSources = new HashMap<>();

        public double getTotalBonus() {
            return cristalBonus + professionBonus + prestigeBonus + temporaryBoostBonus;
        }

        public double getTotalMultiplier() {
            return 1.0 + (getTotalBonus() / 100.0);
        }

        // Getters
        public double getCristalBonus() {
            return cristalBonus;
        }

        // Setters
        public void setCristalBonus(double cristalBonus) {
            this.cristalBonus = cristalBonus;
        }

        public double getProfessionBonus() {
            return professionBonus;
        }

        public void setProfessionBonus(double professionBonus) {
            this.professionBonus = professionBonus;
        }

        public double getPrestigeBonus() {
            return prestigeBonus;
        }

        public void setPrestigeBonus(double prestigeBonus) {
            this.prestigeBonus = prestigeBonus;
        }

        public double getTemporaryBoostBonus() {
            return temporaryBoostBonus;
        }

        public void setTemporaryBoostBonus(double temporaryBoostBonus) {
            this.temporaryBoostBonus = temporaryBoostBonus;
        }

        public Map<String, Double> getDetailedSources() {
            return detailedSources;
        }

        public void addDetailedSource(String source, double bonus) {
            detailedSources.put(source, bonus);
        }
    }
}