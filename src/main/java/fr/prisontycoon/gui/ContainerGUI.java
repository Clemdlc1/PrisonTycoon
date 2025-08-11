package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.ContainerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface graphique pour la configuration des conteneurs - CORRIGÉE
 * Utilise des métadonnées invisibles pour identifier les conteneurs
 */
public class ContainerGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey containerUUIDKey;

    public ContainerGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.containerUUIDKey = new NamespacedKey(plugin, "container_gui_uuid");
    }

    /**
     * CORRIGÉ : Ouvre le menu de configuration avec titre simple et métadonnées UUID
     */
    public void openContainerMenu(Player player, ItemStack containerItem) {
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

        // CORRIGÉ : Titre simple et lisible, sans UUID
        String title;
        if (data.isBroken()) {
            title = "§c💥 Conteneur Cassé - Tier " + data.getTier();
        } else {
            title = "§6📦 Configuration Conteneur Tier " + data.getTier();
        }

        Inventory inv = plugin.getGUIManager().createInventory(27, title);

        // Informations du conteneur (slots 0-8)
        fillContainerInfo(inv, data, containerUUID);

        // Boutons de contrôle (slots 18-26) avec UUID dans les métadonnées
        fillControlButtons(inv, data, containerUUID);

        // Séparateurs (slots 9-17)
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, createGlassPane(Material.GRAY_STAINED_GLASS_PANE));
        }

        plugin.getGUIManager().registerOpenGUI(player, GUIType.CONTAINER_CONFIG, inv);
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);

        if (data.isBroken()) {
            player.sendMessage("§c💥 Conteneur cassé! Vous pouvez récupérer le contenu mais pas le configurer.");
        }
    }

    /**
     * CORRIGÉ : Remplit la zone d'informations avec UUID dans les métadonnées
     */
    private void fillContainerInfo(Inventory inv, ContainerData data, String containerUUID) {
        // Information générale
        ItemStack info = new ItemStack(data.isBroken() ? Material.BARRIER : Material.CHEST);
        ItemMeta infoMeta = info.getItemMeta();
        plugin.getGUIManager().applyName(infoMeta, data.isBroken() ?
                "§c💥 Conteneur Cassé" :
                "§6📦 Conteneur Tier " + data.getTier());

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        infoLore.add("§e📊 Informations générales:");
        infoLore.add("§7┃ Tier: §6" + data.getTier());
        infoLore.add("§7┃ Capacité: §a" + NumberFormatter.format(data.getMaxCapacity()) + " items");

        if (data.isBroken()) {
            infoLore.add("§7┃ État: §c💥 CASSÉ");
            infoLore.add("§7┃ Durabilité: §c0§7/§7" + data.getMaxDurability());
        } else {
            infoLore.add("§7┃ Durabilité: §2" + data.getDurability() + "§7/§2" + data.getMaxDurability());
            double percentage = (double) data.getDurability() / data.getMaxDurability() * 100;
            infoLore.add("§7┃ État: §a" + String.format("%.1f", percentage) + "%");
        }

        infoLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // NOUVEAU : Stocke l'UUID dans les métadonnées
        infoMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
        plugin.getGUIManager().applyLore(infoMeta, infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Contenu actuel
        if (data.getTotalItems() > 0) {
            ItemStack contents = new ItemStack(Material.SHULKER_BOX);
            ItemMeta contentsMeta = contents.getItemMeta();
            plugin.getGUIManager().applyName(contentsMeta, "§e📦 Contenu actuel");

            List<String> contentsLore = new ArrayList<>();
            contentsLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            contentsLore.add("§e📊 Inventaire du conteneur:");
            contentsLore.add("§7┃ Items stockés: §a" + NumberFormatter.format(data.getTotalItems()));
            contentsLore.add("§7┃ Capacité totale: §a" + NumberFormatter.format(data.getMaxCapacity()));

            double fillPercentage = (double) data.getTotalItems() / data.getMaxCapacity() * 100;
            contentsLore.add("§7┃ Taux de remplissage: §d" + String.format("%.1f", fillPercentage) + "%");
            contentsLore.add("");

            var contents_map = data.getContents();
            int itemCount = 0;
            for (var entry : contents_map.entrySet()) {
                if (itemCount >= 5) {
                    contentsLore.add("§7┃ §7... et " + (contents_map.size() - 5) + " autres types");
                    break;
                }
                contentsLore.add("§7┃ §e" + formatMaterialName(entry.getKey().getType()) + "§7: §a" +
                        NumberFormatter.format(entry.getValue()));
                itemCount++;
            }
            contentsLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            // NOUVEAU : Stocke l'UUID dans les métadonnées
            contentsMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
            plugin.getGUIManager().applyLore(contentsMeta, contentsLore);
            contents.setItemMeta(contentsMeta);
            inv.setItem(6, contents);
        }

        // Filtres actuels
        ItemStack filters = new ItemStack(Material.HOPPER);
        ItemMeta filtersMeta = filters.getItemMeta();
        plugin.getGUIManager().applyName(filtersMeta, "§e🎯 Filtres actuels");

        List<String> filtersLore = new ArrayList<>();
        filtersLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (data.getWhitelist().isEmpty()) {
            filtersLore.add("§7Aucun filtre configuré");
            filtersLore.add("§7Le conteneur accepte tous les items");
        } else {
            filtersLore.add("§a✅ Filtres actifs: §b" + data.getWhitelist().size());
            filtersLore.add("§7Matériaux autorisés:");

            int count = 0;
            for (Material mat : data.getWhitelist()) {
                if (count >= 6) {
                    filtersLore.add("§7┃ §7... et " + (data.getWhitelist().size() - 6) + " autres");
                    break;
                }
                filtersLore.add("§7┃ §e" + formatMaterialName(mat));
                count++;
            }
        }

        filtersLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // NOUVEAU : Stocke l'UUID dans les métadonnées
        filtersMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
        plugin.getGUIManager().applyLore(filtersMeta, filtersLore);
        filters.setItemMeta(filtersMeta);
        inv.setItem(2, filters);
    }

    /**
     * CORRIGÉ : Remplit les boutons de contrôle avec UUID dans les métadonnées
     */
    private void fillControlButtons(Inventory inv, ContainerData data, String containerUUID) {

        // Bouton configuration des filtres
        ItemStack configFilters = new ItemStack(Material.ITEM_FRAME);
        ItemMeta configMeta = configFilters.getItemMeta();
        plugin.getGUIManager().applyName(configMeta, "§e🎯 Configurer les filtres");

        List<String> configLore = new ArrayList<>();
        configLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        configLore.add("§e⚙️ Gestion des filtres:");
        configLore.add("§7┃ Ouvre un menu de 9 slots");
        configLore.add("§7┃ Glissez vos items dedans");
        configLore.add("§7┃ Fermez pour sauvegarder");
        configLore.add("");
        configLore.add("§a✅ Filtres actuels: §b" + data.getWhitelist().size());
        if (data.isBroken()) {
            configLore.add("§c❌ Conteneur cassé - Configuration désactivée");
        } else {
            configLore.add("§e▸ Clic: §aOuvrir l'éditeur de filtres");
        }
        configLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // NOUVEAU : Stocke l'UUID dans les métadonnées
        configMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
        plugin.getGUIManager().applyLore(configMeta, configLore);
        configFilters.setItemMeta(configMeta);
        inv.setItem(19, configFilters);

        // Bouton vente
        Material sellMaterial = data.isBroken() ? Material.GRAY_DYE :
                (data.isSellEnabled() ? Material.EMERALD : Material.REDSTONE);
        String sellStatus = data.isBroken() ? "§8Indisponible" :
                (data.isSellEnabled() ? "§a✅ Activée" : "§c❌ Désactivée");

        ItemStack sellButton = new ItemStack(sellMaterial);
        ItemMeta sellMeta = sellButton.getItemMeta();
        plugin.getGUIManager().applyName(sellMeta, "§e💰 Vente automatique");

        List<String> sellLore = new ArrayList<>();
        sellLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sellLore.add("§e💸 Configuration de vente:");
        sellLore.add("§7┃ État: " + sellStatus);

        if (data.isBroken()) {
            sellLore.add("§7┃ Le conteneur doit être réparé");
        } else {
            sellLore.add("§7┃ Vend automatiquement le contenu");
            sellLore.add("§7┃ Utilise les prix du /shop");
            sellLore.add("§e▸ Clic: §7" + (data.isSellEnabled() ? "Désactiver" : "Activer"));
        }
        sellLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // NOUVEAU : Stocke l'UUID dans les métadonnées
        sellMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
        plugin.getGUIManager().applyLore(sellMeta, sellLore);
        sellButton.setItemMeta(sellMeta);
        inv.setItem(21, sellButton);

        // Bouton récupérer contenu
        ItemStack collectButton = new ItemStack(Material.HOPPER_MINECART);
        ItemMeta collectMeta = collectButton.getItemMeta();
        plugin.getGUIManager().applyName(collectMeta, "§a📤 Récupérer le contenu");

        List<String> collectLore = new ArrayList<>();
        collectLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        collectLore.add("§e📦 Récupération d'items:");

        if (data.getTotalItems() == 0) {
            collectLore.add("§7┃ Le conteneur est vide");
            collectLore.add("§7┃ Rien à récupérer");
        } else {
            collectLore.add("§7┃ Items disponibles: §a" + NumberFormatter.format(data.getTotalItems()));
            collectLore.add("§7┃ Transfère vers votre inventaire");
            collectLore.add("§e▸ Clic: §aRécupérer tout le contenu");
        }
        collectLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // NOUVEAU : Stocke l'UUID dans les métadonnées
        collectMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
        plugin.getGUIManager().applyLore(collectMeta, collectLore);
        collectButton.setItemMeta(collectMeta);
        inv.setItem(23, collectButton);

        // Bouton effacer filtres
        ItemStack clearFiltersButton = new ItemStack(data.isBroken() ?
                Material.GRAY_DYE : Material.BARRIER);
        ItemMeta clearFiltersMeta = clearFiltersButton.getItemMeta();
        plugin.getGUIManager().applyName(clearFiltersMeta, "§c🚫 Effacer tous les filtres");

        List<String> clearFiltersLore = new ArrayList<>();
        clearFiltersLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        clearFiltersLore.add("§e🎯 Gestion des filtres:");

        if (data.isBroken()) {
            clearFiltersLore.add("§c❌ Conteneur cassé");
            clearFiltersLore.add("§7┃ Configuration désactivée");
        } else {
            clearFiltersLore.add("§7┃ Supprime tous les filtres actifs");
            clearFiltersLore.add("§7┃ Le conteneur acceptera tous les items");
            clearFiltersLore.add("");
            clearFiltersLore.add("§a✅ Filtres actuels: §b" + data.getWhitelist().size());
            if (data.getWhitelist().isEmpty()) {
                clearFiltersLore.add("§7┃ Aucun filtre à effacer");
            } else {
                clearFiltersLore.add("§e▸ Clic: §cEffacer tous les filtres");
            }
        }
        clearFiltersLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // NOUVEAU : Stocke l'UUID dans les métadonnées
        clearFiltersMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
        plugin.getGUIManager().applyLore(clearFiltersMeta, clearFiltersLore);
        clearFiltersButton.setItemMeta(clearFiltersMeta);
        inv.setItem(25, clearFiltersButton);

        // Bouton fermer
        ItemStack closeButton = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta closeMeta = closeButton.getItemMeta();
        plugin.getGUIManager().applyName(closeMeta, "§c❌ Fermer");

        List<String> closeLore = new ArrayList<>();
        closeLore.add("§7Ferme ce menu");
        closeLore.add("§e▸ Clic: §7Retour au jeu");

        // NOUVEAU : Stocke l'UUID dans les métadonnées
        closeMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
        plugin.getGUIManager().applyLore(closeMeta, closeLore);
        closeButton.setItemMeta(closeMeta);
        inv.setItem(26, closeButton);

        // Séparateurs avec UUID
        for (int i = 18; i < 27; i++) {
            if (inv.getItem(i) == null) {
                ItemStack pane = createGlassPane(Material.BLACK_STAINED_GLASS_PANE);
                ItemMeta paneMeta = pane.getItemMeta();
                paneMeta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, containerUUID);
                pane.setItemMeta(paneMeta);
                inv.setItem(i, pane);
            }
        }
    }

    /**
     * CORRIGÉ : Gère les clics avec identification par métadonnées UUID
     */
    public void handleContainerMenuClick(Player player, int slot, ItemStack clickedItem, String title) {
        // NOUVEAU : Récupère l'UUID depuis les métadonnées de l'item cliqué
        String containerUUID = null;
        if (clickedItem != null && clickedItem.hasItemMeta()) {
            containerUUID = clickedItem.getItemMeta().getPersistentDataContainer()
                    .get(containerUUIDKey, PersistentDataType.STRING);
        }

        if (containerUUID == null) {
            player.sendMessage("§cErreur: Impossible d'identifier le conteneur!");
            player.closeInventory();
            return;
        }

        // CORRIGÉ : Trouve le conteneur exact par UUID
        ItemStack containerItem = plugin.getContainerManager().findContainerByUUID(player, containerUUID);
        if (containerItem == null) {
            player.sendMessage("§cErreur: Conteneur introuvable!");
            player.closeInventory();
            return;
        }

        ContainerData data = plugin.getContainerManager().getContainerData(containerItem);
        if (data == null) {
            player.sendMessage("§cErreur: Données du conteneur corrompues!");
            player.closeInventory();
            return;
        }

        // Gestion des boutons
        if (slot == 19) { // Configurer filtres
            if (data.isBroken()) {
                player.sendMessage("§c❌ Impossible de configurer les filtres d'un conteneur cassé!");
                return;
            }
            plugin.getContainerFilterGUI().openFilterMenu(player, containerItem);
        } else if (slot == 21) { // Vente automatique
            if (data.isBroken()) {
                player.sendMessage("§c❌ Impossible de configurer la vente d'un conteneur cassé!");
                return;
            }

            data.setSellEnabled(!data.isSellEnabled());
            plugin.getContainerManager().saveContainerItem(containerItem, data);

            String status = data.isSellEnabled() ? "§a✅ activée" : "§c❌ désactivée";
            player.sendMessage("§e💰 Vente automatique " + status + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

            // Recharge le menu pour mettre à jour l'affichage
            openContainerMenu(player, containerItem);
        } else if (slot == 23) { // Récupérer contenu
            if (data.getTotalItems() == 0) {
                player.sendMessage("§c❌ Le conteneur est vide!");
                return;
            }

            int itemsTransferred = plugin.getContainerManager().transferContainerToPlayer(player, data);

            if (itemsTransferred > 0) {
                plugin.getContainerManager().saveContainerItem(containerItem, data);
                player.sendMessage("§a✅ " + NumberFormatter.format(itemsTransferred) +
                        " items récupérés dans votre inventaire!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);

                // Recharge le menu pour mettre à jour l'affichage
                openContainerMenu(player, containerItem);
            } else {
                player.sendMessage("§c❌ Votre inventaire est plein!");
            }
        } else if (slot == 25) { // Effacer filtres
            if (data.isBroken()) {
                player.sendMessage("§c❌ Impossible de modifier les filtres d'un conteneur cassé!");
                return;
            }

            if (data.getWhitelist().isEmpty()) {
                player.sendMessage("§c❌ Aucun filtre à effacer!");
                return;
            }

            data.clearFilters();
            data.getReferenceItems().clear();
            plugin.getContainerManager().saveContainerItem(containerItem, data);

            player.sendMessage("§a✅ Tous les filtres ont été effacés!");
            player.sendMessage("§7Le conteneur accepte maintenant tous les items.");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // Recharge le menu pour mettre à jour l'affichage
            openContainerMenu(player, containerItem);
        } else if (slot == 26) { // Fermer
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);
        }
    }

    /**
     * Crée un panneau de verre décoratif
     */
    private ItemStack createGlassPane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        plugin.getGUIManager().applyName(meta, "");
        pane.setItemMeta(meta);
        return pane;
    }

    /**
     * Formate le nom d'un matériau pour l'affichage
     */
    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}