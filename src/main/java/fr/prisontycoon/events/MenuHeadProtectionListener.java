package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

/**
 * Protège la tête "Menu Principal" (slot 9) contre les pertes/déplacements.
 */
public class MenuHeadProtectionListener implements Listener {

    private final PrisonTycoon plugin;

    public MenuHeadProtectionListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    private boolean isMenuHead(ItemStack item) {
        return plugin.getGUIManager().isMainMenuHead(item);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Si clic dans l'inventaire du joueur
        if (event.getClickedInventory() == player.getInventory()) {
            int clickedSlot = event.getSlot();

            // Bloque les actions sur la tête si elle est dans le bon slot (8)
            if (clickedSlot == 8 && current != null && isMenuHead(current)) {
                event.setCancelled(true);
                return;
            }

            // Empêche de placer un item par-dessus la tête au slot 8
            if (clickedSlot == 8 && cursor != null && cursor.getType() != Material.AIR) {
                ItemStack slot8 = player.getInventory().getItem(8);
                if (slot8 != null && isMenuHead(slot8)) {
                    event.setCancelled(true);
                    return;
                }
            }

            // Si on manipule la tête ailleurs que le slot 8, annule et rétablit
            if (current != null && isMenuHead(current) && clickedSlot != 8) {
                event.setCancelled(true);
                // Replace correctement après le tick
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getGUIManager().giveMainMenuHead(player));
                return;
            }

            // Protection touches numériques avec slot 8
            if (event.getClick().isKeyboardClick() && event.getHotbarButton() == 8) {
                ItemStack slot8 = player.getInventory().getItem(8);
                if (slot8 != null && isMenuHead(slot8)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Empêche tout déplacement vers inventaires externes
        if (current != null && isMenuHead(current)) {
            if (event.getClickedInventory() != player.getInventory()) {
                event.setCancelled(true);
                return;
            }

            // Bloque le shift+clic vers le haut
            if (event.isShiftClick()) {
                if (event.getView().getTopInventory() != player.getInventory() &&
                        event.getView().getTopInventory().getType() != InventoryType.CRAFTING) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        // Empêche de tenir la tête sur le curseur et de cliquer autre part que slot 8
        if (cursor != null && isMenuHead(cursor)) {
            if (event.getClickedInventory() != player.getInventory() || event.getSlot() != 8) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        ItemStack dragged = event.getOldCursor();
        if (dragged != null && isMenuHead(dragged)) {
            // Bloque tout drag de cet item
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (isMenuHead(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isMenuHead(event.getMainHandItem()) || isMenuHead(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        var iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (isMenuHead(drop)) {
                event.getItemsToKeep().add(drop);
                iterator.remove();
                // Rétablit en slot 8 au prochain tick
                plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getGUIManager().giveMainMenuHead(player));
                break;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemFramePlace(PlayerItemFrameChangeEvent event) {
        if (isMenuHead(event.getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (isMenuHead(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() == null) return;
        ItemStack inHand = event.getPlayer().getInventory().getItemInMainHand();
        if (isMenuHead(inHand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        var item = event.getItem();
        var player = event.getPlayer();
        if (item == null) return;

        if (plugin.getGUIManager().isMainMenuHead(item)) {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                // Évite d'interagir avec des blocs interactifs
                if (event.getClickedBlock() != null) {
                    org.bukkit.Material m = event.getClickedBlock().getType();
                    if (isInteractiveBlock(m)) return;
                }
                event.setCancelled(true);
                plugin.getMainNavigationGUI().openMainMenu(player);
            }
        }
    }

    private boolean isInteractiveBlock(org.bukkit.Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL,
                 CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER,
                 BREWING_STAND, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON,
                 JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR, ACACIA_DOOR, DARK_OAK_DOOR,
                 CRIMSON_DOOR, WARPED_DOOR, IRON_DOOR, SHULKER_BOX, ITEM_FRAME -> true;
            default -> false;
        };
    }

}


