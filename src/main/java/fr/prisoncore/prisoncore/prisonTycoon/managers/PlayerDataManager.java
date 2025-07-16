package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des données des joueurs
 * CORRIGÉ : Synchronisation exp vanilla lors du chargement et expérience mise à jour
 */
public class PlayerDataManager {

    private final PrisonTycoon plugin;
    private final File playerDataFolder;

    // Cache mémoire des données joueurs (UUID -> PlayerData)
    private final Map<UUID, PlayerData> playerDataCache;

    // Set des joueurs avec données modifiées (pour sauvegarde optimisée)
    private final Set<UUID> dirtyPlayers;

    public PlayerDataManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerDataCache = new ConcurrentHashMap<>();
        this.dirtyPlayers = ConcurrentHashMap.newKeySet();

        // Crée le dossier des données joueurs
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
            plugin.getPluginLogger().info("§7Dossier playerdata créé.");
        }

        plugin.getPluginLogger().info("§aPlayerDataManager initialisé.");
    }

    /**
     * CORRIGÉ : Charge les données d'un joueur avec synchronisation exp vanilla
     */
    public PlayerData getPlayerData(UUID playerId) {
        // Vérifie d'abord le cache
        PlayerData cached = playerDataCache.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Charge depuis le fichier
        PlayerData loaded = loadPlayerDataFromFile(playerId);
        if (loaded != null) {
            playerDataCache.put(playerId, loaded);

            // NOUVEAU : Synchronise l'exp vanilla si le joueur est en ligne
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getEconomyManager().updateVanillaExpFromCustom(player, loaded.getExperience());
                }, 1L); // Petit délai pour s'assurer que tout est initialisé
            }

            return loaded;
        }

        // Crée de nouvelles données si aucun fichier trouvé
        String playerName = getPlayerName(playerId);
        PlayerData newData = new PlayerData(playerId, playerName);
        playerDataCache.put(playerId, newData);
        markDirty(playerId);

        plugin.getPluginLogger().info("§7Nouvelles données créées pour: " + playerName);
        return newData;
    }

    /**
     * CORRIGÉ: Charge les données d'un joueur depuis le fichier YAML avec TOUTES les nouvelles données
     */
    private PlayerData loadPlayerDataFromFile(UUID playerId) {
        File playerFile = new File(playerDataFolder, playerId.toString() + ".yml");
        if (!playerFile.exists()) {
            return null;
        }

        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

            String playerName = config.getString("name", "Unknown");
            PlayerData data = new PlayerData(playerId, playerName);

            // Monnaies TOTALES
            long savedCoins = config.getLong("coins", 0);
            long savedTokens = config.getLong("tokens", 0);
            long savedExperience = config.getLong("experience", 0);
            long savedBeacons = config.getLong("beacons", 0);

            if (savedCoins > 0) data.setCoins(savedCoins);
            if (savedTokens > 0) data.setTokens(savedTokens);
            if (savedExperience > 0) data.setExperience(savedExperience);
            if (savedBeacons > 0) data.setBeacons(savedBeacons);


            // NOUVEAU: Gains spécifiques VIA PIOCHE
            long coinsViaPickaxe = config.getLong("coins-via-pickaxe", 0);
            long tokensViaPickaxe = config.getLong("tokens-via-pickaxe", 0);
            long experienceViaPickaxe = config.getLong("experience-via-pickaxe", 0);

            if (coinsViaPickaxe > 0) data.setCoinsViaPickaxe(coinsViaPickaxe);
            if (tokensViaPickaxe > 0) data.setTokensViaPickaxe(tokensViaPickaxe);
            if (experienceViaPickaxe > 0) data.setExperienceViaPickaxe(experienceViaPickaxe);

            // Enchantements
            if (config.contains("enchantments")) {
                for (String enchName : config.getConfigurationSection("enchantments").getKeys(false)) {
                    int level = config.getInt("enchantments." + enchName, 0);
                    if (level > 0) {
                        data.setEnchantmentLevel(enchName, level);
                    }
                }
            }

            // Auto-upgrade
            if (config.contains("auto-upgrade")) {
                List<String> autoUpgradeList = config.getStringList("auto-upgrade");
                for (String enchName : autoUpgradeList) {
                    data.setAutoUpgrade(enchName, true);
                }
            }

            // NOUVEAU: Enchantements mobilité désactivés
            if (config.contains("mobility-disabled")) {
                List<String> disabledMobility = config.getStringList("mobility-disabled");
                for (String enchName : disabledMobility) {
                    data.setMobilityEnchantmentEnabled(enchName, false);
                }
            }

            if (config.contains("mine-permissions")) {
                List<String> minePermissions = config.getStringList("mine-permissions");
                for (String mineName : minePermissions) {
                    data.addMinePermission(mineName);
                }
            }

            if (config.contains("pickaxe-cristals")) {
                for (String cristalUuid : config.getConfigurationSection("pickaxe-cristals").getKeys(false)) {
                    String cristalData = config.getString("pickaxe-cristals." + cristalUuid);
                    if (cristalData != null && !cristalData.isEmpty()) {
                        data.setPickaxeCristal(cristalUuid, cristalData);
                    }
                }
            }

            if (config.contains("custom-permissions")) {
                List<String> permissions = config.getStringList("custom-permissions");
                Set<String> permissionSet = new HashSet<>(permissions);
                data.setCustomPermissions(permissionSet);
            }

            // NOUVEAU: Historique des sanctions
            if (config.contains("sanctions")) {
                for (String sanctionId : config.getConfigurationSection("sanctions").getKeys(false)) {
                    String path = "sanctions." + sanctionId + ".";
                    String type = config.getString(path + "type");
                    String reason = config.getString(path + "reason");
                    String moderator = config.getString(path + "moderator");
                    long startTime = config.getLong(path + "startTime");
                    long endTime = config.getLong(path + "endTime");

                    data.addSanction(type, reason, moderator, startTime, endTime);
                }
            }

            // Statistiques de base
            data.setTotalBlocksMined(config.getLong("statistics.total-blocks-mined", 0));
            data.setTotalBlocksDestroyed(config.getLong("statistics.total-blocks-destroyed",
                    config.getLong("statistics.total-blocks-mined", 0))); // Fallback pour compatibilité

            // Statistiques spécialisées
            data.setTotalGreedTriggers(config.getLong("statistics.total-greed-triggers", 0));
            data.setTotalKeysObtained(config.getLong("statistics.total-keys-obtained", 0));

            // Reset des stats de la dernière minute après chargement
            data.resetLastMinuteStats();

            plugin.getPluginLogger().info("§7Données chargées pour: " + playerName +
                    " (" + savedCoins + " coins, " + savedTokens + " tokens, " + savedExperience + " exp)" +
                    " [Pioche: " + coinsViaPickaxe + "c, " + tokensViaPickaxe + "t, " + experienceViaPickaxe + "e]");

            return data;

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors du chargement des données pour " + playerId + ":");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * CORRIGÉ: Sauvegarde les données d'un joueur avec TOUTES les nouvelles données
     */
    private void savePlayerDataToFile(UUID playerId, PlayerData data) {
        File playerFile = new File(playerDataFolder, playerId.toString() + ".yml");

        try {
            FileConfiguration config = new YamlConfiguration();

            // Informations de base
            config.set("name", data.getPlayerName());
            config.set("uuid", playerId.toString());
            config.set("last-save", System.currentTimeMillis());

            // Monnaies TOTALES
            config.set("coins", data.getCoins());
            config.set("tokens", data.getTokens());
            config.set("experience", data.getExperience());
            config.set("beacons", data.getBeacons());

            // NOUVEAU: Gains spécifiques VIA PIOCHE
            config.set("coins-via-pickaxe", data.getCoinsViaPickaxe());
            config.set("tokens-via-pickaxe", data.getTokensViaPickaxe());
            config.set("experience-via-pickaxe", data.getExperienceViaPickaxe());

            plugin.getPluginLogger().debug("Sauvegarde de " + data.getPlayerName() +
                    ": " + data.getCoins() + " coins (" + data.getCoinsViaPickaxe() + " pioche), " +
                    data.getTokens() + " tokens (" + data.getTokensViaPickaxe() + " pioche), " +
                    data.getExperience() + " exp (" + data.getExperienceViaPickaxe() + " pioche)" +
                    data.getBeacons() + " beacons");

            // Enchantements
            Map<String, Integer> enchantments = data.getEnchantmentLevels();
            if (!enchantments.isEmpty()) {
                for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                    config.set("enchantments." + entry.getKey(), entry.getValue());
                }
            }

            // Auto-upgrade activé
            Set<String> autoUpgrade = data.getAutoUpgradeEnabled();
            if (!autoUpgrade.isEmpty()) {
                config.set("auto-upgrade", new ArrayList<>(autoUpgrade));
            }

            // NOUVEAU: Enchantements mobilité désactivés
            Set<String> mobilityDisabled = data.getMobilityEnchantmentsDisabled();
            if (!mobilityDisabled.isEmpty()) {
                config.set("mobility-disabled", new ArrayList<>(mobilityDisabled));
            }

            Set<String> minePermissions = data.getMinePermissions();
            if (!minePermissions.isEmpty()) {
                config.set("mine-permissions", new ArrayList<>(minePermissions));
            }

            Map<String, String> pickaxeCristals = data.getPickaxeCristals();
            if (!pickaxeCristals.isEmpty()) {
                for (Map.Entry<String, String> entry : pickaxeCristals.entrySet()) {
                    config.set("pickaxe-cristals." + entry.getKey(), entry.getValue());
                }
            }

            // NOUVEAU: Historique des sanctions
            List<PlayerData.SanctionData> sanctions = data.getSanctionHistory();
            if (!sanctions.isEmpty()) {
                for (int i = 0; i < sanctions.size(); i++) {
                    PlayerData.SanctionData sanction = sanctions.get(i);
                    String path = "sanctions." + i + ".";
                    config.set(path + "type", sanction.getType());
                    config.set(path + "reason", sanction.getReason());
                    config.set(path + "moderator", sanction.getModerator());
                    config.set(path + "startTime", sanction.getStartTime());
                    config.set(path + "endTime", sanction.getEndTime());
                }
            }

            Set<String> permissions = data.getCustomPermissions();
            if (!permissions.isEmpty()) {
                config.set("custom-permissions", new ArrayList<>(permissions));
            }

            // Statistiques complètes
            config.set("statistics.total-blocks-mined", data.getTotalBlocksMined());
            config.set("statistics.total-blocks-destroyed", data.getTotalBlocksDestroyed());
            config.set("statistics.total-greed-triggers", data.getTotalGreedTriggers());
            config.set("statistics.total-keys-obtained", data.getTotalKeysObtained());

            // Sauvegarde le fichier
            config.save(playerFile);

            plugin.getPluginLogger().debug("Fichier sauvegardé pour " + data.getPlayerName() +
                    " avec " + data.getTotalGreedTriggers() + " Greeds et " + data.getTotalKeysObtained() + " clés");

        } catch (IOException e) {
            plugin.getPluginLogger().severe("§cErreur lors de la sauvegarde pour " + data.getPlayerName() + ":");
            e.printStackTrace();
        }
    }

    /**
     * Marque un joueur comme ayant des données modifiées
     */
    public void markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
        plugin.getPluginLogger().debug("Joueur marqué dirty: " + playerId);
    }

    /**
     * Sauvegarde asynchrone de tous les joueurs avec données modifiées
     */
    public CompletableFuture<Void> saveAllPlayersAsync() {
        return CompletableFuture.runAsync(() -> {
            int savedCount = 0;

            // Copie la liste des joueurs à sauvegarder
            Set<UUID> toSave = new HashSet<>(dirtyPlayers);

            for (UUID playerId : toSave) {
                PlayerData data = playerDataCache.get(playerId);
                if (data != null) {
                    savePlayerDataToFile(playerId, data);
                    dirtyPlayers.remove(playerId);
                    savedCount++;
                }
            }

            if (savedCount > 0) {
                plugin.getPluginLogger().info("§7Sauvegarde asynchrone: " + savedCount + " joueurs.");
            }
        });
    }

    /**
     * Sauvegarde synchrone de tous les joueurs (utiliser uniquement à l'arrêt)
     */
    public void saveAllPlayersSync() {
        int savedCount = 0;

        for (Map.Entry<UUID, PlayerData> entry : playerDataCache.entrySet()) {
            savePlayerDataToFile(entry.getKey(), entry.getValue());
            savedCount++;
        }

        dirtyPlayers.clear();
        plugin.getPluginLogger().info("§aSauvegarde synchrone terminée: " + savedCount + " joueurs.");
    }

    /**
     * Sauvegarde un joueur spécifique immédiatement
     */
    public void savePlayerNow(UUID playerId) {
        PlayerData data = playerDataCache.get(playerId);
        if (data != null) {
            savePlayerDataToFile(playerId, data);
            dirtyPlayers.remove(playerId);
        }
    }

    /**
     * CORRIGÉ : Retire un joueur du cache avec sauvegarde forcée et sync exp finale
     */
    public void unloadPlayer(UUID playerId) {
        // NOUVEAU : Synchronisation finale de l'expérience avant sauvegarde
        Player player = plugin.getServer().getPlayer(playerId);
        PlayerData data = playerDataCache.get(playerId);

        if (player != null && player.isOnline() && data != null) {
            try {
                // Sync finale pour s'assurer que l'exp vanilla est à jour
                plugin.getEconomyManager().updateVanillaExpFromCustom(player, data.getExperience());
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur sync exp finale pour " +
                        data.getPlayerName() + ": " + e.getMessage());
            }
        }

        // Sauvegarde FORCÉE avant de décharger
        if (data != null) {
            savePlayerDataToFile(playerId, data);
            dirtyPlayers.remove(playerId);
            plugin.getPluginLogger().info("§7Données sauvegardées pour: " + data.getPlayerName() +
                    " (" + data.getCoins() + " coins, " + data.getTokens() + " tokens, " +
                    data.getTotalGreedTriggers() + " Greeds)");
        }

        playerDataCache.remove(playerId);
        plugin.getPluginLogger().info("§7Joueur déchargé du cache: " + playerId);
    }

    /**
     * Ajoute des tokens à un joueur (commande admin)
     */
    public boolean addTokensToPlayer(UUID playerId, long amount) {
        PlayerData data = getPlayerData(playerId);
        data.addTokens(amount); // Tokens admin ne comptent pas comme "via pioche"
        markDirty(playerId);

        plugin.getPluginLogger().info("§7" + amount + " tokens ajoutés à " + data.getPlayerName());
        return true;
    }

    /**
     * Ajoute une permission de mine à un joueur
     */
    public boolean addMinePermissionToPlayer(UUID playerId, String mineName) {
        PlayerData data = getPlayerData(playerId);
        data.addMinePermission(mineName);
        markDirty(playerId);

        plugin.getPluginLogger().info("§7Permission mine '" + mineName + "' ajoutée à " + data.getPlayerName());
        return true;
    }

    /**
     * Supprime une permission de mine à un joueur
     */
    public boolean removeMinePermissionFromPlayer(UUID playerId, String mineName) {
        PlayerData data = getPlayerData(playerId);
        data.removeMinePermission(mineName);
        markDirty(playerId);

        plugin.getPluginLogger().info("§7Permission mine '" + mineName + "' supprimée de " + data.getPlayerName());
        return true;
    }

    /**
     * Vérifie si un joueur a la permission pour une mine
     */
    public boolean hasPlayerMinePermission(UUID playerId, String mineName) {
        PlayerData data = getPlayerData(playerId);
        return data.hasMinePermission(mineName);
    }

    /**
     * Retourne toutes les permissions de mine d'un joueur
     */
    public Set<String> getPlayerMinePermissions(UUID playerId) {
        PlayerData data = getPlayerData(playerId);
        return data.getMinePermissions();
    }

    /**
     * Obtient le nom d'un joueur
     */
    private String getPlayerName(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        // Essaie de récupérer depuis l'historique
        return plugin.getServer().getOfflinePlayer(playerId).getName();
    }

    /**
     * Retourne tous les joueurs en cache
     */
    public Collection<PlayerData> getAllCachedPlayers() {
        return new ArrayList<>(playerDataCache.values());
    }

    /**
     * Nettoie le cache des joueurs déconnectés
     */
    public void cleanupCache() {
        Set<UUID> onlinePlayerIds = new HashSet<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            onlinePlayerIds.add(player.getUniqueId());
        }

        // Sauvegarde et retire les joueurs hors ligne
        int removedCount = 0;
        Iterator<Map.Entry<UUID, PlayerData>> iterator = playerDataCache.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerData> entry = iterator.next();
            UUID playerId = entry.getKey();

            if (!onlinePlayerIds.contains(playerId)) {
                // Sauvegarde avant suppression
                savePlayerDataToFile(playerId, entry.getValue());
                dirtyPlayers.remove(playerId);

                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            plugin.getPluginLogger().info("§7Cache nettoyé: " + removedCount + " joueurs hors ligne retirés.");
        }
    }

    /**
     * Ajoute une sanction à l'historique d'un joueur
     */
    public void addSanctionToPlayer(UUID playerId, String type, String reason, String moderator, long startTime, long endTime) {
        PlayerData data = getPlayerData(playerId);
        data.addSanction(type, reason, moderator, startTime, endTime);
        markDirty(playerId);

        plugin.getPluginLogger().info("Sanction ajoutée à " + data.getPlayerName() + ": " + type + " par " + moderator);
    }

    /**
     * NOUVEAU: Ajoute une permission à un joueur
     */
    public void addPermissionToPlayer(UUID playerId, String permission) {
        PlayerData data = getPlayerData(playerId);
        data.addPermission(permission);
        markDirty(playerId);

        // Applique immédiatement si le joueur est en ligne
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            plugin.getPermissionManager().attachPermission(player, permission);
        }

        plugin.getPluginLogger().info("Permission ajoutée à " + data.getPlayerName() + ": " + permission);
    }

    /**
     * NOUVEAU: Retire une permission à un joueur
     */
    public void removePermissionFromPlayer(UUID playerId, String permission) {
        PlayerData data = getPlayerData(playerId);
        data.removePermission(permission);
        markDirty(playerId);

        // Retire immédiatement si le joueur est en ligne
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            plugin.getPermissionManager().detachPermission(player, permission);
        }

        plugin.getPluginLogger().info("Permission retirée de " + data.getPlayerName() + ": " + permission);
    }

    /**
     * NOUVEAU: Vérifie si un joueur a une permission custom
     */
    public boolean hasPlayerPermission(UUID playerId, String permission) {
        PlayerData data = getPlayerData(playerId);
        return data.hasCustomPermission(permission);
    }
}