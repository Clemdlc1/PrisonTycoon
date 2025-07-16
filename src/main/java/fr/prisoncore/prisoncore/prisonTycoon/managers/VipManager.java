package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire pour les joueurs VIP
 */
public class VipManager {

    private final PrisonTycoon plugin;
    private final File vipFile;
    private FileConfiguration vipConfig;

    // Cache des VIP pour les performances
    private final Set<UUID> vipCache = ConcurrentHashMap.newKeySet();

    public VipManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.vipFile = new File(plugin.getDataFolder(), "vips.yml");
        initializeFile();
        loadVips();
    }

    /**
     * Initialise le fichier VIP
     */
    private void initializeFile() {
        if (!vipFile.exists()) {
            try {
                vipFile.getParentFile().mkdirs();
                vipFile.createNewFile();
                plugin.getPluginLogger().info("Fichier VIP créé: " + vipFile.getName());
            } catch (IOException e) {
                plugin.getPluginLogger().severe("Impossible de créer le fichier VIP: " + e.getMessage());
                e.printStackTrace();
            }
        }
        vipConfig = YamlConfiguration.loadConfiguration(vipFile);
    }

    /**
     * Charge tous les VIP depuis le fichier
     */
    private void loadVips() {
        vipCache.clear();

        if (vipConfig.contains("vips")) {
            for (String uuidString : vipConfig.getConfigurationSection("vips").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    vipCache.add(uuid);
                } catch (IllegalArgumentException e) {
                    plugin.getPluginLogger().warning("UUID VIP invalide: " + uuidString);
                }
            }
        }

        plugin.getPluginLogger().info("VIP chargés: " + vipCache.size() + " joueurs");
    }

    /**
     * Sauvegarde les VIP dans le fichier
     */
    private void saveVips() {
        try {
            vipConfig.save(vipFile);
        } catch (IOException e) {
            plugin.getPluginLogger().severe("Erreur lors de la sauvegarde des VIP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ajoute un joueur VIP
     */
    public void addVip(UUID uuid, String playerName, String addedBy) {
        vipCache.add(uuid);

        // Sauvegarde dans le fichier
        String path = "vips." + uuid + ".";
        vipConfig.set(path + "playerName", playerName);
        vipConfig.set(path + "addedBy", addedBy);
        vipConfig.set(path + "addedAt", System.currentTimeMillis());

        saveVips();
        plugin.getPluginLogger().info("VIP ajouté: " + playerName + " (" + uuid + ") par " + addedBy);
    }

    /**
     * Retire un joueur VIP
     */
    public void removeVip(UUID uuid, String removedBy) {
        String playerName = vipConfig.getString("vips." + uuid + ".playerName", "Inconnu");

        vipCache.remove(uuid);
        vipConfig.set("vips." + uuid, null);
        saveVips();

        plugin.getPluginLogger().info("VIP retiré: " + playerName + " (" + uuid + ") par " + removedBy);
    }

    /**
     * Vérifie si un joueur est VIP
     */
    public boolean isVip(UUID uuid) {
        return vipCache.contains(uuid);
    }

    /**
     * Obtient les informations d'un VIP
     */
    public VipData getVipData(UUID uuid) {
        if (!isVip(uuid)) return null;

        String path = "vips." + uuid + ".";
        return new VipData(
                uuid,
                vipConfig.getString(path + "playerName"),
                vipConfig.getString(path + "addedBy"),
                vipConfig.getLong(path + "addedAt")
        );
    }

    /**
     * Obtient le nombre de VIP
     */
    public int getVipCount() {
        return vipCache.size();
    }

    /**
     * Obtient tous les UUID des VIP
     */
    public Set<UUID> getAllVips() {
        return new HashSet<>(vipCache);
    }

    /**
     * Recharge le système VIP
     */
    public void reload() {
        vipConfig = YamlConfiguration.loadConfiguration(vipFile);
        loadVips();
        plugin.getPluginLogger().info("Système VIP rechargé");
    }

    /**
     * Classe pour stocker les données VIP
     */
    public static class VipData {
        private final UUID uuid;
        private final String playerName;
        private final String addedBy;
        private final long addedAt;

        public VipData(UUID uuid, String playerName, String addedBy, long addedAt) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.addedBy = addedBy;
            this.addedAt = addedAt;
        }

        public UUID getUuid() { return uuid; }
        public String getPlayerName() { return playerName; }
        public String getAddedBy() { return addedBy; }
        public long getAddedAt() { return addedAt; }
    }
}