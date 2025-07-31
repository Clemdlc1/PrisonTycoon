package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/**
 * Listener central pour tous les événements liés aux conteneurs.
 * Gère les GUIs, le cycle de vie des items et la synchronisation du cache.
 */
public class ContainerListener implements Listener {

    private final PrisonTycoon plugin;

    public ContainerListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    // === ÉVÉNEMENTS DU CYCLE DE VIE DU CACHE ET DE LA PERSISTANCE ===

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Délai pour s'assurer que le joueur est bien chargé
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                plugin.getContainerManager().handlePlayerJoin(event.getPlayer());
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getContainerManager().handlePlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (plugin.getContainerManager().isContainer(event.getItemDrop().getItemStack())) {
            plugin.getContainerManager().handleContainerDrop(event.getPlayer(), event.getItemDrop().getItemStack());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        if (plugin.getContainerManager().isContainer(event.getItem().getItemStack())) {
            // Re-scanner l'inventaire après le ramassage pour mettre à jour le cache
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getContainerManager().rescanAndCachePlayerInventory(event.getPlayer()));
        }
    }

    // === ÉVÉNEMENTS D'INTERACTION GUI & LOGIQUE DE JEU ===

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !plugin.getContainerManager().isContainer(item)) {
            return;
        }

        if (player.isSneaking() &&
                (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                        event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            var data = plugin.getContainerManager().getContainerData(item);
            if (data != null && data.isBroken()) {
                player.sendMessage("§c💥 Conteneur cassé! Ouverture en mode récupération...");
            }
            plugin.getContainerGUI().openContainerMenu(player, item);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getContainerManager().isContainer(event.getItemInHand())) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage("§c❌ Les conteneurs ne peuvent pas être posés au sol!");
            player.sendMessage("§7Gardez-les dans votre inventaire pour qu'ils collectent automatiquement les blocs minés.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        plugin.getContainerManager().handleInventoryUpdate(player);

        if (plugin.getContainerManager().isContainer(event.getCurrentItem()) || plugin.getContainerManager().isContainer(event.getCursor())) {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getContainerManager().rescanAndCachePlayerInventory(player));
        }

        // --- Logique de gestion des GUIs ---
        String title = event.getView().getTitle();
        if (title.contains("Configuration Conteneur") || title.contains("Conteneur Cassé")) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                plugin.getContainerGUI().handleContainerMenuClick(player, event.getSlot(), clickedItem, title);
            }
        } else if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            ItemStack cursorItem = event.getCursor();
            ItemStack currentItem = event.getCurrentItem();
            if ((cursorItem != null && plugin.getContainerManager().isContainer(cursorItem)) ||
                    (currentItem != null && event.getAction().toString().contains("MOVE_TO_OTHER_INVENTORY") && plugin.getContainerManager().isContainer(currentItem))) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Vous ne pouvez pas mettre un conteneur comme filtre!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (title.contains("Configuration Conteneur") || title.contains("Conteneur Cassé")) {
            event.setCancelled(true);
            return;
        }
        if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            if (plugin.getContainerManager().isContainer(event.getOldCursor())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            plugin.getContainerFilterGUI().saveFiltersFromInventory(player, event.getInventory(), title);
        }
    }
}