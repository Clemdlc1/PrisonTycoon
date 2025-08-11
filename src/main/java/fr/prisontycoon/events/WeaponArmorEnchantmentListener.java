package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.enchantments.WeaponArmorEnchantmentManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener pour les enchantements d'épées et armures
 */
public class WeaponArmorEnchantmentListener implements Listener {

    private final PrisonTycoon plugin;

    public WeaponArmorEnchantmentListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère l'ouverture du GUI d'enchantement avec Shift + Clic droit
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) return;

        // Vérifie si c'est une épée ou une armure valide
        if (!isValidWeaponOrArmor(item)) return;

        // Vérifie si c'est Shift + Clic droit
        if (!player.isSneaking()) return;

        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Évite d'ouvrir le menu si le joueur clique sur un bloc interactif
        if (event.getClickedBlock() != null && isInteractiveBlock(event.getClickedBlock().getType())) {
            return;
        }

        event.setCancelled(true);
        plugin.getWeaponArmorEnchantGUI().openEnchantMenu(player, item);
    }

    /**
     * Gère les clics dans les GUIs d'enchantement
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = plugin.getGUIManager().getLegacyTitle(event.getView());

        if (!title.contains("§c⚔ §lEnchantement d'Épée") &&
                !title.contains("§9🛡 §lEnchantement d'Armure")) {
            return;
        }

        // NOUVEAU : Si clic dans l'inventaire du joueur avec un livre d'enchantement
        if (event.getClickedInventory() == player.getInventory()) {
            ItemStack clickedItem = event.getCurrentItem();
            if (isAnyEnchantmentBook(clickedItem)) {
                event.setCancelled(true);
                handleBookApplicationClick(player, clickedItem);
                return;
            }
            // Pour les autres items dans l'inventaire, on n'interfère pas
            return;
        }

        // Si clic dans le GUI d'enchantement
        if (event.getClickedInventory() != player.getInventory()) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                plugin.getWeaponArmorEnchantGUI().handleMenuClick(player, event.getSlot(), clickedItem, event.getClick());
            }
        }
    }

    /**
     * Gère l'application d'un livre d'enchantement (unique ou legacy) sur un item.
     * VERSION CORRIGÉE ET UNIFIÉE
     */
    private void handleBookApplicationClick(Player player, ItemStack book) {
        ItemMeta bookMeta = book.getItemMeta();
        if (bookMeta == null) return;

        // --- Étape 1: Extraire l'ID de l'enchantement de manière unifiée ---
        String enchantId = null;
        NamespacedKey uniqueKey = new NamespacedKey(plugin, "unique_enchant_book");
        NamespacedKey legacyKey = new NamespacedKey(plugin, "enchant_book_id");

        if (bookMeta.getPersistentDataContainer().has(uniqueKey, PersistentDataType.STRING)) {
            enchantId = bookMeta.getPersistentDataContainer().get(uniqueKey, PersistentDataType.STRING);
        } else if (bookMeta.getPersistentDataContainer().has(legacyKey, PersistentDataType.STRING)) {
            enchantId = bookMeta.getPersistentDataContainer().get(legacyKey, PersistentDataType.STRING);
        }

        if (enchantId == null) {
            return;
        }

        // --- Étape 2: Vérifications de base ---
        WeaponArmorEnchantmentManager manager = plugin.getWeaponArmorEnchantmentManager();
        WeaponArmorEnchantmentManager.UniqueEnchantment enchant = manager.getEnchantment(enchantId);

        if (enchant == null) {
            player.sendMessage("§cErreur: L'enchantement '" + enchantId + "' n'existe pas ou n'est plus supporté.");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        // --- Étape 3: Logique d'application et messages ---
        if (manager.isCompatible(enchantId, item)) {
            player.sendMessage("§cCet enchantement n'est pas compatible avec cet item !");
            return;
        }

        boolean success = manager.addEnchantment(item, enchantId, 1);

        if (success) {
            // Logique de succès (consommer le livre, son, message)
            book.setAmount(book.getAmount() - 1);
            int newLevel = manager.getEnchantmentLevel(item, enchantId);

            if (newLevel == 1) {
                player.sendMessage("§a✅ Enchantement §e" + enchant.getName() + " §aappliqué !");
            } else {
                player.sendMessage("§a✅ Enchantement §e" + enchant.getName() + " §aamélioré au niveau " + newLevel + " !");
            }

            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
            plugin.getWeaponArmorEnchantGUI().openEnchantMenu(player, item); // Rafraîchir le GUI
        } else {
            // Logique d'échec (donner la bonne raison au joueur)
            int currentLevel = manager.getEnchantmentLevel(item, enchantId);

            if (currentLevel >= enchant.getMaxLevel()) {
                player.sendMessage("§cCet enchantement est déjà au niveau maximum ! (" + enchant.getMaxLevel() + ")");
            } else {
                // La seule autre raison d'échec est que le nombre max d'enchantements est atteint.
                int currentCount = manager.getUniqueEnchantmentCount(item);
                int maxCount = manager.getMaxEnchantments(item); // On utilise notre nouvelle méthode fiable
                player.sendMessage("§cNombre maximum d'enchantements uniques atteint ! (" + maxCount + ")");
                player.sendMessage("§7Votre item a déjà " + currentCount + " enchantement(s) unique(s).");
            }
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getFoodLevel() < player.getFoodLevel()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Gère les dégâts infligés avec une épée enchantée
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) return;

        // Traite les enchantements d'épée
        plugin.getWeaponArmorEnchantmentManager().handleWeaponDamage(attacker, event.getEntity(), weapon);

        // Traite les enchantements d'armure de la victime
        if (event.getEntity() instanceof Player victim) {
            plugin.getWeaponArmorEnchantmentManager().handleArmorDamage(victim, attacker);

            // NOUVEAU: Réduction de dégâts PvE si la victime tient une épée avec "cuirasse_bestiale"
            ItemStack victimWeapon = victim.getInventory().getItemInMainHand();
            if (victimWeapon != null && victimWeapon.getType() != Material.AIR) {
                int level = plugin.getWeaponArmorEnchantmentManager().getEnchantmentLevel(victimWeapon, "cuirasse_bestiale");
                if (level > 0 && !(attacker instanceof Player)) {
                    // Réduction: 8% + 4%/niveau
                    double reduction = (8.0 + 4.0 * level) / 100.0;
                    double newDamage = Math.max(0.0, event.getDamage() * (1.0 - reduction));
                    event.setDamage(newDamage);
                    victim.sendActionBar(net.kyori.adventure.text.Component.text("§3🛡 Cuirasse bestiale: -" + String.format("%.0f", reduction * 100) + "% dégâts"));
                }
            }
        }
    }

    /**
     * Gère la mort des joueurs pour les enchantements spéciaux
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        if (!dead.getWorld().getName().equalsIgnoreCase("Cave")) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
            return;
        }

        if (killer != null) {
            plugin.getWeaponArmorEnchantmentManager().handlePlayerDeath(dead, killer, event);
            // Quêtes: tuer des joueurs
            plugin.getQuestManager().addProgress(killer, fr.prisontycoon.quests.QuestType.KILL_PLAYERS, 1);
        }
    }

    /**
     * Annule les dégâts de chute sauf dans le monde spécifié.
     *
     * @param event L'événement de dégâts.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (!player.getWorld().getName().equalsIgnoreCase("Cave")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player player)) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            return;
        }
        // Progression des quêtes
        plugin.getQuestManager().addProgress(player, fr.prisontycoon.quests.QuestType.KILL_MONSTERS, 1);

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType() == Material.AIR) {
            return;
        }

        int beheadLevel = plugin.getWeaponArmorEnchantmentManager().getEnchantmentLevel(weapon, "behead");
        int chancePercent;

        // Si le joueur a l'enchantement, on calcule ses chances
        if (beheadLevel > 0) {
            chancePercent = Math.max(1, 10 * beheadLevel);
        } else {
            chancePercent = 1;
        }

        // Drop fragments de forge (quêtes/monstres)
        dropForgeFragments(event, player);

        // On lance le dé pour voir si la tête est obtenue
        if (ThreadLocalRandom.current().nextInt(100) < chancePercent) {
            ItemStack head = plugin.getWeaponArmorEnchantmentManager().createHeadForEntity(event.getEntity());
            if (head != null) {
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(head);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), head);
                }
                if (beheadLevel > 0) {
                    player.sendMessage("§6💀 BeHead: Tête obtenue!");
                } else {
                    player.sendMessage("§6💀 Tête obtenue!");
                }
            }
        }
    }

    private void dropForgeFragments(EntityDeathEvent event, Player player) {
        var rng = ThreadLocalRandom.current();
        int roll = rng.nextInt(100);
        // Chances modestes de drop, progressives selon mob HP (simple):
        // 15% faible, 25% moyen, 35% élevé
        int baseChance = 15;
        double maxHealth = 20.0;
        if (event.getEntity() instanceof org.bukkit.attribute.Attributable attributable) {
            var attr = attributable.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) maxHealth = attr.getBaseValue();
        }
        if (maxHealth >= 60) baseChance = 35;
        else if (maxHealth >= 30) baseChance = 25;
        if (roll < baseChance) {
            // Type de fragment aléatoire avec poids
            fr.prisontycoon.managers.ForgeManager.FragmentType type;
            int t = rng.nextInt(100);
            if (t < 70) type = fr.prisontycoon.managers.ForgeManager.FragmentType.COPPER;
            else type = fr.prisontycoon.managers.ForgeManager.FragmentType.ALLOY;

            int amount = 1 + rng.nextInt(2); // 1-2
            var item = plugin.getForgeManager().createFragment(type, amount);
            if (player.getInventory().firstEmpty() != -1) player.getInventory().addItem(item);
            else event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), item);
            player.sendMessage("§6💀 Fragment de forge obtenu!");
        }
    }

    /**
     * Empêche la perte de durabilité pour les items avec Incassable
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();

        // Vérifie si l'item a l'enchantement Incassable
        if (plugin.getWeaponArmorEnchantmentManager().hasEnchantment(item, "incassable")) {
            event.setCancelled(true); // Annule la perte de durabilité
        }
    }

    // Méthodes utilitaires

    /**
     * Vérifie si l'item est une épée ou une armure valide
     */
    private boolean isValidWeaponOrArmor(ItemStack item) {
        Material type = item.getType();

        // Épées
        if (type == Material.NETHERITE_SWORD || type == Material.DIAMOND_SWORD ||
                type == Material.IRON_SWORD || type == Material.GOLDEN_SWORD ||
                type == Material.STONE_SWORD || type == Material.WOODEN_SWORD) {
            return true;
        }

        // Armures
        String typeName = type.name();
        return typeName.endsWith("_HELMET") || typeName.endsWith("_CHESTPLATE") ||
                typeName.endsWith("_LEGGINGS") || typeName.endsWith("_BOOTS");
    }

    /**
     * Vérifie si l'item est un livre d'enchantement unique (épée/armure)
     */
    private boolean isUniqueEnchantmentBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        if (!item.hasItemMeta()) return false;

        return item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "unique_enchant_book"), PersistentDataType.STRING);
    }

    /**
     * Vérifie si l'item est un livre d'enchantement de pioche (existant)
     */
    private boolean isPickaxeEnchantmentBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        if (!item.hasItemMeta()) return false;

        return item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "enchant_book_id"), PersistentDataType.STRING);
    }

    /**
     * Vérifie si c'est un livre d'enchantement (pioche OU épée/armure)
     */
    private boolean isAnyEnchantmentBook(ItemStack item) {
        return isUniqueEnchantmentBook(item) || isPickaxeEnchantmentBook(item);
    }

    /**
     * Vérifie si un bloc est interactif (pour éviter d'ouvrir le GUI dessus)
     */
    private boolean isInteractiveBlock(org.bukkit.Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, ENDER_CHEST, BARREL,
                 CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER,
                 BREWING_STAND, ENCHANTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON,
                 JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR, ACACIA_DOOR, DARK_OAK_DOOR,
                 CRIMSON_DOOR, WARPED_DOOR, IRON_DOOR -> true;
            default -> false;
        };
    }
}