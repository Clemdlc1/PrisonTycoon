package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
 * Commande pour gérer les joueurs VIP
 * Usage: /vip <add|remove|list> [joueur]
 */
public class VipCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public VipCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérification des permissions pour les sous-commandes admin
        if (args.length > 0 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("list"))) {
            if (!sender.hasPermission("specialmine.admin.vip")) {
                sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
                return true;
            }
        }

        if (args.length == 0) {
            // Commande simple /vip sans arguments - donne le statut VIP au joueur
            if (!(sender instanceof Player)) {
                sender.sendMessage("§c❌ Cette commande ne peut être utilisée que par un joueur!");
                return true;
            }

            if (!sender.hasPermission("specialmine.admin.vip")) {
                sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
                return true;
            }

            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add" -> handleAddCommand(sender, args);
            case "remove" -> handleRemoveCommand(sender, args);
            case "list" -> handleListCommand(sender);
            case "check" -> handleCheckCommand(sender, args);
            case "sync" -> handleSyncCommand(sender);
            case "syncplayer" -> handleSyncPlayerCommand(sender, args);
            default -> {
                // Si pas de sous-commande reconnue et qu'il y a un argument, considère comme /vip add <joueur>
                if (args.length == 1) {
                    handleDirectAddCommand(sender, args[0]);
                } else {
                    sendHelpMessage(sender);
                }
            }
        }

        return true;
    }

    /**
     * Ajoute un joueur VIP
     * Usage: /vip add <joueur>
     */
    private void handleAddCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player addedByPlayer)) {
            sender.sendMessage("§c❌ Cette commande ne peut être exécutée que par un joueur.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /vip add <joueur>");
            return;
        }

        String playerName = args[1];

        // CORRIGÉ 2: Obtenir l'objet Player de la cible (doit être en ligne)
        Player target = Bukkit.getPlayer(playerName);

        // Si getPlayer renvoie null, le joueur n'est pas en ligne.
        if (target == null) {
            sender.sendMessage("§c❌ Le joueur '" + playerName + "' n'est pas en ligne ou n'existe pas.");
            return;
        }

        // La vérification du statut VIP utilise l'UUID, ce qui est correct.
        boolean isAlreadyVip = plugin.getPermissionManager().hasPermission(target, "specialmine.vip");

        if (isAlreadyVip) {
            sender.sendMessage("§c❌ Ce joueur est déjà VIP!");
            return;
        }

        // CORRIGÉ 3: Appel de la méthode addVip avec les bons types (Player, Player)
        // En supposant que la signature attendue est : addVip(Player target, Player addedBy)
        plugin.getVipManager().addVip(target.getUniqueId(), target, addedByPlayer);

        // Messages de succès
        sender.sendMessage("§a✅ Joueur " + target.getName() + " ajouté aux VIP!");
        sender.sendMessage("§a✅ Permission §especialmine.vip §aaccordée automatiquement!");

        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            onlineTarget.sendMessage("§e🌟 Félicitations ! Vous êtes maintenant VIP!");
            onlineTarget.sendMessage("§7Vous pouvez maintenant:");
            onlineTarget.sendMessage("§e• Utiliser les couleurs dans le chat (&c, &e, etc.)");
            onlineTarget.sendMessage("§e• Afficher vos items avec [hand]");
            onlineTarget.sendMessage("§e• Partager votre inventaire avec [inv]");
            onlineTarget.sendMessage("§e• Utiliser /invsee pour voir les inventaires");

            plugin.getVipManager().forcePlayerSync(onlineTarget);
            plugin.getPlayerDataManager().savePlayerNow(onlineTarget.getUniqueId());
        } else {
            // Pour joueurs hors ligne, force une sauvegarde immédiate
            plugin.getPlayerDataManager().savePlayerNow(target.getUniqueId());
        }

        // Annonce aux admins
        broadcastToAdmins("§7[ADMIN] §e" + sender.getName() + " §7a donné le grade VIP à §6" + target.getName(), sender);

        plugin.getPluginLogger().info("VIP ajouté: " + target.getName() + " par " + sender.getName());
    }

    /**
     * NOUVEAU: Retire un joueur VIP (permission automatique)
     */
    private void handleRemoveCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /vip remove <joueur>");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§c❌ Joueur introuvable: " + target);
            return;
        }

        // Vérifie si est VIP
        if (!plugin.getVipManager().isVip(target.getUniqueId())) {
            sender.sendMessage("§c❌ Ce joueur n'est pas VIP!");
            return;
        }

        // NOUVEAU: Retire directement avec permission automatique
        plugin.getVipManager().removeVip(target, sender.getName());

        // Messages de succès
        sender.sendMessage("§a✅ Joueur " + target.getName() + " retiré des VIP!");
        sender.sendMessage("§a✅ Permission §especialmine.vip §cretirée automatiquement!");

        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            onlineTarget.sendMessage("§c❌ Votre grade VIP vous a été retiré.");

            // Force la synchronisation immédiate pour le joueur en ligne
            plugin.getVipManager().forcePlayerSync(onlineTarget);
        }

        // Annonce aux admins
        broadcastToAdmins("§7[ADMIN] §e" + sender.getName() + " §7a retiré le grade VIP à §c" + target.getName(), sender);

        plugin.getPluginLogger().info("VIP retiré: " + target.getName() + " par " + sender.getName());
    }

    /**
     * Commande directe /vip <joueur> (équivalent à /vip add <joueur>)
     */
    private void handleDirectAddCommand(CommandSender sender, String playerName) {
        String[] newArgs = {"add", playerName};
        handleAddCommand(sender, newArgs);
    }

    /**
     * Liste tous les VIP
     * Usage: /vip list
     */
    private void handleListCommand(CommandSender sender) {
        var allVips = plugin.getVipManager().getAllVips();

        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l🌟 LISTE DES VIP (" + allVips.size() + ")");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (allVips.isEmpty()) {
            sender.sendMessage("§7Aucun joueur VIP pour le moment.");
        } else {
            int count = 0;
            for (var uuid : allVips) {
                var vipData = plugin.getVipManager().getVipData(uuid);
                if (vipData != null) {
                    String status = Bukkit.getOfflinePlayer(uuid).isOnline() ? "§a●" : "§7●";
                    String timeAgo = formatTimeAgo(System.currentTimeMillis() - vipData.addedAt());

                    sender.sendMessage("§e• " + status + " §6" + vipData.playerName() +
                                       " §7(ajouté par §e" + vipData.addedBy() + " §7il y a " + timeAgo + ")");
                    count++;

                    // Limite l'affichage pour éviter le spam
                    if (count >= 20) {
                        int remaining = allVips.size() - count;
                        if (remaining > 0) {
                            sender.sendMessage("§7... et " + remaining + " autres VIP");
                        }
                        break;
                    }
                }
            }
        }

        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Vérifie le statut VIP d'un joueur
     * Usage: /vip check <joueur>
     */
    private void handleCheckCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /vip check <joueur>");
            return;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l🔍 STATUT VIP DÉTAILLÉ - " + target.getName().toUpperCase());
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        String statusDetails = plugin.getVipManager().getVipStatusDetailed(target.getUniqueId());
        sender.sendMessage(statusDetails);

        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            boolean isConsistent = plugin.getVipManager().checkVipConsistency(onlineTarget);

            sender.sendMessage("§7Cohérence: " + (isConsistent ? "§a✅ OK" : "§c❌ PROBLÈME"));

            if (!isConsistent) {
                sender.sendMessage("§c⚠️ Incohérence détectée!");
                sender.sendMessage("§7Utilisez §e/vip sync §7pour corriger");
            }

            // Affiche les permissions actives Bukkit
            boolean hasVipPermission = onlineTarget.hasPermission("specialmine.vip");
            sender.sendMessage("§7Test hasPermission: " + (hasVipPermission ? "§a✅ VIP" : "§c❌ Pas VIP"));
        } else {
            sender.sendMessage("§7Statut: §7Hors ligne (impossible de vérifier permissions Bukkit)");
        }

        var vipData = plugin.getVipManager().getVipData(target.getUniqueId());
        if (vipData != null) {
            String timeAgo = formatTimeAgo(System.currentTimeMillis() - vipData.addedAt());
            sender.sendMessage("§7Ajouté par: §e" + vipData.addedBy());
            sender.sendMessage("§7Date: §e" + timeAgo + " ago");
        }

        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Diffuse un message aux administrateurs
     */
    private void broadcastToAdmins(String message, CommandSender exclude) {
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("specialmine.admin") && !admin.equals(exclude)) {
                admin.sendMessage(message);
            }
        }
    }

    /**
     * Formate un temps écoulé en texte lisible
     */
    private String formatTimeAgo(long milliseconds) {
        long days = milliseconds / (24 * 60 * 60 * 1000);
        long hours = (milliseconds % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (milliseconds % (60 * 60 * 1000)) / (60 * 1000);

        if (days > 0) {
            return days + "j";
        } else if (hours > 0) {
            return hours + "h";
        } else if (minutes > 0) {
            return minutes + "m";
        } else {
            return "quelques secondes";
        }
    }

    /**
     * NOUVEAU: Synchronise le cache avec les permissions
     */
    private void handleSyncCommand(CommandSender sender) {
        sender.sendMessage("§e🔄 Synchronisation complète des données VIP...");

        plugin.getVipManager().syncAllVipData();

        sender.sendMessage("§a✅ Synchronisation terminée!");
        sender.sendMessage("§7Vérifiez les logs pour les détails des corrections.");

        // Affiche un résumé
        int onlineVips = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("specialmine.vip")) {
                onlineVips++;
            }
        }

        sender.sendMessage("§7VIP en ligne: §e" + onlineVips + " §7joueurs");
    }

    /**
     * NOUVEAU: Force la synchronisation d'un joueur spécifique
     */
    private void handleSyncPlayerCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /vip syncplayer <joueur>");
            return;
        }

        String playerName = args[1];
        Player target = Bukkit.getPlayer(playerName);

        if (target == null) {
            sender.sendMessage("§c❌ Joueur introuvable ou hors ligne: " + playerName);
            return;
        }

        sender.sendMessage("§e🔄 Synchronisation de " + target.getName() + "...");
        plugin.getVipManager().forcePlayerSync(target);
        sender.sendMessage("§a✅ Synchronisation terminée pour " + target.getName() + "!");
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l🌟 COMMANDES VIP");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§e/vip add <joueur> §7- Ajoute un VIP (permission automatique)");
        sender.sendMessage("§e/vip remove <joueur> §7- Retire un VIP (permission automatique)");
        sender.sendMessage("§e/vip list §7- Liste tous les VIP");
        sender.sendMessage("§e/vip check <joueur> §7- Statut VIP détaillé + cohérence");
        sender.sendMessage("§e/vip sync §7- Synchronise toutes les données VIP");
        sender.sendMessage("§e/vip syncplayer <joueur> §7- Synchronise un joueur spécifique");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§a✅ Permissions gérées automatiquement par le plugin!");
        sender.sendMessage("§7player.hasPermission(\"specialmine.vip\") fonctionne directement");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("specialmine.admin.vip")) {
            return completions;
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("add", "remove", "list", "check", "sync", "syncplayer");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);

            // Ajoute aussi les noms de joueurs pour la commande directe
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") ||
                args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("syncplayer")) {
                // Suggestions de noms de joueurs
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}