package fr.prisontycoon.commands;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Commande /collection pour gérer la collection de têtes
 */
public class HeadCollectionCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public HeadCollectionCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être utilisée que par un joueur !");
            return true;
        }

        // /collection ou /collection menu - ouvre le menu principal
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            plugin.getHeadCollectionGUI().openCollectionMenu(player);
            return true;
        }

        // Vérification des permissions admin pour les autres commandes
        if (!player.hasPermission("prisontycoon.admin")) {
            player.sendMessage("§cVous n'avez pas la permission d'utiliser cette commande !");
            plugin.getHeadCollectionGUI().openCollectionMenu(player);
            return true;
        }

        // Commandes administrateur
        switch (args[0].toLowerCase()) {
            case "setup" -> handleSetupCommand(player, args);
            case "reload" -> handleReloadCommand(player);
            case "stats" -> handleStatsCommand(player);
            case "give" -> handleGiveCommand(player, args);
            case "reset" -> handleResetCommand(player, args);
            case "list" -> handleListCommand(player);
            default -> sendHelpMessage(player);
        }

        return true;
    }

    /**
     * Gère la commande setup
     */
    private void handleSetupCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /collection setup <on|off>");
            return;
        }

        boolean setupMode = args[1].equalsIgnoreCase("on");
        plugin.getHeadCollectionListener().setSetupMode(setupMode);

        if (setupMode) {
            player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§a✓ §lMode configuration activé !");
            player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("");
            player.sendMessage("§e🔧 §lActions disponibles:");
            player.sendMessage("§7• §fPoser une tête §8→ §aEnregistrement automatique");
            player.sendMessage("§7• §fCasser une tête §8→ §cSuppression du registre");
            player.sendMessage("§7• §fClic droit sur une tête §8→ §eInformations");
            player.sendMessage("");
            player.sendMessage("§6💡 §lConseil: §7Utilisez la tête avec cette texture:");
            player.sendMessage("§7eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly...");
            player.sendMessage("");
            player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        } else {
            player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c✗ §lMode configuration désactivé !");
            player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§7Les joueurs peuvent maintenant collecter les têtes.");
        }
    }

    /**
     * Gère la commande reload
     */
    private void handleReloadCommand(Player player) {
        try {
            plugin.getHeadCollectionManager().saveHeadsToFile();
            player.sendMessage("§a✓ Collection de têtes rechargée avec succès !");

            int totalHeads = plugin.getHeadCollectionManager().getTotalHeads();
            player.sendMessage("§7Nombre de têtes enregistrées: §e" + totalHeads);

        } catch (Exception e) {
            player.sendMessage("§cErreur lors du rechargement: " + e.getMessage());
            plugin.getLogger().severe("Erreur lors du rechargement de la collection: " + e.getMessage());
        }
    }

    /**
     * Gère la commande stats
     */
    private void handleStatsCommand(Player player) {
        int totalHeads = plugin.getHeadCollectionManager().getTotalHeads();
        boolean setupMode = plugin.getHeadCollectionListener().isSetupMode();

        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6§l📊 Statistiques de Collection");
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage("§e🎯 Total têtes enregistrées: §f" + totalHeads);
        player.sendMessage("§e🔧 Mode setup: " + (setupMode ? "§aActivé" : "§cDésactivé"));

        // Statistiques des joueurs connectés
        int playersOnline = plugin.getServer().getOnlinePlayers().size();
        player.sendMessage("§e👥 Joueurs en ligne: §f" + playersOnline);

        // Récompenses configurées
        int rewardCount = plugin.getHeadCollectionManager().getRewards().size();
        player.sendMessage("§e🎁 Récompenses configurées: §f" + rewardCount);

        player.sendMessage("");
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Gère la commande give (pour donner la tête de collection)
     */
    private void handleGiveCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /collection give <joueur> [quantité]");
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1 || amount > 64) {
                    player.sendMessage("§cQuantité invalide (1-64): " + args[2]);
                    return;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cQuantité invalide: " + args[2]);
                return;
            }
        }

        // Création de la tête de collection
        ItemStack collectionHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) collectionHead.getItemMeta();

        // --- CORRECTION APPLIQUÉE ICI ---
        // Utilisation de l'API Paper pour la gestion des profils de joueurs.
        String textureValue = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWIwZTA3NjMyMmZjOWFmNzk1OTJlYjg1MmNhOGM3YzQ1YmIyYzNjZWFiYzNjMGU4YTZhMWUwNGI0Y2UzZDM0YiJ9fX0=";

        // 1. On crée un profil via la méthode fournie par Paper.
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());

        // 2. On crée et ajoute la propriété de texture au profil.
        //    L'objet ProfileProperty vient du package de Paper.
        profile.setProperty(new ProfileProperty("textures", textureValue));

        // 3. On applique le profil contenant la texture à la tête.
        headMeta.setPlayerProfile(profile);

        // Définir le nom et la description
        headMeta.setDisplayName("§6Tête de Collection");
        headMeta.setLore(Arrays.asList(
                "§7Posez cette tête pour l'enregistrer",
                "§7dans la collection du serveur."
        ));
        collectionHead.setItemMeta(headMeta);
        collectionHead.setAmount(amount);

        // Donner l'item au joueur
        target.getInventory().addItem(collectionHead);

        player.sendMessage("§a✓ " + amount + " tête(s) de collection donnée(s) à " + target.getName());
        target.sendMessage("§aVous avez reçu " + amount + " tête(s) de collection de " + player.getName() + " !");

        plugin.getPluginLogger().info(player.getName() + " a donné " + amount + " tête(s) de collection à " + target.getName());
    }

    /**
     * Gère la commande reset
     */
    private void handleResetCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /collection reset <joueur>");
            return;
        }

        Player target = plugin.getServer().getPlayer(args[1]);
        if (target == null) {
            player.sendMessage("§cJoueur introuvable: " + args[1]);
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        if (playerData == null) {
            player.sendMessage("§cImpossible de charger les données de " + target.getName());
            return;
        }

        int beforeCount = playerData.getCollectedHeads().size();
        int beforeRewards = playerData.getClaimedHeadRewards().size();

        // Reset de la collection
        playerData.getCollectedHeads().clear();
        playerData.getClaimedHeadRewards().clear();
        plugin.getPlayerDataManager().markDirty(target.getUniqueId());

        player.sendMessage("§a✓ Collection de " + target.getName() + " réinitialisée !");
        player.sendMessage("§7Têtes supprimées: §c" + beforeCount);
        player.sendMessage("§7Récompenses supprimées: §c" + beforeRewards);

        target.sendMessage("§c⚠ Votre collection de têtes a été réinitialisée par " + player.getName());

        plugin.getPluginLogger().info(player.getName() + " a reset la collection de " + target.getName());
    }

    /**
     * Gère la commande list
     */
    private void handleListCommand(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            player.sendMessage("§cErreur lors du chargement de vos données !");
            return;
        }

        int collected = playerData.getCollectedHeads().size();
        int total = plugin.getHeadCollectionManager().getTotalHeads();

        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6§l📋 Votre Collection");
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage("§e⭐ Têtes collectées: §f" + collected + "§8/§f" + total);

        if (total > 0) {
            double percentage = ((double) collected / total) * 100;
            player.sendMessage("§e📈 Progression: §f" + String.format("%.1f", percentage) + "%");
        }

        player.sendMessage("§e🎁 Récompenses réclamées: §f" + playerData.getClaimedHeadRewards().size());
        player.sendMessage("");

        if (collected < total) {
            player.sendMessage("§7Il vous reste §e" + (total - collected) + " tête" +
                    (total - collected > 1 ? "s" : "") + "§7 à trouver !");
        } else if (total > 0) {
            player.sendMessage("§a§l🎉 COLLECTION COMPLÈTE !");
        }

        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    /**
     * Affiche l'aide des commandes
     */
    private void sendHelpMessage(Player player) {
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§6§l📖 Commandes Collection");
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("");
        player.sendMessage("§e👤 §lCommandes joueur:");
        player.sendMessage("§7/collection §8- §fOuvre le menu de collection");
        player.sendMessage("§7/collection menu §8- §fOuvre le menu de collection");

        if (player.hasPermission("prisontycoon.admin")) {
            player.sendMessage("");
            player.sendMessage("§c⚙ §lCommandes admin:");
            player.sendMessage("§7/collection setup <on|off> §8- §fMode configuration");
            player.sendMessage("§7/collection reload §8- §fRecharge les têtes");
            player.sendMessage("§7/collection stats §8- §fAffiche les statistiques");
            player.sendMessage("§7/collection give <joueur> [qté] §8- §fDonne des têtes");
            player.sendMessage("§7/collection reset <joueur> §8- §fReset une collection");
            player.sendMessage("§7/collection list §8- §fVotre collection détaillée");
        }

        player.sendMessage("");
        player.sendMessage("§8§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.add("menu");

            if (sender.hasPermission("prisontycoon.admin")) {
                suggestions.addAll(Arrays.asList("setup", "reload", "stats", "give", "reset", "list"));
            }
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "setup" -> suggestions.addAll(Arrays.asList("on", "off"));
                case "give", "reset" -> {
                    // Suggère les joueurs en ligne
                    for (Player p : sender.getServer().getOnlinePlayers()) {
                        suggestions.add(p.getName());
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Quantités suggérées
            suggestions.addAll(Arrays.asList("1", "5", "10", "16", "32", "64"));
        }

        return suggestions;
    }
}