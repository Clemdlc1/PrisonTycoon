package fr.skyblock.menus;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.managers.MenuManager;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class MainMenu extends BaseMenu {

    public MainMenu(CustomSkyblock plugin, MenuManager menuManager) {
        super(plugin, menuManager);
    }

    @Override
    public void open(Player player) {
        SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().getOrCreatePlayer(player.getUniqueId(), player.getName());

        Inventory inv = createInventory(45, ChatColor.DARK_BLUE + "Menu - Skyblock");

        // Informations de l'île
        if (skyblockPlayer.hasIsland()) {
            Island island = plugin.getDatabaseManager().loadIsland(skyblockPlayer.getIslandId());
            if (island != null) {
                inv.setItem(4, createItem(Material.GRASS_BLOCK, ChatColor.GREEN + "Votre Île",
                        ChatColor.GRAY + "Nom: " + ChatColor.WHITE + island.getName(),
                        ChatColor.GRAY + "Niveau: " + ChatColor.WHITE + island.getLevel(),
                        ChatColor.GRAY + "Taille: " + ChatColor.WHITE + island.getSize() + "x" + island.getSize(),
                        ChatColor.GRAY + "Banque: " + ChatColor.WHITE + String.format("%.2f $", island.getBank()),
                        "",
                        ChatColor.YELLOW + "Clic pour se téléporter"));
            }
        } else {
            inv.setItem(4, createItem(Material.BARRIER, ChatColor.RED + "Aucune île",
                    ChatColor.GRAY + "Vous n'avez pas encore d'île !",
                    "",
                    ChatColor.YELLOW + "Clic pour créer une île"));
        }

        // Navigation
        inv.setItem(10, createItem(Material.COMPASS, ChatColor.AQUA + "Warps & Téléportation",
                ChatColor.GRAY + "Explorer les îles du serveur",
                ChatColor.GRAY + "et se téléporter chez vos amis",
                "",
                ChatColor.YELLOW + "Clic pour ouvrir"));

        inv.setItem(12, createItem(Material.PLAYER_HEAD, ChatColor.AQUA + "Gestion des Membres",
                ChatColor.GRAY + "Inviter, expulser ou gérer",
                ChatColor.GRAY + "les permissions des membres",
                "",
                ChatColor.YELLOW + "Clic pour ouvrir"));

        inv.setItem(14, createItem(Material.REDSTONE, ChatColor.AQUA + "Paramètres de l'île",
                ChatColor.GRAY + "Gérer les flags et",
                ChatColor.GRAY + "les permissions de votre île",
                "",
                ChatColor.YELLOW + "Clic pour ouvrir"));

        inv.setItem(16, createItem(Material.EMERALD, ChatColor.AQUA + "Banque de l'île",
                ChatColor.GRAY + "Gérer l'argent de votre île",
                ChatColor.GRAY + "Déposer ou retirer des fonds",
                "",
                ChatColor.YELLOW + "Clic pour ouvrir"));

        inv.setItem(19, createItem(Material.EXPERIENCE_BOTTLE, ChatColor.AQUA + "Niveau de l'île",
                ChatColor.GRAY + "Voir les détails du niveau",
                ChatColor.GRAY + "et les exigences pour progresser",
                "",
                ChatColor.YELLOW + "Clic pour ouvrir"));

        inv.setItem(21, createItem(Material.GOLDEN_APPLE, ChatColor.AQUA + "Améliorer l'île",
                ChatColor.GRAY + "Agrandir votre île",
                ChatColor.GRAY + "ou débloquer de nouvelles fonctionnalités",
                "",
                ChatColor.YELLOW + "Clic pour ouvrir"));

        inv.setItem(23, createItem(Material.PAPER, ChatColor.AQUA + "Classements",
                ChatColor.GRAY + "Voir le top des îles",
                ChatColor.GRAY + "par niveau et autres statistiques",
                "",
                ChatColor.YELLOW + "Clic pour ouvrir"));

        inv.setItem(25, createItem(Material.BOOK, ChatColor.AQUA + "Aide & Tutoriels",
                ChatColor.GRAY + "Apprendre à jouer",
                ChatColor.GRAY + "et découvrir les fonctionnalités",
                "",
                ChatColor.YELLOW + "Clic pour ouvrir"));

        // Boutons spéciaux
        if (skyblockPlayer.hasIsland()) {
            inv.setItem(40, createItem(Material.TNT, ChatColor.RED + "Supprimer l'île",
                    ChatColor.GRAY + "Attention ! Cette action",
                    ChatColor.GRAY + "est irréversible !",
                    "",
                    ChatColor.RED + "Clic pour supprimer"));
        } else {
            inv.setItem(40, createItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "Créer une île",
                    ChatColor.GRAY + "Commencer votre aventure",
                    ChatColor.GRAY + "sur votre propre île !",
                    "",
                    ChatColor.GREEN + "Clic pour créer"));
        }

        // Décoration
        fillEmptySlots(inv, Material.GRAY_STAINED_GLASS_PANE);

        player.openInventory(inv);
        setPlayerMenu(player, getMenuType());
    }

    @Override
    public void handleClick(Player player, int slot) {
        switch (slot) {
            case 4 -> { // Île principale
                SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(player.getUniqueId());
                if (skyblockPlayer != null && skyblockPlayer.hasIsland()) {
                    // Téléporter à l'île
                    player.closeInventory();
                    player.performCommand("island home");
                } else {
                    // Créer une île
                    menuManager.openSchematicMenu(player);
                }
            }
            case 10 -> menuManager.openWarpMenu(player); // Warps & Téléportation
            case 12 -> menuManager.openMembersMenu(player); // Gestion des membres
            case 14 -> menuManager.openFlagsMenu(player); // Paramètres
            case 16 -> menuManager.openBankMenu(player); // Banque
            case 19 -> { // Niveau
                player.closeInventory();
                player.performCommand("island level");
            }
            case 21 -> menuManager.openUpgradeMenu(player); // Améliorer
            case 23 -> { // Classements
                player.closeInventory();
                player.performCommand("island top");
            }
            case 25 -> { // Aide
                player.closeInventory();
                sendHelpMessage(player);
            }
            case 40 -> { // Créer/Supprimer île
                SkyblockPlayer skyblockPlayer = plugin.getDatabaseManager().loadPlayer(player.getUniqueId());
                if (skyblockPlayer != null && skyblockPlayer.hasIsland()) {
                    player.closeInventory();
                    player.performCommand("island delete");
                } else {
                    menuManager.openSchematicMenu(player);
                }
            }
        }
    }

    @Override
    public String getMenuType() {
        return "main";
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== " + ChatColor.YELLOW + "Guide Skyblock" + ChatColor.GOLD + " ===");
        player.sendMessage(ChatColor.AQUA + "1. " + ChatColor.WHITE + "Créez votre île avec /island create");
        player.sendMessage(ChatColor.AQUA + "2. " + ChatColor.WHITE + "Utilisez /island menu pour accéder aux options");
        player.sendMessage(ChatColor.AQUA + "3. " + ChatColor.WHITE + "Invitez des amis avec /island invite <joueur>");
        player.sendMessage(ChatColor.AQUA + "4. " + ChatColor.WHITE + "Explorez d'autres îles avec /island warp");
        player.sendMessage(ChatColor.AQUA + "5. " + ChatColor.WHITE + "Gérez les permissions avec les flags");
        player.sendMessage(ChatColor.AQUA + "6. " + ChatColor.WHITE + "Améliorez votre île pour débloquer de nouvelles fonctionnalités");
        player.sendMessage(ChatColor.GOLD + "Amusez-vous bien sur votre île !");
    }
}