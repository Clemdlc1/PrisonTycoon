package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.cristaux.CristalType;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire global des bonus pour tous les enchantements
 * Centralise le calcul des bonus venant de différentes sources (cristaux, futurs bonus, etc.)
 */
public class GlobalBonusManager {

    private final PrisonTycoon plugin;

    public GlobalBonusManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Calcule le bonus total pour un type donné EN INCLUANT LES TALENTS DE MÉTIERS
     * MODIFICATION de la méthode existante
     */
    public double getTotalBonusMultiplier(Player player, BonusType bonusType) {
        double totalBonus = 0.0;

        // 1. Bonus des cristaux (système existant)
        if (bonusType.getAssociatedCristal() != null) {
            totalBonus += plugin.getCristalManager().getTotalCristalBonus(player, bonusType.getAssociatedCristal());
        }

        // 2. NOUVEAU: Bonus des talents de métiers
        totalBonus += getProfessionTalentBonus(player, bonusType);

        // 3. Futurs bonus (VIP, événements, etc.)
        // ...

        return 1.0 + (totalBonus / 100.0); // Convertit pourcentage en multiplicateur
    }

    /**
     * Applique le bonus TokenGreed sur une valeur de base
     */
    public long applyTokenGreedBonus(Player player, long baseTokens) {
        if (baseTokens <= 0) return baseTokens;

        double multiplier = getTotalBonusMultiplier(player, BonusType.TOKEN_GREED);
        return Math.round(baseTokens * multiplier);
    }

    /**
     * Applique le bonus MoneyGreed sur une valeur de base
     */
    public long applyMoneyGreedBonus(Player player, long baseMoney) {
        if (baseMoney <= 0) return baseMoney;

        double multiplier = getTotalBonusMultiplier(player, BonusType.MONEY_GREED);
        return Math.round(baseMoney * multiplier);
    }

    /**
     * Applique le bonus ExpGreed sur une valeur de base
     */
    public long applyExpGreedBonus(Player player, long baseExp) {
        if (baseExp <= 0) return baseExp;

        double multiplier = getTotalBonusMultiplier(player, BonusType.EXP_GREED);
        return Math.round(baseExp * multiplier);
    }

    /**
     * Applique le bonus SellBonus sur un prix de vente
     */
    public double applySellBonus(Player player, double basePrice) {
        if (basePrice <= 0) return basePrice;

        double multiplier = getTotalBonusMultiplier(player, BonusType.SELL_BONUS) + getTotalBonusMultiplier(player, BonusType.SELL_BOOST);
        return basePrice * multiplier;
    }

    /**
     * Applique le bonus MineralGreed (Fortune) sur une valeur
     */
    public int applyMineralGreedBonus(Player player, int baseFortune) {
        if (baseFortune <= 0) return baseFortune;

        double multiplier = getTotalBonusMultiplier(player, BonusType.MINERAL_GREED);
        return Math.round(baseFortune * (float) multiplier);
    }

    /**
     * Obtient les détails de tous les bonus actifs pour un joueur
     */
    public Map<BonusType, Double> getActiveBonuses(Player player) {
        Map<BonusType, Double> bonuses = new HashMap<>();

        for (BonusType bonusType : BonusType.values()) {
            double multiplier = getTotalBonusMultiplier(player, bonusType);
            if (multiplier > 1.0) {
                bonuses.put(bonusType, multiplier);
            }
        }

        return bonuses;
    }

    /**
     * NOUVELLE MÉTHODE: Calcule le bonus des talents de métiers pour un type donné
     */
    private double getProfessionTalentBonus(Player player, BonusType bonusType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null) {
            return 0.0;
        }

        double bonus = 0.0;

        // Bonus selon le métier actif et le type de bonus
        switch (activeProfession) {
            case "mineur" -> {
                switch (bonusType) {
                    case EXP_GREED -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "exp_greed");
                        if (talentLevel > 0) {
                            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("mineur");
                            ProfessionManager.ProfessionTalent talent = profession.getTalent("exp_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    case TOKEN_GREED -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "token_greed");
                        if (talentLevel > 0) {
                            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("mineur");
                            ProfessionManager.ProfessionTalent talent = profession.getTalent("token_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    case MONEY_GREED -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "money_greed");
                        if (talentLevel > 0) {
                            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("mineur");
                            ProfessionManager.ProfessionTalent talent = profession.getTalent("money_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                }
            }
            case "commercant" -> {
                switch (bonusType) {
                    case SELL_BOOST -> {
                        int talentLevel = playerData.getTalentLevel("commercant", "sell_boost");
                        if (talentLevel > 0) {
                            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("commercant");
                            ProfessionManager.ProfessionTalent talent = profession.getTalent("sell_boost");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    // Négociations et Vitrines sont gérées ailleurs car ce ne sont pas des multiplicateurs
                }
            }
            case "guerrier" -> {
                switch (bonusType) {
                    case BEACON_MULTIPLIER -> {
                        int talentLevel = playerData.getTalentLevel("guerrier", "beacon_multiplier");
                        if (talentLevel > 0) {
                            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("guerrier");
                            ProfessionManager.ProfessionTalent talent = profession.getTalent("beacon_multiplier");
                            int multiplierValue = talent.getValueAtLevel(talentLevel);
                            // Convertit le multiplicateur en pourcentage de bonus
                            bonus += (multiplierValue - 1) * 100; // x2 devient +100%, x3 devient +200%
                        }
                    }
                }
            }
        }

        return bonus;
    }

    /**
     * NOUVELLE MÉTHODE: Obtient le bonus de négociations (générateurs) pour les commerçants
     */
    public double getNegotiationsBonus(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (!"commercant".equals(activeProfession)) {
            return 0.0;
        }

        int talentLevel = playerData.getTalentLevel("commercant", "negotiations");
        if (talentLevel > 0) {
            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("commercant");
            ProfessionManager.ProfessionTalent talent = profession.getTalent("negotiations");
            return talent.getValueAtLevel(talentLevel);
        }

        return 0.0;
    }

    /**
     * NOUVELLE MÉTHODE: Obtient le nombre de vitrines supplémentaires pour les commerçants
     */
    public int getExtraShopSlots(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (!"commercant".equals(activeProfession)) {
            return 0;
        }

        int talentLevel = playerData.getTalentLevel("commercant", "vitrines_sup");
        if (talentLevel > 0) {
            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("commercant");
            ProfessionManager.ProfessionTalent talent = profession.getTalent("vitrines_sup");
            return talent.getValueAtLevel(talentLevel);
        }

        return 0;
    }

    /**
     * NOUVELLE MÉTHODE: Obtient la réduction de prix PvP pour les guerriers
     */
    public double getPvPDiscountBonus(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (!"guerrier".equals(activeProfession)) {
            return 0.0;
        }

        int talentLevel = playerData.getTalentLevel("guerrier", "soldes");
        if (talentLevel > 0) {
            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("guerrier");
            ProfessionManager.ProfessionTalent talent = profession.getTalent("soldes");
            return talent.getValueAtLevel(talentLevel);
        }

        return 0.0;
    }

    /**
     * NOUVELLE MÉTHODE: Obtient le bonus d'avant-poste pour les guerriers
     */
    public double getOutpostBonus(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (!"guerrier".equals(activeProfession)) {
            return 0.0;
        }

        int talentLevel = playerData.getTalentLevel("guerrier", "garde");
        if (talentLevel > 0) {
            ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession("guerrier");
            ProfessionManager.ProfessionTalent talent = profession.getTalent("garde");
            return talent.getValueAtLevel(talentLevel);
        }

        return 0.0;
    }

    /**
     * Obtient le pourcentage de bonus pour un type donné
     */
    public double getBonusPercentage(Player player, BonusType bonusType) {
        double multiplier = getTotalBonusMultiplier(player, bonusType);
        return (multiplier - 1.0) * 100.0;
    }

    /**
     * Méthode pour gérer les futurs types de bonus
     * (pets, équipements, événements temporaires, etc.)
     */
    private double getFutureBonuses(Player player, BonusType bonusType) {
        double totalFutureBonus = 0.0;

        return totalFutureBonus;
    }

    /**
     * Méthode utilitaire pour les enchantements spéciaux qui ont leurs propres calculs
     */
    public int getAbondanceDuration(Player player, int baseDuration) {
        // Délègue au système de cristaux existant pour maintenir la compatibilité
        return plugin.getCristalBonusHelper().getAbondanceDuration(player, baseDuration);
    }

    /**
     * Méthode utilitaire pour l'efficacité de combustion
     */
    public double getCombustionEfficiency(Player player, double baseEfficiency) {
        // Délègue au système de cristaux existant pour maintenir la compatibilité
        return plugin.getCristalBonusHelper().applyCombustionEfficiency(player, baseEfficiency);
    }

    /**
     * Debug: Affiche tous les bonus actifs pour un joueur
     */
    public void debugBonuses(Player player) {
        plugin.getPluginLogger().info("=== Bonus Debug pour " + player.getName() + " ===");

        Map<BonusType, Double> activeBonuses = getActiveBonuses(player);

        if (activeBonuses.isEmpty()) {
            plugin.getPluginLogger().info("Aucun bonus actif");
        } else {
            for (Map.Entry<BonusType, Double> entry : activeBonuses.entrySet()) {
                double percentage = (entry.getValue() - 1.0) * 100.0;
                plugin.getPluginLogger().info(entry.getKey().getDisplayName() + ": +" +
                        String.format("%.1f", percentage) + "% (x" +
                        String.format("%.3f", entry.getValue()) + ")");
            }
        }
    }

    // Types de bonus gérés
    public enum BonusType {
        // Types existants (ne pas modifier)
        TOKEN_GREED("TokenGreed", CristalType.TOKEN_BOOST),
        MONEY_GREED("MoneyGreed", CristalType.MONEY_BOOST),
        EXP_GREED("ExpGreed", CristalType.XP_BOOST),
        SELL_BONUS("SellBonus", CristalType.SELL_BOOST),
        MINERAL_GREED("MineralGreed", CristalType.MINERAL_GREED),

        // NOUVEAUX types pour les métiers (avec null car pas de cristal associé)
        SELL_BOOST("SellBoost", null),
        BEACON_MULTIPLIER("BeaconMultiplier", null);

        private final String displayName;
        private final CristalType associatedCristal;

        BonusType(String displayName, CristalType associatedCristal) {
            this.displayName = displayName;
            this.associatedCristal = associatedCristal;
        }

        public String getDisplayName() {
            return displayName;
        }

        public CristalType getAssociatedCristal() {
            return associatedCristal;
        }
    }
}