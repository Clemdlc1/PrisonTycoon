package fr.prisontycoon.managers;

import com.fastasyncworldedit.core.FaweAPI;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.function.pattern.RandomPattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockTypes;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import fr.prisontycoon.data.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Gestionnaire des mines optimisé avec adaptation TPS et génération progressive
 * Version anti-lag avec support asynchrone maximal
 */
public class MineManager {

    private final PrisonTycoon plugin;
    private final Map<String, MineData> mines = new ConcurrentHashMap<>();
    private final Map<String, Long> mineResetTimes = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> mineGenerating = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> mineBeingChecked = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // Système de queue pour éviter les générations simultanées
    private final PriorityBlockingQueue<MineResetTask> resetQueue = new PriorityBlockingQueue<>();
    private final ExecutorService asyncExecutor = Executors.newFixedThreadPool(2);



    // Monitoring TPS
    private double currentTPS = 20.0;
    private final LinkedList<Long> tickTimes = new LinkedList<>();
    private static final int TPS_SAMPLE_SIZE = 100;
    private static final long TPS_MONITOR_PERIOD_TICKS = 20L; // 1 seconde à 20 TPS
    private static final long NANOS_PER_TICK_TARGET = 50_000_000L; // 50ms

    // Paramètres adaptatifs
    private int currentChunkSize = 50000; // Nombre de blocs par batch
    private long currentDelay = 1L; // Délai entre les batches en ticks

    public MineManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        configureFAWE(); // Configuration critique pour éviter les freezes
        loadMinesFromConfigManager();
        // Initialisation des hologrammes de mines après chargement
        new BukkitRunnable() {
            @Override
            public void run() {
                initializeMineHolograms();
                // Reset toutes les mines au démarrage
                resetAllMines();
            }
        }.runTaskLater(plugin, 20L);
        startMineResetScheduler();
        startTPSMonitor();
        startResetQueueProcessor();
        startHologramAutoRefresher();
    }

    /**
     * Configure FAWE pour éviter les blocages du serveur
     */
    private void configureFAWE() {
        try {
            // Forcer FAWE à utiliser le mode asynchrone
            com.fastasyncworldedit.core.configuration.Settings.settings().QUEUE.PARALLEL_THREADS =
                    Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

            // Optimiser les chunks
            com.fastasyncworldedit.core.configuration.Settings.settings().QUEUE.TARGET_SIZE = 64;

            plugin.getPluginLogger().info("§aFAWE configuré pour éviter les freezes");
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eImpossible de configurer FAWE: " + e.getMessage());
        }
    }

    /**
     * Moniteur de TPS pour adapter la vitesse de génération
     */
    private void startTPSMonitor() {
        new BukkitRunnable() {
            long lastTick = System.nanoTime();

            @Override
            public void run() {
                long currentTick = System.nanoTime();
                long delta = currentTick - lastTick; // temps écoulé depuis la dernière exécution (en ns)
                lastTick = currentTick;

                tickTimes.add(delta);
                if (tickTimes.size() > TPS_SAMPLE_SIZE) {
                    tickTimes.removeFirst();
                }

                // Calculer le TPS moyen en tenant compte de la période de planification (en ticks)
                if (tickTimes.size() >= 5) {
                    double avgDeltaNs = tickTimes.stream()
                            .mapToLong(Long::longValue)
                            .average()
                            .orElse(TPS_MONITOR_PERIOD_TICKS * NANOS_PER_TICK_TARGET);

                    double tpsEstimate = (TPS_MONITOR_PERIOD_TICKS * 1_000_000_000.0) / avgDeltaNs;
                    currentTPS = Math.max(0.0, Math.min(20.0, tpsEstimate));

                    // Adapter les paramètres selon le TPS
                    adaptGenerationParameters();
                }
            }
        }.runTaskTimer(plugin, 0L, TPS_MONITOR_PERIOD_TICKS);
    }

    /**
     * Adapte les paramètres de génération selon le TPS actuel
     */
    private void adaptGenerationParameters() {
        if (currentTPS >= 19.5) {
            // TPS excellent : génération rapide
            currentChunkSize = 100000;
            currentDelay = 1L;
        } else if (currentTPS >= 18.0) {
            // TPS bon : génération normale
            currentChunkSize = 50000;
            currentDelay = 2L;
        } else if (currentTPS >= 16.0) {
            // TPS moyen : génération prudente
            currentChunkSize = 25000;
            currentDelay = 3L;
        } else if (currentTPS >= 14.0) {
            // TPS faible : génération lente
            currentChunkSize = 10000;
            currentDelay = 5L;
        } else {
            // TPS critique : génération minimale
            currentChunkSize = 5000;
            currentDelay = 10L;
        }
    }

    /**
     * Processeur de queue pour les resets de mines
     */
    private void startResetQueueProcessor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!resetQueue.isEmpty() && currentTPS > 15.0 && !isAnyMineGenerating()) {
                    MineResetTask task = resetQueue.poll();
                    if (task != null && !isMineGenerating(task.mineId)) {
                        executeProgressiveGeneration(task.mineId, task.priority);
                    }
                } else if (!resetQueue.isEmpty()) {
                    plugin.getPluginLogger().debug("§7Queue en attente (" + resetQueue.size() + ") - TPS trop bas: " + String.format("%.1f", currentTPS));
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Vérifie toutes les secondes
    }

    /**
     * Charge les mines depuis le ConfigManager
     */
    private void loadMinesFromConfigManager() {
        plugin.getPluginLogger().info("§7Chargement des mines depuis ConfigManager...");

        Map<String, MineData> configMines = plugin.getConfigManager().getAllMines();

        if (configMines.isEmpty()) {
            plugin.getPluginLogger().warning("§cAucune mine trouvée dans ConfigManager!");
            return;
        }

        for (Map.Entry<String, MineData> entry : configMines.entrySet()) {
            String mineId = entry.getKey();
            MineData mineData = entry.getValue();

            if (mineData.getVolume() <= 0) {
                plugin.getPluginLogger().warning("§cMine " + mineId + " a un volume invalide: " + mineData.getVolume());
                continue;
            }

            mines.put(mineId, mineData);
            mineGenerating.put(mineId, new AtomicBoolean(false));
            mineBeingChecked.put(mineId, new AtomicBoolean(false));

            plugin.getPluginLogger().info("§aMine chargée: " + mineId + " (Type: " + mineData.getType() +
                    ", Volume: " + mineData.getVolume() + " blocs)");
        }

        plugin.getPluginLogger().info("§aMines chargées: " + mines.size());
    }

    // ==================== HOLOGRAMMES DE MINE (SECTION MODIFIÉE) ====================
    private final Map<String, List<ArmorStand>> mineHolograms = new ConcurrentHashMap<>();

    /**
     * Met en majuscule la première lettre de chaque mot dans une chaîne.
     * @param text Le texte à transformer.
     * @return Le texte formaté.
     */
    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return Arrays.stream(text.split(" "))
                .map(word -> {
                    if (word.isEmpty()) return "";
                    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
                })
                .collect(Collectors.joining(" "));
    }

    private Location getMineHologramBase(String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) return null;
        org.bukkit.World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) return null;
        // Ajustement de la hauteur pour un meilleur espacement
        return mine.getCenterLocation(world).add(0, 8, 0);
    }

    public void initializeMineHolograms() {
        for (String mineId : mines.keySet()) {
            ensureMineHologram(mineId);
            updateMineHologram(mineId);
        }
    }

    private List<ArmorStand> spawnHologramStack(Location base, int lines) {
        if (base == null || base.getWorld() == null) return List.of();
        List<ArmorStand> list = new ArrayList<>(lines);
        // Espacement ajusté pour la lisibilité
        double step = 0.45;
        // Inverser la création pour que la ligne 0 soit la plus haute
        Location currentLoc = base.clone();
        for (int i = 0; i < lines; i++) {
            ArmorStand stand = base.getWorld().spawn(currentLoc, ArmorStand.class, s -> {
                s.setInvisible(true);
                s.setMarker(true);
                s.setCustomNameVisible(true);
                s.customName(Component.text(" "));
                s.setGravity(false);
            });
            list.add(stand);
            currentLoc.subtract(0, step, 0);
        }
        return list;
    }

    public void ensureMineHologram(String mineId) {
        if (mineHolograms.containsKey(mineId)) return;
        Location base = getMineHologramBase(mineId);
        if (base == null) return;
        // 8 lignes pour le nouvel affichage : Titre, Comp, Surcharge, Header Top, Top1, Top2, Top3, Vide
        mineHolograms.put(mineId, spawnHologramStack(base, 14));
    }

    /**
     * Méthode d'affichage de l'hologramme entièrement remaniée.
     */
    public void updateMineHologram(String mineId) {
        ensureMineHologram(mineId);
        List<ArmorStand> stands = mineHolograms.get(mineId);
        if (stands == null || stands.isEmpty()) return;

        MineData mine = mines.get(mineId);
        String mineTitle = mine != null ? mine.getDisplayName() : mineId;

        // --- Surcharge (calculs en amont) ---
        var overload = plugin.getMineOverloadManager();
        double gauge = 0.0;
        double mult = 1.0;
        int actifs = 0;
        List<Map.Entry<UUID, Long>> top3 = new ArrayList<>();

        if (overload != null) {
            actifs = overload.getActiveMinersCount(mineId);
            mult = overload.getOverloadMultiplier(null, GlobalBonusManager.BonusCategory.MONEY_BONUS);
            if (mult >= 2.0) gauge = 1.0;
            top3 = overload.getTop3(mineId);
        }
        String bar = buildBar(gauge);
        String overloadLine = "§cSurcharge §f[" + bar + "] §8(§cx" + String.format(Locale.FRANCE, "%.2f", mult) + "§8)  §7Actifs: §f" + actifs;

        // --- Lignes de l'hologramme ---

        // Ligne 0: Titre (en gras)
        setStandName(stands, 0, Component.text("§f§l" + mineTitle));

        // Ligne 1: Surcharge
        setStandName(stands, 1, Component.text(overloadLine));

        // Ligne 2: Espaceur
        setStandName(stands, 2, Component.text(" "));

        // Lignes 3-8: Composition (1 ligne par matériau)
        setStandName(stands, 3, Component.text("§f§lComposition:")); // En-tête en gras

        // Prépare le format pour les pourcentages (ex: 50.25% ou 50%)
        DecimalFormat df = new DecimalFormat("0.##");

        List<Map.Entry<Material, Double>> compositionEntries = mine != null ?
                mine.getBlockComposition().entrySet().stream()
                        .sorted(Map.Entry.<Material, Double>comparingByValue().reversed())
                        .limit(5) // On affiche le top 5
                        .toList() :
                Collections.emptyList();

        for (int i = 0; i < 5; i++) {
            int lineIndex = 4 + i;
            if (i < compositionEntries.size()) {
                Map.Entry<Material, Double> entry = compositionEntries.get(i);
                String name = capitalizeWords(entry.getKey().name().toLowerCase().replace('_', ' '));
                String percent = df.format(entry.getValue() * 100) + "%";
                setStandName(stands, lineIndex, Component.text("  §7- " + name + ": §f" + percent));
            } else {
                setStandName(stands, lineIndex, Component.text(" ")); // Vide si moins de 5 matériaux
            }
        }

        // Ligne 9: Espaceur
        setStandName(stands, 9, Component.text(" "));

        // Lignes 10-13: Top 3 Mineurs
        setStandName(stands, 10, Component.text("§f§lTop Mineurs:")); // En-tête en gras
        setStandName(stands, 11, Component.text("§6§l1. " + formatTopEntry(top3, 0)));
        setStandName(stands, 12, Component.text(top3.size() > 1 ? "§e§l2. " + formatTopEntry(top3, 1) : "  §7-"));
        setStandName(stands, 13, Component.text(top3.size() > 2 ? "§c§l3. " + formatTopEntry(top3, 2) : "  §7-"));
    }


    private void setStandName(List<ArmorStand> stands, int index, Component name) {
        if (index < 0 || index >= stands.size()) return;
        ArmorStand s = stands.get(index);
        if (s != null && !s.isDead()) {
            s.customName(name);
        }
    }

    private String buildBar(double gauge) {
        int segments = 24;
        int filled = (int) Math.round(gauge * segments);
        StringBuilder sb = new StringBuilder(segments * 3); // *3 pour les codes couleurs
        sb.append("§f");
        for (int i = 0; i < segments; i++) {
            if (i < filled) {
                if (i > segments * 0.8) sb.append("§c|"); // Rouge
                else if (i > segments * 0.5) sb.append("§6|"); // Orange
                else sb.append("§e|"); // Jaune
            } else {
                sb.append("§7|"); // Gris
            }
        }
        return sb.toString();
    }

    private String formatTopEntry(List<Map.Entry<UUID, Long>> top, int idx) {
        if (idx >= top.size()) return "§7-";
        var entry = top.get(idx);
        Player p = Bukkit.getPlayer(entry.getKey());
        // Fournit un nom par défaut si le joueur est hors ligne
        String name = (p != null) ? p.getName() : Bukkit.getOfflinePlayer(entry.getKey()).getName();
        if (name == null) name = "Inconnu";
        // Formatage du score (ex: 1.2k, 1.5M)
        return "§f" + name + " §7(" + formatScore(entry.getValue()) + ")";
    }

    private String formatScore(long score) {
        if (score < 1000) return String.valueOf(score);
        if (score < 1_000_000) return String.format(Locale.US, "%.1fk", score / 1000.0);
        if (score < 1_000_000_000) return String.format(Locale.US, "%.1fM", score / 1_000_000.0);
        return String.format(Locale.US, "%.1fG", score / 1_000_000_000.0);
    }


    private void startHologramAutoRefresher() {
        int refresh = plugin.getConfigManager().getOverloadHologramRefreshTicks();
        if (refresh <= 0) refresh = 60; // Refresh toutes les 3 secondes par défaut
        new BukkitRunnable() {
            @Override
            public void run() {
                for (String id : mines.keySet()) {
                    updateMineHologram(id);
                }
            }
        }.runTaskTimer(plugin, refresh, refresh);
    }

    // ... Le reste du code est inchangé ...

//<editor-fold desc="Le reste du code reste inchangé">
    /**
     * Ajoute une mine à la queue de régénération avec priorité
     */
    public void generateMine(String mineId) {
        generateMine(mineId, MineResetPriority.NORMAL);
    }

    public void generateMine(String mineId, MineResetPriority priority) {
        MineData mine = mines.get(mineId);
        if (mine == null) {
            plugin.getPluginLogger().warning("§cMine introuvable: " + mineId);
            return;
        }

        if (isMineGenerating(mineId)) {
            plugin.getPluginLogger().debug("§eMine déjà en génération: " + mineId);
            return;
        }

        // Ajouter à la queue avec priorité
        resetQueue.offer(new MineResetTask(mineId, priority, System.currentTimeMillis()));
        plugin.getPluginLogger().info("§bMine ajoutée à la queue de régénération: " + mineId + " (Priorité: " + priority + ")");
    }

    /**
     * Exécute la génération progressive d'une mine avec détection d'erreurs FAWE
     */
    private void executeProgressiveGeneration(String mineId, MineResetPriority priority) {
        MineData mine = mines.get(mineId);
        if (mine == null) return;

        AtomicBoolean generating = mineGenerating.get(mineId);
        if (generating == null || !generating.compareAndSet(false, true)) {
            return;
        }

        plugin.getPluginLogger().info("§b[FAWE] Démarrage génération adaptative de " + mineId +
                " (TPS: " + String.format("%.1f", currentTPS) + ", Chunk: " + currentChunkSize + " blocs)");

        // Phase 1: Préparation (ASYNC)
        asyncExecutor.submit(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Calculer les joueurs à téléporter
                List<Player> playersToMove = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (mineId.equals(getPlayerCurrentMine(p))) {
                        playersToMove.add(p);
                    }
                }

                // Vérifier si FAWE est disponible
                World faweWorld = null;
                try {
                    faweWorld = FaweAPI.getWorld(mine.getWorldName());
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("§eFAWE indisponible, bascule sur Bukkit API");
                }

                if (faweWorld == null) {
                    // Fallback sur l'API Bukkit
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            generateWithBukkitAPI(mineId);
                        }
                    }.runTask(plugin);
                    return;
                }

                BlockVector3 min = BlockVector3.at(mine.getMinX(), mine.getMinY(), mine.getMinZ());
                BlockVector3 max = BlockVector3.at(mine.getMaxX(), mine.getMaxY(), mine.getMaxZ());
                CuboidRegion region = new CuboidRegion(faweWorld, min, max);

                RandomPattern pattern = new RandomPattern();
                for (Map.Entry<Material, Double> entry : mine.getBlockComposition().entrySet()) {
                    pattern.add(BlockTypes.get(entry.getKey().name().toLowerCase()), entry.getValue());
                }

                // Phase 2: Actions synchrones (téléportations)
                if (!playersToMove.isEmpty()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            for (Player p : playersToMove) {
                                teleportToMineSafely(p, mineId);
                            }
                            String msg = "§e§l[!] §eLa mine '" + mine.getDisplayName() + "§e' se régénère !";
                            broadcastToWorld(mine.getWorldName(), msg);
                        }
                    }.runTask(plugin);
                }

                // Phase 3: Génération asynchrone optimisée avec timeout
                World finalFaweWorld = faweWorld;
                CompletableFuture<Void> generationFuture = CompletableFuture.runAsync(() -> {
                    long volume = region.getVolume();
                    if (volume <= 150_000) {
                        executeBatchedGeneration(finalFaweWorld, region, pattern, mineId, priority, startTime);
                    } else {
                        generateInChunksAsync(finalFaweWorld, region, pattern, mineId, priority, startTime);
                    }
                }, asyncExecutor);

                // Timeout dynamique basé sur le volume (60s min, 600s max)
                long dynamicTimeoutSeconds = Math.max(60, Math.min(600, region.getVolume() / 15_000));
                generationFuture.orTimeout(dynamicTimeoutSeconds, TimeUnit.SECONDS).exceptionally(ex -> {
                    plugin.getPluginLogger().severe("§cTimeout génération FAWE pour " + mineId + ", bascule sur Bukkit API");
                    generating.set(false);

                    // Fallback sur Bukkit API
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            generateWithBukkitAPI(mineId);
                        }
                    }.runTask(plugin);
                    return null;
                });

            } catch (Exception e) {
                plugin.getPluginLogger().severe("§cErreur génération de " + mineId);
                e.printStackTrace();
                generating.set(false);

                // Fallback sur Bukkit API en cas d'erreur
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        generateWithBukkitAPI(mineId);
                    }
                }.runTask(plugin);
            }
        });
    }

    /**
     * Exécute la génération par batches adaptatifs
     * UTILISE L'API ASYNCHRONE DE FAWE CORRECTEMENT
     */
    private void executeBatchedGeneration(World world, CuboidRegion region, RandomPattern pattern,
                                          String mineId, MineResetPriority priority, long startTime) {
        // IMPORTANT: Tout le travail FAWE doit être fait en ASYNCHRONE
        asyncExecutor.submit(() -> {
            try {
                // Créer l'EditSession en mode async
                EditSession editSession = WorldEdit.getInstance().newEditSession(world);
                editSession.setFastMode(true); // Mode rapide pour les grandes opérations

                // Exécuter la génération complète en ASYNC
                editSession.setBlocks((Region) region, pattern);

                // Commit les changements de manière asynchrone
                editSession.flushQueue();

                // Attendre que FAWE finisse son travail
                editSession.close();

                // Finaliser sur le thread principal
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        finalizeMineGeneration(mineId, startTime, region.getVolume());
                    }
                }.runTask(plugin);

            } catch (Exception e) {
                plugin.getPluginLogger().severe("§cErreur génération FAWE async de " + mineId);
                e.printStackTrace();
                mineGenerating.get(mineId).set(false);
            }
        });
    }

    /**
     * Alternative: Génération par chunks vraiment asynchrone si nécessaire
     */
    private void generateInChunksAsync(World world, CuboidRegion region, RandomPattern pattern,
                                       String mineId, MineResetPriority priority, long startTime) {
        // Calculer les sous-régions
        List<CuboidRegion> subRegions = divideRegion(region, currentChunkSize);
        AtomicInteger currentIndex = new AtomicInteger(0);
        AtomicInteger blocksProcessed = new AtomicInteger(0);
        long totalVolume = region.getVolume();

        // Créer une tâche récursive asynchrone
        Runnable processNextChunk = new Runnable() {
            @Override
            public void run() {
                // Vérifier TPS
                if (currentTPS < 12.0) {
                    // Reporter de 5 secondes si TPS trop bas
                    plugin.getPluginLogger().warning("§eTPS trop bas (" + String.format("%.1f", currentTPS) +
                            "), pause génération de " + mineId);
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, 100L);
                    return;
                }

                int index = currentIndex.getAndIncrement();
                if (index >= subRegions.size()) {
                    // Terminé - finaliser sur le thread principal
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            finalizeMineGeneration(mineId, startTime, totalVolume);
                        }
                    }.runTask(plugin);
                    return;
                }

                // Traiter le chunk actuel en ASYNC
                CuboidRegion subRegion = subRegions.get(index);

                try {
                    // Créer un nouvel EditSession pour ce batch
                    EditSession editSession = WorldEdit.getInstance().newEditSession(world);
                    editSession.setFastMode(true);

                    // Appliquer les blocs
                    editSession.setBlocks((Region) subRegion, pattern);
                    editSession.flushQueue();
                    editSession.close();

                    // Mettre à jour le progrès
                    int processed = blocksProcessed.addAndGet((int) Math.min(subRegion.getVolume(), Integer.MAX_VALUE));
                    int percentage = (int) ((processed * 100L) / totalVolume);

                    if (percentage % 25 == 0) {
                        plugin.getPluginLogger().debug("§7Génération async " + mineId + ": " + percentage + "%");
                    }

                    // Planifier le prochain chunk avec délai adaptatif
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this, currentDelay);

                } catch (Exception e) {
                    plugin.getPluginLogger().severe("§cErreur batch async " + mineId);
                    e.printStackTrace();
                    mineGenerating.get(mineId).set(false);
                }
            }
        };

        // Démarrer le traitement asynchrone
        Bukkit.getScheduler().runTaskAsynchronously(plugin, processNextChunk);
    }

    /**
     * Divise une région en sous-régions de taille maximale
     */
    private List<CuboidRegion> divideRegion(CuboidRegion region, int maxBlocksPerChunk) {
        List<CuboidRegion> subRegions = new ArrayList<>();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        // Alignement aux chunks 16x16 pour de meilleures perfs côté serveur/clients
        final int stepX = 16;
        final int stepZ = 16;

        int height = Math.max(1, max.y() - min.y() + 1);
        int maxYStep = Math.max(1, maxBlocksPerChunk / (stepX * stepZ)); // garantir volume <= maxBlocksPerChunk
        int stepY = Math.min(height, Math.max(1, maxYStep));

        for (int x = min.x(); x <= max.x(); x += stepX) {
            int maxX = Math.min(x + stepX - 1, max.x());
            for (int z = min.z(); z <= max.z(); z += stepZ) {
                int maxZ = Math.min(z + stepZ - 1, max.z());
                for (int y = min.y(); y <= max.y(); y += stepY) {
                    int maxY = Math.min(y + stepY - 1, max.y());

                    BlockVector3 subMin = BlockVector3.at(x, y, z);
                    BlockVector3 subMax = BlockVector3.at(maxX, maxY, maxZ);
                    subRegions.add(new CuboidRegion(region.getWorld(), subMin, subMax));
                }
            }
        }

        return subRegions;
    }

    /**
     * Calcule la limite de blocs selon la priorité et le TPS
     */
    private int calculateBlockLimit(MineResetPriority priority) {
        int baseLimit = currentChunkSize;

        // Ajuster selon la priorité
        switch (priority) {
            case URGENT:
                baseLimit *= 2;
                break;
            case HIGH:
                baseLimit = (int) (baseLimit * 1.5);
                break;
            case NORMAL:
                break;
            case LOW:
                baseLimit /= 2;
                break;
        }

        // Ajuster selon le TPS
        if (currentTPS < 15) {
            baseLimit /= 2;
        }

        return Math.max(1000, baseLimit); // Minimum 1000 blocs
    }

    /**
     * Finalise la génération d'une mine
     */
    private void finalizeMineGeneration(String mineId, long startTime, long volume) {
        mineResetTimes.put(mineId, System.currentTimeMillis());
        AtomicBoolean generating = mineGenerating.get(mineId);
        if (generating != null) {
            generating.set(false);
        }

        long duration = System.currentTimeMillis() - startTime;
        plugin.getPluginLogger().info("§a[FAWE] Mine " + mineId + " générée: " + volume +
                " blocs en " + duration + "ms (TPS: " + String.format("%.1f", currentTPS) + ")");

        // Notification finale
        new BukkitRunnable() {
            @Override
            public void run() {
                notifyPlayersInMine(mineId);
            }
        }.runTask(plugin);
    }

    /**
     * Téléportation sécurisée optimisée
     */
    private void teleportToMineSafely(Player player, String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) return;

        org.bukkit.World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) return;

        Location safeLoc = mine.getCenterLocation(world);
        safeLoc.setY(safeLoc.getY() + 2);

        // S'assurer que la zone est safe
        safeLoc.getBlock().setType(Material.AIR,false);
        safeLoc.clone().add(0, 1, 0).getBlock().setType(Material.AIR,false);

        player.teleport(safeLoc);
    }

    /**
     * Vérification asynchrone optimisée du pourcentage de blocs
     */
    public void checkAndRegenerateMineIfNeeded(String mineId) {
        AtomicBoolean checking = mineBeingChecked.get(mineId);
        if (checking == null || !checking.compareAndSet(false, true)) {
            return;
        }

        if (isMineGenerating(mineId)) {
            checking.set(false);
            return;
        }

        // Calcul async optimisé
        asyncExecutor.submit(() -> {
            try {
                MineData mine = getMine(mineId);
                if (mine == null) {
                    checking.set(false);
                    return;
                }

                // Utiliser FAWE pour un calcul plus rapide
                World faweWorld = FaweAPI.getWorld(mine.getWorldName());
                if (faweWorld == null) {
                    checking.set(false);
                    return;
                }

                int totalVolume = mine.getVolume();
                if (totalVolume == 0) {
                    checking.set(false);
                    return;
                }

                // Échantillonnage pour les grandes mines
                double percentageLeft;
                if (totalVolume > 100000) {
                    percentageLeft = calculatePercentageBySampling(faweWorld, mine);
                } else {
                    percentageLeft = calculatePercentageFull(faweWorld, mine);
                }

                // Décider si régénération nécessaire
                if (percentageLeft <= 0.30) {
                    plugin.getPluginLogger().info("§eRégénération auto de " + mineId +
                            " (" + String.format("%.1f%%", percentageLeft * 100) + " restant)");

                    // Priorité selon le pourcentage restant
                    MineResetPriority priority = percentageLeft <= 0.10 ?
                            MineResetPriority.HIGH : MineResetPriority.NORMAL;

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            generateMine(mineId, priority);
                        }
                    }.runTask(plugin);
                }

            } catch (Exception e) {
                plugin.getPluginLogger().severe("§cErreur calcul blocs mine " + mineId);
                e.printStackTrace();
            } finally {
                checking.set(false);
            }
        });
    }

    /**
     * Calcul par échantillonnage pour les grandes mines
     */
    private double calculatePercentageBySampling(World world, MineData mine) {
        int sampleSize = 1000; // Nombre de points à échantillonner
        int nonAirBlocks = 0;

        BlockVector3 min = BlockVector3.at(mine.getMinX(), mine.getMinY(), mine.getMinZ());
        BlockVector3 max = BlockVector3.at(mine.getMaxX(), mine.getMaxY(), mine.getMaxZ());

        for (int i = 0; i < sampleSize; i++) {
            int x = random.nextInt(max.x() - min.x() + 1) + min.x();
            int y = random.nextInt(max.y() - min.y() + 1) + min.y();
            int z = random.nextInt(max.z() - min.z() + 1) + min.z();

            if (world.getBlock(BlockVector3.at(x, y, z)).getBlockType() != BlockTypes.AIR) {
                nonAirBlocks++;
            }
        }

        return (double) nonAirBlocks / sampleSize;
    }

    /**
     * Calcul complet pour les petites mines
     */
    private double calculatePercentageFull(World world, MineData mine) {
        int remainingBlocks = 0;
        BlockVector3 min = BlockVector3.at(mine.getMinX(), mine.getMinY(), mine.getMinZ());
        BlockVector3 max = BlockVector3.at(mine.getMaxX(), mine.getMaxY(), mine.getMaxZ());

        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                for (int z = min.z(); z <= max.z(); z++) {
                    if (world.getBlock(BlockVector3.at(x, y, z)).getBlockType() != BlockTypes.AIR) {
                        remainingBlocks++;
                    }
                }
            }
        }

        return (double) remainingBlocks / mine.getVolume();
    }

    /**
     * Vérification d'accès à une mine
     */
    public boolean canAccessMine(Player player, String mineId) {
        if (mineId == null || mineId.isEmpty()) {
            return false;
        }

        String lowerMineId = mineId.toLowerCase();

        if (lowerMineId.contains("prestige")) {
            try {
                String numberPart = lowerMineId.replaceAll("[^0-9]", "");
                if (numberPart.isEmpty()) return false;
                int requiredPrestige = Integer.parseInt(numberPart);

                int playerPrestige = getPlayerPrestige(player);

                return playerPrestige >= requiredPrestige;

            } catch (NumberFormatException e) {
                return false;
            }
        } else if (lowerMineId.contains("vip")) {
            String identifier = lowerMineId.replace("mine-vip", "").replaceAll("^-", "");
            if (identifier.isEmpty()) return false;
            String requiredPermission = "specialmine.vip." + identifier;
            return player.hasPermission(requiredPermission);
        } else {
            if (lowerMineId.isEmpty()) return false;
            String requiredRank = lowerMineId.substring(lowerMineId.length() - 1);

            if (!requiredRank.matches("[a-z]")) {
                return false;
            }

            String playerRank = getCurrentRank(player);
            return isRankSufficient(playerRank, requiredRank);
        }
    }

    private int getPlayerPrestige(Player player) {
        int playerPrestige = 0;
        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String perm = permInfo.getPermission().toLowerCase();
            if (perm.startsWith("specialmine.prestige.")) {
                try {
                    String levelStr = perm.substring("specialmine.prestige.".length());
                    int level = Integer.parseInt(levelStr);
                    if (level > playerPrestige) {
                        playerPrestige = level;
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        return playerPrestige;
    }

    /**
     * Téléporte un joueur à une mine
     */
    @Deprecated
    public boolean teleportToMine(Player player, String mineId) {
        return plugin.getWarpManager().teleportToWarp(player, "mine-" + mineId, true);
    }

    /**
     * Obtient les informations détaillées d'une mine
     */
    public String getMineInfo(Player player, String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) {
            return "§c❌ Mine introuvable!";
        }

        StringBuilder info = new StringBuilder();
        info.append("§6═══════════════════════════════════\n");
        info.append("§6         📍 ").append(mine.getDisplayName()).append("\n");
        info.append("§6═══════════════════════════════════\n");

        info.append(mine.getDetailedInfo()).append("\n");

        boolean canAccess = canAccessMine(player, mineId);
        info.append("§7Accès: ").append(canAccess ? "§a✅ Autorisé" : "§c❌ Interdit").append("\n");

        if (!canAccess) {
            if (mine.getRequiredPrestige() > 0) {
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                info.append("§7Prestige requis: §d").append(mine.getRequiredPrestige())
                        .append(" §7(actuel: §d").append(playerData.getPrestigeLevel()).append("§7)\n");
            }
            if (mine.isVipOnly()) {
                info.append("§7Statut VIP requis\n");
            }
            if (mine.getRequiredPermission() != null) {
                info.append("§7Permission requise: §e").append(mine.getRequiredPermission()).append("\n");
            }
        }

        Long lastReset = mineResetTimes.get(mineId);
        if (lastReset != null) {
            long timeSince = (System.currentTimeMillis() - lastReset) / 1000 / 60;
            info.append("§7Dernier reset: il y a ").append(timeSince).append(" minute(s)\n");
        }

        // Info TPS
        info.append("§7TPS serveur: ").append(getTpsColor(currentTPS))
                .append(String.format("%.1f", currentTPS)).append("\n");

        return info.toString();
    }

    /**
     * Obtient la couleur selon le TPS
     */
    private String getTpsColor(double tps) {
        if (tps >= 19) return "§a";
        if (tps >= 17) return "§e";
        if (tps >= 15) return "§6";
        return "§c";
    }

    /**
     * Obtient les mines accessibles pour un joueur
     */
    public List<MineData> getAccessibleMines(Player player) {
        List<MineData> accessible = new ArrayList<>();

        for (MineData mine : mines.values()) {
            if (canAccessMine(player, mine.getId())) {
                accessible.add(mine);
            }
        }

        accessible.sort((a, b) -> {
            if (a.getType() != b.getType()) {
                return a.getType().ordinal() - b.getType().ordinal();
            }
            return a.getId().compareToIgnoreCase(b.getId());
        });

        return accessible;
    }

    /**
     * Obtient les mines par type
     */
    public List<MineData> getMinesByType(MineData.MineType type) {
        return mines.values().stream()
                .filter(mine -> mine.getType() == type)
                .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Vérifie si un joueur est dans une mine
     */
    public String getPlayerCurrentMine(Player player) {
        Location playerLocation = player.getLocation();

        for (Map.Entry<String, MineData> entry : mines.entrySet()) {
            if (entry.getValue().contains(playerLocation)) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Notifie tous les joueurs présents dans une mine
     */
    private void notifyPlayersInMine(String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerMine = getPlayerCurrentMine(player);
            if (mineId.equals(playerMine)) {
                player.sendMessage("§a✅ Mine régénérée avec succès !");
            }
        }
    }

    /**
     * Envoie un message à tous les joueurs dans un monde
     */
    private void broadcastToWorld(String worldName, String message) {
        org.bukkit.World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    /**
     * Lance le scheduler de reset automatique des mines
     */
    private void startMineResetScheduler() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Ne pas lancer de resets si le TPS est trop bas
                if (currentTPS < 14.0) {
                    plugin.getPluginLogger().debug("§eTPS trop bas pour les resets auto: " + String.format("%.1f", currentTPS));
                    return;
                }

                long currentTime = System.currentTimeMillis();
                long resetInterval = 1800000; // 30 minutes

                for (Map.Entry<String, Long> entry : mineResetTimes.entrySet()) {
                    String mineId = entry.getKey();
                    long lastReset = entry.getValue();

                    if (currentTime - lastReset > resetInterval && !isMineGenerating(mineId)) {
                        plugin.getPluginLogger().debug("Reset automatique de: " + mineId);
                        generateMine(mineId, MineResetPriority.LOW);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 6000L); // Toutes les 5 minutes
    }

    /**
     * Force le reset de toutes les mines
     */
    public void resetAllMines() {
        plugin.getPluginLogger().info("§6Reset échelonné de toutes les mines...");

        List<String> mineIds = new ArrayList<>(mines.keySet());
        AtomicInteger index = new AtomicInteger(0);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (index.get() >= mineIds.size()) {
                    plugin.getPluginLogger().info("§aToutes les mines ajoutées à la queue!");
                    cancel();
                    return;
                }

                // Ajouter 2-3 mines à la queue par tick selon le TPS
                int minesToAdd = currentTPS >= 18 ? 3 : (currentTPS >= 15 ? 2 : 1);

                for (int i = 0; i < minesToAdd && index.get() < mineIds.size(); i++) {
                    generateMine(mineIds.get(index.getAndIncrement()), MineResetPriority.LOW);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Recharge les mines depuis la configuration
     */
    public void reloadMines() {
        plugin.getPluginLogger().info("§7Rechargement des mines...");

        // Attendre que toutes les générations soient terminées
        for (AtomicBoolean generating : mineGenerating.values()) {
            if (generating.get()) {
                plugin.getPluginLogger().warning("§eAttente fin des générations en cours...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        mines.clear();
        mineResetTimes.clear();
        mineGenerating.clear();
        mineBeingChecked.clear();
        resetQueue.clear();

        loadMinesFromConfigManager();
        plugin.getPluginLogger().info("§aMines rechargées!");
    }

    /**
     * Obtient les statistiques des mines
     */
    public String getMinesStatistics() {
        StringBuilder stats = new StringBuilder();

        stats.append("§6═══════════════════════════════════\n");
        stats.append("§6         📊 STATISTIQUES MINES\n");
        stats.append("§6═══════════════════════════════════\n");

        int normalCount = getMinesByType(MineData.MineType.NORMAL).size();
        int prestigeCount = getMinesByType(MineData.MineType.PRESTIGE).size();
        int vipCount = getMinesByType(MineData.MineType.VIP).size();

        stats.append("§f📍 Mines normales: §7").append(normalCount).append("\n");
        stats.append("§d📍 Mines prestige: §7").append(prestigeCount).append("\n");
        stats.append("§6📍 Mines VIP: §7").append(vipCount).append("\n");
        stats.append("§e📊 Total: §7").append(mines.size()).append(" mines\n");

        long generating = mineGenerating.values().stream()
                .filter(AtomicBoolean::get)
                .count();
        if (generating > 0) {
            stats.append("§c⚡ En génération: §7").append(generating).append("\n");
        }

        stats.append("§7TPS: ").append(getTpsColor(currentTPS))
                .append(String.format("%.1f", currentTPS)).append("\n");
        stats.append("§7Queue: §e").append(resetQueue.size()).append(" mines en attente\n");
        stats.append("§7Chunk size: §b").append(currentChunkSize).append(" blocs\n");

        return stats.toString();
    }

    /**
     * Arrêt propre du manager
     */
    public void shutdown() {
        plugin.getPluginLogger().info("§7Arrêt du MineManager...");

        // Arrêter l'executor
        asyncExecutor.shutdown();
        try {
            if (!asyncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                asyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            asyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        plugin.getPluginLogger().info("§aMineManager arrêté proprement");
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    public String getCurrentRank(Player player) {
        String highestRank = "a";

        Set<String> minePermissions = player.getEffectivePermissions().stream()
                .map(PermissionAttachmentInfo::getPermission)
                .filter(perm -> perm.startsWith("specialmine.mine."))
                .collect(Collectors.toSet());

        for (char c = 'z'; c >= 'a'; c--) {
            String minePermission = "specialmine.mine." + c;
            if (minePermissions.contains(minePermission)) {
                highestRank = String.valueOf(c);
                break;
            }
        }
        return highestRank;
    }

    public boolean isRankSufficient(String currentRank, String requiredRank) {
        if (currentRank == null || requiredRank == null) return false;
        return currentRank.compareToIgnoreCase(requiredRank) >= 0;
    }

    public String getRankColor(String rank) {
        if (rank == null) return "§7";

        return switch (rank.toLowerCase()) {
            case "a", "b", "c", "d", "e" -> "§f";
            case "f", "g", "h", "i", "j" -> "§a";
            case "k", "l", "m", "n", "o" -> "§e";
            case "p", "q", "r", "s", "t" -> "§6";
            case "u", "v", "w", "x", "y", "z" -> "§c";
            default -> "§7";
        };
    }

    public int rankToNumber(String rank) {
        if (rank == null || rank.length() != 1) return 0;
        char c = rank.toLowerCase().charAt(0);
        if (c >= 'a' && c <= 'z') {
            return c - 'a' + 1;
        }
        return 0;
    }

    public String numberToRank(int number) {
        if (number < 1 || number > 26) return "a";
        return String.valueOf((char) ('a' + number - 1));
    }

    public String[] getRankAndColor(Player player) {
        String currentRank = getCurrentRank(player);
        String rankColor = getRankColor(currentRank);
        return new String[]{currentRank, rankColor};
    }

    // ==================== GETTERS ====================

    public MineData getMine(String mineId) {
        return mines.get(mineId);
    }

    public Collection<MineData> getAllMines() {
        return mines.values();
    }

    public boolean mineExists(String mineId) {
        return mines.containsKey(mineId);
    }

    public int getMineCount() {
        return mines.size();
    }

    public Set<String> getMineNames() {
        return new HashSet<>(mines.keySet());
    }

    public boolean isMineGenerating(String mineId) {
        AtomicBoolean generating = mineGenerating.get(mineId);
        return generating != null && generating.get();
    }

    public List<MineData> getAllMinesSorted() {
        return mines.values().stream()
                .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                .collect(Collectors.toList());
    }

    public List<MineData> searchMines(String query) {
        String lowerQuery = query.toLowerCase();
        return mines.values().stream()
                .filter(mine -> mine.getId().toLowerCase().contains(lowerQuery) ||
                        mine.getDisplayName().toLowerCase().contains(lowerQuery))
                .sorted((a, b) -> a.getId().compareToIgnoreCase(b.getId()))
                .collect(Collectors.toList());
    }

    public double getCurrentTPS() {
        return currentTPS;
    }

    public int getQueueSize() {
        return resetQueue.size();
    }

    // ==================== CLASSES INTERNES ====================

    private boolean isAnyMineGenerating() {
        for (AtomicBoolean generating : mineGenerating.values()) {
            if (generating.get()) return true;
        }
        return false;
    }


    /**
     * Énumération des priorités de reset
     */
    public enum MineResetPriority {
        URGENT(0),
        HIGH(1),
        NORMAL(2),
        LOW(3);

        private final int priority;

        MineResetPriority(int priority) {
            this.priority = priority;
        }

        public int getPriority() {
            return priority;
        }
    }

    /**
     * Tâche de reset de mine avec priorité
     */
    private record MineResetTask(String mineId, MineResetPriority priority,
                                 long timestamp) implements Comparable<MineResetTask> {

        @Override
        public int compareTo(MineResetTask other) {
            int priorityCompare = Integer.compare(this.priority.getPriority(), other.priority.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(this.timestamp, other.timestamp);
        }
    }
    /**
     * Fallback mine generation using the Bukkit API.
     * This is slower and more resource-intensive than FAWE but provides a failsafe.
     *
     * @param mineId The ID of the mine to regenerate.
     */
    private void generateWithBukkitAPI(String mineId) {
        MineData mine = mines.get(mineId);
        if (mine == null) {
            plugin.getPluginLogger().warning("§c[Bukkit Fallback] Mine not found: " + mineId);
            return;
        }

        AtomicBoolean generating = mineGenerating.get(mineId);
        if (generating != null && !generating.get()) {
            // If FAWE failed, the flag might be false. Set it to true for this process.
            if (!generating.compareAndSet(false, true)) {
                plugin.getPluginLogger().warning("§e[Bukkit Fallback] Mine is already being generated by another process: " + mineId);
                return;
            }
        } else if (generating == null) {
            // Should not happen, but as a safeguard.
            mineGenerating.put(mineId, new AtomicBoolean(true));
        }


        plugin.getPluginLogger().info("§e[Bukkit Fallback] Starting generation for mine: " + mineId);

        long startTime = System.currentTimeMillis();
        org.bukkit.World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) {
            plugin.getPluginLogger().severe("§c[Bukkit Fallback] World not found: " + mine.getWorldName());
            mineGenerating.get(mineId).set(false);
            return;
        }

        // Create the block pattern list
        List<Material> blockPattern = new ArrayList<>();
        for (Map.Entry<Material, Double> entry : mine.getBlockComposition().entrySet()) {
            int amount = (int) (mine.getVolume() * entry.getValue());
            for (int i = 0; i < amount; i++) {
                blockPattern.add(entry.getKey());
            }
        }
        // Fill any remaining space with the first block type if calculation is imperfect
        while (blockPattern.size() < mine.getVolume() && !mine.getBlockComposition().isEmpty()) {
            blockPattern.add(mine.getBlockComposition().keySet().iterator().next());
        }
        Collections.shuffle(blockPattern, random);

        Iterator<Material> patternIterator = blockPattern.iterator();

        new BukkitRunnable() {
            private int x = mine.getMinX();
            private int y = mine.getMinY();
            private int z = mine.getMinZ();
            private int processed = 0;

            @Override
            public void run() {
                // Ajuste dynamiquement la cadence selon le TPS
                int blocksPerTick;
                double tps = currentTPS;
                if (tps >= 19.5) {
                    blocksPerTick = 4096;
                } else if (tps >= 18.0) {
                    blocksPerTick = 3072;
                } else if (tps >= 16.0) {
                    blocksPerTick = 2048;
                } else if (tps >= 14.0) {
                    blocksPerTick = 1024;
                } else {
                    blocksPerTick = 512;
                }
                for (int i = 0; i < blocksPerTick; i++) {
                    if (!patternIterator.hasNext() || y > mine.getMaxY()) {
                        // Finalize and stop
                        long duration = System.currentTimeMillis() - startTime;
                        plugin.getPluginLogger().info("§a[Bukkit Fallback] Mine " + mineId + " generated in " + duration + "ms.");
                        finalizeMineGeneration(mineId, startTime, mine.getVolume());
                        cancel();
                        return;
                    }

                    Material materialToPlace = patternIterator.next();
                    world.getBlockAt(x, y, z).setType(materialToPlace, false); // 'false' to prevent physics updates during generation

                    processed++;
                    z++;
                    if (z > mine.getMaxZ()) {
                        z = mine.getMinZ();
                        x++;
                        if (x > mine.getMaxX()) {
                            x = mine.getMinX();
                            y++;
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L); // Runs every tick
    }
}