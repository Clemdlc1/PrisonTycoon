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
        int maxPrestige = Math.max(5, playerData.getPrestigeLevel()); // Au minimum afficher jusqu'à P5
        int maxPage = (maxPrestige - 1) / 5; // 5 prestiges par page

        // Clamp de la page
        page = Math.max(0, Math.min(page, maxPage));

        Inventory gui = Bukkit.createInventory(null, 54,
                "§5⚡ Progression Prestige §7(P" + (page * 5 + 1) + "-" + Math.min((page + 1) * 5, maxPrestige) + ")");

        fillWithGlass(gui);
        setupCombinedMenu(gui, player, page);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
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
     * Configure une ligne de prestige (3 colonnes : talent1, talent2/spécial, talent3)
     */
    private void setupPrestigeRow(Inventory gui, Player player, int prestigeLevel, int baseSlot) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean isUnlocked = prestigeLevel <= playerData.getPrestigeLevel();

        if (prestigeLevel % 5 == 0) {
            String[] rewardline = reward.getDescription().split("\n");
            for (int col = 0; col < 3 && col < rewardline.length; col++) {
                gui.setItem(baseSlot + col, createRewardItem(player, prestigeLevel, isUnlocked));
            }
        } else {
            // Palier normal avec 3 talents
            PrestigeTalent talent = PrestigeTalent.getTalentForPrestige(prestigeLevel);
            if (talent != null) {
                // Afficher les 3 bonus du talent dans les 3 colonnes
                String[] bonusLines = talent.getDescription().split("\n");
                for (int col = 0; col < 3 && col < bonusLines.length; col++) {
                    gui.setItem(baseSlot + col, createTalentColumnItem(player, prestigeLevel, talent, bonusLines[col], col, isUnlocked));
                }
            }
        }
    }

    /**
     * Crée un item pour une colonne de talent
     */
    private ItemStack createTalentColumnItem(Player player, int prestigeLevel, PrestigeTalent talent,
                                             String bonusDescription, int column, boolean isUnlocked) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Matériaux selon la colonne et le type
        Material material = isUnlocked ? getMaterialForTalentColumn(talent, column) : Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nom selon la colonne
            String columnName = getColumnName(talent, column);
            meta.setDisplayName(isUnlocked ? "§6" + columnName : "§7" + columnName);

            List<String> lore = new ArrayList<>();
            lore.add("§7Prestige: §e" + prestigeLevel);
            lore.add("§7Bonus: " + bonusDescription);
            lore.add("");

            if (isUnlocked) {
                int talentLevel = playerData.getPrestigeTalents().getOrDefault(talent, 0);
                int availableLevel = PrestigeTalent.getTalentLevel(talent, playerData.getPrestigeLevel());
                lore.add("§7Niveau actuel: §a" + talentLevel + "/" + availableLevel);
                lore.add("§aCet bonus est actif!");
            } else {
                lore.add("§cRequiert le prestige " + prestigeLevel);
            }

            meta.setLore(lore);

            // Enchantement si actif
            if (isUnlocked) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            item.setItemMeta(meta);
        }

        return item;
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
     * Obtient le nom de la colonne
     */
    private String getColumnName(PrestigeTalent talent, int column) {
        return switch (talent) {
            case PROFIT_AMELIORE -> switch (column) {
                case 0 -> "Money Greed";
                case 1 -> "Prix Vente";
                case 2 -> "Gain Avant-poste";
                default -> "Bonus";
            };
            case ECONOMIE_OPTIMISEE -> switch (column) {
                case 0 -> "Token Greed";
                case 1 -> "Réduction Taxe";
                case 2 -> "Prix PvP";
                default -> "Bonus";
            };
            case PROFIT_AMELIORE_II -> switch (column) {
                case 0 -> "Effet Money Greed";
                case 1 -> "Prix Vente Direct";
                case 2 -> "Rinacoins Avant-poste";
                default -> "Bonus";
            };
            case ECONOMIE_OPTIMISEE_II -> switch (column) {
                case 0 -> "Effet Token Greed";
                case 1 -> "Taux Taxe Final";
                case 2 -> "Prix PvP Final";
                default -> "Bonus";
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
            case "back_to_main" -> openMainPrestigeMenu(player);
        }
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

        // Vérifications finales
        if (playerData.getBeacons() < 500) {
            player.sendMessage("§c❌ Beacons insuffisants!");
            return;
        }

        // Effectuer la réinitialisation
        playerData.removeBeacon(500);
        playerData.clearPrestigeTalents();

        // Réappliquer les bonus au GlobalBonusManager

        // Messages et effets
        player.sendMessage("§a✅ Talents de prestige réinitialisés!");
        player.sendMessage("§7Coût: §c-500 beacons");
        player.sendMessage("§7Vous pouvez maintenant réattribuer vos talents selon vos nouveaux prestiges");

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
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