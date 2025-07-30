package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.ReputationManager;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Gestionnaire des commandes de réputation
 * /rep - Consulter sa réputation exacte et historique récent
 * /rep help - Liste actions influençant réputation
 * /rep voir [joueur] - Voir niveau réputation autrui (permission specialmine.repvoir)
 */
public class ReputationCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;
    private final ReputationManager reputationManager;

    public ReputationCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.reputationManager = plugin.getReputationManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            // /rep - Affiche sa propre réputation
            showPlayerReputation(player, player.getUniqueId());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                showReputationHelp(player);
                break;

            case "voir":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /rep voir <joueur>");
                    return true;
                }

                if (!player.hasPermission("specialmine.repvoir")) {
                    player.sendMessage("§cVous n'avez pas la permission de voir la réputation des autres!");
                    return true;
                }

                showOtherPlayerReputation(player, args[1]);
                break;

            case "admin":
                if (!player.hasPermission("specialmine.admin")) {
                    player.sendMessage("§cVous n'avez pas la permission d'utiliser les commandes admin!");
                    return true;
                }
                handleAdminCommands(player, Arrays.copyOfRange(args, 1, args.length));
                break;

            default:
                player.sendMessage("§cCommande inconnue! Utilisez §e/rep help §cpour l'aide.");
                break;
        }

        return true;
    }

    /**
     * Affiche la réputation détaillée d'un joueur
     */
    private void showPlayerReputation(Player viewer, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        String targetName = target != null ? target.getName() :
                plugin.getPlayerDataManager().getPlayerData(targetId).getPlayerName();

        int reputation = reputationManager.getReputation(targetId);
        ReputationTier tier = reputationManager.getReputationTier(targetId);

        boolean isOwnReputation = viewer.getUniqueId().equals(targetId);

        viewer.sendMessage("§6════════════════════════════════");
        viewer.sendMessage("§e§lRÉPUTATION" + (isOwnReputation ? "" : " DE " + targetName.toUpperCase()));
        viewer.sendMessage("§6════════════════════════════════");
        viewer.sendMessage("");

        viewer.sendMessage("§7Joueur: §f" + targetName);
        viewer.sendMessage("§7Réputation: §f" + reputation + " §8/1000");
        viewer.sendMessage("§7Niveau: " + tier.getColoredTitle());
        viewer.sendMessage("");

        // Barre de progression visuelle
        viewer.sendMessage("§7Progression: " + createProgressBar(reputation, tier));
        viewer.sendMessage("");

        // Effets de la réputation
        viewer.sendMessage("§e§lEFFETS ACTUELS:");
        viewer.sendMessage(tier.getEffectsDescription());
        viewer.sendMessage("");

        // Historique récent (seulement pour sa propre réputation)
        if (isOwnReputation) {
            showRecentHistory(viewer, targetId);
        }

        viewer.sendMessage("§6════════════════════════════════");
    }

    /**
     * Affiche l'historique récent des changements de réputation
     */
    private void showRecentHistory(Player player, UUID playerId) {
        List<ReputationManager.ReputationChange> changes = reputationManager.getRecentChanges(playerId);

        if (changes.isEmpty()) {
            player.sendMessage("§7Aucun changement récent de réputation.");
            return;
        }

        player.sendMessage("§e§lHISTORIQUE RÉCENT:");

        int shown = 0;
        for (ReputationManager.ReputationChange change : changes) {
            if (shown >= 5) break; // Limite à 5 entrées

            player.sendMessage("§8• §7" + change.getFormattedTime() + " §8| " +
                               change.getChangeDisplay() + " §8| §7" + change.reason());
            shown++;
        }

        if (changes.size() > 5) {
            player.sendMessage("§8... et " + (changes.size() - 5) + " autres changements");
        }
        player.sendMessage("");
    }

    /**
     * Crée une barre de progression visuelle pour la réputation
     */
    private String createProgressBar(int reputation, ReputationTier tier) {
        int barLength = 20;
        StringBuilder bar = new StringBuilder("§8[");

        // Calcule la position sur la barre (-1000 à +1000)
        double position = (reputation + 1000) / 2000.0; // 0.0 à 1.0
        int filledBars = (int) (position * barLength);

        for (int i = 0; i < barLength; i++) {
            if (i < filledBars) {
                if (reputation > 0) {
                    bar.append("§a■");
                } else if (reputation < 0) {
                    bar.append("§c■");
                } else {
                    bar.append("§7■");
                }
            } else {
                bar.append("§8■");
            }
        }

        bar.append("§8]");
        return bar.toString();
    }

    /**
     * Affiche l'aide sur le système de réputation
     */
    private void showReputationHelp(Player player) {
        player.sendMessage("§6════════════════════════════════");
        player.sendMessage("§e§lSYSTÈME DE RÉPUTATION - AIDE");
        player.sendMessage("§6════════════════════════════════");
        player.sendMessage("");

        player.sendMessage("§e§lACTIONS QUI AUGMENTENT LA RÉPUTATION:");
        player.sendMessage("§a+5 §7Participer à 'Contenir la Brèche'");
        player.sendMessage("§a+20 §7Finir Top 3 dans 'Contenir la Brèche'");
        player.sendMessage("§a+? §7Aider d'autres joueurs");
        player.sendMessage("§a+? §7Respecter les règles du serveur");
        player.sendMessage("");

        player.sendMessage("§e§lACTIONS QUI DIMINUENT LA RÉPUTATION:");
        player.sendMessage("§c-5 §7Participer à 'Course au Butin'");
        player.sendMessage("§c-20 §7Finir Top 3 dans 'Course au Butin'");
        player.sendMessage("§c-1 à -3 §7Acheter au Black Market");
        player.sendMessage("§c-? §7Comportements antisociaux");
        player.sendMessage("");

        player.sendMessage("§e§lNIVEAUX DE RÉPUTATION:");
        for (ReputationTier tier : ReputationTier.values()) {
            player.sendMessage("§8• " + tier.getColoredTitle() + " §8(" +
                               tier.getMinReputation() + " à " + tier.getMaxReputation() + ")");
        }
        player.sendMessage("");

        player.sendMessage("§e§lEFFETS:");
        player.sendMessage("§7• Modification des taxes globales");
        player.sendMessage("§7• Accès et prix du Black Market");
        player.sendMessage("§7• Bonus/malus dans certains événements");
        player.sendMessage("");

        player.sendMessage("§8Note: La réputation extrême (±750+) s'érode naturellement");
        player.sendMessage("§6════════════════════════════════");
    }

    /**
     * Affiche la réputation d'un autre joueur
     */
    private void showOtherPlayerReputation(Player viewer, String targetName) {
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            viewer.sendMessage("§cJoueur '" + targetName + "' introuvable ou hors ligne!");
            return;
        }

        // Affiche seulement le niveau, pas la valeur exacte
        ReputationTier tier = reputationManager.getReputationTier(target.getUniqueId());

        viewer.sendMessage("§6════════════════════════════════");
        viewer.sendMessage("§e§lRÉPUTATION DE " + target.getName().toUpperCase());
        viewer.sendMessage("§6════════════════════════════════");
        viewer.sendMessage("");
        viewer.sendMessage("§7Niveau de réputation: " + tier.getColoredTitle());
        viewer.sendMessage("§7Effets: " + tier.getBlackMarketDescription());
        viewer.sendMessage("");
        viewer.sendMessage("§8Note: La valeur exacte n'est visible que par le joueur lui-même");
        viewer.sendMessage("§6════════════════════════════════");
    }

    /**
     * Gère les commandes d'administration
     */
    private void handleAdminCommands(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§e/rep admin set <joueur> <valeur> - Définir réputation");
            player.sendMessage("§e/rep admin add <joueur> <valeur> [raison] - Ajouter réputation");
            player.sendMessage("§e/rep admin reset <joueur> - Reset réputation à 0");
            player.sendMessage("§e/rep admin erosion - Force l'érosion naturelle");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "set":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /rep admin set <joueur> <valeur>");
                    return;
                }
                adminSetReputation(player, args[1], args[2]);
                break;

            case "add":
                if (args.length < 3) {
                    player.sendMessage("§cUsage: /rep admin add <joueur> <valeur> [raison]");
                    return;
                }
                String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : "Admin";
                adminAddReputation(player, args[1], args[2], reason);
                break;

            case "reset":
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /rep admin reset <joueur>");
                    return;
                }
                adminResetReputation(player, args[1]);
                break;

            case "erosion":
                // Force l'érosion naturelle
                player.sendMessage("§7Force l'érosion naturelle...");
                // TODO: Appeler la méthode d'érosion
                break;

            default:
                player.sendMessage("§cCommande admin inconnue!");
                break;
        }
    }

    /**
     * Définit la réputation d'un joueur (admin)
     */
    private void adminSetReputation(Player admin, String targetName, String valueStr) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            admin.sendMessage("§cJoueur introuvable!");
            return;
        }

        try {
            int value = Integer.parseInt(valueStr);
            value = Math.max(-1000, Math.min(1000, value)); // Limite entre -1000 et +1000

            int oldReputation = reputationManager.getReputation(target.getUniqueId());
            int change = value - oldReputation;

            reputationManager.modifyReputation(target.getUniqueId(), change, "Admin: " + admin.getName());

            admin.sendMessage("§aRéputation de " + target.getName() + " définie à " + value +
                              " (ancien: " + oldReputation + ")");

            target.sendMessage("§6Votre réputation a été modifiée par un administrateur.");

        } catch (NumberFormatException e) {
            admin.sendMessage("§cValeur invalide! Utilisez un nombre entre -1000 et 1000.");
        }
    }

    /**
     * Ajoute de la réputation à un joueur (admin)
     */
    private void adminAddReputation(Player admin, String targetName, String changeStr, String reason) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            admin.sendMessage("§cJoueur introuvable!");
            return;
        }

        try {
            int change = Integer.parseInt(changeStr);
            int oldReputation = reputationManager.getReputation(target.getUniqueId());

            reputationManager.modifyReputation(target.getUniqueId(), change, "Admin: " + reason);

            int newReputation = reputationManager.getReputation(target.getUniqueId());
            String changeColor = change >= 0 ? "§a+" : "§c";

            admin.sendMessage("§aRéputation de " + target.getName() + ": " + oldReputation +
                              " → " + newReputation + " (" + changeColor + change + "§a)");

            target.sendMessage("§6Votre réputation a été modifiée: " + changeColor + change + " §6(" + reason + ")");

        } catch (NumberFormatException e) {
            admin.sendMessage("§cValeur invalide! Utilisez un nombre entre -1000 et 1000.");
        }
    }

    /**
     * Remet la réputation d'un joueur à 0 (admin)
     */
    private void adminResetReputation(Player admin, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            admin.sendMessage("§cJoueur introuvable!");
            return;
        }

        int oldReputation = reputationManager.getReputation(target.getUniqueId());
        reputationManager.modifyReputation(target.getUniqueId(), -oldReputation, "Admin Reset: " + admin.getName());

        admin.sendMessage("§aRéputation de " + target.getName() + " remise à 0 (ancien: " + oldReputation + ")");
        target.sendMessage("§6Votre réputation a été remise à zéro par un administrateur.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = Arrays.asList("help", "voir");
            if (sender.hasPermission("specialmine.admin")) {
                commands = new ArrayList<>(commands);
                commands.add("admin");
            }

            for (String cmd : commands) {
                if (cmd.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(cmd);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("voir")) {
                // Suggestions de joueurs en ligne
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("specialmine.admin")) {
                List<String> adminCommands = Arrays.asList("set", "add", "reset", "erosion");
                for (String cmd : adminCommands) {
                    if (cmd.toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(cmd);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            if (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("reset")) {
                // Suggestions de joueurs en ligne
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
        }

        return completions;
    }
}