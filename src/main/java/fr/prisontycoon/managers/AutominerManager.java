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

        startAutominerProcessing();
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
    public boolean placeAutominer(Player player, AutominerData autominer) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Set<String> currentActive = playerData.getActiveAutominers();

        // Vérification limite de 2 automineurs actifs
        if (currentActive.size() >= 2) {
            player.sendMessage("§c❌ Vous ne pouvez avoir que §e2 automineurs actifs §c simultanément!");
            return false;
        }

        // Vérification que l'automineur n'est pas déjà actif
        if (currentActive.contains(autominer.getUuid())) {
            player.sendMessage("§c❌ Cet automineur est déjà actif!");
            return false;
        }

        // Placement
        currentActive.add(autominer.getUuid());
        playerData.setActiveAutominers(currentActive);

        // Cache pour optimisation
        activeAutominers.computeIfAbsent(player.getUniqueId(), k -> ConcurrentHashMap.newKeySet())
                .add(autominer.getUuid());

        player.sendMessage("§a✅ " + autominer.getType().getColoredName() + " §aAutomineur placé et activé!");
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Retire un automineur (le désactive)
     */
    public boolean removeAutominer(Player player, String autominerUuid) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Set<String> currentActive = playerData.getActiveAutominers();

        if (!currentActive.contains(autominerUuid)) {
            player.sendMessage("§c❌ Cet automineur n'est pas actif!");
            return false;
        }

        // Retrait
        currentActive.remove(autominerUuid);
        playerData.setActiveAutominers(currentActive);

        // Mise à jour cache
        Set<String> playerActive = activeAutominers.get(player.getUniqueId());
        if (playerActive != null) {
            playerActive.remove(autominerUuid);
        }

        player.sendMessage("§a✅ Automineur retiré et désactivé!");
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
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
    public boolean changeWorld(Player player, String newWorld) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Coût en beacons basé sur la "distance" du monde (A=1, B=2, ..., Z=26)
        int worldCost = calculateWorldCost(newWorld);

        // Vérification des beacons
        if (!hasEnoughBeacons(player, worldCost)) {
            player.sendMessage("§c❌ Pas assez de beacons! Coût: §e" + worldCost + " beacons");
            return false;
        }

        // Consommation des beacons
        removeBeacons(player, worldCost);

        // Changement de monde
        playerData.setAutominerWorld(newWorld);
        player.sendMessage("§a✅ Monde changé vers §e" + newWorld + " §7(-" + worldCost + " beacons)");
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Améliore le stockage des automineurs
     */
    public boolean upgradeStorage(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long currentCapacity = playerData.getAutominerStorageCapacity();

        // Prochaine amélioration
        long nextCapacity = getNextStorageCapacity(currentCapacity);
        int beaconCost = getStorageUpgradeCost(currentCapacity);

        if (nextCapacity == currentCapacity) {
            player.sendMessage("§c❌ Stockage déjà au maximum! §7(2M blocs)");
            return false;
        }

        if (!hasEnoughBeacons(player, beaconCost)) {
            player.sendMessage("§c❌ Pas assez de beacons! Coût: §e" + beaconCost + " beacons");
            return false;
        }

        // Amélioration
        removeBeacons(player, beaconCost);
        playerData.setAutominerStorageCapacity(nextCapacity);

        String formatted = formatCapacity(nextCapacity);
        player.sendMessage("§a✅ Stockage amélioré à §e" + formatted + " §7(-" + beaconCost + " beacons)");
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Condense 9 automineurs en 1 de niveau supérieur
     */
    public boolean condenseAutominers(Player player, AutominerType type) {
        // Compter les automineurs du type dans l'inventaire
        int count = 0;
        List<ItemStack> toRemove = new ArrayList<>();

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isAutominer(item)) {
                AutominerData data = AutominerData.fromItemStack(item, uuidKey, typeKey, enchantKey, cristalKey);
                if (data != null && data.getType() == type) {
                    count++;
                    toRemove.add(item);
                    if (count >= 9) break;
                }
            }
        }

        if (count < 9) {
            player.sendMessage("§c❌ Vous avez besoin de §e9 " + type.getColoredName() +
                    " §cAutomineurs! §7(Vous en avez " + count + ")");
            return false;
        }

        AutominerType nextType = type.getNextTier();
        if (nextType == null) {
            player.sendMessage("§c❌ Les automineurs " + type.getColoredName() + " §csont au niveau maximum!");
            return false;
        }

        // Vérification de place
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c❌ Pas assez de place dans l'inventaire!");
            return false;
        }

        // Suppression des 9 automineurs
        for (int i = 0; i < 9; i++) {
            toRemove.get(i).setAmount(0);
        }

        // Création du nouvel automineur
        giveAutominer(player, nextType);
        player.sendMessage("§a✨ §lCONDENSATION RÉUSSIE! §r§a9x " + type.getColoredName() +
                " §a→ 1x " + nextType.getColoredName());
        return true;
    }

    /**
     * Démarre le système de traitement des automineurs (toutes les minutes)
     */
    private void startAutominerProcessing() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processAllAutominers();
            }
        }.runTaskTimer(plugin, 1200L, 1200L); // Toutes les minutes (1200 ticks)
    }

    /**
     * Traite tous les automineurs actifs
     */
    private void processAllAutominers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            processPlayerAutominers(player);
        }
    }

    /**
     * Traite les automineurs d'un joueur spécifique
     */
    private void processPlayerAutominers(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérification si les automineurs sont en marche
        if (!playerData.isAutominersRunning()) {
            return;
        }

        Set<String> activeIds = playerData.getActiveAutominers();
        if (activeIds.isEmpty()) {
            return;
        }

        // Calcul de la consommation totale de carburant
        int totalFuelNeeded = 0;
        List<AutominerData> activeAutominersList = new ArrayList<>();

        // Récupération des automineurs actifs depuis l'inventaire
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isAutominer(item)) {
                AutominerData data = AutominerData.fromItemStack(item, uuidKey, typeKey, enchantKey, cristalKey);
                if (data != null && activeIds.contains(data.getUuid())) {
                    activeAutominersList.add(data);
                    totalFuelNeeded += data.getActualFuelConsumption();
                }
            }
        }

        // Vérification du carburant
        long currentFuel = playerData.getAutominerFuel();
        if (currentFuel < totalFuelNeeded) {
            player.sendMessage("§c⚠️ Carburant insuffisant pour les automineurs! §7(" +
                    currentFuel + "/" + totalFuelNeeded + " têtes)");
            return;
        }

        // Consommation du carburant
        playerData.setAutominerFuel(currentFuel - totalFuelNeeded);

        // Traitement de chaque automineur
        for (AutominerData autominer : activeAutominersList) {
            processSingleAutominer(player, autominer, playerData);
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Traite un automineur individuel (génère les ressources)
     */
    private void processSingleAutominer(Player player, AutominerData autominer, PlayerData playerData) {
        String world = playerData.getAutominerWorld();

        // Génération des blocs minés selon l'efficacité
        int blocksMinedCount = Math.max(1, autominer.getTotalEfficiency() / 10);

        // Génération des ressources
        Map<Material, Integer> minedBlocks = generateMinedBlocks(world, blocksMinedCount, autominer);

        // Stockage des ressources
        for (Map.Entry<Material, Integer> entry : minedBlocks.entrySet()) {
            addToStorage(playerData, entry.getKey(), entry.getValue());
        }

        // Génération des bonus (tokens, XP, money)
        generateBonusRewards(player, autominer, blocksMinedCount);

        // Messages de debug (optionnel)
        if (plugin.getConfig().getBoolean("debug.autominers", false)) {
            player.sendMessage("§7[DEBUG] " + autominer.getType().getDisplayName() +
                    " a miné " + blocksMinedCount + " blocs dans le monde " + world);
        }
    }

    // Méthodes utilitaires

    private boolean isAutominer(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer().has(uuidKey, org.bukkit.persistence.PersistentDataType.STRING);
    }

    private int calculateWorldCost(String world) {
        if (world == null || world.length() != 1) return 1;
        char worldChar = world.toUpperCase().charAt(0);
        return Math.max(1, worldChar - 'A' + 1); // A=1, B=2, ..., Z=26
    }

    private boolean hasEnoughBeacons(Player player, int amount) {
        return player.getInventory().contains(Material.BEACON, amount);
    }

    private void removeBeacons(Player player, int amount) {
        player.getInventory().removeItem(new ItemStack(Material.BEACON, amount));
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

    private Map<Material, Integer> generateMinedBlocks(String world, int blockCount, AutominerData autominer) {
        Map<Material, Integer> result = new HashMap<>();

        // Logique basique - à améliorer selon vos mines
        Material[] possibleBlocks = {Material.STONE, Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.DIAMOND_ORE};
        Random rand = new Random();

        for (int i = 0; i < blockCount; i++) {
            Material block = possibleBlocks[rand.nextInt(possibleBlocks.length)];

            // Application de la Fortune
            int quantity = 1;
            int fortune = autominer.getTotalFortune();
            if (fortune > 0 && rand.nextInt(100) < (fortune * 2)) {
                quantity += rand.nextInt(fortune / 10 + 1) + 1;
            }

            result.merge(block, quantity, Integer::sum);
        }

        return result;
    }

    private void addToStorage(PlayerData playerData, Material material, int amount) {
        // Logique d'ajout au stockage - à intégrer avec votre système
        long currentStored = playerData.getAutominerStoredBlocks().getOrDefault(material, 0L);
        long newAmount = currentStored + amount;

        // Vérification de la capacité
        long totalStored = playerData.getAutominerStoredBlocks().values().stream().mapToLong(Long::longValue).sum();
        long capacity = playerData.getAutominerStorageCapacity();

        if (totalStored + amount <= capacity) {
            playerData.getAutominerStoredBlocks().put(material, newAmount);
        }
        // Sinon, stockage plein - pas d'ajout
    }

    private void generateBonusRewards(Player player, AutominerData autominer, int blocksMinedCount) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Random rand = new Random();

        // Bonus tokens
        int tokenBonus = autominer.getTotalTokenBonus();
        if (tokenBonus > 0 && rand.nextInt(100) < tokenBonus) {
            long tokenAmount = blocksMinedCount * (tokenBonus / 10 + 1);
            playerData.addTokens(tokenAmount);
        }

        // Bonus XP
        int expBonus = autominer.getTotalExpBonus();
        if (expBonus > 0 && rand.nextInt(100) < expBonus) {
            int expAmount = blocksMinedCount * (expBonus / 10 + 1);
            player.giveExp(expAmount);
        }

        // Bonus argent
        int moneyBonus = autominer.getTotalMoneyBonus();
        if (moneyBonus > 0 && rand.nextInt(100) < moneyBonus) {
            long moneyAmount = blocksMinedCount * (moneyBonus / 10 + 1);
            playerData.addCoins(moneyAmount);
        }
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