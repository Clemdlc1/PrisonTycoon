package fr.prisontycoon.quests;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des quêtes (chargement, progression, récompenses).
 * Optimisé et configurable.
 */
public class QuestManager {
    private final PrisonTycoon plugin;
    private final Map<String, QuestDefinition> quests = new HashMap<>();
    private final Map<UUID, PlayerQuestProgress> cache = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private File questsFile;
    private FileConfiguration questsConfig;

    public QuestManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        ensureTables();
        loadConfig();
        loadQuestsFromConfig();
    }

    private void ensureTables() {
        String q1 = """
                CREATE TABLE IF NOT EXISTS player_quests (
                    uuid VARCHAR(36) PRIMARY KEY,
                    progress_json TEXT,
                    claimed_json TEXT,
                    daily_date VARCHAR(20),
                    daily_completed INT,
                    weekly_start VARCHAR(20),
                    weekly_completed INT
                );
                """;
        try (Connection c = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = c.prepareStatement(q1)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("QuestManager table error: " + e.getMessage());
        }

        String q2 = """
                CREATE TABLE IF NOT EXISTS player_block_stats (
                    uuid VARCHAR(36) PRIMARY KEY,
                    stats_json TEXT
                );
                """;
        try (Connection c = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = c.prepareStatement(q2)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Block stats table error: " + e.getMessage());
        }
    }

    private void loadConfig() {
        questsFile = new File(plugin.getDataFolder(), "quests.yml");
        if (!questsFile.exists()) {
            try {
                plugin.saveResource("quests.yml", false);
            } catch (IllegalArgumentException ignored) {
                // fallback: create empty
                try {
                    questsFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Impossible de créer quests.yml");
                }
            }
        }
        questsConfig = YamlConfiguration.loadConfiguration(questsFile);
    }

    private void loadQuestsFromConfig() {
        quests.clear();
        ConfigurationSection daily = questsConfig.getConfigurationSection("daily");
        ConfigurationSection weekly = questsConfig.getConfigurationSection("weekly");
        if (daily != null) {
            loadCategory(daily, QuestCategory.DAILY);
        }
        if (weekly != null) {
            loadCategory(weekly, QuestCategory.WEEKLY);
        }
    }

    private void loadCategory(ConfigurationSection section, QuestCategory category) {
        for (String id : section.getKeys(false)) {
            ConfigurationSection q = section.getConfigurationSection(id);
            if (q == null) continue;
            QuestType type = QuestType.valueOf(q.getString("type", "KILL_PLAYERS").toUpperCase());
            int target = q.getInt("target", 1);
            Map<String, Object> params = new HashMap<>();
            ConfigurationSection p = q.getConfigurationSection("params");
            if (p != null) for (String k : p.getKeys(false)) params.put(k, p.get(k));

            // rewards
            ConfigurationSection r = q.getConfigurationSection("rewards");
            long beacons = r != null ? r.getLong("beacons", 0) : 0;
            int jobXp = r != null ? r.getInt("job_xp", 0) : 0;
            VoucherType voucherType = null;
            int voucherTier = 0;
            BoostType boostType = null;
            int boostMinutes = 0;
            double boostPercent = 0;
            if (r != null) {
                String vType = r.getString("voucher.type", null);
                if (vType != null) {
                    try {
                        voucherType = VoucherType.valueOf(vType.toUpperCase());
                    } catch (Exception ignored) {
                    }
                    voucherTier = r.getInt("voucher.tier", 1);
                }
                String bType = r.getString("boost.type", null);
                if (bType != null) {
                    try {
                        boostType = BoostType.valueOf(bType.toUpperCase());
                    } catch (Exception ignored) {
                    }
                    boostMinutes = r.getInt("boost.minutes", 30);
                    boostPercent = r.getDouble("boost.percent", 50.0);
                }
            }
            // Seul fragments.essence est pris en compte pour les quêtes
            int fragEssence = r != null ? r.getInt("fragments.essence", 0) : 0;

            // Contrainte: les quêtes ne peuvent donner que des fragments d'Essence
            QuestRewards.Builder builder = QuestRewards.builder()
                    .beacons(beacons)
                    .jobXp(jobXp);
            if (fragEssence > 0) {
                builder.essence(fragEssence);
            }
            if (voucherType != null && voucherTier > 0) {
                builder.voucher(voucherType, voucherTier);
            }
            if (boostType != null && boostMinutes > 0 && boostPercent > 0) {
                builder.boost(boostType, boostMinutes, boostPercent);
            }
            QuestRewards rewards = builder.build();
            quests.put(id, new QuestDefinition(id, category, type, target, params, rewards));
        }
    }

    public Collection<QuestDefinition> getAllQuests() {
        return quests.values();
    }

    public List<QuestDefinition> getQuestsByCategory(QuestCategory cat) {
        List<QuestDefinition> list = new ArrayList<>();
        for (var q : quests.values()) if (q.getCategory() == cat) list.add(q);
        return list;
    }

    public QuestDefinition get(String id) {
        return quests.get(id);
    }

    public PlayerQuestProgress getProgress(UUID playerId) {
        return cache.computeIfAbsent(playerId, this::loadProgress);
    }

    private PlayerQuestProgress loadProgress(UUID playerId) {
        String sql = "SELECT * FROM player_quests WHERE uuid = ?";
        try (Connection c = plugin.getDatabaseManager().getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerQuestProgress p = new PlayerQuestProgress(playerId);
                    Type mapType = new TypeToken<Map<String, Integer>>() {
                    }.getType();
                    Map<String, Integer> progress = gson.fromJson(rs.getString("progress_json"), mapType);
                    Map<String, Boolean> claimed = gson.fromJson(rs.getString("claimed_json"), new TypeToken<Map<String, Boolean>>() {
                    }.getType());
                    if (progress != null) progress.forEach(p::set);
                    if (claimed != null) claimed.forEach((k, v) -> {
                        if (Boolean.TRUE.equals(v)) p.setClaimed(k);
                    });
                    String daily = rs.getString("daily_date");
                    if (daily != null) {
                        // Force reset si date différente
                        if (!LocalDate.parse(daily).equals(LocalDate.now())) p.resetDailyIfNeeded();
                    }
                    String start = rs.getString("weekly_start");
                    if (start != null) {
                        if (!LocalDate.parse(start).equals(LocalDate.now().with(java.time.DayOfWeek.MONDAY)))
                            p.resetWeeklyIfNeeded();
                    }
                    return p;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadProgress error: " + e.getMessage());
        }
        return new PlayerQuestProgress(playerId);
    }

    public void saveProgress(PlayerQuestProgress p) {
        try (Connection c = plugin.getDatabaseManager().getConnection()) {
            String sql = """
                    INSERT INTO player_quests(uuid, progress_json, claimed_json, daily_date, daily_completed, weekly_start, weekly_completed)
                    VALUES(?,?,?,?,?,?,?)
                    ON CONFLICT (uuid) DO UPDATE SET
                        progress_json = EXCLUDED.progress_json,
                        claimed_json = EXCLUDED.claimed_json,
                        daily_date = EXCLUDED.daily_date,
                        daily_completed = EXCLUDED.daily_completed,
                        weekly_start = EXCLUDED.weekly_start,
                        weekly_completed = EXCLUDED.weekly_completed
                    """;
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                Map<String, Integer> progress = p.getAllProgress();
                Map<String, Boolean> claimed = p.getAllClaimed();
                ps.setString(1, p.getPlayerId().toString());
                ps.setString(2, gson.toJson(progress));
                ps.setString(3, gson.toJson(claimed));
                ps.setString(4, LocalDate.now().toString());
                ps.setInt(5, p.getDailyCompletedCount());
                ps.setString(6, LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString());
                ps.setInt(7, p.getWeeklyCompletedCount());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("saveProgress error: " + e.getMessage());
        }
    }

    // API d’incrément robustes
    public void addProgress(Player player, QuestType type, int amount) {
        if (amount <= 0) return;
        UUID id = player.getUniqueId();
        PlayerQuestProgress p = getProgress(id);
        p.resetDailyIfNeeded();
        p.resetWeeklyIfNeeded();

        for (QuestDefinition q : quests.values()) {
            if (q.getType() != type) continue;
            String qid = q.getId();
            int current = p.get(qid);
            if (current >= q.getTarget()) continue; // déjà suffisant
            p.set(qid, Math.min(q.getTarget(), current + amount));
        }
        cache.put(id, p);
    }

    public boolean claim(Player player, String questId) {
        QuestDefinition q = quests.get(questId);
        if (q == null) return false;
        PlayerQuestProgress p = getProgress(player.getUniqueId());
        if (p.get(questId) < q.getTarget() || p.isClaimed(questId)) return false;
        q.getRewards().grant(plugin, player);
        p.setClaimed(questId);
        if (q.getCategory() == QuestCategory.DAILY) p.incDailyCompleted();
        if (q.getCategory() == QuestCategory.WEEKLY) p.incWeeklyCompleted();
        cache.put(player.getUniqueId(), p);
        // Sauvegarde immédiate après claim pour résilience (crash/déconnexion)
        saveProgress(p);
        return true;
    }

    public void grantAllDoneBonus(Player player, QuestCategory category) {
        // Bonus: 1 clé Rare si daily, 1 clé Légendaire si weekly
        String keyType = category == QuestCategory.DAILY ? "Rare" : "Légendaire";
        var key = plugin.getEnchantmentManager().createKey(keyType);
        boolean added = plugin.getContainerManager().addItemToContainers(player, key);
        if (!added) player.getInventory().addItem(key);
    }
}


