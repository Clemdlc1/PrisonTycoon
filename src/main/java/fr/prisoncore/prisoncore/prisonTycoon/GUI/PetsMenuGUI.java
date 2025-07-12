package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List; /**
 * Menu des Pets (Feature future)
 */
public class PetsMenuGUI {

    private final PrisonTycoon plugin;

    public PetsMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void openPetsMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6✨ Compagnons de Minage ✨");

        // Remplissage décoratif
        fillWithFutureItems(gui);

        // Item central d'information
        ItemStack info = new ItemStack(Material.WOLF_SPAWN_EGG);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§6🐕 Système de Pets");
        meta.setLore(List.of(
                "§7Fonctionnalité à venir dans une future mise à jour!",
                "",
                "§6✨ Aperçu des fonctionnalités:",
                "§7▸ Compagnons de minage intelligents",
                "§7▸ Bonus d'expérience et de gains",
                "§7▸ Évolution et amélioration des pets",
                "§7▸ Collections et variétés uniques",
                "",
                "§e⏳ Implémentation prévue bientôt..."
        ));
        info.setItemMeta(meta);

        gui.setItem(13, info);

        // Bouton retour
        gui.setItem(22, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.2f);
        player.sendMessage("§6🐕 Aperçu du système de Pets - Fonctionnalité à venir!");
    }

    public void handlePetsMenuClick(Player player, int slot, ItemStack item) {
        if (slot == 22) { // Retour
            plugin.getMainMenuGUI().openMainMenu(player);
        }
    }

    private ItemStack createBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        meta.setDisplayName("§7← Retour");
        meta.setLore(List.of("§7Retourner au menu principal"));
        arrow.setItemMeta(meta);
        return arrow;
    }

    private void fillWithFutureItems(Inventory gui) {
        ItemStack filler = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName("§6");
        filler.setItemMeta(meta);

        // Bordures
        int[] borderSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 23, 24, 25, 26};
        for (int slot : borderSlots) {
            gui.setItem(slot, filler);
        }
    }
}
