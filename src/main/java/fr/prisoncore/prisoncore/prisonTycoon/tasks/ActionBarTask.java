package fr.prisoncore.prisoncore.prisonTycoon.tasks;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;

/**
 * CORRIGÉ : Ajout des notifications régulières pour durabilité < 25%
 */
public class ActionBarTask extends BukkitRunnable {

    private final PrisonTycoon plugin;
    private long tickCount = 0;
    private final Random random = new Random();

    public ActionBarTask(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;
        updateActionBarStatus();
    }

    /**
     * Met à jour l'action bar avec l'état des enchantements actifs
     */
    public void updateActionBarStatus() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            String statusMessage = generateStatusMessage(player);
            if (statusMessage != null && !statusMessage.isEmpty()) {
                player.sendActionBar(statusMessage);
            }
        }
    }

    /**
     * CORRIGÉ : Génère le message avec notifications de durabilité régulières
     */
    private String generateStatusMessage(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        StringBuilder status = new StringBuilder();

        boolean currentlyMining = playerData.isCurrentlyMining();
        boolean isPickaxeBroken = plugin.getEnchantmentManager().isPlayerPickaxeBroken(player);

        // HARMONISATION : Priorité aux messages temporaires de changement d'état
        if (player.hasMetadata("pickaxe_just_broken")) {
            long brokenTime = player.getMetadata("pickaxe_just_broken").get(0).asLong();
            if (System.currentTimeMillis() - brokenTime < 3000) {
                return "§c💥 PIOCHE CASSÉE! Tous enchantements désactivés sauf Token Greed (90% malus)";
            } else {
                player.removeMetadata("pickaxe_just_broken", plugin);
            }
        }

        if (player.hasMetadata("pickaxe_just_repaired")) {
            long repairedTime = player.getMetadata("pickaxe_just_repaired").get(0).asLong();
            if (System.currentTimeMillis() - repairedTime < 3000) {
                return "§a✅ Pioche réparée! Tous les enchantements sont actifs";
            } else {
                player.removeMetadata("pickaxe_just_repaired", plugin);
            }
        }

        // Si la pioche est cassée (état permanent), affiche le message d'avertissement
        if (isPickaxeBroken) {
            return "§c💀 PIOCHE CASSÉE! Réparez-la pour retrouver ses capacités!";
        }

        // NOUVEAU : Notifications régulières de durabilité quand le joueur mine
        if (currentlyMining) {
            String durabilityNotification = checkDurabilityWarnings(player);
            if (durabilityNotification != null) {
                return durabilityNotification;
            }
        }

        // Messages normaux d'enchantements si le joueur mine
        if (!currentlyMining) {
            return ""; // Pas de message si pas en train de miner
        }

        // États spéciaux (combustion, abondance, etc.)
        if (playerData.getCombustionLevel() > 0) {
            if (status.length() > 0) status.append(" §8| ");
            double multiplier = playerData.getCombustionMultiplier();
            status.append("§c🔥 Combustion: §6x").append(String.format("%.2f", multiplier));
        }

        if (playerData.isAbundanceActive()) {
            if (status.length() > 0) status.append(" §8| ");
            status.append("§6⭐ Abondance: §aACTIVE");
        }

        return status.toString();
    }

    /**
     * NOUVEAU : Vérifie et affiche les avertissements de durabilité de façon régulière
     */
    private String checkDurabilityWarnings(Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
        if (pickaxe == null) return null;

        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();
        double durabilityPercent = 1.0 - ((double) currentDurability / maxDurability);

        // Seulement quand < 25% de durabilité
        if (durabilityPercent > 0.25) return null;

        // Fréquence basée sur le niveau de durabilité (plus bas = plus fréquent)
        int warningFrequency;
        String warningMessage;

        if (durabilityPercent <= 0.05) { // Moins de 5% - très critique
            warningFrequency = 20; // Toutes les secondes (20 ticks)
            warningMessage = "§c💀 URGENT! Pioche CRITIQUE! Réparez MAINTENANT! (" + String.format("%.1f%%", durabilityPercent * 100) + ")";
        } else if (durabilityPercent <= 0.10) { // Moins de 10% - critique
            warningFrequency = 40; // Toutes les 2 secondes
            warningMessage = "§c⚠️ CRITIQUE! Pioche très endommagée! (" + String.format("%.1f%%", durabilityPercent * 100) + ")";
        } else if (durabilityPercent <= 0.15) { // Moins de 15% - urgent
            warningFrequency = 60; // Toutes les 3 secondes
            warningMessage = "§6⚠️ URGENT! Réparez votre pioche! (" + String.format("%.1f%%", durabilityPercent * 100) + ")";
        } else { // 15-25% - attention
            warningFrequency = 100; // Toutes les 5 secondes
            warningMessage = "§e⚠️ Attention: Pioche endommagée (" + String.format("%.1f%%", durabilityPercent * 100) + ")";
        }

        // Affiche le message selon la fréquence
        if (tickCount % warningFrequency == 0) {
            return warningMessage;
        }

        return null;
    }

    public ActionBarStats getStats() {
        return new ActionBarStats(
                tickCount,
                plugin.getServer().getOnlinePlayers().size()
        );
    }

    public static class ActionBarStats {
        private final long totalTicks;
        private final int onlinePlayers;

        public ActionBarStats(long totalTicks, int onlinePlayers) {
            this.totalTicks = totalTicks;
            this.onlinePlayers = onlinePlayers;
        }

        public long getTotalTicks() { return totalTicks; }
        public int getOnlinePlayers() { return onlinePlayers; }

        @Override
        public String toString() {
            return String.format("ActionBarStats{ticks=%d, players=%d}",
                    totalTicks, onlinePlayers);
        }
    }
}