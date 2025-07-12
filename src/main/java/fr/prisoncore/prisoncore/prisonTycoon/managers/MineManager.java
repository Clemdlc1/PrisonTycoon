package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.MineData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des mines
 * CORRIGÉ : Suppression de la régénération automatique
 */
public class MineManager {

    private final PrisonTycoon plugin;
    private final Set<String> regeneratingMines;

    public MineManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.regeneratingMines = ConcurrentHashMap.newKeySet();

        plugin.getPluginLogger().info("§aMineManager initialisé.");
    }

    /**
     * Génère une mine selon sa configuration
     */
    public boolean generateMine(String mineName, Player sender) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) {
            if (sender != null) {
                sender.sendMessage("§cMine '" + mineName + "' introuvable dans la configuration!");
            }
            return false;
        }

        if (regeneratingMines.contains(mineName)) {
            if (sender != null) {
                sender.sendMessage("§cLa mine '" + mineName + "' est déjà en cours de régénération!");
            }
            return false;
        }

        regeneratingMines.add(mineName);

        if (sender != null) {
            sender.sendMessage("§7Génération de la mine '" + mineName + "' en cours...");
        }

        // Génération asynchrone pour éviter le lag
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long startTime = System.currentTimeMillis();
                int blocksGenerated = generateMineBlocks(mineData);
                long duration = System.currentTimeMillis() - startTime;

                // Message de retour sur le thread principal
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String message = "§aMine '" + mineName + "' générée! " +
                            "§7(" + NumberFormatter.format(blocksGenerated) + " blocs en " + duration + "ms)";

                    if (sender != null) {
                        sender.sendMessage(message);
                    }

                    plugin.getPluginLogger().info(message);
                    regeneratingMines.remove(mineName);
                });

            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String errorMessage = "§cErreur lors de la génération de la mine '" + mineName + "'!";

                    if (sender != null) {
                        sender.sendMessage(errorMessage);
                    }

                    plugin.getPluginLogger().severe(errorMessage);
                    e.printStackTrace();
                    regeneratingMines.remove(mineName);
                });
            }
        });

        return true;
    }

    /**
     * Génère les blocs d'une mine selon sa composition
     */
    private int generateMineBlocks(MineData mineData) {
        Location min = mineData.getMinCorner();
        Location max = mineData.getMaxCorner();

        int minX = (int) Math.min(min.getX(), max.getX());
        int maxX = (int) Math.max(min.getX(), max.getX());
        int minY = (int) Math.min(min.getY(), max.getY());
        int maxY = (int) Math.max(min.getY(), max.getY());
        int minZ = (int) Math.min(min.getZ(), max.getZ());
        int maxZ = (int) Math.max(min.getZ(), max.getZ());

        int blocksGenerated = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Location blockLocation = new Location(min.getWorld(), x, y, z);
                    Material randomMaterial = mineData.getRandomMaterial();

                    // Place le bloc de manière synchrone
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        blockLocation.getBlock().setType(randomMaterial);
                    });

                    blocksGenerated++;

                    // Pause occasionnelle pour éviter de surcharger le serveur
                    if (blocksGenerated % 1000 == 0) {
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        }

        return blocksGenerated;
    }

    /**
     * Vérifie si un bloc peut être miné (protection des mines)
     */
    public boolean canMineBlock(Location location, Player player) {
        // Vérifie si le joueur est dans une mine
        String mineName = plugin.getConfigManager().getPlayerMine(location);
        if (mineName == null) {
            // Hors mine: peut miner avec n'importe quel outil
            return true;
        }

        // Dans une mine: seule la pioche légendaire peut miner
        var handItem = player.getInventory().getItemInMainHand();
        return plugin.getPickaxeManager().isLegendaryPickaxe(handItem) &&
                plugin.getPickaxeManager().isOwner(handItem, player);
    }

    // SUPPRIMÉ: handleBlockMined() - Plus de régénération automatique

    /**
     * Retourne toutes les mines configurées
     */
    public Set<String> getAllMineNames() {
        return plugin.getConfigManager().getAllMines().keySet();
    }

    /**
     * Vérifie si une mine est en cours de régénération
     */
    public boolean isRegenerating(String mineName) {
        return regeneratingMines.contains(mineName);
    }

    /**
     * Force l'arrêt de la régénération d'une mine
     */
    public void stopRegeneration(String mineName) {
        regeneratingMines.remove(mineName);
    }

    /**
     * Statistiques des mines
     */
    public Map<String, Object> getMineStats() {
        Map<String, Object> stats = new HashMap<>();

        var allMines = plugin.getConfigManager().getAllMines();
        stats.put("total-mines", allMines.size());
        stats.put("regenerating-mines", regeneratingMines.size());

        long totalVolume = 0;
        for (var mineData : allMines.values()) {
            totalVolume += mineData.getVolume();
        }
        stats.put("total-blocks", totalVolume);

        return stats;
    }
}