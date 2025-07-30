package fr.prisontycoon.gangs;

import org.bukkit.Material;

/**
 * Représente un talent de gang avec ses propriétés et coûts
 */
public class GangTalent {

    private final String id;
    private final String name;
    private final String description;
    private final long cost;
    private final int requiredGangLevel;
    private final String category;
    private final double value; // Valeur numérique du talent (pourcentage, multiplicateur, etc.)
    private final Material iconMaterial;

    public GangTalent(String id, String name, String description, long cost,
                      int requiredGangLevel, String category, double value) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.cost = cost;
        this.requiredGangLevel = requiredGangLevel;
        this.category = category;
        this.value = value;
        this.iconMaterial = getIconMaterialForCategory(category);
    }

    /**
     * Détermine l'icône basée sur la catégorie du talent
     */
    private Material getIconMaterialForCategory(String category) {
        return switch (category.toLowerCase()) {
            case "sell_boost" -> Material.EMERALD;
            case "gang_collectif" -> Material.SKELETON_SKULL;
            case "beacon_multiplier" -> Material.BEACON;
            case "token_boost" -> Material.DIAMOND;
            case "experience_boost" -> Material.EXPERIENCE_BOTTLE;
            case "defense_boost" -> Material.SHIELD;
            default -> Material.ENCHANTED_BOOK;
        };
    }

    /**
     * Obtient le niveau entier du talent (pour les talents à niveaux)
     */
    public int getLevel() {
        return (int) value;
    }

    /**
     * Vérifie si ce talent est de type pourcentage
     */
    public boolean isPercentage() {
        return category.contains("boost") && !category.equals("beacon_multiplier");
    }

    /**
     * Vérifie si ce talent est de type multiplicateur
     */
    public boolean isMultiplier() {
        return category.equals("beacon_multiplier");
    }

    /**
     * Obtient la description formatée avec la valeur
     */
    public String getFormattedDescription() {
        if (isPercentage()) {
            return description.replace("{value}", "+" + (int) value + "%");
        } else if (isMultiplier()) {
            return description.replace("{value}", "x" + value);
        } else if (category.equals("gang_collectif")) {
            return description.replace("{value}", "+" + (int) value);
        }
        return description;
    }

    /**
     * Obtient le nom coloré selon la catégorie
     */
    public String getColoredName() {
        String color = switch (category.toLowerCase()) {
            case "sell_boost" -> "§a"; // Vert
            case "gang_collectif" -> "§b"; // Cyan
            case "beacon_multiplier" -> "§c"; // Rouge
            case "token_boost" -> "§9"; // Bleu foncé
            case "experience_boost" -> "§e"; // Jaune
            case "defense_boost" -> "§8"; // Gris foncé
            default -> "§f"; // Blanc
        };
        return color + name;
    }

    /**
     * Obtient l'emoji associé à la catégorie
     */
    public String getCategoryEmoji() {
        return switch (category.toLowerCase()) {
            case "sell_boost" -> "💰";
            case "gang_collectif" -> "👥";
            case "beacon_multiplier" -> "🔥";
            case "token_boost" -> "💎";
            case "experience_boost" -> "⭐";
            case "defense_boost" -> "🛡️";
            default -> "📖";
        };
    }

    /**
     * Vérifie si ce talent peut être acheté par un gang
     */
    public boolean canBePurchased(int gangLevel, long gangBankBalance) {
        return gangLevel >= requiredGangLevel && gangBankBalance >= cost;
    }

    /**
     * Obtient les prérequis sous forme de texte
     */
    public String getRequirementsText() {
        return "§7Niveau gang: §6" + requiredGangLevel + " §7| Coût: §e" + formatCost(cost);
    }

    /**
     * Formate le coût en format lisible
     */
    private String formatCost(long cost) {
        if (cost >= 1_000_000_000L) {
            return (cost / 1_000_000_000L) + "B coins";
        } else if (cost >= 1_000_000L) {
            return (cost / 1_000_000L) + "M coins";
        } else if (cost >= 1_000L) {
            return (cost / 1_000L) + "K coins";
        } else {
            return cost + " coins";
        }
    }

    /**
     * Vérifie si c'est un talent exclusif (un seul par catégorie)
     */
    public boolean isExclusive() {
        return category.equals("beacon_multiplier") || category.equals("gang_collectif");
    }

    /**
     * Obtient le talent prérequis (si applicable)
     */
    public String getPrerequisiteTalent() {
        if (category.equals("sell_boost") && getLevel() > 1) {
            return "sell_boost_" + (getLevel() - 1);
        } else if (category.equals("gang_collectif") && getLevel() > 1) {
            return "gang_collectif_" + (getLevel() - 1);
        } else if (category.equals("beacon_multiplier") && getLevel() > 1) {
            return "beacon_multiplier_" + (getLevel() - 1);
        }
        return null;
    }

    // Getters

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getCost() {
        return cost;
    }

    public int getRequiredGangLevel() {
        return requiredGangLevel;
    }

    public String getCategory() {
        return category;
    }

    public double getValue() {
        return value;
    }

    public Material getIconMaterial() {
        return iconMaterial;
    }

    @Override
    public String toString() {
        return "GangTalent{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", category='" + category + '\'' +
               ", cost=" + cost +
               ", value=" + value +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GangTalent that = (GangTalent) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}