package fr.prisontycoon.prestige;

/**
 * Énumération des talents de prestige disponibles
 */
public enum PrestigeTalent {

    // Talents cycliques (se répètent selon le modulo)
    PROFIT_AMELIORE("Profit Amélioré",
            "§6+3% Money Greed\n§6+3% Prix de vente\n§6+3% Gain avant-poste",
            1), // P1, P6, P11, etc.

    ECONOMIE_OPTIMISEE("Économie Optimisée",
            "§b+3% Token Greed\n§b-1% Taxe\n§b-1% Prix marchand PvP",
            2), // P2, P7, P12, etc.

    PROFIT_AMELIORE_II("Profit Amélioré II",
            "§6+3% Effet Money Greed\n§6+3% Prix vente direct\n§6+3% Gain rinacoins avant-poste",
            3), // P3, P8, P13, etc.

    ECONOMIE_OPTIMISEE_II("Économie Optimisée II",
            "§b+3% Effet Token Greed\n§b-1% Taux taxe final\n§b-1% Prix marchand PvP",
            4); // P4, P9, P14, etc.

    private final String displayName;
    private final String description;
    private final int cycle; // 1-4 pour déterminer à quels prestiges ce talent est disponible

    PrestigeTalent(String displayName, String description, int cycle) {
        this.displayName = displayName;
        this.description = description;
        this.cycle = cycle;
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

    /**
     * Détermine si ce talent est disponible pour un niveau de prestige donné
     */
    public boolean isAvailableForPrestige(int prestigeLevel) {
        if (prestigeLevel < 1 || prestigeLevel > 50) return false;

        // Les talents spéciaux (P5, P10, etc.) n'utilisent pas les talents cycliques
        if (prestigeLevel % 5 == 0) return false;

        // Vérifie si le prestige correspond au cycle de ce talent
        return ((prestigeLevel - 1) % 4) + 1 == this.cycle;
    }

    /**
     * Obtient le talent disponible pour un niveau de prestige donné
     */
    public static PrestigeTalent getTalentForPrestige(int prestigeLevel) {
        for (PrestigeTalent talent : values()) {
            if (talent.isAvailableForPrestige(prestigeLevel)) {
                return talent;
            }
        }
        return null;
    }

    /**
     * Calcule le niveau d'un talent basé sur le nombre de fois qu'il a été choisi
     */
    public static int getTalentLevel(PrestigeTalent talent, int currentPrestige) {
        int count = 0;
        for (int p = 1; p <= currentPrestige; p++) {
            if (talent.isAvailableForPrestige(p)) {
                count++;
            }
        }
        return count;
    }
}