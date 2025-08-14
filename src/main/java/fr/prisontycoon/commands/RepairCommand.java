package fr.prisontycoon.commands;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Sound;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

    /**
     * Commande /repair - Répare gratuitement l'item tenu en main (si réparable) avec un cooldown
     */
public class RepairCommand implements CommandExecutor, TabCompleter {

    private final PrisonTycoon plugin;

    public RepairCommand(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cCette commande ne peut être exécutée que par un joueur!");
            return true;
        }
        
        // Condition de métier: Guerrier niveau 5+
        fr.prisontycoon.data.PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();
        if (activeProfession == null || !"guerrier".equalsIgnoreCase(activeProfession) || playerData.getProfessionLevel("guerrier") < 5 || !player.hasPermission("specialmine.repair")) {
            player.sendMessage("§c❌ Vous devez être §cGuerrier §7niveau §e5+ §cpour utiliser /repair!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // Cooldown 3h (persistant)
        long now = System.currentTimeMillis();
        long last = playerData.getLastRepairTime();
        long cooldownMs = 3L * 60L * 60L * 1000L; // 3 heures
        long remaining = (last + cooldownMs) - now;
        if (remaining > 0) {
            long hours = remaining / (60L * 60L * 1000L);
            long minutes = (remaining % (60L * 60L * 1000L)) / (60L * 1000L);
            long seconds = (remaining % (60L * 1000L)) / 1000L;
            player.sendMessage("§c⌛ /repair en cooldown: §e" + hours + "h " + minutes + "m " + seconds + "s");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        // Répare l'item en main si réparable
        ItemStack inHand = player.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType() == Material.AIR) {
            player.sendMessage("§c❌ Vous devez tenir un item à réparer dans votre main!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        ItemMeta meta = inHand.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            player.sendMessage("§c❌ Cet item ne peut pas être réparé!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return true;
        }

        if (damageable.getDamage() <= 0) {
            player.sendMessage("§eℹ Cet item est déjà en parfait état!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7f, 1.2f);
            return true;
        }

        damageable.setDamage(0);
        inHand.setItemMeta(meta);

        // Set cooldown timestamp
        playerData.setLastRepairTime(now);
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Feedback
        player.sendMessage("§a✅ Votre §e" + inHand.getType().name().toLowerCase() + " §aa été entièrement réparé!");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        return new ArrayList<>(); // Pas de complétion pour cette commande
    }
}