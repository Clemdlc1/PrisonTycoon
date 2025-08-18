package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.WarpData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Adventure handled via GUIManager utilities

/**
 * Interface graphique pour les warps
 * Affiche tous les warps avec des têtes personnalisées et des sous-menus
 */
public class WarpGUI {

    private static final int ITEMS_PER_PAGE = 28;
    private static final Map<WarpData.WarpType, Material> WARP_TYPE_ICONS = Map.of(
            WarpData.WarpType.SPAWN, Material.NETHER_STAR,
            WarpData.WarpType.MINE, Material.DIAMOND_PICKAXE,
            WarpData.WarpType.CRATE, Material.CHEST,
            WarpData.WarpType.CAVE, Material.MOSSY_COBBLESTONE,
            WarpData.WarpType.SHOP, Material.EMERALD,
            WarpData.WarpType.PVP, Material.DIAMOND_SWORD,
            WarpData.WarpType.EVENT, Material.FIREWORK_ROCKET,
            WarpData.WarpType.OTHER, Material.COMPASS
    );
    private final PrisonTycoon plugin;
    // Clés pour stocker les données de manière sécurisée dans les items
    private final NamespacedKey warpIdKey;
    private final NamespacedKey pageNumberKey;
    private final NamespacedKey actionKey;

    public WarpGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        // Initialisation des clés avec le namespace du plugin pour éviter les conflits
        this.warpIdKey = new NamespacedKey(plugin, "warp_id");
        this.pageNumberKey = new NamespacedKey(plugin, "page_number");
        this.actionKey = new NamespacedKey(plugin, "warp_gui_action");
    }

    /**
     * Ouvre le menu principal des warps
     */
    public void openWarpMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(54, "§8• §6Menu des Warps §8•");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.WARP_MENU, gui);
        plugin.getGUIManager().fillBorders(gui);

        Map<WarpData.WarpType, List<WarpData>> warpsByType = plugin.getWarpManager()
                .getAccessibleWarps(player).stream()
                .collect(Collectors.groupingBy(WarpData::getType));

        int slot = 10;
        for (WarpData.WarpType type : WarpData.WarpType.values()) {
            if (warpsByType.containsKey(type)) {
                if (type == WarpData.WarpType.MINE) {
                    gui.setItem(slot, createMineSubmenuItem(player));
                } else {
                    gui.setItem(slot, createWarpTypeItem(type, warpsByType.get(type)));
                }

                slot++;
                if (slot % 9 == 8) slot += 2;
            }
        }

        // Ajout de 3 accès rapides si présents dans la config
        addQuickAccessIfPresent(gui, "crates", 38, "§d📦 §fCrates");
        addQuickAccessIfPresent(gui, "banque", 40, "§6💰 §fBanque");
        addQuickAccessIfPresent(gui, "recherche", 42, "§b🔬 §fRecherche");

        gui.setItem(49, createCloseItem());
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Ouvre le sous-menu des mines
     */
    public void openMineWarpsMenu(Player player, int page) {
        List<WarpData> mineWarps = plugin.getWarpManager().getWarpsByType(WarpData.WarpType.MINE);
        int totalPages = (int) Math.ceil((double) mineWarps.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        page = Math.max(1, Math.min(page, totalPages));

        String title = "§8• §6Mines §8(§e" + page + "§8/§e" + totalPages + "§8) •";
        Inventory gui = plugin.getGUIManager().createInventory(54, title);
        plugin.getGUIManager().fillBorders(gui);

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, mineWarps.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            gui.setItem(slot, createMineWarpItem(mineWarps.get(i), player));
            slot++;
            if (slot % 9 == 8) slot += 2;
        }

        if (page > 1) gui.setItem(48, createPageItem("§e◀ Page Précédente", page - 1));
        if (page < totalPages) gui.setItem(50, createPageItem("§ePage Suivante ▶", page + 1));

        gui.setItem(45, createBackItem());
        gui.setItem(53, createCloseItem());

        // AMÉLIORATION : Enregistrement du GUI avec son propre type
        plugin.getGUIManager().registerOpenGUI(player, GUIType.WARP_MINES_MENU, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
    }

    /**
     * Gère les clics dans les GUIs des warps.
     * La logique est maintenant séparée par GUIType pour plus de clarté.
     */
    public void handleWarpMenuClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;

        // Actions via PDC
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action != null) {
            switch (action) {
                case "close" -> {
                    player.closeInventory();
                    return;
                }
                case "back" -> {
                    openWarpMenu(player);
                    return;
                }
            }
        }

        // AMÉLIORATION : On récupère le type du GUI ouvert pour savoir comment réagir.
        GUIType openGuiType = plugin.getGUIManager().getOpenGUIType(player);
        if (openGuiType == null) return;

        switch (openGuiType) {
            case WARP_MENU:
                handleMainMenuClick(player, item);
                break;
            case WARP_MINES_MENU:
                handleMinesMenuClick(player, item);
                break;
        }
    }

    private void handleMainMenuClick(Player player, ItemStack item) {
        // Clic pour ouvrir le sous-menu des mines via PDC action
        String action = item.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if ("open_mines".equals(action)) {
            openMineWarpsMenu(player, 1);
            return;
        }

        // Clic sur un warp direct (Spawn, etc.)
        String warpId = getWarpIdFromItem(item);
        if (warpId != null) {
            if (plugin.getWarpManager().teleportToWarp(player, warpId)) {
                player.closeInventory();
            }
        }

        // Les accès rapides utilisent aussi warpId PDC, géré plus haut
    }

    private void handleMinesMenuClick(Player player, ItemStack item) {
        ItemMeta meta = item.getItemMeta();

        // Clic sur "Retour" via action PDC géré plus haut

        // AMÉLIORATION : On lit le numéro de page depuis les données de l'item
        Integer page = meta.getPersistentDataContainer().get(pageNumberKey, PersistentDataType.INTEGER);
        if (page != null) {
            openMineWarpsMenu(player, page);
            return;
        }

        // AMÉLIORATION : On lit l'ID du warp depuis les données de l'item
        String warpId = getWarpIdFromItem(item);
        if (warpId != null) {
            if (plugin.getWarpManager().teleportToWarp(player, warpId)) {
                player.closeInventory();
            }
        }
    }

    private ItemStack createWarpTypeItem(WarpData.WarpType type, List<WarpData> warps) {
        ItemStack item = new ItemStack(WARP_TYPE_ICONS.getOrDefault(type, Material.STONE));
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, type.getIcon() + " §f" + type.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7" + type.getDescription());
        lore.add(" ");
        lore.add("§7Warps disponibles: §e" + warps.size());
        lore.add(" ");

        if (warps.size() == 1) {
            lore.add("§e» Cliquez pour vous téléporter");
            meta.getPersistentDataContainer().set(warpIdKey, PersistentDataType.STRING, warps.getFirst().getId());
        } else {
            lore.add("§e» Cliquez pour voir la liste");
            // Note : Pour l'instant, cliquer ne fait rien si > 1, ce qui est le comportement attendu.
            // Si vous voulez un autre sous-menu, la logique devra être ajoutée ici.
        }
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createMineWarpItem(WarpData warp, Player player) {
        boolean canAccess = plugin.getWarpManager().canAccessWarp(player, warp);
        ItemStack item = new ItemStack(canAccess ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();

        String accessColor = canAccess ? "§a" : "§c";
        plugin.getGUIManager().applyName(meta, accessColor + "§l" + warp.getType().getIcon() + " " + warp.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7" + warp.getFormattedDescription());
        lore.add(" ");
        if (canAccess) {
            lore.add("§aVous avez accès à cette mine.");
            lore.add("§e» Cliquez pour vous téléporter");
        } else {
            lore.add("§cAccès refusé.");
            lore.add("§7Vous devez progresser pour débloquer cette mine.");
        }

        // Stockage de l'ID pour la logique de clic (invisible pour le joueur)
        meta.getPersistentDataContainer().set(warpIdKey, PersistentDataType.STRING, warp.getId());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPageItem(String name, int page) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, name);
        // AMÉLIORATION : Stockage du numéro de page de manière sécurisée
        meta.getPersistentDataContainer().set(pageNumberKey, PersistentDataType.INTEGER, page);
        plugin.getGUIManager().applyLore(meta, List.of("§7Cliquez pour changer de page."));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item du sous-menu des mines
     */
    private ItemStack createMineSubmenuItem(Player player) {
        ItemStack item = new ItemStack(WARP_TYPE_ICONS.get(WarpData.WarpType.MINE));
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6⛏ §fMines §8(§7Ouvrir§8)");
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_mines");

        List<WarpData> allMines = plugin.getWarpManager().getWarpsByType(WarpData.WarpType.MINE);
        long accessibleCount = allMines.stream().filter(w -> plugin.getWarpManager().canAccessWarp(player, w)).count();

        List<String> lore = new ArrayList<>();
        lore.add("§7Accédez à toutes les mines du serveur.");
        lore.add(" ");
        lore.add("§fProgrès :");
        lore.add("  §a✓ §7Accessibles: §e" + accessibleCount);
        lore.add("  §c✗ §7Verrouillées: §e" + (allMines.size() - accessibleCount));
        lore.add(" ");
        lore.add("§e» Cliquez pour ouvrir le sous-menu");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un item pour une mine spécifique - MODIFIÉ: Utilise des colorants
     */
    private ItemStack createMineWarpItem(WarpData warp, boolean canAccess) {
        ItemStack item = new ItemStack(canAccess ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        String accessColor = canAccess ? "§a" : "§c";
        plugin.getGUIManager().applyName(meta, accessColor + "§l" + warp.getType().getIcon() + " " + warp.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7" + warp.getFormattedDescription());
        lore.add(" ");
        if (canAccess) {
            lore.add("§aVous avez accès à cette mine.");
            lore.add("§e» Cliquez pour vous téléporter");
        } else {
            lore.add("§cAccès refusé.");
            lore.add("§7Vous devez progresser pour débloquer cette mine.");
        }
        lore.add("§8ID: §7" + warp.getId());

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    // --- Items de navigation ---
    private ItemStack createBackItem() {
        ItemStack item = new ItemStack(Material.OAK_SIGN);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§e◀ Retour");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retourner au menu principal des warps."));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c✖ Fermer");
        plugin.getGUIManager().applyLore(meta, List.of("§7Fermer ce menu."));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        item.setItemMeta(meta);
        return item;
    }

    private String getWarpIdFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(warpIdKey, PersistentDataType.STRING);
    }

    private void addQuickAccessIfPresent(Inventory gui, String warpId, int slot, String display) {
        WarpData data = plugin.getConfigManager().getWarp(warpId);
        if (data == null) return;
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, display);
        plugin.getGUIManager().applyLore(meta, List.of("§7Accès rapide au warp §e" + data.getDisplayName(), "§e» Cliquez pour vous téléporter"));
        meta.getPersistentDataContainer().set(warpIdKey, PersistentDataType.STRING, warpId);
        item.setItemMeta(meta);
        gui.setItem(slot, item);
    }
}