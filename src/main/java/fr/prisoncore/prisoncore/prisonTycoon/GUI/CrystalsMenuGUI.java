package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.crystals.Crystal;
import fr.prisoncore.prisoncore.prisonTycoon.crystals.CrystalType;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface graphique pour gérer les cristaux sur la pioche
 */
public class CrystalsMenuGUI {

    private final PrisonTycoon plugin;

    public CrystalsMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu de gestion des cristaux
     */
    public void openCrystalMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, "§5✦ §lGestion des Cristaux §5✦");

        // Remplissage décoratif
        fillBorders(gui);

        // Tête du joueur
        gui.setItem(4, createPlayerHead(player));

        // Cristaux appliqués (slots 10-13)
        displayAppliedCrystals(gui, player);

        // Boutons d'action
        gui.setItem(30, createInfoButton());
        gui.setItem(31, createRemovalButton());
        gui.setItem(32, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
    }

    /**
     * Affiche les cristaux appliqués sur la pioche
     */
    private void displayAppliedCrystals(Inventory gui, Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        List<Crystal> appliedCrystals = new ArrayList<>();

        if (pickaxe != null) {
            appliedCrystals = plugin.getCrystalManager().getAppliedCrystals(pickaxe);
        }

        int[] crystalSlots = {10, 11, 12, 13};

        for (int i = 0; i < 4; i++) {
            if (i < appliedCrystals.size()) {
                // Cristal appliqué
                gui.setItem(crystalSlots[i], createAppliedCrystalItem(appliedCrystals.get(i), i + 1));
            } else {
                // Slot vide
                gui.setItem(crystalSlots[i], createEmptyCrystalSlot(i + 1));
            }
        }
    }

    /**
     * Crée un item représentant un cristal appliqué
     */
    private ItemStack createAppliedCrystalItem(Crystal crystal, int position) {
        ItemStack item = new ItemStack(crystal.getType().getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§5✦ " + crystal.getType().getDisplayName() + " §5Niveau " + crystal.getLevel());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Position: §5#" + position);
        lore.add("§7Effet: " + crystal.getType().getDetailedDescription(crystal.getLevel()));
        lore.add("§7Bonus actuel: §a+" + String.format("%.1f", crystal.getBonus()) +
                (crystal.getType() == CrystalType.ABONDANCE_CRISTAL ? "s" : "%"));
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§c⚠️ §lRETRAIT");
        lore.add("§7▸ Coût: §e50 tokens");
        lore.add("§7▸ §c50% chance§7 de destruction");
        lore.add("§7▸ Clic pour retirer ce cristal");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un item représentant un slot de cristal vide
     */
    private ItemStack createEmptyCrystalSlot(int position) {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§8✦ §7Slot Cristal #" + position + " §8✦");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Position: §8#" + position);
        lore.add("§7Statut: §8Vide");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");
        lore.add("§e💎 §lAPPLICATION");
        lore.add("§7▸ Coût d'application: §a" + getApplicationCost(position) + " XP");
        lore.add("§7▸ Cliquez avec un cristal révélé pour l'appliquer");
        lore.add("§7▸ 1 seul cristal de chaque type par pioche");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Obtient le coût d'application pour une position
     */
    private int getApplicationCost(int position) {
        return switch (position) {
            case 1 -> 1000;
            case 2 -> 2500;
            case 3 -> 5000;
            case 4 -> 10000;
            default -> 1000;
        };
    }

    /**
     * Crée le bouton d'informations
     */
    private ItemStack createInfoButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e📋 §lInformations Cristaux");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Les cristaux améliorent votre pioche avec");
        lore.add("§7des bonus puissants et permanents.");
        lore.add("");
        lore.add("§e🔮 §lTYPES DISPONIBLES");
        lore.add("§7▸ §6SellBoost§7: +% prix vente");
        lore.add("§7▸ §aXPBoost§7: +% effet XP Greed");
        lore.add("§7▸ §6MoneyBoost§7: +% effet Money Greed");
        lore.add("§7▸ §eTokenBoost§7: +% effet Token Greed");
        lore.add("§7▸ §2MineralGreed§7: +% effet Fortune");
        lore.add("§7▸ §6AbondanceCristal§7: +durée Abondance");
        lore.add("§7▸ §cCombustionCristal§7: +Combustion");
        lore.add("§7▸ §dEchoCristal§7: Échos Laser/Explosion");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de retrait
     */
    private ItemStack createRemovalButton() {
        ItemStack item = new ItemStack(Material.TNT);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c💥 §lRetirer un Cristal");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Retirez un cristal appliqué sur votre pioche.");
        lore.add("");
        lore.add("§c⚠️ §lRISQUES");
        lore.add("§7▸ Coût: §e50 tokens");
        lore.add("§7▸ §c50% chance§7 de destruction du cristal");
        lore.add("§7▸ §a50% chance§7 de récupération");
        lore.add("");
        lore.add("§7Cliquez directement sur un cristal appliqué");
        lore.add("§7pour le retirer de votre pioche.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7← §lRetour");
        meta.setLore(List.of("§7Retourner au menu principal"));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée la tête du joueur avec ses informations
     */
    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(player);
        meta.setDisplayName("§5✦ §l" + player.getName() + " - Cristaux");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);

        List<String> lore = new ArrayList<>();
        lore.add("§5💎 §lÉTAT DES CRISTAUX");

        if (pickaxe != null) {
            List<Crystal> crystals = plugin.getCrystalManager().getAppliedCrystals(pickaxe);
            lore.add("§7│ §5Cristaux appliqués: §e" + crystals.size() + "§7/§54");

            if (!crystals.isEmpty()) {
                lore.add("§7│ §5Types actifs:");
                for (Crystal crystal : crystals) {
                    double bonus = crystal.getBonus();
                    String unit = crystal.getType() == CrystalType.ABONDANCE_CRISTAL ? "s" : "%";
                    lore.add("§7│   §8• " + crystal.getType().getDisplayName() +
                            " §7(+" + String.format("%.1f", bonus) + unit + ")");
                }
            }
        } else {
            lore.add("§7│ §cPioche légendaire introuvable!");
        }

        lore.add("§7│");
        lore.add("§7│ §6Tokens: §e" + NumberFormatter.format(playerData.getTokens()));
        lore.add("§7│ §aXP: §2" + (player.getLevel() * 100 + (int)(player.getExp() * player.getExpToLevel())));
        lore.add("§7└");

        meta.setLore(lore);
        head.setItemMeta(meta);

        return head;
    }

    /**
     * Gère les clics dans le menu des cristaux
     */
    public void handleCrystalMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        if (slot == 32) { // Retour
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            return;
        }

        // Gestion des cristaux appliqués (slots 10-13)
        if (slot >= 10 && slot <= 13) {
            int position = slot - 10;

            // Vérifie si c'est un cristal appliqué
            if (item != null && item.getType() != Material.GRAY_STAINED_GLASS) {
                // Tentative de retrait
                confirmCrystalRemoval(player, position);
            } else {
                // Slot vide - vérifier si le joueur a un cristal en main
                ItemStack handItem = player.getInventory().getItemInMainHand();
                if (Crystal.isCrystal(handItem)) {
                    Crystal crystal = Crystal.fromItemStack(handItem);
                    if (crystal != null && crystal.isRevealed()) {
                        attemptCrystalApplication(player, crystal, handItem);
                    } else {
                        player.sendMessage("§c❌ Ce cristal doit d'abord être révélé!");
                    }
                } else {
                    player.sendMessage("§e💡 Tenez un cristal révélé en main pour l'appliquer!");
                }
            }
        }
    }

    /**
     * Confirme le retrait d'un cristal
     */
    private void confirmCrystalRemoval(Player player, int position) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getTokens() < 50) {
            player.sendMessage("§c❌ Tokens insuffisants! Requis: §e50 tokens");
            return;
        }

        boolean success = plugin.getCrystalManager().removeCrystalFromPickaxe(player, position);

        if (success) {
            // Rouvre le menu
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                openCrystalMenu(player);
            }, 3L);
        }
    }

    /**
     * Tente d'appliquer un cristal
     */
    private void attemptCrystalApplication(Player player, Crystal crystal, ItemStack handItem) {
        boolean success = plugin.getCrystalManager().applyCrystalToPickaxe(player, crystal);

        if (success) {
            // Retire le cristal de la main
            if (handItem.getAmount() > 1) {
                handItem.setAmount(handItem.getAmount() - 1);
            } else {
                player.getInventory().setItemInMainHand(null);
            }

            // Rouvre le menu
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                openCrystalMenu(player);
            }, 3L);
        }
    }

    /**
     * Remplit les bordures
     */
    private void fillBorders(Inventory gui) {
        ItemStack filler = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName("§5");
        filler.setItemMeta(meta);

        // Bordures
        int[] borderSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 33, 34, 35};
        for (int slot : borderSlots) {
            gui.setItem(slot, filler);
        }
    }
}