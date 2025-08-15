package fr.prisontycoon.managers;

import com.google.gson.Gson;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PrinterData;
import fr.prisontycoon.data.PrinterTier;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Gestionnaire des imprimantes à argent
 */
public class PrinterManager {

    private final PrisonTycoon plugin;
    private final DatabaseManager databaseManager;
    private final BillStackManager billStackManager;
    private final Gson gson = new Gson();
    private final NamespacedKey printerKey;
    private final NamespacedKey printerTierKey;
    private final NamespacedKey printerIdKey;
    
    // Cache des imprimantes
    private final Map<String, PrinterData> printerCache = new ConcurrentHashMap<>();
    private final Map<Location, String> printerLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerPrinters = new ConcurrentHashMap<>();
    
    private final Map<String, ArmorStand> printerNametags = new HashMap<>();
    private final Map<UUID, String> nearbyPlayers = new HashMap<>();
    
    // Configuration
    private final int BASE_PRINTER_SLOTS = 10;
    private final int SLOTS_PER_PRESTIGE = 1;
    private final int SLOTS_PER_BANK_TYPE = 5;
    
    public PrinterManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.billStackManager = new BillStackManager(plugin);
        this.printerKey = new NamespacedKey(plugin, "printer");
        this.printerTierKey = new NamespacedKey(plugin, "printer_tier");
        this.printerIdKey = new NamespacedKey(plugin, "printer_id");
        
        initializeCaches();
        startGenerationTask();
        initializeNametagSystem();
        
        // Enregistrer le BillStackManager comme listener
        plugin.getServer().getPluginManager().registerEvents(billStackManager, plugin);
        
        plugin.getPluginLogger().info("§aPrinterManager initialisé.");
    }
    
    private void initializeCaches() {
        // Initialiser les caches à partir du PrinterManager de Skyblock
        if (plugin.getCustomSkyblock() != null) {
            try {
                Object skyblockPrinterManager = plugin.getCustomSkyblock().getClass()
                    .getMethod("getPrinterManager")
                    .invoke(plugin.getCustomSkyblock());
                
                if (skyblockPrinterManager != null) {
                    Object printers = skyblockPrinterManager.getClass()
                        .getMethod("getAllPrinters")
                        .invoke(skyblockPrinterManager);
                    
                    if (printers instanceof Map) {
                        Map<?, ?> printersMap = (Map<?, ?>) printers;
                        for (Map.Entry<?, ?> entry : printersMap.entrySet()) {
                            Object skyblockPrinter = entry.getValue();
                            // Convertir en PrinterData de PrisonTycoon
                            PrinterData printer = new PrinterData(
                                (String) skyblockPrinter.getClass().getMethod("getId").invoke(skyblockPrinter),
                                (UUID) skyblockPrinter.getClass().getMethod("getOwner").invoke(skyblockPrinter),
                                (Location) skyblockPrinter.getClass().getMethod("getLocation").invoke(skyblockPrinter),
                                ((Number) skyblockPrinter.getClass().getMethod("getTier").invoke(skyblockPrinter)).intValue(),
                                ((Number) skyblockPrinter.getClass().getMethod("getLastGenerationTime").invoke(skyblockPrinter)).longValue(),
                                ((Number) skyblockPrinter.getClass().getMethod("getGenerationIntervalSeconds").invoke(skyblockPrinter)).intValue(),
                                (java.math.BigInteger) skyblockPrinter.getClass().getMethod("getBillValue").invoke(skyblockPrinter),
                                org.bukkit.Material.valueOf((String) skyblockPrinter.getClass().getMethod("getBillMaterial").invoke(skyblockPrinter)),
                                (String) skyblockPrinter.getClass().getMethod("getBillName").invoke(skyblockPrinter),
                                (String) skyblockPrinter.getClass().getMethod("getBillLore").invoke(skyblockPrinter)
                            );
                            
                            printerCache.put(printer.getId(), printer);
                            printerLocations.put(printer.getLocation(), printer.getId());
                            playerPrinters.computeIfAbsent(printer.getOwner(), k -> ConcurrentHashMap.newKeySet()).add(printer.getId());
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors de l'initialisation des caches d'imprimantes: " + e.getMessage());
            }
        }
    }
    
    private void loadPrinters() {
        String query = "SELECT * FROM printers";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            
            while (rs.next()) {
                String id = rs.getString("id");
                UUID owner = UUID.fromString(rs.getString("owner"));
                Location location = gson.fromJson(rs.getString("location"), Location.class);
                int tier = rs.getInt("tier");
                long lastGenerationTime = rs.getLong("last_generation_time");
                int generationIntervalSeconds = rs.getInt("generation_interval_seconds");
                String billValueStr = rs.getString("bill_value");
                Material billMaterial = Material.valueOf(rs.getString("bill_material"));
                String billName = rs.getString("bill_name");
                String billLore = rs.getString("bill_lore");
                
                PrinterData printerData = new PrinterData(id, owner, location, tier, lastGenerationTime,
                        generationIntervalSeconds, new java.math.BigInteger(billValueStr), billMaterial, billName, billLore);
                
                printerCache.put(id, printerData);
                printerLocations.put(location, id);
                playerPrinters.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(id);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur lors du chargement des imprimantes: " + e.getMessage());
        }
    }
    
    /**
     * Sauvegarde toutes les imprimantes via le PrinterManager de Skyblock
     */
    public void saveAll() {
        if (plugin.getCustomSkyblock() != null) {
            try {
                Object skyblockPrinterManager = plugin.getCustomSkyblock().getClass()
                    .getMethod("getPrinterManager")
                    .invoke(plugin.getCustomSkyblock());
                
                if (skyblockPrinterManager != null) {
                    // Sauvegarder via le DatabaseManager de Skyblock
                    Object skyblockDb = plugin.getCustomSkyblock().getClass()
                        .getMethod("getDatabaseManager")
                        .invoke(plugin.getCustomSkyblock());
                    
                    if (skyblockDb != null) {
                        skyblockDb.getClass().getMethod("saveAll").invoke(skyblockDb);
                    }
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors de la sauvegarde des imprimantes: " + e.getMessage());
            }
        }
    }
    
    /**
     * Crée une nouvelle imprimante
     */
    public PrinterData createPrinter(UUID owner, Location location, int tier) {
        PrinterTier printerTier = PrinterTier.getByTier(tier);
        if (printerTier == null) {
            return null;
        }
        
        String id = UUID.randomUUID().toString();
        PrinterData printerData = new PrinterData(id, owner, location, tier, System.currentTimeMillis(),
                printerTier.getGenerationIntervalSeconds(), printerTier.getBillValue(),
                printerTier.getBillMaterial(), printerTier.getBillName(), printerTier.getBillLore());
        
        printerCache.put(id, printerData);
        printerLocations.put(location, id);
        playerPrinters.computeIfAbsent(owner, k -> ConcurrentHashMap.newKeySet()).add(id);
        
        // Sauvegarder via le PrinterManager de Skyblock
        if (plugin.getCustomSkyblock() != null) {
            try {
                Object skyblockPrinterManager = plugin.getCustomSkyblock().getClass()
                    .getMethod("getPrinterManager")
                    .invoke(plugin.getCustomSkyblock());
                
                if (skyblockPrinterManager != null) {
                    // Convertir en PrinterData de Skyblock
                    Object skyblockPrinter = skyblockPrinterManager.getClass()
                        .getClassLoader()
                        .loadClass("fr.skyblock.models.PrinterData")
                        .getConstructor(String.class, UUID.class, Location.class, int.class, 
                                       long.class, int.class, java.math.BigInteger.class, 
                                       String.class, String.class, String.class)
                        .newInstance(printerData.getId(), printerData.getOwner(), 
                                   printerData.getLocation(), printerData.getTier(),
                                   printerData.getLastGenerationTime(), 
                                   printerData.getGenerationIntervalSeconds(),
                                   printerData.getBillValue(), 
                                   printerData.getBillMaterial().name(),
                                   printerData.getBillName(), printerData.getBillLore());
                    
                    // Obtenir l'ID de l'île à cette location
                    UUID islandId = (UUID) skyblockPrinterManager.getClass()
                        .getMethod("getIslandIdAtLocation", Location.class)
                        .invoke(skyblockPrinterManager, location);
                    
                    if (islandId != null) {
                        skyblockPrinterManager.getClass()
                            .getMethod("savePrinter", UUID.class, Object.class)
                            .invoke(skyblockPrinterManager, islandId, skyblockPrinter);
                    }
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors de la sauvegarde de l'imprimante: " + e.getMessage());
            }
        }
        
        return printerData;
    }
    

    
    /**
     * Supprime une imprimante
     */
    public boolean removePrinter(String printerId) {
        PrinterData printer = printerCache.get(printerId);
        if (printer == null) {
            return false;
        }
        
        printerCache.remove(printerId);
        printerLocations.remove(printer.getLocation());
        playerPrinters.get(printer.getOwner()).remove(printerId);
        
        // Supprimer le nametag associé
        if (printerNametags.containsKey(printerId)) {
            ArmorStand nametag = printerNametags.get(printerId);
            if (nametag != null && !nametag.isDead()) {
                nametag.remove();
            }
            printerNametags.remove(printerId);
        }
        
        // Supprimer via le PrinterManager de Skyblock
        if (plugin.getCustomSkyblock() != null) {
            try {
                Object skyblockPrinterManager = plugin.getCustomSkyblock().getClass()
                    .getMethod("getPrinterManager")
                    .invoke(plugin.getCustomSkyblock());
                
                if (skyblockPrinterManager != null) {
                    // Obtenir l'ID de l'île à cette location
                    UUID islandId = (UUID) skyblockPrinterManager.getClass()
                        .getMethod("getIslandIdAtLocation", Location.class)
                        .invoke(skyblockPrinterManager, printer.getLocation());
                    
                    if (islandId != null) {
                        skyblockPrinterManager.getClass()
                            .getMethod("removePrinter", UUID.class, String.class)
                            .invoke(skyblockPrinterManager, islandId, printerId);
                    }
                }
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors de la suppression de l'imprimante: " + e.getMessage());
            }
        }
        
        return true;
    }
    
    /**
     * Vérifie si un item est une imprimante
     */
    public boolean isPrinter(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(printerKey, PersistentDataType.BOOLEAN) &&
                meta.getPersistentDataContainer().has(printerTierKey, PersistentDataType.INTEGER);
    }
    
    /**
     * Obtient le tier d'une imprimante depuis un item
     */
    public int getPrinterTier(ItemStack item) {
        if (!isPrinter(item)) return 0;
        return item.getItemMeta().getPersistentDataContainer().get(printerTierKey, PersistentDataType.INTEGER);
    }
    
    /**
     * Crée un item d'imprimante
     */
    public ItemStack createPrinterItem(int tier) {
        PrinterTier printerTier = PrinterTier.getByTier(tier);
        if (printerTier == null) return null;
        
        ItemStack item = new ItemStack(Material.DISPENSER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6§lImprimante Tier " + tier);
        meta.setLore(Arrays.asList(
                "§7Génère des billets de §e" + NumberFormatter.format(printerTier.getBillValue().longValue()) + " coins",
                "§7Intervalle: §e" + printerTier.getGenerationIntervalSeconds() + " secondes",
                "",
                "§eClic-droit pour placer"
        ));
        
        meta.getPersistentDataContainer().set(printerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(printerTierKey, PersistentDataType.INTEGER, tier);
        
        item.setItemMeta(meta);
        return item;
    }
    
    /**
     * Obtient le nombre de slots d'imprimantes d'un joueur
     */
    public int getPlayerPrinterSlots(UUID playerId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        int slots = BASE_PRINTER_SLOTS;
        
        // Bonus par prestige
        slots += playerData.getPrestigeLevel() * SLOTS_PER_PRESTIGE;
        
        // Bonus par type de banque
        if (playerData.getBankType() != fr.prisontycoon.data.BankType.NONE) {
            slots += SLOTS_PER_BANK_TYPE;
        }
        
        // Bonus par items physiques (vouchers de slots)
        slots += playerData.getPrinterSlotBonus();
        
        return slots;
    }
    
    /**
     * Vérifie si un joueur peut placer une imprimante
     */
    public boolean canPlacePrinter(UUID playerId, Location location) {
        // Vérifier les slots individuels du joueur
        int currentPrinters = playerPrinters.getOrDefault(playerId, Collections.emptySet()).size();
        int maxSlots = getPlayerPrinterSlots(playerId);
        if (currentPrinters >= maxSlots) {
            return false;
        }
        
        // Vérifier les limites de l'île
        UUID islandId = getIslandIdAtLocation(location);
        if (islandId != null) {
            int islandPrinterCount = getIslandPrinterCount(islandId);
            if (!canPlacePrinterOnIsland(islandId, islandPrinterCount)) {
                return false;
            }
        }
        
        return true;
    }

    /**
     * Vérifie si un joueur peut placer une imprimante (sans vérification d'île)
     */
    public boolean canPlacePrinter(UUID playerId) {
        int currentPrinters = playerPrinters.getOrDefault(playerId, Collections.emptySet()).size();
        int maxSlots = getPlayerPrinterSlots(playerId);
        return currentPrinters < maxSlots;
    }
    
    /**
     * Obtient les imprimantes d'un joueur
     */
    public Set<PrinterData> getPlayerPrinters(UUID playerId) {
        Set<String> printerIds = playerPrinters.get(playerId);
        if (printerIds == null) return Collections.emptySet();
        
        Set<PrinterData> printers = new HashSet<>();
        for (String id : printerIds) {
            PrinterData printer = printerCache.get(id);
            if (printer != null) {
                printers.add(printer);
            }
        }
        return printers;
    }
    
    /**
     * Démarre la tâche de génération des billets
     */
    private void startGenerationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (PrinterData printer : printerCache.values()) {
                    if (printer.shouldGenerateBill()) {
                        generateBill(printer);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Vérification toutes les secondes
    }
    
    /**
     * Génère un billet pour une imprimante
     */
    private void generateBill(PrinterData printer) {
        // Obtenir l'ID de l'île à la location de l'imprimante
        UUID islandId = getIslandIdAtLocation(printer.getLocation());
        if (islandId == null) return;
        
        // Vérifier si un joueur est sur l'île
        if (!isAnyPlayerOnIsland(islandId)) {
            return; // Aucun joueur sur l'île, pas de génération
        }
        
        // Obtenir le multiplicateur de vitesse de génération
        double speedMultiplier = getPrinterGenerationSpeedMultiplier(islandId);
        
        // Utiliser le BillStackManager pour spawner le billet avec empilement automatique
        Location spawnLocation = printer.getLocation().clone();
        billStackManager.spawnStackedBill(spawnLocation, printer.getTier());
        
        // Mettre à jour le temps de génération avec le multiplicateur de vitesse
        long adjustedInterval = (long) (printer.getGenerationIntervalSeconds() * 1000 / speedMultiplier);
        long currentTime = System.currentTimeMillis();
        long lastGeneration = printer.getLastGenerationTime();
        
        if (currentTime - lastGeneration >= adjustedInterval) {
            PrinterData updatedPrinter = printer.withUpdatedGenerationTime();
            printerCache.put(printer.getId(), updatedPrinter);
            
            // Mettre à jour via le PrinterManager de Skyblock
            if (plugin.getCustomSkyblock() != null) {
                try {
                    Object skyblockPrinterManager = plugin.getCustomSkyblock().getClass()
                        .getMethod("getPrinterManager")
                        .invoke(plugin.getCustomSkyblock());
                    
                    if (skyblockPrinterManager != null) {
                        // Convertir en PrinterData de Skyblock
                        Object skyblockPrinter = skyblockPrinterManager.getClass()
                            .getClassLoader()
                            .loadClass("fr.skyblock.models.PrinterData")
                            .getConstructor(String.class, UUID.class, Location.class, int.class, 
                                           long.class, int.class, java.math.BigInteger.class, 
                                           String.class, String.class, String.class)
                            .newInstance(updatedPrinter.getId(), updatedPrinter.getOwner(), 
                                       updatedPrinter.getLocation(), updatedPrinter.getTier(),
                                       updatedPrinter.getLastGenerationTime(), 
                                       updatedPrinter.getGenerationIntervalSeconds(),
                                       updatedPrinter.getBillValue(), 
                                       updatedPrinter.getBillMaterial().name(),
                                       updatedPrinter.getBillName(), updatedPrinter.getBillLore());
                        
                        // Obtenir l'ID de l'île à cette location
                        UUID printerIslandId = (UUID) skyblockPrinterManager.getClass()
                            .getMethod("getIslandIdAtLocation", Location.class)
                            .invoke(skyblockPrinterManager, printer.getLocation());
                        
                        if (printerIslandId != null) {
                            skyblockPrinterManager.getClass()
                                .getMethod("updatePrinter", UUID.class, Object.class)
                                .invoke(skyblockPrinterManager, printerIslandId, skyblockPrinter);
                        }
                    }
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Erreur lors de la mise à jour de l'imprimante: " + e.getMessage());
                }
            }
            
            // Effet sonore
            printer.getLocation().getWorld().playSound(printer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
        }
    }
    
    /**
     * Initialise le système de nametags pour les imprimantes
     */
    public void initializeNametagSystem() {
        // Tâche pour gérer l'affichage des nametags
        new BukkitRunnable() {
            @Override
            public void run() {
                updatePrinterNametags();
            }
        }.runTaskTimer(plugin, 20L, 20L); // Toutes les secondes
    }
    
    /**
     * Met à jour l'affichage des nametags des imprimantes
     */
    private void updatePrinterNametags() {
        // Supprimer les nametags des imprimantes qui n'existent plus
        printerNametags.entrySet().removeIf(entry -> {
            if (!printerCache.containsKey(entry.getKey())) {
                entry.getValue().remove();
                return true;
            }
            return false;
        });
        
        // Vérifier les joueurs à proximité des imprimantes
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String nearestPrinterId = null;
            double nearestDistance = 5.0; // Distance maximale d'affichage
            
            for (PrinterData printer : printerCache.values()) {
                // Vérifier que les mondes sont les mêmes
                if (!player.getWorld().equals(printer.getLocation().getWorld())) {
                    continue;
                }
                double distance = player.getLocation().distance(printer.getLocation());
                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearestPrinterId = printer.getId();
                }
            }
            
            // Mettre à jour le nametag le plus proche
            String currentNearest = nearbyPlayers.get(player.getUniqueId());
            if (!Objects.equals(currentNearest, nearestPrinterId)) {
                // Supprimer l'ancien nametag si différent
                if (currentNearest != null && printerNametags.containsKey(currentNearest)) {
                    ArmorStand oldNametag = printerNametags.get(currentNearest);
                    if (oldNametag != null && !oldNametag.isDead()) {
                        oldNametag.remove();
                    }
                    printerNametags.remove(currentNearest);
                }
                
                // Créer le nouveau nametag si nécessaire
                if (nearestPrinterId != null) {
                    createPrinterNametag(nearestPrinterId, player);
                }
                
                nearbyPlayers.put(player.getUniqueId(), nearestPrinterId);
            }
        }
    }
    
    /**
     * Crée un nametag pour une imprimante visible par un joueur spécifique
     */
    private void createPrinterNametag(String printerId, Player player) {
        PrinterData printer = printerCache.get(printerId);
        if (printer == null) return;
        
        // Supprimer l'ancien nametag s'il existe
        if (printerNametags.containsKey(printerId)) {
            ArmorStand oldNametag = printerNametags.get(printerId);
            if (oldNametag != null && !oldNametag.isDead()) {
                oldNametag.remove();
            }
        }
        
        // Créer le nouveau nametag
        Location nametagLocation = printer.getLocation().clone().add(0.5, 2.5, 0.5);
        ArmorStand nametag = (ArmorStand) printer.getLocation().getWorld().spawnEntity(nametagLocation, EntityType.ARMOR_STAND);
        
        nametag.setVisible(false);
        nametag.setGravity(false);
        nametag.setCanPickupItems(false);
        nametag.setCustomNameVisible(true);
        nametag.setCustomName(formatPrinterNametag(printer));
        nametag.setMarker(true);
        nametag.setInvulnerable(true);
        nametag.setCollidable(false);
        
        printerNametags.put(printerId, nametag);
    }
    
    /**
     * Formate le nametag d'une imprimante
     */
    private String formatPrinterNametag(PrinterData printer) {
        PrinterTier tier = PrinterTier.getByTier(printer.getTier());
        if (tier == null) return "§cImprimante Inconnue";
        
        return String.format("§6§lImprimante Tier %d\n§7Valeur: §e%s coins\n§7Intervalle: §e%d secondes",
            printer.getTier(),
            NumberFormatter.format(printer.getBillValue().longValue()),
            printer.getGenerationIntervalSeconds()
        );
    }
    
    /**
     * Vérifie si un joueur est sur son île (intégration Skyblock)
     */
    private boolean isPlayerOnIsland(Player player) {
        if (plugin.getCustomSkyblock() == null) return false;
        
        try {
            Object islandUpgradeManager = plugin.getCustomSkyblock().getClass()
                .getMethod("getIslandUpgradeManager")
                .invoke(plugin.getCustomSkyblock());
            
            if (islandUpgradeManager != null) {
                return (boolean) islandUpgradeManager.getClass()
                    .getMethod("isPlayerOnIsland", Player.class)
                    .invoke(islandUpgradeManager, player);
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la vérification de l'île pour " + player.getName() + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Vérifie si au moins un joueur est sur l'île (intégration Skyblock)
     */
    private boolean isAnyPlayerOnIsland(UUID islandId) {
        if (plugin.getCustomSkyblock() == null) return false;
        
        try {
            Object islandUpgradeManager = plugin.getCustomSkyblock().getClass()
                .getMethod("getIslandUpgradeManager")
                .invoke(plugin.getCustomSkyblock());
            
            if (islandUpgradeManager != null) {
                // Utiliser la méthode avec le bon nom
                return (boolean) islandUpgradeManager.getClass()
                    .getMethod("isAnyPlayerOnIsland", UUID.class)
                    .invoke(islandUpgradeManager, islandId);
            }
        } catch (Exception e) {
            // Fallback : vérifier manuellement si un joueur est sur l'île
            try {
                Object islandManager = plugin.getCustomSkyblock().getClass()
                    .getMethod("getIslandManager")
                    .invoke(plugin.getCustomSkyblock());
                
                if (islandManager != null) {
                    Object island = islandManager.getClass()
                        .getMethod("getIslandById", UUID.class)
                        .invoke(islandManager, islandId);
                    
                    if (island != null) {
                        // Vérifier si l'owner est en ligne
                        UUID ownerId = (UUID) island.getClass().getMethod("getOwner").invoke(island);
                        if (plugin.getServer().getPlayer(ownerId) != null) {
                            return true;
                        }
                        
                        // Vérifier si un membre est en ligne
                        Object members = island.getClass().getMethod("getMembers").invoke(island);
                        if (members instanceof java.util.Set) {
                            for (Object memberId : (java.util.Set<?>) members) {
                                if (plugin.getServer().getPlayer((UUID) memberId) != null) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            } catch (Exception fallbackException) {
                plugin.getPluginLogger().warning("Erreur lors de la vérification des joueurs sur l'île " + islandId + " (fallback): " + fallbackException.getMessage());
            }
        }
        return false;
    }

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
     * Obtient le multiplicateur de vitesse de génération des imprimantes pour une île
     */
    private double getPrinterGenerationSpeedMultiplier(UUID islandId) {
        if (plugin.getCustomSkyblock() == null) return 1.0;
        
        try {
            Object islandUpgradeManager = plugin.getCustomSkyblock().getClass()
                .getMethod("getIslandUpgradeManager")
                .invoke(plugin.getCustomSkyblock());
            
            if (islandUpgradeManager != null) {
                return (double) islandUpgradeManager.getClass()
                    .getMethod("getPrinterGenerationSpeedMultiplier", UUID.class)
                    .invoke(islandUpgradeManager, islandId);
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la récupération du multiplicateur de vitesse: " + e.getMessage());
        }
        return 1.0;
    }

    /**
     * Compte le nombre d'imprimantes sur une île
     */
    private int getIslandPrinterCount(UUID islandId) {
        int count = 0;
        for (PrinterData printer : printerCache.values()) {
            UUID printerIslandId = getIslandIdAtLocation(printer.getLocation());
            if (islandId.equals(printerIslandId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Vérifie si on peut placer une imprimante sur une île
     */
    private boolean canPlacePrinterOnIsland(UUID islandId, int currentPrinterCount) {
        if (plugin.getCustomSkyblock() == null) return true;
        
        try {
            Object islandUpgradeManager = plugin.getCustomSkyblock().getClass()
                .getMethod("getIslandUpgradeManager")
                .invoke(plugin.getCustomSkyblock());
            
            if (islandUpgradeManager != null) {
                return (boolean) islandUpgradeManager.getClass()
                    .getMethod("canPlacePrinter", UUID.class, int.class)
                    .invoke(islandUpgradeManager, islandId, currentPrinterCount);
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la vérification de placement d'imprimante: " + e.getMessage());
        }
        return true;
    }
    
    /**
     * Nettoie tous les nametags lors de la désactivation du plugin
     */
    public void cleanupNametags() {
        for (ArmorStand nametag : printerNametags.values()) {
            if (nametag != null && !nametag.isDead()) {
                nametag.remove();
            }
        }
        printerNametags.clear();
        nearbyPlayers.clear();
    }
    
    // Getters
    public Map<String, PrinterData> getPrinterCache() { return printerCache; }
    public Map<Location, String> getPrinterLocations() { return printerLocations; }
    public NamespacedKey getPrinterKey() { return printerKey; }
    public NamespacedKey getPrinterTierKey() { return printerTierKey; }
    public NamespacedKey getPrinterIdKey() { return printerIdKey; }
    public BillStackManager getBillStackManager() { return billStackManager; }
}
