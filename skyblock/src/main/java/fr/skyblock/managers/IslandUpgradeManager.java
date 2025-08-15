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
