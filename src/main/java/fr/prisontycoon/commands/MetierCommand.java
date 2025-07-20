package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.ProfessionManager;
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
 * Commande /metier - Ouvre le menu des métiers
 */
public class MetierCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public MetierCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Vérification du rang F
        if (!plugin.getProfessionManager().canUnlockProfessions(player)) {
            player.sendMessage("§c❌ Les métiers se débloquent au rang F !");
            String currentRank = plugin.getMineManager().getCurrentRank(player);
            player.sendMessage("§7Votre rang actuel: §e" + currentRank.toUpperCase());
            return true;
        }

        if (args.length == 0) {
            // Ouvre le GUI des métiers
            plugin.getProfessionGUI().openProfessionMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> {
                showProfessionInfo(player);
                return true;
            }

            case "choisir" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /metier choisir <mineur|commercant|guerrier>");
                    return true;
                }

                String professionId = args[1].toLowerCase();
                if (plugin.getProfessionManager().setActiveProfession(player, professionId)) {
                    // Succès géré dans le manager
                } else {
                    PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    if (playerData.getActiveProfession() != null) {
                        player.sendMessage("§cVous avez déjà un métier ! Utilisez §e/changemetier §cpour en changer.");
                    } else {
                        player.sendMessage("§cMétier invalide ! Métiers disponibles: mineur, commercant, guerrier");
                    }
                }
                return true;
            }

            case "help" -> {
                sendHelpMessage(player);
                return true;
            }

            default -> {
                player.sendMessage("§cCommande inconnue ! Utilisez §e/metier help §cpour l'aide.");
                return true;
            }
        }
    }

    /**
     * Affiche les informations sur le métier actuel du joueur
     */
    private void showProfessionInfo(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null) {
            player.sendMessage("§c❌ Vous n'avez pas encore choisi de métier !");
            player.sendMessage("§7Utilisez §e/metier §7pour en choisir un.");
            return;
        }

        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(activeProfession);
        if (profession == null) {
            player.sendMessage("§cErreur: métier introuvable !");
            return;
        }

        int level = playerData.getProfessionLevel(activeProfession);
        int xp = playerData.getProfessionXP(activeProfession);
        int nextLevelXP = plugin.getProfessionManager().getXPForNextLevel(level);

        player.sendMessage("");
        player.sendMessage("§e📋 §lInformations sur votre métier");
        player.sendMessage("§7Métier actif: " + profession.getDisplayName());
        player.sendMessage("§7" + profession.getDescription());
        player.sendMessage("");
        player.sendMessage("§7Niveau: §e" + level + "§7/§e10");
        player.sendMessage("§7XP: §e" + xp + "§7/§e" + (level < 10 ? nextLevelXP : "MAX"));

        if (level < 10) {
            int progress = (int) ((double) xp / nextLevelXP * 20);
            StringBuilder bar = new StringBuilder("§7[");
            for (int i = 0; i < 20; i++) {
                if (i < progress) {
                    bar.append("§a█");
                } else {
                    bar.append("§7░");
                }
            }
            bar.append("§7]");
            player.sendMessage(bar.toString());
        }

        player.sendMessage("");
        player.sendMessage("§7Utilisez §e/metier §7pour ouvrir le menu détaillé !");
        player.sendMessage("");
    }

    /**
     * Affiche l'aide de la commande
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§e📖 §lAide - Système de Métiers");
        player.sendMessage("");
        player.sendMessage("§6/metier §7- Ouvre le menu des métiers");
        player.sendMessage("§6/metier info §7- Informations sur votre métier");
        player.sendMessage("§6/metier choisir <métier> §7- Choisit votre premier métier (gratuit)");
        player.sendMessage("§6/changemetier <métier> §7- Change de métier (5000 beacons, 24h cooldown)");
        player.sendMessage("");
        player.sendMessage("§e💡 §7Métiers disponibles:");
        player.sendMessage("§7• §a§lMineur §7- Maître de l'extraction");
        player.sendMessage("§7• §6§lCommerçant §7- Maître de l'économie");
        player.sendMessage("§7• §c§lGuerrier §7- Maître du combat");
        player.sendMessage("");
        player.sendMessage("§e🎯 §7Débloquage: §eRang F §7requis");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("info", "choisir", "help");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("choisir")) {
            List<String> professions = Arrays.asList("mineur", "commercant", "guerrier");
            StringUtil.copyPartialMatches(args[1], professions, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}