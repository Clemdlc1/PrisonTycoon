package fr.prisontycoon.managers;

import com.earth2me.essentials.User;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import net.ess3.api.IEssentials;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Gestionnaire économique intégré avec Vault et EssentialsX
 * INTÉGRATION NATIVE - Remplace l'ancien EconomyManager
 *
 * Système économique hybride:
 * - Économie interne du plugin (coins/tokens)
 * - Intégration Vault (synchronisation bidirectionnelle)
 * - Intégration EssentialsX (balance, transactions)
 * - Gestion des niveaux et expérience
 * - Cache intelligent pour les performances
 */
public class EconomyManager {

    private final PrisonTycoon plugin;

    // Configuration de synchronisation depuis config.yml
    private final boolean vaultSyncEnabled;
    private final boolean essentialsSyncEnabled;
    private final double vaultConversionRate;
    private final double essentialsMultiplier;

    // Cache des dernières synchronisations pour éviter le spam
    private final ConcurrentMap<String, Long> lastVaultSync = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastEssentialsSync = new ConcurrentHashMap<>();

    // Constantes pour l'expérience
    private static final int BASE_XP_PER_LEVEL = 1000;
    private static final double XP_MULTIPLIER = 1.5;

    public EconomyManager(PrisonTycoon plugin) {
        this.plugin = plugin;

        // Charge la configuration
        this.vaultSyncEnabled = plugin.getConfig().getBoolean("hooks.vault.sync-enabled", true);
        this.essentialsSyncEnabled = plugin.getConfig().getBoolean("hooks.essentialsx.sync-economy", true);
        this.vaultConversionRate = plugin.getConfig().getDouble("hooks.vault.token-conversion-rate", 0.1);
        this.essentialsMultiplier = plugin.getConfig().getDouble("hooks.essentialsx.economy-multiplier", 1.0);

        plugin.getPluginLogger().info("EconomyManager intégré initialisé:");
        plugin.getPluginLogger().info("- Vault sync: " + vaultSyncEnabled);
        plugin.getPluginLogger().info("- EssentialsX sync: " + essentialsSyncEnabled);
    }

    /**
     * Ajoute des coins à un joueur avec synchronisation automatique
     */
    public void addCoins(@NotNull Player player, long amount) {
        if (amount <= 0) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long oldCoins = playerData.getCoins();

        // Ajoute au système interne
        playerData.setCoins(oldCoins + amount);

        // Synchronisation Vault
        if (plugin.isVaultEnabled() && vaultSyncEnabled) {
            synchronizeWithVault(player, playerData);
        }

        // Synchronisation EssentialsX
        if (plugin.isEssentialsEnabled() && essentialsSyncEnabled) {
            synchronizeWithEssentials(player, playerData);
        }

        plugin.getPluginLogger().debug("Coins ajoutés à " + player.getName() + ": +" + amount +
                " (total: " + playerData.getCoins() + ")");
    }

    /**
     * Retire des coins d'un joueur avec vérifications
     */
    public boolean removeCoins(@NotNull Player player, long amount) {
        if (amount <= 0) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentCoins = playerData.getCoins();

        if (currentCoins < amount) {
            return false; // Pas assez de coins
        }

        // Retire du système interne
        playerData.setCoins(currentCoins - amount);

        // Synchronisation Vault
        if (plugin.isVaultEnabled() && vaultSyncEnabled) {
            synchronizeWithVault(player, playerData);
        }

        // Synchronisation EssentialsX
        if (plugin.isEssentialsEnabled() && essentialsSyncEnabled) {
            synchronizeWithEssentials(player, playerData);
        }

        plugin.getPluginLogger().debug("Coins retirés de " + player.getName() + ": -" + amount +
                " (total: " + playerData.getCoins() + ")");

        return true;
    }

    /**
     * Ajoute des tokens à un joueur
     */
    public void addTokens(@NotNull Player player, long amount) {
        if (amount <= 0) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long oldTokens = playerData.getTokens();

        playerData.setTokens(oldTokens + amount);

        // Les tokens influencent aussi l'économie Vault (conversion)
        if (plugin.isVaultEnabled() && vaultSyncEnabled) {
            synchronizeWithVault(player, playerData);
        }

        plugin.getPluginLogger().debug("Tokens ajoutés à " + player.getName() + ": +" + amount +
                " (total: " + playerData.getTokens() + ")");
    }

    /**
     * Retire des tokens avec vérifications
     */
    public boolean removeTokens(@NotNull Player player, long amount) {
        if (amount <= 0) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentTokens = playerData.getTokens();

        if (currentTokens < amount) {
            return false; // Pas assez de tokens
        }

        playerData.setTokens(currentTokens - amount);

        // Synchronisation Vault
        if (plugin.isVaultEnabled() && vaultSyncEnabled) {
            synchronizeWithVault(player, playerData);
        }

        plugin.getPluginLogger().debug("Tokens retirés de " + player.getName() + ": -" + amount +
                " (total: " + playerData.getTokens() + ")");

        return true;
    }

    /**
     * Ajoute de l'expérience avec mise à jour du niveau vanilla
     */
    public void addExperience(@NotNull Player player, long amount) {
        if (amount <= 0) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long oldXP = playerData.getExperience();
        int oldLevel = calculateLevelFromExperience(oldXP);

        playerData.setExperience(oldXP + amount);

        int newLevel = calculateLevelFromExperience(playerData.getExperience());

        // Met à jour l'expérience vanilla
        updateVanillaExpFromCustom(player, playerData.getExperience());

        // Vérifie s'il y a eu un level up
        if (newLevel > oldLevel) {
            handleLevelUp(player, oldLevel, newLevel);
        }

        plugin.getPluginLogger().debug("XP ajoutée à " + player.getName() + ": +" + amount +
                " (niveau " + oldLevel + " -> " + newLevel + ")");
    }

    /**
     * Gère le level up d'un joueur
     */
    private void handleLevelUp(@NotNull Player player, int oldLevel, int newLevel) {
        // Récompenses de niveau (coins bonus)
        long coinsReward = newLevel * 100L;
        addCoins(player, coinsReward);

        // Effets visuels/sons
        player.sendMessage("§6✨ NIVEAU SUPÉRIEUR! §e" + oldLevel + " §7→ §a" + newLevel);
        player.sendMessage("§7Récompense: §6+" + formatNumber(coinsReward) + " coins");

        // Son et particules
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        plugin.getPluginLogger().info("Level up: " + player.getName() + " " + oldLevel + " -> " + newLevel);
    }

    /**
     * Synchronise avec Vault Economy
     * INTÉGRATION NATIVE VAULT
     */
    private void synchronizeWithVault(@NotNull Player player, @NotNull PlayerData playerData) {
        if (!plugin.isVaultEnabled()) return;

        String playerName = player.getName();
        Long lastSync = lastVaultSync.get(playerName);

        // Cooldown de 30 secondes pour éviter le spam
        if (lastSync != null && System.currentTimeMillis() - lastSync < 30000) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Economy vault = plugin.getVaultEconomy();
                if (vault == null) return;

                // Calcule l'équivalent Vault (coins + tokens convertis)
                long totalCoins = playerData.getCoins();
                long totalTokens = playerData.getTokens();
                double vaultEquivalent = totalCoins + (totalTokens * vaultConversionRate);

                // Met à jour Vault
                double currentVaultBalance = vault.getBalance(player);
                if (Math.abs(currentVaultBalance - vaultEquivalent) > 0.01) {

                    // S'assure que le compte existe
                    if (!vault.hasAccount(player)) {
                        vault.createPlayerAccount(player);
                    }

                    // Ajuste le solde
                    double difference = vaultEquivalent - currentVaultBalance;
                    EconomyResponse response;

                    if (difference > 0) {
                        response = vault.depositPlayer(player, difference);
                    } else {
                        response = vault.withdrawPlayer(player, Math.abs(difference));
                    }

                    if (response.transactionSuccess()) {
                        plugin.getPluginLogger().debug("Vault synchronisé pour " + playerName +
                                ": " + totalCoins + "c+" + totalTokens + "t -> $" + vaultEquivalent);
                    } else {
                        plugin.getPluginLogger().warning("Erreur sync Vault " + playerName + ": " + response.errorMessage);
                    }
                }

                lastVaultSync.put(playerName, System.currentTimeMillis());

            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur synchronisation Vault " + playerName + ": " + e.getMessage());
            }
        });
    }

    /**
     * Synchronise avec EssentialsX
     * INTÉGRATION NATIVE ESSENTIALSX
     */
    private void synchronizeWithEssentials(@NotNull Player player, @NotNull PlayerData playerData) {
        if (!plugin.isEssentialsEnabled()) return;

        String playerName = player.getName();
        Long lastSync = lastEssentialsSync.get(playerName);

        // Cooldown de 30 secondes pour éviter le spam
        if (lastSync != null && System.currentTimeMillis() - lastSync < 30000) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                IEssentials essentials = plugin.getEssentialsAPI();
                if (essentials == null) return;

                User essentialsUser = essentials.getUser(player.getUniqueId());
                if (essentialsUser == null) return;

                // Calcule l'équivalent EssentialsX
                long totalCoins = playerData.getCoins();
                BigDecimal essentialsEquivalent = BigDecimal.valueOf(totalCoins * essentialsMultiplier);

                // Met à jour EssentialsX
                BigDecimal currentBalance = essentialsUser.getMoney();
                if (currentBalance.compareTo(essentialsEquivalent) != 0) {
                    essentialsUser.setMoney(essentialsEquivalent);

                    plugin.getPluginLogger().debug("EssentialsX synchronisé pour " + playerName +
                            ": " + totalCoins + " coins -> $" + essentialsEquivalent);
                }

                lastEssentialsSync.put(playerName, System.currentTimeMillis());

            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur synchronisation EssentialsX " + playerName + ": " + e.getMessage());
            }
        });
    }

    /**
     * Met à jour l'expérience vanilla depuis l'expérience personnalisée
     * MÉTHODE CONSERVÉE DE L'ANCIEN SYSTÈME
     */
    public void updateVanillaExpFromCustom(@NotNull Player player, long customExperience) {
        int vanillaLevel = calculateLevelFromExperience(customExperience);

        // Calcule la progression dans le niveau actuel
        long currentLevelXP = getExperienceForLevel(vanillaLevel);
        long nextLevelXP = getExperienceForLevel(vanillaLevel + 1);
        long expInCurrentLevel = customExperience - currentLevelXP;
        long expNeededForNextLevel = nextLevelXP - currentLevelXP;

        float progress = expNeededForNextLevel > 0 ? (float) expInCurrentLevel / expNeededForNextLevel : 0.0f;

        // Limite les valeurs
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        vanillaLevel = Math.max(0, Math.min(21863, vanillaLevel));

        // Vérifie si une mise à jour est nécessaire
        boolean shouldUpdate = false;
        if (player.getLevel() != vanillaLevel) {
            shouldUpdate = true;
        } else if (Math.abs(player.getExp() - progress) > 0.01f) {
            shouldUpdate = true;
        }

        if (shouldUpdate) {
            try {
                player.setLevel(vanillaLevel);
                player.setExp(progress);

                plugin.getPluginLogger().debug("XP vanilla mise à jour pour " + player.getName() +
                        ": niveau " + vanillaLevel + " (" + String.format("%.1f", progress * 100) + "%)");

            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur mise à jour XP vanilla " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Calcule le niveau depuis l'expérience
     * MÉTHODE CONSERVÉE DE L'ANCIEN SYSTÈME
     */
    public int calculateLevelFromExperience(long experience) {
        if (experience <= 0) return 0;

        int level = 0;
        long totalXpForLevel = 0;

        while (totalXpForLevel <= experience) {
            level++;
            totalXpForLevel = getExperienceForLevel(level);
            if (level > 10000) break; // Protection contre les boucles infinies
        }

        return Math.max(0, level - 1);
    }

    /**
     * Calcule l'expérience nécessaire pour un niveau donné
     * MÉTHODE CONSERVÉE DE L'ANCIEN SYSTÈME
     */
    public long getExperienceForLevel(int level) {
        if (level <= 0) return 0;

        long totalXp = 0;
        for (int i = 1; i <= level; i++) {
            totalXp += (long) (BASE_XP_PER_LEVEL * Math.pow(XP_MULTIPLIER, i - 1));
        }

        return totalXp;
    }

    /**
     * Formate un nombre pour l'affichage
     */
    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        } else {
            return String.valueOf(number);
        }
    }

    /**
     * Obtient le solde Vault d'un joueur (intégration native)
     */
    public double getVaultBalance(@NotNull OfflinePlayer player) {
        if (!plugin.isVaultEnabled()) return 0.0;

        try {
            Economy vault = plugin.getVaultEconomy();
            return vault != null ? vault.getBalance(player) : 0.0;
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Erreur lecture balance Vault " + player.getName() + ": " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Obtient le solde EssentialsX d'un joueur (intégration native)
     */
    public BigDecimal getEssentialsBalance(@NotNull Player player) {
        if (!plugin.isEssentialsEnabled()) return BigDecimal.ZERO;

        try {
            IEssentials essentials = plugin.getEssentialsAPI();
            if (essentials != null) {
                User user = essentials.getUser(player.getUniqueId());
                return user != null ? user.getMoney() : BigDecimal.ZERO;
            }
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Erreur lecture balance EssentialsX " + player.getName() + ": " + e.getMessage());
        }

        return BigDecimal.ZERO;
    }

    /**
     * Synchronise un joueur au login (intégrations natives)
     */
    public void synchronizePlayerOnLogin(@NotNull Player player) {
        if (!vaultSyncEnabled && !essentialsSyncEnabled) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Synchronisation différée pour éviter les lags au login
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            if (vaultSyncEnabled) {
                synchronizeWithVault(player, playerData);
            }
            if (essentialsSyncEnabled) {
                synchronizeWithEssentials(player, playerData);
            }
        }, 60L); // 3 secondes de délai
    }

    /**
     * Nettoie les caches d'un joueur qui se déconnecte
     */
    public void cleanupPlayer(@NotNull Player player) {
        String playerName = player.getName();
        lastVaultSync.remove(playerName);
        lastEssentialsSync.remove(playerName);
    }

    /**
     * Recharge la configuration
     */
    public void reloadConfig() {
        // Les nouvelles valeurs seront prises en compte au prochain démarrage
        plugin.getPluginLogger().info("Configuration économique rechargée au prochain redémarrage");
    }

    /**
     * Obtient des statistiques sur l'économie
     */
    public String getEconomyStats() {
        int vaultSyncs = lastVaultSync.size();
        int essentialsSyncs = lastEssentialsSync.size();

        return "Économie: Vault(" + vaultSyncs + " syncs) EssentialsX(" + essentialsSyncs + " syncs)";
    }
}