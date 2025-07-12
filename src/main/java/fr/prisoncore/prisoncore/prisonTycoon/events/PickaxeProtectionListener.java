package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour la protection de la pioche légendaire
 * CORRIGÉ : Protection complète contre toutes les formes de perte
 */
public class PickaxeProtectionListener implements Listener {

    private final PrisonTycoon plugin;

    public PickaxeProtectionListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        var currentItem = event.getCurrentItem();
        var cursor = event.getCursor();

        // Vérifie l'item cliqué (pioche dans l'inventaire)
        if (currentItem != null && plugin.getPickaxeManager().isLegendaryPickaxe(currentItem)) {

            // CORRECTION: Bloque TOUTES les interactions hors inventaire principal du joueur
            if (event.getClickedInventory() != player.getInventory()) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Vous ne pouvez pas sortir la pioche légendaire de votre inventaire!");
                plugin.getPluginLogger().debug("Tentative de sortie de pioche bloquée (inventaire externe)");
                return;
            }

            // BLOQUE les touches numériques (hotbar swap) vers d'autres inventaires
            if (event.getClick().isKeyboardClick()) {
                // Vérifie si l'inventaire ouvert n'est pas l'inventaire du joueur
                if (event.getView().getTopInventory() != player.getInventory() &&
                        event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Action interdite avec la pioche légendaire!");
                    plugin.getPluginLogger().debug("Touche clavier bloquée avec pioche");
                    return;
                }
            }

            // BLOQUE le SHIFT+CLIC vers d'autres inventaires
            if (event.isShiftClick()) {
                // Si l'inventaire du haut n'est pas l'inventaire du joueur
                if (event.getView().getTopInventory() != player.getInventory() &&
                        event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Vous ne pouvez pas déplacer la pioche légendaire!");
                    plugin.getPluginLogger().debug("Shift+clic bloqué avec pioche");
                    return;
                }
            }
        }

        // Vérifie l'item dans le curseur (tentative de placement)
        if (cursor != null && plugin.getPickaxeManager().isLegendaryPickaxe(cursor)) {
            // BLOQUE le placement hors inventaire principal
            if (event.getClickedInventory() != player.getInventory()) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Vous ne pouvez pas sortir la pioche légendaire de votre inventaire!");
                plugin.getPluginLogger().debug("Tentative de placement de pioche bloquée");
                return;
            }
        }

        // NOUVELLE VÉRIFICATION: Touches numériques pour échanger avec hotbar
        if (event.getClick().isKeyboardClick() && event.getClickedInventory() != player.getInventory()) {
            // Vérifie si la pioche est dans le slot de la touche pressée
            int hotbarSlot = event.getHotbarButton();
            if (hotbarSlot >= 0 && hotbarSlot < 9) {
                ItemStack hotbarItem = player.getInventory().getItem(hotbarSlot);
                if (hotbarItem != null && plugin.getPickaxeManager().isLegendaryPickaxe(hotbarItem)) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Vous ne pouvez pas échanger la pioche légendaire!");
                    plugin.getPluginLogger().debug("Échange hotbar avec pioche bloqué");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        var draggedItem = event.getOldCursor();

        if (draggedItem != null && plugin.getPickaxeManager().isLegendaryPickaxe(draggedItem)) {
            // Vérifie tous les slots du drag
            for (int rawSlot : event.getRawSlots()) {
                // Si drag vers un inventaire externe
                if (event.getView().getInventory(rawSlot) != player.getInventory()) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Vous ne pouvez pas déplacer la pioche légendaire ici!");
                    plugin.getPluginLogger().debug("Drag de pioche vers inventaire externe bloqué");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Cherche la pioche légendaire dans les drops
        ItemStack pickaxeToSave = null;
        var drops = event.getDrops();

        for (ItemStack drop : new java.util.ArrayList<>(drops)) {
            if (plugin.getPickaxeManager().isLegendaryPickaxe(drop) &&
                    plugin.getPickaxeManager().isOwner(drop, player)) {

                pickaxeToSave = drop;
                drops.remove(drop); // Retire des drops
                break;
            }
        }

        // Remet la pioche dans l'inventaire du joueur après respawn
        if (pickaxeToSave != null) {
            final ItemStack finalPickaxe = pickaxeToSave;

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // Ajoute la pioche à l'inventaire du joueur
                var leftover = player.getInventory().addItem(finalPickaxe);

                // Si l'inventaire est plein, force l'ajout dans le premier slot libre
                if (!leftover.isEmpty()) {
                    int emptySlot = player.getInventory().firstEmpty();
                    if (emptySlot != -1) {
                        player.getInventory().setItem(emptySlot, finalPickaxe);
                    } else {
                        // Force le remplacement du premier slot
                        player.getInventory().setItem(0, finalPickaxe);
                    }
                }

                player.sendMessage("§a✅ Votre pioche légendaire a été récupérée!");
                plugin.getPickaxeManager().updatePlayerPickaxe(player);

            }, 1L); // 1 tick après la mort
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        var item = event.getItemDrop().getItemStack();

        if (plugin.getPickaxeManager().isLegendaryPickaxe(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c❌ Vous ne pouvez pas jeter la pioche légendaire!");
        }
    }

    // NOUVELLE PROTECTION: Échange main/off-hand
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if (plugin.getPickaxeManager().isLegendaryPickaxe(event.getMainHandItem()) ||
                plugin.getPickaxeManager().isLegendaryPickaxe(event.getOffHandItem())) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Action interdite avec la pioche légendaire!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        var item = event.getItem();

        if (item != null && plugin.getPickaxeManager().isLegendaryPickaxe(item)) {
            // Clic droit pour ouvrir le menu d'enchantements
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                    event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {

                // Évite d'ouvrir le menu si le joueur clique sur un bloc interactif
                if (event.getClickedBlock() != null &&
                        isInteractiveBlock(event.getClickedBlock().getType())) {
                    return;
                }

                event.setCancelled(true);
                plugin.getMainMenuGUI().openMainMenu(player);
            }

            // Shift + clic droit pour l'escalateur
            if (player.isSneaking() &&
                    (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                            event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {

                event.setCancelled(true);
                plugin.getPickaxeManager().handleEscalator(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String command = event.getMessage().toLowerCase();
        Player player = event.getPlayer();

        // Bloque les commandes dangereuses si le joueur a une pioche légendaire
        if (plugin.getPickaxeManager().hasLegendaryPickaxe(player)) {
            if (command.startsWith("/clear") ||
                    command.startsWith("/minecraft:clear") ||
                    command.contains("clear @") ||
                    command.startsWith("/give") && command.contains("netherite_pickaxe")) {

                // Vérifie les permissions admin
                if (!player.hasPermission("specialmine.admin")) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Cette commande est bloquée pour protéger votre pioche légendaire!");
                }
            }
        }
    }

    /**
     * Vérifie si un bloc est interactif (coffre, porte, etc.)
     */
    private boolean isInteractiveBlock(org.bukkit.Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL,
                 CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER,
                 BREWING_STAND, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON,
                 JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR, ACACIA_DOOR, DARK_OAK_DOOR,
                 CRIMSON_DOOR, WARPED_DOOR, IRON_DOOR -> true;
            default -> false;
        };
    }
}