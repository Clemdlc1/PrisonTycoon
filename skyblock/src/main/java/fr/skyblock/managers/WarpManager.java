package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import fr.skyblock.models.IslandWarp;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class WarpManager {

    private final CustomSkyblock plugin;
    private final File warpsFile;
    private final File promotionsFile;
    private YamlConfiguration warpsConfig;
    private YamlConfiguration promotionsConfig;

    // Cache en mémoire
    private final Map<UUID, IslandWarp> warpsCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<UUID>> islandWarpsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> promotedIslands = new ConcurrentHashMap<>(); // Island ID -> expiration time

    public WarpManager(CustomSkyblock plugin) {
        this.plugin = plugin;
        this.warpsFile = new File(plugin.getDataFolder(), "warps.yml");
        this.promotionsFile = new File(plugin.getDataFolder(), "promotions.yml");

        setupFiles();
        loadAll();
        startPromotionCleanupTask();
    }

    private void setupFiles() {
        if (!warpsFile.exists()) {
            try {
                warpsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer le fichier warps.yml: " + e.getMessage());
            }
        }

        if (!promotionsFile.exists()) {
            try {
                promotionsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer le fichier promotions.yml: " + e.getMessage());
            }
        }

        warpsConfig = YamlConfiguration.loadConfiguration(warpsFile);
        promotionsConfig = YamlConfiguration.loadConfiguration(promotionsFile);
    }

    // === GESTION DES WARPS ===

    public IslandWarp createWarp(UUID islandId, String name, String description, Location location) {
        UUID warpId = UUID.randomUUID();
        IslandWarp warp = new IslandWarp(warpId, islandId, name, description, location);

        saveWarp(warp);
        return warp;
    }

    public void saveWarp(IslandWarp warp) {
        warpsCache.put(warp.getId(), warp);

        // Mettre à jour le cache des warps par île
        islandWarpsCache.computeIfAbsent(warp.getIslandId(), k -> new ArrayList<>()).add(warp.getId());

        // Sauvegarder dans le fichier
        ConfigurationSection section = warpsConfig.createSection("warps." + warp.getId().toString());
        warp.saveToYaml(section);
        saveWarpsConfig();
    }

    public IslandWarp loadWarp(UUID warpId) {
        if (warpsCache.containsKey(warpId)) {
            return warpsCache.get(warpId);
        }

        ConfigurationSection section = warpsConfig.getConfigurationSection("warps." + warpId.toString());
        if (section == null) {
            return null;
        }

        IslandWarp warp = IslandWarp.loadFromYaml(section);
        warpsCache.put(warpId, warp);

        // Mettre à jour le cache
        islandWarpsCache.computeIfAbsent(warp.getIslandId(), k -> new ArrayList<>()).add(warpId);

        return warp;
    }

    public void deleteWarp(UUID warpId) {
        IslandWarp warp = warpsCache.remove(warpId);
        if (warp != null) {
            // Retirer du cache des îles
            List<UUID> islandWarps = islandWarpsCache.get(warp.getIslandId());
            if (islandWarps != null) {
                islandWarps.remove(warpId);
                if (islandWarps.isEmpty()) {
                    islandWarpsCache.remove(warp.getIslandId());
                }
            }
        }

        warpsConfig.set("warps." + warpId.toString(), null);
        saveWarpsConfig();
    }

    public List<IslandWarp> getIslandWarps(UUID islandId) {
        List<UUID> warpIds = islandWarpsCache.get(islandId);
        if (warpIds == null) {
            return new ArrayList<>();
        }

        return warpIds.stream()
                .map(this::loadWarp)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public int getMaxWarpsForIsland(Island island) {
        int baseWarps = 0;

        // Déblocage par niveau
        if (island.getLevel() >= 10) baseWarps = 1;
        if (island.getLevel() >= 100) baseWarps = 2;
        if (island.getLevel() >= 1000) baseWarps = 3;

        // Bonus VIP
        Player owner = Bukkit.getPlayer(island.getOwner());
        if (owner != null && plugin.getPrisonTycoonHook().hasCustomPermission(owner, "specialmine.vip")) {
            baseWarps++;
        }

        return baseWarps;
    }

    public boolean canCreateWarp(Island island) {
        int currentWarps = getIslandWarps(island.getId()).size();
        int maxWarps = getMaxWarpsForIsland(island);
        return currentWarps < maxWarps;
    }

    // === TÉLÉPORTATION ===

    public boolean teleportToWarp(Player player, UUID warpId) {
        IslandWarp warp = loadWarp(warpId);
        if (warp == null) {
            player.sendMessage(ChatColor.RED + "Ce warp n'existe plus !");
            return false;
        }

        // Vérifier si l'île est ouverte
        Island island = plugin.getDatabaseManager().loadIsland(warp.getIslandId());
        if (island == null) {
            player.sendMessage(ChatColor.RED + "L'île de ce warp n'existe plus !");
            return false;
        }

        if (!island.getFlag(Island.IslandFlag.VISITOR_INTERACT) && !island.isMember(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Cette île est fermée aux visiteurs !");
            return false;
        }

        if (!warp.isPublic() && !island.isMember(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Ce warp est privé !");
            return false;
        }

        // S'assurer que le monde de l'île est chargé avant de téléporter
        plugin.getIslandManager().ensureIslandWorldLoaded(island);

        // Recomposer la Location avec le monde (si rechargé)
        Location target = warp.getLocation();
        if (target.getWorld() == null || plugin.getIslandManager().getIslandWorld(island) == null
                || !plugin.getIslandManager().getIslandWorld(island).equals(target.getWorld())) {
            // Remapper sur le monde d'île actuel
            target = new Location(
                    plugin.getIslandManager().getIslandWorld(island),
                    warp.getLocation().getX(),
                    warp.getLocation().getY(),
                    warp.getLocation().getZ(),
                    warp.getLocation().getYaw(),
                    warp.getLocation().getPitch()
            );
        }

        // Téléporter
        player.teleport(target);
        warp.incrementVisits();
        saveWarp(warp);

        player.sendMessage(ChatColor.GREEN + "Téléporté au warp " + ChatColor.YELLOW + warp.getName() +
                ChatColor.GREEN + " sur l'île de " + ChatColor.AQUA + getIslandOwnerName(island) + " !");

        // Ajouter comme visiteur
        if (!island.isMember(player.getUniqueId())) {
            island.addVisitor(player.getUniqueId());
            plugin.getDatabaseManager().saveIsland(island);
        }

        return true;
    }

    // === SYSTÈME DE PROMOTION ===

    public boolean promoteIsland(UUID islandId, Player payer, int days) {
        long beaconCost = calculatePromotionCost(days);

        if (!plugin.getPrisonTycoonHook().hasBeacons(payer.getUniqueId(), beaconCost)) {
            payer.sendMessage(ChatColor.RED + "Vous n'avez pas assez de beacons ! (Requis: " + beaconCost + ")");
            return false;
        }

        // Effectuer le paiement via PrisonTycoon
        if (Bukkit.getPlayer(payer.getUniqueId()) != null) {
            Player player = Bukkit.getPlayer(payer.getUniqueId());
            // Utiliser removeBeacons directement
            if (!plugin.getPrisonTycoonHook().removeBeacons(player.getUniqueId(), beaconCost)) {
                payer.sendMessage(ChatColor.RED + "Erreur lors du paiement !");
                return false;
            }
        } else {
            return false;
        }

        long expirationTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L);
        promotedIslands.put(islandId, expirationTime);

        // Sauvegarder
        promotionsConfig.set("promotions." + islandId.toString(), expirationTime);
        savePromotionsConfig();

        payer.sendMessage(ChatColor.GREEN + "Votre île a été promue pour " + days + " jour(s) !");
        payer.sendMessage(ChatColor.YELLOW + "Coût: " + beaconCost + " beacons");

        return true;
    }

    public boolean isIslandPromoted(UUID islandId) {
        Long expirationTime = promotedIslands.get(islandId);
        if (expirationTime == null) {
            return false;
        }

        if (System.currentTimeMillis() > expirationTime) {
            // Promotion expirée
            promotedIslands.remove(islandId);
            promotionsConfig.set("promotions." + islandId.toString(), null);
            savePromotionsConfig();
            return false;
        }

        return true;
    }

    public long calculatePromotionCost(int days) {
        return days * 100L; // 100 beacons par jour
    }

    // === GESTION DES ÎLES OUVERTES/FERMÉES ===

    public void setIslandOpen(Island island, boolean open) {
        island.setFlag(Island.IslandFlag.VISITOR_INTERACT, open);
        plugin.getDatabaseManager().saveIsland(island);
    }

    public boolean isIslandOpen(Island island) {
        return island.getFlag(Island.IslandFlag.VISITOR_INTERACT);
    }

    // === RÉCUPÉRATION DES WARPS ===

    public List<IslandWarp> getAllPublicWarps() {
        return warpsCache.values().stream()
                .filter(IslandWarp::isPublic)
                .filter(warp -> {
                    Island island = plugin.getDatabaseManager().loadIsland(warp.getIslandId());
                    return island != null && isIslandOpen(island);
                })
                .collect(Collectors.toList());
    }

    public List<IslandWarp> getPromotedWarps() {
        return getAllPublicWarps().stream()
                .filter(warp -> isIslandPromoted(warp.getIslandId()))
                .sorted((a, b) -> Long.compare(b.getVisits(), a.getVisits()))
                .collect(Collectors.toList());
    }

    public List<IslandWarp> getPopularWarps() {
        return getAllPublicWarps().stream()
                .filter(warp -> !isIslandPromoted(warp.getIslandId()))
                .sorted((a, b) -> Long.compare(b.getVisits(), a.getVisits()))
                .collect(Collectors.toList());
    }

    // === SAUVEGARDE ET CHARGEMENT ===

    private void loadAll() {
        // Charger les warps
        ConfigurationSection warpsSection = warpsConfig.getConfigurationSection("warps");
        if (warpsSection != null) {
            for (String key : warpsSection.getKeys(false)) {
                try {
                    UUID warpId = UUID.fromString(key);
                    loadWarp(warpId);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("ID de warp invalide: " + key);
                }
            }
        }

        // Charger les promotions
        ConfigurationSection promotionsSection = promotionsConfig.getConfigurationSection("promotions");
        if (promotionsSection != null) {
            for (String key : promotionsSection.getKeys(false)) {
                try {
                    UUID islandId = UUID.fromString(key);
                    long expiration = promotionsSection.getLong(key);
                    if (System.currentTimeMillis() < expiration) {
                        promotedIslands.put(islandId, expiration);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("ID d'île invalide dans les promotions: " + key);
                }
            }
        }

        plugin.getLogger().info("Chargé " + warpsCache.size() + " warps et " +
                promotedIslands.size() + " promotions actives");
    }

    private void saveWarpsConfig() {
        try {
            warpsConfig.save(warpsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des warps: " + e.getMessage());
        }
    }

    private void savePromotionsConfig() {
        try {
            promotionsConfig.save(promotionsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des promotions: " + e.getMessage());
        }
    }

    private void startPromotionCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredPromotions();
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // Toutes les 5 minutes
    }

    private void cleanupExpiredPromotions() {
        List<UUID> expired = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (Map.Entry<UUID, Long> entry : promotedIslands.entrySet()) {
            if (currentTime > entry.getValue()) {
                expired.add(entry.getKey());
            }
        }

        for (UUID islandId : expired) {
            promotedIslands.remove(islandId);
            promotionsConfig.set("promotions." + islandId.toString(), null);
        }

        if (!expired.isEmpty()) {
            savePromotionsConfig();
            plugin.getLogger().info("Nettoyé " + expired.size() + " promotions expirées");
        }
    }

    // === MÉTHODES UTILITAIRES ===

    private String getIslandOwnerName(Island island) {
        Player owner = Bukkit.getPlayer(island.getOwner());
        if (owner != null) {
            return owner.getName();
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(island.getOwner());
        return skyblockPlayer != null ? skyblockPlayer.getName() : "Joueur inconnu";
    }

    public void saveAll() {
        saveWarpsConfig();
        savePromotionsConfig();
    }

    public int getTotalWarps() {
        return warpsCache.size();
    }

    public int getTotalPromotedIslands() {
        return promotedIslands.size();
    }
}