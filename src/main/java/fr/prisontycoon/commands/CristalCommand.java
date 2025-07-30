package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.cristaux.CristalType;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Commande pour gérer les cristaux
 * Usage: /cristal <niveau> [joueur] [type]
 */
public class CristalCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public CristalCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("specialmine.admin") && !sender.isOp()) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        // NOUVEAU: Gestion de la sous-commande "reg"
        if (args[0].equalsIgnoreCase("reg")) {
            return handleRegenerateCommand(sender, args);
        }

        int niveau;
        try {
            niveau = Integer.parseInt(args[0]);
            if (niveau < 1 || niveau > 20) {
                sender.sendMessage("§cNiveau de cristal invalide! Doit être entre 1 et 20.");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cNiveau de cristal invalide! Doit être un nombre.");
            return true;
        }

        // Détermine le joueur cible
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cJoueur introuvable: " + args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cVous devez spécifier un joueur depuis la console!");
                return true;
            }
            target = (Player) sender;
        }

        // Détermine le type (si spécifié)
        CristalType type = null;
        if (args.length >= 3) {
            try {
                type = CristalType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage("§cType de cristal invalide: " + args[2]);
                sender.sendMessage("§7Types disponibles: " + Arrays.toString(CristalType.values()));
                return true;
            }
        }

        // Donne le cristal
        boolean success;
        if (type != null) {
            // Cristal avec type spécifique (révélé)
            success = plugin.getCristalManager().giveCristalToPlayer(target, niveau, type);
            if (success) {
                sender.sendMessage("§a✓ Cristal §d" + type.getDisplayName() + " §7(Niveau " + niveau +
                                   ") §adonné à §e" + target.getName() + "§a!");
            }
        } else {
            // Cristal vierge
            success = plugin.getCristalManager().giveCristalToPlayer(target, niveau);
            if (success) {
                sender.sendMessage("§a✓ Cristal vierge §7(Niveau " + niveau +
                                   ") §adonné à §e" + target.getName() + "§a!");
            }
        }

        if (!success) {
            sender.sendMessage("§cErreur lors de la création du cristal!");
        }

        return true;
    }

    /**
     * NOUVEAU: Gère la commande /cristal reg pour régénérer un cristal non vierge en vierge
     */
    private boolean handleRegenerateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Vérifier que le joueur a un cristal en main
        if (itemInHand == null || itemInHand.getType() == Material.AIR) {
            player.sendMessage("§cVous devez avoir un cristal en main!");
            return true;
        }

        if (!plugin.getCristalManager().isCristal(itemInHand)) {
            player.sendMessage("§cL'objet en main n'est pas un cristal!");
            return true;
        }

        Cristal cristal = plugin.getCristalManager().getCristalFromItem(itemInHand);
        if (cristal == null) {
            player.sendMessage("§cErreur: Cristal invalide!");
            return true;
        }

        if (cristal.isVierge()) {
            player.sendMessage("§cCe cristal est déjà vierge!");
            return true;
        }

        // Calculer le coût de régénération (basé sur le niveau du cristal)
        long regenerationCost = calculateRegenerationCost(cristal.getNiveau());

        // Vérifier que le joueur a assez de coins
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getCoins() < regenerationCost) {
            player.sendMessage("§c❌ Coins insuffisants!");
            player.sendMessage("§7Coût: §e" + NumberFormatter.format(regenerationCost) + " coins");
            player.sendMessage("§7Vous avez: §e" + NumberFormatter.format(playerData.getCoins()) + " coins");
            return true;
        }

        // MODIFIÉ: Demander confirmation avec des boutons cliquables au lieu de texte
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§d✨ §lRégénération de Cristal §d✨");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§7Cristal actuel: §d" + cristal.getType().getDisplayName() + " §7(Niveau " + cristal.getNiveau() + ")");
            player.sendMessage("§7Après régénération: §dCristal Vierge §7(Niveau " + cristal.getNiveau() + ")");
            player.sendMessage("");
            player.sendMessage("§6💰 Coût: §e" + NumberFormatter.format(regenerationCost) + " coins");
            player.sendMessage("§7Vos coins: §e" + NumberFormatter.format(playerData.getCoins()) + " coins");
            player.sendMessage("");

            // NOUVEAU: Boutons cliquables au lieu de texte
            TextComponent confirmButton = new TextComponent("§a[✓ CONFIRMER]");
            confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cristal reg confirm"));
            confirmButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aCliquez pour confirmer la régénération")));

            TextComponent separator = new TextComponent("    ");

            TextComponent cancelButton = new TextComponent("§c[✗ ANNULER]");
            cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/menu"));
            cancelButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§cCliquez pour annuler l'opération")));

            player.sendMessage("§eChoisissez une option:");
            player.spigot().sendMessage(confirmButton, separator, cancelButton);
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return true;
        }

        // Effectuer la régénération
        playerData.removeCoins(regenerationCost);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Créer le nouveau cristal vierge avec le même niveau
        Cristal newCristal = plugin.getCristalManager().createCristalVierge(cristal.getNiveau());
        ItemStack newCristalItem = newCristal.toItemStack(
                plugin.getCristalManager().getCristalUuidKey(),
                plugin.getCristalManager().getCristalLevelKey(),
                plugin.getCristalManager().getCristalTypeKey(),
                plugin.getCristalManager().getCristalViergeKey()
        );

        // Remplacer l'item en main
        player.getInventory().setItemInMainHand(newCristalItem);

        // Messages de succès
        player.sendMessage("§d✨ Cristal régénéré avec succès!");
        player.sendMessage("§7Ancien: §d" + cristal.getType().getDisplayName() + " §7→ §dCristal Vierge");
        player.sendMessage("§7Coût: §c-" + NumberFormatter.format(regenerationCost) + " coins");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

        plugin.getPluginLogger().info("§7" + player.getName() + " a régénéré un cristal " +
                                      cristal.getType().getDisplayName() + " niveau " + cristal.getNiveau() +
                                      " pour " + regenerationCost + " coins");

        return true;
    }

    /**
     * NOUVEAU: Calcule le coût de régénération d'un cristal en fonction de son niveau
     */
    private long calculateRegenerationCost(int niveau) {
        // Formule: coût = niveau² × 10000 (coût croissant exponentiellement)
        // Niveau 1: 10,000 coins
        // Niveau 5: 250,000 coins
        // Niveau 10: 1,000,000 coins
        // Niveau 20: 4,000,000 coins
        return niveau * niveau * 10000L;
    }


    /**
     * Affiche l'usage de la commande (modifié pour inclure reg)
     */
    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§d✨ §lCommande Cristal §d✨");
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("§e/cristal <niveau> §8- §7Donne un cristal vierge");
        sender.sendMessage("§e/cristal <niveau> <joueur> §8- §7Donne à un joueur");
        sender.sendMessage("§e/cristal <niveau> <joueur> <type> §8- §7Cristal révélé");
        sender.sendMessage("§6/cristal reg §8- §7Régénère le cristal en main en vierge");
        sender.sendMessage("");
        sender.sendMessage("§7Niveaux: §f1-20");
        sender.sendMessage("§7Types: §f" + Arrays.toString(CristalType.values()));
        sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (!sender.hasPermission("specialmine.admin") && !sender.isOp()) {
            return suggestions;
        }

        if (args.length == 1) {
            // Suggestions pour la première argument (niveau ou "reg")
            String input = args[0].toLowerCase();

            // Ajouter "reg" aux suggestions
            if ("reg".startsWith(input)) {
                suggestions.add("reg");
            }

            // Ajouter les niveaux
            for (int i = 1; i <= 20; i++) {
                String level = String.valueOf(i);
                if (level.startsWith(args[0])) {
                    suggestions.add(level);
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reg")) {
                // Pour /cristal reg, suggérer "confirm"
                if ("confirm".startsWith(args[1].toLowerCase())) {
                    suggestions.add("confirm");
                }
            } else {
                // Pour les autres commandes, suggérer des joueurs
                String input = args[1].toLowerCase();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(input)) {
                        suggestions.add(player.getName());
                    }
                }
            }
        } else if (args.length == 3 && !args[0].equalsIgnoreCase("reg")) {
            // Suggestions de types de cristaux (seulement si ce n'est pas "reg")
            String input = args[2].toLowerCase();
            for (CristalType type : CristalType.values()) {
                if (type.name().toLowerCase().startsWith(input)) {
                    suggestions.add(type.name().toLowerCase());
                }
            }
        }

        return suggestions;
    }
}