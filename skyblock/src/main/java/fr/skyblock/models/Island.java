package fr.skyblock.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class Island {

    private static final Gson gson = new GsonBuilder().create();

    private UUID id;
    private UUID owner;
    private String name;
    private Location center;
    private int size;
    private int level;
    private double bank;
    private Set<UUID> members;
    private Set<UUID> officers; // Nouveaux: officiers de l'île (peuvent gérer les structures)
    private Set<UUID> visitors;
    private Map<IslandFlag, Boolean> flags;
    private long creationTime;
    private long lastActivity;
    
    // Améliorations d'île pour le système d'imprimantes
    private int maxDepositBoxes = 1;
    private int maxHoppers = 5;
    private double hopperTransferSpeed = 1.0;
    private int maxPrinters = 10;
    private double printerGenerationSpeed = 1.0;
    
    // Caisses de dépôt de l'île
    private final Map<String, DepositBoxData> depositBoxes = new HashMap<>();
    
    // Imprimantes de l'île
    private final Map<String, PrinterData> printers = new HashMap<>();

    public enum IslandFlag {
        PVP("Autoriser le PvP"),
        MOB_SPAWNING("Spawn des mobs"),
        ANIMAL_SPAWNING("Spawn des animaux"),
        FIRE_SPREAD("Propagation du feu"),
        EXPLOSION_DAMAGE("Dégâts d'explosion"),
        VISITOR_INTERACT("Interaction des visiteurs"),
        VISITOR_PLACE("Placement des visiteurs"),
        VISITOR_BREAK("Casse des visiteurs"),
        VISITOR_CHEST("Accès coffres visiteurs");

        private final String description;

        IslandFlag(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public Island(UUID id, UUID owner, String name, Location center) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.center = center;
        this.size = 50; // Taille par défaut
        this.level = 1;
        this.bank = 0.0;
        this.members = new HashSet<>();
        this.officers = new HashSet<>(); // Initialiser les officiers
        this.visitors = new HashSet<>();
        this.flags = new HashMap<>();
        this.creationTime = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();

        // Initialisation des flags par défaut
        initializeDefaultFlags();
    }

    private void initializeDefaultFlags() {
        flags.put(IslandFlag.PVP, false);
        flags.put(IslandFlag.MOB_SPAWNING, true);
        flags.put(IslandFlag.ANIMAL_SPAWNING, true);
        flags.put(IslandFlag.FIRE_SPREAD, false);
        flags.put(IslandFlag.EXPLOSION_DAMAGE, false);
        flags.put(IslandFlag.VISITOR_INTERACT, false);
        flags.put(IslandFlag.VISITOR_PLACE, false);
        flags.put(IslandFlag.VISITOR_BREAK, false);
        flags.put(IslandFlag.VISITOR_CHEST, false);
    }

    // Méthodes utiles
    public boolean isMember(UUID player) {
        return owner.equals(player) || members.contains(player);
    }

    public boolean isVisitor(UUID player) {
        return visitors.contains(player);
    }

    public boolean canInteract(UUID player) {
        if (isMember(player)) return true;
        return isVisitor(player) && flags.get(IslandFlag.VISITOR_INTERACT);
    }

    public void addMember(UUID player) {
        members.add(player);
        visitors.remove(player); // Retire des visiteurs s'il était visiteur
    }

    public void removeMember(UUID player) {
        members.remove(player);
        officers.remove(player); // Retirer aussi des officiers si c'était un officier
    }

    public void addOfficer(UUID player) {
        if (isMember(player) || owner.equals(player)) {
            officers.add(player);
        }
    }

    public void removeOfficer(UUID player) {
        officers.remove(player);
    }

    public boolean isOfficer(UUID player) {
        return officers.contains(player);
    }

    public boolean canManageStructures(UUID player) {
        return owner.equals(player) || officers.contains(player);
    }

    public void addVisitor(UUID player) {
        if (!isMember(player)) {
            visitors.add(player);
        }
    }

    public void removeVisitor(UUID player) {
        visitors.remove(player);
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public String toJson() {
        return gson.toJson(this);
    }

    public static Island fromJson(String json) {
        return gson.fromJson(json, Island.class);
    }

    // Getters et Setters
    public UUID getId() { return id; }
    public UUID getOwner() { return owner; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Location getCenter() { return center; }
    public void setCenter(Location center) { this.center = center; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public double getBank() { return bank; }
    public void setBank(double bank) { this.bank = bank; }
    public void addToBank(double amount) { this.bank += amount; }
    public boolean removeFromBank(double amount) {
        if (this.bank >= amount) {
            this.bank -= amount;
            return true;
        }
        return false;
    }
    public Set<UUID> getMembers() { return new HashSet<>(members); }
    public Set<UUID> getOfficers() { return new HashSet<>(officers); }
    public Set<UUID> getVisitors() { return new HashSet<>(visitors); }
    public Map<IslandFlag, Boolean> getFlags() { return new HashMap<>(flags); }
    public void setFlag(IslandFlag flag, boolean value) {
        if (flags == null) {
            flags = new HashMap<>();
            initializeDefaultFlags();
        }
        flags.put(flag, value);
    }
    public boolean getFlag(IslandFlag flag) {
        if (flags == null) {
            flags = new HashMap<>();
            initializeDefaultFlags();
        }
        return flags.getOrDefault(flag, false);
    }
    public long getCreationTime() { return creationTime; }
    public long getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(long lastActivity) {
        this.lastActivity = lastActivity;
    }

    // ==================== AMÉLIORATIONS D'ÎLE ====================

    public int getMaxDepositBoxes() {
        return maxDepositBoxes;
    }

    public void setMaxDepositBoxes(int maxDepositBoxes) {
        this.maxDepositBoxes = Math.max(1, maxDepositBoxes);
    }

    public int getMaxHoppers() {
        return maxHoppers;
    }

    public void setMaxHoppers(int maxHoppers) {
        this.maxHoppers = Math.max(5, maxHoppers);
    }

    public double getHopperTransferSpeed() {
        return hopperTransferSpeed;
    }

    public void setHopperTransferSpeed(double hopperTransferSpeed) {
        this.hopperTransferSpeed = Math.max(1.0, hopperTransferSpeed);
    }

    public int getMaxPrinters() {
        return maxPrinters;
    }

    public void setMaxPrinters(int maxPrinters) {
        this.maxPrinters = Math.max(10, maxPrinters);
    }

    public double getPrinterGenerationSpeed() {
        return printerGenerationSpeed;
    }

    public void setPrinterGenerationSpeed(double printerGenerationSpeed) {
        this.printerGenerationSpeed = Math.max(1.0, printerGenerationSpeed);
    }
    
    // ==================== GESTION DES CAISSES DE DÉPÔT ====================
    
    /**
     * Ajoute une caisse de dépôt à l'île
     */
    public void addDepositBox(DepositBoxData depositBox) {
        depositBoxes.put(depositBox.getId(), depositBox);
    }
    
    /**
     * Supprime une caisse de dépôt de l'île
     */
    public void removeDepositBox(String depositBoxId) {
        depositBoxes.remove(depositBoxId);
    }
    
    /**
     * Obtient une caisse de dépôt par son ID
     */
    public DepositBoxData getDepositBox(String depositBoxId) {
        return depositBoxes.get(depositBoxId);
    }
    
    /**
     * Obtient toutes les caisses de dépôt de l'île
     */
    public Map<String, DepositBoxData> getDepositBoxes() {
        return new HashMap<>(depositBoxes);
    }
    
    /**
     * Obtient le nombre de caisses de dépôt sur l'île
     */
    public int getDepositBoxCount() {
        return depositBoxes.size();
    }
    
    /**
     * Vérifie si on peut placer une caisse de dépôt sur l'île
     */
    public boolean canPlaceDepositBox() {
        return depositBoxes.size() < maxDepositBoxes;
    }
    
    /**
     * Obtient les caisses de dépôt d'un propriétaire spécifique
     */
    public List<DepositBoxData> getDepositBoxesByOwner(UUID owner) {
        return depositBoxes.values().stream()
                .filter(depositBox -> depositBox.getOwner().equals(owner))
                .collect(java.util.stream.Collectors.toList());
    }
    
    // ==================== GESTION DES IMPRIMANTES ====================
    
    /**
     * Ajoute une imprimante à l'île
     */
    public void addPrinter(PrinterData printer) {
        printers.put(printer.getId(), printer);
    }
    
    /**
     * Supprime une imprimante de l'île
     */
    public void removePrinter(String printerId) {
        printers.remove(printerId);
    }
    
    /**
     * Obtient une imprimante par son ID
     */
    public PrinterData getPrinter(String printerId) {
        return printers.get(printerId);
    }
    
    /**
     * Obtient toutes les imprimantes de l'île
     */
    public Map<String, PrinterData> getPrinters() {
        return new HashMap<>(printers);
    }
    
    /**
     * Obtient le nombre d'imprimantes sur l'île
     */
    public int getPrinterCount() {
        return printers.size();
    }
    
    /**
     * Vérifie si on peut placer une imprimante sur l'île
     */
    public boolean canPlacePrinter() {
        return printers.size() < maxPrinters;
    }
    
    /**
     * Obtient les imprimantes d'un propriétaire spécifique
     */
    public List<PrinterData> getPrintersByOwner(UUID owner) {
        return printers.values().stream()
                .filter(printer -> printer.getOwner().equals(owner))
                .collect(java.util.stream.Collectors.toList());
    }
}