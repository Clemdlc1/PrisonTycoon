package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.ContainerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique pour la configuration des conteneurs (RÉDUITE À 27 SLOTS)
 */
public class ContainerGUI {

    private final PrisonTycoon plugin;

    public ContainerGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * MODIFIÉ : Ouvre le menu de configuration d'un conteneur (27 slots, même cassé)
     */
    public void openContainerMenu(Player player, ItemStack containerItem) {
        ContainerData data = plugin.getContainerManager().getContainerData(containerItem);
        if (data == null) {
            player.sendMessage("§cErreur: Impossible de charger les données du conteneur!");
            return;
        }

        String title;
        if (data.isBroken()) {
            title = "§c💥 Conteneur Cassé - Tier " + data.getTier();
        } else {
            title = "§6📦 Configuration Conteneur Tier " + data.getTier();
        }

        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Informations du conteneur (slots 0-8)
        fillContainerInfo(inv, data);

        // Boutons de contrôle (slots 18-26)
        fillControlButtons(inv, data);

        // Séparateurs (slots 9-17)
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, createGlassPane("", Material.GRAY_STAINED_GLASS_PANE));
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);

        if (data.isBroken()) {
            player.sendMessage("§c💥 Conteneur cassé! Vous pouvez récupérer le contenu mais pas le configurer.");
        }
    }

    /**
     * MODIFIÉ : Remplit la zone d'informations du conteneur
     */
    private void fillContainerInfo(Inventory inv, ContainerData data) {
        // Information générale
        ItemStack info = new ItemStack(data.isBroken() ? Material.BARRIER : Material.CHEST);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(data.isBroken() ? "§c💥 Conteneur Cassé" : "§6📦 Conteneur Tier " + data.getTier());

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        infoLore.add("§e📊 Statistiques:");
        infoLore.add("§7┃ Capacité: §a" + NumberFormatter.format(data.getMaxCapacity()) + " items");
        infoLore.add("§7┃ Utilisé: §b" + NumberFormatter.format(data.getTotalItems()) + " items");
        infoLore.add("§7┃ Libre: §a" + NumberFormatter.format(data.getFreeSpace()) + " items");
        infoLore.add("§7┃ Remplissage: §d" + String.format("%.1f", data.getFillPercentage()) + "%");
        infoLore.add("");
        infoLore.add("§e🔧 Durabilité:");
        String durabilityColor = data.getDurabilityPercentage() > 50 ? "§2" :
                data.getDurabilityPercentage() > 25 ? "§e" : "§c";
        infoLore.add("§7┃ État: " + durabilityColor + data.getDurability() + "§7/" + durabilityColor + data.getMaxDurability());
        infoLore.add("§7┃ Pourcentage: " + durabilityColor + String.format("%.1f", data.getDurabilityPercentage()) + "%");

        if (data.isBroken()) {
            infoLore.add("");
            infoLore.add("§c💥 CONTENEUR CASSÉ!");
            infoLore.add("§c┃ Ne collecte plus les items");
            infoLore.add("§c┃ Configuration désactivée");
        }

        infoLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        inv.setItem(4, info);

        // Contenu actuel avec noms d'items
        if (!data.getContents().isEmpty()) {
            ItemStack contents = new ItemStack(Material.SHULKER_BOX);
            ItemMeta contentsMeta = contents.getItemMeta();
            contentsMeta.setDisplayName("§e📦 Contenu actuel");

            List<String> contentsLore = new ArrayList<>();
            contentsLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            contentsLore.add("§e📋 Items stockés:");

            int displayCount = 0;
            for (Map.Entry<ItemStack, Integer> entry : data.getContents().entrySet()) {
                if (displayCount >= 8) {
                    contentsLore.add("§7┃ §7... et " + (data.getContents().size() - 8) + " autres types");
                    break;
                }
                String itemName = ContainerData.getDisplayName(entry.getKey());
                contentsLore.add("§7┃ §e" + NumberFormatter.format(entry.getValue()) + "x §7" + itemName);
                displayCount++;
            }

            contentsLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            contentsMeta.setLore(contentsLore);
            contents.setItemMeta(contentsMeta);
            inv.setItem(6, contents);
        }

        // Filtres actuels
        ItemStack filters = new ItemStack(Material.HOPPER);
        ItemMeta filtersMeta = filters.getItemMeta();
        filtersMeta.setDisplayName("§e🎯 Filtres actuels");

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
        filtersMeta.setLore(filtersLore);
        filters.setItemMeta(filtersMeta);
        inv.setItem(2, filters);
    }

    /**
     * MODIFIÉ : Remplit les boutons de contrôle (slots 18-26)
     */
    private void fillControlButtons(Inventory inv, ContainerData data) {

        // NOUVEAU : Bouton configuration des filtres
        ItemStack configFilters = new ItemStack(Material.ITEM_FRAME);
        ItemMeta configMeta = configFilters.getItemMeta();
        configMeta.setDisplayName("§e🎯 Configurer les filtres");

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

        configMeta.setLore(configLore);
        configFilters.setItemMeta(configMeta);
        inv.setItem(19, configFilters);

        // Bouton vente (désactivé si cassé)
        Material sellMaterial = data.isBroken() ? Material.GRAY_DYE :
                (data.isSellEnabled() ? Material.EMERALD : Material.REDSTONE);
        String sellStatus = data.isBroken() ? "§8Indisponible" :
                (data.isSellEnabled() ? "§aActivée" : "§cDésactivée");

        ItemStack sellButton = new ItemStack(sellMaterial);
        ItemMeta sellMeta = sellButton.getItemMeta();
        sellMeta.setDisplayName("§6💰 Vente automatique: " + sellStatus);

        List<String> sellLore = new ArrayList<>();
        sellLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sellLore.add("§e⚙️ Configuration vente:");

        if (data.isBroken()) {
            sellLore.add("§c❌ Conteneur cassé");
            sellLore.add("§7┃ Fonction de vente désactivée");
        } else if (data.isSellEnabled()) {
            sellLore.add("§a✅ Le contenu sera vendu avec /sell all");
            sellLore.add("§7┃ §eUtilise 1 point de durabilité par vente");
            sellLore.add("§e▸ Clic: §cDésactiver la vente");
        } else {
            sellLore.add("§c❌ Le contenu ne sera PAS vendu avec /sell all");
            sellLore.add("§7┃ §eLe conteneur ne perdra pas de durabilité");
            sellLore.add("§e▸ Clic: §aActiver la vente");
        }
        sellLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        sellMeta.setLore(sellLore);
        sellButton.setItemMeta(sellMeta);
        inv.setItem(21, sellButton);

        // Bouton vider
        ItemStack clearButton = new ItemStack(data.getContents().isEmpty() ? Material.BUCKET : Material.LAVA_BUCKET);
        ItemMeta clearMeta = clearButton.getItemMeta();
        clearMeta.setDisplayName("§c🗑️ Récupérer le contenu");

        List<String> clearLore = new ArrayList<>();
        clearLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        if (data.getContents().isEmpty()) {
            clearLore.add("§7Le conteneur est vide");
            clearLore.add("§7┃ Rien à récupérer");
        } else {
            clearLore.add("§e📦 Récupération intelligente:");
            clearLore.add("§7┃ Calcule l'espace disponible");
            clearLore.add("§7┃ Récupère ce qui rentre");
            clearLore.add("§7┃ §aGarde le reste dans le conteneur");
            clearLore.add("§7┃ Items à récupérer: §b" + NumberFormatter.format(data.getTotalItems()));
        }
        clearLore.add("");
        clearLore.add("§e▸ Clic: §7Récupérer le contenu");
        clearLore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        clearMeta.setLore(clearLore);
        clearButton.setItemMeta(clearMeta);
        inv.setItem(23, clearButton);

        // Bouton effacer filtres (désactivé si cassé)
        ItemStack clearFiltersButton = new ItemStack(data.isBroken() ? Material.GRAY_DYE : Material.BARRIER);
        ItemMeta clearFiltersMeta = clearFiltersButton.getItemMeta();
        clearFiltersMeta.setDisplayName("§c🚫 Effacer tous les filtres");

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

        clearFiltersMeta.setLore(clearFiltersLore);
        clearFiltersButton.setItemMeta(clearFiltersMeta);
        inv.setItem(25, clearFiltersButton);

        // Bouton fermer
        ItemStack closeButton = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName("§c❌ Fermer");

        List<String> closeLore = new ArrayList<>();
        closeLore.add("§7Ferme ce menu");
        closeLore.add("§e▸ Clic: §7Retour au jeu");

        closeMeta.setLore(closeLore);
        closeButton.setItemMeta(closeMeta);
        inv.setItem(26, closeButton);

        // Séparateurs
        for (int i = 18; i < 27; i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, createGlassPane("", Material.BLACK_STAINED_GLASS_PANE));
            }
        }
    }

    /**
     * MODIFIÉ : Gère les clics dans le menu de conteneur (27 slots)
     */
    public void handleContainerMenuClick(Player player, int slot, ItemStack clickedItem, String title) {
        // Récupère le conteneur depuis l'inventaire du joueur
        ItemStack containerItem = findContainerInInventory(player, title);
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
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }

            player.closeInventory();
            plugin.getContainerFilterGUI().openFilterMenu(player, containerItem);
            return;
        }
        else if (slot == 21) { // Bouton vente
            if (data.isBroken()) {
                player.sendMessage("§c❌ Impossible de modifier les paramètres d'un conteneur cassé!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }

            data.setSellEnabled(!data.isSellEnabled());
            String status = data.isSellEnabled() ? "§aactivée" : "§cdésactivée";
            player.sendMessage("§6💰 Vente automatique " + status + "!");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
        }
        else if (slot == 25) { // Effacer filtres
            if (data.isBroken()) {
                player.sendMessage("§c❌ Impossible de modifier les filtres d'un conteneur cassé!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                return;
            }

            if (!data.getWhitelist().isEmpty()) {
                data.clearFilters();
                player.sendMessage("§c🚫 Tous les filtres ont été effacés!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            } else {
                player.sendMessage("§7Aucun filtre à effacer.");
            }
        }
        else if (slot == 23) { // Récupérer contenu
            if (!data.getContents().isEmpty()) {
                handleContentRecovery(player, data);
            } else {
                player.sendMessage("§7Le conteneur est déjà vide.");
            }
        }
        else if (slot == 26) { // Fermer
            player.closeInventory();
            return;
        }

        // Met à jour le conteneur et rouvre le menu
        plugin.getContainerManager().updateContainerItem(containerItem, data);
        openContainerMenu(player, containerItem);
    }

    /**
     * NOUVEAU : Gère la récupération intelligente du contenu
     */
    private void handleContentRecovery(Player player, ContainerData data) {
        // Calcule l'espace disponible dans l'inventaire
        int availableSpace = 0;
        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (invItem == null || invItem.getType() == Material.AIR) {
                availableSpace += 64; // Slot vide = 64 items max
            } else if (invItem.getAmount() < invItem.getMaxStackSize()) {
                availableSpace += invItem.getMaxStackSize() - invItem.getAmount();
            }
        }

        Map<ItemStack, Integer> contentsToMove;
        boolean partialRecovery = false;

        if (availableSpace >= data.getTotalItems()) {
            // Assez de place pour tout
            contentsToMove = data.clearContents();
        } else if (availableSpace > 0) {
            // Place partielle
            contentsToMove = data.clearContentsWithLimit(availableSpace);
            partialRecovery = true;
        } else {
            // Aucune place
            player.sendMessage("§c❌ Votre inventaire est plein! Impossible de récupérer le contenu.");
            player.sendMessage("§7Libérez de l'espace ou utilisez §a/sell all §7pour vider vos conteneurs.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int recovered = 0;
        int lost = 0;

        for (Map.Entry<ItemStack, Integer> entry : contentsToMove.entrySet()) {
            ItemStack itemToAdd = entry.getKey().clone();
            itemToAdd.setAmount(entry.getValue());

            Map<Integer, ItemStack> remaining = player.getInventory().addItem(itemToAdd);

            if (remaining.isEmpty()) {
                recovered += entry.getValue();
            } else {
                for (ItemStack remainingItem : remaining.values()) {
                    lost += remainingItem.getAmount();
                }
                recovered += entry.getValue() - lost;
            }
        }

        if (partialRecovery) {
            player.sendMessage("§e⚠️ Contenu partiellement récupéré: §a" + NumberFormatter.format(recovered) + " items");
            player.sendMessage("§7Il reste §e" + NumberFormatter.format(data.getTotalItems()) + " items §7dans le conteneur.");
            player.sendMessage("§7Libérez plus d'espace pour récupérer le reste.");
        } else {
            player.sendMessage("§a✅ Contenu récupéré: §e" + NumberFormatter.format(recovered) + " items");
        }

        if (lost > 0) {
            player.sendMessage("§c⚠️ §e" + NumberFormatter.format(lost) + " items perdus (calcul d'espace imprécis)!");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    /**
     * Trouve le conteneur dans l'inventaire du joueur basé sur le titre
     */
    private ItemStack findContainerInInventory(Player player, String title) {
        // Extrait le tier du titre
        String tierStr = title.replaceAll(".* Tier (\\d+).*", "$1");
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
     * Crée un panneau de verre décoratif
     */
    private ItemStack createGlassPane(String name, Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(name);
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