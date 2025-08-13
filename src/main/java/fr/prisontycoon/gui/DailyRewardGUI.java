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

        int progress = daily.getProgress(player.getUniqueId()); // jours déjà récupérés dans le cycle (0..14)
        int claimableDay = progress + 1;
        boolean alreadyClaimed = daily.hasClaimedToday(player.getUniqueId());

        // Positions pour 14 premiers jours (2 rangées de 7) centrées: colonnes 1..7 sur rangées 2 et 3
        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25
        };

        for (int i = 0; i < 14; i++) {
            int day = i + 1;
            boolean isClaimed = day <= progress;
            boolean isClaimable = (day == claimableDay && !alreadyClaimed);
            boolean showTomorrow = (!isClaimable && !isClaimed && alreadyClaimed && day == claimableDay);
            ItemStack head = buildDayItem(day, isClaimable, isClaimed, showTomorrow);
            inv.setItem(slots[i], head);
        }

        // Dernier jour (15) au centre d'une rangée dédiée (rangée 4 -> slot 31)
        boolean lastClaimed = 15 <= progress;
        boolean lastClaimable = (15 == claimableDay && !alreadyClaimed);
        ItemStack last = buildDayItem(15, lastClaimable, lastClaimed, false);
        inv.setItem(31, last);

        // Placeholder d'information (slot 4)
        inv.setItem(4, buildInfoItem(progress));

        plugin.getGUIManager().registerOpenGUI(player, GUIType.DAILY_REWARD, inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    private ItemStack buildDayItem(int day, boolean isClaimable, boolean isClaimed, boolean showTomorrowHint) {
        // Règle d'affichage:
        // - OPEN_BOX = jour déjà récupéré
        // - CLOSED_BOX = jour non récupéré
        // - CLOSED_BOX avec glow = jour du J récupérable aujourd'hui
        ItemStack skull = HeadUtils.createHead(isClaimed ? HeadEnum.OPEN_BOX : HeadEnum.CLOSED_BOX);
        ItemMeta meta = skull.getItemMeta();
        if (meta != null) {
            String titleColor = isClaimable ? "§a" : (isClaimed ? "§7" : "§e");
            plugin.getGUIManager().applyName(meta, titleColor + "Jour " + day);

            List<String> lore = new ArrayList<>();
            // Lore
            if (isClaimed) {
                lore.add("§aDéjà récupéré");
            }

            // Récompense visible pour le jour réclamable et le dernier jour
            if (isClaimable || day == 15) {
                lore.add("§7Récompense: §f" + daily.getRewardDescription(day));
                if (isClaimable) {
                    lore.add("");
                    lore.add("§e▶ Cliquez pour réclamer");
                }
            }
            if (showTomorrowHint) {
                lore.add("");
                lore.add("§7Revenez demain pour la récompense");
            }
            plugin.getGUIManager().applyLore(meta, lore);
            skull.setItemMeta(meta);
        }

        // Effet glowing si la récompense du jour est réclamable
        if (!isClaimed && isClaimable) {
            ItemStack glow = plugin.getGUIManager().addGlowEffect(skull);
            if (glow != null) skull = glow;
        }
        return skull;
    }

    private ItemStack buildInfoItem(int progress) {
        ItemStack head = HeadUtils.createHead(HeadEnum.QUESTION);
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            plugin.getGUIManager().applyName(meta, "§eInformations");
            List<String> lore = new ArrayList<>();
            lore.add("§7- §f15 paliers, puis retour au Jour 1");
            lore.add("§7- §fRécompense cachée sauf §eJour J§f et §eJour 15");
            lore.add("§7- §fProgession actuelle: §e" + progress + "§7/§e15");
            plugin.getGUIManager().applyLore(meta, lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    public void handleClick(Player player, int slot) {
        // Autoriser clic uniquement si c'est le slot du jour réclamable
        int progress = daily.getProgress(player.getUniqueId());
        int claimableDay = progress + 1;
        boolean alreadyClaimed = daily.hasClaimedToday(player.getUniqueId());

        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        // on n'utilise plus expectedSlot, on calcule directement le jour cliqué

        int clickedDay = -1;
        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) { clickedDay = i + 1; break; }
        }
        if (slot == 31) clickedDay = 15;

        if (clickedDay != -1) {
            boolean isClaimed = (clickedDay <= progress) || (clickedDay == claimableDay && alreadyClaimed);
            boolean isClaimable = (clickedDay == claimableDay && !alreadyClaimed);

            if (isClaimed) {
                player.sendMessage("§cVous avez déjà récupéré la récompense de ce jour.");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                return;
            }
            if (isClaimable) {
                boolean ok = daily.tryClaim(player);
                if (ok) {
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> open(player), 2L);
                }
                return;
            }
            // Futur
            player.sendMessage("§cVous ne pouvez pas récupérer cette récompense pour le moment.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
            return;
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
    }
}


