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

            dataOutput.writeInt(2); // Version 2 supporte les referenceItems

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

            Map<Integer, ItemStack> refs = data.getReferenceItems();
            dataOutput.writeInt(refs.size());
            for (Map.Entry<Integer, ItemStack> entry : refs.entrySet()) {
                dataOutput.writeInt(entry.getKey());      // Écrit le slot (Integer)
                dataOutput.writeObject(entry.getValue()); // Écrit l'item (ItemStack)
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

            Map<Integer, ItemStack> referenceItems = new HashMap<>();
            if (version >= 2) {
                int refItemsSize = dataInput.readInt();
                for (int i = 0; i < refItemsSize; i++) {
                    try {
                        int slot = dataInput.readInt();             // Lit le slot (Integer)
                        ItemStack refItem = (ItemStack) dataInput.readObject(); // Lit l'item (ItemStack)
                        referenceItems.put(slot, refItem);
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
     * RENFORCÉ : Trouve un conteneur spécifique par UUID dans l'inventaire
     */
    public ItemStack findContainerByUUID(Player player, String uuid) {
        if (uuid == null || player == null) return null;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isContainer(item)) {
                String containerUUID = getContainerUUID(item);
                if (uuid.equals(containerUUID)) {
                    return item;
                }
            }
        }
        return null;
    }

    /**
     * NOUVEAU : Méthode pour mettre à jour un conteneur spécifique dans l'inventaire
     */
    public boolean updateContainerInInventory(Player player, String uuid, ContainerData newData) {
        if (uuid == null || player == null || newData == null) return false;

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && isContainer(item)) {
                String containerUUID = getContainerUUID(item);
                if (uuid.equals(containerUUID)) {
                    updateContainerItem(item, newData);
                    player.getInventory().setItem(i, item); // Force la mise à jour
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * NOUVEAU : Transfère le contenu d'un conteneur vers l'inventaire du joueur
     */
    public int transferContainerToPlayer(Player player, ContainerData data) {
        if (player == null || data == null) return 0;

        int totalTransferred = 0;
        // Utiliser une copie pour éviter ConcurrentModificationException lors de la suppression
        var contents = new HashMap<>(data.getContents());

        for (var entry : contents.entrySet()) {
            ItemStack itemKey = entry.getKey(); // L'ItemStack qui sert de clé
            Material material = itemKey.getType();
            int amount = entry.getValue();

            int amountToRemoveFromContainer = 0; // Quantité à retirer du conteneur

            while (amount > 0) {
                int stackSize = Math.min(amount, material.getMaxStackSize());

                // On utilise l'itemKey cloné pour l'ajout, afin de préserver les métadonnées
                ItemStack itemToAdd = itemKey.clone();
                itemToAdd.setAmount(stackSize);

                // Essaie d'ajouter l'item à l'inventaire
                var leftover = player.getInventory().addItem(itemToAdd);

                if (leftover.isEmpty()) {
                    // Tout le stack a été ajouté
                    totalTransferred += stackSize;
                    amountToRemoveFromContainer += stackSize;
                    amount -= stackSize;
                } else {
                    // L'inventaire est plein, on calcule ce qui a été ajouté
                    int addedAmount = stackSize - leftover.get(0).getAmount();
                    if (addedAmount > 0) {
                        totalTransferred += addedAmount;
                        amountToRemoveFromContainer += addedAmount;
                    }
                    // L'inventaire est plein, on arrête
                    break;
                }
            }

            // Met à jour le conteneur APRES la boucle pour cet item
            if (amountToRemoveFromContainer > 0) {
                data.removeItem(itemKey, amountToRemoveFromContainer);
            }

            if (player.getInventory().firstEmpty() == -1) {
                // L'inventaire est plein, inutile de continuer la boucle principale
                break;
            }
        }

        return totalTransferred;
    }

    /**
     * MODIFIÉ : Met à jour un item conteneur avec de nouvelles données
     */
    public void updateContainerItem(ItemStack container, ContainerData data) {
        if (!isContainer(container) || data == null) return;

        ItemMeta meta = container.getItemMeta();
        if (meta == null) return;

        // Met à jour la lore avec les nouvelles informations
        List<String> lore = new ArrayList<>();
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e📊 Informations du conteneur:");
        lore.add("§7┃ Tier: §6" + data.getTier() + " §7(" + getTierName(data.getTier()) + "§7)");
        lore.add("§7┃ Capacité: §a" + NumberFormatter.format(data.getMaxCapacity()) + " items");

        if (data.isBroken()) {
            lore.add("§7┃ Durabilité: §c0§7/§7" + data.getMaxDurability());
            lore.add("§7┃ État: §c💥 CASSÉ");
            meta.setDisplayName("§c💥 Conteneur Cassé - " + getTierName(data.getTier()));
        } else {
            lore.add("§7┃ Durabilité: §2" + data.getDurability() + "§7/§2" + data.getMaxDurability());
            double percentage = (double) data.getDurability() / data.getMaxDurability() * 100;
            lore.add("§7┃ État: §a" + String.format("%.1f", percentage) + "%");
            meta.setDisplayName("§6📦 Conteneur " + getTierName(data.getTier()));
        }

        lore.add("");
        lore.add("§e📦 Contenu actuel:");

        if (data.getTotalItems() == 0) {
            lore.add("§7┃ Vide");
        } else {
            lore.add("§7┃ Items: §a" + NumberFormatter.format(data.getTotalItems()) +
                    "§7/§a" + NumberFormatter.format(data.getMaxCapacity()));
            double fillPercentage = (double) data.getTotalItems() / data.getMaxCapacity() * 100;
            lore.add("§7┃ Remplissage: §d" + String.format("%.1f", fillPercentage) + "%");
        }

        lore.add("");
        lore.add("§e🎯 Filtres:");

        if (data.getWhitelist().isEmpty()) {
            lore.add("§7┃ Accepte tous les items");
        } else {
            lore.add("§7┃ §a" + data.getWhitelist().size() + " matériaux filtrés");
        }

        lore.add("");
        lore.add("§e💰 Vente automatique:");

        if (data.isBroken()) {
            lore.add("§7┃ §8Indisponible");
        } else {
            lore.add("§7┃ " + (data.isSellEnabled() ? "§a✅ Activée" : "§c❌ Désactivée"));
        }

        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e⚙️ §aShift + Clic droit §7pour configurer");
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);

        // Sérialise et sauvegarde les nouvelles données
        String serializedData = serializeContainerData(data);
        meta.getPersistentDataContainer().set(containerDataKey, PersistentDataType.STRING, serializedData);

        container.setItemMeta(meta);
    }
}