package fr.prisoncore.prisoncore.prisonTycoon.commands;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.cristaux.CristalType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commande pour gérer les cristaux
 * Usage: /cristal <niveau> [joueur] [type]
 */
public class CristalCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public CristalCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérification de permission (admin/op)
        if (!sender.hasPermission("specialmine.admin") && !sender.isOp()) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        // Parse du niveau
        int niveau;
        try {
            niveau = Integer.parseInt(args[0]);
            if (niveau < 1 || niveau > 20) {
                sender.sendMessage("§cLe niveau doit être entre 1 et 20!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cNiveau invalide: " + args[0]);
            return true;
        }

        // Détermine le joueur cible
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cJoueur introuvable: " + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cVous devez spécifier un joueur depuis la console!");
                return true;
            }
            target = (Player) sender;
        }

        // Détermine le type (si spécifié)
        CristalType type = null;
        if (args.length >= 3) {
            try {
                type = CristalType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cType de cristal invalide: " + args[2]);
                sender.sendMessage("§7Types disponibles: " + Arrays.toString(CristalType.values()));
                return true;
            }
        }

        // Donne le cristal
        boolean success;
        if (type != null) {
            // Cristal avec type spécifique (révélé)
            success = plugin.getCristalManager().giveCristalToPlayer(target, niveau, type);
            if (success) {
                sender.sendMessage("§a✓ Cristal §d" + type.getDisplayName() + " §7(Niveau " + niveau +
                        ") §adonné à §e" + target.getName() + "§a!");
            }
        } else {
            // Cristal vierge
            success = plugin.getCristalManager().giveCristalToPlayer(target, niveau);
            if (success) {
                sender.sendMessage("§a✓ Cristal vierge §7(Niveau " + niveau +
                        ") §adonné à §e" + target.getName() + "§a!");
            }
        }

        if (!success) {
            sender.sendMessage("§cErreur lors de la création du cristal!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!sender.hasPermission("specialmine.admin") && !sender.isOp()) {
            return suggestions;
        }

        if (args.length == 1) {
            // Suggestions de niveau
            for (int i = 1; i <= 20; i++) {
                String level = String.valueOf(i);
                if (level.startsWith(args[0])) {
                    suggestions.add(level);
                }
            }
        } else if (args.length == 2) {
            // Suggestions de joueurs
            String input = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input)) {
                    suggestions.add(player.getName());
                }
            }
        } else if (args.length == 3) {
            // Suggestions de types de cristaux
            String input = args[2].toLowerCase();
            for (CristalType type : CristalType.values()) {
                if (type.name().toLowerCase().startsWith(input)) {
                    suggestions.add(type.name().toLowerCase());
                }
            }
        }

        return suggestions;
    }

    /**
     * Affiche l'usage de la commande
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§d✨ §lCommande Cristal §d✨");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§e/cristal <niveau> §8- §7Donne un cristal vierge");
        sender.sendMessage("§e/cristal <niveau> <joueur> §8- §7Donne à un joueur");
        sender.sendMessage("§e/cristal <niveau> <joueur> <type> §8- §7Cristal révélé");
        sender.sendMessage("");
        sender.sendMessage("§7Niveaux: §f1-20");
        sender.sendMessage("§7Types: §f" + Arrays.toString(CristalType.values()));
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
}