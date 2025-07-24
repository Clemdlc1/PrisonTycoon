package fr.prisontycoon.data;

import org.bukkit.Material;

/**
 * Valeurs économiques d'un bloc
 */
public record BlockValueData(Material material, long coins, long tokens, long experience) {
    public BlockValueData(Material material, long coins, long tokens, long experience) {
        this.material = material;
        this.coins = Math.max(0, coins);
        this.tokens = Math.max(0, tokens);
        this.experience = Math.max(0, experience);
    }

    @Override
    public String toString() {
        return String.format("BlockValue{%s: %d coins, %d tokens, %d xp}",
                material.name(), coins, tokens, experience);
    }
}
