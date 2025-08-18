package fr.prisontycoon.pets;

/**
 * Rareté d'un pet. Plus la rareté est élevée, plus l'XP requise par niveau est grande.
 */
public enum PetRarity {
    COMMON(1.0),
    RARE(1.5),
    EPIC(2.25),
    MYTHIC(3.5);

    private final double xpScale; // multiplicateur de l'XP requise

    PetRarity(double xpScale) {
        this.xpScale = xpScale;
    }

    /**
     * Multiplicateur appliqué sur l'XP requise par niveau pour cette rareté.
     */
    public double getXpScale() {
        return xpScale;
    }
}


