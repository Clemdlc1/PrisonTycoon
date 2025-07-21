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
 * Commande /rankup - Système de montée en rang pour les mines (Version corrigée avec permissions bukkit)
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
     * CORRIGÉ: Tente d'effectuer un seul rankup avec les nouvelles permissions bukkit
     */
    private boolean tryRankup(Player player, boolean silent) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentRank = plugin.getMineManager().getCurrentRank(player);
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

        // CORRIGÉ: Effectue la transaction de rankup avec permissions bukkit
        playerData.removeCoins(price);

        // NOUVEAU: Retire l'ancienne permission de mine (si elle existe)
        String oldRank = plugin.getMineManager().getCurrentRank(player);
        if (!oldRank.equals("a")) { // "a" est le rang par défaut, pas de permission à retirer
            plugin.getPlayerDataManager().removeMinePermissionFromPlayer(player.getUniqueId(), oldRank);
        }

        // NOUVEAU: Ajoute la nouvelle permission de mine (seulement la plus élevée)
        plugin.getPlayerDataManager().addMinePermissionToPlayer(player.getUniqueId(), nextRank);
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
     * NOUVEAU: Efface toutes les permissions de mine bukkit du joueur
     */
    private void clearAllMinePermissions(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Retirer les permissions de mine a-z
        for (char c = 'a'; c <= 'z'; c++) {
            String minePermission = "specialmine.mine." + c;
            if (playerData.hasCustomPermission(minePermission)) {
                plugin.getPlayerDataManager().removePermissionFromPlayer(player.getUniqueId(), minePermission);
            }
        }

        // Retirer la permission FREE si elle existe
        if (playerData.hasCustomPermission("specialmine.free")) {
            plugin.getPlayerDataManager().removePermissionFromPlayer(player.getUniqueId(), "specialmine.free");
        }

        // Ancienne logique pour compatibilité
        playerData.clearMinePermissions();
    }

    /**
     * NOUVEAU: Ajoute toutes les permissions de mine jusqu'au rang spécifié (système cumulatif)
     */
    private void addMinePermissionsUpToRank(Player player, String targetRank) {
        // Gestion spéciale pour le rang FREE
        if (targetRank.equalsIgnoreCase("free")) {
            // Ajouter toutes les permissions de A à Z
            for (char c = 'a'; c <= 'z'; c++) {
                String minePermission = "specialmine.mine." + c;
                plugin.getPlayerDataManager().addPermissionToPlayer(player.getUniqueId(), minePermission);
            }

            // Ajouter la permission FREE
            plugin.getPlayerDataManager().addPermissionToPlayer(player.getUniqueId(), "specialmine.free");

            plugin.getPluginLogger().info("Permissions de mine ajoutées pour " + player.getName() + ": A-Z + FREE");
            return;
        }

        // Logique normale pour les rangs a-z
        char targetChar = targetRank.charAt(0);
        for (char c = 'a'; c <= targetChar; c++) {
            String minePermission = "specialmine.mine." + c;
            plugin.getPlayerDataManager().addPermissionToPlayer(player.getUniqueId(), minePermission);
        }

        plugin.getPluginLogger().info("Permissions de mine ajoutées pour " + player.getName() + ": A-" + targetRank.toUpperCase());
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
        String originalRank = plugin.getMineManager().getCurrentRank(player);
        String finalRank = originalRank;
        long totalCost = 0;
        int rankupsCount = 0;

        // Tente de rankup en boucle jusqu'à ce que ça échoue (argent ou rang max)
        while (tryRankup(player, true)) {
            rankupsCount++;
        }

        // Met à jour le rang final et le coût total
        finalRank = plugin.getMineManager().getCurrentRank(player);

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

            plugin.getPluginLogger().info("Rankup All effectué: " + player.getName() + " " +
                    originalRank.toUpperCase() + " → " + finalRank.toUpperCase() +
                    " (" + rankupsCount + " rankups, coût: " + totalCost + " coins)");
        }
    }

    /**
     * Effectue l'auto-rankup pour un joueur.
     */
    public void performAutoRankup(Player player) {
        String originalRank = plugin.getMineManager().getCurrentRank(player);
        int rankupsCount = 0;

        // Tente de rankup en boucle silencieusement
        while (tryRankup(player, true)) {
            rankupsCount++;
        }

        if (rankupsCount > 0) {
            String finalRank = plugin.getMineManager().getCurrentRank(player);
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
        String currentRank = plugin.getMineManager().getCurrentRank(player);
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

        // NOUVEAU: Affiche les permissions de mine actuelles
        showCurrentMinePermissions(player);
    }

    /**
     * NOUVEAU: Affiche les permissions de mine actuelles du joueur
     */
    private void showCurrentMinePermissions(Player player) {
        List<String> accessibleMines = new ArrayList<>();

        for (char c = 'a'; c <= 'z'; c++) {
            String minePermission = "specialmine.mine." + c;
            if (player.hasPermission(minePermission)) {
                accessibleMines.add(String.valueOf(c).toUpperCase());
            }
        }

        if (!accessibleMines.isEmpty()) {
            player.sendMessage("§7Mines accessibles: §a" + String.join(", ", accessibleMines));
        } else {
            player.sendMessage("§7Mines accessibles: §cAucune (erreur de permissions)");
        }
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
     * NOUVEAU: Obtient le rang actuel pour les joueurs hors ligne (fallback)
     */
    private String getCurrentRankOffline(PlayerData playerData) {
        // Utilise les permissions bukkit stockées dans customPermissions
        String highestRank = "a";

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
     * Obtient le rang suivant
     */
    private String getNextRank(String currentRank) {
        if (currentRank == null || currentRank.isEmpty()) {
            return "a";
        }

        // Si on a déjà le rang FREE, on ne peut plus ranker
        if (currentRank.equalsIgnoreCase("free")) {
            return null;
        }

        // Si on est au rang Z, le prochain est FREE
        if (currentRank.equalsIgnoreCase("z")) {
            return "free";
        }

        // Progression normale a->b->c->...->z
        char currentChar = currentRank.charAt(0);
        if (currentChar >= 'z') {
            return "free"; // Fallback au cas où
        }
        return String.valueOf((char) (currentChar + 1));
    }

    /**
     * Obtient le prix de rankup depuis la configuration
     */
    private long getRankupPrice(String targetRank) {
        if (targetRank == null) return -1;

        // Prix spécial pour le rang FREE
        if (targetRank.equalsIgnoreCase("free")) {
            return plugin.getConfig().getLong("ranks.free.price", 1000000); // 1M par défaut
        }

        // Prix normal pour les autres rangs
        String mineName = "mine-" + targetRank.toLowerCase();
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

    /**
     * NOUVEAU: Commande admin pour forcer le rang d'un joueur
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
        }

        Collections.sort(completions);
        return completions;
    }
}