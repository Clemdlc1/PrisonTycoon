package fr.prisontycoon.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Données d'un conteneur - Conserve les métadonnées des items.
 */
public class ContainerData {

    private final int tier;
    private final int maxCapacity;
    private final Map<ItemStack, Integer> contents;
    private final Set<Material> whitelist;
    private final int maxDurability;
    private boolean sellEnabled;
    private int durability;
    private Map<Integer, ItemStack> referenceItems;

    public ContainerData(int tier) {
        this.tier = tier;
        this.maxCapacity = getCapacityForTier(tier);
        this.contents = new LinkedHashMap<>();
        this.whitelist = new HashSet<>();
        this.sellEnabled = true;
        this.maxDurability = getDurabilityForTier(tier);
        this.durability = maxDurability;
        this.referenceItems = new HashMap<>();
    }

    public ContainerData(int tier, Map<ItemStack, Integer> contents, Set<Material> whitelist,
                         boolean sellEnabled, int durability) {
        this.tier = tier;
        this.maxCapacity = getCapacityForTier(tier);
        this.contents = new LinkedHashMap<>(contents);
        this.whitelist = new HashSet<>(whitelist);
        this.sellEnabled = sellEnabled;
        this.maxDurability = getDurabilityForTier(tier);
        this.durability = durability;
        this.referenceItems = new HashMap<>();
    }

    public int getCapacityForTier(int tier) {
        return switch (tier) {
            case 1 -> 6400;
            case 2 -> 9600;
            case 3 -> 16000;
            case 4 -> 32000;
            case 5 -> 64000;
            default -> 1000;
        };
    }

    private int getDurabilityForTier(int tier) {
        return switch (tier) {
            case 1 -> 50;
            case 2 -> 100;
            case 3 -> 200;
            case 4 -> 400;
            case 5 -> 800;
            default -> 25;
        };
    }

    public boolean addItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return false;
        if (!whitelist.isEmpty() && !whitelist.contains(itemStack.getType())) return false;
        if (isFull()) return false;

        // On ne peut pas ajouter plus que l'espace libre
        int amountToAdd = Math.min(itemStack.getAmount(), getFreeSpace());
        if (amountToAdd <= 0) return false;

        ItemStack keyToUpdate = null;
        for (ItemStack key : contents.keySet()) {
            if (key.isSimilar(itemStack)) {
                keyToUpdate = key;
                break;
            }
        }

        if (keyToUpdate != null) {
            contents.merge(keyToUpdate, amountToAdd, Integer::sum);
        } else {
            ItemStack keyItem = itemStack.clone();
            keyItem.setAmount(1); // La clé n'a pas de quantité
            contents.put(keyItem, amountToAdd);
        }
        return true;
    }

    public boolean removeItem(ItemStack itemStack, int amount) {
        ItemStack existingKey = null;
        for (ItemStack key : contents.keySet()) {
            if (key.isSimilar(itemStack)) {
                existingKey = key;
                break;
            }
        }
        if (existingKey == null) return false;

        int current = contents.getOrDefault(existingKey, 0);
        if (current < amount) return false;

        if (current == amount) {
            contents.remove(existingKey);
        } else {
            contents.put(existingKey, current - amount);
        }
        return true;
    }

    public Map<ItemStack, Integer> clearContents() {
        Map<ItemStack, Integer> removed = new LinkedHashMap<>(contents);
        contents.clear();
        return removed;
    }

    public Map<ItemStack, Integer> getVendableContents(java.util.function.Function<Material, Long> priceFunction) {
        Map<ItemStack, Integer> vendable = new LinkedHashMap<>();
        for (Map.Entry<ItemStack, Integer> entry : contents.entrySet()) {
            if (priceFunction.apply(entry.getKey().getType()) > 0) {
                vendable.put(entry.getKey(), entry.getValue());
            }
        }
        return vendable;
    }

    public void clearVendableContents(java.util.function.Function<Material, Long> priceFunction) {
        contents.entrySet().removeIf(entry -> priceFunction.apply(entry.getKey().getType()) > 0);
    }

    public int getTotalItems() {
        return contents.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int getFreeSpace() {
        return maxCapacity - getTotalItems();
    }

    public boolean isFull() {
        return getTotalItems() >= maxCapacity;
    }

    public double getFillPercentage() {
        if (maxCapacity == 0) return 0.0;
        return (double) getTotalItems() / maxCapacity * 100.0;
    }

    public double getDurabilityPercentage() {
        if (maxDurability == 0) return 0.0;
        return (double) durability / maxDurability * 100.0;
    }

    public boolean useDurability(int amount) {
        durability = Math.max(0, durability - amount);
        return durability > 0;
    }

    public boolean isBroken() {
        return durability <= 0;
    }

    public void toggleFilter(Material material) {
        if (!whitelist.remove(material)) {
            whitelist.add(material);
        }
    }

    public void clearFilters() {
        whitelist.clear();
        referenceItems.clear();
    }

    public Map<Integer, ItemStack> getReferenceItems() {
        return this.referenceItems;
    }

    public void setReferenceItems(Map<Integer, ItemStack> referenceItems) {
        this.referenceItems = new HashMap<>(referenceItems);
    }

    // Getters
    public int getTier() { return tier; }
    public int getMaxCapacity() { return maxCapacity; }
    public Map<ItemStack, Integer> getContents() { return new LinkedHashMap<>(contents); }
    public Set<Material> getWhitelist() { return new HashSet<>(whitelist); }
    public boolean isSellEnabled() { return sellEnabled; }
    public int getDurability() { return durability; }
    public int getMaxDurability() { return maxDurability; }

    // Setters
    public void setSellEnabled(boolean sellEnabled) { this.sellEnabled = sellEnabled; }
    public void setDurability(int durability) { this.durability = Math.max(0, Math.min(maxDurability, durability)); }

    @Override
    public ContainerData clone() {
        ContainerData cloned = new ContainerData(tier, contents, whitelist, sellEnabled, durability);
        cloned.setReferenceItems(this.referenceItems);
        return cloned;
    }
}