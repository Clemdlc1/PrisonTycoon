package fr.prisontycoon.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.Objects;

/**
 * Classe de données pour un warp
 * Représente un point de téléportation configurable avec toutes ses propriétés
 */
public class WarpData {

    /**
     * Types de warps disponibles avec leurs icônes et noms d'affichage
     */
    public enum WarpType {
        SPAWN("§e⭐", "Spawn", "Point de spawn principal"),
        MINE("§6⛏", "Mine", "Mines de ressources"),
        CRATE("§d📦", "Crates", "Caisses et récompenses"),
        CAVE("§8🕳", "Caves", "Grottes et donjons"),
        SHOP("§a🏪", "Magasins", "Zones commerciales"),
        PVP("§c⚔", "PvP", "Zones de combat"),
        EVENT("§5🎉", "Événement", "Zones d'événements"),
        OTHER("§7🔗", "Autre", "Autres destinations");

        private final String icon;
        private final String displayName;
        private final String description;

        WarpType(String icon, String displayName, String description) {
            this.icon = icon;
            this.displayName = displayName;
            this.description = description;
        }

        public String getIcon() { return icon; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }

        /**
         * Obtient le nom formaté avec l'icône
         */
        public String getFormattedName() {
            return icon + " §f" + displayName;
        }
    }

    // Identification
    private final String id;
    private final String displayName;
    private final WarpType type;

    // Position
    private final String worldName;
    private final double x, y, z;
    private final float yaw, pitch;

    // Permissions et accès
    private final String permission;
    private final boolean enabled;

    // Affichage dans le GUI
    private final Material headMaterial;
    private final String headTexture;
    private final String description;

    /**
     * Constructeur principal complet
     */
    public WarpData(String id, String displayName, WarpType type, String worldName,
                    double x, double y, double z, float yaw, float pitch,
                    String permission, Material headMaterial, String headTexture,
                    boolean enabled, String description) {

        // Validation des paramètres obligatoires
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("L'ID du warp ne peut pas être vide");
        }
        if (type == null) {
            throw new IllegalArgumentException("Le type du warp ne peut pas être null");
        }
        if (worldName == null || worldName.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du monde ne peut pas être vide");
        }

        // Assignation des valeurs
        this.id = id.toLowerCase().trim();
        this.displayName = displayName != null && !displayName.trim().isEmpty() ? displayName : id;
        this.type = type;
        this.worldName = worldName.trim();
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.permission = permission != null && !permission.trim().isEmpty() ? permission.trim() : null;
        this.headMaterial = headMaterial != null ? headMaterial : Material.PLAYER_HEAD;
        this.headTexture = headTexture != null && !headTexture.trim().isEmpty() ? headTexture.trim() : null;
        this.enabled = enabled;
        this.description = description != null ? description.trim() : "";
    }

    /**
     * Constructeur simplifié avec position seulement
     */
    public WarpData(String id, String displayName, WarpType type, String worldName,
                    double x, double y, double z) {
        this(id, displayName, type, worldName, x, y, z, 0f, 0f,
                null, Material.PLAYER_HEAD, null, true, "");
    }

    /**
     * Constructeur avec Location Bukkit
     */
    public WarpData(String id, String displayName, WarpType type, Location location) {
        this(id, displayName, type,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                null, Material.PLAYER_HEAD, null, true, "");
    }

    /**
     * Constructeur de copie avec modifications
     */
    public WarpData(WarpData original, String newDisplayName, boolean newEnabled) {
        this(original.id, newDisplayName, original.type, original.worldName,
                original.x, original.y, original.z, original.yaw, original.pitch,
                original.permission, original.headMaterial, original.headTexture,
                newEnabled, original.description);
    }

    // ====== GETTERS PRINCIPAUX ======

    /**
     * Obtient l'ID unique du warp
     */
    public String getId() {
        return id;
    }

    /**
     * Obtient le nom d'affichage du warp
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Obtient le type du warp
     */
    public WarpType getType() {
        return type;
    }

    /**
     * Obtient le nom du monde
     */
    public String getWorldName() {
        return worldName;
    }

    /**
     * Obtient la coordonnée X
     */
    public double getX() {
        return x;
    }

    /**
     * Obtient la coordonnée Y
     */
    public double getY() {
        return y;
    }

    /**
     * Obtient la coordonnée Z
     */
    public double getZ() {
        return z;
    }

    /**
     * Obtient l'orientation (yaw)
     */
    public float getYaw() {
        return yaw;
    }

    /**
     * Obtient l'inclinaison (pitch)
     */
    public float getPitch() {
        return pitch;
    }

    /**
     * Obtient la permission requise (peut être null)
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Obtient le matériau pour l'affichage dans le GUI
     */
    public Material getHeadMaterial() {
        return headMaterial;
    }

    /**
     * Obtient la texture personnalisée (peut être null)
     */
    public String getHeadTexture() {
        return headTexture;
    }

    /**
     * Vérifie si le warp est activé
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Obtient la description du warp
     */
    public String getDescription() {
        return description;
    }

    // ====== MÉTHODES UTILITAIRES ======

    /**
     * Obtient la Location Bukkit de ce warp
     */
    public Location getLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Vérifie si ce warp est accessible (monde existant et activé)
     */
    public boolean isAccessible() {
        return enabled && Bukkit.getWorld(worldName) != null;
    }

    /**
     * Vérifie si le warp a une permission requise
     */
    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    /**
     * Vérifie si le warp a une texture personnalisée
     */
    public boolean hasCustomTexture() {
        return headTexture != null && !headTexture.isEmpty();
    }

    /**
     * Obtient la description formatée pour l'affichage
     */
    public String getFormattedDescription() {
        if (description == null || description.trim().isEmpty()) {
            return "§7" + type.getDescription();
        }
        return "§7" + description;
    }

    /**
     * Obtient le nom formaté avec l'icône du type
     */
    public String getFormattedName() {
        return type.getIcon() + " §f" + displayName;
    }

    /**
     * Obtient les coordonnées sous forme de texte
     */
    public String getFormattedCoordinates() {
        return String.format("§e%d, %d, %d", (int)x, (int)y, (int)z);
    }

    /**
     * Obtient les informations complètes du warp pour l'affichage
     */
    public String getDetailedInfo() {
        StringBuilder info = new StringBuilder();

        info.append("§7Type: ").append(type.getFormattedName()).append("\n");
        info.append("§7Monde: §e").append(worldName).append("\n");
        info.append("§7Position: ").append(getFormattedCoordinates()).append("\n");

        if (yaw != 0f || pitch != 0f) {
            info.append("§7Orientation: §e").append(String.format("%.1f°, %.1f°", yaw, pitch)).append("\n");
        }

        if (hasPermission()) {
            info.append("§7Permission: §e").append(permission).append("\n");
        }

        info.append("§7État: ").append(enabled ? "§aActivé" : "§cDésactivé").append("\n");

        if (!description.isEmpty()) {
            info.append("§7Description: ").append(getFormattedDescription());
        }

        return info.toString();
    }

    /**
     * Calcule la distance à une autre position
     */
    public double getDistanceTo(double otherX, double otherY, double otherZ) {
        double dx = x - otherX;
        double dy = y - otherY;
        double dz = z - otherZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calcule la distance à un joueur (si dans le même monde)
     */
    public double getDistanceTo(org.bukkit.entity.Player player) {
        if (!player.getWorld().getName().equals(worldName)) {
            return -1; // Pas dans le même monde
        }

        Location playerLoc = player.getLocation();
        return getDistanceTo(playerLoc.getX(), playerLoc.getY(), playerLoc.getZ());
    }

    /**
     * Vérifie si le warp est dans le même monde qu'une location
     */
    public boolean isInSameWorld(Location location) {
        return location.getWorld().getName().equals(worldName);
    }

    /**
     * Crée une copie du warp avec un nouveau nom d'affichage
     */
    public WarpData withDisplayName(String newDisplayName) {
        return new WarpData(id, newDisplayName, type, worldName, x, y, z, yaw, pitch,
                permission, headMaterial, headTexture, enabled, description);
    }

    /**
     * Crée une copie du warp avec un nouvel état d'activation
     */
    public WarpData withEnabled(boolean newEnabled) {
        return new WarpData(id, displayName, type, worldName, x, y, z, yaw, pitch,
                permission, headMaterial, headTexture, newEnabled, description);
    }

    /**
     * Crée une copie du warp avec une nouvelle description
     */
    public WarpData withDescription(String newDescription) {
        return new WarpData(id, displayName, type, worldName, x, y, z, yaw, pitch,
                permission, headMaterial, headTexture, enabled, newDescription);
    }

    // ====== MÉTHODES STANDARD ======

    @Override
    public String toString() {
        return "WarpData{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", type=" + type +
                ", world='" + worldName + '\'' +
                ", pos=[" + String.format("%.1f, %.1f, %.1f", x, y, z) + "]" +
                (yaw != 0f || pitch != 0f ? ", rot=[" + String.format("%.1f°, %.1f°", yaw, pitch) + "]" : "") +
                (hasPermission() ? ", perm='" + permission + '\'' : "") +
                ", enabled=" + enabled +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        WarpData warpData = (WarpData) obj;
        return Objects.equals(id, warpData.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Compare les warps par ordre alphabétique de leur nom d'affichage
     */
    public int compareTo(WarpData other) {
        if (other == null) return 1;

        // D'abord par type
        int typeComparison = this.type.ordinal() - other.type.ordinal();
        if (typeComparison != 0) {
            return typeComparison;
        }

        // Ensuite par nom d'affichage
        return this.displayName.compareToIgnoreCase(other.displayName);
    }

    // ====== MÉTHODES DE VALIDATION ======

    /**
     * Valide que toutes les données du warp sont cohérentes
     */
    public boolean isValid() {
        return id != null && !id.trim().isEmpty() &&
                displayName != null && !displayName.trim().isEmpty() &&
                type != null &&
                worldName != null && !worldName.trim().isEmpty() &&
                headMaterial != null;
    }

    /**
     * Obtient une liste des problèmes de validation
     */
    public java.util.List<String> getValidationErrors() {
        java.util.List<String> errors = new java.util.ArrayList<>();

        if (id == null || id.trim().isEmpty()) {
            errors.add("ID manquant ou vide");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            errors.add("Nom d'affichage manquant ou vide");
        }
        if (type == null) {
            errors.add("Type manquant");
        }
        if (worldName == null || worldName.trim().isEmpty()) {
            errors.add("Nom du monde manquant ou vide");
        }
        if (headMaterial == null) {
            errors.add("Matériau de tête manquant");
        }
        if (!isAccessible()) {
            errors.add("Warp non accessible (désactivé ou monde inexistant)");
        }

        return errors;
    }
}