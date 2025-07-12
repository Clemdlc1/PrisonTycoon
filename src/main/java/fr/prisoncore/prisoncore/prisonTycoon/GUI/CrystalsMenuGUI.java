package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Menu des Cristaux (Feature future)
 * CORRIGÉ : Noms en gras et gestion des clics
 */
public class CrystalsMenuGUI {

    private final PrisonTycoon plugin;

    public CrystalsMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void openCrystalsMenu(Player player) {
        // CORRECTION: Nom en gras
        Inventory gui = Bukkit.createInventory(null, 27, "§5✨ §lCristaux Magiques §5✨");

        // Remplissage décoratif
        fillWithFutureItems(gui);

        // Item central d'information
        ItemStack info = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§d🔮 §lSystème de Cristaux");
        meta.setLore(List.of(
                "§7Fonctionnalité à venir dans une future mise à jour!",
                "",
                "§5✨ §lAperçu des fonctionnalités:",
                "§7▸ Cristaux de puissance spéciaux",
                "§7▸ Enchantements temporaires",
                "§7▸ Bonus de minage uniques",
                "§7▸ Système de fusion de cristaux",
                "",
                "§e⏳ Implémentation prévue bientôt..."
        ));
        info.setItemMeta(meta);

        gui.setItem(13, info);

        // Bouton retour
        gui.setItem(22, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
        player.sendMessage("§d🔮 Aperçu du système de Cristaux - Fonctionnalité à venir!");
    }

    /**
     * CORRECTION: Gère les clics dans le menu cristaux
     */
    public void handleCrystalsMenuClick(Player player, int slot, ItemStack item) {
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
        ItemStack filler = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName("§5");
        filler.setItemMeta(meta);

        // Bordures
        int[] borderSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 23, 24, 25, 26};
        for (int slot : borderSlots) {
            gui.setItem(slot, filler);
        }
    }
}