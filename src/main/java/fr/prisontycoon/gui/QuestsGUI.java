package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.quests.QuestCategory;
import fr.prisontycoon.quests.QuestDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class QuestsGUI {
    private final PrisonTycoon plugin;

    public QuestsGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = plugin.getGUIManager().createInventory(54, "§b§lQuêtes");

        // Onglets Daily/Weekly
        inv.setItem(4, create(QuestCategory.DAILY, Material.CLOCK, "§eQuêtes quotidiennes"));
        inv.setItem(13, create(QuestCategory.WEEKLY, Material.BOOK, "§dQuêtes hebdomadaires"));

        // Lister les quêtes Daily par défaut
        fillQuests(player, inv, QuestCategory.DAILY);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.QUESTS_MENU, inv);
        player.openInventory(inv);
    }

    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        String name = item.hasItemMeta() && item.getItemMeta().displayName() != null ? net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName()) : "";
        if (name.contains("Quêtes quotidiennes")) {
            fillQuests(player, player.getOpenInventory().getTopInventory(), QuestCategory.DAILY);
            return;
        }
        if (name.contains("Quêtes hebdomadaires")) {
            fillQuests(player, player.getOpenInventory().getTopInventory(), QuestCategory.WEEKLY);
            return;
        }
        // Claim si le lore contient l'id de quête
        List<String> lore = item.hasItemMeta() && item.getItemMeta().lore() != null ? item.getItemMeta().lore().stream().map(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()::serialize).toList() : null;
        if (lore == null) return;
        String questId = null;
        for (String l : lore) {
            if (l.startsWith("§8id:")) { questId = l.substring("§8id:".length()); break; }
        }
        if (questId != null) {
            boolean ok = plugin.getQuestManager().claim(player, questId);
            if (ok) {
                player.sendMessage("§aRécompense réclamée!");
                // Refresh
                String title = plugin.getGUIManager().getLegacyTitle(player.getOpenInventory());
                fillQuests(player, player.getOpenInventory().getTopInventory(), title.contains("hebdo") ? QuestCategory.WEEKLY : QuestCategory.DAILY);
            } else {
                player.sendMessage("§cCondition non atteinte ou déjà réclamée.");
            }
        }
    }

    private ItemStack create(QuestCategory cat, Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        plugin.getGUIManager().applyName(meta, name);
        it.setItemMeta(meta);
        return it;
    }

    private void fillQuests(Player player, Inventory inv, QuestCategory cat) {
        for (int i = 27; i < 54; i++) inv.setItem(i, null);
        var list = plugin.getQuestManager().getQuestsByCategory(cat);
        int idx = 27;
        var progress = plugin.getQuestManager().getProgress(player.getUniqueId());
        for (QuestDefinition q : list) {
            ItemStack book = new ItemStack(Material.BOOK);
            ItemMeta meta = book.getItemMeta();
            plugin.getGUIManager().applyName(meta, (cat==QuestCategory.DAILY?"§e":"§d") + q.getType().name() + " §7(" + progress.get(q.getId()) + "/" + q.getTarget() + ")");
            List<String> lore = new ArrayList<>();
            lore.add("§7Catégorie: " + cat.name());
            lore.add("§7Objectif: §f" + q.getTarget());
            lore.add("§7Récompenses: §b" + q.getRewards().getBeacons() + " beacons, §d+" + q.getRewards().getJobXp() + " XP métier");
            lore.add("§8id:" + q.getId());
            plugin.getGUIManager().applyLore(meta, lore);
            book.setItemMeta(meta);
            inv.setItem(idx++, book);
            if (idx >= 54) break;
        }
    }
}


