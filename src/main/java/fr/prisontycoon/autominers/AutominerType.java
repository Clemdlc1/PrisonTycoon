package fr.prisontycoon.autominers;

import java.util.HashMap;
import java.util.Map;

/**
 * Types d'automineurs avec leurs caractéristiques
 */
public enum AutominerType {
    PIERRE("PIERRE", 60 * 60, 0.5, Map.of(
            "EFFICACITE", 10,
            "FORTUNE", 5
    )),
    FER("FER", 30 * 60, 1.0, Map.of(
            "EFFICACITE", 25,
            "FORTUNE", 10,
            "TOKENGREED", 10
    )),
    OR("OR", 15 * 60, 2.0, Map.of(
            "EFFICACITE", 100,
            "FORTUNE", 50,
            "TOKENGREED", 25,
            "EXPGREED", 25,
            "MONEYGREED", 25
    )),
    DIAMANT("DIAMANT", 5 * 60, 4.0, Map.of(
            "EFFICACITE", 500,
            "FORTUNE", 250,
            "TOKENGREED", 100,
            "EXPGREED", 100,
            "MONEYGREED", 100,
            "KEYGREED", 2,
            "FUELEFFICIENCY", 10
    )),
    EMERAUDE("EMERAUDE", 3 * 60, 7.5, Map.of(
            "EFFICACITE", 5000,
            "FORTUNE", 2000,
            "TOKENGREED", 500,
            "EXPGREED", 500,
            "MONEYGREED", 500,
            "KEYGREED", 1,
            "FUELEFFICIENCY", 50
    )),
    BEACON("BEACON", 1 * 60, 12.0, Map.of(
            "EFFICACITE", Integer.MAX_VALUE,
            "FORTUNE", Integer.MAX_VALUE,
            "TOKENGREED", 10000,
            "EXPGREED", 10000,
            "MONEYGREED", 10000,
            "KEYGREED", 3,
            "BEACONFINDER", 1,
            "FUELEFFICIENCY", 100
    ));

    private final String displayName;
    private final int baseFuelConsumption; // en secondes
    private final double rarityCoefficient;
    private final Map<String, Integer> enchantmentLimits;

    AutominerType(String displayName, int baseFuelConsumption, double rarityCoefficient, Map<String, Integer> enchantmentLimits) {
        this.displayName = displayName;
        this.baseFuelConsumption = baseFuelConsumption;
        this.rarityCoefficient = rarityCoefficient;
        this.enchantmentLimits = new HashMap<>(enchantmentLimits);
    }

    public static AutominerType fromString(String name) {
        for (AutominerType type : values()) {
            if (type.displayName.equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getBaseFuelConsumption() {
        return baseFuelConsumption;
    }

    public double getRarityCoefficient() {
        return rarityCoefficient;
    }

    public Map<String, Integer> getEnchantmentLimits() {
        return new HashMap<>(enchantmentLimits);
    }

    public int getMaxEnchantmentLevel(String enchantment) {
        return enchantmentLimits.getOrDefault(enchantment, 0);
    }

    public boolean canHaveEnchantment(String enchantment) {
        return enchantmentLimits.containsKey(enchantment) && enchantmentLimits.get(enchantment) > 0;
    }

    /**
     * Calcule le coût d'amélioration d'un enchantement avec formules exponentielles spécifiques
     */
    public long calculateUpgradeCost(String enchantment, int currentLevel, int baseTokenCost) {
        if (!canHaveEnchantment(enchantment) || currentLevel >= getMaxEnchantmentLevel(enchantment)) {
            return -1; // Impossible d'améliorer
        }

        int targetLevel = currentLevel + 1;

        // Formules exponentielles spécifiques par enchantement
        double cost = switch (enchantment) {
            case "EFFICACITE" -> // Formule modérée : base * (1.05^niveau) * coeff_rareté
                    baseTokenCost * Math.pow(1.05, targetLevel) * rarityCoefficient;
            case "FORTUNE" -> // Formule forte : base * (1.08^niveau) * coeff_rareté (plus cher car très puissant)
                    baseTokenCost * Math.pow(1.08, targetLevel) * rarityCoefficient;
            case "TOKENGREED", "EXPGREED", "MONEYGREED" -> // Formule progressive : base * (1.06^niveau) * coeff_rareté
                    baseTokenCost * Math.pow(1.06, targetLevel) * rarityCoefficient;
            case "KEYGREED" -> // Formule très forte : base * (1.12^niveau) * coeff_rareté (très rare et puissant)
                    baseTokenCost * Math.pow(2, targetLevel) * rarityCoefficient;
            case "FUELEFFICIENCY" -> // Formule modérée : base * (1.04^niveau) * coeff_rareté (utility)
                    baseTokenCost * Math.pow(1.1, targetLevel) * rarityCoefficient;
            case "BEACONFINDER" -> // Formule extrême : base * (1.15^niveau) * coeff_rareté (très rare, réservé beacon)
                    baseTokenCost * Math.pow(2, targetLevel) * rarityCoefficient;
            default -> // Formule par défaut
                    baseTokenCost * Math.pow(1.05, targetLevel) * rarityCoefficient;
        };

        return Math.round(cost);
    }

    /**
     * Calcule la consommation de carburant par seconde avec l'enchantement FuelEfficiency
     */
    public double calculateFuelConsumptionPerSecond(int fuelEfficiencyLevel) {
        double baseConsumptionPerSecond = 1.0 / baseFuelConsumption;
        double reduction = fuelEfficiencyLevel * 0.01; // 1% de réduction par niveau
        return baseConsumptionPerSecond * (1.0 - Math.min(reduction, 0.9)); // Max 90% de réduction
    }

    /**
     * Obtient le type supérieur pour la condensation
     */
    public AutominerType getNextTier() {
        return switch (this) {
            case PIERRE -> FER;
            case FER -> OR;
            case OR -> DIAMANT;
            case DIAMANT -> EMERAUDE;
            case EMERAUDE -> BEACON;
            case BEACON -> null; // Pas de niveau supérieur
        };
    }

    /**
     * Vérifie si ce type peut être condensé
     */
    public boolean canBeCondensed() {
        return this != BEACON;
    }
}