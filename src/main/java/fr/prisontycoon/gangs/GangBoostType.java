package fr.prisontycoon.gangs;

import org.bukkit.Material;

/**
 * Types de boosts temporaires disponibles pour les gangs
 */

public enum GangBoostType {

    VENTE("Boost Vente", "§e", Material.EMERALD, "Augmente les prix de vente",
            new long[]{100, 500, 2000}),

    TOKEN("Boost Token", "§b", Material.DIAMOND, "Augmente les gains de tokens",
            new long[]{200, 800, 3000}),

    XP("Boost XP", "§a", Material.EXPERIENCE_BOTTLE, "Augmente les gains d'expérience",
            new long[]{150, 600, 2500}),

    BEACONS("Boost Beacons", "§c", Material.BEACON, "Augmente les gains de beacons",
            new long[]{300, 1200, 5000});

    private final String displayName;
    private final String color;
    private final Material material;
    private final String description;
    private final long[] costs; // Coûts pour les tiers 1, 2, 3

    GangBoostType(String displayName, String color, Material material, String description, long[] costs) {
        this.displayName = displayName;
        this.color = color;
        this.material = material;
        this.description = description;
        this.costs = costs;
    }

    /**
     * Parse un nom de boost en GangBoostType
     */
    public static GangBoostType fromString(String name) {
        if (name == null) return null;

        try {
            return GangBoostType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Essaie avec les noms d'affichage
            for (GangBoostType type : values()) {
                if (type.displayName.equalsIgnoreCase(name) ||
                    type.name().equalsIgnoreCase(name.replace(" ", "_"))) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * Obtient tous les types de boost sous forme de liste
     */
    public static String getAllTypesString() {
        StringBuilder builder = new StringBuilder();
        for (GangBoostType type : values()) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(type.name().toLowerCase());
        }
        return builder.toString();
    }

    /**
     * Nom d'affichage du boost
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Nom d'affichage coloré
     */
    public String getColoredDisplayName() {
        return color + displayName;
    }

    /**
     * Couleur du boost
     */
    public String getColor() {
        return color;
    }

    /**
     * Matériau pour l'icône
     */
    public Material getMaterial() {
        return material;
    }

    /**
     * Description du boost
     */
    public String getDescription() {
        return description;
    }

    /**
     * Coûts des différents tiers
     */
    public long[] getCosts() {
        return costs.clone();
    }

    /**
     * Coût d'un tier spécifique
     */
    public long getCost(int tier) {
        if (tier < 1 || tier > 3) return 0;
        return costs[tier - 1];
    }

    /**
     * Durées des différents tiers (en minutes)
     */
    public int[] getDurations() {
        return new int[]{30, 60, 180}; // 30min, 1h, 3h
    }

    /**
     * Durée d'un tier spécifique (en minutes)
     */
    public int getDuration(int tier) {
        int[] durations = getDurations();
        if (tier < 1 || tier > 3) return 0;
        return durations[tier - 1];
    }

    /**
     * Multiplicateurs des différents tiers
     */
    public double[] getMultipliers() {
        return new double[]{1.5, 2.0, 3.0}; // x1.5, x2, x3
    }

    /**
     * Multiplicateur d'un tier spécifique
     */
    public double getMultiplier(int tier) {
        double[] multipliers = getMultipliers();
        if (tier < 1 || tier > 3) return 1.0;
        return multipliers[tier - 1];
    }

    /**
     * Obtient l'emoji associé au type de boost
     */
    public String getEmoji() {
        return switch (this) {
            case VENTE -> "💰";
            case TOKEN -> "💎";
            case XP -> "⭐";
            case BEACONS -> "🔥";
        };
    }

    /**
     * Obtient la description formatée avec multiplicateur et durée
     */
    public String getFormattedDescription(int tier) {
        if (tier < 1 || tier > 3) return description;

        String[] multiplierText = {"1.5x", "2x", "3x"};
        int duration = getDuration(tier);

        return String.format("%s %s pendant %s",
                description,
                multiplierText[tier - 1],
                formatDuration(duration));
    }

    /**
     * Formate une durée en texte lisible
     */
    private String formatDuration(int minutes) {
        if (minutes >= 60) {
            int hours = minutes / 60;
            int remainingMinutes = minutes % 60;
            if (remainingMinutes == 0) {
                return hours + "h";
            } else {
                return hours + "h" + remainingMinutes + "m";
            }
        } else {
            return minutes + "min";
        }
    }

    /**
     * Vérifie si ce boost affecte un type de gain spécifique
     */
    public boolean affects(String gainType) {
        return switch (this) {
            case VENTE -> gainType.equalsIgnoreCase("sell") || gainType.equalsIgnoreCase("vente");
            case TOKEN -> gainType.equalsIgnoreCase("token") || gainType.equalsIgnoreCase("tokens");
            case XP -> gainType.equalsIgnoreCase("xp") || gainType.equalsIgnoreCase("experience");
            case BEACONS -> gainType.equalsIgnoreCase("beacon") || gainType.equalsIgnoreCase("beacons");
        };
    }

    /**
     * Obtient le cooldown en millisecondes (1 heure)
     */
    public long getCooldownMs() {
        return 60 * 60 * 1000; // 1 heure
    }

    /**
     * Obtient la limite quotidienne
     */
    public int getDailyLimit() {
        return 3;
    }

    @Override
    public String toString() {
        return displayName;
    }
}