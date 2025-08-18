package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Menu des Pets (Feature future)
 * CORRIGÉ : Noms en gras et gestion des clics
 */
public class PetsMenuGUI {

    private final PrisonTycoon plugin;

    public PetsMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    public void openPetsMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(54, "§6✨ §lCompagnons §7(équipe & collection)");
        fillWithFutureItems(gui);

        // Emplacements 11/13/15: slots d'équipe (max actuel: 1, extensible plus tard)
        int[] teamSlots = {11, 13, 15};
        for (int idx = 0; idx < teamSlots.length; idx++) {
            ItemStack slotItem = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
            ItemMeta sm = slotItem.getItemMeta();
            plugin.getGUIManager().applyName(sm, idx == 0 ? "§aSlot d'équipe §f#1 (actif)" : "§7Slot d'équipe §f#" + (idx + 1) + " (verrouillé)" );
            plugin.getGUIManager().applyLore(sm, List.of(
                    idx == 0 ? "§7Placez un pet ici pour l'équiper." : "§8Se débloquera via quêtes plus tard."
            ));
            slotItem.setItemMeta(sm);
            gui.setItem(teamSlots[idx], slotItem);
        }

        // Vitrine de collection (lignes 3-5)
        int start = 19;
        int i = 0;
        var petsData = plugin.getPetService().getPlayerPets(player.getUniqueId());
        for (var def : fr.prisontycoon.pets.PetRegistry.all()) {
            ItemStack head = plugin.getPetService().getHeadFor(def);
            ItemMeta hm = head.getItemMeta();
            plugin.getGUIManager().applyName(hm, def.displayName());
            var pd = petsData.get(def.id());
            boolean owned = pd != null;
            boolean equipped = owned && pd != null && pd.equipped;
            int growth = 0;
            if (owned && pd != null) {
                growth = Math.max(0, pd.growth);
            }
            double totalBonus = owned ? def.basePerGrowthPercent() * growth : 0.0;
            plugin.getGUIManager().applyLore(hm, List.of(
                    "§7Rareté: §f" + def.rarity().name(),
                    "§7Effet: §f" + def.effectType().name(),
                    "§7Base/croissance: §a+" + def.basePerGrowthPercent() + "%",
                    "",
                    owned ? (equipped ? "§aÉquipé §7| Croissance §f" + growth + " §8(§a+" + String.format(java.util.Locale.FRANCE, "%.2f", totalBonus) + "%§8)"
                            : "§ePossédé §7| Croissance §f" + growth + " §8(§a+" + String.format(java.util.Locale.FRANCE, "%.2f", totalBonus) + "%§8)")
                          : "§cNon possédé",
                    owned ? (equipped ? "§7Clic: §cRetirer" : "§7Clic: §aÉquiper") : "§7Clic: §8Indisponible"
            ));
            head.setItemMeta(hm);
            int slot = start + i;
            if ((slot % 9) == 0) slot++;
            if (slot >= 52) break;
            gui.setItem(slot, head);
            i++;
        }

        // Bouton retour
        gui.setItem(49, createBackButton());

        plugin.getGUIManager().registerOpenGUI(player, GUIType.PETS_MENU, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1.0f, 1.2f);
    }

    /**
     * CORRECTION: Gère les clics dans le menu pets
     */
    public void handlePetsMenuClick(Player player, int slot, ItemStack item) {
        if (slot == 22 || (item != null && item.getType() == Material.ARROW)) { // Retour
            plugin.getMainMenuGUI().openEnchantmentMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return;
        }
        // Toggle d'équipement en cliquant sur un pet de la vitrine
        if (item != null && item.getType() == Material.PLAYER_HEAD && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            String legacyName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName());
            // displayName au format coloré: on mappe vers l'id
            String id = mapDisplayToId(legacyName);
            if (id != null) {
                boolean ok = plugin.getPetService().toggleEquip(player, id);
                if (ok) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.3f);
                    openPetsMenu(player);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.7f);
                    player.sendMessage("§cAucun slot d'équipe disponible ou pet non possédé.");
                }
            }
        }
    }

    private String mapDisplayToId(String legacyName) {
        if (legacyName == null) return null;
        String plain = legacyName.replaceAll("§[0-9a-fk-or]", "").trim();
        for (var def : fr.prisontycoon.pets.PetRegistry.all()) {
            String defPlain = def.displayName().replaceAll("§[0-9a-fk-or]", "").trim();
            if (defPlain.equalsIgnoreCase(plain)) return def.id();
        }
        return null;
    }

    private ItemStack createBackButton() {
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta meta = arrow.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§7← §lRetour");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retourner au menu principal"));
        arrow.setItemMeta(meta);
        return arrow;
    }

    private void fillWithFutureItems(Inventory gui) {
        ItemStack filler = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§6");
        filler.setItemMeta(meta);

        // Bordures
        int[] borderSlots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 23, 24, 25, 26};
        for (int slot : borderSlots) {
            gui.setItem(slot, filler);
        }
    }
}