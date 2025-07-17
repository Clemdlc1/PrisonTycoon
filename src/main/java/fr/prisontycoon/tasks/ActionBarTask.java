package fr.prisontycoon.tasks;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * MODIFIÉ : ActionBarTask qui respecte les notifications temporaires du MiningListener
 * Les notifications de durabilité ont la priorité sur les messages de combustion/abondance
 */
public class ActionBarTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private long tickCount = 0;

    public ActionBarTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;
        updateActionBarStatus();

        // Nettoie les notifications temporaires expirées
        plugin.getNotificationManager().cleanupExpiredTemporaryNotifications();
    }

    /**
     * Met à jour l'action bar avec l'état des enchantements actifs
     * MODIFIÉ : Respecte les notifications temporaires du MiningListener
     */
    public void updateActionBarStatus() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // NOUVEAU : Vérifie d'abord s'il y a une notification temporaire active
            if (plugin.getNotificationManager().hasActiveTemporaryNotification(player)) {
                // Il y a une notification temporaire active (ex: durabilité),
                // on laisse le NotificationManager s'en occuper
                String tempMessage = plugin.getNotificationManager().getActiveTemporaryNotificationMessage(player);
                if (tempMessage != null) {
                    player.sendActionBar(tempMessage);
                }
                continue; // Passe au joueur suivant
            }

            // Aucune notification temporaire, on peut afficher nos messages normaux
            String statusMessage = generateStatusMessage(player);
            if (statusMessage != null && !statusMessage.isEmpty()) {
                player.sendActionBar(statusMessage);
            }
        }
    }

    /**
     * MODIFIÉ : Génère le message pour les enchantements actifs (seulement si pas de notification temporaire)
     * Supprime les anciennes notifications de durabilité (maintenant gérées par MiningListener)
     */
    private String generateStatusMessage(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        StringBuilder status = new StringBuilder();

        boolean currentlyMining = playerData.isCurrentlyMining();
        boolean isPickaxeBroken = plugin.getEnchantmentManager().isPlayerPickaxeBroken(player);

        // HARMONISATION : Priorité aux messages temporaires de changement d'état
        if (player.hasMetadata("pickaxe_just_broken")) {
            long brokenTime = player.getMetadata("pickaxe_just_broken").get(0).asLong();
            if (System.currentTimeMillis() - brokenTime < 3000) {
                return "§c💥 PIOCHE CASSÉE! Tous enchantements désactivés sauf Token Greed (90% malus)";
            } else {
                player.removeMetadata("pickaxe_just_broken", plugin);
            }
        }

        if (player.hasMetadata("pickaxe_just_repaired")) {
            long repairedTime = player.getMetadata("pickaxe_just_repaired").get(0).asLong();
            if (System.currentTimeMillis() - repairedTime < 3000) {
                return "§a✅ Pioche réparée! Tous les enchantements sont actifs";
            } else {
                player.removeMetadata("pickaxe_just_repaired", plugin);
            }
        }

        // Messages normaux d'enchantements si le joueur mine
        if (!currentlyMining) {
            return ""; // Pas de message si pas en train de miner
        }

        // Si la pioche est cassée (état permanent), affiche le message d'avertissement
        if (isPickaxeBroken) {
            return "§c💥 PIOCHE CASSÉE! Tous enchantements désactivés sauf Token Greed (90% malus)";
        }

        // États spéciaux (combustion, abondance, etc.) - SEULEMENT quand le joueur mine
        if (playerData.getCombustionLevel() > 0) {
            if (status.length() > 0) status.append(" §8| ");
            double multiplier = playerData.getCombustionMultiplier();
            status.append("§c🔥 Combustion: §6x").append(String.format("%.2f", multiplier));
        }

        if (playerData.isAbundanceActive()) {
            if (status.length() > 0) status.append(" §8| ");
            status.append("§6⭐ Abondance: §aACTIVE");
        }

        return status.toString();
    }
}