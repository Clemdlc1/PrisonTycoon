package fr.skyblock.hooks;

import fr.prisontycoon.api.PrisonTycoonAPI;
import fr.skyblock.CustomSkyblock;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Hook pour l'intégration avec PrisonTycoon
 * Gère l'économie et les permissions via l'API PrisonTycoon
 */
public class PrisonTycoonHook {

    private final CustomSkyblock skyblockPlugin;
    private PrisonTycoonAPI prisonAPI;
    private boolean isEnabled = false;

    // Coûts configurables
    private long baseExpandCost = 10; // Beacons pour agrandir
    private long baseLevelCost = 1000; // Coins pour améliorer niveau

    public PrisonTycoonHook(CustomSkyblock plugin) {
        this.skyblockPlugin = plugin;

        // Charger les coûts depuis la configuration
        loadConfig();

        // Tenter de se connecter à PrisonTycoon
        setupHook();
    }

    private void loadConfig() {
        this.baseExpandCost = skyblockPlugin.getConfig().getLong("prison-tycoon.beacon-cost-per-size", 10);
        this.baseLevelCost = skyblockPlugin.getConfig().getLong("prison-tycoon.coins-cost-per-level", 1000);
    }

    /**
     * Initialise le hook avec PrisonTycoon
     */
    private void setupHook() {
        Plugin prisonTycoon = skyblockPlugin.getServer().getPluginManager().getPlugin("PrisonTycoon");

        if (prisonTycoon == null) {
            skyblockPlugin.getLogger().info("PrisonTycoon non trouvé - économie interne utilisée");
            return;
        }

        if (!prisonTycoon.isEnabled()) {
            skyblockPlugin.getLogger().warning("PrisonTycoon trouvé mais désactivé");
            return;
        }

        // Tenter de récupérer l'API
        try {
            this.prisonAPI = PrisonTycoonAPI.getInstance();
            if (prisonAPI != null) {
                this.isEnabled = true;
                skyblockPlugin.getLogger().info("Hook PrisonTycoon activé avec succès !");
                skyblockPlugin.getLogger().info("- Coût agrandissement: " + baseExpandCost + " beacons par niveau");
                skyblockPlugin.getLogger().info("- Coût amélioration: " + baseLevelCost + " coins par niveau");
            } else {
                skyblockPlugin.getLogger().warning("PrisonTycoonAPI non disponible");
            }
        } catch (Exception e) {
            skyblockPlugin.getLogger().warning("Erreur lors de l'initialisation du hook PrisonTycoon: " + e.getMessage());
        }
    }

    /**
     * Vérifie si le hook est actif
     */
    public boolean isEnabled() {
        return isEnabled && prisonAPI != null;
    }

    // === GESTION DES PERMISSIONS ===

    public boolean hasCustomPermission (Player player, String permission) {
        return prisonAPI.hasPermission(player, permission);
    }

    // === GESTION DE L'AGRANDISSEMENT (BEACONS) ===

    /**
     * Calcule le coût en beacons pour agrandir une île
     */
    public long calculateExpandCost(int currentSize, int newSize) {
        // Coût progressif basé sur la différence de taille
        int sizeDifference = (newSize - currentSize) / 25; // Par palier de 25 blocs
        return baseExpandCost * sizeDifference * (sizeDifference + 1) / 2; // Coût progressif
    }

    /**
     * Vérifie si un joueur peut agrandir son île
     */
    public boolean canExpandIsland(UUID playerId, int currentSize, int newSize) {
        long cost = calculateExpandCost(currentSize, newSize);
        return hasBeacons(playerId, cost);
    }

    /**
     * Charge le coût d'agrandissement d'une île
     */
    public boolean chargeExpandIsland(Player player, int currentSize, int newSize) {
        long cost = calculateExpandCost(currentSize, newSize);

        if (cost == 0) {
            return true; // Gratuit
        }

        if (prisonAPI.removeBeacons(player, cost)) {
            player.sendMessage("§aÎle agrandie ! Coût: §b" + cost + " beacons");
            return true;
        } else {
            player.sendMessage("§cVous n'avez pas assez de beacons ! (Requis: " + cost + ")");
            return false;
        }
    }

    // === GESTION DES AMÉLIORATIONS DE NIVEAU (COINS) ===

    /**
     * Calcule le coût en coins pour améliorer le niveau d'une île
     */
    public long calculateLevelUpgradeCost(int currentLevel, int newLevel) {
        // Coût exponentiel pour les niveaux
        long totalCost = 0;
        for (int level = currentLevel + 1; level <= newLevel; level++) {
            totalCost += baseLevelCost * Math.pow(1.5, level - 1);
        }
        return Math.round(totalCost);
    }

    /**
     * Vérifie si un joueur peut améliorer le niveau de son île
     */
    public boolean canUpgradeLevel(UUID playerId, int currentLevel, int newLevel) {
        long cost = calculateLevelUpgradeCost(currentLevel, newLevel);
        return hasCoins(playerId, cost);
    }

    /**
     * Charge le coût d'amélioration de niveau d'une île
     */
    public boolean chargeLevelUpgrade(Player player, int currentLevel, int newLevel) {
        long cost = calculateLevelUpgradeCost(currentLevel, newLevel);

        if (cost == 0) {
            return true; // Gratuit
        }

        if (prisonAPI.removeCoins(player, cost)) {
            player.sendMessage("§aÎle améliorée ! Coût: §6" + cost + " coins");
            return true;
        } else {
            player.sendMessage("§cVous n'avez pas assez de coins ! (Requis: " + cost + ")");
            return false;
        }
    }

    // === Adder ==

    public boolean addCoins(UUID playerId, long amount) {
        return prisonAPI.addCoins(playerId, amount);
    }

    public boolean addTokens(UUID playerId, long amount) {
        return prisonAPI.addTokens(playerId, amount);
    }

    public boolean addExperience(UUID playerId, long amount) {
        return prisonAPI.addExperience(playerId, amount);
    }

    public boolean addBeacons(UUID playerId, long amount) {
        return prisonAPI.addBeacons(playerId, amount);
    }

    public boolean removeCoins(UUID playerId, long amount) {
        return prisonAPI.removeCoins(playerId, amount);
    }

    public boolean removeTokens(UUID playerId, long amount) {
        return prisonAPI.removeTokens(playerId, amount);
    }

    public boolean removeExperience(UUID playerId, long amount) {
        return prisonAPI.removeExperience(playerId, amount);
    }

    public boolean removeBeacons(UUID playerId, long amount) {
        return prisonAPI.removeBeacons(playerId, amount);
    }

    // === MÉTHODES DE VÉRIFICATION ===

    public boolean hasCoins(UUID playerId, long amount) {
        return prisonAPI.hasCoins(playerId, amount);
    }

    public boolean hasTokens(UUID playerId, long amount) {
        return prisonAPI.hasTokens(playerId, amount);
    }

    public boolean hasBeacons(UUID playerId, long amount) {
        return prisonAPI.hasBeacons(playerId, amount);
    }

    public long getCoins(UUID playerId) {
        return isEnabled() ? prisonAPI.getCoins(playerId) : 0;
    }

    public long getTokens(UUID playerId) {
        return isEnabled() ? prisonAPI.getTokens(playerId) : 0;
    }

    public long getBeacons(UUID playerId) {
        return isEnabled() ? prisonAPI.getBeacons(playerId) : 0;
    }

    // === TRANSFERTS ===

    public boolean transferCoins(UUID fromPlayer, UUID toPlayer, long amount) {
        return prisonAPI.transferCoins(fromPlayer, toPlayer, amount);
    }

    public boolean transferTokens(UUID fromPlayer, UUID toPlayer, long amount) {
        return prisonAPI.transferTokens(fromPlayer, toPlayer, amount);
    }

    // === INFORMATIONS ===

    /**
     * Affiche les informations économiques d'un joueur
     */
    public void showPlayerEconomy(Player player) {
        long coins = getCoins(player.getUniqueId());
        long tokens = getTokens(player.getUniqueId());
        long beacons = getBeacons(player.getUniqueId());

        player.sendMessage("§6=== §eVotre Économie §6===");
        player.sendMessage("§6Coins: §f" + formatNumber(coins));
        player.sendMessage("§dTokens: §f" + formatNumber(tokens));
        player.sendMessage("§bBeacons: §f" + formatNumber(beacons));
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

    // === CONFIGURATION ===

    public long getBaseExpandCost() {
        return baseExpandCost;
    }

    public long getBaseLevelCost() {
        return baseLevelCost;
    }

    public void setBaseExpandCost(long cost) {
        this.baseExpandCost = cost;
    }

    public void setBaseLevelCost(long cost) {
        this.baseLevelCost = cost;
    }

    /**
     * Recharge la configuration
     */
    public void reloadConfig() {
        loadConfig();
        skyblockPlugin.getLogger().info("Configuration du hook PrisonTycoon rechargée");
    }
}