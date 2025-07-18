package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.ContainerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ULTRA-OPTIMISÉ : Gestionnaire des conteneurs avec cache ultra-agressif
 * Mise à jour visuelle SEULEMENT si absolument nécessaire pour des performances maximales.
 */
public class ContainerManager implements Listener {

    private final PrisonTycoon plugin;

    // Clés pour les données persistantes
    private final NamespacedKey containerKey;
    private final NamespacedKey containerTierKey;
    private final NamespacedKey containerDataKey;
    private final NamespacedKey containerUUIDKey;

    // === CACHE ULTRA-AGRESSIF ===

    // Cache principal : UUID -> ContainerData (données en mémoire SEULEMENT)
    private final Map<String, ContainerData> containerCache = new ConcurrentHashMap<>();

    // Mapping Player -> Set<UUID> pour optimisation (la clé de la performance)
    private final Map<UUID, Set<String>> playerContainers = new ConcurrentHashMap<>();

    // Timestamp de la dernière mise à jour visuelle par conteneur
    private final Map<String, Long> lastVisualUpdate = new ConcurrentHashMap<>();

    // Configuration des tâches de maintenance
    private static final long VISUAL_UPDATE_INTERVAL = 30_000; // 30 secondes
    private static final long CACHE_CLEANUP_INTERVAL = 5 * 60 * 20; // 5 minutes (en ticks)

    public ContainerManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.containerKey = new NamespacedKey(plugin, "container");
        this.containerTierKey = new NamespacedKey(plugin, "container_tier");
        this.containerDataKey = new NamespacedKey(plugin, "container_data");
        this.containerUUIDKey = new NamespacedKey(plugin, "container_uuid");

        Bukkit.getPluginManager().registerEvents(this, plugin);
        startMaintenanceTasks();
    }

    /**
     * ULTRA-OPTIMISÉ : Récupère les données d'un conteneur, en privilégiant le cache.
     * Ne lit les données de l'item que si celui-ci n'est pas déjà dans le cache.
     */
    public ContainerData getContainerData(ItemStack item) {
        if (!isContainer(item)) return null;

        String uuid = getContainerUUID(item);
        if (uuid == null) return null;

        // Étape 1 : Vérifie le cache (accès quasi-instantané)
        ContainerData cachedData = containerCache.get(uuid);
        if (cachedData != null) {
            return cachedData;
        }

        // Étape 2 (Cache Miss) : Lit les données de l'item, le met en cache, puis le retourne.
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String serializedData = meta.getPersistentDataContainer().get(containerDataKey, PersistentDataType.STRING);
        ContainerData data;
        if (serializedData == null) {
            int tier = meta.getPersistentDataContainer().getOrDefault(containerTierKey, PersistentDataType.INTEGER, 1);
            data = new ContainerData(tier);
        } else {
            data = deserializeContainerData(serializedData);
        }

        if (data != null) {
            containerCache.put(uuid, data); // Ajout au cache pour les futurs appels
        }

        return data;
    }

    /**
     * ULTRA-OPTIMISÉ : Ajoute un item aux conteneurs d'un joueur.
     * Utilise le cache 'playerContainers' pour une identification immédiate des conteneurs.
     * Aucune mise à jour visuelle n'est effectuée sauf si le conteneur devient plein.
     */
    public boolean addItemToContainers(Player player, ItemStack itemToAdd) {
        if (itemToAdd == null || itemToAdd.getAmount() <= 0) return false;

        // Utilise la map trackée pour trouver les conteneurs du joueur instantanément.
        Set<String> playerUUIDs = playerContainers.get(player.getUniqueId());
        if (playerUUIDs == null || playerUUIDs.isEmpty()) {
            return false; // Le joueur n'a aucun conteneur connu, on ne fait rien.
        }

        for (String uuid : playerUUIDs) {
            ContainerData data = containerCache.get(uuid); // Accès direct au cache
            if (data != null && !data.isBroken() && !data.isFull()) {

                if (data.addItem(itemToAdd)) {
                    // L'item a été ajouté aux données en mémoire (cache).
                    // AUCUNE mise à jour visuelle ici pour maximiser les performances.

                    // CAS EXCEPTIONNEL : Le conteneur est maintenant plein, on force une mise à jour visuelle.
                    if (data.isFull()) {
                        updateContainerItemImmediate(player, uuid, data);
                    }
                    return true;
                }
            }
        }

        return false; // Aucun conteneur n'a pu accepter l'item.
    }

    /**
     * Met à jour IMMÉDIATEMENT un conteneur spécifique dans l'inventaire d'un joueur.
     * Trouve l'item par UUID et applique la mise à jour visuelle.
     */
    private void updateContainerItemImmediate(Player player, String uuid, ContainerData data) {
        if (player == null || uuid == null || data == null) return;

        ItemStack containerItem = findContainerByUUID(player, uuid);
        if (containerItem != null) {
            updateContainerVisual(containerItem, data);
            lastVisualUpdate.put(uuid, System.currentTimeMillis());
            // plugin.getPluginLogger().debug("Mise à jour immédiate conteneur: " + uuid.substring(0, 8));
        }
    }

    /**
     * Met à jour l'affichage visuel (lore) et les données persistantes d'un item.
     * C'est une opération coûteuse qui doit être appelée le moins possible.
     */
    private void updateContainerVisual(ItemStack container, ContainerData data) {
        ItemMeta meta = container.getItemMeta();
        if (meta == null) return;

        // Met à jour la lore
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
            double percentage = data.getDurabilityPercentage();
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
            double fillPercentage = data.getFillPercentage();
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

        // Sérialise les données à chaque mise à jour visuelle pour la persistance
        String serializedData = serializeContainerData(data);
        meta.getPersistentDataContainer().set(containerDataKey, PersistentDataType.STRING, serializedData);

        container.setItemMeta(meta);
    }

    /**
     * Tâches de maintenance pour la santé du cache.
     */
    private void startMaintenanceTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Tâche de mise à jour visuelle périodique pour tous les joueurs en ligne
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    Set<String> uuids = playerContainers.get(player.getUniqueId());
                    if (uuids != null) {
                        for (String uuid : uuids) {
                            // Si la dernière mise à jour est trop ancienne, on force la synchro
                            if (now - lastVisualUpdate.getOrDefault(uuid, 0L) > VISUAL_UPDATE_INTERVAL) {
                                ContainerData data = containerCache.get(uuid);
                                if (data != null) {
                                    updateContainerItemImmediate(player, uuid, data);
                                }
                            }
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 200L, VISUAL_UPDATE_INTERVAL / 50); // Toutes les 30 sec

        new BukkitRunnable() {
            @Override
            public void run() {
                // Nettoie le cache des conteneurs qui ne sont plus dans l'inventaire de personne
                Set<String> activeContainers = new HashSet<>();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (isContainer(item)) {
                            activeContainers.add(getContainerUUID(item));
                        }
                    }
                }
                containerCache.keySet().retainAll(activeContainers);
                lastVisualUpdate.keySet().retainAll(activeContainers);
            }
        }.runTaskTimerAsynchronously(plugin, CACHE_CLEANUP_INTERVAL, CACHE_CLEANUP_INTERVAL);
    }

    /**
     * Scanne l'inventaire d'un joueur pour peupler/mettre à jour les caches.
     */
    private void scanAndCachePlayerInventory(Player player) {
        Set<String> uuids = playerContainers.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        uuids.clear(); // On reconstruit la liste

        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                String uuid = getContainerUUID(item);
                if (uuid != null) {
                    uuids.add(uuid);
                    // On s'assure que le conteneur est dans le cache (le charge si besoin)
                    getContainerData(item);
                }
            }
        }
        // plugin.getPluginLogger().debug("Scan terminé pour " + player.getName() + ": " + uuids.size() + " conteneurs.");
    }

    // === GESTION DES ÉVÉNEMENTS POUR LE CACHE ===

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Scanne l'inventaire du joueur à la connexion pour initialiser le cache
        scanAndCachePlayerInventory(event.getPlayer());
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // Un joueur qui ouvre son inventaire est un excellent moment pour tout synchroniser.
            // 1. Re-scanne l'inventaire au cas où il aurait reçu un conteneur
            scanAndCachePlayerInventory(player);
            // 2. Met à jour l'affichage de tous ses conteneurs
            Set<String> playerUUIDs = playerContainers.get(player.getUniqueId());
            if (playerUUIDs != null) {
                for (String uuid : playerUUIDs) {
                    ContainerData data = containerCache.get(uuid);
                    if (data != null) {
                        updateContainerItemImmediate(player, uuid, data);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nettoie les données du joueur qui se déconnecte pour libérer la mémoire
        playerContainers.remove(event.getPlayer().getUniqueId());
    }

    // === MÉTHODES UTILITAIRES ET DE COMPATIBILITÉ ===

    public void updateContainerItem(ItemStack container, ContainerData data) {
        if (!isContainer(container) || data == null) return;
        String uuid = getContainerUUID(container);
        if (uuid == null) return;

        containerCache.put(uuid, data);
        updateContainerVisual(container, data);
        lastVisualUpdate.put(uuid, System.currentTimeMillis());
    }

    public boolean isContainer(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(containerKey, PersistentDataType.BOOLEAN);
    }

    public String getContainerUUID(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(containerUUIDKey, PersistentDataType.STRING);
    }

    public ItemStack findContainerByUUID(Player player, String uuid) {
        if (uuid == null || player == null) return null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item) && uuid.equals(getContainerUUID(item))) {
                return item;
            }
        }
        return null;
    }

    public List<ContainerData> getPlayerContainers(Player player) {
        List<ContainerData> containers = new ArrayList<>();
        Set<String> uuids = playerContainers.get(player.getUniqueId());
        if (uuids != null) {
            for(String uuid : uuids) {
                ContainerData data = containerCache.get(uuid);
                if(data != null) {
                    containers.add(data);
                }
            }
        }
        return containers;
    }

    public ItemStack createContainer(int tier) {
        ItemStack container = new ItemStack(Material.CHEST);
        ItemMeta meta = container.getItemMeta();
        if (meta == null) return null;

        String uniqueId = UUID.randomUUID().toString();
        ContainerData data = new ContainerData(tier);

        // ... (le reste de la méthode de création est principalement visuel et reste inchangé)
        meta.setDisplayName("§6📦 Conteneur " + getTierName(tier));
        List<String> lore = new ArrayList<>();
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e🚀 §aNouveau conteneur automatique!");
        lore.add("§7Les blocs minés vont directement dedans");
        lore.add("");
        lore.add("§e📈 Avantages du Tier " + tier + ":");
        switch (tier) {
            case 1 -> lore.add("§7┃ Capacité de base, durabilité standard");
            case 2 -> lore.add("§7┃ Capacité doublée, durabilité améliorée");
            case 3 -> lore.add("§7┃ Grande capacité, bonne durabilité");
            case 4 -> lore.add("§7┃ Très grande capacité, haute durabilité");
            case 5 -> lore.add("§7┃ Capacité maximale, durabilité maximale");
        }
        lore.add("");
        lore.add("§c⚠️ §7Se dégrade à chaque vente avec §c/sell all");
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(containerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(containerTierKey, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, uniqueId);
        String serializedData = serializeContainerData(data);
        meta.getPersistentDataContainer().set(containerDataKey, PersistentDataType.STRING, serializedData);
        container.setItemMeta(meta);

        // Ajoute directement au cache
        containerCache.put(uniqueId, data);
        return container;
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
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(bos)) {

            dataOutput.writeInt(2); // Version
            dataOutput.writeInt(data.getTier());
            dataOutput.writeInt(data.getDurability());
            dataOutput.writeBoolean(data.isSellEnabled());

            dataOutput.writeInt(data.getWhitelist().size());
            for (Material material : data.getWhitelist()) {
                dataOutput.writeUTF(material.name());
            }

            dataOutput.writeInt(data.getContents().size());
            for (Map.Entry<ItemStack, Integer> entry : data.getContents().entrySet()) {
                dataOutput.writeObject(entry.getKey());
                dataOutput.writeInt(entry.getValue());
            }

            Map<Integer, ItemStack> refs = data.getReferenceItems();
            dataOutput.writeInt(refs.size());
            for (Map.Entry<Integer, ItemStack> entry : refs.entrySet()) {
                dataOutput.writeInt(entry.getKey());
                dataOutput.writeObject(entry.getValue());
            }

            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            plugin.getPluginLogger().warning("Erreur sérialisation conteneur: " + e.getMessage());
            return "";
        }
    }

    private ContainerData deserializeContainerData(String serializedData) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(Base64.getDecoder().decode(serializedData));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(bis)) {

            int version = dataInput.readInt();
            int tier = dataInput.readInt();
            int durability = dataInput.readInt();
            boolean sellEnabled = dataInput.readBoolean();

            Set<Material> whitelist = new HashSet<>();
            int whitelistSize = dataInput.readInt();
            for (int i = 0; i < whitelistSize; i++) {
                try {
                    whitelist.add(Material.valueOf(dataInput.readUTF()));
                } catch (IllegalArgumentException ignored) {}
            }

            Map<ItemStack, Integer> contents = new LinkedHashMap<>();
            int contentsSize = dataInput.readInt();
            for (int i = 0; i < contentsSize; i++) {
                try {
                    contents.put((ItemStack) dataInput.readObject(), dataInput.readInt());
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Erreur lecture item conteneur: " + e.getMessage());
                }
            }

            Map<Integer, ItemStack> referenceItems = new HashMap<>();
            if (version >= 2) {
                int refItemsSize = dataInput.readInt();
                for (int i = 0; i < refItemsSize; i++) {
                    try {
                        referenceItems.put(dataInput.readInt(), (ItemStack) dataInput.readObject());
                    } catch (Exception e) {
                        plugin.getPluginLogger().warning("Erreur lecture item référence: " + e.getMessage());
                    }
                }
            }

            ContainerData containerData = new ContainerData(tier, contents, whitelist, sellEnabled, durability);
            containerData.setReferenceItems(referenceItems);
            return containerData;
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur désérialisation conteneur: " + e.getMessage());
            return null;
        }
    }

    public boolean updateContainerInInventory(Player player, String uuid, ContainerData newData) {
        if (uuid == null || player == null || newData == null) return false;
        containerCache.put(uuid, newData);
        updateContainerItemImmediate(player, uuid, newData);
        return true;
    }

    public long sellAllContainerContents(Player player) {
        long totalValue = 0;
        int totalItems = 0;
        List<ContainerData> brokenContainers = new ArrayList<>();

        Set<String> uuids = playerContainers.get(player.getUniqueId());
        if(uuids == null) return 0;

        for (String uuid : uuids) {
            ContainerData data = containerCache.get(uuid);
            if(data == null) continue;

            if (data.isSellEnabled() && !data.isBroken()) {
                Map<ItemStack, Integer> vendableItems = data.getVendableContents(plugin.getConfigManager()::getSellPrice);
                long containerValue = 0;
                for (Map.Entry<ItemStack, Integer> entry : vendableItems.entrySet()) {
                    long price = plugin.getConfigManager().getSellPrice(entry.getKey().getType());
                    containerValue += price * entry.getValue();
                    totalItems += entry.getValue();
                }

                if (containerValue > 0) {
                    totalValue += containerValue;
                    data.clearVendableContents(plugin.getConfigManager()::getSellPrice);
                    if (!data.useDurability(1)) {
                        brokenContainers.add(data);
                    }
                    updateContainerItemImmediate(player, uuid, data);
                }
            }
        }

        if (!brokenContainers.isEmpty()) {
            player.sendMessage("§c💥 " + brokenContainers.size() + " conteneur(s) cassé(s) lors de la vente!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }

        if (totalItems > 0) {
            player.sendMessage("§a✅ Contenu des conteneurs vendu: §e" + NumberFormatter.format(totalItems) + " items §7pour §6" + NumberFormatter.format(totalValue) + " coins (valeur brute)");
        }
        return totalValue;
    }

    public int transferContainerToPlayer(Player player, ContainerData data) {
        if (player == null || data == null) return 0;

        int totalTransferred = 0;
        var contents = new HashMap<>(data.getContents());

        for (var entry : contents.entrySet()) {
            ItemStack itemKey = entry.getKey();
            int amount = entry.getValue();
            int amountToRemoveFromContainer = 0;

            while (amount > 0) {
                int stackSize = Math.min(amount, itemKey.getType().getMaxStackSize());
                ItemStack itemToAdd = itemKey.clone();
                itemToAdd.setAmount(stackSize);

                var leftover = player.getInventory().addItem(itemToAdd);

                if (leftover.isEmpty()) {
                    totalTransferred += stackSize;
                    amountToRemoveFromContainer += stackSize;
                    amount -= stackSize;
                } else {
                    int addedAmount = stackSize - leftover.get(0).getAmount();
                    if (addedAmount > 0) {
                        totalTransferred += addedAmount;
                        amountToRemoveFromContainer += addedAmount;
                    }
                    break;
                }
            }
            if (amountToRemoveFromContainer > 0) {
                data.removeItem(itemKey, amountToRemoveFromContainer);
            }
            if (player.getInventory().firstEmpty() == -1) break;
        }
        return totalTransferred;
    }
}