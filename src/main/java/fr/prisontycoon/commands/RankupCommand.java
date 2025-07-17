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
 * Commande /rankup - Système de montée en rang pour les mines (Version optimisée)
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
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * Tente d'effectuer un seul rankup.
     * C'est la méthode centrale qui contient toute la logique.
     *
     * @param player Le joueur qui rankup.
     * @param silent Si true, aucun message de succès ou son ne sera envoyé/joué. Les erreurs sont toujours affichées.
     * @return true si le rankup a réussi, false sinon.
     */
    private boolean tryRankup(Player player, boolean silent) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentRank = getCurrentRank(playerData);
        String nextRank = getNextRank(currentRank);

        if (nextRank == null) {
            if (!silent) player.sendMessage("§cVous êtes déjà au rang maximum (Z)!");
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
                player.sendMessage("§7Rang suivant: §e" + nextRank.toUpperCase() + " §7- Prix: §c" + NumberFormatter.format(price) + " coins");
                player.sendMessage("§7Vous avez: §e" + NumberFormatter.format(playerData.getCoins()) + " coins");
            }
            return false;
        }

        // Effectue la transaction de rankup
        playerData.removeCoins(price);
        playerData.clearMinePermissions();
        plugin.getPlayerDataManager().addMinePermissionToPlayer(player.getUniqueId(), "mine-" + nextRank);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        if (!silent) {
            // Messages de succès pour un rankup unique
            player.sendMessage("§a✅ Rankup réussi!");
            player.sendMessage("§7Rang: §e" + currentRank.toUpperCase() + " §7→ §a" + nextRank.toUpperCase());
            player.sendMessage("§7Prix payé: §c-" + NumberFormatter.format(price) + " coins");
            player.sendMessage("§7Coins restants: §e" + NumberFormatter.format(playerData.getCoins()));
            player.sendMessage("§7Vous pouvez maintenant miner dans les mines A à " + nextRank.toUpperCase() + "!");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }

        plugin.getPluginLogger().info("Rankup effectué: " + player.getName() + " " +
                currentRank.toUpperCase() + " → " + nextRank.toUpperCase() +
                " (prix: " + price + " coins)");

        return true;
    }

    /**
     * Lance un seul rankup et affiche les messages.
     * Wrapper pour la commande /rankup simple.
     */
    private void performSingleRankup(Player player) {
        tryRankup(player, false);
    }

    /**
     * Effectue le maximum de rankups possibles en appelant tryRankup en boucle.
     * Envoie un message récapitulatif à la fin.
     */
    private void performMaxRankup(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String originalRank = getCurrentRank(playerData);
        String finalRank = originalRank;
        long totalCost = 0;
        int rankupsCount = 0;

        // Tente de rankup en boucle jusqu'à ce que ça échoue (argent ou rang max)
        while (tryRankup(player, true)) {
            rankupsCount++;
        }

        // Met à jour le rang final et le coût total
        finalRank = getCurrentRank(playerData);
        // Recalculer le coût total est plus sûr que de l'additionner, au cas où les prix changent
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
            // Pour cela, on ré-appelle tryRankup en mode non-silencieux.
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

            plugin.getPluginLogger().info("Rankup All effectué: " + player.getName() + " " +
                    originalRank.toUpperCase() + " → " + finalRank.toUpperCase() +
                    " (" + rankupsCount + " rankups, coût: " + totalCost + " coins)");
        }
    }

    /**
     * Effectue l'auto-rankup pour un joueur.
     * Utilise la même logique que maxRankup mais avec un message de notification discret.
     */
    public void performAutoRankup(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String originalRank = getCurrentRank(playerData);
        int rankupsCount = 0;

        // Tente de rankup en boucle silencieusement
        while (tryRankup(player, true)) {
            rankupsCount++;
        }

        if (rankupsCount > 0) {
            String finalRank = getCurrentRank(playerData);
            // Notification discrète
            player.sendMessage("§a🔄 Auto-rankup: §e" + originalRank.toUpperCase() + " §7→ §a" +
                    finalRank.toUpperCase() + " §7(" + rankupsCount + " niveau" +
                    (rankupsCount > 1 ? "x" : "") + ")");

            plugin.getPluginLogger().info("Auto-rankup effectué pour " + player.getName() + ": " + rankupsCount + " niveau(x).");
        }
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
        String currentRank = getCurrentRank(playerData);
        String nextRank = getNextRank(currentRank);

        player.sendMessage("§e📊 Informations de Rankup");
        player.sendMessage("§7Rang actuel: §e" + currentRank.toUpperCase());
        player.sendMessage("§7Coins disponibles: §e" + NumberFormatter.format(playerData.getCoins()));

        if (nextRank == null) {
            player.sendMessage("§a✅ Rang maximum atteint!");
            return;
        }

        long nextPrice = getRankupPrice(nextRank);
        if (nextPrice >= 0) {
            player.sendMessage("§7Rang suivant: §e" + nextRank.toUpperCase());
            player.sendMessage("§7Prix: §e" + NumberFormatter.format(nextPrice) + " coins");

            if (playerData.getCoins() >= nextPrice) {
                player.sendMessage("§a✅ Vous pouvez rankup!");
            } else {
                long needed = nextPrice - playerData.getCoins();
                player.sendMessage("§c❌ Il vous manque §e" + NumberFormatter.format(needed) + " coins");
            }
        }

        // Auto-rankup info pour VIP
        if (canAutoRankup(player)) {
            boolean autoEnabled = playerData.hasAutoRankup();
            player.sendMessage("§7Auto-rankup VIP: " + (autoEnabled ? "§a✅ Activé" : "§c❌ Désactivé"));
        }

        // Progression vers rang Z
        showProgressToMaxRank(player, currentRank);
    }

    /**
     * Affiche la progression vers le rang maximum
     */
    private void showProgressToMaxRank(Player player, String currentRank) {
        long totalCostToMax = 0;
        int rankupsToMax = 0;
        String tempRank = currentRank;

        while (true) {
            String nextRank = getNextRank(tempRank);
            if (nextRank == null) break;

            long price = getRankupPrice(nextRank);
            if (price < 0) break;

            totalCostToMax += price;
            rankupsToMax++;
            tempRank = nextRank;
        }

        if (rankupsToMax > 0) {
            player.sendMessage("§7Pour atteindre le rang Z: §e" + rankupsToMax + " rankups §7(§e" +
                    NumberFormatter.format(totalCostToMax) + " coins§7)");
        }
    }

    /**
     * Obtient le rang actuel du joueur
     */
    private String getCurrentRank(PlayerData playerData) {
        String highestPermission = playerData.getHighestMinePermission();
        if (highestPermission == null) {
            return "a"; // Rang par défaut
        }
        if (highestPermission.startsWith("mine-")) {
            return highestPermission.substring(5);
        }
        return "a";
    }

    /**
     * Obtient le rang suivant
     */
    private String getNextRank(String currentRank) {
        if (currentRank == null || currentRank.isEmpty()) {
            return "a";
        }
        char currentChar = currentRank.charAt(0);
        if (currentChar >= 'z') {
            return null; // Rang maximum atteint
        }
        return String.valueOf((char) (currentChar + 1));
    }

    /**
     * Obtient le prix de rankup depuis la configuration
     */
    private long getRankupPrice(String rank) {
        String mineName = "mine-" + rank;
        return plugin.getConfig().getLong("mines." + mineName + ".rankup-price", -1);
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
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("all", "info", "max"));

            if (sender.hasPermission("specialmine.vip") || sender.hasPermission("specialmine.admin")) {
                subCommands.add("auto");
            }

            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}