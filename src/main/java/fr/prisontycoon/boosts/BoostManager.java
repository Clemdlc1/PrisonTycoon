package fr.prisontycoon.boosts;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.Gang;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.gangs.GangBoostType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du système de boosts temporaires
 */
public class BoostManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey boostTypeKey;
    private final NamespacedKey boostDurationKey;
    private final NamespacedKey boostBonusKey;

    // Boosts actifs par joueur (non-admin uniquement)
    private final Map<UUID, Map<BoostType, PlayerBoost>> activePlayerBoosts;
    private final Map<String, Map<GangBoostType, GangBoost>> activeGangBoosts; // gangId -> boosts

    // Boosts admin globaux (temporaires, non sauvegardés)
    private final Map<BoostType, PlayerBoost> globalAdminBoosts;

    public BoostManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.boostTypeKey = new NamespacedKey(plugin, "boost_type");
        this.boostDurationKey = new NamespacedKey(plugin, "boost_duration");
        this.boostBonusKey = new NamespacedKey(plugin, "boost_bonus");

        this.activePlayerBoosts = new ConcurrentHashMap<>();
        this.activeGangBoosts = new ConcurrentHashMap<>();

        this.globalAdminBoosts = new ConcurrentHashMap<>();

        // Lance la tâche de nettoyage des boosts expirés
        startCleanupTask();
    }

    /**
     * Crée un item boost
     */
    public ItemStack createBoostItem(BoostType type, int durationMinutes, double bonusPercentage) {
        ItemStack boost = new ItemStack(type.getMaterial());
        ItemMeta meta = boost.getItemMeta();

        // Nom de l'item
        plugin.getGUIManager().applyName(meta, type.getItemName());

        // Lore
        List<String> lore = Arrays.asList(
                "",
                type.getDescription(),
                "§7Bonus: " + type.getColor() + "+" + String.format("%.0f", bonusPercentage) + "%",
                "§7Durée: §e" + durationMinutes + " minutes",
                "",
                "§e▶ Clic droit pour activer",
                "§c⚠ Maximum 1 boost par type",
                "",
                "§8Boost Temporaire"
        );
        plugin.getGUIManager().applyLore(meta, lore);

        // Ajoute les données persistantes
        meta.getPersistentDataContainer().set(boostTypeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(boostDurationKey, PersistentDataType.INTEGER, durationMinutes * 60);
        meta.getPersistentDataContainer().set(boostBonusKey, PersistentDataType.DOUBLE, bonusPercentage);

        boost.setItemMeta(meta);
        return boost;
    }

    /**
     * Vérifie si un item est un boost
     */
    public boolean isBoostItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(boostTypeKey, PersistentDataType.STRING);
    }

    /**
     * Obtient le type de boost d'un item
     */
    public BoostType getBoostType(ItemStack item) {
        if (!isBoostItem(item)) return null;

        String typeName = item.getItemMeta().getPersistentDataContainer()
                .get(boostTypeKey, PersistentDataType.STRING);

        return BoostType.fromString(typeName);
    }

    /**
     * Active un boost pour un joueur
     */
    public boolean activateBoost(Player player, ItemStack boostItem) {
        if (!isBoostItem(boostItem)) {
            return false;
        }

        BoostType type = getBoostType(boostItem);
        if (type == null) {
            player.sendMessage("§cBoost invalide!");
            return false;
        }

        // Vérifie si le joueur a déjà ce type de boost
        if (hasActiveBoost(player, type)) {
            player.sendMessage("§c❌ Vous avez déjà un boost " + type.getFormattedName() + " §cactif!");
            player.sendMessage("§7Attendez qu'il expire ou utilisez §e/boost §7pour voir vos boosts.");
            return false;
        }

        ItemMeta meta = boostItem.getItemMeta();
        int durationSeconds = meta.getPersistentDataContainer()
                .getOrDefault(boostDurationKey, PersistentDataType.INTEGER, type.getDefaultDurationSeconds());
        double bonusPercentage = meta.getPersistentDataContainer()
                .getOrDefault(boostBonusKey, PersistentDataType.DOUBLE, type.getBonusPercentage());

        // Crée et active le boost
        PlayerBoost boost = new PlayerBoost(type, durationSeconds, bonusPercentage);
        addPlayerBoost(player, boost);

        // Messages et effets
        player.sendMessage("§a✅ §lBoost activé!");
        player.sendMessage("§7Type: " + type.getFormattedName());
        player.sendMessage("§7Bonus: " + type.getColor() + "+" + String.format("%.0f", bonusPercentage) + "%");
        player.sendMessage("§7Durée: §e" + (durationSeconds / 60) + " minutes");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);

        // Retire l'item de l'inventaire
        if (boostItem.getAmount() > 1) {
            boostItem.setAmount(boostItem.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        return true;
    }

    /**
     * Ajoute un boost à un joueur
     */
    private void addPlayerBoost(Player player, PlayerBoost boost) {
        UUID playerId = player.getUniqueId();

        activePlayerBoosts.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .put(boost.getType(), boost);

        // Sauvegarde si ce n'est pas un boost admin
        if (!boost.isAdminBoost()) {
            savePlayerBoosts(player);
        }
    }

    /**
     * Vérifie si un joueur a un boost actif d'un type donné
     */
    public boolean hasActiveBoost(Player player, BoostType type) {
        // Vérifie les boosts du joueur
        Map<BoostType, PlayerBoost> playerBoosts = activePlayerBoosts.get(player.getUniqueId());
        if (playerBoosts != null) {
            PlayerBoost boost = playerBoosts.get(type);
            if (boost != null && boost.isActive()) {
                return true;
            }
        }

        // Vérifie les boosts admin globaux
        PlayerBoost adminBoost = globalAdminBoosts.get(type);
        return adminBoost != null && adminBoost.isActive();
    }

    /**
     * Obtient le bonus total pour un type de boost
     */
    public double getTotalBoostBonus(Player player, BoostType type) {
        double totalBonus = 0.0;

        // Bonus du joueur
        Map<BoostType, PlayerBoost> playerBoosts = activePlayerBoosts.get(player.getUniqueId());
        if (playerBoosts != null) {
            PlayerBoost boost = playerBoosts.get(type);
            if (boost != null && boost.isActive()) {
                totalBonus += boost.getBonusPercentage();
            }
        }

        // Bonus admin global
        PlayerBoost adminBoost = globalAdminBoosts.get(type);
        if (adminBoost != null && adminBoost.isActive()) {
            totalBonus += adminBoost.getBonusPercentage();
        }

        // Bonus du boost global (s'applique à tous les types)
        PlayerBoost globalBoost;

        // Global du joueur
        if (playerBoosts != null) {
            globalBoost = playerBoosts.get(BoostType.GLOBAL_BOOST);
            if (globalBoost != null && globalBoost.isActive()) {
                totalBonus += globalBoost.getBonusPercentage();
            }
        }

        // Global admin
        globalBoost = globalAdminBoosts.get(BoostType.GLOBAL_BOOST);
        if (globalBoost != null && globalBoost.isActive()) {
            totalBonus += globalBoost.getBonusPercentage();
        }

        return totalBonus;
    }

    /**
     * Obtient tous les boosts actifs d'un joueur
     */
    public List<PlayerBoost> getActiveBoosts(Player player) {
        List<PlayerBoost> boosts = new ArrayList<>();

        // Boosts du joueur
        Map<BoostType, PlayerBoost> playerBoosts = activePlayerBoosts.get(player.getUniqueId());
        if (playerBoosts != null) {
            for (PlayerBoost boost : playerBoosts.values()) {
                if (boost.isActive()) {
                    boosts.add(boost);
                }
            }
        }

        // Boosts admin globaux
        for (PlayerBoost boost : globalAdminBoosts.values()) {
            if (boost.isActive()) {
                boosts.add(boost);
            }
        }

        return boosts;
    }

    /**
     * Ajoute un boost admin global
     */
    public void addAdminBoost(BoostType type, int durationSeconds, double bonusPercentage) {
        PlayerBoost adminBoost = new PlayerBoost(type, durationSeconds, bonusPercentage, true);
        globalAdminBoosts.put(type, adminBoost);

        // Annonce à tous les joueurs
        String message = "§6⚡ §lBOOST ADMIN ACTIVÉ!";
        String details = "§7Type: " + type.getFormattedName() +
                " §7Bonus: " + type.getColor() + "+" + String.format("%.0f", bonusPercentage) + "% " +
                "§7Durée: §e" + (durationSeconds / 60) + " minutes";

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(message);
            player.sendMessage(details);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.2f);
        }

        plugin.getPluginLogger().info("Boost admin activé: " + type.name() + " +" + bonusPercentage + "% pour " + (durationSeconds / 60) + " minutes");
    }

    /**
     * Sauvegarde les boosts d'un joueur
     */
    public void savePlayerBoosts(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Map<BoostType, PlayerBoost> playerBoosts = activePlayerBoosts.get(player.getUniqueId());

        if (playerBoosts == null || playerBoosts.isEmpty()) {
            playerData.clearActiveBoosts();
        } else {
            // Filtre seulement les boosts actifs et non-admin
            Map<String, PlayerBoost> boostsToSave = new HashMap<>();
            for (Map.Entry<BoostType, PlayerBoost> entry : playerBoosts.entrySet()) {
                PlayerBoost boost = entry.getValue();
                if (boost.isActive() && !boost.isAdminBoost()) {
                    boostsToSave.put(entry.getKey().name(), boost);
                }
            }
            playerData.setActiveBoosts(boostsToSave);
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Charge les boosts d'un joueur depuis la sauvegarde
     */
    public void loadPlayerBoosts(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Map<String, PlayerBoost> savedBoosts = playerData.getActiveBoosts();

        if (savedBoosts == null || savedBoosts.isEmpty()) {
            return;
        }

        Map<BoostType, PlayerBoost> playerBoosts = new ConcurrentHashMap<>();

        for (Map.Entry<String, PlayerBoost> entry : savedBoosts.entrySet()) {
            try {
                BoostType type = BoostType.valueOf(entry.getKey());
                PlayerBoost boost = entry.getValue();

                // Seulement charge les boosts encore actifs
                if (boost.isActive()) {
                    playerBoosts.put(type, boost);
                }
            } catch (IllegalArgumentException e) {
                plugin.getPluginLogger().warning("Type de boost invalide lors du chargement: " + entry.getKey());
            }
        }

        if (!playerBoosts.isEmpty()) {
            activePlayerBoosts.put(player.getUniqueId(), playerBoosts);
            plugin.getPluginLogger().debug("Boosts chargés pour " + player.getName() + ": " + playerBoosts.size());
        }
    }

    /**
     * Retire un joueur du cache et sauvegarde ses boosts
     */
    public void unloadPlayer(Player player) {
        savePlayerBoosts(player);
        activePlayerBoosts.remove(player.getUniqueId());
    }

    /**
     * Lance la tâche de nettoyage des boosts expirés
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredBoosts();
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 30); // Toutes les 30 secondes
    }

    /**
     * Nettoie les boosts expirés
     */
    private void cleanupExpiredBoosts() {
        // Nettoie les boosts des joueurs
        for (Map.Entry<UUID, Map<BoostType, PlayerBoost>> playerEntry : activePlayerBoosts.entrySet()) {
            Map<BoostType, PlayerBoost> playerBoosts = playerEntry.getValue();

            playerBoosts.entrySet().removeIf(entry -> !entry.getValue().isActive());

            // Si le joueur est en ligne, sauvegarde
            Player player = Bukkit.getPlayer(playerEntry.getKey());
            if (player != null && player.isOnline()) {
                savePlayerBoosts(player);
            }
        }

        // Nettoie les boosts admin globaux expirés
        globalAdminBoosts.entrySet().removeIf(entry -> {
            if (!entry.getValue().isActive()) {
                // Annonce l'expiration du boost admin
                String message = "§c⏰ Boost admin expiré: " + entry.getKey().getFormattedName();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(message);
                }
                return true;
            }
            return false;
        });


        // Nettoie les boosts de gang expirés
        for (Map.Entry<String, Map<GangBoostType, GangBoost>> gangEntry : activeGangBoosts.entrySet()) {
            String gangId = gangEntry.getKey();
            Map<GangBoostType, GangBoost> gangBoosts = gangEntry.getValue();

            Iterator<Map.Entry<GangBoostType, GangBoost>> iterator = gangBoosts.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<GangBoostType, GangBoost> entry = iterator.next();
                if (entry.getValue().isExpired()) {
                    iterator.remove();

                    // Notifier le gang de l'expiration
                    Gang gang = plugin.getGangManager().getGang(gangId);
                    if (gang != null) {
                        gang.broadcast("§c⏰ Boost " + entry.getKey().getDisplayName() + " §cexpiré!", null);
                    }
                }
            }

            // Supprimer les maps vides
            if (gangBoosts.isEmpty()) {
                activeGangBoosts.remove(gangId);
            }
        }
    }

    /**
     * Ajoute un boost de gang
     */
    public void addGangBoost(Gang gang, GangBoostType boostType, int tier) {
        double[] multipliers = {1.5, 2.0, 3.0};
        int[] durations = {30, 60, 180}; // minutes

        long durationMs = durations[tier - 1] * 60 * 1000;
        long expirationTime = System.currentTimeMillis() + durationMs;

        GangBoost boostData = new GangBoost(
                boostType,
                multipliers[tier - 1],
                expirationTime,
                gang.getLeader()
        );

        activeGangBoosts.computeIfAbsent(gang.getId(), k -> new ConcurrentHashMap<>())
                .put(boostType, boostData);

        plugin.getPluginLogger().info("Boost de gang activé: " + boostType + " x" + multipliers[tier - 1] +
                " pour " + gang.getName() + " (" + durations[tier - 1] + " min)");
    }

    /**
     * Vérifie si un gang a un boost actif
     */
    public boolean hasActiveGangBoost(String gangId, GangBoostType boostType) {
        Map<GangBoostType, GangBoost> gangBoosts = activeGangBoosts.get(gangId);
        if (gangBoosts == null) return false;

        GangBoost boostData = gangBoosts.get(boostType);
        if (boostData == null || boostData.isExpired()) {
            if (boostData != null) {
                gangBoosts.remove(boostType);
            }
            return false;
        }

        return true;
    }

    /**
     * Obtient le multiplicateur d'un boost de gang
     */
    public double getGangBoostMultiplier(String gangId, GangBoostType boostType) {
        if (!hasActiveGangBoost(gangId, boostType)) return 1.0;

        Map<GangBoostType, GangBoost> gangBoosts = activeGangBoosts.get(gangId);
        GangBoost boostData = gangBoosts.get(boostType);

        return boostData != null ? boostData.multiplier() : 1.0;
    }

    /**
     * Obtient le bonus total incluant les boosts individuels et de gang
     */
    public double getTotalBoostBonusWithGang(Player player, BoostType type) {
        double totalBonus = getTotalBoostBonus(player, type);

        // Ajouter les boosts de gang
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getGangId() != null) {
            GangBoostType gangBoostType = mapBoostTypeToGangBoostType(type);
            if (gangBoostType != null) {
                double gangMultiplier = getGangBoostMultiplier(playerData.getGangId(), gangBoostType);
                if (gangMultiplier > 1.0) {
                    totalBonus += (gangMultiplier - 1.0) * 100; // Convertir multiplicateur en pourcentage
                }
            }
        }

        return totalBonus;
    }

    /**
     * Mappe un BoostType vers un GangBoostType
     */
    private GangBoostType mapBoostTypeToGangBoostType(BoostType type) {
        return switch (type) {
            case SELL_BOOST -> GangBoostType.VENTE;
            case TOKEN_BOOST -> GangBoostType.TOKEN;
            case EXPERIENCE_BOOST -> GangBoostType.XP;
            case BEACON_BOOST -> GangBoostType.BEACONS;
            default -> null;
        };
    }

    /**
     * Obtient tous les boosts de gang actifs pour un gang
     */
    public List<GangBoost> getActiveGangBoosts(String gangId) {
        Map<GangBoostType, GangBoost> gangBoosts = activeGangBoosts.get(gangId);
        if (gangBoosts == null) return new ArrayList<>();

        List<GangBoost> activeBoosts = new ArrayList<>();
        Iterator<Map.Entry<GangBoostType, GangBoost>> iterator = gangBoosts.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<GangBoostType, GangBoost> entry = iterator.next();
            GangBoost boostData = entry.getValue();

            if (boostData.isExpired()) {
                iterator.remove();
            } else {
                activeBoosts.add(boostData);
            }
        }

        return activeBoosts;
    }
}
