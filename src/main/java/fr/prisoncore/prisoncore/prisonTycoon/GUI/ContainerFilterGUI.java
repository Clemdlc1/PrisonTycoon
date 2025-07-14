package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.ContainerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Interface graphique dédiée à la configuration des filtres
 */
public class ContainerFilterGUI {

    private final PrisonTycoon plugin;

    public ContainerFilterGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu de configuration des filtres (9 slots libres)
     */
    public void openFilterMenu(Player player, ItemStack containerItem) {
        ContainerData data = plugin.getContainerManager().getContainerData(containerItem);
        if (data == null) {
            player.sendMessage("§cErreur: Impossible de charger les données du conteneur!");
            return;
        }

        Inventory filterInv = Bukkit.createInventory(null, 9, "§e🎯 Filtres - Conteneur Tier " + data.getTier());

        // Place les filtres actuels dans l'inventaire
        int slot = 0;
        for (Material material : data.getWhitelist()) {
            if (slot >= 9) break;

            ItemStack filterItem = new ItemStack(material);
            ItemMeta meta = filterItem.getItemMeta();
            meta.setDisplayName("§a✅ " + formatMaterialName(material));

            List<String> lore = new ArrayList<>();
            lore.add("§7Matériau autorisé dans le conteneur");
            lore.add("§c❌ Retirez cet item pour supprimer le filtre");
            meta.setLore(lore);

            filterItem.setItemMeta(meta);
            filterInv.setItem(slot, filterItem);
            slot++;
        }

        player.openInventory(filterInv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        // Message d'instructions
        player.sendMessage("§e🎯 §lConfiguration des filtres:");
        player.sendMessage("§7┃ Glissez vos items dans les 9 slots");
        player.sendMessage("§7┃ Retirez un item pour supprimer son filtre");
        player.sendMessage("§7┃ Fermez l'inventaire pour sauvegarder");
        player.sendMessage("§7┃ §eAucun filtre = accepte tous les items");
    }

    /**
     * Sauvegarde les filtres depuis l'inventaire de filtres
     */
    public void saveFiltersFromInventory(Player player, Inventory filterInventory, ItemStack containerItem) {
        ContainerData data = plugin.getContainerManager().getContainerData(containerItem);
        if (data == null) {
            player.sendMessage("§cErreur: Impossible de sauvegarder les filtres!");
            return;
        }

        // Efface les anciens filtres
        data.clearFilters();

        // Collecte les nouveaux filtres
        Set<Material> newFilters = new HashSet<>();
        int filtersCount = 0;

        for (ItemStack item : filterInventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                newFilters.add(item.getType());
                filtersCount++;
            }
        }

        // Applique les nouveaux filtres
        for (Material material : newFilters) {
            data.toggleFilter(material);
        }

        // Met à jour le conteneur
        plugin.getContainerManager().updateContainerItem(containerItem, data);

        // Messages de confirmation
        if (filtersCount == 0) {
            player.sendMessage("§a✅ Filtres supprimés! Le conteneur accepte maintenant tous les items.");
        } else {
            player.sendMessage("§a✅ Filtres sauvegardés! §e" + filtersCount + " matériaux autorisés:");

            int displayCount = 0;
            for (Material material : newFilters) {
                if (displayCount >= 5) {
                    player.sendMessage("§7   ... et " + (newFilters.size() - 5) + " autres");
                    break;
                }
                player.sendMessage("§7   • §e" + formatMaterialName(material));
                displayCount++;
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        plugin.getPluginLogger().debug("Filtres sauvegardés pour " + player.getName() +
                ": " + filtersCount + " matériaux");
    }

    /**
     * Vérifie si un inventaire est un GUI de filtres
     */
    public boolean isFilterGUI(String title) {
        return title.contains("🎯 Filtres - Conteneur Tier");
    }

    /**
     * Trouve le conteneur correspondant au GUI de filtres
     */
    public ItemStack findContainerFromFilterTitle(Player player, String title) {
        // Extrait le tier du titre
        String tierStr = title.replaceAll(".*Tier (\\d+).*", "$1");
        try {
            int tier = Integer.parseInt(tierStr);

            for (ItemStack item : player.getInventory().getContents()) {
                if (plugin.getContainerManager().isContainer(item)) {
                    ContainerData data = plugin.getContainerManager().getContainerData(item);
                    if (data != null && data.getTier() == tier) {
                        return item;
                    }
                }
            }
        } catch (NumberFormatException ignored) {}

        return null;
    }

    /**
     * Formate le nom d'un matériau pour l'affichage
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}