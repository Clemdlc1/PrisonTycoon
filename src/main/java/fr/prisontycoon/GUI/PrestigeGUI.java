package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique pour le système de prestige
 */
public class PrestigeGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey prestigeLevelKey;
    private final NamespacedKey rewardIdKey;
    private final NamespacedKey talentKey;

    // Slots du menu principal
    private static final int PRESTIGE_INFO_SLOT = 13;
    private static final int REWARDS_BUTTON_SLOT = 11;
    private static final int TALENTS_BUTTON_SLOT = 15;
    private static final int PERFORM_PRESTIGE_SLOT = 31;
    private static final int MINES_INFO_SLOT = 22;
    private static final int HELP_SLOT = 18;
    private static final int CLOSE_SLOT = 26;

    public PrestigeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "prestige_action");
        this.prestigeLevelKey = new NamespacedKey(plugin, "prestige_level");
        this.rewardIdKey = new NamespacedKey(plugin, "reward_id");
        this.talentKey = new NamespacedKey(plugin, "talent_name");
    }

    /**
     * Ouvre le menu principal du prestige
     */
    public void openMainPrestigeMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 45, "§6🏆 §lSystème de Prestige §6🏆");

        fillWithGlass(gui);
        setupMainPrestigeMenu(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.2f);
    }

    /**
     * Configure le menu principal du prestige
     */
    private void setupMainPrestigeMenu(Inventory gui, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Informations de prestige au centre
        gui.setItem(PRESTIGE_INFO_SLOT, createPrestigeInfoItem(player));

        // Boutons principaux
        gui.setItem(REWARDS_BUTTON_SLOT, createRewardsButton());
        gui.setItem(TALENTS_BUTTON_SLOT, createTalentsButton());
        gui.setItem(MINES_INFO_SLOT, createMinesInfoItem(player));

        // Bouton de prestige (si possible)
        if (plugin.getPrestigeManager().canPrestige(player)) {
            gui.setItem(PERFORM_PRESTIGE_SLOT, createPerformPrestigeButton(playerData.getPrestigeLevel() + 1));
        } else {
            gui.setItem(PERFORM_PRESTIGE_SLOT, createLockedPrestigeButton());
        }

        // Navigation
        gui.setItem(HELP_SLOT, createHelpItem());
        gui.setItem(CLOSE_SLOT, createCloseItem());
    }

    /**
     * Ouvre le menu des récompenses de prestige
     */
    public void openRewardsMenu(Player player, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6📦 Récompenses de Prestige §7(Page " + (page + 1) + ")");

        fillWithGlass(gui);
        setupRewardsMenu(gui, player, page);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
    }

    /**
     * Configure le menu des récompenses
     */
    private void setupRewardsMenu(Inventory gui, Player player, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        // Affichage des récompenses par tranches de 10 niveaux
        int startLevel = (page * 10) + 1;
        int endLevel = Math.min(startLevel + 9, 50);

        int slot = 10; // Commencer à la deuxième ligne
        for (int level = startLevel; level <= endLevel && slot < 44; level++) {
            ItemStack rewardItem = createRewardDisplayItem(player, level);
            gui.setItem(slot, rewardItem);

            slot++;
            if (slot % 9 == 8) slot += 2; // Éviter les bords
        }

        // Navigation
        if (page > 0) {
            gui.setItem(45, createPageButton("§a⬅ Page précédente", page - 1, "rewards"));
        }
        if (endLevel < 50) {
            gui.setItem(53, createPageButton("§aPage suivante ➡", page + 1, "rewards"));
        }

        gui.setItem(49, createBackToMainButton());
    }

    /**
     * Ouvre le menu des talents de prestige
     */
    public void openTalentsMenu(Player player, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, "§5⚡ Talents de Prestige");

        fillWithGlass(gui);
        setupTalentsMenu(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    /**
     * Configure le menu des talents
     */
    private void setupTalentsMenu(Inventory gui, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Map<PrestigeTalent, Integer> playerTalents = playerData.getPrestigeTalents();

        // Affichage des 4 talents cycliques
        PrestigeTalent[] talents = PrestigeTalent.values();
        int[] slots = {20, 21, 23, 24}; // 2x2 au centre

        for (int i = 0; i < talents.length && i < slots.length; i++) {
            PrestigeTalent talent = talents[i];
            int currentLevel = playerTalents.getOrDefault(talent, 0);

            gui.setItem(slots[i], createTalentDisplayItem(talent, currentLevel, playerData.getPrestigeLevel()));
        }

        // Informations sur les talents
        gui.setItem(13, createTalentsInfoItem(player));

        // Navigation
        gui.setItem(49, createBackToMainButton());
    }

    /**
     * Ouvre le menu de choix des récompenses spéciales
     */
    public void openSpecialRewardsMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Trouver le prochain niveau de prestige qui a des récompenses à choisir
        int targetLevel = -1;
        for (int level = 5; level <= playerData.getPrestigeLevel(); level += 5) {
            if (playerData.canChooseRewardForPrestige(level)) {
                targetLevel = level;
                break;
            }
        }

        if (targetLevel == -1) {
            player.sendMessage("§c❌ Aucune récompense spéciale à choisir!");
            return;
        }

        List<PrestigeReward> rewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(targetLevel);
        if (rewards.size() <= 1) {
            player.sendMessage("§c❌ Pas de choix disponible pour ce niveau!");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27, "§6🎁 Choisir Récompense P" + targetLevel);

        fillWithGlass(gui);

        // Afficher les choix
        int[] choiceSlots = {11, 13, 15};
        for (int i = 0; i < rewards.size() && i < choiceSlots.length; i++) {
            ItemStack rewardItem = rewards.get(i).getDisplayItem();
            ItemMeta meta = rewardItem.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "choose_reward");
                meta.getPersistentDataContainer().set(rewardIdKey, PersistentDataType.STRING, rewards.get(i).getId());
                meta.getPersistentDataContainer().set(prestigeLevelKey, PersistentDataType.INTEGER, targetLevel);
                rewardItem.setItemMeta(meta);
            }
            gui.setItem(choiceSlots[i], rewardItem);
        }

        // Retour
        gui.setItem(22, createBackToMainButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
    }

    /**
     * Gère les clics dans les menus de prestige
     */
    public void handleClick(Player player, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (!meta.getPersistentDataContainer().has(actionKey, PersistentDataType.STRING)) return;

        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

        switch (action) {
            case "open_rewards" -> openRewardsMenu(player, 0);
            case "open_talents" -> openTalentsMenu(player, 0);
            case "perform_prestige" -> {
                player.closeInventory();
                player.performCommand("prestige effectuer");
            }
            case "choose_reward" -> {
                String rewardId = meta.getPersistentDataContainer().get(rewardIdKey, PersistentDataType.STRING);
                int prestigeLevel = meta.getPersistentDataContainer().get(prestigeLevelKey, PersistentDataType.INTEGER);
                handleRewardChoice(player, rewardId, prestigeLevel);
            }
            case "page_navigation" -> {
                // Géré par les données dans l'item
            }
            case "back_to_main" -> openMainPrestigeMenu(player);
            case "close" -> player.closeInventory();
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Gère le choix d'une récompense spéciale
     */
    private void handleRewardChoice(Player player, String rewardId, int prestigeLevel) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (!playerData.canChooseRewardForPrestige(prestigeLevel)) {
            player.sendMessage("§c❌ Vous ne pouvez plus choisir de récompense pour ce niveau!");
            return;
        }

        // Trouver la récompense
        List<PrestigeReward> rewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(prestigeLevel);
        PrestigeReward chosenReward = rewards.stream()
                .filter(reward -> reward.getId().equals(rewardId))
                .findFirst()
                .orElse(null);

        if (chosenReward == null) {
            player.sendMessage("§c❌ Récompense introuvable!");
            return;
        }

        // Donner la récompense
        plugin.getPrestigeManager().getRewardManager().giveSpecialReward(player, chosenReward);

        // Marquer comme choisie
        playerData.addChosenSpecialReward(rewardId);

        player.closeInventory();
        player.sendMessage("§a✅ Récompense choisie: §6" + chosenReward.getDisplayName());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    // =============== MÉTHODES DE CRÉATION D'ITEMS ===============

    private void fillWithGlass(Inventory gui) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, glass);
            }
        }
    }

    private ItemStack createPrestigeInfoItem(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        Material material = prestigeLevel > 0 ? Material.NETHER_STAR : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6🏆 Votre Prestige");

            List<String> lore = new ArrayList<>();
            lore.add("§7Niveau actuel: " + playerData.getPrestigeDisplayName());
            lore.add("");

            if (prestigeLevel > 0) {
                lore.add("§e⚡ Talents actifs:");
                Map<PrestigeTalent, Integer> talents = playerData.getPrestigeTalents();
                if (talents.isEmpty()) {
                    lore.add("§7  Aucun talent actif");
                } else {
                    for (Map.Entry<PrestigeTalent, Integer> entry : talents.entrySet()) {
                        lore.add("§7  • §6" + entry.getKey().getDisplayName() + " §7(Niv." + entry.getValue() + ")");
                    }
                }
                lore.add("");
                lore.add("§a📈 Bonus totaux:");
                lore.add("§7  • Money Greed: §a+" + String.format("%.1f", playerData.getPrestigeMoneyGreedBonus() * 100) + "%");
                lore.add("§7  • Token Greed: §a+" + String.format("%.1f", playerData.getPrestigeTokenGreedBonus() * 100) + "%");
                lore.add("§7  • Réduction taxe: §a-" + String.format("%.1f", playerData.getPrestigeTaxReduction() * 100) + "%");
            } else {
                lore.add("§7Aucun prestige effectué");
                lore.add("§7Atteignez le rang §d§lFREE §7pour débloquer");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createRewardsButton() {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6📦 Récompenses de Prestige");
            meta.setLore(List.of(
                    "§7Consultez toutes les récompenses",
                    "§7disponibles pour chaque niveau",
                    "§7de prestige.",
                    "",
                    "§eCliquez pour ouvrir!"
            ));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_rewards");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createTalentsButton() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§5⚡ Talents de Prestige");
            meta.setLore(List.of(
                    "§7Visualisez vos talents de prestige",
                    "§7et leurs effets cumulés.",
                    "",
                    "§7Les talents se débloquent automatiquement",
                    "§7selon un cycle de 4 niveaux.",
                    "",
                    "§eCliquez pour ouvrir!"
            ));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_talents");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createPerformPrestigeButton(int nextLevel) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§a✨ Effectuer Prestige " + nextLevel);
            meta.setLore(List.of(
                    "§7Effectuez votre prochain prestige!",
                    "",
                    "§c⚠ ATTENTION:",
                    "§7• Retour au rang A",
                    "§7• Remise à 0 des coins",
                    "",
                    "§a✅ AVANTAGES:",
                    "§7• Talents permanents",
                    "§7• Mines prestige exclusives",
                    "§7• Récompenses spéciales",
                    "",
                    "§eCliquez pour commencer!"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "perform_prestige");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createLockedPrestigeButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c🔒 Prestige Verrouillé");
            meta.setLore(List.of(
                    "§7Conditions non remplies:",
                    "§c• Rang FREE requis",
                    "§c• Pas d'épargne active",
                    "§c• Pas d'investissement actif",
                    "§c• Ne pas être en challenge"
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createMinesInfoItem(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§b⛏ Mines Prestige");

            List<String> lore = new ArrayList<>();
            lore.add("§7Mines exclusives aux joueurs prestige");
            lore.add("");

            if (prestigeLevel >= 1) {
                lore.add("§a✅ Mine Prestige I §7(0.1% beacons)");
            } else {
                lore.add("§c🔒 Mine Prestige I §7(P1+)");
            }

            if (prestigeLevel >= 11) {
                lore.add("§a✅ Mine Prestige XI §7(0.5% beacons)");
            } else {
                lore.add("§c🔒 Mine Prestige XI §7(P11+)");
            }

            if (prestigeLevel >= 21) {
                lore.add("§a✅ Mine Prestige XXI §7(1% beacons)");
            } else {
                lore.add("§c🔒 Mine Prestige XXI §7(P21+)");
            }

            if (prestigeLevel >= 31) {
                lore.add("§a✅ Mine Prestige XXXI §7(3% beacons)");
            } else {
                lore.add("§c🔒 Mine Prestige XXXI §7(P31+)");
            }

            if (prestigeLevel >= 41) {
                lore.add("§a✅ Mine Prestige XLI §7(5% beacons)");
            } else {
                lore.add("§c🔒 Mine Prestige XLI §7(P41+)");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createRewardDisplayItem(Player player, int level) {
        // TODO: Créer l'affichage des récompenses pour chaque niveau
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6Prestige " + level);
            // Ajouter les détails des récompenses selon le niveau
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createTalentDisplayItem(PrestigeTalent talent, int currentLevel, int prestigeLevel) {
        Material material = switch (talent) {
            case PROFIT_AMELIORE -> Material.GOLD_NUGGET;
            case ECONOMIE_OPTIMISEE -> Material.EMERALD;
            case PROFIT_AMELIORE_II -> Material.GOLD_INGOT;
            case ECONOMIE_OPTIMISEE_II -> Material.DIAMOND;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6" + talent.getDisplayName());

            List<String> lore = new ArrayList<>();
            lore.add("§7" + talent.getDescription().replace("\n", " §7"));
            lore.add("");
            lore.add("§7Niveau actuel: §e" + currentLevel);
            lore.add("§7Disponible tous les " + talent.getCycle() + " prestiges");

            meta.setLore(lore);
            meta.getPersistentDataContainer().set(talentKey, PersistentDataType.STRING, talent.name());
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createTalentsInfoItem(Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e📖 Informations Talents");
            meta.setLore(List.of(
                    "§7Les talents de prestige sont obtenus",
                    "§7automatiquement selon un cycle:",
                    "",
                    "§6P1,6,11,16... §7Profit Amélioré",
                    "§bP2,7,12,17... §7Économie Optimisée",
                    "§6P3,8,13,18... §7Profit Amélioré II",
                    "§bP4,9,14,19... §7Économie Optimisée II",
                    "",
                    "§7Les paliers P5,10,15... donnent",
                    "§7des récompenses spéciales à choisir."
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createPageButton(String name, int page, String type) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "page_navigation");
            meta.getPersistentDataContainer().set(prestigeLevelKey, PersistentDataType.INTEGER, page);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createBackToMainButton() {
        ItemStack item = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§7← Retour au menu principal");
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_main");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createHelpItem() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e❓ Aide");
            meta.setLore(List.of(
                    "§7Aide sur le système de prestige"
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c✖ Fermer");
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
            item.setItemMeta(meta);
        }

        return item;
    }
}