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
 * CORRIGÉ : Menu de réparation avec nouveau système de coût exponentiel
 * NOUVEAU : Tous les boutons réparent le maximum possible selon les tokens
 * CORRIGÉ : Fix du bug de durabilité à 2030
 */
public class PickaxeRepairGUI {

    private final PrisonTycoon plugin;

    // Slots pour les boutons de réparation (tous identiques maintenant)
    private static final int[] REPAIR_BUTTON_SLOTS = {11, 12, 13, 14, 15};
    private static final int PICKAXE_INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 18;

    public PickaxeRepairGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère les clics dans le menu de réparation
     */
    public void handleRepairMenuClick(Player player, int slot, ItemStack clickedItem) {
        // Clic sur le bouton retour
        if (slot == BACK_BUTTON_SLOT) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            return;
        }

        // Vérifie si le joueur a cliqué sur un bouton de réparation
        for (int repairSlot : REPAIR_BUTTON_SLOTS) {
            if (slot == repairSlot) {
                // Empêche l'action si c'est un bouton désactivé
                if (clickedItem.getType() == Material.BARRIER || clickedItem.getType() == Material.DIAMOND) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // C'est un clic valide sur un bouton de réparation
                handleMaxRepair(player);
                return;
            }
        }
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

        // Boutons de réparation (tous identiques)
        createRepairButtons(gui, player);

        // Bouton retour
        gui.setItem(BACK_BUTTON_SLOT, createBackButton());

        player.openInventory(gui);
    }

    /**
     * NOUVEAU : Crée les boutons de réparation (tous identiques)
     */
    private void createRepairButtons(Inventory gui, Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            // Si pas de pioche, désactive tous les boutons
            for (int slot : REPAIR_BUTTON_SLOTS) {
                gui.setItem(slot, createDisabledButton());
            }
            return;
        }

        // CORRIGÉ : Calcul précis de l'état actuel
        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();
        double currentHealthPercent = ((double)(maxDurability - currentDurability) / maxDurability) * 100;

        // CORRIGÉ : Si déjà à 100% (durabilité = 0), désactive la réparation
        if (currentDurability == 0) {
            for (int slot : REPAIR_BUTTON_SLOTS) {
                gui.setItem(slot, createFullyRepairedButton());
            }
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long playerTokens = playerData.getTokens();

        // NOUVEAU : Calcul de la réparation maximale possible
        MaxRepairResult maxRepair = calculateMaxRepair(currentDurability, maxDurability, playerTokens, playerData);

        // Crée tous les boutons identiques
        for (int slot : REPAIR_BUTTON_SLOTS) {
            ItemStack button = createMaxRepairButton(maxRepair, playerTokens);
            gui.setItem(slot, button);
        }
    }

    /**
     * NOUVEAU : Calcule la réparation maximale possible avec les tokens disponibles
     */
    private MaxRepairResult calculateMaxRepair(short currentDurability, short maxDurability, long playerTokens, PlayerData playerData) {
        if (currentDurability == 0) {
            return new MaxRepairResult(0, 0, 0, 100.0);
        }

        long totalInvested = calculateTotalInvestedTokens(playerData);

        // Recherche binaire pour trouver la réparation maximale possible
        int maxRepairPoints = currentDurability; // Maximum possible
        int bestRepairPoints = 0;
        long bestCost = 0;

        for (int repairPoints = 1; repairPoints <= maxRepairPoints; repairPoints++) {
            long cost = calculateExponentialRepairCost(totalInvested, currentDurability, maxDurability, repairPoints);
            if (cost <= playerTokens) {
                bestRepairPoints = repairPoints;
                bestCost = cost;
            } else {
                break; // Coût trop élevé, on s'arrête
            }
        }

        // Calcul des pourcentages
        double currentHealthPercent = ((double)(maxDurability - currentDurability) / maxDurability) * 100;
        double newDurability = currentDurability - bestRepairPoints;
        double newHealthPercent = ((double)(maxDurability - newDurability) / maxDurability) * 100;
        double repairPercent = newHealthPercent - currentHealthPercent;

        return new MaxRepairResult(bestRepairPoints, bestCost, repairPercent, newHealthPercent);
    }

    /**
     * NOUVEAU : Calcul du coût exponentiel selon les nouvelles règles
     * Plus la pioche est endommagée, plus c'est cher
     */
    private long calculateExponentialRepairCost(long totalInvested, short currentDurability, short maxDurability, int repairPoints) {
        // Base du coût selon l'investissement total
        double baseCost = totalInvested * 0.0001; // 0.01%

        // NOUVEAU : Facteur d'endommagement (plus c'est endommagé, plus c'est cher)
        double damagePercent = ((double) currentDurability / maxDurability);
        double damageFactor = Math.pow(damagePercent + 0.1, 2.5); // Exponentiel

        // NOUVEAU : Facteur de réparation (plus on répare, plus c'est cher par point)
        double repairFactor = Math.pow(repairPoints, 1.8);

        // Coût final exponentiel
        long cost = Math.max(1, (long) (baseCost * damageFactor * repairFactor));

        return cost;
    }

    /**
     * NOUVEAU : Classe pour stocker le résultat de la réparation maximale
     */
    private static class MaxRepairResult {
        final int repairPoints;
        final long cost;
        final double repairPercent;
        final double finalHealthPercent;

        MaxRepairResult(int repairPoints, long cost, double repairPercent, double finalHealthPercent) {
            this.repairPoints = repairPoints;
            this.cost = cost;
            this.repairPercent = repairPercent;
            this.finalHealthPercent = finalHealthPercent;
        }
    }

    /**
     * NOUVEAU : Crée un bouton de réparation maximale
     */
    private ItemStack createMaxRepairButton(MaxRepairResult maxRepair, long playerTokens) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a⚡ §lRÉPARATION MAXIMALE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (maxRepair.repairPoints > 0) {
            lore.add("§e📊 §lDÉTAILS DE LA RÉPARATION");
            lore.add("§7│ §ePoints de réparation: §a+" + maxRepair.repairPoints);
            lore.add("§7│ §eRéparation effective: §a+" + String.format("%.1f%%", maxRepair.repairPercent));
            lore.add("§7│ §eÉtat final: " + getDurabilityColor(maxRepair.finalHealthPercent) +
                    String.format("%.1f%%", maxRepair.finalHealthPercent));
            lore.add("§7└");
            lore.add("");

            lore.add("§6💰 §lCOÛT");
            lore.add("§7│ §eCoût: §6" + NumberFormatter.format(maxRepair.cost) + " tokens");

            if (playerTokens >= maxRepair.cost) {
                lore.add("§7│ §aVous pouvez effectuer cette réparation!");
                lore.add("§7└");
                lore.add("");
                lore.add("§a✅ §lCLIQUEZ POUR RÉPARER");
            } else {
                lore.add("§7│ §cTokens insuffisants!");
                lore.add("§7│ §cIl vous manque: §4" + NumberFormatter.format(maxRepair.cost - playerTokens) + " tokens");
                lore.add("§7└");
                lore.add("");
                lore.add("§c❌ §lTOKENS INSUFFISANTS");
                item.setType(Material.BARRIER);
            }
        } else {
            lore.add("§c❌ §lAUCUNE RÉPARATION POSSIBLE");
            lore.add("§7│ §cTokens insuffisants pour toute réparation");
            lore.add("§7│ §eContinuez à miner pour obtenir plus de tokens!");
            lore.add("§7└");
            item.setType(Material.BARRIER);
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU : Effectue la réparation maximale possible
     */
    public void handleMaxRepair(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérification des conditions de base
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendActionBar("§c❌ Pioche légendaire introuvable!");
            return;
        }

        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();

        // CORRIGÉ : Empêche la réparation si déjà à 100%
        if (currentDurability == 0) {
            player.sendActionBar("§e⚠️ Votre pioche est déjà entièrement réparée!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Calcul de la réparation maximale possible
        MaxRepairResult maxRepair = calculateMaxRepair(currentDurability, maxDurability, playerData.getTokens(), playerData);

        if (maxRepair.repairPoints == 0) {
            player.sendActionBar("§c❌ Tokens insuffisants pour toute réparation!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Vérification finale des tokens (sécurité)
        if (playerData.getTokens() < maxRepair.cost) {
            player.sendActionBar("§c❌ Erreur: tokens insuffisants!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Application de la réparation
        int newDurability = Math.max(0, currentDurability - maxRepair.repairPoints);
        pickaxe.setDurability((short) newDurability);

        // Déduction des tokens
        playerData.removeTokens(maxRepair.cost);

        // Mise à jour de la pioche
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        // Reset les notifications de durabilité
        player.removeMetadata("durability_notif_25", plugin);
        player.removeMetadata("durability_notif_10", plugin);

        // Messages de succès
        player.sendActionBar("§a✅ Pioche réparée: +" + String.format("%.1f%%", maxRepair.repairPercent) +
                " (-" + NumberFormatter.format(maxRepair.cost) + " tokens)");

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        // Mise à jour du menu
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            createRepairButtons(player.getOpenInventory().getTopInventory(), player);
            player.getOpenInventory().getTopInventory().setItem(PICKAXE_INFO_SLOT, createPickaxeInfoItem(player));
        }, 1L);

        plugin.getPluginLogger().info("Réparation maximale effectuée pour " + player.getName() +
                ": +" + maxRepair.repairPoints + " points (+" + String.format("%.1f%%", maxRepair.repairPercent) +
                ") pour " + NumberFormatter.format(maxRepair.cost) + " tokens");
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
        meta.setDisplayName("§c❌ §lRÉPARATION INDISPONIBLE");
        List<String> lore = new ArrayList<>();
        lore.add("§cPioche légendaire introuvable!");
        lore.add("§7Assurez-vous qu'elle est dans votre inventaire.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

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
        lore.add("§e⚠️ §lRÉPARATION IMPOSSIBLE");
        lore.add("§7│ §7Votre pioche n'a pas besoin de réparation.");
        lore.add("§7│ §7Utilisez-la pour miner et revenez quand");
        lore.add("§7│ §7elle sera endommagée.");
        lore.add("§7└");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private String getDurabilityColor(double healthPercent) {
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

    /**
     * CORRIGÉ : Informations de la pioche avec calcul de durabilité précis
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
            short currentDurability = pickaxe.getDurability();
            short maxDurability = pickaxe.getType().getMaxDurability();

            // CORRIGÉ : Calcul précis du pourcentage de santé
            double healthPercent = ((double)(maxDurability - currentDurability) / maxDurability) * 100;

            lore.add("§e⛏️ §lÉTAT ACTUEL");
            lore.add("§7│ §eDurabilité: " + getDurabilityColor(healthPercent) + String.format("%.1f%%", healthPercent));
            lore.add("§7│ §ePoints: §6" + (maxDurability - currentDurability) + "§7/§6" + maxDurability);
            lore.add("§7│ §eEndommagement: §c" + currentDurability + " points");

            if (currentDurability == 0) {
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

            // Informations sur le nouveau système de coût
            long totalInvested = calculateTotalInvestedTokens(playerData);
            lore.add("§6💰 §lSYSTÈME DE COÛT EXPONENTIEL");
            lore.add("§7│ §6Base: §e" + NumberFormatter.format(totalInvested) + " tokens investis");
            lore.add("§7│ §7Plus la pioche est endommagée, plus c'est cher");
            lore.add("§7│ §7Tous les boutons = réparation maximale possible");
            lore.add("§7└");

        } else {
            lore.add("§c❌ §lPIOCHE INTROUVABLE");
            lore.add("§7│ §7Pioche légendaire introuvable!");
            lore.add("§7│ §7Assurez-vous qu'elle est dans votre inventaire.");
            lore.add("§7└");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }
}