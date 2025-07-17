package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Menu des Enchantements Uniques (Feature future)
 * CORRIGÉ : Noms en gras et gestion des clics
 */
public class UniqueEnchantsMenuGUI {

    private final PrisonTycoon plugin;

    public UniqueEnchantsMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void openUniqueEnchantsMenu(Player player) {
        // CORRECTION: Nom en gras
        Inventory gui = Bukkit.createInventory(null, 27, "§d✨ §lEnchantements Uniques §d✨");

        // Remplissage décoratif
        fillWithFutureItems(gui);

        // Item central d'information
        ItemStack info = new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§d⚡ §lEnchantements Légendaires");
        meta.setLore(List.of(
                "§7Fonctionnalité à venir dans une future mise à jour!",
                "",
                "§d✨ §lAperçu des fonctionnalités:",
                "§7▸ Enchantements ultra-rares",
                "§7▸ Effets visuels spectaculaires",
                "§7▸ Pouvoirs uniques et exclusifs",
                "§7▸ Système de découverte par chance",
                "",
                "§e⏳ Implémentation prévue bientôt..."
        ));
        info.setItemMeta(meta);

        gui.setItem(13, info);

        // Bouton retour
        gui.setItem(22, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.5f, 2.0f);
        player.sendMessage("§d⚡ Aperçu des Enchantements Uniques - Fonctionnalité à venir!");
    }

    /**
     * CORRECTION: Gère les clics dans le menu enchantements uniques
     */
    public void handleUniqueEnchantsMenuClick(Player player, int slot, ItemStack item) {
        if (slot == 22) { // Retour
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
        }
        // Tous les autres clics ne font rien (feature future)
    }

    private ItemStack createBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName("§7← §lRetour");
        meta.setLore(List.of("§7Retourner au menu principal"));
        arrow.setItemMeta(meta);
        return arrow;
    }

    private void fillWithFutureItems(Inventory gui) {
        ItemStack filler = new ItemStack(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName("§d");
        filler.setItemMeta(meta);

        // Bordures
        int[] borderSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 23, 24, 25, 26};
        for (int slot : borderSlots) {
            gui.setItem(slot, filler);
        }
    }
}