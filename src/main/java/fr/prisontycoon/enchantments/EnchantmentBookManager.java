package fr.prisontycoon.enchantments;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.BlockValueData;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.ConfigManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gestionnaire principal du système d'enchantements uniques pour pioche
 */
public class EnchantmentBookManager {

    private final PrisonTycoon plugin;
    private final Map<String, EnchantmentBook> enchantmentBooks;
    private final Map<UUID, Set<String>> activeEnchantments; // Joueur -> enchants actifs

    public EnchantmentBookManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.enchantmentBooks = new HashMap<>();
        this.activeEnchantments = new HashMap<>();

        initializeEnchantmentBooks();
        plugin.getPluginLogger().info("§aEnchantmentBookManager initialisé avec " + enchantmentBooks.size() + " livres d'enchantement.");
    }

    /**
     * Initialise tous les livres d'enchantement
     */
    private void initializeEnchantmentBooks() {
        // Définition des enchantements avec leurs coûts par niveau
        registerEnchantmentBook(new BomberEnchantmentBook());
        registerEnchantmentBook(new AutoSellEnchantmentBook());
        registerEnchantmentBook(new BeaconBreakerEnchantmentBook());
        registerEnchantmentBook(new ExcavationEnchantmentBook());
        registerEnchantmentBook(new IncassableEnchantmentBook());
        registerEnchantmentBook(new TunnelEnchantmentBook());
        registerEnchantmentBook(new PlusValueEnchantmentBook());
        registerEnchantmentBook(new TonnerreEnchantmentBook());
        registerEnchantmentBook(new VeinMinerEnchantmentBook());
        registerEnchantmentBook(new ChaosEnchantmentBook());
    }

    private void registerEnchantmentBook(EnchantmentBook book) {
        enchantmentBooks.put(book.getId(), book);
    }

    /**
     * Active/Désactive un enchantement (coût en XP)
     */
    public boolean toggleEnchantment(Player player, String bookId) {
        if (!hasEnchantmentBook(player, bookId) || getEnchantmentBookLevel(player, bookId) <= 0) {
            return false; // Ne pas envoyer de message ici, géré par le GUI
        }

        Set<String> playerActiveEnchants = activeEnchantments.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
        boolean isActive = playerActiveEnchants.contains(bookId);

        if (isActive) {
            // Désactivation (gratuite)
            playerActiveEnchants.remove(bookId);
            player.sendMessage("§c⭕ Enchantement §e" + enchantmentBooks.get(bookId).getName() + " §cdésactivé!");
            player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
            return true;
        } else {
            // Vérification de la limite (4 max)
            if (playerActiveEnchants.size() >= 4) {
                return false; // Erreur gérée par le GUI
            }

            // Calcul du coût XP (augmente selon le nombre d'enchants actifs)
            int xpCost = calculateActivationCost(playerActiveEnchants.size());

            if (player.getTotalExperience() < xpCost) {
                return false; // Erreur gérée par le GUI
            }

            // Déduction de l'XP et activation
            player.setTotalExperience(player.getTotalExperience() - xpCost);
            playerActiveEnchants.add(bookId);

            player.sendMessage("§a✅ Enchantement §e" + enchantmentBooks.get(bookId).getName() + " §aactivé pour §b" + xpCost + " XP§a!");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
            return true;
        }
    }

    /**
     * NOUVEAU : Achète un livre physique et le donne au joueur
     */
    public boolean purchasePhysicalEnchantmentBook(Player player, String bookId) {
        EnchantmentBook book = enchantmentBooks.get(bookId);
        if (book == null) {
            player.sendMessage("§cLivre d'enchantement introuvable!");
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = getEnchantmentBookLevel(player, bookId);

        // MODIFIÉ : Le coût est maintenant fixe et récupéré via getCost()
        long cost = book.getCost();

        if (playerData.getBeacons() < cost) {
            player.sendMessage("§cVous n'avez pas assez de beacons! Coût: §6" + NumberFormatter.format(cost) + " beacons");
            return false;
        }

        // Déduction des beacons
        playerData.removeBeacon(cost);

        // NOUVEAU : Création d'un item livre physique au lieu d'ajouter directement
        ItemStack physicalBook = createPhysicalEnchantmentBook(book, currentLevel + 1);

        // Tentative d'ajout à l'inventaire
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(physicalBook);
            player.sendMessage("§a✅ Livre physique §e" + book.getName() + " §aacheté pour §6" + NumberFormatter.format(cost) + " beacons§a!");
            player.sendMessage("§7Cliquez sur le livre dans votre inventaire pour l'appliquer à votre pioche!");
        }

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
        return true;
    }

    /**
     * NOUVEAU : Crée un item livre physique
     */
    private ItemStack createPhysicalEnchantmentBook(EnchantmentBook book, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§5⚡ " + book.getName());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7" + book.getDescription());
        lore.add("§e⚡ Actions:");
        lore.add("§7▸ §6Clic dans le menu enchantements");
        lore.add("§7  pour appliquer à votre pioche");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);

        // Marquer l'item comme livre d'enchantement physique
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "enchant_book_id"),
                PersistentDataType.STRING,
                book.getId()
        );
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "enchant_book_level"),
                PersistentDataType.INTEGER,
                level
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Calcule le coût d'activation en fonction du nombre d'enchants déjà actifs
     */
    private int calculateActivationCost(int currentActiveCount) {
        return 100 * (int) Math.pow(2, currentActiveCount); // 100, 200, 400, 800
    }

    /**
     * Ajoute des livres d'enchantement au joueur
     */
    public void addEnchantmentBook(Player player, String bookId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int currentLevel = getEnchantmentBookLevel(player, bookId);

        EnchantmentBook book = enchantmentBooks.get(bookId);
        if (book.getMaxLevel() == 1) {
            // Pour les enchants à 1 niveau, on ne fait qu'activer
            playerData.setEnchantmentLevel("unique_" + bookId, 1);
        } else {
            // Pour les enchants à plusieurs niveaux, on augmente
            playerData.setEnchantmentLevel("unique_" + bookId, currentLevel + 1);
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Vérifie si le joueur possède un livre
     */
    public boolean hasEnchantmentBook(Player player, String bookId) {
        return getEnchantmentBookLevel(player, bookId) > 0;
    }

    /**
     * Obtient le niveau d'un livre chez le joueur
     */
    public int getEnchantmentBookLevel(Player player, String bookId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.getEnchantmentLevel("unique_" + bookId);
    }

    /**
     * Vérifie si un enchantement est actif
     */
    public boolean isEnchantmentActive(Player player, String bookId) {
        Set<String> playerActiveEnchants = activeEnchantments.get(player.getUniqueId());
        return playerActiveEnchants != null && playerActiveEnchants.contains(bookId);
    }

    /**
     * Obtient tous les enchantements actifs d'un joueur
     */
    public Set<String> getActiveEnchantments(Player player) {
        return activeEnchantments.getOrDefault(player.getUniqueId(), new HashSet<>());
    }

    /**
     * Traitement des enchantements lors du minage
     */
    public void processMiningEnchantments(Player player, Location location) {
        Set<String> activeEnchants = getActiveEnchantments(player);

        for (String bookId : activeEnchants) {
            EnchantmentBook book = enchantmentBooks.get(bookId);
            if (book != null) {
                int level = getEnchantmentBookLevel(player, bookId);
                book.onMining(player, location, level, plugin);
            }
        }
    }

    /**
     * Obtient tous les livres d'enchantement
     */
    public Collection<EnchantmentBook> getAllEnchantmentBooks() {
        return enchantmentBooks.values();
    }

    public EnchantmentBook getEnchantmentBook(String id) {
        return enchantmentBooks.get(id);
    }

    /**
     * NOUVEAU : Gère l'AutoSell avec pénalité de 2%
     */
    public void processAutoSell(Player player, Material blockType, int quantity) {
        // Récupère la valeur du bloc depuis le ConfigManager
        BlockValueData blockValue = plugin.getConfigManager().getBlockValue(blockType);

        // Vérifie si le bloc a une valeur monétaire
        if (blockValue == null || blockValue.getCoins() <= 0) {
            return;
        }

        // Applique la pénalité de 2%
        long basePrice = blockValue.getCoins();
        long sellPrice = Math.round(basePrice * quantity * 0.98);

        // Ajoute l'argent au joueur
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        playerData.addCoinsViaAutosell(sellPrice);


    }

    /**
     * NOUVEAU : Obtient le bloc de plus haute valeur dans la mine
     */
    public Material getHighestValueBlockInMine(String mineName) {
        ConfigManager configManager = plugin.getConfigManager();
        MineData mineData = configManager.getMineData(mineName);

        if (mineData == null) {
            plugin.getPluginLogger().warning("§cImpossible de trouver les données pour la mine: " + mineName);
            return Material.STONE; // Retourne une valeur par défaut
        }

        Map<Material, Double> blockComposition = mineData.getBlockComposition();
        if (blockComposition.isEmpty()) {
            plugin.getPluginLogger().warning("§cLa mine '" + mineName + "' n'a pas de composition de blocs définie.");
            return Material.STONE;
        }

        Material highestValueBlock = null;
        long maxCoins = -1;

        // Itère sur les blocs de la mine pour trouver celui avec la plus grande valeur
        for (Material material : blockComposition.keySet()) {
            BlockValueData blockValue = configManager.getBlockValue(material);
            if (blockValue != null && blockValue.getCoins() > maxCoins) {
                maxCoins = blockValue.getCoins();
                highestValueBlock = material;
            }
        }

        return highestValueBlock != null ? highestValueBlock : Material.STONE; // Sécurité
    }

    // ===== Classes des livres d'enchantement =====

    public abstract static class EnchantmentBook {
        public abstract String getId();
        public abstract String getName();
        public abstract String getDescription();
        public abstract int getMaxLevel();
        public abstract Material getDisplayMaterial();
        // MODIFIÉ : Remplacé getCostForLevel par getCost
        public abstract long getCost();

        /**
         * Méthode appelée lors du minage si l'enchantement est actif
         */
        public abstract void onMining(Player player, Location location, int level, PrisonTycoon plugin);
    }

    // ===== Implémentation des enchantements =====

    public static class BomberEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "bomber"; }
        @Override public String getName() { return "Bomber"; }
        @Override public String getDescription() { return "Explosion rayon 6 blocs autour du joueur"; }
        @Override public int getMaxLevel() { return 20; }
        @Override public Material getDisplayMaterial() { return Material.TNT; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 15000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            double chance = level * 0.05; // 5% par niveau
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                triggerExplosion(player, location, plugin);
            }
        }

        private void triggerExplosion(Player player, Location center, PrisonTycoon plugin) {
            // Animation d'explosion
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks > 20) {
                        cancel();
                        return;
                    }

                    // Effets visuels
                    center.getWorld().spawnParticle(Particle.EXPLOSION, center, 10, 3, 3, 3, 0.1);
                    center.getWorld().spawnParticle(Particle.LAVA, center, 20, 6, 6, 6, 0);

                    if (ticks == 10) {
                        player.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);

                        // Destruction des blocs dans un rayon de 6
                        for (int x = -6; x <= 6; x++) {
                            for (int y = -6; y <= 6; y++) {
                                for (int z = -6; z <= 6; z++) {
                                    Location blockLoc = center.clone().add(x, y, z);
                                    if (blockLoc.distance(center) <= 6) {
                                        // Simuler le minage du bloc
                                        if (blockLoc.getBlock().getType() != Material.AIR) {
                                            plugin.getEnchantmentManager().processBlockDestroyed(player, blockLoc, blockLoc.getBlock().getType(), "explosion");
                                            blockLoc.getBlock().setType(Material.AIR);
                                        }
                                    }
                                }
                            }
                        }

                        player.sendMessage("§c💥 §eBomber déclenché! Explosion massive!");
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0, 1);
        }
    }

    public static class AutoSellEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "autosell"; }
        @Override public String getName() { return "AutoSell"; }
        @Override public String getDescription() { return "Vente automatique des minerais (-2% pénalité)"; }
        @Override public int getMaxLevel() { return 1; }
        @Override public Material getDisplayMaterial() { return Material.EMERALD; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 25000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            // Logique AutoSell sera intégrée dans le système de minage principal
            // avec pénalité de 2% sur les prix
        }
    }

    public static class BeaconBreakerEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "beaconbreaker"; }
        @Override public String getName() { return "BeaconBreaker"; }
        @Override public String getDescription() { return "Minage instantané des beacons"; }
        @Override public int getMaxLevel() { return 1; }
        @Override public Material getDisplayMaterial() { return Material.BEACON; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 50000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            // Logique de minage instantané beacon intégrée au système principal
        }
    }

    public static class ExcavationEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "excavation"; }
        @Override public String getName() { return "Excavation"; }
        @Override public String getDescription() { return "Minage 3x3"; }
        @Override public int getMaxLevel() { return 1; }
        @Override public Material getDisplayMaterial() { return Material.IRON_PICKAXE; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 30000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            // Mine dans un carré 3x3 autour du bloc miné
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Location blockLoc = location.clone().add(x, y, z);
                        if (blockLoc.getBlock().getType() != Material.AIR) {
                            plugin.getEnchantmentManager().processBlockDestroyed(player, blockLoc, blockLoc.getBlock().getType(), "excavation");
                            blockLoc.getBlock().setType(Material.AIR);
                        }
                    }
                }
            }
        }
    }

    public static class IncassableEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "incassable"; }
        @Override public String getName() { return "Incassable"; }
        @Override public String getDescription() { return "Durabilité infinie"; }
        @Override public int getMaxLevel() { return 1; }
        @Override public Material getDisplayMaterial() { return Material.DIAMOND; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 100000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            // Logique de durabilité infinie intégrée au système principal
        }
    }

    public static class TunnelEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "tunnel"; }
        @Override public String getName() { return "Tunnel"; }
        @Override public String getDescription() { return "Chance de miner 3x3 jusqu'au bout de la mine"; }
        @Override public int getMaxLevel() { return 20; }
        @Override public Material getDisplayMaterial() { return Material.MINECART; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 10000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            double chance = level * 0.025; // 2.5% par niveau
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                triggerTunnel(player, location, plugin);
            }
        }

        private void triggerTunnel(Player player, Location start, PrisonTycoon plugin) {
            new BukkitRunnable() {
                int distance = 0;
                @Override
                public void run() {
                    if (distance > 50) { // Limite de la mine
                        cancel();
                        return;
                    }

                    // Mine 3x3 à cette distance
                    Vector direction = player.getEyeLocation().getDirection();
                    Location center = start.clone().add(direction.multiply(distance));

                    for (int x = -1; x <= 1; x++) {
                        for (int y = -1; y <= 1; y++) {
                            for (int z = -1; z <= 1; z++) {
                                Location blockLoc = center.clone().add(x, y, z);
                                if (blockLoc.getBlock().getType() != Material.AIR) {
                                    plugin.getEnchantmentManager().processBlockDestroyed(player, blockLoc, blockLoc.getBlock().getType(), "tunnel");
                                    blockLoc.getBlock().setType(Material.AIR);

                                    // Effets visuels
                                    blockLoc.getWorld().spawnParticle(Particle.BLOCK, blockLoc, 5, blockLoc.getBlock().getBlockData());
                                }
                            }
                        }
                    }

                    distance++;
                }
            }.runTaskTimer(plugin, 0, 2); // Toutes les 2 ticks

            player.sendMessage("§e⛏️ §bTunnel déclenché! Perçage en cours...");
            player.playSound(start, Sound.BLOCK_PISTON_EXTEND, 1.0f, 0.5f);
        }
    }

    public static class PlusValueEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "plusvalue"; }
        @Override public String getName() { return "PlusValue"; }
        @Override public String getDescription() { return "Transforme tous les blocs en bloc de plus haute valeur"; }
        @Override public int getMaxLevel() { return 1; }
        @Override public Material getDisplayMaterial() { return Material.GOLD_BLOCK; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 100000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            // Logique de transformation intégrée au système de valeur des blocs
        }
    }

    public static class TonnerreEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "tonnerre"; }
        @Override public String getName() { return "Tonnerre"; }
        @Override public String getDescription() { return "Tous les blocs au-dessus cassés dans un rayon de 5"; }
        @Override public int getMaxLevel() { return 20; }
        @Override public Material getDisplayMaterial() { return Material.LIGHTNING_ROD; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 100000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            double chance = level * 0.03; // 3% par niveau
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                triggerThunder(player, location, plugin);
            }
        }

        private void triggerThunder(Player player, Location center, PrisonTycoon plugin) {
            new BukkitRunnable() {
                int phase = 0;
                @Override
                public void run() {
                    if (phase > 30) {
                        cancel();
                        return;
                    }

                    if (phase == 0) {
                        // Effets de préparation
                        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 50, 5, 10, 5, 0.3);
                        player.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 1.2f);
                    } else if (phase == 15) {
                        // Destruction chaotique des blocs au-dessus
                        for (int y = 1; y <= 50; y++) {
                            for (int x = -5; x <= 5; x++) {
                                for (int z = -5; z <= 5; z++) {
                                    if (ThreadLocalRandom.current().nextDouble() < 0.7) { // Aspect chaotique
                                        Location blockLoc = center.clone().add(x, y, z);
                                        if (blockLoc.distance(center.clone().add(0, y, 0)) <= 5) {
                                            if (blockLoc.getBlock().getType() != Material.AIR) {
                                                plugin.getEnchantmentManager().processBlockDestroyed(player, blockLoc, blockLoc.getBlock().getType(), "tonnerre");
                                                blockLoc.getBlock().setType(Material.AIR);

                                                // Effets visuels
                                                blockLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, blockLoc, 3);
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        player.sendMessage("§e⚡ §bTonnerre déclenché! Destruction divine!");
                    }

                    phase++;
                }
            }.runTaskTimer(plugin, 0, 1);
        }
    }

    public static class VeinMinerEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "veinminer"; }
        @Override public String getName() { return "VeinMiner"; }
        @Override public String getDescription() { return "Chance de miner tout le filon connecté (max 100 blocs)"; }
        @Override public int getMaxLevel() { return 20; }
        @Override public Material getDisplayMaterial() { return Material.DIAMOND_ORE; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 100000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            double chance = level * 0.04; // 4% par niveau
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                triggerVeinMining(player, location, plugin);
            }
        }

        private void triggerVeinMining(Player player, Location start, PrisonTycoon plugin) {
            Material targetMaterial = start.getBlock().getType();
            Set<Location> vein = findConnectedBlocks(start, targetMaterial, 100);

            new BukkitRunnable() {
                Iterator<Location> iterator = vein.iterator();
                int count = 0;

                @Override
                public void run() {
                    for (int i = 0; i < 5 && iterator.hasNext(); i++) { // 5 blocs par tick
                        Location loc = iterator.next();
                        if (loc.getBlock().getType() == targetMaterial) {
                            plugin.getEnchantmentManager().processBlockDestroyed(player, loc, targetMaterial, "veinminer");
                            loc.getBlock().setType(Material.AIR);

                            // Effets visuels
                            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 3);
                            count++;
                        }
                    }

                    if (!iterator.hasNext()) {
                        player.sendMessage("§e⛏️ §bVeinMiner déclenché! " + count + " blocs extraits du filon!");
                        player.playSound(start, Sound.BLOCK_ANVIL_USE, 1.0f, 1.5f);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0, 1);
        }

        private Set<Location> findConnectedBlocks(Location start, Material material, int maxBlocks) {
            Set<Location> found = new HashSet<>();
            Queue<Location> toCheck = new LinkedList<>();
            toCheck.add(start);
            found.add(start);

            int[] offsets = {-1, 0, 1};

            while (!toCheck.isEmpty() && found.size() < maxBlocks) {
                Location current = toCheck.poll();

                for (int x : offsets) {
                    for (int y : offsets) {
                        for (int z : offsets) {
                            if (x == 0 && y == 0 && z == 0) continue;

                            Location neighbor = current.clone().add(x, y, z);
                            if (!found.contains(neighbor) && neighbor.getBlock().getType() == material) {
                                found.add(neighbor);
                                toCheck.add(neighbor);

                                if (found.size() >= maxBlocks) break;
                            }
                        }
                        if (found.size() >= maxBlocks) break;
                    }
                    if (found.size() >= maxBlocks) break;
                }
            }

            return found;
        }
    }

    public static class ChaosEnchantmentBook extends EnchantmentBook {
        @Override public String getId() { return "chaos"; }
        @Override public String getName() { return "Chaos"; }
        @Override public String getDescription() { return "Mine 30s aléatoirement dans un rayon de 50 blocs"; }
        @Override public int getMaxLevel() { return 5; }
        @Override public Material getDisplayMaterial() { return Material.NETHER_STAR; }

        // MODIFIÉ : Le coût est maintenant fixe.
        @Override
        public long getCost() {
            return 100000;
        }

        @Override
        public void onMining(Player player, Location location, int level, PrisonTycoon plugin) {
            double chance = level * 0.02; // 2% par niveau
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                triggerChaos(player, location, plugin);
            }
        }

        private void triggerChaos(Player player, Location center, PrisonTycoon plugin) {
            player.sendMessage("§d🌟 §5Chaos déclenché! Minage chaotique pendant 30 secondes!");
            player.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);

            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= 600) { // 30 secondes
                        player.sendMessage("§d🌟 §5Chaos terminé!");
                        cancel();
                        return;
                    }

                    // Mine 3 blocs aléatoires par tick
                    for (int i = 0; i < 3; i++) {
                        double x = (ThreadLocalRandom.current().nextDouble() - 0.5) * 100; // -50 à +50
                        double y = (ThreadLocalRandom.current().nextDouble() - 0.5) * 100;
                        double z = (ThreadLocalRandom.current().nextDouble() - 0.5) * 100;

                        Location randomLoc = center.clone().add(x, y, z);
                        if (randomLoc.getBlock().getType() != Material.AIR) {
                            plugin.getEnchantmentManager().processBlockDestroyed(player, randomLoc, randomLoc.getBlock().getType(), "chaos");
                            randomLoc.getBlock().setType(Material.AIR);

                            // Effets visuels chaotiques
                            randomLoc.getWorld().spawnParticle(Particle.PORTAL, randomLoc, 5, 0.5, 0.5, 0.5, 0.1);
                        }
                    }

                    ticks++;
                }
            }.runTaskTimer(plugin, 0, 1);
        }
    }
}