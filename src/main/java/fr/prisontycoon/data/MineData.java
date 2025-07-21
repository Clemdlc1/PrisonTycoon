package fr.prisontycoon.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;

/**
 * Données complètes d'une mine - Classe consolidée
 */
public class MineData {
    // Identification
    private final String id;
    private final String name;
    private final String displayName;
    private final String description;

    // Géométrie
    private final String worldName;
    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;
    private final Location minCorner;
    private final Location maxCorner;

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

    // Statistiques
    private final int volume;

    /**
     * Types de mines
     */
    public enum MineType {
        NORMAL,     // Mines A-Z normales
        PRESTIGE,   // Mines prestige (P1, P11, etc.)
        VIP         // Mines VIP
    }

    /**
     * Constructeur principal
     */
    public MineData(String id, String worldName, int minX, int minY, int minZ,
                    int maxX, int maxY, int maxZ, Map<Material, Double> blockComposition,
                    String displayName, String description, long rankupPrice,
                    MineType type, int requiredPrestige, String requiredRank,
                    String requiredPermission, double beaconRate, boolean vipOnly, World world) {
        this.id = id;
        this.name = id; // Pour compatibilité
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.blockComposition = new HashMap<>(blockComposition);
        this.displayName = displayName != null ? displayName : id.toUpperCase();
        this.description = description != null ? description : "Mine " + id.toUpperCase();
        this.rankupPrice = rankupPrice;
        this.type = type != null ? type : MineType.NORMAL;
        this.requiredPrestige = requiredPrestige;
        this.requiredRank = requiredRank != null ? requiredRank : "a";
        this.requiredPermission = requiredPermission;
        this.beaconRate = beaconRate;
        this.vipOnly = vipOnly;

        // Création des corners
        if (world != null) {
            this.minCorner = new Location(world, this.minX, this.minY, this.minZ);
            this.maxCorner = new Location(world, this.maxX, this.maxY, this.maxZ);
        } else {
            this.minCorner = null;
            this.maxCorner = null;
        }

        // Calcul du volume
        this.volume = (this.maxX - this.minX + 1) * (this.maxY - this.minY + 1) * (this.maxZ - this.minZ + 1);

        // Précalcule la liste pondérée pour l'optimisation
        this.weightedMaterials = buildWeightedList();
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
            int weight = (int) (entry.getValue() * 1000); // Précision à 0.1%
            for (int i = 0; i < weight; i++) {
                list.add(entry.getKey());
            }
        }

        return list;
    }

    /**
     * Vérifie si une location est dans cette mine
     */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        if (minCorner != null && !location.getWorld().equals(minCorner.getWorld())) {
            return false;
        }

        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }

        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();

        return x >= minX && x <= maxX &&
                y >= minY && y <= maxY &&
                z >= minZ && z <= maxZ;
    }

    /**
     * Retourne un matériau aléatoire selon la composition
     */
    public Material getRandomMaterial(Random random) {
        if (weightedMaterials.isEmpty()) {
            return Material.STONE; // Fallback
        }
        return weightedMaterials.get(random.nextInt(weightedMaterials.size()));
    }

    /**
     * Vérifie si la mine contient des beacons
     */
    public boolean hasBeacons() {
        return beaconRate > 0.0;
    }

    /**
     * Retourne le centre de la mine
     */
    public Location getCenterLocation(World world) {
        double centerX = (minX + maxX) / 2.0 + 0.5;
        double centerY = maxY + 1.0; // Au-dessus de la mine
        double centerZ = (minZ + maxZ) / 2.0 + 0.5;

        return new Location(world, centerX, centerY, centerZ);
    }

    /**
     * Valide que la composition des blocs totalise 1.0 (100%)
     */
    public boolean isCompositionValid() {
        double total = blockComposition.values().stream().mapToDouble(Double::doubleValue).sum();
        return Math.abs(total - 1.0) < 0.001; // Tolérance pour erreurs de float
    }

    /**
     * Retourne les informations détaillées de la mine
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();

        info.append("§6Mine: ").append(displayName).append("\n");
        info.append("§7Type: ").append(getMineTypeDisplay()).append("\n");
        info.append("§7Monde: ").append(worldName).append("\n");
        info.append("§7Volume: ").append(volume).append(" blocs\n");
        info.append("§7Coordonnées: §f").append(minX).append(",").append(minY).append(",").append(minZ)
                .append(" §7à §f").append(maxX).append(",").append(maxY).append(",").append(maxZ).append("\n");

        if (rankupPrice > 0) {
            info.append("§7Prix rankup: §a").append(rankupPrice).append("§7$\n");
        }

        if (hasBeacons()) {
            info.append("§7Taux beacons: §e").append(String.format("%.2f%%", beaconRate)).append("\n");
        }

        info.append("§7Composition:\n");
        for (Map.Entry<Material, Double> entry : blockComposition.entrySet()) {
            String materialName = entry.getKey().name().toLowerCase().replace("_", " ");
            double percentage = entry.getValue() * 100;
            String percentageStr = percentage < 1 ?
                    String.format("%.2f%%", percentage) : String.format("%.0f%%", percentage);
            info.append("§7• ").append(materialName).append(": ").append(percentageStr).append("\n");
        }

        return info.toString().trim();
    }

    /**
     * Affichage du type de mine
     */
    private String getMineTypeDisplay() {
        return switch (type) {
            case NORMAL -> "§fNormale";
            case PRESTIGE -> "§dPrestige";
            case VIP -> "§6VIP";
        };
    }

    // ==================== GETTERS ====================

    public String getId() { return id; }
    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public Location getMinCorner() { return minCorner != null ? minCorner.clone() : null; }
    public Location getMaxCorner() { return maxCorner != null ? maxCorner.clone() : null; }
    public Map<Material, Double> getBlockComposition() { return new HashMap<>(blockComposition); }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public long getRankupPrice() { return rankupPrice; }
    public double getBeaconRate() { return beaconRate; }
    public MineType getType() { return type; }
    public int getRequiredPrestige() { return requiredPrestige; }
    public String getRequiredRank() { return requiredRank; }
    public String getRequiredPermission() { return requiredPermission; }
    public boolean isVipOnly() { return vipOnly; }
    public int getVolume() { return volume; }
    public List<Material> getWeightedMaterials() { return new ArrayList<>(weightedMaterials); }

    @Override
    public String toString() {
        return "MineData{id='" + id + "', type=" + type + ", volume=" + volume + "}";
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
}