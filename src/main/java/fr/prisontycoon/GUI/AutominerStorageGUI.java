package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique pour la gestion du stockage des automineurs
 * Permet de voir les blocs stockés, améliorer la capacité et récupérer les clés
 */
public class AutominerStorageGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    // Layout du menu (54 slots - 6 lignes)
    private static final int STORAGE_INFO_SLOT = 4;
    private static final int UPGRADE_STORAGE_SLOT = 22;
    private static final int WORLD_BUTTON_SLOT = 40;
    private static final int COLLECT_KEYS_SLOT = 42;
    private static final int BACK_BUTTON_SLOT = 45;

    // Zone d'affichage des blocs stockés (slots 10-16, 19-25, 28-34)
    private static final int[] STORAGE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 23, 24, 25, 26,
            28, 29, 30, 31, 32, 33, 34
    };

    public AutominerStorageGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.valueKey = new NamespacedKey(plugin, "gui_value");
    }

    /**
     * Ouvre le menu de gestion du stockage
     */
    public void openStorageMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, "§d📦 Stockage Automineurs");

        // Bordures décoratives
        fillBorders(inv);

        // Informations du stockage (slot 4)
        inv.setItem(STORAGE_INFO_SLOT, createStorageInfoItem(playerData));

        // Bouton d'amélioration du stockage (slot 22)
        inv.setItem(UPGRADE_STORAGE_SLOT, createUpgradeStorageButton(playerData));

        // Bouton de gestion du monde (slot 40)
        inv.setItem(WORLD_BUTTON_SLOT, createWorldButton(playerData));

        // Bouton de récupération des clés (slot 42)
        inv.setItem(COLLECT_KEYS_SLOT, createCollectKeysButton(playerData));

        // Bouton de retour (slot 45)
        inv.setItem(BACK_BUTTON_SLOT, createBackButton());

        // Affichage des blocs stockés
        populateStoredBlocks(inv, playerData);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * Crée l'item d'information du stockage
     */
    private ItemStack createStorageInfoItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§d📦 §lINFORMATIONS STOCKAGE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Statistiques générales
        Map<Material, Long> storedBlocks = playerData.getAutominerStoredBlocks();
        long totalStored = storedBlocks.values().stream().mapToLong(Long::longValue).sum();
        long maxCapacity = playerData.getAutominerStorageCapacity();

        lore.add("§7Capacité totale: §f" + formatCapacity(maxCapacity));
        lore.add("§7Blocs stockés: §f" + NumberFormatter.format(totalStored));

        double percentage = maxCapacity > 0 ? (double) totalStored / maxCapacity * 100 : 0;
        String capacityColor = percentage >= 90 ? "§c" : percentage >= 70 ? "§e" : "§a";
        lore.add("§7Taux de remplissage: " + capacityColor + String.format("%.1f%%", percentage));
        lore.add("");

        // Types de blocs
        lore.add("§7Types de blocs stockés: §f" + storedBlocks.size());
        lore.add("");

        // Valeur totale estimée
        long totalValue = calculateTotalStorageValue(storedBlocks);
        if (totalValue > 0) {
            lore.add("§6💰 Valeur estimée: §f" + NumberFormatter.format(totalValue) + " coins");
            lore.add("");
        }

        // Statut de remplissage
        if (percentage >= 95) {
            lore.add("§c⚠ STOCKAGE PRESQUE PLEIN!");
            lore.add("§cAméliorez la capacité ou vendez des blocs.");
        } else if (percentage >= 80) {
            lore.add("§e⚠ Stockage se remplit rapidement.");
        } else {
            lore.add("§a✅ Espace de stockage suffisant.");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton d'amélioration du stockage
     */
    private ItemStack createUpgradeStorageButton(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a⬆ §lAMÉLIORER STOCKAGE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        long currentCapacity = playerData.getAutominerStorageCapacity();
        long nextCapacity = calculateNextStorageCapacity(currentCapacity);
        int upgradeCost = calculateStorageUpgradeCost(currentCapacity);

        lore.add("§7Capacité actuelle: §f" + formatCapacity(currentCapacity));

        if (nextCapacity > currentCapacity) {
            lore.add("§7Capacité suivante: §a" + formatCapacity(nextCapacity));
            lore.add("§7Amélioration: §a+" + formatCapacity(nextCapacity - currentCapacity));
            lore.add("");
            lore.add("§6💰 Coût: §f" + upgradeCost + " beacons");
            lore.add("");

            if (playerData.getBeacons() >= upgradeCost) {
                lore.add("§a✅ Vous avez assez de beacons!");
                lore.add("§a🖱 §lCLIC: §aAméliorer le stockage");
            } else {
                lore.add("§c❌ Pas assez de beacons!");
                lore.add("§7Beacons requis: §c" + (upgradeCost - playerData.getBeacons()));
            }
        } else {
            lore.add("§c⚠ Capacité maximale atteinte!");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "upgrade_storage", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de gestion du monde
     */
    private ItemStack createWorldButton(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e🌍 §lMONDE: " + playerData.getAutominerWorld().toUpperCase());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Monde de minage actuel de vos automineurs.");
        lore.add("");
        lore.add("§7Blocs disponibles dans ce monde:");
        addWorldBlocksToLore(lore, playerData.getAutominerWorld());
        lore.add("");
        lore.add("§e⇧ §lSHIFT+CLIC: §eAméliorer le monde");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "upgrade_world", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de récupération des clés
     */
    private ItemStack createCollectKeysButton(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6🗝 §lRÉCUPÉRER TOUTES LES CLÉS");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Récupérez toutes les clés générées");
        lore.add("§7par vos automineurs.");
        lore.add("");

        // Calculer le nombre de clés disponibles
        long availableKeys = calculateAvailableKeys(playerData);
        lore.add("§7Clés disponibles: §f" + availableKeys);

        if (availableKeys > 0) {
            lore.add("");
            lore.add("§a🖱 §lCLIC: §aRécupérer toutes les clés");
        } else {
            lore.add("§c⚠ Aucune clé à récupérer.");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "collect_keys", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c← §lRETOUR");

        List<String> lore = new ArrayList<>();
        lore.add("§7Retourner au menu principal des automineurs");

        meta.setLore(lore);
        setItemAction(meta, "back_to_main", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Remplit la zone d'affichage des blocs stockés
     */
    private void populateStoredBlocks(Inventory inv, PlayerData playerData) {
        Map<Material, Long> storedBlocks = playerData.getAutominerStoredBlocks();

        int slotIndex = 0;
        for (Map.Entry<Material, Long> entry : storedBlocks.entrySet()) {
            if (slotIndex >= STORAGE_SLOTS.length) break;

            Material material = entry.getKey();
            Long quantity = entry.getValue();

            if (quantity > 0) {
                ItemStack blockItem = createStoredBlockItem(material, quantity);
                inv.setItem(STORAGE_SLOTS[slotIndex], blockItem);
                slotIndex++;
            }
        }

        // Remplir les slots vides restants avec des panneaux de verre
        for (int i = slotIndex; i < STORAGE_SLOTS.length; i++) {
            inv.setItem(STORAGE_SLOTS[i], createEmptyStorageSlot());
        }
    }

    /**
     * Crée un item représentant un bloc stocké
     */
    private ItemStack createStoredBlockItem(Material material, long quantity) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String materialName = material.name().toLowerCase().replace("_", " ");
        meta.setDisplayName("§f" + capitalize(materialName));

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Quantité: §f" + NumberFormatter.format(quantity));

        // Valeur du bloc
        var blockValue = plugin.getConfigManager().getBlockValue(material);
        if (blockValue != null && blockValue.getCoins() > 0) {
            long totalValue = blockValue.getCoins() * quantity;
            lore.add("§7Valeur unitaire: §6" + blockValue.getCoins() + " coins");
            lore.add("§7Valeur totale: §6" + NumberFormatter.format(totalValue) + " coins");
        }

        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un slot de stockage vide
     */
    private ItemStack createEmptyStorageSlot() {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7▫ §lSlot Vide");

        List<String> lore = new ArrayList<>();
        lore.add("§7Aucun bloc de ce type en stockage.");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Gère les clics dans le menu de stockage
     */
    public void handleStorageClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "upgrade_storage" -> {
                upgradeStorage(player);
            }
            case "upgrade_world" -> {
                if (clickType.isShiftClick()) {
                    upgradeWorld(player);
                }
            }
            case "collect_keys" -> {
                collectAllKeys(player);
            }
            case "back_to_main" -> {
                plugin.getAutominerGUI().openMainMenu(player);
            }
        }
    }

    /**
     * Améliore la capacité de stockage
     */
    private void upgradeStorage(Player player) {
        if (plugin.getAutominerManager().upgradeStorage(player)) {
            openStorageMenu(player);
        }
    }

    /**
     * Améliore le monde de minage
     */
    private void upgradeWorld(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentWorld = playerData.getAutominerWorld();

        int upgradeCost = calculateWorldUpgradeCost(currentWorld);

        if (playerData.getBeacons() < upgradeCost) {
            player.sendMessage("§c❌ Pas assez de beacons! Coût: §6" + upgradeCost + " beacons");
            return;
        }

        String newWorld = generateRandomWorld(currentWorld);

        if (newWorld.equals(currentWorld)) {
            player.sendMessage("§c❌ Impossible d'améliorer davantage!");
            return;
        }

        // Appliquer l'amélioration
        playerData.removeBeacon(upgradeCost);
        playerData.setAutominerWorld(newWorld);

        player.sendMessage("§a✅ Monde amélioré de §e" + currentWorld.toUpperCase() + " §avers §e" + newWorld.toUpperCase() + "§a!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

        // Refresh du menu
        openStorageMenu(player);
    }

    /**
     * Récupère toutes les clés disponibles
     */
    private void collectAllKeys(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        long keysToCollect = calculateAvailableKeys(playerData);

        if (keysToCollect <= 0) {
            player.sendMessage("§c❌ Aucune clé à récupérer!");
            return;
        }

        // Vérifier l'espace dans l'inventaire
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c❌ Inventaire plein! Libérez de l'espace d'abord.");
            return;
        }

        // Créer les clés et les donner au joueur
        // TODO: Implémenter selon votre système de clés
        // ItemStack keyItem = plugin.getKeyManager().createKey(KeyType.COMMON, (int)keysToCollect);
        // player.getInventory().addItem(keyItem);

        // Reset du compteur de clés

        player.sendMessage("§a✅ Vous avez récupéré §e" + keysToCollect + " clés§a!");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);

        // Refresh du menu
        openStorageMenu(player);
    }


    // Méthodes utilitaires

    private void fillBorders(Inventory inv) {
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();
        meta.setDisplayName(" ");
        borderItem.setItemMeta(meta);

        // Bordures haut et bas
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderItem);
            inv.setItem(i + 45, borderItem);
        }

        // Bordures gauche et droite
        for (int i = 1; i < 5; i++) {
            inv.setItem(i * 9, borderItem);
            inv.setItem(i * 9 + 8, borderItem);
        }
    }

    private void setItemAction(ItemMeta meta, String action, String value) {
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
    }

    private String formatCapacity(long capacity) {
        if (capacity >= 1000000) return (capacity / 1000000) + "M";
        if (capacity >= 1000) return (capacity / 1000) + "k";
        return String.valueOf(capacity);
    }

    private String capitalize(String str) {
        if (str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private long calculateTotalStorageValue(Map<Material, Long> storedBlocks) {
        long totalValue = 0;
        for (Map.Entry<Material, Long> entry : storedBlocks.entrySet()) {
            var blockValue = plugin.getConfigManager().getBlockValue(entry.getKey());
            if (blockValue != null) {
                totalValue += blockValue.getCoins() * entry.getValue();
            }
        }
        return totalValue;
    }

    private long calculateNextStorageCapacity(long currentCapacity) {
        // Progression : 10k -> 25k -> 50k -> 100k -> 250k -> 500k -> 1M -> 2M
        if (currentCapacity < 10000) return 10000;
        if (currentCapacity < 25000) return 25000;
        if (currentCapacity < 50000) return 50000;
        if (currentCapacity < 100000) return 100000;
        if (currentCapacity < 250000) return 250000;
        if (currentCapacity < 500000) return 500000;
        if (currentCapacity < 1000000) return 1000000;
        if (currentCapacity < 2000000) return 2000000;
        return currentCapacity; // Maximum atteint
    }

    private int calculateStorageUpgradeCost(long currentCapacity) {
        // Coût croissant selon la capacité
        if (currentCapacity < 10000) return 10;
        if (currentCapacity < 25000) return 25;
        if (currentCapacity < 50000) return 50;
        if (currentCapacity < 100000) return 100;
        if (currentCapacity < 250000) return 250;
        if (currentCapacity < 500000) return 500;
        if (currentCapacity < 1000000) return 1000;
        if (currentCapacity < 2000000) return 2000;
        return Integer.MAX_VALUE; // Pas d'amélioration possible
    }

    private void addWorldBlocksToLore(List<String> lore, String worldName) {
        try {
            var mineData = plugin.getConfigManager().getMineData("mine-" + worldName);
            if (mineData != null && mineData.getBlockComposition() != null) {
                for (Material material : mineData.getBlockComposition().keySet()) {
                    String blockName = material.name().toLowerCase().replace("_", " ");
                    var blockValue = plugin.getConfigManager().getBlockValue(material);
                    if (blockValue != null) {
                        lore.add("§7▸ " + capitalize(blockName) + " (§6" + blockValue.getCoins() + " coins§7)");
                    } else {
                        lore.add("§7▸ " + capitalize(blockName));
                    }
                }
            } else {
                lore.add("§7▸ Informations non disponibles");
            }
        } catch (Exception e) {
            lore.add("§7▸ Erreur lors du chargement");
        }
    }

    private int calculateWorldUpgradeCost(String currentWorld) {
        char currentChar = currentWorld.charAt(0);
        return (currentChar - 'a' + 1) * 10;
    }

    private String generateRandomWorld(String currentWorld) {
        char currentChar = currentWorld.charAt(0);
        if (currentChar >= 'z') return currentWorld;

        double rarity = Math.random();
        char newChar = currentChar;

        for (char c = (char)(currentChar + 1); c <= 'z'; c++) {
            double threshold = Math.pow(0.7, c - currentChar);
            if (rarity < threshold) {
                newChar = c;
                break;
            }
        }

        return String.valueOf(newChar);
    }

    private long calculateAvailableKeys(PlayerData playerData) {
        // TODO: Implémenter selon votre système de calcul des clés
        // Basé sur le temps de fonctionnement, les enchantements KeyGreed, etc.
        return 1;
    }
}