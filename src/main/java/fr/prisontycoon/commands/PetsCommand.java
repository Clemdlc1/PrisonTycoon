package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande /pets pour accéder au menu des compagnons
 */
public class PetsCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public PetsCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c✖ Cette commande est réservée aux joueurs.");
            return true;
        }

        // Commande simple : ouvrir le menu pets
        if (args.length == 0) {
            plugin.getPetsMenuGUI().openPetsMenu(player);
            return true;
        }

        // Sous-commandes optionnelles pour les admins (futures)
        if (args.length >= 1) {
            String subCommand = args[0].toLowerCase();
            
            switch (subCommand) {
                case "help", "aide" -> {
                    sendHelpMessage(player);
                    return true;
                }
                
                default -> {
                    player.sendMessage("§c✖ Sous-commande inconnue. Utilisez §e/pets help §cpour voir l'aide.");
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Sous-commandes disponibles
            completions.add("help");
            completions.add("aide");
            
            // Filtrer selon ce que l'utilisateur a tapé
            String input = args[0].toLowerCase();
            completions.removeIf(completion -> !completion.toLowerCase().startsWith(input));
        }
        
        return completions;
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6🐾 §lAide - Système de Compagnons");
        player.sendMessage("");
        player.sendMessage("§e📋 Commandes disponibles:");
        player.sendMessage("  §7• §e/pets §8- §7Ouvrir le menu des compagnons");
        player.sendMessage("  §7• §e/pets help §8- §7Afficher cette aide");
        player.sendMessage("");
        player.sendMessage("§e🎮 Comment utiliser les pets:");
        player.sendMessage("  §7• Ouvrez des §eboîtes de pets §7pour obtenir des compagnons");
        player.sendMessage("  §7• Équipez jusqu'à §a3 pets §7pour bénéficier de leurs bonus");
        player.sendMessage("  §7• Nourrissez vos pets pour augmenter leur §dXP §7et leur §ecroissance");
        player.sendMessage("  §7• Certaines combinaisons de 3 pets créent des §dsynergies puissantes§7!");
        player.sendMessage("");
        player.sendMessage("§e✨ Types de raretés:");
        player.sendMessage("  §f• §lCommun §8- §7Bonus de base, faciles à obtenir");
        player.sendMessage("  §5• §lRare §8- §7Bonus moyens, plus rares");
        player.sendMessage("  §d• §lÉpique §8- §7Bonus élevés, difficiles à obtenir");
        player.sendMessage("  §6• §lMythique §8- §7Bonus exceptionnels, extrêmement rares");
        player.sendMessage("");
        player.sendMessage("§e💡 Astuce: §7Les pets équipés vous suivent visuellement en jeu!");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
}