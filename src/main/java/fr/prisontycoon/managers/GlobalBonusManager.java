package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.cristaux.CristalType;
import fr.prisontycoon.data.Gang;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.gangs.GangBoostType;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Gestionnaire unifié des bonus (cristaux, talents, boosts)
 * Version refactorisée avec types de bonus simplifiés
 */
public class GlobalBonusManager {

    private static final long BONUS_CACHE_TTL_MS = 2_000L;
    private final PrisonTycoon plugin;
    private final Map<UUID, Map<BonusCategory, CachedMultiplier>> bonusCache = new HashMap<>();

    public GlobalBonusManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * MÉTHODE PRINCIPALE : Calcule le multiplicateur total pour une catégorie de bonus
     */
    public double getTotalBonusMultiplier(Player player, BonusCategory category) {
        UUID pid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Map<BonusCategory, CachedMultiplier> perPlayer = bonusCache.get(pid);
        if (perPlayer != null) {
            CachedMultiplier cached = perPlayer.get(category);
            if (cached != null && (now - cached.timestampMs) <= BONUS_CACHE_TTL_MS) {
                return cached.multiplier;
            }
        }

        BonusSourceDetails details = getBonusSourcesDetails(player, category);
        double mult = details.getTotalMultiplier();
        if (perPlayer == null) {
            perPlayer = new HashMap<>();
            bonusCache.put(pid, perPlayer);
        }
        perPlayer.put(category, new CachedMultiplier(mult, now));
        return mult;
    }

    /**
     * MÉTHODE UTILITAIRE : Obtient les détails complets des sources d'un bonus
     * Cette méthode simplifie l'affichage dans BoostGUI
     */
    public BonusSourceDetails getBonusSourcesDetails(Player player, BonusCategory category) {
        BonusSourceDetails details = new BonusSourceDetails();

        // 1. Bonus des cristaux
        double cristalBonus = getCristalBonus(player, category);
        details.setCristalBonus(cristalBonus);
        if (cristalBonus > 0) {
            details.addDetailedSource("Cristaux " + getCristalTypeName(category), cristalBonus);
        }

        // 2. Bonus armures forge (nouveau)
        double armorBonus = getArmorBonus(player, category);
        details.setArmorBonus(armorBonus);
        if (armorBonus > 0) {
            details.addDetailedSource("Armure Forge", armorBonus);
        }

        // 3. Bonus des talents de métiers
        double professionBonus = getProfessionTalentBonus(player, category);
        details.setProfessionBonus(professionBonus);
        if (professionBonus > 0) {
            String activeProfession = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getActiveProfession();
            details.addDetailedSource("Talent " + (activeProfession != null ? activeProfession : "métier"), professionBonus);
        }

        // 4. Bonus des talents de prestige
        double prestigeBonus = getPrestigeTalentBonus(player, category);
        details.setPrestigeBonus(prestigeBonus);
        if (prestigeBonus > 0) {
            details.addDetailedSource("Talent Prestige", prestigeBonus);
        }

        // 5. Bonus des boosts temporaires (joueur)
        double boostBonus = getTemporaryBoostBonus(player, category);
        details.setTemporaryBoostBonus(boostBonus);
        if (boostBonus > 0) {
            details.addDetailedSource("Boosts Personnels", boostBonus);
        }

        // 6. Bonus permanents du gang (niveaux, talents)
        double gangBonus = getGangBonus(player, category);
        details.setGangBonus(gangBonus);
        if (gangBonus > 0) {
            Gang gang = getPlayerGang(player);
            String gangName = gang != null ? gang.getName() : "Gang";
            details.addDetailedSource("Gang " + gangName, gangBonus);
        }

        // 7. Bonus du boost de gang (temporaire)
        double temporaryGangBoost = getTemporaryGangBoostBonus(player, category);
        details.setTemporaryGangBoostBonus(temporaryGangBoost);
        if (temporaryGangBoost > 0) {
            details.addDetailedSource("Boost de Gang (Temporaire)", temporaryGangBoost);
        }

        double enchantmentBonus = getEnchantmentBonus(player, category);
        details.setEnchantmentBonus(enchantmentBonus);
        if (enchantmentBonus > 0) {
            details.addDetailedSource("Enchantements", enchantmentBonus);
        }

        double overloadBonus = getMineOverloadBonus(player, category);
        details.setOverloadBonus(overloadBonus);
        if (overloadBonus > 0) {
            details.addDetailedSource("Surcharge de Mine", overloadBonus);
        }

        // 8. Type de banque (bonus/malus)
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data != null && data.getBankType() != null) {
            if (category == BonusCategory.SELL_BONUS) {
                double sellMult = data.getBankType().getSellMultiplier();
                if (sellMult != 1.0) {
                    double sellPct = (sellMult - 1.0) * 100.0;
                    details.setBankBonus(details.getBankBonus() + sellPct);
                    details.addDetailedSource("Type de Banque", sellPct);
                }
            }
        }

        return details;
    }

    /**
     * Obtient tous les bonus actifs d'un joueur
     */
    public Map<BonusCategory, BonusSourceDetails> getAllActiveBonuses(Player player) {
        Map<BonusCategory, BonusSourceDetails> bonuses = new HashMap<>();

        for (BonusCategory category : BonusCategory.values()) {
            BonusSourceDetails details = getBonusSourcesDetails(player, category);
            if (details.getTotalBonus() > 0) {
                bonuses.put(category, details);
            }
        }

        return bonuses;
    }

    /**
     * Applique le bonus Token sur une valeur de base
     */
    public long applyTokenBonus(Player player, long baseTokens) {
        if (baseTokens <= 0) return baseTokens;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.TOKEN_BONUS);
        return Math.round(baseTokens * multiplier);
    }

    /**
     * Applique le bonus Money sur une valeur de base
     */
    public long applyMoneyBonus(Player player, long baseMoney) {
        if (baseMoney <= 0) return baseMoney;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.MONEY_BONUS);
        return Math.round(baseMoney * multiplier);
    }

    /**
     * Applique le bonus Experience sur une valeur de base
     */
    public long applyExperienceBonus(Player player, long baseExp) {
        if (baseExp <= 0) return baseExp;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.EXPERIENCE_BONUS);
        return Math.round(baseExp * multiplier);
    }


    // ========================================
    // MÉTHODES D'APPLICATION DES BONUS
    // ========================================

    /**
     * Applique le bonus Sell sur un prix de vente
     */
    public double applySellBonus(Player player, double basePrice) {
        if (basePrice <= 0) return basePrice;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.SELL_BONUS);
        return basePrice * multiplier;
    }

    /**
     * Applique le bonus Fortune sur une valeur
     */
    public int applyFortuneBonus(Player player, int baseFortune) {
        if (baseFortune <= 0) return baseFortune;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.FORTUNE_BONUS);
        return Math.round(baseFortune * (float) multiplier);
    }

    /**
     * Applique le bonus Job XP sur les gains d'XP métier
     */
    public long applyJobXPBonus(Player player, long baseJobXP) {
        if (baseJobXP <= 0) return baseJobXP;
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.JOB_XP_BONUS);
        return Math.round(baseJobXP * multiplier);
    }

    /**
     * Applique le multiplicateur de beacons
     */
    public double applyBeaconMultiplier(Player player, double baseEfficiency) {
        double multiplier = getTotalBonusMultiplier(player, BonusCategory.BEACON_MULTIPLIER);
        return baseEfficiency * multiplier;
    }

    /**
     * Calcule le bonus des cristaux pour une catégorie
     */
    private double getCristalBonus(Player player, BonusCategory category) {
        if (plugin.getCristalManager() == null) return 0.0;

        CristalType cristalType = getCristalTypeForCategory(category);
        if (cristalType == null) return 0.0;

        return plugin.getCristalManager().getTotalCristalBonus(player, cristalType);
    }

    // ========================================
    // MÉTHODES PRIVÉES DE CALCUL DES SOURCES
    // ========================================

    /**
     * Calcule le bonus des talents de métiers pour une catégorie
     */
    private double getProfessionTalentBonus(Player player, BonusCategory category) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null) return 0.0;

        double bonus = 0.0;

        switch (activeProfession) {
            case "mineur" -> {
                switch (category) {
                    case TOKEN_BONUS -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "token_greed");
                        if (talentLevel > 0) {
                            var profession = plugin.getProfessionManager().getProfession("mineur");
                            var talent = profession.getTalent("token_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    case EXPERIENCE_BONUS -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "exp_greed");
                        if (talentLevel > 0) {
                            var profession = plugin.getProfessionManager().getProfession("mineur");
                            var talent = profession.getTalent("exp_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    case MONEY_BONUS -> {
                        int talentLevel = playerData.getTalentLevel("mineur", "money_greed");
                        if (talentLevel > 0) {
                            var profession = plugin.getProfessionManager().getProfession("mineur");
                            var talent = profession.getTalent("money_greed");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    default -> {
                    }
                }
            }
            case "commercant" -> {
                switch (category) {
                    case SELL_BONUS -> {
                        int talentLevel = playerData.getTalentLevel("commercant", "sell_boost");
                        if (talentLevel > 0) {
                            var profession = plugin.getProfessionManager().getProfession("commercant");
                            var talent = profession.getTalent("sell_boost");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    case HDV_SLOT -> {
                        int talentLevel = playerData.getTalentLevel("commercant", "vitrines_sup");
                        if (talentLevel > 0) {
                            var profession = plugin.getProfessionManager().getProfession("commercant");
                            var talent = profession.getTalent("vitrines_sup");
                            bonus += talent.getValueAtLevel(talentLevel);
                        }
                    }
                    default -> {
                    }
                }
            }
            case "guerrier" -> {
                if (category == BonusCategory.BEACON_MULTIPLIER) {
                    int talentLevel = playerData.getTalentLevel("guerrier", "beacon_multiplier");
                    if (talentLevel > 0) {
                        var profession = plugin.getProfessionManager().getProfession("guerrier");
                        var talent = profession.getTalent("beacon_multiplier");
                        int multiplierValue = talent.getValueAtLevel(talentLevel);
                        bonus += (multiplierValue - 1) * 100; // x2 devient +100%
                    }
                }
            }
            default -> {
            }
        }

        return bonus;
    }

    /**
     * Calcule le bonus des talents de prestige pour une catégorie
     */
    private double getPrestigeTalentBonus(Player player, BonusCategory category) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        Map<PrestigeTalent, Integer> talents = playerData.getPrestigeTalents();
        double bonus = 0.0;

        switch (category) {
            case MONEY_BONUS -> {
                int level = talents.getOrDefault(PrestigeTalent.MONEY_GREED_BONUS, 0);
                bonus = level * 3.0; // +3% par niveau
            }
            case TOKEN_BONUS -> {
                int level = talents.getOrDefault(PrestigeTalent.TOKEN_GREED_BONUS, 0);
                bonus = level * 3.0; // +3% par niveau
            }
            case SELL_BONUS -> {
                int level = talents.getOrDefault(PrestigeTalent.SELL_PRICE_BONUS, 0);
                bonus = level * 3.0; // +3% par niveau
            }
            case OUTPOST_BONUS -> {
                int level = talents.getOrDefault(PrestigeTalent.OUTPOST_BONUS, 0);
                bonus = level * 3.0; // +3% par niveau
            }
            case TAX_REDUCTION -> {
                int level = talents.getOrDefault(PrestigeTalent.TAX_REDUCTION, 0);
                bonus = level * 1.0; // -1% par niveau (réduction)
            }
            case PVP_MERCHANT_REDUCTION -> {
                int level = talents.getOrDefault(PrestigeTalent.PVP_MERCHANT_REDUCTION, 0);
                bonus = level * 1.0; // -1% par niveau (réduction)
            }
            case EXPERIENCE_BONUS, FORTUNE_BONUS, HDV_SLOT, BEACON_MULTIPLIER, GANG_BONUS, JOB_XP_BONUS -> {
                // Pas de talents de prestige définis pour ces catégories
                bonus = 0.0;
            }
        }

        return bonus;
    }

    /**
     * Calcule le bonus des boosts temporaires (joueur & admin) pour une catégorie
     */
    private double getTemporaryBoostBonus(Player player, BonusCategory category) {
        if (plugin.getBoostManager() == null) return 0.0;

        BoostType boostType = getBoostTypeForCategory(category);
        if (boostType == null) return 0.0;

        // Cette méthode prend déjà en compte les boosts admin globaux
        return plugin.getBoostManager().getTotalBoostBonus(player, boostType);
    }

    /**
     * Calcule le bonus des boosts de gang temporaires pour une catégorie
     */
    private double getTemporaryGangBoostBonus(Player player, BonusCategory category) {
        if (plugin.getBoostManager() == null) return 0.0;

        Gang gang = getPlayerGang(player);
        if (gang == null) return 0.0;

        GangBoostType gangBoostType = getGangBoostTypeForCategory(category);
        if (gangBoostType == null) return 0.0;

        double multiplier = plugin.getBoostManager().getGangBoostMultiplier(gang.getId(), gangBoostType);

        if (multiplier > 1.0) {
            return (multiplier - 1.0) * 100.0; // Convertit le multiplicateur en bonus en pourcentage (ex: 1.5 -> +50%)
        }

        return 0.0;
    }

    /**
     * Calcule le bonus permanent de gang (niveaux, talents) pour une catégorie
     */
    private double getGangBonus(Player player, BonusCategory category) {
        Gang gang = getPlayerGang(player);
        if (gang == null) return 0.0;

        double bonus = 0.0;

        // Bonus de niveau de gang
        switch (category) {
            case SELL_BONUS -> {
                int levelBonus = plugin.getGangManager().getSellBonus(gang.getLevel());
                bonus += levelBonus;
            }
            case BEACON_MULTIPLIER -> {
                double multiplier = plugin.getGangManager().getBeaconMultiplier(gang);
                if (multiplier > 1.0) {
                    bonus += (multiplier - 1.0) * 100; // Convertir en pourcentage
                }
            }
            default -> {
            }
        }

        // Bonus des talents de gang
        for (Map.Entry<String, Integer> entry : gang.getTalents().entrySet()) {
            String talentId = entry.getKey();
            if (talentId.startsWith("sell_boost_") && category == BonusCategory.SELL_BONUS) {
                bonus += entry.getValue();
            }
            // Ajouter d'autres talents de gang permanents ici si nécessaire
        }

        return bonus;
    }

    /**
     * NOUVEAU : Calcule le bonus des enchantements pour une catégorie
     */
    private double getEnchantmentBonus(Player player, BonusCategory category) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        double bonus = 0.0;
        if (Objects.requireNonNull(category) == BonusCategory.SELL_BONUS) {
            int sellGreedLevel = playerData.getEnchantmentLevel("sell_greed");
            if (sellGreedLevel > 0) {
                bonus += sellGreedLevel * 0.01; // 0,01% par niveau
            }
        }
        return bonus;
    }

    /**
     * NOUVEAU : Calcule le bonus (en %) provenant de la surcharge de mine pour une catégorie donnée
     */
    private double getMineOverloadBonus(Player player, BonusCategory category) {
        try {
            if (plugin.getMineOverloadManager() == null) return 0.0;
            // Exclut KeyGreed car non mappé ici et non demandé
            return plugin.getMineOverloadManager().getOverloadBonusPercent(player, category);
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * NOUVEAU : Bonus provenant de l'armure de forge équipée
     */
    private double getArmorBonus(Player player, BonusCategory category) {
        try {
            if (plugin.getForgeManager() == null) return 0.0;
            var map = plugin.getForgeManager().getEquippedArmorBonuses(player);
            Double val = map.get(category);
            return val == null ? 0.0 : val;
        } catch (Throwable t) {
            return 0.0;
        }
    }

    /**
     * Mappe une catégorie de bonus vers un type de cristal
     */
    private CristalType getCristalTypeForCategory(BonusCategory category) {
        return switch (category) {
            case TOKEN_BONUS -> CristalType.TOKEN_BOOST;
            case MONEY_BONUS -> CristalType.MONEY_BOOST;
            case EXPERIENCE_BONUS -> CristalType.XP_BOOST;
            case SELL_BONUS -> CristalType.SELL_BOOST;
            case FORTUNE_BONUS -> CristalType.MINERAL_GREED;
            default -> null;
        };
    }

    // ========================================
    // MÉTHODES UTILITAIRES DE MAPPING
    // ========================================

    /**
     * Mappe une catégorie de bonus vers un type de boost
     */
    private BoostType getBoostTypeForCategory(BonusCategory category) {
        return switch (category) {
            case TOKEN_BONUS -> BoostType.TOKEN_GREED;
            case MONEY_BONUS -> BoostType.MONEY_GREED;
            case EXPERIENCE_BONUS -> BoostType.EXP_GREED;
            case SELL_BONUS -> BoostType.SELL_BOOST;
            case FORTUNE_BONUS -> BoostType.MINERAL_GREED;
            case JOB_XP_BONUS -> BoostType.JOB_XP_BOOST;
            default -> null;
        };
    }

    /**
     * Mappe une catégorie de bonus vers un type de boost de gang
     */
    private GangBoostType getGangBoostTypeForCategory(BonusCategory category) {
        return switch (category) {
            case SELL_BONUS -> GangBoostType.VENTE;
            case TOKEN_BONUS -> GangBoostType.TOKEN;
            case EXPERIENCE_BONUS -> GangBoostType.XP;
            case BEACON_MULTIPLIER -> GangBoostType.BEACONS;
            default -> null;
        };
    }

    /**
     * Obtient le nom du type de cristal pour l'affichage
     */
    private String getCristalTypeName(BonusCategory category) {
        CristalType cristalType = getCristalTypeForCategory(category);
        return cristalType != null ? cristalType.name() : "Unknown";
    }

    /**
     * Obtient le gang du joueur
     */
    private Gang getPlayerGang(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getGangId() == null) return null;

        return plugin.getGangManager().getGang(playerData.getGangId());
    }

    /**
     * Debug: Affiche tous les bonus actifs pour un joueur
     */
    public void debugBonuses(Player player) {
        plugin.getPluginLogger().info("=== Bonus Debug pour " + player.getName() + " ===");

        Map<BonusCategory, BonusSourceDetails> activeBonuses = getAllActiveBonuses(player);

        if (activeBonuses.isEmpty()) {
            plugin.getPluginLogger().info("Aucun bonus actif");
        } else {
            for (Map.Entry<BonusCategory, BonusSourceDetails> entry : activeBonuses.entrySet()) {
                BonusCategory category = entry.getKey();
                BonusSourceDetails details = entry.getValue();

                plugin.getPluginLogger().info(category.getDisplayName() + ": ×" +
                        String.format("%.3f", details.getTotalMultiplier()) +
                        " (+" + String.format("%.1f", details.getTotalBonus()) + "%)");

                // Détails des sources
                if (details.getCristalBonus() > 0) {
                    plugin.getPluginLogger().info("  - Cristaux: +" + String.format("%.1f", details.getCristalBonus()) + "%");
                }
                if (details.getProfessionBonus() > 0) {
                    plugin.getPluginLogger().info("  - Métiers: +" + String.format("%.1f", details.getProfessionBonus()) + "%");
                }
                if (details.getPrestigeBonus() > 0) {
                    plugin.getPluginLogger().info("  - Prestige: +" + String.format("%.1f", details.getPrestigeBonus()) + "%");
                }
                if (details.getTemporaryBoostBonus() > 0) {
                    plugin.getPluginLogger().info("  - Boosts Perso: +" + String.format("%.1f", details.getTemporaryBoostBonus()) + "%");
                }
                if (details.getGangBonus() > 0) {
                    plugin.getPluginLogger().info("  - Gang (Perm): +" + String.format("%.1f", details.getGangBonus()) + "%");
                }
                if (details.getTemporaryGangBoostBonus() > 0) {
                    plugin.getPluginLogger().info("  - Gang (Temp): +" + String.format("%.1f", details.getTemporaryGangBoostBonus()) + "%");
                }
            }
        }
    }

    // ========================================
    // MÉTHODES DE DEBUG
    // ========================================

    /**
     * NOUVEAU: Méthode utilitaire unifiée pour obtenir un bonus de prestige spécifique
     */
    public double getPrestigeBonus(Player player, BonusCategory category) {
        return getPrestigeTalentBonus(player, category);
    }

    // ========================================
    // MÉTHODES UTILITAIRES MIGRÉES DE PLAYERDATA
    // ========================================

    /**
     * MIGRÉ: Calcule le bonus Money Greed total du prestige
     */
    public double getPrestigeMoneyGreedBonus(Player player) {
        return getPrestigeBonus(player, BonusCategory.MONEY_BONUS) / 100.0;
    }

    /**
     * MIGRÉ: Calcule le bonus Token Greed total du prestige
     */
    public double getPrestigeTokenGreedBonus(Player player) {
        return getPrestigeBonus(player, BonusCategory.TOKEN_BONUS) / 100.0;
    }

    /**
     * MIGRÉ: Calcule la réduction de taxe du prestige
     */
    public double getPrestigeTaxReduction(Player player) {
        double reduction = getPrestigeBonus(player, BonusCategory.TAX_REDUCTION) / 100.0;
        return Math.min(reduction, 0.99); // Maximum 99% de réduction
    }

    /**
     * MIGRÉ: Calcule le bonus de prix de vente du prestige
     */
    public double getPrestigeSellBonus(Player player) {
        return getPrestigeBonus(player, BonusCategory.SELL_BONUS) / 100.0;
    }

    /**
     * NOUVEAU: Calcule le bonus d'avant-poste du prestige
     */
    public double getPrestigeOutpostBonus(Player player) {
        return getPrestigeBonus(player, BonusCategory.OUTPOST_BONUS) / 100.0;
    }

    /**
     * NOUVEAU: Calcule la réduction du marchand PvP du prestige
     */
    public double getPrestigePvpMerchantReduction(Player player) {
        return getPrestigeBonus(player, BonusCategory.PVP_MERCHANT_REDUCTION) / 100.0;
    }

    /**
     * NOUVEAU: Obtient tous les bonus de prestige d'un joueur
     */
    public Map<String, Double> getAllPrestigeBonuses(Player player) {
        Map<String, Double> bonuses = new HashMap<>();
        bonuses.put("money_greed", getPrestigeMoneyGreedBonus(player));
        bonuses.put("token_greed", getPrestigeTokenGreedBonus(player));
        bonuses.put("tax_reduction", getPrestigeTaxReduction(player));
        bonuses.put("sell_bonus", getPrestigeSellBonus(player));
        bonuses.put("outpost_bonus", getPrestigeOutpostBonus(player));
        bonuses.put("pvp_merchant_reduction", getPrestigePvpMerchantReduction(player));
        return bonuses;
    }

    /**
     * Types de bonus unifiés - Un seul type par finalité
     */
    public enum BonusCategory {
        TOKEN_BONUS("Token Bonus", "💎", "§b", "Augmente les gains de tokens"),
        MONEY_BONUS("Money Bonus", "💰", "§6", "Augmente les gains de coins"),
        EXPERIENCE_BONUS("Experience Bonus", "⭐", "§a", "Augmente les gains d'expérience"),
        SELL_BONUS("Sell Bonus", "💸", "§e", "Augmente les prix de vente"),
        FORTUNE_BONUS("Fortune Bonus", "⛏️", "§9", "Augmente l'effet Fortune"),
        JOB_XP_BONUS("Job XP Bonus", "🔨", "§d", "Augmente les gains d'XP métier"),

        // Bonus spéciaux (sans équivalent boost)
        BEACON_MULTIPLIER("Beacon Multiplier", "🔥", "§c", "Multiplicateur de beacons"),
        TAX_REDUCTION("Tax Reduction", "💳", "§5", "Réduction des taxes"),
        OUTPOST_BONUS("Outpost Bonus", "🏰", "§3", "Bonus des avant-postes"),
        PVP_MERCHANT_REDUCTION("PvP Merchant Reduction", "⚔️", "§4", "Réduction prix marchand PvP"),
        HDV_SLOT("Slot HDV", "", "§8", "Augmentation nombre de slot à l'hotel de ventes"),
        GANG_BONUS("Gang Bonus", "🏰", "§6", "Bonus provenant du gang");

        private final String displayName;
        private final String emoji;
        private final String color;
        private final String description;

        BonusCategory(String displayName, String emoji, String color, String description) {
            this.displayName = displayName;
            this.emoji = emoji;
            this.color = color;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getEmoji() {
            return emoji;
        }

        public String getColor() {
            return color;
        }

        public String getDescription() {
            return description;
        }

        public String getFormattedName() {
            return color + emoji + " " + displayName;
        }
    }

    private record CachedMultiplier(double multiplier, long timestampMs) {
    }

    /**
     * Détails des sources d'un bonus
     */
    public static class BonusSourceDetails {
        private final Map<String, Double> detailedSources = new HashMap<>();
        private double cristalBonus = 0.0;
        private double professionBonus = 0.0;
        private double prestigeBonus = 0.0;
        private double temporaryBoostBonus = 0.0;
        private double gangBonus = 0.0;
        private double temporaryGangBoostBonus = 0.0;
        private double enchantmentBonus = 0.0;
        private double overloadBonus = 0.0;
        private double armorBonus = 0.0;
        private double bankBonus = 0.0; // Nouveau champ pour le bonus de banque

        public double getTotalBonus() {
            return cristalBonus + professionBonus + prestigeBonus + temporaryBoostBonus + gangBonus + temporaryGangBoostBonus + enchantmentBonus + overloadBonus + armorBonus + bankBonus;
        }

        public double getTotalMultiplier() {
            return 1.0 + (getTotalBonus() / 100.0);
        }

        public double getEnchantmentBonus() {
            return enchantmentBonus;
        }

        public void setEnchantmentBonus(double enchantmentBonus) {
            this.enchantmentBonus = enchantmentBonus;
        }

        public double getOverloadBonus() {
            return overloadBonus;
        }

        public void setOverloadBonus(double overloadBonus) {
            this.overloadBonus = overloadBonus;
        }

        public double getArmorBonus() {
            return armorBonus;
        }

        public void setArmorBonus(double armorBonus) {
            this.armorBonus = armorBonus;
        }

        public double getBankBonus() {
            return bankBonus;
        }

        public void setBankBonus(double bankBonus) {
            this.bankBonus = bankBonus;
        }

        // Getters
        public double getCristalBonus() {
            return cristalBonus;
        }

        // Setters
        public void setCristalBonus(double cristalBonus) {
            this.cristalBonus = cristalBonus;
        }

        public double getProfessionBonus() {
            return professionBonus;
        }

        public void setProfessionBonus(double professionBonus) {
            this.professionBonus = professionBonus;
        }

        public double getPrestigeBonus() {
            return prestigeBonus;
        }

        public void setPrestigeBonus(double prestigeBonus) {
            this.prestigeBonus = prestigeBonus;
        }

        public double getTemporaryBoostBonus() {
            return temporaryBoostBonus;
        }

        public void setTemporaryBoostBonus(double temporaryBoostBonus) {
            this.temporaryBoostBonus = temporaryBoostBonus;
        }

        public double getGangBonus() {
            return gangBonus;
        }

        public void setGangBonus(double gangBonus) {
            this.gangBonus = gangBonus;
        }

        public double getTemporaryGangBoostBonus() {
            return temporaryGangBoostBonus;
        }

        public void setTemporaryGangBoostBonus(double temporaryGangBoostBonus) {
            this.temporaryGangBoostBonus = temporaryGangBoostBonus;
        }

        public Map<String, Double> getDetailedSources() {
            return detailedSources;
        }

        public void addDetailedSource(String name, double percent) {
            detailedSources.put(name, percent);
        }
    }
}