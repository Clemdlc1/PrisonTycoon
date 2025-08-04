package fr.prisontycoon.managers;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.Gang;
import fr.prisontycoon.data.OutpostData;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Banner;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de l'avant-poste
 * Gère la capture, les récompenses, les skins et les bannières
 */
public class OutpostManager {

    private final PrisonTycoon plugin;
    private final Location outpostCenter;
    private final World caveWorld;

    // Données de l'avant-poste
    private OutpostData outpostData;

    // Systèmes de capture
    private final Map<UUID, Long> captureStartTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> captureProgress = new ConcurrentHashMap<>();

    // Cache des skins disponibles
    private final Map<String, File> availableSkins = new HashMap<>();

    // Constantes
    private static final int CAPTURE_TIME_SECONDS = 30;
    private static final int OUTPOST_SIZE = 7; // 7x7x7
    private static final int REWARD_INTERVAL_TICKS = 20 * 60; // 1 minute

    public OutpostManager(PrisonTycoon plugin) {
        this.plugin = plugin;

        // Initialiser la position de l'avant-poste
        this.caveWorld = Bukkit.getWorld("Cave");
        if (caveWorld == null) {
            plugin.getPluginLogger().severe("§cMonde 'Cave' introuvable pour l'avant-poste!");
            this.outpostCenter = null;
            return;
        }

        this.outpostCenter = new Location(caveWorld, -14, -16, 106);

        // Charger les données de l'avant-poste
        loadOutpostData();

        // Charger les skins disponibles
        loadAvailableSkins();

        // Démarrer les tâches
        startRewardTask();
        startCaptureTask();

        plugin.getPluginLogger().info("§aOutpostManager initialisé - Centre: " +
                outpostCenter.getBlockX() + ", " + outpostCenter.getBlockY() + ", " + outpostCenter.getBlockZ());
    }

    private void createOutpostTable() {
        String query = """
        CREATE TABLE IF NOT EXISTS outpost_data (
            id SERIAL PRIMARY KEY,
            controller VARCHAR(36),
            controller_name VARCHAR(16),
            capture_time BIGINT DEFAULT 0,
            current_skin VARCHAR(255) DEFAULT 'default'
        );
    """;

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.execute();

            // Insérer une ligne par défaut si la table est vide
            String checkQuery = "SELECT COUNT(*) FROM outpost_data";
            try (PreparedStatement checkPs = conn.prepareStatement(checkQuery);
                 ResultSet rs = checkPs.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    String insertQuery = """
                    INSERT INTO outpost_data (controller, controller_name, capture_time, current_skin)
                    VALUES (NULL, NULL, 0, 'default')
                """;
                    try (PreparedStatement insertPs = conn.prepareStatement(insertQuery)) {
                        insertPs.execute();
                    }
                }
            }

            plugin.getPluginLogger().info("§aTable outpost_data créée/vérifiée avec succès.");
        } catch (SQLException e) {
            plugin.getPluginLogger().severe("§cErreur lors de la création de la table outpost_data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * CORRECTION 7: Implémentation complète de loadOutpostData
     */
    private void loadOutpostData() {
        // S'assurer que la table existe
        createOutpostTable();

        String query = """
        SELECT controller, controller_name, capture_time, current_skin
        FROM outpost_data 
        ORDER BY id 
        LIMIT 1
    """;

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                // Charger les données existantes
                String controllerStr = rs.getString("controller");
                UUID controller = controllerStr != null ? UUID.fromString(controllerStr) : null;
                String controllerName = rs.getString("controller_name");
                long captureTime = rs.getLong("capture_time");
                String currentSkin = rs.getString("current_skin");

                // Créer l'objet OutpostData avec toutes les données
                this.outpostData = new OutpostData(controller, controllerName, captureTime, currentSkin);

                plugin.getPluginLogger().info("§aOutpostData chargé: Contrôleur=" +
                        (controllerName != null ? controllerName : "Aucun") +
                        ", Skin=" + currentSkin);
            } else {
                // Aucune données trouvées, créer par défaut
                this.outpostData = new OutpostData();
                saveOutpostData(); // Sauvegarder immédiatement les données par défaut
                plugin.getPluginLogger().info("§eOutpostData créé par défaut et sauvegardé.");
            }

        } catch (SQLException e) {
            plugin.getPluginLogger().severe("§cErreur lors du chargement des données d'avant-poste: " + e.getMessage());
            e.printStackTrace();
            // Créer des données par défaut en cas d'erreur
            this.outpostData = new OutpostData();
        }
    }

    /**
     * CORRECTION 8: Implémentation complète de saveOutpostData
     */
    private void saveOutpostData() {
        if (outpostData == null) {
            plugin.getPluginLogger().warning("§eAttempt to save null outpostData - ignoring");
            return;
        }

        String query = """
        INSERT INTO outpost_data (controller, controller_name, capture_time, current_skin)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (id) DO UPDATE SET
            controller = EXCLUDED.controller,
            controller_name = EXCLUDED.controller_name,
            capture_time = EXCLUDED.capture_time,
            current_skin = EXCLUDED.current_skin,
    """;

        // Pour PostgreSQL, nous devons d'abord vérifier s'il y a des données et faire UPDATE ou INSERT
        String updateQuery = """
        UPDATE outpost_data SET 
            controller = ?, 
            controller_name = ?, 
            capture_time = ?, 
            current_skin = ?
        WHERE id = (SELECT MIN(id) FROM outpost_data)
    """;

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            // Essayer d'abord un UPDATE
            try (PreparedStatement ps = conn.prepareStatement(updateQuery)) {
                ps.setString(1, outpostData.getController() != null ? outpostData.getController().toString() : null);
                ps.setString(2, outpostData.getControllerName());
                ps.setLong(3, outpostData.getCaptureTime());
                ps.setString(4, outpostData.getCurrentSkin());

                int rowsUpdated = ps.executeUpdate();

                // Si aucune ligne n'a été mise à jour, faire un INSERT
                if (rowsUpdated == 0) {
                    String insertQuery = """
                    INSERT INTO outpost_data (controller, controller_name, capture_time, current_skin)
                    VALUES (?, ?, ?, ?)
                """;
                    try (PreparedStatement insertPs = conn.prepareStatement(insertQuery)) {
                        insertPs.setString(1, outpostData.getController() != null ? outpostData.getController().toString() : null);
                        insertPs.setString(2, outpostData.getControllerName());
                        insertPs.setLong(3, outpostData.getCaptureTime());
                        insertPs.setString(4, outpostData.getCurrentSkin());
                        insertPs.executeUpdate();
                    }
                }
            }

            plugin.getPluginLogger().debug("§aOutpostData sauvegardé avec succès.");

        } catch (SQLException e) {
            plugin.getPluginLogger().severe("§cErreur lors de la sauvegarde des données d'avant-poste: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * CORRECTION 9: Méthodes publiques pour sauvegarder depuis l'extérieur
     */
    public void saveOutpostDataAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveOutpostData);
    }

    public void saveOutpostDataSync() {
        saveOutpostData();
    }

    /**
     * CORRECTION 10: Méthode pour forcer le rechargement des données
     */
    public void reloadOutpostData() {
        plugin.getPluginLogger().info("§eRechargement des données d'avant-poste...");
        loadOutpostData();
        plugin.getPluginLogger().info("§aDonnées d'avant-poste rechargées.");
    }

    /**
     * Charge tous les skins disponibles depuis le dossier cosmetics/AP-skins
     */
    private void loadAvailableSkins() {
        File skinsFolder = new File(plugin.getDataFolder(), "AP-skins");
        if (!skinsFolder.exists()) {
            skinsFolder.mkdirs();
            plugin.getPluginLogger().info("§7Dossier AP-skins créé: " + skinsFolder.getPath());
            return;
        }

        File[] schematicFiles = skinsFolder.listFiles((dir, name) -> name.endsWith(".schem"));
        if (schematicFiles != null) {
            for (File file : schematicFiles) {
                String skinName = file.getName().replace(".schem", "");
                availableSkins.put(skinName, file);
                plugin.getPluginLogger().debug("Skin d'avant-poste chargé: " + skinName);
            }
        }

        plugin.getPluginLogger().info("§a" + availableSkins.size() + " skins d'avant-poste chargés");
    }

    /**
     * Vérifie si un joueur est dans la zone de l'avant-poste
     */
    public boolean isPlayerInOutpost(Player player) {
        if (!player.getWorld().equals(caveWorld)) {
            return false;
        }

        Location playerLoc = player.getLocation();
        double distance = playerLoc.distance(outpostCenter);

        // Zone de capture = rayon de 5 blocs autour du centre
        return distance <= 5.0;
    }

    /**
     * Vérifie si une localisation est dans la zone de l'avant-poste.
     * C'est la méthode surchargée qui accepte une Location.
     */
    public boolean isPlayerInOutpost(Location location) {
        // Sécurité : vérifier que la localisation n'est pas nulle
        if (location == null) {
            return false;
        }

        // Vérifier que c'est le bon monde
        if (!location.getWorld().equals(caveWorld)) {
            return false;
        }

        // Calculer la distance par rapport au centre de l'avant-poste
        double distance = location.distance(outpostCenter);

        // La zone est considérée comme un rayon de 5 blocs
        return distance <= 5.0;
    }

    /**
     * Démarre une tentative de capture pour un joueur
     */
    public void startCapture(Player player) {
        UUID playerId = player.getUniqueId();

        // Vérifier si le joueur est déjà en train de capturer
        if (captureStartTimes.containsKey(playerId)) {
            return;
        }

        // Vérifier si le joueur est dans la zone
        if (!isPlayerInOutpost(player)) {
            player.sendMessage("§c❌ Vous devez être sur l'avant-poste pour le capturer!");
            return;
        }

        // Démarrer la capture
        captureStartTimes.put(playerId, System.currentTimeMillis());
        captureProgress.put(playerId, 0);

        // Notifier tous les joueurs du monde Cave
        String currentController = outpostData.getControllerName();
        String message = "§e🏰 §l" + player.getName() + " §etente de capturer l'avant-poste!";
        if (currentController != null) {
            message += " §7(Contrôlé par: §6" + currentController + "§7)";
        }

        broadcastToCaveWorld(message);
        player.sendMessage("§a🏰 Capture en cours... Restez sur l'avant-poste pendant " + CAPTURE_TIME_SECONDS + " secondes!");
    }

    /**
     * Annule la capture d'un joueur
     */
    public void cancelCapture(Player player) {
        UUID playerId = player.getUniqueId();
        captureStartTimes.remove(playerId);
        captureProgress.remove(playerId);
    }

    /**
     * Termine la capture pour un joueur
     */
    private void completeCapture(Player player) {
        UUID playerId = player.getUniqueId();

        // Nettoyer les données de capture
        captureStartTimes.remove(playerId);
        captureProgress.remove(playerId);

        // Définir le nouveau contrôleur
        outpostData.setController(playerId);
        outpostData.setControllerName(player.getName());
        outpostData.setCaptureTime(System.currentTimeMillis());

        // Changer le skin si nécessaire
        String playerSkin = getPlayerSelectedSkin(player);
        if (playerSkin != null && !playerSkin.equals(outpostData.getCurrentSkin())) {
            changeOutpostSkin(playerSkin);
        }

        // Mettre à jour les bannières
        updateOutpostBanners(player);

        // Notifier
        broadcastToCaveWorld("§a🏰 §l" + player.getName() + " §aa capturé l'avant-poste!");
        player.sendMessage("§a✅ Avant-poste capturé! Vous recevrez des récompenses chaque minute.");

        // Sauvegarder
        saveOutpostData();
    }

    /**
     * Change le skin de l'avant-poste
     */
    private void changeOutpostSkin(String skinName) {
        File schematicFile = availableSkins.get(skinName);
        if (schematicFile == null) {
            plugin.getPluginLogger().warning("§cSkin d'avant-poste introuvable: " + skinName);
            return;
        }

        try {
            // Utiliser FAWE pour placer le schematic
            com.sk89q.worldedit.world.World faweWorld = FaweAPI.getWorld(caveWorld.getName());
            if (faweWorld == null) {
                plugin.getPluginLogger().severe("§cMonde FAWE introuvable pour l'avant-poste");
                return;
            }

            // Lire le schematic
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                plugin.getPluginLogger().severe("§cFormat de schematic non supporté: " + schematicFile.getName());
                return;
            }

            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile));
                 EditSession editSession = WorldEdit.getInstance().newEditSession(faweWorld)) {

                Clipboard clipboard = reader.read();

                // Position de placement (centre de l'avant-poste)
                BlockVector3 to = BlockVector3.at(outpostCenter.getBlockX(), outpostCenter.getBlockY(), outpostCenter.getBlockZ());

                // Placer le schematic
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
                editSession.flushQueue();

                outpostData.setCurrentSkin(skinName);
                plugin.getPluginLogger().info("§aSkin d'avant-poste changé: " + skinName);

            }
        } catch (IOException e) {
            plugin.getPluginLogger().severe("§cErreur lors du changement de skin d'avant-poste: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Met à jour les bannières de l'avant-poste avec celles du gang du joueur
     */
    private void updateOutpostBanners(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = null;

        if (playerData.getGangId() != null) {
            gang = plugin.getGangManager().getGang(playerData.getGangId());
        }

        // Parcourir la zone de l'avant-poste pour trouver les bannières
        int radius = OUTPOST_SIZE / 2;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Location loc = outpostCenter.clone().add(x, y, z);
                    Block block = loc.getBlock();

                    if (block.getType().name().contains("BANNER")) {
                        if (block.getState() instanceof Banner banner) {
                            // Appliquer les motifs du gang ou bannière blanche par défaut
                            if (gang != null && gang.getBannerPatterns() != null) {
                                banner.setPatterns(gang.getBannerPatterns());
                            } else {
                                // Bannière blanche par défaut
                                banner.setPatterns(new ArrayList<>());
                            }
                            banner.update();
                        }
                    }
                }
            }
        }
    }

    /**
     * Distribue les récompenses au contrôleur de l'avant-poste
     */
    private void distributeRewards() {
        if (outpostData.getController() == null) {
            return;
        }

        Player controller = Bukkit.getPlayer(outpostData.getController());
        if (controller == null || !controller.isOnline()) {
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(controller.getUniqueId());
        GlobalBonusManager bonusManager = plugin.getGlobalBonusManager();

        // Calcul des récompenses de base
        long baseCoins = 1000;
        long baseTokens = 100;
        long baseExp = 500;
        long baseBeacons = 10;
        int baseJobExp = 50;

        // Appliquer les bonus globaux et de rang
        String currentRank = plugin.getMineManager().getCurrentRank(controller);
        double rankMultiplier = getRankMultiplier(currentRank);

        // Appliquer les bonus du GlobalBonusManager
        long finalCoins = Math.round(bonusManager.applyMoneyBonus(controller, Math.round(baseCoins * rankMultiplier)));
        long finalTokens = Math.round(bonusManager.applyTokenBonus(controller, Math.round(baseTokens * rankMultiplier)));
        long finalExp = Math.round(bonusManager.applyExperienceBonus(controller, Math.round(baseExp * rankMultiplier)));
        long finalBeacons = Math.round(baseBeacons * rankMultiplier);
        int finalJobExp = Math.toIntExact(Math.round(baseJobExp * rankMultiplier));

        // Bonus du métier guerrier (talent "garde")
        if ("guerrier".equals(playerData.getActiveProfession())) {
            ProfessionManager professionManager = plugin.getProfessionManager();
            ProfessionManager.Profession guerrier = professionManager.getProfession("guerrier");
            if (guerrier != null) {
                ProfessionManager.ProfessionTalent gardeTalent = guerrier.getTalent("garde");
                if (gardeTalent != null) {
                    int talentLevel = playerData.getTalentLevel("guerrier", "garde");
                    if (talentLevel > 0) {
                        int bonusPercent = gardeTalent.getValueAtLevel(talentLevel);
                        double gardeMultiplier = 1.0 + (bonusPercent / 100.0);

                        finalCoins = Math.round(finalCoins * gardeMultiplier);
                        finalTokens = Math.round(finalTokens * gardeMultiplier);
                        finalBeacons = Math.round(finalBeacons * gardeMultiplier);
                    }
                }
            }
        }

        // Bonus weekend (vendredi soir à dimanche soir)
        if (isWeekend()) {
            finalCoins *= 2;
            finalTokens *= 2;
            finalExp *= 2;
            finalBeacons *= 2;
            finalJobExp *= 2;
        }

        // Distribuer les récompenses
        plugin.getEconomyManager().addCoins(controller, finalCoins);
        plugin.getEconomyManager().addTokens(controller, finalTokens);
        plugin.getEconomyManager().addExperience(controller, finalExp);
        playerData.addBeacons(finalBeacons);

        // XP métier guerrier
        if ("guerrier".equals(playerData.getActiveProfession())) {
            plugin.getProfessionManager().addProfessionXP(controller, "guerrier", finalJobExp);
        }

        // Ajouter coins au gang
        if (playerData.getGangId() != null) {
            Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
            if (gang != null) {
                long gangCoins = finalCoins / 10; // 10% des gains vont au gang
                // gang.addBankBalance(gangCoins);
                plugin.getGangManager().saveGang(gang);
            }
        }

        // Marquer comme modifié
        plugin.getPlayerDataManager().markDirty(controller.getUniqueId());

        // Notification
        String rewardMessage = "§6🏰 Avant-poste: §a+" + finalCoins + " coins, +" + finalTokens + " tokens, +"
                + finalExp + " XP, +" + finalBeacons + " beacons";
        if (isWeekend()) {
            rewardMessage += " §e(Weekend x2!)";
        }
        controller.sendMessage(rewardMessage);
    }

    /**
     * Calcule le multiplicateur basé sur le rang du joueur
     */
    private double getRankMultiplier(String rank) {
        if (rank == null || rank.isEmpty()) return 1.0;

        char rankChar = rank.toLowerCase().charAt(0);
        return switch (rankChar) {
            case 'z' -> 1.0;
            case 'y' -> 1.1;
            case 'x' -> 1.2;
            case 'w' -> 1.3;
            case 'v' -> 1.4;
            case 'u' -> 1.5;
            case 't' -> 1.6;
            case 's' -> 1.7;
            case 'r' -> 1.8;
            case 'q' -> 1.9;
            case 'p' -> 2.0;
            case 'o' -> 2.2;
            case 'n' -> 2.4;
            case 'm' -> 2.6;
            case 'l' -> 2.8;
            case 'k' -> 3.0;
            case 'j' -> 3.3;
            case 'i' -> 3.6;
            case 'h' -> 4.0;
            case 'g' -> 4.5;
            case 'f' -> 5.0;
            case 'e' -> 6.0;
            case 'd' -> 7.0;
            case 'c' -> 8.0;
            case 'b' -> 9.0;
            case 'a' -> 10.0;
            default -> 1.0;
        };
    }

    /**
     * Vérifie si c'est le weekend (vendredi soir à dimanche soir)
     */
    private boolean isWeekend() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        int hour = now.getHour();

        return (dayOfWeek == DayOfWeek.FRIDAY && hour >= 18) ||
                dayOfWeek == DayOfWeek.SATURDAY ||
                (dayOfWeek == DayOfWeek.SUNDAY && hour <= 23);
    }

    /**
     * Démarre la tâche de distribution des récompenses
     */
    private void startRewardTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::distributeRewards, REWARD_INTERVAL_TICKS, REWARD_INTERVAL_TICKS);
    }

    /**
     * Démarre la tâche de vérification des captures
     */
    private void startCaptureTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, Long>> iterator = captureStartTimes.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                UUID playerId = entry.getKey();
                Long startTime = entry.getValue();

                Player player = Bukkit.getPlayer(playerId);
                if (player == null || !player.isOnline()) {
                    iterator.remove();
                    captureProgress.remove(playerId);
                    continue;
                }

                // Vérifier si le joueur est toujours dans la zone
                if (!isPlayerInOutpost(player)) {
                    player.sendMessage("§c❌ Capture annulée - vous avez quitté l'avant-poste!");
                    iterator.remove();
                    captureProgress.remove(playerId);
                    continue;
                }

                // Vérifier le progrès
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                int progress = (int) (elapsed * 100 / CAPTURE_TIME_SECONDS);
                captureProgress.put(playerId, Math.min(progress, 100));

                if (elapsed >= CAPTURE_TIME_SECONDS) {
                    // Capture terminée!
                    completeCapture(player);
                } else {
                    // Afficher le progrès
                    int remaining = CAPTURE_TIME_SECONDS - (int) elapsed;
                    player.sendActionBar("§6🏰 Capture: §a" + progress + "% §7(" + remaining + "s restantes)");
                }
            }
        }, 20L, 20L); // Toutes les secondes
    }

    /**
     * Diffuse un message à tous les joueurs du monde Cave
     */
    private void broadcastToCaveWorld(String message) {
        if (caveWorld == null) return;

        for (Player player : caveWorld.getPlayers()) {
            player.sendMessage(message);
        }
    }

    /**
     * Obtient le skin sélectionné par un joueur
     */
    private String getPlayerSelectedSkin(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        // TODO: Ajouter le système de sélection de skin dans PlayerData
        // Pour l'instant, retourner le skin par défaut
        return availableSkins.keySet().stream().findFirst().orElse(null);
    }

    // Getters publics
    public OutpostData getOutpostData() {
        return outpostData;
    }

    public Map<String, File> getAvailableSkins() {
        return new HashMap<>(availableSkins);
    }

    public Location getOutpostCenter() {
        return outpostCenter.clone();
    }

    public boolean isWeekendActive() {
        return isWeekend();
    }

    public int getCaptureProgress(Player player) {
        return captureProgress.getOrDefault(player.getUniqueId(), 0);
    }

    public boolean isCapturing(Player player) {
        return captureStartTimes.containsKey(player.getUniqueId());
    }

}