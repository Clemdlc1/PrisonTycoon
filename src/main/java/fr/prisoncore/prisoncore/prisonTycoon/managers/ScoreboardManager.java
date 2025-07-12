package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du scoreboard et des notifications hotbar
 */
public class ScoreboardManager {

    private final PrisonTycoon plugin;
    private final Map<Player, Scoreboard> playerScoreboards;
    private final Map<Player, Long> lastHotbarMessage;

    public ScoreboardManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerScoreboards = new ConcurrentHashMap<>();
        this.lastHotbarMessage = new ConcurrentHashMap<>();

        plugin.getPluginLogger().info("§aScoreboardManager initialisé.");
    }

    /**
     * Crée et affiche le scoreboard pour un joueur
     */
    public void createScoreboard(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

        // Objective principal
        Objective objective = scoreboard.registerNewObjective("prison_stats", "dummy",
                ChatColor.GOLD + "✨ " + ChatColor.BOLD + "PRISON TYCOON" + ChatColor.GOLD + " ✨");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Teams pour les lignes colorées
        setupScoreboardTeams(scoreboard);

        // Met à jour le scoreboard
        updateScoreboard(player, scoreboard);

        // Assigne le scoreboard au joueur
        player.setScoreboard(scoreboard);
        playerScoreboards.put(player, scoreboard);

        plugin.getPluginLogger().debug("Scoreboard créé pour " + player.getName());
    }

    /**
     * Configure les équipes pour les lignes du scoreboard
     */
    private void setupScoreboardTeams(Scoreboard scoreboard) {
        String[] teamNames = {
                "line15", "line14", "line13", "line12", "line11", "line10",
                "line9", "line8", "line7", "line6", "line5", "line4",
                "line3", "line2", "line1"
        };

        for (String teamName : teamNames) {
            Team team = scoreboard.registerNewTeam(teamName);
            team.addEntry(ChatColor.values()[teamNames.length - 1 - java.util.Arrays.asList(teamNames).indexOf(teamName)] + "");
        }
    }

    /**
     * Met à jour le scoreboard d'un joueur
     */
    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.get(player);
        if (scoreboard == null) {
            createScoreboard(player);
            return;
        }

        updateScoreboard(player, scoreboard);
    }

    /**
     * Met à jour le contenu du scoreboard
     */
    private void updateScoreboard(Player player, Scoreboard scoreboard) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Objective objective = scoreboard.getObjective("prison_stats");

        if (objective == null) return;

        // Efface les anciens scores
        scoreboard.getEntries().forEach(entry -> scoreboard.resetScores(entry));

        int line = 15;

        // Ligne vide
        setScoreboardLine(scoreboard, objective, line--, " ");

        // Section économie
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GOLD + "💰 " + ChatColor.BOLD + "ÉCONOMIE");
        setScoreboardLine(scoreboard, objective, line--, ChatColor.YELLOW + "Coins: " + ChatColor.WHITE + NumberFormatter.formatWithColor(playerData.getCoins()));
        setScoreboardLine(scoreboard, objective, line--, ChatColor.YELLOW + "Tokens: " + ChatColor.WHITE + NumberFormatter.formatWithColor(playerData.getTokens()));
        setScoreboardLine(scoreboard, objective, line--, ChatColor.YELLOW + "Expérience: " + ChatColor.WHITE + NumberFormatter.formatWithColor(playerData.getExperience()));

        // Ligne vide
        setScoreboardLine(scoreboard, objective, line--, "  ");

        // Section statistiques
        setScoreboardLine(scoreboard, objective, line--, ChatColor.AQUA + "📊 " + ChatColor.BOLD + "STATISTIQUES");
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Blocs minés: " + ChatColor.BLUE + NumberFormatter.format(playerData.getTotalBlocksMined()));
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Enchantements: " + ChatColor.LIGHT_PURPLE + playerData.getEnchantmentLevels().size());

        // États spéciaux
        if (playerData.getCombustionLevel() > 0 || playerData.isAbundanceActive()) {
            setScoreboardLine(scoreboard, objective, line--, "   ");
            setScoreboardLine(scoreboard, objective, line--, ChatColor.RED + "🔥 " + ChatColor.BOLD + "ÉTATS ACTIFS");

            if (playerData.getCombustionLevel() > 0) {
                double multiplier = playerData.getCombustionMultiplier();
                setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Combustion: " + ChatColor.GOLD +
                        String.format("x%.2f", multiplier) + ChatColor.GRAY + " (" + playerData.getCombustionLevel() + "/1000)");
            }

            if (playerData.isAbundanceActive()) {
                setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Abondance: " + ChatColor.GREEN + "ACTIVE");
            }
        }

        // Ligne vide finale
        setScoreboardLine(scoreboard, objective, line--, "    ");

        // Footer
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "play.prisoncore.fr");
    }

    /**
     * Définit une ligne du scoreboard
     */
    private void setScoreboardLine(Scoreboard scoreboard, Objective objective, int score, String text) {
        // Limite la longueur pour éviter les problèmes d'affichage
        if (text.length() > 40) {
            text = text.substring(0, 37) + "...";
        }

        // Utilise un caractère invisible unique pour chaque ligne
        String entry = ChatColor.values()[score % ChatColor.values().length] + "";
        Team team = scoreboard.getTeam("line" + score);

        if (team != null) {
            team.setPrefix(text);
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }

        objective.getScore(entry).setScore(score);
    }

    /**
     * NOUVEAU: Envoie une notification Greed dans la hotbar
     */
    public void sendHotbarGreedNotification(Player player, String greedType, long amount, String currency) {
        long now = System.currentTimeMillis();

        // Évite le spam (max 1 notification par seconde)
        Long lastMessage = lastHotbarMessage.get(player);
        if (lastMessage != null && now - lastMessage < 1000) {
            return;
        }

        String colorCode = switch (currency) {
            case "tokens" -> "§e";
            case "coins" -> "§6";
            case "XP" -> "§a";
            default -> "§f";
        };

        String message = "§l" + greedType + "! " + colorCode + "+" + NumberFormatter.format(amount) + " " + currency;

        player.sendActionBar(message);
        lastHotbarMessage.put(player, now);

        plugin.getPluginLogger().debug("Hotbar Greed notification envoyée: " + player.getName() + " - " + message);
    }

    /**
     * Retire le scoreboard d'un joueur
     */
    public void removeScoreboard(Player player) {
        playerScoreboards.remove(player);
        lastHotbarMessage.remove(player);

        // Remet le scoreboard par défaut
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Met à jour tous les scoreboards
     */
    public void updateAllScoreboards() {
        for (Player player : playerScoreboards.keySet()) {
            if (player.isOnline()) {
                updateScoreboard(player);
            }
        }
    }

    /**
     * Statistiques du gestionnaire
     */
    public int getActiveScoreboards() {
        return playerScoreboards.size();
    }
}