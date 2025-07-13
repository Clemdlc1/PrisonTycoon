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
 * OPTIMISÉ : Synchronisé avec ChatTask pour inclure les améliorations dans le récap minute
 */
public class AutoUpgradeTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private int cycleCount = 0;

    // Cache pour éviter les vérifications répétées
    private final Map<UUID, Boolean> playerPermissionCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastPermissionCheck = new ConcurrentHashMap<>();

    // Configuration
    private static final long PERMISSION_CACHE_DURATION = 60000; // 1 minute
    private static final int MAX_UPGRADES_PER_MINUTE = 50; // Limite pour éviter le spam

    public AutoUpgradeTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // OPTIMISÉ : Cette tâche ne fait plus rien car elle est appelée par ChatTask
        // On garde la tâche pour la compatibilité mais elle fait juste du nettoyage périodique
        cycleCount++;

        try {
            // Nettoie le cache de permissions toutes les 5 minutes
            if (cycleCount % 60 == 0) { // Toutes les 5 minutes (5 * 60 secondes)
                cleanupPermissionCache();
            }
        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans AutoUpgradeTask cleanup:");
            e.printStackTrace();
        }
    }

    /**
     * NOUVEAU : Traite TOUS les auto-upgrades pour TOUS les joueurs
     * Appelé par ChatTask juste avant le récapitulatif minute
     */
    public AutoUpgradeResult processAllAutoUpgrades() {
        plugin.getPluginLogger().debug("Traitement global des auto-upgrades (cycle #" + cycleCount + ")");

        int totalUpgrades = 0;
        int playersProcessed = 0;
        int playersWithoutPermission = 0;
        long startTime = System.currentTimeMillis();

        try {
            // Traite tous les joueurs en cache
            for (PlayerData playerData : plugin.getPlayerDataManager().getAllCachedPlayers()) {
                UUID playerId = playerData.getPlayerId();

                // Vérifie les permissions avec cache
                if (!hasAutoUpgradePermission(playerId)) {
                    disableAllAutoUpgrades(playerData);
                    playersWithoutPermission++;
                    continue;
                }

                int playerUpgrades = processPlayerAutoUpgrades(playerData);
                if (playerUpgrades > 0) {
                    totalUpgrades += playerUpgrades;
                    playersProcessed++;

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

            long duration = System.currentTimeMillis() - startTime;

            plugin.getPluginLogger().info("Auto-upgrade global terminé: " + totalUpgrades +
                    " améliorations pour " + playersProcessed + " joueurs" +
                    " (" + playersWithoutPermission + " sans permission) en " + duration + "ms");

            return new AutoUpgradeResult(totalUpgrades, playersProcessed, playersWithoutPermission, duration);

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans processAllAutoUpgrades:");
            e.printStackTrace();
            return new AutoUpgradeResult(0, 0, 0, 0);
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
     * Traite les auto-améliorations d'un joueur avec amélioration AU MAXIMUM POSSIBLE
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
                playerData.setAutoUpgrade(enchantmentName, false);
                continue;
            }

            int currentLevel = playerData.getEnchantmentLevel(enchantmentName);

            // Vérifie si peut encore être amélioré
            if (currentLevel >= enchantment.getMaxLevel()) {
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

            // Calcule le MAXIMUM de niveaux possibles avec les tokens disponibles
            long availableTokens = playerData.getTokens();
            int maxAffordableLevels = 0;
            long totalCost = 0;

            // LIMITATION : Maximum 50 niveaux par minute pour éviter le spam
            int maxLevelsThisRound = Math.min(MAX_UPGRADES_PER_MINUTE, enchantment.getMaxLevel() - currentLevel);

            for (int i = 1; i <= maxLevelsThisRound; i++) {
                long levelCost = enchantment.getUpgradeCost(currentLevel + i);
                if (totalCost + levelCost <= availableTokens) {
                    totalCost += levelCost;
                    maxAffordableLevels = i;
                } else {
                    break;
                }
            }

            // Améliore au MAXIMUM possible si on peut s'offrir au moins 1 niveau
            if (maxAffordableLevels > 0) {
                if (playerData.removeTokens(totalCost)) {
                    playerData.setEnchantmentLevel(enchantmentName, currentLevel + maxAffordableLevels);
                    totalUpgradesPerformed += maxAffordableLevels;

                    // Notifie le joueur si en ligne
                    Player player = plugin.getServer().getPlayer(playerData.getPlayerId());
                    if (player != null && player.isOnline()) {
                        if (maxAffordableLevels == 1) {
                            player.sendMessage("§a⚡ Auto-amélioration: " + enchantment.getDisplayName() +
                                    " §aniveau " + (currentLevel + maxAffordableLevels) + " §7(-" +
                                    fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter.format(totalCost) + " tokens)");
                        } else {
                            player.sendMessage("§a⚡ Auto-amélioration: " + enchantment.getDisplayName() +
                                    " §a+" + maxAffordableLevels + " niveaux §7(niveau " + (currentLevel + maxAffordableLevels) +
                                    ") §7(-" + fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter.format(totalCost) + " tokens)");
                        }
                    }

                    plugin.getPluginLogger().info("Auto-amélioration réussie: " + playerData.getPlayerName() +
                            " - " + enchantmentName + " +" + maxAffordableLevels + " niveaux (niveau " +
                            (currentLevel + maxAffordableLevels) + ") (coût: " + totalCost + " tokens)");
                }
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
     * NOUVEAU : Résultat d'un traitement global d'auto-upgrades
     */
    public static class AutoUpgradeResult {
        private final int totalUpgrades;
        private final int playersProcessed;
        private final int playersWithoutPermission;
        private final long processingTimeMs;

        public AutoUpgradeResult(int totalUpgrades, int playersProcessed, int playersWithoutPermission, long processingTimeMs) {
            this.totalUpgrades = totalUpgrades;
            this.playersProcessed = playersProcessed;
            this.playersWithoutPermission = playersWithoutPermission;
            this.processingTimeMs = processingTimeMs;
        }

        public int getTotalUpgrades() { return totalUpgrades; }
        public int getPlayersProcessed() { return playersProcessed; }
        public int getPlayersWithoutPermission() { return playersWithoutPermission; }
        public long getProcessingTimeMs() { return processingTimeMs; }

        @Override
        public String toString() {
            return String.format("AutoUpgradeResult{upgrades=%d, players=%d, withoutPermission=%d, time=%dms}",
                    totalUpgrades, playersProcessed, playersWithoutPermission, processingTimeMs);
        }
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