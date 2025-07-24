package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.ContainerData;
import fr.prisontycoon.utils.NumberFormatter;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ULTRA-OPTIMISÉ : Gestionnaire des conteneurs avec cache réactif, sérialisation robuste et cycle de vie complet.
 * CORRIGÉ : Préserve l'identité des conteneurs lors de chaque sauvegarde.
 */
public class ContainerManager {

    private static final int DATA_VERSION = 3;
    private final PrisonTycoon plugin;
    private final NamespacedKey containerKey;
    private final NamespacedKey containerTierKey;
    private final NamespacedKey containerDataKey;
    private final NamespacedKey containerUUIDKey;
    private final Map<String, ContainerData> containerCache = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerContainers = new ConcurrentHashMap<>();

    public ContainerManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.containerKey = new NamespacedKey(plugin, "container");
        this.containerTierKey = new NamespacedKey(plugin, "container_tier");
        this.containerDataKey = new NamespacedKey(plugin, "container_data");
        this.containerUUIDKey = new NamespacedKey(plugin, "container_uuid");
    }

    // === METHODES COEUR : SAVE & LOAD ===

    /**
     * Méthode centrale qui écrit les données du cache vers un ItemStack physique.
     * C'est la seule source de vérité pour la persistance et l'affichage.
     *
     * @param containerItem L'item à mettre à jour.
     * @param data          Les données à sauvegarder sur l'item.
     * @return true si la sauvegarde a réussi.
     */
    private boolean saveDataToItem(ItemStack containerItem, ContainerData data) {
        if (containerItem == null || data == null) return false;

        // On lit l'UUID de l'item AVANT de modifier ses métadonnées.
        String uuid = getContainerUUID(containerItem);
        if (uuid == null) {
            plugin.getPluginLogger().severe("Tentative de sauvegarde d'un conteneur sans UUID ! L'item pourrait être corrompu.");
            return false;
        }

        ItemMeta meta = containerItem.getItemMeta();
        if (meta == null) return false;


        meta.getPersistentDataContainer().set(containerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, uuid);
        meta.getPersistentDataContainer().set(containerTierKey, PersistentDataType.INTEGER, data.getTier());

        String serialized = serializeContainerData(data);
        if (serialized == null) {
            plugin.getPluginLogger().severe("Échec critique de la sérialisation pour le conteneur UUID: " + uuid);
            return false;
        }
        meta.getPersistentDataContainer().set(containerDataKey, PersistentDataType.STRING, serialized);

        updateLoreAndName(meta, data);

        containerItem.setItemMeta(meta);
        return true;
    }

    public ContainerData loadDataFromItem(ItemStack item) {
        if (!isContainer(item)) return null;
        String uuid = getContainerUUID(item);
        if (uuid == null) return null;
        if (containerCache.containsKey(uuid)) return containerCache.get(uuid);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String serializedData = meta.getPersistentDataContainer().get(containerDataKey, PersistentDataType.STRING);
        ContainerData data = deserializeContainerData(serializedData);

        if (data != null) {
            containerCache.put(uuid, data);
            return data;
        } else {
            int tier = meta.getPersistentDataContainer().getOrDefault(containerTierKey, PersistentDataType.INTEGER, 1);
            ContainerData freshData = new ContainerData(tier);
            containerCache.put(uuid, freshData);
            plugin.getPluginLogger().warning("Un conteneur (UUID: " + uuid.substring(0, 8) + ") avait des données invalides et a été réinitialisé.");
            saveDataToItem(item, freshData);
            return freshData;
        }
    }

    // === MÉTHODES PUBLIQUES (API) ===

    public void saveContainerItem(ItemStack containerItem, ContainerData data) {
        if (!isContainer(containerItem) || data == null) return;
        String uuid = getContainerUUID(containerItem);
        if (uuid == null) return;
        containerCache.put(uuid, data);
        saveDataToItem(containerItem, data);
    }

    public ContainerData getContainerData(ItemStack item) {
        return loadDataFromItem(item);
    }

    public boolean addItemToContainers(Player player, ItemStack itemToAdd) {
        if (itemToAdd == null || itemToAdd.getAmount() <= 0) return false;
        Set<String> playerUUIDs = playerContainers.get(player.getUniqueId());
        if (playerUUIDs == null || playerUUIDs.isEmpty()) return false;
        for (String uuid : playerUUIDs) {
            ContainerData data = containerCache.get(uuid);
            if (data != null && !data.isBroken() && !data.isFull()) {
                if (data.addItem(itemToAdd)) {
                    if (data.isFull()) {
                        updateContainerInInventory(player, uuid, data);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public boolean updateContainerInInventory(Player player, String uuid, ContainerData newData) {
        if (uuid == null || player == null || newData == null) return false;
        containerCache.put(uuid, newData);
        ItemStack item = findContainerByUUID(player, uuid);
        if (item != null) {
            return saveDataToItem(item, newData);
        }
        return false;
    }

    public List<ContainerData> getPlayerContainers(Player player) {
        List<ContainerData> containers = new ArrayList<>();
        Set<String> uuids = playerContainers.get(player.getUniqueId());
        if (uuids != null) {
            for (String uuid : uuids) {
                ContainerData data = containerCache.get(uuid);
                if (data != null) containers.add(data);
            }
        }
        return containers;
    }

    public long sellAllContainerContents(Player player, Map<Material, Integer> containerSoldItems) {
        long totalValue = 0;
        Set<String> uuids = playerContainers.get(player.getUniqueId());
        if (uuids == null) return 0L;
        List<String> brokenMessages = new ArrayList<>();

        for (String uuid : uuids) {
            ContainerData data = containerCache.get(uuid);
            if (data != null && data.isSellEnabled() && !data.isBroken()) {
                Map<ItemStack, Integer> vendableItems = data.getVendableContents(plugin.getConfigManager()::getSellPrice);
                long containerValue = 0;

                for (Map.Entry<ItemStack, Integer> entry : vendableItems.entrySet()) {
                    long price = plugin.getConfigManager().getSellPrice(entry.getKey().getType());
                    if (price > 0) {
                        containerValue += price * entry.getValue();
                        // AJOUT: Ajouter les items vendus aux détails
                        containerSoldItems.merge(entry.getKey().getType(), entry.getValue(), Integer::sum);
                    }
                }

                if (containerValue > 0) {
                    totalValue += containerValue;
                    data.clearVendableContents(plugin.getConfigManager()::getSellPrice);
                    if (!data.useDurability(1)) {
                        brokenMessages.add(getTierName(data.getTier()));
                    }
                    updateContainerInInventory(player, uuid, data);
                }
            }
        }

        if (!brokenMessages.isEmpty()) {
            player.sendMessage("§c💥 Un conteneur " + String.join("§c, ", brokenMessages) + " §cs'est cassé lors de la vente!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }
        return totalValue;
    }

    public int transferContainerToPlayer(Player player, ContainerData data) {
        if (player == null || data == null) return 0;
        int totalTransferred = 0;
        var contentsCopy = new LinkedHashMap<>(data.getContents());
        for (var entry : contentsCopy.entrySet()) {
            ItemStack itemKey = entry.getKey();
            int amountAvailable = entry.getValue();
            int amountToRemoveFromContainer = 0;
            while (amountAvailable > 0) {
                if (player.getInventory().firstEmpty() == -1) {
                    if (amountToRemoveFromContainer > 0) data.removeItem(itemKey, amountToRemoveFromContainer);
                    return totalTransferred;
                }
                int stackSize = Math.min(amountAvailable, itemKey.getType().getMaxStackSize());
                ItemStack toAdd = itemKey.clone();
                toAdd.setAmount(stackSize);
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(toAdd);
                int actuallyAdded = stackSize - leftover.values().stream().mapToInt(ItemStack::getAmount).sum();
                if (actuallyAdded > 0) {
                    totalTransferred += actuallyAdded;
                    amountToRemoveFromContainer += actuallyAdded;
                }
                amountAvailable -= stackSize;
                if (!leftover.isEmpty()) {
                    if (amountToRemoveFromContainer > 0) data.removeItem(itemKey, amountToRemoveFromContainer);
                    return totalTransferred;
                }
            }
            if (amountToRemoveFromContainer > 0) {
                data.removeItem(itemKey, amountToRemoveFromContainer);
            }
        }
        return totalTransferred;
    }

    // === UTILS, SÉRIALISATION ET CRÉATION ===

    public void rescanAndCachePlayerInventory(Player player) {
        if (player == null || !player.isOnline()) return;
        Set<String> currentUUIDs = playerContainers.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        Set<String> foundUUIDs = new HashSet<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                String uuid = getContainerUUID(item);
                if (uuid != null) {
                    foundUUIDs.add(uuid);
                    loadDataFromItem(item);
                }
            }
        }
        currentUUIDs.clear();
        currentUUIDs.addAll(foundUUIDs);
    }

    private void updateLoreAndName(ItemMeta meta, ContainerData data) {
        List<String> lore = new ArrayList<>();
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e📊 Informations du conteneur:");
        lore.add("§7┃ Tier: §6" + data.getTier() + " §7(" + getTierName(data.getTier()) + "§7)");
        lore.add("§7┃ Capacité: §a" + NumberFormatter.format(data.getMaxCapacity()) + " items");
        if (data.isBroken()) {
            lore.add("§7┃ État: §c💥 CASSÉ");
            meta.setDisplayName("§c💥 Conteneur Cassé - " + getTierName(data.getTier()));
        } else {
            lore.add("§7┃ Durabilité: §2" + data.getDurability() + "§7/§2" + data.getMaxDurability());
            meta.setDisplayName("§6📦 Conteneur " + getTierName(data.getTier()));
        }
        lore.add("");
        lore.add("§e📦 Contenu actuel:");
        if (data.getTotalItems() == 0) lore.add("§7┃ Vide");
        else {
            lore.add("§7┃ Items: §a" + NumberFormatter.format(data.getTotalItems()) + "§7/§a" + NumberFormatter.format(data.getMaxCapacity()));
            lore.add("§7┃ Remplissage: §d" + String.format("%.1f", data.getFillPercentage()) + "%");
        }
        lore.add("");
        lore.add("§e🎯 Filtres:");
        if (data.getWhitelist().isEmpty()) lore.add("§7┃ Accepte tous les items");
        else lore.add("§7┃ §a" + data.getWhitelist().size() + " matériaux filtrés");
        lore.add("");
        lore.add("§e💰 Vente automatique:");
        if (data.isBroken()) lore.add("§7┃ §8Indisponible");
        else lore.add("§7┃ " + (data.isSellEnabled() ? "§a✅ Activée" : "§c❌ Désactivée"));
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e⚙️ §aShift + Clic droit §7pour configurer");
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        meta.setLore(lore);
    }

    public ItemStack createContainer(int tier) {
        ItemStack container = new ItemStack(Material.CHEST);
        // Important: Obtenez la meta une seule fois.
        ItemMeta meta = container.getItemMeta();
        if (meta == null) return null;

        // Étape 1: Définir l'identité
        String uniqueId = UUID.randomUUID().toString();
        meta.getPersistentDataContainer().set(containerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(containerTierKey, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, uniqueId);

        // Appliquer la meta avec l'identité à l'item
        container.setItemMeta(meta);

        // Étape 2: Créer les données et les mettre en cache
        ContainerData data = new ContainerData(tier);
        containerCache.put(uniqueId, data);

        // Étape 3: Sauvegarder l'état initial complet (données + lore) sur l'item
        saveDataToItem(container, data);

        return container;
    }

    public boolean isContainer(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        // Le check crucial : l'item doit avoir la clé de base ET la clé UUID.
        return meta.getPersistentDataContainer().has(containerKey, PersistentDataType.BOOLEAN) && meta.getPersistentDataContainer().has(containerUUIDKey, PersistentDataType.STRING);
    }

    public String getContainerUUID(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(containerUUIDKey, PersistentDataType.STRING);
    }

    public ItemStack findContainerByUUID(Player player, String uuid) {
        if (uuid == null || player == null) return null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item) && uuid.equals(getContainerUUID(item))) return item;
        }
        return null;
    }

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

    private String serializeContainerData(ContainerData data) {
        try {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(bos)) {
                // Écriture de la version
                dataOutput.writeInt(DATA_VERSION);
                // Écriture du tier
                dataOutput.writeInt(data.getTier());
                // Écriture de la durabilité
                dataOutput.writeInt(data.getDurability());
                // Écriture du flag de vente
                dataOutput.writeBoolean(data.isSellEnabled());
                // Écriture de la whitelist
                Set<Material> whitelist = data.getWhitelist();
                dataOutput.writeInt(whitelist.size());
                for (Material material : whitelist) {
                    dataOutput.writeUTF(material.name());
                }
                // Écriture du contenu
                Map<ItemStack, Integer> contents = data.getContents();
                dataOutput.writeInt(contents.size());
                for (Map.Entry<ItemStack, Integer> entry : contents.entrySet()) {
                    ItemStack item = entry.getKey();
                    Integer amount = entry.getValue();
                    try {
                        dataOutput.writeObject(item);

                        dataOutput.writeInt(amount);
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }
                }
                // Écriture des items de référence
                Map<Integer, ItemStack> refItems = data.getReferenceItems();
                dataOutput.writeInt(refItems.size());
                for (Map.Entry<Integer, ItemStack> entry : refItems.entrySet()) {
                    Integer key = entry.getKey();
                    ItemStack item = entry.getValue();
                    try {
                        dataOutput.writeInt(key);

                        dataOutput.writeObject(item);
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw e;
                    }
                }
                // Finalisation
                dataOutput.flush();
                byte[] rawData = bos.toByteArray();
                return Base64.getEncoder().encodeToString(rawData);
            } catch (Exception streamException) {
                streamException.printStackTrace();
                throw streamException;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * DÉSÉRIALISATION AVEC DEBUG INTENSIF
     * Remplace la méthode deserializeContainerData existante
     */
    private ContainerData deserializeContainerData(String serializedData) {
        if (serializedData == null || serializedData.isEmpty()) {
            return null;
        }
        byte[] rawData;
        try {
            rawData = Base64.getDecoder().decode(serializedData);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        try (ByteArrayInputStream bis = new ByteArrayInputStream(rawData);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(bis)) {
            // Lecture de la version
            int version = dataInput.readInt();
            if (version != DATA_VERSION) {
                return null;
            }
            // Lecture du tier
            int tier = dataInput.readInt();
            // Lecture de la durabilité
            int durability = dataInput.readInt();
            // Lecture du flag de vente
            boolean sellEnabled = dataInput.readBoolean();
            // Lecture de la whitelist
            int whitelistSize = dataInput.readInt();

            if (whitelistSize < 0 || whitelistSize > 10000) {
                return null;
            }
            Set<Material> whitelist = new HashSet<>(whitelistSize);
            for (int i = 0; i < whitelistSize; i++) {
                try {
                    String materialName = dataInput.readUTF();

                    Material material = Material.valueOf(materialName);
                    whitelist.add(material);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
            // Lecture du contenu
            int contentsSize = dataInput.readInt();
            if (contentsSize < 0 || contentsSize > 100000) {
                return null;
            }

            Map<ItemStack, Integer> contents = new LinkedHashMap<>(contentsSize);
            for (int i = 0; i < contentsSize; i++) {
                try {
                    // Lecture de l'ItemStack
                    Object itemObj = dataInput.readObject();
                    ItemStack item = (ItemStack) itemObj;
                    // Lecture de la quantité
                    int amount = dataInput.readInt();
                    contents.put(item, amount);

                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
            // Création de l'objet avec les données lues jusqu'à présent
            ContainerData data = new ContainerData(tier, contents, whitelist, sellEnabled, durability);
            // Lecture des items de référence
            int refItemsSize = dataInput.readInt();
            if (refItemsSize < 0 || refItemsSize > 1000) {
                return null;
            }
            Map<Integer, ItemStack> refItems = new HashMap<>(refItemsSize);
            for (int i = 0; i < refItemsSize; i++) {
                try {
                    // Lecture de la clé
                    int key = dataInput.readInt();
                    // Lecture de l'ItemStack
                    Object itemObj = dataInput.readObject();
                    ItemStack item = (ItemStack) itemObj;
                    refItems.put(key, item);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw e;
                }
            }
            // Finalisation
            data.setReferenceItems(refItems);
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void handlePlayerJoin(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                containerCache.remove(getContainerUUID(item));
            }
        }
        rescanAndCachePlayerInventory(player);
    }

    public void handlePlayerQuit(Player player) {
        Set<String> uuids = playerContainers.get(player.getUniqueId());

        if (uuids != null && !uuids.isEmpty()) {
            for (String uuid : uuids) {
                ContainerData dataFromCache = containerCache.get(uuid);
                ItemStack itemInInventory = findContainerByUUID(player, uuid);
                if (dataFromCache != null && itemInInventory != null) {
                    saveDataToItem(itemInInventory, dataFromCache);
                }
            }
            for (String uuid : uuids) {
                containerCache.remove(uuid);
            }
        }
        playerContainers.remove(player.getUniqueId());
    }

    public void handleContainerDrop(Player player, ItemStack droppedItem) {
        String uuid = getContainerUUID(droppedItem);
        if (uuid == null) return;
        ContainerData data = containerCache.get(uuid);
        if (data != null) {
            saveDataToItem(droppedItem, data);
            playerContainers.getOrDefault(player.getUniqueId(), new HashSet<>()).remove(uuid);
            containerCache.remove(uuid);
        }
    }

    public void handleInventoryUpdate(Player player) {
        Set<String> uuids = playerContainers.get(player.getUniqueId());
        if (uuids != null) {
            for (String uuid : uuids) {
                ContainerData data = containerCache.get(uuid);
                ItemStack item = findContainerByUUID(player, uuid);
                if (data != null && item != null) {
                    saveDataToItem(item, data);
                }
            }
        }
    }

}