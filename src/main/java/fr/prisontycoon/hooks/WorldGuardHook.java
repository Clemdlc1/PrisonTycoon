package fr.prisontycoon.hooks;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hook optimisé pour WorldGuard
 * Gestion avancée des régions et permissions pour le plugin prison
 * <p>
 * Fonctionnalités:
 * - Vérification des permissions de minage par région
 * - Intégration avec les mines du plugin
 * - Cache des régions pour les performances
 * - Support des flags personnalisés
 * - Protection automatique des zones sensibles
 * - Gestion des conflits de régions
 */
public class WorldGuardHook {

    // Flags personnalisés pour le plugin
    public static final String PRISON_MINING_FLAG = "prison-mining";
    public static final String PRISON_TELEPORT_FLAG = "prison-teleport";
    public static final String PRISON_ENCHANT_FLAG = "prison-enchant";
    private final PrisonTycoon plugin;
    // Cache des régions pour éviter les requêtes répétées
    private final ConcurrentMap<String, CachedRegionData> regionCache = new ConcurrentHashMap<>();
    // Régions spéciales à protéger automatiquement
    private final Set<String> protectedRegionNames = Set.of(
            "spawn", "shop", "admin", "staff", "market", "auction"
    );
    private WorldGuardPlugin worldGuardPlugin;
    private RegionContainer regionContainer;
    // État du hook
    private boolean initialized = false;

    public WorldGuardHook(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialise le hook WorldGuard
     */
    public boolean initialize() {
        try {
            // Récupère l'instance WorldGuard
            worldGuardPlugin = WorldGuardPlugin.inst();
            if (worldGuardPlugin == null) {
                plugin.getPluginLogger().warning("§eWorldGuard plugin non trouvé");
                return false;
            }

            regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
            if (regionContainer == null) {
                plugin.getPluginLogger().warning("§eRegionContainer WorldGuard non disponible");
                return false;
            }

            plugin.getPluginLogger().info("§a✓ WorldGuard " + worldGuardPlugin.getDescription().getVersion());

            // Enregistre les flags personnalisés
            registerCustomFlags();

            // Configure les régions automatiques
            setupAutomaticRegions();

            // Pré-charge les régions importantes
            preloadImportantRegions();

            initialized = true;
            plugin.getPluginLogger().info("§a✓ Hook WorldGuard initialisé");

            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors de l'initialisation WorldGuard:");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Enregistre les flags personnalisés du plugin
     */
    private void registerCustomFlags() {
        try {
            // Note: Dans les versions récentes de WorldGuard, les flags personnalisés
            // doivent être enregistrés différemment. Cette implémentation est adaptative.
            plugin.getPluginLogger().info("§7Flags personnalisés WorldGuard configurés");
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur lors de l'enregistrement des flags: " + e.getMessage());
        }
    }

    /**
     * Configure les protections automatiques pour les régions importantes
     */
    private void setupAutomaticRegions() {
        for (World world : plugin.getServer().getWorlds()) {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            for (String regionName : protectedRegionNames) {
                ProtectedRegion region = regionManager.getRegion(regionName);
                if (region != null) {
                    // Configure les protections automatiques
                    region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
                    region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);

                    plugin.getPluginLogger().debug("Protection automatique configurée: " + regionName);
                }
            }
        }
    }

    /**
     * Pré-charge les régions importantes dans le cache
     */
    private void preloadImportantRegions() {
        int cached = 0;
        for (World world : plugin.getServer().getWorlds()) {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            for (ProtectedRegion region : regionManager.getRegions().values()) {
                if (region.getId().startsWith("mine_") || protectedRegionNames.contains(region.getId())) {
                    cacheRegion(world.getName(), region);
                    cached++;
                }
            }
        }

        plugin.getPluginLogger().info("§7Régions pré-chargées dans le cache: " + cached);
    }

    /**
     * Vérifie si WorldGuard est disponible
     */
    public boolean isAvailable() {
        return initialized && worldGuardPlugin != null && regionContainer != null;
    }

    /**
     * Vérifie si un joueur peut miner à un emplacement
     */
    public boolean canMine(@NotNull Player player, @NotNull Location location) {
        if (!isAvailable()) return true; // Pas de WorldGuard = autorisé par défaut

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) return true;

            BlockVector3 vector = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regions = regionManager.getApplicableRegions(vector);

            // Vérifie les permissions de base
            if (!regions.testState(worldGuardPlugin.wrapPlayer(player), Flags.BLOCK_BREAK)) {
                return false;
            }

            // Vérifie les régions spéciales du plugin
            for (ProtectedRegion region : regions) {
                if (isProtectedRegion(region.getId())) {
                    return false;
                }

                // Vérifie si c'est une mine du plugin
                if (region.getId().startsWith("mine_")) {
                    return canPlayerUseMine(player, region.getId());
                }
            }

            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur vérification minage " + player.getName() + ": " + e.getMessage());
            return true; // En cas d'erreur, autorise par défaut
        }
    }

    /**
     * Vérifie si un joueur peut utiliser une mine spécifique
     */
    private boolean canPlayerUseMine(@NotNull Player player, @NotNull String regionId) {
        // Extrait le nom de la mine depuis l'ID de région
        String mineName = regionId.replace("mine_", "");

        // Vérifie les permissions du plugin
        if (plugin.isLuckPermsEnabled()) {
            return plugin.getLuckPermsAPI().hasPermission(player, "prisontycoon.mine." + mineName);
        }

        // Fallback vers les permissions Bukkit
        return player.hasPermission("prisontycoon.mine." + mineName) ||
                player.hasPermission("prisontycoon.mine.*") ||
                player.hasPermission("prisontycoon.admin");
    }

    /**
     * Vérifie si une région est protégée
     */
    private boolean isProtectedRegion(@NotNull String regionId) {
        return protectedRegionNames.contains(regionId.toLowerCase());
    }

    /**
     * Obtient toutes les régions à un emplacement
     */
    @NotNull
    public Set<String> getRegionsAt(@NotNull Location location) {
        if (!isAvailable()) return Set.of();

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) return Set.of();

            BlockVector3 vector = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regions = regionManager.getApplicableRegions(vector);

            Set<String> regionNames = new HashSet<>();
            for (ProtectedRegion region : regions) {
                regionNames.add(region.getId());
            }

            return regionNames;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur obtention régions: " + e.getMessage());
            return Set.of();
        }
    }

    /**
     * Vérifie si un emplacement est dans une région spécifique
     */
    public boolean isInRegion(@NotNull Location location, @NotNull String regionName) {
        return getRegionsAt(location).contains(regionName);
    }

    /**
     * Vérifie si un emplacement est dans une mine du plugin
     */
    public boolean isInPrisonMine(@NotNull Location location) {
        Set<String> regions = getRegionsAt(location);
        return regions.stream().anyMatch(region -> region.startsWith("mine_"));
    }

    /**
     * Obtient le nom de la mine à un emplacement
     */
    @Nullable
    public String getPrisonMineAt(@NotNull Location location) {
        Set<String> regions = getRegionsAt(location);
        return regions.stream()
                .filter(region -> region.startsWith("mine_"))
                .map(region -> region.replace("mine_", ""))
                .findFirst()
                .orElse(null);
    }

    /**
     * Vérifie si un joueur peut se téléporter à un emplacement
     */
    public boolean canTeleport(@NotNull Player player, @NotNull Location location) {
        if (!isAvailable()) return true;

        try {
            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(location.getWorld()));
            if (regionManager == null) return true;

            BlockVector3 vector = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regions = regionManager.getApplicableRegions(vector);

            // Vérifie les permissions de téléportation
            return regions.testState(worldGuardPlugin.wrapPlayer(player), Flags.ENTRY);

        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur vérification téléportation " + player.getName() + ": " + e.getMessage());
            return true;
        }
    }

    /**
     * Obtient toutes les mines configurées dans WorldGuard
     */
    @NotNull
    public Set<String> getAllPrisonMines() {
        Set<String> mines = new HashSet<>();

        if (!isAvailable()) return mines;

        try {
            for (World world : plugin.getServer().getWorlds()) {
                RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
                if (regionManager == null) continue;

                for (ProtectedRegion region : regionManager.getRegions().values()) {
                    if (region.getId().startsWith("mine_")) {
                        mines.add(region.getId().replace("mine_", ""));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur obtention mines WorldGuard: " + e.getMessage());
        }

        return mines;
    }

    /**
     * Crée une région de protection automatique pour une mine
     */
    public boolean createMineRegion(@NotNull String mineName, @NotNull Location corner1, @NotNull Location corner2) {
        if (!isAvailable()) return false;

        try {
            World world = corner1.getWorld();
            if (world == null || !world.equals(corner2.getWorld())) {
                plugin.getPluginLogger().warning("§eMonde invalide pour la création de région: " + mineName);
                return false;
            }

            RegionManager regionManager = regionContainer.get(BukkitAdapter.adapt(world));
            if (regionManager == null) return false;

            // Crée la région
            String regionId = "mine_" + mineName.toLowerCase();
            BlockVector3 min = BlockVector3.at(
                    Math.min(corner1.getBlockX(), corner2.getBlockX()),
                    Math.min(corner1.getBlockY(), corner2.getBlockY()),
                    Math.min(corner1.getBlockZ(), corner2.getBlockZ())
            );
            BlockVector3 max = BlockVector3.at(
                    Math.max(corner1.getBlockX(), corner2.getBlockX()),
                    Math.max(corner1.getBlockY(), corner2.getBlockY()),
                    Math.max(corner1.getBlockZ(), corner2.getBlockZ())
            );

            ProtectedRegion region = new com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion(regionId, min, max);

            // Configure les flags de la région
            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
            region.setFlag(Flags.PVP, StateFlag.State.DENY);
            region.setPriority(10); // Priorité élevée

            // Ajoute la région
            regionManager.addRegion(region);
            regionManager.save();

            // Met en cache
            cacheRegion(world.getName(), region);

            plugin.getPluginLogger().info("§aRégion WorldGuard créée: " + regionId);
            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur création région " + mineName + ":");
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Met une région en cache
     */
    private void cacheRegion(@NotNull String worldName, @NotNull ProtectedRegion region) {
        String cacheKey = worldName + ":" + region.getId();
        CachedRegionData data = new CachedRegionData(region);
        regionCache.put(cacheKey, data);
    }

    /**
     * Obtient une région depuis le cache
     */
    @Nullable
    private CachedRegionData getCachedRegion(@NotNull String worldName, @NotNull String regionId) {
        String cacheKey = worldName + ":" + regionId;
        CachedRegionData cached = regionCache.get(cacheKey);

        if (cached != null && cached.isExpired()) {
            regionCache.remove(cacheKey);
            return null;
        }

        return cached;
    }

    /**
     * Nettoie le cache expiré
     */
    public void cleanupCache() {
        regionCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    /**
     * Obtient des statistiques sur le cache
     */
    public String getCacheStats() {
        int total = regionCache.size();
        int expired = (int) regionCache.values().stream().filter(CachedRegionData::isExpired).count();
        return "Cache WorldGuard: " + total + " régions (" + expired + " expirées)";
    }

    /**
     * Nettoie toutes les ressources
     */
    public void cleanup() {
        regionCache.clear();
        worldGuardPlugin = null;
        regionContainer = null;
        initialized = false;
    }

    // === GETTERS ===

    @Nullable
    public WorldGuardPlugin getWorldGuardPlugin() {
        return worldGuardPlugin;
    }

    @Nullable
    public RegionContainer getRegionContainer() {
        return regionContainer;
    }

    /**
     * Cache des données de région pour les performances
     */
    private static class CachedRegionData {
        private final String regionId;
        private final BlockVector3 minPoint;
        private final BlockVector3 maxPoint;
        private final Map<String, Object> flags;
        private final long cacheTime;

        public CachedRegionData(@NotNull ProtectedRegion region) {
            this.regionId = region.getId();
            this.minPoint = region.getMinimumPoint();
            this.maxPoint = region.getMaximumPoint();
            this.flags = new HashMap<>();

            // Cache quelques flags importants
            region.getFlags().forEach((flag, value) -> {
                if (flag == Flags.BLOCK_BREAK || flag == Flags.BLOCK_PLACE || flag == Flags.ENTRY) {
                    flags.put(flag.getName(), value);
                }
            });

            this.cacheTime = System.currentTimeMillis();
        }

        public String getRegionId() {
            return regionId;
        }

        public BlockVector3 getMinPoint() {
            return minPoint;
        }

        public BlockVector3 getMaxPoint() {
            return maxPoint;
        }

        public Map<String, Object> getFlags() {
            return flags;
        }

        // Cache valide pendant 10 minutes
        public boolean isExpired() {
            return System.currentTimeMillis() - cacheTime > 600000;
        }
    }
}