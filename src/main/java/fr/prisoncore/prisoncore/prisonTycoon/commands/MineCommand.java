package fr.prisoncore.prisoncore.prisonTycoon.commands;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*; /**
 * Commande /mine - Gérer les mines
 */
public class MineCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public MineCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("specialmine.mine")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "generate", "regen", "regenerate" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /mine generate <nom_mine>");
                    return true;
                }

                String mineName = args[1];
                Player player = sender instanceof Player ? (Player) sender : null;

                boolean success = plugin.getMineManager().generateMine(mineName, player);
                if (!success) {
                    sender.sendMessage("§cImpossible de générer la mine '" + mineName + "'.");
                }

                return true;
            }

            case "list" -> {
                Set<String> mines = plugin.getMineManager().getAllMineNames();

                if (mines.isEmpty()) {
                    sender.sendMessage("§cAucune mine configurée.");
                    return true;
                }

                sender.sendMessage("§e📋 Mines configurées (" + mines.size() + "):");
                for (String mineName : mines) {
                    String status = plugin.getMineManager().isRegenerating(mineName) ?
                            "§c(En régénération)" : "§a(Prête)";
                    sender.sendMessage("§7• §6" + mineName + " " + status);
                }

                return true;
            }

            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /mine info <nom_mine>");
                    return true;
                }

                String mineName = args[1];
                var mineData = plugin.getConfigManager().getMineData(mineName);

                if (mineData == null) {
                    sender.sendMessage("§cMine '" + mineName + "' introuvable!");
                    return true;
                }

                sender.sendMessage("§e📊 Informations sur la mine: §6" + mineName);
                sender.sendMessage("§7Volume: §e" + NumberFormatter.format(mineData.getVolume()) + " blocs");
                sender.sendMessage("§7Coordonnées:");
                sender.sendMessage("§7  Min: §e" + formatLocation(mineData.getMinCorner()));
                sender.sendMessage("§7  Max: §e" + formatLocation(mineData.getMaxCorner()));
                sender.sendMessage("§7Composition:");

                var composition = mineData.getBlockComposition();
                for (var entry : composition.entrySet()) {
                    double percentage = entry.getValue() * 100;
                    sender.sendMessage("§7  • §e" + entry.getKey().name() +
                            " §7(§a" + String.format("%.1f%%", percentage) + "§7)");
                }

                return true;
            }

            case "stats" -> {
                var stats = plugin.getMineManager().getMineStats();

                sender.sendMessage("§e📈 Statistiques des mines:");
                sender.sendMessage("§7Total de mines: §e" + stats.get("total-mines"));
                sender.sendMessage("§7Mines en régénération: §e" + stats.get("regenerating-mines"));
                sender.sendMessage("§7Total de blocs: §e" + NumberFormatter.format((Long) stats.get("total-blocks")));

                return true;
            }
            case "permission", "perm" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
                    return true;
                }

                Player player = (Player) sender;

                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /mine permission <nom_mine>");
                    return true;
                }

                String mineName = args[1];

                // Vérifie que la mine existe
                var mineData = plugin.getConfigManager().getMineData(mineName);
                if (mineData == null) {
                    sender.sendMessage("§cMine '" + mineName + "' introuvable!");
                    return true;
                }

                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

                // Vérifie si le joueur a déjà cette permission ou une supérieure
                String currentHighest = playerData.getHighestMinePermission();
                if (currentHighest != null && currentHighest.compareTo(mineName) >= 0) {
                    sender.sendMessage("§aVous avez déjà accès à la mine '" + mineName + "' (permission actuelle: '" + currentHighest + "')!");
                    return true;
                }

                // Logique d'attribution de permission (exemple simple)
                // Ici, vous pouvez ajouter vos propres conditions (niveau, argent, etc.)
                if (canPlayerObtainMinePermission(player, mineName)) {
                    // Efface les anciennes permissions et ajoute la nouvelle (logique cumulative)
                    playerData.clearMinePermissions();
                    plugin.getPlayerDataManager().addMinePermissionToPlayer(player.getUniqueId(), mineName);
                    sender.sendMessage("§a✅ Permission accordée pour la mine '" + mineName + "'!");
                    sender.sendMessage("§7Vous pouvez maintenant miner dans les mines A à " + mineName.toUpperCase() + ".");
                } else {
                    sender.sendMessage("§cVous ne remplissez pas les conditions pour accéder à la mine '" + mineName + "'!");
                    // Ici, vous pouvez afficher les conditions requises
                    sendMineRequirements(player, mineName);
                }

                return true;
            }

            default -> {
                sendHelpMessage(sender);
                return true;
            }
        }
    }

    /**
     * Vérifie si un joueur peut obtenir la permission pour une mine
     * VOUS POUVEZ MODIFIER CETTE LOGIQUE SELON VOS BESOINS
     */
    private boolean canPlayerObtainMinePermission(Player player, String mineName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Extraire la lettre de la mine du format "mine-X" ou juste "X"
        String mineChar;
        if (mineName.startsWith("mine-")) {
            mineChar = mineName.substring(5); // Récupère "a" de "mine-a"
        } else {
            mineChar = mineName; // Si c'est déjà juste "a"
        }

        // Logique cumulative : pour obtenir la permission "b", il faut déjà avoir "a"
        // Exception : la mine "a" est toujours accessible
        if (mineChar.equals("a")) {
            return true; // Première mine, toujours accessible
        }

        // Pour les autres mines, vérifier qu'on a la mine précédente
        char currentMineChar = mineChar.charAt(0);
        if (currentMineChar > 'a') {
            char previousMineChar = (char) (currentMineChar - 1);
            String previousMineName = String.valueOf(previousMineChar);

            // Vérifie qu'on a au moins la permission précédente
            String currentHighest = playerData.getHighestMinePermission();
            if (currentHighest == null || currentHighest.compareTo(previousMineName) < 0) {
                return false; // N'a pas la mine précédente
            }
        }
        return true; // Conditions remplies
    }

    /**
     * Affiche les prérequis pour une mine
     */
    private void sendMineRequirements(Player player, String mineName) {
        player.sendMessage("§c📋 Conditions requises pour la mine '" + mineName + "':");

        // Extraire la lettre de la mine du format "mine-X" ou juste "X"
        String mineChar;
        if (mineName.startsWith("mine-")) {
            mineChar = mineName.substring(5); // Récupère "a" de "mine-a"
        } else {
            mineChar = mineName;
        }

        if (!mineChar.equals("a")) {
            char currentChar = mineChar.charAt(0);
            char previousChar = (char) (currentChar - 1);
            String previousMineName = String.valueOf(previousChar);

            player.sendMessage("§7• Avoir accès à la mine '" + previousMineName + "'");
        }

        // Ici, vous pouvez ajouter d'autres conditions :
        // player.sendMessage("§7• Niveau minimum: 10");
        // player.sendMessage("§7• Argent requis: 10,000 coins");
        // etc.
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§e📋 Commandes Mine:");
        sender.sendMessage("§7• §6/mine list §7- Liste toutes les mines");
        sender.sendMessage("§7• §6/mine info <nom> §7- Informations sur une mine");
        sender.sendMessage("§7• §6/mine permission <nom> §7- Obtenir la permission pour une mine");

        if (sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c🔧 Admin:");
            sender.sendMessage("§7• §6/mine generate <nom> §7- Régénère une mine");
            sender.sendMessage("§7• §6/mine stats §7- Statistiques globales");
        }
    }

    private String formatLocation(org.bukkit.Location location) {
        return String.format("%.0f, %.0f, %.0f",
                location.getX(), location.getY(), location.getZ());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("generate", "list", "info", "permission", "stats");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("generate") ||
                    args[0].equalsIgnoreCase("info") ||
                    args[0].equalsIgnoreCase("permission")) {
                Set<String> mines = plugin.getMineManager().getAllMineNames();
                StringUtil.copyPartialMatches(args[1], mines, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
