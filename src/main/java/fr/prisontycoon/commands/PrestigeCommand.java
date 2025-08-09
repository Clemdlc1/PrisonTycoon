package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.gui.PrestigeGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commande principale du système de prestige
 * Usage: /prestige [info|récompenses|talents|effectuer]
 */
public class PrestigeCommand implements CommandExecutor, TabCompleter {

    private static final long RESET_CONFIRMATION_TIMEOUT = 30000; // 30 secondes
    private final PrisonTycoon plugin;
    private final PrestigeGUI prestigeGUI;
    private final Map<UUID, Long> pendingResetConfirmations = new ConcurrentHashMap<>();

    public PrestigeCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.prestigeGUI = new PrestigeGUI(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Commande sans arguments = menu principal
        if (args.length == 0) {
            prestigeGUI.openMainPrestigeMenu(player);
            return true;
        }

        // Sous-commandes
        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "info", "informations" -> handleInfoCommand(player);
            case "progression", "progress" -> handleProgressionCommand(player);
            case "effectuer", "faire", "perform" -> handlePerformCommand(player);
            case "confirmer", "confirm" -> handleConfirmCommand(player);
            case "confirmer-reset", "confirm-reset", "confirmreset" -> handleConfirmResetCommand(player); // NOUVEAU
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * Gère la commande /prestige info
     */
    private void handleInfoCommand(Player player) {
        String info = plugin.getPrestigeManager().showPrestigeInfo(player);
        player.sendMessage(info);
    }

    /**
     * Gère la commande /prestige confirmer-reset
     */
    private void handleConfirmResetCommand(Player player) {
        UUID playerId = player.getUniqueId();
        Long confirmationTime = pendingResetConfirmations.get(playerId);

        // Vérifier si une confirmation est en attente
        if (confirmationTime == null) {
            player.sendMessage("§c❌ Aucune confirmation de reset en attente!");
            player.sendMessage("§7Utilisez d'abord le bouton de réinitialisation dans le menu.");
            return;
        }

        // Vérifier si le délai est écoulé
        long currentTime = System.currentTimeMillis();
        if (currentTime - confirmationTime > RESET_CONFIRMATION_TIMEOUT) {
            pendingResetConfirmations.remove(playerId);
            player.sendMessage("§c❌ Délai de confirmation écoulé!");
            player.sendMessage("§7Veuillez réinitier la réinitialisation depuis le menu.");
            return;
        }

        // Supprimer la confirmation en attente
        pendingResetConfirmations.remove(playerId);

        // Procéder à la confirmation
        prestigeGUI.confirmTalentReset(player);
    }

    public void addPendingResetConfirmation(UUID playerId, long timestamp) {
        pendingResetConfirmations.put(playerId, timestamp);
    }

    public boolean removePendingResetConfirmation(UUID playerId, long timestamp) {
        Long existingTime = pendingResetConfirmations.get(playerId);
        if (existingTime != null && existingTime.equals(timestamp)) {
            pendingResetConfirmations.remove(playerId);
            return true;
        }
        return false;
    }

    /**
     * Gère la commande /prestige récompenses
     */
    private void handleProgressionCommand(Player player) {
        prestigeGUI.openMainPrestigeMenu(player);
    }

    /**
     * Gère la commande /prestige effectuer
     */
    private void handlePerformCommand(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (!plugin.getPrestigeManager().canPrestige(player)) {
            player.sendMessage("§c❌ Vous ne pouvez pas effectuer de prestige!");
            player.sendMessage("§7Conditions requises:");
            player.sendMessage("§7• Rang FREE");
            player.sendMessage("§7• Pas d'épargne active en banque");
            player.sendMessage("§7• Pas d'investissement actif");
            player.sendMessage("§7• Ne pas être en challenge");
            return;
        }

        int nextPrestige = playerData.getPrestigeLevel() + 1;

        // Afficher les informations de confirmation
        player.sendMessage("§6⚠ §e§lCONFIRMATION DE PRESTIGE");
        player.sendMessage("");
        player.sendMessage("§7Vous allez effectuer le §6§lPRESTIGE " + nextPrestige + "§7:");
        player.sendMessage("§c• §7Retour au rang §fA");
        player.sendMessage("§c• §7Remise à 0 des coins");
        player.sendMessage("§a• §7Bonus et talents permanents");
        player.sendMessage("§a• §7Accès aux mines prestige");
        player.sendMessage("§a• §7Récompenses exclusives");
        player.sendMessage("");
        player.sendMessage("§7Tapez §a/prestige confirmer §7pour confirmer");
        player.sendMessage("§7ou attendez 30 secondes pour annuler.");

        // TODO: Implémenter un système de confirmation temporisé
    }

    /**
     * Gère la commande /prestige confirmer
     */
    private void handleConfirmCommand(Player player) {
        // TODO: Vérifier si une confirmation est en attente

        boolean success = plugin.getPrestigeManager().performPrestige(player);
        if (!success) {
            player.sendMessage("§c❌ Impossible d'effectuer le prestige!");
        }
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§6§l╔═══════════════════════════════════╗");
        player.sendMessage("§6§l║           §e🏆 PRESTIGE 🏆           §6§l║");
        player.sendMessage("§6§l╠═══════════════════════════════════╣");
        player.sendMessage("§6§l║ §e/prestige                       §6§l║");
        player.sendMessage("§6§l║ §7├─ Ouvre le menu principal       §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/prestige info                  §6§l║");
        player.sendMessage("§6§l║ §7├─ Informations de prestige      §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/prestige progression           §6§l║");
        player.sendMessage("§6§l║ §7├─ Menu talents & récompenses    §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/prestige effectuer             §6§l║");
        player.sendMessage("§6§l║ §7├─ Effectuer un prestige         §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §c/prestige confirmer-reset       §6§l║"); // NOUVEAU
        player.sendMessage("§6§l║ §7├─ Confirmer reset des talents   §6§l║"); // NOUVEAU
        player.sendMessage("§6§l╚═══════════════════════════════════╝");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int prestigeLevel = playerData.getPrestigeLevel();

        player.sendMessage("");
        player.sendMessage("§7Votre prestige actuel: " + playerData.getPrestigeDisplayName());

        if (plugin.getPrestigeManager().canPrestige(player)) {
            player.sendMessage("§a✅ Vous pouvez effectuer un prestige!");
        } else if (prestigeLevel >= 50) {
            player.sendMessage("§e⭐ Niveau maximum atteint!");
        } else {
            player.sendMessage("§c❌ Conditions de prestige non remplies");
            player.sendMessage("§7Tapez §e/prestige info §7pour voir les prérequis");
        }

        // Ajout d'informations sur les talents si ils peuvent être reset
        if (!playerData.getPrestigeTalents().isEmpty()) {
            player.sendMessage("");
            player.sendMessage("§7💡 Vous avez des talents de prestige actifs");
            player.sendMessage("§7Vous pouvez les réinitialiser pour 500 beacons");
        }
    }

    // Modifier la méthode getTabCompletions pour inclure la nouvelle commande :
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> commands = Arrays.asList("info", "progression", "effectuer",
                    "confirmer", "confirmer-reset", "help"); // AJOUT de "confirmer-reset"
            StringUtil.copyPartialMatches(args[0], commands, completions);
            Collections.sort(completions);
            return completions;
        }

        return Collections.emptyList();
    }
}