package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commande principale des automineurs
 */
public class AutominerCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public AutominerCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        // Commande sans arguments : ouvre le menu principal
        if (args.length == 0) {
            plugin.getAutominerGUI().openMainMenu(player);
            return true;
        }

        // Sous-commandes administratives
        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            return handleGiveCommand(player, args);
        }

        // Commande d'aide
        player.sendMessage("§6=== Commandes Automineur ===");
        player.sendMessage("§e/autominer §7- Ouvre le menu principal");
        player.sendMessage("§e/autominer give <type> §7- Donne un automineur (admin)");
        player.sendMessage("§7Types disponibles: pierre, fer, or, diamant, emeraude, beacon");

        return true;
    }

    /**
     * Gère la commande give
     */
    private boolean handleGiveCommand(Player player, String[] args) {
        // Vérification des permissions
        if (!player.hasPermission("prisontycoon.autominer.give")) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        String typeName = args[1].toUpperCase();
        AutominerType type;

        try {
            type = AutominerType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cType d'automineur invalide! Types disponibles:");
            player.sendMessage("§7pierre, fer, or, diamant, emeraude, beacon");
            return true;
        }

        // Créer et donner l'automineur
        ItemStack autominer = plugin.getAutominerManager().createAutominer(type);

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cVotre inventaire est plein!");
            return true;
        }

        player.getInventory().addItem(autominer);
        player.sendMessage("§a✓ Automineur " + type.getDisplayName() + " donné!");

        // Log administratif
        plugin.getPluginLogger().info("§7" + player.getName() + " a reçu un automineur " + type.getDisplayName());

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("give");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            completions.addAll(Arrays.asList("pierre", "fer", "or", "diamant", "emeraude", "beacon"));
        }

        return completions;
    }
}