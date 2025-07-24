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
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /vip add <joueur>");
            return;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        if (plugin.getVipManager().isVip(target.getUniqueId())) {
            sender.sendMessage("§c❌ Ce joueur est déjà VIP!");
            return;
        }

        plugin.getVipManager().addVip(target.getUniqueId(), target.getName(), sender.getName()).thenAccept(success -> {
            if (success) {
                sender.sendMessage("§a✅ Joueur " + target.getName() + " ajouté aux VIP!");
                if (target.isOnline()) {
                    Player onlineTarget = target.getPlayer();
                    onlineTarget.sendMessage("§e🌟 Félicitations ! Vous êtes maintenant VIP!");
                    plugin.getVipManager().applyVipBenefits(onlineTarget);
                }
                broadcastToAdmins("§7[ADMIN] §e" + sender.getName() + " §7a donné le grade VIP à §6" + target.getName(), sender);
                plugin.getPluginLogger().info("VIP ajouté: " + target.getName() + " par " + sender.getName());
            } else {
                sender.sendMessage("§c❌ Une erreur est survenue lors de l'ajout du VIP.");
            }
        });
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
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        // Vérifie si est VIP
        if (!plugin.getVipManager().isVip(target.getUniqueId())) {
            sender.sendMessage("§c❌ Ce joueur n'est pas VIP!");
            return;
        }

        plugin.getVipManager().removeVip(target.getUniqueId()).thenAccept(success -> {
            if (success) {
                sender.sendMessage("§a✅ Joueur " + target.getName() + " retiré des VIP!");
                if (target.isOnline()) {
                    Player onlineTarget = target.getPlayer();
                    onlineTarget.sendMessage("§c❌ Votre grade VIP vous a été retiré.");
                    plugin.getVipManager().removeVipBenefits(onlineTarget);
                }
                broadcastToAdmins("§7[ADMIN] §e" + sender.getName() + " §7a retiré le grade VIP à §c" + target.getName(), sender);
                plugin.getPluginLogger().info("VIP retiré: " + target.getName() + " par " + sender.getName());
            } else {
                sender.sendMessage("§c❌ Une erreur est survenue lors du retrait du VIP.");
            }
        });
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
            for (var entry : allVips.entrySet()) {
                var uuid = entry.getKey();
                var vipData = entry.getValue();
                String status = Bukkit.getOfflinePlayer(uuid).isOnline() ? "§a●" : "§7●";
                String timeAgo = formatTimeAgo(System.currentTimeMillis() - vipData.addedTime.toEpochMilli());

                sender.sendMessage("§e• " + status + " §6" + vipData.playerName +
                        " §7(ajouté par §e" + vipData.addedBy + " §7il y a " + timeAgo + ")");
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

        boolean isVip = plugin.getVipManager().isVip(target.getUniqueId());
        sender.sendMessage("§7Statut VIP: " + (isVip ? "§a✅ Actif" : "§c❌ Inactif"));

        if (isVip) {
            var vipData = plugin.getVipManager().getVipData(target.getUniqueId());
            if (vipData != null) {
                String timeAgo = formatTimeAgo(System.currentTimeMillis() - vipData.addedTime.toEpochMilli());
                sender.sendMessage("§7Groupe: §e" + vipData.group);
                sender.sendMessage("§7Ajouté par: §e" + vipData.addedBy);
                sender.sendMessage("§7Date: §e" + timeAgo + " ago");
                if (vipData.isPermanent()) {
                    sender.sendMessage("§7Expiration: §aJamais");
                } else {
                    String expiry = formatTimeAgo(vipData.expiry.toEpochMilli() - System.currentTimeMillis());
                    sender.sendMessage("§7Expiration: §c" + expiry);
                }
            }
        }

        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            boolean hasVipPermission = onlineTarget.hasPermission("specialmine.vip");
            sender.sendMessage("§7Test hasPermission: " + (hasVipPermission ? "§a✅ VIP" : "§c❌ Pas VIP"));
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
            List<String> subCommands = Arrays.asList("add", "remove", "list", "check");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);

            // Ajoute aussi les noms de joueurs pour la commande directe
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") ||
                    args[0].equalsIgnoreCase("check")) {
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