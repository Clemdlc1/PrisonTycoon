package fr.prisontycoon.reputation;

/**
 * Énumération des niveaux de réputation selon les spécifications
 */
public enum ReputationTier {

    INFAME(-1000, -667, "Infâme", -0.10, -0.25, "Toutes les offres exclusives"),
    CRIMINEL(-666, -334, "Criminel", -0.05, -0.10, "La plupart des offres"),
    SUSPECT(-333, -1, "Suspect", 0.03, -0.05, "Quelques offres"),
    ORDINAIRE(0, 0, "Ordinaire", 0.0, 0.0, "Offres limitées"),
    RESPECTE(1, 333, "Respecté", -0.03, 0.05, "Très peu d'offres"),
    HONORABLE(334, 666, "Honorable", -0.05, 0.10, "0 à 1 offre parfois"),
    EXEMPLAIRE(667, 1000, "Exemplaire", -0.10, 0.0, "Aucune offre");

    private final int minReputation;
    private final int maxReputation;
    private final String title;
    private final double taxModifier;
    private final double blackMarketPriceModifier;
    private final String blackMarketDescription;

    ReputationTier(int minReputation, int maxReputation, String title,
                   double taxModifier, double blackMarketPriceModifier, String blackMarketDescription) {
        this.minReputation = minReputation;
        this.maxReputation = maxReputation;
        this.title = title;
        this.taxModifier = taxModifier;
        this.blackMarketPriceModifier = blackMarketPriceModifier;
        this.blackMarketDescription = blackMarketDescription;
    }

    /**
     * Obtient le niveau de réputation basé sur la valeur numérique
     */
    public static ReputationTier fromReputation(int reputation) {
        for (ReputationTier tier : values()) {
            if (tier == ORDINAIRE && reputation == 0) {
                return tier;
            } else if (reputation >= tier.minReputation && reputation <= tier.maxReputation) {
                return tier;
            }
        }

        // Par défaut, retourne le niveau approprié selon les bornes
        if (reputation > 1000) return EXEMPLAIRE;
        if (reputation < -1000) return INFAME;

        return ORDINAIRE;
    }

    /**
     * Vérifie si cette réputation est positive
     */
    public boolean isPositive() {
        return this.ordinal() > ORDINAIRE.ordinal();
    }

    /**
     * Vérifie si cette réputation est négative
     */
    public boolean isNegative() {
        return this.ordinal() < ORDINAIRE.ordinal();
    }

    /**
     * Obtient le niveau suivant (progression positive)
     */
    public ReputationTier getNextTier() {
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal >= values().length) {
            return this;
        }
        return values()[nextOrdinal];
    }

    /**
     * Obtient le niveau précédent (dégradation)
     */
    public ReputationTier getPreviousTier() {
        int prevOrdinal = this.ordinal() - 1;
        if (prevOrdinal < 0) {
            return this;
        }
        return values()[prevOrdinal];
    }

    // Getters
    public int getMinReputation() {
        return minReputation;
    }

    public int getMaxReputation() {
        return maxReputation;
    }

    public String getTitle() {
        return title;
    }

    public double getTaxModifier() {
        return taxModifier;
    }

    public double getBlackMarketPriceModifier() {
        return blackMarketPriceModifier;
    }

    public String getBlackMarketDescription() {
        return blackMarketDescription;
    }

    /**
     * Formatage coloré du titre selon le niveau
     */
    public String getColoredTitle() {
        return switch (this) {
            case EXEMPLAIRE -> "§a§l" + title;
            case HONORABLE -> "§2" + title;
            case RESPECTE -> "§a" + title;
            case ORDINAIRE -> "§7" + title;
            case SUSPECT -> "§6" + title;
            case CRIMINEL -> "§c" + title;
            case INFAME -> "§4§l" + title;
            default -> "§7" + title;
        };
    }

    /**
     * Description des effets de ce niveau
     */
    public String getEffectsDescription() {
        StringBuilder desc = new StringBuilder();

        if (taxModifier != 0) {
            String sign = taxModifier > 0 ? "+" : "";
            desc.append("§7Taxes: ").append(sign).append((int) (taxModifier * 100)).append("%");
        } else {
            desc.append("§7Taxes: Aucun effet");
        }

        desc.append("\n§7Black Market: ").append(blackMarketDescription);

        if (blackMarketPriceModifier != 0) {
            String sign = blackMarketPriceModifier > 0 ? "+" : "";
            desc.append(" (").append(sign).append((int) (blackMarketPriceModifier * 100)).append("% prix)");
        }

        return desc.toString();
    }
}