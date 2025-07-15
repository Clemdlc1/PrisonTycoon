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

            default -> {
                sendHelpMessage(sender);
                return true;
            }
        }
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§e📋 Commandes Mine:");
        sender.sendMessage("§7• §6/mine list §7- Liste toutes les mines");
        sender.sendMessage("§7• §6/mine info <nom> §7- Informations sur une mine");

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
            List<String> subCommands = Arrays.asList("generate", "list", "info", "stats");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("generate") ||
                    args[0].equalsIgnoreCase("info")) {
                Set<String> mines = plugin.getMineManager().getAllMineNames();
                StringUtil.copyPartialMatches(args[1], mines, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
