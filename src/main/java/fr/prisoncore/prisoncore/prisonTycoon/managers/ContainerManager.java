package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.ContainerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des conteneurs - MODIFIÉ pour conserver les métadonnées
 */
public class ContainerManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey containerKey;
    private final NamespacedKey containerTierKey;
    private final NamespacedKey containerDataKey;
    private final NamespacedKey containerUUIDKey; // NOUVEAU: Identifiant unique

    public ContainerManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.containerKey = new NamespacedKey(plugin, "container");
        this.containerTierKey = new NamespacedKey(plugin, "container_tier");
        this.containerDataKey = new NamespacedKey(plugin, "container_data");
        this.containerUUIDKey = new NamespacedKey(plugin, "container_uuid"); // NOUVEAU

        plugin.getPluginLogger().info("§aContainerManager initialisé.");
    }

    /**
     * MODIFIÉ : Crée un nouvel item conteneur (non-stackable)
     */
    /**
     * CORRIGÉ : Crée un nouvel item conteneur avec UUID aléatoire unique
     */
    public ItemStack createContainer(int tier) {
        if (tier < 1 || tier > 5) {
            throw new IllegalArgumentException("Tier doit être entre 1 et 5");
        }

        ItemStack container = new ItemStack(Material.CHEST);
        ItemMeta meta = container.getItemMeta();

        // Nom du conteneur
        String tierName = getTierName(tier);
        meta.setDisplayName("§6📦 Conteneur " + tierName);

        // CORRIGÉ : UUID aléatoire UNIQUE pour chaque conteneur
        String uniqueId = UUID.randomUUID().toString();

        // CORRIGÉ : Utilise un hashCode basé sur l'UUID + timestamp pour garantir l'unicité absolue
        long timestamp = System.currentTimeMillis();
        int uniqueHash = (uniqueId + "_" + timestamp).hashCode();
        meta.setCustomModelData(Math.abs(uniqueHash % 1000000) + tier * 1000000); // Garantit l'unicité totale

        // Lore détaillé
        List<String> lore = new ArrayList<>();
        ContainerData data = new ContainerData(tier);

        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e📊 Informations du conteneur:");
        lore.add("§7┃ Tier: §6" + tier + " §7(" + tierName + "§7)");
        lore.add("§7┃ Capacité: §a" + NumberFormatter.format(data.getMaxCapacity()) + " items");
        lore.add("§7┃ Durabilité: §2" + data.getDurability() + "§7/§2" + data.getMaxDurability());
        lore.add("§7┃ État: §aNeuf");
        lore.add("");
        lore.add("§e⚙️ Fonctionnalités:");
        lore.add("§7┃ Auto-collecte lors du minage");
        lore.add("§7┃ Système de filtres (whitelist)");
        lore.add("§7┃ Compatible avec §6/sell all");
        lore.add("§7┃ Ne peut pas être posé au sol");
        lore.add("§7┃ §cNon-stackable");
        lore.add("");
        lore.add("§e🎮 Utilisation:");
        lore.add("§7┃ §aShift + Clic droit §7→ Configuration");
        lore.add("§7┃ Gardez dans votre inventaire");
        lore.add("§7┃ Les blocs minés vont directement dedans");
        lore.add("");
        lore.add("§e📈 Avantages du Tier " + tier + ":");
        switch (tier) {
            case 1 -> {
                lore.add("§7┃ Capacité de base (36 stacks)");
                lore.add("§7┃ Durabilité standard (50 utilisations)");
            }
            case 2 -> {
                lore.add("§7┃ Capacité doublée (72 stacks)");
                lore.add("§7┃ Durabilité améliorée (100 utilisations)");
            }
            case 3 -> {
                lore.add("§7┃ Grande capacité (144 stacks)");
                lore.add("§7┃ Bonne durabilité (200 utilisations)");
            }
            case 4 -> {
                lore.add("§7┃ Très grande capacité (288 stacks)");
                lore.add("§7┃ Haute durabilité (400 utilisations)");
            }
            case 5 -> {
                lore.add("§7┃ Capacité maximale (576 stacks)");
                lore.add("§7┃ Durabilité maximale (800 utilisations)");
            }
        }
        lore.add("");
        lore.add("§c⚠️ §7Se dégrade à chaque vente avec §c/sell all");
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);

        // CORRIGÉ : Données persistantes avec UUID unique
        meta.getPersistentDataContainer().set(containerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(containerTierKey, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, uniqueId);

        // Serialise les données du conteneur
        String serializedData = serializeContainerData(data);
        meta.getPersistentDataContainer().set(containerDataKey, PersistentDataType.STRING, serializedData);

        container.setItemMeta(meta);

        plugin.getPluginLogger().debug("Conteneur créé - Tier: " + tier + ", UUID: " + uniqueId + ", Hash: " + uniqueHash);

        return container;
    }

    /**
     * Vérifie si un item est un conteneur
     */
    public boolean isContainer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(containerKey, PersistentDataType.BOOLEAN);
    }

    /**
     * Récupère les données d'un conteneur depuis un item
     */
    public ContainerData getContainerData(ItemStack item) {
        if (!isContainer(item)) return null;

        ItemMeta meta = item.getItemMeta();
        String serializedData = meta.getPersistentDataContainer().get(containerDataKey, PersistentDataType.STRING);

        if (serializedData == null) {
            // Conteneur legacy, créer des données par défaut
            int tier = meta.getPersistentDataContainer().getOrDefault(containerTierKey, PersistentDataType.INTEGER, 1);
            return new ContainerData(tier);
        }

        return deserializeContainerData(serializedData);
    }

    /**
     * Met à jour un item conteneur avec de nouvelles données
     */
    public void updateContainerItem(ItemStack item, ContainerData data) {
        if (!isContainer(item)) return;

        ItemMeta meta = item.getItemMeta();

        // Met à jour le nom si nécessaire
        if (data.isBroken()) {
            meta.setDisplayName("§c💥 Conteneur " + getTierName(data.getTier()) + " §c(CASSÉ)");
        } else {
            meta.setDisplayName("§6📦 Conteneur " + getTierName(data.getTier()));
        }

        // Met à jour le lore
        List<String> lore = generateUpdatedLore(data);
        meta.setLore(lore);

        // Sauvegarde les données
        String serializedData = serializeContainerData(data);
        meta.getPersistentDataContainer().set(containerDataKey, PersistentDataType.STRING, serializedData);

        item.setItemMeta(meta);
    }

    /**
     * MODIFIÉ : Génère le lore mis à jour pour un conteneur
     */
    private List<String> generateUpdatedLore(ContainerData data) {
        List<String> lore = new ArrayList<>();

        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e📊 Informations du conteneur:");
        lore.add("§7┃ Tier: §6" + data.getTier() + " §7(" + getTierName(data.getTier()) + "§7)");
        lore.add("§7┃ Capacité: §a" + NumberFormatter.format(data.getMaxCapacity()) + " items");

        // Durabilité avec couleur
        String durabilityColor = data.getDurabilityPercentage() > 50 ? "§2" :
                data.getDurabilityPercentage() > 25 ? "§e" : "§c";
        lore.add("§7┃ Durabilité: " + durabilityColor + data.getDurability() + "§7/" + durabilityColor + data.getMaxDurability());

        // État
        if (data.isBroken()) {
            lore.add("§7┃ État: §c💥 CASSÉ");
        } else if (data.getDurabilityPercentage() < 25) {
            lore.add("§7┃ État: §6⚠️ Critique");
        } else {
            lore.add("§7┃ État: §aFonctionnel");
        }

        lore.add("");
        lore.add("§e📦 Contenu actuel:");
        lore.add("§7┃ Items stockés: §b" + NumberFormatter.format(data.getTotalItems()) + "§7/§b" + NumberFormatter.format(data.getMaxCapacity()));
        lore.add("§7┃ Remplissage: §d" + String.format("%.1f", data.getFillPercentage()) + "%");
        lore.add("§7┃ Espace libre: §a" + NumberFormatter.format(data.getFreeSpace()));

        // Filtres
        if (data.getWhitelist().isEmpty()) {
            lore.add("§7┃ Filtres: §7Aucun (accepte tout)");
        } else {
            lore.add("§7┃ Filtres: §e" + data.getWhitelist().size() + " matériaux autorisés");
        }

        // Vente
        lore.add("§7┃ Vente /sell all: " + (data.isSellEnabled() ? "§aActivée" : "§cDésactivée"));

        lore.add("");
        lore.add("§e🎮 Utilisation:");
        lore.add("§7┃ §aShift + Clic droit §7→ Configuration");

        if (data.isBroken()) {
            lore.add("");
            lore.add("§c💥 CONTENEUR CASSÉ!");
            lore.add("§c┃ Ne collecte plus les items");
            lore.add("§c┃ Contenu encore accessible");
        }

        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        return lore;
    }

    /**
     * Récupère tous les conteneurs d'un joueur
     */
    public List<ContainerData> getPlayerContainers(Player player) {
        List<ContainerData> containers = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                ContainerData data = getContainerData(item);
                if (data != null) {
                    containers.add(data);
                }
            }
        }

        return containers;
    }

    /**
     * MODIFIÉ : Tente d'ajouter un item aux conteneurs du joueur (avec métadonnées)
     */
    public boolean addItemToContainers(Player player, ItemStack itemToAdd) {
        if (itemToAdd == null || itemToAdd.getAmount() <= 0) return false;

        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                ContainerData data = getContainerData(item);
                if (data != null && !data.isBroken()) {
                    if (data.addItem(itemToAdd)) {
                        // Met à jour l'item dans l'inventaire
                        updateContainerItem(item, data);
                        return true;
                    }
                }
            }
        }

        return false; // Aucun conteneur disponible
    }

    /**
     * MODIFIÉ : Vend le contenu de tous les conteneurs du joueur (seulement les items vendables)
     */
    public long sellAllContainerContents(Player player) {
        long totalValue = 0;
        int totalItems = 0;
        List<ContainerData> brokenContainers = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                ContainerData data = getContainerData(item);
                if (data != null && data.isSellEnabled() && !data.isBroken()) {

                    // NOUVEAU : Obtient seulement les items vendables
                    Map<ItemStack, Integer> vendableItems = data.getVendableContents(
                            material -> plugin.getConfigManager().getSellPrice(material)
                    );

                    // Calcule la valeur du contenu vendable
                    long containerValue = 0;
                    for (Map.Entry<ItemStack, Integer> entry : vendableItems.entrySet()) {
                        long price = plugin.getConfigManager().getSellPrice(entry.getKey().getType());
                        containerValue += price * entry.getValue();
                        totalItems += entry.getValue();
                    }

                    if (containerValue > 0) {
                        totalValue += containerValue;

                        // NOUVEAU : Vide seulement les items vendables
                        data.clearVendableContents(material -> plugin.getConfigManager().getSellPrice(material));

                        // Utilise la durabilité
                        if (!data.useDurability(1)) {
                            brokenContainers.add(data);
                        }

                        // Met à jour l'item
                        updateContainerItem(item, data);
                    }
                }
            }
        }

        // Notifie des conteneurs cassés
        if (!brokenContainers.isEmpty()) {
            player.sendMessage("§c💥 " + brokenContainers.size() + " conteneur(s) cassé(s) lors de la vente!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }

        if (totalItems > 0) {
            player.sendMessage("§a✅ Contenu des conteneurs vendu: §e" + NumberFormatter.format(totalItems) + " items §7pour §6" + NumberFormatter.format(totalValue) + " coins");
        }

        return totalValue;
    }

    /**
     * Nom du tier
     */
    private String getTierName(int tier) {
        return switch (tier) {
            case 1 -> "§7Basique";
            case 2 -> "§aStandard";
            case 3 -> "§bAvancé";
            case 4 -> "§5Épique";
            case 5 -> "§6Légendaire";
            default -> "§fInconnu";
        };
    }

    /**
     * CORRIGÉ : Sérialise les données du conteneur avec version mise à jour
     */
    private String serializeContainerData(ContainerData data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(bos);

            // CORRIGÉ : Version 2 pour supporter les referenceItems
            dataOutput.writeInt(2); // Version augmentée

            // Données de base
            dataOutput.writeInt(data.getTier());
            dataOutput.writeInt(data.getDurability());
            dataOutput.writeBoolean(data.isSellEnabled());

            // Whitelist
            dataOutput.writeInt(data.getWhitelist().size());
            for (Material material : data.getWhitelist()) {
                dataOutput.writeUTF(material.name());
            }

            // Contents
            dataOutput.writeInt(data.getContents().size());
            for (Map.Entry<ItemStack, Integer> entry : data.getContents().entrySet()) {
                dataOutput.writeObject(entry.getKey());
                dataOutput.writeInt(entry.getValue());
            }

            // NOUVEAU: Items de référence (version 2+)
            dataOutput.writeInt(data.getReferenceItems().size());
            for (Map.Entry<String, ItemStack> entry : data.getReferenceItems().entrySet()) {
                dataOutput.writeUTF(entry.getKey());
                dataOutput.writeObject(entry.getValue());
            }

            dataOutput.close();
            return Base64.getEncoder().encodeToString(bos.toByteArray());

        } catch (IOException e) {
            plugin.getPluginLogger().warning("Erreur sérialisation conteneur: " + e.getMessage());
            return "";
        }
    }

    /**
     * MODIFIÉ: Désérialise les données du conteneur (avec items de référence)
     */
    private ContainerData deserializeContainerData(String serializedData) {
        try {
            byte[] data = Base64.getDecoder().decode(serializedData);
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(bis);

            // Version
            int version = dataInput.readInt();

            // Données de base
            int tier = dataInput.readInt();
            int durability = dataInput.readInt();
            boolean sellEnabled = dataInput.readBoolean();

            // Whitelist
            Set<Material> whitelist = new HashSet<>();
            int whitelistSize = dataInput.readInt();
            for (int i = 0; i < whitelistSize; i++) {
                try {
                    whitelist.add(Material.valueOf(dataInput.readUTF()));
                } catch (IllegalArgumentException ignored) {}
            }

            // Contents
            Map<ItemStack, Integer> contents = new LinkedHashMap<>();
            int contentsSize = dataInput.readInt();
            for (int i = 0; i < contentsSize; i++) {
                try {
                    ItemStack item = (ItemStack) dataInput.readObject();
                    int quantity = dataInput.readInt();
                    contents.put(item, quantity);
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Erreur lecture item conteneur: " + e.getMessage());
                }
            }

            // NOUVEAU: Items de référence (seulement version 2+)
            Map<String, ItemStack> referenceItems = new HashMap<>();
            if (version >= 2) {
                int refItemsSize = dataInput.readInt();
                for (int i = 0; i < refItemsSize; i++) {
                    try {
                        String key = dataInput.readUTF();
                        ItemStack refItem = (ItemStack) dataInput.readObject();
                        referenceItems.put(key, refItem);
                    } catch (Exception e) {
                        plugin.getPluginLogger().warning("Erreur lecture item référence: " + e.getMessage());
                    }
                }
            }

            dataInput.close();

            // Crée les données avec les nouvelles informations
            ContainerData containerData = new ContainerData(tier, contents, whitelist, sellEnabled, durability);
            containerData.setReferenceItems(referenceItems);

            return containerData;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur désérialisation conteneur: " + e.getMessage());
            return null;
        }
    }
    /**
     * NOUVEAU : Obtient l'UUID unique d'un conteneur
     */
    public String getContainerUUID(ItemStack item) {
        if (!isContainer(item)) return null;

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(containerUUIDKey, PersistentDataType.STRING);
    }

    /**
     * NOUVEAU : Trouve un conteneur spécifique par UUID dans l'inventaire
     */
    public ItemStack findContainerByUUID(Player player, String uuid) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                String containerUUID = getContainerUUID(item);
                if (uuid.equals(containerUUID)) {
                    return item;
                }
            }
        }
        return null;
    }
}