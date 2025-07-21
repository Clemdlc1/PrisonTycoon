package fr.prisontycoon.data;

import fr.prisontycoon.prestige.PrestigeTalent;
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
    private long coinsViaAutosell;
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
    private long lastMinuteCoins;
    private long lastMinuteAutosell;
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
    private Set<String> pickaxeEnchantmentBooks = new HashSet<>();
    private Set<String> activeEnchantmentBooks = new HashSet<>();

    // Système de métiers
    private String activeProfession; // null si aucun métier choisi
    private long lastProfessionChange; // Timestamp du dernier changement
    private final Map<String, Integer> professionLevels; // profession -> niveau (1-10)
    private final Map<String, Integer> professionXP; // profession -> XP métier
    private final Map<String, Map<String, Integer>> talentLevels; // profession -> (talent -> niveau)
    private final Map<String, Integer> kitLevels; // profession -> niveau du kit (1-10)
    private final Map<String, Set<Integer>> claimedProfessionRewards; // profession -> Set de niveaux réclamés

//prestige
    private int prestigeLevel = 0;
    private final Map<PrestigeTalent, Integer> prestigeTalents = new ConcurrentHashMap<>();
    private final Set<String> chosenSpecialRewards = ConcurrentHashMap.newKeySet(); // IDs des récompenses P5, P10, etc.
    private final Set<String> unlockedPrestigeMines = ConcurrentHashMap.newKeySet();
    private long lastPrestigeTime = 0; // Timestamp du dernier prestige



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

        // Dans le constructeur existant de PlayerData, ajouter:
        this.activeProfession = null;
        this.lastProfessionChange = 0;
        this.professionLevels = new ConcurrentHashMap<>();
        this.professionXP = new ConcurrentHashMap<>();
        this.talentLevels = new ConcurrentHashMap<>();
        this.kitLevels = new ConcurrentHashMap<>();
        this.claimedProfessionRewards = new ConcurrentHashMap<>();



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

    public void addCoinsViaAutosell(long amount) {
        synchronized (dataLock) {
            this.coins = Math.max(0, this.coins + amount);
            this.coinsViaAutosell = Math.max(0, this.coinsViaAutosell + amount);
            this.lastMinuteAutosell += Math.max(0, amount);
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
        }
    }

    public void resetLastMinuteStats() {
        synchronized (dataLock) {
            this.lastMinuteCoins = 0;
            this.lastMinuteAutosell = 0;
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
    public void addAutoUpgradeDetail(String enchantmentName, int levelsGained, int newLevel) {
        synchronized (dataLock) {
            this.lastMinuteAutoUpgrades += levelsGained;
            this.lastMinuteAutoUpgradeDetails.add(new AutoUpgradeDetail(enchantmentName, levelsGained, newLevel));
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
     * NOUVEAU: Ajoute une permission de mine au joueur (maintenant via bukkit)
     */
    public void addMinePermission(String mineName) {
        synchronized (dataLock) {
            // Ancienne logique (gardée pour compatibilité)
            minePermissions.add(mineName.toLowerCase());

            // NOUVEAU: Ajoute aussi la permission bukkit
            String bukkitPermission = "specialmine.mine." + mineName.toLowerCase();
            addPermission(bukkitPermission);
        }
    }

    /**
     * NOUVEAU: Supprime une permission de mine du joueur (maintenant via bukkit)
     */
    public void removeMinePermission(String mineName) {
        synchronized (dataLock) {
            // Ancienne logique (gardée pour compatibilité)
            minePermissions.remove(mineName.toLowerCase());

            // NOUVEAU: Retire aussi la permission bukkit
            String bukkitPermission = "specialmine.mine." + mineName.toLowerCase();
            removePermission(bukkitPermission);
        }
    }

    public boolean hasMinePermission(String mineName) {
        if (mineName == null || mineName.isEmpty()) {
            return false;
        }

        // Normalise le nom de la mine (retire les préfixes)
        String targetMine = mineName.toLowerCase();
        if (targetMine.startsWith("mine-")) {
            targetMine = targetMine.substring(5);
        }

        // Rang A par défaut (toujours accessible)
        if (targetMine.equals("a")) {
            return true;
        }

        // Trouve la permission la plus élevée du joueur
        String highestRank = getHighestMineRank();
        if (highestRank == null) {
            return false; // Aucune permission
        }

        // LOGIQUE HIÉRARCHIQUE: compare les rangs
        // Si j'ai rang 'c', je peux miner dans 'a', 'b', et 'c'
        char playerRank = highestRank.charAt(0);
        char targetRank = targetMine.charAt(0);

        return targetRank <= playerRank;
    }

    /**
     * NOUVEAU: Obtient le rang de mine le plus élevé (pas la permission complète)
     */
    public String getHighestMineRank() {
        synchronized (dataLock) {
            String highestRank = null;

            // Cherche dans les permissions bukkit
            for (String permission : customPermissions) {
                if (permission.startsWith("specialmine.mine.")) {
                    String rank = permission.substring("specialmine.mine.".length());
                    if (rank.length() == 1) { // Valide seulement les rangs d'une lettre
                        if (highestRank == null || rank.compareTo(highestRank) > 0) {
                            highestRank = rank;
                        }
                    }
                }
            }

            return highestRank;
        }
    }

    /**
     * CORRIGÉ: Retourne la plus haute permission de mine (compatibilité)
     */
    public String getHighestMinePermission() {
        synchronized (dataLock) {
            String highestRank = getHighestMineRank();
            if (highestRank != null) {
                return "specialmine.mine." + highestRank;
            }

            // Fallback vers l'ancienne logique
            return minePermissions.stream()
                    .max(String::compareTo)
                    .orElse(null);
        }
    }

    /**
     * NOUVEAU: Liste toutes les mines accessibles avec le rang actuel
     */
    public Set<String> getAccessibleMines() {
        synchronized (dataLock) {
            Set<String> accessibleMines = new HashSet<>();

            String highestRank = getHighestMineRank();
            if (highestRank == null) {
                accessibleMines.add("a"); // Rang par défaut
                return accessibleMines;
            }

            // Ajoute toutes les mines de A jusqu'au rang actuel
            char maxRank = highestRank.charAt(0);
            for (char c = 'a'; c <= maxRank; c++) {
                accessibleMines.add(String.valueOf(c));
            }

            return accessibleMines;
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

    public long getCoinsViaAutosell() {
        synchronized (dataLock) {
            return coinsViaAutosell;
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

    public long getLastMinuteCoinsViaAutosell() {
        synchronized (dataLock) {
            return lastMinuteAutosell;
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

    public void addPickaxeEnchantmentBook(String bookId) {
        pickaxeEnchantmentBooks.add(bookId);
    }

    public void setPickaxeEnchantmentBook(Set<String> bookId) {
        this.pickaxeEnchantmentBooks = new HashSet<>(bookId);
    }

    public Set<String> getPLayerEnchantmentBooks() {
        return new HashSet<>(pickaxeEnchantmentBooks);
    }

    public Set<String> getActiveEnchantmentBooks() {
        return new HashSet<>(activeEnchantmentBooks);
    }

    public void setActiveEnchantmentBooks(Set<String> activeEnchantmentBooks) {
        synchronized (dataLock) {
            this.activeEnchantmentBooks = activeEnchantmentBooks != null ? new HashSet<>(activeEnchantmentBooks) : new HashSet<>();
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

    public record AutoUpgradeDetail(String displayName, int levelsGained, int newLevel) {
    }

    /**
     * Classe interne pour représenter une sanction
     *
     * @param type MUTE, BAN
     */
    public record SanctionData(String type, String reason, String moderator, long startTime, long endTime) {
    }

    //metier
    /**
     * Obtient le métier actif du joueur
     */
    public String getActiveProfession() {
        return activeProfession;
    }

    /**
     * Définit le métier actif du joueur
     */
    public void setActiveProfession(String profession) {
        this.activeProfession = profession;
    }

    /**
     * Obtient le timestamp du dernier changement de métier
     */
    public long getLastProfessionChange() {
        return lastProfessionChange;
    }

    /**
     * Définit le timestamp du dernier changement de métier
     */
    public void setLastProfessionChange(long timestamp) {
        this.lastProfessionChange = timestamp;
    }

    /**
     * Obtient le niveau d'un métier
     */
    public int getProfessionLevel(String profession) {
        return professionLevels.getOrDefault(profession, 1);
    }

    /**
     * Définit le niveau d'un métier
     */
    public void setProfessionLevel(String profession, int level) {
        professionLevels.put(profession, Math.max(1, Math.min(10, level)));
    }

    /**
     * Obtient l'XP d'un métier
     */
    public int getProfessionXP(String profession) {
        return professionXP.getOrDefault(profession, 0);
    }

    /**
     * Définit l'XP d'un métier
     */
    public void setProfessionXP(String profession, int xp) {
        professionXP.put(profession, Math.max(0, xp));
    }

    /**
     * Obtient le niveau d'un talent spécifique
     */
    public int getTalentLevel(String profession, String talent) {
        Map<String, Integer> talents = talentLevels.get(profession);
        if (talents == null) return 0;
        return talents.getOrDefault(talent, 0);
    }

    /**
     * Définit le niveau d'un talent
     */
    public void setTalentLevel(String profession, String talent, int level) {
        talentLevels.computeIfAbsent(profession, k -> new ConcurrentHashMap<>()).put(talent, level);
    }

    /**
     * Obtient tous les niveaux de métiers
     */
    public Map<String, Integer> getAllProfessionLevels() {
        return new HashMap<>(professionLevels);
    }

    /**
     * Obtient tout l'XP de métiers
     */
    public Map<String, Integer> getAllProfessionXP() {
        return new HashMap<>(professionXP);
    }

    /**
     * Obtient tous les niveaux de talents
     */
    public Map<String, Map<String, Integer>> getAllTalentLevels() {
        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : talentLevels.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }
    /**
     * NOUVEAU: Obtient tous les niveaux de kits
     */
    public Map<String, Integer> getAllKitLevels() {
        return new HashMap<>(kitLevels);
    }

    /**
     * NOUVEAU: Obtient le niveau du kit d'un métier
     */
    public int getKitLevel(String profession) {
        return kitLevels.getOrDefault(profession, 0);
    }

    /**
     * NOUVEAU: Définit le niveau du kit d'un métier
     */
    public void setKitLevel(String profession, int level) {
        kitLevels.put(profession, Math.max(0, Math.min(10, level)));
    }

    /**
     * Vérifie si une récompense de métier a été réclamée
     */
    public boolean hasProfessionRewardClaimed(String profession, int level) {
        return claimedProfessionRewards.getOrDefault(profession, new HashSet<>()).contains(level);
    }

    /**
     * Marque une récompense de métier comme réclamée
     */
    public void claimProfessionReward(String profession, int level) {
        claimedProfessionRewards.computeIfAbsent(profession, k -> ConcurrentHashMap.newKeySet()).add(level);
    }

    /**
     * Obtient toutes les récompenses réclamées pour un métier
     */
    public Set<Integer> getClaimedProfessionRewards(String profession) {
        return new HashSet<>(claimedProfessionRewards.getOrDefault(profession, new HashSet<>()));
    }

    /**
     * Obtient toutes les récompenses réclamées (pour la sauvegarde)
     */
    public Map<String, Set<Integer>> getAllClaimedProfessionRewards() {
        Map<String, Set<Integer>> result = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : claimedProfessionRewards.entrySet()) {
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Obtient le niveau de prestige du joueur
     */
    public int getPrestigeLevel() {
        synchronized (dataLock) {
            int highestPrestige = 0;

            // Parcourt toutes les permissions du joueur
            for (String permission : customPermissions) {
                if (permission.startsWith("specialmine.prestige.")) {
                    try {
                        String prestigeStr = permission.substring("specialmine.prestige.".length());
                        int prestigeLevel = Integer.parseInt(prestigeStr);

                        if (prestigeLevel > highestPrestige) {
                            highestPrestige = prestigeLevel;
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }

            return highestPrestige;
        }
    }

    /**
     * Définit le niveau de prestige du joueur
     */
    public void setPrestigeLevel(int level) {
        synchronized (dataLock) {
            // Valider le niveau (0-50)
            int validLevel = Math.max(0, Math.min(50, level));

            // Retirer toutes les anciennes permissions de prestige
            Set<String> prestigePermissionsToRemove = new HashSet<>();
            for (String permission : customPermissions) {
                if (permission.startsWith("specialmine.prestige.")) {
                    prestigePermissionsToRemove.add(permission);
                }
            }

            // Supprimer les anciennes permissions
            for (String permission : prestigePermissionsToRemove) {
                customPermissions.remove(permission);
            }

            // Ajouter la nouvelle permission si le niveau est supérieur à 0
            if (validLevel > 0) {
                String newPrestigePermission = "specialmine.prestige." + validLevel;
                customPermissions.add(newPrestigePermission);
            }

            // Mettre à jour le timestamp du dernier prestige si c'est un niveau valide
            if (validLevel > 0) {
                this.lastPrestigeTime = System.currentTimeMillis();
            }
        }
    }

// ==================== TALENTS DE PRESTIGE ====================

    /**
     * Ajoute un talent de prestige
     */
    public void addPrestigeTalent(PrestigeTalent talent) {
        synchronized (dataLock) {
            prestigeTalents.put(talent, prestigeTalents.getOrDefault(talent, 0) + 1);
        }
    }

    /**
     * Obtient le niveau d'un talent de prestige
     */
    public int getPrestigeTalentLevel(PrestigeTalent talent) {
        synchronized (dataLock) {
            return prestigeTalents.getOrDefault(talent, 0);
        }
    }

    /**
     * Obtient tous les talents de prestige du joueur
     */
    public Map<PrestigeTalent, Integer> getPrestigeTalents() {
        synchronized (dataLock) {
            return new HashMap<>(prestigeTalents);
        }
    }

    /**
     * Retire tous les talents de prestige (pour reset)
     */
    public void clearPrestigeTalents() {
        synchronized (dataLock) {
            prestigeTalents.clear();
        }
    }

    /**
     * Définit les talents de prestige (pour chargement)
     */
    public void setPrestigeTalents(Map<PrestigeTalent, Integer> talents) {
        synchronized (dataLock) {
            prestigeTalents.clear();
            prestigeTalents.putAll(talents);
        }
    }

// ==================== RÉCOMPENSES SPÉCIALES ====================

    /**
     * Marque une récompense spéciale comme choisie
     */
    public void addChosenSpecialReward(String rewardId) {
        synchronized (dataLock) {
            chosenSpecialRewards.add(rewardId);
        }
    }

    /**
     * Vérifie si une récompense spéciale a été choisie
     */
    public boolean hasChosenSpecialReward(String rewardId) {
        synchronized (dataLock) {
            return chosenSpecialRewards.contains(rewardId);
        }
    }

    /**
     * Obtient toutes les récompenses spéciales choisies
     */
    public Set<String> getChosenSpecialRewards() {
        synchronized (dataLock) {
            return new HashSet<>(chosenSpecialRewards);
        }
    }

    /**
     * Vérifie si le joueur peut choisir une récompense pour un niveau donné
     */
    public boolean canChooseRewardForPrestige(int prestigeLevel) {
        synchronized (dataLock) {
            if (prestigeLevel % 5 != 0) return false; // Seuls les paliers P5, P10, etc.

            // Vérifier si une récompense pour ce niveau a déjà été choisie
            String prefix = "p" + prestigeLevel + "_";
            return chosenSpecialRewards.stream().noneMatch(reward -> reward.startsWith(prefix));
        }
    }

// ==================== MINES PRESTIGE ====================

    /**
     * Débloque une mine prestige
     */
    public void unlockPrestigeMine(String mineName) {
        synchronized (dataLock) {
            unlockedPrestigeMines.add(mineName.toLowerCase());
        }
    }

    /**
     * Vérifie si une mine prestige est débloquée
     */
    public boolean hasUnlockedPrestigeMine(String mineName) {
        synchronized (dataLock) {
            return unlockedPrestigeMines.contains(mineName.toLowerCase());
        }
    }

    /**
     * Obtient toutes les mines prestige débloquées
     */
    public Set<String> getUnlockedPrestigeMines() {
        synchronized (dataLock) {
            return new HashSet<>(unlockedPrestigeMines);
        }
    }

    /**
     * Débloque automatiquement les mines selon le niveau de prestige
     */
    public void updateUnlockedPrestigeMines() {
        synchronized (dataLock) {
            if (prestigeLevel >= 1) unlockPrestigeMine("prestige1");
            if (prestigeLevel >= 11) unlockPrestigeMine("prestige11");
            if (prestigeLevel >= 21) unlockPrestigeMine("prestige21");
            if (prestigeLevel >= 31) unlockPrestigeMine("prestige31");
            if (prestigeLevel >= 41) unlockPrestigeMine("prestige41");
        }
    }

// ==================== BONUS ET CALCULS ====================

    /**
     * Calcule le bonus Money Greed total du prestige
     */
    public double getPrestigeMoneyGreedBonus() {
        synchronized (dataLock) {
            double bonus = 0.0;

            // Profit Amélioré : +3% par niveau
            int profitLevel = prestigeTalents.getOrDefault(PrestigeTalent.PROFIT_AMELIORE, 0);
            bonus += profitLevel * 0.03;

            // Profit Amélioré II : +3% d'effet Money Greed (multiplicateur)
            int profitLevel2 = prestigeTalents.getOrDefault(PrestigeTalent.PROFIT_AMELIORE_II, 0);
            bonus *= (1.0 + profitLevel2 * 0.03);

            return bonus;
        }
    }

    /**
     * Calcule le bonus Token Greed total du prestige
     */
    public double getPrestigeTokenGreedBonus() {
        synchronized (dataLock) {
            double bonus = 0.0;

            // Économie Optimisée : +3% par niveau
            int ecoLevel = prestigeTalents.getOrDefault(PrestigeTalent.ECONOMIE_OPTIMISEE, 0);
            bonus += ecoLevel * 0.03;

            // Économie Optimisée II : +3% d'effet Token Greed (multiplicateur)
            int ecoLevel2 = prestigeTalents.getOrDefault(PrestigeTalent.ECONOMIE_OPTIMISEE_II, 0);
            bonus *= (1.0 + ecoLevel2 * 0.03);

            return bonus;
        }
    }

    /**
     * Calcule la réduction de taxe du prestige
     */
    public double getPrestigeTaxReduction() {
        synchronized (dataLock) {
            double reduction = 0.0;

            // Économie Optimisée : -1% par niveau
            int ecoLevel = prestigeTalents.getOrDefault(PrestigeTalent.ECONOMIE_OPTIMISEE, 0);
            reduction += ecoLevel * 0.01;

            // Économie Optimisée II : -1% sur le taux final (multiplicateur)
            int ecoLevel2 = prestigeTalents.getOrDefault(PrestigeTalent.ECONOMIE_OPTIMISEE_II, 0);
            reduction += ecoLevel2 * 0.01;

            return Math.min(reduction, 0.99); // Maximum 99% de réduction
        }
    }

    /**
     * Calcule le bonus de prix de vente du prestige
     */
    public double getPrestigeSellBonus() {
        synchronized (dataLock) {
            double bonus = 0.0;

            // Profit Amélioré : +3% prix de vente
            int profitLevel = prestigeTalents.getOrDefault(PrestigeTalent.PROFIT_AMELIORE, 0);
            bonus += profitLevel * 0.03;

            // Profit Amélioré II : +3% prix vente direct
            int profitLevel2 = prestigeTalents.getOrDefault(PrestigeTalent.PROFIT_AMELIORE_II, 0);
            bonus += profitLevel2 * 0.03;

            return bonus;
        }
    }

// ==================== INFORMATIONS ====================

    /**
     * Obtient le temps du dernier prestige
     */
    public long getLastPrestigeTime() {
        synchronized (dataLock) {
            return lastPrestigeTime;
        }
    }

    /**
     * Obtient le nom affiché du prestige
     */
    public String getPrestigeDisplayName() {
        synchronized (dataLock) {
            int currentLevel = getPrestigeLevel(); // Utilise les permissions
            if (currentLevel == 0) return "§7Aucun";
            return "§6§lP" + currentLevel;
        }
    }

    /**
     * Vérifie si le joueur a atteint un prestige spécifique
     */
    public boolean hasReachedPrestige(int level) {
        synchronized (dataLock) {
            return prestigeLevel >= level;
        }
    }

    /**
     * Obtient le pourcentage de progression vers le prestige maximum
     */
    public double getPrestigeProgress() {
        synchronized (dataLock) {
            return (double) prestigeLevel / 50.0 * 100.0;
        }
    }
}