package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PrinterTier;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire pour l'empilement des billets au sol
 * Optimise les performances en évitant d'avoir trop d'entités
 */
public class BillStackManager implements Listener {

    private final PrisonTycoon plugin;
    private final NamespacedKey billKey;
    private final NamespacedKey billTierKey;
    private final NamespacedKey billStackSizeKey;
    
    // Cache des entités de billets par location (pour éviter les checks trop fréquents)
    private final Map<Location, Set<Item>> billEntities = new ConcurrentHashMap<>();
    
    // Distance maximale pour considérer deux billets comme stackables
    private static final double STACK_DISTANCE = 1.5;
    
    // Taille maximale d'un stack (peut dépasser 64)
    private static final int MAX_STACK_SIZE = 999;

    public BillStackManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.billKey = new NamespacedKey(plugin, "bill");
        this.billTierKey = new NamespacedKey(plugin, "bill_tier");
        this.billStackSizeKey = new NamespacedKey(plugin, "bill_stack_size");
        
        // Démarrer la tâche de nettoyage périodique
        startCleanupTask();
    }

    /**
     * Créer un billet empilé avec une taille personnalisée
     */
    public ItemStack createStackedBill(int tier, int stackSize) {
        PrinterTier printerTier = PrinterTier.getByTier(tier);
        if (printerTier == null) return null;
        
        ItemStack bill = new ItemStack(printerTier.getBillMaterial());
        ItemMeta meta = bill.getItemMeta();
        if (meta == null) return null;
        
        // Données de base du billet
        meta.setDisplayName(printerTier.getBillName());
        meta.setLore(Arrays.asList(
            printerTier.getBillLore(),
            "",
            "§7Quantité: §e" + stackSize,
            "§7Valeur totale: §e" + printerTier.getBillValue().multiply(java.math.BigInteger.valueOf(stackSize))
        ));
        
        // Marquer comme billet avec PersistentData
        meta.getPersistentDataContainer().set(billKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(billTierKey, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(billStackSizeKey, PersistentDataType.INTEGER, stackSize);
        
        bill.setItemMeta(meta);
        bill.setAmount(1); // L'affichage d'amount reste à 1, la vraie quantité est dans les données
        
        return bill;
    }

    /**
     * Obtient la taille réelle du stack d'un billet
     */
    public int getBillStackSize(ItemStack bill) {
        if (!isBill(bill)) return 0;
        return bill.getItemMeta().getPersistentDataContainer()
            .getOrDefault(billStackSizeKey, PersistentDataType.INTEGER, 1);
    }

    /**
     * Vérifie si un item est un billet
     */
    public boolean isBill(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(billKey, PersistentDataType.BOOLEAN) &&
               meta.getPersistentDataContainer().has(billTierKey, PersistentDataType.INTEGER);
    }

    /**
     * Obtient le tier d'un billet
     */
    public int getBillTier(ItemStack bill) {
        if (!isBill(bill)) return 0;
        return bill.getItemMeta().getPersistentDataContainer()
            .get(billTierKey, PersistentDataType.INTEGER);
    }

    /**
     * Tente d'empiler un nouveau billet avec les billets existants dans la zone
     */
    public Item spawnStackedBill(Location location, int tier) {
        // Créer l'item de base
        ItemStack billStack = createStackedBill(tier, 1);
        if (billStack == null) return null;
        
        // Chercher des billets existants dans la zone
        Collection<Item> nearbyItems = getNearbyBillItems(location, tier);
        
        if (!nearbyItems.isEmpty()) {
            // Trouver le meilleur candidat pour l'empilement
            Item bestCandidate = findBestStackCandidate(nearbyItems, tier);
            if (bestCandidate != null && canStack(bestCandidate, tier)) {
                return stackWithExisting(bestCandidate, 1);
            }
        }
        
        // Aucun stack compatible trouvé, créer un nouveau
        Item newItem = location.getWorld().dropItem(location.add(0.5, 1.0, 0.5), billStack);
        newItem.setVelocity(new Vector(0, 0.1, 0));
        newItem.setPickupDelay(20);
        
        // Ajouter au cache
        billEntities.computeIfAbsent(location, k -> ConcurrentHashMap.newKeySet()).add(newItem);
        
        return newItem;
    }

    /**
     * Trouve les items de billets du même tier dans la zone
     */
    private Collection<Item> getNearbyBillItems(Location location, int tier) {
        Collection<Item> nearbyItems = new ArrayList<>();
        
        for (Item entity : location.getWorld().getEntitiesByClass(Item.class)) {
            if (entity.getLocation().distance(location) <= STACK_DISTANCE) {
                ItemStack itemStack = entity.getItemStack();
                if (isBill(itemStack) && getBillTier(itemStack) == tier) {
                    nearbyItems.add(entity);
                }
            }
        }
        
        return nearbyItems;
    }

    /**
     * Trouve le meilleur candidat pour l'empilement (celui avec le plus petit stack qui peut encore grandir)
     */
    private Item findBestStackCandidate(Collection<Item> items, int tier) {
        Item bestCandidate = null;
        int smallestStackSize = Integer.MAX_VALUE;
        
        for (Item item : items) {
            ItemStack itemStack = item.getItemStack();
            if (canStack(item, tier)) {
                int stackSize = getBillStackSize(itemStack);
                if (stackSize < smallestStackSize) {
                    smallestStackSize = stackSize;
                    bestCandidate = item;
                }
            }
        }
        
        return bestCandidate;
    }

    /**
     * Vérifie si un item peut être empilé
     */
    private boolean canStack(Item item, int tier) {
        if (item == null || item.isDead()) return false;
        
        ItemStack itemStack = item.getItemStack();
        if (!isBill(itemStack) || getBillTier(itemStack) != tier) return false;
        
        int currentStackSize = getBillStackSize(itemStack);
        return currentStackSize < MAX_STACK_SIZE;
    }

    /**
     * Empile avec un item existant
     */
    private Item stackWithExisting(Item existingItem, int addAmount) {
        ItemStack itemStack = existingItem.getItemStack();
        int currentStackSize = getBillStackSize(itemStack);
        int newStackSize = Math.min(currentStackSize + addAmount, MAX_STACK_SIZE);
        
        // Mettre à jour l'item avec la nouvelle taille
        int tier = getBillTier(itemStack);
        ItemStack newStack = createStackedBill(tier, newStackSize);
        existingItem.setItemStack(newStack);
        
        // Effet visuel
        existingItem.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, 
            existingItem.getLocation(), 3, 0.2, 0.2, 0.2, 0);
        existingItem.getWorld().playSound(existingItem.getLocation(), 
            Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.5f);
        
        return existingItem;
    }

    /**
     * Gestionnaire d'événement pour l'empilement automatique lors du spawn d'items
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        ItemStack itemStack = item.getItemStack();
        
        // Vérifier si c'est un billet
        if (!isBill(itemStack)) return;
        
        int tier = getBillTier(itemStack);
        Location location = item.getLocation();
        
        // Chercher des billets compatibles dans la zone
        Collection<Item> nearbyItems = getNearbyBillItems(location, tier);
        nearbyItems.remove(item); // Exclure l'item qui vient d'être spawné
        
        if (!nearbyItems.isEmpty()) {
            Item bestCandidate = findBestStackCandidate(nearbyItems, tier);
            if (bestCandidate != null && canStack(bestCandidate, tier)) {
                // Empiler avec l'existant et supprimer le nouveau
                int billAmount = getBillStackSize(itemStack);
                stackWithExisting(bestCandidate, billAmount);
                event.setCancelled(true);
            }
        }
    }

    /**
     * Gestionnaire pour la récupération d'items empilés
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        
        Item item = event.getItem();
        ItemStack itemStack = item.getItemStack();
        
        if (!isBill(itemStack)) return;
        
        Player player = (Player) event.getEntity();
        int stackSize = getBillStackSize(itemStack);
        int tier = getBillTier(itemStack);
        
        // Créer des stacks de taille normale pour l'inventaire du joueur
        int maxStackSize = itemStack.getMaxStackSize();
        int fullStacks = stackSize / maxStackSize;
        int remainder = stackSize % maxStackSize;
        
        // Vérifier si le joueur a assez de place
        int requiredSlots = fullStacks + (remainder > 0 ? 1 : 0);
        if (getAvailableSlots(player) < requiredSlots) {
            return; // Pas assez de place, laisser l'item au sol
        }
        
        // Donner les items au joueur
        for (int i = 0; i < fullStacks; i++) {
            ItemStack stack = createStackedBill(tier, maxStackSize);
            player.getInventory().addItem(stack);
        }
        
        if (remainder > 0) {
            ItemStack stack = createStackedBill(tier, remainder);
            player.getInventory().addItem(stack);
        }
        
        // Supprimer l'item du sol
        event.setCancelled(true);
        item.remove();
        
        // Son de récupération
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Calcule le nombre de slots disponibles dans l'inventaire du joueur
     */
    private int getAvailableSlots(Player player) {
        int availableSlots = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                availableSlots++;
            }
        }
        return availableSlots;
    }

    /**
     * Tâche de nettoyage périodique pour retirer les entités mortes du cache
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                billEntities.entrySet().removeIf(entry -> {
                    Set<Item> items = entry.getValue();
                    items.removeIf(item -> item == null || item.isDead());
                    return items.isEmpty();
                });
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // Toutes les 30 secondes
    }
}