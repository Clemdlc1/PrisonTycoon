package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du système bancaire
 * Gère les comptes d'épargne, investissements, coffres-forts et niveaux bancaires
 */
public class BankManager {

    private final PrisonTycoon plugin;
    private final File investmentDataFile;
    private FileConfiguration investmentConfig;

    // Configuration des investissements
    private final Map<Material, InvestmentBlock> investmentBlocks = new ConcurrentHashMap<>();
    private final Map<Material, List<InvestmentHistory>> investmentHistory = new ConcurrentHashMap<>();

    // Blocs disponibles pour investissement
    private static final Material[] INVESTMENT_MATERIALS = {
            Material.COBBLESTONE, Material.STONE, Material.COAL_ORE, Material.IRON_ORE,
            Material.COPPER_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.BEACON
    };

    public BankManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.investmentDataFile = new File(plugin.getDataFolder(), "investment_data.yml");

        initializeInvestmentBlocks();
        loadInvestmentData();
        startInvestmentFluctuation();
        startInterestTask();
        startSafeMaintenanceTask();
    }

    /**
     * Initialise les blocs d'investissement avec des valeurs de base
     */
    private void initializeInvestmentBlocks() {
        investmentBlocks.put(Material.COBBLESTONE, new InvestmentBlock(Material.COBBLESTONE, 10.0, 0, 0.15));
        investmentBlocks.put(Material.STONE, new InvestmentBlock(Material.STONE, 15.0, 0, 0.12));
        investmentBlocks.put(Material.COAL_ORE, new InvestmentBlock(Material.COAL_ORE, 25.0, 0, 0.10));
        investmentBlocks.put(Material.IRON_ORE, new InvestmentBlock(Material.IRON_ORE, 45.0, 0, 0.08));
        investmentBlocks.put(Material.COPPER_ORE, new InvestmentBlock(Material.COPPER_ORE, 35.0, 0, 0.09));
        investmentBlocks.put(Material.GOLD_ORE, new InvestmentBlock(Material.GOLD_ORE, 75.0, 0, 0.06));
        investmentBlocks.put(Material.DIAMOND_ORE, new InvestmentBlock(Material.DIAMOND_ORE, 150.0, 0, 0.05));
        investmentBlocks.put(Material.EMERALD_ORE, new InvestmentBlock(Material.EMERALD_ORE, 200.0, 0, 0.04));
        investmentBlocks.put(Material.BEACON, new InvestmentBlock(Material.BEACON, 500.0, 0, 0.03));
    }

    /**
     * Vérifie si un joueur peut accéder aux comptes d'épargne
     */
    public boolean canUseSavings(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.hasRank("F");
    }

    /**
     * Vérifie si un joueur a un compte d'épargne
     */
    public boolean hasSavings(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.getSavingsBalance() > 0;
    }

    /**
     * Dépose de l'argent dans le compte d'épargne
     */
    public boolean depositSavings(Player player, long amount) {
        if (!canUseSavings(player)) {
            player.sendMessage("§c❌ Les comptes d'épargne nécessitent le rang F+ !");
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getCoins() < amount) {
            player.sendMessage("§c❌ Solde insuffisant !");
            return false;
        }

        long currentSavings = playerData.getSavingsBalance();
        long maxSavings = getSavingsLimit(player);

        if (currentSavings + amount > maxSavings) {
            player.sendMessage("§c❌ Plafond d'épargne atteint ! Limite: " + NumberFormatter.format(maxSavings));
            return false;
        }

        playerData.removeCoins(amount);
        playerData.addSavingsBalance(amount);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ " + NumberFormatter.format(amount) + " déposés dans votre épargne");
        return true;
    }

    /**
     * Retire de l'argent du compte d'épargne
     */
    public boolean withdrawSavings(Player player, long amount) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getSavingsBalance() < amount) {
            player.sendMessage("§c❌ Solde d'épargne insuffisant !");
            return false;
        }

        playerData.removeSavingsBalance(amount);
        playerData.addCoins(amount);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ " + NumberFormatter.format(amount) + " retirés de votre épargne");
        return true;
    }

    /**
     * Obtient la limite du compte d'épargne selon le niveau bancaire
     */
    public long getSavingsLimit(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int bankLevel = playerData.getBankLevel();
        long baseLimitMillions = plugin.getConfig().getLong("bank.savings.base-limit-millions", 10);
        long baseLimit = baseLimitMillions * 1_000_000;

        // +20% par niveau bancaire
        return (long) (baseLimit * (1 + (bankLevel * 0.20)));
    }

    /**
     * Calcule les intérêts d'épargne
     */
    public void calculateSavingsInterest(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long savingsBalance = playerData.getSavingsBalance();

        if (savingsBalance <= 0) return;

        long lastInterest = playerData.getLastInterestTime();
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastInterest;

        // 5% toutes les 12h, 10% toutes les 2j, 20% toutes les 5j
        int bankLevel = playerData.getBankLevel();
        double interestRate = 0.05; // Base 5%
        long interestPeriod = 12 * 60 * 60 * 1000; // 12h en ms

        // Bonus selon niveau bancaire
        interestRate += bankLevel * 0.01; // +1% par niveau

        if (timeDiff >= interestPeriod) {
            long periods = timeDiff / interestPeriod;
            double totalInterest = savingsBalance * interestRate * periods;

            playerData.addSavingsBalance((long) totalInterest);
            playerData.setLastInterestTime(currentTime);
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());

            if (player.isOnline()) {
                player.sendMessage("§a💰 Intérêts d'épargne: +" + NumberFormatter.format((long) totalInterest));
            }
        }
    }

    /**
     * Achète des investissements
     */
    public boolean buyInvestment(Player player, Material material, int quantity) {
        if (!Arrays.asList(INVESTMENT_MATERIALS).contains(material)) {
            player.sendMessage("§c❌ Ce bloc n'est pas disponible à l'investissement !");
            return false;
        }

        InvestmentBlock block = investmentBlocks.get(material);
        if (block == null) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long totalCost = (long) (block.currentValue * quantity);

        // Bonus commerçant niveau 5+ : levier x2
        if (isTraderLevel5Plus(player)) {
            quantity *= 2;
            player.sendMessage("§6⚡ Bonus Commerçant Niv.5+ : Quantité doublée !");
        }

        if (playerData.getCoins() < totalCost) {
            player.sendMessage("§c❌ Solde insuffisant ! Coût: " + NumberFormatter.format(totalCost));
            return false;
        }

        playerData.removeCoins(totalCost);
        playerData.addInvestment(material, quantity);

        // Augmente la valeur du bloc selon l'investissement
        block.totalInvestments += quantity;
        updateBlockValue(material);

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        saveInvestmentData();

        player.sendMessage("§a✅ Investi " + quantity + "x " + getBlockDisplayName(material));
        player.sendMessage("§7Coût total: " + NumberFormatter.format(totalCost));
        return true;
    }

    /**
     * Vend des investissements
     */
    public boolean sellInvestment(Player player, Material material, int quantity) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getInvestmentQuantity(material) < quantity) {
            player.sendMessage("§c❌ Quantité insuffisante en investissement !");
            return false;
        }

        InvestmentBlock block = investmentBlocks.get(material);
        if (block == null) return false;

        long totalValue = (long) (block.currentValue * quantity);

        playerData.addCoins(totalValue);
        playerData.removeInvestment(material, quantity);

        // Diminue la valeur du bloc
        block.totalInvestments = Math.max(0, block.totalInvestments - quantity);
        updateBlockValue(material);

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        saveInvestmentData();

        player.sendMessage("§a✅ Vendu " + quantity + "x " + getBlockDisplayName(material));
        player.sendMessage("§7Valeur totale: " + NumberFormatter.format(totalValue));
        return true;
    }

    /**
     * Vérifie si le joueur est commerçant niveau 5+
     */
    public boolean isTraderLevel5Plus(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return "commercant".equals(playerData.getActiveProfession()) &&
                playerData.getProfessionLevel("commercant") >= 5;
    }

    /**
     * Vérifie si le joueur est commerçant niveau 3+
     */
    public boolean isTraderLevel3Plus(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return "commercant".equals(playerData.getActiveProfession()) &&
                playerData.getProfessionLevel("commercant") >= 3;
    }

    /**
     * Met à jour la valeur d'un bloc selon les investissements
     */
    private void updateBlockValue(Material material) {
        InvestmentBlock block = investmentBlocks.get(material);
        if (block == null) return;

        // Plus d'investissements = valeur plus stable, moins = plus volatile
        double stabilityFactor = Math.min(1.0, block.totalInvestments / 1000.0);
        block.volatility = block.baseVolatility * (1 - stabilityFactor * 0.5);

        // Valeur augmente avec les investissements
        double popularityBonus = 1 + (block.totalInvestments * 0.001);
        block.currentValue = block.baseValue * popularityBonus;
    }

    /**
     * Démarrage de la fluctuation des investissements
     */
    private void startInvestmentFluctuation() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Material material : INVESTMENT_MATERIALS) {
                    fluctuateBlockValue(material);
                }
                saveInvestmentData();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 5); // Toutes les 5 minutes
    }

    /**
     * Fait fluctuer la valeur d'un bloc
     */
    private void fluctuateBlockValue(Material material) {
        InvestmentBlock block = investmentBlocks.get(material);
        if (block == null) return;

        Random random = new Random();
        double change = (random.nextGaussian() * block.volatility);
        double newValue = block.currentValue * (1 + change);

        // Évite que la valeur descende trop bas ou monte trop haut
        newValue = Math.max(block.baseValue * 0.1, Math.min(block.baseValue * 10, newValue));

        block.currentValue = newValue;

        // Enregistre l'historique
        InvestmentHistory history = new InvestmentHistory(System.currentTimeMillis(), newValue);
        investmentHistory.computeIfAbsent(material, k -> new ArrayList<>()).add(history);

        // Garde seulement les 100 dernières entrées
        List<InvestmentHistory> hist = investmentHistory.get(material);
        if (hist.size() > 100) {
            hist.remove(0);
        }
    }

    /**
     * Démarrage de la tâche d'intérêts
     */
    private void startInterestTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    calculateSavingsInterest(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 30); // Toutes les 30 minutes
    }

    /**
     * Démarrage de la tâche de maintenance du coffre-fort
     */
    private void startSafeMaintenanceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    chargeSafeMaintenance(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 60 * 12); // Toutes les 12h
    }

    /**
     * Prélève les frais de gestion du coffre-fort
     */
    private void chargeSafeMaintenance(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long safeBalance = playerData.getSafeBalance();

        if (safeBalance <= 0) return;

        long maintenanceFee = (long) (safeBalance * 0.10); // 10%
        playerData.removeSafeBalance(maintenanceFee);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        if (player.isOnline()) {
            player.sendMessage("§c💳 Frais de gestion coffre-fort: -" + NumberFormatter.format(maintenanceFee));
        }
    }

    /**
     * Dépose dans le coffre-fort (Prestige 10+ uniquement)
     */
    public boolean depositSafe(Player player, long amount) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getPrestigeLevel() < 10) {
            player.sendMessage("§c❌ Le coffre-fort nécessite Prestige 10+ !");
            return false;
        }

        if (playerData.getCoins() < amount) {
            player.sendMessage("§c❌ Solde insuffisant !");
            return false;
        }

        long currentSafe = playerData.getSafeBalance();
        long maxSafe = getSafeLimit(player);

        if (currentSafe + amount > maxSafe) {
            player.sendMessage("§c❌ Limite du coffre-fort atteinte ! Limite: " + NumberFormatter.format(maxSafe));
            return false;
        }

        playerData.removeCoins(amount);
        playerData.addSafeBalance(amount);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ " + NumberFormatter.format(amount) + " déposés dans le coffre-fort");
        return true;
    }

    /**
     * Retire du coffre-fort avec frais
     */
    public boolean withdrawSafe(Player player, long amount) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getSafeBalance() < amount) {
            player.sendMessage("§c❌ Solde du coffre-fort insuffisant !");
            return false;
        }

        long withdrawalFee = (long) (amount * 0.20); // 20% de frais
        long netAmount = amount - withdrawalFee;

        playerData.removeSafeBalance(amount);
        playerData.addCoins(netAmount);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✅ " + NumberFormatter.format(netAmount) + " retirés du coffre-fort");
        player.sendMessage("§7Frais de retrait (20%): " + NumberFormatter.format(withdrawalFee));
        return true;
    }

    /**
     * Obtient la limite du coffre-fort
     */
    public long getSafeLimit(Player player) {
        return 1_000_000_000L; // 1 milliard de base
    }

    /**
     * Améliore le niveau bancaire
     */
    public boolean upgradeBankLevel(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = playerData.getBankLevel();

        if (currentLevel >= 10) {
            player.sendMessage("§c❌ Niveau bancaire maximum atteint !");
            return false;
        }

        long requiredDeposits = getBankLevelRequiredDeposits(currentLevel + 1);
        long totalDeposits = playerData.getTotalBankDeposits();

        if (totalDeposits < requiredDeposits) {
            player.sendMessage("§c❌ Dépôts cumulés insuffisants !");
            player.sendMessage("§7Requis: " + NumberFormatter.format(requiredDeposits));
            player.sendMessage("§7Actuel: " + NumberFormatter.format(totalDeposits));
            return false;
        }

        long experienceCost = getBankLevelExperienceCost(currentLevel + 1);
        if (playerData.getExperience() < experienceCost) {
            player.sendMessage("§c❌ Expérience insuffisante !");
            player.sendMessage("§7Requis: " + NumberFormatter.format(experienceCost) + " XP");
            return false;
        }

        playerData.removeExperience(experienceCost);
        playerData.setBankLevel(currentLevel + 1);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✨ Niveau bancaire amélioré : " + (currentLevel + 1));
        return true;
    }

    /**
     * Obtient les dépôts requis pour un niveau bancaire
     */
    public long getBankLevelRequiredDeposits(int level) {
        return (long) (1_000_000 * Math.pow(5, level - 1)); // 1M, 5M, 25M, etc.
    }

    /**
     * Obtient le coût en XP pour un niveau bancaire
     */
    public long getBankLevelExperienceCost(int level) {
        return (long) (10_000 * Math.pow(3, level - 1)); // 10k, 30k, 90k, etc.
    }

    /**
     * Transfert d'argent entre joueurs
     */
    public boolean transferMoney(Player sender, String targetName, long amount) {
        Player target = plugin.getServer().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage("§c❌ Joueur introuvable ou hors ligne !");
            return false;
        }

        PlayerData senderData = plugin.getPlayerDataManager().getPlayerData(sender.getUniqueId());
        PlayerData targetData = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());

        if (senderData.getCoins() < amount) {
            sender.sendMessage("§c❌ Solde insuffisant !");
            return false;
        }

        // Frais de transfert : 2%
        long transferFee = (long) (amount * 0.02);
        long netAmount = amount - transferFee;

        senderData.removeCoins(amount);
        targetData.addCoins(netAmount);

        plugin.getPlayerDataManager().markDirty(sender.getUniqueId());
        plugin.getPlayerDataManager().markDirty(target.getUniqueId());

        sender.sendMessage("§a✅ " + NumberFormatter.format(netAmount) + " transférés à " + target.getName());
        sender.sendMessage("§7Frais de transfert: " + NumberFormatter.format(transferFee));

        target.sendMessage("§a📥 " + NumberFormatter.format(netAmount) + " reçus de " + sender.getName());
        return true;
    }

    /**
     * Obtient les informations d'un bloc d'investissement
     */
    public InvestmentBlock getInvestmentBlock(Material material) {
        return investmentBlocks.get(material);
    }

    /**
     * Obtient l'historique d'un bloc
     */
    public List<InvestmentHistory> getInvestmentHistory(Material material) {
        return investmentHistory.getOrDefault(material, new ArrayList<>());
    }

    /**
     * Obtient le nom d'affichage d'un bloc
     */
    public String getBlockDisplayName(Material material) {
        return switch (material) {
            case COBBLESTONE -> "Cobblestone";
            case STONE -> "Pierre";
            case COAL_ORE -> "Charbon";
            case IRON_ORE -> "Fer";
            case COPPER_ORE -> "Cuivre";
            case GOLD_ORE -> "Or";
            case DIAMOND_ORE -> "Diamant";
            case EMERALD_ORE -> "Émeraude";
            case BEACON -> "Beacon";
            default -> material.name();
        };
    }

    /**
     * Charge les données d'investissement
     */
    private void loadInvestmentData() {
        if (!investmentDataFile.exists()) {
            saveInvestmentData();
            return;
        }

        try {
            investmentConfig = YamlConfiguration.loadConfiguration(investmentDataFile);

            for (Material material : INVESTMENT_MATERIALS) {
                String path = material.name().toLowerCase();
                if (investmentConfig.contains(path)) {
                    double currentValue = investmentConfig.getDouble(path + ".current-value");
                    int totalInvestments = investmentConfig.getInt(path + ".total-investments");

                    InvestmentBlock block = investmentBlocks.get(material);
                    if (block != null) {
                        block.currentValue = currentValue;
                        block.totalInvestments = totalInvestments;
                    }
                }

                // Charge l'historique
                if (investmentConfig.contains(path + ".history")) {
                    List<Map<?, ?>> historyList = investmentConfig.getMapList(path + ".history");
                    List<InvestmentHistory> history = new ArrayList<>();

                    for (Map<?, ?> entry : historyList) {
                        long timestamp = (Long) entry.get("timestamp");
                        double value = (Double) entry.get("value");
                        history.add(new InvestmentHistory(timestamp, value));
                    }

                    investmentHistory.put(material, history);
                }
            }

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur lors du chargement des données d'investissement: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde les données d'investissement
     */
    private void saveInvestmentData() {
        try {
            investmentConfig = new YamlConfiguration();

            for (Material material : INVESTMENT_MATERIALS) {
                InvestmentBlock block = investmentBlocks.get(material);
                String path = material.name().toLowerCase();

                investmentConfig.set(path + ".current-value", block.currentValue);
                investmentConfig.set(path + ".total-investments", block.totalInvestments);
                investmentConfig.set(path + ".base-value", block.baseValue);
                investmentConfig.set(path + ".volatility", block.volatility);

                // Sauvegarde l'historique
                List<InvestmentHistory> history = investmentHistory.get(material);
                if (history != null && !history.isEmpty()) {
                    List<Map<String, Object>> historyList = new ArrayList<>();
                    for (InvestmentHistory entry : history) {
                        Map<String, Object> historyEntry = new HashMap<>();
                        historyEntry.put("timestamp", entry.timestamp);
                        historyEntry.put("value", entry.value);
                        historyList.add(historyEntry);
                    }
                    investmentConfig.set(path + ".history", historyList);
                }
            }

            investmentConfig.save(investmentDataFile);

        } catch (IOException e) {
            plugin.getPluginLogger().severe("Erreur lors de la sauvegarde des données d'investissement: " + e.getMessage());
        }
    }

    // Classes internes
    public static class InvestmentBlock {
        public Material material;
        public double baseValue;
        public double currentValue;
        public int totalInvestments;
        public double volatility;
        public double baseVolatility;

        public InvestmentBlock(Material material, double baseValue, int totalInvestments, double baseVolatility) {
            this.material = material;
            this.baseValue = baseValue;
            this.currentValue = baseValue;
            this.totalInvestments = totalInvestments;
            this.baseVolatility = baseVolatility;
            this.volatility = baseVolatility;
        }
    }

    public static class InvestmentHistory {
        public long timestamp;
        public double value;

        public InvestmentHistory(long timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }
}