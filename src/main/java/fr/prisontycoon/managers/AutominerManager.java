package fr.prisontycoon.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire principal du système d'automineurs
 */
public class AutominerManager {

    private final PrisonTycoon plugin;
    private final Gson gson;
    private final NamespacedKey typeKey;
    private final NamespacedKey enchantsKey;
    private final NamespacedKey crystalsKey;

    public AutominerManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.typeKey = new NamespacedKey(plugin, "autominer_type");
        this.enchantsKey = new NamespacedKey(plugin, "autominer_enchants");
        this.crystalsKey = new NamespacedKey(plugin, "autominer_crystals");

        plugin.getPluginLogger().info("§aAutominerManager initialisé.");
    }

    /**
     * Crée un automineur d'un type donné
     */
    public ItemStack createAutominer(AutominerType type) {
        ItemStack autominer = new ItemStack(Material.BEACON);
        ItemMeta meta = autominer.getItemMeta();

        // UUID unique pour éviter la stackabilité
        String uniqueId = UUID.randomUUID().toString();
        NamespacedKey uuidKey = new NamespacedKey(plugin, "autominer_uuid");
        meta.getPersistentDataContainer().set(uuidKey, PersistentDataType.STRING, uniqueId);

        // Nom et lore de base
        plugin.getGUIManager().applyName(meta,"§6⚡ Automineur " + type.getDisplayName() + " ⚡");
        List<String> lore = new ArrayList<>();
        lore.add("§7Type: §f" + type.getDisplayName());
        lore.add("§7Consommation: §e1 tête/" + formatTime(type.getBaseFuelConsumption()));
        lore.add("");
        lore.add("§7Enchantements:");

        // Affichage des limits d'enchantements
        for (Map.Entry<String, Integer> entry : type.getEnchantmentLimits().entrySet()) {
            String enchant = entry.getKey();
            int limit = entry.getValue();
            if (limit == Integer.MAX_VALUE) {
                lore.add("§8• " + enchant + ": §7∞");
            } else {
                lore.add("§8• " + enchant + ": §70/" + limit);
            }
        }

        lore.add("");
        lore.add("§7Cristaux: §80/2");
        lore.add("");
        lore.add("§ePlacez dans un slot d'automineur");
        lore.add("§epour commencer à miner!");

        meta.setLore(lore);

        // Données NBT
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.getDisplayName());
        meta.getPersistentDataContainer().set(enchantsKey, PersistentDataType.STRING, "{}");
        meta.getPersistentDataContainer().set(crystalsKey, PersistentDataType.STRING, "{\"slot_1\":null,\"slot_2\":null}");

        autominer.setItemMeta(meta);
        return autominer;
    }

    /**
     * Vérifie si un item est un automineur
     */
    public boolean isAutominer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(typeKey, PersistentDataType.STRING);
    }

    /**
     * Obtient le type d'un automineur
     */
    public AutominerType getAutominerType(ItemStack item) {
        if (!isAutominer(item)) return null;
        String typeName = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return AutominerType.fromString(typeName);
    }

    /**
     * Obtient les enchantements d'un automineur
     */
    public Map<String, Integer> getAutominerEnchantments(ItemStack item) {
        if (!isAutominer(item)) return new HashMap<>();
        String enchantsJson = item.getItemMeta().getPersistentDataContainer().get(enchantsKey, PersistentDataType.STRING);
        if (enchantsJson == null) return new HashMap<>();

        Type type = new TypeToken<Map<String, Integer>>() {
        }.getType();
        return gson.fromJson(enchantsJson, type);
    }

    /**
     * Obtient les cristaux d'un automineur
     */
    public Map<String, String> getAutominerCrystals(ItemStack item) {
        if (!isAutominer(item)) return new HashMap<>();
        String crystalsJson = item.getItemMeta().getPersistentDataContainer().get(crystalsKey, PersistentDataType.STRING);
        if (crystalsJson == null) return Map.of("slot_1", null, "slot_2", null);

        Type type = new TypeToken<Map<String, String>>() {
        }.getType();
        return gson.fromJson(crystalsJson, type);
    }

    /**
     * Met à jour les enchantements d'un automineur
     */
    public ItemStack setAutominerEnchantments(ItemStack item, Map<String, Integer> enchantments) {
        if (!isAutominer(item)) return item;

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(enchantsKey, PersistentDataType.STRING, gson.toJson(enchantments));
        item.setItemMeta(meta);

        updateAutominerLore(item);
        return item;
    }

    /**
     * Met à jour les cristaux d'un automineur
     */
    public ItemStack setAutominerCrystals(ItemStack item, Map<String, String> crystals) {
        if (!isAutominer(item)) return item;

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(crystalsKey, PersistentDataType.STRING, gson.toJson(crystals));
        item.setItemMeta(meta);

        updateAutominerLore(item);
        return item;
    }

    /**
     * Met à jour la lore d'un automineur selon ses enchantements et cristaux
     */
    public void updateAutominerLore(ItemStack item) {
        if (!isAutominer(item)) return;

        AutominerType type = getAutominerType(item);
        Map<String, Integer> enchantments = getAutominerEnchantments(item);
        Map<String, String> crystals = getAutominerCrystals(item);

        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();

        lore.add("§7Type: §f" + type.getDisplayName());
        lore.add("§7Consommation: §e1 tête/" + formatTime(type.getBaseFuelConsumption()));
        lore.add("");
        lore.add("§7Enchantements:");

        for (Map.Entry<String, Integer> entry : type.getEnchantmentLimits().entrySet()) {
            String enchant = entry.getKey();
            int limit = entry.getValue();
            int current = enchantments.getOrDefault(enchant, 0);

            if (current > 0) {
                if (limit == Integer.MAX_VALUE) {
                    lore.add("§a• " + enchant + ": §f" + NumberFormatter.format(current));
                } else {
                    lore.add("§a• " + enchant + ": §f" + current + "§7/" + limit);
                }
            } else {
                if (limit == Integer.MAX_VALUE) {
                    lore.add("§8• " + enchant + ": §70");
                } else {
                    lore.add("§8• " + enchant + ": §70/" + limit);
                }
            }
        }

        lore.add("");
        lore.add("§7Cristaux:");

        int crystalCount = 0;
        for (Map.Entry<String, String> entry : crystals.entrySet()) {
            String slot = entry.getKey();
            String crystal = entry.getValue();

            if (crystal != null && !crystal.equals("null")) {
                lore.add("§a• Slot " + slot.charAt(slot.length() - 1) + ": §f" + crystal);
                crystalCount++;
            } else {
                lore.add("§8• Slot " + slot.charAt(slot.length() - 1) + ": §7Vide");
            }
        }

        lore.add("");
        lore.add("§ePlacez dans un slot d'automineur");
        lore.add("§epour commencer à miner!");

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Améliore un enchantement d'un automineur
     */
    public boolean upgradeEnchantment(Player player, ItemStack item, String enchantment, int baseTokenCost) {
        if (!isAutominer(item)) return false;

        AutominerType type = getAutominerType(item);
        Map<String, Integer> enchantments = getAutominerEnchantments(item);

        int currentLevel = enchantments.getOrDefault(enchantment, 0);
        int maxLevel = type.getMaxEnchantmentLevel(enchantment);

        if (currentLevel >= maxLevel) {
            player.sendMessage("§cCet enchantement est déjà au niveau maximum!");
            return false;
        }

        long cost = type.calculateUpgradeCost(enchantment, currentLevel, baseTokenCost);
        if (cost < 0) {
            player.sendMessage("§cImpossible d'améliorer cet enchantement!");
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getTokens() < cost) {
            player.sendMessage("§cVous n'avez pas assez de tokens! Coût: " + NumberFormatter.format(cost));
            return false;
        }

        // Déduire les tokens et améliorer l'enchantement
        playerData.removeTokens(cost);
        enchantments.put(enchantment, currentLevel + 1);

        setAutominerEnchantments(item, enchantments);

        player.sendMessage("§a✓ Enchantement " + enchantment + " amélioré au niveau " + (currentLevel + 1) + "!");
        player.sendMessage("§7Coût: " + NumberFormatter.format(cost) + " tokens");

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Simule le minage d'un bloc selon les enchantements et cristaux
     */
    public AutominerMiningResult simulateMining(ItemStack autominer, String worldName) {
        if (!isAutominer(autominer)) return null;

        AutominerType type = getAutominerType(autominer);
        Map<String, Integer> enchantments = getAutominerEnchantments(autominer);
        Map<String, String> crystals = getAutominerCrystals(autominer);

        // Obtenir les données de la mine
        MineData mineData = plugin.getConfigManager().getMineData(worldName);
        if (mineData == null) return null;

        // === NOUVELLE LOGIQUE EFFICACITÉ ===
        int efficaciteLevel = enchantments.getOrDefault("EFFICACITE", 0);
        int blocksToMine = calculateEfficiencyBlocks(efficaciteLevel);

        long totalCoins = 0;
        long totalTokens = 0;
        long totalExperience = 0;
        int totalKeys = 0;
        boolean beaconFound = false;

        // Miner le nombre de blocs calculé par Efficacité
        for (int i = 0; i < blocksToMine; i++) {
            // Choisir un bloc aléatoire selon la composition de la mine
            Material minedBlock = selectRandomBlock(mineData.getBlockComposition());

            // Calculer les quantités avec Fortune
            int fortuneLevel = enchantments.getOrDefault("FORTUNE", 0);
            int baseQuantity = 1;
            int quantity = calculateFortuneQuantity(baseQuantity, fortuneLevel);

            // === CORRECTION : Utiliser getSellPrice ET getBlockValue ===
            long blockCoins = 0;
            long blockTokens = 0;
            long blockExperience = 0;

            // Essayer d'abord getBlockValue
            var blockValue = plugin.getConfigManager().getBlockValue(minedBlock);
            if (blockValue != null) {
                blockCoins = blockValue.coins() * quantity;
                blockTokens = blockValue.tokens() * quantity;
                blockExperience = blockValue.experience() * quantity;
            }

            // Si pas de valeur coins dans BlockValue, utiliser getSellPrice
            if (blockCoins == 0) {
                long sellPrice = plugin.getConfigManager().getSellPrice(minedBlock);
                if (sellPrice > 0) {
                    blockCoins = sellPrice * quantity;
                }
            }

            // Si toujours pas de valeur, utiliser des valeurs par défaut selon le type de bloc
            if (blockCoins == 0 && blockTokens == 0 && blockExperience == 0) {
                switch (minedBlock) {
                    case STONE -> {
                        blockCoins = quantity;
                        blockExperience = 2L * quantity;
                    }
                    case COBBLESTONE -> {
                        blockCoins = quantity;
                        blockExperience = 2L * quantity;
                    }
                    case COAL_ORE -> {
                        blockCoins = 5L * quantity;
                        blockTokens = quantity;
                        blockExperience = 10L * quantity;
                    }
                    case IRON_ORE -> {
                        blockCoins = 10L * quantity;
                        blockTokens = 2L * quantity;
                        blockExperience = 15L * quantity;
                    }
                    case GOLD_ORE -> {
                        blockCoins = 25L * quantity;
                        blockTokens = 5L * quantity;
                        blockExperience = 30L * quantity;
                    }
                    case DIAMOND_ORE -> {
                        blockCoins = 100L * quantity;
                        blockTokens = 20L * quantity;
                        blockExperience = 100L * quantity;
                    }
                    case EMERALD_ORE -> {
                        blockCoins = 150L * quantity;
                        blockTokens = 30L * quantity;
                        blockExperience = 150L * quantity;
                    }
                    case REDSTONE_ORE -> {
                        blockCoins = 15L * quantity;
                        blockTokens = 3L * quantity;
                        blockExperience = 20L * quantity;
                    }
                    case LAPIS_ORE -> {
                        blockCoins = 20L * quantity;
                        blockTokens = 4L * quantity;
                        blockExperience = 25L * quantity;
                    }
                    case ANCIENT_DEBRIS -> {
                        blockCoins = 500L * quantity;
                        blockTokens = 100L * quantity;
                        blockExperience = 500L * quantity;
                    }
                    case NETHERITE_BLOCK -> {
                        blockCoins = 1000L * quantity;
                        blockTokens = 200L * quantity;
                        blockExperience = 1000L * quantity;
                    }
                    case BEACON -> {
                        blockCoins = 2000L * quantity;
                        blockTokens = 500L * quantity;
                        blockExperience = 2000L * quantity;
                    }
                    default -> {
                        // Valeur minimale pour tout autre bloc
                        blockCoins = quantity;
                        blockExperience = quantity;
                    }
                }
            }

            // === NOUVELLES LOGIQUES GREED (Correction) ===

            // MoneyGreed : 25% de chance de bonus par bloc
            int moneyGreedLevel = enchantments.getOrDefault("MONEYGREED", 0);
            if (moneyGreedLevel > 0 && ThreadLocalRandom.current().nextDouble() < 0.25) {
                // Bonus en coins selon le niveau (même si le bloc n'a pas de coins de base)
                long greedBonus = Math.max(1, moneyGreedLevel * quantity); // Minimum 1 coin par niveau
                blockCoins += greedBonus;
            }

            // TokenGreed : 25% de chance de bonus par bloc
            int tokenGreedLevel = enchantments.getOrDefault("TOKENGREED", 0);
            if (tokenGreedLevel > 0 && ThreadLocalRandom.current().nextDouble() < 0.25) {
                // Bonus en tokens selon le niveau (même si le bloc n'a pas de tokens de base)
                long greedBonus = Math.max(1, Math.round(tokenGreedLevel * 0.5 * quantity)); // 0.5 token par niveau
                blockTokens += greedBonus;
            }

            // ExpGreed : 25% de chance de bonus par bloc
            int expGreedLevel = enchantments.getOrDefault("EXPGREED", 0);
            if (expGreedLevel > 0 && ThreadLocalRandom.current().nextDouble() < 0.25) {
                // Bonus en expérience selon le niveau (même si le bloc n'a pas d'exp de base)
                long greedBonus = Math.max(1, expGreedLevel * 2 * quantity); // 2 exp par niveau
                blockExperience += greedBonus;
            }

            // Appliquer les cristaux (amplification)
            for (String crystal : crystals.values()) {
                if (crystal != null && !crystal.equals("null")) {
                    if (crystal.contains("MoneyBoost")) {
                        blockCoins = Math.round(blockCoins * 1.1); // +10% exemple
                    } else if (crystal.contains("TokenBoost")) {
                        blockTokens = Math.round(blockTokens * 1.1);
                    } else if (crystal.contains("XPBoost")) {
                        blockExperience = Math.round(blockExperience * 1.1);
                    }
                }
            }

            totalCoins += blockCoins;
            totalTokens += blockTokens;
            totalExperience += blockExperience;
        }

        // KeyGreed : 0.001% de chance par niveau de donner une clé
        int keyGreedLevel = enchantments.getOrDefault("KEYGREED", 0);
        if (keyGreedLevel > 0) {
            double keyChance = keyGreedLevel * 0.00001; // 0.001% = 0.00001
            if (ThreadLocalRandom.current().nextDouble() < keyChance) {
                totalKeys++;
            }
        }

        // BeaconFinder : niveau 1 = 1% de chance
        int beaconFinderLevel = enchantments.getOrDefault("BEACONFINDER", 0);
        if (beaconFinderLevel > 0) {
            double beaconChance = beaconFinderLevel * 0.01; // 1% par niveau
            if (ThreadLocalRandom.current().nextDouble() < beaconChance) {
                beaconFound = true;
            }
        }
        // Retourner le premier bloc miné pour l'affichage, mais les gains sont la somme
        Material displayBlock = selectRandomBlock(mineData.getBlockComposition());

        return new AutominerMiningResult(displayBlock, blocksToMine, totalCoins, totalTokens,
                totalExperience, totalKeys, beaconFound);
    }

    /**
     * Calcule la consommation de carburant d'un automineur
     */
    public double calculateFuelConsumption(ItemStack autominer) {
        if (!isAutominer(autominer)) return 0;

        AutominerType type = getAutominerType(autominer);
        Map<String, Integer> enchantments = getAutominerEnchantments(autominer);

        int fuelEfficiencyLevel = enchantments.getOrDefault("FUELEFFICIENCY", 0);

        // Nouvelle logique : niveau 100 = réduction par 2
        double reductionFactor = Math.min(fuelEfficiencyLevel / 100.0, 0.9); // Max 90% de réduction
        double baseConsumptionPerSecond = 1.0 / type.getBaseFuelConsumption();

        return baseConsumptionPerSecond * (1.0 - reductionFactor);
    }

// ==================== NOUVELLES MÉTHODES POUR EFFICACITÉ ====================

    /**
     * Calcule le nombre de blocs minés selon le niveau d'Efficacité
     */
    private int calculateEfficiencyBlocks(int efficaciteLevel) {
        if (efficaciteLevel == 0) return 1;

        // Logique :
        // - Niveau 1-1499 : toujours 1 bloc, mais fréquence augmente
        // - Niveau 1500+ : chance de miner un 2ème bloc
        int baseBlocks = 1;

        if (efficaciteLevel >= 1500) {
            // 50% de chance d'un 2ème bloc au niveau 1500, puis +10% par 100 niveaux
            double chanceSecondBlock = 0.5 + ((efficaciteLevel - 1500) / 1000.0);
            chanceSecondBlock = Math.min(chanceSecondBlock, 1.0); // Max 100%

            if (ThreadLocalRandom.current().nextDouble() < chanceSecondBlock) {
                baseBlocks = 2;
            }
        }

        return baseBlocks;
    }

// ==================== MÉTHODES UTILITAIRES ====================

    private String formatTime(int seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h";
        } else if (seconds >= 60) {
            return (seconds / 60) + "min";
        } else {
            return seconds + "s";
        }
    }

    private Material selectRandomBlock(Map<Material, Double> composition) {
        double random = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<Material, Double> entry : composition.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                return entry.getKey();
            }
        }

        // Retour de secours
        return composition.keySet().iterator().next();
    }

    private int calculateFortuneQuantity(int baseQuantity, int fortuneLevel) {
        if (fortuneLevel <= 0) return baseQuantity;

        // Fortune multiplie vraiment le nombre de blocs obtenus
        // Formule : base + (base * niveau * 0.01) avec chance aléatoire
        double multiplier = 1.0 + (fortuneLevel * 0.01); // 1% par niveau

        // Partie entière garantie + chance pour le reste
        int guaranteedQuantity = (int) multiplier;
        double fractionalPart = multiplier - guaranteedQuantity;

        int finalQuantity = guaranteedQuantity;
        if (ThreadLocalRandom.current().nextDouble() < fractionalPart) {
            finalQuantity++;
        }

        return Math.max(baseQuantity, finalQuantity);
    }

    private long applyGreedBonus(long baseValue, int greedLevel) {
        if (greedLevel <= 0) return baseValue;

        double bonus = greedLevel * 0.01; // 1% par niveau
        return Math.round(baseValue * (1 + bonus));
    }

    /**
     * Crée une clé selon le type d'automineur
     */
    public ItemStack createKey(AutominerType type) {
        String keyType = switch (type) {
            case PIERRE, FER -> "Commune";
            case OR -> "Peu Commune";
            case DIAMANT -> "Rare";
            case EMERAUDE -> "Légendaire";
            case BEACON -> "Cristal";
        };

        return createKey(keyType);
    }

    public ItemStack createKey(String keyType) {
        String keyColor = switch (keyType) {
            case "Cristal" -> "§d";
            case "Légendaire" -> "§6";
            case "Rare" -> "§5";
            case "Peu Commune" -> "§9";
            default -> "§f"; // "Commune" et autres cas
        };

        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK);
        var meta = key.getItemMeta();

        plugin.getGUIManager().applyName(meta,keyColor + "Clé " + keyType);
        meta.setLore(Arrays.asList(
                "§7Clé de coffre " + keyColor + keyType,
                "§7Utilise cette clé pour ouvrir des coffres!"
        ));

        key.setItemMeta(meta);
        return key;
    }

    /**
     * Résultat d'une simulation de minage
     */
    public record AutominerMiningResult(Material minedBlock, int quantity, long coins, long tokens, long experience,
                                        int keys, boolean beaconFound) {
    }
}