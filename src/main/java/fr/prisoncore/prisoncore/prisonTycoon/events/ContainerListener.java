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
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Listener pour les événements liés aux conteneurs - CORRIGÉ
 * Gestion complète des GUIs sans déplacement d'items
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

        if (isKey(item)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            player.sendMessage("§c❌ Les clés ne peuvent pas être posées au sol!");
            player.sendMessage("§7Gardez-les dans votre inventaire ou utilisez-les pour ouvrir des coffres.");
            plugin.getPluginLogger().debug("Tentative de placement de clé bloquée: " + player.getName());
        }
    }

    /**
     * Vérifie si un item est une clé
     */
    private boolean isKey(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getDisplayName() == null) {
            return false;
        }

        String displayName = meta.getDisplayName().toLowerCase();

        // Vérifie si le nom contient "clé" et l'un des types de clés
        return displayName.contains("clé") && (
                displayName.contains("cristal") ||
                        displayName.contains("légendaire") ||
                        displayName.contains("rare") ||
                        displayName.contains("peu commune") ||
                        displayName.contains("commune")
        );
    }

    /**
     * CORRIGÉ : Gère les clics dans tous les GUIs de conteneur
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();

        // GUI principal des conteneurs
        if (title.contains("Configuration Conteneur") || title.contains("Conteneur Cassé")) {
            // CRITIQUE : Annule TOUJOURS l'événement pour empêcher le déplacement d'items
            event.setCancelled(true);

            ItemStack clickedItem = event.getCurrentItem();
            int slot = event.getSlot();

            // Ne traite que les clics sur des items avec métadonnées
            if (clickedItem != null && clickedItem.getType() != Material.AIR &&
                    clickedItem.hasItemMeta() && clickedItem.getItemMeta().hasDisplayName()) {

                plugin.getContainerGUI().handleContainerMenuClick(player, slot, clickedItem, title);
            }
            return;
        }

        // GUI des filtres
        if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            ItemStack clickedItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            // Empêche de mettre des conteneurs dans les filtres
            if ((clickedItem != null && plugin.getContainerManager().isContainer(clickedItem)) ||
                    (cursorItem != null && plugin.getContainerManager().isContainer(cursorItem))) {

                event.setCancelled(true);
                player.sendMessage("§c❌ Vous ne pouvez pas mettre un conteneur comme filtre!");
                player.sendMessage("§7Les conteneurs ne peuvent pas filtrer d'autres conteneurs.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // NOUVEAU : Empêche les clics dans l'inventaire du joueur depuis le GUI de filtres
            if (event.getClickedInventory() != null &&
                    event.getClickedInventory().getType() == InventoryType.PLAYER) {

                // Autorise seulement si c'est pour déplacer un item VERS le GUI de filtres
                if (event.getAction().toString().contains("MOVE_TO_OTHER_INVENTORY")) {
                    // Vérifie que l'item n'est pas un conteneur
                    if (clickedItem != null && plugin.getContainerManager().isContainer(clickedItem)) {
                        event.setCancelled(true);
                        player.sendMessage("§c❌ Vous ne pouvez pas utiliser un conteneur comme filtre!");
                        return;
                    }
                    // Autorise le mouvement vers le GUI
                    return;
                }

                // Annule tous les autres clics dans l'inventaire du joueur
                event.setCancelled(true);
                return;
            }

            // Pour les clics dans le GUI de filtres lui-même, permet la manipulation libre
            // (sauf pour les conteneurs, déjà gérés ci-dessus)
            return;
        }
    }

    /**
     * CORRIGÉ : Empêche le drag and drop dans les GUIs de conteneur
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        String title = event.getView().getTitle();

        // Empêche complètement le drag dans les GUIs principaux
        if (title.contains("Configuration Conteneur") || title.contains("Conteneur Cassé")) {
            event.setCancelled(true);
            return;
        }

        // Pour les GUIs de filtres, empêche le drag de conteneurs
        if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            ItemStack draggedItem = event.getOldCursor();
            if (draggedItem != null && plugin.getContainerManager().isContainer(draggedItem)) {
                event.setCancelled(true);
                Player player = (Player) event.getWhoClicked();
                player.sendMessage("§c❌ Vous ne pouvez pas utiliser un conteneur comme filtre!");
                return;
            }
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

        // Sauvegarde automatique des filtres à la fermeture
        if (plugin.getContainerFilterGUI().isFilterGUI(title)) {
            plugin.getContainerFilterGUI().saveFiltersFromInventory(player, event.getInventory(), title);
        }

        // Nettoie les références pour tous les types de GUIs
        if (title.contains("🎯 Filtres")) {
            plugin.getContainerFilterGUI().cleanupClosedGUI(title);
        }
    }

    /**
     * Empêche de jeter les conteneurs
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        if (plugin.getContainerManager().isContainer(item)) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            player.sendMessage("§c❌ Vous ne pouvez pas jeter un conteneur!");
            player.sendMessage("§7Utilisez §e/conteneur §7pour gérer vos conteneurs.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * NOUVEAU : Empêche de mettre les conteneurs dans les coffres/crafting
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onContainerPlacement(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Ignore nos propres GUIs
        String title = event.getView().getTitle();
        if (title.contains("Configuration Conteneur") || title.contains("Conteneur Cassé") ||
                plugin.getContainerFilterGUI().isFilterGUI(title)) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // Empêche de placer des conteneurs dans des coffres/crafting/etc
        if ((clickedItem != null && plugin.getContainerManager().isContainer(clickedItem)) ||
                (cursorItem != null && plugin.getContainerManager().isContainer(cursorItem))) {

            // Autorise seulement dans l'inventaire du joueur
            if (event.getClickedInventory() != null &&
                    event.getClickedInventory().getType() != InventoryType.PLAYER) {

                event.setCancelled(true);
                player.sendMessage("§c❌ Les conteneurs ne peuvent être stockés que dans votre inventaire!");
                player.sendMessage("§7Ils doivent rester sur vous pour fonctionner.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    /**
     * NOUVEAU : Empêche l'utilisation des conteneurs dans le crafting
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftingAttempt(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getInventory() instanceof CraftingInventory) {
            ItemStack clickedItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            if ((clickedItem != null && plugin.getContainerManager().isContainer(clickedItem)) ||
                    (cursorItem != null && plugin.getContainerManager().isContainer(cursorItem))) {

                event.setCancelled(true);
                player.sendMessage("§c❌ Les conteneurs ne peuvent pas être utilisés dans le crafting!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }
}