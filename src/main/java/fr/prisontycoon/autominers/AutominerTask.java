package fr.prisontycoon.autominers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.AutominerManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;

/**
 * Tâche asynchrone de minage des automineurs
 * S'exécute toutes les 20 ticks (1 seconde)
 */
public class AutominerTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private final AutominerManager autominerManager;

    public AutominerTask(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.autominerManager = plugin.getAutominerManager();
    }

    @Override
    public void run() {
        // Parcourt tous les joueurs en ligne
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                processPlayerAutominers(player);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("§cErreur lors du traitement des automineurs pour " +
                        player.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Traite les automineurs d'un joueur
     */
    private void processPlayerAutominers(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifications de base
        if (playerData.getAutominerFuelReserve() <= 0) {
            return; // Pas de carburant
        }

        // Vérifier si le stockage est plein
        if (isStorageFull(playerData)) {
            return; // Stockage plein
        }

        // Traiter chaque slot d'automineur
        processAutominerSlot(player, playerData, playerData.getActiveAutominerSlot1(), "slot_1");
        processAutominerSlot(player, playerData, playerData.getActiveAutominerSlot2(), "slot_2");

        // Marquer les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Traite un slot d'automineur spécifique
     */
    private void processAutominerSlot(Player player, PlayerData playerData, ItemStack autominer, String slotName) {
        if (autominer == null || !autominerManager.isAutominer(autominer)) {
            return; // Pas d'automineur dans ce slot
        }

        // Calculer la consommation de carburant
        double fuelConsumption = autominerManager.calculateFuelConsumption(autominer);
        if (playerData.getAutominerFuelReserve() < fuelConsumption) {
            return; // Pas assez de carburant pour ce tick
        }

        // Simuler le minage
        String currentWorld = playerData.getAutominerCurrentWorld();
        if (currentWorld == null) {
            currentWorld = "mine-a"; // Monde par défaut
            playerData.setAutominerCurrentWorld(currentWorld);
        }

        AutominerManager.AutominerMiningResult result = autominerManager.simulateMining(autominer, currentWorld);
        if (result == null) {
            return; // Erreur de simulation
        }

        // Appliquer les résultats
        applyMiningResults(player, playerData, result);

        // Consommer le carburant
        playerData.setAutominerFuelReserve(playerData.getAutominerFuelReserve() - fuelConsumption);

        // Debug amélioré avec détails Fortune et Greed
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getPluginLogger().info("§7Automineur " + slotName + " de " + player.getName() +
                    " a miné " + result.quantity() + "x " + result.minedBlock().name() +
                    " (Fortune: " + result.quantity() + " blocs)" +
                    " → Gains: " + result.coins() + " coins, " + result.tokens() + " tokens, " +
                    result.experience() + " exp" +
                    (result.keys() > 0 ? ", " + result.keys() + " clés" : "") +
                    (result.beaconFound() ? ", 1 beacon" : ""));
        }
    }

    /**
     * Applique les résultats du minage
     */
    private void applyMiningResults(Player player, PlayerData playerData, AutominerManager.AutominerMiningResult result) {
        // Stocker les blocs minés
        addToStorage(playerData, result.minedBlock(), result.quantity());

        // NOUVEAU: Accumuler les gains greed dans les "pending" au lieu de les donner directement
        if (result.coins() > 0) {
            playerData.addAutominerPendingCoins(result.coins());
        }
        if (result.tokens() > 0) {
            playerData.addAutominerPendingTokens(result.tokens());
        }
        if (result.experience() > 0) {
            playerData.addAutominerPendingExperience(result.experience());
        }

        // Générer des clés si nécessaire
        if (result.keys() > 0) {
            AutominerType type = autominerManager.getAutominerType(playerData.getActiveAutominerSlot1());
            if (type == null) {
                type = autominerManager.getAutominerType(playerData.getActiveAutominerSlot2());
            }

            if (type != null) {
                ItemStack key = autominerManager.createKey(type);
                String keyType = getKeyTypeFromItem(key);

                Map<String, Integer> storedKeys = playerData.getAutominerStoredKeys();
                storedKeys.put(keyType, storedKeys.getOrDefault(keyType, 0) + result.keys());
                playerData.setAutominerStoredKeys(storedKeys);
            }
        }

        // Accumuler les beacons dans les "pending"
        if (result.beaconFound()) {
            playerData.addAutominerPendingBeacons(1);

            // Notification synchrone
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§d✨ Votre automineur a trouvé un beacon!");
                }
            });
        }
    }

    /**
     * Ajoute des items au stockage de l'automineur
     */
    private void addToStorage(PlayerData playerData, Material material, int quantity) {
        Map<Material, Long> storage = playerData.getAutominerStorageContents();
        long currentAmount = storage.getOrDefault(material, 0L);

        // Vérifier la capacité maximale
        long maxCapacity = getMaxStorageCapacity(playerData.getAutominerStorageLevel());
        long totalStored = storage.values().stream().mapToLong(Long::longValue).sum();

        long canStore = Math.min(quantity, maxCapacity - totalStored);
        if (canStore > 0) {
            storage.put(material, currentAmount + canStore);
            playerData.setAutominerStorageContents(storage);
        }
    }

    /**
     * Vérifie si le stockage est plein
     */
    private boolean isStorageFull(PlayerData playerData) {
        Map<Material, Long> storage = playerData.getAutominerStorageContents();
        long totalStored = storage.values().stream().mapToLong(Long::longValue).sum();
        long maxCapacity = getMaxStorageCapacity(playerData.getAutominerStorageLevel());

        return totalStored >= maxCapacity;
    }

    /**
     * Calcule la capacité maximale de stockage selon le niveau
     */
    private long getMaxStorageCapacity(int storageLevel) {
        // Capacité de base : 1000, +500 par niveau
        return 1000L + (storageLevel * 500L);
    }

    /**
     * Extrait le type de clé depuis l'item
     */
    private String getKeyTypeFromItem(ItemStack key) {
        if (key == null || !key.hasItemMeta() || key.getItemMeta().getDisplayName() == null) {
            return "Commune";
        }

        String displayName = key.getItemMeta().getDisplayName();
        if (displayName.contains("Cristal")) return "Cristal";
        if (displayName.contains("Légendaire")) return "Légendaire";
        if (displayName.contains("Rare")) return "Rare";
        if (displayName.contains("Peu Commune")) return "Peu Commune";
        return "Commune";
    }
}