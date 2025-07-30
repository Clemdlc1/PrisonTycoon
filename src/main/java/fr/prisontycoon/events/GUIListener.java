package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.gui.GUIManager;
import fr.prisontycoon.gui.GUIType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Nouveau GUIListener optimisé utilisant le système d'IDs
 * Plus de title.contains() !
 */
public class GUIListener implements Listener {

    private final PrisonTycoon plugin;
    private final GUIManager guiManager;

    public GUIListener(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Récupère le type de GUI depuis le gestionnaire
        GUIType guiType = guiManager.getOpenGUIType(player);
        if (guiType == null) return; // Pas un GUI du plugin

        // Vérifie si c'est un GUI conteneur (géré séparément)
        if (isContainerGUI(guiType)) {
            return;
        }

        if (event.getClickedInventory() == player.getInventory()) {
            if (guiType == GUIType.CRISTAL_MANAGEMENT) {
                plugin.getCristalGUI().handleCristalApplicationClick(player, event);
                return;
            }

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.ENCHANTED_BOOK &&
                clickedItem.hasItemMeta() && clickedItem.getItemMeta().getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "enchant_book_id"), PersistentDataType.STRING) && guiType == GUIType.ENCHANTMENT_BOOK) {
                event.setCancelled(true);
                plugin.getEnchantmentBookGUI().handlePhysicalBookApplication(player, clickedItem);
            }

            if (guiType == GUIType.AUTOMINER_MAIN && plugin.getAutominerManager().isAutominer(clickedItem)) {
                event.setCancelled(true);
                plugin.getAutominerGUI().handleInventoryItemClick(player, clickedItem);
            }
            if (guiType == GUIType.AUTOMINER_UPGRADE && plugin.getCristalManager().isCristal(clickedItem)) {
                plugin.getAutominerEnchantGUI().handleCrystalApplication(player, clickedItem);
            }
            return;
        }

        // Empêche les modifications dans les GUIs
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null) return;

        // Délègue vers la bonne méthode de traitement
        handleGUIClick(player, guiType, event.getSlot(), clickedItem, event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GUIType guiType = guiManager.getOpenGUIType(player);
        if (guiType == null) return;

        // Gère les fermetures spéciales
        handleGUIClose(player, guiType, event);

        // Supprime l'enregistrement
        guiManager.unregisterGUI(player, event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        GUIType guiType = guiManager.getOpenGUIType(player);
        if (guiType == null) return;

        if (isContainerGUI(guiType)) {
            return; // Laisse ContainerListener gérer
        }

        // Empêche le glisser-déposer dans tous les autres GUIs
        event.setCancelled(true);
    }

    /**
     * Délègue les clics vers les bonnes GUIs basé sur l'enum
     */
    private void handleGUIClick(Player player, GUIType guiType, int slot, ItemStack item, org.bukkit.event.inventory.ClickType clickType) {
        switch (guiType) {
            case ENCHANTMENT_MENU -> plugin.getMainMenuGUI().handleEnchantmentMenuClick(player, slot, item);

            case CATEGORY_ENCHANT -> plugin.getCategoryMenuGUI().handleCategoryMenuClick(player, slot, item, clickType);

            case ENCHANTMENT_UPGRADE ->
                    plugin.getEnchantmentUpgradeGUI().handleUpgradeMenuClick(player, slot, item, guiManager.getGUIData(player, "enchantment"));

            case CRISTAL_MANAGEMENT -> plugin.getCristalGUI().handleCristalMenuClick(player, slot, item);

            case ENCHANTMENT_BOOK ->
                    plugin.getEnchantmentBookGUI().handleEnchantmentBookMenuClick(player, slot, item, clickType);

            case BOOK_SHOP -> plugin.getEnchantmentBookGUI().handleBookShopClick(player, slot, item);

            case PETS_MENU -> plugin.getPetsMenuGUI().handlePetsMenuClick(player, slot, item);

            case PICKAXE_REPAIR -> plugin.getPickaxeRepairMenu().handleRepairMenuClick(player, slot, item);

            case PROFESSION_MAIN, PROFESSION_TALENTS, PROFESSION_REWARDS ->
                    plugin.getProfessionGUI().handleProfessionMenuClick(player, slot, item, clickType);

            case PRESTIGE_MENU -> plugin.getPrestigeGUI().handleClick(player, item, clickType);

            case BLACK_MARKET -> plugin.getBlackMarketManager().handleBlackMarketClick(player, item);

            case WEAPON_ARMOR_ENCHANT ->
                    plugin.getWeaponArmorEnchantGUI().handleMenuClick(player, slot, item, clickType);

            case BOOST_MENU -> plugin.getBoostGUI().handleClick(player, item);

            case AUTOMINER_MAIN -> plugin.getAutominerGUI().handleMainMenuClick(player, slot, item, clickType);

            case AUTOMINER_ENCHANT ->
                    plugin.getAutominerEnchantGUI().handleEnchantMenuClick(player, slot, item, clickType);

            case AUTOMINER_UPGRADE ->
                    plugin.getAutominerEnchantUpgradeGUI().handleUpgradeClick(player, slot, item, clickType);

            case AUTOMINER_STORAGE -> plugin.getAutominerCondHeadGUI().handleStorageClick(player, slot, item);

            case GANG_MAIN, GANG_MANAGEMENT -> plugin.getGangGUI().handleGangMenuClick(player, slot, item, clickType);

            case GANG_BANNER_CREATOR -> plugin.getGangGUI().handleBannerCreatorClick(player, slot, item, clickType);

            case BANK_MAIN -> plugin.getBankGUI().handleMainMenuClick(player, slot, item);

            case INVESTMENT_MENU -> plugin.getBankGUI().handleInvestmentMenuClick(player, slot, item, clickType);
            default -> {
                plugin.getPluginLogger().warning("GUI non géré: " + guiType);
            }
        }
    }

    /**
     * Gère les fermetures spéciales de GUIs
     */
    private void handleGUIClose(Player player, GUIType guiType, InventoryCloseEvent event) {
        switch (guiType) {
            case AUTOMINER_CONDENSATION ->
                    plugin.getAutominerCondHeadGUI().handleCondensationClose(player, event.getInventory());

            case AUTOMINER_FUEL -> plugin.getAutominerCondHeadGUI().handleFuelClose(player, event.getInventory());

            case GANG_MAIN, GANG_MANAGEMENT -> plugin.getGangGUI().closeGui(player);

            default -> {
                // La plupart des GUIs n'ont pas besoin de traitement spécial à la fermeture
            }
        }
    }

    /**
     * Vérifie si c'est un GUI de conteneur
     */
    private boolean isContainerGUI(GUIType guiType) {
        return guiType == GUIType.CONTAINER_CONFIG ||
               guiType == GUIType.CONTAINER_FILTER;
    }
}