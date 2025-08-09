package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.cristaux.CristalType;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire des cristaux - MODIFIÉ pour sauvegarder dans PlayerData
 */
public class CristalManager {

    // Coûts pour appliquer les cristaux (en XP joueur)
    private static final long[] APPLICATION_COSTS = {1000, 2500, 5000, 5000}; // 1er, 2e, 3e, 4e cristal
    private final PrisonTycoon plugin;
    private final NamespacedKey cristalUuidKey;
    private final NamespacedKey cristalLevelKey;
    private final NamespacedKey cristalTypeKey;
    private final NamespacedKey cristalViergeKey;

    // Cache léger pour éviter de parser la map à chaque bloc miné
    private final java.util.Map<java.util.UUID, CachedCristals> cristalsCache = new java.util.HashMap<>();
    private static final long CRISTALS_CACHE_TTL_MS = 10_000L;

    public CristalManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.cristalUuidKey = new NamespacedKey(plugin, "cristal_uuid");
        this.cristalLevelKey = new NamespacedKey(plugin, "cristal_level");
        this.cristalTypeKey = new NamespacedKey(plugin, "cristal_type");
        this.cristalViergeKey = new NamespacedKey(plugin, "cristal_vierge");

        plugin.getPluginLogger().info("§aCristalManager initialisé.");
    }

    private record CachedCristals(java.util.List<Cristal> list, long tsMs) {}

    private void invalidateCristalCache(java.util.UUID playerId) {
        cristalsCache.remove(playerId);
    }

    /**
     * Crée un cristal vierge du niveau spécifié
     */
    public Cristal createCristalVierge(int niveau) {
        if (niveau < 1 || niveau > 20) {
            throw new IllegalArgumentException("Niveau de cristal invalide: " + niveau);
        }
        return new Cristal(UUID.randomUUID().toString(), niveau, null, true);
    }

    /**
     * Crée un cristal avec un type spécifique (pour tests/admin)
     */
    public Cristal createCristal(int niveau, CristalType type) {
        if (niveau < 1 || niveau > 20) {
            throw new IllegalArgumentException("Niveau de cristal invalide: " + niveau);
        }
        return new Cristal(UUID.randomUUID().toString(), niveau, type, false);
    }

    /**
     * Donne un cristal vierge à un joueur
     */
    public boolean giveCristalToPlayer(Player player, int niveau) {
        Cristal cristal = createCristalVierge(niveau);
        ItemStack cristalItem = cristal.toItemStack(cristalUuidKey, cristalLevelKey, cristalTypeKey, cristalViergeKey);

        // Vérification de place dans l'inventaire
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cVotre inventaire est plein!");
            return false;
        }

        player.getInventory().addItem(cristalItem);
        player.sendMessage("§d✨ Vous avez reçu un §dCristal Vierge §7(Niveau " + niveau + ")§d!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        plugin.getPluginLogger().info("§7Cristal vierge niveau " + niveau + " donné à " + player.getName());
        return true;
    }

    public ItemStack createCristalViergeApi(int niveau) {
        Cristal cristal = createCristalVierge(niveau);
        ItemStack cristalItem = cristal.toItemStack(cristalUuidKey, cristalLevelKey, cristalTypeKey, cristalViergeKey);
        return cristalItem;
    }

    /**
     * Donne un cristal avec type spécifique à un joueur (pour admins)
     */
    public boolean giveCristalToPlayer(Player player, int niveau, CristalType type) {
        Cristal cristal = createCristal(niveau, type);
        ItemStack cristalItem = cristal.toItemStack(cristalUuidKey, cristalLevelKey, cristalTypeKey, cristalViergeKey);

        // Vérification de place dans l'inventaire
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§cVotre inventaire est plein!");
            return false;
        }

        player.getInventory().addItem(cristalItem);
        player.sendMessage("§d✨ Vous avez reçu un §dCristal " + type.getDisplayName() + " §7(Niveau " + niveau + ")§d!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        plugin.getPluginLogger().info("§7Cristal " + type.getDisplayName() + " niveau " + niveau + " donné à " + player.getName());
        return true;
    }

    /**
     * Vérifie si un ItemStack est un cristal
     */
    public boolean isCristal(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(cristalUuidKey, PersistentDataType.STRING);
    }

    /**
     * Récupère les données d'un cristal depuis un ItemStack
     */
    public Cristal getCristalFromItem(ItemStack item) {
        if (!isCristal(item)) return null;

        ItemMeta meta = item.getItemMeta();
        String uuid = meta.getPersistentDataContainer().get(cristalUuidKey, PersistentDataType.STRING);
        Integer niveau = meta.getPersistentDataContainer().get(cristalLevelKey, PersistentDataType.INTEGER);
        Boolean vierge = meta.getPersistentDataContainer().get(cristalViergeKey, PersistentDataType.BOOLEAN);

        if (uuid == null || niveau == null || vierge == null) return null;

        CristalType type = null;
        if (!vierge) {
            String typeName = meta.getPersistentDataContainer().get(cristalTypeKey, PersistentDataType.STRING);
            if (typeName != null) {
                try {
                    type = CristalType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                    plugin.getPluginLogger().warning("Type de cristal invalide: " + typeName);
                    return null;
                }
            }
        }

        return new Cristal(uuid, niveau, type, vierge);
    }

    /**
     * Révèle un cristal vierge
     */
    public ItemStack revealCristal(ItemStack cristalItem) {
        Cristal cristal = getCristalFromItem(cristalItem);
        if (cristal == null || !cristal.isVierge()) return cristalItem;

        Cristal revealedCristal = cristal.revealType();
        return revealedCristal.toItemStack(cristalUuidKey, cristalLevelKey, cristalTypeKey, cristalViergeKey);
    }

    /**
     * MODIFIÉ : Applique un cristal sur une pioche (sauvegarde dans PlayerData)
     */
    public boolean applyCristalToPickaxe(Player player, ItemStack pickaxe, Cristal cristal) {
        if (!plugin.getPickaxeManager().isLegendaryPickaxe(pickaxe)) {
            player.sendMessage("§cCe cristal ne peut être appliqué que sur une pioche légendaire!");
            return false;
        }

        if (cristal.isVierge()) {
            player.sendMessage("§cVous devez révéler le cristal avant de l'appliquer!");
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        List<Cristal> currentCristals = getPickaxeCristals(player);

        // Vérification du nombre maximum
        if (currentCristals.size() >= 4) {
            player.sendMessage("§cCette pioche a déjà le maximum de cristaux (4)!");
            return false;
        }

        // Vérification de compatibilité (un seul cristal du même type)
        for (Cristal existing : currentCristals) {
            if (!cristal.getType().isCompatibleWith(existing.getType())) {
                player.sendMessage("§cUn cristal de type §d" + existing.getType().getDisplayName() +
                        " §cest déjà appliqué sur cette pioche!");
                return false;
            }
        }

        // Vérification du coût en XP
        long cost = APPLICATION_COSTS[currentCristals.size()];

        if (playerData.getExperience() < cost) {
            player.sendMessage("§cVous n'avez pas assez d'XP! Coût: §e" + cost + " XP");
            return false;
        }

        // Application du cristal
        playerData.removeExperience(cost);
        plugin.getEconomyManager().updateVanillaExpFromCustom(player, playerData.getExperience());

        // NOUVEAU : Sauvegarde dans PlayerData au lieu de la pioche
        String cristalData = cristal.getNiveau() + "," + cristal.getType().name();
        playerData.setPickaxeCristal(cristal.getUuid(), cristalData);
        invalidateCristalCache(player.getUniqueId());

        // Mise à jour de la pioche
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Récupérer la pioche mise à jour
            ItemStack updatedPickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
            if (updatedPickaxe != null) {
                ItemMeta meta = updatedPickaxe.getItemMeta();
                plugin.getPickaxeManager().updatePickaxeLore(meta, player);
                updatedPickaxe.setItemMeta(meta);
            }
        }, 1L);

        player.sendMessage("§d✨ Cristal §d" + cristal.getType().getDisplayName() +
                " §7(Niveau " + cristal.getNiveau() + ") §aappliqué!");
        player.sendMessage("§7Coût: §c-" + cost + " XP");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * MODIFIÉ : Retire un cristal d'une pioche (depuis PlayerData)
     */
    public ItemStack removeCristalFromPickaxe(Player player, ItemStack pickaxe, String cristalUuid) {
        if (!plugin.getPickaxeManager().isLegendaryPickaxe(pickaxe)) {
            player.sendMessage("§cCette action n'est possible que sur une pioche légendaire!");
            return null;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        List<Cristal> currentCristals = getPickaxeCristals(player);
        Cristal toRemove = null;

        // Recherche du cristal à retirer
        for (Cristal cristal : currentCristals) {
            if (cristal.getUuid().equals(cristalUuid)) {
                toRemove = cristal;
                break;
            }
        }

        if (toRemove == null) {
            player.sendMessage("§cCristal introuvable sur cette pioche!");
            return null;
        }

        // Suppression du PlayerData
        playerData.removePickaxeCristal(cristalUuid);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        invalidateCristalCache(player.getUniqueId());

        // Mise à jour de la pioche
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            ItemStack updatedPickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
            if (updatedPickaxe != null) {
                ItemMeta meta = updatedPickaxe.getItemMeta();
                plugin.getPickaxeManager().updatePickaxeLore(meta, player);
                updatedPickaxe.setItemMeta(meta);
            }
        }, 1L);

        // 50% de chance de récupération
        if (Math.random() < 0.5) {
            player.sendMessage("§a✓ Cristal récupéré!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            return toRemove.toItemStack(cristalUuidKey, cristalLevelKey, cristalTypeKey, cristalViergeKey);
        } else {
            player.sendMessage("§c✗ Cristal détruit lors du retrait!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            return null;
        }
    }

    /**
     * MODIFIÉ : Récupère les cristaux de la pioche depuis PlayerData
     */
    public List<Cristal> getPickaxeCristals(Player player) {
        UUID pid = player.getUniqueId();
        long now = System.currentTimeMillis();
        CachedCristals cached = cristalsCache.get(pid);
        if (cached != null && (now - cached.tsMs) <= CRISTALS_CACHE_TTL_MS) {
            // Retourne une copie pour éviter toute modification externe
            return new ArrayList<>(cached.list);
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(pid);
        Map<String, String> pickaxeCristals = playerData.getPickaxeCristals();

        List<Cristal> cristals = new ArrayList<>(pickaxeCristals.size());
        for (Map.Entry<String, String> entry : pickaxeCristals.entrySet()) {
            String value = entry.getValue();
            int comma = value.indexOf(',');
            if (comma <= 0 || comma >= value.length() - 1) {
                continue;
            }
            try {
                String uuid = entry.getKey();
                int niveau = Integer.parseInt(value.substring(0, comma));
                CristalType type = CristalType.valueOf(value.substring(comma + 1));
                cristals.add(new Cristal(uuid, niveau, type, false));
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Erreur de parsing cristal: " + value);
            }
        }

        cristalsCache.put(pid, new CachedCristals(cristals, now));
        return new ArrayList<>(cristals);
    }

    /**
     * Calcule le bonus total d'un type de cristal sur une pioche
     */
    public double getTotalCristalBonus(Player player, CristalType type) {
        List<Cristal> cristals = getPickaxeCristals(player);
        for (Cristal cristal : cristals) {
            if (cristal.getType() == type) {
                return cristal.getType().getBonus(cristal.getNiveau());
            }
        }

        return 0;
    }

    /**
     * Récupère le coût d'application pour le prochain cristal
     */
    public long getApplicationCost(Player player) {
        List<Cristal> currentCristals = getPickaxeCristals(player);
        int count = currentCristals.size();

        if (count >= 4) return -1; // Maximum atteint
        return APPLICATION_COSTS[count];
    }

    // Getters pour les clés
    public NamespacedKey getCristalUuidKey() {
        return cristalUuidKey;
    }

    public NamespacedKey getCristalLevelKey() {
        return cristalLevelKey;
    }

    public NamespacedKey getCristalTypeKey() {
        return cristalTypeKey;
    }

    public NamespacedKey getCristalViergeKey() {
        return cristalViergeKey;
    }
}