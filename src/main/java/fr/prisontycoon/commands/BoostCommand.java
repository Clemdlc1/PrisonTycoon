package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.boosts.PlayerBoost;
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
 * Commande /boost étendue pour voir et gérer les boosts temporaires
 * Usage:
 * - /boost : Ouvre l'interface graphique des boosts
 * - /boost list : Affiche les boosts actifs en texte
 * - /boost admin <type> <durée> [bonus] : Ajoute un boost admin global
 */
public class BoostCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public BoostCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            // Ouvre l'interface graphique des boosts
            plugin.getBoostGUI().openBoostMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list", "show" -> {
                // Affiche les boosts actifs en mode texte
                showPlayerBoostsText(player);
                return true;
            }
            case "admin" -> {
                if (!player.hasPermission("specialmine.admin.boost")) {
                    player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
                    return true;
                }

                if (args.length < 3) {
                    player.sendMessage("§cUsage: /boost admin <type> <durée_minutes> [bonus_%]");
                    player.sendMessage("§7Types disponibles: " + getAvailableBoostTypes());
                    return true;
                }

                return handleAdminBoost(player, args);
            }
            case "help", "aide" -> {
                showHelpMessage(player);
                return true;
            }
            default -> {
                player.sendMessage("§cCommande inconnue! Utilisez:");
                player.sendMessage("§7- /boost : Ouvrir l'interface des boosts");
                player.sendMessage("§7- /boost list : Voir vos boosts en texte");
                player.sendMessage("§7- /boost help : Afficher l'aide");
                if (player.hasPermission("specialmine.admin.boost")) {
                    player.sendMessage("§7- /boost admin <type> <durée> : Ajouter un boost admin");
                }
                return true;
            }
        }
    }

    /**
     * Affiche les boosts actifs d'un joueur en mode texte
     */
    private void showPlayerBoostsText(Player player) {
        List<PlayerBoost> activeBoosts = plugin.getBoostManager().getActiveBoosts(player);

        player.sendMessage("§6⚡ §lVOS BOOSTS ACTIFS");
        player.sendMessage("§7§m────────────────────────────────");

        if (activeBoosts.isEmpty()) {
            player.sendMessage("§7Aucun boost actif actuellement.");
            player.sendMessage("§7Utilisez des items boost ou attendez des boosts admin!");
            player.sendMessage("§7Tapez §e/boost help §7pour plus d'infos.");
        } else {
            for (PlayerBoost boost : activeBoosts) {
                String prefix = boost.isAdminBoost() ? "§c[ADMIN] " : "§a[PERSO] ";
                player.sendMessage(prefix + boost.getDescription());

                // Affiche une barre de progression simplifiée
                double progress = boost.getProgress();
                int filledBars = (int) (progress * 10);
                StringBuilder progressBar = new StringBuilder("§7[§a");

                for (int i = 0; i < 10; i++) {
                    if (i < filledBars) {
                        progressBar.append("█");
                    } else {
                        progressBar.append("§7█");
                    }
                }
                progressBar.append("§7] ").append(String.format("%.1f%%", progress * 100));

                player.sendMessage("         " + progressBar);
            }
        }

        player.sendMessage("§7§m────────────────────────────────");
        player.sendMessage("§7Utilisez §e/boost §7pour ouvrir l'interface graphique!");
    }

    /**
     * Affiche le message d'aide
     */
    private void showHelpMessage(Player player) {
        player.sendMessage("§6⚡ §lAIDE - SYSTÈME DE BOOSTS");
        player.sendMessage("§7§m────────────────────────────────");
        player.sendMessage("§e/boost §7- Ouvre l'interface graphique");
        player.sendMessage("§e/boost list §7- Affiche vos boosts en texte");
        player.sendMessage("§e/boost help §7- Affiche cette aide");

        if (player.hasPermission("specialmine.admin.boost")) {
            player.sendMessage("§e/boost admin <type> <durée> §7- Boost admin global");
        }

        player.sendMessage("");
        player.sendMessage("§7§lComment utiliser les boosts:");
        player.sendMessage("§7• Obtenez des items boost des événements/récompenses");
        player.sendMessage("§7• Utilisez §eclic droit §7sur un item boost pour l'activer");
        player.sendMessage("§7• Maximum 1 boost par type actif à la fois");
        player.sendMessage("§7• Les boosts admin s'appliquent à tous les joueurs");
        player.sendMessage("§7• Les boosts s'appliquent automatiquement à vos gains");
        player.sendMessage("");
        player.sendMessage("§7§lTypes de boosts disponibles:");
        player.sendMessage("§b• Token Greed §7- Augmente les gains de tokens");
        player.sendMessage("§6• Money Greed §7- Augmente les gains de coins");
        player.sendMessage("§a• XP Greed §7- Augmente les gains d'expérience");
        player.sendMessage("§e• Sell Boost §7- Augmente les prix de vente");
        player.sendMessage("§9• Mineral Greed §7- Augmente l'effet Fortune");
        player.sendMessage("§d• Job XP §7- Augmente les gains d'XP métier");
        player.sendMessage("§c• Global §7- Bonus sur tous les types de gains");
        player.sendMessage("§7§m────────────────────────────────");
    }

    /**
     * Gère la commande admin boost
     */
    private boolean handleAdminBoost(Player player, String[] args) {
        try {
            // Parse le type de boost
            BoostType type = BoostType.fromString(args[1]);
            if (type == null) {
                player.sendMessage("§cType de boost invalide: " + args[1]);
                player.sendMessage("§7Types disponibles: " + getAvailableBoostTypes());
                return true;
            }

            // Parse la durée
            int duration;
            try {
                duration = Integer.parseInt(args[2]);
                if (duration <= 0 || duration > 1440) { // Max 24 heures
                    player.sendMessage("§cDurée invalide! (1-1440 minutes)");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cDurée invalide! Utilisez un nombre de minutes.");
                return true;
            }

            // Parse le bonus (optionnel)
            double bonus = type.getBonusPercentage(); // Valeur par défaut
            if (args.length >= 4) {
                try {
                    bonus = Double.parseDouble(args[3]);
                    if (bonus <= 0 || bonus > 500) { // Max 500%
                        player.sendMessage("§cBonus invalide! (1-500%)");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§cBonus invalide! Utilisez un pourcentage.");
                    return true;
                }
            }

            // Active le boost admin
            plugin.getBoostManager().addAdminBoost(type, duration * 60, bonus);

            player.sendMessage("§a✅ Boost admin activé avec succès!");
            player.sendMessage("§7Type: " + type.getFormattedName());
            player.sendMessage("§7Bonus: " + type.getColor() + "+" + String.format("%.0f", bonus) + "%");
            player.sendMessage("§7Durée: §e" + duration + " minutes");
            player.sendMessage("§7Tous les joueurs en ligne ont été notifiés.");

            plugin.getPluginLogger().info("Boost admin activé par " + player.getName() +
                    ": " + type.name() + " +" + bonus + "% pour " + duration + " minutes");

            return true;

        } catch (Exception e) {
            player.sendMessage("§cErreur lors de l'activation du boost admin.");
            plugin.getPluginLogger().severe("Erreur boost admin par " + player.getName() + ": " + e.getMessage());
            return true;
        }
    }

    /**
     * Retourne la liste des types de boost disponibles
     */
    private String getAvailableBoostTypes() {
        StringBuilder types = new StringBuilder();
        for (BoostType type : BoostType.values()) {
            if (!types.isEmpty()) types.append(", ");
            types.append(type.name().toLowerCase().replace("_", "-"));
        }
        return types.toString();
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("list", "help");
            if (sender.hasPermission("specialmine.admin.boost")) {
                subCommands = new ArrayList<>(subCommands);
                subCommands.add("admin");
            }
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            // Auto-complétion des types de boost
            List<String> boostTypes = new ArrayList<>();
            for (BoostType type : BoostType.values()) {
                boostTypes.add(type.name().toLowerCase().replace("_", "-"));
            }
            StringUtil.copyPartialMatches(args[1], boostTypes, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            // Suggestions de durée
            List<String> durations = Arrays.asList("30", "60", "120", "180", "360", "720");
            StringUtil.copyPartialMatches(args[2], durations, completions);
        } else if (args.length == 4 && args[0].equalsIgnoreCase("admin")) {
            // Suggestions de bonus
            List<String> bonuses = Arrays.asList("25", "50", "75", "100", "150", "200");
            StringUtil.copyPartialMatches(args[3], bonuses, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}