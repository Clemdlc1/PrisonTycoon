package fr.prisontycoon.managers;

import com.google.gson.Gson;
import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.NpcAttribute;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.gui.GUIType;
import fr.prisontycoon.reputation.ReputationTier;
import fr.prisontycoon.utils.FancyNPCUtils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class BlackMarketManager {

    private static final int RELOCATION_HOURS = 6;
    private static final double RAID_CHANCE = 0.15;
    private static final double AMBUSH_CHANCE = 0.10;
    private static final double SCAM_CHANCE = 0.05;
    private static final int RAID_DURATION_HOURS = 2;
    private static final int BASE_PRICE_CRISTAL = 50;
    private static final int BASE_PRICE_BOOK = 100;
    private static final int BASE_PRICE_CONTAINER = 25;
    private static final int BASE_PRICE_KEY = 15;

    private final PrisonTycoon plugin;
    private final ReputationManager reputationManager;
    private final DatabaseManager databaseManager;
    private final Gson gson = new Gson();

    private final Map<ItemStack, BlackMarketItem> currentStock = new HashMap<>();
    private final List<BlackMarketLocation> possibleLocations = new ArrayList<>();
    private Npc blackMarketNPC;
    private Location currentLocation;
    private boolean isAvailable = true;
    private MarketState currentState = MarketState.AVAILABLE;
    private FancyNPCUtils npcUtils;


    public BlackMarketManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.reputationManager = plugin.getReputationManager();
        this.databaseManager = plugin.getDatabaseManager();
        this.npcUtils = new FancyNPCUtils(plugin);
        createPurchasesTable();
        loadConfigurationLocations();
        relocateMarket();
        startRelocationTask();
    }

    private void createPurchasesTable() {
        String query = """
                    CREATE TABLE IF NOT EXISTS black_market_purchases (
                        player_uuid VARCHAR(36),
                        item_id VARCHAR(255),
                        PRIMARY KEY (player_uuid, item_id)
                    );
                """;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create black_market_purchases table: " + e.getMessage());
        }
    }

    private void loadConfigurationLocations() {
        possibleLocations.clear();
        ConfigurationSection blackMarketSection = plugin.getConfig().getConfigurationSection("black-market");
        if (blackMarketSection == null) {
            createDefaultConfiguration();
            blackMarketSection = plugin.getConfig().getConfigurationSection("black-market");
            if(blackMarketSection == null) return;
        }
        ConfigurationSection locationsSection = blackMarketSection.getConfigurationSection("locations");
        if (locationsSection == null) return;

        for (String locationKey : locationsSection.getKeys(false)) {
            ConfigurationSection locationSection = locationsSection.getConfigurationSection(locationKey);
            if (locationSection == null) continue;
            try {
                // ... (chargement de x, y, z, yaw, pitch, isSitting inchangé) ...
                boolean isSitting = locationSection.getBoolean("is_sitting", false);

                // AJOUTÉ : Lecture des nouvelles options
                boolean turnToPlayer = locationSection.getBoolean("turn_to_player", false);
                int turnToPlayerDistance = locationSection.getInt("turn_to_player_distance", -1); // -1 = Défaut du plugin

                String name = locationSection.getString("name", locationKey);
                double dangerLevel = locationSection.getDouble("danger-level", 0.3);

                World world = Bukkit.getWorld(locationSection.getString("world"));
                if (world == null) {
                    // ...
                    continue;
                }

                double x = locationSection.getDouble("x");
                double y = locationSection.getDouble("y");
                double z = locationSection.getDouble("z");
                float yaw = (float) locationSection.getDouble("yaw", 0.0);
                float pitch = (float) locationSection.getDouble("pitch", 0.0);

                Location location = new Location(world, x, y, z, yaw, pitch);

                // MODIFIÉ : On passe les nouvelles informations au constructeur du record
                possibleLocations.add(new BlackMarketLocation(location, name, dangerLevel, isSitting, turnToPlayer, turnToPlayerDistance));

            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors du chargement de l'emplacement du marché noir: " + locationKey);
                e.printStackTrace();
            }
        }
    }

    private void createDefaultConfiguration() {
        plugin.getConfig().set("black-market.relocation-hours", RELOCATION_HOURS);
        String path = "black-market.locations.egout_principal.";
        plugin.getConfig().set(path + "name", "Égout Principal");
        plugin.getConfig().set(path + "world", "world");
        plugin.getConfig().set(path + "x", 100.0);
        plugin.getConfig().set(path + "y", 64.0);
        plugin.getConfig().set(path + "z", -250.0);
        plugin.getConfig().set(path + "yaw", 90.0); // Regarde vers l'Est
        plugin.getConfig().set(path + "pitch", 0.0); // Regarde droit devant
        plugin.getConfig().set(path + "is_sitting", true);
        plugin.getConfig().set(path + "turn_to_player", true);
        plugin.getConfig().set(path + "turn_to_player_distance", 10);// Le PNJ sera assis
        plugin.getConfig().set(path + "danger-level", 0.4);

        plugin.saveConfig();
        plugin.reloadConfig(); // S'assurer que les valeurs sont chargées
    }

    private void startRelocationTask() {
        long relocHours = plugin.getConfig().getLong("black-market.relocation-hours", RELOCATION_HOURS);
        new BukkitRunnable() {
            @Override
            public void run() {
                relocateMarket();
            }
        }.runTaskTimer(plugin, 0L, relocHours * 3600L * 20L);
    }

    public void relocateMarket() {
        if (currentState == MarketState.RELOCATING) {
            return;
        }
        if (possibleLocations.isEmpty()) {
            return;
        }
        currentState = MarketState.RELOCATING;
        isAvailable = false;
        if (blackMarketNPC != null) {
            playNPCAnimation(blackMarketNPC, "disappear");
            final Npc oldNPC = blackMarketNPC;
            new BukkitRunnable() {
                @Override
                public void run() {
                    npcUtils.removeNPC(oldNPC);
                    blackMarketNPC = null;
                    createNewMarketLocation();
                }
            }.runTaskLater(plugin, 40L);
        } else {
            createNewMarketLocation();
        }
    }

    private void createNewMarketLocation() {
        double raidChance = plugin.getConfig().getDouble("black-market.raid-chance", RAID_CHANCE);
        if (ThreadLocalRandom.current().nextDouble() < raidChance) {
            triggerRaid();
            return;
        }
        BlackMarketLocation chosenLocation = possibleLocations.get(ThreadLocalRandom.current().nextInt(possibleLocations.size()));
        currentLocation = chosenLocation.location();
        isAvailable = true;
        currentState = MarketState.AVAILABLE;
        spawnBlackMarketNPC(chosenLocation); // On passe l'objet complet
        refreshStock();
        notifyPlayersNearby("§8§l[MARKET] §7Un marchand mystérieux s'est installé près de " + chosenLocation.name() + "...");
    }


    // Dans BlackMarketManager.java

    private void spawnBlackMarketNPC(BlackMarketLocation chosenLocation) {
        if (chosenLocation == null || chosenLocation.location() == null || chosenLocation.location().getWorld() == null) return;

        final Location spawnLocation = chosenLocation.location(); // 'final' est nécessaire pour l'utiliser dans le Runnable

        if (blackMarketNPC != null) {
            npcUtils.removeNPC(blackMarketNPC);
            blackMarketNPC = null;
        }

        String npcId = "blackmarket";
        String displayName = "§8§l⚫ Marchand Noir ⚫";

        // ÉTAPE 1 : Créez le PNJ. Il peut apparaître brièvement au mauvais endroit.
        blackMarketNPC = npcUtils.createNPC(npcId, displayName, spawnLocation);

        if (blackMarketNPC != null) {
            // ÉTAPE 2 : Planifiez une tâche pour 1 tick plus tard afin de corriger la position et d'appliquer les attributs.
            // C'est le cœur de la solution.
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Vérifiez si le PNJ existe toujours (sécurité)
                    if (blackMarketNPC == null) {
                        return;
                    }

                    // **LA CORRECTION** : Forcez à nouveau la position du PNJ.
                    blackMarketNPC.getData().setLocation(spawnLocation);

                    // Appliquez la logique pour s'asseoir
                    if (chosenLocation.isSitting()) {
                        NpcAttribute poseAttribute = FancyNpcsPlugin.get().getAttributeManager().getAttributeByName(EntityType.PLAYER, "pose");
                        if (poseAttribute != null) {
                            blackMarketNPC.getData().addAttribute(poseAttribute, "sitting");
                        }
                    }

                    // Appliquez la logique pour regarder le joueur
                    if (chosenLocation.turnToPlayer()) {
                        blackMarketNPC.getData().setTurnToPlayer(true);
                        if (chosenLocation.turnToPlayerDistance() > 0) {
                            blackMarketNPC.getData().setTurnToPlayerDistance(chosenLocation.turnToPlayerDistance());
                        }
                    }

                    // Mettez à jour le PNJ pour tous les joueurs, ce qui appliquera la position corrigée et les attributs.
                    blackMarketNPC.updateForAll();

                    // Jouez l'animation de spawn APRÈS que le PNJ soit correctement positionné.
                    playNPCAnimation(blackMarketNPC, "appear");

                }
            }.runTaskLater(plugin, 1L); // Le délai de 1 tick est crucial.
        }
    }

    private void playNPCAnimation(Npc npc, String animationType) {
        if (npc == null || currentLocation == null) return;

        Location npcLocation = npc.getData().getLocation().clone();

        World world = npcLocation.getWorld();
        if (world == null) return;

        switch (animationType) {
            case "appear":
                world.spawnParticle(Particle.SMOKE, npcLocation.clone().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
                world.playSound(npcLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.8f);

                new BukkitRunnable() {
                    private int ticks = 0;

                    @Override
                    public void run() {
                        if (blackMarketNPC == null || ticks >= 40) {
                            cancel();
                            return;
                        }
                        // Effet de rotation visuel (pas de rotation réelle du NPC)
                        world.spawnParticle(Particle.HAPPY_VILLAGER, npcLocation.add(0, 1, 0), 1);
                        ticks++;
                    }
                }.runTaskTimer(plugin, 5L, 1L);
                break;

            case "disappear":
                world.spawnParticle(Particle.SMOKE, npcLocation.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
                world.playSound(npcLocation, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
                break;

            case "raid":
                world.spawnParticle(Particle.LAVA, npcLocation.add(0, 1, 0), 20, 1, 1, 1, 0.1);
                world.playSound(npcLocation, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 0.6f);

                break;

            case "trade":
                world.spawnParticle(Particle.HAPPY_VILLAGER, npcLocation.add(0, 2, 0), 5, 0.3, 0.3, 0.3, 0.1);
                world.playSound(npcLocation, Sound.ENTITY_VILLAGER_YES, 0.8f, 1.0f);
                break;

            case "scam":
                world.spawnParticle(Particle.SMOKE, npcLocation.add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                world.playSound(npcLocation, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.5f);

        }
    }

    private void triggerRaid() {
        isAvailable = false;
        currentState = MarketState.RAIDED;
        currentLocation = null;
        currentStock.clear();
        if (blackMarketNPC != null) {
            playNPCAnimation(blackMarketNPC, "raid");
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (blackMarketNPC != null) {
                        npcUtils.removeNPC(blackMarketNPC); // Remplace blackMarketNPC.remove()
                        blackMarketNPC = null;
                    }
                }
            }.runTaskLater(plugin, 60L);
        }
        int raidDuration = plugin.getConfig().getInt("black-market.raid-duration-hours", RAID_DURATION_HOURS);
        notifyPlayersNearby("§c§l🚨 RAID EN COURS! §7Le marché noir ferme temporairement...");
        new BukkitRunnable() {
            @Override
            public void run() {
                currentState = MarketState.AVAILABLE;
                relocateMarket();
                notifyPlayersNearby("§a§l✅ RAID TERMINÉ! §7Le marché noir reprend ses activités...");
            }
        }.runTaskLater(plugin, raidDuration * 3600L * 20L);
    }

    private void refreshStock() {
        currentStock.clear();
        generateCristals();
        generateEnchantmentBooks();
        generateContainers();
        generateKeys();
    }

    private void generateCristals() {
        for (int level = 1; level <= 10; level++) {
            if (ThreadLocalRandom.current().nextDouble() < 0.4) {
                ItemStack cristal = plugin.getCristalManager().createCristalVierge(level).toItemStack(plugin.getCristalManager().getCristalUuidKey(), plugin.getCristalManager().getCristalLevelKey(), plugin.getCristalManager().getCristalTypeKey(), plugin.getCristalManager().getCristalViergeKey());
                int price = BASE_PRICE_CRISTAL * (level * level);
                String itemId = "cristal_" + level + "_" + UUID.randomUUID().toString().substring(0, 8);
                ReputationTier[] required = level > 7 ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME} : level > 4 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME} : new ReputationTier[0];
                currentStock.put(cristal, new BlackMarketItem(itemId, price, "Cristal", required));
            }
        }
    }

    private void generateEnchantmentBooks() {
        EnchantmentBookManager bookManager = plugin.getEnchantmentBookManager();
        if (bookManager == null) return;
        Collection<EnchantmentBookManager.EnchantmentBook> allBooks = bookManager.getAllEnchantmentBooks();
        for (EnchantmentBookManager.EnchantmentBook book : allBooks) {
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                String bookId = book.getId();
                ItemStack bookItem = bookManager.createPhysicalEnchantmentBook(book);
                if (bookItem != null) {
                    int price = BASE_PRICE_BOOK * (book.getMaxLevel() * 2);
                    String itemId = "book_" + bookId + "_" + UUID.randomUUID().toString().substring(0, 8);
                    ReputationTier[] required = book.getMaxLevel() > 3 ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME} : new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME};
                    currentStock.put(bookItem, new BlackMarketItem(itemId, price, "Livre", required));
                }
            }
        }
    }

    private void generateContainers() {
        ContainerManager containerManager = plugin.getContainerManager();
        if (containerManager == null) return;
        for (int tier = 1; tier <= 5; tier++) {
            if (ThreadLocalRandom.current().nextDouble() < (0.6 - tier * 0.1)) {
                ItemStack container = containerManager.createContainer(tier);
                if (container != null) {
                    int price = BASE_PRICE_CONTAINER * (tier * tier * 2);
                    String itemId = "container_" + tier + "_" + UUID.randomUUID().toString().substring(0, 8);
                    ReputationTier[] required = tier >= 4 ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME} : tier >= 3 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME} : new ReputationTier[0];
                    currentStock.put(container, new BlackMarketItem(itemId, price, "Conteneur", required));
                }
            }
        }
    }

    private void generateKeys() {
        String[] keyTypes = {"Commune", "Peu Commune", "Rare", "Légendaire", "Cristal"};
        int[] keyPrices = {BASE_PRICE_KEY, BASE_PRICE_KEY * 2, BASE_PRICE_KEY * 5, BASE_PRICE_KEY * 15, BASE_PRICE_KEY * 50};
        for (int i = 0; i < keyTypes.length; i++) {
            if (ThreadLocalRandom.current().nextDouble() < (0.8 - i * 0.15)) {
                ItemStack key = plugin.getEnchantmentManager().createKey(keyTypes[i]);
                if (key != null) {
                    String itemId = "key_" + keyTypes[i].toLowerCase().replace(" ", "_") + "_" + UUID.randomUUID().toString().substring(0, 8);
                    ReputationTier[] required = i >= 3 ? new ReputationTier[]{ReputationTier.CRIMINEL, ReputationTier.INFAME} : i >= 2 ? new ReputationTier[]{ReputationTier.SUSPECT, ReputationTier.CRIMINEL, ReputationTier.INFAME} : new ReputationTier[0];
                    currentStock.put(key, new BlackMarketItem(itemId, keyPrices[i], "Clé", required));
                }
            }
        }
    }

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
        ReputationTier playerReputation = reputationManager.getReputationTier(player.getUniqueId());
        if (playerReputation == ReputationTier.EXEMPLAIRE) {
            player.sendMessage("§c§lACCÈS REFUSÉ! §7Votre réputation est trop... propre.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }
        checkRandomEvents(player);
        Inventory gui = Bukkit.createInventory(null, 54, "§8§l⚫ MARCHÉ NOIR ⚫");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.BLACK_MARKET, gui);
        ItemStack blackPane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta paneMeta = blackPane.getItemMeta();
        paneMeta.setDisplayName(" ");
        blackPane.setItemMeta(paneMeta);
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, blackPane);
        }
        setupBlackMarketItems(gui, player, playerReputation);
        setupBlackMarketButtons(gui, player);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 0.7f);
        if (blackMarketNPC != null) {
            playNPCAnimation(blackMarketNPC, "trade");
        }
    }

    private void setupBlackMarketItems(Inventory gui, Player player, ReputationTier playerReputation) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int slot = 0;
        Set<String> playerPurchasedItems = getPlayerPurchases(player.getUniqueId());
        for (Map.Entry<ItemStack, BlackMarketItem> entry : currentStock.entrySet()) {
            if (slot >= slots.length) break;
            ItemStack item = entry.getKey();
            BlackMarketItem marketItem = entry.getValue();
            if (!marketItem.canPlayerAccess(playerReputation)) continue;
            boolean alreadyPurchased = playerPurchasedItems.contains(marketItem.itemId());
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

    private void setupBlackMarketButtons(Inventory gui, Player player) {
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName("§e§lINFORMATIONS");
        infoMeta.setLore(Arrays.asList(
                "§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§8⚫ Marché Noir ⚫",
                "§7Commerce d'objets rares et dangereux",
                "",
                "§7État actuel: " + currentState.getDisplay(),
                "§cAttention: §7Transactions à vos risques",
                "§7Votre réputation: §f" + reputationManager.getReputationTier(player.getUniqueId()).getColoredTitle(),
                "§7▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        infoItem.setItemMeta(infoMeta);
        gui.setItem(49, infoItem);
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c§lFERMER");
        closeItem.setItemMeta(closeMeta);
        gui.setItem(53, closeItem);
    }

    private int calculateFinalPrice(BlackMarketItem item, ReputationTier reputation) {
        double modifier = reputation.getBlackMarketPriceModifier();
        return Math.max(1, (int) Math.round(item.basePrice() * (1.0 + modifier)));
    }

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
            if (blackMarketNPC != null) {
                playNPCAnimation(blackMarketNPC, "scam");
            }
        }
    }

    public void handleBlackMarketClick(Player player, ItemStack clicked) {
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.BARRIER && clicked.hasItemMeta() && clicked.getItemMeta().getDisplayName().equals("§c§lFERMER")) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (clicked.getType() == Material.BOOK) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }
        if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
        BlackMarketItem marketItem = findMarketItem(clicked);
        if (marketItem == null) {
            player.sendMessage("§c§lERREUR! §7Item non disponible ou déjà acheté...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }
        if (getPlayerPurchases(player.getUniqueId()).contains(marketItem.itemId())) {
            player.sendMessage("§c§l❌ ERREUR! §7Vous avez déjà acheté cet item!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }
        if (purchaseItem(player, clicked, marketItem)) {
            savePlayerPurchase(player.getUniqueId(), marketItem.itemId());
            removeItemFromStock(clicked);
            player.closeInventory();
            openBlackMarket(player);
        }
    }

    private BlackMarketItem findMarketItem(ItemStack clicked) {
        if (clicked.getType() == Material.BARRIER) return null;
        for (Map.Entry<ItemStack, BlackMarketItem> entry : currentStock.entrySet()) {
            ItemStack marketItem = entry.getKey();
            if (marketItem.getType() == clicked.getType()) {
                ItemMeta marketMeta = marketItem.getItemMeta();
                ItemMeta clickedMeta = clicked.getItemMeta();
                if (marketMeta != null && clickedMeta != null) {
                    String marketName = marketMeta.getDisplayName();
                    String clickedName = clickedMeta.getDisplayName();
                    if (plugin.getContainerManager().isContainer(marketItem)) {
                        if (plugin.getContainerManager().isContainer(clicked)) {
                            return entry.getValue();
                        }
                    } else if (marketItem.getType() == Material.ENCHANTED_BOOK || marketItem.getType() == Material.BOOK) {
                        if (marketName != null && clickedName != null) {
                            String baseMarketName = marketName.split("§")[0];
                            String baseClickedName = clickedName.split("§")[0];
                            if (baseMarketName.equals(baseClickedName)) {
                                return entry.getValue();
                            }
                        }
                    } else if (marketName != null && marketName.equals(clickedName)) {
                        return entry.getValue();
                    }
                }
            }
        }
        return null;
    }

    private boolean purchaseItem(Player player, ItemStack clicked, BlackMarketItem marketItem) {
        UUID playerId = player.getUniqueId();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        ReputationTier reputation = reputationManager.getReputationTier(playerId);
        int finalPrice = calculateFinalPrice(marketItem, reputation);
        if (playerData.getBeacons() < finalPrice) {
            player.sendMessage("§c§l💸 FONDS INSUFFISANTS! §7Vous avez besoin de §e" + finalPrice + " beacons§7.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return false;
        }
        double scamChance = plugin.getConfig().getDouble("black-market.scam-chance", SCAM_CHANCE);
        if (ThreadLocalRandom.current().nextDouble() < scamChance) {
            playerData.removeBeacon(finalPrice);
            plugin.getPlayerDataManager().markDirty(playerId);
            double scamType = ThreadLocalRandom.current().nextDouble();
            if (scamType < 0.5) {
                player.sendMessage("§c§l💸 ARNAQUÉ! §7Le marchand a pris votre argent et a disparu!");
            } else {
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
            if (blackMarketNPC != null) {
                playNPCAnimation(blackMarketNPC, "scam");
            }
            return true;
        }
        playerData.removeBeacon(finalPrice);
        plugin.getPlayerDataManager().markDirty(playerId);
        ItemStack cleanItem = clicked.clone();
        ItemMeta meta = cleanItem.getItemMeta();
        if (meta != null && meta.getLore() != null) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.removeIf(line -> line.contains("Prix:") || line.contains("Catégorie:") || line.contains("▬") || line.contains("CLIC pour acheter") || line.contains("DÉJÀ ACHETÉ"));
            meta.setLore(lore.isEmpty() ? null : lore);
            cleanItem.setItemMeta(meta);
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), cleanItem);
            player.sendMessage("§6§lTRANSACTION RÉUSSIE! §7Item droppé au sol (inventaire plein).");
        } else {
            player.getInventory().addItem(cleanItem);
            player.sendMessage("§6§lTRANSACTION RÉUSSIE! §7Transaction discrète terminée...");
        }
        reputationManager.handleBlackMarketTransaction(playerId, finalPrice);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 0.8f);
        if (blackMarketNPC != null) {
            playNPCAnimation(blackMarketNPC, "trade");
        }
        return true;
    }

    private void removeItemFromStock(ItemStack item) {
        BlackMarketItem marketItem = findMarketItem(item);
        if (marketItem != null) {
            currentStock.entrySet().removeIf(entry -> entry.getValue().equals(marketItem));
        }
    }

    public Location getCurrentLocation() {
        return currentLocation != null ? currentLocation.clone() : null;
    }

    public boolean isAvailable() {
        return !isAvailable || currentState != MarketState.AVAILABLE;
    }

    public MarketState getCurrentState() {
        return currentState;
    }

    private void notifyPlayersNearby(String message) {
        if (currentLocation == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(currentLocation.getWorld()) && player.getLocation().distance(currentLocation) <= 100) {
                player.sendMessage(message);
            }
        }
    }

    public void forceRelocation() {
        relocateMarket();
    }

    public void reloadConfiguration() {
        possibleLocations.clear();
        loadConfigurationLocations();
    }

    public Set<String> getPlayerPurchases(UUID playerId) {
        String query = "SELECT item_id FROM black_market_purchases WHERE player_uuid = ?";
        Set<String> purchases = new HashSet<>();
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                purchases.add(rs.getString("item_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not load player purchases from database: " + e.getMessage());
        }
        return purchases;
    }

    public void savePlayerPurchase(UUID playerId, String itemId) {
        String query = "INSERT INTO black_market_purchases (player_uuid, item_id) VALUES (?, ?)";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerId.toString());
            ps.setString(2, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save player purchase to database: " + e.getMessage());
        }
    }

    public void cleanupPlayerData(UUID playerId) {
        String query = "DELETE FROM black_market_purchases WHERE player_uuid = ?";
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not clean up player purchases from database: " + e.getMessage());
        }
    }

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

    private record BlackMarketLocation(Location location, String name, double dangerLevel, boolean isSitting, boolean turnToPlayer, int turnToPlayerDistance) {
        @Override
        public Location location() {
            return location.clone();
        }
    }

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

    public void shutdown() {
        plugin.getPluginLogger().info("§7Fermeture du BlackMarketManager...");

        try {
            if (blackMarketNPC != null) {
                npcUtils.removeNPC(blackMarketNPC);
                blackMarketNPC = null;
                plugin.getPluginLogger().debug("§7NPC du marché noir supprimé");
            }

            // Nettoyer le stock en mémoire
            currentStock.clear();

            plugin.getPluginLogger().info("§aBlackMarketManager fermé proprement");

        } catch (Exception e) {
            plugin.getPluginLogger().severe("§cErreur lors de la fermeture du BlackMarketManager:");
            e.printStackTrace();
        }
    }
}
