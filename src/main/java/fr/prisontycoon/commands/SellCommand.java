package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Commandes /sell all et /sell hand
 * Vend les items selon les prix configurés
 * Refactorisé pour centraliser la logique de vente et corriger les bugs de bonus.
 */
public class SellCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public SellCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "all" -> sellAll(player);
            case "hand" -> sellHand(player);
            default -> sendHelpMessage(player);
        }
        return true;
    }

    /**
     * Vend tout l'inventaire du joueur ET le contenu de tous ses conteneurs.
     */
    private void sellAll(Player player) {
        long totalValue = 0;
        int totalItems = 0;
        Map<Material, Integer> soldItems = new HashMap<>();

        // Vend le contenu des conteneurs et récupère la valeur (on suppose que cette méthode ne donne pas l'argent)
        long containerValue = plugin.getContainerManager().sellAllContainerContents(player);
        totalValue += containerValue;
        // Note: Nous ne pouvons pas détailler les items des conteneurs ici sans modifier sellAllContainerContents.

        // Parcourt l'inventaire du joueur
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (item == null || item.getType() == Material.AIR ||
                    plugin.getPickaxeManager().isLegendaryPickaxe(item) ||
                    plugin.getContainerManager().isContainer(item)) {
                continue;
            }

            long sellPrice = plugin.getConfigManager().getSellPrice(item.getType());
            if (sellPrice <= 0) continue;

            int amount = item.getAmount();
            totalValue += sellPrice * amount;
            totalItems += amount;
            soldItems.merge(item.getType(), amount, Integer::sum);

            player.getInventory().setItem(i, null);
        }

        String logContext = "sell all (" + player.getName() + ")";
        processSale(player, totalValue, totalItems, soldItems, containerValue, logContext);
    }

    /**
     * Vend l'item en main ou le contenu du conteneur en main.
     */
    private void sellHand(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem.getType() == Material.AIR) {
            player.sendMessage("§c❌ Vous n'avez rien en main!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (plugin.getContainerManager().isContainer(handItem)) {
            sellContainerInHand(player, handItem);
            return;
        }

        if (plugin.getPickaxeManager().isLegendaryPickaxe(handItem)) {
            player.sendMessage("§c❌ Vous ne pouvez pas vendre la pioche légendaire!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long sellPrice = plugin.getConfigManager().getSellPrice(handItem.getType());
        if (sellPrice <= 0) {
            player.sendMessage("§c❌ Cet item ne peut pas être vendu!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int amount = handItem.getAmount();
        long totalValue = sellPrice * amount;

        Map<Material, Integer> soldItems = new HashMap<>();
        soldItems.put(handItem.getType(), amount);

        player.getInventory().setItemInMainHand(null);

        String logContext = "sell hand (" + player.getName() + ")";
        processSale(player, totalValue, amount, soldItems, 0, logContext);
    }

    /**
     * Logique de vente pour un conteneur tenu en main.
     */
    private void sellContainerInHand(Player player, ItemStack containerItem) {
        var containerData = plugin.getContainerManager().getContainerData(containerItem);
        if (containerData == null) {
            player.sendMessage("§c❌ Erreur: Impossible de lire les données du conteneur!");
            return;
        }

        if (containerData.isBroken() || !containerData.isSellEnabled()) {
            // Envoyer des messages d'erreur spécifiques
            if (containerData.isBroken()) {
                player.sendMessage("§c❌ Ce conteneur est cassé et ne peut pas être utilisé pour la vente!");
            } else {
                player.sendMessage("§c❌ La vente est désactivée pour ce conteneur!");
            }
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        long totalValue = 0;
        int totalItems = 0;
        Map<Material, Integer> soldItems = new HashMap<>();

        for (Map.Entry<ItemStack, Integer> entry : containerData.getContents().entrySet()) {
            long price = plugin.getConfigManager().getSellPrice(entry.getKey().getType());
            if (price > 0) {
                totalValue += price * entry.getValue();
                totalItems += entry.getValue();
                soldItems.merge(entry.getKey().getType(), entry.getValue(), Integer::sum);
            }
        }

        // D'abord, traiter la vente pour donner l'argent (CORRIGÉ : applique les bonus)
        String logContext = "conteneur T" + containerData.getTier() + " (" + player.getName() + ")";
        processSale(player, totalValue, totalItems, soldItems, 0, logContext);

        // Si la vente a eu lieu, gérer la logique du conteneur
        if (totalValue > 0) {
            containerData.clearContents();
            boolean stillFunctional = containerData.useDurability(1);

            // Gérer les messages de durabilité
            if (!stillFunctional) {
                player.sendMessage("§c💥 Le conteneur s'est cassé lors de la vente!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            } else {
                double durabilityPercent = containerData.getDurabilityPercentage();
                if (durabilityPercent <= 25) {
                    player.sendMessage("§7▸ Durabilité: " + (durabilityPercent <= 10 ? "§c" : "§e") +
                            String.format("%.1f%%", durabilityPercent));
                }
            }
            plugin.getContainerManager().updateContainerItem(containerItem, containerData);
        }
    }

    /**
     * Méthode centrale qui finalise la vente : applique les bonus, donne l'argent et envoie les messages.
     *
     * @param player         Le joueur qui vend.
     * @param totalValue     La valeur totale brute des items.
     * @param totalItems     Le nombre total d'items vendus.
     * @param soldItems      La map des items vendus pour les détails.
     * @param containerValue La valeur provenant de la vente groupée de conteneurs (pour /sell all).
     * @param logContext     Une chaîne décrivant le contexte de la vente pour les logs.
     */
    private void processSale(Player player, long totalValue, int totalItems, Map<Material, Integer> soldItems, long containerValue, String logContext) {
        if (totalValue <= 0) {
            player.sendMessage("§c❌ Aucun item vendable trouvé!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Applique les bonus globaux
        double finalPrice = plugin.getGlobalBonusManager().applySellBonus(player, totalValue);
        plugin.getEconomyManager().addCoins(player, (long) finalPrice);

        // Envoi des messages de succès
        player.sendMessage("§a✅ §lVente réussie!");
        player.sendMessage("§7▸ Items vendus: §e" + NumberFormatter.format(totalItems));
        player.sendMessage("§7▸ Valeur totale: §6" + NumberFormatter.format((long) finalPrice) + " coins");

        if (containerValue > 0) {
            player.sendMessage("§7▸ Dont conteneurs: §6" + NumberFormatter.format(plugin.getGlobalBonusManager().applySellBonus(player, containerValue)) + " coins");
        }

        if (!soldItems.isEmpty()) {
            if (soldItems.size() <= 5) {
                player.sendMessage("§7▸ Détails:");
                for (Map.Entry<Material, Integer> entry : soldItems.entrySet()) {
                    player.sendMessage("§7  • §e" + entry.getValue() + "x §7" + formatMaterialName(entry.getKey()));
                }
            } else {
                player.sendMessage("§7▸ §e" + soldItems.size() + " types d'items différents vendus.");
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        plugin.getPluginLogger().info("Vente: " + NumberFormatter.format((long) finalPrice) + " coins pour " +
                NumberFormatter.format(totalItems) + " items via " + logContext);
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§e💰 §lCommandes de vente:");
        player.sendMessage("§7/sell all §8- §7Vend tout votre inventaire et le contenu des conteneurs.");
        player.sendMessage("§7/sell hand §8- §7Vend l'item (ou le contenu du conteneur) en main.");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("all", "hand"), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}