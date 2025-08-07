package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Commande pour voir l'inventaire d'autres joueurs
 * Usage: /invsee <joueur>
 */
public class InvseeCommand implements CommandExecutor, TabCompleter, Listener {

    private final PrisonTycoon plugin;

    // Map pour suivre qui regarde l'inventaire de qui
    private final Map<UUID, UUID> viewingPlayers = new ConcurrentHashMap<>();
    // Map pour les inventaires en lecture seule
    private final Map<UUID, String> readOnlyViewers = new ConcurrentHashMap<>();

    public InvseeCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
        // Enregistre les événements
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c❌ Cette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        // CORRECTION : Vérifier directement la permission de la commande
        if (!player.hasPermission("specialmine.invsee.use")) {
            player.sendMessage("§c❌ Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        // Vérifie si le joueur peut modifier les inventaires (admin seulement)
        boolean canModify = player.hasPermission("specialmine.invsee.modify");

        if (args.length != 1) {
            player.sendMessage("§c❌ Usage: /invsee <joueur>");
            return true;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);

        if (target == null) {
            player.sendMessage("§c❌ Joueur introuvable ou hors ligne: " + targetName);
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage("§c❌ Vous ne pouvez pas voir votre propre inventaire de cette façon!");
            return true;
        }

        // Ouvre l'inventaire avec les bonnes permissions
        openInventoryView(player, target, canModify);
        return true;
    }

    /**
     * Ouvre une vue de l'inventaire du joueur cible
     */
    private void openInventoryView(Player viewer, Player target, boolean canModify) {
        // Crée un inventaire 6 lignes pour afficher TOUT l'équipement
        Inventory inv = Bukkit.createInventory(null, 45,
                "§8Inventaire de " + target.getName() + (canModify ? "" : " §c(Lecture seule)"));

        // === INVENTAIRE PRINCIPAL (slots 0-35) ===
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            if (i < 36) {
                inv.setItem(i, contents[i]);
            }
        }

        // === ÉQUIPEMENT D'ARMURE (slots 36-39) ===
        ItemStack[] armor = target.getInventory().getArmorContents();
        // armor[0] = bottes, armor[1] = jambières, armor[2] = plastron, armor[3] = casque
        if (armor.length >= 4) {
            inv.setItem(36, armor[3]); // Casque
            inv.setItem(37, armor[2]); // Plastron
            inv.setItem(38, armor[1]); // Jambières
            inv.setItem(39, armor[0]); // Bottes
        }

        // === MAIN SECONDAIRE (slot 40) ===
        ItemStack offHand = target.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() != Material.AIR) {
            inv.setItem(40, offHand);
        }

        // === SÉPARATEURS VISUELS ===
        ItemStack separator = createSeparatorItem();
        for (int i = 41; i < 44; i++) {
            inv.setItem(i, separator);
        }

        // === PERMISSIONS ET BOUTONS ===
        if (canModify) {
            inv.setItem(44, createAdminActionsItem());
        }

        // Marque la relation viewer -> target
        viewingPlayers.put(viewer.getUniqueId(), target.getUniqueId());

        // Marque comme lecture seule si pas admin
        if (!canModify) {
            readOnlyViewers.put(viewer.getUniqueId(), target.getName());
        }
        plugin.getPickaxeManager().updatePlayerPickaxe(target);
        // Ouvre l'inventaire
        viewer.openInventory(inv);

        // Messages améliorés
        viewer.sendMessage("§a👁 Vous regardez l'inventaire de §e" + target.getName());
        if (canModify) {
            viewer.sendMessage("§a⚙ Mode administrateur - vous pouvez modifier le contenu");
        } else {
            viewer.sendMessage("§7👀 Mode lecture seule - vous ne pouvez pas modifier le contenu");
        }

        // Log l'action avec plus de détails
        plugin.getChatLogger().logAdminAction(viewer.getName(), "INVSEE", target.getName(),
                canModify ? "Consultation avec modification" : "Consultation lecture seule");
    }

    /**
     * Crée un item séparateur
     */
    private ItemStack createSeparatorItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§8▬▬▬");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createAdminActionsItem() {
        ItemStack item = new ItemStack(Material.COMMAND_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§l⚙ Actions Admin");
        meta.setLore(Arrays.asList(
                "§7Vous êtes en mode administrateur",
                "§7Vous pouvez modifier cet inventaire",
                "§c⚠ Soyez prudent avec les modifications!"
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID viewerUuid = player.getUniqueId();

        // Vérifie si c'est un inventaire invsee
        if (!viewingPlayers.containsKey(viewerUuid)) return;

        String invTitle = plugin.getGUIManager().getLegacyTitle(event.getView());
        if (!invTitle.startsWith("§8Inventaire de ")) return;

        UUID targetUuid = viewingPlayers.get(viewerUuid);
        Player target = Bukkit.getPlayer(targetUuid);

        if (target == null) {
            player.closeInventory();
            player.sendMessage("§c❌ Le joueur cible n'est plus en ligne!");
            return;
        }

        int slot = event.getRawSlot();

        // Si c'est en lecture seule, bloque toutes les modifications
        if (readOnlyViewers.containsKey(viewerUuid)) {
            event.setCancelled(true);
            if (slot < 41) { // Seulement pour les slots d'items
                player.sendMessage("§c❌ Mode lecture seule - vous ne pouvez pas modifier l'inventaire!");
            }
            return;
        }

        // Pour les admins, permet la modification avec synchronisation
        if (slot < 36) {
            // Modification de l'inventaire principal
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                syncInventorySlot(target, slot, event.getInventory().getItem(slot));
                player.sendMessage("§a✅ Slot " + slot + " modifié pour " + target.getName());
            }, 1L);

        } else if (slot >= 36 && slot < 40) {
            // Modification de l'armure
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                syncArmorSlot(target, slot - 36, event.getInventory().getItem(slot));
                player.sendMessage("§a✅ Équipement modifié pour " + target.getName());
            }, 1L);

        } else if (slot == 40) {
            // Modification de la main secondaire
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                target.getInventory().setItemInOffHand(event.getInventory().getItem(slot));
                player.sendMessage("§a✅ Main secondaire modifiée pour " + target.getName());
            }, 1L);
        } else {
            event.setCancelled(true);
        }
    }

    /**
     * Synchronise un slot d'inventaire principal
     */
    private void syncInventorySlot(Player target, int slot, ItemStack item) {
        target.getInventory().setItem(slot, item);
    }

    /**
     * Synchronise un slot d'armure
     */
    private void syncArmorSlot(Player target, int armorSlot, ItemStack item) {
        ItemStack[] armor = target.getInventory().getArmorContents();
        armor[armorSlot] = item;
        target.getInventory().setArmorContents(armor);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID viewerUuid = player.getUniqueId();

        if (viewingPlayers.containsKey(viewerUuid)) {
            UUID targetUuid = viewingPlayers.remove(viewerUuid);
            boolean wasReadOnly = readOnlyViewers.remove(viewerUuid) != null;

            Player target = Bukkit.getPlayer(targetUuid);
            String targetName = target != null ? target.getName() : "Joueur déconnecté";

            player.sendMessage("§7👁 Vous avez fermé l'inventaire de §e" + targetName);

            if (!wasReadOnly && target != null) {
                // Notifie le joueur cible que son inventaire a été modifié par un admin
                target.sendMessage("§7⚠ Votre inventaire a été consulté par un administrateur");
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> allPlayerNames = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.equals(sender)) {
                    allPlayerNames.add(player.getName());
                }
            }
            StringUtil.copyPartialMatches(args[0], allPlayerNames, completions);
        }
        Collections.sort(completions);
        return completions;
    }
}