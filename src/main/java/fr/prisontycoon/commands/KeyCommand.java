package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commande pour donner des clés de crates aux joueurs
 * Usage: /key <joueur> <type> [quantité]
 */
public class KeyCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public KeyCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        // Vérification des permissions
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length < 2) {
            sendHelpMessage(sender);
            return true;
        }

        // Parse les arguments
        String playerName = args[0];
        String keyType = args[1];
        int amount = 1;

        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0 || amount > 64) {
                    sender.sendMessage("§c❌ Quantité invalide! (1-64)");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c❌ Quantité invalide! Utilisez un nombre.");
                return true;
            }
        }

        // Trouve le joueur
        Player target = Bukkit.getPlayer(playerName);
        if (target == null) {
            sender.sendMessage("§c❌ Joueur non trouvé: " + playerName);
            return true;
        }

        // Vérifie l'espace dans l'inventaire
        if (target.getInventory().firstEmpty() == -1) {
            sender.sendMessage("§c❌ L'inventaire de " + target.getName() + " est plein!");
            return true;
        }

        // Crée et donne la clé
        ItemStack key = createKey(keyType, amount);
        if (key == null) {
            sender.sendMessage("§c❌ Type de clé invalide: " + keyType);
            sender.sendMessage("§7Types disponibles: Vote, Commune, Peu Commune, Rare, Légendaire, Cristal");
            return true;
        }

        target.getInventory().addItem(key);

        // Messages de confirmation
        String keyColor = getKeyColor(keyType);
        target.sendMessage("§a📥 Vous avez reçu " + amount + "x " + keyColor + "Clé " + keyType + "§a!");
        target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        sender.sendMessage("§a✅ " + amount + "x " + keyColor + "Clé " + keyType + " §adonnée à " + target.getName());

        // Log administratif
        plugin.getPluginLogger().info("§7" + sender.getName() + " a donné " + amount + "x Clé " + keyType + " à " + target.getName());

        return true;
    }

    /**
     * Crée une clé du type spécifié
     */
    private ItemStack createKey(String keyType, int amount) {
        String keyColor = getKeyColor(keyType);
        if (keyColor == null) return null;

        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = key.getItemMeta();
        meta.setDisplayName(keyColor + "Clé " + keyType);
        meta.setLore(Arrays.asList(
                "§7Clé de coffre " + keyColor + keyType,
                "§7Utilise cette clé pour ouvrir des coffres!"
        ));
        key.setItemMeta(meta);
        return key;
    }

    /**
     * Obtient la couleur d'une clé selon son type
     */
    private String getKeyColor(String keyType) {
        return switch (keyType.toLowerCase()) {
            case "vote" -> "§f";
            case "commune" -> "§f";
            case "peu commune" -> "§9";
            case "rare" -> "§5";
            case "légendaire" -> "§6";
            case "cristal" -> "§d";
            default -> null;
        };
    }

    /**
     * Affiche le message d'aide
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§6=== Commande Key ===");
        sender.sendMessage("§7Usage: §e/key <joueur> <type> [quantité]");
        sender.sendMessage("§7Types: §eVote, Commune, Peu Commune, Rare, Légendaire, Cristal");
        sender.sendMessage("§7Quantité: §e1-64 (défaut: 1)");
        sender.sendMessage("§7Permission: §especialmine.admin");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Complétion des noms de joueurs
            String partialName = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partialName)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 2) {
            // Complétion des types de clés
            String partialType = args[1].toLowerCase();
            List<String> keyTypes = Arrays.asList("Vote", "Commune", "Peu Commune", "Rare", "Légendaire", "Cristal");
            for (String type : keyTypes) {
                if (type.toLowerCase().startsWith(partialType)) {
                    completions.add(type);
                }
            }
        } else if (args.length == 3) {
            // Complétion des quantités
            completions.addAll(Arrays.asList("1", "5", "10", "16", "32", "64"));
        }

        return completions;
    }
}
