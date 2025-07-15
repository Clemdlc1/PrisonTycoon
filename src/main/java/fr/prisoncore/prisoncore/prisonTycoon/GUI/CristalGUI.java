package fr.prisoncore.prisoncore.prisonTycoon.GUI;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.cristaux.Cristal;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
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
import java.util.Arrays;
import java.util.List;

/**
 * Interface graphique pour la gestion des cristaux sur les pioches
 */
public class CristalGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey cristalSlotKey;
    private final NamespacedKey actionKey;
    private final NamespacedKey cristalUuidKey;

    public CristalGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.cristalSlotKey = new NamespacedKey(plugin, "cristal_slot");
        this.actionKey = new NamespacedKey(plugin, "cristal_action");
        this.cristalUuidKey = new NamespacedKey(plugin, "cristal_uuid_target");
    }

    /**
     * Ouvre le menu principal de gestion des cristaux
     */
    public void openCristalMenu(Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§cVous devez avoir une pioche légendaire pour utiliser les cristaux!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, "§d✨ Gestion des Cristaux ✨");

        // Informations de la pioche (slot 4)
        fillPickaxeInfo(inv, player, pickaxe);

        // Cristaux appliqués (slots 19-22)
        fillAppliedCristals(inv, player, pickaxe);

        // Cristaux dans l'inventaire (slots 28-43)
        fillInventoryCristals(inv, player);

        // Boutons de contrôle
        fillControlButtons(inv, player, pickaxe);

        // Séparateurs
        fillSeparators(inv);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    /**
     * Affiche les informations de la pioche
     */
    private void fillPickaxeInfo(Inventory inv, Player player, ItemStack pickaxe) {
        ItemStack info = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§6✨ Votre Pioche Légendaire ✨");

        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(pickaxe);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long nextCost = plugin.getCristalManager().getApplicationCost(pickaxe);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Cristaux appliqués: §d" + cristals.size() + "§8/§d4");
        lore.add("§7Votre XP: §e" + NumberFormatter.format(playerData.getExperience()));

        if (nextCost > 0) {
            lore.add("§7Coût prochain cristal: §e" + NumberFormatter.format(nextCost) + " XP");
        } else if (nextCost == -1) {
            lore.add("§c✗ Maximum de cristaux atteint");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (!cristals.isEmpty()) {
            lore.add("§d✨ Bonus actifs:");
            for (Cristal cristal : cristals) {
                lore.add("§8• §d" + cristal.getType().getDisplayName() + " " + cristal.getNiveau() +
                        "§8: §a" + cristal.getType().getBonusDescription(cristal.getNiveau()));
            }
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        meta.setLore(lore);
        info.setItemMeta(meta);
        inv.setItem(4, info);
    }

    /**
     * Affiche les cristaux actuellement appliqués
     */
    private void fillAppliedCristals(Inventory inv, Player player, ItemStack pickaxe) {
        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(pickaxe);

        // Slots pour les cristaux appliqués
        int[] slots = {19, 20, 21, 22};

        for (int i = 0; i < 4; i++) {
            if (i < cristals.size()) {
                // Cristal appliqué
                Cristal cristal = cristals.get(i);
                ItemStack cristalItem = cristal.toItemStack(
                        plugin.getCristalManager().getCristalUuidKey(),
                        plugin.getCristalManager().getCristalLevelKey(),
                        plugin.getCristalManager().getCristalTypeKey(),
                        plugin.getCristalManager().getCristalViergeKey()
                );

                ItemMeta meta = cristalItem.getItemMeta();
                List<String> lore = meta.getLore();
                lore.add("");
                lore.add("§c▸ Clic-droit pour retirer (50% destruction)");

                // Marque pour l'action de retrait
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "remove");
                meta.getPersistentDataContainer().set(cristalUuidKey, PersistentDataType.STRING, cristal.getUuid());
                meta.setLore(lore);
                cristalItem.setItemMeta(meta);

                inv.setItem(slots[i], cristalItem);
            } else {
                // Slot vide
                ItemStack empty = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta meta = empty.getItemMeta();
                meta.setDisplayName("§8⬜ Slot de Cristal Vide");
                meta.setLore(Arrays.asList(
                        "§7Emplacement libre pour un cristal",
                        "",
                        "§e▸ Cliquez sur un cristal dans votre",
                        "§e  inventaire pour l'appliquer ici"
                ));
                meta.getPersistentDataContainer().set(cristalSlotKey, PersistentDataType.INTEGER, i);
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "slot");
                empty.setItemMeta(meta);
                inv.setItem(slots[i], empty);
            }
        }
    }

    /**
     * Affiche les cristaux disponibles dans l'inventaire du joueur
     */
    private void fillInventoryCristals(Inventory inv, Player player) {
        List<ItemStack> cristalsInInventory = new ArrayList<>();

        // Recherche des cristaux dans l'inventaire
        for (ItemStack item : player.getInventory().getContents()) {
            if (plugin.getCristalManager().isCristal(item)) {
                Cristal cristal = plugin.getCristalManager().getCristalFromItem(item);
                if (cristal != null && !cristal.isVierge()) {
                    // Clone l'item pour l'affichage
                    ItemStack displayItem = item.clone();
                    ItemMeta meta = displayItem.getItemMeta();
                    List<String> lore = meta.getLore();
                    lore.add("");
                    lore.add("§e▸ Clic-gauche pour appliquer sur la pioche");

                    // Marque pour l'action d'application
                    meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "apply");
                    meta.setLore(lore);
                    displayItem.setItemMeta(meta);

                    cristalsInInventory.add(displayItem);
                }
            }
        }

        // Affichage dans les slots 28-43 (16 slots)
        int startSlot = 28;
        for (int i = 0; i < 16 && i < cristalsInInventory.size(); i++) {
            inv.setItem(startSlot + i, cristalsInInventory.get(i));
        }

        // Message si aucun cristal
        if (cristalsInInventory.isEmpty()) {
            ItemStack notice = new ItemStack(Material.BARRIER);
            ItemMeta meta = notice.getItemMeta();
            meta.setDisplayName("§c⚠ Aucun cristal révélé");
            meta.setLore(Arrays.asList(
                    "§7Vous n'avez aucun cristal révélé",
                    "§7dans votre inventaire.",
                    "",
                    "§e▸ Utilisez §6/cristal <niveau> §epour",
                    "§e  obtenir des cristaux vierges",
                    "§e▸ Clic-droit sur un cristal vierge",
                    "§e  pour révéler son type"
            ));
            notice.setItemMeta(meta);
            inv.setItem(31, notice);
        }
    }

    /**
     * Boutons de contrôle
     */
    private void fillControlButtons(Inventory inv, Player player, ItemStack pickaxe) {
        // Bouton fermer
        ItemStack close = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta meta = close.getItemMeta();
        meta.setDisplayName("§c❌ Fermer");
        meta.setLore(Arrays.asList("§7Ferme ce menu"));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        close.setItemMeta(meta);
        inv.setItem(49, close);

        // Bouton retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§e↩ Retour");
        backMeta.setLore(Arrays.asList("§7Retour au menu des enchantements"));
        backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back");
        back.setItemMeta(backMeta);
        inv.setItem(45, back);
    }

    /**
     * Séparateurs visuels
     */
    private void fillSeparators(Inventory inv) {
        ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = separator.getItemMeta();
        meta.setDisplayName(" ");
        separator.setItemMeta(meta);

        // Lignes de séparation
        int[] separatorSlots = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 23, 24, 25, 26, 27, 44, 46, 47, 48, 50, 51, 52, 53};
        for (int slot : separatorSlots) {
            inv.setItem(slot, separator);
        }
    }

    /**
     * Gère les clics dans le menu des cristaux
     */
    public void handleCristalMenuClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "apply":
                handleApplyCristal(player, clickedItem);
                break;
            case "remove":
                handleRemoveCristal(player, clickedItem);
                break;
            case "close":
                player.closeInventory();
                break;
            case "back":
                player.closeInventory();
                plugin.getMainMenuGUI().openEnchantmentMenu(player);
                break;
            case "slot":
                // Les slots vides ne font rien
                break;
        }
    }

    /**
     * Applique un cristal sur la pioche
     */
    private void handleApplyCristal(Player player, ItemStack clickedItem) {
        Cristal cristal = plugin.getCristalManager().getCristalFromItem(clickedItem);
        if (cristal == null) {
            player.sendMessage("§cErreur: Cristal invalide!");
            return;
        }

        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§cPioche légendaire introuvable!");
            player.closeInventory();
            return;
        }

        // Recherche et suppression du cristal de l'inventaire
        boolean found = false;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (plugin.getCristalManager().isCristal(item)) {
                Cristal invCristal = plugin.getCristalManager().getCristalFromItem(item);
                if (invCristal != null && invCristal.getUuid().equals(cristal.getUuid())) {
                    // Application du cristal
                    if (plugin.getCristalManager().applyCristalToPickaxe(player, pickaxe, cristal)) {
                        // Suppression de l'inventaire
                        player.getInventory().setItem(i, null);
                        found = true;

                        // Rafraîchissement du menu
                        openCristalMenu(player);
                    }
                    break;
                }
            }
        }

        if (!found) {
            player.sendMessage("§cCristal introuvable dans votre inventaire!");
        }
    }

    /**
     * Retire un cristal de la pioche
     */
    private void handleRemoveCristal(Player player, ItemStack clickedItem) {
        ItemMeta meta = clickedItem.getItemMeta();
        String cristalUuid = meta.getPersistentDataContainer().get(cristalUuidKey, PersistentDataType.STRING);

        if (cristalUuid == null) {
            player.sendMessage("§cErreur: UUID du cristal introuvable!");
            return;
        }

        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§cPioche légendaire introuvable!");
            player.closeInventory();
            return;
        }

        // Confirmation
        player.closeInventory();
        player.sendMessage("§6⚠ §eÊtes-vous sûr de vouloir retirer ce cristal?");
        player.sendMessage("§7Il y a §c50% de chance §7qu'il soit détruit!");
        player.sendMessage("§e▸ Tapez §a'confirmer' §epour continuer, ou ignorez pour annuler.");

        // TODO: Implémenter un système de confirmation ou utiliser un menu de confirmation
        // Pour l'instant, retrait direct
        ItemStack recoveredCristal = plugin.getCristalManager().removeCristalFromPickaxe(player, pickaxe, cristalUuid);

        if (recoveredCristal != null && player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(recoveredCristal);
        }

        // Réouverture du menu après un délai
        Bukkit.getScheduler().runTaskLater(plugin, () -> openCristalMenu(player), 20L);
    }
}