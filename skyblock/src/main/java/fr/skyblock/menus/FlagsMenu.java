package fr.skyblock.menus;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.managers.MenuManager;
import fr.skyblock.models.Island;
import fr.skyblock.models.SkyblockPlayer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class FlagsMenu extends BaseMenu {

    public FlagsMenu(CustomSkyblock plugin, MenuManager menuManager) {
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

        if (!island.getOwner().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "Seul le propriétaire peut modifier les paramètres !");
            return;
        }

        Inventory inv = createInventory(45, ChatColor.DARK_RED + "Paramètres de l'île");

        int slot = 10;
        for (Island.IslandFlag flag : Island.IslandFlag.values()) {
            boolean enabled = island.getFlag(flag);
            Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
            String status = enabled ? ChatColor.GREEN + "Activé" : ChatColor.RED + "Désactivé";

            inv.setItem(slot, createItem(material, ChatColor.YELLOW + flag.getDescription(),
                    ChatColor.GRAY + "Statut: " + status,
                    "",
                    ChatColor.GRAY + "Description:",
                    ChatColor.WHITE + getDetailedFlagDescription(flag),
                    "",
                    ChatColor.YELLOW + "Clic pour " + (enabled ? "désactiver" : "activer")));

            slot++;
            if (slot % 9 == 8) slot += 2; // Saut de ligne avec espacement
        }

        // Bouton retour
        inv.setItem(40, createBackButton());

        fillEmptySlots(inv, Material.RED_STAINED_GLASS_PANE);

        player.openInventory(inv);
        setPlayerMenu(player, getMenuType());
        setMenuData(player, "island", island);
    }

    @Override
    public void handleClick(Player player, int slot) {
        if (slot == 40) { // Retour
            openMainMenu(player);
            return;
        }

        Island island = (Island) getMenuData(player, "island");
        if (island == null) return;

        // Déterminer quel flag a été cliqué
        Island.IslandFlag[] flags = Island.IslandFlag.values();
        int flagIndex = -1;

        if (slot >= 10 && slot <= 16) {
            flagIndex = slot - 10;
        } else if (slot >= 19 && slot <= 25) {
            flagIndex = slot - 19 + 7;
        }

        if (flagIndex >= 0 && flagIndex < flags.length) {
            Island.IslandFlag flag = flags[flagIndex];
            boolean newValue = !island.getFlag(flag);
            island.setFlag(flag, newValue);
            plugin.getDatabaseManager().saveIsland(island);

            player.sendMessage(ChatColor.GREEN + flag.getDescription() + " " +
                    (newValue ? "activé" : "désactivé") + " !");

            // Rafraîchir le menu
            open(player);
        }
    }

    @Override
    public String getMenuType() {
        return "flags";
    }

    private String getDetailedFlagDescription(Island.IslandFlag flag) {
        return switch (flag) {
            case PVP -> "Permet aux joueurs de s'attaquer entre eux sur l'île";
            case MOB_SPAWNING -> "Autorise l'apparition des monstres hostiles";
            case ANIMAL_SPAWNING -> "Autorise l'apparition des animaux pacifiques";
            case FIRE_SPREAD -> "Permet au feu de se propager naturellement";
            case EXPLOSION_DAMAGE -> "Autorise les dégâts d'explosion (TNT, Creepers)";
            case VISITOR_INTERACT -> "Permet aux visiteurs d'interagir avec les objets";
            case VISITOR_PLACE -> "Permet aux visiteurs de placer des blocs";
            case VISITOR_BREAK -> "Permet aux visiteurs de casser des blocs";
            case VISITOR_CHEST -> "Permet aux visiteurs d'ouvrir les coffres";
        };
    }
}