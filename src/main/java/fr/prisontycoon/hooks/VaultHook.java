package fr.prisontycoon.hooks;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hook optimisé pour Vault Economy
 * Système économique hybride qui synchronise avec l'économie interne du plugin
 *
 * Fonctionnalités:
 * - Synchronisation bidirectionnelle avec l'économie Vault
 * - Cache intelligent pour les performances
 * - Support des transactions asynchrones
 * - Intégration transparente avec EconomyManager
 * - Gestion des erreurs et rollback automatique
 * - Support multi-devises (coins/tokens)
 */
public class VaultHook {

    private final PrisonTycoon plugin;
    private Economy vaultEconomy;

    // Cache des balances pour éviter les accès fréquents à la DB
    private final ConcurrentMap<UUID, CachedBalance> balanceCache = new ConcurrentHashMap<>();

    // Configuration de synchronisation
    private final boolean syncWithVault;
    private final boolean preferVaultBalance;
    private final double conversionRate; // Taux de conversion tokens -> vault money

    // État du hook
    private boolean initialized = false;

    public VaultHook(PrisonTycoon plugin) {
        this.plugin = plugin;

        // Configuration depuis config.yml
        this.syncWithVault = plugin.getConfig().getBoolean("hooks.vault.sync-enabled", true);
        this.preferVaultBalance = plugin.getConfig().getBoolean("hooks.vault.prefer-vault-balance", false);
        this.conversionRate = plugin.getConfig().getDouble("hooks.vault.token-conversion-rate", 0.1);
    }

    /**
     * Initialise le hook Vault
     */
    public boolean initialize() {
        try {
            // Recherche le service Economy de Vault
            RegisteredServiceProvider<Economy> economyProvider =
                    plugin.getServer().getServicesManager().getRegistration(Economy.class);

            if (economyProvider == null) {
                plugin.getPluginLogger().warning("§eAucune économie Vault trouvée");
                return false;
            }

            vaultEconomy = economyProvider.getProvider();

            if (vaultEconomy == null) {
                plugin.getPluginLogger().warning("§eÉconomie Vault nulle");
                return false;
            }

            plugin.getPluginLogger().info("§a✓ Vault Economy: " + vaultEconomy.getName());

            // Test de fonctionnement
            if (!testVaultConnection()) {
                plugin.getPluginLogger().warning("§eTest de connexion Vault échoué");
                return false;
            }

            // Synchronise les joueurs en ligne si activé
            if (syncWithVault) {
                synchronizeOnlinePlayersAsync();
            }

            initialized = true;
            plugin.getPluginLogger().info("§a✓ Hook Vault initialisé (sync: " + syncWithVault + ")");

            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors de l'initialisation Vault:");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Teste la connexion avec Vault
     */
    private boolean testVaultConnection() {
        try {
            // Test basique : vérifie si l'économie fonctionne
            boolean hasAccount = vaultEconomy.hasAccount("test_account_vault_hook");
            plugin.getPluginLogger().debug("Test Vault réussi");
            return true;
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eTest Vault échoué: " + e.getMessage());
            return false;
        }
    }

    /**
     * Synchronise tous les joueurs en ligne
     */
    private void synchronizeOnlinePlayersAsync() {
        CompletableFuture.runAsync(() -> {
            int synced = 0;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                try {
                    synchronizePlayerBalance(player);
                    synced++;
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("§eErreur sync " + player.getName() + ": " + e.getMessage());
                }
            }
            plugin.getPluginLogger().info("§aSynchronisation Vault: " + synced + " joueurs");
        });
    }

    /**
     * Vérifie si Vault est disponible et initialisé
     */
    public boolean isAvailable() {
        return initialized && vaultEconomy != null;
    }

    /**
     * Obtient le solde Vault d'un joueur
     */
    public double getVaultBalance(@NotNull OfflinePlayer player) {
        if (!isAvailable()) return 0.0;

        try {
            return vaultEconomy.getBalance(player);
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur lecture solde Vault " + player.getName() + ": " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * Obtient le solde Vault d'un joueur avec cache
     */
    public double getCachedVaultBalance(@NotNull UUID uuid) {
        CachedBalance cached = balanceCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.vaultBalance;
        }

        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        double balance = getVaultBalance(player);

        // Met à jour le cache
        updateBalanceCache(uuid, balance, -1);

        return balance;
    }

    /**
     * Définit le solde Vault d'un joueur
     */
    public boolean setVaultBalance(@NotNull OfflinePlayer player, double amount) {
        if (!isAvailable()) return false;

        try {
            // Assure-toi que le joueur a un compte
            if (!vaultEconomy.hasAccount(player)) {
                vaultEconomy.createPlayerAccount(player);
            }

            double currentBalance = vaultEconomy.getBalance(player);
            double difference = amount - currentBalance;

            EconomyResponse response;
            if (difference > 0) {
                response = vaultEconomy.depositPlayer(player, difference);
            } else if (difference < 0) {
                response = vaultEconomy.withdrawPlayer(player, Math.abs(difference));
            } else {
                return true; // Pas de changement nécessaire
            }

            if (response.transactionSuccess()) {
                // Met à jour le cache
                updateBalanceCache(player.getUniqueId(), amount, -1);
                plugin.getPluginLogger().debug("Solde Vault mis à jour pour " + player.getName() + ": " + amount);
                return true;
            } else {
                plugin.getPluginLogger().warning("§eÉchec transaction Vault " + player.getName() + ": " + response.errorMessage);
                return false;
            }

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur définition solde Vault " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Ajoute de l'argent au solde Vault
     */
    public boolean depositVault(@NotNull OfflinePlayer player, double amount) {
        if (!isAvailable() || amount <= 0) return false;

        try {
            EconomyResponse response = vaultEconomy.depositPlayer(player, amount);

            if (response.transactionSuccess()) {
                // Met à jour le cache
                CachedBalance cached = balanceCache.get(player.getUniqueId());
                if (cached != null) {
                    cached.vaultBalance += amount;
                    cached.lastUpdate = System.currentTimeMillis();
                }

                plugin.getPluginLogger().debug("Dépôt Vault " + player.getName() + ": +" + amount);
                return true;
            } else {
                plugin.getPluginLogger().warning("§eÉchec dépôt Vault " + player.getName() + ": " + response.errorMessage);
                return false;
            }

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur dépôt Vault " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Retire de l'argent du solde Vault
     */
    public boolean withdrawVault(@NotNull OfflinePlayer player, double amount) {
        if (!isAvailable() || amount <= 0) return false;

        try {
            EconomyResponse response = vaultEconomy.withdrawPlayer(player, amount);

            if (response.transactionSuccess()) {
                // Met à jour le cache
                CachedBalance cached = balanceCache.get(player.getUniqueId());
                if (cached != null) {
                    cached.vaultBalance -= amount;
                    cached.lastUpdate = System.currentTimeMillis();
                }

                plugin.getPluginLogger().debug("Retrait Vault " + player.getName() + ": -" + amount);
                return true;
            } else {
                plugin.getPluginLogger().warning("§eÉchec retrait Vault " + player.getName() + ": " + response.errorMessage);
                return false;
            }

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur retrait Vault " + player.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Synchronise le solde d'un joueur entre Vault et le plugin
     */
    public void synchronizePlayerBalance(@NotNull Player player) {
        if (!isAvailable() || !syncWithVault) return;

        try {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

            double vaultBalance = getVaultBalance(player);
            long pluginCoins = playerData.getCoins();
            long pluginTokens = playerData.getTokens();

            if (preferVaultBalance) {
                // Vault a la priorité - met à jour le plugin
                synchronizeFromVaultToPlugin(player, vaultBalance, playerData);
            } else {
                // Plugin a la priorité - met à jour Vault
                synchronizeFromPluginToVault(player, pluginCoins, pluginTokens);
            }

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur synchronisation " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Synchronise depuis Vault vers le plugin
     */
    private void synchronizeFromVaultToPlugin(@NotNull Player player, double vaultBalance, @NotNull PlayerData playerData) {
        // Convertit le solde Vault en coins + tokens
        long totalCoins = (long) Math.floor(vaultBalance);
        long totalTokens = (long) Math.floor((vaultBalance - totalCoins) / conversionRate);

        // Met à jour les données du plugin
        long oldCoins = playerData.getCoins();
        long oldTokens = playerData.getTokens();

        playerData.setCoins(totalCoins);
        playerData.setTokens(totalTokens);

        plugin.getPluginLogger().debug("Sync Vault→Plugin " + player.getName() +
                ": " + oldCoins + "c+" + oldTokens + "t → " + totalCoins + "c+" + totalTokens + "t");
    }

    /**
     * Synchronise depuis le plugin vers Vault
     */
    private void synchronizeFromPluginToVault(@NotNull Player player, long coins, long tokens) {
        // Convertit coins + tokens en solde Vault
        double vaultEquivalent = coins + (tokens * conversionRate);

        // Met à jour Vault
        boolean success = setVaultBalance(player, vaultEquivalent);

        if (success) {
            plugin.getPluginLogger().debug("Sync Plugin→Vault " + player.getName() +
                    ": " + coins + "c+" + tokens + "t → " + vaultEquivalent + "$");
        }
    }

    /**
     * Convertit les tokens en équivalent Vault
     */
    public double tokensToVaultMoney(long tokens) {
        return tokens * conversionRate;
    }

    /**
     * Convertit l'argent Vault en tokens
     */
    public long vaultMoneyToTokens(double money) {
        return (long) Math.floor(money / conversionRate);
    }

    /**
     * Transaction Vault sécurisée avec rollback
     */
    public CompletableFuture<Boolean> safeVaultTransaction(@NotNull Player player, double amount, @NotNull TransactionType type) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isAvailable()) return false;

            double initialBalance = getVaultBalance(player);
            boolean success = false;

            try {
                switch (type) {
                    case DEPOSIT -> success = depositVault(player, amount);
                    case WITHDRAW -> success = withdrawVault(player, amount);
                    case SET -> success = setVaultBalance(player, amount);
                }

                if (success) {
                    plugin.getPluginLogger().debug("Transaction Vault réussie: " + player.getName() + " " + type + " " + amount);
                } else {
                    plugin.getPluginLogger().warning("§eTransaction Vault échouée: " + player.getName() + " " + type + " " + amount);
                }

                return success;

            } catch (Exception e) {
                plugin.getPluginLogger().severe("§cErreur transaction Vault " + player.getName() + ":");
                e.printStackTrace();

                // Tentative de rollback
                try {
                    setVaultBalance(player, initialBalance);
                    plugin.getPluginLogger().info("§aRollback réussi pour " + player.getName());
                } catch (Exception rollbackError) {
                    plugin.getPluginLogger().severe("§cRollback échoué pour " + player.getName() + ":");
                    rollbackError.printStackTrace();
                }

                return false;
            }
        });
    }

    /**
     * Met à jour le cache des balances
     */
    private void updateBalanceCache(@NotNull UUID uuid, double vaultBalance, long pluginBalance) {
        CachedBalance cached = balanceCache.computeIfAbsent(uuid, k -> new CachedBalance());
        cached.vaultBalance = vaultBalance;
        if (pluginBalance >= 0) {
            cached.pluginBalance = pluginBalance;
        }
        cached.lastUpdate = System.currentTimeMillis();
    }

    /**
     * Nettoie le cache d'un joueur
     */
    public void cleanupPlayerCache(@NotNull UUID uuid) {
        balanceCache.remove(uuid);
    }

    /**
     * Nettoie le cache expiré
     */
    public void cleanupExpiredCache() {
        balanceCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Nettoie toutes les ressources
     */
    public void cleanup() {
        balanceCache.clear();
        vaultEconomy = null;
        initialized = false;
    }

    /**
     * Obtient des statistiques sur le cache
     */
    public String getCacheStats() {
        int total = balanceCache.size();
        int expired = (int) balanceCache.values().stream().filter(CachedBalance::isExpired).count();
        return "Cache Vault: " + total + " entrées (" + expired + " expirées)";
    }

    // === GETTERS ===

    @Nullable
    public Economy getVaultEconomy() {
        return vaultEconomy;
    }

    public boolean isSyncEnabled() {
        return syncWithVault;
    }

    public double getConversionRate() {
        return conversionRate;
    }

    /**
     * Types de transaction Vault
     */
    public enum TransactionType {
        DEPOSIT, WITHDRAW, SET
    }

    /**
     * Cache des balances pour les performances
     */
    private static class CachedBalance {
        double vaultBalance = 0.0;
        long pluginBalance = 0L;
        long lastUpdate = System.currentTimeMillis();

        // Cache valide pendant 2 minutes
        boolean isExpired() {
            return System.currentTimeMillis() - lastUpdate > 120000;
        }
    }
}