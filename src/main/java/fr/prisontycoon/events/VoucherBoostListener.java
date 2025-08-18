package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Gestionnaire des événements pour les vouchers et boosts
 */
public class VoucherBoostListener implements Listener {

    private final PrisonTycoon plugin;

    public VoucherBoostListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère les clics droits sur les vouchers et boosts
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Vérifie que c'est un clic droit
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        boolean handled = false;

        // Vérifie si c'est un voucher
        if (plugin.getVoucherManager().isVoucher(item)) {
            handled = plugin.getVoucherManager().useVoucher(event.getPlayer(), item);
        }
        // Vérifie si c'est un boost
        else if (plugin.getBoostManager().isBoostItem(item)) {
            handled = plugin.getBoostManager().activateBoost(event.getPlayer(), item);
        }
        // Vérifie si c'est une boîte de pet
        else if (plugin.getPetService().isPetBox(item)) {
            int tier = plugin.getPetService().getPetBoxTier(item);
            var def = plugin.getPetService().openPetBox(event.getPlayer(), tier);
            if (def != null) {
                // Consommer l'item
                var p = event.getPlayer();
                var hand = p.getInventory().getItemInMainHand();
                if (hand != null && hand.isSimilar(item)) {
                    hand.setAmount(Math.max(0, hand.getAmount() - 1));
                    p.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
                } else {
                    item.setAmount(Math.max(0, item.getAmount() - 1));
                }
                handled = true;
            }
        }

        // Annule l'événement si un voucher ou boost a été utilisé
        if (handled) {
            event.setCancelled(true);
        }
    }
}