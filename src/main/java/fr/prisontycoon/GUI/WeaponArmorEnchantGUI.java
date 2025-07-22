package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.WeaponArmorEnchantmentManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
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
 * Interface graphique pour enchanter les épées et armures
 */
public class WeaponArmorEnchantGUI {

    private final PrisonTycoon plugin;

    // Slots du GUI
    private static final int ITEM_DISPLAY_SLOT = 4;
    private static final int VANILLA_ENCHANT_BUTTON = 13;
    private static final int UNIQUE_BOOK_SLOT_1 = 15;
    private static final int UNIQUE_BOOK_SLOT_2 = 16; // Seulement pour épées
    private static final int SHOP_BUTTON_SLOT = 22;
    private static final int BACK_BUTTON_SLOT = 26;

    public WeaponArmorEnchantGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu d'enchantement pour épée/armure
     */
    public void openEnchantMenu(Player player, ItemStack item) {
        if (item == null || (!isValidWeapon(item) && !isValidArmor(item))) {
            player.sendMessage("§cVous devez tenir une épée ou une pièce d'armure!");
            return;
        }

        boolean isWeapon = isValidWeapon(item);
        String title = isWeapon ? "§c⚔ §lEnchantement d'Épée" : "§9🛡 §lEnchantement d'Armure";

        Inventory gui = Bukkit.createInventory(null, 27, title);

        fillWithGlass(gui, isWeapon);
        setupItemDisplay(gui, item, isWeapon);
        setupVanillaEnchantButton(gui, item, player);
        setupUniqueBookSlots(gui, isWeapon);
        setupControlButtons(gui);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    /**
     * Remplit le GUI avec du verre coloré
     */
    private void fillWithGlass(Inventory gui, boolean isWeapon) {
        Material glassType = isWeapon ? Material.RED_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE;
        ItemStack glass = new ItemStack(glassType);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }
    }

    /**
     * Affiche l'item à enchanter
     */
    private void setupItemDisplay(Inventory gui, ItemStack item, boolean isWeapon) {
        ItemStack displayItem = item.clone();
        ItemMeta meta = displayItem.getItemMeta();

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add("§7Item à enchanter:");
        lore.add("§e" + getItemDisplayName(item));

        // Affiche les enchantements actuels
        if (!item.getEnchantments().isEmpty()) {
            lore.add("");
            lore.add("§6Enchantements actuels:");
            for (Map.Entry<Enchantment, Integer> enchant : item.getEnchantments().entrySet()) {
                lore.add("§7- " + getEnchantmentName(enchant.getKey()) + " " + enchant.getValue());
            }
        }

        meta.setLore(lore);
        displayItem.setItemMeta(meta);
        gui.setItem(ITEM_DISPLAY_SLOT, displayItem);
    }

    /**
     * Bouton pour enchanter au max vanilla
     */
    private void setupVanillaEnchantButton(Inventory gui, ItemStack item, Player player) {
        ItemStack button = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = button.getItemMeta();

        boolean isWeapon = isValidWeapon(item);
        boolean isMaxEnchanted = isMaxVanillaEnchanted(item, isWeapon);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean canAfford = playerData.getBeacons() >= 50;

        if (isMaxEnchanted) {
            meta.setDisplayName("§c❌ §lDéjà Enchanté au Maximum");
            button.setType(Material.BARRIER);
        } else if (!canAfford) {
            meta.setDisplayName("§c💸 §lPas Assez de Beacons");
            button.setType(Material.REDSTONE_BLOCK);
        } else {
            meta.setDisplayName("§a⚡ §lEnchanter au Maximum");
        }

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (isWeapon) {
            lore.add("§7Applique: §6Tranchant V, Incassable III, Flamme II");
        } else {
            lore.add("§7Applique: §6Protection IV, Incassable III");
        }

        lore.add("");
        lore.add("§6💰 Coût: §e50 beacons");
        lore.add("§7Beacons disponibles: §e" + NumberFormatter.format(playerData.getBeacons()));

        if (isMaxEnchanted) {
            lore.add("");
            lore.add("§c❌ Item déjà enchanté au maximum!");
        } else if (!canAfford) {
            lore.add("");
            lore.add("§c❌ Pas assez de beacons!");
        } else {
            lore.add("");
            lore.add("§e➤ Cliquez pour enchanter!");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        button.setItemMeta(meta);
        gui.setItem(VANILLA_ENCHANT_BUTTON, button);
    }

    /**
     * Configure les slots pour les livres uniques
     */
    private void setupUniqueBookSlots(Inventory gui, boolean isWeapon) {
        // Premier slot de livre unique (épée et armure)
        ItemStack slot1 = new ItemStack(Material.ITEM_FRAME);
        ItemMeta meta1 = slot1.getItemMeta();
        meta1.setDisplayName("§5📚 §lSlot Livre Unique");
        meta1.setLore(List.of(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Glissez un livre d'enchantement unique",
                "§7ici pour l'appliquer à votre item.",
                "",
                "§e➤ Drag & Drop depuis votre inventaire!",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        slot1.setItemMeta(meta1);
        gui.setItem(UNIQUE_BOOK_SLOT_1, slot1);

        // Deuxième slot uniquement pour les épées
        if (isWeapon) {
            ItemStack slot2 = new ItemStack(Material.ITEM_FRAME);
            ItemMeta meta2 = slot2.getItemMeta();
            meta2.setDisplayName("§5📚 §lSlot Livre Unique #2");
            meta2.setLore(List.of(
                    "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                    "§7Second slot pour livres d'enchantement",
                    "§7unique (épées seulement).",
                    "",
                    "§e➤ Drag & Drop depuis votre inventaire!",
                    "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
            ));
            slot2.setItemMeta(meta2);
            gui.setItem(UNIQUE_BOOK_SLOT_2, slot2);
        }
    }

    /**
     * Configure les boutons de contrôle
     */
    private void setupControlButtons(Inventory gui) {
        // Bouton boutique
        ItemStack shopButton = new ItemStack(Material.EMERALD);
        ItemMeta shopMeta = shopButton.getItemMeta();
        shopMeta.setDisplayName("§a💰 §lBoutique de Livres");
        shopMeta.setLore(List.of(
                "§7Achetez des livres d'enchantement",
                "§7uniques avec vos beacons!",
                "",
                "§e➤ Cliquez pour ouvrir!"
        ));
        shopButton.setItemMeta(shopMeta);
        gui.setItem(SHOP_BUTTON_SLOT, shopButton);

        // Bouton retour
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c⬅ §lRetour");
        backMeta.setLore(List.of("§7Fermer le menu"));
        backButton.setItemMeta(backMeta);
        gui.setItem(BACK_BUTTON_SLOT, backButton);
    }

    /**
     * Gère les clics dans le GUI
     */
    public void handleMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        // Bouton retour
        if (slot == BACK_BUTTON_SLOT) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // Bouton boutique
        if (slot == SHOP_BUTTON_SLOT) {
            plugin.getEnchantmentBookGUI().openBookShop(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // Bouton enchantement vanilla
        if (slot == VANILLA_ENCHANT_BUTTON) {
            handleVanillaEnchant(player);
            return;
        }

        // Slots de livres uniques - géré via drag & drop dans le listener
    }

    /**
     * Gère l'enchantement vanilla maximum
     */
    private void handleVanillaEnchant(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§cVous devez tenir l'item à enchanter!");
            return;
        }

        boolean isWeapon = isValidWeapon(item);
        boolean isArmor = isValidArmor(item);

        if (!isWeapon && !isArmor) {
            player.sendMessage("§cCet item ne peut pas être enchanté!");
            return;
        }

        if (isMaxVanillaEnchanted(item, isWeapon)) {
            player.sendMessage("§cCet item est déjà enchanté au maximum!");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getBeacons() < 50) {
            player.sendMessage("§cVous n'avez pas assez de beacons! (50 requis)");
            return;
        }

        // CORRIGÉ : Sauvegarder les enchantements uniques AVANT d'ajouter les vanilla
        Map<String, Integer> uniqueEnchants = plugin.getWeaponArmorEnchantmentManager().getUniqueEnchantments(item);

        // Retirer les beacons
        playerData.removeBeacon(50);

        // Appliquer les enchantements vanilla
        if (isWeapon) {
            item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5); // Tranchant V
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3); // Incassable III
            item.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2); // Flamme II
        } else {
            item.addUnsafeEnchantment(Enchantment.PROTECTION, 4); // Protection IV
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3); // Incassable III
        }

        // CORRIGÉ : Restaurer les enchantements uniques après les vanilla
        if (!uniqueEnchants.isEmpty()) {
            ItemMeta meta = item.getItemMeta();
            StringBuilder enchantData = new StringBuilder();
            for (Map.Entry<String, Integer> entry : uniqueEnchants.entrySet()) {
                if (enchantData.length() > 0) enchantData.append(";");
                enchantData.append(entry.getKey()).append(":").append(entry.getValue());
            }

            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "unique_enchantments"),
                    PersistentDataType.STRING,
                    enchantData.toString()
            );

            // Mettre à jour le lore avec les enchantements uniques
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.removeIf(line -> line.startsWith("§5⚡"));

            lore.add("");
            lore.add("§5⚡ §lEnchantements Uniques:");
            for (Map.Entry<String, Integer> entry : uniqueEnchants.entrySet()) {
                WeaponArmorEnchantmentManager.UniqueEnchantment enchant =
                        plugin.getWeaponArmorEnchantmentManager().getEnchantment(entry.getKey());
                if (enchant != null) {
                    String levelStr = enchant.getMaxLevel() > 1 ? " " + toRoman(entry.getValue()) : "";
                    lore.add("§5⚡ " + enchant.getName() + levelStr);
                }
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        player.sendMessage("§a✅ Item enchanté au maximum vanilla!");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        // Refresh le GUI
        openEnchantMenu(player, item);
    }

    // Méthode utilitaire pour les chiffres romains
    private String toRoman(int number) {
        String[] romans = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return number > 0 && number < romans.length ? romans[number] : String.valueOf(number);
    }

    /**
     * Applique un livre unique à l'item - CORRIGÉ pour gérer les améliorations
     */
    public void applyUniqueBook(Player player, ItemStack book, int slot) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§cVous devez tenir l'item à enchanter!");
            return;
        }

        if (!book.hasItemMeta() || !book.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "unique_enchant_book"), PersistentDataType.STRING)) {
            player.sendMessage("§cCe n'est pas un livre d'enchantement unique valide!");
            return;
        }

        String enchantId = book.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "unique_enchant_book"), PersistentDataType.STRING);

        WeaponArmorEnchantmentManager.UniqueEnchantment enchant =
                plugin.getWeaponArmorEnchantmentManager().getEnchantment(enchantId);

        if (enchant == null) {
            player.sendMessage("§cEnchantement unique introuvable!");
            return;
        }

        // Vérifier la compatibilité
        if (!plugin.getWeaponArmorEnchantmentManager().isCompatible(enchantId, item)) {
            player.sendMessage("§cCet enchantement n'est pas compatible avec cet item!");
            return;
        }

        // CORRIGÉ : Utilise la nouvelle méthode addEnchantment qui gère les améliorations et limites
        boolean success = plugin.getWeaponArmorEnchantmentManager().addEnchantment(item, enchantId, 1);

        if (!success) {
            // Vérifie pourquoi ça a échoué
            int currentLevel = plugin.getWeaponArmorEnchantmentManager().getEnchantmentLevel(item, enchantId);
            int maxLevel = enchant.getMaxLevel();

            if (currentLevel >= maxLevel) {
                player.sendMessage("§cCet enchantement est déjà au niveau maximum! (" + maxLevel + ")");
            } else {
                int currentCount = plugin.getWeaponArmorEnchantmentManager().getUniqueEnchantmentCount(item);
                int maxCount = isValidWeapon(item) ? 2 : 1;
                player.sendMessage("§cNombre maximum d'enchantements uniques atteint! (" + maxCount + ")");
            }
            return;
        }

        // Succès - retirer le livre et informer le joueur
        book.setAmount(book.getAmount() - 1);

        int newLevel = plugin.getWeaponArmorEnchantmentManager().getEnchantmentLevel(item, enchantId);
        if (newLevel == 1) {
            player.sendMessage("§a✅ Enchantement §e" + enchant.getName() + " §aappliqué!");
        } else {
            player.sendMessage("§a✅ Enchantement §e" + enchant.getName() + " §aamélioré au niveau " + newLevel + "!");
        }

        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        // Refresh le GUI pour montrer les changements
        openEnchantMenu(player, item);
    }

    // Méthodes utilitaires
    private boolean isValidWeapon(ItemStack item) {
        return item.getType() == Material.NETHERITE_SWORD ||
                item.getType() == Material.DIAMOND_SWORD ||
                item.getType() == Material.IRON_SWORD ||
                item.getType() == Material.GOLDEN_SWORD ||
                item.getType() == Material.STONE_SWORD ||
                item.getType() == Material.WOODEN_SWORD;
    }

    private boolean isValidArmor(ItemStack item) {
        Material type = item.getType();
        return type.name().endsWith("_HELMET") ||
                type.name().endsWith("_CHESTPLATE") ||
                type.name().endsWith("_LEGGINGS") ||
                type.name().endsWith("_BOOTS");
    }

    private boolean isMaxVanillaEnchanted(ItemStack item, boolean isWeapon) {
        if (isWeapon) {
            return item.getEnchantmentLevel(Enchantment.SHARPNESS) >= 5 &&
                    item.getEnchantmentLevel(Enchantment.UNBREAKING) >= 3 &&
                    item.getEnchantmentLevel(Enchantment.FIRE_ASPECT) >= 2;
        } else {
            return item.getEnchantmentLevel(Enchantment.PROTECTION) >= 4 &&
                    item.getEnchantmentLevel(Enchantment.UNBREAKING) >= 3;
        }
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().replace("_", " ").toLowerCase();
    }

    private String getEnchantmentName(Enchantment enchant) {
        // Traduction simplifiée des enchantements
        switch (enchant.getKey().getKey()) {
            case "sharpness": return "Tranchant";
            case "unbreaking": return "Incassable";
            case "fire_aspect": return "Flamme";
            case "protection": return "Protection";
            default: return enchant.getKey().getKey();
        }
    }
}