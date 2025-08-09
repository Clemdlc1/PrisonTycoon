package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Commande /rankup - Système de montée en rang pour les mines A-Z
 * CORRIGÉ: Permissions non-cumulatives - seulement le rang le plus élevé
 */
public class RankupCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public RankupCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
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
     * Tente d'effectuer un seul rankup.
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

        // Définit la permission du nouveau rang de manière non-cumulative
        setMinePermissionToRank(player, nextRank);

        if (!silent) {
            player.sendMessage("§a✅ Rankup réussi!");
            player.sendMessage("§7Nouveau rang: §a" + nextRank.toUpperCase());
            player.sendMessage("§7Coût: §c-" + NumberFormatter.format(price) + " coins");
            player.sendMessage("§7Coins restants: §e" + NumberFormatter.format(playerData.getCoins()));
            player.sendMessage("§7Vous pouvez maintenant miner dans la mine " + nextRank.toUpperCase() + "!");
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
     * Obtient le rang actuel via les permissions effectives du joueur.
     */
    public String getCurrentRank(Player player) {
        String highestRank = "a"; // Rang par défaut

        Set<String> minePermissions = player.getEffectivePermissions().stream()
                .map(PermissionAttachmentInfo::getPermission)
                .filter(perm -> perm.startsWith("specialmine.mine."))
                .collect(Collectors.toSet());

        for (char c = 'z'; c >= 'a'; c--) {
            String minePermission = "specialmine.mine." + c;
            if (minePermissions.contains(minePermission)) {
                highestRank = String.valueOf(c);
                break;
            }
        }

        return highestRank;
    }

    /**
     * Obtient le rang suivant (A-Z).
     */
    private String getNextRank(String currentRank) {
        if (currentRank == null || currentRank.isEmpty()) {
            return "a";
        }
        if (currentRank.equalsIgnoreCase("z")) {
            return null; // Rang maximum
        }
        char currentChar = currentRank.charAt(0);
        return String.valueOf((char) (currentChar + 1));
    }

    /**
     * CORRIGÉ: Définit la permission de mine au rang spécifique (non-cumulatif).
     * Retire les anciennes permissions de rang et ajoute la nouvelle.
     */
    public void setMinePermissionToRank(Player player, String targetRank) {
        // Validation du rang
        if (targetRank == null || targetRank.length() != 1) {
            plugin.getPluginLogger().warning("Tentative de définir un rang invalide: " + targetRank);
            return;
        }
        char targetChar = targetRank.charAt(0);
        if (targetChar < 'a' || targetChar > 'z') {
            plugin.getPluginLogger().warning("Tentative de définir un rang invalide: " + targetRank);
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // 1. Obtenir une copie modifiable des permissions custom du joueur.
        Set<String> permissions = playerData.getCustomPermissions();

        // 2. Retirer toutes les permissions de rang de mine existantes pour garantir un état propre.
        permissions.removeIf(perm -> perm.startsWith("specialmine.mine."));

        // 3. Ajouter la nouvelle permission de rang, en utilisant la chaîne de permission complète et correcte.
        String newRankPermission = "specialmine.mine." + targetRank.toLowerCase();
        permissions.add(newRankPermission);

        // 4. Mettre à jour les données du joueur avec le nouvel ensemble de permissions.
        playerData.setCustomPermissions(permissions);

        // 5. Recharger les permissions en direct du joueur via le PermissionManager pour appliquer les changements.
        plugin.getPermissionManager().reloadPlayerPermissions(player);

        plugin.getPluginLogger().info("Permission de mine définie pour " + player.getName() + ": " + targetRank.toUpperCase() +
                " (les permissions précédentes ont été supprimées)");
    }

    /**
     * Obtient le prix de rankup depuis la configuration.
     */
    private long getRankupPrice(String targetRank) {
        if (targetRank == null) return -1;
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
     * Effectue le maximum de rankups possibles.
     */
    private void performMaxRankup(Player player) {
        String originalRank = getCurrentRank(player);
        int rankupsCount = 0;
        long totalCost = 0;

        while (true) {
            String currentRankForLoop = getCurrentRank(player);
            String nextRank = getNextRank(currentRankForLoop);

            if (nextRank == null) break;

            long price = getRankupPrice(nextRank);
            if (price < 0 || plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getCoins() < price) {
                break;
            }

            // Nous devons manuellement additionner le coût ici car tryRankup le déduit.
            totalCost += price;

            if (!tryRankup(player, true)) {
                // Si tryRankup échoue pour une raison quelconque, nous annulons le dernier coût ajouté et nous arrêtons.
                totalCost -= price;
                break;
            }
            rankupsCount++;
        }

        if (rankupsCount > 0) {
            String finalRank = getCurrentRank(player);
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

            player.sendMessage("§a✅ Rankup ALL terminé!");
            player.sendMessage("§7Progression: §e" + originalRank.toUpperCase() + " §7→ §a" + finalRank.toUpperCase());
            player.sendMessage("§7Rankups effectués: §a" + rankupsCount);
            player.sendMessage("§7Coût total: §c-" + NumberFormatter.format(totalCost) + " coins");
            player.sendMessage("§7Coins restants: §e" + NumberFormatter.format(playerData.getCoins()));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

            if (finalRank.equals("z")) {
                player.sendMessage("§6🏆 Félicitations! Vous avez atteint le rang maximum!");
                player.sendMessage("§6✨ Vous pouvez maintenant effectuer un /prestige pour obtenir des bonus permanents!");
            }

            plugin.getPluginLogger().info("Rankup All effectué: " + player.getName() + " " +
                    originalRank.toUpperCase() + " → " + finalRank.toUpperCase() +
                    " (" + rankupsCount + " rankups, coût: " + totalCost + " coins)");
        } else {
            player.sendMessage("§cVous n'avez pas assez d'argent pour le prochain rang ou vous êtes déjà au rang maximum.");
        }
    }

    /**
     * Effectue l'auto-rankup pour un joueur.
     */
    public void performAutoRankup(Player player) {
        String originalRank = getCurrentRank(player);
        int rankupsCount = 0;

        while (tryRankup(player, true)) {
            rankupsCount++;
        }

        if (rankupsCount > 0) {
            String finalRank = getCurrentRank(player);
            player.sendMessage("§a🔄 Auto-rankup: §e" + originalRank.toUpperCase() + " §7→ §a" +
                    finalRank.toUpperCase() + " §7(" + rankupsCount + " niveau" +
                    (rankupsCount > 1 ? "x" : "") + ")");
            plugin.getPluginLogger().info("Auto-rankup effectué pour " + player.getName() + ": " + rankupsCount + " niveau(x).");
        }
    }

    /**
     * Vérifie si un joueur peut effectuer un auto-rankup.
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
        } else {
            player.sendMessage("§a✅ Auto-rankup activé");
        }
    }

    /**
     * Affiche les informations de rankup.
     */
    private void showRankupInfo(Player player) {
        String currentRank = getCurrentRank(player);
        String nextRank = getNextRank(currentRank);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6⛏ §lINFORMATIONS RANKUP");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§7Rang actuel: §a" + currentRank.toUpperCase());

        if (nextRank != null) {
            long price = getRankupPrice(nextRank);
            player.sendMessage("§7Prochain rang: §e" + nextRank.toUpperCase());
            player.sendMessage("§7Prix: §c" + NumberFormatter.format(price) + " coins");
            player.sendMessage("§7Vos coins: §e" + NumberFormatter.format(playerData.getCoins()) + " coins");

            if (playerData.getCoins() >= price) {
                player.sendMessage("§a✅ Vous pouvez rankup!");
            } else {
                long needed = price - playerData.getCoins();
                player.sendMessage("§c❌ Il vous manque " + NumberFormatter.format(needed) + " coins");
            }
        } else {
            player.sendMessage("§6★ Vous êtes au rang maximum (Z)!");
            player.sendMessage("§7Utilisez §e/prestige §7pour continuer votre progression.");
        }

        if (player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin")) {
            boolean autoRankupEnabled = playerData.hasAutoRankup();
            player.sendMessage("§7Auto-rankup VIP: " +
                    (autoRankupEnabled ? "§a✅ Activé" : "§c❌ Désactivé"));
        }

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Envoie le message d'aide.
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6⛏ §lCOMMANDES RANKUP");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§e/rankup §7- Monte d'un rang");
        player.sendMessage("§e/rankup all §7- Monte au maximum possible");
        player.sendMessage("§e/rankup info §7- Informations sur votre progression");

        if (player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin")) {
            player.sendMessage("§e/rankup auto §7- Active/désactive l'auto-rankup VIP");
        }

        if (player.hasPermission("specialmine.admin")) {
            player.sendMessage("§c/rankup force <joueur> <rang> §7- Force un rang (admin)");
        }

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * ADMIN: Force le rang d'un joueur.
     */
    private void forceRankPlayer(Player admin, String playerName, String rank) {
        Player target = plugin.getServer().getPlayer(playerName);
        if (target == null) {
            admin.sendMessage("§c❌ Joueur introuvable ou hors ligne!");
            return;
        }

        if (rank.length() != 1 || rank.charAt(0) < 'a' || rank.charAt(0) > 'z') {
            admin.sendMessage("§c❌ Rang invalide! Utilisez une lettre de a à z.");
            return;
        }

        setMinePermissionToRank(target, rank);

        admin.sendMessage("§a✅ Rang forcé pour " + target.getName() + ": " + rank.toUpperCase());
        target.sendMessage("§a✅ Votre rang a été défini à " + rank.toUpperCase() + " par un administrateur!");

        plugin.getPluginLogger().info("Rang forcé par " + admin.getName() + " pour " + target.getName() + ": " + rank.toUpperCase());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        List<String> subCommands = new ArrayList<>(Arrays.asList("all", "max", "info"));

        if (sender.hasPermission("specialmine.vip") || sender.hasPermission("specialmine.admin")) {
            subCommands.add("auto");
        }
        if (sender.hasPermission("specialmine.admin")) {
            subCommands.add("force");
        }

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("force") && sender.hasPermission("specialmine.admin")) {
            plugin.getServer().getOnlinePlayers().forEach(player -> completions.add(player.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("force") && sender.hasPermission("specialmine.admin")) {
            for (char c = 'a'; c <= 'z'; c++) {
                completions.add(String.valueOf(c));
            }
        }

        Collections.sort(completions);
        return completions;
    }
}