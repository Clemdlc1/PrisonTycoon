package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire central pour tous les GUIs du plugin
 * Remplace le système title.contains() par des IDs uniques
 * CORRIGÉ : Gère les transitions entre GUIs
 */
public class GUIManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey guiTypeKey;

    // Cache des GUIs ouverts par joueur avec l'inventaire associé
    private final Map<UUID, GUIInfo> openGUIs = new ConcurrentHashMap<>();

    // Données additionnelles par GUI (ex: enchantment name, slot, etc.)
    private final Map<UUID, Map<String, String>> guiData = new ConcurrentHashMap<>();

    public GUIManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiTypeKey = new NamespacedKey(plugin, "gui_type");
        NamespacedKey guiInstanceKey = new NamespacedKey(plugin, "gui_instance");
    }

    /**
     * Enregistre l'ouverture d'un GUI pour un joueur
     * CORRIGÉ : Stocke aussi l'inventaire pour éviter les conflits lors des transitions
     */
    public void registerOpenGUI(Player player, GUIType guiType, Inventory inventory) {
        openGUIs.put(player.getUniqueId(), new GUIInfo(guiType, inventory));
    }

    /**
     * Enregistre l'ouverture d'un GUI avec des données additionnelles
     */
    public void registerOpenGUI(Player player, GUIType guiType, Inventory inventory, Map<String, String> data) {
        openGUIs.put(player.getUniqueId(), new GUIInfo(guiType, inventory));
        guiData.put(player.getUniqueId(), data);
    }

    /**
     * Supprime l'enregistrement d'un GUI fermé
     * CORRIGÉ : Ne supprime que si l'inventaire correspond (évite les suppressions lors des transitions)
     */
    public void unregisterGUI(Player player, Inventory closedInventory) {
        UUID playerId = player.getUniqueId();
        GUIInfo currentGUI = openGUIs.get(playerId);

        if (currentGUI != null && currentGUI.getInventory().equals(closedInventory)) {
            // C'est bien le GUI actuellement enregistré qui se ferme
            openGUIs.remove(playerId);
            guiData.remove(playerId);
        }
    }

    /**
     * Obtient le type de GUI ouvert par un joueur
     */
    public GUIType getOpenGUIType(Player player) {
        GUIInfo guiInfo = openGUIs.get(player.getUniqueId());
        return guiInfo != null ? guiInfo.getType() : null;
    }

    /**
     * Obtient l'inventaire du GUI ouvert par un joueur
     */
    public Inventory getOpenGUIInventory(Player player) {
        GUIInfo guiInfo = openGUIs.get(player.getUniqueId());
        return guiInfo != null ? guiInfo.getInventory() : null;
    }

    // NOUVEAU : Méthode pour récupérer une donnée spécifique
    public String getGUIData(Player player, String key) {
        Map<String, String> data = guiData.get(player.getUniqueId());
        return data != null ? data.get(key) : null;
    }

    /**
     * Ajoute des métadonnées à un item pour identifier le GUI
     */
    public ItemStack addGUIMetadata(ItemStack item, GUIType guiType) {
        if (item == null || !item.hasItemMeta()) return item;

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(guiTypeKey, PersistentDataType.STRING, guiType.getId());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Ajoute des métadonnées avec données additionnelles
     */
    public ItemStack addGUIMetadata(ItemStack item, GUIType guiType, String dataKey, String dataValue) {
        if (item == null || !item.hasItemMeta()) return item;

        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(guiTypeKey, PersistentDataType.STRING, guiType.getId());

        if (dataKey != null && dataValue != null) {
            NamespacedKey key = new NamespacedKey(plugin, "gui_" + dataKey);
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, dataValue);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Récupère le type de GUI depuis un item cliqué
     */
    public GUIType getGUITypeFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        String guiId = item.getItemMeta().getPersistentDataContainer()
                .get(guiTypeKey, PersistentDataType.STRING);

        return GUIType.fromId(guiId);
    }

    /**
     * Récupère une donnée spécifique depuis un item
     */
    public String getDataFromItem(ItemStack item, String dataKey) {
        if (item == null || !item.hasItemMeta()) return null;

        NamespacedKey key = new NamespacedKey(plugin, "gui_" + dataKey);
        return item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
    }

    // Structure pour stocker les infos du GUI
    private static class GUIInfo {
        private final GUIType type;
        private final Inventory inventory;
        private final long openTime;

        public GUIInfo(GUIType type, Inventory inventory) {
            this.type = type;
            this.inventory = inventory;
            this.openTime = System.currentTimeMillis();
        }

        public GUIType getType() {
            return type;
        }

        public Inventory getInventory() {
            return inventory;
        }

        public long getOpenTime() {
            return openTime;
        }
    }
}