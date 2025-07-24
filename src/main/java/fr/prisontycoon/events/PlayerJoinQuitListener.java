package fr.prisontycoon.events;

import com.earth2me.essentials.User;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import net.ess3.api.IEssentials;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Listener pour les connexions/déconnexions avec intégrations complètes
 * INTÉGRATION NATIVE - Orchestre toutes les intégrations
 * <p>
 * Fonctionnalités intégrées:
 * - Synchronisation LuckPerms (permissions, groupes, VIP)
 * - Synchronisation Vault (économie)
 * - Synchronisation EssentialsX (homes, balance)
 * - Mise à jour du tab en temps réel
 * - Application des avantages VIP
 * - Création automatique des homes/warps de mines
 */
public class PlayerJoinQuitListener implements Listener {

    private final PrisonTycoon plugin;

    public PlayerJoinQuitListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère la connexion d'un joueur avec toutes les intégrations
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Délai pour laisser le temps aux autres plugins de se charger
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            handlePlayerJoinIntegrations(player);
        }, 40L); // 2 secondes de délai

        // Message de bienvenue immédiat
        sendWelcomeMessage(player, event);
    }

    /**
     * Gère la déconnexion d'un joueur avec nettoyage
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Nettoyage asynchrone pour éviter les lags
        CompletableFuture.runAsync(() -> {
            handlePlayerQuitCleanup(player);
        });
    }

    /**
     * Gère toutes les intégrations lors de la connexion
     */
    private void handlePlayerJoinIntegrations(@NotNull Player player) {
        try {
            plugin.getPluginLogger().debug("Intégrations connexion pour: " + player.getName());

            // 1. Charge les données du joueur
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

            // 2. INTÉGRATION LUCKPERMS - Permissions et VIP
            if (plugin.isLuckPermsEnabled()) {
                handleLuckPermsIntegration(player, playerData);
            } else {
                // Fallback vers système interne
                plugin.getPermissionManager().applyStoredPermissions(player);
            }

            // 3. INTÉGRATION VAULT - Économie
            if (plugin.isVaultEnabled()) {
                handleVaultIntegration(player, playerData);
            }

            // 4. INTÉGRATION ESSENTIALSX - Homes et économie
            if (plugin.isEssentialsEnabled()) {
                handleEssentialsXIntegration(player, playerData);
            }

            // 5. Met à jour le tab avec toutes les intégrations
            plugin.getTabManager().onPlayerJoin(player);

            // 6. Applique les avantages VIP si nécessaire
            if (plugin.getVipManager().isVip(player)) {
                plugin.getVipManager().applyVipBenefits(player);
            }

            // 7. Synchronise l'expérience vanilla
            plugin.getEconomyManager().updateVanillaExpFromCustom(player, playerData.getExperience());

            // 8. Notifications de première connexion
            if (playerData.isFirstJoin()) {
                handleFirstJoin(player, playerData);
            }

            plugin.getPluginLogger().debug("Intégrations terminées pour: " + player.getName());

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur intégrations connexion " + player.getName() + ":");
            e.printStackTrace();
        }
    }

    /**
     * Gère l'intégration LuckPerms
     * INTÉGRATION NATIVE LUCKPERMS
     */
    private void handleLuckPermsIntegration(@NotNull Player player, @NotNull PlayerData playerData) {
        try {
            // Applique les permissions stockées
            plugin.getPermissionManager().applyStoredPermissions(player);

            // Synchronise le statut VIP
            boolean isVipInLuckPerms = plugin.getPermissionManager().isVip(player);
            if (plugin.getVipManager().isVip(player) != isVipInLuckPerms) {
                playerData.setVip(isVipInLuckPerms);
                plugin.getPluginLogger().debug("Statut VIP synchronisé LuckPerms: " + player.getName() + " -> " + isVipInLuckPerms);
            }

            // Obtient les informations de groupe pour les logs
            String primaryGroup = plugin.getPermissionManager().getPrimaryGroup(player);
            plugin.getPluginLogger().debug("LuckPerms - " + player.getName() + ": groupe=" + primaryGroup + ", vip=" + isVipInLuckPerms);

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur intégration LuckPerms " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Gère l'intégration Vault
     * INTÉGRATION NATIVE VAULT
     */
    private void handleVaultIntegration(@NotNull Player player, @NotNull PlayerData playerData) {
        try {
            // Synchronise l'économie avec un délai pour éviter les conflits
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                plugin.getEconomyManager().synchronizePlayerOnLogin(player);
            }, 60L); // 3 secondes de délai

            // Obtient le solde Vault pour les logs
            double vaultBalance = plugin.getEconomyManager().getVaultBalance(player);
            plugin.getPluginLogger().debug("Vault - " + player.getName() + ": balance=$" + vaultBalance);

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur intégration Vault " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Gère l'intégration EssentialsX
     * INTÉGRATION NATIVE ESSENTIALSX
     */
    private void handleEssentialsXIntegration(@NotNull Player player, @NotNull PlayerData playerData) {
        try {
            IEssentials essentials = plugin.getEssentialsAPI();
            if (essentials == null) return;

            User essentialsUser = essentials.getUser(player.getUniqueId());
            if (essentialsUser == null) return;

            // Synchronise l'économie EssentialsX
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                plugin.getEconomyManager().synchronizePlayerOnLogin(player);
            }, 80L); // 4 secondes de délai

            // Crée les homes de mines VIP si nécessaire
            if (plugin.getVipManager().isVip(player)) {
                plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    createVipMineHomes(player);
                }, 100L); // 5 secondes de délai
            }

            // Obtient des infos pour les logs
            boolean isAFK = essentialsUser.isAfk();
            boolean isVanished = essentialsUser.isVanished();
            plugin.getPluginLogger().debug("EssentialsX - " + player.getName() + ": afk=" + isAFK + ", vanished=" + isVanished);

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur intégration EssentialsX " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Crée les homes de mines VIP automatiquement
     * INTÉGRATION NATIVE ESSENTIALSX
     */
    private void createVipMineHomes(@NotNull Player player) {
        try {
            IEssentials essentials = plugin.getEssentialsAPI();
            if (essentials == null) return;

            User essentialsUser = essentials.getUser(player.getUniqueId());
            if (essentialsUser == null) return;

            String homePrefix = plugin.getConfig().getString("hooks.essentialsx.mine-home-prefix", "mine_");
            int homesCreated = 0;

            // Obtient toutes les mines accessibles
            for (String mineName : plugin.getMineManager().getAvailableMines(player)) {
                var mineData = plugin.getMineManager().getMineData(mineName);
                if (mineData != null && mineData.isVipOnly()) {

                    String homeName = homePrefix + mineName.toLowerCase();
                    var spawnLocation = plugin.getMineManager().getMineSpawn(mineName);

                    if (spawnLocation != null) {
                        try {
                            // Vérifie si le home existe déjà
                            if (!essentialsUser.getHomes().contains(homeName)) {
                                essentialsUser.setHome(homeName, spawnLocation);
                                homesCreated++;
                                plugin.getPluginLogger().debug("Home VIP créé: " + player.getName() + " -> " + homeName);
                            }
                        } catch (Exception e) {
                            plugin.getPluginLogger().debug("Erreur création home " + homeName + ": " + e.getMessage());
                        }
                    }
                }
            }

            if (homesCreated > 0) {
                player.sendMessage("§6✨ " + homesCreated + " home(s) VIP de mines créé(s) automatiquement!");
                player.sendMessage("§7Utilisez §e/home " + homePrefix + "<mine> §7pour vous téléporter");
            }

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur création homes VIP " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Gère la première connexion d'un joueur
     */
    private void handleFirstJoin(@NotNull Player player, @NotNull PlayerData playerData) {
        // Marque comme connecté
        playerData.setFirstJoin(false);

        // Message de bienvenue étendu
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§6§l           BIENVENUE SUR PRISONTYCOON!");
            player.sendMessage("§e§l              Votre aventure commence ici!");
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("");
            player.sendMessage("§7🎯 §aCommandes utiles:");
            player.sendMessage("§7   • §e/enchant §7- Menu d'enchantements");
            player.sendMessage("§7   • §e/sell §7- Vendre vos items");
            player.sendMessage("§7   • §e/status §7- Voir votre profil complet");
            player.sendMessage("");

            // Informations sur les intégrations disponibles
            if (plugin.isLuckPermsEnabled() || plugin.isVaultEnabled() ||
                    plugin.isWorldGuardEnabled() || plugin.isEssentialsEnabled()) {

                player.sendMessage("§7🔗 §6Intégrations actives:");
                if (plugin.isLuckPermsEnabled()) {
                    player.sendMessage("§7   • §aLuckPerms §7- Système de permissions avancé");
                }
                if (plugin.isVaultEnabled()) {
                    player.sendMessage("§7   • §6Vault §7- Économie synchronisée");
                }
                if (plugin.isWorldGuardEnabled()) {
                    player.sendMessage("§7   • §cWorldGuard §7- Protection des zones");
                }
                if (plugin.isEssentialsEnabled()) {
                    player.sendMessage("§7   • §eEssentialsX §7- Fonctionnalités étendues");
                }
                player.sendMessage("");
            }

            player.sendMessage("§aAmusez-vous bien et bon jeu! 🎮");
            player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            // Son de bienvenue
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

            // Téléporte à la mine de départ si configuré
            String startingMine = plugin.getConfig().getString("gameplay.starting-mine", "mine_a");
            if (plugin.getMineManager().canAccessMine(player, startingMine)) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getMineManager().teleportToMine(player, startingMine);
                    player.sendMessage("§aTéléportation automatique vers la mine de départ!");
                }, 100L); // 5 secondes de délai
            }

        }, 60L); // 3 secondes de délai

        // Stats pour les logs
        plugin.getPluginLogger().info("Première connexion: " + player.getName() + " (" + player.getUniqueId() + ")");
    }

    /**
     * Envoie le message de bienvenue
     */
    private void sendWelcomeMessage(@NotNull Player player, @NotNull PlayerJoinEvent event) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.isFirstJoin()) {
            // Message de première connexion (simple)
            event.setJoinMessage("§6✨ §e" + player.getName() + " §ajoint le serveur pour la première fois! §6✨");
        } else {
            // Message de reconnexion
            String prefix = "";
            if (plugin.isLuckPermsEnabled()) {
                try {
                    String luckPermsPrefix = plugin.getPermissionManager().getPrimaryGroup(player);
                    if (luckPermsPrefix != null && !luckPermsPrefix.equals("default")) {
                        prefix = "§7[§6" + luckPermsPrefix.toUpperCase() + "§7] ";
                    }
                } catch (Exception ignored) {
                }
            }

            if (plugin.getVipManager().isVip(player)) {
                event.setJoinMessage("§6→ " + prefix + "§6" + player.getName() + " §ea rejoint la partie");
            } else {
                event.setJoinMessage("§7→ " + prefix + "§f" + player.getName() + " §ea rejoint la partie");
            }
        }
    }

    /**
     * Gère le nettoyage lors de la déconnexion
     */
    private void handlePlayerQuitCleanup(@NotNull Player player) {
        try {
            plugin.getPluginLogger().debug("Nettoyage déconnexion pour: " + player.getName());

            // 1. Sauvegarde les données du joueur
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            plugin.getPlayerDataManager().savePlayerData(player.getUniqueId(), playerData);

            // 2. Nettoie les permissions
            plugin.getPermissionManager().cleanupPlayer(player);

            // 3. Nettoie le tab
            plugin.getTabManager().onPlayerQuit(player);

            // 4. Nettoie l'économie
            plugin.getEconomyManager().cleanupPlayer(player);

            // 5. Nettoie les caches des intégrations si disponibles
            // (Les intégrations natives se nettoient automatiquement)

            plugin.getPluginLogger().debug("Nettoyage terminé pour: " + player.getName());

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur nettoyage déconnexion " + player.getName() + ":");
            e.printStackTrace();
        }
    }
}