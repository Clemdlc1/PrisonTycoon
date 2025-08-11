package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique pour l'amélioration des enchantements et cristaux
 */
public class AutominerEnchantGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey enchantKey;
    private final NamespacedKey slotKey;

    public AutominerEnchantGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.enchantKey = new NamespacedKey(plugin, "enchant_name");
        this.slotKey = new NamespacedKey(plugin, "autominer_slot");
    }

    /**
     * Ouvre le menu d'amélioration pour un automineur
     */
    public void openEnchantMenu(Player player, ItemStack autominer, int slotNumber) {
        if (!plugin.getAutominerManager().isAutominer(autominer)) {
            player.sendMessage("§cCet item n'est pas un automineur!");
            return;
        }

        AutominerType type = plugin.getAutominerManager().getAutominerType(autominer);
        Map<String, Integer> enchantments = plugin.getAutominerManager().getAutominerEnchantments(autominer);
        Map<String, String> crystals = plugin.getAutominerManager().getAutominerCrystals(autominer);

        Inventory inv = plugin.getGUIManager().createInventory(54, "§6⚡ Amélioration Automineur §6⚡");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.AUTOMINER_ENCHANT, inv, Map.of("slot", String.valueOf(slotNumber)));

        // Automineur au centre-haut
        inv.setItem(4, autominer.clone());

        // === SECTION ENCHANTEMENTS (LIGNE 2-3) ===
        ItemStack enchantTitle = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta enchantTitleMeta = enchantTitle.getItemMeta();
        plugin.getGUIManager().applyName(enchantTitleMeta, "§b✨ Enchantements");
        plugin.getGUIManager().applyLore(enchantTitleMeta, Arrays.asList("§7Cliquez sur un enchantement", "§7pour l'améliorer"));
        enchantTitle.setItemMeta(enchantTitleMeta);
        inv.setItem(9, enchantTitle);

        // Enchantements disponibles (ligne 2-3)
        int[] enchantSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int enchantIndex = 0;

        for (Map.Entry<String, Integer> entry : type.getEnchantmentLimits().entrySet()) {
            if (enchantIndex >= enchantSlots.length) break;

            String enchantName = entry.getKey();
            int maxLevel = entry.getValue();
            int currentLevel = enchantments.getOrDefault(enchantName, 0);

            ItemStack enchantItem = createEnchantmentItem(enchantName, currentLevel, maxLevel, slotNumber);
            inv.setItem(enchantSlots[enchantIndex], enchantItem);
            enchantIndex++;
        }

        // === SECTION CRISTAUX (LIGNE 4) ===
        ItemStack crystalTitle = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta crystalTitleMeta = crystalTitle.getItemMeta();
        plugin.getGUIManager().applyName(crystalTitleMeta, "§d💎 Cristaux");
        plugin.getGUIManager().applyLore(crystalTitleMeta, Arrays.asList("§7Cliquez avec un cristal 'Greed'", "§7pour l'appliquer"));
        crystalTitle.setItemMeta(crystalTitleMeta);
        inv.setItem(27, crystalTitle);

        // Slots de cristaux avec cristaux appliqués
        inv.setItem(29, createCrystalSlotItem("slot_1", crystals.get("slot_1"), slotNumber));
        inv.setItem(33, createCrystalSlotItem("slot_2", crystals.get("slot_2"), slotNumber));

        // === INFORMATIONS AUTOMINEUR (LIGNE 5) ===
        ItemStack info = createAutominerInfoItem(type, enchantments, crystals);
        inv.setItem(40, info);

        // Bouton retour
        inv.setItem(49, createBackButton());

        // Remplir les slots vides avec du verre
        plugin.getGUIManager().fillBorders(inv);

        player.openInventory(inv);
    }

    /**
     * Gère les clics dans le menu d'amélioration
     */
    public void handleEnchantMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();

        // Bouton retour
        if (slot == 49) {
            plugin.getAutominerGUI().openMainMenu(player);
            return;
        }

        // Clic sur un enchantement
        if (meta.getPersistentDataContainer().has(enchantKey, PersistentDataType.STRING)) {
            String enchantName = meta.getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);
            int slotNumber = meta.getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);

            handleEnchantmentUpgrade(player, enchantName, slotNumber, clickType);
        }

        // Clic sur un slot de cristal - retirer cristal avec shift+clic
        String legacyName = meta.displayName() != null ? net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(meta.displayName()) : "";
        if (legacyName.contains("Slot Cristal") && clickType == ClickType.SHIFT_LEFT) {
            int slotNumber = meta.getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
            handleCrystalRemoval(player, slot, slotNumber);
        }
    }

    private void handleCrystalRemoval(Player player, int guiSlot, int autominerSlot) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        ItemStack autominer = (autominerSlot == 1) ?
                playerData.getActiveAutominerSlot1() :
                playerData.getActiveAutominerSlot2();

        if (autominer == null) {
            player.sendMessage("§cErreur: Automineur non trouvé!");
            return;
        }

        Map<String, String> crystals = plugin.getAutominerManager().getAutominerCrystals(autominer);
        String crystalSlot = (guiSlot == 29) ? "slot_1" : "slot_2";
        String currentCrystal = crystals.get(crystalSlot);

        if (currentCrystal == null || currentCrystal.equals("null")) {
            player.sendMessage("§cAucun cristal à retirer!");
            return;
        }

        // Retirer le cristal
        crystals.put(crystalSlot, null);
        autominer = plugin.getAutominerManager().setAutominerCrystals(autominer, crystals);

        // Mettre à jour dans les données du joueur
        if (autominerSlot == 1) {
            playerData.setActiveAutominerSlot1(autominer);
        } else {
            playerData.setActiveAutominerSlot2(autominer);
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✓ Cristal retiré: " + currentCrystal);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

        // Rafraîchir le menu
        openEnchantMenu(player, autominer, autominerSlot);
    }

    private String formatTime(int seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h";
        } else if (seconds >= 60) {
            return (seconds / 60) + "min";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Gère l'application d'un cristal depuis l'inventaire du joueur
     */
    public void handleCrystalApplication(Player player, ItemStack crystal) {
        // Vérifier que c'est bien un cristal de type "Greed"
        if (!isCrystalValid(crystal)) {
            player.sendMessage("§cSeuls les cristaux de type 'Greed' peuvent être appliqués aux automineurs!");
            return;
        }

        String inventoryTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(player.getOpenInventory().title());
        if (!inventoryTitle.contains("Amélioration Automineur")) {
            return;
        }

        // Extraire le numéro de slot depuis les métadonnées du GUI
        // Pour simplifier, on va chercher le premier slot libre
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Déterminer quel automineur est en cours de modification
        ItemStack slot1 = playerData.getActiveAutominerSlot1();
        ItemStack slot2 = playerData.getActiveAutominerSlot2();

        ItemStack targetAutominer = null;
        int targetSlotNumber = 0;

        // Simple heuristique : on prend le premier automineur qui a un slot cristal libre
        if (slot1 != null) {
            Map<String, String> crystals1 = plugin.getAutominerManager().getAutominerCrystals(slot1);
            if (crystals1.get("slot_1") == null || crystals1.get("slot_1").equals("null") ||
                    crystals1.get("slot_2") == null || crystals1.get("slot_2").equals("null")) {
                targetAutominer = slot1;
                targetSlotNumber = 1;
            }
        }

        if (targetAutominer == null && slot2 != null) {
            Map<String, String> crystals2 = plugin.getAutominerManager().getAutominerCrystals(slot2);
            if (crystals2.get("slot_1") == null || crystals2.get("slot_1").equals("null") ||
                    crystals2.get("slot_2") == null || crystals2.get("slot_2").equals("null")) {
                targetAutominer = slot2;
                targetSlotNumber = 2;
            }
        }

        if (targetAutominer == null) {
            player.sendMessage("§cAucun slot de cristal libre trouvé!");
            return;
        }

        // Vérifier le coût en XP
        int xpCost = calculateCrystalApplicationCost(crystal);
        if (player.getLevel() < xpCost) {
            player.sendMessage("§cVous n'avez pas assez de niveaux d'expérience! Coût: " + xpCost + " niveaux");
            return;
        }

        // Appliquer le cristal
        Map<String, String> crystals = plugin.getAutominerManager().getAutominerCrystals(targetAutominer);
        String crystalName = extractCrystalName(crystal);

        if (crystals.get("slot_1") == null || crystals.get("slot_1").equals("null")) {
            crystals.put("slot_1", crystalName);
        } else if (crystals.get("slot_2") == null || crystals.get("slot_2").equals("null")) {
            crystals.put("slot_2", crystalName);
        } else {
            player.sendMessage("§cTous les slots de cristaux sont occupés!");
            return;
        }

        // Mettre à jour l'automineur
        targetAutominer = plugin.getAutominerManager().setAutominerCrystals(targetAutominer, crystals);

        // Sauvegarder dans les données du joueur
        if (targetSlotNumber == 1) {
            playerData.setActiveAutominerSlot1(targetAutominer);
        } else {
            playerData.setActiveAutominerSlot2(targetAutominer);
        }

        // Consommer le cristal et l'XP
        crystal.setAmount(crystal.getAmount() - 1);
        player.setLevel(player.getLevel() - xpCost);

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✓ Cristal " + crystalName + " appliqué avec succès!");
        player.sendMessage("§7Coût: " + xpCost + " niveaux d'expérience");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

        // Fermer et rouvrir le menu pour actualiser
        player.closeInventory();
        openEnchantMenu(player, targetAutominer, targetSlotNumber);
    }

    // ==================== MÉTHODES PRIVÉES ====================

    private void handleEnchantmentUpgrade(Player player, String enchantName, int slotNumber, ClickType clickType) {
        // Ouvrir le menu d'amélioration dédié
        plugin.getAutominerEnchantUpgradeGUI().openUpgradeMenu(player, enchantName, slotNumber);
    }

    private ItemStack createEnchantmentItem(String enchantName, int currentLevel, int maxLevel, int slotNumber) {
        Material material = getEnchantmentMaterial(enchantName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§b" + enchantName);
        List<String> lore = new ArrayList<>();

        if (maxLevel == Integer.MAX_VALUE) {
            lore.add("§7Niveau: §f" + NumberFormatter.format(currentLevel) + " §7(∞)");
        } else {
            lore.add("§7Niveau: §f" + currentLevel + "§7/" + maxLevel);
        }

        if (currentLevel < maxLevel) {
            int baseTokenCost = getBaseTokenCost(enchantName);
            AutominerType type = getCurrentAutominerType(slotNumber);
            if (type != null) {
                long cost = type.calculateUpgradeCost(enchantName, currentLevel, baseTokenCost);
                lore.add("§7Coût amélioration: §e" + NumberFormatter.format(cost) + " tokens");
                lore.add("");
                lore.add("§eClic: §7Améliorer");
            }
        } else {
            lore.add("§cNiveau maximum atteint!");
        }

        plugin.getGUIManager().applyLore(meta, lore);
        meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchantName);
        meta.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, slotNumber);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createCrystalSlotItem(String slotName, String currentCrystal, int slotNumber) {
        Material material = (currentCrystal != null && !currentCrystal.equals("null")) ?
                Material.AMETHYST_SHARD : Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String slotNum = slotName.charAt(slotName.length() - 1) + "";
        plugin.getGUIManager().applyName(meta, "§d💎 Slot Cristal " + slotNum);
        List<String> lore = new ArrayList<>();

        if (currentCrystal != null && !currentCrystal.equals("null")) {
            // Cristal appliqué
            lore.add("§a✓ Cristal appliqué:");
            lore.add("§f" + currentCrystal);
            lore.add("");
            lore.add("§7Bonus actif sur l'automineur");
            lore.add("");
            lore.add("§cShift+Clic: §7Retirer le cristal");
        } else {
            // Slot vide
            lore.add("§7Slot vide");
            lore.add("");
            lore.add("§eCliquez avec un cristal 'Greed'");
            lore.add("§epour l'appliquer");
            lore.add("");
            lore.add("§7Types acceptés:");
            lore.add("§8• MoneyBoost, TokenBoost");
            lore.add("§8• XPBoost, MineralGreed");
        }

        plugin.getGUIManager().applyLore(meta, lore);
        meta.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, slotNumber);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createAutominerInfoItem(AutominerType type, Map<String, Integer> enchantments, Map<String, String> crystals) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§6📊 Informations");
        List<String> lore = new ArrayList<>();

        lore.add("§7Type: §f" + type.getDisplayName());
        lore.add("§7Consommation: §e1 tête/" + formatTime(type.getBaseFuelConsumption()));
        lore.add("§7Coefficient: §fx" + type.getRarityCoefficient());
        lore.add("");

        // Enchantements actifs
        long totalEnchants = enchantments.values().stream().mapToLong(Integer::longValue).sum();
        lore.add("§bEnchantements actifs: §f" + totalEnchants);
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            if (entry.getValue() > 0) {
                lore.add("§8• " + entry.getKey() + " " + entry.getValue());
            }
        }

        lore.add("");

        // Cristaux actifs
        int activeCrystals = 0;
        for (String crystal : crystals.values()) {
            if (crystal != null && !crystal.equals("null")) {
                activeCrystals++;
            }
        }
        lore.add("§dCristaux actifs: §f" + activeCrystals + "/2");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§cRetour");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retourner au menu principal"));
        item.setItemMeta(meta);
        return item;
    }

    private Material getEnchantmentMaterial(String enchantName) {
        return switch (enchantName) {
            case "EFFICACITE" -> Material.DIAMOND_PICKAXE;
            case "FORTUNE" -> Material.EMERALD;
            case "TOKENGREED" -> Material.SUNFLOWER;
            case "EXPGREED" -> Material.EXPERIENCE_BOTTLE;
            case "MONEYGREED" -> Material.GOLD_INGOT;
            case "KEYGREED" -> Material.TRIPWIRE_HOOK;
            case "FUELEFFICIENCY" -> Material.REDSTONE;
            case "BEACONFINDER" -> Material.BEACON;
            default -> Material.ENCHANTED_BOOK;
        };
    }

    private int getBaseTokenCost(String enchantName) {
        return switch (enchantName) {
            case "EFFICACITE" -> 100;
            case "FORTUNE" -> 200;
            case "TOKENGREED" -> 500;
            case "EXPGREED" -> 500;
            case "MONEYGREED" -> 500;
            case "KEYGREED" -> 1000;
            case "FUELEFFICIENCY" -> 750;
            case "BEACONFINDER" -> 2000;
            default -> 100;
        };
    }

    private AutominerType getCurrentAutominerType(int slotNumber) {
        // Cette méthode devrait récupérer le type de l'automineur actuel
        // Pour simplifier, on retourne null ici, mais dans une implémentation complète
        // on récupérerait les données du joueur
        return null;
    }

    private boolean isCrystalValid(ItemStack crystal) {
        if (!plugin.getCristalManager().isCristal(crystal)) {
            return false;
        }

        // Vérifier que c'est un cristal de type "Greed"
        String crystalName = extractCrystalName(crystal);
        return crystalName.contains("MoneyBoost") ||
                crystalName.contains("TokenBoost") ||
                crystalName.contains("XPBoost") ||
                crystalName.contains("MineralGreed");
    }

    private String extractCrystalName(ItemStack crystal) {
        if (crystal.hasItemMeta() && crystal.getItemMeta().displayName() != null) {
            return crystal.getItemMeta().getDisplayName();
        }
        return "Cristal Inconnu";
    }

    private int calculateCrystalApplicationCost(ItemStack crystal) {
        // Coût en niveaux d'expérience selon le type de cristal
        String crystalName = extractCrystalName(crystal);

        if (crystalName.contains("Niveau 1")) return 5;
        if (crystalName.contains("Niveau 2")) return 10;
        if (crystalName.contains("Niveau 3")) return 15;
        if (crystalName.contains("Niveau 4")) return 20;
        if (crystalName.contains("Niveau 5")) return 25;

        return 10; // Coût par défaut
    }
}