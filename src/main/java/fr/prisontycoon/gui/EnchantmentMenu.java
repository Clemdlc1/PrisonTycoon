package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.CustomEnchantment;
import fr.prisontycoon.enchantments.EnchantmentCategory;
import fr.prisontycoon.utils.NumberFormatter;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Menu principal du système d'enchantements et features
 * CORRIGÉ : Réorganisation des lignes et noms en gras
 */
public class EnchantmentMenu {

    // Slots du menu principal (réorganisés)
    private static final int PLAYER_HEAD_SLOT = 4;      // Ligne du haut
    private static final int MAIN_MENU_SLOT = 45;       // Ligne du bas
    private static final int CRYSTALS_SLOT = 48;        // Ligne du bas
    private static final int UNIQUE_ENCHANTS_SLOT = 49; // Ligne du bas
    private static final int PETS_SLOT = 50;            // Ligne du bas
    private static final int REPAIR_PICKAXE_SLOT = 53;  // Ligne du bas
    private final PrisonTycoon plugin;

    public EnchantmentMenu(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu principal
     */
    public void openEnchantmentMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(54, "§6✨ §lMenu Enchantement §6✨");

        // Tête du joueur avec informations économiques
        gui.setItem(PLAYER_HEAD_SLOT, createPlayerHead(player));

        // Boutons utilitaires bas de page
        gui.setItem(MAIN_MENU_SLOT, createFutureFeatureItem("Menu Principal", Material.COMPASS, "§eNavigation générale"));
        gui.setItem(CRYSTALS_SLOT, createCristalsButton(player));
        gui.setItem(UNIQUE_ENCHANTS_SLOT, createFutureFeatureItem("Enchantements Uniques", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "§dEnchantements légendaires rares"));
        gui.setItem(PETS_SLOT, createFutureFeatureItem("Pets", Material.WOLF_SPAWN_EGG, "§6Compagnons de minage"));
        gui.setItem(REPAIR_PICKAXE_SLOT, createRepairPickaxeButton(player));

        // Place directement les enchantements (toutes catégories confondues)
        List<CustomEnchantment> all = new ArrayList<>(plugin.getEnchantmentManager().getAllEnchantments());
        // Tri par catégorie puis par nom d'affichage
        all.sort(Comparator
                .comparing((CustomEnchantment e) -> e.getCategory().ordinal())
                .thenComparing(CustomEnchantment::getDisplayName));

        int[] slots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < all.size() && i < slots.length; i++) {
            gui.setItem(slots[i], createEnchantmentItem(all.get(i), player));
        }

        // Remplissage décoratif des espaces restants
        plugin.getGUIManager().fillBorders(gui);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.ENCHANTMENT_MENU, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Gère les clics dans le menu principal
     */
    public void handleEnchantmentMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            // Features futures
            case CRYSTALS_SLOT -> plugin.getCristalGUI().openCristalMenu(player);
            case UNIQUE_ENCHANTS_SLOT -> plugin.getEnchantmentBookGUI().openEnchantmentBookMenu(player);
            case PETS_SLOT -> plugin.getPetsMenuGUI().openPetsMenu(player);
            case MAIN_MENU_SLOT -> plugin.getMainNavigationGUI().openMainMenu(player);
            case REPAIR_PICKAXE_SLOT -> plugin.getPickaxeRepairMenu().openRepairGUI(player);
            default -> {
                // Gère le clic sur un enchantement
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    String displayName = item.getItemMeta().displayName() != null ?
                            LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName()) : "";

                    CustomEnchantment targetEnchantment = plugin.getEnchantmentManager().getAllEnchantments()
                            .stream()
                            .filter(enchantment -> displayName.contains(enchantment.getDisplayName()))
                            .findFirst().orElse(null);

                    if (targetEnchantment != null) {
                        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                        int currentLevel = playerData.getEnchantmentLevel(targetEnchantment.getName());

                        if (clickType != null && clickType.isShiftClick() && targetEnchantment.getCategory() == EnchantmentCategory.MOBILITY) {
                            if (currentLevel > 0) {
                                toggleMobilityEnchantment(player, targetEnchantment.getName(), playerData);
                                plugin.getServer().getScheduler().runTaskLater(plugin, () -> openEnchantmentMenu(player), 1L);
                            } else {
                                player.sendMessage("§c❌ Vous devez d'abord acheter cet enchantement!");
                                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            }
                        } else {
                            if (currentLevel >= targetEnchantment.getMaxLevel()) {
                                animateMaxLevelItem(player, slot);
                            } else {
                                plugin.getEnchantmentUpgradeGUI().openEnchantmentUpgradeMenu(player, targetEnchantment.getName());
                            }
                        }
                    }
                }
            }
        }
    }

    // ======= Items d'enchantements (migrés depuis CategoryMenuGUI) =======
    private ItemStack createEnchantmentItem(CustomEnchantment enchantment, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getEnchantmentLevel(enchantment.getName());
        long availableTokens = playerData.getTokens();

        ItemStack item = new ItemStack(enchantment.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

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

        if (currentLevel > 0) {
            lore.add("§a📈 §lEFFETS ACTUELS");
            addEnchantmentEffectPreview(lore, enchantment, currentLevel);
            lore.add("");
        }

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

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        String clickAction = currentLevel >= enchantment.getMaxLevel() ? "voir détails" : "améliorer";
        lore.add("§e✨ Cliquez pour " + clickAction);
        if (enchantment.getCategory() == EnchantmentCategory.MOBILITY && currentLevel > 0) {
            lore.add("§e🖱️ SHIFT + Clic pour activer/désactiver");
        }
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

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
                lore.add("§7▸ §dRayon:  blocs");
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

    private void toggleMobilityEnchantment(Player player, String enchantmentName, PlayerData playerData) {
        boolean currentlyEnabled = playerData.isMobilityEnchantmentEnabled(enchantmentName);
        boolean newState = !currentlyEnabled;

        playerData.setMobilityEnchantmentEnabled(enchantmentName, newState);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
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
    }

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
     * NOUVEAU: Crée un item pour une feature future
     */
    private ItemStack createFutureFeatureItem(String name, Material material, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // CORRECTION: Nom en gras
        plugin.getGUIManager().applyName(meta, "§e🔮 §l" + name);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + description);
        lore.add("§7");
        lore.add("§6Statut: " + "§7Implémentation future");
        lore.add("§7");
        lore.add("§7Cette fonctionnalité sera disponible");
        lore.add("§7dans une future mise à jour du plugin.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✨ Cliquez pour un aperçu!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU: Menu principal général (feature future)
     */
    public void openGeneralMainMenu(Player player) {
        player.sendMessage("§e🔮 Menu Principal Général - Fonctionnalité à venir!");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
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
        lore.add("§7│ §dBeacons: §c" + NumberFormatter.format(playerData.getBeacons()));

        plugin.getGUIManager().applyLore(meta, lore);
        head.setItemMeta(meta);

        return head;
    }

    /**
     * Crée le bouton de réparation de pioche
     */
    private ItemStack createRepairPickaxeButton(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);

        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§c🔨 §lRÉPARATION DE PIOCHE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Réparez votre pioche légendaire contre");
        lore.add("§7des tokens basés sur vos investissements.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (pickaxe != null) {
            // État actuel de la pioche
            int currentDurability = 0;
            int maxDurability = pickaxe.getType().getMaxDurability();
            if (pickaxe.hasItemMeta() && pickaxe.getItemMeta() instanceof Damageable damageable) {
                currentDurability = damageable.getDamage();
            }

            // Prendre en compte l'enchantement durabilité
            int durabilityLevel = playerData.getEnchantmentLevel("durability");
            double durabilityBonus = durabilityLevel * 10.0;

            double healthPercent = ((double) (maxDurability - currentDurability) / maxDurability) * 100;

            lore.add("§e⛏️ §lÉTAT ACTUEL");
            lore.add("§7│ §eDurabilité: " + getDurabilityColorForButton(healthPercent) + String.format("%.1f%%", healthPercent));

            if (durabilityLevel > 0) {
                lore.add("§7│ §eBonus Solidité: §a+" + String.format("%.0f%%", durabilityBonus) + " §7(Niv." + durabilityLevel + ")");
            }

            // Recommandation basée sur l'état
            if (healthPercent < 30) {
                lore.add("§7│ §c⚠️ Réparation recommandée!");
            } else if (healthPercent < 60) {
                lore.add("§7│ §e⚠️ Réparation optionnelle");
            } else {
                lore.add("§7│ §a✓ En bon état");
            }

            lore.add("§7└");
            lore.add("");

            // Estimation des coûts de réparation
            long totalInvested = calculateTotalInvestedTokensForButton(playerData);
            lore.add("§6💰 §lCOÛTS DE RÉPARATION");
            lore.add("§7│ §6Base: §e" + NumberFormatter.format(totalInvested) + " tokens investis");
            lore.add("§7│ §7Réparation 20%: §6" + NumberFormatter.format((long) (totalInvested * 0.01)) + " tokens");
            lore.add("§7│ §7Réparation 50%: §6" + NumberFormatter.format((long) (totalInvested * 0.035)) + " tokens");
            lore.add("§7│ §7Réparation 100%: §6" + NumberFormatter.format((long) (totalInvested * 0.10)) + " tokens");
            lore.add("§7└ §7Coûts précis dans le menu de réparation");

        } else {
            lore.add("§c❌ §lPIOCHE INTROUVABLE");
            lore.add("§7│ §cVotre pioche légendaire est introuvable!");
            lore.add("§7│ §7Assurez-vous qu'elle est dans votre inventaire.");
            lore.add("§7└");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (pickaxe != null) {
            lore.add("§e✨ Cliquez pour ouvrir le menu de réparation!");
        } else {
            lore.add("§7Récupérez votre pioche pour accéder à la réparation");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Retourne la couleur selon le pourcentage de durabilité pour les boutons
     */
    private String getDurabilityColorForButton(double healthPercent) {
        if (healthPercent >= 75) return "§a"; // Vert
        if (healthPercent >= 50) return "§e"; // Jaune
        if (healthPercent >= 25) return "§6"; // Orange
        return "§c"; // Rouge
    }

    /**
     * Calcule le coût total de tokens investis dans tous les enchantements (pour bouton)
     */
    private long calculateTotalInvestedTokensForButton(PlayerData playerData) {
        long totalCost = 0;

        Map<String, Integer> enchantments = playerData.getEnchantmentLevels();

        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            String enchantName = entry.getKey();
            int currentLevel = entry.getValue();

            CustomEnchantment enchantment = plugin.getEnchantmentManager().getEnchantment(enchantName);
            if (enchantment != null) {
                // Calcule le coût total pour atteindre ce niveau
                for (int level = 1; level <= currentLevel; level++) {
                    totalCost += enchantment.getUpgradeCost(level);
                }
            }
        }

        return totalCost;
    }

    /**
     * NOUVEAU: Crée le bouton cristaux amélioré
     */
    private ItemStack createCristalsButton(Player player) {
        ItemStack cristalsBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = cristalsBtn.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§d✨ §lGestion des Cristaux §d✨");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Améliorez votre pioche avec des");
        lore.add("§7cristaux magiques puissants!");
        lore.add("");

        // Affichage des cristaux actuels
        List<Cristal> currentCristals = plugin.getCristalManager().getPickaxeCristals(player);
        lore.add("§7Cristaux appliqués: §d" + currentCristals.size() + "§8/§d4");

        if (!currentCristals.isEmpty()) {
            lore.add("§7Bonus actifs:");
            for (Cristal cristal : currentCristals) {
                lore.add("§8• §d" + cristal.getType().getDisplayName() + " " + cristal.getNiveau());
            }
        }

        lore.add("");
        lore.add("§e▸ Appliquer des cristaux sur la pioche");
        lore.add("§e▸ Fusionner 9 cristaux → niveau +1");
        lore.add("§e▸ Bonus permanents de minage");
        lore.add("");
        lore.add("§a🖱 Clic pour ouvrir le menu");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        cristalsBtn.setItemMeta(meta);

        return cristalsBtn;
    }
}
