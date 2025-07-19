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
    private final int maxDurability;
    private final Map<ItemStack, Integer> contents;
    private final Set<Material> whitelist;
    private Map<Integer, ItemStack> referenceItems = new HashMap<>();
    private boolean sellEnabled;
    private int durability;

    // NOUVEAU: Cache pour éviter les recalculs
    private int totalItemsCache = 0;
    private boolean totalItemsCacheValid = false;

    // Constructeurs existants restent identiques...
    public ContainerData(int tier) {
        this(tier, new LinkedHashMap<>(), new HashSet<>(), false, getMaxDurabilityForTier(tier));
    }

    public ContainerData(int tier, Map<ItemStack, Integer> contents, Set<Material> whitelist, boolean sellEnabled, int durability) {
        this.tier = tier;
        this.maxCapacity = getMaxCapacityForTier(tier);
        this.maxDurability = getMaxDurabilityForTier(tier);
        this.contents = new LinkedHashMap<>(contents);
        this.whitelist = new HashSet<>(whitelist);
        this.sellEnabled = sellEnabled;
        this.durability = Math.max(0, Math.min(maxDurability, durability));
        invalidateTotalItemsCache();
    }

    private void invalidateTotalItemsCache() {
        this.totalItemsCacheValid = false;
    }

    private void updateTotalItemsCache() {
        if (!totalItemsCacheValid) {
            // Utilisation d'une boucle simple au lieu d'un stream
            int total = 0;
            for (Integer value : contents.values()) {
                total += value;
            }
            this.totalItemsCache = total;
            this.totalItemsCacheValid = true;
        }
    }

    public int getMaxCapacityForTier(int tier) {
        return switch (tier) {
            case 1 -> 6400;
            case 2 -> 9600;
            case 3 -> 16000;
            case 4 -> 32000;
            case 5 -> 64000;
            default -> 1000;
        };
    }

    public boolean addItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return false;
        if (!whitelist.isEmpty() && !whitelist.contains(itemStack.getType())) return false;

        // Vérification rapide de l'espace disponible
        updateTotalItemsCache();
        if (totalItemsCache >= maxCapacity) return false;

        // Calculer l'espace libre directement du cache
        int freeSpace = maxCapacity - totalItemsCache;
        int amountToAdd = Math.min(itemStack.getAmount(), freeSpace);
        if (amountToAdd <= 0) return false;

        // OPTIMISATION: Recherche optimisée de clé existante
        ItemStack keyToUpdate = findSimilarKey(itemStack);

        if (keyToUpdate != null) {
            contents.merge(keyToUpdate, amountToAdd, Integer::sum);
        } else {
            ItemStack keyItem = itemStack.clone();
            keyItem.setAmount(1); // La clé n'a pas de quantité
            contents.put(keyItem, amountToAdd);
        }

        // Mettre à jour le cache directement au lieu de l'invalider
        totalItemsCache += amountToAdd;
        // Le cache reste valide car on vient de le mettre à jour

        return true;
    }

    private ItemStack findSimilarKey(ItemStack itemStack) {
        // Pour de petites collections, une boucle simple est plus rapide qu'un stream
        for (ItemStack key : contents.keySet()) {
            if (key.isSimilar(itemStack)) {
                return key;
            }
        }
        return null;
    }

    public boolean removeItem(ItemStack itemStack, int amount) {
        ItemStack existingKey = findSimilarKey(itemStack);
        if (existingKey == null) return false;

        int current = contents.getOrDefault(existingKey, 0);
        if (current < amount) return false;

        if (current == amount) {
            contents.remove(existingKey);
        } else {
            contents.put(existingKey, current - amount);
        }

        // Invalider le cache après modification
        invalidateTotalItemsCache();
        return true;
    }

    public Map<ItemStack, Integer> clearContents() {
        Map<ItemStack, Integer> removed = new LinkedHashMap<>(contents);
        contents.clear();
        // Invalider le cache après modification
        invalidateTotalItemsCache();
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
        boolean modified = contents.entrySet().removeIf(entry -> priceFunction.apply(entry.getKey().getType()) > 0);
        if (modified) {
            invalidateTotalItemsCache();
        }
    }

    public int getTotalItems() {
        updateTotalItemsCache();
        return totalItemsCache;
    }

    // OPTIMISATION: getFreeSpace() avec cache direct
    public int getFreeSpace() {
        updateTotalItemsCache();
        return maxCapacity - totalItemsCache;
    }

    // OPTIMISATION: isFull() avec cache direct
    public boolean isFull() {
        updateTotalItemsCache();
        return totalItemsCache >= maxCapacity;
    }

    public double getFillPercentage() {
        if (maxCapacity == 0) return 0.0;
        updateTotalItemsCache();
        return (double) totalItemsCache / maxCapacity * 100.0;
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

    // Getters
    public int getTier() { return tier; }
    public int getMaxCapacity() { return maxCapacity; }
    public Map<ItemStack, Integer> getContents() { return new LinkedHashMap<>(contents); }
    public Set<Material> getWhitelist() { return new HashSet<>(whitelist); }
    public boolean isSellEnabled() { return sellEnabled; }
    public int getDurability() { return durability; }
    public int getMaxDurability() { return maxDurability; }
    public Map<Integer, ItemStack> getReferenceItems() { return this.referenceItems; }

    // Setters
    public void setSellEnabled(boolean sellEnabled) { this.sellEnabled = sellEnabled; }
    public void setDurability(int durability) { this.durability = Math.max(0, Math.min(maxDurability, durability)); }
    public void setReferenceItems(Map<Integer, ItemStack> referenceItems) { this.referenceItems = new HashMap<>(referenceItems); }

    @Override
    public ContainerData clone() {
        ContainerData cloned = new ContainerData(tier, contents, whitelist, sellEnabled, durability);
        cloned.setReferenceItems(this.referenceItems);
        return cloned;
    }

    public static int getMaxDurabilityForTier(int tier) {
        return switch (tier) {
            case 1 -> 50;
            case 2 -> 100;
            case 3 -> 200;
            case 4 -> 400;
            case 5 -> 800;
            default -> 25;
        };
    }
}