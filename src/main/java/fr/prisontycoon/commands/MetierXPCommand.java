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
 * Commande admin /metierxp <nombre> - Donne de l'XP métier au joueur
 */
public class MetierXPCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public MetierXPCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        if (!player.hasPermission("specialmine.admin")) {
            player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /metierxp <nombre>");
            return true;
        }

        try {
            int xpAmount = Integer.parseInt(args[0]);

            if (xpAmount <= 0) {
                player.sendMessage("§cLe nombre doit être positif !");
                return true;
            }

            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            String activeProfession = playerData.getActiveProfession();

            if (activeProfession == null) {
                player.sendMessage("§c❌ Vous devez avoir un métier actif !");
                return true;
            }

            // Ajoute l'XP métier
            plugin.getProfessionManager().addProfessionXP(player, activeProfession, xpAmount);

            player.sendMessage("§a✅ Vous avez reçu §e" + NumberFormatter.format(xpAmount) +
                    " XP §apour le métier §e" + activeProfession + " §a!");

        } catch (NumberFormatException e) {
            player.sendMessage("§cNombre invalide !");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> amounts = Arrays.asList("100", "500", "1000", "5000", "10000");
            StringUtil.copyPartialMatches(args[0], amounts, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}