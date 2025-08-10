package fr.prisontycoon.events;

import fr.custommobs.CustomMobsPlugin;
import fr.custommobs.events.EventScheduler;
import fr.custommobs.events.types.GangWarEvent;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.GlobalBonusManager;
import fr.prisontycoon.managers.PickaxeManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
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
        ItemStack playerPickaxe = player.getInventory().getItemInMainHand();
        Location location = event.getBlock().getLocation();
        Material material = event.getBlock().getType();
        String mineName = plugin.getConfigManager().getPlayerMine(location);
        String worldName = location.getWorld().getName();
        boolean isLegendaryPickaxe = plugin.getPickaxeManager().isLegendaryPickaxe(playerPickaxe);
        plugin.getBlockCollectorManager().add(player, material, 1);

        // Cas 1 : Le joueur est dans une mine configurée
        if (mineName != null) {
            if (plugin.getMineManager().isMineGenerating(mineName)) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Attendez la fin de la génération de la mine!");
            }
            if (!isLegendaryPickaxe) {
                event.setCancelled(true);
                player.sendMessage("§c❌ Seule la pioche légendaire peut miner dans cette zone!");
                return;
            }
            if (!plugin.getMineManager().canAccessMine(player, mineName)) {
                event.setCancelled(true);
                MineData mine = plugin.getConfigManager().getMineData(mineName);
                String requiredRank = mine != null ? mine.getRequiredRank().toUpperCase() : mineName.replace("mine-", "").toUpperCase();
                String[] rankInfo = plugin.getMineManager().getRankAndColor(player);
                player.sendMessage("§c❌ Vous n'avez pas accès à cette mine!");
                player.sendMessage("§7Mine: §e" + requiredRank + " §7- Votre rang: " + rankInfo[1] + rankInfo[0].toUpperCase());
                return;
            }

            event.setDropItems(false);
            event.setExpToDrop(0);
            processMiningInMine(player, location, material, mineName);
            plugin.getMineOverloadManager().onBlockMined(player, mineName, material);

            // Cas 2 : Le joueur casse une balise dans la "Cave"
        } else if (material == Material.BEACON && worldName.startsWith("Cave")) {
            event.setCancelled(true); // Annule le cassage normal car la logique est gérée manuellement
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            double beaconBonus = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, GlobalBonusManager.BonusCategory.BEACON_MULTIPLIER);
            int finalBeaconGain = (int) (1 * beaconBonus);
            playerData.addBeacons(finalBeaconGain);
            handleCaveBeaconBreak(player, location, finalBeaconGain);
            plugin.getQuestManager().addProgress(player, fr.prisontycoon.quests.QuestType.MINE_BEACONS, 1);

            // Cas 3 : Le joueur a la permission de miner dans des mondes spéciaux (hors mines)
        } else if (worldName.startsWith("Market") || worldName.startsWith("island") || player.hasPermission("specialmine.admin")) {
            processMiningOutsideMine(player);

            // Cas 4 : Tentative de cassage illégale
        } else {
            event.setCancelled(true);
            player.sendMessage("§cVous ne pouvez pas casser de blocs ici.");
            return;
        }

        // Post-traitement si une pioche légendaire a été utilisée
        if (isLegendaryPickaxe) {
            handlePickaxeDurability(player);
            postProcessLegendaryPickaxe(player);
            incrementBlockCountAndCheckDurabilityNotification(player, playerPickaxe);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Player player = event.getPlayer();
        Location blockLocation = event.getBlock().getLocation();
        Material material = event.getBlock().getType();

        boolean hasBeaconBreaker = plugin.getEnchantmentBookManager() != null && plugin.getEnchantmentBookManager().isEnchantmentActive(player, "beaconbreaker");
        if (material != Material.BEACON || !hasBeaconBreaker || PickaxeManager.isPickaxeBroken(player)) {
            return;
        }

        String mineName = plugin.getConfigManager().getPlayerMine(blockLocation);
        String worldName = blockLocation.getWorld().getName();

        if (mineName != null || worldName.startsWith("Cave")) {
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            double beaconBonus = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, GlobalBonusManager.BonusCategory.BEACON_MULTIPLIER);
            int finalBeaconGain = (int) (1 * beaconBonus);
            playerData.addBeacons(finalBeaconGain);
            blockLocation.getWorld().spawnParticle(Particle.END_ROD, blockLocation.clone().add(0.5, 0.5, 0.5), 20, 0.5, 0.5, 0.5, 0.1);
            player.playSound(blockLocation, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.5f);

            // Logique spécifique à la zone
            if (mineName != null) {
                blockLocation.getBlock().setType(Material.AIR, false); // Disparaît simplement dans une mine
            } else {
                handleCaveBeaconBreak(player, blockLocation, finalBeaconGain);
            }
        }
    }

    /**
     * Gère la logique de réapparition et de points de guerre des gangs pour une balise cassée dans la cave.
     *
     * @param player        Le joueur qui a cassé la balise.
     * @param blockLocation La localisation de la balise.
     */
    private void handleCaveBeaconBreak(Player player, Location blockLocation, int beaconsGained) {
        blockLocation.getBlock().setType(Material.BEDROCK, false);
        int playerCountInCave = blockLocation.getWorld().getPlayers().size();
        long respawnDelayTicks = calculateBeaconRespawnTime(playerCountInCave);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (blockLocation.getBlock().getType() == Material.BEDROCK) {
                blockLocation.getBlock().setType(Material.BEACON,false);
            }
        }, respawnDelayTicks);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String playerGangId = playerData.getGangId();
        int enemyPlayerCount = 0;

        if (playerGangId != null) {
            enemyPlayerCount = (int) blockLocation.getWorld().getPlayers().stream()
                    .filter(p -> {
                        PlayerData otherPlayerData = plugin.getPlayerDataManager().getPlayerData(p.getUniqueId());
                        return otherPlayerData.getGangId() != null && !playerGangId.equals(otherPlayerData.getGangId());
                    })
                    .count();
        }

        int xpGained = 0;
        if ("guerrier".equals(playerData.getActiveProfession())) {
            xpGained = 1 + enemyPlayerCount;
            plugin.getProfessionManager().addProfessionXP(player,"guerrier", xpGained);
        }

        if (playerGangId != null && enemyPlayerCount > 0) {
            CustomMobsPlugin customMobs = plugin.getCustomMobsPlugin();
            if (customMobs != null) {
                EventScheduler scheduler = customMobs.getEventScheduler();
                if (scheduler.isEventActive("gang_war") && scheduler.getActiveEvent("gang_war") instanceof GangWarEvent gangWarEvent) {
                    gangWarEvent.addPointsToPlayer(player, playerGangId, enemyPlayerCount);
                }
            }
        }
        StringBuilder legacyTextBuilder = new StringBuilder();

        legacyTextBuilder.append("§b✚ ").append(beaconsGained).append(" Balises");

        if (xpGained > 0) {
            legacyTextBuilder.append("   §d✚ ").append(xpGained).append(" XP");
        }

        if (enemyPlayerCount > 0) {
            legacyTextBuilder.append("   §c✚ ").append(enemyPlayerCount).append(" Points");
        }

        Component actionBarComponent = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyTextBuilder.toString());

        player.sendActionBar(actionBarComponent);
    }

    /**
     * UNIFIÉ : Gère la durabilité de toutes les pioches (légendaires ET normales)
     */
    private void handlePickaxeDurability(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Vérifier si c'est une pioche
        if (!tool.getType().name().contains("PICKAXE")) {
            return;
        }

        // DISTINCTION : Traitement différent selon le type de pioche
        if (plugin.getPickaxeManager().isLegendaryPickaxe(tool)) {
            handleLegendaryPickaxeDurability(player, tool);
        }
    }

    /**
     * Gère spécifiquement la durabilité des pioches légendaires
     */
    private void handleLegendaryPickaxeDurability(Player player, ItemStack tool) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");

        // Gestion spéciale pour Incassable
        if (plugin.getEnchantmentBookManager().isEnchantmentActive(player, "incassable")) {
            plugin.getPickaxeManager().deactivateBrokenPickaxeMode(player);
            return;
        }

        // SOLIDITÉ : Chance d'éviter la perte de durabilité
        if (durabilityLevel > 0) {
            double preservationChance = Math.min(95.0, durabilityLevel * 5.0);
            if (random.nextDouble() * 100 < preservationChance) {
                return;
            }
        }

        // APPLIQUER DOMMAGE : Seulement pour les pioches légendaires
        if (tool.getItemMeta() instanceof Damageable meta) {
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
                plugin.getPickaxeManager().checkLegendaryPickaxeState(player, tool, (short) (maxDurability - 1), maxDurability);
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
        // Vérifie d'abord si l'item ou son ItemMeta est non-existant
        if (pickaxe == null || !pickaxe.hasItemMeta()) {
            return;
        }

        ItemMeta meta = pickaxe.getItemMeta();

        // Vérifie si l'item peut prendre des dégâts en testant si son meta est une instance de Damageable
        if (!(meta instanceof Damageable damageableMeta)) {
            // L'item ne peut pas subir de dégâts, on arrête donc la fonction ici.
            return;
        }

        // À ce stade, on sait que le cast est sûr

        UUID playerId = player.getUniqueId();

        // Incrémente le compteur de blocs cassés
        int currentCount = playerBlocksMinedCount.getOrDefault(playerId, 0) + 1;
        playerBlocksMinedCount.put(playerId, currentCount);

        // Vérifie la durabilité de la pioche
        short currentDurability = (short) damageableMeta.getDamage();
        short maxDurability = pickaxe.getType().getMaxDurability();

        // Si la durabilité max est 0 ou moins, l'item est incassable
        if (maxDurability <= 0) {
            return;
        }

        double durabilityPercent = 1.0 - ((double) currentDurability / maxDurability);

        // Seulement envoyer des notifications quand la durabilité est < 25%
        if (durabilityPercent > 0.25 || PickaxeManager.isPickaxeBroken(player)) {
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
     * Traite le minage dans une mine avec pioche légendaire
     */
    private void processMiningInMine(Player player, Location location, Material material, String mineName) {
        plugin.getPluginLogger().debug("Traitement minage dans mine: " + mineName);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Met à jour l'activité de minage pour l'ActionBar
        playerData.updateMiningActivity();

        // Si le bloc est un beacon, on le traite spécifiquement
        if (material == Material.BEACON) {
            double beaconBonus = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, GlobalBonusManager.BonusCategory.BEACON_MULTIPLIER);
            int finalBeaconGain = (int) (1 * beaconBonus);
            playerData.addBeacons(finalBeaconGain);            
            plugin.getBlockCollectorManager().add(player, material, 1);
            return;
        }
        // Traite ce bloc MINÉ directement par le joueur (avec Greeds, enchants spéciaux, etc.)
        plugin.getEnchantmentManager().processBlockMined(player, location, material, mineName);

        plugin.getMineManager().checkAndRegenerateMineIfNeeded(mineName);

    }

    /**
     * Traite le minage hors mine avec pioche légendaire (restrictions appliquées)
     */
    private void processMiningOutsideMine(Player player) {
        plugin.getPluginLogger().debug("Traitement minage hors mine (restrictions)");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Met à jour l'activité de minage pour l'ActionBar
        playerData.updateMiningActivity();

        // Avertissement si première fois hors mine
        if (!player.hasMetadata("outside_mine_warning_shown")) {
            player.sendMessage("§c⚠️ Hors mine: seuls efficacité, solidité et mobilité actifs!");
            player.setMetadata("outside_mine_warning_shown", new FixedMetadataValue(plugin, true));
        }
    }

    /**
     * Post-traitement après utilisation de la pioche légendaire
     */
    private void postProcessLegendaryPickaxe(Player player) {
        plugin.getPluginLogger().debug("Post-traitement pioche légendaire pour " + player.getName());

        // 2. S'assure que la pioche reste au bon slot
        plugin.getPickaxeManager().enforcePickaxeSlot(player);

        // 3. Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        plugin.getPluginLogger().debug("Post-traitement pioche terminé pour " + player.getName());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        String worldName = location.getWorld().getName();
        if (!worldName.startsWith("Market") && !worldName.startsWith("id") && !player.hasPermission("specialmine.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cVous ne pouvez pas casser de blocs ici.");
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        Player player = (Player) event.getPlayer();
        plugin.getPickaxeManager().updatePlayerPickaxe(player);
    }

    /**
     * Calcule le délai de réapparition d'une balise dans la cave.
     * Le délai diminue à mesure que le nombre de joueurs augmente.
     *
     * @param playerCount Le nombre de joueurs dans le monde de la cave.
     * @return Le délai en ticks (de 2400 à 6000 ticks).
     */
    private long calculateBeaconRespawnTime(int playerCount) {
        final long MIN_DELAY_TICKS = 2 * 60 * 20; // 2 minutes
        final long MAX_DELAY_TICKS = 5 * 60 * 20; // 5 minutes
        final int MAX_PLAYERS_FOR_SCALING = 15;
        if (playerCount <= 1) {
            return MAX_DELAY_TICKS;
        }
        if (playerCount >= MAX_PLAYERS_FOR_SCALING) {
            return MIN_DELAY_TICKS;
        }
        double scale = (double) (playerCount - 1) / (MAX_PLAYERS_FOR_SCALING - 1);
        return MAX_DELAY_TICKS - (long) ((MAX_DELAY_TICKS - MIN_DELAY_TICKS) * scale);
    }
}