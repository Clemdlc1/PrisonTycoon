package fr.prisontycoon.managers;

import com.google.gson.Gson;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.DepositBoxData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.data.PrinterTier;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

/**
 * Gestionnaire des caisses de dépôt
 */
public class DepositBoxManager {

    private final PrisonTycoon plugin;
    private final Gson gson = new Gson();
    private final NamespacedKey depositBoxKey;
    private final NamespacedKey depositBoxIdKey;
    private final NamespacedKey billKey;
    private final NamespacedKey billTierKey;
    
    // Cache des caisses de dépôt (utilise le DatabaseManager de Skyblock)
    private final Map<String, DepositBoxData> depositBoxCache = new ConcurrentHashMap<>();
    private final Map<Location, String> depositBoxLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerDepositBoxes = new ConcurrentHashMap<>();
    
    // Configuration de la caisse de dépôt
    private final DepositBoxConfig depositBoxConfig;
    
    public DepositBoxManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.depositBoxKey = new NamespacedKey(plugin, "deposit_box");
        this.depositBoxIdKey = new NamespacedKey(plugin, "deposit_box_id");
        this.billKey = new NamespacedKey(plugin, "bill");
        this.billTierKey = new NamespacedKey(plugin, "bill_tier");
        
        // Une seule caisse de dépôt avec améliorations via GUI
        this.depositBoxConfig = new DepositBoxConfig(2, 1.0, 1000L);
        
        initializeCaches();
        startProcessingTask();
        
        plugin.getPluginLogger().info("§aDepositBoxManager initialisé.");
    }
    
    private void initializeCaches() {
        // Initialiser les caches à partir du DepositBoxManager de Skyblock
        if (plugin.getCustomSkyblock() != null && plugin.getCustomSkyblock().getDepositBoxManager() != null) {
            var skyblockDepositBoxManager = plugin.getCustomSkyblock().getDepositBoxManager();
            try {
                var depositBoxes = (Map<String, fr.skyblock.models.DepositBoxData>) skyblockDepositBoxManager.getClass()
                    .getMethod("getAllDepositBoxes")
                    .invoke(skyblockDepositBoxManager);
            
            for (Map.Entry<String, fr.skyblock.models.DepositBoxData> entry : depositBoxes.entrySet()) {
                fr.skyblock.models.DepositBoxData skyblockDepositBox = entry.getValue();
                // Convertir en DepositBoxData de PrisonTycoon
                DepositBoxData depositBox = new DepositBoxData(
                    skyblockDepositBox.getId(),
                    skyblockDepositBox.getOwner(),
                    skyblockDepositBox.getLocation(),
                    skyblockDepositBox.getCapacityLevel(),
                    skyblockDepositBox.getMultiplierLevel(),
                    skyblockDepositBox.getLastProcessingTime(),
                    skyblockDepositBox.getProcessingIntervalMs(),
                    skyblockDepositBox.getMaxItemsPerSecond()
                );
                
                depositBoxCache.put(depositBox.getId(), depositBox);
                depositBoxLocations.put(depositBox.getLocation(), depositBox.getId());
                playerDepositBoxes.computeIfAbsent(depositBox.getOwner(), k -> ConcurrentHashMap.newKeySet()).add(depositBox.getId());
            }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors du chargement des caisses de dépôt: " + e.getMessage());
            }
        }
    }
    
    /**
     * Sauvegarde toutes les caisses de dépôt via le DatabaseManager de Skyblock
     */
    public void saveAll() {
        if (plugin.getCustomSkyblock() != null && plugin.getCustomSkyblock().getDatabaseManager() != null) {
            var skyblockDb = plugin.getCustomSkyblock().getDatabaseManager();
            skyblockDb.saveAll();
        }
    }
    
    /**
     * Crée une nouvelle caisse de dépôt
     */
    public DepositBoxData createDepositBox(UUID owner, Location location) {
        String id = UUID.randomUUID().toString();
        DepositBoxData depositBox = new DepositBoxData(id, owner, location, 1,
                depositBoxConfig.getMultiplier(), System.currentTimeMillis(), 1000, depositBoxConfig.getMaxItemsPerSecond());
        
        depositBoxCache.put(id, depositBox);
        depositBoxLocations.put(location, id);
        playerDepositBoxes.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(id);
        
        // Sauvegarder via le DepositBoxManager de Skyblock
        if (plugin.getCustomSkyblock() != null && plugin.getCustomSkyblock().getDepositBoxManager() != null) {
            var skyblockDepositBoxManager = plugin.getCustomSkyblock().getDepositBoxManager();
            // Convertir en DepositBoxData de Skyblock
            fr.skyblock.models.DepositBoxData skyblockDepositBox = new fr.skyblock.models.DepositBoxData(
                depositBox.getId(),
                depositBox.getOwner(),
                depositBox.getLocation(),
                depositBox.getCapacityLevel(),
                depositBox.getMultiplierLevel(),
                depositBox.getLastProcessingTime(),
                depositBox.getProcessingIntervalMs(),
                depositBox.getMaxItemsPerSecond()
            );
            
            // Obtenir l'ID de l'île à cette location via réflexion
            try {
                UUID islandId = (UUID) skyblockDepositBoxManager.getClass()
                    .getMethod("getIslandIdAtLocation", Location.class)
                    .invoke(skyblockDepositBoxManager, location);
                
                if (islandId != null) {
                    skyblockDepositBoxManager.getClass()
                        .getMethod("saveDepositBox", UUID.class, Object.class)
                        .invoke(skyblockDepositBoxManager, islandId, skyblockDepositBox);
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors de la sauvegarde de la caisse de dépôt: " + e.getMessage());
            }
        }
        
        return depositBox;
    }
    

    
    /**
     * Vérifie si un item est une caisse de dépôt
     */
    public boolean isDepositBox(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(depositBoxKey, PersistentDataType.BOOLEAN);
    }
    

    
    /**
     * Crée un item de caisse de dépôt
     */
    public ItemStack createDepositBoxItem() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lCaisse de Dépôt");
        meta.setLore(Arrays.asList(
                "§7Capacité: §e" + depositBoxConfig.getMaxItemsPerSecond() + " items/seconde",
                "§7Multiplicateur: §e" + depositBoxConfig.getMultiplier() + "x",
                "§7Améliorable via GUI",
                "",
                "§eClic-droit pour placer"
        ));
        
        meta.getPersistentDataContainer().set(depositBoxKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(depositBoxIdKey, PersistentDataType.INTEGER, 1);
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Vérifie si un item est un billet
     */
    public boolean isBill(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        
        // Vérifier d'abord avec le BillStackManager
        BillStackManager billStackManager = plugin.getPrinterManager().getBillStackManager();
        if (billStackManager != null && billStackManager.isBill(item)) {
            return true;
        }
        
        // Fallback vers la méthode classique
        return meta.getPersistentDataContainer().has(billKey, PersistentDataType.BOOLEAN) &&
                meta.getPersistentDataContainer().has(billTierKey, PersistentDataType.INTEGER);
    }
    
    /**
     * Obtient le tier d'un billet
     */
    public int getBillTier(ItemStack item) {
        if (!isBill(item)) return 0;
        
        // Vérifier d'abord avec le BillStackManager
        BillStackManager billStackManager = plugin.getPrinterManager().getBillStackManager();
        if (billStackManager != null && billStackManager.isBill(item)) {
            return billStackManager.getBillTier(item);
        }
        
        // Fallback vers la méthode classique
        return item.getItemMeta().getPersistentDataContainer().get(billTierKey, PersistentDataType.INTEGER);
    }
    
    /**
     * Obtient la quantité réelle d'un billet (en tenant compte du stacking)
     */
    public int getBillStackSize(ItemStack item) {
        if (!isBill(item)) return 0;
        
        // Vérifier d'abord avec le BillStackManager
        BillStackManager billStackManager = plugin.getPrinterManager().getBillStackManager();
        if (billStackManager != null && billStackManager.isBill(item)) {
            return billStackManager.getBillStackSize(item);
        }
        
        // Fallback vers la quantité classique
        return item.getAmount();
    }
    
    /**
     * Obtient la valeur d'un billet
     */
    public BigInteger getBillValue(ItemStack item) {
        int tier = getBillTier(item);
        PrinterTier printerTier = PrinterTier.getByTier(tier);
        return printerTier != null ? printerTier.getBillValue() : BigInteger.ZERO;
    }
    
    /**
     * Démarre la tâche de traitement des caisses de dépôt
     */
    private void startProcessingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (DepositBoxData depositBox : depositBoxCache.values()) {
                    if (depositBox.shouldProcessItems()) {
                        processDepositBox(depositBox);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Vérification toutes les secondes
    }
    
    /**
     * Traite une caisse de dépôt
     */
    private void processDepositBox(DepositBoxData depositBox) {
        Location location = depositBox.getLocation();
        Block block = location.getBlock();
        
        if (block.getType() != Material.CHEST) {
            return; // La caisse a été détruite
        }
        
        // Obtenir l'ID de l'île pour les améliorations
        UUID islandId = getIslandIdAtLocation(location);
        double hopperTransferSpeed = islandId != null ? getHopperTransferSpeed(islandId) : 1.0;
        
        // Vérifier s'il y a un hopper connecté
        Location hopperLocation = location.clone().add(0, -1, 0);
        Block hopperBlock = hopperLocation.getBlock();
        
        if (hopperBlock.getType() != Material.HOPPER) {
            return; // Pas de hopper connecté
        }
        
        Hopper hopper = (Hopper) hopperBlock.getState();
        Inventory hopperInventory = hopper.getInventory();
        
        // Traiter les items du hopper avec la vitesse de transfert améliorée
        int baseItemsToProcess = depositBox.getItemsToProcess();
        int itemsToProcess = (int) (baseItemsToProcess * hopperTransferSpeed);
        int processedItems = 0;
        BigInteger totalEarnings = BigInteger.ZERO;
        
        for (int i = 0; i < hopperInventory.getSize() && processedItems < itemsToProcess; i++) {
            ItemStack item = hopperInventory.getItem(i);
            if (item != null && isBill(item)) {
                // Obtenir la quantité réelle (stack size)
                int stackSize = getBillStackSize(item);
                BigInteger billValue = getBillValue(item);
                
                // Calculer la valeur totale avec le multiplicateur de la caisse de dépôt
                double multiplier = 1.0 + (depositBox.getMultiplierLevel() * 0.1); // 10% par niveau
                BigInteger totalValue = billValue.multiply(BigInteger.valueOf(stackSize))
                    .multiply(BigInteger.valueOf((long) (multiplier * 100)))
                    .divide(BigInteger.valueOf(100));
                
                // Appliquer le bonus de vente du joueur via GlobalBonusManager
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(depositBox.getOwner());
                Player player = plugin.getServer().getPlayer(depositBox.getOwner());
                if (player != null) {
                    double salesMultiplier = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, 
                        fr.prisontycoon.managers.GlobalBonusManager.BonusCategory.SELL_BONUS);
                    totalValue = totalValue.multiply(BigInteger.valueOf((long) (salesMultiplier * 100)))
                        .divide(BigInteger.valueOf(100));
                }
                
                totalEarnings = totalEarnings.add(totalValue);
                
                // Retirer l'item du hopper
                hopperInventory.setItem(i, null);
                processedItems += stackSize; // Compter la vraie quantité processée
                
                // Limiter le traitement si on dépasse la capacité
                if (processedItems >= itemsToProcess) {
                    break;
                }
            }
        }
        
        // Donner l'argent au propriétaire s'il y a des gains
        if (totalEarnings.compareTo(BigInteger.ZERO) > 0) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(depositBox.getOwner());
            playerData.addCoins(totalEarnings.longValue());
            plugin.getPlayerDataManager().markDirty(depositBox.getOwner());
            
            // Effet sonore et notification
            location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            
            // Notifier le joueur s'il est en ligne
            Player owner = plugin.getServer().getPlayer(depositBox.getOwner());
            if (owner != null) {
                owner.sendMessage("§a💰 §lCaisse de dépôt: §6+" + fr.prisontycoon.utils.NumberFormatter.format(totalEarnings.longValue()) + " coins");
            }
        }
        
        // Mettre à jour le temps de traitement
        DepositBoxData updatedDepositBox = depositBox.withUpdatedProcessingTime();
        depositBoxCache.put(depositBox.getId(), updatedDepositBox);
        
        // Sauvegarder via le DepositBoxManager de Skyblock
        if (plugin.getCustomSkyblock() != null && plugin.getCustomSkyblock().getDepositBoxManager() != null) {
            var skyblockDepositBoxManager = plugin.getCustomSkyblock().getDepositBoxManager();
            // Convertir en DepositBoxData de Skyblock
            fr.skyblock.models.DepositBoxData skyblockDepositBox = new fr.skyblock.models.DepositBoxData(
                updatedDepositBox.getId(),
                updatedDepositBox.getOwner(),
                updatedDepositBox.getLocation(),
                updatedDepositBox.getCapacityLevel(),
                updatedDepositBox.getMultiplierLevel(),
                updatedDepositBox.getLastProcessingTime(),
                updatedDepositBox.getProcessingIntervalMs(),
                updatedDepositBox.getMaxItemsPerSecond()
            );
            
            // Obtenir l'ID de l'île à cette location via réflexion
            try {
                UUID islandId2 = (UUID) skyblockDepositBoxManager.getClass()
                    .getMethod("getIslandIdAtLocation", Location.class)
                    .invoke(skyblockDepositBoxManager, depositBox.getLocation());
                
                if (islandId2 != null) {
                    skyblockDepositBoxManager.getClass()
                        .getMethod("saveDepositBox", UUID.class, Object.class)
                        .invoke(skyblockDepositBoxManager, islandId2, skyblockDepositBox);
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors de la sauvegarde de la caisse de dépôt: " + e.getMessage());
            }
        }
    }
    
    /**
     * Traite l'inventaire d'un joueur pour vendre les billets (commande /depot)
     */
    public void processPlayerInventory(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        BigInteger totalEarnings = BigInteger.ZERO;
        int billsSold = 0;

        // Parcourir l'inventaire du joueur
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && isBill(item)) {
                // Calculer la valeur du billet
                BigInteger billValue = getBillValue(item);
                int stackSize = getBillStackSize(item); // Utiliser la taille du stack réelle
                
                // Appliquer le bonus de vente du joueur via GlobalBonusManager
                double salesMultiplier = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, 
                    fr.prisontycoon.managers.GlobalBonusManager.BonusCategory.SELL_BONUS);
                BigInteger totalValue = billValue.multiply(BigInteger.valueOf(stackSize))
                    .multiply(BigInteger.valueOf((long) (salesMultiplier * 100)))
                    .divide(BigInteger.valueOf(100));

                totalEarnings = totalEarnings.add(totalValue);
                billsSold += stackSize;
                
                // Retirer l'item de l'inventaire
                player.getInventory().setItem(i, null);
            }
        }

        if (billsSold > 0) {
            // Ajouter les gains au joueur
            playerData.addCoins(totalEarnings.longValue());
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());

            // Messages de succès
            player.sendMessage("§a✅ §lVente réussie!");
            player.sendMessage("§7Billets vendus: §e" + billsSold);
            player.sendMessage("§7Gains totaux: §6" + NumberFormatter.format(totalEarnings.longValue()) + " coins");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        } else {
            player.sendMessage("§c❌ Aucun billet trouvé dans votre inventaire!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * Récupère une caisse de dépôt par son ID
     */
    public DepositBoxData getDepositBoxById(String id) {
        return depositBoxCache.values().stream()
            .filter(depositBox -> depositBox.getId().equals(id))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Obtient l'ID d'une caisse de dépôt à une location donnée
     */
    public String getDepositBoxIdAtLocation(Location location) {
        for (DepositBoxData depositBox : depositBoxCache.values()) {
            if (depositBox.getLocation().equals(location)) {
                return depositBox.getId();
            }
        }
        return null;
    }

    /**
     * Améliore la capacité d'une caisse de dépôt
     */
    public DepositBoxData upgradeDepositBoxCapacity(String depositBoxId) {
        DepositBoxData depositBox = getDepositBoxById(depositBoxId);
        if (depositBox == null) return null;

        int currentLevel = depositBox.getCapacityLevel();
        if (currentLevel >= 20) return depositBox; // Déjà au maximum

        // Créer une nouvelle instance avec le niveau amélioré
        DepositBoxData upgradedDepositBox = new DepositBoxData(
            depositBox.getId(),
            depositBox.getOwner(),
            depositBox.getLocation(),
            currentLevel + 1,
            depositBox.getMultiplierLevel(),
            depositBox.getLastProcessingTime(),
            depositBox.getProcessingIntervalMs(),
            (currentLevel + 1) * 2 // Nouvelle capacité
        );

        // Mettre à jour le cache
        depositBoxCache.put(depositBoxId, upgradedDepositBox);

        // Sauvegarder via le DepositBoxManager de Skyblock
        if (plugin.getCustomSkyblock() != null && plugin.getCustomSkyblock().getDepositBoxManager() != null) {
            var skyblockDepositBoxManager = plugin.getCustomSkyblock().getDepositBoxManager();
            // Convertir en DepositBoxData de Skyblock
            fr.skyblock.models.DepositBoxData skyblockDepositBox = new fr.skyblock.models.DepositBoxData(
                upgradedDepositBox.getId(),
                upgradedDepositBox.getOwner(),
                upgradedDepositBox.getLocation(),
                upgradedDepositBox.getCapacityLevel(),
                upgradedDepositBox.getMultiplierLevel(),
                upgradedDepositBox.getLastProcessingTime(),
                upgradedDepositBox.getProcessingIntervalMs(),
                upgradedDepositBox.getMaxItemsPerSecond()
            );
            
            // Obtenir l'ID de l'île à cette location
            UUID islandId = skyblockDepositBoxManager.getIslandIdAtLocation(upgradedDepositBox.getLocation());
            if (islandId != null) {
                skyblockDepositBoxManager.saveDepositBox(islandId, skyblockDepositBox);
            }
        }

        return upgradedDepositBox;
    }

    /**
     * Améliore le multiplicateur d'une caisse de dépôt
     */
    public DepositBoxData upgradeDepositBoxMultiplier(String depositBoxId) {
        DepositBoxData depositBox = getDepositBoxById(depositBoxId);
        if (depositBox == null) return null;

        double currentMultiplier = depositBox.getMultiplierLevel();
        if (currentMultiplier >= 20.0) return depositBox; // Déjà au maximum

        // Créer une nouvelle instance avec le multiplicateur amélioré
        DepositBoxData upgradedDepositBox = new DepositBoxData(
            depositBox.getId(),
            depositBox.getOwner(),
            depositBox.getLocation(),
            depositBox.getCapacityLevel(),
            currentMultiplier + 0.5,
            depositBox.getLastProcessingTime(),
            depositBox.getProcessingIntervalMs(),
            depositBox.getMaxItemsPerSecond()
        );

        // Mettre à jour le cache
        depositBoxCache.put(depositBoxId, upgradedDepositBox);

        // Sauvegarder via le DepositBoxManager de Skyblock
        if (plugin.getCustomSkyblock() != null && plugin.getCustomSkyblock().getDepositBoxManager() != null) {
            var skyblockDepositBoxManager = plugin.getCustomSkyblock().getDepositBoxManager();
            // Convertir en DepositBoxData de Skyblock
            fr.skyblock.models.DepositBoxData skyblockDepositBox = new fr.skyblock.models.DepositBoxData(
                upgradedDepositBox.getId(),
                upgradedDepositBox.getOwner(),
                upgradedDepositBox.getLocation(),
                upgradedDepositBox.getCapacityLevel(),
                upgradedDepositBox.getMultiplierLevel(),
                upgradedDepositBox.getLastProcessingTime(),
                upgradedDepositBox.getProcessingIntervalMs(),
                upgradedDepositBox.getMaxItemsPerSecond()
            );
            
            // Obtenir l'ID de l'île à cette location
            UUID islandId = skyblockDepositBoxManager.getIslandIdAtLocation(upgradedDepositBox.getLocation());
            if (islandId != null) {
                skyblockDepositBoxManager.saveDepositBox(islandId, skyblockDepositBox);
            }
        }

        return upgradedDepositBox;
    }
    
    // Classe interne pour la caisse de dépôt
    private static class DepositBoxConfig {
        private final int maxItemsPerSecond;
        private final double multiplier;
        private final long price;
        
        public DepositBoxConfig(int maxItemsPerSecond, double multiplier, long price) {
            this.maxItemsPerSecond = maxItemsPerSecond;
            this.multiplier = multiplier;
            this.price = price;
        }
        
        public int getMaxItemsPerSecond() { return maxItemsPerSecond; }
        public double getMultiplier() { return multiplier; }
        public long getPrice() { return price; }
    }
    
    // ==================== INTÉGRATION SKYBLOCK ====================

    /**
     * Obtient l'ID de l'île à une location donnée
     */
    private UUID getIslandIdAtLocation(Location location) {
        if (plugin.getCustomSkyblock() == null) return null;
        
        try {
            Object islandManager = plugin.getCustomSkyblock().getClass()
                .getMethod("getIslandManager")
                .invoke(plugin.getCustomSkyblock());
            
            if (islandManager != null) {
                Object island = islandManager.getClass()
                    .getMethod("getIslandAtLocation", Location.class)
                    .invoke(islandManager, location);
                
                if (island != null) {
                    return (UUID) island.getClass().getMethod("getId").invoke(island);
                }
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la récupération de l'île à la location: " + e.getMessage());
        }
        return null;
    }

    /**
     * Vérifie si on peut placer une caisse de dépôt sur une île
     */
    private boolean canPlaceDepositBoxOnIsland(UUID islandId, int currentDepositBoxCount) {
        if (plugin.getCustomSkyblock() == null) return true;
        
        try {
            Object islandUpgradeManager = plugin.getCustomSkyblock().getClass()
                .getMethod("getIslandUpgradeManager")
                .invoke(plugin.getCustomSkyblock());
            
            if (islandUpgradeManager != null) {
                return (boolean) islandUpgradeManager.getClass()
                    .getMethod("canPlaceDepositBox", UUID.class, int.class)
                    .invoke(islandUpgradeManager, islandId, currentDepositBoxCount);
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la vérification de placement de caisse de dépôt: " + e.getMessage());
        }
        return true;
    }

    /**
     * Obtient la vitesse de transfert des hoppers pour une île
     */
    private double getHopperTransferSpeed(UUID islandId) {
        if (plugin.getCustomSkyblock() == null) return 1.0;
        
        try {
            Object islandUpgradeManager = plugin.getCustomSkyblock().getClass()
                .getMethod("getIslandUpgradeManager")
                .invoke(plugin.getCustomSkyblock());
            
            if (islandUpgradeManager != null) {
                return (double) islandUpgradeManager.getClass()
                    .getMethod("getHopperTransferSpeed", UUID.class)
                    .invoke(islandUpgradeManager, islandId);
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la récupération de la vitesse de transfert: " + e.getMessage());
        }
        return 1.0;
    }

    /**
     * Compte le nombre de caisses de dépôt sur une île
     */
    private int getIslandDepositBoxCount(UUID islandId) {
        int count = 0;
        for (DepositBoxData depositBox : depositBoxCache.values()) {
            UUID depositBoxIslandId = getIslandIdAtLocation(depositBox.getLocation());
            if (islandId.equals(depositBoxIslandId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Vérifie si un joueur peut placer une caisse de dépôt (avec vérification d'île)
     */
    public boolean canPlaceDepositBox(UUID playerId, Location location) {
        // Vérifier les limites de l'île
        UUID islandId = getIslandIdAtLocation(location);
        if (islandId != null) {
            int islandDepositBoxCount = getIslandDepositBoxCount(islandId);
            if (!canPlaceDepositBoxOnIsland(islandId, islandDepositBoxCount)) {
                return false;
            }
        }
        
        return true;
    }

    // Getters
    public Map<String, DepositBoxData> getDepositBoxCache() { return depositBoxCache; }
    public Map<Location, String> getDepositBoxLocations() { return depositBoxLocations; }
    public NamespacedKey getDepositBoxKey() { return depositBoxKey; }
    public NamespacedKey getDepositBoxIdKey() { return depositBoxIdKey; }
    public NamespacedKey getBillKey() { return billKey; }
    public NamespacedKey getBillTierKey() { return billTierKey; }
}
