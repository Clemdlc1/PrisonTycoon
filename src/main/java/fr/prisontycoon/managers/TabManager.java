package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Gestionnaire pour le système de tab personnalisé avec teams de scoreboard
 * Version corrigée utilisant la méthode commune pour le formatage des préfixes
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

        plugin.getPluginLogger().info(ChatColor.GREEN + "TabManager démarré - Mise à jour toutes les secondes");
    }

    /**
     * Arrête la tâche de mise à jour du tab
     */
    public void stopTabUpdater() {
        if (tabUpdateTask != null) {
            tabUpdateTask.cancel();
            tabUpdateTask = null;
            plugin.getPluginLogger().info(ChatColor.RED + "TabManager arrêté");
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
        String separator = ChatColor.DARK_GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

        return separator + "\n" +
                ChatColor.GOLD + ChatColor.BOLD + "⛏ PRISON TYCOON ⛏\n" +
                ChatColor.GRAY + "Serveur de minage et de progression\n" +
                ChatColor.YELLOW + "📊 Joueurs connectés: " + ChatColor.GREEN + onlinePlayers + ChatColor.GRAY + "/" + ChatColor.GREEN + maxPlayers + "\n" +
                separator;
    }

    /**
     * Construit le pied de page du tab avec les stats du joueur
     */
    private String buildTabFooter(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String separator = ChatColor.DARK_GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

        return separator + "\n" +
                ChatColor.GRAY + "Votre progression:\n" +
                ChatColor.YELLOW + "💰 Coins: " + ChatColor.GOLD + NumberFormatter.format(playerData.getCoins()) + "\n" +
                ChatColor.AQUA + "🎟 Tokens: " + ChatColor.DARK_AQUA + NumberFormatter.format(playerData.getTokens()) + "\n" +
                ChatColor.GREEN + "⭐ Expérience: " + ChatColor.DARK_GREEN + NumberFormatter.format(playerData.getExperience()) + "\n" +
                ChatColor.LIGHT_PURPLE + "🏆 Rang: " + ChatColor.WHITE + getCurrentRankDisplay(player) + "\n" +
                ChatColor.DARK_PURPLE + "🌟 Prestige: " + getPrestigeDisplay(player) + "\n" +
                separator;
    }

    /**
     * Obtient l'affichage du rang actuel du joueur dans les mines
     */
    private String getCurrentRankDisplay(Player player) {
        String highestPermission = plugin.getMineManager().getCurrentRank(player);
        if (highestPermission != null) {
            String rank = highestPermission.toUpperCase();
            return "Mine " + rank;
        }
        return "Mine A";
    }

    /**
     * NOUVEAU: Obtient l'affichage du prestige
     */
    private String getPrestigeDisplay(Player player) {
        int prestigeLevel = plugin.getPrestigeManager().getPrestigeLevel(player);
        if (prestigeLevel > 0) {
            String prestigeColor = getPrestigeColor(prestigeLevel);
            return prestigeColor + "P" + prestigeLevel;
        }
        return "§7Aucun";
    }

    /**
     * MÉTHODE COMMUNE - Obtient la couleur selon le niveau de prestige
     */
    private String getPrestigeColor(int prestigeLevel) {
        if (prestigeLevel >= 50) return "§c"; // Rouge - Prestige légendaire
        if (prestigeLevel >= 40) return "§6"; // Orange - Prestige élevé
        if (prestigeLevel >= 30) return "§d"; // Rose/Magenta - Haut prestige
        if (prestigeLevel >= 20) return "§b"; // Cyan - Prestige moyen-haut
        if (prestigeLevel >= 10) return "§a"; // Vert - Prestige moyen
        if (prestigeLevel >= 5) return "§9";  // Bleu foncé - Bas prestige
        return "§f"; // Blanc - Prestige très bas (P1-P4)
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
     * CORRIGÉ: Met à jour une équipe de joueur avec le nouveau système de préfixes
     */
    private void updatePlayerTeam(Scoreboard scoreboard, Player player) {
        String teamName = getTeamName(player);

        String prefix = getPlayerPrefix(player);

        removePlayerFromAllTeams(scoreboard, player);

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setPrefix(prefix + " ");
        team.addEntry(player.getName());
    }

    /**
     * Implémentation de secours pour le préfixe
     */
    public String getPlayerPrefix(Player player) {
        // Détermine le type de joueur et sa couleur de base
        String playerType;
        String playerTypeColor;

        if (player.hasPermission("specialmine.admin")) {
            playerType = "ADMIN";
            playerTypeColor = "§4"; // Rouge foncé
        } else if (player.hasPermission("specialmine.vip")) {
            playerType = "VIP";
            playerTypeColor = "§e"; // Jaune
        } else {
            playerType = "JOUEUR";
            playerTypeColor = "§7"; // Gris
        }

        // Récupère le niveau de prestige
        int prestigeLevel = plugin.getPrestigeManager().getPrestigeLevel(player);

        // Récupère le rang de mine actuel
        String[] rankInfo = plugin.getMineManager().getRankAndColor(player);
        String mineRank = rankInfo[0].toUpperCase(); // A, B, C... Z
        String mineRankColor = rankInfo[1]; // Couleur du rang

        // Construit le préfixe selon les spécifications
        StringBuilder prefix = new StringBuilder();

        // [TYPE] en couleur du type de joueur
        prefix.append(playerTypeColor).append("[").append(playerType).append("]");

        // [P{niveau}] seulement si prestige > 0, couleur selon prestige
        if (prestigeLevel > 0) {
            String prestigeColor = getPrestigeColor(prestigeLevel);
            prefix.append(" ").append(prestigeColor).append("[P").append(prestigeLevel).append("]");
        }

        // [RANG] en couleur du rang de mine
        prefix.append(" ").append(mineRankColor).append("[").append(mineRank).append("]");

        return prefix.toString();
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
                setupInitialTeams(player);
                updatePlayerTab(player);

                // Met à jour le tab pour tous les autres joueurs (nouveau joueur visible)
                updateAllPlayersTab();

                plugin.getPluginLogger().info("Tab initialisé pour " + player.getName());
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
        createTeamIfNotExists(scoreboard, ADMIN_TEAM, "§c");
        createTeamIfNotExists(scoreboard, VIP_TEAM, "§6");
        createTeamIfNotExists(scoreboard, PLAYER_TEAM, "§7");

        // Met à jour toutes les équipes pour ce joueur
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            updatePlayerTeam(scoreboard, onlinePlayer);
        }
    }

    /**
     * Crée une équipe si elle n'existe pas déjà
     */
    private void createTeamIfNotExists(Scoreboard scoreboard, String teamName, String nameColorCode) {
        if (scoreboard.getTeam(teamName) == null) {
            Team team = scoreboard.registerNewTeam(teamName);
            char colorChar = nameColorCode.charAt(1);
            team.setColor(ChatColor.getByChar(colorChar));
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
                    Scoreboard board = onlinePlayer.getScoreboard();
                    Team team = board.getEntryTeam(player.getName());
                    if (team != null) {
                        team.removeEntry(player.getName());
                    }
                }
            }

            plugin.getPluginLogger().info("Tab nettoyé pour " + player.getName());
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