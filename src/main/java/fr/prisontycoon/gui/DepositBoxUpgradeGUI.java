package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.DepositBoxData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.DepositBoxManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Interface graphique pour les améliorations de caisses de dépôt
 */
public class DepositBoxUpgradeGUI {

    private final PrisonTycoon plugin;
    private final GUIManager guiManager;

    // Slots pour les améliorations
    private static final int CAPACITY_UPGRADE_SLOT = 10;
    private static final int MULTIPLIER_UPGRADE_SLOT = 12;
    private static final int INFO_SLOT = 14;
    private static final int CLOSE_SLOT = 16;

    public DepositBoxUpgradeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    /**
     * Ouvre le menu d'amélioration pour une caisse de dépôt
     */
    public void openUpgradeMenu(Player player, DepositBoxData depositBox) {
        Inventory gui = guiManager.createInventory(27, "§6🏦 §lAMÉLIORATION CAISSE DE DÉPÔT");
        guiManager.fillBorders(gui);

        // Header
        gui.setItem(4, createHeader(depositBox));

        // Amélioration de capacité
        gui.setItem(CAPACITY_UPGRADE_SLOT, createCapacityUpgradeButton(player, depositBox));

        // Amélioration de multiplicateur
        gui.setItem(MULTIPLIER_UPGRADE_SLOT, createMultiplierUpgradeButton(player, depositBox));

        // Informations
        gui.setItem(INFO_SLOT, createInfoButton(depositBox));

        // Bouton fermer
        gui.setItem(CLOSE_SLOT, createCloseButton());

        guiManager.registerOpenGUI(player, GUIType.DEPOSIT_BOX_UPGRADE, gui);
        guiManager.setGUIData(player, "deposit_box_id", depositBox.getId());
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
    }

    /**
     * Gère les clics dans le GUI d'amélioration
     */
    public void handleClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String action = guiManager.getDataFromItem(clickedItem, "action");
        if (action == null) return;

        String depositBoxId = guiManager.getGUIData(player, "deposit_box_id");
        if (depositBoxId == null) {
            player.sendMessage("§c❌ Erreur: caisse de dépôt introuvable!");
            player.closeInventory();
            return;
        }

        DepositBoxManager depositBoxManager = plugin.getDepositBoxManager();
        DepositBoxData depositBox = depositBoxManager.getDepositBoxById(depositBoxId);
        if (depositBox == null) {
            player.sendMessage("§c❌ Erreur: caisse de dépôt introuvable!");
            player.closeInventory();
            return;
        }

        switch (action) {
            case "upgrade_capacity" -> handleCapacityUpgrade(player, depositBox);
            case "upgrade_multiplier" -> handleMultiplierUpgrade(player, depositBox);
            case "close" -> player.closeInventory();
        }
    }

    /**
     * Gère l'amélioration de capacité
     */
    private void handleCapacityUpgrade(Player player, DepositBoxData depositBox) {
        int currentLevel = depositBox.getCapacityLevel();
        if (currentLevel >= 20) {
            player.sendMessage("§c❌ Cette caisse de dépôt a déjà la capacité maximale!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long upgradeCost = getCapacityUpgradeCost(currentLevel + 1);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getCoins() < upgradeCost) {
            player.sendMessage("§c❌ §lFonds insuffisants!");
            player.sendMessage("§7Coût: §6" + NumberFormatter.format(upgradeCost) + " coins");
            player.sendMessage("§7Votre solde: §6" + NumberFormatter.format(playerData.getCoins()) + " coins");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Effectuer l'amélioration
        playerData.removeCoins(upgradeCost);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        DepositBoxManager depositBoxManager = plugin.getDepositBoxManager();
        DepositBoxData upgradedDepositBox = depositBoxManager.upgradeDepositBoxCapacity(depositBox.getId());
        
        if (upgradedDepositBox != null) {
            player.sendMessage("§a✅ §lAmélioration de capacité réussie!");
            player.sendMessage("§7Nouvelle capacité: §e" + upgradedDepositBox.getMaxItemsPerSecond() + " items/seconde");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            
            // Recharger le menu
            openUpgradeMenu(player, upgradedDepositBox);
        } else {
            player.sendMessage("§c❌ Erreur lors de l'amélioration!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * Gère l'amélioration de multiplicateur
     */
    private void handleMultiplierUpgrade(Player player, DepositBoxData depositBox) {
        double currentMultiplier = depositBox.getMultiplierLevel();
        if (currentMultiplier >= 20.0) {
            player.sendMessage("§c❌ Cette caisse de dépôt a déjà le multiplicateur maximal!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long upgradeCost = getMultiplierUpgradeCost(currentMultiplier + 0.5);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getCoins() < upgradeCost) {
            player.sendMessage("§c❌ §lFonds insuffisants!");
            player.sendMessage("§7Coût: §6" + NumberFormatter.format(upgradeCost) + " coins");
            player.sendMessage("§7Votre solde: §6" + NumberFormatter.format(playerData.getCoins()) + " coins");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Effectuer l'amélioration
        playerData.removeCoins(upgradeCost);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        DepositBoxManager depositBoxManager = plugin.getDepositBoxManager();
        DepositBoxData upgradedDepositBox = depositBoxManager.upgradeDepositBoxMultiplier(depositBox.getId());
        
        if (upgradedDepositBox != null) {
            player.sendMessage("§a✅ §lAmélioration de multiplicateur réussie!");
            player.sendMessage("§7Nouveau multiplicateur: §e" + upgradedDepositBox.getMultiplierLevel() + "x");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            
            // Recharger le menu
            openUpgradeMenu(player, upgradedDepositBox);
        } else {
            player.sendMessage("§c❌ Erreur lors de l'amélioration!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    // ==================== CRÉATION D'ITEMS GUI ====================

    private ItemStack createHeader(DepositBoxData depositBox) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§6🏦 §lCAISSE DE DÉPÔT");

        List<String> lore = Arrays.asList(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7ID: §e" + depositBox.getId(),
                "§7Propriétaire: §e" + depositBox.getOwner(),
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "",
                "§7Niveau de capacité: §e" + depositBox.getCapacityLevel() + "/20",
                "§7Multiplicateur: §e" + depositBox.getMultiplierLevel() + "x/20x",
                "§7Capacité: §e" + depositBox.getMaxItemsPerSecond() + " items/seconde",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createCapacityUpgradeButton(Player player, DepositBoxData depositBox) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();

        int currentLevel = depositBox.getCapacityLevel();
        boolean canUpgrade = currentLevel < 20;
        long upgradeCost = canUpgrade ? getCapacityUpgradeCost(currentLevel + 1) : 0;
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean canAfford = playerData.getCoins() >= upgradeCost;

        if (canUpgrade) {
            guiManager.applyName(meta, "§a⚡ §lAMÉLIORER CAPACITÉ");
        } else {
            guiManager.applyName(meta, "§c⚡ §lCAPACITÉ MAXIMALE");
        }

        List<String> lore = Arrays.asList(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Niveau actuel: §e" + currentLevel + "/20",
                "§7Capacité actuelle: §e" + depositBox.getMaxItemsPerSecond() + " items/seconde"
        );

        if (canUpgrade) {
            lore.add("§7Nouveau niveau: §e" + (currentLevel + 1) + "/20");
            lore.add("§7Nouvelle capacité: §e" + ((currentLevel + 1) * 2) + " items/seconde");
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§7Coût: §6" + NumberFormatter.format(upgradeCost) + " coins");
            
            if (canAfford) {
                lore.add("");
                lore.add("§a✅ §lVous pouvez améliorer!");
                lore.add("§e▶ Cliquez pour améliorer!");
            } else {
                lore.add("");
                lore.add("§c❌ §lFonds insuffisants");
                lore.add("§7Il vous manque §6" + NumberFormatter.format(upgradeCost - playerData.getCoins()) + " coins");
            }
        } else {
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§c❌ §lCapacité maximale atteinte");
        }

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        if (canUpgrade && canAfford) {
            guiManager.addGUIMetadata(item, GUIType.DEPOSIT_BOX_UPGRADE, "action", "upgrade_capacity");
        }

        return item;
    }

    private ItemStack createMultiplierUpgradeButton(Player player, DepositBoxData depositBox) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();

        double currentMultiplier = depositBox.getMultiplierLevel();
        boolean canUpgrade = currentMultiplier < 20.0;
        long upgradeCost = canUpgrade ? getMultiplierUpgradeCost(currentMultiplier + 0.5) : 0;
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean canAfford = playerData.getCoins() >= upgradeCost;

        if (canUpgrade) {
            guiManager.applyName(meta, "§a💰 §lAMÉLIORER MULTIPLICATEUR");
        } else {
            guiManager.applyName(meta, "§c💰 §lMULTIPLICATEUR MAXIMAL");
        }

        List<String> lore = Arrays.asList(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Multiplicateur actuel: §e" + currentMultiplier + "x",
                "§7Bonus sur les gains de billets"
        );

        if (canUpgrade) {
            lore.add("§7Nouveau multiplicateur: §e" + (currentMultiplier + 0.5) + "x");
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§7Coût: §6" + NumberFormatter.format(upgradeCost) + " coins");
            
            if (canAfford) {
                lore.add("");
                lore.add("§a✅ §lVous pouvez améliorer!");
                lore.add("§e▶ Cliquez pour améliorer!");
            } else {
                lore.add("");
                lore.add("§c❌ §lFonds insuffisants");
                lore.add("§7Il vous manque §6" + NumberFormatter.format(upgradeCost - playerData.getCoins()) + " coins");
            }
        } else {
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§c❌ §lMultiplicateur maximal atteint");
        }

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        if (canUpgrade && canAfford) {
            guiManager.addGUIMetadata(item, GUIType.DEPOSIT_BOX_UPGRADE, "action", "upgrade_multiplier");
        }

        return item;
    }

    private ItemStack createInfoButton(DepositBoxData depositBox) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§e📖 §lINFORMATIONS");

        List<String> lore = Arrays.asList(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Cette caisse de dépôt vend automatiquement",
                "§7les billets envoyés via hoppers.",
                "",
                "§7Capacité: §e" + depositBox.getMaxItemsPerSecond() + " items/seconde",
                "§7Multiplicateur: §e" + depositBox.getMultiplierLevel() + "x",
                "",
                "§7Les gains sont calculés avec votre",
                "§7bonus de vente × multiplicateur.",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§c✗ §lFermer");
        guiManager.applyLore(meta, Arrays.asList("§7Ferme le menu d'amélioration"));
        item.setItemMeta(meta);
        guiManager.addGUIMetadata(item, GUIType.DEPOSIT_BOX_UPGRADE, "action", "close");

        return item;
    }

    // ==================== CALCUL DES COÛTS ====================

    private long getCapacityUpgradeCost(int newLevel) {
        return switch (newLevel) {
            case 2 -> 5000L;
            case 3 -> 25000L;
            case 4 -> 100000L;
            case 5 -> 500000L;
            case 6 -> 1000000L;
            case 7 -> 2500000L;
            case 8 -> 5000000L;
            case 9 -> 10000000L;
            case 10 -> 25000000L;
            case 11 -> 50000000L;
            case 12 -> 100000000L;
            case 13 -> 250000000L;
            case 14 -> 500000000L;
            case 15 -> 1000000000L;
            case 16 -> 2500000000L;
            case 17 -> 5000000000L;
            case 18 -> 10000000000L;
            case 19 -> 25000000000L;
            case 20 -> 50000000000L;
            default -> 1000L;
        };
    }

    private long getMultiplierUpgradeCost(double newMultiplier) {
        return switch ((int) (newMultiplier * 2)) {
            case 3 -> 10000L;   // 1.5x
            case 4 -> 50000L;   // 2.0x
            case 5 -> 200000L;  // 2.5x
            case 6 -> 750000L;  // 3.0x
            case 7 -> 2000000L; // 3.5x
            case 8 -> 5000000L; // 4.0x
            case 9 -> 10000000L; // 4.5x
            case 10 -> 25000000L; // 5.0x
            case 11 -> 50000000L; // 5.5x
            case 12 -> 100000000L; // 6.0x
            case 13 -> 250000000L; // 6.5x
            case 14 -> 500000000L; // 7.0x
            case 15 -> 1000000000L; // 7.5x
            case 16 -> 2500000000L; // 8.0x
            case 17 -> 5000000000L; // 8.5x
            case 18 -> 10000000000L; // 9.0x
            case 19 -> 25000000000L; // 9.5x
            case 20 -> 50000000000L; // 10.0x
            case 21 -> 100000000000L; // 10.5x
            case 22 -> 250000000000L; // 11.0x
            case 23 -> 500000000000L; // 11.5x
            case 24 -> 1000000000000L; // 12.0x
            case 25 -> 2500000000000L; // 12.5x
            case 26 -> 5000000000000L; // 13.0x
            case 27 -> 10000000000000L; // 13.5x
            case 28 -> 25000000000000L; // 14.0x
            case 29 -> 50000000000000L; // 14.5x
            case 30 -> 100000000000000L; // 15.0x
            case 31 -> 250000000000000L; // 15.5x
            case 32 -> 500000000000000L; // 16.0x
            case 33 -> 1000000000000000L; // 16.5x
            case 34 -> 2500000000000000L; // 17.0x
            case 35 -> 5000000000000000L; // 17.5x
            case 36 -> 10000000000000000L; // 18.0x
            case 37 -> 25000000000000000L; // 18.5x
            case 38 -> 50000000000000000L; // 19.0x
            case 39 -> 100000000000000000L; // 19.5x
            case 40 -> 250000000000000000L; // 20.0x
            default -> 5000L;
        };
    }
}
