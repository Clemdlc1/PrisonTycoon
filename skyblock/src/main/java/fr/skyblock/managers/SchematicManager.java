package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class SchematicManager {

    private final CustomSkyblock plugin;
    private final File schematicsFolder;
    private final Map<String, SchematicData> loadedSchematics = new HashMap<>();

    public SchematicManager(CustomSkyblock plugin) {
        this.plugin = plugin;
        this.schematicsFolder = new File(plugin.getDataFolder(), "schematics");

        setupSchematicsFolder();
        loadDefaultSchematics();
        loadCustomSchematics();
    }

    private void setupSchematicsFolder() {
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
            plugin.getLogger().info("Dossier schematics créé: " + schematicsFolder.getPath());
        }
    }

    // === CHARGEMENT DES SCHÉMATICS ===

    private void loadDefaultSchematics() {
        // Schematic classique
        createDefaultSchematic("classic", "Île Classique", Material.GRASS_BLOCK,
                Arrays.asList("Une île traditionnelle avec de l'herbe", "et un arbre de chêne."),
                createClassicIslandStructure());

        // Schematic désert
        createDefaultSchematic("desert", "Île Désertique", Material.SAND,
                Arrays.asList("Une île de sable avec un cactus", "et un puits d'eau."),
                createDesertIslandStructure());

        // Schematic jungle
        createDefaultSchematic("jungle", "Île Jungle", Material.JUNGLE_LOG,
                Arrays.asList("Une île tropicale avec de la végétation", "luxuriante et des ressources exotiques."),
                createJungleIslandStructure());

        // Schematic neige
        createDefaultSchematic("snow", "Île Enneigée", Material.SNOW_BLOCK,
                Arrays.asList("Une île glacée avec de la neige", "et des ressources arctiques."),
                createSnowIslandStructure());

        // Schematic champignon
        createDefaultSchematic("mushroom", "Île Champignon", Material.MYCELIUM,
                Arrays.asList("Une île mystérieuse couverte de mycélium", "avec des champignons géants."),
                createMushroomIslandStructure());
    }

    private void loadCustomSchematics() {
        File customSchematicsFile = new File(schematicsFolder, "custom_schematics.yml");

        if (!customSchematicsFile.exists()) {
            createCustomSchematicsFile(customSchematicsFile);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(customSchematicsFile);

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section != null) {
                try {
                    SchematicData schematic = SchematicData.fromConfig(key, section);
                    loadedSchematics.put(key, schematic);
                    plugin.getLogger().info("Schematic personnalisé chargé: " + key);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement du schematic " + key + ": " + e.getMessage());
                }
            }
        }
    }

    private void createCustomSchematicsFile(File file) {
        YamlConfiguration config = new YamlConfiguration();

        // Exemple de schematic personnalisé
        config.set("nether.name", "Île du Nether");
        config.set("nether.material", "NETHERRACK");
        config.set("nether.description", Arrays.asList("Une île infernale avec de la netherrack", "et des ressources du Nether."));
        config.set("nether.biome", "NETHER_WASTES");
        config.set("nether.structure", Arrays.asList(
                "  nnn  ",
                " nnnnn ",
                "nnnnnnn",
                "nnnlnnn",
                "nnnnnnn",
                " nnnnn ",
                "  nnn  "
        ));
        config.set("nether.materials.n", "NETHERRACK");
        config.set("nether.materials.l", "LAVA");
        config.set("nether.chest_items", Arrays.asList(
                "FIRE_CHARGE:3",
                "FLINT_AND_STEEL:1",
                "BREAD:5",
                "NETHER_WART:2"
        ));

        try {
            config.save(file);
            plugin.getLogger().info("Fichier custom_schematics.yml créé avec des exemples.");
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de créer custom_schematics.yml: " + e.getMessage());
        }
    }

    // === CRÉATION D'ÎLES AVEC SCHÉMATICS ===

    public Island createIslandWithSchematic(Player player, String schematicName) {
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().getOrCreatePlayer(player.getUniqueId(), player.getName());

        if (skyblockPlayer.hasIsland()) {
            player.sendMessage(ChatColor.RED + "Vous avez déjà une île !");
            return null;
        }

        SchematicData schematic = loadedSchematics.get(schematicName);
        if (schematic == null) {
            player.sendMessage(ChatColor.RED + "Schematic introuvable: " + schematicName);
            return null;
        }

        // Créer l'île avec une location temporaire
        UUID islandId = UUID.randomUUID();
        Location tempLocation = new Location(Bukkit.getWorlds().get(0), 0, 64, 0);
        Island island = new Island(islandId, player.getUniqueId(), player.getName() + "'s Island", tempLocation);

        // Créer le monde dédié pour cette île
        World islandWorld = plugin.getWorldManager().createIslandWorld(island);
        if (islandWorld == null) {
            player.sendMessage(ChatColor.RED + "Impossible de créer le monde pour votre île !");
            return null;
        }

        // CORRECTION : Sauvegarder l'île EN PREMIER avant le joueur
        plugin.getDatabaseManager().saveIsland(island);
        plugin.getLogger().info("Île sauvegardée avant mise à jour du joueur : " + island.getId());

        // Maintenant mettre à jour le joueur
        skyblockPlayer.createIsland(islandId);
        plugin.getDatabaseManager().savePlayer(skyblockPlayer);
        plugin.getLogger().info("Joueur mis à jour avec l'île : " + player.getName());

        // Générer l'île avec le schematic
        generateIslandFromSchematic(island, schematic, player);

        // Téléporter le joueur
        new BukkitRunnable() {
            @Override
            public void run() {
                if (plugin.getIslandManager().teleportToIsland(player, island)) {
                    player.sendMessage(ChatColor.GREEN + "Votre île " + schematic.getName() + " a été créée avec succès !");
                    player.sendMessage(ChatColor.GOLD + "Bienvenue sur votre nouvelle île !");

                    // Donner des récompenses de départ
                    if (plugin.getPrisonTycoonHook().isEnabled()) {
                        plugin.getPrisonTycoonHook().addTokens(player.getUniqueId(), 10);
                        plugin.getPrisonTycoonHook().addBeacons(player.getUniqueId(), 5);
                        player.sendMessage(ChatColor.LIGHT_PURPLE + "Récompenses reçues: 10 tokens, 5 beacons !");
                    } else {
                        plugin.getEconomyManager().rewardPlayer(player.getUniqueId(), 100.0, "Création d'île");
                        player.sendMessage(ChatColor.GOLD + "Récompense reçue: 100$ !");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Erreur lors de la téléportation ! Utilisez /is home");
                }
            }
        }.runTaskLater(plugin, 20L); // Attendre 1 seconde

        return island;
    }

    private void generateIslandFromSchematic(Island island, SchematicData schematic, Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Location center = island.getCenter();
                World world = center.getWorld();
                if (world == null) return;

                // Nettoyer la zone
                clearArea(center, 20);

                // Générer la structure principale
                generateStructure(center, schematic);

                // Placer les objets spéciaux
                generateSpecialItems(center, schematic);

                // Définir le biome
                if (schematic.getBiome() != null) {
                    setBiome(center, island.getSize(), schematic.getBiome());
                }

                player.sendMessage(ChatColor.GREEN + "Structure de l'île générée !");
            }
        }.runTaskLater(plugin, 5L);
    }

    private void generateStructure(Location center, SchematicData schematic) {
        World world = center.getWorld();
        if (world == null) return;

        List<String> structure = schematic.getStructure();
        Map<Character, Material> materials = schematic.getMaterials();

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Couches de base (bedrock et stone)
        for (int y = centerY - 4; y < centerY; y++) {
            for (int x = centerX - 3; x <= centerX + 3; x++) {
                for (int z = centerZ - 3; z <= centerZ + 3; z++) {
                    if (y == centerY - 4) {
                        world.getBlockAt(x, y, z).setType(Material.BEDROCK);
                    } else {
                        world.getBlockAt(x, y, z).setType(Material.STONE);
                    }
                }
            }
        }

        // Structure principale
        for (int i = 0; i < structure.size(); i++) {
            String row = structure.get(i);
            for (int j = 0; j < row.length(); j++) {
                char blockChar = row.charAt(j);
                if (blockChar != ' ') {
                    Material material = materials.get(blockChar);
                    if (material != null) {
                        int x = centerX + j - (row.length() / 2);
                        int z = centerZ + i - (structure.size() / 2);

                        // Couche de surface
                        world.getBlockAt(x, centerY, z).setType(material);

                        // Couche de base si nécessaire
                        if (material == Material.GRASS_BLOCK || material.name().contains("DIRT")) {
                            world.getBlockAt(x, centerY - 1, z).setType(Material.DIRT);
                        }
                    }
                }
            }
        }
    }

    private void generateSpecialItems(Location center, SchematicData schematic) {
        // Arbre ou végétation selon le type
        switch (schematic.getId()) {
            case "classic" -> generateOakTree(center.clone().add(2, 1, 2));
            case "desert" -> generateCactus(center.clone().add(-2, 1, -2));
            case "jungle" -> generateJungleTree(center.clone().add(2, 1, 2));
            case "snow" -> generateSpruceTree(center.clone().add(2, 1, 2));
            case "mushroom" -> generateGiantMushroom(center.clone().add(2, 1, 2));
        }

        // Coffre avec objets de départ
        generateStarterChest(center.clone().add(-2, 1, -2), schematic.getChestItems());
    }

    // === GÉNÉRATION D'ARBRES ET VÉGÉTATION ===

    private void generateOakTree(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Tronc
        for (int i = 0; i < 4; i++) {
            world.getBlockAt(location.getBlockX(), location.getBlockY() + i, location.getBlockZ())
                    .setType(Material.OAK_LOG);
        }

        // Feuilles
        generateLeaves(location.clone().add(0, 3, 0), Material.OAK_LEAVES, 2);
    }

    private void generateJungleTree(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Tronc plus grand
        for (int i = 0; i < 6; i++) {
            world.getBlockAt(location.getBlockX(), location.getBlockY() + i, location.getBlockZ())
                    .setType(Material.JUNGLE_LOG);
        }

        // Feuilles de jungle
        generateLeaves(location.clone().add(0, 5, 0), Material.JUNGLE_LEAVES, 3);

        // Quelques lianes
        generateVines(location);
    }

    private void generateSpruceTree(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Tronc
        for (int i = 0; i < 5; i++) {
            world.getBlockAt(location.getBlockX(), location.getBlockY() + i, location.getBlockZ())
                    .setType(Material.SPRUCE_LOG);
        }

        // Feuilles en forme de sapin
        generateSpruceLeaves(location.clone().add(0, 4, 0));
    }

    private void generateCactus(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Placer du sable sous le cactus
        world.getBlockAt(location.getBlockX(), location.getBlockY() - 1, location.getBlockZ())
                .setType(Material.SAND);

        // Cactus de 3 blocs
        for (int i = 0; i < 3; i++) {
            world.getBlockAt(location.getBlockX(), location.getBlockY() + i, location.getBlockZ())
                    .setType(Material.CACTUS);
        }
    }

    private void generateGiantMushroom(Location location) {
        World world = location.getWorld();
        if (world == null) return;

        // Tige
        for (int i = 0; i < 3; i++) {
            world.getBlockAt(location.getBlockX(), location.getBlockY() + i, location.getBlockZ())
                    .setType(Material.MUSHROOM_STEM);
        }

        // Chapeau de champignon
        generateMushroomCap(location.clone().add(0, 3, 0));
    }

    private void generateLeaves(Location center, Material leafType, int radius) {
        World world = center.getWorld();
        if (world == null) return;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -1; y <= 1; y++) {
                    double distance = Math.sqrt(x * x + z * z);
                    if (distance <= radius && (x != 0 || z != 0 || y != 0)) {
                        Block block = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z);
                        if (block.getType() == Material.AIR) {
                            block.setType(leafType);
                        }
                    }
                }
            }
        }
    }

    private void generateSpruceLeaves(Location top) {
        World world = top.getWorld();
        if (world == null) return;

        // Forme de sapin (pyramide)
        for (int layer = 0; layer <= 2; layer++) {
            int radius = layer + 1;
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) == radius || Math.abs(z) == radius) {
                        Block block = world.getBlockAt(top.getBlockX() + x, top.getBlockY() - layer, top.getBlockZ() + z);
                        if (block.getType() == Material.AIR) {
                            block.setType(Material.SPRUCE_LEAVES);
                        }
                    }
                }
            }
        }
    }

    private void generateVines(Location treeLocation) {
        World world = treeLocation.getWorld();
        if (world == null) return;

        Random random = new Random();

        // Quelques lianes aléatoires autour de l'arbre
        for (int i = 0; i < 3; i++) {
            int x = treeLocation.getBlockX() + random.nextInt(5) - 2;
            int z = treeLocation.getBlockZ() + random.nextInt(5) - 2;
            int y = treeLocation.getBlockY() + 2 + random.nextInt(3);

            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.AIR) {
                block.setType(Material.VINE);
            }
        }
    }

    private void generateMushroomCap(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Chapeau de champignon 3x3
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.getBlockAt(center.getBlockX() + x, center.getBlockY(), center.getBlockZ() + z)
                        .setType(Material.RED_MUSHROOM_BLOCK);
            }
        }
    }

    private void generateStarterChest(Location location, List<String> items) {
        World world = location.getWorld();
        if (world == null) return;

        world.getBlockAt(location).setType(Material.CHEST);

        new BukkitRunnable() {
            @Override
            public void run() {
                Block block = world.getBlockAt(location);
                if (block.getState() instanceof Chest chest) {
                    for (String itemString : items) {
                        try {
                            String[] parts = itemString.split(":");
                            Material material = Material.valueOf(parts[0]);
                            int amount = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;

                            chest.getInventory().addItem(new ItemStack(material, amount));
                        } catch (Exception e) {
                            plugin.getLogger().warning("Objet invalide dans le coffre: " + itemString);
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 2L);
    }

    // === MÉTHODES UTILITAIRES ===

    private void clearArea(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) return;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -5; y <= 20; y++) {
                for (int z = -radius; z <= radius; z++) {
                    world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y, center.getBlockZ() + z)
                            .setType(Material.AIR);
                }
            }
        }
    }

    private void setBiome(Location center, int size, Biome biome) {
        World world = center.getWorld();
        if (world == null) return;

        int radius = size / 2;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.setBiome(center.getBlockX() + x, center.getBlockZ() + z, biome);
            }
        }
    }

    // === GETTERS PUBLICS ===

    public List<String> getAvailableSchematics() {
        return new ArrayList<>(loadedSchematics.keySet());
    }

    public Map<String, Object> getSchematicData(String schematicId) {
        SchematicData schematic = loadedSchematics.get(schematicId);
        if (schematic == null) return new HashMap<>();

        Map<String, Object> data = new HashMap<>();
        data.put("name", schematic.getName());
        data.put("material", schematic.getMaterial().name());
        data.put("description", schematic.getDescription());
        return data;
    }

    // === CRÉATION DES STRUCTURES PAR DÉFAUT ===

    private void createDefaultSchematic(String id, String name, Material material, List<String> description, SchematicData data) {
        data.setId(id);
        data.setName(name);
        data.setMaterial(material);
        data.setDescription(description);
        loadedSchematics.put(id, data);
    }

    private SchematicData createClassicIslandStructure() {
        SchematicData schematic = new SchematicData();
        schematic.setStructure(Arrays.asList(
                "  ggg  ",
                " ggggg ",
                "ggggggg",
                "gggdggg",
                "ggggggg",
                " ggggg ",
                "  ggg  "
        ));
        schematic.addMaterial('g', Material.GRASS_BLOCK);
        schematic.addMaterial('d', Material.DIRT);
        schematic.setBiome(Biome.PLAINS);
        schematic.setChestItems(Arrays.asList(
                "ICE:2", "LAVA_BUCKET:1", "MELON_SEEDS:1", "PUMPKIN_SEEDS:1",
                "SUGAR_CANE:1", "BREAD:5", "BONE_MEAL:3"
        ));
        return schematic;
    }

    private SchematicData createDesertIslandStructure() {
        SchematicData schematic = new SchematicData();
        schematic.setStructure(Arrays.asList(
                "  sss  ",
                " sssss ",
                "sssssss",
                "ssswsss",
                "sssssss",
                " sssss ",
                "  sss  "
        ));
        schematic.addMaterial('s', Material.SAND);
        schematic.addMaterial('w', Material.WATER);
        schematic.setBiome(Biome.DESERT);
        schematic.setChestItems(Arrays.asList(
                "CACTUS:3", "DEAD_BUSH:2", "WATER_BUCKET:1", "WHEAT_SEEDS:2",
                "BREAD:3", "LEATHER:2"
        ));
        return schematic;
    }

    private SchematicData createJungleIslandStructure() {
        SchematicData schematic = new SchematicData();
        schematic.setStructure(Arrays.asList(
                "  ggg  ",
                " ggggg ",
                "ggggggg",
                "gggwggg",
                "ggggggg",
                " ggggg ",
                "  ggg  "
        ));
        schematic.addMaterial('g', Material.GRASS_BLOCK);
        schematic.addMaterial('w', Material.WATER);
        schematic.setBiome(Biome.JUNGLE);
        schematic.setChestItems(Arrays.asList(
                "JUNGLE_SAPLING:2", "COCOA_BEANS:3", "MELON_SEEDS:2", "BAMBOO:3",
                "BREAD:4", "VINE:2"
        ));
        return schematic;
    }

    private SchematicData createSnowIslandStructure() {
        SchematicData schematic = new SchematicData();
        schematic.setStructure(Arrays.asList(
                "  sss  ",
                " sssss ",
                "sssssss",
                "sssisss",
                "sssssss",
                " sssss ",
                "  sss  "
        ));
        schematic.addMaterial('s', Material.SNOW_BLOCK);
        schematic.addMaterial('i', Material.ICE);
        schematic.setBiome(Biome.SNOWY_PLAINS);
        schematic.setChestItems(Arrays.asList(
                "SPRUCE_SAPLING:2", "POTATO:3", "CARROT:3", "LEATHER_BOOTS:1",
                "BREAD:3", "SNOWBALL:8"
        ));
        return schematic;
    }

    private SchematicData createMushroomIslandStructure() {
        SchematicData schematic = new SchematicData();
        schematic.setStructure(Arrays.asList(
                "  mmm  ",
                " mmmmm ",
                "mmmmmmm",
                "mmmwmmm",
                "mmmmmmm",
                " mmmmm ",
                "  mmm  "
        ));
        schematic.addMaterial('m', Material.MYCELIUM);
        schematic.addMaterial('w', Material.WATER);
        schematic.setBiome(Biome.MUSHROOM_FIELDS);
        schematic.setChestItems(Arrays.asList(
                "RED_MUSHROOM:3", "BROWN_MUSHROOM:3", "MUSHROOM_STEW:2",
                "BREAD:3", "MYCELIUM:5"
        ));
        return schematic;
    }

    // === CLASSE INTERNE SCHEMATICDATA ===

    public static class SchematicData {
        private String id;
        private String name;
        private Material material;
        private List<String> description;
        private List<String> structure;
        private Map<Character, Material> materials;
        private Biome biome;
        private List<String> chestItems;

        public SchematicData() {
            this.materials = new HashMap<>();
            this.description = new ArrayList<>();
            this.structure = new ArrayList<>();
            this.chestItems = new ArrayList<>();
        }

        public static SchematicData fromConfig(String id, ConfigurationSection section) {
            SchematicData data = new SchematicData();
            data.setId(id);
            data.setName(section.getString("name", id));
            data.setMaterial(Material.valueOf(section.getString("material", "GRASS_BLOCK")));
            data.setDescription(section.getStringList("description"));
            data.setStructure(section.getStringList("structure"));
            data.setChestItems(section.getStringList("chest_items"));

            String biomeName = section.getString("biome");
            if (biomeName != null) {
                try {
                    data.setBiome(Biome.valueOf(biomeName));
                } catch (IllegalArgumentException e) {
                    // Biome invalide, utiliser par défaut
                    data.setBiome(Biome.PLAINS);
                }
            }

            ConfigurationSection materialsSection = section.getConfigurationSection("materials");
            if (materialsSection != null) {
                for (String key : materialsSection.getKeys(false)) {
                    char character = key.charAt(0);
                    String materialName = materialsSection.getString(key);
                    try {
                        Material material = Material.valueOf(materialName);
                        data.addMaterial(character, material);
                    } catch (IllegalArgumentException e) {
                        // Matériau invalide, ignorer
                    }
                }
            }

            return data;
        }

        // Getters et Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Material getMaterial() { return material; }
        public void setMaterial(Material material) { this.material = material; }

        public List<String> getDescription() { return description; }
        public void setDescription(List<String> description) { this.description = description; }

        public List<String> getStructure() { return structure; }
        public void setStructure(List<String> structure) { this.structure = structure; }

        public Map<Character, Material> getMaterials() { return materials; }
        public void addMaterial(char character, Material material) { this.materials.put(character, material); }

        public Biome getBiome() { return biome; }
        public void setBiome(Biome biome) { this.biome = biome; }

        public List<String> getChestItems() { return chestItems; }
        public void setChestItems(List<String> chestItems) { this.chestItems = chestItems; }
    }
}