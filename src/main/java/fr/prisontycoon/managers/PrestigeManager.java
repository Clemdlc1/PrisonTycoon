package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.data.BankType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import fr.prisontycoon.utils.NumberFormatter;

/**
 * Gestionnaire principal du système de prestige (CORRIGÉ: sans rang FREE)
 */
public class PrestigeManager {

    private final PrisonTycoon plugin;
    private final PrestigeRewardManager rewardManager;

    public PrestigeManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.rewardManager = new PrestigeRewardManager(plugin);
    }

    /**
     * Calcule le coût de prestige en coins pour un niveau donné
     */
    public long getPrestigeCost(Player player, int prestigeLevel) {
        // Coût de base: 1M coins pour P1, puis +500k par niveau
        long baseCost = 1_000_000L + ((prestigeLevel - 1) * 500_000L);
        
        // Appliquer le multiplicateur du type de banque
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        double bankMultiplier = playerData.getBankType().getPrestigeCostMultiplier();
        
        return (long) (baseCost * bankMultiplier);
    }

    /**
     * Vérifie si un joueur a assez de coins pour le prestige
     */
    public boolean hasEnoughCoinsForPrestige(Player player, int prestigeLevel) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long requiredCoins = getPrestigeCost(player, prestigeLevel);
        return playerData.getCoins() >= requiredCoins;
    }

    /**
     * Déduit le coût de prestige des coins du joueur
     */
    public boolean deductPrestigeCost(Player player, int prestigeLevel) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long cost = getPrestigeCost(player, prestigeLevel);

        if (!hasEnoughCoinsForPrestige(player, prestigeLevel)) {
            return false;
        }
    
        playerData.removeCoins(cost);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
        return true;
    }

    /**
     * CORRIGÉ: Vérifie si un joueur peut effectuer un prestige (rang Z requis au lieu de FREE)
     */
    public boolean canPrestige(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Vérifier le rang Z (maximum) au lieu du rang FREE
        if (!playerData.hasCustomPermission("specialmine.mine.z")) {
            return false;
        }

        // Vérifier le niveau de prestige maximum
        if (playerData.getPrestigeLevel() >= 50) {
            return false;
        }

        // Vérifier si le joueur a assez de coins
        int nextPrestigeLevel = playerData.getPrestigeLevel() + 1;
        if (!hasEnoughCoinsForPrestige(player, nextPrestigeLevel)) {
            return false;
        }

        // Vérifier pas d'épargne active en banque
        if (playerData.getSavingsBalance() > 0) {
            player.sendMessage("§c❌ Vous ne pouvez pas faire de prestige avec de l'épargne active !");
            player.sendMessage("§7Retirez d'abord votre épargne via §e/bank withdraw§7.");
            return false;
        }
        
        // Vérifier pas d'investissement actif
        Map<Material, Long> investments = playerData.getAllInvestments();
        if (!investments.isEmpty()) {
            player.sendMessage("§c❌ Vous ne pouvez pas faire de prestige avec des investissements actifs !");
            player.sendMessage("§7Vendez d'abord vos investissements via §e/bank invest§7.");
            return false;
        }
        
        return true;
    }

    /**
     * CORRIGÉ: Effectue le prestige d'un joueur (reset vers rang A avec permission prestige)
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

        // Déduire le coût de prestige
        if (!deductPrestigeCost(player, newPrestigeLevel)) {
            player.sendMessage("§c❌ Erreur: Impossible de déduire le coût de prestige!");
            return false;
        }

        // Sauvegarder l'ancien rang pour les messages
        String oldRank = getCurrentRank(player);

        // Effectuer le reset
        resetPlayerForPrestige(player);

        // Mettre à jour le niveau de prestige via PlayerData (qui gère les permissions)
        playerData.setPrestigeLevel(newPrestigeLevel);

        // Appliquer immédiatement la permission si le joueur est en ligne
        Player onlinePlayer = plugin.getServer().getPlayer(player.getUniqueId());
        if (onlinePlayer != null && onlinePlayer.isOnline()) {
            String prestigePermission = "specialmine.prestige." + newPrestigeLevel;
            plugin.getPermissionManager().attachPermission(onlinePlayer, prestigePermission);
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
     * CORRIGÉ: Obtient le rang actuel du joueur via PermissionManager
     */
    private String getCurrentRank(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String highestRank = "a"; // Rang par défaut

        // Recherche du rang le plus élevé via les permissions bukkit
        for (char c = 'z'; c >= 'a'; c--) {
            String minePermission = "specialmine.mine." + c;
            if (playerData.hasCustomPermission(minePermission)) {
                highestRank = String.valueOf(c);
                break;
            }
        }

        return highestRank;
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
     * CORRIGÉ: Reset le joueur pour le prestige (retour au rang A via PermissionManager)
     */
    private void resetPlayerForPrestige(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Retirer toutes les permissions de mine via PermissionManager
        plugin.getRankupCommand().setMinePermissionToRank(player, "a");

        // Reset des coins
        playerData.setCoins(0);

        plugin.getPluginLogger().info("Reset de prestige effectué pour: " + player.getName() + " (retour au rang A)");
    }

    /**
     * Gère les talents ou récompenses spéciales selon le niveau de prestige
     */
    private void handlePrestigeRewardOrTalent(Player player, int prestigeLevel) {
        if (prestigeLevel % 5 == 0) {
            // Paliers spéciaux (P5, P10, P15, etc.)
            player.sendMessage("§6🎁 Palier spécial P" + prestigeLevel + " atteint!");
            player.sendMessage("§7Consultez /prestige talents pour découvrir vos nouveaux bonus!");
        }

        // Récompenses automatiques selon le niveau
        switch (prestigeLevel) {
            case 1 -> player.sendMessage("§a🎉 Premier prestige! Bonus de vitesse permanente débloqué!");
            case 5 -> player.sendMessage("§b🎁 P5 atteint! Bonus d'efficacité de minage débloqué!");
            case 10 -> player.sendMessage("§d🏆 P10 atteint! Accès aux mines de prestige débloqué!");
            case 25 -> player.sendMessage("§6👑 P25 atteint! Bonus de multiplicateur de coins débloqué!");
            case 50 -> player.sendMessage("§c🌟 P50 atteint! Rang LÉGENDE débloqué! Félicitations!");
        }
    }

    /**
     * Envoie les messages de prestige au joueur
     */
    private void sendPrestigeMessages(Player player, int prestigeLevel) {
        long costPaid = getPrestigeCost(player, prestigeLevel);
        player.sendMessage("§6════════════════════════════════");
        player.sendMessage("§6🏆        PRESTIGE RÉUSSI!        🏆");
        player.sendMessage("§6════════════════════════════════");
        player.sendMessage("§7Nouveau niveau de prestige: §6§lP" + prestigeLevel);
        player.sendMessage("§7Vous avez été reset au rang §eA §7avec des bonus permanents!");
        player.sendMessage("§7Coût payé: §c" + NumberFormatter.format(costPaid) + " coins");
        player.sendMessage("§7Tapez §e/prestige info §7pour voir vos avantages!");
        player.sendMessage("§6════════════════════════════════");
    }

    /**
     * Annonce le prestige à tous les joueurs
     */
    private void broadcastPrestige(Player player, int prestigeLevel) {
        String message = "§6🏆 " + player.getName() + " a atteint le Prestige " + prestigeLevel + "! 🏆";

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            onlinePlayer.sendMessage(message);
            onlinePlayer.playSound(onlinePlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.0f);
        }
    }

    /**
     * Vérifie si un joueur a un niveau de prestige spécifique
     */
    public boolean hasPrestigeLevel(Player player, int level) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.getPrestigeLevel() >= level;
    }

    /**
     * Obtient le niveau de prestige d'un joueur
     */
    public int getPrestigeLevel(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.getPrestigeLevel();
    }

    /**
     * Obtient le gestionnaire de récompenses de prestige
     */
    public PrestigeRewardManager getRewardManager() {
        return rewardManager;
    }

    /**
     * NOUVEAU: Vérifie si un joueur peut accéder à une mine de prestige
     */
    public boolean canAccessPrestigeMine(Player player, int requiredPrestigeLevel) {
        return hasPrestigeLevel(player, requiredPrestigeLevel) && getCurrentRank(player).equals("z");
    }

    /**
     * NOUVEAU: Obtient la liste des mines de prestige accessibles
     */
    public List<String> getAccessiblePrestigeMines(Player player) {
        List<String> accessibleMines = new ArrayList<>();
        int playerPrestigeLevel = getPrestigeLevel(player);
        String currentRank = getCurrentRank(player);

        // Vérifie que le joueur est au rang Z
        if (!currentRank.equals("z")) {
            return accessibleMines; // Liste vide si pas rang Z
        }

        // Ajoute les mines de prestige selon le niveau
        if (playerPrestigeLevel >= 1) {
            accessibleMines.add("mine-prestige1");
        }
        if (playerPrestigeLevel >= 11) {
            accessibleMines.add("mine-prestige11");
        }
        if (playerPrestigeLevel >= 21) {
            accessibleMines.add("mine-prestige21");
        }
        if (playerPrestigeLevel >= 31) {
            accessibleMines.add("mine-prestige31");
        }
        if (playerPrestigeLevel >= 41) {
            accessibleMines.add("mine-prestige41");
        }

        return accessibleMines;
    }

    /**
     * NOUVEAU: Affiche les informations de prestige du joueur
     */
    public String showPrestigeInfo(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();
        String currentRank = getCurrentRank(player);

        player.sendMessage("§6═══════════ PRESTIGE INFO ═══════════");
        player.sendMessage("§7Niveau de prestige: §6P" + prestigeLevel);
        player.sendMessage("§7Rang actuel: §e" + currentRank.toUpperCase());

        if (canPrestige(player)) {
            player.sendMessage("§a✅ Vous pouvez effectuer un prestige!");
            int nextLevel = prestigeLevel + 1;
            long cost = getPrestigeCost(player, nextLevel);
            player.sendMessage("§7Coût pour P" + nextLevel + ": §c" + NumberFormatter.format(cost) + " coins");
            player.sendMessage("§7Tapez §e/prestige effectuer §7pour continuer.");
        } else if (!currentRank.equals("z")) {
            player.sendMessage("§c❌ Vous devez atteindre le rang Z pour prestigier.");
        } else if (prestigeLevel >= 50) {
            player.sendMessage("§6👑 Prestige maximum atteint! Rang LÉGENDE!");
        } else {
            // Afficher pourquoi le prestige n'est pas possible
            int nextLevel = prestigeLevel + 1;
            long cost = getPrestigeCost(player, nextLevel);
            long currentCoins = playerData.getCoins();
            player.sendMessage("§c❌ Coût requis pour P" + nextLevel + ": §c" + NumberFormatter.format(cost) + " coins");
            player.sendMessage("§7Vos coins: §e" + NumberFormatter.format(currentCoins) + " coins");
            if (currentCoins < cost) {
                long missing = cost - currentCoins;
                player.sendMessage("§c❌ Il vous manque: §c" + NumberFormatter.format(missing) + " coins");
            }
        }

        // Affiche les mines de prestige accessibles
        List<String> accessibleMines = getAccessiblePrestigeMines(player);
        if (!accessibleMines.isEmpty()) {
            player.sendMessage("§dMines de prestige accessibles:");
            for (String mine : accessibleMines) {
                player.sendMessage("§7- §d" + mine);
            }
        }

        player.sendMessage("§6═══════════════════════════════════");
        return currentRank;
    }
}