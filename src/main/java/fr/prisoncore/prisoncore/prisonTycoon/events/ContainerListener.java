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
 * Listener pour les événements liés aux conteneurs - MODIFIÉ pour filtres séparés
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

            // MODIFIÉ : Permet d'ouvrir même si cassé (pour récupérer le contenu)
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
        // NOUVEAU : GUI des filtres
        else if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            // Dans le GUI des filtres, on permet TOUT (libre manipulation des 9 slots)
            // Pas de cancellation ici, laisse le joueur faire ce qu'il veut
        }
    }

    /**
     * MODIFIÉ : Gère la fermeture des inventaires (sauvegarde automatique des filtres)
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        // Sauvegarde automatique des filtres
        if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            ItemStack containerItem = plugin.getContainerFilterGUI().findContainerFromFilterTitle(player, title);
            if (containerItem != null) {
                plugin.getContainerFilterGUI().saveFiltersFromInventory(player, event.getInventory(), containerItem);
            }
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
        // Les filtres permettent le drag libre
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
     * NOUVEAU : Empêche les conteneurs dans les tables de craft et enclumes + déplacements clavier
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftingTableUse(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Vérifie le type d'inventaire
        if (event.getInventory().getType() == InventoryType.WORKBENCH ||
                event.getInventory().getType() == InventoryType.ANVIL ||
                event.getInventory().getType() == InventoryType.GRINDSTONE ||
                event.getInventory().getType() == InventoryType.SMITHING) {

            ItemStack clickedItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            // NOUVEAU : Bloque aussi les déplacements par touches (shift+click, etc.)
            if (event.isShiftClick() || event.isRightClick() || event.isLeftClick()) {
                if ((clickedItem != null && plugin.getContainerManager().isContainer(clickedItem)) ||
                        (cursorItem != null && plugin.getContainerManager().isContainer(cursorItem))) {

                    event.setCancelled(true);
                    player.sendMessage("§c❌ Les conteneurs ne peuvent pas être utilisés dans les tables de craft, enclumes, meules ou tables de forge!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
            }
        }
    }

    /**
     * NOUVEAU : Bloque les crafts dans l'inventaire du joueur quand il a des conteneurs
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryCraft(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Vérifie si c'est un craft dans l'inventaire du joueur
        if (event.getInventory() instanceof CraftingInventory craftingInv &&
                event.getInventory().getType() == InventoryType.CRAFTING) {

            // Vérifie si le joueur a des conteneurs
            boolean hasContainers = false;
            for (ItemStack item : player.getInventory().getContents()) {
                if (plugin.getContainerManager().isContainer(item)) {
                    hasContainers = true;
                    break;
                }
            }

            if (hasContainers) {
                // Vérifie si le clic est dans la zone de craft (slots 1-4) ou résultat (slot 0)
                if (event.getSlot() >= 0 && event.getSlot() <= 4) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Impossible de crafter avec des conteneurs dans l'inventaire!");
                    player.sendMessage("§7Les conteneurs peuvent interférer avec le système de craft.");
                    player.sendMessage("§7Stockez-les temporairement dans un coffre pour crafter.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Protection contre la modification des conteneurs dans l'inventaire normal
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onContainerModification(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        // Ignore les GUIs de conteneurs et filtres
        if (title.contains("Configuration Conteneur") || title.contains("Conteneur Cassé") ||
                plugin.getContainerFilterGUI().isFilterGUI(title)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Empêche de modifier les conteneurs dans l'inventaire normal via certaines actions
        boolean isContainerClick = (clickedItem != null && plugin.getContainerManager().isContainer(clickedItem)) ||
                (cursorItem != null && plugin.getContainerManager().isContainer(cursorItem));

        if (isContainerClick) {
            // Permet le déplacement normal mais empêche certaines actions dangereuses
            switch (event.getClick()) {
                case DROP, CONTROL_DROP -> {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Vous ne pouvez pas jeter les conteneurs!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
                case SHIFT_LEFT, SHIFT_RIGHT -> {
                    // Permet les shift-clics pour organiser l'inventaire
                    // Mais vérifie que ce n'est pas dans un inventaire externe
                    if (!event.getView().getTopInventory().equals(player.getInventory())) {
                        // Empêche de mettre les conteneurs dans d'autres inventaires
                        event.setCancelled(true);
                        player.sendMessage("§c❌ Les conteneurs doivent rester dans votre inventaire!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                }
                // NOUVEAU : Bloque aussi les double-clics et autres
                case DOUBLE_CLICK -> {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Action bloquée avec les conteneurs!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    /**
     * Nettoie les ressources quand un joueur se déconnecte
     */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Ferme le GUI s'il est ouvert
        if (player.getOpenInventory() != null &&
                (player.getOpenInventory().getTitle().contains("Configuration Conteneur") ||
                        player.getOpenInventory().getTitle().contains("Conteneur Cassé") ||
                        plugin.getContainerFilterGUI().isFilterGUI(player.getOpenInventory().getTitle()))) {
            player.closeInventory();
        }

        plugin.getPluginLogger().debug("Nettoyage des données de conteneur pour " + player.getName());
    }
}