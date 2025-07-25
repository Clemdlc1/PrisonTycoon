package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire pour les sanctions (mutes/bans) persistantes
 */
public class ModerationManager {

    private final PrisonTycoon plugin;
    private final File moderationFile;
    // Cache en mémoire pour les performances
    private final Map<UUID, ModerationData> muteCache = new ConcurrentHashMap<>();
    private final Map<UUID, ModerationData> banCache = new ConcurrentHashMap<>();
    private FileConfiguration moderationConfig;

    public ModerationManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.moderationFile = new File(plugin.getDataFolder(), "moderation.yml");
        initializeFile();
        loadModerations();
    }

    /**
     * Initialise le fichier de modération
     */
    private void initializeFile() {
        if (!moderationFile.exists()) {
            try {
                moderationFile.getParentFile().mkdirs();
                moderationFile.createNewFile();
                plugin.getPluginLogger().info("Fichier de modération créé: " + moderationFile.getName());
            } catch (IOException e) {
                plugin.getPluginLogger().severe("Impossible de créer le fichier de modération: " + e.getMessage());
                e.printStackTrace();
            }
        }
        moderationConfig = YamlConfiguration.loadConfiguration(moderationFile);
    }

    /**
     * Charge toutes les modérations depuis le fichier
     */
    private void loadModerations() {
        muteCache.clear();
        banCache.clear();

        // Charge les mutes
        if (moderationConfig.contains("mutes")) {
            for (String uuidString : moderationConfig.getConfigurationSection("mutes").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String path = "mutes." + uuidString + ".";

                    ModerationData data = new ModerationData(
                            uuid,
                            moderationConfig.getString(path + "playerName"),
                            moderationConfig.getLong(path + "endTime"),
                            moderationConfig.getString(path + "reason"),
                            moderationConfig.getString(path + "moderator"),
                            moderationConfig.getLong(path + "startTime")
                    );

                    muteCache.put(uuid, data);
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Erreur lors du chargement du mute pour " + uuidString + ": " + e.getMessage());
                }
            }
        }

        // Charge les bans
        if (moderationConfig.contains("bans")) {
            for (String uuidString : moderationConfig.getConfigurationSection("bans").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    String path = "bans." + uuidString + ".";

                    ModerationData data = new ModerationData(
                            uuid,
                            moderationConfig.getString(path + "playerName"),
                            moderationConfig.getLong(path + "endTime"),
                            moderationConfig.getString(path + "reason"),
                            moderationConfig.getString(path + "moderator"),
                            moderationConfig.getLong(path + "startTime")
                    );

                    banCache.put(uuid, data);
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Erreur lors du chargement du ban pour " + uuidString + ": " + e.getMessage());
                }
            }
        }

        plugin.getPluginLogger().info("Modération chargée: " + muteCache.size() + " mutes, " + banCache.size() + " bans");
    }

    /**
     * Sauvegarde les modérations dans le fichier
     */
    private void saveModeration() {
        try {
            moderationConfig.save(moderationFile);
        } catch (IOException e) {
            plugin.getPluginLogger().severe("Erreur lors de la sauvegarde des modérations: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * CORRIGÉ: Mute un joueur avec enregistrement dans PlayerData
     */
    public void mutePlayer(UUID uuid, String playerName, long endTime, String reason, String moderator) {
        ModerationData muteData = new ModerationData(uuid, playerName, endTime, reason, moderator, System.currentTimeMillis());
        muteCache.put(uuid, muteData);

        // Sauvegarde dans le fichier de modération
        String path = "mutes." + uuid + ".";
        moderationConfig.set(path + "playerName", playerName);
        moderationConfig.set(path + "endTime", endTime);
        moderationConfig.set(path + "reason", reason);
        moderationConfig.set(path + "moderator", moderator);
        moderationConfig.set(path + "startTime", muteData.startTime());

        saveModeration();

        // NOUVEAU: Ajoute aussi à l'historique du joueur
        addSanctionToPlayer(
                uuid, "MUTE", reason, moderator, muteData.startTime(), endTime
        );

        plugin.getPluginLogger().info("Joueur muté: " + playerName + " par " + moderator + " (ajouté à l'historique)");
    }

    /**
     * Ajoute une sanction à l'historique d'un joueur
     */
    public void addSanctionToPlayer(UUID playerId, String type, String reason, String moderator, long startTime, long endTime) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);
        data.addSanction(type, reason, moderator, startTime, endTime);
        plugin.getPlayerDataManager().markDirty(playerId);

        plugin.getPluginLogger().info("Sanction ajoutée à " + data.getPlayerName() + ": " + type + " par " + moderator);
    }

    /**
     * Démute un joueur
     */
    public void unmutePlayer(UUID uuid, String moderator) {
        muteCache.remove(uuid);
        moderationConfig.set("mutes." + uuid, null);
        saveModeration();

        plugin.getPluginLogger().info("Joueur démuté: " + uuid + " par " + moderator);
    }

    /**
     * CORRIGÉ: Ban un joueur avec enregistrement dans PlayerData
     */
    public void banPlayer(UUID uuid, String playerName, long endTime, String reason, String moderator) {
        ModerationData banData = new ModerationData(uuid, playerName, endTime, reason, moderator, System.currentTimeMillis());
        banCache.put(uuid, banData);

        // Sauvegarde dans le fichier de modération
        String path = "bans." + uuid + ".";
        moderationConfig.set(path + "playerName", playerName);
        moderationConfig.set(path + "endTime", endTime);
        moderationConfig.set(path + "reason", reason);
        moderationConfig.set(path + "moderator", moderator);
        moderationConfig.set(path + "startTime", banData.startTime());

        saveModeration();

        // NOUVEAU: Ajoute aussi à l'historique du joueur
        addSanctionToPlayer(
                uuid, "BAN", reason, moderator, banData.startTime(), endTime
        );

        plugin.getPluginLogger().info("Joueur banni: " + playerName + " par " + moderator + " (ajouté à l'historique)");
    }

    /**
     * Déban un joueur
     */
    public void unbanPlayer(UUID uuid, String moderator) {
        banCache.remove(uuid);
        moderationConfig.set("bans." + uuid, null);
        saveModeration();

        plugin.getPluginLogger().info("Joueur débanni: " + uuid + " par " + moderator);
    }

    /**
     * Vérifie si un joueur est muté
     */
    public boolean isMuted(UUID uuid) {
        ModerationData muteData = muteCache.get(uuid);
        if (muteData == null) return false;

        // Vérifie si le mute a expiré
        if (muteData.endTime() != 0 && System.currentTimeMillis() > muteData.endTime()) {
            unmutePlayer(uuid, "SYSTÈME");
            return false;
        }

        return true;
    }

    /**
     * Vérifie si un joueur est banni
     */
    public boolean isBanned(UUID uuid) {
        ModerationData banData = banCache.get(uuid);
        if (banData == null) return false;

        // Vérifie si le ban a expiré
        if (banData.endTime() != 0 && System.currentTimeMillis() > banData.endTime()) {
            unbanPlayer(uuid, "SYSTÈME");
            return false;
        }

        return true;
    }

    /**
     * Obtient les informations d'un mute
     */
    public ModerationData getMuteData(UUID uuid) {
        if (!isMuted(uuid)) return null;
        return muteCache.get(uuid);
    }

    /**
     * Obtient les informations d'un ban
     */
    public ModerationData getBanData(UUID uuid) {
        if (!isBanned(uuid)) return null;
        return banCache.get(uuid);
    }

    /**
     * Obtient le nombre de joueurs mutés
     */
    public int getMutedPlayersCount() {
        // Nettoie les mutes expirés avant de compter
        muteCache.entrySet().removeIf(entry -> {
            ModerationData data = entry.getValue();
            return data.endTime() != 0 && System.currentTimeMillis() > data.endTime();
        });
        return muteCache.size();
    }

    /**
     * Obtient le nombre de joueurs bannis
     */
    public int getBannedPlayersCount() {
        // Nettoie les bans expirés avant de compter
        banCache.entrySet().removeIf(entry -> {
            ModerationData data = entry.getValue();
            return data.endTime() != 0 && System.currentTimeMillis() > data.endTime();
        });
        return banCache.size();
    }

    /**
     * Recharge le système de modération
     */
    public void reload() {
        moderationConfig = YamlConfiguration.loadConfiguration(moderationFile);
        loadModerations();
        plugin.getPluginLogger().info("Système de modération rechargé");
    }

    /**
     * Nettoie automatiquement les sanctions expirées
     */
    public void cleanupExpiredSanctions() {
        long currentTime = System.currentTimeMillis();
        int cleanedMutes = 0;
        int cleanedBans = 0;

        // Nettoie les mutes expirés
        Iterator<Map.Entry<UUID, ModerationData>> muteIterator = muteCache.entrySet().iterator();
        while (muteIterator.hasNext()) {
            Map.Entry<UUID, ModerationData> entry = muteIterator.next();
            ModerationData data = entry.getValue();

            if (data.endTime() != 0 && currentTime > data.endTime()) {
                muteIterator.remove();
                moderationConfig.set("mutes." + entry.getKey(), null);
                cleanedMutes++;
            }
        }

        // Nettoie les bans expirés
        Iterator<Map.Entry<UUID, ModerationData>> banIterator = banCache.entrySet().iterator();
        while (banIterator.hasNext()) {
            Map.Entry<UUID, ModerationData> entry = banIterator.next();
            ModerationData data = entry.getValue();

            if (data.endTime() != 0 && currentTime > data.endTime()) {
                banIterator.remove();
                moderationConfig.set("bans." + entry.getKey(), null);
                cleanedBans++;
            }
        }

        if (cleanedMutes > 0 || cleanedBans > 0) {
            saveModeration();
            plugin.getPluginLogger().info("Nettoyage automatique: " + cleanedMutes + " mutes et " + cleanedBans + " bans expirés supprimés");
        }
    }

    /**
     * NOUVEAU: Obtient l'historique complet des sanctions d'un joueur
     */
    public List<PlayerData.SanctionData> getPlayerSanctionHistory(UUID uuid) {
        return plugin.getPlayerDataManager().getPlayerData(uuid).getSanctionHistory();
    }

    /**
     * NOUVEAU: Obtient le nombre total de sanctions d'un joueur
     */
    public int getPlayerTotalSanctions(UUID uuid) {
        return plugin.getPlayerDataManager().getPlayerData(uuid).getTotalSanctions();
    }

    /**
     * Classe pour stocker les données de modération
     */
    public record ModerationData(UUID uuid, String playerName, long endTime, String reason, String moderator,
                                 long startTime) {

        public boolean isPermanent() {
            return endTime == 0;
        }

        public long getRemainingTime() {
            if (isPermanent()) return -1;
            return Math.max(0, endTime - System.currentTimeMillis());
        }
    }
}