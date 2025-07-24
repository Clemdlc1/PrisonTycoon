package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande /repair - Ouvre le menu de réparation de la pioche légendaire
 */
public class RepairCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public RepairCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Vérifie la permission
        if (!player.hasPermission("specialmine.repair")) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        // Vérifie si le joueur a une pioche légendaire
        if (!plugin.getPickaxeManager().hasLegendaryPickaxe(player)) {
            player.sendMessage("§c❌ Vous n'avez pas de pioche légendaire!");
            player.sendMessage("§7Utilisez §e/pickaxe §7pour en obtenir une.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // Vérifie si la pioche est dans l'inventaire
        if (plugin.getPickaxeManager().findPlayerPickaxe(player) == null) {
            player.sendMessage("§c❌ Pioche légendaire introuvable dans votre inventaire!");
            player.sendMessage("§7Assurez-vous qu'elle soit dans votre inventaire.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // Ouvre le menu de réparation
        plugin.getPickaxeRepairGUI().openRepairGUI(player);

        // Message de bienvenue
        player.sendMessage("§e🔨 Menu de réparation ouvert!");
        player.sendMessage("§7Cliquez directement sur les pourcentages pour réparer votre pioche.");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>(); // Pas de complétion pour cette commande
    }
}