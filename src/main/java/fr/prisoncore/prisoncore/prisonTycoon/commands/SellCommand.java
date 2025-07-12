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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Commande de vente des items
 */
public class SellCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public SellCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "all" -> sellAllInventory(player);
            case "hand" -> sellHandItem(player);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * Vend tout l'inventaire (sauf items protégés)
     */
    private void sellAllInventory(Player player) {
        Map<Material, Integer> itemCounts = new HashMap<>();
        long totalValue = 0;
        int totalItems = 0;

        // Parcourt l'inventaire (slots 9-35 pour éviter la hotbar avec la pioche)
        for (int i = 9; i < 36; i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (item == null || item.getType() == Material.AIR) continue;
            if (isProtectedItem(item)) continue;

            double sellPrice = getSellPrice(item.getType());
            if (sellPrice <= 0) continue;

            int amount = item.getAmount();
            itemCounts.merge(item.getType(), amount, Integer::sum);
            totalItems += amount;

            // Retire l'item de l'inventaire
            player.getInventory().setItem(i, null);
        }

        if (totalItems == 0) {
            player.sendMessage("§c❌ Aucun item vendable dans votre inventaire!");
            return;
        }

        // Calcule la valeur totale avec bonus SellBoost
        double sellBoostMultiplier = getSellBoostMultiplier(player);
        for (Map.Entry<Material, Integer> entry : itemCounts.entrySet()) {
            double baseValue = getSellPrice(entry.getKey()) * entry.getValue();
            totalValue += Math.round(baseValue * sellBoostMultiplier);
        }

        // Donne les coins au joueur
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        playerData.addCoins(totalValue);

        // Messages
        player.sendMessage("§a✅ §lVENTE RÉUSSIE");
        player.sendMessage("§7Items vendus: §e" + NumberFormatter.format(totalItems));
        player.sendMessage("§7Valeur totale: §6" + NumberFormatter.format(totalValue) + " coins");

        double sellBoost = (sellBoostMultiplier - 1.0) * 100.0;
        if (sellBoost > 0) {
            player.sendMessage("§7Bonus SellBoost: §a+" + String.format("%.1f%%", sellBoost));
        }

        // Détail des items vendus (top 5)
        List<Map.Entry<Material, Integer>> sortedItems = new ArrayList<>(itemCounts.entrySet());
        sortedItems.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        player.sendMessage("§7Détail (top 5):");
        for (int i = 0; i < Math.min(5, sortedItems.size()); i++) {
            Map.Entry<Material, Integer> entry = sortedItems.get(i);
            player.sendMessage("§7▸ " + getItemDisplayName(entry.getKey()) +
                    ": §e" + NumberFormatter.format(entry.getValue()));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        plugin.getPluginLogger().info("Vente all pour " + player.getName() +
                ": " + totalItems + " items, " + totalValue + " coins");
    }

    /**
     * Vend l'item en main
     */
    private void sellHandItem(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c❌ Vous devez tenir un item en main!");
            return;
        }

        if (isProtectedItem(handItem)) {
            player.sendMessage("§c❌ Cet item ne peut pas être vendu!");
            return;
        }

        double sellPrice = getSellPrice(handItem.getType());
        if (sellPrice <= 0) {
            player.sendMessage("§c❌ Cet item n'a pas de valeur de vente!");
            return;
        }

        int amount = handItem.getAmount();
        double baseValue = sellPrice * amount;
        long totalValue = Math.round(baseValue * getSellBoostMultiplier(player));

        // Donne les coins au joueur
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        playerData.addCoins(totalValue);

        // Retire l'item de la main
        player.getInventory().setItemInMainHand(null);

        // Messages
        player.sendMessage("§a✅ §lVENTE RÉUSSIE");
        player.sendMessage("§7Item: §e" + getItemDisplayName(handItem.getType()) + " x" + amount);
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
     * Vérifie si un item est protégé de la vente
     */
    private boolean isProtectedItem(ItemStack item) {
        // Pioche légendaire
        if (plugin.getPickaxeManager().isLegendaryPickaxe(item)) {
            return true;
        }

        // Cristaux
        if (Crystal.isCrystal(item)) {
            return true;
        }

        // Clés (si elles ont un tag spécial)
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            if (displayName.contains("Clé") || displayName.contains("Key")) {
                return true;
            }
        }

        return false;
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
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§7/sell all §8- §7Vend tout l'inventaire");
        player.sendMessage("§7/sell hand §8- §7Vend l'item en main");
        player.sendMessage("");
        player.sendMessage("§e📋 §lINFORMATIONS");
        player.sendMessage("§7▸ Les cristaux §dSellBoost§7 augmentent les prix");
        player.sendMessage("§7▸ La pioche légendaire ne peut pas être vendue");
        player.sendMessage("§7▸ Les cristaux ne peuvent pas être vendus");
        player.sendMessage("§7▸ Les clés ne peuvent pas être vendues");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
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