package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire des enchantements spécialisés pour automineurs
 * Gère l'amélioration avec des tokens selon les formules spécifiques
 */
public class AutominerEnchantmentManager {

    private final PrisonTycoon plugin;
    private final Map<String, AutominerEnchantment> enchantments;

    public AutominerEnchantmentManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.enchantments = new HashMap<>();

        registerEnchantments();
        plugin.getPluginLogger().info("§aAutominerEnchantmentManager initialisé avec " +
                enchantments.size() + " enchantements.");
    }

    /**
     * Enregistre tous les enchantements d'automineur
     */
    private void registerEnchantments() {
        // Enchantements de base (compatibles avec les pioches)
        enchantments.put("efficiency", new EfficiencyEnchantment());
        enchantments.put("fortune", new FortuneEnchantment());
        enchantments.put("tokengreed", new TokenGreedEnchantment());
        enchantments.put("expgreed", new ExpGreedEnchantment());
        enchantments.put("moneygreed", new MoneyGreedEnchantment());
        enchantments.put("keygreed", new KeyGreedEnchantment());

        // Enchantements spécialisés automineurs
        enchantments.put("fuelefficiency", new FuelEfficiencyEnchantment());
        enchantments.put("beaconfinder", new BeaconFinderEnchantment());
    }

    /**
     * Améliore un enchantement d'automineur avec des tokens
     */
    public boolean upgradeEnchantment(Player player, AutominerData autominer, String enchantmentName, int levels) {
        AutominerEnchantment enchant = enchantments.get(enchantmentName.toLowerCase());
        if (enchant == null) {
            player.sendMessage("§c❌ Enchantement introuvable: " + enchantmentName);
            return false;
        }

        // Vérification de compatibilité avec le type d'automineur
        if (!autominer.getType().supportsEnchantment(enchantmentName)) {
            player.sendMessage("§c❌ Cet enchantement n'est pas compatible avec un automineur " +
                    autominer.getType().getColoredName());
            return false;
        }

        int currentLevel = autominer.getEnchantmentLevel(enchantmentName);
        int maxLevel = autominer.getType().getMaxEnchantmentLevel(enchantmentName);

        // Vérification du niveau maximum
        if (currentLevel >= maxLevel) {
            player.sendMessage("§c❌ Enchantement déjà au niveau maximum! §7(" + maxLevel + ")");
            return false;
        }

        // Limitation des niveaux à ajouter
        int actualLevels = Math.min(levels, maxLevel - currentLevel);

        // Calcul du coût total
        long totalCost = 0;
        for (int i = 0; i < actualLevels; i++) {
            totalCost += calculateEnchantmentCost(enchant, currentLevel + i, autominer.getType());
        }

        // Vérification des tokens
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getTokens() < totalCost) {
            player.sendMessage("§c❌ Tokens insuffisants! §7(Requis: " +
                    NumberFormatter.format(totalCost) + ", Vous avez: " +
                    NumberFormatter.format(playerData.getTokens()) + ")");
            return false;
        }

        // Application de l'amélioration
        playerData.removeTokens(totalCost);
        boolean success = autominer.addEnchantment(enchantmentName, actualLevels);

        if (success) {
            int newLevel = autominer.getEnchantmentLevel(enchantmentName);
            player.sendMessage("§a✅ " + enchant.getDisplayName() + " amélioré au niveau §e" +
                    newLevel + " §7(-" + NumberFormatter.format(totalCost) + " tokens)");
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
            return true;
        }

        return false;
    }

    /**
     * Calcule le coût d'amélioration d'un enchantement
     * Formule: BaseTokens × NiveauActuel/100 × CoeffRareté
     */
    public long calculateEnchantmentCost(AutominerEnchantment enchant, int currentLevel, AutominerType type) {
        long baseTokens = enchant.getBaseTokenCost();
        double levelMultiplier = Math.max(1.0, currentLevel / 100.0);
        double rarityCoeff = type.getRarityCoefficient();

        return Math.round(baseTokens * levelMultiplier * rarityCoeff);
    }

    /**
     * Obtient les informations de coût pour l'interface
     */
    public String getUpgradeCostInfo(AutominerEnchantment enchant, int currentLevel,
                                     AutominerType type, int levels) {
        long totalCost = 0;
        for (int i = 0; i < levels; i++) {
            totalCost += calculateEnchantmentCost(enchant, currentLevel + i, type);
        }
        return NumberFormatter.format(totalCost) + " tokens";
    }

    // Getters publics
    public AutominerEnchantment getEnchantment(String name) {
        return enchantments.get(name.toLowerCase());
    }

    // Implémentations des enchantements

    public Map<String, AutominerEnchantment> getAllEnchantments() {
        return new HashMap<>(enchantments);
    }

    /**
     * Interface pour les enchantements d'automineur
     */
    public interface AutominerEnchantment {
        String getName();

        String getDisplayName();

        String getDescription();

        long getBaseTokenCost();

        int getMaxLevel();
    }

    public static class EfficiencyEnchantment implements AutominerEnchantment {
        @Override
        public String getName() {
            return "efficiency";
        }

        @Override
        public String getDisplayName() {
            return "Efficacité";
        }

        @Override
        public String getDescription() {
            return "Augmente la vitesse de minage virtuel";
        }

        @Override
        public long getBaseTokenCost() {
            return 1000;
        }

        @Override
        public int getMaxLevel() {
            return Integer.MAX_VALUE;
        }
    }

    public static class FortuneEnchantment implements AutominerEnchantment {
        @Override
        public String getName() {
            return "fortune";
        }

        @Override
        public String getDisplayName() {
            return "Fortune";
        }

        @Override
        public String getDescription() {
            return "Chance d'obtenir plus de ressources";
        }

        @Override
        public long getBaseTokenCost() {
            return 2000;
        }

        @Override
        public int getMaxLevel() {
            return Integer.MAX_VALUE;
        }
    }

    public static class TokenGreedEnchantment implements AutominerEnchantment {
        @Override
        public String getName() {
            return "tokengreed";
        }

        @Override
        public String getDisplayName() {
            return "Token Greed";
        }

        @Override
        public String getDescription() {
            return "Chance d'obtenir des tokens bonus";
        }

        @Override
        public long getBaseTokenCost() {
            return 5000;
        }

        @Override
        public int getMaxLevel() {
            return Integer.MAX_VALUE;
        }
    }

    public static class ExpGreedEnchantment implements AutominerEnchantment {
        @Override
        public String getName() {
            return "expgreed";
        }

        @Override
        public String getDisplayName() {
            return "Exp Greed";
        }

        @Override
        public String getDescription() {
            return "Chance d'obtenir de l'expérience bonus";
        }

        @Override
        public long getBaseTokenCost() {
            return 3000;
        }

        @Override
        public int getMaxLevel() {
            return Integer.MAX_VALUE;
        }
    }

    // Enchantements spécialisés automineurs

    public static class MoneyGreedEnchantment implements AutominerEnchantment {
        @Override
        public String getName() {
            return "moneygreed";
        }

        @Override
        public String getDisplayName() {
            return "Money Greed";
        }

        @Override
        public String getDescription() {
            return "Chance d'obtenir de l'argent bonus";
        }

        @Override
        public long getBaseTokenCost() {
            return 4000;
        }

        @Override
        public int getMaxLevel() {
            return Integer.MAX_VALUE;
        }
    }

    public static class KeyGreedEnchantment implements AutominerEnchantment {
        @Override
        public String getName() {
            return "keygreed";
        }

        @Override
        public String getDisplayName() {
            return "Key Greed";
        }

        @Override
        public String getDescription() {
            return "Chance d'obtenir des clés bonus";
        }

        @Override
        public long getBaseTokenCost() {
            return 8000;
        }

        @Override
        public int getMaxLevel() {
            return Integer.MAX_VALUE;
        }
    }

    public static class FuelEfficiencyEnchantment implements AutominerEnchantment {
        @Override
        public String getName() {
            return "fuelefficiency";
        }

        @Override
        public String getDisplayName() {
            return "Efficacité Carburant";
        }

        @Override
        public String getDescription() {
            return "Réduit la consommation de têtes (-2% par niveau)";
        }

        @Override
        public long getBaseTokenCost() {
            return 6000;
        }

        @Override
        public int getMaxLevel() {
            return 100;
        } // Limite pour éviter la consommation nulle
    }

    public static class BeaconFinderEnchantment implements AutominerEnchantment {
        @Override
        public String getName() {
            return "beaconfinder";
        }

        @Override
        public String getDisplayName() {
            return "Beacon Finder";
        }

        @Override
        public String getDescription() {
            return "Augmente la chance de miner des beacons (Beacon uniquement)";
        }

        @Override
        public long getBaseTokenCost() {
            return 50000;
        }

        @Override
        public int getMaxLevel() {
            return 1;
        } // Enchantement unique
    }
}