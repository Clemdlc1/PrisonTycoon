package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.WeaponArmorEnchantmentManager;
import fr.prisontycoon.utils.NumberFormatter;
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
import java.util.Objects;

/**
 * Interface graphique pour enchanter les épées et armures
 */
public class WeaponArmorEnchantGUI {

    // Slots du GUI
    private static final int ITEM_DISPLAY_SLOT = 4;
    private static final int VANILLA_ENCHANT_BUTTON = 13;
    private static final int UNIQUE_BOOK_SLOT_1 = 15;
    private static final int UNIQUE_BOOK_SLOT_2 = 16; // Seulement pour épées
    private static final int SHOP_BUTTON_SLOT = 22;
    private static final int BACK_BUTTON_SLOT = 26;
    private final PrisonTycoon plugin;

    public WeaponArmorEnchantGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * CORRIGÉ : Méthode openEnchantMenu mise à jour pour passer l'item à setupUniqueBookSlots
     */
    public void openEnchantMenu(Player player, ItemStack item) {
        if (item == null || (!isValidWeapon(item) && !isValidArmor(item))) {
            player.sendMessage("§cVous devez tenir une épée ou une pièce d'armure!");
            return;
        }

        boolean isWeapon = isValidWeapon(item);
        String title = isWeapon ? "§c⚔ §lEnchantement d'Épée" : "§9🛡 §lEnchantement d'Armure";
        Inventory gui = plugin.getGUIManager().createInventory(27, title);
        plugin.getGUIManager().registerOpenGUI(player, GUIType.WEAPON_ARMOR_ENCHANT, gui);

        fillWithGlass(gui, isWeapon);
        setupItemDisplay(gui, item, isWeapon);
        setupVanillaEnchantButton(gui, item, player);
        setupUniqueBookSlots(gui, isWeapon, item); // CORRIGÉ : Passe l'item en paramètre
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
        plugin.getGUIManager().applyName(glassMeta, " ");
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

        List<String> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.getLore())) : new ArrayList<>();

        plugin.getGUIManager().applyLore(meta, lore);
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
            plugin.getGUIManager().applyName(meta, "§c❌ §lDéjà Enchanté au Maximum");
            button.withType(Material.BARRIER);
        } else if (!canAfford) {
            plugin.getGUIManager().applyName(meta, "§c💸 §lPas Assez de Beacons");
            button.withType(Material.REDSTONE_BLOCK);
        } else {
            plugin.getGUIManager().applyName(meta, "§a⚡ §lEnchanter au Maximum");
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

        plugin.getGUIManager().applyLore(meta, lore);
        button.setItemMeta(meta);
        gui.setItem(VANILLA_ENCHANT_BUTTON, button);
    }

    /**
     * Configure les slots pour les livres uniques
     */
    private void setupUniqueBookSlots(Inventory gui, boolean isWeapon, ItemStack item) {
        Map<String, Integer> existingEnchants = plugin.getWeaponArmorEnchantmentManager().getUniqueEnchantments(item);
        List<Map.Entry<String, Integer>> enchantList = new ArrayList<>(existingEnchants.entrySet());

        // Premier slot de livre unique (épée et armure)
        if (!enchantList.isEmpty()) {
            // NOUVEAU : Affiche le premier enchantement existant
            Map.Entry<String, Integer> firstEnchant = enchantList.getFirst();
            ItemStack existingBook = createDisplayBook(firstEnchant.getKey(), firstEnchant.getValue());
            gui.setItem(UNIQUE_BOOK_SLOT_1, existingBook);
        } else {
            // Slot vide comme avant
            ItemStack slot1 = new ItemStack(Material.ITEM_FRAME);
            ItemMeta meta1 = slot1.getItemMeta();
            plugin.getGUIManager().applyName(meta1, "§5📚 §lSlot Livre Unique");
            plugin.getGUIManager().applyLore(meta1, List.of(
                    "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                    "§7Glissez un livre d'enchantement unique",
                    "§7ici pour l'appliquer à votre item.",
                    "",
                    "§e➤ Drag & Drop depuis votre inventaire!",
                    "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
            ));
            slot1.setItemMeta(meta1);
            gui.setItem(UNIQUE_BOOK_SLOT_1, slot1);
        }

        // Deuxième slot uniquement pour les épées
        if (isWeapon) {
            if (enchantList.size() > 1) {
                // NOUVEAU : Affiche le deuxième enchantement existant
                Map.Entry<String, Integer> secondEnchant = enchantList.get(1);
                ItemStack existingBook = createDisplayBook(secondEnchant.getKey(), secondEnchant.getValue());
                gui.setItem(UNIQUE_BOOK_SLOT_2, existingBook);
            } else {
                // Slot vide comme avant
                ItemStack slot2 = new ItemStack(Material.ITEM_FRAME);
                ItemMeta meta2 = slot2.getItemMeta();
                plugin.getGUIManager().applyName(meta2, "§5📚 §lSlot Livre Unique #2");
                plugin.getGUIManager().applyLore(meta2, List.of(
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
    }

    /**
     * NOUVEAU : Crée un livre d'affichage pour un enchantement existant
     */
    private ItemStack createDisplayBook(String enchantId, int level) {
        WeaponArmorEnchantmentManager.UniqueEnchantment enchant =
                plugin.getWeaponArmorEnchantmentManager().getEnchantment(enchantId);

        if (enchant == null) return new ItemStack(Material.BARRIER);

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        // Couleur selon le type
        String typeColor = getEnchantmentColor(enchantId);
        String levelStr = enchant.getMaxLevel() > 1 ? " " + toRoman(level) : "";
        plugin.getGUIManager().applyName(meta, typeColor + "⚡ §l" + enchant.getName() + levelStr);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§a✅ §lEnchantement Actif");
        lore.add("");
        lore.add("§6📖 §lEffet:");
        lore.add("§7" + enchant.getDescription());
        lore.add("");

        if (enchant.getMaxLevel() > 1) {
            lore.add("§e📊 §lNiveau: §7" + level + "/" + enchant.getMaxLevel());
            if (level < enchant.getMaxLevel()) {
                lore.add("§a▸ Peut être amélioré!");
            } else {
                lore.add("§6▸ Niveau maximum atteint!");
            }
        } else {
            lore.add("§e📊 §lNiveau: §7Unique");
        }

        lore.add("");
        lore.add("§e➤ Appliquez un livre pour améliorer!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        book.setItemMeta(meta);
        return book;
    }

    /**
     * NOUVEAU : Méthode utilitaire pour récupérer la couleur d'un enchantement
     */
    private String getEnchantmentColor(String enchantId) {
        return switch (enchantId) {
            case "tonnerre", "incassable" -> "§5"; // Violet pour universels
            case "tornade", "repercussion", "behead", "chasseur" -> "§c"; // Rouge pour épées
            default -> "§5";
        };
    }

    /**
     * Configure les boutons de contrôle
     */
    private void setupControlButtons(Inventory gui) {
        // Bouton boutique
        ItemStack shopButton = new ItemStack(Material.EMERALD);
        ItemMeta shopMeta = shopButton.getItemMeta();
        plugin.getGUIManager().applyName(shopMeta, "§a💰 §lBoutique de Livres");
        plugin.getGUIManager().applyLore(shopMeta, List.of(
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
        plugin.getGUIManager().applyName(backMeta, "§c⬅ §lRetour");
        plugin.getGUIManager().applyLore(backMeta, List.of("§7Fermer le menu"));
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
        }

        // Slots de livres uniques - géré via drag & drop dans le listener
    }

    /**
     * Gère l'enchantement vanilla maximum
     */
    private void handleVanillaEnchant(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
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
        if (item.getType() == Material.AIR) {
            player.sendMessage("§c❌ Vous devez tenir l'item à enchanter!");
            return;
        }

        if (!book.hasItemMeta() || !book.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "unique_enchant_book"), PersistentDataType.STRING)) {
            player.sendMessage("§c❌ Ce n'est pas un livre d'enchantement unique valide!");
            return;
        }

        String enchantId = book.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "unique_enchant_book"), PersistentDataType.STRING);

        WeaponArmorEnchantmentManager.UniqueEnchantment enchant =
                plugin.getWeaponArmorEnchantmentManager().getEnchantment(enchantId);

        if (enchant == null) {
            player.sendMessage("§c❌ Enchantement unique introuvable: " + enchantId);
            return;
        }

        // Vérifier la compatibilité
        if (plugin.getWeaponArmorEnchantmentManager().isCompatible(enchantId, item)) {
            player.sendMessage("§c❌ Cet enchantement n'est pas compatible avec cet item!");
            assert enchantId != null;
            player.sendMessage("§7ℹ §e" + enchant.getName() + " §7fonctionne sur: " + getCompatibilityInfo(enchantId));
            return;
        }

        // ÉTAT AVANT TENTATIVE
        Map<String, Integer> beforeEnchants = plugin.getWeaponArmorEnchantmentManager().getUniqueEnchantments(item);
        int beforeCount = beforeEnchants.size();
        int beforeLevel = beforeEnchants.getOrDefault(enchantId, 0);

        // Tentative d'application
        boolean success = plugin.getWeaponArmorEnchantmentManager().addEnchantment(item, enchantId, 1);

        if (!success) {
            // DIAGNOSTIC DÉTAILLÉ
            int currentLevel = plugin.getWeaponArmorEnchantmentManager().getEnchantmentLevel(item, enchantId);
            int maxLevel = enchant.getMaxLevel();
            int currentCount = plugin.getWeaponArmorEnchantmentManager().getUniqueEnchantmentCount(item);
            boolean isWeapon = isValidWeapon(item);
            int maxCount = isWeapon ? 2 : 1;

            player.sendMessage("§c❌ §lÉchec de l'application:");

            if (currentLevel >= maxLevel) {
                player.sendMessage("§7┃ §eRaison: §fNiveau maximum atteint");
                player.sendMessage("§7┃ §eNiveau: §f" + currentLevel + "/" + maxLevel);
            } else {
                player.sendMessage("§7┃ §eRaison: §fLimite d'enchantements atteinte");
                player.sendMessage("§7┃ §eActuel: §f" + currentCount + "/" + maxCount + " enchantements");
                player.sendMessage("§7┃ §eType: §f" + (isWeapon ? "Épée (2 max)" : "Armure (1 max)"));

                // Liste des enchantements présents
                if (currentCount > 0) {
                    player.sendMessage("§7┃ §eEnchantements présents:");
                    Map<String, Integer> current = plugin.getWeaponArmorEnchantmentManager().getUniqueEnchantments(item);
                    for (Map.Entry<String, Integer> entry : current.entrySet()) {
                        WeaponArmorEnchantmentManager.UniqueEnchantment existing =
                                plugin.getWeaponArmorEnchantmentManager().getEnchantment(entry.getKey());
                        if (existing != null) {
                            String levelStr = existing.getMaxLevel() > 1 ? " " + toRoman(entry.getValue()) : "";
                            player.sendMessage("§7┃   §5⚡ " + existing.getName() + levelStr);
                        }
                    }
                }
            }
            return;
        }

        // SUCCÈS - Messages et effets
        book.setAmount(book.getAmount() - 1);

        int newLevel = plugin.getWeaponArmorEnchantmentManager().getEnchantmentLevel(item, enchantId);

        if (newLevel == 1) {
            player.sendMessage("§a✅ §l" + enchant.getName() + " §aappliqué avec succès!");
            player.sendMessage("§7✦ Votre " + item.getType().name().toLowerCase() + " brille d'une nouvelle puissance!");
        } else {
            player.sendMessage("§a✅ §l" + enchant.getName() + " §aamélioré au niveau " + newLevel + "!");
            player.sendMessage("§7✦ La puissance mystique s'intensifie!");
        }

        // Effets visuels et sonores
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        // Refresh le GUI
        openEnchantMenu(player, item);
    }

    /**
     * NOUVEAU : Information de compatibilité lisible
     */
    private String getCompatibilityInfo(String enchantId) {
        return switch (enchantId) {
            case "tonnerre" -> "§eÉpées §7et §ePioches";
            case "incassable" -> "§eÉpées§7, §ePioches §7et §eArmures";
            case "tornade", "repercussion", "behead", "chasseur" -> "§eÉpées §7uniquement";
            default -> "§cType inconnu";
        };
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
        return switch (enchant.getKey().getKey()) {
            case "sharpness" -> "Tranchant";
            case "unbreaking" -> "Incassable";
            case "fire_aspect" -> "Flamme";
            case "protection" -> "Protection";
            default -> enchant.getKey().getKey();
        };
    }
}