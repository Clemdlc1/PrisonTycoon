package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.boosts.PlayerBoost;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> plugin.getEconomyManager().updateVanillaExpFromCustom(player, loaded.getExperience()), 1L); // Petit délai pour s'assurer que tout est initialisé
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

            ConfigurationSection columnsSection = config.getConfigurationSection("prestige.chosen-columns");
            if (columnsSection != null) {
                Map<Integer, PrestigeTalent> chosenColumns = new HashMap<>();
                Map<PrestigeTalent, Integer> activeTalents = new HashMap<>();

                for (String key : columnsSection.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(key);
                        String talentName = columnsSection.getString(key);
                        PrestigeTalent talent = PrestigeTalent.valueOf(talentName);

                        chosenColumns.put(level, talent);
                        // Compter pour les bonus actifs
                        activeTalents.put(talent, activeTalents.getOrDefault(talent, 0) + 1);

                    } catch (Exception e) {
                        plugin.getPluginLogger().warning("Choix de colonne invalide: " + key);
                    }
                }

                data.setChosenPrestigeColumns(chosenColumns);
                data.setPrestigeTalents(activeTalents);
            }

            // Charger les choix de récompenses spéciales (nouveau système)
            ConfigurationSection rewardsSection = config.getConfigurationSection("prestige.chosen-special-rewards");
            if (rewardsSection != null) {
                Map<Integer, String> chosenRewards = new HashMap<>();
                Map<String, Boolean> unlockedRewards = new HashMap<>();

                for (String key : rewardsSection.getKeys(false)) {
                    try {
                        int level = Integer.parseInt(key);
                        String rewardId = rewardsSection.getString(key);

                        chosenRewards.put(level, rewardId);
                        unlockedRewards.put(rewardId, true);
                        data.markPrestigeLevelCompleted(level);

                    } catch (Exception e) {
                        plugin.getPluginLogger().warning("Choix de récompense invalide: " + key);
                    }
                }

                data.setChosenSpecialRewards(chosenRewards);
                data.setUnlockedPrestigeRewards(unlockedRewards);
            }

            int savedReputation = config.getInt("reputation", 0);
            if (savedReputation != 0) {
                data.setReputation(savedReputation);
            }

            if (config.contains("boosts")) {
                Map<String, PlayerBoost> loadedBoosts = new HashMap<>();
                for (String boostKey : config.getConfigurationSection("boosts").getKeys(false)) {
                    try {
                        String typeName = config.getString("boosts." + boostKey + ".type");
                        long startTime = config.getLong("boosts." + boostKey + ".start-time");
                        long endTime = config.getLong("boosts." + boostKey + ".end-time");
                        double bonus = config.getDouble("boosts." + boostKey + ".bonus");

                        BoostType type = BoostType.valueOf(typeName);
                        PlayerBoost boost = new PlayerBoost(type, startTime, endTime, bonus);

                        // Seulement charge les boosts encore actifs
                        if (boost.isActive()) {
                            loadedBoosts.put(boostKey, boost);
                        }
                    } catch (Exception e) {
                        plugin.getPluginLogger().warning("Erreur chargement boost " + boostKey +
                                " pour " + playerName + ": " + e.getMessage());
                    }
                }

                if (!loadedBoosts.isEmpty()) {
                    data.setActiveBoosts(loadedBoosts);
                    plugin.getPluginLogger().debug("Boosts chargés pour " + playerName + ": " + loadedBoosts.size());
                }
            }

            if (config.contains("autominer.active-slot-1")) {
                ItemStack slot1 = config.getItemStack("autominer.active-slot-1");
                if (slot1 != null) {
                    data.setActiveAutominerSlot1(slot1);
                }
            }

            if (config.contains("autominer.active-slot-2")) {
                ItemStack slot2 = config.getItemStack("autominer.active-slot-2");
                if (slot2 != null) {
                    data.setActiveAutominerSlot2(slot2);
                }
            }

            data.setAutominerFuelReserve(config.getDouble("autominer.fuel-reserve", 0.0));
            data.setAutominerCurrentWorld(config.getString("autominer.current-world", "mine-a"));
            data.setAutominerStorageLevel(config.getInt("autominer.storage-level", 0));

            // Charger le contenu du stockage
            if (config.contains("autominer.storage-contents")) {
                Map<Material, Long> storageContents = new HashMap<>();
                ConfigurationSection storageSection = config.getConfigurationSection("autominer.storage-contents");
                for (String materialName : storageSection.getKeys(false)) {
                    try {
                        Material material = Material.valueOf(materialName.toUpperCase());
                        long amount = storageSection.getLong(materialName);
                        storageContents.put(material, amount);
                    } catch (IllegalArgumentException e) {
                        plugin.getPluginLogger().warning("§cMatériel invalide dans le stockage automineur: " + materialName);
                    }
                }
                data.setAutominerStorageContents(storageContents);
            }

            // Charger les clés stockées
            if (config.contains("autominer.stored-keys")) {
                Map<String, Integer> storedKeys = new HashMap<>();
                ConfigurationSection keysSection = config.getConfigurationSection("autominer.stored-keys");
                for (String keyType : keysSection.getKeys(false)) {
                    int amount = keysSection.getInt(keyType);
                    storedKeys.put(keyType, amount);
                }
                data.setAutominerStoredKeys(storedKeys);
            }

            // NOUVEAU: Charger les gains en attente
            data.setAutominerPendingCoins(config.getLong("autominer.pending-coins", 0));
            data.setAutominerPendingTokens(config.getLong("autominer.pending-tokens", 0));
            data.setAutominerPendingExperience(config.getLong("autominer.pending-experience", 0));
            data.setAutominerPendingBeacons(config.getLong("autominer.pending-beacons", 0));

            long savedSavingsBalance = config.getLong("bank.savings-balance", 0);
            long savedSafeBalance = config.getLong("bank.safe-balance", 0);
            int savedBankLevel = config.getInt("bank.level", 1);
            long savedTotalDeposits = config.getLong("bank.total-deposits", 0);
            long savedLastInterest = config.getLong("bank.last-interest", System.currentTimeMillis());

            if (savedSavingsBalance > 0) data.setSavingsBalance(savedSavingsBalance);
            if (savedSafeBalance > 0) data.setSafeBalance(savedSafeBalance);
            if (savedBankLevel > 1) data.setBankLevel(savedBankLevel);
            if (savedTotalDeposits > 0) data.setTotalBankDeposits(savedTotalDeposits);
            data.setLastInterestTime(savedLastInterest);

            // Investissements avec support grandes valeurs
            if (config.contains("bank.investments")) {
                ConfigurationSection investSection = config.getConfigurationSection("bank.investments");
                for (String materialName : investSection.getKeys(false)) {
                    try {
                        Material material = Material.valueOf(materialName.toUpperCase());
                        long quantity = investSection.getLong(materialName); // Changé en getLong
                        if (quantity > 0L) {
                            data.setInvestment(material, quantity);
                        }
                    } catch (IllegalArgumentException e) {
                        // Matériau invalide, ignore
                    }
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

            Map<Integer, PrestigeTalent> chosenColumns = data.getChosenPrestigeColumns();
            if (!chosenColumns.isEmpty()) {
                for (Map.Entry<Integer, PrestigeTalent> entry : chosenColumns.entrySet()) {
                    config.set("prestige.chosen-columns." + entry.getKey(), entry.getValue().name());
                }
            }

            // Nouveau système : Choix de récompenses spéciales (exclusif)
            Map<Integer, String> chosenRewards = data.getChosenSpecialRewards();
            if (!chosenRewards.isEmpty()) {
                for (Map.Entry<Integer, String> entry : chosenRewards.entrySet()) {
                    config.set("prestige.chosen-special-rewards." + entry.getKey(), entry.getValue());
                }
            }

            Map<String, PlayerBoost> boosts = data.getActiveBoosts();
            if (!boosts.isEmpty()) {
                for (Map.Entry<String, PlayerBoost> entry : boosts.entrySet()) {
                    PlayerBoost boost = entry.getValue();
                    String path = "boosts." + entry.getKey();
                    config.set(path + ".type", boost.getType().name());
                    config.set(path + ".start-time", boost.getStartTime());
                    config.set(path + ".end-time", boost.getEndTime());
                    config.set(path + ".bonus", boost.getBonusPercentage());
                }
            }

            config.set("reputation", data.getReputation());

            if (data.getActiveAutominerSlot1() != null) {
                config.set("autominer.active-slot-1", data.getActiveAutominerSlot1());
            }

            if (data.getActiveAutominerSlot2() != null) {
                config.set("autominer.active-slot-2", data.getActiveAutominerSlot2());
            }

            config.set("autominer.fuel-reserve", data.getAutominerFuelReserve());
            config.set("autominer.current-world", data.getAutominerCurrentWorld());
            config.set("autominer.storage-level", data.getAutominerStorageLevel());

            // Sauvegarder le contenu du stockage
            Map<Material, Long> storageContents = data.getAutominerStorageContents();
            if (!storageContents.isEmpty()) {
                for (Map.Entry<Material, Long> entry : storageContents.entrySet()) {
                    config.set("autominer.storage-contents." + entry.getKey().name().toLowerCase(), entry.getValue());
                }
            }

            // Sauvegarder les clés stockées
            Map<String, Integer> storedKeys = data.getAutominerStoredKeys();
            if (!storedKeys.isEmpty()) {
                for (Map.Entry<String, Integer> entry : storedKeys.entrySet()) {
                    config.set("autominer.stored-keys." + entry.getKey(), entry.getValue());
                }
            }

            config.set("bank.savings-balance", data.getSavingsBalance());
            config.set("bank.safe-balance", data.getSafeBalance());
            config.set("bank.level", data.getBankLevel());
            config.set("bank.total-deposits", data.getTotalBankDeposits());
            config.set("bank.last-interest", data.getLastInterestTime());

            // Investissements avec support grandes valeurs
            Map<Material, Long> investments = data.getAllInvestments();
            if (!investments.isEmpty()) {
                for (Map.Entry<Material, Long> entry : investments.entrySet()) {
                    config.set("bank.investments." + entry.getKey().name().toLowerCase(), entry.getValue());
                }
            }

            // NOUVEAU: Sauvegarder les gains en attente
            config.set("autominer.pending-coins", data.getAutominerPendingCoins());
            config.set("autominer.pending-tokens", data.getAutominerPendingTokens());
            config.set("autominer.pending-experience", data.getAutominerPendingExperience());
            config.set("autominer.pending-beacons", data.getAutominerPendingBeacons());


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
}