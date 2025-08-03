package fr.prisontycoon.managers;

import com.sk89q.worldedit.regions.Region;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestionnaire des mines complet - CORRIGÉ pour utiliser MineData externe
 * Support des mines normales, prestige et VIP avec génération optimisée
 */
public class MineManager {

    private final PrisonTycoon plugin;
    private final Map<String, MineData> mines = new ConcurrentHashMap<>();
    private final Map<String, Long> mineResetTimes = new ConcurrentHashMap<>();
    private final Map<String, Boolean> mineGenerating = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public MineManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        loadMinesFromConfigManager();
        startMineResetScheduler();
    }

    /**
     * CORRIGÉ: Charge les mines depuis le ConfigManager au lieu de la config directe
     */
    private void loadMinesFromConfigManager() {
        plugin.getPluginLogger().info("§7Chargement des mines depuis ConfigManager...");

        // Récupère toutes les mines du ConfigManager corrigé
        Map<String, MineData> configMines = plugin.getConfigManager().getAllMines();

        if (configMines.isEmpty()) {
            plugin.getPluginLogger().warning("§cAucune mine trouvée dans ConfigManager!");
            return;
        }

        for (Map.Entry<String, MineData> entry : configMines.entrySet()) {
            String mineId = entry.getKey();
            MineData mineData = entry.getValue();

            // Validation supplémentaire
            if (mineData.getVolume() <= 0) {
                plugin.getPluginLogger().warning("§cMine " + mineId + " a un volume invalide: " + mineData.getVolume());
                continue;
            }

            if (!mineData.isCompositionValid()) {
                plugin.getPluginLogger().warning("§cMine " + mineId + " a une composition invalide");
                // On peut quand même la charger avec un avertissement
            }

            mines.put(mineId, mineData);
            plugin.getPluginLogger().info("§aMine chargée: " + mineId + " (Type: " + mineData.getType() +
                    ", Volume: " + mineData.getVolume() + " blocs)");
        }

        plugin.getPluginLogger().info("§aMines chargées: " + mines.size());
    }

    /**
     * Génération de mine via l'API FastAsyncWorldEdit. C'est la méthode la plus rapide.
     *
     * @param mineId L'ID de la mine à générer.
     */
    /**
     * Génération de mine via l'API FastAsyncWorldEdit (Version API moderne et correcte).
     *
     * @param mineId L'ID de la mine à générer.
     */
    public void generateMine(String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) { /* ... */ return; }
        if (mineGenerating.getOrDefault(mineId, false)) { /* ... */ return; }

        // --- CORRECTION CLÉ ---
        // On utilise la méthode qui existe dans votre version de l'API.
        World faweWorld = FaweAPI.getWorld(mine.getWorldName());
        if (faweWorld == null) {
            plugin.getPluginLogger().severe("§cMonde FAWE introuvable pour la mine " + mineId);
            return;
        }

        mineGenerating.put(mineId, true);
        plugin.getPluginLogger().info("§b[FAWE] Démarrage de la génération de la mine " + mineId + "...");
        notifyPlayersInMine(mineId, "§e⚠️ Régénération de la mine en cours...");
        long startTime = System.currentTimeMillis();

        // On utilise un try-with-resources pour s'assurer que l'EditSession est bien fermée.
        // C'est compatible avec les anciennes et nouvelles versions.
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(faweWorld)) {

            // 1. Définir la région de la mine
            BlockVector3 min = BlockVector3.at(mine.getMinX(), mine.getMinY(), mine.getMinZ());
            BlockVector3 max = BlockVector3.at(mine.getMaxX(), mine.getMaxY(), mine.getMaxZ());
            CuboidRegion region = new CuboidRegion(faweWorld, min, max);

            // 2. Créer le "pattern" (modèle) aléatoire
            RandomPattern randomPattern = new RandomPattern();
            for (Map.Entry<Material, Double> entry : mine.getBlockComposition().entrySet()) {
                randomPattern.add(BlockTypes.get(entry.getKey().name().toLowerCase()), entry.getValue());
            }

            // 3. Exécuter l'opération !
            // Le cast explicite vers (Region) peut être nécessaire avec certaines versions.
            // Si votre IDE ne le demande pas, vous pouvez l'enlever.
            editSession.setBlocks((Region) region, randomPattern);

            // 4. Forcer la soumission de la file d'attente.
            // Avec les anciennes API et le newEditSession(world), il est plus sûr de le garder.
            editSession.flushQueue();

            // ... reste du code de finalisation ...
            mineResetTimes.put(mineId, System.currentTimeMillis());
            mineGenerating.put(mineId, false);
            long duration = System.currentTimeMillis() - startTime;
            plugin.getPluginLogger().info("§a[FAWE] Mine " + mineId + " générée : " + region.getVolume() +
                    " blocs en " + duration + "ms");
            notifyPlayersInMine(mineId, "§a✅ Mine régénérée avec succès !");

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors de la génération FAWE de la mine " + mineId);
            e.printStackTrace();
            mineGenerating.put(mineId, false);
        }
    }

    /**
     * Vérifie l'accès d'un joueur à une mine
     */
    public boolean canAccessMine(Player player, String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérification VIP
        if (mine.isVipOnly() && !player.hasPermission("specialmine.vip")) {
            return false;
        }

        // Vérification permission spécifique
        if (mine.getRequiredPermission() != null && !player.hasPermission(mine.getRequiredPermission())) {
            return false;
        }

        // Vérification prestige
        if (mine.getRequiredPrestige() > playerData.getPrestigeLevel()) {
            return false;
        }

        // Vérification rang (pour mines normales)
        if (mine.getType() == MineData.MineType.NORMAL) {
            String currentRank = getCurrentRank(player);
            String requiredRank = mine.getRequiredRank();

            return isRankSufficient(currentRank, requiredRank);
        }

        return true;
    }

    /**
     * Téléporte un joueur à une mine
     */
    public boolean teleportToMine(Player player, String mineId) {
        if (!canAccessMine(player, mineId)) {
            player.sendMessage("§c❌ Vous n'avez pas accès à cette mine!");
            return false;
        }

        MineData mine = mines.get(mineId);
        if (mine == null) {
            player.sendMessage("§c❌ Mine introuvable!");
            return false;
        }

        org.bukkit.World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) {
            player.sendMessage("§c❌ Monde de la mine introuvable!");
            return false;
        }

        // Utilise la nouvelle méthode getCenterLocation
        Location teleportLocation = mine.getCenterLocation(world);

        // S'assurer qu'il y a de l'air pour le joueur
        Block airBlock1 = teleportLocation.getBlock();
        Block airBlock2 = teleportLocation.clone().add(0, 1, 0).getBlock();

        if (airBlock1.getType() != Material.AIR) {
            airBlock1.setType(Material.AIR);
        }
        if (airBlock2.getType() != Material.AIR) {
            airBlock2.setType(Material.AIR);
        }

        player.teleport(teleportLocation);
        player.sendMessage("§a✅ Téléporté à la mine " + mine.getDisplayName());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        return true;
    }

    /**
     * Obtient les informations détaillées d'une mine
     */
    public String getMineInfo(Player player, String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) {
            return "§c❌ Mine introuvable!";
        }

        StringBuilder info = new StringBuilder();
        info.append("§6═══════════════════════════════════\n");
        info.append("§6         📍 ").append(mine.getDisplayName()).append("\n");
        info.append("§6═══════════════════════════════════\n");

        // Utilise la nouvelle méthode getDetailedInfo
        info.append(mine.getDetailedInfo()).append("\n");

        // Informations d'accès
        boolean canAccess = canAccessMine(player, mineId);
        info.append("§7Accès: ").append(canAccess ? "§a✅ Autorisé" : "§c❌ Interdit").append("\n");

        // Conditions d'accès si bloqué
        if (!canAccess) {
            if (mine.getRequiredPrestige() > 0) {
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                info.append("§7Prestige requis: §d").append(mine.getRequiredPrestige())
                        .append(" §7(actuel: §d").append(playerData.getPrestigeLevel()).append("§7)\n");
            }
            if (mine.isVipOnly()) {
                info.append("§7Statut VIP requis\n");
            }
            if (mine.getRequiredPermission() != null) {
                info.append("§7Permission requise: §e").append(mine.getRequiredPermission()).append("\n");
            }
        }

        // Temps depuis le dernier reset
        Long lastReset = mineResetTimes.get(mineId);
        if (lastReset != null) {
            long timeSince = (System.currentTimeMillis() - lastReset) / 1000 / 60; // minutes
            info.append("§7Dernier reset: il y a ").append(timeSince).append(" minute(s)");
        }

        return info.toString();
    }

    /**
     * Obtient les mines accessibles pour un joueur
     */
    public List<MineData> getAccessibleMines(Player player) {
        List<MineData> accessible = new ArrayList<>();

        for (MineData mine : mines.values()) {
            if (canAccessMine(player, mine.getId())) {
                accessible.add(mine);
            }
        }

        // Trier par type puis par nom
        accessible.sort((a, b) -> {
            if (a.getType() != b.getType()) {
                return a.getType().ordinal() - b.getType().ordinal();
            }
            return a.getId().compareToIgnoreCase(b.getId());
        });

        return accessible;
    }

    /**
     * Obtient les mines par type
     */
    public List<MineData> getMinesByType(MineData.MineType type) {
        return mines.values().stream()
                .filter(mine -> mine.getType() == type)
                .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Vérifie si un joueur est dans une mine
     */
    public String getPlayerCurrentMine(Player player) {
        Location playerLocation = player.getLocation();

        for (Map.Entry<String, MineData> entry : mines.entrySet()) {
            if (entry.getValue().contains(playerLocation)) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Notifie tous les joueurs présents dans une mine
     */
    private void notifyPlayersInMine(String mineId, String message) {
        MineData mine = mines.get(mineId);
        if (mine == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerMine = getPlayerCurrentMine(player);
            if (mineId.equals(playerMine)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Lance le scheduler de reset automatique des mines
     */
    private void startMineResetScheduler() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long currentTime = System.currentTimeMillis();
            long resetInterval = 1800000; // 30 minutes par défaut

            for (Map.Entry<String, Long> entry : mineResetTimes.entrySet()) {
                String mineId = entry.getKey();
                long lastReset = entry.getValue();

                if (currentTime - lastReset > resetInterval) {
                    plugin.getPluginLogger().debug("Reset automatique de la mine: " + mineId);
                    generateMine(mineId);
                }
            }
        }, 0L, 6000L); // Vérifie toutes les 5 minutes (6000 ticks)
    }

    /**
     * Force le reset de toutes les mines
     */
    public void resetAllMines() {
        plugin.getPluginLogger().info("§6Lancement de la régénération échelonnée de toutes les mines...");
        final Queue<String> minesToReset = new LinkedList<>(mines.keySet());

        if (minesToReset.isEmpty()) {
            plugin.getPluginLogger().info("§eAucune mine à régénérer.");
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (minesToReset.isEmpty()) {
                    plugin.getPluginLogger().info("§aRégénération échelonnée terminée !");
                    this.cancel();
                    return;
                }
                String mineId = minesToReset.poll();
                generateMine(mineId);
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    /**
     * Recharge les mines depuis la configuration
     */
    public void reloadMines() {
        plugin.getPluginLogger().info("§7Rechargement des mines...");

        mines.clear();
        mineResetTimes.clear();
        mineGenerating.clear();

        loadMinesFromConfigManager();
        plugin.getPluginLogger().info("§aMines rechargées!");
    }

    /**
     * Obtient les statistiques des mines
     */
    public String getMinesStatistics() {
        StringBuilder stats = new StringBuilder();

        stats.append("§6═══════════════════════════════════\n");
        stats.append("§6         📊 STATISTIQUES MINES\n");
        stats.append("§6═══════════════════════════════════\n");

        int normalCount = getMinesByType(MineData.MineType.NORMAL).size();
        int prestigeCount = getMinesByType(MineData.MineType.PRESTIGE).size();
        int vipCount = getMinesByType(MineData.MineType.VIP).size();

        stats.append("§f📍 Mines normales: §7").append(normalCount).append("\n");
        stats.append("§d📍 Mines prestige: §7").append(prestigeCount).append("\n");
        stats.append("§6📍 Mines VIP: §7").append(vipCount).append("\n");
        stats.append("§e📊 Total: §7").append(mines.size()).append(" mines\n");

        // Mines en cours de génération
        long generating = mineGenerating.values().stream().mapToLong(b -> b ? 1 : 0).sum();
        if (generating > 0) {
            stats.append("§c⚡ En génération: §7").append(generating).append("\n");
        }

        return stats.toString();
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Obtient le rang actuel d'un joueur
     */
    public String getCurrentRank(Player player) {
        String highestRank = "a"; // Rang par défaut

        // Cherche toutes les permissions de mine que le joueur possède
        Set<String> minePermissions = player.getEffectivePermissions().stream()
                .map(PermissionAttachmentInfo::getPermission)
                .filter(perm -> perm.startsWith("specialmine.mine."))
                .collect(Collectors.toSet());

        // Recherche du rang le plus élevé en itérant de z vers a
        for (char c = 'z'; c >= 'a'; c--) {
            String minePermission = "specialmine.mine." + c;
            if (minePermissions.contains(minePermission)) {
                highestRank = String.valueOf(c);
                break; // Premier trouvé = le plus élevé
            }
        }
        return highestRank;
    }

    /**
     * Vérifie si un rang est suffisant pour accéder à une mine
     */
    public boolean isRankSufficient(String currentRank, String requiredRank) {
        if (currentRank == null || requiredRank == null) return false;
        return currentRank.compareToIgnoreCase(requiredRank) >= 0;
    }

    /**
     * Obtient la couleur d'un rang
     */
    public String getRankColor(String rank) {
        if (rank == null) return "§7";

        return switch (rank.toLowerCase()) {
            case "a", "b", "c", "d", "e" -> "§f";
            case "f", "g", "h", "i", "j" -> "§a";
            case "k", "l", "m", "n", "o" -> "§e";
            case "p", "q", "r", "s", "t" -> "§6";
            case "u", "v", "w", "x", "y", "z" -> "§c";
            default -> "§7";
        };
    }

    /**
     * Convertit un nom de rang en numéro (a=1, b=2, etc.)
     */
    public int rankToNumber(String rank) {
        if (rank == null || rank.length() != 1) return 0;
        char c = rank.toLowerCase().charAt(0);
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 1;
        }
        return 0;
    }

    /**
     * Convertit un numéro en nom de rang (1=a, 2=b, etc.)
     */
    public String numberToRank(int number) {
        if (number < 1 || number > 26) return "a";
        return String.valueOf((char) ('a' + number - 1));
    }

    /**
     * NOUVEAU: Obtient le rang et sa couleur d'un joueur
     *
     * @param player Le joueur
     * @return String[] avec [0] = rang, [1] = couleur
     */
    public String[] getRankAndColor(Player player) {
        String currentRank = getCurrentRank(player);
        String rankColor = getRankColor(currentRank);
        return new String[]{currentRank, rankColor};
    }

    // ==================== GETTERS ====================

    /**
     * Obtient une mine par son ID
     */
    public MineData getMine(String mineId) {
        return mines.get(mineId);
    }

    /**
     * Obtient toutes les mines
     */
    public Collection<MineData> getAllMines() {
        return mines.values();
    }

    /**
     * Vérifie si une mine existe
     */
    public boolean mineExists(String mineId) {
        return mines.containsKey(mineId);
    }

    /**
     * Obtient le nombre total de mines
     */
    public int getMineCount() {
        return mines.size();
    }

    /**
     * Obtient les noms de toutes les mines
     */
    public Set<String> getMineNames() {
        return new HashSet<>(mines.keySet());
    }

    /**
     * Vérifie si une mine est en cours de génération
     */
    public boolean isMineGenerating(String mineId) {
        return mineGenerating.getOrDefault(mineId, false);
    }

    /**
     * Obtient toutes les mines triées par nom
     */
    public List<MineData> getAllMinesSorted() {
        return mines.values().stream()
                .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Recherche des mines par nom partiel
     */
    public List<MineData> searchMines(String query) {
        String lowerQuery = query.toLowerCase();
        return mines.values().stream()
                .filter(mine -> mine.getId().toLowerCase().contains(lowerQuery) ||
                        mine.getDisplayName().toLowerCase().contains(lowerQuery))
                .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                .collect(Collectors.toList());
    }
}