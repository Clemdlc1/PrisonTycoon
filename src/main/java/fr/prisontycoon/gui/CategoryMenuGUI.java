package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.CustomEnchantment;
import fr.prisontycoon.enchantments.EnchantmentCategory;
import fr.prisontycoon.utils.NumberFormatter;
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
 * CORRIGÉ : Clic molette pour désactiver enchantements mobilité
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
        String title = "§6✨ §l" + category.getDisplayName() + " §6✨";
        Inventory gui = plugin.getGUIManager().createInventory(27, title);
        plugin.getGUIManager().registerOpenGUI(player, GUIType.CATEGORY_ENCHANT, gui);

        // Remplissage décoratif
        fillBorders(gui);

        // Tête du joueur
        gui.setItem(4, createPlayerHead(player));

        // Bouton retour
        gui.setItem(22, createBackButton());

        // Enchantements de la catégorie
        var enchantments = plugin.getEnchantmentManager().getEnchantmentsByCategory(category);
        int[] slots;
        slots = new int[]{10, 11, 12, 13, 14, 15, 16};

        for (int i = 0; i < enchantments.size() && i < slots.length; i++) {
            CustomEnchantment enchantment = enchantments.get(i);
            gui.setItem(slots[i], createEnchantmentItem(enchantment, player));
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    /**
     * CORRIGÉ : Gère les clics dans le menu de catégorie avec SHIFT-CLIC pour mobilité
     */
    public void handleCategoryMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        if (slot == 22) { // Bouton retour
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            return;
        }

        // Vérifie si c'est un enchantement
        if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().displayName() != null ?
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName()) : "";

            // Cherche l'enchantement par son nom d'affichage
            CustomEnchantment targetEnchantment = plugin.getEnchantmentManager().getAllEnchantments().stream().filter(enchantment -> displayName.contains(enchantment.getDisplayName())).findFirst().orElse(null);

            if (targetEnchantment != null) {
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                int currentLevel = playerData.getEnchantmentLevel(targetEnchantment.getName());

                // --- MODIFIÉ ICI ---
                // On vérifie maintenant si le clic est un Shift-Clic (gauche ou droit)
                if (clickType.isShiftClick() && targetEnchantment.getCategory() == EnchantmentCategory.MOBILITY) {
                    if (currentLevel > 0) { // Seulement si l'enchantement est acheté
                        toggleMobilityEnchantment(player, targetEnchantment.getName(), playerData);
                        // Rouvre le menu pour actualiser l'affichage
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> openCategoryMenu(player, targetEnchantment.getCategory()), 1L);
                    } else {
                        player.sendMessage("§c❌ Vous devez d'abord acheter cet enchantement!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                    return;
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
     * CORRIGÉ : Active/Désactive un enchantement de mobilité
     */
    private void toggleMobilityEnchantment(Player player, String enchantmentName, PlayerData playerData) {
        boolean currentlyEnabled = playerData.isMobilityEnchantmentEnabled(enchantmentName);
        boolean newState = !currentlyEnabled;

        playerData.setMobilityEnchantmentEnabled(enchantmentName, newState);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // CORRIGÉ : Met à jour les effets de mobilité immédiatement
        plugin.getPickaxeManager().updateMobilityEffects(player);

        CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantmentName);
        String enchantDisplayName = enchantment != null ? enchantment.getDisplayName() : enchantmentName;

        if (newState) {
            player.sendMessage("§a✅ " + enchantDisplayName + " §aactivé!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        } else {
            player.sendMessage("§c❌ " + enchantDisplayName + " §cdésactivé!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        }

        plugin.getPluginLogger().debug("Enchantement mobilité " + enchantmentName +
                " " + (newState ? "activé" : "désactivé") + " pour " + player.getName());
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

        // Nom avec statut
        String levelDisplay = currentLevel == 0 ? "§7[Niveau 0]" :
                (enchantment.getMaxLevel() == Integer.MAX_VALUE ?
                        "§a[Niveau " + NumberFormatter.format(currentLevel) + "]" :
                        "§a[Niveau " + currentLevel + "§7/§e" + enchantment.getMaxLevel() + "§a]");

        plugin.getGUIManager().applyName(meta, "§6✦ §l" + enchantment.getDisplayName() + " " + levelDisplay);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + enchantment.getDescription());
        lore.add("§7Catégorie: " + enchantment.getCategory().getDisplayName());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        // Statut standardisé
        lore.add("§6📊 §lSTATUT ACTUEL");
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

                lore.add("§7▸ +1 niveau: §6" + NumberFormatter.format(enchantment.getUpgradeCost(currentLevel + 1)));
                if (maxAffordable >= 5) {
                    long cost5 = 0;
                    for (int i = 1; i <= 5; i++) {
                        cost5 += enchantment.getUpgradeCost(currentLevel + i);
                    }
                    lore.add("§7▸ +5 niveaux: §6" + NumberFormatter.format(cost5));
                }

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

        // CORRIGÉ : État mobilité si applicable avec indication plus claire
        if (enchantment.getCategory() == EnchantmentCategory.MOBILITY && currentLevel > 0) {
            boolean enabled = playerData.isMobilityEnchantmentEnabled(enchantment.getName());
            lore.add("§b🎮 §lÉTAT MOBILITÉ");
            lore.add("§7▸ Enchantement: " + (enabled ? "§a✅ Activé" : "§c❌ Désactivé"));
            lore.add("§7▸ §e🖱️ SHIFT + Clic pour " + (enabled ? "désactiver" : "activer"));

            if (!enabled) {
                lore.add("§7▸ §cEffets suspendus");
            } else {
                lore.add("§7▸ §aEffets appliqués");
            }
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
            lore.add("§e🖱️ Clic molette pour activer/désactiver");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Ajoute un aperçu des effets d'un enchantement
     */
    private void addEnchantmentEffectPreview(List<String> lore, CustomEnchantment enchantment, int level) {
        switch (enchantment.getName()) {
            case "token_greed" -> {
                long tokensPerTrigger = level * 5L;
                lore.add("§7▸ §6+" + NumberFormatter.format(tokensPerTrigger) + " tokens par déclenchement");
                lore.add("§7▸ §e5% chance de base par bloc détruit");
            }
            case "exp_greed" -> {
                long expPerTrigger = level * 50L;
                lore.add("§7▸ §a+" + NumberFormatter.format(expPerTrigger) + " XP par déclenchement");
                lore.add("§7▸ §e5% chance de base par bloc détruit");
            }
            case "money_greed" -> {
                long coinsPerTrigger = level * 10L;
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
            case "efficiency" -> {
                lore.add("§7▸ §bVitesse de minage améliorée");
                lore.add("§7▸ §7Niveau " + level + "/100 (Fast Digging " + level + ")");
            }
            case "durability" -> {
                double bonus = level * 10;
                lore.add("§7▸ §e+" + String.format("%.0f%%", bonus) + " durabilité de la pioche");
                lore.add("§7▸ §7Casse moins souvent, dure plus longtemps");
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
            case "night_vision" -> {
                lore.add("§7▸ §eVision nocturne permanente");
                lore.add("§7▸ §7Voir dans l'obscurité totale");
            }
            case "speed" -> {
                lore.add("§7▸ §bVitesse de déplacement +" + level);
                lore.add("§7▸ §7Mouvement plus rapide");
            }
            case "haste" -> {
                lore.add("§7▸ §eCélérité +" + level);
                lore.add("§7▸ §7Minage et attaque plus rapides");
            }
            case "jump_boost" -> {
                lore.add("§7▸ §aSaut amélioré +" + level);
                lore.add("§7▸ §7Sautez plus haut");
            }
            case "escalator" -> {
                lore.add("§7▸ §dTéléportation vers la surface");
                lore.add("§7▸ §7Shift + clic droit avec la pioche");
            }
            case "sell_greed" -> {
                double bonusPercent = level * 0.01; // 0,01% par niveau
                lore.add("§7▸ §6+" + String.format("%.1f%%", bonusPercent) + " bonus de vente permanent");
                lore.add("§7▸ §eAppliqué via GlobalBonusManager");
            }
            case "jackhammer" -> {
                double chance = Math.min(2.0, level / 1000.0); // Max 2% au niveau 2000
                lore.add("§7▸ §d" + String.format("%.1f%%", chance) + " chance de casser une couche");
                lore.add("§7▸ §dRayon: " + " blocs");
                lore.add("§7▸ §dSusceptible à Echo (plusieurs couches)");
            }
            case "jackpot" -> {
                double chance = level * 0.0001; // ex: 0.01% par 100 niveaux
                lore.add("§7▸ §e" + String.format("%.4f%%", chance) + " chance de voucher aléatoire");
                lore.add("§7▸ §7Augmente avec le niveau");
            }
            case "cohesion" -> {
                double mult = 1.0 + Math.min(2.0, (level / 10000.0) * 0.5); // +50% max via cohesion
                lore.add("§7▸ §aMultiplicateur Greed: §ex" + String.format("%.2f", mult));
                lore.add("§7▸ §7Dépend du nombre de joueurs dans la mine");
            }
            case "heritage" -> {
                double chance = Math.min(50.0, level * 0.05); // 0.05%/niv, cap 50%
                lore.add("§7▸ §e" + String.format("%.2f%%", chance) + " chance de copier un Greed voisin");
                lore.add("§7▸ §7Anti-boucle inclus");
            }
            case "opportunity_fever" -> {
                double chance = Math.min(25.0, level * 0.01); // 0.01%/niv, cap 25%
                lore.add("§7▸ §e" + String.format("%.2f%%", chance) + " de Fièvre 10s (bloc cible garanti)");
            }
            case "planneur" -> {
                lore.add("§7▸ §bChute lente en l'air (sneak pour annuler)");
                lore.add("§7▸ §7Niv.2: rayon d'activation plus permissif");
            }
            default -> lore.add("§7▸ §7Effet de niveau " + level + " actif");
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
        plugin.getGUIManager().applyName(meta, "§6📊 §l" + player.getName());

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("§6💰 §lÉCONOMIE ACTUELLE");
        lore.add("§7│ §6Coins: §e" + NumberFormatter.format(playerData.getCoins()));
        lore.add("§7│ §eTokens: §6" + NumberFormatter.format(playerData.getTokens()));
        lore.add("§7│ §aExpérience: §2" + NumberFormatter.format(playerData.getExperience()));
        lore.add("§7│ §bNiveau vanilla: §3" + player.getLevel());
        lore.add("§7└ §7Enchantements actifs: §b" + playerData.getEnchantmentLevels().size());

        plugin.getGUIManager().applyLore(meta, lore);
        head.setItemMeta(meta);

        return head;
    }

    /**
     * Crée le bouton retour
     */
    private ItemStack createBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§7← §lRetour");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retourner au menu principal"));

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
        plugin.getGUIManager().applyName(meta, "§c❌ Niveau maximum atteint");
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
        plugin.getGUIManager().applyName(meta, "§7");
        filler.setItemMeta(meta);

        // CORRIGÉ: Bordures pour 27 slots
        int[] borderSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 23, 24, 25, 26};
        for (int slot : borderSlots) {
            gui.setItem(slot, filler);
        }
    }
}