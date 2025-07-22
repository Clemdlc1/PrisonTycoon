package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire du Black Market amélioré - PNJ clandestin mobile
 * Gère les offres spécialisées selon la réputation, les déplacements et les risques
 */
public class BlackMarketManager implements Listener {

    private final PrisonTycoon plugin;
    private final ReputationManager reputationManager;

    // Localisation actuelle du marché noir
    private Location currentLocation;
    private boolean isAvailable;

    // Stock d'items actuels avec leurs types
    private final Map<ItemStack, BlackMarketItem> currentStock; // Item -> Données du marché

    // Emplacements possibles pour le marché noir
    private final List<Location> possibleLocations;

    // Configuration des déplacements
    private static final int RELOCATION_HOURS = 6; // Toutes les 6 heures
    private static final double RAID_CHANCE = 0.15; // 15% de chance de raid
    private static final double AMBUSH_CHANCE = 0.10; // 10% de chance d'embuscade
    private static final double SCAM_CHANCE = 0.05; // 5% de chance d'arnaque

    // Configuration des prix et probabilités
    private static final int BASE_PRICE_CRISTAL = 50; // Prix de base pour cristaux
    private static final int BASE_PRICE_BOOK = 100; // Prix de base pour livres
    private static final int BASE_PRICE_CONTAINER = 25; // Prix de base pour conteneurs
    private static final int BASE_PRICE_KEY = 15; // Prix de base pour clés

    public BlackMarketManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.reputationManager = plugin.getReputationManager();
        this.currentStock = new HashMap<>();
        this.possibleLocations = new ArrayList<>();
        this.isAvailable = true;

        initializePossibleLocations();
        relocateMarket(); // Positionnement initial
        startRelocationTask(); // Démarre les déplacements automatiques

        // Enregistre les événements pour les clics
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        plugin.getPluginLogger().info("§aBlackMarketManager amélioré initialisé avec " + possibleLocations.size() + " emplacements possibles.");
    }

    /**
     * Classe interne pour représenter un item du marché noir
     */
    private static class BlackMarketItem {
        private final int basePrice;
        private final String category;
        private final ReputationTier[] requiredTiers;

        public BlackMarketItem(int basePrice, String category, ReputationTier... requiredTiers) {
            this.basePrice = basePrice;
            this.category = category;
            this.requiredTiers = requiredTiers;
        }

        public int getBasePrice() { return basePrice; }
        public String getCategory() { return category; }
        public ReputationTier[] getRequiredTiers() { return requiredTiers; }

        public boolean canPlayerAccess(ReputationTier playerTier) {
            if (requiredTiers.length == 0) return true;
            for (ReputationTier tier : requiredTiers) {
                if (tier == playerTier) return true;
            }
            return false;
        }
    }

    /**
     * Initialise les emplacements possibles (à adapter selon votre serveur)
     */
    private void initializePossibleLocations() {
        // Exemples d'emplacements cachés (adaptez selon votre serveur)
        // possibleLocations.add(new Location(Bukkit.getWorld("world"), -1500, 70, 2300));
        // possibleLocations.add(new Location(Bukkit.getWorld("world"), 1200, 65, -800));
        // Pour le moment, ajout d'emplacements de test
        if (Bukkit.getWorld("world") != null) {
            possibleLocations.add(new Location(Bukkit.getWorld("world"), 0, 70, 0));
        }
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
        }.runTaskTimer(plugin, 0L, RELOCATION_HOURS * 3600L * 20L); // Conversion en ticks
    }

    /**
     * Relocalise le marché noir
     */
    private void relocateMarket() {
        if (possibleLocations.isEmpty()) {
            plugin.getPluginLogger().warning("Aucun emplacement configuré pour le Black Market!");
            return;
        }

        // Vérifie s'il y a un raid
        if (ThreadLocalRandom.current().nextDouble() < RAID_CHANCE) {
            triggerRaid();
            return;
        }

        // Nouvelle localisation aléatoire
        currentLocation = possibleLocations.get(ThreadLocalRandom.current().nextInt(possibleLocations.size()));
        isAvailable = true;

        // Rafraîchit le stock avec les nouveaux items spécialisés
        refreshStock();

        plugin.getPluginLogger().info("Black Market relocalisé: " +
                (int) currentLocation.getX() + ", " + (int) currentLocation.getY() + ", " + (int) currentLocation.getZ());

        // Notifie les joueurs proches
        notifyPlayersNearby("§8§l[MARKET] §7Un marchand mystérieux s'est installé dans les environs...");
    }

    /**
     * Déclenche un raid qui ferme temporairement le marché
     */
    private void triggerRaid() {
        isAvailable = false;
        currentLocation = null;
        currentStock.clear();

        plugin.getPluginLogger().info("RAID sur le Black Market! Indisponible pour " + RELOCATION_HOURS + "h");

        // Notifie les joueurs proches de la dernière position
        notifyPlayersNearby("§c§lRAID EN COURS! §7Le marché noir ferme temporairement...");
    }

    /**
     * Rafraîchit le stock du marché noir avec les nouveaux items spécialisés
     */
    private void refreshStock() {
        currentStock.clear();

        // Génère des cristaux vierges de niveaux variables
        generateCristals();

        // Génère des livres d'enchantement physiques
        generateEnchantmentBooks();

        // Génère des conteneurs de différents tiers
        generateContainers();

        // Génère des clés de différentes raretés
        generateKeys();

        plugin.getPluginLogger().debug("Stock Black Market rafraîchi: " + currentStock.size() + " items spécialisés");
    }

    /**
     * Génère des cristaux vierges pour le stock
     */
    private void generateCristals() {
        for (int level = 1; level <= 10; level++) {
            if (ThreadLocalRandom.current().nextDouble() < 0.4) { // 40% de chance par niveau
                ItemStack cristal = plugin.getCristalManager().createCristalVierge(level).toItemStack(
                        plugin.getCristalManager().getCristalUuidKey(),
                        plugin.getCristalManager().getCristalLevelKey(),
                        plugin.getCristalManager().getCristalTypeKey(),
                        plugin.getCristalManager().getCristalViergeKey()
                );

                int price = BASE_PRICE_CRISTAL * (level * level); // Prix exponentiel selon le niveau

                // Les cristaux de haut niveau nécessitent une meilleure réputation
                ReputationTier[] required = level > 7 ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME}
                        : level > 4 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME}
                        : new ReputationTier[0];

                currentStock.put(cristal, new BlackMarketItem(price, "Cristal", required));
            }
        }
    }

    /**
     * Génère des livres d'enchantement physiques pour le stock
     */
    private void generateEnchantmentBooks() {
        List<String> availableBooks = Arrays.asList(
                "bomber", "autosell", "beaconbreaker", "excavation",
                "incassable", "tunnel", "plusvalue", "tonnerre", "veinminer", "chaos"
        );

        EnchantmentBookManager bookManager = plugin.getEnchantmentBookManager();

        for (String bookId : availableBooks) {
            if (ThreadLocalRandom.current().nextDouble() < 0.3) { // 30% de chance par livre
                // CORRECTION : Récupérer l'objet EnchantmentBook à partir de son ID
                EnchantmentBookManager.EnchantmentBook enchantmentBook = bookManager.getEnchantmentBook(bookId);

                if (enchantmentBook != null) {
                    // CORRECTION : Utiliser l'objet EnchantmentBook pour créer l'item physique
                    ItemStack book = bookManager.createPhysicalEnchantmentBook(enchantmentBook);

                    if (book != null) {
                        int price = BASE_PRICE_BOOK;

                        // Certains livres sont plus rares et nécessitent plus de réputation
                        ReputationTier[] required = Arrays.asList("chaos", "tonnerre", "bomber").contains(bookId)
                                ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME}
                                : new ReputationTier[0];

                        currentStock.put(book, new BlackMarketItem(price, "Livre", required));
                    }
                }
            }
        }
    }

    /**
     * Génère des conteneurs pour le stock
     */
    private void generateContainers() {
        for (int tier = 1; tier <= 5; tier++) {
            if (ThreadLocalRandom.current().nextDouble() < 0.5) { // 50% de chance par tier
                ItemStack container = plugin.getContainerManager().createContainer(tier);
                int price = BASE_PRICE_CONTAINER * tier * tier; // Prix exponentiel selon le tier

                ReputationTier[] required = tier > 3 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME}
                        : new ReputationTier[0];

                currentStock.put(container, new BlackMarketItem(price, "Conteneur", required));
            }
        }
    }

    /**
     * Génère des clés pour le stock
     */
    private void generateKeys() {
        String[] keyTypes = {"Commune", "Peu Commune", "Rare", "Légendaire", "Cristal"};
        int[] keyPrices = {BASE_PRICE_KEY, BASE_PRICE_KEY * 2, BASE_PRICE_KEY * 5, BASE_PRICE_KEY * 15, BASE_PRICE_KEY * 50};

        for (int i = 0; i < keyTypes.length; i++) {
            if (ThreadLocalRandom.current().nextDouble() < (0.8 - i * 0.15)) { // Probabilité décroissante
                ItemStack key = plugin.getEnchantmentManager().createKey(keyTypes[i]);

                ReputationTier[] required = i >= 3 ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME}
                        : i >= 2 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME}
                        : new ReputationTier[0];

                currentStock.put(key, new BlackMarketItem(keyPrices[i], "Clé", required));
            }
        }
    }

    /**
     * Ouvre l'interface du marché noir pour un joueur
     */
    public void openBlackMarket(Player player) {
        if (!isAvailable) {
            player.sendMessage("§c§lMARCHÉ FERMÉ! §7Le marché noir est temporairement indisponible...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        if (currentLocation == null || player.getLocation().distance(currentLocation) > 10) {
            player.sendMessage("§c§lACCÈS REFUSÉ! §7Vous devez être proche du marchand...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        // Vérification de la réputation minimale
        ReputationTier playerReputation = reputationManager.getReputationTier(player.getUniqueId());
        if (playerReputation == ReputationTier.EXEMPLAIRE) {
            player.sendMessage("§c§lACCÈS REFUSÉ! §7Votre réputation est trop... propre.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, "§8§l⚫ MARCHÉ NOIR ⚫");

        // Remplissage avec du verre noir
        fillWithDarkGlass(gui);

        // Affichage des items disponibles selon la réputation
        displayAvailableItems(gui, player);

        // Boutons de contrôle
        setupBlackMarketButtons(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1.0f, 0.8f);

        // Chance d'événements spéciaux
        checkRandomEvents(player);
    }

    /**
     * Remplit l'interface avec du verre noir pour l'ambiance
     */
    private void fillWithDarkGlass(Inventory gui) {
        ItemStack darkGlass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = darkGlass.getItemMeta();
        meta.setDisplayName("§8▓");
        darkGlass.setItemMeta(meta);

        int[] glassSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53};
        for (int slot : glassSlots) {
            gui.setItem(slot, darkGlass);
        }
    }

    /**
     * Affiche les items disponibles selon la réputation du joueur
     */
    private void displayAvailableItems(Inventory gui, Player player) {
        ReputationTier playerReputation = reputationManager.getReputationTier(player.getUniqueId());
        int slot = 10;

        for (Map.Entry<ItemStack, BlackMarketItem> entry : currentStock.entrySet()) {
            if (slot > 43 || (slot + 1) % 9 == 0) { // Évite les bordures
                slot += 2;
                if (slot > 43) break;
            }

            BlackMarketItem marketItem = entry.getValue();

            if (marketItem.canPlayerAccess(playerReputation)) {
                ItemStack displayItem = entry.getKey().clone();
                ItemMeta meta = displayItem.getItemMeta();

                // Ajout du prix dans le lore
                List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§6💰 Prix: §e" + calculateFinalPrice(marketItem, playerReputation) + " beacons");
                lore.add("§7Catégorie: §f" + marketItem.getCategory());
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§a§l➤ CLIC pour acheter");

                meta.setLore(lore);
                displayItem.setItemMeta(meta);

                gui.setItem(slot, displayItem);
            }

            slot++;
        }
    }

    /**
     * Configure les boutons de contrôle du marché noir
     */
    private void setupBlackMarketButtons(Inventory gui, Player player) {
        // Bouton d'informations
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§e§lINFORMATIONS");
        List<String> infoLore = Arrays.asList(
                "§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§8⚫ Marché Noir ⚫",
                "§7Commerce d'objets rares et dangereux",
                "",
                "§cAttention: §7Transactions à vos risques",
                "§7Votre réputation: §f" + reputationManager.getReputationTier(player.getUniqueId()).getColoredTitle(),
                "§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        );
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        gui.setItem(49, infoItem);

        // Bouton de fermeture
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c§lFERMER");
        closeItem.setItemMeta(closeMeta);
        gui.setItem(53, closeItem);
    }

    /**
     * Calcule le prix final selon la réputation
     */
    private int calculateFinalPrice(BlackMarketItem item, ReputationTier reputation) {
        double modifier = reputation.getBlackMarketPriceModifier();
        return Math.max(1, (int) Math.round(item.getBasePrice() * (1.0 + modifier)));
    }

    /**
     * Vérifie les événements aléatoires lors de l'ouverture
     */
    private void checkRandomEvents(Player player) {
        double rand = ThreadLocalRandom.current().nextDouble();

        if (rand < AMBUSH_CHANCE) {
            player.sendMessage("§c§l⚔ EMBUSCADE! §7Des gardes vous ont repéré...");
            player.damage(2.0);
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.8f);
        } else if (rand < SCAM_CHANCE) {
            player.sendMessage("§c§l💸 ARNAQUE! §7Le marchand semble louche aujourd'hui...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
        }
    }

    /**
     * Gère les clics dans l'interface du marché noir
     */
    @EventHandler
    public void onBlackMarketClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!event.getView().getTitle().equals("§8§l⚫ MARCHÉ NOIR ⚫")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Gestion du bouton de fermeture
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // Gestion du bouton d'informations
        if (clicked.getType() == Material.BOOK) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // Gestion de l'achat d'items
        if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

        // Trouve l'item correspondant dans le stock
        BlackMarketItem marketItem = findMarketItem(clicked);
        if (marketItem == null) {
            player.sendMessage("§c§lERREUR! §7Item non disponible...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        // Tentative d'achat
        if (purchaseItem(player, clicked, marketItem)) {
            // Retire l'item du stock et rafraîchit l'affichage
            removeItemFromStock(clicked);

            // Rafraîchit l'interface
            player.closeInventory();
            openBlackMarket(player);
        }
    }

    /**
     * Trouve l'item du marché correspondant à l'item cliqué
     */
    private BlackMarketItem findMarketItem(ItemStack clicked) {
        // Retire le lore ajouté pour la comparaison
        ItemStack cleanedItem = clicked.clone();
        ItemMeta meta = cleanedItem.getItemMeta();
        if (meta != null && meta.getLore() != null) {
            List<String> lore = new ArrayList<>(meta.getLore());
            // Retire les lignes ajoutées par le marché noir
            lore.removeIf(line -> line.contains("Prix:") || line.contains("Catégorie:") ||
                    line.contains("▬") || line.contains("CLIC pour acheter"));
            meta.setLore(lore.isEmpty() ? null : lore);
            cleanedItem.setItemMeta(meta);
        }

        for (Map.Entry<ItemStack, BlackMarketItem> entry : currentStock.entrySet()) {
            if (entry.getKey().isSimilar(cleanedItem)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Retire un item du stock
     */
    private void removeItemFromStock(ItemStack item) {
        currentStock.entrySet().removeIf(entry -> entry.getKey().isSimilar(item));
    }

    /**
     * Tente d'acheter un item
     */
    private boolean purchaseItem(Player player, ItemStack item, BlackMarketItem marketItem) {
        UUID playerId = player.getUniqueId();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        ReputationTier reputation = reputationManager.getReputationTier(playerId);

        // Vérification de l'accès
        if (!marketItem.canPlayerAccess(reputation)) {
            player.sendMessage("§c§lACCÈS REFUSÉ! §7Votre réputation n'est pas suffisante...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return false;
        }

        // Calcul du prix final
        int finalPrice = calculateFinalPrice(marketItem, reputation);

        // Vérification des fonds
        if (playerData.getBeacons() < finalPrice) {
            player.sendMessage("§c§lFONDS INSUFFISANTS! §7Il vous faut §e" + finalPrice + " beacons§7.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return false;
        }

        // Déduction des beacons
        playerData.removeBeacon(finalPrice);

        // Nettoyage de l'item (retire le lore du marché)
        ItemStack cleanItem = item.clone();
        ItemMeta meta = cleanItem.getItemMeta();
        if (meta != null && meta.getLore() != null) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.removeIf(line -> line.contains("Prix:") || line.contains("Catégorie:") ||
                    line.contains("▬") || line.contains("CLIC pour acheter"));
            meta.setLore(lore.isEmpty() ? null : lore);
            cleanItem.setItemMeta(meta);
        }

        // Tentative d'ajout à l'inventaire
        if (player.getInventory().firstEmpty() == -1) {
            // Inventaire plein, drop au sol
            player.getWorld().dropItemNaturally(player.getLocation(), cleanItem);
            player.sendMessage("§6§lTRANSACTION RÉUSSIE! §7Item droppé au sol (inventaire plein).");
        } else {
            player.getInventory().addItem(cleanItem);
            player.sendMessage("§6§lTRANSACTION RÉUSSIE! §7Transaction discrète terminée...");
        }

        // Impact sur la réputation
        reputationManager.handleBlackMarketTransaction(playerId, finalPrice);

        // Sons et effets
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);

        return true;
    }

    /**
     * Trouve le prix d'un item dans le stock
     */
    private int findItemPrice(ItemStack searchItem, Player buyer) {
        BlackMarketItem marketItem = findMarketItem(searchItem);
        if (marketItem == null) return -1;

        ReputationTier reputation = reputationManager.getReputationTier(buyer.getUniqueId());
        return calculateFinalPrice(marketItem, reputation);
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
        plugin.getPluginLogger().info("Relocalisation forcée du Black Market amélioré");
    }

    /**
     * Méthode de debug pour lister le stock actuel
     */
    public void debugStock() {
        plugin.getPluginLogger().info("=== STOCK BLACK MARKET AMÉLIORÉ ===");
        for (Map.Entry<ItemStack, BlackMarketItem> entry : currentStock.entrySet()) {
            BlackMarketItem item = entry.getValue();
            plugin.getPluginLogger().info("- " + entry.getKey().getItemMeta().getDisplayName() +
                    " [" + item.getCategory() + "] : " + item.getBasePrice() + " beacons");
        }
        plugin.getPluginLogger().info("=================================");
    }
}