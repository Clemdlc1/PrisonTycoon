package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.BankManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique du système bancaire
 */
public class BankGUI {

    // Slots pour le menu principal
    private static final int SAVINGS_SLOT = 11;
    private static final int INVESTMENT_SLOT = 13;
    private static final int SAFE_SLOT = 15;
    private static final int BANK_LEVEL_SLOT = 21;
    private static final int CLOSE_SLOT = 35;
    private static final int BANK_TYPE_SLOT = 23;
    // Slots pour le menu d'investissement
    private static final int[] INVESTMENT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 25};
    private final PrisonTycoon plugin;
    private final GUIManager guiManager;
    private final BankManager bankManager;

    public BankGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiManager = plugin.getGUIManager();
        this.bankManager = plugin.getBankManager();
    }

    /**
     * Ouvre le menu principal de la banque
     */
    public void openMainMenu(Player player) {
        if (player == null) return;
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data.getBankType() == fr.prisontycoon.data.BankType.NONE) {
            if (!bankManager.canUseSavings(player)) {
                player.sendMessage("§c❌ Le système bancaire nécessite le rang F+ pour choisir une banque !");
                return;
            }
            openBankTypeSelector(player);
            return;
        }
        if (!bankManager.canUseSavings(player)) {
            player.sendMessage("§c❌ Le système bancaire nécessite le rang F+ !");
            return;
        }

        Inventory gui = plugin.getGUIManager().createInventory(36, "§6🏦 Banque PrisonTycoon");
        guiManager.registerOpenGUI(player, GUIType.BANK_MAIN, gui);
        plugin.getGUIManager().fillBorders(gui);
        setupMainMenu(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    public void openBankTypeSelector(Player player) {
        if (!bankManager.canUseSavings(player)) {
            player.sendMessage("§c❌ Le système bancaire nécessite le rang F+ pour choisir une banque !");
            return;
        }
        Inventory gui = plugin.getGUIManager().createInventory(27, "§d🏦 Choix de la Banque");
        guiManager.registerOpenGUI(player, GUIType.BANK_TYPE_SELECTOR, gui);
        plugin.getGUIManager().fillBorders(gui);

        // Placeholder info
        gui.setItem(4, createInfoItem());

        int[] slots = {10, 12, 14, 16};
        fr.prisontycoon.data.BankType[] types = {
                fr.prisontycoon.data.BankType.PRUDENTIA,
                fr.prisontycoon.data.BankType.MERCATORIA,
                fr.prisontycoon.data.BankType.INDUSTRIA,
                fr.prisontycoon.data.BankType.FORTIS
        };
        var current = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getBankType();
        for (int i = 0; i < slots.length; i++) {
            gui.setItem(slots[i], createBankTypeChoiceItem(types[i], current));
        }
        gui.setItem(22, createCloseItem());
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 1.0f, 1.0f);
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6🏦 Types de Banque");
        List<String> lore = new ArrayList<>();
        lore.add("§7Choisissez votre type de banque");
        lore.add("§7pour bénéficier d'avantages");
        lore.add("§7spécifiques à votre style de jeu.");
        lore.add("");
        lore.add("§c⚠ Changement possible 1 fois par semaine");
        lore.add("§e▶ Cliquez sur une banque pour la sélectionner");
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBankTypeChoiceItem(fr.prisontycoon.data.BankType type, fr.prisontycoon.data.BankType current) {
        Material icon = switch (type) {
            case PRUDENTIA -> Material.BOOK;
            case MERCATORIA -> Material.EMERALD;
            case INDUSTRIA -> Material.PISTON;
            case FORTIS -> Material.ENDER_CHEST;
            default -> Material.PAPER;
        };
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        boolean isCurrent = current == type;
        plugin.getGUIManager().applyName(meta, (isCurrent ? "§a" : "§e") + type.getDisplayName() + (isCurrent ? " §7(Actuelle)" : ""));
        List<String> lore = new ArrayList<>();
        lore.add("");
        double dSell = (type.getSellMultiplier() - 1.0) * 100.0;
        double dPrestige = (type.getPrestigeCostMultiplier() - 1.0) * 100.0;
        double dInvest = (type.getInvestmentBuyCostMultiplier() - 1.0) * 100.0;
        double dInterest = (type.getInterestGainMultiplier() - 1.0) * 100.0;
        double dSafe = (type.getSafeLimitMultiplier() - 1.0) * 100.0;
        if (dSell != 0)
            lore.add("§7• Vente: " + (dSell >= 0 ? "§a+" : "§c-") + String.format("%.0f%%", Math.abs(dSell)));
        if (dPrestige != 0)
            lore.add("§7• Coût prestige: " + (dPrestige >= 0 ? "§c+" : "§a-") + String.format("%.0f%%", Math.abs(dPrestige)));
        if (dInvest != 0)
            lore.add("§7• Coût investissement: " + (dInvest >= 0 ? "§c+" : "§a-") + String.format("%.0f%%", Math.abs(dInvest)));
        if (dInterest != 0)
            lore.add("§7• Intérêts: " + (dInterest >= 0 ? "§a+" : "§c-") + String.format("%.0f%%", Math.abs(dInterest)));
        if (type.getIslandPrintersBonus() != 0) lore.add("§7• Imprimantes île: §a+" + type.getIslandPrintersBonus());
        if (dSafe != 0)
            lore.add("§7• Coffre-fort: " + (dSafe >= 0 ? "§a+" : "§c-") + String.format("%.0f%%", Math.abs(dSafe)) + " §7capacité");
        lore.add("");
        if (isCurrent) {
            lore.add("§a✔ Déjà sélectionnée");
        } else {
            lore.add("§e▶ Cliquez pour choisir ce type");
        }
        plugin.getGUIManager().applyLore(meta, lore);
        // tag
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "bank_type"),
                PersistentDataType.STRING, type.name());
        if (isCurrent) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Configure le menu principal
     */
    private void setupMainMenu(Inventory gui, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Compte d'épargne
        gui.setItem(SAVINGS_SLOT, createSavingsItem(player, playerData));

        // Investissements
        gui.setItem(INVESTMENT_SLOT, createInvestmentItem(player, playerData));

        // Coffre-fort (si Prestige 10+)
        if (playerData.getPrestigeLevel() >= 10) {
            gui.setItem(SAFE_SLOT, createSafeItem(player, playerData));
        } else {
            gui.setItem(SAFE_SLOT, createLockedSafeItem());
        }

        // Niveau bancaire et type de banque
        gui.setItem(BANK_LEVEL_SLOT, createBankLevelItem(player, playerData));
        gui.setItem(BANK_TYPE_SLOT, createBankTypeItem(playerData));

        // Fermer
        gui.setItem(CLOSE_SLOT, createCloseItem());

        // Informations du joueur en haut
        gui.setItem(4, createPlayerInfoItem(player, playerData));
    }

    /**
     * Crée l'item du compte d'épargne
     */
    private ItemStack createSavingsItem(Player player, PlayerData playerData) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§a💰 Compte d'Épargne");

        List<String> lore = new ArrayList<>();
        lore.add("§7Solde actuel: §a" + NumberFormatter.format(playerData.getSavingsBalance()) + " coins");
        lore.add("§7Limite: §e" + NumberFormatter.format(bankManager.getSavingsLimit(player)) + " coins");
        lore.add("");

        int bankLevel = playerData.getBankLevel();
        double interestRate = 5.0 + (bankLevel * 1.0);
        lore.add("§7Taux d'intérêt: §e" + String.format("%.1f%%", interestRate) + " §7toutes les 12h");
        lore.add("§7Niveau bancaire: §6" + bankLevel + "/10");
        lore.add("");
        lore.add("§e▶ Cliquez pour gérer votre épargne");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item des investissements avec informations différenciées
     */
    private ItemStack createInvestmentItem(Player player, PlayerData playerData) {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§b📈 Investissements");

        List<String> lore = new ArrayList<>();

        Map<Material, Long> investments = playerData.getAllInvestments();
        boolean isTrader = bankManager.isTraderLevel3Plus(player);

        if (investments.isEmpty()) {
            lore.add("§7Aucun investissement actuel");
        } else {
            long totalValue = 0;
            long totalInvestments = 0;

            for (Map.Entry<Material, Long> entry : investments.entrySet()) {
                BankManager.InvestmentBlock block = bankManager.getInvestmentBlock(entry.getKey());
                if (block != null) {
                    totalValue += (long) (block.currentValue * entry.getValue());
                    totalInvestments += entry.getValue();
                }
            }

            lore.add("§7Investissements: §e" + NumberFormatter.format(totalInvestments) + " unités");
            lore.add("§7Valeur totale: §a" + NumberFormatter.format(totalValue) + " coins");
        }

        lore.add("");
        lore.add("§7Blocs disponibles: §e9 types");

        if (bankManager.isTraderLevel5Plus(player)) {
            lore.add("§6⚡ Bonus Commerçant Niv.5+: §eLevier x2 optionnel");
        }
        if (isTrader) {
            lore.add("§6⚡ Bonus Commerçant Niv.3+: §eInformations détaillées");
        } else {
            lore.add("§7§o💡 Devenez Commerçant pour plus d'infos !");
        }

        lore.add("");
        lore.add("§e▶ Cliquez pour gérer vos investissements");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item du coffre-fort
     */
    private ItemStack createSafeItem(Player player, PlayerData playerData) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§6🔒 Coffre-Fort");

        List<String> lore = new ArrayList<>();
        lore.add("§7Solde: §a" + NumberFormatter.format(playerData.getSafeBalance()) + " coins");
        lore.add("§7Limite: §e" + NumberFormatter.format(bankManager.getSafeLimit(player)) + " coins");
        lore.add("");
        lore.add("§c⚠ Frais de retrait: §e20%");
        lore.add("§c⚠ Frais de gestion: §e10% §7toutes les 12h");
        lore.add("");
        lore.add("§a✅ Protégé des resets prestige");
        lore.add("");
        lore.add("§e▶ Cliquez pour gérer votre coffre-fort");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item du coffre-fort verrouillé
     */
    private ItemStack createLockedSafeItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§c🔒 Coffre-Fort Verrouillé");

        List<String> lore = new ArrayList<>();
        lore.add("§c❌ Prestige 10+ requis");
        lore.add("");
        lore.add("§7Le coffre-fort permet de stocker");
        lore.add("§7des coins en sécurité, protégés");
        lore.add("§7des resets de prestige.");
        lore.add("");
        lore.add("§7Capacité: §e1,000M coins");
        lore.add("§7Frais de retrait: §c20%");
        lore.add("§7Frais de gestion: §c10% toutes les 12h");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée l'item du niveau bancaire
     */
    private ItemStack createBankLevelItem(Player player, PlayerData playerData) {
        int currentLevel = playerData.getBankLevel();
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§6⭐ Niveau Bancaire: " + currentLevel + "/10");

        List<String> lore = new ArrayList<>();
        lore.add("§7Niveau actuel: §e" + currentLevel);

        if (currentLevel < 10) {
            int nextLevel = currentLevel + 1;
            long requiredDeposits = bankManager.getBankLevelRequiredDeposits(nextLevel);
            long experienceCost = bankManager.getBankLevelExperienceCost(nextLevel);
            long totalDeposits = playerData.getTotalBankDeposits();

            lore.add("");
            lore.add("§7Prochain niveau (§e" + nextLevel + "§7):");
            lore.add("§7Dépôts requis: §e" + NumberFormatter.format(requiredDeposits));
            lore.add("§7Vos dépôts: " + (totalDeposits >= requiredDeposits ? "§a" : "§c") +
                    NumberFormatter.format(totalDeposits));
            lore.add("§7Coût XP: §b" + NumberFormatter.format(experienceCost));
            lore.add("§7Votre XP: " + (playerData.getExperience() >= experienceCost ? "§a" : "§c") +
                    NumberFormatter.format(playerData.getExperience()));
        }

        lore.add("");
        lore.add("§7Avantages actuels:");
        lore.add("§7• Intérêts épargne: §a+" + (currentLevel) + "%");
        lore.add("§7• Plafond épargne: §a+" + (currentLevel * 20) + "%");

        if (currentLevel < 10) {
            lore.add("");
            lore.add("§e▶ Cliquez pour améliorer");
        } else {
            lore.add("");
            lore.add("§a✅ Niveau maximum atteint!");
        }

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createBankTypeItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();

        var bankType = playerData.getBankType();
        plugin.getGUIManager().applyName(meta, "§d🏦 Type de Banque: " + bankType.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("");
        double dSell = (bankType.getSellMultiplier() - 1.0) * 100.0;
        double dPrestige = (bankType.getPrestigeCostMultiplier() - 1.0) * 100.0;
        double dInvest = (bankType.getInvestmentBuyCostMultiplier() - 1.0) * 100.0;
        double dInterest = (bankType.getInterestGainMultiplier() - 1.0) * 100.0;
        double dSafe = (bankType.getSafeLimitMultiplier() - 1.0) * 100.0;
        lore.add("§7• Vente: " + (dSell >= 0 ? "§a+" : "§c-") + String.format("%.0f%%", Math.abs(dSell)));
        lore.add("§7• Coût prestige: " + (dPrestige >= 0 ? "§c+" : "§a-") + String.format("%.0f%%", Math.abs(dPrestige)));
        lore.add("§7• Coût investissement: " + (dInvest >= 0 ? "§c+" : "§a-") + String.format("%.0f%%", Math.abs(dInvest)));
        lore.add("§7• Intérêts: " + (dInterest >= 0 ? "§a+" : "§c-") + String.format("%.0f%%", Math.abs(dInterest)));
        if (bankType.getIslandPrintersBonus() != 0) {
            lore.add("§7• Imprimantes île: §a+" + bankType.getIslandPrintersBonus());
        }
        if (Math.abs(dSafe) > 0.0001) {
            lore.add("§7• Coffre-fort: " + (dSafe >= 0 ? "§a+" : "§c-") + String.format("%.0f%%", Math.abs(dSafe)) + " §7capacité");
        }
        lore.add("");
        lore.add("§7Changement possible 1 fois / 7 jours");
        lore.add("§e▶ Cliquez pour changer");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item d'informations du joueur
     */
    private ItemStack createPlayerInfoItem(Player player, PlayerData playerData) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§6👤 " + player.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§7Solde: §a" + NumberFormatter.format(playerData.getCoins()) + " coins");
        lore.add("§7Tokens: §b" + NumberFormatter.format(playerData.getTokens()));
        lore.add("§7Expérience: §d" + NumberFormatter.format(playerData.getExperience()) + " XP");
        lore.add("§7Niveau bancaire: §6" + playerData.getBankLevel() + "/10");
        lore.add("§7Prestige: §5" + playerData.getPrestigeLevel());

        String profession = playerData.getActiveProfession();
        if (profession != null) {
            lore.add("§7Métier: §e" + profession.substring(0, 1).toUpperCase() + profession.substring(1) +
                    " §7Niv." + playerData.getProfessionLevel(profession));
        }

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Ouvre le menu des investissements
     */
    public void openInvestmentMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(54, "§b📈 Investissements");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.INVESTMENT_MENU, gui);
        plugin.getGUIManager().fillBorders(gui);
        setupInvestmentMenu(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    /**
     * Configure le menu des investissements
     */
    private void setupInvestmentMenu(Inventory gui, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Matériaux d'investissement
        Material[] materials = {
                Material.COBBLESTONE, Material.STONE, Material.COAL_ORE,
                Material.IRON_ORE, Material.COPPER_ORE, Material.GOLD_ORE,
                Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.BEACON
        };

        for (int i = 0; i < materials.length; i++) {
            gui.setItem(INVESTMENT_SLOTS[i], createInvestmentBlockItem(player, materials[i], playerData));
        }

        // Informations en bas
        gui.setItem(45, createBackButton());
        gui.setItem(49, createPortfolioSummaryItem(player, playerData));
        gui.setItem(53, createCloseItem());
    }

    /**
     * Crée un item pour un bloc d'investissement avec informations différenciées
     */
    private ItemStack createInvestmentBlockItem(Player player, Material material, PlayerData playerData) {
        BankManager.InvestmentBlock block = bankManager.getInvestmentBlock(material);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String blockName = bankManager.getBlockDisplayName(material);
        plugin.getGUIManager().applyName(meta, "§e" + blockName);

        List<String> lore = new ArrayList<>();
        boolean isTrader3Plus = bankManager.isTraderLevel3Plus(player);
        boolean isTrader5Plus = bankManager.isTraderLevel5Plus(player);

        // Informations de base pour tous
        lore.add("§7Valeur actuelle: §a" + NumberFormatter.format((long) block.currentValue) + " coins");
        lore.add("§7Investisseurs: §b" + NumberFormatter.format(block.totalInvestments));

        // Informations détaillées pour commerçants niveau 3+
        if (isTrader3Plus) {
            lore.add("§7Volatilité: §c" + String.format("%.1f%%", block.volatility * 100));

            // Évolution en temps réel
            List<BankManager.InvestmentHistory> history = bankManager.getInvestmentHistory(material);
            if (history.size() >= 2) {
                BankManager.InvestmentHistory current = history.getLast();
                BankManager.InvestmentHistory previous = history.get(history.size() - 2);

                double change = ((current.value - previous.value) / previous.value) * 100;
                String changeColor = change > 0 ? "§a+" : "§c";
                lore.add("§6⚡ Évolution: " + changeColor + String.format("%.2f%%", change));
            }
        }

        long playerInvestment = playerData.getInvestmentQuantity(material);
        if (playerInvestment > 0) {
            long value = (long) (block.currentValue * playerInvestment);
            lore.add("");
            lore.add("§7Votre investissement: §6" + NumberFormatter.format(playerInvestment) + " unités");
            lore.add("§7Valeur: §a" + NumberFormatter.format(value) + " coins");
        }

        lore.add("");

        // Instructions d'utilisation
        lore.add("§7Clic gauche: §eInfo détaillée");
        lore.add("§7Clic droit: §aAcheter rapidement");
        if (playerInvestment > 0) {
            lore.add("§7Shift + clic droit: §cVendre rapidement");
        }

        // Bonus commerçant
        if (isTrader5Plus) {
            lore.add("§6⚡ Levier x2 disponible");
        } else if (!isTrader3Plus) {
            lore.add("§7§o💡 Commerçant pour plus d'infos");
        }

        plugin.getGUIManager().applyLore(meta, lore);

        // Ajout des données pour le clic
        meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "investment_material"),
                PersistentDataType.STRING, material.name());

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un résumé du portefeuille avec support grandes valeurs
     */
    private ItemStack createPortfolioSummaryItem(Player player, PlayerData playerData) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§6📊 Résumé du Portefeuille");

        List<String> lore = new ArrayList<>();
        Map<Material, Long> investments = playerData.getAllInvestments();

        if (investments.isEmpty()) {
            lore.add("§7Aucun investissement");
        } else {
            long totalValue = 0;
            long totalQuantity = 0;

            for (Map.Entry<Material, Long> entry : investments.entrySet()) {
                BankManager.InvestmentBlock block = bankManager.getInvestmentBlock(entry.getKey());
                if (block != null) {
                    totalValue += (long) (block.currentValue * entry.getValue());
                    totalQuantity += entry.getValue();
                }
            }

            lore.add("§7Total investi: §e" + NumberFormatter.format(totalQuantity) + " unités");
            lore.add("§7Valeur totale: §a" + NumberFormatter.format(totalValue) + " coins");
            lore.add("§7Types d'investissements: §b" + investments.size());
        }

        if (bankManager.isTraderLevel5Plus(player)) {
            lore.add("");
            lore.add("§6⚡ Bonus Commerçant Actif:");
            lore.add("§7• Levier x2 optionnel sur achats");
            lore.add("§7• Informations détaillées");
        } else if (bankManager.isTraderLevel3Plus(player)) {
            lore.add("");
            lore.add("§6⚡ Bonus Commerçant Actif:");
            lore.add("§7• Informations détaillées");
        }

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée un bouton de retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§7← Retour");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retour au menu principal"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un bouton de fermeture
     */
    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c✖ Fermer");
        plugin.getGUIManager().applyLore(meta, List.of("§7Ferme ce menu"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Gère les clics dans le menu principal
     */
    public void handleMainMenuClick(Player player, int slot, ItemStack clicked) {
        switch (slot) {
            case SAVINGS_SLOT -> {
                // Ouvre un menu de chat pour gérer l'épargne
                player.closeInventory();
                player.sendMessage("§6═══════════════════════════════");
                player.sendMessage("§e💰 Gestion du Compte d'Épargne");
                player.sendMessage("§6═══════════════════════════════");
                player.sendMessage("§e/bank deposit <montant> §7- Déposer");
                player.sendMessage("§e/bank withdraw <montant> §7- Retirer");
                player.sendMessage("§7Exemple: §e/bank deposit 10k");
                player.sendMessage("§6═══════════════════════════════");
            }
            case INVESTMENT_SLOT -> openInvestmentMenu(player);
            case SAFE_SLOT -> {
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (playerData.getPrestigeLevel() >= 10) {
                    player.closeInventory();
                    player.sendMessage("§6═══════════════════════════════");
                    player.sendMessage("§6🔒 Gestion du Coffre-Fort");
                    player.sendMessage("§6═══════════════════════════════");
                    player.sendMessage("§e/bank safe deposit <montant> §7- Déposer");
                    player.sendMessage("§e/bank safe withdraw <montant> §7- Retirer");
                    player.sendMessage("§c⚠ Frais de retrait: 20%");
                    player.sendMessage("§6═══════════════════════════════");
                } else {
                    player.sendMessage("§c❌ Prestige 10+ requis pour le coffre-fort!");
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                }
            }
            case BANK_LEVEL_SLOT -> {
                player.closeInventory();
                bankManager.upgradeBankLevel(player);
            }
            case BANK_TYPE_SLOT -> {
                openBankTypeSelector(player);
            }
            case CLOSE_SLOT -> player.closeInventory();
        }
    }

    /**
     * Gère les clics dans le menu d'investissement avec nouvelles fonctionnalités
     */
    public void handleInvestmentMenuClick(Player player, int slot, ItemStack clicked, ClickType clickType) {

        boolean rightClick = clickType.isRightClick();
        boolean shift = clickType.isShiftClick();
        // Boutons de navigation
        if (slot == 45) { // Retour
            openMainMenu(player);
            return;
        }
        if (slot == 53) { // Fermer
            player.closeInventory();
            return;
        }

        // Clics sur les blocs d'investissement
        if (Arrays.stream(INVESTMENT_SLOTS).anyMatch(s -> s == slot)) {
            String materialName = clicked.getItemMeta().getPersistentDataContainer()
                    .get(new org.bukkit.NamespacedKey(plugin, "investment_material"), PersistentDataType.STRING);
            if (materialName == null) return;

            Material material;
            try {
                Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                return;
            }

            if (rightClick) {
                if (shift) {
                    // Vente rapide - tout vendre
                    player.closeInventory();
                    player.performCommand("bank invest sell " + materialName.toLowerCase() + " all");
                } else {
                    // Achat rapide avec option levier pour commerçants niveau 5+
                    player.closeInventory();
                    if (bankManager.isTraderLevel5Plus(player)) {
                        // Proposer le choix via chat
                        player.sendMessage("§6⚡ Achat rapide - Choix du levier:");
                        player.sendMessage("§e• /bank invest buy " + materialName.toLowerCase() + " 10 §7- Achat normal");
                        player.sendMessage("§e• /bank invest buy " + materialName.toLowerCase() + " 10 levier §7- Avec levier x2");
                    } else {
                        player.performCommand("bank invest buy " + materialName.toLowerCase() + " 10");
                    }
                }
            } else {
                // Informations détaillées
                player.closeInventory();
                player.performCommand("bank invest info " + materialName.toLowerCase());
            }
        }
    }

    public void handleBankTypeSelectionClick(Player player, int slot, ItemStack clicked) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        if (!bankManager.canUseSavings(player)) {
            player.sendMessage("§c❌ Le système bancaire nécessite le rang F+ pour choisir une banque !");
            player.closeInventory();
            return;
        }
        if (slot == 22) {
            openMainMenu(player);
        }

        String typeName = clicked.getItemMeta().getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "bank_type"), PersistentDataType.STRING);
        if (typeName == null) return;
        fr.prisontycoon.data.BankType selected;
        try {
            selected = fr.prisontycoon.data.BankType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data.getBankType() == selected) {
            player.sendMessage("§eCette banque est déjà sélectionnée.");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
            return;
        }
        data.setBankType(selected);
        data.setLastBankTypeChange(System.currentTimeMillis());
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        player.sendMessage("§a✅ Type de banque défini: §e" + selected.getDisplayName());
        openMainMenu(player);
    }
}