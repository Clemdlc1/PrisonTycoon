package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire des mines
 * CORRIGÉ : Utilisation de l'enum ChatColor pour une meilleure lisibilité.
 */
public class MineManager {

    private final PrisonTycoon plugin;
    private final Set<String> regeneratingMines;

    public MineManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.regeneratingMines = ConcurrentHashMap.newKeySet();

        plugin.getPluginLogger().info(ChatColor.GREEN + "MineManager initialisé.");
    }

    /**
     * Génère une mine selon sa configuration
     */
    public boolean generateMine(String mineName, Player sender) {
        var mineData = plugin.getConfigManager().getMineData(mineName);
        if (mineData == null) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "Mine '" + mineName + "' introuvable dans la configuration!");
            }
            return false;
        }

        if (regeneratingMines.contains(mineName)) {
            if (sender != null) {
                sender.sendMessage(ChatColor.RED + "La mine '" + mineName + "' est déjà en cours de régénération!");
            }
            return false;
        }

        regeneratingMines.add(mineName);

        if (sender != null) {
            sender.sendMessage(ChatColor.GRAY + "Génération de la mine '" + mineName + "' en cours...");
        }

        // Génération asynchrone pour éviter le lag
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long startTime = System.currentTimeMillis();
                int blocksGenerated = generateMineBlocks(mineData);
                long duration = System.currentTimeMillis() - startTime;

                // Message de retour sur le thread principal
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String message = ChatColor.GREEN + "Mine '" + mineName + "' générée! " +
                            ChatColor.GRAY + "(" + NumberFormatter.format(blocksGenerated) + " blocs en " + duration + "ms)";

                    if (sender != null) {
                        sender.sendMessage(message);
                    }

                    plugin.getPluginLogger().info(message);
                    regeneratingMines.remove(mineName);
                });

            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String errorMessage = ChatColor.RED + "Erreur lors de la génération de la mine '" + mineName + "'!";

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

    /**
     * NOUVEAU: Vérifie les permissions d'accès hiérarchiques pour une mine
     */
    public boolean hasAccessToMine(Player player, String mineName) {
        if (mineName == null || mineName.isEmpty()) {
            return true;
        }

        // Normalise le nom de la mine
        String targetMine = mineName.toLowerCase();
        if (targetMine.startsWith("mine-")) {
            targetMine = targetMine.substring(5);
        }

        // Rang A toujours accessible
        if (targetMine.equals("a")) {
            return true;
        }

        // Trouve le rang le plus élevé du joueur
        String highestRank = getPlayerHighestRank(player);
        if (highestRank == null) {
            return false; // Aucune permission
        }

        // LOGIQUE HIÉRARCHIQUE: compare les rangs
        char playerRank = highestRank.charAt(0);
        char targetRank = targetMine.charAt(0);

        return targetRank <= playerRank;
    }

    /**
     * NOUVEAU: Obtient le rang le plus élevé d'un joueur en ligne
     */
    public String getPlayerHighestRank(Player player) {
        // Parcourt les rangs de z à a pour trouver le plus élevé
        for (char c = 'z'; c >= 'a'; c--) {
            String rank = String.valueOf(c);
            if (player.hasPermission("specialmine.mine." + rank)) {
                return rank;
            }
        }

        return null; // Aucune permission trouvée
    }

    /**
     * NOUVEAU: Vérifie si un joueur peut miner dans une location spécifique
     */
    public boolean canMineAtLocation(Player player, Location location) {
        // Vérifie si c'est dans une mine
        String mineName = plugin.getConfigManager().getPlayerMine(location);
        if (mineName == null) {
            return true; // Hors mine, peut miner avec n'importe quel outil
        }

        // Vérifie la pioche légendaire
        if (!canMineBlock(location, player)) {
            return false;
        }

        // Vérifie les permissions hiérarchiques
        return hasAccessToMine(player, mineName);
    }

    /**
     * Détermine le rang et les couleurs du joueur
     *
     * @param player Le joueur
     * @return [rang, couleur du nom, couleur du rang, rang mine, couleur rang mine]
     */
    public String[] getRankAndColor(Player player) {

        String currentRank = getCurrentRank(player);
        String rankColor = getRankColor(currentRank);

        if (player.hasPermission("specialmine.admin")) {
            return new String[]{"ADMIN", ChatColor.RED.toString(), ChatColor.DARK_RED.toString(), currentRank.toUpperCase(), rankColor};
        } else if (player.hasPermission("specialmine.vip")) {
            return new String[]{"VIP", ChatColor.GOLD.toString(), ChatColor.YELLOW.toString(), currentRank.toUpperCase(), rankColor};
        } else {
            return new String[]{"JOUEUR", ChatColor.GRAY.toString(), ChatColor.DARK_GRAY.toString(), currentRank.toUpperCase(), rankColor};
        }
    }

    /**
     * Obtient la couleur progressive pour un rang de mine (a-z)
     */
    public String getRankColor(String rank) {
        if (rank == null || rank.isEmpty()) return ChatColor.GRAY.toString();

        char rankChar = rank.toLowerCase().charAt(0);

        // Couleurs progressives de a à z
        return switch (rankChar) {
            case 'a', 'b' -> ChatColor.WHITE.toString();      // Blanc (débutant)
            case 'c', 'd' -> ChatColor.GRAY.toString();      // Gris clair
            case 'e', 'f' -> ChatColor.DARK_GRAY.toString();      // Gris foncé
            case 'g', 'h' -> ChatColor.DARK_GREEN.toString();      // Vert foncé
            case 'i', 'j' -> ChatColor.GREEN.toString();      // Vert clair
            case 'k', 'l' -> ChatColor.YELLOW.toString();      // Jaune
            case 'm', 'n' -> ChatColor.GOLD.toString();      // Orange
            case 'o', 'p' -> ChatColor.RED.toString();      // Rouge clair
            case 'q', 'r' -> ChatColor.DARK_RED.toString();      // Rouge foncé
            case 's', 't' -> ChatColor.LIGHT_PURPLE.toString();      // Rose
            case 'u', 'v' -> ChatColor.DARK_PURPLE.toString();      // Violet
            case 'w', 'x' -> ChatColor.BLUE.toString();      // Bleu
            case 'y' -> ChatColor.AQUA.toString();           // Cyan
            case 'z' -> ChatColor.GOLD.toString() + ChatColor.BOLD; // Or gras (rang maximum)
            default -> ChatColor.GRAY.toString();
        };
    }

    /**
     * CORRIGÉ: Obtient le rang actuel du joueur via les permissions bukkit
     */
    public String getCurrentRank(Player player) {
        var playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String highestPermission = playerData.getHighestMinePermission();
        if (highestPermission == null) {
            return "a"; // Rang par défaut
        }

        // NOUVEAU: Gère les permissions bukkit
        if (highestPermission.startsWith("specialmine.mine.")) {
            return highestPermission.substring("specialmine.mine.".length());
        }
        return "a";
    }
}