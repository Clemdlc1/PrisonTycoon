package fr.prisontycoon.prestige;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Représente une récompense spéciale de prestige
 *
 * @param value Peut être int, String, etc. selon le type
 */
public record PrestigeReward(String id, String displayName, String description, RewardType type, Object value,
                             ItemStack displayItem) {

    public PrestigeReward(String id, String displayName, String description, RewardType type, Object value, Material displayItem) {
        this(id, displayName, description, type, value, createDisplayItem(displayItem, displayName, description, type));
    }

    private static ItemStack createDisplayItem(Material material, String displayName, String description, RewardType type) {
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

    @Override
    public ItemStack displayItem() {
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
        ARMOR_SET, 
        PET_FOOD,
        PET_BOX
    }

    /**
     * Créé les récompenses spéciales pour chaque palier
     */
    public static class SpecialRewards {

        // P5 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP5Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p5_autominer", "Autominer Fer",
                            "Un autominer de fer automatique", RewardType.AUTOMINER, "FER", Material.IRON_INGOT),
                    new PrestigeReward("p5_keys", "Clés",
                            "1 clé légendaire", RewardType.KEYS, "legendary:1", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p5_crystals", "Cristaux Vierges",
                            "3 cristaux vierges niveau 15", RewardType.CRYSTALS, "15:3", Material.DIAMOND)
            );
        }

        // P10 - Titre + cosmétique (pas de choix)
        public static PrestigeReward getP10Reward() {
            return new PrestigeReward("p10_title", "Titre Prisonnier",
                    "Titre 'Prisonnier' + 1 cosmétique Prison", RewardType.TITLE, "Prisonnier", Material.NAME_TAG);
        }//todo crée cosmétique

        // P15 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP15Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p15_book", "Livre Unique",
                            "1 livre unique aléatoire", RewardType.BOOK, "bomber", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p15_keys", "Clés",
                            "3 clés légendaire", RewardType.KEYS, "legendary:3", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p15_crystal", "Cristal Vierge",
                            "1 cristal vierge niveau 16", RewardType.CRYSTALS, "16:1", Material.DIAMOND)
            );
        }

        // P20 - Titre + cosmétique (pas de choix)
        public static PrestigeReward getP20Reward() {
            return new PrestigeReward("p20_title", "Expert du Prison",
                    "Titre 'Expert du Prison' + 1 cosmétique Prison", RewardType.TITLE, "Expert du Prison", Material.NAME_TAG);
        }//todo crée cosmétique

        // P25 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP25Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p25_book", "Livre Unique",
                            "1 livre unique", RewardType.BOOK, "autosell", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p25_keys", "Clés",
                            "3 clés légendaire", RewardType.KEYS, "legendary:3", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p25_crystals", "Cristaux Vierges",
                            "3 cristaux vierges niveau 16", RewardType.CRYSTALS, "16:3", Material.DIAMOND)
            );
        }

        // P30 - Titre + cosmétique (pas de choix)
        public static PrestigeReward getP30Reward() {
            return new PrestigeReward("p30_title", "Maître du Prison",
                    "Titre 'Maître du Prison' + 1 cosmétique Prison", RewardType.TITLE, "Maître du Prison", Material.NAME_TAG);
        }//todo crée cosmétique

        // P35 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP35Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p35_book", "Livre Unique",
                            "1 livre unique", RewardType.BOOK, "beaconbreaker", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p35_keys", "Clés",
                            "1 clé cristal", RewardType.KEYS, "crystal:1", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p35_crystal", "Cristal Vierge",
                            "1 cristal vierge niveau 17", RewardType.CRYSTALS, "17:1", Material.DIAMOND)
            );
        }

        // P40 - Titre + cosmétique (pas de choix)
        public static PrestigeReward getP40Reward() {
                return new PrestigeReward("p40_title", "Maître du Prison",
                        "Titre 'Maître du Prison' + 1 cosmétique Prison", RewardType.TITLE, "Maître du Prison", Material.NAME_TAG);
            }//todo crée cosmétique

        // P45 - Choix entre 3 récompenses
        public static List<PrestigeReward> getP45Rewards() {
            return Arrays.asList(
                    new PrestigeReward("p45_books", "Livres Uniques",
                            "2 livres uniques", RewardType.BOOK, "incassable,tonnerre", Material.ENCHANTED_BOOK),
                    new PrestigeReward("p45_keys", "Clés",
                            "3 clés cristal", RewardType.KEYS, "crystal:3", Material.TRIPWIRE_HOOK),
                    new PrestigeReward("p45_crystal", "Cristal Vierge",
                            "1 cristal vierge niveau 19", RewardType.CRYSTALS, "19:1", Material.DIAMOND)
            );
        }

        // P50 - Récompense ultime (pas de choix)
        public static PrestigeReward getP50Reward() {
            return new PrestigeReward("p50_ultimate", "Légende du Prison",
                    "Titre unique 'Légende du Prison' + cosmétique exclusif", RewardType.TITLE, "Légende du Prison", Material.NETHER_STAR);
        } //todo crée cosmétique

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
                case 30 -> List.of(getP30Reward());
                case 35 -> getP35Rewards();
                case 40 -> List.of(getP40Reward());
                case 45 -> getP45Rewards();
                case 50 -> List.of(getP50Reward());
                default -> new ArrayList<>();
            };
        }
    }
}