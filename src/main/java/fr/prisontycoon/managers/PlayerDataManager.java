package fr.prisontycoon.managers;

import com.google.gson.Gson;
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

public class PlayerDataManager {

    private final PrisonTycoon plugin;
    private final DatabaseManager databaseManager;
    private final Gson gson = new Gson();

    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();

    public PlayerDataManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        createPlayersTable();
    }

    private void createPlayersTable() {
        String query = """
                    CREATE TABLE IF NOT EXISTS players (
                        uuid VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(16),
                        coins BIGINT,
                        tokens BIGINT,
                        experience BIGINT,
                        beacons BIGINT,
                        coins_via_pickaxe BIGINT,
                        tokens_via_pickaxe BIGINT,
                        experience_via_pickaxe BIGINT,
                        active_profession VARCHAR(255),
                        last_profession_change BIGINT,
                        enchantments TEXT,
                        auto_upgrade TEXT,
                        mobility_disabled TEXT,
                        pickaxe_cristals TEXT,
                        custom_permissions TEXT,
                        sanctions TEXT,
                        active_enchantments TEXT,
                        pickaxe_enchantment_books TEXT,
                        profession_levels TEXT,
                        profession_xp TEXT,
                        talent_levels TEXT,
                        kit_levels TEXT,
                        profession_rewards TEXT,
                        chosen_prestige_columns TEXT,
                        chosen_special_rewards TEXT,
                        reputation INT,
                        boosts TEXT,
                        autominer_active_slot_1 TEXT,
                        autominer_active_slot_2 TEXT,
                        autominer_fuel_reserve DOUBLE PRECISION,
                        autominer_current_world VARCHAR(255),
                        autominer_storage_level INT,
                        autominer_storage_contents TEXT,
                        autominer_stored_keys TEXT,
                        autominer_pending_coins BIGINT,
                        autominer_pending_tokens BIGINT,
                        autominer_pending_experience BIGINT,
                        autominer_pending_beacons BIGINT,
                        bank_savings_balance BIGINT,
                        bank_safe_balance BIGINT,
                        bank_level INT,
                        bank_total_deposits BIGINT,
                        bank_last_interest BIGINT,
                        bank_investments TEXT,
                        statistics_total_blocks_mined BIGINT,
                        statistics_total_blocks_destroyed BIGINT,
                        statistics_total_greed_triggers BIGINT,
                        statistics_total_keys_obtained BIGINT,
                        gang_id VARCHAR(36),
                        gang_invitation VARCHAR(36)
                    );
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create players table: " + e.getMessage());
        }
    }

    public PlayerData getPlayerData(UUID playerId) {
        if (playerDataCache.containsKey(playerId)) {
            return playerDataCache.get(playerId);
        }

        PlayerData playerData = loadPlayerDataFromDatabase(playerId);
        playerDataCache.put(playerId, playerData);
        return playerData;
    }

    private PlayerData loadPlayerDataFromDatabase(UUID playerId) {
        String query = "SELECT * FROM players WHERE uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                String playerName = rs.getString("name");
                PlayerData data = new PlayerData(playerId, playerName);

                data.setCoins(rs.getLong("coins"));
                data.setTokens(rs.getLong("tokens"));
                data.setExperience(rs.getLong("experience"));
                data.setBeacons(rs.getLong("beacons"));
                data.setCoinsViaPickaxe(rs.getLong("coins_via_pickaxe"));
                data.setTokensViaPickaxe(rs.getLong("tokens_via_pickaxe"));
                data.setExperienceViaPickaxe(rs.getLong("experience_via_pickaxe"));
                data.setActiveProfession(rs.getString("active_profession"));
                data.setLastProfessionChange(rs.getLong("last_profession_change"));

                Type stringIntegerMapType = new TypeToken<Map<String, Integer>>() {
                }.getType();
                Type stringStringMapType = new TypeToken<Map<String, String>>() {
                }.getType();
                Type stringSetType = new TypeToken<Set<String>>() {
                }.getType();
                Type sanctionListType = new TypeToken<List<PlayerData.SanctionData>>() {
                }.getType();
                Type stringMapMapType = new TypeToken<Map<String, Map<String, Integer>>>() {
                }.getType();
                Type intTalentMapType = new TypeToken<Map<Integer, PrestigeTalent>>() {
                }.getType();
                Type intStringMapType = new TypeToken<Map<Integer, String>>() {
                }.getType();
                Type stringBoostMapType = new TypeToken<Map<String, PlayerBoost>>() {
                }.getType();
                Type materialLongMapType = new TypeToken<Map<Material, Long>>() {
                }.getType();

                data.getEnchantmentLevels().putAll(gson.fromJson(rs.getString("enchantments"), stringIntegerMapType));
                data.getAutoUpgradeEnabled().addAll(gson.fromJson(rs.getString("auto_upgrade"), stringSetType));
                data.getMobilityEnchantmentsDisabled().addAll(gson.fromJson(rs.getString("mobility_disabled"), stringSetType));
                data.getPickaxeCristals().putAll(gson.fromJson(rs.getString("pickaxe_cristals"), stringStringMapType));
                data.getCustomPermissions().addAll(gson.fromJson(rs.getString("custom_permissions"), stringSetType));
                data.getSanctionHistory().addAll(gson.fromJson(rs.getString("sanctions"), sanctionListType));
                data.getActiveEnchantmentBooks().addAll(gson.fromJson(rs.getString("active_enchantments"), stringSetType));
                data.getPLayerEnchantmentBooks().addAll(gson.fromJson(rs.getString("pickaxe_enchantment_books"), stringSetType));
                data.getAllProfessionLevels().putAll(gson.fromJson(rs.getString("profession_levels"), stringIntegerMapType));
                data.getAllProfessionXP().putAll(gson.fromJson(rs.getString("profession_xp"), stringIntegerMapType));
                data.getAllTalentLevels().putAll(gson.fromJson(rs.getString("talent_levels"), stringMapMapType));
                data.getAllKitLevels().putAll(gson.fromJson(rs.getString("kit_levels"), stringIntegerMapType));
                data.getAllClaimedProfessionRewards().putAll(gson.fromJson(rs.getString("profession_rewards"), new TypeToken<Map<String, Set<Integer>>>() {
                }.getType()));
                data.getChosenPrestigeColumns().putAll(gson.fromJson(rs.getString("chosen_prestige_columns"), intTalentMapType));
                data.getChosenSpecialRewards().putAll(gson.fromJson(rs.getString("chosen_special_rewards"), intStringMapType));
                data.setReputation(rs.getInt("reputation"));
                data.getActiveBoosts().putAll(gson.fromJson(rs.getString("boosts"), stringBoostMapType));
                data.setActiveAutominerSlot1(gson.fromJson(rs.getString("autominer_active_slot_1"), ItemStack.class));
                data.setActiveAutominerSlot2(gson.fromJson(rs.getString("autominer_active_slot_2"), ItemStack.class));
                data.setAutominerFuelReserve(rs.getDouble("autominer_fuel_reserve"));
                data.setAutominerCurrentWorld(rs.getString("autominer_current_world"));
                data.setAutominerStorageLevel(rs.getInt("autominer_storage_level"));
                data.getAutominerStorageContents().putAll(gson.fromJson(rs.getString("autominer_storage_contents"), materialLongMapType));
                data.getAutominerStoredKeys().putAll(gson.fromJson(rs.getString("autominer_stored_keys"), stringIntegerMapType));
                data.setAutominerPendingCoins(rs.getLong("autominer_pending_coins"));
                data.setAutominerPendingTokens(rs.getLong("autominer_pending_tokens"));
                data.setAutominerPendingExperience(rs.getLong("autominer_pending_experience"));
                data.setAutominerPendingBeacons(rs.getLong("autominer_pending_beacons"));
                data.setSavingsBalance(rs.getLong("bank_savings_balance"));
                data.setSafeBalance(rs.getLong("bank_safe_balance"));
                data.setBankLevel(rs.getInt("bank_level"));
                data.setTotalBankDeposits(rs.getLong("bank_total_deposits"));
                data.setLastInterestTime(rs.getLong("bank_last_interest"));
                data.getAllInvestments().putAll(gson.fromJson(rs.getString("bank_investments"), materialLongMapType));
                data.setTotalBlocksMined(rs.getLong("statistics_total_blocks_mined"));
                data.setTotalBlocksDestroyed(rs.getLong("statistics_total_blocks_destroyed"));
                data.setTotalGreedTriggers(rs.getLong("statistics_total_greed_triggers"));
                data.setTotalKeysObtained(rs.getLong("statistics_total_keys_obtained"));
                data.setGangId(rs.getString("gang_id"));
                data.setGangInvitation(rs.getString("gang_invitation"));

                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load player data from database: " + e.getMessage());
        }
        return new PlayerData(playerId, getPlayerName(playerId));
    }

    public void savePlayerData(UUID playerId) {
        PlayerData data = playerDataCache.get(playerId);
        if (data == null) {
            return;
        }

        String query = """
                    INSERT INTO players (uuid, name, coins, tokens, experience, beacons, coins_via_pickaxe, tokens_via_pickaxe, experience_via_pickaxe, active_profession, last_profession_change, enchantments, auto_upgrade, mobility_disabled, pickaxe_cristals, custom_permissions, sanctions, active_enchantments, pickaxe_enchantment_books, profession_levels, profession_xp, talent_levels, kit_levels, profession_rewards, chosen_prestige_columns, chosen_special_rewards, reputation, boosts, autominer_active_slot_1, autominer_active_slot_2, autominer_fuel_reserve, autominer_current_world, autominer_storage_level, autominer_storage_contents, autominer_stored_keys, autominer_pending_coins, autominer_pending_tokens, autominer_pending_experience, autominer_pending_beacons, bank_savings_balance, bank_safe_balance, bank_level, bank_total_deposits, bank_last_interest, bank_investments, statistics_total_blocks_mined, statistics_total_blocks_destroyed, statistics_total_greed_triggers, statistics_total_keys_obtained, gang_id, gang_invitation)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        pickaxe_enchantment_books = EXCLUDED.pickaxe_enchantment_books,
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
                        autominer_storage_contents = EXCLUDED.autominer_storage_contents,
                        autominer_stored_keys = EXCLUDED.autominer_stored_keys,
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
                        gang_invitation = EXCLUDED.gang_invitation;
                """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {

            ps.setString(1, data.getPlayerId().toString());
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
            ps.setString(19, gson.toJson(data.getPLayerEnchantmentBooks()));
            ps.setString(20, gson.toJson(data.getAllProfessionLevels()));
            ps.setString(21, gson.toJson(data.getAllProfessionXP()));
            ps.setString(22, gson.toJson(data.getAllTalentLevels()));
            ps.setString(23, gson.toJson(data.getAllKitLevels()));
            ps.setString(24, gson.toJson(data.getAllClaimedProfessionRewards()));
            ps.setString(25, gson.toJson(data.getChosenPrestigeColumns()));
            ps.setString(26, gson.toJson(data.getChosenSpecialRewards()));
            ps.setInt(27, data.getReputation());
            ps.setString(28, gson.toJson(data.getActiveBoosts()));
            ps.setString(29, gson.toJson(data.getActiveAutominerSlot1()));
            ps.setString(30, gson.toJson(data.getActiveAutominerSlot2()));
            ps.setDouble(31, data.getAutominerFuelReserve());
            ps.setString(32, data.getAutominerCurrentWorld());
            ps.setInt(33, data.getAutominerStorageLevel());
            ps.setString(34, gson.toJson(data.getAutominerStorageContents()));
            ps.setString(35, gson.toJson(data.getAutominerStoredKeys()));
            ps.setLong(36, data.getAutominerPendingCoins());
            ps.setLong(37, data.getAutominerPendingTokens());
            ps.setLong(38, data.getAutominerPendingExperience());
            ps.setLong(39, data.getAutominerPendingBeacons());
            ps.setLong(40, data.getSavingsBalance());
            ps.setLong(41, data.getSafeBalance());
            ps.setInt(42, data.getBankLevel());
            ps.setLong(43, data.getTotalBankDeposits());
            ps.setLong(44, data.getLastInterestTime());
            ps.setString(45, gson.toJson(data.getAllInvestments()));
            ps.setLong(46, data.getTotalBlocksMined());
            ps.setLong(47, data.getTotalBlocksDestroyed());
            ps.setLong(48, data.getTotalGreedTriggers());
            ps.setLong(49, data.getTotalKeysObtained());
            ps.setString(50, data.getGangId());
            ps.setString(51, data.getGangInvitation());

            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save player data to database: " + e.getMessage());
        }
    }

    public void markDirty(UUID playerId) {
        dirtyPlayers.add(playerId);
    }

    public CompletableFuture<Void> saveAllPlayersAsync() {
        return CompletableFuture.runAsync(() -> {
            for (UUID playerId : dirtyPlayers) {
                savePlayerData(playerId);
            }
            dirtyPlayers.clear();
        });
    }

    public void saveAllPlayersSync() {
        for (UUID playerId : playerDataCache.keySet()) {
            savePlayerData(playerId);
        }
        dirtyPlayers.clear();
    }

    public void savePlayerNow(UUID playerId) {
        savePlayerData(playerId);
        dirtyPlayers.remove(playerId);
    }

    public void unloadPlayer(UUID playerId) {
        savePlayerNow(playerId);
        playerDataCache.remove(playerId);
    }

    public String getPlayerName(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        return player != null ? player.getName() : "Unknown";
    }

    public Collection<PlayerData> getAllCachedPlayers() {
        return playerDataCache.values();
    }

    public void cleanupCache() {
        // No-op with database
    }
}
