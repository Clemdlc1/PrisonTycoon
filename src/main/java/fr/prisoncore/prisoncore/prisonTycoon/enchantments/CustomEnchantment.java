package fr.prisoncore.prisoncore.prisonTycoon.enchantments;

import org.bukkit.Material;

/**
 * Interface pour les enchantements custom
 */
public interface CustomEnchantment {
    String getName();

    String getDisplayName();

    EnchantmentCategory getCategory();

    String getDescription();

    int getMaxLevel();

    long getUpgradeCost(int level);

    Material getDisplayMaterial();
}
