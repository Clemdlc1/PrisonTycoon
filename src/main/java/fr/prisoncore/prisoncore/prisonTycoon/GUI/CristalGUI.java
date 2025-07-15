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
     * Affiche les cristaux actuellement appliqués avec lore dynamique pour les emplacements vides
     */
    private void fillAppliedCristals(Inventory inv, Player player) {
        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Slots pour les cristaux appliqués (10-13)
        int[] slots = {11, 12, 14, 15};

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
                // Slot vide avec lore dynamique
                ItemStack emptySlot = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
                ItemMeta meta = emptySlot.getItemMeta();
                meta.setDisplayName("§7⬜ Emplacement libre §8(" + (i + 1) + "/4)");

                // NOUVEAU: Lore dynamique avec coût XP
                List<String> lore = new ArrayList<>();
                lore.add("§7Cet emplacement est libre.");
                lore.add("");

                // Calculer le coût pour cet emplacement
                long applicationCost = plugin.getCristalManager().getApplicationCost(player);
                long playerXP = playerData.getExperience();

                if (applicationCost > 0) {
                    lore.add("§6💰 Coût d'application:");
                    lore.add("§e  " + NumberFormatter.format(applicationCost) + " XP");
                    lore.add("");

                    if (playerXP >= applicationCost) {
                        lore.add("§a✅ Vous avez assez d'XP!");
                    } else {
                        long missing = applicationCost - playerXP;
                        lore.add("§c❌ XP insuffisant");
                        lore.add("§c  Il vous manque: " + NumberFormatter.format(missing) + " XP");
                    }
                    lore.add("");
                }

                lore.add("§e▸ Cliquez sur un cristal révélé");
                lore.add("§e  dans votre inventaire pour");
                lore.add("§e  l'appliquer ici");

                meta.setLore(lore);
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
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§7");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, filler);
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
            return;
        }
        player.sendMessage("hello");
        // Tenter l'application du cristal
        if (plugin.getCristalManager().applyCristalToPickaxe(player, pickaxe, cristal)) {
            // NOUVEAU: Actualiser le GUI immédiatement après l'application
            player.closeInventory();
            openCristalMenu(player);
            player.sendMessage("lol");
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

    /**
     * NOUVEAU: Actualise le GUI des cristaux si un joueur l'a ouvert
     */
    public void refreshCristalGUI(Player player) {
        if (player.getOpenInventory() != null &&
                player.getOpenInventory().getTitle().equals("§d✨ Gestion des Cristaux ✨")) {

            // Actualiser seulement les parties dynamiques du GUI
            Inventory inv = player.getOpenInventory().getTopInventory();

            // Actualiser les informations de la pioche (slot 4)
            fillPickaxeInfo(inv, player);

            // Actualiser les cristaux appliqués (slots avec lore dynamique)
            fillAppliedCristals(inv, player);
        }
    }
}