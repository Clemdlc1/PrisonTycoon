package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.*;

/**
 * Commande /conteneur pour obtenir des conteneurs
 */
public class ContainerCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public ContainerCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "1", "2", "3", "4", "5" -> {
                int tier = Integer.parseInt(subCommand);
                giveContainer(player, tier);
                return true;
            }
            case "info" -> {
                showContainerInfo(player);
                return true;
            }
            case "list" -> {
                listPlayerContainers(player);
                return true;
            }
            default -> {
                sendHelpMessage(player);
                return true;
            }
        }
    }

    /**
     * Donne un conteneur d'un tier spécifique au joueur
     */
    private void giveContainer(Player player, int tier) {
        if (tier < 1 || tier > 5) {
            player.sendMessage("§cTier invalide! Utilisez un tier entre 1 et 5.");
            return;
        }

        // Vérifie si l'inventaire a de la place
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c❌ Votre inventaire est plein!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Crée et donne le conteneur
        ItemStack container = plugin.getContainerManager().createContainer(tier);
        player.getInventory().addItem(container);

        // Messages de succès
        String tierName = getTierName(tier);
        player.sendMessage("§a✅ §lConteneur obtenu!");
        player.sendMessage("§7▸ Tier: §6" + tier + " §7(" + tierName + "§7)");
        player.sendMessage("§7▸ Type: §e" + getContainerDescription(tier));
        player.sendMessage("§7▸ Capacité: §a" + getCapacityForTier(tier) + " items");
        player.sendMessage("§7▸ Durabilité: §2" + getDurabilityForTier(tier) + " utilisations");
        player.sendMessage("");
        player.sendMessage("§e💡 §aShift + Clic droit §7sur le conteneur pour le configurer!");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        plugin.getPluginLogger().info("§7" + player.getName() + " a obtenu un conteneur tier " + tier);
    }

    /**
     * Affiche les informations générales sur les conteneurs
     */
    private void showContainerInfo(Player player) {
        player.sendMessage("§6📦 §l=== SYSTÈME DE CONTENEURS ===");
        player.sendMessage("");
        player.sendMessage("§e🎯 Fonctionnement:");
        player.sendMessage("§7┃ Les blocs minés vont directement dans vos conteneurs");
        player.sendMessage("§7┃ Système de filtres (whitelist) disponible");
        player.sendMessage("§7┃ Compatible avec §6/sell all");
        player.sendMessage("§7┃ Les conteneurs ont une durabilité limitée");
        player.sendMessage("§7┃ Shift + Clic droit pour configurer");
        player.sendMessage("");
        player.sendMessage("§e📊 Tiers disponibles:");

        for (int tier = 1; tier <= 5; tier++) {
            String tierName = getTierName(tier);
            player.sendMessage("§7┃ Tier §6" + tier + " §7(" + tierName + "§7): §a" +
                    getCapacityForTier(tier) + " items §7- §2" + getDurabilityForTier(tier) + " utilisations");
        }

        player.sendMessage("");
        player.sendMessage("§e⚠️ Important:");
        player.sendMessage("§7┃ Les conteneurs ne peuvent pas être posés au sol");
        player.sendMessage("§7┃ Ils perdent de la durabilité à chaque §6/sell all");
        player.sendMessage("§7┃ Si tous vos conteneurs sont pleins, les items vont dans l'inventaire");
        player.sendMessage("");
        player.sendMessage("§e📋 Commandes:");
        player.sendMessage("§7┃ §a/conteneur <1-5> §7- Obtenir un conteneur");
        player.sendMessage("§7┃ §a/conteneur list §7- Voir vos conteneurs");
        player.sendMessage("§7┃ §a/conteneur info §7- Voir cette aide");
    }

    /**
     * Liste les conteneurs du joueur
     */
    private void listPlayerContainers(Player player) {
        var containers = plugin.getContainerManager().getPlayerContainers(player);

        if (containers.isEmpty()) {
            player.sendMessage("§c📦 Vous n'avez aucun conteneur!");
            player.sendMessage("§7Utilisez §a/conteneur <tier> §7pour en obtenir un.");
            return;
        }

        player.sendMessage("§6📦 §lVos conteneurs §7(" + containers.size() + " total):");
        player.sendMessage("");

        Map<Integer, Integer> tierCounts = new HashMap<>();
        int totalItems = 0;
        int totalCapacity = 0;
        int brokenCount = 0;

        for (var container : containers) {
            tierCounts.merge(container.getTier(), 1, Integer::sum);
            totalItems += container.getTotalItems();
            totalCapacity += container.getMaxCapacity();
            if (container.isBroken()) brokenCount++;
        }

        // Résumé par tier
        player.sendMessage("§e📊 Résumé par tier:");
        for (Map.Entry<Integer, Integer> entry : tierCounts.entrySet()) {
            int tier = entry.getKey();
            int count = entry.getValue();
            String tierName = getTierName(tier);
            player.sendMessage("§7┃ Tier §6" + tier + " §7(" + tierName + "§7): §b" + count + " conteneur(s)");
        }

        player.sendMessage("");
        player.sendMessage("§e📈 Statistiques globales:");
        player.sendMessage("§7┃ Items stockés: §a" + formatNumber(totalItems) + "§7/§a" + formatNumber(totalCapacity));
        player.sendMessage("§7┃ Taux de remplissage: §d" + String.format("%.1f", (double) totalItems / totalCapacity * 100) + "%");
        player.sendMessage("§7┃ Conteneurs fonctionnels: §a" + (containers.size() - brokenCount) + "§7/§a" + containers.size());

        if (brokenCount > 0) {
            player.sendMessage("§7┃ Conteneurs cassés: §c" + brokenCount);
        }

        player.sendMessage("");
        player.sendMessage("§e💡 §aShift + Clic droit §7sur un conteneur pour le configurer!");
    }

    /**
     * Envoie le message d'aide
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§6📦 §lCommandes de conteneurs:");
        player.sendMessage("§7/conteneur <1-5> §8- §7Obtenir un conteneur du tier spécifié");
        player.sendMessage("§7/conteneur list §8- §7Voir vos conteneurs actuels");
        player.sendMessage("§7/conteneur info §8- §7Informations sur les conteneurs");
        player.sendMessage("");
        player.sendMessage("§e💡 §7Les conteneurs collectent automatiquement les blocs minés!");
    }

    /**
     * Nom du tier
     */
    private String getTierName(int tier) {
        return switch (tier) {
            case 1 -> "§7Basique";
            case 2 -> "§aStandard";
            case 3 -> "§bAvancé";
            case 4 -> "§5Épique";
            case 5 -> "§6Légendaire";
            default -> "§fInconnu";
        };
    }

    /**
     * Description du conteneur selon le tier
     */
    private String getContainerDescription(int tier) {
        return switch (tier) {
            case 1 -> "Conteneur de débutant";
            case 2 -> "Conteneur amélioré";
            case 3 -> "Conteneur avancé";
            case 4 -> "Conteneur épique";
            case 5 -> "Conteneur de maître";
            default -> "Conteneur mystérieux";
        };
    }

    /**
     * Capacité selon le tier
     */
    private int getCapacityForTier(int tier) {
        return switch (tier) {
            case 1 -> 6400;
            case 2 -> 9600;
            case 3 -> 16000;
            case 4 -> 32000;
            case 5 -> 64000;
            default -> 1000;
        };
    }

    /**
     * Durabilité selon le tier
     */
    private int getDurabilityForTier(int tier) {
        return switch (tier) {
            case 1 -> 50;   // 50 utilisations
            case 2 -> 100;  // 100 utilisations
            case 3 -> 200;  // 200 utilisations
            case 4 -> 400;  // 400 utilisations
            case 5 -> 800;  // 800 utilisations
            default -> 25;  // 25 pour tier invalide
        };
    }

    /**
     * Formate un nombre pour l'affichage
     */
    private String formatNumber(int number) {
        if (number >= 1000000) {
            return String.format("%.1fM", number / 1000000.0);
        } else if (number >= 1000) {
            return String.format("%.1fK", number / 1000.0);
        } else {
            return String.valueOf(number);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("1", "2", "3", "4", "5", "info", "list");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}