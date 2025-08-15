package fr.skyblock.menus;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.managers.MenuManager;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;

public class UpgradeMenu extends BaseMenu {

    public UpgradeMenu(CustomSkyblock plugin, MenuManager menuManager) {
        super(plugin, menuManager);
    }

    @Override
    public void open(Player player) {
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(player.getUniqueId());
        if (skyblockPlayer == null || !skyblockPlayer.hasIsland()) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas d'île !");
            return;
        }

        Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
        if (island == null) {
            player.sendMessage(ChatColor.RED + "Impossible de charger votre île !");
            return;
        }

        Inventory inv = createInventory(45, ChatColor.DARK_PURPLE + "Améliorer l'île");

        // === AGRANDISSEMENT AVEC BEACONS ===
        setupExpansionUpgrades(inv, island, player);

        // === AMÉLIORATION DU NIVEAU AVEC COINS ===
        setupLevelUpgrades(inv, island, player);

        // === AMÉLIORATIONS SPÉCIALES ===
        setupSpecialUpgrades(inv, island, player);

        // === ÉCONOMIE ET STATISTIQUES ===
        setupEconomyDisplay(inv, player);

        // Bouton retour
        inv.setItem(40, createBackButton());

        fillEmptySlots(inv, Material.PURPLE_STAINED_GLASS_PANE);

        player.openInventory(inv);
        setPlayerMenu(player, getMenuType());
        setMenuData(player, "island", island);
    }

    private void setupExpansionUpgrades(Inventory inv, Island island, Player player) {
        int currentSize = island.getSize();
        int maxSize = plugin.getMaxIslandSize();

        // Agrandissement +25
        int nextSize25 = currentSize + 25;
        if (nextSize25 <= maxSize) {
            long cost25 = plugin.getPrisonTycoonHook().calculateExpandCost(currentSize, nextSize25);
            boolean canAfford25 = plugin.getPrisonTycoonHook().hasBeacons(player.getUniqueId(), cost25);

            List<String> lore25 = new ArrayList<>();
            lore25.add(ChatColor.GRAY + "Taille actuelle: " + ChatColor.WHITE + currentSize + "x" + currentSize);
            lore25.add(ChatColor.GRAY + "Nouvelle taille: " + ChatColor.WHITE + nextSize25 + "x" + nextSize25);
            lore25.add(ChatColor.GRAY + "Coût: " + ChatColor.AQUA + cost25 + " beacons");
            lore25.add(ChatColor.GRAY + "Vos beacons: " + ChatColor.WHITE + plugin.getPrisonTycoonHook().getBeacons(player.getUniqueId()));
            lore25.add("");
            lore25.add(ChatColor.YELLOW + "Avantages:");
            lore25.add(ChatColor.GRAY + "• Plus d'espace pour construire");
            lore25.add(ChatColor.GRAY + "• Augmentation des revenus passifs");
            lore25.add("");
            lore25.add(canAfford25 ? ChatColor.GREEN + "Clic pour agrandir" : ChatColor.RED + "Pas assez de beacons");

            inv.setItem(10, createItem(canAfford25 ? Material.EMERALD : Material.BARRIER,
                    ChatColor.GREEN + "Agrandir +25", lore25));
        }

        // Agrandissement +50 (si possible)
        int nextSize50 = currentSize + 50;
        if (nextSize50 <= maxSize) {
            long cost50 = plugin.getPrisonTycoonHook().calculateExpandCost(currentSize, nextSize50);
            boolean canAfford50 = plugin.getPrisonTycoonHook().hasBeacons(player.getUniqueId(), cost50);

            List<String> lore50 = new ArrayList<>();
            lore50.add(ChatColor.GRAY + "Taille actuelle: " + ChatColor.WHITE + currentSize + "x" + currentSize);
            lore50.add(ChatColor.GRAY + "Nouvelle taille: " + ChatColor.WHITE + nextSize50 + "x" + nextSize50);
            lore50.add(ChatColor.GRAY + "Coût: " + ChatColor.AQUA + cost50 + " beacons");
            lore50.add(ChatColor.GRAY + "Vos beacons: " + ChatColor.WHITE + plugin.getPrisonTycoonHook().getBeacons(player.getUniqueId()));
            lore50.add("");
            lore50.add(ChatColor.YELLOW + "Avantages:");
            lore50.add(ChatColor.GRAY + "• Encore plus d'espace");
            lore50.add(ChatColor.GRAY + "• Bonus de revenus important");
            lore50.add(ChatColor.GRAY + "• Réduction du coût par bloc");
            lore50.add("");
            lore50.add(canAfford50 ? ChatColor.GREEN + "Clic pour agrandir" : ChatColor.RED + "Pas assez de beacons");

            inv.setItem(11, createItem(canAfford50 ? Material.DIAMOND : Material.BARRIER,
                    ChatColor.GREEN + "Agrandir +50", lore50));
        }

        // Agrandissement maximum
        if (currentSize < maxSize) {
            long costMax = plugin.getPrisonTycoonHook().calculateExpandCost(currentSize, maxSize);
            boolean canAffordMax = plugin.getPrisonTycoonHook().hasBeacons(player.getUniqueId(), costMax);

            List<String> loreMax = new ArrayList<>();
            loreMax.add(ChatColor.GRAY + "Taille actuelle: " + ChatColor.WHITE + currentSize + "x" + currentSize);
            loreMax.add(ChatColor.GRAY + "Taille maximale: " + ChatColor.WHITE + maxSize + "x" + maxSize);
            loreMax.add(ChatColor.GRAY + "Coût: " + ChatColor.AQUA + costMax + " beacons");
            loreMax.add(ChatColor.GRAY + "Vos beacons: " + ChatColor.WHITE + plugin.getPrisonTycoonHook().getBeacons(player.getUniqueId()));
            loreMax.add("");
            loreMax.add(ChatColor.GOLD + "⭐ AMÉLIORATION ULTIME ⭐");
            loreMax.add(ChatColor.YELLOW + "Avantages:");
            loreMax.add(ChatColor.GRAY + "• Taille maximale possible");
            loreMax.add(ChatColor.GRAY + "• Revenus passifs maximisés");
            loreMax.add(ChatColor.GRAY + "• Prestige maximal");
            loreMax.add("");
            loreMax.add(canAffordMax ? ChatColor.GREEN + "Clic pour maximiser" : ChatColor.RED + "Pas assez de beacons");

            inv.setItem(12, createItem(canAffordMax ? Material.NETHERITE_BLOCK : Material.BARRIER,
                    ChatColor.GOLD + "Taille maximale", loreMax));
        }
    }

    private void setupLevelUpgrades(Inventory inv, Island island, Player player) {
        int currentLevel = island.getLevel();

        // Amélioration niveau +1
        int nextLevel1 = currentLevel + 1;
        long cost1 = plugin.getPrisonTycoonHook().calculateLevelUpgradeCost(currentLevel, nextLevel1);
        boolean canAfford1 = plugin.getPrisonTycoonHook().hasCoins(player.getUniqueId(), cost1);

        List<String> lore1 = new ArrayList<>();
        lore1.add(ChatColor.GRAY + "Niveau actuel: " + ChatColor.WHITE + currentLevel);
        lore1.add(ChatColor.GRAY + "Nouveau niveau: " + ChatColor.WHITE + nextLevel1);
        lore1.add(ChatColor.GRAY + "Coût: " + ChatColor.GOLD + cost1 + " coins");
        lore1.add(ChatColor.GRAY + "Vos coins: " + ChatColor.WHITE + plugin.getPrisonTycoonHook().getCoins(player.getUniqueId()));
        lore1.add("");
        lore1.add(ChatColor.YELLOW + "Avantages:");
        lore1.add(ChatColor.GRAY + "• Augmentation du prestige");
        lore1.add(ChatColor.GRAY + "• Meilleurs revenus passifs");
        if (nextLevel1 == 10) lore1.add(ChatColor.GREEN + "• Déblocage: 1er warp d'île");
        if (nextLevel1 == 100) lore1.add(ChatColor.GREEN + "• Déblocage: 2ème warp d'île");
        if (nextLevel1 == 1000) lore1.add(ChatColor.GREEN + "• Déblocage: 3ème warp d'île");
        lore1.add("");
        lore1.add(canAfford1 ? ChatColor.GREEN + "Clic pour améliorer" : ChatColor.RED + "Pas assez de coins");

        inv.setItem(19, createItem(canAfford1 ? Material.EXPERIENCE_BOTTLE : Material.BARRIER,
                ChatColor.BLUE + "Améliorer niveau +1", lore1));

        // Amélioration niveau +5
        int nextLevel5 = currentLevel + 5;
        long cost5 = plugin.getPrisonTycoonHook().calculateLevelUpgradeCost(currentLevel, nextLevel5);
        boolean canAfford5 = plugin.getPrisonTycoonHook().hasCoins(player.getUniqueId(), cost5);

        List<String> lore5 = new ArrayList<>();
        lore5.add(ChatColor.GRAY + "Niveau actuel: " + ChatColor.WHITE + currentLevel);
        lore5.add(ChatColor.GRAY + "Nouveau niveau: " + ChatColor.WHITE + nextLevel5);
        lore5.add(ChatColor.GRAY + "Coût: " + ChatColor.GOLD + cost5 + " coins");
        lore5.add(ChatColor.GRAY + "Vos coins: " + ChatColor.WHITE + plugin.getPrisonTycoonHook().getCoins(player.getUniqueId()));
        lore5.add("");
        lore5.add(ChatColor.YELLOW + "Pack amélioration rapide !");
        lore5.add(ChatColor.GRAY + "• Bonus de prestige important");
        lore5.add(ChatColor.GRAY + "• Revenus passifs boostés");
        lore5.add(ChatColor.GRAY + "• Économie de temps");
        lore5.add("");
        lore5.add(canAfford5 ? ChatColor.GREEN + "Clic pour améliorer" : ChatColor.RED + "Pas assez de coins");

        inv.setItem(20, createItem(canAfford5 ? Material.ENCHANTED_GOLDEN_APPLE : Material.BARRIER,
                ChatColor.BLUE + "Améliorer niveau +5", lore5));
    }

    private void setupSpecialUpgrades(Inventory inv, Island island, Player player) {
        // Boost de revenus temporaire
        inv.setItem(28, createItem(Material.CLOCK, ChatColor.YELLOW + "Boost de revenus",
                ChatColor.GRAY + "Double vos revenus passifs",
                ChatColor.GRAY + "pendant 24 heures",
                ChatColor.GRAY + "Coût: " + ChatColor.AQUA + "50 beacons",
                "",
                ChatColor.YELLOW + "Clic pour activer"));

        // Protection avancée
        inv.setItem(29, createItem(Material.SHIELD, ChatColor.BLUE + "Protection avancée",
                ChatColor.GRAY + "Protection contre les griefs",
                ChatColor.GRAY + "et les exploits pendant 7 jours",
                ChatColor.GRAY + "Coût: " + ChatColor.GOLD + "10000 coins",
                "",
                ChatColor.YELLOW + "Clic pour activer"));

        // Pack de warps bonus
        boolean hasVip = plugin.getPrisonTycoonHook().hasCustomPermission(player, "specialmine.vip");
        if (!hasVip) {
            inv.setItem(30, createItem(Material.ENDER_PEARL, ChatColor.GOLD + "Pack Warps VIP",
                    ChatColor.GRAY + "Obtenez +1 warp d'île",
                    ChatColor.GRAY + "permanent pour votre île",
                    ChatColor.GRAY + "Coût: " + ChatColor.AQUA + "500 beacons",
                    "",
                    ChatColor.YELLOW + "Amélioration permanente !"));
        }
    }

    private void setupEconomyDisplay(Inventory inv, Player player) {
        // Économie du joueur
        inv.setItem(4, createItem(Material.GOLD_INGOT, ChatColor.GOLD + "Votre Économie",
                ChatColor.GRAY + "Coins: " + ChatColor.GOLD + plugin.getPrisonTycoonHook().getCoins(player.getUniqueId()),
                ChatColor.GRAY + "Tokens: " + ChatColor.LIGHT_PURPLE + plugin.getPrisonTycoonHook().getTokens(player.getUniqueId()),
                ChatColor.GRAY + "Beacons: " + ChatColor.AQUA + plugin.getPrisonTycoonHook().getBeacons(player.getUniqueId()),
                "",
                ChatColor.YELLOW + "Ressources pour les améliorations"));

        // Calculateur de coûts
        inv.setItem(6, createItem(Material.COMMAND_BLOCK, ChatColor.AQUA + "Calculateur",
                ChatColor.GRAY + "Calculez le coût total",
                ChatColor.GRAY + "de vos améliorations futures",
                "",
                ChatColor.YELLOW + "Clic pour calculer"));
    }

    @Override
    public void handleClick(Player player, int slot) {
        Island island = (Island) getMenuData(player, "island");
        if (island == null) return;

        switch (slot) {
            case 40 -> openMainMenu(player); // Retour
            case 4 -> { // Économie
                player.closeInventory();
                plugin.getPrisonTycoonHook().showPlayerEconomy(player);
            }
            case 6 -> openCalculator(player, island); // Calculateur
            case 10 -> handleExpansion(player, island, island.getSize() + 25); // +25
            case 11 -> handleExpansion(player, island, island.getSize() + 50); // +50
            case 12 -> handleExpansion(player, island, plugin.getMaxIslandSize()); // Max
            case 19 -> handleLevelUpgrade(player, island, island.getLevel() + 1); // +1 niveau
            case 20 -> handleLevelUpgrade(player, island, island.getLevel() + 5); // +5 niveaux
            case 28 -> handleRevenueBoost(player, island); // Boost revenus
            case 29 -> handleAdvancedProtection(player, island); // Protection
            case 30 -> handleVipWarps(player, island); // Warps VIP
        }
    }

    @Override
    public String getMenuType() {
        return "upgrade";
    }

    private void handleExpansion(Player player, Island island, int newSize) {
        if (newSize > plugin.getMaxIslandSize()) {
            player.sendMessage(ChatColor.RED + "Taille maximale atteinte !");
            return;
        }

        if (plugin.getIslandManager().expandIsland(island, newSize)) {
            player.sendMessage(ChatColor.GREEN + "Île agrandie avec succès à " + newSize + "x" + newSize + " !");
            open(player); // Rafraîchir
        } else {
            long cost = plugin.getPrisonTycoonHook().calculateExpandCost(island.getSize(), newSize);
            player.sendMessage(ChatColor.RED + "Impossible d'agrandir l'île !");
            player.sendMessage(ChatColor.GRAY + "Beacons requis: " + ChatColor.AQUA + cost);
        }
    }

    private void handleLevelUpgrade(Player player, Island island, int newLevel) {
        if (plugin.getIslandManager().upgradeLevelIsland(island, newLevel)) {
            player.sendMessage(ChatColor.GREEN + "Niveau de l'île amélioré à " + newLevel + " !");

            // Messages spéciaux pour les niveaux importants
            if (newLevel == 10) {
                player.sendMessage(ChatColor.GOLD + "🎉 Vous pouvez maintenant créer votre premier warp d'île !");
            } else if (newLevel == 100) {
                player.sendMessage(ChatColor.GOLD + "🎉 Deuxième slot de warp débloqué !");
            } else if (newLevel == 1000) {
                player.sendMessage(ChatColor.GOLD + "🎉 Troisième slot de warp débloqué ! Île de prestige maximum !");
            }

            open(player); // Rafraîchir
        } else {
            long cost = plugin.getPrisonTycoonHook().calculateLevelUpgradeCost(island.getLevel(), newLevel);
            player.sendMessage(ChatColor.RED + "Impossible d'améliorer le niveau !");
            player.sendMessage(ChatColor.GRAY + "Coins requis: " + ChatColor.GOLD + cost);
        }
    }

    private void handleRevenueBoost(Player player, Island island) {
        // TODO: Implémenter le boost de revenus temporaire
        player.sendMessage(ChatColor.YELLOW + "Fonctionnalité de boost de revenus bientôt disponible !");
    }

    private void handleAdvancedProtection(Player player, Island island) {
        // TODO: Implémenter la protection avancée
        player.sendMessage(ChatColor.YELLOW + "Fonctionnalité de protection avancée bientôt disponible !");
    }

    private void handleVipWarps(Player player, Island island) {
        // TODO: Implémenter l'achat de warps VIP
        player.sendMessage(ChatColor.YELLOW + "Fonctionnalité de warps VIP bientôt disponible !");
    }

    private void openCalculator(Player player, Island island) {
        // TODO: Implémenter un calculateur de coûts
        player.sendMessage(ChatColor.YELLOW + "Calculateur bientôt disponible !");
    }
}