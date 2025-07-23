package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.managers.AutominerManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class AutominerEnchantGUI {

    private final PrisonTycoon plugin;
    private final AutominerManager autominerManager;
    private final NamespacedKey actionKey;
    private final NamespacedKey enchantKey;
    private final NamespacedKey autominerUuidKey;

    public AutominerEnchantGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.autominerManager = plugin.getAutominerManager();
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.enchantKey = new NamespacedKey(plugin, "gui_enchant");
        this.autominerUuidKey = new NamespacedKey(plugin, "autominer_uuid");
    }

    public void openEnchantMenu(Player player, String autominerUuid) {
        AutominerData autominerData = findAutominerData(player, autominerUuid);
        if (autominerData == null) {
            player.sendMessage("§cAutomineur introuvable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "§6⚡ Améliorer Automineur");
        inv.setItem(0, createAutominerUuidItem(autominerUuid));


        // Fill borders
        fillBorders(inv);

        // Add autominer item
        inv.setItem(4, autominerData.toItemStack(autominerManager.getUuidKey(), autominerManager.getTypeKey(), autominerManager.getEnchantKey(), autominerManager.getCristalKey()));

        // Add enchantment items
        addEnchantmentItem(inv, 20, "Efficacité", "efficiency", autominerData);
        addEnchantmentItem(inv, 22, "Fortune", "fortune", autominerData);
        addEnchantmentItem(inv, 24, "TokenGreed", "tokengreed", autominerData);
        addEnchantmentItem(inv, 38, "ExpGreed", "expgreed", autominerData);
        addEnchantmentItem(inv, 40, "MoneyGreed", "moneygreed", autominerData);
        addEnchantmentItem(inv, 42, "KeyGreed", "keygreed", autominerData);
        addEnchantmentItem(inv, 31, "FuelEfficiency", "fuelefficiency", autominerData);
        if (autominerData.getType() == fr.prisontycoon.autominers.AutominerType.BEACON) {
            addEnchantmentItem(inv, 49, "BeaconFinder", "beaconfinder", autominerData);
        }

        // Add crystal slots
        inv.setItem(12, createCrystalSlot(autominerData, 0));
        inv.setItem(14, createCrystalSlot(autominerData, 1));


        player.openInventory(inv);
    }

    private ItemStack createAutominerUuidItem(String autominerUuid) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Autominer UUID");
        meta.getPersistentDataContainer().set(autominerUuidKey, PersistentDataType.STRING, autominerUuid);
        item.setItemMeta(meta);
        return item;
    }

    private void addEnchantmentItem(Inventory inv, int slot, String displayName, String enchantName, AutominerData autominerData) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b" + displayName);
        List<String> lore = new ArrayList<>();
        int level = autominerData.getEnchantmentLevel(enchantName);
        int maxLevel = autominerData.getType().getMaxEnchantmentLevel(enchantName);
        lore.add("§7Niveau: §e" + level + "/" + maxLevel);
        lore.add("§7Coût: §6" + plugin.getAutominerEnchantmentManager().calculateEnchantmentCost(plugin.getAutominerEnchantmentManager().getEnchantment(enchantName), level, autominerData.getType()) + " tokens");
        lore.add("");
        lore.add("§aClic gauche pour améliorer");
        lore.add("§eClic droit pour améliorer x10");
        lore.add("§cShift-clic pour améliorer au max");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "enchant");
        meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchantName);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private ItemStack createCrystalSlot(AutominerData autominerData, int crystalSlot) {
        if (crystalSlot < autominerData.getAppliedCristals().size()) {
            Cristal crystal = autominerData.getAppliedCristals().get(crystalSlot);
            ItemStack item = crystal.toItemStack(
                    new NamespacedKey(plugin, "cristal_uuid"),
                    new NamespacedKey(plugin, "cristal_level"),
                    new NamespacedKey(plugin, "cristal_type"),
                    new NamespacedKey(plugin, "cristal_vierge")
            );
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore();
            lore.add("§cShift-clic pour retirer");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "remove_crystal");
            item.setItemMeta(meta);
            return item;
        } else {
            // Display empty slot
            ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName("§7Emplacement de cristal");
            List<String> lore = new ArrayList<>();
            lore.add("§7Glissez un cristal ici pour l'appliquer.");
            meta.setLore(lore);
            item.setItemMeta(meta);
            return item;
        }
    }


    private void fillBorders(Inventory inv) {
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();
        meta.setDisplayName(" ");
        borderItem.setItemMeta(meta);

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                if (i < 9 || i > 44 || i % 9 == 0 || i % 9 == 8) {
                    inv.setItem(i, borderItem);
                }
            }
        }
    }

    private AutominerData findAutominerData(Player player, String autominerUuid) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && autominerManager.isAutominer(item)) {
                AutominerData data = AutominerData.fromItemStack(item, autominerManager.getUuidKey(), autominerManager.getTypeKey(), autominerManager.getEnchantKey(), autominerManager.getCristalKey());
                if (data != null && data.getUuid().equals(autominerUuid)) {
                    return data;
                }
            }
        }
        return null;
    }

    public void handleMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType, Inventory inv) {
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String autominerUuid = inv.getItem(0).getItemMeta().getPersistentDataContainer().get(autominerUuidKey, PersistentDataType.STRING);

        if (action != null) {
            if (action.equals("enchant")) {
                String enchantName = meta.getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);

                int levelsToUpgrade = 0;
                if (clickType.isLeftClick()) {
                    levelsToUpgrade = 1;
                } else if (clickType.isRightClick()) {
                    levelsToUpgrade = 10;
                } else if (clickType.isShiftClick()) {
                    levelsToUpgrade = 1000; // A large number to max it out
                }

                if (levelsToUpgrade > 0) {
                    AutominerData autominerData = findAutominerData(player, autominerUuid);
                    if (autominerData != null) {
                        plugin.getAutominerEnchantmentManager().upgradeEnchantment(player, autominerData, enchantName, levelsToUpgrade);
                        // Update the item in the player's inventory
                        for (int i = 0; i < player.getInventory().getSize(); i++) {
                            ItemStack item = player.getInventory().getItem(i);
                            if (item != null && autominerManager.isAutominer(item)) {
                                String itemUuid = item.getItemMeta().getPersistentDataContainer().get(autominerManager.getUuidKey(), PersistentDataType.STRING);
                                if (autominerUuid.equals(itemUuid)) {
                                    player.getInventory().setItem(i, autominerData.toItemStack(autominerManager.getUuidKey(), autominerManager.getTypeKey(), autominerManager.getEnchantKey(), autominerManager.getCristalKey()));
                                    break;
                                }
                            }
                        }
                        openEnchantMenu(player, autominerUuid); // Refresh
                    }
                }
            } else if (action.equals("remove_crystal")) {
                if (clickType.isShiftClick()) {
                    AutominerData autominerData = findAutominerData(player, autominerUuid);
                    if (autominerData != null) {
                        String crystalUuid = clickedItem.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "cristal_uuid"), PersistentDataType.STRING);
                        Cristal removedCrystal = autominerData.removeCristal(crystalUuid);
                        if (removedCrystal != null) {
                            player.getInventory().addItem(removedCrystal.toItemStack(new NamespacedKey(plugin, "cristal_uuid"), new NamespacedKey(plugin, "cristal_level"), new NamespacedKey(plugin, "cristal_type"), new NamespacedKey(plugin, "cristal_vierge")));
                            // Update the item in the player's inventory
                            for (int i = 0; i < player.getInventory().getSize(); i++) {
                                ItemStack item = player.getInventory().getItem(i);
                                if (item != null && autominerManager.isAutominer(item)) {
                                    String itemUuid = item.getItemMeta().getPersistentDataContainer().get(autominerManager.getUuidKey(), PersistentDataType.STRING);
                                    if (autominerUuid.equals(itemUuid)) {
                                        player.getInventory().setItem(i, autominerData.toItemStack(autominerManager.getUuidKey(), autominerManager.getTypeKey(), autominerManager.getEnchantKey(), autominerManager.getCristalKey()));
                                        break;
                                    }
                                }
                            }
                            openEnchantMenu(player, autominerUuid); // Refresh
                        }
                    }
                }
            }
        } else if (inv.getItem(12) == null || inv.getItem(14) == null) {
            // Handle crystal application
            ItemStack cursorItem = player.getItemOnCursor();
            if (cursorItem != null && plugin.getCristalManager().isCristal(cursorItem)) {
                AutominerData autominerData = findAutominerData(player, autominerUuid);
                if (autominerData != null) {
                    if (autominerData.getAppliedCristals().size() < 2) {
                        Cristal crystal = plugin.getCristalManager().getCristalFromItem(cursorItem);
                        if (autominerData.applyCristal(crystal)) {
                            player.setItemOnCursor(null);
                            // Update the item in the player's inventory
                            for (int i = 0; i < player.getInventory().getSize(); i++) {
                                ItemStack item = player.getInventory().getItem(i);
                                if (item != null && autominerManager.isAutominer(item)) {
                                    String itemUuid = item.getItemMeta().getPersistentDataContainer().get(autominerManager.getUuidKey(), PersistentDataType.STRING);
                                    if (autominerUuid.equals(itemUuid)) {
                                        player.getInventory().setItem(i, autominerData.toItemStack(autominerManager.getUuidKey(), autominerManager.getTypeKey(), autominerManager.getEnchantKey(), autominerManager.getCristalKey()));
                                        break;
                                    }
                                }
                            }
                            openEnchantMenu(player, autominerUuid); // Refresh
                        }
                    }
                }
            }
        }
    }
}