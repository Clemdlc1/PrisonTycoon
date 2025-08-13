package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.BattlePassManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * GUI Pass de combat: 5 pages, 6 paliers/page, affichage double ligne (gratuit/premium)
 */
public class BattlePassGUI {

    private final PrisonTycoon plugin;
    private final GUIManager guiManager;

    public BattlePassGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    public void open(Player player, int page) {
        if (page < 1) page = 1;
        if (page > 5) page = 5;

        Inventory inv = guiManager.createInventory(54, "§6⚔ §lPass de Combat §8(page " + page + "/5)");
        guiManager.fillBorders(inv);

        // Header
        inv.setItem(4, createHeader(player, page));

        // Navigation
        inv.setItem(45, createBackButton());
        inv.setItem(49, createBuyPremiumButton(player));
        inv.setItem(53, createNextButton());
        inv.setItem(45 + 2, createPrevButton());

        // Remplir 6 paliers sur cette page
        int startTier = (page - 1) * 6 + 1;
        for (int i = 0; i < 6; i++) {
            int tier = startTier + i;
            setTierColumns(inv, player, i, tier);
        }

        guiManager.registerOpenGUI(player, GUIType.BATTLE_PASS_MENU, inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.8f, 1.2f);
    }

    private void setTierColumns(Inventory inv, Player player, int index, int tier) {
        int column = switch (index) {
            case 0 -> 10; case 1 -> 12; case 2 -> 14; case 3 -> 28; case 4 -> 30; case 5 -> 32; default -> 10;
        };
        BattlePassManager bpm = plugin.getBattlePassManager();
        var pd = bpm.getPlayerData(player.getUniqueId());
        int currentTier = bpm.getTier(player.getUniqueId());
        var rewards = bpm.getRewardsForTier(tier);

        // Gratuit (ligne du haut)
        inv.setItem(column, createTierItem(player, tier, false, rewards, currentTier, pd));

        // Premium (ligne du bas)
        inv.setItem(column + 9, createTierItem(player, tier, true, rewards, currentTier, pd));
    }

    private ItemStack createTierItem(Player player, int tier, boolean premiumRow, BattlePassManager.TierRewards rewards, int currentTier, BattlePassManager.PlayerPassData pd) {
        boolean reachable = currentTier >= tier;
        boolean alreadyClaimed = premiumRow ? pd.claimedPremium().contains(tier) : pd.claimedFree().contains(tier);
        boolean canClaim = reachable && !alreadyClaimed && (!premiumRow || pd.premium());

        ItemStack it = new ItemStack(premiumRow ? Material.GOLD_BLOCK : Material.IRON_BLOCK);
        ItemMeta meta = it.getItemMeta();
        String title = (premiumRow ? "§6Premium" : "§7Gratuit") + " §8- §fPalier §e" + tier;
        guiManager.applyName(meta, title);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Récompenses:");
        var qr = premiumRow ? rewards.premium() : rewards.free();
        lore.add("§f• " + qr.getDescription());
        lore.add("");
        lore.add("§7État: " + (alreadyClaimed ? "§aRéclamé" : (reachable ? "§eDisponible" : "§cVerrouillé")));
        if (canClaim) lore.add("§e▶ Cliquez pour réclamer");
        if (premiumRow && !pd.premium()) lore.add("§cPremium requis");
        lore.add("§8TIER:" + tier + (premiumRow ? ":P" : ":F"));
        guiManager.applyLore(meta, lore);
        it.setItemMeta(meta);

        if (canClaim) it = guiManager.addGlowEffect(it);
        return it;
    }

    private ItemStack createHeader(Player player, int page) {
        BattlePassManager bpm = plugin.getBattlePassManager();
        int points = bpm.getPoints(player.getUniqueId());
        int tier = bpm.getTier(player.getUniqueId());
        int inTier = bpm.getProgressInTier(player.getUniqueId());

        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = it.getItemMeta();
        guiManager.applyName(meta, "§6⚔ §lSeason " + bpm.getCurrentSeasonId());
        List<String> lore = Arrays.asList(
                "",
                "§7Durée: §f30 jours",
                "§7Progrès: §e" + points + " §7points (Palier §6" + tier + "§7, §e" + inTier + "§7/§e" + BattlePassManager.POINTS_PER_TIER + ")",
                "",
                "§7Gagnez des points via:",
                "§f• §eQuêtes journalières",
                "§f• §dQuêtes hebdomadaires",
                "§f• §6Quêtes de Pass",
                "",
                "§7Palier > 50: §61 Clé Rare par palier supplémentaire"
        );
        guiManager.applyLore(meta, lore);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack createBackButton() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta m = it.getItemMeta();
        guiManager.applyName(m, "§7← Retour quêtes");
        it.setItemMeta(m);
        return it;
    }

    private ItemStack createPrevButton() {
        ItemStack it = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta m = it.getItemMeta();
        guiManager.applyName(m, "§7Page précédente");
        it.setItemMeta(m);
        return it;
    }

    private ItemStack createNextButton() {
        ItemStack it = new ItemStack(Material.TIPPED_ARROW);
        ItemMeta m = it.getItemMeta();
        guiManager.applyName(m, "§7Page suivante");
        it.setItemMeta(m);
        return it;
    }

    private ItemStack createBuyPremiumButton(Player player) {
        boolean premium = plugin.getBattlePassManager().hasPremium(player.getUniqueId());
        ItemStack it = new ItemStack(premium ? Material.EMERALD_BLOCK : Material.EMERALD);
        ItemMeta m = it.getItemMeta();
        guiManager.applyName(m, premium ? "§aPremium §7(Actif)" : "§aAcheter Premium");
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (premium) {
            lore.add("§7Vous possédez déjà le Pass Premium.");
        } else {
            lore.add("§7Débloque la ligne §6Premium §7du Pass.");
            lore.add("§7Prix: §cBeacons (réduction VIP)");
            lore.add("§e▶ Cliquez pour acheter");
        }
        guiManager.applyLore(m, lore);
        it.setItemMeta(m);
        return it;
    }

    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        String title = plugin.getGUIManager().getLegacyTitle(player.getOpenInventory());
        int page = 1;
        try {
            int idx = title.indexOf("(page ");
            if (idx >= 0) {
                String sub = title.substring(idx + 6);
                page = Integer.parseInt(sub.substring(0, sub.indexOf('/')));
            }
        } catch (Exception ignored) {}

        if (slot == 45) { // retour
            plugin.getQuestsGUI().openMainMenu(player);
            return;
        }
        if (slot == 47) { // prev
            open(player, Math.max(1, page - 1));
            return;
        }
        if (slot == 53) { // next
            open(player, Math.min(5, page + 1));
            return;
        }
        if (slot == 49) { // buy premium
            boolean ok = plugin.getBattlePassManager().purchasePremium(player);
            if (ok) {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1.4f);
                player.sendMessage("§a✓ Pass Premium débloqué !");
                open(player, page);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                player.sendMessage("§c✗ Achat impossible (fonds insuffisants?)");
            }
            return;
        }

        // Claim d’un palier: décode l’ID caché
        if (!item.hasItemMeta() || item.getItemMeta().lore() == null) return;
        List<String> raw = item.getItemMeta().lore().stream()
                .map(c -> LegacyComponentSerializer.legacySection().serialize(c))
                .toList();
        String idLine = raw.stream().filter(s -> s.startsWith("§8TIER:")).findFirst().orElse(null);
        if (idLine == null) return;
        boolean premiumRow = idLine.endsWith(":P");
        int tier = Integer.parseInt(idLine.substring(8, idLine.indexOf(':', 9)));

        boolean ok = premiumRow ? plugin.getBattlePassManager().claimPremium(player, tier)
                : plugin.getBattlePassManager().claimFree(player, tier);
        if (ok) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            player.sendMessage("§a✓ Récompense de palier " + tier + " réclamée !");
            open(player, page);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            player.sendMessage("§c✗ Impossible de réclamer ce palier");
        }
    }
}


