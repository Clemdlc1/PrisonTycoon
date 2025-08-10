package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.quests.BlockCollectorManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BlockCollectorGUI {
    private final PrisonTycoon plugin;

    public BlockCollectorGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = plugin.getGUIManager().createInventory(54, "§a§lCollectionneur de blocs");
        var stats = plugin.getBlockCollectorManager().getStats(player.getUniqueId());

        List<Map.Entry<Material, Long>> entries = new ArrayList<>(stats.entrySet());
        entries.sort(Comparator.comparingLong(Map.Entry<Material, Long>::getValue).reversed());

        int idx = 0;
        for (var e : entries) {
            if (idx >= inv.getSize()) break;
            Material mat = e.getKey();
            long count = e.getValue();
            ItemStack it = new ItemStack(mat);
            ItemMeta meta = it.getItemMeta();

            int tier = plugin.getBlockCollectorManager().getTierFor(player, mat);
            long toNext = plugin.getBlockCollectorManager().getProgressToNext(player, mat);
            long nextReq = plugin.getBlockCollectorManager().getNextTierRequirement(Math.min(100, tier + 1));

            plugin.getGUIManager().applyName(meta, "§f" + mat.name());
            List<String> lore = new ArrayList<>();
            lore.add("§7Détruits: §b" + count);
            lore.add("§7Palier: §e" + tier + "/100");
            if (tier < 100) lore.add("§7Prochain palier: §b" + (nextReq - (nextReq - toNext)) + "/" + nextReq + "); restant: §c" + toNext);
            else lore.add("§aMax atteint");

            // Top3
            var top = plugin.getBlockCollectorManager().getTopFor(mat, 3);
            lore.add("§7Top 3:");
            int pos = 1;
            for (var t : top) {
                UUID id = t.getKey();
                String name = plugin.getServer().getOfflinePlayer(id).getName();
                lore.add("§7 " + pos++ + ". §f" + (name==null?"?":name) + " §8(" + t.getValue() + ")");
            }
            plugin.getGUIManager().applyLore(meta, lore);
            it.setItemMeta(meta);
            inv.setItem(idx++, it);
        }

        plugin.getGUIManager().registerOpenGUI(player, GUIType.BLOCK_COLLECTOR, inv);
        player.openInventory(inv);
    }

    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null) return;
        Material mat = item.getType();
        if (mat == Material.AIR) return;
        boolean ok = plugin.getBlockCollectorManager().claimTier(player, mat);
        if (ok) {
            player.sendMessage("§aPalier validé pour §f" + mat.name() + "§a!");
            open(player);
        } else {
            player.sendMessage("§cAucun palier à valider.");
        }
    }
}


