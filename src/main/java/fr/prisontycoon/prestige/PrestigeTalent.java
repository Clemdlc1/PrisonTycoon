package fr.prisontycoon.prestige;

/**
 * Énumération des bonus de prestige individuels (par colonne)
 * Système simplifié avec cycle de 4 prestiges
 */
public enum PrestigeTalent {

    // CYCLE 1 & 3 (P1, P3, P6, P8, P11, P13, etc.)
    MONEY_GREED_BONUS("Money Greed", "§6+3% Money Greed", 1),
    SELL_PRICE_BONUS("Prix de Vente", "§6+3% Prix de vente", 1),
    OUTPOST_BONUS("Gain Avant-Poste", "§6+3% Gain avant-poste", 1),

    // CYCLE 2 & 4 (P2, P4, P7, P9, P12, P14, etc.)
    TOKEN_GREED_BONUS("Token Greed", "§b+3% Token Greed", 2),
    TAX_REDUCTION("Réduction Taxe", "§b-1% Taxe", 2),
    PVP_MERCHANT_REDUCTION("Marchand PvP", "§b-1% Prix marchand PvP", 2);

    private final String displayName;
    private final String description;
    private final int cycle; // 1 ou 2 pour déterminer le cycle

    PrestigeTalent(String displayName, String description, int cycle) {
        this.displayName = displayName;
        this.description = description;
        this.cycle = cycle;
    }

    /**
     * Obtient tous les talents disponibles pour un niveau de prestige donné
     */
    public static PrestigeTalent[] getTalentsForPrestige(int prestigeLevel) {
        if (prestigeLevel < 1 || prestigeLevel > 50) return new PrestigeTalent[0];

        // Les talents spéciaux (P5, P10, etc.) n'utilisent pas les talents cycliques
        if (prestigeLevel % 5 == 0) return new PrestigeTalent[0];

        // Déterminer le cycle selon le modulo
        int cycleType = ((prestigeLevel - 1) % 4) + 1;

        if (cycleType == 1 || cycleType == 3) {
            // Cycle Money/Sell/Outpost (P1, P3, P6, P8, etc.)
            return new PrestigeTalent[]{MONEY_GREED_BONUS, SELL_PRICE_BONUS, OUTPOST_BONUS};
        } else {
            // Cycle Token/Tax/PvP (P2, P4, P7, P9, etc.)
            return new PrestigeTalent[]{TOKEN_GREED_BONUS, TAX_REDUCTION, PVP_MERCHANT_REDUCTION};
        }
    }

    /**
     * Vérifie si ce talent est disponible pour un niveau de prestige donné
     */
    public boolean isAvailableForPrestige(int prestigeLevel) {
        PrestigeTalent[] available = getTalentsForPrestige(prestigeLevel);
        for (PrestigeTalent talent : available) {
            if (talent == this) return true;
        }
        return false;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getCycle() {
        return cycle;
    }
}