package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.GUI.PrestigeGUI;
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
 * Commande principale du système de prestige
 * Usage: /prestige [info|récompenses|talents|effectuer]
 */
public class PrestigeCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;
    private final PrestigeGUI prestigeGUI;

    public PrestigeCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.prestigeGUI = new PrestigeGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Commande sans arguments = menu principal
        if (args.length == 0) {
            prestigeGUI.openMainPrestigeMenu(player);
            return true;
        }

        // Sous-commandes
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "info", "informations" -> handleInfoCommand(player);
            case "récompenses", "recompenses", "rewards" -> handleRewardsCommand(player, args);
            case "talents", "talent" -> handleTalentsCommand(player, args);
            case "effectuer", "faire", "perform" -> handlePerformCommand(player);
            case "confirmer", "confirm" -> handleConfirmCommand(player);
            case "help", "aide" -> sendHelpMessage(player);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * Gère la commande /prestige info
     */
    private void handleInfoCommand(Player player) {
        String info = plugin.getPrestigeManager().getPrestigeInfo(player);
        player.sendMessage(info);
    }

    /**
     * Gère la commande /prestige récompenses
     */
    private void handleRewardsCommand(Player player, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("choisir")) {
            // Ouvrir l'interface de choix pour les récompenses en attente
            prestigeGUI.openSpecialRewardsMenu(player);
        } else {
            // Ouvrir le menu général des récompenses
            prestigeGUI.openRewardsMenu(player, 0);
        }
    }

    /**
     * Gère la commande /prestige talents
     */
    private void handleTalentsCommand(Player player, String[] args) {
        int page = 0;

        // Parse de la page si spécifiée
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]) - 1; // Les joueurs comptent de 1, on compte de 0
                page = Math.max(0, page);
            } catch (NumberFormatException e) {
                player.sendMessage("§cPage invalide! Utilisez un nombre.");
                return;
            }
        }

        prestigeGUI.openTalentsMenu(player, page);
    }

    /**
     * Gère la commande /prestige effectuer
     */
    private void handlePerformCommand(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (!plugin.getPrestigeManager().canPrestige(player)) {
            player.sendMessage("§c❌ Vous ne pouvez pas effectuer de prestige!");
            player.sendMessage("§7Conditions requises:");
            player.sendMessage("§7• Rang FREE");
            player.sendMessage("§7• Pas d'épargne active en banque");
            player.sendMessage("§7• Pas d'investissement actif");
            player.sendMessage("§7• Ne pas être en challenge");
            return;
        }

        int nextPrestige = playerData.getPrestigeLevel() + 1;

        // Afficher les informations de confirmation
        player.sendMessage("§6⚠ §e§lCONFIRMATION DE PRESTIGE");
        player.sendMessage("");
        player.sendMessage("§7Vous allez effectuer le §6§lPRESTIGE " + nextPrestige + "§7:");
        player.sendMessage("§c• §7Retour au rang §fA");
        player.sendMessage("§c• §7Remise à 0 des coins");
        player.sendMessage("§a• §7Bonus et talents permanents");
        player.sendMessage("§a• §7Accès aux mines prestige");
        player.sendMessage("§a• §7Récompenses exclusives");
        player.sendMessage("");
        player.sendMessage("§7Tapez §a/prestige confirmer §7pour confirmer");
        player.sendMessage("§7ou attendez 30 secondes pour annuler.");

        // TODO: Implémenter un système de confirmation temporisé
    }

    /**
     * Gère la commande /prestige confirmer
     */
    private void handleConfirmCommand(Player player) {
        // TODO: Vérifier si une confirmation est en attente

        boolean success = plugin.getPrestigeManager().performPrestige(player);
        if (!success) {
            player.sendMessage("§c❌ Impossible d'effectuer le prestige!");
        }
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§6§l╔═══════════════════════════════════╗");
        player.sendMessage("§6§l║           §e🏆 PRESTIGE 🏆           §6§l║");
        player.sendMessage("§6§l╠═══════════════════════════════════╣");
        player.sendMessage("§6§l║ §e/prestige                       §6§l║");
        player.sendMessage("§6§l║ §7├─ Ouvre le menu principal       §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/prestige info                  §6§l║");
        player.sendMessage("§6§l║ §7├─ Informations de prestige      §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/prestige récompenses           §6§l║");
        player.sendMessage("§6§l║ §7├─ Menu des récompenses          §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/prestige talents               §6§l║");
        player.sendMessage("§6§l║ §7├─ Menu des talents              §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/prestige effectuer             §6§l║");
        player.sendMessage("§6§l║ §7├─ Effectuer un prestige         §6§l║");
        player.sendMessage("§6§l╚═══════════════════════════════════╝");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        player.sendMessage("");
        player.sendMessage("§7Votre prestige actuel: " + playerData.getPrestigeDisplayName());

        if (plugin.getPrestigeManager().canPrestige(player)) {
            player.sendMessage("§a✅ Vous pouvez effectuer un prestige!");
        } else if (prestigeLevel >= 50) {
            player.sendMessage("§e⭐ Niveau maximum atteint!");
        } else {
            player.sendMessage("§c❌ Conditions non remplies");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "info", "récompenses", "talents", "effectuer", "confirmer", "help"
            );
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "récompenses", "recompenses", "rewards" -> {
                    List<String> rewardCommands = Arrays.asList("choisir");
                    StringUtil.copyPartialMatches(args[1], rewardCommands, completions);
                }
                case "talents", "talent" -> {
                    // Suggestions de pages
                    List<String> pages = Arrays.asList("1", "2", "3", "4", "5");
                    StringUtil.copyPartialMatches(args[1], pages, completions);
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}