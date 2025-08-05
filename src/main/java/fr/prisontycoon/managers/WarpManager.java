package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.WarpData;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Gestionnaire des warps
 * Gère tous les points de téléportation du serveur
 */
public class WarpManager {

    private final PrisonTycoon plugin;
    private final Map<String, WarpData> warps = new ConcurrentHashMap<>();
    private final Map<String, Long> teleportCooldowns = new ConcurrentHashMap<>();

    // Configuration
    private static final int TELEPORT_COOLDOWN = 3; // secondes
    private static final String BYPASS_COOLDOWN_PERMISSION = "specialmine.warp.nocooldown";

    public WarpManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        loadWarpsFromConfig();
        generateMineWarps();
    }

    /**
     * Charge les warps depuis la configuration
     */
    private void loadWarpsFromConfig() {
        plugin.getPluginLogger().info("§7Chargement des warps...");

        Map<String, WarpData> configWarps = plugin.getConfigManager().getAllWarps();
        warps.putAll(configWarps);

        plugin.getPluginLogger().info("§a" + warps.size() + " warps chargés depuis la configuration");
    }

    /**
     * Génère automatiquement les warps des mines - MODIFIÉ pour coordonnées personnalisées
     */
    private void generateMineWarps() {
        plugin.getPluginLogger().info("§7Génération des warps de mines...");

        Collection<MineData> mines = plugin.getMineManager().getAllMines();
        int mineWarpsCreated = 0;

        for (MineData mine : mines) {
            String warpId = mine.getId().toLowerCase();

            // Ne pas écraser un warp configuré manuellement
            if (warps.containsKey(warpId)) {
                continue;
            }

            // Utiliser les coordonnées de téléportation personnalisées de la mine
            org.bukkit.World world = plugin.getServer().getWorld(mine.getWorldName());
            if (world == null) {
                plugin.getPluginLogger().warning("§cImpossible de créer le warp pour la mine " + mine.getId() + " (monde introuvable)");
                continue;
            }

            // Utiliser les coordonnées de téléportation définies dans la config de la mine
            Location teleportLoc = mine.getTeleportLocation(world);
            if (teleportLoc == null) {
                plugin.getPluginLogger().warning("§cCoordonnées de téléportation invalides pour la mine " + mine.getId());
                continue;
            }

            WarpData mineWarp = new WarpData(
                    warpId,
                    mine.getDisplayName(),
                    WarpData.WarpType.MINE,
                    mine.getWorldName(),
                    mine.getTeleportX(),     // Utiliser les coordonnées personnalisées
                    mine.getTeleportY(),
                    mine.getTeleportZ(),
                    mine.getTeleportYaw(),
                    mine.getTeleportPitch(),
                    null, // Permission gérée par MineManager.canAccessMine
                    mine.getType().getHeadMaterial(),
                    null,
                    true,
                    "Mine " + mine.getDisplayName()
            );

            warps.put(warpId, mineWarp);
            mineWarpsCreated++;

            // Log des coordonnées pour debug
            if (mine.hasCustomTeleportLocation()) {
                plugin.getPluginLogger().debug("§aMine " + mine.getId() + " avec téléportation personnalisée: " +
                        mine.getTeleportX() + ", " + mine.getTeleportY() + ", " + mine.getTeleportZ());
            }
        }

        plugin.getPluginLogger().info("§a" + mineWarpsCreated + " warps de mines générés automatiquement");
    }

    /**
     * Téléporte un joueur vers un warp
     */
    public boolean teleportToWarp(Player player, String warpName) {
        return teleportToWarp(player, warpName, true);
    }

    /**
     * Téléporte un joueur vers un warp avec option de vérification des permissions
     */
    public boolean teleportToWarp(Player player, String warpName, boolean checkPermissions) {
        WarpData warp = findWarp(warpName);
        if (warp == null) {
            player.sendMessage("§c❌ Warp introuvable: §e" + warpName);
            return false;
        }

        // Vérification des permissions si demandé
        if (checkPermissions && !canAccessWarp(player, warp)) {
            player.sendMessage("§c❌ Vous n'avez pas accès à ce warp!");
            return false;
        }

        // Vérification du cooldown
        if (!player.hasPermission(BYPASS_COOLDOWN_PERMISSION) && hasTeleportCooldown(player)) {
            long remaining = getRemainingCooldown(player);
            player.sendMessage("§c❌ Attendez encore §e" + remaining + "s §cavant de vous téléporter!");
            return false;
        }

        // Vérification de l'accessibilité du warp
        if (!warp.isAccessible()) {
            player.sendMessage("§c❌ Ce warp n'est pas disponible actuellement!");
            return false;
        }

        Location location = warp.getLocation();
        if (location == null) {
            player.sendMessage("§c❌ Erreur de téléportation (monde introuvable)!");
            return false;
        }

        // Si c'est un warp de mine, utiliser les coordonnées personnalisées
        if (warp.getType() == WarpData.WarpType.MINE) {
            String mineId = warp.getId().substring(5);
            MineData mine = plugin.getMineManager().getMine(mineId);

            if (mine != null) {
                // Vérifier l'accès via MineManager
                if (checkPermissions && !plugin.getMineManager().canAccessMine(player, mineId)) {
                    player.sendMessage("§c❌ Vous n'avez pas accès à cette mine!");
                    return false;
                }

                // Utiliser les coordonnées de téléportation personnalisées de la mine
                Location teleportLocation = mine.getTeleportLocation(location.getWorld());
                if (teleportLocation != null) {
                    location = teleportLocation;
                    plugin.getPluginLogger().debug("Téléportation vers mine " + mineId + " avec coordonnées personnalisées");
                } else {
                    plugin.getPluginLogger().warning("§cCoordonnées de téléportation invalides pour la mine " + mineId);
                }
            }
        }

        // Téléportation standard
        player.teleport(location);
        player.sendMessage("§a✅ Téléporté vers " + warp.getFormattedName());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Appliquer le cooldown
        if (!player.hasPermission(BYPASS_COOLDOWN_PERMISSION)) {
            setTeleportCooldown(player);
        }

        return true;
    }

    /**
     * Trouve un warp par son nom (insensible à la casse)
     */
    public WarpData findWarp(String name) {
        String lowerName = name.toLowerCase();

        // Recherche exacte par ID
        WarpData warp = warps.get(lowerName);
        if (warp != null) {
            return warp;
        }

        // Recherche par nom d'affichage
        return warps.values().stream()
                .filter(w -> w.getDisplayName().toLowerCase().contains(lowerName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Vérifie si un joueur peut accéder à un warp
     */
    public boolean canAccessWarp(Player player, WarpData warp) {
        // Vérification de l'activation
        if (!warp.isEnabled()) {
            return false;
        }

        // Vérification de la permission spécifique
        if (warp.getPermission() != null && !warp.getPermission().isEmpty()) {
            if (!player.hasPermission(warp.getPermission())) {
                return false;
            }
        }

        // Vérification spéciale pour les mines
        if (warp.getType() == WarpData.WarpType.MINE) {
            String mineId = warp.getId().substring(5);
            return plugin.getMineManager().canAccessMine(player, mineId);
        }

        return true;
    }

    /**
     * Obtient tous les warps d'un type spécifique
     */
    public List<WarpData> getWarpsByType(WarpData.WarpType type) {
        List<WarpData> filteredWarps = warps.values().stream()
                .filter(warp -> warp.getType() == type)
                .collect(Collectors.toList());

        // CORRIGÉ : Appliquer un tri spécial et robuste uniquement pour les mines
        if (type == WarpData.WarpType.MINE) {
            filteredWarps.sort(new MineWarpComparator());
        } else {
            filteredWarps.sort(Comparator.comparing(WarpData::getDisplayName));
        }

        return filteredWarps;
    }

    /**
     * MODIFIÉ : Comparateur de warps de mines entièrement réécrit.
     * Priorise Normal < VIP < Prestige, puis trie alphabétiquement ou numériquement.
     */
    private static class MineWarpComparator implements Comparator<WarpData> {

        // Pattern pour extraire les nombres des IDs de mines
        private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)$");

        // Fonction pour extraire le numéro d'un ID de mine (ex: "mine-prestige11" -> 11)
        private int extractNumber(String id) {
            Matcher matcher = NUMBER_PATTERN.matcher(id);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
            return 0; // Fallback
        }

        // Attribue une priorité en fonction du type de mine
        private int getPriority(String id) {
            if (id.startsWith("mine-prestige")) return 2; // Priorité la plus haute
            if (id.startsWith("mine-vip")) return 1;      // Priorité intermédiaire
            if (id.matches("mine-[a-z]")) return 0;       // Priorité la plus basse pour A-Z
            return 3; // Fallback pour les autres cas
        }

        @Override
        public int compare(WarpData w1, WarpData w2) {
            // 1. Comparer par priorité (Normal < VIP < Prestige)
            int priority1 = getPriority(w1.getId());
            int priority2 = getPriority(w2.getId());

            if (priority1 != priority2) {
                return Integer.compare(priority1, priority2);
            }

            // 2. Si la priorité est la même, appliquer un tri secondaire
            // Cas des mines normales (A-Z)
            if (priority1 == 0) {
                return w1.getId().compareTo(w2.getId()); // Tri alphabétique simple
            }

            // Cas des mines VIP et Prestige
            if (priority1 == 1 || priority1 == 2) {
                int num1 = extractNumber(w1.getId());
                int num2 = extractNumber(w2.getId());
                return Integer.compare(num1, num2); // Tri numérique
            }

            // Fallback
            return w1.getId().compareTo(w2.getId());
        }
    }

    /**
     * Obtient tous les warps accessibles par un joueur
     */
    public List<WarpData> getAccessibleWarps(Player player) {
        return warps.values().stream()
                .filter(warp -> canAccessWarp(player, warp))
                .sorted((a, b) -> {
                    if (a.getType() != b.getType()) {
                        return a.getType().ordinal() - b.getType().ordinal();
                    }
                    return a.getDisplayName().compareToIgnoreCase(b.getDisplayName());
                })
                .collect(Collectors.toList());
    }

    /**
     * Obtient tous les warps
     */
    public Collection<WarpData> getAllWarps() {
        return new ArrayList<>(warps.values());
    }

    /**
     * Gestion du cooldown de téléportation
     */
    private boolean hasTeleportCooldown(Player player) {
        String playerId = player.getUniqueId().toString();
        Long lastTeleport = teleportCooldowns.get(playerId);

        if (lastTeleport == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        return (currentTime - lastTeleport) < (TELEPORT_COOLDOWN * 1000L);
    }

    private long getRemainingCooldown(Player player) {
        String playerId = player.getUniqueId().toString();
        Long lastTeleport = teleportCooldowns.get(playerId);

        if (lastTeleport == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastTeleport;
        long cooldownMs = TELEPORT_COOLDOWN * 1000L;

        return Math.max(0, (cooldownMs - elapsed) / 1000);
    }

    private void setTeleportCooldown(Player player) {
        String playerId = player.getUniqueId().toString();
        teleportCooldowns.put(playerId, System.currentTimeMillis());
    }

    /**
     * Obtient le warp spawn
     */
    public WarpData getSpawnWarp() {
        return warps.values().stream()
                .filter(warp -> warp.getType() == WarpData.WarpType.SPAWN)
                .findFirst()
                .orElse(null);
    }

    /**
     * Recharge les warps depuis la configuration
     */
    public void reload() {
        warps.clear();
        loadWarpsFromConfig();
        generateMineWarps();
        plugin.getPluginLogger().info("§aWarps rechargés!");
    }
}