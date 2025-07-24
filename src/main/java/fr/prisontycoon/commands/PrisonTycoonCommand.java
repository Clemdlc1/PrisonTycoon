package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.EconomyManager;
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
 * Commande /prisontycoon - Commande principale d'information et de gestion
 */
public class PrisonTycoonCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public PrisonTycoonCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendMainInfo(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> {
                if (!sender.hasPermission("specialmine.admin")) {
                    sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
                    return true;
                }

                sender.sendMessage("§7Rechargement de la configuration...");
                try {
                    plugin.getConfigManager().reloadConfiguration();
                    sender.sendMessage("§a✅ Configuration rechargée avec succès!");
                } catch (Exception e) {
                    sender.sendMessage("§cErreur lors du rechargement: " + e.getMessage());
                    plugin.getPluginLogger().severe("Erreur reload config:");
                    e.printStackTrace();
                }

                return true;
            }

            case "stats" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
                    return true;
                }

                showPlayerStats(player);
                return true;
            }

            case "economy" -> {
                if (!sender.hasPermission("specialmine.admin")) {
                    sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
                    return true;
                }

                showEconomyStats(sender);
                return true;
            }

            case "top" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /prisontycoon top <coins|tokens|xp|blocks>");
                    return true;
                }

                showTopPlayers(sender, args[1]);
                return true;
            }

            default -> {
                sendHelpMessage(sender);
                return true;
            }
        }
    }

    private void sendMainInfo(CommandSender sender) {
        sender.sendMessage("§6✨ §lPrisonTycoon Plugin §6✨");
        sender.sendMessage("§7Version: §e1.0.0 §7| Auteur: §ePrisonCore");
        sender.sendMessage("§7Plugin de minage avec pioche légendaire et enchantements custom");
        sender.sendMessage("");
        sender.sendMessage("§eCommandes disponibles:");
        sender.sendMessage("§7/pickaxe §8- §7Obtenir la pioche légendaire");
        sender.sendMessage("§7/mine §8- §7Gérer les mines");
        sender.sendMessage("§7/prisontycoon help §8- §7Aide complète");
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§e📋 Aide PrisonTycoon:");
        sender.sendMessage("§7/prisontycoon §8- §7Informations générales");
        sender.sendMessage("§7/prisontycoon stats §8- §7Vos statistiques");
        sender.sendMessage("§7/prisontycoon top <type> §8- §7Classements");

        if (sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c🔧 Commandes admin:");
            sender.sendMessage("§7/prisontycoon reload §8- §7Recharger la config");
            sender.sendMessage("§7/prisontycoon economy §8- §7Stats économiques");
            sender.sendMessage("§7/givetokens <joueur> <qty> §8- §7Donner des tokens");
        }
    }

    private void showPlayerStats(Player player) {
        var playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        var balance = plugin.getEconomyManager().getBalance(player);

        player.sendMessage("§e📊 Vos statistiques:");
        player.sendMessage("§7Coins: §6" + NumberFormatter.format(balance.));
        player.sendMessage("§7Tokens: §e" + NumberFormatter.format(balance.getTokens()));
        player.sendMessage("§7Expérience: §a" + NumberFormatter.format(balance.getExperience()));
        player.sendMessage("§7Blocs minés: §b" + NumberFormatter.format(playerData.getTotalBlocksMined()));

        // Enchantements actifs
        var enchantments = playerData.getEnchantmentLevels();
        if (!enchantments.isEmpty()) {
            player.sendMessage("§7Enchantements actifs: §e" + enchantments.size());
        }

        // Combustion
        if (playerData.getCombustionLevel() > 0) {
            player.sendMessage("§7Combustion: §c" + playerData.getCombustionLevel() + "/1000 " +
                    "(§6x" + String.format("%.2f", playerData.getCombustionMultiplier()) + "§7)");
        }

        // Abondance
        if (playerData.isAbundanceActive()) {
            player.sendMessage("§7Abondance: §6✨ ACTIVE");
        }
    }

    private void showEconomyStats(CommandSender sender) {
        var stats = plugin.getEconomyManager().getGlobalEconomicStats();

        sender.sendMessage("§e💰 Statistiques économiques globales:");
        sender.sendMessage("§7Joueurs actifs: §e" + stats.get("active-players"));
        sender.sendMessage("§7Total coins: §6" + NumberFormatter.format((Long) stats.get("total-coins")));
        sender.sendMessage("§7Total tokens: §e" + NumberFormatter.format((Long) stats.get("total-tokens")));
        sender.sendMessage("§7Total XP: §a" + NumberFormatter.format((Long) stats.get("total-experience")));
        sender.sendMessage("§7Blocs minés: §b" + NumberFormatter.format((Long) stats.get("total-blocks-mined")));

        if ((Integer) stats.get("active-players") > 0) {
            sender.sendMessage("§7Moyennes par joueur:");
            sender.sendMessage("§7  Coins: §6" + NumberFormatter.format((Long) stats.get("average-coins")));
            sender.sendMessage("§7  Tokens: §e" + NumberFormatter.format((Long) stats.get("average-tokens")));
            sender.sendMessage("§7  XP: §a" + NumberFormatter.format((Long) stats.get("average-experience")));
        }
    }

    private void showTopPlayers(CommandSender sender, String type) {
        var economyType = switch (type.toLowerCase()) {
            case "coins", "coin" -> EconomyManager.EconomicType.COINS;
            case "tokens", "token" -> EconomyManager.EconomicType.TOKENS;
            case "xp", "exp", "experience" -> EconomyManager.EconomicType.EXPERIENCE;
            case "blocks", "block" -> EconomyManager.EconomicType.TOTAL_BLOCKS;
            default -> null;
        };

        if (economyType == null) {
            sender.sendMessage("§cType invalide! Utilisez: coins, tokens, xp ou blocks");
            return;
        }

        var rankings = plugin.getEconomyManager().getTopPlayers(economyType, 10);

        if (rankings.isEmpty()) {
            sender.sendMessage("§cAucune donnée disponible pour le classement.");
            return;
        }

        String typeDisplay = switch (economyType) {
            case COINS -> "Coins";
            case TOKENS -> "Tokens";
            case EXPERIENCE -> "Expérience";
            case TOTAL_BLOCKS -> "Blocs minés";
        };

        sender.sendMessage("§e🏆 Top 10 - " + typeDisplay + ":");

        for (int i = 0; i < rankings.size(); i++) {
            var ranking = rankings.get(i);
            String medal = switch (i) {
                case 0 -> "§6🥇";
                case 1 -> "§7🥈";
                case 2 -> "§c🥉";
                default -> "§7" + (i + 1) + ".";
            };

            sender.sendMessage(medal + " §e" + ranking.getPlayerName() +
                    " §7- §a" + NumberFormatter.format(ranking.getValue()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("stats", "top", "help"));

            if (sender.hasPermission("specialmine.admin")) {
                subCommands.addAll(Arrays.asList("reload", "economy"));
            }

            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            List<String> types = Arrays.asList("coins", "tokens", "xp", "blocks");
            StringUtil.copyPartialMatches(args[1], types, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}