package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande /pickaxe - Obtenir la pioche légendaire
 */
public class PickaxeCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public PickaxeCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Vérifie la permission
        if (!player.hasPermission("specialmine.pickaxe")) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        // Vérifie si le joueur a déjà une pioche
        if (plugin.getPickaxeManager().hasLegendaryPickaxe(player)) {
            player.sendMessage("§cVous avez déjà une pioche légendaire!");
            return true;
        }

        // Vérifie si l'inventaire a de la place
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cVotre inventaire est plein! Libérez de l'espace d'abord.");
            return true;
        }

        // Crée et donne la pioche
        plugin.getPickaxeManager().createLegendaryPickaxe(player);

        player.sendMessage("§a✅ Pioche légendaire obtenue!");
        player.sendMessage("§7Utilisez §eclic droit §7pour ouvrir le menu d'enchantements.");
        player.sendMessage("§7Utilisez §eShift + clic droit §7pour l'escalateur (si débloqué).");

        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        plugin.getPluginLogger().info("§7Pioche légendaire donnée à: " + player.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return new ArrayList<>(); // Pas de complétion pour cette commande
    }
}
