package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire principal des automineurs
 * Gère le placement, le fonctionnement et la logique métier
 */
public class AutominerManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey uuidKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey enchantKey;
    private final NamespacedKey cristalKey;

    // Cache des automineurs actifs pour optimisation
    private final Map<UUID, Set<String>> activeAutominers = new ConcurrentHashMap<>();

    public AutominerManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.uuidKey = new NamespacedKey(plugin, "autominer_uuid");
        this.typeKey = new NamespacedKey(plugin, "autominer_type");
        this.enchantKey = new NamespacedKey(plugin, "autominer_enchants");
        this.cristalKey = new NamespacedKey(plugin, "autominer_cristals");

        plugin.getPluginLogger().info("§aAutominerManager initialisé.");
    }

    /**
     * Crée un nouvel automineur et le donne au joueur
     */
    public boolean giveAutominer(Player player, AutominerType type) {
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cVotre inventaire est plein!");
            return false;
        }

        String uuid = UUID.randomUUID().toString();
        AutominerData autominer = new AutominerData(uuid, type);
        ItemStack item = autominer.toItemStack(uuidKey, typeKey, enchantKey, cristalKey);

        player.getInventory().addItem(item);
        player.sendMessage("§a✅ Vous avez reçu un " + type.getColoredName() + " §aAutomineur!");
        return true;
    }

    /**
     * Place un automineur (l'active)
     */
    public boolean placeAutominer(Player player, ItemStack autominerItem) {
        AutominerData autominer = AutominerData.fromItemStack(autominerItem, uuidKey, typeKey, enchantKey, cristalKey);
        if (autominer == null) {
            player.sendMessage("§c❌ Cet item n'est pas un automineur valide.");
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.addActiveAutominer(autominer.getUuid())) {
            player.sendMessage("§a✅ " + autominer.getType().getColoredName() + " §aAutomineur placé et activé!");
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
            return true;
        } else {
            player.sendMessage("§c❌ Vous ne pouvez avoir que §e2 automineurs actifs §c simultanément!");
            return false;
        }
    }

    /**
     * Retire un automineur (le désactive)
     */
    public boolean removeAutominer(Player player, String autominerUuid) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.removeActiveAutominer(autominerUuid)) {
            player.sendMessage("§a✅ Automineur retiré et désactivé!");
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());
            return true;
        } else {
            player.sendMessage("§c❌ Cet automineur n'est pas actif!");
            return false;
        }
    }

    /**
     * Démarre/arrête tous les automineurs d'un joueur
     */
    public void toggleAllAutominers(Player player, boolean start) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        playerData.setAutominersRunning(start);

        String status = start ? "§adémarrés" : "§carrêtés";
        player.sendMessage("§e⚡ Tous vos automineurs ont été " + status + "§e!");
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Ajoute des têtes au carburant d'un joueur
     */
    public void addFuelHeads(Player player, int amount) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentFuel = playerData.getAutominerFuel();
        playerData.setAutominerFuel(currentFuel + amount);

        player.sendMessage("§a✅ " + amount + " têtes ajoutées au carburant! §7(Total: " +
                (currentFuel + amount) + ")");
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Change le monde de minage (avec coût en beacons)
     */
    public boolean changeWorld(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentWorld = playerData.getAutominerWorld();
        String newWorld = playerData.rollRandomWorld();

        // Coût en beacons basé sur la "distance" du monde (a=1, b=2, ..., z=26)
        int worldCost = (newWorld.charAt(0) - 'a' + 1) * 2;

        // Vérification des beacons
        if (playerData.getBeacons() < worldCost) {
            player.sendMessage("§c❌ Pas assez de beacons! Coût: §e" + worldCost + " beacons");
            return false;
        }

        // Consommation des beacons
        playerData.removeBeacon(worldCost);

        // Changement de monde
        playerData.setAutominerWorld(newWorld);
        player.sendMessage("§a✅ Monde changé de " + currentWorld.toUpperCase() + " vers §e" + newWorld.toUpperCase() + " §7(-" + worldCost + " beacons)");
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Améliore le stockage des automineurs
     */
    public boolean upgradeStorage(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentCapacity = playerData.getAutominerStorageCapacity();
        long nextCapacity = getNextStorageCapacity(currentCapacity);
        int beaconCost = getStorageUpgradeCost(currentCapacity);

        if (nextCapacity == currentCapacity) {
            player.sendMessage("§c❌ Stockage déjà au maximum! §7(2M blocs)");
            return false;
        }

        if (playerData.getBeacons() < beaconCost) {
            player.sendMessage("§c❌ Pas assez de beacons! Coût: §e" + beaconCost + " beacons");
            return false;
        }

        // Amélioration
        playerData.removeBeacon(beaconCost);
        playerData.setAutominerStorageCapacity(nextCapacity);

        String formatted = formatCapacity(nextCapacity);
        player.sendMessage("§a✅ Stockage amélioré à §e" + formatted + " §7(-" + beaconCost + " beacons)");
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Condense 9 automineurs en 1 de niveau supérieur
     */
    public boolean condenseAutominers(Player player, List<ItemStack> items) {
        if (items.size() != 9) {
            player.sendMessage("§c❌ Vous devez placer exactement 9 automineurs.");
            return false;
        }

        AutominerData firstAutominer = AutominerData.fromItemStack(items.get(0), uuidKey, typeKey, enchantKey, cristalKey);
        if (firstAutominer == null) {
            player.sendMessage("§c❌ Item invalide dans la grille de condensation.");
            return false;
        }

        AutominerType type = firstAutominer.getType();
        for (ItemStack item : items) {
            AutominerData data = AutominerData.fromItemStack(item, uuidKey, typeKey, enchantKey, cristalKey);
            if (data == null || data.getType() != type) {
                player.sendMessage("§c❌ Tous les automineurs doivent être du même type.");
                return false;
            }
        }

        AutominerType nextType = type.getNextTier();
        if (nextType == null) {
            player.sendMessage("§c❌ Les automineurs " + type.getColoredName() + " §csont au niveau maximum!");
            return false;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c❌ Pas de place dans l'inventaire!");
            return false;
        }

        // Création du nouvel automineur
        giveAutominer(player, nextType);
        player.sendMessage("§a✨ §lCONDENSATION RÉUSSIE! §r§a9x " + type.getColoredName() +
                " §a→ 1x " + nextType.getColoredName());
        return true;
    }


    // Méthodes utilitaires

    private boolean isAutominer(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer().has(uuidKey, org.bukkit.persistence.PersistentDataType.STRING);
    }

    private long getNextStorageCapacity(long current) {
        if (current < 10000) return 10000;
        if (current < 50000) return 50000;
        if (current < 100000) return 100000;
        if (current < 500000) return 500000;
        if (current < 1000000) return 1000000;
        if (current < 2000000) return 2000000;
        return current; // Maximum atteint
    }

    private int getStorageUpgradeCost(long current) {
        if (current < 10000) return 1;
        if (current < 50000) return 5;
        if (current < 100000) return 10;
        if (current < 500000) return 25;
        if (current < 1000000) return 50;
        if (current < 2000000) return 100;
        return Integer.MAX_VALUE; // Pas d'amélioration possible
    }

    private String formatCapacity(long capacity) {
        if (capacity >= 1000000) return (capacity / 1000000) + "M";
        if (capacity >= 1000) return (capacity / 1000) + "k";
        return String.valueOf(capacity);
    }

    // Getters publics
    public NamespacedKey getUuidKey() {
        return uuidKey;
    }

    public NamespacedKey getTypeKey() {
        return typeKey;
    }

    public NamespacedKey getEnchantKey() {
        return enchantKey;
    }

    public NamespacedKey getCristalKey() {
        return cristalKey;
    }
}