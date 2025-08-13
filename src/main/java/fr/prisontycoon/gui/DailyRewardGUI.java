package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.DailyRewardManager;
import fr.prisontycoon.utils.HeadEnum;
import fr.prisontycoon.utils.HeadUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu des récompenses journalières
 * Taille 45, 2 rangées de 7 jours puis dernière rangée avec le jour 15 au centre.
 * Les récompenses sont cachées sauf le jour J et le dernier jour.
 */
public class DailyRewardGUI {

    private final PrisonTycoon plugin;
    private final DailyRewardManager daily;

    public DailyRewardGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.daily = plugin.getDailyRewardManager();
    }

    public void open(Player player) {
        Inventory inv = plugin.getGUIManager().createInventory(45, "§8• §6Récompenses Journalières §8•");
        plugin.getGUIManager().fillBorders(inv);

        int claimableDay = daily.getClaimableDay(player.getUniqueId());
        boolean alreadyClaimed = daily.hasClaimedToday(player.getUniqueId());

        // Positions pour 14 premiers jours (2 rangées de 7) centrées: colonnes 1..7 sur rangées 2 et 3
        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        for (int i = 0; i < 14; i++) {
            int day = i + 1;
            boolean isToday = (day == claimableDay && !alreadyClaimed);
            boolean isClaimedToday = (day == claimableDay && alreadyClaimed);
            ItemStack head = buildDayItem(day, isToday, isClaimedToday);
            inv.setItem(slots[i], head);
        }

        // Dernier jour (15) au centre d'une rangée dédiée (rangée 4 -> slot 31)
        ItemStack last = buildDayItem(15, claimableDay == 15 && !alreadyClaimed, claimableDay == 15 && alreadyClaimed);
        inv.setItem(31, last);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.DAILY_REWARD, inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    private ItemStack buildDayItem(int day, boolean isToday, boolean isClaimedToday) {
        // Box head: ouverte si réclamable aujourd'hui, fermée sinon
        ItemStack skull = HeadUtils.createHead(isToday ? HeadEnum.OPEN_BOX : HeadEnum.CLOSED_BOX);
        ItemMeta meta = skull.getItemMeta();
        if (meta != null) {
            String titleColor = isToday ? "§a" : (isClaimedToday ? "§7" : "§e");
            plugin.getGUIManager().applyName(meta, titleColor + "Jour " + day);

            List<String> lore = new ArrayList<>();
            // Récompense visible seulement pour le jour J et le dernier jour
            if (isToday || day == 15) {
                lore.add("§7Récompense: §f" + daily.getRewardDescription(day));
                if (isToday) lore.add("");
                if (isToday) lore.add("§e▶ Cliquez pour réclamer");
            } else {
                lore.add("§8Récompense cachée");
            }
            plugin.getGUIManager().applyLore(meta, lore);
            skull.setItemMeta(meta);
        }

        // Effet glowing si la récompense du jour est réclamable
        if (isToday) {
            ItemStack glow = plugin.getGUIManager().addGlowEffect(skull);
            if (glow != null) skull = glow;
        }
        return skull;
    }

    public void handleClick(Player player, int slot) {
        // Autoriser clic uniquement si c'est le slot du jour réclamable
        int claimableDay = daily.getClaimableDay(player.getUniqueId());

        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int expectedSlot = -1;
        if (claimableDay >= 1 && claimableDay <= 14) {
            expectedSlot = slots[claimableDay - 1];
        } else if (claimableDay == 15) {
            expectedSlot = 31;
        }

        if (slot == expectedSlot) {
            boolean ok = daily.tryClaim(player);
            if (ok) {
                // Refresh
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> open(player), 2L);
            } else {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            }
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        }
    }
}


