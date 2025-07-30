package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.ContainerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Interface graphique dédiée à la configuration des filtres - VERSION FINALE CORRIGÉE
 * Sauvegarde et restaure l'état exact de l'inventaire des filtres.
 */
public class ContainerFilterGUI {

    private final PrisonTycoon plugin;
    private final Map<String, String> activeFilterGUIs = new HashMap<>();

    public ContainerFilterGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu de filtres et restaure l'état exact (position, meta, quantité).
     */
    public void openFilterMenu(Player player, ItemStack containerItem) {
        ContainerData data = plugin.getContainerManager().getContainerData(containerItem);
        if (data == null) {
            player.sendMessage("§cErreur: Impossible de charger les données du conteneur!");
            return;
        }

        String containerUUID = plugin.getContainerManager().getContainerUUID(containerItem);
        if (containerUUID == null) {
            player.sendMessage("§cErreur: Conteneur sans UUID valide!");
            return;
        }

        // Titre simple et lisible
        String title = "§e🎯 Filtres - Conteneur Tier " + data.getTier();
        Inventory filterInv = Bukkit.createInventory(null, 9, title);

        // Place les items de référence en utilisant le slot sauvegardé (la clé de la Map).
        Map<Integer, ItemStack> referenceItems = data.getReferenceItems();
        for (Map.Entry<Integer, ItemStack> entry : referenceItems.entrySet()) {
            int slot = entry.getKey();
            ItemStack referenceItem = entry.getValue();

            if (slot < 9) { // Sécurité pour s'assurer que le slot est valide
                // On clone l'item pour que l'inventaire ait sa propre copie.
                filterInv.setItem(slot, referenceItem.clone());
            }
        }

        // Associe ce GUI à l'UUID du conteneur pour le retrouver à la fermeture
        activeFilterGUIs.put(title, containerUUID);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.CONTAINER_FILTER, filterInv);
        player.openInventory(filterInv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        // Messages d'instructions
        player.sendMessage("§e🎯 §lConfiguration des filtres:");
        player.sendMessage("§7┃ Glissez vos items dans les 9 slots");
        player.sendMessage("§7┃ L'emplacement, la quantité et les métadonnées seront préservés");
        player.sendMessage("§7┃ Fermez l'inventaire pour sauvegarder");
        player.sendMessage("§7┃ §eAucun filtre = accepte tous les items");
    }

    /**
     * CORRIGÉ : Sauvegarde l'état exact de l'inventaire des filtres (position, meta, quantité).
     */
    public void saveFiltersFromInventory(Player player, Inventory filterInventory, String title) {
        // Récupère l'UUID depuis la map de tracking
        String containerUUID = activeFilterGUIs.get(title);
        if (containerUUID == null) {
            player.sendMessage("§c❌ Erreur: Impossible d'identifier le conteneur pour la sauvegarde!");
            return;
        }

        // Trouve le conteneur exact par UUID pour récupérer ses données
        ItemStack currentContainerItem = plugin.getContainerManager().findContainerByUUID(player, containerUUID);
        if (currentContainerItem == null) {
            player.sendMessage("§c❌ Erreur: Conteneur introuvable! La sauvegarde a échoué.");
            return;
        }

        ContainerData data = plugin.getContainerManager().getContainerData(currentContainerItem);
        if (data == null) {
            player.sendMessage("§cErreur: Données du conteneur corrompues! La sauvegarde a échoué.");
            return;
        }

        // Crée la nouvelle map de filtres en préservant slot, meta ET quantité.
        Map<Integer, ItemStack> newReferenceItems = new HashMap<>();
        Set<Material> uniqueMaterials = new HashSet<>();
        int totalStacks = 0;

        // On parcourt l'inventaire slot par slot pour conserver la position.
        for (int i = 0; i < filterInventory.getSize(); i++) {
            ItemStack item = filterInventory.getItem(i);

            if (item != null && item.getType() != Material.AIR) {
                totalStacks++;

                // ----- CORRECTION N°1 : CONSERVATION DE LA QUANTITÉ ET DES METAS -----
                // On clone l'item EXACTEMENT comme il est, SANS changer la quantité ou autre chose.
                ItemStack exactClone = item.clone();

                // Stocke le clone exact dans la map avec son slot (i) comme clé.
                newReferenceItems.put(i, exactClone);

                // Ajoute le Material pour la logique de filtrage interne (whitelist)
                uniqueMaterials.add(item.getType());
            }
        }

        // Met à jour l'objet "data" avec les nouvelles informations
        data.clearFilters();
        for (Material material : uniqueMaterials) {
            data.toggleFilter(material);
        }
        data.setReferenceItems(newReferenceItems);

        boolean success = plugin.getContainerManager().updateContainerInInventory(player, containerUUID, data);

        if (!success) {
            player.sendMessage("§c❌ Une erreur critique est survenue lors de la sauvegarde des filtres.");
            plugin.getPluginLogger().warning("Échec de updateContainerInInventory pour " + player.getName() + " (UUID: " + containerUUID + ")");
            activeFilterGUIs.remove(title); // Nettoyage même en cas d'échec
            return;
        }

        // Messages de confirmation
        if (totalStacks == 0) {
            player.sendMessage("§a✅ Filtres supprimés! Le conteneur accepte maintenant tous les items.");
        } else {
            long totalItemCount = newReferenceItems.values().stream().mapToLong(ItemStack::getAmount).sum();
            player.sendMessage("§a✅ Filtres sauvegardés! §e" + totalItemCount + " items §7(dans §e" + totalStacks + " stacks§7) sur §e" + uniqueMaterials.size() + " matériaux.");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // Nettoie la référence de l'inventaire actif
        activeFilterGUIs.remove(title);

        plugin.getPluginLogger().debug("Filtres sauvegardés pour " + player.getName() +
                                       " (UUID: " + containerUUID.substring(0, 8) + "): " +
                                       totalStacks + " slots utilisés.");
    }

    /**
     * Vérifie si un inventaire est un GUI de filtres via son titre.
     */
    public boolean isFilterGUI(String title) {
        return title.contains("🎯 Filtres - Conteneur Tier");
    }
}