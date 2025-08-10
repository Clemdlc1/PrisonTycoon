package fr.prisontycoon.quests;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.entity.Player;

/**
 * Récompenses d'une quête (beacons, xp métier actif, vouchers/boosters).
 * VERSION COMPLÈTE avec tous les getters nécessaires
 */
public class QuestRewards {
    private final long beacons;
    private final int jobXp;
    private final VoucherType voucherType; // optionnel
    private final int voucherTier;         // optionnel
    private final BoostType boostType;     // optionnel
    private final int boostMinutes;        // optionnel
    private final double boostPercent;     // optionnel

    /**
     * Constructeur principal pour toutes les récompenses
     */
    public QuestRewards(long beacons, int jobXp,
                        VoucherType voucherType, int voucherTier,
                        BoostType boostType, int boostMinutes, double boostPercent) {
        this.beacons = Math.max(0, beacons);
        this.jobXp = Math.max(0, jobXp);
        this.voucherType = voucherType;
        this.voucherTier = Math.max(1, voucherTier);
        this.boostType = boostType;
        this.boostMinutes = Math.max(1, boostMinutes);
        this.boostPercent = Math.max(1.0, boostPercent);
    }

    /**
     * Constructeur simplifié pour récompenses basiques (beacons + XP uniquement)
     */
    public QuestRewards(long beacons, int jobXp) {
        this(beacons, jobXp, null, 0, null, 0, 0.0);
    }

    /**
     * Constructeur avec voucher uniquement
     */
    public QuestRewards(long beacons, int jobXp, VoucherType voucherType, int voucherTier) {
        this(beacons, jobXp, voucherType, voucherTier, null, 0, 0.0);
    }

    /**
     * Constructeur avec boost uniquement
     */
    public QuestRewards(long beacons, int jobXp, BoostType boostType, int boostMinutes, double boostPercent) {
        this(beacons, jobXp, null, 0, boostType, boostMinutes, boostPercent);
    }

    /**
     * Octroie toutes les récompenses au joueur
     */
    public void grant(PrisonTycoon plugin, Player player) {
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Ajout des beacons
        if (beacons > 0) {
            data.addBeacons(beacons);
        }

        // Ajout de l'XP métier actif
        String activeProfession = data.getActiveProfession();
        if (jobXp > 0 && activeProfession != null) {
            plugin.getProfessionManager().addProfessionXP(player, activeProfession, jobXp);
        }

        // Création et don du voucher si spécifié
        if (voucherType != null && voucherTier > 0) {
            var voucherItem = plugin.getVoucherManager().createVoucher(voucherType, voucherTier);
            boolean added = plugin.getContainerManager().addItemToContainers(player, voucherItem);
            if (!added) {
                player.getInventory().addItem(voucherItem);
            }
        }

        // Création et don du boost si spécifié
        if (boostType != null && boostMinutes > 0 && boostPercent > 0) {
            var boostItem = plugin.getBoostManager().createBoostItem(boostType, boostMinutes, boostPercent);
            boolean added = plugin.getContainerManager().addItemToContainers(player, boostItem);
            if (!added) {
                player.getInventory().addItem(boostItem);
            }
        }

        // Marque les données comme modifiées pour sauvegarde
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Génère une description textuelle des récompenses pour affichage
     */
    public String getDescription() {
        StringBuilder desc = new StringBuilder();

        if (beacons > 0) {
            desc.append("§6").append(formatNumber(beacons)).append(" Beacons");
        }

        if (jobXp > 0) {
            if (desc.length() > 0) desc.append("§7, ");
            desc.append("§d+").append(jobXp).append(" XP Métier");
        }

        if (voucherType != null && voucherTier > 0) {
            if (desc.length() > 0) desc.append("§7, ");
            desc.append("§bVoucher ").append(voucherType.name()).append(" T").append(voucherTier);
        }

        if (boostType != null && boostMinutes > 0) {
            if (desc.length() > 0) desc.append("§7, ");
            desc.append("§aBoost ").append(boostType.name())
                    .append(" ").append(String.format("%.0f", boostPercent))
                    .append("% (").append(boostMinutes).append("min)");
        }

        return desc.toString();
    }

    /**
     * Vérifie si cette récompense a des éléments bonus (voucher ou boost)
     */
    public boolean hasBonusRewards() {
        return (voucherType != null && voucherTier > 0) ||
                (boostType != null && boostMinutes > 0 && boostPercent > 0);
    }

    /**
     * Calcule la "valeur" approximative de ces récompenses pour comparaison
     */
    public long getApproximateValue() {
        long totalValue = beacons;

        // Ajoute une valeur approximative pour l'XP métier (1 XP = 10 beacons)
        totalValue += jobXp * 10L;

        // Ajoute une valeur approximative pour les vouchers selon le tier
        if (voucherType != null && voucherTier > 0) {
            totalValue += voucherTier * 1000L; // Valeur arbitraire selon le tier
        }

        // Ajoute une valeur approximative pour les boosts selon durée et pourcentage
        if (boostType != null && boostMinutes > 0 && boostPercent > 0) {
            totalValue += (long) (boostMinutes * boostPercent * 10); // Valeur arbitraire
        }

        return totalValue;
    }

    // ================================================================================================
    // GETTERS PRINCIPAUX
    // ================================================================================================

    /**
     * @return Le nombre de beacons à donner
     */
    public long getBeacons() {
        return beacons;
    }

    /**
     * @return Le nombre d'XP métier à donner
     */
    public int getJobXp() {
        return jobXp;
    }

    // ================================================================================================
    // GETTERS VOUCHER (MÉTHODES MANQUANTES)
    // ================================================================================================

    /**
     * @return Le type de voucher à donner, ou null si aucun
     */
    public VoucherType getVoucherType() {
        return voucherType;
    }

    /**
     * @return Le tier du voucher à donner (1-10)
     */
    public int getVoucherTier() {
        return voucherTier;
    }

    /**
     * @return true si cette récompense inclut un voucher
     */
    public boolean hasVoucher() {
        return voucherType != null && voucherTier > 0;
    }

    // ================================================================================================
    // GETTERS BOOST (MÉTHODES MANQUANTES)
    // ================================================================================================

    /**
     * @return Le type de boost à donner, ou null si aucun
     */
    public BoostType getBoostType() {
        return boostType;
    }

    /**
     * @return La durée du boost en minutes
     */
    public int getBoostMinutes() {
        return boostMinutes;
    }

    /**
     * @return Le pourcentage de bonus du boost
     */
    public double getBoostPercent() {
        return boostPercent;
    }

    /**
     * @return true si cette récompense inclut un boost
     */
    public boolean hasBoost() {
        return boostType != null && boostMinutes > 0 && boostPercent > 0;
    }

    // ================================================================================================
    // MÉTHODES UTILITAIRES
    // ================================================================================================

    /**
     * Formate un nombre avec des unités (K, M, G)
     */
    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fG", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    // ================================================================================================
    // BUILDER PATTERN (OPTIONNEL POUR FACILITER LA CRÉATION)
    // ================================================================================================

    /**
     * Builder pour créer facilement des QuestRewards complexes
     */
    public static class Builder {
        private long beacons = 0;
        private int jobXp = 0;
        private VoucherType voucherType = null;
        private int voucherTier = 0;
        private BoostType boostType = null;
        private int boostMinutes = 0;
        private double boostPercent = 0.0;

        public Builder beacons(long beacons) {
            this.beacons = beacons;
            return this;
        }

        public Builder jobXp(int jobXp) {
            this.jobXp = jobXp;
            return this;
        }

        public Builder voucher(VoucherType type, int tier) {
            this.voucherType = type;
            this.voucherTier = tier;
            return this;
        }

        public Builder boost(BoostType type, int minutes, double percent) {
            this.boostType = type;
            this.boostMinutes = minutes;
            this.boostPercent = percent;
            return this;
        }

        public QuestRewards build() {
            return new QuestRewards(beacons, jobXp, voucherType, voucherTier,
                    boostType, boostMinutes, boostPercent);
        }
    }

    /**
     * Créé un builder pour construire des récompenses
     */
    public static Builder builder() {
        return new Builder();
    }

    // ================================================================================================
    // MÉTHODES DE COMMODITÉ STATIQUES
    // ================================================================================================

    /**
     * Crée des récompenses basiques avec seulement beacons et XP
     */
    public static QuestRewards basic(long beacons, int jobXp) {
        return new QuestRewards(beacons, jobXp);
    }

    /**
     * Crée des récompenses avec voucher
     */
    public static QuestRewards withVoucher(long beacons, int jobXp, VoucherType voucherType, int voucherTier) {
        return new QuestRewards(beacons, jobXp, voucherType, voucherTier);
    }

    /**
     * Crée des récompenses avec boost
     */
    public static QuestRewards withBoost(long beacons, int jobXp, BoostType boostType, int minutes, double percent) {
        return new QuestRewards(beacons, jobXp, boostType, minutes, percent);
    }

    // ================================================================================================
    // OVERRIDE POUR DÉBOGAGE
    // ================================================================================================

    @Override
    public String toString() {
        return "QuestRewards{" +
                "beacons=" + beacons +
                ", jobXp=" + jobXp +
                ", voucherType=" + voucherType +
                ", voucherTier=" + voucherTier +
                ", boostType=" + boostType +
                ", boostMinutes=" + boostMinutes +
                ", boostPercent=" + boostPercent +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        QuestRewards that = (QuestRewards) obj;

        return beacons == that.beacons &&
                jobXp == that.jobXp &&
                voucherTier == that.voucherTier &&
                boostMinutes == that.boostMinutes &&
                Double.compare(that.boostPercent, boostPercent) == 0 &&
                voucherType == that.voucherType &&
                boostType == that.boostType;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(beacons, jobXp, voucherType, voucherTier,
                boostType, boostMinutes, boostPercent);
    }
}