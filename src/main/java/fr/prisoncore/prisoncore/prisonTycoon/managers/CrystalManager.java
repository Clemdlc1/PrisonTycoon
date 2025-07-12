package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.crystals.Crystal;
import fr.prisoncore.prisoncore.prisonTycoon.crystals.CrystalType;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;

/**
 * Gestionnaire du système de cristaux
 */
public class CrystalManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey crystalsAppliedKey;

    // Coûts d'application par position
    private static final int[] APPLICATION_COSTS = {1000, 2500, 5000, 10000};
    private static final int MAX_CRYSTALS_PER_PICKAXE = 4;
    private static final int REMOVAL_TOKEN_COST = 50;
    private static final double REMOVAL_DESTRUCTION_CHANCE = 0.5;

    public CrystalManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.crystalsAppliedKey = new NamespacedKey(plugin, "crystals_applied");

        // Initialise les clés pour Crystal
        Crystal.CRYSTAL_KEY = new NamespacedKey(plugin, "crystal");
        Crystal.CRYSTAL_TYPE_KEY = new NamespacedKey(plugin, "crystal_type");
        Crystal.CRYSTAL_LEVEL_KEY = new NamespacedKey(plugin, "crystal_level");
        Crystal.CRYSTAL_REVEALED_KEY = new NamespacedKey(plugin, "crystal_revealed");

        plugin.getPluginLogger().info("§aCrystalManager initialisé.");
    }

    /**
     * Crée un cristal vierge du niveau spécifié
     */
    public Crystal createBlankCrystal(int level) {
        return new Crystal(level);
    }

    /**
     * Révèle un cristal
     */
    public boolean revealCrystal(Player player, ItemStack crystalItem) {
        Crystal crystal = Crystal.fromItemStack(crystalItem);
        if (crystal == null || crystal.isRevealed()) {
            return false;
        }

        crystal.reveal();

        // Met à jour l'item
        ItemStack newItem = crystal.createItemStack();
        crystalItem.setItemMeta(newItem.getItemMeta());

        player.sendMessage("§5✨ Cristal révélé ! Type: " + crystal.getType().getDisplayName());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        plugin.getPluginLogger().info("Cristal révélé pour " + player.getName() +
                ": " + crystal.getType().getDisplayName() + " niveau " + crystal.getLevel());

        return true;
    }

    /**
     * Applique un cristal sur la pioche du joueur
     */
    public boolean applyCrystalToPickaxe(Player player, Crystal crystal) {
        if (!crystal.isRevealed()) {
            player.sendMessage("§c❌ Ce cristal doit d'abord être révélé!");
            return false;
        }

        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§c❌ Vous devez avoir votre pioche légendaire!");
            return false;
        }

        List<Crystal> appliedCrystals = getAppliedCrystals(pickaxe);

        // Vérifications
        if (appliedCrystals.size() >= MAX_CRYSTALS_PER_PICKAXE) {
            player.sendMessage("§c❌ Maximum " + MAX_CRYSTALS_PER_PICKAXE + " cristaux par pioche!");
            return false;
        }

        // Vérifie qu'il n'y a pas déjà ce type de cristal
        for (Crystal applied : appliedCrystals) {
            if (applied.getType() == crystal.getType()) {
                player.sendMessage("§c❌ Un cristal " + crystal.getType().getDisplayName() + " §cest déjà appliqué!");
                return false;
            }
        }

        // Vérifie le coût en XP
        int position = appliedCrystals.size();
        int xpCost = APPLICATION_COSTS[position];

        if (player.getLevel() * 100 + (int)(player.getExp() * player.getExpToLevel()) < xpCost) {
            player.sendMessage("§c❌ XP insuffisant! Requis: §a" + xpCost + " XP");
            return false;
        }

        // Retire l'XP
        removePlayerXP(player, xpCost);

        // Applique le cristal
        appliedCrystals.add(crystal);
        saveAppliedCrystals(pickaxe, appliedCrystals);

        player.sendMessage("§a✅ Cristal " + crystal.getType().getDisplayName() +
                " §aappliqué! §7(Position " + (position + 1) + ", -" + xpCost + " XP)");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        // Met à jour la pioche
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        plugin.getPluginLogger().info("Cristal appliqué pour " + player.getName() +
                ": " + crystal.getType().getDisplayName() + " niveau " + crystal.getLevel());

        return true;
    }

    /**
     * Retire un cristal de la pioche
     */
    public boolean removeCrystalFromPickaxe(Player player, int position) {
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§c❌ Vous devez avoir votre pioche légendaire!");
            return false;
        }

        List<Crystal> appliedCrystals = getAppliedCrystals(pickaxe);
        if (position < 0 || position >= appliedCrystals.size()) {
            player.sendMessage("§c❌ Position de cristal invalide!");
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getTokens() < REMOVAL_TOKEN_COST) {
            player.sendMessage("§c❌ Tokens insuffisants! Requis: §e" + REMOVAL_TOKEN_COST + " tokens");
            return false;
        }

        Crystal removedCrystal = appliedCrystals.get(position);

        // Retire les tokens
        playerData.removeTokens(REMOVAL_TOKEN_COST);

        // Chance de destruction
        boolean destroyed = Math.random() < REMOVAL_DESTRUCTION_CHANCE;

        if (destroyed) {
            player.sendMessage("§c💥 Le cristal " + removedCrystal.getType().getDisplayName() +
                    " §ca été détruit lors du retrait! §7(-" + REMOVAL_TOKEN_COST + " tokens)");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            // Redonne le cristal
            ItemStack crystalItem = removedCrystal.createItemStack();
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(crystalItem);

            if (!leftover.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), crystalItem);
                player.sendMessage("§e⚠️ Cristal récupéré au sol (inventaire plein)");
            }

            player.sendMessage("§a✅ Cristal " + removedCrystal.getType().getDisplayName() +
                    " §aretirés avec succès! §7(-" + REMOVAL_TOKEN_COST + " tokens)");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        }

        // Retire le cristal de la liste
        appliedCrystals.remove(position);
        saveAppliedCrystals(pickaxe, appliedCrystals);

        // Met à jour la pioche
        plugin.getPickaxeManager().updatePlayerPickaxe(player);

        plugin.getPluginLogger().info("Cristal retiré pour " + player.getName() +
                ": " + removedCrystal.getType().getDisplayName() + " (détruit: " + destroyed + ")");

        return true;
    }

    /**
     * Obtient les cristaux appliqués sur une pioche
     */
    public List<Crystal> getAppliedCrystals(ItemStack pickaxe) {
        List<Crystal> crystals = new ArrayList<>();

        if (pickaxe == null || !pickaxe.hasItemMeta()) {
            return crystals;
        }

        ItemMeta meta = pickaxe.getItemMeta();
        String crystalData = meta.getPersistentDataContainer().get(crystalsAppliedKey, PersistentDataType.STRING);

        if (crystalData != null && !crystalData.isEmpty()) {
            String[] crystalStrings = crystalData.split(";");

            for (String crystalString : crystalStrings) {
                if (!crystalString.trim().isEmpty()) {
                    Crystal crystal = parseCrystalFromString(crystalString);
                    if (crystal != null) {
                        crystals.add(crystal);
                    }
                }
            }
        }

        return crystals;
    }

    /**
     * Sauvegarde les cristaux appliqués sur une pioche
     */
    private void saveAppliedCrystals(ItemStack pickaxe, List<Crystal> crystals) {
        if (pickaxe == null || !pickaxe.hasItemMeta()) return;

        ItemMeta meta = pickaxe.getItemMeta();

        if (crystals.isEmpty()) {
            meta.getPersistentDataContainer().remove(crystalsAppliedKey);
        } else {
            StringBuilder data = new StringBuilder();
            for (int i = 0; i < crystals.size(); i++) {
                if (i > 0) data.append(";");
                data.append(crystalToString(crystals.get(i)));
            }
            meta.getPersistentDataContainer().set(crystalsAppliedKey, PersistentDataType.STRING, data.toString());
        }

        pickaxe.setItemMeta(meta);
    }

    /**
     * Convertit un cristal en string pour sauvegarde
     */
    private String crystalToString(Crystal crystal) {
        return crystal.getType().getInternalName() + ":" + crystal.getLevel();
    }

    /**
     * Parse un cristal depuis une string
     */
    private Crystal parseCrystalFromString(String data) {
        try {
            String[] parts = data.split(":");
            if (parts.length != 2) return null;

            String typeName = parts[0];
            int level = Integer.parseInt(parts[1]);

            for (CrystalType type : CrystalType.values()) {
                if (type.getInternalName().equals(typeName)) {
                    return new Crystal(type, level, true);
                }
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur parse cristal: " + data);
        }
        return null;
    }

    /**
     * Retire de l'XP au joueur
     */
    private void removePlayerXP(Player player, int amount) {
        int totalXP = getTotalExperience(player);
        totalXP = Math.max(0, totalXP - amount);

        player.setLevel(0);
        player.setExp(0);
        player.giveExp(totalXP);
    }

    /**
     * Obtient l'XP total du joueur
     */
    private int getTotalExperience(Player player) {
        int totalXP = 0;

        // XP des niveaux complets
        for (int i = 1; i <= player.getLevel(); i++) {
            totalXP += getXPNeededForLevel(i);
        }

        // XP du niveau actuel
        totalXP += Math.round(player.getExp() * getXPNeededForLevel(player.getLevel() + 1));

        return totalXP;
    }

    /**
     * Obtient l'XP nécessaire pour un niveau
     */
    private int getXPNeededForLevel(int level) {
        if (level <= 16) {
            return 2 * level + 7;
        } else if (level <= 31) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }

    /**
     * Calcule le bonus total d'un type de cristal
     */
    public double getTotalCrystalBonus(List<Crystal> crystals, CrystalType type) {
        return crystals.stream()
                .filter(c -> c.getType() == type)
                .mapToDouble(Crystal::getBonus)
                .sum();
    }

    /**
     * Vérifie si un joueur a un cristal d'un type spécifique
     */
    public boolean hasCrystalType(ItemStack pickaxe, CrystalType type) {
        return getAppliedCrystals(pickaxe).stream()
                .anyMatch(c -> c.getType() == type);
    }

    /**
     * Obtient les statistiques des cristaux
     */
    public Map<String, Object> getCrystalStats() {
        Map<String, Object> stats = new HashMap<>();

        int totalCrystalsApplied = 0;
        Map<CrystalType, Integer> typeCount = new HashMap<>();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
            if (pickaxe != null) {
                List<Crystal> crystals = getAppliedCrystals(pickaxe);
                totalCrystalsApplied += crystals.size();

                for (Crystal crystal : crystals) {
                    typeCount.merge(crystal.getType(), 1, Integer::sum);
                }
            }
        }

        stats.put("total-crystals-applied", totalCrystalsApplied);
        stats.put("crystals-by-type", typeCount);
        stats.put("max-crystals-per-pickaxe", MAX_CRYSTALS_PER_PICKAXE);
        stats.put("removal-cost", REMOVAL_TOKEN_COST);
        stats.put("destruction-chance", REMOVAL_DESTRUCTION_CHANCE);

        return stats;
    }
}