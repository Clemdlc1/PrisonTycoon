package fr.prisontycoon.boosts;

import org.bukkit.Material;

/**
 * Types de boosts temporaires disponibles
 */
public enum BoostType {

    TOKEN_GREED("Token Greed", "§b💎 Boost Token Greed", "§7+50% de gains de tokens",
            Material.DIAMOND, 50.0, 3600),

    MONEY_GREED("Money Greed", "§6💰 Boost Money Greed", "§7+50% de gains de coins",
            Material.GOLD_INGOT, 50.0, 3600),

    EXP_GREED("XP Greed", "§a⭐ Boost XP Greed", "§7+50% de gains d'expérience",
            Material.EXPERIENCE_BOTTLE, 50.0, 3600),

    SELL_BOOST("Sell Boost", "§e💸 Boost Vente", "§7+30% de prix de vente",
            Material.EMERALD, 30.0, 3600),

    MINERAL_GREED("Mineral Greed", "§9⛏️ Boost Fortune", "§7+40% d'effet Fortune",
            Material.DIAMOND_PICKAXE, 40.0, 3600),

    JOB_XP_BOOST("Job XP", "§d🔨 Boost XP Métier", "§7+75% de gains d'XP métier",
            Material.ANVIL, 75.0, 3600),

    GLOBAL_BOOST("Global", "§c🌟 Boost Global", "§7+25% sur tous les gains",
            Material.NETHER_STAR, 25.0, 1800);

    private final String displayName;
    private final String itemName;
    private final String description;
    private final Material material;
    private final double bonusPercentage;
    private final int defaultDurationSeconds;

    BoostType(String displayName, String itemName, String description, Material material,
              double bonusPercentage, int defaultDurationSeconds) {
        this.displayName = displayName;
        this.itemName = itemName;
        this.description = description;
        this.material = material;
        this.bonusPercentage = bonusPercentage;
        this.defaultDurationSeconds = defaultDurationSeconds;
    }

    /**
     * Retourne la couleur selon le type de boost
     */
    public String getColor() {
        return switch (this) {
            case TOKEN_GREED -> "§b";
            case MONEY_GREED -> "§6";
            case EXP_GREED -> "§a";
            case SELL_BOOST -> "§e";
            case MINERAL_GREED -> "§9";
            case JOB_XP_BOOST -> "§d";
            case GLOBAL_BOOST -> "§c";
        };
    }

    /**
     * Retourne l'emoji selon le type
     */
    public String getEmoji() {
        return switch (this) {
            case TOKEN_GREED -> "💎";
            case MONEY_GREED -> "💰";
            case EXP_GREED -> "⭐";
            case SELL_BOOST -> "💸";
            case MINERAL_GREED -> "⛏️";
            case JOB_XP_BOOST -> "🔨";
            case GLOBAL_BOOST -> "🌟";
        };
    }

    /**
     * Retourne le nom coloré avec emoji
     */
    public String getFormattedName() {
        return getColor() + getEmoji() + " " + displayName;
    }

    /**
     * Retourne la description du bonus
     */
    public String getBonusDescription() {
        return getColor() + "+" + String.format("%.0f", bonusPercentage) + "%";
    }

    /**
     * Formate le temps restant en format lisible
     */
    public static String formatTimeRemaining(long secondsLeft) {
        if (secondsLeft <= 0) return "§cExpiré";

        long hours = secondsLeft / 3600;
        long minutes = (secondsLeft % 3600) / 60;
        long seconds = secondsLeft % 60;

        if (hours > 0) {
            return String.format("§e%dh %02dm %02ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("§e%dm %02ds", minutes, seconds);
        } else {
            return String.format("§e%ds", seconds);
        }
    }

    /**
     * Parse un nom de boost en BoostType
     */
    public static BoostType fromString(String name) {
        if (name == null) return null;

        try {
            return BoostType.valueOf(name.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            // Essaie avec le display name
            for (BoostType type : values()) {
                if (type.displayName.equalsIgnoreCase(name) ||
                        type.name().equalsIgnoreCase(name.replace(" ", "_"))) {
                    return type;
                }
            }
            return null;
        }
    }

    // Getters
    public String getDisplayName() {
        return displayName;
    }

    public String getItemName() {
        return itemName;
    }

    public String getDescription() {
        return description;
    }

    public Material getMaterial() {
        return material;
    }

    public double getBonusPercentage() {
        return bonusPercentage;
    }

    public int getDefaultDurationSeconds() {
        return defaultDurationSeconds;
    }
}