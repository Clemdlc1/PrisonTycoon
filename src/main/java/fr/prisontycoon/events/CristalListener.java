package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.cristaux.Cristal;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Vérifier que c'est le menu de fusion qui se ferme
        if (!event.getView().getTitle().equals("§6⚡ Fusion de Cristaux ⚡")) return;

        Inventory fusionInv = event.getInventory();
        List<ItemStack> cristalsInMenu = new ArrayList<>();
        List<Cristal> cristalsForFusion = new ArrayList<>();

        // Récupérer tous les cristaux dans le menu
        for (int i = 0; i < 9; i++) {
            ItemStack item = fusionInv.getItem(i);
            if (item != null && plugin.getCristalManager().isCristal(item)) {
                Cristal cristal = plugin.getCristalManager().getCristalFromItem(item);
                if (cristal != null) {
                    cristalsInMenu.add(item);
                    cristalsForFusion.add(cristal);
                }
            }
        }

        // Vérifier les conditions de fusion
        if (cristalsForFusion.size() == 9) {
            // Vérifier que tous les cristaux sont du même niveau et type (si révélés)
            boolean canFuse = true;
            int baseLevel = cristalsForFusion.get(0).getNiveau();

            for (Cristal cristal : cristalsForFusion) {
                if (cristal.getNiveau() != baseLevel) {
                    canFuse = false;
                    break;
                }
            }

            if (canFuse && baseLevel < 20) {
                // Fusion réussie - créer un cristal vierge de niveau supérieur
                int nouveauNiveau = baseLevel + 1;
                Cristal fusedCristal = plugin.getCristalManager().createCristalVierge(nouveauNiveau);
                ItemStack fusedItem = fusedCristal.toItemStack(
                        plugin.getCristalManager().getCristalUuidKey(),
                        plugin.getCristalManager().getCristalLevelKey(),
                        plugin.getCristalManager().getCristalTypeKey(),
                        plugin.getCristalManager().getCristalViergeKey()
                );

                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(fusedItem);
                    player.sendMessage("§a✨ Fusion réussie! Vous avez obtenu un cristal vierge niveau " + nouveauNiveau + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    return; // Pas besoin de rendre les cristaux
                } else {
                    player.sendMessage("§cInventaire plein! Fusion annulée.");
                }
            } else if (baseLevel >= 20) {
                player.sendMessage("§cImpossible de fusionner des cristaux de niveau 20!");
            } else {
                player.sendMessage("§cTous les cristaux doivent être du même niveau et type pour la fusion!");
            }
        }

        // Rendre tous les cristaux au joueur
        for (ItemStack item : cristalsInMenu) {
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(item);
            } else {
                // Inventaire plein, jeter les items au sol
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

}