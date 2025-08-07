package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
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
        private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();

    // Cache des GUIs ouverts par joueur avec l'inventaire associé
    private final Map<UUID, GUIInfo> openGUIs = new ConcurrentHashMap<>();

    // Données additionnelles par GUI (ex: enchantment name, slot, etc.)
    private final Map<UUID, Map<String, String>> guiData = new ConcurrentHashMap<>();

    public GUIManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.guiTypeKey = new NamespacedKey(plugin, "gui_type");
    }

    // ===============================================================================================
    // UTILITAIRES ADVENTURE (TITRE, NOM, LORE)
    // ===============================================================================================

    /**
     * Désérialise un texte legacy (§) en Component et supprime l'italique par défaut.
     */
    public Component deserializeNoItalic(String legacyText) {
        return legacy.deserialize(legacyText).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Désérialise une liste de textes legacy en liste de Components sans italique.
     */
    public java.util.List<Component> deserializeNoItalics(java.util.List<String> lines) {
        return lines.stream().map(this::deserializeNoItalic).toList();
    }

    /**
     * Crée un inventaire avec un titre Adventure (non italique) à partir d'un titre legacy.
     */
    public Inventory createInventory(int size, String legacyTitle) {
        return org.bukkit.Bukkit.createInventory(null, size, deserializeNoItalic(legacyTitle));
    }

    /**
     * Applique un nom personnalisé Adventure (non italique) à partir d'un texte legacy.
     * Utilise ItemMeta.customName pour éviter les APIs dépréciées.
     */
    public void applyName(ItemMeta meta, String legacyName) {
        if (meta == null) return;
        Component name = deserializeNoItalic(legacyName);
        meta.customName(name);
        meta.displayName(name);
    }

    /**
     * Applique une lore Adventure non italique à partir d'une liste de textes legacy.
     */
    public void applyLore(ItemMeta meta, java.util.List<String> legacyLore) {
        if (meta == null) return;
        meta.lore(deserializeNoItalics(legacyLore));
    }

    /**
     * Applique display name + lore en une fois.
     */
    public void applyNameAndLore(ItemMeta meta, String legacyName, java.util.List<String> legacyLore) {
        if (meta == null) return;
        applyName(meta, legacyName);
        applyLore(meta, legacyLore);
    }

    /**
     * Retourne le titre legacy (§) d'une InventoryView (évite les appels verbeux au serializer).
     */
    public String getLegacyTitle(InventoryView view) {
        if (view == null) return "";
        return legacy.serialize(view.title());
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