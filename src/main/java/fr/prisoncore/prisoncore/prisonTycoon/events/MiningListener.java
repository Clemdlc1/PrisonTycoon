package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.BlockValueData;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener pour les événements de minage
 * OPTIMISÉ : Factorisation de la logique pioche et gestion uniforme de la durabilité
 */
public class MiningListener implements Listener {

    private final PrisonTycoon plugin;

    public MiningListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        Material material = event.getBlock().getType();

        plugin.getPluginLogger().debug("Bloc cassé: " + material + " à " +
                location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

        // OPTIMISÉ : Vérification unique de la pioche légendaire
        ItemStack playerPickaxe = getPlayerLegendaryPickaxe(player);
        String mineName = plugin.getConfigManager().getPlayerMine(location);

        if (mineName != null) {
            // Dans une mine - pioche légendaire obligatoire
            if (playerPickaxe == null) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Seule la pioche légendaire peut miner dans cette zone!");
                return;
            }

            // Empêche TOUS les drops (items ET exp)
            event.setDropItems(false);
            event.setExpToDrop(0);

            // Traite le minage dans la mine
            processMiningInMine(player, location, material, mineName);

        } else if (playerPickaxe != null) {
            // Hors mine avec pioche légendaire - restrictions appliquées
            processMiningOutsideMine(player, location, material);
        }
        // Sinon : comportement normal de Minecraft (pas de pioche légendaire)

        // OPTIMISÉ : Post-traitement unifié de la pioche si elle est utilisée
        if (playerPickaxe != null) {
            postProcessLegendaryPickaxe(player, playerPickaxe);
        }
    }

    /**
     * OPTIMISÉ : Obtient la pioche légendaire du joueur s'il en a une dans le bon slot
     */
    private ItemStack getPlayerLegendaryPickaxe(Player player) {
        // Vérifie d'abord si la pioche est au bon endroit (slot 0)
        if (!plugin.getPickaxeManager().isPickaxeInCorrectSlot(player)) {
            // Essaie de trouver la pioche ailleurs et la forcer au bon slot
            ItemStack foundPickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
            if (foundPickaxe != null) {
                plugin.getPickaxeManager().enforcePickaxeSlot(player);
                return foundPickaxe;
            }
            return null;
        }

        // Pioche au bon endroit - la retourne
        ItemStack slotPickaxe = player.getInventory().getItem(0);
        if (slotPickaxe != null &&
                plugin.getPickaxeManager().isLegendaryPickaxe(slotPickaxe) &&
                plugin.getPickaxeManager().isOwner(slotPickaxe, player)) {
            return slotPickaxe;
        }

        return null;
    }

    /**
     * OPTIMISÉ : Post-traitement unifié de la pioche légendaire
     */
    private void postProcessLegendaryPickaxe(Player player, ItemStack pickaxe) {
        plugin.getPluginLogger().debug("Post-traitement pioche légendaire pour " + player.getName());

        // 1. Gère la durabilité (TOUJOURS - mine et hors mine)
        plugin.getPickaxeManager().handleDurability(pickaxe, player);

        // 2. Met à jour la pioche avec ses enchantements
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        // 3. S'assure que la pioche reste au bon slot
        plugin.getPickaxeManager().enforcePickaxeSlot(player);

        // 4. Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        plugin.getPluginLogger().debug("Post-traitement pioche terminé pour " + player.getName());
    }

    /**
     * MODIFIÉ : Traite le minage dans une mine (sans gestion pioche - déjà fait)
     */
    private void processMiningInMine(Player player, Location location, Material material, String mineName) {
        plugin.getPluginLogger().debug("Traitement minage dans mine: " + mineName);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Met à jour l'activité de minage pour l'ActionBar
        playerData.updateMiningActivity();

        // Ajoute le bloc directement à l'inventaire du joueur
        addBlockToInventory(player, material);

        // Récupère les gains de base
        BlockValueData baseValue = plugin.getConfigManager().getBlockValue(material);

        // Notifie les gains de base via le nouveau système
        if (baseValue.getCoins() > 0 || baseValue.getTokens() > 0 || baseValue.getExperience() > 0) {
            plugin.getNotificationManager().queueRegularGains(player,
                    baseValue.getCoins(), baseValue.getTokens(), baseValue.getExperience());
        }

        // Traite ce bloc MINÉ directement par le joueur (avec Greeds, enchants spéciaux, etc.)
        plugin.getEnchantmentManager().processBlockMined(player, location, material, mineName);

        plugin.getPluginLogger().debug("Bloc miné traité: " + material + " par " + player.getName());
    }

    /**
     * MODIFIÉ : Traite le minage hors mine (sans gestion pioche - déjà fait)
     */
    private void processMiningOutsideMine(Player player, Location location, Material material) {
        plugin.getPluginLogger().debug("Traitement minage hors mine avec pioche légendaire");

        // Applique uniquement les enchantements autorisés hors mine
        plugin.getEnchantmentManager().processBlockMinedOutsideMine(player, material);

        // Notifie les restrictions occasionnellement
        if (Math.random() < 0.1) { // 10% chance de rappeler les restrictions
            plugin.getNotificationManager().queueSpecialStateNotification(player,
                    "Hors Mine", "§7Greeds et effets spéciaux inactifs");
        }

        plugin.getPluginLogger().debug("Bloc miné hors mine traité: " + material + " par " + player.getName() +
                " (restrictions appliquées)");
    }

    /**
     * OPTIMISÉ : Ajoute un bloc à l'inventaire avec gestion d'erreur
     */
    private void addBlockToInventory(Player player, Material material) {
        ItemStack blockItem = new ItemStack(material, 1);

        // Essaie d'ajouter à l'inventaire
        var leftover = player.getInventory().addItem(blockItem);

        if (!leftover.isEmpty()) {
            plugin.getPluginLogger().debug("Inventaire plein pour " + player.getName() +
                    " - bloc " + material + " perdu");
        } else {
            plugin.getPluginLogger().debug("Bloc ajouté à l'inventaire: " + material +
                    " pour " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        Location location = event.getBlock().getLocation();

        // Empêche de placer des blocs dans les mines
        String mineName = plugin.getConfigManager().getPlayerMine(location);
        if (mineName != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c❌ Impossible de placer des blocs dans une mine!");

            plugin.getPluginLogger().debug("Tentative de placement de bloc bloquée dans mine " +
                    mineName + " par " + event.getPlayer().getName());
        }
    }
}