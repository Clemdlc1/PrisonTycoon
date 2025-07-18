package fr.prisontycoon.tasks;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tâche de mise à jour des scoreboards - Gestion complète intégrée
 * NOUVEAU : Remplace complètement ScoreboardManager + optimisations
 */
public class ScoreboardTask extends BukkitRunnable {

    // Optimisations
    private static final int BATCH_SIZE = 10; // Nombre de scoreboards mis à jour par cycle
    private final PrisonTycoon plugin;
    // Gestion complète des scoreboards intégrée
    private final Map<Player, Scoreboard> playerScoreboards;
    private final Map<Player, Long> lastScoreboardUpdate;
    private long tickCount = 0;
    private int updateCycles = 0;

    public ScoreboardTask(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.playerScoreboards = new ConcurrentHashMap<>();
        this.lastScoreboardUpdate = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {
        tickCount++;

        try {
            // Appel direct de la mise à jour à chaque exécution de la tâche
            updateScoreboardsBatch();
            plugin.getEconomyManager().syncAllVanillaExp();
            updateCycles++;

            if (tickCount % 100 == 0) {
                cleanupDisconnectedPlayers();
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur dans ScoreboardTask:");
            e.printStackTrace();
        }
    }

    /**
     * CORRIGÉ : Met à jour les scoreboards de manière plus fréquente et fiable
     */
    private void updateScoreboardsBatch() {
        long now = System.currentTimeMillis();
        int updated = 0;
        int skipped = 0;

        final long INTERVAL = 2000; // 2 secondes au lieu de 5

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (updated >= BATCH_SIZE) {
                break; // Limite le nombre de mises à jour par cycle
            }

            try {
                // CORRIGÉ : Intervalle réduit pour des mises à jour plus réactives
                Long lastUpdate = lastScoreboardUpdate.get(player);
                if (lastUpdate != null && (now - lastUpdate) < INTERVAL) {
                    skipped++;
                    continue;
                }

                updateScoreboard(player);
                lastScoreboardUpdate.put(player, now);
                updated++;

            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur mise à jour scoreboard pour " +
                        player.getName() + ": " + e.getMessage());
            }
        }

        plugin.getPluginLogger().debug("Scoreboards mis à jour: " + updated + " joueurs (ignorés: " +
                skipped + ") (cycle #" + updateCycles + ")");
    }

    /**
     * CORRIGÉ : Création de scoreboard plus robuste
     */
    public void createScoreboard(Player player) {
        if (playerScoreboards.containsKey(player)) {
            plugin.getPluginLogger().debug("Scoreboard déjà existant pour " + player.getName() + ", recréation...");
            // Retire l'ancien avant de créer le nouveau
            removeScoreboard(player);
        }

        try {
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
            lastScoreboardUpdate.put(player, System.currentTimeMillis());

            plugin.getPluginLogger().debug("Scoreboard créé et assigné à " + player.getName());

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur création scoreboard pour " + player.getName() + ":");
            e.printStackTrace();
        }
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
            team.addEntry(ChatColor.values()[teamNames.length - 1 -
                    java.util.Arrays.asList(teamNames).indexOf(teamName)] + "");
        }
    }

    /**
     * CORRIGÉ : Mise à jour plus robuste avec gestion d'erreur
     */
    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = playerScoreboards.get(player);
        if (scoreboard == null) {
            plugin.getPluginLogger().debug("Scoreboard manquant pour " + player.getName() + ", création...");
            createScoreboard(player);
            return;
        }

        try {
            updateScoreboard(player, scoreboard);
            plugin.getPluginLogger().debug("Scoreboard mis à jour pour " + player.getName());
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur mise à jour scoreboard pour " + player.getName() +
                    ", recréation...");
            e.printStackTrace();

            // En cas d'erreur, recrée le scoreboard
            removeScoreboard(player);
            createScoreboard(player);
        }
    }

    /**
     * OPTIMISÉ : Met à jour le contenu du scoreboard avec cache des données
     */
    private void updateScoreboard(Player player, Scoreboard scoreboard) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Objective objective = scoreboard.getObjective("prison_stats");

        if (objective == null) return;

        // OPTIMISATION : Cache des valeurs pour éviter les calculs répétés
        long coins = playerData.getCoins();
        long tokens = playerData.getTokens();
        long experience = playerData.getExperience();
        long beacons = playerData.getBeacons();

        long blocksMined = playerData.getTotalBlocksMined();
        long blocksDestroyed = playerData.getTotalBlocksDestroyed();

        // Efface les anciens scores de manière optimisée
        clearScoreboardEntries(scoreboard);

        int line = 15;

        // Ligne vide
        setScoreboardLine(scoreboard, objective, line--, " ");

        // Section économie
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GOLD + "💰 " + ChatColor.BOLD + "ÉCONOMIE");
        setScoreboardLine(scoreboard, objective, line--, ChatColor.YELLOW + "Coins: " +
                ChatColor.WHITE + NumberFormatter.formatWithColor(coins));
        setScoreboardLine(scoreboard, objective, line--, ChatColor.YELLOW + "Tokens: " +
                ChatColor.WHITE + NumberFormatter.formatWithColor(tokens));
        setScoreboardLine(scoreboard, objective, line--, ChatColor.YELLOW + "Expérience: " +
                ChatColor.WHITE + NumberFormatter.formatWithColor(experience));
        setScoreboardLine(scoreboard, objective, line--, ChatColor.YELLOW + "Beacons: " +
                ChatColor.WHITE + NumberFormatter.formatWithColor(beacons));

        // Ligne vide
        setScoreboardLine(scoreboard, objective, line--, "  ");

        // Section statistiques avec distinction blocs minés/cassés
        setScoreboardLine(scoreboard, objective, line--, ChatColor.AQUA + "📊 " + ChatColor.BOLD + "STATISTIQUES");
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Blocs minés: " +
                ChatColor.BLUE + NumberFormatter.format(blocksMined));

        // Affiche les blocs cassés seulement si différent des blocs minés
        if (blocksDestroyed > blocksMined) {
            setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "Blocs cassés: " +
                    ChatColor.LIGHT_PURPLE + NumberFormatter.format(blocksDestroyed));
        }

        // Ligne vide finale
        setScoreboardLine(scoreboard, objective, line--, "     ");

        // Footer
        setScoreboardLine(scoreboard, objective, line--, ChatColor.GRAY + "play.prisoncore.fr");
    }

    /**
     * OPTIMISÉ : Efface les entrées du scoreboard de manière efficace
     */
    private void clearScoreboardEntries(Scoreboard scoreboard) {
        // Plus efficace que resetScores() pour chaque entrée
        for (String entry : new HashSet<>(scoreboard.getEntries())) {
            scoreboard.resetScores(entry);
        }
    }

    /**
     * Définit une ligne du scoreboard avec optimisations
     */
    private void setScoreboardLine(Scoreboard scoreboard, Objective objective, int score, String text) {
        // CORRECTION : S'assurer que le score n'est pas négatif
        if (score < 0) {
            return;
        }

        // Limite la longueur pour éviter les problèmes d'affichage
        if (text.length() > 40) {
            text = text.substring(0, 37) + "...";
        }

        // Utilise un caractère invisible unique pour chaque ligne
        String entry = ChatColor.values()[score % ChatColor.values().length] + "";
        Team team = scoreboard.getTeam("line" + score);

        if (team != null) {
            String currentPrefix = team.getPrefix();
            if (currentPrefix == null || !currentPrefix.equals(text)) {
                team.setPrefix(text);
            }

            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        }

        objective.getScore(entry).setScore(score);
    }

    /**
     * Retire le scoreboard d'un joueur
     */
    public void removeScoreboard(Player player) {
        Scoreboard removed = playerScoreboards.remove(player);
        lastScoreboardUpdate.remove(player);

        if (removed != null) {
            // Remet le scoreboard par défaut
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            plugin.getPluginLogger().debug("Scoreboard retiré pour " + player.getName());
        }
    }

    /**
     * NOUVEAU : Force une mise à jour immédiate pour tous les joueurs en ligne
     */
    public void forceUpdateAllOnline() {
        lastScoreboardUpdate.clear(); // Force la mise à jour de tous

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                updateScoreboard(player);
                lastScoreboardUpdate.put(player, System.currentTimeMillis());
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur force update scoreboard pour " +
                        player.getName() + ": " + e.getMessage());
            }
        }

        plugin.getPluginLogger().debug("Mise à jour forcée de tous les scoreboards en ligne");
    }


    /**
     * NOUVEAU : Force une mise à jour immédiate pour un joueur spécifique
     */
    public void forceUpdatePlayer(Player player) {
        lastScoreboardUpdate.remove(player); // Force la mise à jour
        updateScoreboard(player);
        lastScoreboardUpdate.put(player, System.currentTimeMillis());
    }

    /**
     * Nettoie les joueurs déconnectés
     */
    private void cleanupDisconnectedPlayers() {
        int cleanedCount = 0;

        // Utilise un iterator pour éviter ConcurrentModificationException
        var iterator = playerScoreboards.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Player player = entry.getKey();

            if (!player.isOnline()) {
                iterator.remove();
                lastScoreboardUpdate.remove(player);
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            plugin.getPluginLogger().debug("Nettoyage scoreboards: " + cleanedCount +
                    " joueurs déconnectés supprimés");
        }
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

        @Override
        public String toString() {
            return String.format("ScoreboardStats{ticks=%d, cycles=%d, players=%d, scoreboards=%d}",
                    totalTicks, updateCycles, onlinePlayers, activeScoreboards);
        }
    }
}