package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.BankManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Commande /bank - Système bancaire complet
 * Sous-commandes:
 * - /bank : ouvre le menu principal
 * - /bank deposit <montant> : dépose dans l'épargne
 * - /bank withdraw <montant> : retire de l'épargne
 * - /bank invest : gère les investissements
 * - /bank invest info <bloc> : info détaillée sur un bloc
 * - /bank invest buy <bloc> <quantité> : achète des investissements
 * - /bank invest sell <bloc> <quantité> : vend des investissements
 * - /bank safe deposit <montant> : dépose dans le coffre-fort
 * - /bank safe withdraw <montant> : retire du coffre-fort
 * - /bank improve : améliore le niveau bancaire
 * - /bank transfer <joueur> <montant> : transfert d'argent
 */
public class BankCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;
    private final BankManager bankManager;

    // Matériaux d'investissement pour l'autocomplétion
    private static final List<String> INVESTMENT_MATERIALS = Arrays.asList(
            "cobblestone", "stone", "coal", "iron", "cuivre", "gold", "diamond", "emerauld", "beacon"
    );

    public BankCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.bankManager = plugin.getBankManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c❌ Cette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Menu principal si aucun argument
        if (args.length == 0) {
            plugin.getBankGUI().openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "deposit" -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            case "invest" -> handleInvest(player, args);
            case "safe" -> handleSafe(player, args);
            case "improve" -> handleImprove(player);
            case "transfer" -> handleTransfer(player, args);
            case "help" -> sendHelpMessage(player);
            default -> {
                player.sendMessage("§c❌ Sous-commande inconnue. Utilisez /bank help");
                return true;
            }
        }

        return true;
    }

    /**
     * Gère les dépôts d'épargne
     */
    private void handleDeposit(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§c❌ Usage: /bank deposit <montant>");
            return;
        }

        try {
            long amount = parseAmount(args[1], player);
            if (amount <= 0) {
                player.sendMessage("§c❌ Le montant doit être positif!");
                return;
            }

            bankManager.depositSavings(player, amount);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Montant invalide: " + args[1]);
        }
    }

    /**
     * Gère les retraits d'épargne
     */
    private void handleWithdraw(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§c❌ Usage: /bank withdraw <montant>");
            return;
        }

        try {
            long amount = parseAmount(args[1], player);
            if (amount <= 0) {
                player.sendMessage("§c❌ Le montant doit être positif!");
                return;
            }

            bankManager.withdrawSavings(player, amount);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Montant invalide: " + args[1]);
        }
    }

    /**
     * Gère les investissements
     */
    private void handleInvest(Player player, String[] args) {
        if (args.length == 1) {
            // Affiche les investissements du joueur
            showPlayerInvestments(player);
            return;
        }

        String investAction = args[1].toLowerCase();

        switch (investAction) {
            case "info" -> handleInvestInfo(player, args);
            case "buy" -> handleInvestBuy(player, args);
            case "sell" -> handleInvestSell(player, args);
            default -> {
                player.sendMessage("§c❌ Usage: /bank invest [info|buy|sell]");
                return;
            }
        }
    }

    /**
     * Affiche les informations détaillées d'un bloc d'investissement
     */
    private void handleInvestInfo(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§c❌ Usage: /bank invest info <bloc>");
            return;
        }

        Material material = parseMaterial(args[2]);
        if (material == null) {
            player.sendMessage("§c❌ Bloc d'investissement invalide: " + args[2]);
            showAvailableBlocks(player);
            return;
        }

        BankManager.InvestmentBlock block = bankManager.getInvestmentBlock(material);
        if (block == null) {
            player.sendMessage("§c❌ Ce bloc n'est pas disponible à l'investissement!");
            return;
        }

        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§e📊 " + bankManager.getBlockDisplayName(material));
        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§7Valeur actuelle: §a" + NumberFormatter.format((long) block.currentValue) + " coins");
        player.sendMessage("§7Valeur de base: §e" + NumberFormatter.format((long) block.baseValue) + " coins");
        player.sendMessage("§7Investisseurs totaux: §b" + NumberFormatter.format(block.totalInvestments));
        player.sendMessage("§7Volatilité: §c" + String.format("%.1f%%", block.volatility * 100));

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int playerInvestment = playerData.getInvestmentQuantity(material);
        if (playerInvestment > 0) {
            long totalValue = (long) (block.currentValue * playerInvestment);
            player.sendMessage("§7Votre investissement: §6" + playerInvestment + " unités");
            player.sendMessage("§7Valeur de votre portefeuille: §a" + NumberFormatter.format(totalValue) + " coins");
        }

        // Informations détaillées pour les commerçants niveau 3+
        if (bankManager.isTraderLevel3Plus(player)) {
            player.sendMessage("");
            player.sendMessage("§6⚡ Informations Commerçant:");

            List<BankManager.InvestmentHistory> history = bankManager.getInvestmentHistory(material);
            if (history.size() >= 2) {
                BankManager.InvestmentHistory current = history.get(history.size() - 1);
                BankManager.InvestmentHistory previous = history.get(history.size() - 2);

                double change = ((current.value - previous.value) / previous.value) * 100;
                String changeColor = change > 0 ? "§a+" : "§c";
                player.sendMessage("§7Évolution récente: " + changeColor + String.format("%.2f%%", change));

                // Tendance sur les 10 dernières valeurs
                if (history.size() >= 10) {
                    double trend = calculateTrend(history.subList(history.size() - 10, history.size()));
                    String trendText = trend > 0.02 ? "§aHaussière" : trend < -0.02 ? "§cBaissière" : "§eStable";
                    player.sendMessage("§7Tendance (10 pts): " + trendText);
                }
            }
        }

        player.sendMessage("§6═══════════════════════════════");
    }

    /**
     * Calcule la tendance d'une série de valeurs
     */
    private double calculateTrend(List<BankManager.InvestmentHistory> history) {
        if (history.size() < 2) return 0;

        double firstValue = history.get(0).value;
        double lastValue = history.get(history.size() - 1).value;

        return (lastValue - firstValue) / firstValue;
    }

    /**
     * Gère l'achat d'investissements
     */
    private void handleInvestBuy(Player player, String[] args) {
        if (args.length != 4) {
            player.sendMessage("§c❌ Usage: /bank invest buy <bloc> <quantité>");
            return;
        }

        Material material = parseMaterial(args[2]);
        if (material == null) {
            player.sendMessage("§c❌ Bloc d'investissement invalide: " + args[2]);
            showAvailableBlocks(player);
            return;
        }

        try {
            int quantity = Integer.parseInt(args[3]);
            if (quantity <= 0) {
                player.sendMessage("§c❌ La quantité doit être positive!");
                return;
            }

            bankManager.buyInvestment(player, material, quantity);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Quantité invalide: " + args[3]);
        }
    }

    /**
     * Gère la vente d'investissements
     */
    private void handleInvestSell(Player player, String[] args) {
        if (args.length != 4) {
            player.sendMessage("§c❌ Usage: /bank invest sell <bloc> <quantité>");
            return;
        }

        Material material = parseMaterial(args[2]);
        if (material == null) {
            player.sendMessage("§c❌ Bloc d'investissement invalide: " + args[2]);
            showAvailableBlocks(player);
            return;
        }

        try {
            int quantity = Integer.parseInt(args[3]);
            if (quantity <= 0) {
                player.sendMessage("§c❌ La quantité doit être positive!");
                return;
            }

            bankManager.sellInvestment(player, material, quantity);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Quantité invalide: " + args[3]);
        }
    }

    /**
     * Affiche les investissements du joueur
     */
    private void showPlayerInvestments(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Map<Material, Integer> investments = playerData.getAllInvestments();

        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§e💼 Votre Portefeuille d'Investissements");
        player.sendMessage("§6═══════════════════════════════");

        if (investments.isEmpty()) {
            player.sendMessage("§7Aucun investissement actuel.");
            player.sendMessage("§7Utilisez §e/bank invest buy <bloc> <quantité>§7 pour investir.");
        } else {
            long totalValue = 0;

            for (Map.Entry<Material, Integer> entry : investments.entrySet()) {
                Material material = entry.getKey();
                int quantity = entry.getValue();

                BankManager.InvestmentBlock block = bankManager.getInvestmentBlock(material);
                if (block != null) {
                    long value = (long) (block.currentValue * quantity);
                    totalValue += value;

                    String blockName = bankManager.getBlockDisplayName(material);
                    player.sendMessage("§7• " + blockName + ": §e" + quantity + "x §7(§a" +
                            NumberFormatter.format(value) + " coins§7)");
                }
            }

            player.sendMessage("§6─────────────────────────────");
            player.sendMessage("§7Valeur totale: §a" + NumberFormatter.format(totalValue) + " coins");
        }

        player.sendMessage("§6═══════════════════════════════");
    }

    /**
     * Gère le coffre-fort
     */
    private void handleSafe(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /bank safe [deposit|withdraw] <montant>");
            return;
        }

        String safeAction = args[1].toLowerCase();

        switch (safeAction) {
            case "deposit" -> {
                if (args.length != 3) {
                    player.sendMessage("§c❌ Usage: /bank safe deposit <montant>");
                    return;
                }

                try {
                    long amount = parseAmount(args[2], player);
                    if (amount <= 0) {
                        player.sendMessage("§c❌ Le montant doit être positif!");
                        return;
                    }

                    bankManager.depositSafe(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c❌ Montant invalide: " + args[2]);
                }
            }
            case "withdraw" -> {
                if (args.length != 3) {
                    player.sendMessage("§c❌ Usage: /bank safe withdraw <montant>");
                    return;
                }

                try {
                    long amount = parseAmount(args[2], player);
                    if (amount <= 0) {
                        player.sendMessage("§c❌ Le montant doit être positif!");
                        return;
                    }

                    bankManager.withdrawSafe(player, amount);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c❌ Montant invalide: " + args[2]);
                }
            }
            default -> player.sendMessage("§c❌ Usage: /bank safe [deposit|withdraw] <montant>");
        }
    }

    /**
     * Gère l'amélioration du niveau bancaire
     */
    private void handleImprove(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getBankLevel();

        if (currentLevel >= 10) {
            player.sendMessage("§c❌ Niveau bancaire maximum déjà atteint!");
            return;
        }

        int nextLevel = currentLevel + 1;
        long requiredDeposits = bankManager.getBankLevelRequiredDeposits(nextLevel);
        long experienceCost = bankManager.getBankLevelExperienceCost(nextLevel);
        long totalDeposits = playerData.getTotalBankDeposits();

        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§e⭐ Amélioration Niveau Bancaire");
        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§7Niveau actuel: §e" + currentLevel);
        player.sendMessage("§7Niveau suivant: §a" + nextLevel);
        player.sendMessage("");
        player.sendMessage("§7Dépôts requis: §e" + NumberFormatter.format(requiredDeposits));
        player.sendMessage("§7Vos dépôts: " + (totalDeposits >= requiredDeposits ? "§a" : "§c") +
                NumberFormatter.format(totalDeposits));
        player.sendMessage("§7Coût en XP: §b" + NumberFormatter.format(experienceCost));
        player.sendMessage("§7Votre XP: " + (playerData.getExperience() >= experienceCost ? "§a" : "§c") +
                NumberFormatter.format(playerData.getExperience()));
        player.sendMessage("");

        if (totalDeposits >= requiredDeposits && playerData.getExperience() >= experienceCost) {
            player.sendMessage("§a✅ Conditions remplies! Confirmez l'amélioration avec §e/bank improve confirm");
        } else {
            player.sendMessage("§c❌ Conditions non remplies.");
        }

        player.sendMessage("§6═══════════════════════════════");
    }

    /**
     * Gère les transferts d'argent
     */
    private void handleTransfer(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage("§c❌ Usage: /bank transfer <joueur> <montant>");
            return;
        }

        String targetName = args[1];

        try {
            long amount = parseAmount(args[2], player);
            if (amount <= 0) {
                player.sendMessage("§c❌ Le montant doit être positif!");
                return;
            }

            if (amount < 1000) {
                player.sendMessage("§c❌ Montant minimum de transfert: 1,000 coins");
                return;
            }

            bankManager.transferMoney(player, targetName, amount);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Montant invalide: " + args[2]);
        }
    }

    /**
     * Parse un montant avec support des raccourcis (k, m, b)
     */
    private long parseAmount(String amountStr, Player player) {
        if (amountStr.equalsIgnoreCase("all")) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            return playerData.getCoins();
        }

        String clean = amountStr.toLowerCase().replace(",", "").replace(".", "");

        long multiplier = 1;
        if (clean.endsWith("k")) {
            multiplier = 1_000;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("b")) {
            multiplier = 1_000_000_000;
            clean = clean.substring(0, clean.length() - 1);
        }

        return Long.parseLong(clean) * multiplier;
    }

    /**
     * Parse un matériau à partir d'un nom
     */
    private Material parseMaterial(String materialName) {
        return switch (materialName.toLowerCase()) {
            case "cobblestone", "cobble" -> Material.COBBLESTONE;
            case "stone", "pierre" -> Material.STONE;
            case "coal", "charbon" -> Material.COAL_ORE;
            case "iron", "fer" -> Material.IRON_ORE;
            case "cuivre", "copper" -> Material.COPPER_ORE;
            case "gold", "or" -> Material.GOLD_ORE;
            case "diamond", "diamant" -> Material.DIAMOND_ORE;
            case "emerauld", "emerald", "emeraude" -> Material.EMERALD_ORE;
            case "beacon" -> Material.BEACON;
            default -> null;
        };
    }

    /**
     * Affiche les blocs disponibles pour l'investissement
     */
    private void showAvailableBlocks(Player player) {
        player.sendMessage("§7Blocs disponibles: §ecobblestone, stone, coal, iron, cuivre, gold, diamond, emerauld, beacon");
    }

    /**
     * Envoie le message d'aide
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§e🏦 Aide - Système Bancaire");
        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§e/bank §7- Ouvre le menu principal");
        player.sendMessage("§e/bank deposit <montant> §7- Dépose dans l'épargne");
        player.sendMessage("§e/bank withdraw <montant> §7- Retire de l'épargne");
        player.sendMessage("§e/bank invest §7- Affiche vos investissements");
        player.sendMessage("§e/bank invest info <bloc> §7- Informations sur un bloc");
        player.sendMessage("§e/bank invest buy <bloc> <qté> §7- Achète des investissements");
        player.sendMessage("§e/bank invest sell <bloc> <qté> §7- Vend des investissements");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getPrestigeLevel() >= 10) {
            player.sendMessage("§e/bank safe deposit <montant> §7- Dépose dans le coffre-fort");
            player.sendMessage("§e/bank safe withdraw <montant> §7- Retire du coffre-fort");
        }

        player.sendMessage("§e/bank improve §7- Améliore le niveau bancaire");
        player.sendMessage("§e/bank transfer <joueur> <montant> §7- Transfère de l'argent");
        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§7Montants acceptés: §e1000, 1k, 1.5m, all");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "deposit", "withdraw", "invest", "safe", "improve", "transfer", "help"
            );
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "invest" -> {
                    List<String> investCommands = Arrays.asList("info", "buy", "sell");
                    StringUtil.copyPartialMatches(args[1], investCommands, completions);
                }
                case "safe" -> {
                    List<String> safeCommands = Arrays.asList("deposit", "withdraw");
                    StringUtil.copyPartialMatches(args[1], safeCommands, completions);
                }
                case "deposit", "withdraw" -> {
                    List<String> amounts = Arrays.asList("1000", "5000", "10k", "50k", "100k", "1m", "all");
                    StringUtil.copyPartialMatches(args[1], amounts, completions);
                }
                case "transfer" -> {
                    // Suggère les joueurs en ligne
                    List<String> playerNames = plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[1], playerNames, completions);
                }
            }
        } else if (args.length == 3) {
            if ("invest".equals(args[0].toLowerCase())) {
                if ("info".equals(args[1].toLowerCase()) || "buy".equals(args[1].toLowerCase()) || "sell".equals(args[1].toLowerCase())) {
                    StringUtil.copyPartialMatches(args[2], INVESTMENT_MATERIALS, completions);
                }
            } else if ("transfer".equals(args[0].toLowerCase())) {
                List<String> amounts = Arrays.asList("1000", "5000", "10k", "50k", "100k", "1m");
                StringUtil.copyPartialMatches(args[2], amounts, completions);
            }
        } else if (args.length == 4) {
            if ("invest".equals(args[0].toLowerCase()) && ("buy".equals(args[1].toLowerCase()) || "sell".equals(args[1].toLowerCase()))) {
                List<String> quantities = Arrays.asList("1", "5", "10", "25", "50", "100");
                StringUtil.copyPartialMatches(args[3], quantities, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}