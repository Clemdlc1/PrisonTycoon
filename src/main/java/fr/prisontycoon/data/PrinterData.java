package fr.prisontycoon.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.math.BigInteger;
import java.util.UUID;

/**
 * Données d'une imprimante à argent
 */
public class PrinterData {
    
    private final String id;
    private final UUID owner;
    private final Location location;
    private final int tier;
    private final long lastGenerationTime;
    private final int generationIntervalSeconds;
    private final BigInteger billValue;
    private final Material billMaterial;
    private final String billName;
    private final String billLore;
    
    public PrinterData(String id, UUID owner, Location location, int tier, long lastGenerationTime, 
                      int generationIntervalSeconds, BigInteger billValue, Material billMaterial, 
                      String billName, String billLore) {
        this.id = id;
        this.owner = owner;
        this.location = location;
        this.tier = tier;
        this.lastGenerationTime = lastGenerationTime;
        this.generationIntervalSeconds = generationIntervalSeconds;
        this.billValue = billValue;
        this.billMaterial = billMaterial;
        this.billName = billName;
        this.billLore = billLore;
    }
    
    // Getters
    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public Location getLocation() { return location; }
    public int getTier() { return tier; }
    public long getLastGenerationTime() { return lastGenerationTime; }
    public int getGenerationIntervalSeconds() { return generationIntervalSeconds; }
    public BigInteger getBillValue() { return billValue; }
    public Material getBillMaterial() { return billMaterial; }
    public String getBillName() { return billName; }
    public String getBillLore() { return billLore; }
    
    /**
     * Crée un ItemStack représentant un billet de cette imprimante
     */
    public ItemStack createBillItem() {
        ItemStack bill = new ItemStack(billMaterial);
        bill.getItemMeta().setDisplayName(billName);
        bill.getItemMeta().setLore(java.util.Arrays.asList(billLore));
        return bill;
    }
    
    /**
     * Vérifie si l'imprimante doit générer un nouveau billet
     */
    public boolean shouldGenerateBill() {
        return System.currentTimeMillis() - lastGenerationTime >= generationIntervalSeconds * 1000L;
    }
    
    /**
     * Crée une nouvelle instance avec le temps de génération mis à jour
     */
    public PrinterData withUpdatedGenerationTime() {
        return new PrinterData(id, owner, location, tier, System.currentTimeMillis(), 
                              generationIntervalSeconds, billValue, billMaterial, billName, billLore);
    }
}
