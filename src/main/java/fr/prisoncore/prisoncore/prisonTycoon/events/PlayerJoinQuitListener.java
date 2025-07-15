package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Listener pour les événements de connexion/déconnexion
 * CORRIGÉ : Utilise ScoreboardTask au lieu de ScoreboardManager + ajout checkLegendaryPickaxeState
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

        // CORRIGÉ : Initialisation avec délai plus long pour assurer stabilité
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Crée le scoreboard
            if (plugin.getScoreboardTask() != null) {
                plugin.getScoreboardTask().createScoreboard(player);
            }

            // Initialise l'expérience vanilla basée sur l'expérience custom
            plugin.getEconomyManager().initializeVanillaExp(player);

            // NOUVEAU : Vérifie l'état de la pioche légendaire à la connexion
            ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
            if (pickaxe != null && pickaxe.getItemMeta() instanceof Damageable) {
                Damageable meta = (Damageable) pickaxe.getItemMeta();
                short currentDurability = (short) meta.getDamage();
                short maxDurability = pickaxe.getType().getMaxDurability();

                plugin.getPickaxeManager().checkLegendaryPickaxeState(player, pickaxe, currentDurability, maxDurability);
            }

            plugin.getPickaxeManager().updateMobilityEffects(player);

            // Force refresh des permissions auto-upgrade si le joueur en avait
            if (plugin.getAutoUpgradeTask() != null) {
                plugin.getAutoUpgradeTask().refreshPlayerPermissions(player.getUniqueId());
            }

            // NOUVEAU : Force une mise à jour immédiate du scoreboard après tout
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getScoreboardTask() != null) {
                    plugin.getScoreboardTask().forceUpdatePlayer(player);
                }
            }, 20L); // 1 seconde après l'initialisation

            plugin.getPluginLogger().debug("Initialisation complète pour " + player.getName());
        }, 40L);

        plugin.getPluginLogger().info("§7Joueur connecté: " + player.getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Retire les effets de mobilité
        plugin.getPickaxeManager().removeMobilityEffects(player);

        // CORRIGÉ : Utilise ScoreboardTask au lieu de ScoreboardManager
        if (plugin.getScoreboardTask() != null) {
            plugin.getScoreboardTask().removeScoreboard(player);
        }

        // NOUVEAU : Nettoie les notifications en attente
        plugin.getNotificationManager().cleanupPlayerData(player.getUniqueId());

        // Décharge les données du joueur (avec sauvegarde)
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());

        plugin.getPluginLogger().info("§7Joueur déconnecté: " + player.getName());
    }
}