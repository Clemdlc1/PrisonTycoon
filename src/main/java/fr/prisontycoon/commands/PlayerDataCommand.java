package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerDataCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public PlayerDataCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§eUsage: /" + label + " <save|load|dump> <joueur> [colonne]");
            try {
                List<String> cols = plugin.getPlayerDataManager().getAllPlayerColumns();
                if (!cols.isEmpty()) {
                    sender.sendMessage("§7Colonnes: §f" + String.join(", ", cols));
                }
            } catch (Exception ignored) {}
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];
        String column = args.length >= 3 ? args[2].toLowerCase() : null;

        UUID targetId = resolvePlayerUUID(targetName);
        if (targetId == null) {
            sender.sendMessage("§cJoueur introuvable: §e" + targetName);
            return true;
        }

        switch (action) {
            case "save" -> {
                if (column == null) {
                    // Sauvegarde complète
                    ensureCached(targetId);
                    plugin.getPlayerDataManager().savePlayerNow(targetId);
                    sender.sendMessage("§aSauvegardé: §e" + targetName);
                } else {
                    boolean ok = plugin.getPlayerDataManager().saveSingleColumn(targetId, column);
                    if (ok) sender.sendMessage("§aSauvegardé colonne §e" + column + " §apour §e" + targetName);
                    else sender.sendMessage("§cColonne inconnue: §e" + column);
                }
            }
            case "load" -> {
                if (column == null) {
                    plugin.getPlayerDataManager().reloadPlayerData(targetId);
                    sender.sendMessage("§aRechargé depuis la base: §e" + targetName);
                } else {
                    boolean ok = plugin.getPlayerDataManager().loadSingleColumn(targetId, column);
                    if (ok) sender.sendMessage("§aRechargé colonne §e" + column + " §apour §e" + targetName);
                    else sender.sendMessage("§cColonne inconnue: §e" + column);
                }
            }
            default -> sender.sendMessage("§cAction inconnue. Utilisez: save, load, dump");
        }

        return true;
    }

    private UUID resolvePlayerUUID(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) return offline.getUniqueId();
        return null;
    }

    private void ensureCached(UUID playerId) {
        plugin.getPlayerDataManager().getPlayerData(playerId);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> sub = Arrays.asList("save", "load");
            StringUtil.copyPartialMatches(args[0], sub, completions);
        } else if (args.length == 2) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[1], players, completions);
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("load"))) {
            List<String> columns = plugin.getPlayerDataManager().getAllPlayerColumns();
            StringUtil.copyPartialMatches(args[2], columns, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}