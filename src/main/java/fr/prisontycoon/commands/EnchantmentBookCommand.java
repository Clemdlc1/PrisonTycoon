package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commande /enchantbook pour le système d'enchantements uniques
 */
public class EnchantmentBookCommand implements CommandExecutor, TabCompleter {

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

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "menu" -> plugin.getEnchantmentBookGUI().openEnchantmentBookMenu(player);
            case "shop", "boutique" -> plugin.getEnchantmentBookGUI().openBookShop(player);
            case "buy", "acheter" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /enchantbook buy <type>");
                    return true;
                }
                handlePurchase(player, args[1]);
            }
            case "list", "liste" -> showBookList(player);
            case "active", "actif" -> showActiveEnchantments(player);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * Gère l'achat direct d'un livre via commande
     */
    private void handlePurchase(Player player, String bookType) {
        EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(bookType.toLowerCase());

        if (book == null) {
            player.sendMessage("§cType de livre invalide! Utilisez /enchantbook list pour voir la liste.");
            return;
        }

        boolean success = plugin.getEnchantmentBookManager().purchaseEnchantmentBook(player, book.getId());
        if (!success) {
            player.sendMessage("§cÉchec de l'achat. Vérifiez vos beacons ou le niveau maximum.");
        }
    }

    /**
     * Affiche la liste de tous les livres disponibles
     */
    private void showBookList(Player player) {
        player.sendMessage("§e§l=== LIVRES D'ENCHANTEMENT DISPONIBLES ===");

        for (EnchantmentBookManager.EnchantmentBook book : plugin.getEnchantmentBookManager().getAllEnchantmentBooks()) {
            int level = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, book.getId());
            boolean isActive = plugin.getEnchantmentBookManager().isEnchantmentActive(player, book.getId());

            String status = isActive ? "§a[ACTIF]" : (level > 0 ? "§e[POSSÉDÉ]" : "§7[NON POSSÉDÉ]");
            player.sendMessage("§7▸ §e" + book.getName() + " " + status + " §7- " + book.getDescription());
        }

        player.sendMessage("§7Utilisez §e/enchantbook menu §7pour ouvrir l'interface graphique.");
    }

    /**
     * Affiche les enchantements actifs du joueur
     */
    private void showActiveEnchantments(Player player) {
        var activeEnchants = plugin.getEnchantmentBookManager().getActiveEnchantments(player);

        if (activeEnchants.isEmpty()) {
            player.sendMessage("§cAucun enchantement actif.");
            return;
        }

        player.sendMessage("§a§l=== ENCHANTEMENTS ACTIFS (" + activeEnchants.size() + "/4) ===");
        for (String bookId : activeEnchants) {
            EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(bookId);
            if (book != null) {
                int level = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, bookId);
                player.sendMessage("§7▸ §a" + book.getName() + " §7(Niveau " + level + ")");
            }
        }
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§e§l=== COMMANDES ENCHANTBOOK ===");
        player.sendMessage("§7/enchantbook §e- Ouvrir le menu");
        player.sendMessage("§7/enchantbook menu §e- Ouvrir le menu");
        player.sendMessage("§7/enchantbook shop §e- Ouvrir la boutique");
        player.sendMessage("§7/enchantbook buy <type> §e- Acheter un livre");
        player.sendMessage("§7/enchantbook list §e- Liste des livres");
        player.sendMessage("§7/enchantbook active §e- Enchantements actifs");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("menu", "shop", "buy", "list", "active");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            // Auto-complétion pour les types de livres
            for (EnchantmentBookManager.EnchantmentBook book : plugin.getEnchantmentBookManager().getAllEnchantmentBooks()) {
                completions.add(book.getId());
            }
            StringUtil.copyPartialMatches(args[1], completions, new ArrayList<>());
        }

        return completions;
    }
}