package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.PrinterData;
import fr.skyblock.models.Island;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager pour gérer les imprimantes dans le système Skyblock
 */
public class PrinterManager {

    private final CustomSkyblock plugin;
    private final DatabaseManager databaseManager;
    
    // Cache des imprimantes par île
    private final Map<UUID, Map<String, PrinterData>> islandPrinters = new ConcurrentHashMap<>();
    
    public PrinterManager(CustomSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        loadAllPrinters();
    }
    
    /**
     * Charge toutes les imprimantes depuis les îles
     */
    private void loadAllPrinters() {
        for (Island island : databaseManager.getAllIslands()) {
            Map<String, PrinterData> printers = island.getPrinters();
            if (!printers.isEmpty()) {
                islandPrinters.put(island.getId(), new ConcurrentHashMap<>(printers));
            }
        }
    }
    
    /**
     * Obtient toutes les imprimantes de toutes les îles
     */
    public Map<String, PrinterData> getAllPrinters() {
        Map<String, PrinterData> allPrinters = new HashMap<>();
        for (Map<String, PrinterData> islandPrinters : islandPrinters.values()) {
            allPrinters.putAll(islandPrinters);
        }
        return allPrinters;
    }
    
    /**
     * Obtient les imprimantes d'une île spécifique
     */
    public Map<String, PrinterData> getPrintersForIsland(UUID islandId) {
        return islandPrinters.getOrDefault(islandId, new HashMap<>());
    }
    
    /**
     * Obtient les imprimantes d'un joueur spécifique
     */
    public List<PrinterData> getPrintersForPlayer(UUID playerId) {
        List<PrinterData> playerPrinters = new ArrayList<>();
        for (Map<String, PrinterData> islandPrinters : islandPrinters.values()) {
            for (PrinterData printer : islandPrinters.values()) {
                if (printer.getOwner().equals(playerId)) {
                    playerPrinters.add(printer);
                }
            }
        }
        return playerPrinters;
    }
    
    /**
     * Sauvegarde une imprimante spécifique
     */
    public void savePrinter(UUID islandId, PrinterData printer) {
        Island island = databaseManager.loadIsland(islandId);
        if (island != null) {
            island.addPrinter(printer);
            databaseManager.saveIsland(island);
            
            // Mettre à jour le cache
            islandPrinters.computeIfAbsent(islandId, k -> new ConcurrentHashMap<>())
                         .put(printer.getId(), printer);
        }
    }
    
    /**
     * Supprime une imprimante
     */
    public void removePrinter(UUID islandId, String printerId) {
        Island island = databaseManager.loadIsland(islandId);
        if (island != null) {
            island.removePrinter(printerId);
            databaseManager.saveIsland(island);
            
            // Mettre à jour le cache
            Map<String, PrinterData> islandPrinters = this.islandPrinters.get(islandId);
            if (islandPrinters != null) {
                islandPrinters.remove(printerId);
            }
        }
    }
    
    /**
     * Obtient une imprimante par son ID
     */
    public PrinterData getPrinterById(String printerId) {
        for (Map<String, PrinterData> islandPrinters : islandPrinters.values()) {
            PrinterData printer = islandPrinters.get(printerId);
            if (printer != null) {
                return printer;
            }
        }
        return null;
    }
    
    /**
     * Obtient l'île qui contient une imprimante spécifique
     */
    public Island getIslandForPrinter(String printerId) {
        for (Map.Entry<UUID, Map<String, PrinterData>> entry : islandPrinters.entrySet()) {
            if (entry.getValue().containsKey(printerId)) {
                return databaseManager.loadIsland(entry.getKey());
            }
        }
        return null;
    }
    
    /**
     * Obtient l'ID de l'île à une location donnée
     */
    public UUID getIslandIdAtLocation(Location location) {
        for (Island island : databaseManager.getAllIslands()) {
            if (island.getCenter() != null && island.getCenter().getWorld().equals(location.getWorld())) {
                double distance = island.getCenter().distance(location);
                if (distance <= island.getSize()) {
                    return island.getId();
                }
            }
        }
        return null;
    }
    
    /**
     * Vérifie si on peut placer une imprimante sur une île
     */
    public boolean canPlacePrinter(UUID islandId) {
        Island island = databaseManager.loadIsland(islandId);
        return island != null && island.canPlacePrinter();
    }
    
    /**
     * Obtient le nombre d'imprimantes sur une île
     */
    public int getPrinterCount(UUID islandId) {
        Map<String, PrinterData> islandPrinters = this.islandPrinters.get(islandId);
        return islandPrinters != null ? islandPrinters.size() : 0;
    }
    
    /**
     * Met à jour une imprimante existante
     */
    public void updatePrinter(UUID islandId, PrinterData printer) {
        Island island = databaseManager.loadIsland(islandId);
        if (island != null) {
            // Supprimer l'ancienne et ajouter la nouvelle
            island.removePrinter(printer.getId());
            island.addPrinter(printer);
            databaseManager.saveIsland(island);
            
            // Mettre à jour le cache
            Map<String, PrinterData> islandPrinters = this.islandPrinters.get(islandId);
            if (islandPrinters != null) {
                islandPrinters.put(printer.getId(), printer);
            }
        }
    }
    
    /**
     * Recharge toutes les imprimantes depuis la base de données
     */
    public void reloadAll() {
        islandPrinters.clear();
        loadAllPrinters();
    }
}
