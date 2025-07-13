package fr.prisoncore.prisoncore.prisonTycoon.commands;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Material;
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
 * Commandes /sell all et /sell hand
 * Vend les items selon les prix configurés
 */
public class SellCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public SellCommand(PrisonTycoon plugin) {
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
            case "all" -> {
                sellAll(player);
                return true;
            }
            case "hand" -> {
                sellHand(player);
                return true;
            }
            default -> {
                sendHelpMessage(player);
                return true;
            }
        }
    }

    /**
     * Vend tout l'inventaire du joueur
     */
    private void sellAll(Player player) {
        long totalValue = 0;
        int totalItems = 0;
        Map<Material, Integer> soldItems = new HashMap<>();

        // Parcourt tout l'inventaire (sauf la pioche légendaire)
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (item == null || item.getType() == Material.AIR) continue;

            // Vérifie si c'est la pioche légendaire
            if (plugin.getPickaxeManager().isLegendaryPickaxe(item)) {
                continue; // Ne pas vendre la pioche légendaire
            }

            // Obtient le prix de l'item
            long sellPrice = plugin.getConfigManager().getSellPrice(item.getType());
            if (sellPrice <= 0) continue; // Item non vendable

            int amount = item.getAmount();
            long itemTotalValue = sellPrice * amount;

            totalValue += itemTotalValue;
            totalItems += amount;
            soldItems.merge(item.getType(), amount, Integer::sum);

            // Retire l'item de l'inventaire
            player.getInventory().setItem(i, null);
        }

        if (totalValue <= 0) {
            player.sendMessage("§c❌ Aucun item vendable trouvé dans votre inventaire!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Ajoute l'argent au joueur
        plugin.getEconomyManager().addCoins(player, totalValue);

        // Message de succès
        player.sendMessage("§a✅ §lVente réussie!");
        player.sendMessage("§7▸ Items vendus: §e" + NumberFormatter.format(totalItems));
        player.sendMessage("§7▸ Valeur totale: §6" + NumberFormatter.format(totalValue) + " coins");

        if (soldItems.size() <= 5) {
            player.sendMessage("§7▸ Détails:");
            for (Map.Entry<Material, Integer> entry : soldItems.entrySet()) {
                long itemPrice = plugin.getConfigManager().getSellPrice(entry.getKey());
                player.sendMessage("§7  • §e" + entry.getValue() + "x §7" +
                        formatMaterialName(entry.getKey()) + " §7(§6" +
                        NumberFormatter.format(itemPrice * entry.getValue()) + " coins§7)");
            }
        } else {
            player.sendMessage("§7▸ §e" + soldItems.size() + " types d'items différents vendus");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        plugin.getPluginLogger().info("§7" + player.getName() + " a vendu " + totalItems +
                " items pour " + NumberFormatter.format(totalValue) + " coins");
    }

    /**
     * Vend l'item en main
     */
    private void sellHand(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c❌ Vous n'avez rien en main!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Vérifie si c'est la pioche légendaire
        if (plugin.getPickaxeManager().isLegendaryPickaxe(handItem)) {
            player.sendMessage("§c❌ Vous ne pouvez pas vendre la pioche légendaire!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Obtient le prix de l'item
        long sellPrice = plugin.getConfigManager().getSellPrice(handItem.getType());
        if (sellPrice <= 0) {
            player.sendMessage("§c❌ §7" + formatMaterialName(handItem.getType()) + " §cn'est pas vendable!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int amount = handItem.getAmount();
        long totalValue = sellPrice * amount;

        // Ajoute l'argent au joueur
        plugin.getEconomyManager().addCoins(player, totalValue);

        // Retire l'item de la main
        player.getInventory().setItemInMainHand(null);

        // Message de succès
        player.sendMessage("§a✅ §lVente réussie!");
        player.sendMessage("§7▸ Item: §e" + amount + "x §7" + formatMaterialName(handItem.getType()));
        player.sendMessage("§7▸ Prix unitaire: §6" + NumberFormatter.format(sellPrice) + " coins");
        player.sendMessage("§7▸ Valeur totale: §6" + NumberFormatter.format(totalValue) + " coins");

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        plugin.getPluginLogger().info("§7" + player.getName() + " a vendu " + amount + "x " +
                handItem.getType().name() + " pour " + NumberFormatter.format(totalValue) + " coins");
    }

    /**
     * Formate le nom d'un matériau pour l'affichage
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    /**
     * Envoie le message d'aide
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§e💰 §lCommandes de vente:");
        player.sendMessage("§7/sell all §8- §7Vend tout l'inventaire");
        player.sendMessage("§7/sell hand §8- §7Vend l'item en main");
        player.sendMessage("§7");
        player.sendMessage("§c⚠️ §7La pioche légendaire ne peut pas être vendue");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("all", "hand");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}