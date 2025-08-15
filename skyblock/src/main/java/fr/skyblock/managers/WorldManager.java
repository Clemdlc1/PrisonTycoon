package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.mvplugins.multiverse.core.locale.message.Message;
import org.mvplugins.multiverse.core.utils.result.Attempt;
import org.mvplugins.multiverse.core.world.LoadedMultiverseWorld;
import org.mvplugins.multiverse.core.world.MultiverseWorld;
import org.mvplugins.multiverse.core.world.options.CreateWorldOptions;
import org.mvplugins.multiverse.core.world.options.DeleteWorldOptions;
import org.mvplugins.multiverse.core.world.reasons.CreateFailureReason;
import org.mvplugins.multiverse.core.world.reasons.DeleteFailureReason;
import org.mvplugins.multiverse.external.jetbrains.annotations.NotNull;
import org.mvplugins.multiverse.external.vavr.control.Option;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class WorldManager {

    private final CustomSkyblock plugin;
    private final Map<UUID, String> islandWorlds = new ConcurrentHashMap<>();
    private final String worldPrefix = "island_";
    // Sous-dossier pour stocker physiquement les mondes d'îles (ex: islands/island_<uuid>)
    private final String islandsFolder;
    // Suivi d'activité et déchargement
    private final Map<String, Long> lastPlayerLeftAt = new ConcurrentHashMap<>();
    private final long unloadAfterMillis;

    public WorldManager(CustomSkyblock plugin) {
        this.plugin = plugin;
        this.islandsFolder = plugin.getConfig().getString("island.worlds-folder", "islands");
        int minutes = plugin.getConfig().getInt("advanced.auto-unload-minutes", 30);
        this.unloadAfterMillis = Math.max(1, minutes) * 60L * 1000L;
        ensureIslandsFolder();
        loadExistingWorlds();
        unloadIdleIslandWorldsOnStartup();
        startAutoUnloadTask();
    }

    private void ensureIslandsFolder() {
        File folder = new File(Bukkit.getWorldContainer(), islandsFolder);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Impossible de créer le dossier des îles: " + folder.getAbsolutePath());
        }
    }

    /**
     * Crée un nouveau monde pour une île
     */
    public World createIslandWorld(Island island) {
        String worldName = islandsFolder + "/" + worldPrefix + island.getId().toString();

        try {
            org.mvplugins.multiverse.core.world.@NotNull WorldManager mvWorldManager = plugin.getMultiverseCoreApi().getWorldManager();

            // CORRECTION : Configuration améliorée pour éviter les erreurs de génération
            CreateWorldOptions options = CreateWorldOptions.worldName(worldName)
                    .environment(World.Environment.NORMAL)
                    .worldType(WorldType.FLAT)
                    .generateStructures(false)
                    .seed(0L); // Seed fixe pour éviter les erreurs

            // CORRECTION : Essayer avec un générateur par défaut d'abord
            String voidGenerator = plugin.getConfig().getString("advanced.void-generator", "VoidWorldGenerator");

            // Vérifier si le générateur existe
            if (plugin.getServer().getPluginManager().getPlugin(voidGenerator) != null) {
                options = options.generator(voidGenerator);
                plugin.getLogger().info("Utilisation du générateur: " + voidGenerator);
            } else {
                plugin.getLogger().warning("Générateur " + voidGenerator + " non trouvé, utilisation du générateur par défaut");
                // Utiliser un générateur plat personnalisé
                options = options.generatorSettings("3;minecraft:air;127;");
            }

            Attempt<LoadedMultiverseWorld, CreateFailureReason> result = mvWorldManager.createWorld(options);

            if (result.isFailure()) {
                Message failureMessage = result.getFailureMessage();
                plugin.getLogger().severe("Échec de création du monde " + worldName + ": " + failureMessage);
                return null;
            }

            // Récupérer le monde créé
            LoadedMultiverseWorld loadedWorld = result.get();
            World world = loadedWorld.getBukkitWorld().getOrNull();

            if (world == null) {
                plugin.getLogger().severe("Monde " + worldName + " créé mais introuvable !");
                return null;
            }

            // CORRECTION : Configuration du monde améliorée
            setupWorldSettings(world);
            islandWorlds.put(island.getId(), worldName);

            // Mettre à jour la location du centre avec le vrai monde
            Location newCenter = new Location(world, 0, 64, 0);
            island.setCenter(newCenter);

            plugin.getLogger().info("Monde créé avec succès pour l'île " + island.getId() + " : " + worldName);

            // CORRECTION : Nettoyer le terrain et préparer le spawn
            prepareWorldForIsland(world);

            // Désactiver l'auto-load pour ce monde dans Multiverse
            try {
                mvWorldManager.getWorld(world.getName()).peek(mvWorld -> mvWorld.setAutoLoad(false));
            } catch (Exception ignored) {
            }

            return world;

        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la création du monde " + worldName + " : " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Supprime le monde d'une île
     */
    public boolean deleteIslandWorld(Island island) {
        String worldName = islandWorlds.get(island.getId());
        if (worldName == null) {
            plugin.getLogger().warning("Aucun monde trouvé pour l'île " + island.getId());
            return false;
        }

        org.mvplugins.multiverse.core.world.@NotNull WorldManager mvWorldManager = plugin.getMultiverseCoreApi().getWorldManager();

        Option<MultiverseWorld> mvWorldOption = mvWorldManager.getWorld(worldName);
        if (mvWorldOption.isEmpty()) {
            plugin.getLogger().warning("Le monde '" + worldName + "' n'est pas connu de Multiverse. Nettoyage de l'entrée invalide.");
            islandWorlds.remove(island.getId());
            return false;
        }
        MultiverseWorld mvWorld = mvWorldOption.get();

        // CORRECTION : Vérifier si le monde est chargé avant de tenter d'accéder aux joueurs.
        if (mvWorld.isLoaded()) {
            // Obtenir la version CHARGÉE du monde pour pouvoir accéder au monde Bukkit.
            Option<LoadedMultiverseWorld> loadedWorldOption = mvWorldManager.getLoadedWorld(mvWorld);
            if (loadedWorldOption.isDefined()) {
                // L'objet `loadedWorld` a accès au monde Bukkit.
                LoadedMultiverseWorld loadedWorld = loadedWorldOption.get();
                // getBukkitWorld() retourne une Option<World>, nous utilisons peek pour agir si elle est présente.
                loadedWorld.getBukkitWorld().peek(bukkitWorld -> {
                    // Maintenant nous avons l'objet World de Bukkit et nous pouvons appeler getPlayers()
                    for (Player player : bukkitWorld.getPlayers()) {
                        Location spawnLocation = Bukkit.getWorlds().getFirst().getSpawnLocation();
                        player.teleport(spawnLocation);
                        player.sendMessage("§cVous avez été téléporté car le monde de l'île a été supprimé.");
                    }
                });
            }
        }

        try {
            DeleteWorldOptions options = DeleteWorldOptions.world(mvWorld);
            Attempt<String, DeleteFailureReason> result = mvWorldManager.deleteWorld(options);

            if (result.isSuccess()) {
                islandWorlds.remove(island.getId());
                plugin.getLogger().info("Monde " + worldName + " et ses fichiers supprimés avec succès via Multiverse.");
                return true;
            } else {
                Message failureMessage = result.getFailureMessage();
                plugin.getLogger().severe("Échec de suppression du monde " + worldName + " : " + failureMessage);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Erreur lors de la suppression du monde " + worldName + " : " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Obtient le monde d'une île
     */
    public World getIslandWorld(Island island) {
        String worldName = islandWorlds.get(island.getId());
        if (worldName == null) {
            return null;
        }
        return Bukkit.getWorld(worldName);
    }

    /**
     * Obtient le nom du monde d'une île
     */
    public String getIslandWorldName(Island island) {
        return islandWorlds.get(island.getId());
    }

    /**
     * Vérifie si un monde appartient à une île
     */
    public boolean isIslandWorld(String worldName) {
        return worldName.startsWith(islandsFolder + "/" + worldPrefix) || worldName.startsWith(worldPrefix);
    }

    /**
     * Obtient l'ID de l'île à partir du nom du monde
     */
    public UUID getIslandIdFromWorldName(String worldName) {
        if (!isIslandWorld(worldName)) {
            return null;
        }

        try {
            String plain = worldName.contains("/") ? worldName.substring(worldName.lastIndexOf('/') + 1) : worldName;
            String uuidString = plain.substring(worldPrefix.length());
            return UUID.fromString(uuidString);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Nom de monde d'île invalide : " + worldName);
            return null;
        }
    }

    /**
     * Met à jour la world border d'une île
     */
    public void updateWorldBorder(Island island) {
        World world = getIslandWorld(island);
        if (world == null) return;

        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(island.getCenter().getX(), island.getCenter().getZ());
        border.setSize(island.getSize());
    }

    /**
     * Charge les mondes existants au démarrage
     */
    private void loadExistingWorlds() {
        // Parcourir tous les mondes chargés pour trouver les mondes d'îles
        for (World world : Bukkit.getWorlds()) {
            String worldName = world.getName();
            if (isIslandWorld(worldName)) {
                UUID islandId = getIslandIdFromWorldName(worldName);
                if (islandId != null) {
                    islandWorlds.put(islandId, worldName);
                    plugin.getLogger().info("Monde d'île chargé : " + worldName + " -> " + islandId);
                }
            }
        }

        // Vérifier les mondes dans Multiverse
        if (plugin.getMultiverseCoreApi() != null) {
            org.mvplugins.multiverse.core.world.@NotNull WorldManager mvWorldManager = plugin.getMultiverseCoreApi().getWorldManager();
            for (MultiverseWorld mvWorld : mvWorldManager.getWorlds()) {
                String worldName = mvWorld.getName();
                if (isIslandWorld(worldName) && !islandWorlds.containsValue(worldName)) {
                    UUID islandId = getIslandIdFromWorldName(worldName);
                    if (islandId != null) {
                        islandWorlds.put(islandId, worldName);
                        // Désactiver l'autoload pour ne pas charger au démarrage
                        mvWorld.setAutoLoad(false);
                        plugin.getLogger().info("Monde d'île référencé (autoload off) : " + worldName + " -> " + islandId);
                    }
                }
            }
        }

        plugin.getLogger().info("Chargement terminé : " + islandWorlds.size() + " mondes d'îles trouvés");
    }

    /**
     * Supprime physiquement les fichiers d'un monde
     */
    private void deleteWorldFiles(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists() && worldFolder.isDirectory()) {
            try {
                deleteDirectory(worldFolder);
                plugin.getLogger().info("Fichiers du monde " + worldName + " supprimés");
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la suppression des fichiers du monde " + worldName + " : " + e.getMessage());
            }
        }
    }

    /**
     * Supprime récursivement un dossier
     */
    private boolean deleteDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return false;
        }

        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }

        return directory.delete();
    }

    /**
     * Obtient le nombre de mondes d'îles
     */
    public int getIslandWorldCount() {
        return islandWorlds.size();
    }

    /**
     * Nettoie les références aux mondes supprimés
     */
    public void cleanupMissingWorlds() {
        islandWorlds.entrySet().removeIf(entry -> {
            String worldName = entry.getValue();
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().info("Nettoyage de la référence au monde manquant : " + worldName);
                return true;
            }
            return false;
        });
    }

    /**
     * Sauvegarde tous les mondes d'îles
     */
    public void saveAllIslandWorlds() {
        for (String worldName : islandWorlds.values()) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.save();
            }
        }
    }

    /**
     * Vérifie si une île a un monde associé
     */
    public boolean hasWorld(Island island) {
        return islandWorlds.containsKey(island.getId()) &&
                Bukkit.getWorld(islandWorlds.get(island.getId())) != null;
    }

    /**
     * Récupère le monde d'une île ou le crée s'il n'existe pas
     */
    public World getOrCreateIslandWorld(Island island) {
        World world = getIslandWorld(island);
        if (world != null) return world;

        // Si un dossier de monde existe, tenter import/chargement via Multiverse plutôt que recréer
        String worldName = islandsFolder + "/" + worldPrefix + island.getId();
        try {
            org.mvplugins.multiverse.core.world.@NotNull WorldManager mvWorldManager = plugin.getMultiverseCoreApi().getWorldManager();
            Option<MultiverseWorld> mvWorldOpt = mvWorldManager.getWorld(worldName);
            if (mvWorldOpt.isDefined()) {
                // Charger si déchargé
                Attempt<org.mvplugins.multiverse.core.world.LoadedMultiverseWorld, org.mvplugins.multiverse.core.world.reasons.LoadFailureReason> loadAttempt =
                        mvWorldManager.loadWorld(mvWorldOpt.get());
                if (loadAttempt.isFailure()) {
                    plugin.getLogger().warning("Échec du chargement du monde existant " + worldName + " : " + loadAttempt.getFailureMessage());
                }
            } else {
                // Importer un monde existant si le dossier existe
                File folder = new File(Bukkit.getWorldContainer(), worldName);
                if (folder.exists()) {
                    Attempt<org.mvplugins.multiverse.core.world.LoadedMultiverseWorld, org.mvplugins.multiverse.core.world.reasons.ImportFailureReason> importAttempt =
                            mvWorldManager.importWorld(org.mvplugins.multiverse.core.world.options.ImportWorldOptions.worldName(worldName));
                    if (importAttempt.isFailure()) {
                        plugin.getLogger().warning("Échec de l'import du monde existant " + worldName + " : " + importAttempt.getFailureMessage());
                    }
                } else {
                    // Créer si aucun dossier
                    createIslandWorld(island);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la (re)mise à disposition du monde d'île: " + e.getMessage());
        }

        return getIslandWorld(island);
    }

    /**
     * Configure les paramètres d'un monde d'île
     */
    private void setupWorldSettings(World world) {
        // Paramètres de base
        world.setDifficulty(org.bukkit.Difficulty.NORMAL);
        world.setSpawnLocation(0, 64, 0);
        world.setKeepSpawnInMemory(false);
        world.setAutoSave(true);

        // Règles de jeu
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, true);
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false);
        world.setGameRule(org.bukkit.GameRule.MOB_GRIEFING, false);
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, true);
        world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 3);

        // CORRECTION : Désactiver les spawns naturels pour éviter les problèmes
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.SPAWN_RADIUS, 0);

        // World border par défaut
        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(100); // Taille par défaut
        border.setWarningDistance(5);
        border.setWarningTime(15);
        border.setDamageAmount(0.2);
        border.setDamageBuffer(5);

        plugin.getLogger().info("Configuration du monde terminée: " + world.getName());
    }

    /**
     * AJOUT : Prépare le monde pour l'île en créant une plateforme de base
     */
    private void prepareWorldForIsland(World world) {
        try {
            // Créer une petite plateforme de bedrock pour éviter les problèmes de spawn
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    world.getBlockAt(x, 63, z).setType(Material.BEDROCK);
                }
            }

            // Placer un bloc de spawn sécurisé
            world.getBlockAt(0, 64, 0).setType(Material.STONE);

            plugin.getLogger().info("Plateforme de base créée pour le monde: " + world.getName());

        } catch (Exception e) {
            plugin.getLogger().warning("Impossible de créer la plateforme de base: " + e.getMessage());
        }
    }

    // ==== Gestion du déchargement automatique ====

    public void markPlayerLeft(String worldName) {
        if (isIslandWorld(worldName)) {
            lastPlayerLeftAt.put(worldName, System.currentTimeMillis());
        }
    }

    public void markPlayerEntered(String worldName) {
        if (isIslandWorld(worldName)) {
            lastPlayerLeftAt.remove(worldName);
        }
    }

    private void startAutoUnloadTask() {
        // Vérifie toutes les minutes et décharge les mondes d'îles inactifs depuis 30 minutes
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                org.mvplugins.multiverse.core.world.@NotNull WorldManager mvWorldManager = plugin.getMultiverseCoreApi().getWorldManager();
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, String> entry : islandWorlds.entrySet()) {
                    String worldName = entry.getValue();
                    World bukkitWorld = Bukkit.getWorld(worldName);
                    if (bukkitWorld == null) continue; // déjà déchargé
                    if (!bukkitWorld.getPlayers().isEmpty()) continue; // encore utilisé

                    long last = lastPlayerLeftAt.getOrDefault(worldName, now);
                    // si pas de timestamp, initialise maintenant pour laisser 30min
                    if (!lastPlayerLeftAt.containsKey(worldName)) {
                        lastPlayerLeftAt.put(worldName, now);
                        continue;
                    }

                    if (now - last >= unloadAfterMillis) {
                        // Sauvegarder l'île avant de décharger le monde
                        UUID islandId = entry.getKey();
                        Island island = plugin.getDatabaseManager().loadIsland(islandId);
                        if (island != null) {
                            plugin.getDatabaseManager().forceSaveIsland(island);
                            plugin.getLogger().info("Île sauvegardée avant déchargement: " + islandId);
                        }
                        
                        // Décharger via Multiverse (sauvegarde true)
                        mvWorldManager.getLoadedWorld(bukkitWorld).peek(loaded -> {
                            mvWorldManager.unloadWorld(org.mvplugins.multiverse.core.world.options.UnloadWorldOptions
                                    .world(loaded)
                                    .saveBukkitWorld(true)
                                    .unloadBukkitWorld(true));
                            plugin.getLogger().info("Monde d'île déchargé pour inactivité: " + worldName);
                        });
                        lastPlayerLeftAt.remove(worldName);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur tâche déchargement auto: " + e.getMessage());
            }
        }, 20L * 60L, 20L * 60L);
    }

    private void unloadIdleIslandWorldsOnStartup() {
        try {
            org.mvplugins.multiverse.core.world.@NotNull WorldManager mvWorldManager = plugin.getMultiverseCoreApi().getWorldManager();
            for (World world : Bukkit.getWorlds()) {
                String worldName = world.getName();
                if (!isIslandWorld(worldName)) continue;
                if (!world.getPlayers().isEmpty()) continue;

                // Sauvegarder l'île avant de décharger le monde
                UUID islandId = getIslandIdFromWorldName(worldName);
                if (islandId != null) {
                    Island island = plugin.getDatabaseManager().loadIsland(islandId);
                    if (island != null) {
                        plugin.getDatabaseManager().forceSaveIsland(island);
                        plugin.getLogger().info("Île sauvegardée avant déchargement au démarrage: " + islandId);
                    }
                }

                mvWorldManager.getLoadedWorld(world).peek(loaded -> {
                    mvWorldManager.unloadWorld(org.mvplugins.multiverse.core.world.options.UnloadWorldOptions
                            .world(loaded)
                            .saveBukkitWorld(true)
                            .unloadBukkitWorld(true));
                    plugin.getLogger().info("Monde d'île déchargé au démarrage (sans joueurs): " + worldName);
                });
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors du déchargement initial des mondes d'îles: " + e.getMessage());
        }
    }
}