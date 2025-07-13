package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour la protection de la pioche légendaire
 * CORRIGÉ : Protection totale, pioche IMMOBILE dans le slot 0
 */
public class PickaxeProtectionListener implements Listener {

    private final PrisonTycoon plugin;

    public PickaxeProtectionListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    // NOUVEAU : Surveille quand le joueur change d'item en main pour gérer les effets mobilité
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        // Délai pour que l'item soit changé
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Met à jour les effets de mobilité selon si la pioche est au slot 0
            plugin.getPickaxeManager().updateMobilityEffects(player);
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // NOUVEAU : Ne pas bloquer les clics molette dans les GUIs du plugin pour les enchants mobilité
        if (event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE) {
            String title = event.getView().getTitle();
            if (title.contains("Mobilité")) {
                // Laisse passer le clic molette pour les enchants mobilité
                return;
            }
        }

        var currentItem = event.getCurrentItem();
        var cursor = event.getCursor();

        // NOUVEAU : Protection spéciale pour le slot 0 (pioche immobile)
        if (event.getClickedInventory() == player.getInventory()) {
            int clickedSlot = event.getSlot();

            // Si clic sur slot 0 et contient une pioche légendaire
            if (clickedSlot == 0 && currentItem != null &&
                    plugin.getPickaxeManager().isLegendaryPickaxe(currentItem)) {

                // BLOQUE TOUS LES CLICS sur la pioche dans le slot 0
                event.setCancelled(true);
                player.sendMessage("§c❌ La pioche légendaire ne peut pas être déplacée du slot 1!");
                plugin.getPluginLogger().debug("Tentative de déplacement de pioche slot 0 bloquée");
                return;
            }

            // Si tentative de placer quelque chose dans le slot 0 et il y a une pioche
            if (clickedSlot == 0 && cursor != null && cursor.getType() != org.bukkit.Material.AIR) {
                ItemStack slot0Item = player.getInventory().getItem(0);
                if (slot0Item != null && plugin.getPickaxeManager().isLegendaryPickaxe(slot0Item)) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Le slot 1 est réservé à la pioche légendaire!");
                    plugin.getPluginLogger().debug("Tentative de placement item slot 0 avec pioche bloquée");
                    return;
                }
            }

            // Protection touches numériques avec slot 0
            if (event.getClick().isKeyboardClick() && event.getHotbarButton() == 0) {
                ItemStack slot0Item = player.getInventory().getItem(0);
                if (slot0Item != null && plugin.getPickaxeManager().isLegendaryPickaxe(slot0Item)) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ La pioche légendaire ne peut pas être échangée!");
                    plugin.getPluginLogger().debug("Échange touche numérique slot 0 avec pioche bloqué");
                    return;
                }
            }
        }

        // Vérifie l'item cliqué (pioche dans l'inventaire)
        if (currentItem != null && plugin.getPickaxeManager().isLegendaryPickaxe(currentItem)) {

            // CORRECTION : Bloque TOUTES les interactions hors inventaire principal du joueur
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

            // NOUVEAU : Bloque AUSSI les mouvements à l'intérieur de l'inventaire du joueur
            // sauf si la pioche va au slot 0
            if (event.getClickedInventory() == player.getInventory()) {
                int clickedSlot = event.getSlot();

                // Si la pioche n'est pas au slot 0 et qu'on ne la met pas au slot 0
                if (clickedSlot != 0) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ La pioche légendaire doit rester dans le slot 1!");

                    // Force le retour au slot 0
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        plugin.getPickaxeManager().enforcePickaxeSlot(player);
                    }, 1L);

                    plugin.getPluginLogger().debug("Mouvement pioche hors slot 0 bloqué");
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

            // NOUVEAU : Force le placement uniquement au slot 0
            if (event.getClickedInventory() == player.getInventory() && event.getSlot() != 0) {
                event.setCancelled(true);
                player.sendMessage("§c❌ La pioche légendaire ne peut aller que dans le slot 1!");
                plugin.getPluginLogger().debug("Placement pioche hors slot 0 bloqué");
                return;
            }
        }

        // NOUVELLE VÉRIFICATION : Touches numériques pour échanger avec hotbar
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

                // NOUVEAU : Si drag vers un slot autre que 0 dans l'inventaire du joueur
                if (event.getView().getInventory(rawSlot) == player.getInventory()) {
                    int slot = event.getView().convertSlot(rawSlot);
                    if (slot != 0) {
                        event.setCancelled(true);
                        player.sendMessage("§c❌ La pioche légendaire ne peut aller que dans le slot 1!");
                        plugin.getPluginLogger().debug("Drag pioche hors slot 0 bloqué");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // NOUVELLE RÈGLE : Le joueur ne perd pas son exp vanilla
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        player.sendMessage("§a✅ Votre expérience vanilla a été conservée!");

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

        // NOUVELLE RÈGLE : Si le joueur a une pioche légendaire, elle perd 5% de durabilité
        if (pickaxeToSave != null) {
            applyDeathDurabilityPenalty(pickaxeToSave, player);
        }

        // Remet la pioche dans le slot 0 après respawn
        if (pickaxeToSave != null) {
            final ItemStack finalPickaxe = pickaxeToSave;

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                // NOUVEAU : Force le placement au slot 0
                ItemStack existingSlot0 = player.getInventory().getItem(0);

                // Place la pioche au slot 0
                player.getInventory().setItem(0, finalPickaxe);

                // Si il y avait un item au slot 0, essaie de le replacer
                if (existingSlot0 != null && existingSlot0.getType() != org.bukkit.Material.AIR) {
                    var leftover = player.getInventory().addItem(existingSlot0);
                    if (!leftover.isEmpty()) {
                        for (ItemStack overflow : leftover.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                        }
                    }
                }

                player.sendMessage("§a✅ Votre pioche légendaire a été récupérée dans le slot 1!");
                plugin.getPickaxeManager().updatePlayerPickaxe(player);

            }, 1L); // 1 tick après la mort
        }
    }

    /**
     * NOUVELLE MÉTHODE : Applique la pénalité de durabilité de 5% à la mort
     */
    private void applyDeathDurabilityPenalty(ItemStack pickaxe, Player player) {
        if (!plugin.getPickaxeManager().isLegendaryPickaxe(pickaxe)) return;

        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();

        // Calcule 5% de la durabilité maximale
        int durabilityPenalty = (int) Math.ceil(maxDurability * 0.05);

        // Applique la pénalité
        int newDurability = Math.min(currentDurability + durabilityPenalty, maxDurability - 1);
        pickaxe.setDurability((short) newDurability);

        // Vérifie les enchantements de solidité pour ajuster la durabilité
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");

        if (durabilityLevel > 0) {
            // Calcule la durabilité bonus avec l'enchantement Solidité
            double durabilityBonus = durabilityLevel * 10.0; // +10% par niveau
            double durabilityMultiplier = 1.0 + (durabilityBonus / 100.0);
            int maxDurabilityWithBonus = (int) (maxDurability * durabilityMultiplier);

            // S'assure que la durabilité ne dépasse pas la limite avec bonus
            if (newDurability >= maxDurabilityWithBonus) {
                pickaxe.setDurability((short) (maxDurabilityWithBonus - 1));
            }
        }

        // Affiche le message de pénalité
        double percentageLost = (durabilityPenalty / (double) maxDurability) * 100;
        player.sendMessage("§c⚠️ Votre pioche a perdu " + String.format("%.1f%%", percentageLost) +
                " de durabilité due à votre mort (" + durabilityPenalty + " points)");

        plugin.getPluginLogger().debug("Pénalité de mort appliquée à la pioche de " + player.getName() +
                ": -" + durabilityPenalty + " durabilité (" + String.format("%.1f%%", percentageLost) + ")");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        var item = event.getItemDrop().getItemStack();

        if (plugin.getPickaxeManager().isLegendaryPickaxe(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c❌ Vous ne pouvez pas jeter la pioche légendaire!");
        }
    }

    // NOUVELLE PROTECTION : Échange main/off-hand
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
            // Vérifie que la pioche est au bon endroit
            if (!plugin.getPickaxeManager().isPickaxeInCorrectSlot(player)) {
                event.setCancelled(true);
                player.sendMessage("§c❌ La pioche légendaire doit être dans le slot 1 pour fonctionner!");
                plugin.getPickaxeManager().enforcePickaxeSlot(player);
                return;
            }

            // Clic droit pour ouvrir le menu d'enchantements
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                    event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {

                // Évite d'ouvrir le menu si le joueur clique sur un bloc interactif
                if (event.getClickedBlock() != null &&
                        isInteractiveBlock(event.getClickedBlock().getType())) {
                    return;
                }

                event.setCancelled(true);
                plugin.getMainMenuGUI().openEnchantmentMenu(player);
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
                } else {
                    // Même pour les admins, s'assure que la pioche reste au slot 0
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        plugin.getPickaxeManager().enforcePickaxeSlot(player);
                    }, 5L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemFramePlace(PlayerItemFrameChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemStack();

        if (item != null && plugin.getPickaxeManager().isLegendaryPickaxe(item)) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous ne pouvez pas placer la pioche légendaire dans un cadre!");
            plugin.getPluginLogger().debug("Tentative de placement pioche dans item frame bloquée: " + player.getName());
        }
    }

    /**
     * NOUVEAU : Protège contre placement sur armor stand
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        ItemStack playerItem = event.getPlayerItem();

        if (playerItem != null && plugin.getPickaxeManager().isLegendaryPickaxe(playerItem)) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous ne pouvez pas équiper la pioche légendaire sur un armor stand!");
            plugin.getPluginLogger().debug("Tentative d'équipement pioche sur armor stand bloquée: " + player.getName());
        }
    }

    /**
     * NOUVEAU : Protège contre placement dans des structures suspendues
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getEntity() instanceof ItemFrame) {
            // Vérifie si le joueur a une pioche légendaire en main
            if (event.getPlayer() != null) {
                Player player = event.getPlayer();
                ItemStack handItem = player.getInventory().getItemInMainHand();

                if (plugin.getPickaxeManager().isLegendaryPickaxe(handItem)) {
                    event.setCancelled(true);
                    player.sendMessage("§c❌ Vous ne pouvez pas placer d'item frame avec la pioche légendaire en main!");
                }
            }
        }
    }

    /**
     * NOUVEAU : Protège contre placement via shift+clic dans des conteneurs spéciaux
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClickExtended(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        var currentItem = event.getCurrentItem();
        var cursor = event.getCursor();

        // Vérifie placement dans des inventaires d'entités
        if (event.getClickedInventory() != null) {
            String inventoryTitle = event.getView().getTitle().toLowerCase();

            // Bloque placement dans des inventaires spéciaux
            if (inventoryTitle.contains("armor stand") ||
                    inventoryTitle.contains("item frame") ||
                    inventoryTitle.contains("display")) {

                if ((currentItem != null && plugin.getPickaxeManager().isLegendaryPickaxe(currentItem)) ||
                        (cursor != null && plugin.getPickaxeManager().isLegendaryPickaxe(cursor))) {

                    event.setCancelled(true);
                    player.sendMessage("§c❌ Vous ne pouvez pas placer la pioche légendaire ici!");
                    plugin.getPluginLogger().debug("Tentative de placement pioche dans inventaire spécial bloquée: " + player.getName());
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