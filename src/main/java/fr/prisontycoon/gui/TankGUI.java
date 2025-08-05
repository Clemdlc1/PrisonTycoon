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
import org.bukkit.event.inventory.InventoryClickEvent;
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

        Inventory gui = Bukkit.createInventory(null, 54, "§6⚡ Configuration Tank " + tankId);
        plugin.getGUIManager().registerOpenGUI(player, GUIType.TANK_CONFIG, gui);

        // === LIGNE 1 : INFORMATIONS ===
        gui.setItem(4, createInfoItem(tankData));

        // === LIGNE 2 : GESTION ===
        gui.setItem(11, createCustomNameItem(tankData));
        gui.setItem(13, createFiltersItem(tankData));
        gui.setItem(15, createContentsItem(tankData));

        // === LIGNE 3 : ACTIONS ===
        gui.setItem(20, createAddFilterItem());
        gui.setItem(21, createClearFiltersItem());
        gui.setItem(22, createWithdrawAllItem());
        gui.setItem(23, createPricesOverviewItem(tankData));
        gui.setItem(24, createClearPricesItem());

        // === LIGNES 4-5 : MINERAIS COURANTS ===
        for (int i = 0; i < Math.min(COMMON_ORES.length, 8); i++) {
            gui.setItem(28 + i, createMaterialFilterItem(tankData, COMMON_ORES[i]));
        }

        // === LIGNE 6 : MINERAIS DEEPSLATE ===
        for (int i = 0; i < Math.min(DEEPSLATE_ORES.length, 8); i++) {
            gui.setItem(37 + i, createMaterialFilterItem(tankData, DEEPSLATE_ORES[i]));
        }

        // Bordures propres
        fillBorders(gui);

        player.openInventory(gui);
        openTankGUIs.put(player.getUniqueId(), tankId);
    }

    /**
     * Ouvre la GUI de visualisation des prix pour un autre joueur
     */
    public void openPricesViewGUI(Player player, TankData tankData) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6💰 Prix - Tank de " +
                plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName());
        plugin.getGUIManager().registerOpenGUI(player, GUIType.TANK_PRICES, gui);

        // Informations
        gui.setItem(4, createPublicInfoItem(tankData));

        if (tankData.getPrices().isEmpty()) {
            // Aucun prix configuré
            ItemStack noPrice = new ItemStack(Material.BARRIER);
            ItemMeta meta = noPrice.getItemMeta();
            meta.setDisplayName("§c❌ Aucun prix configuré");
            meta.setLore(Arrays.asList(
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
                meta.setDisplayName("§f" + entry.getKey().name().toLowerCase());
                meta.setLore(Arrays.asList(
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
        fillBorders(gui);

        player.openInventory(gui);
    }

    /**
     * Ouvre la GUI de sélection de matériaux
     */
    public void openMaterialSelectionGUI(Player player, String tankId) {
        Inventory gui = Bukkit.createInventory(null, 54, "§e🔧 Ajouter un filtre - " + tankId);

        List<Material> allMaterials = Arrays.asList(Material.values());
        allMaterials = allMaterials.stream()
                .filter(material -> material.isBlock() || material.isItem())
                .filter(material -> !material.isAir())
                .sorted(Comparator.comparing(Material::name))
                .toList();

        int slot = 0;
        for (Material material : allMaterials) {
            if (slot >= 45) break; // Laisser de la place pour les boutons

            gui.setItem(slot, createMaterialSelectionItem(material));
            slot++;
        }

        // Boutons de navigation
        gui.setItem(45, createBackButton());
        gui.setItem(53, createCloseButton());

        player.openInventory(gui);
    }

    // === CRÉATION DES ITEMS ===

    /**
     * Crée l'item d'information du tank
     */
    private ItemStack createInfoItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.BARREL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6⚡ Informations du Tank");

        List<String> lore = Arrays.asList(
                "§7ID: §e" + tankData.getId(),
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
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item d'information publique du tank
     */
    private ItemStack createPublicInfoItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.BARREL);
        ItemMeta meta = item.getItemMeta();
        String ownerName = plugin.getServer().getOfflinePlayer(tankData.getOwner()).getName();

        meta.setDisplayName("§6⚡ Tank de " + ownerName);

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

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de nom personnalisé
     */
    private ItemStack createCustomNameItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e✏ Nom personnalisé");

        List<String> lore = new ArrayList<>();
        lore.add("§7Nom actuel: " + (tankData.hasCustomName() ? "§f" + tankData.getCustomName() : "§cAucun"));
        lore.add("");
        lore.add("§7Clic gauche: §eChanger le nom");
        lore.add("§7Clic droit: §cSupprimer le nom");
        lore.add("");
        lore.add("§7Le nom apparaîtra au-dessus du tank");
        lore.add("§7en plus des informations techniques");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de gestion des filtres
     */
    private ItemStack createFiltersItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e🔧 Filtres actifs");

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

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de contenu du tank
     */
    private ItemStack createContentsItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b📦 Contenu stocké");

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

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de vue d'ensemble des prix
     */
    private ItemStack createPricesOverviewItem(TankData tankData) {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6💰 Vue d'ensemble des prix");

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

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour ajouter un filtre
     */
    private ItemStack createAddFilterItem() {
        ItemStack item = new ItemStack(Material.GREEN_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a+ Ajouter un filtre");

        List<String> lore = Arrays.asList(
                "§7Ouvre la sélection de matériaux",
                "§7pour ajouter un nouveau filtre",
                "",
                "§7Vous pouvez filtrer n'importe",
                "§7quel matériau ou bloc du jeu"
        );
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour vider tous les filtres
     */
    private ItemStack createClearFiltersItem() {
        ItemStack item = new ItemStack(Material.RED_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c✗ Vider les filtres");

        List<String> lore = Arrays.asList(
                "§7Supprime tous les filtres",
                "§7et prix configurés",
                "",
                "§c⚠ Action irréversible!"
        );
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour vider tous les prix
     */
    private ItemStack createClearPricesItem() {
        ItemStack item = new ItemStack(Material.ORANGE_WOOL);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6✗ Réinitialiser les prix");

        List<String> lore = Arrays.asList(
                "§7Remet tous les prix à zéro",
                "§7sans supprimer les filtres",
                "",
                "§6⚠ Action irréversible!"
        );
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour retirer tout le contenu
     */
    private ItemStack createWithdrawAllItem() {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c📤 Récupérer le contenu");

        List<String> lore = Arrays.asList(
                "§7Transfère tout le contenu du tank",
                "§7vers votre inventaire",
                "",
                "§c⚠ Vérifiez que vous avez assez de place!"
        );
        meta.setLore(lore);
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

        meta.setDisplayName((isFiltered ? "§a✓ " : "§c✗ ") + "§f" + material.name().toLowerCase());

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

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un item de sélection de matériau
     */
    private ItemStack createMaterialSelectionItem(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§f" + material.name().toLowerCase());
        meta.setLore(Arrays.asList(
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
        meta.setDisplayName("§7← Retour");
        meta.setLore(List.of("§7Retour au menu principal"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton fermer
     */
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c✗ Fermer");
        meta.setLore(List.of("§7Fermer cette interface"));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Remplit les bordures de la GUI de manière plus propre
     */
    private void fillBorders(Inventory gui) {
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);

        // Bordures uniquement sur les côtés et coins
        int[] borderSlots = {0, 1, 2, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 51, 52, 53};
        for (int slot : borderSlots) {
            if (gui.getItem(slot) == null) {
                gui.setItem(slot, border);
            }
        }
    }

    // === GESTION DES ÉVÉNEMENTS ===

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clicked = event.getCurrentItem();
        String tankId = openTankGUIs.get(player.getUniqueId());
        if (tankId == null) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (clicked == null || clicked.getType() == Material.AIR) return;

        String title = event.getView().getTitle();

        if (title.startsWith("§e🔧 Ajouter un filtre")) {
            handleMaterialSelection(player, clicked, tankId);
        } else if (title.startsWith("§6💰 Prix - Tank de")) {
            if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
            }
        } else {
            handleMainGUIClick(player, clicked, event.getClick(), tankId);
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
            case GREEN_WOOL -> openMaterialSelectionGUI(player, tankId);
            case RED_WOOL -> handleClearFilters(player, tankData);
            case ORANGE_WOOL -> handleClearPrices(player, tankData);
            case ENDER_CHEST -> handleWithdrawAll(player, tankData);
            default -> {
                // Vérifier si c'est un matériau filtrable
                if (isMaterialFilterItem(clicked)) {
                    handleMaterialFilter(player, tankData, clickedType, clickType);
                }
            }
        }
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
            openTankGUI(player, tankData.getId());

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
}