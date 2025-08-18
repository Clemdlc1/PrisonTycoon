package fr.prisontycoon.pets;

import fr.prisontycoon.managers.GlobalBonusManager;

/**
 * Types d'effets supportés par les pets.
 * Seuls les effets déjà mappés dans le projet sont utilisés (aucun nouveau BonusCategory n'est créé).
 */
public enum PetEffectType {
    // Effets directs mappables sur GlobalBonusManager
    TOKEN(GlobalBonusManager.BonusCategory.TOKEN_BONUS),
    MONEY(GlobalBonusManager.BonusCategory.MONEY_BONUS),
    XP(GlobalBonusManager.BonusCategory.EXPERIENCE_BONUS),
    SELL(GlobalBonusManager.BonusCategory.SELL_BONUS),
    FORTUNE(GlobalBonusManager.BonusCategory.FORTUNE_BONUS),
    JOB_XP(GlobalBonusManager.BonusCategory.JOB_XP_BONUS),
    BEACONS(GlobalBonusManager.BonusCategory.BEACON_MULTIPLIER),
    OUTPOST(GlobalBonusManager.BonusCategory.OUTPOST_BONUS),
    TAX_REDUCTION(GlobalBonusManager.BonusCategory.TAX_REDUCTION),
    PVP_MERCHANT_REDUCTION(GlobalBonusManager.BonusCategory.PVP_MERCHANT_REDUCTION),

    // Effets utilitaires internes (déjà gérés côté systèmes existants)
    PROC_PICKAXE(null),           // chance proc enchants pickaxe (hors uniques) appliquée côté EnchantmentManager
    AUTOMINER_FUEL_CONSUMPTION(null),   // réduction consommation autominer appliquée côté AutominerManager
    PICKAXE_WEAR(null),           // réduction d'usure appliquée côté MiningListener/PickaxeManager
    KEYS_CHANCE(null);            // chance d'obtenir des clés (déjà utilisé dans plusieurs endroits)

    private final GlobalBonusManager.BonusCategory mappedCategory;

    PetEffectType(GlobalBonusManager.BonusCategory mappedCategory) {
        this.mappedCategory = mappedCategory;
    }

    public GlobalBonusManager.BonusCategory getMappedCategory() {
        return mappedCategory;
    }
}


