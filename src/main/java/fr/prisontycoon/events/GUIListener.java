package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.cristaux.Cristal;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener pour les interfaces graphiques générales
 */
public class GUIListener implements Listener {

    private final PrisonTycoon plugin;

    public GUIListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        if (title.equals("§6⚡ Fusion de Cristaux ⚡")) {
            plugin.getCristalGUI().handleFusionInventoryClick(event);
            return;
        }

        if (isContainerGUI(title)) {
            return;
        }

        // Si le clic est dans l'inventaire du joueur
        if (event.getClickedInventory() == player.getInventory()) {
            // On agit uniquement si le GUI des cristaux est ouvert
            if (title.contains("Gestion des Cristaux")) {
                handleCristalApplicationClick(player, event);
            }

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.ENCHANTED_BOOK &&
                    clickedItem.hasItemMeta() && clickedItem.getItemMeta().getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "enchant_book_id"), PersistentDataType.STRING) && title.contains("Enchantements Uniques")) {
                event.setCancelled(true);
                plugin.getEnchantmentBookGUI().handlePhysicalBookApplication(player, clickedItem);
            }
            // Pour tous les autres GUIs, on n'interfère pas avec l'inventaire du joueur.
            return;
        }

        // Si on arrive ici, le clic a eu lieu dans l'inventaire du haut (le GUI)
        if (!isPluginGUI(title)) return;

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        handleGUIClick(player, title, event.getSlot(), clickedItem, event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        String title = event.getView().getTitle();

        // NOUVEAU : Ignore complètement les GUIs de conteneur
        if (isContainerGUI(title)) {
            return; // Laisse ContainerListener gérer ces GUIs
        }

        if (!isPluginGUI(title)) return;

        // Empêche le glisser-déposer dans tous les autres GUIs du plugin
        event.setCancelled(true);
    }

    /**
     * Délègue les clics vers les bonnes GUIs (sauf conteneurs)
     */
    private void handleGUIClick(Player player, String title, int slot, ItemStack item, org.bukkit.event.inventory.ClickType clickType) {
        if (title.contains("Menu Principal") || title.contains("Menu Enchantement")) {
            plugin.getMainMenuGUI().handleEnchantmentMenuClick(player, slot, item);
        } else if (title.contains("Économiques") || title.contains("Utilités") ||
                title.contains("Mobilité") || title.contains("Spéciaux")) {
            plugin.getCategoryMenuGUI().handleCategoryMenuClick(player, slot, item, title, clickType);
        } else if (title.contains("🔧")) {
            plugin.getEnchantmentUpgradeGUI().handleUpgradeMenuClick(player, slot, item, clickType, title);
        } else if (title.contains("Gestion des Cristaux")) {
            plugin.getCristalGUI().handleCristalMenuClick(player, slot, item);
        } else if (title.contains("Enchantements Uniques")) {
            plugin.getEnchantmentBookGUI().handleEnchantmentBookMenuClick(player, slot, item, clickType);
        } else if (title.contains("Boutique de Livres")) {
            plugin.getEnchantmentBookGUI().handleBookShopClick(player, slot, item);
        } else if (title.contains("Compagnons")) {
            plugin.getPetsMenuGUI().handlePetsMenuClick(player, slot, item);
        } else if (title.contains("Réparation")) {
            plugin.getPickaxeRepairMenu().handleRepairMenuClick(player, slot, item);
        } else if (title.contains("Métiers") || title.contains("Choisir un Métier") || title.contains("⭐") || title.contains("Changer de Métier")) {
            plugin.getProfessionGUI().handleProfessionMenuClick(player, slot, item, clickType);
        } else if (title.contains("🎁")) {
            plugin.getProfessionRewardsGUI().handleRewardMenuClick(player, slot, item, clickType);
        } else if (title.contains("Prestige")) {
            plugin.getPrestigeGUI().handleClick(player, item, clickType);
        } else if (title.contains("MARCHÉ NOIR")) {
            plugin.getBlackMarketManager().handleBlackMarketClick(player, item);
        } else if (title.contains("Enchantement d'Épée") || title.contains("Enchantement d'Armure")) {
            plugin.getWeaponArmorEnchantGUI().handleMenuClick(player, slot, item, clickType);
        } else if (title.contains("Vos Boosts Actifs")) {
            plugin.getBoostGUI().handleClick(player, item);
        } else if (title.contains("§6⚡ §lGESTION DES AUTOMINEURS")) {
            plugin.getAutominerGUI().handleMainMenuClick(player, slot, item, clickType);
        } else if (title.contains("AUTOMINEUR §6⚙️")) {
            plugin.getAutominerGUI().handleAutominerManagementClick(player, slot, item, clickType);
        } else if (title.contains("§e⛽ §lCARBURANT")) {
            plugin.getAutominerGUI().handleFuelMenuClick(player, item, clickType);
        } else if (title.contains("§b🌍 §lMONDE")) {
            plugin.getAutominerGUI().handleWorldMenuClick(player, item);
        } else if (title.contains("§d📦 §lSTOCKAGE")) {
            plugin.getAutominerGUI().handleStorageMenuClick(player, item);
        } else if (title.contains("§6⚡ §lCONDENSATION")) {
            plugin.getAutominerGUI().handleCondenseMenuClick(player, item);
        }
    }

    /**
     * NOUVEAU : Gère la tentative d'application d'un cristal en cliquant dessus
     * depuis l'inventaire du joueur lorsque le GUI des cristaux est ouvert.
     */
    private void handleCristalApplicationClick(Player player, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();

        // On vérifie si l'item cliqué est un cristal révélé
        if (clickedItem == null || !plugin.getCristalManager().isCristal(clickedItem)) {
            return;
        }

        Cristal cristal = plugin.getCristalManager().getCristalFromItem(clickedItem);
        if (cristal == null || cristal.isVierge()) {
            player.sendMessage("§cCe cristal doit d'abord être révélé (clic-droit en main).");
            return;
        }

        // C'est un cristal applicable, on annule l'événement pour prendre le contrôle.
        event.setCancelled(true);

        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§cVous devez avoir une pioche légendaire pour appliquer des cristaux!");
            return;
        }

        // On tente l'application du cristal
        if (plugin.getCristalManager().applyCristalToPickaxe(player, pickaxe, cristal)) {
            // L'application a réussi, on consomme l'item
            clickedItem.setAmount(clickedItem.getAmount() - 1);

            // On rafraîchit l'interface pour montrer le changement
            plugin.getCristalGUI().refreshCristalGUI(player);
        }
        // Si l'application échoue, les messages d'erreur sont déjà envoyés par applyCristalToPickaxe.
    }

    /**
     * NOUVEAU : Vérifie si c'est un GUI de conteneur
     */
    private boolean isContainerGUI(String title) {
        return title.contains("Configuration Conteneur") ||
                title.contains("Conteneur Cassé") ||
                plugin.getContainerFilterGUI().isFilterGUI(title);
    }

    /**
     * Vérifie si c'est un GUI du plugin (sauf conteneurs)
     */
    private boolean isPluginGUI(String title) {
        return title.contains("Menu Principal") ||
                title.contains("Menu Enchantement") ||
                title.contains("Économiques") ||
                title.contains("Utilités") ||
                title.contains("Mobilité") ||
                title.contains("Spéciaux") ||
                title.contains("🔧") ||
                title.contains("Cristaux") ||
                title.contains("Enchantements Uniques") ||
                title.contains("Compagnons") ||
                title.contains("Livres d'Enchantements") ||
                title.contains("Boutique de Livres") ||
                title.contains("Réparation") ||
                title.contains("Métiers") ||
                title.contains("Choisir un Métier") ||
                title.contains("⭐") ||
                title.contains("Changer de Métier") ||
                title.contains("🎁") ||
                title.contains("Prestige") ||
                title.contains("MARCHÉ NOIR") ||
                title.contains("Vos Boosts Actifs") ||
                title.contains("§6⚡ §lGESTION DES AUTOMINEURS") ||
                title.contains("AUTOMINEUR §6⚙️") ||
                title.contains("§e⛽ §lCARBURANT") ||
                title.contains("§b🌍 §lMONDE") ||
                title.contains("§d📦 §lSTOCKAGE") ||
                title.contains("§6⚡ §lCONDENSATION") ||
                title.contains("§c⚡ §lÉNERGIE");
    }
}