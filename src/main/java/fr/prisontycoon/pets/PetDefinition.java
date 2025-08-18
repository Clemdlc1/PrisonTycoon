package fr.prisontycoon.pets;

/**
 * Définition immuable d'un pet (id, nom affiché, rareté, type d'effet et valeur de base par croissance).
 */
public record PetDefinition(
        String id,               // identifiant unique interne (ex: "gargouille")
        String displayName,      // nom coloré pour l'UI
        PetRarity rarity,
        PetEffectType effectType,
        double basePerGrowthPercent // pourcentage par point de croissance (ex: 1.0 => +1% par croissance)
) {
}


