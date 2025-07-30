package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire du système de réputation
 * Gère l'échelle bidirectionnelle de -1000 à +1000 avec spécialisation vertueuse ou criminelle
 */
public class ReputationManager {

    // Seuil d'érosion naturelle
    private static final int EROSION_THRESHOLD = 750;
    private static final int EROSION_AMOUNT = 1;
    private final PrisonTycoon plugin;
    // Cache des réputations pour optimisation
    private final Map<UUID, Integer> reputationCache;
    // Historique récent des changements (pour /rep help)
    private final Map<UUID, List<ReputationChange>> recentChanges;

    public ReputationManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.reputationCache = new ConcurrentHashMap<>();
        this.recentChanges = new ConcurrentHashMap<>();

        // Démarre la tâche d'érosion naturelle (1 fois par jour)
        startErosionTask();

        plugin.getPluginLogger().info("§aReputationManager initialisé avec érosion naturelle.");
    }

    /**
     * Obtient la réputation actuelle d'un joueur
     */
    public int getReputation(UUID playerId) {
        // Vérifie le cache d'abord
        Integer cached = reputationCache.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Charge depuis PlayerData
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        int reputation = playerData.getReputation();

        // Met en cache
        reputationCache.put(playerId, reputation);
        return reputation;
    }

    /**
     * Obtient le niveau de réputation d'un joueur
     */
    public ReputationTier getReputationTier(UUID playerId) {
        return ReputationTier.fromReputation(getReputation(playerId));
    }

    /**
     * Modifie la réputation d'un joueur avec progression non-linéaire
     */
    public void modifyReputation(UUID playerId, int change, String reason) {
        if (change == 0) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(playerId);
        int currentReputation = getReputation(playerId);
        ReputationTier currentTier = ReputationTier.fromReputation(currentReputation);

        // Applique la résistance progressive pour les valeurs extrêmes
        int adjustedChange = applyProgressiveResistance(currentReputation, change);

        // Calcule la nouvelle réputation
        int newReputation = Math.max(-1000, Math.min(1000, currentReputation + adjustedChange));

        // Met à jour les données
        playerData.setReputation(newReputation);
        reputationCache.put(playerId, newReputation);
        plugin.getPlayerDataManager().markDirty(playerId);

        // Enregistre le changement dans l'historique
        recordReputationChange(playerId, change, adjustedChange, reason, currentReputation, newReputation);

        // Vérifie le changement de niveau
        ReputationTier newTier = ReputationTier.fromReputation(newReputation);
        if (currentTier != newTier) {
            notifyTierChange(playerId, currentTier, newTier);
        }

        plugin.getPluginLogger().debug("Réputation de " + playerData.getPlayerName() +
                                       ": " + currentReputation + " → " + newReputation + " (" + reason + ")");
    }

    /**
     * Applique la résistance progressive selon les spécifications
     */
    private int applyProgressiveResistance(int currentReputation, int change) {
        int absReputation = Math.abs(currentReputation);

        // Pas de résistance en dessous de 500
        if (absReputation < 500) {
            return change;
        }

        // Résistance progressive entre 500 et 1000
        double resistanceFactor = 1.0;

        if (absReputation >= 500 && absReputation < 750) {
            resistanceFactor = 0.8; // 20% de résistance
        } else if (absReputation >= 750 && absReputation < 900) {
            resistanceFactor = 0.6; // 40% de résistance
        } else if (absReputation >= 900) {
            resistanceFactor = 0.4; // 60% de résistance
        }

        return (int) Math.round(change * resistanceFactor);
    }

    /**
     * Enregistre un changement de réputation dans l'historique
     */
    private void recordReputationChange(UUID playerId, int originalChange, int appliedChange,
                                        String reason, int oldReputation, int newReputation) {
        List<ReputationChange> history = recentChanges.computeIfAbsent(playerId, k -> new ArrayList<>());

        ReputationChange change = new ReputationChange(
                System.currentTimeMillis(),
                originalChange,
                appliedChange,
                reason,
                oldReputation,
                newReputation
        );

        history.addFirst(change); // Ajoute au début

        // Garde seulement les 10 derniers changements
        if (history.size() > 10) {
            history.subList(10, history.size()).clear();
        }
    }

    /**
     * Notifie un joueur du changement de niveau de réputation
     */
    private void notifyTierChange(UUID playerId, ReputationTier oldTier, ReputationTier newTier) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        player.sendMessage("§6═══════════════════════════════");

        if (newTier.ordinal() > oldTier.ordinal()) {
            player.sendMessage("§a§l✓ RÉPUTATION AMÉLIORÉE !");
            player.sendMessage("§7Vous êtes passé de §f" + oldTier.getColoredTitle() + " §7à §f" + newTier.getColoredTitle());
        } else {
            player.sendMessage("§c§l✗ RÉPUTATION DÉGRADÉE !");
            player.sendMessage("§7Vous êtes passé de §f" + oldTier.getColoredTitle() + " §7à §f" + newTier.getColoredTitle());
        }

        player.sendMessage("");
        player.sendMessage("§7Nouveaux effets:");
        player.sendMessage(newTier.getEffectsDescription());
        player.sendMessage("§6═══════════════════════════════");
    }

    /**
     * Obtient l'historique récent des changements d'un joueur
     */
    public List<ReputationChange> getRecentChanges(UUID playerId) {
        return recentChanges.getOrDefault(playerId, new ArrayList<>());
    }

    /**
     * Démarre la tâche d'érosion naturelle
     */
    private void startErosionTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processNaturalErosion();
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 60 * 60 * 24, 20 * 60 * 60 * 24); // 24h en ticks
    }

    /**
     * Traite l'érosion naturelle des points extrêmes
     */
    private void processNaturalErosion() {
        int processed = 0;

        for (UUID playerId : reputationCache.keySet()) {
            int currentReputation = getReputation(playerId);

            if (Math.abs(currentReputation) > EROSION_THRESHOLD) {
                int erosion = currentReputation > 0 ? -EROSION_AMOUNT : EROSION_AMOUNT;
                modifyReputation(playerId, erosion, "Érosion naturelle");
                processed++;
            }
        }

        if (processed > 0) {
            plugin.getPluginLogger().info("Érosion naturelle: " + processed + " joueurs traités");
        }
    }

    /**
     * Calcule le modificateur de taxes pour un joueur
     */
    public double getTaxModifier(UUID playerId) {
        return getReputationTier(playerId).getTaxModifier();
    }

    /**
     * Vérifie si un joueur peut accéder au Black Market
     */
    public boolean canAccessBlackMarket(UUID playerId) {
        ReputationTier tier = getReputationTier(playerId);
        return tier != ReputationTier.EXEMPLAIRE; // Exemplaire n'a aucune offre
    }

    /**
     * Obtient le modificateur de prix du Black Market pour un joueur
     */
    public double getBlackMarketPriceModifier(UUID playerId) {
        return getReputationTier(playerId).getBlackMarketPriceModifier();
    }

    /**
     * Gère la participation à "Contenir la Brèche" (événement coopératif)
     */
    public void handleBreachContainmentParticipation(UUID playerId, boolean isTop3) {
        if (isTop3) {
            modifyReputation(playerId, 20, "Top 3 Contenir la Brèche");
        } else {
            modifyReputation(playerId, 5, "Participation Contenir la Brèche");
        }
    }

    /**
     * Gère la participation à "Course au Butin" (événement compétitif)
     */
    public void handleTreasureHuntParticipation(UUID playerId, boolean isTop3) {
        if (isTop3) {
            modifyReputation(playerId, -20, "Top 3 Course au Butin");
        } else {
            modifyReputation(playerId, -5, "Participation Course au Butin");
        }
    }

    /**
     * Gère les transactions du Black Market
     */
    public void handleBlackMarketTransaction(UUID playerId, int itemValue) {
        // Entre -1 et -3 points selon la valeur de l'item
        int penalty = Math.max(-3, Math.min(-1, -(itemValue / 10)));
        modifyReputation(playerId, penalty, "Transaction Black Market");
    }

    /**
     * Obtient le modificateur de vitesse pour "Contenir la Brèche"
     */
    public double getBreachContainmentSpeedModifier(UUID playerId) {
        ReputationTier tier = getReputationTier(playerId);

        if (tier.isPositive()) {
            return 1.0; // Vitesse normale
        } else if (tier == ReputationTier.ORDINAIRE) {
            return 0.95; // -5%
        } else {
            // Réputation négative: -10% à -30%
            return switch (tier) {
                case SUSPECT -> 0.90; // -10%
                case CRIMINEL -> 0.80; // -20%
                case INFAME -> 0.70; // -30%
                default -> 0.95;
            };
        }
    }

    /**
     * Obtient le modificateur de vision pour "Course au Butin"
     */
    public double getTreasureHuntVisionModifier(UUID playerId) {
        ReputationTier tier = getReputationTier(playerId);

        if (tier.isNegative()) {
            return 1.0; // Vision parfaite
        } else if (tier == ReputationTier.ORDINAIRE) {
            return 0.90; // -10%
        } else {
            // Réputation positive: -20% à -50%
            return switch (tier) {
                case RESPECTE -> 0.80; // -20%
                case HONORABLE -> 0.65; // -35%
                case EXEMPLAIRE -> 0.50; // -50%
                default -> 0.90;
            };
        }
    }

    /**
     * Nettoie le cache et l'historique (méthode utilitaire)
     */
    public void cleanup() {
        // Nettoie l'historique ancien (plus de 7 jours)
        long weekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L);

        for (Map.Entry<UUID, List<ReputationChange>> entry : recentChanges.entrySet()) {
            List<ReputationChange> changes = entry.getValue();
            changes.removeIf(change -> change.timestamp < weekAgo);
        }

        plugin.getPluginLogger().debug("Cache réputation nettoyé");
    }

    /**
     * Classe interne pour représenter un changement de réputation
     */
    public record ReputationChange(long timestamp, int originalChange, int appliedChange, String reason,
                                   int oldReputation, int newReputation) {

        public String getFormattedTime() {
            LocalDateTime dateTime = LocalDateTime.ofEpochSecond(timestamp / 1000, 0,
                    java.time.ZoneOffset.systemDefault().getRules().getOffset(java.time.Instant.ofEpochMilli(timestamp)));
            return dateTime.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
        }

        public String getChangeDisplay() {
            String color = appliedChange >= 0 ? "§a+" : "§c";
            return color + appliedChange;
        }
    }
}