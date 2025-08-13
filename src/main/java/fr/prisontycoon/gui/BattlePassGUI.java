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
import java.util.HashMap;
import java.util.Map;

/**
 * GUI Pass de combat amélioré: 5 pages, 6 paliers/page, affichage double ligne avec barres de progression
 * Correctifs des bugs et interface plus dynamique et jolie
 */
public class BattlePassGUI {

    private final PrisonTycoon plugin;
    private final GUIManager guiManager;

    public BattlePassGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    // ========================================
    // MENU INTERMÉDIAIRE
    // ========================================

    /**
     * Ouvre le menu intermédiaire du Battle Pass avec 5 boutons
     */
    public void openMainMenu(Player player) {
        Inventory inv = guiManager.createInventory(45, "§6⚔ §lPass de Combat §8- §fMenu Principal");
        guiManager.fillBorders(inv);

        // Header avec informations générales
        inv.setItem(4, createMainHeader(player));

        BattlePassManager bpm = plugin.getBattlePassManager();
        var playerData = bpm.getPlayerData(player.getUniqueId());
        int currentTier = bpm.getTier(player.getUniqueId());

        // 5 boutons principaux
        // 1. Visualisation et récupération des récompenses
        inv.setItem(19, createVisualizationButton(player, currentTier, playerData));

        // 2. Acheter Premium
        inv.setItem(21, createBuyPremiumButton(player, playerData));

        // 3. Quêtes de Pass
        inv.setItem(23, createPassQuestsButton());

        // 4. Aide
        inv.setItem(25, createHelpButton());

        // 5. Retour
        inv.setItem(40, createBackToQuestsButton());

        // Statistiques sur les côtés
        inv.setItem(11, createStatsItem(player));
        inv.setItem(15, createSeasonInfoItem());

        guiManager.registerOpenGUI(player, GUIType.BATTLE_PASS_MAIN_MENU, inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.8f, 1.2f);
    }

    // ========================================
    // GUI DU PASS AVEC PALIERS
    // ========================================

    /**
     * Ouvre une page spécifique du Pass de Combat
     */
    public void openPass(Player player, int page) {
        if (page < 1) page = 1;
        if (page > 5) page = 5;

        Inventory inv = guiManager.createInventory(54, "§6⚔ §lPass de Combat §8(Page " + page + "/5)");

        // Bordures décoratives mais pas complètes (laisser place aux paliers)
        fillDecorativeBorders(inv);

        // Header avec barre de progression générale
        inv.setItem(4, createProgressHeader(player, page));

        // Navigation
        inv.setItem(45, createBackToMainMenuButton());
        inv.setItem(47, createPrevPageButton(page));
        inv.setItem(51, createNextPageButton(page));
        inv.setItem(49, createQuickBuyButton(player));
        inv.setItem(53, createPageIndicator(page));

        // Affichage des 6 paliers de cette page
        renderTierPage(inv, player, page);

		Map<String, String> data = new HashMap<>();
		data.put("view", "pass");
		data.put("page", String.valueOf(page));
		guiManager.registerOpenGUI(player, GUIType.BATTLE_PASS_MENU, inv, data);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.8f, 1.2f);
    }

    // ========================================
    // RENDERING DES PALIERS
    // ========================================

    private void renderTierPage(Inventory inv, Player player, int page) {
        int startTier = (page - 1) * 6 + 1;
        BattlePassManager bpm = plugin.getBattlePassManager();
        var playerData = bpm.getPlayerData(player.getUniqueId());
        int currentTier = bpm.getTier(player.getUniqueId());

        // Colonnes pour les 6 paliers: slots 10, 12, 14, 16, 28, 30
        int[] columns = {10, 12, 14, 16, 28, 30};

        for (int i = 0; i < 6; i++) {
            int tier = startTier + i;
            if (tier > 50) continue; // Au-delà du palier 50

            int column = columns[i];
            renderTierColumn(inv, player, tier, column, currentTier, playerData);
        }

        // Barres de progression au centre (ligne 3)
        renderProgressBars(inv, player, startTier, currentTier);
    }

    private void renderTierColumn(Inventory inv, Player player, int tier, int column, int currentTier, BattlePassManager.PlayerPassData playerData) {
        BattlePassManager bpm = plugin.getBattlePassManager();
        var rewards = bpm.getRewardsForTier(tier);

        // Ligne Premium (ligne 2)
        inv.setItem(column, createTierItem(player, tier, true, rewards, currentTier, playerData));

        // Ligne Gratuite (ligne 4)
        inv.setItem(column + 18, createTierItem(player, tier, false, rewards, currentTier, playerData));

        // Indicateur de palier (entre les deux)
        inv.setItem(column + 9, createTierIndicator(tier, currentTier));
    }

    private void renderProgressBars(Inventory inv, Player player, int startTier, int currentTier) {
        BattlePassManager bpm = plugin.getBattlePassManager();
        int progressInTier = bpm.getProgressInTier(player.getUniqueId());

        // Slots 19, 20, 21, 22, 23, 24 pour les barres de progression
        int[] barSlots = {19, 20, 21, 22, 23, 24};

        for (int i = 0; i < 6; i++) {
            int tier = startTier + i;
            if (tier > 50) continue;

            inv.setItem(barSlots[i], createProgressBar(tier, currentTier, progressInTier, tier == currentTier));
        }
    }

    // ========================================
    // CRÉATION DES ITEMS
    // ========================================

    private ItemStack createMainHeader(Player player) {
        BattlePassManager bpm = plugin.getBattlePassManager();
        int tier = bpm.getTier(player.getUniqueId());
        var playerData = bpm.getPlayerData(player.getUniqueId());

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§6⚔ §lPass de Combat §6Season " + bpm.getCurrentSeasonId());

        List<String> lore = Arrays.asList(
                "",
                "§7§m                                    ",
                "§7Palier actuel: §6" + tier + "§7/§650",
                "§7Premium: " + (playerData.premium() ? "§a✓ Actif" : "§c✗ Inactif"),
                "",
                "§7Durée de la saison: §f30 jours",
                "§7Fin: §e" + bpm.getSeasonEndDate(),
                "",
                "§7Récompenses disponibles: §a" + getAvailableRewardsCount(player),
                "§7§m                                    ",
                "",
                "§e▶ Sélectionnez une option ci-dessous"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return guiManager.addGlowEffect(item);
    }

    private ItemStack createVisualizationButton(Player player, int currentTier, BattlePassManager.PlayerPassData playerData) {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§a§l📋 Visualiser le Pass");

        int availableRewards = getAvailableRewardsCount(player);
        List<String> lore = Arrays.asList(
                "",
                "§7Consultez vos paliers et récupérez",
                "§7vos récompenses débloquées.",
                "",
                "§7Palier actuel: §6" + currentTier,
                "§7Premium: " + (playerData.premium() ? "§aActif" : "§cInactif"),
                "§7Récompenses prêtes: §e" + availableRewards,
                "",
                "§e▶ Cliquez pour ouvrir le Pass"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        if (availableRewards > 0) {
            item = guiManager.addGlowEffect(item);
        }

        return item;
    }

    private ItemStack createBuyPremiumButton(Player player, BattlePassManager.PlayerPassData playerData) {
        boolean hasPremium = playerData.premium();
        ItemStack item = new ItemStack(hasPremium ? Material.EMERALD_BLOCK : Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        if (hasPremium) {
            guiManager.applyName(meta, "§a§l💎 Premium Actif");
            List<String> lore = Arrays.asList(
                    "",
                    "§7Vous possédez déjà le Pass Premium",
                    "§7pour cette saison !",
                    "",
                    "§aAvantages Premium:",
                    "§f• §7Récompenses supplémentaires",
                    "§f• §7Ligne premium débloquée",
                    "§f• §7Contenu exclusif",
                    "",
                    "§a✓ Profitez de vos avantages !"
            );
            guiManager.applyLore(meta, lore);
            item.setItemMeta(meta);
        } else {
            guiManager.applyName(meta, "§6§l💎 Acheter Premium");

            // Calculer le prix (réduction VIP)
            boolean isVip = player.hasPermission("prisontycoon.vip");
            int basePrice = 5000;
            int price = isVip ? (int)(basePrice * 0.7) : basePrice;

            List<String> lore = Arrays.asList(
                    "",
                    "§7Débloquez la ligne §6Premium §7pour",
                    "§7accéder à des récompenses exclusives !",
                    "",
                    "§7Prix: §e" + price + " Beacons" + (isVip ? " §a(VIP -30%)" : ""),
                    "",
                    "§6Avantages Premium:",
                    "§f• §7Récompenses supplémentaires",
                    "§f• §7Ligne premium débloquée",
                    "§f• §7Contenu exclusif",
                    "",
                    "§e▶ Cliquez pour acheter"
            );
            guiManager.applyLore(meta, lore);
            item.setItemMeta(meta);
            item = guiManager.addGlowEffect(item);
        }

        return item;
    }

    private ItemStack createPassQuestsButton() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§d§l📝 Quêtes de Pass");

        List<String> lore = Arrays.asList(
                "",
                "§7Consultez les quêtes spéciales",
                "§7du Pass de Combat.",
                "",
                "§7Ces quêtes durent §e30 jours §7et",
                "§7donnent de nombreux points XP !",
                "",
                "§dTypes de quêtes:",
                "§f• §7Quêtes journalières",
                "§f• §7Quêtes hebdomadaires",
                "§f• §7Quêtes de temps de jeu",
                "§f• §7Quêtes spéciales Pass",
                "",
                "§e▶ Cliquez pour voir vos quêtes"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHelpButton() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§b§l❓ Aide");

        List<String> lore = Arrays.asList(
                "",
                "§7Besoin d'aide avec le Pass de Combat ?",
                "",
                "§bComment ça marche:",
                "§f• §7Gagnez des points via les quêtes",
                "§f• §7Débloquez des paliers (50 points/palier)",
                "§f• §7Récupérez vos récompenses",
                "§f• §750 paliers + bonus infini après",
                "",
                "§bConseils:",
                "§f• §7Faites vos quêtes quotidiennes",
                "§f• §7Le Premium double vos récompenses",
                "§f• §7Saison = 30 jours (renouvelée chaque mois)",
                "",
                "§e▶ Cliquez pour plus d'infos"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackToQuestsButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§7← Retour aux Quêtes");

        List<String> lore = Arrays.asList(
                "",
                "§7Retourner au menu principal",
                "§7des quêtes.",
                "",
                "§e▶ Cliquez pour revenir"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProgressHeader(Player player, int page) {
        BattlePassManager bpm = plugin.getBattlePassManager();
        int points = bpm.getPoints(player.getUniqueId());
        int tier = bpm.getTier(player.getUniqueId());
        int progressInTier = bpm.getProgressInTier(player.getUniqueId());

        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§6⚔ Season " + bpm.getCurrentSeasonId() + " §8(Page " + page + "/5)");

        // Barre de progression visuelle
        String progressBar = createProgressBarString(progressInTier, BattlePassManager.POINTS_PER_TIER);

        List<String> lore = Arrays.asList(
                "",
                "§7Palier actuel: §6" + tier + "§7/§650",
                "§7Progression: " + progressBar,
                "§7Points: §e" + progressInTier + "§7/§e" + BattlePassManager.POINTS_PER_TIER,
                "§7Total: §e" + points + " §7XP",
                "",
                "§7Paliers 51+: §61 Clé Rare §7par palier",
                "",
                "§e⚡ Gagnez des points via les quêtes !"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createTierItem(Player player, int tier, boolean premiumRow,
                                     BattlePassManager.TierRewards rewards, int currentTier,
                                     BattlePassManager.PlayerPassData playerData) {

        boolean reachable = currentTier >= tier;
        boolean alreadyClaimed = premiumRow ?
                playerData.claimedPremium().contains(tier) :
                playerData.claimedFree().contains(tier);
        boolean canClaim = reachable && !alreadyClaimed && (!premiumRow || playerData.premium());
        boolean premiumRequired = premiumRow && !playerData.premium();

        // Matériau selon l'état
        Material material;
        if (alreadyClaimed) {
            material = premiumRow ? Material.GOLD_BLOCK : Material.IRON_BLOCK;
        } else if (canClaim) {
            material = premiumRow ? Material.GLOWSTONE : Material.QUARTZ_BLOCK;
        } else if (reachable && premiumRequired) {
            material = Material.BARRIER;
        } else {
            material = premiumRow ? Material.ORANGE_CONCRETE : Material.GRAY_CONCRETE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String title = (premiumRow ? "§6⭐ Premium" : "§7⚪ Gratuit") + " §8- §fPalier §e" + tier;
        guiManager.applyName(meta, title);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(premiumRow ? "§6▸ Récompenses Premium:" : "§7▸ Récompenses Gratuites:");

        var reward = premiumRow ? rewards.premium() : rewards.free();
        lore.add("§f  • " + reward.getDescription());
        lore.add("");

        // État
        if (alreadyClaimed) {
            lore.add("§a✓ Récompense réclamée");
        } else if (canClaim) {
            lore.add("§e⚡ Prêt à réclamer !");
            lore.add("§e▶ Cliquez pour récupérer");
        } else if (reachable && premiumRequired) {
            lore.add("§c🔒 Premium requis");
            lore.add("§7Achetez le Pass Premium");
        } else {
            lore.add("§c🔒 Palier " + tier + " requis");
            lore.add("§7Gagnez plus de points XP");
        }

        // ID caché pour le parsing (FORMAT AMÉLIORÉ)
        lore.add("§8[TIER_DATA:" + tier + ":" + (premiumRow ? "P" : "F") + "]");

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);

        if (canClaim) {
            item = guiManager.addGlowEffect(item);
        }

        return item;
    }

    private ItemStack createTierIndicator(int tier, int currentTier) {
        boolean unlocked = currentTier >= tier;
        ItemStack item = new ItemStack(unlocked ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, (unlocked ? "§a" : "§7") + "Palier " + tier);

        List<String> lore = Arrays.asList(
                "",
                unlocked ? "§a✓ Palier débloqué" : "§7○ Palier verrouillé",
                "§7Requis: §e" + (tier * BattlePassManager.POINTS_PER_TIER) + " XP"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createProgressBar(int tier, int currentTier, int progressInTier, boolean isCurrentTier) {
        ItemStack item;
        if (tier < currentTier) {
            item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE); // Complété
        } else if (tier > currentTier) {
            item = new ItemStack(Material.RED_STAINED_GLASS_PANE); // Non atteint
        } else {
            // Palier actuel - progression partielle
            if (progressInTier >= BattlePassManager.POINTS_PER_TIER) {
                item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
            } else {
                item = new ItemStack(Material.YELLOW_STAINED_GLASS_PANE);
            }
        }

        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§7Progression Palier " + tier);

        if (isCurrentTier) {
            List<String> lore = Arrays.asList(
                    "",
                    "§7Progression: §e" + progressInTier + "§7/§e" + BattlePassManager.POINTS_PER_TIER,
                    createProgressBarString(progressInTier, BattlePassManager.POINTS_PER_TIER),
                    "",
                    "§7Manquant: §c" + (BattlePassManager.POINTS_PER_TIER - progressInTier) + " XP"
            );
            guiManager.applyLore(meta, lore);
        } else {
            guiManager.applyLore(meta, Arrays.asList("", tier < currentTier ? "§a✓ Terminé" : "§c✗ À débloquer"));
        }

        item.setItemMeta(meta);
        return item;
    }

    // ========================================
    // GESTION DES CLICS
    // ========================================

	public void handleClick(Player player, int slot, ItemStack item) {
		if (item == null || item.getType() == Material.AIR) return;

		GUIType openType = guiManager.getOpenGUIType(player);
		if (openType == null) return;

		if (openType == GUIType.BATTLE_PASS_MAIN_MENU) {
			handleMainMenuClick(player, slot);
			return;
		}

		if (openType == GUIType.BATTLE_PASS_MENU) {
			String view = guiManager.getGUIData(player, "view");
			if ("help".equals(view)) {
				if (slot == 40) {
					openMainMenu(player);
				} else {
					player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
				}
				return;
			}

			int page = 1;
			try {
				String pageStr = guiManager.getGUIData(player, "page");
				if (pageStr != null) page = Integer.parseInt(pageStr);
			} catch (NumberFormatException ignored) {}

			// Fallback: si pas de data, essaie de lire depuis le titre
			if (page == 1) {
				String title = plugin.getGUIManager().getLegacyTitle(player.getOpenInventory());
				if (title.contains("Page ")) {
					try {
						int start = title.indexOf("Page ") + 5;
						int end = title.indexOf("/5)", start);
						if (start > 4 && end > start) page = Integer.parseInt(title.substring(start, end));
					} catch (Exception ignored) {}
				}
			}

			handlePassClick(player, slot, item, page);
		}
	}

    private void handleMainMenuClick(Player player, int slot) {
        switch (slot) {
            case 19: // Visualisation
                openPass(player, 1);
                break;
            case 21: // Acheter Premium
                handlePremiumPurchase(player);
                break;
            case 23: // Quêtes de Pass
                plugin.getQuestsGUI().openMainMenu(player);
                break;
            case 25: // Aide
                openHelpMenu(player);
                break;
            case 40: // Retour
                plugin.getQuestsGUI().openMainMenu(player);
                break;
        }
    }

	private void handlePassClick(Player player, int slot, ItemStack item, int page) {
		switch (slot) {
            case 45: // Retour menu principal
                openMainMenu(player);
                break;
            case 47: // Page précédente
                openPass(player, Math.max(1, page - 1));
                break;
            case 51: // Page suivante
                openPass(player, Math.min(5, page + 1));
                break;
            case 49: // Achat rapide Premium
                handlePremiumPurchase(player);
                break;
            default:
                // Tentative de claim d'une récompense
                handleRewardClaim(player, item, page);
                break;
        }
    }

    private void handleRewardClaim(Player player, ItemStack item, int page) {
        if (!item.hasItemMeta() || item.getItemMeta().lore() == null) return;

        List<String> lore = item.getItemMeta().lore().stream()
                .map(c -> LegacyComponentSerializer.legacySection().serialize(c))
                .toList();

        // Parsing amélioré et sécurisé
        String tierDataLine = null;
        for (String line : lore) {
            if (line.startsWith("§8[TIER_DATA:") && line.endsWith("]")) {
                tierDataLine = line;
                break;
            }
        }

        if (tierDataLine == null) return;

        try {
            // Format: §8[TIER_DATA:TIER:TYPE]
            String data = tierDataLine.substring(13, tierDataLine.length() - 1); // Retire §8[TIER_DATA: et ]
            String[] parts = data.split(":");

            if (parts.length != 2) {
                plugin.getLogger().warning("Format TIER_DATA invalide: " + tierDataLine);
                return;
            }

            int tier = Integer.parseInt(parts[0]);
            boolean premiumRow = "P".equals(parts[1]);

            // Tentative de claim
            BattlePassManager bpm = plugin.getBattlePassManager();
            boolean success = premiumRow ?
                    bpm.claimPremium(player, tier) :
                    bpm.claimFree(player, tier);

            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                player.sendMessage("§a✓ Récompense du palier " + tier + " réclamée !");

                // Rafraîchir la page
                openPass(player, page);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
                player.sendMessage("§c✗ Impossible de réclamer cette récompense");
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Erreur parsing tier data: " + e.getMessage());
            player.sendMessage("§c✗ Erreur lors de la réclamation");
        }
    }

    private void handlePremiumPurchase(Player player) {
        BattlePassManager bpm = plugin.getBattlePassManager();
        boolean success = bpm.purchasePremium(player);

        if (success) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1.4f);
            player.sendMessage("§a✓ Pass Premium débloqué pour cette saison !");

            // Rafraîchir l'interface
            openMainMenu(player);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            player.sendMessage("§c✗ Achat impossible (fonds insuffisants ou déjà possédé)");
        }
    }

    // ========================================
    // MÉTHODES UTILITAIRES
    // ========================================

    private void fillDecorativeBorders(Inventory inv) {
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        guiManager.applyName(meta, "§8");
        border.setItemMeta(meta);

        // Bordures partielles pour laisser place aux paliers
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, border); // Top sauf header
        }
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, border.clone()); // Bottom
        }
        // Côtés sélectifs
        inv.setItem(9, border.clone());
        inv.setItem(17, border.clone());
        inv.setItem(18, border.clone());
        inv.setItem(26, border.clone());
        inv.setItem(27, border.clone());
        inv.setItem(35, border.clone());
        inv.setItem(36, border.clone());
        inv.setItem(44, border.clone());
    }

    private String createProgressBarString(int current, int max) {
        int bars = 10;
        int filled = (int) ((double) current / max * bars);
        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                sb.append("§a█");
            } else {
                sb.append("§7░");
            }
        }
        sb.append("§7]");
        return sb.toString();
    }

    private int getAvailableRewardsCount(Player player) {
        BattlePassManager bpm = plugin.getBattlePassManager();
        var playerData = bpm.getPlayerData(player.getUniqueId());
        int currentTier = bpm.getTier(player.getUniqueId());
        int count = 0;

        for (int tier = 1; tier <= currentTier; tier++) {
            if (!playerData.claimedFree().contains(tier)) count++;
            if (playerData.premium() && !playerData.claimedPremium().contains(tier)) count++;
        }

        return count;
    }

    private ItemStack createStatsItem(Player player) {
        BattlePassManager bpm = plugin.getBattlePassManager();
        int points = bpm.getPoints(player.getUniqueId());
        int tier = bpm.getTier(player.getUniqueId());
        var playerData = bpm.getPlayerData(player.getUniqueId());

        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§e📊 Statistiques");

        List<String> lore = Arrays.asList(
                "",
                "§7Points totaux: §e" + points,
                "§7Palier actuel: §6" + tier,
                "§7Récompenses réclamées:",
                "§f  • §7Gratuites: §a" + playerData.claimedFree().size(),
                "§f  • §7Premium: §6" + playerData.claimedPremium().size(),
                "",
                "§7Statut: " + (playerData.premium() ? "§6Premium" : "§7Gratuit")
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSeasonInfoItem() {
        BattlePassManager bpm = plugin.getBattlePassManager();
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§b⏰ Informations Saison");

        List<String> lore = Arrays.asList(
                "",
                "§7Saison: §b" + bpm.getCurrentSeasonId(),
                "§7Début: §f" + bpm.getSeasonStartDate(),
                "§7Fin: §f" + bpm.getSeasonEndDate(),
                "",
                "§7Durée totale: §e30 jours",
                "§7Paliers disponibles: §650",
                "§7Bonus après 50: §6Clés Rares"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private void openHelpMenu(Player player) {
        Inventory inv = guiManager.createInventory(45, "§b❓ §lAide - Pass de Combat");
        guiManager.fillBorders(inv);

        // Informations détaillées sur le fonctionnement
        inv.setItem(13, createDetailedHelpItem());
        inv.setItem(40, createBackToMainMenuButton());

		Map<String, String> data = new HashMap<>();
		data.put("view", "help");
		guiManager.registerOpenGUI(player, GUIType.BATTLE_PASS_MENU, inv, data);
        player.openInventory(inv);
    }

    private ItemStack createDetailedHelpItem() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§b📚 Guide Complet du Pass de Combat");

        List<String> lore = Arrays.asList(
                "",
                "§6⚔ Comment ça marche:",
                "§7• Gagnez des points XP via les quêtes",
                "§7• Chaque 50 points = 1 nouveau palier",
                "§7• 50 paliers principaux + bonus infini",
                "",
                "§6💎 Pass Premium:",
                "§7• Débloque la ligne de récompenses premium",
                "§7• Prix réduit pour les VIP (-30%)",
                "§7• Récompenses exclusives et doublées",
                "",
                "§6📅 Système de Saisons:",
                "§7• Chaque saison dure 30 jours",
                "§7• Nouvelle saison = remise à zéro",
                "§7• Démarrage automatique le 1er du mois",
                "",
                "§6🎯 Sources de Points XP:",
                "§7• Quêtes journalières (20-50 points)",
                "§7• Quêtes hebdomadaires (100-200 points)",
                "§7• Quêtes spéciales Pass (50-100 points)",
                "§7• Quêtes de temps de jeu (bonus)",
                "",
                "§6🎁 Après le Palier 50:",
                "§7• Chaque palier supplémentaire = 1 Clé Rare",
                "§7• Progression infinie possible"
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return guiManager.addGlowEffect(item);
    }

    // Boutons de navigation améliorés
    private ItemStack createBackToMainMenuButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§7← Menu Principal");
        guiManager.applyLore(meta, Arrays.asList("", "§7Retourner au menu principal", "§7du Pass de Combat.", "", "§e▶ Cliquez pour revenir"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPrevPageButton(int currentPage) {
        ItemStack item = new ItemStack(Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§7← Page Précédente");
        guiManager.applyLore(meta, Arrays.asList("", "§7Page actuelle: §e" + currentPage + "§7/§e5",
                currentPage > 1 ? "§e▶ Page " + (currentPage - 1) : "§cDéjà à la première page"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPageButton(int currentPage) {
        ItemStack item = new ItemStack(Material.TIPPED_ARROW);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§7Page Suivante →");
        guiManager.applyLore(meta, Arrays.asList("", "§7Page actuelle: §e" + currentPage + "§7/§e5",
                currentPage < 5 ? "§e▶ Page " + (currentPage + 1) : "§cDéjà à la dernière page"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createQuickBuyButton(Player player) {
        boolean hasPremium = plugin.getBattlePassManager().hasPremium(player.getUniqueId());
        ItemStack item = new ItemStack(hasPremium ? Material.EMERALD_BLOCK : Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, hasPremium ? "§a💎 Premium Actif" : "§6💎 Achat Rapide Premium");
        guiManager.applyLore(meta, Arrays.asList("", hasPremium ? "§7Vous avez déjà le Premium !" : "§7Achetez le Premium rapidement",
                "", hasPremium ? "§a✓ Profitez de vos avantages" : "§e▶ Cliquez pour acheter"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageIndicator(int page) {
        ItemStack item = new ItemStack(Material.MAP);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§6📍 Page " + page + "/5");
        guiManager.applyLore(meta, Arrays.asList("", "§7Vous consultez la page §e" + page,
                "§7Paliers §6" + ((page-1)*6+1) + " §7à §6" + Math.min(50, page*6)));
        item.setItemMeta(meta);
        return item;
    }
}