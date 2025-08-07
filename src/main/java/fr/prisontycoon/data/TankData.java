package fr.prisontycoon.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

/**
 * Données d'un Tank - Stockage automatique pour l'achat d'items
 */
public class TankData {

    public static final int MAX_CAPACITY = 100_000_000; // 100 millions d'items

    private final String id;
    private final UUID owner;
    private final Map<Material, Integer> contents; // Contenu du tank
    private final Set<Material> filters; // Filtres - quels matériaux accepter
    private final Map<Material, Long> prices; // Prix d'achat par matériau

    // NOUVEAU: Coordonnées du tank placé et nom personnalisé
    private Location location; // Position du tank dans le monde
    private String customName; // Nom personnalisé du tank

    public TankData(String id, UUID owner) {
        this.id = id;
        this.owner = owner;
        this.contents = new HashMap<>();
        this.filters = new HashSet<>();
        this.prices = new HashMap<>();
        this.location = null;
        this.customName = null;
    }

    public TankData(String id, UUID owner, Map<Material, Integer> contents,
                    Set<Material> filters, Map<Material, Long> prices, Location location, String customName) {
        this.id = id;
        this.owner = owner;
        this.contents = new HashMap<>(contents);
        this.filters = new HashSet<>(filters);
        this.prices = new HashMap<>(prices);
        this.location = location;
        this.customName = customName;
    }

    // === GESTION DU CONTENU ===

    /**
     * Crée une instance TankData depuis une ConfigurationSection YAML
     */
    public static TankData fromYaml(ConfigurationSection section) {
        if (section == null) return null;

        try {
            String id = section.getString("id");
            UUID owner = UUID.fromString(section.getString("owner"));
            String customName = section.getString("custom-name", null);

            // Position
            Location location = null;
            if (section.contains("location")) {
                ConfigurationSection locSection = section.getConfigurationSection("location");
                if (locSection != null) {
                    try {
                        String worldName = locSection.getString("world");
                        int x = locSection.getInt("x");
                        int y = locSection.getInt("y");
                        int z = locSection.getInt("z");

                        World world = org.bukkit.Bukkit.getWorld(worldName);
                        if (world != null) {
                            location = new Location(world, x, y, z);
                        }
                    } catch (Exception e) {
                        // Position invalide, ignorer
                    }
                }
            }

            // Contenu
            Map<Material, Integer> contents = new HashMap<>();
            if (section.contains("contents")) {
                ConfigurationSection contentsSection = section.getConfigurationSection("contents");
                if (contentsSection != null) {
                    for (String materialName : contentsSection.getKeys(false)) {
                        try {
                            Material material = Material.valueOf(materialName);
                            int amount = contentsSection.getInt(materialName);
                            if (amount > 0) {
                                contents.put(material, amount);
                            }
                        } catch (IllegalArgumentException e) {
                            // Matériau invalide, ignorer
                        }
                    }
                }
            }

            // Filtres
            Set<Material> filters = new HashSet<>();
            if (section.contains("filters")) {
                for (String materialName : section.getStringList("filters")) {
                    try {
                        Material material = Material.valueOf(materialName);
                        filters.add(material);
                    } catch (IllegalArgumentException e) {
                        // Matériau invalide, ignorer
                    }
                }
            }

            // Prix
            Map<Material, Long> prices = new HashMap<>();
            if (section.contains("prices")) {
                ConfigurationSection pricesSection = section.getConfigurationSection("prices");
                if (pricesSection != null) {
                    for (String materialName : pricesSection.getKeys(false)) {
                        try {
                            Material material = Material.valueOf(materialName);
                            long price = pricesSection.getLong(materialName);
                            if (price > 0) {
                                prices.put(material, price);
                            }
                        } catch (IllegalArgumentException e) {
                            // Matériau invalide, ignorer
                        }
                    }
                }
            }

            TankData tankData = new TankData(id, owner, contents, filters, prices, location, customName);
            return tankData;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ajoute des items au tank
     */
    public boolean addItems(Material material, int amount) {
        if (amount <= 0) return false;

        int currentAmount = contents.getOrDefault(material, 0);
        int newTotal = currentAmount + amount;

        // Vérifier la capacité
        if (getTotalItems() - currentAmount + newTotal > MAX_CAPACITY) {
            return false;
        }

        contents.put(material, newTotal);
        return true;
    }

    /**
     * Retire des items du tank
     */
    public boolean removeItems(Material material, int amount) {
        if (amount <= 0) return false;

        int currentAmount = contents.getOrDefault(material, 0);
        if (currentAmount < amount) return false;

        if (currentAmount == amount) {
            contents.remove(material);
        } else {
            contents.put(material, currentAmount - amount);
        }
        return true;
    }

    /**
     * Vide complètement le tank
     */
    public void clearContents() {
        contents.clear();
    }

    /**
     * Calcule le nombre total d'items dans le tank
     */
    public int getTotalItems() {
        return contents.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Vérifie si le tank peut accepter des items supplémentaires
     */
    public boolean canAddItems(int amount) {
        return getTotalItems() + amount > MAX_CAPACITY;
    }

    // === GESTION DES FILTRES ===

    /**
     * Vérifie si le tank est plein
     */
    public boolean isFull() {
        return getTotalItems() >= MAX_CAPACITY;
    }

    /**
     * Ajoute un matériau aux filtres acceptés
     */
    public void addFilter(Material material) {
        filters.add(material);
    }

    /**
     * Retire un matériau des filtres
     */
    public void removeFilter(Material material) {
        filters.remove(material);
        prices.remove(material); // Retirer aussi le prix associé
    }

    /**
     * Vide tous les filtres
     */
    public void clearFilters() {
        filters.clear();
        prices.clear();
    }

    // === GESTION DES PRIX ===

    /**
     * Vérifie si un matériau est accepté par le tank
     */
    public boolean acceptsMaterial(Material material) {
        return !filters.contains(material);
    }

    /**
     * Définit le prix d'achat pour un matériau
     */
    public void setPrice(Material material, long price) {
        if (price <= 0) {
            prices.remove(material);
        } else {
            prices.put(material, price);
        }
    }

    /**
     * Récupère le prix d'achat pour un matériau
     */
    public long getPrice(Material material) {
        return prices.getOrDefault(material, 0L);
    }

    // === GESTION DE L'ARGENT ===
    // SUPPRIMÉ : L'argent est maintenant géré directement sur le compte du joueur

    // === GESTION DE LA POSITION ===

    /**
     * Vérifie si un prix est configuré pour un matériau
     */
    public boolean hasPriceFor(Material material) {
        return !prices.containsKey(material) || prices.get(material) <= 0;
    }

    /**
     * Récupère la position du tank
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Définit la position du tank placé
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    // === GESTION DU NOM PERSONNALISÉ ===

    /**
     * Vérifie si le tank est placé dans le monde
     */
    public boolean isPlaced() {
        return location != null;
    }

    /**
     * Récupère le nom personnalisé du tank
     */
    public String getCustomName() {
        return customName;
    }

    /**
     * Définit le nom personnalisé du tank
     */
    public void setCustomName(String customName) {
        this.customName = customName;
    }

    // === SÉRIALISATION YAML ===

    /**
     * Vérifie si le tank a un nom personnalisé
     */
    public boolean hasCustomName() {
        return customName != null && !customName.trim().isEmpty();
    }

    /**
     * Convertit les données en ConfigurationSection YAML
     */
    public Map<String, Object> toYaml() {
        Map<String, Object> data = new HashMap<>();

        data.put("id", id);
        data.put("owner", owner.toString());

        // Nom personnalisé
        if (hasCustomName()) {
            data.put("custom-name", customName);
        }

        // Position (si placé)
        if (location != null) {
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("world", location.getWorld().getName());
            locationData.put("x", location.getBlockX());
            locationData.put("y", location.getBlockY());
            locationData.put("z", location.getBlockZ());
            data.put("location", locationData);
        }

        // Contenu
        Map<String, Integer> contentsMap = new HashMap<>();
        for (Map.Entry<Material, Integer> entry : contents.entrySet()) {
            contentsMap.put(entry.getKey().name(), entry.getValue());
        }
        data.put("contents", contentsMap);

        // Filtres
        List<String> filtersList = new ArrayList<>();
        for (Material material : filters) {
            filtersList.add(material.name());
        }
        data.put("filters", filtersList);

        // Prix
        Map<String, Long> pricesMap = new HashMap<>();
        for (Map.Entry<Material, Long> entry : prices.entrySet()) {
            pricesMap.put(entry.getKey().name(), entry.getValue());
        }
        data.put("prices", pricesMap);

        return data;
    }

    // === GETTERS ===

    public String getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public Map<Material, Integer> getContents() {
        return new HashMap<>(contents);
    }

    public Set<Material> getFilters() {
        return new HashSet<>(filters);
    }

    public Map<Material, Long> getPrices() {
        return new HashMap<>(prices);
    }

    @Override
    public TankData clone() {
        return new TankData(id, owner, contents, filters, prices, location, customName);
    }
}