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

public class BankMenu extends BaseMenu {

    public BankMenu(CustomSkyblock plugin, MenuManager menuManager) {
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

        Inventory inv = createInventory(36, ChatColor.DARK_GREEN + "Banque de l'île");

        // Solde actuel
        double bankBalance = island.getBank();
        double playerBalance = plugin.getPrisonTycoonHook().getCoins(player.getUniqueId());

        inv.setItem(4, createItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "Solde de la banque",
                ChatColor.GRAY + "Solde banque: " + ChatColor.WHITE + plugin.getEconomyManager().formatMoney(bankBalance),
                ChatColor.GRAY + "Vos coins: " + ChatColor.GOLD + plugin.getPrisonTycoonHook().getCoins(player.getUniqueId()),
                ChatColor.GRAY + "Vos tokens: " + ChatColor.LIGHT_PURPLE + plugin.getPrisonTycoonHook().getTokens(player.getUniqueId()),
                ChatColor.GRAY + "Vos beacons: " + ChatColor.AQUA + plugin.getPrisonTycoonHook().getBeacons(player.getUniqueId()),
                "",
                ChatColor.YELLOW + "La banque stocke l'argent commun de l'île"));

        // Actions rapides avec montants prédéfinis
        List<Double> quickAmounts = List.of(100.0, 500.0, 1000.0, 5000.0);
        int[] quickSlots = {10, 11, 12, 13};

        for (int i = 0; i < quickAmounts.size(); i++) {
            double amount = quickAmounts.get(i);
            boolean canAfford = plugin.getPrisonTycoonHook().getCoins(player.getUniqueId()) >= amount;

            Material material = canAfford ? Material.GOLD_INGOT : Material.CLAY;
            ChatColor nameColor = canAfford ? ChatColor.YELLOW : ChatColor.GRAY;

            inv.setItem(quickSlots[i], createItem(material, nameColor + "Déposer " + (int)amount + " coins",
                    ChatColor.GRAY + "Déposer rapidement",
                    ChatColor.GRAY + "" + (int)amount + " coins dans la banque",
                    "",
                    canAfford ? ChatColor.GREEN + "Clic pour déposer" : ChatColor.RED + "Coins insuffisants"));
        }

        // Actions rapides de retrait
        int[] withdrawSlots = {19, 20, 21, 22};

        for (int i = 0; i < quickAmounts.size(); i++) {
            double amount = quickAmounts.get(i);
            boolean bankHasAmount = bankBalance >= amount;

            Material material = bankHasAmount ? Material.DIAMOND : Material.CLAY;
            ChatColor nameColor = bankHasAmount ? ChatColor.AQUA : ChatColor.GRAY;

            inv.setItem(withdrawSlots[i], createItem(material, nameColor + "Retirer " + (int)amount,
                    ChatColor.GRAY + "Retirer rapidement",
                    ChatColor.GRAY + "" + (int)amount + " de la banque vers vos coins",
                    "",
                    bankHasAmount ? ChatColor.GREEN + "Clic pour retirer" : ChatColor.RED + "Fonds insuffisants"));
        }

        // Actions personnalisées
        inv.setItem(15, createItem(Material.HOPPER, ChatColor.YELLOW + "Déposer montant personnalisé",
                ChatColor.GRAY + "Choisir le montant exact",
                ChatColor.GRAY + "à déposer dans la banque",
                "",
                ChatColor.YELLOW + "Clic pour déposer"));

        inv.setItem(24, createItem(Material.DROPPER, ChatColor.AQUA + "Retirer montant personnalisé",
                ChatColor.GRAY + "Choisir le montant exact",
                ChatColor.GRAY + "à retirer de la banque",
                "",
                ChatColor.YELLOW + "Clic pour retirer"));

        // Actions spéciales
        inv.setItem(16, createItem(Material.CHEST, ChatColor.GOLD + "Déposer tout",
                ChatColor.GRAY + "Déposer tous vos coins",
                ChatColor.GRAY + "dans la banque de l'île",
                ChatColor.GRAY + "Montant: " + ChatColor.WHITE + plugin.getPrisonTycoonHook().getCoins(player.getUniqueId()) + " coins",
                "",
                ChatColor.YELLOW + "Clic pour déposer tout"));

        inv.setItem(25, createItem(Material.ENDER_CHEST, ChatColor.GOLD + "Retirer tout",
                ChatColor.GRAY + "Retirer tout l'argent",
                ChatColor.GRAY + "de la banque vers vos coins",
                ChatColor.GRAY + "Montant: " + ChatColor.WHITE + plugin.getEconomyManager().formatMoney(bankBalance),
                "",
                ChatColor.YELLOW + "Clic pour retirer tout"));

        // Historique des transactions (placeholder pour une future fonctionnalité)
        inv.setItem(7, createItem(Material.BOOK, ChatColor.BLUE + "Historique des transactions",
                ChatColor.GRAY + "Voir l'historique des",
                ChatColor.GRAY + "dépôts et retraits",
                "",
                ChatColor.GRAY + "Fonctionnalité bientôt disponible"));

        // Statistiques
        inv.setItem(1, createItem(Material.CLOCK, ChatColor.AQUA + "Statistiques",
                ChatColor.GRAY + "Revenus passifs par heure:",
                ChatColor.WHITE + "+ " + calculateHourlyIncome(island) + " coins",
                ChatColor.GRAY + "Basé sur le niveau de l'île",
                "",
                ChatColor.YELLOW + "Les revenus sont automatiques"));

        // Bouton retour
        inv.setItem(31, createBackButton());

        fillEmptySlots(inv, Material.GREEN_STAINED_GLASS_PANE);

        player.openInventory(inv);
        setPlayerMenu(player, getMenuType());
        setMenuData(player, "island", island);
    }

    @Override
    public void handleClick(Player player, int slot) {
        Island island = (Island) getMenuData(player, "island");
        if (island == null) return;

        switch (slot) {
            case 31 -> openMainMenu(player); // Retour
            case 7 -> { // Historique
                player.sendMessage(ChatColor.YELLOW + "Historique des transactions bientôt disponible !");
            }
            case 15 -> { // Déposer montant personnalisé
                player.closeInventory();
                plugin.getEconomyManager().startBankDeposit(player, island);
            }
            case 24 -> { // Retirer montant personnalisé
                player.closeInventory();
                plugin.getEconomyManager().startBankWithdrawal(player, island);
            }
            case 16 -> { // Déposer tout
                handleDepositAll(player, island);
            }
            case 25 -> { // Retirer tout
                handleWithdrawAll(player, island);
            }
            default -> {
                // Gestion des montants rapides
                handleQuickAction(player, slot, island);
            }
        }
    }

    @Override
    public String getMenuType() {
        return "bank";
    }

    private void handleQuickAction(Player player, int slot, Island island) {
        List<Double> quickAmounts = List.of(100.0, 500.0, 1000.0, 5000.0);
        int[] depositSlots = {10, 11, 12, 13};
        int[] withdrawSlots = {19, 20, 21, 22};

        // Vérifier si c'est un dépôt rapide
        for (int i = 0; i < depositSlots.length; i++) {
            if (slot == depositSlots[i]) {
                double amount = quickAmounts.get(i);
                handleQuickDeposit(player, island, amount);
                return;
            }
        }

        // Vérifier si c'est un retrait rapide
        for (int i = 0; i < withdrawSlots.length; i++) {
            if (slot == withdrawSlots[i]) {
                double amount = quickAmounts.get(i);
                handleQuickWithdraw(player, island, amount);
                return;
            }
        }
    }

    private void handleQuickDeposit(Player player, Island island, double amount) {
        long coinsAmount = Math.round(amount);

        if (!plugin.getPrisonTycoonHook().hasCoins(player.getUniqueId(), coinsAmount)) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas assez de coins !");
            return;
        }

        // Effectuer la transaction via PrisonTycoon
        if (plugin.getPrisonTycoonHook().removeCoins(player.getUniqueId(), coinsAmount)) {
            island.addToBank(amount);
            plugin.getDatabaseManager().saveIsland(island);

            player.sendMessage(ChatColor.GREEN + "Vous avez déposé " + ChatColor.GOLD +
                    coinsAmount + " coins " + ChatColor.GREEN + "dans la banque de l'île !");

            // Rafraîchir le menu
            open(player);
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors du dépôt !");
        }
    }

    private void handleQuickWithdraw(Player player, Island island, double amount) {
        if (!island.removeFromBank(amount)) {
            player.sendMessage(ChatColor.RED + "Fonds insuffisants dans la banque de l'île !");
            return;
        }

        // Donner les coins via PrisonTycoon
        long coinsAmount = Math.round(amount);
        plugin.getPrisonTycoonHook().removeCoins(player.getUniqueId(), coinsAmount);
        plugin.getDatabaseManager().saveIsland(island);

        player.sendMessage(ChatColor.GREEN + "Vous avez retiré " + ChatColor.GOLD +
                coinsAmount + " coins " + ChatColor.GREEN + "de la banque de l'île !");

        // Rafraîchir le menu
        open(player);
    }

    private void handleDepositAll(Player player, Island island) {
        long playerCoins = plugin.getPrisonTycoonHook().getCoins(player.getUniqueId());

        if (playerCoins <= 0) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas de coins à déposer !");
            return;
        }

        if (plugin.getPrisonTycoonHook().removeCoins(player.getUniqueId(), playerCoins)) {
            island.addToBank(playerCoins);
            plugin.getDatabaseManager().saveIsland(island);

            player.sendMessage(ChatColor.GREEN + "Vous avez déposé tous vos coins (" +
                    ChatColor.GOLD + playerCoins + ChatColor.GREEN + ") dans la banque !");

            // Rafraîchir le menu
            open(player);
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors du dépôt !");
        }
    }

    private void handleWithdrawAll(Player player, Island island) {
        double bankBalance = island.getBank();

        if (bankBalance <= 0) {
            player.sendMessage(ChatColor.RED + "La banque de l'île est vide !");
            return;
        }

        if (island.removeFromBank(bankBalance)) {
            long coinsAmount = Math.round(bankBalance);
            plugin.getPrisonTycoonHook().addCoins(player.getUniqueId(), coinsAmount);
            plugin.getDatabaseManager().saveIsland(island);

            player.sendMessage(ChatColor.GREEN + "Vous avez retiré tout l'argent de la banque (" +
                    ChatColor.GOLD + coinsAmount + " coins" + ChatColor.GREEN + ") !");

            // Rafraîchir le menu
            open(player);
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors du retrait !");
        }
    }

    private String calculateHourlyIncome(Island island) {
        // Calcul basé sur le niveau de l'île (similaire à EconomyManager)
        double baseIncome = island.getLevel() * 0.5;
        double sizeMultiplier = 1.0 + (island.getSize() - 50) * 0.001;
        double hourlyIncome = baseIncome * sizeMultiplier;

        return String.format("%.1f", hourlyIncome);
    }
}