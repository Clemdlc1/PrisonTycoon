package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.commands.PrestigeCommand;
import fr.prisontycoon.data.BankType;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.PrestigeReward;
import fr.prisontycoon.prestige.PrestigeTalent;
import fr.prisontycoon.utils.NumberFormatter;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.stream.Collectors.joining;

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
    private static final long RESET_CONFIRMATION_TIMEOUT = 30000; // 30 secondes
    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey prestigeLevelKey;
    private final NamespacedKey rewardIdKey;
    private final NamespacedKey talentKey;
    private final Map<UUID, Integer> currentPages = new ConcurrentHashMap<>();


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
        Inventory gui = plugin.getGUIManager().createInventory(27, "§6🏆 §lSystème de Prestige §6🏆");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.PRESTIGE_MENU, gui);

        plugin.getGUIManager().fillBorders(gui);
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

        // NOUVEAU: Résumé des talents actifs
        gui.setItem(13, createTalentSummaryButton(player)); // Centre

        // Bouton principal : Talents & Récompenses combinés avec compteurs
        gui.setItem(COMBINED_BUTTON_SLOT, createCombinedButton(player));

        // Bouton réinitialisation des talents
        gui.setItem(RESET_TALENTS_SLOT, createResetTalentsButton(player));

        // Bouton de prestige (si possible)
        if (plugin.getPrestigeManager().canPrestige(player)) {
            gui.setItem(PERFORM_PRESTIGE_SLOT, createPerformPrestigeButton(player, playerData.getPrestigeLevel() + 1));
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

        Inventory gui = plugin.getGUIManager().createInventory(54, "§6🏆 Progression Prestige : P" + (page * 5 + 1) + "-P" + Math.min((page + 1) * 5, maxPrestige));

        plugin.getGUIManager().fillBorders(gui);
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

    private void setupSpecialRewardRow(Inventory gui, Player player, int prestigeLevel, int baseSlot, boolean isUnlocked) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        List<PrestigeReward> rewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(prestigeLevel);

        if (rewards.isEmpty()) return;

        // Vérifier si un choix a déjà été fait pour ce niveau
        String chosenRewardId = playerData.getChosenSpecialReward(prestigeLevel);
        boolean hasChoice = chosenRewardId != null;

        if (rewards.size() == 1) {
            // Récompense unique (P10, P20, etc.) - centrer sur la colonne du milieu
            PrestigeReward reward = rewards.getFirst();
            boolean isChosen = reward.id().equals(chosenRewardId);
            gui.setItem(baseSlot + 1, createExclusiveRewardItem(player, reward, prestigeLevel, isUnlocked, isChosen, hasChoice));
        } else {
            // Choix multiple (P5, P15, etc.) - étaler sur les 3 colonnes
            for (int col = 0; col < Math.min(3, rewards.size()); col++) {
                PrestigeReward reward = rewards.get(col);
                boolean isChosen = reward.id().equals(chosenRewardId);

                // CORRECTION: Si hasChoice = true et cette récompense n'est pas choisie,
                // alors createExclusiveRewardItem va utiliser du Glass
                gui.setItem(baseSlot + col, createExclusiveRewardItem(player, reward, prestigeLevel, isUnlocked, isChosen, hasChoice));
            }
        }
    }

    /**
     * Crée un item de récompense avec système exclusif
     */
    private ItemStack createExclusiveRewardItem(Player player, PrestigeReward reward, int prestigeLevel,
                                                boolean isUnlocked, boolean isChosen, boolean hasAnyChoice) {
        Material material;
        if (isChosen) {
            material = Material.CHEST; // Récompense choisie
        } else if (hasAnyChoice) {
            material = Material.GRAY_STAINED_GLASS_PANE; // Autre choix fait
        } else if (isUnlocked) {
            material = Material.ENDER_CHEST; // Disponible
        } else {
            material = Material.BARRIER; // Verrouillé
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String prefix;
            String nameColor;
            List<String> statusLore = new ArrayList<>();

            if (isChosen) {
                // RÉCOMPENSE CHOISIE ET RÉCUPÉRÉE
                prefix = "§a✅ ";
                nameColor = "§a§l";
                statusLore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§a🎁 RÉCOMPENSE RÉCUPÉRÉE");
                statusLore.add("§7Cette récompense a été choisie et");
                statusLore.add("§7appliquée à votre compte pour P" + prestigeLevel + ".");
                statusLore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            } else if (hasAnyChoice) {
                // AUTRE RÉCOMPENSE DÉJÀ CHOISIE
                prefix = "§8✗ ";
                nameColor = "§8";
                statusLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§c❌ NON RÉCLAMABLE");
                statusLore.add("§7Vous avez déjà choisi une autre");
                statusLore.add("§7récompense pour le niveau P" + prestigeLevel + ".");
                statusLore.add("§7");
                statusLore.add("§7💡 Une seule récompense par niveau!");
                statusLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            } else if (isUnlocked) {
                // RÉCOMPENSE DISPONIBLE
                prefix = "§e🎁 ";
                nameColor = "§e§l";
                statusLore.add("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§6🌟 RÉCOMPENSE DISPONIBLE");
                statusLore.add("§7Vous pouvez choisir cette récompense");
                statusLore.add("§7spéciale pour P" + prestigeLevel + ".");
                statusLore.add("§7");
                statusLore.add("§c⚠ Attention: §7Choix définitif!");
                statusLore.add("§7Les autres récompenses seront perdues.");
                statusLore.add("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§a➤ Cliquez pour choisir");

                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "choose_reward");
                meta.getPersistentDataContainer().set(rewardIdKey, PersistentDataType.STRING, reward.id());
                meta.getPersistentDataContainer().set(prestigeLevelKey, PersistentDataType.INTEGER, prestigeLevel);

            } else {
                // RÉCOMPENSE VERROUILLÉE
                prefix = "§c🔒 ";
                nameColor = "§c";
                statusLore.add("§c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§4❌ RÉCOMPENSE VERROUILLÉE");
                statusLore.add("§7Atteignez le niveau §6P" + prestigeLevel);
                statusLore.add("§7pour débloquer cette récompense.");
                statusLore.add("§c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            }

            plugin.getGUIManager().applyName(meta, prefix + nameColor + reward.displayName() + " §7(P" + prestigeLevel + ")");

            // Construire la lore complète
            List<String> lore = new ArrayList<>();
            lore.add("§f" + reward.description());
            lore.add("");
            lore.addAll(statusLore);

            plugin.getGUIManager().applyLore(meta, lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crée un item de récompense amélioré
     */
    // ==================== DIFFÉRENCIATION VISUELLE DES TALENTS DANS LE MENU PROGRESSION ====================
    private void setupTalentRow(Inventory gui, Player player, int prestigeLevel, int baseSlot, boolean isUnlocked) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Obtenir tous les talents disponibles pour ce niveau
        PrestigeTalent[] availableTalents = PrestigeTalent.getTalentsForPrestige(prestigeLevel);
        if (availableTalents.length == 0) return;

        // Vérifier si un choix a déjà été fait pour ce niveau
        PrestigeTalent chosenTalent = playerData.getChosenPrestigeColumn(prestigeLevel);
        boolean hasChoice = chosenTalent != null;

        // Afficher chaque talent sur sa colonne
        for (int col = 0; col < Math.min(3, availableTalents.length); col++) {
            PrestigeTalent talent = availableTalents[col];
            boolean isChosen = talent.equals(chosenTalent);

            ItemStack item = createTalentColumnItem(player, talent, prestigeLevel, col,
                    isUnlocked, isChosen, hasChoice);
            gui.setItem(baseSlot + col, item);
        }
    }


    /**
     * Crée un item pour une colonne de talent spécifique
     */
    private ItemStack createTalentColumnItem(Player player, PrestigeTalent talent, int prestigeLevel,
                                             int column, boolean isUnlocked, boolean isChosen, boolean hasAnyChoice) {
        // Déterminer le matériau selon l'état
        Material material;
        if (isChosen) {
            material = getTalentMaterialForBonus(talent, true); // Version brillante
        } else if (hasAnyChoice) {
            material = Material.GRAY_STAINED_GLASS_PANE; // Autre choix fait
        } else if (isUnlocked) {
            material = getTalentMaterialForBonus(talent, false); // Version normale
        } else {
            material = Material.BLACK_STAINED_GLASS_PANE; // Verrouillé
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String prefix;
            String nameColor;
            List<String> statusLore = new ArrayList<>();

            if (isChosen) {
                // BONUS CHOISI ET ACTIF
                prefix = "§a✅ ";
                nameColor = "§a§l";
                statusLore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§a✨ BONUS ACTIF ✨");
                statusLore.add("§7Ce bonus est appliqué à votre compte");
                statusLore.add("§7depuis le niveau P" + prestigeLevel + ".");
                statusLore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            } else if (hasAnyChoice) {
                // AUTRE BONUS DÉJÀ CHOISI
                prefix = "§8✗ ";
                nameColor = "§8";
                statusLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§c❌ NON SÉLECTIONNABLE");
                statusLore.add("§7Vous avez déjà choisi un autre bonus");
                statusLore.add("§7pour le niveau P" + prestigeLevel + ".");
                statusLore.add("§7");
                statusLore.add("§7💡 Utilisez §e§lRéinitialiser Talents");
                statusLore.add("§7pour rechoisir (500 beacons)");
                statusLore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            } else if (isUnlocked) {
                // BONUS DISPONIBLE
                prefix = "§e⭘ ";
                nameColor = "§e§l";
                statusLore.add("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§6🌟 DISPONIBLE");
                statusLore.add("§7Vous pouvez sélectionner ce bonus");
                statusLore.add("§7pour le niveau P" + prestigeLevel + ".");
                statusLore.add("§7");
                statusLore.add("§c⚠ Attention: §7Choix définitif!");
                statusLore.add("§7Seul ce bonus sera actif, pas les autres.");
                statusLore.add("§e▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§a➤ Cliquez pour sélectionner");

                // Ajouter les données pour le clic
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "choose_column");
                meta.getPersistentDataContainer().set(prestigeLevelKey, PersistentDataType.INTEGER, prestigeLevel);
                meta.getPersistentDataContainer().set(talentKey, PersistentDataType.STRING, talent.name());

            } else {
                // BONUS VERROUILLÉ
                prefix = "§c🔒 ";
                nameColor = "§c";
                statusLore.add("§c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                statusLore.add("§4❌ VERROUILLÉ");
                statusLore.add("§7Atteignez le niveau §6P" + prestigeLevel);
                statusLore.add("§7pour débloquer ce bonus.");
                statusLore.add("§c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            }

            // Nom du bonus
            plugin.getGUIManager().applyName(meta, prefix + nameColor + talent.getDisplayName() + " §7(P" + prestigeLevel + ")");

            // Construire la lore complète
            List<String> lore = new ArrayList<>();
            lore.add("§f" + talent.getDescription());
            lore.add("");
            lore.addAll(statusLore);

            plugin.getGUIManager().applyLore(meta, lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Détermine le matériau selon le type de bonus
     */
    private Material getTalentMaterialForBonus(PrestigeTalent talent, boolean isActive) {
        Material baseMaterial = switch (talent) {
            case MONEY_GREED_BONUS -> Material.GOLD_NUGGET;
            case SELL_PRICE_BONUS -> Material.EMERALD;
            case OUTPOST_BONUS -> Material.BEACON;
            case TOKEN_GREED_BONUS -> Material.DIAMOND;
            case TAX_REDUCTION -> Material.REDSTONE;
            case PVP_MERCHANT_REDUCTION -> Material.IRON_SWORD;
        };

        // Version améliorée si le bonus est actif
        if (isActive) {
            return switch (baseMaterial) {
                case GOLD_NUGGET -> Material.GOLD_INGOT;
                case EMERALD -> Material.EMERALD_BLOCK;
                case DIAMOND -> Material.DIAMOND_BLOCK;
                case REDSTONE -> Material.REDSTONE_BLOCK;
                case IRON_SWORD -> Material.NETHERITE_SWORD;
                default -> baseMaterial;
            };
        }

        return baseMaterial;
    }

    // Modifier setupPrestigeRow pour inclure l'en-tête :
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
     * Met à jour la gestion des clics pour la navigation (CORRIGÉ)
     */
    public void handleClick(Player player, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "choose_column" -> {
                Integer prestigeLevel = meta.getPersistentDataContainer().get(prestigeLevelKey, PersistentDataType.INTEGER);
                String talentName = meta.getPersistentDataContainer().get(talentKey, PersistentDataType.STRING);
                if (prestigeLevel != null && talentName != null) {
                    handleColumnChoice(player, prestigeLevel, talentName);
                }
            }
            case "choose_reward" -> {
                String rewardId = meta.getPersistentDataContainer().get(rewardIdKey, PersistentDataType.STRING);
                Integer prestigeLevel = meta.getPersistentDataContainer().get(prestigeLevelKey, PersistentDataType.INTEGER);
                if (rewardId != null && prestigeLevel != null) {
                    handleRewardChoice(player, rewardId, prestigeLevel);
                }
            }
            case "page_navigation" -> {
                // CORRECTION: Utiliser la bonne clé pour la page cible
                NamespacedKey targetPageKey = new NamespacedKey(plugin, "target_page");
                Integer targetPage = meta.getPersistentDataContainer().get(targetPageKey, PersistentDataType.INTEGER);
                if (targetPage != null) {
                    currentPages.put(player.getUniqueId(), targetPage);
                    openCombinedMenu(player, targetPage);
                }
            }
            case "open_combined" -> openCombinedMenu(player, 0);
            case "perform_prestige" -> {
                plugin.getPrestigeManager().performPrestige(player);
                player.closeInventory();
            }
            case "reset_talents" -> handleTalentReset(player);
            case "back_to_main" -> openMainPrestigeMenu(player);
        }
    }

    /**
     * Gère le choix d'une colonne spécifique (NOUVELLE MÉTHODE)
     */
    private void handleColumnChoice(Player player, int prestigeLevel, String talentName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier que le niveau est débloqué
        if (prestigeLevel > playerData.getPrestigeLevel()) {
            player.sendMessage("§c❌ Vous devez atteindre P" + prestigeLevel + " pour choisir ce bonus!");
            return;
        }

        // Vérifier si un choix a déjà été fait pour ce niveau
        PrestigeTalent existingChoice = playerData.getChosenPrestigeColumn(prestigeLevel);
        if (existingChoice != null) {
            player.sendMessage("§c❌ Vous avez déjà choisi un bonus pour P" + prestigeLevel + "!");
            player.sendMessage("§7Choix actuel: §e" + existingChoice.getDisplayName());
            player.sendMessage("§7Utilisez la réinitialisation des talents pour rechoisir.");
            return;
        }

        // Vérifier que le talent est valide pour ce niveau
        try {
            PrestigeTalent talent = PrestigeTalent.valueOf(talentName);
            if (!talent.isAvailableForPrestige(prestigeLevel)) {
                player.sendMessage("§c❌ Ce bonus n'est pas disponible pour P" + prestigeLevel + "!");
                return;
            }

            // Faire le choix
            playerData.choosePrestigeColumn(prestigeLevel, talent);

            // Messages et effets
            player.sendMessage("§a✅ Bonus sélectionné : " + talent.getDisplayName());
            player.sendMessage("§7Effet: " + talent.getDescription());
            player.sendMessage("§7Le bonus est maintenant actif!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

            UUID playerId = player.getUniqueId();
            Integer currentPage = currentPages.getOrDefault(playerId, 0);

            // Sauvegarder
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());

            // Rafraîchir l'interface
            player.closeInventory();
            openCombinedMenu(player, currentPage);

        } catch (IllegalArgumentException e) {
            player.sendMessage("§c❌ Bonus invalide!");
        }
    }

    /**
     * Gère le déverrouillage d'une récompense (gratuit)
     */
    private void handleRewardChoice(Player player, String rewardId, int prestigeLevel) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier que le niveau est débloqué
        if (prestigeLevel > playerData.getPrestigeLevel()) {
            player.sendMessage("§c❌ Vous devez atteindre P" + prestigeLevel + " pour choisir cette récompense!");
            return;
        }

        // Vérifier si un choix a déjà été fait pour ce niveau
        if (playerData.hasRewardChoiceForLevel(prestigeLevel)) {
            String existingChoice = playerData.getChosenSpecialReward(prestigeLevel);
            player.sendMessage("§c❌ Vous avez déjà choisi une récompense pour P" + prestigeLevel + "!");

            // Trouver le nom de la récompense existante
            PrestigeReward existingReward = findRewardById(existingChoice);
            if (existingReward != null) {
                player.sendMessage("§7Choix actuel: §e" + existingReward.displayName());
            }
            return;
        }

        // Récupérer la récompense
        PrestigeReward reward = findRewardById(rewardId);
        if (reward == null) {
            player.sendMessage("§c❌ Récompense introuvable!");
            return;
        }

        // Faire le choix
        playerData.chooseSpecialReward(prestigeLevel, rewardId);

        // Donner la récompense
        plugin.getPrestigeManager().getRewardManager().giveSpecialReward(player, reward);

        // Messages et effets
        player.sendMessage("§a✅ Récompense choisie : " + reward.displayName());
        player.sendMessage("§7Cette récompense a été appliquée à votre compte!");
        player.sendMessage("§7Les autres récompenses de P" + prestigeLevel + " ne sont plus disponibles.");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        UUID playerId = player.getUniqueId();
        Integer currentPage = currentPages.getOrDefault(playerId, 0);

        // Sauvegarder
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Rafraîchir l'interface
        player.closeInventory();
        openCombinedMenu(player, currentPage);
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
                if (reward.id().equals(rewardId)) {
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

        // NOUVEAU : Ajouter la confirmation en attente avec timestamp
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Obtenir l'instance de PrestigeCommand pour accéder à la Map
        PrestigeCommand prestigeCommand = (PrestigeCommand) plugin.getCommand("prestige").getExecutor();
        prestigeCommand.addPendingResetConfirmation(playerId, currentTime);

        // Confirmation avec chrono
        player.sendMessage("§6⚠ CONFIRMATION REQUISE ⚠");
        player.sendMessage("§7Cette action va:");
        player.sendMessage("§7• Réinitialiser TOUS vos talents de prestige");
        player.sendMessage("§7• Coûter §c500 beacons");
        player.sendMessage("§7• Les récompenses spéciales ne seront PAS récupérables");
        player.sendMessage("");
        player.sendMessage("§aTapez §e/prestige confirmer-reset §apour confirmer");
        player.sendMessage("§c⏰ Vous avez 30 secondes pour confirmer");

        player.closeInventory();
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 0.8f);

        // Programmer l'expiration automatique
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (prestigeCommand.removePendingResetConfirmation(playerId, currentTime)) {
                if (player.isOnline()) {
                    player.sendMessage("§c⏰ Délai de confirmation écoulé pour la réinitialisation des talents.");
                }
            }
        }, RESET_CONFIRMATION_TIMEOUT / 50); // Convertir ms en ticks
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

    private ItemStack createPrestigeInfoItem(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        Material material = prestigeLevel > 0 ? Material.NETHER_STAR : Material.GRAY_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            plugin.getGUIManager().applyName(meta, "§6🏆 Votre Prestige");

            List<String> lore = new ArrayList<>();
            lore.add("§7Niveau actuel: " + playerData.getPrestigeDisplayName());
            lore.add("");

            if (prestigeLevel > 0) {
                lore.add("§e⚡ Bonus actifs:");

                // Calculer les bonus totaux
                double moneyBonus = plugin.getGlobalBonusManager().getPrestigeMoneyGreedBonus(player);
                double tokenBonus = plugin.getGlobalBonusManager().getPrestigeTokenGreedBonus(player);
                double taxReduction = plugin.getGlobalBonusManager().getPrestigeTaxReduction(player);
                double sellBonus = plugin.getGlobalBonusManager().getPrestigeSellBonus(player);

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

            plugin.getGUIManager().applyLore(meta, lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crée le bouton principal pour accéder aux talents & récompenses
     */
    private ItemStack createCombinedButton(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentPrestige = playerData.getPrestigeLevel();

        // Calculer les statistiques avec le nouveau système
        int availableTalents = 0;
        int availableRewards = 0;
        int chosenTalents = 0;
        int chosenRewards = 0;
        int totalTalentLevels = Math.max(currentPrestige, 1);
        int totalRewardLevels = currentPrestige / 5; // Niveaux P5, P10, P15, etc.

        // Compter les talents disponibles et choisis (nouveau système)
        Map<Integer, PrestigeTalent> chosenColumns = playerData.getChosenPrestigeColumns();
        for (int level = 1; level <= currentPrestige; level++) {
            if (level % 5 != 0) { // Pas un niveau de récompense
                if (chosenColumns.containsKey(level)) {
                    chosenTalents++;
                } else {
                    // Vérifier si des talents sont disponibles pour ce niveau
                    PrestigeTalent[] availableTalentsForLevel = PrestigeTalent.getTalentsForPrestige(level);
                    if (availableTalentsForLevel.length > 0) {
                        availableTalents++;
                    }
                }
            }
        }

        // Compter les récompenses disponibles et choisies (nouveau système)
        Map<Integer, String> chosenSpecialRewards = playerData.getChosenSpecialRewards();
        for (int level = 5; level <= currentPrestige; level += 5) {
            if (chosenSpecialRewards.containsKey(level)) {
                chosenRewards++;
            } else {
                // Vérifier si des récompenses sont disponibles pour ce niveau
                List<PrestigeReward> rewardsForLevel = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(level);
                if (!rewardsForLevel.isEmpty()) {
                    availableRewards++;
                }
            }
        }

        // Déterminer le matériau et l'état du bouton
        Material material;
        boolean hasAvailable = availableTalents > 0 || availableRewards > 0;
        boolean hasChosen = chosenTalents > 0 || chosenRewards > 0;

        if (hasAvailable) {
            material = Material.ENCHANTED_BOOK; // Quelque chose d'disponible
        } else if (hasChosen) {
            material = Material.KNOWLEDGE_BOOK; // Seulement des éléments déjà choisis
        } else {
            material = Material.BOOK; // Rien de disponible
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Titre dynamique selon l'état
            String title;
            if (hasAvailable) {
                title = "§e📚 §lTalents & Récompenses §e⭐";
            } else if (hasChosen) {
                title = "§a📚 §lTalents & Récompenses §a✓";
            } else {
                title = "§7📚 §lTalents & Récompenses";
            }
            plugin.getGUIManager().applyName(meta, title);

            List<String> lore = new ArrayList<>();
            lore.add("§7Gérez vos talents et récompenses");
            lore.add("§7de prestige dans un menu unifié.");
            lore.add("");

            // ============ SECTION TALENTS ============
            lore.add("§6⭐ TALENTS DE PRESTIGE:");
            if (chosenTalents > 0) {
                lore.add("§a• Bonus sélectionnés: §e" + chosenTalents + "§7/" + (totalTalentLevels - totalRewardLevels));

                // Afficher les bonus totaux actifs
                Map<PrestigeTalent, Integer> activeTalents = playerData.getPrestigeTalents();
                double moneyGreed = calculateTotalColumnBonus(activeTalents, PrestigeTalent.MONEY_GREED_BONUS);
                double tokenGreed = calculateTotalColumnBonus(activeTalents, PrestigeTalent.TOKEN_GREED_BONUS);
                double sellBonus = calculateTotalColumnBonus(activeTalents, PrestigeTalent.SELL_PRICE_BONUS);
                double outpostBonus = calculateTotalColumnBonus(activeTalents, PrestigeTalent.OUTPOST_BONUS);
                double taxReduction = calculateTotalColumnBonus(activeTalents, PrestigeTalent.TAX_REDUCTION);
                double pvpReduction = calculateTotalColumnBonus(activeTalents, PrestigeTalent.PVP_MERCHANT_REDUCTION);

                List<String> activeBonus = new ArrayList<>();
                if (moneyGreed > 0) activeBonus.add("§6Money +" + (int) moneyGreed + "%");
                if (tokenGreed > 0) activeBonus.add("§bToken +" + (int) tokenGreed + "%");
                if (sellBonus > 0) activeBonus.add("§aVente +" + (int) sellBonus + "%");
                if (outpostBonus > 0) activeBonus.add("§eAvant-poste +" + (int) outpostBonus + "%");
                if (taxReduction > 0) activeBonus.add("§cTaxe -" + (int) taxReduction + "%");
                if (pvpReduction > 0) activeBonus.add("§9PvP -" + (int) pvpReduction + "%");

                if (!activeBonus.isEmpty()) {
                    String bonusLine = String.join("§7, ", activeBonus);
                    // Couper la ligne si elle est trop longue
                    if (bonusLine.length() > 40) {
                        lore.add("§7  Actifs: " + activeBonus.getFirst() + "§7...");
                    } else {
                        lore.add("§7  Actifs: " + bonusLine);
                    }
                }
            } else {
                lore.add("§c• Aucun bonus sélectionné");
            }

            if (availableTalents > 0) {
                lore.add("§e• Disponibles: §a" + availableTalents + " choix§7 en attente");
            }

            lore.add("");

            // ============ SECTION RÉCOMPENSES ============
            lore.add("§d🎁 RÉCOMPENSES SPÉCIALES:");
            if (chosenRewards > 0) {
                lore.add("§a• Récompenses réclamées: §e" + chosenRewards + "§7/" + totalRewardLevels);

                // Afficher quelques récompenses réclamées
                List<String> rewardNames = new ArrayList<>();
                for (Map.Entry<Integer, String> entry : chosenSpecialRewards.entrySet()) {
                    if (rewardNames.size() >= 2) break; // Limite à 2 pour l'espace

                    PrestigeReward reward = findRewardById(entry.getValue());
                    String name = reward != null ? reward.displayName() : "Récompense P" + entry.getKey();
                    rewardNames.add("§eP" + entry.getKey() + ": §7" + name);
                }

                if (!rewardNames.isEmpty()) {
                    for (String rewardName : rewardNames) {
                        lore.add("§7  " + rewardName);
                    }
                    if (chosenRewards > rewardNames.size()) {
                        lore.add("§7  ... et " + (chosenRewards - rewardNames.size()) + " autre(s)");
                    }
                }
            } else {
                lore.add("§c• Aucune récompense réclamée");
            }

            if (availableRewards > 0) {
                lore.add("§e• Disponibles: §a" + availableRewards + " récompense" + (availableRewards > 1 ? "s" : ""));
            }

            lore.add("");

            // ============ SECTION ACTION ============
            if (hasAvailable) {
                lore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§6✨ ACTIONS DISPONIBLES ✨");
                if (availableTalents > 0) {
                    lore.add("§7• Sélectionner " + availableTalents + " talent" + (availableTalents > 1 ? "s" : ""));
                }
                if (availableRewards > 0) {
                    lore.add("§7• Réclamer " + availableRewards + " récompense" + (availableRewards > 1 ? "s" : ""));
                }
                lore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§e➤ Cliquez pour ouvrir le menu");

                // Ajouter un effet brillant
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else if (hasChosen) {
                lore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§a✅ PROGRESSION COMPLÈTE");
                lore.add("§7Tous les talents et récompenses");
                lore.add("§7disponibles ont été sélectionnés.");
                lore.add("§a▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§e➤ Cliquez pour voir votre progression");
            } else {
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§7Progressez en prestige pour débloquer");
                lore.add("§7des talents et récompenses spéciales!");
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§e➤ Cliquez pour voir les prestiges");
            }

            plugin.getGUIManager().applyLore(meta, lore);

            // Action pour ouvrir le menu combiné
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
            plugin.getGUIManager().applyName(meta, "§c🔄 Réinitialiser Talents");

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

            plugin.getGUIManager().applyLore(meta, lore);

            if (hasEnoughBeacons && hasTalents) {
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "reset_talents");
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createPerformPrestigeButton(Player player, int nextLevel) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            plugin.getGUIManager().applyName(meta, "§6🚀 Effectuer Prestige " + nextLevel);
            List<String> lore = new ArrayList<>();
            lore.add("§7Passez au niveau de prestige suivant");
            lore.add("§7et débloquez de nouveaux bonus!");
            lore.add("");

            // Afficher le coût de prestige
            long cost = plugin.getPrestigeManager().getPrestigeCost(player, nextLevel);
            lore.add("§7Coût: §c" + NumberFormatter.format(cost) + " coins");

            // Afficher l'effet de la banque
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            BankType bankType = playerData.getBankType();
            if (bankType != BankType.NONE && bankType.getPrestigeCostMultiplier() != 1.0) {
                double multiplier = bankType.getPrestigeCostMultiplier();
                double percentage = (multiplier - 1.0) * 100.0;
                String effect = percentage > 0 ? "§c+" + String.format("%.0f%%", percentage) : "§a" + String.format("%.0f%%", percentage);
                lore.add("§7Effet banque: " + effect + " §7sur le coût");
            }

            lore.add("");
            lore.add("§aConditions remplies!");
            lore.add("");
            lore.add("§eCliquez pour prestigier!");

            plugin.getGUIManager().applyLore(meta, lore);
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
            plugin.getGUIManager().applyName(meta, "§c🔒 Prestige Verrouillé");
            plugin.getGUIManager().applyLore(meta, List.of(
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

    private ItemStack createPageButton(String displayName, int targetPage) {
        ItemStack item = new ItemStack(targetPage > currentPages.getOrDefault(UUID.randomUUID(), 0) ?
                Material.ARROW : Material.SPECTRAL_ARROW);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            plugin.getGUIManager().applyName(meta, displayName);

            List<String> lore = new ArrayList<>();
            lore.add("§7Aller à la page " + (targetPage + 1));
            lore.add("§e➤ Cliquez pour naviguer");
            plugin.getGUIManager().applyLore(meta, lore);

            // CORRECTION: Utiliser la bonne action et clé
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "page_navigation");
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "target_page"), PersistentDataType.INTEGER, targetPage);

            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createBackToMainButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            plugin.getGUIManager().applyName(meta, "§c← Retour au menu principal");
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_main");
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createHelpItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            plugin.getGUIManager().applyName(meta, "§e❓ Aide");
            plugin.getGUIManager().applyLore(meta, List.of(
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
            plugin.getGUIManager().applyName(meta, "§c✗ Fermer");
            item.setItemMeta(meta);
        }

        return item;
    }

    public void onPlayerQuit(Player player) {
        UUID playerId = player.getUniqueId();
        currentPages.remove(playerId);
    }

    private ItemStack createTalentSummaryButton(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            plugin.getGUIManager().applyName(meta, "§6📋 §lRésumé de Progression §6📋");

            List<String> lore = new ArrayList<>();
            lore.add("§7Votre progression complète en prestige:");
            lore.add("");

            // ============ SECTION BONUS ACTIFS ============
            Map<Integer, PrestigeTalent> chosenColumns = playerData.getChosenPrestigeColumns();
            Map<PrestigeTalent, Integer> activeTalents = playerData.getPrestigeTalents();

            lore.add("§6🌟 BONUS ACTIFS:");
            if (chosenColumns.isEmpty()) {
                lore.add("§c❌ Aucun bonus sélectionné");
                lore.add("§7Progressez en prestige pour débloquer");
                lore.add("§7des bonus et améliorer vos performances!");
            } else {
                // Grouper par type de bonus avec comptage
                Map<PrestigeTalent, List<Integer>> bonusLevels = new HashMap<>();

                for (Map.Entry<Integer, PrestigeTalent> entry : chosenColumns.entrySet()) {
                    PrestigeTalent talent = entry.getValue();
                    bonusLevels.computeIfAbsent(talent, k -> new ArrayList<>()).add(entry.getKey());
                }

                // Afficher chaque type de bonus
                for (Map.Entry<PrestigeTalent, List<Integer>> entry : bonusLevels.entrySet()) {
                    PrestigeTalent talent = entry.getKey();
                    List<Integer> levels = entry.getValue();
                    int totalLevel = levels.size(); // Nouveau calcul : nombre de fois choisi

                    lore.add("§a• " + talent.getDisplayName() + " §7(×" + totalLevel + ")");
                    lore.add("§7  Niveaux: §6" + levels.stream()
                            .sorted()
                            .map(l -> "P" + l)
                            .collect(joining(", ")));
                }

                lore.add("");
                // Calculs des bonus totaux
                double moneyGreed = calculateTotalColumnBonus(activeTalents, PrestigeTalent.MONEY_GREED_BONUS);
                double tokenGreed = calculateTotalColumnBonus(activeTalents, PrestigeTalent.TOKEN_GREED_BONUS);
                double sellBonus = calculateTotalColumnBonus(activeTalents, PrestigeTalent.SELL_PRICE_BONUS);
                double outpostBonus = calculateTotalColumnBonus(activeTalents, PrestigeTalent.OUTPOST_BONUS);
                double taxReduction = calculateTotalColumnBonus(activeTalents, PrestigeTalent.TAX_REDUCTION);
                double pvpReduction = calculateTotalColumnBonus(activeTalents, PrestigeTalent.PVP_MERCHANT_REDUCTION);

                lore.add("§6📊 BONUS TOTAUX:");
                if (moneyGreed > 0) lore.add("§6• Money Greed: +" + (int) moneyGreed + "%");
                if (tokenGreed > 0) lore.add("§b• Token Greed: +" + (int) tokenGreed + "%");
                if (sellBonus > 0) lore.add("§a• Prix de vente: +" + (int) sellBonus + "%");
                if (outpostBonus > 0) lore.add("§e• Gain avant-poste: +" + (int) outpostBonus + "%");
                if (taxReduction > 0) lore.add("§c• Réduction taxe: -" + (int) taxReduction + "%");
                if (pvpReduction > 0) lore.add("§9• Prix marchand PvP: -" + (int) pvpReduction + "%");
            }

            lore.add("");

            // ============ SECTION RÉCOMPENSES SPÉCIALES ============
            Map<Integer, String> chosenRewards = playerData.getChosenSpecialRewards();
            lore.add("§d🎁 RÉCOMPENSES SPÉCIALES:");
            if (chosenRewards.isEmpty()) {
                lore.add("§c❌ Aucune récompense réclamée");
                lore.add("§7Atteignez P5, P10, P15... pour débloquer");
                lore.add("§7des récompenses spéciales!");
            } else {
                for (Map.Entry<Integer, String> entry : chosenRewards.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList()) {
                    int level = entry.getKey();
                    String rewardId = entry.getValue();

                    PrestigeReward reward = findRewardById(rewardId);
                    String rewardName = reward != null ? reward.displayName() : rewardId;

                    lore.add("§a• P" + level + ": §e" + rewardName);
                }
            }

            lore.add("");
            lore.add("§e➤ Cliquez pour voir la progression détaillée");

            plugin.getGUIManager().applyLore(meta, lore);
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_combined");

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Calcule le bonus total pour un type de talent spécifique
     */
    private double calculateTotalColumnBonus(Map<PrestigeTalent, Integer> talents, PrestigeTalent targetTalent) {
        int level = talents.getOrDefault(targetTalent, 0);

        return switch (targetTalent) {
            case MONEY_GREED_BONUS, TOKEN_GREED_BONUS, SELL_PRICE_BONUS, OUTPOST_BONUS -> level * 3.0; // +3% par niveau
            case TAX_REDUCTION, PVP_MERCHANT_REDUCTION -> level * 1.0; // -1% par niveau
        };
    }
}