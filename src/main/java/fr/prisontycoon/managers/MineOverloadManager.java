package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.MineData;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestionnaire de surcharge des mines (Mine Overload)
 * <p>
 * - Calcule un multiplicateur de Greed par mine selon une jauge de surcharge
 * - La jauge augmente avec le nombre de mineurs actifs et décroit automatiquement
 * - À chaque palier atteint: notification aux joueurs présents
 * - Au max: réinitialisation + animation + récompense Top 3 (clé légendaire)
 * - Affiche des nametags/hologrammes centrés au-dessus de la mine
 */
public class MineOverloadManager {

    private final PrisonTycoon plugin;

    /**
     * État par mine
     */
    private final Map<String, OverloadState> mineIdToState = new ConcurrentHashMap<>();

    /**
     * États de minage récents par joueur (pour compter les mineurs actifs)
     */
    private final Map<UUID, RecentMining> recentMining = new ConcurrentHashMap<>();

    // Hologrammes gérés par MineManager

    // Configuration (chargée depuis config.yml)
    private final double[] TIER_THRESHOLDS;
    private final double[] TIER_MULTIPLIERS;
    private final long ACTIVE_WINDOW_MS; // Un joueur est considéré actif s'il a miné récemment
    private final double BASE_INCREASE_PER_BLOCK = 0.0005; // Augmentation par bloc et par mineur actif
    private final double DECAY_PER_SECOND; // Décroissance par seconde quand personne ne mine

    public MineOverloadManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        // Charger la configuration surcharge
        this.TIER_THRESHOLDS = plugin.getConfigManager().getOverloadThresholds();
        this.TIER_MULTIPLIERS = plugin.getConfigManager().getOverloadMultipliers();
        this.ACTIVE_WINDOW_MS = plugin.getConfigManager().getOverloadActiveWindowMs();
        this.DECAY_PER_SECOND = plugin.getConfigManager().getOverloadDecayPerSecond();
        startScheduler();
    }

    /**
     * Appelé à chaque bloc miné dans une mine.
     */
    public void onBlockMined(Player player, String mineId, Material material) {
        if (mineId == null) return;

        // Beacon hors portée (les greeds key sont exclus du multiplicateur mais la jauge peut quand même évoluer)
        OverloadState state = mineIdToState.computeIfAbsent(mineId, m -> new OverloadState());

        long now = System.currentTimeMillis();
        recentMining.put(player.getUniqueId(), new RecentMining(mineId, now));
        state.lastActivityMs = now;

        int activeMiners = getActiveMinersCount(mineId);
        // Anti-bruit: borne à [1..50]
        activeMiners = Math.max(1, Math.min(50, activeMiners));

        // Augmentation de jauge selon mineurs actifs
        double inc = BASE_INCREASE_PER_BLOCK * activeMiners;
        state.gauge = Math.min(1.0, state.gauge + inc);

        // Compteur Top 3 pendant la session de surcharge courante
        state.blocksMinedDuringCycle.merge(player.getUniqueId(), 1L, Long::sum);

        // Détection de nouveau palier
        int newTier = computeTier(state.gauge);
        if (newTier > state.currentTier) {
            state.currentTier = newTier;
            notifyTierUp(mineId, newTier, activeMiners);
        }

        // Max atteint -> reset & récompenses
        if (state.gauge >= 1.0 - 1e-6) {
            handleOverloadMax(mineId);
        }
    }

    /**
     * Multiplicateur applicable aux catégories Greed (hors KeyGreed)
     */
    public double getOverloadMultiplier(Player player, GlobalBonusManager.BonusCategory category) {
        if (player == null) return 1.0;
        if (!isGreedCategory(category)) return 1.0; // Appliqué uniquement aux greeds

        String mineId = plugin.getMineManager().getPlayerCurrentMine(player);
        if (mineId == null) return 1.0;

        OverloadState state = mineIdToState.get(mineId);
        if (state == null) return 1.0;

        return TIER_MULTIPLIERS[Math.max(0, Math.min(TIER_MULTIPLIERS.length - 1, state.currentTier))];
    }

    /**
     * Renvoie le pourcentage de bonus (ex: +50%) associé à la surcharge actuelle pour un joueur/catégorie
     */
    public double getOverloadBonusPercent(Player player, GlobalBonusManager.BonusCategory category) {
        double mult = getOverloadMultiplier(player, category);
        return (mult - 1.0) * 100.0;
    }

    /**
     * Nombre de mineurs actifs récents dans la mine (fenêtre de 3 secondes)
     */
    public int getActiveMinersCount(String mineId) {
        long now = System.currentTimeMillis();
        int count = 0;
        for (RecentMining rm : recentMining.values()) {
            if (rm != null && rm.mineId.equals(mineId) && (now - rm.timestampMs) <= ACTIVE_WINDOW_MS) {
                count++;
            }
        }
        return count;
    }

    /**
     * Renvoie le Top 3 (nom -> blocs) de la session de surcharge courante d'une mine
     */
    public List<Map.Entry<UUID, Long>> getTop3(String mineId) {
        OverloadState state = mineIdToState.get(mineId);
        if (state == null) return List.of();
        return state.blocksMinedDuringCycle.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(3)
                .toList();
    }

    private void startScheduler() {
        // Décroissance et mise à jour hologrammes
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Map.Entry<String, OverloadState> entry : mineIdToState.entrySet()) {
                    String mineId = entry.getKey();
                    OverloadState state = entry.getValue();

                    // Décroissance si inactif
                    if ((now - state.lastActivityMs) > ACTIVE_WINDOW_MS) {
                        double old = state.gauge;
                        state.gauge = Math.max(0.0, state.gauge - DECAY_PER_SECOND);
                        int newTier = computeTier(state.gauge);
                        if (newTier < state.currentTier) {
                            state.currentTier = newTier;
                            // Optionnel: message de perte de palier (éviter spam)
                        }
                        if (old != state.gauge) {
                            plugin.getMineManager().updateMineHologram(mineId);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void notifyTierUp(String mineId, int tier, int activeMiners) {
        MineData mine = plugin.getMineManager().getMine(mineId);
        String mineName = mine != null ? mine.getDisplayName() : mineId;
        double mult = TIER_MULTIPLIERS[Math.max(0, Math.min(TIER_MULTIPLIERS.length - 1, tier))];
        String msg = "§c§l[SURCHARGE] §7La mine §f" + mineName + " §7passe au palier §c" + tier +
                " §7(§cx" + String.format(Locale.FRANCE, "%.2f", mult) + "§7). §7Mineurs actifs: §c" + activeMiners;

        broadcastToPlayersInMine(mineId, msg);
        plugin.getMineManager().updateMineHologram(mineId);
        // Effets visuels/sonores
        Location center = getMineCenter(mineId);
        playTierUpEffects(center, tier);
    }

    private void handleOverloadMax(String mineId) {
        OverloadState state = mineIdToState.get(mineId);
        if (state == null) return;

        MineData mine = plugin.getMineManager().getMine(mineId);
        String mineName = mine != null ? mine.getDisplayName() : mineId;

        // Animation de fin
        Location center = getMineCenter(mineId);
        if (center != null) {
            spawnOverloadAnimation(center);
        }

        // Récompense Top 3
        List<Map.Entry<UUID, Long>> top3 = getTop3(mineId);
        for (Map.Entry<UUID, Long> entry : top3) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) {
                ItemStack legendaryKey = plugin.getEnchantmentManager().createKey("Légendaire");
                boolean toContainer = plugin.getContainerManager().addItemToContainers(p, legendaryKey);
                if (!toContainer) {
                    p.getInventory().addItem(legendaryKey);
                }
                p.sendMessage("§6🎁 Récompense Surcharge: §e1 Clé Légendaire (Top Mineur)");
            }
        }

        String winners = top3.stream()
                .map(e -> {
                    Player p = Bukkit.getPlayer(e.getKey());
                    String name = p != null ? p.getName() : "?";
                    return name + " (§f" + e.getValue() + "§7)";
                })
                .collect(Collectors.joining(" §7| "));

        broadcastToPlayersInMine(mineId, "§c§l[SURCHARGE] §7Maximum atteint dans §f" + mineName + "§7! Réinitialisation. Top: §6" + winners);

        // Reset jauge & compteurs
        state.gauge = 0.0;
        state.currentTier = 0;
        state.blocksMinedDuringCycle.clear();
        state.lastActivityMs = System.currentTimeMillis();
        plugin.getMineManager().updateMineHologram(mineId);
    }

    private void broadcastToPlayersInMine(String mineId, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String pm = plugin.getMineManager().getPlayerCurrentMine(player);
            if (mineId.equals(pm)) {
                player.sendMessage(message);
            }
        }
    }

    private int computeTier(double gauge) {
        for (int i = TIER_THRESHOLDS.length - 1; i >= 0; i--) {
            if (gauge >= TIER_THRESHOLDS[i] - 1e-9) {
                return i;
            }
        }
        return 0;
    }

    private boolean isGreedCategory(GlobalBonusManager.BonusCategory category) {
        return category == GlobalBonusManager.BonusCategory.TOKEN_BONUS ||
                category == GlobalBonusManager.BonusCategory.MONEY_BONUS ||
                category == GlobalBonusManager.BonusCategory.EXPERIENCE_BONUS ||
                category == GlobalBonusManager.BonusCategory.FORTUNE_BONUS;
    }

    private Location getMineCenter(String mineId) {
        MineData mine = plugin.getMineManager().getMine(mineId);
        if (mine == null) return null;
        World world = Bukkit.getWorld(mine.getWorldName());
        if (world == null) return null;
        return mine.getCenterLocation(world).add(0, 2.5, 0);
    }

    private void spawnOverloadAnimation(Location center) {
        if (center == null) return;
        World world = center.getWorld();
        if (world == null) return;
        world.playSound(center, org.bukkit.Sound.ENTITY_GHAST_SCREAM, 0.7f, 1.2f);
        world.playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        // Particules ascendantes
        for (int i = 0; i < 8; i++) {
            world.spawnParticle(Particle.FLAME, center.clone().add(0, i * 0.35, 0), 30, 0.8, 0.2, 0.8, 0.015);
            world.spawnParticle(Particle.WHITE_ASH, center.clone().add(0, i * 0.35, 0), 10, 0.6, 0.1, 0.6, 0.01);
        }
    }

    private void playTierUpEffects(Location center, int tier) {
        if (center == null) return;
        World world = center.getWorld();
        if (world == null) return;
        float pitch = 0.9f + (tier * 0.05f);
        world.playSound(center, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.7f, pitch);
        world.playSound(center, org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, pitch + 0.2f);

        int rings = Math.min(5, Math.max(1, tier));
        for (int r = 1; r <= rings; r++) {
            double radius = 0.8 + (r * 0.35);
            int points = 16 + (r * 8);
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double x = radius * Math.cos(angle);
                double z = radius * Math.sin(angle);
                world.spawnParticle(Particle.LARGE_SMOKE, center.clone().add(x, 0.2 + r * 0.2, z), 1);
            }
        }
    }

    // Hologrammes: toute la logique déplacée dans MineManager

    private static class OverloadState {
        final Map<UUID, Long> blocksMinedDuringCycle = new HashMap<>();
        double gauge = 0.0; // 0..1
        int currentTier = 0; // index dans TIER_*
        long lastActivityMs = System.currentTimeMillis();
    }

    private record RecentMining(String mineId, long timestampMs) {
    }
}


