package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.ModerationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Listener pour les événements de connexion/déconnexion
 * CORRIGÉ : Ajout du chargement des enchantements actifs à la connexion
 */
public class PlayerJoinQuitListener implements Listener {

    private final PrisonTycoon plugin;

    public PlayerJoinQuitListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        Player player = event.getPlayer();
        if (plugin.getModerationManager().isBanned(player.getUniqueId())) {
            var banData = plugin.getModerationManager().getBanData(player.getUniqueId());

            if (banData != null) {
                String kickMessage = createBanKickMessage(banData);

                // Kick le joueur avec un délai pour éviter les problèmes de timing
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.kickPlayer(kickMessage);

                        // Log l'attempt de connexion
                        plugin.getPluginLogger().info("Joueur banni refusé: " + player.getName() +
                                " (Raison: " + banData.reason() + ")");
                    }
                }, 5L);

                return; // Arrête le traitement ici
            }
        }
        plugin.getWarpManager().teleportToWarp(player, "Spawn");
        plugin.getPermissionManager().applyStoredPermissions(player);

        plugin.getTabManager().onPlayerJoin(player);

        // Charge les données du joueur
        plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        plugin.getBoostManager().loadPlayerBoosts(player);

        // NOUVEAU : Charge les enchantements uniques actifs du joueur
        plugin.getEnchantmentBookManager().loadActiveEnchantments(player);
        plugin.getPluginLogger().debug("Enchantements actifs chargés pour " + player.getName());

        // Détermine le message personnalisé selon le rang
        String joinMessage = getJoinMessage(player);

        // Diffuse le message personnalisé si applicable
        if (joinMessage != null && !joinMessage.isEmpty()) {
            plugin.getServer().broadcastMessage(joinMessage);
        }

        // CORRIGÉ : Initialisation avec délai plus long pour assurer stabilité
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Crée le scoreboard
            if (plugin.getScoreboardTask() != null) {
                plugin.getScoreboardTask().createScoreboard(player);
            }

            // Initialise l'expérience vanilla basée sur l'expérience custom
            plugin.getEconomyManager().initializeVanillaExp(player);

            // NOUVEAU : Vérifie l'état de la pioche légendaire à la connexion
            ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
            if (pickaxe != null && pickaxe.getItemMeta() instanceof Damageable meta) {
                short currentDurability = (short) meta.getDamage();
                short maxDurability = pickaxe.getType().getMaxDurability();

                plugin.getPickaxeManager().checkLegendaryPickaxeState(player, pickaxe, currentDurability, maxDurability);
            }

            plugin.getPickaxeManager().updateMobilityEffects(player);

            // Force refresh des permissions auto-upgrade si le joueur en avait
            if (plugin.getAutoUpgradeTask() != null) {
                plugin.getAutoUpgradeTask().refreshPlayerPermissions(player.getUniqueId());
            }

            plugin.getGUIManager().giveMainMenuHead(player);

            // Notification récompense quotidienne
            plugin.getDailyRewardManager().notifyIfClaimAvailable(player);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (plugin.getScoreboardTask() != null) {
                    plugin.getScoreboardTask().forceUpdatePlayer(player);
                }
            }, 20L); // 1 seconde après l'initialisation

            plugin.getPluginLogger().debug("Initialisation complète pour " + player.getName());
        }, 40L);

        plugin.getPluginLogger().info("§7Joueur connecté: " + player.getName());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getTabManager().onPlayerQuit(player);

        event.quitMessage(null);

        // Détermine le message personnalisé selon le rang
        String quitMessage = getQuitMessage(player);

        // Diffuse le message personnalisé si applicable
        if (quitMessage != null && !quitMessage.isEmpty()) {
            plugin.getServer().broadcastMessage(quitMessage);
        }

        // Retire les effets de mobilité
        plugin.getPickaxeManager().removeMobilityEffects(player);

        // CORRIGÉ : Utilise ScoreboardTask au lieu de ScoreboardManager
        if (plugin.getScoreboardTask() != null) {
            plugin.getScoreboardTask().removeScoreboard(player);
        }
        plugin.getBoostManager().unloadPlayer(player);

        // NOUVEAU : Nettoie les notifications en attente
        plugin.getNotificationManager().cleanupPlayerData(player.getUniqueId());

        // NOUVEAU : Sauvegarde les enchantements actifs avant déconnexion
        plugin.getEnchantmentBookManager().saveActiveEnchantments(player);
        plugin.getEnchantmentBookManager().clearActiveEnchantments(player.getUniqueId());

        // Sauvegarde quêtes + collectionneur avant déchargement
        var questProgress = plugin.getQuestManager().getProgress(player.getUniqueId());
        plugin.getQuestManager().saveProgress(questProgress);
        plugin.getBlockCollectorManager().save(player.getUniqueId());

        // Décharge les données du joueur (avec sauvegarde)
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());
        plugin.getPermissionManager().removeAttachment(player);
        plugin.getPrestigeGUI().onPlayerQuit(event.getPlayer());
        plugin.getPluginLogger().info("§7Joueur déconnecté: " + player.getName());
    }

    /**
     * Génère le message de connexion selon le rang du joueur
     */
    private String getJoinMessage(Player player) {
        if (player.hasPermission("specialmine.admin") || player.hasPermission("specialmine.moderator")) {
            return null;
        } else if (player.hasPermission("specialmine.vip")) {
            // VIP : [+] [VIP(jaune)] NOM
            return "§a[+] §e[VIP] §6" + player.getName();
        } else {
            // Joueur normal : [+] NOM
            return "§a[+] §7" + player.getName();
        }
    }

    /**
     * Génère le message de déconnexion selon le rang du joueur
     */
    private String getQuitMessage(Player player) {
        if (player.hasPermission("specialmine.admin") || player.hasPermission("specialmine.moderator")) {
            return null;
        } else if (player.hasPermission("specialmine.vip")) {
            // VIP : [-] [VIP(jaune)] NOM
            return "§c[-] §e[VIP] §6" + player.getName();
        } else {
            // Joueur normal : [-] NOM
            return "§c[-] §7" + player.getName();
        }
    }

    /**
     * Crée le message de kick pour un joueur banni
     */
    private String createBanKickMessage(ModerationManager.ModerationData banData) {
        StringBuilder message = new StringBuilder();

        message.append("§c§l=== VOUS ÊTES BANNI ===\n\n");
        message.append("§cVous avez été banni du serveur\n\n");

        message.append("§7Raison: §e").append(banData.reason()).append("\n");
        message.append("§7Banni par: §e").append(banData.moderator()).append("\n");

        if (banData.isPermanent()) {
            message.append("§7Durée: §cPermanent\n");
        } else {
            long remaining = banData.getRemainingTime();
            if (remaining > 0) {
                message.append("§7Temps restant: §e").append(formatDuration(remaining)).append("\n");
            } else {
                // Ban expiré, on le retire automatiquement
                plugin.getModerationManager().unbanPlayer(banData.uuid(), "SYSTÈME");
                return null; // Permet la connexion
            }
        }

        message.append("\n§7Si vous pensez que c'est une erreur,\n");
        message.append("§7contactez un administrateur.");

        return message.toString();
    }

    /**
     * Formate une durée en millisecondes
     */
    private String formatDuration(long milliseconds) {
        if (milliseconds <= 0) return "0s";

        long days = milliseconds / (24 * 60 * 60 * 1000);
        long hours = (milliseconds % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (milliseconds % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (milliseconds % (60 * 1000)) / 1000;

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("j ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (seconds > 0 && days == 0) result.append(seconds).append("s ");

        return result.toString().trim();
    }
}