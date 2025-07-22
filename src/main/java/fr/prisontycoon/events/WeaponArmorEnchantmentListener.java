package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.enchantments.WeaponArmorEnchantmentManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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

        String title = event.getView().getTitle();

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
     * NOUVEAU : Gère l'application d'un livre en cliquant dessus (comme les cristaux)
     */
    private void handleBookApplicationClick(Player player, ItemStack book) {
        // Vérifie si c'est un livre d'enchantement unique (épée/armure)
        if (isUniqueEnchantmentBook(book)) {
            plugin.getWeaponArmorEnchantGUI().applyUniqueBook(player, book, 15); // Slot 15 par défaut
            return;
        }

        // Vérifie si c'est un livre de pioche existant (pour compatibilité)
        if (isPickaxeEnchantmentBook(book)) {
            // Applique le livre de pioche sur l'épée/armure si compatible
            String bookId = book.getItemMeta().getPersistentDataContainer().get(
                    new NamespacedKey(plugin, "enchant_book_id"), PersistentDataType.STRING);

            ItemStack item = player.getInventory().getItemInMainHand();
            if (item != null && (bookId.equals("tonnerre") || bookId.equals("incassable"))) {
                if (plugin.getWeaponArmorEnchantmentManager().isCompatible(bookId, item)) {
                    // CORRIGÉ : Utilise la nouvelle logique d'amélioration
                    boolean success = plugin.getWeaponArmorEnchantmentManager().addEnchantment(item, bookId, 1);

                    if (success) {
                        // Retirer le livre
                        book.setAmount(book.getAmount() - 1);

                        int newLevel = plugin.getWeaponArmorEnchantmentManager().getEnchantmentLevel(item, bookId);
                        String enchantName = plugin.getWeaponArmorEnchantmentManager().getEnchantment(bookId).getName();

                        if (newLevel == 1) {
                            player.sendMessage("§a✅ Enchantement §e" + enchantName + " §aappliqué!");
                        } else {
                            player.sendMessage("§a✅ Enchantement §e" + enchantName + " §aamélioré au niveau " + newLevel + "!");
                        }

                        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);

                        // Refresh le GUI
                        plugin.getWeaponArmorEnchantGUI().openEnchantMenu(player, item);
                    } else {
                        // Vérifie pourquoi ça a échoué
                        WeaponArmorEnchantmentManager.UniqueEnchantment enchant =
                                plugin.getWeaponArmorEnchantmentManager().getEnchantment(bookId);
                        int currentLevel = plugin.getWeaponArmorEnchantmentManager().getEnchantmentLevel(item, bookId);

                        if (currentLevel >= enchant.getMaxLevel()) {
                            player.sendMessage("§cCet enchantement est déjà au niveau maximum! (" + enchant.getMaxLevel() + ")");
                        } else {
                            int currentCount = plugin.getWeaponArmorEnchantmentManager().getUniqueEnchantmentCount(item);
                            boolean isWeapon = item.getType().name().contains("SWORD");
                            int maxCount = isWeapon ? 2 : 1;
                            player.sendMessage("§cNombre maximum d'enchantements uniques atteint! (" + maxCount + ")");
                        }
                    }
                } else {
                    player.sendMessage("§cCet enchantement n'est pas compatible avec cet item!");
                }
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
        }
    }

    /**
     * Gère la mort des joueurs pour les enchantements spéciaux
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        Player killer = dead.getKiller();

        if (killer != null) {
            // Gère les effets de Répercussion et Chasseur
            plugin.getWeaponArmorEnchantmentManager().handlePlayerDeath(dead, killer);
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