package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour les interfaces graphiques
 * CORRIGÉ : Logique de clic simplifiée et robuste
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
        if (!isPluginGUI(title)) {
            return; // Ce n'est pas une GUI de notre plugin
        }

        // --- CORRECTION MAJEURE ---
        // 1. On annule TOUJOURS l'événement pour empêcher toute interaction par défaut (comme prendre un item).
        event.setCancelled(true);

        // 2. On vérifie l'item cliqué
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return; // Clic sur un slot vide
        }

        // Évite le traitement sur les items de remplissage (sans nom)
        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        plugin.getPluginLogger().debug("Clic dans GUI: " + title + ", slot: " + event.getSlot() +
                ", clickType: " + event.getClick());

        // 3. On délègue TOUJOURS le traitement à la méthode handleGUIClick.
        // C'est ensuite au gestionnaire de la GUI de décider quoi faire avec le type de clic.
        handleGUIClick(player, title, event.getSlot(), clickedItem, event.getClick());
    }

    /**
     * Délègue les clics vers les bonnes GUIs
     */
    private void handleGUIClick(Player player, String title, int slot, ItemStack item, ClickType clickType) {
        if (title.contains("Menu Principal") || title.contains("Menu Enchantement")) {
            plugin.getMainMenuGUI().handleEnchantmentMenuClick(player, slot, item);
        } else if (title.contains("Économiques") || title.contains("Utilités") ||
                title.contains("Mobilité") || title.contains("Spéciaux")) {
            // La méthode handleCategoryMenuClick reçoit maintenant bien le clic molette
            plugin.getCategoryMenuGUI().handleCategoryMenuClick(player, slot, item, title, clickType);
        } else if (title.contains("🔧")) {
            plugin.getEnchantmentUpgradeGUI().handleUpgradeMenuClick(player, slot, item, clickType, title);
        } else if (title.contains("Cristaux")) {
            plugin.getCrystalsMenuGUI().handleCrystalsMenuClick(player, slot, item);
        } else if (title.contains("Enchantements Uniques")) {
            plugin.getUniqueEnchantsMenuGUI().handleUniqueEnchantsMenuClick(player, slot, item);
        } else if (title.contains("Compagnons")) {
            plugin.getPetsMenuGUI().handlePetsMenuClick(player, slot, item);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (isPluginGUI(title)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                plugin.getPickaxeManager().updatePlayerPickaxe(player);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (isPluginGUI(title)) {
            event.setCancelled(true);
        }
    }

    /**
     * Vérifie si le titre correspond à une GUI du plugin
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
                title.contains("🔧");
    }
}