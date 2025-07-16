package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * NOUVEAU: Gestionnaire des permissions Bukkit
 * Permet que player.hasPermission("specialmine.vip") fonctionne
 */
public class PermissionManager {

    private final PrisonTycoon plugin;

    // Cache des PermissionAttachment par joueur
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public PermissionManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Applique toutes les permissions stockées à un joueur qui se connecte
     */
    public void applyStoredPermissions(Player player) {
        UUID uuid = player.getUniqueId();

        // Retire l'ancien attachment s'il existe
        removeAttachment(player);

        // Crée un nouveau PermissionAttachment
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        // Applique toutes les permissions stockées
        Set<String> permissions = plugin.getPlayerDataManager().getPlayerData(uuid).getCustomPermissions();
        for (String permission : permissions) {
            attachment.setPermission(permission, true);
        }

        if (!permissions.isEmpty()) {
            plugin.getPluginLogger().info("Permissions appliquées à " + player.getName() + ": " + permissions);
        }
    }

    /**
     * Attache une permission spécifique à un joueur en ligne
     */
    public void attachPermission(Player player, String permission) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment attachment = attachments.get(uuid);

        if (attachment == null) {
            // Crée un nouveau attachment si nécessaire
            attachment = player.addAttachment(plugin);
            attachments.put(uuid, attachment);
        }

        attachment.setPermission(permission, true);
        plugin.getPluginLogger().debug("Permission attachée: " + permission + " à " + player.getName());
    }

    /**
     * Détache une permission spécifique d'un joueur en ligne
     */
    public void detachPermission(Player player, String permission) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment attachment = attachments.get(uuid);

        if (attachment != null) {
            attachment.unsetPermission(permission);
            plugin.getPluginLogger().debug("Permission détachée: " + permission + " de " + player.getName());
        }
    }

    /**
     * Retire toutes les permissions d'un joueur (déconnexion)
     */
    public void removeAttachment(Player player) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment attachment = attachments.remove(uuid);

        if (attachment != null) {
            player.removeAttachment(attachment);
            plugin.getPluginLogger().debug("Permissions détachées de " + player.getName());
        }
    }

    /**
     * Recharge toutes les permissions d'un joueur
     */
    public void reloadPlayerPermissions(Player player) {
        UUID uuid = player.getUniqueId();

        // Retire l'ancien attachment
        removeAttachment(player);

        // Réapplique toutes les permissions
        applyStoredPermissions(player);

        plugin.getPluginLogger().info("Permissions rechargées pour " + player.getName());
    }

    /**
     * Vérifie si un joueur a une permission (pour debug)
     */
    public boolean hasPermission(Player player, String permission) {
        return player.hasPermission(permission);
    }

    /**
     * Obtient toutes les permissions actives d'un joueur (debug)
     */
    public Map<String, Boolean> getActivePermissions(Player player) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment attachment = attachments.get(uuid);

        if (attachment != null) {
            return new HashMap<>(attachment.getPermissions());
        }

        return new HashMap<>();
    }

    /**
     * Nettoie tous les attachments (arrêt du plugin)
     */
    public void cleanup() {
        for (Map.Entry<UUID, PermissionAttachment> entry : attachments.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                player.removeAttachment(entry.getValue());
            }
        }
        attachments.clear();
        plugin.getPluginLogger().info("PermissionManager nettoyé");
    }
}