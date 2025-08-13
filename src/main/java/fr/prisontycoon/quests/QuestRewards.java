package fr.prisontycoon.quests;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Récompenses d'une quête (beacons, xp métier actif, vouchers/boosters).
 * VERSION COMPLÈTE avec tous les getters nécessaires
 */
public class QuestRewards {
    private final long beacons;
    private final int jobXp;
    private VoucherType voucherType; // optionnel
    private int voucherTier;         // optionnel
    private BoostType boostType;     // optionnel
    private int boostMinutes;        // optionnel
    private double boostPercent;     // optionnel
    private int essenceFragments;
    private long tokens;
    private Map<String, Integer> crateKeys;

    // Constructeur principal simple pour beacons + jobXp
    public QuestRewards(long beacons, int jobXp) {
        this.beacons = Math.max(0, beacons);
        this.jobXp = Math.max(0, jobXp);
        this.voucherType = null;
        this.voucherTier = 0;
        this.boostType = null;
        this.boostMinutes = 0;
        this.boostPercent = 0.0;
        this.essenceFragments = 0;
            this.tokens = 0;
            this.crateKeys = new HashMap<>();
    }

    // Constructeur pratique avec voucher
    public QuestRewards(long beacons, int jobXp, VoucherType voucherType, int voucherTier) {
        this(beacons, jobXp);
        this.voucherType = voucherType;
        this.voucherTier = Math.max(1, voucherTier);
    }

    // Constructeur pratique avec boost
    public QuestRewards(long beacons, int jobXp, BoostType boostType, int boostMinutes, double boostPercent) {
        this(beacons, jobXp);
        this.boostType = boostType;
        this.boostMinutes = Math.max(1, boostMinutes);
        this.boostPercent = Math.max(1.0, boostPercent);
    }

    // Constructeur pratique avec fragments d'essence
    public QuestRewards(long beacons, int jobXp, int essenceFragments) {
        this(beacons, jobXp);
        this.essenceFragments = Math.max(0, essenceFragments);
    }

    /**
     * Créé un builder pour construire des récompenses
     */
    public static Builder builder() {
        return new Builder();
    }

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
    // GETTERS PRINCIPAUX
    // ================================================================================================

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

        // Fragments de forge: quêtes → Essence seulement
        if (essenceFragments > 0) {
            var it = plugin.getForgeManager().createFragment(fr.prisontycoon.managers.ForgeManager.FragmentType.ESSENCE, essenceFragments);
            if (!plugin.getContainerManager().addItemToContainers(player, it)) player.getInventory().addItem(it);
        }

        // Ajoute des tokens
        if (tokens > 0) {
            plugin.getEconomyManager().addTokens(player, tokens);
        }

        // Don des clés de crate
        if (crateKeys != null && !crateKeys.isEmpty()) {
            for (Map.Entry<String, Integer> entry : crateKeys.entrySet()) {
                String mappedKeyType = mapKeyLabel(entry.getKey());
                int remaining = Math.max(0, entry.getValue());
                while (remaining > 0) {
                    int give = Math.min(remaining, 64);
                    ItemStack key = plugin.getEnchantmentManager().createKey(mappedKeyType);
                    key.setAmount(give);
                    boolean added = plugin.getContainerManager().addItemToContainers(player, key);
                    if (!added) {
                        player.getInventory().addItem(key);
                    }
                    remaining -= give;
                }
                data.addKeyObtained();
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
            if (!desc.isEmpty()) desc.append("§7, ");
            desc.append("§d+").append(jobXp).append(" XP Métier");
        }

        if (tokens > 0) {
            if (!desc.isEmpty()) desc.append("§7, ");
            desc.append("§e").append(formatNumber(tokens)).append(" Tokens");
        }

        if (voucherType != null && voucherTier > 0) {
            if (!desc.isEmpty()) desc.append("§7, ");
            desc.append("§bVoucher ").append(voucherType.name()).append(" T").append(voucherTier);
        }

        if (boostType != null && boostMinutes > 0) {
            if (!desc.isEmpty()) desc.append("§7, ");
            desc.append("§aBoost ").append(boostType.name())
                    .append(" ").append(String.format("%.0f", boostPercent))
                    .append("% (").append(boostMinutes).append("min)");
        }

        if (essenceFragments > 0) {
            if (!desc.isEmpty()) desc.append("§7, ");
            desc.append("§bFragments Essence x").append(essenceFragments);
        }

        if (crateKeys != null && !crateKeys.isEmpty()) {
            if (!desc.isEmpty()) desc.append("§7, ");
            desc.append("§dClés: ");
            boolean first = true;
            for (Map.Entry<String, Integer> e : crateKeys.entrySet()) {
                if (!first) desc.append("§7, ");
                first = false;
                desc.append("§f").append(mapKeyLabel(e.getKey())).append(" x").append(e.getValue());
            }
        }

        return desc.toString();
    }

    // ================================================================================================
    // GETTERS VOUCHER (MÉTHODES MANQUANTES)
    // ================================================================================================

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

        // Valeur approximative des tokens (1 token = 1)
        if (tokens > 0) {
            totalValue += tokens;
        }

        // Valeur approximative des clés (par clé = 1000)
        if (crateKeys != null) {
            for (Integer count : crateKeys.values()) {
                if (count != null && count > 0) totalValue += count * 1000L;
            }
        }

        return totalValue;
    }

    /**
     * @return Le nombre de beacons à donner
     */
    public long getBeacons() {
        return beacons;
    }

    // ================================================================================================
    // GETTERS BOOST (MÉTHODES MANQUANTES)
    // ================================================================================================

    /**
     * @return Le nombre d'XP métier à donner
     */
    public int getJobXp() {
        return jobXp;
    }

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

    /**
     * @return Le nombre de tokens
     */
    public long getTokens() {
        return tokens;
    }

    /**
     * @return Les clés de crates à donner (type -> quantité)
     */
    public Map<String, Integer> getCrateKeys() {
        return crateKeys != null ? new HashMap<>(crateKeys) : Map.of();
    }

    // ================================================================================================
    // MÉTHODES UTILITAIRES
    // ================================================================================================

    /**
     * @return Le type de boost à donner, ou null si aucun
     */
    public BoostType getBoostType() {
        return boostType;
    }

    // ================================================================================================
    // BUILDER PATTERN (OPTIONNEL POUR FACILITER LA CRÉATION)
    // ================================================================================================

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

    // ================================================================================================
    // MÉTHODES DE COMMODITÉ STATIQUES
    // ================================================================================================

    /**
     * @return true si cette récompense inclut un boost
     */
    public boolean hasBoost() {
        return boostType != null && boostMinutes > 0 && boostPercent > 0;
    }

    // ================================================================================================
    // UTILITAIRES INTERNES
    // ================================================================================================

    private static String mapKeyLabel(String label) {
        if (label == null) return "Commune";
        String l = label.trim().toLowerCase();
        return switch (l) {
            case "common", "commune" -> "Commune";
            case "uncommon", "peu_commune", "peu commune", "peu-commune" -> "Peu Commune";
            case "rare" -> "Rare";
            case "epic", "epique", "épique" -> "Rare"; // approximation
            case "legendary", "legendaire", "légendaire" -> "Légendaire";
            case "cristal", "crystal" -> "Cristal";
            default -> Character.toUpperCase(l.charAt(0)) + l.substring(1);
        };
    }

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
                ", essenceFragments=" + essenceFragments +
                ", tokens=" + tokens +
                ", crateKeys=" + crateKeys +
                '}';
    }

    // ================================================================================================
    // OVERRIDE POUR DÉBOGAGE
    // ================================================================================================

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
                boostType, boostMinutes, boostPercent, essenceFragments);
    }

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
        private int essenceFragments = 0;
        private long tokens = 0;
        private Map<String, Integer> crateKeys = new HashMap<>();

        public Builder beacons(long beacons) {
            this.beacons = beacons;
            return this;
        }

        public Builder jobXp(int jobXp) {
            this.jobXp = jobXp;
            return this;
        }

        public Builder tokens(long tokens) {
            this.tokens = Math.max(0, tokens);
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

        public Builder essence(int essence) {
            this.essenceFragments = essence;
            return this;
        }

        public Builder keys(Map<String, Integer> keys) {
            if (keys != null) {
                for (Map.Entry<String, Integer> e : keys.entrySet()) {
                    if (e.getValue() != null && e.getValue() > 0) {
                        this.crateKeys.merge(e.getKey(), e.getValue(), Integer::sum);
                    }
                }
            }
            return this;
        }

        public QuestRewards build() {
            QuestRewards r = new QuestRewards(beacons, jobXp);
            if (voucherType != null && voucherTier > 0) {
                r.voucherType = voucherType;
                r.voucherTier = Math.max(1, voucherTier);
            }
            if (boostType != null && boostMinutes > 0 && boostPercent > 0) {
                r.boostType = boostType;
                r.boostMinutes = Math.max(1, boostMinutes);
                r.boostPercent = Math.max(1.0, boostPercent);
            }
            if (essenceFragments > 0) {
                r.essenceFragments = Math.max(0, essenceFragments);
            }
            if (tokens > 0) {
                r.tokens = tokens;
            }
            if (crateKeys != null && !crateKeys.isEmpty()) {
                r.crateKeys = new HashMap<>(crateKeys);
            }
            return r;
        }
    }
}