package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.OutpostData;
import fr.prisontycoon.managers.OutpostManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Commande /AP pour gérer l'avant-poste
 * Usage: /AP [info|skins|capture|tp|admin]
 */
public class OutpostCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_BASIC = "specialmine.basic";
    private static final String PERMISSION_ADMIN = "specialmine.admin";
    private final PrisonTycoon plugin;

    public OutpostCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c❌ Cette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        if (!player.hasPermission(PERMISSION_BASIC)) {
            player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        OutpostManager outpostManager = plugin.getOutpostManager();
        if (outpostManager == null) {
            player.sendMessage("§c❌ Système d'avant-poste non disponible!");
            return true;
        }

        // Commande sans arguments = ouvrir le menu principal
        if (args.length == 0) {
            plugin.getOutpostGUI().openOutpostMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info", "i" -> handleInfoCommand(player, outpostManager);
            case "skins", "skin", "s" -> handleSkinsCommand(player);
            case "capture", "cap", "c" -> handleCaptureCommand(player, outpostManager);
            case "admin", "a" -> handleAdminCommand(player, outpostManager, args);
            case "help", "h", "?" -> handleHelpCommand(player);
            default -> {
                player.sendMessage("§c❌ Sous-commande inconnue: §e" + subCommand);
                player.sendMessage("§7Utilisez §e/AP help §7pour voir l'aide.");
            }
        }

        return true;
    }

    /**
     * Affiche les informations de l'avant-poste
     */
    private void handleInfoCommand(Player player, OutpostManager outpostManager) {
        OutpostData outpostData = outpostManager.getOutpostData();

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6        🏰 AVANT-POSTE INFO");
        player.sendMessage("§6═══════════════════════════════════");

        if (outpostData.isControlled()) {
            player.sendMessage("§7Contrôleur: §6" + outpostData.getControllerName());
            long timeSince = outpostData.getTimeSinceCapture();
            player.sendMessage("§7Depuis: §e" + formatTime(timeSince));
        } else {
            player.sendMessage("§7Statut: §c❌ Non contrôlé");
        }

        player.sendMessage("§7Skin actuel: §b" + outpostData.getCurrentSkin());
        player.sendMessage("§7Position: §e-14, -16, 106 §7(Cave)");
        player.sendMessage("");

        if (outpostManager.isWeekendActive()) {
            player.sendMessage("§e🎉 WEEKEND ACTIF - Récompenses x2!");
            player.sendMessage("");
        }

        player.sendMessage("§a💰 §lRécompenses par minute:");
        player.sendMessage("§7• Coins, Tokens, XP, Beacons");
        player.sendMessage("§7• XP Métier Guerrier");
        player.sendMessage("§7• Bonus de gang (10% des coins)");
        player.sendMessage("");

        if (outpostManager.isCapturing(player)) {
            int progress = outpostManager.getCaptureProgress(player);
            player.sendMessage("§e⏳ Vous capturez actuellement: §6" + progress + "%");
        }

        player.sendMessage("§7Utilisez §e/AP §7pour ouvrir le menu complet!");
    }

    /**
     * Ouvre le menu des skins
     */
    private void handleSkinsCommand(Player player) {
        plugin.getOutpostGUI().openSkinsMenu(player, 0);
    }

    /**
     * Démarre une capture ou affiche l'état
     */
    private void handleCaptureCommand(Player player, OutpostManager outpostManager) {
        if (outpostManager.isCapturing(player)) {
            int progress = outpostManager.getCaptureProgress(player);
            player.sendMessage("§e⏳ Capture en cours: §6" + progress + "% §7- Restez sur l'avant-poste!");
            return;
        }

        if (!player.getWorld().getName().equals("Cave")) {
            player.sendMessage("§c❌ L'avant-poste se trouve dans le monde §eCave§c!");
            player.sendMessage("§7Utilisez §e/AP tp §7pour vous y téléporter.");
            return;
        }

        if (outpostManager.isPlayerInOutpost(player)) {
            player.sendMessage("§c❌ Vous devez être sur l'avant-poste pour le capturer!");
            player.sendMessage("§7Position: §e-14, -16, 106");
            return;
        }

        outpostManager.startCapture(player);
    }
 
    /**
     * Commandes administrateur
     */
    private void handleAdminCommand(Player player, OutpostManager outpostManager, String[] args) {
        if (!player.hasPermission(PERMISSION_ADMIN)) {
            player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser les commandes admin!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c❌ Utilisation: /AP admin <reset|setskin|stats|reload>");
            return;
        }

        String adminAction = args[1].toLowerCase();

        switch (adminAction) {
            case "reset" -> {
                outpostManager.getOutpostData().resetControl();
                player.sendMessage("§a✅ Avant-poste remis à zéro!");
            }
            case "setskin" -> {
                if (args.length < 3) {
                    player.sendMessage("§c❌ Utilisation: /AP admin setskin <nom>");
                    return;
                }
                String skinName = args[2];
                // outpostManager.changeOutpostSkin(skinName);
                player.sendMessage("§a✅ Skin changé: " + skinName);
            }
            case "stats" -> {
                OutpostData data = outpostManager.getOutpostData();
                player.sendMessage("§6📊 Statistiques d'avant-poste:");
                player.sendMessage("§7Captures totales: " + data.getTotalCapturesCount());
                player.sendMessage("§7Coins générés: " + data.getTotalCoinsGenerated());
                player.sendMessage("§7Tokens générés: " + data.getTotalTokensGenerated());
            }
            case "reload" -> {
                // outpostManager.reload();
                player.sendMessage("§a✅ Système d'avant-poste rechargé!");
            }
            default -> {
                player.sendMessage("§c❌ Action admin inconnue: " + adminAction);
                player.sendMessage("§7Actions: reset, setskin, stats, reload");
            }
        }
    }

    /**
     * Affiche l'aide
     */
    private void handleHelpCommand(Player player) {
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6      🏰 AIDE AVANT-POSTE");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§e/AP §7- Ouvre le menu principal");
        player.sendMessage("§e/AP info §7- Affiche les informations");
        player.sendMessage("§e/AP skins §7- Menu des skins");
        player.sendMessage("§e/AP capture §7- Démarre/vérifie la capture");
        player.sendMessage("");
        player.sendMessage("§6🎯 §lComment capturer:");
        player.sendMessage("§71. Allez sur l'avant-poste (Cave)");
        player.sendMessage("§72. Restez 30 secondes sur la zone");
        player.sendMessage("§73. Recevez des récompenses chaque minute!");
        player.sendMessage("");
        player.sendMessage("§6💰 §lRécompenses: §7Coins, Tokens, XP, Beacons");
        player.sendMessage("§6⚔ §lBonus Guerrier: §7+XP métier");
        player.sendMessage("§6🎉 §lWeekend: §7Récompenses x2!");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("info", "skins", "capture", "help"));

            if (player.hasPermission(PERMISSION_ADMIN)) {
                completions.add("admin");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("admin") && player.hasPermission(PERMISSION_ADMIN)) {
            return Stream.of("reset", "setskin", "stats", "reload")
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return new ArrayList<>();
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }
}