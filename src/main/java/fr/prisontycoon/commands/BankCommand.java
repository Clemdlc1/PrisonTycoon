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
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
 */
public class BankCommand implements CommandExecutor, TabCompleter {

    // Matériaux d'investissement pour l'autocomplétion
    private static final List<String> INVESTMENT_MATERIALS = Arrays.asList(
            "cobblestone", "stone", "coal", "iron", "cuivre", "gold", "diamond", "emerauld", "beacon"
    );
    private final PrisonTycoon plugin;
    private final BankManager bankManager;

    public BankCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.bankManager = plugin.getBankManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
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
            }
        }
    }

    /**
     * Affiche les informations détaillées d'un bloc avec différenciation par métier
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

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean isTrader3Plus = bankManager.isTraderLevel3Plus(player);
        boolean isTrader5Plus = bankManager.isTraderLevel5Plus(player);

        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§e📊 " + bankManager.getBlockDisplayName(material));
        player.sendMessage("§6═══════════════════════════════");

        // Informations de base (tous les joueurs)
        player.sendMessage("§7Valeur actuelle: §a" + NumberFormatter.format((long) block.currentValue) + " coins");
        player.sendMessage("§7Investisseurs totaux: §b" + NumberFormatter.format(block.totalInvestments));

        long playerInvestment = playerData.getInvestmentQuantity(material);
        if (playerInvestment > 0) {
            long totalValue = (long) (block.currentValue * playerInvestment);
            player.sendMessage("§7Votre investissement: §6" + NumberFormatter.format(playerInvestment) + " unités");
            player.sendMessage("§7Valeur de votre portefeuille: §a" + NumberFormatter.format(totalValue) + " coins");
        }

        if (isTrader3Plus) {
            player.sendMessage("");
            player.sendMessage("§6⚡ Informations Commerçant:");
            player.sendMessage("§7Valeur de base: §e" + NumberFormatter.format((long) block.baseValue) + " coins");
            player.sendMessage("§7Volatilité: §c" + String.format("%.1f%%", block.volatility * 100));

            // Évolution en temps réel sur les dernières valeurs
            List<BankManager.InvestmentHistory> history = bankManager.getInvestmentHistory(material);
            if (history.size() >= 2) {
                BankManager.InvestmentHistory current = history.getLast();
                BankManager.InvestmentHistory previous = history.get(history.size() - 2);

                double change = ((current.value - previous.value) / previous.value) * 100;
                String changeColor = change > 0 ? "§a+" : "§c";
                player.sendMessage("§7Évolution (Temps réel): " + changeColor + String.format("%.2f%%", change));

                // Tendance sur les 10 dernières valeurs
                if (history.size() >= 10) {
                    double trend = calculateTrend(history.subList(history.size() - 10, history.size()));
                    String trendText = trend > 0.02 ? "§aHaussière" : trend < -0.02 ? "§cBaissière" : "§eStable";
                    player.sendMessage("§7Tendance (10 pts): " + trendText);

                    // Affichage du graphique textuel pour les commerçants
                    if (history.size() >= 5) {
                        StringBuilder graph = new StringBuilder("§7Graphique: ");
                        List<BankManager.InvestmentHistory> last5 = history.subList(history.size() - 5, history.size());
                        double minVal = last5.stream().mapToDouble(h -> h.value).min().orElse(0);
                        double maxVal = last5.stream().mapToDouble(h -> h.value).max().orElse(1);

                        for (BankManager.InvestmentHistory h : last5) {
                            double normalized = (h.value - minVal) / (maxVal - minVal);
                            if (normalized > 0.8) graph.append("§a▲");
                            else if (normalized > 0.6) graph.append("§e▲");
                            else if (normalized > 0.4) graph.append("§e■");
                            else if (normalized > 0.2) graph.append("§c▼");
                            else graph.append("§c▼");
                        }

                        player.sendMessage(graph.toString());
                    }
                }
            }
        }

        if (isTrader5Plus) {
            player.sendMessage("§6⚡ Bonus Levier x2 disponible !");
            player.sendMessage("§7Utilisez: §e/bank invest buy " + args[2] + " <quantité> levier");
        }

        // Conseils selon le métier
        if (!isTrader3Plus) {
            player.sendMessage("");
            player.sendMessage("§7§o💡 Conseil: Devenez Commerçant niveau 3+ pour");
            player.sendMessage("§7§o   des analyses détaillées et graphiques !");
        }

        player.sendMessage("§6═══════════════════════════════");
    }

    /**
     * Calcule la tendance d'une série de valeurs
     */
    private double calculateTrend(List<BankManager.InvestmentHistory> history) {
        if (history.size() < 2) return 0;

        double firstValue = history.getFirst().value;
        double lastValue = history.getLast().value;

        return (lastValue - firstValue) / firstValue;
    }

    /**
     * Gère l'achat d'investissements avec levier optionnel
     */
    private void handleInvestBuy(Player player, String[] args) {
        if (args.length < 4 || args.length > 5) {
            player.sendMessage("§c❌ Usage: /bank invest buy <bloc> <quantité> [levier]");
            player.sendMessage("§7Exemple: §e/bank invest buy gold 100 levier");
            return;
        }

        Material material = parseMaterial(args[2]);
        if (material == null) {
            player.sendMessage("§c❌ Bloc d'investissement invalide: " + args[2]);
            showAvailableBlocks(player);
            return;
        }

        try {
            long quantity = parseAmount(args[3], player);
            if (quantity <= 0) {
                player.sendMessage("§c❌ La quantité doit être positive!");
                return;
            }

            boolean useLevier = args.length == 5 && args[4].equalsIgnoreCase("levier");

            bankManager.buyInvestment(player, material, quantity, useLevier);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Quantité invalide: " + args[3]);
        }
    }

    /**
     * Gère la vente d'investissements avec support pour "all"
     */
    private void handleInvestSell(Player player, String[] args) {
        if (args.length != 4) {
            player.sendMessage("§c❌ Usage: /bank invest sell <bloc> <quantité|all>");
            player.sendMessage("§7Exemple: §e/bank invest sell gold all");
            return;
        }

        Material material = parseMaterial(args[2]);
        if (material == null) {
            player.sendMessage("§c❌ Bloc d'investissement invalide: " + args[2]);
            showAvailableBlocks(player);
            return;
        }

        try {
            long quantity;
            if (args[3].equalsIgnoreCase("all")) {
                quantity = -1; // -1 indique "all" au BankManager
            } else {
                quantity = parseAmount(args[3], player);
                if (quantity <= 0) {
                    player.sendMessage("§c❌ La quantité doit être positive!");
                    return;
                }
            }

            bankManager.sellInvestment(player, material, quantity);
        } catch (NumberFormatException e) {
            player.sendMessage("§c❌ Quantité invalide: " + args[3]);
        }
    }

    /**
     * Affiche les investissements du joueur avec informations différenciées
     */
    private void showPlayerInvestments(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Map<Material, Long> investments = playerData.getAllInvestments();
        boolean isTrader = bankManager.isTraderLevel3Plus(player);

        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§e💼 Votre Portefeuille d'Investissements");
        player.sendMessage("§6═══════════════════════════════");

        if (investments.isEmpty()) {
            player.sendMessage("§7Aucun investissement actuel.");
            player.sendMessage("§7Utilisez §e/bank invest buy <bloc> <quantité> [levier]§7 pour investir.");
            if (bankManager.isTraderLevel5Plus(player)) {
                player.sendMessage("§6⚡ Astuce: Ajoutez §elevier§6 pour doubler votre achat !");
            }
        } else {
            long totalValue = 0;

            for (Map.Entry<Material, Long> entry : investments.entrySet()) {
                Material material = entry.getKey();
                long quantity = entry.getValue();

                BankManager.InvestmentBlock block = bankManager.getInvestmentBlock(material);
                if (block != null) {
                    long value = (long) (block.currentValue * quantity);
                    totalValue += value;

                    String blockName = bankManager.getBlockDisplayName(material);

                    if (isTrader) {
                        // Informations détaillées pour les commerçants
                        List<BankManager.InvestmentHistory> history = bankManager.getInvestmentHistory(material);
                        String evolutionText = "";
                        if (history.size() >= 2) {
                            BankManager.InvestmentHistory current = history.getLast();
                            BankManager.InvestmentHistory previous = history.get(history.size() - 2);
                            double change = ((current.value - previous.value) / previous.value) * 100;
                            String changeColor = change > 0 ? "§a+" : "§c";
                            evolutionText = " " + changeColor + String.format("%.2f%%", change);
                        }

                        player.sendMessage("§7• " + blockName + ": §e" + NumberFormatter.format(quantity) + "x §7(§a" +
                                NumberFormatter.format(value) + " coins§7)" + evolutionText);
                    } else {
                        // Informations basiques pour les non-commerçants
                        player.sendMessage("§7• " + blockName + ": §e" + NumberFormatter.format(quantity) + "x §7(§a" +
                                NumberFormatter.format(value) + " coins§7)");
                    }
                }
            }

            player.sendMessage("§6─────────────────────────────");
            player.sendMessage("§7Valeur totale: §a" + NumberFormatter.format(totalValue) + " coins");

            if (!isTrader) {
                player.sendMessage("§7§o💡 Devenez Commerçant pour des infos détaillées !");
            }
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
     * Parse un montant avec support des raccourcis (k, m, b, t) et valeurs extrêmes
     */
    private long parseAmount(String amountStr, Player player) {
        if (amountStr.equalsIgnoreCase("all")) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            return playerData.getCoins();
        }

        String clean = amountStr.toLowerCase().replace(",", "").replace(".", "");

        long multiplier = 1;
        if (clean.endsWith("k")) {
            multiplier = 1_000L;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000L;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("b")) {
            multiplier = 1_000_000_000L;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("t")) {
            multiplier = 1_000_000_000_000L; // Support pour les trillions
            clean = clean.substring(0, clean.length() - 1);
        }

        long baseAmount = Long.parseLong(clean);

        // Protection contre les débordements
        if (baseAmount > Long.MAX_VALUE / multiplier) {
            throw new NumberFormatException("Montant trop élevé");
        }

        return baseAmount * multiplier;
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
     * Envoie le message d'aide avec nouvelles syntaxes
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
        player.sendMessage("§e/bank invest buy <bloc> <qté> [levier] §7- Achète des investissements");
        player.sendMessage("§e/bank invest sell <bloc> <qté|all> §7- Vend des investissements");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getPrestigeLevel() >= 10) {
            player.sendMessage("§e/bank safe deposit <montant> §7- Dépose dans le coffre-fort");
            player.sendMessage("§e/bank safe withdraw <montant> §7- Retire du coffre-fort");
        }

        player.sendMessage("§e/bank improve §7- Améliore le niveau bancaire");
        player.sendMessage("§6═══════════════════════════════");
        player.sendMessage("§7Montants acceptés: §e1000, 1k, 1.5m, 2b, 3t, all");

        if (bankManager.isTraderLevel5Plus(player)) {
            player.sendMessage("§6⚡ Bonus Commerçant: Ajoutez §elevier§6 pour doubler vos achats !");
        } else if (bankManager.isTraderLevel3Plus(player)) {
            player.sendMessage("§6⚡ Bonus Commerçant: Informations détaillées disponibles !");
        } else {
            player.sendMessage("§7💡 Conseil: Devenez Commerçant pour des bonus d'investissement !");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "deposit", "withdraw", "invest", "safe", "improve", "help"
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
            }
        } else if (args.length == 3) {
            if ("invest".equalsIgnoreCase(args[0])) {
                if ("info".equalsIgnoreCase(args[1]) || "buy".equalsIgnoreCase(args[1]) || "sell".equalsIgnoreCase(args[1])) {
                    StringUtil.copyPartialMatches(args[2], INVESTMENT_MATERIALS, completions);
                }
            }
        } else if (args.length == 4) {
            if ("invest".equalsIgnoreCase(args[0]) && ("buy".equalsIgnoreCase(args[1]) || "sell".equalsIgnoreCase(args[1]))) {
                List<String> quantities;
                if ("sell".equalsIgnoreCase(args[1])) {
                    quantities = Arrays.asList("1", "5", "10", "25", "50", "100", "all");
                } else {
                    quantities = Arrays.asList("1", "5", "10", "25", "50", "100", "1k", "10k");
                }
                StringUtil.copyPartialMatches(args[3], quantities, completions);
            }
        } else if (args.length == 5) {
            if ("invest".equalsIgnoreCase(args[0]) && "buy".equalsIgnoreCase(args[1])) {
                List<String> leverOptions = List.of("levier");
                StringUtil.copyPartialMatches(args[4], leverOptions, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}