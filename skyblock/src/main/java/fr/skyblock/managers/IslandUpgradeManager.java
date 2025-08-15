package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.World;

import java.util.UUID;

/**
 * Manager pour gérer les améliorations d'île liées au système d'imprimantes
 * (Logique métier uniquement, pas de gestion de base de données)
 */
public class IslandUpgradeManager {

    private final CustomSkyblock plugin;
    private final DatabaseManager databaseManager;

    public IslandUpgradeManager(CustomSkyblock plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
    }

    /**
     * Vérifie si un joueur peut placer une imprimante sur une île
     */
    public boolean canPlacePrinter(UUID islandId, int currentPrinterCount) {
        Island island = databaseManager.loadIsland(islandId);
        return island != null && currentPrinterCount < island.getMaxPrinters();
    }

    /**
     * Vérifie si un joueur peut placer une caisse de dépôt sur une île
     */
    public boolean canPlaceDepositBox(UUID islandId, int currentDepositBoxCount) {
        Island island = databaseManager.loadIsland(islandId);
        return island != null && currentDepositBoxCount < island.getMaxDepositBoxes();
    }

    /**
     * Obtient le multiplicateur de vitesse de génération des imprimantes pour une île
     */
    public double getPrinterGenerationSpeedMultiplier(UUID islandId) {
        Island island = databaseManager.loadIsland(islandId);
        return island != null ? island.getPrinterGenerationSpeed() : 1.0;
    }

    /**
     * Obtient la vitesse de transfert des hoppers pour une île
     */
    public double getHopperTransferSpeed(UUID islandId) {
        Island island = databaseManager.loadIsland(islandId);
        return island != null ? island.getHopperTransferSpeed() : 1.0;
    }

    /**
     * Met à jour les améliorations d'une île
     */
    public void updateIslandUpgrades(UUID islandId, int maxDepositBoxes, int maxHoppers, 
                                   double hopperTransferSpeed, int maxPrinters, double printerGenerationSpeed) {
        Island island = databaseManager.loadIsland(islandId);
        if (island != null) {
            island.setMaxDepositBoxes(maxDepositBoxes);
            island.setMaxHoppers(maxHoppers);
            island.setHopperTransferSpeed(hopperTransferSpeed);
            island.setMaxPrinters(maxPrinters);
            island.setPrinterGenerationSpeed(printerGenerationSpeed);
            databaseManager.saveIsland(island);
        }
    }

    /**
     * Vérifie si un joueur est connecté et présent sur l'île
     */
    public boolean isPlayerOnIsland(Player player) {
        Island island = getIslandByPlayer(player.getUniqueId());
        if (island == null) return false;
        
        // Vérifier si le joueur est dans le bon monde et dans la zone de l'île
        return isPlayerInIslandBounds(player, island);
    }

    /**
     * Vérifie si au moins un joueur est présent sur une île donnée
     */
    public boolean isAnyPlayerOnIsland(UUID islandId) {
        Island island = databaseManager.loadIsland(islandId);
        if (island == null) return false;
        
        // Vérifier le propriétaire
        Player owner = plugin.getServer().getPlayer(island.getOwner());
        if (owner != null && isPlayerInIslandBounds(owner, island)) {
            return true;
        }
        
        // Vérifier les membres
        for (UUID memberId : island.getMembers()) {
            Player member = plugin.getServer().getPlayer(memberId);
            if (member != null && isPlayerInIslandBounds(member, island)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Vérifie si un joueur est dans les limites d'une île
     */
    private boolean isPlayerInIslandBounds(Player player, Island island) {
        if (player == null || island == null || island.getCenter() == null) return false;
        
        Location center = island.getCenter();
        Location playerLoc = player.getLocation();
        
        // Vérifier le monde
        if (!playerLoc.getWorld().equals(center.getWorld())) return false;
        
        // Vérifier la distance
        double distance = playerLoc.distance(center);
        return distance <= island.getSize();
    }

    /**
     * Obtient l'île d'un joueur (propriétaire ou membre)
     */
    private Island getIslandByPlayer(UUID playerId) {
        for (Island island : databaseManager.getAllIslands()) {
            if (island.getOwner().equals(playerId) || island.getMembers().contains(playerId)) {
                return island;
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
     * Obtient les améliorations d'une île sous forme de données structurées
     */
    public IslandUpgradeData getIslandUpgrades(UUID islandId) {
        Island island = databaseManager.loadIsland(islandId);
        if (island == null) {
            return new IslandUpgradeData(islandId, 1, 5, 1.0, 10, 1.0);
        }
        
        return new IslandUpgradeData(
            islandId,
            island.getMaxDepositBoxes(),
            island.getMaxHoppers(),
            island.getHopperTransferSpeed(),
            island.getMaxPrinters(),
            island.getPrinterGenerationSpeed()
        );
    }

    /**
     * Classe pour stocker les données d'amélioration (pour compatibilité)
     */
    public static class IslandUpgradeData {
        private final UUID islandId;
        private int maxDepositBoxes;
        private int maxHoppers;
        private double hopperTransferSpeed;
        private int maxPrinters;
        private double printerGenerationSpeed;

        public IslandUpgradeData(UUID islandId, int maxDepositBoxes, int maxHoppers, 
                               double hopperTransferSpeed, int maxPrinters, double printerGenerationSpeed) {
            this.islandId = islandId;
            this.maxDepositBoxes = Math.max(1, maxDepositBoxes);
            this.maxHoppers = Math.max(5, maxHoppers);
            this.hopperTransferSpeed = Math.max(1.0, hopperTransferSpeed);
            this.maxPrinters = Math.max(10, maxPrinters);
            this.printerGenerationSpeed = Math.max(1.0, printerGenerationSpeed);
        }

        // Getters
        public UUID getIslandId() { return islandId; }
        public int getMaxDepositBoxes() { return maxDepositBoxes; }
        public int getMaxHoppers() { return maxHoppers; }
        public double getHopperTransferSpeed() { return hopperTransferSpeed; }
        public int getMaxPrinters() { return maxPrinters; }
        public double getPrinterGenerationSpeed() { return printerGenerationSpeed; }

        // Setters
        public void setMaxDepositBoxes(int maxDepositBoxes) { this.maxDepositBoxes = Math.max(1, maxDepositBoxes); }
        public void setMaxHoppers(int maxHoppers) { this.maxHoppers = Math.max(5, maxHoppers); }
        public void setHopperTransferSpeed(double hopperTransferSpeed) { this.hopperTransferSpeed = Math.max(1.0, hopperTransferSpeed); }
        public void setMaxPrinters(int maxPrinters) { this.maxPrinters = Math.max(10, maxPrinters); }
        public void setPrinterGenerationSpeed(double printerGenerationSpeed) { this.printerGenerationSpeed = Math.max(1.0, printerGenerationSpeed); }
    }
}
