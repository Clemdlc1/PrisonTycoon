package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interface graphique complète du système de shop
 * Avec catégories, sous-menus quantité et talent "solde du guerrier"
 */
public class ShopGUI {

    private final PrisonTycoon plugin;
    private final GUIManager guiManager;

    // Slots des catégories dans le menu principal
    private static final int CATEGORY_PVP_SLOT = 10;
    private static final int CATEGORY_BLOCKS_SLOT = 12;
    private static final int CATEGORY_FOOD_SLOT = 14;
    private static final int CATEGORY_TOOLS_SLOT = 16;
    private static final int CATEGORY_MISC_SLOT = 19;
    private static final int CATEGORY_REDSTONE_SLOT = 21;
    private static final int CATEGORY_DECORATION_SLOT = 23;
    private static final int CATEGORY_FARMING_SLOT = 25;
    private static final int CLOSE_SLOT = 44;

    // Quantités de base proposées (seront filtrées selon la stack max de l'item)
    private static final int[] BASE_QUANTITIES = {1, 5, 10, 16, 32, 64};

    public ShopGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    /**
     * Ouvre le menu principal du shop
     */
    public void openMainShop(Player player) {
        Inventory gui = guiManager.createInventory(45, "§6🛒 §lMAGASIN §8- §fCatégories");
        guiManager.fillBorders(gui);

        // Header décoratif
        gui.setItem(4, createShopHeader());

        // Catégories
        gui.setItem(CATEGORY_PVP_SLOT, createCategoryItem(ShopCategory.PVP, player));
        gui.setItem(CATEGORY_BLOCKS_SLOT, createCategoryItem(ShopCategory.BLOCKS, player));
        gui.setItem(CATEGORY_FOOD_SLOT, createCategoryItem(ShopCategory.FOOD, player));
        gui.setItem(CATEGORY_TOOLS_SLOT, createCategoryItem(ShopCategory.TOOLS, player));
        gui.setItem(CATEGORY_MISC_SLOT, createCategoryItem(ShopCategory.MISC, player));
        gui.setItem(CATEGORY_REDSTONE_SLOT, createCategoryItem(ShopCategory.REDSTONE, player));
        gui.setItem(CATEGORY_DECORATION_SLOT, createCategoryItem(ShopCategory.DECORATION, player));
        gui.setItem(CATEGORY_FARMING_SLOT, createCategoryItem(ShopCategory.FARMING, player));

        // Bouton fermer
        gui.setItem(CLOSE_SLOT, createCloseButton());

        guiManager.registerOpenGUI(player, GUIType.SHOP_MAIN, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    /**
     * Ouvre une catégorie (page 0 par défaut)
     */
    public void openCategory(Player player, ShopCategory category) {
        openCategory(player, category, 0);
    }

    /**
     * Ouvre une catégorie à une page donnée (pagination)
     */
    public void openCategory(Player player, ShopCategory category, int page) {
        List<ShopItem> allItems = getItemsForCategory(category);
        int itemsPerPage = 28; // grille intérieure 4x7
        int totalPages = Math.max(1, (int) Math.ceil(allItems.size() / (double) itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String title = "§6🛒 §l" + category.getDisplayName() + " §8- §fArticles §7(§e" + (page + 1) + "§7/§e" + totalPages + "§7)";
        Inventory gui = guiManager.createInventory(54, title);
        guiManager.fillBorders(gui);

        // Header (solde + info page)
        gui.setItem(4, createCategoryHeader(category, player, page, totalPages));

        // Placer les items de la page courante dans la grille intérieure (4 lignes x 7 colonnes)
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allItems.size());
        int index = start;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                if (index >= end) break;
                int slot = row * 9 + col; // évite les bordures
                gui.setItem(slot, createShopItemStack(allItems.get(index), player));
                index++;
            }
            if (index >= end) break;
        }

        // Navigation et retour
        gui.setItem(49, createBackButton());
        if (page > 0) gui.setItem(48, createPrevPageButton());
        if (page < totalPages - 1) gui.setItem(50, createNextPageButton());

        guiManager.registerOpenGUI(player, GUIType.SHOP_CATEGORY, gui);
        guiManager.setGUIData(player, "category", category.name());
        guiManager.setGUIData(player, "page", String.valueOf(page));
        player.openInventory(gui);
    }

    /**
     * Ouvre le menu de sélection de quantité
     */
    public void openQuantityMenu(Player player, ShopItem item) {
        String title = "§6📦 §lQuantité §8- §f" + item.getDisplayName();
        Inventory gui = guiManager.createInventory(27, title);
        guiManager.fillBorders(gui);

        // Header avec l'item
        gui.setItem(4, createQuantityHeader(item, player));

        // Boutons de quantité (7 emplacements sur la deuxième ligne)
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int[] quantities = getSuggestedQuantitiesFor(item);
        for (int i = 0; i < quantities.length && i < slots.length; i++) {
            gui.setItem(slots[i], createQuantityButton(item, quantities[i], player));
        }

        // Bouton retour
        gui.setItem(22, createBackButton());

        guiManager.registerOpenGUI(player, GUIType.SHOP_QUANTITY, gui);
        guiManager.setGUIData(player, "item", item.getId());
        player.openInventory(gui);
    }

    /**
     * Gère les clics dans les GUIs du shop
     */
    public void handleClick(Player player, GUIType guiType, int slot, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        String action = guiManager.getDataFromItem(clickedItem, "action");
        if (action == null) return;

        switch (action) {
            case "open_category" -> {
                String categoryName = guiManager.getDataFromItem(clickedItem, "category");
                if (categoryName != null) {
                    try {
                        ShopCategory category = ShopCategory.valueOf(categoryName);
                        openCategory(player, category);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage("§cErreur: catégorie invalide!");
                    }
                }
            }
            case "next_page" -> {
                if (guiType == GUIType.SHOP_CATEGORY) {
                    String category = guiManager.getGUIData(player, "category");
                    String pageStr = guiManager.getGUIData(player, "page");
                    int currentPage = 0;
                    if (pageStr != null) {
                        try { currentPage = Integer.parseInt(pageStr); } catch (NumberFormatException ignored) {}
                    }
                    if (category != null) {
                        try {
                            openCategory(player, ShopCategory.valueOf(category), currentPage + 1);
                        } catch (IllegalArgumentException ignored) {
                            openMainShop(player);
                        }
                    }
                }
            }
            case "prev_page" -> {
                if (guiType == GUIType.SHOP_CATEGORY) {
                    String category = guiManager.getGUIData(player, "category");
                    String pageStr = guiManager.getGUIData(player, "page");
                    int currentPage = 0;
                    if (pageStr != null) {
                        try { currentPage = Integer.parseInt(pageStr); } catch (NumberFormatException ignored) {}
                    }
                    if (category != null) {
                        try {
                            openCategory(player, ShopCategory.valueOf(category), currentPage - 1);
                        } catch (IllegalArgumentException ignored) {
                            openMainShop(player);
                        }
                    }
                }
            }
            case "select_item" -> {
                String itemId = guiManager.getDataFromItem(clickedItem, "item");
                if (itemId != null) {
                    ShopItem item = getShopItemById(itemId);
                    if (item != null) {
                        openQuantityMenu(player, item);
                    }
                }
            }
            case "buy_item" -> {
                String itemId = guiManager.getDataFromItem(clickedItem, "item");
                String quantityStr = guiManager.getDataFromItem(clickedItem, "quantity");
                if (itemId != null && quantityStr != null) {
                    try {
                        int quantity = Integer.parseInt(quantityStr);
                        ShopItem item = getShopItemById(itemId);
                        if (item != null) {
                            purchaseItem(player, item, quantity);
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cErreur: quantité invalide!");
                    }
                }
            }
            case "back" -> {
                if (guiType == GUIType.SHOP_QUANTITY) {
                    String category = guiManager.getGUIData(player, "category");
                    String pageStr = guiManager.getGUIData(player, "page");
                    int currentPage = 0;
                    if (pageStr != null) {
                        try { currentPage = Integer.parseInt(pageStr); } catch (NumberFormatException ignored) {}
                    }
                    if (category != null) {
                        try {
                            openCategory(player, ShopCategory.valueOf(category), currentPage);
                        } catch (IllegalArgumentException e) {
                            openMainShop(player);
                        }
                    } else {
                        openMainShop(player);
                    }
                } else {
                    openMainShop(player);
                }
            }
            case "close" -> player.closeInventory();
        }
    }

    /**
     * Effectue l'achat d'un item
     */
    private void purchaseItem(Player player, ShopItem item, int quantity) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Calcul du prix avec réductions
        long totalPrice = calculatePrice(item, quantity, player);

        // Vérification des fonds
        if (playerData.getCoins() < totalPrice) {
            player.sendMessage("§c❌ §lInsuffisant!");
            player.sendMessage("§7Il vous faut §6" + NumberFormatter.format(totalPrice) + " coins");
            player.sendMessage("§7Vous avez §6" + NumberFormatter.format(playerData.getCoins()) + " coins");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Vérification de l'espace d'inventaire
        if (!hasInventorySpace(player, item, quantity)) {
            player.sendMessage("§c❌ Espace d'inventaire insuffisant!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Déduction des coins
        playerData.removeCoins(totalPrice);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Donner les items en respectant la stack max (outils non stackables, etc.)
        int remaining = quantity;
        int maxStack = Math.max(1, item.createItemStack(1).getMaxStackSize());
        while (remaining > 0) {
            int give = Math.min(remaining, maxStack);
            ItemStack purchasedItem = item.createItemStack(give);
            player.getInventory().addItem(purchasedItem);
            remaining -= give;
        }

        // Messages de succès
        player.sendMessage("§a✅ §lAchat réussi!");
        player.sendMessage("§7Vous avez acheté §e" + quantity + "x " + item.getDisplayName());
        player.sendMessage("§7Prix payé: §6" + NumberFormatter.format(totalPrice) + " coins");

        // Afficher la réduction si applicable
        long basePrice = item.getPrice() * quantity;
        if (totalPrice < basePrice) {
            long saved = basePrice - totalPrice;
            player.sendMessage("§a💰 Économisé: §6" + NumberFormatter.format(saved) + " coins §7(Talent Solde du Guerrier)");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        // Fermer le GUI
        player.closeInventory();
    }

    /**
     * Calcule le prix avec les réductions applicables
     */
    private long calculatePrice(ShopItem item, int quantity, Player player) {
        long basePrice = item.getPrice() * quantity;

        // Application du talent "solde du guerrier" pour la catégorie PVP
        if (item.getCategory() == ShopCategory.PVP) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if ("guerrier".equalsIgnoreCase(playerData.getActiveProfession())) {
                var professionManager = plugin.getProfessionManager();
                if (professionManager != null) {
                    var guerrier = professionManager.getProfession("guerrier");
                    if (guerrier != null) {
                        var soldes = guerrier.getTalent("soldes");
                        if (soldes != null) {
                            int level = playerData.getTalentLevel("guerrier", "soldes");
                            if (level > 0) {
                                int percent = soldes.getValueAtLevel(level);
                                percent = Math.max(0, Math.min(90, percent)); // Limite à 90%
                                double multiplier = 1.0 - (percent / 100.0);
                                return Math.max(0, Math.round(basePrice * multiplier));
                            }
                        }
                    }
                }
            }
        }

        return basePrice;
    }

    /**
     * Vérifie si le joueur a assez d'espace dans son inventaire
     */
    private boolean hasInventorySpace(Player player, ShopItem shopItem, int quantity) {
        int maxStack = Math.max(1, shopItem.createItemStack(1).getMaxStackSize());
        int needed = quantity;

        // Tente d'empiler sur les stacks existants du même type/meta
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) continue;
            if (!content.isSimilar(shopItem.createItemStack(1))) continue;
            int canAdd = maxStack - content.getAmount();
            if (canAdd > 0) {
                int used = Math.min(canAdd, needed);
                needed -= used;
                if (needed <= 0) return true;
            }
        }

        // Calcule le nombre de slots vides restants
        int emptySlots = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) emptySlots++;
        }

        int stacksNeeded = (int) Math.ceil(needed / (double) maxStack);
        return emptySlots >= stacksNeeded;
    }

    // ==================== CRÉATION D'ITEMS GUI ====================

    private ItemStack createShopHeader() {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§6🛒 §lMAGASIN PRINCIPAL");

        List<String> lore = Arrays.asList(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Bienvenue dans le magasin!",
                "§7Choisissez une catégorie ci-dessous",
                "§7pour découvrir tous nos articles.",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "",
                "§e💰 Monnaie: §6Coins"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createCategoryItem(ShopCategory category, Player player) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§6🏷️ §l" + category.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + category.getDescription());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§7Articles disponibles: §e" + getItemsForCategory(category).size());

        // Bonus spécial pour PVP
        if (category == ShopCategory.PVP) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if ("guerrier".equalsIgnoreCase(playerData.getActiveProfession())) {
                var professionManager = plugin.getProfessionManager();
                if (professionManager != null) {
                    var guerrier = professionManager.getProfession("guerrier");
                    if (guerrier != null) {
                        var soldes = guerrier.getTalent("soldes");
                        if (soldes != null) {
                            int level = playerData.getTalentLevel("guerrier", "soldes");
                            if (level > 0) {
                                int percent = soldes.getValueAtLevel(level);
                                lore.add("§a⚔️ Solde du Guerrier: §e-" + percent + "%");
                            }
                        }
                    }
                }
            } else {
                lore.add("§7⚔️ Solde du Guerrier: §cMétier requis");
            }
        }

        lore.add("");
        lore.add("§e▶ Cliquez pour ouvrir!");

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        guiManager.addGUIMetadata(item, GUIType.SHOP_MAIN, "action", "open_category");
        guiManager.addGUIMetadata(item, GUIType.SHOP_MAIN, "category", category.name());

        return item;
    }

    private ItemStack createCategoryHeader(ShopCategory category, Player player, int page, int totalPages) {
        ItemStack item = new ItemStack(category.getIcon());
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§6🏷️ §l" + category.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + category.getDescription());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        lore.add("");
        lore.add("§e💰 Votre solde: §6" + NumberFormatter.format(playerData.getCoins()) + " coins");
        lore.add("§7Page: §e" + (page + 1) + "§7/§e" + totalPages);

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createShopItemStack(ShopItem shopItem, Player player) {
        ItemStack item = shopItem.createItemStack(1);
        ItemMeta meta = item.getItemMeta();

        // Récupérer le lore existant ou créer un nouveau
        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Ajouter les informations du shop
        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        long price = calculatePrice(shopItem, 1, player);
        lore.add("§e💰 Prix: §6" + NumberFormatter.format(price) + " coins");

        // Afficher la réduction si applicable
        if (price < shopItem.getPrice()) {
            long saved = shopItem.getPrice() - price;
            lore.add("§a💸 Économie: §6" + NumberFormatter.format(saved) + " coins");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§e▶ Cliquez pour acheter!");

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        guiManager.addGUIMetadata(item, GUIType.SHOP_CATEGORY, "action", "select_item");
        guiManager.addGUIMetadata(item, GUIType.SHOP_CATEGORY, "item", shopItem.getId());

        return item;
    }

    private ItemStack createQuantityHeader(ShopItem shopItem, Player player) {
        ItemStack item = shopItem.createItemStack(1);
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§6📦 §l" + shopItem.getDisplayName());

        List<String> lore = Arrays.asList(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Sélectionnez la quantité à acheter",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "",
                "§e💰 Prix unitaire: §6" + NumberFormatter.format(calculatePrice(shopItem, 1, player)) + " coins"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createPrevPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§e« §lPage précédente");
        guiManager.applyLore(meta, List.of("§7Aller à la page précédente"));
        item.setItemMeta(meta);
        guiManager.addGUIMetadata(item, GUIType.SHOP_CATEGORY, "action", "prev_page");
        return item;
    }

    private ItemStack createNextPageButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§ePage suivante §l»");
        guiManager.applyLore(meta, List.of("§7Aller à la page suivante"));
        item.setItemMeta(meta);
        guiManager.addGUIMetadata(item, GUIType.SHOP_CATEGORY, "action", "next_page");
        return item;
    }

    private ItemStack createQuantityButton(ShopItem shopItem, int quantity, Player player) {
        ItemStack item = new ItemStack(Material.EMERALD, Math.min(quantity, 64));
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§a📦 §l" + quantity + "x " + shopItem.getDisplayName());

        long totalPrice = calculatePrice(shopItem, quantity, player);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean canAfford = playerData.getCoins() >= totalPrice;
        boolean canFit = hasInventorySpace(player, shopItem, quantity);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Quantité: §e" + quantity);
        lore.add("§7Prix total: §6" + NumberFormatter.format(totalPrice) + " coins");

        // Afficher la réduction totale si applicable
        long baseTotal = shopItem.getPrice() * quantity;
        if (totalPrice < baseTotal) {
            long totalSaved = baseTotal - totalPrice;
            lore.add("§a💸 Économie totale: §6" + NumberFormatter.format(totalSaved) + " coins");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        if (canAfford && canFit) {
            lore.add("§a✅ §lVous pouvez acheter cet article");
            lore.add("§e▶ Cliquez pour confirmer l'achat!");
        } else if (!canAfford) {
            lore.add("§c❌ §lFonds insuffisants");
            lore.add("§7Il vous manque §6" + NumberFormatter.format(totalPrice - playerData.getCoins()) + " coins");
        } else {
            lore.add("§c❌ §lEspace d'inventaire insuffisant");
        }

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        if (canAfford && canFit) {
            guiManager.addGUIMetadata(item, GUIType.SHOP_QUANTITY, "action", "buy_item");
            guiManager.addGUIMetadata(item, GUIType.SHOP_QUANTITY, "item", shopItem.getId());
            guiManager.addGUIMetadata(item, GUIType.SHOP_QUANTITY, "quantity", String.valueOf(quantity));
        }

        return item;
    }

    private int[] getSuggestedQuantitiesFor(ShopItem shopItem) {
        int max = Math.max(1, shopItem.createItemStack(1).getMaxStackSize());
        // Cas explicites demandés
        if (max >= 64) return new int[] {1, 2, 4, 8, 16, 32, 64};
        if (max == 32) return new int[] {1, 2, 4, 8, 16, 32, 64};
        if (max == 16) return new int[] {1, 2, 4, 8, 16, 24, 32};
        if (max == 8)  return new int[] {1, 2, 4, 8, 12, 16, 24};

        // Fallback générique: construit une progression jusqu'à 7 valeurs
        java.util.ArrayList<Integer> list = new java.util.ArrayList<>();
        list.add(1);
        if (max >= 2) list.add(2);
        if (max >= 4) list.add(4);
        if (max >= 8) list.add(8);
        if (max >= 16) list.add(16);
        if (max >= 32) list.add(32);
        if (list.isEmpty()) list.add(max);
        // Compléter avec des multiples au‑delà du max si besoin
        int cursor = list.isEmpty() ? max : Math.max(list.get(list.size() - 1), max);
        while (list.size() < 7) {
            cursor = cursor + Math.max(1, max / 2);
            list.add(cursor);
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§7← §lRetour");
        guiManager.applyLore(meta, List.of("§7Retourner au menu précédent"));
        item.setItemMeta(meta);
        guiManager.addGUIMetadata(item, GUIType.SHOP_CATEGORY, "action", "back");

        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§c✗ §lFermer");
        guiManager.applyLore(meta, List.of("§7Ferme le magasin"));
        item.setItemMeta(meta);
        guiManager.addGUIMetadata(item, GUIType.SHOP_MAIN, "action", "close");

        return item;
    }

    // ==================== GESTION DES ITEMS ====================

    /**
     * Récupère les items disponibles pour une catégorie
     */
    private List<ShopItem> getItemsForCategory(ShopCategory category) {
        List<ShopItem> items = new ArrayList<>();

        switch (category) {
            case PVP -> {
                // ==================== ARMURES COMPLÈTES ====================

                // Armures Netherite
                items.add(new ShopItem("netherite_helmet_enchanted", "Casque en Netherite Enchanté",
                        createEnchantedArmor(Material.NETHERITE_HELMET), 150000, category));
                items.add(new ShopItem("netherite_chestplate_enchanted", "Plastron en Netherite Enchanté",
                        createEnchantedArmor(Material.NETHERITE_CHESTPLATE), 250000, category));
                items.add(new ShopItem("netherite_leggings_enchanted", "Jambières en Netherite Enchantées",
                        createEnchantedArmor(Material.NETHERITE_LEGGINGS), 200000, category));
                items.add(new ShopItem("netherite_boots_enchanted", "Bottes en Netherite Enchantées",
                        createEnchantedArmor(Material.NETHERITE_BOOTS), 120000, category));

                // Armures Diamant
                items.add(new ShopItem("diamond_helmet_enchanted", "Casque en Diamant Enchanté",
                        createEnchantedArmor(Material.DIAMOND_HELMET), 50000, category));
                items.add(new ShopItem("diamond_chestplate_enchanted", "Plastron en Diamant Enchanté",
                        createEnchantedArmor(Material.DIAMOND_CHESTPLATE), 80000, category));
                items.add(new ShopItem("diamond_leggings_enchanted", "Jambières en Diamant Enchantées",
                        createEnchantedArmor(Material.DIAMOND_LEGGINGS), 70000, category));
                items.add(new ShopItem("diamond_boots_enchanted", "Bottes en Diamant Enchantées",
                        createEnchantedArmor(Material.DIAMOND_BOOTS), 40000, category));

                // Armures Fer
                items.add(new ShopItem("iron_helmet_enchanted", "Casque en Fer Enchanté",
                        createEnchantedArmor(Material.IRON_HELMET), 15000, category));
                items.add(new ShopItem("iron_chestplate_enchanted", "Plastron en Fer Enchanté",
                        createEnchantedArmor(Material.IRON_CHESTPLATE), 25000, category));
                items.add(new ShopItem("iron_leggings_enchanted", "Jambières en Fer Enchantées",
                        createEnchantedArmor(Material.IRON_LEGGINGS), 20000, category));
                items.add(new ShopItem("iron_boots_enchanted", "Bottes en Fer Enchantées",
                        createEnchantedArmor(Material.IRON_BOOTS), 12000, category));

                // ==================== ARMES COMPLÈTES ====================

                // Épées
                items.add(new ShopItem("netherite_sword_enchanted", "Épée en Netherite Enchantée",
                        createEnchantedWeapon(Material.NETHERITE_SWORD), 120000, category));
                items.add(new ShopItem("diamond_sword_enchanted", "Épée en Diamant Enchantée",
                        createEnchantedWeapon(Material.DIAMOND_SWORD), 60000, category));
                items.add(new ShopItem("iron_sword_enchanted", "Épée en Fer Enchantée",
                        createEnchantedWeapon(Material.IRON_SWORD), 25000, category));
                items.add(new ShopItem("golden_sword_enchanted", "Épée en Or Enchantée",
                        createEnchantedWeapon(Material.GOLDEN_SWORD), 35000, category));

                // Haches de combat
                items.add(new ShopItem("netherite_axe_enchanted", "Hache en Netherite Enchantée",
                        createEnchantedWeapon(Material.NETHERITE_AXE), 100000, category));
                items.add(new ShopItem("diamond_axe_enchanted", "Hache en Diamant Enchantée",
                        createEnchantedWeapon(Material.DIAMOND_AXE), 50000, category));

                // Arcs et arbalètes
                items.add(new ShopItem("bow_enchanted", "Arc Enchanté",
                        createEnchantedBow(), 45000, category));
                items.add(new ShopItem("crossbow_enchanted", "Arbalète Enchantée",
                        createEnchantedCrossbow(), 55000, category));

                // Tridents
                items.add(new ShopItem("trident_enchanted", "Trident Enchanté",
                        createEnchantedTrident(), 85000, category));

                // ==================== CONSOMMABLES PVP ====================

                // Pommes
                items.add(new ShopItem("golden_apple", "Pomme d'Or",
                        new ItemStack(Material.GOLDEN_APPLE), 1000, category));
                items.add(new ShopItem("enchanted_golden_apple", "Pomme d'Or Enchantée",
                        new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 10000, category));

                // Projectiles
                items.add(new ShopItem("arrows", "Flèches",
                        new ItemStack(Material.ARROW), 10, category));
                items.add(new ShopItem("spectral_arrow", "Flèche Spectrale",
                        new ItemStack(Material.SPECTRAL_ARROW), 50, category));
                items.add(new ShopItem("tipped_arrow_poison", "Flèche Empoisonnée",
                        createTippedArrow("poison"), 100, category));
                items.add(new ShopItem("tipped_arrow_slowness", "Flèche de Lenteur",
                        createTippedArrow("slowness"), 80, category));
                items.add(new ShopItem("tipped_arrow_weakness", "Flèche de Faiblesse",
                        createTippedArrow("weakness"), 90, category));

                // Items de mobilité
                items.add(new ShopItem("ender_pearl", "Perle de l'Ender",
                        new ItemStack(Material.ENDER_PEARL), 500, category));
                items.add(new ShopItem("chorus_fruit", "Fruit de Chorus",
                        new ItemStack(Material.CHORUS_FRUIT), 200, category));

                // Potions de combat
                items.add(new ShopItem("potion_strength", "Potion de Force",
                        createPotion("strength", 3, 0), 1500, category));
                items.add(new ShopItem("potion_speed", "Potion de Rapidité",
                        createPotion("speed", 3, 0), 1200, category));
                items.add(new ShopItem("potion_fire_resistance", "Potion de Résistance au Feu",
                        createPotion("fire_resistance", 3, 0), 2000, category));
                items.add(new ShopItem("potion_invisibility", "Potion d'Invisibilité",
                        createPotion("invisibility", 3, 0), 3000, category));
                items.add(new ShopItem("potion_night_vision", "Potion de Vision Nocturne",
                        createPotion("night_vision", 3, 0), 800, category));

                // Potions splash
                items.add(new ShopItem("splash_potion_poison", "Potion Splash Poison",
                        createSplashPotion("poison", 1, 0), 800, category));
                items.add(new ShopItem("splash_potion_slowness", "Potion Splash Lenteur",
                        createSplashPotion("slowness", 1, 20), 600, category));
                items.add(new ShopItem("splash_potion_weakness", "Potion Splash Faiblesse",
                        createSplashPotion("weakness", 1, 30), 700, category));
                items.add(new ShopItem("splash_potion_harming", "Potion Splash Dégâts",
                        createSplashPotion("instant_damage", 0, 0), 1200, category));

                // Items spéciaux PvP
                items.add(new ShopItem("totem_of_undying", "Totem d'Immortalité",
                        new ItemStack(Material.TOTEM_OF_UNDYING), 25000, category));
                items.add(new ShopItem("shield", "Bouclier",
                        new ItemStack(Material.SHIELD), 8000, category));
                items.add(new ShopItem("elytra", "Élytres",
                        createEnchantedElytra(), 50000, category));

                // Blocs tactiques
                items.add(new ShopItem("obsidian_pvp", "Obsidienne (PvP)",
                        new ItemStack(Material.OBSIDIAN), 100, category));
                items.add(new ShopItem("cobweb", "Toile d'Araignée",
                        new ItemStack(Material.COBWEB), 500, category));
                items.add(new ShopItem("anvil", "Enclume",
                        new ItemStack(Material.ANVIL), 2000, category));

                // TNT et explosifs
                items.add(new ShopItem("tnt", "TNT",
                        new ItemStack(Material.TNT), 1000, category));
                items.add(new ShopItem("fire_charge", "Boule de Feu",
                        new ItemStack(Material.FIRE_CHARGE), 200, category));

                // Matériaux d'enchantement
                items.add(new ShopItem("enchanting_table", "Table d'Enchantement",
                        new ItemStack(Material.ENCHANTING_TABLE), 15000, category));
                items.add(new ShopItem("experience_bottle", "Fiole d'Expérience",
                        new ItemStack(Material.EXPERIENCE_BOTTLE), 100, category));
                items.add(new ShopItem("lapis_lazuli", "Lapis-lazuli",
                        new ItemStack(Material.LAPIS_LAZULI), 50, category));
            }

            case BLOCKS -> {
                // ==================== BLOCS DE CONSTRUCTION ====================

                // Pierre et variantes
                items.add(new ShopItem("stone", "Pierre", new ItemStack(Material.STONE), 5, category));
                items.add(new ShopItem("cobblestone", "Pierre Taillée", new ItemStack(Material.COBBLESTONE), 3, category));
                items.add(new ShopItem("smooth_stone", "Pierre Lisse", new ItemStack(Material.SMOOTH_STONE), 8, category));
                items.add(new ShopItem("stone_bricks", "Briques de Pierre", new ItemStack(Material.STONE_BRICKS), 10, category));
                items.add(new ShopItem("mossy_stone_bricks", "Briques de Pierre Moussues", new ItemStack(Material.MOSSY_STONE_BRICKS), 15, category));
                items.add(new ShopItem("cracked_stone_bricks", "Briques de Pierre Fissurées", new ItemStack(Material.CRACKED_STONE_BRICKS), 12, category));
                items.add(new ShopItem("chiseled_stone_bricks", "Briques de Pierre Sculptées", new ItemStack(Material.CHISELED_STONE_BRICKS), 20, category));

                // Granite, Diorite, Andésite
                items.add(new ShopItem("granite", "Granite", new ItemStack(Material.GRANITE), 6, category));
                items.add(new ShopItem("polished_granite", "Granite Poli", new ItemStack(Material.POLISHED_GRANITE), 10, category));
                items.add(new ShopItem("diorite", "Diorite", new ItemStack(Material.DIORITE), 6, category));
                items.add(new ShopItem("polished_diorite", "Diorite Polie", new ItemStack(Material.POLISHED_DIORITE), 10, category));
                items.add(new ShopItem("andesite", "Andésite", new ItemStack(Material.ANDESITE), 6, category));
                items.add(new ShopItem("polished_andesite", "Andésite Polie", new ItemStack(Material.POLISHED_ANDESITE), 10, category));

                // Bois (tous les types)
                items.add(new ShopItem("oak_log", "Bûche de Chêne", new ItemStack(Material.OAK_LOG), 20, category));
                items.add(new ShopItem("birch_log", "Bûche de Bouleau", new ItemStack(Material.BIRCH_LOG), 20, category));
                items.add(new ShopItem("spruce_log", "Bûche de Sapin", new ItemStack(Material.SPRUCE_LOG), 20, category));
                items.add(new ShopItem("jungle_log", "Bûche de Jungle", new ItemStack(Material.JUNGLE_LOG), 25, category));
                items.add(new ShopItem("acacia_log", "Bûche d'Acacia", new ItemStack(Material.ACACIA_LOG), 25, category));
                items.add(new ShopItem("dark_oak_log", "Bûche de Chêne Noir", new ItemStack(Material.DARK_OAK_LOG), 30, category));
                items.add(new ShopItem("mangrove_log", "Bûche de Mangrove", new ItemStack(Material.MANGROVE_LOG), 35, category));
                items.add(new ShopItem("cherry_log", "Bûche de Cerisier", new ItemStack(Material.CHERRY_LOG), 40, category));

                // Planches
                items.add(new ShopItem("oak_planks", "Planches de Chêne", new ItemStack(Material.OAK_PLANKS), 15, category));
                items.add(new ShopItem("birch_planks", "Planches de Bouleau", new ItemStack(Material.BIRCH_PLANKS), 15, category));
                items.add(new ShopItem("spruce_planks", "Planches de Sapin", new ItemStack(Material.SPRUCE_PLANKS), 15, category));
                items.add(new ShopItem("jungle_planks", "Planches de Jungle", new ItemStack(Material.JUNGLE_PLANKS), 18, category));
                items.add(new ShopItem("acacia_planks", "Planches d'Acacia", new ItemStack(Material.ACACIA_PLANKS), 18, category));
                items.add(new ShopItem("dark_oak_planks", "Planches de Chêne Noir", new ItemStack(Material.DARK_OAK_PLANKS), 22, category));

                // Terre et sable
                items.add(new ShopItem("dirt", "Terre", new ItemStack(Material.DIRT), 2, category));
                items.add(new ShopItem("grass_block", "Bloc d'Herbe", new ItemStack(Material.GRASS_BLOCK), 5, category));
                items.add(new ShopItem("coarse_dirt", "Terre Stérile", new ItemStack(Material.COARSE_DIRT), 4, category));
                items.add(new ShopItem("podzol", "Podzol", new ItemStack(Material.PODZOL), 8, category));
                items.add(new ShopItem("sand", "Sable", new ItemStack(Material.SAND), 8, category));
                items.add(new ShopItem("red_sand", "Sable Rouge", new ItemStack(Material.RED_SAND), 10, category));
                items.add(new ShopItem("gravel", "Gravier", new ItemStack(Material.GRAVEL), 6, category));
                items.add(new ShopItem("clay", "Argile", new ItemStack(Material.CLAY), 15, category));

                // Verre
                items.add(new ShopItem("glass", "Verre", new ItemStack(Material.GLASS), 15, category));
                items.add(new ShopItem("white_stained_glass", "Verre Blanc", new ItemStack(Material.WHITE_STAINED_GLASS), 18, category));
                items.add(new ShopItem("orange_stained_glass", "Verre Orange", new ItemStack(Material.ORANGE_STAINED_GLASS), 18, category));
                items.add(new ShopItem("magenta_stained_glass", "Verre Magenta", new ItemStack(Material.MAGENTA_STAINED_GLASS), 18, category));
                items.add(new ShopItem("light_blue_stained_glass", "Verre Bleu Clair", new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS), 18, category));
                items.add(new ShopItem("yellow_stained_glass", "Verre Jaune", new ItemStack(Material.YELLOW_STAINED_GLASS), 18, category));
                items.add(new ShopItem("lime_stained_glass", "Verre Vert Clair", new ItemStack(Material.LIME_STAINED_GLASS), 18, category));
                items.add(new ShopItem("pink_stained_glass", "Verre Rose", new ItemStack(Material.PINK_STAINED_GLASS), 18, category));
                items.add(new ShopItem("gray_stained_glass", "Verre Gris", new ItemStack(Material.GRAY_STAINED_GLASS), 18, category));
                items.add(new ShopItem("light_gray_stained_glass", "Verre Gris Clair", new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS), 18, category));
                items.add(new ShopItem("cyan_stained_glass", "Verre Cyan", new ItemStack(Material.CYAN_STAINED_GLASS), 18, category));
                items.add(new ShopItem("purple_stained_glass", "Verre Violet", new ItemStack(Material.PURPLE_STAINED_GLASS), 18, category));
                items.add(new ShopItem("blue_stained_glass", "Verre Bleu", new ItemStack(Material.BLUE_STAINED_GLASS), 18, category));
                items.add(new ShopItem("brown_stained_glass", "Verre Marron", new ItemStack(Material.BROWN_STAINED_GLASS), 18, category));
                items.add(new ShopItem("green_stained_glass", "Verre Vert", new ItemStack(Material.GREEN_STAINED_GLASS), 18, category));
                items.add(new ShopItem("red_stained_glass", "Verre Rouge", new ItemStack(Material.RED_STAINED_GLASS), 18, category));
                items.add(new ShopItem("black_stained_glass", "Verre Noir", new ItemStack(Material.BLACK_STAINED_GLASS), 18, category));

                // Laine colorée
                items.add(new ShopItem("white_wool", "Laine Blanche", new ItemStack(Material.WHITE_WOOL), 25, category));
                items.add(new ShopItem("orange_wool", "Laine Orange", new ItemStack(Material.ORANGE_WOOL), 30, category));
                items.add(new ShopItem("magenta_wool", "Laine Magenta", new ItemStack(Material.MAGENTA_WOOL), 30, category));
                items.add(new ShopItem("light_blue_wool", "Laine Bleu Clair", new ItemStack(Material.LIGHT_BLUE_WOOL), 30, category));
                items.add(new ShopItem("yellow_wool", "Laine Jaune", new ItemStack(Material.YELLOW_WOOL), 30, category));
                items.add(new ShopItem("lime_wool", "Laine Vert Clair", new ItemStack(Material.LIME_WOOL), 30, category));
                items.add(new ShopItem("pink_wool", "Laine Rose", new ItemStack(Material.PINK_WOOL), 30, category));
                items.add(new ShopItem("gray_wool", "Laine Grise", new ItemStack(Material.GRAY_WOOL), 30, category));
                items.add(new ShopItem("light_gray_wool", "Laine Gris Clair", new ItemStack(Material.LIGHT_GRAY_WOOL), 30, category));
                items.add(new ShopItem("cyan_wool", "Laine Cyan", new ItemStack(Material.CYAN_WOOL), 30, category));
                items.add(new ShopItem("purple_wool", "Laine Violette", new ItemStack(Material.PURPLE_WOOL), 30, category));
                items.add(new ShopItem("blue_wool", "Laine Bleue", new ItemStack(Material.BLUE_WOOL), 30, category));
                items.add(new ShopItem("brown_wool", "Laine Marron", new ItemStack(Material.BROWN_WOOL), 30, category));
                items.add(new ShopItem("green_wool", "Laine Verte", new ItemStack(Material.GREEN_WOOL), 30, category));
                items.add(new ShopItem("red_wool", "Laine Rouge", new ItemStack(Material.RED_WOOL), 30, category));
                items.add(new ShopItem("black_wool", "Laine Noire", new ItemStack(Material.BLACK_WOOL), 30, category));

                // Blocs spéciaux
                items.add(new ShopItem("obsidian", "Obsidienne", new ItemStack(Material.OBSIDIAN), 100, category));
                items.add(new ShopItem("bedrock", "Bedrock", new ItemStack(Material.BEDROCK), 50000, category));
                items.add(new ShopItem("netherrack", "Netherrack", new ItemStack(Material.NETHERRACK), 12, category));
                items.add(new ShopItem("end_stone", "Pierre de l'End", new ItemStack(Material.END_STONE), 50, category));
                items.add(new ShopItem("soul_sand", "Sable des Âmes", new ItemStack(Material.SOUL_SAND), 25, category));
                items.add(new ShopItem("soul_soil", "Terre des Âmes", new ItemStack(Material.SOUL_SOIL), 20, category));

                // Blocs de minerai décoratifs
                items.add(new ShopItem("iron_block", "Bloc de Fer", new ItemStack(Material.IRON_BLOCK), 2000, category));
                items.add(new ShopItem("gold_block", "Bloc d'Or", new ItemStack(Material.GOLD_BLOCK), 5000, category));
                items.add(new ShopItem("diamond_block", "Bloc de Diamant", new ItemStack(Material.DIAMOND_BLOCK), 15000, category));
                items.add(new ShopItem("emerald_block", "Bloc d'Émeraude", new ItemStack(Material.EMERALD_BLOCK), 20000, category));
                items.add(new ShopItem("netherite_block", "Bloc de Netherite", new ItemStack(Material.NETHERITE_BLOCK), 50000, category));

                // Escaliers et dalles populaires
                items.add(new ShopItem("oak_stairs", "Escaliers de Chêne", new ItemStack(Material.OAK_STAIRS), 12, category));
                items.add(new ShopItem("stone_stairs", "Escaliers de Pierre", new ItemStack(Material.STONE_STAIRS), 8, category));
                items.add(new ShopItem("stone_brick_stairs", "Escaliers de Briques de Pierre", new ItemStack(Material.STONE_BRICK_STAIRS), 15, category));
                items.add(new ShopItem("oak_slab", "Dalle de Chêne", new ItemStack(Material.OAK_SLAB), 8, category));
                items.add(new ShopItem("stone_slab", "Dalle de Pierre", new ItemStack(Material.STONE_SLAB), 4, category));
                items.add(new ShopItem("stone_brick_slab", "Dalle de Briques de Pierre", new ItemStack(Material.STONE_BRICK_SLAB), 8, category));
            }

            case FOOD -> {
                // ==================== ALIMENTS DE BASE ====================

                // Pains et céréales
                items.add(new ShopItem("bread", "Pain", new ItemStack(Material.BREAD), 25, category));
                items.add(new ShopItem("wheat", "Blé", new ItemStack(Material.WHEAT), 8, category));
                items.add(new ShopItem("cake", "Gâteau", new ItemStack(Material.CAKE), 200, category));
                items.add(new ShopItem("cookie", "Cookie", new ItemStack(Material.COOKIE), 15, category));
                items.add(new ShopItem("pumpkin_pie", "Tarte à la Citrouille", new ItemStack(Material.PUMPKIN_PIE), 40, category));

                // Viandes crues
                items.add(new ShopItem("beef", "Bœuf Cru", new ItemStack(Material.BEEF), 20, category));
                items.add(new ShopItem("porkchop", "Côtelette de Porc Crue", new ItemStack(Material.PORKCHOP), 18, category));
                items.add(new ShopItem("chicken", "Poulet Cru", new ItemStack(Material.CHICKEN), 15, category));
                items.add(new ShopItem("mutton", "Mouton Cru", new ItemStack(Material.MUTTON), 16, category));
                items.add(new ShopItem("rabbit", "Lapin Cru", new ItemStack(Material.RABBIT), 22, category));

                // Viandes cuites
                items.add(new ShopItem("cooked_beef", "Bœuf Cuit", new ItemStack(Material.COOKED_BEEF), 40, category));
                items.add(new ShopItem("cooked_porkchop", "Côtelette de Porc Cuite", new ItemStack(Material.COOKED_PORKCHOP), 35, category));
                items.add(new ShopItem("cooked_chicken", "Poulet Cuit", new ItemStack(Material.COOKED_CHICKEN), 30, category));
                items.add(new ShopItem("cooked_mutton", "Mouton Cuit", new ItemStack(Material.COOKED_MUTTON), 32, category));
                items.add(new ShopItem("cooked_rabbit", "Lapin Cuit", new ItemStack(Material.COOKED_RABBIT), 44, category));

                // Poissons crus
                items.add(new ShopItem("cod", "Morue Crue", new ItemStack(Material.COD), 18, category));
                items.add(new ShopItem("salmon", "Saumon Cru", new ItemStack(Material.SALMON), 22, category));
                items.add(new ShopItem("tropical_fish", "Poisson Tropical", new ItemStack(Material.TROPICAL_FISH), 25, category));
                items.add(new ShopItem("pufferfish", "Poisson-Globe", new ItemStack(Material.PUFFERFISH), 35, category));

                // Poissons cuits
                items.add(new ShopItem("cooked_cod", "Morue Cuite", new ItemStack(Material.COOKED_COD), 35, category));
                items.add(new ShopItem("cooked_salmon", "Saumon Cuit", new ItemStack(Material.COOKED_SALMON), 44, category));

                // Fruits et légumes
                items.add(new ShopItem("apple", "Pomme", new ItemStack(Material.APPLE), 15, category));
                items.add(new ShopItem("carrot", "Carotte", new ItemStack(Material.CARROT), 10, category));
                items.add(new ShopItem("potato", "Pomme de Terre", new ItemStack(Material.POTATO), 8, category));
                items.add(new ShopItem("baked_potato", "Pomme de Terre Cuite", new ItemStack(Material.BAKED_POTATO), 20, category));
                items.add(new ShopItem("beetroot", "Betterave", new ItemStack(Material.BEETROOT), 12, category));
                items.add(new ShopItem("melon_slice", "Tranche de Pastèque", new ItemStack(Material.MELON_SLICE), 8, category));
                items.add(new ShopItem("sweet_berries", "Baies Sucrées", new ItemStack(Material.SWEET_BERRIES), 18, category));
                items.add(new ShopItem("glow_berries", "Baies Lumineuses", new ItemStack(Material.GLOW_BERRIES), 25, category));

                // Champignons et soupes
                items.add(new ShopItem("mushroom_stew", "Soupe aux Champignons", new ItemStack(Material.MUSHROOM_STEW), 35, category));
                items.add(new ShopItem("rabbit_stew", "Ragoût de Lapin", new ItemStack(Material.RABBIT_STEW), 60, category));
                items.add(new ShopItem("beetroot_soup", "Soupe de Betterave", new ItemStack(Material.BEETROOT_SOUP), 45, category));
                items.add(new ShopItem("suspicious_stew", "Ragoût Suspect", new ItemStack(Material.SUSPICIOUS_STEW), 80, category));

                // Aliments spéciaux
                items.add(new ShopItem("golden_carrot", "Carotte Dorée", new ItemStack(Material.GOLDEN_CARROT), 150, category));
                items.add(new ShopItem("golden_apple", "Pomme d'Or", new ItemStack(Material.GOLDEN_APPLE), 1000, category));
                items.add(new ShopItem("enchanted_golden_apple", "Pomme d'Or Enchantée", new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 10000, category));
                items.add(new ShopItem("honey_bottle", "Fiole de Miel", new ItemStack(Material.HONEY_BOTTLE), 40, category));

                // Ingrédients de cuisine
                items.add(new ShopItem("sugar", "Sucre", new ItemStack(Material.SUGAR), 12, category));
                items.add(new ShopItem("egg", "Œuf", new ItemStack(Material.EGG), 8, category));
                items.add(new ShopItem("milk_bucket", "Seau de Lait", new ItemStack(Material.MILK_BUCKET), 50, category));
                items.add(new ShopItem("cocoa_beans", "Fèves de Cacao", new ItemStack(Material.COCOA_BEANS), 15, category));

                // Potions de régénération
                items.add(new ShopItem("potion_healing", "Potion de Soin",
                        createPotion("instant_health", 0, 0), 800, category));
                items.add(new ShopItem("potion_regeneration", "Potion de Régénération",
                        createPotion("regeneration", 1, 45), 1200, category));
                items.add(new ShopItem("potion_saturation", "Potion de Saturation",
                        createPotion("saturation", 0, 0), 2000, category));
            }

            case TOOLS -> {

                // ==================== HACHES ====================

                items.add(new ShopItem("netherite_axe", "Hache en Netherite", new ItemStack(Material.NETHERITE_AXE), 20000, category));
                items.add(new ShopItem("diamond_axe", "Hache en Diamant", new ItemStack(Material.DIAMOND_AXE), 4000, category));
                items.add(new ShopItem("iron_axe", "Hache en Fer", new ItemStack(Material.IRON_AXE), 1000, category));
                items.add(new ShopItem("stone_axe", "Hache en Pierre", new ItemStack(Material.STONE_AXE), 250, category));
                items.add(new ShopItem("golden_axe", "Hache en Or", new ItemStack(Material.GOLDEN_AXE), 600, category));

                // ==================== PELLES ====================

                items.add(new ShopItem("netherite_shovel", "Pelle en Netherite", new ItemStack(Material.NETHERITE_SHOVEL), 15000, category));
                items.add(new ShopItem("diamond_shovel", "Pelle en Diamant", new ItemStack(Material.DIAMOND_SHOVEL), 3000, category));
                items.add(new ShopItem("iron_shovel", "Pelle en Fer", new ItemStack(Material.IRON_SHOVEL), 800, category));
                items.add(new ShopItem("stone_shovel", "Pelle en Pierre", new ItemStack(Material.STONE_SHOVEL), 200, category));
                items.add(new ShopItem("golden_shovel", "Pelle en Or", new ItemStack(Material.GOLDEN_SHOVEL), 500, category));

                // ==================== HOUES ====================

                items.add(new ShopItem("netherite_hoe", "Houe en Netherite", new ItemStack(Material.NETHERITE_HOE), 12000, category));
                items.add(new ShopItem("diamond_hoe", "Houe en Diamant", new ItemStack(Material.DIAMOND_HOE), 2500, category));
                items.add(new ShopItem("iron_hoe", "Houe en Fer", new ItemStack(Material.IRON_HOE), 600, category));
                items.add(new ShopItem("stone_hoe", "Houe en Pierre", new ItemStack(Material.STONE_HOE), 150, category));
                items.add(new ShopItem("golden_hoe", "Houe en Or", new ItemStack(Material.GOLDEN_HOE), 400, category));

                // ==================== OUTILS SPÉCIALISÉS ====================

                items.add(new ShopItem("shears", "Cisailles", new ItemStack(Material.SHEARS), 500, category));
                items.add(new ShopItem("fishing_rod", "Canne à Pêche", new ItemStack(Material.FISHING_ROD), 800, category));
                items.add(new ShopItem("flint_and_steel", "Briquet", new ItemStack(Material.FLINT_AND_STEEL), 200, category));
                items.add(new ShopItem("compass", "Boussole", new ItemStack(Material.COMPASS), 300, category));
                items.add(new ShopItem("clock", "Horloge", new ItemStack(Material.CLOCK), 400, category));
                items.add(new ShopItem("spyglass", "Longue-vue", new ItemStack(Material.SPYGLASS), 1500, category));

                // Outils de transport
                items.add(new ShopItem("boat_oak", "Bateau en Chêne", new ItemStack(Material.OAK_BOAT), 250, category));
                items.add(new ShopItem("chest_boat_oak", "Bateau avec Coffre en Chêne", new ItemStack(Material.OAK_CHEST_BOAT), 400, category));
                items.add(new ShopItem("minecart", "Wagon", new ItemStack(Material.MINECART), 500, category));
                items.add(new ShopItem("chest_minecart", "Wagon avec Coffre", new ItemStack(Material.CHEST_MINECART), 800, category));
                items.add(new ShopItem("hopper_minecart", "Wagon avec Entonnoir", new ItemStack(Material.HOPPER_MINECART), 1200, category));
                items.add(new ShopItem("tnt_minecart", "Wagon avec TNT", new ItemStack(Material.TNT_MINECART), 1500, category));

                // Outils magiques
                items.add(new ShopItem("enchanting_table", "Table d'Enchantement", new ItemStack(Material.ENCHANTING_TABLE), 15000, category));
                items.add(new ShopItem("anvil", "Enclume", new ItemStack(Material.ANVIL), 2000, category));
                items.add(new ShopItem("grindstone", "Meule", new ItemStack(Material.GRINDSTONE), 800, category));
                items.add(new ShopItem("smithing_table", "Table de Forge", new ItemStack(Material.SMITHING_TABLE), 1200, category));

                // Outils de mesure
                items.add(new ShopItem("lead", "Laisse", new ItemStack(Material.LEAD), 300, category));
                items.add(new ShopItem("name_tag", "Étiquette", new ItemStack(Material.NAME_TAG), 1000, category));
            }

            case REDSTONE -> {
                // ==================== COMPOSANTS DE BASE ====================

                items.add(new ShopItem("redstone", "Poudre de Redstone", new ItemStack(Material.REDSTONE), 25, category));
                items.add(new ShopItem("redstone_block", "Bloc de Redstone", new ItemStack(Material.REDSTONE_BLOCK), 250, category));
                items.add(new ShopItem("redstone_torch", "Torche de Redstone", new ItemStack(Material.REDSTONE_TORCH), 30, category));
                items.add(new ShopItem("lever", "Levier", new ItemStack(Material.LEVER), 40, category));
                items.add(new ShopItem("stone_button", "Bouton en Pierre", new ItemStack(Material.STONE_BUTTON), 35, category));
                items.add(new ShopItem("oak_button", "Bouton en Chêne", new ItemStack(Material.OAK_BUTTON), 25, category));
                items.add(new ShopItem("stone_pressure_plate", "Plaque de Pression en Pierre", new ItemStack(Material.STONE_PRESSURE_PLATE), 50, category));
                items.add(new ShopItem("oak_pressure_plate", "Plaque de Pression en Chêne", new ItemStack(Material.OAK_PRESSURE_PLATE), 35, category));
                items.add(new ShopItem("heavy_weighted_pressure_plate", "Plaque de Pression Lourde", new ItemStack(Material.HEAVY_WEIGHTED_PRESSURE_PLATE), 100, category));
                items.add(new ShopItem("light_weighted_pressure_plate", "Plaque de Pression Légère", new ItemStack(Material.LIGHT_WEIGHTED_PRESSURE_PLATE), 80, category));

                // ==================== PORTES ET TRAPPES ====================

                items.add(new ShopItem("oak_door", "Porte en Chêne", new ItemStack(Material.OAK_DOOR), 60, category));
                items.add(new ShopItem("iron_door", "Porte en Fer", new ItemStack(Material.IRON_DOOR), 200, category));
                items.add(new ShopItem("oak_trapdoor", "Trappe en Chêne", new ItemStack(Material.OAK_TRAPDOOR), 45, category));
                items.add(new ShopItem("iron_trapdoor", "Trappe en Fer", new ItemStack(Material.IRON_TRAPDOOR), 150, category));
                items.add(new ShopItem("oak_fence_gate", "Portillon en Chêne", new ItemStack(Material.OAK_FENCE_GATE), 50, category));

                // ==================== AUTOMATISATION ====================

                items.add(new ShopItem("dispenser", "Distributeur", new ItemStack(Material.DISPENSER), 300, category));
                items.add(new ShopItem("dropper", "Dropper", new ItemStack(Material.DROPPER), 250, category));
                items.add(new ShopItem("hopper", "Entonnoir", new ItemStack(Material.HOPPER), 800, category));
                items.add(new ShopItem("observer", "Observateur", new ItemStack(Material.OBSERVER), 400, category));
                items.add(new ShopItem("piston", "Piston", new ItemStack(Material.PISTON), 200, category));
                items.add(new ShopItem("sticky_piston", "Piston Collant", new ItemStack(Material.STICKY_PISTON), 300, category));
                items.add(new ShopItem("slime_block", "Bloc de Slime", new ItemStack(Material.SLIME_BLOCK), 500, category));
                items.add(new ShopItem("honey_block", "Bloc de Miel", new ItemStack(Material.HONEY_BLOCK), 400, category));

                // ==================== DÉTECTEURS ====================

                items.add(new ShopItem("tripwire_hook", "Crochet de Fil de Détente", new ItemStack(Material.TRIPWIRE_HOOK), 150, category));
                items.add(new ShopItem("daylight_detector", "Capteur de Luminosité", new ItemStack(Material.DAYLIGHT_DETECTOR), 600, category));
                items.add(new ShopItem("target", "Cible", new ItemStack(Material.TARGET), 200, category));
                items.add(new ShopItem("lightning_rod", "Paratonnerre", new ItemStack(Material.LIGHTNING_ROD), 800, category));

                // ==================== TRANSPORT ====================

                items.add(new ShopItem("rail", "Rail", new ItemStack(Material.RAIL), 30, category));
                items.add(new ShopItem("powered_rail", "Rail de Propulsion", new ItemStack(Material.POWERED_RAIL), 100, category));
                items.add(new ShopItem("detector_rail", "Rail Détecteur", new ItemStack(Material.DETECTOR_RAIL), 80, category));
                items.add(new ShopItem("activator_rail", "Rail Activateur", new ItemStack(Material.ACTIVATOR_RAIL), 70, category));

                // ==================== COMPARATEURS ET RÉPÉTEURS ====================

                items.add(new ShopItem("repeater", "Répéteur", new ItemStack(Material.REPEATER), 150, category));
                items.add(new ShopItem("comparator", "Comparateur", new ItemStack(Material.COMPARATOR), 200, category));

                // ==================== STOCKAGE AUTOMATISÉ ====================

                items.add(new ShopItem("chest", "Coffre", new ItemStack(Material.CHEST), 100, category));
                items.add(new ShopItem("trapped_chest", "Coffre Piégé", new ItemStack(Material.TRAPPED_CHEST), 150, category));
                items.add(new ShopItem("ender_chest", "Coffre de l'Ender", new ItemStack(Material.ENDER_CHEST), 5000, category));
                items.add(new ShopItem("shulker_box", "Boîte de Shulker", new ItemStack(Material.SHULKER_BOX), 8000, category));

                // Boîtes de Shulker colorées
                items.add(new ShopItem("white_shulker_box", "Boîte de Shulker Blanche", new ItemStack(Material.WHITE_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("orange_shulker_box", "Boîte de Shulker Orange", new ItemStack(Material.ORANGE_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("magenta_shulker_box", "Boîte de Shulker Magenta", new ItemStack(Material.MAGENTA_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("light_blue_shulker_box", "Boîte de Shulker Bleu Clair", new ItemStack(Material.LIGHT_BLUE_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("yellow_shulker_box", "Boîte de Shulker Jaune", new ItemStack(Material.YELLOW_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("lime_shulker_box", "Boîte de Shulker Vert Clair", new ItemStack(Material.LIME_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("pink_shulker_box", "Boîte de Shulker Rose", new ItemStack(Material.PINK_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("gray_shulker_box", "Boîte de Shulker Grise", new ItemStack(Material.GRAY_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("light_gray_shulker_box", "Boîte de Shulker Gris Clair", new ItemStack(Material.LIGHT_GRAY_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("cyan_shulker_box", "Boîte de Shulker Cyan", new ItemStack(Material.CYAN_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("purple_shulker_box", "Boîte de Shulker Violette", new ItemStack(Material.PURPLE_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("blue_shulker_box", "Boîte de Shulker Bleue", new ItemStack(Material.BLUE_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("brown_shulker_box", "Boîte de Shulker Marron", new ItemStack(Material.BROWN_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("green_shulker_box", "Boîte de Shulker Verte", new ItemStack(Material.GREEN_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("red_shulker_box", "Boîte de Shulker Rouge", new ItemStack(Material.RED_SHULKER_BOX), 8200, category));
                items.add(new ShopItem("black_shulker_box", "Boîte de Shulker Noire", new ItemStack(Material.BLACK_SHULKER_BOX), 8200, category));
            }

            case DECORATION -> {
                // ==================== ÉCLAIRAGE ====================

                items.add(new ShopItem("torch", "Torche", new ItemStack(Material.TORCH), 5, category));
                items.add(new ShopItem("soul_torch", "Torche des Âmes", new ItemStack(Material.SOUL_TORCH), 15, category));
                items.add(new ShopItem("lantern", "Lanterne", new ItemStack(Material.LANTERN), 30, category));
                items.add(new ShopItem("soul_lantern", "Lanterne des Âmes", new ItemStack(Material.SOUL_LANTERN), 45, category));
                items.add(new ShopItem("sea_lantern", "Lanterne Aquatique", new ItemStack(Material.SEA_LANTERN), 200, category));
                items.add(new ShopItem("glowstone", "Pierre Lumineuse", new ItemStack(Material.GLOWSTONE), 100, category));
                items.add(new ShopItem("shroomlight", "Champilumière", new ItemStack(Material.SHROOMLIGHT), 150, category));
                items.add(new ShopItem("jack_o_lantern", "Citrouille-Lanterne", new ItemStack(Material.JACK_O_LANTERN), 80, category));
                items.add(new ShopItem("campfire", "Feu de Camp", new ItemStack(Material.CAMPFIRE), 120, category));
                items.add(new ShopItem("soul_campfire", "Feu de Camp des Âmes", new ItemStack(Material.SOUL_CAMPFIRE), 180, category));

                // ==================== PLANTES DÉCORATIVES ====================

                items.add(new ShopItem("oak_sapling", "Pousse de Chêne", new ItemStack(Material.OAK_SAPLING), 50, category));
                items.add(new ShopItem("birch_sapling", "Pousse de Bouleau", new ItemStack(Material.BIRCH_SAPLING), 50, category));
                items.add(new ShopItem("spruce_sapling", "Pousse de Sapin", new ItemStack(Material.SPRUCE_SAPLING), 50, category));
                items.add(new ShopItem("jungle_sapling", "Pousse de Jungle", new ItemStack(Material.JUNGLE_SAPLING), 75, category));
                items.add(new ShopItem("acacia_sapling", "Pousse d'Acacia", new ItemStack(Material.ACACIA_SAPLING), 75, category));
                items.add(new ShopItem("dark_oak_sapling", "Pousse de Chêne Noir", new ItemStack(Material.DARK_OAK_SAPLING), 100, category));

                items.add(new ShopItem("grass", "Herbe", new ItemStack(Material.GRASS_BLOCK), 8, category));
                items.add(new ShopItem("fern", "Fougère", new ItemStack(Material.FERN), 10, category));
                items.add(new ShopItem("dead_bush", "Buisson Mort", new ItemStack(Material.DEAD_BUSH), 5, category));
                items.add(new ShopItem("seagrass", "Algue", new ItemStack(Material.SEAGRASS), 12, category));
                items.add(new ShopItem("kelp", "Varech", new ItemStack(Material.KELP), 15, category));
                items.add(new ShopItem("bamboo", "Bambou", new ItemStack(Material.BAMBOO), 20, category));
                items.add(new ShopItem("cactus", "Cactus", new ItemStack(Material.CACTUS), 25, category));
                items.add(new ShopItem("sugar_cane", "Canne à Sucre", new ItemStack(Material.SUGAR_CANE), 18, category));
                items.add(new ShopItem("vine", "Lianes", new ItemStack(Material.VINE), 15, category));
                items.add(new ShopItem("lily_pad", "Nénuphar", new ItemStack(Material.LILY_PAD), 30, category));

                // Fleurs
                items.add(new ShopItem("dandelion", "Pissenlit", new ItemStack(Material.DANDELION), 20, category));
                items.add(new ShopItem("poppy", "Coquelicot", new ItemStack(Material.POPPY), 20, category));
                items.add(new ShopItem("blue_orchid", "Orchidée Bleue", new ItemStack(Material.BLUE_ORCHID), 35, category));
                items.add(new ShopItem("allium", "Allium", new ItemStack(Material.ALLIUM), 30, category));
                items.add(new ShopItem("azure_bluet", "Myosotis", new ItemStack(Material.AZURE_BLUET), 25, category));
                items.add(new ShopItem("red_tulip", "Tulipe Rouge", new ItemStack(Material.RED_TULIP), 25, category));
                items.add(new ShopItem("orange_tulip", "Tulipe Orange", new ItemStack(Material.ORANGE_TULIP), 25, category));
                items.add(new ShopItem("white_tulip", "Tulipe Blanche", new ItemStack(Material.WHITE_TULIP), 25, category));
                items.add(new ShopItem("pink_tulip", "Tulipe Rose", new ItemStack(Material.PINK_TULIP), 25, category));
                items.add(new ShopItem("oxeye_daisy", "Marguerite", new ItemStack(Material.OXEYE_DAISY), 22, category));
                items.add(new ShopItem("cornflower", "Bleuet", new ItemStack(Material.CORNFLOWER), 28, category));
                items.add(new ShopItem("lily_of_the_valley", "Muguet", new ItemStack(Material.LILY_OF_THE_VALLEY), 40, category));
                items.add(new ShopItem("sunflower", "Tournesol", new ItemStack(Material.SUNFLOWER), 50, category));
                items.add(new ShopItem("lilac", "Lilas", new ItemStack(Material.LILAC), 45, category));
                items.add(new ShopItem("rose_bush", "Rosier", new ItemStack(Material.ROSE_BUSH), 60, category));
                items.add(new ShopItem("peony", "Pivoine", new ItemStack(Material.PEONY), 55, category));

                // ==================== TAPIS ET REVÊTEMENTS ====================

                items.add(new ShopItem("white_carpet", "Tapis Blanc", new ItemStack(Material.WHITE_CARPET), 18, category));
                items.add(new ShopItem("orange_carpet", "Tapis Orange", new ItemStack(Material.ORANGE_CARPET), 20, category));
                items.add(new ShopItem("magenta_carpet", "Tapis Magenta", new ItemStack(Material.MAGENTA_CARPET), 20, category));
                items.add(new ShopItem("light_blue_carpet", "Tapis Bleu Clair", new ItemStack(Material.LIGHT_BLUE_CARPET), 20, category));
                items.add(new ShopItem("yellow_carpet", "Tapis Jaune", new ItemStack(Material.YELLOW_CARPET), 20, category));
                items.add(new ShopItem("lime_carpet", "Tapis Vert Clair", new ItemStack(Material.LIME_CARPET), 20, category));
                items.add(new ShopItem("pink_carpet", "Tapis Rose", new ItemStack(Material.PINK_CARPET), 20, category));
                items.add(new ShopItem("gray_carpet", "Tapis Gris", new ItemStack(Material.GRAY_CARPET), 20, category));
                items.add(new ShopItem("light_gray_carpet", "Tapis Gris Clair", new ItemStack(Material.LIGHT_GRAY_CARPET), 20, category));
                items.add(new ShopItem("cyan_carpet", "Tapis Cyan", new ItemStack(Material.CYAN_CARPET), 20, category));
                items.add(new ShopItem("purple_carpet", "Tapis Violet", new ItemStack(Material.PURPLE_CARPET), 20, category));
                items.add(new ShopItem("blue_carpet", "Tapis Bleu", new ItemStack(Material.BLUE_CARPET), 20, category));
                items.add(new ShopItem("brown_carpet", "Tapis Marron", new ItemStack(Material.BROWN_CARPET), 20, category));
                items.add(new ShopItem("green_carpet", "Tapis Vert", new ItemStack(Material.GREEN_CARPET), 20, category));
                items.add(new ShopItem("red_carpet", "Tapis Rouge", new ItemStack(Material.RED_CARPET), 20, category));
                items.add(new ShopItem("black_carpet", "Tapis Noir", new ItemStack(Material.BLACK_CARPET), 20, category));

                // ==================== PEINTURES ET DÉCORATIONS ====================

                items.add(new ShopItem("painting", "Peinture", new ItemStack(Material.PAINTING), 150, category));
                items.add(new ShopItem("item_frame", "Cadre", new ItemStack(Material.ITEM_FRAME), 100, category));
                items.add(new ShopItem("glow_item_frame", "Cadre Lumineux", new ItemStack(Material.GLOW_ITEM_FRAME), 200, category));
                items.add(new ShopItem("armor_stand", "Porte-Armure", new ItemStack(Material.ARMOR_STAND), 500, category));
                items.add(new ShopItem("end_rod", "Baguette de l'End", new ItemStack(Material.END_ROD), 300, category));

                // ==================== BANNIÈRES ====================

                items.add(new ShopItem("white_banner", "Bannière Blanche", new ItemStack(Material.WHITE_BANNER), 200, category));
                items.add(new ShopItem("orange_banner", "Bannière Orange", new ItemStack(Material.ORANGE_BANNER), 220, category));
                items.add(new ShopItem("magenta_banner", "Bannière Magenta", new ItemStack(Material.MAGENTA_BANNER), 220, category));
                items.add(new ShopItem("light_blue_banner", "Bannière Bleu Clair", new ItemStack(Material.LIGHT_BLUE_BANNER), 220, category));
                items.add(new ShopItem("yellow_banner", "Bannière Jaune", new ItemStack(Material.YELLOW_BANNER), 220, category));
                items.add(new ShopItem("lime_banner", "Bannière Vert Clair", new ItemStack(Material.LIME_BANNER), 220, category));
                items.add(new ShopItem("pink_banner", "Bannière Rose", new ItemStack(Material.PINK_BANNER), 220, category));
                items.add(new ShopItem("gray_banner", "Bannière Grise", new ItemStack(Material.GRAY_BANNER), 220, category));
                items.add(new ShopItem("light_gray_banner", "Bannière Gris Clair", new ItemStack(Material.LIGHT_GRAY_BANNER), 220, category));
                items.add(new ShopItem("cyan_banner", "Bannière Cyan", new ItemStack(Material.CYAN_BANNER), 220, category));
                items.add(new ShopItem("purple_banner", "Bannière Violette", new ItemStack(Material.PURPLE_BANNER), 220, category));
                items.add(new ShopItem("blue_banner", "Bannière Bleue", new ItemStack(Material.BLUE_BANNER), 220, category));
                items.add(new ShopItem("brown_banner", "Bannière Marron", new ItemStack(Material.BROWN_BANNER), 220, category));
                items.add(new ShopItem("green_banner", "Bannière Verte", new ItemStack(Material.GREEN_BANNER), 220, category));
                items.add(new ShopItem("red_banner", "Bannière Rouge", new ItemStack(Material.RED_BANNER), 220, category));
                items.add(new ShopItem("black_banner", "Bannière Noire", new ItemStack(Material.BLACK_BANNER), 220, category));

                // ==================== TÊTES DÉCORATIVES ====================

                items.add(new ShopItem("skeleton_skull", "Crâne de Squelette", new ItemStack(Material.SKELETON_SKULL), 1000, category));
                items.add(new ShopItem("wither_skeleton_skull", "Crâne de Wither Squelette", new ItemStack(Material.WITHER_SKELETON_SKULL), 5000, category));
                items.add(new ShopItem("zombie_head", "Tête de Zombie", new ItemStack(Material.ZOMBIE_HEAD), 1200, category));
                items.add(new ShopItem("creeper_head", "Tête de Creeper", new ItemStack(Material.CREEPER_HEAD), 2000, category));
                items.add(new ShopItem("dragon_head", "Tête de Dragon", new ItemStack(Material.DRAGON_HEAD), 15000, category));

                // ==================== BLOCS MUSICAUX ====================

                items.add(new ShopItem("note_block", "Bloc Musical", new ItemStack(Material.NOTE_BLOCK), 400, category));
                items.add(new ShopItem("jukebox", "Juke-box", new ItemStack(Material.JUKEBOX), 800, category));

                // Disques musicaux
                items.add(new ShopItem("music_disc_13", "Disque Musical - 13", new ItemStack(Material.MUSIC_DISC_13), 2000, category));
                items.add(new ShopItem("music_disc_cat", "Disque Musical - Cat", new ItemStack(Material.MUSIC_DISC_CAT), 2000, category));
                items.add(new ShopItem("music_disc_blocks", "Disque Musical - Blocks", new ItemStack(Material.MUSIC_DISC_BLOCKS), 2000, category));
                items.add(new ShopItem("music_disc_chirp", "Disque Musical - Chirp", new ItemStack(Material.MUSIC_DISC_CHIRP), 2000, category));
                items.add(new ShopItem("music_disc_far", "Disque Musical - Far", new ItemStack(Material.MUSIC_DISC_FAR), 2000, category));
                items.add(new ShopItem("music_disc_mall", "Disque Musical - Mall", new ItemStack(Material.MUSIC_DISC_MALL), 2000, category));
                items.add(new ShopItem("music_disc_mellohi", "Disque Musical - Mellohi", new ItemStack(Material.MUSIC_DISC_MELLOHI), 2000, category));
                items.add(new ShopItem("music_disc_stal", "Disque Musical - Stal", new ItemStack(Material.MUSIC_DISC_STAL), 2000, category));
                items.add(new ShopItem("music_disc_strad", "Disque Musical - Strad", new ItemStack(Material.MUSIC_DISC_STRAD), 2000, category));
                items.add(new ShopItem("music_disc_ward", "Disque Musical - Ward", new ItemStack(Material.MUSIC_DISC_WARD), 2000, category));
                items.add(new ShopItem("music_disc_11", "Disque Musical - 11", new ItemStack(Material.MUSIC_DISC_11), 2500, category));
                items.add(new ShopItem("music_disc_wait", "Disque Musical - Wait", new ItemStack(Material.MUSIC_DISC_WAIT), 2000, category));
            }

            case FARMING -> {
                // ==================== GRAINES ET POUSSES ====================

                items.add(new ShopItem("wheat_seeds", "Graines de Blé", new ItemStack(Material.WHEAT_SEEDS), 15, category));
                items.add(new ShopItem("beetroot_seeds", "Graines de Betterave", new ItemStack(Material.BEETROOT_SEEDS), 20, category));
                items.add(new ShopItem("pumpkin_seeds", "Graines de Citrouille", new ItemStack(Material.PUMPKIN_SEEDS), 25, category));
                items.add(new ShopItem("melon_seeds", "Graines de Pastèque", new ItemStack(Material.MELON_SEEDS), 30, category));
                items.add(new ShopItem("carrot", "Carotte (Graine)", new ItemStack(Material.CARROT), 18, category));
                items.add(new ShopItem("potato", "Pomme de Terre (Graine)", new ItemStack(Material.POTATO), 15, category));
                items.add(new ShopItem("sweet_berries", "Baies Sucrées", new ItemStack(Material.SWEET_BERRIES), 35, category));
                items.add(new ShopItem("glow_berries", "Baies Lumineuses", new ItemStack(Material.GLOW_BERRIES), 50, category));
                items.add(new ShopItem("cocoa_beans", "Fèves de Cacao", new ItemStack(Material.COCOA_BEANS), 25, category));
                items.add(new ShopItem("nether_wart", "Verrues du Nether", new ItemStack(Material.NETHER_WART), 100, category));

                // Pousses d'arbres
                items.add(new ShopItem("oak_sapling", "Pousse de Chêne", new ItemStack(Material.OAK_SAPLING), 50, category));
                items.add(new ShopItem("birch_sapling", "Pousse de Bouleau", new ItemStack(Material.BIRCH_SAPLING), 50, category));
                items.add(new ShopItem("spruce_sapling", "Pousse de Sapin", new ItemStack(Material.SPRUCE_SAPLING), 50, category));
                items.add(new ShopItem("jungle_sapling", "Pousse de Jungle", new ItemStack(Material.JUNGLE_SAPLING), 75, category));
                items.add(new ShopItem("acacia_sapling", "Pousse d'Acacia", new ItemStack(Material.ACACIA_SAPLING), 75, category));
                items.add(new ShopItem("dark_oak_sapling", "Pousse de Chêne Noir", new ItemStack(Material.DARK_OAK_SAPLING), 100, category));
                items.add(new ShopItem("mangrove_propagule", "Propagule de Mangrove", new ItemStack(Material.MANGROVE_PROPAGULE), 120, category));
                items.add(new ShopItem("cherry_sapling", "Pousse de Cerisier", new ItemStack(Material.CHERRY_SAPLING), 150, category));

                // ==================== OUTILS D'AGRICULTURE ====================

                items.add(new ShopItem("wooden_hoe", "Houe en Bois", new ItemStack(Material.WOODEN_HOE), 50, category));
                items.add(new ShopItem("stone_hoe", "Houe en Pierre", new ItemStack(Material.STONE_HOE), 150, category));
                items.add(new ShopItem("iron_hoe", "Houe en Fer", new ItemStack(Material.IRON_HOE), 600, category));
                items.add(new ShopItem("diamond_hoe", "Houe en Diamant", new ItemStack(Material.DIAMOND_HOE), 2500, category));
                items.add(new ShopItem("netherite_hoe", "Houe en Netherite", new ItemStack(Material.NETHERITE_HOE), 12000, category));
                items.add(new ShopItem("shears", "Cisailles", new ItemStack(Material.SHEARS), 500, category));
                items.add(new ShopItem("bucket", "Seau Vide", new ItemStack(Material.BUCKET), 80, category));
                items.add(new ShopItem("water_bucket", "Seau d'Eau", new ItemStack(Material.WATER_BUCKET), 100, category));

                // ==================== FERTILISANTS ====================

                items.add(new ShopItem("bone_meal", "Poudre d'Os", new ItemStack(Material.BONE_MEAL), 25, category));
                items.add(new ShopItem("composter", "Composteur", new ItemStack(Material.COMPOSTER), 300, category));

                // ==================== BLOCS AGRICOLES ====================

                items.add(new ShopItem("farmland", "Terre Labourée", new ItemStack(Material.FARMLAND), 10, category));
                items.add(new ShopItem("dirt", "Terre", new ItemStack(Material.DIRT), 5, category));
                items.add(new ShopItem("grass_block", "Bloc d'Herbe", new ItemStack(Material.GRASS_BLOCK), 8, category));
                items.add(new ShopItem("podzol", "Podzol", new ItemStack(Material.PODZOL), 15, category));
                items.add(new ShopItem("coarse_dirt", "Terre Stérile", new ItemStack(Material.COARSE_DIRT), 7, category));
                items.add(new ShopItem("mycelium", "Mycélium", new ItemStack(Material.MYCELIUM), 50, category));

                // ==================== CHAMPIGNONS ====================

                items.add(new ShopItem("brown_mushroom", "Champignon Marron", new ItemStack(Material.BROWN_MUSHROOM), 30, category));
                items.add(new ShopItem("red_mushroom", "Champignon Rouge", new ItemStack(Material.RED_MUSHROOM), 30, category));
                items.add(new ShopItem("crimson_fungus", "Champignon Carmin", new ItemStack(Material.CRIMSON_FUNGUS), 80, category));
                items.add(new ShopItem("warped_fungus", "Champignon Biscornu", new ItemStack(Material.WARPED_FUNGUS), 80, category));

                // ==================== ÉLEVAGE ====================

                items.add(new ShopItem("wheat", "Blé (Nourriture Animaux)", new ItemStack(Material.WHEAT), 12, category));
                items.add(new ShopItem("carrot", "Carotte (Nourriture Cochons)", new ItemStack(Material.CARROT), 15, category));
                items.add(new ShopItem("potato", "Pomme de Terre (Nourriture Cochons)", new ItemStack(Material.POTATO), 12, category));
                items.add(new ShopItem("beetroot", "Betterave (Nourriture Cochons)", new ItemStack(Material.BEETROOT), 18, category));
                items.add(new ShopItem("kelp", "Varech (Nourriture Tortues)", new ItemStack(Material.KELP), 25, category));
                items.add(new ShopItem("seagrass", "Algue (Nourriture Tortues)", new ItemStack(Material.SEAGRASS), 20, category));
                items.add(new ShopItem("bamboo", "Bambou (Nourriture Pandas)", new ItemStack(Material.BAMBOO), 30, category));
                items.add(new ShopItem("lead", "Laisse", new ItemStack(Material.LEAD), 300, category));
                items.add(new ShopItem("name_tag", "Étiquette", new ItemStack(Material.NAME_TAG), 1000, category));
                items.add(new ShopItem("saddle", "Selle", new ItemStack(Material.SADDLE), 2000, category));

                // ==================== APICULTURE ====================

                items.add(new ShopItem("bee_nest", "Nid d'Abeilles", new ItemStack(Material.BEE_NEST), 1500, category));
                items.add(new ShopItem("beehive", "Ruche", new ItemStack(Material.BEEHIVE), 800, category));
                items.add(new ShopItem("honeycomb", "Rayon de Miel", new ItemStack(Material.HONEYCOMB), 100, category));
                items.add(new ShopItem("honey_bottle", "Fiole de Miel", new ItemStack(Material.HONEY_BOTTLE), 80, category));
                items.add(new ShopItem("honey_block", "Bloc de Miel", new ItemStack(Material.HONEY_BLOCK), 400, category));

                // ==================== FLEURS POUR ABEILLES ====================

                items.add(new ShopItem("dandelion", "Pissenlit", new ItemStack(Material.DANDELION), 20, category));
                items.add(new ShopItem("poppy", "Coquelicot", new ItemStack(Material.POPPY), 20, category));
                items.add(new ShopItem("blue_orchid", "Orchidée Bleue", new ItemStack(Material.BLUE_ORCHID), 35, category));
                items.add(new ShopItem("allium", "Allium", new ItemStack(Material.ALLIUM), 30, category));
                items.add(new ShopItem("azure_bluet", "Myosotis", new ItemStack(Material.AZURE_BLUET), 25, category));
                items.add(new ShopItem("red_tulip", "Tulipe Rouge", new ItemStack(Material.RED_TULIP), 25, category));
                items.add(new ShopItem("orange_tulip", "Tulipe Orange", new ItemStack(Material.ORANGE_TULIP), 25, category));
                items.add(new ShopItem("white_tulip", "Tulipe Blanche", new ItemStack(Material.WHITE_TULIP), 25, category));
                items.add(new ShopItem("pink_tulip", "Tulipe Rose", new ItemStack(Material.PINK_TULIP), 25, category));
                items.add(new ShopItem("oxeye_daisy", "Marguerite", new ItemStack(Material.OXEYE_DAISY), 22, category));
                items.add(new ShopItem("cornflower", "Bleuet", new ItemStack(Material.CORNFLOWER), 28, category));
                items.add(new ShopItem("lily_of_the_valley", "Muguet", new ItemStack(Material.LILY_OF_THE_VALLEY), 40, category));
                items.add(new ShopItem("sunflower", "Tournesol", new ItemStack(Material.SUNFLOWER), 50, category));
            }

            case MISC -> {
                // ==================== MATÉRIAUX DE CRAFT ====================

                items.add(new ShopItem("stick", "Bâton", new ItemStack(Material.STICK), 3, category));
                items.add(new ShopItem("coal", "Charbon", new ItemStack(Material.COAL), 20, category));
                items.add(new ShopItem("charcoal", "Charbon de Bois", new ItemStack(Material.CHARCOAL), 15, category));
                items.add(new ShopItem("iron_ingot", "Lingot de Fer", new ItemStack(Material.IRON_INGOT), 200, category));
                items.add(new ShopItem("gold_ingot", "Lingot d'Or", new ItemStack(Material.GOLD_INGOT), 500, category));
                items.add(new ShopItem("diamond", "Diamant", new ItemStack(Material.DIAMOND), 1500, category));
                items.add(new ShopItem("emerald", "Émeraude", new ItemStack(Material.EMERALD), 2000, category));
                items.add(new ShopItem("netherite_ingot", "Lingot de Netherite", new ItemStack(Material.NETHERITE_INGOT), 25000, category));
                items.add(new ShopItem("copper_ingot", "Lingot de Cuivre", new ItemStack(Material.COPPER_INGOT), 150, category));

                // Matériaux rares
                items.add(new ShopItem("quartz", "Quartz du Nether", new ItemStack(Material.QUARTZ), 100, category));
                items.add(new ShopItem("prismarine_shard", "Éclat de Prismarine", new ItemStack(Material.PRISMARINE_SHARD), 80, category));
                items.add(new ShopItem("prismarine_crystals", "Cristaux de Prismarine", new ItemStack(Material.PRISMARINE_CRYSTALS), 120, category));
                items.add(new ShopItem("nautilus_shell", "Coquillage Nautile", new ItemStack(Material.NAUTILUS_SHELL), 500, category));
                items.add(new ShopItem("heart_of_the_sea", "Cœur de la Mer", new ItemStack(Material.HEART_OF_THE_SEA), 5000, category));
                items.add(new ShopItem("nether_star", "Étoile du Nether", new ItemStack(Material.NETHER_STAR), 50000, category));
                items.add(new ShopItem("dragon_breath", "Souffle de Dragon", new ItemStack(Material.DRAGON_BREATH), 10000, category));
                items.add(new ShopItem("shulker_shell", "Carapace de Shulker", new ItemStack(Material.SHULKER_SHELL), 8000, category));
                items.add(new ShopItem("phantom_membrane", "Membrane de Phantom", new ItemStack(Material.PHANTOM_MEMBRANE), 2000, category));

                // ==================== CONTENANTS ====================

                items.add(new ShopItem("chest", "Coffre", new ItemStack(Material.CHEST), 100, category));
                items.add(new ShopItem("barrel", "Tonneau", new ItemStack(Material.BARREL), 120, category));
                items.add(new ShopItem("ender_chest", "Coffre de l'Ender", new ItemStack(Material.ENDER_CHEST), 5000, category));
                items.add(new ShopItem("bucket", "Seau Vide", new ItemStack(Material.BUCKET), 80, category));
                items.add(new ShopItem("water_bucket", "Seau d'Eau", new ItemStack(Material.WATER_BUCKET), 100, category));
                items.add(new ShopItem("lava_bucket", "Seau de Lave", new ItemStack(Material.LAVA_BUCKET), 500, category));
                items.add(new ShopItem("milk_bucket", "Seau de Lait", new ItemStack(Material.MILK_BUCKET), 150, category));
                items.add(new ShopItem("powder_snow_bucket", "Seau de Neige Poudreuse", new ItemStack(Material.POWDER_SNOW_BUCKET), 200, category));

                // ==================== MATÉRIAUX TEXTILES ====================

                items.add(new ShopItem("string", "Ficelle", new ItemStack(Material.STRING), 15, category));
                items.add(new ShopItem("leather", "Cuir", new ItemStack(Material.LEATHER), 40, category));
                items.add(new ShopItem("rabbit_hide", "Peau de Lapin", new ItemStack(Material.RABBIT_HIDE), 25, category));
                items.add(new ShopItem("feather", "Plume", new ItemStack(Material.FEATHER), 20, category));

                // ==================== PAPETERIE ====================

                items.add(new ShopItem("book", "Livre", new ItemStack(Material.BOOK), 50, category));
                items.add(new ShopItem("writable_book", "Livre et Plume", new ItemStack(Material.WRITABLE_BOOK), 100, category));
                items.add(new ShopItem("paper", "Papier", new ItemStack(Material.PAPER), 20, category));
                items.add(new ShopItem("map", "Carte Vierge", new ItemStack(Material.MAP), 200, category));
                items.add(new ShopItem("compass", "Boussole", new ItemStack(Material.COMPASS), 300, category));
                items.add(new ShopItem("clock", "Horloge", new ItemStack(Material.CLOCK), 400, category));

                // ==================== MATÉRIAUX DIVERS ====================

                items.add(new ShopItem("flint", "Silex", new ItemStack(Material.FLINT), 12, category));
                items.add(new ShopItem("gunpowder", "Poudre à Canon", new ItemStack(Material.GUNPOWDER), 80, category));
                items.add(new ShopItem("redstone", "Poudre de Redstone", new ItemStack(Material.REDSTONE), 25, category));
                items.add(new ShopItem("glowstone_dust", "Poudre de Pierre Lumineuse", new ItemStack(Material.GLOWSTONE_DUST), 30, category));
                items.add(new ShopItem("blaze_powder", "Poudre de Blaze", new ItemStack(Material.BLAZE_POWDER), 200, category));
                items.add(new ShopItem("ender_pearl", "Perle de l'Ender", new ItemStack(Material.ENDER_PEARL), 500, category));
                items.add(new ShopItem("slime_ball", "Boule de Slime", new ItemStack(Material.SLIME_BALL), 150, category));
                items.add(new ShopItem("magma_cream", "Crème de Magma", new ItemStack(Material.MAGMA_CREAM), 250, category));
                items.add(new ShopItem("ghast_tear", "Larme de Ghast", new ItemStack(Material.GHAST_TEAR), 800, category));
                items.add(new ShopItem("spider_eye", "Œil d'Araignée", new ItemStack(Material.SPIDER_EYE), 60, category));
                items.add(new ShopItem("fermented_spider_eye", "Œil d'Araignée Fermenté", new ItemStack(Material.FERMENTED_SPIDER_EYE), 120, category));
                items.add(new ShopItem("blaze_rod", "Baguette de Blaze", new ItemStack(Material.BLAZE_ROD), 500, category));

                // ==================== TEINTURES ====================

                items.add(new ShopItem("white_dye", "Teinture Blanche", new ItemStack(Material.WHITE_DYE), 20, category));
                items.add(new ShopItem("orange_dye", "Teinture Orange", new ItemStack(Material.ORANGE_DYE), 25, category));
                items.add(new ShopItem("magenta_dye", "Teinture Magenta", new ItemStack(Material.MAGENTA_DYE), 25, category));
                items.add(new ShopItem("light_blue_dye", "Teinture Bleu Clair", new ItemStack(Material.LIGHT_BLUE_DYE), 25, category));
                items.add(new ShopItem("yellow_dye", "Teinture Jaune", new ItemStack(Material.YELLOW_DYE), 25, category));
                items.add(new ShopItem("lime_dye", "Teinture Vert Clair", new ItemStack(Material.LIME_DYE), 25, category));
                items.add(new ShopItem("pink_dye", "Teinture Rose", new ItemStack(Material.PINK_DYE), 25, category));
                items.add(new ShopItem("gray_dye", "Teinture Grise", new ItemStack(Material.GRAY_DYE), 25, category));
                items.add(new ShopItem("light_gray_dye", "Teinture Gris Clair", new ItemStack(Material.LIGHT_GRAY_DYE), 25, category));
                items.add(new ShopItem("cyan_dye", "Teinture Cyan", new ItemStack(Material.CYAN_DYE), 25, category));
                items.add(new ShopItem("purple_dye", "Teinture Violette", new ItemStack(Material.PURPLE_DYE), 25, category));
                items.add(new ShopItem("blue_dye", "Teinture Bleue", new ItemStack(Material.BLUE_DYE), 25, category));
                items.add(new ShopItem("brown_dye", "Teinture Marron", new ItemStack(Material.BROWN_DYE), 25, category));
                items.add(new ShopItem("green_dye", "Teinture Verte", new ItemStack(Material.GREEN_DYE), 25, category));
                items.add(new ShopItem("red_dye", "Teinture Rouge", new ItemStack(Material.RED_DYE), 25, category));
                items.add(new ShopItem("black_dye", "Teinture Noire", new ItemStack(Material.BLACK_DYE), 25, category));
                items.add(new ShopItem("ink_sac", "Poche d'Encre", new ItemStack(Material.INK_SAC), 30, category));
                items.add(new ShopItem("glow_ink_sac", "Poche d'Encre Lumineuse", new ItemStack(Material.GLOW_INK_SAC), 80, category));
                items.add(new ShopItem("lapis_lazuli", "Lapis-lazuli", new ItemStack(Material.LAPIS_LAZULI), 50, category));
                items.add(new ShopItem("bone_meal", "Poudre d'Os", new ItemStack(Material.BONE_MEAL), 25, category));
                items.add(new ShopItem("cocoa_beans", "Fèves de Cacao", new ItemStack(Material.COCOA_BEANS), 30, category));

                // ==================== MATÉRIAUX D'OS ====================

                items.add(new ShopItem("bone", "Os", new ItemStack(Material.BONE), 25, category));
                items.add(new ShopItem("bone_block", "Bloc d'Os", new ItemStack(Material.BONE_BLOCK), 200, category));

                // ==================== OBJETS RARES ET SPÉCIAUX ====================

                items.add(new ShopItem("totem_of_undying", "Totem d'Immortalité", new ItemStack(Material.TOTEM_OF_UNDYING), 25000, category));
                items.add(new ShopItem("elytra", "Élytres", new ItemStack(Material.ELYTRA), 50000, category));
                items.add(new ShopItem("dragon_egg", "Œuf de Dragon", new ItemStack(Material.DRAGON_EGG), 100000, category));
                items.add(new ShopItem("beacon", "Balise", new ItemStack(Material.BEACON), 75000, category));
                items.add(new ShopItem("conduit", "Conduit", new ItemStack(Material.CONDUIT), 15000, category));

                // ==================== SPAWN EGGS (Prix élevés) ====================

                items.add(new ShopItem("cow_spawn_egg", "Œuf de Vache", new ItemStack(Material.COW_SPAWN_EGG), 2000, category));
                items.add(new ShopItem("pig_spawn_egg", "Œuf de Cochon", new ItemStack(Material.PIG_SPAWN_EGG), 1800, category));
                items.add(new ShopItem("chicken_spawn_egg", "Œuf de Poule", new ItemStack(Material.CHICKEN_SPAWN_EGG), 1500, category));
                items.add(new ShopItem("sheep_spawn_egg", "Œuf de Mouton", new ItemStack(Material.SHEEP_SPAWN_EGG), 1700, category));
                items.add(new ShopItem("horse_spawn_egg", "Œuf de Cheval", new ItemStack(Material.HORSE_SPAWN_EGG), 5000, category));
                items.add(new ShopItem("wolf_spawn_egg", "Œuf de Loup", new ItemStack(Material.WOLF_SPAWN_EGG), 3000, category));
                items.add(new ShopItem("cat_spawn_egg", "Œuf de Chat", new ItemStack(Material.CAT_SPAWN_EGG), 2500, category));
                items.add(new ShopItem("villager_spawn_egg", "Œuf de Villageois", new ItemStack(Material.VILLAGER_SPAWN_EGG), 10000, category));
            }
        }

        return items;
    }

    /**
     * Récupère un ShopItem par son ID
     */
    private ShopItem getShopItemById(String id) {
        for (ShopCategory category : ShopCategory.values()) {
            for (ShopItem item : getItemsForCategory(category)) {
                if (item.getId().equals(id)) {
                    return item;
                }
            }
        }
        return null;
    }

    // ==================== CRÉATION D'ITEMS ENCHANTÉS ====================

    private ItemStack createEnchantedArmor(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Enchantements max vanilla pour armures
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createEnchantedWeapon(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Enchantements max vanilla pour épées
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);

        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createEnchantedBow() {
        ItemStack item = new ItemStack(Material.BOW);
        ItemMeta meta = item.getItemMeta();

        // Enchantements max vanilla pour arc
        meta.addEnchant(Enchantment.POWER, 5, true);
        meta.addEnchant(Enchantment.PUNCH, 2, true);
        meta.addEnchant(Enchantment.FLAME, 1, true);
        meta.addEnchant(Enchantment.INFINITY, 1, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);

        item.setItemMeta(meta);

        return item;
    }

    // ==================== CLASSES INTERNES ====================

    /**
     * Énumération des catégories du shop
     */
    public enum ShopCategory {
        PVP("PvP & Combat", "Équipements et consommables pour le combat", Material.DIAMOND_SWORD),
        BLOCKS("Blocs", "Blocs de construction et matériaux", Material.COBBLESTONE),
        FOOD("Nourriture", "Aliments pour restaurer votre faim", Material.BREAD),
        TOOLS("Outils", "Outils pour faciliter vos tâches", Material.DIAMOND_PICKAXE),
        REDSTONE("Redstone", "Composants et mécanismes automatisés", Material.REDSTONE),
        DECORATION("Décoration", "Éléments décoratifs et esthétiques", Material.PAINTING),
        FARMING("Agriculture", "Graines, outils et matériel d'élevage", Material.WHEAT_SEEDS),
        MISC("Divers", "Articles utiles de toutes sortes", Material.CHEST);

        private final String displayName;
        private final String description;
        private final Material icon;

        ShopCategory(String displayName, String description, Material icon) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Material getIcon() { return icon; }
    }

    /**
     * Classe représentant un article du shop
     */
    public static class ShopItem {
        private final String id;
        private final String displayName;
        private final ItemStack baseItem;
        private final long price;
        private final ShopCategory category;

        public ShopItem(String id, String displayName, ItemStack baseItem, long price, ShopCategory category) {
            this.id = id;
            this.displayName = displayName;
            this.baseItem = baseItem.clone();
            this.price = price;
            this.category = category;
        }

        public String getId() { return id; }
        public String getDisplayName() { return displayName; }
        public long getPrice() { return price; }
        public ShopCategory getCategory() { return category; }

        public ItemStack createItemStack(int quantity) {
            ItemStack item = baseItem.clone();
            item.setAmount(Math.min(quantity, item.getMaxStackSize()));
            return item;
        }
    }

    // ==================== MÉTHODES DE CRÉATION D'ITEMS ENCHANTÉS ====================

    /**
     * Crée une arbalète enchantée
     */
    private ItemStack createEnchantedCrossbow() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        ItemMeta meta = item.getItemMeta();

        meta.addEnchant(Enchantment.QUICK_CHARGE, 3, true);
        meta.addEnchant(Enchantment.MULTISHOT, 1, true);
        meta.addEnchant(Enchantment.PIERCING, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);

        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un trident enchanté
     */
    private ItemStack createEnchantedTrident() {
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();

        meta.addEnchant(Enchantment.IMPALING, 5, true);
        meta.addEnchant(Enchantment.LOYALTY, 3, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addEnchant(Enchantment.CHANNELING, 1, true);

        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée des élytres enchantées
     */
    private ItemStack createEnchantedElytra() {
        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta = item.getItemMeta();

        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);

        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée une flèche à effet spécial
     */
    private ItemStack createTippedArrow(String effectType) {
        ItemStack item = new ItemStack(Material.TIPPED_ARROW);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        PotionType type = switch (effectType.toLowerCase()) {
            case "poison" -> PotionType.POISON;
            case "slowness" -> PotionType.SLOWNESS;
            case "weakness" -> PotionType.WEAKNESS;
            default -> PotionType.POISON;
        };
        meta.setBasePotionType(type);
        guiManager.applyName(meta, "§fFlèche à effet: §e" + capitalize(effectType));
        guiManager.applyLore(meta, List.of("§7Flèche imprégnée d'effet de §e" + capitalize(effectType)));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée une potion avec effet spécifique
     */
    private ItemStack createPotion(String effectType, int level, int duration) {
        ItemStack item = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        PotionType type = mapPotionType(effectType);
        if (type != null) {
            meta.setBasePotionType(type);
        }
        // Note: on utilise ici uniquement le type de potion de base pour un rendu correct en GUI
        guiManager.applyName(meta, "§fPotion: §e" + capitalize(effectType));
        guiManager.applyLore(meta, Arrays.asList("§7Effet: §e" + capitalize(effectType), duration > 0 ? "§7Durée: §e" + formatTime(duration) : "§7Durée: §e3:00"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée une potion splash avec effet spécifique
     */
    private ItemStack createSplashPotion(String effectType, int level, int duration) {
        ItemStack item = new ItemStack(Material.SPLASH_POTION);
        PotionMeta meta = (PotionMeta) item.getItemMeta();
        PotionType type = mapPotionType(effectType);
        if (type != null) {
            meta.setBasePotionType(type);
        }
        // Note: on utilise ici uniquement le type de potion de base pour un rendu correct en GUI
        guiManager.applyName(meta, "§fPotion jetable: §e" + capitalize(effectType));
        guiManager.applyLore(meta, Arrays.asList("§7Effet: §e" + capitalize(effectType), duration > 0 ? "§7Durée: §e" + formatTime(duration) : "§7Durée: §e1:30"));
        item.setItemMeta(meta);
        return item;
    }

    private PotionType mapPotionType(String effectType) {
        return switch (effectType.toLowerCase()) {
            case "strength" -> PotionType.STRENGTH;
            case "speed" -> PotionType.SWIFTNESS;
            case "fire_resistance" -> PotionType.FIRE_RESISTANCE;
            case "invisibility" -> PotionType.INVISIBILITY;
            case "night_vision" -> PotionType.NIGHT_VISION;
            case "instant_health" -> PotionType.HEALING;
            case "regeneration" -> PotionType.REGENERATION;
            case "poison" -> PotionType.POISON;
            case "slowness" -> PotionType.SLOWNESS;
            case "weakness" -> PotionType.WEAKNESS;
            case "instant_damage" -> PotionType.HARMING;
            default -> null;
        };
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] parts = text.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /**
     * Formate le temps en minutes:secondes
     */
    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
}