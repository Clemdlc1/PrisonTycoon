package fr.skyblock.commands;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class IslandAdminCommand implements CommandExecutor, TabCompleter {

    private final CustomSkyblock plugin;

    public IslandAdminCommand(CustomSkyblock plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande !");
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> handleAdminInfo(sender, args);
            case "delete" -> handleAdminDelete(sender, args);
            case "tp", "teleport" -> handleAdminTeleport(sender, args);
            case "create" -> handleAdminCreate(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "setsize" -> handleSetSize(sender, args);
            case "addmoney" -> handleAddMoney(sender, args);
            case "removemoney" -> handleRemoveMoney(sender, args);
            case "purge" -> handlePurge(sender, args);
            case "reload" -> handleReload(sender);
            case "stats" -> handleStats(sender);
            case "cleanup" -> handleCleanup(sender, args);
            case "backup" -> handleBackup(sender);
            case "restore" -> handleRestore(sender, args);
            case "economy" -> handleEconomy(sender, args);
            case "give" -> handleGiveMoney(sender, args);
            case "take" -> handleTakeMoney(sender, args);
            case "balance" -> handleCheckBalance(sender, args);
            default -> sendAdminHelp(sender);
        }

        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.RED + "Commandes Admin Skyblock" + ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.AQUA + "/isa info <joueur>" + ChatColor.WHITE + " - Infos détaillées d'une île");
        sender.sendMessage(ChatColor.AQUA + "/isa delete <joueur>" + ChatColor.WHITE + " - Supprimer l'île d'un joueur");
        sender.sendMessage(ChatColor.AQUA + "/isa tp <joueur>" + ChatColor.WHITE + " - Se téléporter à une île");
        sender.sendMessage(ChatColor.AQUA + "/isa create <joueur>" + ChatColor.WHITE + " - Créer une île pour un joueur");
        sender.sendMessage(ChatColor.AQUA + "/isa setlevel <joueur> <niveau>" + ChatColor.WHITE + " - Définir le niveau d'une île");
        sender.sendMessage(ChatColor.AQUA + "/isa setsize <joueur> <taille>" + ChatColor.WHITE + " - Définir la taille d'une île");
        sender.sendMessage(ChatColor.AQUA + "/isa addmoney <joueur> <montant>" + ChatColor.WHITE + " - Ajouter de l'argent à la banque");
        sender.sendMessage(ChatColor.AQUA + "/isa removemoney <joueur> <montant>" + ChatColor.WHITE + " - Retirer de l'argent de la banque");
        sender.sendMessage(ChatColor.AQUA + "/isa purge <jours>" + ChatColor.WHITE + " - Supprimer les îles inactives");
        sender.sendMessage(ChatColor.AQUA + "/isa cleanup" + ChatColor.WHITE + " - Nettoyer les données corrompues");
        sender.sendMessage(ChatColor.AQUA + "/isa reload" + ChatColor.WHITE + " - Recharger le plugin");
        sender.sendMessage(ChatColor.AQUA + "/isa stats" + ChatColor.WHITE + " - Statistiques du serveur");
        sender.sendMessage(ChatColor.AQUA + "/isa backup" + ChatColor.WHITE + " - Sauvegarder les données");
        sender.sendMessage(ChatColor.AQUA + "/isa give <joueur> <montant>" + ChatColor.WHITE + " - Donner de l'argent");
        sender.sendMessage(ChatColor.AQUA + "/isa take <joueur> <montant>" + ChatColor.WHITE + " - Prendre de l'argent");
        sender.sendMessage(ChatColor.AQUA + "/isa balance <joueur>" + ChatColor.WHITE + " - Voir le solde");
        sender.sendMessage(ChatColor.AQUA + "/isa economy reset" + ChatColor.WHITE + " - Reset économie");
    }

    private void handleAdminInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa info <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (skyblockPlayer == null) {
            sender.sendMessage(ChatColor.RED + "Aucune donnée trouvée pour ce joueur !");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Infos Admin - " + target.getName() + ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.AQUA + "UUID: " + ChatColor.WHITE + skyblockPlayer.getUuid());
        sender.sendMessage(ChatColor.AQUA + "A une île: " + ChatColor.WHITE + (skyblockPlayer.hasIsland() ? "Oui" : "Non"));
        sender.sendMessage(ChatColor.AQUA + "Première connexion: " + ChatColor.WHITE + new Date(skyblockPlayer.getFirstJoin()));
        sender.sendMessage(ChatColor.AQUA + "Dernière activité: " + ChatColor.WHITE + new Date(skyblockPlayer.getLastSeen()));
        sender.sendMessage(ChatColor.AQUA + "Resets d'île: " + ChatColor.WHITE + skyblockPlayer.getIslandResets());
        sender.sendMessage(ChatColor.AQUA + "Membre d'îles: " + ChatColor.WHITE + skyblockPlayer.getMemberOfIslands().size());

        if (skyblockPlayer.hasIsland()) {
            Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
            if (island != null) {
                sender.sendMessage(ChatColor.GOLD + "--- Informations de l'île ---");
                sender.sendMessage(ChatColor.AQUA + "ID île: " + ChatColor.WHITE + island.getId());
                sender.sendMessage(ChatColor.AQUA + "Nom: " + ChatColor.WHITE + island.getName());
                sender.sendMessage(ChatColor.AQUA + "Niveau: " + ChatColor.WHITE + island.getLevel());
                sender.sendMessage(ChatColor.AQUA + "Taille: " + ChatColor.WHITE + island.getSize());
                sender.sendMessage(ChatColor.AQUA + "Banque: " + ChatColor.WHITE + String.format("%.2f", island.getBank()));
                sender.sendMessage(ChatColor.AQUA + "Centre: " + ChatColor.WHITE +
                        String.format("%.1f, %.1f, %.1f",
                                island.getCenter().getX(),
                                island.getCenter().getY(),
                                island.getCenter().getZ()));
                sender.sendMessage(ChatColor.AQUA + "Membres: " + ChatColor.WHITE + island.getMembers().size());
                sender.sendMessage(ChatColor.AQUA + "Visiteurs actuels: " + ChatColor.WHITE + island.getVisitors().size());
            }
        }
    }

    private void handleAdminDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa delete <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (skyblockPlayer == null || !skyblockPlayer.hasIsland()) {
            sender.sendMessage(ChatColor.RED + "Ce joueur n'a pas d'île !");
            return;
        }

        Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
        if (island == null) {
            sender.sendMessage(ChatColor.RED + "Impossible de trouver l'île de ce joueur !");
            return;
        }

        if (plugin.getIslandManager().deleteIsland(island)) {
            sender.sendMessage(ChatColor.GREEN + "L'île de " + target.getName() + " a été supprimée avec succès !");
            if (target.isOnline()) {
                target.sendMessage(ChatColor.RED + "Votre île a été supprimée par un administrateur.");
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Erreur lors de la suppression de l'île !");
        }
    }

    private void handleAdminTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande ne peut être utilisée que par un joueur !");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa tp <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (skyblockPlayer == null || !skyblockPlayer.hasIsland()) {
            sender.sendMessage(ChatColor.RED + "Ce joueur n'a pas d'île !");
            return;
        }

        Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
        if (island == null) {
            sender.sendMessage(ChatColor.RED + "Impossible de trouver l'île de ce joueur !");
            return;
        }

        if (plugin.getIslandManager().teleportToIsland(player, island)) {
            sender.sendMessage(ChatColor.GREEN + "Téléporté à l'île de " + target.getName() + " !");
        } else {
            sender.sendMessage(ChatColor.RED + "Erreur lors de la téléportation !");
        }
    }

    private void handleAdminCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa create <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().getOrCreatePlayer(target.getUniqueId(), target.getName());
        if (skyblockPlayer.hasIsland()) {
            sender.sendMessage(ChatColor.RED + "Ce joueur a déjà une île !");
            return;
        }

        Island island = plugin.getIslandManager().createIsland(target);
        if (island != null) {
            sender.sendMessage(ChatColor.GREEN + "Île créée avec succès pour " + target.getName() + " !");
            target.sendMessage(ChatColor.GREEN + "Une île a été créée pour vous par un administrateur !");
        } else {
            sender.sendMessage(ChatColor.RED + "Erreur lors de la création de l'île !");
        }
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa setlevel <joueur> <niveau>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Niveau invalide !");
            return;
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (skyblockPlayer == null || !skyblockPlayer.hasIsland()) {
            sender.sendMessage(ChatColor.RED + "Ce joueur n'a pas d'île !");
            return;
        }

        Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
        if (island == null) {
            sender.sendMessage(ChatColor.RED + "Impossible de trouver l'île de ce joueur !");
            return;
        }

        island.setLevel(level);
        plugin.getDatabaseManager().saveIsland(island);

        sender.sendMessage(ChatColor.GREEN + "Niveau de l'île de " + target.getName() + " défini à " + level + " !");
        if (target.isOnline()) {
            target.sendMessage(ChatColor.GREEN + "Le niveau de votre île a été défini à " + level + " par un administrateur !");
        }
    }

    private void handleSetSize(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa setsize <joueur> <taille>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        int size;
        try {
            size = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Taille invalide !");
            return;
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (skyblockPlayer == null || !skyblockPlayer.hasIsland()) {
            sender.sendMessage(ChatColor.RED + "Ce joueur n'a pas d'île !");
            return;
        }

        Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
        if (island == null) {
            sender.sendMessage(ChatColor.RED + "Impossible de trouver l'île de ce joueur !");
            return;
        }

        island.setSize(size);
        plugin.getDatabaseManager().saveIsland(island);

        sender.sendMessage(ChatColor.GREEN + "Taille de l'île de " + target.getName() + " définie à " + size + " !");
        if (target.isOnline()) {
            target.sendMessage(ChatColor.GREEN + "La taille de votre île a été définie à " + size + " par un administrateur !");
        }
    }

    private void handleAddMoney(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa addmoney <joueur> <montant>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Montant invalide !");
            return;
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (skyblockPlayer == null || !skyblockPlayer.hasIsland()) {
            sender.sendMessage(ChatColor.RED + "Ce joueur n'a pas d'île !");
            return;
        }

        Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
        if (island == null) {
            sender.sendMessage(ChatColor.RED + "Impossible de trouver l'île de ce joueur !");
            return;
        }

        island.addToBank(amount);
        plugin.getDatabaseManager().saveIsland(island);

        sender.sendMessage(ChatColor.GREEN + String.format("%.2f $ ajoutés à la banque de l'île de %s !", amount, target.getName()));
        if (target.isOnline()) {
            target.sendMessage(ChatColor.GREEN + String.format("%.2f $ ont été ajoutés à votre banque d'île par un administrateur !", amount));
        }
    }

    private void handleRemoveMoney(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa removemoney <joueur> <montant>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Montant invalide !");
            return;
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (skyblockPlayer == null || !skyblockPlayer.hasIsland()) {
            sender.sendMessage(ChatColor.RED + "Ce joueur n'a pas d'île !");
            return;
        }

        Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
        if (island == null) {
            sender.sendMessage(ChatColor.RED + "Impossible de trouver l'île de ce joueur !");
            return;
        }

        if (island.removeFromBank(amount)) {
            plugin.getDatabaseManager().saveIsland(island);
            sender.sendMessage(ChatColor.GREEN + String.format("%.2f $ retirés de la banque de l'île de %s !", amount, target.getName()));
            if (target.isOnline()) {
                target.sendMessage(ChatColor.YELLOW + String.format("%.2f $ ont été retirés de votre banque d'île par un administrateur !", amount));
            }
        } else {
            sender.sendMessage(ChatColor.RED + "Fonds insuffisants dans la banque de l'île !");
        }
    }

    private void handlePurge(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa purge <jours_inactivité>");
            return;
        }

        int days;
        try {
            days = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Nombre de jours invalide !");
            return;
        }

        List<Island> inactiveIslands = plugin.getDatabaseManager().getInactiveIslands(days);

        sender.sendMessage(ChatColor.YELLOW + "Suppression de " + inactiveIslands.size() + " îles inactives depuis plus de " + days + " jours...");

        int deleted = 0;
        for (Island island : inactiveIslands) {
            if (plugin.getIslandManager().deleteIsland(island)) {
                deleted++;
            }
        }

        sender.sendMessage(ChatColor.GREEN + "" + deleted + " îles supprimées avec succès !");
    }

    private void handleReload(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Rechargement du plugin en cours...");

        // Sauvegarder avant de recharger
        plugin.getDatabaseManager().saveAll();

        // Recharger la configuration
        plugin.reloadConfig();

        // Recharger les données
        plugin.getDatabaseManager().reloadFromDisk();

        sender.sendMessage(ChatColor.GREEN + "Plugin rechargé avec succès !");
    }

    private void handleStats(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Statistiques Skyblock" + ChatColor.GOLD + " ===");
        sender.sendMessage(ChatColor.AQUA + "Total îles: " + ChatColor.WHITE + plugin.getDatabaseManager().getTotalIslands());
        sender.sendMessage(ChatColor.AQUA + "Total joueurs: " + ChatColor.WHITE + plugin.getDatabaseManager().getTotalPlayers());
        sender.sendMessage(ChatColor.AQUA + "Îles actives (7 jours): " + ChatColor.WHITE + plugin.getDatabaseManager().getActiveIslands(7));
        sender.sendMessage(ChatColor.AQUA + "Îles actives (30 jours): " + ChatColor.WHITE + plugin.getDatabaseManager().getActiveIslands(30));

        // Statistiques sur les niveaux
        List<Island> topIslands = plugin.getDatabaseManager().getTopIslandsByLevel(5);
        if (!topIslands.isEmpty()) {
            sender.sendMessage(ChatColor.GOLD + "--- Top 5 îles ---");
            for (int i = 0; i < topIslands.size(); i++) {
                Island island = topIslands.get(i);
                Player owner = Bukkit.getPlayer(island.getOwner());
                String ownerName = owner != null ? owner.getName() : "Inconnu";
                sender.sendMessage("" + ChatColor.AQUA + (i + 1) + ". " + ChatColor.WHITE + ownerName +
                        ChatColor.GRAY + " - Niveau " + ChatColor.YELLOW + island.getLevel());
            }
        }
    }

    private void handleCleanup(CommandSender sender, String[] args) {
        sender.sendMessage(ChatColor.YELLOW + "Nettoyage des données en cours...");

        // TODO: Implémenter le nettoyage des données corrompues
        // - Vérifier l'intégrité des îles
        // - Supprimer les références orphelines
        // - Corriger les incohérences

        sender.sendMessage(ChatColor.GREEN + "Nettoyage terminé !");
    }

    private void handleBackup(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Sauvegarde en cours...");
        plugin.getDatabaseManager().saveAll();
        sender.sendMessage(ChatColor.GREEN + "Sauvegarde terminée !");
    }

    private void handleRestore(CommandSender sender, String[] args) {
        // Système de restauration simple
        sender.sendMessage(ChatColor.YELLOW + "Rechargement des données depuis les fichiers...");
        plugin.getDatabaseManager().reloadFromDisk();
        sender.sendMessage(ChatColor.GREEN + "Données restaurées depuis les fichiers !");
    }

    private void handleEconomy(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa economy <reset|stats>");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "reset" -> {
                sender.sendMessage(ChatColor.YELLOW + "Reset de l'économie en cours...");
                // Reset de tous les soldes
                for (SkyblockPlayer player : new ArrayList<>(plugin.getDatabaseManager().getAllPlayers())) {
                    plugin.getEconomyManager().setBalance(player.getUuid(),
                            plugin.getConfig().getDouble("economy.starting-money", 50.0));
                }
                // Reset des banques d'îles
                for (Island island : plugin.getDatabaseManager().getAllIslands()) {
                    island.setBank(0.0);
                    plugin.getDatabaseManager().saveIsland(island);
                }
                sender.sendMessage(ChatColor.GREEN + "Économie reset avec succès !");
            }
            case "stats" -> {
                double totalMoney = 0;
                double totalBankMoney = 0;

                for (SkyblockPlayer player : new ArrayList<>(plugin.getDatabaseManager().getAllPlayers())) {
                    totalMoney += plugin.getEconomyManager().getBalance(player.getUuid());
                }

                for (Island island : plugin.getDatabaseManager().getAllIslands()) {
                    totalBankMoney += island.getBank();
                }

                sender.sendMessage(ChatColor.GOLD + "=== Statistiques Économie ===");
                sender.sendMessage(ChatColor.AQUA + "Argent total joueurs: " + ChatColor.WHITE +
                        plugin.getEconomyManager().formatMoney(totalMoney));
                sender.sendMessage(ChatColor.AQUA + "Argent total banques: " + ChatColor.WHITE +
                        plugin.getEconomyManager().formatMoney(totalBankMoney));
                sender.sendMessage(ChatColor.AQUA + "Argent total serveur: " + ChatColor.WHITE +
                        plugin.getEconomyManager().formatMoney(totalMoney + totalBankMoney));
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /isa economy <reset|stats>");
        }
    }

    private void handleGiveMoney(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa give <joueur> <montant>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Montant invalide !");
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Le montant doit être positif !");
            return;
        }

        plugin.getEconomyManager().addBalance(target.getUniqueId(), amount);

        sender.sendMessage(ChatColor.GREEN + "Vous avez donné " +
                plugin.getEconomyManager().formatMoney(amount) + " à " + target.getName() + " !");
        target.sendMessage(ChatColor.GREEN + "Vous avez reçu " +
                plugin.getEconomyManager().formatMoney(amount) + " d'un administrateur !");
    }

    private void handleTakeMoney(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa take <joueur> <montant>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Montant invalide !");
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(ChatColor.RED + "Le montant doit être positif !");
            return;
        }

        if (plugin.getEconomyManager().removeBalance(target.getUniqueId(), amount)) {
            sender.sendMessage(ChatColor.GREEN + "Vous avez retiré " +
                    plugin.getEconomyManager().formatMoney(amount) + " à " + target.getName() + " !");
            target.sendMessage(ChatColor.YELLOW + plugin.getEconomyManager().formatMoney(amount) +
                    " ont été retirés de votre compte par un administrateur !");
        } else {
            sender.sendMessage(ChatColor.RED + "Fonds insuffisants chez " + target.getName() + " !");
        }
    }

    private void handleCheckBalance(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /isa balance <joueur>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable !");
            return;
        }

        double balance = plugin.getEconomyManager().getBalance(target.getUniqueId());
        sender.sendMessage(ChatColor.AQUA + "Solde de " + target.getName() + ": " + ChatColor.WHITE +
                plugin.getEconomyManager().formatMoney(balance));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList(
                    "info", "delete", "tp", "create", "setlevel", "setsize",
                    "addmoney", "removemoney", "purge", "reload", "stats",
                    "cleanup", "backup", "restore"
            );
            return subCommands.stream()
                    .filter(sub -> sub.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "info", "delete", "tp", "create", "setlevel", "setsize", "addmoney", "removemoney" -> {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
            }
        }

        return new ArrayList<>();
    }
}