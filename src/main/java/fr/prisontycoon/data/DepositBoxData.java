package fr.prisontycoon.data;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Données d'une caisse de dépôt
 */
public class DepositBoxData {
    
    private final String id;
    private final UUID owner;
    private final Location location;
    private final int capacityLevel; // 1-20, améliore le nombre d'items par seconde
    private final double multiplierLevel; // Multiplicateur des gains des billets
    private final long lastProcessingTime;
    private final int processingIntervalMs; // Intervalle de traitement en millisecondes
    private final int maxItemsPerSecond; // Nombre max d'items traités par seconde
    
    public DepositBoxData(String id, UUID owner, Location location, int capacityLevel, 
                         double multiplierLevel, long lastProcessingTime, int processingIntervalMs, 
                         int maxItemsPerSecond) {
        this.id = id;
        this.owner = owner;
        this.location = location;
        this.capacityLevel = capacityLevel;
        this.multiplierLevel = multiplierLevel;
        this.lastProcessingTime = lastProcessingTime;
        this.processingIntervalMs = processingIntervalMs;
        this.maxItemsPerSecond = maxItemsPerSecond;
    }
    
    // Getters
    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public Location getLocation() { return location; }
    public int getCapacityLevel() { return capacityLevel; }
    public double getMultiplierLevel() { return multiplierLevel; }
    public long getLastProcessingTime() { return lastProcessingTime; }
    public int getProcessingIntervalMs() { return processingIntervalMs; }
    public int getMaxItemsPerSecond() { return maxItemsPerSecond; }
    
    /**
     * Vérifie si la caisse doit traiter des items
     */
    public boolean shouldProcessItems() {
        return System.currentTimeMillis() - lastProcessingTime >= processingIntervalMs;
    }
    
    /**
     * Crée une nouvelle instance avec le temps de traitement mis à jour
     */
    public DepositBoxData withUpdatedProcessingTime() {
        return new DepositBoxData(id, owner, location, capacityLevel, multiplierLevel, 
                                System.currentTimeMillis(), processingIntervalMs, maxItemsPerSecond);
    }
    
    /**
     * Calcule le nombre d'items à traiter basé sur le niveau de capacité
     */
    public int getItemsToProcess() {
        return Math.min(maxItemsPerSecond, capacityLevel * 2); // 2 items par niveau par seconde
    }
}
