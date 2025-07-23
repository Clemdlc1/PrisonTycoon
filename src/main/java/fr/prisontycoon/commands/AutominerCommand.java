package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commande principale des automineurs : /autominer
 *
 * Sous-commandes disponibles :
 * - /autominer (ou sans args) : Ouvre l'interface principale
 * - /autominer give <type> [joueur] : Donne un automineur (admin)
 * - /autominer energy : Ouvre directement le menu carburant
 * - /autominer info : Affiche les informations des automineurs du joueur
 * - /autominer reload : Recharge la configuration (admin)
 */
public class AutominerCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public AutominerCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // Commande disponible seulement pour les joueurs (sauf reload)
        if (!(sender instanceof Player) && (args.length == 0 || !args[0].equals("reload"))) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;

        // Sans arguments : ouvre l'interface principale
        if (args.length == 0) {
            if (player == null) return true;

            plugin.getAutominerGUI().openMainMenu(player);
            return true;
        }

        // Traitement des sous-commandes
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> {
                return handleGiveCommand(sender, args);
            }
            case "energy" -> {
                if (player == null) return true;

                // Ouvre directement l'interface de gestion du carburant
                player.sendMessage("§e⚡ Ouverture du menu carburant...");
                plugin.getAutominerFuelGUI().openFuelMenu(player);

                return true;
            }
            case "info" -> {
                return handleInfoCommand(player);
            }
            case "reload" -> {
                return handleReloadCommand(sender);
            }
            case "help" -> {
                return handleHelpCommand(sender);
            }
            default -> {
                sender.sendMessage("§c❌ Sous-commande inconnue! Utilisez §e/autominer help §cpour l'aide.");
                return true;
            }
        }
    }

    /**
     * Gère la commande /autominer give <type> [joueur]
     */
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        // Vérification des permissions
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: §e/autominer give <type> [joueur]");
            sender.sendMessage("§7Types disponibles: pierre, fer, or, diamant, emeraude, beacon");
            return true;
        }

        // Parse du type d'automineur
        AutominerType type;
        try {
            type = AutominerType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c❌ Type d'automineur invalide: §e" + args[1]);
            sender.sendMessage("§7Types disponibles: pierre, fer, or, diamant, emeraude, beacon");
            return true;
        }

        // Détermination du joueur cible
        Player target;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage("§c❌ Joueur introuvable: §e" + args[2]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c❌ Vous devez spécifier un joueur depuis la console!");
                return true;
            }
            target = (Player) sender;
        }

        // Attribution de l'automineur
        boolean success = plugin.getAutominerManager().giveAutominer(target, type);

        if (success) {
            sender.sendMessage("§a✅ " + type.getColoredName() + " §aAutomineur donné à §e" + target.getName() + "§a!");

            if (!sender.equals(target)) {
                target.sendMessage("§a✅ Vous avez reçu un " + type.getColoredName() + " §aAutomineur de la part de §e" + sender.getName() + "§a!");
            }
        } else {
            sender.sendMessage("§c❌ Impossible de donner l'automineur (inventaire plein?)");
        }

        return true;
    }

    /**
     * Gère la commande /autominer info
     */
    private boolean handleInfoCommand(Player player) {
        if (player == null) return true;

        var playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6         ⚡ VOS AUTOMINEURS ⚡");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("");

        // Informations générales
        var activeIds = playerData.getActiveAutominers();
        String statusColor = playerData.isAutominersRunning() ? "§a" : "§c";
        String statusText = playerData.isAutominersRunning() ? "EN MARCHE" : "ARRÊTÉS";

        player.sendMessage("§e📊 §lÉTAT GÉNÉRAL");
        player.sendMessage("§7▸ Automineurs placés: §e" + activeIds.size() + "§8/§e2");
        player.sendMessage("§7▸ Statut: " + statusColor + statusText);
        player.sendMessage("§7▸ Monde actuel: §b" + playerData.getAutominerWorld());
        player.sendMessage("");

        // Ressources
        player.sendMessage("§e💰 §lRESSOURCES");
        player.sendMessage("§7▸ Carburant: §e" + NumberFormatter.format(playerData.getAutominerFuel()) + " têtes");

        long totalStored = playerData.getTotalStoredBlocks();
        long capacity = playerData.getAutominerStorageCapacity();
        double percentage = playerData.getStorageUsagePercentage();

        player.sendMessage("§7▸ Stockage: §d" + NumberFormatter.format(totalStored) +
                "§8/§d" + NumberFormatter.format(capacity) +
                " §7(" + String.format("%.1f", percentage) + "%)");
        player.sendMessage("");

        // Détails des automineurs actifs
        if (!activeIds.isEmpty()) {
            player.sendMessage("§e⚡ §lAUTOMINEURS ACTIFS");

            // Recherche des automineurs dans l'inventaire
            int count = 1;
            for (var item : player.getInventory().getContents()) {
                if (item != null && isAutominer(item)) {
                    var data = AutominerData.fromItemStack(item,
                            plugin.getAutominerManager().getUuidKey(),
                            plugin.getAutominerManager().getTypeKey(),
                            plugin.getAutominerManager().getEnchantKey(),
                            plugin.getAutominerManager().getCristalKey());
                    if (data != null && activeIds.contains(data.getUuid())) {
                        player.sendMessage("§7▸ §e#" + count + " " + data.getType().getColoredName() +
                                " §7(Efficacité: §a" + data.getTotalEfficiency() +
                                "§7, Fortune: §a" + data.getTotalFortune() + "§7)");
                        count++;
                    }
                }
            }
        } else {
            player.sendMessage("§7Aucun automineur actif.");
        }

        player.sendMessage("");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§7Utilisez §e/autominer §7pour ouvrir l'interface!");
        player.sendMessage("§6═══════════════════════════════════");

        return true;
    }

    /**
     * Gère la commande /autominer reload
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        sender.sendMessage("§e⚡ Rechargement de la configuration des automineurs...");

        try {
            // Rechargement de la configuration (si nécessaire)
            plugin.reloadConfig();

            // Éventuels autres rechargements spécifiques aux automineurs
            // plugin.getAutominerManager().reload();

            sender.sendMessage("§a✅ Configuration des automineurs rechargée avec succès!");

        } catch (Exception e) {
            sender.sendMessage("§c❌ Erreur lors du rechargement: " + e.getMessage());
            plugin.getPluginLogger().severe("Erreur lors du rechargement des automineurs: " + e.getMessage());
        }

        return true;
    }

    /**
     * Gère la commande /autominer help
     */
    private boolean handleHelpCommand(CommandSender sender) {
        sender.sendMessage("§6═══════════════════════════════════");
        sender.sendMessage("§6            ⚡ AIDE AUTOMINEURS ⚡");
        sender.sendMessage("§6═══════════════════════════════════");
        sender.sendMessage("");
        sender.sendMessage("§e/autominer §8- §7Ouvre l'interface de gestion");
        sender.sendMessage("§e/autominer info §8- §7Affiche vos informations");
        sender.sendMessage("§e/autominer energy §8- §7Menu de gestion du carburant");
        sender.sendMessage("§e/autominer help §8- §7Affiche cette aide");
        sender.sendMessage("");

        if (sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c📋 §lCOMMANDES ADMINISTRATEUR");
            sender.sendMessage("§e/autominer give <type> [joueur] §8- §7Donne un automineur");
            sender.sendMessage("§e/autominer reload §8- §7Recharge la configuration");
            sender.sendMessage("");
            sender.sendMessage("§7Types d'automineurs: §epierre§7, §efer§7, §eor§7, §ediamant§7, §eemeralde§7, §ebeacon");
            sender.sendMessage("");
        }

        sender.sendMessage("§e📖 §lFONCTIONNEMENT");
        sender.sendMessage("§7▸ Placez jusqu'à 2 automineurs simultanément");
        sender.sendMessage("§7▸ Ils consomment des têtes comme carburant");
        sender.sendMessage("§7▸ Améliorez-les avec des enchantements et cristaux");
        sender.sendMessage("§7▸ Changez de monde pour miner différents blocs");
        sender.sendMessage("§7▸ Condensez 9 automineurs en 1 de niveau supérieur");
        sender.sendMessage("");
        sender.sendMessage("§6═══════════════════════════════════");

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Première argument : sous-commandes
            List<String> subCommands = Arrays.asList("give", "energy", "info", "help");

            if (sender.hasPermission("specialmine.admin")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.add("reload");
            }

            String partial = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(partial)) {
                    completions.add(subCommand);
                }
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Deuxième argument pour "give" : types d'automineurs
            if (sender.hasPermission("specialmine.admin")) {
                String partial = args[1].toLowerCase();
                for (AutominerType type : AutominerType.values()) {
                    String typeName = type.name().toLowerCase();
                    if (typeName.startsWith(partial)) {
                        completions.add(typeName);
                    }
                }
            }
        }
        else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Troisième argument pour "give" : noms des joueurs
            if (sender.hasPermission("specialmine.admin")) {
                String partial = args[2].toLowerCase();
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    String playerName = player.getName().toLowerCase();
                    if (playerName.startsWith(partial)) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        return completions;
    }

    /**
     * Vérifie si un item est un automineur
     */
    private boolean isAutominer(org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(plugin.getAutominerManager().getUuidKey(),
                        org.bukkit.persistence.PersistentDataType.STRING);
    }
}