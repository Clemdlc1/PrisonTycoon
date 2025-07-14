package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
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
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour les événements liés aux conteneurs - MODIFIÉ pour autoriser mouvement libre
 */
public class ContainerListener implements Listener {

    private final PrisonTycoon plugin;

    public ContainerListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère les interactions avec les conteneurs (Shift + Clic droit pour ouvrir la config)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !plugin.getContainerManager().isContainer(item)) {
            return;
        }

        // Shift + Clic droit pour ouvrir la configuration
        if (player.isSneaking() &&
                (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                        event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {

            event.setCancelled(true);

            // Permet d'ouvrir même si cassé (pour récupérer le contenu)
            var data = plugin.getContainerManager().getContainerData(item);
            if (data != null && data.isBroken()) {
                player.sendMessage("§c💥 Conteneur cassé! Ouverture en mode récupération...");
            }

            // Ouvre l'interface de configuration
            plugin.getContainerGUI().openContainerMenu(player, item);
        }
    }

    /**
     * Empêche de placer les conteneurs au sol
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();

        if (plugin.getContainerManager().isContainer(item)) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            player.sendMessage("§c❌ Les conteneurs ne peuvent pas être posés au sol!");
            player.sendMessage("§7Gardez-les dans votre inventaire pour qu'ils collectent automatiquement les blocs minés.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * MODIFIÉ : Gère les clics dans les GUIs (conteneur principal ET filtres)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // GUI principal des conteneurs
        if (title.contains("Configuration Conteneur") || title.contains("Conteneur Cassé")) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            // Annule toujours l'événement pour prendre le contrôle total
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getSlot();

            // Gère les clics
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                plugin.getContainerGUI().handleContainerMenuClick(player, slot, clickedItem, title);
            }
        }
        // NOUVEAU : GUI des filtres - empêche de mettre des conteneurs
        else if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            ItemStack clickedItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            // Vérifie si on essaie de mettre un conteneur dans les filtres
            if ((clickedItem != null && plugin.getContainerManager().isContainer(clickedItem)) ||
                    (cursorItem != null && plugin.getContainerManager().isContainer(cursorItem))) {

                event.setCancelled(true);
                player.sendMessage("§c❌ Vous ne pouvez pas mettre un conteneur comme filtre!");
                player.sendMessage("§7Les conteneurs ne peuvent pas filtrer d'autres conteneurs.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            // Sinon, permet la manipulation libre dans le GUI des filtres
        }
    }

    /**
     * CORRIGÉ : Gère la fermeture des inventaires (sauvegarde automatique des filtres)
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        // Sauvegarde automatique des filtres avec méthode corrigée
        if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            plugin.getContainerFilterGUI().saveFiltersFromInventory(player, event.getInventory(), title);
            return;
        }

        if (title.contains("Configuration Conteneur")) {
            plugin.getPluginLogger().debug("Fermeture GUI conteneur pour " + player.getName());
        }
    }

    /**
     * Empêche de drag dans les GUIs des conteneurs (pas dans les filtres)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();

        if (title.contains("Configuration Conteneur") || title.contains("Conteneur Cassé")) {
            event.setCancelled(true);
        }

        // NOUVEAU : Empêche de drag des conteneurs dans les filtres
        if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            // Vérifie si on essaie de drag un conteneur
            ItemStack draggedItem = event.getOldCursor();
            if (draggedItem != null && plugin.getContainerManager().isContainer(draggedItem)) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Vous ne pouvez pas mettre un conteneur comme filtre!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Gère les drops d'items
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Vérifie si le joueur a un GUI de conteneur ouvert
        if (player.getOpenInventory() != null &&
                (player.getOpenInventory().getTitle().contains("Configuration Conteneur") ||
                        player.getOpenInventory().getTitle().contains("Conteneur Cassé"))) {

            // Empêche de drop des items quand le GUI conteneur principal est ouvert
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous ne pouvez pas jeter d'items avec ce menu ouvert!");
        }
        // Les filtres permettent le drop (pour plus de liberté)
    }

    /**
     * MODIFIÉ : Protections seulement pour tables de craft/enclume + placement au sol
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftingTableUse(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Vérifie SEULEMENT les tables de craft, enclumes, etc. (pas l'inventaire du joueur)
        if (event.getInventory().getType() == InventoryType.WORKBENCH ||
                event.getInventory().getType() == InventoryType.ANVIL ||
                event.getInventory().getType() == InventoryType.GRINDSTONE ||
                event.getInventory().getType() == InventoryType.SMITHING ||
                event.getInventory().getType() == InventoryType.ENCHANTING ||
                event.getInventory().getType() == InventoryType.CARTOGRAPHY ||
                event.getInventory().getType() == InventoryType.LOOM) {

            ItemStack clickedItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            // Bloque les conteneurs dans ces interfaces spécifiques
            if ((clickedItem != null && plugin.getContainerManager().isContainer(clickedItem)) ||
                    (cursorItem != null && plugin.getContainerManager().isContainer(cursorItem))) {

                event.setCancelled(true);
                player.sendMessage("§c❌ Les conteneurs ne peuvent pas être utilisés dans les tables de craft, enclumes ou autres stations de travail!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    /**
     * SUPPRIMÉ : Plus de restrictions dans l'inventaire du joueur pour le craft
     * Les joueurs peuvent maintenant bouger librement leurs items même avec des conteneurs
     */

    /**
     * SUPPRIMÉ : onContainerModification qui bloquait tout mouvement
     * Les conteneurs peuvent maintenant être bougés librement dans l'inventaire
     */
}