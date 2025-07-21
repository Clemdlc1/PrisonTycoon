package fr.prisontycoon.prestige;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Représente une récompense spéciale de prestige
 */
public class PrestigeReward {

    private final String id;
    private final String displayName;
    private final String description;
    private final RewardType type;
    private final Object value; // Peut être int, String, etc. selon le type
    private final ItemStack displayItem;
    public PrestigeReward(String id, String displayName, String description, RewardType type, Object value, Material displayMaterial) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.value = value;
        this.displayItem = createDisplayItem(displayMaterial);
    }

    private ItemStack createDisplayItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6" + displayName);
            List<String> lore = new ArrayList<>();
            lore.add("§7" + description);
            lore.add("");
            lore.add("§eType: §f" + type.name());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public RewardType getType() {
        return type;
    }

    public Object getValue() {
        return value;
    }

    public ItemStack getDisplayItem() {
        return displayItem.clone();
    }

    public enum RewardType {
        TOKENS,
        KEYS,
        CRYSTALS,
        AUTOMINER,
        BOOK,
        TITLE,
        COSMETIC,
        BEACONS,
        ARMOR_SET
    }

    /**
     * Créé les récompenses spéciales pour chaque palier
     */
    public static class SpecialRewards {

        // P5 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP5Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p5_autominer", "Autominer Emeraude",
                            "Un autominer d'émeraude automatique", RewardType.AUTOMINER, "emerald", Material.EMERALD),
                    new PrestigeReward("p5_keys", "Clés Premium",
                            "1 clé cristal + 1 clé légendaire", RewardType.KEYS, "crystal:1,legendary:1", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p5_crystals", "Cristaux Vierges",
                            "3 cristaux vierges niveau 17", RewardType.CRYSTALS, "17:3", Material.DIAMOND)
            );
        }

        // P10 - Titre + cosmétique (pas de choix)
        public static PrestigeReward getP10Reward() {
            return new PrestigeReward("p10_title", "Titre Prisonnier",
                    "Titre 'Prisonnier' + 1 cosmétique Prison", RewardType.TITLE, "Prisonnier", Material.NAME_TAG);
        }

        // P15 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP15Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p15_book", "Livre Unique",
                            "1 livre unique aléatoire", RewardType.BOOK, "unique_random", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p15_keys", "Clés Premium",
                            "2 clés cristal + 3 clés légendaire", RewardType.KEYS, "crystal:2,legendary:3", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p15_crystal", "Cristal Vierge",
                            "1 cristal vierge niveau 18", RewardType.CRYSTALS, "18:1", Material.DIAMOND)
            );
        }

        // P20 - Titre + cosmétique (pas de choix)
        public static PrestigeReward getP20Reward() {
            return new PrestigeReward("p20_title", "Expert du Prison",
                    "Titre 'Expert du Prison' + 1 cosmétique Prison", RewardType.TITLE, "Expert du Prison", Material.NAME_TAG);
        }

        // P25 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP25Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p25_book", "Livre Unique",
                            "1 livre unique aléatoire", RewardType.BOOK, "unique_random", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p25_keys", "Clés Premium",
                            "3 clés cristal + 5 clés légendaire", RewardType.KEYS, "crystal:3,legendary:5", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p25_crystals", "Cristaux Vierges",
                            "3 cristaux vierges niveau 18", RewardType.CRYSTALS, "18:3", Material.DIAMOND)
            );
        }

        // P30 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP30Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p30_book", "Livre Unique",
                            "1 livre unique aléatoire", RewardType.BOOK, "unique_random", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p30_keys", "Clés Premium",
                            "4 clés cristal + 10 clés légendaire", RewardType.KEYS, "crystal:4,legendary:10", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p30_crystal", "Cristal Vierge",
                            "1 cristal vierge niveau 19", RewardType.CRYSTALS, "19:1", Material.DIAMOND)
            );
        }

        // P35 - Titre + cosmétique (pas de choix)
        public static PrestigeReward getP35Reward() {
            return new PrestigeReward("p35_title", "Maître du Prison",
                    "Titre 'Maître du Prison' + 1 cosmétique Prison", RewardType.TITLE, "Maître du Prison", Material.NAME_TAG);
        }

        // P40 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP40Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p40_book", "Livre Unique",
                            "1 livre unique aléatoire", RewardType.BOOK, "unique_random", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p40_keys", "Clés Premium",
                            "5 clés cristal + 13 clés légendaire", RewardType.KEYS, "crystal:5,legendary:13", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p40_crystals", "Cristaux Vierges",
                            "3 cristaux vierges niveau 19", RewardType.CRYSTALS, "19:3", Material.DIAMOND)
            );
        }

        // P45 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP45Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p45_books", "Livres Uniques",
                            "2 livres uniques aléatoires", RewardType.BOOK, "unique_random:2", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p45_keys", "Clés Premium",
                            "7 clés cristal + 16 clés légendaire", RewardType.KEYS, "crystal:7,legendary:16", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p45_crystal", "Cristal Vierge",
                            "1 cristal vierge niveau 20", RewardType.CRYSTALS, "20:1", Material.DIAMOND)
            );
        }

        // P50 - Récompense ultime (pas de choix)
        public static PrestigeReward getP50Reward() {
            return new PrestigeReward("p50_ultimate", "Légende du Prison",
                    "Titre unique 'Légende du Prison' + cosmétique exclusif", RewardType.TITLE, "Légende du Prison", Material.NETHER_STAR);
        }

        /**
         * Obtient les récompenses spéciales pour un niveau de prestige donné
         */
        public static List<PrestigeReward> getSpecialRewardsForPrestige(int prestigeLevel) {
            return switch (prestigeLevel) {
                case 5 -> getP5Rewards();
                case 10 -> List.of(getP10Reward());
                case 15 -> getP15Rewards();
                case 20 -> List.of(getP20Reward());
                case 25 -> getP25Rewards();
                case 30 -> getP30Rewards();
                case 35 -> List.of(getP35Reward());
                case 40 -> getP40Rewards();
                case 45 -> getP45Rewards();
                case 50 -> List.of(getP50Reward());
                default -> new ArrayList<>();
            };
        }
    }
}