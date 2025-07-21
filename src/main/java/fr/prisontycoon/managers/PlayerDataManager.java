package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.configuration.ConfigurationSection;
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

            //metier
            data.setActiveProfession(config.getString("active-profession"));
            data.setLastProfessionChange(config.getLong("last-profession-change", 0));

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

            if (config.contains("active-enchantments")) {
                List<String> activeEnchants = config.getStringList("active-enchantments");
                data.setActiveEnchantmentBooks(new HashSet<>(activeEnchants));
            }

            if (config.contains("pickaxe-enchantment-books")) {
                List<String> books = config.getStringList("pickaxe-enchantment-books");
                Set<String> booksSet = new HashSet<>(books);
                data.setPickaxeEnchantmentBook(booksSet);
            }

            // Niveaux de métiers
            if (config.contains("profession-levels")) {
                for (String profession : config.getConfigurationSection("profession-levels").getKeys(false)) {
                    int level = config.getInt("profession-levels." + profession);
                    data.setProfessionLevel(profession, level);
                }
            }

            // XP des métiers
            if (config.contains("profession-xp")) {
                for (String profession : config.getConfigurationSection("profession-xp").getKeys(false)) {
                    int xp = config.getInt("profession-xp." + profession);
                    data.setProfessionXP(profession, xp);
                }
            }

            // Talents
            if (config.contains("talent-levels")) {
                for (String profession : config.getConfigurationSection("talent-levels").getKeys(false)) {
                    for (String talent : config.getConfigurationSection("talent-levels." + profession).getKeys(false)) {
                        int level = config.getInt("talent-levels." + profession + "." + talent);
                        data.setTalentLevel(profession, talent, level);
                    }
                }
            }

            if (config.contains("kit-levels")) {
                for (String profession : config.getConfigurationSection("kit-levels").getKeys(false)) {
                    int level = config.getInt("kit-levels." + profession);
                    data.setKitLevel(profession, level);
                }
            }

            if (config.contains("profession-rewards")) {
                for (String profession : config.getConfigurationSection("profession-rewards").getKeys(false)) {
                    List<Integer> claimedLevels = config.getIntegerList("profession-rewards." + profession);
                    for (int level : claimedLevels) {
                        data.claimProfessionReward(profession, level);
                    }
                }
            }

            if (config.contains("prestige.talents")) {
                ConfigurationSection talentsSection = config.getConfigurationSection("prestige.talents");
                Map<PrestigeTalent, Integer> prestigeTalents = new HashMap<>();

                for (String talentName : talentsSection.getKeys(false)) {
                    try {
                        PrestigeTalent talent = PrestigeTalent.valueOf(talentName);
                        int level = talentsSection.getInt(talentName, 0);
                        if (level > 0) {
                            prestigeTalents.put(talent, level);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getPluginLogger().warning("Talent de prestige invalide trouvé pour " + playerName + ": " + talentName);
                    }
                }

                if (!prestigeTalents.isEmpty()) {
                    data.setPrestigeTalents(prestigeTalents);
                }
            }

            // Talents choisis par niveau
            if (config.contains("prestige.chosen-talents")) {
                ConfigurationSection chosenTalentsSection = config.getConfigurationSection("prestige.chosen-talents");
                Map<Integer, String> chosenTalents = new HashMap<>();

                for (String levelStr : chosenTalentsSection.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(levelStr);
                        String talentName = chosenTalentsSection.getString(levelStr);
                        if (talentName != null && !talentName.isEmpty()) {
                            chosenTalents.put(level, talentName);
                        }
                    } catch (NumberFormatException e) {
                        plugin.getPluginLogger().warning("Niveau de prestige invalide trouvé pour " + playerName + ": " + levelStr);
                    }
                }

                if (!chosenTalents.isEmpty()) {
                    data.setChosenPrestigeTalents(chosenTalents);
                }
            }

            // Récompenses spéciales réclamées
            if (config.contains("prestige.chosen-special-rewards")) {
                List<String> chosenRewardsList = config.getStringList("prestige.chosen-special-rewards");
                if (!chosenRewardsList.isEmpty()) {
                    for (String rewardId : chosenRewardsList) {
                        if (rewardId != null && !rewardId.isEmpty()) {
                            data.addChosenSpecialReward(rewardId);
                        }
                    }

                    plugin.getPluginLogger().debug("Chargé " + chosenRewardsList.size() + " récompenses spéciales pour " + playerName);
                }
            }

            // Récompenses débloquées (statut)
            if (config.contains("prestige.unlocked-rewards")) {
                ConfigurationSection unlockedSection = config.getConfigurationSection("prestige.unlocked-rewards");
                Map<String, Boolean> unlockedRewards = new HashMap<>();

                for (String rewardId : unlockedSection.getKeys(false)) {
                    boolean unlocked = unlockedSection.getBoolean(rewardId, false);
                    unlockedRewards.put(rewardId, unlocked);
                }

                if (!unlockedRewards.isEmpty()) {
                    data.setUnlockedPrestigeRewards(unlockedRewards);
                    plugin.getPluginLogger().debug("Chargé " + unlockedRewards.size() + " statuts de récompenses pour " + playerName);
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
                    config.set(path + "type", sanction.type());
                    config.set(path + "reason", sanction.reason());
                    config.set(path + "moderator", sanction.moderator());
                    config.set(path + "startTime", sanction.startTime());
                    config.set(path + "endTime", sanction.endTime());
                }
            }

            Set<String> permissions = data.getCustomPermissions();
            if (!permissions.isEmpty()) {
                config.set("custom-permissions", new ArrayList<>(permissions));
            }

            Set<String> pickaxeEnchantmentBooks = data.getPLayerEnchantmentBooks();
            if (!pickaxeEnchantmentBooks.isEmpty()) {
                config.set("pickaxe-enchantment-books", new ArrayList<>(pickaxeEnchantmentBooks));
            }

            Set<String> activeEnchants = data.getActiveEnchantmentBooks();
            if (!activeEnchants.isEmpty()) {
                config.set("active-enchantments", new ArrayList<>(activeEnchants));
            }

            // Système de métiers
            if (data.getActiveProfession() != null) {
                config.set("active-profession", data.getActiveProfession());
            }
            config.set("last-profession-change", data.getLastProfessionChange());

            // Niveaux de métiers
            Map<String, Integer> professionLevels = data.getAllProfessionLevels();
            if (!professionLevels.isEmpty()) {
                for (Map.Entry<String, Integer> entry : professionLevels.entrySet()) {
                    config.set("profession-levels." + entry.getKey(), entry.getValue());
                }
            }

            // XP des métiers
            Map<String, Integer> professionXP = data.getAllProfessionXP();
            if (!professionXP.isEmpty()) {
                for (Map.Entry<String, Integer> entry : professionXP.entrySet()) {
                    config.set("profession-xp." + entry.getKey(), entry.getValue());
                }
            }

            // Talents métier
            Map<String, Map<String, Integer>> talentLevels = data.getAllTalentLevels();
            if (!talentLevels.isEmpty()) {
                for (Map.Entry<String, Map<String, Integer>> professionEntry : talentLevels.entrySet()) {
                    for (Map.Entry<String, Integer> talentEntry : professionEntry.getValue().entrySet()) {
                        config.set("talent-levels." + professionEntry.getKey() + "." + talentEntry.getKey(), talentEntry.getValue());
                    }
                }
            }

            Map<String, Integer> kitLevels = data.getAllKitLevels();
            if (!kitLevels.isEmpty()) {
                for (Map.Entry<String, Integer> entry : kitLevels.entrySet()) {
                    config.set("kit-levels." + entry.getKey(), entry.getValue());
                }
            }

            if (!data.getAllClaimedProfessionRewards().isEmpty()) {
                for (Map.Entry<String, Set<Integer>> entry : data.getAllClaimedProfessionRewards().entrySet()) {
                    String profession = entry.getKey();
                    Set<Integer> claimedLevels = entry.getValue();
                    if (!claimedLevels.isEmpty()) {
                        config.set("profession-rewards." + profession, new ArrayList<>(claimedLevels));
                    }
                }
            }

            Map<PrestigeTalent, Integer> prestigeTalents = data.getPrestigeTalents();
            if (!prestigeTalents.isEmpty()) {
                for (Map.Entry<PrestigeTalent, Integer> entry : prestigeTalents.entrySet()) {
                    config.set("prestige.talents." + entry.getKey().name(), entry.getValue());
                }
            }

            Map<Integer, String> chosenTalents = data.getChosenPrestigeTalents();
            if (!chosenTalents.isEmpty()) {
                for (Map.Entry<Integer, String> entry : chosenTalents.entrySet()) {
                    config.set("prestige.chosen-talents." + entry.getKey(), entry.getValue());
                }
            }

            Set<String> chosenRewards = data.getChosenSpecialRewards();
            if (!chosenRewards.isEmpty()) {
                config.set("prestige.chosen-special-rewards", new ArrayList<>(chosenRewards));
            }

            // Récompenses débloquées (statut)
            Map<String, Boolean> unlockedRewards = data.getUnlockedPrestigeRewards();
            if (!unlockedRewards.isEmpty()) {
                for (Map.Entry<String, Boolean> entry : unlockedRewards.entrySet()) {
                    config.set("prestige.unlocked-rewards." + entry.getKey(), entry.getValue());
                }
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
     * CORRIGÉ: Ajoute une permission de mine (seulement la plus élevée)
     */
    public boolean addMinePermissionToPlayer(UUID playerId, String mineName) {
        // Normalise le nom de la mine
        String rankName = mineName.toLowerCase();
        if (rankName.startsWith("mine-")) {
            rankName = rankName.substring(5);
        }

        // Valide le rang
        if (rankName.length() != 1 || rankName.charAt(0) < 'a' || rankName.charAt(0) > 'z') {
            plugin.getPluginLogger().warning("Rang de mine invalide: " + mineName);
            return false;
        }

        PlayerData data = getPlayerData(playerId);

        // NOUVEAU: Retire l'ancienne permission de mine (pour éviter les doublons)
        removeAllMinePermissionsFromPlayer(playerId);

        // Ajoute la nouvelle permission bukkit
        String bukkitPermission = "specialmine.mine." + rankName;
        addPermissionToPlayer(playerId, bukkitPermission);

        // Garde l'ancienne logique pour compatibilité
        data.addMinePermission(rankName);
        markDirty(playerId);

        plugin.getPluginLogger().info("Permission mine bukkit '" + bukkitPermission + "' ajoutée à " + data.getPlayerName());
        return true;
    }

    /**
     * NOUVEAU: Retire toutes les permissions de mine d'un joueur
     */
    public void removeAllMinePermissionsFromPlayer(UUID playerId) {
        PlayerData data = getPlayerData(playerId);

        // Retire toutes les permissions bukkit de mine
        Set<String> permissionsToRemove = new HashSet<>();
        for (String permission : data.getCustomPermissions()) {
            if (permission.startsWith("specialmine.mine.")) {
                permissionsToRemove.add(permission);
            }
        }

        for (String permission : permissionsToRemove) {
            removePermissionFromPlayer(playerId, permission);
        }

        // Nettoie aussi l'ancienne logique
        data.clearMinePermissions();
        markDirty(playerId);

        if (!permissionsToRemove.isEmpty()) {
            plugin.getPluginLogger().info("Permissions de mine retirées de " + data.getPlayerName() + ": " + permissionsToRemove);
        }
    }

    /**
     * CORRIGÉ: Retire une permission de mine spécifique
     */
    public boolean removeMinePermissionFromPlayer(UUID playerId, String mineName) {
        // Normalise le nom de la mine
        String rankName = mineName.toLowerCase();
        if (rankName.startsWith("mine-")) {
            rankName = rankName.substring(5);
        }

        // Convertit en permission bukkit
        String bukkitPermission = "specialmine.mine." + rankName;

        // Retire de customPermissions
        removePermissionFromPlayer(playerId, bukkitPermission);

        // Retire de l'ancienne logique
        PlayerData data = getPlayerData(playerId);
        data.removeMinePermission(rankName);
        markDirty(playerId);

        plugin.getPluginLogger().info("Permission mine bukkit '" + bukkitPermission + "' retirée de " + data.getPlayerName());
        return true;
    }

    /**
     * CORRIGÉ: Vérifie si un joueur a la permission pour une mine (logique hiérarchique)
     */
    public boolean hasPlayerMinePermission(UUID playerId, String mineName) {
        Player player = plugin.getServer().getPlayer(playerId);

        if (player != null && player.isOnline()) {
            // Pour joueurs en ligne: utilise la logique hiérarchique via PlayerData
            PlayerData data = getPlayerData(playerId);
            return data.hasMinePermission(mineName);
        } else {
            // Pour joueurs hors ligne: vérifie les données stockées avec logique hiérarchique
            PlayerData data = getPlayerData(playerId);
            return data.hasMinePermission(mineName);
        }
    }

    /**
     * NOUVEAU: Commande admin pour diagnostiquer les permissions
     */
    public void diagnosePlayerPermissions(UUID playerId) {
        PlayerData data = getPlayerData(playerId);
        Player player = plugin.getServer().getPlayer(playerId);

        plugin.getPluginLogger().info("=== DIAGNOSTIC PERMISSIONS - " + data.getPlayerName() + " ===");

        // Permissions dans les données
        Set<String> storedPermissions = new HashSet<>();
        for (String permission : data.getCustomPermissions()) {
            if (permission.startsWith("specialmine.mine.")) {
                storedPermissions.add(permission);
            }
        }
        plugin.getPluginLogger().info("Permissions stockées: " + storedPermissions);

        // Permissions bukkit (si en ligne)
        if (player != null && player.isOnline()) {
            Set<String> bukkitPermissions = new HashSet<>();
            for (char c = 'a'; c <= 'z'; c++) {
                String permission = "specialmine.mine." + c;
                if (player.hasPermission(permission)) {
                    bukkitPermissions.add(permission);
                }
            }
            plugin.getPluginLogger().info("Permissions bukkit: " + bukkitPermissions);
        }

        // Rang calculé
        String calculatedRank = data.getHighestMineRank();
        plugin.getPluginLogger().info("Rang calculé: " + (calculatedRank != null ? calculatedRank.toUpperCase() : "A (défaut)"));

        // Mines accessibles
        Set<String> accessibleMines = data.getAccessibleMines();
        plugin.getPluginLogger().info("Mines accessibles: " + accessibleMines);

        plugin.getPluginLogger().info("=== FIN DIAGNOSTIC ===");
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

        // NOUVEAU: Force une sauvegarde immédiate pour les permissions critiques
        if (permission.equals("specialmine.vip")) {
            savePlayerNow(playerId);
            plugin.getPluginLogger().info("Permission VIP sauvegardée immédiatement pour " + data.getPlayerName());
        }

        plugin.getPluginLogger().info("Permission ajoutée à " + data.getPlayerName() + ": " + permission);
    }

    public void removePermissionFromPlayer(UUID playerId, String permission) {
        PlayerData data = getPlayerData(playerId);
        data.removePermission(permission);
        markDirty(playerId);

        // Retire immédiatement si le joueur est en ligne
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null && player.isOnline()) {
            plugin.getPermissionManager().detachPermission(player, permission);
        }

        // NOUVEAU: Force une sauvegarde immédiate pour les permissions critiques
        if (permission.equals("specialmine.vip")) {
            savePlayerNow(playerId);
            plugin.getPluginLogger().info("Permission VIP retirée et sauvegardée pour " + data.getPlayerName());
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