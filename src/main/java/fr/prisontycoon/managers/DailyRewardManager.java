package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Gestionnaire des récompenses journalières (15 paliers, reset ensuite)
 * Sauvegarde via PlayerData/PlayerDataManager (pas de nouvelle table).
 */
public class DailyRewardManager {

    private final PrisonTycoon plugin;

    public DailyRewardManager(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    // =========================
    // Données / Accès (via PlayerData)
    // =========================

    public int getProgress(UUID playerId) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);
        return data != null ? data.getDailyProgress() : 0;
    }

    public long getLastClaimMillis(UUID playerId) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);
        return data != null ? data.getDailyLastClaim() : 0L;
    }

    public int getClaimableDay(UUID playerId) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);
        return data != null ? data.getDailyClaimableDay() : 1;
    }

    public boolean hasClaimedToday(UUID playerId) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);
        return data != null && data.hasClaimedToday(LocalDate.now());
    }

    public boolean canClaimToday(UUID playerId) {
        return !hasClaimedToday(playerId);
    }

    public void notifyIfClaimAvailable(Player player) {
        if (player == null) return;
        if (canClaimToday(player.getUniqueId())) {
            player.sendMessage("§6🎁 Récompense journalière disponible! §eUtilisez §a/jour §eou §a/daily§e.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
        }
    }

    // =========================
    // Réclamation
    // =========================

    public boolean tryClaim(Player player) {
        if (player == null) return false;
        UUID pid = player.getUniqueId();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(pid);
        if (data == null) return false;

        if (!canClaimToday(pid)) {
            player.sendMessage("§cVous avez déjà récupéré votre récompense aujourd'hui.");
            return false;
        }

        int day = data.getDailyClaimableDay();
        boolean success = grantReward(player, day);
        if (!success) return false;

        data.advanceDailyProgress();
        data.setDailyLastClaim(System.currentTimeMillis());
        plugin.getPlayerDataManager().markDirty(pid);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        player.sendMessage("§a✅ Récompense du §eJour " + day + " §arécupérée!");
        return true;
    }

    // =========================
    // Récompenses (switch 1..15)
    // =========================

    public String getRewardDescription(int day) {
        return switch (day) {
            case 1 -> "5000 tokens";
            case 2 -> "10 000 coins";
            case 3 -> "Clé Commune x1";
            case 4 -> "XP 5 000";
            case 5 -> "Beacons x25";
            case 6 -> "Clé Peu Commune x1";
            case 7 -> "Tokens x15 000";
            case 8 -> "Coins x25 000";
            case 9 -> "Cristal vierge Niv. 5";
            case 10 -> "Clé Rare x1";
            case 11 -> "XP 15 000";
            case 12 -> "Beacons x100";
            case 13 -> "Clé Légendaire x1";
            case 14 -> "Tokens x50 000";
            case 15 -> "Coffre final: Cristal vierge Niv. 10 + Clé Cristal";
            default -> "?";
        };
    }

    private boolean grantReward(Player player, int day) {
        try {
            switch (day) {
                case 1 -> plugin.getEconomyManager().addTokens(player, 5_000);
                case 2 -> plugin.getEconomyManager().addCoins(player, 10_000);
                case 3 -> player.getInventory().addItem(plugin.getEnchantmentManager().createKey("Peu comune"));
                case 4 -> plugin.getEconomyManager().addExperience(player, 5_000);
                case 5 -> plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).addBeacons(25);
                case 6 -> player.getInventory().addItem(plugin.getEnchantmentManager().createKey("Peu comune"));
                case 7 -> plugin.getEconomyManager().addTokens(player, 15_000);
                case 8 -> plugin.getEconomyManager().addCoins(player, 25_000);
                case 9 -> plugin.getCristalManager().giveCristalToPlayer(player, 10);
                case 10 -> player.getInventory().addItem(plugin.getEnchantmentManager().createKey("Rare"));
                case 11 -> plugin.getEconomyManager().addExperience(player, 15_000);
                case 12 -> plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).addBeacons(100);
                case 13 -> player.getInventory().addItem(plugin.getEnchantmentManager().createKey("Légendaire"));
                case 14 -> plugin.getEconomyManager().addTokens(player, 50_000);
                case 15 -> {
                    plugin.getCristalManager().giveCristalToPlayer(player, 16);
                    player.getInventory().addItem(plugin.getEnchantmentManager().createKey("Cristal"));
                }
                default -> {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            plugin.getPluginLogger().warning("§eErreur lors de l'attribution de la récompense jour " + day + ": " + e.getMessage());
            return false;
        }
    }
}


