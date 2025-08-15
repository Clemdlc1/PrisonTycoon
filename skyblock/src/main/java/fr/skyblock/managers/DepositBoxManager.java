package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.DepositBoxData;
import fr.skyblock.models.Island;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager pour gérer les caisses de dépôt dans le système Skyblock
 */
public class DepositBoxManager {

    private final CustomSkyblock plugin;
    private final DatabaseManager databaseManager;
    
    // Cache des caisses de dépôt par île
    private final Map<UUID, Map<String, DepositBoxData>> islandDepositBoxes = new ConcurrentHashMap<>();
    
    public DepositBoxManager(CustomSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        loadAllDepositBoxes();
    }
    
    /**
     * Charge toutes les caisses de dépôt depuis les îles
     */
    private void loadAllDepositBoxes() {
        for (Island island : databaseManager.getAllIslands()) {
            Map<String, DepositBoxData> depositBoxes = island.getDepositBoxes();
            if (!depositBoxes.isEmpty()) {
                islandDepositBoxes.put(island.getId(), new ConcurrentHashMap<>(depositBoxes));
            }
        }
    }
    
    /**
     * Obtient toutes les caisses de dépôt de toutes les îles
     */
    public Map<String, DepositBoxData> getAllDepositBoxes() {
        Map<String, DepositBoxData> allDepositBoxes = new HashMap<>();
        for (Map<String, DepositBoxData> islandBoxes : islandDepositBoxes.values()) {
            allDepositBoxes.putAll(islandBoxes);
        }
        return allDepositBoxes;
    }
    
    /**
     * Obtient les caisses de dépôt d'une île spécifique
     */
    public Map<String, DepositBoxData> getDepositBoxesForIsland(UUID islandId) {
        return islandDepositBoxes.getOrDefault(islandId, new HashMap<>());
    }
    
    /**
     * Obtient les caisses de dépôt d'un joueur spécifique
     */
    public List<DepositBoxData> getDepositBoxesForPlayer(UUID playerId) {
        List<DepositBoxData> playerDepositBoxes = new ArrayList<>();
        for (Map<String, DepositBoxData> islandBoxes : islandDepositBoxes.values()) {
            for (DepositBoxData depositBox : islandBoxes.values()) {
                if (depositBox.getOwner().equals(playerId)) {
                    playerDepositBoxes.add(depositBox);
                }
            }
        }
        return playerDepositBoxes;
    }
    
    /**
     * Sauvegarde une caisse de dépôt spécifique
     */
    public void saveDepositBox(UUID islandId, DepositBoxData depositBox) {
        Island island = databaseManager.loadIsland(islandId);
        if (island != null) {
            island.addDepositBox(depositBox);
            databaseManager.saveIsland(island);
            
            // Mettre à jour le cache
            islandDepositBoxes.computeIfAbsent(islandId, k -> new ConcurrentHashMap<>())
                             .put(depositBox.getId(), depositBox);
        }
    }
    
    /**
     * Supprime une caisse de dépôt
     */
    public void removeDepositBox(UUID islandId, String depositBoxId) {
        Island island = databaseManager.loadIsland(islandId);
        if (island != null) {
            island.removeDepositBox(depositBoxId);
            databaseManager.saveIsland(island);
            
            // Mettre à jour le cache
            Map<String, DepositBoxData> islandBoxes = islandDepositBoxes.get(islandId);
            if (islandBoxes != null) {
                islandBoxes.remove(depositBoxId);
            }
        }
    }
    
    /**
     * Obtient une caisse de dépôt par son ID
     */
    public DepositBoxData getDepositBoxById(String depositBoxId) {
        for (Map<String, DepositBoxData> islandBoxes : islandDepositBoxes.values()) {
            DepositBoxData depositBox = islandBoxes.get(depositBoxId);
            if (depositBox != null) {
                return depositBox;
            }
        }
        return null;
    }
    
    /**
     * Obtient l'île qui contient une caisse de dépôt spécifique
     */
    public Island getIslandForDepositBox(String depositBoxId) {
        for (Map.Entry<UUID, Map<String, DepositBoxData>> entry : islandDepositBoxes.entrySet()) {
            if (entry.getValue().containsKey(depositBoxId)) {
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
     * Vérifie si on peut placer une caisse de dépôt sur une île
     */
    public boolean canPlaceDepositBox(UUID islandId) {
        Island island = databaseManager.loadIsland(islandId);
        return island != null && island.canPlaceDepositBox();
    }
    
    /**
     * Obtient le nombre de caisses de dépôt sur une île
     */
    public int getDepositBoxCount(UUID islandId) {
        Map<String, DepositBoxData> islandBoxes = islandDepositBoxes.get(islandId);
        return islandBoxes != null ? islandBoxes.size() : 0;
    }
    
    /**
     * Met à jour une caisse de dépôt existante
     */
    public void updateDepositBox(UUID islandId, DepositBoxData depositBox) {
        Island island = databaseManager.loadIsland(islandId);
        if (island != null) {
            // Supprimer l'ancienne et ajouter la nouvelle
            island.removeDepositBox(depositBox.getId());
            island.addDepositBox(depositBox);
            databaseManager.saveIsland(island);
            
            // Mettre à jour le cache
            Map<String, DepositBoxData> islandBoxes = islandDepositBoxes.get(islandId);
            if (islandBoxes != null) {
                islandBoxes.put(depositBox.getId(), depositBox);
            }
        }
    }
    
    /**
     * Recharge toutes les caisses de dépôt depuis la base de données
     */
    public void reloadAll() {
        islandDepositBoxes.clear();
        loadAllDepositBoxes();
    }
}
