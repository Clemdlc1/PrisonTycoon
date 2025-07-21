package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Commande /rankup - Système de montée en rang pour les mines A-Z (CORRIGÉ: sans rang FREE)
 */
public class RankupCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public RankupCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        if (!player.hasPermission("specialmine.rankup")) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length == 0) {
            performSingleRankup(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "all", "max" -> performMaxRankup(player);
            case "auto" -> {
                if (!player.hasPermission("specialmine.vip") && !player.hasPermission("specialmine.admin")) {
                    player.sendMessage("§cCette fonctionnalité est réservée aux VIP!");
                    return true;
                }
                toggleAutoRankup(player);
            }
            case "info" -> showRankupInfo(player);
            case "force" -> {
                if (!player.hasPermission("specialmine.admin")) {
                    player.sendMessage("§cVous n'avez pas la permission!");
                    return true;
                }
                if (args.length != 3) {
                    player.sendMessage("§cUsage: /rankup force <joueur> <a-z>");
                    return true;
                }
                forceRankPlayer(player, args[1], args[2]);
            }
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * CORRIGÉ: Tente d'effectuer un seul rankup sans rang FREE
     */
    private boolean tryRankup(Player player, boolean silent) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentRank = getCurrentRank(player);
        String nextRank = getNextRank(currentRank);

        if (nextRank == null) {
            if (!silent) player.sendMessage("§cVous êtes déjà au rang maximum (Z)! Utilisez /prestige pour continuer.");
            return false;
        }

        long price = getRankupPrice(nextRank);
        if (price < 0) {
            if (!silent)
                player.sendMessage("§cErreur: Prix de rankup non configuré pour le rang " + nextRank.toUpperCase() + "!");
            return false;
        }

        if (playerData.getCoins() < price) {
            if (!silent) {
                player.sendMessage("§cCoins insuffisants!");
                player.sendMessage("§7Nécessaire: §c" + NumberFormatter.format(price) + " coins");
                player.sendMessage("§7Actuel: §e" + NumberFormatter.format(playerData.getCoins()) + " coins");
                player.sendMessage("§7Manquant: §c" + NumberFormatter.format(price - playerData.getCoins()) + " coins");
            }
            return false;
        }

        // Effectuer le rankup
        playerData.removeCoins(price);

        // Effacer toutes les anciennes permissions de mine
        clearAllMinePermissions(player);

        // Ajouter toutes les permissions de mine jusqu'au nouveau rang (système cumulatif)
        addMinePermissionsUpToRank(player, nextRank);

        if (!silent) {
            player.sendMessage("§a✅ Rankup réussi!");
            player.sendMessage("§7Nouveau rang: §a" + nextRank.toUpperCase());
            player.sendMessage("§7Coût: §c-" + NumberFormatter.format(price) + " coins");
            player.sendMessage("§7Coins restants: §e" + NumberFormatter.format(playerData.getCoins()));
            player.sendMessage("§7Vous pouvez maintenant miner dans les mines A à " + nextRank.toUpperCase() + "!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            // Notification spéciale pour le rang F (débloque les métiers)
            if (nextRank.equals("f")) {
                plugin.getProfessionManager().notifyProfessionUnlock(player);
            }

            // Notification spéciale pour le rang Z (permet prestige)
            if (nextRank.equals("z")) {
                player.sendMessage("§6🏆 Félicitations! Vous avez atteint le rang maximum!");
                player.sendMessage("§6✨ Vous pouvez maintenant effectuer un /prestige pour obtenir des bonus permanents!");
            }
        }

        plugin.getPluginLogger().info("Rankup effectué: " + player.getName() + " " +
                currentRank.toUpperCase() + " → " + nextRank.toUpperCase() +
                " (coût: " + NumberFormatter.format(price) + " coins)");

        return true;
    }

    /**
     * CORRIGÉ: Obtient le rang actuel via PermissionManager
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

        // Fallback vers l'ancienne logique si nécessaire
        if (highestRank.equals("a")) {
            String oldPermission = playerData.getHighestMinePermission();
            if (oldPermission != null && oldPermission.startsWith("mine-")) {
                highestRank = oldPermission.substring(5);
            }
        }

        return highestRank;
    }

    /**
     * CORRIGÉ: Obtient le rang suivant (A-Z seulement, plus de FREE)
     */
    private String getNextRank(String currentRank) {
        if (currentRank == null || currentRank.isEmpty()) {
            return "a";
        }

        // Si on est au rang Z, c'est le maximum
        if (currentRank.equalsIgnoreCase("z")) {
            return null;
        }

        // Progression normale a->b->c->...->z
        char currentChar = currentRank.charAt(0);
        if (currentChar >= 'z') {
            return null; // Rang maximum atteint
        }
        return String.valueOf((char) (currentChar + 1));
    }

    /**
     * CORRIGÉ: Retire toutes les permissions de mine (plus de FREE)
     */
    private void clearAllMinePermissions(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Retirer toutes les permissions de mine a-z via PermissionManager
        for (char c = 'a'; c <= 'z'; c++) {
            String minePermission = "specialmine.mine." + c;
            if (playerData.hasCustomPermission(minePermission)) {
                plugin.getPlayerDataManager().removePermissionFromPlayer(player.getUniqueId(), minePermission);
            }
        }

        // Ancienne logique pour compatibilité
        playerData.clearMinePermissions();
    }

    /**
     * CORRIGÉ: Ajoute toutes les permissions de mine jusqu'au rang spécifié (système cumulatif, plus de FREE)
     */
    private void addMinePermissionsUpToRank(Player player, String targetRank) {
        // Validation du rang
        if (targetRank == null || targetRank.length() != 1) {
            plugin.getPluginLogger().warning("Rang invalide: " + targetRank);
            return;
        }

        char targetChar = targetRank.charAt(0);
        if (targetChar < 'a' || targetChar > 'z') {
            plugin.getPluginLogger().warning("Rang invalide: " + targetRank);
            return;
        }

        // Ajouter toutes les permissions de A jusqu'au rang cible via PermissionManager
        for (char c = 'a'; c <= targetChar; c++) {
            String minePermission = "specialmine.mine." + c;
            plugin.getPlayerDataManager().addPermissionToPlayer(player.getUniqueId(), minePermission);
        }

        plugin.getPluginLogger().info("Permissions de mine ajoutées pour " + player.getName() + ": A-" + targetRank.toUpperCase());
    }

    /**
     * CORRIGÉ: Obtient le prix de rankup depuis la configuration (plus de FREE)
     */
    private long getRankupPrice(String targetRank) {
        if (targetRank == null) return -1;

        // Prix normal pour les rangs a-z
        String mineName = "mine-" + targetRank.toLowerCase();
        return plugin.getConfig().getLong("mines." + mineName + ".rankup-price", -1);
    }

    /**
     * Lance un seul rankup et affiche les messages.
     */
    private void performSingleRankup(Player player) {
        tryRankup(player, false);
    }

    /**
     * Effectue le maximum de rankups possibles en appelant tryRankup en boucle.
     */
    private void performMaxRankup(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String originalRank = getCurrentRank(player);
        String finalRank = originalRank;
        long totalCost = 0;
        int rankupsCount = 0;

        // Tente de rankup en boucle jusqu'à ce que ça échoue (argent ou rang max)
        while (tryRankup(player, true)) {
            rankupsCount++;
        }

        // Met à jour le rang final et le coût total
        finalRank = getCurrentRank(player);

        // Recalculer le coût total
        long cost = 0;
        String tempRank = originalRank;
        for (int i = 0; i < rankupsCount; i++) {
            String next = getNextRank(tempRank);
            if (next == null) break;
            cost += getRankupPrice(next);
            tempRank = next;
        }
        totalCost = cost;

        if (rankupsCount == 0) {
            // Si aucun rankup n'a eu lieu, on affiche le message d'erreur du premier échec.
            tryRankup(player, false);
        } else {
            // Messages de succès pour /rankup all
            player.sendMessage("§a✅ Rankup All réussi!");
            player.sendMessage("§7Progressé de §e" + originalRank.toUpperCase() + " §7à §a" + finalRank.toUpperCase());
            player.sendMessage("§7Nombre de rankups: §e" + rankupsCount);
            player.sendMessage("§7Coût total: §c-" + NumberFormatter.format(totalCost) + " coins");
            player.sendMessage("§7Coins restants: §e" + NumberFormatter.format(playerData.getCoins()));
            player.sendMessage("§7Vous pouvez maintenant miner dans les mines A à " + finalRank.toUpperCase() + "!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            if (finalRank.equals("f")) {
                plugin.getProfessionManager().notifyProfessionUnlock(player);
            }

            // Notification spéciale pour le rang Z
            if (finalRank.equals("z")) {
                player.sendMessage("§6🏆 Félicitations! Vous avez atteint le rang maximum!");
                player.sendMessage("§6✨ Vous pouvez maintenant effectuer un /prestige pour obtenir des bonus permanents!");
            }

            plugin.getPluginLogger().info("Rankup All effectué: " + player.getName() + " " +
                    originalRank.toUpperCase() + " → " + finalRank.toUpperCase() +
                    " (" + rankupsCount + " rankups, coût: " + totalCost + " coins)");
        }
    }

    /**
     * Effectue l'auto-rankup pour un joueur.
     */
    public void performAutoRankup(Player player) {
        String originalRank = getCurrentRank(player);
        int rankupsCount = 0;

        // Tente de rankup en boucle silencieusement
        while (tryRankup(player, true)) {
            rankupsCount++;
        }

        if (rankupsCount > 0) {
            String finalRank = getCurrentRank(player);
            // Notification discrète
            player.sendMessage("§a🔄 Auto-rankup: §e" + originalRank.toUpperCase() + " §7→ §a" +
                    finalRank.toUpperCase() + " §7(" + rankupsCount + " niveau" +
                    (rankupsCount > 1 ? "x" : "") + ")");

            plugin.getPluginLogger().info("Auto-rankup effectué pour " + player.getName() + ": " + rankupsCount + " niveau(x).");
        }
    }

    /**
     * Vérifie si un joueur peut effectuer un auto-rankup
     */
    public boolean canAutoRankup(Player player) {
        if (!player.hasPermission("specialmine.vip") && !player.hasPermission("specialmine.admin")) {
            return false;
        }
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return playerData.hasAutoRankup();
    }

    /**
     * Active/désactive l'auto-rankup pour un joueur.
     */
    private void toggleAutoRankup(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean currentState = playerData.hasAutoRankup();

        playerData.setAutoRankup(!currentState);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        if (currentState) {
            player.sendMessage("§c❌ Auto-rankup désactivé");
            player.sendMessage("§7Vous devrez utiliser §e/rankup §7ou §e/rankup all §7manuellement.");
        } else {
            player.sendMessage("§a✅ Auto-rankup activé");
            player.sendMessage("§7Vous monterez automatiquement en rang dès que possible.");
        }
    }

    /**
     * Affiche les informations de rankup du joueur
     */
    private void showRankupInfo(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentRank = getCurrentRank(player);
        String nextRank = getNextRank(currentRank);

        player.sendMessage("§e📊 Informations de Rankup");
        player.sendMessage("§7Rang actuel: §e" + currentRank.toUpperCase());
        player.sendMessage("§7Coins disponibles: §e" + NumberFormatter.format(playerData.getCoins()));

        if (nextRank == null) {
            player.sendMessage("§a✅ Rang maximum atteint (Z)!");
            player.sendMessage("§6🏆 Utilisez /prestige pour continuer votre progression!");
        } else {
            long price = getRankupPrice(nextRank);
            player.sendMessage("§7Prochain rang: §a" + nextRank.toUpperCase());
            player.sendMessage("§7Prix: §c" + NumberFormatter.format(price) + " coins");

            if (playerData.getCoins() >= price) {
                player.sendMessage("§a✅ Vous pouvez effectuer ce rankup!");
            } else {
                long missing = price - playerData.getCoins();
                player.sendMessage("§c❌ Coins manquants: " + NumberFormatter.format(missing));
            }
        }

        // Info auto-rankup
        if (player.hasPermission("specialmine.vip")) {
            boolean hasAutoRankup = playerData.hasAutoRankup();
            player.sendMessage("§7Auto-rankup: " + (hasAutoRankup ? "§a✅ Activé" : "§c❌ Désactivé"));
        }
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§e📋 Commandes Rankup:");
        player.sendMessage("§7• §6/rankup §7- Monte d'un rang");
        player.sendMessage("§7• §6/rankup all §7- Monte au maximum possible");
        player.sendMessage("§7• §6/rankup info §7- Informations sur votre progression");

        if (player.hasPermission("specialmine.vip")) {
            player.sendMessage("§e🌟 VIP:");
            player.sendMessage("§7• §6/rankup auto §7- Active/désactive l'auto-rankup");
        }

        if (player.hasPermission("specialmine.admin")) {
            player.sendMessage("§c🔧 Admin:");
            player.sendMessage("§7• §6/rankup force <joueur> <a-z> §7- Force le rang d'un joueur");
        }
    }

    /**
     * Commande admin pour forcer le rang d'un joueur
     */
    public void forceRankPlayer(Player admin, String playerName, String rank) {
        if (!admin.hasPermission("specialmine.admin")) {
            admin.sendMessage("§c❌ Vous n'avez pas la permission!");
            return;
        }

        Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            admin.sendMessage("§c❌ Joueur introuvable ou hors ligne!");
            return;
        }

        // Validation du rang
        if (rank.length() != 1 || rank.charAt(0) < 'a' || rank.charAt(0) > 'z') {
            admin.sendMessage("§c❌ Rang invalide! Utilisez a-z");
            return;
        }

        // Efface les anciennes permissions
        clearAllMinePermissions(target);

        // Ajoute les nouvelles permissions
        addMinePermissionsUpToRank(target, rank);

        admin.sendMessage("§a✅ Rang forcé pour " + target.getName() + ": " + rank.toUpperCase());
        target.sendMessage("§a✅ Votre rang a été défini à " + rank.toUpperCase() + " par un administrateur!");

        plugin.getPluginLogger().info("Rang forcé par " + admin.getName() + " pour " + target.getName() + ": " + rank.toUpperCase());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("all", "info", "max"));

            if (sender.hasPermission("specialmine.vip") || sender.hasPermission("specialmine.admin")) {
                subCommands.add("auto");
            }

            if (sender.hasPermission("specialmine.admin")) {
                subCommands.add("force");
            }

            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("force") && sender.hasPermission("specialmine.admin")) {
            // Auto-complétion des joueurs en ligne
            plugin.getServer().getOnlinePlayers().forEach(player -> completions.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("force") && sender.hasPermission("specialmine.admin")) {
            // Auto-complétion des rangs a-z
            for (char c = 'a'; c <= 'z'; c++) {
                completions.add(String.valueOf(c));
            }
        }

        Collections.sort(completions);
        return completions;
    }
}