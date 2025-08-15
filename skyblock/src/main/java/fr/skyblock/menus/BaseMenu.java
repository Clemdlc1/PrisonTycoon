package fr.skyblock.menus;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.managers.MenuManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;

public abstract class BaseMenu {

    protected final CustomSkyblock plugin;
    protected final MenuManager menuManager;

    public BaseMenu(CustomSkyblock plugin, MenuManager menuManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
    }

    /**
     * Ouvre le menu pour un joueur
     */
    public abstract void open(Player player);

    /**
     * Gère un clic dans le menu
     */
    public abstract void handleClick(Player player, int slot);

    /**
     * Obtient le type de menu (pour l'identification)
     */
    public abstract String getMenuType();

    // === MÉTHODES UTILITAIRES ===

    protected ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    protected ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    protected ItemStack createPlayerHead(String playerName, String displayName, String... lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwner(playerName);
            meta.setDisplayName(displayName);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            head.setItemMeta(meta);
        }
        return head;
    }

    protected ItemStack createPlayerHead(String playerName, String displayName, List<String> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwner(playerName);
            meta.setDisplayName(displayName);
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    protected void fillEmptySlots(Inventory inv, Material material) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
            }
        }
    }

    protected Inventory createInventory(int size, String title) {
        return Bukkit.createInventory(null, size, title);
    }

    protected void setPlayerMenu(Player player, String menuType) {
        menuManager.setPlayerMenu(player.getUniqueId(), menuType);
    }

    protected void setMenuData(Player player, String key, Object value) {
        menuManager.setMenuData(player.getUniqueId(), key, value);
    }

    protected Object getMenuData(Player player, String key) {
        return menuManager.getMenuData(player.getUniqueId(), key);
    }

    protected void openMainMenu(Player player) {
        menuManager.openMainMenu(player);
    }

    // === BOUTONS COMMUNS ===

    protected ItemStack createBackButton() {
        return createItem(Material.ARROW, ChatColor.YELLOW + "Retour",
                ChatColor.GRAY + "Retourner au menu principal");
    }

    protected ItemStack createCloseButton() {
        return createItem(Material.BARRIER, ChatColor.RED + "Fermer",
                ChatColor.GRAY + "Fermer ce menu");
    }

    protected ItemStack createNextPageButton() {
        return createItem(Material.SPECTRAL_ARROW, ChatColor.GREEN + "Page suivante",
                ChatColor.GRAY + "Voir la page suivante");
    }

    protected ItemStack createPreviousPageButton() {
        return createItem(Material.SPECTRAL_ARROW, ChatColor.GREEN + "Page précédente",
                ChatColor.GRAY + "Voir la page précédente");
    }

    protected ItemStack createConfirmButton() {
        return createItem(Material.LIME_DYE, ChatColor.GREEN + "Confirmer",
                ChatColor.GRAY + "Confirmer cette action");
    }

    protected ItemStack createCancelButton() {
        return createItem(Material.RED_DYE, ChatColor.RED + "Annuler",
                ChatColor.GRAY + "Annuler cette action");
    }
}