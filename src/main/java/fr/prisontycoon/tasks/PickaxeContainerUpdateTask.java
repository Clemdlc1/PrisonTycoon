package fr.prisontycoon.tasks;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cette tâche met à jour :
 * - Les lores des pickaxes de tous les joueurs en ligne
 * - Tous les conteneurs dans l'inventaire de chaque joueur
 */
public class PickaxeContainerUpdateTask extends BukkitRunnable {

    private static final long MIN_UPDATE_INTERVAL = 950; // Intervalle minimum entre mises à jour (ms)

    private final PrisonTycoon plugin;
    private final ConcurrentHashMap<String, Long> lastUpdateTimes = new ConcurrentHashMap<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false); // Verrou pour éviter la superposition
    private final AtomicInteger totalPickaxeUpdates = new AtomicInteger(0);
    private final AtomicInteger totalContainerUpdates = new AtomicInteger(0);
    private final AtomicInteger errors = new AtomicInteger(0);
    // Métriques de performance
    private long cycleCount = 0;

    public PickaxeContainerUpdateTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        if (isRunning.getAndSet(true)) {
            plugin.getPluginLogger().debug("§7PickaxeContainerUpdateTask ignoré car le cycle précédent est toujours en cours.");
            return;
        }

        cycleCount++;

        // Log des statistiques toutes les 5 minutes
        if (cycleCount % 150 == 0) {
            logPerformanceStats();
        }

        List<Player> playersToUpdate = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        // 1. On collecte les joueurs qui ont besoin d'une mise à jour
        for (Player player : Bukkit.getOnlinePlayers()) {
            Long lastUpdate = lastUpdateTimes.get(player.getUniqueId().toString());
            if (lastUpdate == null || (currentTime - lastUpdate) > MIN_UPDATE_INTERVAL) {
                playersToUpdate.add(player);
            }
        }

        if (playersToUpdate.isEmpty()) {
            isRunning.set(false); // On libère le verrou si personne n'est à traiter
            return;
        }

        // 2. Démarre TOUJOURS le traitement sur le thread principal
        new BukkitRunnable() {
            @Override
            public void run() {
                processPlayersSequentially(playersToUpdate.iterator());
            }
        }.runTask(plugin);
    }

    /**
     * Traite une liste de joueurs de manière séquentielle, avec un délai de 1 tick entre chaque joueur.
     * C'est une méthode récursive qui utilise le scheduler de Bukkit.
     *
     * @param playerIterator L'itérateur des joueurs à traiter.
     */
    private void processPlayersSequentially(Iterator<Player> playerIterator) {
        // Condition d'arrêt : plus de joueurs à traiter
        if (!playerIterator.hasNext()) {
            isRunning.set(false); // Tous les joueurs ont été traités, on libère le verrou.
            plugin.getPluginLogger().debug("§7Cycle de PickaxeContainerUpdateTask terminé.");
            return;
        }

        Player player = playerIterator.next();

        // On vérifie que le joueur est toujours valide
        if (player == null || !player.isOnline()) {
            // On passe directement au joueur suivant au prochain tick
            scheduleNextPlayer(playerIterator);
            return;
        }

        try {
            // Traitement du joueur (cette partie s'exécute sur le thread principal)
            processPlayer(player);
        } catch (Exception e) {
            errors.incrementAndGet();
            plugin.getPluginLogger().warning("§cErreur lors du traitement de " + player.getName() + ": " + e.getMessage());
        }

        // On planifie le traitement du joueur suivant pour le prochain tick
        scheduleNextPlayer(playerIterator);
    }

    /**
     * Planifie l'appel à processPlayersSequentially pour le prochain tick.
     *
     * @param playerIterator L'itérateur à passer au prochain appel.
     */
    private void scheduleNextPlayer(Iterator<Player> playerIterator) {
        new BukkitRunnable() {
            @Override
            public void run() {
                processPlayersSequentially(playerIterator);
            }
        }.runTaskLater(plugin, 1L); // Délai de 1 tick
    }


    /**
     * Traite un joueur spécifique : met à jour sa pioche et ses conteneurs.
     * CETTE MÉTHODE DOIT ÊTRE APPELÉE DEPUIS LE THREAD PRINCIPAL.
     */
    private void processPlayer(Player player) {
        boolean pickaxeUpdated = updatePlayerPickaxe(player);
        if (pickaxeUpdated) {
            totalPickaxeUpdates.incrementAndGet();
        }

        int containersUpdated = updatePlayerContainers(player);
        if (containersUpdated > 0) {
            totalContainerUpdates.addAndGet(containersUpdated);
        }

        // Marque la dernière mise à jour
        if (pickaxeUpdated || containersUpdated > 0) {
            lastUpdateTimes.put(player.getUniqueId().toString(), System.currentTimeMillis());
        }
    }

    private boolean updatePlayerPickaxe(Player player) {
        if (plugin.getPickaxeManager().getPlayerPickaxe(player) != null) {
            plugin.getPickaxeManager().updatePlayerPickaxe(player);
            return true;
        }
        return false;
    }

    private int updatePlayerContainers(Player player) {
        return plugin.getContainerManager().updateAllPlayerContainers(player);
    }

    private void cleanupDisconnectedPlayers() {
        var onlinePlayerIds = Bukkit.getOnlinePlayers().stream()
                .map(p -> p.getUniqueId().toString())
                .toList();

        lastUpdateTimes.entrySet().removeIf(entry -> !onlinePlayerIds.contains(entry.getKey()));
    }

    private void logPerformanceStats() {
        plugin.getPluginLogger().info("§a[PickaxeContainerUpdateTask] Statistiques (5 min):");
        plugin.getPluginLogger().info("§7- Cycles lancés: " + cycleCount);
        plugin.getPluginLogger().info("§7- Mises à jour (Pioches): " + totalPickaxeUpdates.get());
        plugin.getPluginLogger().info("§7- Mises à jour (Conteneurs): " + totalContainerUpdates.get());
        plugin.getPluginLogger().info("§7- Erreurs: " + errors.get());
        plugin.getPluginLogger().info("§7- Joueurs en cache: " + lastUpdateTimes.size());

        cleanupDisconnectedPlayers();
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        plugin.getPluginLogger().info("§7Arrêt de PickaxeContainerUpdateTask...");
        logPerformanceStats(); // Log final
        lastUpdateTimes.clear();
        super.cancel();
    }

    public long getCycleCount() {
        return cycleCount;
    }

    public int getTotalPickaxeUpdates() {
        return totalPickaxeUpdates.get();
    }

    public int getTotalContainerUpdates() {
        return totalContainerUpdates.get();
    }

    public int getErrors() {
        return errors.get();
    }

    public int getCachedPlayersCount() {
        return lastUpdateTimes.size();
    }
}