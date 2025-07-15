package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.cristaux.CristalType;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire global des bonus pour tous les enchantements
 * Centralise le calcul des bonus venant de différentes sources (cristaux, futurs bonus, etc.)
 */
public class GlobalBonusManager {

    private final PrisonTycoon plugin;

    // Types de bonus gérés
    public enum BonusType {
        TOKEN_GREED("TokenGreed", CristalType.TOKEN_BOOST),
        MONEY_GREED("MoneyGreed", CristalType.MONEY_BOOST),
        EXP_GREED("ExpGreed", CristalType.XP_BOOST),
        SELL_BONUS("SellBonus", CristalType.SELL_BOOST),
        MINERAL_GREED("MineralGreed", CristalType.MINERAL_GREED);

        private final String displayName;
        private final CristalType associatedCristal;

        BonusType(String displayName, CristalType associatedCristal) {
            this.displayName = displayName;
            this.associatedCristal = associatedCristal;
        }

        public String getDisplayName() { return displayName; }
        public CristalType getAssociatedCristal() { return associatedCristal; }
    }

    public GlobalBonusManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Calcule le bonus total pour un type donné
     * @param player Le joueur
     * @param bonusType Le type de bonus
     * @return Le multiplicateur final (1.0 = pas de bonus, 1.5 = +50%)
     */
    public double getTotalBonusMultiplier(Player player, BonusType bonusType) {
        double totalBonus = 0.0;

        // 1. Bonus des cristaux (système existant)
        if (bonusType.getAssociatedCristal() != null) {
            totalBonus += plugin.getCristalManager().getTotalCristalBonus(player, bonusType.getAssociatedCristal());
        }

        // 2. Futurs bonus (à implémenter plus tard)
        totalBonus += getFutureBonuses(player, bonusType);

        // Retourne le multiplicateur (pourcentage -> multiplicateur)
        return 1.0 + (totalBonus / 100.0);
    }

    /**
     * Applique le bonus TokenGreed sur une valeur de base
     */
    public long applyTokenGreedBonus(Player player, long baseTokens) {
        if (baseTokens <= 0) return baseTokens;

        double multiplier = getTotalBonusMultiplier(player, BonusType.TOKEN_GREED);
        return Math.round(baseTokens * multiplier);
    }

    /**
     * Applique le bonus MoneyGreed sur une valeur de base
     */
    public long applyMoneyGreedBonus(Player player, long baseMoney) {
        if (baseMoney <= 0) return baseMoney;

        double multiplier = getTotalBonusMultiplier(player, BonusType.MONEY_GREED);
        return Math.round(baseMoney * multiplier);
    }

    /**
     * Applique le bonus ExpGreed sur une valeur de base
     */
    public long applyExpGreedBonus(Player player, long baseExp) {
        if (baseExp <= 0) return baseExp;

        double multiplier = getTotalBonusMultiplier(player, BonusType.EXP_GREED);
        return Math.round(baseExp * multiplier);
    }

    /**
     * Applique le bonus SellBonus sur un prix de vente
     */
    public double applySellBonus(Player player, double basePrice) {
        if (basePrice <= 0) return basePrice;

        double multiplier = getTotalBonusMultiplier(player, BonusType.SELL_BONUS);
        return basePrice * multiplier;
    }

    /**
     * Applique le bonus MineralGreed (Fortune) sur une valeur
     */
    public int applyMineralGreedBonus(Player player, int baseFortune) {
        if (baseFortune <= 0) return baseFortune;

        double multiplier = getTotalBonusMultiplier(player, BonusType.MINERAL_GREED);
        return Math.round(baseFortune * (float)multiplier);
    }

    /**
     * Obtient les détails de tous les bonus actifs pour un joueur
     */
    public Map<BonusType, Double> getActiveBonuses(Player player) {
        Map<BonusType, Double> bonuses = new HashMap<>();

        for (BonusType bonusType : BonusType.values()) {
            double multiplier = getTotalBonusMultiplier(player, bonusType);
            if (multiplier > 1.0) {
                bonuses.put(bonusType, multiplier);
            }
        }

        return bonuses;
    }

    /**
     * Obtient le pourcentage de bonus pour un type donné
     */
    public double getBonusPercentage(Player player, BonusType bonusType) {
        double multiplier = getTotalBonusMultiplier(player, bonusType);
        return (multiplier - 1.0) * 100.0;
    }

    /**
     * Méthode pour gérer les futurs types de bonus
     * (pets, équipements, événements temporaires, etc.)
     */
    private double getFutureBonuses(Player player, BonusType bonusType) {
        double totalFutureBonus = 0.0;

        return totalFutureBonus;
    }

    /**
     * Méthode utilitaire pour les enchantements spéciaux qui ont leurs propres calculs
     */
    public int getAbondanceDuration(Player player, int baseDuration) {
        // Délègue au système de cristaux existant pour maintenir la compatibilité
        return plugin.getCristalBonusHelper().getAbondanceDuration(player, baseDuration);
    }

    /**
     * Méthode utilitaire pour l'efficacité de combustion
     */
    public double getCombustionEfficiency(Player player, double baseEfficiency) {
        // Délègue au système de cristaux existant pour maintenir la compatibilité
        return plugin.getCristalBonusHelper().applyCombustionEfficiency(player, baseEfficiency);
    }

    /**
     * Debug: Affiche tous les bonus actifs pour un joueur
     */
    public void debugBonuses(Player player) {
        plugin.getPluginLogger().info("=== Bonus Debug pour " + player.getName() + " ===");

        Map<BonusType, Double> activeBonuses = getActiveBonuses(player);

        if (activeBonuses.isEmpty()) {
            plugin.getPluginLogger().info("Aucun bonus actif");
        } else {
            for (Map.Entry<BonusType, Double> entry : activeBonuses.entrySet()) {
                double percentage = (entry.getValue() - 1.0) * 100.0;
                plugin.getPluginLogger().info(entry.getKey().getDisplayName() + ": +" +
                        String.format("%.1f", percentage) + "% (x" +
                        String.format("%.3f", entry.getValue()) + ")");
            }
        }
    }
}