package fr.skyblock.models;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.UUID;

public class IslandWarp {

    private UUID id;
    private UUID islandId;
    private String name;
    private String description;
    private Location location;
    private boolean isPublic;
    private long creationTime;
    private int visits;

    public IslandWarp(UUID id, UUID islandId, String name, String description, Location location) {
        this.id = id;
        this.islandId = islandId;
        this.name = name;
        this.description = description;
        this.location = location;
        this.isPublic = true;
        this.creationTime = System.currentTimeMillis();
        this.visits = 0;
    }

    // === SAUVEGARDE ET CHARGEMENT YAML ===

    public void saveToYaml(ConfigurationSection section) {
        section.set("id", id.toString());
        section.set("island-id", islandId.toString());
        section.set("name", name);
        section.set("description", description);
        section.set("public", isPublic);
        section.set("creation-time", creationTime);
        section.set("visits", visits);

        // Sauvegarde de la location
        section.set("location.world", location.getWorld().getName());
        section.set("location.x", location.getX());
        section.set("location.y", location.getY());
        section.set("location.z", location.getZ());
        section.set("location.yaw", location.getYaw());
        section.set("location.pitch", location.getPitch());
    }

    public static IslandWarp loadFromYaml(ConfigurationSection section) {
        UUID id = UUID.fromString(section.getString("id"));
        UUID islandId = UUID.fromString(section.getString("island-id"));
        String name = section.getString("name");
        String description = section.getString("description", "");

        // Reconstruction de la location
        String worldName = section.getString("location.world");
        double x = section.getDouble("location.x");
        double y = section.getDouble("location.y");
        double z = section.getDouble("location.z");
        float yaw = (float) section.getDouble("location.yaw");
        float pitch = (float) section.getDouble("location.pitch");

        // Le monde peut être déchargé; on gardera temporairement un monde null ici.
        Location location = new Location(org.bukkit.Bukkit.getWorld(worldName), x, y, z, yaw, pitch);

        IslandWarp warp = new IslandWarp(id, islandId, name, description, location);
        warp.isPublic = section.getBoolean("public", true);
        warp.creationTime = section.getLong("creation-time", System.currentTimeMillis());
        warp.visits = section.getInt("visits", 0);

        return warp;
    }

    // === MÉTHODES UTILES ===

    public void incrementVisits() {
        this.visits++;
    }

    public long getDaysOld() {
        return (System.currentTimeMillis() - creationTime) / (24 * 60 * 60 * 1000);
    }

    public boolean isRecent() {
        return getDaysOld() <= 7; // Considéré comme récent si moins de 7 jours
    }

    // === GETTERS ET SETTERS ===

    public UUID getId() { return id; }

    public UUID getIslandId() { return islandId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public long getCreationTime() { return creationTime; }

    public int getVisits() { return visits; }
    public void setVisits(int visits) { this.visits = visits; }
}