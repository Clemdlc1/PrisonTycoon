package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.TankData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des Tanks - Barils automatiques pour l'achat d'items
 */
public class TankManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey tankKey;
    private final NamespacedKey tankIdKey;
    private final Map<String, TankData> tankCache = new ConcurrentHashMap<>();
    private final Map<Location, String> tankLocations = new ConcurrentHashMap<>(); // Position -> ID Tank
    private final Map<String, ArmorStand> tankNameTags = new ConcurrentHashMap<>(); // ID Tank -> ArmorStand
    private final File tanksFile;
    private YamlConfiguration tanksConfig;
    private int nextTankId = 1;

    public TankManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.tankKey = new NamespacedKey(plugin, "tank");
        this.tankIdKey = new NamespacedKey(plugin, "tank_id");
        this.tanksFile = new File(plugin.getDataFolder(), "tanks.yml");
        loadTanks();
    }

    /**
     * Vérifie si l'item est un Tank
     */
    public boolean isTank(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(tankKey, PersistentDataType.BOOLEAN) &&
                meta.getPersistentDataContainer().has(tankIdKey, PersistentDataType.STRING);
    }

    /**
     * Vérifie si un bloc à cette position est un Tank
     */
    public boolean isTankBlock(Location location) {
        return tankLocations.containsKey(location) &&
                location.getBlock().getType() == Material.BARREL;
    }

    /**
     * Récupère le Tank à une position donnée
     */
    public TankData getTankAt(Location location) {
        String tankId = tankLocations.get(location);
        return tankId != null ? tankCache.get(tankId) : null;
    }

    /**
     * Crée un nouveau Tank
     */
    public ItemStack createTank(Player owner) {
        String tankId = generateTankId();

        // Créer l'item Tank (Baril)
        ItemStack tank = new ItemStack(Material.BARREL);
        ItemMeta meta = tank.getItemMeta();

        // Données persistantes
        meta.getPersistentDataContainer().set(tankKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(tankIdKey, PersistentDataType.STRING, tankId);

        // Apparence
        meta.setDisplayName("§6⚡ Tank Automatique");
        List<String> lore = Arrays.asList(
                "§7Propriétaire: §e" + owner.getName(),
                "§7Capacité: §b" + NumberFormatter.format(TankData.MAX_CAPACITY) + " items",
                "§7Filtres: §cAucun",
                "§7Prix: §cNon configuré",
                "",
                "§7▸ Placer au sol pour activer",
                "§7▸ Shift + Clic droit (placé) pour configurer",
                "§7▸ Shift + Clic (placé) pour vendre avec Sell Hand"
        );
        meta.setLore(lore);

        tank.setItemMeta(meta);

        // Créer les données du tank
        TankData tankData = new TankData(tankId, owner.getUniqueId());
        tankCache.put(tankId, tankData);
        saveTank(tankData);

        return tank;
    }

    /**
     * Place un Tank dans le monde
     */
    public boolean placeTank(Location location, ItemStack tankItem, Player player) {
        if (!isTank(tankItem)) return false;

        String tankId = getTankId(tankItem);
        TankData tankData = tankCache.get(tankId);
        if (tankData == null) return false;

        // Vérifier qu'il n'y a pas déjà un tank à cette position
        if (isTankBlock(location)) {
            player.sendMessage("§c❌ Il y a déjà un tank à cette position!");
            return false;
        }

        // Placer le baril dans le monde
        location.getBlock().setType(Material.BARREL);

        // Mettre à jour les données
        tankData.setLocation(location);
        tankLocations.put(location, tankId);

        // Créer le nametag
        createNameTag(tankData);

        // Sauvegarder
        saveTank(tankData);

        player.sendMessage("§a✓ Tank placé! Les autres joueurs peuvent maintenant vendre leurs items.");
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_PLACE, 1.0f, 1.0f);

        return true;
    }

    /**
     * Récupère un Tank placé
     */
    public ItemStack breakTank(Location location, Player player) {
        TankData tankData = getTankAt(location);
        if (tankData == null) return null;

        // Vérifier la propriété
        if (!tankData.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Vous n'êtes pas le propriétaire de ce tank!");
            return null;
        }

        // Retirer le nametag
        removeNameTag(tankData.getId());

        // Nettoyer les données de position
        tankLocations.remove(location);
        tankData.setLocation(null);

        // Recréer l'item
        ItemStack tankItem = createTankItem(tankData);

        // Sauvegarder
        saveTank(tankData);

        player.sendMessage("§a✓ Tank récupéré!");
        player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);

        return tankItem;
    }

    /**
     * Crée l'item Tank à partir des données
     */
    private ItemStack createTankItem(TankData tankData) {
        ItemStack tank = new ItemStack(Material.BARREL);
        ItemMeta meta = tank.getItemMeta();

        // Données persistantes
        meta.getPersistentDataContainer().set(tankKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(tankIdKey, PersistentDataType.STRING, tankData.getId());

        // Mettre à jour l'affichage
        updateTankItemDisplay(meta, tankData);
        tank.setItemMeta(meta);

        return tank;
    }

    /**
     * Crée un nametag au-dessus du tank (double nametag)
     */
    private void createNameTag(TankData tankData) {
        if (!tankData.isPlaced()) return;

        Location nameTagLoc = tankData.getLocation().clone().add(0.5, 1.5, 0.5);

        // Nametag principal (informations techniques)
        ArmorStand mainNameTag = (ArmorStand) nameTagLoc.getWorld().spawnEntity(nameTagLoc, EntityType.ARMOR_STAND);
        setupNameTag(mainNameTag);

        // Nametag personnalisé (si défini)
        ArmorStand customNameTag = null;
        if (tankData.hasCustomName()) {
            Location customLoc = nameTagLoc.clone().add(0, 0.3, 0);
            customNameTag = (ArmorStand) customLoc.getWorld().spawnEntity(customLoc, EntityType.ARMOR_STAND);
            setupNameTag(customNameTag);
            customNameTag.setCustomName(tankData.getCustomName());
        }

        updateNameTag(tankData, mainNameTag);

        // Stocker les deux nametags
        tankNameTags.put(tankData.getId(), mainNameTag);
        if (customNameTag != null) {
            tankNameTags.put(tankData.getId() + "_custom", customNameTag);
        }
    }

    /**
     * Configure un ArmorStand pour être un nametag
     */
    private void setupNameTag(ArmorStand nameTag) {
        nameTag.setVisible(false);
        nameTag.setGravity(false);
        nameTag.setCanPickupItems(false);
        nameTag.setCustomNameVisible(true);
        nameTag.setRemoveWhenFarAway(false);
        nameTag.setInvulnerable(true);
    }

    /**
     * Met à jour le nametag d'un tank
     */
    private void updateNameTag(TankData tankData, ArmorStand nameTag) {
        if (nameTag == null || nameTag.isDead()) return;

        String ownerName = plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName();
        String playerBalance = "0$"; // Placeholder - adapter selon votre système économique
        // playerBalance = NumberFormatter.format(plugin.getEconomyManager().getMoney(plugin.getServer().getOfflinePlayer(tankData.getOwner()))) + "$";

        List<String> lines = Arrays.asList(
                "§6⚡ Tank de §e" + ownerName,
                "§7Solde propriétaire: §a" + playerBalance,
                "§7Items: §b" + NumberFormatter.format(tankData.getTotalItems()) + "§7/§b" + NumberFormatter.format(TankData.MAX_CAPACITY),
                "§7Prix: " + (tankData.getPrices().isEmpty() ? "§cAucun" : "§a" + tankData.getPrices().size() + " configurés")
        );

        nameTag.setCustomName(String.join("\n", lines));
    }

    /**
     * Met à jour les nametags d'un tank (méthode publique)
     */
    public void updateTankNameTag(TankData tankData) {
        if (!tankData.isPlaced()) return;

        // Schedule the world modification part to run on the main server thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ArmorStand mainNameTag = tankNameTags.get(tankData.getId());
            ArmorStand customNameTag = tankNameTags.get(tankData.getId() + "_custom");

            // Mettre à jour le nametag principal
            if (mainNameTag != null && !mainNameTag.isDead()) {
                updateNameTag(tankData, mainNameTag);
            }

            // Gérer le nametag personnalisé
            if (tankData.hasCustomName()) {
                if (customNameTag == null || customNameTag.isDead()) {
                    // Créer le nametag personnalisé
                    Location customLoc = tankData.getLocation().clone().add(0.5, 1.8, 0.5);
                    // This is now safe because it's running on the main thread
                    ArmorStand newCustomNameTag = (ArmorStand) customLoc.getWorld().spawnEntity(customLoc, EntityType.ARMOR_STAND);
                    setupNameTag(newCustomNameTag);
                    tankNameTags.put(tankData.getId() + "_custom", newCustomNameTag);
                    customNameTag = newCustomNameTag; // Use the newly created stand
                }
                customNameTag.setCustomName(tankData.getCustomName());
            } else {
                // Supprimer le nametag personnalisé s'il existe
                if (customNameTag != null && !customNameTag.isDead()) {
                    customNameTag.remove();
                    tankNameTags.remove(tankData.getId() + "_custom");
                }
            }
        });
    }

    /**
     * Retire les nametags d'un tank
     */
    private void removeNameTag(String tankId) {
        ArmorStand mainNameTag = tankNameTags.remove(tankId);
        ArmorStand customNameTag = tankNameTags.remove(tankId + "_custom");

        if (mainNameTag != null && !mainNameTag.isDead()) {
            mainNameTag.remove();
        }

        if (customNameTag != null && !customNameTag.isDead()) {
            customNameTag.remove();
        }
    }

    /**
     * Met à jour l'affichage d'un Tank item
     */
    private void updateTankItemDisplay(ItemMeta meta, TankData tankData) {
        meta.setDisplayName("§6⚡ Tank Automatique §7(ID: " + tankData.getId() + ")");

        List<String> lore = new ArrayList<>();
        lore.add("§7Propriétaire: §e" + plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName());

        if (tankData.hasCustomName()) {
            lore.add("§7Nom: §f" + tankData.getCustomName());
        }

        lore.addAll(Arrays.asList(
                "§7Capacité: §b" + NumberFormatter.format(tankData.getTotalItems()) + "§7/§b" + NumberFormatter.format(TankData.MAX_CAPACITY),
                "§7Filtres: " + (tankData.getFilters().isEmpty() ? "§cAucun" : "§a" + tankData.getFilters().size() + " matériaux"),
                "§7Prix configurés: " + (tankData.getPrices().isEmpty() ? "§cAucun" : "§a" + tankData.getPrices().size()),
                "",
                "§7▸ Placer au sol pour activer",
                "§7▸ Shift + Clic droit (placé) pour configurer",
                "§7▸ Shift + Clic (placé) pour vendre avec Sell Hand",
                "§7▸ Clic droit (placé) pour vendre ou voir les prix"
        ));
        meta.setLore(lore);
    }

    /**
     * Tente de vendre des items au Tank placé
     */
    public boolean sellToTank(Location tankLocation, Player seller, ItemStack itemToSell) {
        TankData tankData = getTankAt(tankLocation);
        if (tankData == null) return false;

        Material material = itemToSell.getType();

        // Vérifier les filtres
        if (!tankData.getFilters().contains(material)) {
            seller.sendMessage("§c❌ Ce tank n'accepte pas " + material.name().toLowerCase() + "!");
            return false;
        }

        // Vérifier le prix configuré
        if (!tankData.getPrices().containsKey(material)) {
            seller.sendMessage("§c❌ Aucun prix configuré pour " + material.name().toLowerCase() + "!");
            return false;
        }

        long pricePerItem = tankData.getPrices().get(material);
        if (pricePerItem <= 0) {
            seller.sendMessage("§c❌ Prix non configuré pour " + material.name().toLowerCase() + "!");
            return false;
        }

        int amount = itemToSell.getAmount();
        long totalPrice = pricePerItem * amount;

        // Vérifier que le propriétaire a assez d'argent sur son compte
        Player owner = plugin.getServer().getPlayer(tankData.getOwner());
        if (owner == null) {
            // Le propriétaire n'est pas en ligne - vérifier via l'économie offline
            // if (!plugin.getEconomyManager().hasEnoughMoney(tankData.getOwner(), totalPrice)) {
            //     seller.sendMessage("§c❌ Le propriétaire du tank n'a pas assez d'argent! (Requis: " + NumberFormatter.format(totalPrice) + "$)");
            //     return false;
            // }
        } else {
            // Le propriétaire est en ligne
            // if (plugin.getEconomyManager().getMoney(owner) < totalPrice) {
            //     seller.sendMessage("§c❌ Le propriétaire du tank n'a pas assez d'argent! (Requis: " + NumberFormatter.format(totalPrice) + "$)");
            //     return false;
            // }
        }

        // Vérifier la capacité
        if (!tankData.canAddItems(amount)) {
            seller.sendMessage("§c❌ Le tank est plein!");
            return false;
        }

        // Effectuer la transaction
        tankData.addItems(material, amount);

        // Retirer l'argent du propriétaire et le donner au vendeur
        // if (owner != null) {
        //     plugin.getEconomyManager().removeMoney(owner, totalPrice);
        // } else {
        //     plugin.getEconomyManager().removeMoneyOffline(tankData.getOwner(), totalPrice);
        // }
        // plugin.getEconomyManager().addMoney(seller, totalPrice);

        // Messages
        seller.sendMessage("§a✓ Vendu " + amount + "x " + material.name().toLowerCase() +
                " pour " + NumberFormatter.format(totalPrice) + "$!");
        seller.playSound(seller.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

        // Notifier le propriétaire s'il est en ligne
        if (owner != null && !owner.equals(seller)) {
            owner.sendMessage("§7📦 " + seller.getName() + " a vendu " + amount + "x " +
                    material.name().toLowerCase() + " à votre tank pour " +
                    NumberFormatter.format(totalPrice) + "$");
        }

        // Mettre à jour le nametag
        updateTankNameTag(tankData);

        // Sauvegarder
        saveTank(tankData);

        return true;
    }

    /**
     * Récupère les données d'un Tank
     */
    public TankData getTankData(ItemStack tank) {
        if (!isTank(tank)) return null;
        String tankId = getTankId(tank);
        return tankId != null ? tankCache.get(tankId) : null;
    }

    /**
     * Récupère l'ID d'un Tank
     */
    public String getTankId(ItemStack tank) {
        if (!isTank(tank)) return null;
        ItemMeta meta = tank.getItemMeta();
        return meta.getPersistentDataContainer().get(tankIdKey, PersistentDataType.STRING);
    }

    /**
     * Génère un ID unique pour un Tank
     */
    private String generateTankId() {
        String id;
        do {
            id = "TANK-" + String.format("%06d", nextTankId++);
        } while (tankCache.containsKey(id));
        return id;
    }

    /**
     * Charge tous les Tanks depuis le fichier YAML
     */
    private void loadTanks() {
        if (!tanksFile.exists()) {
            tanksConfig = new YamlConfiguration();
            saveTanksFile();
            return;
        }

        tanksConfig = YamlConfiguration.loadConfiguration(tanksFile);

        // Charger nextTankId
        nextTankId = tanksConfig.getInt("next-id", 1);

        // Charger tous les tanks
        if (tanksConfig.contains("tanks")) {
            for (String tankId : tanksConfig.getConfigurationSection("tanks").getKeys(false)) {
                try {
                    TankData tankData = TankData.fromYaml(tanksConfig.getConfigurationSection("tanks." + tankId));
                    if (tankData != null) {
                        tankCache.put(tankId, tankData);

                        // Si le tank est placé, restaurer sa position et son nametag
                        if (tankData.isPlaced()) {
                            Location loc = tankData.getLocation();
                            if (loc.getBlock().getType() == Material.BARREL) {
                                tankLocations.put(loc, tankId);
                                createNameTag(tankData);
                            } else {
                                // Le bloc n'est plus un baril, réinitialiser la position
                                tankData.setLocation(null);
                                saveTank(tankData);
                            }
                        }
                    }
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Erreur lors du chargement du tank " + tankId + ": " + e.getMessage());
                }
            }
        }

        plugin.getPluginLogger().info("Chargé " + tankCache.size() + " tanks (" + tankLocations.size() + " placés)");
    }

    /**
     * Sauvegarde un Tank spécifique
     */
    public void saveTank(TankData tankData) {
        tanksConfig.set("tanks." + tankData.getId(), tankData.toYaml());
        tanksConfig.set("next-id", nextTankId);
        saveTanksFile();
    }

    /**
     * Récupère le cache des tanks (pour usage interne)
     */
    public Map<String, TankData> getTankCache() {
        return tankCache;
    }

    /**
     * Récupère la NamespacedKey pour l'ID des tanks
     */
    public NamespacedKey getTankIdKey() {
        return tankIdKey;
    }

    /**
     * Sauvegarde le fichier YAML
     */
    private void saveTanksFile() {
        try {
            tanksConfig.save(tanksFile);
        } catch (IOException e) {
            plugin.getPluginLogger().severe("Erreur lors de la sauvegarde des tanks: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde tous les Tanks
     */
    public void saveAllTanks() {
        for (TankData tankData : tankCache.values()) {
            tanksConfig.set("tanks." + tankData.getId(), tankData.toYaml());
        }
        tanksConfig.set("next-id", nextTankId);
        saveTanksFile();
    }

    /**
     * Récupère tous les Tanks d'un joueur
     */
    public List<TankData> getPlayerTanks(UUID playerUUID) {
        return tankCache.values().stream()
                .filter(tank -> tank.getOwner().equals(playerUUID))
                .toList();
    }

    /**
     * Supprime un Tank
     */
    public void removeTank(String tankId) {
        TankData tankData = tankCache.remove(tankId);
        if (tankData != null) {
            // Supprimer de la position si placé
            if (tankData.isPlaced()) {
                tankLocations.remove(tankData.getLocation());
                removeNameTag(tankId);
            }
        }
        tanksConfig.set("tanks." + tankId, null);
        saveTanksFile();
    }

    /**
     * Nettoyage à l'arrêt du plugin
     */
    public void shutdown() {
        // Sauvegarder tous les tanks
        saveAllTanks();

        // Supprimer tous les nametags
        for (ArmorStand nameTag : tankNameTags.values()) {
            if (nameTag != null && !nameTag.isDead()) {
                nameTag.remove();
            }
        }
        tankNameTags.clear();
    }
}