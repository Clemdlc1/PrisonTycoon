package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.SellHandManager.SellHandType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Commande d'administration pour les Tanks et Sell Hands
 * Usage: /tankadmin <give|reload> [args...]
 */
public class TankAdminCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public TankAdminCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // Vérifier les permissions
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGiveCommand(sender, args);
            case "reload" -> handleReloadCommand(sender);
            default -> sendHelpMessage(sender);
        }

        return true;
    }

    /**
     * Gère la sous-commande "give"
     */
    private void handleGiveCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c❌ Usage: /tankadmin give <tank|sellhand> <joueur> [type]");
            return;
        }

        String itemType = args[1].toLowerCase();
        String playerName = args[2];

        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§c❌ Joueur '" + playerName + "' introuvable!");
            return;
        }

        switch (itemType) {
            case "tank" -> giveTank(sender, target);
            case "sellhand" -> giveSellHand(sender, target, args);
            default -> sender.sendMessage("§c❌ Type d'item invalide! Utilisez 'tank' ou 'sellhand'");
        }
    }

    /**
     * Donne un Tank à un joueur
     */
    private void giveTank(CommandSender sender, Player target) {
        if (target.getInventory().firstEmpty() == -1) {
            sender.sendMessage("§c❌ L'inventaire de " + target.getName() + " est plein!");
            return;
        }

        ItemStack tank = plugin.getTankManager().createTank(target);
        target.getInventory().addItem(tank);

        // Messages
        target.sendMessage("§a✓ Vous avez reçu un §6⚡ Tank Automatique§a!");
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        sender.sendMessage("§a✓ Tank donné à " + target.getName());

        // Log
        plugin.getPluginLogger().info(sender.getName() + " a donné un tank à " + target.getName());
    }

    /**
     * Donne un Sell Hand à un joueur
     */
    private void giveSellHand(CommandSender sender, Player target, String[] args) {
        if (target.getInventory().firstEmpty() == -1) {
            sender.sendMessage("§c❌ L'inventaire de " + target.getName() + " est plein!");
            return;
        }

        SellHandType type = SellHandType.WOOD; // Par défaut

        // Si un type est spécifié
        if (args.length >= 4) {
            try {
                type = SellHandType.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§c❌ Type de Sell Hand invalide! Types disponibles:");
                for (SellHandType t : SellHandType.values()) {
                    sender.sendMessage("§7- " + t.name().toLowerCase() + " (" + t.getDisplayName() + "§7)");
                }
                return;
            }
        }

        ItemStack sellHand = plugin.getSellHandManager().createSellHand(type);
        target.getInventory().addItem(sellHand);

        // Messages
        target.sendMessage("§a✓ Vous avez reçu un " + type.getDisplayName() + "§a!");
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        sender.sendMessage("§a✓ " + type.getDisplayName() + " §adonné à " + target.getName());

        // Log
        plugin.getPluginLogger().info(sender.getName() + " a donné un " + type.name() + " sell hand à " + target.getName());
    }

    /**
     * Gère la sous-commande "reload"
     */
    private void handleReloadCommand(CommandSender sender) {
        try {
            // Sauvegarder tous les tanks avant de recharger
            plugin.getTankManager().saveAllTanks();

            sender.sendMessage("§a✓ Configuration des tanks rechargée!");

            // Log
            plugin.getPluginLogger().info(sender.getName() + " a rechargé la configuration des tanks");

        } catch (Exception e) {
            sender.sendMessage("§c❌ Erreur lors du rechargement: " + e.getMessage());
            plugin.getPluginLogger().severe("Erreur lors du rechargement par " + sender.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Affiche le message d'aide
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6=== Tank Admin - Commandes ===");
        sender.sendMessage("§e/tankadmin give tank <joueur> §7- Donner un tank");
        sender.sendMessage("§e/tankadmin give sellhand <joueur> [type] §7- Donner un sell hand");
        sender.sendMessage("§e/tankadmin reload §7- Recharger la configuration");
        sender.sendMessage("§e/tankadmin help §7- Afficher cette aide");
        sender.sendMessage("");
        sender.sendMessage("§7Types de Sell Hand disponibles:");
        for (SellHandType type : SellHandType.values()) {
            sender.sendMessage("§8▸ §7" + type.name().toLowerCase() + " §8- " + type.getDisplayName() +
                    " §8(x" + String.format("%.2f", type.getMultiplier()) + ")");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Première argument: sous-commandes
            StringUtil.copyPartialMatches(args[0], Arrays.asList("give", "reload", "help"), completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Deuxième argument pour give: type d'item
            StringUtil.copyPartialMatches(args[1], Arrays.asList("tank", "sellhand"), completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Troisième argument pour give: nom du joueur
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (StringUtil.startsWithIgnoreCase(player.getName(), args[2])) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give") && args[1].equalsIgnoreCase("sellhand")) {
            // Quatrième argument pour give sellhand: type de sell hand
            for (SellHandType type : SellHandType.values()) {
                if (StringUtil.startsWithIgnoreCase(type.name(), args[3])) {
                    completions.add(type.name().toLowerCase());
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}