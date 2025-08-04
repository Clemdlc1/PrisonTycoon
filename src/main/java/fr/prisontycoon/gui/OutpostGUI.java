package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.OutpostData;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.OutpostManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/**
 * Interface graphique pour la gestion des skins d'avant-poste
 * Accessible via /AP
 */
public class OutpostGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey skinKey;

    public OutpostGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "outpost_action");
        this.skinKey = new NamespacedKey(plugin, "skin_name");
    }

    /**
     * Ouvre le menu principal de l'avant-poste
     */
    public void openOutpostMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6🏰 §lAvant-Poste");

        fillWithGlass(gui);
        setupOutpostMenu(gui, player);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.OUTPOST_MAIN, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.0f);
    }

    /**
     * Configure le menu principal
     */
    private void setupOutpostMenu(Inventory gui, Player player) {
        OutpostManager outpostManager = plugin.getOutpostManager();
        OutpostData outpostData = outpostManager.getOutpostData();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Info de l'avant-poste (centre)
        gui.setItem(13, createOutpostInfoItem(outpostData, outpostManager));

        // Skins disponibles
        gui.setItem(21, createSkinsButton());

        // Statistiques
        gui.setItem(23, createStatsItem(outpostData, player));

        // Capture/État
        if (outpostManager.isCapturing(player)) {
            gui.setItem(31, createCapturingItem(player, outpostManager));
        } else if (outpostData.getController() != null && outpostData.getController().equals(player.getUniqueId())) {
            gui.setItem(31, createControllerItem(outpostData));
        } else {
            gui.setItem(31, createCaptureButton());
        }

        // Fermer
        gui.setItem(49, createCloseButton());
    }

    /**
     * Ouvre le menu de sélection des skins
     */
    public void openSkinsMenu(Player player, int page) {
        Map<String, File> availableSkins = plugin.getOutpostManager().getAvailableSkins();
        List<String> skinList = new ArrayList<>(availableSkins.keySet());

        int totalPages = (int) Math.ceil(skinList.size() / 28.0); // 28 slots pour les skins
        page = Math.max(0, Math.min(page, totalPages - 1));

        Inventory gui = Bukkit.createInventory(null, 54, "§6🎨 Skins d'Avant-Poste §7(Page " + (page + 1) + "/" + Math.max(1, totalPages) + ")");

        fillWithGlass(gui);
        setupSkinsMenu(gui, player, skinList, page);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.OUTPOST_SKINS, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Configure le menu des skins
     */
    private void setupSkinsMenu(Inventory gui, Player player, List<String> skinList, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String selectedSkin = getPlayerSelectedSkin(player);

        // Skins (slots 10-43, excluant les bordures)
        int startIndex = page * 28;
        int[] skinSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < skinSlots.length && startIndex + i < skinList.size(); i++) {
            String skinName = skinList.get(startIndex + i);
            gui.setItem(skinSlots[i], createSkinItem(skinName, selectedSkin, playerData));
        }

        // Navigation
        if (page > 0) {
            gui.setItem(45, createPreviousPageButton(page));
        }
        if (startIndex + 28 < skinList.size()) {
            gui.setItem(53, createNextPageButton(page));
        }

        // Retour
        gui.setItem(49, createBackButton());
    }

    /**
     * Crée l'item d'information de l'avant-poste
     */
    private ItemStack createOutpostInfoItem(OutpostData outpostData, OutpostManager outpostManager) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6🏰 §lAvant-Poste Central");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        if (outpostData.isControlled()) {
            lore.add("§7Contrôlé par: §6" + outpostData.getControllerName());
            long timeSince = outpostData.getTimeSinceCapture();
            lore.add("§7Depuis: §e" + formatTime(timeSince));
        } else {
            lore.add("§7Statut: §c❌ Non contrôlé");
        }

        lore.add("§7Skin actuel: §b" + outpostData.getCurrentSkin());
        lore.add("");
        lore.add("§6📍 Position: §7-14, -16, 106 (Cave)");
        lore.add("§6⏱ Temps de capture: §e30 secondes");
        lore.add("");

        if (outpostManager.isWeekendActive()) {
            lore.add("§e🎉 WEEKEND: Récompenses x2!");
            lore.add("");
        }

        lore.add("§a💰 Récompenses par minute:");
        lore.add("§7• Coins, Tokens, XP, Beacons");
        lore.add("§7• XP Métier Guerrier");
        lore.add("§7• Bonus au gang (10% des coins)");
        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton d'accès aux skins
     */
    private ItemStack createSkinsButton() {
        ItemStack item = new ItemStack(Material.ARMOR_STAND);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§b🎨 §lSkins d'Avant-Poste");
        meta.setLore(Arrays.asList(
                "",
                "§7Personnalisez l'apparence de l'avant-poste",
                "§7quand vous le contrôlez!",
                "",
                "§7Skins disponibles: §e" + plugin.getOutpostManager().getAvailableSkins().size(),
                "",
                "§a▶ Cliquez pour voir les skins!"
        ));

        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_skins");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de statistiques
     */
    private ItemStack createStatsItem(OutpostData outpostData, Player player) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e📊 §lStatistiques");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Captures totales: §6" + outpostData.getTotalCapturesCount());
        lore.add("§7Coins générés: §a" + formatNumber(outpostData.getTotalCoinsGenerated()));
        lore.add("§7Tokens générés: §b" + formatNumber(outpostData.getTotalTokensGenerated()));
        lore.add("");

        // Vérifier si le joueur contrôle actuellement l'avant-poste
        if (outpostData.getController() != null && outpostData.getController().equals(player.getUniqueId())) {
            lore.add("§a✅ Vous contrôlez actuellement l'avant-poste!");
            long timeSince = outpostData.getTimeSinceCapture();
            lore.add("§7Temps de contrôle: §6" + formatTime(timeSince));
        } else {
            lore.add("§7Vous ne contrôlez pas l'avant-poste");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item de capture en cours
     */
    private ItemStack createCapturingItem(Player player, OutpostManager outpostManager) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e⏳ §lCapture en cours...");

        int progress = outpostManager.getCaptureProgress(player);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Progression: §6" + progress + "%");
        lore.add("§7Restez sur l'avant-poste!");
        lore.add("");
        lore.add("§c❌ Quittez pour annuler");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item du contrôleur actuel
     */
    private ItemStack createControllerItem(OutpostData outpostData) {
        ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6👑 §lVous contrôlez l'avant-poste!");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Récompenses automatiques chaque minute");
        lore.add("§7Votre skin d'avant-poste est appliqué");
        lore.add("");
        long timeSince = outpostData.getTimeSinceCapture();
        lore.add("§7Temps de contrôle: §6" + formatTime(timeSince));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton de capture
     */
    private ItemStack createCaptureButton() {
        ItemStack item = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c⚔ §lCapturer l'Avant-Poste");
        meta.setLore(Arrays.asList(
                "",
                "§7Allez sur l'avant-poste et maintenez",
                "§7votre position pendant 30 secondes!",
                "",
                "§6Position: §7-14, -16, 106 (Cave)",
                "",
                "§e▶ Fermez ce menu et allez-y!"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un item de skin
     */
    private ItemStack createSkinItem(String skinName, String selectedSkin, PlayerData playerData) {
        // Déterminer si le joueur possède ce skin
        boolean owns = hasPlayerUnlockedSkin(playerData, skinName);
        boolean isSelected = skinName.equals(selectedSkin);

        Material material = isSelected ? Material.EMERALD_BLOCK :
                owns ? Material.DIAMOND_BLOCK : Material.STONE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String prefix = isSelected ? "§a✅ " : owns ? "§b🔓 " : "§c🔒 ";
        meta.setDisplayName(prefix + "§l" + formatSkinName(skinName));

        List<String> lore = new ArrayList<>();
        lore.add("");

        if (isSelected) {
            lore.add("§a✅ Skin sélectionné");
        } else if (owns) {
            lore.add("§b🔓 Skin débloqué");
            lore.add("");
            lore.add("§e▶ Cliquez pour sélectionner!");
        } else {
            lore.add("§c🔒 Skin verrouillé");
            lore.add("");
            lore.add("§7Comment débloquer:");
            lore.add("§7• Contrôlez l'avant-poste");
            lore.add("§7• Complétez des défis");
            lore.add("§7• Récompenses d'événements");
        }

        lore.add("");
        lore.add("§8ID: " + skinName);

        meta.setLore(lore);
        if (owns && !isSelected) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "select_skin");
            meta.getPersistentDataContainer().set(skinKey, PersistentDataType.STRING, skinName);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Gère les clics dans les menus
     */
    public boolean handleClick(Player player, ItemStack clickedItem, String guiType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return false;
        }

        String action = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);

        if (action == null) {
            return false;
        }

        switch (action) {
            case "open_skins" -> {
                openSkinsMenu(player, 0);
                return true;
            }
            case "select_skin" -> {
                String skinName = clickedItem.getItemMeta().getPersistentDataContainer()
                        .get(skinKey, PersistentDataType.STRING);
                if (skinName != null) {
                    selectSkin(player, skinName);
                }
                return true;
            }
            case "close" -> {
                player.closeInventory();
                return true;
            }
            case "back" -> {
                openOutpostMenu(player);
                return true;
            }
            case "prev_page" -> {
                // Gérer page précédente
                return true;
            }
            case "next_page" -> {
                // Gérer page suivante
                return true;
            }
        }

        return false;
    }

    /**
     * Sélectionne un skin pour le joueur
     */
    private void selectSkin(Player player, String skinName) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (!hasPlayerUnlockedSkin(playerData, skinName)) {
            player.sendMessage("§c❌ Vous n'avez pas débloqué ce skin!");
            return;
        }

        // TODO: Sauvegarder la sélection de skin dans PlayerData
        // setPlayerSelectedSkin(player, skinName);

        player.sendMessage("§a✅ Skin sélectionné: §6" + formatSkinName(skinName));
        player.sendMessage("§7Il sera appliqué quand vous contrôlerez l'avant-poste!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);

        // Rafraîchir le menu
        openSkinsMenu(player, 0);
    }

    // Méthodes utilitaires

    private void fillWithGlass(Inventory gui) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);

        // Remplir les bordures
        for (int i = 0; i < 9; i++) gui.setItem(i, glass);
        for (int i = 45; i < 54; i++) gui.setItem(i, glass);
        for (int i = 9; i < 45; i += 9) {
            gui.setItem(i, glass);
            gui.setItem(i + 8, glass);
        }
    }

    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c✖ Fermer");
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e← Retour");
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPreviousPageButton(int currentPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e← Page Précédente");
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "prev_page");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createNextPageButton(int currentPage) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§ePage Suivante →");
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "next_page");
        item.setItemMeta(meta);
        return item;
    }

    private String formatSkinName(String skinName) {
        return skinName.substring(0, 1).toUpperCase() + skinName.substring(1).toLowerCase();
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.1fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        } else {
            return String.valueOf(number);
        }
    }

    private boolean hasPlayerUnlockedSkin(PlayerData playerData, String skinName) {
        // TODO: Implémenter le système de déblocage des skins
        // Pour l'instant, tous les skins sont débloqués
        return true;
    }

    private String getPlayerSelectedSkin(Player player) {
        // TODO: Récupérer depuis PlayerData
        // Pour l'instant, retourner le premier skin disponible
        return plugin.getOutpostManager().getAvailableSkins().keySet().stream().findFirst().orElse("default");
    }
}