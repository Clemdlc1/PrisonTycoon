package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.quests.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Interface graphique ultra-améliorée pour le système de quêtes
 * Inclut les quêtes journalières, hebdomadaires, collectionneur de blocs et quêtes d'avancement
 */
public class QuestsGUI {

    // Slots de navigation principaux
    private static final int DAILY_QUESTS_SLOT = 11;
    private static final int WEEKLY_QUESTS_SLOT = 15;
    private static final int BLOCK_COLLECTOR_SLOT = 13;
    private static final int BATTLE_PASS_SLOT = 33;
    private static final int ADVANCEMENT_QUESTS_SLOT = 29;
    // Slots utilitaires
    private static final int REFRESH_SLOT = 40;
    private static final int CLOSE_SLOT = 44;
    private final PrisonTycoon plugin;
    private final GUIManager guiManager;

    public QuestsGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
    }

    /**
     * Ouvre le menu principal des quêtes
     */
    public void openMainMenu(Player player) {
        Inventory gui = guiManager.createInventory(45, "§6✦ §lSYSTÈME DE QUÊTES §6✦");

        // Remplir avec du verre coloré
        plugin.getGUIManager().fillBorders(gui);

        // Titre central décoratif
        gui.setItem(4, createTitleItem());

        // Navigation principale
        gui.setItem(DAILY_QUESTS_SLOT, createDailyQuestsButton(player));
        gui.setItem(WEEKLY_QUESTS_SLOT, createWeeklyQuestsButton(player));
        gui.setItem(BLOCK_COLLECTOR_SLOT, createBlockCollectorButton(player));
        gui.setItem(BATTLE_PASS_SLOT, createBattlePassButton(player));
        gui.setItem(ADVANCEMENT_QUESTS_SLOT, createAdvancementQuestsButton(player));

        // Utilitaires
        gui.setItem(REFRESH_SLOT, createRefreshButton());
        gui.setItem(CLOSE_SLOT, createCloseButton());

        guiManager.registerOpenGUI(player, GUIType.QUESTS_MENU, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    /**
     * Ouvre le menu des quêtes journalières
     */
    public void openDailyQuests(Player player) {
        openQuestCategory(player, QuestCategory.DAILY);
    }

    /**
     * Ouvre le menu des quêtes hebdomadaires
     */
    public void openWeeklyQuests(Player player) {
        openQuestCategory(player, QuestCategory.WEEKLY);
    }

    /**
     * Ouvre une catégorie de quêtes spécifique
     */
    private void openQuestCategory(Player player, QuestCategory category) {
        String title = category == QuestCategory.DAILY ?
                "§e⏰ §lQUÊTES JOURNALIÈRES" : "§d📅 §lQUÊTES HEBDOMADAIRES";

        Inventory gui = guiManager.createInventory(54, title);
        plugin.getGUIManager().fillBorders(gui);

        // Header avec informations
        gui.setItem(4, createCategoryInfoItem(player, category));

        // Bouton retour
        gui.setItem(45, createBackToMainButton());

        // Bouton récompense bonus
        gui.setItem(49, category == QuestCategory.DAILY ?
                createDailyBonusReward(player) : createWeeklyBonusReward(player));

        // Remplir avec les quêtes
        fillQuestSlots(gui, player, category);

        Map<String, String> data = new HashMap<>();
        data.put("category", category.name());
        guiManager.registerOpenGUI(player, GUIType.QUESTS_MENU, gui, data);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.0f);
    }

    /**
     * Ouvre le menu du collectionneur de blocs amélioré
     */
    public void openBlockCollector(Player player) {
        Inventory gui = guiManager.createInventory(54, "§a⛏ §lCOLLECTIONNEUR DE BLOCS");

        plugin.getGUIManager().fillBorders(gui);
        gui.setItem(4, createBlockCollectorInfoItem(player));
        gui.setItem(45, createBackToMainButton());
        gui.setItem(49, createBlockStatsButton(player));

        // Remplir avec les statistiques de blocs
        fillBlockCollectorSlots(gui, player);

        Map<String, String> data = new HashMap<>();
        data.put("view", "main");
        guiManager.registerOpenGUI(player, GUIType.BLOCK_COLLECTOR, gui, data);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.3f, 1.5f);
    }

    /**
     * Gère les clics dans l'interface
     */
    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;

        String viewData = guiManager.getGUIData(player, "view");
        String categoryData = guiManager.getGUIData(player, "category");

        // Navigation principale
        if (slot == DAILY_QUESTS_SLOT) {
            openDailyQuests(player);
            return;
        }

        if (slot == WEEKLY_QUESTS_SLOT) {
            openWeeklyQuests(player);
            return;
        }

        if (slot == BLOCK_COLLECTOR_SLOT) {
            openBlockCollector(player);
            return;
        }
        
        if (slot == ADVANCEMENT_QUESTS_SLOT) {
            openAdvancementQuests(player);
            return;
        }

        if (slot == BATTLE_PASS_SLOT) {
            openBattlePass(player);
            return;
        }

        // Boutons utilitaires
        if (slot == 45) { // Retour
            openMainMenu(player);
            return;
        }

        if (slot == REFRESH_SLOT) {
            refreshCurrentView(player);
            return;
        }

        if (slot == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

		// Gestion des clics sur les quêtes et bonus dans une catégorie
		if (categoryData != null) {
			QuestCategory category = QuestCategory.valueOf(categoryData);
			if (slot == 49) {
				claimBonusReward(player, category);
				return;
			}
			handleQuestClick(player, slot, item, category);
			return;
		}

        // Gestion du collectionneur de blocs
        if ("main".equals(viewData)) {
            handleBlockCollectorClick(player, slot, item);
            return;
        }
    }

    // ================================================================================================
    // CRÉATION DES ITEMS DE L'INTERFACE
    // ================================================================================================

    private ItemStack createTitleItem() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§6✦ §lSYSTÈME DE QUÊTES §6✦");
        List<String> lore = Arrays.asList(
                "",
                "§7Complétez des quêtes pour obtenir des récompenses !",
                "§7» §eQuêtes Journalières §7- Se renouvellent chaque jour",
                "§7» §dQuêtes Hebdomadaires §7- Se renouvellent chaque semaine",
                "§7» §aCollectionneur §7- Collectez et progressez",
                "§7» §bQuêtes d'Avancement §7- Objectifs à long terme",
                ""
        );
        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDailyQuestsButton(Player player) {
        PlayerQuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId());
        List<QuestDefinition> dailyQuests = plugin.getQuestManager().getActiveQuestsForPlayer(player.getUniqueId(), QuestCategory.DAILY);

        int completed = 0;
        int total = dailyQuests.size();

        for (QuestDefinition quest : dailyQuests) {
            if (progress.get(quest.getId()) >= quest.getTarget()) {
                completed++;
            }
        }

        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§e⏰ §lQuêtes Journalières");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Progression: §e" + completed + "§7/§e" + total);
        lore.add("§7Se renouvellent chaque jour à §e00h00");
        lore.add("");

        if (completed == total && total > 0) {
            lore.add("§a✓ Toutes les quêtes terminées !");
            lore.add("§a▶ Récompense bonus disponible !");
        } else {
            lore.add("§7Terminez toutes les quêtes pour obtenir");
            lore.add("§7une §6Clé Rare §7en bonus !");
        }

        lore.add("");
        lore.add("§e▶ Cliquez pour voir les quêtes");

        // Enchantement si toutes terminées
        if (completed == total && total > 0) {
            item = new ItemStack(Material.GOLDEN_APPLE);
            item = guiManager.addGlowEffect(item);
        }

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWeeklyQuestsButton(Player player) {
        PlayerQuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId());
        List<QuestDefinition> weeklyQuests = plugin.getQuestManager().getActiveQuestsForPlayer(player.getUniqueId(), QuestCategory.WEEKLY);

        int completed = 0;
        int total = weeklyQuests.size();

        for (QuestDefinition quest : weeklyQuests) {
            if (progress.get(quest.getId()) >= quest.getTarget()) {
                completed++;
            }
        }

        ItemStack item = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§d📅 §lQuêtes Hebdomadaires");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Progression: §d" + completed + "§7/§d" + total);
        lore.add("§7Se renouvellent chaque §dlundi");
        lore.add("");

        if (completed == total && total > 0) {
            lore.add("§a✓ Toutes les quêtes terminées !");
            lore.add("§a▶ Récompense bonus disponible !");
        } else {
            lore.add("§7Terminez toutes les quêtes pour obtenir");
            lore.add("§7une §6Clé Légendaire §7en bonus !");
        }

        lore.add("");
        lore.add("§d▶ Cliquez pour voir les quêtes");

        // Enchantement si toutes terminées
        if (completed == total && total > 0) {
            item = new ItemStack(Material.GOLDEN_APPLE);
            item = guiManager.addGlowEffect(item);
        }

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBlockCollectorButton(Player player) {
        Map<Material, Long> stats = plugin.getBlockCollectorManager().getStats(player.getUniqueId());

        long totalBlocks = stats.values().stream().mapToLong(Long::longValue).sum();
        int totalTiers = 0;

        for (Material material : stats.keySet()) {
            totalTiers += plugin.getBlockCollectorManager().getTierFor(player, material);
        }

        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§a⛏ §lCollectionneur de Blocs");

        List<String> lore = Arrays.asList(
                "",
                "§7Blocs détruits au total: §b" + formatNumber(totalBlocks),
                "§7Paliers obtenus: §e" + totalTiers,
                "§7Types de blocs: §a" + stats.size(),
                "",
                "§7Détruisez des blocs en mine pour",
                "§7progresser et obtenir des récompenses !",
                "",
                "§a▶ Cliquez pour voir vos statistiques"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBattlePassButton(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§6⚔ §lPass de Combat");

        var bpm = plugin.getBattlePassManager();
        int tier = bpm.getTier(player.getUniqueId());
        int points = bpm.getPoints(player.getUniqueId());
        boolean premium = bpm.hasPremium(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Saison: §f" + bpm.getCurrentSeasonId());
        lore.add("§7Statut: " + (premium ? "§aPremium" : "§7Gratuit"));
        lore.add("§7Palier actuel: §e" + tier + " §7(§e" + (points % fr.prisontycoon.managers.BattlePassManager.POINTS_PER_TIER) + "§7/§e" + fr.prisontycoon.managers.BattlePassManager.POINTS_PER_TIER + ")");
        lore.add("");
        lore.add("§e▶ Cliquez pour ouvrir");

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAdvancementQuestsButton(Player player) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§b✨ §lQuêtes d'Avancement");

        List<String> lore = Arrays.asList(
                "",
                "§7Quêtes d'avancement à long terme",
                "§7avec des récompenses exceptionnelles !",
                "",
                "§c⚠ Bientôt disponible ⚠",
                "",
                "§7En attendant, concentrez-vous sur",
                "§7les quêtes journalières et hebdomadaires !",
                "",
                "§8▶ Fonctionnalité en développement"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDailyBonusReward(Player player) {
        PlayerQuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId());
        List<QuestDefinition> dailyQuests = plugin.getQuestManager().getActiveQuestsForPlayer(player.getUniqueId(), QuestCategory.DAILY);

        boolean allCompleted = dailyQuests.stream()
                .allMatch(quest -> progress.get(quest.getId()) >= quest.getTarget());

        ItemStack item = new ItemStack(allCompleted ? Material.GOLDEN_APPLE : Material.APPLE);
        ItemMeta meta = item.getItemMeta();

        if (allCompleted) {
            guiManager.applyName(meta, "§6★ §lRécompense Journalière");
            List<String> lore = Arrays.asList(
                    "",
                    "§a✓ Toutes les quêtes journalières terminées !",
                    "",
                    "§7Récompense: §6Clé Rare",
                    "§7» Ouvre des coffres rares",
                    "§7» Objets et équipements de qualité",
                    "",
                    "§e▶ Cliquez pour réclamer !"
            );
            guiManager.applyLore(meta, lore);
            item = guiManager.addGlowEffect(item);
        } else {
            guiManager.applyName(meta, "§7★ Récompense Journalière");
            List<String> lore = Arrays.asList(
                    "",
                    "§7Terminez toutes les quêtes journalières",
                    "§7pour obtenir cette récompense !",
                    "",
                    "§7Récompense: §6Clé Rare",
                    "§7» Ouvre des coffres rares",
                    "§7» Objets et équipements de qualité",
                    "",
                    "§c✗ Non disponible"
            );
            guiManager.applyLore(meta, lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createWeeklyBonusReward(Player player) {
        PlayerQuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId());
        List<QuestDefinition> weeklyQuests = plugin.getQuestManager().getActiveQuestsForPlayer(player.getUniqueId(), QuestCategory.WEEKLY);

        boolean allCompleted = weeklyQuests.stream()
                .allMatch(quest -> progress.get(quest.getId()) >= quest.getTarget());

        ItemStack item = new ItemStack(allCompleted ? Material.ENCHANTED_GOLDEN_APPLE : Material.GOLDEN_APPLE);
        ItemMeta meta = item.getItemMeta();

        if (allCompleted) {
            guiManager.applyName(meta, "§d★ §lRécompense Hebdomadaire");
            List<String> lore = Arrays.asList(
                    "",
                    "§a✓ Toutes les quêtes hebdomadaires terminées !",
                    "",
                    "§7Récompense: §6Clé Légendaire",
                    "§7» Ouvre des coffres légendaires",
                    "§7» Objets rares et puissants",
                    "",
                    "§e▶ Cliquez pour réclamer !"
            );
            guiManager.applyLore(meta, lore);
            item = guiManager.addGlowEffect(item);
        } else {
            guiManager.applyName(meta, "§7★ Récompense Hebdomadaire");
            List<String> lore = Arrays.asList(
                    "",
                    "§7Terminez toutes les quêtes hebdomadaires",
                    "§7pour obtenir cette récompense !",
                    "",
                    "§7Récompense: §6Clé Légendaire",
                    "§7» Ouvre des coffres légendaires",
                    "§7» Objets rares et puissants",
                    "",
                    "§c✗ Non disponible"
            );
            guiManager.applyLore(meta, lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    // ================================================================================================
    // REMPLISSAGE ET GESTION DES SLOTS
    // ================================================================================================

    private void fillQuestSlots(Inventory gui, Player player, QuestCategory category) {
        List<QuestDefinition> quests = plugin.getQuestManager().getActiveQuestsForPlayer(player.getUniqueId(), category);
        PlayerQuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId());

        int[] questSlots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int index = 0;

        for (QuestDefinition quest : quests) {
            if (index >= questSlots.length) break;

            ItemStack questItem = createQuestItem(quest, progress, player);
            gui.setItem(questSlots[index], questItem);
            index++;
        }
    }

    private void fillBlockCollectorSlots(Inventory gui, Player player) {
        Map<Material, Long> stats = plugin.getBlockCollectorManager().getStats(player.getUniqueId());

        List<Map.Entry<Material, Long>> entries = stats.entrySet().stream()
                .sorted(Map.Entry.<Material, Long>comparingByValue().reversed())
                .limit(28)
                .toList();

        int[] collectorSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int index = 0;
        for (Map.Entry<Material, Long> entry : entries) {
            if (index >= collectorSlots.length) break;

            ItemStack blockItem = createBlockCollectorItem(entry.getKey(), entry.getValue(), player);
            gui.setItem(collectorSlots[index], blockItem);
            index++;
        }
    }

    private ItemStack createQuestItem(QuestDefinition quest, PlayerQuestProgress progress, Player player) {
        int currentProgress = progress.get(quest.getId());
        int target = quest.getTarget();
        boolean completed = currentProgress >= target;
        boolean claimed = progress.isClaimed(quest.getId());

        // Choix du matériau selon le type de quête
        Material material = getQuestMaterial(quest.getType());
        ItemStack item = new ItemStack(material);

        if (claimed) {
            item.withType(Material.LIME_STAINED_GLASS_PANE);
        } else if (completed) {
            item = guiManager.addGlowEffect(item);
        }

        ItemMeta meta = item.getItemMeta();

        // Nom de la quête
        String questName = getQuestDisplayName(quest);
        String statusColor = claimed ? "§a" : (completed ? "§e" : "§7");
        guiManager.applyName(meta, statusColor + questName);

        // Lore détaillée
        List<String> lore = new ArrayList<>();
        lore.add("");

        // Description de l'objectif
        lore.add("§7Objectif: §f" + getQuestDescription(quest));
        lore.add("§7Progression: " + statusColor + currentProgress + "§7/§f" + target);

        // Barre de progression visuelle
        lore.add(createProgressBar(currentProgress, target, 20));
        lore.add("");

        // Récompenses détaillées
        lore.add("§7Récompenses:");
        QuestRewards rewards = quest.getRewards();

        if (rewards.getBeacons() > 0) {
            lore.add("§7» §6" + formatNumber(rewards.getBeacons()) + " Beacons");
        }
        if (rewards.getJobXp() > 0) {
            lore.add("§7» §d+" + rewards.getJobXp() + " XP Métier");
        }
        if (rewards.getVoucherType() != null) {
            lore.add("§7» §bVoucher " + rewards.getVoucherType().name() + " T" + rewards.getVoucherTier());
        }
        if (rewards.getBoostType() != null) {
            lore.add("§7» §aBoost " + rewards.getBoostType().name() + " " +
                    rewards.getBoostPercent() + "% (" + rewards.getBoostMinutes() + "min)");
        }

        lore.add("");

        // État et action
        if (claimed) {
            lore.add("§a✓ Récompense réclamée");
        } else if (completed) {
            lore.add("§e▶ Cliquez pour réclamer !");
        } else {
            lore.add("§c✗ Objectif non atteint");
        }

        // ID pour gestion interne
        lore.add("§8ID: " + quest.getId());

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBlockCollectorItem(Material blockType, Long count, Player player) {
        int tier = plugin.getBlockCollectorManager().getTierFor(player, blockType);
        long toNext = plugin.getBlockCollectorManager().getProgressToNext(player, blockType);

        ItemStack item = new ItemStack(blockType);
        ItemMeta meta = item.getItemMeta();

        guiManager.applyName(meta, "§f" + formatMaterialName(blockType));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Blocs détruits: §b" + formatNumber(count));
        lore.add("§7Palier actuel: §e" + tier + "§7/§e100");

        if (tier < 100) {
            long nextReq = plugin.getBlockCollectorManager().getNextTierRequirement(tier + 1);
            lore.add("§7Prochain palier: §a" + (nextReq - toNext) + "§7/§a" + nextReq);
            lore.add(createProgressBar((int) (nextReq - toNext), (int) nextReq, 15));
            lore.add("§7Restant: §c" + formatNumber(toNext));
        } else {
            lore.add("§a★ Palier maximum atteint !");
        }

        lore.add("");

        // Top 3 pour ce bloc
        List<Map.Entry<UUID, Long>> topPlayers = plugin.getBlockCollectorManager().getTopFor(blockType, 3);
        if (!topPlayers.isEmpty()) {
            lore.add("§7Top 3 du serveur:");
            int position = 1;
            for (Map.Entry<UUID, Long> entry : topPlayers) {
                String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (playerName == null) playerName = "Joueur inconnu";

                String positionColor = position == 1 ? "§6" : (position == 2 ? "§7" : "§c");
                lore.add("§7 " + position + ". " + positionColor + playerName + " §8(" + formatNumber(entry.getValue()) + ")");
                position++;
            }
        }

        lore.add("");

        boolean canClaim = plugin.getBlockCollectorManager().canClaim(player, blockType);
        if (canClaim) {
            lore.add("§e▶ Cliquez pour valider le palier !");
            item = guiManager.addGlowEffect(item);
        } else {
            lore.add("§7Continuez à miner pour progresser");
        }

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    // ================================================================================================
    // GESTION DES CLICS
    // ================================================================================================

    private void handleQuestClick(Player player, int slot, ItemStack item, QuestCategory category) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasLore()) return;

        List<String> lore = item.getItemMeta().lore().stream()
                .map(component -> net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component))
                .toList();

        // Trouver l'ID de la quête
        String questId = null;
        for (String line : lore) {
            if (line.startsWith("§8ID: ")) {
                questId = line.substring("§8ID: ".length());
                break;
            }
        }

        if (questId != null) {
            boolean success = plugin.getQuestManager().claim(player, questId);
            if (success) {
                player.sendMessage("§a✓ Récompense de quête réclamée avec succès !");
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

                // Vérifier les bonus
                checkAndGrantCategoryBonus(player, category);

                // Rafraîchir l'interface
                openQuestCategory(player, category);
            } else {
                player.sendMessage("§c✗ Impossible de réclamer cette récompense !");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            }
        }
    }

    private void handleBlockCollectorClick(Player player, int slot, ItemStack item) {
        if (item.getType() == Material.AIR) return;

        Material blockType = item.getType();
        boolean success = plugin.getBlockCollectorManager().claimTier(player, blockType);

        if (success) {
            player.sendMessage("§a✓ Palier validé pour " + formatMaterialName(blockType) + " !");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
            openBlockCollector(player);
        } else {
            player.sendMessage("§c✗ Aucun palier disponible à valider pour ce bloc !");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        }
    }

    private void claimBonusReward(Player player, QuestCategory category) {
        List<QuestDefinition> quests = plugin.getQuestManager().getActiveQuestsForPlayer(player.getUniqueId(), category);
        PlayerQuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId());

        boolean allCompleted = quests.stream()
                .allMatch(quest -> progress.get(quest.getId()) >= quest.getTarget());

        if (allCompleted) {
            plugin.getQuestManager().grantAllDoneBonus(player, category);
            String keyType = category == QuestCategory.DAILY ? "Rare" : "Légendaire";

            player.sendMessage("§a✓ Vous avez reçu une Clé " + keyType + " !");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);

            openMainMenu(player);
        } else {
            player.sendMessage("§c✗ Vous devez terminer toutes les quêtes avant de réclamer le bonus !");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        }
    }

    // ================================================================================================
    // MÉTHODES UTILITAIRES
    // ================================================================================================

    private String createProgressBar(int current, int max, int length) {
        if (max <= 0) return "§8[" + "§7".repeat(length) + "§8]";

        int filled = Math.min(length, (current * length) / max);
        int empty = length - filled;

        return "§8[" + "§a" + "█".repeat(filled) +
                "§7" + "█".repeat(empty) +
                "§8]";
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fG", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (!formatted.isEmpty()) formatted.append(" ");
            formatted.append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1));
        }

        return formatted.toString();
    }

    private Material getQuestMaterial(QuestType type) {
        return switch (type) {
            case KILL_PLAYERS -> Material.DIAMOND_SWORD;
            case KILL_MONSTERS -> Material.IRON_SWORD;
            case MINE_BEACONS -> Material.BEACON;
            case UPGRADE_ENCHANTMENTS -> Material.ENCHANTING_TABLE;
            case CAPTURE_OUTPOST -> Material.BLACK_BANNER;
            case HOLD_OUTPOST_MINUTES -> Material.CLOCK;
            case WIN_SPONTANEOUS_EVENT -> Material.FIREWORK_ROCKET;
            case PARTICIPATE_BOSS -> Material.WITHER_SKELETON_SKULL;
            case USE_SELLHAND -> Material.GOLD_INGOT;
            case BREAK_CONTAINER -> Material.CHEST;
            default -> Material.BOOK;
        };
    }

    private String getQuestDisplayName(QuestDefinition quest) {
        return quest.getType().getDescription();
    }

    private String getQuestDescription(QuestDefinition quest) {
        String base = quest.getType().getDescription();
        String units = quest.getType().getUnitDescription();
        StringBuilder details = new StringBuilder();
        // Ajout d'éventuels paramètres contextuels pour enrichir la description
        if (quest.getParams() != null && !quest.getParams().isEmpty()) {
            Object material = quest.getParams().get("material");
            Object mine = quest.getParams().get("mine");
            Object category = quest.getParams().get("category");
            List<String> parts = new ArrayList<>();
            if (material != null) parts.add("Bloc: " + String.valueOf(material));
            if (mine != null) parts.add("Mine: " + String.valueOf(mine));
            if (category != null) parts.add("Catégorie: " + String.valueOf(category));
            if (!parts.isEmpty()) {
                details.append(" – ").append(String.join(", ", parts));
            }
        }
        return base + " (cible: " + quest.getTarget() + " " + units + ")" + details;
    }

    // ================================================================================================
    // MÉTHODES AUXILIAIRES POUR L'INTERFACE
    // ================================================================================================

    private ItemStack createCategoryInfoItem(Player player, QuestCategory category) {
        ItemStack item = new ItemStack(category == QuestCategory.DAILY ? Material.CLOCK : Material.WRITTEN_BOOK);
        ItemMeta meta = item.getItemMeta();

        String title = category == QuestCategory.DAILY ? "§e⏰ Quêtes Journalières" : "§d📅 Quêtes Hebdomadaires";
        guiManager.applyName(meta, title);

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (category == QuestCategory.DAILY) {
            lore.add("§7Ces quêtes se renouvellent chaque jour");
            lore.add("§7à §e00h00§7. Terminez-les toutes pour");
            lore.add("§7obtenir une §6Clé Rare §7en bonus !");
        } else {
            lore.add("§7Ces quêtes se renouvellent chaque");
            lore.add("§7§dlundi§7. Terminez-les toutes pour");
            lore.add("§7obtenir une §6Clé Légendaire §7en bonus !");
        }

        lore.add("");
        lore.add("§7Cliquez sur une quête terminée pour");
        lore.add("§7réclamer sa récompense !");

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBlockCollectorInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§a⛏ Collectionneur de Blocs");

        Map<Material, Long> stats = plugin.getBlockCollectorManager().getStats(player.getUniqueId());
        long totalBlocks = stats.values().stream().mapToLong(Long::longValue).sum();

        List<String> lore = Arrays.asList(
                "",
                "§7Chaque bloc détruit en mine est comptabilisé",
                "§7et vous fait progresser vers des paliers !",
                "",
                "§7Il existe §e100 paliers §7par type de bloc.",
                "§7Plus vous montez en palier, plus les",
                "§7récompenses en beacons sont importantes !",
                "",
                "§7Total blocs détruits: §b" + formatNumber(totalBlocks),
                "§7Types différents: §a" + stats.size(),
                "",
                "§7Cliquez sur un bloc pour valider un palier"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackToMainButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§7← Retour au menu principal");
        guiManager.applyLore(meta, Arrays.asList("", "§7Retournez au menu des quêtes"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createRefreshButton() {
        ItemStack item = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§a⟲ Actualiser");
        guiManager.applyLore(meta, Arrays.asList("", "§7Met à jour les informations affichées"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§c✖ Fermer");
        guiManager.applyLore(meta, Arrays.asList("", "§7Ferme ce menu"));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatsButton(Player player) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§b📊 Statistiques");

        PlayerQuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId());
        List<String> lore = Arrays.asList(
                "",
                "§7Quêtes journalières terminées: §e" + progress.getDailyCompletedCount(),
                "§7Quêtes hebdomadaires terminées: §d" + progress.getWeeklyCompletedCount(),
                "",
                "§b▶ Cliquez pour plus de détails"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBlockStatsButton(Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        guiManager.applyName(meta, "§e📈 Statistiques Détaillées");

        Map<Material, Long> stats = plugin.getBlockCollectorManager().getStats(player.getUniqueId());
        long totalBlocks = stats.values().stream().mapToLong(Long::longValue).sum();

        List<String> lore = Arrays.asList(
                "",
                "§7Blocs totaux détruits: §b" + formatNumber(totalBlocks),
                "§7Types de blocs collectés: §a" + stats.size(),
                "",
                "§e▶ Cliquez pour voir les détails"
        );

        guiManager.applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    // ================================================================================================
    // MÉTHODES DE GESTION AVANCÉES
    // ================================================================================================

    private void refreshCurrentView(Player player) {
        String categoryData = guiManager.getGUIData(player, "category");
        String viewData = guiManager.getGUIData(player, "view");

        if (categoryData != null) {
            openQuestCategory(player, QuestCategory.valueOf(categoryData));
        } else if ("main".equals(viewData)) {
            openBlockCollector(player);
        } else {
            openMainMenu(player);
        }

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, 1.2f);
    }

    private void openBattlePass(Player player) {
        plugin.getBattlePassGUI().openMainMenu(player);
    }

    private void openAdvancementQuests(Player player) {
        player.sendMessage("§c⚠ Les quêtes d'avancement ne sont pas encore disponibles !");
        player.sendMessage("§7Cette fonctionnalité sera ajoutée dans une future mise à jour.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
    }

    private void checkAndGrantCategoryBonus(Player player, QuestCategory category) {
        List<QuestDefinition> quests = plugin.getQuestManager().getActiveQuestsForPlayer(player.getUniqueId(), category);
        PlayerQuestProgress progress = plugin.getQuestManager().getProgress(player.getUniqueId());

        boolean allCompleted = quests.stream()
                .allMatch(quest -> progress.get(quest.getId()) >= quest.getTarget());

        if (allCompleted) {
            player.sendMessage("§a★ Toutes les quêtes " +
                    (category == QuestCategory.DAILY ? "journalières" : "hebdomadaires") +
                    " sont terminées !");
            player.sendMessage("§7Vous pouvez maintenant réclamer votre récompense bonus !");
        }
    }
}