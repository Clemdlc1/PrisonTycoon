package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.CustomEnchantment;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.EnchantmentCategory;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu d'une catégorie d'enchantements
 */
public class CategoryMenuGUI {

    private final PrisonTycoon plugin;

    public CategoryMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu d'une catégorie d'enchantements
     */
    public void openCategoryMenu(Player player, EnchantmentCategory category) {
        String title = "§6✨ " + category.getDisplayName() + " ✨";
        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Remplissage décoratif
        fillBorders(gui);

        // Tête du joueur
        gui.setItem(4, createPlayerHead(player));

        // Bouton retour
        gui.setItem(45, createBackButton());

        // Enchantements de la catégorie
        var enchantments = plugin.getEnchantmentManager().getEnchantmentsByCategory(category);
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

        for (int i = 0; i < enchantments.size() && i < slots.length; i++) {
            CustomEnchantment enchantment = enchantments.get(i);
            gui.setItem(slots[i], createEnchantmentItem(enchantment, player));
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /**
     * Gère les clics dans le menu de catégorie
     */
    public void handleCategoryMenuClick(Player player, int slot, ItemStack item, String title, ClickType clickType) {
        if (slot == 45) { // Bouton retour
            plugin.getMainMenuGUI().openMainMenu(player);
            return;
        }

        // Vérifie si c'est un enchantement
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();

            // Cherche l'enchantement par son nom d'affichage
            CustomEnchantment targetEnchantment = null;
            for (CustomEnchantment enchantment : plugin.getEnchantmentManager().getAllEnchantments()) {
                if (displayName.contains(enchantment.getDisplayName())) {
                    targetEnchantment = enchantment;
                    break;
                }
            }

            if (targetEnchantment != null) {
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                int currentLevel = playerData.getEnchantmentLevel(targetEnchantment.getName());

                // NOUVEAU: Gestion du clic molette pour les enchantements mobilité
                if (clickType == ClickType.MIDDLE && targetEnchantment.getCategory() == EnchantmentCategory.MOBILITY) {
                    if (currentLevel > 0) { // Seulement si l'enchantement est acheté
                        toggleMobilityEnchantment(player, targetEnchantment.getName(), playerData);
                        // Rouvre le menu pour actualiser l'affichage
                        openCategoryMenu(player, targetEnchantment.getCategory());
                        return;
                    }
                }

                if (currentLevel >= targetEnchantment.getMaxLevel()) {
                    // Animation pour niveau max
                    animateMaxLevelItem(player, slot);
                } else {
                    plugin.getEnchantmentUpgradeGUI().openEnchantmentUpgradeMenu(player, targetEnchantment.getName());
                }
            }
        }
    }

    /**
     * NOUVEAU: Active/Désactive un enchantement de mobilité
     */
    private void toggleMobilityEnchantment(Player player, String enchantmentName, PlayerData playerData) {
        boolean currentlyEnabled = playerData.isMobilityEnchantmentEnabled(enchantmentName);
        boolean newState = !currentlyEnabled;

        playerData.setMobilityEnchantmentEnabled(enchantmentName, newState);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Met à jour les effets de mobilité
        plugin.getPickaxeManager().updateMobilityEffects(player);

        CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
        String enchantDisplayName = enchantment != null ? enchantment.getDisplayName() : enchantmentName;

        if (newState) {
            player.sendMessage("§a✅ " + enchantDisplayName + " §aactivé!");
        } else {
            player.sendMessage("§c❌ " + enchantDisplayName + " §cdésactivé!");
        }

        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, newState ? 1.2f : 0.8f);
    }

    /**
     * Crée un item d'enchantement avec les nouvelles informations
     */
    private ItemStack createEnchantmentItem(CustomEnchantment enchantment, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantment.getName());
        long availableTokens = playerData.getTokens();

        ItemStack item = new ItemStack(enchantment.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        // Détermine le statut et les couleurs
        String statusColor;
        String statusText;

        if (currentLevel >= enchantment.getMaxLevel()) {
            statusColor = "§2"; // Vert foncé - Max
            statusText = "MAXIMUM";
        } else {
            long nextCost = enchantment.getUpgradeCost(currentLevel + 1);
            if (availableTokens >= nextCost) {
                statusColor = "§a"; // Vert - Améliorable
                statusText = "AMÉLIORABLE";
            } else {
                statusColor = "§c"; // Rouge - Insuffisant
                statusText = "BLOQUÉ";
            }
        }

        // Nom avec statut
        String levelDisplay = currentLevel == 0 ? "§7[Niveau 0]" :
                (enchantment.getMaxLevel() == Integer.MAX_VALUE ?
                        "§a[Niveau " + NumberFormatter.format(currentLevel) + "]" :
                        "§a[Niveau " + currentLevel + "§7/§e" + enchantment.getMaxLevel() + "§a]");

        meta.setDisplayName(statusColor + "✦ " + enchantment.getDisplayName() + " " + levelDisplay);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + enchantment.getDescription());
        lore.add("§7Catégorie: " + enchantment.getCategory().getDisplayName());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        // Statut standardisé
        lore.add("§6📊 §lSTATUT ACTUEL");
        lore.add("§7▸ État: " + statusColor + statusText);
        lore.add("§7▸ Niveau actuel: §e" + NumberFormatter.format(currentLevel));
        lore.add("§7▸ Niveau maximum: " + (enchantment.getMaxLevel() == Integer.MAX_VALUE ?
                "§e∞" : "§e" + enchantment.getMaxLevel()));
        lore.add("§7▸ Tokens disponibles: §6" + NumberFormatter.format(availableTokens));

        if (currentLevel < enchantment.getMaxLevel()) {
            long nextCost = enchantment.getUpgradeCost(currentLevel + 1);
            lore.add("§7▸ Coût prochain niveau: §e" + NumberFormatter.format(nextCost));

            if (availableTokens < nextCost) {
                long missing = nextCost - availableTokens;
                lore.add("§7▸ Tokens manquants: §c" + NumberFormatter.format(missing));
            }
        }
        lore.add("");

        // Niveaux possibles avec tokens actuels
        if (currentLevel < enchantment.getMaxLevel()) {
            int maxAffordable = calculateMaxAffordableUpgrades(enchantment, currentLevel, availableTokens);

            lore.add("§e⚡ §lAMÉLIORATIONS POSSIBLES");
            if (maxAffordable > 0) {
                lore.add("§7▸ Niveaux améliorables: §a+" + maxAffordable);

                if (maxAffordable >= 1) {
                    lore.add("§7▸ +1 niveau: §6" + NumberFormatter.format(enchantment.getUpgradeCost(currentLevel + 1)));
                }
                if (maxAffordable >= 5) {
                    long cost5 = 0;
                    for (int i = 1; i <= Math.min(5, maxAffordable); i++) {
                        cost5 += enchantment.getUpgradeCost(currentLevel + i);
                    }
                    lore.add("§7▸ +5 niveaux: §6" + NumberFormatter.format(cost5));
                }

                // CORRIGÉ: Après MAX niveau achetable (pas +1)
                lore.add("§7▸ Nouveau niveau: §a" + (currentLevel + maxAffordable));

            } else {
                lore.add("§7▸ §cAucune amélioration possible");
                lore.add("§7▸ §7Continuez à miner pour plus de tokens");
            }
            lore.add("");
        }

        // Effets actuels
        if (currentLevel > 0) {
            lore.add("§a📈 §lEFFETS ACTUELS");
            addEnchantmentEffectPreview(lore, enchantment, currentLevel);
            lore.add("");
        }

        // NOUVEAU: État mobilité si applicable
        if (enchantment.getCategory() == EnchantmentCategory.MOBILITY && currentLevel > 0) {
            boolean enabled = playerData.isMobilityEnchantmentEnabled(enchantment.getName());
            lore.add("§b🎮 §lÉTAT MOBILITÉ");
            lore.add("§7▸ Enchantement: " + (enabled ? "§aActivé" : "§cDésactivé"));
            lore.add("§7▸ §eClique molette pour " + (enabled ? "désactiver" : "activer"));
            lore.add("");
        }

        // Auto-upgrade status
        if (plugin.getEnchantmentManager().canUseAutoUpgrade(player) &&
                playerData.isAutoUpgradeEnabled(enchantment.getName())) {
            lore.add("§a🔄 §lAUTO-AMÉLIORATION ACTIVE");
            lore.add("§7▸ Se met à niveau automatiquement");
            lore.add("");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        String clickAction = currentLevel >= enchantment.getMaxLevel() ? "voir détails" : "améliorer";
        lore.add("§e✨ Cliquez pour " + clickAction);

        if (enchantment.getCategory() == EnchantmentCategory.MOBILITY && currentLevel > 0) {
            lore.add("§e🎮 Clic molette pour activer/désactiver");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Ajoute un aperçu des effets d'un enchantement
     */
    private void addEnchantmentEffectPreview(List<String> lore, CustomEnchantment enchantment, int level) {
        switch (enchantment.getName()) {
            case "token_greed" -> {
                long tokensPerTrigger = level * 5;
                lore.add("§7▸ §6+" + NumberFormatter.format(tokensPerTrigger) + " tokens par déclenchement");
                lore.add("§7▸ §e5% chance de base par bloc détruit");
            }
            case "exp_greed" -> {
                long expPerTrigger = level * 50;
                lore.add("§7▸ §a+" + NumberFormatter.format(expPerTrigger) + " XP par déclenchement");
                lore.add("§7▸ §e5% chance de base par bloc détruit");
            }
            case "money_greed" -> {
                long coinsPerTrigger = level * 10;
                lore.add("§7▸ §6+" + NumberFormatter.format(coinsPerTrigger) + " coins par déclenchement");
                lore.add("§7▸ §e5% chance de base par bloc détruit");
            }
            case "luck" -> {
                double bonusChance = level * 0.2;
                lore.add("§7▸ §e+" + String.format("%.1f%%", bonusChance) + " chance bonus pour tous les Greeds");
                lore.add("§7▸ §7Améliore Token, Exp, Money Greed");
            }
            case "fortune" -> {
                double multiplier = 1.0 + (level * 0.2);
                lore.add("§7▸ §ax" + String.format("%.1f", multiplier) + " multiplicateur de tous les gains");
                lore.add("§7▸ §7Affecte coins, tokens ET expérience");
            }
            case "combustion" -> {
                int gainPerBlock = Math.max(1, level / 10);
                lore.add("§7▸ §c+" + gainPerBlock + " combustion par bloc miné");
                lore.add("§7▸ §7Jusqu'à x2 multiplicateur à 1000 combustion");
            }
            case "key_greed" -> {
                double chancePerBlock = level * 1.0;
                lore.add("§7▸ §e" + String.format("%.1f%%", chancePerBlock) + " chance de clé par bloc");
                lore.add("§7▸ §7Clés: Commune, Rare, Légendaire, Cristal");
            }
            default -> {
                lore.add("§7▸ §7Effet de niveau " + level + " actif");
            }
        }
    }

    /**
     * Calcule le nombre maximum de niveaux abordables
     */
    private int calculateMaxAffordableUpgrades(CustomEnchantment enchantment, int currentLevel, long availableTokens) {
        if (currentLevel >= enchantment.getMaxLevel()) {
            return 0;
        }

        int maxLevels = 0;
        long remainingTokens = availableTokens;

        for (int level = currentLevel + 1; level <= enchantment.getMaxLevel() && remainingTokens > 0; level++) {
            long cost = enchantment.getUpgradeCost(level);
            if (remainingTokens >= cost) {
                remainingTokens -= cost;
                maxLevels++;
            } else {
                break;
            }
        }

        return maxLevels;
    }

    /**
     * Crée la tête du joueur avec ses statistiques
     */
    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(player);
        meta.setDisplayName("§6📊 " + player.getName());

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("§6💰 §lÉCONOMIE ACTUELLE");
        lore.add("§7│ §6Coins: §e" + NumberFormatter.format(playerData.getCoins()));
        lore.add("§7│ §eTokens: §6" + NumberFormatter.format(playerData.getTokens()));
        lore.add("§7│ §aExpérience: §2" + NumberFormatter.format(playerData.getExperience()));
        lore.add("§7│ §bNiveau vanilla: §3" + player.getLevel());
        lore.add("§7└ §7Enchantements actifs: §b" + playerData.getEnchantmentLevels().size());

        meta.setLore(lore);
        head.setItemMeta(meta);

        return head;
    }

    /**
     * Crée le bouton retour
     */
    private ItemStack createBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();

        meta.setDisplayName("§7← Retour");
        meta.setLore(List.of("§7Retourner au menu principal"));

        arrow.setItemMeta(meta);
        return arrow;
    }

    /**
     * Animation pour niveau max
     */
    private void animateMaxLevelItem(Player player, int slot) {
        Inventory gui = player.getOpenInventory().getTopInventory();
        ItemStack originalItem = gui.getItem(slot);

        ItemStack redBlock = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = redBlock.getItemMeta();
        meta.setDisplayName("§c❌ Niveau maximum atteint");
        redBlock.setItemMeta(meta);

        gui.setItem(slot, redBlock);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.getOpenInventory().getTopInventory().equals(gui)) {
                gui.setItem(slot, originalItem);
            }
        }, 20L);
    }

    /**
     * Remplit les bordures avec des items décoratifs
     */
    private void fillBorders(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName("§7");
        filler.setItemMeta(meta);

        int size = gui.getSize();

        for (int i = 0; i < 9; i++) {
            gui.setItem(i, filler);
            if (size > 9) {
                gui.setItem(size - 9 + i, filler);
            }
        }

        for (int i = 9; i < size - 9; i += 9) {
            gui.setItem(i, filler);
            gui.setItem(i + 8, filler);
        }
    }
}