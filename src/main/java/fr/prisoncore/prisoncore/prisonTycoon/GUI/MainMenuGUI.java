package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
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
import java.util.Set;

/**
 * Menu principal du système d'enchantements et features
 */
public class MainMenuGUI {

    private final PrisonTycoon plugin;

    // Slots du menu principal
    private static final int PLAYER_HEAD_SLOT = 4;
    private static final int ECONOMIC_SLOT = 10;
    private static final int EFFICIENCY_SLOT = 12;
    private static final int MOBILITY_SLOT = 14;
    private static final int SPECIAL_SLOT = 16;

    // Nouveaux slots pour features futures (ligne du bas)
    private static final int CRYSTALS_SLOT = 19;
    private static final int UNIQUE_ENCHANTS_SLOT = 21;
    private static final int PETS_SLOT = 23;
    private static final int MAIN_MENU_SLOT = 25;

    public MainMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu principal
     */
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 36, "§6✨ PrisonTycoon - Menu Principal ✨");

        // Remplissage décoratif
        fillBorders(gui);

        // Tête du joueur avec informations économiques
        gui.setItem(PLAYER_HEAD_SLOT, createPlayerHead(player));

        // Catégories d'enchantements (ligne du milieu)
        gui.setItem(ECONOMIC_SLOT, createCategoryItem(EnchantmentCategory.ECONOMIC, player));
        gui.setItem(EFFICIENCY_SLOT, createCategoryItem(EnchantmentCategory.EFFICIENCY, player));
        gui.setItem(MOBILITY_SLOT, createCategoryItem(EnchantmentCategory.MOBILITY, player));
        gui.setItem(SPECIAL_SLOT, createCategoryItem(EnchantmentCategory.SPECIAL, player));

        // NOUVEAU: Features futures (ligne du bas)
        gui.setItem(CRYSTALS_SLOT, createFutureFeatureItem("Cristaux", Material.AMETHYST_SHARD,
                "§5Système de cristaux magiques", "§7Implémentation future"));

        gui.setItem(UNIQUE_ENCHANTS_SLOT, createFutureFeatureItem("Enchantements Uniques", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "§dEnchantements légendaires rares", "§7Implémentation future"));

        gui.setItem(PETS_SLOT, createFutureFeatureItem("Pets", Material.WOLF_SPAWN_EGG,
                "§6Compagnons de minage", "§7Implémentation future"));

        gui.setItem(MAIN_MENU_SLOT, createFutureFeatureItem("Menu Principal", Material.COMPASS,
                "§eNavigation générale", "§7Implémentation future"));

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Gère les clics dans le menu principal
     */
    public void handleMainMenuClick(Player player, int slot, ItemStack item) {
        switch (slot) {
            case ECONOMIC_SLOT -> plugin.getCategoryMenuGUI().openCategoryMenu(player, EnchantmentCategory.ECONOMIC);
            case EFFICIENCY_SLOT -> plugin.getCategoryMenuGUI().openCategoryMenu(player, EnchantmentCategory.EFFICIENCY);
            case MOBILITY_SLOT -> plugin.getCategoryMenuGUI().openCategoryMenu(player, EnchantmentCategory.MOBILITY);
            case SPECIAL_SLOT -> plugin.getCategoryMenuGUI().openCategoryMenu(player, EnchantmentCategory.SPECIAL);

            // Features futures
            case CRYSTALS_SLOT -> plugin.getCrystalsMenuGUI().openCrystalsMenu(player);
            case UNIQUE_ENCHANTS_SLOT -> plugin.getUniqueEnchantsMenuGUI().openUniqueEnchantsMenu(player);
            case PETS_SLOT -> plugin.getPetsMenuGUI().openPetsMenu(player);
            case MAIN_MENU_SLOT -> plugin.getMainMenuGUI().openGeneralMainMenu(player);
        }
    }

    /**
     * Crée un item de catégorie d'enchantements
     */
    private ItemStack createCategoryItem(EnchantmentCategory category, Player player) {
        Material material = switch (category) {
            case ECONOMIC -> Material.EMERALD;
            case EFFICIENCY -> Material.DIAMOND;
            case MOBILITY -> Material.FEATHER;
            case SPECIAL -> Material.NETHER_STAR;
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

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
            case EFFICIENCY -> {
                lore.add("§7Enchantements qui améliorent l'efficacité");
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

        meta.setDisplayName("§e🔮 " + name);

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
     * Remplit les bordures avec des items décoratifs
     */
    private void fillBorders(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.setDisplayName("§7");
        filler.setItemMeta(meta);

        int size = gui.getSize();

        // Bordures haut et bas
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, filler);
            gui.setItem(size - 9 + i, filler);
        }

        // Bordures côtés
        for (int i = 9; i < size - 9; i += 9) {
            gui.setItem(i, filler);
            gui.setItem(i + 8, filler);
        }
    }
}