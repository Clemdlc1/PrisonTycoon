package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.data.TankData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Interface graphique pour configurer les Tanks - Version réorganisée
 */
public class TankGUI implements Listener {

    // Matériaux disponibles pour filtres
    private static final Material[] COMMON_ORES = {
            Material.COAL_ORE, Material.IRON_ORE, Material.GOLD_ORE,
            Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE,
            Material.REDSTONE_ORE, Material.COPPER_ORE
    };
    private static final Material[] DEEPSLATE_ORES = {
            Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_IRON_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.DEEPSLATE_DIAMOND_ORE, Material.DEEPSLATE_EMERALD_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DEEPSLATE_REDSTONE_ORE, Material.DEEPSLATE_COPPER_ORE
    };
    private static final Material[] NETHER_MATERIALS = {
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.ANCIENT_DEBRIS,
            Material.NETHERRACK, Material.SOUL_SAND, Material.SOUL_SOIL
    };
    private final PrisonTycoon plugin;
    private final Map<UUID, String> openTankGUIs = new HashMap<>();
    private final Map<UUID, String> awaitingPriceInput = new HashMap<>();
    private final Map<UUID, Material> awaitingPriceMaterial = new HashMap<>();
    private final Map<UUID, String> awaitingNameInput = new HashMap<>();

    public TankGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Ouvre la GUI principale du Tank - Layout réorganisé
     */
    public void openTankGUI(Player player, String tankId) {
        TankData tankData = plugin.getTankManager().getTankCache().get(tankId);

        if (tankData == null) {
            player.sendMessage("§c❌ Tank introuvable!");
            return;
        }

        // Vérifier la propriété
        if (!tankData.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§c❌ Vous n'êtes pas le propriétaire de ce tank!");
            return;
        }

        Inventory gui = plugin.getGUIManager().createInventory(36, "§6⚡ Tank §8- §fMenu");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.TANK_CONFIG, gui, java.util.Map.of("tank_id", tankId));
        plugin.getGUIManager().fillBorders(gui);

        // === LIGNE 1 : INFORMATIONS ===
        gui.setItem(4, createInfoItem(tankData));

        // === LIGNE 2 : GESTION ===
        gui.setItem(11, createCustomNameItem(tankData));
        gui.setItem(13, createSellAllItem());
        gui.setItem(15, createContentsItem(tankData));

        // === LIGNE 3 : ACTIONS ===
        gui.setItem(20, createBillsOverviewItem(tankData));
        gui.setItem(21, createWithdrawAllItem());
        gui.setItem(23, createFiltersMenuButton());
        gui.setItem(24, createPricesOverviewItem(tankData));

        // Boutons navigation/informations supplémentaires
        gui.setItem(31, createCloseButton());

        player.openInventory(gui);
        openTankGUIs.put(player.getUniqueId(), tankId);
    }

    /**
     * Ouvre la GUI de visualisation des prix pour un autre joueur
     */
    public void openPricesViewGUI(Player player, TankData tankData) {
        Inventory gui = plugin.getGUIManager().createInventory(54, "§6💰 Prix - Tank de " +
                plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName());
        plugin.getGUIManager().registerOpenGUI(player, GUIType.TANK_PRICES, gui);
        plugin.getGUIManager().fillBorders(gui);

        // Informations
        gui.setItem(4, createPublicInfoItem(tankData));

        if (tankData.getPrices().isEmpty()) {
            // Aucun prix configuré
            ItemStack noPrice = new ItemStack(Material.BARRIER);
            ItemMeta meta = noPrice.getItemMeta();
            plugin.getGUIManager().applyName(meta, "§c❌ Aucun prix configuré");
            plugin.getGUIManager().applyLore(meta, Arrays.asList(
                    "§7Ce tank n'achète aucun item",
                    "§7pour le moment"
            ));
            noPrice.setItemMeta(meta);
            gui.setItem(22, noPrice);
        } else {
            // Afficher les prix
            int slot = 10;
            for (Map.Entry<Material, Long> entry : tankData.getPrices().entrySet()) {
                if (slot >= 44) break;

                ItemStack priceItem = new ItemStack(entry.getKey());
                ItemMeta meta = priceItem.getItemMeta();
                plugin.getGUIManager().applyName(meta, "§f" + entry.getKey().name().toLowerCase());
                plugin.getGUIManager().applyLore(meta, Arrays.asList(
                        "§7Prix d'achat: §a" + NumberFormatter.format(entry.getValue()) + "$ §7par item",
                        "§7Stock actuel: §b" + NumberFormatter.format(tankData.getContents().getOrDefault(entry.getKey(), 0)),
                        "",
                        "§7Vendez vos " + entry.getKey().name().toLowerCase(),
                        "§7en cliquant sur ce tank!"
                ));
                priceItem.setItemMeta(meta);
                gui.setItem(slot, priceItem);

                slot++;
                if ((slot + 1) % 9 == 0) slot += 2; // Passer à la ligne suivante
            }
        }

        // Bouton fermer
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
    }

    /**
     * Ouvre la GUI Filtres & Prix (vue par défaut: manage)
     */
    public void openMaterialSelectionGUI(Player player, String tankId, int page) {
        openMaterialSelectionGUI(player, tankId, page, "manage");
    }

    /**
     * Ouvre la GUI Filtres & Prix avec vue (manage/select)
     */
    public void openMaterialSelectionGUI(Player player, String tankId, int page, String view) {
        if ("select".equalsIgnoreCase(view)) {
            // Vue sélection des matériaux (ajout)
            List<TankFilter> filters = Arrays.asList(TankFilter.values());
            int itemsPerPage = 45;
            int totalPages = Math.max(1, (int) Math.ceil(filters.size() / (double) itemsPerPage));
            page = Math.max(0, Math.min(page, totalPages - 1));

            String title = "§e🔧 Ajouter un filtre §7(§e" + (page + 1) + "§7/§e" + totalPages + "§7)";
            Inventory gui = plugin.getGUIManager().createInventory(54, title);

            int start = page * itemsPerPage;
            int end = Math.min(start + itemsPerPage, filters.size());
            int slot = 0;
            for (int i = start; i < end; i++) {
                TankFilter tf = filters.get(i);
                ItemStack it = new ItemStack(tf.material());
                ItemMeta meta = it.getItemMeta();
                plugin.getGUIManager().applyName(meta, tf.displayName());
                plugin.getGUIManager().applyLore(meta, Arrays.asList(
                        "§7Clic pour autoriser ce matériau",
                        "§7dans les filtres du tank"
                ));
                it.setItemMeta(meta);
                gui.setItem(slot++, it);
            }

            // Navigation
            gui.setItem(45, createBackButton());
            if (page > 0) {
                ItemStack prev = new ItemStack(Material.ARROW);
                ItemMeta pMeta = prev.getItemMeta();
                plugin.getGUIManager().applyName(pMeta, "§7← Page précédente");
                prev.setItemMeta(pMeta);
                gui.setItem(46, prev);
            }
            if (page < totalPages - 1) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta nMeta = next.getItemMeta();
                plugin.getGUIManager().applyName(nMeta, "§7Page suivante →");
                next.setItemMeta(nMeta);
                gui.setItem(52, next);
            }
            gui.setItem(53, createCloseButton());

            plugin.getGUIManager().registerOpenGUI(player, GUIType.TANK_FILTER, gui, java.util.Map.of(
                    "tank_filter_view", "select",
                    "tank_filter_page", String.valueOf(page),
                    "tank_id", tankId
            ));
            player.openInventory(gui);
            return;
        }

        // Vue gestion (manage) - filtres actifs + actions
        TankData tankData = plugin.getTankManager().getTankCache().get(tankId);
        if (tankData == null) return;

        int itemsPerPage = 28;
        java.util.List<Material> active = new java.util.ArrayList<>(tankData.getFilters());
        int totalPages = Math.max(1, (int) Math.ceil(active.size() / (double) itemsPerPage));
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory gui = plugin.getGUIManager().createInventory(54, "§e🔧 Filtres & Prix §7(§e" + (page + 1) + "§7/§e" + totalPages + "§7)");
        plugin.getGUIManager().fillBorders(gui);

        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, active.size());
        int slot = 10;
        for (int i = start; i < end; i++) {
            gui.setItem(slot++, createMaterialFilterItem(tankData, active.get(i)));
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        // Ajouter un filtre
        ItemStack add = new ItemStack(Material.GREEN_WOOL);
        ItemMeta aMeta = add.getItemMeta();
        plugin.getGUIManager().applyName(aMeta, "§a+ Ajouter un filtre");
        plugin.getGUIManager().applyLore(aMeta, java.util.List.of("§7Ouvre la sélection des matériaux"));
        add.setItemMeta(aMeta);
        gui.setItem(48, add);

        // Navigation
        gui.setItem(45, createBackButton());
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pMeta = prev.getItemMeta();
            plugin.getGUIManager().applyName(pMeta, "§7← Page précédente");
            prev.setItemMeta(pMeta);
            gui.setItem(46, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nMeta = next.getItemMeta();
            plugin.getGUIManager().applyName(nMeta, "§7Page suivante →");
            next.setItemMeta(nMeta);
            gui.setItem(52, next);
        }
        gui.setItem(49, createCloseButton());

        plugin.getGUIManager().registerOpenGUI(player, GUIType.TANK_FILTER, gui, java.util.Map.of(
                "tank_filter_view", "manage",
                "tank_filter_page", String.valueOf(page),
                "tank_id", tankId
        ));
        player.openInventory(gui);
    }

    /**
     * Crée l'item d'information du tank
     */
    private ItemStack createInfoItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.BARREL);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6⚡ Informations du Tank");

        List<String> lore = Arrays.asList(
                "§7Propriétaire: §e" + plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName(),
                "§7Nom personnalisé: " + (tankData.hasCustomName() ? "§f" + tankData.getCustomName() : "§cAucun"),
                "",
                "§7Capacité: §b" + NumberFormatter.format(tankData.getTotalItems()) + "§7/§b" + NumberFormatter.format(TankData.MAX_CAPACITY),
                "§7Filtres actifs: §e" + tankData.getFilters().size(),
                "§7Prix configurés: §e" + tankData.getPrices().size(),
                "§7Position: " + (tankData.isPlaced() ? "§aPlacé" : "§cNon placé"),
                "",
                "§7Ce tank achète automatiquement les items",
                "§7avec de l'argent prélevé sur votre compte"
        );
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    // === CRÉATION DES ITEMS ===

    /**
     * Crée l'item d'information publique du tank
     */
    private ItemStack createPublicInfoItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.BARREL);
        ItemMeta meta = item.getItemMeta();
        String ownerName = plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName();

        plugin.getGUIManager().applyName(meta, "§6⚡ Tank de " + ownerName);

        List<String> lore = new ArrayList<>();
        if (tankData.hasCustomName()) {
            lore.add("§7\"§f" + tankData.getCustomName() + "§7\"");
            lore.add("");
        }

        lore.addAll(Arrays.asList(
                "§7Propriétaire: §e" + ownerName,
                "§7Items en stock: §b" + NumberFormatter.format(tankData.getTotalItems()),
                "§7Types d'items acceptés: §e" + tankData.getPrices().size(),
                "",
                "§7Cliquez sur ce tank pour lui vendre",
                "§7vos items aux prix affichés!"
        ));

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de nom personnalisé
     */
    private ItemStack createCustomNameItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§e✏ Nom personnalisé");

        List<String> lore = new ArrayList<>();
        lore.add("§7Nom actuel: " + (tankData.hasCustomName() ? "§f" + tankData.getCustomName() : "§cAucun"));
        lore.add("");
        lore.add("§7Clic gauche: §eChanger le nom");
        lore.add("§7Clic droit: §cSupprimer le nom");
        lore.add("");
        lore.add("§7Le nom apparaîtra au-dessus du tank");
        lore.add("§7en plus des informations techniques");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de gestion des filtres
     */
    private ItemStack createFiltersItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§e🔧 Filtres actifs");

        List<String> lore = new ArrayList<>();
        lore.add("§7Nombre de filtres: §e" + tankData.getFilters().size());
        lore.add("");

        if (tankData.getFilters().isEmpty()) {
            lore.add("§c Aucun filtre configuré");
            lore.add("§7Ajoutez des filtres pour que le tank");
            lore.add("§7puisse acheter des items");
        } else {
            lore.add("§7Matériaux acceptés:");
            int count = 0;
            for (Material material : tankData.getFilters()) {
                if (count >= 4) {
                    lore.add("§8▸ §7... et " + (tankData.getFilters().size() - 4) + " autres");
                    break;
                }
                lore.add("§8▸ §7" + material.name().toLowerCase());
                count++;
            }
        }

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Bouton d'accès au sous-menu Filtres/Prix
     */
    private ItemStack createFiltersMenuButton() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§e🔧 Filtres & Prix");
        plugin.getGUIManager().applyLore(meta, List.of(
                "§7Ouvrir la gestion des filtres",
                "§7et configuration des prix"
        ));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de contenu du tank
     */
    private ItemStack createContentsItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§b📦 Contenu stocké");

        List<String> lore = new ArrayList<>();
        lore.add("§7Total: §b" + NumberFormatter.format(tankData.getTotalItems()) + " items");
        double percentage = (double) tankData.getTotalItems() / TankData.MAX_CAPACITY * 100;
        lore.add("§7Remplissage: §e" + String.format("%.2f", percentage) + "%");
        lore.add("");

        if (tankData.getContents().isEmpty()) {
            lore.add("§7Le tank est vide");
        } else {
            lore.add("§7Contenu stocké:");
            int count = 0;
            for (Map.Entry<Material, Integer> entry : tankData.getContents().entrySet()) {
                if (count >= 4) {
                    lore.add("§8▸ §7... et " + (tankData.getContents().size() - 4) + " autres types");
                    break;
                }
                lore.add("§8▸ §b" + NumberFormatter.format(entry.getValue()) + "x §7" +
                        entry.getKey().name().toLowerCase());
                count++;
            }
        }

        lore.add("");
        lore.add("§7Clic: §bRécupérer tout le contenu");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Bouton pour vendre tout le contenu (items + billets) avec bonus du joueur
     */
    private ItemStack createSellAllItem() {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6💰 Vendre le contenu");
        plugin.getGUIManager().applyLore(meta, List.of(
                "§7Vend tout le contenu du tank",
                "§7en appliquant vos bonus de vente"
        ));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Aperçu des billets stockés et accès au sous-menu
     */
    private ItemStack createBillsOverviewItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§f🧾 Billets stockés");
        int total = tankData.getBills().values().stream().mapToInt(Integer::intValue).sum();
        List<String> lore = new ArrayList<>();
        lore.add("§7Total billets: §b" + NumberFormatter.format(total));
        if (total == 0) {
            lore.add("§7Aucun billet pour le moment");
        } else {
            int count = 0;
            for (var e : tankData.getBills().entrySet()) {
                if (count >= 5) {
                    lore.add("§8▸ §7... et d'autres");
                    break;
                }
                lore.add("§8▸ §fTier " + e.getKey() + " §7x §b" + NumberFormatter.format(e.getValue()));
                count++;
            }
        }
        lore.add("");
        lore.add("§7Clic: §bOuvrir le menu des billets");
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de vue d'ensemble des prix
     */
    private ItemStack createPricesOverviewItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6💰 Vue d'ensemble des prix");

        List<String> lore = new ArrayList<>();
        lore.add("§7Prix configurés: §e" + tankData.getPrices().size());
        lore.add("");

        if (tankData.getPrices().isEmpty()) {
            lore.add("§c Aucun prix configuré");
            lore.add("§7Configurez les prix pour chaque");
            lore.add("§7matériau en cliquant dessus");
        } else {
            lore.add("§7Prix d'achat configurés:");
            int count = 0;
            for (Map.Entry<Material, Long> entry : tankData.getPrices().entrySet()) {
                if (count >= 4) {
                    lore.add("§8▸ §7... et " + (tankData.getPrices().size() - 4) + " autres");
                    break;
                }
                lore.add("§8▸ §7" + entry.getKey().name().toLowerCase() + " §a" +
                        NumberFormatter.format(entry.getValue()) + "$/item");
                count++;
            }
        }

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour ajouter un filtre
     */
    private ItemStack createAddFilterItem() {
        ItemStack item = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§a+ Ajouter un filtre");

        List<String> lore = Arrays.asList(
                "§7Ouvre la sélection de matériaux",
                "§7pour ajouter un nouveau filtre",
                "",
                "§7Vous pouvez filtrer n'importe",
                "§7quel matériau ou bloc du jeu"
        );
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour vider tous les filtres
     */
    private ItemStack createClearFiltersItem() {
        ItemStack item = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c✗ Vider les filtres");

        List<String> lore = Arrays.asList(
                "§7Supprime tous les filtres",
                "§7et prix configurés",
                "",
                "§c⚠ Action irréversible!"
        );
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour vider tous les prix
     */
    private ItemStack createClearPricesItem() {
        ItemStack item = new ItemStack(Material.ORANGE_WOOL);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6✗ Réinitialiser les prix");

        List<String> lore = Arrays.asList(
                "§7Remet tous les prix à zéro",
                "§7sans supprimer les filtres",
                "",
                "§6⚠ Action irréversible!"
        );
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour retirer tout le contenu
     */
    private ItemStack createWithdrawAllItem() {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c📤 Récupérer le contenu");

        List<String> lore = Arrays.asList(
                "§7Transfère tout le contenu du tank",
                "§7vers votre inventaire",
                "",
                "§c⚠ Vérifiez que vous avez assez de place!"
        );
        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un item de filtre pour un matériau
     */
    private ItemStack createMaterialFilterItem(TankData tankData, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        boolean isFiltered = tankData.getFilters().contains(material);
        long price = tankData.getPrice(material);

        plugin.getGUIManager().applyName(meta, (isFiltered ? "§a✓ " : "§c✗ ") + "§f" + material.name().toLowerCase());

        List<String> lore = new ArrayList<>();
        lore.add("§7Statut: " + (isFiltered ? "§aAccepté" : "§cRefusé"));

        if (isFiltered) {
            if (price > 0) {
                lore.add("§7Prix: §a" + NumberFormatter.format(price) + "$/item");
                int stock = tankData.getContents().getOrDefault(material, 0);
                lore.add("§7Stock: §b" + NumberFormatter.format(stock) + " items");
            } else {
                lore.add("§7Prix: §c⚠ Non configuré");
            }
        }

        lore.add("");
        lore.add("§7Clic gauche: §e" + (isFiltered ? "Désactiver" : "Activer"));
        if (isFiltered) {
            lore.add("§7Clic droit: §eConfigurer le prix");
        }

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un item de sélection de matériau
     */
    private ItemStack createMaterialSelectionItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§f" + material.name().toLowerCase());
        plugin.getGUIManager().applyLore(meta, Arrays.asList(
                "§7Clic pour ajouter ce matériau",
                "§7aux filtres du tank",
                "",
                "§7Vous pourrez ensuite configurer",
                "§7le prix d'achat pour cet item"
        ));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§7← Retour");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retour au menu principal"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton fermer
     */
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§c✗ Fermer");
        plugin.getGUIManager().applyLore(meta, List.of("§7Fermer cette interface"));
        item.setItemMeta(meta);
        return item;
    }

    // Nouveau handler centralisé appelé par GUIListener
    public void handleClick(Player player, int slot, ItemStack clicked, ClickType clickType) {
        GUIType guiType = plugin.getGUIManager().getOpenGUIType(player);
        player.sendMessage("1");
        if (guiType == null) return;
        player.sendMessage("2");
        String tankId = plugin.getGUIManager().getGUIData(player, "tank_id");
        if (tankId == null) {
            tankId = openTankGUIs.get(player.getUniqueId());
        }
        if (tankId == null) return;
        player.sendMessage("3");

        switch (guiType) {
            case TANK_CONFIG -> {
                // Actions du menu principal
                if (clicked == null || clicked.getType() == Material.AIR) return;
                player.sendMessage("4");
                handleMainGUIClick(player, clicked, clickType, tankId);
            }
            case TANK_FILTER -> {
                String view = plugin.getGUIManager().getGUIData(player, "tank_filter_view");
                String pageStr = plugin.getGUIManager().getGUIData(player, "tank_filter_page");
                int page = 0;
                try {
                    if (pageStr != null) page = Integer.parseInt(pageStr);
                } catch (NumberFormatException ignored) {
                }

                if ("select".equalsIgnoreCase(view)) {
                    int itemsPerPage = 45;
                    int totalPages = Math.max(1, (int) Math.ceil(TankFilter.values().length / (double) itemsPerPage));
                    if (slot == 45) {
                        openMaterialSelectionGUI(player, tankId, 0, "manage");
                        return;
                    }
                    if (slot == 46 && page > 0) {
                        openMaterialSelectionGUI(player, tankId, page - 1, "select");
                        return;
                    }
                    if (slot == 52 && page < totalPages - 1) {
                        openMaterialSelectionGUI(player, tankId, page + 1, "select");
                        return;
                    }
                    if (slot == 53) {
                        player.closeInventory();
                        return;
                    }
                    if (clicked != null && clicked.getType() != Material.AIR) {
                        handleMaterialSelection(player, clicked, tankId);
                        openMaterialSelectionGUI(player, tankId, 0, "manage");
                    }
                    return;
                }

                // manage view
                if (slot == 45) {
                    openTankGUI(player, tankId);
                    return;
                }
                if (slot == 46 && page > 0) {
                    openMaterialSelectionGUI(player, tankId, page - 1, "manage");
                    return;
                }
                if (slot == 52) {
                    openMaterialSelectionGUI(player, tankId, page + 1, "manage");
                    return;
                }
                if (slot == 49) {
                    player.closeInventory();
                    return;
                }
                if (slot == 48) {
                    openMaterialSelectionGUI(player, tankId, 0, "select");
                    return;
                }

                if (clicked != null && clicked.getType() != Material.AIR) {
                    TankData tankData = plugin.getTankManager().getTankCache().get(tankId);
                    if (tankData != null && tankData.getFilters().contains(clicked.getType())) {
                        handleMaterialFilter(player, tankData, clicked.getType(), clickType);
                    }
                }
            }
            case TANK_BILLS -> {
                TankData tankData = plugin.getTankManager().getTankCache().get(tankId);
                if (tankData == null || clicked == null) return;
                if (clicked.getType() == Material.GOLD_BLOCK) {
                    long total = 0;
                    int qty = 0;
                    for (java.util.Map.Entry<Integer, Integer> e : tankData.getBills().entrySet()) {
                        total += plugin.getTankManager().getBillValue(e.getKey()) * e.getValue();
                        qty += e.getValue();
                    }
                    if (qty == 0) {
                        player.sendMessage("§c❌ Aucun billet à vendre!");
                        return;
                    }
                    double global = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, fr.prisontycoon.managers.GlobalBonusManager.BonusCategory.SELL_BONUS);
                    long finalValue = Math.round(total * global);
                    var pdata = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                    pdata.addCoins(finalValue);
                    tankData.clearBills();
                    plugin.getTankManager().saveTank(tankData);
                    plugin.getTankManager().updateTankNameTag(tankData);
                    player.sendMessage("§a✓ Vendu §b" + NumberFormatter.format(qty) + " §abillets pour §e" + NumberFormatter.format(finalValue) + "$ §a(avec bonus)");
                    openBillsMenu(player, tankData);
                    return;
                }
                if (clicked.getType() == Material.CHEST_MINECART) {
                    int moved = 0;
                    java.util.Map<Integer, Integer> map = new java.util.HashMap<>(tankData.getBills());
                    for (java.util.Map.Entry<Integer, Integer> e : map.entrySet()) {
                        int tier = e.getKey();
                        int amount = e.getValue();
                        while (amount > 0 && player.getInventory().firstEmpty() != -1) {
                            int give = Math.min(64, amount);
                            ItemStack bill = plugin.getTankManager().createBillForTier(tier);
                            bill.setAmount(give);
                            player.getInventory().addItem(bill);
                            tankData.removeBills(tier, give);
                            amount -= give;
                            moved += give;
                        }
                    }
                    if (moved > 0) {
                        plugin.getTankManager().saveTank(tankData);
                        plugin.getTankManager().updateTankNameTag(tankData);
                        player.sendMessage("§a✓ Récupéré §b" + NumberFormatter.format(moved) + " §abillets depuis le tank");
                    } else {
                        player.sendMessage("§c❌ Inventaire plein ou aucun billet!");
                    }
                    openBillsMenu(player, tankData);
                    return;
                }
                if (slot == 49) { // back
                    openTankGUI(player, tankData.getId());
                }
            }
            case TANK_PRICES -> {
                if (clicked != null && clicked.getType() == Material.BARRIER) {
                    player.closeInventory();
                }
            }
            default -> {
            }
        }
    }

    /**
     * Gère les clics dans la GUI principale
     */
    private void handleMainGUIClick(Player player, ItemStack clicked, ClickType clickType, String tankId) {
        TankData tankData = plugin.getTankManager().getTankCache().get(tankId);
        if (tankData == null) return;

        Material clickedType = clicked.getType();

        switch (clickedType) {
            case NAME_TAG -> handleCustomName(player, tankData, clickType);
            case GOLD_INGOT -> handleSellAllFromGUI(player, tankData);
            case GREEN_WOOL, HOPPER -> openMaterialSelectionGUI(player, tankId, 0);
            case RED_WOOL -> handleClearFilters(player, tankData);
            case ORANGE_WOOL -> handleClearPrices(player, tankData);
            case ENDER_CHEST -> handleWithdrawAll(player, tankData);
            case PAPER -> openBillsMenu(player, tankData);
            default -> {
                // Vérifier si c'est un matériau filtrable
                if (isMaterialFilterItem(clicked)) {
                    handleMaterialFilter(player, tankData, clickedType, clickType);
                }
            }
        }
    }

    private void openBillsMenu(Player player, TankData tankData) {
        // Simple listing avec options vendre tout / récupérer tout
        Inventory gui = plugin.getGUIManager().createInventory(54, "§f🧾 Billets");
        plugin.getGUIManager().fillBorders(gui);
        int slot = 10;
        for (var e : tankData.getBills().entrySet()) {
            if (slot > 43) break;
            int tier = e.getKey();
            int amount = e.getValue();
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta meta = it.getItemMeta();
            plugin.getGUIManager().applyName(meta, "§fBillet Tier " + tier);
            plugin.getGUIManager().applyLore(meta, List.of(
                    "§7Tier " + tier + " (" + NumberFormatter.format(plugin.getTankManager().getBillValue(tier)) + "$)",
                    "§7Utiliser /sell ou un tank pour vendre vos billets",
                    "",
                    "§7Quantité: §b" + NumberFormatter.format(amount)
            ));
            it.setItemMeta(meta);
            gui.setItem(slot++, it);
            if ((slot + 1) % 9 == 0) slot += 2;
        }
        // Boutons actions
        ItemStack sellAll = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta sMeta = sellAll.getItemMeta();
        plugin.getGUIManager().applyName(sMeta, "§6Vendre tous les billets");
        sellAll.setItemMeta(sMeta);
        gui.setItem(48, sellAll);

        ItemStack takeAll = new ItemStack(Material.CHEST_MINECART);
        ItemMeta tMeta = takeAll.getItemMeta();
        plugin.getGUIManager().applyName(tMeta, "§bRécupérer tous les billets");
        takeAll.setItemMeta(tMeta);
        gui.setItem(50, takeAll);

        gui.setItem(49, createBackButton());

        plugin.getGUIManager().registerOpenGUI(player, GUIType.TANK_BILLS, gui, java.util.Map.of("tank_id", tankData.getId()));
        player.openInventory(gui);
    }

    private void handleSellAllFromGUI(Player player, TankData tankData) {
        // Utilise la logique de SellHand (sans durabilité, bonus perso s'applique via GlobalBonusManager)
        var fakeSellHand = plugin.getSellHandManager();
        if (fakeSellHand == null) return;
        // Reproduire le calcul simplifié ici
        long totalValue = 0;
        int totalItems = 0;
        for (var entry : tankData.getContents().entrySet()) {
            long price = plugin.getConfigManager().getSellPrice(entry.getKey());
            if (price > 0) {
                totalValue += price * entry.getValue();
                totalItems += entry.getValue();
            }
        }
        for (var e : tankData.getBills().entrySet()) {
            long v = plugin.getTankManager().getBillValue(e.getKey());
            if (v > 0) {
                totalValue += v * e.getValue();
                totalItems += e.getValue();
            }
        }
        if (totalValue <= 0) {
            player.sendMessage("§c❌ Rien à vendre!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        double global = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, fr.prisontycoon.managers.GlobalBonusManager.BonusCategory.SELL_BONUS);
        long finalValue = Math.round(totalValue * global);
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        data.addCoins(finalValue);
        tankData.clearContents();
        tankData.clearBills();
        plugin.getTankManager().saveTank(tankData);
        plugin.getTankManager().updateTankNameTag(tankData);
        player.sendMessage("§a✓ Vendu §b" + NumberFormatter.format(totalItems) + " §aitems/billets pour §e" + NumberFormatter.format(finalValue) + "$ §a(avec bonus)");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        openTankGUI(player, tankData.getId());
    }

    /**
     * Gère la sélection de matériau
     */
    private void handleMaterialSelection(Player player, ItemStack clicked, String tankId) {
        if (clicked.getType() == Material.ARROW) {
            openTankGUI(player, tankId);
            return;
        }

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        TankData tankData = plugin.getTankManager().getTankCache().get(tankId);
        if (tankData == null) return;

        Material material = clicked.getType();
        if (tankData.getFilters().contains(material)) {
            player.sendMessage("§c❌ Ce matériau est déjà filtré!");
            return;
        }

        tankData.addFilter(material);

        player.sendMessage("§a✓ Filtre ajouté pour " + material.name().toLowerCase());
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);

        openTankGUI(player, tankId);
    }

    /**
     * Gère le nom personnalisé
     */
    private void handleCustomName(Player player, TankData tankData, ClickType clickType) {
        if (clickType == ClickType.LEFT) {
            // Changer le nom
            player.closeInventory();
            player.sendMessage("§e💬 Tapez le nouveau nom personnalisé pour votre tank:");
            player.sendMessage("§7Vous pouvez utiliser les codes couleur avec & (ex: &6Tank &cRapide)");
            player.sendMessage("§7Tapez 'cancel' pour annuler");

            awaitingNameInput.put(player.getUniqueId(), tankData.getId());

        } else if (clickType == ClickType.RIGHT) {
            // Supprimer le nom
            if (tankData.hasCustomName()) {
                tankData.setCustomName(null);
                player.sendMessage("§c✗ Nom personnalisé supprimé");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);

                // Mettre à jour le nametag si placé
                if (tankData.isPlaced()) {
                    plugin.getTankManager().updateTankNameTag(tankData);
                }

                openTankGUI(player, tankData.getId());
            } else {
                player.sendMessage("§c❌ Ce tank n'a pas de nom personnalisé!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Gère la suppression de tous les filtres
     */
    private void handleClearFilters(Player player, TankData tankData) {
        if (tankData.getFilters().isEmpty()) {
            player.sendMessage("§c❌ Aucun filtre à supprimer!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int filterCount = tankData.getFilters().size();
        tankData.clearFilters();

        player.sendMessage("§c✗ Supprimé " + filterCount + " filtres et tous les prix!");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

        openTankGUI(player, tankData.getId());
    }

    /**
     * Gère la réinitialisation des prix
     */
    private void handleClearPrices(Player player, TankData tankData) {
        if (tankData.getPrices().isEmpty()) {
            player.sendMessage("§c❌ Aucun prix à réinitialiser!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int priceCount = tankData.getPrices().size();
        for (Material material : tankData.getFilters()) {
            tankData.setPrice(material, 0L);
        }

        player.sendMessage("§6✗ Réinitialisé " + priceCount + " prix!");
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 0.8f);

        openTankGUI(player, tankData.getId());
    }

    /**
     * Gère le retrait de tout le contenu
     */
    private void handleWithdrawAll(Player player, TankData tankData) {
        if (tankData.getContents().isEmpty()) {
            player.sendMessage("§c❌ Le tank est vide!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int transferredItems = 0;
        Map<Material, Integer> contents = new HashMap<>(tankData.getContents());

        for (Map.Entry<Material, Integer> entry : contents.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            // Créer les ItemStacks et les donner au joueur
            while (amount > 0) {
                int stackSize = Math.min(amount, material.getMaxStackSize());
                ItemStack item = new ItemStack(material, stackSize);

                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item);
                    tankData.removeItems(material, stackSize);
                    transferredItems += stackSize;
                    amount -= stackSize;
                } else {
                    break; // Inventaire plein
                }
            }
        }

        if (transferredItems > 0) {
            player.sendMessage("§a✓ Transféré " + NumberFormatter.format(transferredItems) + " items vers votre inventaire!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);

            // Mettre à jour le nametag si placé
            if (tankData.isPlaced()) {
                plugin.getTankManager().updateTankNameTag(tankData);
            }

            openTankGUI(player, tankData.getId());
        } else {
            player.sendMessage("§c❌ Votre inventaire est plein!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * Gère les filtres de matériaux
     */
    private void handleMaterialFilter(Player player, TankData tankData, Material material, ClickType clickType) {
        if (clickType == ClickType.LEFT) {
            // Activer/désactiver le filtre
            if (tankData.getFilters().contains(material)) {
                tankData.removeFilter(material);
                player.sendMessage("§c✗ Filtre désactivé pour " + material.name().toLowerCase());
            } else {
                tankData.addFilter(material);
                player.sendMessage("§a✓ Filtre activé pour " + material.name().toLowerCase());
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);

            // Mettre à jour la GUI
            openMaterialSelectionGUI(player, tankData.getId(), 0);

        } else if (clickType == ClickType.RIGHT && tankData.getFilters().contains(material)) {
            // Configurer le prix
            player.closeInventory();
            player.sendMessage("§e💬 Tapez le prix d'achat pour " + material.name().toLowerCase() + " (en $ par item):");
            player.sendMessage("§7Tapez 'cancel' pour annuler ou '0' pour désactiver");

            awaitingPriceInput.put(player.getUniqueId(), tankData.getId());
            awaitingPriceMaterial.put(player.getUniqueId(), material);
        }
    }

    /**
     * Vérifie si l'item est un filtre de matériau
     */
    private boolean isMaterialFilterItem(ItemStack item) {
        return Arrays.asList(COMMON_ORES).contains(item.getType()) ||
                Arrays.asList(DEEPSLATE_ORES).contains(item.getType()) ||
                Arrays.asList(NETHER_MATERIALS).contains(item.getType());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        String tankId = openTankGUIs.remove(player.getUniqueId());

        if (tankId != null) {
            // Sauvegarder les modifications du tank
            TankData tankData = plugin.getTankManager().getTankCache().get(tankId);
            if (tankData != null) {
                plugin.getTankManager().saveTank(tankData);

                // Mettre à jour le nametag si placé
                if (tankData.isPlaced()) {
                    plugin.getTankManager().updateTankNameTag(tankData);
                }
            }
        }
    }

    /**
     * Vérifie si un joueur est en attente de saisie pour le nom ou le prix d'un tank.
     *
     * @param player Le joueur à vérifier.
     * @return true si le joueur est en attente de saisie, false sinon.
     */
    public boolean isPlayerAwaitingInput(Player player) {
        UUID playerUUID = player.getUniqueId();
        return awaitingPriceInput.containsKey(playerUUID) || awaitingNameInput.containsKey(playerUUID);
    }

    /**
     * Appelé par ChatListener pour traiter la saisie de prix/nom sans diffuser au chat
     */
    public void handleChatInput(Player player, String rawMessage) {
        UUID uuid = player.getUniqueId();
        String tankId = awaitingPriceInput.get(uuid);
        Material material = awaitingPriceMaterial.get(uuid);
        String nameInputTankId = awaitingNameInput.get(uuid);

        if (tankId != null && material != null) {
            awaitingPriceInput.remove(uuid);
            awaitingPriceMaterial.remove(uuid);

            String message = rawMessage.trim();
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage("§c❌ Configuration du prix annulée");
                Bukkit.getScheduler().runTask(plugin, () -> openTankGUI(player, tankId));
                return;
            }
            try {
                long price = Long.parseLong(message);
                if (price < 0) {
                    player.sendMessage("§c❌ Le prix ne peut pas être négatif!");
                    return;
                }
                TankData tankData = plugin.getTankManager().getTankCache().get(tankId);
                if (tankData != null) {
                    tankData.setPrice(material, price);
                    if (price == 0) {
                        player.sendMessage("§6⚠ Prix désactivé pour " + material.name().toLowerCase());
                    } else {
                        player.sendMessage("§a✓ Prix configuré: " + NumberFormatter.format(price) + "$ /item pour " + material.name().toLowerCase());
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> openTankGUI(player, tankId));
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c❌ Veuillez entrer un nombre valide!");
            }
            return;
        }

        if (nameInputTankId != null) {
            awaitingNameInput.remove(uuid);
            String message = rawMessage.trim();
            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage("§c❌ Configuration du nom annulée");
                Bukkit.getScheduler().runTask(plugin, () -> openTankGUI(player, nameInputTankId));
                return;
            }
            if (message.length() > 32) {
                player.sendMessage("§c❌ Le nom est trop long! (32 caractères maximum)");
                return;
            }
            TankData tankData = plugin.getTankManager().getTankCache().get(nameInputTankId);
            if (tankData != null) {
                String processedName = message.replace('&', '§');
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(uuid);
                if (!playerData.hasCustomPermission("specialmine.vip")) {
                    processedName = ChatColor.stripColor(processedName);
                }
                tankData.setCustomName(processedName);
                player.sendMessage("§a✓ Nom personnalisé défini: " + processedName);
                if (tankData.isPlaced()) {
                    plugin.getTankManager().updateTankNameTag(tankData);
                }
                Bukkit.getScheduler().runTask(plugin, () -> openTankGUI(player, nameInputTankId));
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String tankId = awaitingPriceInput.get(player.getUniqueId());
        Material material = awaitingPriceMaterial.get(player.getUniqueId());
        String nameInputTankId = awaitingNameInput.get(player.getUniqueId());

        if (tankId != null && material != null) {
            // Configuration de prix
            event.setCancelled(true);

            awaitingPriceInput.remove(player.getUniqueId());
            awaitingPriceMaterial.remove(player.getUniqueId());

            String message = event.getMessage().trim();

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage("§c❌ Configuration du prix annulée");
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> openTankGUI(player, tankId));
                return;
            }

            try {
                long price = Long.parseLong(message);
                if (price < 0) {
                    player.sendMessage("§c❌ Le prix ne peut pas être négatif!");
                    event.setCancelled(true);
                    return;
                }

                TankData tankData = plugin.getTankManager().getTankCache().get(tankId);
                if (tankData != null) {
                    tankData.setPrice(material, price);

                    if (price == 0) {
                        player.sendMessage("§6⚠ Prix désactivé pour " + material.name().toLowerCase());
                    } else {
                        player.sendMessage("§a✓ Prix configuré: " + NumberFormatter.format(price) + "$/item pour " + material.name().toLowerCase());
                    }
                    event.setCancelled(true);
                    Bukkit.getScheduler().runTask(plugin, () -> openTankGUI(player, tankId));
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c❌ Veuillez entrer un nombre valide!");
            }

        } else if (nameInputTankId != null) {
            // Configuration de nom
            event.setCancelled(true);

            awaitingNameInput.remove(player.getUniqueId());

            String message = event.getMessage().trim();

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage("§c❌ Configuration du nom annulée");
                Bukkit.getScheduler().runTask(plugin, () -> openTankGUI(player, nameInputTankId));
                return;
            }

            if (message.length() > 32) {
                player.sendMessage("§c❌ Le nom est trop long! (32 caractères maximum)");
                return;
            }

            TankData tankData = plugin.getTankManager().getTankCache().get(nameInputTankId);
            if (tankData != null) {
                // Traiter les codes couleur (vérifier si VIP pour les couleurs)
                String processedName = message.replace('&', '§');

                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (!playerData.hasCustomPermission("specialmine.vip")) {
                    processedName = ChatColor.stripColor(processedName);
                }

                tankData.setCustomName(processedName);
                player.sendMessage("§a✓ Nom personnalisé défini: " + processedName);

                // Mettre à jour le nametag si placé
                if (tankData.isPlaced()) {
                    plugin.getTankManager().updateTankNameTag(tankData);
                }

                Bukkit.getScheduler().runTask(plugin, () -> openTankGUI(player, nameInputTankId));
            }
        }
    }

    public enum TankFilter {
        COAL_ORE(Material.COAL_ORE, "§fMinerai de charbon"),
        IRON_ORE(Material.IRON_ORE, "§fMinerai de fer"),
        GOLD_ORE(Material.GOLD_ORE, "§fMinerai d'or"),
        COPPER_ORE(Material.COPPER_ORE, "§fMinerai de cuivre"),
        REDSTONE_ORE(Material.REDSTONE_ORE, "§fMinerai de redstone"),
        LAPIS_ORE(Material.LAPIS_ORE, "§fMinerai de lapis"),
        DIAMOND_ORE(Material.DIAMOND_ORE, "§fMinerai de diamant"),
        EMERALD_ORE(Material.EMERALD_ORE, "§fMinerai d'émeraude"),
        DEEPSLATE_COAL(Material.DEEPSLATE_COAL_ORE, "§8Deepslate charbon"),
        DEEPSLATE_IRON(Material.DEEPSLATE_IRON_ORE, "§8Deepslate fer"),
        DEEPSLATE_GOLD(Material.DEEPSLATE_GOLD_ORE, "§8Deepslate or"),
        DEEPSLATE_COPPER(Material.DEEPSLATE_COPPER_ORE, "§8Deepslate cuivre"),
        DEEPSLATE_REDSTONE(Material.DEEPSLATE_REDSTONE_ORE, "§8Deepslate redstone"),
        DEEPSLATE_LAPIS(Material.DEEPSLATE_LAPIS_ORE, "§8Deepslate lapis"),
        DEEPSLATE_DIAMOND(Material.DEEPSLATE_DIAMOND_ORE, "§8Deepslate diamant"),
        DEEPSLATE_EMERALD(Material.DEEPSLATE_EMERALD_ORE, "§8Deepslate émeraude"),
        NETHER_QUARTZ(Material.NETHER_QUARTZ_ORE, "§cQuartz du Nether"),
        NETHER_GOLD(Material.NETHER_GOLD_ORE, "§cOr du Nether"),
        ANCIENT_DEBRIS(Material.ANCIENT_DEBRIS, "§4Débris antiques"),
        NETHERRACK(Material.NETHERRACK, "§cNetherrack"),
        SOUL_SAND(Material.SOUL_SAND, "§cSable des âmes"),
        SOUL_SOIL(Material.SOUL_SOIL, "§cTerre des âmes");

        private final Material material;
        private final String displayName;

        TankFilter(Material material, String displayName) {
            this.material = material;
            this.displayName = displayName;
        }

        public Material material() {
            return material;
        }

        public String displayName() {
            return displayName;
        }
    }
}