package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerData;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface graphique pour la condensation des automineurs
 * Permet de combiner 9 automineurs du même type en 1 automineur de type supérieur
 */
public class AutominerCondenseGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    // Layout du menu (54 slots - 6 lignes)
    private static final int INFO_SLOT = 4;
    private static final int RESULT_SLOT = 23;
    private static final int CONDENSE_BUTTON_SLOT = 40;
    private static final int BACK_BUTTON_SLOT = 45;
    private static final int CLEAR_BUTTON_SLOT = 53;

    // Grille de condensation (3x3 au centre)
    private static final int[] CONDENSE_SLOTS = {
            10, 11, 12,
            19, 20, 21,
            28, 29, 30
    };

    public AutominerCondenseGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.valueKey = new NamespacedKey(plugin, "gui_value");
    }

    /**
     * Ouvre le menu de condensation
     */
    public void openCondenseMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§b🔧 Condensation d'Automineurs");

        // Bordures décoratives
        fillBorders(inv);

        // Informations générales (slot 4)
        inv.setItem(INFO_SLOT, createInfoItem());

        // Slot de résultat (slot 23)
        inv.setItem(RESULT_SLOT, createResultSlot());

        // Bouton de condensation (slot 40)
        inv.setItem(CONDENSE_BUTTON_SLOT, createCondenseButton());

        // Bouton de retour (slot 45)
        inv.setItem(BACK_BUTTON_SLOT, createBackButton());

        // Bouton de vidage (slot 53)
        inv.setItem(CLEAR_BUTTON_SLOT, createClearButton());

        // Grille de condensation vide
        fillCondenseGrid(inv);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * Crée l'item d'information générale
     */
    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§b🔧 §lCONDENSATION D'AUTOMINEURS");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Combinez 9 automineurs du §emême type");
        lore.add("§7pour obtenir 1 automineur de §atype supérieur§7.");
        lore.add("");
        lore.add("§e📋 §lRECETTES DISPONIBLES:");
        lore.add("§7▸ 9x §fPierre §7→ 1x §7Fer");
        lore.add("§7▸ 9x §7Fer §7→ 1x §6Or");
        lore.add("§7▸ 9x §6Or §7→ 1x §bDiamant");
        lore.add("§7▸ 9x §bDiamant §7→ 1x §aÉmeraude");
        lore.add("§7▸ 9x §aÉmeraude §7→ 1x §eBeacon");
        lore.add("");
        lore.add("§c⚠ §lIMPORTANT:");
        lore.add("§7▸ Les automineurs doivent être §eexactement §7du même type");
        lore.add("§7▸ Les enchantements et cristaux sont §cperdus");
        lore.add("§7▸ Le nouvel automineur est §evierge§7 (sans améliorations)");
        lore.add("");
        lore.add("§e💡 §lCOMMENT UTILISER:");
        lore.add("§71. Placez 9 automineurs identiques dans la grille");
        lore.add("§72. Vérifiez le résultat à droite");
        lore.add("§73. Cliquez sur §a'Condenser'§7 pour valider");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le slot de résultat
     */
    private ItemStack createResultSlot() {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§e⚡ §lRÉSULTAT DE LA CONDENSATION");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Le résultat de la condensation");
        lore.add("§7apparaîtra ici une fois que vous");
        lore.add("§7aurez placé 9 automineurs identiques.");
        lore.add("");
        lore.add("§7En attente d'automineurs...");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de condensation
     */
    private ItemStack createCondenseButton() {
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a🔨 §lCONDENSER");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Effectue la condensation des automineurs");
        lore.add("§7placés dans la grille.");
        lore.add("");
        lore.add("§cConditions requises:");
        lore.add("§7▸ Exactement 9 automineurs");
        lore.add("§7▸ Tous du même type");
        lore.add("§7▸ Type condensable (Beacon non condensable)");
        lore.add("");
        lore.add("§a🖱 §lCLIC: §aEffectuer la condensation");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "condense", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de retour
     */
    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c← §lRETOUR");

        List<String> lore = new ArrayList<>();
        lore.add("§7Retourner au menu principal des automineurs");

        meta.setLore(lore);
        setItemAction(meta, "back_to_main", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton de vidage
     */
    private ItemStack createClearButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c🗑 §lVIDER LA GRILLE");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Retire tous les automineurs de la grille");
        lore.add("§7et les remet dans votre inventaire.");
        lore.add("");
        lore.add("§c🖱 §lCLIC: §cVider la grille");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "clear_grid", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Remplit la grille de condensation avec des emplacements vides
     */
    private void fillCondenseGrid(Inventory inv) {
        ItemStack slotItem = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = slotItem.getItemMeta();

        meta.setDisplayName("§7⚡ §lEmplacement Automineur");

        List<String> lore = new ArrayList<>();
        lore.add("§7Placez un automineur ici");

        meta.setLore(lore);
        slotItem.setItemMeta(meta);

        for (int slot : CONDENSE_SLOTS) {
            inv.setItem(slot, slotItem);
        }
    }

    /**
     * Gère les clics dans le menu de condensation
     */
    public void handleCondenseClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || clickedItem.getItemMeta() == null) {
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

        if (action != null) {
            switch (action) {
                case "condense" -> {
                    performCondensation(player);
                }
                case "back_to_main" -> {
                    // Rendre les items avant de fermer
                    returnItemsToPlayer(player);
                    plugin.getAutominerGUI().openMainMenu(player);
                }
                case "clear_grid" -> {
                    clearCondenseGrid(player);
                }
            }
            return;
        }

        // Gestion du placement/retrait d'items dans la grille
        if (isCondenseSlot(slot)) {
            handleGridSlotClick(player, slot, clickedItem, clickType);
        }
    }

    /**
     * Gère les clics sur les slots de la grille de condensation
     */
    private void handleGridSlotClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        ItemStack playerItem = player.getInventory().getItemInMainHand();

        // Si le slot contient un item de remplissage, permettre le placement
        if (isPlaceholderItem(clickedItem)) {
            if (playerItem != null && playerItem.getType() != Material.AIR) {
                // Vérifier si c'est un automineur valide
                AutominerData autominerData = getAutominerFromItem(playerItem);
                if (autominerData != null) {
                    // Placer l'automineur dans le slot
                    inv.setItem(slot, playerItem.clone());
                    playerItem.setAmount(playerItem.getAmount() - 1);

                    // Mettre à jour l'affichage du résultat
                    updateResultDisplay(inv);

                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
                } else {
                    player.sendMessage("§c❌ Vous devez placer un automineur valide!");
                }
            }
        } else {
            // Retirer l'item du slot
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(clickedItem.clone());
                inv.setItem(slot, createPlaceholderItem());

                // Mettre à jour l'affichage du résultat
                updateResultDisplay(inv);

                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f);
            } else {
                player.sendMessage("§c❌ Inventaire plein!");
            }
        }
    }

    /**
     * Met à jour l'affichage du résultat de condensation
     */
    private void updateResultDisplay(Inventory inv) {
        List<AutominerData> autominers = getAutominersFromGrid(inv);

        if (autominers.size() == 9 && areAllSameType(autominers)) {
            AutominerType currentType = autominers.get(0).getType();
            AutominerType nextType = getNextAutominerType(currentType);

            if (nextType != null) {
                // Créer l'item de résultat
                ItemStack resultItem = new ItemStack(nextType.getDisplayMaterial());
                ItemMeta meta = resultItem.getItemMeta();

                meta.setDisplayName("§a✅ " + nextType.getColoredName() + " §aAutomineur");

                List<String> lore = new ArrayList<>();
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§7Résultat de la condensation:");
                lore.add("§79x " + currentType.getColoredName() + " §7→ 1x " + nextType.getColoredName());
                lore.add("");
                lore.add("§7Type: " + nextType.getDisplayName());
                lore.add("§7Consommation: §c" + getConsumptionRate(nextType));
                lore.add("§7Enchantements max:");
                addMaxEnchantments(lore, nextType);
                lore.add("");
                lore.add("§a✅ Condensation possible!");
                lore.add("§7Cliquez sur §a'Condenser'§7 pour valider.");
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                meta.setLore(lore);
                resultItem.setItemMeta(meta);

                inv.setItem(RESULT_SLOT, resultItem);
            } else {
                // Type non condensable
                inv.setItem(RESULT_SLOT, createErrorResultItem("Type non condensable!"));
            }
        } else if (autominers.size() > 0) {
            // Pas le bon nombre ou pas le même type
            String error = autominers.size() != 9 ?
                    "Il faut exactement 9 automineurs!" :
                    "Tous les automineurs doivent être du même type!";
            inv.setItem(RESULT_SLOT, createErrorResultItem(error));
        } else {
            // Grille vide
            inv.setItem(RESULT_SLOT, createResultSlot());
        }
    }

    /**
     * Crée un item de résultat d'erreur
     */
    private ItemStack createErrorResultItem(String error) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c❌ §lERREUR");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§c" + error);
        lore.add("");
        lore.add("§7Vérifiez votre configuration et réessayez.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Effectue la condensation
     */
    private void performCondensation(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();
        List<AutominerData> autominers = getAutominersFromGrid(inv);

        // Vérifications
        if (autominers.size() != 9) {
            player.sendMessage("§c❌ Il faut exactement 9 automineurs pour la condensation!");
            return;
        }

        if (!areAllSameType(autominers)) {
            player.sendMessage("§c❌ Tous les automineurs doivent être du même type!");
            return;
        }

        AutominerType currentType = autominers.get(0).getType();
        AutominerType nextType = getNextAutominerType(currentType);

        if (nextType == null) {
            player.sendMessage("§c❌ Ce type d'automineur ne peut pas être condensé davantage!");
            return;
        }

        // Vérifier l'espace dans l'inventaire
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c❌ Inventaire plein! Libérez de l'espace d'abord.");
            return;
        }

        // Effectuer la condensation
        clearGridItems(inv);

        // Créer le nouvel automineur
        AutominerData newAutominer = new AutominerData(java.util.UUID.randomUUID().toString(), nextType);
        ItemStack newAutominerItem = newAutominer.toItemStack(
                plugin.getAutominerManager().getUuidKey(),
                plugin.getAutominerManager().getTypeKey(),
                plugin.getAutominerManager().getEnchantKey(),
                plugin.getAutominerManager().getCristalKey()
        );

        // Donner au joueur
        player.getInventory().addItem(newAutominerItem);

        player.sendMessage("§a✅ Condensation réussie! Vous avez obtenu un automineur " + nextType.getColoredName() + "§a!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);

        // Mettre à jour l'affichage
        updateResultDisplay(inv);
    }

    /**
     * Vide la grille de condensation
     */
    private void clearCondenseGrid(Player player) {
        returnItemsToPlayer(player);

        Inventory inv = player.getOpenInventory().getTopInventory();
        fillCondenseGrid(inv);
        updateResultDisplay(inv);

        player.sendMessage("§a✅ Grille vidée! Vos automineurs ont été rendus.");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Rend tous les items de la grille au joueur
     */
    private void returnItemsToPlayer(Player player) {
        Inventory inv = player.getOpenInventory().getTopInventory();

        for (int slot : CONDENSE_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !isPlaceholderItem(item)) {
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
    }

    // Méthodes utilitaires

    private void fillBorders(Inventory inv) {
        ItemStack borderItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = borderItem.getItemMeta();
        meta.setDisplayName(" ");
        borderItem.setItemMeta(meta);

        // Bordures haut et bas
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, borderItem);
            inv.setItem(i + 45, borderItem);
        }

        // Bordures gauche et droite
        for (int i = 1; i < 5; i++) {
            inv.setItem(i * 9, borderItem);
            inv.setItem(i * 9 + 8, borderItem);
        }

        // Remplir les espaces vides autour de la grille
        int[] fillerSlots = {13, 14, 15, 16, 17, 22, 24, 31, 32, 33, 34, 35, 36, 37, 38, 39, 41, 42, 43, 44};
        for (int slot : fillerSlots) {
            inv.setItem(slot, borderItem);
        }
    }

    private void setItemAction(ItemMeta meta, String action, String value) {
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
    }

    private boolean isCondenseSlot(int slot) {
        for (int condenseSlot : CONDENSE_SLOTS) {
            if (slot == condenseSlot) return true;
        }
        return false;
    }

    private boolean isPlaceholderItem(ItemStack item) {
        return item != null && item.getType() == Material.LIGHT_GRAY_STAINED_GLASS_PANE;
    }

    private ItemStack createPlaceholderItem() {
        ItemStack item = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§7⚡ §lEmplacement Automineur");
        List<String> lore = new ArrayList<>();
        lore.add("§7Placez un automineur ici");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private AutominerData getAutominerFromItem(ItemStack item) {
        return AutominerData.fromItemStack(item,
                plugin.getAutominerManager().getUuidKey(),
                plugin.getAutominerManager().getTypeKey(),
                plugin.getAutominerManager().getEnchantKey(),
                plugin.getAutominerManager().getCristalKey());
    }

    private List<AutominerData> getAutominersFromGrid(Inventory inv) {
        List<AutominerData> autominers = new ArrayList<>();

        for (int slot : CONDENSE_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !isPlaceholderItem(item)) {
                AutominerData data = getAutominerFromItem(item);
                if (data != null) {
                    autominers.add(data);
                }
            }
        }

        return autominers;
    }

    private boolean areAllSameType(List<AutominerData> autominers) {
        if (autominers.isEmpty()) return false;

        AutominerType firstType = autominers.get(0).getType();
        return autominers.stream().allMatch(a -> a.getType() == firstType);
    }

    private AutominerType getNextAutominerType(AutominerType currentType) {
        return switch (currentType) {
            case PIERRE -> AutominerType.FER;
            case FER -> AutominerType.OR;
            case OR -> AutominerType.DIAMANT;
            case DIAMANT -> AutominerType.EMERAUDE;
            case EMERAUDE -> AutominerType.BEACON;
            case BEACON -> null; // Pas de type supérieur
        };
    }

    private String getConsumptionRate(AutominerType type) {
        return switch (type) {
            case PIERRE -> "1 tête/60min";
            case FER -> "1 tête/30min";
            case OR -> "1 tête/15min";
            case DIAMANT -> "1 tête/5min";
            case EMERAUDE -> "1 tête/3min";
            case BEACON -> "1 tête/1min";
        };
    }

    private void addMaxEnchantments(List<String> lore, AutominerType type) {
        switch (type) {
            case PIERRE -> {
                lore.add("§7▸ Efficacité: 10, Fortune: 5");
            }
            case FER -> {
                lore.add("§7▸ Efficacité: 25, Fortune: 10");
                lore.add("§7▸ Token Greed: 10");
            }
            case OR -> {
                lore.add("§7▸ Efficacité: 100, Fortune: 50");
                lore.add("§7▸ Token/Exp/Money Greed: 25");
            }
            case DIAMANT -> {
                lore.add("§7▸ Efficacité: 500, Fortune: 250");
                lore.add("§7▸ Token/Exp/Money Greed: 100");
                lore.add("§7▸ Key Greed: 2, Fuel Efficiency: 10");
            }
            case EMERAUDE -> {
                lore.add("§7▸ Efficacité: 5000, Fortune: 2000");
                lore.add("§7▸ Token/Exp/Money Greed: 500");
                lore.add("§7▸ Key Greed: 1, Fuel Efficiency: 50");
            }
            case BEACON -> {
                lore.add("§7▸ Efficacité: ∞, Fortune: ∞");
                lore.add("§7▸ Token/Exp/Money Greed: 10000");
                lore.add("§7▸ Key Greed: 3, Beacon Finder: 1");
                lore.add("§7▸ Fuel Efficiency: 100");
            }
        }
    }

    private void clearGridItems(Inventory inv) {
        for (int slot : CONDENSE_SLOTS) {
            inv.setItem(slot, createPlaceholderItem());
        }
    }
}