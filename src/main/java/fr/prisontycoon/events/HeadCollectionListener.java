package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Listener pour le système de collection de têtes
 * Gère le placement, la casse et les interactions avec les têtes
 */
public class HeadCollectionListener implements Listener {

    private final PrisonTycoon plugin;
    private boolean setupMode = false; // Mode configuration

    public HeadCollectionListener(PrisonTycoon plugin) {
        this.plugin = plugin;
        plugin.getPluginLogger().info("§aHeadCollectionListener initialisé.");
    }

    /**
     * Détecte la pose de têtes de collection (mode setup uniquement)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        // Ne fonctionne qu'en mode setup
        if (!setupMode) return;
        Player player = event.getPlayer();
        Block placedBlock = event.getBlock();

        // Vérifier si c'est une tête de collection
        if (placedBlock.getType() == Material.PLAYER_HEAD &&
                plugin.getHeadCollectionManager().isCollectionHeadItem(event.getItemInHand())) {
            // Enregistrer la position
            plugin.getHeadCollectionManager().registerHeadPlacement(placedBlock.getLocation());
            player.sendMessage("§a✓ Tête de collection enregistrée !");
            player.sendMessage("§7Position: " + placedBlock.getX() + ", " + placedBlock.getY() + ", " + placedBlock.getZ());

            // Log pour l'admin
            plugin.getPluginLogger().info("§aTête placée par " + player.getName() + " en " +
                    placedBlock.getWorld().getName() + " " + placedBlock.getX() + "," + placedBlock.getY() + "," + placedBlock.getZ());
        }
    }

    /**
     * Détecte la casse de têtes de collection (mode setup uniquement)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // Mode setup : permet la suppression des têtes enregistrées
        if (setupMode) {
            Block brokenBlock = event.getBlock();

            // Vérifier si c'est une tête de collection enregistrée
            if (brokenBlock.getType() == Material.PLAYER_HEAD &&
                    plugin.getHeadCollectionManager().isCollectionHead(brokenBlock.getLocation())) {

                plugin.getHeadCollectionManager().unregisterHeadBreak(brokenBlock.getLocation());

                Player player = event.getPlayer();
                player.sendMessage("§c✗ Tête de collection supprimée !");

                // Log pour l'admin
                plugin.getPluginLogger().info("§cTête supprimée par " + player.getName() + " en " +
                        brokenBlock.getWorld().getName() + " " + brokenBlock.getX() + "," + brokenBlock.getY() + "," + brokenBlock.getZ());
                return;
            }
        }

        // Mode normal : empêcher la casse des têtes de collection
        if (!setupMode) {
            Block brokenBlock = event.getBlock();

            if (brokenBlock.getType() == Material.PLAYER_HEAD &&
                    plugin.getHeadCollectionManager().isCollectionHead(brokenBlock.getLocation())) {

                event.setCancelled(true);

                Player player = event.getPlayer();
                player.sendMessage("§c❌ Vous ne pouvez pas casser cette tête de collection !");
                player.sendMessage("§7Faites un clic droit pour la collecter.");
            }
        }
    }

    /**
     * Gère les clics droits sur les têtes pour la collection
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;

        Block clickedBlock = event.getClickedBlock();

        // Vérifier si c'est une tête de collection
        if (clickedBlock.getType() == Material.PLAYER_HEAD &&
                plugin.getHeadCollectionManager().isCollectionHead(clickedBlock.getLocation())) {

            event.setCancelled(true);

            Player player = event.getPlayer();

            // En mode setup, on donne des infos aux admins
            if (setupMode && player.hasPermission("prisontycoon.admin")) {
                player.sendMessage("§e📍 Tête de collection détectée !");
                player.sendMessage("§7Monde: §f" + clickedBlock.getWorld().getName());
                player.sendMessage("§7Position: §f" + clickedBlock.getX() + ", " + clickedBlock.getY() + ", " + clickedBlock.getZ());
                player.sendMessage("§7Mode: §aConfiguration activé");
                return;
            }

            // Mode normal : tentative de collection
            if (!setupMode) {
                boolean collected = plugin.getHeadCollectionManager().collectHead(player, clickedBlock.getLocation());

                if (!collected) {
                    // Le message d'erreur est déjà envoyé dans collectHead()
                    return;
                }
            }
        }
    }

    /**
     * Récupère l'état du mode setup
     */
    public boolean isSetupMode() {
        return setupMode;
    }

    /**
     * Active/désactive le mode setup
     */
    public void setSetupMode(boolean setupMode) {
        this.setupMode = setupMode;

        if (setupMode) {
            plugin.getPluginLogger().info("§eMode setup des têtes ACTIVÉ");
        } else {
            plugin.getPluginLogger().info("§cMode setup des têtes DÉSACTIVÉ");
        }
    }
}