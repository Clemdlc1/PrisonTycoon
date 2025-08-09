package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.enchantments.WeaponArmorEnchantmentManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
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

public class BookShopGUI {

    private final PrisonTycoon plugin;

    public BookShopGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    // ==================== OUVERTURES ====================
    public void openSelector(Player player) {
        Inventory gui =  plugin.getGUIManager().createInventory(27, "§a💰 §lBoutique de Livres");
        fillWithGlass(gui);

        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta pickaxeMeta = pickaxe.getItemMeta();
        plugin.getGUIManager().applyName(pickaxeMeta, "§b⛏ §lLivres PIOCHE");
        plugin.getGUIManager().applyLore(pickaxeMeta, List.of(
                "§7Accéder aux livres de pioche",
                "§e➤ Cliquez"
        ));
        pickaxe.setItemMeta(pickaxeMeta);
        gui.setItem(11, pickaxe);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        plugin.getGUIManager().applyName(swordMeta, "§c⚔ §lLivres ÉPÉE/ARMURE");
        plugin.getGUIManager().applyLore(swordMeta, List.of(
                "§7Accéder aux livres d'épée/armure",
                "§e➤ Cliquez"
        ));
        sword.setItemMeta(swordMeta);
        gui.setItem(15, sword);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.BOOK_SHOP_SELECTOR, gui);
        player.openInventory(gui);
    }

    public void openPickaxeShop(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(45, "§a💰 §lBoutique Livres §b⛏");
        fillWithGlass(gui);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta infoMeta = playerInfo.getItemMeta();
        plugin.getGUIManager().applyName(infoMeta, "§6💰 Vos Beacons");
        infoMeta.setLore(List.of("§7Beacons disponibles:", "§e" + NumberFormatter.format(playerData.getBeacons()) + " beacons"));
        playerInfo.setItemMeta(infoMeta);
        gui.setItem(4, playerInfo);

        List<EnchantmentBookManager.EnchantmentBook> allBooks = new ArrayList<>(plugin.getEnchantmentBookManager().getAllEnchantmentBooks());
        int[] slots = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
        for (int i = 0; i < Math.min(allBooks.size(), slots.length); i++) {
            EnchantmentBookManager.EnchantmentBook book = allBooks.get(i);
            gui.setItem(slots[i], createPickaxeShopItem(player, book));
        }

        // Bouton retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        plugin.getGUIManager().applyName(backMeta, "§c⬅ §lRetour");
        back.setItemMeta(backMeta);
        gui.setItem(36, back);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.BOOK_SHOP_PICKAXE, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    public void openWeaponArmorShop(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(45, "§a💰 §lBoutique §c⚔ §9🛡");
        fillWithGlass(gui);

        // Placeholder info joueur (beacons)
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        ItemStack playerInfo = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta infoMeta = playerInfo.getItemMeta();
        plugin.getGUIManager().applyName(infoMeta, "§6💰 Vos Beacons");
        infoMeta.setLore(List.of(
                "§7Beacons disponibles:",
                "§e" + NumberFormatter.format(playerData.getBeacons()) + " beacons"
        ));
        playerInfo.setItemMeta(infoMeta);
        gui.setItem(4, playerInfo);

        String[] enchants = {"tornade", "repercussion", "behead", "chasseur", "bete_traquee", "cuirasse_bestiale"};
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < enchants.length && i < slots.length; i++) {
            ItemStack item = plugin.getUniqueEnchantmentBookFactory().createShopItem(enchants[i]);
            if (item != null) gui.setItem(slots[i], item);
        }

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        plugin.getGUIManager().applyName(backMeta, "§c⬅ §lRetour");
        back.setItemMeta(backMeta);
        gui.setItem(36, back);

        plugin.getGUIManager().registerOpenGUI(player, GUIType.BOOK_SHOP_WEAPON_ARMOR, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    // ==================== HANDLERS ====================
    public void handleClick(Player player, GUIType type, int slot, ItemStack item) {
        if (type == GUIType.BOOK_SHOP_SELECTOR) {
            if (slot == 11) openPickaxeShop(player);
            if (slot == 15) openWeaponArmorShop(player);
            return;
        }

        if (type == GUIType.BOOK_SHOP_PICKAXE) {
            if (item.getType() == Material.ARROW) {
                openSelector(player);
                return;
            }
            handlePickaxeShopClick(player, item);
            return;
        }

        if (type == GUIType.BOOK_SHOP_WEAPON_ARMOR) {
            if (item.getType() == Material.ARROW) {
                openSelector(player);
                return;
            }
            handleWeaponArmorShopClick(player, item);
        }
    }

    private void handlePickaxeShopClick(Player player, ItemStack clickedItem) {
        if (!clickedItem.hasItemMeta()) return;
        ItemMeta meta = clickedItem.getItemMeta();
        String pickaxeBookId = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "shop_book_id"), PersistentDataType.STRING);
        if (pickaxeBookId == null) return;

        EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(pickaxeBookId);
        if (book == null) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long cost = book.getCost();
        if (playerData.getBeacons() < cost) {
            long missing = cost - playerData.getBeacons();
            player.sendMessage("§c💸 Pas assez de beacons! (" + NumberFormatter.format(missing) + " manquants)");
            return;
        }

        boolean success = plugin.getEnchantmentBookManager().purchasePhysicalEnchantmentBook(player, pickaxeBookId);
        if (success) {
            openPickaxeShop(player);
        }
    }

    private void handleWeaponArmorShopClick(Player player, ItemStack clickedItem) {
        if (!clickedItem.hasItemMeta()) return;
        ItemMeta meta = clickedItem.getItemMeta();
        String enchantId = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "shop_enchant_id"), PersistentDataType.STRING);
        if (enchantId == null) return;

        WeaponArmorEnchantmentManager.UniqueEnchantment enchant = plugin.getWeaponArmorEnchantmentManager().getEnchantment(enchantId);
        if (enchant == null) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long cost = enchant.getCost();
        if (playerData.getBeacons() < cost) {
            long missing = cost - playerData.getBeacons();
            player.sendMessage("§c💸 Pas assez de beacons! (" + NumberFormatter.format(missing) + " manquants)");
            return;
        }

        playerData.removeBeacon(cost);
        ItemStack book = plugin.getUniqueEnchantmentBookFactory().createUniqueEnchantmentBook(enchantId);
        if (book != null) {
            if (player.getInventory().firstEmpty() != -1) player.getInventory().addItem(book);
            else player.getWorld().dropItemNaturally(player.getLocation(), book);
            player.sendMessage("§a✅ Livre §e" + enchant.getName() + " §aacheté pour §e" + NumberFormatter.format(cost) + " beacons§a!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
            openWeaponArmorShop(player);
        }
    }

    // ==================== UTILITAIRES ====================
    private void fillWithGlass(Inventory gui) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        plugin.getGUIManager().applyName(glassMeta, " ");
        glass.setItemMeta(glassMeta);
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) gui.setItem(i, glass);
        }
    }

    private ItemStack createPickaxeShopItem(Player player, EnchantmentBookManager.EnchantmentBook book) {
        ItemStack item = new ItemStack(book.getDisplayMaterial());
        ItemMeta meta = item.getItemMeta();

        int currentLevel = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, book.getId());
        long cost = book.getCost();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        boolean canAfford = playerData.getBeacons() >= cost;

        String affordIcon = canAfford ? "§a💰" : "§c💸";
        plugin.getGUIManager().applyName(meta, affordIcon + " §5⚡ §l" + book.getName());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_book_id"), PersistentDataType.STRING, book.getId());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§e✨ §lEnchantement Unique Légendaire");
        lore.add("");
        lore.add("§6📖 §lDescription:");
        lore.add("§7▸ " + book.getDescription());
        lore.add("");
        lore.add("§e📊 §lInformations:");
        lore.add("§7▸ Niveau max: §e" + book.getMaxLevel());
        lore.add("§7▸ Votre niveau: §a" + currentLevel + "§7/§e" + book.getMaxLevel());
        lore.add("");
        lore.add("§6💰 §lPrix:");
        if (canAfford) {
            lore.add("§7▸ §a" + NumberFormatter.format(cost) + " beacons");
            lore.add("§7▸ §aVous pouvez acheter!");
        } else {
            lore.add("§7▸ §c" + NumberFormatter.format(cost) + " beacons");
            lore.add("§7▸ §c" + NumberFormatter.format(cost - playerData.getBeacons()) + " beacons manquants");
        }
        lore.add("");
        lore.add(canAfford ? "§e➤ Cliquez pour acheter le livre!" : "§c❌ Pas assez de beacons!");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
