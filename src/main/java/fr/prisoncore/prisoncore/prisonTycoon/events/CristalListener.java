package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.cristaux.Cristal;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour les interactions avec les cristaux
 */
public class CristalListener implements Listener {

    private final PrisonTycoon plugin;

    public CristalListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère les clics-droit sur les cristaux pour les révéler
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !plugin.getCristalManager().isCristal(item)) {
            return;
        }

        // Vérifie que c'est un clic-droit
        if (!event.getAction().name().contains("RIGHT_CLICK")) {
            return;
        }

        Cristal cristal = plugin.getCristalManager().getCristalFromItem(item);
        if (cristal == null || !cristal.isVierge()) {
            return;
        }

        event.setCancelled(true);

        // Révélation du cristal
        ItemStack revealedItem = plugin.getCristalManager().revealCristal(item);
        Cristal revealedCristal = plugin.getCristalManager().getCristalFromItem(revealedItem);

        if (revealedCristal != null) {
            // Remplace l'item dans l'inventaire
            if (event.getHand() != null) {
                player.getInventory().setItemInMainHand(revealedItem);
            }

            // Messages et effets
            player.sendMessage("§d✨ §lRévélation du cristal!");
            player.sendMessage("§7Votre cristal vierge s'est transformé en:");
            player.sendMessage("§d✨ Cristal " + revealedCristal.getType().getDisplayName() +
                    " §7(Niveau " + revealedCristal.getNiveau() + ")");
            player.sendMessage("§a" + revealedCristal.getType().getBonusDescription(revealedCristal.getNiveau()));

            // Effets sonores et visuels
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

            plugin.getPluginLogger().info("§7Cristal révélé pour " + player.getName() + ": " +
                    revealedCristal.getType().getDisplayName() + " niv." + revealedCristal.getNiveau());
        }
    }
}