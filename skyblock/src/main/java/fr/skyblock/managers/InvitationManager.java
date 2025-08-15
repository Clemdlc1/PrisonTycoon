package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InvitationManager {

    private final CustomSkyblock plugin;

    // Structure: invité -> liste des invitations reçues
    private final Map<UUID, List<Invitation>> pendingInvitations = new ConcurrentHashMap<>();

    // Structure: inviteur -> liste des invitations envoyées
    private final Map<UUID, List<Invitation>> sentInvitations = new ConcurrentHashMap<>();

    public InvitationManager(CustomSkyblock plugin) {
        this.plugin = plugin;

        // Nettoyage automatique des invitations expirées toutes les 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupExpiredInvitations();
            }
        }.runTaskTimer(plugin, 6000L, 6000L); // 5 minutes
    }

    // === GESTION DES INVITATIONS ===

    public boolean sendInvitation(Player inviter, Player target) {
        if (inviter.equals(target)) {
            inviter.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous inviter vous-même !");
            return false;
        }

        SkyblockPlayer inviterData = plugin.getDatabaseManager().loadPlayer(inviter.getUniqueId());
        if (inviterData == null || !inviterData.hasIsland()) {
            inviter.sendMessage(ChatColor.RED + "Vous devez avoir une île pour inviter des joueurs !");
            return false;
        }

        Island island = plugin.getDatabaseManager().loadIsland(inviterData.getIslandId());
        if (island == null) {
            inviter.sendMessage(ChatColor.RED + "Impossible de trouver votre île !");
            return false;
        }

        // Vérifier si le joueur est le propriétaire
        if (!island.getOwner().equals(inviter.getUniqueId())) {
            inviter.sendMessage(ChatColor.RED + "Seul le propriétaire de l'île peut inviter des membres !");
            return false;
        }

        // Vérifier si le joueur cible est déjà membre
        if (island.isMember(target.getUniqueId())) {
            inviter.sendMessage(ChatColor.RED + target.getName() + " est déjà membre de votre île !");
            return false;
        }

        // Vérifier si une invitation est déjà en attente
        if (hasInvitation(target.getUniqueId(), inviter.getUniqueId())) {
            inviter.sendMessage(ChatColor.RED + "Vous avez déjà envoyé une invitation à " + target.getName() + " !");
            return false;
        }

        // Vérifier si le joueur cible a déjà une île
        SkyblockPlayer targetData = plugin.getDatabaseManager().loadPlayer(target.getUniqueId());
        if (targetData != null && targetData.hasIsland()) {
            inviter.sendMessage(ChatColor.RED + target.getName() + " a déjà sa propre île !");
            return false;
        }

        // Créer l'invitation
        Invitation invitation = new Invitation(
                inviter.getUniqueId(),
                target.getUniqueId(),
                island.getId(),
                System.currentTimeMillis() + (5 * 60 * 1000) // Expire dans 5 minutes
        );

        // Ajouter aux listes
        pendingInvitations.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>()).add(invitation);
        sentInvitations.computeIfAbsent(inviter.getUniqueId(), k -> new ArrayList<>()).add(invitation);

        // Messages
        inviter.sendMessage(ChatColor.GREEN + "Invitation envoyée à " + ChatColor.YELLOW + target.getName() + ChatColor.GREEN + " !");

        target.sendMessage("");
        target.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "INVITATION D'ÎLE" + ChatColor.GOLD + " ===");
        target.sendMessage(ChatColor.GREEN + inviter.getName() + ChatColor.YELLOW + " vous invite à rejoindre son île !");
        target.sendMessage(ChatColor.GRAY + "Nom de l'île: " + ChatColor.WHITE + island.getName());
        target.sendMessage(ChatColor.GRAY + "Niveau: " + ChatColor.WHITE + island.getLevel());
        target.sendMessage("");
        target.sendMessage(ChatColor.GREEN + "Utilisez " + ChatColor.GOLD + "/island accept " + inviter.getName() +
                ChatColor.GREEN + " pour accepter");
        target.sendMessage(ChatColor.RED + "Utilisez " + ChatColor.GOLD + "/island deny " + inviter.getName() +
                ChatColor.RED + " pour refuser");
        target.sendMessage(ChatColor.GRAY + "Cette invitation expire dans 5 minutes.");
        target.sendMessage("");

        return true;
    }

    public boolean acceptInvitation(Player player, String inviterName) {
        Player inviter = Bukkit.getPlayer(inviterName);
        if (inviter == null) {
            player.sendMessage(ChatColor.RED + "Joueur introuvable: " + inviterName);
            return false;
        }

        Invitation invitation = getInvitation(player.getUniqueId(), inviter.getUniqueId());
        if (invitation == null) {
            player.sendMessage(ChatColor.RED + "Aucune invitation de " + inviterName + " trouvée !");
            return false;
        }

        if (invitation.isExpired()) {
            removeInvitation(invitation);
            player.sendMessage(ChatColor.RED + "Cette invitation a expiré !");
            return false;
        }

        // Vérifier que le joueur n'a pas d'île
        SkyblockPlayer playerData = plugin.getDatabaseManager().getOrCreatePlayer(player.getUniqueId(), player.getName());
        if (playerData.hasIsland()) {
            player.sendMessage(ChatColor.RED + "Vous avez déjà une île ! Supprimez-la d'abord avec /island delete");
            return false;
        }

        Island island = plugin.getDatabaseManager().loadIsland(invitation.getIslandId());
        if (island == null) {
            removeInvitation(invitation);
            player.sendMessage(ChatColor.RED + "L'île n'existe plus !");
            return false;
        }

        // Ajouter le joueur comme membre
        if (plugin.getIslandManager().addMember(island, player.getUniqueId())) {
            removeInvitation(invitation);

            player.sendMessage(ChatColor.GREEN + "Vous avez rejoint l'île de " + ChatColor.YELLOW + inviterName + ChatColor.GREEN + " !");
            player.sendMessage(ChatColor.GOLD + "Utilisez /island home pour vous y téléporter !");

            if (inviter.isOnline()) {
                inviter.sendMessage(ChatColor.GREEN + player.getName() + " a rejoint votre île !");
            }

            // Notifier tous les autres membres
            notifyIslandMembers(island, ChatColor.AQUA + player.getName() + ChatColor.GREEN + " a rejoint l'île !",
                    Set.of(player.getUniqueId(), inviter.getUniqueId()));

            return true;
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors de l'ajout à l'île !");
            return false;
        }
    }

    public boolean denyInvitation(Player player, String inviterName) {
        Player inviter = Bukkit.getPlayer(inviterName);
        UUID inviterUuid;

        if (inviter != null) {
            inviterUuid = inviter.getUniqueId();
        } else {
            // Chercher par nom dans les invitations
            inviterUuid = findInviterByName(player.getUniqueId(), inviterName);
            if (inviterUuid == null) {
                player.sendMessage(ChatColor.RED + "Aucune invitation de " + inviterName + " trouvée !");
                return false;
            }
        }

        Invitation invitation = getInvitation(player.getUniqueId(), inviterUuid);
        if (invitation == null) {
            player.sendMessage(ChatColor.RED + "Aucune invitation de " + inviterName + " trouvée !");
            return false;
        }

        removeInvitation(invitation);

        player.sendMessage(ChatColor.YELLOW + "Vous avez refusé l'invitation de " + inviterName);

        if (inviter != null && inviter.isOnline()) {
            inviter.sendMessage(ChatColor.YELLOW + player.getName() + " a refusé votre invitation.");
        }

        return true;
    }

    // === MÉTHODES UTILITAIRES ===

    private boolean hasInvitation(UUID target, UUID inviter) {
        List<Invitation> invitations = pendingInvitations.get(target);
        if (invitations == null) return false;

        return invitations.stream().anyMatch(inv -> inv.getInviter().equals(inviter) && !inv.isExpired());
    }

    private Invitation getInvitation(UUID target, UUID inviter) {
        List<Invitation> invitations = pendingInvitations.get(target);
        if (invitations == null) return null;

        return invitations.stream()
                .filter(inv -> inv.getInviter().equals(inviter) && !inv.isExpired())
                .findFirst()
                .orElse(null);
    }

    private UUID findInviterByName(UUID target, String inviterName) {
        List<Invitation> invitations = pendingInvitations.get(target);
        if (invitations == null) return null;

        for (Invitation invitation : invitations) {
            Player inviter = Bukkit.getPlayer(invitation.getInviter());
            if (inviter != null && inviter.getName().equalsIgnoreCase(inviterName)) {
                return invitation.getInviter();
            }

            // Chercher dans la base de données si le joueur n'est pas en ligne
            SkyblockPlayer inviterData = plugin.getDatabaseManager().loadPlayer(invitation.getInviter());
            if (inviterData != null && inviterData.getName().equalsIgnoreCase(inviterName)) {
                return invitation.getInviter();
            }
        }

        return null;
    }

    private void removeInvitation(Invitation invitation) {
        // Retirer des invitations reçues
        List<Invitation> targetInvitations = pendingInvitations.get(invitation.getTarget());
        if (targetInvitations != null) {
            targetInvitations.remove(invitation);
            if (targetInvitations.isEmpty()) {
                pendingInvitations.remove(invitation.getTarget());
            }
        }

        // Retirer des invitations envoyées
        List<Invitation> inviterInvitations = sentInvitations.get(invitation.getInviter());
        if (inviterInvitations != null) {
            inviterInvitations.remove(invitation);
            if (inviterInvitations.isEmpty()) {
                sentInvitations.remove(invitation.getInviter());
            }
        }
    }

    public void cleanupInvitations(UUID playerUuid) {
        // Nettoyer les invitations quand un joueur se déconnecte
        pendingInvitations.remove(playerUuid);
        sentInvitations.remove(playerUuid);

        // Retirer aussi les invitations où ce joueur était l'inviteur
        pendingInvitations.values().forEach(list ->
                list.removeIf(inv -> inv.getInviter().equals(playerUuid))
        );

        // Nettoyer les listes vides
        pendingInvitations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private void cleanupExpiredInvitations() {
        long currentTime = System.currentTimeMillis();

        // Nettoyer les invitations expirées
        pendingInvitations.values().forEach(list ->
                list.removeIf(invitation -> invitation.isExpired())
        );

        sentInvitations.values().forEach(list ->
                list.removeIf(invitation -> invitation.isExpired())
        );

        // Nettoyer les listes vides
        pendingInvitations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        sentInvitations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void notifyPendingInvitations(Player player) {
        List<Invitation> invitations = pendingInvitations.get(player.getUniqueId());
        if (invitations == null || invitations.isEmpty()) return;

        // Filtrer les invitations non expirées
        List<Invitation> validInvitations = invitations.stream()
                .filter(inv -> !inv.isExpired())
                .toList();

        if (validInvitations.isEmpty()) return;

        player.sendMessage(ChatColor.GOLD + "Vous avez " + validInvitations.size() + " invitation(s) en attente !");

        for (Invitation invitation : validInvitations) {
            Player inviter = Bukkit.getPlayer(invitation.getInviter());
            String inviterName = inviter != null ? inviter.getName() : "Joueur inconnu";

            long timeLeft = (invitation.getExpirationTime() - System.currentTimeMillis()) / 1000;
            player.sendMessage(ChatColor.YELLOW + "- " + inviterName + " (" + timeLeft + "s restantes)");
        }

        player.sendMessage(ChatColor.GREEN + "Utilisez /island accept <joueur> pour accepter");
        player.sendMessage(ChatColor.RED + "Utilisez /island deny <joueur> pour refuser");
    }

    private void notifyIslandMembers(Island island, String message, Set<UUID> exclude) {
        // Notifier le propriétaire
        if (!exclude.contains(island.getOwner())) {
            Player owner = Bukkit.getPlayer(island.getOwner());
            if (owner != null && owner.isOnline()) {
                owner.sendMessage(message);
            }
        }

        // Notifier tous les membres
        for (UUID memberUuid : island.getMembers()) {
            if (!exclude.contains(memberUuid)) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    member.sendMessage(message);
                }
            }
        }
    }

    public List<String> getPendingInvitationNames(UUID playerUuid) {
        List<Invitation> invitations = pendingInvitations.get(playerUuid);
        if (invitations == null) return new ArrayList<>();

        List<String> names = new ArrayList<>();
        for (Invitation invitation : invitations) {
            if (!invitation.isExpired()) {
                Player inviter = Bukkit.getPlayer(invitation.getInviter());
                if (inviter != null) {
                    names.add(inviter.getName());
                } else {
                    SkyblockPlayer inviterData = plugin.getDatabaseManager().loadPlayer(invitation.getInviter());
                    if (inviterData != null) {
                        names.add(inviterData.getName());
                    }
                }
            }
        }

        return names;
    }

    // === CLASSE INTERNE INVITATION ===

    private static class Invitation {
        private final UUID inviter;
        private final UUID target;
        private final UUID islandId;
        private final long expirationTime;

        public Invitation(UUID inviter, UUID target, UUID islandId, long expirationTime) {
            this.inviter = inviter;
            this.target = target;
            this.islandId = islandId;
            this.expirationTime = expirationTime;
        }

        public UUID getInviter() { return inviter; }
        public UUID getTarget() { return target; }
        public UUID getIslandId() { return islandId; }
        public long getExpirationTime() { return expirationTime; }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Invitation that = (Invitation) obj;
            return inviter.equals(that.inviter) && target.equals(that.target) && islandId.equals(that.islandId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inviter, target, islandId);
        }
    }
}