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
 * CORRIGÉ : Plus d'XP des blocs, seulement via Exp Greed
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
            plugin.getPluginLogger().debug("Bloc cassé dans la mine: " + mineName);

            // Vérifie si le bloc peut être miné (protection)
            if (!plugin.getMineManager().canMineBlock(location, player)) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Seule la pioche légendaire peut miner dans cette zone!");
                return;
            }

            // CORRECTION: Empêche TOUS les drops (items ET exp)
            event.setDropItems(false);
            event.setExpToDrop(0); // Plus d'exp des blocs minés

            // Traite les gains et enchantements pour ce bloc détruit
            processMiningInMine(player, location, material, mineName);

            // Met à jour la pioche
            plugin.getPickaxeManager().updatePlayerPickaxe(player);

            // Gère la durabilité
            var pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
            if (pickaxe != null) {
                plugin.getPickaxeManager().handleDurability(pickaxe, player);
            }

        } else {
            plugin.getPluginLogger().debug("Bloc cassé hors mine - minage normal");
            // Hors mine: comportement normal de Minecraft
        }
    }

    /**
     * Traite le minage dans une mine avec la nouvelle approche par bloc détruit
     */
    private void processMiningInMine(Player player, Location location, Material material, String mineName) {
        plugin.getPluginLogger().debug("Traitement minage dans mine: " + mineName);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Ajoute le bloc directement à l'inventaire du joueur
        addBlockToInventory(player, material);

        // NOUVEAU: Traite ce bloc détruit avec la nouvelle méthode
        plugin.getEnchantmentManager().processBlockDestroyed(player, location, material, mineName);

        // Ajoute aux statistiques (déjà fait dans processBlockDestroyed via addMinedBlock)
        // playerData.addMinedBlock(material); - supprimé pour éviter double comptage

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        plugin.getPluginLogger().debug("Bloc traité: " + material + " par " + player.getName());
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