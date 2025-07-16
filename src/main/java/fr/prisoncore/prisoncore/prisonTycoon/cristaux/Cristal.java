package fr.prisoncore.prisoncore.prisonTycoon.cristaux;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.UUID;

/**
 * Représente un cristal avec ses propriétés
 */
public class Cristal {

    private final String uuid;
    private final int niveau;
    private CristalType type;
    private final boolean vierge;

    /**
     * Constructeur pour cristal vierge
     */
    public Cristal(int niveau) {
        this.uuid = UUID.randomUUID().toString();
        this.niveau = niveau;
        this.type = null;
        this.vierge = true;
    }

    /**
     * Constructeur pour cristal avec type défini
     */
    public Cristal(int niveau, CristalType type) {
        this.uuid = UUID.randomUUID().toString();
        this.niveau = niveau;
        this.type = type;
        this.vierge = false;
    }

    /**
     * Constructeur depuis données existantes
     */
    public Cristal(String uuid, int niveau, CristalType type, boolean vierge) {
        this.uuid = uuid;
        this.niveau = niveau;
        this.type = type;
        this.vierge = vierge;
    }

    /**
     * Révèle le type du cristal (si vierge)
     */
    public Cristal revealType() {
        if (!vierge) return this;

        // Sélection aléatoire du type
        CristalType[] types = CristalType.values();
        CristalType randomType = types[(int) (Math.random() * types.length)];

        return new Cristal(uuid, niveau, randomType, false);
    }

    /**
     * Crée l'ItemStack correspondant au cristal
     */
    public ItemStack toItemStack(NamespacedKey cristalUuidKey, NamespacedKey cristalLevelKey,
                                 NamespacedKey cristalTypeKey, NamespacedKey cristalViergeKey) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();

        // Stockage des données persistantes
        meta.getPersistentDataContainer().set(cristalUuidKey, PersistentDataType.STRING, uuid);
        meta.getPersistentDataContainer().set(cristalLevelKey, PersistentDataType.INTEGER, niveau);
        meta.getPersistentDataContainer().set(cristalViergeKey, PersistentDataType.BOOLEAN, vierge);

        if (!vierge && type != null) {
            meta.getPersistentDataContainer().set(cristalTypeKey, PersistentDataType.STRING, type.name());
        }

        // Nom et description
        if (vierge) {
            meta.setDisplayName("§d✨ Cristal Vierge §7(Niveau " + niveau + ")");
            meta.setLore(Arrays.asList(
                    "§7Un cristal mystérieux qui attend",
                    "§7d'être révélé...",
                    "",
                    "§e▸ Clic-droit pour révéler son type",
                    "§7Niveau: §f" + niveau,
                    ""
            ));
        } else {
            meta.setDisplayName("§d✨ Cristal " + type.getDisplayName() + " §7(Niveau " + niveau + ")");
            meta.setLore(Arrays.asList(
                    "§7" + type.getDescription(),
                    "",
                    "§7Bonus: §a" + type.getBonusDescription(niveau),
                    "§7Niveau: §f" + niveau + "§8/§720",
                    "",
                    "§e▸ Placez sur une pioche pour l'améliorer",
                    "§c▸ Retrait: 50% chance de destruction",
                    ""
            ));
        }

        item.setItemMeta(meta);
        return item;
    }

    // Getters
    public String getUuid() { return uuid; }
    public int getNiveau() { return niveau; }
    public CristalType getType() { return type; }
    public boolean isVierge() { return vierge; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Cristal cristal = (Cristal) obj;
        return uuid.equals(cristal.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "Cristal{uuid='" + uuid + "', niveau=" + niveau +
                ", type=" + type + ", vierge=" + vierge + '}';
    }
}