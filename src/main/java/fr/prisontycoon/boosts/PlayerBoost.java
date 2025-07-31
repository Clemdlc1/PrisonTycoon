package fr.prisontycoon.boosts;

/**
 * Représente un boost actif pour un joueur
 */
public class PlayerBoost {

    private final BoostType type;
    private final long startTime;
    private final long endTime;
    private final double bonusPercentage;
    private final boolean isAdminBoost;

    /**
     * Constructeur pour un boost normal de joueur
     */
    public PlayerBoost(BoostType type, int durationSeconds, double bonusPercentage) {
        this.type = type;
        this.startTime = System.currentTimeMillis();
        this.endTime = this.startTime + (durationSeconds * 1000L);
        this.bonusPercentage = bonusPercentage;
        this.isAdminBoost = false;
    }

    /**
     * Constructeur pour un boost admin (ne se sauvegarde pas)
     */
    public PlayerBoost(BoostType type, int durationSeconds, double bonusPercentage, boolean isAdminBoost) {
        this.type = type;
        this.startTime = System.currentTimeMillis();
        this.endTime = this.startTime + (durationSeconds * 1000L);
        this.bonusPercentage = bonusPercentage;
        this.isAdminBoost = isAdminBoost;
    }

    /**
     * Constructeur pour charger depuis la sauvegarde
     */
    public PlayerBoost(BoostType type, long startTime, long endTime, double bonusPercentage) {
        this.type = type;
        this.startTime = startTime;
        this.endTime = endTime;
        this.bonusPercentage = bonusPercentage;
        this.isAdminBoost = false;
    }

    /**
     * Vérifie si le boost est encore actif
     */
    public boolean isActive() {
        return System.currentTimeMillis() < endTime;
    }

    /**
     * Retourne le temps restant en secondes
     */
    public long getTimeRemainingSeconds() {
        long remaining = (endTime - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * Retourne le temps restant formaté
     */
    public String getFormattedTimeRemaining() {
        return BoostType.formatTimeRemaining(getTimeRemainingSeconds());
    }

    /**
     * Retourne la durée totale en secondes
     */
    public long getTotalDurationSeconds() {
        return (endTime - startTime) / 1000;
    }

    /**
     * Retourne le pourcentage de progression (0.0 à 1.0)
     */
    public double getProgress() {
        long total = endTime - startTime;
        long elapsed = System.currentTimeMillis() - startTime;
        return Math.min(1.0, Math.max(0.0, (double) elapsed / total));
    }

    /**
     * Retourne une description complète du boost
     */
    public String getDescription() {
        return type.getFormattedName() + " " +
                type.getColor() + "+" + String.format("%.0f", bonusPercentage) + "% " +
                "§7(" + getFormattedTimeRemaining() + ")";
    }

    /**
     * Retourne une version courte pour l'affichage
     */
    public String getShortDescription() {
        return type.getFormattedName() + " §7(" + getFormattedTimeRemaining() + ")";
    }

    // Getters
    public BoostType getType() {
        return type;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public double getBonusPercentage() {
        return bonusPercentage;
    }

    public boolean isAdminBoost() {
        return isAdminBoost;
    }

    @Override
    public String toString() {
        return "PlayerBoost{" +
                "type=" + type +
                ", bonus=" + bonusPercentage + "%" +
                ", remaining=" + getTimeRemainingSeconds() + "s" +
                ", admin=" + isAdminBoost +
                '}';
    }
}