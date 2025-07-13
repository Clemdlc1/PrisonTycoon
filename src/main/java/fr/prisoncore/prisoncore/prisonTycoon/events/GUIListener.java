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
 * Listener pour les interfaces graphiques
 * CORRIGÉ : Protection complète contre déplacement d'items dans tous les menus
 */
public class GUIListener implements Listener {

    private final PrisonTycoon plugin;

    public GUIListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!isPluginGUI(title)) return;

        // ÉTAPE CRUCIALE : Toujours annuler l'événement pour prendre le contrôle total.
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return; // Ne rien faire si c'est un slot vide ou un item décoratif.
        }

        // Déléguer le traitement à la méthode centrale.
        handleGUIClick(player, title, event.getSlot(), clickedItem, event.getClick());
    }

    /**
     * Délègue les clics vers les bonnes GUIs
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
        else if (title.contains("Cristaux")) {
            plugin.getCrystalsMenuGUI().handleCrystalsMenuClick(player, slot, item);
        }
        else if (title.contains("Enchantements Uniques")) {
            plugin.getUniqueEnchantsMenuGUI().handleUniqueEnchantsMenuClick(player, slot, item);
        }
        else if (title.contains("Compagnons")) {
            plugin.getPetsMenuGUI().handlePetsMenuClick(player, slot, item);
        }
        else if (title.contains("Réparation")) {
            plugin.getPetsMenuGUI().handlePetsMenuClick(player, slot, item);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (isPluginGUI(title)) {
            // Met à jour la pioche quand on ferme le menu
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getPickaxeManager().updatePlayerPickaxe(player);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (isPluginGUI(title)) {
            // CORRIGÉ : Empêche TOUT drag dans les GUIs du plugin
            event.setCancelled(true);
            plugin.getPluginLogger().debug("Drag bloqué dans GUI: " + title);
        }
    }

    /**
     * CORRIGÉ : Vérifie si le titre correspond à une GUI du plugin (toutes les nouvelles)
     */
    private boolean isPluginGUI(String title) {
        return title.contains("PrisonTycoon") ||
                title.contains("Menu Principal") ||
                title.contains("Menu Enchantement") ||
                title.contains("Enchantements") ||
                title.contains("Économiques") ||
                title.contains("Utilités") ||
                title.contains("Mobilité") ||
                title.contains("Spéciaux") ||
                title.contains("Cristaux") ||
                title.contains("Compagnons") ||
                title.contains("🔧") ||
                title.contains("Réparation");
    }
}