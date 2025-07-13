package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CORRIGÉ : Gestionnaire de durabilité pour les pioches légendaires
 * - Solidité ne change plus la durabilité max mais donne chance d'éviter la perte
 * - Notifications pioche cassée dans action bar
 */
public class PickaxeDurabilityListener implements Listener {

    private final PrisonTycoon plugin;

    public PickaxeDurabilityListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Vérifie si c'est une pioche légendaire
        if (!plugin.getPickaxeManager().isLegendaryPickaxe(item)) {
            return; // Laisse le comportement normal pour les autres items
        }

        // Vérifie que c'est bien la pioche du joueur
        if (!plugin.getPickaxeManager().isOwner(item, player)) {
            return;
        }

        // CRITIQUE : Annule TOUJOURS les dégâts automatiques du jeu
        event.setCancelled(true);

        // Gère manuellement la durabilité
        handleCustomDurability(player, item, event.getDamage());
    }

    /**
     * CORRIGÉ : Gère la durabilité sans augmenter la durabilité max
     */
    private void handleCustomDurability(Player player, ItemStack pickaxe, int damage) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");

        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();

        // CORRIGÉ : La solidité donne une chance d'éviter la perte, pas d'augmentation max
        if (durabilityLevel > 0) {
            // Chance de ne PAS perdre de durabilité basée sur le niveau
            double preservationChance = Math.min(0.95, durabilityLevel * 0.05); // 5% par niveau, max 95%

            if (ThreadLocalRandom.current().nextDouble() < preservationChance) {
                // La pioche ne perd pas de durabilité cette fois
                plugin.getPluginLogger().debug("Durabilité préservée pour " + player.getName() +
                        " (chance: " + String.format("%.1f%%", preservationChance * 100) + ")");

                // Vérifie quand même l'état de la pioche pour les effets
                checkPickaxeState(player, pickaxe, currentDurability, maxDurability);
                return;
            }
        }

        // Applique la perte de durabilité normale
        int newDurability = Math.min(currentDurability + damage, maxDurability - 1);
        pickaxe.setDurability((short) newDurability);

        // Vérifie l'état de la pioche après modification
        checkPickaxeState(player, pickaxe, (short) newDurability, maxDurability);

        plugin.getPluginLogger().debug("Durabilité mise à jour pour " + player.getName() +
                ": " + newDurability + "/" + maxDurability +
                " (solidité niveau " + durabilityLevel + ")");
    }

    private void checkPickaxeState(Player player, ItemStack pickaxe, short currentDurability, short maxDurability) {
        // Calcule le pourcentage de durabilité restante avec la durabilité normale
        double durabilityPercent = 1.0 - ((double) currentDurability / maxDurability);

        // CORRIGÉ : Si durabilité = 0 (durabilité maximale atteinte)
        if (currentDurability >= maxDurability - 1) {
            // Active le mode "pioche cassée" - tous enchantements désactivés sauf tokengreed avec malus
            if (!isPickaxeBroken(player)) {
                activateBrokenPickaxeMode(player);
            }
        } else {
            // Désactive le mode "pioche cassée" si il était actif
            if (isPickaxeBroken(player)) {
                deactivateBrokenPickaxeMode(player);
            }
        }

        if (currentDurability >= maxDurability - 1) {
            // Le mode "pioche cassée" a ses propres notifications
        } else if (durabilityPercent <= 0.10) { // Moins de 10% restant
            // Crée le message cliquable
            TextComponent message = new TextComponent("§6⚠️ Votre pioche est très endommagée ! §e[CLIQUEZ POUR RÉPARER]");
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aOuvrir le menu de réparation")));

            // Envoie le message au joueur
            player.spigot().sendMessage(message);

        } else if (durabilityPercent <= 0.25) { // Moins de 25% restant
            // Crée le message cliquable
            TextComponent message = new TextComponent("§e⚠️ Votre pioche commence à être endommagée. §e[RÉPARER]");
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aOuvrir le menu de réparation")));

            // Envoie le message au joueur
            player.spigot().sendMessage(message);
        }
    }

    /**
     * Vérifie si la pioche est en mode "cassée"
     */
    private boolean isPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    /**
     * CORRIGÉ : Active le mode cassé avec notifications action bar
     */
    private void activateBrokenPickaxeMode(Player player) {
        // Marque le joueur comme ayant une pioche cassée
        player.setMetadata("pickaxe_broken", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        // HARMONISATION : Marque pour notification temporaire dans ActionBarTask
        player.setMetadata("pickaxe_just_broken", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));

        // Retire tous les effets de mobilité
        plugin.getPickaxeManager().removeMobilityEffects(player);

        plugin.getEnchantmentManager().forceDisableAbundanceAndResetCombustion(player);

        // Son d'alerte
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.5f);

        plugin.getPluginLogger().info("Mode pioche cassée activé pour " + player.getName());
    }

    /**
     * CORRIGÉ : Désactive le mode cassé avec notification action bar
     */
    private void deactivateBrokenPickaxeMode(Player player) {
        // Retire le metadata
        player.removeMetadata("pickaxe_broken", plugin);

        // HARMONISATION : Marque pour notification temporaire dans ActionBarTask
        player.setMetadata("pickaxe_just_repaired", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis()));

        // Réapplique les effets de mobilité si approprié
        plugin.getPickaxeManager().updateMobilityEffects(player);

        // Son de récupération
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.2f);

        plugin.getPluginLogger().info("Mode pioche cassée désactivé pour " + player.getName());
    }

    /**
     * MÉTHODE PUBLIQUE : Vérifie si la pioche d'un joueur est cassée (pour les autres classes)
     */
    public static boolean isPlayerPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    /**
     * MÉTHODE PUBLIQUE : Calcule le malus à appliquer selon l'état de la pioche
     */
    public static double getPickaxePenaltyMultiplier(Player player) {
        if (isPlayerPickaxeBroken(player)) {
            return 0.1; // 90% de malus = on garde 10%
        }
        return 1.0; // Aucun malus
    }
}