package fr.prisoncore.prisoncore.prisonTycoon.crystals;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Représente un cristal avec son type et niveau
 */
public class Crystal {
    private CrystalType type;
    private final int level;
    private boolean isRevealed;

    // Clés pour les données persistantes
    public static NamespacedKey CRYSTAL_KEY;
    public static NamespacedKey CRYSTAL_TYPE_KEY;
    public static NamespacedKey CRYSTAL_LEVEL_KEY;
    public static NamespacedKey CRYSTAL_REVEALED_KEY;

    public Crystal(CrystalType type, int level, boolean isRevealed) {
        this.type = type;
        this.level = Math.max(1, Math.min(20, level));
        this.isRevealed = isRevealed;
    }

    public Crystal(int level) {
        this(null, level, false); // Cristal vierge
    }

    public CrystalType getType() { return type; }
    public int getLevel() { return level; }
    public boolean isRevealed() { return isRevealed; }

    /**
     * Révèle le cristal avec un type aléatoire
     */
    public void reveal() {
        if (!isRevealed) {
            this.type = CrystalType.getRandomType();
            this.isRevealed = true;
        }
    }

    /**
     * Obtient le bonus de ce cristal
     */
    public double getBonus() {
        return isRevealed && type != null ? type.calculateBonus(level) : 0.0;
    }

    /**
     * Crée un ItemStack représentant ce cristal
     */
    public ItemStack createItemStack() {
        ItemStack crystal = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = crystal.getItemMeta();

        // Données persistantes
        meta.getPersistentDataContainer().set(CRYSTAL_KEY, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(CRYSTAL_LEVEL_KEY, PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(CRYSTAL_REVEALED_KEY, PersistentDataType.BOOLEAN, isRevealed);

        if (isRevealed && type != null) {
            meta.getPersistentDataContainer().set(CRYSTAL_TYPE_KEY, PersistentDataType.STRING, type.getInternalName());
        }

        // Apparence
        if (isRevealed && type != null) {
            meta.setDisplayName("§5✦ " + type.getDisplayName() + " §5Niveau " + level + " ✦");
        } else {
            meta.setDisplayName("§8✦ §7Cristal Mystérieux §5Niveau " + level + " §8✦");
        }

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (isRevealed && type != null) {
            lore.add("§7" + type.getDescription());
            lore.add("§7Effet: " + type.getDetailedDescription(level));
            lore.add("§7Niveau: §5" + level + "§7/§520");
            lore.add("");
            lore.add("§e📋 §lAPPLICATION");
            lore.add("§7▸ Coût d'application: §a" + getApplicationCost() + " XP");
            lore.add("§7▸ Maximum 4 cristaux par pioche");
            lore.add("§7▸ 1 seul cristal de ce type par pioche");
            lore.add("");
            lore.add("§c⚠️ §lRETRAIT");
            lore.add("§7▸ §c50% chance de destruction");
            lore.add("§7▸ Coût: §650 tokens");
        } else {
            lore.add("§7Type: §8???");
            lore.add("§7Effet: §8Mystérieux");
            lore.add("§7Niveau: §5" + level + "§7/§520");
            lore.add("");
            lore.add("§d🔮 §lRÉVÉLATION");
            lore.add("§7▸ §eClic droit§7 pour révéler le type");
            lore.add("§7▸ Le niveau reste identique");
            lore.add("§7▸ Type déterminé aléatoirement");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (isRevealed && type != null) {
            lore.add("§5✨ Cliquez sur votre pioche pour appliquer");
        } else {
            lore.add("§e✨ Clic droit pour révéler le mystère");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        crystal.setItemMeta(meta);

        return crystal;
    }

    /**
     * Obtient le coût d'application du cristal
     */
    public int getApplicationCost() {
        // Coûts : 1k, 2.5k, 5k, 10k XP selon la position
        return switch (level <= 5 ? 1 : level <= 10 ? 2 : level <= 15 ? 3 : 4) {
            case 1 -> 1000;
            case 2 -> 2500;
            case 3 -> 5000;
            default -> 10000;
        };
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
     * Crée un cristal depuis un ItemStack
     */
    public static Crystal fromItemStack(ItemStack item) {
        if (!isCrystal(item)) return null;

        ItemMeta meta = item.getItemMeta();
        int level = meta.getPersistentDataContainer().get(CRYSTAL_LEVEL_KEY, PersistentDataType.INTEGER);
        boolean revealed = meta.getPersistentDataContainer().get(CRYSTAL_REVEALED_KEY, PersistentDataType.BOOLEAN);

        CrystalType type = null;
        if (revealed) {
            String typeName = meta.getPersistentDataContainer().get(CRYSTAL_TYPE_KEY, PersistentDataType.STRING);
            if (typeName != null) {
                for (CrystalType crystalType : CrystalType.values()) {
                    if (crystalType.getInternalName().equals(typeName)) {
                        type = crystalType;
                        break;
                    }
                }
            }
        }

        return new Crystal(type, level, revealed);
    }

    @Override
    public String toString() {
        if (isRevealed && type != null) {
            return type.getDisplayName() + " Niveau " + level;
        } else {
            return "Cristal Mystérieux Niveau " + level;
        }
    }
}