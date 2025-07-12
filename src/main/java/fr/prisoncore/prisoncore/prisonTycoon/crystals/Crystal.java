package fr.prisoncore.prisoncore.prisonTycoon.crystals;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un cristal avec ses propriétés
 */
public class Crystal {

    public static NamespacedKey CRYSTAL_KEY;
    public static NamespacedKey CRYSTAL_TYPE_KEY;
    public static NamespacedKey CRYSTAL_LEVEL_KEY;
    public static NamespacedKey CRYSTAL_REVEALED_KEY;

    private CrystalType type;
    private int level;
    private boolean revealed;

    /**
     * Constructeur pour cristal vierge
     */
    public Crystal(int level) {
        this.level = Math.max(1, Math.min(20, level));
        this.revealed = false;
        this.type = null;
    }

    /**
     * Constructeur pour cristal spécifique
     */
    public Crystal(CrystalType type, int level, boolean revealed) {
        this.type = type;
        this.level = Math.max(1, Math.min(20, level));
        this.revealed = revealed;
    }

    /**
     * Révèle le cristal avec un type aléatoire
     */
    public void reveal() {
        if (!revealed) {
            this.type = CrystalType.getRandomType();
            this.revealed = true;
        }
    }

    /**
     * Crée l'ItemStack du cristal
     */
    public ItemStack createItemStack() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        // Nom
        if (revealed) {
            meta.setDisplayName("§5✨ " + type.getDisplayName() + " §7[Niveau " + level + "]");
        } else {
            meta.setDisplayName("§8✨ §lCristal Vierge §7[Niveau " + level + "]");
        }

        // Lore
        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (revealed) {
            lore.add("§7Type: " + type.getDisplayName());
            lore.add("§7Niveau: §b" + level);
            lore.add("§7Effet: " + type.getDetailedDescription(level));
            lore.add("");
            lore.add("§a✅ §lRÉVÉLÉ");
            lore.add("§e💡 Clic-droit pour appliquer à votre pioche");
        } else {
            lore.add("§7Niveau: §b" + level);
            lore.add("§7Type: §8Inconnu");
            lore.add("");
            lore.add("§c❌ §lNON RÉVÉLÉ");
            lore.add("§e💡 Clic-droit pour révéler le type");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        meta.setLore(lore);

        // Données persistantes
        meta.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(CRYSTAL_LEVEL_KEY, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(CRYSTAL_REVEALED_KEY, PersistentDataType.BOOLEAN, revealed);

        if (revealed && type != null) {
            meta.getPersistentDataContainer().set(CRYSTAL_TYPE_KEY, PersistentDataType.STRING, type.getInternalName());
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Vérifie si un ItemStack est un cristal
     */
    public static boolean isCrystal(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;

        return item.getItemMeta().getPersistentDataContainer()
                .has(CRYSTAL_KEY, PersistentDataType.BOOLEAN);
    }

    /**
     * Crée un Crystal depuis un ItemStack
     */
    public static Crystal fromItemStack(ItemStack item) {
        if (!isCrystal(item)) return null;

        ItemMeta meta = item.getItemMeta();

        int level = meta.getPersistentDataContainer()
                .getOrDefault(CRYSTAL_LEVEL_KEY, PersistentDataType.INTEGER, 1);

        boolean revealed = meta.getPersistentDataContainer()
                .getOrDefault(CRYSTAL_REVEALED_KEY, PersistentDataType.BOOLEAN, false);

        if (revealed) {
            String typeName = meta.getPersistentDataContainer()
                    .get(CRYSTAL_TYPE_KEY, PersistentDataType.STRING);

            if (typeName != null) {
                for (CrystalType type : CrystalType.values()) {
                    if (type.getInternalName().equals(typeName)) {
                        return new Crystal(type, level, true);
                    }
                }
            }
        }

        return new Crystal(level);
    }

    // Getters
    public CrystalType getType() { return type; }
    public int getLevel() { return level; }
    public boolean isRevealed() { return revealed; }

    /**
     * Obtient le bonus de ce cristal
     */
    public double getBonus() {
        if (!revealed || type == null) return 0;
        return type.calculateBonus(level);
    }

    @Override
    public String toString() {
        if (revealed) {
            return type.getDisplayName() + " niveau " + level;
        } else {
            return "Cristal vierge niveau " + level;
        }
    }
}