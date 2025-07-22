package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class PNJInteract implements Listener {

    // Référence à votre classe principale pour pouvoir appeler openBlackMarket
    private final PrisonTycoon plugin;

    public PNJInteract(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();

        // Vérifier si l'entité cliquée a un nom custom
        if (clickedEntity.getCustomName() == null) {
            return;
        }

        // Vérifier si le nom de l'entité correspond à celui du Marchand Noir
        // Le equals() compare les chaînes de caractères, y compris les codes couleur.
        if (clickedEntity.getCustomName().equals("§8§l⚫ Marchand Noir ⚫")) {
            // Empêche l'ouverture de l'interface de villageois par défaut
            event.setCancelled(true);

            // Ouvre votre interface de marché noir pour le joueur
            // Assurez-vous que la méthode openBlackMarket(player) est publique dans votre classe principale
            plugin.getBlackMarketManager().openBlackMarket(player);
        }
    }
}