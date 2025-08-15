package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.DepositBoxManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour optimiser l'intégration des hoppers avec les caisses de dépôt
 */
public class HopperIntegrationListener implements Listener {

    private final PrisonTycoon plugin;
    private final DepositBoxManager depositBoxManager;

    public HopperIntegrationListener(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.depositBoxManager = plugin.getDepositBoxManager();
    }

    /**
     * Gestionnaire pour les transferts d'items entre inventaires
     * Optimise les transfers vers les caisses de dépôt
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Vérifier si c'est un transfert depuis un hopper vers un coffre
        if (!(event.getSource().getHolder() instanceof Hopper)) return;
        if (event.getDestination().getLocation() == null) return;
        
        Location chestLocation = event.getDestination().getLocation();
        Block chestBlock = chestLocation.getBlock();
        
        if (chestBlock.getType() != Material.CHEST) return;
        
        // Vérifier si le coffre est une caisse de dépôt
        String depositBoxId = depositBoxManager.getDepositBoxIdAtLocation(chestLocation);
        if (depositBoxId == null) return;
        
        ItemStack item = event.getItem();
        
        // Si c'est un billet, empêcher le transfer normal et traiter immédiatement
        if (depositBoxManager.isBill(item)) {
            event.setCancelled(true);
            
            // Traiter immédiatement le billet
            processImmediateBillTransfer(depositBoxId, item, (Hopper) event.getSource().getHolder());
        }
    }

    /**
     * Traite immédiatement un billet transféré par hopper
     */
    private void processImmediateBillTransfer(String depositBoxId, ItemStack bill, Hopper sourceHopper) {
        var depositBox = depositBoxManager.getDepositBoxById(depositBoxId);
        if (depositBox == null) return;
        
        // Obtenir la quantité réelle du billet (stack size)
        int stackSize = depositBoxManager.getBillStackSize(bill);
        java.math.BigInteger billValue = depositBoxManager.getBillValue(bill);
        
        // Calculer la valeur avec le multiplicateur de la caisse de dépôt
        double multiplier = 1.0 + (depositBox.getMultiplierLevel() * 0.1); // 10% par niveau
        java.math.BigInteger totalValue = billValue.multiply(java.math.BigInteger.valueOf(stackSize))
            .multiply(java.math.BigInteger.valueOf((long) (multiplier * 100)))
            .divide(java.math.BigInteger.valueOf(100));
        
        // Appliquer le bonus de vente du joueur si connecté
        org.bukkit.entity.Player player = plugin.getServer().getPlayer(depositBox.getOwner());
        if (player != null) {
            double salesMultiplier = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, 
                fr.prisontycoon.managers.GlobalBonusManager.BonusCategory.SELL_BONUS);
            totalValue = totalValue.multiply(java.math.BigInteger.valueOf((long) (salesMultiplier * 100)))
                .divide(java.math.BigInteger.valueOf(100));
        }
        
        // Donner l'argent au propriétaire
        var playerData = plugin.getPlayerDataManager().getPlayerData(depositBox.getOwner());
        playerData.addCoins(totalValue.longValue());
        plugin.getPlayerDataManager().markDirty(depositBox.getOwner());
        
        // Retirer l'item du hopper source
        for (int i = 0; i < sourceHopper.getInventory().getSize(); i++) {
            ItemStack hopperItem = sourceHopper.getInventory().getItem(i);
            if (hopperItem != null && hopperItem.isSimilar(bill)) {
                sourceHopper.getInventory().setItem(i, null);
                break;
            }
        }
        
        // Effets visuels et sonores
        Location location = depositBox.getLocation();
        location.getWorld().playSound(location, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        location.getWorld().spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, 
            location.add(0.5, 1, 0.5), 5, 0.3, 0.3, 0.3, 0);
        
        // Notifier le joueur s'il est en ligne
        if (player != null) {
            player.sendMessage("§a💰 §lCaisse de dépôt: §6+" + 
                fr.prisontycoon.utils.NumberFormatter.format(totalValue.longValue()) + " coins");
        }
    }
}