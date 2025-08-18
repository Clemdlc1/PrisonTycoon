package fr.prisontycoon.pets;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registre central des pets disponibles (data-driven minimal, facile à modifier).
 * Les valeurs correspondent au document idées (effet de base par croissance en %).
 */
public final class PetRegistry {
    private static final Map<String, PetDefinition> BY_ID = new LinkedHashMap<>();

    static {
        // Commun (7)
        register("gargouille", "§fGargouille", PetRarity.COMMON, PetEffectType.XP, 1.0);
        register("vouivre", "§fVouivre", PetRarity.COMMON, PetEffectType.MONEY, 1.2);
        register("kelpie", "§fKelpie", PetRarity.COMMON, PetEffectType.JOB_XP, 1.0);
        register("caitsith", "§fCait Sith", PetRarity.COMMON, PetEffectType.FORTUNE, 0.9);
        register("blackshuck", "§fBlack Shuck", PetRarity.COMMON, PetEffectType.PICKAXE_WEAR, 0.8);
        register("nereide", "§fNéréide", PetRarity.COMMON, PetEffectType.JOB_XP, 1.0);
        register("gevaudan_c", "§fBête du Gévaudan", PetRarity.COMMON, PetEffectType.OUTPOST, 1.0);

        // Rare (5)
        register("griffon", "§dGriffon", PetRarity.RARE, PetEffectType.TOKEN, 2.4);
        register("basilic", "§dBasilic", PetRarity.RARE, PetEffectType.MONEY, 2.0);
        register("selkie", "§dSelkie", PetRarity.RARE, PetEffectType.PROC_PICKAXE, 0.4);
        register("tarasque", "§dTarasque", PetRarity.RARE, PetEffectType.AUTOMINER_FUEL_CONSUMPTION, 0.4);
        register("farfadet_r", "§dFarfadet", PetRarity.RARE, PetEffectType.KEYS_CHANCE, 0.8);

        // Épique (5)
        register("licorne", "§bLicorne", PetRarity.EPIC, PetEffectType.TOKEN, 3.0);
        register("sphinx", "§bSphinx", PetRarity.EPIC, PetEffectType.JOB_XP, 3.0);
        register("morrigan", "§bMorrigan", PetRarity.EPIC, PetEffectType.AUTOMINER_FUEL_CONSUMPTION, 0.6);
        register("cernunnos", "§bCernunnos", PetRarity.EPIC, PetEffectType.FORTUNE, 2.4);
        register("hippogriffe", "§bHippogriffe", PetRarity.EPIC, PetEffectType.OUTPOST, 2.2);

        // Mythique (3)
        register("fenrir", "§6Fenrir", PetRarity.MYTHIC, PetEffectType.SELL, 3.0);
        register("kraken", "§6Kraken", PetRarity.MYTHIC, PetEffectType.BEACONS, 1.0);
        register("tarasque_royale", "§6Tarasque Royale", PetRarity.MYTHIC, PetEffectType.PROC_PICKAXE, 0.75);
    }

    private static void register(String id, String name, PetRarity rarity, PetEffectType type, double basePerGrowth) {
        BY_ID.put(id, new PetDefinition(id, name, rarity, type, basePerGrowth));
    }

    public static PetDefinition get(String id) {
        return BY_ID.get(id);
    }

    public static Collection<PetDefinition> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }
}


