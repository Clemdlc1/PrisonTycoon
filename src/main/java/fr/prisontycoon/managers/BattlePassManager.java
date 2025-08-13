package fr.prisontycoon.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.quests.QuestRewards;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Gestionnaire du Pass de combat (Battle Pass)
 * - Durée: 30 jours par défaut
 * - Deux lignes de récompenses: Gratuit et Premium
 * - 50 paliers principaux, puis 1 clé Rare par palier supplémentaire
 * - Points nécessaires par palier: 50
 */
public class BattlePassManager {

    public static final int POINTS_PER_TIER = 50;
    public static final int MAX_DEFINED_TIERS = 50;
    private static final long SEASON_DURATION_DAYS = 30L;
    private static final long PREMIUM_PRICE_BEACONS = 20000L;
    private static final double VIP_DISCOUNT = 0.25; // -25% si VIP

    private final PrisonTycoon plugin;
    private final Gson gson = new Gson();
    private final Type intSetType = new TypeToken<Set<Integer>>() {}.getType();

    private final Map<Integer, TierRewards> tierRewards = new HashMap<>();

    public BattlePassManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        initializeDefaultRewards();
    }


    private void initializeDefaultRewards() {
        // Exemples détaillés pour les 10 premiers paliers
        for (int tier = 1; tier <= 10; tier++) {
            TierRewards tr = exampleTierRewards(tier);
            tierRewards.put(tier, tr);
        }
        // Dupliquer une logique croissante simple au-delà du niveau 10 jusqu'à 50
        for (int tier = 11; tier <= MAX_DEFINED_TIERS; tier++) {
            long beaconsFree = 150L * tier;
            long beaconsPremium = 300L * tier;
            tierRewards.put(tier, new TierRewards(
                    QuestRewards.builder().beacons(beaconsFree).build(),
                    QuestRewards.builder().beacons(beaconsPremium).build()
            ));
        }
    }

    private TierRewards exampleTierRewards(int tier) {
        // Switch d'exemples pour 10 premiers paliers
        return switch (tier) {
            case 1 -> new TierRewards(
                    QuestRewards.builder().beacons(250).build(),
                    QuestRewards.builder().beacons(500).essence(2).build()
            );
            case 2 -> new TierRewards(
                    QuestRewards.builder().beacons(300).build(),
                    QuestRewards.builder().beacons(600).build()
            );
            case 3 -> new TierRewards(
                    QuestRewards.builder().beacons(350).build(),
                    QuestRewards.builder().beacons(700).boost(fr.prisontycoon.boosts.BoostType.TOKEN_BOOST, 30, 25.0).build()
            );
            case 4 -> new TierRewards(
                    QuestRewards.builder().beacons(400).build(),
                    QuestRewards.builder().beacons(800).build()
            );
            case 5 -> new TierRewards(
                    QuestRewards.builder().beacons(500).build(),
                    QuestRewards.builder().beacons(1000).essence(5).build()
            );
            case 6 -> new TierRewards(
                    QuestRewards.builder().beacons(600).build(),
                    QuestRewards.builder().beacons(1200).build()
            );
            case 7 -> new TierRewards(
                    QuestRewards.builder().beacons(700).build(),
                    QuestRewards.builder().beacons(1400).boost(fr.prisontycoon.boosts.BoostType.EXPERIENCE_BOOST, 30, 25.0).build()
            );
            case 8 -> new TierRewards(
                    QuestRewards.builder().beacons(800).build(),
                    QuestRewards.builder().beacons(1600).build()
            );
            case 9 -> new TierRewards(
                    QuestRewards.builder().beacons(900).build(),
                    QuestRewards.builder().beacons(1800).voucher(fr.prisontycoon.vouchers.VoucherType.COINS, 2).build()
            );
            case 10 -> new TierRewards(
                    QuestRewards.builder().beacons(1200).build(),
                    QuestRewards.builder().beacons(2400).essence(10).build()
            );
            default -> new TierRewards(QuestRewards.builder().beacons(100).build(), QuestRewards.builder().beacons(200).build());
        };
    }

    public String getCurrentSeasonId() {
        long override = plugin.getConfig().getLong("battlepass.overrideStart", 0L);
        long now = System.currentTimeMillis();
        if (override > 0 && now < override + SEASON_DURATION_DAYS * 24L * 3600L * 1000L) {
            return "S" + override; // identifiant unique basé sur timestamp
        }
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        return String.format("%04d-%02d", first.getYear(), first.getMonthValue());
    }

    public LocalDate getSeasonStartDate() {
        long override = plugin.getConfig().getLong("battlepass.overrideStart", 0L);
        if (override > 0) {
            return LocalDate.ofInstant(new Date(override).toInstant(), ZoneId.systemDefault());
        }
        return LocalDate.now().withDayOfMonth(1);
    }

    public LocalDate getSeasonEndDate() {
        return getSeasonStartDate().plusDays(SEASON_DURATION_DAYS);
    }

    public void startNewSeasonNow() {
        long now = System.currentTimeMillis();
        plugin.getConfig().set("battlepass.overrideStart", now);
        plugin.saveConfig();
        // On ne wipe pas la table; l'ID de saison change donc nouveau jeu de données
    }

    // ============================================================================================
    // Données joueur
    // ============================================================================================

    public PlayerPassData getPlayerData(UUID playerId) {
        String seasonId = getCurrentSeasonId();
        String sql = "SELECT bp_season_id, bp_points, bp_premium, bp_claimed_free, bp_claimed_premium FROM player_quests WHERE uuid = ?";
        try (Connection c = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String storedSeason = rs.getString("bp_season_id");
                    int points = rs.getInt("bp_points");
                    boolean premium = rs.getBoolean("bp_premium");
                    Set<Integer> claimedFree = parseIntSet(rs.getString("bp_claimed_free"));
                    Set<Integer> claimedPremium = parseIntSet(rs.getString("bp_claimed_premium"));
                    if (storedSeason == null || !storedSeason.equals(seasonId)) {
                        // nouvelle saison => reset
                        return new PlayerPassData(0, false, new HashSet<>(), new HashSet<>());
                    }
                    return new PlayerPassData(points, premium, claimedFree, claimedPremium);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("getPlayerData BP: " + e.getMessage());
        }
        // Créer entrée par défaut
        return new PlayerPassData(0, false, new HashSet<>(), new HashSet<>());
    }

    private void upsertPlayerData(UUID playerId, PlayerPassData data) {
        String seasonId = getCurrentSeasonId();
        String sql = """
                INSERT INTO player_quests(uuid, bp_season_id, bp_points, bp_premium, bp_claimed_free, bp_claimed_premium)
                VALUES(?,?,?,?,?,?)
                ON CONFLICT (uuid) DO UPDATE SET
                    bp_season_id = EXCLUDED.bp_season_id,
                    bp_points = EXCLUDED.bp_points,
                    bp_premium = EXCLUDED.bp_premium,
                    bp_claimed_free = EXCLUDED.bp_claimed_free,
                    bp_claimed_premium = EXCLUDED.bp_claimed_premium
                """;
        try (Connection c = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, seasonId);
            ps.setInt(3, Math.max(0, data.points()));
            ps.setBoolean(4, data.premium());
            ps.setString(5, gson.toJson(data.claimedFree()));
            ps.setString(6, gson.toJson(data.claimedPremium()));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("upsertPlayerData BP: " + e.getMessage());
        }
    }

    private Set<Integer> parseIntSet(String json) {
        if (json == null || json.isBlank() || json.equals("null")) return new HashSet<>();
        try {
            Set<Integer> set = gson.fromJson(json, intSetType);
            return set != null ? set : new HashSet<>();
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    public int getPoints(UUID playerId) {
        return getPlayerData(playerId).points();
    }

    public int getTier(UUID playerId) {
        return Math.min(MAX_DEFINED_TIERS + 9999, (getPoints(playerId) / POINTS_PER_TIER));
    }

    public int getProgressInTier(UUID playerId) {
        return getPoints(playerId) % POINTS_PER_TIER;
    }

    public void addPoints(Player player, int amount) {
        if (amount <= 0) return;
        UUID id = player.getUniqueId();
        PlayerPassData data = getPlayerData(id);
        data = data.withPoints(data.points() + amount);
        upsertPlayerData(id, data);
    }

    public boolean hasPremium(UUID playerId) {
        return getPlayerData(playerId).premium();
    }

    public boolean purchasePremium(Player player) {
        UUID id = player.getUniqueId();
        PlayerPassData data = getPlayerData(id);
        if (data.premium()) return false;

        long price = PREMIUM_PRICE_BEACONS;
        try {
            if (plugin.getVipManager().isVip(player.getUniqueId())) {
                price = Math.round(price * (1.0 - VIP_DISCOUNT));
            }
        } catch (Exception ignored) {
        }

        var pd = plugin.getPlayerDataManager().getPlayerData(id);
        if (pd.getBeacons() < price) {
            return false;
        }
        pd.removeBeacon(price);
        plugin.getPlayerDataManager().markDirty(id);

        upsertPlayerData(id, data.withPremium(true));
        return true;
    }

    public boolean claimFree(Player player, int tier) {
        return claimInternal(player, tier, false);
    }

    public boolean claimPremium(Player player, int tier) {
        if (!hasPremium(player.getUniqueId())) return false;
        return claimInternal(player, tier, true);
    }

    private boolean claimInternal(Player player, int tier, boolean premiumRow) {
        if (tier <= 0) return false;
        UUID id = player.getUniqueId();
        PlayerPassData data = getPlayerData(id);
        int currentTier = getTier(id);
        if (tier > currentTier) return false; // pas encore atteint

        if (premiumRow) {
            if (data.claimedPremium().contains(tier)) return false;
        } else {
            if (data.claimedFree().contains(tier)) return false;
        }

        // Récompense
        if (tier > MAX_DEFINED_TIERS) {
            // Donne 1 clé Rare par palier supplémentaire
            ItemStack key = plugin.getEnchantmentManager().createKey("Rare");
            boolean added = plugin.getContainerManager().addItemToContainers(player, key);
            if (!added) player.getInventory().addItem(key);
        } else {
            TierRewards tr = tierRewards.get(tier);
            if (tr == null) return false;
            QuestRewards rewards = premiumRow ? tr.premium() : tr.free();
            rewards.grant(plugin, player);
        }

        // Marque comme réclamé
        if (premiumRow) {
            data.claimedPremium().add(tier);
        } else {
            data.claimedFree().add(tier);
        }
        upsertPlayerData(id, data);
        return true;
    }

    public TierRewards getRewardsForTier(int tier) {
        return tierRewards.getOrDefault(tier, tierRewards.get(MAX_DEFINED_TIERS));
    }

    public int getTotalPages() {
        return (int) Math.ceil(MAX_DEFINED_TIERS / 6.0);
    }

    public record TierRewards(QuestRewards free, QuestRewards premium) {}

    public record PlayerPassData(int points, boolean premium, Set<Integer> claimedFree, Set<Integer> claimedPremium) {
        public PlayerPassData withPoints(int p) { return new PlayerPassData(Math.max(0, p), premium, new HashSet<>(claimedFree), new HashSet<>(claimedPremium)); }
        public PlayerPassData withPremium(boolean value) { return new PlayerPassData(points, value, new HashSet<>(claimedFree), new HashSet<>(claimedPremium)); }
    }
}


