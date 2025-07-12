package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tâche de mise à jour des scoreboards
 * NOUVEAU : Avec gestion scoreboard intégrée (ancien ScoreboardManager supprimé)
 */
public class ScoreboardTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private long tickCount = 0;
    private int updateCycles = 0;

    // NOUVEAU : Gestion des scoreboards intégrée
    private final Map<Player, Scoreboard> playerScoreboards;

    public ScoreboardTask(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerScoreboards = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {
        tickCount++;

        try {
            // Met à jour tous les scoreboards toutes les 20 secondes (400 ticks)
            if (tickCount % 400 == 0) {
                updateAllScoreboards();
                updateCycles++;
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans ScoreboardTask:");
            e.printStackTrace();
        }
    }

    /**
     * NOUVEAU : Crée et affiche le scoreboard pour un joueur
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
     * NOUVEAU : Configure les équipes pour les lignes du scoreboard
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
     * NOUVEAU : Met à jour le scoreboard d'un joueur
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
     * NOUVEAU : Met à jour le contenu du scoreboard
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

        // Section statistiques avec distinction blocs minés/cassés
        setScoreboardLine(scoreboard, objective, line--, ChatColor.AQUA + "📊 " + ChatColor.BOLD + "STATISTIQUES");
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Blocs minés: " + ChatColor.BLUE + NumberFormatter.format(playerData.getTotalBlocksMined()));

        // Affiche les blocs cassés seulement si différent des blocs minés
        long blocksDestroyed = playerData.getTotalBlocksDestroyed();
        long blocksMinedOnly = playerData.getTotalBlocksMined();
        if (blocksDestroyed > blocksMinedOnly) {
            long specialDestroyed = blocksDestroyed - blocksMinedOnly;
            setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Blocs cassés: " + ChatColor.LIGHT_PURPLE + NumberFormatter.format(specialDestroyed));
        }

        setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Enchantements: " + ChatColor.LIGHT_PURPLE + playerData.getEnchantmentLevels().size());

        // États spéciaux actifs
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

        // Position pioche
        boolean pickaxeInCorrectSlot = plugin.getPickaxeManager().isPickaxeInCorrectSlot(player);
        if (plugin.getPickaxeManager().hasLegendaryPickaxe(player)) {
            setScoreboardLine(scoreboard, objective, line--, "    ");
            setScoreboardLine(scoreboard, objective, line--, ChatColor.YELLOW + "⛏️ " + ChatColor.BOLD + "PIOCHE");
            String slotStatus = pickaxeInCorrectSlot ? ChatColor.GREEN + "Slot 1 ✓" : ChatColor.RED + "Mauvaise position!";
            setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Position: " + slotStatus);
        }

        // Ligne vide finale
        setScoreboardLine(scoreboard, objective, line--, "     ");

        // Footer
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "play.prisoncore.fr");
    }

    /**
     * NOUVEAU : Définit une ligne du scoreboard
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
     * NOUVEAU : Retire le scoreboard d'un joueur
     */
    public void removeScoreboard(Player player) {
        playerScoreboards.remove(player);

        // Remet le scoreboard par défaut
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Met à jour tous les scoreboards
     */
    private void updateAllScoreboards() {
        int updatedCount = 0;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                updateScoreboard(player);
                updatedCount++;
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur mise à jour scoreboard pour " + player.getName() + ": " + e.getMessage());
            }
        }

        plugin.getPluginLogger().debug("Scoreboards mis à jour: " + updatedCount + " joueurs (cycle #" + updateCycles + ")");
    }

    /**
     * Force une mise à jour immédiate de tous les scoreboards
     */
    public void forceUpdate() {
        updateAllScoreboards();
        plugin.getPluginLogger().debug("Mise à jour forcée des scoreboards");
    }

    /**
     * NOUVEAU : Statistiques du gestionnaire
     */
    public int getActiveScoreboards() {
        return playerScoreboards.size();
    }

    /**
     * Obtient les statistiques de la tâche
     */
    public ScoreboardStats getStats() {
        return new ScoreboardStats(
                tickCount,
                updateCycles,
                plugin.getServer().getOnlinePlayers().size(),
                getActiveScoreboards()
        );
    }

    /**
     * Statistiques de la ScoreboardTask
     */
    public static class ScoreboardStats {
        private final long totalTicks;
        private final int updateCycles;
        private final int onlinePlayers;
        private final int activeScoreboards;

        public ScoreboardStats(long totalTicks, int updateCycles, int onlinePlayers, int activeScoreboards) {
            this.totalTicks = totalTicks;
            this.updateCycles = updateCycles;
            this.onlinePlayers = onlinePlayers;
            this.activeScoreboards = activeScoreboards;
        }

        public long getTotalTicks() { return totalTicks; }
        public int getUpdateCycles() { return updateCycles; }
        public int getOnlinePlayers() { return onlinePlayers; }
        public int getActiveScoreboards() { return activeScoreboards; }

        @Override
        public String toString() {
            return String.format("ScoreboardStats{ticks=%d, cycles=%d, players=%d, scoreboards=%d}",
                    totalTicks, updateCycles, onlinePlayers, activeScoreboards);
        }
    }
}