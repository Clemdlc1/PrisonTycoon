package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Commande /giveboost pour donner des items boost aux joueurs
 * Usage: /giveboost <joueur> <type> <durée_minutes> [bonus_%] [quantité]
 */
public class GiveBoostCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public GiveBoostCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérification des permissions
        if (!sender.hasPermission("specialmine.admin.boost")) {
            sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length < 3) {
            sendHelpMessage(sender);
            return true;
        }

        // Parse les arguments
        String playerName = args[0];
        String typeStr = args[1];
        String durationStr = args[2];
        double bonus = 0.0; // Sera défini par défaut selon le type
        int amount = 1;

        // Trouve le joueur
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§cJoueur non trouvé: " + playerName);
            return true;
        }

        // Parse le type de boost
        BoostType type = BoostType.fromString(typeStr);
        if (type == null) {
            sender.sendMessage("§cType de boost invalide: " + typeStr);
            sender.sendMessage("§7Types disponibles: " + getAvailableBoostTypes());
            return true;
        }

        // Parse la durée
        int durationMinutes;
        try {
            durationMinutes = Integer.parseInt(durationStr);
            if (durationMinutes <= 0 || durationMinutes > 1440) { // Max 24 heures
                sender.sendMessage("§cDurée invalide! (1-1440 minutes)");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cDurée invalide! Utilisez un nombre de minutes.");
            return true;
        }

        // Parse le bonus (optionnel)
        if (args.length >= 4) {
            try {
                bonus = Double.parseDouble(args[3]);
                if (bonus <= 0 || bonus > 500) { // Max 500%
                    sender.sendMessage("§cBonus invalide! (1-500%)");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cBonus invalide! Utilisez un pourcentage.");
                return true;
            }
        } else {
            // Utilise la valeur par défaut du type
            bonus = type.getBonusPercentage();
        }

        // Parse la quantité (optionnel)
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
                if (amount <= 0 || amount > 64) {
                    sender.sendMessage("§cQuantité invalide! (1-64)");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cQuantité invalide! Utilisez un nombre.");
                return true;
            }
        }

        // Vérifie l'espace dans l'inventaire
        if (target.getInventory().firstEmpty() == -1) {
            sender.sendMessage("§cInventaire du joueur plein!");
            return true;
        }

        // Crée et donne l'item boost
        ItemStack boostItem = plugin.getBoostManager().createBoostItem(type, durationMinutes, bonus);
        boostItem.setAmount(amount);

        target.getInventory().addItem(boostItem);

        // Messages de succès
        sender.sendMessage("§a✅ Item boost donné avec succès!");
        sender.sendMessage("§7Joueur: §e" + target.getName());
        sender.sendMessage("§7Type: " + type.getFormattedName());
        sender.sendMessage("§7Durée: §e" + durationMinutes + " minutes");
        sender.sendMessage("§7Bonus: " + type.getColor() + "+" + String.format("%.0f", bonus) + "%");
        if (amount > 1) {
            sender.sendMessage("§7Quantité: §e" + amount);
        }

        // Message au joueur
        target.sendMessage("§a✅ Vous avez reçu un item boost!");
        target.sendMessage("§7De: §e" + sender.getName());
        target.sendMessage("§7Type: " + type.getFormattedName());
        target.sendMessage("§7Utilisez §eclic droit §7pour l'activer!");
        target.sendMessage("§c⚠ Maximum 1 boost par type actif à la fois");

        // Log
        plugin.getPluginLogger().info("Item boost donné par " + sender.getName() +
                " à " + target.getName() + ": " + amount + "x " + type.name() +
                " (" + durationMinutes + "min, +" + bonus + "%)");

        return true;
    }

    /**
     * Affiche le message d'aide
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6⚡ §lCommande Give Boost");
        sender.sendMessage("§7Usage: §e/giveboost <joueur> <type> <durée> [bonus] [quantité]");
        sender.sendMessage("");
        sender.sendMessage("§7Types disponibles: §e" + getAvailableBoostTypes());
        sender.sendMessage("§7Durée: §e1-1440 minutes");
        sender.sendMessage("§7Bonus: §e1-500% (optionnel, défaut selon type)");
        sender.sendMessage("§7Quantité: §e1-64 (optionnel, défaut: 1)");
        sender.sendMessage("");
        sender.sendMessage("§7Exemples:");
        sender.sendMessage("§7- §e/giveboost Player123 token-greed 60");
        sender.sendMessage("§7- §e/giveboost Player123 sell-boost 120 75 5");
        sender.sendMessage("§7- §e/giveboost Player123 global 30 25");
    }

    /**
     * Retourne la liste des types de boost disponibles
     */
    private String getAvailableBoostTypes() {
        StringBuilder types = new StringBuilder();
        for (BoostType type : BoostType.values()) {
            if (types.length() > 0) types.append(", ");
            types.append(type.name().toLowerCase().replace("_", "-"));
        }
        return types.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Auto-complétion des joueurs en ligne
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[0], playerNames, completions);
        } else if (args.length == 2) {
            // Auto-complétion des types de boost
            List<String> boostTypes = new ArrayList<>();
            for (BoostType type : BoostType.values()) {
                boostTypes.add(type.name().toLowerCase().replace("_", "-"));
            }
            StringUtil.copyPartialMatches(args[1], boostTypes, completions);
        } else if (args.length == 3) {
            // Auto-complétion des durées
            List<String> durations = Arrays.asList("30", "60", "120", "180", "360", "720");
            StringUtil.copyPartialMatches(args[2], durations, completions);
        } else if (args.length == 4) {
            // Auto-complétion des bonus
            List<String> bonuses = Arrays.asList("25", "50", "75", "100", "150", "200");
            StringUtil.copyPartialMatches(args[3], bonuses, completions);
        } else if (args.length == 5) {
            // Auto-complétion des quantités
            List<String> quantities = Arrays.asList("1", "5", "10", "16", "32", "64");
            StringUtil.copyPartialMatches(args[4], quantities, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}