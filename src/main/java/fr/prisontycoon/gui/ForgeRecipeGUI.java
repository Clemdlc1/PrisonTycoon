package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.ForgeManager;
import fr.prisontycoon.managers.ForgeManager.ArmorPiece;
import fr.prisontycoon.managers.ForgeManager.ArmorTier;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * GUI de craft de la Forge (taille 54)
 */
public class ForgeRecipeGUI {

    private final PrisonTycoon plugin;

    public ForgeRecipeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void openForgeRecipe(Player player, ArmorTier tier) {
        Inventory inv = plugin.getGUIManager().createInventory(54, "§8• §6Forge §7- §e" + tier.getDisplayName() + " §8•");

        // Colonne gauche: set complet d'aperçu
        inv.setItem(10, plugin.getForgeManager().createArmorPiece(tier, ArmorPiece.HELMET));
        inv.setItem(19, plugin.getForgeManager().createArmorPiece(tier, ArmorPiece.CHESTPLATE));
        inv.setItem(28, plugin.getForgeManager().createArmorPiece(tier, ArmorPiece.LEGGINGS));
        inv.setItem(37, plugin.getForgeManager().createArmorPiece(tier, ArmorPiece.BOOTS));

        // Colonne droite: craft par pièce (boutons)
        inv.setItem(16, createCraftButton(tier, ArmorPiece.HELMET));
        inv.setItem(25, createCraftButton(tier, ArmorPiece.CHESTPLATE));
        inv.setItem(34, createCraftButton(tier, ArmorPiece.LEGGINGS));
        inv.setItem(43, createCraftButton(tier, ArmorPiece.BOOTS));

        // Zone d'infos craft (centre)
        inv.setItem(22, createInfo(tier));

        plugin.getGUIManager().registerOpenGUI(player, GUIType.FORGE_RECIPE, inv);
        player.openInventory(inv);
    }

    private ItemStack createCraftButton(ArmorTier tier, ArmorPiece piece) {
        ItemStack anvil = new ItemStack(Material.ANVIL);
        ItemMeta meta = anvil.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§aForger §6" + piece.getDisplayName());
        ForgeManager.CraftCost cost = plugin.getForgeManager().getCraftCost(tier, piece);
        List<String> lore = new ArrayList<>();
        lore.add("§7Coûts:");
        lore.add("§7- §dPlan T" + cost.blueprintTier());
        for (var e : cost.fragments().entrySet()) {
            lore.add("§7- §b" + e.getKey().getDisplay() + " §7x" + e.getValue());
        }
        if (cost.requirePreviousPiece()) lore.add("§7- §7Pièce précédente (T" + (tier.getLevel() - 1) + ")");
        lore.add("§7- §aXP §7x" + cost.experienceCost());
        lore.add(" ");
        lore.add("§eCliquez pour forger si vous avez tout");
        plugin.getGUIManager().applyLore(meta, lore);
        anvil.setItemMeta(meta);
        return plugin.getGUIManager().addGUIMetadata(anvil, GUIType.FORGE_RECIPE, "craft", piece.name() + ":" + tier.getLevel());
    }

    private ItemStack createInfo(ArmorTier tier) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6Aperçu des coûts");
        List<String> lore = new ArrayList<>();
        lore.add("§7Plans: caisses; Fragments: quêtes & monstres");
        lore.add("§7Les bonus s'appliquent par pièce, set complet +20%.");
        plugin.getGUIManager().applyLore(meta, lore);
        book.setItemMeta(meta);
        return book;
    }

    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        String craft = plugin.getGUIManager().getDataFromItem(item, "craft");
        if (craft != null) {
            String[] parts = craft.split(":");
            ArmorPiece piece = ArmorPiece.valueOf(parts[0]);
            ArmorTier tier = ArmorTier.ofLevel(Integer.parseInt(parts[1]));
            if (plugin.getForgeManager().canCraft(player, tier, piece)) {
                boolean ok = plugin.getForgeManager().craft(player, tier, piece);
                if (ok) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    openForgeRecipe(player, tier);
                }
            } else {
                List<String> missing = plugin.getForgeManager().getMissingRequirements(player, tier, piece);
                player.sendMessage("§c❌ Il vous manque:");
                for (String m : missing) player.sendMessage(" §7• " + m);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
    }
}


