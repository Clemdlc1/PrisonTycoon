package fr.prisoncore.prisoncore.prisonTycoon.data;

import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Données d'un joueur
 * CORRIGÉ : Distinction gains pioche vs autres moyens
 */
public class PlayerData {
    private final UUID playerId;
    private final String playerName;

    // Économie TOTALE (toutes sources)
    private long coins;
    private long tokens;
    private long experience;

    // Gains SPÉCIFIQUES via pioche (pour statistiques pioche)
    private long coinsViaPickaxe;
    private long tokensViaPickaxe;
    private long experienceViaPickaxe;

    // Enchantements de la pioche (nom -> niveau)
    private final Map<String, Integer> enchantmentLevels;

    // États temporaires
    private long combustionLevel;
    private long lastCombustionTime;
    private boolean abundanceActive;
    private long abundanceEndTime;

    // Auto-amélioration des enchantements
    private final Set<String> autoUpgradeEnabled;

    // Enchantements mobilité désactivés
    private final Set<String> mobilityEnchantmentsDisabled;

    // Statistiques de minage
    private long totalBlocksMined;
    private long totalBlocksDestroyed;
    private final Map<Material, Long> blocksMinedByType;

    // Statistiques spécialisées
    private long totalGreedTriggers;
    private long totalKeysObtained;

    // Gains de la dernière minute (pour récapitulatif)
    private long lastMinuteCoins;
    private long lastMinuteTokens;
    private long lastMinuteExperience;
    private int lastMinuteAutoUpgrades;
    private long lastMinuteBlocksMined;
    private long lastMinuteBlocksDestroyed;
    private long lastMinuteGreedTriggers;
    private long lastMinuteKeysObtained;

    // Données thread-safe
    private final Object dataLock = new Object();

    public PlayerData(UUID playerId, String playerName) {
        this.playerId = playerId;
        this.playerName = playerName;

        // Initialisation des valeurs par défaut
        this.coins = 0;
        this.tokens = 0;
        this.experience = 0;

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

    // NOUVEAUX: Méthodes spécifiques VIA PIOCHE

    public void addCoinsViaPickaxe(long amount) {
        synchronized (dataLock) {
            this.coins = Math.max(0, this.coins + amount);
            this.coinsViaPickaxe = Math.max(0, this.coinsViaPickaxe + amount);
            this.lastMinuteCoins += Math.max(0, amount);
        }
    }

    public void addTokensViaPickaxe(long amount) {
        synchronized (dataLock) {
            this.tokens = Math.max(0, this.tokens + amount);
            this.tokensViaPickaxe = Math.max(0, this.tokensViaPickaxe + amount);
            this.lastMinuteTokens += Math.max(0, amount);
        }
    }

    public void addExperienceViaPickaxe(long amount) {
        synchronized (dataLock) {
            this.experience = Math.max(0, this.experience + amount);
            this.experienceViaPickaxe = Math.max(0, this.experienceViaPickaxe + amount);
            this.lastMinuteExperience += Math.max(0, amount);
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

    // Setters directs pour la sauvegarde/chargement
    public void setCoins(long coins) {
        synchronized (dataLock) {
            this.coins = Math.max(0, coins);
        }
    }

    public void setTokens(long tokens) {
        synchronized (dataLock) {
            this.tokens = Math.max(0, tokens);
        }
    }

    public void setExperience(long experience) {
        synchronized (dataLock) {
            this.experience = Math.max(0, experience);
        }
    }

    public void setCoinsViaPickaxe(long coinsViaPickaxe) {
        synchronized (dataLock) {
            this.coinsViaPickaxe = Math.max(0, coinsViaPickaxe);
        }
    }

    public void setTokensViaPickaxe(long tokensViaPickaxe) {
        synchronized (dataLock) {
            this.tokensViaPickaxe = Math.max(0, tokensViaPickaxe);
        }
    }

    public void setExperienceViaPickaxe(long experienceViaPickaxe) {
        synchronized (dataLock) {
            this.experienceViaPickaxe = Math.max(0, experienceViaPickaxe);
        }
    }

    public void setTotalBlocksMined(long totalBlocksMined) {
        synchronized (dataLock) {
            this.totalBlocksMined = Math.max(0, totalBlocksMined);
        }
    }

    public void setTotalBlocksDestroyed(long totalBlocksDestroyed) {
        synchronized (dataLock) {
            this.totalBlocksDestroyed = Math.max(0, totalBlocksDestroyed);
        }
    }

    public void setTotalGreedTriggers(long totalGreedTriggers) {
        synchronized (dataLock) {
            this.totalGreedTriggers = Math.max(0, totalGreedTriggers);
        }
    }

    public void setTotalKeysObtained(long totalKeysObtained) {
        synchronized (dataLock) {
            this.totalKeysObtained = Math.max(0, totalKeysObtained);
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

    // Méthodes d'enchantements

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

    public boolean upgradeEnchantment(String enchantmentName) {
        synchronized (dataLock) {
            int currentLevel = enchantmentLevels.getOrDefault(enchantmentName, 0);
            enchantmentLevels.put(enchantmentName, currentLevel + 1);
            lastMinuteAutoUpgrades++;
            return true;
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

    // Combustion

    public void updateCombustion(int gainPerBlock) {
        synchronized (dataLock) {
            this.combustionLevel = Math.min(1000, this.combustionLevel + gainPerBlock);
            this.lastCombustionTime = System.currentTimeMillis();
        }
    }

    public void decayCombustion() {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            long secondsSinceLastMining = (now - lastCombustionTime) / 1000;

            if (secondsSinceLastMining > 0) {
                this.combustionLevel = Math.max(0, this.combustionLevel - secondsSinceLastMining);
                this.lastCombustionTime = now;
            }
        }
    }

    public double getCombustionMultiplier() {
        synchronized (dataLock) {
            return 1.0 + (combustionLevel / 1000.0);
        }
    }

    // Abondance

    public void activateAbundance(long durationMs) {
        synchronized (dataLock) {
            long now = System.currentTimeMillis();
            if (abundanceActive && abundanceEndTime > now) {
                abundanceEndTime = Math.max(abundanceEndTime, now + durationMs);
            } else {
                abundanceActive = true;
                abundanceEndTime = now + durationMs;
            }
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

    // Statistiques avec tracking des blocs
    public void addMinedBlock(Material material) {
        synchronized (dataLock) {
            totalBlocksMined++;
            totalBlocksDestroyed++;
            lastMinuteBlocksMined++;
            lastMinuteBlocksDestroyed++;

            if (material != null) {
                blocksMinedByType.merge(material, 1L, Long::sum);
            }
        }
    }

    public void addDestroyedBlocks(int count) {
        synchronized (dataLock) {
            totalBlocksDestroyed += count;
            lastMinuteBlocksDestroyed += count;
        }
    }

    public void resetLastMinuteStats() {
        synchronized (dataLock) {
            lastMinuteCoins = 0;
            lastMinuteTokens = 0;
            lastMinuteExperience = 0;
            lastMinuteAutoUpgrades = 0;
            lastMinuteBlocksMined = 0;
            lastMinuteBlocksDestroyed = 0;
            lastMinuteGreedTriggers = 0;
            lastMinuteKeysObtained = 0;
        }
    }

    // Auto-upgrade

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

    // Getters thread-safe

    public long getCoins() {
        synchronized (dataLock) {
            return coins;
        }
    }

    public long getTokens() {
        synchronized (dataLock) {
            return tokens;
        }
    }

    public long getExperience() {
        synchronized (dataLock) {
            return experience;
        }
    }

    // NOUVEAUX: Getters spécifiques pioche
    public long getCoinsViaPickaxe() {
        synchronized (dataLock) {
            return coinsViaPickaxe;
        }
    }

    public long getTokensViaPickaxe() {
        synchronized (dataLock) {
            return tokensViaPickaxe;
        }
    }

    public long getExperienceViaPickaxe() {
        synchronized (dataLock) {
            return experienceViaPickaxe;
        }
    }

    public long getCombustionLevel() {
        synchronized (dataLock) {
            return combustionLevel;
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

    public int getLastMinuteAutoUpgrades() {
        synchronized (dataLock) {
            return lastMinuteAutoUpgrades;
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

    public long getTotalKeysObtained() {
        synchronized (dataLock) {
            return totalKeysObtained;
        }
    }

    // Autres getters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public long getTotalBlocksMined() { return totalBlocksMined; }
    public long getTotalBlocksDestroyed() { return totalBlocksDestroyed; }
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
     * Vérifie si le joueur a été actif cette minute
     */
    public boolean hasMinedThisMinute() {
        synchronized (dataLock) {
            return lastMinuteBlocksMined > 0 || lastMinuteBlocksDestroyed > 0;
        }
    }
}