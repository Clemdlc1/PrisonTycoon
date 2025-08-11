package fr.prisontycoon.data;

import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;

/**
 * Données complètes d'une mine - Version avec coordonnées de téléportation
 * MODIFIÉ pour supporter les coordonnées de téléportation personnalisées
 */
public class MineData {

    // Identification
    private final String id;
    private final String name;
    private final String displayName;
    private final String description;

    // Géométrie de la mine
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final Location minCorner;
    private final Location maxCorner;
    private final BlockVector3 minPos;
    private final BlockVector3 maxPos;

    // NOUVEAU: Coordonnées de téléportation personnalisées
    private final double teleportX;
    private final double teleportY;
    private final double teleportZ;
    private final float teleportYaw;
    private final float teleportPitch;

    // Composition des blocs
    private final Map<Material, Double> blockComposition;
    private final List<Material> weightedMaterials;

    // Propriétés économiques
    private final long rankupPrice;
    private final double beaconRate;

    // Accès et permissions
    private final MineType type;
    private final int requiredPrestige;
    private final String requiredRank;
    private final String requiredPermission;
    private final boolean vipOnly;

    // Propriétés de gestion
    private final int resetIntervalMinutes;

    // Statistiques
    private final int volume;

    /**
     * Constructeur principal avec coordonnées de téléportation
     */
    public MineData(String id, String worldName, int minX, int minY, int minZ,
                    int maxX, int maxY, int maxZ, Map<Material, Double> blockComposition,
                    String displayName, String description, long rankupPrice,
                    MineType type, int requiredPrestige, String requiredRank,
                    String requiredPermission, double beaconRate, boolean vipOnly,
                    int resetIntervalMinutes, World world,
                    double teleportX, double teleportY, double teleportZ,
                    float teleportYaw, float teleportPitch) {

        // Validation des paramètres de base
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("L'ID de la mine ne peut pas être vide");
        }
        if (blockComposition == null || blockComposition.isEmpty()) {
            throw new IllegalArgumentException("La composition des blocs ne peut pas être vide");
        }

        // Identification
        this.id = id.toLowerCase();
        this.name = id; // Pour compatibilité
        this.displayName = displayName != null ? displayName : id.toUpperCase();
        this.description = description != null ? description : "Mine " + id.toUpperCase();

        // Géométrie de la mine (normalisation des coordonnées)
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);

        // Coordonnées BlockVector3 pour WorldEdit
        this.minPos = BlockVector3.at(this.minX, this.minY, this.minZ);
        this.maxPos = BlockVector3.at(this.maxX, this.maxY, this.maxZ);

        // Création des corners Bukkit
        if (world != null) {
            this.minCorner = new Location(world, this.minX, this.minY, this.minZ);
            this.maxCorner = new Location(world, this.maxX, this.maxY, this.maxZ);
        } else {
            this.minCorner = null;
            this.maxCorner = null;
        }

        // NOUVEAU: Coordonnées de téléportation
        this.teleportX = teleportX;
        this.teleportY = teleportY;
        this.teleportZ = teleportZ;
        this.teleportYaw = teleportYaw;
        this.teleportPitch = teleportPitch;

        // Propriétés de la mine
        this.blockComposition = new HashMap<>(blockComposition);
        this.rankupPrice = rankupPrice;
        this.type = type != null ? type : MineType.NORMAL;
        this.requiredPrestige = requiredPrestige;
        this.requiredRank = requiredRank != null ? requiredRank : "a";
        this.requiredPermission = requiredPermission;
        this.beaconRate = beaconRate;
        this.vipOnly = vipOnly;
        this.resetIntervalMinutes = Math.max(1, resetIntervalMinutes);

        // Calcul du volume
        this.volume = (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);

        // Précalcule la liste pondérée pour l'optimisation
        this.weightedMaterials = buildWeightedList();
    }

    /**
     * Constructeur avec coordonnées de téléportation utilisant BlockVector3
     */
    public MineData(String id, String displayName, String description, MineType type,
                    String worldName, BlockVector3 minPos, BlockVector3 maxPos,
                    Map<Material, Double> blockComposition, int resetIntervalMinutes,
                    double teleportX, double teleportY, double teleportZ,
                    float teleportYaw, float teleportPitch) {

        this(id, worldName,
                minPos.x(), minPos.y(), minPos.z(),
                maxPos.x(), maxPos.y(), maxPos.z(),
                blockComposition, displayName, description, 0,
                type, 0, "a", null, 0.0, false,
                resetIntervalMinutes, null,
                teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
    }

    /**
     * Constructeur de compatibilité (utilise le centre comme téléportation)
     */
    public MineData(String id, String worldName, int minX, int minY, int minZ,
                    int maxX, int maxY, int maxZ, Map<Material, Double> blockComposition,
                    String displayName, String description, long rankupPrice,
                    MineType type, int requiredPrestige, String requiredRank,
                    String requiredPermission, double beaconRate, boolean vipOnly, World world) {

        this(id, worldName, minX, minY, minZ, maxX, maxY, maxZ, blockComposition,
                displayName, description, rankupPrice, type, requiredPrestige, requiredRank,
                requiredPermission, beaconRate, vipOnly, 30, world,
                // Utiliser le centre comme téléportation par défaut
                (Math.min(minX, maxX) + Math.max(minX, maxX)) / 2.0,
                Math.max(minY, maxY) + 1, // Un bloc au-dessus
                (Math.min(minZ, maxZ) + Math.max(minZ, maxZ)) / 2.0,
                0f, 0f);
    }

    /**
     * Constructeur simplifié pour ConfigManager
     */
    public MineData(String name, Location minCorner, Location maxCorner, Map<Material, Double> blockComposition) {
        this(name,
                minCorner.getWorld().getName(),
                minCorner.getBlockX(), minCorner.getBlockY(), minCorner.getBlockZ(),
                maxCorner.getBlockX(), maxCorner.getBlockY(), maxCorner.getBlockZ(),
                blockComposition,
                name.toUpperCase(),
                "Mine " + name.toUpperCase(),
                0,
                MineType.NORMAL,
                0,
                "a",
                null,
                0.0,
                false,
                minCorner.getWorld());
    }

    /**
     * Construit une liste pondérée des matériaux pour génération rapide
     */
    private List<Material> buildWeightedList() {
        List<Material> list = new ArrayList<>();

        for (Map.Entry<Material, Double> entry : blockComposition.entrySet()) {
            Material material = entry.getKey();
            double percentage = entry.getValue();

            // Ajouter le matériau un nombre de fois proportionnel à son pourcentage
            int weight = (int) Math.round(percentage * 1000);
            for (int i = 0; i < weight; i++) {
                list.add(material);
            }
        }

        return list;
    }

    // ====== GETTERS EXISTANTS ======

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public Location getMinCorner() {
        return minCorner != null ? minCorner.clone() : null;
    }

    public Location getMaxCorner() {
        return maxCorner != null ? maxCorner.clone() : null;
    }

    public BlockVector3 getMinPos() {
        return minPos;
    }

    public BlockVector3 getMaxPos() {
        return maxPos;
    }

    public Map<Material, Double> getBlockComposition() {
        return new HashMap<>(blockComposition);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public long getRankupPrice() {
        return rankupPrice;
    }

    public double getBeaconRate() {
        return beaconRate;
    }

    public MineType getType() {
        return type;
    }

    public int getRequiredPrestige() {
        return requiredPrestige;
    }

    public String getRequiredRank() {
        return requiredRank;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public boolean isVipOnly() {
        return vipOnly;
    }

    public int getVolume() {
        return volume;
    }

    public int getResetIntervalMinutes() {
        return resetIntervalMinutes;
    }

    public List<Material> getWeightedMaterials() {
        return new ArrayList<>(weightedMaterials);
    }

    // ====== NOUVEAUX GETTERS POUR LA TÉLÉPORTATION ======

    /**
     * Obtient les coordonnées de téléportation personnalisées
     */
    public double getTeleportX() {
        return teleportX;
    }

    public double getTeleportY() {
        return teleportY;
    }

    public double getTeleportZ() {
        return teleportZ;
    }

    public float getTeleportYaw() {
        return teleportYaw;
    }

    public float getTeleportPitch() {
        return teleportPitch;
    }

    /**
     * Obtient la location de téléportation personnalisée
     */
    public Location getTeleportLocation(World world) {
        if (world == null) {
            return null;
        }
        return new Location(world, teleportX, teleportY, teleportZ, teleportYaw, teleportPitch);
    }

    /**
     * Obtient la location du centre de la mine (ancienne méthode)
     */
    public Location getCenterLocation(World world) {
        if (world == null) {
            return null;
        }
        double centerX = (minX + maxX) / 2.0;
        double centerY = maxY + 1; // Un bloc au-dessus
        double centerZ = (minZ + maxZ) / 2.0;
        return new Location(world, centerX, centerY, centerZ);
    }

    /**
     * Vérifie si des coordonnées de téléportation personnalisées sont définies
     */
    public boolean hasCustomTeleportLocation() {
        // Si les coordonnées ne sont pas au centre, c'est personnalisé
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double centerY = maxY + 1;

        return Math.abs(teleportX - centerX) > 0.1 ||
                Math.abs(teleportY - centerY) > 0.1 ||
                Math.abs(teleportZ - centerZ) > 0.1;
    }

    // ====== MÉTHODES UTILITAIRES ======

    /**
     * Vérifie si une location se trouve dans cette mine
     */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        // Vérifier que c'est le bon monde
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }

        // Vérifier les coordonnées
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= minX && x <= maxX &&
                y >= minY && y <= maxY &&
                z >= minZ && z <= maxZ;
    }

    /**
     * Vérifie si des coordonnées se trouvent dans cette mine
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX &&
                y >= minY && y <= maxY &&
                z >= minZ && z <= maxZ;
    }

    /**
     * Vérifie si un joueur se trouve dans cette mine
     */
    public boolean contains(org.bukkit.entity.Player player) {
        return contains(player.getLocation());
    }

    /**
     * Vérifie si la mine contient des beacons
     */
    public boolean hasBeacons() {
        return beaconRate > 0;
    }

    /**
     * Obtient des informations détaillées sur la mine
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("§7Type: ").append(getTypeColor()).append(type.name()).append("\n");
        info.append("§7Monde: §e").append(worldName).append("\n");
        info.append("§7Volume: §e").append(volume).append(" blocs\n");
        info.append("§7Reset: §e").append(resetIntervalMinutes).append(" minutes\n");

        if (hasCustomTeleportLocation()) {
            info.append("§7Téléportation: §e").append((int) teleportX).append(", ")
                    .append((int) teleportY).append(", ").append((int) teleportZ).append("\n");
        } else {
            info.append("§7Téléportation: §7Centre de la mine\n");
        }

        if (hasBeacons()) {
            info.append("§7Beacons: §6").append(String.format("%.1f%%", beaconRate)).append("\n");
        }

        return info.toString();
    }

    /**
     * Obtient la couleur associée au type de mine
     */
    public String getTypeColor() {
        return switch (type) {
            case NORMAL -> "§f";
            case PRESTIGE -> "§d";
            case VIP -> "§6";
        };
    }

    /**
     * Obtient le matériau de tête pour le GUI selon le type
     */
    public Material getHeadMaterial() {
        return switch (type) {
            case NORMAL -> Material.IRON_PICKAXE;
            case PRESTIGE -> Material.DIAMOND_PICKAXE;
            case VIP -> Material.NETHERITE_PICKAXE;
        };
    }

    @Override
    public String toString() {
        return "MineData{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", type=" + type +
                ", world='" + worldName + '\'' +
                ", region=[" + minX + "," + minY + "," + minZ + " to " + maxX + "," + maxY + "," + maxZ + "]" +
                ", teleport=[" + teleportX + ", " + teleportY + ", " + teleportZ + "]" +
                ", volume=" + volume +
                ", blocks=" + blockComposition.size() +
                ", resetInterval=" + resetIntervalMinutes + "min" +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MineData mineData = (MineData) obj;
        return Objects.equals(id, mineData.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Types de mines
     */
    public enum MineType {
        NORMAL,     // Mines A-Z normales
        PRESTIGE,   // Mines prestige (P1, P11, etc.)
        VIP;        // Mines VIP

        /**
         * Obtient le matériau de tête pour ce type
         */
        public Material getHeadMaterial() {
            return switch (this) {
                case NORMAL -> Material.IRON_PICKAXE;
                case PRESTIGE -> Material.DIAMOND_PICKAXE;
                case VIP -> Material.NETHERITE_PICKAXE;
            };
        }

        /**
         * Obtient la couleur pour ce type
         */
        public String getColor() {
            return switch (this) {
                case NORMAL -> "§f";
                case PRESTIGE -> "§d";
                case VIP -> "§6";
            };
        }
    }
}