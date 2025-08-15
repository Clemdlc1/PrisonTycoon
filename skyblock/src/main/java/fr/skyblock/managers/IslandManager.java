package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;

public class IslandManager {

    private final CustomSkyblock plugin;

    public IslandManager(CustomSkyblock plugin) {
        this.plugin = plugin;
    }

    /**
     * Crée une nouvelle île pour un joueur avec le schematic par défaut
     */
    public Island createIsland(Player player) {
        return createIslandWithSchematic(player, "classic");
    }

    /**
     * Crée une nouvelle île pour un joueur avec un schematic spécifique
     */
    public Island createIslandWithSchematic(Player player, String schematicName) {
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().getOrCreatePlayer(player.getUniqueId(), player.getName());

        if (skyblockPlayer.hasIsland()) {
            return null; // Le joueur a déjà une île
        }

        // Utiliser le SchematicManager pour créer l'île
        return plugin.getSchematicManager().createIslandWithSchematic(player, schematicName);
    }

    /**
     * Supprime une île et son monde
     */
    public boolean deleteIsland(Island island) {
        if (island == null) return false;

        // Supprimer le monde associé
        plugin.getWorldManager().deleteIslandWorld(island);

        // Mettre à jour les données des joueurs
        SkyblockPlayer owner = plugin.getDatabaseManager().loadPlayer(island.getOwner());
        if (owner != null) {
            owner.deleteIsland();
            plugin.getDatabaseManager().savePlayer(owner);
        }

        // Retirer tous les membres
        for (UUID memberUuid : island.getMembers()) {
            SkyblockPlayer member = plugin.getDatabaseManager().loadPlayer(memberUuid);
            if (member != null) {
                member.leaveIsland(island.getId());
                plugin.getDatabaseManager().savePlayer(member);
            }
        }

        // Supprimer l'île de la base de données
        plugin.getDatabaseManager().deleteIsland(island.getId());

        return true;
    }

    /**
     * Téléporte un joueur à son île
     */
    public boolean teleportToIsland(Player player, Island island) {
        if (island == null) return false;

        // Obtenir ou créer le monde de l'île
        World world = plugin.getWorldManager().getOrCreateIslandWorld(island);
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Impossible de charger le monde de l'île !");
            return false;
        }

        Location teleportLocation;

        // Vérifier s'il y a un home personnalisé
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(player.getUniqueId());
        if (skyblockPlayer != null && skyblockPlayer.hasData("home_x")) {
            double x = skyblockPlayer.getData("home_x", Double.class);
            double y = skyblockPlayer.getData("home_y", Double.class);
            double z = skyblockPlayer.getData("home_z", Double.class);
            float yaw = skyblockPlayer.getData("home_yaw", Double.class).floatValue();
            float pitch = skyblockPlayer.getData("home_pitch", Double.class).floatValue();

            teleportLocation = new Location(world, x, y, z, yaw, pitch);
        } else {
            // Position par défaut au centre
            teleportLocation = new Location(world, 0, 65, 0);
        }

        // Téléportation
        player.teleport(teleportLocation);
        plugin.getWorldManager().markPlayerEntered(world.getName());
        island.updateActivity();
        
        // Sauvegarder l'île
        plugin.getDatabaseManager().saveIsland(island);

        // Mettre à jour la world border
        updateWorldBorder(player, island);

        return true;
    }

    /**
     * Met à jour la world border pour un joueur
     */
    public void updateWorldBorder(Player player, Island island) {
        World world = plugin.getWorldManager().getIslandWorld(island);
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0); // Centre du monde de l'île
        border.setSize(island.getSize());
        border.setWarningDistance(5);
    }

    /**
     * Vérifie si un joueur est sur une île spécifique
     */
    public boolean isPlayerOnIsland(Player player, Island island) {
        World playerWorld = player.getLocation().getWorld();
        World islandWorld = plugin.getWorldManager().getIslandWorld(island);

        return playerWorld != null && playerWorld.equals(islandWorld);
    }

    /**
     * Trouve l'île à une location donnée
     */
    public Island getIslandAtLocation(Location location) {
        if (location.getWorld() == null) return null;

        String worldName = location.getWorld().getName();
        UUID islandId = plugin.getWorldManager().getIslandIdFromWorldName(worldName);

        if (islandId != null) {
            return plugin.getDatabaseManager().loadIsland(islandId);
        }

        return null;
    }

    /**
     * Agrandit une île
     */
    public boolean expandIsland(Island island, int newSize) {
        if (newSize > plugin.getMaxIslandSize() || newSize <= island.getSize()) {
            return false;
        }

        // Vérifier et charger le coût avec PrisonTycoon
        Player owner = Bukkit.getPlayer(island.getOwner());
        if (owner == null) {
            return false; // Le propriétaire doit être en ligne pour payer
        }

        if (!plugin.getPrisonTycoonHook().canExpandIsland(owner.getUniqueId(), island.getSize(), newSize)) {
            return false; // Pas assez de beacons
        }

        if (!plugin.getPrisonTycoonHook().chargeExpandIsland(owner, island.getSize(), newSize)) {
            return false; // Échec du paiement
        }

        island.setSize(newSize);

        // Mettre à jour la world border
        plugin.getWorldManager().updateWorldBorder(island);

        // Mettre à jour la world border pour les joueurs en ligne sur l'île
        World islandWorld = plugin.getWorldManager().getIslandWorld(island);
        if (islandWorld != null) {
            for (Player player : islandWorld.getPlayers()) {
                updateWorldBorder(player, island);
            }
        }

        plugin.getDatabaseManager().saveIsland(island);
        return true;
    }

    /**
     * Améliore le niveau d'une île
     */
    public boolean upgradeLevelIsland(Island island, int newLevel) {
        if (newLevel <= island.getLevel()) {
            return false;
        }

        // Vérifier et charger le coût avec PrisonTycoon
        Player owner = Bukkit.getPlayer(island.getOwner());
        if (owner == null) {
            return false; // Le propriétaire doit être en ligne pour payer
        }

        if (!plugin.getPrisonTycoonHook().canUpgradeLevel(owner.getUniqueId(), island.getLevel(), newLevel)) {
            return false; // Pas assez de coins
        }

        if (!plugin.getPrisonTycoonHook().chargeLevelUpgrade(owner, island.getLevel(), newLevel)) {
            return false; // Échec du paiement
        }

        island.setLevel(newLevel);
        plugin.getDatabaseManager().saveIsland(island);
        return true;
    }

    /**
     * Ajoute un membre à une île
     */
    public boolean addMember(Island island, UUID playerUuid) {
        if (island.isMember(playerUuid)) {
            return false; // Déjà membre
        }

        island.addMember(playerUuid);

        // Mettre à jour les données du joueur
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(playerUuid);
        if (skyblockPlayer != null) {
            skyblockPlayer.joinIsland(island.getId());
            plugin.getDatabaseManager().savePlayer(skyblockPlayer);
        }

        plugin.getDatabaseManager().saveIsland(island);
        return true;
    }

    /**
     * Retire un membre d'une île
     */
    public boolean removeMember(Island island, UUID playerUuid) {
        if (!island.isMember(playerUuid) || island.getOwner().equals(playerUuid)) {
            return false; // Pas membre ou propriétaire
        }

        island.removeMember(playerUuid);

        // Téléporter le joueur hors de l'île s'il est en ligne
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null && isPlayerOnIsland(player, island)) {
            Location spawnLocation = Bukkit.getWorlds().get(0).getSpawnLocation();
            player.teleport(spawnLocation);
            player.sendMessage(ChatColor.RED + "Vous avez été expulsé de l'île !");
        }

        // Mettre à jour les données du joueur
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(playerUuid);
        if (skyblockPlayer != null) {
            skyblockPlayer.leaveIsland(island.getId());
            plugin.getDatabaseManager().savePlayer(skyblockPlayer);
        }

        plugin.getDatabaseManager().saveIsland(island);
        return true;
    }

    /**
     * Vérifie si un monde est un monde d'île
     */
    public boolean isIslandWorld(World world) {
        return plugin.getWorldManager().isIslandWorld(world.getName());
    }

    /**
     * Obtient l'île associée à un monde
     */
    public Island getIslandFromWorld(World world) {
        UUID islandId = plugin.getWorldManager().getIslandIdFromWorldName(world.getName());
        if (islandId != null) {
            return plugin.getDatabaseManager().loadIsland(islandId);
        }
        return null;
    }

    /**
     * Charge le monde d'une île si nécessaire
     */
    public World ensureIslandWorldLoaded(Island island) {
        return plugin.getWorldManager().getOrCreateIslandWorld(island);
    }

    public World getIslandWorld(Island island) {
        return plugin.getWorldManager().getIslandWorld(island);
    }

    /**
     * Récupère toutes les îles avec leur monde chargé
     */
    public List<Island> getAllIslandsWithWorlds() {
        List<Island> islandsWithWorlds = new ArrayList<>();

        for (Island island : plugin.getDatabaseManager().getAllIslands()) {
            if (plugin.getWorldManager().hasWorld(island)) {
                islandsWithWorlds.add(island);
            }
        }

        return islandsWithWorlds;
    }

    /**
     * Obtient l'île d'un joueur (propriétaire ou membre)
     */
    public Island getPlayerIsland(UUID playerId) {
        // D'abord vérifier si le joueur est propriétaire d'une île
        Island ownedIsland = plugin.getDatabaseManager().getIslandByOwner(playerId);
        if (ownedIsland != null) {
            return ownedIsland;
        }

        // Sinon, vérifier si le joueur est membre d'une île
        for (Island island : plugin.getDatabaseManager().getAllIslands()) {
            if (island.isMember(playerId)) {
                return island;
            }
        }

        return null;
    }

    /**
     * Obtient une île par son ID
     */
    public Island getIslandById(UUID islandId) {
        return plugin.getDatabaseManager().loadIsland(islandId);
    }

    /**
     * Nettoie les îles sans monde
     */
    public void cleanupOrphanedIslands() {
        List<Island> orphanedIslands = new ArrayList<>();

        for (Island island : plugin.getDatabaseManager().getAllIslands()) {
            if (!plugin.getWorldManager().hasWorld(island)) {
                orphanedIslands.add(island);
            }
        }

        if (!orphanedIslands.isEmpty()) {
            plugin.getLogger().info("Nettoyage de " + orphanedIslands.size() + " îles orphelines...");

            for (Island island : orphanedIslands) {
                // Optionnel: supprimer l'île ou essayer de recréer son monde
                plugin.getLogger().warning("Île orpheline trouvée: " + island.getId() + " (propriétaire: " + island.getOwner() + ")");
            }
        }
    }
}