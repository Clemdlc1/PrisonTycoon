package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire VIP intégré avec LuckPerms
 * INTÉGRATION NATIVE - Remplace l'ancien VipManager
 * <p>
 * Fonctionnalités intégrées:
 * - Gestion complète avec LuckPerms (groupes, permissions)
 * - Fallback vers fichier YAML si LuckPerms indisponible
 * - VIP temporaires avec expiration automatique
 * - Synchronisation bidirectionnelle
 * - Avantages VIP automatiques
 */
public class VipManager {

    private final PrisonTycoon plugin;
    private final File vipFile;
    // Groupes VIP configurés (depuis config.yml)
    private final Set<String> vipGroups;
    private final String defaultVipGroup;
    // Cache local pour les performances (fallback)
    private final Map<UUID, VipData> vipCache = new ConcurrentHashMap<>();
    // Avantages VIP configurés
    private final Map<String, Object> vipBenefits;
    private FileConfiguration vipConfig;

    public VipManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.vipFile = new File(plugin.getDataFolder(), "vips.yml");

        // Charge les groupes VIP depuis la config
        this.vipGroups = new HashSet<>(plugin.getConfig().getStringList("hooks.luckperms.vip-groups"));
        if (vipGroups.isEmpty()) {
            // Valeurs par défaut
            vipGroups.addAll(List.of("vip", "vip+", "mvp", "mvp+", "premium", "elite"));
        }

        this.defaultVipGroup = plugin.getConfig().getString("hooks.luckperms.default-vip-group", "vip");

        // Charge les avantages VIP
        this.vipBenefits = loadVipBenefits();

        // Initialise le fichier fallback
        initializeFile();

        // Charge les VIP existants
        loadVips();

        // Démarre les tâches de maintenance
        startMaintenanceTasks();

        plugin.getPluginLogger().info("VipManager intégré initialisé:");
        plugin.getPluginLogger().info("- Groupes VIP: " + vipGroups);
        plugin.getPluginLogger().info("- Groupe par défaut: " + defaultVipGroup);
        plugin.getPluginLogger().info("- LuckPerms: " + plugin.isLuckPermsEnabled());
    }

    /**
     * Charge les avantages VIP depuis la configuration
     */
    private Map<String, Object> loadVipBenefits() {
        Map<String, Object> benefits = new HashMap<>();

        // Avantages économiques
        benefits.put("coin_multiplier", plugin.getConfig().getDouble("vip.benefits.coin_multiplier", 1.5));
        benefits.put("token_multiplier", plugin.getConfig().getDouble("vip.benefits.token_multiplier", 2.0));
        benefits.put("xp_multiplier", plugin.getConfig().getDouble("vip.benefits.xp_multiplier", 1.3));

        // Avantages fonctionnels
        benefits.put("max_homes", plugin.getConfig().getInt("vip.benefits.max_homes", 10));
        benefits.put("max_autominers", plugin.getConfig().getInt("vip.benefits.max_autominers", 5));
        benefits.put("daily_bonus", plugin.getConfig().getLong("vip.benefits.daily_bonus", 10000));

        // Permissions spéciales
        List<String> permissions = plugin.getConfig().getStringList("vip.benefits.permissions");
        if (permissions.isEmpty()) {
            permissions = List.of(
                    "prisontycoon.vip",
                    "prisontycoon.boost",
                    "prisontycoon.premium",
                    "prisontycoon.autoupgrade",
                    "prisontycoon.blackmarket"
            );
        }
        benefits.put("permissions", permissions);

        return benefits;
    }

    /**
     * Initialise le fichier VIP fallback
     */
    private void initializeFile() {
        if (!vipFile.exists()) {
            try {
                vipFile.getParentFile().mkdirs();
                vipFile.createNewFile();
                plugin.getPluginLogger().info("Fichier VIP fallback créé: " + vipFile.getName());
            } catch (IOException e) {
                plugin.getPluginLogger().severe("Impossible de créer le fichier VIP: " + e.getMessage());
                e.printStackTrace();
            }
        }
        vipConfig = YamlConfiguration.loadConfiguration(vipFile);
    }

    /**
     * Charge tous les VIP existants
     */
    private void loadVips() {
        vipCache.clear();

        if (plugin.isLuckPermsEnabled()) {
            // Charge depuis LuckPerms
            loadVipsFromLuckPerms();
        } else {
            // Charge depuis le fichier fallback
            loadVipsFromFile();
        }

        plugin.getPluginLogger().info("VIP chargés: " + vipCache.size() + " joueurs");
    }

    /**
     * Charge les VIP depuis LuckPerms
     * INTÉGRATION NATIVE LUCKPERMS
     */
    private void loadVipsFromLuckPerms() {
        if (!plugin.isLuckPermsEnabled()) return;

        try {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();

            // Charge tous les utilisateurs en ligne et synchronise
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                User user = userManager.getUser(player.getUniqueId());
                if (user != null) {
                    boolean isVip = user.getNodes().stream()
                            .filter(node -> node instanceof InheritanceNode)
                            .map(node -> ((InheritanceNode) node).getGroupName())
                            .anyMatch(group -> vipGroups.contains(group.toLowerCase()));

                    if (isVip) {
                        String group = user.getPrimaryGroup();
                        Instant expiry = user.getNodes().stream()
                                .filter(node -> node instanceof InheritanceNode)
                                .filter(node -> vipGroups.contains(((InheritanceNode) node).getGroupName().toLowerCase()))
                                .map(node -> node.getExpiry())
                                .filter(Objects::nonNull)
                                .findFirst()
                                .orElse(null);

                        VipData vipData = new VipData(player.getName(), group, expiry, "LuckPerms", Instant.now());
                        vipCache.put(player.getUniqueId(), vipData);
                    }
                }
            }

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur chargement VIP depuis LuckPerms: " + e.getMessage());
        }
    }

    /**
     * Charge les VIP depuis le fichier fallback
     */
    private void loadVipsFromFile() {
        if (!vipConfig.contains("vips")) return;

        for (String uuidString : vipConfig.getConfigurationSection("vips").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                String path = "vips." + uuidString + ".";

                String playerName = vipConfig.getString(path + "name", "Unknown");
                String group = vipConfig.getString(path + "group", defaultVipGroup);
                String addedBy = vipConfig.getString(path + "added_by", "Unknown");

                Instant addedTime = Instant.ofEpochMilli(vipConfig.getLong(path + "added_time", System.currentTimeMillis()));

                Instant expiry = null;
                if (vipConfig.contains(path + "expiry")) {
                    expiry = Instant.ofEpochMilli(vipConfig.getLong(path + "expiry"));
                }

                VipData vipData = new VipData(playerName, group, expiry, addedBy, addedTime);
                vipCache.put(uuid, vipData);

            } catch (IllegalArgumentException e) {
                plugin.getPluginLogger().warning("UUID VIP invalide: " + uuidString);
            }
        }
    }

    /**
     * Vérifie si un joueur est VIP
     * INTÉGRATION NATIVE
     */
    public boolean isVip(@NotNull Player player) {
        return isVip(player.getUniqueId());
    }

    /**
     * Vérifie si un UUID est VIP
     */
    public boolean isVip(@NotNull UUID uuid) {
        if (plugin.isLuckPermsEnabled()) {
            // INTÉGRATION LUCKPERMS NATIVE
            return isVipInLuckPerms(uuid);
        } else {
            // FALLBACK vers cache local
            VipData vipData = vipCache.get(uuid);
            return vipData != null && !vipData.isExpired();
        }
    }

    /**
     * Vérifie VIP dans LuckPerms
     * INTÉGRATION NATIVE LUCKPERMS
     */
    private boolean isVipInLuckPerms(@NotNull UUID uuid) {
        try {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();
            User user = userManager.getUser(uuid);

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
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Erreur vérification VIP LuckPerms " + uuid + ": " + e.getMessage());
        }

        return false;
    }

    /**
     * Ajoute un joueur VIP
     * INTÉGRATION NATIVE
     */
    public CompletableFuture<Boolean> addVip(@NotNull UUID uuid, @NotNull String playerName, @NotNull String addedBy) {
        return addVip(uuid, playerName, addedBy, null, defaultVipGroup);
    }

    /**
     * Ajoute un joueur VIP temporaire
     */
    public CompletableFuture<Boolean> addTemporaryVip(@NotNull UUID uuid, @NotNull String playerName,
                                                      @NotNull String addedBy, @NotNull Duration duration) {
        Instant expiry = Instant.now().plus(duration);
        return addVip(uuid, playerName, addedBy, expiry, defaultVipGroup);
    }

    /**
     * Ajoute un joueur VIP avec groupe spécifique
     */
    public CompletableFuture<Boolean> addVip(@NotNull UUID uuid, @NotNull String playerName, @NotNull String addedBy,
                                             Instant expiry, @NotNull String group) {

        if (plugin.isLuckPermsEnabled()) {
            // INTÉGRATION LUCKPERMS NATIVE
            return addVipInLuckPerms(uuid, group, expiry).thenApply(success -> {
                if (success) {
                    // Met à jour le cache local
                    VipData vipData = new VipData(playerName, group, expiry, addedBy, Instant.now());
                    vipCache.put(uuid, vipData);

                    // Synchronise avec PlayerData
                    synchronizeWithPlayerData(uuid, true);

                    // Applique les avantages VIP
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null) {
                        applyVipBenefits(player);
                    }

                    String expiryText = expiry != null ? " (expire: " + expiry + ")" : " (permanent)";
                    plugin.getPluginLogger().info("VIP ajouté (LuckPerms): " + playerName + " -> " + group + expiryText);
                }
                return success;
            });
        } else {
            // FALLBACK vers fichier
            return CompletableFuture.supplyAsync(() -> {
                VipData vipData = new VipData(playerName, group, expiry, addedBy, Instant.now());
                vipCache.put(uuid, vipData);

                // Sauvegarde dans le fichier
                saveVipToFile(uuid, vipData);

                // Synchronise avec PlayerData
                synchronizeWithPlayerData(uuid, true);

                // Applique les avantages VIP
                Player player = plugin.getServer().getPlayer(uuid);
                if (player != null) {
                    applyVipBenefits(player);
                }

                String expiryText = expiry != null ? " (expire: " + expiry + ")" : " (permanent)";
                plugin.getPluginLogger().info("VIP ajouté (fichier): " + playerName + " -> " + group + expiryText);

                return true;
            });
        }
    }

    /**
     * Ajoute VIP dans LuckPerms
     * INTÉGRATION NATIVE LUCKPERMS
     */
    private CompletableFuture<Boolean> addVipInLuckPerms(@NotNull UUID uuid, @NotNull String group, Instant expiry) {
        try {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();

            return userManager.loadUser(uuid).thenCompose(user -> {
                if (user == null) return CompletableFuture.completedFuture(false);

                // Crée le nœud de groupe
                InheritanceNode.Builder nodeBuilder = InheritanceNode.builder(group);
                if (expiry != null) {
                    nodeBuilder.expiry(expiry);
                }
                InheritanceNode node = nodeBuilder.build();

                // Ajoute le nœud
                user.data().add(node);

                // Ajoute aussi les permissions VIP
                @SuppressWarnings("unchecked")
                List<String> vipPermissions = (List<String>) vipBenefits.get("permissions");
                for (String permission : vipPermissions) {
                    var permNode = net.luckperms.api.node.types.PermissionNode.builder(permission);
                    if (expiry != null) {
                        permNode.expiry(expiry);
                    }
                    user.data().add(permNode.build());
                }

                // Sauvegarde
                return userManager.saveUser(user).thenApply(v -> true);
            });

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur ajout VIP LuckPerms:");
            e.printStackTrace();
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Retire le statut VIP d'un joueur
     */
    public CompletableFuture<Boolean> removeVip(@NotNull UUID uuid) {
        if (plugin.isLuckPermsEnabled()) {
            // INTÉGRATION LUCKPERMS NATIVE
            return removeVipFromLuckPerms(uuid).thenApply(success -> {
                if (success) {
                    vipCache.remove(uuid);
                    synchronizeWithPlayerData(uuid, false);

                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null) {
                        removeVipBenefits(player);
                        plugin.getPluginLogger().info("VIP retiré (LuckPerms): " + player.getName());
                    }
                }
                return success;
            });
        } else {
            // FALLBACK vers fichier
            return CompletableFuture.supplyAsync(() -> {
                VipData removed = vipCache.remove(uuid);
                if (removed != null) {
                    removeVipFromFile(uuid);
                    synchronizeWithPlayerData(uuid, false);

                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null) {
                        removeVipBenefits(player);
                        plugin.getPluginLogger().info("VIP retiré (fichier): " + player.getName());
                    }
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Retire VIP de LuckPerms
     */
    private CompletableFuture<Boolean> removeVipFromLuckPerms(@NotNull UUID uuid) {
        try {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();

            return userManager.loadUser(uuid).thenCompose(user -> {
                if (user == null) return CompletableFuture.completedFuture(false);

                boolean changed = false;

                // Retire tous les groupes VIP
                for (String vipGroup : vipGroups) {
                    InheritanceNode node = InheritanceNode.builder(vipGroup).build();
                    if (user.data().remove(node).wasSuccessful()) {
                        changed = true;
                    }
                }

                // Retire les permissions VIP
                @SuppressWarnings("unchecked")
                List<String> vipPermissions = (List<String>) vipBenefits.get("permissions");
                for (String permission : vipPermissions) {
                    var permNode = net.luckperms.api.node.types.PermissionNode.builder(permission).build();
                    if (user.data().remove(permNode).wasSuccessful()) {
                        changed = true;
                    }
                }

                if (changed) {
                    return userManager.saveUser(user).thenApply(v -> true);
                } else {
                    return CompletableFuture.completedFuture(false);
                }
            });

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur retrait VIP LuckPerms:");
            e.printStackTrace();
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Synchronise le statut VIP avec PlayerData
     */
    private void synchronizeWithPlayerData(@NotNull UUID uuid, boolean isVip) {
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(uuid);
            if (playerData.isVip(player) != isVip) {
                playerData.setVip(player, isVip);
                plugin.getPluginLogger().debug("PlayerData VIP synchronisé: " + uuid + " -> " + isVip);
            }
        }
    }

    /**
     * Applique les avantages VIP à un joueur
     */
    public void applyVipBenefits(@NotNull Player player) {
        if (!isVip(player)) return;

        try {
            // Applique les permissions via PermissionManager
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) vipBenefits.get("permissions");
            for (String permission : permissions) {
                plugin.getPermissionManager().attachPermission(player, permission);
            }

            // Met à jour le tab
            plugin.getTabManager().updatePlayerTab(player);

            // Notifications
            player.sendMessage("§6✨ Avantages VIP activés!");
            player.sendMessage("§7- Multiplicateur coins: §6×" + vipBenefits.get("coin_multiplier"));
            player.sendMessage("§7- Multiplicateur tokens: §b×" + vipBenefits.get("token_multiplier"));
            player.sendMessage("§7- Multiplicateur XP: §a×" + vipBenefits.get("xp_multiplier"));

            plugin.getPluginLogger().debug("Avantages VIP appliqués à " + player.getName());

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur application avantages VIP " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Retire les avantages VIP d'un joueur
     */
    public void removeVipBenefits(@NotNull Player player) {
        try {
            // Retire les permissions via PermissionManager
            @SuppressWarnings("unchecked")
            List<String> permissions = (List<String>) vipBenefits.get("permissions");
            for (String permission : permissions) {
                plugin.getPermissionManager().removePermission(player, permission);
            }

            // Met à jour le tab
            plugin.getTabManager().updatePlayerTab(player);

            player.sendMessage("§cStatut VIP expiré.");

            plugin.getPluginLogger().debug("Avantages VIP retirés de " + player.getName());

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur retrait avantages VIP " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Sauvegarde un VIP dans le fichier fallback
     */
    private void saveVipToFile(@NotNull UUID uuid, @NotNull VipData vipData) {
        String path = "vips." + uuid + ".";

        vipConfig.set(path + "name", vipData.playerName);
        vipConfig.set(path + "group", vipData.group);
        vipConfig.set(path + "added_by", vipData.addedBy);
        vipConfig.set(path + "added_time", vipData.addedTime.toEpochMilli());

        if (vipData.expiry != null) {
            vipConfig.set(path + "expiry", vipData.expiry.toEpochMilli());
        }

        try {
            vipConfig.save(vipFile);
        } catch (IOException e) {
            plugin.getPluginLogger().severe("Erreur sauvegarde VIP: " + e.getMessage());
        }
    }

    /**
     * Retire un VIP du fichier fallback
     */
    private void removeVipFromFile(@NotNull UUID uuid) {
        vipConfig.set("vips." + uuid, null);

        try {
            vipConfig.save(vipFile);
        } catch (IOException e) {
            plugin.getPluginLogger().severe("Erreur suppression VIP: " + e.getMessage());
        }
    }

    /**
     * Démarre les tâches de maintenance
     */
    private void startMaintenanceTasks() {
        // Vérifie les VIP expirés toutes les 10 minutes
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            checkExpiredVips();
        }, 12000L, 12000L); // 10 minutes
    }

    /**
     * Vérifie et retire les VIP expirés
     */
    private void checkExpiredVips() {
        List<UUID> expiredVips = new ArrayList<>();

        for (Map.Entry<UUID, VipData> entry : vipCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                expiredVips.add(entry.getKey());
            }
        }

        for (UUID uuid : expiredVips) {
            removeVip(uuid).thenAccept(success -> {
                if (success) {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player != null) {
                        player.sendMessage("§c⏰ Votre statut VIP a expiré!");
                    }
                    plugin.getPluginLogger().info("VIP expiré automatiquement: " + uuid);
                }
            });
        }

        if (!expiredVips.isEmpty()) {
            plugin.getPluginLogger().info("VIP expirés nettoyés: " + expiredVips.size());
        }
    }

    /**
     * Obtient les informations VIP d'un joueur
     */
    public VipData getVipData(@NotNull UUID uuid) {
        return vipCache.get(uuid);
    }

    /**
     * Obtient tous les VIP
     */
    public Map<UUID, VipData> getAllVips() {
        return new HashMap<>(vipCache);
    }

    /**
     * Recharge les VIP
     */
    public void reload() {
        loadVips();
        plugin.getPluginLogger().info("VipManager rechargé");
    }

    /**
     * Classe pour stocker les données VIP
     */
    public static class VipData {
        public final String playerName;
        public final String group;
        public final Instant expiry; // null = permanent
        public final String addedBy;
        public final Instant addedTime;

        public VipData(String playerName, String group, Instant expiry, String addedBy, Instant addedTime) {
            this.playerName = playerName;
            this.group = group;
            this.expiry = expiry;
            this.addedBy = addedBy;
            this.addedTime = addedTime;
        }

        public boolean isExpired() {
            return expiry != null && Instant.now().isAfter(expiry);
        }

        public boolean isPermanent() {
            return expiry == null;
        }
    }
}