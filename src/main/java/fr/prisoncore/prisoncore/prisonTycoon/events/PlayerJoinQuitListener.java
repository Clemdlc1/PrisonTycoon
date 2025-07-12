package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener pour les événements de connexion/déconnexion
 * CORRIGÉ: Initialise scoreboard + expérience vanilla
 */
public class PlayerJoinQuitListener implements Listener {

    private final PrisonTycoon plugin;

    public PlayerJoinQuitListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Charge les données du joueur
        plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Initialisation avec délai pour assurer que tout est chargé
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // NOUVEAU: Crée et affiche le scoreboard
            plugin.getScoreboardManager().createScoreboard(player);

            // Initialise l'expérience vanilla basée sur l'expérience custom
            plugin.getEconomyManager().initializeVanillaExp(player);

            // Applique les effets de mobilité
            plugin.getPickaxeManager().updateMobilityEffects(player);

            // Force refresh des permissions auto-upgrade si le joueur en avait
            plugin.getAutoUpgradeTask().refreshPlayerPermissions(player.getUniqueId());

            plugin.getPluginLogger().debug("Initialisation complète pour " + player.getName());
        }, 20L); // 1 seconde de délai

        plugin.getPluginLogger().info("§7Joueur connecté: " + player.getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Retire les effets de mobilité
        plugin.getPickaxeManager().removeMobilityEffects(player);

        // NOUVEAU: Retire le scoreboard
        plugin.getScoreboardManager().removeScoreboard(player);

        // Décharge les données du joueur (avec sauvegarde)
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());

        plugin.getPluginLogger().info("§7Joueur déconnecté: " + player.getName());
    }
}