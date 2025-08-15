package fr.skyblock.listeners;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerListener implements Listener {

    private final CustomSkyblock plugin;
    private final Set<UUID> playersInSkyblockWorld = ConcurrentHashMap.newKeySet();

    public PlayerListener(CustomSkyblock plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Créer ou charger les données du joueur
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().getOrCreatePlayer(
                player.getUniqueId(), player.getName());

        // Mettre à jour la dernière connexion
        skyblockPlayer.updateLastSeen();
        plugin.getDatabaseManager().savePlayer(skyblockPlayer);

        // Message de bienvenue pour les nouveaux joueurs
        if (skyblockPlayer.getFirstJoin() == skyblockPlayer.getLastSeen()) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Bienvenue sur Skyblock !" + ChatColor.GOLD + " ===");
            player.sendMessage(ChatColor.GREEN + "Créez votre première île avec " + ChatColor.GOLD + "/island create");
            player.sendMessage(ChatColor.GREEN + "Utilisez " + ChatColor.GOLD + "/island menu" + ChatColor.GREEN + " pour accéder à toutes les options");
            player.sendMessage(ChatColor.GREEN + "Tapez " + ChatColor.GOLD + "/island help" + ChatColor.GREEN + " pour obtenir de l'aide");
            player.sendMessage("");

            // Donner de l'argent de départ
            plugin.getEconomyManager().rewardPlayer(player.getUniqueId(), 50.0, "Bonus de première connexion");
        }

        // Vérifier si le joueur est dans le monde skyblock
        checkPlayerWorld(player);

        // Traiter les invitations en attente
        processIncomingInvitations(player);
    }

    /**
     * Sauvegarde sécurisée d'une île seulement si elle est accessible
     */
    private void safeSaveIsland(Island island) {
        if (plugin.getDatabaseManager().isIslandLoaded(island.getId())) {
            plugin.getDatabaseManager().saveIsland(island);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Mettre à jour la dernière activité
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(player.getUniqueId());
        if (skyblockPlayer != null) {
            skyblockPlayer.updateLastSeen();
            plugin.getDatabaseManager().savePlayer(skyblockPlayer);
        }

        // Mettre à jour l'activité de l'île si le joueur était sur son île
        Island island = plugin.getIslandManager().getIslandAtLocation(player.getLocation());
        if (island != null && island.isMember(player.getUniqueId())) {
            island.updateActivity();
            safeSaveIsland(island);
        }

        // Nettoyer les données temporaires
        playersInSkyblockWorld.remove(player.getUniqueId());
        plugin.getInvitationManager().cleanupInvitations(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to == null) return;

        // Vérifier si le joueur se téléporte vers un monde d'île
        if (plugin.getIslandManager().isIslandWorld(to.getWorld())) {
            playersInSkyblockWorld.add(player.getUniqueId());

            // Obtenir l'île associée au monde
            Island island = plugin.getIslandManager().getIslandFromWorld(to.getWorld());
            if (island != null) {
                handleIslandEntry(player, island);
                plugin.getWorldManager().markPlayerEntered(to.getWorld().getName());
            }
        } else {
            playersInSkyblockWorld.remove(player.getUniqueId());
            resetWorldBorder(player);
            // Si le monde quitté est un monde d'île et qu'il n'a plus de joueurs, marquer le départ
            Location from = event.getFrom();
            if (from != null && from.getWorld() != null && plugin.getIslandManager().isIslandWorld(from.getWorld())) {
                if (from.getWorld().getPlayers().size() <= 1) { // le joueur qui part était le dernier
                    plugin.getWorldManager().markPlayerLeft(from.getWorld().getName());
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to == null) return;

        // Vérifier si le joueur change de monde d'île
        boolean fromIslandWorld = from.getWorld() != null && plugin.getIslandManager().isIslandWorld(from.getWorld());
        boolean toIslandWorld = to.getWorld() != null && plugin.getIslandManager().isIslandWorld(to.getWorld());

        if (fromIslandWorld != toIslandWorld || !from.getWorld().equals(to.getWorld())) {
            Island fromIsland = fromIslandWorld ? plugin.getIslandManager().getIslandFromWorld(from.getWorld()) : null;
            Island toIsland = toIslandWorld ? plugin.getIslandManager().getIslandFromWorld(to.getWorld()) : null;

            if (fromIsland != null) {
                handleIslandExit(player, fromIsland);
                if (from.getWorld() != null && from.getWorld().getPlayers().size() <= 1) {
                    plugin.getWorldManager().markPlayerLeft(from.getWorld().getName());
                }
            }
            if (toIsland != null) {
                handleIslandEntry(player, toIsland);
                if (to.getWorld() != null) {
                    plugin.getWorldManager().markPlayerEntered(to.getWorld().getName());
                }
            } else if (!toIslandWorld) {
                // Joueur quitte tous les mondes d'îles
                resetWorldBorder(player);
            }
        }
    }

    @EventHandler
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().toLowerCase();

        // Bloquer certaines commandes dans les mondes d'îles
        if (player.getWorld() != null && plugin.getIslandManager().isIslandWorld(player.getWorld())) {
            if (command.startsWith("/tp ") || command.startsWith("/teleport ")) {
                if (!player.hasPermission("skyblock.admin")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Utilisez /island warp pour vous téléporter aux îles !");
                }
            }

            if (command.startsWith("/home") && !command.startsWith("/home ")) {
                // Rediriger vers l'île du joueur
                event.setCancelled(true);
                player.performCommand("island home");
            }

            if (command.startsWith("/spawn")) {
                if (!player.hasPermission("skyblock.admin")) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Utilisez /island home pour retourner à votre île !");
                }
            }
        }

        // Commandes d'économie
        if (command.startsWith("/balance") || command.startsWith("/bal")) {
            event.setCancelled(true);
            plugin.getEconomyManager().handleBalanceCommand(player);
        }

        if (command.startsWith("/pay ")) {
            event.setCancelled(true);
            String[] args = command.split(" ");
            if (args.length >= 3) {
                String[] payArgs = {args[1], args[2]};
                plugin.getEconomyManager().handlePayCommand(player, payArgs);
            } else {
                player.sendMessage(ChatColor.RED + "Usage: /pay <joueur> <montant>");
            }
        }
    }

    // === GESTION D'ENTRÉE/SORTIE D'ÎLE ===

    private void handleIslandEntry(Player player, Island island) {
        // Ajouter comme visiteur si pas membre
        if (!island.isMember(player.getUniqueId())) {
            island.addVisitor(player.getUniqueId());
            safeSaveIsland(island);

            // Message de bienvenue
            player.sendMessage(ChatColor.GREEN + "Bienvenue sur l'île de " +
                    getPlayerName(island.getOwner()) + " !");

            // Notifier le propriétaire si en ligne
            Player owner = plugin.getServer().getPlayer(island.getOwner());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(ChatColor.YELLOW + player.getName() +
                        ChatColor.GREEN + " visite votre île !");
            }
        } else {
            // Membre ou propriétaire
            player.sendMessage(ChatColor.GREEN + "Bienvenue sur votre île !");
            island.updateActivity();
        }

        // Mettre à jour la world border
        plugin.getIslandManager().updateWorldBorder(player, island);

        // Afficher les informations de l'île
        showIslandInfo(player, island);
    }

    private void handleIslandExit(Player player, Island island) {
        // Retirer des visiteurs
        if (!island.isMember(player.getUniqueId())) {
            island.removeVisitor(player.getUniqueId());
            safeSaveIsland(island);

            player.sendMessage(ChatColor.YELLOW + "Vous quittez l'île de " +
                    getPlayerName(island.getOwner()));
        }
    }

    private void showIslandInfo(Player player, Island island) {
        if (island.isMember(player.getUniqueId())) {
            // Informations pour les membres
            player.sendMessage(ChatColor.AQUA + "Niveau: " + ChatColor.WHITE + island.getLevel() +
                    ChatColor.GRAY + " | " + ChatColor.AQUA + "Banque: " + ChatColor.WHITE +
                    plugin.getEconomyManager().formatMoney(island.getBank()));
        } else {
            // Informations pour les visiteurs
            if (island.getFlag(Island.IslandFlag.VISITOR_INTERACT)) {
                player.sendMessage(ChatColor.GREEN + "Vous pouvez interagir sur cette île !");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Île protégée - interactions limitées");
            }
        }
    }

    private void resetWorldBorder(Player player) {
        // Réinitialiser la world border à la taille du monde
        player.getWorld().getWorldBorder().reset();
    }

    private void checkPlayerWorld(Player player) {
        if (player.getWorld() != null && plugin.getIslandManager().isIslandWorld(player.getWorld())) {
            playersInSkyblockWorld.add(player.getUniqueId());

            // Vérifier s'il est sur une île
            Island island = plugin.getIslandManager().getIslandFromWorld(player.getWorld());
            if (island != null) {
                plugin.getIslandManager().updateWorldBorder(player, island);
            }
        }
    }

    private void processIncomingInvitations(Player player) {
        // Vérifier les invitations en attente
        plugin.getInvitationManager().notifyPendingInvitations(player);
    }

    private String getPlayerName(UUID playerUuid) {
        Player player = plugin.getServer().getPlayer(playerUuid);
        if (player != null) {
            return player.getName();
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(playerUuid);
        return skyblockPlayer != null ? skyblockPlayer.getName() : "Joueur inconnu";
    }
}