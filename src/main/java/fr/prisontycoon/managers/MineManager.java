package fr.prisontycoon.managers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import net.ess3.api.IEssentials;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire des mines intégré avec WorldGuard et EssentialsX
 * INTÉGRATION NATIVE - Remplace l'ancien MineManager
 * <p>
 * Fonctionnalités intégrées:
 * - Protection automatique des mines avec WorldGuard
 * - Gestion des permissions avec LuckPerms
 * - Création automatique des warps/homes avec EssentialsX
 * - Reset automatique des mines
 * - Système de téléportation avancé
 */
public class MineManager {

    private final PrisonTycoon plugin;

    // Données des mines chargées depuis la configuration
    private final Map<String, MineData> mines = new HashMap<>();

    // Cache des derniers resets pour optimisation
    private final ConcurrentHashMap<String, Long> lastResetTime = new ConcurrentHashMap<>();

    // Tâches de reset automatique
    private final Map<String, BukkitRunnable> resetTasks = new HashMap<>();

    // Joueurs en cours de téléportation
    private final Set<UUID> teleportingPlayers = ConcurrentHashMap.newKeySet();

    public MineManager(PrisonTycoon plugin) {
        this.plugin = plugin;

        // Charge les mines depuis la configuration
        loadMinesFromConfig();

        // Initialise les protections WorldGuard
        setupWorldGuardProtections();

        // Crée les warps/homes EssentialsX
        setupEssentialsXIntegration();

        // Démarre les tâches de reset automatique
        startAutoResetTasks();

        plugin.getPluginLogger().info("MineManager intégré initialisé avec " + mines.size() + " mines");
    }

    /**
     * Charge les mines depuis la configuration
     * INTÉGRATION avec la ConfigManager existante
     */
    private void loadMinesFromConfig() {
        mines.clear();

        // Utilise la ConfigManager existante
        for (String mineName : plugin.getConfigManager().getAllMineNames()) {
            MineData mineData = plugin.getConfigManager().getMineData(mineName);
            if (mineData != null) {
                mines.put(mineName.toLowerCase(), mineData);
                plugin.getPluginLogger().debug("Mine chargée: " + mineName);
            }
        }

        if (mines.isEmpty()) {
            plugin.getPluginLogger().warning("§eAucune mine configurée trouvée!");
        }
    }

    /**
     * Configure les protections WorldGuard automatiques
     * INTÉGRATION NATIVE WORLDGUARD
     */
    private void setupWorldGuardProtections() {
        if (!plugin.isWorldGuardEnabled()) {
            plugin.getPluginLogger().info("WorldGuard non disponible - Protections des mines désactivées");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                WorldGuardPlugin worldGuard = plugin.getWorldGuardPlugin();
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();

                int created = 0;
                int updated = 0;

                for (Map.Entry<String, MineData> entry : mines.entrySet()) {
                    String mineName = entry.getKey();
                    MineData mineData = entry.getValue();

                    World world = plugin.getServer().getWorld((Key) mineData.getWorld());
                    if (world == null) {
                        plugin.getPluginLogger().warning("Monde introuvable pour la mine " + mineName + ": " + mineData.getWorld());
                        continue;
                    }

                    RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
                    if (regionManager == null) continue;

                    String regionId = "mine_" + mineName;
                    ProtectedRegion existingRegion = regionManager.getRegion(regionId);

                    // Crée ou met à jour la région
                    Location corner1 = mineData.getCorner1();
                    Location corner2 = mineData.getCorner2();

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

                    ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);

                    // Configuration des flags
                    region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
                    region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);
                    region.setFlag(Flags.PVP, StateFlag.State.DENY);
                    region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.DENY);
                    region.setPriority(10);

                    if (existingRegion == null) {
                        regionManager.addRegion(region);
                        created++;
                        plugin.getPluginLogger().debug("Région WorldGuard créée: " + regionId);
                    } else {
                        regionManager.removeRegion(regionId);
                        regionManager.addRegion(region);
                        updated++;
                        plugin.getPluginLogger().debug("Région WorldGuard mise à jour: " + regionId);
                    }
                }

                // Sauvegarde toutes les régions
                for (World world : plugin.getServer().getWorlds()) {
                    RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
                    if (regionManager != null) {
                        try {
                            regionManager.save();
                        } catch (Exception e) {
                            plugin.getPluginLogger().warning("Erreur sauvegarde régions WorldGuard: " + e.getMessage());
                        }
                    }
                }

                plugin.getPluginLogger().info("Protections WorldGuard: " + created + " créées, " + updated + " mises à jour");

            } catch (Exception e) {
                plugin.getPluginLogger().severe("Erreur configuration WorldGuard:");
                e.printStackTrace();
            }
        });
    }

    /**
     * Configure l'intégration EssentialsX (warps et homes)
     * INTÉGRATION NATIVE ESSENTIALSX
     */
    private void setupEssentialsXIntegration() {
        if (!plugin.isEssentialsEnabled()) {
            plugin.getPluginLogger().info("EssentialsX non disponible - Warps/homes automatiques désactivés");
            return;
        }

        boolean createWarps = plugin.getConfig().getBoolean("hooks.essentialsx.create-mine-warps", true);
        if (!createWarps) return;

        CompletableFuture.runAsync(() -> {
            try {
                IEssentials essentials = plugin.getEssentialsAPI();
                if (essentials == null) return;

                int warpsCreated = 0;
                String warpPrefix = plugin.getConfig().getString("hooks.essentialsx.mine-warp-prefix", "mine_");

                for (Map.Entry<String, MineData> entry : mines.entrySet()) {
                    String mineName = entry.getKey();
                    MineData mineData = entry.getValue();

                    Location spawnLocation = getSpawnLocation(mineData);
                    if (spawnLocation == null) continue;

                    String warpName = warpPrefix + mineName;

                    try {
                        // Vérifie si le warp existe déjà
                        Location existingWarp = essentials.getWarps().getWarp(warpName);

                        if (existingWarp == null) {
                            // Crée le nouveau warp
                            essentials.getWarps().setWarp(warpName, spawnLocation);
                            warpsCreated++;
                            plugin.getPluginLogger().debug("Warp EssentialsX créé: " + warpName);
                        } else {
                            // Met à jour le warp existant
                            essentials.getWarps().setWarp(warpName, spawnLocation);
                            plugin.getPluginLogger().debug("Warp EssentialsX mis à jour: " + warpName);
                        }

                    } catch (Exception e) {
                        plugin.getPluginLogger().warning("Erreur création warp " + warpName + ": " + e.getMessage());
                    }
                }

                plugin.getPluginLogger().info("Intégration EssentialsX: " + warpsCreated + " warps de mines créés");

            } catch (Exception e) {
                plugin.getPluginLogger().severe("Erreur intégration EssentialsX:");
                e.printStackTrace();
            }
        });
    }

    /**
     * Démarre les tâches de reset automatique
     */
    private void startAutoResetTasks() {
        for (Map.Entry<String, MineData> entry : mines.entrySet()) {
            String mineName = entry.getKey();
            MineData mineData = entry.getValue();

            int resetTime = mineData.getResetTime();
            if (resetTime > 0) {
                BukkitRunnable resetTask = new BukkitRunnable() {
                    @Override
                    public void run() {
                        resetMine(mineName, true);
                    }
                };

                // Démarre avec un délai aléatoire pour éviter tous les resets simultanément
                long delay = ThreadLocalRandom.current().nextLong(1200L, 6000L); // 1-5 minutes
                resetTask.runTaskTimer(plugin, delay, resetTime * 20L);
                resetTasks.put(mineName, resetTask);

                plugin.getPluginLogger().debug("Tâche de reset automatique créée pour " + mineName +
                        " (toutes les " + resetTime + "s)");
            }
        }
    }

    /**
     * Vérifie si un joueur peut accéder à une mine
     * INTÉGRATION NATIVE LUCKPERMS
     */
    public boolean canAccessMine(@NotNull Player player, @NotNull String mineName) {
        MineData mineData = mines.get(mineName.toLowerCase());
        if (mineData == null) return false;

        // Vérifie les permissions avec le système intégré
        PermissionManager permissionManager = plugin.getPermissionManager();

        // Permission spécifique de la mine
        if (permissionManager.hasPermission(player, "prisontycoon.mine." + mineName.toLowerCase())) {
            return true;
        }

        // Permission générale des mines
        if (permissionManager.hasPermission(player, "prisontycoon.mine.access")) {
            return true;
        }

        // Vérifie les exigences spéciales (prestige, niveau, etc.)
        if (!meetsMineRequirements(player, mineData)) {
            return false;
        }

        // Les admins peuvent accéder à tout
        if (plugin.isLuckPermsEnabled()) {
            if (plugin.getLuckPermsAPI() != null) {
                return player.hasPermission("prisontycoon.admin");
            }
        }

        return player.hasPermission("prisontycoon.admin");
    }

    /**
     * Vérifie si un joueur remplit les exigences d'une mine
     */
    private boolean meetsMineRequirements(@NotNull Player player, @NotNull MineData mineData) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifie le niveau de prestige requis
        if (mineData.getRequiredPrestigeLevel() > 0) {
            if (playerData.getPrestigeLevel(player) < mineData.getRequiredPrestigeLevel()) {
                return false;
            }
        }

        // Vérifie si c'est VIP uniquement
        if (mineData.isVipOnly()) {
            PermissionManager permissionManager = plugin.getPermissionManager();
            return permissionManager.isVip(player);
        }

        return true;
    }

    /**
     * Vérifie si un joueur peut miner à une location
     * INTÉGRATION NATIVE WORLDGUARD
     */
    public boolean canMineAt(@NotNull Player player, @NotNull Location location) {
        if (!plugin.isWorldGuardEnabled()) return true;

        try {
            WorldGuardPlugin worldGuard = plugin.getWorldGuardPlugin();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regionManager = container.get(BukkitAdapter.adapt(location.getWorld()));

            if (regionManager == null) return true;

            BlockVector3 vector = BlockVector3.at(location.getX(), location.getY(), location.getZ());
            ApplicableRegionSet regions = regionManager.getApplicableRegions(vector);

            // Vérifie les permissions de base WorldGuard
            if (!regions.testState(worldGuard.wrapPlayer(player), Flags.BLOCK_BREAK)) {
                return false;
            }

            // Vérifie si c'est dans une mine du plugin
            for (ProtectedRegion region : regions) {
                if (region.getId().startsWith("mine_")) {
                    String mineName = region.getId().replace("mine_", "");
                    return canAccessMine(player, mineName);
                }
            }

            return true;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur vérification minage WorldGuard: " + e.getMessage());
            return true; // En cas d'erreur, autorise par défaut
        }
    }

    /**
     * Téléporte un joueur à une mine avec intégrations
     */
    public void teleportToMine(@NotNull Player player, @NotNull String mineName) {
        if (teleportingPlayers.contains(player.getUniqueId())) {
            player.sendMessage("§cVous êtes déjà en cours de téléportation!");
            return;
        }

        MineData mineData = mines.get(mineName.toLowerCase());
        if (mineData == null) {
            player.sendMessage("§cMine introuvable: " + mineName);
            return;
        }

        if (!canAccessMine(player, mineName)) {
            player.sendMessage("§cVous n'avez pas accès à cette mine!");
            return;
        }

        Location spawnLocation = getSpawnLocation(mineData);
        if (spawnLocation == null) {
            player.sendMessage("§cErreur: Point de spawn de la mine non configuré!");
            return;
        }

        teleportingPlayers.add(player.getUniqueId());

        // Téléportation avec délai et effets
        player.sendMessage("§eTéléportation vers la mine §6" + mineData.getDisplayName() + "§e...");

        new BukkitRunnable() {
            int countdown = 3;

            @Override
            public void run() {
                if (!player.isOnline() || !teleportingPlayers.contains(player.getUniqueId())) {
                    teleportingPlayers.remove(player.getUniqueId());
                    cancel();
                    return;
                }

                if (countdown > 0) {
                    player.sendMessage("§eTéléportation dans §6" + countdown + "§e seconde" + (countdown > 1 ? "s" : "") + "...");
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (0.1f * countdown));
                    countdown--;
                } else {
                    // Téléportation
                    player.teleport(spawnLocation);
                    player.sendMessage("§aBienvenue dans la mine §6" + mineData.getDisplayName() + "§a!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);

                    // Effets visuels
                    player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL,
                            player.getLocation().add(0, 1, 0), 50, 1, 1, 1, 0.1);

                    teleportingPlayers.remove(player.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Reset une mine avec notifications
     */
    public void resetMine(@NotNull String mineName, boolean automatic) {
        MineData mineData = mines.get(mineName.toLowerCase());
        if (mineData == null) {
            plugin.getPluginLogger().warning("Tentative de reset d'une mine inexistante: " + mineName);
            return;
        }

        // Vérifie le cooldown pour éviter les resets trop fréquents
        Long lastReset = lastResetTime.get(mineName);
        if (lastReset != null && System.currentTimeMillis() - lastReset < 60000) { // 1 minute minimum
            plugin.getPluginLogger().debug("Reset de mine ignoré (cooldown): " + mineName);
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Notifications aux joueurs dans la mine
                notifyPlayersInMine(mineData, automatic);

                // Reset effectif de la mine
                performMineReset(mineData);

                // Met à jour le cache
                lastResetTime.put(mineName, System.currentTimeMillis());

                String resetType = automatic ? "automatique" : "manuel";
                plugin.getPluginLogger().info("Mine resetée (" + resetType + "): " + mineName);

            } catch (Exception e) {
                plugin.getPluginLogger().severe("Erreur lors du reset de la mine " + mineName + ":");
                e.printStackTrace();
            }
        });
    }

    /**
     * Notifie les joueurs dans une mine du reset
     */
    private void notifyPlayersInMine(@NotNull MineData mineData, boolean automatic) {
        Location corner1 = mineData.getCorner1();
        Location corner2 = mineData.getCorner2();

        if (corner1.getWorld() != corner2.getWorld()) return;

        World world = corner1.getWorld();
        if (world == null) return;

        // Trouve tous les joueurs dans la zone de la mine
        List<Player> playersInMine = new ArrayList<>();

        for (Player player : world.getPlayers()) {
            Location playerLoc = player.getLocation();

            if (isLocationInMine(playerLoc, corner1, corner2)) {
                playersInMine.add(player);
            }
        }

        if (!playersInMine.isEmpty()) {
            String resetType = automatic ? "automatique" : "manuel";
            String message = "§6⚠ RESET " + resetType.toUpperCase() + " §6⚠";
            String mineMessage = "§eMine §6" + mineData.getDisplayName() + " §erestaurée!";

            for (Player player : playersInMine) {
                player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                player.sendMessage("§f" + message);
                player.sendMessage("§f" + mineMessage);
                player.sendMessage("§6§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
            }

            plugin.getPluginLogger().debug("Notification de reset envoyée à " + playersInMine.size() + " joueur(s)");
        }
    }

    /**
     * Effectue le reset physique de la mine
     */
    private void performMineReset(@NotNull MineData mineData) {
        Location corner1 = mineData.getCorner1();
        Location corner2 = mineData.getCorner2();
        Map<Material, Double> composition = mineData.getComposition();

        if (corner1.getWorld() != corner2.getWorld()) return;

        World world = corner1.getWorld();
        if (world == null) return;

        // Calcule les limites
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        int blocksReset = 0;

        // Reset par chunks pour éviter les lags
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Material newMaterial = selectRandomMaterial(composition);
                    world.getBlockAt(x, y, z).setType(newMaterial, false);
                    blocksReset++;

                    // Pause périodique pour éviter les lags
                    if (blocksReset % 1000 == 0) {
                        try {
                            Thread.sleep(50); // 50ms de pause toutes les 1000 blocs
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        plugin.getPluginLogger().debug("Reset terminé: " + blocksReset + " blocs resetés");
    }

    /**
     * Sélectionne un matériau aléatoire selon la composition
     */
    private Material selectRandomMaterial(@NotNull Map<Material, Double> composition) {
        double random = ThreadLocalRandom.current().nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<Material, Double> entry : composition.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                return entry.getKey();
            }
        }

        // Fallback vers le premier matériau si erreur de probabilité
        return composition.keySet().iterator().next();
    }

    /**
     * Vérifie si une location est dans une mine
     */
    private boolean isLocationInMine(@NotNull Location location, @NotNull Location corner1, @NotNull Location corner2) {
        if (!location.getWorld().equals(corner1.getWorld())) return false;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minY = Math.min(corner1.getBlockY(), corner2.getBlockY());
        int maxY = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * Obtient la location de spawn d'une mine
     */
    @Nullable
    private Location getSpawnLocation(@NotNull MineData mineData) {
        Location spawn = mineData.getSpawn();
        if (spawn != null) return spawn;

        // Calcule un spawn par défaut au centre de la mine, en hauteur
        Location corner1 = mineData.getCorner1();
        Location corner2 = mineData.getCorner2();

        if (corner1.getWorld() != corner2.getWorld()) return null;

        double centerX = (corner1.getX() + corner2.getX()) / 2.0;
        double centerZ = (corner1.getZ() + corner2.getZ()) / 2.0;
        double highestY = Math.max(corner1.getY(), corner2.getY()) + 2; // 2 blocs au-dessus

        return new Location(corner1.getWorld(), centerX, highestY, centerZ);
    }

    // === MÉTHODES PUBLIQUES POUR L'API ===

    /**
     * Obtient toutes les mines disponibles pour un joueur
     */
    @NotNull
    public Set<String> getAvailableMines(@NotNull Player player) {
        Set<String> available = new HashSet<>();

        for (String mineName : mines.keySet()) {
            if (canAccessMine(player, mineName)) {
                available.add(mineName);
            }
        }

        return available;
    }

    /**
     * Obtient toutes les mines configurées
     */
    @NotNull
    public Set<String> getAllMineNames() {
        return new HashSet<>(mines.keySet());
    }

    /**
     * Obtient les données d'une mine
     */
    @Nullable
    public MineData getMineData(@NotNull String mineName) {
        return mines.get(mineName.toLowerCase());
    }

    /**
     * Obtient la location de spawn d'une mine
     */
    @Nullable
    public Location getMineSpawn(@NotNull String mineName) {
        MineData mineData = mines.get(mineName.toLowerCase());
        return mineData != null ? getSpawnLocation(mineData) : null;
    }

    /**
     * Force le reset d'une mine
     */
    public void forceResetMine(@NotNull String mineName) {
        resetMine(mineName, false);
    }

    /**
     * Recharge les mines depuis la configuration
     */
    public void reloadMines() {
        // Arrête les anciennes tâches
        resetTasks.values().forEach(BukkitRunnable::cancel);
        resetTasks.clear();

        // Recharge
        loadMinesFromConfig();
        setupWorldGuardProtections();
        setupEssentialsXIntegration();
        startAutoResetTasks();

        plugin.getPluginLogger().info("Mines rechargées: " + mines.size() + " mines disponibles");
    }

    /**
     * Nettoie les ressources
     */
    public void cleanup() {
        resetTasks.values().forEach(BukkitRunnable::cancel);
        resetTasks.clear();
        teleportingPlayers.clear();
        lastResetTime.clear();
    }

    public List<MineData> getMinesByType(MineData.MineType type) {
        List<MineData> result = new ArrayList<>();
        for (MineData mineData : mines.values()) {
            if (mineData.getType() == type) {
                result.add(mineData);
            }
        }
        return result;
    }

    public Collection<MineData> getAllMines() {
        return mines.values();
    }

    public List<MineData> searchMines(String query) {
        List<MineData> result = new ArrayList<>();
        for (MineData mineData : mines.values()) {
            if (mineData.getName().toLowerCase().contains(query.toLowerCase())) {
                result.add(mineData);
            }
        }
        return result;
    }

    public MineData getMine(String name) {
        return getMineData(name);
    }

    public String getMineInfo(MineData mineData) {
        return mineData.getDetailedInfo();
    }

    public void resetAllMines() {
        for (String mineName : mines.keySet()) {
            resetMine(mineName, false);
        }
    }

    public boolean isMineGenerating(String mineName) {
        return false;
    }

    public void generateMine(String mineName) {
        resetMine(mineName, false);
    }

    public String getCurrentRank(Player player) {
        for (char c = 'z'; c >= 'a'; c--) {
            if (plugin.getPermissionManager().hasPermission(player, "specialmine.mine." + c)) {
                return String.valueOf(c);
            }
        }
        return "a";
    }

    public String getRankColor(String rank) {
        // This is just a placeholder. I will need to implement this properly.
        return "§f";
    }

    public String[] getRankAndColor(Player player) {
        String rank = getCurrentRank(player);
        String color = getRankColor(rank);
        return new String[]{rank, color};
    }

    public List<MineData> getAccessibleMines(Player player) {
        List<MineData> result = new ArrayList<>();
        String rank = getCurrentRank(player);
        for (char c = 'a'; c <= rank.charAt(0); c++) {
            MineData mineData = getMineData(String.valueOf(c));
            if (mineData != null) {
                result.add(mineData);
            }
        }
        return result;
    }

    public String getMinesStatistics() {
        return "";
    }

    public MineData getPlayerCurrentMine(Player player) {
        return null;
    }

    public boolean mineExists(String name) {
        return mines.containsKey(name.toLowerCase());
    }
}