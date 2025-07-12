package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
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

        // Vérifie si c'est une GUI du plugin
        String title = event.getView().getTitle();
        if (!isPluginGUI(title)) {
            return; // Pas une GUI du plugin
        }

        plugin.getPluginLogger().debug("Clic dans GUI: " + title + ", slot: " + event.getSlot() +
                ", clickType: " + event.getClick());

        // CORRIGÉ : Annule TOUS les clics dans les GUIs du plugin SAUF le clic molette pour mobilité
        if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE && title.contains("Mobilité")) {
            // Laisse passer le clic molette pour les enchants mobilité
            var clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {
                // Délègue à la bonne GUI pour traitement du clic molette
                handleGUIClick(player, title, event.getSlot(), clickedItem, event.getClick());
            }
            return; // Ne pas annuler le clic molette dans le menu mobilité
        }

        // ANNULE TOUS LES AUTRES CLICS dans les GUIs du plugin
        event.setCancelled(true);

        var clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == org.bukkit.Material.AIR) {
            return; // Clic sur slot vide
        }

        // Évite le traitement si l'item n'a pas de nom (item de remplissage)
        if (!clickedItem.hasItemMeta() || !clickedItem.getItemMeta().hasDisplayName()) {
            return;
        }

        // Délègue à la bonne GUI selon le titre
        handleGUIClick(player, title, event.getSlot(), clickedItem, event.getClick());
    }

    /**
     * Délègue les clics vers les bonnes GUIs
     */
    private void handleGUIClick(Player player, String title, int slot, ItemStack item, org.bukkit.event.inventory.ClickType clickType) {
        if (title.contains("Menu Principal") || title.contains("Menu Enchantement")) {
            plugin.getMainMenuGUI().handleEnchantmentMenuClick(player, slot, item);
        }
        else if (title.contains("Économiques") || title.contains("Efficacité") ||
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
        // LEGACY: Support pour l'ancien menu unifié (si jamais utilisé)
        else if (title.contains("Enchantements PrisonTycoon")) {
            // Redirige vers le nouveau menu principal
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
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
                // Met à jour le scoreboard aussi
                plugin.getScoreboardManager().updateScoreboard(player);
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
                title.contains("Efficacité") ||
                title.contains("Mobilité") ||
                title.contains("Spéciaux") ||
                title.contains("Cristaux") ||
                title.contains("Compagnons") ||
                title.contains("🔧"); // Menu d'amélioration
    }
}