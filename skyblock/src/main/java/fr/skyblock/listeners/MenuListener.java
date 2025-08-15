package fr.skyblock.listeners;

import fr.skyblock.CustomSkyblock;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class MenuListener implements Listener {

    private final CustomSkyblock plugin;

    public MenuListener(CustomSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String menuType = plugin.getMenuManager().getPlayerMenu(player.getUniqueId());
        if (menuType == null) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Déléguer la gestion du clic au MenuManager
        plugin.getMenuManager().handleMenuClick(player, event.getSlot(), menuType);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            plugin.getMenuManager().removePlayerMenu(player.getUniqueId());
        }
    }
}