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
 * CORRIGÉ : Menu de réparation avec nouveaux coûts et empêche réparation à 100%
 * INTÈGRE : Gestion des clics depuis GUIListener
 */
public class PickaxeRepairGUI {

    private final PrisonTycoon plugin;

    // Slots pour la barre de pourcentage de réparation (ligne du milieu)
    private static final int[] REPAIR_BAR_SLOTS = {11, 12, 13, 14, 15};
    private static final int PICKAXE_INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 18;

    public PickaxeRepairGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * NOUVEAU : Gère les clics dans le menu de réparation, appelé par GUIListener.
     * C'est la méthode qui manquait pour faire le lien.
     */
    public void handleRepairMenuClick(Player player, int slot, ItemStack clickedItem) {
        // Clic sur le bouton retour
        if (slot == BACK_BUTTON_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            // Redirige vers le menu principal (à adapter si le nom de la méthode est différent)
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            return;
        }

        // Vérifie si le joueur a cliqué sur un bouton de réparation
        int[] repairPercentages = {20, 40, 60, 80, 100};
        for (int i = 0; i < REPAIR_BAR_SLOTS.length; i++) {
            if (slot == REPAIR_BAR_SLOTS[i]) {
                // Empêche l'action si c'est un bouton désactivé (barrière, pioche réparée)
                if (clickedItem.getType() == Material.BARRIER || clickedItem.getType() == Material.DIAMOND) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // C'est un clic valide sur un bouton de réparation
                int percentage = repairPercentages[i];
                handleDirectRepair(player, percentage);
                return;
            }
        }
        // Si le clic n'est sur aucun bouton interactif, ne rien faire.
        // L'événement est déjà annulé par le GUIListener.
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

        // Barre de réparation avec pourcentages
        createRepairBar(gui, player);

        // Bouton retour
        gui.setItem(BACK_BUTTON_SLOT, createBackButton());

        player.openInventory(gui);
    }

    /**
     * NOUVEAU : Crée la barre de réparation avec le nouveau système de pourcentages
     */
    private void createRepairBar(Inventory gui, Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            // Si pas de pioche, désactive tous les boutons
            for (int slot : REPAIR_BAR_SLOTS) {
                gui.setItem(slot, createDisabledButton());
            }
            return;
        }

        // Calcul de l'état actuel
        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();
        double currentHealthPercent = ((double)(maxDurability - currentDurability) / maxDurability) * 100;

        // NOUVEAU : Si déjà à 100% ou presque, désactive la réparation
        if (currentDurability <= 1) {
            for (int slot : REPAIR_BAR_SLOTS) {
                gui.setItem(slot, createFullyRepairedButton());
            }
            return;
        }

        // Pourcentages de réparation (% de ce qu'il reste à réparer)
        int[] repairPercentages = {20, 40, 60, 80, 100};

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long totalInvested = calculateTotalInvestedTokens(playerData);

        for (int i = 0; i < REPAIR_BAR_SLOTS.length; i++) {
            int repairPercent = repairPercentages[i];

            // NOUVEAU : Calcul basé sur ce qu'il reste à réparer
            double remainingDamagePercent = 100.0 - currentHealthPercent;
            double actualRepairPercent = (remainingDamagePercent * repairPercent) / 100.0;
            double finalHealthPercent = currentHealthPercent + actualRepairPercent;

            // NOUVEAU : Coût adapté au nouveau système
            long cost = calculateNewRepairCost(totalInvested, repairPercent);

            ItemStack button = createRepairButton(repairPercent, actualRepairPercent,
                    finalHealthPercent, cost, playerData.getTokens());
            gui.setItem(REPAIR_BAR_SLOTS[i], button);
        }
    }

    /**
     * NOUVEAU : Calcule le coût de réparation selon le nouveau système
     */
    private long calculateNewRepairCost(long totalInvested, int repairPercent) {
        // Base : 0,01% du total investi pour 100% de réparation
        double basePercentage = 0.0001; // 0,01%

        // Facteur selon le pourcentage de réparation demandé
        double factor = Math.pow(repairPercent / 100.0, 0.8); // Coût légèrement progressif

        return Math.max(1, (long) (totalInvested * basePercentage * factor));
    }

    /**
     * NOUVEAU : Crée un bouton de réparation avec le nouveau système
     */
    private ItemStack createRepairButton(int repairPercent, double actualRepairPercent,
                                         double finalHealthPercent, long cost, long playerTokens) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a⚡ §lRÉPARER " + repairPercent + "%");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e📊 §lDÉTAILS DE LA RÉPARATION");
        lore.add("§7│ §eRéparation demandée: §a" + repairPercent + "% §7du reste à réparer");
        lore.add("§7│ §eRéparation effective: §a+" + String.format("%.1f%%", actualRepairPercent) + " §7de durabilité totale");
        lore.add("§7│ §eÉtat final: " + getDurabilityColorForButton(finalHealthPercent) +
                String.format("%.1f%%", finalHealthPercent));
        lore.add("§7└");
        lore.add("");

        lore.add("§6💰 §lCOÛT");
        lore.add("§7│ §eCoût: §6" + NumberFormatter.format(cost) + " tokens");

        if (playerTokens >= cost) {
            lore.add("§7│ §aVous pouvez effectuer cette réparation!");
            lore.add("§7└");
            lore.add("");
            lore.add("§a✅ §lCLIQUEZ POUR RÉPARER");
        } else {
            lore.add("§7│ §cTokens insuffisants!");
            lore.add("§7│ §cIl vous manque: §4" + NumberFormatter.format(cost - playerTokens) + " tokens");
            lore.add("§7└");
            lore.add("");
            lore.add("§c❌ §lTOKENS INSUFFISANTS");

            // Change l'item en barrière si pas assez de tokens
            item.setType(Material.BARRIER);
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU : Bouton quand la pioche est déjà entièrement réparée
     */
    private ItemStack createFullyRepairedButton() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a✅ §lPIOCHE ENTIÈREMENT RÉPARÉE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§a📊 §lÉTAT PARFAIT");
        lore.add("§7│ §eDurabilité: §a100.0%");
        lore.add("§7│ §eVotre pioche est en parfait état!");
        lore.add("§7└");
        lore.add("");
        lore.add("§e⚠️ §lREPAR ATION IMPOSSIBLE");
        lore.add("§7│ §7Votre pioche n'a pas besoin de réparation.");
        lore.add("§7│ §7Utilisez-la pour miner et revenez quand");
        lore.add("§7│ §7elle sera endommagée.");
        lore.add("§7└");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Traite la réparation directe selon le nouveau système.
     * Maintenant appelée par handleRepairMenuClick.
     */
    public void handleDirectRepair(Player player, int percentage) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérification des conditions de base
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendActionBar("§c❌ Pioche légendaire introuvable!");
            return;
        }

        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();

        // NOUVEAU : Empêche la réparation si déjà à 100%
        if (currentDurability <= 1) {
            player.sendActionBar("§e⚠️ Votre pioche est déjà entièrement réparée!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // NOUVEAU : Calcul avec le nouveau système de pourcentage
        double currentHealthPercent = ((double)(maxDurability - currentDurability) / maxDurability) * 100;
        double remainingDamagePercent = 100.0 - currentHealthPercent;
        double actualRepairPercent = (remainingDamagePercent * percentage) / 100.0;

        // Calcul du coût selon le nouveau système
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

        // Application de la réparation
        int actualRepairPoints = (int) (maxDurability * (actualRepairPercent / 100.0));
        int newDurability = Math.max(0, currentDurability - actualRepairPoints);

        pickaxe.setDurability((short) newDurability);

        // Déduction des tokens
        playerData.removeTokens(cost);

        // Mise à jour de la pioche
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        // NOUVEAU : Reset les notifications de durabilité
        player.removeMetadata("durability_notif_25", plugin);
        player.removeMetadata("durability_notif_10", plugin);

        // Messages de succès
        player.sendActionBar("§a✅ Pioche réparée: +" + String.format("%.1f%%", actualRepairPercent) +
                " (-" + NumberFormatter.format(cost) + " tokens)");

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        // CORRIGÉ : On met à jour le menu en place au lieu de le fermer/rouvrir
        // ce qui évite un clignotement désagréable.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Recrée juste les items qui changent
            createRepairBar(player.getOpenInventory().getTopInventory(), player);
            player.getOpenInventory().getTopInventory().setItem(PICKAXE_INFO_SLOT, createPickaxeInfoItem(player));
        }, 1L); // 1 tick de délai pour que la mise à jour de l'item soit prise en compte

        plugin.getPluginLogger().info("Réparation effectuée pour " + player.getName() +
                ": " + percentage + "% du reste (+" + String.format("%.1f%%", actualRepairPercent) +
                " effectif) pour " + NumberFormatter.format(cost) + " tokens");
    }

    private void fillBorders(Inventory gui) {
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();
        meta.setDisplayName(" ");
        borderItem.setItemMeta(meta);

        // Remplir les bordures
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, borderItem);
            gui.setItem(i + 18, borderItem);
        }

        for (int i = 9; i < 18; i += 9) {
            gui.setItem(i, borderItem);
            gui.setItem(i + 8, borderItem);
        }
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c⬅ §lRetour au menu principal");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDisabledButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c❌ §lREPAR ATION INDISPONIBLE");
        List<String> lore = new ArrayList<>();
        lore.add("§cPioche légendaire introuvable!");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String getDurabilityColorForButton(double healthPercent) {
        if (healthPercent >= 90) return "§a";
        if (healthPercent >= 70) return "§e";
        if (healthPercent >= 40) return "§6";
        if (healthPercent >= 20) return "§c";
        return "§4";
    }

    private long calculateTotalInvestedTokens(PlayerData playerData) {
        long total = 0;
        Map<String, Integer> enchantments = playerData.getEnchantmentLevels();

        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            CustomEnchantment enchant = plugin.getEnchantmentManager().getEnchantment(entry.getKey());
            if (enchant != null) {
                int level = entry.getValue();
                for (int i = 1; i <= level; i++) {
                    total += enchant.getUpgradeCost(i);
                }
            }
        }

        return total;
    }

    private ItemStack createPickaxeInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6⛏️ §lINFORMATIONS DE LA PIOCHE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (pickaxe != null) {
            short currentDurability = pickaxe.getDurability();
            short maxDurability = pickaxe.getType().getMaxDurability();

            double healthPercent = ((double)(maxDurability - currentDurability) / maxDurability) * 100;

            lore.add("§e⛏️ §lÉTAT ACTUEL");
            lore.add("§7│ §eDurabilité: " + getDurabilityColorForButton(healthPercent) + String.format("%.1f%%", healthPercent));
            lore.add("§7│ §ePoints: §6" + (maxDurability - currentDurability) + "§7/§6" + maxDurability);

            if (currentDurability <= 1) {
                lore.add("§7│ §a✓ Pioche en parfait état!");
            } else if (healthPercent < 15) {
                lore.add("§7│ §c⚠️ Réparation critique recommandée!");
            } else if (healthPercent < 30) {
                lore.add("§7│ §e⚠️ Réparation recommandée");
            } else {
                lore.add("§7│ §a✓ En bon état");
            }

            lore.add("§7└");
            lore.add("");

            // Coûts selon le nouveau système
            long totalInvested = calculateTotalInvestedTokens(playerData);
            lore.add("§6💰 §lCOÛTS DE RÉPARATION (NOUVEAU SYSTÈME)");
            lore.add("§7│ §6Base: §e" + NumberFormatter.format(totalInvested) + " tokens investis");
            lore.add("§7│ §7Réparation 20%: §6" + NumberFormatter.format(calculateNewRepairCost(totalInvested, 20)) + " tokens");
            lore.add("§7│ §7Réparation 50%: §6" + NumberFormatter.format(calculateNewRepairCost(totalInvested, 50)) + " tokens");
            lore.add("§7│ §7Réparation 100%: §6" + NumberFormatter.format(calculateNewRepairCost(totalInvested, 100)) + " tokens");
            lore.add("§7└ §7Nouveau: pourcentage du reste à réparer");

        } else {
            lore.add("§c❌ §lPIOCHE INTROUVABLE");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }
}