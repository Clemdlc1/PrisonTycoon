package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener pour gérer le statut de combat (combat tag)
 */
public class CombatListener implements Listener {

    private final PrisonTycoon plugin;

    public CombatListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        plugin.getCombatManager().tagPlayerInCombat(victim, attacker);
        // On peut aussi tag l'attaquant pour éviter qu'il se téléporte
        plugin.getCombatManager().tagPlayerInCombat(attacker, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getCombatManager().handlePlayerQuit(event.getPlayer());
    }
}


