package fr.skyblock.listeners;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Animals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;

public class IslandListener implements Listener {

    private final CustomSkyblock plugin;

    public IslandListener(CustomSkyblock plugin) {
        this.plugin = plugin;
    }

    // === PROTECTION DES BLOCS ===

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        if (!isInSkyblockWorld(location)) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(location);
        if (island == null) {
            // Hors de toute île - autoriser seulement les admins
            if (!player.hasPermission("skyblock.admin")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas casser des blocs ici !");
            }
            return;
        }

        // Vérifier les permissions
        if (island.isMember(player.getUniqueId())) {
            // Membre ou propriétaire - toujours autorisé
            island.updateActivity();
            safeSaveIsland(island);
            return;
        }

        // Visiteur - vérifier le flag
        if (!island.getFlag(Island.IslandFlag.VISITOR_BREAK)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas autorisé à casser des blocs sur cette île !");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        if (!isInSkyblockWorld(location)) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(location);
        if (island == null) {
            // Hors de toute île - autoriser seulement les admins
            if (!player.hasPermission("skyblock.admin")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas placer des blocs ici !");
            }
            return;
        }

        // Vérifier les permissions
        if (island.isMember(player.getUniqueId())) {
            // Membre ou propriétaire - toujours autorisé
            island.updateActivity();
            safeSaveIsland(island);
            return;
        }

        // Visiteur - vérifier le flag
        if (!island.getFlag(Island.IslandFlag.VISITOR_PLACE)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas autorisé à placer des blocs sur cette île !");
        }
    }

    // === PROTECTION DES INTERACTIONS ===

    /**
     * Sauvegarde sécurisée d'une île seulement si elle est chargée
     */
    private void safeSaveIsland(Island island) {
        if (plugin.getDatabaseManager().isIslandLoaded(island.getId())) {
            plugin.getDatabaseManager().saveIsland(island);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null || !isInSkyblockWorld(block.getLocation())) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(block.getLocation());
        if (island == null) return;

        // Membre ou propriétaire - toujours autorisé
        if (island.isMember(player.getUniqueId())) {
            island.updateActivity();
            safeSaveIsland(island);
            return;
        }

        // Vérifier si c'est un bloc interactif
        Material material = block.getType();
        if (isInteractiveBlock(material)) {
            if (!island.getFlag(Island.IslandFlag.VISITOR_INTERACT)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous n'êtes pas autorisé à interagir avec cet objet !");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Location location = event.getInventory().getLocation();
        if (location == null || !isInSkyblockWorld(location)) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(location);
        if (island == null) return;

        // Membre ou propriétaire - toujours autorisé
        if (island.isMember(player.getUniqueId())) {
            island.updateActivity();
            safeSaveIsland(island);
            return;
        }

        // Vérifier si c'est un coffre ou container
        if (event.getInventory().getHolder() instanceof Chest ||
                event.getInventory().getHolder() instanceof ShulkerBox) {

            if (!island.getFlag(Island.IslandFlag.VISITOR_CHEST)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous n'êtes pas autorisé à ouvrir les coffres sur cette île !");
            }
        }
    }

    // === PROTECTION PVP ===

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        Location location = victim.getLocation();
        if (!isInSkyblockWorld(location)) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(location);
        if (island == null) return;

        // Vérifier le flag PVP
        if (!island.getFlag(Island.IslandFlag.PVP)) {
            event.setCancelled(true);
            attacker.sendMessage(ChatColor.RED + "Le PvP est désactivé sur cette île !");
        }
    }

    // === PROTECTION CONTRE LES EXPLOSIONS ===

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        Location location = event.getLocation();
        if (!isInSkyblockWorld(location)) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(location);
        if (island == null) {
            // Hors de toute île - bloquer les explosions
            event.setCancelled(true);
            return;
        }

        // Vérifier le flag d'explosion
        if (!island.getFlag(Island.IslandFlag.EXPLOSION_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    // === GESTION DU FEU ===

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBurn(BlockBurnEvent event) {
        Location location = event.getBlock().getLocation();
        if (!isInSkyblockWorld(location)) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(location);
        if (island == null) {
            event.setCancelled(true);
            return;
        }

        // Vérifier le flag de propagation du feu
        if (!island.getFlag(Island.IslandFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockIgnite(BlockIgniteEvent event) {
        Location location = event.getBlock().getLocation();
        if (!isInSkyblockWorld(location)) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(location);
        if (island == null) {
            // Hors de toute île - bloquer l'allumage sauf admin
            if (event.getPlayer() == null || !event.getPlayer().hasPermission("skyblock.admin")) {
                event.setCancelled(true);
            }
            return;
        }

        // Si c'est allumé par un joueur, vérifier les permissions
        if (event.getPlayer() != null) {
            Player player = event.getPlayer();

            if (!island.isMember(player.getUniqueId()) && !island.getFlag(Island.IslandFlag.VISITOR_INTERACT)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous n'êtes pas autorisé à allumer du feu ici !");
                return;
            }
        }

        // Vérifier le flag de propagation du feu pour l'allumage naturel
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD && !island.getFlag(Island.IslandFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    // === GESTION DES SPAWNS D'ENTITÉS ===

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        Location location = event.getLocation();

        if (!isInSkyblockWorld(location)) return;

        Island island = plugin.getIslandManager().getIslandAtLocation(location);
        if (island == null) {

            if (event instanceof CreatureSpawnEvent) {
                CreatureSpawnEvent creatureEvent = (CreatureSpawnEvent) event;
                CreatureSpawnEvent.SpawnReason reason = creatureEvent.getSpawnReason();

                if (reason != CreatureSpawnEvent.SpawnReason.COMMAND &&
                        reason != CreatureSpawnEvent.SpawnReason.CUSTOM &&
                        reason != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
                    event.setCancelled(true);
                }
            }
            return;
        }

        // Vérifier les flags de spawn selon le type d'entité
        if (entity instanceof Monster) {
            if (!island.getFlag(Island.IslandFlag.MOB_SPAWNING)) {
                event.setCancelled(true);
            }
        } else if (entity instanceof Animals) {
            if (!island.getFlag(Island.IslandFlag.ANIMAL_SPAWNING)) {
                event.setCancelled(true);
            }
        }
    }

    // === MÉTHODES UTILITAIRES ===

    private boolean isInSkyblockWorld(Location location) {
        return location.getWorld() != null &&
                plugin.getIslandManager().isIslandWorld(location.getWorld());
    }

    private boolean isInteractiveBlock(Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL,
                 FURNACE, BLAST_FURNACE, SMOKER,
                 CRAFTING_TABLE, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 BREWING_STAND, CAULDRON, WATER_CAULDRON, LAVA_CAULDRON, POWDER_SNOW_CAULDRON,
                 LECTERN, CARTOGRAPHY_TABLE, FLETCHING_TABLE, SMITHING_TABLE,
                 STONECUTTER, LOOM, GRINDSTONE,
                 DISPENSER, DROPPER, HOPPER,
                 LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON,
                 JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON, CHERRY_BUTTON,
                 STONE_PRESSURE_PLATE, OAK_PRESSURE_PLATE, SPRUCE_PRESSURE_PLATE,
                 BIRCH_PRESSURE_PLATE, JUNGLE_PRESSURE_PLATE, ACACIA_PRESSURE_PLATE,
                 DARK_OAK_PRESSURE_PLATE, MANGROVE_PRESSURE_PLATE, CHERRY_PRESSURE_PLATE,
                 LIGHT_WEIGHTED_PRESSURE_PLATE, HEAVY_WEIGHTED_PRESSURE_PLATE,
                 TRIPWIRE_HOOK, REDSTONE_WIRE,
                 REPEATER, COMPARATOR,
                 JUKEBOX, NOTE_BLOCK,
                 BEACON, CONDUIT,
                 SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX,
                 BLUE_SHULKER_BOX, BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> material.name().contains("DOOR") ||
                    material.name().contains("GATE") ||
                    material.name().contains("TRAPDOOR") ||
                    material.name().contains("FENCE_GATE") ||
                    material.name().contains("BED");
        };
    }
}