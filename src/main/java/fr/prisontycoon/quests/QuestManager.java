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
                    weekly_completed INT,
                    bp_season_id VARCHAR(32),
                    bp_points INT DEFAULT 0,
                    bp_premium BOOLEAN DEFAULT FALSE,
                    bp_claimed_free TEXT DEFAULT '[]',
                    bp_claimed_premium TEXT DEFAULT '[]'
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
        ConfigurationSection pass = questsConfig.getConfigurationSection("pass");
        if (daily != null) {
            loadCategory(daily, QuestCategory.DAILY);
        }
        if (weekly != null) {
            loadCategory(weekly, QuestCategory.WEEKLY);
        }
        if (pass != null) {
            loadCategory(pass, QuestCategory.PASS);
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
            int battlePassPoints = q.getInt("bp_points", category == QuestCategory.DAILY ? 20 : (category == QuestCategory.WEEKLY ? 200 : 0));
            quests.put(id, new QuestDefinition(id, category, type, target, params, rewards, battlePassPoints));
        }
    }

    public Collection<QuestDefinition> getAllQuests() {
        return quests.values();
    }

    public List<QuestDefinition> getQuestsByCategory(QuestCategory cat) {
        List<QuestDefinition> list = new ArrayList<>();
        for (var q : quests.values()) if (q.category() == cat) list.add(q);
        return list;
    }

    public QuestDefinition get(String id) {
        return quests.get(id);
    }

    public PlayerQuestProgress getProgress(UUID playerId) {
        PlayerQuestProgress p = cache.computeIfAbsent(playerId, this::loadProgress);
        boolean changed = false;
        // Garantit qu'un set actif est présent (résilience si passage de jour/semaine)
        changed |= ensureActiveQuestsForCategory(p, QuestCategory.DAILY, 7);
        changed |= ensureActiveQuestsForCategory(p, QuestCategory.WEEKLY, 10);
        if (changed) {
            saveProgress(p);
        }
        return p;
    }

    /**
     * Variante interne qui ne déclenche jamais de (re-)sélection.
     * Utilisée pour les ajouts de progression afin de ne pas commencer une quête automatiquement.
     */
    private PlayerQuestProgress getProgressNoAutoselect(UUID playerId) {
        PlayerQuestProgress p = cache.get(playerId);
        if (p == null) {
            p = loadProgress(playerId);
            cache.put(playerId, p);
        }
        return p;
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
                    // Charger les champs Battle Pass
                    String bpSeasonId = rs.getString("bp_season_id");
                    int bpPoints = rs.getInt("bp_points");
                    boolean bpPremium = rs.getBoolean("bp_premium");
                    try {
                        java.lang.reflect.Type intSetType = new TypeToken<java.util.Set<Integer>>() {
                        }.getType();
                        String bpClaimedFreeJson = rs.getString("bp_claimed_free");
                        String bpClaimedPremiumJson = rs.getString("bp_claimed_premium");
                        java.util.Set<Integer> bpClaimedFree = gson.fromJson(bpClaimedFreeJson, intSetType);
                        java.util.Set<Integer> bpClaimedPremium = gson.fromJson(bpClaimedPremiumJson, intSetType);
                        p.setBattlePassSeasonId(bpSeasonId);
                        p.setBattlePassPoints(bpPoints);
                        p.setBattlePassPremium(bpPremium);
                        if (bpClaimedFree != null) p.setBattlePassClaimedFree(bpClaimedFree);
                        if (bpClaimedPremium != null) p.setBattlePassClaimedPremium(bpClaimedPremium);
                    } catch (Exception ignored) {
                    }
                    String daily = rs.getString("daily_date");
                    boolean needDailySelection = false;
                    if (daily != null) {
                        // Reset et re-sélection si date différente
                        if (!LocalDate.parse(daily).equals(LocalDate.now())) {
                            p.resetDailyIfNeeded();
                            needDailySelection = true;
                        }
                    } else {
                        // Première initialisation
                        needDailySelection = true;
                    }
                    String start = rs.getString("weekly_start");
                    boolean needWeeklySelection = false;
                    if (start != null) {
                        if (!LocalDate.parse(start).equals(LocalDate.now().with(java.time.DayOfWeek.MONDAY))) {
                            p.resetWeeklyIfNeeded();
                            needWeeklySelection = true;
                        }
                    } else {
                        needWeeklySelection = true;
                    }

                    boolean changed = false;
                    if (needDailySelection) {
                        changed |= selectActiveQuestsForCategory(p, QuestCategory.DAILY, 7);
                    }
                    if (needWeeklySelection) {
                        changed |= selectActiveQuestsForCategory(p, QuestCategory.WEEKLY, 10);
                    }
                    if (changed) {
                        // Persiste immédiatement la sélection (même à 0 de progression)
                        saveProgress(p);
                    }
                    return p;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("loadProgress error: " + e.getMessage());
        }
        // Aucune ligne trouvée: créer progression et sélectionner des quêtes
        PlayerQuestProgress p = new PlayerQuestProgress(playerId);
        boolean changed = false;
        changed |= selectActiveQuestsForCategory(p, QuestCategory.DAILY, 7);
        changed |= selectActiveQuestsForCategory(p, QuestCategory.WEEKLY, 10);
        if (changed) {
            saveProgress(p);
        }
        return p;
    }

    public void saveProgress(PlayerQuestProgress p) {
        try (Connection c = plugin.getDatabaseManager().getConnection()) {
            String sql = """
                    INSERT INTO player_quests(uuid, progress_json, claimed_json, daily_date, daily_completed, weekly_start, weekly_completed,
                                              bp_season_id, bp_points, bp_premium, bp_claimed_free, bp_claimed_premium)
                    VALUES(?,?,?,?,?,?,?, ?,?,?,?,?)
                    ON CONFLICT (uuid) DO UPDATE SET
                        progress_json = EXCLUDED.progress_json,
                        claimed_json = EXCLUDED.claimed_json,
                        daily_date = EXCLUDED.daily_date,
                        daily_completed = EXCLUDED.daily_completed,
                        weekly_start = EXCLUDED.weekly_start,
                        weekly_completed = EXCLUDED.weekly_completed,
                        bp_season_id = EXCLUDED.bp_season_id,
                        bp_points = EXCLUDED.bp_points,
                        bp_premium = EXCLUDED.bp_premium,
                        bp_claimed_free = EXCLUDED.bp_claimed_free,
                        bp_claimed_premium = EXCLUDED.bp_claimed_premium
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
                // Champs BP
                ps.setString(8, p.getBattlePassSeasonId());
                ps.setInt(9, p.getBattlePassPoints());
                ps.setBoolean(10, p.isBattlePassPremium());
                ps.setString(11, gson.toJson(p.getBattlePassClaimedFree()));
                ps.setString(12, gson.toJson(p.getBattlePassClaimedPremium()));
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
        PlayerQuestProgress p = getProgressNoAutoselect(id);
        p.resetDailyIfNeeded();
        p.resetWeeklyIfNeeded();

        for (QuestDefinition q : quests.values()) {
            if (q.type() != type) continue;
            String qid = q.id();
            // Restreindre aux quêtes actives (présentes dans la map de progression)
            if (!p.getAllProgress().containsKey(qid)) continue;
            int current = p.get(qid);
            if (current >= q.target()) continue; // déjà suffisant
            p.set(qid, Math.min(q.target(), current + amount));
        }
        cache.put(id, p);
    }

    public boolean claim(Player player, String questId) {
        QuestDefinition q = quests.get(questId);
        if (q == null) return false;
        PlayerQuestProgress p = getProgress(player.getUniqueId());
        if (p.get(questId) < q.target() || p.isClaimed(questId)) return false;
        q.rewards().grant(plugin, player);
        p.setClaimed(questId);
        if (q.category() == QuestCategory.DAILY) p.incDailyCompleted();
        if (q.category() == QuestCategory.WEEKLY) p.incWeeklyCompleted();
        cache.put(player.getUniqueId(), p);
        // Sauvegarde immédiate après claim pour résilience (crash/déconnexion)
        saveProgress(p);
        // Ajouter des points de Pass
        int bp = q.battlePassPoints();
        if (bp > 0) {
            plugin.getBattlePassManager().addPoints(player, bp);
        }
        return true;
    }

    public void grantAllDoneBonus(Player player, QuestCategory category) {
        // Bonus: 1 clé Rare si daily, 1 clé Légendaire si weekly
        String keyType = category == QuestCategory.DAILY ? "Rare" : "Légendaire";
        var key = plugin.getEnchantmentManager().createKey(keyType);
        boolean added = plugin.getContainerManager().addItemToContainers(player, key);
        if (!added) player.getInventory().addItem(key);
    }

    /**
     * Sélectionne aléatoirement un sous-ensemble de quêtes pour une catégorie,
     * les enregistre dans la progression du joueur (même à 0),
     * et purge les autres quêtes de cette catégorie de la progression.
     */
    private boolean selectActiveQuestsForCategory(PlayerQuestProgress progress, QuestCategory category, int desiredCount) {
        // Récupère toutes les quêtes de la catégorie
        List<QuestDefinition> all = getQuestsByCategory(category);
        if (all.isEmpty()) return false;

        // Mélange et sélection
        List<QuestDefinition> pool = new ArrayList<>(all);
        Collections.shuffle(pool, new Random());
        int count = Math.min(desiredCount, pool.size());
        Set<String> selected = new HashSet<>();
        for (int i = 0; i < count; i++) {
            selected.add(pool.get(i).id());
        }

        // Supprimer de la progression toutes les quêtes de cette catégorie qui ne sont pas sélectionnées
        // et s'assurer que les sélectionnées existent au moins à 0
        // Construire l'ensemble des IDs de cette catégorie
        Set<String> categoryIds = new HashSet<>();
        for (QuestDefinition q : all) categoryIds.add(q.id());

        boolean changed = false;
        // Purge
        Map<String, Integer> currentProgress = progress.getAllProgress();
        for (String qid : categoryIds) {
            if (!selected.contains(qid) && currentProgress.containsKey(qid)) {
                progress.remove(qid);
                changed = true;
            }
        }

        // Ensure entries for selected
        for (String qid : selected) {
            Integer before = progress.getAllProgress().get(qid);
            progress.ensureEntry(qid);
            // Réinitialise l'état réclamé au cas où
            progress.clearClaimed(qid);
            if (before == null) changed = true;
        }
        return changed;
    }

    /**
     * Garantit un nombre minimum de quêtes actives pour la catégorie.
     * Retourne true si une sélection a été (ré)effectuée.
     */
    private boolean ensureActiveQuestsForCategory(PlayerQuestProgress progress, QuestCategory category, int desiredCount) {
        Set<String> categoryIds = new HashSet<>();
        for (QuestDefinition q : getQuestsByCategory(category)) categoryIds.add(q.id());
        int active = 0;
        for (String qid : progress.getAllProgress().keySet()) {
            if (categoryIds.contains(qid)) active++;
        }
        if (active < Math.min(desiredCount, categoryIds.size())) {
            return selectActiveQuestsForCategory(progress, category, desiredCount);
        }
        return false;
    }

    /**
     * Liste des quêtes actives d'un joueur pour une catégorie (celles présentes dans progress_json)
     */
    public List<QuestDefinition> getActiveQuestsForPlayer(UUID playerId, QuestCategory category) {
        PlayerQuestProgress p = getProgress(playerId);
        Set<String> activeIds = p.getAllProgress().keySet();
        List<QuestDefinition> result = new ArrayList<>();
        for (QuestDefinition q : getQuestsByCategory(category)) {
            if (activeIds.contains(q.id())) result.add(q);
        }
        // Optionnel: ordre stable
        result.sort(Comparator.comparing(QuestDefinition::id));
        return result;
    }
}


