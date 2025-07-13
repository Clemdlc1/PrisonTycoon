package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.BlockValueData;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
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
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.Random;

/**
 * UNIFIÉ : Listener pour le minage ET la gestion de durabilité des pioches légendaires
 * Résout les conflits de priorité entre MiningListener et PickaxeDurabilityListener
 */
public class MiningListener implements Listener {

    private final PrisonTycoon plugin;
    private final Random random = new Random();

    public MiningListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        Material material = event.getBlock().getType();

        // 1. GESTION DURABILITÉ : Traite d'abord la durabilité de TOUTES les pioches
        handlePickaxeDurability(player, event);

        // 2. LOGIQUE MINAGE : Traite ensuite la logique spécifique aux pioches légendaires
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

        // 3. POST-TRAITEMENT : Mise à jour de la pioche légendaire si utilisée
        if (playerPickaxe != null) {
            postProcessLegendaryPickaxe(player, playerPickaxe);
        }
    }

    // ================================
    // SECTION 1: GESTION DURABILITÉ
    // ================================

    /**
     * UNIFIÉ : Gère la durabilité de toutes les pioches (légendaires ET normales)
     */
    private void handlePickaxeDurability(Player player, BlockBreakEvent event) {
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Vérifier si c'est une pioche
        if (plugin.getPickaxeManager().isLegendaryPickaxe(tool)) {
            plugin.getPluginLogger().info("La pioche de " + player.getName() + " est bien légendaire. Application de la protection."); // <--- AJOUTEZ CECI
            handleLegendaryPickaxeDurability(player, tool, event);
        } else if (tool.getType().name().contains("PICKAXE")) {
            plugin.getPluginLogger().warning("La pioche de " + player.getName() + " N'EST PAS légendaire. Aucune protection appliquée."); // <--- ET CECI
        }
    }

    /**
     * Gère spécifiquement la durabilité des pioches légendaires
     */
    private void handleLegendaryPickaxeDurability(Player player, ItemStack tool, BlockBreakEvent event) {
        if (!(tool.getItemMeta() instanceof Damageable)) {
            return;
        }

        Damageable meta = (Damageable) tool.getItemMeta();
        short maxDurability = tool.getType().getMaxDurability();
        short currentDamage = (short) meta.getDamage();

        // Le serveur appliquera +1 de dégât après cet événement.
        // Notre logique doit anticiper et contrer ce comportement si nécessaire.

        // 1. GESTION DE "SOLIDITÉ" (UNBREAKING)
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");
        if (durabilityLevel > 0) {
            double preservationChance = Math.min(95.0, durabilityLevel * 5.0);
            if (random.nextDouble() * 100 < preservationChance) {
                // Pour PRÉSERVER la durabilité, nous devons annuler le +1 du serveur.
                // Pour cela, nous appliquons -1 de dégât ici.
                // Le calcul final sera : currentDamage - 1 (plugin) + 1 (serveur) = currentDamage (aucun changement)
                meta.setDamage(Math.max(0, currentDamage - 1));
                tool.setItemMeta(meta);

                plugin.getPluginLogger().debug("Durabilité préservée par Solidité pour " + player.getName());
                return; // La durabilité est préservée, on a fini.
            }
        }

        // 2. GESTION DE LA PROTECTION ANTI-CASSE
        // Si la pioche est sur le point de recevoir le coup fatal...
        // (c'est-à-dire si elle est déjà à sa durabilité max - 1)
        if (currentDamage >= maxDurability - 1) {
            // ...nous devons aussi annuler le coup du serveur pour la protéger.
            // On fait -1 pour que le +1 du serveur la ramène simplement à son état actuel.
            meta.setDamage(currentDamage - 1);
            tool.setItemMeta(meta);

            plugin.getPluginLogger().debug("Casse finale de la pioche de " + player.getName() + " empêchée.");

            // On vérifie quand même son état pour activer le mode "cassé"
            checkLegendaryPickaxeState(player, tool, currentDamage, maxDurability);
        } else {
            // La pioche n'est pas en danger critique. On laisse le serveur appliquer son dégât.
            // On vérifie juste l'état en anticipant le futur dégât pour les notifications.
            checkLegendaryPickaxeState(player, tool, (short)(currentDamage + 1), maxDurability);
        }
    }

    /**
     * Vérifie l'état de la pioche légendaire et affiche les notifications appropriées
     */
    private void checkLegendaryPickaxeState(Player player, ItemStack pickaxe, short currentDurability, short maxDurability) {
        double durabilityPercent = 1.0 - ((double) currentDurability / maxDurability);

        // PIOCHE CASSÉE (100% utilisée)
        if (currentDurability >= maxDurability - 1) {
            if (!isPickaxeBroken(player)) {
                activateBrokenPickaxeMode(player);

                // Message spécial pour pioche cassée (une seule fois)
                if (!player.hasMetadata("durability_notif_broken")) {
                    TextComponent message = new TextComponent("§c💀 PIOCHE CASSÉE! Tous enchantements désactivés! §e[RÉPARER IMMÉDIATEMENT]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§cRéparation critique requise!")));
                    player.spigot().sendMessage(message);

                    player.setMetadata("durability_notif_broken", new FixedMetadataValue(plugin, true));
                }
            }
        } else {
            // Désactive le mode "pioche cassée" si il était actif
            if (isPickaxeBroken(player)) {
                deactivateBrokenPickaxeMode(player);
                player.removeMetadata("durability_notif_broken", plugin);
            }

            // NOTIFICATIONS PAR SEUILS (une seule fois par niveau)
            if (durabilityPercent <= 0.10) { // Moins de 10% restant
                if (!player.hasMetadata("durability_notif_10")) {
                    TextComponent message = new TextComponent("§6⚠️ Votre pioche est très endommagée ! §e[CLIQUEZ POUR RÉPARER]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aOuvrir le menu de réparation")));
                    player.spigot().sendMessage(message);

                    player.setMetadata("durability_notif_10", new FixedMetadataValue(plugin, true));
                }
            } else if (durabilityPercent <= 0.25) { // Moins de 25% restant
                if (!player.hasMetadata("durability_notif_25")) {
                    TextComponent message = new TextComponent("§e⚠️ Votre pioche commence à être endommagée. §e[RÉPARER]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aOuvrir le menu de réparation")));
                    player.spigot().sendMessage(message);

                    player.setMetadata("durability_notif_25", new FixedMetadataValue(plugin, true));
                }
            }
        }
    }

    // ================================
    // SECTION 2: LOGIQUE MINAGE
    // ================================

    /**
     * Traite le minage dans une mine avec pioche légendaire
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
     * Traite le minage hors mine avec pioche légendaire (restrictions appliquées)
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

    // ================================
    // SECTION 3: UTILITAIRES
    // ================================

    /**
     * Obtient la pioche légendaire du joueur s'il en a une dans le bon slot
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
     * Post-traitement unifié de la pioche légendaire
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
     * Ajoute un bloc à l'inventaire avec gestion d'erreur
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

    // ================================
    // SECTION 4: GESTION ÉTAT "PIOCHE CASSÉE"
    // ================================

    private boolean isPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    private void activateBrokenPickaxeMode(Player player) {
        player.setMetadata("pickaxe_broken", new FixedMetadataValue(plugin, true));
        player.setMetadata("pickaxe_just_broken", new FixedMetadataValue(plugin, System.currentTimeMillis()));

        plugin.getPickaxeManager().removeMobilityEffects(player);
        plugin.getEnchantmentManager().forceDisableAbundanceAndResetCombustion(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.5f);
        plugin.getPluginLogger().info("Mode pioche cassée activé pour " + player.getName());
    }

    private void deactivateBrokenPickaxeMode(Player player) {
        player.removeMetadata("pickaxe_broken", plugin);
        player.setMetadata("pickaxe_just_repaired", new FixedMetadataValue(plugin, System.currentTimeMillis()));

        plugin.getPickaxeManager().updateMobilityEffects(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        plugin.getPluginLogger().info("Mode pioche cassée désactivé pour " + player.getName());
    }

    // ================================
    // SECTION 5: MÉTHODES STATIQUES (compatibilité)
    // ================================

    /**
     * Méthode statique pour compatibilité avec les autres classes
     */
    public static boolean isPlayerPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    /**
     * Méthode statique pour obtenir le multiplicateur de malus
     */
    public static double getPickaxePenaltyMultiplier(Player player) {
        if (isPlayerPickaxeBroken(player)) {
            return 0.1; // 90% de malus = on garde 10%
        }
        return 1.0; // Aucun malus
    }

    // ================================
    // SECTION 6: ÉVÉNEMENT PLACEMENT
    // ================================

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