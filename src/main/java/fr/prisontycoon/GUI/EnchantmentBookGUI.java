package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Interface graphique pour les livres d'enchantement uniques
 */
public class EnchantmentBookGUI {

    private final PrisonTycoon plugin;

    // Slots pour les 7 livres principaux (centre)
    private static final int[] MAIN_BOOK_SLOTS = {11, 12, 13, 14, 15, 20, 21};
    // Slots pour les 3 livres supplémentaires (dessous)
    private static final int[] EXTRA_BOOK_SLOTS = {22, 23, 24};
    private static final int SUMMARY_SLOT = 4;

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

        // Nom avec statut amélioré
        String status;
        if (isActive) {
            status = "§a✅ ACTIF";
        } else if (level > 0) {
            status = "§c⭕ INACTIF";
        } else {
            status = "§7❌ NON POSSÉDÉ";
        }

        meta.setDisplayName("§e" + book.getName() + " §7(" + status + "§7)");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + book.getDescription());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        if (level > 0) {
            lore.add("§aNiveau actuel: §b" + level + "§7/§b" + book.getMaxLevel());
            lore.add("");

            // NOUVEAU : Affichage des conditions d'activation
            String errorMsg = checkActivationError(player, book.getId());
            if (errorMsg != null && !isActive) {
                lore.add("§c⚠️ " + errorMsg);
                lore.add("");
            } else {
                lore.add("§e⚡ Actions:");
                lore.add("§7▸ §6Shift+Clic §7pour " + (isActive ? "§cdésactiver" : "§aactiver"));

                if (!isActive && errorMsg == null) {
                    Set<String> activeEnchants = plugin.getEnchantmentBookManager().getActiveEnchantments(player);
                    int cost = calculateActivationCost(activeEnchants.size());
                    lore.add("§7▸ Coût activation: §b" + cost + " XP");
                }
            }
        } else {
            lore.add("§cVous ne possédez pas ce livre!");
            lore.add("");
            lore.add("§7▸ Achetez-le dans la boutique");
            lore.add("§7  pour pouvoir l'utiliser");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU : Vérifie les conditions d'activation d'un enchantement
     */
    private String checkActivationError(Player player, String bookId) {
        if (!plugin.getEnchantmentBookManager().hasEnchantmentBook(player, bookId)) {
            return "§cVous ne possédez pas ce livre!";
        }

        Set<String> activeEnchants = plugin.getEnchantmentBookManager().getActiveEnchantments(player);
        boolean isActive = activeEnchants.contains(bookId);

        if (!isActive) {
            if (activeEnchants.size() >= 4) {
                return "§cMaximum 4 enchantements actifs!";
            }

            int xpCost = calculateActivationCost(activeEnchants.size());
            if (player.getTotalExperience() < xpCost) {
                return "§cPas assez d'XP! (" + xpCost + " requis)";
            }
        }

        return null; // Pas d'erreur
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

        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§5⚡ §lRÉSUMÉ DES ENCHANTS ACTIFS");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Affiche la liste détaillée de tous");
        lore.add("§7vos enchantements uniques actifs");
        lore.add("§7avec leurs niveaux et descriptions.");
        lore.add("");
        lore.add("§e➤ Cliquez pour voir le résumé!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        gui.setItem(SUMMARY_SLOT, item);

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

        List<EnchantmentBookManager.EnchantmentBook> allBooks = new ArrayList<>(plugin.getEnchantmentBookManager().getAllEnchantmentBooks());

        // Slots pour les livres (3 lignes centrales)
        int[] bookSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

        for (int i = 0; i < Math.min(allBooks.size(), bookSlots.length); i++) {
            EnchantmentBookManager.EnchantmentBook book = allBooks.get(i);
            ItemStack bookItem = createShopBookItem(player, book);
            gui.setItem(bookSlots[i], bookItem);
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
     * MODIFIÉ : Crée l'item pour la boutique avec nouvelles instructions
     */
    private ItemStack createShopBookItem(Player player, EnchantmentBookManager.EnchantmentBook book) {
        ItemStack item = new ItemStack(book.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        int currentLevel = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, book.getId());
        long cost = book.getCost();

        meta.setDisplayName("§e" + book.getName());

        // NOUVEAU : Ajout de l'ID du livre dans les métadonnées pour identification
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "shop_book_id"),
                PersistentDataType.STRING,
                book.getId()
        );

        List<String> lore = new ArrayList<>();
        lore.add("§7" + book.getDescription());
        lore.add("");
        lore.add("§bNiveau max: §e" + book.getMaxLevel());
        lore.add("§bNiveau actuel: §e" + currentLevel);
        lore.add("");

        if (cost > 0) {
            lore.add("§aCoût " + ": §6" + NumberFormatter.format(cost) + " beacons");
            lore.add("");
            lore.add("§e⚡ Achat intelligent:");

            // Vérifier si l'inventaire a de la place
            if (player.getInventory().firstEmpty() != -1) {
                lore.add("§7▸ §aLivre physique §7(inventaire libre)");
                lore.add("§7  Peut être appliqué plus tard");
            } else {
                lore.add("§7▸ §6Achat direct §7(inventaire plein)");
                lore.add("§7  Appliqué immédiatement");
            }
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
     * MODIFIÉ : Gère les clics dans la boutique avec choix livre physique/direct
     */
    public void handleBookShopClick(Player player, int slot, ItemStack clickedItem) {
        // Bouton retour
        if (slot == 49) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openEnchantmentBookMenu(player);
            return;
        }

        // NOUVEAU : Récupération de l'ID du livre depuis les métadonnées de l'item
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();

        String bookId = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "shop_book_id"), PersistentDataType.STRING);
        EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(bookId);
        if (book == null) return;

        // Vérifications préliminaires
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long cost = book.getCost();


        if (playerData.getBeacons() < cost) {
            player.sendMessage("§cPas assez de beacons! (" + NumberFormatter.format(cost) + " requis)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
            return;
        }

        if (player.getInventory().firstEmpty() != -1) {
            boolean success = plugin.getEnchantmentBookManager().purchasePhysicalEnchantmentBook(player, bookId);
            if (success) {
                openBookShop(player); // Refresh
            }
        }
    }



    /**
     * MODIFIÉ : Gère les clics dans le menu des livres d'enchantement + détection livres physiques
     */
    public void handleEnchantmentBookMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        // NOUVEAU : Vérification si le joueur clique avec un livre physique dans la main
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() == Material.ENCHANTED_BOOK &&
                cursor.hasItemMeta() && cursor.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "enchant_book_id"), PersistentDataType.STRING)) {

            handlePhysicalBookApplication(player, cursor);
            return;
        }

        // Bouton retour
        if (slot == BACK_BUTTON_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            return;
        }

        // NOUVEAU : Bouton résumé des enchants actifs
        if (slot == SUMMARY_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            // Affiche un message détaillé des enchants actifs
            displayActiveEnchantsSummary(player);
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
                // Activation/désactivation avec gestion d'erreur visuelle
                if (plugin.getEnchantmentBookManager().hasEnchantmentBook(player, bookId)) {
                    boolean success = plugin.getEnchantmentBookManager().toggleEnchantment(player, bookId);
                    if (!success) {
                        // NOUVEAU : Affichage d'erreur visuelle
                        showErrorFeedback(player, slot, "§cPas assez d'XP ou limite atteinte!");
                        return;
                    }
                    openEnchantmentBookMenu(player); // Refresh GUI
                } else {
                    showErrorFeedback(player, slot, "§cVous ne possédez pas ce livre!");
                }
            }
        }
    }

    /**
     * NOUVEAU : Applique un livre physique à la pioche
     */
    public void handlePhysicalBookApplication(Player player, ItemStack physicalBook) {
        String bookId = physicalBook.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "enchant_book_id"), PersistentDataType.STRING);

        if (bookId == null) return;

        // Vérifier que le joueur a une pioche légendaire
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§cVous devez avoir votre pioche légendaire pour appliquer ce livre!");
            return;
        }

        EnchantmentBookManager enchantmentManager = plugin.getEnchantmentBookManager();

        int playerLevel = enchantmentManager.getEnchantmentBookLevel(player, bookId);

        EnchantmentBookManager.EnchantmentBook book = enchantmentManager.getEnchantmentBook(bookId);

        int maxLevel = book.getMaxLevel();

        if (playerLevel == maxLevel)
            return;

        // Appliquer le livre
        plugin.getEnchantmentBookManager().addEnchantmentBook(player, bookId);

        // Enregistrer sur la pioche
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        playerData.addPickaxeEnchantmentBook(bookId);
        // Retirer le livre physique

        physicalBook.setAmount(physicalBook.getAmount() - 1);

        // Mettre à jour le lore de la pioche
        plugin.getPickaxeManager().updatePickaxeLore(pickaxe.getItemMeta(), player);

        player.sendMessage("§a✅ Livre §e" + book.getName() + " §aappliqué à votre pioche!");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        // Refresh GUI
        openEnchantmentBookMenu(player);
    }

    /**
     * NOUVEAU : Affiche le résumé des enchants actifs
     */
    private void displayActiveEnchantsSummary(Player player) {
        Set<String> activeBooks = plugin.getEnchantmentBookManager().getActiveEnchantments(player);

        if (activeBooks.isEmpty()) {
            player.sendMessage("§e⚡ Aucun enchantement unique actif sur votre pioche.");
            return;
        }

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§5⚡ §lENCHANTEMENTS UNIQUES ACTIFS §7(" + activeBooks.size() + "/4)");

        for (String bookId : activeBooks) {
            EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(bookId);
            if (book != null) {
                int level = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, bookId);
                player.sendMessage("§7▸ §d" + book.getName() + " §7(Niveau " + level + ") - §e" + book.getDescription());
            }
        }

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * AMÉLIORÉ : Affiche un feedback d'erreur visuel temporaire avec plus d'options
     */
    private void showErrorFeedback(Player player, int slot, String errorMessage) {
        Inventory gui = player.getOpenInventory().getTopInventory();
        ItemStack originalItem = gui.getItem(slot);

        // Créer l'item d'erreur avec animation
        ItemStack errorItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = errorItem.getItemMeta();
        meta.setDisplayName("§c⚠️ §lERREUR");

        List<String> errorLore = new ArrayList<>();
        errorLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        errorLore.add(errorMessage);
        errorLore.add("");
        errorLore.add("§7L'action n'a pas pu être effectuée.");
        errorLore.add("§7Vérifiez vos ressources et réessayez.");
        errorLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(errorLore);
        errorItem.setItemMeta(meta);

        // Animation d'erreur
        gui.setItem(slot, errorItem);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);

        // Effet de particules d'erreur au joueur
        player.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);

        // Restaurer l'item original après 1.5 secondes
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().equals(gui) &&
                    gui.getItem(slot) != null && gui.getItem(slot).getType() == Material.RED_CONCRETE) {
                gui.setItem(slot, originalItem);
            }
        }, 30L); // 1.5 secondes
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
     * NOUVEAU : Calcule le coût d'activation (copie de la méthode privée)
     */
    private int calculateActivationCost(int currentActiveCount) {
        return 100 * (int) Math.pow(2, currentActiveCount); // 100, 200, 400, 800
    }
}