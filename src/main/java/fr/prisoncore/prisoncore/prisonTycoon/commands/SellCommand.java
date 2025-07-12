package fr.prisoncore.prisoncore.prisonTycoon.commands;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.crystals.Crystal;
import fr.prisoncore.prisoncore.prisonTycoon.crystals.CrystalType;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Commandes /sell all et /sell hand
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
                sellAllItems(player);
                return true;
            }
            case "hand" -> {
                sellHandItem(player);
                return true;
            }
            default -> {
                sendHelpMessage(player);
                return true;
            }
        }
    }

    /**
     * Vend tous les items vendables de l'inventaire
     */
    private void sellAllItems(Player player) {
        Map<Material, Integer> itemsToSell = new HashMap<>();
        long totalValue = 0;
        int totalItems = 0;

        // Collecte tous les items vendables
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (item == null || item.getType() == Material.AIR) continue;

            // Ne vend pas la pioche légendaire
            if (plugin.getPickaxeManager().isLegendaryPickaxe(item)) continue;

            // Ne vend pas les cristaux
            if (Crystal.isCrystal(item)) continue;

            double basePrice = getSellPrice(item.getType());
            if (basePrice > 0) {
                int amount = item.getAmount();
                itemsToSell.merge(item.getType(), amount, Integer::sum);

                // Applique le bonus SellBoost
                double finalPrice = basePrice * getSellBoostMultiplier(player);
                totalValue += (long) (finalPrice * amount);
                totalItems += amount;

                player.getInventory().setItem(i, null);
            }
        }

        if (totalItems == 0) {
            player.sendMessage("§c❌ Aucun item vendable dans votre inventaire!");
            return;
        }

        // Ajoute les coins
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        playerData.addCoins(totalValue);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Message détaillé
        player.sendMessage("§a✅ §lVENTE RÉUSSIE!");
        player.sendMessage("§7Items vendus: §e" + NumberFormatter.format(totalItems));
        player.sendMessage("§7Types différents: §e" + itemsToSell.size());
        player.sendMessage("§7Valeur totale: §6" + NumberFormatter.format(totalValue) + " coins");

        double sellBoost = (getSellBoostMultiplier(player) - 1.0) * 100.0;
        if (sellBoost > 0) {
            player.sendMessage("§7Bonus SellBoost: §a+" + String.format("%.1f%%", sellBoost));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        plugin.getPluginLogger().info("Vente all pour " + player.getName() +
                ": " + totalItems + " items, " + totalValue + " coins");
    }

    /**
     * Vend l'item en main
     */
    private void sellHandItem(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c❌ Vous n'avez rien en main!");
            return;
        }

        // Vérifie les protections
        if (plugin.getPickaxeManager().isLegendaryPickaxe(handItem)) {
            player.sendMessage("§c❌ Vous ne pouvez pas vendre votre pioche légendaire!");
            return;
        }

        if (Crystal.isCrystal(handItem)) {
            player.sendMessage("§c❌ Vous ne pouvez pas vendre des cristaux!");
            return;
        }

        double basePrice = getSellPrice(handItem.getType());
        if (basePrice <= 0) {
            player.sendMessage("§c❌ Cet item ne peut pas être vendu!");
            return;
        }

        int amount = handItem.getAmount();
        double finalPrice = basePrice * getSellBoostMultiplier(player);
        long totalValue = (long) (finalPrice * amount);

        // Retire l'item et ajoute les coins
        player.getInventory().setItemInMainHand(null);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        playerData.addCoins(totalValue);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ §l" + amount + "x " + getItemDisplayName(handItem.getType()) + " §avendu!");
        player.sendMessage("§7Valeur: §6" + NumberFormatter.format(totalValue) + " coins");

        double sellBoost = (getSellBoostMultiplier(player) - 1.0) * 100.0;
        if (sellBoost > 0) {
            player.sendMessage("§7Bonus SellBoost: §a+" + String.format("%.1f%%", sellBoost));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        plugin.getPluginLogger().info("Vente hand pour " + player.getName() +
                ": " + amount + "x " + handItem.getType() + ", " + totalValue + " coins");
    }

    /**
     * Obtient le prix de vente d'un matériau
     */
    private double getSellPrice(Material material) {
        return plugin.getConfig().getDouble("sell-prices." + material.name().toLowerCase(), 0.0);
    }

    /**
     * Obtient le multiplicateur SellBoost du joueur
     */
    private double getSellBoostMultiplier(Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) return 1.0;

        List<Crystal> crystals = plugin.getCrystalManager().getAppliedCrystals(pickaxe);
        double bonus = plugin.getCrystalManager().getTotalCrystalBonus(crystals, CrystalType.SELL_BOOST);

        return 1.0 + (bonus / 100.0);
    }

    /**
     * Obtient le nom d'affichage d'un matériau
     */
    private String getItemDisplayName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }

        return result.toString();
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§6💰 §lCommandes de Vente:");
        player.sendMessage("§7/sell all §8- §7Vend tout l'inventaire");
        player.sendMessage("§7/sell hand §8- §7Vend l'item en main");
        player.sendMessage("");
        player.sendMessage("§e📋 §lINFORMATIONS");
        player.sendMessage("§7▸ Les cristaux §dSellBoost§7 augmentent les prix");
        player.sendMessage("§7▸ La pioche légendaire ne peut pas être vendue");
        player.sendMessage("§7▸ Les cristaux ne peuvent pas être vendus");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("all");
            completions.add("hand");
        }

        return completions;
    }
}