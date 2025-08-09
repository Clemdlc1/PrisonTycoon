package fr.prisontycoon.managers;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.PlayerBoost;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * GESTIONNAIRE DE DONNÉES JOUEURS - VERSION CORRIGÉE
 * <p>
 * ✅ CORRECTIONS APPORTÉES :
 * - Requête UPSERT pour éviter les conflits de clés primaires
 * - Chargement complet des données avec gestion d'erreurs robuste
 * - Transactions atomiques pour la cohérence des données
 * - Protection contre la corruption JSON
 * - Gestion appropriée de la concurrence
 * - Logique de cache optimisée
 * - BUG FIXÉ: Utilisation des setters au lieu des copies défensives
 */
public class PlayerDataManager {

    private final PrisonTycoon plugin;
    private final DatabaseManager databaseManager;
    private final Gson gson = new Gson();

    // Cache thread-safe avec verrous pour la cohérence
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastSaveTime = new ConcurrentHashMap<>();

    // Verrous pour éviter les race conditions
    private final Map<UUID, ReentrantReadWriteLock> playerLocks = new ConcurrentHashMap<>();

    // Types Gson pré-compilés pour les performances
    private final Type stringIntegerMapType = new TypeToken<Map<String, Integer>>() {
    }.getType();
    private final Type stringStringMapType = new TypeToken<Map<String, String>>() {
    }.getType();
    private final Type stringSetType = new TypeToken<Set<String>>() {
    }.getType();
    private final Type sanctionListType = new TypeToken<List<PlayerData.SanctionData>>() {
    }.getType();
    private final Type stringMapMapType = new TypeToken<Map<String, Map<String, Integer>>>() {
    }.getType();
    private final Type intTalentMapType = new TypeToken<Map<Integer, PrestigeTalent>>() {
    }.getType();
    private final Type intStringMapType = new TypeToken<Map<Integer, String>>() {
    }.getType();
    private final Type stringBoostMapType = new TypeToken<Map<String, PlayerBoost>>() {
    }.getType();
    private final Type materialLongMapType = new TypeToken<Map<Material, Long>>() {
    }.getType();
    private final Type stringSetIntegerMapType = new TypeToken<Map<String, Set<Integer>>>() {
    }.getType();
    private final Type integerSetType = new TypeToken<Set<Integer>>(){}.getType();


    public PlayerDataManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        createPlayersTable();
        plugin.getPluginLogger().info("§aPlayerDataManager initialisé avec corrections de sécurité.");
    }

    /**
     * Création/vérification de la table players avec tous les champs nécessaires
     */
    private void createPlayersTable() {
        String query = """
                CREATE TABLE IF NOT EXISTS players (
                    uuid VARCHAR(36) PRIMARY KEY,
                    name VARCHAR(16) NOT NULL,
                    coins BIGINT DEFAULT 0,
                    tokens BIGINT DEFAULT 0,
                    experience BIGINT DEFAULT 0,
                    beacons BIGINT DEFAULT 0,
                    coins_via_pickaxe BIGINT DEFAULT 0,
                    tokens_via_pickaxe BIGINT DEFAULT 0,
                    experience_via_pickaxe BIGINT DEFAULT 0,
                    active_profession TEXT DEFAULT 'mineur',
                    last_profession_change BIGINT DEFAULT 0,
                    enchantments TEXT DEFAULT '{}',
                    auto_upgrade TEXT DEFAULT '[]',
                    mobility_disabled TEXT DEFAULT '[]',
                    pickaxe_cristals TEXT DEFAULT '{}',
                    custom_permissions TEXT DEFAULT '[]',
                    sanctions TEXT DEFAULT '[]',
                    active_enchantments TEXT DEFAULT '[]',
                    pickaxe_enchantment_book_levels TEXT DEFAULT '{}',
                    profession_levels TEXT DEFAULT '{}',
                    profession_xp TEXT DEFAULT '{}',
                    talent_levels TEXT DEFAULT '{}',
                    kit_levels TEXT DEFAULT '{}',
                    profession_rewards TEXT DEFAULT '{}',
                    chosen_prestige_columns TEXT DEFAULT '{}',
                    chosen_special_rewards TEXT DEFAULT '{}',
                    reputation INT DEFAULT 0,
                    boosts TEXT DEFAULT '{}',
                    autominer_active_slot_1 TEXT DEFAULT NULL,
                    autominer_active_slot_2 TEXT DEFAULT NULL,
                    autominer_fuel_reserve BIGINT DEFAULT 0.0,
                    autominer_current_world VARCHAR(100) DEFAULT NULL,
                    autominer_storage_level INT DEFAULT 1,
                    autominer_storage_items TEXT DEFAULT '{}',
                    autominer_pending_coins BIGINT DEFAULT 0,
                    autominer_pending_tokens BIGINT DEFAULT 0,
                    autominer_pending_experience BIGINT DEFAULT 0,
                    autominer_pending_beacons BIGINT DEFAULT 0,
                    bank_savings_balance BIGINT DEFAULT 0,
                    bank_safe_balance BIGINT DEFAULT 0,
                    bank_level INT DEFAULT 1,
                    bank_total_deposits BIGINT DEFAULT 0,
                    bank_last_interest BIGINT DEFAULT 0,
                    bank_investments TEXT DEFAULT '{}',
                    statistics_total_blocks_mined BIGINT DEFAULT 0,
                    statistics_total_blocks_destroyed BIGINT DEFAULT 0,
                    statistics_total_greed_triggers BIGINT DEFAULT 0,
                    statistics_total_keys_obtained BIGINT DEFAULT 0,
                    gang_id VARCHAR(36),
                    gang_invitation VARCHAR(36),
                    selected_outpost_skin VARCHAR(255) DEFAULT 'default',
                    unlocked_outpost_skins TEXT DEFAULT '["default"]',
                    collected_heads TEXT DEFAULT '[]',
                    claimed_head_rewards TEXT DEFAULT '[]'
                );
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.execute();
            plugin.getPluginLogger().info("§7Table 'players' créée/vérifiée avec succès.");
        } catch (SQLException e) {
            plugin.getLogger().severe("§cErreur lors de la création de la table 'players': " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Récupère les données d'un joueur (cache-first avec fallback BDD)
     */
    public PlayerData getPlayerData(UUID playerId) {
        if (playerId == null) return null;

        ReentrantReadWriteLock lock = playerLocks.computeIfAbsent(playerId, k -> new ReentrantReadWriteLock());

        // Lecture thread-safe du cache
        lock.readLock().lock();
        try {
            if (playerDataCache.containsKey(playerId)) {
                return playerDataCache.get(playerId);
            }
        } finally {
            lock.readLock().unlock();
        }

        // Chargement depuis la BDD avec verrou d'écriture
        lock.writeLock().lock();
        try {
            // Double-check après avoir obtenu le verrou d'écriture
            if (playerDataCache.containsKey(playerId)) {
                return playerDataCache.get(playerId);
            }

            PlayerData playerData = loadPlayerDataFromDatabase(playerId);
            playerDataCache.put(playerId, playerData);
            return playerData;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 🔥 MÉTHODE CORRIGÉE : Charge complètement les données depuis la base avec gestion d'erreurs
     * BUG FIXÉ: Utilisation des setters au lieu des copies défensives
     */
    private PlayerData loadPlayerDataFromDatabase(UUID playerId) {
        String query = "SELECT * FROM players WHERE uuid = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String playerName = rs.getString("name");
                PlayerData data = new PlayerData(playerId, playerName);

                // Chargement des données de base
                data.setCoins(rs.getLong("coins"));
                data.setTokens(rs.getLong("tokens"));
                data.setExperience(rs.getLong("experience"));
                data.setBeacons(rs.getLong("beacons"));
                data.setCoinsViaPickaxe(rs.getLong("coins_via_pickaxe"));
                data.setTokensViaPickaxe(rs.getLong("tokens_via_pickaxe"));
                data.setExperienceViaPickaxe(rs.getLong("experience_via_pickaxe"));
                data.setActiveProfession(rs.getString("active_profession"));
                data.setLastProfessionChange(rs.getLong("last_profession_change"));

                // 🔥 CORRECTION PRINCIPALE : Chargement avec setters au lieu de copies défensives
                Map<String, Integer> enchantments = loadJsonValue(rs, "enchantments", stringIntegerMapType);
                if (enchantments != null) {
                    data.setEnchantmentLevel(enchantments);
                }

                Set<String> autoUpgrade = loadJsonValue(rs, "auto_upgrade", stringSetType);
                if (autoUpgrade != null) {
                    data.setAutoUpgradeEnabled(autoUpgrade);
                }

                Set<String> mobilityDisabled = loadJsonValue(rs, "mobility_disabled", stringSetType);
                if (mobilityDisabled != null) {
                    data.setMobilityEnchantmentEnabled(mobilityDisabled);
                }

                Map<String, String> pickaxeCristals = loadJsonValue(rs, "pickaxe_cristals", stringStringMapType);
                if (pickaxeCristals != null) {
                    data.setPickaxeCristal(pickaxeCristals);
                }

                // Permissions custom (conservé tel quel - fonctionne déjà correctement)
                Set<String> customPermissions = loadJsonValue(rs, "custom_permissions", stringSetType);
                if (customPermissions != null) {
                    data.setCustomPermissions(customPermissions);
                }

                List<PlayerData.SanctionData> sanctions = loadJsonValue(rs, "sanctions", sanctionListType);
                if (sanctions != null) {
                    data.setSanctionHistory(sanctions);
                }

                // 🔥 CORRECTIF PRINCIPAL : Enchantements actifs
                Set<String> activeEnchantments = loadJsonValue(rs, "active_enchantments", stringSetType);
                if (activeEnchantments != null) {
                    data.setActiveEnchantmentBooks(activeEnchantments);
                }

                // 🔥 CORRECTIF PRINCIPAL : Niveaux des livres d'enchantement
                Map<String, Integer> pickaxeEnchantmentBookLevels = loadJsonValue(rs, "pickaxe_enchantment_book_levels", stringIntegerMapType);
                if (pickaxeEnchantmentBookLevels != null) {
                    data.setPickaxeEnchantmentBookLevels(pickaxeEnchantmentBookLevels);
                }

                Map<String, Integer> professionLevels = loadJsonValue(rs, "profession_levels", stringIntegerMapType);
                boolean hadInvalidProfessionKeys = false;
                if (professionLevels != null) {
                    for (Map.Entry<String, Integer> entry : professionLevels.entrySet()) {
                        String prof = entry.getKey();
                        if (prof != null && plugin.getProfessionManager().getProfession(prof) != null) {
                            data.setProfessionLevel(prof, entry.getValue());
                        } else {
                            hadInvalidProfessionKeys = true;
                        }
                    }
                }

                Map<String, Integer> professionXP = loadJsonValue(rs, "profession_xp", stringIntegerMapType);
                if (professionXP != null) {
                    for (Map.Entry<String, Integer> entry : professionXP.entrySet()) {
                        String prof = entry.getKey();
                        if (prof != null && plugin.getProfessionManager().getProfession(prof) != null) {
                            data.setProfessionXP(prof, entry.getValue());
                        } else {
                            hadInvalidProfessionKeys = true;
                        }
                    }
                }

                if (hadInvalidProfessionKeys) {
                    // Marque pour sauvegarde afin d'assainir les colonnes JSON à la prochaine écriture
                    markDirty(playerId);
                }

                Map<String, Map<String, Integer>> talentLevels = loadJsonValue(rs, "talent_levels", stringMapMapType);
                if (talentLevels != null) {
                    data.setTalentLevels(talentLevels);
                }

                Map<String, Integer> kitLevels = loadJsonValue(rs, "kit_levels", stringIntegerMapType);
                if (kitLevels != null) {
                    data.setKitLevels(kitLevels);
                }

                Map<String, Set<Integer>> professionRewards = loadJsonValue(rs, "profession_rewards", stringSetIntegerMapType);
                if (professionRewards != null) {
                    data.setClaimedProfessionRewards(professionRewards);
                }

                Map<Integer, PrestigeTalent> chosenPrestigeColumns = loadJsonValue(rs, "chosen_prestige_columns", intTalentMapType);
                if (chosenPrestigeColumns != null) {
                    data.setChosenPrestigeColumns(chosenPrestigeColumns);
                }

                Map<Integer, String> chosenSpecialRewards = loadJsonValue(rs, "chosen_special_rewards", intStringMapType);
                if (chosenSpecialRewards != null) {
                    data.setChosenSpecialRewards(chosenSpecialRewards);
                }

                data.setReputation(rs.getInt("reputation"));

                Map<String, PlayerBoost> boosts = loadJsonValue(rs, "boosts", stringBoostMapType);
                if (boosts != null) {
                    data.setActiveBoosts(boosts);
                }

                // AutoMiner ItemStacks (gestion spéciale)
                loadAutominerSlot(rs, "autominer_active_slot_1", data, 1);
                loadAutominerSlot(rs, "autominer_active_slot_2", data, 2);

                data.setAutominerFuelReserve(rs.getDouble("autominer_fuel_reserve"));
                data.setAutominerCurrentWorld(rs.getString("autominer_current_world"));
                data.setAutominerStorageLevel(rs.getInt("autominer_storage_level"));

                Map<Material, Long> autominerStorageItems = loadJsonValue(rs, "autominer_storage_items", materialLongMapType);
                if (autominerStorageItems != null) {
                    for (Map.Entry<Material, Long> entry : autominerStorageItems.entrySet()) {
                        data.setAutominerStorageContents(entry.getKey(), entry.getValue());
                    }
                }

                data.setAutominerPendingCoins(rs.getLong("autominer_pending_coins"));
                data.setAutominerPendingTokens(rs.getLong("autominer_pending_tokens"));
                data.setAutominerPendingExperience(rs.getLong("autominer_pending_experience"));
                data.setAutominerPendingBeacons(rs.getLong("autominer_pending_beacons"));

                data.setBankSavingsBalance(rs.getLong("bank_savings_balance"));
                data.setBankSafeBalance(rs.getLong("bank_safe_balance"));
                data.setBankLevel(rs.getInt("bank_level"));
                data.setTotalBankDeposits(rs.getLong("bank_total_deposits"));
                data.setBankLastInterest(rs.getLong("bank_last_interest"));

                Map<String, Long> bankInvestments = loadJsonValue(rs, "bank_investments", new TypeToken<Map<String, Long>>() {
                }.getType());
                if (bankInvestments != null) {
                    for (Map.Entry<String, Long> entry : bankInvestments.entrySet()) {
                        data.setBankInvestment(entry.getKey(), entry.getValue());
                    }
                }

                data.setTotalBlocksMined(rs.getLong("statistics_total_blocks_mined"));
                data.setTotalBlocksDestroyed(rs.getLong("statistics_total_blocks_destroyed"));
                data.setTotalGreedTriggers(rs.getLong("statistics_total_greed_triggers"));
                data.setTotalKeysObtained(rs.getLong("statistics_total_keys_obtained"));
                data.setGangId(rs.getString("gang_id"));
                data.setGangInvitation(rs.getString("gang_invitation"));

                data.setSelectedOutpostSkin(rs.getString("selected_outpost_skin"));

                Set<String> unlockedSkins = loadJsonValue(rs, "unlocked_outpost_skins", stringSetType);
                if (unlockedSkins != null) {
                    data.setUnlockedOutpostSkins(unlockedSkins);
                } else {
                    // Valeurs par défaut si null
                    Set<String> defaultSkins = new HashSet<>();
                    defaultSkins.add("default");
                    data.setUnlockedOutpostSkins(defaultSkins);
                }

                String collectedHeadsJson = rs.getString("collected_heads");
                if (collectedHeadsJson != null && !collectedHeadsJson.isEmpty()) {
                    try {
                        Set<String> collectedHeads = gson.fromJson(collectedHeadsJson, stringSetType);
                        data.setCollectedHeads(collectedHeads);
                    } catch (Exception e) {
                        plugin.getPluginLogger().debug("Erreur lors du parsing des têtes collectées pour " + playerId + ": " + e.getMessage());
                        data.setCollectedHeads(new HashSet<>());
                    }
                } else {
                    data.setCollectedHeads(new HashSet<>());
                }

                String claimedHeadRewardsJson = rs.getString("claimed_head_rewards");
                if (claimedHeadRewardsJson != null && !claimedHeadRewardsJson.isEmpty()) {
                    try {
                        Set<Integer> claimedRewards = gson.fromJson(claimedHeadRewardsJson, integerSetType);
                        data.setClaimedHeadRewards(claimedRewards);
                    } catch (Exception e) {
                        plugin.getPluginLogger().debug("Erreur lors du parsing des récompenses réclamées pour " + playerId + ": " + e.getMessage());
                        data.setClaimedHeadRewards(new HashSet<>());
                    }
                } else {
                    data.setClaimedHeadRewards(new HashSet<>());
                }

                plugin.getPluginLogger().debug("§aDonnées chargées avec succès pour " + playerName + " (" + playerId + ")");
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("§cErreur lors du chargement des données de " + playerId + ": " + e.getMessage());
            e.printStackTrace();
        }

        // Retourne des données par défaut si le chargement échoue
        plugin.getPluginLogger().warning("§eChargement par défaut pour " + playerId + " (nouveau joueur ou erreur BDD)");
        return new PlayerData(playerId, getPlayerName(playerId));
    }

    /**
     * NOUVELLE MÉTHODE : Charge et désérialise une valeur JSON avec protection d'erreurs
     */
    private <T> T loadJsonValue(ResultSet rs, String columnName, Type type) {
        try {
            String jsonValue = rs.getString(columnName);
            if (jsonValue != null && !jsonValue.trim().isEmpty() && !jsonValue.equals("null")) {
                try {
                    return gson.fromJson(jsonValue, type);
                } catch (JsonSyntaxException e) {
                    plugin.getPluginLogger().warning("§eDonnées JSON corrompues pour " + columnName +
                            ". Valeurs par défaut utilisées.");
                }
            }
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("§eErreur de lecture pour " + columnName + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * NOUVELLE MÉTHODE : Charge les slots AutoMiner avec gestion spéciale ItemStack
     */
    private void loadAutominerSlot(ResultSet rs, String columnName, PlayerData data, int slotNumber) {
        try {
            String jsonValue = rs.getString(columnName);
            if (jsonValue != null && !jsonValue.trim().isEmpty() && !jsonValue.equals("null")) {
                try {
                    // Désérialise l'ItemStack depuis le JSON
                    Map<String, Object> itemData = gson.fromJson(jsonValue, new TypeToken<Map<String, Object>>() {
                    }.getType());
                    if (itemData != null) {
                        ItemStack item = ItemStack.deserialize(itemData);
                        if (slotNumber == 1) {
                            data.setActiveAutominerSlot1(item);
                        } else if (slotNumber == 2) {
                            data.setActiveAutominerSlot2(item);
                        }
                    }
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("§eErreur lors du chargement du slot autominer " + slotNumber + ": " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            plugin.getPluginLogger().warning("§eErreur SQL pour " + columnName + ": " + e.getMessage());
        }
    }

    /**
     * MÉTHODE CORRIGÉE : Sauvegarde avec transaction atomique et UPSERT
     */
    public void savePlayerData(UUID playerId) {
        if (playerId == null) return;

        ReentrantReadWriteLock lock = playerLocks.computeIfAbsent(playerId, k -> new ReentrantReadWriteLock());
        lock.readLock().lock();

        try {
            PlayerData data = playerDataCache.get(playerId);
            if (data == null) {
                plugin.getPluginLogger().debug("§eAucune donnée en cache pour " + playerId + ", sauvegarde ignorée.");
                return;
            }

            // Requête UPSERT pour éviter les conflits de clés primaires
            String query = """
                    INSERT INTO players (
                        uuid, name, coins, tokens, experience, beacons, coins_via_pickaxe, tokens_via_pickaxe, 
                        experience_via_pickaxe, active_profession, last_profession_change, enchantments, 
                        auto_upgrade, mobility_disabled, pickaxe_cristals, custom_permissions, sanctions, 
                        active_enchantments, pickaxe_enchantment_book_levels, profession_levels, profession_xp, 
                        talent_levels, kit_levels, profession_rewards, chosen_prestige_columns, chosen_special_rewards, 
                        reputation, boosts, autominer_active_slot_1, autominer_active_slot_2, autominer_fuel_reserve, 
                        autominer_current_world, autominer_storage_level, autominer_storage_items, 
                        autominer_pending_coins, autominer_pending_tokens, autominer_pending_experience, 
                        autominer_pending_beacons, bank_savings_balance, bank_safe_balance, bank_level, 
                        bank_total_deposits, bank_last_interest, bank_investments, statistics_total_blocks_mined, 
                        statistics_total_blocks_destroyed, statistics_total_greed_triggers, statistics_total_keys_obtained, 
                        gang_id, gang_invitation, selected_outpost_skin, unlocked_outpost_skins, collected_heads, claimed_head_rewards
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?,?,?)
                    ON CONFLICT (uuid) DO UPDATE SET
                        name = EXCLUDED.name,
                        coins = EXCLUDED.coins,
                        tokens = EXCLUDED.tokens,
                        experience = EXCLUDED.experience,
                        beacons = EXCLUDED.beacons,
                        coins_via_pickaxe = EXCLUDED.coins_via_pickaxe,
                        tokens_via_pickaxe = EXCLUDED.tokens_via_pickaxe,
                        experience_via_pickaxe = EXCLUDED.experience_via_pickaxe,
                        active_profession = EXCLUDED.active_profession,
                        last_profession_change = EXCLUDED.last_profession_change,
                        enchantments = EXCLUDED.enchantments,
                        auto_upgrade = EXCLUDED.auto_upgrade,
                        mobility_disabled = EXCLUDED.mobility_disabled,
                        pickaxe_cristals = EXCLUDED.pickaxe_cristals,
                        custom_permissions = EXCLUDED.custom_permissions,
                        sanctions = EXCLUDED.sanctions,
                        active_enchantments = EXCLUDED.active_enchantments,
                        pickaxe_enchantment_book_levels = EXCLUDED.pickaxe_enchantment_book_levels,
                        profession_levels = EXCLUDED.profession_levels,
                        profession_xp = EXCLUDED.profession_xp,
                        talent_levels = EXCLUDED.talent_levels,
                        kit_levels = EXCLUDED.kit_levels,
                        profession_rewards = EXCLUDED.profession_rewards,
                        chosen_prestige_columns = EXCLUDED.chosen_prestige_columns,
                        chosen_special_rewards = EXCLUDED.chosen_special_rewards,
                        reputation = EXCLUDED.reputation,
                        boosts = EXCLUDED.boosts,
                        autominer_active_slot_1 = EXCLUDED.autominer_active_slot_1,
                        autominer_active_slot_2 = EXCLUDED.autominer_active_slot_2,
                        autominer_fuel_reserve = EXCLUDED.autominer_fuel_reserve,
                        autominer_current_world = EXCLUDED.autominer_current_world,
                        autominer_storage_level = EXCLUDED.autominer_storage_level,
                        autominer_storage_items = EXCLUDED.autominer_storage_items,
                        autominer_pending_coins = EXCLUDED.autominer_pending_coins,
                        autominer_pending_tokens = EXCLUDED.autominer_pending_tokens,
                        autominer_pending_experience = EXCLUDED.autominer_pending_experience,
                        autominer_pending_beacons = EXCLUDED.autominer_pending_beacons,
                        bank_savings_balance = EXCLUDED.bank_savings_balance,
                        bank_safe_balance = EXCLUDED.bank_safe_balance,
                        bank_level = EXCLUDED.bank_level,
                        bank_total_deposits = EXCLUDED.bank_total_deposits,
                        bank_last_interest = EXCLUDED.bank_last_interest,
                        bank_investments = EXCLUDED.bank_investments,
                        statistics_total_blocks_mined = EXCLUDED.statistics_total_blocks_mined,
                        statistics_total_blocks_destroyed = EXCLUDED.statistics_total_blocks_destroyed,
                        statistics_total_greed_triggers = EXCLUDED.statistics_total_greed_triggers,
                        statistics_total_keys_obtained = EXCLUDED.statistics_total_keys_obtained,
                        gang_id = EXCLUDED.gang_id,
                        gang_invitation = EXCLUDED.gang_invitation,
                        selected_outpost_skin =  EXCLUDED.selected_outpost_skin,
                        unlocked_outpost_skins =  EXCLUDED.unlocked_outpost_skins,
                        collected_heads = EXCLUDED.collected_heads,
                        claimed_head_rewards = EXCLUDED.claimed_head_rewards
                    """;

            try (Connection conn = databaseManager.getConnection()) {
                conn.setAutoCommit(false); // Début de transaction

                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    long currentTime = System.currentTimeMillis();

                    // Préparation de tous les paramètres
                    ps.setString(1, playerId.toString());
                    ps.setString(2, data.getPlayerName());
                    ps.setLong(3, data.getCoins());
                    ps.setLong(4, data.getTokens());
                    ps.setLong(5, data.getExperience());
                    ps.setLong(6, data.getBeacons());
                    ps.setLong(7, data.getCoinsViaPickaxe());
                    ps.setLong(8, data.getTokensViaPickaxe());
                    ps.setLong(9, data.getExperienceViaPickaxe());
                    ps.setString(10, data.getActiveProfession());
                    ps.setLong(11, data.getLastProfessionChange());
                    ps.setString(12, gson.toJson(data.getEnchantmentLevels()));
                    ps.setString(13, gson.toJson(data.getAutoUpgradeEnabled()));
                    ps.setString(14, gson.toJson(data.getMobilityEnchantmentsDisabled()));
                    ps.setString(15, gson.toJson(data.getPickaxeCristals()));
                    ps.setString(16, gson.toJson(data.getCustomPermissions()));
                    ps.setString(17, gson.toJson(data.getSanctionHistory()));
                    ps.setString(18, gson.toJson(data.getActiveEnchantmentBooks()));
                    ps.setString(19, gson.toJson(data.getPickaxeEnchantmentBookLevels()));
                    ps.setString(20, gson.toJson(data.getAllProfessionLevels()));
                    ps.setString(21, gson.toJson(data.getAllProfessionXP()));
                    ps.setString(22, gson.toJson(data.getAllTalentLevels()));
                    ps.setString(23, gson.toJson(data.getAllKitLevels()));
                    ps.setString(24, gson.toJson(data.getAllClaimedProfessionRewards()));
                    ps.setString(25, gson.toJson(data.getChosenPrestigeColumns()));
                    ps.setString(26, gson.toJson(data.getChosenSpecialRewards()));
                    ps.setInt(27, data.getReputation());
                    ps.setString(28, gson.toJson(data.getActiveBoosts()));
                    ps.setString(29, serializeItemStack(data.getActiveAutominerSlot1()));
                    ps.setString(30, serializeItemStack(data.getActiveAutominerSlot2()));
                    ps.setDouble(31, data.getAutominerFuelReserve());
                    ps.setString(32, data.getAutominerCurrentWorld());
                    ps.setInt(33, data.getAutominerStorageLevel());
                    ps.setString(34, gson.toJson(data.getAutominerStorageContents()));
                    ps.setLong(35, data.getAutominerPendingCoins());
                    ps.setLong(36, data.getAutominerPendingTokens());
                    ps.setLong(37, data.getAutominerPendingExperience());
                    ps.setLong(38, data.getAutominerPendingBeacons());
                    ps.setLong(39, data.getBankSavingsBalance());
                    ps.setLong(40, data.getBankSafeBalance());
                    ps.setInt(41, data.getBankLevel());
                    ps.setLong(42, data.getTotalBankDeposits());
                    ps.setLong(43, data.getBankLastInterest());
                    ps.setString(44, gson.toJson(data.getBankInvestments()));
                    ps.setLong(45, data.getTotalBlocksMined());
                    ps.setLong(46, data.getTotalBlocksDestroyed());
                    ps.setLong(47, data.getTotalGreedTriggers());
                    ps.setLong(48, data.getTotalKeysObtained());
                    ps.setString(49, data.getGangId());
                    ps.setString(50, data.getGangInvitation());
                    ps.setString(51, data.getSelectedOutpostSkin());
                    ps.setString(52, gson.toJson(data.getUnlockedOutpostSkins()));
                    ps.setString(53, gson.toJson(data.getCollectedHeads()));
                    ps.setString(54, gson.toJson(data.getClaimedHeadRewards()));

                    ps.executeUpdate();
                    conn.commit();
                    lastSaveTime.put(playerId, currentTime);

                    plugin.getPluginLogger().debug("§aDonnées sauvegardées avec succès pour " + data.getPlayerName());

                } catch (SQLException e) {
                    conn.rollback(); // Annulation en cas d'erreur
                    throw e;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("§cErreur lors de la sauvegarde de " + playerId + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * NOUVELLE MÉTHODE : Sérialise un ItemStack pour la base de données
     */
    private String serializeItemStack(ItemStack item) {
        if (item == null) return null;
        try {
            return gson.toJson(item.serialize());
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur lors de la sérialisation d'un ItemStack: " + e.getMessage());
            return null;
        }
    }

    /**
     * Marque un joueur comme modifié (thread-safe)
     */
    public void markDirty(UUID playerId) {
        if (playerId != null) {
            dirtyPlayers.add(playerId);
        }
    }

    /**
     * MÉTHODE CORRIGÉE : Sauvegarde asynchrone des joueurs modifiés uniquement
     */
    public CompletableFuture<Void> saveAllPlayersAsync() {
        return CompletableFuture.runAsync(() -> {
            Set<UUID> playersToSave = new HashSet<>(dirtyPlayers);
            dirtyPlayers.clear(); // Nettoyage immédiat pour éviter les doublons

            int savedCount = 0;
            for (UUID playerId : playersToSave) {
                try {
                    savePlayerData(playerId);
                    savedCount++;
                } catch (Exception e) {
                    plugin.getLogger().severe("§cErreur lors de la sauvegarde asynchrone de " + playerId + ": " + e.getMessage());
                    dirtyPlayers.add(playerId); // Re-marquer pour tentative ultérieure
                }
            }

            if (savedCount > 0) {
                plugin.getPluginLogger().info("§aSauvegarde asynchrone terminée: " + savedCount + " joueur(s) sauvegardé(s).");
            }
        });
    }

    /**
     * MÉTHODE CORRIGÉE : Sauvegarde synchrone des joueurs modifiés uniquement
     */
    public void saveAllPlayersSync() {
        Set<UUID> playersToSave = new HashSet<>(dirtyPlayers);
        dirtyPlayers.clear();

        int savedCount = 0;
        for (UUID playerId : playersToSave) {
            try {
                savePlayerData(playerId);
                savedCount++;
            } catch (Exception e) {
                plugin.getLogger().severe("§cErreur lors de la sauvegarde synchrone de " + playerId + ": " + e.getMessage());
                dirtyPlayers.add(playerId); // Re-marquer pour tentative ultérieure
            }
        }

        if (savedCount > 0) {
            plugin.getPluginLogger().info("§aSauvegarde synchrone terminée: " + savedCount + " joueur(s) sauvegardé(s).");
        }
    }

    /**
     * Sauvegarde immédiate d'un joueur spécifique
     */
    public void savePlayerNow(UUID playerId) {
        if (playerId != null) {
            savePlayerData(playerId);
            dirtyPlayers.remove(playerId);
        }
    }

    /**
     * Décharge un joueur du cache après sauvegarde
     */
    public void unloadPlayer(UUID playerId) {
        if (playerId != null) {
            savePlayerNow(playerId);
            playerDataCache.remove(playerId);
            playerLocks.remove(playerId);
            lastSaveTime.remove(playerId);
        }
    }

    /**
     * Récupère le nom d'un joueur
     */
    public String getPlayerName(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        return player != null ? player.getName() : "Unknown";
    }

    /**
     * Récupère tous les joueurs en cache
     */
    public Collection<PlayerData> getAllCachedPlayers() {
        return new ArrayList<>(playerDataCache.values());
    }

    /**
     * Nettoyage du cache (utilisé par AutoSaveTask)
     */
    public void cleanupCache() {
        // Optionnel: retirer les joueurs hors ligne du cache après sauvegarde
        long now = System.currentTimeMillis();
        Set<UUID> toRemove = new HashSet<>();

        for (UUID playerId : playerDataCache.keySet()) {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                Long lastSave = lastSaveTime.get(playerId);
                // Retire du cache après 30 minutes d'inactivité
                if (lastSave != null && (now - lastSave) > 1800000) { // 30 minutes
                    toRemove.add(playerId);
                }
            }
        }

        for (UUID playerId : toRemove) {
            unloadPlayer(playerId);
        }

        if (!toRemove.isEmpty()) {
            plugin.getPluginLogger().debug("§7Nettoyage du cache: " + toRemove.size() + " joueur(s) déchargé(s).");
        }
    }

    /**
     * NOUVELLE MÉTHODE : Statistiques du gestionnaire
     */
    public String getStats() {
        return String.format("§7Cache: %d joueurs | Dirty: %d | Verrous: %d",
                playerDataCache.size(), dirtyPlayers.size(), playerLocks.size());
    }

    /**
     * NOUVELLE MÉTHODE : Force le rechargement d'un joueur depuis la BDD
     */
    public void reloadPlayerData(UUID playerId) {
        if (playerId != null) {
            // Sauvegarde d'abord si le joueur est modifié
            if (dirtyPlayers.contains(playerId)) {
                savePlayerNow(playerId);
            }

            // Supprime du cache et recharge
            playerDataCache.remove(playerId);
            getPlayerData(playerId); // Force le rechargement
        }
    }

    /**
     * NOUVELLE MÉTHODE : Vérifie la cohérence des données
     */
    public boolean validatePlayerData(UUID playerId) {
        PlayerData data = playerDataCache.get(playerId);
        if (data == null) return false;

        // Vérifications de base
        if (data.getCoins() < 0 || data.getTokens() < 0 || data.getExperience() < 0 || data.getBeacons() < 0) {
            plugin.getPluginLogger().warning("§cDonnées invalides détectées pour " + data.getPlayerName() + " (valeurs négatives)");
            return false;
        }

        return true;
    }
}
