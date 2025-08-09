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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
            sender.sendMessage("§eUsage: /" + label + " <save|load|dump> <joueur>");
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];

        UUID targetId = null;
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            targetId = online.getUniqueId();
        } else {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetName);
            if (offline != null && offline.hasPlayedBefore()) {
                targetId = offline.getUniqueId();
            }
        }

        if (targetId == null) {
            sender.sendMessage("§cJoueur introuvable: §e" + targetName);
            return true;
        }

        switch (action) {
            case "save" -> {
                // Assure la présence dans le cache puis sauvegarde immédiate
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(targetId);
                plugin.getPlayerDataManager().savePlayerNow(targetId);
                sender.sendMessage("§aSauvegardé: §e" + data.getPlayerName());
            }
            case "load" -> {
                // Recharge depuis la BDD (réinitialise le cache)
                plugin.getPlayerDataManager().reloadPlayerData(targetId);
                sender.sendMessage("§aRechargé depuis la base: §e" + targetName);
            }
            case "dump" -> {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(targetId);
                String activeProfession = data.getActiveProfession();
                sender.sendMessage("§7--- §ePlayerData Dump§7 ---");
                sender.sendMessage("§7Joueur: §e" + data.getPlayerName() + " §8(" + targetId + ")");
                sender.sendMessage("§7Métier actif: §e" + (activeProfession == null ? "aucun" : activeProfession));
                sender.sendMessage("§7Niveaux métiers: §e" + data.getAllProfessionLevels());
                sender.sendMessage("§7XP métiers: §e" + data.getAllProfessionXP());
                sender.sendMessage("§7Talents: §e" + data.getAllTalentLevels().keySet());
            }
            default -> sender.sendMessage("§cAction inconnue. Utilisez: save, load, dump");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> sub = Arrays.asList("save", "load", "dump");
            StringUtil.copyPartialMatches(args[0], sub, completions);
        } else if (args.length == 2) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[1], players, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}