package fr.prisoncore.prisoncore.prisonTycoon.commands;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.crystals.Crystal;
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
 * Commande /cristal pour obtenir des cristaux
 */
public class CrystalCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public CrystalCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        // Vérifie la permission admin
        if (!player.hasPermission("specialmine.admin")) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length < 1) {
            sendHelpMessage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give", "donner" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /cristal give <niveau> [joueur]");
                    return true;
                }

                int level;
                try {
                    level = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cNiveau invalide: " + args[1]);
                    return true;
                }

                if (level < 1 || level > 20) {
                    player.sendMessage("§cLe niveau doit être entre 1 et 20!");
                    return true;
                }

                Player target = player;
                if (args.length >= 3) {
                    target = plugin.getServer().getPlayer(args[2]);
                    if (target == null) {
                        player.sendMessage("§cJoueur '" + args[2] + "' introuvable ou hors ligne!");
                        return true;
                    }
                }

                giveCrystal(player, target, level);
                return true;
            }

            case "info" -> {
                showCrystalInfo(player);
                return true;
            }

            case "stats" -> {
                showCrystalStats(player);
                return true;
            }

            default -> {
                sendHelpMessage(player);
                return true;
            }
        }
    }

    private void giveCrystal(Player giver, Player target, int level) {
        // Vérifie que l'inventaire a de la place
        if (target.getInventory().firstEmpty() == -1) {
            giver.sendMessage("§cInventaire de " + target.getName() + " plein!");
            return;
        }

        // Crée le cristal vierge
        Crystal crystal = plugin.getCrystalManager().createBlankCrystal(level);
        target.getInventory().addItem(crystal.createItemStack());

        target.sendMessage("§5✨ Vous avez reçu un §8Cristal Mystérieux Niveau " + level + " §5!");
        target.sendMessage("§7§oClic droit pour révéler son type mystérieux...");
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        if (giver != target) {
            giver.sendMessage("§a✅ Cristal niveau " + level + " donné à " + target.getName());
        }

        plugin.getPluginLogger().info("Cristal niveau " + level + " donné à " + target.getName() +
                " par " + giver.getName());
    }

    private void showCrystalInfo(Player player) {
        player.sendMessage("§5✦ §lSYSTÈME DE CRISTAUX §5✦");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");

        player.sendMessage("§e🔮 §lFONCTIONNEMENT");
        player.sendMessage("§7▸ Cristaux obtenus §8vierges §7avec type mystérieux");
        player.sendMessage("§7▸ §eClic droit§7 sur un cristal pour révéler son type");
        player.sendMessage("§7▸ Appliquez les cristaux révélés sur votre pioche");
        player.sendMessage("§7▸ Maximum §54 cristaux§7 par pioche");
        player.sendMessage("§7▸ §c1 seul cristal§7 de chaque type par pioche");
        player.sendMessage("");

        player.sendMessage("§6💰 §lCOÛTS D'APPLICATION");
        player.sendMessage("§7▸ 1er cristal: §a1,000 XP");
        player.sendMessage("§7▸ 2e cristal: §a2,500 XP");
        player.sendMessage("§7▸ 3e cristal: §a5,000 XP");
        player.sendMessage("§7▸ 4e cristal: §a10,000 XP");
        player.sendMessage("");

        player.sendMessage("§c⚠️ §lRETRAIT");
        player.sendMessage("§7▸ Coût: §e50 tokens");
        player.sendMessage("§7▸ §c50% chance§7 de destruction du cristal");
        player.sendMessage("");

        player.sendMessage("§d✨ §lTYPES DE CRISTAUX");
        player.sendMessage("§7▸ §6SellBoost§7: +% prix vente (max +70%)");
        player.sendMessage("§7▸ §aXPBoost§7: +% effet XP Greed (max +100%)");
        player.sendMessage("§7▸ §6MoneyBoost§7: +% effet Money Greed (max +100%)");
        player.sendMessage("§7▸ §eTokenBoost§7: +% effet Token Greed (max +100%)");
        player.sendMessage("§7▸ §2MineralGreed§7: +% effet Fortune (max +100%)");
        player.sendMessage("§7▸ §6AbondanceCristal§7: +durée Abondance (max +60s)");
        player.sendMessage("§7▸ §cCombustionCristal§7: +Combustion, -diminution");
        player.sendMessage("§7▸ §dEchoCristal§7: Chance échos Laser/Explosion");

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void showCrystalStats(Player player) {
        var stats = plugin.getCrystalManager().getCrystalStats();

        player.sendMessage("§5📊 §lSTATISTIQUES CRISTAUX");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§7Total cristaux appliqués: §5" + stats.get("total-crystals-applied"));
        player.sendMessage("§7Maximum par pioche: §5" + stats.get("max-crystals-per-pickaxe"));
        player.sendMessage("§7Coût de retrait: §e" + stats.get("removal-cost") + " tokens");
        player.sendMessage("§7Chance de destruction: §c" +
                String.format("%.0f%%", (Double) stats.get("destruction-chance") * 100));

        @SuppressWarnings("unchecked")
        Map<Object, Integer> typeCount = (Map<Object, Integer>) stats.get("crystals-by-type");
        if (!typeCount.isEmpty()) {
            player.sendMessage("§7Répartition par type:");
            typeCount.forEach((type, count) ->
                    player.sendMessage("§7  • " + type + ": §5" + count));
        }

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§5⚡ §lCommandes Cristaux:");
        player.sendMessage("§7/cristal give <niveau> [joueur] §8- §7Donne un cristal");
        player.sendMessage("§7/cristal info §8- §7Informations sur les cristaux");
        player.sendMessage("§7/cristal stats §8- §7Statistiques des cristaux");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("give", "info", "stats");
            StringUtil.copyPartialMatches(args[0], subCommands, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            List<String> levels = Arrays.asList("1", "5", "10", "15", "20");
            StringUtil.copyPartialMatches(args[1], levels, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (StringUtil.startsWithIgnoreCase(player.getName(), args[2])) {
                    completions.add(player.getName());
                }
            }
        }

        Collections.sort(completions);
        return completions;
    }
}