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
 * CORRIGÉ : Retire les messages automatiques, laisse le summary gérer l'affichage
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

    /**
     * CORRIGÉ: Traite les auto-améliorations sans envoyer de messages (le summary s'en charge)
     */
    private int processPlayerAutoUpgrades(PlayerData playerData) {
        int totalUpgradesPerformed = 0;

        // Récupère les enchantements avec auto-amélioration activée
        Set<String> autoUpgradeEnabled = new HashSet<>(playerData.getAutoUpgradeEnabled());

        if (autoUpgradeEnabled.isEmpty()) {
            return 0;
        }

        plugin.getPluginLogger().debug("Joueur " + playerData.getPlayerName() +
                " a " + autoUpgradeEnabled.size() + " auto-upgrades actifs: " + autoUpgradeEnabled);

        // Traite chaque enchantement
        for (String enchantmentName : autoUpgradeEnabled) {
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

                // SUPPRIMÉ : Message niveau max atteint (sera dans le summary si nécessaire)

                plugin.getPluginLogger().info("Auto-upgrade désactivé pour " + enchantmentName +
                        " (niveau max atteint) - " + playerData.getPlayerName());
                continue;
            }

            // CORRIGÉ: Calcule le MAXIMUM de niveaux possibles avec les tokens disponibles
            long availableTokens = playerData.getTokens();
            int maxAffordableLevels = 0;
            long totalCost = 0;

            // Calcule combien de niveaux on peut s'offrir
            for (int i = 1; i <= (enchantment.getMaxLevel() - currentLevel); i++) {
                long levelCost = enchantment.getUpgradeCost(currentLevel + i);
                if (totalCost + levelCost <= availableTokens) {
                    totalCost += levelCost;
                    maxAffordableLevels = i;
                } else {
                    break; // Plus assez de tokens
                }
            }

            plugin.getPluginLogger().debug("Auto-upgrade cost check: " + enchantmentName +
                    " max affordable levels=" + maxAffordableLevels + ", total cost=" + totalCost +
                    ", available tokens=" + availableTokens + " pour " + playerData.getPlayerName());

            // CORRIGÉ: Améliore au MAXIMUM possible si on peut s'offrir au moins 1 niveau
            if (maxAffordableLevels > 0) {
                // Effectue l'amélioration directement sur les données
                if (playerData.removeTokens(totalCost)) {
                    playerData.setEnchantmentLevel(enchantmentName, currentLevel + maxAffordableLevels);
                    totalUpgradesPerformed += maxAffordableLevels;

                    plugin.getPluginLogger().info("Auto-amélioration silencieuse réussie: " + playerData.getPlayerName() +
                            " - " + enchantmentName + " +" + maxAffordableLevels + " niveaux (niveau " +
                            (currentLevel + maxAffordableLevels) + ") (coût: " + totalCost + " tokens)");
                } else {
                    plugin.getPluginLogger().warning("Échec retrait tokens pour auto-upgrade: " +
                            enchantmentName + " pour " + playerData.getPlayerName());
                }
            } else {
                plugin.getPluginLogger().debug("Auto-upgrade bloqué pour " + playerData.getPlayerName() +
                        " - " + enchantmentName + ": pas assez de tokens pour le prochain niveau");
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