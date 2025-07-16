package fr.prisoncore.prisoncore.prisonTycoon.data;

import org.bukkit.Material;

/**
 * Valeurs économiques d'un bloc
 */
public class BlockValueData {
    private final Material material;
    private final long coins;
    private final long tokens;
    private final long experience;

    public BlockValueData(Material material, long coins, long tokens, long experience) {
        this.material = material;
        this.coins = Math.max(0, coins);
        this.tokens = Math.max(0, tokens);
        this.experience = Math.max(0, experience);
    }

    // Getters
    public Material getMaterial() {
        return material;
    }

    public long getCoins() {
        return coins;
    }

    public long getTokens() {
        return tokens;
    }

    public long getExperience() {
        return experience;
    }

    @Override
    public String toString() {
        return String.format("BlockValue{%s: %d coins, %d tokens, %d xp}",
                material.name(), coins, tokens, experience);
    }
}
