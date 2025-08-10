package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.ForgeManager.ArmorPiece;
import fr.prisontycoon.managers.ForgeManager.ArmorTier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI principal de la Forge (taille 27)
 */
public class ForgeGUI {

    private final PrisonTycoon plugin;

    public ForgeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void openForgeMenu(Player player) {
        Inventory inv = plugin.getGUIManager().createInventory(27, "§8• §6Forge §8•");

        // Placeholder info joueur + effets armure équipés
        inv.setItem(4, createInfoItem(player));

        // Casques en cuivre progressifs: 3 + séparateur + 3 sur la ligne du milieu
        // Slots de la ligne du milieu: 9..17
        int[] slots = { 10, 11, 12, 14, 15, 16 };
        ArmorTier[] tiers = ArmorTier.values();
        for (int i = 0; i < 6; i++) {
            ArmorTier tier = tiers[Math.min(i, tiers.length - 1)];
            ItemStack helmet = plugin.getForgeManager().createArmorPiece(tier, ArmorPiece.HELMET);
            ItemMeta meta = helmet.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Cliquez pour ouvrir la forge du set §e" + tier.getDisplayName());
            meta.lore(plugin.getGUIManager().deserializeNoItalics(lore));
            helmet.setItemMeta(meta);
            inv.setItem(slots[i], plugin.getGUIManager().addGUIMetadata(helmet, GUIType.FORGE_MAIN, "tier", String.valueOf(tier.getLevel())));
        }

        // Séparateur au milieu
        ItemStack sep = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = sep.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            sep.setItemMeta(m);
        }
        inv.setItem(13, sep);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.FORGE_MAIN, inv);
        player.openInventory(inv);
    }

    private ItemStack createInfoItem(Player player) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6ℹ Infos Forge");
        java.util.Map<fr.prisontycoon.managers.GlobalBonusManager.BonusCategory, Double> bonuses = plugin.getForgeManager().getEquippedArmorBonuses(player);

        List<String> lore = new ArrayList<>();
        lore.add("§7Votre armure confère:");
        for (var entry : bonuses.entrySet()) {
            lore.add("§7- " + entry.getKey().getFormattedName() + ": §a+" + String.format("%.1f", entry.getValue()) + "%");
        }
        if (bonuses.isEmpty()) lore.add("§7Aucun bonus d'armure actif");
        lore.add(" ");
        lore.add("§7Plans: caisses");
        lore.add("§7Fragments: quêtes & monstres");
        plugin.getGUIManager().applyLore(meta, lore);
        book.setItemMeta(meta);
        return book;
    }

    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        String tierStr = plugin.getGUIManager().getDataFromItem(item, "tier");
        if (tierStr != null) {
            int lvl = Integer.parseInt(tierStr);
            plugin.getForgeRecipeGUI().openForgeRecipe(player, ArmorTier.ofLevel(lvl));
        }
    }
}


