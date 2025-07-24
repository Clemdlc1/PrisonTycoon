package fr.prisontycoon.hooks;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Hook optimisé pour LuckPerms
 * Remplace complètement l'ancien PermissionManager
 *
 * Fonctionnalités:
 * - Gestion des groupes et permissions
 * - Cache intelligent avec invalidation automatique
 * - Support des métadonnées (préfixes, suffixes, options)
 * - Intégration avec les rangs VIP du plugin
 * - API asynchrone pour les performances
 */
public class LuckPermsHook {

    private final PrisonTycoon plugin;
    private LuckPerms luckPermsAPI;
    private UserManager userManager;

    // Cache des données utilisateur pour les performances
    private final Map<UUID, CachedUserData> userCache = new ConcurrentHashMap<>();

    // Contexte par défaut pour les requêtes
    private final ImmutableContextSet defaultContext;

    // Groupes VIP configurés
    private final Set<String> vipGroups = Set.of("vip", "vip+");

    // Permissions clés du plugin
    public static final String ADMIN_PERMISSION = "prisontycoon.admin";
    public static final String VIP_PERMISSION = "prisontycoon.vip";
    public static final String MODERATOR_PERMISSION = "prisontycoon.moderator";
    public static final String BYPASS_PERMISSION = "prisontycoon.bypass";

    public LuckPermsHook(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.defaultContext = ImmutableContextSet.builder()
                .add("server", "global")
                .add("world", "world")
                .build();
    }

    /**
     * Initialise le hook LuckPerms
     */
    public boolean initialize() {
        try {
            // Récupère l'API LuckPerms
            luckPermsAPI = LuckPermsProvider.get();
            userManager = luckPermsAPI.getUserManager();

            plugin.getPluginLogger().info("§a✓ LuckPerms API initialisée");

            // Enregistre les listeners pour l'invalidation du cache
            setupCacheInvalidation();

            // Pré-charge les données des joueurs en ligne
            preloadOnlineUsers();

            plugin.getPluginLogger().info("§a✓ Cache LuckPerms initialisé");

            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors de l'initialisation LuckPerms:");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Configure l'invalidation automatique du cache
     */
    private void setupCacheInvalidation() {
        // Invalide le cache quand les données d'un utilisateur changent
        luckPermsAPI.getEventBus().subscribe(plugin, net.luckperms.api.event.user.UserDataRecalculateEvent.class, event -> {
            UUID userId = event.getUser().getUniqueId();
            userCache.remove(userId);
            plugin.getPluginLogger().debug("Cache invalidé pour: " + userId);
        });

        // Invalide le cache lors des changements de groupe
        luckPermsAPI.getEventBus().subscribe(plugin, net.luckperms.api.event.node.NodeAddEvent.class, event -> {
            if (event.getTarget() instanceof User user) {
                userCache.remove(user.getUniqueId());
            }
        });

        luckPermsAPI.getEventBus().subscribe(plugin, net.luckperms.api.event.node.NodeRemoveEvent.class, event -> {
            if (event.getTarget() instanceof User user) {
                userCache.remove(user.getUniqueId());
            }
        });
    }

    /**
     * Pré-charge les données des joueurs en ligne
     */
    private void preloadOnlineUsers() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            loadUserDataAsync(player.getUniqueId());
        }
    }

    /**
     * Vérifie si un joueur a une permission
     */
    public boolean hasPermission(@NotNull Player player, @NotNull String permission) {
        return hasPermission(player.getUniqueId(), permission);
    }

    /**
     * Vérifie si un UUID a une permission
     */
    public boolean hasPermission(@NotNull UUID uuid, @NotNull String permission) {
        CachedUserData userData = getUserData(uuid);
        if (userData != null) {
            return userData.hasPermission(permission);
        }

        // Fallback synchrone si le cache n'est pas disponible
        User user = userManager.getUser(uuid);
        if (user != null) {
            return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
        }

        return false;
    }

    /**
     * Vérifie si un joueur est admin
     */
    public boolean isAdmin(@NotNull Player player) {
        return hasPermission(player, ADMIN_PERMISSION) || player.hasPermission("*");
    }

    /**
     * Vérifie si un joueur est VIP (par permission ou groupe)
     */
    public boolean isVip(@NotNull Player player) {
        return isVip(player.getUniqueId());
    }

    /**
     * Vérifie si un UUID est VIP
     */
    public boolean isVip(@NotNull UUID uuid) {
        // Vérifie la permission VIP
        if (hasPermission(uuid, VIP_PERMISSION)) {
            return true;
        }

        // Vérifie les groupes VIP
        CachedUserData userData = getUserData(uuid);
        if (userData != null) {
            return userData.getGroups().stream()
                    .anyMatch(group -> vipGroups.contains(group.toLowerCase()));
        }

        return false;
    }

    /**
     * Obtient le groupe principal d'un joueur
     */
    @Nullable
    public String getPrimaryGroup(@NotNull UUID uuid) {
        CachedUserData userData = getUserData(uuid);
        if (userData != null) {
            return userData.getPrimaryGroup();
        }

        User user = userManager.getUser(uuid);
        return user != null ? user.getPrimaryGroup() : null;
    }

    /**
     * Obtient tous les groupes d'un joueur
     */
    @NotNull
    public Set<String> getGroups(@NotNull UUID uuid) {
        CachedUserData userData = getUserData(uuid);
        if (userData != null) {
            return userData.getGroups();
        }

        User user = userManager.getUser(uuid);
        if (user != null) {
            return user.getNodes(NodeType.INHERITANCE).stream()
                    .map(InheritanceNode::getGroupName)
                    .collect(Collectors.toSet());
        }

        return Set.of();
    }

    /**
     * Obtient le préfixe d'un joueur
     */
    @Nullable
    public String getPrefix(@NotNull UUID uuid) {
        CachedUserData userData = getUserData(uuid);
        if (userData != null) {
            return userData.getPrefix();
        }

        User user = userManager.getUser(uuid);
        if (user != null) {
            CachedMetaData metaData = user.getCachedData().getMetaData();
            return metaData.getPrefix();
        }

        return null;
    }

    /**
     * Obtient le suffixe d'un joueur
     */
    @Nullable
    public String getSuffix(@NotNull UUID uuid) {
        CachedUserData userData = getUserData(uuid);
        if (userData != null) {
            return userData.getSuffix();
        }

        User user = userManager.getUser(uuid);
        if (user != null) {
            CachedMetaData metaData = user.getCachedData().getMetaData();
            return metaData.getSuffix();
        }

        return null;
    }

    /**
     * Obtient une métadonnée spécifique
     */
    @Nullable
    public String getMetaData(@NotNull UUID uuid, @NotNull String key) {
        User user = userManager.getUser(uuid);
        if (user != null) {
            CachedMetaData metaData = user.getCachedData().getMetaData();
            return metaData.getMetaValue(key);
        }
        return null;
    }

    /**
     * Ajoute une permission temporaire à un joueur
     */
    public CompletableFuture<Boolean> addTemporaryPermission(@NotNull UUID uuid, @NotNull String permission, long durationSeconds) {
        return loadUserAsync(uuid).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(false);

            Node node = PermissionNode.builder(permission)
                    .value(true)
                    .expiry(System.currentTimeMillis() / 1000 + durationSeconds)
                    .build();

            user.data().add(node);
            return luckPermsAPI.getUserManager().saveUser(user)
                    .thenApply(v -> true);
        });
    }

    /**
     * Ajoute une permission permanente à un joueur
     */
    public CompletableFuture<Boolean> addPermission(@NotNull UUID uuid, @NotNull String permission) {
        return loadUserAsync(uuid).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(false);

            Node node = PermissionNode.builder(permission)
                    .value(true)
                    .build();

            user.data().add(node);
            return luckPermsAPI.getUserManager().saveUser(user)
                    .thenApply(v -> true);
        });
    }

    /**
     * Retire une permission d'un joueur
     */
    public CompletableFuture<Boolean> removePermission(@NotNull UUID uuid, @NotNull String permission) {
        return loadUserAsync(uuid).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(false);

            Node node = PermissionNode.builder(permission).build();
            user.data().remove(node);

            return luckPermsAPI.getUserManager().saveUser(user)
                    .thenApply(v -> true);
        });
    }

    /**
     * Ajoute un joueur à un groupe
     */
    public CompletableFuture<Boolean> addToGroup(@NotNull UUID uuid, @NotNull String group) {
        return loadUserAsync(uuid).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(false);

            Node node = InheritanceNode.builder(group).build();
            user.data().add(node);

            return luckPermsAPI.getUserManager().saveUser(user)
                    .thenApply(v -> true);
        });
    }

    /**
     * Retire un joueur d'un groupe
     */
    public CompletableFuture<Boolean> removeFromGroup(@NotNull UUID uuid, @NotNull String group) {
        return loadUserAsync(uuid).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(false);

            Node node = InheritanceNode.builder(group).build();
            user.data().remove(node);

            return luckPermsAPI.getUserManager().saveUser(user)
                    .thenApply(v -> true);
        });
    }

    /**
     * Définit le groupe principal d'un joueur
     */
    public CompletableFuture<Boolean> setPrimaryGroup(@NotNull UUID uuid, @NotNull String group) {
        return loadUserAsync(uuid).thenCompose(user -> {
            if (user == null) return CompletableFuture.completedFuture(false);

            user.setPrimaryGroup(group);
            return luckPermsAPI.getUserManager().saveUser(user)
                    .thenApply(v -> true);
        });
    }

    /**
     * Charge un utilisateur de manière asynchrone
     */
    private CompletableFuture<User> loadUserAsync(@NotNull UUID uuid) {
        User cachedUser = userManager.getUser(uuid);
        if (cachedUser != null) {
            return CompletableFuture.completedFuture(cachedUser);
        }

        return userManager.loadUser(uuid);
    }

    /**
     * Charge les données utilisateur dans le cache
     */
    private void loadUserDataAsync(@NotNull UUID uuid) {
        loadUserAsync(uuid).thenAccept(user -> {
            if (user != null) {
                CachedUserData cachedData = new CachedUserData(user);
                userCache.put(uuid, cachedData);
            }
        });
    }

    /**
     * Récupère les données utilisateur du cache
     */
    @Nullable
    private CachedUserData getUserData(@NotNull UUID uuid) {
        CachedUserData cached = userCache.get(uuid);
        if (cached == null) {
            // Essaie de charger depuis LuckPerms si pas en cache
            User user = userManager.getUser(uuid);
            if (user != null) {
                cached = new CachedUserData(user);
                userCache.put(uuid, cached);
            }
        }
        return cached;
    }

    /**
     * Nettoie le cache d'un joueur qui se déconnecte
     */
    public void cleanupUser(@NotNull UUID uuid) {
        userCache.remove(uuid);
    }

    /**
     * Nettoie toutes les ressources
     */
    public void cleanup() {
        userCache.clear();
        luckPermsAPI.getEventBus().unregisterSubscriptions(plugin);
    }

    /**
     * Intègre les données LuckPerms avec PlayerData
     */
    public void integrateWithPlayerData(@NotNull Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Synchronise le statut VIP
        boolean isVip = isVip(player);
        if (playerData.isVip() != isVip) {
            playerData.setVip(isVip);
            logger.debug("Statut VIP synchronisé pour " + player.getName() + ": " + isVip);
        }

        // Synchronise les permissions personnalisées
        Set<String> luckPermsPermissions = getEffectivePermissions(player.getUniqueId());
        Set<String> pluginPermissions = playerData.getCustomPermissions();

        // Ajoute les nouvelles permissions
        for (String perm : luckPermsPermissions) {
            if (perm.startsWith("prisontycoon.") && !pluginPermissions.contains(perm)) {
                pluginPermissions.add(perm);
            }
        }
    }

    /**
     * Obtient toutes les permissions effectives d'un utilisateur
     */
    @NotNull
    private Set<String> getEffectivePermissions(@NotNull UUID uuid) {
        User user = userManager.getUser(uuid);
        if (user != null) {
            return user.getCachedData().getPermissionData().getPermissionMap().keySet();
        }
        return Set.of();
    }

    /**
     * Classe pour le cache des données utilisateur
     */
    private static class CachedUserData {
        private final String primaryGroup;
        private final Set<String> groups;
        private final String prefix;
        private final String suffix;
        private final Map<String, Boolean> permissions;
        private final long cacheTime;

        public CachedUserData(User user) {
            this.primaryGroup = user.getPrimaryGroup();
            this.groups = user.getNodes(NodeType.INHERITANCE).stream()
                    .map(InheritanceNode::getGroupName)
                    .collect(Collectors.toSet());

            CachedMetaData metaData = user.getCachedData().getMetaData();
            this.prefix = metaData.getPrefix();
            this.suffix = metaData.getSuffix();

            this.permissions = user.getCachedData().getPermissionData().getPermissionMap();
            this.cacheTime = System.currentTimeMillis();
        }

        public String getPrimaryGroup() { return primaryGroup; }
        public Set<String> getGroups() { return groups; }
        public String getPrefix() { return prefix; }
        public String getSuffix() { return suffix; }

        public boolean hasPermission(String permission) {
            return permissions.getOrDefault(permission, false);
        }

        // Cache valide pendant 5 minutes
        public boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > 300000;
        }
    }
}