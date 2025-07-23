package fr.prisontycoon.data;

import fr.prisontycoon.boosts.PlayerBoost;
import fr.prisontycoon.prestige.PrestigeTalent;
import fr.prisontycoon.utils.NumberFormatter;
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
    private final Map<String, Integer> professionLevels; // profession -> niveau (1-10)
    private final Map<String, Integer> professionXP; // profession -> XP métier
    private final Map<String, Map<String, Integer>> talentLevels; // profession -> (talent -> niveau)
    private final Map<String, Integer> kitLevels; // profession -> niveau du kit (1-10)
    private final Map<String, Set<Integer>> claimedProfessionRewards; // profession -> Set de niveaux réclamés
    private final Map<Integer, String> chosenSpecialRewards = new ConcurrentHashMap<>();
    private final Set<String> unlockedPrestigeMines = ConcurrentHashMap.newKeySet();
    private final Set<Integer> completedPrestigeLevels = new HashSet<>();
    private final Map<Integer, PrestigeTalent> chosenPrestigeColumns = new ConcurrentHashMap<>();
    private final Map<String, PlayerBoost> activeBoosts = new HashMap<>();
    private Map<PrestigeTalent, Integer> prestigeTalents = new HashMap<>();
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
    //prestige
    private int prestigeLevel = 0;
    private long lastPrestigeTime = 0; // Timestamp du dernier prestige
    // NOUVEAUX ajouts pour le système amélioré
    private Map<String, Boolean> unlockedPrestigeRewards = new HashMap<>(); // rewardId -> unlocked
    private Map<Integer, String> chosenPrestigeTalents = new HashMap<>(); // prestigeLevel -> talentName
    private int reputation = 0;
    private Set<String> activeAutominers = new HashSet<>();

    // État de fonctionnement des automineurs
    private boolean autominersRunning = false;

    // Carburant (têtes de joueur/monstre)
    private long autominerFuel = 0;

    // Monde de minage actuel (A-Z)
    private String autominerWorld = "a";

    // Stockage des ressources minées
    private Map<Material, Long> autominerStoredBlocks = new HashMap<>();

    // Capacité de stockage (évolutive)
    private long autominerStorageCapacity = 10000; // Démarre à 10k blocs


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

    //metier

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

// ==================== TALENTS DE PRESTIGE ====================

    /**
     * Obtient tous les talents de prestige du joueur
     */
    public Map<PrestigeTalent, Integer> getPrestigeTalents() {
        synchronized (dataLock) {
            return new HashMap<>(prestigeTalents);
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

    /**
     * Retire tous les talents de prestige (pour reset)
     */
    public void clearPrestigeTalents() {
        synchronized (dataLock) {
            prestigeTalents.clear();
        }
    }

    /**
     * Marque une récompense spéciale comme choisie
     */
    public void addChosenSpecialReward(String rewardId) {
        synchronized (dataLock) {
            try {
                String levelStr = rewardId.substring(1, rewardId.indexOf("_"));
                int prestigeLevel = Integer.parseInt(levelStr);
                completedPrestigeLevels.add(prestigeLevel);
            } catch (Exception e) {
                // Log erreur
            }
        }
    }

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

// ==================== MINES PRESTIGE ====================

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

// ==================== INFORMATIONS ====================

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

    /**
     * Débloque une récompense de prestige (action gratuite)
     */
    public void unlockPrestigeReward(String rewardId) {
        synchronized (dataLock) {
            unlockedPrestigeRewards.put(rewardId, true);
        }
    }

    /**
     * Vérifie si une récompense de prestige est débloquée
     */
    public boolean isPrestigeRewardUnlocked(String rewardId) {
        synchronized (dataLock) {
            return unlockedPrestigeRewards.getOrDefault(rewardId, false);
        }
    }

    /**
     * Choisit un talent pour un niveau de prestige donné (un seul par niveau)
     */
    public void choosePrestigeTalent(int prestigeLevel, String talentName) {
        synchronized (dataLock) {
            chosenPrestigeTalents.put(prestigeLevel, talentName);
        }
    }

    /**
     * Obtient le talent choisi pour un niveau de prestige
     */
    public String getChosenPrestigeTalent(int prestigeLevel) {
        synchronized (dataLock) {
            return chosenPrestigeTalents.get(prestigeLevel);
        }
    }

    // Getters/Setters pour la sauvegarde
    public Map<String, Boolean> getUnlockedPrestigeRewards() {
        synchronized (dataLock) {
            return new HashMap<>(unlockedPrestigeRewards);
        }
    }

    public void setUnlockedPrestigeRewards(Map<String, Boolean> rewards) {
        synchronized (dataLock) {
            this.unlockedPrestigeRewards.clear();
            this.unlockedPrestigeRewards.putAll(rewards);
        }
    }

    public Map<Integer, String> getChosenPrestigeTalents() {
        synchronized (dataLock) {
            return new HashMap<>(chosenPrestigeTalents);
        }
    }

    public void setChosenPrestigeTalents(Map<Integer, String> talents) {
        synchronized (dataLock) {
            this.chosenPrestigeTalents.clear();
            this.chosenPrestigeTalents.putAll(talents);
        }
    }

    public void markPrestigeLevelCompleted(int prestigeLevel) {
        synchronized (dataLock) {
            completedPrestigeLevels.add(prestigeLevel);
        }
    }

    public boolean isPrestigeLevelCompleted(int prestigeLevel) {
        synchronized (dataLock) {
            return completedPrestigeLevels.contains(prestigeLevel);
        }
    }

    public Set<Integer> getCompletedPrestigeLevels() {
        synchronized (dataLock) {
            return new HashSet<>(completedPrestigeLevels);
        }
    }

    /**
     * Choisit un bonus spécifique (colonne) pour un niveau de prestige
     */
    public void choosePrestigeColumn(int prestigeLevel, PrestigeTalent talent) {
        synchronized (dataLock) {
            chosenPrestigeColumns.put(prestigeLevel, talent);
            // Ajouter aussi aux talents actifs pour les calculs de bonus
            prestigeTalents.put(talent, prestigeTalents.getOrDefault(talent, 0) + 1);
        }
    }

    /**
     * Obtient le bonus choisi pour un niveau de prestige
     */
    public PrestigeTalent getChosenPrestigeColumn(int prestigeLevel) {
        synchronized (dataLock) {
            return chosenPrestigeColumns.get(prestigeLevel);
        }
    }

    /**
     * Obtient tous les choix de colonnes
     */
    public Map<Integer, PrestigeTalent> getChosenPrestigeColumns() {
        synchronized (dataLock) {
            return new HashMap<>(chosenPrestigeColumns);
        }
    }

    /**
     * Définit les choix de colonnes (pour chargement)
     */
    public void setChosenPrestigeColumns(Map<Integer, PrestigeTalent> columns) {
        synchronized (dataLock) {
            this.chosenPrestigeColumns.clear();
            this.chosenPrestigeColumns.putAll(columns);
        }
    }

    /**
     * Réinitialise les talents de prestige (NOUVELLE VERSION)
     */
    public void resetPrestigeTalents() {
        synchronized (dataLock) {
            prestigeTalents.clear();
            chosenPrestigeColumns.clear();
            // Supprimer l'ancienne Map aussi
            chosenPrestigeTalents.clear();
            // Les récompenses spéciales sont CONSERVÉES
        }
    }

    /**
     * Choisit une récompense spéciale spécifique pour un niveau de prestige
     */
    public void chooseSpecialReward(int prestigeLevel, String rewardId) {
        synchronized (dataLock) {
            chosenSpecialRewards.put(prestigeLevel, rewardId);
            // Marquer aussi comme débloquée pour la compatibilité
            unlockedPrestigeRewards.put(rewardId, true);
            markPrestigeLevelCompleted(prestigeLevel);
        }
    }

    /**
     * Obtient la récompense choisie pour un niveau de prestige
     */
    public String getChosenSpecialReward(int prestigeLevel) {
        synchronized (dataLock) {
            return chosenSpecialRewards.get(prestigeLevel);
        }
    }

    /**
     * Vérifie si une récompense spéciale a été choisie (NOUVELLE VERSION EXCLUSIVE)
     */
    public boolean hasChosenSpecialReward(String rewardId) {
        synchronized (dataLock) {
            // Vérifier si cette récompense spécifique a été choisie
            return chosenSpecialRewards.containsValue(rewardId);
        }
    }

    /**
     * Vérifie si un niveau de prestige a déjà un choix de récompense
     */
    public boolean hasRewardChoiceForLevel(int prestigeLevel) {
        synchronized (dataLock) {
            return chosenSpecialRewards.containsKey(prestigeLevel);
        }
    }

    public Map<Integer, String> getChosenSpecialRewards() {
        synchronized (dataLock) {
            return new HashMap<>(chosenSpecialRewards);
        }
    }

    public void setChosenSpecialRewards(Map<Integer, String> rewards) {
        synchronized (dataLock) {
            this.chosenSpecialRewards.clear();
            this.chosenSpecialRewards.putAll(rewards);
        }
    }

    /**
     * Obtient la réputation actuelle du joueur
     *
     * @return valeur de réputation entre -1000 et +1000
     */
    public int getReputation() {
        return reputation;
    }

    /**
     * Définit la réputation du joueur
     *
     * @param reputation nouvelle valeur (sera contrainte entre -1000 et +1000)
     */
    public void setReputation(int reputation) {
        this.reputation = Math.max(-1000, Math.min(1000, reputation));
    }

    /**
     * Ajoute de la réputation (peut être négatif pour diminuer)
     *
     * @param amount montant à ajouter
     */
    public void addReputation(int amount) {
        setReputation(this.reputation + amount);
    }

    /**
     * Retire de la réputation
     *
     * @param amount montant à retirer
     */
    public void removeReputation(int amount) {
        setReputation(this.reputation - amount);
    }

    /**
     * Vérifie si le joueur a une réputation positive
     *
     * @return true si réputation > 0
     */
    public boolean hasPositiveReputation() {
        return reputation > 0;
    }

    /**
     * Vérifie si le joueur a une réputation négative
     *
     * @return true si réputation < 0
     */
    public boolean hasNegativeReputation() {
        return reputation < 0;
    }

    /**
     * Vérifie si le joueur a une réputation neutre
     *
     * @return true si réputation == 0
     */
    public boolean hasNeutralReputation() {
        return reputation == 0;
    }

    /**
     * Obtient les boosts actifs
     */
    public Map<String, PlayerBoost> getActiveBoosts() {
        synchronized (dataLock) {
            // Nettoie les boosts expirés avant de retourner
            activeBoosts.entrySet().removeIf(entry -> !entry.getValue().isActive());
            return new HashMap<>(activeBoosts);
        }
    }

    /**
     * Définit les boosts actifs
     */
    public void setActiveBoosts(Map<String, PlayerBoost> boosts) {
        synchronized (dataLock) {
            this.activeBoosts.clear();
            if (boosts != null) {
                // Filtre seulement les boosts encore actifs
                for (Map.Entry<String, PlayerBoost> entry : boosts.entrySet()) {
                    if (entry.getValue().isActive()) {
                        this.activeBoosts.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    /**
     * Ajoute un boost actif
     */
    public void addActiveBoost(String type, PlayerBoost boost) {
        synchronized (dataLock) {
            if (boost.isActive()) {
                this.activeBoosts.put(type, boost);
            }
        }
    }

    /**
     * Retire un boost actif
     */
    public void removeActiveBoost(String type) {
        synchronized (dataLock) {
            this.activeBoosts.remove(type);
        }
    }

    /**
     * Vide tous les boosts actifs
     */
    public void clearActiveBoosts() {
        synchronized (dataLock) {
            this.activeBoosts.clear();
        }
    }

    /**
     * Vérifie si un boost est actif
     */
    public boolean hasActiveBoost(String type) {
        synchronized (dataLock) {
            PlayerBoost boost = activeBoosts.get(type);
            return boost != null && boost.isActive();
        }
    }

    /**
     * Nettoie les boosts expirés
     */
    public void cleanupExpiredBoosts() {
        synchronized (dataLock) {
            activeBoosts.entrySet().removeIf(entry -> !entry.getValue().isActive());
        }
    }

    /**
     * Obtient les UUIDs des automineurs actifs
     */
    public Set<String> getActiveAutominers() {
        return new HashSet<>(activeAutominers);
    }

    /**
     * Définit les automineurs actifs
     */
    public void setActiveAutominers(Set<String> activeAutominers) {
        this.activeAutominers = new HashSet<>(activeAutominers);
    }

    // ========================================
//           GETTERS & SETTERS
// ========================================

    /**
     * Ajoute un automineur actif
     */
    public boolean addActiveAutominer(String autominerUuid) {
        if (activeAutominers.size() >= 2) {
            return false; // Limite de 2 automineurs
        }
        return activeAutominers.add(autominerUuid);
    }

    /**
     * Retire un automineur actif
     */
    public boolean removeActiveAutominer(String autominerUuid) {
        return activeAutominers.remove(autominerUuid);
    }

    /**
     * Vérifie si un automineur est actif
     */
    public boolean isAutominerActive(String autominerUuid) {
        return activeAutominers.contains(autominerUuid);
    }

    /**
     * Obtient l'état de fonctionnement des automineurs
     */
    public boolean isAutominersRunning() {
        return autominersRunning;
    }

    /**
     * Définit l'état de fonctionnement des automineurs
     */
    public void setAutominersRunning(boolean running) {
        this.autominersRunning = running;
    }

    /**
     * Obtient la quantité de carburant
     */
    public long getAutominerFuel() {
        return autominerFuel;
    }

    /**
     * Définit la quantité de carburant
     */
    public void setAutominerFuel(long fuel) {
        this.autominerFuel = Math.max(0, fuel);
    }

    /**
     * Ajoute du carburant
     */
    public void addAutominerFuel(long amount) {
        this.autominerFuel += Math.max(0, amount);
    }

    /**
     * Consomme du carburant
     */
    public boolean consumeAutominerFuel(long amount) {
        if (autominerFuel >= amount) {
            autominerFuel -= amount;
            return true;
        }
        return false;
    }

    /**
     * Obtient le monde de minage actuel
     */
    public String getAutominerWorld() {
        return autominerWorld;
    }

    /**
     * Définit le monde de minage
     */
    public void setAutominerWorld(String world) {
        // Validation du monde (a-z)
        if (world != null && world.length() == 1 &&
                world.charAt(0) >= 'a' && world.charAt(0) <= 'z') {
            this.autominerWorld = world;
        }
    }

    /**
     * Tire un monde aléatoire (a-z, plus proche de z = plus rare)
     */
    public String rollRandomWorld() {
        Random rand = new Random();

        // Distribution pondérée : a plus commun, z plus rare
        double[] weights = new double[26];
        for (int i = 0; i < 26; i++) {
            weights[i] = 1.0 / (i + 2); // a=1/2, b=1/3, ..., z=1/27
        }

        // Sélection pondérée
        double totalWeight = 0;
        for (double weight : weights) {
            totalWeight += weight;
        }

        double randomValue = rand.nextDouble() * totalWeight;
        double cumulativeWeight = 0;

        for (int i = 0; i < 26; i++) {
            cumulativeWeight += weights[i];
            if (randomValue <= cumulativeWeight) {
                return String.valueOf((char) ('a' + i));
            }
        }

        return "a"; // Fallback
    }

    /**
     * Obtient les blocs stockés
     */
    public Map<Material, Long> getAutominerStoredBlocks() {
        return autominerStoredBlocks;
    }

    /**
     * Ajoute des blocs au stockage
     */
    public boolean addStoredBlocks(Material material, long amount) {
        long currentTotal = autominerStoredBlocks.values().stream().mapToLong(Long::longValue).sum();

        if (currentTotal + amount <= autominerStorageCapacity) {
            autominerStoredBlocks.merge(material, amount, Long::sum);
            return true;
        }
        return false; // Stockage plein
    }

    /**
     * Retire des blocs du stockage
     */
    public boolean removeStoredBlocks(Material material, long amount) {
        long current = autominerStoredBlocks.getOrDefault(material, 0L);
        if (current >= amount) {
            if (current == amount) {
                autominerStoredBlocks.remove(material);
            } else {
                autominerStoredBlocks.put(material, current - amount);
            }
            return true;
        }
        return false;
    }

    /**
     * Obtient la quantité stockée d'un matériau
     */
    public long getStoredBlockCount(Material material) {
        return autominerStoredBlocks.getOrDefault(material, 0L);
    }

    /**
     * Obtient le nombre total de blocs stockés
     */
    public long getTotalStoredBlocks() {
        return autominerStoredBlocks.values().stream().mapToLong(Long::longValue).sum();
    }

    /**
     * Vide complètement le stockage
     */
    public void clearStoredBlocks() {
        autominerStoredBlocks.clear();
    }

    /**
     * Obtient la capacité de stockage actuelle
     */
    public long getAutominerStorageCapacity() {
        return autominerStorageCapacity;
    }

    /**
     * Définit la capacité de stockage
     */
    public void setAutominerStorageCapacity(long capacity) {
        this.autominerStorageCapacity = Math.max(10000, capacity); // Minimum 10k
    }

    /**
     * Vérifie si le stockage est plein
     */
    public boolean isStorageFull() {
        return getTotalStoredBlocks() >= autominerStorageCapacity;
    }

    /**
     * Obtient le pourcentage de remplissage du stockage
     */
    public double getStorageUsagePercentage() {
        if (autominerStorageCapacity == 0) return 0.0;
        return (double) getTotalStoredBlocks() / autominerStorageCapacity * 100.0;
    }

    /**
     * Obtient l'espace libre dans le stockage
     */
    public long getFreeStorageSpace() {
        return Math.max(0, autominerStorageCapacity - getTotalStoredBlocks());
    }

    /**
     * Sauvegarde les données des automineurs dans une map
     */
    public Map<String, Object> serializeAutominerData() {
        Map<String, Object> data = new HashMap<>();

        data.put("activeAutominers", new ArrayList<>(activeAutominers));
        data.put("autominersRunning", autominersRunning);
        data.put("autominerFuel", autominerFuel);
        data.put("autominerWorld", autominerWorld);
        data.put("autominerStorageCapacity", autominerStorageCapacity);

        // Sérialisation des blocs stockés
        Map<String, Long> storedBlocksData = new HashMap<>();
        for (Map.Entry<Material, Long> entry : autominerStoredBlocks.entrySet()) {
            storedBlocksData.put(entry.getKey().name(), entry.getValue());
        }
        data.put("autominerStoredBlocks", storedBlocksData);

        return data;
    }

    /**
     * Charge les données des automineurs depuis une map
     */
    @SuppressWarnings("unchecked")
    public void deserializeAutominerData(Map<String, Object> data) {
        // Automineurs actifs
        Object activeMinersObj = data.get("activeAutominers");
        if (activeMinersObj instanceof List<?> list) {
            this.activeAutominers = new HashSet<>();
            for (Object obj : list) {
                if (obj instanceof String str) {
                    this.activeAutominers.add(str);
                }
            }
        }

        // État de fonctionnement
        Object runningObj = data.get("autominersRunning");
        if (runningObj instanceof Boolean bool) {
            this.autominersRunning = bool;
        }

        // Carburant
        Object fuelObj = data.get("autominerFuel");
        if (fuelObj instanceof Number num) {
            this.autominerFuel = num.longValue();
        }

        // Monde
        Object worldObj = data.get("autominerWorld");
        if (worldObj instanceof String world && world.length() == 1 &&
                world.charAt(0) >= 'a' && world.charAt(0) <= 'z') {
            this.autominerWorld = world;
        }

        // Capacité de stockage
        Object capacityObj = data.get("autominerStorageCapacity");
        if (capacityObj instanceof Number num) {
            this.autominerStorageCapacity = Math.max(10000, num.longValue());
        }

        // Blocs stockés
        Object storedBlocksObj = data.get("autominerStoredBlocks");
        if (storedBlocksObj instanceof Map<?, ?> storedData) {
            this.autominerStoredBlocks = new HashMap<>();
            for (Map.Entry<?, ?> entry : storedData.entrySet()) {
                if (entry.getKey() instanceof String materialName &&
                        entry.getValue() instanceof Number amount) {
                    try {
                        Material material = Material.valueOf(materialName);
                        this.autominerStoredBlocks.put(material, amount.longValue());
                    } catch (IllegalArgumentException e) {
                        // Matériau invalide, ignoré
                    }
                }
            }
        }
    }

// ========================================
//        MÉTHODES DE SÉRIALISATION
// ========================================

    /**
     * Obtient un résumé des automineurs pour l'affichage
     */
    public String getAutominerSummary() {
        StringBuilder summary = new StringBuilder();

        summary.append("§6⚡ §lAUTOMINEURS\n");
        summary.append("§7▸ Actifs: §e").append(activeAutominers.size()).append("§8/§e2\n");
        summary.append("§7▸ État: ").append(autominersRunning ? "§a✅ En marche" : "§c❌ Arrêtés").append("\n");
        summary.append("§7▸ Carburant: §e").append(autominerFuel).append(" têtes\n");
        summary.append("§7▸ Monde: §e").append(autominerWorld).append("\n");

        long totalStored = getTotalStoredBlocks();
        double percentage = getStorageUsagePercentage();
        summary.append("§7▸ Stockage: §e").append(NumberFormatter.format(totalStored))
                .append("§8/§e").append(NumberFormatter.format(autominerStorageCapacity))
                .append(" §7(").append(String.format("%.1f", percentage)).append("%)\n");

        return summary.toString();
    }

    /**
     * Remet à zéro toutes les données des automineurs
     */
    public void resetAutominerData() {
        activeAutominers.clear();
        autominersRunning = false;
        autominerFuel = 0;
        autominerWorld = "a";
        autominerStoredBlocks.clear();
        autominerStorageCapacity = 10000;
    }

// ========================================
//           MÉTHODES UTILITAIRES
// ========================================

    public record AutoUpgradeDetail(String displayName, int levelsGained, int newLevel) {
    }

    /**
     * Classe interne pour représenter une sanction
     *
     * @param type MUTE, BAN
     */
    public record SanctionData(String type, String reason, String moderator, long startTime, long endTime) {
    }
}
