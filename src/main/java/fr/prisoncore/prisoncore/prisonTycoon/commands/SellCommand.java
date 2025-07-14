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
     * MODIFIÉ : Vend tout l'inventaire du joueur ET le contenu des conteneurs
     */
    private void sellAll(Player player) {
        long totalValue = 0;
        int totalItems = 0;
        Map<Material, Integer> soldItems = new HashMap<>();

        // NOUVEAU : Vend d'abord le contenu des conteneurs
        long containerValue = plugin.getContainerManager().sellAllContainerContents(player);
        totalValue += containerValue;

        // Parcourt tout l'inventaire (sauf la pioche légendaire et les conteneurs)
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (item == null || item.getType() == Material.AIR) continue;

            // Vérifie si c'est la pioche légendaire
            if (plugin.getPickaxeManager().isLegendaryPickaxe(item)) {
                continue; // Ne pas vendre la pioche légendaire
            }

            // NOUVEAU : Vérifie si c'est un conteneur
            if (plugin.getContainerManager().isContainer(item)) {
                continue; // Ne pas vendre les conteneurs eux-mêmes
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
            player.sendMessage("§c❌ Aucun item vendable trouvé dans votre inventaire ou vos conteneurs!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Ajoute l'argent au joueur
        plugin.getEconomyManager().addCoins(player, totalValue);

        // Message de succès amélioré
        player.sendMessage("§a✅ §lVente réussie!");
        player.sendMessage("§7▸ Items vendus: §e" + NumberFormatter.format(totalItems));
        player.sendMessage("§7▸ Valeur totale: §6" + NumberFormatter.format(totalValue) + " coins");

        // NOUVEAU : Affiche séparément la valeur des conteneurs si significative
        if (containerValue > 0) {
            player.sendMessage("§7▸ Dont conteneurs: §6" + NumberFormatter.format(containerValue) + " coins");
        }

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
                " items pour " + NumberFormatter.format(totalValue) + " coins (dont " +
                NumberFormatter.format(containerValue) + " des conteneurs)");
    }

    /**
     * MODIFIÉ : Vend l'item en main OU le contenu d'un conteneur en main
     */
    private void sellHand(Player player) {
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) {
            player.sendMessage("§c❌ Vous n'avez rien en main!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // NOUVEAU : Vérifie si c'est un conteneur
        if (plugin.getContainerManager().isContainer(handItem)) {
            sellContainerContent(player, handItem);
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
     * NOUVEAU : Vend le contenu d'un conteneur spécifique
     */
    private void sellContainerContent(Player player, ItemStack containerItem) {
        var containerData = plugin.getContainerManager().getContainerData(containerItem);
        if (containerData == null) {
            player.sendMessage("§c❌ Erreur: Impossible de lire les données du conteneur!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (containerData.isBroken()) {
            player.sendMessage("§c❌ Ce conteneur est cassé et ne peut pas être utilisé pour la vente!");
            player.sendMessage("§7Vous pouvez encore récupérer son contenu via la configuration.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        if (!containerData.isSellEnabled()) {
            player.sendMessage("§c❌ La vente est désactivée pour ce conteneur!");
            player.sendMessage("§7Utilisez §aShift + Clic droit §7pour activer la vente automatique.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Calcule la valeur du contenu
        long totalValue = 0;
        int totalItems = 0;
        Map<Material, Integer> soldItems = new HashMap<>();

        for (Map.Entry<ItemStack, Integer> entry : containerData.getContents().entrySet()) {
            long price = plugin.getConfigManager().getSellPrice(entry.getKey().getType()); // .getType() ajouté
            if (price > 0) {
                long itemValue = price * entry.getValue();
                totalValue += itemValue;
                totalItems += entry.getValue();
                soldItems.put(entry.getKey().getType(), entry.getValue());
            }
        }

        if (totalValue <= 0) {
            player.sendMessage("§c❌ Ce conteneur ne contient aucun item vendable!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Vide le conteneur
        containerData.clearContents();

        // Utilise la durabilité
        boolean stillFunctional = containerData.useDurability(1);

        // Ajoute l'argent au joueur
        plugin.getEconomyManager().addCoins(player, totalValue);

        // Messages de succès
        player.sendMessage("§a✅ §lContenu du conteneur vendu!");
        player.sendMessage("§7▸ Conteneur: §6Tier " + containerData.getTier());
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

        // État de la durabilité
        if (!stillFunctional) {
            player.sendMessage("§c💥 Le conteneur s'est cassé lors de la vente!");
            player.sendMessage("§7Le contenu était déjà vidé, mais il ne peut plus collecter d'items.");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        } else {
            double durabilityPercent = containerData.getDurabilityPercentage();
            if (durabilityPercent <= 25) {
                String durabilityColor = durabilityPercent <= 10 ? "§c" : "§e";
                player.sendMessage("§7▸ Durabilité: " + durabilityColor + containerData.getDurability() +
                        "§7/" + durabilityColor + containerData.getMaxDurability() +
                        " §7(" + String.format("%.1f", durabilityPercent) + "%)");

                if (durabilityPercent <= 10) {
                    player.sendMessage("§c⚠️ Attention: Le conteneur est presque cassé!");
                }
            }
        }

        // Met à jour l'item du conteneur
        plugin.getContainerManager().updateContainerItem(containerItem, containerData);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        plugin.getPluginLogger().info("§7" + player.getName() + " a vendu le contenu d'un conteneur tier " +
                containerData.getTier() + " pour " + NumberFormatter.format(totalValue) + " coins (" +
                totalItems + " items)");
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
        player.sendMessage("§7/sell all §8- §7Vend tout l'inventaire et les conteneurs");
        player.sendMessage("§7/sell hand §8- §7Vend l'item en main ou le contenu du conteneur en main");
        player.sendMessage("§7");
        player.sendMessage("§c⚠️ §7La pioche légendaire ne peut pas être vendue");
        player.sendMessage("§e💡 §7Les conteneurs perdent de la durabilité à chaque vente");
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