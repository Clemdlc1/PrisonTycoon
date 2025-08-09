package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;

public class MobilityEffectsListener implements Listener {

    private final PrisonTycoon plugin;

    public MobilityEffectsListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Gère le respawn du joueur - remet les effets si il a la pioche + vérifie l'état de la pioche
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // Délai d'1 tick pour s'assurer que le joueur est complètement respawn
        new BukkitRunnable() {
            @Override
            public void run() {

                // NOUVEAU : Vérifie l'état de la pioche légendaire après respawn
                ItemStack pickaxe = plugin.getPickaxeManager().findPlayerPickaxe(player);
                if (pickaxe != null && pickaxe.getItemMeta() instanceof Damageable meta) {
                    short currentDurability = (short) meta.getDamage();
                    short maxDurability = pickaxe.getType().getMaxDurability();

                    plugin.getPickaxeManager().checkLegendaryPickaxeState(player, pickaxe, currentDurability, maxDurability);
                }
                plugin.getPickaxeManager().updateMobilityEffects(player);

            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Gère le changement d'item en main (slot de hotbar)
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        // Délai d'1 tick pour s'assurer que l'item est complètement changé
        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getPickaxeManager().updateMobilityEffects(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    /**
     * Applique l'effet Planneur (chute lente) lors d'une chute si l'enchantement est actif.
     * Sneak pour annuler temporairement.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        int planneur = data.getEnchantmentLevel("planneur");
        if (planneur <= 0) return;

        // Désactivation si le joueur sneak
        if (player.isSneaking()) {
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
            return;
        }

        // Applique l'effet si le joueur est en l'air et chute
        if (player.getLocation().subtract(0, 0.1, 0).getBlock().getType().isAir() && player.getVelocity().getY() < -0.08) {
            PotionEffect current = player.getPotionEffect(PotionEffectType.SLOW_FALLING);
            if (current == null || current.getDuration() < 20) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, true, false));
            }
        }
    }
}