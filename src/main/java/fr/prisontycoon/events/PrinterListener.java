package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.PrinterManager;
import fr.prisontycoon.managers.DepositBoxManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour les événements liés aux imprimantes et caisses de dépôt
 */
public class PrinterListener implements Listener {

    private final PrisonTycoon plugin;
    private final PrinterManager printerManager;
    private final DepositBoxManager depositBoxManager;

    public PrinterListener(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.printerManager = plugin.getPrinterManager();
        this.depositBoxManager = plugin.getDepositBoxManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack item = event.getItemInHand();

        // Vérifier si c'est une imprimante
        if (printerManager.isPrinter(item)) {
            handlePrinterPlace(event, player, block, item);
            return;
        }

        // Vérifier si c'est une caisse de dépôt
        if (depositBoxManager.isDepositBox(item)) {
            handleDepositBoxPlace(event, player, block, item);
            return;
        }
    }

    private void handlePrinterPlace(BlockPlaceEvent event, Player player, Block block, ItemStack item) {
        // Vérifier les permissions
        if (!player.hasPermission("specialmine.basic")) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous n'avez pas la permission de placer des imprimantes.");
            return;
        }

        // Vérifier si la location est sur une île
        if (!isLocationOnIsland(block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Les imprimantes doivent être placées sur une île.");
            return;
        }

        // Vérifier si le joueur peut placer une imprimante (slots individuels)
        if (!printerManager.canPlacePrinter(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous avez atteint votre limite d'imprimantes.");
            return;
        }

        // Vérifier les limites de l'île
        if (!printerManager.canPlacePrinter(player.getUniqueId(), block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Limite d'imprimantes de l'île atteinte.");
            return;
        }

        // Vérifier si le bloc est un dispenser
        if (block.getType() != Material.DISPENSER) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Les imprimantes doivent être placées comme des dispensers.");
            return;
        }

        // Créer l'imprimante
        int tier = printerManager.getPrinterTier(item);
        printerManager.createPrinter(player.getUniqueId(), block.getLocation(), tier);

        // Retirer un seul item de la main
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getAmount() > 1) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Messages de confirmation
        player.sendMessage("§a✅ Imprimante Tier " + tier + " placée avec succès !");
        player.sendMessage("§7Elle générera des billets toutes les X secondes quand un joueur sera sur l'île.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    private void handleDepositBoxPlace(BlockPlaceEvent event, Player player, Block block, ItemStack item) {
        // Vérifier les permissions
        if (!player.hasPermission("specialmine.basic")) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous n'avez pas la permission de placer des caisses de dépôt.");
            return;
        }

        // Vérifier si la location est sur une île
        if (!isLocationOnIsland(block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Les caisses de dépôt doivent être placées sur une île.");
            return;
        }

        // Vérifier les limites de l'île
        if (!depositBoxManager.canPlaceDepositBox(player.getUniqueId(), block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Limite de caisses de dépôt de l'île atteinte.");
            return;
        }

        // Vérifier si le bloc est un coffre
        if (block.getType() != Material.CHEST) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Les caisses de dépôt doivent être placées comme des coffres.");
            return;
        }

        // Créer la caisse de dépôt
        depositBoxManager.createDepositBox(player.getUniqueId(), block.getLocation());

        // Retirer un seul item de la main
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand.getAmount() > 1) {
            mainHand.setAmount(mainHand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Messages de confirmation
        player.sendMessage("§a✅ Caisse de dépôt placée avec succès !");
        player.sendMessage("§7Placez un hopper en dessous pour qu'elle traite automatiquement les billets.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location location = block.getLocation();

        // Vérifier si c'est une imprimante
        String printerId = printerManager.getPrinterLocations().get(location);
        if (printerId != null) {
            handlePrinterBreak(event, player, printerId);
            return;
        }

        // Vérifier si c'est une caisse de dépôt
        String depositBoxId = depositBoxManager.getDepositBoxLocations().get(location);
        if (depositBoxId != null) {
            handleDepositBoxBreak(event, player, depositBoxId);
            return;
        }
    }

    private void handlePrinterBreak(BlockBreakEvent event, Player player, String printerId) {
        // Vérifier les permissions
        if (!player.hasPermission("specialmine.basic")) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous n'avez pas la permission de casser des imprimantes.");
            return;
        }

        // Vérifier si le joueur peut casser cette imprimante
        if (!canBreakPrinter(player, printerId)) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous ne pouvez pas casser cette imprimante.");
            player.sendMessage("§7Seuls le propriétaire, le chef d'île et les officiers peuvent la retirer.");
            return;
        }

        // Supprimer l'imprimante
        printerManager.removePrinter(printerId);

        // Messages de confirmation
        player.sendMessage("§a✅ Imprimante retirée avec succès !");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);
    }

    private void handleDepositBoxBreak(BlockBreakEvent event, Player player, String depositBoxId) {
        // Vérifier les permissions
        if (!player.hasPermission("specialmine.basic")) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous n'avez pas la permission de casser des caisses de dépôt.");
            return;
        }

        // Vérifier si le joueur peut casser cette caisse de dépôt
        if (!canBreakDepositBox(player, depositBoxId)) {
            event.setCancelled(true);
            player.sendMessage("§c❌ Vous ne pouvez pas casser cette caisse de dépôt.");
            player.sendMessage("§7Seuls le propriétaire, le chef d'île et les officiers peuvent la retirer.");
            return;
        }

        // Supprimer la caisse de dépôt
        depositBoxManager.getDepositBoxCache().remove(depositBoxId);
        depositBoxManager.getDepositBoxLocations().remove(event.getBlock().getLocation());

        // Messages de confirmation
        player.sendMessage("§a✅ Caisse de dépôt retirée avec succès !");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f);
    }

    /**
     * Vérifie si un joueur peut casser une imprimante
     */
    private boolean canBreakPrinter(Player player, String printerId) {
        // Récupérer les données de l'imprimante via réflexion
        try {
            var skyblockPlugin = plugin.getServer().getPluginManager().getPlugin("CustomSkyblock");
            if (skyblockPlugin == null) return false;

            var skyblockPrinterManager = skyblockPlugin.getClass().getMethod("getPrinterManager").invoke(skyblockPlugin);
            var printerData = skyblockPrinterManager.getClass().getMethod("getPrinterById", String.class)
                    .invoke(skyblockPrinterManager, printerId);
            
            if (printerData == null) return false;

            // Le propriétaire peut toujours casser sa propre imprimante
            var owner = printerData.getClass().getMethod("getOwner").invoke(printerData);
            if (owner.equals(player.getUniqueId())) {
                return true;
            }

            // Vérifier si le joueur est chef d'île ou officier sur cette île
            var location = printerData.getClass().getMethod("getLocation").invoke(printerData);
            var islandManager = skyblockPlugin.getClass().getMethod("getIslandManager").invoke(skyblockPlugin);
            var island = islandManager.getClass().getMethod("getIslandAtLocation", Location.class)
                    .invoke(islandManager, location);
            
            if (island == null) return false;

            var islandOwner = island.getClass().getMethod("getOwner").invoke(island);
            var islandOfficers = island.getClass().getMethod("getOfficers").invoke(island);

            // Le chef d'île peut casser toutes les imprimantes
            if (islandOwner.equals(player.getUniqueId())) {
                return true;
            }

            // Les officiers peuvent casser toutes les imprimantes
            if (islandOfficers != null && islandOfficers instanceof java.util.Set) {
                if (((java.util.Set<?>) islandOfficers).contains(player.getUniqueId())) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la vérification des permissions d'imprimante: " + e.getMessage());
            return false;
        }
    }

    /**
     * Vérifie si un joueur peut casser une caisse de dépôt
     */
    private boolean canBreakDepositBox(Player player, String depositBoxId) {
        // Récupérer les données de la caisse de dépôt via réflexion
        try {
            var skyblockPlugin = plugin.getServer().getPluginManager().getPlugin("CustomSkyblock");
            if (skyblockPlugin == null) return false;

            var skyblockDepositBoxManager = skyblockPlugin.getClass().getMethod("getDepositBoxManager").invoke(skyblockPlugin);
            var depositBoxData = skyblockDepositBoxManager.getClass().getMethod("getDepositBoxById", String.class)
                    .invoke(skyblockDepositBoxManager, depositBoxId);
            
            if (depositBoxData == null) return false;

            // Le propriétaire peut toujours casser sa propre caisse de dépôt
            var owner = depositBoxData.getClass().getMethod("getOwner").invoke(depositBoxData);
            if (owner.equals(player.getUniqueId())) {
                return true;
            }

            // Vérifier si le joueur est chef d'île ou officier sur cette île
            var location = depositBoxData.getClass().getMethod("getLocation").invoke(depositBoxData);
            var islandManager = skyblockPlugin.getClass().getMethod("getIslandManager").invoke(skyblockPlugin);
            var island = islandManager.getClass().getMethod("getIslandAtLocation", Location.class)
                    .invoke(islandManager, location);
            
            if (island == null) return false;

            var islandOwner = island.getClass().getMethod("getOwner").invoke(island);
            var islandOfficers = island.getClass().getMethod("getOfficers").invoke(island);

            // Le chef d'île peut casser toutes les caisses de dépôt
            if (islandOwner.equals(player.getUniqueId())) {
                return true;
            }

            // Les officiers peuvent casser toutes les caisses de dépôt
            if (islandOfficers != null && islandOfficers instanceof java.util.Set) {
                if (((java.util.Set<?>) islandOfficers).contains(player.getUniqueId())) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la vérification des permissions de caisse de dépôt: " + e.getMessage());
            return false;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;

        // Vérifier si c'est une imprimante
        String printerId = printerManager.getPrinterLocations().get(block.getLocation());
        if (printerId != null) {
            handlePrinterInteract(event, player, printerId);
            return;
        }

        // Vérifier si c'est une caisse de dépôt
        String depositBoxId = depositBoxManager.getDepositBoxLocations().get(block.getLocation());
        if (depositBoxId != null) {
            handleDepositBoxInteract(event, player, depositBoxId);
            return;
        }
    }

    private void handlePrinterInteract(PlayerInteractEvent event, Player player, String printerId) {
        // Empêcher l'ouverture de l'inventaire du dispenser
        event.setCancelled(true);

        // Afficher les informations de l'imprimante
        try {
            var skyblockPlugin = plugin.getServer().getPluginManager().getPlugin("CustomSkyblock");
            if (skyblockPlugin != null) {
                var skyblockPrinterManager = skyblockPlugin.getClass().getMethod("getPrinterManager").invoke(skyblockPlugin);
                var printerData = skyblockPrinterManager.getClass().getMethod("getPrinterById", String.class)
                        .invoke(skyblockPrinterManager, printerId);
                
                if (printerData != null) {
                    var tier = printerData.getClass().getMethod("getTier").invoke(printerData);
                    
                    player.sendMessage("§6§l🖨️ IMPRIMANTE TIER " + tier);
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    player.sendMessage("§7Génère des billets toutes les X secondes");
                    player.sendMessage("§7quand un joueur est sur l'île.");
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                }
            }
        } catch (Exception e) {
            player.sendMessage("§6§l🖨️ IMPRIMANTE");
            player.sendMessage("§7Clic-droit pour voir les informations");
        }
        
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
    }

    private void handleDepositBoxInteract(PlayerInteractEvent event, Player player, String depositBoxId) {
        // Empêcher l'ouverture de l'inventaire du coffre
        event.setCancelled(true);

        // Ouvrir le GUI d'amélioration de la caisse de dépôt
        try {
            var skyblockPlugin = plugin.getServer().getPluginManager().getPlugin("CustomSkyblock");
            if (skyblockPlugin != null) {
                var skyblockDepositBoxManager = skyblockPlugin.getClass().getMethod("getDepositBoxManager").invoke(skyblockPlugin);
                var depositBoxData = skyblockDepositBoxManager.getClass().getMethod("getDepositBoxById", String.class)
                        .invoke(skyblockDepositBoxManager, depositBoxId);
                
                if (depositBoxData != null) {
                    // Convertir en DepositBoxData de PrisonTycoon pour le GUI
                    var owner = (java.util.UUID) depositBoxData.getClass().getMethod("getOwner").invoke(depositBoxData);
                    var location = (org.bukkit.Location) depositBoxData.getClass().getMethod("getLocation").invoke(depositBoxData);
                    var capacityLevel = (Integer) depositBoxData.getClass().getMethod("getCapacityLevel").invoke(depositBoxData);
                    var multiplierLevel = (Double) depositBoxData.getClass().getMethod("getMultiplierLevel").invoke(depositBoxData);
                    
                    var prisonTycoonDepositBox = new fr.prisontycoon.data.DepositBoxData(
                        depositBoxId, owner, location, capacityLevel, multiplierLevel,
                        System.currentTimeMillis(), 1000, capacityLevel * 2
                    );
                    
                    plugin.getDepositBoxUpgradeGUI().openUpgradeMenu(player, prisonTycoonDepositBox);
                    return;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de l'ouverture du GUI de caisse de dépôt: " + e.getMessage());
            plugin.getLogger().warning("Stack trace: " + e.toString());
            for (StackTraceElement element : e.getStackTrace()) {
                plugin.getLogger().warning("  at " + element.toString());
            }
        }

        // Fallback si erreur
        player.sendMessage("§6§l🏦 CAISSE DE DÉPÔT");
        player.sendMessage("§7Clic-droit pour voir les informations");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
    }

    /**
     * Vérifie si une location est sur une île
     */
    private boolean isLocationOnIsland(Location location) {
        try {
            var skyblockPlugin = plugin.getServer().getPluginManager().getPlugin("CustomSkyblock");
            if (skyblockPlugin == null) return false;

            var islandManager = skyblockPlugin.getClass().getMethod("getIslandManager").invoke(skyblockPlugin);
            var island = islandManager.getClass().getMethod("getIslandAtLocation", Location.class)
                    .invoke(islandManager, location);
            
            return island != null;
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la vérification de l'île: " + e.getMessage());
            return false;
        }
    }
}
