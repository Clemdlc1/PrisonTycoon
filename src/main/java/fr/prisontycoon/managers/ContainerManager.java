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
 * Mise à jour visuelle SEULEMENT si absolument nécessaire
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

    // Tracking des conteneurs PLEINS uniquement (nécessitent mise à jour immédiate)
    private final Set<String> fullContainers = ConcurrentHashMap.newKeySet();

    // Mapping Player -> Set<UUID> pour optimisation
    private final Map<UUID, Set<String>> playerContainers = new ConcurrentHashMap<>();

    // Timestamp de la dernière mise à jour forcée par conteneur
    private final Map<String, Long> lastForcedUpdate = new ConcurrentHashMap<>();

    // Configuration ultra-conservative
    private static final long FORCED_UPDATE_INTERVAL = 30_000; // 30 secondes (très long)
    private static final long CACHE_CLEANUP_INTERVAL = 120_000; // 2 minutes

    public ContainerManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.containerKey = new NamespacedKey(plugin, "container");
        this.containerTierKey = new NamespacedKey(plugin, "container_tier");
        this.containerDataKey = new NamespacedKey(plugin, "container_data");
        this.containerUUIDKey = new NamespacedKey(plugin, "container_uuid");

        // Enregistre les listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Démarre SEULEMENT la tâche de nettoyage (pas de mise à jour forcée)
        startMinimalMaintenanceTasks();
    }

    /**
     * ULTRA-OPTIMISÉ : Récupère les données SEULEMENT depuis le cache
     */
    public ContainerData getContainerData(ItemStack item) {
        if (!isContainer(item)) return null;

        String uuid = getContainerUUID(item);
        if (uuid == null) return null;

        // Vérifie le cache AVANT tout
        ContainerData cached = containerCache.get(uuid);
        if (cached != null) {
            return cached; // Retourne DIRECTEMENT le cache (aucune sérialisation)
        }

        // SEULEMENT si pas en cache, désérialise UNE SEULE FOIS
        ItemMeta meta = item.getItemMeta();
        String serializedData = meta.getPersistentDataContainer().get(containerDataKey, PersistentDataType.STRING);

        ContainerData data;
        if (serializedData == null) {
            int tier = meta.getPersistentDataContainer().getOrDefault(containerTierKey, PersistentDataType.INTEGER, 1);
            data = new ContainerData(tier);
        } else {
            data = deserializeContainerData(serializedData);
        }

        if (data != null) {
            // Ajoute au cache IMMÉDIATEMENT
            containerCache.put(uuid, data);

            // Track pour le joueur
            Player player = findPlayerWithContainer(uuid);
            if (player != null) {
                playerContainers.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet()).add(uuid);
            }
        }

        return data;
    }

    /**
     * ULTRA-OPTIMISÉ : Ajoute item SANS mise à jour visuelle (sauf si plein)
     */
    public boolean addItemToContainers(Player player, ItemStack itemToAdd) {
        if (itemToAdd == null || itemToAdd.getAmount() <= 0) return false;

        Set<String> playerUUIDs = playerContainers.getOrDefault(player.getUniqueId(), Collections.emptySet());

        // Optimisation : vérifie SEULEMENT les conteneurs en cache
        for (String uuid : playerUUIDs) {
            ContainerData data = containerCache.get(uuid);
            if (data != null && !data.isBroken()) {

                // Calcule si sera plein AVANT d'ajouter
                boolean willBeFull = (data.getTotalItems() + itemToAdd.getAmount()) >= data.getMaxCapacity();

                if (data.addItem(itemToAdd)) {
                    // PAS de mise à jour visuelle par défaut !

                    // SEULEMENT si le conteneur devient plein
                    if (willBeFull) {
                        fullContainers.add(uuid);
                        updateContainerItemImmediate(player, uuid, data);
                        plugin.getPluginLogger().debug("Conteneur " + uuid + " plein - mise à jour forcée");
                    }
                    // SINON : AUCUNE mise à jour ! (données restent en cache uniquement)

                    return true;
                }
            }
        }

        // Fallback SEULEMENT si aucun conteneur en cache trouvé
        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                ContainerData data = getContainerData(item);
                if (data != null && !data.isBroken()) {

                    boolean willBeFull = (data.getTotalItems() + itemToAdd.getAmount()) >= data.getMaxCapacity();

                    if (data.addItem(itemToAdd)) {
                        String uuid = getContainerUUID(item);

                        if (willBeFull) {
                            fullContainers.add(uuid);
                            updateContainerItemImmediate(player, uuid, data);
                        }

                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * NOUVEAU : Met à jour IMMÉDIATEMENT un conteneur spécifique
     */
    private void updateContainerItemImmediate(Player player, String uuid, ContainerData data) {
        ItemStack containerItem = findContainerByUUID(player, uuid);
        if (containerItem != null) {
            updateContainerVisual(containerItem, data);
            plugin.getPluginLogger().debug("Mise à jour immédiate conteneur: " + uuid);
        }
    }

    /**
     * SIMPLIFIÉ : Met à jour seulement l'affichage visuel (pas de cache)
     */
    public void updateContainerItem(ItemStack container, ContainerData data) {
        if (!isContainer(container) || data == null) return;

        String uuid = getContainerUUID(container);
        if (uuid == null) return;

        // Met à jour le cache EN PREMIER
        containerCache.put(uuid, data);

        // Met à jour l'affichage visuel IMMÉDIATEMENT
        updateContainerVisual(container, data);

        // Marque comme mis à jour
        lastForcedUpdate.put(uuid, System.currentTimeMillis());
    }

    /**
     * OPTIMISÉ : Met à jour SEULEMENT l'affichage visuel (lore + sérialisation)
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

        // Sérialise SEULEMENT si mise à jour visuelle
        String serializedData = serializeContainerData(data);
        meta.getPersistentDataContainer().set(containerDataKey, PersistentDataType.STRING, serializedData);

        container.setItemMeta(meta);
    }

    /**
     * SIMPLIFIÉ : Tâches de maintenance minimales
     */
    private void startMinimalMaintenanceTasks() {
        // SEULEMENT nettoyage du cache (pas de mise à jour forcée)
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupCache();
            }
        }.runTaskTimer(plugin, CACHE_CLEANUP_INTERVAL / 50, CACHE_CLEANUP_INTERVAL / 50);

        // Tâche de mise à jour forcée TRÈS RARE (30 secondes)
        new BukkitRunnable() {
            @Override
            public void run() {
                performRareUpdates();
            }
        }.runTaskTimer(plugin, FORCED_UPDATE_INTERVAL / 50, FORCED_UPDATE_INTERVAL / 50);
    }

    /**
     * NOUVEAU : Mises à jour rares (toutes les 30 secondes)
     */
    private void performRareUpdates() {
        long now = System.currentTimeMillis();
        int updatedCount = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<String> playerUUIDs = playerContainers.get(player.getUniqueId());
            if (playerUUIDs != null) {
                for (String uuid : playerUUIDs) {
                    ContainerData data = containerCache.get(uuid);
                    Long lastUpdate = lastForcedUpdate.get(uuid);

                    // Met à jour SEULEMENT si jamais mis à jour OU très ancien
                    if (data != null && (lastUpdate == null || (now - lastUpdate) >= FORCED_UPDATE_INTERVAL)) {
                        updateContainerItemImmediate(player, uuid, data);
                        lastForcedUpdate.put(uuid, now);
                        updatedCount++;
                    }
                }
            }
        }

        if (updatedCount > 0) {
            plugin.getPluginLogger().debug("Mise à jour forcée rare: " + updatedCount + " conteneurs");
        }
    }

    /**
     * OPTIMISÉ : Nettoie le cache des conteneurs non utilisés
     */
    private void cleanupCache() {
        Set<String> activeContainers = new HashSet<>();

        // Collecte tous les conteneurs actifs
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (isContainer(item)) {
                    String uuid = getContainerUUID(item);
                    if (uuid != null) {
                        activeContainers.add(uuid);
                    }
                }
            }
        }

        // Supprime les conteneurs inactifs
        containerCache.keySet().retainAll(activeContainers);
        fullContainers.retainAll(activeContainers);
        lastForcedUpdate.keySet().retainAll(activeContainers);

        // Met à jour le mapping joueur -> conteneurs
        playerContainers.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Set<String> playerUUIDs = new HashSet<>();
            for (ItemStack item : player.getInventory().getContents()) {
                if (isContainer(item)) {
                    String uuid = getContainerUUID(item);
                    if (uuid != null) {
                        playerUUIDs.add(uuid);
                    }
                }
            }
            if (!playerUUIDs.isEmpty()) {
                playerContainers.put(player.getUniqueId(), playerUUIDs);
            }
        }

        plugin.getPluginLogger().debug("Cache nettoyé: " + containerCache.size() + " conteneurs actifs");
    }

    /**
     * EVENT : Force la mise à jour SEULEMENT quand joueur ouvre inventaire
     */
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // Met à jour TOUS les conteneurs du joueur à l'ouverture d'inventaire
            Set<String> playerUUIDs = playerContainers.get(player.getUniqueId());
            if (playerUUIDs != null) {
                int updatedCount = 0;
                for (String uuid : playerUUIDs) {
                    ContainerData data = containerCache.get(uuid);
                    if (data != null) {
                        updateContainerItemImmediate(player, uuid, data);
                        lastForcedUpdate.put(uuid, System.currentTimeMillis());
                        updatedCount++;
                    }
                }
                plugin.getPluginLogger().debug("Inventaire ouvert - mise à jour de " + updatedCount + " conteneurs");
            }
        }
    }

    /**
     * EVENT : Nettoie le cache quand un joueur se déconnecte
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Set<String> playerUUIDs = playerContainers.remove(player.getUniqueId());

        if (playerUUIDs != null) {
            for (String uuid : playerUUIDs) {
                boolean stillUsed = false;
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    if (!onlinePlayer.equals(player) && findContainerByUUID(onlinePlayer, uuid) != null) {
                        stillUsed = true;
                        break;
                    }
                }

                if (!stillUsed) {
                    containerCache.remove(uuid);
                    fullContainers.remove(uuid);
                    lastForcedUpdate.remove(uuid);
                }
            }
        }
    }

    /**
     * NOUVEAU : Trouve le joueur qui possède un conteneur
     */
    private Player findPlayerWithContainer(String uuid) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (findContainerByUUID(player, uuid) != null) {
                return player;
            }
        }
        return null;
    }

    // === MÉTHODES UTILITAIRES CONSERVÉES ===

    /**
     * Crée un nouveau conteneur
     */
    public ItemStack createContainer(int tier) {
        ItemStack container = new ItemStack(Material.CHEST);
        ItemMeta meta = container.getItemMeta();

        if (meta == null) return null;

        String uniqueId = UUID.randomUUID().toString();
        ContainerData data = new ContainerData(tier);

        meta.setDisplayName("§6📦 Conteneur " + getTierName(tier));

        List<String> lore = new ArrayList<>();
        lore.add("§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e🚀 §aNouveau conteneur automatique!");
        lore.add("§7Les blocs minés vont directement dedans");
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

        meta.getPersistentDataContainer().set(containerKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(containerTierKey, PersistentDataType.INTEGER, tier);
        meta.getPersistentDataContainer().set(containerUUIDKey, PersistentDataType.STRING, uniqueId);

        String serializedData = serializeContainerData(data);
        meta.getPersistentDataContainer().set(containerDataKey, PersistentDataType.STRING, serializedData);

        container.setItemMeta(meta);

        // Ajoute au cache
        containerCache.put(uniqueId, data);

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
     * Obtient l'UUID unique d'un conteneur
     */
    public String getContainerUUID(ItemStack item) {
        if (!isContainer(item)) return null;

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(containerUUIDKey, PersistentDataType.STRING);
    }

    /**
     * Trouve un conteneur spécifique par UUID dans l'inventaire
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
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(bos);

            dataOutput.writeInt(2);
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

            dataOutput.close();
            return Base64.getEncoder().encodeToString(bos.toByteArray());

        } catch (IOException e) {
            plugin.getPluginLogger().warning("Erreur sérialisation conteneur: " + e.getMessage());
            return "";
        }
    }

    private ContainerData deserializeContainerData(String serializedData) {
        try {
            byte[] data = Base64.getDecoder().decode(serializedData);
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(bis);

            int version = dataInput.readInt();
            int tier = dataInput.readInt();
            int durability = dataInput.readInt();
            boolean sellEnabled = dataInput.readBoolean();

            Set<Material> whitelist = new HashSet<>();
            int whitelistSize = dataInput.readInt();
            for (int i = 0; i < whitelistSize; i++) {
                try {
                    whitelist.add(Material.valueOf(dataInput.readUTF()));
                } catch (IllegalArgumentException ignored) {
                }
            }

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
                        int slot = dataInput.readInt();
                        ItemStack refItem = (ItemStack) dataInput.readObject();
                        referenceItems.put(slot, refItem);
                    } catch (Exception e) {
                        plugin.getPluginLogger().warning("Erreur lecture item référence: " + e.getMessage());
                    }
                }
            }

            dataInput.close();

            ContainerData containerData = new ContainerData(tier, contents, whitelist, sellEnabled, durability);
            containerData.setReferenceItems(referenceItems);

            return containerData;

        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur désérialisation conteneur: " + e.getMessage());
            return null;
        }
    }

    // === MÉTHODES POUR LA COMPATIBILITÉ ===

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

        for (ItemStack item : player.getInventory().getContents()) {
            if (isContainer(item)) {
                ContainerData data = getContainerData(item);
                if (data != null && data.isSellEnabled() && !data.isBroken()) {

                    Map<ItemStack, Integer> vendableItems = data.getVendableContents(
                            material -> plugin.getConfigManager().getSellPrice(material)
                    );

                    long containerValue = 0;
                    for (Map.Entry<ItemStack, Integer> entry : vendableItems.entrySet()) {
                        long price = plugin.getConfigManager().getSellPrice(entry.getKey().getType());
                        containerValue += price * entry.getValue();
                        totalItems += entry.getValue();
                    }

                    if (containerValue > 0) {
                        totalValue += containerValue;

                        data.clearVendableContents(material -> plugin.getConfigManager().getSellPrice(material));

                        if (!data.useDurability(1)) {
                            brokenContainers.add(data);
                        }

                        // Force la mise à jour après vente
                        String uuid = getContainerUUID(item);
                        if (uuid != null) {
                            containerCache.put(uuid, data);
                            updateContainerItemImmediate(player, uuid, data);
                        }
                    }
                }
            }
        }

        if (!brokenContainers.isEmpty()) {
            player.sendMessage("§c💥 " + brokenContainers.size() + " conteneur(s) cassé(s) lors de la vente!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        }

        if (totalItems > 0) {
            player.sendMessage("§a✅ Contenu des conteneurs vendu: §e" + NumberFormatter.format(totalItems) + " items §7pour §6" + NumberFormatter.format(totalValue) + " coins");
        }

        return totalValue;
    }

    public int transferContainerToPlayer(Player player, ContainerData data) {
        if (player == null || data == null) return 0;

        int totalTransferred = 0;
        var contents = new HashMap<>(data.getContents());

        for (var entry : contents.entrySet()) {
            ItemStack itemKey = entry.getKey();
            Material material = itemKey.getType();
            int amount = entry.getValue();
            int amountToRemoveFromContainer = 0;

            while (amount > 0) {
                int stackSize = Math.min(amount, material.getMaxStackSize());
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

            if (player.getInventory().firstEmpty() == -1) {
                break;
            }
        }

        return totalTransferred;
    }
}