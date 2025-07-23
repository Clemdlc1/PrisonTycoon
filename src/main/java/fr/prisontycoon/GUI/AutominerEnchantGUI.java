package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.cristaux.CristalType;
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
import java.util.Map;

/**
 * Interface graphique pour les enchantements et cristaux d'un automineur spécifique
 * Menu séparé du menu principal pour une meilleure organisation
 */
public class AutominerEnchantGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    // Layout du menu (54 slots - 6 lignes)
    private static final int AUTOMINER_INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 45;
    private static final int APPLY_CRISTAL_SLOT = 53;

    // Emplacements des enchantements (ligne 2-3)
    private static final int[] ENCHANTMENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    // Emplacements des cristaux appliqués (ligne 5)
    private static final int CRISTAL_1_SLOT = 37;
    private static final int CRISTAL_2_SLOT = 38;

    public AutominerEnchantGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.valueKey = new NamespacedKey(plugin, "gui_value");
    }

    /**
     * Ouvre le menu d'enchantement pour un automineur spécifique
     */
    public void openEnchantMenu(Player player, String autominerUuid) {
        // Trouve l'automineur dans l'inventaire
        AutominerData autominer = findAutominerInInventory(player, autominerUuid);
        if (autominer == null) {
            player.sendMessage("§c❌ Automineur introuvable!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "§6⚡ Amélioration - " + autominer.getType().getColoredName());

        // Bordures décoratives
        fillBorders(inv);

        // Informations de l'automineur (slot 4)
        inv.setItem(AUTOMINER_INFO_SLOT, createAutominerInfoItem(autominer));

        // Enchantements disponibles
        populateEnchantments(inv, autominer);

        // Cristaux appliqués
        populateAppliedCristals(inv, autominer);

        // Boutons d'action
        inv.setItem(BACK_BUTTON_SLOT, createBackButton());
        inv.setItem(APPLY_CRISTAL_SLOT, createApplyCristalButton());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * Crée l'item d'information de l'automineur
     */
    private ItemStack createAutominerInfoItem(AutominerData autominer) {
        ItemStack item = new ItemStack(autominer.getType().getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(autominer.getType().getColoredName() + " §6Automineur");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7UUID: §f" + autominer.getUuid().substring(0, 8) + "...");
        lore.add("");

        // Statistiques actuelles
        lore.add("§e⚡ §lSTATISTIQUES ACTUELLES");
        lore.add("§7▸ Efficacité: §a" + autominer.getTotalEfficiency());
        lore.add("§7▸ Fortune: §a" + autominer.getTotalFortune());
        if (autominer.getEnchantmentLevel("tokengreed") > 0) {
            lore.add("§7▸ Token Greed: §a+" + autominer.getEnchantmentLevel("tokengreed") + "%");
        }
        if (autominer.getEnchantmentLevel("expgreed") > 0) {
            lore.add("§7▸ Exp Greed: §a+" + autominer.getEnchantmentLevel("expgreed") + "%");
        }
        if (autominer.getEnchantmentLevel("moneygreed") > 0) {
            lore.add("§7▸ Money Greed: §a+" + autominer.getEnchantmentLevel("moneygreed") + "%");
        }
        if (autominer.getEnchantmentLevel("keygreed") > 0) {
            lore.add("§7▸ Key Greed: §a+" + autominer.getEnchantmentLevel("keygreed") + "%");
        }
        if (autominer.getEnchantmentLevel("fuelefficiency") > 0) {
            lore.add("§7▸ Fuel Efficiency: §a+" + autominer.getEnchantmentLevel("fuelefficiency") + "%");
        }
        lore.add("");

        // Cristaux appliqués
        if (!autominer.getAppliedCristals().isEmpty()) {
            lore.add("§d💎 §lCRISTAUX APPLIQUÉS");
            for (Cristal cristal : autominer.getAppliedCristals()) {
                lore.add("§7▸ " + cristal.getType().getDisplayName() + " §7Niv." + cristal.getNiveau());
            }
            lore.add("");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Remplit les emplacements d'enchantements
     */
    private void populateEnchantments(Inventory inv, AutominerData autominer) {
        AutominerEnchantmentManager enchantManager = plugin.getAutominerEnchantmentManager();
        String[] availableEnchants = {"efficiency", "fortune", "tokengreed", "expgreed", "moneygreed", "keygreed", "fuelefficiency", "beaconfinder"};

        int slotIndex = 0;
        for (String enchantName : availableEnchants) {
            if (slotIndex >= ENCHANTMENT_SLOTS.length) break;

            // Vérifier si l'enchantement est disponible pour ce type d'automineur
            if (!isEnchantmentAvailableForType(enchantName, autominer.getType())) {
                continue;
            }

            int currentLevel = autominer.getEnchantmentLevel(enchantName);
            int maxLevel = getMaxEnchantmentLevel(enchantName, autominer.getType());

            ItemStack enchantItem = createEnchantmentItem(enchantName, currentLevel, maxLevel, autominer);
            inv.setItem(ENCHANTMENT_SLOTS[slotIndex], enchantItem);
            slotIndex++;
        }
    }

    /**
     * Crée un item d'enchantement
     */
    private ItemStack createEnchantmentItem(String enchantName, int currentLevel, int maxLevel, AutominerData autominer) {
        Material material = getEnchantmentMaterial(enchantName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String displayName = getEnchantmentDisplayName(enchantName);
        boolean isMaxed = currentLevel >= maxLevel;

        meta.setDisplayName((isMaxed ? "§a✓ " : "§e⚡ ") + "§l" + displayName);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Niveau actuel: §f" + currentLevel + "§7/§f" + maxLevel);

        if (currentLevel < maxLevel) {
            long upgradeCost = calculateUpgradeCost(enchantName, currentLevel + 1, autominer.getType());
            lore.add("§7Coût amélioration: §6" + NumberFormatter.format(upgradeCost) + " Tokens");
            lore.add("");
            lore.add("§e🖱 §lCLIC: §eAméliorer d'1 niveau");
            lore.add("§e⇧ §lSHIFT+CLIC: §eAméliorer de 10 niveaux");
        } else {
            lore.add("§a✅ Niveau maximum atteint!");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "upgrade_enchant", enchantName + ":" + autominer.getUuid());
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Remplit les cristaux appliqués
     */
    private void populateAppliedCristals(Inventory inv, AutominerData autominer) {
        List<Cristal> appliedCristals = autominer.getAppliedCristals();

        // Slot 1 des cristaux
        if (appliedCristals.size() > 0) {
            inv.setItem(CRISTAL_1_SLOT, createAppliedCristalItem(appliedCristals.get(0), 1, autominer.getUuid()));
        } else {
            inv.setItem(CRISTAL_1_SLOT, createEmptyCristalSlot(1));
        }

        // Slot 2 des cristaux
        if (appliedCristals.size() > 1) {
            inv.setItem(CRISTAL_2_SLOT, createAppliedCristalItem(appliedCristals.get(1), 2, autominer.getUuid()));
        } else {
            inv.setItem(CRISTAL_2_SLOT, createEmptyCristalSlot(2));
        }
    }

    /**
     * Crée un item de cristal appliqué
     */
    private ItemStack createAppliedCristalItem(Cristal cristal, int slotNumber, String autominerUuid) {
        ItemStack item = new ItemStack(Material.valueOf(cristal.getType().getDisplayName()));
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(cristal.getType().getDisplayName() + " §fNiv." + cristal.getNiveau());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Emplacement: §f" + slotNumber);
        lore.add("§7Bonus: §a+" + cristal.getType() + "%");
        lore.add("");
        lore.add("§c🗑 §lCLIC: §cRetirer le cristal");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "remove_cristal", slotNumber + ":" + autominerUuid);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un emplacement vide pour cristal
     */
    private ItemStack createEmptyCristalSlot(int slotNumber) {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7💎 §lEMPLACEMENT CRISTAL " + slotNumber + " §8(VIDE)");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Aucun cristal appliqué sur cet emplacement.");
        lore.add("");
        lore.add("§ePour appliquer un cristal:");
        lore.add("§7▸ Cliquez sur le bouton §e'Appliquer Cristal'");
        lore.add("§7▸ Placez un cristal §aGreed §7dans votre inventaire");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
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
     * Crée le bouton d'application de cristal
     */
    private ItemStack createApplyCristalButton() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a💎 §lAPPLIQUER CRISTAL");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Appliquez un cristal §aGreed §7sur cet automineur.");
        lore.add("");
        lore.add("§eTypes acceptés:");
        lore.add("§7▸ MoneyBoost, TokenBoost, XPBoost");
        lore.add("§7▸ MineralGreed");
        lore.add("");
        lore.add("§c⚠ §cCoût: XP du joueur");
        lore.add("§c⚠ §cMaximum 2 cristaux par automineur");
        lore.add("");
        lore.add("§a🖱 §lCLIC: §aOuvrir le menu d'application");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "open_cristal_apply", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Gère les clics dans le menu d'enchantement
     */
    public void handleClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
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
                    String uuid = parts[1];
                    int levels = clickType.isShiftClick() ? 10 : 1;
                    upgradeAutominerEnchantment(player, uuid, enchantName, levels);
                }
            }
            case "remove_cristal" -> {
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    int cristalSlot = Integer.parseInt(parts[0]);
                    String uuid = parts[1];
                    removeCristalFromAutominer(player, uuid, cristalSlot);
                }
            }
            case "back_to_main" -> {
                plugin.getAutominerGUI().openMainMenu(player);
            }
            case "open_cristal_apply" -> {
//                openCristalApplicationMenu(player, autominerUuid);
            }
        }
    }

    /**
     * Améliore un enchantement d'automineur
     */
    private void upgradeAutominerEnchantment(Player player, String autominerUuid, String enchantName, int levels) {
        // Trouve l'automineur dans l'inventaire
        AutominerData autominer = findAutominerInInventory(player, autominerUuid);
        ItemStack autominerItem = findAutominerItemInInventory(player, autominerUuid);

        if (autominer == null || autominerItem == null) {
            player.sendMessage("§c❌ Automineur introuvable!");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        AutominerEnchantmentManager enchantManager = plugin.getAutominerEnchantmentManager();

        int currentLevel = autominer.getEnchantmentLevel(enchantName);
        int maxLevel = getMaxEnchantmentLevel(enchantName, autominer.getType());
        int actualLevels = Math.min(levels, maxLevel - currentLevel);

        if (actualLevels <= 0) {
            player.sendMessage("§c❌ Enchantement déjà au niveau maximum!");
            return;
        }

        // Calcul du coût total
        long totalCost = 0;
        for (int i = 1; i <= actualLevels; i++) {
            totalCost += calculateUpgradeCost(enchantName, currentLevel + i, autominer.getType());
        }

        if (playerData.getTokens() < totalCost) {
            player.sendMessage("§c❌ Pas assez de tokens! Coût: §6" + NumberFormatter.format(totalCost));
            return;
        }

        // Application de l'amélioration
        playerData.removeTokens(totalCost);
        autominer.addEnchantment(enchantName, actualLevels);

        // Mise à jour de l'item dans l'inventaire
        updateAutominerItemInInventory(player, autominerUuid, autominer);

        player.sendMessage("§a✅ " + getEnchantmentDisplayName(enchantName) + " amélioré de §e" + actualLevels + " niveaux §a!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

        // Refresh du menu
        openEnchantMenu(player, autominerUuid);
    }

    /**
     * Retire un cristal d'un automineur
     */
    private void removeCristalFromAutominer(Player player, String autominerUuid, int cristalSlot) {
        // Trouve l'automineur dans l'inventaire
        AutominerData autominer = findAutominerInInventory(player, autominerUuid);

        if (autominer == null) {
            player.sendMessage("§c❌ Automineur introuvable!");
            return;
        }

        List<Cristal> appliedCristals = autominer.getAppliedCristals();
        if (cristalSlot < 1 || cristalSlot > appliedCristals.size()) {
            player.sendMessage("§c❌ Emplacement de cristal invalide!");
            return;
        }

//        Cristal removedCristal = appliedCristals.remove(cristalSlot - 1);

//        // Redonne le cristal au joueur s'il a de la place
//        if (player.getInventory().firstEmpty() != -1) {
//            ItemStack cristalItem = removedCristal;
//            player.getInventory().addItem(cristalItem);
//            player.sendMessage("§a✅ Cristal retiré et ajouté à votre inventaire!");
//        } else {
//            player.sendMessage("§c❌ Inventaire plein! Cristal perdu...");
//        }

        // Mise à jour de l'item dans l'inventaire
        updateAutominerItemInInventory(player, autominerUuid, autominer);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);

        // Refresh du menu
        openEnchantMenu(player, autominerUuid);
    }

    /**
     * Ouvre le menu d'application de cristal
     */
    private void openCristalApplicationMenu(Player player, String autominerUuid) {
        Inventory inv = Bukkit.createInventory(null, 45, "§d💎 Application de Cristal");

        // Bordures
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = borderItem.getItemMeta();
        borderMeta.setDisplayName(" ");
        borderItem.setItemMeta(borderMeta);

        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderItem);
            inv.setItem(i + 36, borderItem);
        }
        for (int i = 1; i < 4; i++) {
            inv.setItem(i * 9, borderItem);
            inv.setItem(i * 9 + 8, borderItem);
        }

        // Instructions
        ItemStack instructions = new ItemStack(Material.BOOK);
        ItemMeta instructionsMeta = instructions.getItemMeta();
        instructionsMeta.setDisplayName("§d💎 §lAPPLICATION DE CRISTAL");
        List<String> instructionsLore = new ArrayList<>();
        instructionsLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        instructionsLore.add("§7Placez un cristal §aGreed §7dans la zone");
        instructionsLore.add("§7centrale pour l'appliquer sur l'automineur.");
        instructionsLore.add("");
        instructionsLore.add("§aCristaux acceptés:");
        instructionsLore.add("§7▸ MoneyBoost, TokenBoost, XPBoost");
        instructionsLore.add("§7▸ MineralGreed");
        instructionsLore.add("");
        instructionsLore.add("§c⚠ §cCoût: XP du joueur");
        instructionsLore.add("§c⚠ §cMaximum 2 cristaux par automineur");
        instructionsLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        instructionsMeta.setLore(instructionsLore);
        instructions.setItemMeta(instructionsMeta);
        inv.setItem(4, instructions);

        // Zone de placement des cristaux
        ItemStack placeholder = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta placeholderMeta = placeholder.getItemMeta();
        placeholderMeta.setDisplayName("§7💎 §lPlacez votre cristal ici");
        List<String> placeholderLore = new ArrayList<>();
        placeholderLore.add("§7Glissez un cristal Greed depuis votre inventaire");
        placeholderMeta.setLore(placeholderLore);
        placeholder.setItemMeta(placeholderMeta);

        for (int i = 19; i <= 25; i++) {
            if (i != 22) {
                inv.setItem(i, placeholder);
            }
        }

        // Bouton d'application
        ItemStack applyButton = new ItemStack(Material.EMERALD);
        ItemMeta applyMeta = applyButton.getItemMeta();
        applyMeta.setDisplayName("§a✅ §lAPPLIQUER LE CRISTAL");
        List<String> applyLore = new ArrayList<>();
        applyLore.add("§7Applique le cristal placé sur l'automineur");
        applyMeta.setLore(applyLore);
        setItemAction(applyMeta, "apply_cristal_from_slot", autominerUuid);
        applyButton.setItemMeta(applyMeta);
        inv.setItem(31, applyButton);

        // Bouton de retour
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c← §lRETOUR");
        List<String> backLore = new ArrayList<>();
        backLore.add("§7Retourner au menu d'amélioration");
        backMeta.setLore(backLore);
        setItemAction(backMeta, "back_to_enchant_menu", autominerUuid);
        backButton.setItemMeta(backMeta);
        inv.setItem(36, backButton);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * Gère les clics dans le menu d'application de cristal
     */
    public void handleCristalApplicationClick(Player player, int slot, ItemStack clickedItem, ClickType clickType, String autominerUuid) {
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "apply_cristal_from_slot" -> {
                applyCristalFromApplicationMenu(player, autominerUuid);
            }
            case "back_to_enchant_menu" -> {
                // Rendre les items avant de fermer
                returnCristalToPlayer(player);
                openEnchantMenu(player, autominerUuid);
            }
        }
    }

    /**
     * Applique un cristal depuis le menu d'application
     */
    private void applyCristalFromApplicationMenu(Player player, String autominerUuid) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack cristalItem = inv.getItem(22); // Slot central

        if (cristalItem == null || cristalItem.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
            player.sendMessage("§c❌ Aucun cristal placé!");
            return;
        }

        // Vérifier que c'est un cristal
        if (!plugin.getCristalManager().isCristal(cristalItem)) {
            player.sendMessage("§c❌ Ceci n'est pas un cristal valide!");
            return;
        }

        Cristal cristal = plugin.getCristalManager().getCristalFromItem(cristalItem);
        if (cristal == null || cristal.isVierge()) {
            player.sendMessage("§c❌ Le cristal doit être révélé avant d'être appliqué!");
            return;
        }

        // Vérifier que c'est un cristal Greed
        if (!isGreedCristal(cristal.getType())) {
            player.sendMessage("§c❌ Seuls les cristaux Greed peuvent être appliqués sur les automineurs!");
            return;
        }

        // Trouver l'automineur
        AutominerData autominer = findAutominerInInventory(player, autominerUuid);
        if (autominer == null) {
            player.sendMessage("§c❌ Automineur introuvable!");
            return;
        }

        // Vérifier les emplacements disponibles
        if (autominer.getAppliedCristals().size() >= 2) {
            player.sendMessage("§c❌ Automineur déjà équipé du maximum de cristaux (2)!");
            return;
        }

        // Calculer le coût en XP
        long xpCost = (autominer.getAppliedCristals().size() + 1) * 1000L;
        if (player.getTotalExperience() < xpCost) {
            player.sendMessage("§c❌ XP insuffisant! Requis: §e" + xpCost + " XP");
            return;
        }

        // Application du cristal
        if (autominer.applyCristal(cristal)) {
            // Consommer l'XP et retirer le cristal
            player.giveExp(-(int)xpCost);
            inv.setItem(22, null);

            // Mettre à jour l'automineur dans l'inventaire
            updateAutominerItemInInventory(player, autominerUuid, autominer);

            player.sendMessage("§a✅ Cristal " + cristal.getType().getDisplayName() + " §aappliqué sur l'automineur!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

            // Retourner au menu d'amélioration
            openEnchantMenu(player, autominerUuid);
        } else {
            player.sendMessage("§c❌ Impossible d'appliquer le cristal!");
        }
    }

    /**
     * Rend le cristal au joueur lors de la fermeture du menu
     */
    private void returnCristalToPlayer(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack cristalItem = inv.getItem(22);

        if (cristalItem != null && cristalItem.getType() != Material.LIGHT_GRAY_STAINED_GLASS_PANE) {
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(cristalItem);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), cristalItem);
                player.sendMessage("§e⚠ Inventaire plein! Le cristal a été jeté au sol.");
            }
        }
    }

    /**
     * Vérifie si un cristal est de type Greed
     */
    private boolean isGreedCristal(CristalType type) {
        return switch (type) {
            case MONEY_BOOST, TOKEN_BOOST, XP_BOOST, MINERAL_GREED -> true;
            default -> false;
        };
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

    private boolean isEnchantmentAvailableForType(String enchantName, AutominerType type) {
        return switch (enchantName) {
            case "efficiency", "fortune" -> true; // Disponible pour tous
            case "tokengreed" -> type != AutominerType.PIERRE;
            case "expgreed", "moneygreed" -> type.ordinal() >= AutominerType.OR.ordinal();
            case "keygreed" -> type.ordinal() >= AutominerType.DIAMANT.ordinal();
            case "fuelefficiency" -> type.ordinal() >= AutominerType.DIAMANT.ordinal();
            case "beaconfinder" -> type == AutominerType.BEACON;
            default -> false;
        };
    }

    private int getMaxEnchantmentLevel(String enchantName, AutominerType type) {
        return switch (type) {
            case PIERRE -> switch (enchantName) {
                case "efficiency" -> 10;
                case "fortune" -> 5;
                default -> 0;
            };
            case FER -> switch (enchantName) {
                case "efficiency" -> 25;
                case "fortune" -> 10;
                case "tokengreed" -> 10;
                default -> 0;
            };
            case OR -> switch (enchantName) {
                case "efficiency" -> 100;
                case "fortune" -> 50;
                case "tokengreed", "expgreed", "moneygreed" -> 25;
                default -> 0;
            };
            case DIAMANT -> switch (enchantName) {
                case "efficiency" -> 500;
                case "fortune" -> 250;
                case "tokengreed", "expgreed", "moneygreed" -> 100;
                case "keygreed" -> 2;
                case "fuelefficiency" -> 10;
                default -> 0;
            };
            case EMERAUDE -> switch (enchantName) {
                case "efficiency" -> 5000;
                case "fortune" -> 2000;
                case "tokengreed", "expgreed", "moneygreed" -> 500;
                case "keygreed" -> 1;
                case "fuelefficiency" -> 50;
                default -> 0;
            };
            case BEACON -> switch (enchantName) {
                case "efficiency", "fortune" -> Integer.MAX_VALUE;
                case "tokengreed", "expgreed", "moneygreed" -> 10000;
                case "keygreed" -> 3;
                case "fuelefficiency" -> 100;
                case "beaconfinder" -> 1;
                default -> 0;
            };
        };
    }

    private Material getEnchantmentMaterial(String enchantName) {
        return switch (enchantName) {
            case "efficiency" -> Material.DIAMOND_PICKAXE;
            case "fortune" -> Material.EMERALD;
            case "tokengreed" -> Material.SUNFLOWER;
            case "expgreed" -> Material.EXPERIENCE_BOTTLE;
            case "moneygreed" -> Material.GOLD_INGOT;
            case "keygreed" -> Material.TRIPWIRE_HOOK;
            case "fuelefficiency" -> Material.COAL;
            case "beaconfinder" -> Material.BEACON;
            default -> Material.BOOK;
        };
    }

    private String getEnchantmentDisplayName(String enchantName) {
        return switch (enchantName) {
            case "efficiency" -> "EFFICACITÉ";
            case "fortune" -> "FORTUNE";
            case "tokengreed" -> "TOKEN GREED";
            case "expgreed" -> "EXP GREED";
            case "moneygreed" -> "MONEY GREED";
            case "keygreed" -> "KEY GREED";
            case "fuelefficiency" -> "FUEL EFFICIENCY";
            case "beaconfinder" -> "BEACON FINDER";
            default -> enchantName.toUpperCase();
        };
    }

    private long calculateUpgradeCost(String enchantName, int level, AutominerType type) {
        // Coût de base par enchantement
        long baseCost = switch (enchantName) {
            case "efficiency" -> 100;
            case "fortune" -> 200;
            case "tokengreed", "expgreed", "moneygreed" -> 500;
            case "keygreed" -> 2000;
            case "fuelefficiency" -> 1000;
            case "beaconfinder" -> 10000;
            default -> 100;
        };

        // Coefficient de rareté
        double rarityCoeff = switch (type) {
            case PIERRE -> 0.5;
            case FER -> 1.0;
            case OR -> 2.0;
            case DIAMANT -> 4.0;
            case EMERAUDE -> 7.5;
            case BEACON -> 12.0;
        };

        return Math.round(baseCost * level / 100.0 * rarityCoeff);
    }

    // Méthodes de recherche dans l'inventaire
    private AutominerData findAutominerInInventory(Player player, String uuid) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
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

    private ItemStack findAutominerItemInInventory(Player player, String uuid) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
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

    private void updateAutominerItemInInventory(Player player, String uuid, AutominerData autominer) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                AutominerData data = AutominerData.fromItemStack(item,
                        plugin.getAutominerManager().getUuidKey(),
                        plugin.getAutominerManager().getTypeKey(),
                        plugin.getAutominerManager().getEnchantKey(),
                        plugin.getAutominerManager().getCristalKey());

                if (data != null && data.getUuid().equals(uuid)) {
                    ItemStack updatedItem = autominer.toItemStack(
                            plugin.getAutominerManager().getUuidKey(),
                            plugin.getAutominerManager().getTypeKey(),
                            plugin.getAutominerManager().getEnchantKey(),
                            plugin.getAutominerManager().getCristalKey());
                    player.getInventory().setItem(i, updatedItem);
                    break;
                }
            }
        }
    }
}