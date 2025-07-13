package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.BlockValueData;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
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

import java.util.Map;

/**
 * Listener pour les événements de minage
 * CORRIGÉ : Intégration avec le nouveau système de notifications
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

            // Empêche TOUS les drops (items ET exp)
            event.setDropItems(false);
            event.setExpToDrop(0);

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

            // Hors mine, vérification de la pioche légendaire
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
     * CORRIGÉ : Traite le minage dans une mine avec nouveau système de notifications et activité
     */
    private void processMiningInMine(Player player, Location location, Material material, String mineName) {
        plugin.getPluginLogger().debug("Traitement minage dans mine: " + mineName);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // NOUVEAU : Met à jour l'activité de minage pour l'ActionBar
        playerData.updateMiningActivity();

        // Ajoute le bloc directement à l'inventaire du joueur
        addBlockToInventory(player, material);

        // NOUVEAU : Récupère les gains de base (Fortune sera appliquée dans EnchantmentManager)
        BlockValueData baseValue = plugin.getConfigManager().getBlockValue(material);

        // NOUVEAU : Notifie les gains de base via le nouveau système
        if (baseValue.getCoins() > 0 || baseValue.getTokens() > 0 || baseValue.getExperience() > 0) {
            plugin.getNotificationManager().queueRegularGains(player,
                    baseValue.getCoins(), baseValue.getTokens(), baseValue.getExperience());
        }

        // Traite ce bloc MINÉ directement par le joueur (avec Greeds, enchants spéciaux, etc.)
        plugin.getEnchantmentManager().processBlockMined(player, location, material, mineName);

        // Met à jour la pioche
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        // Gère la durabilité
        var pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe != null) {
            plugin.getPickaxeManager().handleDurability(pickaxe, player);
        }

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        plugin.getPluginLogger().debug("Bloc miné traité: " + material + " par " + player.getName());
    }

    /**
     * Traite le minage hors mine (restrictions enchantements)
     */
    private void processMiningOutsideMine(Player player, Location location, Material material) {
        plugin.getPluginLogger().debug("Traitement minage hors mine avec pioche légendaire");

        // Applique uniquement les enchantements autorisés hors mine
        plugin.getEnchantmentManager().processBlockMinedOutsideMine(player, material);

        // NOUVEAU : Notifie les restrictions
        if (Math.random() < 0.1) { // 10% chance de rappeler les restrictions
            plugin.getNotificationManager().queueSpecialStateNotification(player,
                    "Hors Mine", "§7Greeds et effets spéciaux inactifs");
        }

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