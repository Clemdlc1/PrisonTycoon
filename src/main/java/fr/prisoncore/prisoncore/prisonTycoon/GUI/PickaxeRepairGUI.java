package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.CustomEnchantment;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Menu de réparation de la pioche légendaire
 */
public class PickaxeRepairGUI {

    private final PrisonTycoon plugin;

    // Slots pour la barre de pourcentage de réparation (ligne du milieu)
    private static final int[] REPAIR_BAR_SLOTS = {11, 12, 13, 14, 15};
    private static final int PICKAXE_INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 18;
    private static final int CONFIRM_REPAIR_SLOT = 26;

    public PickaxeRepairGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu de réparation de la pioche
     */
    public void openRepairGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§c🔨 §lRéparation de Pioche §c🔨");

        // Remplissage décoratif
        fillBorders(gui);

        // Informations de la pioche
        gui.setItem(PICKAXE_INFO_SLOT, createPickaxeInfoItem(player));

        // Barre de pourcentage de réparation
        createRepairPercentageBar(gui, player, 50); // 50% par défaut

        // Bouton retour
        gui.setItem(BACK_BUTTON_SLOT, createBackButton());

        // Bouton de confirmation (désactivé par défaut)
        gui.setItem(CONFIRM_REPAIR_SLOT, createConfirmRepairButton(player, 50));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Gère les clics dans le menu de réparation
     */
    public void handleRepairMenuClick(Player player, int slot, ItemStack item) {
        if (slot == BACK_BUTTON_SLOT) {
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            return;
        }

        // Vérifier si c'est un clic sur la barre de pourcentage
        for (int i = 0; i < REPAIR_BAR_SLOTS.length; i++) {
            if (slot == REPAIR_BAR_SLOTS[i]) {
                int percentage = (i + 1) * 20; // 20%, 40%, 60%, 80%, 100%
                updateRepairSelection(player, percentage);
                return;
            }
        }

        // Bouton de confirmation
        if (slot == CONFIRM_REPAIR_SLOT) {
            executeRepair(player, item);
        }
    }

    /**
     * Met à jour la sélection de pourcentage de réparation
     */
    private void updateRepairSelection(Player player, int percentage) {
        Inventory gui = player.getOpenInventory().getTopInventory();

        // Met à jour la barre de pourcentage
        createRepairPercentageBar(gui, player, percentage);

        // Met à jour le bouton de confirmation
        gui.setItem(CONFIRM_REPAIR_SLOT, createConfirmRepairButton(player, percentage));

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
    }

    /**
     * Exécute la réparation de la pioche
     */
    private void executeRepair(Player player, ItemStack confirmItem) {
        if (confirmItem == null || !confirmItem.hasItemMeta()) return;

        ItemMeta meta = confirmItem.getItemMeta();
        if (!meta.hasLore()) return;

        // Extrait le pourcentage depuis le lore du bouton de confirmation
        List<String> lore = meta.getLore();
        int percentage = 0;
        long cost = 0;

        for (String line : lore) {
            if (line.contains("Réparation:")) {
                String percentStr = line.replaceAll("[^0-9]", "");
                if (!percentStr.isEmpty()) {
                    percentage = Integer.parseInt(percentStr);
                }
            }
            if (line.contains("Coût:")) {
                String costStr = line.replaceAll("[^0-9]", "");
                if (!costStr.isEmpty()) {
                    cost = Long.parseLong(costStr);
                }
            }
        }

        if (percentage == 0 || cost == 0) {
            player.sendMessage("§c❌ Erreur lors de la lecture des données de réparation!");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier si le joueur a assez de tokens
        if (playerData.getTokens() < cost) {
            player.sendMessage("§c❌ Vous n'avez pas assez de tokens pour cette réparation!");
            player.sendMessage("§7Coût: §6" + NumberFormatter.format(cost) + " tokens");
            player.sendMessage("§7Disponible: §c" + NumberFormatter.format(playerData.getTokens()) + " tokens");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Trouver la pioche du joueur
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§c❌ Pioche légendaire introuvable!");
            return;
        }

        // Calculer la nouvelle durabilité
        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();

        // Prendre en compte l'enchantement durabilité
        int durabilityLevel = playerData.getEnchantmentLevel("durability");
        double durabilityBonus = durabilityLevel * 10.0;
        int maxDurabilityWithBonus = (int) (maxDurability * (1.0 + durabilityBonus / 100.0));

        // Calculer la réparation
        int currentDamage = currentDurability;
        int repairAmount = (int) (maxDurabilityWithBonus * (percentage / 100.0));
        int newDurability = Math.max(0, currentDamage - repairAmount);

        // Appliquer la réparation
        pickaxe.setDurability((short) newDurability);

        // Déduire les tokens
        playerData.removeTokens(cost);

        // Mettre à jour la pioche
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        // Messages de succès
        double repairedPercent = ((double) repairAmount / maxDurabilityWithBonus) * 100;
        player.sendMessage("§a✅ Pioche réparée avec succès!");
        player.sendMessage("§7Réparation: §a+" + String.format("%.1f%%", repairedPercent) + " de durabilité");
        player.sendMessage("§7Coût: §6" + NumberFormatter.format(cost) + " tokens");
        player.sendMessage("§7Tokens restants: §e" + NumberFormatter.format(playerData.getTokens()));

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.closeInventory();

        plugin.getPluginLogger().info("§a" + player.getName() + " a réparé sa pioche de " +
                String.format("%.1f%%", repairedPercent) + " pour " + NumberFormatter.format(cost) + " tokens");
    }

    /**
     * Crée l'item d'information de la pioche
     */
    private ItemStack createPickaxeInfoItem(Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6⛏️ §lÉTAT DE VOTRE PIOCHE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (pickaxe != null) {
            short currentDurability = pickaxe.getDurability();
            short maxDurability = pickaxe.getType().getMaxDurability();

            // Prendre en compte l'enchantement durabilité
            int durabilityLevel = playerData.getEnchantmentLevel("durability");
            double durabilityBonus = durabilityLevel * 10.0;
            int maxDurabilityWithBonus = (int) (maxDurability * (1.0 + durabilityBonus / 100.0));

            double currentHealthPercent = ((double)(maxDurabilityWithBonus - currentDurability) / maxDurabilityWithBonus) * 100;

            lore.add("§e📊 §lDURABILITÉ ACTUELLE");
            lore.add("§7│ §eÉtat: " + getDurabilityColor(currentHealthPercent) + String.format("%.1f%%", currentHealthPercent));
            lore.add("§7│ §eDurabilité: §6" + (maxDurabilityWithBonus - currentDurability) + "§7/§6" + maxDurabilityWithBonus);

            if (durabilityLevel > 0) {
                lore.add("§7│ §eBonus Solidité: §a+" + String.format("%.0f%%", durabilityBonus) + " §7(Niv." + durabilityLevel + ")");
            }

            lore.add("§7└ " + getDurabilityStatus(currentHealthPercent));
            lore.add("");

            // Valeur totale investie
            long totalInvested = calculateTotalInvestedTokens(playerData);
            lore.add("§6💰 §lVALEUR TOTALE INVESTIE");
            lore.add("§7│ §6Tokens investis: §e" + NumberFormatter.format(totalInvested));
            lore.add("§7└ §7Base de calcul pour les coûts de réparation");

        } else {
            lore.add("§c❌ Pioche légendaire introuvable!");
            lore.add("§7Vous devez avoir votre pioche légendaire");
            lore.add("§7dans l'inventaire pour la réparer.");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée la barre de sélection de pourcentage de réparation
     */
    private void createRepairPercentageBar(Inventory gui, Player player, int selectedPercentage) {
        for (int i = 0; i < REPAIR_BAR_SLOTS.length; i++) {
            int percentage = (i + 1) * 20; // 20%, 40%, 60%, 80%, 100%
            boolean isSelected = percentage == selectedPercentage;

            Material material;
            String color;
            if (isSelected) {
                material = Material.LIME_STAINED_GLASS;
                color = "§a";
            } else if (percentage <= 40) {
                material = Material.YELLOW_STAINED_GLASS;
                color = "§e";
            } else if (percentage <= 80) {
                material = Material.ORANGE_STAINED_GLASS;
                color = "§6";
            } else {
                material = Material.RED_STAINED_GLASS;
                color = "§c";
            }

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            meta.setDisplayName(color + "§l" + percentage + "% §7de réparation");

            List<String> lore = new ArrayList<>();
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            if (isSelected) {
                lore.add("§a✅ §lSÉLECTIONNÉ");
                lore.add("§7Cette option est actuellement sélectionnée");
            } else {
                lore.add("§7Réparer §e" + percentage + "% §7de la durabilité");
                lore.add("§7de votre pioche légendaire");
            }

            // Calcul du coût pour ce pourcentage
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            long totalInvested = calculateTotalInvestedTokens(playerData);
            long cost = calculateRepairCost(totalInvested, percentage);

            lore.add("§7");
            lore.add("§6💰 §lCOÛT DE RÉPARATION");
            lore.add("§7│ §6Coût: §e" + NumberFormatter.format(cost) + " tokens");
            lore.add("§7│ §7Base: §6" + NumberFormatter.format(totalInvested) + " tokens investis");
            lore.add("§7└ §7Pourcentage: §e" + getRepairCostPercentage(percentage) + "%");

            if (!isSelected) {
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§e✨ Cliquez pour sélectionner!");
            }

            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            meta.setLore(lore);
            item.setItemMeta(meta);

            gui.setItem(REPAIR_BAR_SLOTS[i], item);
        }
    }

    /**
     * Crée le bouton de confirmation de réparation
     */
    private ItemStack createConfirmRepairButton(Player player, int percentage) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long totalInvested = calculateTotalInvestedTokens(playerData);
        long cost = calculateRepairCost(totalInvested, percentage);
        boolean canAfford = playerData.getTokens() >= cost;

        Material material = canAfford ? Material.EMERALD : Material.BARRIER;
        String color = canAfford ? "§a" : "§c";
        String icon = canAfford ? "✅" : "❌";

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color + icon + " §lCONFIRMER LA RÉPARATION");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        lore.add("§e🔨 §lRÉSUMÉ DE LA RÉPARATION");
        lore.add("§7│ §eRéparation: §a" + percentage + "%");
        lore.add("§7│ §6Coût: §e" + NumberFormatter.format(cost) + " tokens");
        lore.add("§7│ §eDisponible: " + (canAfford ? "§a" : "§c") + NumberFormatter.format(playerData.getTokens()) + " tokens");

        if (canAfford) {
            long remaining = playerData.getTokens() - cost;
            lore.add("§7│ §eRestant: §a" + NumberFormatter.format(remaining) + " tokens");
            lore.add("§7└");
            lore.add("");
            lore.add("§a✅ §lRÉPARATION POSSIBLE");
            lore.add("§7Votre pioche sera réparée de §a" + percentage + "%");
            lore.add("§7et vous conserverez §a" + NumberFormatter.format(remaining) + " tokens");
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§a✨ Cliquez pour confirmer!");
        } else {
            long missing = cost - playerData.getTokens();
            lore.add("§7│ §cManquant: §4" + NumberFormatter.format(missing) + " tokens");
            lore.add("§7└");
            lore.add("");
            lore.add("§c❌ §lTOKENS INSUFFISANTS");
            lore.add("§7Vous devez obtenir §c" + NumberFormatter.format(missing));
            lore.add("§7tokens supplémentaires pour cette réparation.");
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§7Continuez à miner pour plus de tokens!");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Calcule le coût total de tokens investis dans tous les enchantements
     */
    private long calculateTotalInvestedTokens(PlayerData playerData) {
        long totalCost = 0;

        Map<String, Integer> enchantments = playerData.getEnchantmentLevels();

        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            String enchantName = entry.getKey();
            int currentLevel = entry.getValue();

            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantName);
            if (enchantment != null) {
                // Calcule le coût total pour atteindre ce niveau
                for (int level = 1; level <= currentLevel; level++) {
                    totalCost += enchantment.getUpgradeCost(level);
                }
            }
        }

        return totalCost;
    }

    /**
     * Calcule le coût de réparation basé sur un pourcentage de la valeur investie
     */
    private long calculateRepairCost(long totalInvested, int repairPercentage) {
        // Le coût de réparation est un pourcentage de la valeur totale investie
        double costPercentage = getRepairCostPercentage(repairPercentage) / 100.0;
        return (long) (totalInvested * costPercentage);
    }

    /**
     * Retourne le pourcentage du coût de réparation selon le pourcentage de réparation
     */
    private double getRepairCostPercentage(int repairPercentage) {
        // Coût plus élevé pour les réparations importantes
        return switch (repairPercentage) {
            case 20 -> 1.0;   // 1% de la valeur investie
            case 40 -> 2.5;   // 2.5% de la valeur investie
            case 60 -> 4.5;   // 4.5% de la valeur investie
            case 80 -> 7.0;   // 7% de la valeur investie
            case 100 -> 10.0; // 10% de la valeur investie
            default -> 1.0;
        };
    }

    /**
     * Retourne la couleur selon le pourcentage de durabilité
     */
    private String getDurabilityColor(double healthPercent) {
        if (healthPercent >= 75) return "§a"; // Vert
        if (healthPercent >= 50) return "§e"; // Jaune
        if (healthPercent >= 25) return "§6"; // Orange
        return "§c"; // Rouge
    }

    /**
     * Retourne le statut de la durabilité
     */
    private String getDurabilityStatus(double healthPercent) {
        if (healthPercent >= 75) return "§a✓ Pioche en excellent état";
        if (healthPercent >= 50) return "§e⚠️ Pioche en bon état";
        if (healthPercent >= 25) return "§6⚠️ Pioche usée, réparation recommandée";
        return "§c⚠️ Pioche très endommagée, réparation urgente!";
    }

    /**
     * Crée le bouton de retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7⬅ §lRetour au Menu Principal");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Retour au menu d'enchantements");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✨ Cliquez pour revenir!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Remplit les bordures avec des items décoratifs
     */
    private void fillBorders(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
        }

        // Remplir les bordures (slots 0-8, 9, 17, 18-26)
        int[] borderSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 16, 17, 19, 20, 21, 22, 23, 24, 25};

        for (int slot : borderSlots) {
            gui.setItem(slot, filler);
        }
    }
}