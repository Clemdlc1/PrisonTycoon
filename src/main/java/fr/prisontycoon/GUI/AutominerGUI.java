package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.AutominerEnchantmentManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Interface graphique complète pour la gestion des automineurs
 * Menu principal : Vue d'ensemble + gestion des automineurs placés
 * Menu individuel : Enchantements et cristaux par automineur
 */
public class AutominerGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    // Slots du menu principal (54 slots - 6 lignes)
    private static final int PLAYER_INFO_SLOT = 4;
    private static final int START_STOP_SLOT = 13;
    private static final int FUEL_SLOT = 22;
    private static final int WORLD_SLOT = 31;
    private static final int STORAGE_SLOT = 40;

    // Emplacements des automineurs placés (SEULEMENT 2 SLOTS)
    private static final int AUTOMINER_1_SLOT = 21;
    private static final int AUTOMINER_2_SLOT = 23;

    // Actions rapides
    private static final int CONDENSE_SLOT = 38;
    private static final int ENERGY_SLOT = 42;

    public AutominerGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "autominer_action");
        this.valueKey = new NamespacedKey(plugin, "autominer_value");
    }

    /**
     * Ouvre le menu principal des automineurs (/autominer)
     */
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6⚡ §lGESTION DES AUTOMINEURS §6⚡");

        fillMainMenu(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    /**
     * Remplit le menu principal avec toutes les informations
     */
    private void fillMainMenu(Inventory gui, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Bordures décoratives
        fillBorders(gui);

        // Informations du joueur (centré en haut)
        gui.setItem(PLAYER_INFO_SLOT, createPlayerInfoItem(playerData));

        // Contrôles principaux (ligne du milieu)
        gui.setItem(START_STOP_SLOT, createStartStopItem(playerData));
        gui.setItem(FUEL_SLOT, createFuelItem(playerData));
        gui.setItem(WORLD_SLOT, createWorldItem(playerData));
        gui.setItem(STORAGE_SLOT, createStorageItem(playerData));

        // Automineurs placés (positions spéciales)
        fillPlacedAutominers(gui, player, playerData);

        // Actions rapides (bas)
        gui.setItem(CONDENSE_SLOT, createCondenseItem());
        gui.setItem(ENERGY_SLOT, createEnergyItem());

        // Instructions d'utilisation
        gui.setItem(49, createHelpItem());
    }

    /**
     * Affiche les automineurs actuellement placés
     */
    private void fillPlacedAutominers(Inventory gui, Player player, PlayerData playerData) {
        Set<String> activeIds = playerData.getActiveAutominers();
        List<AutominerData> activeList = getActiveAutominers(player, activeIds);

        // Emplacement 1
        if (activeList.size() >= 1) {
            gui.setItem(AUTOMINER_1_SLOT, createPlacedAutominerItem(activeList.get(0), 1));
        } else {
            gui.setItem(AUTOMINER_1_SLOT, createEmptySlotItem(1));
        }

        // Emplacement 2
        if (activeList.size() >= 2) {
            gui.setItem(AUTOMINER_2_SLOT, createPlacedAutominerItem(activeList.get(1), 2));
        } else {
            gui.setItem(AUTOMINER_2_SLOT, createEmptySlotItem(2));
        }

        // Indicateurs visuels autour des emplacements
        fillAutominerIndicators(gui, activeList);
    }

    /**
     * Crée l'item d'informations du joueur
     */
    private ItemStack createPlayerInfoItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e⚡ §lVOS AUTOMINEURS §e⚡");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§6📊 §lÉTAT GÉNÉRAL");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        // Statut des automineurs
        Set<String> activeIds = playerData.getActiveAutominers();
        String statusColor = playerData.isAutominersRunning() ? "§a" : "§c";
        String statusText = playerData.isAutominersRunning() ? "EN MARCHE" : "ARRÊTÉS";

        lore.add("§7▸ Automineurs placés: §e" + activeIds.size() + "§8/§e2");
        lore.add("§7▸ Statut: " + statusColor + "⚡ " + statusText);
        lore.add("§7▸ Monde actuel: §b" + playerData.getAutominerWorld());
        lore.add("");

        // Ressources
        lore.add("§6💰 §lRESSOURCES");
        lore.add("§7▸ Carburant: §e" + NumberFormatter.format(playerData.getAutominerFuel()) + " têtes");

        long totalStored = playerData.getTotalStoredBlocks();
        long capacity = playerData.getAutominerStorageCapacity();
        String capacityFormatted = NumberFormatter.format(capacity);

        lore.add("§7▸ Stockage: §d" + NumberFormatter.format(totalStored) + "§8/§d" + capacityFormatted);

        // Barre de progression du stockage
        double percentage = playerData.getStorageUsagePercentage();
        String progressBar = createProgressBar(percentage);
        lore.add("§7▸ " + progressBar + " §7(" + String.format("%.1f", percentage) + "%)");

        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Gérez vos automineurs ci-dessous!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item représentant un automineur placé
     */
    private ItemStack createPlacedAutominerItem(AutominerData autominer, int slotNumber) {
        ItemStack item = new ItemStack(autominer.getType().getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        // Nom avec couleur de rareté et statut
        meta.setDisplayName("§e⚡ §lSLOT " + slotNumber + " §8- " +
                autominer.getType().getColoredName() + " §eAutomineur");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§a✅ §lAUTOMINEUR ACTIF");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        // Informations de base
        lore.add("§6⚙️ §lCARACTÉRISTIQUES");
        lore.add("§7▸ Type: " + autominer.getType().getColoredName());
        lore.add("§7▸ Efficacité totale: §a" + autominer.getTotalEfficiency());
        lore.add("§7▸ Fortune totale: §a" + autominer.getTotalFortune());
        lore.add("§7▸ Consommation: §e" + autominer.getActualFuelConsumption() + "min/tête");
        lore.add("");

        // Enchantements actifs
        if (!autominer.getEnchantments().isEmpty()) {
            lore.add("§d✨ §lENCHANTEMENTS ACTIFS");
            for (var entry : autominer.getEnchantments().entrySet()) {
                if (entry.getValue() > 0) {
                    String enchantName = formatEnchantmentName(entry.getKey());
                    lore.add("§7▸ " + enchantName + ": §a" + entry.getValue());
                }
            }
            lore.add("");
        }

        // Cristaux appliqués
        List<Cristal> cristals = autominer.getAppliedCristals();
        if (!cristals.isEmpty()) {
            lore.add("§b💎 §lCRISTAUX APPLIQUÉS §7(" + cristals.size() + "/2)");
            for (Cristal cristal : cristals) {
                lore.add("§7▸ §d" + cristal.getType().getDisplayName() + " §7Niv." + cristal.getNiveau());
            }
            lore.add("");
        }

        // Bonus totaux
        lore.add("§d💰 §lBONUS TOTAL");
        if (autominer.getTotalTokenBonus() > 0) {
            lore.add("§7▸ Tokens: §a+" + autominer.getTotalTokenBonus() + "%");
        }
        if (autominer.getTotalExpBonus() > 0) {
            lore.add("§7▸ Expérience: §a+" + autominer.getTotalExpBonus() + "%");
        }
        if (autominer.getTotalMoneyBonus() > 0) {
            lore.add("§7▸ Argent: §a+" + autominer.getTotalMoneyBonus() + "%");
        }
        if (autominer.getEnchantmentLevel("keygreed") > 0) {
            lore.add("§7▸ Clés: §a+" + autominer.getEnchantmentLevel("keygreed") + "%");
        }
        lore.add("");

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
     * Ouvre le menu de gestion individuelle d'un automineur
     */
    public void openAutominerManagementMenu(Player player, String autominerUuid) {
        // Trouve l'automineur dans l'inventaire
        AutominerData autominer = findAutominerInInventory(player, autominerUuid);
        if (autominer == null) {
            player.sendMessage("§c❌ Automineur introuvable!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54,
                "§6⚙️ §l" + autominer.getType().getDisplayName().toUpperCase() + " AUTOMINEUR §6⚙️");

        fillAutominerManagementMenu(gui, player, autominer);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
    }

    /**
     * Remplit le menu de gestion individuelle
     */
    private void fillAutominerManagementMenu(Inventory gui, Player player, AutominerData autominer) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Bordures
        fillBorders(gui);

        // Informations de l'automineur (centre haut)
        gui.setItem(4, createAutominerDetailsItem(autominer));

        // Enchantements disponibles (ligne du milieu)
        fillEnchantmentOptions(gui, player, autominer);

        // Gestion des cristaux
        fillCristalOptions(gui, player, autominer);

        // Actions spéciales
        gui.setItem(45, createBackButton());
        gui.setItem(49, createRemoveAutominerButton(autominer));
        gui.setItem(53, createHelpButton());
    }

    /**
     * Remplit les options d'enchantements
     */
    private void fillEnchantmentOptions(Inventory gui, Player player, AutominerData autominer) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        AutominerEnchantmentManager enchantManager = plugin.getAutominerEnchantmentManager();

        // Enchantements principaux (ligne 2)
        int[] enchantSlots = {19, 20, 21, 22, 23, 24, 25};
        String[] enchantNames = {"efficiency", "fortune", "tokengreed", "expgreed", "moneygreed", "keygreed", "fuelefficiency"};

        for (int i = 0; i < enchantNames.length && i < enchantSlots.length; i++) {
            String enchantName = enchantNames[i];

            // Vérification de compatibilité
            if (autominer.getType().supportsEnchantment(enchantName)) {
                gui.setItem(enchantSlots[i], createEnchantmentUpgradeItem(player, autominer, enchantName, enchantManager));
            } else {
                gui.setItem(enchantSlots[i], createDisabledEnchantmentItem(enchantName));
            }
        }

        // BeaconFinder spécial (uniquement pour Beacon)
        if (autominer.getType() == AutominerType.BEACON) {
            gui.setItem(26, createEnchantmentUpgradeItem(player, autominer, "beaconfinder", enchantManager));
        }
    }

    /**
     * Remplit les options de cristaux
     */
    private void fillCristalOptions(Inventory gui, Player player, AutominerData autominer) {
        // Cristaux appliqués (ligne 4)
        List<Cristal> appliedCristals = autominer.getAppliedCristals();

        // Slot cristal 1
        if (appliedCristals.size() >= 1) {
            gui.setItem(37, createAppliedCristalItem(appliedCristals.get(0), 1, autominer));
        } else {
            gui.setItem(37, createEmptyCristalSlot(1, autominer));
        }

        // Slot cristal 2
        if (appliedCristals.size() >= 2) {
            gui.setItem(43, createAppliedCristalItem(appliedCristals.get(1), 2, autominer));
        } else {
            gui.setItem(43, createEmptyCristalSlot(2, autominer));
        }

        // Informations cristaux
        gui.setItem(40, createCristalInfoItem(autominer));
    }

    /**
     * Crée un item d'amélioration d'enchantement
     */
    private ItemStack createEnchantmentUpgradeItem(Player player, AutominerData autominer,
                                                   String enchantName, AutominerEnchantmentManager enchantManager) {
        AutominerEnchantmentManager.AutominerEnchantment enchant = enchantManager.getEnchantment(enchantName);
        if (enchant == null) return new ItemStack(Material.BARRIER);

        int currentLevel = autominer.getEnchantmentLevel(enchantName);
        int maxLevel = autominer.getType().getMaxEnchantmentLevel(enchantName);

        // Matériau selon l'enchantement
        Material material = getEnchantmentMaterial(enchantName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Couleur selon le statut
        String nameColor = currentLevel >= maxLevel ? "§2" : "§e";
        meta.setDisplayName(nameColor + "✨ §l" + enchant.getDisplayName().toUpperCase());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§d✨ §lENCHANTEMENT D'AUTOMINEUR");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§7📝 §lDESCRIPTION");
        lore.add("§7" + enchant.getDescription());
        lore.add("");
        lore.add("§6📊 §lNIVEAU ACTUEL");
        lore.add("§7▸ Niveau: §a" + currentLevel + " §8/ §a" +
                (maxLevel == Integer.MAX_VALUE ? "∞" : maxLevel));
        lore.add("");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (currentLevel >= maxLevel) {
            lore.add("§2✅ §lNIVEAU MAXIMUM ATTEINT");
        } else {
            // Coûts d'amélioration
            lore.add("§e💰 §lCOÛT D'AMÉLIORATION");
            long cost1 = enchantManager.calculateEnchantmentCost(enchant, currentLevel, autominer.getType());
            long cost10 = 0;
            for (int i = 0; i < 10 && currentLevel + i < maxLevel; i++) {
                cost10 += enchantManager.calculateEnchantmentCost(enchant, currentLevel + i, autominer.getType());
            }

            lore.add("§7▸ +1 niveau: §e" + NumberFormatter.format(cost1) + " tokens");
            if (currentLevel + 10 <= maxLevel) {
                lore.add("§7▸ +10 niveaux: §e" + NumberFormatter.format(cost10) + " tokens");
            }
            lore.add("§7▸ Vos tokens: §e" + NumberFormatter.format(playerData.getTokens()));
            lore.add("");

            // Instructions
            if (playerData.getTokens() >= cost1) {
                lore.add("§a🖱 §lCLIC: §a+1 niveau");
                if (currentLevel + 10 <= maxLevel && playerData.getTokens() >= cost10) {
                    lore.add("§a⇧ §lSHIFT+CLIC: §a+10 niveaux");
                }
            } else {
                lore.add("§c❌ §lTOKENS INSUFFISANTS");
            }
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "upgrade_enchant", enchantName + ":" + autominer.getUuid());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un item de cristal appliqué
     */
    private ItemStack createAppliedCristalItem(Cristal cristal, int slotNumber, AutominerData autominer) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§d💎 §lCRISTAL SLOT " + slotNumber + " §8- " +
                cristal.getType().getDisplayName() + " " + cristal.getNiveau());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§a✅ §lCRISTAL APPLIQUÉ");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§7▸ Type: §d" + cristal.getType().getDisplayName());
        lore.add("§7▸ Niveau: §d" + cristal.getNiveau());
        lore.add("§7▸ Bonus: §a+" + (cristal.getNiveau() * 5) + "%");
        lore.add("");
        lore.add("§c❌ §lCLIC: §cRetirer le cristal");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "remove_cristal", cristal.getUuid() + ":" + autominer.getUuid());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un slot vide pour cristal
     */
    private ItemStack createEmptyCristalSlot(int slotNumber, AutominerData autominer) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7💎 §lSLOT CRISTAL " + slotNumber + " §8(LIBRE)");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Aucun cristal appliqué ici.");
        lore.add("");
        lore.add("§e📝 §lTYPES DE CRISTAUX COMPATIBLES:");
        lore.add("§7▸ §dMoney Boost §7(Bonus argent)");
        lore.add("§7▸ §dToken Boost §7(Bonus tokens)");
        lore.add("§7▸ §dXP Boost §7(Bonus expérience)");
        lore.add("§7▸ §dMineral Greed §7(Bonus efficacité)");
        lore.add("");
        lore.add("§a🖱 §lCLIC: §aAppliquer le cristal en main");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "apply_cristal", slotNumber + ":" + autominer.getUuid());
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
                    // Clic normal = ouvrir le menu de gestion
                    openAutominerManagementMenu(player, value);
                }
            }
            case "place_autominer" -> {
                placeAutominerFromHand(player, Integer.parseInt(value));
            }
            case "fuel_management" -> openFuelManagementMenu(player);
            case "world_management" -> openWorldManagementMenu(player);
            case "storage_management" -> openStorageManagementMenu(player);
            case "condense_autominers" -> openCondenseMenu(player);
            case "energy_management" -> openEnergyManagementMenu(player);
        }
    }

    /**
     * Gère les clics dans le menu de gestion d'automineur
     */
    public void handleAutominerManagementClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "upgrade_enchant" -> {
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    String enchantName = parts[0];
                    String autominerUuid = parts[1];
                    int levels = clickType.isShiftClick() ? 10 : 1;
                    upgradeAutominerEnchantment(player, autominerUuid, enchantName, levels);
                }
            }
            case "apply_cristal" -> {
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    int cristalSlot = Integer.parseInt(parts[0]);
                    String autominerUuid = parts[1];
                    applyCristalFromHand(player, autominerUuid, cristalSlot);
                }
            }
            case "remove_cristal" -> {
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    String cristalUuid = parts[0];
                    String autominerUuid = parts[1];
                    removeCristalFromAutominer(player, autominerUuid, cristalUuid);
                }
            }
            case "back_to_main" -> openMainMenu(player);
            case "remove_autominer_confirm" -> {
                plugin.getAutominerManager().removeAutominer(player, value);
                openMainMenu(player);
            }
        }
    }

    // Méthodes utilitaires et de création d'items

    private void fillBorders(Inventory gui) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName("§8");
        border.setItemMeta(meta);

        // Bordures complètes
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, border);
            gui.setItem(i + 45, border);
        }
        for (int i = 1; i < 5; i++) {
            gui.setItem(i * 9, border);
            gui.setItem(i * 9 + 8, border);
        }
    }

    private void fillAutominerIndicators(Inventory gui, List<AutominerData> activeList) {
        // Indicateurs visuels autour des automineurs
        ItemStack indicator = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta meta = indicator.getItemMeta();
        meta.setDisplayName("§a⚡ Automineur Actif");
        indicator.setItemMeta(meta);

        ItemStack empty = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta emptyMeta = empty.getItemMeta();
        emptyMeta.setDisplayName("§c⚡ Emplacement Libre");
        empty.setItemMeta(emptyMeta);

        // Autour du slot 1 (21)
        gui.setItem(12, activeList.size() >= 1 ? indicator : empty);
        gui.setItem(30, activeList.size() >= 1 ? indicator : empty);

        // Autour du slot 2 (23)
        gui.setItem(14, activeList.size() >= 2 ? indicator : empty);
        gui.setItem(32, activeList.size() >= 2 ? indicator : empty);
    }

    private List<AutominerData> getActiveAutominers(Player player, Set<String> activeIds) {
        List<AutominerData> activeList = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isAutominer(item)) {
                AutominerData data = AutominerData.fromItemStack(item,
                        plugin.getAutominerManager().getUuidKey(),
                        plugin.getAutominerManager().getTypeKey(),
                        plugin.getAutominerManager().getEnchantKey(),
                        plugin.getAutominerManager().getCristalKey());

                if (data != null && activeIds.contains(data.getUuid())) {
                    activeList.add(data);
                }
            }
        }

        return activeList;
    }

    private AutominerData findAutominerInInventory(Player player, String uuid) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isAutominer(item)) {
                AutominerData data = AutominerData.fromItemStack(item,
                        plugin.getAutominerManager().getUuidKey(),
                        plugin.getAutominerManager().getTypeKey(),
                        plugin.getAutominerManager().getEnchantKey(),
                        plugin.getAutominerManager().getCristalKey());

                if (data != null && data.getUuid().equals(uuid)) {
                    return data;
                }
            }
        }
        return null;
    }

    private boolean isAutominer(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(plugin.getAutominerManager().getUuidKey(), PersistentDataType.STRING);
    }

    private void setItemAction(ItemMeta meta, String action, String value) {
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
    }

    private String createProgressBar(double percentage) {
        int bars = (int) (percentage / 10); // 10 barres max
        StringBuilder sb = new StringBuilder("§8[");

        for (int i = 0; i < 10; i++) {
            if (i < bars) {
                if (percentage < 50) sb.append("§a■");
                else if (percentage < 75) sb.append("§e■");
                else if (percentage < 90) sb.append("§6■");
                else sb.append("§c■");
            } else {
                sb.append("§7■");
            }
        }

        sb.append("§8]");
        return sb.toString();
    }

    private String formatEnchantmentName(String enchantName) {
        return switch (enchantName.toLowerCase()) {
            case "efficiency" -> "§aEfficacité";
            case "fortune" -> "§aFortune";
            case "tokengreed" -> "§eToken Greed";
            case "expgreed" -> "§bExp Greed";
            case "moneygreed" -> "§6Money Greed";
            case "keygreed" -> "§dKey Greed";
            case "fuelefficiency" -> "§7Fuel Efficiency";
            case "beaconfinder" -> "§6Beacon Finder";
            default -> enchantName;
        };
    }

    private Material getEnchantmentMaterial(String enchantName) {
        return switch (enchantName.toLowerCase()) {
            case "efficiency" -> Material.DIAMOND_PICKAXE;
            case "fortune" -> Material.EMERALD;
            case "tokengreed" -> Material.GOLD_NUGGET;
            case "expgreed" -> Material.EXPERIENCE_BOTTLE;
            case "moneygreed" -> Material.EMERALD;
            case "keygreed" -> Material.TRIPWIRE_HOOK;
            case "fuelefficiency" -> Material.REDSTONE;
            case "beaconfinder" -> Material.BEACON;
            default -> Material.ENCHANTED_BOOK;
        };
    }

    // Méthodes d'action (à compléter selon vos besoins)

    private ItemStack createStartStopItem(PlayerData playerData) {
        boolean running = playerData.isAutominersRunning();
        ItemStack item = new ItemStack(running ? Material.REDSTONE_BLOCK : Material.EMERALD_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(running ? "§c🛑 §lARRÊTER" : "§a▶️ §lDÉMARRER");
        List<String> lore = new ArrayList<>();
        lore.add("§7" + (running ? "Arrêter" : "Démarrer") + " tous les automineurs");
        lore.add("§a🖱 Cliquez pour " + (running ? "arrêter" : "démarrer"));
        meta.setLore(lore);
        setItemAction(meta, "toggle_running", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFuelItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e⛽ §lCARBURANT");
        List<String> lore = new ArrayList<>();
        lore.add("§7Têtes disponibles: §e" + NumberFormatter.format(playerData.getAutominerFuel()));
        lore.add("§a🖱 Cliquez pour gérer");
        meta.setLore(lore);
        setItemAction(meta, "fuel_management", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWorldItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b🌍 §lMONDE: " + playerData.getAutominerWorld());
        List<String> lore = new ArrayList<>();
        lore.add("§7Monde actuel de minage");
        lore.add("§a🖱 Cliquez pour changer");
        meta.setLore(lore);
        setItemAction(meta, "world_management", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStorageItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d📦 §lSTOCKAGE");
        List<String> lore = new ArrayList<>();
        long total = playerData.getTotalStoredBlocks();
        long capacity = playerData.getAutominerStorageCapacity();
        lore.add("§7" + NumberFormatter.format(total) + "/" + NumberFormatter.format(capacity));
        lore.add("§a🖱 Cliquez pour gérer");
        meta.setLore(lore);
        setItemAction(meta, "storage_management", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCondenseItem() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6⚡ §lCONDENSATION");
        List<String> lore = new ArrayList<>();
        lore.add("§79 automineurs → 1 niveau supérieur");
        lore.add("§a🖱 Cliquez pour condenser");
        meta.setLore(lore);
        setItemAction(meta, "condense_autominers", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEnergyItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD); // Changé de REDSTONE à PLAYER_HEAD
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c⚡ §lCARBURANT (TÊTES)"); // Corrigé pour clarifier
        List<String> lore = new ArrayList<>();
        lore.add("§7Interface de gestion du carburant");
        lore.add("§7(têtes de joueur/monstre)");
        lore.add("§a🖱 Cliquez pour ouvrir");
        meta.setLore(lore);
        setItemAction(meta, "energy_management", "");
        item.setItemMeta(meta);
        return item;
    }

    // Méthodes utilitaires manquantes

    private ItemStack findAutominerItemInInventory(Player player, String uuid) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isAutominer(item)) {
                AutominerData data = AutominerData.fromItemStack(item,
                        plugin.getAutominerManager().getUuidKey(),
                        plugin.getAutominerManager().getTypeKey(),
                        plugin.getAutominerManager().getEnchantKey(),
                        plugin.getAutominerManager().getCristalKey());

                if (data != null && data.getUuid().equals(uuid)) {
                    return item;
                }
            }
        }
        return null;
    }

    private void updateAutominerInInventory(Player player, AutominerData autominer) {
        ItemStack updatedItem = autominer.toItemStack(
                plugin.getAutominerManager().getUuidKey(),
                plugin.getAutominerManager().getTypeKey(),
                plugin.getAutominerManager().getEnchantKey(),
                plugin.getAutominerManager().getCristalKey());

        // Trouve et remplace l'ancien item
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && isAutominer(item)) {
                AutominerData itemData = AutominerData.fromItemStack(item,
                        plugin.getAutominerManager().getUuidKey(),
                        plugin.getAutominerManager().getTypeKey(),
                        plugin.getAutominerManager().getEnchantKey(),
                        plugin.getAutominerManager().getCristalKey());
                if (itemData != null && itemData.getUuid().equals(autominer.getUuid())) {
                    player.getInventory().setItem(i, updatedItem);
                    break;
                }
            }
        }
    }

    private boolean isCristal(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        // Vérifie si l'item a la clé de cristal (simplifié pour l'instant)
        var container = item.getItemMeta().getPersistentDataContainer();
        return container.has(new org.bukkit.NamespacedKey(plugin, "cristal_uuid"),
                org.bukkit.persistence.PersistentDataType.STRING) ||
                container.has(new org.bukkit.NamespacedKey(plugin, "cristal_type"),
                        org.bukkit.persistence.PersistentDataType.STRING);
    }

    private boolean isGreedCristal(fr.prisontycoon.cristaux.CristalType type) {
        return switch (type) {
            case MONEY_BOOST, TOKEN_BOOST, XP_BOOST, MINERAL_GREED -> true;
            default -> false;
        };
    }

    // Méthodes pour les items des sous-menus

    private ItemStack createCurrentFuelInfoItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e⛽ §lCARBURANT ACTUEL");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Têtes disponibles: §e" + NumberFormatter.format(playerData.getAutominerFuel()));
        lore.add("");
        lore.add("§7Les automineurs consomment des têtes");
        lore.add("§7de joueur ou de monstre comme carburant.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAddFuelFromInventoryItem(Player player) {
        // Compte les têtes dans l'inventaire
        int headsCount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isPlayerHead(item)) {
                headsCount += item.getAmount();
            }
        }

        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a➕ §lAJOUTER DU CARBURANT");

        List<String> lore = new ArrayList<>();
        lore.add("§7Têtes dans votre inventaire: §e" + headsCount);
        lore.add("");
        lore.add("§7Ajoute toutes les têtes de votre");
        lore.add("§7inventaire au réservoir de carburant.");
        lore.add("");
        if (headsCount > 0) {
            lore.add("§a🖱 Cliquez pour ajouter " + headsCount + " têtes");
        } else {
            lore.add("§c❌ Aucune tête dans l'inventaire");
        }

        meta.setLore(lore);
        setItemAction(meta, "add_fuel_from_inventory", String.valueOf(headsCount));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWithdrawFuelItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.DROPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c➖ §lRETIRER DU CARBURANT");

        List<String> lore = new ArrayList<>();
        lore.add("§7Carburant disponible: §e" + NumberFormatter.format(playerData.getAutominerFuel()));
        lore.add("");
        lore.add("§7Retire des têtes du réservoir vers");
        lore.add("§7votre inventaire.");
        lore.add("");
        lore.add("§e🖱 Clic: Retirer 1 tête");
        lore.add("§e⇧ Shift-Clic: Retirer 64 têtes");

        meta.setLore(lore);
        setItemAction(meta, "withdraw_fuel", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCurrentWorldInfoItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b🌍 §lMONDE ACTUEL: " + playerData.getAutominerWorld());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Le monde détermine les blocs que");
        lore.add("§7vos automineurs vont miner.");
        lore.add("");
        lore.add("§7Plus le monde est proche de Z,");
        lore.add("§7plus les blocs sont rares et précieux.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRandomWorldRollItem() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e🎲 §lTIRER UN MONDE ALÉATOIRE");

        List<String> lore = new ArrayList<>();
        lore.add("§7Tire un monde aléatoire entre A et Z.");
        lore.add("§7Plus c'est proche de Z, plus c'est rare!");
        lore.add("");
        lore.add("§c⚠️ Coût variable en beacons selon le résultat");
        lore.add("");
        lore.add("§a🖱 Cliquez pour tirer un monde");

        meta.setLore(lore);
        setItemAction(meta, "roll_random_world", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createManualWorldChangeItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6📝 §lCHANGER DE MONDE MANUELLEMENT");

        List<String> lore = new ArrayList<>();
        lore.add("§7Choisissez précisément le monde");
        lore.add("§7que vous voulez (A à Z).");
        lore.add("");
        lore.add("§e💰 Coût: §7A=1 beacon, B=2 beacons, ..., Z=26 beacons");
        lore.add("");
        lore.add("§a🖱 Cliquez pour ouvrir le sélecteur");

        meta.setLore(lore);
        setItemAction(meta, "manual_world_change", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStorageInfoItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d📦 §lINFORMATIONS DU STOCKAGE");

        long totalStored = playerData.getTotalStoredBlocks();
        long capacity = playerData.getAutominerStorageCapacity();
        double percentage = playerData.getStorageUsagePercentage();

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§6📊 §lÉTAT DU STOCKAGE");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§7▸ Utilisé: §d" + NumberFormatter.format(totalStored) + " blocs");
        lore.add("§7▸ Capacité: §d" + NumberFormatter.format(capacity) + " blocs");
        lore.add("§7▸ Libre: §a" + NumberFormatter.format(capacity - totalStored) + " blocs");
        lore.add("");

        String progressBar = createProgressBar(percentage);
        lore.add("§7▸ " + progressBar + " §7(" + String.format("%.1f", percentage) + "%)");
        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUpgradeStorageItem(PlayerData playerData) {
        long currentCapacity = playerData.getAutominerStorageCapacity();
        long nextCapacity = getNextStorageCapacity(currentCapacity);
        int cost = getStorageUpgradeCost(currentCapacity);

        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        if (nextCapacity > currentCapacity) {
            meta.setDisplayName("§a⬆️ §lAMÉLIORER LE STOCKAGE");

            List<String> lore = new ArrayList<>();
            lore.add("§7Capacité actuelle: §d" + NumberFormatter.format(currentCapacity));
            lore.add("§7Prochaine capacité: §a" + NumberFormatter.format(nextCapacity));
            lore.add("");
            lore.add("§e💰 Coût: §6" + cost + " beacons");
            lore.add("");
            lore.add("§a🖱 Cliquez pour améliorer");

            meta.setLore(lore);
            setItemAction(meta, "upgrade_storage", "");
        } else {
            meta.setDisplayName("§2✅ §lSTOCKAGE MAXIMUM");

            List<String> lore = new ArrayList<>();
            lore.add("§7Vous avez atteint la capacité");
            lore.add("§7maximale de stockage!");
            lore.add("");
            lore.add("§2✅ 2M blocs - Niveau maximum");

            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createEmptyStorageItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c🗑️ §lVIDER LE STOCKAGE");

        List<String> lore = new ArrayList<>();
        lore.add("§c⚠️ ATTENTION: Cette action est irréversible!");
        lore.add("");
        lore.add("§7Vide complètement votre stockage");
        lore.add("§7et récupère tous les blocs dans");
        lore.add("§7votre inventaire (si possible).");
        lore.add("");
        lore.add("§c🖱 Cliquez pour vider");

        meta.setLore(lore);
        setItemAction(meta, "empty_storage", "");
        item.setItemMeta(meta);
        return item;
    }

    private void fillStoredResourcesDisplay(Inventory gui, PlayerData playerData) {
        // Affiche les ressources stockées dans des slots spécifiques
        var storedBlocks = playerData.getAutominerStoredBlocks();

        int[] displaySlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int slotIndex = 0;

        for (var entry : storedBlocks.entrySet()) {
            if (slotIndex >= displaySlots.length) break;

            Material material = entry.getKey();
            long amount = entry.getValue();

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§e" + material.name().replace('_', ' '));

            List<String> lore = new ArrayList<>();
            lore.add("§7Quantité stockée: §a" + NumberFormatter.format(amount));
            lore.add("");
            lore.add("§a🖱 Cliquez pour récupérer");

            meta.setLore(lore);
            setItemAction(meta, "withdraw_resource", material.name() + ":" + amount);
            item.setItemMeta(meta);

            gui.setItem(displaySlots[slotIndex], item);
            slotIndex++;
        }
    }

    private ItemStack createCondenseInstructionsItem() {
        ItemStack item = new ItemStack(Material.CRAFTING_TABLE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6⚡ §lCONDENSATION D'AUTOMINEURS");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e📝 §lCOMMENT ÇA MARCHE");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§79 automineurs identiques = 1 niveau supérieur");
        lore.add("");
        lore.add("§7▸ §e9 Pierre §7→ §f1 Fer");
        lore.add("§7▸ §f9 Fer §7→ §e1 Or");
        lore.add("§7▸ §e9 Or §7→ §b1 Diamant");
        lore.add("§7▸ §b9 Diamant §7→ §a1 Émeraude");
        lore.add("§7▸ §a9 Émeraude §7→ §61 Beacon");
        lore.add("");
        lore.add("§cLes Beacon ne peuvent pas être condensés");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCondenseOptionItem(Player player, AutominerType type) {
        // Compte les automineurs de ce type dans l'inventaire
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isAutominer(item)) {
                AutominerData data = AutominerData.fromItemStack(item,
                        plugin.getAutominerManager().getUuidKey(),
                        plugin.getAutominerManager().getTypeKey(),
                        plugin.getAutominerManager().getEnchantKey(),
                        plugin.getAutominerManager().getCristalKey());
                if (data != null && data.getType() == type) {
                    count++;
                }
            }
        }

        ItemStack item = new ItemStack(type.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getColoredName() + " §fAutomineurs");

        List<String> lore = new ArrayList<>();
        lore.add("§7Dans votre inventaire: §e" + count + "§8/§e9");
        lore.add("");

        AutominerType nextTier = type.getNextTier();
        if (nextTier != null) {
            lore.add("§7Résultat: §a1x " + nextTier.getColoredName());
            lore.add("");

            if (count >= 9) {
                lore.add("§a✅ Vous pouvez condenser!");
                lore.add("§a🖱 Cliquez pour condenser");
            } else {
                lore.add("§c❌ Pas assez d'automineurs");
                lore.add("§7Il vous en manque " + (9 - count));
            }
        } else {
            lore.add("§c❌ Type maximum - Pas de condensation");
        }

        meta.setLore(lore);
        if (count >= 9 && nextTier != null) {
            setItemAction(meta, "condense_type", type.name());
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackToMainButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7◀ §lRETOUR AU MENU PRINCIPAL");
        setItemAction(meta, "back_to_main", "");
        item.setItemMeta(meta);
        return item;
    }

    private boolean isPlayerHead(ItemStack item) {
        return item != null && (item.getType() == Material.PLAYER_HEAD ||
                item.getType() == Material.ZOMBIE_HEAD ||
                item.getType() == Material.SKELETON_SKULL ||
                item.getType() == Material.CREEPER_HEAD ||
                item.getType() == Material.WITHER_SKELETON_SKULL);
    }

    // Méthodes utilitaires de stockage
    private long getNextStorageCapacity(long current) {
        if (current < 10000) return 10000;
        if (current < 50000) return 50000;
        if (current < 100000) return 100000;
        if (current < 500000) return 500000;
        if (current < 1000000) return 1000000;
        if (current < 2000000) return 2000000;
        return current; // Maximum atteint
    }

    private int getStorageUpgradeCost(long current) {
        if (current < 10000) return 1;
        if (current < 50000) return 5;
        if (current < 100000) return 10;
        if (current < 500000) return 25;
        if (current < 1000000) return 50;
        if (current < 2000000) return 100;
        return Integer.MAX_VALUE; // Pas d'amélioration possible
    }

    /**
     * Crée un ItemStack représentant un cristal
     */
    private ItemStack createCristalItem(Cristal cristal) {
        ItemStack item = new ItemStack(Material.PRISMARINE_SHARD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§d💎 " + cristal.getType().getDisplayName() + " §7Niveau " + cristal.getNiveau());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§d✨ §lCRISTAL GREED");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§7▸ Type: §d" + cristal.getType().getDisplayName());
        lore.add("§7▸ Niveau: §d" + cristal.getNiveau());
        lore.add("§7▸ Bonus: §a+" + (cristal.getNiveau() * 5) + "%");
        lore.add("");
        lore.add("§7Peut être appliqué sur les automineurs");
        lore.add("§7pour augmenter leurs performances.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);

        // Stockage des données du cristal
        var container = meta.getPersistentDataContainer();
        container.set(new org.bukkit.NamespacedKey(plugin, "cristal_uuid"),
                org.bukkit.persistence.PersistentDataType.STRING, cristal.getUuid());
        container.set(new org.bukkit.NamespacedKey(plugin, "cristal_type"),
                org.bukkit.persistence.PersistentDataType.STRING, cristal.getType().name());
        container.set(new org.bukkit.NamespacedKey(plugin, "cristal_level"),
                org.bukkit.persistence.PersistentDataType.INTEGER, cristal.getNiveau());

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHelpItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e📖 §lAIDE");
        List<String> lore = new ArrayList<>();
        lore.add("§7Guide d'utilisation des automineurs");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // Méthodes de création pour le menu de gestion individuelle
    private ItemStack createAutominerDetailsItem(AutominerData autominer) {
        ItemStack item = new ItemStack(autominer.getType().getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6⚙️ §l" + autominer.getType().getDisplayName().toUpperCase());
        // ... lore détaillée
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDisabledEnchantmentItem(String enchantName) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7✨ " + formatEnchantmentName(enchantName) + " §c(INCOMPATIBLE)");
        List<String> lore = new ArrayList<>();
        lore.add("§7Cet enchantement n'est pas");
        lore.add("§7compatible avec ce type d'automineur.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCristalInfoItem(AutominerData autominer) {
        ItemStack item = new ItemStack(Material.PRISMARINE_CRYSTALS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d💎 §lINFO CRISTAUX");
        List<String> lore = new ArrayList<>();
        lore.add("§7Cristaux appliqués: §d" + autominer.getAppliedCristals().size() + "/2");
        long nextCost = (autominer.getAppliedCristals().size() + 1) * 1000L;
        lore.add("§7Coût prochain cristal: §e" + nextCost + " XP");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7◀ §lRETOUR");
        setItemAction(meta, "back_to_main", "");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRemoveAutominerButton(AutominerData autominer) {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c❌ §lRETIRER L'AUTOMINEUR");
        setItemAction(meta, "remove_autominer_confirm", autominer.getUuid());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHelpButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e❓ §lAIDE");
        item.setItemMeta(meta);
        return item;
    }

    // Méthodes d'action (stubs - à implémenter)
    private void placeAutominerFromHand(Player player, int slot) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (!isAutominer(handItem)) {
            player.sendMessage("§c❌ Vous devez tenir un automineur en main!");
            return;
        }

        AutominerData data = AutominerData.fromItemStack(handItem,
                plugin.getAutominerManager().getUuidKey(),
                plugin.getAutominerManager().getTypeKey(),
                plugin.getAutominerManager().getEnchantKey(),
                plugin.getAutominerManager().getCristalKey());

        if (data == null) {
            player.sendMessage("§c❌ Automineur invalide!");
            return;
        }

        if (plugin.getAutominerManager().placeAutominer(player, data)) {
            handItem.setAmount(handItem.getAmount() - 1); // Consommer l'item
            openMainMenu(player); // Refresh
        }
    }

    private void upgradeAutominerEnchantment(Player player, String autominerUuid, String enchantName, int levels) {
        // Trouve l'automineur dans l'inventaire
        AutominerData autominer = findAutominerInInventory(player, autominerUuid);
        ItemStack autominerItem = findAutominerItemInInventory(player, autominerUuid);

        if (autominer == null || autominerItem == null) {
            player.sendMessage("§c❌ Automineur introuvable!");
            return;
        }

        // Utilise l'AutominerEnchantmentManager pour l'amélioration
        boolean success = plugin.getAutominerEnchantmentManager().upgradeEnchantment(player, autominer, enchantName, levels);

        if (success) {
            // Met à jour l'item dans l'inventaire avec les nouvelles données
            ItemStack updatedItem = autominer.toItemStack(
                    plugin.getAutominerManager().getUuidKey(),
                    plugin.getAutominerManager().getTypeKey(),
                    plugin.getAutominerManager().getEnchantKey(),
                    plugin.getAutominerManager().getCristalKey());

            // Remplace l'ancien item
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && isAutominer(item)) {
                    AutominerData itemData = AutominerData.fromItemStack(item,
                            plugin.getAutominerManager().getUuidKey(),
                            plugin.getAutominerManager().getTypeKey(),
                            plugin.getAutominerManager().getEnchantKey(),
                            plugin.getAutominerManager().getCristalKey());
                    if (itemData != null && itemData.getUuid().equals(autominerUuid)) {
                        player.getInventory().setItem(i, updatedItem);
                        break;
                    }
                }
            }

            // Rouvre le menu de gestion
            openAutominerManagementMenu(player, autominerUuid);
        }
    }

    private void applyCristalFromHand(Player player, String autominerUuid, int slot) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (!isCristal(handItem)) {
            player.sendMessage("§c❌ Vous devez tenir un cristal Greed en main!");
            return;
        }

        // Trouve l'automineur
        AutominerData autominer = findAutominerInInventory(player, autominerUuid);
        if (autominer == null) {
            player.sendMessage("§c❌ Automineur introuvable!");
            return;
        }

        // Récupère le cristal depuis l'item (implémentation simplifiée)
        var container = handItem.getItemMeta().getPersistentDataContainer();
        String cristalUuid = container.get(new org.bukkit.NamespacedKey(plugin, "cristal_uuid"),
                org.bukkit.persistence.PersistentDataType.STRING);
        String cristalTypeStr = container.get(new org.bukkit.NamespacedKey(plugin, "cristal_type"),
                org.bukkit.persistence.PersistentDataType.STRING);
        Integer cristalLevel = container.get(new org.bukkit.NamespacedKey(plugin, "cristal_level"),
                org.bukkit.persistence.PersistentDataType.INTEGER);

        if (cristalUuid == null || cristalTypeStr == null || cristalLevel == null) {
            player.sendMessage("§c❌ Cristal invalide!");
            return;
        }

        // Crée un objet Cristal temporaire
        fr.prisontycoon.cristaux.CristalType cristalType;
        try {
            cristalType = fr.prisontycoon.cristaux.CristalType.valueOf(cristalTypeStr);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§c❌ Type de cristal invalide!");
            return;
        }

        Cristal cristal = new Cristal(cristalUuid, cristalLevel, cristalType, false);

        // Vérifie le type de cristal (doit être Greed)
        if (!isGreedCristal(cristal.getType())) {
            player.sendMessage("§c❌ Seuls les cristaux Greed peuvent être appliqués sur les automineurs!");
            return;
        }

        // Calcule le coût en XP (coût basique selon le nombre de cristaux déjà appliqués)
        long xpCost = (autominer.getAppliedCristals().size() + 1) * 1000L; // 1000, 2000, 3000... XP
        if (player.getTotalExperience() < xpCost) {
            player.sendMessage("§c❌ XP insuffisant! Requis: §e" + xpCost + " XP");
            return;
        }

        // Application du cristal
        if (autominer.applyCristal(cristal)) {
            // Consomme l'XP et l'item
            player.giveExp(-(int)xpCost);
            handItem.setAmount(handItem.getAmount() - 1);

            // Met à jour l'automineur dans l'inventaire
            updateAutominerInInventory(player, autominer);

            player.sendMessage("§a✅ Cristal " + cristal.getType().getDisplayName() +
                    " §aappliqué sur l'automineur!");

            // Rouvre le menu
            openAutominerManagementMenu(player, autominerUuid);
        } else {
            player.sendMessage("§c❌ Impossible d'appliquer le cristal (slots pleins?)");
        }
    }

    private void removeCristalFromAutominer(Player player, String autominerUuid, String cristalUuid) {
        // Trouve l'automineur
        AutominerData autominer = findAutominerInInventory(player, autominerUuid);
        if (autominer == null) {
            player.sendMessage("§c❌ Automineur introuvable!");
            return;
        }

        // Retire le cristal
        Cristal removedCristal = autominer.removeCristal(cristalUuid);
        if (removedCristal == null) {
            player.sendMessage("§c❌ Cristal introuvable!");
            return;
        }

        // Donne le cristal au joueur (création simplifiée d'item cristal)
        if (player.getInventory().firstEmpty() != -1) {
            ItemStack cristalItem = createCristalItem(removedCristal);
            player.getInventory().addItem(cristalItem);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(),
                    createCristalItem(removedCristal));
            player.sendMessage("§e⚠️ Inventaire plein! Le cristal a été jeté au sol.");
        }

        // Met à jour l'automineur
        updateAutominerInInventory(player, autominer);

        player.sendMessage("§a✅ Cristal " + removedCristal.getType().getDisplayName() + " §aretiré!");

        // Rouvre le menu
        openAutominerManagementMenu(player, autominerUuid);
    }

    // Méthodes de sous-menus
    public void openFuelManagementMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, "§e⛽ §lGESTION DU CARBURANT §e⛽");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Bordures
        fillBorders(gui);

        // Informations du carburant actuel (centre)
        gui.setItem(22, createCurrentFuelInfoItem(playerData));

        // Ajouter des têtes depuis l'inventaire
        gui.setItem(19, createAddFuelFromInventoryItem(player));

        // Retrait de têtes (vers l'inventaire)
        gui.setItem(25, createWithdrawFuelItem(playerData));

        // Bouton retour
        gui.setItem(40, createBackToMainButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    public void openWorldManagementMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, "§b🌍 §lGESTION DU MONDE §b🌍");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Bordures
        fillBorders(gui);

        // Monde actuel (centre)
        gui.setItem(22, createCurrentWorldInfoItem(playerData));

        // Tirer un monde aléatoire
        gui.setItem(19, createRandomWorldRollItem());

        // Changer de monde manuellement (A-Z)
        gui.setItem(25, createManualWorldChangeItem());

        // Bouton retour
        gui.setItem(40, createBackToMainButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.0f, 1.0f);
    }

    public void openStorageManagementMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§d📦 §lGESTION DU STOCKAGE §d📦");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Bordures
        fillBorders(gui);

        // Informations du stockage
        gui.setItem(4, createStorageInfoItem(playerData));

        // Améliorer le stockage
        gui.setItem(22, createUpgradeStorageItem(playerData));

        // Vider le stockage
        gui.setItem(31, createEmptyStorageItem(playerData));

        // Affichage des ressources stockées
        fillStoredResourcesDisplay(gui, playerData);

        // Bouton retour
        gui.setItem(49, createBackToMainButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    public void openCondenseMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, "§6⚡ §lCONDENSATION D'AUTOMINEURS §6⚡");

        // Bordures
        fillBorders(gui);

        // Instructions (centre)
        gui.setItem(22, createCondenseInstructionsItem());

        // Options de condensation pour chaque type
        int[] slots = {19, 20, 21, 23, 24, 25};
        AutominerType[] types = {AutominerType.PIERRE, AutominerType.FER, AutominerType.OR,
                AutominerType.DIAMANT, AutominerType.EMERAUDE};

        for (int i = 0; i < types.length && i < slots.length; i++) {
            gui.setItem(slots[i], createCondenseOptionItem(player, types[i]));
        }

        // Bouton retour
        gui.setItem(40, createBackToMainButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CRAFTER_CRAFT, 1.0f, 1.0f);
    }

    private void openEnergyManagementMenu(Player player) {
        // L'énergie = carburant (têtes), donc on redirige vers le menu carburant
        openFuelManagementMenu(player);
    }

    /**
     * Gère les clics dans le menu de carburant
     */
    public void handleFuelMenuClick(Player player, ItemStack item, ClickType clickType) {
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "autominer_action"),
                org.bukkit.persistence.PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "autominer_value"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "add_fuel_from_inventory" -> {
                addFuelFromInventory(player);
                plugin.getAutominerGUI().openFuelManagementMenu(player); // Refresh
            }
            case "withdraw_fuel" -> {
                int amount = clickType.isShiftClick() ? 64 : 1;
                withdrawFuel(player, amount);
                plugin.getAutominerGUI().openFuelManagementMenu(player); // Refresh
            }
            case "back_to_main" -> {
                plugin.getAutominerGUI().openMainMenu(player);
            }
        }
    }

    /**
     * Gère les clics dans le menu de monde
     */
    public void handleWorldMenuClick(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "autominer_action"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "roll_random_world" -> {
                rollRandomWorld(player);
                plugin.getAutominerGUI().openWorldManagementMenu(player); // Refresh
            }
            case "manual_world_change" -> {
                openWorldSelector(player);
            }
            case "back_to_main" -> {
                plugin.getAutominerGUI().openMainMenu(player);
            }
        }
    }

    /**
     * Gère les clics dans le menu de stockage
     */
    public void handleStorageMenuClick(Player player, ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "autominer_action"),
                org.bukkit.persistence.PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "autominer_value"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "upgrade_storage" -> {
                upgradeStorage(player);
                plugin.getAutominerGUI().openStorageManagementMenu(player); // Refresh
            }
            case "empty_storage" -> {
                emptyStorage(player);
                plugin.getAutominerGUI().openStorageManagementMenu(player); // Refresh
            }
            case "withdraw_resource" -> {
                if (value != null && value.contains(":")) {
                    String[] parts = value.split(":");
                    org.bukkit.Material material = org.bukkit.Material.valueOf(parts[0]);
                    withdrawResource(player, material);
                    plugin.getAutominerGUI().openStorageManagementMenu(player); // Refresh
                }
            }
            case "back_to_main" -> {
                plugin.getAutominerGUI().openMainMenu(player);
            }
        }
    }

    /**
     * Gère les clics dans le menu de condensation
     */
    public void handleCondenseMenuClick(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "autominer_action"),
                org.bukkit.persistence.PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey(plugin, "autominer_value"),
                org.bukkit.persistence.PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "condense_type" -> {
                if (value != null) {
                    try {
                        fr.prisontycoon.autominers.AutominerType type =
                                fr.prisontycoon.autominers.AutominerType.valueOf(value);
                        plugin.getAutominerManager().condenseAutominers(player, type);
                        plugin.getAutominerGUI().openCondenseMenu(player); // Refresh
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("§c❌ Type d'automineur invalide!");
                    }
                }
            }
            case "back_to_main" -> {
                plugin.getAutominerGUI().openMainMenu(player);
            }
        }
    }

    /**
     * Ajoute du carburant depuis l'inventaire du joueur
     */
    private void addFuelFromInventory(Player player) {
        int headsCount = 0;

        // Compte et retire les têtes de joueur dans l'inventaire
        for (var item : player.getInventory().getContents()) {
            if (item != null && isPlayerHead(item)) {
                headsCount += item.getAmount();
            }
        }

        if (headsCount == 0) {
            player.sendMessage("§c❌ Vous n'avez aucune tête dans votre inventaire!");
            return;
        }

        // Retire toutes les têtes et les ajoute au carburant
        player.getInventory().remove(org.bukkit.Material.PLAYER_HEAD);
        player.getInventory().remove(org.bukkit.Material.ZOMBIE_HEAD);
        player.getInventory().remove(org.bukkit.Material.SKELETON_SKULL);
        player.getInventory().remove(org.bukkit.Material.CREEPER_HEAD);
        player.getInventory().remove(org.bukkit.Material.WITHER_SKELETON_SKULL);

        plugin.getAutominerManager().addFuelHeads(player, headsCount);

        player.sendMessage("§a✅ " + headsCount + " têtes ajoutées au carburant!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Retire du carburant vers l'inventaire
     */
    private void withdrawFuel(Player player, int amount) {
        var playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentFuel = playerData.getAutominerFuel();

        if (currentFuel < amount) {
            player.sendMessage("§c❌ Pas assez de carburant! Vous avez " + currentFuel + " têtes.");
            return;
        }

        // Vérification de place dans l'inventaire
        int freeSlots = 0;
        for (var item : player.getInventory().getContents()) {
            if (item == null) freeSlots++;
        }

        int slotsNeeded = (amount + 63) / 64; // Nombre de stacks nécessaires
        if (freeSlots < slotsNeeded) {
            player.sendMessage("§c❌ Pas assez de place dans l'inventaire! Requis: " + slotsNeeded + " slots");
            return;
        }

        // Retire du carburant et donne les têtes
        playerData.setAutominerFuel(currentFuel - amount);

        while (amount > 0) {
            int stackSize = Math.min(amount, 64);
            org.bukkit.inventory.ItemStack heads = new org.bukkit.inventory.ItemStack(org.bukkit.Material.PLAYER_HEAD, stackSize);
            player.getInventory().addItem(heads);
            amount -= stackSize;
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        player.sendMessage("§a✅ Têtes retirées du carburant!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Tire un monde aléatoire
     */
    private void rollRandomWorld(Player player) {
        var playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String newWorld = playerData.rollRandomWorld();

        if (plugin.getAutominerManager().changeWorld(player, newWorld)) {
            player.sendMessage("§a✅ Monde tiré: §e" + newWorld + "!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
    }

    /**
     * Ouvre le sélecteur de monde manuel
     */
    private void openWorldSelector(Player player) {
        // Simple implémentation : permet de choisir A, B, C... Z
        player.sendMessage("§b🌍 Sélecteur de monde manuel - À implémenter avec une interface dédiée");
        player.sendMessage("§7Pour l'instant, utilisez le tirage aléatoire!");
    }

    /**
     * Améliore le stockage
     */
    private void upgradeStorage(Player player) {
        if (plugin.getAutominerManager().upgradeStorage(player)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    /**
     * Vide le stockage
     */
    private void emptyStorage(Player player) {
        var playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        var storedBlocks = playerData.getAutominerStoredBlocks();

        if (storedBlocks.isEmpty()) {
            player.sendMessage("§c❌ Le stockage est déjà vide!");
            return;
        }

        int itemsGiven = 0;
        for (var entry : storedBlocks.entrySet()) {
            org.bukkit.Material material = entry.getKey();
            long amount = entry.getValue();

            // Donne les items par stacks de 64
            while (amount > 0 && player.getInventory().firstEmpty() != -1) {
                int stackSize = (int) Math.min(amount, 64);
                org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, stackSize);
                player.getInventory().addItem(item);
                amount -= stackSize;
                itemsGiven += stackSize;
            }

            // Si il reste des items, les drop au sol
            if (amount > 0) {
                while (amount > 0) {
                    int stackSize = (int) Math.min(amount, 64);
                    org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, stackSize);
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                    amount -= stackSize;
                    itemsGiven += stackSize;
                }
                player.sendMessage("§e⚠️ Inventaire plein! Certains items ont été jetés au sol.");
            }
        }

        // Vide le stockage
        playerData.clearStoredBlocks();
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ Stockage vidé! " + itemsGiven + " items récupérés.");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
    }

    /**
     * Retire une ressource spécifique du stockage
     */
    private void withdrawResource(Player player, org.bukkit.Material material) {
        var playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long stored = playerData.getStoredBlockCount(material);

        if (stored == 0) {
            player.sendMessage("§c❌ Aucun " + material.name().toLowerCase() + " en stockage!");
            return;
        }

        // Calcule combien on peut donner (limité par l'espace d'inventaire)
        int freeSlots = 0;
        for (var item : player.getInventory().getContents()) {
            if (item == null) freeSlots++;
        }

        long maxCanWithdraw = freeSlots * 64L;
        long toWithdraw = Math.min(stored, maxCanWithdraw);

        if (toWithdraw == 0) {
            player.sendMessage("§c❌ Inventaire plein!");
            return;
        }

        // Retire du stockage et donne au joueur
        playerData.removeStoredBlocks(material, toWithdraw);

        long remaining = toWithdraw;
        while (remaining > 0) {
            int stackSize = (int) Math.min(remaining, 64);
            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material, stackSize);
            player.getInventory().addItem(item);
            remaining -= stackSize;
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        player.sendMessage("§a✅ " + toWithdraw + "x " + material.name().toLowerCase() + " récupérés!");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

        if (toWithdraw < stored) {
            remaining = stored - toWithdraw;
            player.sendMessage("§7Il reste " + remaining + "x " + material.name().toLowerCase() + " en stockage.");
        }
    }

    /**
     * Utilitaire pour débugger les clics
     */
    private void debugClick(Player player, String menu, int slot, String action) {
        if (plugin.getConfig().getBoolean("debug.autominers", false)) {
            plugin.getPluginLogger().debug("Joueur " + player.getName() +
                    " - Menu: " + menu +
                    " - Slot: " + slot +
                    " - Action: " + action);
        }
    }

    /**
     * Gère les erreurs de menu de façon gracieuse
     */
    private void handleMenuError(Player player, String menu, Exception e) {
        player.sendMessage("§c❌ Une erreur est survenue dans le menu " + menu);
        plugin.getPluginLogger().severe("Erreur dans le menu " + menu + " pour " + player.getName() + ": " + e.getMessage());

        // Ferme le menu et rouvre le menu principal en cas d'erreur
        player.closeInventory();

        // Délai pour éviter les problèmes de concurrence
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getAutominerGUI().openMainMenu(player);
        }, 5L);
    }

    /**
     * Vérifie les permissions pour une action spécifique
     */
    private boolean hasPermission(Player player, String action) {
        return switch (action) {
            case "admin" -> player.hasPermission("specialmine.admin");
            case "vip" -> player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin");
            case "basic" -> player.hasPermission("specialmine.basic") ||
                    player.hasPermission("specialmine.vip") ||
                    player.hasPermission("specialmine.admin");
            default -> true;
        };
    }
}