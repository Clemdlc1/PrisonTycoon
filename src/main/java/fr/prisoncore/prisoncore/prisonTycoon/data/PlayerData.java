package fr.prisoncore.prisoncore.prisonTycoon.data;

import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Données d'un joueur
 * CORRIGÉ : Distinction correcte gains pioche vs autres moyens, tracking séparé dernière minute
 */
public class PlayerData {
    private final UUID playerId;
    private final String playerName;
    // Enchantements de la pioche (nom -> niveau)
    private final Map<String, Integer> enchantmentLevels;
    private final Map<String, String> pickaxeCristals;
    // Auto-amélioration des enchantements
    private final Set<String> autoUpgradeEnabled;
    private final List<AutoUpgradeDetail> lastMinuteAutoUpgradeDetails = new ArrayList<>();
    // Enchantements mobilité désactivés
    private final Set<String> mobilityEnchantmentsDisabled;
    private final Map<Material, Long> blocksMinedByType;
    private final Set<String> minePermissions;
    // Données thread-safe
    private final Object dataLock = new Object();
    private final List<SanctionData> sanctionHistory;
    // Économie TOTALE (toutes sources)
    private long coins;
    private long tokens;
    private long experience;
    private long beacons;
    // Gains SPÉCIFIQUES via pioche (pour statistiques pioche)
    private long coinsViaPickaxe;
    private long tokensViaPickaxe;
    private long experienceViaPickaxe;
    // États temporaires
    private long combustionLevel;
    private long lastCombustionTime;
    private boolean abundanceActive;
    private long abundanceEndTime;
    private long abundanceCooldownEnd;
    private long lastMiningTime;
    private boolean autoRankup = false;
    // CORRIGÉ : Statistiques de minage avec distinction minés/cassés
    private long totalBlocksMined;     // Blocs minés DIRECTEMENT par le joueur
    private long totalBlocksDestroyed; // Blocs minés + cassés (laser/explosion)
    // Statistiques spécialisées
    private long totalGreedTriggers;
    private long totalKeysObtained;
    // NOUVEAU : Gains de la dernière minute SÉPARÉS (toutes sources vs via pioche)
    private long lastMinuteCoins;                    // TOUS les gains coins
    private long lastMinuteTokens;                   // TOUS les gains tokens
    private long lastMinuteExperience;               // TOUS les gains expérience
    private long lastMinuteCoinsViaPickaxe;          // SEULEMENT via pioche
    private long lastMinuteTokensViaPickaxe;         // SEULEMENT via pioche
    private long lastMinuteExperienceViaPickaxe;     // SEULEMENT via pioche
    private int lastMinuteAutoUpgrades;
    private long lastMinuteBlocksMined;
    private long lastMinuteBlocksDestroyed;
    private long lastMinuteGreedTriggers;
    private long lastMinuteKeysObtained;
    private long lastMinuteBlocksAddedToInventory;
    private Set<String> customPermissions; // NOUVEAU: permissions custom du plugin

    public PlayerData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;

        // Initialisation des valeurs par défaut
        this.coins = 0;
        this.tokens = 0;
        this.experience = 0;
        this.beacons = 0;

        this.coinsViaPickaxe = 0;
        this.tokensViaPickaxe = 0;
        this.experienceViaPickaxe = 0;

        this.enchantmentLevels = new ConcurrentHashMap<>();
        this.autoUpgradeEnabled = ConcurrentHashMap.newKeySet();
        this.mobilityEnchantmentsDisabled = ConcurrentHashMap.newKeySet();
        this.blocksMinedByType = new ConcurrentHashMap<>();

        this.combustionLevel = 0;
        this.lastCombustionTime = System.currentTimeMillis();
        this.abundanceActive = false;
        this.abundanceEndTime = 0;

        this.totalBlocksMined = 0;
        this.totalBlocksDestroyed = 0;
        this.totalGreedTriggers = 0;
        this.totalKeysObtained = 0;

        this.minePermissions = ConcurrentHashMap.newKeySet();

        this.pickaxeCristals = new ConcurrentHashMap<>(); // NOUVEAU
        this.sanctionHistory = new ArrayList<>();
        this.customPermissions = new HashSet<>(); // NOUVEAU


        // Reset stats dernière minute
        resetLastMinuteStats();
    }

    // Méthodes économiques - TOTAL (toutes sources)

    public void addCoins(long amount) {
        synchronized (dataLock) {
            this.coins = Math.max(0, this.coins + amount);
            this.lastMinuteCoins += Math.max(0, amount);
        }
    }

    public void addTokens(long amount) {
        synchronized (dataLock) {
            this.tokens = Math.max(0, this.tokens + amount);
            this.lastMinuteTokens += Math.max(0, amount);
        }
    }

    public void addExperience(long amount) {
        synchronized (dataLock) {
            this.experience = Math.max(0, this.experience + amount);
            this.lastMinuteExperience += Math.max(0, amount);
        }
    }

    public void addBeacons(long amount) {
        synchronized (dataLock) {
            this.beacons = Math.max(0, this.beacons + amount);
        }
    }

    // CORRIGÉ: Méthodes spécifiques VIA PIOCHE avec tracking séparé

    public void addCoinsViaPickaxe(long amount) {
        synchronized (dataLock) {
            this.coins = Math.max(0, this.coins + amount);
            this.coinsViaPickaxe = Math.max(0, this.coinsViaPickaxe + amount);
            this.lastMinuteCoins += Math.max(0, amount);
            this.lastMinuteCoinsViaPickaxe += Math.max(0, amount); // NOUVEAU : Track séparé
        }
    }

    public void addTokensViaPickaxe(long amount) {
        synchronized (dataLock) {
            this.tokens = Math.max(0, this.tokens + amount);
            this.tokensViaPickaxe = Math.max(0, this.tokensViaPickaxe + amount);
            this.lastMinuteTokens += Math.max(0, amount);
            this.lastMinuteTokensViaPickaxe += Math.max(0, amount); // NOUVEAU : Track séparé
        }
    }

    public void addExperienceViaPickaxe(long amount) {
        synchronized (dataLock) {
            this.experience = Math.max(0, this.experience + amount);
            this.experienceViaPickaxe = Math.max(0, this.experienceViaPickaxe + amount);
            this.lastMinuteExperience += Math.max(0, amount);
            this.lastMinuteExperienceViaPickaxe += Math.max(0, amount); // NOUVEAU : Track séparé
        }
    }

    // Méthodes pour le tracking des statistiques spécialisées
    public void addGreedTrigger() {
        synchronized (dataLock) {
            this.totalGreedTriggers++;
            this.lastMinuteGreedTriggers++;
        }
    }

    public void addKeyObtained() {
        synchronized (dataLock) {
            this.totalKeysObtained++;
            this.lastMinuteKeysObtained++;
        }
    }

    public boolean removeTokens(long amount) {
        synchronized (dataLock) {
            if (this.tokens >= amount) {
                this.tokens -= amount;
                return true;
            }
            return false;
        }
    }

    public void removeCoins(long amount) {
        synchronized (dataLock) {
            if (this.coins >= amount) {
                this.coins -= amount;
            }
        }
    }

    public void removeExperience(long amount) {
        synchronized (dataLock) {
            if (this.experience >= amount) {
                this.experience -= amount;

            }
        }
    }

    public void removeBeacon(long amount) {
        synchronized (dataLock) {
            if (this.beacons >= amount) {
                this.beacons -= amount;

            }
        }
    }

    public int getEnchantmentLevel(String enchantmentName) {
        return enchantmentLevels.getOrDefault(enchantmentName, 0);
    }

    public void setEnchantmentLevel(String enchantmentName, int level) {
        synchronized (dataLock) {
            if (level <= 0) {
                enchantmentLevels.remove(enchantmentName);
            } else {
                enchantmentLevels.put(enchantmentName, level);
            }
        }
    }

    // NOUVEAU: Gestion des enchantements mobilité désactivés
    public boolean isMobilityEnchantmentEnabled(String enchantmentName) {
        return !mobilityEnchantmentsDisabled.contains(enchantmentName);
    }

    public void setMobilityEnchantmentEnabled(String enchantmentName, boolean enabled) {
        if (enabled) {
            mobilityEnchantmentsDisabled.remove(enchantmentName);
        } else {
            mobilityEnchantmentsDisabled.add(enchantmentName);
        }
    }

    public void updateCombustion(int gainPerBlock) {
        synchronized (dataLock) {
            this.combustionLevel = Math.min(1000, this.combustionLevel + gainPerBlock);
            this.lastCombustionTime = System.currentTimeMillis();
        }
    }

    public double getCombustionMultiplier() {
        synchronized (dataLock) {
            return 1.0 + (combustionLevel / 1000.0);
        }
    }

    /**
     * CORRIGÉ : Vérifie si abondance est en cooldown (pas pendant qu'elle est active)
     */
    public boolean isAbundanceOnCooldown() {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();

            // En cooldown seulement si l'effet est terminé ET que le cooldown n'est pas fini
            return !abundanceActive && abundanceCooldownEnd > now;
        }
    }

    /**
     * CORRIGÉ : Cooldown restant seulement si pas active et en cooldown
     */
    public long getAbundanceCooldownSecondsLeft() {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();

            if (abundanceActive) return 0;

            if (abundanceCooldownEnd <= now) return 0;
            return (abundanceCooldownEnd - now) / 1000;
        }
    }

    // MODIFIÉ : Activation d'abondance avec cooldown
    public void activateAbundance(long durationMs) {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();

            // Vérifie le cooldown
            if (abundanceCooldownEnd > now) {
                return; // Encore en cooldown
            }

            abundanceActive = true;
            abundanceEndTime = now + durationMs;

            // CORRIGÉ : Le cooldown commence APRÈS la fin de l'effet d'abondance
            abundanceCooldownEnd = abundanceEndTime + (5 * 60 * 1000); // 5 minutes APRÈS la fin
        }
    }

    public boolean isAbundanceActive() {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            if (abundanceActive && abundanceEndTime <= now) {
                abundanceActive = false;
            }
            return abundanceActive;
        }
    }

    /**
     * NOUVEAU : Désactive de force l'abondance (pour pioche cassée)
     */
    public void deactivateAbundance() {
        synchronized (dataLock) {
            if (abundanceActive) {
                abundanceActive = false;
                abundanceEndTime = 0;
                // Note: On ne touche pas au cooldown pour pas perturber le système normal
            }
        }
    }

    // Méthodes d'enchantements

    /**
     * CORRIGÉ : Ajoute un bloc MINÉ directement par le joueur avec la pioche
     * Les blocs minés comptent aussi comme détruits dans le total général
     */
    public void addMinedBlock(Material material) {
        synchronized (dataLock) {
            totalBlocksMined++;           // Blocs minés directement par la pioche
            totalBlocksDestroyed++;       // Ces blocs comptent aussi dans le total général
            lastMinuteBlocksMined++;      // Stats de la minute
            lastMinuteBlocksDestroyed++;  // Ces blocs comptent aussi dans le total minute

            if (material != null) {
                blocksMinedByType.merge(material, 1L, Long::sum);
            }
        }
    }

    /**
     * CORRIGÉ : Ajoute des blocs CASSÉS par laser/explosion (pas minés directement)
     * Ces blocs s'ajoutent au total des blocs détruits mais pas au total des blocs minés
     */
    public void addDestroyedBlocks(int count) {
        synchronized (dataLock) {
            totalBlocksDestroyed += count;       // S'ajoute au total général
            lastMinuteBlocksDestroyed += count;  // S'ajoute au total minute
            // NE PAS ajouter à totalBlocksMined car ce ne sont pas des blocs minés directement
        }
    }

    public void resetLastMinuteStats() {
        synchronized (dataLock) {
            this.lastMinuteCoins = 0;
            this.lastMinuteTokens = 0;
            this.lastMinuteExperience = 0;
            this.lastMinuteCoinsViaPickaxe = 0;
            this.lastMinuteTokensViaPickaxe = 0;
            this.lastMinuteExperienceViaPickaxe = 0;
            this.lastMinuteAutoUpgrades = 0;
            this.lastMinuteAutoUpgradeDetails.clear(); // ← NOUVEAU
            this.lastMinuteBlocksMined = 0;
            this.lastMinuteBlocksDestroyed = 0;
            this.lastMinuteGreedTriggers = 0;
            this.lastMinuteKeysObtained = 0;
            this.lastMinuteBlocksAddedToInventory = 0;
        }
    }

    public boolean isAutoUpgradeEnabled(String enchantmentName) {
        return autoUpgradeEnabled.contains(enchantmentName);
    }

    // Combustion

    public void setAutoUpgrade(String enchantmentName, boolean enabled) {
        if (enabled) {
            autoUpgradeEnabled.add(enchantmentName);
        } else {
            autoUpgradeEnabled.remove(enchantmentName);
        }
    }

    /**
     * Ajoute un détail d'auto-upgrade
     */
    public void addAutoUpgradeDetail(String enchantmentName, String displayName, int levelsGained, int newLevel) {
        synchronized (dataLock) {
            this.lastMinuteAutoUpgrades += levelsGained;
            this.lastMinuteAutoUpgradeDetails.add(new AutoUpgradeDetail(enchantmentName, displayName, levelsGained, newLevel));
        }
    }

    /**
     * Obtient les détails des auto-upgrades de la dernière minute
     */
    public List<AutoUpgradeDetail> getLastMinuteAutoUpgradeDetails() {
        synchronized (dataLock) {
            return new ArrayList<>(lastMinuteAutoUpgradeDetails);
        }
    }

    /**
     * Ajoute une permission de mine au joueur
     */
    public void addMinePermission(String mineName) {
        synchronized (dataLock) {
            minePermissions.add(mineName.toLowerCase());
        }
    }

    /**
     * Supprime une permission de mine du joueur
     */
    public void removeMinePermission(String mineName) {
        synchronized (dataLock) {
            minePermissions.remove(mineName.toLowerCase());
        }
    }

    /**
     * Vérifie si le joueur a la permission pour une mine spécifique
     * Logique cumulative :
     * - Permission "a" : peut miner dans A seulement
     * - Permission "b" : peut miner dans A et B
     * - Permission "c" : peut miner dans A, B et C
     */
    public boolean hasMinePermission(String mineName) {
        if (mineName == null || mineName.isEmpty()) {
            return false;
        }
        if (mineName.contains("a")) {
            return true;
        }

        String targetMine = mineName.toLowerCase();
        synchronized (dataLock) {
            // Trouve la permission la plus élevée du joueur
            String highestPermission = getHighestMinePermission();
            if (highestPermission == null) {
                return false; // Aucune permission
            }

            // Vérifie si la mine demandée est accessible avec la permission la plus élevée
            return targetMine.compareTo(highestPermission) <= 0;
        }
    }

    /**
     * Retourne toutes les permissions de mine du joueur
     */
    public Set<String> getMinePermissions() {
        synchronized (dataLock) {
            return new HashSet<>(minePermissions);
        }
    }

    /**
     * Retourne la plus haute permission de mine (alphabétiquement)
     */
    public String getHighestMinePermission() {
        synchronized (dataLock) {
            return minePermissions.stream()
                    .max(String::compareTo)
                    .orElse(null);
        }
    }

    /**
     * Vérifie si le joueur a au moins une permission de mine
     */
    public boolean hasAnyMinePermission() {
        synchronized (dataLock) {
            return !minePermissions.isEmpty();
        }
    }

    /**
     * Efface toutes les permissions de mine
     */
    public void clearMinePermissions() {
        synchronized (dataLock) {
            minePermissions.clear();
        }
    }

    public long getCoins() {
        synchronized (dataLock) {
            return coins;
        }
    }

    // Auto-upgrade

    // Setters directs pour la sauvegarde/chargement
    public void setCoins(long coins) {
        synchronized (dataLock) {
            this.coins = Math.max(0, coins);
        }
    }

    public long getTokens() {
        synchronized (dataLock) {
            return tokens;
        }
    }

    public void setTokens(long tokens) {
        synchronized (dataLock) {
            this.tokens = Math.max(0, tokens);
        }
    }

    public long getExperience() {
        synchronized (dataLock) {
            return experience;
        }
    }

    public void setExperience(long experience) {
        synchronized (dataLock) {
            this.experience = Math.max(0, experience);
        }
    }

    // Mine

    public long getBeacons() {
        synchronized (dataLock) {
            return beacons;
        }
    }

    public void setBeacons(long beacons) {
        synchronized (dataLock) {
            this.beacons = Math.max(0, beacons);
        }
    }

    // NOUVEAUX: Getters spécifiques pioche
    public long getCoinsViaPickaxe() {
        synchronized (dataLock) {
            return coinsViaPickaxe;
        }
    }

    public void setCoinsViaPickaxe(long coinsViaPickaxe) {
        synchronized (dataLock) {
            this.coinsViaPickaxe = Math.max(0, coinsViaPickaxe);
        }
    }

    public long getTokensViaPickaxe() {
        synchronized (dataLock) {
            return tokensViaPickaxe;
        }
    }

    public void setTokensViaPickaxe(long tokensViaPickaxe) {
        synchronized (dataLock) {
            this.tokensViaPickaxe = Math.max(0, tokensViaPickaxe);
        }
    }

    public long getExperienceViaPickaxe() {
        synchronized (dataLock) {
            return experienceViaPickaxe;
        }
    }

    // Getters thread-safe

    public void setExperienceViaPickaxe(long experienceViaPickaxe) {
        synchronized (dataLock) {
            this.experienceViaPickaxe = Math.max(0, experienceViaPickaxe);
        }
    }

    public long getCombustionLevel() {
        synchronized (dataLock) {
            return combustionLevel;
        }
    }

    /**
     * NOUVEAU : Définit directement le niveau de combustion (pour CombustionDecayTask)
     */
    public void setCombustionLevel(long combustionLevel) {
        synchronized (dataLock) {
            this.combustionLevel = Math.max(0, Math.min(1000, combustionLevel));
        }
    }

    public long getLastMinuteCoins() {
        synchronized (dataLock) {
            return lastMinuteCoins;
        }
    }

    public long getLastMinuteTokens() {
        synchronized (dataLock) {
            return lastMinuteTokens;
        }
    }

    public long getLastMinuteExperience() {
        synchronized (dataLock) {
            return lastMinuteExperience;
        }
    }

    // NOUVEAUX: Getters gains via pioche dernière minute
    public long getLastMinuteCoinsViaPickaxe() {
        synchronized (dataLock) {
            return lastMinuteCoinsViaPickaxe;
        }
    }

    public long getLastMinuteTokensViaPickaxe() {
        synchronized (dataLock) {
            return lastMinuteTokensViaPickaxe;
        }
    }

    public long getLastMinuteExperienceViaPickaxe() {
        synchronized (dataLock) {
            return lastMinuteExperienceViaPickaxe;
        }
    }

    public int getLastMinuteAutoUpgrades() {
        synchronized (dataLock) {
            return lastMinuteAutoUpgrades;
        }
    }

    public void setLastMinuteAutoUpgrades(int count) {
        synchronized (dataLock) {
            this.lastMinuteAutoUpgrades += count;
        }
    }

    public long getLastMinuteBlocksMined() {
        synchronized (dataLock) {
            return lastMinuteBlocksMined;
        }
    }

    public long getLastMinuteBlocksDestroyed() {
        synchronized (dataLock) {
            return lastMinuteBlocksDestroyed;
        }
    }

    public long getLastMinuteGreedTriggers() {
        synchronized (dataLock) {
            return lastMinuteGreedTriggers;
        }
    }

    public long getLastMinuteKeysObtained() {
        synchronized (dataLock) {
            return lastMinuteKeysObtained;
        }
    }

    public long getTotalGreedTriggers() {
        synchronized (dataLock) {
            return totalGreedTriggers;
        }
    }

    public void setTotalGreedTriggers(long totalGreedTriggers) {
        synchronized (dataLock) {
            this.totalGreedTriggers = Math.max(0, totalGreedTriggers);
        }
    }

    public long getTotalKeysObtained() {
        synchronized (dataLock) {
            return totalKeysObtained;
        }
    }

    public void setTotalKeysObtained(long totalKeysObtained) {
        synchronized (dataLock) {
            this.totalKeysObtained = Math.max(0, totalKeysObtained);
        }
    }

    public void addBlocksToInventory(int count) {
        synchronized (dataLock) {
            this.lastMinuteBlocksAddedToInventory += count;
        }
    }

    public long getLastMinuteBlocksAddedToInventory() {
        synchronized (dataLock) {
            return lastMinuteBlocksAddedToInventory;
        }
    }

    // NOUVEAU : Met à jour le temps de minage (utilisé pour tracker l'activité)
    public void updateMiningActivity() {
        synchronized (dataLock) {
            this.lastMiningTime = System.currentTimeMillis();
        }
    }

    // NOUVEAU : Vérifie si le joueur mine actuellement (dans les dernières 3 secondes)
    public boolean isCurrentlyMining() {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            return (now - lastMiningTime) <= 3000; // 3 secondes
        }
    }

    // Autres getters
    public UUID getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getTotalBlocksMined() {
        return totalBlocksMined;
    }

    public void setTotalBlocksMined(long totalBlocksMined) {
        synchronized (dataLock) {
            this.totalBlocksMined = Math.max(0, totalBlocksMined);
        }
    }

    public long getTotalBlocksDestroyed() {
        return totalBlocksDestroyed;
    }

    public void setTotalBlocksDestroyed(long totalBlocksDestroyed) {
        synchronized (dataLock) {
            this.totalBlocksDestroyed = Math.max(0, totalBlocksDestroyed);
        }
    }

    public Map<String, Integer> getEnchantmentLevels() {
        return new HashMap<>(enchantmentLevels);
    }

    public Set<String> getAutoUpgradeEnabled() {
        return new HashSet<>(autoUpgradeEnabled);
    }

    public Set<String> getMobilityEnchantmentsDisabled() {
        return new HashSet<>(mobilityEnchantmentsDisabled);
    }

    /**
     * CORRIGÉ : Vérifie si le joueur a miné cette minute (pas seulement cassé)
     */
    public boolean hasMinedThisMinute() {
        synchronized (dataLock) {
            return lastMinuteBlocksMined > 0; // Seulement les blocs MINÉS comptent pour le récapitulatif
        }
    }

    /**
     * Obtient l'état de l'auto-rankup
     */
    public boolean hasAutoRankup() {
        synchronized (dataLock) {
            return autoRankup;
        }
    }

    /**
     * Définit l'état de l'auto-rankup
     */
    public void setAutoRankup(boolean enabled) {
        synchronized (dataLock) {
            this.autoRankup = enabled;
        }
    }

    public Map<String, String> getPickaxeCristals() {
        synchronized (dataLock) {
            return new HashMap<>(pickaxeCristals);
        }
    }

    public void setPickaxeCristal(String cristalUuid, String cristalData) {
        synchronized (dataLock) {
            pickaxeCristals.put(cristalUuid, cristalData);
        }
    }

    public void removePickaxeCristal(String cristalUuid) {
        synchronized (dataLock) {
            pickaxeCristals.remove(cristalUuid);
        }
    }

    public void clearPickaxeCristals() {
        synchronized (dataLock) {
            pickaxeCristals.clear();
        }
    }

    public boolean hasPickaxeCristal(String cristalUuid) {
        synchronized (dataLock) {
            return pickaxeCristals.containsKey(cristalUuid);
        }
    }

    /**
     * NOUVEAU: Ajoute une permission custom
     */
    public void addPermission(String permission) {
        customPermissions.add(permission);
    }

    /**
     * NOUVEAU: Retire une permission custom
     */
    public void removePermission(String permission) {
        customPermissions.remove(permission);
    }

    /**
     * NOUVEAU: Vérifie si a une permission custom
     */
    public boolean hasCustomPermission(String permission) {
        return customPermissions.contains(permission);
    }

    /**
     * NOUVEAU: Obtient toutes les permissions custom
     */
    public Set<String> getCustomPermissions() {
        return new HashSet<>(customPermissions);
    }

    /**
     * NOUVEAU: Définit toutes les permissions (pour chargement)
     */
    public void setCustomPermissions(Set<String> permissions) {
        this.customPermissions = new HashSet<>(permissions);
    }

    /**
     * NOUVEAU: Vérifie si est VIP via permission stockée
     */
    public boolean isVip() {
        return hasCustomPermission("specialmine.vip");
    }

    /**
     * NOUVEAU: Définit le statut VIP
     */
    public void setVip(boolean vip) {
        if (vip) {
            addPermission("specialmine.vip");
        } else {
            removePermission("specialmine.vip");
        }
    }

    /**
     * Ajoute une sanction à l'historique
     */
    public void addSanction(String type, String reason, String moderator, long startTime, long endTime) {
        SanctionData sanction = new SanctionData(type, reason, moderator, startTime, endTime);
        sanctionHistory.add(sanction);
    }

    /**
     * Obtient l'historique des sanctions
     */
    public List<SanctionData> getSanctionHistory() {
        return new ArrayList<>(sanctionHistory);
    }

    /**
     * Obtient le nombre total de sanctions
     */
    public int getTotalSanctions() {
        return sanctionHistory.size();
    }

    public static class AutoUpgradeDetail {
        private final String enchantmentName;
        private final String displayName;
        private final int levelsGained;
        private final int newLevel;

        public AutoUpgradeDetail(String enchantmentName, String displayName, int levelsGained, int newLevel) {
            this.enchantmentName = enchantmentName;
            this.displayName = displayName;
            this.levelsGained = levelsGained;
            this.newLevel = newLevel;
        }

        public String getEnchantmentName() {
            return enchantmentName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public int getLevelsGained() {
            return levelsGained;
        }

        public int getNewLevel() {
            return newLevel;
        }
    }

    /**
     * Classe interne pour représenter une sanction
     */
    public static class SanctionData {
        private final String type; // MUTE, BAN
        private final String reason;
        private final String moderator;
        private final long startTime;
        private final long endTime;

        public SanctionData(String type, String reason, String moderator, long startTime, long endTime) {
            this.type = type;
            this.reason = reason;
            this.moderator = moderator;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        // Getters
        public String getType() {
            return type;
        }

        public String getReason() {
            return reason;
        }

        public String getModerator() {
            return moderator;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public boolean isPermanent() {
            return endTime == 0;
        }
    }
}