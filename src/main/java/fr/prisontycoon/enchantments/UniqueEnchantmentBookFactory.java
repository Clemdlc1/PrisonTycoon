package fr.prisontycoon.enchantments;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory pour créer les livres d'enchantement uniques pour épées et armures
 */
public class UniqueEnchantmentBookFactory {

    private final PrisonTycoon plugin;
    private final NamespacedKey uniqueBookKey;

    public UniqueEnchantmentBookFactory(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.uniqueBookKey = new NamespacedKey(plugin, "unique_enchant_book");
    }

    /**
     * Crée un livre d'enchantement unique
     */
    public ItemStack createUniqueEnchantmentBook(String enchantId) {
        WeaponArmorEnchantmentManager.UniqueEnchantment enchant =
                plugin.getWeaponArmorEnchantmentManager().getEnchantment(enchantId);

        if (enchant == null) return null;

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();

        // Titre avec couleur selon le type
        String typeColor = getEnchantmentColor(enchantId);
        meta.setDisplayName(typeColor + "⚡ §l" + enchant.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§5✨ §lLivre d'Enchantement Unique");
        lore.add("");

        // Type d'item compatible avec informations correctes
        addCompatibilityInfo(lore, enchantId);

        lore.add("");
        lore.add("§6📖 §lDescription:");
        lore.add("§7" + enchant.getDescription());
        lore.add("");

        // Niveau maximum
        if (enchant.getMaxLevel() > 1) {
            lore.add("§e📊 §lNiveaux: §71 à " + enchant.getMaxLevel());
        } else {
            lore.add("§e📊 §lNiveaux: §7Niveau unique");
        }

        lore.add("");
        lore.add("§a🎯 §lUtilisation:");
        lore.add("§7▸ Shift + Clic droit avec épée/armure");
        lore.add("§7▸ Cliquez sur le livre dans votre inventaire");
        lore.add("§7▸ Maximum " + getMaxBooksPerItem(enchantId) + " livre(s) par item");

        lore.add("");
        lore.add("§c⚠ §lAttention:");
        lore.add("§7Ce livre sera consommé lors de l'application!");

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);

        // Marquer comme livre d'enchantement unique
        meta.getPersistentDataContainer().set(uniqueBookKey, PersistentDataType.STRING, enchantId);

        book.setItemMeta(meta);
        return book;
    }

    /**
     * Crée un item de boutique pour un enchantement
     */
    public ItemStack createShopItem(String enchantId) {
        WeaponArmorEnchantmentManager.UniqueEnchantment enchant =
                plugin.getWeaponArmorEnchantmentManager().getEnchantment(enchantId);

        if (enchant == null) return null;

        // Matériau selon le type
        Material material = getShopMaterial(enchantId);

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Titre avec couleur selon le type
        String typeColor = getEnchantmentColor(enchantId);
        meta.setDisplayName(typeColor + "⚡ §l" + enchant.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§5✨ §lEnchantement Unique Légendaire");
        lore.add("");

        // Type d'item compatible
        addCompatibilityInfo(lore, enchantId);

        lore.add("");
        lore.add("§6📖 §lEffet:");
        lore.add("§7" + enchant.getDescription());
        lore.add("");

        // Détails spécifiques selon l'enchantement
        addSpecificEnchantmentDetails(lore, enchantId, enchant);

        lore.add("");
        lore.add("§6💰 §lPrix: §e" + NumberFormatter.format(enchant.getCost()) + " beacons");
        lore.add("");
        lore.add("§e➤ Cliquez pour acheter le livre!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);

        // Marquer comme item de boutique
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "shop_enchant_id"),
                PersistentDataType.STRING,
                enchantId
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Ajoute les informations de compatibilité selon l'enchantement
     */
    private void addCompatibilityInfo(List<String> lore, String enchantId) {
        switch (enchantId) {
            case "tonnerre":
                lore.add("§5⚡ §lCompatible: §7Pioches et Épées");
                lore.add("§7(Livre partagé avec le système de pioche)");
                break;
            case "incassable":
                lore.add("§5⚡ §lCompatible: §7Pioches, Épées et Armures");
                lore.add("§7(Livre universel)");
                break;
            case "tornade":
            case "repercussion":
            case "behead":
            case "chasseur":
                lore.add("§c⚔ §lCompatible: §7Épées uniquement");
                break;
            default:
                lore.add("§5⚡ §lCompatible: §7Type inconnu");
                break;
        }
    }

    /**
     * Récupère la couleur selon le type d'enchantement
     */
    private String getEnchantmentColor(String enchantId) {
        switch (enchantId) {
            case "tonnerre":
            case "incassable":
                return "§5"; // Violet pour universels
            case "tornade":
            case "repercussion":
            case "behead":
            case "chasseur":
                return "§c"; // Rouge pour épées
            default:
                return "§5";
        }
    }

    /**
     * Récupère le matériau d'affichage dans la boutique
     */
    private Material getShopMaterial(String enchantId) {
        switch (enchantId) {
            case "tonnerre":
                return Material.TRIDENT; // Pour représenter la foudre
            case "incassable":
                return Material.ANVIL; // Pour représenter la résistance
            case "tornade":
                return Material.NETHERITE_SWORD;
            case "repercussion":
                return Material.DIAMOND_SWORD;
            case "behead":
                return Material.IRON_SWORD;
            case "chasseur":
                return Material.GOLDEN_SWORD;
            default:
                return Material.ENCHANTED_BOOK;
        }
    }

    /**
     * Récupère le nombre maximum de livres par item
     */
    private String getMaxBooksPerItem(String enchantId) {
        // Pour l'instant, même logique que les épées/armures standard
        return "1-2";
    }

    /**
     * Ajoute des détails spécifiques selon l'enchantement
     */
    private void addSpecificEnchantmentDetails(List<String> lore, String enchantId, WeaponArmorEnchantmentManager.UniqueEnchantment enchant) {
        switch (enchantId) {
            case "tonnerre":
                lore.add("§e⚡ §lEffets:");
                lore.add("§7▸ Chance: 5% + (Niveau ÷ 2)%");
                lore.add("§7▸ Dégâts: 0.5 à 2 cœurs");
                lore.add("§7▸ Max niveau: " + enchant.getMaxLevel());
                lore.add("§7▸ Fonctionne en minage ET combat");
                lore.add("§a▸ Compatible: Pioches et Épées");
                break;

            case "incassable":
                lore.add("§e🛡 §lEffets:");
                lore.add("§7▸ Durabilité infinie");
                lore.add("§7▸ Plus jamais d'usure");
                lore.add("§7▸ Fonctionne sur tous les équipements");
                lore.add("§7▸ Économisez vos réparations!");
                lore.add("§a▸ Compatible: Pioches, Épées et Armures");
                break;

            case "tornade":
                lore.add("§e🌪 §lEffets:");
                lore.add("§7▸ Chance: 10% par attaque");
                lore.add("§7▸ Animation spectaculaire de 3 secondes");
                lore.add("§7▸ Aspiration puis propulsion des ennemis");
                lore.add("§7▸ Dégâts légers continus pendant l'effet");
                break;

            case "repercussion":
                lore.add("§e⚖ §lEffets selon réputation:");
                lore.add("§a▸ Rép. Positive: Conserve inventaire");
                lore.add("§c▸ Rép. Négative: Vol de coins");
                lore.add("§7▸ Max niveau: " + enchant.getMaxLevel());
                lore.add("§7▸ Effet proportionnel à la réputation");
                break;

            case "behead":
                lore.add("§e💀 §lEffets:");
                lore.add("§7▸ 10% chance de tête joueur/monstre");
                lore.add("§7▸ Compatible: Zombie, Squelette, etc.");
                lore.add("§7▸ Trophées de collection!");
                lore.add("§7▸ Têtes spéciales pour boss rares");
                break;

            case "chasseur":
                lore.add("§e🏹 §lEffets:");
                lore.add("§7▸ Bonus coins vs réputation opposée");
                lore.add("§7▸ Calcul selon écart de réputation");
                lore.add("§7▸ Max niveau: " + enchant.getMaxLevel());
                lore.add("§7▸ Plus l'écart est grand, plus le gain");
                break;
        }
    }

    /**
     * Vérifie si un item est un livre d'enchantement unique
     */
    public boolean isUniqueEnchantmentBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) return false;
        if (!item.hasItemMeta()) return false;

        return item.getItemMeta().getPersistentDataContainer().has(uniqueBookKey, PersistentDataType.STRING);
    }

    /**
     * Récupère l'ID de l'enchantement depuis un livre
     */
    public String getEnchantmentId(ItemStack book) {
        if (!isUniqueEnchantmentBook(book)) return null;

        return book.getItemMeta().getPersistentDataContainer().get(uniqueBookKey, PersistentDataType.STRING);
    }
}