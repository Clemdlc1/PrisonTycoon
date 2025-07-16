package fr.prisoncore.prisoncore.prisonTycoon.commands;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
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

        // Vérifie les permissions
        boolean isAdmin = player.hasPermission("specialmine.admin");
        boolean isVip = player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin");

        if (!isAdmin && !isVip) {
            player.sendMessage("§c❌ Vous devez être VIP ou Admin pour utiliser cette commande!");
            return true;
        }

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

        // Ouvre l'inventaire
        openInventoryView(player, target, isAdmin);
        return true;
    }

    /**
     * Ouvre une vue de l'inventaire du joueur cible
     */
    private void openInventoryView(Player viewer, Player target, boolean canModify) {
        // Crée un inventaire personnalisé pour afficher tout l'équipement
        Inventory inv = Bukkit.createInventory(null, 54,
                "§8Inventaire de " + target.getName() + (canModify ? "" : " §c(Lecture seule)"));

        // Copie l'inventaire principal (slots 0-35)
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            if (i < 36) {
                inv.setItem(i, contents[i]);
            }
        }

        // Ajoute l'armure dans les slots 36-39
        ItemStack[] armor = target.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            inv.setItem(36 + i, armor[i]);
        }

        // Ajoute l'item de la main secondaire au slot 40
        ItemStack offHand = target.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getType() != Material.AIR) {
            inv.setItem(40, offHand);
        }

        // Ajoute des séparateurs visuels
        ItemStack separator = createSeparatorItem();
        for (int i = 41; i < 45; i++) {
            inv.setItem(i, separator);
        }

        // Ajoute des items d'information
        inv.setItem(45, createInfoItem("§6§lInventaire Principal", "§7Slots 0-35"));
        inv.setItem(46, createInfoItem("§c§lArmure", "§7Casque, Plastron, Jambières, Bottes"));
        inv.setItem(47, createInfoItem("§e§lMain Secondaire", "§7Item de la main gauche"));

        // Item de fermeture
        inv.setItem(53, createCloseItem());

        // Marque la relation viewer -> target
        viewingPlayers.put(viewer.getUniqueId(), target.getUniqueId());

        // Marque comme lecture seule si pas admin
        if (!canModify) {
            readOnlyViewers.put(viewer.getUniqueId(), target.getName());
        }

        // Ouvre l'inventaire
        viewer.openInventory(inv);

        // Messages
        viewer.sendMessage("§a👁 Vous regardez l'inventaire de §e" + target.getName());
        if (!canModify) {
            viewer.sendMessage("§7Mode lecture seule - vous ne pouvez pas modifier le contenu");
        }

        // Log l'action
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

    /**
     * Crée un item d'information
     */
    private ItemStack createInfoItem(String title, String description) {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(Arrays.asList("§7" + description));
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un item de fermeture
     */
    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c§lFermer");
        meta.setLore(Arrays.asList("§7Cliquez pour fermer cette fenêtre"));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        UUID viewerUuid = player.getUniqueId();

        // Vérifie si c'est un inventaire invsee
        if (!viewingPlayers.containsKey(viewerUuid)) return;

        String invTitle = event.getView().getTitle();
        if (!invTitle.startsWith("§8Inventaire de ")) return;

        UUID targetUuid = viewingPlayers.get(viewerUuid);
        Player target = Bukkit.getPlayer(targetUuid);

        if (target == null) {
            player.closeInventory();
            player.sendMessage("§c❌ Le joueur cible n'est plus en ligne!");
            return;
        }

        int slot = event.getRawSlot();

        // Bloque les clics sur les items d'interface (41+)
        if (slot >= 41) {
            event.setCancelled(true);

            // Gère le clic sur fermer
            if (slot == 53) {
                player.closeInventory();
            }
            return;
        }

        // Si c'est en lecture seule, bloque toutes les modifications
        if (readOnlyViewers.containsKey(viewerUuid)) {
            event.setCancelled(true);
            if (slot < 40) { // Seulement pour les slots d'items
                player.sendMessage("§c❌ Mode lecture seule - vous ne pouvez pas modifier l'inventaire!");
            }
            return;
        }

        // Pour les admins, permet la modification
        if (slot < 36) {
            // Modification de l'inventaire principal
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                syncInventorySlot(target, slot, event.getInventory().getItem(slot));
            }, 1L);
        } else if (slot >= 36 && slot < 40) {
            // Modification de l'armure
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                syncArmorSlot(target, slot - 36, event.getInventory().getItem(slot));
            }, 1L);
        } else if (slot == 40) {
            // Modification de la main secondaire
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                target.getInventory().setItemInOffHand(event.getInventory().getItem(slot));
            }, 1L);
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

    /**
     * Nettoie les données quand un joueur se déconnecte
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();

        // Si quelqu'un regarde l'inventaire de ce joueur, ferme sa vue
        viewingPlayers.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(uuid)) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer != null && viewer.isOnline()) {
                    viewer.closeInventory();
                    viewer.sendMessage("§c❌ Le joueur dont vous regardiez l'inventaire s'est déconnecté!");
                }
                return true;
            }
            return false;
        });

        // Si ce joueur regardait l'inventaire de quelqu'un, nettoie
        viewingPlayers.remove(uuid);
        readOnlyViewers.remove(uuid);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Suggestions de noms de joueurs en ligne
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.equals(sender)) { // Exclut le sender
                    completions.add(player.getName());
                }
            }
            StringUtil.copyPartialMatches(args[0], completions, completions);
        }

        Collections.sort(completions);
        return completions;
    }
}