package fr.prisontycoon.managers;

import com.sk89q.worldedit.math.BlockVector3;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.BlockValueData;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.WarpData;
import org.bukkit.Location;
import org.bukkit.Material;
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
    private Map<String, WarpData> warpsData;


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
            loadWarpsConfiguration();

            // Valeurs par défaut du shop imprimantes si absentes (T1..T50)
            if (!config.isConfigurationSection("shop.printers")) {
                for (int tier = 1; tier <= 50; tier++) {
                    long price = Math.max(1000L, Math.round(5000L * Math.pow(1.35, tier - 1)));
                    config.set("shop.printers.t" + tier + ".price", price);
                }
                plugin.saveConfig();
            }


            plugin.getPluginLogger().info("§aConfiguration chargée avec succès!");
            plugin.getPluginLogger().info("§7- " + minesData.size() + " mines configurées");
            plugin.getPluginLogger().info("§7- " + blockValues.size() + " types de blocs valorisés");
            plugin.getPluginLogger().info("§7- " + sellPrices.size() + " prix de vente configurés");
            plugin.getPluginLogger().info("§7- " + warpsData.size() + " warps configurés");


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
    private MineData loadSingleMine(String mineName, ConfigurationSection section) {
        plugin.getPluginLogger().debug("Chargement détaillé de la mine: " + mineName);

        try {
            // Informations de base
            String displayName = section.getString("display-name", mineName.toUpperCase());
            String description = section.getString("description", "");

            // Type de mine
            String typeString = section.getString("type", "NORMAL").toUpperCase();
            MineData.MineType type;
            try {
                type = MineData.MineType.valueOf(typeString);
            } catch (IllegalArgumentException e) {
                plugin.getPluginLogger().warning("§cType de mine invalide '" + typeString + "' pour " + mineName + ", utilisation de NORMAL");
                type = MineData.MineType.NORMAL;
            }

            // Monde
            String worldName = section.getString("world");
            if (worldName == null || worldName.trim().isEmpty()) {
                throw new IllegalArgumentException("Monde non spécifié pour la mine " + mineName);
            }

            // Coordonnées de la région
            ConfigurationSection coordsSection = section.getConfigurationSection("coordinates");
            if (coordsSection == null) {
                throw new IllegalArgumentException("Section 'coordinates' manquante pour la mine " + mineName);
            }

            int minX = coordsSection.getInt("min-x");
            int minY = coordsSection.getInt("min-y");
            int minZ = coordsSection.getInt("min-z");
            int maxX = coordsSection.getInt("max-x");
            int maxY = coordsSection.getInt("max-y");
            int maxZ = coordsSection.getInt("max-z");

            BlockVector3 minPos = BlockVector3.at(Math.min(minX, maxX), Math.min(minY, maxY), Math.min(minZ, maxZ));
            BlockVector3 maxPos = BlockVector3.at(Math.max(minX, maxX), Math.max(minY, maxY), Math.max(minZ, maxZ));

            // Composition des blocs
            ConfigurationSection blocksSection = section.getConfigurationSection("blocks");
            if (blocksSection == null) {
                throw new IllegalArgumentException("Section 'blocks' manquante pour la mine " + mineName);
            }

            Map<Material, Double> blockComposition = new HashMap<>();
            Set<String> blockNames = blocksSection.getKeys(false);

            for (String blockName : blockNames) {
                try {
                    Material material = Material.valueOf(blockName.toUpperCase());
                    double percentage = blocksSection.getDouble(blockName);

                    if (percentage < 0 || percentage > 1) {
                        plugin.getPluginLogger().warning("§cPourcentage invalide pour " + blockName + " dans " + mineName + ": " + percentage);
                        continue;
                    }

                    blockComposition.put(material, percentage);
                    plugin.getPluginLogger().debug("  Bloc ajouté: " + material + " -> " + (percentage * 100) + "%");

                } catch (IllegalArgumentException e) {
                    plugin.getPluginLogger().warning("§cMatériau invalide ignoré dans " + mineName + ": " + blockName);
                }
            }

            if (blockComposition.isEmpty()) {
                throw new IllegalArgumentException("Aucun bloc valide trouvé pour la mine " + mineName);
            }

            // Validation de la composition totale
            double totalPercentage = blockComposition.values().stream().mapToDouble(Double::doubleValue).sum();
            plugin.getPluginLogger().debug("Pourcentage total pour " + mineName + ": " + totalPercentage);

            if (Math.abs(totalPercentage - 1.0) > 0.01) {
                plugin.getPluginLogger().warning("§cLa somme des pourcentages n'est pas égale à 100% pour " + mineName +
                        " (actuel: " + (totalPercentage * 100) + "%)");
            }

            // Intervalle de reset
            int resetInterval = section.getInt("reset-interval-minutes", 30);

            // NOUVEAU: Coordonnées de téléportation
            double teleportX, teleportY, teleportZ;
            float teleportYaw = 0f, teleportPitch = 0f;

            ConfigurationSection teleportSection = section.getConfigurationSection("teleport");
            if (teleportSection != null) {
                // Coordonnées personnalisées de téléportation
                teleportX = teleportSection.getDouble("x");
                teleportY = teleportSection.getDouble("y");
                teleportZ = teleportSection.getDouble("z");
                teleportYaw = (float) teleportSection.getDouble("yaw", 0.0);
                teleportPitch = (float) teleportSection.getDouble("pitch", 0.0);

                plugin.getPluginLogger().debug("Téléportation personnalisée pour " + mineName + ": " +
                        teleportX + ", " + teleportY + ", " + teleportZ);
            } else {
                teleportX = (minPos.x() + maxPos.x()) / 2.0;
                teleportY = maxPos.y() + 1;
                teleportZ = (minPos.z() + maxPos.z()) / 2.0;

                plugin.getPluginLogger().debug("Téléportation automatique pour " + mineName + ": " +
                        teleportX + ", " + teleportY + ", " + teleportZ);
            }

            // Création de l'objet MineData avec les nouvelles coordonnées
            return new MineData(
                    mineName, displayName, description, type, worldName,
                    minPos, maxPos, blockComposition, resetInterval,
                    teleportX, teleportY, teleportZ, teleportYaw, teleportPitch
            );

        } catch (Exception e) {
            plugin.getPluginLogger().severe("Erreur lors du chargement de la mine " + mineName + ":");
            e.printStackTrace();
            throw new RuntimeException("Échec du chargement de la mine " + mineName, e);
        }
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
     * Charge la configuration des warps
     */
    private void loadWarpsConfiguration() {
        plugin.getPluginLogger().debug("Chargement des warps...");

        warpsData = new HashMap<>();

        ConfigurationSection warpsSection = config.getConfigurationSection("warps");
        if (warpsSection == null) {
            plugin.getPluginLogger().info("§7Aucune section 'warps' trouvée dans la config - génération automatique seulement");
            return;
        }

        Set<String> warpNames = warpsSection.getKeys(false);
        plugin.getPluginLogger().debug("Warps trouvés dans la config: " + warpNames);

        for (String warpName : warpNames) {
            try {
                plugin.getPluginLogger().debug("Chargement du warp: " + warpName);

                ConfigurationSection warpSection = warpsSection.getConfigurationSection(warpName);
                if (warpSection == null) {
                    plugin.getPluginLogger().warning("§cSection warp invalide pour: " + warpName);
                    continue;
                }

                WarpData warpData = loadSingleWarp(warpName, warpSection);
                warpsData.put(warpName, warpData);

                plugin.getPluginLogger().info("§aWarp '" + warpName + "' chargé: " +
                        warpData.getType().getDisplayName() + " vers " + warpData.getWorldName());

            } catch (Exception e) {
                plugin.getPluginLogger().severe("§cErreur lors du chargement du warp '" + warpName + "':");
                e.printStackTrace();
            }
        }

        if (warpsData.isEmpty()) {
            plugin.getPluginLogger().info("§7Aucun warp configuré manuellement - utilisation de la génération automatique");
        }
    }

    /**
     * Charge un seul warp depuis la configuration
     */
    private WarpData loadSingleWarp(String warpId, ConfigurationSection section) {
        // Informations de base
        String displayName = section.getString("display-name", warpId);

        // Type de warp
        String typeString = section.getString("type", "OTHER").toUpperCase();
        WarpData.WarpType type;
        try {
            type = WarpData.WarpType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            plugin.getPluginLogger().warning("§cType de warp invalide '" + typeString + "' pour " + warpId + ", utilisation de OTHER");
            type = WarpData.WarpType.OTHER;
        }

        // Position
        String worldName = section.getString("world");
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("Monde non spécifié pour le warp " + warpId);
        }

        double x = section.getDouble("x", 0);
        double y = section.getDouble("y", 100);
        double z = section.getDouble("z", 0);
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);

        // Permission
        String permission = section.getString("permission", null);

        // Tête personnalisée
        String headMaterialString = section.getString("head-material", "PLAYER_HEAD");
        Material headMaterial;
        try {
            headMaterial = Material.valueOf(headMaterialString.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getPluginLogger().warning("§cMatériau de tête invalide '" + headMaterialString + "' pour " + warpId);
            headMaterial = Material.PLAYER_HEAD;
        }

        String headTexture = section.getString("head-texture", null);

        // Autres propriétés
        boolean enabled = section.getBoolean("enabled", true);
        String description = section.getString("description", "");

        return new WarpData(
                warpId, displayName, type, worldName,
                x, y, z, yaw, pitch,
                permission, headMaterial, headTexture,
                enabled, description
        );
    }

    /**
     * Obtient tous les warps configurés
     */
    public Map<String, WarpData> getAllWarps() {
        return new HashMap<>(warpsData);
    }

    /**
     * Obtient un warp par son ID
     */
    public WarpData getWarp(String warpId) {
        return warpsData.get(warpId);
    }

    /**
     * NOUVEAU : Obtient le prix de vente d'un matériau
     */
    public long getSellPrice(Material material) {
        return sellPrices.getOrDefault(material, 0L);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String path, T def) {
        return (T) config.get(path, def);
    }

    // ==================== OVERLOAD (Surcharge de mine) ====================

    public double[] getOverloadThresholds() {
        java.util.List<Double> list = (java.util.List<Double>) config.getList("overload.thresholds",
                java.util.Arrays.asList(0.0, 0.20, 0.40, 0.60, 0.80, 1.00));
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    public double[] getOverloadMultipliers() {
        java.util.List<Double> list = (java.util.List<Double>) config.getList("overload.multipliers",
                java.util.Arrays.asList(1.00, 1.10, 1.25, 1.50, 1.75, 2.00));
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    public double getOverloadDecayPerSecond() {
        return config.getDouble("overload.decay-per-second", 0.02);
    }

    public long getOverloadActiveWindowMs() {
        return config.getLong("overload.active-window-ms", 3000L);
    }

    public int getOverloadHologramRefreshTicks() {
        return config.getInt("overload.hologram-refresh-ticks", 100);
    }

    /**
     * Recharge la configuration depuis le fichier
     */
    public void reloadConfiguration() {
        loadConfiguration();
        plugin.getPluginLogger().info("§aConfiguration rechargée!");
    }
}