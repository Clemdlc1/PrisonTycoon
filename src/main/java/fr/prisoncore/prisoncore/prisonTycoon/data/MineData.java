package fr.prisoncore.prisoncore.prisonTycoon.data;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;

/**
 * Données d'une mine
 */
public class MineData {
    private final String name;
    private final Location minCorner;
    private final Location maxCorner;
    private final Map<Material, Double> blockComposition;

    // Cache pour optimiser la génération aléatoire
    private final List<Material> weightedMaterials;

    public MineData(String name, Location minCorner, Location maxCorner, Map<Material, Double> blockComposition) {
        this.name = name;
        this.minCorner = minCorner.clone();
        this.maxCorner = maxCorner.clone();
        this.blockComposition = new HashMap<>(blockComposition);

        // Précalcule la liste pondérée pour l'optimisation
        this.weightedMaterials = buildWeightedList();
    }

    /**
     * Construit une liste pondérée des matériaux pour génération rapide
     */
    private List<Material> buildWeightedList() {
        List<Material> list = new ArrayList<>();

        for (Map.Entry<Material, Double> entry : blockComposition.entrySet()) {
            int weight = (int) (entry.getValue() * 1000); // Précision à 0.1%
            for (int i = 0; i < weight; i++) {
                list.add(entry.getKey());
            }
        }

        return list;
    }

    /**
     * Vérifie si une location est dans cette mine
     */
    public boolean contains(Location location) {
        if (!location.getWorld().equals(minCorner.getWorld())) {
            return false;
        }

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        return x >= Math.min(minCorner.getX(), maxCorner.getX()) &&
                x <= Math.max(minCorner.getX(), maxCorner.getX()) &&
                y >= Math.min(minCorner.getY(), maxCorner.getY()) &&
                y <= Math.max(minCorner.getY(), maxCorner.getY()) &&
                z >= Math.min(minCorner.getZ(), maxCorner.getZ()) &&
                z <= Math.max(minCorner.getZ(), maxCorner.getZ());
    }

    /**
     * Retourne un matériau aléatoire selon la composition
     */
    public Material getRandomMaterial() {
        if (weightedMaterials.isEmpty()) {
            return Material.STONE; // Fallback
        }

        Random random = new Random();
        return weightedMaterials.get(random.nextInt(weightedMaterials.size()));
    }

    /**
     * Calcule le volume total de la mine
     */
    public long getVolume() {
        long width = Math.abs((long) maxCorner.getX() - (long) minCorner.getX()) + 1;
        long height = Math.abs((long) maxCorner.getY() - (long) minCorner.getY()) + 1;
        long length = Math.abs((long) maxCorner.getZ() - (long) minCorner.getZ()) + 1;

        return width * height * length;
    }

    // Getters
    public String getName() {
        return name;
    }

    public Location getMinCorner() {
        return minCorner.clone();
    }

    public Location getMaxCorner() {
        return maxCorner.clone();
    }

    public Map<Material, Double> getBlockComposition() {
        return new HashMap<>(blockComposition);
    }
}

