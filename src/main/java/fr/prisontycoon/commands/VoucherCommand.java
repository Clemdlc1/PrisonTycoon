package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Commande /voucher pour donner des vouchers aux joueurs
 * Usage: /voucher <joueur> <type> <tier> [quantité]
 */
public class VoucherCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public VoucherCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // Vérification des permissions
        if (!sender.hasPermission("specialmine.admin.voucher")) {
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
        String tierStr = args[2];
        int amount = 1;

        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount <= 0 || amount > 64) {
                    sender.sendMessage("§cQuantité invalide! (1-64)");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§cQuantité invalide! Utilisez un nombre.");
                return true;
            }
        }

        // Trouve le joueur
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§cJoueur non trouvé: " + playerName);
            return true;
        }

        // Parse le type de voucher
        VoucherType type = null;
        for (VoucherType voucherType : VoucherType.values()) {
            if (voucherType.name().equalsIgnoreCase(typeStr) ||
                    voucherType.getDisplayName().equalsIgnoreCase(typeStr)) {
                type = voucherType;
                break;
            }
        }

        if (type == null) {
            sender.sendMessage("§cType de voucher invalide: " + typeStr);
            sender.sendMessage("§7Types disponibles: " + getAvailableVoucherTypes());
            return true;
        }

        // Parse le tier
        int tier;
        try {
            tier = Integer.parseInt(tierStr);
            if (tier < 1 || tier > 10) {
                sender.sendMessage("§cTier invalide! (1-10)");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cTier invalide! Utilisez un nombre entre 1 et 10.");
            return true;
        }

        // Donne le voucher
        boolean success = plugin.getVoucherManager().giveVoucher(target, type, tier, amount);

        if (success) {
            // Message au staff
            sender.sendMessage("§a✅ Voucher donné avec succès!");
            sender.sendMessage("§7Joueur: §e" + target.getName());
            sender.sendMessage("§7Type: " + type.getFullName(tier));
            sender.sendMessage("§7Quantité: §e" + amount);

            // Message au joueur
            target.sendMessage("§a✅ Vous avez reçu un voucher!");
            target.sendMessage("§7De: §e" + sender.getName());
            target.sendMessage("§7Type: " + type.getFullName(tier));
            if (amount > 1) {
                target.sendMessage("§7Quantité: §e" + amount);
            }
            target.sendMessage("§7Utilisez §eclic droit §7pour l'activer!");

            // Log
            plugin.getPluginLogger().info("Voucher donné par " + sender.getName() +
                    " à " + target.getName() + ": " + amount + "x " + type.name() + " tier " + tier);
        } else {
            sender.sendMessage("§cErreur lors de la création du voucher.");
        }

        return true;
    }

    /**
     * Affiche le message d'aide
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6📜 §lCommande Voucher");
        sender.sendMessage("§7Usage: §e/voucher <joueur> <type> <tier> [quantité]");
        sender.sendMessage("");
        sender.sendMessage("§7Types disponibles: §e" + getAvailableVoucherTypes());
        sender.sendMessage("§7Tiers: §e1-10");
        sender.sendMessage("§7Quantité: §e1-64 (optionnel, défaut: 1)");
        sender.sendMessage("");
        sender.sendMessage("§7Exemples:");
        sender.sendMessage("§7- §e/voucher Player123 tokens 5");
        sender.sendMessage("§7- §e/voucher Player123 coins 10 3");
        sender.sendMessage("§7- §e/voucher Player123 experience 7");
    }

    /**
     * Retourne la liste des types de voucher disponibles
     */
    private String getAvailableVoucherTypes() {
        StringBuilder types = new StringBuilder();
        for (VoucherType type : VoucherType.values()) {
            if (!types.isEmpty()) types.append(", ");
            types.append(type.name().toLowerCase());
        }
        return types.toString();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Auto-complétion des joueurs en ligne
            List<String> playerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                playerNames.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[0], playerNames, completions);
        } else if (args.length == 2) {
            // Auto-complétion des types de voucher
            List<String> voucherTypes = new ArrayList<>();
            for (VoucherType type : VoucherType.values()) {
                voucherTypes.add(type.name().toLowerCase());
            }
            StringUtil.copyPartialMatches(args[1], voucherTypes, completions);
        } else if (args.length == 3) {
            // Auto-complétion des tiers
            List<String> tiers = Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
            StringUtil.copyPartialMatches(args[2], tiers, completions);
        } else if (args.length == 4) {
            // Auto-complétion des quantités
            List<String> quantities = Arrays.asList("1", "5", "10", "16", "32", "64");
            StringUtil.copyPartialMatches(args[3], quantities, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}