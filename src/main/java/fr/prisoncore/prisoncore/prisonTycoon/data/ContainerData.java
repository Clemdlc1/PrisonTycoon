package fr.prisoncore.prisoncore.prisonTycoon.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Données d'un conteneur - MODIFIÉ pour conserver les métadonnées des items
 */
public class ContainerData {

    private final int tier;
    private final int maxCapacity;
    private final Map<ItemStack, Integer> contents;
    private final Set<Material> whitelist;
    private boolean sellEnabled;
    private int durability;
    private final int maxDurability;

    private Map<Integer, ItemStack> referenceItems;

    public ContainerData(int tier) {
        this.tier = tier;
        this.maxCapacity = getCapacityForTier(tier);
        this.contents = new LinkedHashMap<>();
        this.whitelist = new HashSet<>();
        this.sellEnabled = true;
        this.maxDurability = getDurabilityForTier(tier);
        this.durability = maxDurability;
        this.referenceItems = new HashMap<>(); // Initialisation de la nouvelle structure
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
        this.referenceItems = new HashMap<>(); // Initialisation
    }

    /**
     * Capacité selon le tier
     */
    private int getCapacityForTier(int tier) {
        return switch (tier) {
            case 1 -> 2304; // 36 stacks
            case 2 -> 4608; // 72 stacks
            case 3 -> 9216; // 144 stacks
            case 4 -> 18432; // 288 stacks
            case 5 -> 36864; // 576 stacks
            default -> 1152; // 18 stacks pour tier invalide
        };
    }

    /**
     * Durabilité selon le tier
     */
    private int getDurabilityForTier(int tier) {
        return switch (tier) {
            case 1 -> 50;   // 50 utilisations
            case 2 -> 100;  // 100 utilisations
            case 3 -> 200;  // 200 utilisations
            case 4 -> 400;  // 400 utilisations
            case 5 -> 800;  // 800 utilisations
            default -> 25;  // 25 pour tier invalide
        };
    }

    /**
     * MODIFIÉ : Tente d'ajouter un item au conteneur (avec métadonnées)
     */
    public boolean addItem(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) {
            return false;
        }

        // Vérifie la whitelist par Material
        if (!whitelist.isEmpty() && !whitelist.contains(itemStack.getType())) {
            return false;
        }

        // Vérifie la capacité
        int currentTotal = getTotalItems();
        if (currentTotal + itemStack.getAmount() > maxCapacity) {
            return false;
        }

        // Cherche un ItemStack similaire (même type et même meta)
        ItemStack existingKey = findSimilarItem(itemStack);

        if (existingKey != null) {
            // Ajoute à l'existant
            contents.merge(existingKey, itemStack.getAmount(), Integer::sum);
        } else {
            // Crée une nouvelle entrée
            ItemStack keyItem = itemStack.clone();
            keyItem.setAmount(1); // La clé n'a pas de quantité
            contents.put(keyItem, itemStack.getAmount());
        }

        return true;
    }

    /**
     * Trouve un ItemStack similaire dans le conteneur
     */
    private ItemStack findSimilarItem(ItemStack item) {
        for (ItemStack key : contents.keySet()) {
            if (key.getType() == item.getType() && key.isSimilar(item)) {
                return key;
            }
        }
        return null;
    }

    /**
     * MODIFIÉ : Retire un item du conteneur
     */
    public boolean removeItem(ItemStack itemStack, int amount) {
        ItemStack existingKey = findSimilarItem(itemStack);
        if (existingKey == null) {
            return false;
        }

        int current = contents.getOrDefault(existingKey, 0);
        if (current < amount) {
            return false;
        }

        if (current == amount) {
            contents.remove(existingKey);
        } else {
            contents.put(existingKey, current - amount);
        }
        return true;
    }

    /**
     * MODIFIÉ : Vide complètement le conteneur
     */
    public Map<ItemStack, Integer> clearContents() {
        Map<ItemStack, Integer> removed = new LinkedHashMap<>();

        // Crée des ItemStack avec les bonnes quantités
        for (Map.Entry<ItemStack, Integer> entry : contents.entrySet()) {
            ItemStack item = entry.getKey().clone();
            item.setAmount(entry.getValue());
            removed.put(item, entry.getValue());
        }

        contents.clear();
        return removed;
    }

    /**
     * MODIFIÉ : Vide partiellement le conteneur selon l'espace disponible
     */
    public Map<ItemStack, Integer> clearContentsWithLimit(int maxItems) {
        Map<ItemStack, Integer> removed = new LinkedHashMap<>();
        int remainingSpace = maxItems;

        Iterator<Map.Entry<ItemStack, Integer>> iterator = contents.entrySet().iterator();

        while (iterator.hasNext() && remainingSpace > 0) {
            Map.Entry<ItemStack, Integer> entry = iterator.next();
            ItemStack keyItem = entry.getKey();
            int quantity = entry.getValue();

            if (quantity <= remainingSpace) {
                // Prend tout cet item
                ItemStack item = keyItem.clone();
                item.setAmount(quantity);
                removed.put(item, quantity);
                remainingSpace -= quantity;
                iterator.remove();
            } else {
                // Prend seulement ce qui rentre
                ItemStack item = keyItem.clone();
                item.setAmount(remainingSpace);
                removed.put(item, remainingSpace);

                // Met à jour la quantité restante
                entry.setValue(quantity - remainingSpace);
                remainingSpace = 0;
            }
        }

        return removed;
    }

    /**
     * Nombre total d'items dans le conteneur
     */
    public int getTotalItems() {
        return contents.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Espace libre dans le conteneur
     */
    public int getFreeSpace() {
        return maxCapacity - getTotalItems();
    }

    /**
     * Pourcentage de remplissage
     */
    public double getFillPercentage() {
        return (double) getTotalItems() / maxCapacity * 100.0;
    }

    /**
     * Pourcentage de durabilité
     */
    public double getDurabilityPercentage() {
        return (double) durability / maxDurability * 100.0;
    }

    /**
     * Utilise de la durabilité (lors de la vente)
     */
    public boolean useDurability(int amount) {
        durability = Math.max(0, durability - amount);
        return durability > 0; // Retourne false si cassé
    }

    /**
     * Vérifie si le conteneur est cassé
     */
    public boolean isBroken() {
        return durability <= 0;
    }

    /**
     * Ajoute ou retire un filtre
     */
    public void toggleFilter(Material material) {
        if (whitelist.contains(material)) {
            whitelist.remove(material);
        } else {
            whitelist.add(material);
        }
    }

    /**
     * Efface tous les filtres
     */
    public void clearFilters() {
        whitelist.clear();
    }

    /**
     * NOUVEAU : Obtient la liste des items vendables dans le conteneur
     */
    public Map<ItemStack, Integer> getVendableContents(java.util.function.Function<Material, Long> priceFunction) {
        Map<ItemStack, Integer> vendable = new LinkedHashMap<>();

        for (Map.Entry<ItemStack, Integer> entry : contents.entrySet()) {
            ItemStack item = entry.getKey();
            if (priceFunction.apply(item.getType()) > 0) {
                vendable.put(item, entry.getValue());
            }
        }

        return vendable;
    }

    /**
     * NOUVEAU : Retire seulement les items vendables du conteneur
     */
    public Map<ItemStack, Integer> clearVendableContents(java.util.function.Function<Material, Long> priceFunction) {
        Map<ItemStack, Integer> removed = new LinkedHashMap<>();
        Iterator<Map.Entry<ItemStack, Integer>> iterator = contents.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<ItemStack, Integer> entry = iterator.next();
            ItemStack keyItem = entry.getKey();

            if (priceFunction.apply(keyItem.getType()) > 0) {
                // Item vendable
                ItemStack item = keyItem.clone();
                item.setAmount(entry.getValue());
                removed.put(item, entry.getValue());
                iterator.remove();
            }
        }

        return removed;
    }

    /**
     * NOUVEAU: Getters/Setters pour les items de référence
     */
    public Map<Integer, ItemStack> getReferenceItems() {
        return new HashMap<>(referenceItems);
    }

    public void setReferenceItems(Map<Integer, ItemStack> referenceItems) {
        this.referenceItems = new HashMap<>(referenceItems);
    }

    public ItemStack getReferenceItem(Material material) {
        return referenceItems.get(material.name());
    }

    /**
     * NOUVEAU : Obtient le nom d'affichage d'un item
     */
    public static String getDisplayName(ItemStack item) {
        if (item == null) return "Item invalide";

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        } else {
            // Nom par défaut du matériau
            String name = item.getType().name().toLowerCase().replace("_", " ");
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }
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

    /**
     * Clone pour éviter les modifications non contrôlées
     */
    public ContainerData clone() {
        return new ContainerData(tier, contents, whitelist, sellEnabled, durability);
    }
}