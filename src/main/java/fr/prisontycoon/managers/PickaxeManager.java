package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.cristaux.Cristal;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.enchantments.EnchantmentCategory;
import fr.prisontycoon.utils.NumberFormatter;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Gestionnaire de la pioche légendaire
 * CORRIGÉ : Durability bien implémentée et vérifiée
 */
public class PickaxeManager {

    // NOUVEAU : Slot fixe pour la pioche
    private static final int PICKAXE_SLOT = 0;
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
     * Vérifie si la pioche du joueur est cassée
     */
    public static boolean isPickaxeBroken(Player player) {
        return player.hasMetadata("pickaxe_broken");
    }

    /**
     * Retourne le multiplicateur de pénalité (90% de malus = 10% d'efficacité)
     */
    public static double getPickaxePenaltyMultiplier(Player player) {
        return isPickaxeBroken(player) ? 0.10 : 1.0;
    }

    /**
     * CORRIGÉ : Crée une nouvelle pioche légendaire et la place dans le slot 0
     */
    public void createLegendaryPickaxe(Player player) {
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
            return;
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
     * CORRIGÉ : Met à jour le lore ET les enchantements vanilla de la pioche
     */
    public void updatePickaxeLore(ItemMeta meta, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // NOUVEAU : Applique les enchantements vanilla selon les enchantements custom
        applyVanillaEnchantments(meta, playerData);

        List<String> lore = new ArrayList<>();

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Pioche légendaire");
        lore.add("§7Propriétaire: §e" + player.getName());
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("");

        // CORRIGÉ : Statistiques UNIQUEMENT via pioche avec distinction minés/cassés
        lore.add("§6⛏ §lSTATISTIQUES PIOCHE");
        lore.add("§7│ §6Coins : §e" + NumberFormatter.formatWithColor(playerData.getCoinsViaPickaxe()));
        lore.add("§7│ §eTokens : §6" + NumberFormatter.formatWithColor(playerData.getTokensViaPickaxe()));
        lore.add("§7│ §aExpérience : §2" + NumberFormatter.formatWithColor(playerData.getExperienceViaPickaxe()));
        lore.add("§7│ §bBlocs minés: §3" + NumberFormatter.formatWithColor(playerData.getTotalBlocksMined()));
        lore.add("§7└ §dBlocs détruits : §5" + NumberFormatter.formatWithColor(playerData.getTotalBlocksDestroyed()));
        lore.add("");

        ItemStack currentPickaxe = findPlayerPickaxe(player);

        // Valeurs par défaut si la pioche n'existe pas encore (création en cours)
        short currentDurability = 0;
        short maxDurability = Material.NETHERITE_PICKAXE.getMaxDurability();
        boolean isBroken = false;

        // NOUVEAU: Vérification null pour éviter la NullPointerException
        if (currentPickaxe != null) {
            currentDurability = currentPickaxe.getDurability();
            maxDurability = currentPickaxe.getType().getMaxDurability();
            isBroken = currentDurability >= maxDurability - 1;
        }

        // CORRIGÉ : Utilise la durabilité de base (pas augmentée par solidité)
        double healthPercent = ((double) (maxDurability - currentDurability) / maxDurability) * 100;
        int currentHealth = maxDurability - currentDurability;

        if (isBroken) {
            // PIOCHE CASSÉE - Affichage spécial
            lore.add("§c💀 §l§nPIOCHE CASSÉE§r");
            lore.add("§7│ §cDurabilité: §4§l0.0%§r §7(CASSÉE)");
            lore.add("§7│ §cPoints: §40§7/§6" + maxDurability);
            lore.add("§7│ §c§l⚠️ TOUS LES ENCHANTEMENTS DÉSACTIVÉS§r");
            lore.add("§7│ §c➤ Token Greed fonctionne avec 90% de malus");
            lore.add("§7│ §e➤ Réparez immédiatement votre pioche!");

            // Barre de durabilité cassée
            String brokenBar = "§c▓▓▓▓▓▓▓▓▓▓";
            lore.add("§7│ " + brokenBar + " §c§l(CASSÉE)");
            lore.add("");

        } else {
            // PIOCHE NORMALE - Affichage avec durabilité de base
            lore.add("§c🔨 §lÉTAT DE LA PIOCHE");
            lore.add("§7│ §eDurabilité: " + getDurabilityColor(healthPercent) + String.format("%.1f%%", healthPercent));
            lore.add("§7│ §ePoints: §6" + currentHealth + "§7/§6" + maxDurability);

            // CORRIGÉ : Affichage solidité sans bonus de durabilité max
            int durabilityLevel = playerData.getEnchantmentLevel("durability");
            if (durabilityLevel > 0) {
                double preservationChance = Math.min(95.0, durabilityLevel * 5.0);
                lore.add("§7│ §eSolidité: §a" + String.format("%.0f%%", preservationChance) +
                        " §7chance d'éviter perte (Niv." + durabilityLevel + ")");
            }

            // Indicateur visuel avec barre de durabilité
            String durabilityBar = createDurabilityBar(healthPercent);
            lore.add("§7│ " + durabilityBar);
            lore.add("");
        }

        lore.add("§d✨ §lENCHANTEMENTS ACTIFS");
        var enchantments = playerData.getEnchantmentLevels();

        if (enchantments.isEmpty()) {
            lore.add("§7│ §7Aucun enchantement custom actif");
            lore.add("§7└ §7Utilisez §eclic droit §7pour en débloquer!");
        } else {
            // CORRIGÉ : Vérification si pioche cassée pour l'affichage
            isBroken = false;
            currentPickaxe = findPlayerPickaxe(player);
            if (currentPickaxe != null) {
                currentDurability = currentPickaxe.getDurability();
                maxDurability = currentPickaxe.getType().getMaxDurability();
                isBroken = currentDurability >= maxDurability - 1;
            }

            if (isBroken) {
                lore.add("§7│ §c§l⚠ TOUS DÉSACTIVÉS (pioche cassée)§r");
                lore.add("§7│ §7Réparez pour les réactiver");
            }

            // Trie les enchantements par catégorie
            Map<EnchantmentCategory, List<String>> enchantsByCategory = new HashMap<>();

            for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
                var enchant = plugin.getEnchantmentManager().getEnchantment(entry.getKey());
                if (enchant != null) {
                    EnchantmentCategory category = enchant.getCategory();
                    enchantsByCategory.computeIfAbsent(category, k -> new ArrayList<>());

                    // CORRIGÉ : Indication si enchantement désactivé (pioche cassée ou mobilité désactivée)
                    String statusIndicator;
                    String statusColor = "§a"; // Vert par défaut

                    if (isBroken) {
                        // Pioche cassée - tous désactivés sauf token greed avec malus
                        if (entry.getKey().equals("token_greed")) {
                            statusIndicator = " §c(90% malus)";
                            statusColor = "§6"; // Orange pour indiquer le malus
                        } else {
                            statusIndicator = "";
                            statusColor = "§8"; // Gris pour désactivé
                        }
                    } else if (category == EnchantmentCategory.MOBILITY) {
                        boolean enabled = playerData.isMobilityEnchantmentEnabled(entry.getKey());
                        statusIndicator = enabled ? " §a✓" : " §c✗";
                        statusColor = enabled ? "§a" : "§8";
                    } else {
                        statusIndicator = " §a✓"; // Actif normalement
                    }

                    String displayText = statusColor + enchant.getDisplayName() + " " +
                            NumberFormatter.formatRoman(entry.getValue()) + statusIndicator;

                    enchantsByCategory.get(category).add(displayText);
                }
            }

            // Affichage par catégorie
            for (EnchantmentCategory category : EnchantmentCategory.values()) {
                List<String> categoryEnchants = enchantsByCategory.get(category);
                if (categoryEnchants != null && !categoryEnchants.isEmpty()) {
                    lore.add("§7│ " + category.getDisplayName() + ":");
                    for (String enchantText : categoryEnchants) {
                        lore.add("§7│  §8• " + enchantText);
                    }
                }
            }
        }

        if (!isBroken) {
            // CORRIGÉ : Utilise le nouveau système pour récupérer les enchantements actifs
            Set<String> activeBooks = plugin.getEnchantmentBookManager().getActiveEnchantments(player);

            if (!activeBooks.isEmpty()) {
                lore.add("§7│ §5UNIQUES ACTIFS §l⚡ §l:");
                for (String bookId : activeBooks) {
                    EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(bookId);
                    if (book != null) {
                        // CORRIGÉ : Utilise la nouvelle méthode pour récupérer le niveau
                        int level = plugin.getEnchantmentBookManager().getEnchantmentBookLevel(player, bookId);
                        String levelDisplay = level > 1 ? " §7(Niv." + level + ")" : "";
                        lore.add("§7│ §d" + book.getName() + levelDisplay + " §a✓");
                    }
                }
            }

            // NOUVEAU : Affichage des livres possédés mais non actifs
            Map<String, Integer> allBooks = plugin.getEnchantmentBookManager().getAllEnchantmentBooksWithLevels(player);
            Set<String> inactiveBooks = new HashSet<>();

            for (Map.Entry<String, Integer> entry : allBooks.entrySet()) {
                String bookId = entry.getKey();
                if (!activeBooks.contains(bookId)) {
                    inactiveBooks.add(bookId);
                }
            }

            if (!inactiveBooks.isEmpty()) {
                lore.add("§7│ §8UNIQUES INACTIFS:");
                for (String bookId : inactiveBooks) {
                    EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(bookId);
                    if (book != null) {
                        int level = allBooks.get(bookId);
                        String levelDisplay = level > 1 ? " §7(Niv." + level + ")" : "";
                        lore.add("§7│ §8" + book.getName() + levelDisplay + " §c✗");
                    }
                }
            }
        }

        lore.add("§7└ §7Clic droit pour gérer vos enchantements");
        List<Cristal> cristals = plugin.getCristalManager().getPickaxeCristals(player);
        if (!cristals.isEmpty()) {
            lore.add("§d✨ Cristaux Appliqués §8(" + cristals.size() + "/4)§d:");
            for (Cristal cristal : cristals) {
                lore.add("§8• §d" + cristal.getType().getDisplayName() + " " + cristal.getNiveau() +
                        " §8- §a" + cristal.getType().getBonusDescription(cristal.getNiveau()));
            }
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        lore.add("");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§6✨ §lPrisonTycoon §6✨");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
    }

    /**
     * NOUVEAU : Applique les enchantements vanilla selon les enchantements custom
     */
    private void applyVanillaEnchantments(ItemMeta meta, PlayerData playerData) {
        for (Enchantment ench : meta.getEnchants().keySet()) {
            if (ench != Enchantment.UNBREAKING) { // Garde Unbreaking pour l'effet glowing
                meta.removeEnchant(ench);
            }
        }

        // Applique Efficacité selon le niveau custom
        int efficiencyLevel = playerData.getEnchantmentLevel("efficiency");
        Player player = plugin.getServer().getPlayer(playerData.getPlayerId());
        if (efficiencyLevel > 0) {
            if (!isPickaxeBroken(player)) {
                meta.addEnchant(Enchantment.EFFICIENCY, efficiencyLevel, true);
            } else {
                meta.addEnchant(Enchantment.EFFICIENCY, 3, true);

            }
        }
    }

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
        // D'abord, vérifie le slot dédié pour éviter de parcourir tout l'inventaire
        ItemStack slotItem = player.getInventory().getItem(PICKAXE_SLOT);
        if (slotItem != null && isLegendaryPickaxe(slotItem) && isOwner(slotItem, player)) {
            return slotItem;
        }

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
     * NOUVEAU : Met à jour les effets de mobilité selon les enchantements activés/désactivés
     */
    public void updateMobilityEffects(Player player) {

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!plugin.getPickaxeManager().isLegendaryPickaxe(handItem) || !isPickaxeInCorrectSlot(player) || isPickaxeBroken(player)) {
            removeMobilityEffects(player);
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // Retire tous les effets d'abord
        removeMobilityEffects(player);

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

        // Célérité (Haste)
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

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (!plugin.getPickaxeManager().isLegendaryPickaxe(handItem) || !isPickaxeInCorrectSlot(player)) {
            player.sendMessage("§c❌ Vous devez avoir la pioche légendaire en main!");
            return;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        // NOUVEAU : Vérifie que la pioche n'est pas cassée
        if (isPickaxeBroken(player) && playerData.getEnchantmentLevel("escalator") > 0) {
            player.sendMessage("§c❌ Votre pioche est trop endommagée pour utiliser Escalateur! Réparez-la d'abord.");
            return;
        }

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
        }
    }

    /**
     * CORRIGÉ : Crée la barre de durabilité visuelle
     */
    private String createDurabilityBar(double healthPercent) {
        StringBuilder bar = new StringBuilder();
        int totalBars = 10;
        int filledBars = (int) Math.round((healthPercent / 100.0) * totalBars);

        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                if (healthPercent >= 70) {
                    bar.append("§a▓");
                } else if (healthPercent >= 40) {
                    bar.append("§e▓");
                } else if (healthPercent >= 20) {
                    bar.append("§6▓");
                } else {
                    bar.append("§c▓");
                }
            } else {
                bar.append("§8▓");
            }
        }

        return bar.toString();
    }

    /**
     * Vérifie l'état de la pioche légendaire et affiche les notifications appropriées
     */
    public void checkLegendaryPickaxeState(Player player, ItemStack pickaxe, short currentDurability, short maxDurability) {
        double durabilityPercent = 1.0 - ((double) currentDurability / maxDurability);
        if (plugin.getEnchantmentBookManager().isEnchantmentActive(player, "incassable")) {
            deactivateBrokenPickaxeMode(player);
        }
        // PIOCHE CASSÉE (100% utilisée)
        if (currentDurability >= maxDurability - 1) {
            if (!isPickaxeBroken(player)) {
                activateBrokenPickaxeMode(player);

                // Message spécial pour pioche cassée (une seule fois)
                if (!player.hasMetadata("durability_notif_broken")) {
                    TextComponent message = new TextComponent("§c💀 PIOCHE CASSÉE! Tous enchantements désactivés! §e[RÉPARER IMMÉDIATEMENT]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§cRéparation critique requise!")));
                    player.spigot().sendMessage(message);

                    player.setMetadata("durability_notif_broken", new FixedMetadataValue(plugin, true));
                }
            }
        } else {
            // Désactive le mode "pioche cassée" si il était actif
            if (isPickaxeBroken(player)) {
                deactivateBrokenPickaxeMode(player);
                player.removeMetadata("durability_notif_broken", plugin);
            }

            // NOTIFICATIONS PAR SEUILS (une seule fois par niveau)
            if (durabilityPercent <= 0.10) { // Moins de 10% restant
                if (!player.hasMetadata("durability_notif_10")) {
                    TextComponent message = new TextComponent("§6⚠️ Votre pioche est très endommagée ! §e[CLIQUEZ POUR RÉPARER]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aOuvrir le menu de réparation")));
                    player.spigot().sendMessage(message);

                    player.setMetadata("durability_notif_10", new FixedMetadataValue(plugin, true));
                }
            } else if (durabilityPercent <= 0.25) { // Moins de 25% restant
                if (!player.hasMetadata("durability_notif_25")) {
                    TextComponent message = new TextComponent("§e⚠️ Votre pioche commence à être endommagée. §e[RÉPARER]");
                    message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/repair"));
                    message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aOuvrir le menu de réparation")));
                    player.spigot().sendMessage(message);

                    player.setMetadata("durability_notif_25", new FixedMetadataValue(plugin, true));
                }
            }
        }
    }

    /**
     * Active le mode "pioche cassée"
     */
    private void activateBrokenPickaxeMode(Player player) {
        player.setMetadata("pickaxe_broken", new FixedMetadataValue(plugin, true));
        player.setMetadata("pickaxe_just_broken", new FixedMetadataValue(plugin, System.currentTimeMillis()));
        plugin.getEnchantmentManager().forceDisableAbundanceAndResetCombustion(player);
        updatePlayerPickaxe(player);
        updateMobilityEffects(player);
        plugin.getPluginLogger().debug("Mode pioche cassée activé pour " + player.getName());
    }

    /**
     * Désactive le mode "pioche cassée"
     */
    public void deactivateBrokenPickaxeMode(Player player) {
        player.removeMetadata("pickaxe_broken", plugin);
        player.setMetadata("pickaxe_just_repaired", new FixedMetadataValue(plugin, System.currentTimeMillis()));
        updatePlayerPickaxe(player);
        updateMobilityEffects(player);
        plugin.getPluginLogger().debug("Mode pioche cassée désactivé pour " + player.getName());
    }

    /**
     * CORRIGÉ : Couleur selon la durabilité
     */
    private String getDurabilityColor(double healthPercent) {
        if (healthPercent >= 80) return "§a";
        if (healthPercent >= 60) return "§e";
        if (healthPercent >= 40) return "§6";
        if (healthPercent >= 20) return "§c";
        return "§4";
    }

    public ItemStack getPlayerPickaxe(Player player) {
        // Vérifie d'abord le slot réservé (0)
        ItemStack slotItem = player.getInventory().getItem(PICKAXE_SLOT);
        if (slotItem != null && isLegendaryPickaxe(slotItem) && isOwner(slotItem, player)) {
            return slotItem;
        }
        // Puis la main principale (utile pendant certains mouvements d'inventaire)
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isLegendaryPickaxe(mainHand) && isOwner(mainHand, player)) {
            return mainHand;
        }
        // Fallback: parcourt l'inventaire
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLegendaryPickaxe(item) && isOwner(item, player)) {
                return item;
            }
        }
        return null;
    }
}