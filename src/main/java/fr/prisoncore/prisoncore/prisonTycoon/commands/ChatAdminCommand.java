package fr.prisoncore.prisoncore.prisonTycoon.commands;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.Bukkit;
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
 * Commandes administratives pour gérer le système de chat et tab
 * Usage: /chatadmin <sous-commande> [arguments]
 */
public class ChatAdminCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public ChatAdminCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("specialmine.admin.chat")) {
            sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "mute" -> handleMuteCommand(sender, args);
            case "unmute" -> handleUnmuteCommand(sender, args);
            case "reload" -> handleReloadCommand(sender);
            case "stats" -> handleStatsCommand(sender);
            case "test" -> handleTestCommand(sender, args);
            case "broadcast" -> handleBroadcastCommand(sender, args);
            case "clear" -> handleClearCommand(sender);
            default -> sendHelpMessage(sender);
        }

        return true;
    }

    /**
     * Mute un joueur
     */
    private void handleMuteCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /chatadmin mute <joueur> [raison]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c❌ Joueur introuvable: " + args[1]);
            return;
        }

        if (target.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c❌ Vous ne pouvez pas muter un administrateur!");
            return;
        }

        // Construit la raison
        String reason = "Aucune raison spécifiée";
        if (args.length > 2) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        // Mute le joueur
        target.addAttachment(plugin, "specialmine.chat", false);

        // Messages
        target.sendMessage("§c🔇 Vous avez été muté par " + sender.getName());
        target.sendMessage("§7Raison: §e" + reason);
        sender.sendMessage("§a✅ Joueur " + target.getName() + " muté avec succès.");

        // Annonce aux admins
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("specialmine.admin") && !admin.equals(sender)) {
                admin.sendMessage("§7[ADMIN] §e" + sender.getName() + " §7a muté §c" + target.getName() + " §7(Raison: " + reason + ")");
            }
        }

        plugin.getPluginLogger().info("Joueur muté: " + target.getName() + " par " + sender.getName() + " (Raison: " + reason + ")");
    }

    /**
     * Unmute un joueur
     */
    private void handleUnmuteCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /chatadmin unmute <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c❌ Joueur introuvable: " + args[1]);
            return;
        }

        // Unmute le joueur
        target.addAttachment(plugin, "specialmine.chat", true);

        // Messages
        target.sendMessage("§a🔊 Vous avez été démuté par " + sender.getName());
        sender.sendMessage("§a✅ Joueur " + target.getName() + " démuté avec succès.");

        // Annonce aux admins
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission("specialmine.admin") && !admin.equals(sender)) {
                admin.sendMessage("§7[ADMIN] §e" + sender.getName() + " §7a démuté §a" + target.getName());
            }
        }

        plugin.getPluginLogger().info("Joueur démuté: " + target.getName() + " par " + sender.getName());
    }

    /**
     * Recharge le système de chat/tab
     */
    private void handleReloadCommand(CommandSender sender) {
        try {
            // Redémarre le TabManager
            plugin.getTabManager().stopTabUpdater();
            plugin.getTabManager().startTabUpdater();

            // Met à jour tous les tabs
            for (Player player : Bukkit.getOnlinePlayers()) {
                plugin.getTabManager().forceUpdatePlayer(player);
            }

            sender.sendMessage("§a✅ Système de chat et tab rechargé avec succès!");
            plugin.getPluginLogger().info("Système de chat/tab rechargé par " + sender.getName());

        } catch (Exception e) {
            sender.sendMessage("§c❌ Erreur lors du rechargement: " + e.getMessage());
            plugin.getPluginLogger().severe("Erreur lors du rechargement chat/tab: " + e.getMessage());
        }
    }

    /**
     * Affiche les statistiques du système
     */
    private void handleStatsCommand(CommandSender sender) {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int adminCount = 0;
        int vipCount = 0;
        int playerCount = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("specialmine.admin")) {
                adminCount++;
            } else if (player.hasPermission("specialmine.vip")) {
                vipCount++;
            } else {
                playerCount++;
            }
        }

        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l📊 STATISTIQUES CHAT & TAB");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§e📈 Joueurs connectés: §a" + onlinePlayers);
        sender.sendMessage("§c🔧 Administrateurs: §f" + adminCount);
        sender.sendMessage("§6⭐ VIP: §f" + vipCount);
        sender.sendMessage("§7👤 Joueurs: §f" + playerCount);
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Teste le format de chat avec différents rangs
     */
    private void handleTestCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c❌ Cette commande ne peut être utilisée que par un joueur!");
            return;
        }

        String testMessage = "Message de test du système de chat!";
        if (args.length > 1) {
            StringBuilder msgBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                msgBuilder.append(args[i]).append(" ");
            }
            testMessage = msgBuilder.toString().trim();
        }

        sender.sendMessage("§a🧪 Test des formats de chat:");
        sender.sendMessage("§4[ADMIN] §c[Z] §cAdminTest §f: " + testMessage);
        sender.sendMessage("§e[VIP] §6[M] §6VipTest §f: " + testMessage);
        sender.sendMessage("§a[J] §7JoueurTest §f: " + testMessage);
        sender.sendMessage("");
        sender.sendMessage("§7Exemple de progression des rangs:");
        sender.sendMessage("§f[A] §7Débutant §f: " + testMessage);
        sender.sendMessage("§e[L] §7Intermédiaire §f: " + testMessage);
        sender.sendMessage("§c[P] §7Avancé §f: " + testMessage);
        sender.sendMessage("§6§l[Z] §7Maximum §f: " + testMessage);
    }

    /**
     * Diffuse un message depuis l'administration
     */
    private void handleBroadcastCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /chatadmin broadcast <message>");
            return;
        }

        StringBuilder messageBuilder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            messageBuilder.append(args[i]).append(" ");
        }
        String message = messageBuilder.toString().trim();

        String broadcastMessage = "§8§l[§6§lANNONCE§8§l] §e" + message;

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(broadcastMessage);
        }

        sender.sendMessage("§a✅ Annonce diffusée à " + Bukkit.getOnlinePlayers().size() + " joueurs.");
        plugin.getPluginLogger().info("Annonce diffusée par " + sender.getName() + ": " + message);
    }

    /**
     * Vide le chat de tous les joueurs
     */
    private void handleClearCommand(CommandSender sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (int i = 0; i < 100; i++) {
                player.sendMessage("");
            }
            player.sendMessage("§a✅ Chat vidé par un administrateur.");
        }

        sender.sendMessage("§a✅ Chat vidé pour tous les joueurs connectés.");
        plugin.getPluginLogger().info("Chat vidé par " + sender.getName());
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l⚙ COMMANDES ADMIN CHAT");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§e/chatadmin mute <joueur> [raison] §7- Mute un joueur");
        sender.sendMessage("§e/chatadmin unmute <joueur> §7- Démute un joueur");
        sender.sendMessage("§e/chatadmin reload §7- Recharge le système");
        sender.sendMessage("§e/chatadmin stats §7- Affiche les statistiques");
        sender.sendMessage("§e/chatadmin test [message] §7- Teste les formats");
        sender.sendMessage("§e/chatadmin broadcast <message> §7- Diffuse une annonce");
        sender.sendMessage("§e/chatadmin clear §7- Vide le chat pour tous");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("mute", "unmute", "reload", "stats", "test", "broadcast", "clear");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute"))) {
            // Suggestions de noms de joueurs pour mute/unmute
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
        }

        Collections.sort(completions);
        return completions;
    }
}