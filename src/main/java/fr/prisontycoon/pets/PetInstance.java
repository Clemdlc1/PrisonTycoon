package fr.prisontycoon.pets;

import java.util.Objects;

/**
 * Instance possédée d'un pet par un joueur.
 * - growth: croissance (0..50) ; augmente de +1 tous les 100 niveaux.
 * - xp: xp actuelle vers le prochain point de croissance.
 * - equipped: si le pet est équipé (dans un slot actif).
 */
public class PetInstance {
    private final PetDefinition definition;
    private int growth; // 0..50
    private long xp;    // xp courante vers la prochaine croissance
    private boolean equipped;

    public PetInstance(PetDefinition definition) {
        this.definition = Objects.requireNonNull(definition);
        this.growth = 0;
        this.xp = 0L;
        this.equipped = false;
    }

    public PetDefinition getDefinition() {
        return definition;
    }

    public int getGrowth() {
        return growth;
    }

    public void setGrowth(int growth) {
        this.growth = Math.max(0, Math.min(50, growth));
    }

    public long getXp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0L, xp);
    }

    public boolean isEquipped() {
        return equipped;
    }

    public void setEquipped(boolean equipped) {
        this.equipped = equipped;
    }

    /**
     * Retourne le bonus total (en pourcentage) produit par ce pet selon la formule: base% * growth.
     */
    public double getTotalPercentBonus() {
        return definition.basePerGrowthPercent() * growth;
    }
}


