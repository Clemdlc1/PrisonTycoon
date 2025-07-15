package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

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
            return; // Le traitement est entièrement délégué, on arrête ici.
        }

        // NOUVEAU : Ignore complètement les GUIs de conteneur (gérés par ContainerListener)
        if (isContainerGUI(title)) {
            return; // Laisse ContainerListener gérer ces GUIs
        }

        if (!isPluginGUI(title)) return;

        // ÉTAPE CRUCIALE : Toujours annuler l'événement pour prendre le contrôle total.
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR ||
                !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return; // Ne rien faire si c'est un slot vide ou un item décoratif.
        }

        // Déléguer le traitement à la méthode centrale.
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
        }
        else if (title.contains("Économiques") || title.contains("Utilités") ||
                title.contains("Mobilité") || title.contains("Spéciaux")) {
            plugin.getCategoryMenuGUI().handleCategoryMenuClick(player, slot, item, title, clickType);
        }
        else if (title.contains("🔧")) {
            plugin.getEnchantmentUpgradeGUI().handleUpgradeMenuClick(player, slot, item, clickType, title);
        }
        else if (title.contains("Gestion des Cristaux")) {
            plugin.getCristalGUI().handleCristalMenuClick(player, slot, item);
        }
        else if (title.contains("Enchantements Uniques")) {
            plugin.getUniqueEnchantsMenuGUI().handleUniqueEnchantsMenuClick(player, slot, item);
        }
        else if (title.contains("Compagnons")) {
            plugin.getPetsMenuGUI().handlePetsMenuClick(player, slot, item);
        }
        else if (title.contains("Réparation")) {
            plugin.getPickaxeRepairMenu().handleRepairMenuClick(player, slot, item);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // NOUVEAU : Ignore complètement les GUIs de conteneur
        if (isContainerGUI(title)) {
            return; // Laisse ContainerListener gérer la fermeture
        }

        // Gestion de la fermeture pour les autres GUIs si nécessaire
        // (Actuellement aucune action spéciale requise)
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
                title.contains("Réparation");
    }
}