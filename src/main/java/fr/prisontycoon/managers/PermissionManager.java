package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NOUVEAU: Gestionnaire des permissions intégré nativement avec LuckPerms
 * Remplace complètement l'ancien système et utilise directement l'API LuckPerms
 *
 * Fonctionnalités intégrées:
 * - Utilisation directe de l'API LuckPerms du plugin principal
 * - Fallback automatique vers Bukkit si LuckPerms indisponible
 * - Synchronisation bidirectionnelle des permissions
 * - Cache intelligent intégré
 * - Gestion VIP automatique
 */
public class PermissionManager {

    private final PrisonTycoon plugin;

    // Cache des PermissionAttachment par joueur (fallback Bukkit)
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    // Groupes VIP configurés (depuis config.yml)
    private final Set<String> vipGroups;

    public PermissionManager(PrisonTycoon plugin) {
        this.plugin = plugin;

        // Charge les groupes VIP depuis la config
        this.vipGroups = new HashSet<>(plugin.getConfig().getStringList("hooks.luckperms.vip-groups"));
        if (vipGroups.isEmpty()) {
            // Valeurs par défaut si aucune config
            vipGroups.addAll(List.of("vip", "vip+", "mvp", "mvp+", "premium", "elite"));
        }
    }

    /**
     * Applique toutes les permissions stockées à un joueur qui se connecte
     * INTÉGRATION NATIVE avec LuckPerms
     */
    public void applyStoredPermissions(Player player) {
        UUID uuid = player.getUniqueId();

        if (plugin.isLuckPermsEnabled()) {
            // INTÉGRATION LUCKPERMS NATIVE
            handleLuckPermsIntegration(player);
        } else {
            // FALLBACK BUKKIT
            handleBukkitFallback(player);
        }

        plugin.getPluginLogger().info("Permissions appliquées à " + player.getName() +
                " (LuckPerms: " + plugin.isLuckPermsEnabled() + ")");
    }

    /**
     * Gestion native LuckPerms
     */
    private void handleLuckPermsIntegration(Player player) {
        UUID uuid = player.getUniqueId();
        LuckPerms luckPerms = plugin.getLuckPermsAPI();

        if (luckPerms == null) return;

        UserManager userManager = luckPerms.getUserManager();

        // Charge l'utilisateur de manière asynchrone
        userManager.loadUser(uuid).thenAccept(user -> {
            if (user == null) return;

            // Synchronise le statut VIP
            synchronizeVipStatus(player, user);

            // Synchronise les permissions personnalisées
            synchronizeCustomPermissions(player, user);

            plugin.getPluginLogger().debug("Intégration LuckPerms complétée pour " + player.getName());
        });
    }

    /**
     * Synchronise le statut VIP entre LuckPerms et PlayerData
     */
    private void synchronizeVipStatus(Player player, User luckPermsUser) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifie si le joueur est VIP selon LuckPerms
        boolean isVipInLuckPerms = luckPermsUser.getNodes().stream()
                .filter(node -> node instanceof InheritanceNode)
                .map(node -> ((InheritanceNode) node).getGroupName())
                .anyMatch(group -> vipGroups.contains(group.toLowerCase()));

        // Vérifie aussi les permissions directes
        if (!isVipInLuckPerms) {
            isVipInLuckPerms = luckPermsUser.getCachedData().getPermissionData()
                    .checkPermission("prisontycoon.vip").asBoolean();
        }

        // Synchronise avec PlayerData
        if (playerData.isVip() != isVipInLuckPerms) {
            playerData.setVip(isVipInLuckPerms);
            plugin.getPluginLogger().debug("Statut VIP synchronisé pour " + player.getName() + ": " + isVipInLuckPerms);

            // Met à jour le TabManager si disponible
            if (plugin.getTabManager() != null) {
                plugin.getTabManager().updatePlayerTab(player);
            }
        }
    }

    /**
     * Synchronise les permissions personnalisées
     */
    private void synchronizeCustomPermissions(Player player, User luckPermsUser) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Set<String> pluginPermissions = playerData.getCustomPermissions();

        // Ajoute toutes les permissions du plugin qui sont dans LuckPerms
        luckPermsUser.getNodes().stream()
                .filter(node -> node instanceof PermissionNode)
                .map(node -> ((PermissionNode) node).getPermission())
                .filter(perm -> perm.startsWith("prisontycoon."))
                .forEach(pluginPermissions::add);
    }

    /**
     * Fallback Bukkit (ancien système amélioré)
     */
    private void handleBukkitFallback(Player player) {
        UUID uuid = player.getUniqueId();

        // Retire l'ancien attachment
        removeAttachment(player);

        // Crée un nouveau PermissionAttachment
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        // Applique toutes les permissions stockées
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(uuid);
        Set<String> permissions = playerData.getCustomPermissions();

        for (String permission : permissions) {
            attachment.setPermission(permission, true);
        }

        // Ajoute les permissions automatiques
        if (playerData.isVip()) {
            attachment.setPermission("prisontycoon.vip", true);
        }

        plugin.getPluginLogger().debug("Permissions Bukkit appliquées à " + player.getName() + ": " + permissions.size());
    }

    /**
     * Attache une permission spécifique à un joueur en ligne
     * INTÉGRATION NATIVE avec LuckPerms
     */
    public CompletableFuture<Boolean> attachPermission(Player player, String permission) {
        UUID uuid = player.getUniqueId();

        if (plugin.isLuckPermsEnabled()) {
            // INTÉGRATION LUCKPERMS NATIVE
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();

            return userManager.loadUser(uuid).thenCompose(user -> {
                if (user == null) return CompletableFuture.completedFuture(false);

                // Ajoute la permission
                PermissionNode node = PermissionNode.builder(permission).build();
                user.data().add(node);

                // Sauvegarde
                return userManager.saveUser(user).thenApply(v -> {
                    plugin.getPluginLogger().info("Permission LuckPerms ajoutée: " + player.getName() + " -> " + permission);
                    return true;
                });
            });
        } else {
            // FALLBACK BUKKIT
            return CompletableFuture.supplyAsync(() -> {
                PermissionAttachment attachment = attachments.get(uuid);
                if (attachment != null) {
                    attachment.setPermission(permission, true);

                    // Sauvegarde dans PlayerData
                    PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(uuid);
                    playerData.getCustomPermissions().add(permission);

                    plugin.getPluginLogger().info("Permission Bukkit ajoutée: " + player.getName() + " -> " + permission);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Retire une permission d'un joueur
     * INTÉGRATION NATIVE avec LuckPerms
     */
    public CompletableFuture<Boolean> removePermission(Player player, String permission) {
        UUID uuid = player.getUniqueId();

        if (plugin.isLuckPermsEnabled()) {
            // INTÉGRATION LUCKPERMS NATIVE
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();

            return userManager.loadUser(uuid).thenCompose(user -> {
                if (user == null) return CompletableFuture.completedFuture(false);

                // Retire la permission
                PermissionNode node = PermissionNode.builder(permission).build();
                user.data().remove(node);

                // Sauvegarde
                return userManager.saveUser(user).thenApply(v -> {
                    plugin.getPluginLogger().info("Permission LuckPerms retirée: " + player.getName() + " -> " + permission);
                    return true;
                });
            });
        } else {
            // FALLBACK BUKKIT
            return CompletableFuture.supplyAsync(() -> {
                PermissionAttachment attachment = attachments.get(uuid);
                if (attachment != null) {
                    attachment.setPermission(permission, false);

                    // Sauvegarde dans PlayerData
                    PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(uuid);
                    playerData.getCustomPermissions().remove(permission);

                    plugin.getPluginLogger().info("Permission Bukkit retirée: " + player.getName() + " -> " + permission);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Vérifie si un joueur a une permission
     * INTÉGRATION NATIVE avec LuckPerms
     */
    public boolean hasPermission(@NotNull Player player, @NotNull String permission) {
        if (plugin.isLuckPermsEnabled()) {
            // INTÉGRATION LUCKPERMS NATIVE - utilise le cache de LuckPerms
            return player.hasPermission(permission);
        } else {
            // FALLBACK BUKKIT
            return player.hasPermission(permission);
        }
    }

    /**
     * Vérifie si un joueur est VIP
     * INTÉGRATION NATIVE avec LuckPerms
     */
    public boolean isVip(@NotNull Player player) {
        if (plugin.isLuckPermsEnabled()) {
            // INTÉGRATION LUCKPERMS NATIVE
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();

            User user = userManager.getUser(player.getUniqueId());
            if (user != null) {
                // Vérifie les groupes
                boolean hasVipGroup = user.getNodes().stream()
                        .filter(node -> node instanceof InheritanceNode)
                        .map(node -> ((InheritanceNode) node).getGroupName())
                        .anyMatch(group -> vipGroups.contains(group.toLowerCase()));

                // Vérifie les permissions directes
                boolean hasVipPermission = user.getCachedData().getPermissionData()
                        .checkPermission("prisontycoon.vip").asBoolean();

                return hasVipGroup || hasVipPermission;
            }
        }

        // FALLBACK vers PlayerData
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.isVip();
    }

    /**
     * Ajoute un joueur à un groupe VIP
     * INTÉGRATION NATIVE avec LuckPerms
     */
    public CompletableFuture<Boolean> addToVipGroup(@NotNull Player player, @NotNull String group) {
        if (!plugin.isLuckPermsEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        LuckPerms luckPerms = plugin.getLuckPermsAPI();
        UserManager userManager = luckPerms.getUserManager();

        return userManager.loadUser(player.getUniqueId()).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(false);

            // Ajoute au groupe
            InheritanceNode node = InheritanceNode.builder(group).build();
            user.data().add(node);

            // Sauvegarde
            return userManager.saveUser(user).thenApply(v -> {
                plugin.getPluginLogger().info("Groupe VIP ajouté: " + player.getName() + " -> " + group);

                // Met à jour PlayerData
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                playerData.setVip(true);

                return true;
            });
        });
    }

    /**
     * Obtient le groupe principal d'un joueur
     * INTÉGRATION NATIVE avec LuckPerms
     */
    public String getPrimaryGroup(@NotNull Player player) {
        if (plugin.isLuckPermsEnabled()) {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();

            User user = userManager.getUser(player.getUniqueId());
            return user != null ? user.getPrimaryGroup() : "default";
        }

        return "default";
    }

    /**
     * Obtient tous les groupes d'un joueur
     * INTÉGRATION NATIVE avec LuckPerms
     */
    public Set<String> getGroups(@NotNull Player player) {
        if (plugin.isLuckPermsEnabled()) {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();

            User user = userManager.getUser(player.getUniqueId());
            if (user != null) {
                return user.getNodes().stream()
                        .filter(node -> node instanceof InheritanceNode)
                        .map(node -> ((InheritanceNode) node).getGroupName())
                        .collect(java.util.stream.Collectors.toSet());
            }
        }

        return Set.of("default");
    }

    /**
     * Retire l'attachment Bukkit d'un joueur (nettoyage)
     */
    private void removeAttachment(Player player) {
        UUID uuid = player.getUniqueId();
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment != null) {
            attachment.remove();
        }
    }

    /**
     * Nettoie les données d'un joueur qui se déconnecte
     */
    public void cleanupPlayer(Player player) {
        removeAttachment(player);
        plugin.getPluginLogger().debug("Permissions nettoyées pour: " + player.getName());
    }

    /**
     * Recharge les groupes VIP depuis la configuration
     */
    public void reloadVipGroups() {
        vipGroups.clear();
        vipGroups.addAll(plugin.getConfig().getStringList("hooks.luckperms.vip-groups"));
        if (vipGroups.isEmpty()) {
            vipGroups.addAll(List.of("vip", "vip+", "mvp", "mvp+", "premium", "elite"));
        }
        plugin.getPluginLogger().info("Groupes VIP rechargés: " + vipGroups);
    }

    /**
     * Obtient les groupes VIP configurés
     */
    public Set<String> getVipGroups() {
        return new HashSet<>(vipGroups);
    }
}