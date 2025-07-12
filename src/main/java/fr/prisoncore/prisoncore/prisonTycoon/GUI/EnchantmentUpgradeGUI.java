package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.CustomEnchantment;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Menu d'amélioration d'un enchantement
 * CORRIGÉ : Effets avant/après pour tous les cas et auto-upgrade fonctionnel + noms en gras
 */
public class EnchantmentUpgradeGUI {

    private final PrisonTycoon plugin;

    public EnchantmentUpgradeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu d'amélioration d'un enchantement
     */
    public void openEnchantmentUpgradeMenu(Player player, String enchantmentName) {
        CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
        if (enchantment == null) return;

        // CORRECTION: Nom en gras
        String title = "§6🔧 §l" + enchantment.getDisplayName() + " §6🔧";
        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Remplissage décoratif
        fillBorders(gui);

        // Tête du joueur
        gui.setItem(4, createPlayerHead(player));

        // Enchantement central
        gui.setItem(22, createDetailedEnchantmentItem(enchantment, player));

        // Boutons d'amélioration
        createUpgradeButtons(gui, enchantment, player);

        // Bouton auto-upgrade (si permission)
        if (plugin.getEnchantmentManager().canUseAutoUpgrade(player)) {
            gui.setItem(45, createAutoUpgradeButton(enchantment, player));
        } else {
            gui.setItem(45, createNoPermissionAutoUpgradeItem());
        }

        // Bouton retour
        gui.setItem(53, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    /**
     * Gère les clics dans le menu d'amélioration
     */
    public void handleUpgradeMenuClick(Player player, int slot, ItemStack item, ClickType clickType, String title) {
        if (slot == 53) { // Bouton retour
            // Retourne au menu de catégorie approprié
            String enchantmentName = extractEnchantmentNameFromTitle(title);
            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
            if (enchantment != null) {
                plugin.getCategoryMenuGUI().openCategoryMenu(player, enchantment.getCategory());
            }
            return;
        }

        if (slot == 45) { // Auto-upgrade
            if (plugin.getEnchantmentManager().canUseAutoUpgrade(player)) {
                toggleAutoUpgrade(player, title);
            } else {
                player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser l'auto-amélioration!");
                player.sendMessage("§7Permissions requises: §especialmine.vip §7ou §especialmine.admin");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        // Boutons d'amélioration
        if (item != null && item.hasItemMeta()) {
            String displayName = item.getItemMeta().getDisplayName();
            String enchantmentName = extractEnchantmentNameFromTitle(title);

            if (displayName.contains("+1")) upgradeEnchantment(player, enchantmentName, 1);
            else if (displayName.contains("+5")) upgradeEnchantment(player, enchantmentName, 5);
            else if (displayName.contains("+10")) upgradeEnchantment(player, enchantmentName, 10);
            else if (displayName.contains("+25")) upgradeEnchantment(player, enchantmentName, 25);
            else if (displayName.contains("+100")) upgradeEnchantment(player, enchantmentName, 100);
            else if (displayName.contains("+250")) upgradeEnchantment(player, enchantmentName, 250);
            else if (displayName.contains("+500")) upgradeEnchantment(player, enchantmentName, 500);
            else if (displayName.contains("MAX")) upgradeToMax(player, enchantmentName);
        }
    }

    /**
     * CORRIGÉ: Crée les boutons d'amélioration avec lores détaillés
     */
    private void createUpgradeButtons(Inventory gui, CustomEnchantment enchantment, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantment.getName());

        int[] upgradeAmounts = {1, 5, 10, 25, 100, 250, 500};
        int[] slots = {28, 29, 30, 31, 32, 33, 34};

        for (int i = 0; i < upgradeAmounts.length; i++) {
            int amount = upgradeAmounts[i];
            if (currentLevel + amount <= enchantment.getMaxLevel()) {
                gui.setItem(slots[i], createUpgradeButton(enchantment, player, amount));
            }
        }

        if (currentLevel < enchantment.getMaxLevel()) {
            gui.setItem(37, createMaxUpgradeButton(enchantment, player));
        }
    }

    /**
     * CORRIGÉ : Crée un bouton d'amélioration avec effets avant/après TOUJOURS présents
     */
    private ItemStack createUpgradeButton(CustomEnchantment enchantment, Player player, int requestedLevels) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantment.getName());
        long availableTokens = playerData.getTokens();

        // Calcule le nombre réel de niveaux possibles
        int maxPossibleLevels = Math.min(requestedLevels, enchantment.getMaxLevel() - currentLevel);
        int actualLevels = 0;
        long totalCost = 0;

        for (int i = 1; i <= maxPossibleLevels; i++) {
            long cost = enchantment.getUpgradeCost(currentLevel + i);
            if (totalCost + cost <= availableTokens) {
                totalCost += cost;
                actualLevels = i;
            } else {
                break;
            }
        }

        boolean canAfford = actualLevels > 0;
        Material material = canAfford ? Material.GREEN_CONCRETE : Material.RED_CONCRETE;
        String color = canAfford ? "§a" : "§c";

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (canAfford) {
            meta.setDisplayName(color + "+" + actualLevels + " Niveau" + (actualLevels > 1 ? "x" : ""));
        } else {
            meta.setDisplayName(color + "+" + requestedLevels + " Niveau" + (requestedLevels > 1 ? "x" : "") + " §c(Impossible)");
        }

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (canAfford) {
            // Section amélioration
            lore.add("§6💰 §lAMÉLIORATION");
            lore.add("§7▸ Niveaux à acheter: §a+" + actualLevels);
            lore.add("§7▸ Coût total: §6" + NumberFormatter.format(totalCost) + " tokens");
            lore.add("§7▸ Tokens disponibles: §a" + NumberFormatter.format(availableTokens));
            lore.add("§7▸ Tokens restants: §e" + NumberFormatter.format(availableTokens - totalCost));
            lore.add("");

            // Section progression
            lore.add("§e📈 §lPROGRESSION");
            lore.add("§7▸ Niveau avant: §e" + currentLevel);
            lore.add("§7▸ Niveau après: §a" + (currentLevel + actualLevels));
            lore.add("");

            // NOUVEAU: Effets avant/après
            lore.add("§b🔮 §lEFFETS AVANT → APRÈS");
            addEffectComparison(lore, enchantment, currentLevel, currentLevel + actualLevels);
            lore.add("");

            lore.add("§a✨ Cliquez pour améliorer!");

        } else {
            // Section insuffisant avec détails pour le niveau demandé
            lore.add("§c❌ §lTOKENS INSUFFISANTS");
            lore.add("§7▸ Niveaux demandés: §c+" + requestedLevels);

            if (maxPossibleLevels > 0) {
                // Calcule le coût pour les niveaux demandés
                long costForRequested = 0;
                for (int i = 1; i <= Math.min(requestedLevels, enchantment.getMaxLevel() - currentLevel); i++) {
                    costForRequested += enchantment.getUpgradeCost(currentLevel + i);
                }

                lore.add("§7▸ Coût total demandé: §6" + NumberFormatter.format(costForRequested));
                lore.add("§7▸ Tokens disponibles: §c" + NumberFormatter.format(availableTokens));
                lore.add("§7▸ Tokens manquants: §c" + NumberFormatter.format(costForRequested - availableTokens));

                // Progression vers cet objectif
                double progressPercent = (double) availableTokens / costForRequested * 100;
                lore.add("§7▸ Progression: §e" + String.format("%.1f%%", progressPercent));
            } else {
                lore.add("§7▸ §cNiveau maximum déjà proche");
            }
            lore.add("");

            // CORRECTION: Ajoute les effets même si on ne peut pas se payer l'amélioration
            lore.add("§b🔮 §lEFFETS SI ACHETÉ");
            int targetLevel = Math.min(currentLevel + requestedLevels, enchantment.getMaxLevel());
            addEffectComparison(lore, enchantment, currentLevel, targetLevel);
            lore.add("");

            lore.add("§7Continuez à miner pour obtenir plus de tokens!");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU: Ajoute une comparaison des effets avant/après
     */
    private void addEffectComparison(List<String> lore, CustomEnchantment enchantment, int fromLevel, int toLevel) {
        switch (enchantment.getName()) {
            case "token_greed" -> {
                long fromTokens = fromLevel * 5;
                long toTokens = toLevel * 5;
                lore.add("§7▸ Tokens/Greed: §e" + NumberFormatter.format(fromTokens) +
                        " §7→ §a" + NumberFormatter.format(toTokens));
            }
            case "exp_greed" -> {
                long fromExp = fromLevel * 50;
                long toExp = toLevel * 50;
                lore.add("§7▸ XP/Greed: §e" + NumberFormatter.format(fromExp) +
                        " §7→ §a" + NumberFormatter.format(toExp));
            }
            case "money_greed" -> {
                long fromCoins = fromLevel * 10;
                long toCoins = toLevel * 10;
                lore.add("§7▸ Coins/Greed: §e" + NumberFormatter.format(fromCoins) +
                        " §7→ §a" + NumberFormatter.format(toCoins));
            }
            case "luck" -> {
                double fromBonus = fromLevel * 0.2;
                double toBonus = toLevel * 0.2;
                lore.add("§7▸ Bonus chance: §e+" + String.format("%.1f%%", fromBonus) +
                        " §7→ §a+" + String.format("%.1f%%", toBonus));
            }
            case "fortune" -> {
                double fromMult = 1.0 + (fromLevel * 0.2);
                double toMult = 1.0 + (toLevel * 0.2);
                lore.add("§7▸ Multiplicateur: §ex" + String.format("%.1f", fromMult) +
                        " §7→ §ax" + String.format("%.1f", toMult));
            }
            case "combustion" -> {
                int fromGain = Math.max(1, fromLevel / 10);
                int toGain = Math.max(1, toLevel / 10);
                lore.add("§7▸ Gain/bloc: §e+" + fromGain + " §7→ §a+" + toGain + " combustion");
            }
            case "key_greed" -> {
                double fromChance = fromLevel * 1.0;
                double toChance = toLevel * 1.0;
                lore.add("§7▸ Chance clé: §e" + String.format("%.1f%%", fromChance) +
                        " §7→ §a" + String.format("%.1f%%", toChance));
            }
            default -> {
                lore.add("§7▸ Amélioration de §2+" + (toLevel - fromLevel) + " niveau" +
                        (toLevel - fromLevel > 1 ? "x" : ""));
            }
        }
    }

    /**
     * CORRIGÉ: Crée le bouton MAX avec la même logique améliorée
     */
    private ItemStack createMaxUpgradeButton(CustomEnchantment enchantment, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantment.getName());
        long availableTokens = playerData.getTokens();

        int maxAffordableLevels = 0;
        long totalCost = 0;

        for (int i = 1; i <= (enchantment.getMaxLevel() - currentLevel); i++) {
            long cost = enchantment.getUpgradeCost(currentLevel + i);
            if (totalCost + cost <= availableTokens) {
                totalCost += cost;
                maxAffordableLevels = i;
            } else {
                break;
            }
        }

        boolean canUpgrade = maxAffordableLevels > 0;
        ItemStack item = new ItemStack(canUpgrade ? Material.DIAMOND : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName((canUpgrade ? "§6" : "§c") + "MAX Possible");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (canUpgrade) {
            lore.add("§6💎 §lAMÉLIORATION MAXIMALE");
            lore.add("§7▸ Niveaux disponibles: §a+" + maxAffordableLevels);
            lore.add("§7▸ Coût total: §6" + NumberFormatter.format(totalCost) + " tokens");
            lore.add("§7▸ Niveau avant: §e" + currentLevel);
            lore.add("§7▸ Niveau après: §a" + (currentLevel + maxAffordableLevels));
            lore.add("");

            // Effets max
            lore.add("§b🔮 §lEFFETS MAXIMUM POSSIBLES");
            addEffectComparison(lore, enchantment, currentLevel, currentLevel + maxAffordableLevels);
            lore.add("");

            lore.add("§e✨ Cliquez pour améliorer au maximum!");
        } else {
            lore.add("§c❌ §lAMÉLIORATION IMPOSSIBLE");
            if (currentLevel >= enchantment.getMaxLevel()) {
                lore.add("§7▸ §cNiveau maximum déjà atteint!");
            } else {
                long nextCost = enchantment.getUpgradeCost(currentLevel + 1);
                lore.add("§7▸ §cPas assez de tokens");
                lore.add("§7▸ Coût prochain niveau: §6" + NumberFormatter.format(nextCost));
                lore.add("§7▸ Tokens disponibles: §c" + NumberFormatter.format(availableTokens));
                lore.add("§7▸ Tokens manquants: §c" + NumberFormatter.format(nextCost - availableTokens));
                lore.add("");

                // CORRECTION: Ajoute les effets même si impossible
                lore.add("§b🔮 §lEFFETS SI ACHETÉ");
                addEffectComparison(lore, enchantment, currentLevel, currentLevel + 1);
            }
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * CORRIGÉ: Crée le bouton auto-upgrade avec gestion des permissions
     */
    private ItemStack createAutoUpgradeButton(CustomEnchantment enchantment, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean autoEnabled = playerData.isAutoUpgradeEnabled(enchantment.getName());

        Material material = autoEnabled ? Material.LIME_DYE : Material.RED_DYE;
        String color = autoEnabled ? "§a" : "§c";
        String icon = autoEnabled ? "✅" : "❌";

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color + icon + " §lAuto-amélioration");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Améliore automatiquement cet enchantement");
        lore.add("§7dès que vous avez assez de tokens.");
        lore.add("§7Vérifie toutes les 10 secondes.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        lore.add("§e📊 §lSTATUT");
        lore.add("§7▸ Auto-amélioration: " + (autoEnabled ? "§aActivée" : "§cDésactivée"));

        // Vérifie s'il y a déjà un autre auto-enchant actif
        Set<String> currentAutoUpgrades = playerData.getAutoUpgradeEnabled();
        boolean hasOtherAutoUpgrade = currentAutoUpgrades.stream()
                .anyMatch(name -> !name.equals(enchantment.getName()));

        if (hasOtherAutoUpgrade && !autoEnabled) {
            String activeAutoUpgrade = currentAutoUpgrades.iterator().next();
            CustomEnchantment activeEnchant = plugin.getEnchantmentManager().getEnchantment(activeAutoUpgrade);
            String activeName = activeEnchant != null ? activeEnchant.getDisplayName() : activeAutoUpgrade;

            lore.add("§7▸ Autre auto-amélioration: §e" + activeName);
            lore.add("§7▸ §cActiver ici désactivera l'autre");
        } else if (autoEnabled) {
            lore.add("§7▸ §aCet enchantement s'améliorera automatiquement");
        }

        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✨ Cliquez pour " + (autoEnabled ? "désactiver" : "activer"));
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU: Item pour les joueurs sans permission auto-upgrade
     */
    private ItemStack createNoPermissionAutoUpgradeItem() {
        ItemStack item = new ItemStack(Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§7🔒 §lAuto-amélioration");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Fonctionnalité réservée aux joueurs VIP+");
        lore.add("§7");
        lore.add("§c❌ §lPERMISSION REQUISE");
        lore.add("§7▸ §especialmine.vip §7ou §especialmine.admin");
        lore.add("§7");
        lore.add("§7L'auto-amélioration permet d'améliorer");
        lore.add("§7automatiquement vos enchantements sans");
        lore.add("§7intervention manuelle.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    // Actions et utilitaires...

    private void upgradeEnchantment(Player player, String enchantmentName, int requestedLevels) {
        boolean success = plugin.getEnchantmentManager().upgradeEnchantment(player, enchantmentName, requestedLevels);

        if (success) {
            // Vérifie si niveau max atteint après amélioration
            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            int newLevel = playerData.getEnchantmentLevel(enchantmentName);

            if (newLevel >= enchantment.getMaxLevel()) {
                playerData.setAutoUpgrade(enchantmentName, false);
                player.sendMessage("§2🏆 " + enchantment.getDisplayName() + " §2niveau maximum atteint!");

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    plugin.getCategoryMenuGUI().openCategoryMenu(player, enchantment.getCategory());
                }, 10L);
                return;
            }

            openEnchantmentUpgradeMenu(player, enchantmentName);
        } else {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
        }
    }

    private void upgradeToMax(Player player, String enchantmentName) {
        CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
        if (enchantment == null) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantmentName);

        long availableTokens = playerData.getTokens();
        int maxAffordableLevels = 0;

        for (int i = 1; i <= (enchantment.getMaxLevel() - currentLevel); i++) {
            long cost = enchantment.getUpgradeCost(currentLevel + i);
            if (availableTokens >= cost) {
                availableTokens -= cost;
                maxAffordableLevels = i;
            } else {
                break;
            }
        }

        if (maxAffordableLevels > 0) {
            upgradeEnchantment(player, enchantmentName, maxAffordableLevels);
        } else {
            player.sendMessage("§cPas assez de tokens pour améliorer cet enchantement!");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
        }
    }

    /**
     * CORRIGÉ: Toggle auto-upgrade avec mise à jour GUI fonctionnelle pour tous les enchantements
     */
    private void toggleAutoUpgrade(Player player, String title) {
        String enchantmentName = extractEnchantmentNameFromTitle(title);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        boolean current = playerData.isAutoUpgradeEnabled(enchantmentName);

        if (!current) {
            Set<String> currentAutoUpgrades = new HashSet<>(playerData.getAutoUpgradeEnabled());
            for (String otherEnchant : currentAutoUpgrades) {
                playerData.setAutoUpgrade(otherEnchant, false);
            }

            playerData.setAutoUpgrade(enchantmentName, true);

            CustomEnchantment enchant = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
            String displayName = enchant != null ? enchant.getDisplayName() : enchantmentName;

            player.sendMessage("§a✅ Auto-amélioration activée pour " + displayName);
            if (!currentAutoUpgrades.isEmpty()) {
                player.sendMessage("§7Les autres auto-améliorations ont été désactivées.");
            }

        } else {
            playerData.setAutoUpgrade(enchantmentName, false);

            CustomEnchantment enchant = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
            String displayName = enchant != null ? enchant.getDisplayName() : enchantmentName;

            player.sendMessage("§c❌ Auto-amélioration désactivée pour " + displayName);
        }

        // CORRECTION: Force la mise à jour pour tous les enchantements
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // CORRECTION: Rouvre le menu immédiatement pour voir les changements
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            openEnchantmentUpgradeMenu(player, enchantmentName);
        }, 1L);
    }

    // Utilitaires...

    private ItemStack createDetailedEnchantmentItem(CustomEnchantment enchantment, Player player) {
        ItemStack item = new ItemStack(enchantment.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6✨ §l" + enchantment.getDisplayName() + " §6✨");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + enchantment.getDescription());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(player);
        meta.setDisplayName("§6📊 §l" + player.getName());

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("§6💰 §lÉCONOMIE ACTUELLE");
        lore.add("§7│ §6Coins: §e" + NumberFormatter.format(playerData.getCoins()));
        lore.add("§7│ §eTokens: §6" + NumberFormatter.format(playerData.getTokens()));
        lore.add("§7│ §aExpérience: §2" + NumberFormatter.format(playerData.getExperience()));
        lore.add("§7└ §7Enchantements actifs: §b" + playerData.getEnchantmentLevels().size());

        meta.setLore(lore);
        head.setItemMeta(meta);

        return head;
    }

    private ItemStack createBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();

        meta.setDisplayName("§7← §lRetour");
        meta.setLore(List.of("§7Retourner au menu précédent"));

        arrow.setItemMeta(meta);
        return arrow;
    }

    /**
     * CORRIGÉ: Extraction du nom d'enchantement plus robuste
     */
    private String extractEnchantmentNameFromTitle(String title) {
        // Retire les codes couleur et les caractères spéciaux pour extraire le nom
        String cleanTitle = title.replaceAll("§[0-9a-fk-or]", "").replace("🔧", "").trim();

        // Mappe les noms d'affichage vers les noms internes
        return switch (cleanTitle.toLowerCase()) {
            case "token greed" -> "token_greed";
            case "exp greed" -> "exp_greed";
            case "money greed" -> "money_greed";
            case "key greed" -> "key_greed";
            case "abondance" -> "abundance";
            case "combustion" -> "combustion";
            case "pet xp" -> "pet_xp";
            case "efficacité" -> "efficiency";
            case "fortune" -> "fortune";
            case "solidité" -> "durability";
            case "vision nocturne" -> "night_vision";
            case "vitesse" -> "speed";
            case "rapidité" -> "haste";
            case "saut" -> "jump_boost";
            case "escalateur" -> "escalator";
            case "chance" -> "luck";
            case "laser" -> "laser";
            case "explosion" -> "explosion";
            default -> cleanTitle.toLowerCase().replace(" ", "_");
        };
    }

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