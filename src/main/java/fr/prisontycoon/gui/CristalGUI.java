package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
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
    private final NamespacedKey actionKey;
    private final NamespacedKey cristalUuidKey;

    public CristalGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
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

        plugin.getGUIManager().registerOpenGUI(player, GUIType.CRISTAL_MANAGEMENT, inv);
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

        // Coûts d'application pour chaque emplacement
        long[] applicationCosts = {10000, 50000, 100000, 200000}; // 1er, 2ème, 3ème, 4ème cristal
        long playerXP = playerData.getExperience();

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
                // MODIFIÉ: Slot vide avec prix spécifique et couleur dynamique
                long slotCost = applicationCosts[i];
                boolean canAfford = playerXP >= slotCost;

                // Choix du matériau et couleur selon les moyens du joueur
                Material slotMaterial = canAfford ? Material.PURPLE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
                String slotColor = canAfford ? "§7" : "§c";

                ItemStack emptySlot = new ItemStack(slotMaterial);
                ItemMeta meta = emptySlot.getItemMeta();
                meta.setDisplayName(slotColor + "⬜ Emplacement libre §8(" + (i + 1) + "/4)");

                // NOUVEAU: Lore dynamique avec prix spécifique pour chaque emplacement
                List<String> lore = new ArrayList<>();
                lore.add("§7Cet emplacement est libre.");
                lore.add("");

                // Affichage du coût spécifique à cet emplacement
                String[] slotNames = {"1er", "2ème", "3ème", "4ème"};
                lore.add("§6💰 Coût pour le " + slotNames[i] + " cristal:");
                lore.add("§e  " + NumberFormatter.format(slotCost) + " XP");
                lore.add("");

                if (canAfford) {
                    lore.add("§a✅ Vous avez assez d'XP!");
                    lore.add("");
                    lore.add("§e▸ Cliquez sur un cristal révélé");
                    lore.add("§e  dans votre inventaire pour");
                    lore.add("§e  l'appliquer ici");
                } else {
                    long missing = slotCost - playerXP;
                    lore.add("§c❌ XP insuffisant");
                    lore.add("§c  Il vous manque: " + NumberFormatter.format(missing) + " XP");
                    lore.add("");
                    lore.add("§7Obtenez plus d'XP en minant");
                    lore.add("§7pour débloquer cet emplacement");
                }

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
        backMeta.setLore(List.of("§7Retour au menu des enchantements"));
        backMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back");
        back.setItemMeta(backMeta);
        inv.setItem(18, back);

        // Bouton fermer
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c❌ Fermer");
        closeMeta.setLore(List.of("§7Ferme ce menu"));
        closeMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        close.setItemMeta(closeMeta);
        inv.setItem(26, close);
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

    public void handleCristalApplicationClick(Player player, InventoryClickEvent event) {
        ItemStack clickedItem = event.getCurrentItem();

        // On vérifie si l'item cliqué est un cristal révélé
        if (clickedItem == null || !plugin.getCristalManager().isCristal(clickedItem)) {
            return;
        }

        Cristal cristal = plugin.getCristalManager().getCristalFromItem(clickedItem);
        if (cristal == null || cristal.isVierge()) {
            player.sendMessage("§cCe cristal doit d'abord être révélé (clic-droit en main).");
            return;
        }

        // C'est un cristal applicable, on annule l'événement pour prendre le contrôle.
        event.setCancelled(true);

        ItemStack pickaxe = plugin.getPickaxeManager().getPlayerPickaxe(player);
        if (pickaxe == null) {
            player.sendMessage("§cVous devez avoir une pioche légendaire pour appliquer des cristaux!");
            return;
        }

        // On tente l'application du cristal
        if (plugin.getCristalManager().applyCristalToPickaxe(player, pickaxe, cristal)) {
            // L'application a réussi, on consomme l'item
            clickedItem.setAmount(clickedItem.getAmount() - 1);

            // On rafraîchit l'interface pour montrer le changement
            plugin.getCristalGUI().refreshCristalGUI(player);
        }
        // Si l'application échoue, les messages d'erreur sont déjà envoyés par applyCristalToPickaxe.
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