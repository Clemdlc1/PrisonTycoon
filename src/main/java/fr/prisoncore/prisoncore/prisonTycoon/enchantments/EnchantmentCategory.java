package fr.prisoncore.prisoncore.prisonTycoon.enchantments;

/**
 * Catégories d'enchantements
 */
public enum EnchantmentCategory {
    ECONOMIC("§6Économiques", "💰"),
    UTILITY("§aUtilités", "⚡"),
    MOBILITY("§bMobilité", "💨"),
    SPECIAL("§dSpéciaux", "✨");

    private final String displayName;
    private final String icon;

    EnchantmentCategory(String displayName, String icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }
}
