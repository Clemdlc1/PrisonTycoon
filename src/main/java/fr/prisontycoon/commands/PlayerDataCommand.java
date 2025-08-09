package fr.prisontycoon.commands;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Type;
import java.util.*;

public class PlayerDataCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;
    private final Gson gson = new Gson();

    public PlayerDataCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            sender.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§eUsage: /" + label + " <save|load|dump> <joueur> [colonne]");
            sender.sendMessage("§7Colonnes supportées: profession_levels, profession_xp, talent_levels, kit_levels, profession_rewards");
            return true;
        }

        String action = args[0].toLowerCase();
        String targetName = args[1];
        String column = args.length >= 3 ? args[2].toLowerCase() : null;

        UUID targetId = resolvePlayerUUID(targetName);
        if (targetId == null) {
            sender.sendMessage("§cJoueur introuvable: §e" + targetName);
            return true;
        }

        switch (action) {
            case "save" -> {
                if (column == null) {
                    // Sauvegarde complète
                    ensureCached(targetId);
                    plugin.getPlayerDataManager().savePlayerNow(targetId);
                    sender.sendMessage("§aSauvegardé: §e" + targetName);
                } else {
                    boolean ok = saveSingleColumn(targetId, column);
                    if (ok) sender.sendMessage("§aSauvegardé colonne §e" + column + " §apour §e" + targetName);
                    else sender.sendMessage("§cColonne inconnue: §e" + column);
                }
            }
            case "load" -> {
                if (column == null) {
                    plugin.getPlayerDataManager().reloadPlayerData(targetId);
                    sender.sendMessage("§aRechargé depuis la base: §e" + targetName);
                } else {
                    boolean ok = loadSingleColumn(targetId, column);
                    if (ok) sender.sendMessage("§aRechargé colonne §e" + column + " §apour §e" + targetName);
                    else sender.sendMessage("§cColonne inconnue: §e" + column);
                }
            }
            case "dump" -> {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(targetId);
                String activeProfession = data.getActiveProfession();
                sender.sendMessage("§7--- §ePlayerData Dump§7 ---");
                sender.sendMessage("§7Joueur: §e" + data.getPlayerName() + " §8(" + targetId + ")");
                sender.sendMessage("§7Métier actif: §e" + (activeProfession == null ? "aucun" : activeProfession));
                sender.sendMessage("§7Niveaux métiers: §e" + data.getAllProfessionLevels());
                sender.sendMessage("§7XP métiers: §e" + data.getAllProfessionXP());
                sender.sendMessage("§7Talents: §e" + data.getAllTalentLevels().keySet());
            }
            default -> sender.sendMessage("§cAction inconnue. Utilisez: save, load, dump");
        }

        return true;
    }

    private UUID resolvePlayerUUID(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) return offline.getUniqueId();
        return null;
    }

    private void ensureCached(UUID playerId) {
        plugin.getPlayerDataManager().getPlayerData(playerId);
    }

    private boolean saveSingleColumn(UUID playerId, String column) {
        // On persiste la colonne en mettant à jour uniquement cette colonne via une requête dédiée
        // Pour rester simple ici: on sauvegarde d'abord en cache puis on exécute un UPDATE ciblé.
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);
        String json;
        String sql;

        switch (column) {
            case "profession_levels" -> {
                json = new Gson().toJson(data.getAllProfessionLevels());
                sql = "UPDATE players SET profession_levels = ? WHERE uuid = ?";
            }
            case "profession_xp" -> {
                json = new Gson().toJson(data.getAllProfessionXP());
                sql = "UPDATE players SET profession_xp = ? WHERE uuid = ?";
            }
            case "talent_levels" -> {
                json = new Gson().toJson(data.getAllTalentLevels());
                sql = "UPDATE players SET talent_levels = ? WHERE uuid = ?";
            }
            case "kit_levels" -> {
                json = new Gson().toJson(data.getAllKitLevels());
                sql = "UPDATE players SET kit_levels = ? WHERE uuid = ?";
            }
            case "profession_rewards" -> {
                json = new Gson().toJson(data.getAllClaimedProfessionRewards());
                sql = "UPDATE players SET profession_rewards = ? WHERE uuid = ?";
            }
            default -> {
                return false;
            }
        }

        try (var conn = plugin.getDatabaseManager().getConnection(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, json);
            ps.setString(2, playerId.toString());
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur saveSingleColumn(" + column + "): " + e.getMessage());
            return false;
        }
    }

    private boolean loadSingleColumn(UUID playerId, String column) {
        String sql;
        Type mapStrInt = new TypeToken<Map<String, Integer>>(){}.getType();
        Type mapStrMapStrInt = new TypeToken<Map<String, Map<String, Integer>>>(){}.getType();
        Type mapStrSetInt = new TypeToken<Map<String, Set<Integer>>>(){}.getType();

        switch (column) {
            case "profession_levels" -> sql = "SELECT profession_levels AS col FROM players WHERE uuid = ?";
            case "profession_xp" -> sql = "SELECT profession_xp AS col FROM players WHERE uuid = ?";
            case "talent_levels" -> sql = "SELECT talent_levels AS col FROM players WHERE uuid = ?";
            case "kit_levels" -> sql = "SELECT kit_levels AS col FROM players WHERE uuid = ?";
            case "profession_rewards" -> sql = "SELECT profession_rewards AS col FROM players WHERE uuid = ?";
            default -> { return false; }
        }

        try (var conn = plugin.getDatabaseManager().getConnection(); var ps = conn.prepareStatement(sql)) {
            ps.setString(1, playerId.toString());
            try (var rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                String json = rs.getString("col");
                if (json == null || json.isBlank() || json.equals("null")) return true; // rien à charger

                PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerId);

                switch (column) {
                    case "profession_levels" -> {
                        Map<String, Integer> levels = gson.fromJson(json, mapStrInt);
                        Map<String, Integer> cleaned = new HashMap<>();
                        for (var e : levels.entrySet()) {
                            if (plugin.getProfessionManager().getProfession(e.getKey()) != null) {
                                cleaned.put(e.getKey(), e.getValue());
                            }
                        }
                        data.setAllProfessionLevels(cleaned);
                    }
                    case "profession_xp" -> {
                        Map<String, Integer> xpMap = gson.fromJson(json, mapStrInt);
                        Map<String, Integer> cleaned = new HashMap<>();
                        for (var e : xpMap.entrySet()) {
                            if (plugin.getProfessionManager().getProfession(e.getKey()) != null) {
                                cleaned.put(e.getKey(), e.getValue());
                            }
                        }
                        data.setAllProfessionXP(cleaned);
                    }
                    case "talent_levels" -> {
                        Map<String, Map<String, Integer>> talents = gson.fromJson(json, mapStrMapStrInt);
                        data.setTalentLevels(talents);
                    }
                    case "kit_levels" -> {
                        Map<String, Integer> kits = gson.fromJson(json, mapStrInt);
                        data.setKitLevels(kits);
                    }
                    case "profession_rewards" -> {
                        Map<String, Set<Integer>> rewards = gson.fromJson(json, mapStrSetInt);
                        data.setClaimedProfessionRewards(rewards);
                    }
                }

                // marque le joueur dirty pour qu'une sauvegarde complète assainisse la BDD
                plugin.getPlayerDataManager().markDirty(playerId);
                return true;
            }
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Erreur loadSingleColumn(" + column + "): " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("specialmine.admin")) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> sub = Arrays.asList("save", "load", "dump");
            StringUtil.copyPartialMatches(args[0], sub, completions);
        } else if (args.length == 2) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                players.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[1], players, completions);
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("load"))) {
            List<String> columns = Arrays.asList("profession_levels", "profession_xp", "talent_levels", "kit_levels", "profession_rewards");
            StringUtil.copyPartialMatches(args[2], columns, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}