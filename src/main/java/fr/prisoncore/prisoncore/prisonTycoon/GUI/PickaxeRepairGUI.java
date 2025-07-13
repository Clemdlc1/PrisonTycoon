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
 * CORRIGÉ : Menu de réparation avec nouveaux coûts et sans confirmation
 */
public class PickaxeRepairGUI {

    private final PrisonTycoon plugin;

    // Slots pour la barre de pourcentage de réparation (ligne du milieu)
    private static final int[] REPAIR_BAR_SLOTS = {11, 12, 13, 14, 15};
    private static final int PICKAXE_INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 18;
    // SUPPRIMÉ : Plus de bouton de confirmation

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

        // Barre de pourcentage de réparation (sans sélection, clics directs)
        createRepairPercentageBar(gui, player);

        // Bouton retour
        gui.setItem(BACK_BUTTON_SLOT, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * CORRIGÉ : Gère les clics avec exécution directe
     */
    public void handleRepairMenuClick(Player player, int slot, ItemStack item) {
        if (slot == BACK_BUTTON_SLOT) {
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            return;
        }

        // CORRIGÉ : Clics directs sur la barre de pourcentage = réparation immédiate
        for (int i = 0; i < REPAIR_BAR_SLOTS.length; i++) {
            if (slot == REPAIR_BAR_SLOTS[i]) {
                int percentage = (i + 1) * 20; // 20%, 40%, 60%, 80%, 100%
                executeRepairDirect(player, percentage);
                return;
            }
        }
    }

    /**
     * CORRIGÉ : Barre sans sélection, boutons directs
     */
    private void createRepairPercentageBar(Inventory gui, Player player) {
        for (int i = 0; i < REPAIR_BAR_SLOTS.length; i++) {
            int percentage = (i + 1) * 20; // 20%, 40%, 60%, 80%, 100%

            Material material;
            String color;
            if (percentage <= 40) {
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
            lore.add("§7Réparer §e" + percentage + "% §7de la durabilité");
            lore.add("§7de votre pioche légendaire");

            // CORRIGÉ : Calcul du coût avec nouveau système (0,01% pour 100%)
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            long totalInvested = calculateTotalInvestedTokens(playerData);
            long cost = calculateNewRepairCost(totalInvested, percentage);

            lore.add("§7");
            lore.add("§6💰 §lCOÛT DE RÉPARATION");
            lore.add("§7│ §6Coût: §e" + NumberFormatter.format(cost) + " tokens");
            lore.add("§7│ §7Base: §6" + NumberFormatter.format(totalInvested) + " tokens investis");
            lore.add("§7│ §7Pourcentage: §e" + String.format("%.3f%%", percentage * 0.01) + " du total");
            lore.add("§7└");

            // Vérification fonds
            if (playerData.getTokens() >= cost) {
                lore.add("§a✅ Cliquez pour réparer immédiatement!");
            } else {
                lore.add("§c❌ Tokens insuffisants");
            }

            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            meta.setLore(lore);
            item.setItemMeta(meta);
            gui.setItem(REPAIR_BAR_SLOTS[i], item);
        }
    }

    /**
     * NOUVEAU : Calcul de coût avec 0,01% du total pour 100%
     */
    private long calculateNewRepairCost(long totalInvested, int percentage) {
        // 100% = 0,01% du total
        // Donc : percentage% = (percentage / 100) * 0,01% du total
        double costPercentage = (percentage / 100.0) * 0.0001; // 0,01% = 0.0001
        return Math.max(1, (long)(totalInvested * costPercentage));
    }

    /**
     * CORRIGÉ : Exécution directe de la réparation
     */
    private void executeRepairDirect(Player player, int percentage) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Calcul du coût
        long totalInvested = calculateTotalInvestedTokens(playerData);
        long cost = calculateNewRepairCost(totalInvested, percentage);

        // Vérification des tokens
        if (playerData.getTokens() < cost) {
            player.sendActionBar("§c❌ Tokens insuffisants! " +
                    NumberFormatter.format(cost) + " requis, " +
                    NumberFormatter.format(playerData.getTokens()) + " disponibles");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Trouver la pioche du joueur
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendActionBar("§c❌ Pioche légendaire introuvable!");
            return;
        }

        // CORRIGÉ : Calculer avec la durabilité de base (pas augmentée par solidité)
        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();

        // Calculer la réparation sur la durabilité normale
        int currentDamage = currentDurability;
        int repairAmount = (int) (maxDurability * (percentage / 100.0));
        int newDurability = Math.max(0, currentDamage - repairAmount);

        // Appliquer la réparation
        pickaxe.setDurability((short) newDurability);

        // Déduire les tokens
        playerData.removeTokens(cost);

        // Mettre à jour la pioche
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        // Messages de succès dans l'action bar
        double repairedPercent = ((double) repairAmount / maxDurability) * 100;
        player.sendActionBar("§a✅ Pioche réparée: +" + String.format("%.1f%%", repairedPercent) +
                " (-" + NumberFormatter.format(cost) + " tokens)");

        // Son de succès
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        // Fermer le menu et rouvrir pour mise à jour
        player.closeInventory();

        // Délai petit pour laisser le temps au son
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            openRepairGUI(player);
        }, 5L);

        plugin.getPluginLogger().info("Réparation directe effectuée pour " + player.getName() +
                ": " + percentage + "% pour " + NumberFormatter.format(cost) + " tokens");
    }

    /**
     * CORRIGÉ : Informations de la pioche avec durabilité de base
     */
    private ItemStack createPickaxeInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6⛏️ §lINFORMATIONS DE LA PIOCHE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (pickaxe != null) {
            // CORRIGÉ : État avec durabilité normale (pas augmentée)
            short currentDurability = pickaxe.getDurability();
            short maxDurability = pickaxe.getType().getMaxDurability();

            double healthPercent = ((double)(maxDurability - currentDurability) / maxDurability) * 100;

            lore.add("§e⛏️ §lÉTAT ACTUEL");
            lore.add("§7│ §eDurabilité: " + getDurabilityColorForButton(healthPercent) + String.format("%.1f%%", healthPercent));
            lore.add("§7│ §ePoints: §6" + (maxDurability - currentDurability) + "§7/§6" + maxDurability);

            // Afficher info solidité sans bonus de durabilité
            int durabilityLevel = playerData.getEnchantmentLevel("durability");
            if (durabilityLevel > 0) {
                double preservationChance = Math.min(95.0, durabilityLevel * 5.0);
                lore.add("§7│ §eBonus Solidité: §a" + String.format("%.0f%%", preservationChance) +
                        " §7chance d'éviter la perte (Niv." + durabilityLevel + ")");
            }

            // CORRIGÉ : Recommandation basée sur l'état réel
            if (currentDurability >= maxDurability - 1) {
                lore.add("§7│ §c💀 PIOCHE CASSÉE! Réparation URGENTE!");
            } else if (healthPercent < 15) {
                lore.add("§7│ §c⚠️ Réparation critique recommandée!");
            } else if (healthPercent < 30) {
                lore.add("§7│ §e⚠️ Réparation recommandée");
            } else if (healthPercent < 60) {
                lore.add("§7│ §e⚠️ Réparation optionnelle");
            } else {
                lore.add("§7│ §a✓ En bon état");
            }

            lore.add("§7└");
            lore.add("");

            // CORRIGÉ : Coûts avec nouveau système
            long totalInvested = calculateTotalInvestedTokens(playerData);
            lore.add("§6💰 §lCOÛTS DE RÉPARATION (NOUVEAUX)");
            lore.add("§7│ §6Base: §e" + NumberFormatter.format(totalInvested) + " tokens investis");
            lore.add("§7│ §7Réparation 20%: §6" + NumberFormatter.format(calculateNewRepairCost(totalInvested, 20)) + " tokens");
            lore.add("§7│ §7Réparation 50%: §6" + NumberFormatter.format(calculateNewRepairCost(totalInvested, 50)) + " tokens");
            lore.add("§7│ §7Réparation 100%: §6" + NumberFormatter.format(calculateNewRepairCost(totalInvested, 100)) + " tokens");
            lore.add("§7└ §7Nouveau: 0,01% du total pour 100%");

        } else {
            lore.add("§c❌ §lPIOCHE INTROUVABLE");
            lore.add("§7│ §cVotre pioche légendaire est introuvable!");
            lore.add("§7│ §7Assurez-vous qu'elle est dans votre inventaire.");
            lore.add("§7└");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (pickaxe != null) {
            lore.add("§e✨ Cliquez directement sur les pourcentages!");
        } else {
            lore.add("§7Récupérez votre pioche pour accéder à la réparation");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Bouton retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c↩ §lRetour");

        List<String> lore = new ArrayList<>();
        lore.add("§7Retourner au menu des enchantements");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Remplissage décoratif des bordures
     */
    private void fillBorders(Inventory gui) {
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();
        meta.setDisplayName(" ");
        borderItem.setItemMeta(meta);

        // Remplir bordures
        int[] borderSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26};
        for (int slot : borderSlots) {
            gui.setItem(slot, borderItem);
        }
    }

    /**
     * Calcule le total des tokens investis
     */
    private long calculateTotalInvestedTokens(PlayerData playerData) {
        long total = 0;

        Map<String, Integer> enchantments = playerData.getEnchantmentLevels();

        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            String enchantName = entry.getKey();
            int currentLevel = entry.getValue();

            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantName);
            if (enchantment != null) {
                // Calcule le coût total pour atteindre ce niveau
                for (int level = 1; level <= currentLevel; level++) {
                    total += enchantment.getUpgradeCost(level);
                }
            }
        }
        return total;
    }


    /**
     * Couleur selon la durabilité
     */
    private String getDurabilityColorForButton(double healthPercent) {
        if (healthPercent >= 80) return "§a";
        if (healthPercent >= 60) return "§e";
        if (healthPercent >= 40) return "§6";
        if (healthPercent >= 20) return "§c";
        return "§4";
    }
}