package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.PrestigeReward;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique REFONTE pour le système de prestige
 * - Talents et récompenses dans le même menu
 * - Organisation en lignes par niveau, colonnes par type
 * - Pages dynamiques selon le prestige
 * - Système de réinitialisation des talents
 */
public class PrestigeGUI {

    // Slots du menu principal - NOUVEAU LAYOUT
    private static final int PRESTIGE_INFO_SLOT = 4;
    private static final int COMBINED_BUTTON_SLOT = 15; // Talents & Récompenses
    private static final int RESET_TALENTS_SLOT = 14; // Réinitialiser talents
    private static final int PERFORM_PRESTIGE_SLOT = 11;
    private static final int HELP_SLOT = 9;
    private static final int CLOSE_SLOT = 26;

    // Layout du menu talents/récompenses (54 slots)
    // 5 prestiges par page, 3 slots par prestige (colonnes)
    private static final int[] PRESTIGE_ROWS = {3, 12, 21, 30, 39}; // 5 lignes

    // Navigation
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int BACK_SLOT = 49;

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey prestigeLevelKey;
    private final NamespacedKey rewardIdKey;
    private final NamespacedKey talentKey;

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
        Inventory gui = Bukkit.createInventory(null, 27, "§6🏆 §lSystème de Prestige §6🏆");

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

        // Informations de prestige au centre-haut
        gui.setItem(PRESTIGE_INFO_SLOT, createPrestigeInfoItem(player));

        // Bouton principal : Talents & Récompenses combinés
        gui.setItem(COMBINED_BUTTON_SLOT, createCombinedButton());

        // Bouton réinitialisation des talents
        gui.setItem(RESET_TALENTS_SLOT, createResetTalentsButton(player));

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
     * Ouvre le menu combiné talents/récompenses avec pages dynamiques
     */
    public void openCombinedMenu(Player player, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int maxPrestige = playerData.getPrestigeLevel();

        // 54 slots, 5 prestiges par page
        int maxPage = (maxPrestige - 1) / 5;
        page = Math.max(0, Math.min(page, maxPage));

        Inventory gui = Bukkit.createInventory(null, 54, "§6🏆 Progression Prestige : P" + (page * 5 + 1) + "-P" + Math.min((page + 1) * 5, maxPrestige));

        fillWithGlass(gui);
        setupProgressionMenu(gui, player, page);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.2f);
    }

    private void setupProgressionMenu(Inventory gui, Player player, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int maxPrestige = playerData.getPrestigeLevel();

        // 5 lignes de prestige par page
        for (int i = 0; i < 5; i++) {
            int prestigeLevel = page * 5 + i + 1;
            if (prestigeLevel > 50) break; // Max P50

            int baseSlot = PRESTIGE_ROWS[i];
            setupPrestigeRow(gui, player, prestigeLevel, baseSlot);
        }

        // Navigation
        if (page > 0) {
            gui.setItem(PREV_PAGE_SLOT, createPageButton("§c⬅ Page précédente", page - 1));
        }

        int maxPage = (Math.min(maxPrestige, 50) - 1) / 5;
        if (page < maxPage) {
            gui.setItem(NEXT_PAGE_SLOT, createPageButton("§aPage suivante ➡", page + 1));
        }

        gui.setItem(BACK_SLOT, createBackToMainButton());
    }

    /**
     * Configure le menu combiné avec organisation en lignes/colonnes
     */
    private void setupCombinedMenu(Inventory gui, Player player, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentPrestige = playerData.getPrestigeLevel();

        // Calcul des prestiges à afficher sur cette page
        int startPrestige = page * 5 + 1;
        int endPrestige = Math.min(startPrestige + 4, Math.max(5, currentPrestige));

        // Pour chaque ligne (niveau de prestige)
        for (int i = 0; i < 5; i++) {
            int prestigeLevel = startPrestige + i;
            if (prestigeLevel > Math.max(5, currentPrestige)) break;

            int baseSlot = PRESTIGE_ROWS[i];
            setupPrestigeRow(gui, player, prestigeLevel, baseSlot);
        }

        // Navigation
        if (page > 0) {
            gui.setItem(PREV_PAGE_SLOT, createPageButton("§a⬅ Page précédente", page - 1));
        }

        int maxPage = (Math.max(5, currentPrestige) - 1) / 5;
        if (page < maxPage) {
            gui.setItem(NEXT_PAGE_SLOT, createPageButton("§aPage suivante ➡", page + 1));
        }

        gui.setItem(BACK_SLOT, createBackToMainButton());
    }

    /**
     * Configure une ligne de prestige (3 colonnes dynamiques)
     */
    private void setupPrestigeRow(Inventory gui, Player player, int prestigeLevel, int baseSlot) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean isUnlocked = prestigeLevel <= playerData.getPrestigeLevel();

        if (prestigeLevel % 5 == 0) {
            // Palier spécial : récompenses
            setupSpecialRewardRow(gui, player, prestigeLevel, baseSlot, isUnlocked);
        } else {
            // Palier normal : talents
            setupTalentRow(gui, player, prestigeLevel, baseSlot, isUnlocked);
        }
    }

    /**
     * Configure une ligne de récompenses spéciales (P5, P10, etc.)
     */
    private void setupSpecialRewardRow(Inventory gui, Player player, int prestigeLevel, int baseSlot, boolean isUnlocked) {
        List<PrestigeReward> rewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(prestigeLevel);

        if (rewards.size() == 1) {
            // Récompense unique (P10, P20, etc.) - centrer sur la colonne du milieu
            PrestigeReward reward = rewards.get(0);
            gui.setItem(baseSlot + 1, createRewardItem(player, reward, isUnlocked));
        } else {
            // Choix multiple (P5, P15, etc.) - étaler sur les 3 colonnes
            for (int col = 0; col < Math.min(3, rewards.size()); col++) {
                PrestigeReward reward = rewards.get(col);
                gui.setItem(baseSlot + col, createRewardItem(player, reward, isUnlocked));
            }
        }
    }

    /**
     * Configure une ligne de talents (un seul talent par niveau, max un choisi)
     */
    private void setupTalentRow(Inventory gui, Player player, int prestigeLevel, int baseSlot, boolean isUnlocked) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        PrestigeTalent talent = PrestigeTalent.getTalentForPrestige(prestigeLevel);

        if (talent != null) {
            String chosenTalent = playerData.getChosenPrestigeTalent(prestigeLevel);
            boolean isChosen = talent.name().equals(chosenTalent);

            // Séparer la description en colonnes
            String[] bonusLines = talent.getDescription().split("\\n");

            for (int col = 0; col < Math.min(3, bonusLines.length); col++) {
                ItemStack item = createTalentColumnItem(player, prestigeLevel, talent, bonusLines[col], col, isUnlocked, isChosen);
                gui.setItem(baseSlot + col, item);
            }
        }
    }

    /**
     * Crée un item de récompense amélioré
     */
    private ItemStack createRewardItem(Player player, PrestigeReward reward, boolean isUnlocked) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        boolean isRewardUnlocked = playerData.isPrestigeRewardUnlocked(reward.getId());
        boolean isChosen = playerData.hasChosenSpecialReward(reward.getId());

        Material material = isChosen ? Material.EMERALD :
                isUnlocked ? Material.DIAMOND : Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String statusColor = isChosen ? "§a" : isUnlocked ? "§e" : "§7";
            meta.setDisplayName(statusColor + reward.getDisplayName());

            List<String> lore = new ArrayList<>();

            // Description sur plusieurs lignes
            String[] descLines = reward.getDescription().split("\\n");
            for (String line : descLines) {
                lore.add("§7" + line);
            }

            lore.add("");
            lore.add("§7Type: §f" + reward.getType().name());

            if (isChosen) {
                lore.add("§a✅ Récompense choisie et réclamée!");
            } else if (isUnlocked) {
                lore.add("§e⚡ Cliquez pour débloquer!");
            } else {
                lore.add("§c🔒 Atteignez P" + (reward.getId().contains("p") ? reward.getId().substring(1, reward.getId().indexOf("_")) : "?") + " pour débloquer");
            }

            meta.setLore(lore);

            // Métadonnées pour le clic
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "unlock_reward");
            meta.getPersistentDataContainer().set(rewardIdKey, PersistentDataType.STRING, reward.getId());

            item.setItemMeta(meta);
        }

        if (isChosen) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            ItemMeta enchMeta = item.getItemMeta();
            if (enchMeta != null) {
                enchMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(enchMeta);
            }
        }

        return item;
    }

    /**
     * Crée un item pour une colonne de talent amélioré
     */
    private ItemStack createTalentColumnItem(Player player, int prestigeLevel, PrestigeTalent talent,
                                             String bonusDescription, int column, boolean isUnlocked, boolean isChosen) {
        Material material = isChosen ? Material.ENCHANTED_BOOK :
                isUnlocked ? Material.BOOK : Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String columnName = getColumnName(talent, column);
            String statusColor = isChosen ? "§a" : isUnlocked ? "§e" : "§7";
            meta.setDisplayName(statusColor + columnName);

            List<String> lore = new ArrayList<>();
            lore.add("§7Prestige: §e" + prestigeLevel);
            lore.add("§7Bonus: " + bonusDescription);
            lore.add("");

            if (isChosen) {
                lore.add("§a✅ Talent choisi et actif!");
                lore.add("§7Ce bonus s'applique automatiquement");
            } else if (isUnlocked) {
                lore.add("§e⚡ Cliquez pour choisir ce talent!");
                lore.add("§c⚠ Un seul talent par niveau de prestige");
            } else {
                lore.add("§c🔒 Atteignez P" + prestigeLevel + " pour débloquer");
            }

            meta.setLore(lore);

            // Métadonnées pour le clic
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "choose_talent");
            meta.getPersistentDataContainer().set(prestigeLevelKey, PersistentDataType.INTEGER, prestigeLevel);
            meta.getPersistentDataContainer().set(talentKey, PersistentDataType.STRING, talent.name());

            item.setItemMeta(meta);
        }

        if (isChosen) {
            item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
            ItemMeta enchMeta = item.getItemMeta();
            if (enchMeta != null) {
                enchMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(enchMeta);
            }
        }

        return item;
    }

    /**
     * Obtient le nom de la colonne selon le talent et la position
     */
    private String getColumnName(PrestigeTalent talent, int column) {
        switch (talent) {
            case PROFIT_AMELIORE:
            case PROFIT_AMELIORE_II:
                return switch (column) {
                    case 0 -> "Money Greed";
                    case 1 -> "Prix de Vente";
                    case 2 -> "Gain Avant-poste";
                    default -> "Bonus " + (column + 1);
                };
            case ECONOMIE_OPTIMISEE:
            case ECONOMIE_OPTIMISEE_II:
                return switch (column) {
                    case 0 -> "Token Greed";
                    case 1 -> "Réduction Taxe";
                    case 2 -> "Prix Marchand PvP";
                    default -> "Bonus " + (column + 1);
                };
            default:
                return "Bonus " + (column + 1);
        }
    }

    /**
     * Obtient le matériau pour une colonne de talent
     */
    private Material getMaterialForTalentColumn(PrestigeTalent talent, int column) {
        return switch (talent) {
            case PROFIT_AMELIORE -> switch (column) {
                case 0 -> Material.GOLD_NUGGET; // Money Greed
                case 1 -> Material.EMERALD; // Prix vente
                case 2 -> Material.BEACON; // Gain avant-poste
                default -> Material.GOLD_INGOT;
            };
            case ECONOMIE_OPTIMISEE -> switch (column) {
                case 0 -> Material.DIAMOND; // Token Greed
                case 1 -> Material.REDSTONE; // Taxe
                case 2 -> Material.IRON_SWORD; // Prix marchand PvP
                default -> Material.DIAMOND;
            };
            case PROFIT_AMELIORE_II -> switch (column) {
                case 0 -> Material.GOLD_BLOCK; // Effet Money Greed
                case 1 -> Material.EMERALD_BLOCK; // Prix vente direct
                case 2 -> Material.BEACON; // Gain rinacoins avant-poste
                default -> Material.GOLD_BLOCK;
            };
            case ECONOMIE_OPTIMISEE_II -> switch (column) {
                case 0 -> Material.DIAMOND_BLOCK; // Effet Token Greed
                case 1 -> Material.REDSTONE_BLOCK; // Taux taxe final
                case 2 -> Material.NETHERITE_SWORD; // Prix marchand PvP
                default -> Material.DIAMOND_BLOCK;
            };
        };
    }

    /**
     * Gère les clics dans le menu
     */
    public void handleMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "open_combined" -> openCombinedMenu(player, 0);
            case "reset_talents" -> handleTalentReset(player);
            case "perform_prestige" -> {
                if (plugin.getPrestigeManager().canPrestige(player)) {
                    plugin.getPrestigeManager().performPrestige(player);
                    openMainPrestigeMenu(player); // Refresh
                }
            }
            case "page_navigation" -> {
                Integer targetPage = meta.getPersistentDataContainer().get(NamespacedKey.fromString("page"), PersistentDataType.INTEGER);
                if (targetPage != null) {
                    openCombinedMenu(player, targetPage);
                }
            }
            case "unlock_reward" -> {
                String rewardId = meta.getPersistentDataContainer().get(rewardIdKey, PersistentDataType.STRING);
                if (rewardId != null) {
                    handleRewardUnlock(player, rewardId);
                }
            }
            case "choose_talent" -> {
                Integer prestigeLevel = meta.getPersistentDataContainer().get(prestigeLevelKey, PersistentDataType.INTEGER);
                String talentName = meta.getPersistentDataContainer().get(talentKey, PersistentDataType.STRING);
                if (prestigeLevel != null && talentName != null) {
                    handleTalentChoice(player, prestigeLevel, talentName);
                }
            }
            case "back_to_main" -> openMainPrestigeMenu(player);
        }
    }


    /**
     * Gère le déverrouillage d'une récompense (gratuit)
     */
    private void handleRewardUnlock(Player player, String rewardId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier si déjà réclamée
        if (playerData.hasChosenSpecialReward(rewardId)) {
            player.sendMessage("§c❌ Vous avez déjà réclamé cette récompense!");
            return;
        }

        // Récupérer la récompense
        PrestigeReward reward = findRewardById(rewardId);
        if (reward == null) {
            player.sendMessage("§c❌ Récompense introuvable!");
            return;
        }

        // Marquer comme choisie et débloquée
        playerData.addChosenSpecialReward(rewardId);
        playerData.unlockPrestigeReward(rewardId);

        // Donner la récompense
        plugin.getPrestigeManager().getRewardManager().giveSpecialReward(player, reward);

        // Messages et effets
        player.sendMessage("§a✅ Récompense débloquée : " + reward.getDisplayName());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Sauvegarder
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Rafraîchir l'interface
        player.closeInventory();
        openCombinedMenu(player, 1);
    }

    /**
     * Gère le choix d'un talent (gratuit, un seul par niveau)
     */
    private void handleTalentChoice(Player player, int prestigeLevel, String talentName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier que le niveau est débloqué
        if (prestigeLevel > playerData.getPrestigeLevel()) {
            player.sendMessage("§c❌ Vous devez atteindre P" + prestigeLevel + " pour choisir ce talent!");
            return;
        }

        // Vérifier si un talent est déjà choisi pour ce niveau
        String existingTalent = playerData.getChosenPrestigeTalent(prestigeLevel);
        if (existingTalent != null) {
            player.sendMessage("§c❌ Vous avez déjà choisi un talent pour P" + prestigeLevel + "!");
            player.sendMessage("§7Utilisez la réinitialisation des talents pour rechoisir.");
            return;
        }

        // Choisir le talent
        playerData.choosePrestigeTalent(prestigeLevel, talentName);

        // Ajouter le talent aux talents actifs
        PrestigeTalent talent = PrestigeTalent.valueOf(talentName);
        playerData.addPrestigeTalent(talent);

        // Messages et effets
        player.sendMessage("§a✅ Talent choisi : " + talent.getDisplayName());
        player.sendMessage("§7Les bonus sont maintenant actifs!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // Sauvegarder
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Rafraîchir l'interface
        player.closeInventory();
        openCombinedMenu(player, 1);
    }

    /**
     * Trouve une récompense par son ID
     */
    private PrestigeReward findRewardById(String rewardId) {
        // Extraire le niveau de prestige depuis l'ID (format: "p5_autominer", "p10_title", etc.)
        try {
            String levelStr = rewardId.substring(1, rewardId.indexOf("_"));
            int prestigeLevel = Integer.parseInt(levelStr);

            List<PrestigeReward> rewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(prestigeLevel);

            for (PrestigeReward reward : rewards) {
                if (reward.getId().equals(rewardId)) {
                    return reward;
                }
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur lors de la recherche de récompense: " + rewardId);
        }

        return null;
    }


    /**
     * Gère la réinitialisation des talents
     */
    private void handleTalentReset(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérification des beacons
        if (playerData.getBeacons() < 500) {
            player.sendMessage("§c❌ Vous n'avez pas assez de beacons! Requis: §e500");
            player.sendMessage("§7Vous avez: §c" + playerData.getBeacons() + " beacons");
            return;
        }

        // Vérification qu'il y a des talents à réinitialiser
        if (playerData.getPrestigeTalents().isEmpty()) {
            player.sendMessage("§c❌ Vous n'avez aucun talent de prestige à réinitialiser!");
            return;
        }

        // Confirmation
        player.sendMessage("§6⚠ CONFIRMATION REQUISE ⚠");
        player.sendMessage("§7Cette action va:");
        player.sendMessage("§7• Réinitialiser TOUS vos talents de prestige");
        player.sendMessage("§7• Coûter §c500 beacons");
        player.sendMessage("§7• Les récompenses spéciales ne seront PAS récupérables");
        player.sendMessage("");
        player.sendMessage("§aTapez §e/prestige confirmer-reset §apour confirmer");

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 0.8f);
    }

    /**
     * Confirme la réinitialisation des talents (appelée depuis la commande)
     */
    public void confirmTalentReset(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier les beacons
        if (playerData.getBeacons() < 500) {
            player.sendMessage("§c❌ Vous n'avez pas assez de beacons! (500 requis)");
            return;
        }

        // Effectuer la réinitialisation
        playerData.removeBeacon(500);
        playerData.resetPrestigeTalents(); // Nouvelle méthode qui garde les récompenses

        // Messages et effets
        player.sendMessage("§a✅ Talents de prestige réinitialisés!");
        player.sendMessage("§7Coût: §c-500 beacons");
        player.sendMessage("§7Vos récompenses spéciales sont conservées");
        player.sendMessage("§7Vous pouvez maintenant rechoisir vos talents");

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Fermer le menu et rouvrir le principal
        player.closeInventory();
        openMainPrestigeMenu(player);
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
                lore.add("§e⚡ Bonus actifs:");

                // Calculer les bonus totaux
                double moneyBonus = playerData.getPrestigeMoneyGreedBonus();
                double tokenBonus = playerData.getPrestigeTokenGreedBonus();
                double taxReduction = playerData.getPrestigeTaxReduction();
                double sellBonus = playerData.getPrestigeSellBonus();

                if (moneyBonus > 0) {
                    lore.add("§7  • §6Money Greed: §a+" + String.format("%.1f", moneyBonus * 100) + "%");
                }
                if (tokenBonus > 0) {
                    lore.add("§7  • §bToken Greed: §a+" + String.format("%.1f", tokenBonus * 100) + "%");
                }
                if (taxReduction > 0) {
                    lore.add("§7  • §cRéduction Taxe: §a-" + String.format("%.1f", taxReduction * 100) + "%");
                }
                if (sellBonus > 0) {
                    lore.add("§7  • §ePrix Vente: §a+" + String.format("%.1f", sellBonus * 100) + "%");
                }

                lore.add("");
                lore.add("§7Récompenses spéciales réclamées: §e" + playerData.getChosenSpecialRewards().size());
            } else {
                lore.add("§7Atteignez le prestige 1 pour débloquer des bonus!");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createCombinedButton() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§5⚡ Talents & Récompenses");
            meta.setLore(List.of(
                    "§7Consultez et gérez vos talents",
                    "§7de prestige et récompenses spéciales",
                    "",
                    "§7• Organisation par niveau de prestige",
                    "§7• Affichage des bonus par colonne",
                    "§7• Récompenses spéciales tous les 5 niveaux",
                    "",
                    "§eCliquez pour ouvrir!"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_combined");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createResetTalentsButton(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean hasEnoughBeacons = playerData.getBeacons() >= 500;
        boolean hasTalents = !playerData.getPrestigeTalents().isEmpty();

        Material material = hasEnoughBeacons && hasTalents ? Material.TNT : Material.BARRIER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c🔄 Réinitialiser Talents");

            List<String> lore = new ArrayList<>();
            lore.add("§7Remet à zéro tous vos talents");
            lore.add("§7de prestige pour les réattribuer");
            lore.add("");
            lore.add("§7Coût: §e500 beacons");
            lore.add("§7Vos beacons: " + (hasEnoughBeacons ? "§a" : "§c") + playerData.getBeacons());
            lore.add("");

            if (!hasTalents) {
                lore.add("§cAucun talent à réinitialiser");
            } else if (!hasEnoughBeacons) {
                lore.add("§cBeacons insuffisants!");
            } else {
                lore.add("§7⚠ Les récompenses spéciales");
                lore.add("§7ne peuvent PAS être réclamées à nouveau");
                lore.add("");
                lore.add("§eCliquez pour réinitialiser");
            }

            meta.setLore(lore);

            if (hasEnoughBeacons && hasTalents) {
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "reset_talents");
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createPerformPrestigeButton(int nextLevel) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6🚀 Effectuer Prestige " + nextLevel);
            meta.setLore(List.of(
                    "§7Passez au niveau de prestige suivant",
                    "§7et débloquez de nouveaux bonus!",
                    "",
                    "§aConditions remplies!",
                    "",
                    "§eCliquez pour prestigier!"
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
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
                    "§7Vous devez remplir les conditions",
                    "§7pour effectuer un prestige",
                    "",
                    "§7Consultez §e/prestige info §7pour",
                    "§7voir les prérequis"
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createPageButton(String name, int targetPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of("§7Page " + (targetPage + 1)));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "page_navigation");
            meta.getPersistentDataContainer().set(NamespacedKey.fromString("page"), PersistentDataType.INTEGER, targetPage);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createBackToMainButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c← Retour au menu principal");
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_main");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createBackToCombinedButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§a← Retour aux Talents & Récompenses");
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "page_navigation");
            meta.getPersistentDataContainer().set(NamespacedKey.fromString("page"), PersistentDataType.INTEGER, 0);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createHelpItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§e❓ Aide");
            meta.setLore(List.of(
                    "§7Le système de prestige vous permet",
                    "§7de recommencer avec des bonus permanents",
                    "",
                    "§7• Talents cycliques automatiques",
                    "§7• Récompenses spéciales tous les 5 niveaux",
                    "§7• Possibilité de réinitialiser les talents",
                    "",
                    "§7Plus d'infos: §e/prestige help"
            ));
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§c✗ Fermer");
            item.setItemMeta(meta);
        }

        return item;
    }
}