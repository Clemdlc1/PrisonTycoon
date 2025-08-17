package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.WarpData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commandes de warp: /warp, /mine, /spawn
 * Gère la téléportation vers les différents warps du serveur
 */
public class WarpCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public WarpCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "warp" -> handleWarpCommand(player, args);
            case "mine" -> handleMineCommand(player, args);
            case "spawn" -> handleSpawnCommand(player);
        }

        return true;
    }

    /**
     * Gère la commande /warp
     */
    private void handleWarpCommand(Player player, String[] args) {
        if (args.length == 0) {
            // Ouvrir le GUI des warps
            plugin.getWarpGUI().openWarpMenu(player);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "list", "liste" -> handleWarpList(player, args);
            case "info" -> handleWarpInfo(player, args);
            case "gui", "menu" -> plugin.getWarpGUI().openWarpMenu(player);
            case "help", "aide" -> sendWarpHelp(player);
            default -> {
                // Essayer de se téléporter au warp
                String warpName = args[0];
                plugin.getWarpManager().teleportToWarp(player, warpName);
            }
        }
    }

    /**
     * Gère la commande /mine
     */
    private void handleMineCommand(Player player, String[] args) {
        if (args.length == 0) {
            // Ouvrir le GUI des mines
            plugin.getWarpGUI().openMineWarpsMenu(player, 1);
            return;
        }

        String mineName = args[0];

        // Essayer d'abord avec le système de warps pour une gestion unifiée
        String mineWarpId = mineName.toLowerCase();
        WarpData mineWarp = plugin.getWarpManager().findWarp(mineWarpId);

        if (mineWarp != null) {
            plugin.getWarpManager().teleportToWarp(player, mineWarpId);
        } else {
            // Fallback vers l'ancien système si le warp n'existe pas
            plugin.getMineManager().teleportToMine(player, mineName);
        }
    }

    /**
     * Gère la commande /spawn
     */
    private void handleSpawnCommand(Player player) {
        if (plugin.getCombatManager() != null && plugin.getCombatManager().isInCombat(player)) {
            long remaining = plugin.getCombatManager().getRemainingCombatSeconds(player);
            player.sendMessage("§c❌ Impossible de se téléporter en combat! Reste: §e" + remaining + "s");
            return;
        }
        WarpData spawnWarp = plugin.getWarpManager().getSpawnWarp();

        if (spawnWarp != null) {
            plugin.getWarpManager().teleportToWarp(player, spawnWarp.getId(), false);
        } else {
            player.sendMessage("§c❌ Aucun spawn configuré!");
            player.sendMessage("§7Contactez un administrateur.");
        }
    }

    /**
     * Affiche la liste des warps
     */
    private void handleWarpList(Player player, String[] args) {
        WarpData.WarpType filterType = null;

        // Filtrage par type si spécifié
        if (args.length >= 2) {
            try {
                filterType = WarpData.WarpType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c❌ Type de warp invalide!");
                player.sendMessage("§7Types disponibles: §eSpawn§7, §eMine§7, §eCrate§7, §eCave§7, §eShop§7, §ePvP§7, §eEvent§7, §eOther");
                return;
            }
        }

        player.sendMessage("§6═══════════════════════════════════");
        if (filterType != null) {
            player.sendMessage("§6      🌟 WARPS " + filterType.getDisplayName().toUpperCase());
        } else {
            player.sendMessage("§6         🌟 TOUS LES WARPS");
        }
        player.sendMessage("§6═══════════════════════════════════");

        List<WarpData> warps;
        if (filterType != null) {
            warps = plugin.getWarpManager().getWarpsByType(filterType);
        } else {
            warps = new ArrayList<>(plugin.getWarpManager().getAccessibleWarps(player));
        }

        if (warps.isEmpty()) {
            player.sendMessage("§7Aucun warp trouvé.");
            return;
        }

        // Grouper par type
        WarpData.WarpType currentType = null;
        for (WarpData warp : warps) {
            if (currentType != warp.getType()) {
                currentType = warp.getType();
                player.sendMessage("");
                player.sendMessage("§6" + currentType.getIcon() + " " + currentType.getDisplayName() + ":");
            }

            boolean canAccess = plugin.getWarpManager().canAccessWarp(player, warp);
            String accessIcon = canAccess ? "§a✓" : "§c✗";
            String command = warp.getType() == WarpData.WarpType.MINE ? "/mine " : "/warp ";
            String warpId = warp.getType() == WarpData.WarpType.MINE
                    ? warp.getId().substring(5) : warp.getId();

            player.sendMessage("§7  " + accessIcon + " " + warp.getFormattedName() +
                    " §8(" + command + warpId + ")");
        }

        player.sendMessage("");
        player.sendMessage("§7Utilisez §e/warp gui §7pour une interface graphique");
    }

    /**
     * Affiche les informations d'un warp
     */
    private void handleWarpInfo(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /warp info <nom_warp>");
            return;
        }

        String warpName = args[1];
        WarpData warp = plugin.getWarpManager().findWarp(warpName);

        if (warp == null) {
            player.sendMessage("§c❌ Warp introuvable: §e" + warpName);
            return;
        }

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6         📍 " + warp.getDisplayName());
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§7ID: §e" + warp.getId());
        player.sendMessage("§7Type: " + warp.getType().getIcon() + " §e" + warp.getType().getDisplayName());
        player.sendMessage("§7Monde: §e" + warp.getWorldName());
        player.sendMessage("§7Position: §e" + (int) warp.getX() + ", " + (int) warp.getY() + ", " + (int) warp.getZ());

        if (warp.getPermission() != null && !warp.getPermission().isEmpty()) {
            player.sendMessage("§7Permission: §e" + warp.getPermission());
        }

        player.sendMessage("§7Description: " + warp.getFormattedDescription());

        boolean canAccess = plugin.getWarpManager().canAccessWarp(player, warp);
        player.sendMessage("§7Accès: " + (canAccess ? "§a✓ Autorisé" : "§c✗ Refusé"));

        if (canAccess) {
            String command = warp.getType() == WarpData.WarpType.MINE ? "/mine " : "/warp ";
            String warpId = warp.getType() == WarpData.WarpType.MINE
                    ? warp.getId().substring(5) : warp.getId();
            player.sendMessage("§7Commande: §e" + command + warpId);
        }
    }

    /**
     * Affiche l'aide des commandes warp
     */
    private void sendWarpHelp(Player player) {
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6         🌟 AIDE WARPS");
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§e/warp §7- Ouvre le menu des warps");
        player.sendMessage("§e/warp <nom> §7- Se téléporte à un warp");
        player.sendMessage("§e/warp list [type] §7- Liste les warps");
        player.sendMessage("§e/warp info <nom> §7- Infos d'un warp");
        player.sendMessage("§e/warp gui §7- Ouvre l'interface graphique");
        player.sendMessage("");
        player.sendMessage("§e/mine [nom] §7- Se téléporte à une mine");
        player.sendMessage("§e/spawn §7- Retourne au spawn");
        player.sendMessage("");
        player.sendMessage("§7Types de warps: §eSpawn§7, §eMine§7, §eCrate§7, §eCave§7,");
        player.sendMessage("§eShop§7, §ePvP§7, §eEvent§7, §eOther");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();
        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "warp" -> {
                if (args.length == 1) {
                    // Sous-commandes et noms de warps
                    List<String> subCommands = List.of("list", "info", "gui", "help");
                    StringUtil.copyPartialMatches(args[0], subCommands, completions);

                    // Ajouter les warps accessibles
                    List<String> warpNames = plugin.getWarpManager().getAccessibleWarps(player).stream()
                            .map(WarpData::getId)
                            .collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[0], warpNames, completions);
                } else if (args.length == 2) {
                    if ("list".equals(args[0])) {
                        // Types de warps pour /warp list
                        List<String> types = List.of("spawn", "mine", "crate", "cave", "shop", "pvp", "event", "other");
                        StringUtil.copyPartialMatches(args[1], types, completions);
                    } else if ("info".equals(args[0])) {
                        // Noms de warps pour /warp info
                        List<String> warpNames = plugin.getWarpManager().getAllWarps().stream()
                                .map(WarpData::getId)
                                .collect(Collectors.toList());
                        StringUtil.copyPartialMatches(args[1], warpNames, completions);
                    }
                }
            }
            case "mine" -> {
                if (args.length == 1) {
                    // Noms des mines
                    List<String> mineNames = plugin.getWarpManager().getWarpsByType(WarpData.WarpType.MINE).stream()
                            .filter(warp -> plugin.getWarpManager().canAccessWarp(player, warp))
                            .map(WarpData::getId)
                            .collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[0], mineNames, completions);
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}