package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.CustomEnchantment;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tâche d'auto-amélioration des enchantements
 * ENTIÈREMENT RECODÉE : Permissions, meilleure gestion, corrections bugs
 */
public class AutoUpgradeTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private int cycleCount = 0;

    // Cache pour éviter les vérifications répétées
    private final Map<UUID, Boolean> playerPermissionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPermissionCheck = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpgradeTime = new ConcurrentHashMap<>();

    // Configuration
    private static final long PERMISSION_CACHE_DURATION = 60000; // 1 minute
    private static final long MIN_UPGRADE_INTERVAL = 10000; // 10 secondes entre upgrades d'un même joueur
    private static final int MAX_UPGRADES_PER_CYCLE = 3;

    public AutoUpgradeTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        try {
            cycleCount++;
            int totalUpgrades = 0;
            int playersProcessed = 0;

            // Nettoie le cache de permissions périodiquement
            if (cycleCount % 60 == 0) { // Toutes les 10 minutes
                cleanupPermissionCache();
            }

            // Traite tous les joueurs en cache
            for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {
                UUID playerId = playerData.getPlayerId();

                // Vérifie les permissions avec cache
                if (!hasAutoUpgradePermission(playerId)) {
                    // Désactive silencieusement l'auto-upgrade si plus de permission
                    disableAllAutoUpgrades(playerData);
                    continue;
                }

                // Vérifie l'intervalle minimum entre upgrades
                Long lastUpgrade = lastUpgradeTime.get(playerId);
                long now = System.currentTimeMillis();
                if (lastUpgrade != null && now - lastUpgrade < MIN_UPGRADE_INTERVAL) {
                    continue;
                }

                int playerUpgrades = processPlayerAutoUpgrades(playerData);
                if (playerUpgrades > 0) {
                    totalUpgrades += playerUpgrades;
                    playersProcessed++;
                    lastUpgradeTime.put(playerId, now);

                    // Marque le joueur comme modifié
                    plugin.getPlayerDataManager().markDirty(playerId);

                    // Met à jour la pioche du joueur s'il est en ligne
                    Player player = plugin.getServer().getPlayer(playerId);
                    if (player != null && player.isOnline()) {
                        plugin.getPickaxeManager().updatePlayerPickaxe(player);
                        plugin.getEconomyManager().initializeVanillaExp(player);
                    }
                }
            }

            // Log périodique des statistiques
            if (cycleCount % 30 == 0 || totalUpgrades > 0) {
                plugin.getPluginLogger().info("AutoUpgrade cycle #" + cycleCount +
                        ": " + totalUpgrades + " améliorations pour " + playersProcessed + " joueurs");
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans AutoUpgradeTask:");
            e.printStackTrace();
        }
    }

    /**
     * NOUVEAU: Vérifie les permissions avec cache
     */
    private boolean hasAutoUpgradePermission(UUID playerId) {
        long now = System.currentTimeMillis();
        Long lastCheck = lastPermissionCheck.get(playerId);

        // Utilise le cache si récent
        if (lastCheck != null && now - lastCheck < PERMISSION_CACHE_DURATION) {
            return playerPermissionCache.getOrDefault(playerId, false);
        }

        // Vérifie les permissions
        Player player = plugin.getServer().getPlayer(playerId);
        boolean hasPermission = false;

        if (player != null && player.isOnline()) {
            hasPermission = plugin.getEnchantmentManager().canUseAutoUpgrade(player);
        }

        // Met à jour le cache
        playerPermissionCache.put(playerId, hasPermission);
        lastPermissionCheck.put(playerId, now);

        return hasPermission;
    }

    /**
     * NOUVEAU: Désactive tous les auto-upgrades d'un joueur
     */
    private void disableAllAutoUpgrades(PlayerData playerData) {
        Set<String> currentAutoUpgrades = new HashSet<>(playerData.getAutoUpgradeEnabled());

        if (!currentAutoUpgrades.isEmpty()) {
            for (String enchantmentName : currentAutoUpgrades) {
                playerData.setAutoUpgrade(enchantmentName, false);
            }

            plugin.getPluginLogger().debug("Auto-upgrades désactivés pour " + playerData.getPlayerName() +
                    " (permission manquante)");
        }
    }

    /**
     * CORRIGÉ: Traite les auto-améliorations d'un joueur avec meilleure gestion
     */
    private int processPlayerAutoUpgrades(PlayerData playerData) {
        int upgradesPerformed = 0;

        // Récupère les enchantements avec auto-amélioration activée
        Set<String> autoUpgradeEnabled = new HashSet<>(playerData.getAutoUpgradeEnabled());

        if (autoUpgradeEnabled.isEmpty()) {
            return 0;
        }

        plugin.getPluginLogger().debug("Joueur " + playerData.getPlayerName() +
                " a " + autoUpgradeEnabled.size() + " auto-upgrades actifs: " + autoUpgradeEnabled);

        // Traite chaque enchantement
        for (String enchantmentName : autoUpgradeEnabled) {
            if (upgradesPerformed >= MAX_UPGRADES_PER_CYCLE) {
                plugin.getPluginLogger().debug("Limite d'améliorations atteinte (" + MAX_UPGRADES_PER_CYCLE +
                        ") pour " + playerData.getPlayerName());
                break;
            }

            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
            if (enchantment == null) {
                plugin.getPluginLogger().warning("§cEnchantement invalide dans auto-upgrade: '" +
                        enchantmentName + "' pour " + playerData.getPlayerName());
                // Désactive cet auto-upgrade invalide
                playerData.setAutoUpgrade(enchantmentName, false);
                continue;
            }

            int currentLevel = playerData.getEnchantmentLevel(enchantmentName);

            plugin.getPluginLogger().debug("Auto-upgrade check: " + enchantmentName +
                    " niveau " + currentLevel + "/" + enchantment.getMaxLevel() +
                    " pour " + playerData.getPlayerName());

            // Vérifie si peut encore être amélioré
            if (currentLevel >= enchantment.getMaxLevel()) {
                // Désactive l'auto-amélioration si niveau max atteint
                playerData.setAutoUpgrade(enchantmentName, false);

                // Notifie le joueur si en ligne
                Player player = plugin.getServer().getPlayer(playerData.getPlayerId());
                if (player != null && player.isOnline()) {
                    player.sendMessage("§2🏆 Auto-amélioration terminée pour " +
                            enchantment.getDisplayName() + " §2(niveau maximum " +
                            enchantment.getMaxLevel() + " atteint)!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                }

                plugin.getPluginLogger().info("Auto-upgrade désactivé pour " + enchantmentName +
                        " (niveau max atteint) - " + playerData.getPlayerName());
                continue;
            }

            // Calcule le coût de l'amélioration suivante
            long upgradeCost = enchantment.getUpgradeCost(currentLevel + 1);
            long availableTokens = playerData.getTokens();

            plugin.getPluginLogger().debug("Auto-upgrade cost check: " + enchantmentName +
                    " coût=" + upgradeCost + ", tokens=" + availableTokens +
                    " pour " + playerData.getPlayerName());

            // Vérifie si le joueur a assez de tokens
            if (availableTokens >= upgradeCost) {
                // CORRIGÉ: Effectue l'amélioration directement sur les données
                if (playerData.removeTokens(upgradeCost)) {
                    playerData.setEnchantmentLevel(enchantmentName, currentLevel + 1);
                    upgradesPerformed++;

                    // Notifie le joueur si en ligne
                    Player player = plugin.getServer().getPlayer(playerData.getPlayerId());
                    if (player != null && player.isOnline()) {
                        player.sendMessage("§a⚡ Auto-amélioration: " + enchantment.getDisplayName() +
                                " §aniveau " + (currentLevel + 1) + " §7(-" +
                                fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter.format(upgradeCost) + " tokens)");
                    }

                    plugin.getPluginLogger().info("Auto-amélioration réussie: " + playerData.getPlayerName() +
                            " - " + enchantmentName + " niveau " + (currentLevel + 1) +
                            " (coût: " + upgradeCost + " tokens)");
                } else {
                    plugin.getPluginLogger().warning("Échec retrait tokens pour auto-upgrade: " +
                            enchantmentName + " pour " + playerData.getPlayerName());
                }
            } else {
                plugin.getPluginLogger().debug("Auto-upgrade bloqué pour " + playerData.getPlayerName() +
                        " - " + enchantmentName + ": " + availableTokens + "/" + upgradeCost + " tokens");
            }
        }

        return upgradesPerformed;
    }

    /**
     * NOUVEAU: Nettoie le cache de permissions
     */
    private void cleanupPermissionCache() {
        long now = System.currentTimeMillis();
        Set<UUID> toRemove = new HashSet<>();

        for (Map.Entry<UUID, Long> entry : lastPermissionCheck.entrySet()) {
            if (now - entry.getValue() > PERMISSION_CACHE_DURATION * 2) {
                toRemove.add(entry.getKey());
            }
        }

        for (UUID playerId : toRemove) {
            playerPermissionCache.remove(playerId);
            lastPermissionCheck.remove(playerId);
            lastUpgradeTime.remove(playerId);
        }

        if (!toRemove.isEmpty()) {
            plugin.getPluginLogger().debug("Nettoyage cache permissions: " + toRemove.size() + " entrées supprimées");
        }
    }

    /**
     * NOUVEAU: Force la vérification des permissions pour un joueur
     */
    public void refreshPlayerPermissions(UUID playerId) {
        lastPermissionCheck.remove(playerId);
        playerPermissionCache.remove(playerId);
    }

    /**
     * Obtient les statistiques de l'auto-amélioration
     */
    public AutoUpgradeStats getStats() {
        int enabledPlayers = 0;
        int totalAutoEnchantments = 0;
        int playersWithPermission = 0;

        for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {
            var autoEnabled = playerData.getAutoUpgradeEnabled();
            if (!autoEnabled.isEmpty()) {
                enabledPlayers++;
                totalAutoEnchantments += autoEnabled.size();

                if (hasAutoUpgradePermission(playerData.getPlayerId())) {
                    playersWithPermission++;
                }
            }
        }

        return new AutoUpgradeStats(enabledPlayers, totalAutoEnchantments, cycleCount, playersWithPermission);
    }

    /**
     * Statistiques de l'auto-amélioration (améliorées)
     */
    public static class AutoUpgradeStats {
        private final int enabledPlayers;
        private final int totalAutoEnchantments;
        private final int totalCycles;
        private final int playersWithPermission;

        public AutoUpgradeStats(int enabledPlayers, int totalAutoEnchantments, int totalCycles, int playersWithPermission) {
            this.enabledPlayers = enabledPlayers;
            this.totalAutoEnchantments = totalAutoEnchantments;
            this.totalCycles = totalCycles;
            this.playersWithPermission = playersWithPermission;
        }

        public int getEnabledPlayers() { return enabledPlayers; }
        public int getTotalAutoEnchantments() { return totalAutoEnchantments; }
        public int getTotalCycles() { return totalCycles; }
        public int getPlayersWithPermission() { return playersWithPermission; }

        @Override
        public String toString() {
            return String.format("AutoUpgradeStats{players=%d, enchantments=%d, cycles=%d, withPermission=%d}",
                    enabledPlayers, totalAutoEnchantments, totalCycles, playersWithPermission);
        }
    }
}