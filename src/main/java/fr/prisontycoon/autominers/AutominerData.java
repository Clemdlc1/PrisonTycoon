package fr.prisontycoon.autominers;

import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.cristaux.CristalType;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Représente un automineur avec ses statistiques et enchantements
 * Les données sont stockées sur l'ItemStack via PersistentDataContainer
 */
public class AutominerData {

    private final String uuid;
    private final AutominerType type;
    private final Map<String, Integer> enchantments;
    private final List<Cristal> appliedCristals;
    private final long creationTime;

    public AutominerData(String uuid, AutominerType type) {
        this.uuid = uuid;
        this.type = type;
        this.enchantments = new HashMap<>();
        this.appliedCristals = new ArrayList<>();
        this.creationTime = System.currentTimeMillis();
    }

    public AutominerData(String uuid, AutominerType type, Map<String, Integer> enchantments,
                         List<Cristal> cristals) {
        this.uuid = uuid;
        this.type = type;
        this.enchantments = new HashMap<>(enchantments);
        this.appliedCristals = new ArrayList<>(cristals);
        this.creationTime = System.currentTimeMillis();
    }

    /**
     * Crée un AutominerData depuis un ItemStack
     */
    public static AutominerData fromItemStack(ItemStack item, NamespacedKey uuidKey,
                                              NamespacedKey typeKey, NamespacedKey enchantKey,
                                              NamespacedKey cristalKey) {
        if (item == null || item.getItemMeta() == null) {
            return null;
        }

        var container = item.getItemMeta().getPersistentDataContainer();

        // UUID et type requis
        String uuid = container.get(uuidKey, PersistentDataType.STRING);
        String typeStr = container.get(typeKey, PersistentDataType.STRING);

        if (uuid == null || typeStr == null) {
            return null;
        }

        AutominerType type;
        try {
            type = AutominerType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        // Enchantements
        Map<String, Integer> enchantments = new HashMap<>();
        String enchantData = container.get(enchantKey, PersistentDataType.STRING);
        if (enchantData != null && !enchantData.isEmpty()) {
            String[] enchants = enchantData.split(",");
            for (String enchant : enchants) {
                String[] parts = enchant.split(":");
                if (parts.length == 2) {
                    try {
                        enchantments.put(parts[0], Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        // Cristaux
        List<Cristal> cristals = new ArrayList<>();
        String cristalData = container.get(cristalKey, PersistentDataType.STRING);
        if (cristalData != null && !cristalData.isEmpty()) {
            String[] cristalsArray = cristalData.split(";");
            for (String cristalStr : cristalsArray) {
                String[] parts = cristalStr.split(":");
                if (parts.length == 4) {
                    try {
                        cristals.add(new Cristal(parts[0], Integer.parseInt(parts[1]), CristalType.valueOf(parts[2]), Boolean.parseBoolean(parts[3])));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }

        return new AutominerData(uuid, type, enchantments, cristals);
    }

    // Getters de base
    public String getUuid() {
        return uuid;
    }

    public AutominerType getType() {
        return type;
    }

    public Map<String, Integer> getEnchantments() {
        return enchantments;
    }

    public List<Cristal> getAppliedCristals() {
        return appliedCristals;
    }

    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Obtient le niveau d'un enchantement
     */
    public int getEnchantmentLevel(String enchantmentName) {
        return enchantments.getOrDefault(enchantmentName.toLowerCase(), 0);
    }

    /**
     * Ajoute/améliore un enchantement
     */
    public boolean addEnchantment(String enchantmentName, int levels) {
        String enchantName = enchantmentName.toLowerCase();

        if (!type.supportsEnchantment(enchantName)) {
            return false;
        }

        int currentLevel = getEnchantmentLevel(enchantName);
        int newLevel = currentLevel + levels;
        int maxLevel = type.getMaxEnchantmentLevel(enchantName);

        if (newLevel > maxLevel) {
            newLevel = maxLevel;
        }

        enchantments.put(enchantName, newLevel);
        return newLevel > currentLevel;
    }

    /**
     * Applique un cristal Greed (max 2)
     */
    public boolean applyCristal(Cristal cristal) {
        if (appliedCristals.size() >= 2) {
            return false; // Maximum 2 cristaux
        }

        // Vérifie que c'est un cristal de type Greed compatible
        CristalType cristalType = cristal.getType();
        if (cristalType == null || !isGreedCristal(cristalType)) {
            return false;
        }

        appliedCristals.add(cristal);
        return true;
    }

    /**
     * Retire un cristal par son UUID
     */
    public Cristal removeCristal(String cristalUuid) {
        for (int i = 0; i < appliedCristals.size(); i++) {
            Cristal cristal = appliedCristals.get(i);
            if (cristal.getUuid().equals(cristalUuid)) {
                return appliedCristals.remove(i);
            }
        }
        return null;
    }

    /**
     * Vérifie si un cristal est de type Greed compatible
     */
    private boolean isGreedCristal(CristalType type) {
        return switch (type) {
            case MONEY_BOOST, TOKEN_BOOST, XP_BOOST, MINERAL_GREED -> true;
            default -> false;
        };
    }

    /**
     * Calcule la consommation de carburant en minutes (avec FuelEfficiency)
     */
    public int getActualFuelConsumption() {
        int baseFuel = type.getFuelConsumptionMinutes();
        int fuelEfficiency = getEnchantmentLevel("fuelefficiency");

        // Chaque niveau de FuelEfficiency réduit de 2% la consommation
        double reduction = fuelEfficiency * 0.02;
        return Math.max(1, (int) (baseFuel * (1.0 - reduction)));
    }

    /**
     * Calcule l'efficacité totale de minage (enchantement + cristaux)
     */
    public int getTotalEfficiency() {
        int baseEfficiency = getEnchantmentLevel("efficiency");

        // Bonus des cristaux MINERAL_GREED
        for (Cristal cristal : appliedCristals) {
            if (cristal.getType() == CristalType.MINERAL_GREED) {
                baseEfficiency += cristal.getNiveau() * 10; // 10 efficacité par niveau
            }
        }

        return baseEfficiency;
    }

    /**
     * Calcule la fortune totale
     */
    public int getTotalFortune() {
        return getEnchantmentLevel("fortune");
    }

    /**
     * Calcule le bonus de tokens total (enchantement + cristaux)
     */
    public int getTotalTokenBonus() {
        int baseTokens = getEnchantmentLevel("tokengreed");

        // Bonus des cristaux TOKEN_BOOST
        for (Cristal cristal : appliedCristals) {
            if (cristal.getType() == CristalType.TOKEN_BOOST) {
                baseTokens += cristal.getNiveau() * 5; // 5 tokens par niveau
            }
        }

        return baseTokens;
    }

    /**
     * Calcule le bonus d'XP total
     */
    public int getTotalExpBonus() {
        int baseExp = getEnchantmentLevel("expgreed");

        // Bonus des cristaux XP_BOOST
        for (Cristal cristal : appliedCristals) {
            if (cristal.getType() == CristalType.XP_BOOST) {
                baseExp += cristal.getNiveau() * 5; // 5 XP par niveau
            }
        }

        return baseExp;
    }

    /**
     * Calcule le bonus d'argent total
     */
    public int getTotalMoneyBonus() {
        int baseMoney = getEnchantmentLevel("moneygreed");

        // Bonus des cristaux MONEY_BOOST
        for (Cristal cristal : appliedCristals) {
            if (cristal.getType() == CristalType.MONEY_BOOST) {
                baseMoney += cristal.getNiveau() * 5; // 5 money par niveau
            }
        }

        return baseMoney;
    }

    /**
     * Convertit en ItemStack avec toutes les données stockées
     */
    public ItemStack toItemStack(NamespacedKey uuidKey, NamespacedKey typeKey,
                                 NamespacedKey enchantKey, NamespacedKey cristalKey) {
        ItemStack item = new ItemStack(type.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nom avec couleur de rareté
            meta.setDisplayName(type.getColoredName() + " §f⚡ Automineur");

            // Lore avec statistiques
            List<String> lore = createItemLore();
            meta.setLore(lore);

            // Stockage des données persistantes
            var container = meta.getPersistentDataContainer();

            // UUID et type
            container.set(uuidKey, PersistentDataType.STRING, uuid);
            container.set(typeKey, PersistentDataType.STRING, type.name());

            // Enchantements (format: "name:level,name:level")
            if (!enchantments.isEmpty()) {
                StringBuilder enchantData = new StringBuilder();
                for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                    if (enchantData.length() > 0) enchantData.append(",");
                    enchantData.append(entry.getKey()).append(":").append(entry.getValue());
                }
                container.set(enchantKey, PersistentDataType.STRING, enchantData.toString());
            }

            // Cristaux appliqués (format: "uuid:level:type:vierge;...")
            if (!appliedCristals.isEmpty()) {
                StringBuilder cristalData = new StringBuilder();
                for (Cristal cristal : appliedCristals) {
                    if (cristalData.length() > 0) {
                        cristalData.append(";");
                    }
                    cristalData.append(cristal.getUuid()).append(":")
                            .append(cristal.getNiveau()).append(":")
                            .append(cristal.getType()).append(":")
                            .append(cristal.isVierge());
                }
                container.set(cristalKey, PersistentDataType.STRING, cristalData.toString());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crée la lore descriptive de l'item
     */
    private List<String> createItemLore() {
        List<String> lore = new ArrayList<>();

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e⚡ §lAUTOMINEUR " + type.getColoredName().toUpperCase());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        // Statistiques de base
        lore.add("§6📊 §lSTATISTIQUES");
        lore.add("§7▸ Carburant: §e" + getActualFuelConsumption() + " min/tête");
        lore.add("§7▸ Efficacité: §a" + getTotalEfficiency());
        lore.add("§7▸ Fortune: §a" + getTotalFortune());
        lore.add("");

        // Bonus Greed
        if (getTotalTokenBonus() > 0 || getTotalExpBonus() > 0 || getTotalMoneyBonus() > 0 || getEnchantmentLevel("keygreed") > 0) {
            lore.add("§d💎 §lBONUS GREED");
            if (getTotalTokenBonus() > 0) {
                lore.add("§7▸ Tokens: §a+" + getTotalTokenBonus() + "%");
            }
            if (getTotalExpBonus() > 0) {
                lore.add("§7▸ Expérience: §a+" + getTotalExpBonus() + "%");
            }
            if (getTotalMoneyBonus() > 0) {
                lore.add("§7▸ Argent: §a+" + getTotalMoneyBonus() + "%");
            }
            if (getEnchantmentLevel("keygreed") > 0) {
                lore.add("§7▸ Clés: §a+" + getEnchantmentLevel("keygreed") + "%");
            }
            lore.add("");
        }

        // Cristaux appliqués
        lore.add("§d✨ §lCRISTAUX APPLIQUÉS §7(" + appliedCristals.size() + "/2)");
        if (!appliedCristals.isEmpty()) {
            for (Cristal cristal : appliedCristals) {
                lore.add("§8• §d" + cristal.getType().getDisplayName() + " " + cristal.getNiveau());
            }
        } else {
            lore.add("§8• §7Aucun cristal appliqué");
        }
        lore.add("");


        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§a⚡ Cliquez dans votre inventaire pour placer l'autominer");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        return lore;
    }
}