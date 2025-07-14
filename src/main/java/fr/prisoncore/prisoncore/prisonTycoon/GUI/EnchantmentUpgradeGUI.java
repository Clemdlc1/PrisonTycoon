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
 * CORRIGÉ : Boutons d'amélioration FIXES (ne s'adaptent plus au max possible)
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

        String title = "§6🔧 §l" + enchantment.getDisplayName() + " §6🔧";
        Inventory gui = Bukkit.createInventory(null, 27, title);

        // Remplissage décoratif
        fillBorders(gui);

        // Tête du joueur
        gui.setItem(4, createPlayerHead(player));

        // Boutons d'amélioration FIXES
        createFixedUpgradeButtons(gui, enchantment, player);

        gui.setItem(18, createBackButton());

        if (plugin.getEnchantmentManager().canUseAutoUpgrade(player)) {
            gui.setItem(26, createAutoUpgradeButton(enchantment, player));
        } else {
            gui.setItem(26, createNoPermissionAutoUpgradeItem());
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    /**
     * Gère les clics dans le menu d'amélioration
     */
    public void handleUpgradeMenuClick(Player player, int slot, ItemStack item, ClickType clickType, String title) {
        if (slot == 18) { // Bouton retour
            String enchantmentName = extractEnchantmentNameFromTitle(title);
            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
            if (enchantment != null) {
                plugin.getCategoryMenuGUI().openCategoryMenu(player, enchantment.getCategory());
            }
            return;
        }

        if (slot == 26) { // Auto-upgrade
            if (plugin.getEnchantmentManager().canUseAutoUpgrade(player)) {
                toggleAutoUpgrade(player, title);
            } else {
                player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser l'auto-amélioration!");
                player.sendMessage("§7Permissions requises: §especialmine.vip §7ou §especialmine.admin");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        if (slot == 22) { // MAX possible
            String enchantmentName = extractEnchantmentNameFromTitle(title);
            upgradeToMax(player, enchantmentName);
            return;
        }

        // Boutons d'amélioration fixes
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
        }
    }

    /**
     * CORRIGÉ : Crée les boutons d'amélioration FIXES (ne s'adaptent pas)
     */
    private void createFixedUpgradeButtons(Inventory gui, CustomEnchantment enchantment, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantment.getName());

        int[] upgradeAmounts = {1, 5, 10, 25, 100, 250, 500};
        int[] slots = {10, 11, 12, 13, 14, 15, 16};

        for (int i = 0; i < upgradeAmounts.length; i++) {
            int amount = upgradeAmounts[i];

            // CORRECTION : Toujours afficher le bouton, mais en rouge si impossible
            gui.setItem(slots[i], createFixedUpgradeButton(enchantment, player, amount));
        }

        // MAX au milieu dernière ligne (slot 23)
        if (currentLevel < enchantment.getMaxLevel()) {
            gui.setItem(23, createMaxUpgradeButton(enchantment, player));
        }
    }

    /**
     * NOUVEAU : Crée un bouton d'amélioration FIXE qui ne s'adapte jamais
     */
    private ItemStack createFixedUpgradeButton(CustomEnchantment enchantment, Player player, int requestedLevels) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantment.getName());
        long availableTokens = playerData.getTokens();

        // Vérifie si on peut acheter exactement ce nombre de niveaux
        boolean canAffordExact = canAffordExactLevels(enchantment, currentLevel, availableTokens, requestedLevels);
        boolean levelMaxReached = currentLevel + requestedLevels > enchantment.getMaxLevel();

        Material material;
        String color;

        if (levelMaxReached) {
            material = Material.BARRIER;
            color = "§c";
        } else if (canAffordExact) {
            material = Material.GREEN_CONCRETE;
            color = "§a";
        } else {
            material = Material.RED_CONCRETE;
            color = "§c";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color + "+" + requestedLevels + " Niveau" + (requestedLevels > 1 ? "x" : ""));

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (levelMaxReached) {
            // Dépasserait le niveau maximum
            lore.add("§c❌ §lNIVEAU MAXIMUM DÉPASSÉ");
            lore.add("§7▸ Niveaux demandés: §c+" + requestedLevels);
            lore.add("§7▸ Niveau actuel: §e" + currentLevel);
            lore.add("§7▸ Niveau maximum: §e" + enchantment.getMaxLevel());
            lore.add("§7▸ Niveaux possibles: §e" + Math.max(0, enchantment.getMaxLevel() - currentLevel));
            lore.add("");
            lore.add("§7Réduisez le nombre de niveaux demandés!");

        } else if (canAffordExact) {
            // Peut se payer exactement
            long totalCost = calculateExactCost(enchantment, currentLevel, requestedLevels);

            lore.add("§a✅ §lAMÉLIORATION POSSIBLE");
            lore.add("§7▸ Niveaux à acheter: §a+" + requestedLevels);
            lore.add("§7▸ Coût total: §6" + NumberFormatter.format(totalCost) + " tokens");
            lore.add("§7▸ Tokens disponibles: §a" + NumberFormatter.format(availableTokens));
            lore.add("§7▸ Tokens restants: §e" + NumberFormatter.format(availableTokens - totalCost));
            lore.add("");

            // Section progression
            lore.add("§e📈 §lPROGRESSION");
            lore.add("§7▸ Niveau avant: §e" + currentLevel);
            lore.add("§7▸ Niveau après: §a" + (currentLevel + requestedLevels));
            lore.add("");

            // Effets avant/après
            lore.add("§b🔮 §lEFFETS AVANT → APRÈS");
            addEffectComparison(lore, enchantment, currentLevel, currentLevel + requestedLevels);
            lore.add("");

            lore.add("§a✨ Cliquez pour améliorer!");

        } else {
            // Ne peut pas se payer
            long totalCost = calculateExactCost(enchantment, currentLevel, requestedLevels);

            lore.add("§c❌ §lTOKENS INSUFFISANTS");
            lore.add("§7▸ Niveaux demandés: §c+" + requestedLevels);
            lore.add("§7▸ Coût total: §6" + NumberFormatter.format(totalCost));
            lore.add("§7▸ Tokens disponibles: §c" + NumberFormatter.format(availableTokens));
            lore.add("§7▸ Tokens manquants: §c" + NumberFormatter.format(totalCost - availableTokens));

            // Progression vers cet objectif
            double progressPercent = (double) availableTokens / totalCost * 100;
            lore.add("§7▸ Progression: §e" + String.format("%.1f%%", progressPercent));
            lore.add("");

            // Calcule combien on pourrait acheter avec les tokens actuels
            int maxAffordable = calculateMaxAffordableUpgrades(enchantment, currentLevel, availableTokens);
            if (maxAffordable > 0) {
                lore.add("§e💡 §lALTERNATIVE POSSIBLE");
                lore.add("§7▸ Niveaux abordables: §a+" + maxAffordable);
                lore.add("§7▸ Utilisez un bouton plus petit ou MAX");
                lore.add("");
            }

            // Ajoute les effets même si on ne peut pas se payer l'amélioration
            lore.add("§b🔮 §lEFFETS SI ACHETÉ");
            addEffectComparison(lore, enchantment, currentLevel, currentLevel + requestedLevels);
            lore.add("");

            lore.add("§7Continuez à miner pour obtenir plus de tokens!");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU : Vérifie si on peut se payer exactement le nombre de niveaux demandés
     */
    private boolean canAffordExactLevels(CustomEnchantment enchantment, int currentLevel, long availableTokens, int requestedLevels) {
        if (currentLevel + requestedLevels > enchantment.getMaxLevel()) {
            return false;
        }

        long totalCost = calculateExactCost(enchantment, currentLevel, requestedLevels);
        return availableTokens >= totalCost;
    }

    /**
     * NOUVEAU : Calcule le coût exact pour un nombre de niveaux donné
     */
    private long calculateExactCost(CustomEnchantment enchantment, int currentLevel, int requestedLevels) {
        long totalCost = 0;
        for (int i = 1; i <= requestedLevels; i++) {
            totalCost += enchantment.getUpgradeCost(currentLevel + i);
        }
        return totalCost;
    }

    /**
     * Ajoute une comparaison des effets avant/après POUR TOUS LES ENCHANTS
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
            case "key_greed" -> {
                double fromChance = fromLevel * 1.0;
                double toChance = toLevel * 1.0;
                lore.add("§7▸ Chance clé: §e" + String.format("%.1f%%", fromChance) +
                        " §7→ §a" + String.format("%.1f%%", toChance));
            }
            case "abundance" -> {
                double fromChance = fromLevel * 0.0001;
                double toChance = toLevel * 0.0001;
                lore.add("§7▸ Chance activation: §e" + String.format("%.4f%%", fromChance) +
                        " §7→ §a" + String.format("%.4f%%", toChance));
            }
            case "combustion" -> {
                int fromGain = Math.max(1, fromLevel / 10);
                int toGain = Math.max(1, toLevel / 10);
                lore.add("§7▸ Gain/bloc: §e+" + fromGain + " §7→ §a+" + toGain + " combustion");
            }
            case "pet_xp" -> {
                long fromBonus = fromLevel * 10;
                long toBonus = toLevel * 10;
                lore.add("§7▸ Bonus Pet XP: §e+" + NumberFormatter.format(fromBonus) +
                        " §7→ §a+" + NumberFormatter.format(toBonus));
            }
            case "efficiency" -> {
                lore.add("§7▸ Vitesse minage: §eFast+" + fromLevel + " §7→ §aFast+" + toLevel);
            }
            case "fortune" -> {
                double fromMult = 1.0 + (fromLevel * 0.2);
                double toMult = 1.0 + (toLevel * 0.2);
                lore.add("§7▸ Multiplicateur: §ex" + String.format("%.1f", fromMult) +
                        " §7→ §ax" + String.format("%.1f", toMult));
            }
            case "durability" -> {
                double fromBonus = fromLevel * 10;
                double toBonus = toLevel * 10;
                lore.add("§7▸ Durabilité: §e+" + String.format("%.0f%%", fromBonus) +
                        " §7→ §a+" + String.format("%.0f%%", toBonus));
            }
            case "night_vision" -> {
                if (fromLevel == 0 && toLevel == 1) {
                    lore.add("§7▸ Vision nocturne: §cInactive §7→ §aActive");
                } else {
                    lore.add("§7▸ Vision nocturne: §aActive");
                }
            }
            case "speed" -> {
                lore.add("§7▸ Vitesse: §eSpeed " + fromLevel + " §7→ §aSpeed " + toLevel);
            }
            case "haste" -> {
                lore.add("§7▸ Rapidité: §eHaste " + fromLevel + " §7→ §aHaste " + toLevel);
            }
            case "jump_boost" -> {
                lore.add("§7▸ Saut: §eJump " + fromLevel + " §7→ §aJump " + toLevel);
            }
            case "escalator" -> {
                if (fromLevel == 0 && toLevel == 1) {
                    lore.add("§7▸ Téléportation: §cInactive §7→ §aActive");
                } else {
                    lore.add("§7▸ Téléportation: §aActive");
                }
            }
            case "luck" -> {
                double fromBonus = fromLevel * 0.2;
                double toBonus = toLevel * 0.2;
                lore.add("§7▸ Bonus chance: §e+" + String.format("%.1f%%", fromBonus) +
                        " §7→ §a+" + String.format("%.1f%%", toBonus));
            }
            case "laser" -> {
                double fromChance = fromLevel * 0.002;
                double toChance = toLevel * 0.002;
                lore.add("§7▸ Chance laser: §e" + String.format("%.3f%%", fromChance) +
                        " §7→ §a" + String.format("%.3f%%", toChance));
            }
            case "explosion" -> {
                double fromChance = fromLevel * 0.05;
                double toChance = toLevel * 0.05;
                lore.add("§7▸ Chance explosion: §e" + String.format("%.2f%%", fromChance) +
                        " §7→ §a" + String.format("%.2f%%", toChance));
            }
            default -> {
                lore.add("§7▸ Amélioration de §2+" + (toLevel - fromLevel) + " niveau" +
                        (toLevel - fromLevel > 1 ? "x" : ""));
            }
        }
    }

    /**
     * Crée le bouton MAX avec la même logique améliorée
     */
    private ItemStack createMaxUpgradeButton(CustomEnchantment enchantment, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantment.getName());
        long availableTokens = playerData.getTokens();

        int maxAffordableLevels = calculateMaxAffordableUpgrades(enchantment, currentLevel, availableTokens);
        long totalCost = 0;

        if (maxAffordableLevels > 0) {
            totalCost = calculateExactCost(enchantment, currentLevel, maxAffordableLevels);
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

                // Ajoute les effets même si impossible
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
     * Crée le bouton auto-upgrade avec gestion des permissions
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
     * Item pour les joueurs sans permission auto-upgrade
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

    /**
     * Version optimisée d'upgradeEnchantment avec option de silence
     * @param player Le joueur
     * @param enchantmentName Nom de l'enchantement
     * @param requestedLevels Nombre de niveaux demandés
     * @param silent Si true, n'affiche pas les messages d'erreur ni les sons
     * @return true si l'amélioration a réussi, false sinon
     */
    private boolean upgradeEnchantment(Player player, String enchantmentName, int requestedLevels, boolean silent) {
        boolean success = plugin.getEnchantmentManager().upgradeEnchantment(player, enchantmentName, requestedLevels);

        if (success) {
            // Vérifie si niveau max atteint après amélioration
            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            int newLevel = playerData.getEnchantmentLevel(enchantmentName);

            if (newLevel >= enchantment.getMaxLevel()) {
                playerData.setAutoUpgrade(enchantmentName, false);
                if (!silent) {
                    player.sendMessage("§2🏆 " + enchantment.getDisplayName() + " §2niveau maximum atteint!");
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        plugin.getCategoryMenuGUI().openCategoryMenu(player, enchantment.getCategory());
                    }, 10L);
                }
            } else if (!silent) {
                openEnchantmentUpgradeMenu(player, enchantmentName);
            }
            plugin.getPickaxeManager().updateMobilityEffects(player);
            return true;
        } else {
            if (!silent) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
            }
            return false;
        }
    }

    /**
     * Version publique originale (pour compatibilité)
     */
    private void upgradeEnchantment(Player player, String enchantmentName, int requestedLevels) {
        upgradeEnchantment(player, enchantmentName, requestedLevels, false);
    }

    /**
     * Version optimisée de upgradeToMax avec option de silence
     * @param player Le joueur
     * @param enchantmentName Nom de l'enchantement
     * @param silent Si true, n'affiche pas les messages d'erreur ni les sons
     * @return true si au moins une amélioration a été effectuée, false sinon
     */
    public boolean upgradeToMax(Player player, String enchantmentName, boolean silent) {
        CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
        if (enchantment == null) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantmentName);

        // Vérifie si déjà au niveau max
        if (currentLevel >= enchantment.getMaxLevel()) {
            if (!silent) {
                playerData.setAutoUpgrade(enchantmentName, false);
                player.sendMessage("§2🏆 " + enchantment.getDisplayName() + " §2niveau maximum atteint!");
            }
            return false;
        }

        long availableTokens = playerData.getTokens();
        int maxAffordableLevels = calculateMaxAffordableUpgrades(enchantment, currentLevel, availableTokens);

        if (maxAffordableLevels > 0) {
            // Utilise la méthode upgradeEnchantment en mode silencieux
            return upgradeEnchantment(player, enchantmentName, maxAffordableLevels, silent);
        } else {
            // Pas assez de tokens - ne fait rien en mode silencieux
            if (!silent) {
                player.sendMessage("§cPas assez de tokens pour améliorer cet enchantement!");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.5f);
            }
            return false;
        }
    }

    /**
     * Version publique originale (pour compatibilité)
     */
    public void upgradeToMax(Player player, String enchantmentName) {
        upgradeToMax(player, enchantmentName, false);
    }

    /**
     * Toggle auto-upgrade avec mise à jour GUI fonctionnelle
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

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Rouvre le menu immédiatement
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            openEnchantmentUpgradeMenu(player, enchantmentName);
        }, 1L);
    }

    // Utilitaires...

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
     * Extraction du nom d'enchantement plus robuste
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

        // CORRIGÉ: Bordures pour 27 slots
        int[] borderSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 19, 20, 21, 22, 24, 25, 26};
        for (int slot : borderSlots) {
            gui.setItem(slot, filler);
        }
    }
}