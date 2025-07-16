package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.cristaux.Cristal;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.CustomEnchantment;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.EnchantmentCategory;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Menu principal du système d'enchantements et features
 * CORRIGÉ : Réorganisation des lignes et noms en gras
 */
public class EnchantmentMenu {

    // Slots du menu principal
    private static final int PLAYER_HEAD_SLOT = 4;
    private static final int CRYSTALS_SLOT = 30;
    private static final int UNIQUE_ENCHANTS_SLOT = 31;
    private static final int PETS_SLOT = 32;
    private static final int MAIN_MENU_SLOT = 27;
    private static final int REPAIR_PICKAXE_SLOT = 35; // En bas à droite du menu
    private static final int ECONOMIC_SLOT = 10;
    private static final int UTILITY_SLOT = 12;
    private static final int MOBILITY_SLOT = 14;
    private static final int SPECIAL_SLOT = 16;
    private final PrisonTycoon plugin;

    public EnchantmentMenu(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu principal
     */
    public void openEnchantmentMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, "§6✨ §lMenu Enchantement §6✨");

        // Remplissage décoratif
        fillEmptySlots(gui);

        // Tête du joueur avec informations économiques
        gui.setItem(PLAYER_HEAD_SLOT, createPlayerHead(player));

        gui.setItem(CRYSTALS_SLOT, createCristalsButton(player));


        gui.setItem(UNIQUE_ENCHANTS_SLOT, createFutureFeatureItem("Enchantements Uniques", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "§dEnchantements légendaires rares", "§7Implémentation future"));

        gui.setItem(PETS_SLOT, createFutureFeatureItem("Pets", Material.WOLF_SPAWN_EGG,
                "§6Compagnons de minage", "§7Implémentation future"));

        gui.setItem(MAIN_MENU_SLOT, createFutureFeatureItem("Menu Principal", Material.COMPASS,
                "§eNavigation générale", "§7Implémentation future"));

        gui.setItem(REPAIR_PICKAXE_SLOT, createRepairPickaxeButton(player));

        // CORRECTION: Catégories d'enchantements (ligne du bas)
        gui.setItem(ECONOMIC_SLOT, createCategoryItem(EnchantmentCategory.ECONOMIC, player));
        gui.setItem(UTILITY_SLOT, createCategoryItem(EnchantmentCategory.UTILITY, player));
        gui.setItem(MOBILITY_SLOT, createCategoryItem(EnchantmentCategory.MOBILITY, player));
        gui.setItem(SPECIAL_SLOT, createCategoryItem(EnchantmentCategory.SPECIAL, player));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Gère les clics dans le menu principal
     */
    public void handleEnchantmentMenuClick(Player player, int slot, ItemStack item) {
        switch (slot) {
            // Features futures
            case CRYSTALS_SLOT -> plugin.getCristalGUI().openCristalMenu(player);
            case UNIQUE_ENCHANTS_SLOT -> plugin.getUniqueEnchantsMenuGUI().openUniqueEnchantsMenu(player);
            case PETS_SLOT -> plugin.getPetsMenuGUI().openPetsMenu(player);
            case MAIN_MENU_SLOT -> plugin.getMainMenuGUI().openGeneralMainMenu(player);
            case REPAIR_PICKAXE_SLOT -> plugin.getPickaxeRepairMenu().openRepairGUI(player);

            // Catégories d'enchantements
            case ECONOMIC_SLOT -> plugin.getCategoryMenuGUI().openCategoryMenu(player, EnchantmentCategory.ECONOMIC);
            case UTILITY_SLOT -> plugin.getCategoryMenuGUI().openCategoryMenu(player, EnchantmentCategory.UTILITY);
            case MOBILITY_SLOT -> plugin.getCategoryMenuGUI().openCategoryMenu(player, EnchantmentCategory.MOBILITY);
            case SPECIAL_SLOT -> plugin.getCategoryMenuGUI().openCategoryMenu(player, EnchantmentCategory.SPECIAL);
        }
    }

    /**
     * Crée un item de catégorie d'enchantements
     */
    private ItemStack createCategoryItem(EnchantmentCategory category, Player player) {
        Material material = switch (category) {
            case ECONOMIC -> Material.EMERALD;
            case UTILITY -> Material.DIAMOND;
            case MOBILITY -> Material.FEATHER;
            case SPECIAL -> Material.NETHER_STAR;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // CORRECTION: Nom en gras
        meta.setDisplayName(category.getIcon() + " §l" + category.getDisplayName().toUpperCase());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Description de la catégorie
        switch (category) {
            case ECONOMIC -> {
                lore.add("§7Enchantements qui augmentent vos gains");
                lore.add("§7économiques lors du minage et de la destruction.");
                lore.add("§7");
                lore.add("§e📊 §lEffets principaux:");
                lore.add("§7▸ §6Tokens, Coins, Expérience bonus via Greeds");
                lore.add("§7▸ §6Clés de coffres rares (Key Greed)");
                lore.add("§7▸ §6Multiplicateurs temporaires (Combustion)");
                lore.add("§7▸ §6Effets d'abondance (x2 gains temporaire)");
            }
            case UTILITY -> {
                lore.add("§7Enchantements qui améliorent l'utilité");
                lore.add("§7de votre pioche et multiplient vos gains.");
                lore.add("§7");
                lore.add("§e⚡ §lEffets principaux:");
                lore.add("§7▸ §aMinage plus rapide (Efficacité)");
                lore.add("§7▸ §aMultiplicateurs globaux (Fortune)");
                lore.add("§7▸ §aDurabilité améliorée (Solidité)");
                lore.add("§7▸ §aOptimisation des ressources");
            }
            case MOBILITY -> {
                lore.add("§7Enchantements qui améliorent votre");
                lore.add("§7mobilité et confort de jeu dans les mines.");
                lore.add("§7");
                lore.add("§e🏃 §lEffets principaux:");
                lore.add("§7▸ §bVitesse de déplacement permanente");
                lore.add("§7▸ §bHauteur de saut augmentée");
                lore.add("§7▸ §bVision nocturne continue");
                lore.add("§7▸ §bTéléportation surface (Escalateur)");
            }
            case SPECIAL -> {
                lore.add("§7Enchantements uniques avec des effets");
                lore.add("§7spéciaux et des mécaniques avancées.");
                lore.add("§7");
                lore.add("§e✨ §lEffets principaux:");
                lore.add("§7▸ §dExplosions de minage destructrices");
                lore.add("§7▸ §dLaser destructeur en ligne droite");
                lore.add("§7▸ §dAugmentation globale de chance");
                lore.add("§7▸ §dEffets spéciaux uniques");
            }
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Statistiques de la catégorie
        var enchantments = plugin.getEnchantmentManager().getEnchantmentsByCategory(category);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        int unlocked = 0;
        long totalTokensInvested = 0;

        Set<String> defaultEnchants = Set.of("token_greed", "efficiency", "durability");

        for (var ench : enchantments) {
            int level = playerData.getEnchantmentLevel(ench.getName());
            if (level > 0) {
                unlocked++;

                int startLevel = 1;
                if (defaultEnchants.contains(ench.getName())) {
                    int defaultLevel = switch (ench.getName()) {
                        case "token_greed" -> 5;
                        case "efficiency" -> 3;
                        case "durability" -> 1;
                        default -> 0;
                    };
                    startLevel = defaultLevel + 1;
                }

                for (int i = startLevel; i <= level; i++) {
                    totalTokensInvested += ench.getUpgradeCost(i);
                }
            }
        }

        lore.add("§e📈 §lSTATISTIQUES CATÉGORIE");
        lore.add("§7▸ Enchantements actifs: §a" + unlocked + "§7/§6" + enchantments.size());
        lore.add("§7▸ Tokens investis: §6" + NumberFormatter.format(totalTokensInvested));

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✨ Cliquez pour explorer cette catégorie!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU: Crée un item pour une feature future
     */
    private ItemStack createFutureFeatureItem(String name, Material material, String description, String status) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // CORRECTION: Nom en gras
        meta.setDisplayName("§e🔮 §l" + name);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + description);
        lore.add("§7");
        lore.add("§6Statut: " + status);
        lore.add("§7");
        lore.add("§7Cette fonctionnalité sera disponible");
        lore.add("§7dans une future mise à jour du plugin.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✨ Cliquez pour un aperçu!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
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
        meta.setDisplayName("§6📊 §l" + player.getName());

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("§6💰 §lÉCONOMIE ACTUELLE");
        lore.add("§7│ §6Coins: §e" + NumberFormatter.format(playerData.getCoins()));
        lore.add("§7│ §eTokens: §6" + NumberFormatter.format(playerData.getTokens()));
        lore.add("§7│ §aExpérience: §2" + NumberFormatter.format(playerData.getExperience()));
        lore.add("§7│ §dBeacons: §c" + NumberFormatter.format(playerData.getBeacons()));

        meta.setLore(lore);
        head.setItemMeta(meta);

        return head;
    }

    /**
     * Remplit les bordures avec des items décoratifs
     */
    private void fillEmptySlots(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }

    /**
     * Crée le bouton de réparation de pioche
     */
    private ItemStack createRepairPickaxeButton(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);

        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c🔨 §lRÉPARATION DE PIOCHE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Réparez votre pioche légendaire contre");
        lore.add("§7des tokens basés sur vos investissements.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (pickaxe != null) {
            // État actuel de la pioche
            short currentDurability = pickaxe.getDurability();
            short maxDurability = pickaxe.getType().getMaxDurability();

            // Prendre en compte l'enchantement durabilité
            int durabilityLevel = playerData.getEnchantmentLevel("durability");
            double durabilityBonus = durabilityLevel * 10.0;
            int maxDurabilityWithBonus = (int) (maxDurability * (1.0 + durabilityBonus / 100.0));

            double healthPercent = ((double) (maxDurabilityWithBonus - currentDurability) / maxDurabilityWithBonus) * 100;

            lore.add("§e⛏️ §lÉTAT ACTUEL");
            lore.add("§7│ §eDurabilité: " + getDurabilityColorForButton(healthPercent) + String.format("%.1f%%", healthPercent));
            lore.add("§7│ §ePoints: §6" + (maxDurabilityWithBonus - currentDurability) + "§7/§6" + maxDurabilityWithBonus);

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

        meta.setLore(lore);
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
        meta.setDisplayName("§d✨ §lGestion des Cristaux §d✨");

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

        meta.setLore(lore);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        cristalsBtn.setItemMeta(meta);

        return cristalsBtn;
    }
}
