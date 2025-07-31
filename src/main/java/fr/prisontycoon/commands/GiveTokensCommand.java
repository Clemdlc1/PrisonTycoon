package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Commande /givetokens - Donner des tokens (admin)
 */
public class GiveTokensCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public GiveTokensCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérifie la permission admin
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§cUsage: /givetokens <joueur> <quantité>");
            return true;
        }

        // Récupère le joueur cible
        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cJoueur '" + args[0] + "' introuvable ou hors ligne!");
            return true;
        }

        // Parse la quantité
        long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cQuantité invalide: " + args[1]);
            return true;
        }

        if (amount <= 0) {
            sender.sendMessage("§cLa quantité doit être positive!");
            return true;
        }

        // Donne les tokens
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        playerData.addTokens(amount);

        String formattedAmount = NumberFormatter.format(amount);

        sender.sendMessage("§a✅ " + formattedAmount + " tokens donnés à " + target.getName());
        target.sendMessage("§a📥 Vous avez reçu " + formattedAmount + " tokens de la part d'un administrateur!");

        // Son de notification
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        plugin.getPluginLogger().info("§7" + sender.getName() + " a donné " +
                formattedAmount + " tokens à " + target.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Auto-complétion des noms de joueurs en ligne
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (StringUtil.startsWithIgnoreCase(player.getName(), args[0])) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // Suggestions de quantités
            List<String> amounts = Arrays.asList("100", "1000", "10000", "100000", "1000000");
            StringUtil.copyPartialMatches(args[1], amounts, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}
