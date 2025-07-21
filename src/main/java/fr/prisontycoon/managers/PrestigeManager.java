package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.prestige.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Gestionnaire principal du système de prestige
 */
public class PrestigeManager {

    private final PrisonTycoon plugin;
    private final PrestigeRewardManager rewardManager;

    public PrestigeManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.rewardManager = new PrestigeRewardManager(plugin);
    }

    /**
     * Vérifie si un joueur peut effectuer un prestige
     */
    public boolean canPrestige(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier le rang FREE
        if (!playerData.hasCustomPermission("specialmine.free")) {
            return false;
        }

        // Vérifier le niveau de prestige maximum
        if (playerData.getPrestigeLevel() >= 50) {
            return false;
        }

        // TODO: Vérifier pas d'épargne active en banque
        // TODO: Vérifier pas d'investissement actif
        // TODO: Vérifier ne pas être en challenge

        return true;
    }

    /**
     * Effectue le prestige d'un joueur
     */
    public boolean performPrestige(Player player) {
        if (!canPrestige(player)) {
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int newPrestigeLevel = playerData.getPrestigeLevel() + 1;

        // Confirmation du prestige
        if (!confirmPrestige(player, newPrestigeLevel)) {
            return false;
        }

        // Sauvegarder l'ancien rang pour les messages
        String oldRank = plugin.getMineManager().getCurrentRank(player);

        // Effectuer le reset
        resetPlayerForPrestige(player);

        // Mettre à jour le niveau de prestige
        playerData.setPrestigeLevel(newPrestigeLevel);

        // Ajouter la permission de prestige
        String prestigePermission = "specialmine.prestige." + newPrestigeLevel;
        playerData.addPermission(prestigePermission);

        // Retirer l'ancienne permission de prestige si elle existe
        if (newPrestigeLevel > 1) {
            String oldPrestigePermission = "specialmine.prestige." + (newPrestigeLevel - 1);
            playerData.removePermission(oldPrestigePermission);
        }

        // Donner les récompenses automatiques
        rewardManager.giveAutomaticRewards(player, newPrestigeLevel);

        // Gérer les talents ou récompenses spéciales
        handlePrestigeRewardOrTalent(player, newPrestigeLevel);

        // Messages et effets
        sendPrestigeMessages(player, newPrestigeLevel);
        broadcastPrestige(player, newPrestigeLevel);

        // Effets sonores et visuels
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        plugin.getPluginLogger().info("Prestige effectué: " + player.getName() + " -> P" + newPrestigeLevel);

        return true;
    }

    /**
     * Demande confirmation au joueur pour le prestige
     */
    private boolean confirmPrestige(Player player, int newLevel) {
        // TODO: Implémenter une interface de confirmation
        // Pour l'instant, on assume que la confirmation a été faite via l'interface
        return true;
    }

    /**
     * Reset le joueur pour le prestige
     */
    private void resetPlayerForPrestige(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Retirer toutes les permissions de mine
        clearAllMinePermissions(player);

        // Remettre la permission de base (rang A)
        playerData.addPermission("specialmine.mine.a");

        playerData.setCoins(0);

        plugin.getPluginLogger().info("Reset de prestige effectué pour: " + player.getName());
    }

    /**
     * Retire toutes les permissions de mine d'un joueur
     */
    private void clearAllMinePermissions(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Retirer les permissions de mine a-z
        for (char c = 'a'; c <= 'z'; c++) {
            String minePermission = "specialmine.mine." + c;
            playerData.removePermission(minePermission);
        }

        // Retirer la permission FREE
        playerData.removePermission("specialmine.free");
    }

    /**
     * Gère les talents ou récompenses spéciales selon le niveau de prestige
     */
    private void handlePrestigeRewardOrTalent(Player player, int prestigeLevel) {
        if (prestigeLevel % 5 == 0) {
            // Paliers spéciaux (P5, P10, P15, etc.) - Récompenses spéciales
            List<PrestigeReward> specialRewards = PrestigeReward.SpecialRewards.getSpecialRewardsForPrestige(prestigeLevel);

            if (specialRewards.size() == 1) {
                // Récompense unique (titres)
                rewardManager.giveSpecialReward(player, specialRewards.get(0));
            } else if (specialRewards.size() > 1) {
                // Choix entre plusieurs récompenses
                // TODO: Ouvrir interface de choix
                player.sendMessage("§e🎁 Vous avez débloqué des récompenses spéciales!");
                player.sendMessage("§7Utilisez §6/prestige récompenses §7pour choisir votre récompense P" + prestigeLevel);
            }
        } else {
            // Talents cycliques
            PrestigeTalent availableTalent = PrestigeTalent.getTalentForPrestige(prestigeLevel);
            if (availableTalent != null) {
                // Ajouter automatiquement le talent (ou permettre le choix plus tard)
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                playerData.addPrestigeTalent(availableTalent);

                player.sendMessage("§a✨ Talent débloqué: §6" + availableTalent.getDisplayName());
                player.sendMessage("§7" + availableTalent.getDescription().replace("\n", " §7"));
            }
        }
    }

    /**
     * Envoie les messages de prestige au joueur
     */
    private void sendPrestigeMessages(Player player, int prestigeLevel) {
        player.sendMessage("");
        player.sendMessage("§6§l╔══════════════════════════════════════╗");
        player.sendMessage("§6§l║           §e✨ PRESTIGE RÉUSSI! ✨           §6§l║");
        player.sendMessage("§6§l╠══════════════════════════════════════╣");
        player.sendMessage("§6§l║  §fVous êtes maintenant §6§lPRESTIGE " + prestigeLevel + "§f!     §6§l║");
        player.sendMessage("§6§l║  §7Retour au rang §fA §7avec des bonus     §6§l║");
        player.sendMessage("§6§l║  §7permanents et des mines exclusives!    §6§l║");
        player.sendMessage("§6§l╚══════════════════════════════════════╝");
        player.sendMessage("");

        // Informations sur les mines prestige débloquées
        List<String> unlockedMines = getUnlockedPrestigeMines(prestigeLevel);
        if (!unlockedMines.isEmpty()) {
            player.sendMessage("§a🏔️ Mines Prestige débloquées:");
            for (String mine : unlockedMines) {
                player.sendMessage("§7• §6" + mine);
            }
        }
    }

    /**
     * Diffuse le prestige dans le chat global
     */
    private void broadcastPrestige(Player player, int prestigeLevel) {
        String message = ChatColor.GOLD + "🏆 " + ChatColor.YELLOW + player.getName() +
                ChatColor.WHITE + " a atteint le " +
                ChatColor.GOLD + ChatColor.BOLD + "PRESTIGE " + prestigeLevel + ChatColor.WHITE + "! " +
                ChatColor.GRAY + "Félicitations!";

        Bukkit.broadcastMessage(message);

        // Son pour tous les joueurs en ligne
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
        }
    }

    /**
     * Obtient les mines prestige débloquées pour un niveau donné
     */
    private List<String> getUnlockedPrestigeMines(int prestigeLevel) {
        List<String> mines = new ArrayList<>();

        if (prestigeLevel >= 1) mines.add("Mine Prestige I (0.1% beacons)");
        if (prestigeLevel >= 11) mines.add("Mine Prestige XI (0.5% beacons)");
        if (prestigeLevel >= 21) mines.add("Mine Prestige XXI (1% beacons)");
        if (prestigeLevel >= 31) mines.add("Mine Prestige XXXI (3% beacons)");
        if (prestigeLevel >= 41) mines.add("Mine Prestige XLI (5% beacons)");

        return mines;
    }

    /**
     * Vérifie si un joueur peut accéder à une mine prestige
     */
    public boolean canAccessPrestigeMine(Player player, String mineName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        // Le joueur doit aussi avoir atteint le rang Z pour accéder aux mines prestige
        String currentRank = plugin.getMineManager().getCurrentRank(player);
        if (!currentRank.equals("z") && !playerData.hasCustomPermission("specialmine.free")) {
            return false;
        }

        return switch (mineName.toLowerCase()) {
            case "prestige1", "prestige_i" -> prestigeLevel >= 1;
            case "prestige11", "prestige_xi" -> prestigeLevel >= 11;
            case "prestige21", "prestige_xxi" -> prestigeLevel >= 21;
            case "prestige31", "prestige_xxxi" -> prestigeLevel >= 31;
            case "prestige41", "prestige_xli" -> prestigeLevel >= 41;
            default -> false;
        };
    }

    /**
     * Obtient les informations de prestige d'un joueur
     */
    public String getPrestigeInfo(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        StringBuilder info = new StringBuilder();
        info.append("§6🏆 Informations Prestige:\n");
        info.append("§7Niveau actuel: §6").append(prestigeLevel > 0 ? "P" + prestigeLevel : "Aucun").append("\n");

        if (canPrestige(player)) {
            info.append("§a✅ Vous pouvez effectuer un prestige!\n");
            info.append("§7Prochain niveau: §6P").append(prestigeLevel + 1).append("\n");
        } else if (prestigeLevel >= 50) {
            info.append("§e⭐ Niveau maximum atteint!\n");
        } else {
            info.append("§c❌ Conditions non remplies pour le prestige\n");
            info.append("§7Requis: §fRang FREE\n");
        }

        // Talents actifs
        Map<PrestigeTalent, Integer> talents = playerData.getPrestigeTalents();
        if (!talents.isEmpty()) {
            info.append("§e⚡ Talents actifs:\n");
            for (Map.Entry<PrestigeTalent, Integer> entry : talents.entrySet()) {
                info.append("§7• §6").append(entry.getKey().getDisplayName())
                        .append(" §7(Niveau ").append(entry.getValue()).append(")\n");
            }
        }

        return info.toString();
    }

    public PrestigeRewardManager getRewardManager() {
        return rewardManager;
    }
}