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
 * GUI principal de la Forge Améliorée (taille 27)
 */
public class ForgeGUI {

    private final PrisonTycoon plugin;

    public ForgeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void openForgeMenu(Player player) {
        Inventory inv = plugin.getGUIManager().createInventory(27, "§8▬▬ §6⚒ §lFORGE LÉGENDAIRE §6⚒ §8▬▬");

        // Bordures simples
        fillBorders(inv);

        // Info joueur (centre haut)
        inv.setItem(4, createInfoItem(player));

        // Casques progressifs sur la ligne du milieu (2x3)
        int[] slots = { 10, 11, 12, 14, 15, 16 };
        ArmorTier[] tiers = ArmorTier.values();

        for (int i = 0; i < 6; i++) {
            ArmorTier tier = tiers[i];
            ItemStack helmet = createHelmetDisplay(tier);
            inv.setItem(slots[i], plugin.getGUIManager().addGUIMetadata(helmet, GUIType.FORGE_MAIN, "tier", String.valueOf(tier.getLevel())));
        }

        // Séparateur central
        inv.setItem(13, createSeparator());

        plugin.getGUIManager().registerOpenGUI(player, GUIType.FORGE_MAIN, inv);
        player.openInventory(inv);
    }

    private void fillBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(Component.text("§8▬").decoration(TextDecoration.ITALIC, false));
        border.setItemMeta(meta);

        // Bordures
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, border); // Sauf slot 4 (info)
        }
        inv.setItem(9, border);
        inv.setItem(17, border);
        for (int i = 18; i < 27; i++) {
            inv.setItem(i, border);
        }
    }

    private ItemStack createInfoItem(Player player) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6✦ §lINFOS FORGE");

        java.util.Map<fr.prisontycoon.managers.GlobalBonusManager.BonusCategory, Double> bonuses =
                plugin.getForgeManager().getEquippedArmorBonuses(player);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Bonus actuels de votre armure:");
        lore.add("");

        if (bonuses.isEmpty()) {
            lore.add("§c⚠ Aucune armure de forge équipée");
        } else {
            for (var entry : bonuses.entrySet()) {
                String icon = getBonusIcon(entry.getKey());
                lore.add("§7" + icon + " " + entry.getKey().getFormattedName() + ": §a+" +
                        String.format("%.1f", entry.getValue()) + "%");
            }
        }

        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§d◆ Plans: §7Caisses");
        lore.add("§b◆ Fragments: §7Quêtes & monstres");
        lore.add("§a◆ Expérience: §7Activités");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        book.setItemMeta(meta);
        return book;
    }

    private ItemStack createHelmetDisplay(ArmorTier tier) {
        ItemStack helmet = plugin.getForgeManager().createArmorPiece(tier, ArmorPiece.HELMET);
        ItemMeta meta = helmet.getItemMeta();

        plugin.getGUIManager().applyName(meta, tier.getIcon() + " §l" + tier.getRPName().toUpperCase());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Rang: " + tier.getRankDisplay());
        lore.add("§7Palier: §e" + tier.getLevel() + "/6");
        lore.add("");

        // Aperçu bonus set complet
        lore.add("§6⚡ Bonus du set complet:");
        var bonuses = plugin.getForgeManager().getPerPieceBonus(tier);
        for (var entry : bonuses.entrySet()) {
            String icon = getBonusIcon(entry.getKey());
            double fullSetBonus = entry.getValue() * 4 * 1.2; // 4 pièces + 20%
            lore.add("§7" + icon + " " + entry.getKey().getFormattedName() + ": §a+" +
                    String.format("%.1f", fullSetBonus) + "%");
        }

        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✦ Cliquez pour accéder à la forge");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.lore(plugin.getGUIManager().deserializeNoItalics(lore));
        helmet.setItemMeta(meta);
        return helmet;
    }

    private ItemStack createSeparator() {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§5✦ §d§lÉNERGIE MYSTIQUE §5✦");
        plugin.getGUIManager().applyLore(meta, List.of(
                "§7La force mystique qui unit",
                "§7tous les équipements forgés."
        ));
        star.setItemMeta(meta);
        return star;
    }

    private String getBonusIcon(fr.prisontycoon.managers.GlobalBonusManager.BonusCategory category) {
        return switch (category) {
            case EXPERIENCE_BONUS -> "📚";
            case TOKEN_BONUS -> "🪙";
            case MONEY_BONUS -> "💰";
            case SELL_BONUS -> "📈";
            case FORTUNE_BONUS -> "💎";
            default -> "⚡";
        };
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