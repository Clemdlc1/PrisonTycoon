package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
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
 * Commande /changemetier [métier] - Change de métier (payant, cooldown)
 */
public class ChangeMetierCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public ChangeMetierCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Vérification du rang F
        if (!plugin.getProfessionManager().canUnlockProfessions(player)) {
            player.sendMessage("§c❌ Les métiers se débloquent au rang F !");
            String currentRank = plugin.getMineManager().getCurrentRank(player);
            player.sendMessage("§7Votre rang actuel: §e" + currentRank.toUpperCase());
            return true;
        }

        if (args.length == 0) {
            sendUsageMessage(player);
            return true;
        }

        String professionId = args[0].toLowerCase();
        plugin.getProfessionManager().changeProfession(player, professionId);

        return true;
    }

    /**
     * Affiche l'usage de la commande
     */
    private void sendUsageMessage(Player player) {
        player.sendMessage("§e💰 §lChangement de Métier");
        player.sendMessage("");
        player.sendMessage("§6Usage: §e/changemetier <métier>");
        player.sendMessage("");
        player.sendMessage("§7Métiers disponibles:");
        player.sendMessage("§7• §a§lmineur §7- Maître de l'extraction");
        player.sendMessage("§7• §6§lcommercant §7- Maître de l'économie");
        player.sendMessage("§7• §c§lguerrier §7- Maître du combat");
        player.sendMessage("");
        player.sendMessage("§c💸 Coût: §e5000 beacons");
        player.sendMessage("§c⏰ Cooldown: §e24 heures");
        player.sendMessage("");
        player.sendMessage("§e💡 Votre progression dans chaque métier est conservée !");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> professions = Arrays.asList("mineur", "commercant", "guerrier");
            StringUtil.copyPartialMatches(args[0], professions, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}