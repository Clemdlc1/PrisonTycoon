package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class MobilityEffectsListener implements Listener {

    private final PrisonTycoon plugin;

    public MobilityEffectsListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère la mort du joueur - retire les effets de mobilité
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Retire les effets de mobilité à la mort
        plugin.getPickaxeManager().removeMobilityEffects(player);
    }

    /**
     * Gère le respawn du joueur - remet les effets si il a la pioche
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Délai d'1 tick pour s'assurer que le joueur est complètement respawn
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getPickaxeManager().updateMobilityEffects(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Gère le changement d'item en main (slot de hotbar)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Délai d'1 tick pour s'assurer que l'item est complètement changé
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getPickaxeManager().updateMobilityEffects(player);
            }
        }.runTaskLater(plugin, 1L);
    }
}