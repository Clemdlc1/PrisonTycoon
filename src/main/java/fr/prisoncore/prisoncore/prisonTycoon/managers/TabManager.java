package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Gestionnaire pour le système de tab personnalisé avec teams de scoreboard
 * Version améliorée avec tri automatique par rang
 */
public class TabManager {

    // Noms des équipes pour le tri (préfixe numérique pour l'ordre)
    private static final String ADMIN_TEAM = "01_admin";
    private static final String VIP_TEAM = "02_vip";
    private static final String PLAYER_TEAM = "03_joueur";
    private final PrisonTycoon plugin;
    private BukkitRunnable tabUpdateTask;

    public TabManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Démarre la tâche de mise à jour du tab
     */
    public void startTabUpdater() {
        if (tabUpdateTask != null) {
            tabUpdateTask.cancel();
        }

        tabUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllPlayersTab();
            }
        };

        // Met à jour toutes les 20 ticks (1 seconde)
        tabUpdateTask.runTaskTimer(plugin, 0L, 20L);

        plugin.getPluginLogger().info("§aTabManager démarré - Mise à jour toutes les secondes");
    }

    /**
     * Arrête la tâche de mise à jour du tab
     */
    public void stopTabUpdater() {
        if (tabUpdateTask != null) {
            tabUpdateTask.cancel();
            tabUpdateTask = null;
            plugin.getPluginLogger().info("§cTabManager arrêté");
        }
    }

    /**
     * Met à jour le tab pour tous les joueurs
     */
    private void updateAllPlayersTab() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayerTab(player);
        }
    }

    /**
     * Met à jour le tab pour un joueur spécifique
     */
    public void updatePlayerTab(Player player) {
        try {
            // Header (en-tête du tab)
            String header = buildTabHeader();

            // Footer (pied de page du tab)
            String footer = buildTabFooter(player);

            // Applique le header et footer
            player.setPlayerListHeaderFooter(header, footer);

            // Met à jour les teams pour le tri
            updatePlayerTeams(player);

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la mise à jour du tab pour " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Construit l'en-tête du tab
     */
    private String buildTabHeader() {
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        int maxPlayers = plugin.getServer().getMaxPlayers();

        return "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                "§6§l⛏ PRISON TYCOON ⛏\n" +
                "§7Serveur de minage et de progression\n" +
                "§e📊 Joueurs connectés: §a" + onlinePlayers + "§7/§a" + maxPlayers + "\n" +
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    }

    /**
     * Construit le pied de page du tab avec les stats du joueur
     */
    private String buildTabFooter(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        return "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                "§7Votre progression:\n" +
                "§e💰 Coins: §6" + NumberFormatter.format(playerData.getCoins()) + "\n" +
                "§b🎟 Tokens: §3" + NumberFormatter.format(playerData.getTokens()) + "\n" +
                "§a⭐ Expérience: §2" + NumberFormatter.format(playerData.getExperience()) + "\n" +
                "§d🏆 Rang: §f" + getCurrentRankDisplay(playerData) + "\n" +
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    }

    /**
     * Obtient l'affichage du rang actuel du joueur dans les mines
     */
    private String getCurrentRankDisplay(PlayerData playerData) {
        String highestPermission = playerData.getHighestMinePermission();
        if (highestPermission != null && highestPermission.startsWith("mine-")) {
            String rank = highestPermission.substring(5).toUpperCase();
            return "Mine " + rank;
        }
        return "Mine A";
    }

    /**
     * Met à jour les teams pour le tri des joueurs dans le tab
     */
    private void updatePlayerTeams(Player player) {
        // Utilise le scoreboard principal (celui de ScoreboardTask)
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) {
            scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();
        }

        // Met à jour tous les joueurs connectés
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            updatePlayerTeam(scoreboard, onlinePlayer);
        }
    }

    /**
     * Met à jour l'équipe d'un joueur spécifique pour le tab
     */
    private void updatePlayerTeam(Scoreboard scoreboard, Player player) {
        String[] rankInfo = plugin.getMineManager().getRankAndColor(player);
        String teamName = getTeamName(player);

        // NOUVEAU FORMAT : Simple avec juste nom et préfixe VIP/ADMIN si applicable
        String prefix = "";
        if ("ADMIN".equals(rankInfo[0])) {
            prefix = rankInfo[2] + "[ADMIN] "; // Rouge
        } else if ("VIP".equals(rankInfo[0])) {
            prefix = rankInfo[2] + "[VIP] "; // Jaune
        }
        // Joueurs normaux : pas de préfixe, juste le nom

        // Retire le joueur de toutes les équipes existantes
        removePlayerFromAllTeams(scoreboard, player);

        // Crée ou récupère l'équipe appropriée
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setPrefix(prefix);
            if (!prefix.isEmpty()) {
                team.setColor(org.bukkit.ChatColor.valueOf(rankInfo[1].substring(1).toUpperCase()));
            } else {
                team.setColor(org.bukkit.ChatColor.WHITE);
            }
        } else {
            team.setPrefix(prefix);
        }

        // Ajoute le joueur à l'équipe
        team.addEntry(player.getName());
    }

    /**
     * Retire un joueur de toutes les équipes
     */
    private void removePlayerFromAllTeams(Scoreboard scoreboard, Player player) {
        for (Team team : scoreboard.getTeams()) {
            if (team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
    }

    /**
     * Retourne le nom de l'équipe pour un joueur (pour le tri dans le tab)
     */
    private String getTeamName(Player player) {
        if (player.hasPermission("specialmine.admin")) {
            return ADMIN_TEAM;
        } else if (player.hasPermission("specialmine.vip")) {
            return VIP_TEAM;
        } else {
            return PLAYER_TEAM;
        }
    }

    /**
     * Met à jour le tab lors de la connexion d'un joueur
     */
    public void onPlayerJoin(Player player) {
        // Délai pour assurer que le joueur est complètement connecté
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                updatePlayerTab(player);
                setupInitialTeams(player);

                // Met à jour le tab pour tous les autres joueurs (nouveau joueur visible)
                updateAllPlayersTab();

                plugin.getPluginLogger().debug("Tab initialisé pour " + player.getName());
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur lors de l'initialisation du tab pour " + player.getName() + ": " + e.getMessage());
            }
        }, 20L); // 1 seconde de délai
    }

    /**
     * Configure les équipes initiales pour un nouveau joueur
     */
    private void setupInitialTeams(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard == null) {
            scoreboard = plugin.getServer().getScoreboardManager().getMainScoreboard();
        }

        // Crée les équipes de base si elles n'existent pas
        createTeamIfNotExists(scoreboard, ADMIN_TEAM, "§4", "§c");
        createTeamIfNotExists(scoreboard, VIP_TEAM, "§e", "§6");
        createTeamIfNotExists(scoreboard, PLAYER_TEAM, "§8", "§7");

        // Met à jour toutes les équipes pour ce joueur
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            updatePlayerTeam(scoreboard, onlinePlayer);
        }
    }

    /**
     * Crée une équipe si elle n'existe pas
     */
    private void createTeamIfNotExists(Scoreboard scoreboard, String teamName, String rankColor, String nameColor) {
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            team.setColor(org.bukkit.ChatColor.valueOf(nameColor.substring(1).toUpperCase()));
        }
    }

    /**
     * Nettoie les données du tab lors de la déconnexion d'un joueur
     */
    public void onPlayerQuit(Player player) {
        try {
            // Retire le joueur de toutes les équipes de tous les scoreboards
            for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
                if (!onlinePlayer.equals(player)) {
                    removePlayerFromAllTeams(onlinePlayer.getScoreboard(), player);
                }
            }

            plugin.getPluginLogger().debug("Tab nettoyé pour " + player.getName());
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors du nettoyage du tab pour " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Force la mise à jour du tab pour un joueur (utile après changement de permissions)
     */
    public void forceUpdatePlayer(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            updatePlayerTab(player);
            updateAllPlayersTab(); // Met à jour pour tous car le rang a pu changer
        });
    }
}