package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Gestionnaire du Tab intégré avec LuckPerms et toutes les intégrations
 * INTÉGRATION NATIVE - Remplace l'ancien TabManager
 * <p>
 * Fonctionnalités intégrées:
 * - Préfixes/suffixes LuckPerms
 * - Balances Vault et EssentialsX
 * - Informations en temps réel
 * - Cache intelligent
 * - Mise à jour automatique
 */
public class TabManager {

    private final PrisonTycoon plugin;
    private final NumberFormat numberFormat;

    // Cache des données de tab pour éviter les calculs répétés
    private final ConcurrentMap<String, CachedTabData> tabCache = new ConcurrentHashMap<>();
    // Configuration
    private final boolean showPrefixSuffix;
    private final boolean showVaultBalance;
    private final boolean showEssentialsBalance;
    private final boolean showPluginStats;
    private final int updateInterval;
    // Tâche de mise à jour périodique
    private BukkitRunnable updateTask;

    public TabManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.numberFormat = NumberFormat.getInstance(Locale.FRENCH);

        // Configuration depuis config.yml
        this.showPrefixSuffix = plugin.getConfig().getBoolean("gui.integrations.show-luckperms-group", true);
        this.showVaultBalance = plugin.getConfig().getBoolean("gui.integrations.show-vault-balance", true);
        this.showEssentialsBalance = plugin.getConfig().getBoolean("gui.integrations.show-essentialsx-status", true);
        this.showPluginStats = plugin.getConfig().getBoolean("gui.integrations.show-plugin-stats", true);
        this.updateInterval = plugin.getConfig().getInt("gui.tab-update-interval", 10); // secondes

        // Démarre les mises à jour automatiques
        startUpdateTask();

        plugin.getPluginLogger().info("TabManager intégré initialisé (update: " + updateInterval + "s)");
    }

    /**
     * Démarre la tâche de mise à jour automatique du tab
     */
    private void startUpdateTask() {
        updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    updateAllPlayerTabs();
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Erreur mise à jour tab: " + e.getMessage());
                }
            }
        };

        // Démarre avec un délai initial et répète toutes les X secondes
        updateTask.runTaskTimerAsynchronously(plugin, 20L, updateInterval * 20L);
    }

    /**
     * Met à jour le tab de tous les joueurs connectés
     */
    public void updateAllPlayerTabs() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayerTab(player);
        }

        // Nettoie le cache périodiquement
        cleanupExpiredCache();
    }

    /**
     * Met à jour le tab d'un joueur spécifique
     * INTÉGRATION NATIVE avec tous les plugins
     */
    public void updatePlayerTab(@NotNull Player player) {
        try {
            // Génère les données de tab
            TabData tabData = generateTabData(player);

            // Applique au joueur
            applyTabToPlayer(player, tabData);

            // Met en cache
            tabCache.put(player.getName(), new CachedTabData(tabData));

        } catch (Exception e) {
            plugin.getPluginLogger().debug("Erreur mise à jour tab " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Génère les données de tab pour un joueur
     * INTÉGRATION COMPLÈTE
     */
    private TabData generateTabData(@NotNull Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Header du tab
        String header = generateTabHeader(player, playerData);

        // Footer du tab
        String footer = generateTabFooter(player, playerData);

        // Nom d'affichage du joueur
        String displayName = generatePlayerDisplayName(player, playerData);

        return new TabData(header, footer, displayName);
    }

    /**
     * Génère l'en-tête du tab avec intégrations
     */
    private String generateTabHeader(@NotNull Player player, @NotNull PlayerData playerData) {
        StringBuilder header = new StringBuilder();

        // Logo du serveur
        header.append("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
        header.append("§6§l           PRISONTYCOON           \n");
        header.append("§e§l        Serveur Prison Moderne        \n");
        header.append("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n");
        header.append("\n");

        // Informations du joueur avec intégrations
        header.append("§f§lVotre Profil:\n");

        // Groupe LuckPerms si disponible
        if (plugin.isLuckPermsEnabled() && showPrefixSuffix) {
            String group = getPrimaryGroup(player);
            String prefix = getPrefix(player);

            if (prefix != null && !prefix.isEmpty()) {
                header.append("§7Rang: ").append(prefix).append(" §f").append(group).append("\n");
            } else {
                header.append("§7Rang: §6").append(group).append("\n");
            }
        }

        // Économie plugin
        if (showPluginStats) {
            header.append("§7Coins: §6").append(formatNumber(playerData.getCoins())).append("\n");
            header.append("§7Tokens: §b").append(formatNumber(playerData.getTokens())).append("\n");
        }

        // Balance Vault si disponible
        if (plugin.isVaultEnabled() && showVaultBalance) {
            double vaultBalance = plugin.getEconomyManager().getVaultBalance(player);
            header.append("§7Vault: §a$").append(formatNumber((long) vaultBalance)).append("\n");
        }

        // Balance EssentialsX si disponible
        if (plugin.isEssentialsEnabled() && showEssentialsBalance) {
            BigDecimal essentialsBalance = plugin.getEconomyManager().getEssentialsBalance(player);
            header.append("§7EssentialsX: §e$").append(formatNumber(essentialsBalance.longValue())).append("\n");
        }

        return header.toString();
    }

    /**
     * Génère le pied de page du tab avec statistiques
     */
    private String generateTabFooter(@NotNull Player player, @NotNull PlayerData playerData) {
        StringBuilder footer = new StringBuilder();

        footer.append("\n");
        footer.append("§f§lStatistiques:\n");

        // Niveau et expérience
        long experience = playerData.getExperience();
        int level = plugin.getEconomyManager().calculateLevelFromExperience(experience);
        footer.append("§7Niveau: §b").append(level).append(" §7(§e").append(formatNumber(experience)).append(" XP§7)\n");

        // Prestige si disponible
        int prestigeLevel = playerData.getPrestigeLevel(player);
        if (prestigeLevel > 0) {
            footer.append("§7Prestige: §5✦ ").append(prestigeLevel).append("\n");
        }

        // Statistiques de minage
        footer.append("§7Blocs minés: §f").append(formatNumber(playerData.getTotalBlocksMined())).append("\n");
        footer.append("§7Blocs détruits: §f").append(formatNumber(playerData.getTotalBlocksDestroyed())).append("\n");


        // Statut VIP
        boolean isVip = plugin.getPermissionManager().isVip(player);
        footer.append("§7Statut: ").append(isVip ? "§6VIP" : "§7Joueur").append("\n");

        footer.append("\n");
        footer.append("§f§lServeur:\n");
        footer.append("§7Joueurs: §a").append(plugin.getServer().getOnlinePlayers().size())
                .append("§7/§c").append(plugin.getServer().getMaxPlayers()).append("\n");

        // Informations sur les intégrations
        StringBuilder integrations = new StringBuilder("§7Intégrations: ");
        if (plugin.isLuckPermsEnabled()) integrations.append("§aLP ");
        if (plugin.isVaultEnabled()) integrations.append("§6V ");
        if (plugin.isWorldGuardEnabled()) integrations.append("§cWG ");
        if (plugin.isEssentialsEnabled()) integrations.append("§eESS ");
        footer.append(integrations).append("\n");

        footer.append("\n");
        footer.append("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        return footer.toString();
    }

    /**
     * Génère le nom d'affichage du joueur avec préfixe/suffixe
     * INTÉGRATION NATIVE LUCKPERMS
     */
    private String generatePlayerDisplayName(@NotNull Player player, @NotNull PlayerData playerData) {
        StringBuilder displayName = new StringBuilder();

        if (plugin.isLuckPermsEnabled() && showPrefixSuffix) {
            String prefix = getPrefix(player);
            String suffix = getSuffix(player);

            if (prefix != null && !prefix.isEmpty()) {
                displayName.append(prefix).append(" ");
            }

            displayName.append("§f").append(player.getName());

            if (suffix != null && !suffix.isEmpty()) {
                displayName.append(" ").append(suffix);
            }
        } else {
            // Fallback vers le système du plugin
            if (plugin.getVipManager().isVip(player)) {
                displayName.append("§6[VIP] §f").append(player.getName());
            } else {
                displayName.append("§7").append(player.getName());
            }
        }

        return displayName.toString();
    }

    /**
     * Applique les données de tab à un joueur
     */
    private void applyTabToPlayer(@NotNull Player player, @NotNull TabData tabData) {
        // Utilise l'API Paper moderne si disponible
        try {
            player.setPlayerListHeaderFooter(tabData.header, tabData.footer);
            player.setPlayerListName(tabData.displayName);
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Erreur application tab " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Obtient le groupe principal d'un joueur
     * INTÉGRATION NATIVE LUCKPERMS
     */
    @Nullable
    private String getPrimaryGroup(@NotNull Player player) {
        if (!plugin.isLuckPermsEnabled()) return "default";

        try {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();
            User user = userManager.getUser(player.getUniqueId());

            return user != null ? user.getPrimaryGroup() : "default";
        } catch (Exception e) {
            return "default";
        }
    }

    /**
     * Obtient le préfixe d'un joueur
     * INTÉGRATION NATIVE LUCKPERMS
     */
    @Nullable
    private String getPrefix(@NotNull Player player) {
        if (!plugin.isLuckPermsEnabled()) return null;

        try {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();
            User user = userManager.getUser(player.getUniqueId());

            if (user != null) {
                CachedMetaData metaData = user.getCachedData().getMetaData();
                return metaData.getPrefix();
            }
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Erreur obtention préfixe " + player.getName() + ": " + e.getMessage());
        }

        return null;
    }

    /**
     * Obtient le suffixe d'un joueur
     * INTÉGRATION NATIVE LUCKPERMS
     */
    @Nullable
    private String getSuffix(@NotNull Player player) {
        if (!plugin.isLuckPermsEnabled()) return null;

        try {
            LuckPerms luckPerms = plugin.getLuckPermsAPI();
            UserManager userManager = luckPerms.getUserManager();
            User user = userManager.getUser(player.getUniqueId());

            if (user != null) {
                CachedMetaData metaData = user.getCachedData().getMetaData();
                return metaData.getSuffix();
            }
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Erreur obtention suffixe " + player.getName() + ": " + e.getMessage());
        }

        return null;
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
     * Nettoie le cache expiré
     */
    private void cleanupExpiredCache() {
        tabCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Met à jour le tab d'un joueur au login
     */
    public void onPlayerJoin(@NotNull Player player) {
        // Délai pour laisser le temps aux autres plugins de se charger
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            updatePlayerTab(player);
        }, 20L); // 1 seconde de délai
    }

    /**
     * Nettoie les données d'un joueur qui quitte
     */
    public void onPlayerQuit(@NotNull Player player) {
        tabCache.remove(player.getName());
    }

    /**
     * Force la mise à jour de tous les tabs
     */
    public void forceUpdateAll() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::updateAllPlayerTabs);
    }

    /**
     * Recharge la configuration
     */
    public void reloadConfig() {
        // Arrête l'ancienne tâche
        if (updateTask != null) {
            updateTask.cancel();
        }

        // Nettoie le cache
        tabCache.clear();

        // Redémarre avec la nouvelle config
        startUpdateTask();

        plugin.getPluginLogger().info("TabManager rechargé");
    }

    /**
     * Nettoie toutes les ressources
     */
    public void cleanup() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        tabCache.clear();
    }

    /**
     * Obtient des statistiques sur le tab
     */
    public String getTabStats() {
        int cached = tabCache.size();
        int expired = (int) tabCache.values().stream().filter(CachedTabData::isExpired).count();
        return "TabManager: " + cached + " joueurs en cache (" + expired + " expirés)";
    }

    /**
     * Classe pour stocker les données de tab
     */
    private static class TabData {
        final String header;
        final String footer;
        final String displayName;

        TabData(String header, String footer, String displayName) {
            this.header = header;
            this.footer = footer;
            this.displayName = displayName;
        }
    }

    /**
     * Cache des données de tab
     */
    private static class CachedTabData {
        private final TabData tabData;
        private final long cacheTime;

        CachedTabData(TabData tabData) {
            this.tabData = tabData;
            this.cacheTime = System.currentTimeMillis();
        }

        TabData getTabData() {
            return tabData;
        }

        // Cache valide pendant 2 minutes
        boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > 120000;
        }
    }
}