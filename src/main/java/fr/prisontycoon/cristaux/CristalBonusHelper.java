package fr.prisontycoon.cristaux;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * Helper pour appliquer les bonus des cristaux aux enchantements
 */
public class CristalBonusHelper {

    private final PrisonTycoon plugin;
    private final Random random = new Random();

    public CristalBonusHelper(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Applique le bonus SellBoost au prix de vente
     */
    public double applySellBoost(Player player, double basePrice) {
        double bonus = plugin.getCristalManager().getTotalCristalBonus(player, CristalType.SELL_BOOST);
        return basePrice * (1.0 + bonus / 100.0);
    }

    /**
     * Applique le bonus XPBoost aux gains d'XP
     */
    public long applyXPBoost(Player player, long baseXP) {
        double bonus = plugin.getCristalManager().getTotalCristalBonus(player, CristalType.XP_BOOST);
        return Math.round(baseXP * (1.0 + bonus / 100.0));
    }

    /**
     * Applique le bonus MoneyBoost aux gains d'argent
     */
    public long applyMoneyBoost(Player player, long baseMoney) {
        double bonus = plugin.getCristalManager().getTotalCristalBonus(player, CristalType.MONEY_BOOST);
        return Math.round(baseMoney * (1.0 + bonus / 100.0));
    }

    /**
     * Applique le bonus TokenBoost aux gains de tokens
     */
    public long applyTokenBoost(Player player, long baseTokens) {
        double bonus = plugin.getCristalManager().getTotalCristalBonus(player, CristalType.TOKEN_BOOST);
        return Math.round(baseTokens * (1.0 + bonus / 100.0));
    }

    /**
     * Applique le bonus MineralGreed à l'effet Fortune
     */
    public int applyMineralGreed(Player player, int baseFortune) {
        double bonus = plugin.getCristalManager().getTotalCristalBonus(player, CristalType.MINERAL_GREED);
        return Math.round(baseFortune * (1.0f + (float) bonus / 100.0f));
    }

    /**
     * Calcule la durée prolongée de l'enchantement Abondance
     */
    public int getAbondanceDuration(Player player, int baseDuration) {
        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) return baseDuration;

        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);
        for (Cristal cristal : cristals) {
            if (cristal.getType() == CristalType.ABONDANCE_CRISTAL) {
                int bonus = cristal.getType().getAbondanceDurationBonus(cristal.getNiveau());
                return baseDuration + bonus;
            }
        }

        return baseDuration;
    }

    /**
     * Applique les bonus du cristal Combustion
     */
    public double applyCombustionEfficiency(Player player, double baseEfficiency) {
        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) return baseEfficiency;

        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);
        for (Cristal cristal : cristals) {
            if (cristal.getType() == CristalType.COMBUSTION_CRISTAL) {
                double bonus = cristal.getType().getCombustionEfficiencyBonus(cristal.getNiveau());
                return baseEfficiency * (1.0 + bonus / 100.0);
            }
        }

        return baseEfficiency;
    }

    /**
     * Applique la réduction de diminution du cristal Combustion
     */
    public double applyCombustionDecayReduction(Player player, double baseDecay) {
        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) return baseDecay;

        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);
        for (Cristal cristal : cristals) {
            if (cristal.getType() == CristalType.COMBUSTION_CRISTAL) {
                double reduction = cristal.getType().getCombustionDecayReduction(cristal.getNiveau());
                return baseDecay * (1.0 - reduction / 100.0);
            }
        }

        return baseDecay;
    }

    /**
     * Calcule le nombre d'échos à déclencher pour EchoCristal.
     * Teste les probabilités du plus grand nombre d'échos (le plus rare) au plus petit.
     *
     * @param player Le joueur concerné.
     * @return Le nombre d'échos (ex: de 1 à 5), ou 0 si aucun ne se déclenche.
     */
    public int getEchoCount(Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) return 0;
        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);
        for (Cristal cristal : cristals) {
            if (cristal.getType() == CristalType.ECHO_CRISTAL) {
                double[] chances = cristal.getType().getEchoChances(cristal.getNiveau());
                for (int i = chances.length - 1; i >= 0; i--) {
                    if (random.nextDouble() * 100.0 < chances[i]) {
                        return i + 1;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Récupère le niveau du cristal d'un type spécifique
     */
    public int getCristalLevel(Player player, CristalType type) {
        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) return 0;

        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);
        for (Cristal cristal : cristals) {
            if (cristal.getType() == type) {
                return cristal.getNiveau();
            }
        }

        return 0;
    }
}