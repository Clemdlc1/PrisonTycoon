package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.ProfessionManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * COMMANDE MÉTIER FUSIONNÉE - Combine toutes les fonctionnalités
 * - /metier : ouvre le GUI
 * - /metier info : informations sur le métier
 * - /metier choisir <métier> : premier choix gratuit
 * - /metier changemetier <métier> : changement payant
 * - /metier metierxp <nombre> : admin - donner XP métier
 * - /metier help : aide complète
 */
public class MetierCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public MetierCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Vérification du rang F pour les sous-commandes non-admin
        boolean isAdmin = player.hasPermission("specialmine.admin");
        boolean needsRankCheck = args.length == 0 ||
                (!args[0].equalsIgnoreCase("metierxp") && !args[0].equalsIgnoreCase("help"));

        if (needsRankCheck && !plugin.getProfessionManager().canUnlockProfessions(player)) {
            player.sendMessage("§c❌ Les métiers se débloquent au rang F !");
            String currentRank = plugin.getMineManager().getCurrentRank(player);
            player.sendMessage("§7Votre rang actuel: §e" + currentRank.toUpperCase());
            return true;
        }

        // Commande sans arguments : ouvre le GUI
        if (args.length == 0) {
            plugin.getProfessionGUI().openProfessionMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "info" -> {
                showProfessionInfo(player);
                yield true;
            }
            case "choisir" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /metier choisir <mineur|commercant|guerrier>");
                    yield true;
                }

                String professionId = args[1].toLowerCase();
                if (!plugin.getProfessionManager().setActiveProfession(player, professionId)) {
                    PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    if (playerData.getActiveProfession() != null) {
                        player.sendMessage("§cVous avez déjà un métier ! Utilisez §e/metier changemetier §cpour en changer.");
                    } else {
                        player.sendMessage("§cMétier invalide ! Métiers disponibles: mineur, commercant, guerrier");
                    }
                }
                yield true;
            }
            case "changemetier", "changer" -> {
                if (args.length < 2) {
                    sendChangeUsageMessage(player);
                    yield true;
                }

                String professionId = args[1].toLowerCase();
                plugin.getProfessionManager().changeProfession(player, professionId);
                yield true;
            }
            case "metierxp", "xp" -> {
                if (!isAdmin) {
                    player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
                    yield true;
                }

                if (args.length < 2) {
                    player.sendMessage("§cUsage: /metier metierxp <nombre>");
                    yield true;
                }

                yield handleAdminXP(player, args[1]);
            }
            case "help", "aide" -> {
                sendHelpMessage(player);
                yield true;
            }
            default -> {
                player.sendMessage("§cCommande inconnue ! Utilisez §e/metier help §cpour l'aide.");
                yield true;
            }
        };
    }

    /**
     * Affiche les informations sur le métier actuel du joueur
     */
    private void showProfessionInfo(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        if (activeProfession == null) {
            player.sendMessage("§c❌ Vous n'avez pas encore choisi de métier !");
            player.sendMessage("§7Utilisez §e/metier choisir <métier> §7pour en choisir un.");
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

        // En-tête avec style
        player.sendMessage("");
        player.sendMessage("§e╔══════════════════════════════════════╗");
        player.sendMessage("§e║     §l📋 INFORMATIONS MÉTIER §r§e     ║");
        player.sendMessage("§e╚══════════════════════════════════════╝");
        player.sendMessage("");

        // Informations principales
        player.sendMessage("§6✦ §lMétier: " + profession.displayName());
        player.sendMessage("§7" + profession.description());
        player.sendMessage("");

        // Progression
        player.sendMessage("§e⚡ §lProgression:");
        player.sendMessage("§7Niveau: §e" + level + "§7/§e10");
        player.sendMessage("§7XP: §e" + NumberFormatter.format(xp) + "§7/§e" +
                (level < 10 ? NumberFormatter.format(nextLevelXP) : "MAX"));

        // Barre de progression
        if (level < 10) {
            int progress = Math.min(20, (int) ((double) xp / nextLevelXP * 20));
            StringBuilder bar = new StringBuilder("§7[");
            for (int i = 0; i < 20; i++) {
                if (i < progress) {
                    bar.append("§a█");
                } else {
                    bar.append("§7░");
                }
            }
            bar.append("§7] ").append("§e").append((int) ((double) xp / nextLevelXP * 100)).append("%");
            player.sendMessage(bar.toString());
        } else {
            player.sendMessage("§a[████████████████████] §e100% §a(MAX)");
        }

        player.sendMessage("");

        // Talents et kit
        List<ProfessionManager.ProfessionTalent> talents = profession.talents();
        int kitLevel = playerData.getKitLevel(activeProfession);

        player.sendMessage("§5⭐ §lTalents & Kit:");
        int talentCount = 0;
        for (ProfessionManager.ProfessionTalent talent : talents) {
            int talentLevel = playerData.getTalentLevel(activeProfession, talent.id());
            player.sendMessage("§7• " + talent.displayName() + ": §e" + talentLevel + "§7/§e10");
            talentCount++;
            if (talentCount >= 3) break; // Limite à 3 talents affichés
        }
        player.sendMessage("§7• §6Kit de métier: §e" + kitLevel + "§7/§e10");

        player.sendMessage("");
        player.sendMessage("§e💡 §7Utilisez §e/metier §7pour ouvrir le menu détaillé !");
        player.sendMessage("");
    }

    /**
     * Gère la commande admin de XP métier
     */
    private boolean handleAdminXP(Player player, String amountStr) {
        try {
            int xpAmount = Integer.parseInt(amountStr);

            if (xpAmount <= 0) {
                player.sendMessage("§cLe nombre doit être positif !");
                return true;
            }

            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            String activeProfession = playerData.getActiveProfession();

            if (activeProfession == null) {
                player.sendMessage("§c❌ Vous devez avoir un métier actif !");
                return true;
            }

            // Ajoute l'XP métier
            plugin.getProfessionManager().addProfessionXP(player, activeProfession, xpAmount);

            player.sendMessage("§a✅ Vous avez reçu §e" + NumberFormatter.format(xpAmount) +
                    " XP §apour le métier §e" + activeProfession + " §a!");

        } catch (NumberFormatException e) {
            player.sendMessage("§cNombre invalide !");
        }

        return true;
    }

    /**
     * Affiche l'usage pour le changement de métier
     */
    private void sendChangeUsageMessage(Player player) {
        player.sendMessage("§e💰 §lChangement de Métier");
        player.sendMessage("");
        player.sendMessage("§6Usage: §e/metier changemetier <métier>");
        player.sendMessage("");
        player.sendMessage("§7Métiers disponibles:");
        player.sendMessage("§7• §a§lmineur §7- Maître de l'extraction");
        player.sendMessage("§7• §6§lcommercant §7- Maître de l'économie");
        player.sendMessage("§7• §c§lguerrier §7- Maître du combat");
        player.sendMessage("");
        player.sendMessage("§c💸 Coût: §e5000 beacons");
        player.sendMessage("§c⏰ Cooldown: §e24 heures");
        player.sendMessage("");
        player.sendMessage("§e💡 Votre progression dans chaque métier est conservée !");
    }

    /**
     * Affiche l'aide complète de la commande
     */
    private void sendHelpMessage(Player player) {
        boolean isAdmin = player.hasPermission("specialmine.admin");

        player.sendMessage("");
        player.sendMessage("§e╔══════════════════════════════════════╗");
        player.sendMessage("§e║       §l📖 AIDE - SYSTÈME MÉTIERS §r§e    ║");
        player.sendMessage("§e╚══════════════════════════════════════╝");
        player.sendMessage("");

        player.sendMessage("§6📋 §lCommandes joueur:");
        player.sendMessage("§e/metier §7- Ouvre le menu des métiers");
        player.sendMessage("§e/metier info §7- Informations sur votre métier");
        player.sendMessage("§e/metier choisir <métier> §7- Choisit votre premier métier (gratuit)");
        player.sendMessage("§e/metier changemetier <métier> §7- Change de métier (payant)");
        player.sendMessage("§e/metier help §7- Affiche cette aide");

        if (isAdmin) {
            player.sendMessage("");
            player.sendMessage("§c⚒ §lCommandes admin:");
            player.sendMessage("§c/metier metierxp <nombre> §7- Donne de l'XP métier");
        }

        player.sendMessage("");
        player.sendMessage("§e💡 §lMétiers disponibles:");
        player.sendMessage("§7• §a§lMineur §7- Maître de l'extraction et du minage");
        player.sendMessage("§7• §6§lCommerçant §7- Maître de l'économie et des échanges");
        player.sendMessage("§7• §c§lGuerrier §7- Maître du combat et de la défense");

        player.sendMessage("");
        player.sendMessage("§e🎯 §lPré-requis:");
        player.sendMessage("§7• Débloquage: §eRang F §7requis");
        player.sendMessage("§7• Premier choix: §aGratuit");
        player.sendMessage("§7• Changement: §e5000 beacons §7+ §e24h cooldown");

        player.sendMessage("");
        player.sendMessage("§e⭐ §lFonctionnalités:");
        player.sendMessage("§7• Progression sauvegardée par métier");
        player.sendMessage("§7• Talents améliorables (coût XP joueur)");
        player.sendMessage("§7• Kit d'équipement évolutif");
        player.sendMessage("§7• Avantages passifs uniques");

        player.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("info", "choisir", "changemetier", "help"));

            // Ajoute les commandes admin si le joueur a la permission
            if (sender.hasPermission("specialmine.admin")) {
                subCommands.add("metierxp");
            }

            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("choisir") || args[0].equalsIgnoreCase("changemetier")) {
                List<String> professions = Arrays.asList("mineur", "commercant", "guerrier");
                StringUtil.copyPartialMatches(args[1], professions, completions);
            } else if (args[0].equalsIgnoreCase("metierxp") && sender.hasPermission("specialmine.admin")) {
                List<String> amounts = Arrays.asList("100", "500", "1000", "5000", "10000");
                StringUtil.copyPartialMatches(args[1], amounts, completions);
            }
        }

        Collections.sort(completions);
        return completions;
    }
}