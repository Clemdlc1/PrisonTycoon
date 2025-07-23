package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.AutominerManager;
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

import java.util.*;

/**
 * Interface graphique principale simplifiée pour les automineurs
 * Affiche les automineurs placés et donne accès aux sous-menus spécialisés
 */
public class AutominerGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    // Layout du menu principal (54 slots - 6 lignes)
    private static final int PLAYER_INFO_SLOT = 4;
    private static final int START_STOP_SLOT = 13;

    // Emplacements des automineurs placés (SEULEMENT 2 SLOTS)
    private static final int AUTOMINER_1_SLOT = 21;
    private static final int AUTOMINER_2_SLOT = 23;

    // Boutons de gestion (ligne du bas)
    private static final int FUEL_BUTTON_SLOT = 45;
    private static final int WORLD_BUTTON_SLOT = 46;
    private static final int STORAGE_BUTTON_SLOT = 47;
    private static final int CONDENSE_BUTTON_SLOT = 48;

    public AutominerGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.valueKey = new NamespacedKey(plugin, "gui_value");
    }

    /**
     * Ouvre le menu principal des automineurs
     */
    public void openMainMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, "§6⚡ Automineurs - Menu");

        // Bordures décoratives
        fillBorders(inv);

        // Informations du joueur (slot 4)
        inv.setItem(PLAYER_INFO_SLOT, createPlayerInfoItem(playerData));

        // Bouton start/stop (slot 13)
        inv.setItem(START_STOP_SLOT, createStartStopButton(playerData));

        // Automineurs placés
        populatePlacedAutominers(inv, playerData, player);

        // Boutons de gestion
        inv.setItem(FUEL_BUTTON_SLOT, createFuelButton(playerData));
        inv.setItem(WORLD_BUTTON_SLOT, createWorldButton(playerData));
        inv.setItem(STORAGE_BUTTON_SLOT, createStorageButton(playerData));
        inv.setItem(CONDENSE_BUTTON_SLOT, createCondenseButton());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * Crée l'item d'information du joueur
     */
    private ItemStack createPlayerInfoItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6⚡ §lVOS AUTOMINEURS");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Statut général
        boolean isRunning = playerData.isAutominersRunning();
        lore.add("§7État: " + (isRunning ? "§a✅ EN FONCTIONNEMENT" : "§c❌ ARRÊTÉS"));
        lore.add("§7Automineurs actifs: §f" + playerData.getActiveAutominers().size() + "§7/§f2");
        lore.add("");

        // Monde et carburant
        lore.add("§e🌍 §lMONDE ACTUEL");
        lore.add("§7▸ Monde: §f" + playerData.getAutominerWorld().toUpperCase());
        lore.add("");

        lore.add("§c⛽ §lCARBURANT");
        long fuelAmount = playerData.getAutominerFuel();
        lore.add("§7▸ Têtes disponibles: §f" + NumberFormatter.format(fuelAmount));

        if (isRunning && !playerData.getActiveAutominers().isEmpty()) {
            int totalConsumption = calculateTotalFuelConsumption(playerData);
            long remainingTime = totalConsumption > 0 ? (fuelAmount * 60) / totalConsumption : 0;
            lore.add("§7▸ Temps restant: §f" + formatTime(remainingTime) + " minutes");
        }
        lore.add("");

        // Stockage
        lore.add("§d📦 §lSTOCKAGE");
        long currentStored = playerData.getAutominerStoredBlocks().values().stream().mapToLong(Long::longValue).sum();
        long maxCapacity = playerData.getAutominerStorageCapacity();
        lore.add("§7▸ Capacité: §f" + NumberFormatter.format(currentStored) + "§7/§f" + formatCapacity(maxCapacity));

        double percentage = maxCapacity > 0 ? (double) currentStored / maxCapacity * 100 : 0;
        String capacityColor = percentage >= 90 ? "§c" : percentage >= 70 ? "§e" : "§a";
        lore.add("§7▸ Taux de remplissage: " + capacityColor + String.format("%.1f%%", percentage));

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton start/stop
     */
    private ItemStack createStartStopButton(PlayerData playerData) {
        boolean isRunning = playerData.isAutominersRunning();

        ItemStack item = new ItemStack(isRunning ? Material.RED_CONCRETE : Material.GREEN_CONCRETE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(isRunning ? "§c⏹ §lARRÊTER LES AUTOMINEURS" : "§a▶ §lDÉMARRER LES AUTOMINEURS");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (isRunning) {
            lore.add("§7Vos automineurs sont actuellement §aen fonctionnement§7.");
            lore.add("§7Cliquez pour les §carrêter temporairement§7.");
        } else {
            lore.add("§7Vos automineurs sont actuellement §carrêtés§7.");
            if (playerData.getActiveAutominers().isEmpty()) {
                lore.add("§c⚠ Aucun automineur n'est placé!");
            } else if (playerData.getAutominerFuel() <= 0) {
                lore.add("§c⚠ Pas assez de carburant!");
            } else {
                lore.add("§7Cliquez pour les §adémarrer§7.");
            }
        }

        lore.add("");
        lore.add("§a🖱 §lCLIC: §a" + (isRunning ? "Arrêter" : "Démarrer"));
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "toggle_running", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Remplit les emplacements des automineurs placés
     */
    private void populatePlacedAutominers(Inventory inv, PlayerData playerData, Player player) {
        Set<String> activeUuids = playerData.getActiveAutominers();
        List<AutominerData> activeAutominers = new ArrayList<>();

        // Find autominer data from player's inventory based on active UUIDs
        for (String uuid : activeUuids) {
            AutominerData data = findAutominerDataByUuid(player, uuid);
            if (data != null) {
                activeAutominers.add(data);
            }
        }

        // Slot 1
        if (activeAutominers.size() > 0) {
            inv.setItem(AUTOMINER_1_SLOT, createPlacedAutominerItem(activeAutominers.get(0), 1));
        } else {
            inv.setItem(AUTOMINER_1_SLOT, createEmptySlotItem(1));
        }

        // Slot 2
        if (activeAutominers.size() > 1) {
            inv.setItem(AUTOMINER_2_SLOT, createPlacedAutominerItem(activeAutominers.get(1), 2));
        } else {
            inv.setItem(AUTOMINER_2_SLOT, createEmptySlotItem(2));
        }
    }

    /**
     * Crée un item représentant un automineur placé
     */
    private ItemStack createPlacedAutominerItem(AutominerData autominer, int slotNumber) {
        ItemStack item = new ItemStack(autominer.getType().getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6⚡ " + autominer.getType().getColoredName() + " §6Automineur §8#" + slotNumber);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7UUID: §f" + autominer.getUuid().substring(0, 8) + "...");
        lore.add("");

        // Statistiques principales
        lore.add("§e⚡ §lSTATISTIQUES");
        lore.add("§7▸ Efficacité: §a" + autominer.getTotalEfficiency());
        lore.add("§7▸ Fortune: §a" + autominer.getTotalFortune());
        lore.add("§7▸ Consommation: §c" + autominer.getActualFuelConsumption() + " têtes/heure");
        lore.add("");

        // Bonus de récompenses
        if (autominer.getTotalTokenBonus() > 0 || autominer.getTotalExpBonus() > 0 || autominer.getTotalMoneyBonus() > 0) {
            lore.add("§d💰 §lBONUS");
            if (autominer.getTotalTokenBonus() > 0) {
                lore.add("§7▸ Tokens: §a+" + autominer.getTotalTokenBonus() + "%");
            }
            if (autominer.getTotalExpBonus() > 0) {
                lore.add("§7▸ Expérience: §a+" + autominer.getTotalExpBonus() + "%");
            }
            if (autominer.getTotalMoneyBonus() > 0) {
                lore.add("§7▸ Argent: §a+" + autominer.getTotalMoneyBonus() + "%");
            }
            lore.add("");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§a🖱 §lCLIC: §aOuvrir le menu d'amélioration");
        lore.add("§c⇧ §lSHIFT+CLIC: §cRetirer l'automineur");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "manage_autominer", autominer.getUuid());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un emplacement vide pour automineur
     */
    private ItemStack createEmptySlotItem(int slotNumber) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7⚡ §lEMPLACEMENT " + slotNumber + " §8(LIBRE)");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Aucun automineur placé dans ce slot.");
        lore.add("");
        lore.add("§e📝 §lCOMMENT PLACER UN AUTOMINEUR:");
        lore.add("§7▸ Tenez un automineur en main");
        lore.add("§7▸ Cliquez sur cet emplacement");
        lore.add("§7▸ L'automineur sera automatiquement placé");
        lore.add("");
        lore.add("§a🖱 §lCLIC: §aPlacer l'automineur en main");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "place_autominer", String.valueOf(slotNumber));
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de gestion du carburant
     */
    private ItemStack createFuelButton(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c⛽ §lGESTION CARBURANT");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Gérez le carburant de vos automineurs.");
        lore.add("");
        lore.add("§7Têtes disponibles: §f" + NumberFormatter.format(playerData.getAutominerFuel()));
        lore.add("§7Consommation totale: §c" + calculateTotalFuelConsumption(playerData) + " têtes/heure");
        lore.add("");
        lore.add("§a🖱 §lCLIC: §aOuvrir le menu carburant");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "open_fuel_menu", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de gestion du monde
     */
    private ItemStack createWorldButton(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e🌍 §lGESTION MONDE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Changez le monde de minage de vos automineurs.");
        lore.add("");
        lore.add("§7Monde actuel: §f" + playerData.getAutominerWorld().toUpperCase());

        // Afficher les blocs disponibles dans ce monde
        lore.add("§7Blocs disponibles:");
        addWorldBlocksToLore(lore, playerData.getAutominerWorld());

        lore.add("");
        lore.add("§e⇧ §lSHIFT+CLIC: §eAméliorer le monde (coût en beacons)");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "upgrade_world", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de gestion du stockage
     */
    private ItemStack createStorageButton(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§d📦 §lGESTION STOCKAGE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Gérez le stockage de vos automineurs.");
        lore.add("");

        long currentStored = playerData.getAutominerStoredBlocks().values().stream().mapToLong(Long::longValue).sum();
        long maxCapacity = playerData.getAutominerStorageCapacity();

        lore.add("§7Capacité: §f" + NumberFormatter.format(currentStored) + "§7/§f" + formatCapacity(maxCapacity));

        double percentage = maxCapacity > 0 ? (double) currentStored / maxCapacity * 100 : 0;
        String capacityColor = percentage >= 90 ? "§c" : percentage >= 70 ? "§e" : "§a";
        lore.add("§7Taux de remplissage: " + capacityColor + String.format("%.1f%%", percentage));

        lore.add("");
        lore.add("§a🖱 §lCLIC: §aOuvrir le menu stockage");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "open_storage_menu", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de condensation
     */
    private ItemStack createCondenseButton() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§b🔧 §lCONDENSATION");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Condensez 9 automineurs du même type");
        lore.add("§7en un automineur de type supérieur.");
        lore.add("");
        lore.add("§eExemples:");
        lore.add("§7▸ 9 Pierre → 1 Fer");
        lore.add("§7▸ 9 Fer → 1 Or");
        lore.add("§7▸ 9 Or → 1 Diamant");
        lore.add("");
        lore.add("§a🖱 §lCLIC: §aOuvrir le menu de condensation");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "open_condense_menu", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Gère les clics dans le menu principal
     */
    public void handleMainMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "toggle_running" -> {
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                plugin.getAutominerManager().toggleAllAutominers(player, !playerData.isAutominersRunning());
                openMainMenu(player); // Refresh
            }
            case "manage_autominer" -> {
                if (clickType.isShiftClick()) {
                    // Shift-clic = retirer l'automineur
                    plugin.getAutominerManager().removeAutominer(player, value);
                    openMainMenu(player);
                } else {
                    // Clic normal = ouvrir le menu d'enchantement
                    plugin.getAutominerEnchantGUI().openEnchantMenu(player, value);
                }
            }
            case "place_autominer" -> {
                placeAutominerFromInventory(player, Integer.parseInt(value));
            }
            case "upgrade_world" -> {
                if (clickType.isShiftClick()) {
                    upgradeWorld(player);
                }
            }
            case "open_fuel_menu" -> {
                plugin.getAutominerFuelGUI().openFuelMenu(player);
            }
            case "open_storage_menu" -> {
                plugin.getAutominerStorageGUI().openStorageMenu(player);
            }
            case "open_condense_menu" -> {
                plugin.getAutominerCondenseGUI().openCondenseMenu(player);
            }
        }
    }

    /**
     * Place un automineur depuis la main du joueur
     */
    private void placeAutominerFromInventory(Player player, int slotNumber) {
        ItemStack clickedItem = player.getOpenInventory().getCursor();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            player.sendMessage("§c❌ Vous devez tenir un automineur en main!");
            return;
        }

        AutominerData data = AutominerData.fromItemStack(clickedItem,
                plugin.getAutominerManager().getUuidKey(),
                plugin.getAutominerManager().getTypeKey(),
                plugin.getAutominerManager().getEnchantKey(),
                plugin.getAutominerManager().getCristalKey());

        if (data == null) {
            player.sendMessage("§c❌ Automineur invalide!");
            return;
        }

        if (plugin.getAutominerManager().placeAutominer(player, data)) {
            clickedItem.setAmount(clickedItem.getAmount() - 1); // Consommer l'item
            openMainMenu(player); // Refresh
        }
    }

    /**
     * Améliore le monde de minage
     */
    private void upgradeWorld(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentWorld = playerData.getAutominerWorld();

        // Calculer le coût d'amélioration
        int upgradeCost = calculateWorldUpgradeCost(currentWorld);

        if (playerData.getBeacons() < upgradeCost) {
            player.sendMessage("§c❌ Pas assez de beacons! Coût: §6" + upgradeCost + " beacons");
            return;
        }

        // Générer un nouveau monde aléatoire (plus proche de 'z' = plus rare)
        String newWorld = generateRandomWorld(currentWorld);

        if (newWorld.equals(currentWorld)) {
            player.sendMessage("§c❌ Impossible d'améliorer davantage!");
            return;
        }

        // Appliquer l'amélioration
        playerData.removeBeacon(upgradeCost);
        playerData.setAutominerWorld(newWorld);

        player.sendMessage("§a✅ Monde amélioré de §e" + currentWorld.toUpperCase() + " §avers §e" + newWorld.toUpperCase() + "§a!");
        player.sendMessage("§7Coût: §6" + upgradeCost + " beacons");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

        // Refresh du menu
        openMainMenu(player);
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

    private int calculateTotalFuelConsumption(PlayerData playerData) {
        Player player = Bukkit.getPlayer(playerData.getPlayerId());
        int totalConsumption = 0;
        for (String autominerUuid : playerData.getActiveAutominers()) {
            AutominerData autominer = findAutominerDataByUuid(player, autominerUuid);
            if (autominer != null) {
                totalConsumption += autominer.getActualFuelConsumption();
            }
        }
        return totalConsumption;
    }

    private String formatTime(long minutes) {
        if (minutes >= 60) {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            return hours + "h" + (remainingMinutes > 0 ? remainingMinutes + "m" : "");
        }
        return minutes + "m";
    }

    private String formatCapacity(long capacity) {
        if (capacity >= 1000000) return (capacity / 1000000) + "M";
        if (capacity >= 1000) return (capacity / 1000) + "k";
        return String.valueOf(capacity);
    }

    private void addWorldBlocksToLore(List<String> lore, String worldName) {
        MineData mineData = plugin.getMineManager().getMine("mine-" + worldName.toLowerCase());
        if (mineData != null) {
            for (Map.Entry<Material, Double> entry : mineData.getBlockComposition().entrySet()) {
                Material material = entry.getKey();
                long sellPrice = plugin.getConfigManager().getSellPrice(material);
                lore.add("§7- " + capitalize(material.name().replace("_", " ")) + " (§6" + sellPrice + " coins§7)");
            }
        } else {
            lore.add("§cMonde invalide");
        }
    }

    private String capitalize(String str) {
        if (str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private int calculateWorldUpgradeCost(String currentWorld) {
        // Coût basé sur la distance depuis 'a'
        char currentChar = currentWorld.charAt(0);
        return (currentChar - 'a' + 1) * 10; // 10, 20, 30, etc.
    }

    private String generateRandomWorld(String currentWorld) {
        Random rand = new Random();
        char randomChar = (char) ('a' + rand.nextInt(26));
        return String.valueOf(randomChar).toUpperCase();
    }

    // Méthodes de recherche
    private AutominerData findAutominerDataByUuid(Player player, String uuid) {
        AutominerManager am = plugin.getAutominerManager();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && am.isAutominer(item)) {
                String itemUuid = item.getItemMeta().getPersistentDataContainer().get(am.getUuidKey(), PersistentDataType.STRING);
                if (uuid.equals(itemUuid)) {
                    return AutominerData.fromItemStack(item, am.getUuidKey(), am.getTypeKey(), am.getEnchantKey(), am.getCristalKey());
                }
            }
        }
        return null;
    }
}