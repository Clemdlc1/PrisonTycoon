package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.BlockValueData;
import fr.prisontycoon.data.MineData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Gestionnaire de configuration du plugin
 * CORRIGÉ : Chargement des mines et gestion des erreurs
 */
public class ConfigManager {

    private final PrisonTycoon plugin;
    private FileConfiguration config;

    // Cache des données de configuration
    private Map<String, MineData> minesData;
    private Map<Material, BlockValueData> blockValues;
    private Map<String, Object> enchantmentSettings;
    private Map<Material, Long> sellPrices;


    public ConfigManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    /**
     * Charge et valide toute la configuration
     */
    public void loadConfiguration() {
        plugin.getPluginLogger().info("§7Chargement de la configuration...");

        // Recharge le fichier depuis le disque
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Initialise les collections
        minesData = new HashMap<>();
        blockValues = new HashMap<>();
        enchantmentSettings = new HashMap<>();
        sellPrices = new HashMap<>();


        try {
            // Charge les différentes sections
            loadMinesConfiguration();
            loadBlockValuesConfiguration();
            loadEnchantmentConfiguration();
            loadSellPricesConfiguration();

            plugin.getPluginLogger().info("§aConfiguration chargée avec succès!");
            plugin.getPluginLogger().info("§7- " + minesData.size() + " mines configurées");
            plugin.getPluginLogger().info("§7- " + blockValues.size() + " types de blocs valorisés");
            plugin.getPluginLogger().info("§7- " + sellPrices.size() + " prix de vente configurés"); // NOUVEAU


        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors du chargement de la configuration:");
            e.printStackTrace();
            throw new RuntimeException("Configuration invalide", e);
        }
    }

    /**
     * Charge la configuration des mines
     * CORRIGÉ : Gestion des erreurs et validation
     */
    private void loadMinesConfiguration() {
        plugin.getPluginLogger().debug("Chargement des mines...");

        ConfigurationSection minesSection = config.getConfigurationSection("mines");
        if (minesSection == null) {
            plugin.getPluginLogger().warning("§cAucune section 'mines' trouvée dans la config!");
            plugin.getPluginLogger().warning("§cVérifiez que le fichier config.yml contient une section 'mines:'");
            return;
        }

        Set<String> mineNames = minesSection.getKeys(false);
        plugin.getPluginLogger().debug("Mines trouvées dans la config: " + mineNames);

        for (String mineName : mineNames) {
            try {
                plugin.getPluginLogger().debug("Chargement de la mine: " + mineName);

                ConfigurationSection mineSection = minesSection.getConfigurationSection(mineName);
                if (mineSection == null) {
                    plugin.getPluginLogger().warning("§cSection mine invalide pour: " + mineName);
                    continue;
                }

                MineData mineData = loadSingleMine(mineName, mineSection);
                minesData.put(mineName, mineData);

                plugin.getPluginLogger().info("§aMine '" + mineName + "' chargée: " +
                        mineData.getBlockComposition().size() + " types de blocs, volume: " +
                        mineData.getVolume() + " blocs");

            } catch (Exception e) {
                plugin.getPluginLogger().severe("§cErreur lors du chargement de la mine '" + mineName + "':");
                e.printStackTrace();
            }
        }

        if (minesData.isEmpty()) {
            plugin.getPluginLogger().warning("§cAucune mine valide chargée! Vérifiez votre configuration.");
        }
    }

    /**
     * Charge une mine individuelle depuis la configuration
     * CORRIGÉ : Validation et gestion des mondes
     */
    private MineData loadSingleMine(String name, ConfigurationSection section) {
        if (section == null) {
            throw new IllegalArgumentException("Section de mine nulle pour: " + name);
        }

        plugin.getPluginLogger().debug("Chargement des coordonnées pour: " + name);

        // Coordonnées
        ConfigurationSection coordsSection = section.getConfigurationSection("coordinates");
        if (coordsSection == null) {
            throw new IllegalArgumentException("Coordonnées manquantes pour la mine: " + name);
        }

        // Monde (par défaut ou spécifié)
        String worldName = section.getString("world", "world");
        World world = plugin.getServer().getWorld(worldName);

        if (world == null) {
            plugin.getPluginLogger().warning("§cMonde '" + worldName + "' introuvable pour la mine " + name +
                    ", utilisation du monde principal");
            world = plugin.getServer().getWorlds().getFirst();
        }

        // Lecture des coordonnées avec valeurs par défaut
        int minX = coordsSection.getInt("min-x", 0);
        int minY = coordsSection.getInt("min-y", 0);
        int minZ = coordsSection.getInt("min-z", 0);
        int maxX = coordsSection.getInt("max-x", 0);
        int maxY = coordsSection.getInt("max-y", 0);
        int maxZ = coordsSection.getInt("max-z", 0);

        Location minCorner = new Location(world, minX, minY, minZ);
        Location maxCorner = new Location(world, maxX, maxY, maxZ);

        plugin.getPluginLogger().debug("Mine " + name + " - Coordonnées: " +
                minX + "," + minY + "," + minZ + " à " + maxX + "," + maxY + "," + maxZ);

        // Validation des coordonnées
        if (minX == maxX && minY == maxY && minZ == maxZ) {
            throw new IllegalArgumentException("Coordonnées identiques pour la mine: " + name);
        }

        // Composition des blocs
        plugin.getPluginLogger().debug("Chargement de la composition pour: " + name);
        ConfigurationSection blocksSection = section.getConfigurationSection("blocks");
        if (blocksSection == null) {
            throw new IllegalArgumentException("Composition de blocs manquante pour la mine: " + name);
        }

        Map<Material, Double> composition = new HashMap<>();
        double totalProbability = 0.0;

        Set<String> blockNames = blocksSection.getKeys(false);
        plugin.getPluginLogger().debug("Blocs trouvés pour " + name + ": " + blockNames);

        for (String blockName : blockNames) {
            try {
                Material material = Material.valueOf(blockName.toUpperCase());
                double probability = blocksSection.getDouble(blockName);

                if (probability <= 0.0 || probability > 1.0) {
                    throw new IllegalArgumentException("Probabilité invalide pour " + blockName +
                            " dans la mine " + name + ": " + probability + " (doit être entre 0.0 et 1.0)");
                }

                composition.put(material, probability);
                totalProbability += probability;

                plugin.getPluginLogger().debug("- " + blockName + ": " + probability);

            } catch (IllegalArgumentException e) {
                if (e.getMessage().contains("No enum constant")) {
                    plugin.getPluginLogger().warning("§cMatériau invalide ignoré: " + blockName +
                            " dans la mine " + name + " (matériau inexistant dans Minecraft 1.21)");
                } else {
                    throw e;
                }
            }
        }

        if (composition.isEmpty()) {
            throw new IllegalArgumentException("Aucun bloc valide dans la composition de la mine: " + name);
        }

        // Validation de la probabilité totale
        if (Math.abs(totalProbability - 1.0) > 0.01) { // Tolérance de 1%
            plugin.getPluginLogger().warning("§eAttention: Probabilité totale pour la mine '" + name +
                    "' = " + String.format("%.3f", totalProbability) + " (devrait être proche de 1.0)");
        }

        return new MineData(name, minCorner, maxCorner, composition);
    }

    /**
     * Charge la configuration des valeurs de blocs
     * CORRIGÉ : Validation des matériaux
     */
    private void loadBlockValuesConfiguration() {
        ConfigurationSection valuesSection = config.getConfigurationSection("block-values");
        if (valuesSection == null) {
            plugin.getPluginLogger().warning("§cAucune section 'block-values' trouvée dans la config!");
            // Crée des valeurs par défaut pour les blocs communs
            createDefaultBlockValues();
            return;
        }

        for (String blockName : valuesSection.getKeys(false)) {
            try {
                Material material = Material.valueOf(blockName.toUpperCase());
                ConfigurationSection blockSection = valuesSection.getConfigurationSection(blockName);

                if (blockSection == null) continue;

                long coins = blockSection.getLong("coins", 0);
                long tokens = blockSection.getLong("tokens", 0);
                long experience = blockSection.getLong("experience", 0);

                BlockValueData valueData = new BlockValueData(material, coins, tokens, experience);
                blockValues.put(material, valueData);

                plugin.getPluginLogger().debug("Valeur chargée: " + blockName + " -> " +
                        coins + " coins, " + tokens + " tokens, " + experience + " XP");

            } catch (IllegalArgumentException e) {
                plugin.getPluginLogger().warning("§cMatériau invalide ignoré dans block-values: " + blockName);
            }
        }

        plugin.getPluginLogger().info("§7" + blockValues.size() + " valeurs de blocs chargées.");
    }

    /**
     * Crée des valeurs par défaut si aucune n'est configurée
     */
    private void createDefaultBlockValues() {
        plugin.getPluginLogger().info("§7Création de valeurs par défaut...");

        // Valeurs par défaut pour les blocs communs
        blockValues.put(Material.STONE, new BlockValueData(Material.STONE, 5, 2, 10));
        blockValues.put(Material.COBBLESTONE, new BlockValueData(Material.COBBLESTONE, 3, 1, 8));
        blockValues.put(Material.COAL_ORE, new BlockValueData(Material.COAL_ORE, 15, 5, 25));
        blockValues.put(Material.IRON_ORE, new BlockValueData(Material.IRON_ORE, 30, 10, 50));
        blockValues.put(Material.GOLD_ORE, new BlockValueData(Material.GOLD_ORE, 75, 25, 100));
        blockValues.put(Material.DIAMOND_ORE, new BlockValueData(Material.DIAMOND_ORE, 200, 75, 300));
    }

    /**
     * Charge la configuration des enchantements
     */
    private void loadEnchantmentConfiguration() {
        ConfigurationSection enchSection = config.getConfigurationSection("enchantments");
        if (enchSection == null) {
            plugin.getPluginLogger().warning("§cAucune section 'enchantments' trouvée dans la config!");
            return;
        }

        // Charge tous les paramètres des enchantements
        loadEnchantmentSection("greed", enchSection);
        loadEnchantmentSection("combustion", enchSection);
        loadEnchantmentSection("keys", enchSection);
        loadEnchantmentSection("efficiency", enchSection);
        loadEnchantmentSection("fortune", enchSection);
        loadEnchantmentSection("durability", enchSection);
        loadEnchantmentSection("mobility", enchSection);
        loadEnchantmentSection("special", enchSection);
        loadEnchantmentSection("costs", enchSection);
    }

    /**
     * Charge une section d'enchantement spécifique
     */
    private void loadEnchantmentSection(String sectionName, ConfigurationSection parent) {
        ConfigurationSection section = parent.getConfigurationSection(sectionName);
        if (section == null) return;

        for (String key : section.getKeys(true)) {
            enchantmentSettings.put(sectionName + "." + key, section.get(key));
        }
    }

    // Getters pour accéder aux données de configuration

    /**
     * Retourne les données d'une mine par son nom
     * CORRIGÉ : Logging pour debug
     */
    public MineData getMineData(String mineName) {
        MineData data = minesData.get(mineName);
        if (data == null) {
            plugin.getPluginLogger().debug("Mine non trouvée: " + mineName +
                    ". Mines disponibles: " + minesData.keySet());
        }
        return data;
    }

    /**
     * Retourne toutes les mines configurées
     */
    public Map<String, MineData> getAllMines() {
        return new HashMap<>(minesData);
    }

    /**
     * Retourne les valeurs économiques d'un bloc
     */
    public BlockValueData getBlockValue(Material material) {
        return blockValues.getOrDefault(material, new BlockValueData(material, 0, 0, 0));
    }

    /**
     * Retourne un paramètre d'enchantement
     */
    @SuppressWarnings("unchecked")
    public <T> T getEnchantmentSetting(String path, T defaultValue) {
        Object value = enchantmentSettings.get(path);
        if (value == null) return defaultValue;

        try {
            return (T) value;
        } catch (ClassCastException e) {
            plugin.getPluginLogger().warning("§cType incorrect pour le paramètre: " + path);
            return defaultValue;
        }
    }

    /**
     * Valide qu'un joueur est dans une mine
     * CORRIGÉ : Meilleur logging
     */
    public String getPlayerMine(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        for (Map.Entry<String, MineData> entry : minesData.entrySet()) {
            if (entry.getValue().contains(location)) {
                plugin.getPluginLogger().debug("Joueur dans la mine: " + entry.getKey());
                return entry.getKey();
            }
        }

        plugin.getPluginLogger().debug("Joueur hors mine à: " + location.getWorld().getName() +
                " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        return null;
    }

    /**
     * NOUVEAU : Charge la configuration des prix de vente
     */
    private void loadSellPricesConfiguration() {
        ConfigurationSection sellSection = config.getConfigurationSection("sell-prices");
        if (sellSection == null) {
            plugin.getPluginLogger().warning("§cAucune section 'sell-prices' trouvée dans la config!");
            // Crée des prix par défaut
            createDefaultSellPrices();
            return;
        }

        for (String blockName : sellSection.getKeys(false)) {
            try {
                Material material = Material.valueOf(blockName.toUpperCase());
                long price = sellSection.getLong(blockName, 0);

                if (price > 0) {
                    sellPrices.put(material, price);
                    plugin.getPluginLogger().debug("Prix de vente chargé: " + blockName + " -> " + price + " coins");
                }

            } catch (IllegalArgumentException e) {
                plugin.getPluginLogger().warning("§cMatériau invalide ignoré dans sell-prices: " + blockName);
            }
        }

        plugin.getPluginLogger().info("§7" + sellPrices.size() + " prix de vente chargés.");
    }

    /**
     * NOUVEAU : Crée des prix de vente par défaut
     */
    private void createDefaultSellPrices() {
        plugin.getPluginLogger().info("§7Création de prix de vente par défaut...");

        // Prix par défaut basés sur la rareté
        sellPrices.put(Material.STONE, 1L);
        sellPrices.put(Material.COBBLESTONE, 1L);
        sellPrices.put(Material.COAL_ORE, 5L);
        sellPrices.put(Material.IRON_ORE, 10L);
        sellPrices.put(Material.GOLD_ORE, 25L);
        sellPrices.put(Material.REDSTONE_ORE, 15L);
        sellPrices.put(Material.LAPIS_ORE, 20L);
        sellPrices.put(Material.DIAMOND_ORE, 50L);
        sellPrices.put(Material.EMERALD_ORE, 75L);
        sellPrices.put(Material.ANCIENT_DEBRIS, 200L);

        // Items transformés
        sellPrices.put(Material.COAL, 3L);
        sellPrices.put(Material.IRON_INGOT, 8L);
        sellPrices.put(Material.GOLD_INGOT, 20L);
        sellPrices.put(Material.REDSTONE, 2L);
        sellPrices.put(Material.LAPIS_LAZULI, 3L);
        sellPrices.put(Material.DIAMOND, 40L);
        sellPrices.put(Material.EMERALD, 60L);
        sellPrices.put(Material.NETHERITE_SCRAP, 150L);
    }

    /**
     * NOUVEAU : Obtient le prix de vente d'un matériau
     */
    public long getSellPrice(Material material) {
        return sellPrices.getOrDefault(material, 0L);
    }

    /**
     * Recharge la configuration depuis le fichier
     */
    public void reloadConfiguration() {
        loadConfiguration();
        plugin.getPluginLogger().info("§aConfiguration rechargée!");
    }
}