package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface graphique pour les livres d'enchantement uniques
 */
public class EnchantmentBookGUI {

    private final PrisonTycoon plugin;

    // Slots pour les 7 livres principaux (centre)
    private static final int[] MAIN_BOOK_SLOTS = {11, 12, 13, 14, 15, 20, 21};
    // Slots pour les 3 livres supplémentaires (dessous)
    private static final int[] EXTRA_BOOK_SLOTS = {22, 23, 24};

    // Slots des boutons de contrôle
    private static final int BACK_BUTTON_SLOT = 27;
    private static final int INFO_SLOT = 31;
    private static final int SHOP_SLOT = 35;

    public EnchantmentBookGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu principal des livres d'enchantement
     */
    public void openEnchantmentBookMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, "§5⚡ §lEnchantements Uniques §5⚡");

        // Remplissage décoratif
        fillWithGlass(gui);

        // Affichage des livres d'enchantement
        displayEnchantmentBooks(gui, player);

        // Boutons de contrôle
        setupControlButtons(gui);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    /**
     * Affiche tous les livres d'enchantement
     */
    private void displayEnchantmentBooks(Inventory gui, Player player) {
        List<EnchantmentBookManager.EnchantmentBook> allBooks = new ArrayList<>(plugin.getEnchantmentBookManager().getAllEnchantmentBooks());

        // Affichage des 7 livres principaux
        for (int i = 0; i < Math.min(7, allBooks.size()); i++) {
            EnchantmentBookManager.EnchantmentBook book = allBooks.get(i);
            ItemStack bookItem = createBookItem(player, book);
            gui.setItem(MAIN_BOOK_SLOTS[i], bookItem);
        }

        // Affichage des 3 livres supplémentaires
        for (int i = 7; i < Math.min(10, allBooks.size()); i++) {
            EnchantmentBookManager.EnchantmentBook book = allBooks.get(i);
            ItemStack bookItem = createBookItem(player, book);
            gui.setItem(EXTRA_BOOK_SLOTS[i - 7], bookItem);
        }
    }

    /**
     * Crée l'item représentant un livre d'enchantement
     */
    private ItemStack createBookItem(Player player, EnchantmentBookManager.EnchantmentBook book) {
        ItemStack item = new ItemStack(book.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        int level = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, book.getId());
        boolean isActive = plugin.getEnchantmentBookManager().isEnchantmentActive(player, book.getId());

        // Nom avec statut
        String status = isActive ? "§a✅ ACTIF" : (level > 0 ? "§c⭕ INACTIF" : "§7❌ NON POSSÉDÉ");
        meta.setDisplayName("§e" + book.getName() + " §7(" + status + "§7)");

        List<String> lore = new ArrayList<>();
        lore.add("§7" + book.getDescription());
        lore.add("");

        if (level > 0) {
            lore.add("§aNiveau actuel: §b" + level + "§7/§b" + book.getMaxLevel());
            lore.add("");
            lore.add("§e⚡ Actions:");
            lore.add("§7▸ §6Shift+Clic §7pour " + (isActive ? "§cdésactiver" : "§aactiver"));

            if (!isActive) {
                int activeCount = plugin.getEnchantmentBookManager().getActiveEnchantments(player).size();
                if (activeCount >= 4) {
                    lore.add("§c▸ Maximum 4 enchantements actifs!");
                } else {
                    int cost = calculateActivationCost(activeCount);
                    lore.add("§7▸ Coût activation: §b" + cost + " XP");
                }
            }
        } else {
            lore.add("§cVous ne possédez pas ce livre!");
            lore.add("§7Achetez-le dans la boutique");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Configure les boutons de contrôle
     */
    private void setupControlButtons(Inventory gui) {
        // Bouton retour
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c⬅ §lRetour");
        backMeta.setLore(List.of("§7Retour au menu principal"));
        backButton.setItemMeta(backMeta);
        gui.setItem(BACK_BUTTON_SLOT, backButton);

        // Item d'information
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§b📖 §lInformations");
        infoMeta.setLore(List.of(
                "§7§lSystème d'Enchantements Uniques",
                "",
                "§e⚡ Fonctionnement:",
                "§7▸ Achetez des livres avec des beacons",
                "§7▸ Ajoutez les livres pour augmenter le niveau",
                "§7▸ Activez/désactivez avec Shift+Clic",
                "§7▸ Maximum 4 enchantements actifs",
                "§7▸ Coût d'activation en XP",
                "",
                "§c⚠ §7Ajouter un livre désactive l'enchant!"
        ));
        infoItem.setItemMeta(infoMeta);
        gui.setItem(INFO_SLOT, infoItem);

        // Bouton boutique
        ItemStack shopButton = new ItemStack(Material.EMERALD);
        ItemMeta shopMeta = shopButton.getItemMeta();
        shopMeta.setDisplayName("§a💰 §lBoutique de Livres");
        shopMeta.setLore(List.of(
                "§7Achetez des livres d'enchantement",
                "§7avec vos beacons!"
        ));
        shopButton.setItemMeta(shopMeta);
        gui.setItem(SHOP_SLOT, shopButton);
    }

    /**
     * Remplit les slots vides avec du verre coloré
     */
    private void fillWithGlass(Inventory gui) {
        ItemStack glass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
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
     * Ouvre la boutique des livres d'enchantement
     */
    public void openBookShop(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§a💰 §lBoutique de Livres §a💰");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Information du joueur
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta infoMeta = playerInfo.getItemMeta();
        infoMeta.setDisplayName("§6💰 Vos Beacons");
        infoMeta.setLore(List.of(
                "§7Beacons disponibles:",
                "§e" + NumberFormatter.format(playerData.getBeacons()) + " beacons"
        ));
        playerInfo.setItemMeta(infoMeta);
        gui.setItem(4, playerInfo);

        // Affichage de tous les livres disponibles à l'achat
        int slot = 10;
        for (EnchantmentBookManager.EnchantmentBook book : plugin.getEnchantmentBookManager().getAllEnchantmentBooks()) {
            if (slot >= 44) break; // Limite de l'interface

            ItemStack bookItem = createShopBookItem(player, book);
            gui.setItem(slot, bookItem);

            slot++;
            if (slot % 9 == 8) slot += 2; // Saut de ligne avec marge
        }

        // Bouton retour
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName("§c⬅ §lRetour");
        backButton.setItemMeta(backMeta);
        gui.setItem(49, backButton);

        // Remplissage décoratif
        ItemStack glass = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.5f);
    }

    /**
     * Crée l'item pour la boutique
     */
    private ItemStack createShopBookItem(Player player, EnchantmentBookManager.EnchantmentBook book) {
        ItemStack item = new ItemStack(book.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        int currentLevel = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, book.getId());
        long cost = book.getCostForLevel(currentLevel + 1);

        meta.setDisplayName("§e" + book.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§7" + book.getDescription());
        lore.add("");
        lore.add("§bNiveau max: §e" + book.getMaxLevel());
        lore.add("§bNiveau actuel: §e" + currentLevel);
        lore.add("");

        if (cost > 0) {
            lore.add("§aCoût niveau " + (currentLevel + 1) + ": §6" + NumberFormatter.format(cost) + " beacons");
            lore.add("");
            lore.add("§e➤ Cliquez pour acheter!");
        } else {
            lore.add("§c✘ Niveau maximum atteint!");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Gère les clics dans le menu des livres d'enchantement
     */
    public void handleEnchantmentBookMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        // Bouton retour
        if (slot == BACK_BUTTON_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            return;
        }

        // Bouton boutique
        if (slot == SHOP_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openBookShop(player);
            return;
        }

        // Gestion des livres d'enchantement
        String bookId = getBookIdFromSlot(slot);
        if (bookId != null) {
            if (clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT) {
                // Activation/désactivation
                if (plugin.getEnchantmentBookManager().hasEnchantmentBook(player, bookId)) {
                    plugin.getEnchantmentBookManager().toggleEnchantment(player, bookId);
                    openEnchantmentBookMenu(player); // Refresh GUI
                } else {
                    player.sendMessage("§cVous ne possédez pas ce livre d'enchantement!");
                }
            } else {
                // Affichage des informations détaillées
                showBookDetails(player, bookId);
            }
        }
    }

    /**
     * Gère les clics dans la boutique
     */
    public void handleBookShopClick(Player player, int slot, ItemStack clickedItem) {
        // Bouton retour
        if (slot == 49) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openEnchantmentBookMenu(player);
            return;
        }

        // Achat de livre
        if (clickedItem != null && clickedItem.hasItemMeta()) {
            String displayName = clickedItem.getItemMeta().getDisplayName();
            for (EnchantmentBookManager.EnchantmentBook book : plugin.getEnchantmentBookManager().getAllEnchantmentBooks()) {
                if (displayName.contains(book.getName())) {
                    boolean success = plugin.getEnchantmentBookManager().purchaseEnchantmentBook(player, book.getId());
                    if (success) {
                        openBookShop(player); // Refresh
                    }
                    return;
                }
            }
        }
    }

    /**
     * Obtient l'ID du livre en fonction du slot
     */
    private String getBookIdFromSlot(int slot) {
        List<EnchantmentBookManager.EnchantmentBook> allBooks = new ArrayList<>(plugin.getEnchantmentBookManager().getAllEnchantmentBooks());

        // Vérification des slots principaux
        for (int i = 0; i < MAIN_BOOK_SLOTS.length && i < allBooks.size(); i++) {
            if (MAIN_BOOK_SLOTS[i] == slot) {
                return allBooks.get(i).getId();
            }
        }

        // Vérification des slots supplémentaires
        for (int i = 0; i < EXTRA_BOOK_SLOTS.length && (i + 7) < allBooks.size(); i++) {
            if (EXTRA_BOOK_SLOTS[i] == slot) {
                return allBooks.get(i + 7).getId();
            }
        }

        return null;
    }

    /**
     * Affiche les détails d'un livre
     */
    private void showBookDetails(Player player, String bookId) {
        EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(bookId);
        if (book == null) return;

        int level = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, bookId);
        boolean isActive = plugin.getEnchantmentBookManager().isEnchantmentActive(player, bookId);

        player.sendMessage("§e§l=== " + book.getName() + " ===");
        player.sendMessage("§7" + book.getDescription());
        player.sendMessage("§bNiveau: §e" + level + "§7/§e" + book.getMaxLevel());
        player.sendMessage("§bStatut: " + (isActive ? "§a✅ Actif" : "§c⭕ Inactif"));

        if (level > 0) {
            player.sendMessage("§7§lUtilisez Shift+Clic pour " + (isActive ? "désactiver" : "activer"));
        }
    }

    /**
     * Calcule le coût d'activation
     */
    private int calculateActivationCost(int currentActiveCount) {
        return 100 * (int) Math.pow(2, currentActiveCount);
    }
}