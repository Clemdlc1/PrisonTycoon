package fr.prisontycoon.managers;

import com.google.gson.Gson;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BankManager {

    private static final Material[] INVESTMENT_MATERIALS = {
            Material.COBBLESTONE, Material.STONE, Material.COAL_ORE, Material.IRON_ORE,
            Material.COPPER_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.BEACON
    };
    private final PrisonTycoon plugin;
    private final DatabaseManager databaseManager;
    private final Gson gson = new Gson();
    private final Map<Material, InvestmentBlock> investmentBlocks = new ConcurrentHashMap<>();
    private final Map<Material, List<InvestmentHistory>> investmentHistory = new ConcurrentHashMap<>();

    public BankManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        createTables();
        initializeInvestmentBlocks();
        loadInvestmentData();
        startInvestmentFluctuation();
        startInterestTask();
        startSafeMaintenanceTask();
    }

    private void createTables() {
        String investmentsTable = """
                    CREATE TABLE IF NOT EXISTS investments (
                        material VARCHAR(255) PRIMARY KEY,
                        current_value DOUBLE PRECISION,
                        total_investments BIGINT,
                        base_value DOUBLE PRECISION,
                        volatility DOUBLE PRECISION
                    );
                """;
        String historyTable = """
                    CREATE TABLE IF NOT EXISTS investment_history (
                        material VARCHAR(255),
                        timestamp BIGINT,
                        value DOUBLE PRECISION,
                        PRIMARY KEY (material, timestamp)
                    );
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps1 = conn.prepareStatement(investmentsTable);
             PreparedStatement ps2 = conn.prepareStatement(historyTable)) {
            ps1.execute();
            ps2.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create bank tables: " + e.getMessage());
        }
    }

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

    public boolean canUseSavings(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.hasRank("F");
    }

    public boolean hasSavings(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.getSavingsBalance() > 0 || canUseSavings(player);
    }

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

    public long getSavingsLimit(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int bankLevel = playerData.getBankLevel();
        long baseLimitMillions = plugin.getConfig().getLong("bank.savings.base-limit-millions", 10);
        long baseLimit = baseLimitMillions * 1_000_000;
        return (long) (baseLimit * (1 + (bankLevel * 0.20)));
    }

    public void calculateSavingsInterest(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long savingsBalance = playerData.getSavingsBalance();
        if (savingsBalance <= 0) return;
        long lastInterest = playerData.getLastInterestTime();
        long currentTime = System.currentTimeMillis();
        long timeDiff = currentTime - lastInterest;
        int bankLevel = playerData.getBankLevel();
        double interestRate = 0.05;
        long interestPeriod = 12 * 60 * 60 * 1000;
        interestRate += bankLevel * 0.01;
        // Applique le multiplicateur de type de banque
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data != null && data.getBankType() != null) {
            interestRate *= data.getBankType().getInterestGainMultiplier();
        }
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

    public boolean buyInvestment(Player player, Material material, long quantity, boolean useLevier) {
        if (!Arrays.asList(INVESTMENT_MATERIALS).contains(material)) {
            player.sendMessage("§c❌ Ce bloc n'est pas disponible à l'investissement !");
            return false;
        }
        InvestmentBlock block = investmentBlocks.get(material);
        if (block == null) return false;
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (quantity <= 0 || quantity > 1_000_000_000L) {
            player.sendMessage("§c❌ Quantité invalide ! (1 à 1,000,000,000)");
            return false;
        }
        long finalQuantity = quantity;
        if (useLevier && isTraderLevel5Plus(player)) {
            finalQuantity *= 2;
            player.sendMessage("§6⚡ Levier Commerçant x2 activé !");
        } else if (useLevier && !isTraderLevel5Plus(player)) {
            player.sendMessage("§c❌ Le levier nécessite Commerçant niveau 5+ !");
            return false;
        }
        double costPerUnit = block.currentValue;
        if (costPerUnit * finalQuantity > Long.MAX_VALUE) {
            player.sendMessage("§c❌ Montant trop élevé ! Réduisez la quantité.");
            return false;
        }
        long totalCost = (long) (costPerUnit * finalQuantity);
        if (playerData.getCoins() < totalCost) {
            player.sendMessage("§c❌ Solde insuffisant ! Coût: " + NumberFormatter.format(totalCost));
            return false;
        }
        playerData.removeCoins(totalCost);
        playerData.addInvestment(material, finalQuantity);
        if (block.totalInvestments + finalQuantity > 0) {
            block.totalInvestments += finalQuantity;
            updateBlockValue(material);
        }
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        saveInvestmentData();
        player.sendMessage("§a✅ Investi " + NumberFormatter.format(finalQuantity) + "x " + getBlockDisplayName(material));
        player.sendMessage("§7Coût total: " + NumberFormatter.format(totalCost));
        if (useLevier && isTraderLevel5Plus(player)) {
            player.sendMessage("§6⚡ Quantité avec levier: " + NumberFormatter.format(quantity) + " → " + NumberFormatter.format(finalQuantity));
        }
        return true;
    }

    public boolean sellInvestment(Player player, Material material, long quantity) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentInvestment = playerData.getInvestmentQuantity(material);
        if (currentInvestment <= 0) {
            player.sendMessage("§c❌ Vous n'avez aucun investissement dans ce bloc !");
            return false;
        }
        if (quantity == -1) {
            quantity = currentInvestment;
        }
        if (quantity <= 0 || quantity > currentInvestment) {
            player.sendMessage("§c❌ Quantité invalide ! Vous avez " + NumberFormatter.format(currentInvestment) + " unités.");
            return false;
        }
        InvestmentBlock block = investmentBlocks.get(material);
        if (block == null) return false;
        if (block.currentValue * quantity > Long.MAX_VALUE) {
            player.sendMessage("§c❌ Valeur trop élevée ! Contactez un administrateur.");
            return false;
        }
        // Coût affecté par type de banque (sans altérer la valeur globale)
        double effectiveUnitPrice = block.currentValue;
        var data2 = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data2 != null && data2.getBankType() != null) {
            effectiveUnitPrice *= data2.getBankType().getInvestmentBuyCostMultiplier();
        }
        if (effectiveUnitPrice * quantity > Long.MAX_VALUE) {
            player.sendMessage("§c❌ Montant trop élevé ! Réduisez la quantité.");
            return false;
        }
        long totalValue = (long) (effectiveUnitPrice * quantity);
        playerData.addCoins(totalValue);
        playerData.removeInvestment(material, quantity);
        block.totalInvestments = Math.max(0, block.totalInvestments - quantity);
        updateBlockValue(material);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        saveInvestmentData();
        String quantityText = quantity == currentInvestment ? "TOUT (" + NumberFormatter.format(quantity) + "x)" : NumberFormatter.format(quantity) + "x";
        player.sendMessage("§a✅ Vendu " + quantityText + " " + getBlockDisplayName(material));
        player.sendMessage("§7Valeur totale: " + NumberFormatter.format(totalValue));
        if (quantity == currentInvestment) {
            player.sendMessage("§7Vous n'avez plus d'investissement dans ce bloc.");
        }
        return true;
    }

    public boolean isTraderLevel5Plus(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return "commercant".equals(playerData.getActiveProfession()) && playerData.getProfessionLevel("commercant") >= 5;
    }

    public boolean isTraderLevel3Plus(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return "commercant".equals(playerData.getActiveProfession()) && playerData.getProfessionLevel("commercant") >= 3;
    }

    private void updateBlockValue(Material material) {
        InvestmentBlock block = investmentBlocks.get(material);
        if (block == null) return;
        double stabilityFactor = Math.min(1.0, block.totalInvestments / 100_000.0);
        block.volatility = block.baseVolatility * (1 - stabilityFactor * 0.5);
        double popularityMultiplier = 1 + (Math.log10(Math.max(1, block.totalInvestments)) * 0.1);
        block.currentValue = block.baseValue * popularityMultiplier;
        double maxValue = block.baseValue * 50.0;
        block.currentValue = Math.min(block.currentValue, maxValue);
    }

    private void startInvestmentFluctuation() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Material material : INVESTMENT_MATERIALS) {
                    fluctuateBlockValue(material);
                }
                saveInvestmentData();
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 5);
    }

    private void fluctuateBlockValue(Material material) {
        InvestmentBlock block = investmentBlocks.get(material);
        if (block == null) return;
        Random random = new Random();
        double change = (random.nextGaussian() * block.volatility);
        double newValue = block.currentValue * (1 + change);
        newValue = Math.max(block.baseValue * 0.1, Math.min(block.baseValue * 10, newValue));
        block.currentValue = newValue;
        InvestmentHistory history = new InvestmentHistory(System.currentTimeMillis(), newValue);
        investmentHistory.computeIfAbsent(material, k -> new ArrayList<>()).add(history);
        List<InvestmentHistory> hist = investmentHistory.get(material);
        if (hist.size() > 100) {
            hist.removeFirst();
        }
    }

    private void startInterestTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    calculateSavingsInterest(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 30);
    }

    private void startSafeMaintenanceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    chargeSafeMaintenance(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L * 60 * 60 * 12);
    }

    private void chargeSafeMaintenance(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long safeBalance = playerData.getSafeBalance();
        if (safeBalance <= 0) return;
        long maintenanceFee = (long) (safeBalance * 0.10);
        playerData.removeSafeBalance(maintenanceFee);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        if (player.isOnline()) {
            player.sendMessage("§c💳 Frais de gestion coffre-fort: -" + NumberFormatter.format(maintenanceFee));
        }
    }

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

    public boolean withdrawSafe(Player player, long amount) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getSafeBalance() < amount) {
            player.sendMessage("§c❌ Solde du coffre-fort insuffisant !");
            return false;
        }
        double feeRate = 0.20;
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data != null && data.getBankType() != null) {
            // Banque Fortis: frais -10 points => 10%
            if (data.getBankType().name().equals("FORTIS")) {
                feeRate = 0.10;
            }
        }
        long withdrawalFee = (long) (amount * feeRate);
        long netAmount = amount - withdrawalFee;
        playerData.removeSafeBalance(amount);
        playerData.addCoins(netAmount);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        player.sendMessage("§a✅ " + NumberFormatter.format(netAmount) + " retirés du coffre-fort");
        player.sendMessage("§7Frais de retrait (" + (int) Math.round(feeRate * 100) + "%): " + NumberFormatter.format(withdrawalFee));
        return true;
    }

    public long getSafeLimit(Player player) {
        // Capacité de base, modifiée par le type de banque
        long base = 1_000_000_000L;
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        var bankType = data.getBankType();
        return (long) (base * bankType.getSafeLimitMultiplier());
    }

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

    public long getBankLevelRequiredDeposits(int level) {
        return (long) (1_000_000 * Math.pow(5, level - 1));
    }

    public long getBankLevelExperienceCost(int level) {
        return (long) (10_000 * Math.pow(3, level - 1));
    }

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

    public InvestmentBlock getInvestmentBlock(Material material) {
        return investmentBlocks.get(material);
    }

    public List<InvestmentHistory> getInvestmentHistory(Material material) {
        return investmentHistory.getOrDefault(material, new ArrayList<>());
    }

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

    private void loadInvestmentData() {
        String query = "SELECT * FROM investments";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Material material = Material.valueOf(rs.getString("material"));
                double currentValue = rs.getDouble("current_value");
                long totalInvestments = rs.getLong("total_investments");
                InvestmentBlock block = investmentBlocks.get(material);
                if (block != null) {
                    block.currentValue = currentValue;
                    block.totalInvestments = totalInvestments;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load investment data from database: " + e.getMessage());
        }

        String historyQuery = "SELECT * FROM investment_history";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(historyQuery);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Material material = Material.valueOf(rs.getString("material"));
                long timestamp = rs.getLong("timestamp");
                double value = rs.getDouble("value");
                investmentHistory.computeIfAbsent(material, k -> new ArrayList<>()).add(new InvestmentHistory(timestamp, value));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load investment history from database: " + e.getMessage());
        }
    }

    private void saveInvestmentData() {
        String query = """
                    INSERT INTO investments (material, current_value, total_investments, base_value, volatility)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (material) DO UPDATE SET
                        current_value = EXCLUDED.current_value,
                        total_investments = EXCLUDED.total_investments,
                        base_value = EXCLUDED.base_value,
                        volatility = EXCLUDED.volatility;
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            for (InvestmentBlock block : investmentBlocks.values()) {
                ps.setString(1, block.material.name());
                ps.setDouble(2, block.currentValue);
                ps.setLong(3, block.totalInvestments);
                ps.setDouble(4, block.baseValue);
                ps.setDouble(5, block.volatility);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save investment data to database: " + e.getMessage());
        }

        String historyQuery = """
                    INSERT INTO investment_history (material, timestamp, value)
                    VALUES (?, ?, ?)
                    ON CONFLICT (material, timestamp) DO NOTHING;
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(historyQuery)) {
            for (Map.Entry<Material, List<InvestmentHistory>> entry : investmentHistory.entrySet()) {
                for (InvestmentHistory history : entry.getValue()) {
                    ps.setString(1, entry.getKey().name());
                    ps.setLong(2, history.timestamp);
                    ps.setDouble(3, history.value);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save investment history to database: " + e.getMessage());
        }
    }

    public static class InvestmentBlock {
        public Material material;
        public double baseValue;
        public double currentValue;
        public long totalInvestments;
        public double volatility;
        public double baseVolatility;

        public InvestmentBlock(Material material, double baseValue, long totalInvestments, double baseVolatility) {
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
