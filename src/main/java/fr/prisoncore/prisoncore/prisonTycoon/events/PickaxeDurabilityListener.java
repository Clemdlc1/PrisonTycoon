package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NOUVEAU : Gestionnaire de durabilité pour les pioches légendaires
 * Empêche la casse et gère la solidité correctement
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
     * Gère la durabilité customisée de la pioche légendaire
     */
    private void handleCustomDurability(Player player, ItemStack pickaxe, int damage) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");

        short currentDurability = pickaxe.getDurability();
        short maxDurability = pickaxe.getType().getMaxDurability();

        // Calcule la durabilité maximale avec l'enchantement Solidité
        double durabilityBonus = durabilityLevel * 10.0; // +10% par niveau
        double durabilityMultiplier = 1.0 + (durabilityBonus / 100.0);
        int maxDurabilityWithBonus = (int) (maxDurability * durabilityMultiplier);

        // Si l'enchantement solidité est présent, chance d'éviter la perte
        if (durabilityLevel > 0) {
            // Chance de ne PAS perdre de durabilité basée sur le niveau
            double preservationChance = Math.min(0.95, durabilityLevel * 0.05); // 5% par niveau, max 95%

            if (ThreadLocalRandom.current().nextDouble() < preservationChance) {
                // La pioche ne perd pas de durabilité cette fois
                plugin.getPluginLogger().debug("Durabilité préservée pour " + player.getName() +
                        " (chance: " + String.format("%.1f%%", preservationChance * 100) + ")");

                // Vérifie quand même l'état de la pioche pour les effets
                checkPickaxeState(player, pickaxe, currentDurability, maxDurabilityWithBonus);
                return;
            }
        }

        // Applique la perte de durabilité
        int newDurability = Math.min(currentDurability + damage, maxDurabilityWithBonus - 1);
        pickaxe.setDurability((short) newDurability);

        // Vérifie l'état de la pioche après modification
        checkPickaxeState(player, pickaxe, (short) newDurability, maxDurabilityWithBonus);

        plugin.getPluginLogger().debug("Durabilité mise à jour pour " + player.getName() +
                ": " + newDurability + "/" + maxDurabilityWithBonus +
                " (solidité niveau " + durabilityLevel + ")");
    }

    /**
     * Vérifie l'état de la pioche et applique les effets selon la durabilité
     */
    private void checkPickaxeState(Player player, ItemStack pickaxe, short currentDurability, int maxDurabilityWithBonus) {
        // Calcule le pourcentage de durabilité restante
        double durabilityPercent = 1.0 - ((double) currentDurability / maxDurabilityWithBonus);

        // RÈGLE CRITIQUE : Si durabilité = 1 point restant (99%+ endommagée)
        if (currentDurability >= maxDurabilityWithBonus - 1) {
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

        // Messages d'avertissement selon l'état
        if (currentDurability >= maxDurabilityWithBonus - 1) {
            player.sendMessage("§c💥 PIOCHE GRAVEMENT ENDOMMAGÉE! Tous les enchantements sont désactivés sauf Token Greed (90% malus)!");
        } else if (durabilityPercent < 0.10) { // Moins de 10% restant
            player.sendMessage("§6⚠️ Votre pioche est très endommagée! Réparez-la rapidement.");
        } else if (durabilityPercent < 0.25) { // Moins de 25% restant
            player.sendMessage("§e⚠️ Votre pioche commence à être endommagée.");
        }
    }

    /**
     * Vérifie si la pioche est en mode "cassée"
     */
    private boolean isPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    /**
     * Active le mode "pioche cassée"
     */
    private void activateBrokenPickaxeMode(Player player) {
        // Marque le joueur comme ayant une pioche cassée
        player.setMetadata("pickaxe_broken", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

        // Retire tous les effets de mobilité
        plugin.getPickaxeManager().removeMobilityEffects(player);

        // Message critique
        player.sendMessage("§c💀 ATTENTION: Votre pioche légendaire est gravement endommagée!");
        player.sendMessage("§c➤ Tous les enchantements sont désactivés");
        player.sendMessage("§c➤ Token Greed fonctionne avec 90% de malus");
        player.sendMessage("§e➤ Réparez votre pioche pour retrouver ses capacités!");

        // Son d'alerte
        player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_BREAK, 1.0f, 0.5f);

        plugin.getPluginLogger().info("Mode pioche cassée activé pour " + player.getName());
    }

    /**
     * Désactive le mode "pioche cassée"
     */
    private void deactivateBrokenPickaxeMode(Player player) {
        // Retire le metadata
        player.removeMetadata("pickaxe_broken", plugin);

        // Réapplique les effets de mobilité si approprié
        plugin.getPickaxeManager().updateMobilityEffects(player);

        // Message de récupération
        player.sendMessage("§a✅ Votre pioche légendaire a récupéré ses capacités!");
        player.sendMessage("§a➤ Tous les enchantements sont de nouveau actifs");

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