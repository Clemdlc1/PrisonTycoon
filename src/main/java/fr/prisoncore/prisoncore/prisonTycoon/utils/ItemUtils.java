package fr.prisoncore.prisoncore.prisonTycoon.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map; /**
 * Utilitaires pour les items Minecraft
 */
public class ItemUtils {

    /**
     * Crée un item avec nom et lore
     */
    public static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(ColorUtils.translateColors(name));
            }

            if (lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(ColorUtils.translateColors(line));
                }
                meta.setLore(loreList);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crée un item avec quantité
     */
    public static ItemStack createItem(Material material, int amount, String name, String... lore) {
        ItemStack item = createItem(material, name, lore);
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return item;
    }

    /**
     * Crée une tête de joueur
     */
    public static ItemStack createPlayerHead(String playerName, String displayName, String... lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            // Définit le propriétaire de la tête
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));

            if (displayName != null) {
                meta.setDisplayName(ColorUtils.translateColors(displayName));
            }

            if (lore.length > 0) {
                List<String> loreList = new ArrayList<>();
                for (String line : lore) {
                    loreList.add(ColorUtils.translateColors(line));
                }
                meta.setLore(loreList);
            }

            head.setItemMeta(meta);
        }

        return head;
    }

    /**
     * Vérifie si un item a un nom spécifique
     */
    public static boolean hasName(ItemStack item, String name) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() &&
                ColorUtils.stripColors(meta.getDisplayName()).equals(ColorUtils.stripColors(name));
    }

    /**
     * Vérifie si un item contient un texte dans son lore
     */
    public static boolean hasLore(ItemStack item, String text) {
        if (item == null || !item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (!meta.hasLore()) return false;

        String strippedText = ColorUtils.stripColors(text).toLowerCase();

        for (String line : meta.getLore()) {
            if (ColorUtils.stripColors(line).toLowerCase().contains(strippedText)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Clone un item de manière sécurisée
     */
    public static ItemStack safeClone(ItemStack item) {
        return item != null ? item.clone() : null;
    }

    /**
     * Ajoute des items à un inventaire de manière sécurisée
     */
    public static Map<Integer, ItemStack> safeAddItems(Inventory inventory, ItemStack... items) {
        Map<Integer, ItemStack> leftover = new HashMap<>();

        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                leftover.putAll(inventory.addItem(item));
            }
        }

        return leftover;
    }

    /**
     * Compte le nombre d'items d'un type dans un inventaire
     */
    public static int countItems(Inventory inventory, Material material) {
        int count = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }

        return count;
    }

    /**
     * Retire des items d'un inventaire
     */
    public static boolean removeItems(Inventory inventory, Material material, int amount) {
        if (countItems(inventory, material) < amount) {
            return false;
        }

        int remaining = amount;

        for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
            ItemStack item = inventory.getItem(i);

            if (item != null && item.getType() == material) {
                int itemAmount = item.getAmount();

                if (itemAmount <= remaining) {
                    inventory.setItem(i, null);
                    remaining -= itemAmount;
                } else {
                    item.setAmount(itemAmount - remaining);
                    remaining = 0;
                }
            }
        }

        return remaining == 0;
    }

    /**
     * Vérifie si un inventaire est plein
     */
    public static boolean isInventoryFull(Inventory inventory) {
        return inventory.firstEmpty() == -1;
    }

    /**
     * Obtient le nombre de slots libres dans un inventaire
     */
    public static int getFreeSlots(Inventory inventory) {
        int freeSlots = 0;

        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                freeSlots++;
            }
        }

        return freeSlots;
    }

    /**
     * Crée un item de remplissage pour les GUIs
     */
    public static ItemStack createFiller(Material material, String name) {
        return createItem(material, name != null ? name : " ");
    }

    /**
     * Crée un item de remplissage standard (vitre grise)
     */
    public static ItemStack createDefaultFiller() {
        return createFiller(Material.GRAY_STAINED_GLASS_PANE, "§7");
    }
}
