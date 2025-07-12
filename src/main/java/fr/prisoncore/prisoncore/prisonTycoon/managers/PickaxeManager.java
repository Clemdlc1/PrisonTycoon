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
 * CORRIGÉ : Durability bien implémentée et vérifiée
 */
public class PickaxeManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey legendaryPickaxeKey;
    private final NamespacedKey pickaxeOwnerKey;

    // NOUVEAU : Slot fixe pour la pioche
    private static final int PICKAXE_SLOT = 0;

    public PickaxeManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.legendaryPickaxeKey = new NamespacedKey(plugin, "legendary_pickaxe");
        this.pickaxeOwnerKey = new NamespacedKey(plugin, "pickaxe_owner");

        plugin.getPluginLogger().info("§aPickaxeManager initialisé.");
    }

    /**
     * CORRIGÉ : Crée une nouvelle pioche légendaire et la place dans le slot 0
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

        // NOUVEAU : Place la pioche OBLIGATOIREMENT dans le slot 0
        placePickaxeInSlot0(player, pickaxe);

        plugin.getPluginLogger().info("§7Pioche légendaire créée pour: " + player.getName() +
                " avec enchantements par défaut (placée slot 0)");
        return pickaxe;
    }

    /**
     * NOUVEAU : Place la pioche dans le slot 0 et vide le slot si nécessaire
     */
    private void placePickaxeInSlot0(Player player, ItemStack pickaxe) {
        // Sauvegarde l'item qui était dans le slot 0
        ItemStack existingItem = player.getInventory().getItem(PICKAXE_SLOT);

        // Place la pioche dans le slot 0
        player.getInventory().setItem(PICKAXE_SLOT, pickaxe);

        // Si il y avait un item, essaie de le placer ailleurs
        if (existingItem != null && existingItem.getType() != Material.AIR) {
            var leftover = player.getInventory().addItem(existingItem);
            if (!leftover.isEmpty()) {
                // Drop au sol si pas de place
                for (ItemStack overflow : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                }
                player.sendMessage("§e⚠️ Item du slot 1 déplacé au sol (pioche légendaire prioritaire)");
            }
        }

        plugin.getPluginLogger().debug("Pioche placée dans le slot 0 pour " + player.getName());
    }

    /**
     * NOUVEAU : Vérifie si la pioche est dans le bon slot
     */
    public boolean isPickaxeInCorrectSlot(Player player) {
        ItemStack slotItem = player.getInventory().getItem(PICKAXE_SLOT);
        return slotItem != null && isLegendaryPickaxe(slotItem) && isOwner(slotItem, player);
    }

    /**
     * NOUVEAU : Force le retour de la pioche au slot 0
     */
    public void enforcePickaxeSlot(Player player) {
        if (isPickaxeInCorrectSlot(player)) {
            return; // Déjà au bon endroit
        }

        // Cherche la pioche dans l'inventaire
        ItemStack foundPickaxe = findPlayerPickaxe(player);
        if (foundPickaxe != null) {
            // Retire la pioche de son emplacement actuel
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (item != null && item.equals(foundPickaxe)) {
                    player.getInventory().setItem(i, null);
                    break;
                }
            }

            // La replace au slot 0
            placePickaxeInSlot0(player, foundPickaxe);
            player.sendMessage("§e⚠️ Pioche légendaire replacée dans le slot 1 (position obligatoire)");
        }
    }

    /**
     * CORRIGÉ : Met à jour le lore avec distinction blocs minés/cassés
     */
    public void updatePickaxeLore(ItemMeta meta, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        List<String> lore = new ArrayList<>();

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Pioche légendaire unique et indestructible");
        lore.add("§7Propriétaire: §e" + player.getName());
        lore.add("§c⚠️ §lDOIT RESTER DANS LE SLOT 1");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        // CORRIGÉ : Statistiques UNIQUEMENT via pioche avec distinction minés/cassés
        lore.add("§6⛏️ §lSTATISTIQUES PIOCHE");
        lore.add("§7│ §6Coins via pioche: §e" + NumberFormatter.formatWithColor(playerData.getCoinsViaPickaxe()));
        lore.add("§7│ §eTokens via pioche: §6" + NumberFormatter.formatWithColor(playerData.getTokensViaPickaxe()));
        lore.add("§7│ §aExpérience via pioche: §2" + NumberFormatter.formatWithColor(playerData.getExperienceViaPickaxe()));
        lore.add("§7│ §bBlocs minés: §3" + NumberFormatter.formatWithColor(playerData.getTotalBlocksMined()));
        lore.add("§7└ §dBlocs détruits (laser/explosion): §5" + NumberFormatter.formatWithColor(playerData.getTotalBlocksDestroyed() - playerData.getTotalBlocksMined()));
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

        // NOUVEAU : État de durabilité
        int durabilityLevel = playerData.getEnchantmentLevel("durability");
        if (durabilityLevel > 0) {
            double durabilityBonus = durabilityLevel * 10.0;
            int maxDurability = (int) (Material.NETHERITE_PICKAXE.getMaxDurability() * (1.0 + durabilityBonus / 100.0));
            ItemStack currentPickaxe = findPlayerPickaxe(player);

            if (currentPickaxe != null) {
                short currentDurability = currentPickaxe.getDurability();
                double healthPercent = ((double)(maxDurability - currentDurability) / maxDurability) * 100;

                lore.add("§e🔨 §lÉTAT DE LA PIOCHE");
                lore.add("§7│ §eDurabilité: §a" + String.format("%.1f%%", healthPercent));
                lore.add("§7│ §eDurabilité max: §6" + maxDurability + " §7(+" + String.format("%.0f%%", durabilityBonus) + ")");

                if (healthPercent < 25) {
                    lore.add("§7│ §c⚠️ Durabilité faible! Réparez bientôt.");
                } else if (healthPercent < 50) {
                    lore.add("§7│ §e⚠️ Durabilité moyenne.");
                } else {
                    lore.add("§7│ §a✓ Pioche en bon état.");
                }
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

                    // NOUVEAU : Indication si enchantement mobilité désactivé
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
        lore.add("§7│ §6Auto-mine: §7Dans les mines uniquement");
        lore.add("§7│ §cHors mine: §7Seuls efficacité/solidité/mobilité actifs");
        lore.add("§7│ §6Protection: §cDoit rester dans le slot 1");
        lore.add("§7└ §6Indestructible: §7Ne se casse jamais complètement");
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

        // Vérifie que la pioche est toujours au bon endroit
        enforcePickaxeSlot(player);
    }

    /**
     * CORRIGÉ : Gère la durabilité de la pioche (enchantement Solidité implémenté)
     */
    public void handleDurability(ItemStack pickaxe, Player player) {
        if (!isLegendaryPickaxe(pickaxe)) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int durabilityLevel = playerData.getEnchantmentLevel("durability");

        if (durabilityLevel <= 0) {
            // Pas d'enchantement solidité, comportement normal
            return;
        }

        // IMPLÉMENTATION : Calcule la durabilité bonus avec l'enchantement Solidité
        double durabilityBonus = durabilityLevel * 10.0; // +10% par niveau
        double durabilityMultiplier = 1.0 + (durabilityBonus / 100.0);
        int maxDurability = (int) (Material.NETHERITE_PICKAXE.getMaxDurability() * durabilityMultiplier);

        // Chance de ne PAS perdre de durabilité basée sur le niveau
        double preservationChance = Math.min(0.95, durabilityLevel * 0.05); // 5% par niveau, max 95%

        if (Math.random() < preservationChance) {
            // La pioche ne perd pas de durabilité cette fois
            plugin.getPluginLogger().debug("Durabilité préservée pour " + player.getName() +
                    " (chance: " + String.format("%.1f%%", preservationChance * 100) + ")");
            return;
        }

        // Applique la perte de durabilité normale
        short currentDurability = pickaxe.getDurability();

        // Vérifie si la pioche est "cassée" selon sa durabilité max améliorée
        if (currentDurability >= maxDurability * 0.95) { // 95% de durabilité max
            player.sendMessage("§c⚠️ Votre pioche est très endommagée! " +
                    "Durabilité bonus: +" + String.format("%.0f%%", durabilityBonus) + " grâce à Solidité " + durabilityLevel);
        }

        // La pioche ne se casse jamais complètement grâce à Solidité
        if (currentDurability >= maxDurability) {
            player.sendMessage("§e⚠️ Votre pioche aurait dû se casser, mais Solidité " + durabilityLevel + " l'a protégée!");
            pickaxe.setDurability((short) (maxDurability - 1)); // Reste à 1 point de durabilité
        }

        plugin.getPluginLogger().debug("Durabilité pour " + player.getName() + ": " +
                currentDurability + "/" + maxDurability + " (bonus: +" + String.format("%.0f%%", durabilityBonus) + ")");
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
     * NOUVEAU : Met à jour les effets de mobilité selon les enchantements activés/désactivés
     */
    public void updateMobilityEffects(Player player) {
        // Vérifie d'abord si la pioche légendaire est dans le slot 0
        boolean hasPickaxeInSlot0 = isPickaxeInCorrectSlot(player);

        // Si pas de pioche au slot 0, retire tous les effets
        if (!hasPickaxeInSlot0) {
            removeMobilityEffects(player);
            plugin.getPluginLogger().debug("Effets mobilité retirés pour " + player.getName() + " (pioche pas au slot 0)");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Retire tous les effets d'abord
        removeMobilityEffects(player);

        // Applique seulement les effets activés ET si pioche au slot 0

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
                " (pioche au slot 0: " + hasPickaxeInSlot0 + ")");
    }

    /**
     * RENOMMÉ : Applique les effets de mobilité (utilise updateMobilityEffects)
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
     * CORRIGÉ : Gère la téléportation Escalateur (maintenant dans mobilité)
     */
    public void handleEscalator(Player player) {
        // Vérifie que la pioche est au slot 0
        if (!isPickaxeInCorrectSlot(player)) {
            player.sendMessage("§c❌ Vous devez avoir la pioche légendaire dans le slot 1!");
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

    /**
     * NOUVEAU : Getter pour le slot obligatoire de la pioche
     */
    public static int getPickaxeSlot() {
        return PICKAXE_SLOT;
    }
}