package fr.prisontycoon.tasks;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.CustomEnchantment;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tâche d'auto-amélioration des enchantements
 * CORRIGÉ : Retire les messages automatiques, laisse le summary gérer l'affichage
 */
public class AutoUpgradeTask extends BukkitRunnable {

    // Configuration
    private static final long PERMISSION_CACHE_DURATION = 60000; // 1 minute
    private static final long MIN_UPGRADE_INTERVAL = 10000; // 10 secondes entre upgrades d'un même joueur
    private final PrisonTycoon plugin;
    // Cache pour éviter les vérifications répétées
    private final Map<UUID, Boolean> playerPermissionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPermissionCheck = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpgradeTime = new ConcurrentHashMap<>();
    private int cycleCount = 0;

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

                    playerData.setLastMinuteAutoUpgrades(playerUpgrades);

                    plugin.getPluginLogger().debug("Auto-upgrades effectués pour " + playerData.getPlayerName() +
                            ": " + playerUpgrades + " (total minute: " + playerData.getLastMinuteAutoUpgrades() + ")");

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
     * Vérifie les permissions avec cache
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
     * Désactive tous les auto-upgrades d'un joueur
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

    private int processPlayerAutoUpgrades(PlayerData playerData) {
        int totalUpgradesPerformed = 0;

        // Récupère les enchantements avec auto-amélioration activée
        Set<String> autoUpgradeEnabled = new HashSet<>(playerData.getAutoUpgradeEnabled());

        if (autoUpgradeEnabled.isEmpty()) {
            return 0;
        }

        plugin.getPluginLogger().debug("Joueur " + playerData.getPlayerName() +
                " a " + autoUpgradeEnabled.size() + " auto-upgrades actifs: " + autoUpgradeEnabled);

        // Récupère le joueur pour les améliorations
        Player player = plugin.getServer().getPlayer(playerData.getPlayerId());
        if (player == null || !player.isOnline()) {
            return 0;
        }

        // Traite chaque enchantement
        for (String enchantmentName : autoUpgradeEnabled) {
            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
            if (enchantment == null) {
                plugin.getPluginLogger().warning("§cEnchantement invalide dans auto-upgrade: '" +
                        enchantmentName + "' pour " + playerData.getPlayerName());
                playerData.setAutoUpgrade(enchantmentName, false);
                continue;
            }

            int currentLevel = playerData.getEnchantmentLevel(enchantmentName);

            plugin.getPluginLogger().debug("Auto-upgrade check: " + enchantmentName +
                    " niveau " + currentLevel + "/" + enchantment.getMaxLevel() +
                    " pour " + playerData.getPlayerName());

            // Vérifie si peut encore être amélioré
            if (currentLevel >= enchantment.getMaxLevel()) {
                playerData.setAutoUpgrade(enchantmentName, false);
                plugin.getPluginLogger().info("Auto-upgrade désactivé pour " + enchantmentName +
                        " (niveau max atteint) - " + playerData.getPlayerName());
                continue;
            }

            // Utilise la méthode optimisée d'EnchantmentUpgradeGUI en mode silencieux
            boolean upgradePerformed = plugin.getEnchantmentUpgradeGUI().upgradeToMax(player, enchantmentName, true);

            if (upgradePerformed) {
                // Calcule les détails pour le récapitulatif
                int newLevel = playerData.getEnchantmentLevel(enchantmentName);
                int levelsGained = newLevel - currentLevel;
                totalUpgradesPerformed += levelsGained;

                // NOUVEAU : Ajouter les détails de l'upgrade pour le récapitulatif
                playerData.addAutoUpgradeDetail(
                        enchantmentName,
                        levelsGained,
                        newLevel
                );

                plugin.getPluginLogger().info("Auto-amélioration silencieuse réussie: " + playerData.getPlayerName() +
                        " - " + enchantment.getDisplayName() + " +" + levelsGained + " niveaux (niveau " +
                        newLevel + ")");
            } else {
                plugin.getPluginLogger().debug("Auto-upgrade bloqué pour " + playerData.getPlayerName() +
                        " - " + enchantmentName + ": pas assez de tokens");
            }
        }

        return totalUpgradesPerformed;
    }

    /**
     * Nettoie le cache de permissions
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
     * Force la vérification des permissions pour un joueur
     */
    public void refreshPlayerPermissions(UUID playerId) {
        lastPermissionCheck.remove(playerId);
        playerPermissionCache.remove(playerId);
    }

    /**
     * Statistiques de l'auto-amélioration
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

        @Override
        public String toString() {
            return String.format("AutoUpgradeStats{players=%d, enchantments=%d, cycles=%d, withPermission=%d}",
                    enabledPlayers, totalAutoEnchantments, totalCycles, playersWithPermission);
        }
    }
}