package fr.prisoncore.prisoncore.prisonTycoon.managers;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import fr.prisoncore.prisoncore.prisonTycoon.data.PlayerData;
import fr.prisoncore.prisoncore.prisonTycoon.enchantments.EnchantmentCategory;
import fr.prisoncore.prisoncore.prisonTycoon.utils.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Gestionnaire de la pioche légendaire
 * CORRIGÉ : Gestion des enchantements mobilité activables/désactivables + lore pioche uniquement
 */
public class PickaxeManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey legendaryPickaxeKey;
    private final NamespacedKey pickaxeOwnerKey;

    public PickaxeManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.legendaryPickaxeKey = new NamespacedKey(plugin, "legendary_pickaxe");
        this.pickaxeOwnerKey = new NamespacedKey(plugin, "pickaxe_owner");

        plugin.getPluginLogger().info("§aPickaxeManager initialisé.");
    }

    /**
     * Crée une nouvelle pioche légendaire avec enchantements par défaut
     */
    public ItemStack createLegendaryPickaxe(Player player) {
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();

        // Nom et protection
        meta.setDisplayName("§6✨ Pioche Légendaire ✨");
        meta.getPersistentDataContainer().set(legendaryPickaxeKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(pickaxeOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());

        // Effet glowing sans enchantement visible
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

        // Ajoute les enchantements custom par défaut
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Enchantements par défaut requis
        playerData.setEnchantmentLevel("token_greed", 5);
        playerData.setEnchantmentLevel("efficiency", 3);
        playerData.setEnchantmentLevel("durability", 1);

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Met à jour le lore dès la création
        updatePickaxeLore(meta, player);
        pickaxe.setItemMeta(meta);

        plugin.getPluginLogger().info("§7Pioche légendaire créée pour: " + player.getName() +
                " avec enchantements par défaut");
        return pickaxe;
    }

    /**
     * CORRIGÉ: Met à jour le lore de la pioche avec SEULEMENT les gains via pioche
     */
    public void updatePickaxeLore(ItemMeta meta, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        List<String> lore = new ArrayList<>();

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Pioche légendaire unique et indestructible");
        lore.add("§7Propriétaire: §e" + player.getName());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        // CORRIGÉ: Statistiques UNIQUEMENT via pioche
        lore.add("§6⛏️ §lSTATISTIQUES PIOCHE");
        lore.add("§7│ §6Coins via pioche: §e" + NumberFormatter.formatWithColor(playerData.getCoinsViaPickaxe()));
        lore.add("§7│ §eTokens via pioche: §6" + NumberFormatter.formatWithColor(playerData.getTokensViaPickaxe()));
        lore.add("§7│ §aExpérience via pioche: §2" + NumberFormatter.formatWithColor(playerData.getExperienceViaPickaxe()));
        lore.add("§7│ §bBlocs minés: §3" + NumberFormatter.formatWithColor(playerData.getTotalBlocksMined()));
        lore.add("§7└ §dBlocs détruits: §5" + NumberFormatter.formatWithColor(playerData.getTotalBlocksDestroyed()));
        lore.add("");

        // États spéciaux actifs
        boolean hasSpecialStates = false;
        if (playerData.getCombustionLevel() > 0 || playerData.isAbundanceActive()) {
            lore.add("§c🔥 §lÉTATS SPÉCIAUX");

            if (playerData.getCombustionLevel() > 0) {
                double multiplier = playerData.getCombustionMultiplier();
                lore.add("§7│ §cCombustion: §6x" + String.format("%.2f", multiplier) +
                        " §7(" + playerData.getCombustionLevel() + "/1000)");
                hasSpecialStates = true;
            }

            if (playerData.isAbundanceActive()) {
                lore.add("§7│ §6⭐ Abondance: §aACTIVE §7(x2 gains)");
                hasSpecialStates = true;
            }

            if (hasSpecialStates) {
                lore.add("§7└");
                lore.add("");
            }
        }

        // Enchantements triés par catégorie
        lore.add("§d✨ §lENCHANTEMENTS ACTIFS");
        var enchantments = playerData.getEnchantmentLevels();

        if (enchantments.isEmpty()) {
            lore.add("§7│ §7Aucun enchantement custom actif");
            lore.add("§7└ §7Utilisez §eclic droit §7pour en débloquer!");
        } else {
            // Trie les enchantements par catégorie
            Map<EnchantmentCategory, List<String>> enchantsByCategory = new HashMap<>();

            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                var enchant = plugin.getEnchantmentManager().getEnchantment(entry.getKey());
                if (enchant != null) {
                    EnchantmentCategory category = enchant.getCategory();
                    enchantsByCategory.computeIfAbsent(category, k -> new ArrayList<>());

                    String levelStr = entry.getValue() == Integer.MAX_VALUE ? "∞" :
                            NumberFormatter.format(entry.getValue());

                    // NOUVEAU: Indication si enchantement mobilité désactivé
                    String statusIndicator = "";
                    if (category == EnchantmentCategory.MOBILITY) {
                        boolean enabled = playerData.isMobilityEnchantmentEnabled(entry.getKey());
                        statusIndicator = enabled ? " §a✓" : " §c✗";
                    }

                    String enchantLine = "§8  ▸ §7" + enchant.getDisplayName() + " §a" + levelStr + statusIndicator;
                    enchantsByCategory.get(category).add(enchantLine);
                }
            }

            // Affiche par catégorie
            boolean first = true;
            for (EnchantmentCategory category : EnchantmentCategory.values()) {
                List<String> categoryEnchants = enchantsByCategory.get(category);
                if (categoryEnchants != null && !categoryEnchants.isEmpty()) {
                    if (!first) {
                        lore.add("§7│");
                    }

                    // En-tête de catégorie
                    lore.add("§7├─ " + category.getIcon() + " §l" + category.getDisplayName().toUpperCase());

                    // Enchantements de cette catégorie
                    for (String enchantLine : categoryEnchants) {
                        lore.add("§7│" + enchantLine);
                    }

                    first = false;
                }
            }

            lore.add("§7└ §eClic droit §7pour gérer les enchantements");
        }

        lore.add("");
        lore.add("§e⚡ §lFONCTIONNALITÉS");
        lore.add("§7│ §6Clic droit: §eMenu enchantements");
        lore.add("§7│ §6Shift+Clic droit: §eÉscalateur §7(si débloqué)");
        lore.add("§7│ §6Clic molette: §7Activer/désactiver mobilité");
        lore.add("§7│ §6Auto-mine: §7Uniquement dans les mines");
        lore.add("§7│ §6Protection: §cImpossible à perdre/jeter");
        lore.add("§7└ §6Indestructible: §7Ne se casse jamais");
        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§6✨ §lPioche Légendaire PrisonTycoon §6✨");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
    }

    // Méthodes de vérification (inchangées)

    /**
     * Vérifie si un item est une pioche légendaire
     */
    public boolean isLegendaryPickaxe(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_PICKAXE) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return meta.getPersistentDataContainer().has(legendaryPickaxeKey, PersistentDataType.BOOLEAN);
    }

    /**
     * Vérifie si une pioche appartient à un joueur spécifique
     */
    public boolean isOwner(ItemStack item, Player player) {
        if (!isLegendaryPickaxe(item)) return false;

        ItemMeta meta = item.getItemMeta();
        String ownerUUID = meta.getPersistentDataContainer().get(pickaxeOwnerKey, PersistentDataType.STRING);

        return player.getUniqueId().toString().equals(ownerUUID);
    }

    /**
     * Trouve la pioche légendaire d'un joueur dans son inventaire
     */
    public ItemStack findPlayerPickaxe(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLegendaryPickaxe(item) && isOwner(item, player)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Vérifie si un joueur a déjà une pioche légendaire
     */
    public boolean hasLegendaryPickaxe(Player player) {
        return findPlayerPickaxe(player) != null;
    }

    /**
     * Met à jour la pioche d'un joueur avec ses enchantements actuels
     */
    public void updatePlayerPickaxe(Player player) {
        ItemStack pickaxe = findPlayerPickaxe(player);
        if (pickaxe == null) return;

        ItemMeta meta = pickaxe.getItemMeta();
        if (meta == null) return;

        updatePickaxeLore(meta, player);
        pickaxe.setItemMeta(meta);
    }

    /**
     * Gère la durabilité de la pioche (ne se casse jamais)
     */
    public void handleDurability(ItemStack pickaxe, Player player) {
        if (!isLegendaryPickaxe(pickaxe)) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");

        // Calcule la durabilité bonus
        double durabilityMultiplier = 1.0 + (durabilityLevel * 0.1);
        int maxDurability = (int) (Material.NETHERITE_PICKAXE.getMaxDurability() * durabilityMultiplier);

        // Si la pioche est "cassée"
        if (pickaxe.getDurability() >= maxDurability) {
            player.sendMessage("§c⚠️ Votre pioche est endommagée! Réparez-la pour réactiver tous les enchantements.");
        }
    }

    /**
     * Répare complètement la pioche légendaire
     */
    public void repairPickaxe(ItemStack pickaxe) {
        if (isLegendaryPickaxe(pickaxe)) {
            pickaxe.setDurability((short) 0);
        }
    }

    /**
     * NOUVEAU: Met à jour les effets de mobilité selon les enchantements activés/désactivés
     */
    public void updateMobilityEffects(Player player) {
        // Vérifie d'abord si la pioche légendaire est en main
        ItemStack handItem = player.getInventory().getItemInMainHand();
        boolean hasPickaxeInHand = handItem != null && isLegendaryPickaxe(handItem) && isOwner(handItem, player);

        // Si pas de pioche en main, retire tous les effets
        if (!hasPickaxeInHand) {
            removeMobilityEffects(player);
            plugin.getPluginLogger().debug("Effets mobilité retirés pour " + player.getName() + " (pioche pas en main)");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Retire tous les effets d'abord
        removeMobilityEffects(player);

        // Applique seulement les effets activés ET si pioche en main

        // Vision nocturne
        if (playerData.getEnchantmentLevel("night_vision") > 0 &&
                playerData.isMobilityEnchantmentEnabled("night_vision")) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, true, false));
        }

        // Vitesse
        int speedLevel = playerData.getEnchantmentLevel("speed");
        if (speedLevel > 0 && playerData.isMobilityEnchantmentEnabled("speed")) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.SPEED,
                    Integer.MAX_VALUE, speedLevel - 1, true, false));
        }

        // Rapidité (Haste)
        int hasteLevel = playerData.getEnchantmentLevel("haste");
        if (hasteLevel > 0 && playerData.isMobilityEnchantmentEnabled("haste")) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HASTE,
                    Integer.MAX_VALUE, hasteLevel - 1, true, false));
        }

        // Saut
        int jumpLevel = playerData.getEnchantmentLevel("jump_boost");
        if (jumpLevel > 0 && playerData.isMobilityEnchantmentEnabled("jump_boost")) {
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.JUMP_BOOST,
                    Integer.MAX_VALUE, jumpLevel - 1, true, false));
        }

        plugin.getPluginLogger().debug("Effets mobilité mis à jour pour " + player.getName() +
                " (pioche en main: " + hasPickaxeInHand + ")");
    }

    /**
     * RENOMMÉ: Applique les effets de mobilité (utilise updateMobilityEffects)
     */
    public void applyMobilityEffects(Player player) {
        updateMobilityEffects(player);
    }

    /**
     * Retire les effets de mobilité
     */
    public void removeMobilityEffects(Player player) {
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.HASTE);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }

    /**
     * CORRIGÉ: Gère la téléportation Escalateur (maintenant dans mobilité)
     */
    public void handleEscalator(Player player) {
        // Vérifie que la pioche est en main
        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem == null || !isLegendaryPickaxe(handItem) || !isOwner(handItem, player)) {
            player.sendMessage("§c❌ Vous devez avoir la pioche légendaire en main!");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getEnchantmentLevel("escalator") > 0 &&
                playerData.isMobilityEnchantmentEnabled("escalator")) {
            Location location = player.getLocation();
            World world = location.getWorld();

            // Trouve la surface
            int surfaceY = world.getHighestBlockYAt(location);
            Location surfaceLocation = new Location(world, location.getX(), surfaceY + 1, location.getZ());

            // Vérifie qu'il y a 2 blocs d'air
            if (surfaceLocation.getBlock().getType() == Material.AIR &&
                    surfaceLocation.clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {

                player.teleport(surfaceLocation);
                player.sendMessage("§a✨ Escalateur activé! Téléportation vers la surface.");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            } else {
                player.sendMessage("§cImpossible de se téléporter: surface obstruée!");
            }
        } else if (playerData.getEnchantmentLevel("escalator") > 0) {
            player.sendMessage("§c❌ Escalateur désactivé! Utilisez le clic molette pour l'activer.");
        } else {
            player.sendMessage("§c❌ Escalateur non débloqué!");
        }
    }
}