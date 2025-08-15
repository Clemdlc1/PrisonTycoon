package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.PrinterManager;
import fr.prisontycoon.managers.DepositBoxManager;
import org.bukkit.Bukkit;
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
 * Commande admin pour gérer les imprimantes et caisses de dépôt
 */
public class PrinterAdminCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;
    private final PrinterManager printerManager;
    private final DepositBoxManager depositBoxManager;

    public PrinterAdminCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.printerManager = plugin.getPrinterManager();
        this.depositBoxManager = plugin.getDepositBoxManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande.");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "info" -> handleInfo(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sendHelpMessage(sender);
                return true;
            }
        }

        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§c❌ Usage: /printeradmin give <joueur> <type> <tier> [quantité]");
            return;
        }

        String playerName = args[1];
        String type = args[2].toLowerCase();
        int tier;
        int quantity = 1;

        try {
            tier = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c❌ Le tier doit être un nombre.");
            return;
        }

        if (args.length >= 5) {
            try {
                quantity = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c❌ La quantité doit être un nombre.");
                return;
            }
        }

        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        ItemStack item = null;
        switch (type) {
            case "printer" -> {
                if (tier < 1 || tier > 50) {
                    sender.sendMessage("§c❌ Le tier doit être entre 1 et 50.");
                    return;
                }
                item = printerManager.createPrinterItem(tier);
            }
            case "depositbox" -> {
                if (tier < 1 || tier > 5) {
                    sender.sendMessage("§c❌ Le tier doit être entre 1 et 5.");
                    return;
                }
                item = depositBoxManager.createDepositBoxItem();
            }
            default -> {
                sender.sendMessage("§c❌ Type invalide. Utilisez 'printer' ou 'depositbox'.");
                return;
            }
        }

        if (item == null) {
            sender.sendMessage("§c❌ Erreur lors de la création de l'item.");
            return;
        }

        // Donner les items
        for (int i = 0; i < quantity; i++) {
            targetPlayer.getInventory().addItem(item);
        }

        sender.sendMessage("§a✅ " + quantity + "x " + type + " tier " + tier + " donné à " + targetPlayer.getName());
        targetPlayer.sendMessage("§a✅ Vous avez reçu " + quantity + "x " + type + " tier " + tier);
    }

    private void handleList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /printeradmin list <type>");
            return;
        }

        String type = args[1].toLowerCase();
        switch (type) {
            case "printers" -> {
                sender.sendMessage("§6§lImprimantes placées:");
                printerManager.getPrinterCache().values().forEach(printer -> {
                    String ownerName = Bukkit.getOfflinePlayer(printer.getOwner()).getName();
                    sender.sendMessage("§7- Tier " + printer.getTier() + " par " + ownerName + 
                                     " à " + printer.getLocation().getWorld().getName() + 
                                     " (" + printer.getLocation().getBlockX() + ", " + 
                                     printer.getLocation().getBlockY() + ", " + 
                                     printer.getLocation().getBlockZ() + ")");
                });
            }
            case "depositboxes" -> {
                sender.sendMessage("§6§lCaisses de dépôt placées:");
                depositBoxManager.getDepositBoxCache().values().forEach(depositBox -> {
                    String ownerName = Bukkit.getOfflinePlayer(depositBox.getOwner()).getName();
                    sender.sendMessage("§7- Niveau " + depositBox.getCapacityLevel() + " par " + ownerName + 
                                     " à " + depositBox.getLocation().getWorld().getName() + 
                                     " (" + depositBox.getLocation().getBlockX() + ", " + 
                                     depositBox.getLocation().getBlockY() + ", " + 
                                     depositBox.getLocation().getBlockZ() + ")");
                });
            }
            default -> sender.sendMessage("§c❌ Type invalide. Utilisez 'printers' ou 'depositboxes'.");
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c❌ Usage: /printeradmin remove <type> <id>");
            return;
        }

        String type = args[1].toLowerCase();
        String id = args[2];

        boolean success = false;
        switch (type) {
            case "printer" -> success = printerManager.removePrinter(id);
            case "depositbox" -> {
                depositBoxManager.getDepositBoxCache().remove(id);
                success = true;
            }
            default -> {
                sender.sendMessage("§c❌ Type invalide. Utilisez 'printer' ou 'depositbox'.");
                return;
            }
        }

        if (success) {
            sender.sendMessage("§a✅ " + type + " supprimé avec succès.");
        } else {
            sender.sendMessage("§c❌ " + type + " introuvable.");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /printeradmin info <joueur>");
            return;
        }

        String playerName = args[1];
        Player targetPlayer = Bukkit.getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        int printerSlots = printerManager.getPlayerPrinterSlots(targetPlayer.getUniqueId());
        int currentPrinters = printerManager.getPlayerPrinters(targetPlayer.getUniqueId()).size();

        sender.sendMessage("§6§lInformations de " + targetPlayer.getName() + ":");
        sender.sendMessage("§7Slots d'imprimantes: §e" + currentPrinters + "/" + printerSlots);
        sender.sendMessage("§7Imprimantes placées: §e" + currentPrinters);
    }

    private void handleReload(CommandSender sender) {
        // Recharger les données
        printerManager.saveAll();
        depositBoxManager.saveAll();
        
        sender.sendMessage("§a✅ Données des imprimantes rechargées.");
    }

    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6▬▬▬▬▬▬▬▬▬▬ §lCOMMANDES ADMIN IMPRIMANTES §6▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§e/printeradmin give <joueur> <type> <tier> [quantité] §7- Donner des items");
        sender.sendMessage("§e/printeradmin list <type> §7- Lister les items placés");
        sender.sendMessage("§e/printeradmin remove <type> <id> §7- Supprimer un item");
        sender.sendMessage("§e/printeradmin info <joueur> §7- Informations d'un joueur");
        sender.sendMessage("§e/printeradmin reload §7- Recharger les données");
        sender.sendMessage("");
        sender.sendMessage("§7Types: printer (tier 1-50), depositbox (tier 1-5)");
        sender.sendMessage("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("give", "list", "remove", "info", "reload"), completions);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "give" -> {
                    // Liste des joueurs en ligne
                    Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
                }
                case "list" -> StringUtil.copyPartialMatches(args[1], Arrays.asList("printers", "depositboxes"), completions);
                case "remove" -> StringUtil.copyPartialMatches(args[1], Arrays.asList("printer", "depositbox"), completions);
                case "info" -> {
                    // Liste des joueurs en ligne
                    Bukkit.getOnlinePlayers().forEach(player -> completions.add(player.getName()));
                }
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "give" -> StringUtil.copyPartialMatches(args[2], Arrays.asList("printer", "depositbox"), completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}
