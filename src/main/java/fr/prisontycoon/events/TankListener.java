package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.TankData;
import fr.prisontycoon.gui.TankGUI;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Gestionnaire des événements pour les Tanks
 */
public class TankListener implements Listener {

    private final PrisonTycoon plugin;
    private final TankGUI tankGUI;

    public TankListener(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.tankGUI = new TankGUI(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        // Vérifier si l'item placé est un Tank
        if (!plugin.getTankManager().isTank(item)) return;

        Location location = event.getBlock().getLocation();

        // Placer le tank
        if (plugin.getTankManager().placeTank(location, item, player)) {
            // Le tank a été placé avec succès, l'event peut continuer
            // (le bloc BARREL a déjà été placé par placeTank)
        } else {
            // Échec du placement
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        // Vérifier si le bloc cassé est un Tank
        if (!plugin.getTankManager().isTankBlock(location)) return;

        event.setCancelled(true); // Annuler la casse normale

        // Récupérer le tank
        ItemStack tankItem = plugin.getTankManager().breakTank(location, player);
        if (tankItem != null) {
            // Casser le bloc
            location.getBlock().setType(Material.AIR);

            // Donner le tank au joueur ou le faire tomber
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(tankItem);
            } else {
                location.getWorld().dropItemNaturally(location, tankItem);
                player.sendMessage("§7Tank récupéré au sol (inventaire plein)");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Vérifier si l'événement concerne la main principale
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        Action action = event.getAction();
        // Interaction avec un Tank placé
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null && !plugin.getSellHandManager().isSellHand(mainHand)) {
            Location location = event.getClickedBlock().getLocation();

            if (plugin.getTankManager().isTankBlock(location)) {
                event.setCancelled(true);
                handleTankBlockInteraction(player, location);
                return;
            }
        }

        // Interaction avec un Tank en main
        ItemStack item = event.getItem();
        if (item != null && plugin.getTankManager().isTank(item)) {

            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                if (player.isSneaking()) {
                    event.setCancelled(true);

                    // Shift + Clic droit = Configurer le tank (seulement si pas placé)
                    handleTankItemConfiguration(player, item);
                }
                // On retire la vente directe - seul le Sell Hand peut vendre
            }
        }

        // Vérifier si l'événement concerne la main principale
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Vérifier si l'item en main principale est un Sell Hand
        if (mainHand == null || !plugin.getSellHandManager().isSellHand(mainHand)) return;

        player.sendMessage("4");

        // Vérifier si c'est un shift + clic sur un bloc
        if ((action == Action.RIGHT_CLICK_BLOCK) && player.isSneaking() && event.getClickedBlock() != null) {
            Location location = event.getClickedBlock().getLocation();

            // Vérifier si le bloc cliqué est un Tank
            if (!plugin.getTankManager().isTankBlock(location)) {
                player.sendMessage("§c❌ Vous devez faire shift + clic droit sur un Tank placé!");
                player.sendMessage("§7💡 Astuce: Placez un tank au sol et faites shift + clic dessus avec le Sell Hand");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            event.setCancelled(true);

            // Récupérer les données du tank
            TankData tankData = plugin.getTankManager().getTankAt(location);
            if (tankData == null) {
                player.sendMessage("§c❌ Erreur: Impossible de lire les données du tank!");
                return;
            }

            // Vérifier que le joueur est le propriétaire du tank
            if (!tankData.getOwner().equals(player.getUniqueId())) {
                player.sendMessage("§c❌ Vous ne pouvez vendre que le contenu de vos propres tanks!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Utiliser le Sell Hand sur le Tank
            boolean success = plugin.getSellHandManager().useSellHandOnTank(player, mainHand, tankData);

            if (success) {
                // Mettre à jour le nametag du tank
                plugin.getTankManager().updateTankNameTag(tankData);
            }
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // Clic normal sans shift - donner des instructions
            if (!player.isSneaking()) {
                event.setCancelled(true);
                player.sendMessage("§e💡 Comment utiliser le Sell Hand:");
                player.sendMessage("§7▸ Placez un de vos tanks au sol");
                player.sendMessage("§7▸ Tenez le Sell Hand en main");
                player.sendMessage("§7▸ Faites §eShift + Clic droit§7 sur le tank");
                player.sendMessage("§7▸ Le contenu du tank sera vendu au serveur!");
            }
        }
    }

    /**
     * Gère l'interaction avec un Tank placé au sol
     */
    private void handleTankBlockInteraction(Player player, Location location) {
        TankData tankData = plugin.getTankManager().getTankAt(location);
        if (tankData == null) {
            player.sendMessage("§c❌ Erreur: Impossible de lire les données du tank!");
            return;
        }

        boolean isOwner = tankData.getOwner().equals(player.getUniqueId());

        if (player.isSneaking()) {
            // Shift + Clic droit = Configurer (propriétaire seulement)
            if (isOwner) {
                tankGUI.openTankGUI(player, tankData.getId());
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            } else {
                player.sendMessage("§c❌ Vous n'êtes pas le propriétaire de ce tank!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        } else {
            // Clic simple
            if (isOwner) {
                // Propriétaire = Vendre des items
                handleSellToTank(player, location);
            } else {
                // Autre joueur = Voir les prix ou vendre
                if (tankData.getPrices().isEmpty()) {
                    player.sendMessage("§c❌ Ce tank n'achète aucun item pour le moment!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                } else {
                    // Essayer de vendre d'abord
                    if (!handleSellToTank(player, location)) {
                        // Si rien à vendre, afficher les prix
                        tankGUI.openPricesViewGUI(player, tankData);
                        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    /**
     * Gère la configuration d'un Tank en main
     */
    private void handleTankItemConfiguration(Player player, ItemStack tankItem) {
        TankData tankData = plugin.getTankManager().getTankData(tankItem);
        if (tankData == null) {
            player.sendMessage("§c❌ Erreur: Impossible de lire les données du tank!");
            return;
        }

        // Vérifier que le joueur est le propriétaire
        if (!tankData.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Vous n'êtes pas le propriétaire de ce tank!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Ouvrir la GUI de configuration
        tankGUI.openTankGUI(player, tankData.getId());
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    /**
     * Gère la vente au Tank placé
     */
    private boolean handleSellToTank(Player player, Location tankLocation) {
        TankData tankData = plugin.getTankManager().getTankAt(tankLocation);
        if (tankData == null) return false;

        Inventory inventory = player.getInventory();
        boolean soldSomething = false;
        int totalItemsSold = 0;
        long totalMoneyEarned = 0;

        // Parcourir l'inventaire pour trouver des items compatibles
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;
            if (plugin.getTankManager().isTank(item)) continue; // Ignorer les autres tanks
            if (plugin.getPickaxeManager() != null && plugin.getPickaxeManager().isLegendaryPickaxe(item)) continue;
            if (plugin.getSellHandManager() != null && plugin.getSellHandManager().isSellHand(item))
                continue; // Ignorer les sell hands

            Material material = item.getType();

            // Vérifier si le tank accepte ce matériau
            if (!tankData.acceptsMaterial(material)) continue;
            if (!tankData.hasPriceFor(material)) continue;

            int amount = item.getAmount();
            long pricePerItem = tankData.getPrice(material);
            long totalPrice = pricePerItem * amount;

            // Vérifier que le propriétaire a assez d'argent (placeholder)
            // Vérifier la capacité
            if (!tankData.canAddItems(amount)) continue;

            // Effectuer la transaction
            if (tankData.addItems(material, amount)) {
                // Transactions économiques (placeholder - adapter selon votre système)
                // plugin.getEconomyManager().transferMoney(tankData.getOwner(), player, totalPrice);

                // Retirer l'item de l'inventaire
                inventory.setItem(i, null);

                soldSomething = true;
                totalItemsSold += amount;
                totalMoneyEarned += totalPrice;
            }
        }

        // Vendre depuis les conteneurs du joueur aussi
        if (plugin.getContainerManager() != null) {
            var playerContainers = plugin.getContainerManager().getPlayerContainers(player);

            for (var containerData : playerContainers) {
                if (containerData.isBroken()) continue;

                var contents = new java.util.HashMap<>(containerData.getContents());
                for (var entry : contents.entrySet()) {
                    Material material = entry.getKey().getType();
                    int amount = entry.getValue();

                    // Vérifier si le tank accepte ce matériau
                    if (!tankData.acceptsMaterial(material)) continue;
                    if (!tankData.hasPriceFor(material)) continue;

                    long pricePerItem = tankData.getPrice(material);
                    long totalPrice = pricePerItem * amount;

                    // Vérifier la capacité
                    if (!tankData.canAddItems(amount)) continue;

                    // Effectuer la transaction
                    if (tankData.addItems(material, amount)) {
                        // Transactions économiques (placeholder)
                        // plugin.getEconomyManager().transferMoney(tankData.getOwner(), player, totalPrice);

                        // Retirer l'item du conteneur
                        containerData.removeItem(entry.getKey(), amount);

                        soldSomething = true;
                        totalItemsSold += amount;
                        totalMoneyEarned += totalPrice;
                    }
                }
            }
        }

        if (soldSomething) {
            player.sendMessage("§a✓ Vendu " + totalItemsSold + " items pour " +
                    NumberFormatter.format(totalMoneyEarned) + "$!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

            // Sauvegarder et mettre à jour le nametag
            plugin.getTankManager().saveTank(tankData);
            plugin.getTankManager().updateTankNameTag(tankData);

            return true;
        } else {
            // Ne pas afficher de message ici - sera géré par l'appelant
            return false;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        // Vérifier si on essaie de faire un shift-clic sur un Tank dans l'inventaire
        if (event.isShiftClick() && clicked != null && plugin.getTankManager().isTank(clicked)) {
            // Les tanks ne peuvent plus être utilisés directement depuis l'inventaire
            // Ils doivent être placés au sol
            player.sendMessage("§c❌ Vous devez placer le tank au sol pour l'utiliser!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            event.setCancelled(true);
            return;
        }

        // Empêcher de mettre des items dans un Tank en drag&drop
        if (cursor != null && cursor.getType() != Material.AIR && clicked != null &&
                plugin.getTankManager().isTank(clicked)) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Placez le tank au sol et utilisez-le là-bas!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
}