package fr.skyblock.menus;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.managers.MenuManager;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MembersMenu extends BaseMenu {

    public MembersMenu(CustomSkyblock plugin, MenuManager menuManager) {
        super(plugin, menuManager);
    }

    @Override
    public void open(Player player) {
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(player.getUniqueId());
        if (skyblockPlayer == null || !skyblockPlayer.hasIsland()) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas d'île !");
            return;
        }

        Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
        if (island == null) {
            player.sendMessage(ChatColor.RED + "Impossible de charger votre île !");
            return;
        }

        Inventory inv = createInventory(54, ChatColor.DARK_GREEN + "Gestion des Membres");

        // Boutons d'action (si propriétaire)
        if (island.getOwner().equals(player.getUniqueId())) {
            inv.setItem(4, createItem(Material.PLAYER_HEAD, ChatColor.GREEN + "Inviter un joueur",
                    ChatColor.GRAY + "Inviter un nouveau membre",
                    ChatColor.GRAY + "à rejoindre votre île",
                    "",
                    ChatColor.YELLOW + "Utilisez /island invite <joueur>"));

            inv.setItem(22, createItem(Material.BOOK, ChatColor.AQUA + "Invitations en attente",
                    ChatColor.GRAY + "Voir les invitations que",
                    ChatColor.GRAY + "vous avez envoyées",
                    "",
                    ChatColor.YELLOW + "Clic pour voir"));
        }

        // Statistiques
        int totalMembers = island.getMembers().size() + 1; // +1 pour le propriétaire
        int maxMembers = plugin.getConfig().getInt("advanced.max-members-per-island", 10);

        inv.setItem(13, createItem(Material.EMERALD, ChatColor.GOLD + "Statistiques de l'île",
                ChatColor.GRAY + "Membres: " + ChatColor.WHITE + totalMembers + "/" + maxMembers,
                ChatColor.GRAY + "Visiteurs actuels: " + ChatColor.WHITE + island.getVisitors().size(),
                ChatColor.GRAY + "Propriétaire: " + ChatColor.WHITE + getPlayerName(island.getOwner()),
                "",
                ChatColor.AQUA + "Informations générales"));

        // Liste des membres
        int slot = 28;

        // Propriétaire
        Player owner = Bukkit.getPlayer(island.getOwner());
        String ownerName = getPlayerName(island.getOwner());
        boolean ownerOnline = owner != null && owner.isOnline();

        ItemStack ownerHead = createPlayerHead(ownerName, ChatColor.GOLD + ownerName + " (Propriétaire)",
                ChatColor.GRAY + "Statut: " + (ownerOnline ? ChatColor.GREEN + "En ligne" : ChatColor.RED + "Hors ligne"),
                ChatColor.GRAY + "Rôle: " + ChatColor.GOLD + "Propriétaire",
                ChatColor.GRAY + "Depuis: " + ChatColor.WHITE + formatDate(island.getCreationTime()),
                "",
                ChatColor.YELLOW + "Le chef de cette île");
        inv.setItem(slot++, ownerHead);

        // Membres
        for (UUID memberUuid : island.getMembers()) {
            if (slot >= 44) break; // Limite d'affichage

            Player member = Bukkit.getPlayer(memberUuid);
            String memberName = getPlayerName(memberUuid);
            boolean isOnline = member != null && member.isOnline();

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Statut: " + (isOnline ? ChatColor.GREEN + "En ligne" : ChatColor.RED + "Hors ligne"));
            lore.add(ChatColor.GRAY + "Rôle: " + ChatColor.AQUA + "Membre");

            SkyblockPlayer memberData = plugin.getDatabaseManager().loadPlayer(memberUuid);
            if (memberData != null) {
                lore.add(ChatColor.GRAY + "Dernière connexion: " + ChatColor.WHITE + formatDate(memberData.getLastSeen()));
            }

            lore.add("");

            if (island.getOwner().equals(player.getUniqueId())) {
                lore.add(ChatColor.RED + "Clic pour expulser");
            } else {
                lore.add(ChatColor.GRAY + "Membre de l'île");
            }

            ItemStack memberHead = createPlayerHead(memberName, ChatColor.AQUA + memberName, lore);
            inv.setItem(slot++, memberHead);
        }

        // Remplir les slots vides avec des têtes de joueurs fantômes
        while (slot < 44) {
            inv.setItem(slot, createItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, ChatColor.GRAY + "Slot libre",
                    ChatColor.DARK_GRAY + "Invitez plus de joueurs",
                    ChatColor.DARK_GRAY + "pour remplir votre île !"));
            slot++;
        }

        // Boutons de navigation
        if (island.getOwner().equals(player.getUniqueId())) {
            inv.setItem(45, createItem(Material.REDSTONE, ChatColor.RED + "Expulser tous les visiteurs",
                    ChatColor.GRAY + "Faire partir tous les",
                    ChatColor.GRAY + "visiteurs actuels de l'île",
                    "",
                    ChatColor.YELLOW + "Clic pour expulser"));
        }

        inv.setItem(49, createBackButton());

        // Bouton quitter l'île (pour les membres)
        if (!island.getOwner().equals(player.getUniqueId()) && island.isMember(player.getUniqueId())) {
            inv.setItem(53, createItem(Material.BARRIER, ChatColor.RED + "Quitter l'île",
                    ChatColor.GRAY + "Quitter cette île et",
                    ChatColor.GRAY + "retourner au spawn",
                    "",
                    ChatColor.RED + "Clic pour quitter"));
        }

        fillEmptySlots(inv, Material.LIGHT_BLUE_STAINED_GLASS_PANE);

        player.openInventory(inv);
        setPlayerMenu(player, getMenuType());
        setMenuData(player, "island", island);
    }

    @Override
    public void handleClick(Player player, int slot) {
        Island island = (Island) getMenuData(player, "island");
        if (island == null) return;

        switch (slot) {
            case 4 -> { // Inviter joueur
                if (island.getOwner().equals(player.getUniqueId())) {
                    player.closeInventory();
                    player.sendMessage(ChatColor.YELLOW + "Utilisez " + ChatColor.GOLD + "/island invite <joueur>" +
                            ChatColor.YELLOW + " pour inviter un joueur !");
                }
            }
            case 22 -> { // Invitations en attente
                if (island.getOwner().equals(player.getUniqueId())) {
                    showPendingInvitations(player);
                }
            }
            case 45 -> { // Expulser visiteurs
                if (island.getOwner().equals(player.getUniqueId())) {
                    expelAllVisitors(player, island);
                }
            }
            case 49 -> openMainMenu(player); // Retour
            case 53 -> { // Quitter l'île
                if (!island.getOwner().equals(player.getUniqueId()) && island.isMember(player.getUniqueId())) {
                    player.closeInventory();
                    player.performCommand("island leave");
                }
            }
            default -> {
                // Gestion des clics sur les membres
                if (slot >= 28 && slot < 44 && island.getOwner().equals(player.getUniqueId())) {
                    handleMemberClick(player, slot - 28, island);
                }
            }
        }
    }

    @Override
    public String getMenuType() {
        return "members";
    }

    private void handleMemberClick(Player player, int memberIndex, Island island) {
        // Le premier slot (index 0) est le propriétaire, ne pas permettre l'expulsion
        if (memberIndex == 0) return;

        // Ajuster l'index pour les membres (retirer 1 car le propriétaire occupe le premier slot)
        int realMemberIndex = memberIndex - 1;

        List<UUID> members = new ArrayList<>(island.getMembers());
        if (realMemberIndex >= 0 && realMemberIndex < members.size()) {
            UUID memberToKick = members.get(realMemberIndex);
            String memberName = getPlayerName(memberToKick);

            player.closeInventory();

            // Confirmation avant expulsion
            player.sendMessage(ChatColor.YELLOW + "Êtes-vous sûr de vouloir expulser " + ChatColor.AQUA + memberName + ChatColor.YELLOW + " ?");
            player.sendMessage(ChatColor.GRAY + "Tapez " + ChatColor.GREEN + "/island kick " + memberName + ChatColor.GRAY + " pour confirmer.");
        }
    }

    private void showPendingInvitations(Player player) {
        // TODO: Implémenter l'affichage des invitations en attente
        player.sendMessage(ChatColor.YELLOW + "Fonctionnalité en cours de développement...");
        player.sendMessage(ChatColor.GRAY + "Vous pourrez bientôt voir toutes vos invitations en attente ici.");
    }

    private void expelAllVisitors(Player player, Island island) {
        int expelled = 0;

        for (UUID visitorUuid : new ArrayList<>(island.getVisitors())) {
            Player visitor = Bukkit.getPlayer(visitorUuid);
            if (visitor != null && visitor.isOnline()) {
                // Téléporter le visiteur au spawn
                visitor.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                visitor.sendMessage(ChatColor.RED + "Vous avez été expulsé de l'île de " + player.getName() + " !");
                expelled++;
            }
            island.removeVisitor(visitorUuid);
        }

        plugin.getDatabaseManager().saveIsland(island);

        player.sendMessage(ChatColor.GREEN + "Vous avez expulsé " + expelled + " visiteur(s) de votre île !");

        // Rafraîchir le menu
        open(player);
    }

    private String getPlayerName(UUID playerUuid) {
        Player player = Bukkit.getPlayer(playerUuid);
        if (player != null) {
            return player.getName();
        }

        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(playerUuid);
        return skyblockPlayer != null ? skyblockPlayer.getName() : "Joueur inconnu";
    }

    private String formatDate(long timestamp) {
        long daysSince = (System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000);
        if (daysSince == 0) {
            return "Aujourd'hui";
        } else if (daysSince == 1) {
            return "Hier";
        } else if (daysSince < 30) {
            return "Il y a " + daysSince + " jours";
        } else {
            long monthsSince = daysSince / 30;
            return "Il y a " + monthsSince + " mois";
        }
    }
}