package fr.prisontycoon.data;

import fr.prisontycoon.boosts.PlayerBoost;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

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
    // Données thread-safe
    private final Object dataLock = new Object();
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
    private final Map<Material, Long> autominerStorageContents;
    private final Map<String, Integer> autominerStoredKeys;
    private final Map<PrestigeTalent, Integer> prestigeTalents = new HashMap<>();
    private final Map<String, Boolean> unlockedPrestigeRewards = new HashMap<>(); // rewardId -> unlocked
    private final Map<Integer, String> chosenPrestigeTalents = new HashMap<>(); // prestigeLevel -> talentName
    // Investissements - Map<Material, Quantité> avec support grandes valeurs
    private final Map<Material, Long> investments = new ConcurrentHashMap<>();
    private final Map<String, Integer> pickaxeEnchantmentBookLevels = new ConcurrentHashMap<>();
    private List<SanctionData> sanctionHistory;
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
    private Set<String> customPermissions;
    private Set<String> activeEnchantmentBooks = new HashSet<>();
    // Système de métiers
    private String activeProfession; // null si aucun métier choisi
    private long lastProfessionChange; // Timestamp du dernier changement
    private int reputation = 0;
    private ItemStack activeAutominerSlot1;
    private ItemStack activeAutominerSlot2;
    // Carburant et monde
    private double autominerFuelReserve;
    private String autominerCurrentWorld;
    // Stockage
    private int autominerStorageLevel;
    // Gains en attente (greed et beacons)
    private long autominerPendingCoins;
    private long autominerPendingTokens;
    private long autominerPendingExperience;
    private long autominerPendingBeacons;
    private long savingsBalance = 0;
    private long safeBalance = 0;
    private int bankLevel = 1;
    private long totalBankDeposits = 0;
    private long lastInterestTime = System.currentTimeMillis();
    // Type de banque choisi et cooldown de changement
    private BankType bankType = BankType.NONE;
    private long lastBankTypeChange = 0L;

    private String gangId;
    private String gangInvitation; // ID du gang qui a invité ce joueur

    private String selectedOutpostSkin = "default";
    private Set<String> unlockedOutpostSkins = new HashSet<>();

    private Set<String> collectedHeads = new HashSet<>();
    private Set<Integer> claimedHeadRewards = new HashSet<>();

    // Récompense journalière
    private int dailyProgress = 0; // 0..14 ; jour réclamable = dailyProgress + 1
    private long dailyLastClaim = 0L; // epoch millis

    // Temps de jeu total (ms) et début de session (ms, non persisté)
    private long totalPlaytimeMillis = 0L;
    private long sessionStartMillis = 0L;

	// Cooldowns/horodatages divers
	    private long lastRepairTime = 0L; // /repair (3h)
    
    // Slots d'imprimantes bonus (via items physiques)
    private int printerSlotBonus = 0;


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

        this.pickaxeCristals = new ConcurrentHashMap<>(); // NOUVEAU
        this.sanctionHistory = new ArrayList<>();
        this.customPermissions = new HashSet<>(); // NOUVEAU

        // Récompense journalière par défaut
        this.dailyProgress = 0;
        this.dailyLastClaim = 0L;

        // Dans le constructeur existant de PlayerData, ajouter:
        this.activeProfession = null;
        this.lastProfessionChange = 0;
        this.professionLevels = new ConcurrentHashMap<>();
        this.professionXP = new ConcurrentHashMap<>();
        this.talentLevels = new ConcurrentHashMap<>();
        this.kitLevels = new ConcurrentHashMap<>();
        this.claimedProfessionRewards = new ConcurrentHashMap<>();

        this.activeAutominerSlot1 = null;
        this.activeAutominerSlot2 = null;
        this.autominerFuelReserve = 0.0;
        this.autominerCurrentWorld = "mine-a";
        this.autominerStorageLevel = 0;
        this.autominerStorageContents = new ConcurrentHashMap<>();
        this.autominerStoredKeys = new ConcurrentHashMap<>();
        this.autominerPendingCoins = 0L;
        this.autominerPendingTokens = 0L;
        this.autominerPendingExperience = 0L;
        this.autominerPendingBeacons = 0L;

        this.gangId = null;
        this.gangInvitation = null;

        this.selectedOutpostSkin = "default";
        this.unlockedOutpostSkins = new HashSet<>();

        // Récompense journalière par défaut
        this.dailyProgress = 0;
        this.dailyLastClaim = 0L;

        // Playtime par défaut
        this.totalPlaytimeMillis = 0L;
        this.sessionStartMillis = 0L;

        // Reset stats dernière minute
        resetLastMinuteStats();
    }

    public BankType getBankType() {
        return bankType;
    }

    public void setBankType(BankType bankType) {
        this.bankType = bankType != null ? bankType : BankType.NONE;
    }

    public long getLastBankTypeChange() {
        return lastBankTypeChange;
    }

    public void setLastBankTypeChange(long lastBankTypeChange) {
        this.lastBankTypeChange = Math.max(0L, lastBankTypeChange);
    }

    // --- Daily reward getters/setters ---
    public int getDailyProgress() {
        return dailyProgress;
    }

    public void setDailyProgress(int dailyProgress) {
        this.dailyProgress = Math.max(0, Math.min(14, dailyProgress));
    }

    public long getDailyLastClaim() {
        return dailyLastClaim;
    }

    public void setDailyLastClaim(long dailyLastClaim) {
        this.dailyLastClaim = Math.max(0L, dailyLastClaim);
    }

    public int getDailyClaimableDay() {
        return (dailyProgress % 15) + 1;
    }

    public boolean hasClaimedToday(java.time.LocalDate today) {
        if (dailyLastClaim <= 0) return false;
        java.time.LocalDate then = java.time.Instant.ofEpochMilli(dailyLastClaim)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        return today.equals(then);
    }

    public void advanceDailyProgress() {
        this.dailyProgress = (this.dailyProgress + 1) % 15;
    }

    // --- Playtime ---
    public long getTotalPlaytimeMillis() {
        return totalPlaytimeMillis;
    }

    public void setTotalPlaytimeMillis(long totalPlaytimeMillis) {
        this.totalPlaytimeMillis = Math.max(0L, totalPlaytimeMillis);
    }

    public long getSessionStartMillis() {
        return sessionStartMillis;
    }

    public void setSessionStartMillis(long sessionStartMillis) {
        this.sessionStartMillis = Math.max(0L, sessionStartMillis);
    }

	public long getLastRepairTime() {
		return lastRepairTime;
	}

	public void setLastRepairTime(long lastRepairTime) {
		this.lastRepairTime = Math.max(0L, lastRepairTime);
	}
	
	// --- Printer slots bonus ---
	public int getPrinterSlotBonus() {
		return printerSlotBonus;
	}
	
	public void setPrinterSlotBonus(int printerSlotBonus) {
		this.printerSlotBonus = Math.max(0, printerSlotBonus);
	}
	
	public void addPrinterSlotBonus(int amount) {
		this.printerSlotBonus = Math.max(0, this.printerSlotBonus + amount);
	}

    public void addPlaytimeMillis(long delta) {
        if (delta > 0) {
            this.totalPlaytimeMillis += delta;
        }
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

    public void setEnchantmentLevel(Map<String, Integer> enchantments) {
        synchronized (dataLock) {
            this.enchantmentLevels.clear();
            if (enchantments != null) {
                this.enchantmentLevels.putAll(enchantments);
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

    public void setMobilityEnchantmentEnabled(Set<String> mobilityDisabled) {
        synchronized (dataLock) {
            this.mobilityEnchantmentsDisabled.clear();
            if (mobilityDisabled != null) {
                this.mobilityEnchantmentsDisabled.addAll(mobilityDisabled);
            }
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

    public void setAutoUpgrade(String enchantmentName, boolean enabled) {
        if (enabled) {
            autoUpgradeEnabled.add(enchantmentName);
        } else {
            autoUpgradeEnabled.remove(enchantmentName);
        }
    }

    // Combustion

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

    public long getCoins() {
        synchronized (dataLock) {
            return coins;
        }
    }

    // Setters directs pour la sauvegarde/chargement
    public void setCoins(long coins) {
        synchronized (dataLock) {
            this.coins = Math.max(0, coins);
        }
    }

    // Auto-upgrade

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

    public long getBeacons() {
        synchronized (dataLock) {
            return beacons;
        }
    }

    // Mine

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

    public void setExperienceViaPickaxe(long experienceViaPickaxe) {
        synchronized (dataLock) {
            this.experienceViaPickaxe = Math.max(0, experienceViaPickaxe);
        }
    }

    // Getters thread-safe

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

    public void setAutoUpgradeEnabled(Set<String> autoUpgrade) {
        synchronized (dataLock) {
            this.autoUpgradeEnabled.clear();
            if (autoUpgrade != null) {
                this.autoUpgradeEnabled.addAll(autoUpgrade);
            }
        }
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

    public void setPickaxeCristal(Map<String, String> cristals) {
        synchronized (dataLock) {
            this.pickaxeCristals.clear();
            if (cristals != null) {
                this.pickaxeCristals.putAll(cristals);
            }
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

    /**
     * NOUVEAU : Définit le niveau d'un livre d'enchantement unique
     */
    public void setPickaxeEnchantmentBookLevel(String bookId, int level) {
        synchronized (dataLock) {
            if (level <= 0) {
                pickaxeEnchantmentBookLevels.remove(bookId);
            } else {
                pickaxeEnchantmentBookLevels.put(bookId, level);
            }
        }
    }

    /**
     * NOUVEAU : Récupère le niveau d'un livre d'enchantement unique
     */
    public int getPickaxeEnchantmentBookLevel(String bookId) {
        synchronized (dataLock) {
            return pickaxeEnchantmentBookLevels.getOrDefault(bookId, 0);
        }
    }

    /**
     * NOUVEAU : Ajoute un niveau à un livre d'enchantement unique existant
     */
    public void addPickaxeEnchantmentBookLevel(String bookId, int levels) {
        synchronized (dataLock) {
            int currentLevel = getPickaxeEnchantmentBookLevel(bookId);
            setPickaxeEnchantmentBookLevel(bookId, currentLevel + levels);
        }
    }

    /**
     * NOUVEAU : Vérifie si le joueur possède un livre d'enchantement unique
     */
    public boolean hasPickaxeEnchantmentBook(String bookId) {
        return getPickaxeEnchantmentBookLevel(bookId) > 0;
    }

    /**
     * NOUVEAU : Récupère tous les niveaux des livres d'enchantements uniques
     */
    public Map<String, Integer> getPickaxeEnchantmentBookLevels() {
        synchronized (dataLock) {
            return new HashMap<>(pickaxeEnchantmentBookLevels);
        }
    }

    /**
     * NOUVEAU : Définit tous les niveaux des livres d'enchantements uniques (pour chargement)
     */
    public void setPickaxeEnchantmentBookLevels(Map<String, Integer> levels) {
        synchronized (dataLock) {
            this.pickaxeEnchantmentBookLevels.clear();
            if (levels != null) {
                this.pickaxeEnchantmentBookLevels.putAll(levels);
            }
        }
    }

    /**
     * NOUVEAU : Récupère la liste des livres possédés (déduite des niveaux)
     */
    public Set<String> getPlayerEnchantmentBooks() {
        synchronized (dataLock) {
            Set<String> books = new HashSet<>();
            for (Map.Entry<String, Integer> entry : pickaxeEnchantmentBookLevels.entrySet()) {
                if (entry.getValue() > 0) {
                    books.add(entry.getKey());
                }
            }
            return books;
        }
    }

    /**
     * OPTIMISÉ : Remplace l'ancienne méthode addPickaxeEnchantmentBook
     */
    public void addPickaxeEnchantmentBook(String bookId) {
        synchronized (dataLock) {
            // Si pas de niveau défini, mettre niveau 1 par défaut
            if (!pickaxeEnchantmentBookLevels.containsKey(bookId)) {
                pickaxeEnchantmentBookLevels.put(bookId, 1);
            }
        }
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

    public void setSanctionHistory(List<SanctionData> sanctions) {
        synchronized (dataLock) {
            this.sanctionHistory.clear();
            if (sanctions != null) {
                this.sanctionHistory.addAll(sanctions);
            }
        }
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
     * Remplace complètement la map des niveaux de métiers
     */
    public void setAllProfessionLevels(Map<String, Integer> levels) {
        synchronized (dataLock) {
            this.professionLevels.clear();
            if (levels != null) {
                this.professionLevels.putAll(levels);
            }
        }
    }

    /**
     * Obtient tout l'XP de métiers
     */
    public Map<String, Integer> getAllProfessionXP() {
        return new HashMap<>(professionXP);
    }

    /**
     * Remplace complètement la map des XP de métiers
     */
    public void setAllProfessionXP(Map<String, Integer> xpMap) {
        synchronized (dataLock) {
            this.professionXP.clear();
            if (xpMap != null) {
                this.professionXP.putAll(xpMap);
            }
        }
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

    public void setTalentLevels(Map<String, Map<String, Integer>> talents) {
        synchronized (dataLock) {
            this.talentLevels.clear();
            if (talents != null) {
                this.talentLevels.putAll(talents);
            }
        }
    }

    /**
     * NOUVEAU: Obtient tous les niveaux de kits
     */
    public Map<String, Integer> getAllKitLevels() {
        return new HashMap<>(kitLevels);
    }

    public void setKitLevels(Map<String, Integer> kits) {
        synchronized (dataLock) {
            this.kitLevels.clear();
            if (kits != null) {
                this.kitLevels.putAll(kits);
            }
        }
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

    public void setClaimedProfessionRewards(Map<String, Set<Integer>> rewards) {
        synchronized (dataLock) {
            this.claimedProfessionRewards.clear();
            if (rewards != null) {
                this.claimedProfessionRewards.putAll(rewards);
            }
        }
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
                    } catch (NumberFormatException ignored) {
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
     * Obtient le nom affiché du prestige
     */
    public String getPrestigeDisplayName() {
        synchronized (dataLock) {
            int currentLevel = getPrestigeLevel();
            if (currentLevel == 0) return "§7Aucun";
            return "§6§lP" + currentLevel;
        }
    }

    public void setUnlockedPrestigeRewards(Map<String, Boolean> rewards) {
        synchronized (dataLock) {
            this.unlockedPrestigeRewards.clear();
            this.unlockedPrestigeRewards.putAll(rewards);
        }
    }

    public void markPrestigeLevelCompleted(int prestigeLevel) {
        synchronized (dataLock) {
            completedPrestigeLevels.add(prestigeLevel);
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

    // Automineurs actifs
    public ItemStack getActiveAutominerSlot1() {
        return activeAutominerSlot1;
    }

    public void setActiveAutominerSlot1(ItemStack activeAutominerSlot1) {
        this.activeAutominerSlot1 = activeAutominerSlot1;
    }

    public ItemStack getActiveAutominerSlot2() {
        return activeAutominerSlot2;
    }

    public void setActiveAutominerSlot2(ItemStack activeAutominerSlot2) {
        this.activeAutominerSlot2 = activeAutominerSlot2;
    }

    // Carburant
    public double getAutominerFuelReserve() {
        return autominerFuelReserve;
    }

    public void setAutominerFuelReserve(double autominerFuelReserve) {
        this.autominerFuelReserve = Math.max(0, autominerFuelReserve);
    }

    // Monde de minage
    public String getAutominerCurrentWorld() {
        return autominerCurrentWorld;
    }

    public void setAutominerCurrentWorld(String autominerCurrentWorld) {
        this.autominerCurrentWorld = autominerCurrentWorld;
    }

    // Stockage
    public int getAutominerStorageLevel() {
        return autominerStorageLevel;
    }

    public void setAutominerStorageLevel(int autominerStorageLevel) {
        this.autominerStorageLevel = Math.max(0, autominerStorageLevel);
    }

    public Map<Material, Long> getAutominerStorageContents() {
        return new HashMap<>(autominerStorageContents);
    }

    public void setAutominerStorageContents(Map<Material, Long> contents) {
        this.autominerStorageContents.clear();
        if (contents != null) {
            this.autominerStorageContents.putAll(contents);
        }
    }

    public void setAutominerStorageContents(Material material, long value) {
        synchronized (dataLock) {
            if (value <= 0) {
                autominerStorageContents.remove(material);
            } else {
                autominerStorageContents.put(material, value);
            }
        }
    }

    // Clés stockées
    public Map<String, Integer> getAutominerStoredKeys() {
        return new HashMap<>(autominerStoredKeys);
    }

    public void setAutominerStoredKeys(Map<String, Integer> keys) {
        this.autominerStoredKeys.clear();
        if (keys != null) {
            this.autominerStoredKeys.putAll(keys);
        }
    }

    // Gains en attente (greed et beacons)
    public long getAutominerPendingCoins() {
        return autominerPendingCoins;
    }

    public void setAutominerPendingCoins(long autominerPendingCoins) {
        this.autominerPendingCoins = Math.max(0, autominerPendingCoins);
    }

    public void addAutominerPendingCoins(long amount) {
        if (amount > 0) {
            this.autominerPendingCoins += amount;
        }
    }

    public long claimAutominerPendingCoins() {
        long amount = this.autominerPendingCoins;
        this.autominerPendingCoins = 0;
        return amount;
    }

    public long getAutominerPendingTokens() {
        return autominerPendingTokens;
    }

    public void setAutominerPendingTokens(long autominerPendingTokens) {
        this.autominerPendingTokens = Math.max(0, autominerPendingTokens);
    }

    public void addAutominerPendingTokens(long amount) {
        if (amount > 0) {
            this.autominerPendingTokens += amount;
        }
    }

    public long claimAutominerPendingTokens() {
        long amount = this.autominerPendingTokens;
        this.autominerPendingTokens = 0;
        return amount;
    }

    public long getAutominerPendingExperience() {
        return autominerPendingExperience;
    }

    public void setAutominerPendingExperience(long autominerPendingExperience) {
        this.autominerPendingExperience = Math.max(0, autominerPendingExperience);
    }

    public void addAutominerPendingExperience(long amount) {
        if (amount > 0) {
            this.autominerPendingExperience += amount;
        }
    }

    public long claimAutominerPendingExperience() {
        long amount = this.autominerPendingExperience;
        this.autominerPendingExperience = 0;
        return amount;
    }

    public long getAutominerPendingBeacons() {
        return autominerPendingBeacons;
    }

    public void setAutominerPendingBeacons(long autominerPendingBeacons) {
        this.autominerPendingBeacons = Math.max(0, autominerPendingBeacons);
    }

    public void addAutominerPendingBeacons(long amount) {
        if (amount > 0) {
            this.autominerPendingBeacons += amount;
        }
    }

    public long claimAutominerPendingBeacons() {
        long amount = this.autominerPendingBeacons;
        this.autominerPendingBeacons = 0;
        return amount;
    }

    /**
     * Vérifie si le joueur a un rang spécifique ou supérieur
     */
    public boolean hasRank(String rankName) {
        for (String permission : customPermissions) {
            if (permission.startsWith("specialmine.mine.")) {
                String rank = permission.substring("specialmine.mine.".length()).toUpperCase();
                // Logique de comparaison des rangs (A-Z, avec F+ requis pour la banque)
                if (compareRanks(rank, rankName.toUpperCase()) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Compare deux rangs (A=1, B=2, ..., Z=26)
     * Retourne: positif si rank1 > rank2, 0 si égaux, négatif si rank1 < rank2
     */
    private int compareRanks(String rank1, String rank2) {
        char c1 = rank1.charAt(0);
        char c2 = rank2.charAt(0);

        return Character.compare(c1, c2);
    }

    public long getSavingsBalance() {
        synchronized (dataLock) {
            return savingsBalance;
        }
    }

    public void setSavingsBalance(long balance) {
        synchronized (dataLock) {
            this.savingsBalance = Math.max(0, balance);
        }
    }

    public void addSavingsBalance(long amount) {
        synchronized (dataLock) {
            this.savingsBalance += amount;
            this.totalBankDeposits += amount; // Compte pour les niveaux bancaires
        }
    }

    public void removeSavingsBalance(long amount) {
        synchronized (dataLock) {
            this.savingsBalance = Math.max(0, this.savingsBalance - amount);
        }
    }

    public long getSafeBalance() {
        synchronized (dataLock) {
            return safeBalance;
        }
    }

    public void setSafeBalance(long balance) {
        synchronized (dataLock) {
            this.safeBalance = Math.max(0, balance);
        }
    }

// ===============================
// MÉTHODES COFFRE-FORT
// ===============================

    public void addSafeBalance(long amount) {
        synchronized (dataLock) {
            this.safeBalance += amount;
        }
    }

    public void removeSafeBalance(long amount) {
        synchronized (dataLock) {
            this.safeBalance = Math.max(0, this.safeBalance - amount);
        }
    }

    public int getBankLevel() {
        synchronized (dataLock) {
            return bankLevel;
        }
    }

    public void setBankLevel(int level) {
        synchronized (dataLock) {
            this.bankLevel = Math.max(1, Math.min(10, level));
        }
    }

// ===============================
// MÉTHODES NIVEAU BANCAIRE
// ===============================

    public long getTotalBankDeposits() {
        synchronized (dataLock) {
            return totalBankDeposits;
        }
    }

    public void setTotalBankDeposits(long total) {
        synchronized (dataLock) {
            this.totalBankDeposits = Math.max(0, total);
        }
    }

    public long getLastInterestTime() {
        synchronized (dataLock) {
            return lastInterestTime;
        }
    }

    public void setLastInterestTime(long timestamp) {
        synchronized (dataLock) {
            this.lastInterestTime = timestamp;
        }
    }

    public Map<Material, Long> getAllInvestments() {
        synchronized (dataLock) {
            return new HashMap<>(investments);
        }
    }

    public long getInvestmentQuantity(Material material) {
        synchronized (dataLock) {
            return investments.getOrDefault(material, 0L);
        }
    }

// ===============================
// MÉTHODES INVESTISSEMENTS (avec support grandes valeurs)
// ===============================

    public void addInvestment(Material material, long quantity) {
        synchronized (dataLock) {
            investments.put(material, investments.getOrDefault(material, 0L) + quantity);
        }
    }

    public void removeInvestment(Material material, long quantity) {
        synchronized (dataLock) {
            long current = investments.getOrDefault(material, 0L);
            long newAmount = Math.max(0L, current - quantity);

            if (newAmount == 0L) {
                investments.remove(material);
            } else {
                investments.put(material, newAmount);
            }
        }
    }

    public void setInvestment(Material material, long quantity) {
        synchronized (dataLock) {
            if (quantity <= 0L) {
                investments.remove(material);
            } else {
                investments.put(material, quantity);
            }
        }
    }

    public void setBankInvestment(String materialStr, long value) {
        try {
            Material material = Material.valueOf(materialStr);
            setInvestment(material, value);
        } catch (IllegalArgumentException e) {
            // Material not found, handle error
        }
    }

    public Map<String, Long> getBankInvestments() {
        Map<String, Long> stringInvestments = new HashMap<>();
        for (Map.Entry<Material, Long> entry : investments.entrySet()) {
            stringInvestments.put(entry.getKey().name(), entry.getValue());
        }
        return stringInvestments;
    }

    public long getBankSavingsBalance() {
        return getSavingsBalance();
    }

    public void setBankSavingsBalance(long balance) {
        setSavingsBalance(balance);
    }

    public long getBankSafeBalance() {
        return getSafeBalance();
    }

    public void setBankSafeBalance(long balance) {
        setSafeBalance(balance);
    }

    public long getBankLastInterest() {
        return getLastInterestTime();
    }

    public void setBankLastInterest(long time) {
        setLastInterestTime(time);
    }

    /**
     * Obtient l'ID du gang du joueur
     */
    public String getGangId() {
        return gangId;
    }

    /**
     * Définit l'ID du gang du joueur
     */
    public void setGangId(String gangId) {
        this.gangId = gangId;
    }

    /**
     * Obtient l'invitation de gang en attente
     */
    public String getGangInvitation() {
        return gangInvitation;
    }

    /**
     * Définit l'invitation de gang en attente
     */
    public void setGangInvitation(String gangInvitation) {
        this.gangInvitation = gangInvitation;
    }

    /**
     * Vérifie si le joueur est dans un gang
     */
    public boolean hasGang() {
        return gangId != null;
    }

    /**
     * Vérifie si le joueur a une invitation de gang en attente
     */
    public boolean hasPendingGangInvitation() {
        return gangInvitation != null;
    }

    /**
     * Récupère le skin d'avant-poste sélectionné
     */
    public String getSelectedOutpostSkin() {
        return selectedOutpostSkin;
    }

    /**
     * Définit le skin d'avant-poste sélectionné
     */
    public void setSelectedOutpostSkin(String selectedOutpostSkin) {
        this.selectedOutpostSkin = selectedOutpostSkin;
    }

    /**
     * Récupère les skins d'avant-poste débloqués
     */
    public Set<String> getUnlockedOutpostSkins() {
        return new HashSet<>(unlockedOutpostSkins);
    }

    /**
     * Définit tous les skins d'avant-poste débloqués
     */
    public void setUnlockedOutpostSkins(Set<String> unlockedOutpostSkins) {
        this.unlockedOutpostSkins = new HashSet<>(unlockedOutpostSkins);
    }

    /**
     * Vérifie si un skin d'avant-poste est débloqué
     */
    public boolean hasUnlockedOutpostSkin(String skinName) {
        return unlockedOutpostSkins.contains(skinName);
    }

    /**
     * Débloque un skin d'avant-poste
     */
    public void unlockOutpostSkin(String skinName) {
        unlockedOutpostSkins.add(skinName);
    }

    /**
     * Récupère les IDs des têtes collectées par le joueur
     */
    public Set<String> getCollectedHeads() {
        return collectedHeads != null ? collectedHeads : new HashSet<>();
    }

    /**
     * Définit les IDs des têtes collectées par le joueur
     */
    public void setCollectedHeads(Set<String> collectedHeads) {
        this.collectedHeads = collectedHeads != null ? collectedHeads : new HashSet<>();
    }

    /**
     * Récupère les niveaux de récompenses réclamées
     */
    public Set<Integer> getClaimedHeadRewards() {
        return claimedHeadRewards != null ? claimedHeadRewards : new HashSet<>();
    }

    /**
     * Définit les niveaux de récompenses réclamées
     */
    public void setClaimedHeadRewards(Set<Integer> claimedHeadRewards) {
        this.claimedHeadRewards = claimedHeadRewards != null ? claimedHeadRewards : new HashSet<>();
    }

    public record AutoUpgradeDetail(String displayName, int levelsGained, int newLevel) {
    }

    public record SanctionData(String type, String reason, String moderator, long startTime, long endTime) {
    }
}