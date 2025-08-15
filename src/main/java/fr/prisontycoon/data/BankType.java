package fr.prisontycoon.data;

/**
 * Types de banques avec bonus/malus globaux.
 * Le joueur peut en choisir un, modifiable 1 fois par semaine.
 */
public enum BankType {
    // Aucune banque choisie
    NONE(
            "NONE",
            "Aucune banque",
            "Choisissez un type de banque pour débloquer les fonctionnalités.",
            1.00,
            1.00,
            1.00,
            1.00,
            0,
            1.00
    ),
    // 1. -5% de vente ; -5% de coût pour prestige
    PRUDENTIA(
            "PRUDENTIA",
            "Banque Prudentia",
            "Moins de risque, moins de gains.",
            0.95, // multiplicateur de vente
            0.95, // multiplicateur du coût de prestige
            1.00, // multiplicateur du coût d'achat d'investissements
            1.00, // multiplicateur des intérêts gagnés
            0,    // bonus d'imprimantes max sur l'île
            1.00  // multiplicateur de capacité du coffre-fort
    ),

    // 2. +5% de vente ; +10% de frais pour l'investissement et -10% sur les intérêts
    MERCATORIA(
            "MERCATORIA",
            "Banque Mercatoria",
            "+5% de vente, mais investissements plus coûteux et intérêts réduits.",
            1.05,
            1.00,
            1.10,
            0.90,
            0,
            1.00
    ),

    // 3. +5 imprimantes max sur l'île ; -10% de vente
    INDUSTRIA(
            "INDUSTRIA",
            "Banque Industria",
            "+5 imprimantes max, mais -10% sur la vente.",
            0.90,
            1.00,
            1.00,
            1.00,
            5,
            1.00
    ),

    // 4. Coffre-fort avantageux : capacité +25%, frais de retrait -10%
    FORTIS(
            "FORTIS",
            "Banque Fortis",
            "Coffre-fort renforcé (capacité +25%).",
            1.00,
            1.00,
            1.00,
            1.00,
            0,
            1.25
    );

    private final String id;
    private final String displayName;
    private final String description;
    private final double sellMultiplier;
    private final double prestigeCostMultiplier;
    private final double investmentBuyCostMultiplier;
    private final double interestGainMultiplier;
    private final int islandPrintersBonus;
    private final double safeLimitMultiplier;

    BankType(String id,
             String displayName,
             String description,
             double sellMultiplier,
             double prestigeCostMultiplier,
             double investmentBuyCostMultiplier,
             double interestGainMultiplier,
             int islandPrintersBonus,
             double safeLimitMultiplier) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.sellMultiplier = sellMultiplier;
        this.prestigeCostMultiplier = prestigeCostMultiplier;
        this.investmentBuyCostMultiplier = investmentBuyCostMultiplier;
        this.interestGainMultiplier = interestGainMultiplier;
        this.islandPrintersBonus = islandPrintersBonus;
        this.safeLimitMultiplier = safeLimitMultiplier;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getSellMultiplier() {
        return sellMultiplier;
    }

    public double getPrestigeCostMultiplier() {
        return prestigeCostMultiplier;
    }

    public double getInvestmentBuyCostMultiplier() {
        return investmentBuyCostMultiplier;
    }

    public double getInterestGainMultiplier() {
        return interestGainMultiplier;
    }

    public int getIslandPrintersBonus() {
        return islandPrintersBonus;
    }

    public double getSafeLimitMultiplier() {
        return safeLimitMultiplier;
    }

    public static BankType fromId(String id) {
        if (id == null) return NONE;
        for (BankType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return NONE;
    }
}


