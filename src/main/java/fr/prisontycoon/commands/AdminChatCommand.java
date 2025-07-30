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
import java.util.concurrent.TimeUnit;

/**
 * Commandes administratives améliorées pour gérer le système de chat
 * Usage: /adminchat <sous-commande> [arguments]
 */
public class AdminChatCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public AdminChatCommand(PrisonTycoon plugin) {
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
            case "ban" -> handleBanCommand(sender, args);
            case "unban" -> handleUnbanCommand(sender, args);
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
     * Mute un joueur avec durée
     * Usage: /adminchat mute <joueur> <temps> [raison]
     */
    private void handleMuteCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c❌ Usage: /adminchat mute <joueur> <temps> [raison]");
            sender.sendMessage("§7Exemples de temps: 10m, 1h, 2d, permanent");
            return;
        }

        String playerName = args[1];
        String timeString = args[2];

        // Construit la raison
        String reason = "Aucune raison spécifiée";
        if (args.length > 3) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        // Parse la durée
        long muteDuration = parseTimeString(timeString);
        if (muteDuration == -1) {
            sender.sendMessage("§c❌ Format de temps invalide! Exemples: 10m, 1h, 2d, permanent");
            return;
        }

        // Cherche le joueur (en ligne ou hors ligne)
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        // Vérifie si c'est un admin
        if (target.isOnline() && target.getPlayer().hasPermission("specialmine.admin")) {
            sender.sendMessage("§c❌ Vous ne pouvez pas muter un administrateur!");
            return;
        }

        // Applique le mute
        long muteEnd = muteDuration == 0 ? 0 : System.currentTimeMillis() + muteDuration;
        plugin.getModerationManager().mutePlayer(target.getUniqueId(), target.getName(), muteEnd, reason, sender.getName());

        // Messages
        String durationText = muteDuration == 0 ? "permanent" : formatDuration(muteDuration);
        if (target.isOnline()) {
            Player onlineTarget = target.getPlayer();
            onlineTarget.sendMessage("§c🔇 Vous avez été muté par " + sender.getName());
            onlineTarget.sendMessage("§7Durée: §e" + durationText);
            onlineTarget.sendMessage("§7Raison: §e" + reason);
        }

        sender.sendMessage("§a✅ Joueur " + target.getName() + " muté pour " + durationText);
        sender.sendMessage("§7Raison: " + reason);

        // Annonce aux admins
        broadcastToAdmins("§7[ADMIN] §e" + sender.getName() + " §7a muté §c" + target.getName() +
                          " §7pour " + durationText + " (Raison: " + reason + ")", sender);

        plugin.getPluginLogger().info("Joueur muté: " + target.getName() + " par " + sender.getName() +
                                      " pour " + durationText + " (Raison: " + reason + ")");
    }

    /**
     * Unmute un joueur
     * Usage: /adminchat unmute <joueur>
     */
    private void handleUnmuteCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /adminchat unmute <joueur>");
            return;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        if (!plugin.getModerationManager().isMuted(target.getUniqueId())) {
            sender.sendMessage("§c❌ Ce joueur n'est pas muté!");
            return;
        }

        // Retire le mute
        plugin.getModerationManager().unmutePlayer(target.getUniqueId(), sender.getName());

        // Messages
        if (target.isOnline()) {
            target.getPlayer().sendMessage("§a🔊 Vous avez été démuté par " + sender.getName());
        }

        sender.sendMessage("§a✅ Joueur " + target.getName() + " démuté avec succès.");

        // Annonce aux admins
        broadcastToAdmins("§7[ADMIN] §e" + sender.getName() + " §7a démuté §a" + target.getName(), sender);

        plugin.getPluginLogger().info("Joueur démuté: " + target.getName() + " par " + sender.getName());
    }

    /**
     * Ban un joueur avec durée
     * Usage: /adminchat ban <joueur> <temps> [raison]
     */
    private void handleBanCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§c❌ Usage: /adminchat ban <joueur> <temps> [raison]");
            sender.sendMessage("§7Exemples de temps: 10m, 1h, 2d, permanent");
            return;
        }

        String playerName = args[1];
        String timeString = args[2];

        // Construit la raison
        String reason = "Aucune raison spécifiée";
        if (args.length > 3) {
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 3; i < args.length; i++) {
                reasonBuilder.append(args[i]).append(" ");
            }
            reason = reasonBuilder.toString().trim();
        }

        // Parse la durée
        long banDuration = parseTimeString(timeString);
        if (banDuration == -1) {
            sender.sendMessage("§c❌ Format de temps invalide! Exemples: 10m, 1h, 2d, permanent");
            return;
        }

        // Cherche le joueur
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        // Vérifie si c'est un admin
        if (target.isOnline() && target.getPlayer().hasPermission("specialmine.admin")) {
            sender.sendMessage("§c❌ Vous ne pouvez pas bannir un administrateur!");
            return;
        }

        // Applique le ban
        long banEnd = banDuration == 0 ? 0 : System.currentTimeMillis() + banDuration;
        plugin.getModerationManager().banPlayer(target.getUniqueId(), target.getName(), banEnd, reason, sender.getName());

        // Messages
        String durationText = banDuration == 0 ? "permanent" : formatDuration(banDuration);
        if (target.isOnline()) {
            String kickMessage = "§c§l=== BANNISSEMENT ===\n\n" +
                                 "§cVous avez été banni du serveur\n" +
                                 "§7Durée: §e" + durationText + "\n" +
                                 "§7Raison: §e" + reason + "\n" +
                                 "§7Par: §e" + sender.getName();
            target.getPlayer().kickPlayer(kickMessage);
        }

        sender.sendMessage("§a✅ Joueur " + target.getName() + " banni pour " + durationText);
        sender.sendMessage("§7Raison: " + reason);

        // Annonce aux admins
        broadcastToAdmins("§7[ADMIN] §e" + sender.getName() + " §7a banni §c" + target.getName() +
                          " §7pour " + durationText + " (Raison: " + reason + ")", sender);

        plugin.getPluginLogger().info("Joueur banni: " + target.getName() + " par " + sender.getName() +
                                      " pour " + durationText + " (Raison: " + reason + ")");
    }

    /**
     * Unban un joueur
     * Usage: /adminchat unban <joueur>
     */
    private void handleUnbanCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /adminchat unban <joueur>");
            return;
        }

        String playerName = args[1];
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            sender.sendMessage("§c❌ Joueur introuvable: " + playerName);
            return;
        }

        if (!plugin.getModerationManager().isBanned(target.getUniqueId())) {
            sender.sendMessage("§c❌ Ce joueur n'est pas banni!");
            return;
        }

        // Retire le ban
        plugin.getModerationManager().unbanPlayer(target.getUniqueId(), sender.getName());

        sender.sendMessage("§a✅ Joueur " + target.getName() + " débanni avec succès.");

        // Annonce aux admins
        broadcastToAdmins("§7[ADMIN] §e" + sender.getName() + " §7a débanni §a" + target.getName(), sender);

        plugin.getPluginLogger().info("Joueur débanni: " + target.getName() + " par " + sender.getName());
    }

    /**
     * Recharge le système
     */
    private void handleReloadCommand(CommandSender sender) {
        try {
            plugin.getModerationManager().reload();

            sender.sendMessage("§a✅ Système de chat rechargé avec succès!");
            plugin.getPluginLogger().info("Système rechargé par " + sender.getName());
        } catch (Exception e) {
            sender.sendMessage("§c❌ Erreur lors du rechargement: " + e.getMessage());
            plugin.getPluginLogger().severe("Erreur lors du rechargement par " + sender.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Affiche les statistiques
     */
    private void handleStatsCommand(CommandSender sender) {
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l📊 STATISTIQUES DU CHAT");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        int mutedCount = plugin.getModerationManager().getMutedPlayersCount();
        int bannedCount = plugin.getModerationManager().getBannedPlayersCount();
        int messagesLogged = plugin.getChatLogger().getTotalMessagesLogged();

        sender.sendMessage("§e• Joueurs mutés: §6" + mutedCount);
        sender.sendMessage("§e• Joueurs bannis: §6" + bannedCount);
        sender.sendMessage("§e• Messages enregistrés: §6" + messagesLogged);
        sender.sendMessage("§e• Joueurs VIP: §6" + plugin.getVipManager().getVipCount());

        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Teste les formats de chat
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
        sender.sendMessage("§4[ADMIN] §c[Z] §cAdminTest §f: §4" + testMessage);
        sender.sendMessage("§e[VIP] §6[M] §6VipTest §f: §e" + testMessage);
        sender.sendMessage("§a[J] §7JoueurTest §f: " + testMessage);
        sender.sendMessage("");
        sender.sendMessage("§7Fonctionnalités spéciales:");
        sender.sendMessage("§e[VIP/ADMIN] §7[hand] = Affiche l'item en main");
        sender.sendMessage("§e[VIP/ADMIN] §7[inv] = Bouton inventaire cliquable");
        sender.sendMessage("§e[VIP/ADMIN] §7&c, &e, etc. = Couleurs dans le chat");
    }

    /**
     * Diffuse un message depuis l'administration
     */
    private void handleBroadcastCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c❌ Usage: /adminchat broadcast <message>");
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
     * Parse une chaîne de temps (ex: 10m, 1h, 2d, permanent)
     */
    private long parseTimeString(String timeString) {
        if (timeString.equalsIgnoreCase("permanent") || timeString.equalsIgnoreCase("perm")) {
            return 0; // 0 = permanent
        }

        if (timeString.length() < 2) {
            return -1;
        }

        String numberPart = timeString.substring(0, timeString.length() - 1);
        String unitPart = timeString.substring(timeString.length() - 1);

        try {
            long number = Long.parseLong(numberPart);
            return switch (unitPart.toLowerCase()) {
                case "s" -> TimeUnit.SECONDS.toMillis(number);
                case "m" -> TimeUnit.MINUTES.toMillis(number);
                case "h" -> TimeUnit.HOURS.toMillis(number);
                case "d" -> TimeUnit.DAYS.toMillis(number);
                default -> -1;
            };
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Formate une durée en millisecondes en texte lisible
     */
    private String formatDuration(long milliseconds) {
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60;

        if (days > 0) {
            return days + "j " + hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§6§l⚙ COMMANDES ADMIN CHAT");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§e/adminchat mute <joueur> <temps> [raison] §7- Mute un joueur");
        sender.sendMessage("§e/adminchat unmute <joueur> §7- Démute un joueur");
        sender.sendMessage("§e/adminchat ban <joueur> <temps> [raison] §7- Ban un joueur");
        sender.sendMessage("§e/adminchat unban <joueur> §7- Déban un joueur");
        sender.sendMessage("§e/adminchat reload §7- Recharge le système");
        sender.sendMessage("§e/adminchat stats §7- Affiche les statistiques");
        sender.sendMessage("§e/adminchat test [message] §7- Teste les formats");
        sender.sendMessage("§e/adminchat broadcast <message> §7- Diffuse une annonce");
        sender.sendMessage("§e/adminchat clear §7- Vide le chat pour tous");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§7Temps: 10s, 5m, 2h, 1d, permanent");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("mute", "unmute", "ban", "unban", "reload", "stats", "test", "broadcast", "clear");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("unmute") ||
                args[0].equalsIgnoreCase("ban") || args[0].equalsIgnoreCase("unban")) {
                // Suggestions de noms de joueurs
                for (Player player : Bukkit.getOnlinePlayers()) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("mute") || args[0].equalsIgnoreCase("ban")) {
                // Suggestions de temps
                List<String> timeExamples = Arrays.asList("10m", "1h", "2h", "6h", "1d", "3d", "7d", "permanent");
                StringUtil.copyPartialMatches(args[2], timeExamples, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}