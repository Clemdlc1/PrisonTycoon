package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Interface graphique pour les livres d'enchantement uniques
 */
public class EnchantmentBookGUI {

    private static final int[] MAIN_BOOK_SLOTS = {11, 12, 13, 14, 15};
    private static final int[] EXTRA_BOOK_SLOTS = {20, 21, 22, 23, 24};
    private static final int SUMMARY_SLOT = 4;
    // Slots des boutons de contrôle
    private static final int INFO_SLOT = 31;
    private static final int SHOP_SLOT = 35;
    private final PrisonTycoon plugin;

    public EnchantmentBookGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu principal des livres d'enchantement
     */
    public void openEnchantmentBookMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(36, "§5⚡ §lEnchantements Uniques §5⚡");

        plugin.getGUIManager().fillBorders(gui);
        displayEnchantmentBooks(gui, player);
        setupControlButtons(gui);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.ENCHANTMENT_BOOK, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    /**
     * Affiche tous les livres d'enchantement
     */
    private void displayEnchantmentBooks(Inventory gui, Player player) {
        List<EnchantmentBookManager.EnchantmentBook> allBooks = new ArrayList<>(plugin.getEnchantmentBookManager().getAllEnchantmentBooks());

        for (int i = 0; i < Math.min(5, allBooks.size()); i++) {
            EnchantmentBookManager.EnchantmentBook book = allBooks.get(i);
            ItemStack bookItem = createBookItem(player, book);
            gui.setItem(MAIN_BOOK_SLOTS[i], bookItem);
        }

        for (int i = 5; i < Math.min(10, allBooks.size()); i++) {
            EnchantmentBookManager.EnchantmentBook book = allBooks.get(i);
            ItemStack bookItem = createBookItem(player, book);
            gui.setItem(EXTRA_BOOK_SLOTS[i - 5], bookItem);
        }
    }

    /**
     * AMÉLIORÉ : Crée l'item représentant un livre d'enchantement avec lore détaillé
     */
    private ItemStack createBookItem(Player player, EnchantmentBookManager.EnchantmentBook book) {
        boolean owned = plugin.getEnchantmentBookManager().hasEnchantmentBook(player, book.getId());
        int level = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, book.getId());
        boolean isActive = plugin.getEnchantmentBookManager().isEnchantmentActive(player, book.getId());

        // Matériau selon l'état
        Material material = owned ? (isActive ? book.getDisplayMaterial() : Material.ENCHANTED_BOOK) : Material.BOOK;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Titre avec état - UNIFORMISÉ
        String statusIcon = owned ? (isActive ? "§a✅" : "§c⭕") : "§8❌";
        plugin.getGUIManager().applyName(meta, statusIcon + " §5⚡ §l" + book.getName()); // UNIFORMISÉ avec ⚡

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // NOUVEAU : Ajout de la compatibilité pour clarté
        switch (book.getId()) {
            case "tonnerre":
                lore.add("§5⚡ §lCompatible: §7Pioches et Épées");
                break;
            case "incassable":
                lore.add("§5⚡ §lCompatible: §7Pioches, Épées et Armures");
                break;
            default:
                lore.add("§5⛏ §lCompatible: §7Pioches uniquement");
                break;
        }
        lore.add("");

        // Description avec emojis - UNIFORMISÉ
        lore.add("§6📖 §lDescription:"); // UNIFORMISÉ
        lore.add("§7▸ " + book.getDescription()); // UNIFORMISÉ avec ▸
        lore.add("");

        // NOUVEAU : Ajout du pouvoir pour uniformité
        lore.add("§e⚡ Pouvoir: §d" + getPickaxePowerDescription(book.getId()));
        lore.add("");

        // Informations détaillées - UNIFORMISÉ
        lore.add("§e📊 §lInformations:"); // UNIFORMISÉ
        lore.add("§7▸ Niveau max: §e" + book.getMaxLevel());

        if (owned) {
            lore.add("§7▸ Votre niveau: §a" + level + "§7/§e" + book.getMaxLevel());
            lore.add("§7▸ Statut: " + (isActive ? "§a✅ Actif" : "§c⭕ Inactif"));
        } else {
            lore.add("§7▸ Statut: §c❌ Non possédé");
        }
        lore.add("");

        // Actions possibles - UNIFORMISÉ
        lore.add("§a🎯 §lActions:"); // UNIFORMISÉ
        if (owned) {
            if (isActive) {
                lore.add("§7▸ §c⇧ + Clic §7pour désactiver");
                lore.add("§7  §7(Gratuit)");
            } else {
                Set<String> activeEnchants = plugin.getEnchantmentBookManager().getActiveEnchantments(player);
                if (activeEnchants.size() >= 4) {
                    lore.add("§7▸ §c❌ Limite atteinte (4 max)");
                } else {
                    int xpCost = calculateActivationCost(activeEnchants.size());
                    if (player.getTotalExperience() >= xpCost) {
                        lore.add("§7▸ §a⇧ + Clic §7pour activer");
                        lore.add("§7  §b(" + xpCost + " XP requis)");
                    } else {
                        lore.add("§7▸ §c⇧ + Clic §7pour activer");
                        lore.add("§7  §c(" + xpCost + " XP requis)");
                    }
                }
            }
            lore.add("§7▸ §eClic normal §7pour voir détails");
        } else {
            lore.add("§7▸ §6Achetez d'abord ce livre");
            lore.add("§7  §7dans la boutique!");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * NOUVEAU : Description du pouvoir pour les livres de pioche
     */
    private String getPickaxePowerDescription(String bookId) {
        return switch (bookId) {
            case "tonnerre" -> "Foudroie les ennemis et les blocs";
            case "incassable" -> "Durabilité infinie";
            case "explosion" -> "Explosion de minage";
            case "speed" -> "Vitesse de minage";
            case "xp_boost" -> "Multiplicateur d'XP";
            case "auto_sell" -> "Vente automatique";
            case "fortune" -> "Multiplicateur de drops";
            case "telekinesis" -> "Collecte magnétique";
            case "beacon_finder" -> "Détection de beacons";
            case "multiplier" -> "Multiplicateur de gains";
            default -> "Effet mystérieux";
        };
    }

    /**
     * Configure les boutons de contrôle
     */
    private void setupControlButtons(Inventory gui) {
        // Bouton boutique -> redirige vers un GUI dédié (sélecteur)
        ItemStack shopButton = new ItemStack(Material.EMERALD);
        ItemMeta shopMeta = shopButton.getItemMeta();
        plugin.getGUIManager().applyName(shopMeta, "§a💰 §lBoutique de Livres");
        plugin.getGUIManager().applyLore(shopMeta, List.of(
                "§7Accédez à la boutique dédiée",
                "§7Pioches ou Épées/Armures"
        ));
        shopButton.setItemMeta(shopMeta);
        gui.setItem(SHOP_SLOT, shopButton);

        // Item de résumé
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§5⚡ §lRÉSUMÉ DES ENCHANTS ACTIFS");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Affiche la liste détaillée de tous");
        lore.add("§7vos enchantements uniques actifs");
        lore.add("§7avec leurs niveaux et descriptions.");
        lore.add("");
        lore.add("§e➤ Cliquez pour voir le résumé!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        gui.setItem(SUMMARY_SLOT, item);
    }

    /**
     * MODIFIÉ : Gère les clics dans le menu des livres d'enchantement + détection livres physiques
     */
    public void handleEnchantmentBookMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        // NOUVEAU : Vérification si le joueur clique avec un livre physique dans la main
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType() == Material.ENCHANTED_BOOK && cursor.hasItemMeta() && cursor.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "enchant_book_id"), PersistentDataType.STRING)) {

            handlePhysicalBookApplication(player, cursor);
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
            plugin.getBookShopGUI().openPickaxeShop(player);
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
                        // MODIFIÉ : On passe l'item cliqué à la fonction d'erreur
                        showErrorFeedback(player, slot, "§cPas assez d'XP ou limite atteinte!", clickedItem);
                        return;
                    }
                    openEnchantmentBookMenu(player); // Refresh GUI
                } else {
                    // MODIFIÉ : On passe l'item cliqué à la fonction d'erreur
                    showErrorFeedback(player, slot, "§cVous ne possédez pas ce livre!", clickedItem);
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
        EnchantmentBookManager.EnchantmentBook book = enchantmentManager.getEnchantmentBook(bookId);
        int playerLevel = enchantmentManager.getEnchantmentBookLevel(player, bookId);
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
     * NOUVEAU : Affiche un feedback d'erreur visuel temporaire (comme CategoryMenuGUI)
     * MODIFIÉ : La méthode accepte maintenant l'item original en paramètre pour éviter les bugs.
     */
    private void showErrorFeedback(Player player, int slot, String errorMessage, ItemStack originalItem) {
        Inventory gui = player.getOpenInventory().getTopInventory();
        // Créer l'item d'erreur rouge
        ItemStack errorItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = errorItem.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c⚠️ §lERREUR");

        List<String> errorLore = new ArrayList<>();
        errorLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Split le message d'erreur sur plusieurs lignes
        String[] lines = errorMessage.split("\n");
        Collections.addAll(errorLore, lines);

        errorLore.add("");
        errorLore.add("§7L'action n'a pas pu être effectuée.");
        errorLore.add("§7Vérifiez vos ressources et réessayez.");
        errorLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, errorLore);
        errorItem.setItemMeta(meta);

        // Afficher l'erreur temporairement
        gui.setItem(slot, errorItem);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);

        // Restaurer l'item original après 0,5 seconde
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().equals(gui)) {
                ItemStack currentItem = gui.getItem(slot);
                if (currentItem != null && currentItem.getType() == Material.RED_CONCRETE) {
                    gui.setItem(slot, originalItem);
                }
            }
        }, 10L);
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
        for (int i = 0; i < EXTRA_BOOK_SLOTS.length && (i + 5) < allBooks.size(); i++) {
            if (EXTRA_BOOK_SLOTS[i] == slot) {
                return allBooks.get(i + 5).getId();
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