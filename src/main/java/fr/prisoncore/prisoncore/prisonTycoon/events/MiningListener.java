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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UNIFIÉ : Listener pour le minage ET la gestion de durabilité des pioches légendaires
 * NOUVEAU : Système de notifications de durabilité basé sur les blocs cassés
 */
public class MiningListener implements Listener {

    private final PrisonTycoon plugin;
    private final Random random = new Random();

    // NOUVEAU : Compteur de blocs cassés par joueur pour les notifications de durabilité
    private final Map<UUID, Integer> playerBlocksMinedCount = new ConcurrentHashMap<>();

    public MiningListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        Material material = event.getBlock().getType();

        ItemStack playerPickaxe = player.getInventory().getItemInMainHand();
        String mineName = plugin.getConfigManager().getPlayerMine(location);

        if (mineName != null) {
            // Dans une mine - pioche légendaire obligatoire
            if (!plugin.getPickaxeManager().isLegendaryPickaxe(playerPickaxe)) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Seule la pioche légendaire peut miner dans cette zone!");
                return;
            }

            // NOUVEAU : Vérification des permissions de mine
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (!playerData.hasMinePermission(mineName)) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Vous n'avez pas la permission de miner dans la mine '" + mineName + "'!");
                player.sendMessage("§7Utilisez §e/mine permission " + mineName + " §7pour obtenir l'accès.");
                return;
            }

            handlePickaxeDurability(player, event);

            // Empêche TOUS les drops (items ET exp)
            event.setDropItems(false);
            event.setExpToDrop(0);

            // Traite le minage dans la mine
            processMiningInMine(player, location, material, mineName);

        } else if (playerPickaxe != null) {
            // Hors mine avec pioche légendaire - restrictions appliquées
            processMiningOutsideMine(player, location, material);
            handlePickaxeDurability(player, event);

        }
        // Sinon : comportement normal de Minecraft (pas de pioche légendaire)

        // 3. POST-TRAITEMENT : Mise à jour de la pioche légendaire si utilisée
        if (playerPickaxe != null) {
            postProcessLegendaryPickaxe(player, playerPickaxe);

            // NOUVEAU : Incrémente le compteur de blocs et vérifie les notifications de durabilité
            incrementBlockCountAndCheckDurabilityNotification(player, playerPickaxe);
        }
    }

    /**
     * UNIFIÉ : Gère la durabilité de toutes les pioches (légendaires ET normales)
     */
    private void handlePickaxeDurability(Player player, BlockBreakEvent event) {
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Vérifier si c'est une pioche
        if (!tool.getType().name().contains("PICKAXE")) {
            return;
        }

        // DISTINCTION : Traitement différent selon le type de pioche
        if (plugin.getPickaxeManager().isLegendaryPickaxe(tool)) {
            handleLegendaryPickaxeDurability(player, tool, event);
        }
    }

    /**
     * Gère spécifiquement la durabilité des pioches légendaires
     */
    private void handleLegendaryPickaxeDurability(Player player, ItemStack tool, BlockBreakEvent event) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");

        // SOLIDITÉ : Chance d'éviter la perte de durabilité
        if (durabilityLevel > 0) {
            double preservationChance = Math.min(95.0, durabilityLevel * 5.0);
            if (random.nextDouble() * 100 < preservationChance) {
                return; // La durabilité est préservée
            }
        }

        // APPLIQUER DOMMAGE : Seulement pour les pioches légendaires
        if (tool.getItemMeta() instanceof Damageable) {
            Damageable meta = (Damageable) tool.getItemMeta();
            short maxDurability = tool.getType().getMaxDurability();
            short currentDurability = (short) meta.getDamage();

            // CORRECTION CRITIQUE: Empêcher la pioche de dépasser maxDurability - 1
            if (currentDurability >= maxDurability - 1) {
                plugin.getPluginLogger().debug("Pioche légendaire de " + player.getName() + " déjà au maximum de casse (" + currentDurability + "/" + maxDurability + ")");

                // S'assurer qu'elle reste à maxDurability - 1 (état "cassée" mais pas détruite)
                if (currentDurability > maxDurability - 1) {
                    meta.setDamage(maxDurability - 1);
                    tool.setItemMeta(meta);
                }

                // Vérifier l'état mais ne pas augmenter les dégâts
                plugin.getPickaxeManager().checkLegendaryPickaxeState(player, tool, (short)(maxDurability - 1), maxDurability);
                return;
            }

            // Augmente les dégâts de 1 point SEULEMENT si pas encore cassée
            short newDurability = (short) Math.min(currentDurability + 1, maxDurability - 1);
            meta.setDamage(newDurability);
            tool.setItemMeta(meta);

            plugin.getPluginLogger().debug("Durabilité pioche légendaire " + player.getName() + ": " + currentDurability + " -> " + newDurability + " (max: " + maxDurability + ")");

            // Vérifier l'état après modification
            plugin.getPickaxeManager().checkLegendaryPickaxeState(player, tool, newDurability, maxDurability);
        }
    }

    /**
     * NOUVEAU : Incrémente le compteur de blocs et vérifie s'il faut envoyer une notification de durabilité
     */
    private void incrementBlockCountAndCheckDurabilityNotification(Player player, ItemStack pickaxe) {
        UUID playerId = player.getUniqueId();

        // Incrémente le compteur de blocs cassés
        int currentCount = playerBlocksMinedCount.getOrDefault(playerId, 0) + 1;
        playerBlocksMinedCount.put(playerId, currentCount);

        // Vérifie la durabilité de la pioche
        short currentDurability = (short) ((Damageable) pickaxe.getItemMeta()).getDamage();
        short maxDurability = pickaxe.getType().getMaxDurability();
        double durabilityPercent = 1.0 - ((double) currentDurability / maxDurability);

        // Seulement envoyer des notifications quand la durabilité est < 25%
        if (durabilityPercent > 0.25  || plugin.getPickaxeManager().isPickaxeBroken(player)) {
            return;
        }

        // Calcule la fréquence basée sur le niveau de durabilité
        int notificationFrequency = calculateNotificationFrequency(durabilityPercent);

        // Vérifie s'il faut envoyer une notification
        if (currentCount % notificationFrequency == 0) {
            String notificationMessage = createDurabilityNotificationMessage(player, durabilityPercent);

            // Envoie une notification temporaire via le NotificationManager (durée: 2 secondes)
            plugin.getNotificationManager().sendTemporaryDurabilityNotification(player, notificationMessage, 2000);

            plugin.getPluginLogger().debug("Notification de durabilité envoyée à " + player.getName() +
                    " après " + currentCount + " blocs (fréquence: " + notificationFrequency + ")");
        }
    }

    /**
     * NOUVEAU : Calcule la fréquence des notifications basée sur le niveau de durabilité
     */
    private int calculateNotificationFrequency(double durabilityPercent) {
        if (durabilityPercent <= 0.05) { // Moins de 5% - très critique
            return 10; // Toutes les 10 blocs
        } else if (durabilityPercent <= 0.10) { // Moins de 10% - critique
            return 20; // Toutes les 20 blocs
        } else if (durabilityPercent <= 0.15) { // Moins de 15% - urgent
            return 30; // Toutes les 30 blocs
        } else { // 15-25% - attention
            return 50; // Toutes les 50 blocs (fréquence de base)
        }
    }

    /**
     * NOUVEAU : Crée le message de notification de durabilité
     */
    private String createDurabilityNotificationMessage(Player player, double durabilityPercent) {
        String percentageStr = String.format("%.1f%%", durabilityPercent * 100);

        if (durabilityPercent <= 0.05) {
            return "§c💀 URGENT! Pioche CRITIQUE! Réparez MAINTENANT! (" + percentageStr + ")";
        } else if (durabilityPercent <= 0.10) { // Moins de 10% - critique
            return "§c⚠️ CRITIQUE! Pioche très endommagée! (" + percentageStr + ")";
        } else if (durabilityPercent <= 0.15) { // Moins de 15% - urgent
            return "§6⚠️ URGENT! Réparez votre pioche! (" + percentageStr + ")";
        } else { // 15-25% - attention
            return "§e⚠️ Attention: Pioche endommagée (" + percentageStr + ")";
        }
    }

    /**
     * NOUVEAU : Réinitialise le compteur de blocs pour un joueur (utilisé lors de la réparation)
     */
    public void resetBlockCountForPlayer(Player player) {
        playerBlocksMinedCount.remove(player.getUniqueId());
        plugin.getPluginLogger().debug("Compteur de blocs réinitialisé pour " + player.getName());
    }

    /**
     * Traite le minage dans une mine avec pioche légendaire
     */
    private void processMiningInMine(Player player, Location location, Material material, String mineName) {
        plugin.getPluginLogger().debug("Traitement minage dans mine: " + mineName);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Met à jour l'activité de minage pour l'ActionBar
        playerData.updateMiningActivity();

        // Si le bloc est un beacon, on le traite spécifiquement
        if (material == Material.BEACON) {
            // Incrémente le compteur de beacons minés dans les données du joueur
            playerData.addBeacons(1);

            // Appelle la méthode spécifique pour la rupture d'un beacon
            // plugin.getEnchantmentUniqueManager().beaconBreak(player, mineName);
            return;
        }

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
    }

    /**
     * Traite le minage hors mine avec pioche légendaire (restrictions appliquées)
     */
    private void processMiningOutsideMine(Player player, Location location, Material material) {
        plugin.getPluginLogger().debug("Traitement minage hors mine (restrictions)");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Met à jour l'activité de minage pour l'ActionBar
        playerData.updateMiningActivity();

        // Avertissement si première fois hors mine
        if (!player.hasMetadata("outside_mine_warning_shown")) {
            player.sendMessage("§c⚠️ Hors mine: seuls efficacité, solidité et mobilité actifs!");
            player.setMetadata("outside_mine_warning_shown", new FixedMetadataValue(plugin, true));
        }

        // Traite le bloc MINÉ hors mine (restrictions)
        plugin.getEnchantmentManager().processBlockMinedOutsideMine(player, material);
    }


    /**
     * MODIFIÉ : Ajoute un bloc à l'inventaire du joueur ou dans ses conteneurs
     * Priorité: Conteneurs -> Inventaire normal
     */
    private void addBlockToInventory(Player player, Material material) {
        ItemStack itemToAdd = new ItemStack(material, 1);

        // NOUVEAU: Tente d'abord d'ajouter aux conteneurs
        if (plugin.getContainerManager().addItemToContainers(player, itemToAdd)) {
            plugin.getPluginLogger().debug("Bloc " + material.name() + " ajouté au conteneur de " + player.getName());
            return;
        }

        // Si aucun conteneur disponible, ajoute à l'inventaire normal
        var remaining = player.getInventory().addItem(itemToAdd);

        if (!remaining.isEmpty()) {

            // Message d'avertissement moins fréquent
            if (!player.hasMetadata("inventory_full_warning") ||
                    System.currentTimeMillis() - player.getMetadata("inventory_full_warning").get(0).asLong() > 30000) {

                player.sendMessage("§c⚠️ Inventaire et conteneurs pleins! Items droppés au sol.");
                player.sendMessage("§e💡 Utilisez §a/sell all §epour vider vos conteneurs!");
                player.setMetadata("inventory_full_warning", new FixedMetadataValue(plugin, System.currentTimeMillis()));
            }
        }
    }

    /**
     * Trouve la pioche légendaire du joueur
     */
    private ItemStack getPlayerLegendaryPickaxe(Player player) {
        return plugin.getPickaxeManager().findPlayerPickaxe(player);
    }

    /**
     * Post-traitement après utilisation de la pioche légendaire
     */
    private void postProcessLegendaryPickaxe(Player player, ItemStack pickaxe) {
        plugin.getPluginLogger().debug("Post-traitement pioche légendaire pour " + player.getName());

        // 1. Met à jour la pioche avec ses enchantements
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        // 2. S'assure que la pioche reste au bon slot
        plugin.getPickaxeManager().enforcePickaxeSlot(player);

        // 3. Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        plugin.getPluginLogger().debug("Post-traitement pioche terminé pour " + player.getName());
    }

    /**
     * Vérifie si la pioche du joueur est cassée
     */
    public static boolean isPlayerPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    /**
     * Retourne le multiplicateur de pénalité (90% de malus = 10% d'efficacité)
     */
    public static double getPickaxePenaltyMultiplier(Player player) {
        return isPlayerPickaxeBroken(player) ? 0.10 : 1.0;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // Empêche de placer des blocs dans les mines
        String mineName = plugin.getConfigManager().getPlayerMine(event.getBlock().getLocation());
        if (mineName != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c❌ Impossible de placer des blocs dans une mine!");
        }
    }
}