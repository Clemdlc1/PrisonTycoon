package fr.prisontycoon.data;

import fr.prisontycoon.gangs.GangBoostType;
import fr.prisontycoon.gangs.GangRole;
import org.bukkit.Bukkit;
import org.bukkit.block.banner.Pattern;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Représente un gang avec tous ses membres, données et fonctionnalités
 */
public class Gang {

    private final String id;
    private final Map<UUID, GangRole> members;
    private final long creationDate;
    private final Map<String, Integer> talents;
    // Système de boosts temporaires
    private final Map<GangBoostType, GangBoostData> activeBoosts;
    private final Map<GangBoostType, Long> lastBoostActivation;
    private final Map<GangBoostType, Integer> dailyBoostCount;
    private String name;
    private String tag;
    private UUID leader;
    private int level;
    private long bankBalance;
    private String description;
    private List<Pattern> bannerPatterns;
    private long lastDailyReset;

    public Gang(String id, String name, String tag, UUID leader, long creationDate) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.leader = leader;
        this.creationDate = creationDate;
        this.members = new ConcurrentHashMap<>();
        this.level = 1;
        this.bankBalance = 0L;
        this.talents = new ConcurrentHashMap<>();
        this.activeBoosts = new ConcurrentHashMap<>();
        this.lastBoostActivation = new ConcurrentHashMap<>();
        this.dailyBoostCount = new ConcurrentHashMap<>();
        this.lastDailyReset = System.currentTimeMillis();

        // Le leader est automatiquement ajouté comme chef
        this.members.put(leader, GangRole.CHEF);
    }

    // Gestion des membres

    /**
     * Ajoute un membre au gang
     */
    public void addMember(UUID playerId, GangRole role) {
        members.put(playerId, role);
    }

    /**
     * Retire un membre du gang
     */
    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    /**
     * Obtient le rôle d'un membre
     */
    public GangRole getMemberRole(UUID playerId) {
        return members.get(playerId);
    }

    /**
     * Définit le rôle d'un membre
     */
    public void setMemberRole(UUID playerId, GangRole role) {
        if (members.containsKey(playerId)) {
            members.put(playerId, role);
        }
    }

    /**
     * Vérifie si un joueur est membre du gang
     */
    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    /**
     * Obtient le nombre maximum de membres selon le niveau et les talents
     */
    public int getMaxMembers() {
        int baseMembers = 3; // Chef + 2 membres de base

        // Bonus par niveau
        int levelBonus = 0;
        if (level >= 2) levelBonus++; // +1 au niveau 2 (total 4)
        if (level >= 3) levelBonus++; // +1 au niveau 3 (total 5)
        if (level >= 5) levelBonus++; // +1 au niveau 5 (total 6)
        if (level >= 6) levelBonus++; // +1 au niveau 6 (total 7)
        if (level >= 8) levelBonus++; // +1 au niveau 8 (total 8)
        if (level >= 9) levelBonus++; // +1 au niveau 9 (total 9)
        if (level >= 10) levelBonus++; // +1 au niveau 10 (total 10)

        // Bonus des talents Gang Collectif (max +5)
        int talentBonus = 0;
        for (int i = 1; i <= 5; i++) {
            if (talents.containsKey("gang_collectif_" + i)) {
                talentBonus += i;
            }
        }

        return baseMembers + levelBonus + Math.min(talentBonus, 5);
    }

    // Gestion des boosts temporaires

    /**
     * Vérifie si un boost peut être activé (cooldown et limite quotidienne)
     */
    public boolean canActivateBoost(GangBoostType boostType) {
        resetDailyCountIfNeeded();

        // Vérifier le cooldown (1 heure)
        Long lastActivation = lastBoostActivation.get(boostType);
        if (lastActivation != null) {
            long cooldownEnd = lastActivation + (60 * 60 * 1000); // 1 heure
            if (System.currentTimeMillis() < cooldownEnd) {
                return false;
            }
        }

        // Vérifier la limite quotidienne (3 par type par jour)
        int todayCount = dailyBoostCount.getOrDefault(boostType, 0);
        return todayCount < 3;
    }

    /**
     * Active un boost temporaire
     */
    public void activateBoost(GangBoostType boostType, int tier, UUID activatorId) {
        resetDailyCountIfNeeded();

        // Calculer la durée selon le tier
        int[] durations = {30, 60, 180}; // minutes
        long durationMs = durations[tier - 1] * 60 * 1000;
        long expirationTime = System.currentTimeMillis() + durationMs;

        // Enregistrer le boost
        double[] multipliers = {1.5, 2.0, 3.0};
        GangBoostData boostData = new GangBoostData(
                boostType,
                multipliers[tier - 1],
                expirationTime,
                activatorId
        );

        activeBoosts.put(boostType, boostData);
        lastBoostActivation.put(boostType, System.currentTimeMillis());
        dailyBoostCount.put(boostType, dailyBoostCount.getOrDefault(boostType, 0) + 1);
    }

    /**
     * Vérifie si un boost est actif
     */
    public boolean hasActiveBoost(GangBoostType boostType) {
        GangBoostData boostData = activeBoosts.get(boostType);
        if (boostData == null) return false;

        if (System.currentTimeMillis() > boostData.getExpirationTime()) {
            activeBoosts.remove(boostType);
            return false;
        }

        return true;
    }

    /**
     * Obtient le multiplicateur d'un boost actif
     */
    public double getBoostMultiplier(GangBoostType boostType) {
        if (!hasActiveBoost(boostType)) return 1.0;
        return activeBoosts.get(boostType).getMultiplier();
    }

    /**
     * Remet à zéro les compteurs quotidiens si nécessaire
     */
    private void resetDailyCountIfNeeded() {
        long now = System.currentTimeMillis();
        long daysSinceReset = (now - lastDailyReset) / (24 * 60 * 60 * 1000);

        if (daysSinceReset >= 1) {
            dailyBoostCount.clear();
            lastDailyReset = now;
        }
    }

    // Communication

    /**
     * Diffuse un message à tous les membres en ligne
     */
    public void broadcast(String message, Player sender) {
        String prefix = "§7[§6" + tag + "§7] ";
        String fullMessage = prefix + message;

        for (UUID memberId : members.keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline() && !member.equals(sender)) {
                member.sendMessage(fullMessage);
            }
        }
    }

    /**
     * Envoie un message dans le chat du gang
     */
    public void sendChatMessage(Player sender, String message) {
        GangRole senderRole = getMemberRole(sender.getUniqueId());
        String rolePrefix = senderRole.getChatPrefix();
        String prefix = "§7[§6" + tag + "§7] ";
        String fullMessage = prefix + rolePrefix + sender.getName() + "§7: §f" + message;

        for (UUID memberId : members.keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                member.sendMessage(fullMessage);
            }
        }
    }

    // Getters et Setters

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Map<UUID, GangRole> getMembers() {
        return new HashMap<>(members);
    }

    public void setMembers(Map<UUID, GangRole> members) {
        this.members.clear();
        this.members.putAll(members);
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public long getBankBalance() {
        return bankBalance;
    }

    public void setBankBalance(long bankBalance) {
        this.bankBalance = bankBalance;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Integer> getTalents() {
        return new HashMap<>(talents);
    }

    public void setTalents(Map<String, Integer> talents) {
        this.talents.clear();
        this.talents.putAll(talents);
    }

    public List<Pattern> getBannerPatterns() {
        return bannerPatterns != null ? new ArrayList<>(bannerPatterns) : null;
    }

    public void setBannerPatterns(List<Pattern> bannerPatterns) {
        this.bannerPatterns = bannerPatterns != null ? new ArrayList<>(bannerPatterns) : null;
    }

    /**
     * Classe interne pour représenter les données d'un boost actif
     */
    public static class GangBoostData {
        private final GangBoostType type;
        private final double multiplier;
        private final long expirationTime;
        private final UUID activatorId;

        public GangBoostData(GangBoostType type, double multiplier, long expirationTime, UUID activatorId) {
            this.type = type;
            this.multiplier = multiplier;
            this.expirationTime = expirationTime;
            this.activatorId = activatorId;
        }

        public GangBoostType getType() {
            return type;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public long getExpirationTime() {
            return expirationTime;
        }

        public UUID getActivatorId() {
            return activatorId;
        }

        public long getRemainingTime() {
            return Math.max(0, expirationTime - System.currentTimeMillis());
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
}