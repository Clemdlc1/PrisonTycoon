package fr.skyblock.managers;

import fr.skyblock.CustomSkyblock;
import fr.skyblock.menus.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager {

    private final CustomSkyblock plugin;
    private final Map<UUID, String> playerMenus = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Object>> menuData = new ConcurrentHashMap<>();

    // Sous-menus
    private final MainMenu mainMenu;
    private final MembersMenu membersMenu;
    private final FlagsMenu flagsMenu;
    private final BankMenu bankMenu;
    private final UpgradeMenu upgradeMenu;
    private final SchematicMenu schematicMenu;
    private final WarpMenu warpMenu;

    public MenuManager(CustomSkyblock plugin) {
        this.plugin = plugin;

        // Initialiser les sous-menus
        this.mainMenu = new MainMenu(plugin, this);
        this.membersMenu = new MembersMenu(plugin, this);
        this.flagsMenu = new FlagsMenu(plugin, this);
        this.bankMenu = new BankMenu(plugin, this);
        this.upgradeMenu = new UpgradeMenu(plugin, this);
        this.schematicMenu = new SchematicMenu(plugin, this);
        this.warpMenu = new WarpMenu(plugin, this);
    }

    // === MÉTHODES PUBLIQUES POUR OUVRIR LES MENUS ===

    public void openMainMenu(Player player) {
        mainMenu.open(player);
    }

    public void openMembersMenu(Player player) {
        membersMenu.open(player);
    }

    public void openFlagsMenu(Player player) {
        flagsMenu.open(player);
    }

    public void openBankMenu(Player player) {
        bankMenu.open(player);
    }

    public void openUpgradeMenu(Player player) {
        upgradeMenu.open(player);
    }

    public void openSchematicMenu(Player player) {
        schematicMenu.open(player);
    }

    public void openWarpMenu(Player player) {
        warpMenu.open(player);
    }

    public void openPlayerWarpsMenu(Player player, String targetPlayerName) {
        warpMenu.openPlayerWarps(player, targetPlayerName);
    }

    // === GESTION DES DONNÉES DE MENU ===

    public void setPlayerMenu(UUID playerId, String menuType) {
        playerMenus.put(playerId, menuType);
    }

    public String getPlayerMenu(UUID playerId) {
        return playerMenus.get(playerId);
    }

    public void removePlayerMenu(UUID playerId) {
        playerMenus.remove(playerId);
        menuData.remove(playerId);
    }

    public void setMenuData(UUID playerId, String key, Object value) {
        menuData.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public Object getMenuData(UUID playerId, String key) {
        Map<String, Object> data = menuData.get(playerId);
        return data != null ? data.get(key) : null;
    }

    public Map<String, Object> getAllMenuData(UUID playerId) {
        return menuData.get(playerId);
    }

    // === GESTIONNAIRES DE CLICS ===

    public void handleMenuClick(Player player, int slot, String menuType) {
        switch (menuType) {
            case "main" -> mainMenu.handleClick(player, slot);
            case "members" -> membersMenu.handleClick(player, slot);
            case "flags" -> flagsMenu.handleClick(player, slot);
            case "bank" -> bankMenu.handleClick(player, slot);
            case "upgrade" -> upgradeMenu.handleClick(player, slot);
            case "schematic" -> schematicMenu.handleClick(player, slot);
            case "warp" -> warpMenu.handleClick(player, slot);
            case "player_warps" -> warpMenu.handlePlayerWarpsClick(player, slot);
            case "my_warps" -> warpMenu.handleMyWarpsClick(player, slot);
        }
    }

    // === GETTERS ===

    public CustomSkyblock getPlugin() {
        return plugin;
    }

    public MainMenu getMainMenu() {
        return mainMenu;
    }

    public MembersMenu getMembersMenu() {
        return membersMenu;
    }

    public FlagsMenu getFlagsMenu() {
        return flagsMenu;
    }

    public BankMenu getBankMenu() {
        return bankMenu;
    }

    public UpgradeMenu getUpgradeMenu() {
        return upgradeMenu;
    }

    public SchematicMenu getSchematicMenu() {
        return schematicMenu;
    }

    public WarpMenu getWarpMenu() {
        return warpMenu;
    }
}