package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
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
 * Interface graphique pour la gestion du carburant des automineurs
 * Le joueur peut déposer des têtes de joueur/monstre pour alimenter ses automineurs
 */
public class AutominerFuelGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey valueKey;

    // Layout du menu (54 slots - 6 lignes)
    private static final int FUEL_INFO_SLOT = 4;
    private static final int BACK_BUTTON_SLOT = 45;
    private static final int DEPOSIT_ALL_SLOT = 53;

    // Zone de dépôt des têtes (3x7 = 21 slots centraux)
    private static final int[] DEPOSIT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    public AutominerFuelGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
        this.valueKey = new NamespacedKey(plugin, "gui_value");
    }

    /**
     * Ouvre le menu de gestion du carburant
     */
    public void openFuelMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 54, "§c⛽ Carburant Automineurs");

        // Bordures décoratives
        fillBorders(inv);

        // Informations du carburant (slot 4)
        inv.setItem(FUEL_INFO_SLOT, createFuelInfoItem(playerData));

        // Bouton de retour (slot 45)
        inv.setItem(BACK_BUTTON_SLOT, createBackButton());

        // Bouton de dépôt automatique (slot 53)
        inv.setItem(DEPOSIT_ALL_SLOT, createDepositAllButton());

        // Instructions dans la zone centrale
        fillDepositArea(inv);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
    }

    /**
     * Crée l'item d'information du carburant
     */
    private ItemStack createFuelInfoItem(PlayerData playerData) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c⛽ §lINFORMATIONS CARBURANT");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Carburant actuel
        long currentFuel = playerData.getAutominerFuel();
        lore.add("§7Têtes stockées: §f" + NumberFormatter.format(currentFuel));
        lore.add("");

        // Consommation des automineurs
        int totalConsumption = calculateTotalFuelConsumption(playerData);
        if (totalConsumption > 0) {
            lore.add("§7Consommation totale: §c" + totalConsumption + " têtes/heure");

            long remainingTime = currentFuel * 60 / totalConsumption; // en minutes
            lore.add("§7Temps restant: §f" + formatTime(remainingTime));
            lore.add("");

            // Alertes de niveau de carburant
            if (remainingTime < 60) {
                lore.add("§c⚠ NIVEAU CRITIQUE!");
                lore.add("§cAjoutez du carburant rapidement!");
            } else if (remainingTime < 300) {
                lore.add("§e⚠ Niveau bas de carburant.");
            } else {
                lore.add("§a✅ Niveau de carburant suffisant.");
            }
        } else {
            lore.add("§7Aucun automineur actif.");
            lore.add("§a✅ Pas de consommation de carburant.");
        }

        lore.add("");
        lore.add("§e💡 §lCOMMENT AJOUTER DU CARBURANT:");
        lore.add("§7▸ Placez des têtes de joueur/monstre");
        lore.add("§7▸ dans la zone centrale de ce menu");
        lore.add("§7▸ Fermez le menu pour valider");
        lore.add("§7▸ Ou utilisez le bouton 'Déposer Tout'");

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
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
     * Crée le bouton de dépôt automatique
     */
    private ItemStack createDepositAllButton() {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§a📥 §lDÉPOSER TOUT");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Dépose automatiquement toutes les têtes");
        lore.add("§7de votre inventaire dans la réserve de carburant.");
        lore.add("");
        lore.add("§7Types acceptés:");
        lore.add("§7▸ Têtes de joueur");
        lore.add("§7▸ Têtes de monstre (squelette, zombie, etc.)");
        lore.add("");
        lore.add("§a🖱 §lCLIC: §aDéposer toutes les têtes");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        setItemAction(meta, "deposit_all_heads", "");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Remplit la zone de dépôt avec des instructions
     */
    private void fillDepositArea(Inventory inv) {
        // Créer un item d'instruction au centre
        ItemStack instructionItem = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = instructionItem.getItemMeta();

        meta.setDisplayName("§6📋 §lZONE DE DÉPÔT");

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Placez vos têtes de joueur/monstre");
        lore.add("§7dans cette zone pour les ajouter");
        lore.add("§7à votre réserve de carburant.");
        lore.add("");
        lore.add("§aTêtes acceptées:");
        lore.add("§7▸ Tête de joueur");
        lore.add("§7▸ Tête de squelette");
        lore.add("§7▸ Tête de zombie");
        lore.add("§7▸ Tête de creeper");
        lore.add("§7▸ Et autres têtes de monstres");
        lore.add("");
        lore.add("§e💡 §lASTUCE:");
        lore.add("§7Fermez le menu pour valider le dépôt");
        lore.add("§7ou utilisez le bouton §a'Déposer Tout'§7.");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        instructionItem.setItemMeta(meta);

        // Placer l'instruction au centre
        inv.setItem(22, instructionItem);

        // Remplir les autres slots avec du verre transparent
        ItemStack fillerItem = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = fillerItem.getItemMeta();
        fillerMeta.setDisplayName("§7Déposez vos têtes ici");
        List<String> fillerLore = new ArrayList<>();
        fillerLore.add("§7Zone de dépôt pour le carburant");
        fillerMeta.setLore(fillerLore);
        fillerItem.setItemMeta(fillerMeta);

        for (int slot : DEPOSIT_SLOTS) {
            if (slot != 22) { // Ne pas remplacer l'instruction centrale
                inv.setItem(slot, fillerItem);
            }
        }
    }

    /**
     * Gère les clics dans le menu de carburant
     */
    public void handleFuelClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || clickedItem.getItemMeta() == null) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        String value = meta.getPersistentDataContainer().get(valueKey, PersistentDataType.STRING);

        if (action == null) return;

        switch (action) {
            case "back_to_main" -> {
                // Traiter les items déposés avant de fermer
                processFuelDeposit(player);
                plugin.getAutominerGUI().openMainMenu(player);
            }
            case "deposit_all_heads" -> {
                depositAllHeadsFromInventory(player);
            }
        }
    }

    /**
     * Traite les items déposés dans la zone de carburant
     */
    public void processFuelDeposit(Player player) {
        Inventory fuelInventory = player.getOpenInventory().getTopInventory();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        int headsDeposited = 0;
        List<ItemStack> invalidItems = new ArrayList<>();

        // Parcourir les slots de dépôt
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack item = fuelInventory.getItem(slot);
            if (item != null && item.getType() != Material.AIR) {

                if (isValidHead(item)) {
                    // Item valide - compter les têtes
                    headsDeposited += item.getAmount();
                } else {
                    // Item invalide - le sauvegarder pour le redonner
                    invalidItems.add(item.clone());
                }

                // Vider le slot
                fuelInventory.setItem(slot, null);
            }
        }

        // Ajouter les têtes à la réserve
        if (headsDeposited > 0) {
            playerData.addAutominerFuel(headsDeposited);
            player.sendMessage("§a✅ " + headsDeposited + " têtes ajoutées à la réserve de carburant!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        }

        // Redonner les items invalides
        if (!invalidItems.isEmpty()) {
            for (ItemStack invalidItem : invalidItems) {
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(invalidItem);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), invalidItem);
                }
            }
            player.sendMessage("§e⚠ " + invalidItems.size() + " items invalides rendus à votre inventaire.");
        }

        // Marquer les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());
    }

    /**
     * Dépose automatiquement toutes les têtes de l'inventaire du joueur
     */
    private void depositAllHeadsFromInventory(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        int totalHeads = 0;
        List<ItemStack> toRemove = new ArrayList<>();

        // Parcourir l'inventaire du joueur
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isValidHead(item)) {
                totalHeads += item.getAmount();
                toRemove.add(item);
            }
        }

        if (totalHeads == 0) {
            player.sendMessage("§c❌ Aucune tête trouvée dans votre inventaire!");
            return;
        }

        // Retirer les items de l'inventaire
        for (ItemStack item : toRemove) {
            player.getInventory().removeItem(item);
        }

        // Ajouter à la réserve de carburant
        playerData.addAutominerFuel(totalHeads);

        player.sendMessage("§a✅ " + totalHeads + " têtes déposées automatiquement!");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        // Refresh du menu
        openFuelMenu(player);
    }

    /**
     * Vérifie si un item est une tête valide pour le carburant
     */
    private boolean isValidHead(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        // Têtes acceptées
        return switch (item.getType()) {
            case PLAYER_HEAD, SKELETON_SKULL, ZOMBIE_HEAD, CREEPER_HEAD,
                 DRAGON_HEAD, PIGLIN_HEAD, WITHER_SKELETON_SKULL -> true;
            default -> false;
        };
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
    }

    private void setItemAction(ItemMeta meta, String action, String value) {
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.STRING, value);
    }

    private int calculateTotalFuelConsumption(PlayerData playerData) {
        int totalConsumption = 0;
        for (String autominerUuid : playerData.getActiveAutominers()) {
            // Récupérer les données de l'automineur pour calculer sa consommation
            // Basé sur le type et les enchantements FuelEfficiency
            totalConsumption += calculateAutominerFuelConsumption(autominerUuid);
        }
        return totalConsumption;
    }

    private int calculateAutominerFuelConsumption(String autominerUuid) {
        // Consommation de base selon le type d'automineur
        // Pierre: 1 tête/60min, Fer: 1 tête/30min, Or: 1 tête/15min,
        // Diamant: 1 tête/5min, Émeraude: 1 tête/3min, Beacon: 1 tête/1min

        // Pour l'instant, retourne une valeur par défaut
        // Dans une implémentation complète, il faudrait récupérer le type et les enchantements
        return 1; // 1 tête par heure par défaut
    }

    private String formatTime(long minutes) {
        if (minutes >= 1440) { // Plus d'un jour
            long days = minutes / 1440;
            long remainingHours = (minutes % 1440) / 60;
            return days + "j" + (remainingHours > 0 ? remainingHours + "h" : "");
        } else if (minutes >= 60) {
            long hours = minutes / 60;
            long remainingMinutes = minutes % 60;
            return hours + "h" + (remainingMinutes > 0 ? remainingMinutes + "m" : "");
        }
        return minutes + "m";
    }
}