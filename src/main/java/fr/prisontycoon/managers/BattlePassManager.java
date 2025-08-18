package fr.prisontycoon.managers;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.api.PrisonTycoonAPI;
import fr.prisontycoon.quests.QuestRewards;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.quests.QuestManager;
import fr.prisontycoon.quests.PlayerQuestProgress;
import org.bukkit.entity.Player;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Gestionnaire du Pass de Combat amélioré
 * - Utilise QuestManager pour les données (cohérence)
 * - Corrections des bugs de StringIndexOutOfBoundsException
 * - Gestion robuste des saisons et récompenses
 * - Quêtes de temps de jeu intégrées
 */
public class BattlePassManager {

    public static final int POINTS_PER_TIER = 50;
    public static final int MAX_TIER = 50;
    public static final int SEASON_DURATION_DAYS = 30;

    private final PrisonTycoon plugin;


    public BattlePassManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    // ============================================================================================
    // TRACKING DU TEMPS DE JEU
    // ============================================================================================


    // ============================================================================================
    // GESTION DES SAISONS
    // ============================================================================================

    public String getCurrentSeasonId() {
        long override = plugin.getConfig().getLong("battlepass.overrideStart", 0L);
        long now = System.currentTimeMillis();

        if (override > 0 && now < override + SEASON_DURATION_DAYS * 24L * 3600L * 1000L) {
            return "S" + (override / 1000L); // Identifiant basé sur timestamp de démarrage
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

        plugin.getLogger().info("Nouvelle saison du Battle Pass démarrée: " + getCurrentSeasonId());
    }

    // ============================================================================================
    // DONNÉES JOUEUR (via QuestManager)
    // ============================================================================================

    public PlayerPassData getPlayerData(UUID playerId) {
        // Utiliser QuestManager pour récupérer les données BP
        QuestManager questManager = plugin.getQuestManager();
        PlayerQuestProgress progress = questManager.getProgress(playerId);

        // Extraire les données BP du PlayerQuestProgress
        // Ces données sont stockées dans player_quests par QuestManager
        return extractBattlePassData(progress, playerId);
    }

    private PlayerPassData extractBattlePassData(PlayerQuestProgress progress, UUID playerId) {
        String currentSeason = getCurrentSeasonId();
        String storedSeason = progress.getBattlePassSeasonId();
        int points = progress.getBattlePassPoints();
        boolean premium = progress.isBattlePassPremium();
        Set<Integer> claimedFree = progress.getBattlePassClaimedFree();
        Set<Integer> claimedPremium = progress.getBattlePassClaimedPremium();

        // Reset si la saison a changé
        if (storedSeason == null || !storedSeason.equals(currentSeason)) {
            points = 0;
            premium = false; // ou conserver selon choix design
            claimedFree = new HashSet<>();
            claimedPremium = new HashSet<>();

            progress.setBattlePassSeasonId(currentSeason);
            progress.setBattlePassPoints(0);
            progress.setBattlePassPremium(false);
            progress.setBattlePassClaimedFree(claimedFree);
            progress.setBattlePassClaimedPremium(claimedPremium);
            // Sauvegarde immédiate
            plugin.getQuestManager().saveProgress(progress);
        }

        return new PlayerPassData(points, premium, claimedFree, claimedPremium);
    }

    private void updatePlayerData(UUID playerId, PlayerPassData data) {
        QuestManager questManager = plugin.getQuestManager();
        PlayerQuestProgress progress = questManager.getProgress(playerId);
        progress.setBattlePassSeasonId(getCurrentSeasonId());
        progress.setBattlePassPoints(data.points());
        progress.setBattlePassPremium(data.premium());
        progress.setBattlePassClaimedFree(data.claimedFree());
        progress.setBattlePassClaimedPremium(data.claimedPremium());
        questManager.saveProgress(progress);
    }

    // ============================================================================================
    // API PUBLIQUE
    // ============================================================================================

    public int getPoints(UUID playerId) {
        return getPlayerData(playerId).points();
    }

    public int getTier(UUID playerId) {
        int points = getPoints(playerId);
        return Math.min(MAX_TIER + Math.max(0, (points - MAX_TIER * POINTS_PER_TIER) / POINTS_PER_TIER),
                points / POINTS_PER_TIER);
    }

    public int getProgressInTier(UUID playerId) {
        int points = getPoints(playerId);
        int currentTier = getTier(playerId);

        if (currentTier >= MAX_TIER) {
            // Au-delà du palier 50, progression pour le prochain bonus
            return (points - MAX_TIER * POINTS_PER_TIER) % POINTS_PER_TIER;
        }

        return points % POINTS_PER_TIER;
    }

    public boolean hasPremium(UUID playerId) {
        return getPlayerData(playerId).premium();
    }

    public void addPoints(Player player, int points) {
        if (points <= 0) return;

        UUID uuid = player.getUniqueId();
        PlayerPassData data = getPlayerData(uuid);
        int newPoints = data.points() + points;
        int oldTier = getTier(uuid);

        PlayerPassData newData = new PlayerPassData(
                newPoints, data.premium(), data.claimedFree(), data.claimedPremium()
        );

        updatePlayerData(uuid, newData);

        int newTier = newPoints / POINTS_PER_TIER;
        if (newTier > oldTier) {
            player.sendMessage("§6⚔ §ePalier " + newTier + " débloqué ! §7(§a+" + points + " XP§7)");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);

            // Notification pour les récompenses après palier 50
            if (newTier > MAX_TIER) {
                int extraTiers = newTier - MAX_TIER;
                player.sendMessage("§6✨ Bonus: §e" + extraTiers + " Clé(s) Rare(s) §6à récupérer !");
            }
        } else {
            player.sendMessage("§e+§a" + points + " XP §7Battle Pass §8(§e" +
                    getProgressInTier(uuid) + "§7/§e" + POINTS_PER_TIER + "§8)");
        }
    }

    public boolean claimFree(Player player, int tier) {
        UUID uuid = player.getUniqueId();
        PlayerPassData data = getPlayerData(uuid);

        if (getTier(uuid) < tier || data.claimedFree().contains(tier)) {
            return false;
        }

        // Donner la récompense
        TierRewards rewards = getRewardsForTier(tier);
        rewards.free().grant(plugin, player);

        // Marquer comme réclamé
        Set<Integer> newClaimedFree = new HashSet<>(data.claimedFree());
        newClaimedFree.add(tier);

        PlayerPassData newData = new PlayerPassData(
                data.points(), data.premium(), newClaimedFree, data.claimedPremium()
        );

        updatePlayerData(uuid, newData);
        return true;
    }

    public boolean claimPremium(Player player, int tier) {
        UUID uuid = player.getUniqueId();
        PlayerPassData data = getPlayerData(uuid);

        if (!data.premium() || getTier(uuid) < tier || data.claimedPremium().contains(tier)) {
            return false;
        }

        // Donner la récompense premium
        TierRewards rewards = getRewardsForTier(tier);
        rewards.premium().grant(plugin, player);

        // Marquer comme réclamé
        Set<Integer> newClaimedPremium = new HashSet<>(data.claimedPremium());
        newClaimedPremium.add(tier);

        PlayerPassData newData = new PlayerPassData(
                data.points(), data.premium(), data.claimedFree(), newClaimedPremium
        );

        updatePlayerData(uuid, newData);
        return true;
    }

    public boolean purchasePremium(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerPassData data = getPlayerData(uuid);

        if (data.premium()) {
            return false; // Déjà premium
        }

        // Calculer le prix (réduction VIP)
        boolean isVip = player.hasPermission("prisontycoon.vip");
        long basePrice = 5000;
        long price = isVip ? (long)(basePrice * 0.7) : basePrice;

        // Vérifier les fonds via l'API
        PrisonTycoonAPI api = PrisonTycoonAPI.getInstance();
        if (api == null || !api.hasBeacons(uuid, price)) {
            return false;
        }

        // Déduire les beacons
        if (!api.removeBeacons(uuid, price)) {
            return false;
        }

        // Activer le premium
        PlayerPassData newData = new PlayerPassData(
                data.points(), true, data.claimedFree(), data.claimedPremium()
        );

        updatePlayerData(uuid, newData);

        player.sendMessage("§6💎 Pass Premium activé ! §7(§c-" + price + " Beacons§7)");
        return true;
    }

    // ============================================================================================
    // RÉCOMPENSES PAR PALIER
    // ============================================================================================

    public TierRewards getRewardsForTier(int tier) {
        if (tier > MAX_TIER) {
            // Paliers au-delà de 50 = 1 Clé Rare
            QuestRewards rareTierReward = QuestRewards.builder()
                    .keys(Map.of("rare", 1))
                    .build();
            return new TierRewards(rareTierReward, rareTierReward);
        }

        return switch (tier) {
            case 1 -> new TierRewards(
                    QuestRewards.builder().beacons(200).build(),
                    QuestRewards.builder().beacons(500).tokens(100).build()
            );
            case 2 -> new TierRewards(
                    QuestRewards.builder().tokens(100).build(),
                    QuestRewards.builder().tokens(300).jobXp(500).build()
            );
            case 3 -> new TierRewards(
                    QuestRewards.builder().jobXp(300).build(),
                    QuestRewards.builder().beacons(800).essence(2).build()
            );
            case 5 -> new TierRewards(
                    QuestRewards.builder().keys(Map.of("common", 1)).build(),
                    QuestRewards.builder().keys(Map.of("rare", 1)).beacons(1000).build()
            );
            case 10 -> new TierRewards(
                    QuestRewards.builder().beacons(1000).tokens(500).build(),
                    QuestRewards.builder().beacons(2500).keys(Map.of("epic", 1)).build()
            );
            case 15 -> new TierRewards(
                    QuestRewards.builder().essence(3).build(),
                    QuestRewards.builder().essence(8).keys(Map.of("rare", 2)).build()
            );
            case 20 -> new TierRewards(
                    QuestRewards.builder().keys(Map.of("rare", 1)).beacons(1500).build(),
                    QuestRewards.builder().keys(Map.of("legendary", 1)).beacons(3000).build()
            );
            case 25 -> new TierRewards(
                    QuestRewards.builder().beacons(2000).tokens(1000).build(),
                    QuestRewards.builder().beacons(5000).essence(10).keys(Map.of("epic", 2)).build()
            );
            case 30 -> new TierRewards(
                    QuestRewards.builder().essence(5).keys(Map.of("common", 3)).build(),
                    QuestRewards.builder().essence(15).keys(Map.of("rare", 3)).boost(BoostType.EXPERIENCE_BOOST, 60, 50.0).build()
            );
            case 35 -> new TierRewards(
                    QuestRewards.builder().keys(Map.of("epic", 1)).beacons(3000).build(),
                    QuestRewards.builder().keys(Map.of("legendary", 1)).beacons(6000).essence(20).build()
            );
            case 40 -> new TierRewards(
                    QuestRewards.builder().beacons(4000).tokens(2000).essence(8).build(),
                    QuestRewards.builder().beacons(8000).tokens(4000).keys(Map.of("epic", 3)).build()
            );
            case 45 -> new TierRewards(
                    QuestRewards.builder().keys(Map.of("rare", 2)).essence(10).build(),
                    QuestRewards.builder().keys(Map.of("legendary", 2)).essence(25).boost(BoostType.BEACON_BOOST, 120, 50.0).build()
            );
            case 50 -> new TierRewards(
                    QuestRewards.builder().keys(Map.of("legendary", 1)).beacons(5000).essence(15).build(),
                    QuestRewards.builder().keys(Map.of("legendary", 3)).beacons(10000).essence(50).build()
            );
            default -> new TierRewards(
                    QuestRewards.builder().beacons(100 + tier * 20).build(),
                    QuestRewards.builder().beacons(200 + tier * 40).tokens(tier * 10).build()
            );
        };
    }

    // ============================================================================================
    // RECORDS POUR LES DONNÉES
    // ============================================================================================

    public record PlayerPassData(int points, boolean premium, Set<Integer> claimedFree, Set<Integer> claimedPremium) {}

    public record TierRewards(QuestRewards free, QuestRewards premium) {}

    // ============================================================================================
    // COMMANDES ADMIN
    // ============================================================================================

    public void addPointsAdmin(UUID playerId, int points) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            addPoints(player, points);
        } else {
            // Joueur hors ligne, mise à jour directe
            PlayerPassData data = getPlayerData(playerId);
            PlayerPassData newData = new PlayerPassData(
                    data.points() + points, data.premium(), data.claimedFree(), data.claimedPremium()
            );
            updatePlayerData(playerId, newData);
        }
    }

    public void setPremiumAdmin(UUID playerId, boolean premium) {
        PlayerPassData data = getPlayerData(playerId);
        PlayerPassData newData = new PlayerPassData(
                data.points(), premium, data.claimedFree(), data.claimedPremium()
        );
        updatePlayerData(playerId, newData);
    }

    public void resetPlayerData(UUID playerId) {
        PlayerPassData newData = new PlayerPassData(0, false, new HashSet<>(), new HashSet<>());
        updatePlayerData(playerId, newData);
        plugin.getLogger().info("Données Battle Pass réinitialisées pour " + playerId);
    }
}