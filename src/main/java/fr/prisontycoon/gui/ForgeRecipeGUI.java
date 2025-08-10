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

import java.util.ArrayList;
import java.util.List;

/**
 * GUI de craft de la Forge Simplifiée (taille 54)
 * Design table de craft avec aperçu du set à gauche
 */
public class ForgeRecipeGUI {

    private final PrisonTycoon plugin;

    public ForgeRecipeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void openForgeRecipe(Player player, ArmorTier tier) {
        String tierName = tier.getRPName();
        String tierIcon = tier.getIcon();

        Inventory inv = plugin.getGUIManager().createInventory(54,
                "§8▬ " + tierIcon + " §lFORGE " + tierName + " " + tierIcon + " §8▬");

        // Bordures
        fillBorders(inv);

        // GAUCHE: Set d'armure (colonne 1-2)
        createArmorDisplay(inv, tier);

        // CENTRE: Zone de craft avec placeholders (colonne 4-5)
        createCraftZone(inv);

        // DROITE: Informations (colonne 7-8)
        createInfoZone(inv, tier);

        // Bouton retour
        inv.setItem(45, createBackButton());

        plugin.getGUIManager().registerOpenGUI(player, GUIType.FORGE_RECIPE, inv);
        player.openInventory(inv);
    }

    private void fillBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("§8▬")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        border.setItemMeta(meta);

        // Bordure complète
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 45; i < 54; i++) inv.setItem(i, border);
        for (int i = 9; i < 45; i += 9) inv.setItem(i, border);
        for (int i = 17; i < 53; i += 9) inv.setItem(i, border);

        // Séparateurs verticaux
        for (int i = 12; i < 45; i += 9) inv.setItem(i, border);
        for (int i = 14; i < 45; i += 9) inv.setItem(i, border);
    }

    private void createArmorDisplay(Inventory inv, ArmorTier tier) {
        // Titre
        ItemStack title = new ItemStack(Material.DIAMOND_HELMET);
        ItemMeta meta = title.getItemMeta();
        plugin.getGUIManager().applyName(meta, tier.getIcon() + " §lSET " + tier.getRPName().toUpperCase());
        plugin.getGUIManager().applyLore(meta, List.of("§7Cliquez sur une pièce pour la forger"));
        title.setItemMeta(meta);
        inv.setItem(10, title);

        // Pièces d'armure cliquables
        inv.setItem(10, createClickableArmor(tier, ArmorPiece.HELMET));
        inv.setItem(19, createClickableArmor(tier, ArmorPiece.CHESTPLATE));
        inv.setItem(28, createClickableArmor(tier, ArmorPiece.LEGGINGS));
        inv.setItem(37, createClickableArmor(tier, ArmorPiece.BOOTS));
    }

    private ItemStack createClickableArmor(ArmorTier tier, ArmorPiece piece) {
        ItemStack armor = plugin.getForgeManager().createArmorPiece(tier, piece);
        ItemMeta meta = armor.getItemMeta();

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✦ Cliquez pour voir les coûts");
        lore.add("§7et forger cette pièce");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.lore(plugin.getGUIManager().deserializeNoItalics(lore));
        armor.setItemMeta(meta);
        return plugin.getGUIManager().addGUIMetadata(armor, GUIType.FORGE_RECIPE, "select", piece.name() + ":" + tier.getLevel());
    }

    private void createCraftZone(Inventory inv) {
        // Titre zone craft
        ItemStack craftTitle = new ItemStack(Material.ANVIL);
        ItemMeta meta = craftTitle.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6⚒ §lZONE DE FORGE");
        plugin.getGUIManager().applyLore(meta, List.of("§7Sélectionnez une pièce à gauche"));
        craftTitle.setItemMeta(meta);
        inv.setItem(13, craftTitle);

        // Placeholders pour ressources (par défaut vides)
        createEmptyPlaceholder(inv, 22, "§7Placeholder 1", "§7Sélectionnez une pièce pour voir");
        createEmptyPlaceholder(inv, 31, "§7Placeholder 2", "§7les ressources nécessaires");

        // Bouton craft (désactivé par défaut)
        createDisabledCraftButton(inv, 40);
    }

    private void createEmptyPlaceholder(Inventory inv, int slot, String name, String description) {
        ItemStack placeholder = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        plugin.getGUIManager().applyName(meta, name);
        plugin.getGUIManager().applyLore(meta, List.of(description));
        placeholder.setItemMeta(meta);
        inv.setItem(slot, placeholder);
    }

    private void createDisabledCraftButton(Inventory inv, int slot) {
        ItemStack button = new ItemStack(Material.BARRIER);
        ItemMeta meta = button.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c✖ Sélectionnez une pièce");
        plugin.getGUIManager().applyLore(meta, List.of("§7Cliquez sur une pièce d'armure", "§7à gauche pour commencer"));
        button.setItemMeta(meta);
        inv.setItem(slot, button);
    }

    private void createInfoZone(Inventory inv, ArmorTier tier) {
        // Statistiques du tier
        ItemStack stats = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = stats.getItemMeta();
        plugin.getGUIManager().applyName(meta, tier.getIcon() + " §6§lSTATISTIQUES");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Nom: §e" + tier.getRPName());
        lore.add("§7Niveau: §6" + tier.getLevel() + "/6");
        lore.add("§7Rang: " + tier.getRankDisplay());
        lore.add("");
        lore.add("§6⚡ Bonus par pièce:");

        var bonuses = plugin.getForgeManager().getPerPieceBonus(tier);
        for (var entry : bonuses.entrySet()) {
            String icon = getBonusIcon(entry.getKey());
            lore.add("§7" + icon + " " + entry.getKey().getFormattedName() + ": §a+" +
                    String.format("%.1f", entry.getValue()) + "%");
        }

        lore.add("");
        lore.add("§a✦ Set complet: +20%");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        stats.setItemMeta(meta);
        inv.setItem(16, stats);

        // Guide ressources
        ItemStack guide = new ItemStack(Material.BOOK);
        meta = guide.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§b📖 §lGUIDE RESSOURCES");
        plugin.getGUIManager().applyLore(meta, List.of(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§d◆ Plans: §7Caisses",
                "§b◆ Fragments: §7Quêtes & monstres",
                "§a◆ Expérience: §7Toutes activités",
                "§7◆ Pièce précédente: §7Pour T2+",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        guide.setItemMeta(meta);
        inv.setItem(25, guide);
    }

    private ItemStack createBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c◄ §lRETOUR");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retour au menu principal"));
        arrow.setItemMeta(meta);
        return plugin.getGUIManager().addGUIMetadata(arrow, GUIType.FORGE_RECIPE, "back", "main");
    }

    // Mise à jour de la zone de craft quand une pièce est sélectionnée
    public void updateCraftZone(Inventory inv, ArmorTier tier, ArmorPiece piece) {
        ForgeManager.CraftCost cost = plugin.getForgeManager().getCraftCost(tier, piece);

        // Placeholder 1: Plan + Fragments
        createResourcePlaceholder1(inv, 22, tier, cost);

        // Placeholder 2: Expérience + Pièce précédente
        createResourcePlaceholder2(inv, 31, tier, piece, cost);

        // Bouton craft activé
        createActiveCraftButton(inv, 40, tier, piece);
    }

    private void createResourcePlaceholder1(Inventory inv, int slot, ArmorTier tier, ForgeManager.CraftCost cost) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§d📜 §lRESSOURCES PRIMAIRES");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§d◆ Plan T" + cost.blueprintTier() + ": §fx1");

        for (var entry : cost.fragments().entrySet()) {
            lore.add("§b◆ " + entry.getKey().getEmoji() + " " + entry.getKey().getDisplay() + ": §fx" + entry.getValue());
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void createResourcePlaceholder2(Inventory inv, int slot, ArmorTier tier, ArmorPiece piece, ForgeManager.CraftCost cost) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§a⚡ §lRESSOURCES SECONDAIRES");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§a◆ Expérience: §fx" + cost.experienceCost());

        if (cost.requirePreviousPiece()) {
            lore.add("§7◆ " + piece.getDisplayName() + " T" + (tier.getLevel() - 1) + ": §fx1");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private void createActiveCraftButton(Inventory inv, int slot, ArmorTier tier, ArmorPiece piece) {
        ItemStack button = new ItemStack(Material.SMITHING_TABLE);
        ItemMeta meta = button.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§a✦ §lFORGER " + piece.getIcon() + " " + piece.getDisplayName().toUpperCase());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Forger: §6" + piece.getDisplayName() + " " + tier.getRPName());
        lore.add("");
        lore.add("§e✦ Cliquez pour forger cette pièce");
        lore.add("§7(Vérification automatique des ressources)");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        button.setItemMeta(meta);
        inv.setItem(slot, plugin.getGUIManager().addGUIMetadata(button, GUIType.FORGE_RECIPE, "craft", piece.name() + ":" + tier.getLevel()));
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

        // Bouton retour
        String back = plugin.getGUIManager().getDataFromItem(item, "back");
        if ("main".equals(back)) {
            plugin.getForgeGUI().openForgeMenu(player);
            return;
        }

        // Sélection d'une pièce
        String select = plugin.getGUIManager().getDataFromItem(item, "select");
        if (select != null) {
            String[] parts = select.split(":");
            ArmorPiece piece = ArmorPiece.valueOf(parts[0]);
            ArmorTier tier = ArmorTier.ofLevel(Integer.parseInt(parts[1]));
            updateCraftZone(player.getOpenInventory().getTopInventory(), tier, piece);
            return;
        }

        // Craft
        String craft = plugin.getGUIManager().getDataFromItem(item, "craft");
        if (craft != null) {
            String[] parts = craft.split(":");
            ArmorPiece piece = ArmorPiece.valueOf(parts[0]);
            ArmorTier tier = ArmorTier.ofLevel(Integer.parseInt(parts[1]));

            if (plugin.getForgeManager().canCraft(player, tier, piece)) {
                boolean success = plugin.getForgeManager().craft(player, tier, piece);
                if (success) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
                    player.sendMessage("§a✦ §lFORGE RÉUSSIE ! §r§a" + piece.getDisplayName() + " " + tier.getRPName() + " forgé avec succès !");
                    openForgeRecipe(player, tier);
                }
            } else {
                List<String> missing = plugin.getForgeManager().getMissingRequirements(player, tier, piece);
                player.sendMessage("§c❌ §lRessources insuffisantes !");
                player.sendMessage("§7Il vous manque:");
                for (String m : missing) {
                    player.sendMessage(" §c• §7" + m);
                }
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
    }
}