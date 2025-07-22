package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.BlackMarketManager;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

/**
 * Commande /blackmarket améliorée avec support du PNJ et des états
 */
public class BlackMarketCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public BlackMarketCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }

        if (args.length == 0) {
            showMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "ouvrir", "open", "menu" -> {
                plugin.getBlackMarketManager().openBlackMarket(player);
                return true;
            }
            case "localiser", "locate", "find" -> {
                locateBlackMarket(player);
                return true;
            }
            case "info", "informations" -> {
                showBlackMarketInfo(player);
                return true;
            }
            case "etat", "status", "state" -> {
                showMarketStatus(player);
                return true;
            }
            case "statistiques", "stats" -> {
                showPlayerStats(player);
                return true;
            }
            case "aide", "help" -> {
                showHelp(player);
                return true;
            }
            // Commandes admin
            case "reload" -> {
                if (!player.hasPermission("blackmarket.admin")) {
                    player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
                    return true;
                }
                plugin.getBlackMarketManager().reloadConfiguration();
                player.sendMessage("§a✅ Configuration du marché noir rechargée!");
                return true;
            }
            case "relocate" -> {
                if (!player.hasPermission("blackmarket.admin")) {
                    player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
                    return true;
                }
                plugin.getBlackMarketManager().forceRelocation();
                player.sendMessage("§a✅ Relocalisation forcée du marché noir!");
                return true;
            }
            case "debug" -> {
                if (!player.hasPermission("blackmarket.admin")) {
                    player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
                    return true;
                }
                plugin.getBlackMarketManager().debugStock();
                player.sendMessage("§a✅ Informations de debug affichées dans la console!");
                return true;
            }
            case "teleport", "tp" -> {
                if (!player.hasPermission("blackmarket.admin")) {
                    player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
                    return true;
                }
                teleportToBlackMarket(player);
                return true;
            }
            default -> {
                showMainMenu(player);
                return true;
            }
        }
    }

    /**
     * Affiche le menu principal du marché noir
     */
    private void showMainMenu(Player player) {
        BlackMarketManager.MarketState currentState = plugin.getBlackMarketManager().getCurrentState();
        ReputationTier reputation = plugin.getReputationManager().getReputationTier(player.getUniqueId());

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§8§l⚫ MARCHÉ NOIR - MENU PRINCIPAL ⚫");
        player.sendMessage("");

        // État actuel
        player.sendMessage("§7📊 État actuel: " + currentState.getDisplay());
        player.sendMessage("§7👤 Votre réputation: " + reputation.getColoredTitle());

        // Accès
        if (reputation == ReputationTier.EXEMPLAIRE) {
            player.sendMessage("§c⚠ §7Votre réputation est trop propre pour accéder au marché noir!");
        } else if (!plugin.getBlackMarketManager().isAvailable()) {
            player.sendMessage("§c⚠ §7Le marché noir est actuellement fermé!");
        } else {
            player.sendMessage("§a✅ §7Vous pouvez accéder au marché noir!");
        }

        player.sendMessage("");
        player.sendMessage("§e📋 Commandes disponibles:");
        player.sendMessage("§7┃ §a/blackmarket ouvrir §8- §7Ouvrir le marché");
        player.sendMessage("§7┃ §a/blackmarket localiser §8- §7Trouver le marchand");
        player.sendMessage("§7┃ §a/blackmarket info §8- §7Informations détaillées");
        player.sendMessage("§7┃ §a/blackmarket etat §8- §7État du marché");
        player.sendMessage("§7┃ §a/blackmarket stats §8- §7Vos statistiques");
        player.sendMessage("§7┃ §a/blackmarket aide §8- §7Aide complète");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Localise le marché noir actuel
     */
    private void locateBlackMarket(Player player) {
        if (!plugin.getBlackMarketManager().isAvailable()) {
            BlackMarketManager.MarketState state = plugin.getBlackMarketManager().getCurrentState();
            player.sendMessage("§c§lMARCHÉ INDISPONIBLE!");
            player.sendMessage("§7État: " + state.getDisplay());

            switch (state) {
                case RAIDED -> player.sendMessage("§7Un raid est en cours, revenez plus tard...");
                case RELOCATING -> player.sendMessage("§7Le marchand se déplace, soyez patient...");
                case HIDDEN -> player.sendMessage("§7Le marchand se cache temporairement...");
            }

            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return;
        }

        Location marketLocation = plugin.getBlackMarketManager().getCurrentLocation();
        if (marketLocation == null) {
            player.sendMessage("§c§lINTROUVABLE! §7Aucune trace du marchand actuellement...");
            return;
        }

        double distance = player.getLocation().distance(marketLocation);
        String direction = getDirection(player.getLocation(), marketLocation);

        player.sendMessage("§8§l⚫ LOCALISATION DU MARCHAND ⚫");
        player.sendMessage("§7▸ Distance: §e" + Math.round(distance) + " blocs");
        player.sendMessage("§7▸ Direction: §e" + direction);
        player.sendMessage("§7▸ Monde: §e" + marketLocation.getWorld().getName());
        player.sendMessage("§7▸ Coordonnées: §e" + (int) marketLocation.getX() + ", " +
                (int) marketLocation.getY() + ", " + (int) marketLocation.getZ());

        // Indications de proximité
        if (distance <= 10) {
            player.sendMessage("§a§l🎯 Vous êtes très proche! §7Cherchez le PNJ.");
        } else if (distance <= 50) {
            player.sendMessage("§e§l📍 Vous êtes proche! §7Continuez dans cette direction.");
        } else if (distance <= 200) {
            player.sendMessage("§6§l🧭 Direction correcte. §7Encore un peu de chemin...");
        } else {
            player.sendMessage("§c§l🗺️ Très loin! §7Préparez-vous pour un long voyage.");
        }

        player.sendMessage("§c⚠ §7Soyez discret, des gardes patrouillent...");
        player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_READY, 1.0f, 0.8f);
    }

    /**
     * Affiche les informations détaillées sur le marché noir
     */
    private void showBlackMarketInfo(Player player) {
        ReputationTier reputation = plugin.getReputationManager().getReputationTier(player.getUniqueId());

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§8§l⚫ INFORMATIONS MARCHÉ NOIR ⚫");
        player.sendMessage("");

        player.sendMessage("§e🎯 Fonctionnement:");
        player.sendMessage("§7┃ Le marché noir est tenu par un PNJ marchand");
        player.sendMessage("§7┃ Il se déplace toutes les 6 heures vers un nouvel emplacement");
        player.sendMessage("§7┃ Seuls les criminels peuvent y accéder");
        player.sendMessage("§7┃ Chaque item ne peut être acheté qu'une seule fois");
        player.sendMessage("");

        player.sendMessage("§e📦 Objets disponibles:");
        player.sendMessage("§7┃ §dCristaux vierges §7de tous niveaux (1-10)");
        player.sendMessage("§7┃ §5Livres d'enchantement §7physiques rares");
        player.sendMessage("§7┃ §6Conteneurs §7de stockage améliorés (Tier 1-5)");
        player.sendMessage("§7┃ §eClés §7de coffres de toutes raretés");
        player.sendMessage("");

        player.sendMessage("§e👤 Votre situation:");
        player.sendMessage("§7┃ Réputation: " + reputation.getColoredTitle());
        player.sendMessage("§7┃ Modificateur de prix: §e" + (reputation.getBlackMarketPriceModifier() > 0 ? "+" : "") +
                Math.round(reputation.getBlackMarketPriceModifier() * 100) + "%");

        if (reputation == ReputationTier.EXEMPLAIRE) {
            player.sendMessage("§7┃ §c❌ Accès refusé (réputation trop propre)");
        } else {
            player.sendMessage("§7┃ §a✅ Accès autorisé");
        }

        player.sendMessage("");
        player.sendMessage("§e⚠️ Risques et événements:");
        player.sendMessage("§7┃ §c🚨 Raids §7(fermeture temporaire pendant 2h)");
        player.sendMessage("§7┃ §c⚔ Embuscades §7(dégâts lors de l'ouverture)");
        player.sendMessage("§7┃ §c💸 Arnaques §7(perte d'argent, mauvais items)");
        player.sendMessage("§7┃ §e⚡ Relocalisations §7fréquentes");
        player.sendMessage("");

        player.sendMessage("§e💡 Conseils:");
        player.sendMessage("§7┃ Restez discret pour éviter les embuscades");
        player.sendMessage("§7┃ Le marché se relocalise, revenez régulièrement");
        player.sendMessage("§7┃ Plus votre réputation est mauvaise, moins cher c'est");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Affiche l'état actuel du marché
     */
    private void showMarketStatus(Player player) {
        BlackMarketManager.MarketState state = plugin.getBlackMarketManager().getCurrentState();
        Location location = plugin.getBlackMarketManager().getCurrentLocation();

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§8§l⚫ ÉTAT DU MARCHÉ NOIR ⚫");
        player.sendMessage("");

        player.sendMessage("§e📊 État actuel: " + state.getDisplay());

        switch (state) {
            case AVAILABLE -> {
                player.sendMessage("§a✅ §7Le marché est ouvert et fonctionnel");
                if (location != null) {
                    double distance = player.getLocation().distance(location);
                    player.sendMessage("§7📍 Distance: §e" + Math.round(distance) + " blocs");
                    player.sendMessage("§7🌍 Monde: §e" + location.getWorld().getName());
                }
            }
            case RAIDED -> {
                player.sendMessage("§c🚨 §7Raid en cours! Le marchand se cache.");
                player.sendMessage("§7⏰ Il reviendra dans quelques heures...");
            }
            case RELOCATING -> {
                player.sendMessage("§e⚡ §7Le marchand se déplace vers un nouvel emplacement.");
                player.sendMessage("§7⏰ Relocalisation en cours...");
            }
            case HIDDEN -> {
                player.sendMessage("§8👁 §7Le marchand est temporairement caché.");
                player.sendMessage("§7⏰ Il réapparaîtra bientôt...");
            }
        }

        player.sendMessage("");
        player.sendMessage("§e🕐 Prochaine relocalisation automatique:");
        player.sendMessage("§7┃ Fréquence: §etous les 6 heures");
        player.sendMessage("§7┃ Le marchand peut disparaître à tout moment!");

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Affiche les statistiques du joueur
     */
    private void showPlayerStats(Player player) {
        Set<String> purchases = plugin.getBlackMarketManager().getPlayerPurchases(player.getUniqueId());
        ReputationTier reputation = plugin.getReputationManager().getReputationTier(player.getUniqueId());

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§8§l⚫ VOS STATISTIQUES MARCHÉ NOIR ⚫");
        player.sendMessage("");

        player.sendMessage("§e📊 Statistiques générales:");
        player.sendMessage("§7┃ Items achetés: §a" + purchases.size());
        player.sendMessage("§7┃ Réputation: " + reputation.getColoredTitle());
        player.sendMessage("§7┃ Modificateur prix: §e" + (reputation.getBlackMarketPriceModifier() > 0 ? "+" : "") +
                Math.round(reputation.getBlackMarketPriceModifier() * 100) + "%");

        player.sendMessage("");
        player.sendMessage("§e🛡️ Statut d'accès:");
        if (reputation == ReputationTier.EXEMPLAIRE) {
            player.sendMessage("§7┃ §c❌ Accès refusé");
            player.sendMessage("§7┃ §7Votre réputation est trop exemplaire");
            player.sendMessage("§7┃ §6💡 Commettez des crimes pour accéder au marché");
        } else {
            player.sendMessage("§7┃ §a✅ Accès autorisé");
            player.sendMessage("§7┃ §7Vous pouvez commercer avec le marchand noir");
        }

        if (purchases.size() > 0) {
            player.sendMessage("");
            player.sendMessage("§e🛒 Historique récent:");
            player.sendMessage("§7┃ Vous avez acheté " + purchases.size() + " items uniques");
            player.sendMessage("§7┃ §c⚠ Chaque item ne peut être acheté qu'une fois!");
        }

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Affiche l'aide complète
     */
    private void showHelp(Player player) {
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§8§l⚫ AIDE MARCHÉ NOIR ⚫");
        player.sendMessage("");

        player.sendMessage("§e📋 Commandes principales:");
        player.sendMessage("§7┃ §a/blackmarket §8- §7Menu principal");
        player.sendMessage("§7┃ §a/blackmarket ouvrir §8- §7Ouvrir le marché");
        player.sendMessage("§7┃ §a/blackmarket localiser §8- §7Trouver le marchand");
        player.sendMessage("§7┃ §a/blackmarket info §8- §7Informations détaillées");
        player.sendMessage("§7┃ §a/blackmarket etat §8- §7État actuel du marché");
        player.sendMessage("§7┃ §a/blackmarket stats §8- §7Vos statistiques");
        player.sendMessage("");

        if (player.hasPermission("blackmarket.admin")) {
            player.sendMessage("§c⚡ Commandes administrateur:");
            player.sendMessage("§7┃ §c/blackmarket reload §8- §7Recharger la config");
            player.sendMessage("§7┃ §c/blackmarket relocate §8- §7Forcer relocalisation");
            player.sendMessage("§7┃ §c/blackmarket debug §8- §7Informations debug");
            player.sendMessage("§7┃ §c/blackmarket tp §8- §7Téléportation au marché");
            player.sendMessage("");
        }

        player.sendMessage("§e🎯 Guide rapide:");
        player.sendMessage("§7┃ 1. Vérifiez votre réputation (pas exemplaire)");
        player.sendMessage("§7┃ 2. Localisez le marchand noir");
        player.sendMessage("§7┃ 3. Approchez-vous du PNJ (moins de 10 blocs)");
        player.sendMessage("§7┃ 4. Ouvrez le marché et achetez");
        player.sendMessage("§7┃ 5. Attention aux arnaques et embuscades!");

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Téléportation admin au marché noir
     */
    private void teleportToBlackMarket(Player player) {
        Location marketLocation = plugin.getBlackMarketManager().getCurrentLocation();
        if (marketLocation == null) {
            player.sendMessage("§cAucun marché noir actif!");
            return;
        }

        player.teleport(marketLocation);
        player.sendMessage("§a§lTÉLÉPORTATION §7vers le marché noir!");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
    }

    /**
     * Calcule la direction vers une localisation
     */
    private String getDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        double angle = Math.atan2(dz, dx) * 180 / Math.PI;
        angle = (angle + 360) % 360; // Normalise à 0-360

        if (angle >= 337.5 || angle < 22.5) return "Est (→)";
        else if (angle >= 22.5 && angle < 67.5) return "Sud-Est (↘)";
        else if (angle >= 67.5 && angle < 112.5) return "Sud (↓)";
        else if (angle >= 112.5 && angle < 157.5) return "Sud-Ouest (↙)";
        else if (angle >= 157.5 && angle < 202.5) return "Ouest (←)";
        else if (angle >= 202.5 && angle < 247.5) return "Nord-Ouest (↖)";
        else if (angle >= 247.5 && angle < 292.5) return "Nord (↑)";
        else return "Nord-Est (↗)";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> commands = Arrays.asList("ouvrir", "localiser", "info", "etat", "stats", "aide");

            if (sender.hasPermission("blackmarket.admin")) {
                commands = new ArrayList<>(commands);
                commands.addAll(Arrays.asList("reload", "relocate", "debug", "tp"));
            }

            StringUtil.copyPartialMatches(args[0], commands, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}