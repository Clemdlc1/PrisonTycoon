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
        plugin.getGUIManager().applyName(meta,typeColor + "⚡ §l" + enchant.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✨ §lEnchantement Unique Légendaire"); // UNIFORMISÉ
        lore.add("");

        // Type d'item compatible avec informations correctes
        addCompatibilityInfo(lore, enchantId);

        lore.add("");
        lore.add("§6📖 §lDescription:"); // UNIFORMISÉ
        lore.add("§7▸ " + enchant.getDescription()); // UNIFORMISÉ avec ▸
        lore.add("");

        // Niveau maximum - UNIFORMISÉ
        if (enchant.getMaxLevel() > 1) {
            lore.add("§e📊 §lNiveaux: §71 à " + enchant.getMaxLevel());
        } else {
            lore.add("§e📊 §lNiveaux: §7Niveau unique");
        }

        lore.add("");
        lore.add("§a🎯 §lUtilisation:"); // UNIFORMISÉ
        lore.add("§7▸ §6Cliquez dans le menu enchantements"); // UNIFORMISÉ
        lore.add("§7  pour appliquer à votre item");
        lore.add("§7▸ §aPeut être activé/désactivé");
        lore.add("§7▸ Maximum " + getMaxBooksPerItem(enchantId) + " livre(s) par item");

        lore.add("");
        lore.add("§e⚡ Pouvoir: §d" + getEnchantmentPowerDescription(enchantId)); // UNIFORMISÉ
        lore.add("");
        lore.add("§c⚠ §lAttention:");
        lore.add("§7Ce livre sera consommé lors de l'application!");

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getGUIManager().applyLore(meta, lore);

        // Marquer comme livre d'enchantement unique
        meta.getPersistentDataContainer().set(uniqueBookKey, PersistentDataType.STRING, enchantId);

        book.setItemMeta(meta);
        return book;
    }

    private String getEnchantmentPowerDescription(String enchantId) {
        return switch (enchantId) {
            case "tonnerre" -> "Foudroie les ennemis et les blocs";
            case "incassable" -> "Durabilité infinie";
            case "tornade" -> "Tourbillon destructeur";
            case "repercussion" -> "Explosion vengeresse";
            case "behead" -> "Décapitation brutale";
            case "chasseur" -> "Bonus contre les joueurs";
            case "bete_traquee" -> "Augmente fortement vos dégâts contre les entités non joueurs (PvE)";
            case "cuirasse_bestiale" -> "Après avoir touché une entité non joueur, réduis brièvement les dégâts subis (PvE)";
            default -> "Effet mystérieux";
        };
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
        plugin.getGUIManager().applyName(meta,typeColor + "⚡ §l" + enchant.getName());

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

        plugin.getGUIManager().applyLore(meta, lore);

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
     * CORRIGÉ : Ajoute les informations de compatibilité selon l'enchantement
     */
    private void addCompatibilityInfo(List<String> lore, String enchantId) {
        switch (enchantId) {
            case "tonnerre":
                lore.add("§5⚡ §lCompatible: §7Pioches et Épées"); // CORRIGÉ : pas armures
                break;
            case "incassable":
                lore.add("§5⚡ §lCompatible: §7Pioches, Épées et Armures"); // CORRIGÉ : toutes armures aussi
                break;
            case "tornade":
            case "repercussion":
            case "behead":
            case "chasseur":
            case "bete_traquee":
            case "cuirasse_bestiale":
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
        return switch (enchantId) {
            case "tonnerre", "incassable" -> "§5"; // Violet pour universels
            case "tornade", "repercussion", "behead", "chasseur" -> "§c"; // Rouge pour épées
            default -> "§5";
        };
    }

    /**
     * Récupère le matériau d'affichage dans la boutique
     */
    private Material getShopMaterial(String enchantId) {
        return switch (enchantId) {
            case "tonnerre" -> Material.TRIDENT; // Pour représenter la foudre
            case "incassable" -> Material.ANVIL; // Pour représenter la résistance
            case "tornade" -> Material.NETHERITE_SWORD;
            case "repercussion" -> Material.DIAMOND_SWORD;
            case "behead" -> Material.IRON_SWORD;
            case "chasseur" -> Material.GOLDEN_SWORD;
            case "bete_traquee" -> Material.DAMAGED_ANVIL; // marqueur offensif PvE
            case "cuirasse_bestiale" -> Material.NETHERITE_SWORD; // désormais épée
            default -> Material.ENCHANTED_BOOK;
        };
    }

    /**
     * Récupère le nombre maximum de livres par item
     */
    private String getMaxBooksPerItem(String enchantId) {
        return switch (enchantId) {
            case "tonnerre", "incassable" -> "1"; // Livres universels : 1 seul par item
            default -> "1-3"; // Épées peuvent avoir jusqu'à 3 enchantements uniques maintenant
        };
    }

    /**
     * CORRIGÉ : Ajoute des détails spécifiques selon l'enchantement
     */
    private void addSpecificEnchantmentDetails(List<String> lore, String enchantId, WeaponArmorEnchantmentManager.UniqueEnchantment enchant) {
        switch (enchantId) {
            case "tonnerre":
                lore.add("§e⚡ §lEffets:");
                lore.add("§7▸ Chance: 5% + (Niveau ÷ 2)%");
                lore.add("§7▸ Dégâts: 0.5 à 2 cœurs");
                lore.add("§7▸ Max niveau: " + enchant.getMaxLevel());
                lore.add("§7▸ Fonctionne en minage ET combat");
                lore.add("§a▸ Compatible: Pioches et Épées"); // CORRIGÉ
                break;

            case "incassable":
                lore.add("§e🛡 §lEffets:");
                lore.add("§7▸ Durabilité infinie");
                lore.add("§7▸ Plus jamais d'usure");
                lore.add("§7▸ Fonctionne sur tous les équipements");
                lore.add("§7▸ Économisez vos réparations!");
                lore.add("§a▸ Compatible: Pioches, Épées et Armures"); // CORRIGÉ
                break;

            case "tornade":
                lore.add("§e🌪 §lEffets:");
                lore.add("§7▸ Tourbillon dévastateur");
                lore.add("§7▸ Dégâts en zone");
                lore.add("§7▸ Repousse les ennemis");
                lore.add("§a▸ Compatible: Épées uniquement");
                break;

            case "repercussion":
                lore.add("§e💥 §lEffets:");
                lore.add("§7▸ Explosion à la mort du tueur");
                lore.add("§7▸ Vengeance posthume");
                lore.add("§7▸ Dégâts aux alentours");
                lore.add("§a▸ Compatible: Épées uniquement");
                break;

            case "behead":
                lore.add("§e🗡 §lEffets:");
                lore.add("§7▸ Décapitation brutale");
                lore.add("§7▸ Chance de tête de joueur");
                lore.add("§7▸ Effet psychologique");
                lore.add("§a▸ Compatible: Épées uniquement");
                break;

            case "chasseur":
                lore.add("§e🎯 §lEffets:");
                lore.add("§7▸ Bonus contre les joueurs");
                lore.add("§7▸ Dégâts amplifiés en PvP");
                lore.add("§7▸ Traque impitoyable");
                lore.add("§a▸ Compatible: Épées uniquement");
                break;

            default:
                lore.add("§e❓ §lEffets:");
                lore.add("§7▸ Effet mystérieux");
                lore.add("§7▸ Découvrez par vous-même!");
                break;
        }
    }
}