package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.Random;

/**
 * CORRIGÉ : Système de notifications une seule fois par niveau et message pour 100%
 */
public class PickaxeDurabilityListener implements Listener {

    private final PrisonTycoon plugin;
    private final Random random = new Random();

    public PickaxeDurabilityListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Vérifier si c'est une pioche
        if (!tool.getType().name().contains("PICKAXE")) {
            return;
        }

        // Obtenir les données du joueur pour l'enchantement de solidité
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");

        // CORRIGÉ : Chance d'éviter la perte de durabilité avec solidité
        if (durabilityLevel > 0) {
            double preservationChance = Math.min(95.0, durabilityLevel * 5.0);
            if (random.nextDouble() * 100 < preservationChance) {
                return; // La durabilité est préservée
            }
        }

        // Appliquer le dommage normal (1 point)
        if (tool.getItemMeta() instanceof Damageable) {
            Damageable meta = (Damageable) tool.getItemMeta();
            short maxDurability = tool.getType().getMaxDurability();
            short currentDurability = (short) meta.getDamage();

            // Augmente les dégâts de 1 point
            short newDurability = (short) Math.min(currentDurability + 1, maxDurability - 1);
            meta.setDamage(newDurability);
            tool.setItemMeta(meta);

            // Vérifier l'état après modification
            checkPickaxeState(player, tool, newDurability, maxDurability);
        }
    }

    /**
     * NOUVEAU : Vérification avec système de notification une seule fois par niveau
     */
    private void checkPickaxeState(Player player, ItemStack pickaxe, short currentDurability, short maxDurability) {
        double durabilityPercent = 1.0 - ((double) currentDurability / maxDurability);

        // NOUVEAU : Message pour pioche à 100% (cassée)
        if (currentDurability >= maxDurability - 1) {
            // Active le mode "pioche cassée"
            if (!isPickaxeBroken(player)) {
                activateBrokenPickaxeMode(player);

                // Message spécial pour pioche cassée (une seule fois)
                if (!player.hasMetadata("durability_notif_broken")) {
                    TextComponent message = new TextComponent("§c💀 PIOCHE CASSÉE! Tous enchantements désactivés! §e[RÉPARER IMMÉDIATEMENT]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§cRéparation critique requise!")));
                    player.spigot().sendMessage(message);

                    // Marque pour éviter le spam
                    player.setMetadata("durability_notif_broken", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                }
            }
        } else {
            // Désactive le mode "pioche cassée" si il était actif
            if (isPickaxeBroken(player)) {
                deactivateBrokenPickaxeMode(player);

                // Reset le flag de notification cassée
                player.removeMetadata("durability_notif_broken", plugin);
            }

            // NOUVEAU : Système de notification une seule fois par niveau
            if (durabilityPercent <= 0.10) { // Moins de 10% restant
                if (!player.hasMetadata("durability_notif_10")) {
                    TextComponent message = new TextComponent("§6⚠️ Votre pioche est très endommagée ! §e[CLIQUEZ POUR RÉPARER]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aOuvrir le menu de réparation")));
                    player.spigot().sendMessage(message);

                    // Marque pour éviter les répétitions
                    player.setMetadata("durability_notif_10", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                }
            } else if (durabilityPercent <= 0.25) { // Moins de 25% restant
                if (!player.hasMetadata("durability_notif_25")) {
                    TextComponent message = new TextComponent("§e⚠️ Votre pioche commence à être endommagée. §e[RÉPARER]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aOuvrir le menu de réparation")));
                    player.spigot().sendMessage(message);

                    // Marque pour éviter les répétitions
                    player.setMetadata("durability_notif_25", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                }
            }
        }
    }

    private boolean isPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    private void activateBrokenPickaxeMode(Player player) {
        player.setMetadata("pickaxe_broken", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        player.setMetadata("pickaxe_just_broken", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));

        plugin.getPickaxeManager().removeMobilityEffects(player);
        plugin.getEnchantmentManager().forceDisableAbundanceAndResetCombustion(player);

        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.5f);
        plugin.getPluginLogger().info("Mode pioche cassée activé pour " + player.getName());
    }

    private void deactivateBrokenPickaxeMode(Player player) {
        player.removeMetadata("pickaxe_broken", plugin);
        player.setMetadata("pickaxe_just_repaired", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));

        plugin.getPickaxeManager().updateMobilityEffects(player);

        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);
        plugin.getPluginLogger().info("Mode pioche cassée désactivé pour " + player.getName());
    }

    public static boolean isPlayerPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    public static double getPickaxePenaltyMultiplier(Player player) {
        if (isPlayerPickaxeBroken(player)) {
            return 0.1; // 90% de malus = on garde 10%
        }
        return 1.0; // Aucun malus
    }
}