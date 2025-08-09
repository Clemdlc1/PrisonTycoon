package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.Gang;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.gangs.GangRole;
import fr.prisontycoon.utils.NumberFormatter;
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
 * Commandes pour la gestion des gangs
 */
public class GangCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public GangCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur.");
            return true;
        }

        String commandName = command.getName().toLowerCase();

        if (commandName.equals("g")) {
            handleChat(player, args);
            return true;
        }

        if (args.length == 0) {
            plugin.getGangGUI().openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create", "creategang" -> handleCreateGang(player, args);
            case "invite", "ganginvite" -> handleInvite(player, args);
            case "kick", "gangkick" -> handleKick(player, args);
            case "leave" -> handleLeave(player);
            case "promote" -> handlePromote(player, args);
            case "demote" -> handleDemote(player, args);
            case "transfer" -> handleTransfer(player, args);
            case "disband" -> handleDisband(player);
            case "accept" -> handleAcceptInvite(player);
            case "deny", "decline" -> handleDenyInvite(player);
            case "info" -> handleInfo(player, args);
            case "list" -> handleList(player);
            case "deposit", "gangdeposit" -> handleDeposit(player, args);
            case "upgrade", "gangupgrade" -> handleUpgrade(player);
            case "shop" -> handleShop(player);
            case "rename" -> handleRename(player, args);
            case "description", "desc" -> handleDescription(player, args);
            case "banner" -> handleBanner(player);
            case "help" -> sendHelpMessage(player);
            default -> {
                player.sendMessage("§cCommande inconnue. Utilisez §e/gang help §cpour voir les commandes disponibles.");
                return true;
            }
        }

        return true;
    }

    private void handleCreateGang(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cUsage: /gang create <nom> <tag>");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifications
        if (!plugin.getMineManager().isRankSufficient(plugin.getMineManager().getCurrentRank(player), "g")) { // Rang "g" minimum                                │            player.sendMessage("§c❌ Vous devez avoir au moins le rang §6G §cpour créer un gang.");
            return;
        }

        if (playerData.getGangId() != null) {
            player.sendMessage("§c❌ Vous êtes déjà dans un gang!");
            return;
        }

        if (playerData.getBeacons() < 10000) {
            player.sendMessage("§c❌ Création d'un gang: §e10,000 beacons §c(vous avez: §e" +
                    NumberFormatter.format(playerData.getBeacons()) + "§c)");
            return;
        }

        String name = args[1];
        String tag = args[2];

        // Validation nom et tag
        if (name.length() < 3 || name.length() > 16) {
            player.sendMessage("§c❌ Le nom du gang doit faire entre 3 et 16 caractères.");
            return;
        }

        if (tag.length() < 2 || tag.length() > 6) {
            player.sendMessage("§c❌ Le tag du gang doit faire entre 2 et 6 caractères.");
            return;
        }

        if (!name.matches("[a-zA-Z0-9_]+") || !tag.matches("[a-zA-Z0-9_]+")) {
            player.sendMessage("§c❌ Le nom et tag ne peuvent contenir que des lettres, chiffres et _");
            return;
        }

        // Vérifier unicité
        if (plugin.getGangManager().gangExists(name, tag)) {
            player.sendMessage("§c❌ Un gang avec ce nom ou tag existe déjà.");
            return;
        }

        // Créer le gang
        boolean success = plugin.getGangManager().createGang(player, name, tag);
        if (success) {
            player.sendMessage("§a✅ Gang §e" + name + " §a[§e" + tag + "§a] créé avec succès!");
            player.sendMessage("§7Coût: §e10,000 beacons");
        } else {
            player.sendMessage("§c❌ Erreur lors de la création du gang.");
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /gang invite <joueur>");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        GangRole role = gang.getMemberRole(player.getUniqueId());
        if (role != GangRole.CHEF && role != GangRole.OFFICIER) {
            player.sendMessage("§c❌ Seuls les chefs et officiers peuvent inviter des membres.");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§c❌ Joueur introuvable ou hors ligne.");
            return;
        }

        boolean success = plugin.getGangManager().invitePlayer(gang, player, target);
        if (!success) {
            // Le message d'erreur est déjà envoyé par GangManager
            return;
        }

        player.sendMessage("§a✅ Invitation envoyée à §e" + target.getName() + "§a.");
        target.sendMessage("§6📨 Vous avez reçu une invitation du gang §e" + gang.getName() + " §6[§e" + gang.getTag() + "§6]");
        target.sendMessage("§7Invité par: §e" + player.getName());
        target.sendMessage("§a▶ §e/gang accept §a- Accepter l'invitation");
        target.sendMessage("§c▶ §e/gang deny §c- Refuser l'invitation");
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /gang kick <joueur>");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        GangRole role = gang.getMemberRole(player.getUniqueId());
        if (role != GangRole.CHEF && role != GangRole.OFFICIER) {
            player.sendMessage("§c❌ Seuls les chefs et officiers peuvent exclure des membres.");
            return;
        }

        String targetName = args[1];
        boolean success = plugin.getGangManager().kickPlayer(gang, player, targetName);

        if (success) {
            player.sendMessage("§a✅ §e" + targetName + " §aa été exclu du gang.");

            // Notifier le joueur s'il est en ligne
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                target.sendMessage("§c❌ Vous avez été exclu du gang §e" + gang.getName() + " §cpar §e" + player.getName() + "§c.");
            }
        }
    }

    private void handleLeave(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        if (gang.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Le chef ne peut pas quitter le gang. Utilisez §e/gang transfer §cou §e/gang disband§c.");
            return;
        }

        boolean success = plugin.getGangManager().removePlayer(gang, player.getUniqueId());
        if (success) {
            player.sendMessage("§a✅ Vous avez quitté le gang §e" + gang.getName() + "§a.");

            // Notifier les membres du gang
            gang.broadcast("§7" + player.getName() + " §7a quitté le gang.", null);
        }
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /gang promote <joueur>");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        if (!gang.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Seul le chef peut promouvoir des membres.");
            return;
        }

        String targetName = args[1];
        boolean success = plugin.getGangManager().promotePlayer(gang, targetName);

        if (success) {
            player.sendMessage("§a✅ §e" + targetName + " §aa été promu officier.");

            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                target.sendMessage("§a🎉 Vous avez été promu §6Officier §adu gang §e" + gang.getName() + "§a!");
            }

            gang.broadcast("§7" + targetName + " §7a été promu §6Officier§7.", player);
        }
    }

    private void handleDemote(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /gang demote <joueur>");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        if (!gang.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Seul le chef peut rétrograder des membres.");
            return;
        }

        String targetName = args[1];
        boolean success = plugin.getGangManager().demotePlayer(gang, targetName);

        if (success) {
            player.sendMessage("§a✅ §e" + targetName + " §aa été rétrogradé membre.");

            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                target.sendMessage("§c📉 Vous avez été rétrogradé §7Membre §cdu gang §e" + gang.getName() + "§c.");
            }

            gang.broadcast("§7" + targetName + " §7a été rétrogradé §7Membre§7.", player);
        }
    }

    private void handleTransfer(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /gang transfer <joueur>");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        if (!gang.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Seul le chef peut transférer le leadership.");
            return;
        }

        String targetName = args[1];
        boolean success = plugin.getGangManager().transferLeadership(gang, targetName);

        if (success) {
            player.sendMessage("§a✅ Leadership transféré à §e" + targetName + "§a.");

            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                target.sendMessage("§6👑 Vous êtes maintenant le chef du gang §e" + gang.getName() + "§6!");
            }

            gang.broadcast("§6👑 " + targetName + " §6est maintenant le nouveau chef du gang!", null);
        }
    }

    private void handleDisband(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        if (!gang.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Seul le chef peut dissoudre le gang.");
            return;
        }

        // Confirmation
        player.sendMessage("§c⚠️ §lATTENTION!");
        player.sendMessage("§cVous êtes sur le point de dissoudre le gang §e" + gang.getName() + "§c.");
        player.sendMessage("§cCette action est §lIRRÉVERSIBLE§c.");
        player.sendMessage("§cTapez §e/gang disband confirm §cpour confirmer.");
    }

    private void handleAcceptInvite(Player player) {
        plugin.getGangManager().acceptInvite(player);
    }

    private void handleDenyInvite(Player player) {
        boolean success = plugin.getGangManager().denyInvite(player);
        if (success) {
            player.sendMessage("§c❌ Invitation refusée.");
        } else {
            player.sendMessage("§c❌ Aucune invitation en attente.");
        }
    }

    private void handleInfo(Player player, String[] args) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        Gang gang;
        if (args.length > 1) {
            // Info d'un autre gang
            String gangName = args[1];
            gang = plugin.getGangManager().getGangByName(gangName);
            if (gang == null) {
                player.sendMessage("§c❌ Gang introuvable: §e" + gangName);
                return;
            }
        } else {
            // Info de son propre gang
            if (playerData.getGangId() == null) {
                player.sendMessage("§c❌ Vous n'êtes pas dans un gang. Utilisez §e/gang info <nom> §cpour voir les infos d'un autre gang.");
                return;
            }
            gang = plugin.getGangManager().getGang(playerData.getGangId());
            if (gang == null) {
                player.sendMessage("§c❌ Erreur: gang introuvable.");
                return;
            }
        }

        plugin.getGangGUI().openGangInfo(player, gang);
    }

    private void handleList(Player player) {
        plugin.getGangGUI().openGangList(player);
    }

    private void handleDeposit(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /gang deposit <montant>");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        long amount;
        try {
            amount = NumberFormatter.parse(args[1]);
            if (amount <= 0) {
                player.sendMessage("§c❌ Le montant doit être positif.");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Montant invalide.");
            return;
        }

        boolean success = plugin.getGangManager().depositToBank(gang, player, amount);
        if (success) {
            player.sendMessage("§a✅ Dépôt de §e" + NumberFormatter.format(amount) + " coins §adans la banque du gang.");
            gang.broadcast("§7" + player.getName() + " §7a déposé §e" + NumberFormatter.format(amount) + " coins §7dans la banque.", player);
        }
    }

    private void handleUpgrade(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        if (!gang.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Seul le chef peut améliorer le gang.");
            return;
        }

        plugin.getGangGUI().openUpgradeMenu(player, gang);
    }

    private void handleShop(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        plugin.getGangGUI().openShop(player, gang);
    }

    private void handleRename(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /gang rename <nouveau_nom>");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        if (!gang.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Seul le chef peut renommer le gang.");
            return;
        }

        String newName = args[1];
        boolean success = plugin.getGangManager().renameGang(gang, newName);

        if (success) {
            player.sendMessage("§a✅ Gang renommé en §e" + newName + "§a.");
            gang.broadcast("§6📝 Le gang a été renommé en §e" + newName + "§6!", null);
        }
    }

    private void handleDescription(Player player, String[] args) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        GangRole role = gang.getMemberRole(player.getUniqueId());
        if (role != GangRole.CHEF && role != GangRole.OFFICIER) {
            player.sendMessage("§c❌ Seuls les chefs et officiers peuvent modifier la description.");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /gang desc <description>");
            player.sendMessage("§7Description actuelle: §f" + (gang.getDescription() != null ? gang.getDescription() : "Aucune"));
            return;
        }

        String description = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (description.length() > 100) {
            player.sendMessage("§c❌ La description ne peut pas dépasser 100 caractères.");
            return;
        }

        gang.setDescription(description);
        plugin.getGangManager().saveGang(gang);

        player.sendMessage("§a✅ Description du gang mise à jour.");
        gang.broadcast("§7" + player.getName() + " §7a mis à jour la description du gang.", player);
    }

    private void handleBanner(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        if (!gang.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Seul le chef peut créer/modifier la bannière du gang.");
            return;
        }
        if (gang.getLevel() <= 2) {
            player.sendMessage("§c❌ Votre gang doit être au moins niveau pour créer/modifier la bannière du gang.");
            return;
        }

        // Ouvrir GUI de création de bannière
        plugin.getGangGUI().openBannerCreator(player, gang);
    }

    private void handleChat(Player player, String[] args) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            player.sendMessage("§c❌ Vous n'êtes pas dans un gang.");
            return;
        }

        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) {
            player.sendMessage("§c❌ Erreur: gang introuvable.");
            return;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 0, args.length));
        gang.sendChatMessage(player, message);
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6▬▬▬▬▬▬▬▬▬▬ §lCOMMANDES GANG §6▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§e/gang §7- Ouvre le menu principal");
        player.sendMessage("§e/gang create <nom> <tag> §7- Créer un gang (10k beacons)");
        player.sendMessage("§e/gang invite <joueur> §7- Inviter un joueur");
        player.sendMessage("§e/gang kick <joueur> §7- Exclure un membre");
        player.sendMessage("§e/gang leave §7- Quitter le gang");
        player.sendMessage("§e/gang promote <joueur> §7- Promouvoir en officier");
        player.sendMessage("§e/gang demote <joueur> §7- Rétrograder en membre");
        player.sendMessage("§e/gang transfer <joueur> §7- Transférer le leadership");
        player.sendMessage("§e/gang disband §7- Dissoudre le gang");
        player.sendMessage("§e/gang accept §7- Accepter une invitation");
        player.sendMessage("§e/gang deny §7- Refuser une invitation");
        player.sendMessage("§e/gang info [gang] §7- Informations du gang");
        player.sendMessage("§e/gang list §7- Liste des gangs");
        player.sendMessage("§e/gang deposit <montant> §7- Déposer dans la banque");
        player.sendMessage("§e/gang upgrade §7- Améliorer le gang");
        player.sendMessage("§e/gang shop §7- Boutique du gang");
        player.sendMessage("§e/gang rename <nom> §7- Renommer le gang (5k beacons)");
        player.sendMessage("§e/gang desc <description> §7- Changer la description");
        player.sendMessage("§e/gang banner §7- Créer/modifier la bannière");
        player.sendMessage("§e/g <message> §7- Chat du gang");
        player.sendMessage("§6▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        String commandName = command.getName().toLowerCase();

        if (args.length == 1 && !commandName.equals("g")) {
            List<String> subCommands = Arrays.asList(
                    "create", "invite", "kick", "leave", "promote", "demote", "transfer",
                    "disband", "accept", "deny", "info", "list", "deposit", "upgrade",
                    "shop", "rename", "description", "banner", "help"
            );
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("invite") ||
                args[0].equalsIgnoreCase("kick") ||
                args[0].equalsIgnoreCase("promote") ||
                args[0].equalsIgnoreCase("demote") ||
                args[0].equalsIgnoreCase("transfer"))) {
            // Auto-complétion des noms de joueurs
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
            StringUtil.copyPartialMatches(args[1], completions, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            // Auto-complétion des noms de gangs
            List<String> gangNames = plugin.getGangManager().getAllGangNames();
            StringUtil.copyPartialMatches(args[1], gangNames, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}