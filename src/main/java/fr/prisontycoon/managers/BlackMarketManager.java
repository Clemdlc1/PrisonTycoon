package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire du Black Market - PNJ clandestin mobile
 * Gère les offres selon la réputation, les déplacements et les risques
 */
public class BlackMarketManager {

    private final PrisonTycoon plugin;
    private final ReputationManager reputationManager;

    // Localisation actuelle du marché noir
    private Location currentLocation;
    private boolean isAvailable;

    // Stock d'items actuels
    private final Map<ItemStack, Integer> currentStock; // Item -> Prix en beacons

    // Emplacements possibles pour le marché noir
    private final List<Location> possibleLocations;

    // Configuration des déplacements
    private static final int RELOCATION_HOURS = 6; // Toutes les 6 heures
    private static final double RAID_CHANCE = 0.15; // 15% de chance de raid
    private static final double AMBUSH_CHANCE = 0.10; // 10% de chance d'embuscade
    private static final double SCAM_CHANCE = 0.05; // 5% de chance d'arnaque

    public BlackMarketManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.reputationManager = plugin.getReputationManager();
        this.currentStock = new HashMap<>();
        this.possibleLocations = new ArrayList<>();
        this.isAvailable = true;

        initializePossibleLocations();
        relocateMarket(); // Positionnement initial
        startRelocationTask(); // Démarre les déplacements automatiques

        plugin.getPluginLogger().info("§aBlackMarketManager initialisé avec " + possibleLocations.size() + " emplacements possibles.");
    }

    /**
     * Initialise les emplacements possibles du marché noir
     */
    private void initializePossibleLocations() {
        // TODO: Charger depuis la configuration ou définir manuellement
        // Pour l'instant, utilise des coordonnées par défaut

        // Exemple d'emplacements cachés
        possibleLocations.add(new Location(Bukkit.getWorld("world"), 120, 64, -45));
        possibleLocations.add(new Location(Bukkit.getWorld("world"), -85, 70, 200));
        possibleLocations.add(new Location(Bukkit.getWorld("world"), 350, 68, -120));
        possibleLocations.add(new Location(Bukkit.getWorld("world"), -200, 65, -300));
        possibleLocations.add(new Location(Bukkit.getWorld("world"), 0, 72, 450));

        plugin.getPluginLogger().info("Emplacements Black Market configurés: " + possibleLocations.size());
    }

    /**
     * Démarre la tâche de relocalisation automatique
     */
    private void startRelocationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                relocateMarket();
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 60 * 60 * RELOCATION_HOURS, 20 * 60 * 60 * RELOCATION_HOURS);
    }

    /**
     * Relocate le marché noir et rafraîchit le stock
     */
    public void relocateMarket() {
        // Vérifie les risques
        processRisks();

        if (!isAvailable) {
            plugin.getPluginLogger().info("Black Market indisponible suite à un raid");
            return;
        }

        // Choisit un nouvel emplacement
        Location newLocation = possibleLocations.get(ThreadLocalRandom.current().nextInt(possibleLocations.size()));
        currentLocation = newLocation;

        // Rafraîchit le stock
        refreshStock();

        plugin.getPluginLogger().info("Black Market relocalisé en " +
                currentLocation.getBlockX() + ", " + currentLocation.getBlockY() + ", " + currentLocation.getBlockZ());
    }

    /**
     * Traite les risques (raids, embuscades)
     */
    private void processRisks() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Vérifie les raids
        if (random.nextDouble() < RAID_CHANCE) {
            isAvailable = false;
            int raidDuration = random.nextInt(2, 6); // 2 à 6 heures

            // Redevient disponible après le raid
            new BukkitRunnable() {
                @Override
                public void run() {
                    isAvailable = true;
                    plugin.getPluginLogger().info("Black Market redevient disponible après le raid");
                }
            }.runTaskLater(plugin, 20 * 60 * 60 * raidDuration);

            plugin.getPluginLogger().info("Black Market subit un raid! Indisponible pour " + raidDuration + "h");

            // Notifie les joueurs proches
            notifyPlayersNearby("§c§lRAID EN COURS! §7Le marché noir ferme temporairement...");
        }
    }

    /**
     * Rafraîchit le stock du marché noir
     */
    private void refreshStock() {
        currentStock.clear();

        // Génère de nouveaux items selon la rareté
        generateStockItems();

        plugin.getPluginLogger().debug("Stock Black Market rafraîchi: " + currentStock.size() + " items");
    }

    /**
     * Génère les items en stock
     */
    private void generateStockItems() {
        // CORRECTION 1: Ajout du prix de base dans l'appel à addPossibleItem
        addPossibleItem(createSpecialItem("Cristal Obscur", Material.NETHER_STAR, "§5Cristal mystérieux", "§7Augmente les capacités criminelles"), 25);
        addPossibleItem(createSpecialItem("Lame Empoisonnée", Material.DIAMOND_SWORD, "§cLame enduite de poison", "§7Effet poison sur les ennemis"), 15);
        addPossibleItem(createSpecialItem("Cape d'Invisibilité", Material.LEATHER_CHESTPLATE, "§8Cape des voleurs", "§7Permet de se faufiler discrètement"), 30);
        addPossibleItem(createSpecialItem("Dynamite", Material.TNT, "§cExplosif artisanal", "§7Pour faire sauter les obstacles"), 10);
        addPossibleItem(createSpecialItem("Clé Squelette", Material.TRIPWIRE_HOOK, "§6Clé universelle", "§7Ouvre certaines portes verrouillées"), 8);

        // CORRECTION 1: Ajout du prix de base dans l'appel à addPossibleItem
        addPossibleItem(createSpecialItem("Contrat d'Assassinat", Material.PAPER, "§4Document dangereux", "§7Services... spéciaux"), 50, ReputationTier.INFAME);
        addPossibleItem(createSpecialItem("Armure Volée", Material.DIAMOND_CHESTPLATE, "§cArmure d'origine douteuse", "§7Protection maximale"), 20, ReputationTier.CRIMINEL, ReputationTier.INFAME);
    }

    /**
     * Ajoute un item possible au stock (sans restriction de réputation)
     */
    private void addPossibleItem(ItemStack item, int basePrice) {
        if (ThreadLocalRandom.current().nextDouble() < 0.6) { // 60% de chance d'apparaître
            currentStock.put(item, basePrice);
        }
    }

    /**
     * Ajoute un item possible au stock (avec restriction de réputation)
     */
    private void addPossibleItem(ItemStack item, int basePrice, ReputationTier... requiredTiers) {
        if (ThreadLocalRandom.current().nextDouble() < 0.4) { // 40% de chance pour items spéciaux
            // Marque l'item avec les restrictions
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.getLore();
                if (lore == null) lore = new ArrayList<>();
                lore.add("§8Réservé aux: " + Arrays.toString(requiredTiers));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            currentStock.put(item, basePrice);
        }
    }

    /**
     * Crée un item spécial pour le marché noir
     */
    private ItemStack createSpecialItem(String name, Material material, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6" + name);
            List<String> lore = new ArrayList<>(Arrays.asList(loreLines));
            lore.add("");
            lore.add("§8Marché Noir Exclusif");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }


    /**
     * Ouvre l'interface du marché noir pour un joueur
     */
    public void openBlackMarket(Player player) {
        if (!isAvailable) {
            player.sendMessage("§c§lMarché Noir fermé! §7Suite à un raid récent...");
            return;
        }

        UUID playerId = player.getUniqueId();
        ReputationTier reputation = reputationManager.getReputationTier(playerId);

        // Vérifie l'accès selon la réputation
        if (reputation == ReputationTier.EXEMPLAIRE) {
            player.sendMessage("§c§lAccès refusé! §7Votre réputation exemplaire vous interdit l'accès au marché noir.");
            return;
        }

        // Crée l'inventaire selon la réputation
        Inventory inv = Bukkit.createInventory(null, 54, "§8§lMarché Noir §7(" + reputation.getColoredTitle() + "§7)");

        populateInventoryForPlayer(inv, player, reputation);
        player.openInventory(inv);

        player.sendMessage("§8§l[Marché Noir] §7Bienvenue... §f" + reputation.getTitle() + "§7.");
    }

    /**
     * Remplit l'inventaire selon la réputation du joueur
     */
    private void populateInventoryForPlayer(Inventory inv, Player player, ReputationTier reputation) {
        double priceModifier = reputation.getBlackMarketPriceModifier();
        int slot = 0;

        for (Map.Entry<ItemStack, Integer> entry : currentStock.entrySet()) {
            if (slot >= 45) break; // Limite d'espace

            ItemStack item = entry.getKey().clone();
            int basePrice = entry.getValue();

            // Vérifie si le joueur peut voir cet item selon sa réputation
            if (canPlayerSeeItem(reputation, item)) {
                // Applique le modificateur de prix
                int finalPrice = Math.max(1, (int) Math.round(basePrice * (1.0 + priceModifier)));

                // Met à jour le lore avec le prix final
                updateItemPriceDisplay(item, finalPrice, priceModifier);

                inv.setItem(slot++, item);
            }
        }

        // Ajoute les informations de réputation
        addReputationInfo(inv, reputation);
    }

    /**
     * Vérifie si un joueur peut voir un item selon sa réputation
     */
    private boolean canPlayerSeeItem(ReputationTier playerTier, ItemStack item) {
        // Logique selon les spécifications de la table 10.2
        switch (playerTier) {
            case EXEMPLAIRE:
                return false; // Aucune offre
            case HONORABLE:
                return ThreadLocalRandom.current().nextDouble() < 0.1; // 0-1 offre parfois
            case RESPECTE:
                return ThreadLocalRandom.current().nextDouble() < 0.3; // Très peu d'offres
            case ORDINAIRE:
                return ThreadLocalRandom.current().nextDouble() < 0.5; // Offres limitées
            case SUSPECT:
                return ThreadLocalRandom.current().nextDouble() < 0.7; // Quelques offres
            case CRIMINEL:
                return ThreadLocalRandom.current().nextDouble() < 0.9; // La plupart des offres
            case INFAME:
                return true; // Toutes les offres + exclusives
            default:
                return false;
        }
    }

    /**
     * Met à jour l'affichage du prix sur l'item
     */
    private void updateItemPriceDisplay(ItemStack item, int finalPrice, double modifier) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore();
            if (lore == null) lore = new ArrayList<>();

            // Remplace ou ajoute le prix final
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Prix de base:")) {
                    lore.set(i, "§7Prix: §e" + finalPrice + " beacons");
                    break;
                }
            }

            if (modifier != 0) {
                String modifierText = modifier > 0 ? "§c+" : "§a";
                lore.add("§7Modificateur réputation: " + modifierText + (int)(modifier * 100) + "%");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    /**
     * Ajoute les informations de réputation dans l'inventaire
     */
    private void addReputationInfo(Inventory inv, ReputationTier reputation) {
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta meta = infoItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6Votre Statut: " + reputation.getColoredTitle());

            List<String> lore = new ArrayList<>();
            lore.add("§7Votre réputation affecte:");
            lore.add("§7• Nombre d'offres disponibles");
            lore.add("§7• Prix des items");
            lore.add("§7• Accès aux items exclusifs");
            lore.add("");
            lore.add(reputation.getEffectsDescription());

            meta.setLore(lore);
            infoItem.setItemMeta(meta);
        }

        inv.setItem(53, infoItem); // Coin supérieur droit
    }

    /**
     * Traite l'achat d'un item du marché noir
     */
    public boolean purchaseItem(Player player, ItemStack item) {
        UUID playerId = player.getUniqueId();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);

        // Trouve l'item dans le stock et son prix
        int price = findItemPrice(item, player);
        if (price == -1) {
            player.sendMessage("§cCet item n'est plus disponible!");
            return false;
        }

        // Vérifie si le joueur a assez de beacons
        if (playerData.getBeacons() < price) {
            player.sendMessage("§cVous n'avez pas assez de beacons! §7(Requis: " + price + ")");
            return false;
        }

        // Vérifie les risques d'arnaque
        if (ThreadLocalRandom.current().nextDouble() < SCAM_CHANCE) {
            // Arnaque! Le joueur paye mais ne reçoit rien ou reçoit un mauvais item
            playerData.removeBeacon(price);
            plugin.getPlayerDataManager().markDirty(playerId);

            player.sendMessage("§4§lARNAQUE! §cVous avez été dupé... Le marchand s'enfuit avec vos beacons!");
            reputationManager.modifyReputation(playerId, -2, "Victime d'arnaque Black Market");
            return false;
        }

        // Transaction réussie
        playerData.removeBeacon(price);
        plugin.getPlayerDataManager().markDirty(playerId);

        // Donne l'item
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage("§eInventaire plein! Item droppé au sol.");
        } else {
            player.getInventory().addItem(item);
        }

        // Impact sur la réputation
        reputationManager.handleBlackMarketTransaction(playerId, price);

        player.sendMessage("§a§lAchat réussi! §7Transaction discrète terminée...");
        return true;
    }

    /**
     * Trouve le prix d'un item dans le stock
     */
    private int findItemPrice(ItemStack searchItem, Player buyer) {
        for (Map.Entry<ItemStack, Integer> entry : currentStock.entrySet()) {
            // isSimilar() vérifie le type, la durabilité et les métadonnées comme le nom
            if (entry.getKey().isSimilar(searchItem)) {
                ReputationTier reputation = reputationManager.getReputationTier(buyer.getUniqueId());
                double modifier = reputation.getBlackMarketPriceModifier();
                return Math.max(1, (int) Math.round(entry.getValue() * (1.0 + modifier)));
            }
        }
        return -1; // Item non trouvé
    }

    /**
     * Obtient la localisation actuelle du marché noir
     */
    public Location getCurrentLocation() {
        return currentLocation != null ? currentLocation.clone() : null;
    }

    /**
     * Vérifie si le marché noir est disponible
     */
    public boolean isAvailable() {
        return isAvailable;
    }

    /**
     * Notifie les joueurs à proximité d'un événement
     */
    private void notifyPlayersNearby(String message) {
        if (currentLocation == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(currentLocation.getWorld()) &&
                    player.getLocation().distance(currentLocation) <= 100) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Force une relocalisation (commande admin)
     */
    public void forceRelocation() {
        relocateMarket();
        plugin.getPluginLogger().info("Relocalisation forcée du Black Market");
    }

    /**
     * Méthode de debug pour lister le stock actuel
     */
    public void debugStock() {
        plugin.getPluginLogger().info("=== STOCK BLACK MARKET ===");
        for (Map.Entry<ItemStack, Integer> entry : currentStock.entrySet()) {
            plugin.getPluginLogger().info("- " + entry.getKey().getItemMeta().getDisplayName() +
                    " : " + entry.getValue() + " beacons");
        }
        plugin.getPluginLogger().info("=========================");
    }
}