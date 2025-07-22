package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.reputation.ReputationTier;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Commande améliorée pour le Black Market
 */
public class BlackMarketCommand implements CommandExecutor {

    private final PrisonTycoon plugin;

    public BlackMarketCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        Player player = (Player) sender;

        // Vérification de base de la réputation
        ReputationTier reputation = plugin.getReputationManager().getReputationTier(player.getUniqueId());
        if (reputation == ReputationTier.EXEMPLAIRE) {
            player.sendMessage("§c§lACCÈS REFUSÉ! §7Votre réputation est trop... propre pour connaître ces lieux.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            return true;
        }

        if (args.length == 0) {
            showBlackMarketHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "localiser":
            case "find":
                locateBlackMarket(player);
                break;

            case "ouvrir":
            case "open":
                plugin.getBlackMarketManager().openBlackMarket(player);
                break;

            case "info":
                showBlackMarketInfo(player);
                break;

            case "tp":
                if (player.hasPermission("specialmine.admin")) {
                    teleportToBlackMarket(player);
                } else {
                    player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande!");
                }
                break;

            default:
                showBlackMarketHelp(player);
                break;
        }

        return true;
    }

    private void showBlackMarketHelp(Player player) {
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§8§l⚫ MARCHÉ NOIR - AIDE ⚫");
        player.sendMessage("");
        player.sendMessage("§7▸ §e/bm localiser §7- Localise le marché");
        player.sendMessage("§7▸ §e/bm ouvrir §7- Ouvre le marché (si proche)");
        player.sendMessage("§7▸ §e/bm info §7- Informations générales");
        player.sendMessage("");
        player.sendMessage("§c⚠ §7Attention: Commerce à vos risques!");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void locateBlackMarket(Player player) {
        if (!plugin.getBlackMarketManager().isAvailable()) {
            player.sendMessage("§c§lMARCHÉ FERMÉ! §7Le marché noir est temporairement indisponible...");
            player.sendMessage("§7Revenez plus tard, des rumeurs disent qu'il se relocalise souvent.");
            return;
        }

        Location marketLocation = plugin.getBlackMarketManager().getCurrentLocation();
        if (marketLocation == null) {
            player.sendMessage("§c§lINTROUVABLE! §7Aucune trace du marché noir actuellement...");
            return;
        }

        double distance = player.getLocation().distance(marketLocation);
        String direction = getDirection(player.getLocation(), marketLocation);

        player.sendMessage("§8§l⚫ LOCALISATION DU MARCHÉ NOIR ⚫");
        player.sendMessage("§7▸ Distance: §e" + Math.round(distance) + " blocs");
        player.sendMessage("§7▸ Direction: §e" + direction);
        player.sendMessage("§7▸ Coordonnées: §e" + (int) marketLocation.getX() + ", " + (int) marketLocation.getZ());
        player.sendMessage("§c⚠ §7Soyez discret, des gardes patrouillent...");

        player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_READY, 1.0f, 0.8f);
    }

    private void showBlackMarketInfo(Player player) {
        ReputationTier reputation = plugin.getReputationManager().getReputationTier(player.getUniqueId());

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§8§l⚫ INFORMATIONS MARCHÉ NOIR ⚫");
        player.sendMessage("");
        player.sendMessage("§7Le marché noir propose des objets rares:");
        player.sendMessage("§7▸ §dCristaux vierges §7de tous niveaux");
        player.sendMessage("§7▸ §5Livres d'enchantement §7uniques");
        player.sendMessage("§7▸ §6Conteneurs §7de stockage améliorés");
        player.sendMessage("§7▸ §eClés §7de coffres rares");
        player.sendMessage("");
        player.sendMessage("§7Votre réputation: §f" + reputation.getColoredTitle());
        player.sendMessage("§7Modificateur de prix: §e" + (reputation.getBlackMarketPriceModifier() > 0 ? "+" : "") +
                Math.round(reputation.getBlackMarketPriceModifier() * 100) + "%");
        player.sendMessage("");
        player.sendMessage("§c⚠ §7Risques: Embuscades, arnaques, raids");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    private void teleportToBlackMarket(Player player) {
        Location marketLocation = plugin.getBlackMarketManager().getCurrentLocation();
        if (marketLocation == null) {
            player.sendMessage("§cAucun marché noir actif!");
            return;
        }

        player.teleport(marketLocation);
        player.sendMessage("§a§lTÉLÉPORTATION §7vers le marché noir!");
    }

    private String getDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        double angle = Math.atan2(dz, dx) * 180 / Math.PI;
        angle = (angle + 360) % 360;

        if (angle >= 337.5 || angle < 22.5) return "Est";
        else if (angle >= 22.5 && angle < 67.5) return "Sud-Est";
        else if (angle >= 67.5 && angle < 112.5) return "Sud";
        else if (angle >= 112.5 && angle < 157.5) return "Sud-Ouest";
        else if (angle >= 157.5 && angle < 202.5) return "Ouest";
        else if (angle >= 202.5 && angle < 247.5) return "Nord-Ouest";
        else if (angle >= 247.5 && angle < 292.5) return "Nord";
        else return "Nord-Est";
    }
}