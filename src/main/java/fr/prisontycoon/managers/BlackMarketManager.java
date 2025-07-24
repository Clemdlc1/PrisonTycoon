package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire du Black Market amélioré avec PNJ, animations et configuration
 * Version complète avec gestion des événements et système d'achat unique
 */
public class BlackMarketManager {

    // Configuration des déplacements et événements
    private static final int RELOCATION_HOURS = 6; // Toutes les 6 heures
    private static final double RAID_CHANCE = 0.15; // 15% de chance de raid
    private static final double AMBUSH_CHANCE = 0.10; // 10% de chance d'embuscade
    private static final double SCAM_CHANCE = 0.05; // 5% de chance d'arnaque
    private static final int RAID_DURATION_HOURS = 2; // Durée d'un raid
    // Configuration des prix
    private static final int BASE_PRICE_CRISTAL = 50;
    private static final int BASE_PRICE_BOOK = 100;
    private static final int BASE_PRICE_CONTAINER = 25;
    private static final int BASE_PRICE_KEY = 15;
    private final PrisonTycoon plugin;
    private final ReputationManager reputationManager;
    // Stock d'items actuels avec leurs types
    private final Map<ItemStack, BlackMarketItem> currentStock;
    // Système d'achat unique par joueur
    private final Map<UUID, Set<String>> playerPurchases; // UUID joueur -> Set des IDs d'items achetés
    // Emplacements possibles pour le marché noir (chargés depuis config)
    private final List<BlackMarketLocation> possibleLocations;
    // PNJ du marché noir
    private Villager blackMarketNPC;
    private Location currentLocation;
    private boolean isAvailable;
    private MarketState currentState;

    public BlackMarketManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.reputationManager = plugin.getReputationManager();
        this.currentStock = new HashMap<>();
        this.playerPurchases = new HashMap<>();
        this.possibleLocations = new ArrayList<>();
        this.isAvailable = true;
        this.currentState = MarketState.AVAILABLE;

        loadConfigurationLocations();
        relocateMarket(); // Positionnement initial
        startRelocationTask(); // Démarre les déplacements automatiques

        plugin.getPluginLogger().info("§aBlackMarketManager amélioré initialisé avec " + possibleLocations.size() + " emplacements possibles.");
    }

    /**
     * Charge les emplacements depuis la configuration
     */
    private void loadConfigurationLocations() {
        ConfigurationSection blackMarketSection = plugin.getConfig().getConfigurationSection("black-market");
        if (blackMarketSection == null) {
            plugin.getPluginLogger().warning("§cAucune section 'black-market' trouvée dans la config! Création de la configuration par défaut...");
            createDefaultConfiguration();
            return;
        }

        ConfigurationSection locationsSection = blackMarketSection.getConfigurationSection("locations");
        if (locationsSection == null) {
            plugin.getPluginLogger().warning("§cAucune section 'locations' trouvée dans la config black-market!");
            return;
        }

        for (String locationKey : locationsSection.getKeys(false)) {
            ConfigurationSection locationSection = locationsSection.getConfigurationSection(locationKey);
            if (locationSection == null) continue;

            try {
                String worldName = locationSection.getString("world");
                double x = locationSection.getDouble("x");
                double y = locationSection.getDouble("y");
                double z = locationSection.getDouble("z");
                String name = locationSection.getString("name", locationKey);
                double dangerLevel = locationSection.getDouble("danger-level", 0.3);

                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getPluginLogger().warning("§cMonde '" + worldName + "' non trouvé pour l'emplacement '" + locationKey + "'");
                    continue;
                }

                Location location = new Location(world, x, y, z);
                possibleLocations.add(new BlackMarketLocation(location, name, dangerLevel));
                plugin.getPluginLogger().debug("Emplacement chargé: " + name + " (" + worldName + ")");

            } catch (Exception e) {
                plugin.getPluginLogger().warning("§cErreur lors du chargement de l'emplacement '" + locationKey + "': " + e.getMessage());
            }
        }

        plugin.getPluginLogger().info("§a" + possibleLocations.size() + " emplacements du marché noir chargés depuis la configuration.");
    }

    /**
     * Crée la configuration par défaut pour le marché noir
     */
    private void createDefaultConfiguration() {
        plugin.getConfig().set("black-market.relocation-hours", RELOCATION_HOURS);
        plugin.getConfig().set("black-market.raid-chance", RAID_CHANCE);
        plugin.getConfig().set("black-market.ambush-chance", AMBUSH_CHANCE);
        plugin.getConfig().set("black-market.scam-chance", SCAM_CHANCE);
        plugin.getConfig().set("black-market.raid-duration-hours", RAID_DURATION_HOURS);

        // Emplacements par défaut
        plugin.getConfig().set("black-market.locations.ruins.world", "world");
        plugin.getConfig().set("black-market.locations.ruins.x", -1500);
        plugin.getConfig().set("black-market.locations.ruins.y", 70);
        plugin.getConfig().set("black-market.locations.ruins.z", 2300);
        plugin.getConfig().set("black-market.locations.ruins.name", "§8Ruines Abandonées");
        plugin.getConfig().set("black-market.locations.ruins.danger-level", 0.4);

        plugin.getConfig().set("black-market.locations.cave.world", "world");
        plugin.getConfig().set("black-market.locations.cave.x", 1200);
        plugin.getConfig().set("black-market.locations.cave.y", 45);
        plugin.getConfig().set("black-market.locations.cave.z", -800);
        plugin.getConfig().set("black-market.locations.cave.name", "§2Grotte Cachée");
        plugin.getConfig().set("black-market.locations.cave.danger-level", 0.2);

        plugin.getConfig().set("black-market.locations.warehouse.world", "world");
        plugin.getConfig().set("black-market.locations.warehouse.x", 800);
        plugin.getConfig().set("black-market.locations.warehouse.y", 65);
        plugin.getConfig().set("black-market.locations.warehouse.z", 1500);
        plugin.getConfig().set("black-market.locations.warehouse.name", "§6Entrepôt Désaffecté");
        plugin.getConfig().set("black-market.locations.warehouse.danger-level", 0.6);

        plugin.saveConfig();
        plugin.getPluginLogger().info("§aConfiguration par défaut du marché noir créée.");
    }

    /**
     * Démarre la tâche de relocalisation automatique
     */
    private void startRelocationTask() {
        long relocHours = plugin.getConfig().getLong("black-market.relocation-hours", RELOCATION_HOURS);

        new BukkitRunnable() {
            @Override
            public void run() {
                relocateMarket();
            }
        }.runTaskTimer(plugin, 0L, relocHours * 3600L * 20L); // Conversion en ticks
    }

    /**
     * Relocalise le marché noir vers un nouvel emplacement
     */
    private void relocateMarket() {
        if (possibleLocations.isEmpty()) {
            plugin.getPluginLogger().warning("§cAucun emplacement disponible pour relocaliser le marché noir!");
            return;
        }

        plugin.getPluginLogger().info("Relocalisation du marché noir en cours...");

        // Met le marché en état de relocalisation
        currentState = MarketState.RELOCATING;
        isAvailable = false;

        // Supprime l'ancien PNJ s'il existe et attend avant de créer le nouveau
        if (blackMarketNPC != null && !blackMarketNPC.isDead()) {
            // Animation de disparition
            playNPCAnimation(blackMarketNPC, "disappear");

            // Crée une référence finale pour l'utiliser dans la tâche asynchrone
            final Villager oldNPC = blackMarketNPC;
            blackMarketNPC = null; // Réinitialise immédiatement la référence

            new BukkitRunnable() {
                @Override
                public void run() {
                    // Supprime définitivement l'ancien PNJ
                    oldNPC.remove();

                    // Attend un tick supplémentaire pour s'assurer que la suppression est effective
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Maintenant on peut créer le nouveau marché en toute sécurité
                            createNewMarketLocation();
                        }
                    }.runTaskLater(plugin, 1L);
                }
            }.runTaskLater(plugin, 40L); // 2 secondes pour l'animation
        } else {
            // Pas d'ancien PNJ, on peut créer directement le nouveau marché
            createNewMarketLocation();
        }
    }

    /**
     * Crée le nouveau marché à un nouvel emplacement
     */
    private void createNewMarketLocation() {
        // Vérifie s'il y a un raid
        double raidChance = plugin.getConfig().getDouble("black-market.raid-chance", RAID_CHANCE);
        if (ThreadLocalRandom.current().nextDouble() < raidChance) {
            triggerRaid();
            return;
        }

        // Nouvelle localisation aléatoire
        BlackMarketLocation chosenLocation = possibleLocations.get(
                ThreadLocalRandom.current().nextInt(possibleLocations.size())
        );
        currentLocation = chosenLocation.location();
        isAvailable = true;
        currentState = MarketState.AVAILABLE;

        // Crée le nouveau PNJ
        spawnBlackMarketNPC(chosenLocation);

        // Rafraîchit le stock avec les nouveaux items spécialisés
        refreshStock();

        plugin.getPluginLogger().info("Black Market relocalisé: " + chosenLocation.name() +
                " [" + (int) currentLocation.getX() + ", " + (int) currentLocation.getY() +
                ", " + (int) currentLocation.getZ() + "]");

        // Notifie les joueurs proches
        notifyPlayersNearby("§8§l[MARKET] §7Un marchand mystérieux s'est installé près de " +
                chosenLocation.name() + "...");
    }

    /**
     * Crée le PNJ du marché noir
     */
    private void spawnBlackMarketNPC(BlackMarketLocation location) {
        if (currentLocation == null || currentLocation.getWorld() == null) return;

        blackMarketNPC = (Villager) currentLocation.getWorld().spawnEntity(currentLocation, EntityType.VILLAGER);
        blackMarketNPC.setCustomName("§8§l⚫ Marchand Noir ⚫");
        blackMarketNPC.setCustomNameVisible(true);
        blackMarketNPC.setAI(false);
        blackMarketNPC.setInvulnerable(true);
        blackMarketNPC.setSilent(false);
        blackMarketNPC.setProfession(Villager.Profession.NITWIT);

        // Animation d'apparition
        playNPCAnimation(blackMarketNPC, "appear");

        plugin.getPluginLogger().debug("PNJ du marché noir créé à " + location.name());
    }

    /**
     * Joue les animations du PNJ selon les événements
     */
    private void playNPCAnimation(Villager npc, String animationType) {
        if (npc == null || npc.isDead()) return;

        switch (animationType) {
            case "appear" -> {
                // Particules et sons d'apparition
                npc.getWorld().spawnParticle(Particle.SMOKE, npc.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
                npc.getWorld().playSound(npc.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.8f);

                // Animation de rotation lente
                new BukkitRunnable() {
                    private int ticks = 0;

                    @Override
                    public void run() {
                        if (npc.isDead()) {
                            cancel();
                            return;
                        }

                        if (ticks < 40) { // 2 secondes
                            npc.getLocation().setYaw(npc.getLocation().getYaw() + 9f);
                            npc.teleport(npc.getLocation());
                            ticks++;
                        } else {
                            cancel();
                        }
                    }
                }.runTaskTimer(plugin, 5L, 1L);
            }

            case "disappear" -> {
                npc.getWorld().spawnParticle(Particle.SMOKE, npc.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
                npc.getWorld().playSound(npc.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            }

            case "raid" -> {
                npc.getWorld().spawnParticle(org.bukkit.Particle.LAVA, npc.getLocation().add(0, 1, 0), 20, 1, 1, 1, 0.1);
                npc.getWorld().playSound(npc.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.6f);
                npc.setCustomName("§c§l🚨 RAID EN COURS 🚨");
            }

            case "trade" -> {
                npc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, npc.getLocation().add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0.1);
                npc.getWorld().playSound(npc.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1.0f);
            }

            case "scam" -> {
                npc.getWorld().spawnParticle(Particle.SMOKE, npc.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                npc.getWorld().playSound(npc.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);
                npc.setCustomName("§c§l💸 Marchand Louche 💸");

                // Remet le nom normal après 5 secondes
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!npc.isDead()) {
                            npc.setCustomName("§8§l⚫ Marchand Noir ⚫");
                        }
                    }
                }.runTaskLater(plugin, 100L);
            }
        }
    }

    /**
     * Déclenche un raid qui ferme temporairement le marché
     */
    private void triggerRaid() {
        isAvailable = false;
        currentState = MarketState.RAIDED;
        currentLocation = null;
        currentStock.clear();

        if (blackMarketNPC != null && !blackMarketNPC.isDead()) {
            playNPCAnimation(blackMarketNPC, "raid");

            // Supprime le PNJ après l'animation
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!blackMarketNPC.isDead()) {
                        blackMarketNPC.remove();
                    }
                }
            }.runTaskLater(plugin, 60L); // 3 secondes
        }

        int raidDuration = plugin.getConfig().getInt("black-market.raid-duration-hours", RAID_DURATION_HOURS);
        plugin.getPluginLogger().info("RAID sur le Black Market! Indisponible pour " + raidDuration + "h");

        // Notifie les joueurs
        notifyPlayersNearby("§c§l🚨 RAID EN COURS! §7Le marché noir ferme temporairement...");

        // Programme la réouverture après le raid
        new BukkitRunnable() {
            @Override
            public void run() {
                currentState = MarketState.AVAILABLE;
                relocateMarket();
                notifyPlayersNearby("§a§l✅ RAID TERMINÉ! §7Le marché noir reprend ses activités...");
            }
        }.runTaskLater(plugin, raidDuration * 3600L * 20L);
    }

    /**
     * Rafraîchit le stock du marché noir
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
                String itemId = "cristal_" + level + "_" + UUID.randomUUID().toString().substring(0, 8);

                // Les cristaux de haut niveau nécessitent une meilleure réputation
                ReputationTier[] required = level > 7 ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME}
                        : level > 4 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME}
                        : new ReputationTier[0];

                currentStock.put(cristal, new BlackMarketItem(itemId, price, "Cristal", required));
            }
        }
    }

    /**
     * Génère des livres d'enchantement pour le stock
     */
    private void generateEnchantmentBooks() {
        EnchantmentBookManager bookManager = plugin.getEnchantmentBookManager();
        if (bookManager == null) return;


        Collection<EnchantmentBookManager.EnchantmentBook> allBooks = bookManager.getAllEnchantmentBooks();


        for (EnchantmentBookManager.EnchantmentBook book : allBooks) {
            if (ThreadLocalRandom.current().nextDouble() < 0.3) { // 30% de chance par livre
                String bookId = book.getId(); // On récupère l'ID directement depuis l'objet livre

                ItemStack bookItem = bookManager.createPhysicalEnchantmentBook(book);

                if (bookItem != null) {
                    int price = BASE_PRICE_BOOK * (book.getMaxLevel() * 2);
                    String itemId = "book_" + bookId + "_" + UUID.randomUUID().toString().substring(0, 8);

                    // Les livres rares nécessitent une meilleure réputation
                    ReputationTier[] required = book.getMaxLevel() > 3 ?
                            new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME} :
                            new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME};

                    currentStock.put(bookItem, new BlackMarketItem(itemId, price, "Livre", required));
                }
            }
        }
    }

    /**
     * Génère des conteneurs pour le stock
     */
    private void generateContainers() {
        ContainerManager containerManager = plugin.getContainerManager();
        if (containerManager == null) return;

        for (int tier = 1; tier <= 5; tier++) {
            if (ThreadLocalRandom.current().nextDouble() < (0.6 - tier * 0.1)) { // Probabilité décroissante
                ItemStack container = containerManager.createContainer(tier);
                if (container != null) {
                    int price = BASE_PRICE_CONTAINER * (tier * tier * 2);
                    String itemId = "container_" + tier + "_" + UUID.randomUUID().toString().substring(0, 8);

                    // Les conteneurs de haut tier nécessitent une meilleure réputation
                    ReputationTier[] required = tier >= 4 ?
                            new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME} :
                            tier >= 3 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME}
                                    : new ReputationTier[0];

                    currentStock.put(container, new BlackMarketItem(itemId, price, "Conteneur", required));
                }
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
                if (key != null) {
                    String itemId = "key_" + keyTypes[i].toLowerCase().replace(" ", "_") + "_" + UUID.randomUUID().toString().substring(0, 8);

                    ReputationTier[] required = i >= 3 ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME}
                            : i >= 2 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME}
                            : new ReputationTier[0];

                    currentStock.put(key, new BlackMarketItem(itemId, keyPrices[i], "Clé", required));
                }
            }
        }
    }

    /**
     * Ouvre l'interface du marché noir pour un joueur
     */
    public void openBlackMarket(Player player) {
        if (!isAvailable || currentState != MarketState.AVAILABLE) {
            player.sendMessage("§c§lMARCHÉ FERMÉ! §7Le marché noir est temporairement indisponible... " + currentState.getDisplay());
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

        // Vérifie les événements aléatoires avant l'ouverture
        checkRandomEvents(player);

        // Crée l'interface
        Inventory gui = Bukkit.createInventory(null, 54, "§8§l⚫ MARCHÉ NOIR ⚫");

        // Remplir avec des vitres noires
        ItemStack blackPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneMeta = blackPane.getItemMeta();
        paneMeta.setDisplayName(" ");
        blackPane.setItemMeta(paneMeta);

        for (int i = 0; i < 54; i++) {
            gui.setItem(i, blackPane);
        }

        // Ajoute les items disponibles
        setupBlackMarketItems(gui, player, playerReputation);

        // Ajoute les boutons de contrôle
        setupBlackMarketButtons(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 0.7f);

        // Animation du PNJ
        if (blackMarketNPC != null && !blackMarketNPC.isDead()) {
            playNPCAnimation(blackMarketNPC, "trade");
        }
    }

    /**
     * Configure les items du marché noir dans l'interface
     */
    private void setupBlackMarketItems(Inventory gui, Player player, ReputationTier playerReputation) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int slot = 0;

        Set<String> playerPurchasedItems = playerPurchases.getOrDefault(player.getUniqueId(), new HashSet<>());

        for (Map.Entry<ItemStack, BlackMarketItem> entry : currentStock.entrySet()) {
            if (slot >= slots.length) break;

            ItemStack item = entry.getKey();
            BlackMarketItem marketItem = entry.getValue();

            // Vérifie si le joueur peut accéder à cet item (réputation)
            if (!marketItem.canPlayerAccess(playerReputation)) continue;

            // Vérifie si le joueur a déjà acheté cet item
            boolean alreadyPurchased = playerPurchasedItems.contains(marketItem.itemId());

            // Clone l'item pour l'affichage
            ItemStack displayItem = item.clone();
            ItemMeta meta = displayItem.getItemMeta();

            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§6💰 Prix: §e" + calculateFinalPrice(marketItem, playerReputation) + " beacons");
                lore.add("§7Catégorie: §f" + marketItem.category());

                if (alreadyPurchased) {
                    lore.add("§c⚠ §lDÉJÀ ACHETÉ!");
                    lore.add("§7Vous ne pouvez acheter cet item qu'une fois");
                } else {
                    lore.add("§a§l➤ CLIC pour acheter");
                }

                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                meta.setLore(lore);

                // Griser l'item s'il est déjà acheté
                if (alreadyPurchased) {
                    displayItem.setType(Material.BARRIER);
                    meta.setDisplayName("§c§l" + (meta.getDisplayName() != null ? meta.getDisplayName() : item.getType().name()) + " §7(Déjà acheté)");
                }

                displayItem.setItemMeta(meta);
            }

            gui.setItem(slots[slot], displayItem);
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
                "§7État actuel: " + currentState.getDisplay(),
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
        return Math.max(1, (int) Math.round(item.basePrice() * (1.0 + modifier)));
    }

    /**
     * Vérifie les événements aléatoires lors de l'ouverture
     */
    private void checkRandomEvents(Player player) {
        double ambushChance = plugin.getConfig().getDouble("black-market.ambush-chance", AMBUSH_CHANCE);
        double scamChance = plugin.getConfig().getDouble("black-market.scam-chance", SCAM_CHANCE);
        double rand = ThreadLocalRandom.current().nextDouble();

        if (rand < ambushChance) {
            player.sendMessage("§c§l⚔ EMBUSCADE! §7Des gardes vous ont repéré...");
            player.damage(2.0);
            player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.8f);
        } else if (rand < scamChance) {
            player.sendMessage("§c§l💸 ARNAQUE! §7Le marchand semble louche aujourd'hui...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);

            // Animation du PNJ
            if (blackMarketNPC != null && !blackMarketNPC.isDead()) {
                playNPCAnimation(blackMarketNPC, "scam");
            }
        }
    }

    /**
     * Gère les clics dans l'interface du marché noir
     * Cette méthode est maintenant appelée par GUIListener
     */
    public void handleBlackMarketClick(Player player, ItemStack clicked) {


        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Gestion du bouton de fermeture
        if (clicked.getType() == Material.BARRIER && clicked.hasItemMeta() && clicked.getItemMeta().getDisplayName().equals("§c§lFERMER")) {
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
            player.sendMessage("§c§lERREUR! §7Item non disponible ou déjà acheté...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        // Vérifie si le joueur a déjà acheté cet item
        Set<String> playerPurchasedItems = playerPurchases.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        if (playerPurchasedItems.contains(marketItem.itemId())) {
            player.sendMessage("§c§l❌ ERREUR! §7Vous avez déjà acheté cet item!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        // Tentative d'achat
        if (purchaseItem(player, clicked, marketItem)) {
            // Marque l'item comme acheté par ce joueur
            playerPurchasedItems.add(marketItem.itemId());

            // Retire l'item du stock pour tout le monde
            removeItemFromStock(clicked);

            // Rafraîchit l'interface pour le joueur
            // Fermer et rouvrir est le moyen le plus simple de mettre à jour l'état "déjà acheté"
            player.closeInventory();
            openBlackMarket(player);
        }
    }

    /**
     * Trouve l'item du marché correspondant à l'item cliqué
     */
    private BlackMarketItem findMarketItem(ItemStack clicked) {
        // Pour les items barrières (déjà achetés), on ne peut pas les acheter
        if (clicked.getType() == Material.BARRIER) {
            return null;
        }

        // Compare les items de base (sans le lore du marché noir)
        for (Map.Entry<ItemStack, BlackMarketItem> entry : currentStock.entrySet()) {
            ItemStack marketItem = entry.getKey();

            // Compare le type de matériau et le nom de base
            if (marketItem.getType() == clicked.getType()) {
                // Pour les items spéciaux comme les cristaux, livres, conteneurs
                ItemMeta marketMeta = marketItem.getItemMeta();
                ItemMeta clickedMeta = clicked.getItemMeta();

                if (marketMeta != null && clickedMeta != null) {
                    // Compare les noms de base (en ignorant les modifications du marché)
                    String marketName = marketMeta.getDisplayName();
                    String clickedName = clickedMeta.getDisplayName();

                    // Pour les conteneurs
                    if (plugin.getContainerManager().isContainer(marketItem)) {
                        if (plugin.getContainerManager().isContainer(clicked)) {
                            // Compare les tiers des conteneurs
                            return entry.getValue();
                        }
                    }
                    // Pour les livres d'enchantement
                    else if (marketItem.getType() == Material.ENCHANTED_BOOK || marketItem.getType() == Material.BOOK) {
                        if (marketName != null && clickedName != null) {
                            // Compare les noms de base des livres
                            String baseMarketName = marketName.split("§")[0]; // Prendre la partie avant les codes couleur
                            String baseClickedName = clickedName.split("§")[0];
                            if (baseMarketName.equals(baseClickedName)) {
                                return entry.getValue();
                            }
                        }
                    }
                    // Pour les cristaux et autres items
                    else if (marketName != null && marketName.equals(clickedName)) {
                        return entry.getValue();
                    }
                }
            }
        }

        return null;
    }

    /**
     * Tentative d'achat d'un item
     */
    private boolean purchaseItem(Player player, ItemStack clicked, BlackMarketItem marketItem) {
        UUID playerId = player.getUniqueId();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        ReputationTier reputation = reputationManager.getReputationTier(playerId);

        // Calcule le prix final
        int finalPrice = calculateFinalPrice(marketItem, reputation);

        // Vérifie si le joueur a assez de beacons
        if (playerData.getBeacons() < finalPrice) {
            player.sendMessage("§c§l💸 FONDS INSUFFISANTS! §7Vous avez besoin de §e" + finalPrice + " beacons§7.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return false;
        }

        // Chance d'arnaque
        double scamChance = plugin.getConfig().getDouble("black-market.scam-chance", SCAM_CHANCE);
        if (ThreadLocalRandom.current().nextDouble() < scamChance) {
            // Le joueur paye mais ne reçoit rien (ou reçoit un mauvais item)
            playerData.removeBeacon(finalPrice);
            plugin.getPlayerDataManager().markDirty(playerId);

            double scamType = ThreadLocalRandom.current().nextDouble();
            if (scamType < 0.5) {
                // Ne reçoit rien
                player.sendMessage("§c§l💸 ARNAQUÉ! §7Le marchand a pris votre argent et a disparu!");
            } else {
                // Reçoit un item pourri
                ItemStack scamItem = new ItemStack(Material.ROTTEN_FLESH);
                ItemMeta scamMeta = scamItem.getItemMeta();
                scamMeta.setDisplayName("§c§lArticle Défectueux");
                scamMeta.setLore(Arrays.asList("§7Vous vous êtes fait arnaquer...", "§cLa prochaine fois, soyez plus prudent!"));
                scamItem.setItemMeta(scamMeta);

                if (player.getInventory().firstEmpty() == -1) {
                    player.getWorld().dropItemNaturally(player.getLocation(), scamItem);
                } else {
                    player.getInventory().addItem(scamItem);
                }

                player.sendMessage("§c§l💸 ARNAQUÉ! §7Vous avez reçu un article défectueux!");
            }

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);

            // Animation du PNJ
            if (blackMarketNPC != null && !blackMarketNPC.isDead()) {
                playNPCAnimation(blackMarketNPC, "scam");
            }

            return true; // L'achat a techniquement eu lieu
        }

        // Retire les beacons
        playerData.removeBeacon(finalPrice);
        plugin.getPlayerDataManager().markDirty(playerId);

        // Donne l'item au joueur (version nettoyée sans le lore du marché)
        ItemStack cleanItem = clicked.clone();
        ItemMeta meta = cleanItem.getItemMeta();
        if (meta != null && meta.getLore() != null) {
            List<String> lore = new ArrayList<>(meta.getLore());
            // Retire les lignes ajoutées par le marché noir
            lore.removeIf(line -> line.contains("Prix:") || line.contains("Catégorie:") ||
                    line.contains("▬") || line.contains("CLIC pour acheter") || line.contains("DÉJÀ ACHETÉ"));
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

        // Animation du PNJ
        if (blackMarketNPC != null && !blackMarketNPC.isDead()) {
            playNPCAnimation(blackMarketNPC, "trade");
        }

        return true;
    }

    /**
     * Retire un item du stock
     */
    private void removeItemFromStock(ItemStack item) {
        BlackMarketItem marketItem = findMarketItem(item);
        if (marketItem != null) {
            currentStock.entrySet().removeIf(entry -> entry.getValue().equals(marketItem));
        }
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
        return isAvailable && currentState == MarketState.AVAILABLE;
    }

    /**
     * Obtient l'état actuel du marché
     */
    public MarketState getCurrentState() {
        return currentState;
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
     * Recharge la configuration
     */
    public void reloadConfiguration() {
        possibleLocations.clear();
        loadConfigurationLocations();
        plugin.getPluginLogger().info("Configuration du marché noir rechargée.");
    }

    /**
     * Obtient les statistiques d'achat d'un joueur
     */
    public Set<String> getPlayerPurchases(UUID playerId) {
        return new HashSet<>(playerPurchases.getOrDefault(playerId, new HashSet<>()));
    }

    /**
     * Nettoie les données d'un joueur (pour suppression de compte)
     */
    public void cleanupPlayerData(UUID playerId) {
        playerPurchases.remove(playerId);
    }

    /**
     * Méthode de debug pour lister le stock actuel
     */
    public void debugStock() {
        plugin.getPluginLogger().info("=== STOCK BLACK MARKET AMÉLIORÉ ===");
        plugin.getPluginLogger().info("État: " + currentState.getDisplay());
        plugin.getPluginLogger().info("Localisation: " + (currentLocation != null ?
                currentLocation.getWorld().getName() + " " + currentLocation.getBlockX() + "," + currentLocation.getBlockY() + "," + currentLocation.getBlockZ()
                : "Aucune"));

        for (Map.Entry<ItemStack, BlackMarketItem> entry : currentStock.entrySet()) {
            BlackMarketItem item = entry.getValue();
            plugin.getPluginLogger().info("- " + entry.getKey().getItemMeta().getDisplayName() +
                    " [" + item.category() + "] : " + item.basePrice() + " beacons (ID: " + item.itemId() + ")");
        }
        plugin.getPluginLogger().info("=== " + currentStock.size() + " items au total ===");
    }

    // États du marché
    public enum MarketState {
        AVAILABLE("§a✅ Disponible"),
        RAIDED("§c🚨 Raid en cours"),
        RELOCATING("§e⚡ Relocalisation"),
        HIDDEN("§8👁 Caché");

        private final String display;

        MarketState(String display) {
            this.display = display;
        }

        public String getDisplay() {
            return display;
        }
    }

    /**
     * Classe pour représenter un emplacement du marché noir
     *
     * @param dangerLevel 0.0 à 1.0
     */
        private record BlackMarketLocation(Location location, String name, double dangerLevel) {

        @Override
        public Location location() {
                return location.clone();
            }
        }

    /**
     * Classe interne pour représenter un item du marché noir
     *
     * @param itemId ID unique pour chaque item
     */
        private record BlackMarketItem(String itemId, int basePrice, String category, ReputationTier... requiredTiers) {

        public boolean canPlayerAccess(ReputationTier playerTier) {
                if (requiredTiers.length == 0) return true;
                for (ReputationTier tier : requiredTiers) {
                    if (tier == playerTier) return true;
                }
                return false;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                BlackMarketItem that = (BlackMarketItem) obj;
                return Objects.equals(itemId, that.itemId);
            }

            @Override
            public int hashCode() {
                return Objects.hash(itemId);
            }
        }
}