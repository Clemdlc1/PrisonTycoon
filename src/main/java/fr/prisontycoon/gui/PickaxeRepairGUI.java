package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.CustomEnchantment;
import fr.prisontycoon.utils.NumberFormatter;
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

    // Slots pour les boutons de réparation (tous identiques maintenant)
    private static final int[] REPAIR_BUTTON_SLOTS = {11, 12, 13, 14, 15};
    private static final int PICKAXE_INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 18;
    private static final double COST_BASE_FACTOR = 0.0001; // 0.01% de l'investissement total
    private static final double DAMAGE_EXPONENT = 2.5;     // Exposant de la courbe de coût
    private final PrisonTycoon plugin;

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

        plugin.getGUIManager().registerOpenGUI(player, GUIType.PICKAXE_REPAIR, gui);
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

    private long calculateCostToRepairRange(long totalInvested, short maxDurability, int fromDurability, int toDurability) {
        if (fromDurability <= toDurability) {
            return 0;
        }

        // Constante C de la formule f(x) = C * x^k
        double constant = (totalInvested * COST_BASE_FACTOR) / Math.pow(maxDurability, DAMAGE_EXPONENT);

        // Nouvel exposant k+1
        double newExponent = DAMAGE_EXPONENT + 1.0;

        // Calcul de l'intégrale définie : F(from) - F(to)
        double integralFrom = Math.pow(fromDurability, newExponent) / newExponent;
        double integralTo = Math.pow(toDurability, newExponent) / newExponent;

        return Math.max(1, (long) (constant * (integralFrom - integralTo)));
    }

    /**
     * CORRIGÉ : Calcule la réparation maximale en additionnant le coût de chaque point.
     * Cette méthode est juste et empêche de "tricher" en réparant en plusieurs fois.
     */
    private MaxRepairResult calculateMaxRepair(short currentDurability, short maxDurability, long playerTokens, PlayerData playerData) {
        if (currentDurability == 0) {
            return new MaxRepairResult(0, 0, 0, 100.0);
        }

        long totalInvested = calculateTotalInvestedTokens(playerData);

        // Bornes pour la recherche dichotomique
        int low = 0; // 0 points réparés
        int high = currentDurability; // Tous les points réparés
        int bestRepairAmount = 0;

        // La recherche s'arrête après ~11-12 itérations au lieu de ~1500
        while (low <= high) {
            int mid = low + (high - low) / 2; // Le nombre de points qu'on essaie de réparer
            if (mid == 0) {
                low = 1;
                continue;
            }

            // Durabilité finale si on répare 'mid' points
            int finalDurability = currentDurability - mid;

            // On utilise la formule O(1) pour calculer le coût
            long cost = calculateCostToRepairRange(totalInvested, maxDurability, currentDurability, finalDurability);

            if (cost <= playerTokens) {
                // On peut se le permettre. On stocke ce résultat et on essaie d'en réparer plus.
                bestRepairAmount = mid;
                low = mid + 1;
            } else {
                // Trop cher. On cherche dans la moitié inférieure.
                high = mid - 1;
            }
        }

        if (bestRepairAmount == 0) {
            return new MaxRepairResult(0, 0, 0, ((double) (maxDurability - currentDurability) / maxDurability) * 100);
        }

        long finalCost = calculateCostToRepairRange(totalInvested, maxDurability, currentDurability, currentDurability - bestRepairAmount);

        // Calcul des pourcentages pour l'affichage
        double currentHealthPercent = ((double) (maxDurability - currentDurability) / maxDurability) * 100;
        double newDurability = currentDurability - bestRepairAmount;
        double newHealthPercent = ((maxDurability - newDurability) / maxDurability) * 100;
        double repairPercent = newHealthPercent - currentHealthPercent;

        return new MaxRepairResult(bestRepairAmount, finalCost, repairPercent, newHealthPercent);
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
            lore.add("§7│ §cTokens insuffisants pour réparer");
            lore.add("§7│ §emême un seul point de durabilité.");
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
        plugin.getPickaxeManager().deactivateBrokenPickaxeMode(player);
        player.removeMetadata("durability_notif_broken", plugin);
        plugin.getPickaxeManager().updatePlayerPickaxe(player);
        plugin.getPickaxeManager().updateMobilityEffects(player);

        // Reset les notifications de durabilité
        player.removeMetadata("durability_notif_25", plugin);
        player.removeMetadata("durability_notif_10", plugin);

        // Messages de succès
        player.sendActionBar("§a✅ Pioche réparée: +" + String.format("%.1f%%", maxRepair.repairPercent) +
                " (-" + NumberFormatter.format(maxRepair.cost) + " tokens)");

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        plugin.getActionBarTask().updateActionBarStatus();

        // Mise à jour du menu
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTitle().equals("§c🔨 §lRéparation de Pioche §c🔨")) {
                createRepairButtons(player.getOpenInventory().getTopInventory(), player);
                player.getOpenInventory().getTopInventory().setItem(PICKAXE_INFO_SLOT, createPickaxeInfoItem(player));
            }
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
            double healthPercent = ((double) (maxDurability - currentDurability) / maxDurability) * 100;

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

    /**
     * NOUVEAU : Classe pour stocker le résultat de la réparation maximale
     */
    private record MaxRepairResult(int repairPoints, long cost, double repairPercent, double finalHealthPercent) {
    }
}