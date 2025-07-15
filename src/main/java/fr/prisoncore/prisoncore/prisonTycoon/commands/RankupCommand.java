package fr.prisoncore.prisoncore.prisonTycoon.commands;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

/**
 * Commande /rankup - Système de montée en rang pour les mines
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
            case "all", "max" -> performAllRankups(player);
            case "auto" -> {
                if (!player.hasPermission("specialmine.vip") && !player.hasPermission("specialmine.admin")) {
                    player.sendMessage("§cCette fonctionnalité est réservée aux VIP!");
                    return true;
                }
                toggleAutoRankup(player);
            }
            case "info" -> showRankupInfo(player);
            default -> {
                sendHelpMessage(player);
            }
        }

        return true;
    }

    /**
     * Effectue un seul rankup
     */
    private void performSingleRankup(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentRank = getCurrentRank(playerData);
        String nextRank = getNextRank(currentRank);

        if (nextRank == null) {
            player.sendMessage("§cVous êtes déjà au rang maximum (Z)!");
            return;
        }

        long price = getRankupPrice(nextRank);
        if (price < 0) {
            player.sendMessage("§cErreur: Prix de rankup non configuré pour le rang " + nextRank.toUpperCase() + "!");
            return;
        }

        if (playerData.getCoins() < price) {
            player.sendMessage("§cCoins insuffisants!");
            player.sendMessage("§7Rang suivant: §e" + nextRank.toUpperCase() + " §7- Prix: §c" + NumberFormatter.format(price) + " coins");
            player.sendMessage("§7Vous avez: §e" + NumberFormatter.format(playerData.getCoins()) + " coins");
            return;
        }

        // Effectue le rankup
        playerData.removeCoins(price);
        playerData.clearMinePermissions();
        // MODIFICATION: Ajoute la permission avec le préfixe "mine-"
        plugin.getPlayerDataManager().addMinePermissionToPlayer(player.getUniqueId(), "mine-" + nextRank);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Messages de succès
        player.sendMessage("§a✅ Rankup réussi!");
        player.sendMessage("§7Rang: §e" + currentRank.toUpperCase() + " §7→ §a" + nextRank.toUpperCase());
        player.sendMessage("§7Prix payé: §c-" + NumberFormatter.format(price) + " coins");
        player.sendMessage("§7Coins restants: §e" + NumberFormatter.format(playerData.getCoins()));
        player.sendMessage("§7Vous pouvez maintenant miner dans les mines A à " + nextRank.toUpperCase() + "!");

        // Son de succès
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        plugin.getPluginLogger().info("Rankup effectué: " + player.getName() + " " +
                currentRank.toUpperCase() + " → " + nextRank.toUpperCase() +
                " (prix: " + price + " coins)");
    }

    /**
     * Effectue tous les rankups possibles
     */
    private void performAllRankups(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentRank = getCurrentRank(playerData);
        String originalRank = currentRank;
        long totalCost = 0;
        int rankupsCount = 0;

        // Calcule combien de rankups sont possibles
        while (true) {
            String nextRank = getNextRank(currentRank);
            if (nextRank == null) break; // Rang maximum atteint

            long price = getRankupPrice(nextRank);
            if (price < 0) break; // Prix non configuré

            if (playerData.getCoins() - totalCost < price) break; // Plus assez de coins

            totalCost += price;
            currentRank = nextRank;
            rankupsCount++;
        }

        if (rankupsCount == 0) {
            if (getNextRank(getCurrentRank(playerData)) == null) {
                player.sendMessage("§cVous êtes déjà au rang maximum (Z)!");
            } else {
                player.sendMessage("§cCoins insuffisants pour le prochain rankup!");
                showNextRankupInfo(player);
            }
            return;
        }

        // Effectue tous les rankups
        playerData.removeCoins(totalCost);
        playerData.clearMinePermissions();
        // MODIFICATION: Ajoute la permission avec le préfixe "mine-"
        plugin.getPlayerDataManager().addMinePermissionToPlayer(player.getUniqueId(), "mine-" + currentRank);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Messages de succès
        player.sendMessage("§a✅ Rankup All réussi!");
        player.sendMessage("§7Progressé de §e" + originalRank.toUpperCase() + " §7à §a" + currentRank.toUpperCase());
        player.sendMessage("§7Nombre de rankups: §e" + rankupsCount);
        player.sendMessage("§7Coût total: §c-" + NumberFormatter.format(totalCost) + " coins");
        player.sendMessage("§7Coins restants: §e" + NumberFormatter.format(playerData.getCoins()));
        player.sendMessage("§7Vous pouvez maintenant miner dans les mines A à " + currentRank.toUpperCase() + "!");

        // Son de succès
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        plugin.getPluginLogger().info("Rankup All effectué: " + player.getName() + " " +
                originalRank.toUpperCase() + " → " + currentRank.toUpperCase() +
                " (" + rankupsCount + " rankups, coût: " + totalCost + " coins)");
    }

    /**
     * Active/désactive l'auto-rankup pour un joueur VIP
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
            player.sendMessage("§7Vous monterez automatiquement en rang après chaque récapitulatif.");
            player.sendMessage("§7L'auto-rankup s'exécute seulement si vous avez miné dans la minute précédente.");
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
     * Affiche les informations du prochain rankup
     */
    private void showNextRankupInfo(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentRank = getCurrentRank(playerData);
        String nextRank = getNextRank(currentRank);

        if (nextRank != null) {
            long price = getRankupPrice(nextRank);
            if (price >= 0) {
                player.sendMessage("§7Prochain rang: §e" + nextRank.toUpperCase() + " §7- Prix: §e" +
                        NumberFormatter.format(price) + " coins");
            }
        }
    }

    /**
     * Obtient le rang actuel du joueur
     */
    private String getCurrentRank(PlayerData playerData) {
        String highestPermission = playerData.getHighestMinePermission();
        if (highestPermission == null) {
            // NOTE: Si un nouveau joueur n'a aucune permission de mine, on le considère au rang A par défaut.
            // La première montée le passera au rang B.
            return "a"; // Rang par défaut
        }

        // Extrait la lettre de la permission (ex: de "mine-a" on garde "a")
        if (highestPermission.startsWith("mine-")) {
            return highestPermission.substring(5);
        }

        // Sécurité: Si la permission n'a pas le bon format, on retourne le rang par défaut pour éviter des erreurs.
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
        // Le nom de la mine dans la config est déjà au format "mine-a", "mine-b", etc.
        String mineName = "mine-" + rank;

        // Cherche le prix dans la configuration de la mine
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
     * Effectue l'auto-rankup pour un joueur VIP (appelé par une autre tâche)
     */
    public void performAutoRankup(Player player) {

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String originalRank = getCurrentRank(playerData);

        // Utilise la même logique que /rankup all
        String currentRank = originalRank;
        long totalCost = 0;
        int rankupsCount = 0;

        while (true) {
            String nextRank = getNextRank(currentRank);
            if (nextRank == null) break;

            long price = getRankupPrice(nextRank);
            if (price < 0) break;

            if (playerData.getCoins() - totalCost < price) break;

            totalCost += price;
            currentRank = nextRank;
            rankupsCount++;
        }

        if (rankupsCount == 0) {
            return; // Aucun rankup possible
        }

        // Effectue les rankups
        playerData.removeCoins(totalCost);
        playerData.clearMinePermissions();
        // MODIFICATION: Ajoute la permission avec le préfixe "mine-"
        plugin.getPlayerDataManager().addMinePermissionToPlayer(player.getUniqueId(), "mine-" + currentRank);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Notification discrète
        player.sendMessage("§a🔄 Auto-rankup: §e" + originalRank.toUpperCase() + " §7→ §a" +
                currentRank.toUpperCase() + " §7(" + rankupsCount + " niveau" +
                (rankupsCount > 1 ? "x" : "") + ")");

        plugin.getPluginLogger().info("Auto-rankup effectué: " + player.getName() + " " +
                originalRank.toUpperCase() + " → " + currentRank.toUpperCase() +
                " (" + rankupsCount + " rankups, coût: " + totalCost + " coins)");
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