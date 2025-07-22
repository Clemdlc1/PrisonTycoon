package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.BlackMarketManager;
import fr.prisontycoon.prestige.PrestigeTalent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Commande pour localiser et accéder au Black Market
 * /fbm ou /findblackmarket - Localise le marché noir (Talent Guerrier niv.5+ requis)
 */
public class BlackMarketCommand implements CommandExecutor {

    private final PrisonTycoon plugin;
    private final BlackMarketManager blackMarketManager;

    public BlackMarketCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.blackMarketManager = plugin.getBlackMarketManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        Player player = (Player) sender;
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (!playerData.hasCustomPermission("specialmine.fbm")) {
            player.sendMessage("§c§lAccès refusé!");
            player.sendMessage("§7Vous devez avoir le talent §eGuerrier niveau 5+ §7pour localiser le marché noir.");
            player.sendMessage("§8Seuls les combattants expérimentés connaissent les réseaux clandestins...");
            return true;
        }

        if (args.length == 0) {
            // /fbm - Localise le marché noir
            locateBlackMarket(player);
        } else {
            switch (args[0].toLowerCase()) {
                case "tp":
                case "teleport":
                    // /fbm tp - Se téléporte au marché noir (admin ou test)
                    if (!player.hasPermission("specialmine.admin")) {
                        player.sendMessage("§cVous n'avez pas la permission de vous téléporter!");
                        return true;
                    }
                    teleportToBlackMarket(player);
                    break;

                case "open":
                    // /fbm open - Ouvre directement l'interface (si proche)
                    openBlackMarketInterface(player);
                    break;

                case "info":
                    // /fbm info - Informations sur le marché noir
                    showBlackMarketInfo(player);
                    break;

                default:
                    player.sendMessage("§cUsage: /fbm [tp|open|info]");
                    break;
            }
        }

        return true;
    }

    /**
     * Localise le marché noir et donne des indications
     */
    private void locateBlackMarket(Player player) {
        if (!blackMarketManager.isAvailable()) {
            player.sendMessage("§8§l[Réseau Clandestin] §cMarché fermé temporairement...");
            player.sendMessage("§7Vos contacts signalent une activité policière récente.");
            player.sendMessage("§8Revenez plus tard.");
            return;
        }

        Location marketLocation = blackMarketManager.getCurrentLocation();
        if (marketLocation == null) {
            player.sendMessage("§cErreur: Emplacement du marché noir introuvable!");
            return;
        }

        Location playerLocation = player.getLocation();

        // Vérifie si les joueurs sont dans le même monde
        if (!playerLocation.getWorld().equals(marketLocation.getWorld())) {
            player.sendMessage("§8§l[Réseau Clandestin] §cMarché inaccessible depuis ce monde.");
            return;
        }

        double distance = playerLocation.distance(marketLocation);

        player.sendMessage("§8§l[Réseau Clandestin] §7Localisation en cours...");
        player.sendMessage("");

        if (distance <= 10) {
            // Très proche - peut ouvrir directement
            player.sendMessage("§a§lMarché trouvé! §7Le marchand est tout près...");
            player.sendMessage("§eCliquez-droit sur le marchand ou utilisez §f/fbm open");

            // Effet de particules pour indiquer la direction
            player.spawnParticle(Particle.HAPPY_VILLAGER, marketLocation, 10);

        } else if (distance <= 50) {
            // Proche - donne direction précise
            String direction = getDirectionToLocation(playerLocation, marketLocation);
            player.sendMessage("§e§lMarché proche! §7Distance: §f" + Math.round(distance) + " blocs");
            player.sendMessage("§7Direction: §f" + direction);
            player.sendMessage("§8Cherchez les signes d'activité suspecte...");

        } else if (distance <= 200) {
            // Moyen - donne direction générale
            String direction = getGeneralDirection(playerLocation, marketLocation);
            player.sendMessage("§6§lMarché détecté! §7Distance: §f" + Math.round(distance) + " blocs");
            player.sendMessage("§7Direction générale: §f" + direction);
            player.sendMessage("§8Suivez les rumeurs et restez discret...");

        } else {
            // Loin - donne coordonnées approximatives
            int approxX = (int) (Math.round(marketLocation.getX() / 50.0) * 50);
            int approxZ = (int) (Math.round(marketLocation.getZ() / 50.0) * 50);

            player.sendMessage("§c§lMarché distant! §7Distance: §f" + Math.round(distance) + " blocs");
            player.sendMessage("§7Zone approximative: §fX: " + approxX + ", Z: " + approxZ);
            player.sendMessage("§8Un long voyage vous attend...");
        }

        player.sendMessage("");
        player.sendMessage("§8§o\"Les affaires se font dans l'ombre...\"");
    }

    /**
     * Ouvre l'interface du marché noir si le joueur est assez proche
     */
    private void openBlackMarketInterface(Player player) {
        if (!blackMarketManager.isAvailable()) {
            player.sendMessage("§cMarché noir fermé temporairement!");
            return;
        }

        Location marketLocation = blackMarketManager.getCurrentLocation();
        Location playerLocation = player.getLocation();

        if (marketLocation == null || !playerLocation.getWorld().equals(marketLocation.getWorld())) {
            player.sendMessage("§cMarché noir inaccessible!");
            return;
        }

        double distance = playerLocation.distance(marketLocation);

        if (distance > 15) {
            player.sendMessage("§cVous êtes trop loin du marché noir! §7(Distance: " + Math.round(distance) + " blocs)");
            player.sendMessage("§8Utilisez §f/fbm §8pour le localiser.");
            return;
        }

        // Ouvre l'interface du marché noir
        blackMarketManager.openBlackMarket(player);
    }

    /**
     * Téléporte le joueur au marché noir (admin uniquement)
     */
    private void teleportToBlackMarket(Player player) {
        Location marketLocation = blackMarketManager.getCurrentLocation();

        if (marketLocation == null) {
            player.sendMessage("§cErreur: Emplacement du marché noir introuvable!");
            return;
        }

        // Ajuste la localisation pour être sûr que le joueur apparaît en sécurité
        Location teleportLocation = marketLocation.clone().add(0, 1, 0);

        player.teleport(teleportLocation);
        player.sendMessage("§a§lTéléporté au marché noir! §7(Admin)");
    }

    /**
     * Affiche des informations sur le marché noir
     */
    private void showBlackMarketInfo(Player player) {
        player.sendMessage("§8§l════════════════════════════════");
        player.sendMessage("§6§lMARCHÉ NOIR - INFORMATIONS");
        player.sendMessage("§8§l════════════════════════════════");
        player.sendMessage("");

        player.sendMessage("§e§lACCÈS:");
        player.sendMessage("§7• Talent §eGuerrier niveau 5+ §7requis");
        player.sendMessage("§7• Réputation §c§lExemplaire §7interdite");
        player.sendMessage("");

        player.sendMessage("§e§lFONCTIONNEMENT:");
        player.sendMessage("§7• Emplacement change toutes les 6h");
        player.sendMessage("§7• Stock rafraîchi à chaque déplacement");
        player.sendMessage("§7• Prix en §ebeacons §7uniquement");
        player.sendMessage("");

        player.sendMessage("§e§lRISQUES:");
        player.sendMessage("§c• 15% chance de raid (fermeture temporaire)");
        player.sendMessage("§c• 10% chance d'embuscade (changement forcé)");
        player.sendMessage("§c• 5% chance d'arnaque par transaction");
        player.sendMessage("");

        player.sendMessage("§e§lIMPACT RÉPUTATION:");
        player.sendMessage("§7• Chaque achat: §c-1 à -3 points");
        player.sendMessage("§7• Meilleurs prix pour réputation négative");
        player.sendMessage("§7• Plus d'offres pour criminels");
        player.sendMessage("");

        if (blackMarketManager.isAvailable()) {
            player.sendMessage("§a§lStatut: §7Marché ouvert");
        } else {
            player.sendMessage("§c§lStatut: §7Marché fermé (raid en cours)");
        }

        player.sendMessage("§8§l════════════════════════════════");
    }

    /**
     * Obtient la direction précise vers une localisation
     */
    private String getDirectionToLocation(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        double angle = Math.atan2(dz, dx) * 180 / Math.PI;
        angle = (angle + 360) % 360; // Normalise entre 0 et 360

        if (angle >= 337.5 || angle < 22.5) return "Est";
        else if (angle >= 22.5 && angle < 67.5) return "Sud-Est";
        else if (angle >= 67.5 && angle < 112.5) return "Sud";
        else if (angle >= 112.5 && angle < 157.5) return "Sud-Ouest";
        else if (angle >= 157.5 && angle < 202.5) return "Ouest";
        else if (angle >= 202.5 && angle < 247.5) return "Nord-Ouest";
        else if (angle >= 247.5 && angle < 292.5) return "Nord";
        else return "Nord-Est";
    }

    /**
     * Obtient la direction générale vers une localisation
     */
    private String getGeneralDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();

        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? "Est" : "Ouest";
        } else {
            return dz > 0 ? "Sud" : "Nord";
        }
    }
}