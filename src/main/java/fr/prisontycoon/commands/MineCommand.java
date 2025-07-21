package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.MineManager;
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
import java.util.stream.Collectors;

/**
 * Commande pour gérer les mines (normale, prestige, VIP)
 * Usage: /mine <list|tp|info|generate|types> [mine] [args]
 */
public class MineCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public MineCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "list", "liste" -> handleListCommand(player, args);
            case "tp", "teleport", "warp" -> handleTeleportCommand(player, args);
            case "info", "informations" -> handleInfoCommand(player, args);
            case "generate", "gen", "reset" -> handleGenerateCommand(player, args);
            case "types", "categories" -> handleTypesCommand(player);
            case "accessible", "access" -> handleAccessibleCommand(player);
            case "help", "aide" -> sendHelpMessage(player);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * Gère /mine list [type]
     */
    private void handleListCommand(Player player, String[] args) {
        MineManager.MineType filterType = null;

        // Filtrage par type si spécifié
        if (args.length >= 2) {
            try {
                filterType = MineManager.MineType.valueOf(args[1].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage("§c❌ Type de mine invalide! Types disponibles: NORMAL, PRESTIGE, VIP");
                return;
            }
        }

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6         📍 LISTE DES MINES");
        if (filterType != null) {
            player.sendMessage("§6         (Type: " + filterType.toString() + ")");
        }
        player.sendMessage("§6═══════════════════════════════════");

        List<MineManager.MineData> mines;
        if (filterType != null) {
            mines = plugin.getMineManager().getMinesByType(filterType);
        } else {
            mines = new ArrayList<>(plugin.getMineManager().getAllMines());
            mines.sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));
        }

        if (mines.isEmpty()) {
            player.sendMessage("§7Aucune mine trouvée.");
            return;
        }

        for (MineManager.MineData mine : mines) {
            boolean canAccess = plugin.getMineManager().canAccessMine(player, mine.getId());
            String accessIcon = canAccess ? "§a✅" : "§c🔒";
            String typeColor = getTypeColor(mine.getType());

            String beaconInfo = "";
            if (mine.hasBeacons()) {
                beaconInfo = String.format(" §e(%.1f%% beacons)", mine.getBeaconRate());
            }

            player.sendMessage(String.format("%s %s%s §7- %s%s",
                    accessIcon, typeColor, mine.getDisplayName(), mine.getDescription(), beaconInfo));
        }

        player.sendMessage("§7Utilisez §6/mine tp <nom> §7pour vous téléporter");
        player.sendMessage("§7Utilisez §6/mine info <nom> §7pour plus d'informations");
    }

    /**
     * Gère /mine tp <mine>
     */
    private void handleTeleportCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /mine tp <nom_mine>");
            return;
        }

        String mineName = args[1].toLowerCase();

        // Essayer de trouver la mine (avec ou sans préfixe "mine-")
        String mineId = mineName.startsWith("mine-") ? mineName : "mine-" + mineName;

        // Vérifier les autres formats (prestige, vip)
        String finalMineId = mineId;
        if (!plugin.getMineManager().getAllMines().stream().anyMatch(m -> m.getId().equals(finalMineId))) {
            // Essayer les formats alternatifs
            List<String> possibleIds = Arrays.asList(
                    mineName,
                    "mine-" + mineName,
                    "mine-prestige" + mineName,
                    "mine-vip" + mineName
            );

            String foundId = null;
            for (String possibleId : possibleIds) {
                if (plugin.getMineManager().getAllMines().stream().anyMatch(m -> m.getId().equals(possibleId))) {
                    foundId = possibleId;
                    break;
                }
            }

            if (foundId == null) {
                player.sendMessage("§c❌ Mine introuvable: " + mineName);
                player.sendMessage("§7Utilisez §6/mine list §7pour voir toutes les mines");
                return;
            }

            mineId = foundId;
        }

        boolean success = plugin.getMineManager().teleportToMine(player, mineId);
        if (!success) {
            player.sendMessage("§c❌ Impossible de se téléporter à cette mine!");
        }
    }

    /**
     * Gère /mine info <mine>
     */
    private void handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /mine info <nom_mine>");
            return;
        }

        String mineName = args[1].toLowerCase();
        String mineId = mineName.startsWith("mine-") ? mineName : "mine-" + mineName;

        // Chercher la mine (même logique que teleport)
        String finalMineId = mineId;
        if (!plugin.getMineManager().getAllMines().stream().anyMatch(m -> m.getId().equals(finalMineId))) {
            List<String> possibleIds = Arrays.asList(
                    mineName,
                    "mine-" + mineName,
                    "mine-prestige" + mineName,
                    "mine-vip" + mineName
            );

            String foundId = null;
            for (String possibleId : possibleIds) {
                if (plugin.getMineManager().getAllMines().stream().anyMatch(m -> m.getId().equals(possibleId))) {
                    foundId = possibleId;
                    break;
                }
            }

            if (foundId == null) {
                player.sendMessage("§c❌ Mine introuvable: " + mineName);
                return;
            }

            mineId = foundId;
        }

        String info = plugin.getMineManager().getMineInfo(player, mineId);
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage(info);
        player.sendMessage("§6═══════════════════════════════════");
    }

    /**
     * Gère /mine generate <mine> (admin seulement)
     */
    private void handleGenerateCommand(Player player, String[] args) {
        if (!player.hasPermission("specialmine.admin")) {
            player.sendMessage("§c❌ Vous n'avez pas la permission!");
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§c❌ Usage: /mine generate <nom_mine|all>");
            return;
        }

        String target = args[1].toLowerCase();

        if (target.equals("all")) {
            player.sendMessage("§e🔄 Génération de toutes les mines en cours...");

            for (MineManager.MineData mine : plugin.getMineManager().getAllMines()) {
                plugin.getMineManager().generateMine(mine.getId());
            }

            player.sendMessage("§a✅ Toutes les mines ont été régénérées!");
            return;
        }

        // Génération d'une mine spécifique
        String mineId = target.startsWith("mine-") ? target : "mine-" + target;

        if (!plugin.getMineManager().getAllMines().stream().anyMatch(m -> m.getId().equals(mineId))) {
            player.sendMessage("§c❌ Mine introuvable: " + target);
            return;
        }

        player.sendMessage("§e🔄 Génération de la mine " + mineId + "...");
        plugin.getMineManager().generateMine(mineId);
        player.sendMessage("§a✅ Mine générée avec succès!");
    }

    /**
     * Gère /mine types
     */
    private void handleTypesCommand(Player player) {
        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6         📊 TYPES DE MINES");
        player.sendMessage("§6═══════════════════════════════════");

        // Compter les mines par type
        List<MineManager.MineData> normalMines = plugin.getMineManager().getMinesByType(MineManager.MineType.NORMAL);
        List<MineManager.MineData> prestigeMines = plugin.getMineManager().getMinesByType(MineManager.MineType.PRESTIGE);
        List<MineManager.MineData> vipMines = plugin.getMineManager().getMinesByType(MineManager.MineType.VIP);

        player.sendMessage("§f📍 NORMALES §7(" + normalMines.size() + " mines)");
        player.sendMessage("§7  Mines standard de A à Z");
        player.sendMessage("§7  Débloquées par rankup");

        player.sendMessage("");
        player.sendMessage("§d📍 PRESTIGE §7(" + prestigeMines.size() + " mines)");
        player.sendMessage("§7  Mines exclusives aux joueurs prestige");
        player.sendMessage("§7  Contiennent des beacons rares");

        player.sendMessage("");
        player.sendMessage("§6📍 VIP §7(" + vipMines.size() + " mines)");
        player.sendMessage("§7  Mines exclusives aux VIP");
        player.sendMessage("§7  Ressources de haute qualité");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        player.sendMessage("");
        player.sendMessage("§e📊 Votre accès:");
        player.sendMessage("§7• Rang actuel: " + plugin.getMineManager().getRankColor(plugin.getMineManager().getCurrentRank(player)) + plugin.getMineManager().getCurrentRank(player).toUpperCase());
        player.sendMessage("§7• Prestige: " + playerData.getPrestigeDisplayName());
        player.sendMessage("§7• VIP: " + (player.hasPermission("specialmine.vip") ? "§a✅" : "§c❌"));
    }

    /**
     * Gère /mine accessible
     */
    private void handleAccessibleCommand(Player player) {
        List<MineManager.MineData> accessibleMines = plugin.getMineManager().getAccessibleMines(player);

        player.sendMessage("§6═══════════════════════════════════");
        player.sendMessage("§6      🔓 MINES ACCESSIBLES");
        player.sendMessage("§6═══════════════════════════════════");

        if (accessibleMines.isEmpty()) {
            player.sendMessage("§7Aucune mine accessible.");
            return;
        }

        // Grouper par type
        List<MineManager.MineData> normalAccessible = accessibleMines.stream()
                .filter(m -> m.getType() == MineManager.MineType.NORMAL)
                .collect(Collectors.toList());

        List<MineManager.MineData> prestigeAccessible = accessibleMines.stream()
                .filter(m -> m.getType() == MineManager.MineType.PRESTIGE)
                .collect(Collectors.toList());

        List<MineManager.MineData> vipAccessible = accessibleMines.stream()
                .filter(m -> m.getType() == MineManager.MineType.VIP)
                .collect(Collectors.toList());

        if (!normalAccessible.isEmpty()) {
            player.sendMessage("§f📍 Mines normales:");
            for (MineManager.MineData mine : normalAccessible) {
                player.sendMessage("§7  • " + mine.getDisplayName());
            }
        }

        if (!prestigeAccessible.isEmpty()) {
            player.sendMessage("§d📍 Mines prestige:");
            for (MineManager.MineData mine : prestigeAccessible) {
                String beacons = mine.hasBeacons() ? String.format(" §e(%.1f%% beacons)", mine.getBeaconRate()) : "";
                player.sendMessage("§7  • " + mine.getDisplayName() + beacons);
            }
        }

        if (!vipAccessible.isEmpty()) {
            player.sendMessage("§6📍 Mines VIP:");
            for (MineManager.MineData mine : vipAccessible) {
                String beacons = mine.hasBeacons() ? String.format(" §e(%.1f%% beacons)", mine.getBeaconRate()) : "";
                player.sendMessage("§7  • " + mine.getDisplayName() + beacons);
            }
        }

        player.sendMessage("");
        player.sendMessage("§7Total: §6" + accessibleMines.size() + " §7mines accessibles");
    }

    /**
     * Obtient la couleur d'affichage d'un type de mine
     */
    private String getTypeColor(MineManager.MineType type) {
        return switch (type) {
            case NORMAL -> "§f";
            case PRESTIGE -> "§d";
            case VIP -> "§6";
        };
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§6§l╔═══════════════════════════════════╗");
        player.sendMessage("§6§l║           §e⛏ MINES ⛏             §6§l║");
        player.sendMessage("§6§l╠═══════════════════════════════════╣");
        player.sendMessage("§6§l║ §e/mine list [type]               §6§l║");
        player.sendMessage("§6§l║ §7├─ Liste toutes les mines        §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/mine tp <mine>                 §6§l║");
        player.sendMessage("§6§l║ §7├─ Se téléporter à une mine      §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/mine info <mine>               §6§l║");
        player.sendMessage("§6§l║ §7├─ Informations sur une mine     §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/mine types                     §6§l║");
        player.sendMessage("§6§l║ §7├─ Types de mines disponibles    §6§l║");
        player.sendMessage("§6§l║                                   §6§l║");
        player.sendMessage("§6§l║ §e/mine accessible                §6§l║");
        player.sendMessage("§6§l║ §7├─ Mines que vous pouvez visiter §6§l║");

        if (player.hasPermission("specialmine.admin")) {
            player.sendMessage("§6§l║                                   §6§l║");
            player.sendMessage("§6§l║ §c/mine generate <mine|all>       §6§l║");
            player.sendMessage("§6§l║ §7├─ Régénérer une/toutes mines   §6§l║");
        }

        player.sendMessage("§6§l╚═══════════════════════════════════╝");

        // Statistiques rapides
        int totalMines = plugin.getMineManager().getAllMines().size();
        int accessibleMines = plugin.getMineManager().getAccessibleMines(player).size();

        player.sendMessage("");
        player.sendMessage("§7📊 Mines disponibles: §6" + accessibleMines + "§7/§6" + totalMines);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList(
                    "list", "tp", "info", "types", "accessible", "help"
            ));

            if (sender.hasPermission("specialmine.admin")) {
                subCommands.add("generate");
            }

            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "list" -> {
                    List<String> types = Arrays.asList("normal", "prestige", "vip");
                    StringUtil.copyPartialMatches(args[1], types, completions);
                }
                case "tp", "info", "generate" -> {
                    // Suggérer les noms de mines
                    List<String> mineNames = plugin.getMineManager().getAllMines().stream()
                            .map(mine -> mine.getId().replace("mine-", ""))
                            .collect(Collectors.toList());

                    StringUtil.copyPartialMatches(args[1], mineNames, completions);

                    // Ajouter "all" pour generate
                    if (args[0].toLowerCase().equals("generate")) {
                        StringUtil.copyPartialMatches(args[1], List.of("all"), completions);
                    }
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}