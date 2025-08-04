package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.OutpostData;
import fr.prisontycoon.managers.OutpostManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Listener pour les événements liés à l'avant-poste
 */
public class OutpostListener implements Listener {

    private final PrisonTycoon plugin;


    public OutpostListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Détecte quand un joueur entre ou quitte la zone de l'avant-poste
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        OutpostManager outpostManager = plugin.getOutpostManager();

        if (outpostManager == null) {
            return;
        }

        // Vérifier uniquement si le joueur change de bloc (optimisation)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Vérifier si le joueur est en train de capturer
        if (!outpostManager.isCapturing(player)) {
            return;
        }

        // Si le joueur n'est plus dans la zone, annuler la capture
        if (!outpostManager.isPlayerInOutpost(player)) {
            outpostManager.cancelCapture(player);
            player.sendMessage("§c❌ Capture annulée - vous avez quitté l'avant-poste!");
        }
    }

    /**
     * Auto-capture quand un joueur entre dans la zone de l'avant-poste
     * (optionnel - peut être activé/désactivé)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerEnterOutpost(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        OutpostManager outpostManager = plugin.getOutpostManager();

        if (outpostManager == null) {
            return;
        }

        if (outpostManager.getOutpostData().getController() != null) {
            return;
        }

        // Vérifier uniquement si le joueur change de bloc
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Vérifier si le joueur vient d'entrer dans la zone
        boolean wasInOutpost = outpostManager.isPlayerInOutpost(event.getFrom());
        boolean isInOutpost = outpostManager.isPlayerInOutpost(event.getTo());

        if (!wasInOutpost && isInOutpost) {
            // Joueur vient d'entrer dans l'avant-poste
            if (!outpostManager.isCapturing(player)) {
                // Informer le joueur qu'il peut capturer
                player.sendMessage("§6🏰 Vous êtes sur l'avant-poste!");
                player.sendMessage("§7Utilisez §e/AP capture §7ou restez ici pour le capturer!");

                // Auto-démarrer la capture (optionnel)
                outpostManager.startCapture(player);
            }
        }
    }

    /**
     * Annule les captures en cours quand un joueur se déconnecte
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        OutpostManager outpostManager = plugin.getOutpostManager();

        if (outpostManager != null && outpostManager.isCapturing(player)) {
            outpostManager.cancelCapture(player);
        }
    }

    /**
     * Annule les captures en cours quand un joueur se téléporte hors de la zone
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        OutpostManager outpostManager = plugin.getOutpostManager();

        if (outpostManager == null || !outpostManager.isCapturing(player)) {
            return;
        }

        // Vérifier si la téléportation sort le joueur de la zone
        if (!outpostManager.isPlayerInOutpost(event.getTo())) {
            outpostManager.cancelCapture(player);
            player.sendMessage("§c❌ Capture annulée - téléportation hors de l'avant-poste!");
        }
    }

    /**
     * Empêche le placement de blocs dans l'avant-poste
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Player player = event.getPlayer();
        OutpostManager outpostManager = plugin.getOutpostManager();

        if (outpostManager == null) {
            return;
        }

        // Vérifier si le joueur place des blocs dans la zone de l'avant-poste
        if (outpostManager.isPlayerInOutpost(event.getBlock().getLocation())) {
            // Empêcher la modification de l'avant-poste par les joueurs normaux
            if (!player.hasPermission("specialmine.admin")) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Vous ne pouvez pas placer de blocs dans l'avant-poste!");
            }
        }
    }

    /**
     * Gère les interactions avec les bannières de l'avant-poste
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        OutpostManager outpostManager = plugin.getOutpostManager();

        if (outpostManager == null) {
            return;
        }

        // Vérifier si l'interaction est dans la zone de l'avant-poste
        if (outpostManager.isPlayerInOutpost(event.getClickedBlock().getLocation())) {
            // Si c'est une bannière, afficher des informations
            if (event.getClickedBlock().getType().name().contains("BANNER")) {
                event.setCancelled(true);

                player.sendMessage("§6🏳️ Bannière de l'avant-poste");
                if (outpostManager.getOutpostData().isControlled()) {
                    player.sendMessage("§7Contrôlé par: §6" + outpostManager.getOutpostData().getControllerName());
                } else {
                    player.sendMessage("§7Aucun contrôleur actuellement");
                }
                player.sendMessage("§7Utilisez §e/AP §7pour plus d'informations!");
            }
        }
    }
}