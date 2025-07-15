package fr.prisoncore.prisoncore.prisonTycoon.cristaux;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Types de cristaux disponibles avec leurs bonus
 */
public enum CristalType {

    SELL_BOOST("SellBoost", "Augmente le prix de vente", 3.5, 70),
    XP_BOOST("XPBoost", "Augmente l'effet XPGreed", 5.0, 100),
    MONEY_BOOST("MoneyBoost", "Augmente l'effet MoneyGreed", 5.0, 100),
    TOKEN_BOOST("TokenBoost", "Augmente l'effet TokenGreed", 5.0, 100),
    MINERAL_GREED("MineralGreed", "Augmente l'effet Fortune", 5.0, 100),
    ABONDANCE_CRISTAL("AbondanceCristal", "Prolonge l'enchantement Abondance", 3.0, 60),
    COMBUSTION_CRISTAL("CombustionCristal", "Améliore l'enchantement Combustion", 3.0, 60),
    ECHO_CRISTAL("EchoCristal", "Créé des échos pour Laser", 2.0, 40);

    private final String displayName;
    private final String description;
    private final double bonusPerLevel;
    private final double maxBonus;

    CristalType(String displayName, String description, double bonusPerLevel, double maxBonus) {
        this.displayName = displayName;
        this.description = description;
        this.bonusPerLevel = bonusPerLevel;
        this.maxBonus = maxBonus;
    }

    /**
     * Calcule le bonus pour un niveau donné
     */
    public double getBonus(int level) {
        if (level <= 0) return 0;
        if (level > 20) level = 20;

        double bonus = level * bonusPerLevel;
        return Math.min(bonus, maxBonus);
    }

    /**
     * Retourne la description du bonus pour un niveau
     */
    public String getBonusDescription(int level) {
        double bonus = getBonus(level);

        switch (this) {
            case SELL_BOOST:
                return "+" + String.format("%.1f", bonus) + "% prix de vente";
            case XP_BOOST:
            case MONEY_BOOST:
            case TOKEN_BOOST:
            case MINERAL_GREED:
                return "+" + String.format("%.0f", bonus) + "% d'effet";
            case ABONDANCE_CRISTAL:
                return "+" + String.format("%.0f", bonus) + "s de durée (total: " + (60 + bonus) + "s)";
            case COMBUSTION_CRISTAL:
                return "+" + String.format("%.0f", bonus) + "% efficacité et -" + String.format("%.0f", bonus) + "% diminution";
            case ECHO_CRISTAL:
                return getEchoDescription(level);
            default:
                return "+" + String.format("%.1f", bonus) + "%";
        }
    }

    /**
     * Description spéciale pour EchoCristal
     */
    private String getEchoDescription(int level) {
        double chance1 = level * 2.0;
        double chance2 = level * 1.0;
        double chance3 = level * 0.5;
        double chance4 = level * 0.25;
        double chance5 = level * 0.125;

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        DecimalFormat df = new DecimalFormat("#.#####", symbols);

        // On applique le formatage à chaque valeur avant de construire la chaîne finale.
        return String.format("%s%% (1 écho), %s%% (2), %s%% (3), %s%% (4), %s%% (5)",
                df.format(chance1),
                df.format(chance2),
                df.format(chance3),
                df.format(chance4),
                df.format(chance5));
    }

    /**
     * Vérifie si ce type peut être appliqué avec un autre type sur la même pioche
     */
    public boolean isCompatibleWith(CristalType other) {
        if (this == other) return false; // Même type = incompatible
        return true; // Tous les autres types sont compatibles entre eux
    }

    /**
     * Calcule les chances d'écho pour EchoCristal
     */
    public double[] getEchoChances(int level) {
        if (this != ECHO_CRISTAL) return new double[0];

        return new double[] {
                level * 2.0,    // 1 écho
                level * 1.0,    // 2 échos
                level * 0.5,    // 3 échos
                level * 0.25,   // 4 échos
                level * 0.125   // 5 échos
        };
    }

    /**
     * Applique l'effet spécial pour AbondanceCristal
     */
    public int getAbondanceDurationBonus(int level) {
        if (this != ABONDANCE_CRISTAL) return 0;
        return (int) getBonus(level); // +3s par niveau
    }

    /**
     * Applique l'effet spécial pour CombustionCristal
     */
    public double getCombustionEfficiencyBonus(int level) {
        if (this != COMBUSTION_CRISTAL) return 0;
        return getBonus(level); // +3% par niveau
    }

    /**
     * Applique l'effet de réduction de diminution pour CombustionCristal
     */
    public double getCombustionDecayReduction(int level) {
        if (this != COMBUSTION_CRISTAL) return 0;
        return getBonus(level); // -3% par niveau
    }

    // Getters
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getBonusPerLevel() { return bonusPerLevel; }
    public double getMaxBonus() { return maxBonus; }
}