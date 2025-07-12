package fr.prisoncore.prisoncore.prisonTycoon.crystals;

import org.bukkit.Material;

/**
 * Types de cristaux avec leurs effets
 */
public enum CrystalType {
    SELL_BOOST("SellBoost", "§6Boost de Vente",
            "Augmente le prix de vente des blocs",
            3.5, 70.0, Material.GOLD_INGOT),

    XP_BOOST("XPBoost", "§aBoost d'XP",
            "Augmente l'effet d'XP Greed",
            5.0, 100.0, Material.EXPERIENCE_BOTTLE),

    MONEY_BOOST("MoneyBoost", "§6Boost de Coins",
            "Augmente l'effet de Money Greed",
            5.0, 100.0, Material.GOLD_NUGGET),

    TOKEN_BOOST("TokenBoost", "§eBoost de Tokens",
            "Augmente l'effet de Token Greed",
            5.0, 100.0, Material.SUNFLOWER),

    MINERAL_GREED("MineralGreed", "§2Cupidité Minérale",
            "Augmente l'effet de Fortune",
            5.0, 100.0, Material.EMERALD),

    ABONDANCE_CRISTAL("AbondanceCristal", "§6Cristal d'Abondance",
            "Prolonge la durée d'Abondance",
            3.0, 60.0, Material.NETHER_STAR),

    COMBUSTION_CRISTAL("CombustionCristal", "§cCristal de Combustion",
            "Améliore Combustion et réduit sa diminution",
            3.0, 60.0, Material.FIRE_CHARGE),

    ECHO_CRISTAL("EchoCristal", "§dCristal d'Écho",
            "Chance d'échos pour Laser et Explosion",
            5.0, 100.0, Material.ECHO_SHARD);

    private final String internalName;
    private final String displayName;
    private final String description;
    private final double bonusPerLevel;
    private final double maxBonus;
    private final Material displayMaterial;

    CrystalType(String internalName, String displayName, String description,
                double bonusPerLevel, double maxBonus, Material displayMaterial) {
        this.internalName = internalName;
        this.displayName = displayName;
        this.description = description;
        this.bonusPerLevel = bonusPerLevel;
        this.maxBonus = maxBonus;
        this.displayMaterial = displayMaterial;
    }

    public String getInternalName() { return internalName; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public double getBonusPerLevel() { return bonusPerLevel; }
    public double getMaxBonus() { return maxBonus; }
    public Material getDisplayMaterial() { return displayMaterial; }

    /**
     * Calcule le bonus pour un niveau donné
     */
    public double calculateBonus(int level) {
        double bonus = Math.min(bonusPerLevel * level, maxBonus);
        return Math.max(0, bonus);
    }

    /**
     * Obtient la description détaillée avec bonus
     */
    public String getDetailedDescription(int level) {
        double bonus = calculateBonus(level);
        return switch (this) {
            case SELL_BOOST -> "§7+" + String.format("%.1f%%", bonus) + " prix de vente des blocs";
            case XP_BOOST -> "§7+" + String.format("%.0f%%", bonus) + " effet XP Greed";
            case MONEY_BOOST -> "§7+" + String.format("%.0f%%", bonus) + " effet Money Greed";
            case TOKEN_BOOST -> "§7+" + String.format("%.0f%%", bonus) + " effet Token Greed";
            case MINERAL_GREED -> "§7+" + String.format("%.0f%%", bonus) + " effet Fortune";
            case ABONDANCE_CRISTAL -> "§7+" + String.format("%.0f", bonus) + "s durée Abondance";
            case COMBUSTION_CRISTAL -> "§7+" + String.format("%.0f%%", bonus) + " Combustion, " +
                    "-" + String.format("%.0f%%", bonus) + " diminution";
            case ECHO_CRISTAL -> "§7" + String.format("%.1f%%", bonus) + " chance 1er écho";
        };
    }

    /**
     * Obtient un type de cristal aléatoire
     */
    public static CrystalType getRandomType() {
        CrystalType[] types = values();
        return types[(int) (Math.random() * types.length)];
    }
}