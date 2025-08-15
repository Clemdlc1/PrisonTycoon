package fr.prisontycoon.test;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PrinterTier;
import fr.prisontycoon.managers.BillStackManager;
import fr.prisontycoon.managers.PrinterManager;
import fr.prisontycoon.managers.DepositBoxManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Classe de test d'intégration pour vérifier le bon fonctionnement
 * des systèmes d'imprimantes et de caisses de dépôt
 */
public class PrinterIntegrationTest {

    private final PrisonTycoon plugin;
    private final PrinterManager printerManager;
    private final DepositBoxManager depositBoxManager;
    private final BillStackManager billStackManager;

    public PrinterIntegrationTest(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.printerManager = plugin.getPrinterManager();
        this.depositBoxManager = plugin.getDepositBoxManager();
        this.billStackManager = printerManager.getBillStackManager();
    }

    /**
     * Test complet du système d'imprimantes et caisses de dépôt
     */
    public void runIntegrationTest(Player testPlayer) {
        plugin.getLogger().info("§6[TEST] Démarrage du test d'intégration...");

        try {
            // Test 1: Vérifier les tiers d'imprimantes
            testPrinterTiers();

            // Test 2: Vérifier la création d'imprimantes
            testPrinterCreation(testPlayer);

            // Test 3: Vérifier le système de stacking de billets
            testBillStacking(testPlayer);

            // Test 4: Vérifier les slots d'imprimantes
            testPrinterSlots(testPlayer);

            // Test 5: Vérifier le système de caisses de dépôt
            testDepositBoxes(testPlayer);

            plugin.getLogger().info("§a[TEST] ✅ Tous les tests d'intégration réussis!");

        } catch (Exception e) {
            plugin.getLogger().severe("§c[TEST] ❌ Échec du test d'intégration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void testPrinterTiers() {
        plugin.getLogger().info("§6[TEST] Test des tiers d'imprimantes...");

        // Vérifier que tous les 50 tiers existent
        for (int tier = 1; tier <= 50; tier++) {
            PrinterTier printerTier = PrinterTier.getByTier(tier);
            if (printerTier == null) {
                throw new RuntimeException("Tier d'imprimante " + tier + " manquant!");
            }

            // Vérifier que le tier a des valeurs cohérentes
            if (printerTier.getBillValue().compareTo(java.math.BigInteger.ZERO) <= 0) {
                throw new RuntimeException("Valeur invalide pour le tier " + tier);
            }

            if (printerTier.getGenerationIntervalSeconds() <= 0) {
                throw new RuntimeException("Intervalle de génération invalide pour le tier " + tier);
            }
        }

        plugin.getLogger().info("§a[TEST] ✅ Tous les 50 tiers d'imprimantes sont valides");
    }

    private void testPrinterCreation(Player testPlayer) {
        plugin.getLogger().info("§6[TEST] Test de création d'imprimantes...");

        Location testLocation = testPlayer.getLocation().add(1, 0, 0);

        // Tester la création d'une imprimante tier 1
        var printerData = printerManager.createPrinter(testPlayer.getUniqueId(), testLocation, 1);
        if (printerData == null) {
            throw new RuntimeException("Impossible de créer une imprimante tier 1");
        }

        // Vérifier que l'imprimante est dans le cache
        if (!printerManager.getPrinterCache().containsKey(printerData.getId())) {
            throw new RuntimeException("Imprimante non trouvée dans le cache");
        }

        // Vérifier que la location est enregistrée
        if (!printerManager.getPrinterLocations().containsKey(testLocation)) {
            throw new RuntimeException("Location d'imprimante non enregistrée");
        }

        plugin.getLogger().info("§a[TEST] ✅ Création d'imprimantes fonctionnelle");
    }

    private void testBillStacking(Player testPlayer) {
        plugin.getLogger().info("§6[TEST] Test du système de stacking de billets...");

        // Créer un billet stacké
        ItemStack stackedBill = billStackManager.createStackedBill(1, 150); // Au-delà de 64
        if (stackedBill == null) {
            throw new RuntimeException("Impossible de créer un billet stacké");
        }

        // Vérifier que c'est bien un billet
        if (!billStackManager.isBill(stackedBill)) {
            throw new RuntimeException("L'item créé n'est pas reconnu comme un billet");
        }

        // Vérifier la taille du stack
        int stackSize = billStackManager.getBillStackSize(stackedBill);
        if (stackSize != 150) {
            throw new RuntimeException("Taille de stack incorrecte: " + stackSize + " au lieu de 150");
        }

        // Vérifier le tier
        int tier = billStackManager.getBillTier(stackedBill);
        if (tier != 1) {
            throw new RuntimeException("Tier de billet incorrect: " + tier + " au lieu de 1");
        }

        plugin.getLogger().info("§a[TEST] ✅ Système de stacking de billets fonctionnel");
    }

    private void testPrinterSlots(Player testPlayer) {
        plugin.getLogger().info("§6[TEST] Test du système de slots d'imprimantes...");

        // Obtenir les slots actuels
        int currentSlots = printerManager.getPlayerPrinterSlots(testPlayer.getUniqueId());

        // Vérifier que c'est au moins 10 (valeur de base)
        if (currentSlots < 10) {
            throw new RuntimeException("Nombre de slots insuffisant: " + currentSlots + " (minimum 10)");
        }

        // Ajouter un bonus via les données du joueur
        var playerData = plugin.getPlayerDataManager().getPlayerData(testPlayer.getUniqueId());
        int oldBonus = playerData.getPrinterSlotBonus();
        playerData.addPrinterSlotBonus(5);

        // Vérifier que les slots ont augmenté
        int newSlots = printerManager.getPlayerPrinterSlots(testPlayer.getUniqueId());
        if (newSlots != currentSlots + 5) {
            throw new RuntimeException("Augmentation de slots incorrecte: " + newSlots + " au lieu de " + (currentSlots + 5));
        }

        // Remettre la valeur d'origine
        playerData.setPrinterSlotBonus(oldBonus);

        plugin.getLogger().info("§a[TEST] ✅ Système de slots d'imprimantes fonctionnel");
    }

    private void testDepositBoxes(Player testPlayer) {
        plugin.getLogger().info("§6[TEST] Test du système de caisses de dépôt...");

        Location testLocation = testPlayer.getLocation().add(-1, 0, 0);

        // Créer une caisse de dépôt
        var depositBoxData = depositBoxManager.createDepositBox(testPlayer.getUniqueId(), testLocation);
        if (depositBoxData == null) {
            throw new RuntimeException("Impossible de créer une caisse de dépôt");
        }

        // Vérifier que la caisse est dans le cache
        if (depositBoxManager.getDepositBoxById(depositBoxData.getId()) == null) {
            throw new RuntimeException("Caisse de dépôt non trouvée dans le cache");
        }

        // Tester la reconnaissance des billets
        ItemStack testBill = billStackManager.createStackedBill(5, 10);
        if (!depositBoxManager.isBill(testBill)) {
            throw new RuntimeException("Billet non reconnu par le DepositBoxManager");
        }

        // Vérifier la valeur calculée
        java.math.BigInteger billValue = depositBoxManager.getBillValue(testBill);
        PrinterTier tier5 = PrinterTier.getByTier(5);
        if (!billValue.equals(tier5.getBillValue())) {
            throw new RuntimeException("Valeur de billet incorrecte");
        }

        plugin.getLogger().info("§a[TEST] ✅ Système de caisses de dépôt fonctionnel");
    }

    /**
     * Test rapide pour valider l'état général du système
     */
    public boolean quickHealthCheck() {
        try {
            // Vérifier que tous les managers sont initialisés
            if (printerManager == null || depositBoxManager == null || billStackManager == null) {
                return false;
            }

            // Vérifier quelques tiers d'imprimantes
            for (int tier : new int[]{1, 10, 25, 50}) {
                if (PrinterTier.getByTier(tier) == null) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}