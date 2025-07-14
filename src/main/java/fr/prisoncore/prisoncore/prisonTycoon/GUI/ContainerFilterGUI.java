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

import java.util.*;

/**
 * Interface graphique dédiée à la configuration des filtres - CORRIGÉE
 */
public class ContainerFilterGUI {

    private final PrisonTycoon plugin;
    // NOUVEAU : Map pour associer les GUIs aux conteneurs
    private final Map<String, ItemStack> openFilterGUIs = new HashMap<>();

    public ContainerFilterGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * CORRIGÉ : Ouvre le menu de configuration des filtres sans modifier les items
     */
    public void openFilterMenu(Player player, ItemStack containerItem) {
        ContainerData data = plugin.getContainerManager().getContainerData(containerItem);
        if (data == null) {
            player.sendMessage("§cErreur: Impossible de charger les données du conteneur!");
            return;
        }

        // NOUVEAU : Crée un UUID unique pour ce GUI
        String guiId = player.getUniqueId().toString() + "_" + System.currentTimeMillis();
        String title = "§e🎯 Filtres - ID:" + guiId.substring(0, 8);

        Inventory filterInv = Bukkit.createInventory(null, 9, title);

        // CORRIGÉ : Place les items de référence EXACTEMENT comme ils sont sauvegardés
        Map<String, ItemStack> referenceItems = data.getReferenceItems();
        int slot = 0;

        for (ItemStack referenceItem : referenceItems.values()) {
            if (slot >= 9) break;

            // IMPORTANT : Clone l'item exact sans aucune modification
            ItemStack exactItem = referenceItem.clone();
            filterInv.setItem(slot, exactItem);
            slot++;
        }

        // NOUVEAU : Stocke la référence du conteneur
        openFilterGUIs.put(title, containerItem.clone());

        player.openInventory(filterInv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        // Message d'instructions
        player.sendMessage("§e🎯 §lConfiguration des filtres:");
        player.sendMessage("§7┃ Glissez vos items dans les 9 slots");
        player.sendMessage("§7┃ Les items seront préservés exactement comme ils sont");
        player.sendMessage("§7┃ Fermez l'inventaire pour sauvegarder");
        player.sendMessage("§7┃ §eAucun filtre = accepte tous les items");
    }

    /**
     * CORRIGÉ : Sauvegarde les filtres depuis l'inventaire sans modifier les items
     */
    public void saveFiltersFromInventory(Player player, Inventory filterInventory, String title) {
        // NOUVEAU : Utilise d'abord la map de tracking
        ItemStack trackedContainer = openFilterGUIs.get(title);

        ItemStack containerItem = null;

        if (trackedContainer != null) {
            // Trouve le conteneur correspondant dans l'inventaire du joueur
            containerItem = findMatchingContainerInInventory(player, trackedContainer);
        }

        // FALLBACK : Si pas trouvé, utilise l'ancienne méthode mais améliorée
        if (containerItem == null) {
            containerItem = findContainerFromFilterTitle(player, title);
        }

        if (containerItem == null) {
            plugin.getPluginLogger().warning("Conteneur introuvable pour " + player.getName() +
                    " - title: " + title + " - tracked: " + (trackedContainer != null));
            player.sendMessage("§c❌ Erreur: Conteneur introuvable! Vérifiez que le conteneur est toujours dans votre inventaire.");
            return;
        }

        ContainerData data = plugin.getContainerManager().getContainerData(containerItem);
        if (data == null) {
            player.sendMessage("§cErreur: Données du conteneur corrompues!");
            return;
        }

        // Efface les anciens filtres
        data.clearFilters();

        // CORRIGÉ : Collecte les items EXACTEMENT comme ils sont, sans modification
        Map<String, ItemStack> exactItems = new HashMap<>();
        Set<Material> uniqueMaterials = new HashSet<>();
        int totalItems = 0;

        for (ItemStack item : filterInventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                totalItems++;

                // IMPORTANT : Clone l'item EXACT sans aucune modification
                ItemStack exactClone = item.clone();
                exactClone.setAmount(1); // Normalise seulement la quantité

                Material material = item.getType();

                // Génère une clé unique pour cet item exact (Material + hash des métadonnées)
                String itemKey = material.name();
                if (item.hasItemMeta()) {
                    itemKey += "_" + item.getItemMeta().hashCode();
                }

                // Stocke l'item exact (pas de doublons par clé)
                exactItems.put(itemKey, exactClone);

                // Ajoute le Material pour les filtres (dédoublonné automatiquement par le Set)
                uniqueMaterials.add(material);
            }
        }

        // CORRIGÉ : Applique les filtres basés sur les Materials uniques
        for (Material material : uniqueMaterials) {
            data.toggleFilter(material);
        }

        // CORRIGÉ : Stocke les items exacts comme référence
        data.setReferenceItems(exactItems);

        // Met à jour le conteneur
        plugin.getContainerManager().updateContainerItem(containerItem, data);

        // Messages de confirmation
        if (totalItems == 0) {
            player.sendMessage("§a✅ Filtres supprimés! Le conteneur accepte maintenant tous les items.");
        } else {
            player.sendMessage("§a✅ Filtres sauvegardés! §e" + totalItems + " items exacts §7→ §e" + uniqueMaterials.size() + " matériaux filtrés:");

            int displayCount = 0;
            for (Material material : uniqueMaterials) {
                if (displayCount >= 5) {
                    player.sendMessage("§7   ... et " + (uniqueMaterials.size() - 5) + " autres");
                    break;
                }
                player.sendMessage("§7   • §e" + formatMaterialName(material));
                displayCount++;
            }

            if (totalItems > uniqueMaterials.size()) {
                player.sendMessage("§7   §8(§7" + (totalItems - uniqueMaterials.size()) + " items en doublon ignorés§8)");
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // NOUVEAU : Nettoie la référence
        openFilterGUIs.remove(title);

        plugin.getPluginLogger().debug("Filtres sauvegardés pour " + player.getName() +
                ": " + totalItems + " items exacts → " + uniqueMaterials.size() + " matériaux");
    }

    /**
     * NOUVEAU : Trouve un conteneur correspondant dans l'inventaire du joueur
     */
    private ItemStack findMatchingContainerInInventory(Player player, ItemStack referenceContainer) {
        ContainerData referenceData = plugin.getContainerManager().getContainerData(referenceContainer);
        if (referenceData == null) return null;

        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getContainerManager().isContainer(item)) {
                ContainerData itemData = plugin.getContainerManager().getContainerData(item);
                if (itemData != null && itemData.getTier() == referenceData.getTier()) {
                    // Vérifie si c'est le même conteneur (même UUID si disponible)
                    String refUUID = plugin.getContainerManager().getContainerUUID(referenceContainer);
                    String itemUUID = plugin.getContainerManager().getContainerUUID(item);

                    if (refUUID != null && itemUUID != null && refUUID.equals(itemUUID)) {
                        return item;
                    }

                    // FALLBACK : Si pas d'UUID, compare les données
                    if (itemData.getTotalItems() == referenceData.getTotalItems() &&
                            itemData.getDurability() == referenceData.getDurability()) {
                        return item;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Vérifie si un inventaire est un GUI de filtres
     */
    public boolean isFilterGUI(String title) {
        return title.contains("🎯 Filtres - ID:") || title.contains("🎯 Filtres - Conteneur Tier");
    }

    /**
     * AMÉLIORÉ : Trouve le conteneur correspondant au GUI de filtres (méthode fallback)
     */
    public ItemStack findContainerFromFilterTitle(Player player, String title) {
        // Nouvelle méthode avec ID unique
        if (title.contains("🎯 Filtres - ID:")) {
            return openFilterGUIs.get(title);
        }

        // Ancienne méthode pour compatibilité
        if (title.contains("🎯 Filtres - Conteneur Tier")) {
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
            } catch (NumberFormatException e) {
                plugin.getPluginLogger().warning("Erreur parsing tier du titre: " + title);
            }
        }

        return null;
    }

    /**
     * NOUVEAU : Nettoie les références des GUIs fermés
     */
    public void cleanupClosedGUI(String title) {
        openFilterGUIs.remove(title);
    }

    /**
     * Formate le nom d'un matériau pour l'affichage
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}