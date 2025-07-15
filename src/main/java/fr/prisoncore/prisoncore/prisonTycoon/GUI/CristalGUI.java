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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interface graphique améliorée pour la gestion des cristaux (27 slots)
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
     * Ouvre le menu principal de gestion des cristaux (27 slots)
     */
    public void openCristalMenu(Player player) {
        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§cVous devez avoir une pioche légendaire pour utiliser les cristaux!");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "§d✨ Gestion des Cristaux ✨");

        // Informations de la pioche (slot 4)
        fillPickaxeInfo(inv, player);

        // Cristaux appliqués (slots 10-13)
        fillAppliedCristals(inv, player);

        // Boutons de contrôle
        fillControlButtons(inv);

        // Séparateurs
        fillSeparators(inv);

        // Livre d'explication (slot 8)
        fillExplanationBook(inv);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
    }

    /**
     * Ouvre le menu de fusion des cristaux
     */
    public void openFusionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "§6⚡ Fusion de Cristaux ⚡");
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    /**
     * Affiche les informations de la pioche
     */
    private void fillPickaxeInfo(Inventory inv, Player player) {
        ItemStack info = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§6✨ Votre Pioche Légendaire ✨");

        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long nextCost = plugin.getCristalManager().getApplicationCost(player);

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Cristaux appliqués: §d" + cristals.size() + "§8/§d4");
        lore.add("§7Votre XP: §e" + NumberFormatter.format(playerData.getExperience()));

        if (nextCost > 0) {
            lore.add("§7Coût prochain cristal: §e" + NumberFormatter.format(nextCost) + " XP");
        } else if (nextCost == -1) {
            lore.add("§c✗ Maximum de cristaux atteint");
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (!cristals.isEmpty()) {
            lore.add("§d✨ Bonus actifs:");
            for (Cristal cristal : cristals) {
                lore.add("§8• §d" + cristal.getType().getDisplayName() + " " + cristal.getNiveau() +
                        "§8: §a" + cristal.getType().getBonusDescription(cristal.getNiveau()));
            }
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        meta.setLore(lore);
        info.setItemMeta(meta);
        inv.setItem(4, info);
    }

    /**
     * Affiche les cristaux actuellement appliqués
     */
    private void fillAppliedCristals(Inventory inv, Player player) {
        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);

        // Slots pour les cristaux appliqués (10-13)
        int[] slots = {14, 11, 12, 15};

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
                lore.add("§c▸ Clic-gauche pour retirer (50% destruction)");

                // Marque pour l'action de retrait
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "remove");
                meta.getPersistentDataContainer().set(cristalUuidKey, PersistentDataType.STRING, cristal.getUuid());
                meta.setLore(lore);
                cristalItem.setItemMeta(meta);

                inv.setItem(slots[i], cristalItem);
            } else {
                // Slot vide
                ItemStack emptySlot = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
                ItemMeta meta = emptySlot.getItemMeta();
                meta.setDisplayName("§7⬜ Emplacement libre");
                meta.setLore(Arrays.asList(
                        "§7Cet emplacement est libre.",
                        "",
                        "§e▸ Cliquez sur un cristal révélé",
                        "§e  dans votre inventaire pour",
                        "§e  l'appliquer ici"
                ));
                meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "slot");
                emptySlot.setItemMeta(meta);
                inv.setItem(slots[i], emptySlot);
            }
        }
    }

    /**
     * Livre d'explication du système de cristaux
     */
    private void fillExplanationBook(Inventory inv) {
        ItemStack book = new ItemStack(Material.BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName("§6📚 Guide des Cristaux");
        meta.setLore(Arrays.asList(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Les cristaux améliorent votre pioche",
                "§7avec des bonus permanents.",
                "",
                "§e🔸 Obtention:",
                "§7• Utilisez §6/cristal <niveau>",
                "§7• Révélez avec clic-droit",
                "",
                "§e🔸 Application:",
                "§7• Maximum 4 cristaux par pioche",
                "§7• Coût en XP croissant",
                "§7• Un seul type par pioche",
                "",
                "§e🔸 Retrait:",
                "§7• Clic-gauche sur un cristal appliqué",
                "§7• 50% de chance de destruction",
                "",
                "§e🔸 Fusion:",
                "§7• 9 cristaux même niveau → 1 niveau +1",
                "§7• Niveau max: 20",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        book.setItemMeta(meta);
        inv.setItem(8, book);
    }

    /**
     * Boutons de contrôle
     */
    private void fillControlButtons(Inventory inv) {
        // Bouton fusion
        ItemStack fusion = new ItemStack(Material.BLAZE_POWDER);
        ItemMeta fusionMeta = fusion.getItemMeta();
        fusionMeta.setDisplayName("§6⚡ Fusion de Cristaux");
        fusionMeta.setLore(Arrays.asList(
                "§7Fusionnez 9 cristaux du même niveau",
                "§7pour obtenir 1 cristal de niveau supérieur",
                "",
                "§e▸ Clic pour ouvrir le menu de fusion"
        ));
        fusionMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "fusion");
        fusion.setItemMeta(fusionMeta);
        inv.setItem(7, fusion);

        // Bouton retour
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§e↩ Retour");
        backMeta.setLore(Arrays.asList("§7Retour au menu des enchantements"));
        backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back");
        back.setItemMeta(backMeta);
        inv.setItem(18, back);

        // Bouton fermer
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c❌ Fermer");
        closeMeta.setLore(Arrays.asList("§7Ferme ce menu"));
        closeMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        close.setItemMeta(closeMeta);
        inv.setItem(26, close);
    }

    /**
     * Informations de fusion
     */
    private void fillFusionInfo(Inventory inv) {
        ItemStack info = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = info.getItemMeta();
        meta.setDisplayName("§6⚡ Table de Fusion ⚡");
        meta.setLore(Arrays.asList(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Placez 9 cristaux du même niveau",
                "§7dans les emplacements ci-dessous.",
                "",
                "§7Lorsque vous fermez le menu avec",
                "§79 cristaux identiques, vous recevrez",
                "§7un cristal vierge de niveau supérieur.",
                "",
                "§c⚠ Niveaux acceptés: 1 à 19",
                "§c⚠ Cristaux vierges et révélés acceptés",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        ));
        info.setItemMeta(meta);
        inv.setItem(4, info);
    }

    /**
     * Slots de fusion
     */
    private void fillFusionSlots(Inventory inv) {
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 17, 18};

        for (int slot : slots) {
            ItemStack fusionSlot = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
            ItemMeta meta = fusionSlot.getItemMeta();
            meta.setDisplayName("§6⬜ Emplacement de fusion");
            meta.setLore(Arrays.asList(
                    "§7Placez un cristal ici",
                    "§7(niveau 1 à 19)"
            ));
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "fusion_slot");
            fusionSlot.setItemMeta(meta);
            inv.setItem(slot, fusionSlot);
        }
    }

    /**
     * Boutons de contrôle fusion
     */
    private void fillFusionControlButtons(Inventory inv) {
        // Bouton retour cristaux
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§e↩ Retour aux cristaux");
        backMeta.setLore(Arrays.asList("§7Retour au menu des cristaux"));
        backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_cristals");
        back.setItemMeta(backMeta);
        inv.setItem(0, back);

        // Bouton fermer
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c❌ Fermer");
        closeMeta.setLore(Arrays.asList("§7Ferme ce menu et rend les objets"));
        closeMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close_fusion");
        close.setItemMeta(closeMeta);
        inv.setItem(8, close);
    }

    /**
     * Séparateurs visuels
     */
    private void fillSeparators(Inventory inv) {
        ItemStack separator = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = separator.getItemMeta();
        meta.setDisplayName(" ");
        separator.setItemMeta(meta);

        // Lignes de séparation pour 27 slots
        int[] separatorSlots = {0, 1, 2, 3, 5, 6, 9, 14, 15, 16, 17, 23, 24, 25};
        for (int slot : separatorSlots) {
            if (inv.getItem(slot) == null) { // Ne pas écraser les items déjà placés
                inv.setItem(slot, separator);
            }
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
            case "fusion":
                openFusionMenu(player);
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
     * Gère les clics dans le menu de fusion.
     * Reçoit l'événement complet pour gérer les items sur le curseur.
     */
    public void handleFusionInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Vérifier que c'est bien le menu de fusion
        if (!event.getView().getTitle().equals("§6⚡ Fusion de Cristaux ⚡")) return;

        // Si le clic est dans l'inventaire du joueur, on autorise
        if (event.getClickedInventory() == player.getInventory()) {
            return; // Laisser le joueur gérer ses items
        }

        // Si le clic est dans le menu de fusion
        if (event.getClickedInventory() != null && event.getClickedInventory().getSize() == 9) {
            event.setCancelled(true);

            int slot = event.getSlot();
            ItemStack currentItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            // Cas 1: Le joueur veut placer un cristal depuis son curseur
            if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                if (plugin.getCristalManager().isCristal(cursorItem)) {
                    Cristal cristal = plugin.getCristalManager().getCristalFromItem(cursorItem);
                    if (cristal != null && cristal.getNiveau() >= 1 && cristal.getNiveau() <= 19) {
                        // Placer le cristal et vider le curseur
                        event.getClickedInventory().setItem(slot, cursorItem.clone());
                        player.setItemOnCursor(null);
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
                    } else {
                        player.sendMessage("§cSeuls les cristaux de niveau 1 à 19 peuvent être fusionnés!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                } else {
                    player.sendMessage("§cSeuls les cristaux peuvent être placés ici!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
            // Cas 2: Le joueur veut récupérer un cristal
            else if (currentItem != null && currentItem.getType() != Material.AIR) {
                if (plugin.getCristalManager().isCristal(currentItem)) {
                    // Mettre le cristal sur le curseur et vider le slot
                    player.setItemOnCursor(currentItem.clone());
                    event.getClickedInventory().setItem(slot, null);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.8f);
                }
            }
        }
    }

    /**
     * Gère les clics dans le menu de fusion
     */
    public void handleFusionMenuClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "back_cristals":
                openCristalMenu(player);
                break;
            case "close_fusion":
                handleCloseFusion(player);
                break;
            case "fusion_slot":
                // Les slots de fusion sont gérés par l'event principal
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
                        player.sendMessage("hello");
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
     * Retire un cristal de la pioche (sans confirmation)
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

        // Retrait direct sans confirmation
        ItemStack recoveredCristal = plugin.getCristalManager().removeCristalFromPickaxe(player, pickaxe, cristalUuid);

        if (recoveredCristal != null && player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(recoveredCristal);
        }

        // Rafraîchissement du menu
        openCristalMenu(player);
    }

    /**
     * Gère la fermeture du menu de fusion
     */
    private void handleCloseFusion(Player player) {
        Inventory fusionInv = player.getOpenInventory().getTopInventory();
        List<ItemStack> itemsToReturn = new ArrayList<>();
        List<Cristal> cristalsForFusion = new ArrayList<>();

        // Récupération des items dans les slots de fusion
        int[] fusionSlots = {10, 11, 12, 13, 14, 15, 16, 17, 18};

        for (int slot : fusionSlots) {
            ItemStack item = fusionInv.getItem(slot);
            if (item != null && plugin.getCristalManager().isCristal(item)) {
                Cristal cristal = plugin.getCristalManager().getCristalFromItem(item);
                if (cristal != null) {
                    cristalsForFusion.add(cristal);
                    itemsToReturn.add(item);
                }
            }
        }

        player.closeInventory();

        // Vérification de la fusion
        if (cristalsForFusion.size() == 9) {
            if (plugin.getCristalManager().fuseCristals(cristalsForFusion)) {
                // Fusion réussie
                int nouveauNiveau = cristalsForFusion.get(0).getNiveau() + 1;
                Cristal fusedCristal = plugin.getCristalManager().createFusedCristal(nouveauNiveau);
                ItemStack fusedItem = fusedCristal.toItemStack(
                        plugin.getCristalManager().getCristalUuidKey(),
                        plugin.getCristalManager().getCristalLevelKey(),
                        plugin.getCristalManager().getCristalTypeKey(),
                        plugin.getCristalManager().getCristalViergeKey()
                );

                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(fusedItem);
                    player.sendMessage("§a✨ Fusion réussie! Vous avez obtenu un cristal niveau " + nouveauNiveau + "!");
                    player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
                } else {
                    player.sendMessage("§cInventaire plein! Fusion annulée.");
                    // Rendre les items
                    for (ItemStack item : itemsToReturn) {
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(item);
                        } else {
                            player.getWorld().dropItem(player.getLocation(), item);
                        }
                    }
                }
            } else {
                player.sendMessage("§cFusion impossible: cristaux de niveaux différents ou niveau max atteint!");
                // Rendre les items
                for (ItemStack item : itemsToReturn) {
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item);
                    } else {
                        player.getWorld().dropItem(player.getLocation(), item);
                    }
                }
            }
        } else if (!itemsToReturn.isEmpty()) {
            // Rendre les items si fusion incomplète
            player.sendMessage("§7Items rendus - fusion incomplète.");
            for (ItemStack item : itemsToReturn) {
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(item);
                } else {
                    player.getWorld().dropItem(player.getLocation(), item);
                }
            }
        }
    }
}