package fr.skyblock.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class SkyblockPlayer {

    private static final Gson gson = new GsonBuilder().create();

    private UUID uuid;
    private String name;
    private UUID islandId; // ID de l'île dont il est propriétaire
    private Set<UUID> memberOfIslands; // Îles dont il est membre
    private long firstJoin;
    private long lastSeen;
    private boolean hasIsland;
    private int islandResets; // Nombre de fois qu'il a reset son île
    private Map<String, Object> data; // Données supplémentaires

    public SkyblockPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.islandId = null;
        this.memberOfIslands = new HashSet<>();
        this.firstJoin = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
        this.hasIsland = false;
        this.islandResets = 0;
        this.data = new HashMap<>();
    }

    // Méthodes utiles
    public void createIsland(UUID islandId) {
        this.islandId = islandId;
        this.hasIsland = true;
    }

    public void deleteIsland() {
        this.islandId = null;
        this.hasIsland = false;
        this.islandResets++;
    }

    public void joinIsland(UUID islandId) {
        memberOfIslands.add(islandId);
    }

    public void leaveIsland(UUID islandId) {
        memberOfIslands.remove(islandId);
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static SkyblockPlayer fromJson(String json) {
        return gson.fromJson(json, SkyblockPlayer.class);
    }

    // Getters et Setters
    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getIslandId() { return islandId; }
    public boolean hasIsland() { return hasIsland; }
    public Set<UUID> getMemberOfIslands() { return new HashSet<>(memberOfIslands); }
    public long getFirstJoin() { return firstJoin; }
    public void setFirstJoin(long firstJoin) { this.firstJoin = firstJoin; }
    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }
    public int getIslandResets() { return islandResets; }
    public void setIslandResets(int islandResets) { this.islandResets = islandResets; }

    // Gestion des données supplémentaires
    public void setData(String key, Object value) {
        data.put(key, value);
    }

    public Object getData(String key) {
        return data.get(key);
    }

    public <T> T getData(String key, Class<T> type) {
        Object value = data.get(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return null;
    }

    public boolean hasData(String key) {
        return data.containsKey(key);
    }

    public void removeData(String key) {
        data.remove(key);
    }

    // AJOUT : Getter pour toutes les données
    public Map<String, Object> getData() {
        return new HashMap<>(data);
    }
}