package fr.skyblock.menus;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.managers.MenuManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchematicMenu extends BaseMenu {

    public SchematicMenu(CustomSkyblock plugin, MenuManager menuManager) {
        super(plugin, menuManager);
    }

    @Override
    public void open(Player player) {
        // Vérifier si le joueur peut créer une île
        if (!plugin.getPrisonTycoonHook().hasCustomPermission(player, "specialmine.free")) {
            player.sendMessage(ChatColor.RED + "Vous devez avoir la permission " + ChatColor.AQUA + "specialmine.free" +
                    ChatColor.RED + " ou assez de ressources pour créer une île !");
            return;
        }

        Inventory inv = createInventory(45, ChatColor.DARK_BLUE + "Choisir un type d'île");

        List<String> schematics = plugin.getSchematicManager().getAvailableSchematics();

        int slot = 10;
        for (String schematic : schematics) {
            Map<String, Object> schematicData = plugin.getSchematicManager().getSchematicData(schematic);

            Material displayMaterial = Material.valueOf((String) schematicData.getOrDefault("material", "GRASS_BLOCK"));
            String displayName = (String) schematicData.getOrDefault("name", schematic);
            List<String> description = (List<String>) schematicData.getOrDefault("description", List.of("Île standard"));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Type: " + ChatColor.WHITE + displayName);
            lore.add("");

            // Description du schematic
            for (String line : description) {
                lore.add(ChatColor.GRAY + line);
            }

            lore.add("");

            // Informations spécifiques selon le type
            addSchematicSpecificInfo(lore, schematic);

            lore.add("");
            lore.add(ChatColor.YELLOW + "Clic pour sélectionner");

            inv.setItem(slot, createItem(displayMaterial, ChatColor.GREEN + displayName, lore));

            slot++;
            if (slot == 17) slot = 19; // Ligne suivante
            if (slot >= 26) break; // Limite
        }

        // Informations générales
        inv.setItem(4, createItem(Material.COMPASS, ChatColor.GOLD + "Création d'île",
                ChatColor.GRAY + "Choisissez le type d'île",
                ChatColor.GRAY + "qui vous convient le mieux",
                "",
                ChatColor.YELLOW + "Chaque type a ses avantages !"));

        // Recommandations
        inv.setItem(6, createItem(Material.BOOK, ChatColor.AQUA + "Recommandations",
                ChatColor.GRAY + "Débutant: " + ChatColor.GREEN + "Île Classique",
                ChatColor.GRAY + "Expérimenté: " + ChatColor.YELLOW + "Île Jungle",
                ChatColor.GRAY + "Expert: " + ChatColor.RED + "Île Champignon",
                "",
                ChatColor.WHITE + "Choisissez selon votre expérience !"));

        // Coût de création
        String costInfo = plugin.getPrisonTycoonHook().hasCustomPermission(player, "specialmine.free") ?
                ChatColor.GREEN + "GRATUIT (Permission Free)" :
                ChatColor.YELLOW + "Coût: " + plugin.getConfig().getLong("prison-tycoon.island-creation-cost", 100) + " coins";

        inv.setItem(2, createItem(Material.GOLD_INGOT, ChatColor.GOLD + "Coût de création",
                ChatColor.GRAY + "Votre statut: " + costInfo,
                "",
                ChatColor.GRAY + "Les joueurs avec la permission",
                ChatColor.AQUA + "specialmine.free " + ChatColor.GRAY + "créent gratuitement"));

        // Bouton annuler
        inv.setItem(40, createItem(Material.BARRIER, ChatColor.RED + "Annuler",
                ChatColor.GRAY + "Annuler la création d'île",
                ChatColor.GRAY + "et retourner au menu principal"));

        fillEmptySlots(inv, Material.BLUE_STAINED_GLASS_PANE);

        player.openInventory(inv);
        setPlayerMenu(player, getMenuType());
    }

    private void addSchematicSpecificInfo(List<String> lore, String schematic) {
        switch (schematic.toLowerCase()) {
            case "classic" -> {
                lore.add(ChatColor.GREEN + "✓ Parfait pour débuter");
                lore.add(ChatColor.GREEN + "✓ Ressources équilibrées");
                lore.add(ChatColor.GREEN + "✓ Facile à développer");
                lore.add(ChatColor.YELLOW + "Recommandé pour les nouveaux joueurs");
            }
            case "desert" -> {
                lore.add(ChatColor.YELLOW + "• Spécialisé dans l'agriculture");
                lore.add(ChatColor.YELLOW + "• Ressources rares du désert");
                lore.add(ChatColor.RED + "⚠ Gestion de l'eau importante");
                lore.add(ChatColor.AQUA + "Idéal pour les farms");
            }
            case "jungle" -> {
                lore.add(ChatColor.GREEN + "✓ Ressources exotiques");
                lore.add(ChatColor.GREEN + "✓ Croissance rapide");
                lore.add(ChatColor.YELLOW + "• Plus de défis");
                lore.add(ChatColor.GOLD + "Pour les joueurs expérimentés");
            }
            case "snow" -> {
                lore.add(ChatColor.AQUA + "• Thème hivernal unique");
                lore.add(ChatColor.AQUA + "• Ressources arctiques");
                lore.add(ChatColor.YELLOW + "• Défis de survie");
                lore.add(ChatColor.WHITE + "Ambiance froide et paisible");
            }
            case "mushroom" -> {
                lore.add(ChatColor.LIGHT_PURPLE + "✦ Très rare et unique");
                lore.add(ChatColor.LIGHT_PURPLE + "✦ Ressources mystiques");
                lore.add(ChatColor.RED + "⚠ Très difficile");
                lore.add(ChatColor.GOLD + "Pour les maîtres de Skyblock");
            }
            case "nether" -> {
                lore.add(ChatColor.RED + "🔥 Thème infernal");
                lore.add(ChatColor.RED + "🔥 Ressources du Nether");
                lore.add(ChatColor.DARK_RED + "⚠ Extrêmement dangereux");
                lore.add(ChatColor.GOLD + "Challenge ultime");
            }
        }
    }

    @Override
    public void handleClick(Player player, int slot) {
        switch (slot) {
            case 40 -> { // Annuler
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Création d'île annulée.");
            }
            default -> {
                // Clic sur un schematic
                List<String> schematics = plugin.getSchematicManager().getAvailableSchematics();
                int schematicIndex = calculateSchematicIndex(slot);

                if (schematicIndex >= 0 && schematicIndex < schematics.size()) {
                    String selectedSchematic = schematics.get(schematicIndex);

                    // Vérification finale avant création
                    if (!plugin.getPrisonTycoonHook().hasCustomPermission(player, "specialmine.free")) {
                        player.sendMessage(ChatColor.RED + "Vous n'avez plus la permission de créer une île !");
                        player.closeInventory();
                        return;
                    }

                    player.closeInventory();

                    // Message de confirmation
                    String schematicName = (String) plugin.getSchematicManager().getSchematicData(selectedSchematic).get("name");
                    player.sendMessage(ChatColor.YELLOW + "Création de votre île " + ChatColor.GREEN + schematicName +
                            ChatColor.YELLOW + " en cours...");
                    player.sendMessage(ChatColor.GRAY + "Cela peut prendre quelques secondes...");

                    // Créer l'île
                    plugin.getSchematicManager().createIslandWithSchematic(player, selectedSchematic);
                }
            }
        }
    }

    @Override
    public String getMenuType() {
        return "schematic";
    }

    private int calculateSchematicIndex(int slot) {
        // Calcul basé sur la disposition: ligne 1 (slots 10-16), ligne 2 (19-25)
        if (slot >= 10 && slot <= 16) {
            return slot - 10;
        } else if (slot >= 19 && slot <= 25) {
            return (slot - 19) + 7;
        }
        return -1;
    }
}