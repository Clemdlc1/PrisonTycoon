package fr.prisontycoon.autominers;

import org.bukkit.Material;

/**
 * Types d'automineurs avec leurs caractéristiques
 */
public enum AutominerType {

    PIERRE("Pierre", Material.COBBLESTONE, 60, 0.5,
            10, 5, 0, 0, 0, 0, 0, 0),

    FER("Fer", Material.IRON_INGOT, 30, 1.0,
            25, 10, 10, 0, 0, 0, 0, 0),

    OR("Or", Material.GOLD_INGOT, 15, 2.0,
            100, 50, 25, 25, 25, 0, 0, 0),

    DIAMANT("Diamant", Material.DIAMOND, 5, 4.0,
            500, 250, 100, 100, 100, 2, 10, 0),

    EMERAUDE("Émeraude", Material.EMERALD, 3, 7.5,
            5000, 2000, 500, 500, 500, 1, 50, 0),

    BEACON("Beacon", Material.BEACON, 1, 12.0,
            Integer.MAX_VALUE, Integer.MAX_VALUE, 10000, 10000, 10000, 3, 100, 1);

    private final String displayName;
    private final Material displayMaterial;
    private final int fuelConsumptionMinutes; // Consommation en minutes par tête
    private final double rarityCoefficient; // Pour calcul coût enchantements

    // Limites maximales des enchantements
    private final int maxEfficiency;
    private final int maxFortune;
    private final int maxTokenGreed;
    private final int maxExpGreed;
    private final int maxMoneyGreed;
    private final int maxKeyGreed;
    private final int maxFuelEfficiency;
    private final int maxBeaconFinder;

    AutominerType(String displayName, Material displayMaterial, int fuelConsumptionMinutes,
                  double rarityCoefficient, int maxEfficiency, int maxFortune,
                  int maxTokenGreed, int maxExpGreed, int maxMoneyGreed,
                  int maxKeyGreed, int maxFuelEfficiency, int maxBeaconFinder) {
        this.displayName = displayName;
        this.displayMaterial = displayMaterial;
        this.fuelConsumptionMinutes = fuelConsumptionMinutes;
        this.rarityCoefficient = rarityCoefficient;
        this.maxEfficiency = maxEfficiency;
        this.maxFortune = maxFortune;
        this.maxTokenGreed = maxTokenGreed;
        this.maxExpGreed = maxExpGreed;
        this.maxMoneyGreed = maxMoneyGreed;
        this.maxKeyGreed = maxKeyGreed;
        this.maxFuelEfficiency = maxFuelEfficiency;
        this.maxBeaconFinder = maxBeaconFinder;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getDisplayMaterial() {
        return displayMaterial;
    }

    public int getFuelConsumptionMinutes() {
        return fuelConsumptionMinutes;
    }

    public double getRarityCoefficient() {
        return rarityCoefficient;
    }

    public int getMaxEfficiency() {
        return maxEfficiency;
    }

    public int getMaxFortune() {
        return maxFortune;
    }

    public int getMaxTokenGreed() {
        return maxTokenGreed;
    }

    public int getMaxExpGreed() {
        return maxExpGreed;
    }

    public int getMaxMoneyGreed() {
        return maxMoneyGreed;
    }

    public int getMaxKeyGreed() {
        return maxKeyGreed;
    }

    public int getMaxFuelEfficiency() {
        return maxFuelEfficiency;
    }

    public int getMaxBeaconFinder() {
        return maxBeaconFinder;
    }

    /**
     * Obtient le type suivant pour la condensation (9 → 1 niveau supérieur)
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
     * Vérifie si ce type supporte un enchantement donné
     */
    public boolean supportsEnchantment(String enchantmentName) {
        return switch (enchantmentName.toLowerCase()) {
            case "efficiency" -> maxEfficiency > 0;
            case "fortune" -> maxFortune > 0;
            case "tokengreed" -> maxTokenGreed > 0;
            case "expgreed" -> maxExpGreed > 0;
            case "moneygreed" -> maxMoneyGreed > 0;
            case "keygreed" -> maxKeyGreed > 0;
            case "fuelefficiency" -> maxFuelEfficiency > 0;
            case "beaconfinder" -> this == BEACON; // Seulement pour Beacon
            default -> false;
        };
    }

    /**
     * Obtient la limite maximale pour un enchantement
     */
    public int getMaxEnchantmentLevel(String enchantmentName) {
        return switch (enchantmentName.toLowerCase()) {
            case "efficiency" -> maxEfficiency;
            case "fortune" -> maxFortune;
            case "tokengreed" -> maxTokenGreed;
            case "expgreed" -> maxExpGreed;
            case "moneygreed" -> maxMoneyGreed;
            case "keygreed" -> maxKeyGreed;
            case "fuelefficiency" -> maxFuelEfficiency;
            case "beaconfinder" -> maxBeaconFinder;
            default -> 0;
        };
    }

    /**
     * Obtient la rareté sous forme de couleur
     */
    public String getRarityColor() {
        return switch (this) {
            case PIERRE -> "§7"; // Gris - Commun
            case FER -> "§f"; // Blanc - Peu Commun
            case OR -> "§e"; // Jaune - Rare
            case DIAMANT -> "§b"; // Bleu clair - Assez Rare
            case EMERAUDE -> "§a"; // Vert - Très Rare
            case BEACON -> "§6"; // Orange/Gold - Hyper Rare
        };
    }

    /**
     * Obtient le nom avec couleur de rareté
     */
    public String getColoredName() {
        return getRarityColor() + displayName;
    }
}