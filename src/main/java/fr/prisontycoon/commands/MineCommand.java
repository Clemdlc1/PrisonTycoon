package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commande pour gérer les mines - VERSION CORRIGÉE
 * Utilise la nouvelle classe MineData externe
 * Usage: /mine <list|tp|info|generate|types|accessible|stats|search> [args...]
 */
public class MineCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public MineCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "list", "liste" -> handleListCommand(player, args);
            case "tp", "teleport", "warp" -> handleTeleportCommand(player, args);
            case "info", "informations" -> handleInfoCommand(player, args);
            case "generate", "gen", "reset" -> handleGenerateCommand(player, args);
            case "types", "categories" -> handleTypesCommand(player);
            case "accessible", "access" -> handleAccessibleCommand(player);
            case "stats", "statistics", "statistiques" -> handleStatsCommand(player);
            case "search", "find", "chercher" -> handleSearchCommand(player, args);
            case "current", "actuel" -> handleCurrentCommand(player);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * Gère /mine list [type]
     */
    private void handleListCommand(Player player, String[] args) {
        MineData.MineType filterType = null;

        // Filtrage par type si spécifié
        if (args.length >= 2) {
            try {
                filterType = MineData.MineType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c❌ Type de mine invalide! Types disponibles: §eNORMAL§c, §ePRESTIGE§c, §eVIP");
                return;
            }
        }

        player.sendMessage("§6═══════════════════════════════════");
        if (filterType != null) {
            player.sendMessage("§6      📋 MINES " + getTypeDisplayName(filterType).toUpperCase());
        } else {
            player.sendMessage("§6         📋 TOUTES LES MINES");
        }
        player.sendMessage("§6═══════════════════════════════════");

        List<MineData> mines;
        if (filterType != null) {
            mines = plugin.getMineManager().getMinesByType(filterType);
        } else {
            mines = new ArrayList<>(plugin.getMineManager().getAllMines());
            mines.sort((a, b) -> {
                if (a.getType() != b.getType()) {
                    return a.getType().ordinal() - b.getType().ordinal();
                }
                return a.getId().compareToIgnoreCase(b.getId());
            });
        }

        if (mines.isEmpty()) {
            player.sendMessage("§7Aucune mine trouvée.");
            return;
        }

        // Grouper et afficher par type
        MineData.MineType currentType = null;
        for (MineData mine : mines) {
            if (currentType != mine.getType()) {
                currentType = mine.getType();
                player.sendMessage("");
                player.sendMessage("§6📍 " + getTypeDisplayName(currentType) + ":");
            }

            boolean canAccess = plugin.getMineManager().canAccessMine(player, mine.getId());
            String accessIcon = canAccess ? "§a✅" : "§c❌";
            String mineInfo = "§7  " + accessIcon + " §f" + mine.getDisplayName();

            // Ajout d'infos supplémentaires
            if (mine.hasBeacons()) {
                mineInfo += " §e(%.1f%% beacons)".formatted(mine.getBeaconRate());
            }
            if (mine.getRankupPrice() > 0) {
                mineInfo += " §7- §a" + NumberFormatter.format(mine.getRankupPrice()) + "$";
            }

            player.sendMessage(mineInfo);
        }

        player.sendMessage("");
        player.sendMessage("§7Total: §6" + mines.size() + " §7mines");
        player.sendMessage("§7Utilisez §6/mine tp <nom> §7pour vous téléporter");
    }

    /**
     * Gère /mine tp <mine>
     */
    private void handleTeleportCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /mine tp <nom_mine>");
            player.sendMessage("§7Utilisez §6/mine accessible §7pour voir vos mines");
            return;
        }

        String mineName = args[1].toLowerCase();

        // Recherche intelligente de la mine
        String mineId = findMineId(mineName);

        if (mineId == null) {
            player.sendMessage("§c❌ Mine introuvable: §e" + mineName);

            // Suggestions de mines similaires
            List<MineData> suggestions = plugin.getMineManager().searchMines(mineName);
            if (!suggestions.isEmpty()) {
                player.sendMessage("§7Mines similaires:");
                suggestions.stream().limit(3).forEach(mine ->
                        player.sendMessage("§7  • §6" + mine.getId() + " §7(§f" + mine.getDisplayName() + "§7)")
                );
            }
            return;
        }

        boolean success = plugin.getMineManager().teleportToMine(player, mineId);
        if (!success) {
            MineData mine = plugin.getMineManager().getMine(mineId);
            if (mine != null) {
                // Message d'erreur détaillé selon la raison
                if (!plugin.getMineManager().canAccessMine(player, mineId)) {
                    showAccessRequirements(player, mine);
                } else {
                    player.sendMessage("§c❌ Erreur lors de la téléportation!");
                }
            }
        }
    }

    /**
     * Gère /mine info <mine>
     */
    private void handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /mine info <nom_mine>");
            return;
        }

        String mineName = args[1].toLowerCase();
        String mineId = findMineId(mineName);

        if (mineId == null) {
            player.sendMessage("§c❌ Mine introuvable: §e" + mineName);
            return;
        }

        String info = plugin.getMineManager().getMineInfo(player, mineId);
        player.sendMessage(info);
    }

    /**
     * Gère /mine generate <mine|all> (admin seulement)
     */
    private void handleGenerateCommand(Player player, String[] args) {
        if (!player.hasPermission("specialmine.admin")) {
            player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /mine generate <mine|all>");
            return;
        }

        String target = args[1].toLowerCase();

        if (target.equals("all")) {
            player.sendMessage("§e🔄 Génération de toutes les mines...");
            plugin.getMineManager().resetAllMines();
            player.sendMessage("§a✅ Toutes les mines ont été régénérées!");
            return;
        }

        String mineId = findMineId(target);
        if (mineId == null) {
            player.sendMessage("§c❌ Mine introuvable: §e" + target);
            return;
        }

        if (plugin.getMineManager().isMineGenerating(mineId)) {
            player.sendMessage("§c❌ Cette mine est déjà en cours de génération!");
            return;
        }

        player.sendMessage("§e🔄 Génération de la mine §6" + mineId + "§e...");
        plugin.getMineManager().generateMine(mineId);
        player.sendMessage("§a✅ Mine générée avec succès!");
    }

    /**
     * Gère /mine types
     */
    private void handleTypesCommand(Player player) {
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6         📊 TYPES DE MINES");
        player.sendMessage("§6═══════════════════════════════════");

        // Compter les mines par type
        List<MineData> normalMines = plugin.getMineManager().getMinesByType(MineData.MineType.NORMAL);
        List<MineData> prestigeMines = plugin.getMineManager().getMinesByType(MineData.MineType.PRESTIGE);
        List<MineData> vipMines = plugin.getMineManager().getMinesByType(MineData.MineType.VIP);

        player.sendMessage("§f📍 NORMALES §7(" + normalMines.size() + " mines)");
        player.sendMessage("§7  Mines standard de A à Z");
        player.sendMessage("§7  Débloquées par rankup");
        player.sendMessage("§7  Exemple: §6/mine tp a");

        player.sendMessage("");
        player.sendMessage("§d📍 PRESTIGE §7(" + prestigeMines.size() + " mines)");
        player.sendMessage("§7  Mines exclusives aux joueurs prestige");
        player.sendMessage("§7  Contiennent des beacons rares");
        player.sendMessage("§7  Exemple: §6/mine tp p1");

        player.sendMessage("");
        player.sendMessage("§6📍 VIP §7(" + vipMines.size() + " mines)");
        player.sendMessage("§7  Mines exclusives aux VIP");
        player.sendMessage("§7  Ressources de haute qualité");
        player.sendMessage("§7  Exemple: §6/mine tp vip1");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§e📊 Votre accès:");
        String currentRank = plugin.getMineManager().getCurrentRank(player);
        player.sendMessage("§7• Rang actuel: " + plugin.getMineManager().getRankColor(currentRank) + currentRank.toUpperCase());
        player.sendMessage("§7• Prestige: " + playerData.getPrestigeDisplayName());
        player.sendMessage("§7• VIP: " + (player.hasPermission("specialmine.vip") ? "§a✅" : "§c❌"));
    }

    /**
     * Gère /mine accessible
     */
    private void handleAccessibleCommand(Player player) {
        List<MineData> accessibleMines = plugin.getMineManager().getAccessibleMines(player);

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6      🔓 MINES ACCESSIBLES");
        player.sendMessage("§6═══════════════════════════════════");

        if (accessibleMines.isEmpty()) {
            player.sendMessage("§7Aucune mine accessible.");
            player.sendMessage("§7Améliorez votre rang avec §6/rankup§7!");
            return;
        }

        // Grouper par type
        List<MineData> normalAccessible = accessibleMines.stream()
                .filter(m -> m.getType() == MineData.MineType.NORMAL)
                .toList();

        List<MineData> prestigeAccessible = accessibleMines.stream()
                .filter(m -> m.getType() == MineData.MineType.PRESTIGE)
                .toList();

        List<MineData> vipAccessible = accessibleMines.stream()
                .filter(m -> m.getType() == MineData.MineType.VIP)
                .toList();

        if (!normalAccessible.isEmpty()) {
            player.sendMessage("§f📍 Mines normales:");
            for (MineData mine : normalAccessible) {
                player.sendMessage("§7  • §6/mine tp " + mine.getId() + " §7- §f" + mine.getDisplayName());
            }
        }

        if (!prestigeAccessible.isEmpty()) {
            player.sendMessage("");
            player.sendMessage("§d📍 Mines prestige:");
            for (MineData mine : prestigeAccessible) {
                String beacons = mine.hasBeacons() ?
                        String.format(" §e(%.1f%% beacons)", mine.getBeaconRate()) : "";
                player.sendMessage("§7  • §6/mine tp " + mine.getId() + " §7- §f" + mine.getDisplayName() + beacons);
            }
        }

        if (!vipAccessible.isEmpty()) {
            player.sendMessage("");
            player.sendMessage("§6📍 Mines VIP:");
            for (MineData mine : vipAccessible) {
                String beacons = mine.hasBeacons() ?
                        String.format(" §e(%.1f%% beacons)", mine.getBeaconRate()) : "";
                player.sendMessage("§7  • §6/mine tp " + mine.getId() + " §7- §f" + mine.getDisplayName() + beacons);
            }
        }

        player.sendMessage("");
        player.sendMessage("§7Total accessible: §6" + accessibleMines.size() + " §7mines");
    }

    /**
     * NOUVEAU: Gère /mine stats
     */
    private void handleStatsCommand(Player player) {
        String stats = plugin.getMineManager().getMinesStatistics();
        player.sendMessage(stats);

        // Ajout de stats personnelles
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentMine = plugin.getMineManager().getPlayerCurrentMine(player);

        player.sendMessage("§e📊 Vos statistiques:");
        player.sendMessage("§7• Mine actuelle: " + (currentMine != null ? "§6" + currentMine : "§cAucune"));
        player.sendMessage("§7• Mines accessibles: §6" + plugin.getMineManager().getAccessibleMines(player).size());
        player.sendMessage("§7• Rang: " + plugin.getMineManager().getRankColor(plugin.getMineManager().getCurrentRank(player)) +
                plugin.getMineManager().getCurrentRank(player).toUpperCase());
        player.sendMessage("§7• Prestige: " + playerData.getPrestigeDisplayName());
    }

    /**
     * NOUVEAU: Gère /mine search <terme>
     */
    private void handleSearchCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /mine search <terme>");
            return;
        }

        String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        List<MineData> results = plugin.getMineManager().searchMines(query);

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6      🔍 RECHERCHE: " + query.toUpperCase());
        player.sendMessage("§6═══════════════════════════════════");

        if (results.isEmpty()) {
            player.sendMessage("§7Aucune mine trouvée pour: §e" + query);
            return;
        }

        for (MineData mine : results) {
            boolean canAccess = plugin.getMineManager().canAccessMine(player, mine.getId());
            String accessIcon = canAccess ? "§a✅" : "§c❌";

            player.sendMessage("§7" + accessIcon + " §6" + mine.getId() + " §7- §f" + mine.getDisplayName() +
                    " §7(" + getTypeDisplayName(mine.getType()) + "§7)");
        }

        player.sendMessage("");
        player.sendMessage("§7Trouvé: §6" + results.size() + " §7mine(s)");
    }

    /**
     * NOUVEAU: Gère /mine current
     */
    private void handleCurrentCommand(Player player) {
        String currentMine = plugin.getMineManager().getPlayerCurrentMine(player);

        if (currentMine == null) {
            player.sendMessage("§c❌ Vous n'êtes actuellement dans aucune mine!");
            player.sendMessage("§7Utilisez §6/mine tp <nom> §7pour vous téléporter à une mine");
            return;
        }

        MineData mine = plugin.getMineManager().getMine(currentMine);
        if (mine == null) {
            player.sendMessage("§c❌ Mine actuelle introuvable dans la configuration!");
            return;
        }

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6         📍 MINE ACTUELLE");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§7Vous êtes dans: §6" + mine.getDisplayName());
        player.sendMessage("§7Type: " + getTypeDisplayName(mine.getType()));
        player.sendMessage("§7ID: §f" + mine.getId());

        if (mine.hasBeacons()) {
            player.sendMessage("§7Taux beacons: §e" + String.format("%.1f%%", mine.getBeaconRate()));
        }

        player.sendMessage("");
        player.sendMessage("§7Utilisez §6/mine info " + mine.getId() + " §7pour plus de détails");
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Recherche intelligente d'une mine par son nom
     */
    private String findMineId(String mineName) {
        // Recherche exacte d'abord
        if (plugin.getMineManager().mineExists(mineName)) {
            return mineName;
        }

        // Essayer différents formats
        List<String> possibleIds = Arrays.asList(
                mineName.toLowerCase(),
                "mine-" + mineName.toLowerCase(),
                mineName.toLowerCase().replace("mine-", ""),
                mineName.toLowerCase().replace("mine_", ""),
                mineName.toLowerCase().replace(" ", ""),
                mineName.toLowerCase().replace("-", "")
        );

        for (String possibleId : possibleIds) {
            if (plugin.getMineManager().mineExists(possibleId)) {
                return possibleId;
            }
        }

        // Recherche partielle
        List<MineData> matches = plugin.getMineManager().searchMines(mineName);
        if (!matches.isEmpty()) {
            return matches.getFirst().getId(); // Retourne le premier match
        }

        return null;
    }

    /**
     * Affiche les prérequis d'accès pour une mine
     */
    private void showAccessRequirements(Player player, MineData mine) {
        player.sendMessage("§c❌ Accès refusé à la mine §6" + mine.getDisplayName());

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (mine.getRequiredPrestige() > playerData.getPrestigeLevel()) {
            player.sendMessage("§7Prestige requis: §d" + mine.getRequiredPrestige() +
                    " §7(actuel: §d" + playerData.getPrestigeLevel() + "§7)");
        }

        if (mine.isVipOnly() && !player.hasPermission("specialmine.vip")) {
            player.sendMessage("§7Statut §6VIP §7requis");
        }

        if (mine.getRequiredPermission() != null && !player.hasPermission(mine.getRequiredPermission())) {
            player.sendMessage("§7Permission requise: §e" + mine.getRequiredPermission());
        }

        String currentRank = plugin.getMineManager().getCurrentRank(player);
        if (mine.getType() == MineData.MineType.NORMAL &&
                !plugin.getMineManager().canAccessMine(player, mine.getId())) {
            player.sendMessage("§7Rang requis: §6" + mine.getRequiredRank().toUpperCase() +
                    " §7(actuel: §6" + currentRank.toUpperCase() + "§7)");
        }
    }

    /**
     * Obtient le nom d'affichage d'un type de mine
     */
    private String getTypeDisplayName(MineData.MineType type) {
        return switch (type) {
            case NORMAL -> "§fNormale";
            case PRESTIGE -> "§dPrestige";
            case VIP -> "§6VIP";
        };
    }

    /**
     * Affiche le message d'aide
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§6╔═══════════════════════════════════╗");
        player.sendMessage("§6║           §f§lCOMMANDES MINES          §6║");
        player.sendMessage("§6╠═══════════════════════════════════╣");
        player.sendMessage("§6║ §e/mine list [type]               §6║");
        player.sendMessage("§6║ §7├─ Liste des mines disponibles   §6║");
        player.sendMessage("§6║                                   §6║");
        player.sendMessage("§6║ §e/mine tp <mine>                 §6║");
        player.sendMessage("§6║ §7├─ Se téléporter à une mine      §6║");
        player.sendMessage("§6║                                   §6║");
        player.sendMessage("§6║ §e/mine info <mine>               §6║");
        player.sendMessage("§6║ §7├─ Informations sur une mine     §6║");
        player.sendMessage("§6║                                   §6║");
        player.sendMessage("§6║ §e/mine accessible                §6║");
        player.sendMessage("§6║ §7├─ Mines que vous pouvez visiter §6║");
        player.sendMessage("§6║                                   §6║");
        player.sendMessage("§6║ §e/mine types                     §6║");
        player.sendMessage("§6║ §7├─ Types de mines disponibles    §6║");
        player.sendMessage("§6║                                   §6║");
        player.sendMessage("§6║ §e/mine search <terme>            §6║");
        player.sendMessage("§6║ §7├─ Rechercher des mines          §6║");
        player.sendMessage("§6║                                   §6║");
        player.sendMessage("§6║ §e/mine current                   §6║");
        player.sendMessage("§6║ §7├─ Mine actuelle                 §6║");
        player.sendMessage("§6║                                   §6║");
        player.sendMessage("§6║ §e/mine stats                     §6║");
        player.sendMessage("§6║ §7├─ Statistiques des mines        §6║");

        if (player.hasPermission("specialmine.admin")) {
            player.sendMessage("§6║                                   §6║");
            player.sendMessage("§6║ §c/mine generate <mine|all>       §6║");
            player.sendMessage("§6║ §7├─ Régénérer une/toutes mines   §6║");
        }

        player.sendMessage("§6╚═══════════════════════════════════╝");

        // Statistiques rapides
        int totalMines = plugin.getMineManager().getAllMines().size();
        int accessibleMines = plugin.getMineManager().getAccessibleMines(player).size();
        String currentMine = plugin.getMineManager().getPlayerCurrentMine(player);

        player.sendMessage("");
        player.sendMessage("§7📊 Mines disponibles: §6" + accessibleMines + "§7/§6" + totalMines);
        player.sendMessage("§7📍 Mine actuelle: " + (currentMine != null ? "§6" + currentMine : "§cAucune"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList(
                    "list", "tp", "info", "types", "accessible", "stats", "search", "current", "help"
            ));

            if (sender.hasPermission("specialmine.admin")) {
                subCommands.add("generate");
            }

            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "list" -> {
                    List<String> types = Arrays.asList("normal", "prestige", "vip");
                    StringUtil.copyPartialMatches(args[1], types, completions);
                }
                case "tp", "info", "generate" -> {
                    // Suggérer les noms de mines accessibles pour tp et info
                    List<String> mineNames = new ArrayList<>();

                    if (sender instanceof Player player) {
                        if (args[0].equalsIgnoreCase("tp")) {
                            // Pour tp, seulement les mines accessibles
                            mineNames = plugin.getMineManager().getAccessibleMines(player).stream()
                                    .map(MineData::getId)
                                    .collect(Collectors.toList());
                        } else {
                            // Pour info et generate, toutes les mines
                            mineNames = plugin.getMineManager().getAllMines().stream()
                                    .map(MineData::getId)
                                    .collect(Collectors.toList());
                        }
                    }

                    StringUtil.copyPartialMatches(args[1], mineNames, completions);

                    // Ajouter "all" pour generate
                    if (args[0].equalsIgnoreCase("generate") && sender.hasPermission("specialmine.admin")) {
                        StringUtil.copyPartialMatches(args[1], List.of("all"), completions);
                    }
                }
                case "search" -> {
                    // Suggérer quelques termes de recherche courants
                    List<String> searchTerms = Arrays.asList("normal", "prestige", "vip", "beacon");
                    StringUtil.copyPartialMatches(args[1], searchTerms, completions);
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}