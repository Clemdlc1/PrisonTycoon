package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.BlockValueData;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.ItemUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener pour les événements de minage
 * CORRIGÉ : Distinction mine/hors mine, blocs minés directement par le joueur
 */
public class MiningListener implements Listener {

    private final PrisonTycoon plugin;

    public MiningListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        var block = event.getBlock();
        var location = block.getLocation();
        var material = block.getType();

        plugin.getPluginLogger().debug("Bloc cassé: " + material + " à " +
                location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());

        // Vérifie si le joueur est dans une mine
        String mineName = plugin.getConfigManager().getPlayerMine(location);

        if (mineName != null) {
            plugin.getPluginLogger().debug("Bloc miné dans la mine: " + mineName);

            // Vérifie si le bloc peut être miné (protection)
            if (!plugin.getMineManager().canMineBlock(location, player)) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Seule la pioche légendaire peut miner dans cette zone!");
                return;
            }

            // CORRECTION : Empêche TOUS les drops (items ET exp)
            event.setDropItems(false);
            event.setExpToDrop(0); // Plus d'exp des blocs minés

            // NOUVEAU : Traite le bloc MINÉ directement par le joueur dans une mine
            processMiningInMine(player, location, material, mineName);

            // Met à jour la pioche
            plugin.getPickaxeManager().updatePlayerPickaxe(player);

            // Gère la durabilité
            var pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
            if (pickaxe != null) {
                plugin.getPickaxeManager().handleDurability(pickaxe, player);
            }

        } else {
            plugin.getPluginLogger().debug("Bloc miné hors mine - minage normal avec restrictions");

            // NOUVEAU : Hors mine, vérification de la pioche légendaire
            var handItem = player.getInventory().getItemInMainHand();
            if (handItem != null && plugin.getPickaxeManager().isLegendaryPickaxe(handItem) &&
                    plugin.getPickaxeManager().isOwner(handItem, player)) {

                // Hors mine avec pioche légendaire : seuls efficacité, solidité, mobilité actifs
                processMiningOutsideMine(player, location, material);

                // Met à jour la pioche
                plugin.getPickaxeManager().updatePlayerPickaxe(player);

                // Gère la durabilité
                plugin.getPickaxeManager().handleDurability(handItem, player);
            }
            // Sinon : comportement normal de Minecraft (pas de restrictions)
        }
    }

    /**
     * NOUVEAU : Traite le minage dans une mine (bloc MINÉ directement)
     */
    private void processMiningInMine(Player player, Location location, Material material, String mineName) {
        plugin.getPluginLogger().debug("Traitement minage dans mine: " + mineName);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Ajoute le bloc directement à l'inventaire du joueur
        addBlockToInventory(player, material);

        // NOUVEAU : Traite ce bloc MINÉ directement par le joueur
        plugin.getEnchantmentManager().processBlockMined(player, location, material, mineName);

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        plugin.getPluginLogger().debug("Bloc miné traité: " + material + " par " + player.getName());
    }

    /**
     * NOUVEAU : Traite le minage hors mine (restrictions enchantements)
     */
    private void processMiningOutsideMine(Player player, Location location, Material material) {
        plugin.getPluginLogger().debug("Traitement minage hors mine avec pioche légendaire");

        // RESTRICTION : Seuls efficacité, solidité, mobilité actifs hors mine
        // Donc PAS de gains économiques, PAS de Greeds, PAS d'effets spéciaux

        // Applique uniquement les enchantements autorisés hors mine
        plugin.getEnchantmentManager().processBlockMinedOutsideMine(player, material);

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        plugin.getPluginLogger().debug("Bloc miné hors mine traité: " + material + " par " + player.getName() +
                " (restrictions appliquées)");
    }

    /**
     * Ajoute un bloc directement à l'inventaire du joueur
     */
    private void addBlockToInventory(Player player, Material material) {
        ItemStack blockItem = new ItemStack(material, 1);

        // Essaie d'ajouter à l'inventaire
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(blockItem);

        // Si l'inventaire est plein, drop au sol avec message
        if (!leftover.isEmpty()) {
            for (ItemStack overflow : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
            player.sendMessage("§c⚠️ Inventaire plein! Blocs droppés au sol.");
        }

        plugin.getPluginLogger().debug("Bloc ajouté à l'inventaire: " + material);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        var location = event.getBlock().getLocation();

        // Empêche de placer des blocs dans les mines
        String mineName = plugin.getConfigManager().getPlayerMine(location);
        if (mineName != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c❌ Impossible de placer des blocs dans une mine!");
        }
    }
}