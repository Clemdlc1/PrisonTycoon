package fr.prisontycoon.hooks;

import com.earth2me.essentials.Essentials;
import com.earth2me.essentials.User;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import net.ess3.api.IEssentials;
import net.ess3.api.MaxMoneyException;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hook optimisé pour EssentialsX
 * Intégration complète avec l'économie, homes, warps et fonctionnalités avancées
 * <p>
 * Fonctionnalités:
 * - Économie synchronisée avec Vault et le plugin
 * - Gestion des homes personnalisés pour les mines
 * - Warps automatiques pour les mines
 * - Intégration des kits VIP
 * - Gestion des cooldowns avancés
 * - Synchronisation des balances
 * - Support des transactions sécurisées
 */
public class EssentialsXHook {

    private final PrisonTycoon plugin;
    // Cache des utilisateurs EssentialsX
    private final ConcurrentMap<UUID, CachedEssentialsUser> userCache = new ConcurrentHashMap<>();
    // Configuration de l'intégration
    private final boolean syncEconomy;
    private final boolean syncHomes;
    private final boolean syncWarps;
    private final boolean createMineWarps;
    private final double economyMultiplier;
    // Cooldowns pour éviter le spam
    private final ConcurrentMap<UUID, Long> lastBalanceSync = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastHomeUpdate = new ConcurrentHashMap<>();
    private IEssentials essentials;
    private Essentials essentialsPlugin;
    // État du hook
    private boolean initialized = false;

    public EssentialsXHook(PrisonTycoon plugin) {
        this.plugin = plugin;

        // Configuration depuis config.yml
        this.syncEconomy = plugin.getConfig().getBoolean("hooks.essentialsx.sync-economy", true);
        this.syncHomes = plugin.getConfig().getBoolean("hooks.essentialsx.sync-homes", true);
        this.syncWarps = plugin.getConfig().getBoolean("hooks.essentialsx.sync-warps", true);
        this.createMineWarps = plugin.getConfig().getBoolean("hooks.essentialsx.create-mine-warps", true);
        this.economyMultiplier = plugin.getConfig().getDouble("hooks.essentialsx.economy-multiplier", 1.0);
    }

    /**
     * Initialise le hook EssentialsX
     */
    public boolean initialize() {
        try {
            // Recherche EssentialsX
            var essentialsPlugin = plugin.getServer().getPluginManager().getPlugin("Essentials");
            if (essentialsPlugin == null) {
                plugin.getPluginLogger().warning("§eEssentialsX non détecté");
                return false;
            }

            if (!(essentialsPlugin instanceof Essentials)) {
                plugin.getPluginLogger().warning("§ePlugin Essentials invalide");
                return false;
            }

            this.essentialsPlugin = (Essentials) essentialsPlugin;
            this.essentials = this.essentialsPlugin;

            plugin.getPluginLogger().info("§a✓ EssentialsX " + essentialsPlugin.getDescription().getVersion());

            // Test de fonctionnement
            if (!testEssentialsConnection()) {
                plugin.getPluginLogger().warning("§eTest de connexion EssentialsX échoué");
                return false;
            }

            // Configuration initiale
            if (createMineWarps) {
                createMineWarpsAsync();
            }

            if (syncEconomy) {
                synchronizeOnlinePlayersEconomyAsync();
            }

            // Démarre les tâches de maintenance
            startMaintenanceTasks();

            initialized = true;
            plugin.getPluginLogger().info("§a✓ Hook EssentialsX initialisé");
            plugin.getPluginLogger().info("§7- Économie: " + (syncEconomy ? "§aOUI" : "§cNON"));
            plugin.getPluginLogger().info("§7- Homes: " + (syncHomes ? "§aOUI" : "§cNON"));
            plugin.getPluginLogger().info("§7- Warps: " + (syncWarps ? "§aOUI" : "§cNON"));

            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors de l'initialisation EssentialsX:");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Teste la connexion avec EssentialsX
     */
    private boolean testEssentialsConnection() {
        try {
            // Test basique de l'API EssentialsX
            essentials.getSettings();
            plugin.getPluginLogger().debug("Test EssentialsX réussi");
            return true;
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eTest EssentialsX échoué: " + e.getMessage());
            return false;
        }
    }

    /**
     * Vérifie si EssentialsX est disponible
     */
    public boolean isAvailable() {
        return initialized && essentials != null;
    }

    /**
     * Obtient un utilisateur EssentialsX avec cache
     */
    @Nullable
    private User getEssentialsUser(@NotNull UUID uuid) {
        CachedEssentialsUser cached = userCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.getUser();
        }

        try {
            User user = essentials.getUser(uuid);
            if (user != null) {
                userCache.put(uuid, new CachedEssentialsUser(user));
            }
            return user;
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Erreur obtention utilisateur EssentialsX " + uuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtient le solde EssentialsX d'un joueur
     */
    public BigDecimal getEssentialsBalance(@NotNull UUID uuid) {
        if (!isAvailable()) return BigDecimal.ZERO;

        try {
            User user = getEssentialsUser(uuid);
            return user != null ? user.getMoney() : BigDecimal.ZERO;
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur lecture solde EssentialsX " + uuid + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Définit le solde EssentialsX d'un joueur
     */
    public boolean setEssentialsBalance(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        if (!isAvailable()) return false;

        try {
            User user = getEssentialsUser(uuid);
            if (user == null) return false;

            user.setMoney(amount);

            // Met à jour le cache
            userCache.remove(uuid);

            plugin.getPluginLogger().debug("Solde EssentialsX mis à jour pour " + uuid + ": " + amount);
            return true;

        } catch (MaxMoneyException e) {
            plugin.getPluginLogger().warning("§eMontant trop élevé pour EssentialsX " + uuid + ": " + amount);
            return false;
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur définition solde EssentialsX " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Ajoute de l'argent au solde EssentialsX
     */
    public boolean addEssentialsMoney(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        if (!isAvailable() || amount.compareTo(BigDecimal.ZERO) <= 0) return false;

        try {
            User user = getEssentialsUser(uuid);
            if (user == null) return false;

            user.giveMoney(amount);

            // Met à jour le cache
            userCache.remove(uuid);

            plugin.getPluginLogger().debug("Argent EssentialsX ajouté pour " + uuid + ": +" + amount);
            return true;

        } catch (MaxMoneyException e) {
            plugin.getPluginLogger().warning("§eAjout impossible, limite atteinte pour " + uuid + ": " + amount);
            return false;
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur ajout argent EssentialsX " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Retire de l'argent du solde EssentialsX
     */
    public boolean takeEssentialsMoney(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        if (!isAvailable() || amount.compareTo(BigDecimal.ZERO) <= 0) return false;

        try {
            User user = getEssentialsUser(uuid);
            if (user == null) return false;

            if (user.getMoney().compareTo(amount) < 0) {
                plugin.getPluginLogger().debug("Solde insuffisant EssentialsX " + uuid + ": " + user.getMoney() + " < " + amount);
                return false;
            }

            user.takeMoney(amount);

            // Met à jour le cache
            userCache.remove(uuid);

            plugin.getPluginLogger().debug("Argent EssentialsX retiré pour " + uuid + ": -" + amount);
            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur retrait argent EssentialsX " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Synchronise l'économie entre EssentialsX et le plugin
     */
    public void synchronizePlayerEconomy(@NotNull Player player) {
        if (!isAvailable() || !syncEconomy) return;

        UUID uuid = player.getUniqueId();

        // Vérifie le cooldown
        Long lastSync = lastBalanceSync.get(uuid);
        if (lastSync != null && System.currentTimeMillis() - lastSync < 30000) { // 30 secondes
            return;
        }

        try {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(uuid);
            BigDecimal essentialsBalance = getEssentialsBalance(uuid);

            // Convertit les coins du plugin en argent EssentialsX
            long pluginCoins = playerData.getCoins();
            BigDecimal pluginEquivalent = BigDecimal.valueOf(pluginCoins * economyMultiplier);

            // Synchronise vers EssentialsX si différent
            if (essentialsBalance.compareTo(pluginEquivalent) != 0) {
                setEssentialsBalance(uuid, pluginEquivalent);

                plugin.getPluginLogger().debug("Économie synchronisée " + player.getName() +
                        ": " + pluginCoins + " coins → " + pluginEquivalent + "$");
            }

            lastBalanceSync.put(uuid, System.currentTimeMillis());

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur synchronisation économie " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Synchronise l'économie de tous les joueurs en ligne
     */
    private void synchronizeOnlinePlayersEconomyAsync() {
        if (!syncEconomy) return;

        CompletableFuture.runAsync(() -> {
            int synced = 0;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                try {
                    synchronizePlayerEconomy(player);
                    synced++;
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("§eErreur sync économie " + player.getName() + ": " + e.getMessage());
                }
            }
            plugin.getPluginLogger().info("§aSynchronisation économie EssentialsX: " + synced + " joueurs");
        });
    }

    /**
     * Obtient tous les homes d'un joueur
     */
    @NotNull
    public Set<String> getPlayerHomes(@NotNull UUID uuid) {
        if (!isAvailable()) return Set.of();

        try {
            User user = getEssentialsUser(uuid);
            if (user == null) return Set.of();

            return user.getHomes();

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur obtention homes " + uuid + ": " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Obtient la location d'un home
     */
    @Nullable
    public Location getHomeLocation(@NotNull UUID uuid, @NotNull String homeName) {
        if (!isAvailable()) return null;

        try {
            User user = getEssentialsUser(uuid);
            if (user == null) return null;

            return user.getHome(homeName);

        } catch (Exception e) {
            plugin.getPluginLogger().debug("Home non trouvé " + uuid + " [" + homeName + "]: " + e.getMessage());
            return null;
        }
    }

    /**
     * Définit un home pour un joueur
     */
    public boolean setPlayerHome(@NotNull UUID uuid, @NotNull String homeName, @NotNull Location location) {
        if (!isAvailable() || !syncHomes) return false;

        try {
            User user = getEssentialsUser(uuid);
            if (user == null) return false;

            user.setHome(homeName, location);

            // Met à jour le cache
            userCache.remove(uuid);
            lastHomeUpdate.put(uuid, System.currentTimeMillis());

            plugin.getPluginLogger().debug("Home défini " + uuid + " [" + homeName + "] à " +
                    location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur définition home " + uuid + " [" + homeName + "]: " + e.getMessage());
            return false;
        }
    }

    /**
     * Supprime un home d'un joueur
     */
    public boolean deletePlayerHome(@NotNull UUID uuid, @NotNull String homeName) {
        if (!isAvailable()) return false;

        try {
            User user = getEssentialsUser(uuid);
            if (user == null) return false;

            user.delHome(homeName);

            // Met à jour le cache
            userCache.remove(uuid);

            plugin.getPluginLogger().debug("Home supprimé " + uuid + " [" + homeName + "]");
            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur suppression home " + uuid + " [" + homeName + "]: " + e.getMessage());
            return false;
        }
    }

    /**
     * Crée des homes automatiques pour les mines accessibles
     */
    public void createMineHomesForPlayer(@NotNull Player player) {
        if (!isAvailable() || !syncHomes) return;

        UUID uuid = player.getUniqueId();

        // Vérifie le cooldown
        Long lastUpdate = lastHomeUpdate.get(uuid);
        if (lastUpdate != null && System.currentTimeMillis() - lastUpdate < 300000) { // 5 minutes
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Set<String> availableMines = plugin.getMineManager().getAvailableMines(player);

                for (String mineName : availableMines) {
                    Location mineSpawn = plugin.getMineManager().getMineSpawn(mineName);
                    if (mineSpawn != null) {
                        String homeName = "mine_" + mineName.toLowerCase();
                        setPlayerHome(uuid, homeName, mineSpawn);
                    }
                }

                plugin.getPluginLogger().debug("Homes de mines créés pour " + player.getName() + ": " + availableMines.size());

            } catch (Exception e) {
                plugin.getPluginLogger().warning("§eErreur création homes mines " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Obtient tous les warps EssentialsX
     */
    @NotNull
    public Set<String> getAllWarps() {
        if (!isAvailable()) return Set.of();

        try {
            return essentials.getWarps().getList();
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur obtention warps: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Obtient la location d'un warp
     */
    @Nullable
    public Location getWarpLocation(@NotNull String warpName) {
        if (!isAvailable()) return null;

        try {
            return essentials.getWarps().getWarp(warpName);
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Warp non trouvé [" + warpName + "]: " + e.getMessage());
            return null;
        }
    }

    /**
     * Crée un warp
     */
    public boolean setWarp(@NotNull String warpName, @NotNull Location location) {
        if (!isAvailable() || !syncWarps) return false;

        try {
            essentials.getWarps().setWarp(warpName, location);

            plugin.getPluginLogger().debug("Warp créé [" + warpName + "] à " +
                    location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur création warp [" + warpName + "]: " + e.getMessage());
            return false;
        }
    }

    /**
     * Supprime un warp
     */
    public boolean deleteWarp(@NotNull String warpName) {
        if (!isAvailable()) return false;

        try {
            essentials.getWarps().removeWarp(warpName);

            plugin.getPluginLogger().debug("Warp supprimé [" + warpName + "]");
            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur suppression warp [" + warpName + "]: " + e.getMessage());
            return false;
        }
    }

    /**
     * Crée des warps automatiques pour toutes les mines
     */
    private void createMineWarpsAsync() {
        if (!createMineWarps) return;

        CompletableFuture.runAsync(() -> {
            try {
                Set<String> mineNames = plugin.getMineManager().getAllMineNames();
                int created = 0;

                for (String mineName : mineNames) {
                    Location mineSpawn = plugin.getMineManager().getMineSpawn(mineName);
                    if (mineSpawn != null) {
                        String warpName = "mine_" + mineName.toLowerCase();

                        // Vérifie si le warp existe déjà
                        if (getWarpLocation(warpName) == null) {
                            if (setWarp(warpName, mineSpawn)) {
                                created++;
                            }
                        }
                    }
                }

                plugin.getPluginLogger().info("§aWarps de mines créés: " + created + "/" + mineNames.size());

            } catch (Exception e) {
                plugin.getPluginLogger().warning("§eErreur création warps mines: " + e.getMessage());
            }
        });
    }

    /**
     * Vérifie si un joueur est AFK
     */
    public boolean isPlayerAFK(@NotNull UUID uuid) {
        if (!isAvailable()) return false;

        try {
            User user = getEssentialsUser(uuid);
            return user != null && user.isAfk();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Obtient le temps AFK d'un joueur
     */
    public long getPlayerAFKTime(@NotNull UUID uuid) {
        if (!isAvailable()) return 0;

        try {
            User user = getEssentialsUser(uuid);
            return user != null ? user.getAfkSince() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Vérifie si un joueur est en mode vanish
     */
    public boolean isPlayerVanished(@NotNull UUID uuid) {
        if (!isAvailable()) return false;

        try {
            User user = getEssentialsUser(uuid);
            return user != null && user.isVanished();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Démarre les tâches de maintenance
     */
    private void startMaintenanceTasks() {
        // Nettoyage du cache toutes les 10 minutes
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupExpiredCache();
        }, 12000L, 12000L); // 10 minutes

        // Synchronisation économique toutes les 5 minutes
        if (syncEconomy) {
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                synchronizeOnlinePlayersEconomyAsync();
            }, 6000L, 6000L); // 5 minutes
        }
    }

    /**
     * Nettoie le cache expiré
     */
    public void cleanupExpiredCache() {
        userCache.entrySet().removeIf(entry -> entry.getValue().isExpired());

        // Nettoie aussi les cooldowns anciens
        long now = System.currentTimeMillis();
        lastBalanceSync.entrySet().removeIf(entry -> now - entry.getValue() > 1800000); // 30 minutes
        lastHomeUpdate.entrySet().removeIf(entry -> now - entry.getValue() > 1800000);
    }

    /**
     * Nettoie les données d'un joueur
     */
    public void cleanupPlayer(@NotNull UUID uuid) {
        userCache.remove(uuid);
        lastBalanceSync.remove(uuid);
        lastHomeUpdate.remove(uuid);
    }

    /**
     * Obtient des statistiques sur le cache
     */
    public String getCacheStats() {
        int users = userCache.size();
        int expired = (int) userCache.values().stream().filter(CachedEssentialsUser::isExpired).count();
        return "Cache EssentialsX: " + users + " utilisateurs (" + expired + " expirés)";
    }

    /**
     * Nettoie toutes les ressources
     */
    public void cleanup() {
        userCache.clear();
        lastBalanceSync.clear();
        lastHomeUpdate.clear();
        essentials = null;
        essentialsPlugin = null;
        initialized = false;
    }

    // === GETTERS ===

    @Nullable
    public IEssentials getEssentials() {
        return essentials;
    }

    public boolean isSyncEconomyEnabled() {
        return syncEconomy;
    }

    public boolean isSyncHomesEnabled() {
        return syncHomes;
    }

    public boolean isSyncWarpsEnabled() {
        return syncWarps;
    }

    public double getEconomyMultiplier() {
        return economyMultiplier;
    }

    /**
     * Cache des utilisateurs EssentialsX
     */
    private static class CachedEssentialsUser {
        private final User user;
        private final long cacheTime;

        public CachedEssentialsUser(@NotNull User user) {
            this.user = user;
            this.cacheTime = System.currentTimeMillis();
        }

        public User getUser() {
            return user;
        }

        // Cache valide pendant 5 minutes
        public boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > 300000;
        }
    }
}