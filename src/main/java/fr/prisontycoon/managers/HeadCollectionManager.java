package fr.prisontycoon.managers;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du système de collection de têtes
 */
public class HeadCollectionManager {

    // Valeur de la tête spéciale
    private static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWIwZTA3NjMyMmZjOWFmNzk1OTJlYjg1MmNhOGM3YzQ1YmIyYzNjZWFiYzNjMGU4YTZhMWUwNGI0Y2UzZDM0YiJ9fX0=";
    private final PrisonTycoon plugin;
    private final Gson gson = new Gson();
    private final Type stringSetType = new TypeToken<Set<String>>() {
    }.getType();
    // Cache des têtes placées
    private final Map<String, HeadData> placedHeads = new ConcurrentHashMap<>();
    // Configuration des récompenses par nombre de têtes collectées
    private final Map<Integer, HeadReward> rewards = new LinkedHashMap<>();
    // Fichier de sauvegarde des positions des têtes
    private File headsFile;
    private FileConfiguration headsConfig;
    // Tâche pour les particules
    private BukkitTask particleTask;

    public HeadCollectionManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        setupFiles();
        loadHeadsFromFile();
        setupDefaultRewards();
        startParticleTask();

        plugin.getPluginLogger().info("§aHeadCollectionManager initialisé.");
    }

    /**
     * Configuration des fichiers YAML
     */
    private void setupFiles() {
        headsFile = new File(plugin.getDataFolder(), "heads.yml");
        if (!headsFile.exists()) {
            try {
                headsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer le fichier heads.yml: " + e.getMessage());
            }
        }
        headsConfig = YamlConfiguration.loadConfiguration(headsFile);
    }

    /**
     * Configuration des récompenses par défaut (modifiable plus tard)
     */
    private void setupDefaultRewards() {
        // Récompenses configurables - tu pourras les modifier comme tu veux
        rewards.put(1, new HeadReward(1, HeadRewardType.BASIC, "§e+100 Coins"));
        rewards.put(5, new HeadReward(5, HeadRewardType.BASIC, "§e+500 Coins"));
        rewards.put(10, new HeadReward(10, HeadRewardType.INTERMEDIATE, "§e+1000 Coins + §61 Token"));
        rewards.put(25, new HeadReward(25, HeadRewardType.ADVANCED, "§e+2500 Coins + §62 Tokens"));
        rewards.put(50, new HeadReward(50, HeadRewardType.RARE, "§e+5000 Coins + §65 Tokens"));
        rewards.put(100, new HeadReward(100, HeadRewardType.LEGENDARY, "§e+10000 Coins + §610 Tokens"));
    }

    /**
     * Charge toutes les têtes depuis le fichier YAML
     */
    private void loadHeadsFromFile() {
        if (!headsConfig.contains("heads")) return;

        for (String headId : headsConfig.getConfigurationSection("heads").getKeys(false)) {
            try {
                String worldName = headsConfig.getString("heads." + headId + ".world");
                double x = headsConfig.getDouble("heads." + headId + ".x");
                double y = headsConfig.getDouble("heads." + headId + ".y");
                double z = headsConfig.getDouble("heads." + headId + ".z");

                World world = plugin.getServer().getWorld(worldName);
                if (world != null) {
                    Location location = new Location(world, x, y, z);
                    placedHeads.put(headId, new HeadData(headId, location));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du chargement de la tête " + headId + ": " + e.getMessage());
            }
        }

        plugin.getPluginLogger().info("§a" + placedHeads.size() + " têtes chargées depuis le fichier.");
    }

    /**
     * Sauvegarde toutes les têtes dans le fichier YAML
     */
    public void saveHeadsToFile() {
        try {
            // Nettoie la section existante
            headsConfig.set("heads", null);

            // Sauvegarde toutes les têtes
            for (HeadData head : placedHeads.values()) {
                String path = "heads." + head.getId();
                headsConfig.set(path + ".world", head.getLocation().getWorld().getName());
                headsConfig.set(path + ".x", head.getLocation().getX());
                headsConfig.set(path + ".y", head.getLocation().getY());
                headsConfig.set(path + ".z", head.getLocation().getZ());
            }

            headsConfig.save(headsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des têtes: " + e.getMessage());
        }
    }

    /**
     * Enregistre une nouvelle tête placée
     */
    public void registerHeadPlacement(Location location) {
        String headId = generateHeadId();
        HeadData headData = new HeadData(headId, location);
        placedHeads.put(headId, headData);
        saveHeadsToFile();

        plugin.getPluginLogger().info("§aTête enregistrée: " + headId + " à " +
                location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
    }

    /**
     * Supprime une tête cassée
     */
    public void unregisterHeadBreak(Location location) {
        HeadData toRemove = null;
        for (HeadData head : placedHeads.values()) {
            if (head.getLocation().getBlock().equals(location.getBlock())) {
                toRemove = head;
                break;
            }
        }

        if (toRemove != null) {
            placedHeads.remove(toRemove.getId());
            saveHeadsToFile();
            plugin.getPluginLogger().info("§cTête supprimée: " + toRemove.getId());
        }
    }

    /**
     * Gère la collecte d'une tête par un joueur
     */
    public boolean collectHead(Player player, Location location) {
        HeadData head = getHeadAtLocation(location);
        if (head == null) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        Set<String> collectedHeads = playerData.getCollectedHeads();
        if (collectedHeads.contains(head.getId())) {
            player.sendMessage("§c❌ Vous avez déjà collecté cette tête !");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        // Ajouter la tête à la collection
        collectedHeads.add(head.getId());
        playerData.setCollectedHeads(collectedHeads);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Effets visuels et sonores
        location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0.5, 1, 0.5), 10, 0.3, 0.3, 0.3, 0.1);

        player.sendMessage("§a✓ Tête collectée ! (" + collectedHeads.size() + "/" + placedHeads.size() + ")");
        player.sendMessage("§7Utilisez §e/collection §7pour voir vos récompenses !");

        return true;
    }

    /**
     * Vérifie si une location contient une tête de collection
     */
    public boolean isCollectionHead(Location location) {
        return getHeadAtLocation(location) != null;
    }

    /**
     * Vérifie si c'est une tête de collection via l'ItemStack
     */
    public boolean isCollectionHeadItem(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return false;
        }

        // On s'assure que l'item a bien des métadonnées de type SkullMeta.
        if (!(item.getItemMeta() instanceof SkullMeta skull)) {
            return false;
        }

        PlayerProfile profile = skull.getPlayerProfile();
        if (profile == null) {
            return false;
        }

        // On parcourt les propriétés du profil pour trouver la texture.
        for (ProfileProperty property : profile.getProperties()) {
            if (property.getName().equals("textures")) {
                // Si on trouve la propriété "textures", on compare sa valeur
                // avec notre texture de référence.
                return HEAD_TEXTURE.equals(property.getValue());
            }
        }

        // Si aucune propriété de texture n'a été trouvée ou ne correspond, ce n'est pas la bonne tête.
        return false;
    }

    /**
     * Récupère une tête à une location donnée
     */
    private HeadData getHeadAtLocation(Location location) {
        for (HeadData head : placedHeads.values()) {
            if (head.getLocation().getBlock().equals(location.getBlock())) {
                return head;
            }
        }
        return null;
    }

    /**
     * Génère un ID unique pour une tête
     */
    private String generateHeadId() {
        return "head_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    /**
     * Démarre la tâche des particules pour les têtes non collectées
     */
    private void startParticleTask() {
        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (HeadData head : placedHeads.values()) {
                    showParticlesForUncollectedHead(head);
                }
            }
        }.runTaskTimer(plugin, 60L, 40L); // Toutes les 2 secondes après 3 secondes
    }

    /**
     * Affiche des particules pour les têtes non collectées
     */
    private void showParticlesForUncollectedHead(HeadData head) {
        Location location = head.getLocation();
        if (location.getWorld().getPlayers().isEmpty()) return;

        boolean hasUncollectedPlayers = false;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distance(location) > 50) continue;

            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (playerData != null && !playerData.getCollectedHeads().contains(head.getId())) {
                hasUncollectedPlayers = true;
                break;
            }
        }

        if (hasUncollectedPlayers) {
            location.getWorld().spawnParticle(Particle.ENCHANT,
                    location.clone().add(0.5, 1.2, 0.5), 3, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /**
     * Récupère toutes les récompenses disponibles
     */
    public Map<Integer, HeadReward> getRewards() {
        return new LinkedHashMap<>(rewards);
    }

    /**
     * Vérifie si un joueur peut réclamer une récompense
     */
    public boolean canClaimReward(Player player, int requiredHeads) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) return false;

        return playerData.getCollectedHeads().size() >= requiredHeads &&
                !playerData.getClaimedHeadRewards().contains(requiredHeads);
    }

    /**
     * Réclame une récompense - TU PEUX MODIFIER LA LOGIQUE ICI
     */
    public boolean claimReward(Player player, int rewardLevel) {
        if (!canClaimReward(player, rewardLevel)) return false;

        HeadReward reward = rewards.get(rewardLevel);
        if (reward == null) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // LOGIQUE DE RÉCOMPENSE - MODIFIE SELON TES BESOINS
        giveRewardToPlayer(player, reward);

        // Marquer comme réclamé
        Set<Integer> claimedRewards = playerData.getClaimedHeadRewards();
        claimedRewards.add(rewardLevel);
        playerData.setClaimedHeadRewards(claimedRewards);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Effets
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.sendMessage("§a✓ Récompense réclamée : " + reward.getDescription());

        return true;
    }

    /**
     * Donne la récompense au joueur - PERSONNALISE CETTE MÉTHODE
     */
    private void giveRewardToPlayer(Player player, HeadReward reward) {
        switch (reward.getType()) {
            case BASIC -> {
            }
            case INTERMEDIATE -> {

            }
            case ADVANCED -> {

            }
            case RARE -> {

            }
            case LEGENDARY -> {

                // Tu peux ajouter d'autres récompenses ici
            }
        }
    }

    /**
     * Récupère le nombre total de têtes placées
     */
    public int getTotalHeads() {
        return placedHeads.size();
    }

    /**
     * Fermeture propre du gestionnaire
     */
    public void shutdown() {
        if (particleTask != null) {
            particleTask.cancel();
        }
        saveHeadsToFile();
    }

    /**
     * Types de récompenses - tu peux en ajouter d'autres
     */
    public enum HeadRewardType {
        BASIC,
        INTERMEDIATE,
        ADVANCED,
        RARE,
        LEGENDARY
    }

    // Classes de données internes
    public static class HeadData {
        private final String id;
        private final Location location;

        public HeadData(String id, Location location) {
            this.id = id;
            this.location = location;
        }

        public String getId() {
            return id;
        }

        public Location getLocation() {
            return location;
        }
    }

    /**
     * Classe pour les récompenses - similaire au système de crates
     */
    public static class HeadReward {
        private final int requiredHeads;
        private final HeadRewardType type;
        private final String description;

        public HeadReward(int requiredHeads, HeadRewardType type, String description) {
            this.requiredHeads = requiredHeads;
            this.type = type;
            this.description = description;
        }

        public int getRequiredHeads() {
            return requiredHeads;
        }

        public HeadRewardType getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }
    }
}