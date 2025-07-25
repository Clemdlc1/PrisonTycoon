package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire pour les joueurs VIP
 */
public class VipManager {

    private final PrisonTycoon plugin;
    private final File vipFile;
    // Cache des VIP pour les performances
    private final Set<UUID> vipCache = ConcurrentHashMap.newKeySet();
    private FileConfiguration vipConfig;

    public VipManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.vipFile = new File(plugin.getDataFolder(), "vips.yml");
        initializeFile();
        loadVips();
    }

    /**
     * Initialise le fichier VIP
     */
    private void initializeFile() {
        if (!vipFile.exists()) {
            try {
                vipFile.getParentFile().mkdirs();
                vipFile.createNewFile();
                plugin.getPluginLogger().info("Fichier VIP créé: " + vipFile.getName());
            } catch (IOException e) {
                plugin.getPluginLogger().severe("Impossible de créer le fichier VIP: " + e.getMessage());
                e.printStackTrace();
            }
        }
        vipConfig = YamlConfiguration.loadConfiguration(vipFile);
    }

    /**
     * Charge tous les VIP depuis le fichier
     */
    private void loadVips() {
        vipCache.clear();

        if (vipConfig.contains("vips")) {
            for (String uuidString : vipConfig.getConfigurationSection("vips").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    vipCache.add(uuid);
                } catch (IllegalArgumentException e) {
                    plugin.getPluginLogger().warning("UUID VIP invalide: " + uuidString);
                }
            }
        }

        plugin.getPluginLogger().info("VIP chargés: " + vipCache.size() + " joueurs");
    }

    /**
     * Sauvegarde les VIP dans le fichier
     */
    private void saveVips() {
        try {
            vipConfig.save(vipFile);
        } catch (IOException e) {
            plugin.getPluginLogger().severe("Erreur lors de la sauvegarde des VIP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // CORRECTION: VipManager.java - VIP = Permission uniquement

    /**
     * SIMPLIFIÉ: Ajoute un joueur VIP (donne la permission)
     */
    public void addVip(UUID uuid, Player target, Player addedBy) {
        // Enregistre dans le fichier pour historique/logs
        String path = "vips." + uuid + ".";
        vipConfig.set(path + "playerName", target);
        vipConfig.set(path + "addedBy", addedBy);
        vipConfig.set(path + "addedAt", System.currentTimeMillis());
        saveVips();

        // Ajoute au cache
        vipCache.add(uuid);

        plugin.getPermissionManager().attachPermission(target, "specialmine.vip");

        // Log
        plugin.getPluginLogger().info("VIP ajouté: " + target + " (" + uuid + ") par " + addedBy);
        plugin.getPluginLogger().info("Permission specialmine.vip accordée automatiquement");
    }

    /**
     * NOUVEAU: Retire un joueur VIP (retire la permission directement)
     */
    public void removeVip(Player player, String removedBy) {
        String playerName = vipConfig.getString("vips." + player + ".playerName", "Inconnu");

        // Retire du fichier et cache
        vipCache.remove(player.getUniqueId());
        vipConfig.set("vips." + player, null);
        saveVips();

        // NOUVEAU: Retire la permission directement de PlayerData
        plugin.getPermissionManager().detachPermission(player, "specialmine.vip");

        // Log
        plugin.getPluginLogger().info("VIP retiré: " + playerName + " (" + player + ") par " + removedBy);
        plugin.getPluginLogger().info("Permission specialmine.vip retirée automatiquement");
    }

    /**
     * NOUVEAU: Vérifie si un joueur est VIP (via permission Bukkit + données)
     */
    public boolean isVip(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);

        // Pour joueurs hors ligne: vérifier données stockées
        return plugin.getPermissionManager().hasPermission(player, "specialmine.vip");
    }

    /**
     * NOUVEAU: Vérifie la cohérence permission/cache/données pour un joueur en ligne
     */
    public boolean checkVipConsistency(Player player) {
        UUID uuid = player.getUniqueId();
        boolean hasPermissionBukkit = player.hasPermission("specialmine.vip");
        boolean hasPermissionData = plugin.getPermissionManager().hasPermission(player, "specialmine.vip");
        boolean inCache = vipCache.contains(uuid);

        boolean consistent = hasPermissionBukkit == hasPermissionData && hasPermissionData == inCache;

        if (!consistent) {
            plugin.getPluginLogger().warning("§c⚠️ INCOHÉRENCE VIP pour " + player.getName() +
                    " - Bukkit: " + hasPermissionBukkit +
                    ", Données: " + hasPermissionData +
                    ", Cache: " + inCache);
        }

        return consistent;
    }

    /**
     * NOUVEAU: Synchronise tout (cache, données, permissions Bukkit)
     */
    public void syncAllVipData() {
        int synced = 0;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean hasPermissionBukkit = player.hasPermission("specialmine.vip");
            boolean hasPermissionData = plugin.getPermissionManager().hasPermission(player, "specialmine.vip");
            boolean inCache = vipCache.contains(uuid);

            // Source de vérité = données stockées (PlayerData)

            // Synchronise le cache
            if (hasPermissionData && !inCache) {
                vipCache.add(uuid);
                plugin.getPluginLogger().info("§7Ajouté au cache VIP: " + player.getName());
                synced++;
            } else if (!hasPermissionData && inCache) {
                vipCache.remove(uuid);
                plugin.getPluginLogger().info("§7Retiré du cache VIP: " + player.getName());
                synced++;
            }

            // Synchronise les permissions Bukkit
            if (hasPermissionData && !hasPermissionBukkit) {
                plugin.getPermissionManager().attachPermission(player, "specialmine.vip");
                plugin.getPluginLogger().info("§7Permission attachée: " + player.getName());
                synced++;
            } else if (!hasPermissionData && hasPermissionBukkit) {
                plugin.getPermissionManager().detachPermission(player, "specialmine.vip");
                plugin.getPluginLogger().info("§7Permission détachée: " + player.getName());
                synced++;
            }
        }

        if (synced > 0) {
            plugin.getPluginLogger().info("§aSynchronisation VIP complète: " + synced + " éléments mis à jour");
        } else {
            plugin.getPluginLogger().info("§aTout est synchronisé, aucune correction nécessaire");
        }
    }

    /**
     * NOUVEAU: Obtient le statut VIP détaillé (debug)
     */
    public String getVipStatusDetailed(UUID uuid) {
        Player player = plugin.getServer().getPlayer(uuid);
        String playerName = player != null ? player.getName() :
                vipConfig.getString("vips." + uuid + ".playerName", "Inconnu");

        boolean hasPermissionBukkit = player != null && player.hasPermission("specialmine.vip");
        boolean hasPermissionData = plugin.getPermissionManager().hasPermission(player, "specialmine.vip");
        boolean inCache = vipCache.contains(uuid);

        return String.format("§e%s §7- Bukkit: %s§7, Données: %s§7, Cache: %s",
                playerName,
                hasPermissionBukkit ? "§aOUI" : "§cNON",
                hasPermissionData ? "§aOUI" : "§cNON",
                inCache ? "§aOUI" : "§cNON");
    }

    /**
     * NOUVEAU: Force la synchronisation d'un joueur spécifique
     */
    public void forcePlayerSync(Player player) {
        UUID uuid = player.getUniqueId();

        boolean shouldBeVip = plugin.getPermissionManager().hasPermission(player, "specialmine.vip");

        boolean hasPermissionBukkit = player.hasPermission("specialmine.vip");

        if (shouldBeVip != hasPermissionBukkit) {
            plugin.getPermissionManager().reloadPlayerPermissions(player);
            plugin.getPluginLogger().info("Permissions rechargées pour " + player.getName() + " (VIP: " + shouldBeVip + ")");
        }

        // Met à jour le cache
        if (shouldBeVip) {
            vipCache.add(uuid);
        } else {
            vipCache.remove(uuid);
        }

        // Validation finale
        boolean finalCheck = player.hasPermission("specialmine.vip");
        if (finalCheck != shouldBeVip) {
            plugin.getPluginLogger().warning("⚠️ Échec de synchronisation pour " + player.getName() +
                    " - Attendu: " + shouldBeVip + ", Actuel: " + finalCheck);
        } else {
            plugin.getPluginLogger().info("✅ Synchronisation réussie pour " + player.getName() + " (VIP: " + shouldBeVip + ")");
        }
    }

    /**
     * Obtient les informations d'un VIP
     */
    public VipData getVipData(UUID uuid) {
        if (!isVip(uuid)) return null;

        String path = "vips." + uuid + ".";
        return new VipData(
                uuid,
                vipConfig.getString(path + "playerName"),
                vipConfig.getString(path + "addedBy"),
                vipConfig.getLong(path + "addedAt")
        );
    }

    /**
     * Obtient le nombre de VIP
     */
    public int getVipCount() {
        return vipCache.size();
    }

    /**
     * Obtient tous les UUID des VIP
     */
    public Set<UUID> getAllVips() {
        return new HashSet<>(vipCache);
    }

    /**
     * Recharge le système VIP
     */
    public void reload() {
        vipConfig = YamlConfiguration.loadConfiguration(vipFile);
        loadVips();
        plugin.getPluginLogger().info("Système VIP rechargé");
    }

    /**
     * Classe pour stocker les données VIP
     */
    public record VipData(UUID uuid, String playerName, String addedBy, long addedAt) {
    }
}