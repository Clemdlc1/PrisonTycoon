package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.cristaux.Cristal;
import fr.prisoncore.prisoncore.prisonTycoon.cristaux.CristalType;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Gestionnaire des cristaux - Gère la création, application et retrait des cristaux
 */
public class CristalManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey cristalUuidKey;
    private final NamespacedKey cristalLevelKey;
    private final NamespacedKey cristalTypeKey;
    private final NamespacedKey cristalViergeKey;
    private final NamespacedKey pickaxeCristalsKey;

    // Coûts pour appliquer les cristaux (en XP joueur)
    private static final long[] APPLICATION_COSTS = {1000, 2500, 5000, 5000}; // 1er, 2e, 3e, 4e cristal

    public CristalManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.cristalUuidKey = new NamespacedKey(plugin, "cristal_uuid");
        this.cristalLevelKey = new NamespacedKey(plugin, "cristal_level");
        this.cristalTypeKey = new NamespacedKey(plugin, "cristal_type");
        this.cristalViergeKey = new NamespacedKey(plugin, "cristal_vierge");
        this.pickaxeCristalsKey = new NamespacedKey(plugin, "pickaxe_cristals");

        plugin.getPluginLogger().info("§aCristalManager initialisé.");
    }

    /**
     * Crée un cristal vierge du niveau spécifié
     */
    public Cristal createCristalVierge(int niveau) {
        if (niveau < 1 || niveau > 20) {
            throw new IllegalArgumentException("Niveau de cristal invalide: " + niveau);
        }
        return new Cristal(niveau);
    }

    /**
     * Crée un cristal avec un type spécifique (pour tests/admin)
     */
    public Cristal createCristal(int niveau, CristalType type) {
        if (niveau < 1 || niveau > 20) {
            throw new IllegalArgumentException("Niveau de cristal invalide: " + niveau);
        }
        return new Cristal(niveau, type);
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
     * Applique un cristal sur une pioche
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

        // Récupération des cristaux actuels
        List<Cristal> currentCristals = getPickaxeCristals(pickaxe);

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
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getExperience() < cost) {
            player.sendMessage("§cVous n'avez pas assez d'XP! Coût: §e" + cost + " XP");
            return false;
        }

        // Application du cristal
        playerData.removeExperience(cost);
        plugin.getEconomyManager().updateVanillaExpFromCustom(player, playerData.getExperience());
        currentCristals.add(cristal);
        setPickaxeCristals(pickaxe, currentCristals);

        // Mise à jour de la pioche
        plugin.getPickaxeManager().updatePickaxeLore(pickaxe.getItemMeta(), player);

        player.sendMessage("§d✨ Cristal §d" + cristal.getType().getDisplayName() +
                " §7(Niveau " + cristal.getNiveau() + ") §aappliqué!");
        player.sendMessage("§7Coût: §c-" + cost + " XP");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * Retire un cristal d'une pioche (50% chance de destruction)
     */
    public ItemStack removeCristalFromPickaxe(Player player, ItemStack pickaxe, String cristalUuid) {
        if (!plugin.getPickaxeManager().isLegendaryPickaxe(pickaxe)) {
            player.sendMessage("§cCette action n'est possible que sur une pioche légendaire!");
            return null;
        }

        List<Cristal> currentCristals = getPickaxeCristals(pickaxe);
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

        // Retrait du cristal
        currentCristals.remove(toRemove);
        setPickaxeCristals(pickaxe, currentCristals);

        // 50% chance de destruction
        boolean destroyed = Math.random() < 0.5;

        if (destroyed) {
            player.sendMessage("§c💥 Le cristal §d" + toRemove.getType().getDisplayName() +
                    " §ca été détruit lors du retrait!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            return null;
        } else {
            player.sendMessage("§a✓ Cristal §d" + toRemove.getType().getDisplayName() +
                    " §aretiré avec succès!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);

            // Mise à jour de la pioche
            plugin.getPickaxeManager().updatePickaxeLore(pickaxe.getItemMeta(), player);

            return toRemove.toItemStack(cristalUuidKey, cristalLevelKey, cristalTypeKey, cristalViergeKey);
        }
    }

    /**
     * Récupère les cristaux appliqués sur une pioche
     */
    public List<Cristal> getPickaxeCristals(ItemStack pickaxe) {
        if (!plugin.getPickaxeManager().isLegendaryPickaxe(pickaxe)) {
            return new ArrayList<>();
        }

        ItemMeta meta = pickaxe.getItemMeta();
        String cristalsData = meta.getPersistentDataContainer().get(pickaxeCristalsKey, PersistentDataType.STRING);

        if (cristalsData == null || cristalsData.isEmpty()) {
            return new ArrayList<>();
        }

        List<Cristal> cristals = new ArrayList<>();
        String[] cristalEntries = cristalsData.split(";");

        for (String entry : cristalEntries) {
            if (entry.trim().isEmpty()) continue;

            String[] parts = entry.split(",");
            if (parts.length >= 3) {
                try {
                    String uuid = parts[0];
                    int niveau = Integer.parseInt(parts[1]);
                    CristalType type = CristalType.valueOf(parts[2]);

                    cristals.add(new Cristal(uuid, niveau, type, false));
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Erreur de parsing cristal: " + entry);
                }
            }
        }

        return cristals;
    }

    /**
     * Définit les cristaux sur une pioche
     */
    private void setPickaxeCristals(ItemStack pickaxe, List<Cristal> cristals) {
        ItemMeta meta = pickaxe.getItemMeta();

        if (cristals.isEmpty()) {
            meta.getPersistentDataContainer().remove(pickaxeCristalsKey);
        } else {
            StringBuilder data = new StringBuilder();
            for (int i = 0; i < cristals.size(); i++) {
                Cristal cristal = cristals.get(i);
                if (i > 0) data.append(";");
                data.append(cristal.getUuid()).append(",")
                        .append(cristal.getNiveau()).append(",")
                        .append(cristal.getType().name());
            }
            meta.getPersistentDataContainer().set(pickaxeCristalsKey, PersistentDataType.STRING, data.toString());
        }

        pickaxe.setItemMeta(meta);
    }

    /**
     * Calcule le bonus total d'un type de cristal sur une pioche
     */
    public double getTotalCristalBonus(Player player, CristalType type) {
        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) return 0;

        List<Cristal> cristals = getPickaxeCristals(pickaxe);
        for (Cristal cristal : cristals) {
            if (cristal.getType() == type) {
                return cristal.getType().getBonus(cristal.getNiveau());
            }
        }

        return 0;
    }

    /**
     * Vérifie si une pioche a un cristal d'un type spécifique
     */
    public boolean hasPickaxeCristalType(ItemStack pickaxe, CristalType type) {
        List<Cristal> cristals = getPickaxeCristals(pickaxe);
        return cristals.stream().anyMatch(c -> c.getType() == type);
    }

    /**
     * Récupère le coût d'application pour le prochain cristal
     */
    public long getApplicationCost(ItemStack pickaxe) {
        List<Cristal> currentCristals = getPickaxeCristals(pickaxe);
        int count = currentCristals.size();

        if (count >= 4) return -1; // Maximum atteint
        return APPLICATION_COSTS[count];
    }

    // Getters pour les clés
    public NamespacedKey getCristalUuidKey() { return cristalUuidKey; }
    public NamespacedKey getCristalLevelKey() { return cristalLevelKey; }
    public NamespacedKey getCristalTypeKey() { return cristalTypeKey; }
    public NamespacedKey getCristalViergeKey() { return cristalViergeKey; }
}