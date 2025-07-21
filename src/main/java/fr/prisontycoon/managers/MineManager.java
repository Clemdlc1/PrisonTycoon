package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des mines amélioré avec support Prestige et VIP
 */
public class MineManager {

    private final PrisonTycoon plugin;
    private final Map<String, MineData> mines = new ConcurrentHashMap<>();
    private final Map<String, Long> mineResetTimes = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // Types de mines
    public enum MineType {
        NORMAL,     // Mines A-Z normales
        PRESTIGE,   // Mines prestige (P1, P11, etc.)
        VIP         // Mines VIP
    }

    public MineManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        loadMinesFromConfig();
        startMineResetScheduler();
    }

    /**
     * Charge toutes les mines depuis la configuration
     */
    private void loadMinesFromConfig() {
        ConfigurationSection minesSection = plugin.getConfig().getConfigurationSection("mines");
        if (minesSection == null) {
            plugin.getPluginLogger().warning("Aucune mine configurée!");
            return;
        }

        for (String mineId : minesSection.getKeys(false)) {
            try {
                MineData mineData = loadMineData(mineId, minesSection.getConfigurationSection(mineId));
                if (mineData != null) {
                    mines.put(mineId, mineData);
                    plugin.getPluginLogger().info("Mine chargée: " + mineId + " (Type: " + mineData.getType() + ")");
                }
            } catch (Exception e) {
                plugin.getPluginLogger().severe("Erreur lors du chargement de la mine " + mineId + ": " + e.getMessage());
            }
        }

        plugin.getPluginLogger().info("Mines chargées: " + mines.size());
    }

    /**
     * Charge les données d'une mine depuis la configuration
     */
    private MineData loadMineData(String mineId, ConfigurationSection section) {
        if (section == null) return null;

        // Coordonnées
        ConfigurationSection coords = section.getConfigurationSection("coordinates");
        if (coords == null) {
            plugin.getPluginLogger().warning("Coordonnées manquantes pour la mine: " + mineId);
            return null;
        }

        String worldName = section.getString("world", "world");
        int minX = coords.getInt("min-x");
        int minY = coords.getInt("min-y");
        int minZ = coords.getInt("min-z");
        int maxX = coords.getInt("max-x");
        int maxY = coords.getInt("max-y");
        int maxZ = coords.getInt("max-z");

        // Composition des blocs
        ConfigurationSection blocks = section.getConfigurationSection("blocks");
        if (blocks == null) {
            plugin.getPluginLogger().warning("Composition de blocs manquante pour la mine: " + mineId);
            return null;
        }

        Map<Material, Double> blockComposition = new HashMap<>();
        for (String blockName : blocks.getKeys(false)) {
            try {
                Material material = Material.valueOf(blockName.toUpperCase());
                double probability = blocks.getDouble(blockName);
                blockComposition.put(material, probability);
            } catch (IllegalArgumentException e) {
                plugin.getPluginLogger().warning("Matériau invalide dans la mine " + mineId + ": " + blockName);
            }
        }

        // Informations de la mine
        String displayName = section.getString("display-name", mineId);
        String description = section.getString("description", "");
        long rankupPrice = section.getLong("rankup-price", -1);

        // Déterminer le type de mine
        MineType type = determineMineType(mineId);

        // Conditions d'accès spéciales
        int requiredPrestige = section.getInt("required-prestige", -1);
        String requiredRank = section.getString("required-rank", null);
        String requiredPermission = section.getString("required-permission", null);

        return new MineData(mineId, worldName, minX, minY, minZ, maxX, maxY, maxZ,
                blockComposition, displayName, description, rankupPrice, type,
                requiredPrestige, requiredRank, requiredPermission);
    }

    /**
     * Détermine le type d'une mine basé sur son ID
     */
    private MineType determineMineType(String mineId) {
        if (mineId.startsWith("mine-prestige")) {
            return MineType.PRESTIGE;
        } else if (mineId.startsWith("mine-vip")) {
            return MineType.VIP;
        } else {
            return MineType.NORMAL;
        }
    }

    /**
     * Vérifie si un joueur peut accéder à une mine
     */
    public boolean canAccessMine(Player player, String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) return false;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        switch (mine.getType()) {
            case NORMAL -> {
                return canAccessNormalMine(player, mineId);
            }
            case PRESTIGE -> {
                return canAccessPrestigeMine(player, mine, playerData);
            }
            case VIP -> {
                return canAccessVipMine(player, mine);
            }
        }

        return false;
    }

    /**
     * Vérifie l'accès aux mines normales (A-Z)
     */
    private boolean canAccessNormalMine(Player player, String mineId) {
        if (mineId.equals("mine-a")) return true; // Mine A toujours accessible

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Extraire la lettre de la mine
        if (mineId.startsWith("mine-") && mineId.length() >= 6) {
            String rank = mineId.substring(5); // Récupère la lettre après "mine-"
            return playerData.hasMinePermission(rank);
        }

        return false;
    }

    /**
     * Vérifie l'accès aux mines prestige
     */
    private boolean canAccessPrestigeMine(Player player, MineData mine, PlayerData playerData) {
        // Vérifier le niveau de prestige requis
        if (mine.getRequiredPrestige() > 0 && playerData.getPrestigeLevel() < mine.getRequiredPrestige()) {
            return false;
        }

        // Vérifier le rang requis (généralement Z)
        if (mine.getRequiredRank() != null) {
            String currentRank = getCurrentRank(player);
            if (!currentRank.equals(mine.getRequiredRank()) && !playerData.hasCustomPermission("specialmine.free")) {
                return false;
            }
        }

        return true;
    }

    /**
     * Vérifie l'accès aux mines VIP
     */
    private boolean canAccessVipMine(Player player, MineData mine) {
        if (mine.getRequiredPermission() != null) {
            return player.hasPermission(mine.getRequiredPermission());
        }
        return player.hasPermission("specialmine.vip");
    }

    /**
     * Génère les blocs d'une mine selon sa composition
     */
    public void generateMine(String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) return;

        World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) {
            plugin.getPluginLogger().warning("Monde introuvable pour la mine: " + mineId);
            return;
        }

        // Calculer le nombre total de blocs
        int totalBlocks = (mine.getMaxX() - mine.getMinX() + 1) *
                (mine.getMaxY() - mine.getMinY() + 1) *
                (mine.getMaxZ() - mine.getMinZ() + 1);

        plugin.getPluginLogger().info("Génération de la mine " + mineId + " (" + totalBlocks + " blocs)");

        // Générer les blocs
        for (int x = mine.getMinX(); x <= mine.getMaxX(); x++) {
            for (int y = mine.getMinY(); y <= mine.getMaxY(); y++) {
                for (int z = mine.getMinZ(); z <= mine.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material material = getRandomMaterial(mine.getBlockComposition());
                    block.setType(material);
                }
            }
        }

        // Mettre à jour le temps de reset
        mineResetTimes.put(mineId, System.currentTimeMillis());
        plugin.getPluginLogger().info("Mine " + mineId + " générée avec succès!");
    }

    /**
     * Sélectionne un matériau aléatoire selon les probabilités
     */
    private Material getRandomMaterial(Map<Material, Double> composition) {
        double randomValue = random.nextDouble();
        double cumulativeProbability = 0.0;

        for (Map.Entry<Material, Double> entry : composition.entrySet()) {
            cumulativeProbability += entry.getValue();
            if (randomValue <= cumulativeProbability) {
                return entry.getKey();
            }
        }

        // Fallback vers le premier matériau si aucun n'est trouvé
        return composition.keySet().iterator().next();
    }

    /**
     * Téléporte un joueur vers une mine
     */
    public boolean teleportToMine(Player player, String mineId) {
        if (!canAccessMine(player, mineId)) {
            player.sendMessage("§c❌ Vous n'avez pas accès à cette mine!");
            return false;
        }

        MineData mine = mines.get(mineId);
        if (mine == null) {
            player.sendMessage("§c❌ Mine introuvable!");
            return false;
        }

        World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) {
            player.sendMessage("§c❌ Monde de la mine introuvable!");
            return false;
        }

        // Calculer le centre de la mine
        double centerX = (mine.getMinX() + mine.getMaxX()) / 2.0 + 0.5;
        double centerY = mine.getMaxY() + 1.0; // Au-dessus de la mine
        double centerZ = (mine.getMinZ() + mine.getMaxZ()) / 2.0 + 0.5;

        Location teleportLocation = new Location(world, centerX, centerY, centerZ);

        // S'assurer qu'il y a de l'air pour le joueur
        Block airBlock1 = teleportLocation.getBlock();
        Block airBlock2 = teleportLocation.add(0, 1, 0).getBlock();

        if (airBlock1.getType() != Material.AIR) {
            airBlock1.setType(Material.AIR);
        }
        if (airBlock2.getType() != Material.AIR) {
            airBlock2.setType(Material.AIR);
        }

        player.teleport(teleportLocation);
        player.sendMessage("§a✅ Téléporté à la mine " + mine.getDisplayName());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        return true;
    }

    /**
     * Obtient la liste des mines accessibles pour un joueur
     */
    public List<MineData> getAccessibleMines(Player player) {
        List<MineData> accessible = new ArrayList<>();

        for (MineData mine : mines.values()) {
            if (canAccessMine(player, mine.getId())) {
                accessible.add(mine);
            }
        }

        // Trier par ordre alphabétique/numérique
        accessible.sort((a, b) -> {
            // Priorité: Normal < VIP < Prestige
            if (a.getType() != b.getType()) {
                return a.getType().ordinal() - b.getType().ordinal();
            }
            return a.getId().compareToIgnoreCase(b.getId());
        });

        return accessible;
    }

    /**
     * Obtient les informations d'une mine
     */
    public String getMineInfo(Player player, String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) {
            return "§c❌ Mine introuvable!";
        }

        StringBuilder info = new StringBuilder();
        info.append("§6📍 Informations - ").append(mine.getDisplayName()).append("\n");
        info.append("§7Description: ").append(mine.getDescription()).append("\n");
        info.append("§7Type: ").append(getMineTypeDisplay(mine.getType())).append("\n");

        // Conditions d'accès
        if (!canAccessMine(player, mineId)) {
            info.append("§c🔒 ACCÈS VERROUILLÉ\n");

            switch (mine.getType()) {
                case PRESTIGE -> {
                    if (mine.getRequiredPrestige() > 0) {
                        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                        info.append("§7Prestige requis: §6P").append(mine.getRequiredPrestige())
                                .append(" §7(Actuel: P").append(playerData.getPrestigeLevel()).append(")\n");
                    }
                    if (mine.getRequiredRank() != null) {
                        info.append("§7Rang requis: ").append(mine.getRequiredRank().toUpperCase()).append("\n");
                    }
                }
                case VIP -> {
                    info.append("§7Permission VIP requise\n");
                }
                case NORMAL -> {
                    String currentRank = getCurrentRank(player);
                    String requiredRank = mine.getId().replace("mine-", "");
                    info.append("§7Rang requis: ").append(requiredRank.toUpperCase())
                            .append(" §7(Actuel: ").append(currentRank.toUpperCase()).append(")\n");
                }
            }
        } else {
            info.append("§a✅ ACCÈS AUTORISÉ\n");
        }

        // Composition des blocs
        info.append("§e⚒ Composition:\n");
        for (Map.Entry<Material, Double> entry : mine.getBlockComposition().entrySet()) {
            String materialName = entry.getKey().toString().toLowerCase().replace("_", " ");
            double percentage = entry.getValue() * 100;
            String percentageStr = percentage < 1 ? String.format("%.2f%%", percentage) : String.format("%.0f%%", percentage);
            info.append("§7• ").append(materialName).append(": ").append(percentageStr).append("\n");
        }

        // Temps depuis le dernier reset
        Long lastReset = mineResetTimes.get(mineId);
        if (lastReset != null) {
            long timeSince = (System.currentTimeMillis() - lastReset) / 1000 / 60; // minutes
            info.append("§7Dernier reset: il y a ").append(timeSince).append(" minute(s)");
        }

        return info.toString();
    }

    /**
     * Affichage du type de mine
     */
    private String getMineTypeDisplay(MineType type) {
        return switch (type) {
            case NORMAL -> "§fNormale";
            case PRESTIGE -> "§dPrestige";
            case VIP -> "§6VIP";
        };
    }

    /**
     * Obtient les mines par type
     */
    public List<MineData> getMinesByType(MineType type) {
        return mines.values().stream()
                .filter(mine -> mine.getType() == type)
                .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                .toList();
    }

    /**
     * Obtient une mine par son ID
     */
    public MineData getMine(String mineId) {
        return mines.get(mineId);
    }

    /**
     * Obtient toutes les mines
     */
    public Collection<MineData> getAllMines() {
        return mines.values();
    }

    /**
     * Lance le scheduler de reset automatique des mines
     */
    private void startMineResetScheduler() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Reset automatique toutes les 30 minutes
            long currentTime = System.currentTimeMillis();
            for (Map.Entry<String, Long> entry : mineResetTimes.entrySet()) {
                if (currentTime - entry.getValue() >= 30 * 60 * 1000) { // 30 minutes
                    generateMine(entry.getKey());
                }
            }
        }, 20L * 60L, 20L * 60L); // Vérification chaque minute
    }

    // ==================== MÉTHODES EXISTANTES CONSERVÉES ====================

    /**
     * Détermine le rang et les couleurs du joueur
     *
     * @param player Le joueur
     * @return [rang, couleur du nom, couleur du rang, rang mine, couleur rang mine]
     */
    public String[] getRankAndColor(Player player) {

        String currentRank = getCurrentRank(player);
        String rankColor = getRankColor(currentRank);

        if (player.hasPermission("specialmine.admin")) {
            return new String[]{"ADMIN", ChatColor.RED.toString(), ChatColor.DARK_RED.toString(), currentRank.toUpperCase(), rankColor};
        } else if (player.hasPermission("specialmine.vip")) {
            return new String[]{"VIP", ChatColor.GOLD.toString(), ChatColor.YELLOW.toString(), currentRank.toUpperCase(), rankColor};
        } else {
            return new String[]{"JOUEUR", ChatColor.GRAY.toString(), ChatColor.DARK_GRAY.toString(), currentRank.toUpperCase(), rankColor};
        }
    }

    /**
     * Obtient la couleur progressive pour un rang de mine (a-z)
     */
    public String getRankColor(String rank) {
        if (rank == null || rank.isEmpty()) return ChatColor.GRAY.toString();

        // Couleur spéciale pour FREE
        if (rank.equalsIgnoreCase("free")) {
            return ChatColor.LIGHT_PURPLE.toString() + ChatColor.BOLD.toString(); // Violet gras
        }

        char rankChar = rank.toLowerCase().charAt(0);

        // Couleurs progressives de a à z (logique existante)
        return switch (rankChar) {
            case 'a', 'b' -> ChatColor.WHITE.toString();
            case 'c', 'd' -> ChatColor.GRAY.toString();
            case 'e', 'f' -> ChatColor.DARK_GRAY.toString();
            case 'g', 'h' -> ChatColor.DARK_GREEN.toString();
            case 'i', 'j' -> ChatColor.GREEN.toString();
            case 'k', 'l' -> ChatColor.YELLOW.toString();
            case 'm', 'n' -> ChatColor.GOLD.toString();
            case 'o', 'p' -> ChatColor.RED.toString();
            case 'q', 'r' -> ChatColor.DARK_RED.toString();
            case 's', 't' -> ChatColor.LIGHT_PURPLE.toString();
            case 'u', 'v' -> ChatColor.DARK_PURPLE.toString();
            case 'w', 'x' -> ChatColor.BLUE.toString();
            case 'y' -> ChatColor.AQUA.toString();
            case 'z' -> ChatColor.GOLD.toString() + ChatColor.BOLD;
            default -> ChatColor.GRAY.toString();
        };
    }

    /**
     * CORRIGÉ: Obtient le rang actuel du joueur via les permissions bukkit
     */
    public String getCurrentRank(Player player) {
        var playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier d'abord si le joueur a le rang FREE
        if (playerData.hasCustomPermission("specialmine.free")) {
            return "free";
        }

        // Logique existante pour les rangs a-z
        String highestPermission = playerData.getHighestMinePermission();
        if (highestPermission == null) {
            return "a"; // Rang par défaut
        }

        if (highestPermission.startsWith("specialmine.mine.")) {
            return highestPermission.substring("specialmine.mine.".length());
        }
        return "a";
    }

    // ==================== CLASSE INTERNE MineData ====================

    /**
     * Représente les données d'une mine
     */
    public static class MineData {
        private final String id;
        private final String worldName;
        private final int minX, minY, minZ, maxX, maxY, maxZ;
        private final Map<Material, Double> blockComposition;
        private final String displayName;
        private final String description;
        private final long rankupPrice;
        private final MineType type;
        private final int requiredPrestige;
        private final String requiredRank;
        private final String requiredPermission;

        public MineData(String id, String worldName, int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ, Map<Material, Double> blockComposition,
                        String displayName, String description, long rankupPrice,
                        MineType type, int requiredPrestige, String requiredRank,
                        String requiredPermission) {
            this.id = id;
            this.worldName = worldName;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.blockComposition = new HashMap<>(blockComposition);
            this.displayName = displayName;
            this.description = description;
            this.rankupPrice = rankupPrice;
            this.type = type;
            this.requiredPrestige = requiredPrestige;
            this.requiredRank = requiredRank;
            this.requiredPermission = requiredPermission;
        }

        // Getters
        public String getId() { return id; }
        public String getWorldName() { return worldName; }
        public int getMinX() { return minX; }
        public int getMinY() { return minY; }
        public int getMinZ() { return minZ; }
        public int getMaxX() { return maxX; }
        public int getMaxY() { return maxY; }
        public int getMaxZ() { return maxZ; }
        public Map<Material, Double> getBlockComposition() { return new HashMap<>(blockComposition); }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public long getRankupPrice() { return rankupPrice; }
        public MineType getType() { return type; }
        public int getRequiredPrestige() { return requiredPrestige; }
        public String getRequiredRank() { return requiredRank; }
        public String getRequiredPermission() { return requiredPermission; }

        /**
         * Vérifie si la mine contient des beacons
         */
        public boolean hasBeacons() {
            return blockComposition.containsKey(Material.BEACON) && blockComposition.get(Material.BEACON) > 0;
        }

        /**
         * Obtient le taux de beacons de la mine
         */
        public double getBeaconRate() {
            return blockComposition.getOrDefault(Material.BEACON, 0.0) * 100;
        }

        /**
         * Vérifie si un point est dans la mine
         */
        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /**
         * Calcule le volume de la mine
         */
        public int getVolume() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }
}