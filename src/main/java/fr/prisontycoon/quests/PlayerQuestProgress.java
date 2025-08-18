package fr.prisontycoon.quests;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Progression des quêtes pour un joueur.
 */
public class PlayerQuestProgress {
    private final UUID playerId;
    private final Map<String, Integer> progressByQuest = new HashMap<>(); // questId -> progress
    private final Map<String, Boolean> claimedByQuest = new HashMap<>();  // questId -> claimed

    private LocalDate dailyDate;   // reset quotidien
    private int dailyCompletedCount;

    private LocalDate weeklyWeekStart; // reset hebdo
    private int weeklyCompletedCount;

    // Données Battle Pass (BP)
    private String battlePassSeasonId;
    private int battlePassPoints;
    private boolean battlePassPremium;
    private final Set<Integer> battlePassClaimedFree = new HashSet<>();
    private final Set<Integer> battlePassClaimedPremium = new HashSet<>();

    public PlayerQuestProgress(UUID playerId) {
        this.playerId = playerId;
        this.dailyDate = LocalDate.now();
        this.weeklyWeekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int get(String questId) {
        return progressByQuest.getOrDefault(questId, 0);
    }

    public void add(String questId, int amount) {
        progressByQuest.merge(questId, amount, Integer::sum);
    }

    public void set(String questId, int amount) {
        progressByQuest.put(questId, Math.max(0, amount));
    }

	/**
	 * S'assure qu'une entrée existe pour cette quête (par exemple pour la persistance à 0)
	 */
	public void ensureEntry(String questId) {
		progressByQuest.putIfAbsent(questId, 0);
	}

	/**
	 * Supprime toute trace d'une quête (progression et statut réclamé)
	 */
	public void remove(String questId) {
		progressByQuest.remove(questId);
		claimedByQuest.remove(questId);
	}

    public boolean isClaimed(String questId) {
        return claimedByQuest.getOrDefault(questId, false);
    }

    public void setClaimed(String questId) {
        claimedByQuest.put(questId, true);
    }

	/**
	 * Efface l'état "réclamé" d'une quête
	 */
	public void clearClaimed(String questId) {
		claimedByQuest.remove(questId);
	}

    public void resetDailyIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(dailyDate)) {
            dailyDate = today;
            dailyCompletedCount = 0;
            // On ne supprime pas la map complète pour permettre l'affichage historique, mais on peut la nettoyer si besoin.
        }
    }

    public void resetWeeklyIfNeeded() {
        LocalDate currentWeekStart = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        if (!currentWeekStart.equals(weeklyWeekStart)) {
            weeklyWeekStart = currentWeekStart;
            weeklyCompletedCount = 0;
        }
    }

    public void incDailyCompleted() {
        dailyCompletedCount++;
    }

    public void incWeeklyCompleted() {
        weeklyCompletedCount++;
    }

    public int getDailyCompletedCount() {
        return dailyCompletedCount;
    }

    public int getWeeklyCompletedCount() {
        return weeklyCompletedCount;
    }

    /**
     * Retourne une copie immuable de la progression par quête pour persistance
     */
    public Map<String, Integer> getAllProgress() {
        return new HashMap<>(progressByQuest);
    }

    /**
     * Retourne une copie immuable des quêtes réclamées pour persistance
     */
    public Map<String, Boolean> getAllClaimed() {
        return new HashMap<>(claimedByQuest);
    }

    // Accesseurs Battle Pass (BP)
    public String getBattlePassSeasonId() {
        return battlePassSeasonId;
    }

    public void setBattlePassSeasonId(String seasonId) {
        this.battlePassSeasonId = seasonId;
    }

    public int getBattlePassPoints() {
        return battlePassPoints;
    }

    public void setBattlePassPoints(int points) {
        this.battlePassPoints = Math.max(0, points);
    }

    public boolean isBattlePassPremium() {
        return battlePassPremium;
    }

    public void setBattlePassPremium(boolean premium) {
        this.battlePassPremium = premium;
    }

    public Set<Integer> getBattlePassClaimedFree() {
        return new HashSet<>(battlePassClaimedFree);
    }

    public void setBattlePassClaimedFree(Set<Integer> tiers) {
        battlePassClaimedFree.clear();
        if (tiers != null) battlePassClaimedFree.addAll(tiers);
    }

    public Set<Integer> getBattlePassClaimedPremium() {
        return new HashSet<>(battlePassClaimedPremium);
    }

    public void setBattlePassClaimedPremium(Set<Integer> tiers) {
        battlePassClaimedPremium.clear();
        if (tiers != null) battlePassClaimedPremium.addAll(tiers);
    }
}


