package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Commande /enchantbook pour le système d'enchantements uniques
 */
public class EnchantmentBookCommand implements CommandExecutor {

    private final PrisonTycoon plugin;

    public EnchantmentBookCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            // Ouvre le menu principal
            plugin.getEnchantmentBookGUI().openEnchantmentBookMenu(player);
            return true;
        }
        return false;
    }
}