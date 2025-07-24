package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.GlobalBonusManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique pour la condensation, le carburant et le stockage
 */
public class AutominerCondHeadGUI {

    private final PrisonTycoon plugin;

    public AutominerCondHeadGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    // ==================== MENU CONDENSATION ====================

    /**
     * Ouvre le menu de condensation
     */
    public void openCondensationMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "§d🔧 Condensation d'Automineurs");
        player.openInventory(inv);
    }

    /**
     * Gère la fermeture du menu de condensation
     */
    public void handleCondensationClose(Player player, Inventory inventory) {
        List<ItemStack> autominers = new ArrayList<>();

        // Collecter les 9 slots
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !item.getType().isAir()) {
                autominers.add(item);
            }
        }

        if (autominers.size() != 9) {
            returnItemsToPlayer(player, autominers);
            reopenMainMenu(player);
            return;
        }

        // Vérifier que tous sont des automineurs du même type
        AutominerType firstType = plugin.getAutominerManager().getAutominerType(autominers.get(0));
        if (firstType == null) {
            player.sendMessage("§cLe premier item n'est pas un automineur!");
            returnItemsToPlayer(player, autominers);
            reopenMainMenu(player);
            return;
        }

        for (ItemStack autominer : autominers) {
            AutominerType type = plugin.getAutominerManager().getAutominerType(autominer);
            if (type != firstType) {
                player.sendMessage("§cTous les automineurs doivent être du même type!");
                returnItemsToPlayer(player, autominers);
                reopenMainMenu(player);
                return;
            }
        }

        // Vérifier que le type peut être condensé
        if (!firstType.canBeCondensed()) {
            player.sendMessage("§cLes automineurs " + firstType.getDisplayName() + " ne peuvent pas être condensés!");
            returnItemsToPlayer(player, autominers);
            reopenMainMenu(player);
            return;
        }

        // Effectuer la condensation
        AutominerType nextType = firstType.getNextTier();
        ItemStack newAutominer = plugin.getAutominerManager().createAutominer(nextType);

        // Donner le nouvel automineur
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItemNaturally(player.getLocation(), newAutominer);
            player.sendMessage("§eVotre inventaire était plein, l'automineur a été jeté au sol!");
        } else {
            player.getInventory().addItem(newAutominer);
        }

        player.sendMessage("§a✓ Condensation réussie!");
        player.sendMessage("§79x " + firstType.getDisplayName() + " → 1x " + nextType.getDisplayName());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        plugin.getPluginLogger().info("§7" + player.getName() + " a condensé 9x " + firstType.getDisplayName() +
                " en 1x " + nextType.getDisplayName());

        reopenMainMenu(player);
    }

    // ==================== MENU CARBURANT ====================

    /**
     * Ouvre le menu de carburant
     */
    public void openFuelMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "§c⛽ Ajout de Carburant");
        player.openInventory(inv);
    }

    /**
     * Gère la fermeture du menu de carburant
     */
    public void handleFuelClose(Player player, Inventory inventory) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        List<ItemStack> invalidItems = new ArrayList<>();
        double totalFuelAdded = 0;

        // Traiter chaque item dans l'inventaire
        for (int i = 0; i < 9; i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            if (isValidFuel(item)) {
                double fuelValue = getFuelValue(item);
                totalFuelAdded += fuelValue * item.getAmount();
            } else {
                invalidItems.add(item);
            }
        }

        // Ajouter le carburant au réservoir
        if (totalFuelAdded > 0) {
            playerData.setAutominerFuelReserve(playerData.getAutominerFuelReserve() + totalFuelAdded);
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());

            player.sendMessage("§a✓ " + NumberFormatter.format((long)totalFuelAdded) + " têtes ajoutées au réservoir!");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }

        // Retourner les items invalides
        returnItemsToPlayer(player, invalidItems);

        reopenMainMenu(player);
    }

    // ==================== MENU STOCKAGE ====================

    /**
     * Ouvre le menu de stockage
     */
    public void openStorageMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Map<Material, Long> storage = playerData.getAutominerStorageContents();

        int size = Math.min(54, Math.max(27, ((storage.size() + 8) / 9) * 9 + 9));
        Inventory inv = Bukkit.createInventory(null, size, "§6📦 Stockage Automineur");

        // Afficher les items stockés
        int slot = 0;
        for (Map.Entry<Material, Long> entry : storage.entrySet()) {
            if (slot >= size - 9) break; // Garder la dernière ligne pour les boutons

            ItemStack displayItem = new ItemStack(entry.getKey());
            ItemMeta meta = displayItem.getItemMeta();

            String materialName = formatMaterialName(entry.getKey());
            meta.setDisplayName("§f" + materialName);

            List<String> lore = new ArrayList<>();
            lore.add("§7Quantité: §e" + NumberFormatter.format(entry.getValue()));

            // === CORRECTION : Afficher le prix de vente avec nouvelle logique ===
            long sellPrice = plugin.getConfigManager().getSellPrice(entry.getKey());
            long blockCoins = 0;
            long blockTokens = 0;

            var blockValue = plugin.getConfigManager().getBlockValue(entry.getKey());
            if (blockValue != null) {
                blockCoins = blockValue.getCoins();
                blockTokens = blockValue.getTokens();
            }

            // Utiliser sellPrice en priorité, sinon blockValue, sinon défaut
            long finalPrice = sellPrice > 0 ? sellPrice :
                    (blockCoins > 0 ? blockCoins :
                            (blockTokens > 0 ? blockTokens :
                                    getDefaultSellPrice(entry.getKey())));

            if (finalPrice > 0) {
                long totalValue = finalPrice * entry.getValue();
                lore.add("§7Prix unitaire: §a" + NumberFormatter.format(finalPrice) + "§7$");
                lore.add("§7Valeur totale: §a" + NumberFormatter.format(totalValue) + "§7$");
            } else {
                lore.add("§cPas de valeur de vente");
            }

            meta.setLore(lore);
            displayItem.setItemMeta(meta);

            inv.setItem(slot++, displayItem);
        }

        // Boutons dans la dernière ligne
        int lastRow = size - 9;

        // Bouton améliorer stockage
        ItemStack upgrade = new ItemStack(Material.ENDER_CHEST);
        ItemMeta upgradeMeta = upgrade.getItemMeta();
        upgradeMeta.setDisplayName("§bAméliorer le Stockage");

        long currentCapacity = 1000L + (playerData.getAutominerStorageLevel() * 500L);
        long nextCapacity = currentCapacity + 500L;
        int upgradeCost = (playerData.getAutominerStorageLevel() + 1) * 2; // 2, 4, 6, 8... beacons

        upgradeMeta.setLore(Arrays.asList(
                "§7Niveau actuel: §f" + playerData.getAutominerStorageLevel(),
                "§7Capacité actuelle: §f" + NumberFormatter.format(currentCapacity),
                "§7Prochaine capacité: §f" + NumberFormatter.format(nextCapacity),
                "",
                "§7Coût: §e" + upgradeCost + " beacons",
                "",
                "§eClic: §7Améliorer"
        ));
        upgrade.setItemMeta(upgradeMeta);
        inv.setItem(lastRow + 1, upgrade);

        // Bouton récupérer gains (clés + greed + beacons)
        ItemStack gains = new ItemStack(Material.CHEST_MINECART);
        ItemMeta gainsMeta = gains.getItemMeta();
        gainsMeta.setDisplayName("§a💰 Récupérer les Gains");

        Map<String, Integer> storedKeys = playerData.getAutominerStoredKeys();
        List<String> gainsLore = new ArrayList<>();

        // Gains en attente
        long pendingCoins = playerData.getAutominerPendingCoins();
        long pendingTokens = playerData.getAutominerPendingTokens();
        long pendingExperience = playerData.getAutominerPendingExperience();
        long pendingBeacons = playerData.getAutominerPendingBeacons();

        gainsLore.add("§7Récupérer tous les gains d'automineur:");
        gainsLore.add("");

        // Clés stockées
        gainsLore.add("§d🗝️ Clés stockées:");
        if (storedKeys.isEmpty()) {
            gainsLore.add("§8Aucune clé stockée");
        } else {
            for (Map.Entry<String, Integer> entry : storedKeys.entrySet()) {
                gainsLore.add("§8• §f" + entry.getKey() + ": §e" + entry.getValue());
            }
        }

        gainsLore.add("");
        gainsLore.add("§a💚 Gains Greed en attente:");
        gainsLore.add("§8• §6Coins: §e" + NumberFormatter.format(pendingCoins));
        gainsLore.add("§8• §eTokens: §e" + NumberFormatter.format(pendingTokens));
        gainsLore.add("§8• §aExpérience: §e" + NumberFormatter.format(pendingExperience));
        gainsLore.add("§8• §bBeacons: §e" + NumberFormatter.format(pendingBeacons));

        gainsLore.add("");
        gainsLore.add("§eClic: §7Récupérer tous les gains");
        gainsMeta.setLore(gainsLore);
        gains.setItemMeta(gainsMeta);
        inv.setItem(lastRow + 4, gains);

        // Bouton vendre tout
        ItemStack sellAll = new ItemStack(Material.EMERALD);
        ItemMeta sellAllMeta = sellAll.getItemMeta();
        sellAllMeta.setDisplayName("§aVendre Tout");

        long totalSellValue = calculateTotalSellValue(storage);
        String valueDisplay = totalSellValue > 0 ?
                "§a" + NumberFormatter.format(totalSellValue) : "§c0";

        sellAllMeta.setLore(Arrays.asList(
                "§7Vendre tous les minerais stockés",
                "§7Valeur totale: " + valueDisplay + "§7$ (+ tokens)",
                "",
                storage.isEmpty() ? "§cAucun minerai à vendre" : "§eClic: §7Vendre tout"
        ));
        sellAll.setItemMeta(sellAllMeta);
        inv.setItem(lastRow + 7, sellAll);

        // Bouton retour
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cRetour");
        backMeta.setLore(Arrays.asList("§7Retourner au menu principal"));
        back.setItemMeta(backMeta);
        inv.setItem(lastRow + 8, back);

        player.openInventory(inv);
    }

    /**
     * Gère les clics dans le menu de stockage
     */
    public void handleStorageClick(Player player, int slot, ItemStack clickedItem) {
        if (clickedItem == null) return;

        String displayName = clickedItem.getItemMeta().getDisplayName();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (displayName.contains("Améliorer le Stockage")) {
            handleStorageUpgrade(player, playerData);
        } else if (displayName.contains("Récupérer les Gains")) {
            handleGainsRetrieval(player, playerData);
        } else if (displayName.contains("Vendre Tout")) {
            handleSellAll(player, playerData);
        } else if (displayName.contains("Retour")) {
            plugin.getAutominerGUI().openMainMenu(player);
        }
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    private void returnItemsToPlayer(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(item);
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
                player.sendMessage("§eVotre inventaire était plein, certains items ont été jetés au sol!");
            }
        }
    }

    private boolean isValidFuel(ItemStack item) {
        return item.getType() == Material.PLAYER_HEAD ||
                item.getType() == Material.SKELETON_SKULL ||
                item.getType() == Material.ZOMBIE_HEAD ||
                item.getType() == Material.CREEPER_HEAD ||
                item.getType() == Material.WITHER_SKELETON_SKULL;
    }

    private double getFuelValue(ItemStack item) {
        return switch (item.getType()) {
            case PLAYER_HEAD -> 1.0;
            case WITHER_SKELETON_SKULL -> 2.0;
            default -> 0.5; // Autres têtes de monstres
        };
    }

    private long calculateTotalSellValue(Map<Material, Long> storage) {
        long total = 0;
        for (Map.Entry<Material, Long> entry : storage.entrySet()) {
            Material material = entry.getKey();
            long quantity = entry.getValue();

            // === CORRECTION : Utiliser getSellPrice ET getBlockValue ===
            long itemValue = 0;

            // Essayer d'abord getSellPrice
            long sellPrice = plugin.getConfigManager().getSellPrice(material);
            if (sellPrice > 0) {
                itemValue = sellPrice;
            } else {
                // Essayer getBlockValue
                var blockValue = plugin.getConfigManager().getBlockValue(material);
                if (blockValue != null) {
                    itemValue = Math.max(blockValue.getCoins(), blockValue.getTokens());
                }
            }

            // Si toujours pas de valeur, utiliser des valeurs par défaut
            if (itemValue == 0) {
                itemValue = getDefaultSellPrice(material);
            }

            if (itemValue > 0) {
                total += itemValue * quantity;
            }
        }
        return total;
    }

    private long getDefaultSellPrice(Material material) {
        return switch (material) {
            case STONE, COBBLESTONE -> 1L;
            case COAL_ORE -> 5L;
            case IRON_ORE -> 10L;
            case GOLD_ORE -> 25L;
            case DIAMOND_ORE -> 100L;
            case EMERALD_ORE -> 150L;
            case REDSTONE_ORE -> 15L;
            case LAPIS_ORE -> 20L;
            case ANCIENT_DEBRIS -> 500L;
            case NETHERITE_BLOCK -> 1000L;
            case BEACON -> 2000L;
            default -> 1L; // Prix minimal pour tout autre bloc
        };
    }

    private String formatMaterialName(Material material) {
        return switch (material) {
            case COAL_ORE -> "Minerai de Charbon";
            case IRON_ORE -> "Minerai de Fer";
            case GOLD_ORE -> "Minerai d'Or";
            case DIAMOND_ORE -> "Minerai de Diamant";
            case EMERALD_ORE -> "Minerai d'Émeraude";
            case REDSTONE_ORE -> "Minerai de Redstone";
            case LAPIS_ORE -> "Minerai de Lapis-Lazuli";
            case ANCIENT_DEBRIS -> "Débris Antiques";
            case NETHERITE_BLOCK -> "Bloc de Netherite";
            case BEACON -> "Beacon";
            case STONE -> "Pierre";
            case COBBLESTONE -> "Pierre Taillée";
            default -> {
                String name = material.name().toLowerCase().replace("_", " ");
                String[] words = name.split(" ");
                StringBuilder formatted = new StringBuilder();
                for (String word : words) {
                    if (!word.isEmpty()) {
                        formatted.append(Character.toUpperCase(word.charAt(0)))
                                .append(word.substring(1))
                                .append(" ");
                    }
                }
                yield formatted.toString().trim();
            }
        };
    }

    private void handleStorageUpgrade(Player player, PlayerData playerData) {
        int upgradeCost = (playerData.getAutominerStorageLevel() + 1) * 2;

        if (playerData.getBeacons() < upgradeCost) {
            player.sendMessage("§cVous n'avez pas assez de beacons! Coût: " + upgradeCost);
            return;
        }

        playerData.removeBeacon(upgradeCost);
        playerData.setAutominerStorageLevel(playerData.getAutominerStorageLevel() + 1);

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✓ Stockage amélioré au niveau " + playerData.getAutominerStorageLevel() + "!");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        openStorageMenu(player); // Rafraîchir le menu
    }

    private void handleGainsRetrieval(Player player, PlayerData playerData) {
        // Récupérer les clés
        Map<String, Integer> storedKeys = playerData.getAutominerStoredKeys();

        // Récupérer les gains greed en attente
        long pendingCoins = playerData.claimAutominerPendingCoins();
        long pendingTokens = playerData.claimAutominerPendingTokens();
        long pendingExperience = playerData.claimAutominerPendingExperience();
        long pendingBeacons = playerData.claimAutominerPendingBeacons();

        boolean hasGains = false;
        List<String> messages = new ArrayList<>();

        // Donner les clés
        if (!storedKeys.isEmpty()) {
            int totalKeys = 0;
            for (Map.Entry<String, Integer> entry : storedKeys.entrySet()) {
                String keyType = entry.getKey();
                int quantity = entry.getValue();

                ItemStack key = plugin.getAutominerManager().createKey(keyType);
                key.setAmount(Math.min(quantity, 64)); // Max 64 par stack

                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(key);
                    totalKeys += quantity;
                } else {
                    player.sendMessage("§cInventaire plein! Libérez de l'espace pour récupérer vos gains.");
                    // Remettre les gains en attente
                    playerData.addAutominerPendingCoins(pendingCoins);
                    playerData.addAutominerPendingTokens(pendingTokens);
                    playerData.addAutominerPendingExperience(pendingExperience);
                    playerData.addAutominerPendingBeacons(pendingBeacons);
                    return;
                }
            }

            if (totalKeys > 0) {
                storedKeys.clear();
                playerData.setAutominerStoredKeys(storedKeys);
                messages.add("§d🗝️ " + totalKeys + " clés récupérées!");
                hasGains = true;
            }
        }

        // Donner les gains économiques
        if (pendingCoins > 0) {
            playerData.addCoins(pendingCoins);
            messages.add("§6💰 +" + NumberFormatter.format(pendingCoins) + " coins");
            hasGains = true;
        }

        if (pendingTokens > 0) {
            playerData.addTokens(pendingTokens);
            messages.add("§e🪙 +" + NumberFormatter.format(pendingTokens) + " tokens");
            hasGains = true;
        }

        if (pendingExperience > 0) {
            playerData.addExperience(pendingExperience);
            messages.add("§a⭐ +" + NumberFormatter.format(pendingExperience) + " expérience");
            hasGains = true;
        }

        if (pendingBeacons > 0) {
            playerData.addBeacons(pendingBeacons);
            messages.add("§b🔆 +" + NumberFormatter.format(pendingBeacons) + " beacons");
            hasGains = true;
        }

        if (hasGains) {
            plugin.getPlayerDataManager().markDirty(player.getUniqueId());

            player.sendMessage("§a✓ Gains d'automineur récupérés:");
            for (String message : messages) {
                player.sendMessage("  " + message);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.sendMessage("§cAucun gain à récupérer!");
        }

        openStorageMenu(player); // Rafraîchir le menu
    }

    private void handleSellAll(Player player, PlayerData playerData) {
        Map<Material, Long> storage = playerData.getAutominerStorageContents();

        if (storage.isEmpty()) {
            player.sendMessage("§cAucun minerai à vendre!");
            return;
        }

        long totalCoinsEarnings = 0;
        long totalTokensEarnings = 0;
        int itemsSold = 0;

        for (Map.Entry<Material, Long> entry : storage.entrySet()) {
            Material material = entry.getKey();
            long quantity = entry.getValue();

            // === CORRECTION : Utiliser getSellPrice ET getBlockValue ===
            long coinsValue = 0;
            long tokensValue = 0;

            // Essayer d'abord getSellPrice pour les coins
            long sellPrice = plugin.getConfigManager().getSellPrice(material);
            if (sellPrice > 0) {
                coinsValue = sellPrice;
            }

            // Essayer getBlockValue pour coins et tokens
            var blockValue = plugin.getConfigManager().getBlockValue(material);
            if (blockValue != null) {
                if (coinsValue == 0 && blockValue.getCoins() > 0) {
                    coinsValue = blockValue.getCoins();
                }
                if (blockValue.getTokens() > 0) {
                    tokensValue = blockValue.getTokens();
                }
            }

            // Si toujours pas de valeur, utiliser des valeurs par défaut
            if (coinsValue == 0 && tokensValue == 0) {
                coinsValue = getDefaultSellPrice(material);
            }

            // Calculer les gains avec bonus
            if (coinsValue > 0) {
                long earnings = coinsValue * quantity;

                // Appliquer le bonus sell_boost du GlobalBonusManager
                double sellBoost = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, GlobalBonusManager.BonusCategory.SELL_BONUS);
                earnings = Math.round(earnings * (1.0 + sellBoost));

                totalCoinsEarnings += earnings;
                itemsSold++;
            }

            if (tokensValue > 0) {
                long earnings = tokensValue * quantity;

                // Appliquer le bonus sell_boost du GlobalBonusManager
                double sellBoost = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, GlobalBonusManager.BonusCategory.SELL_BONUS);
                earnings = Math.round(earnings * (1.0 + sellBoost));

                totalTokensEarnings += earnings;
                itemsSold++;
            }
        }

        if (itemsSold > 0) {
            // Vider le stockage
            storage.clear();
            playerData.setAutominerStorageContents(storage);

            // Ajouter les gains
            if (totalCoinsEarnings > 0) {
                playerData.addCoins(totalCoinsEarnings);
            }
            if (totalTokensEarnings > 0) {
                playerData.addTokens(totalTokensEarnings);
            }

            plugin.getPlayerDataManager().markDirty(player.getUniqueId());

            // Messages de confirmation
            List<String> messages = new ArrayList<>();
            messages.add("§a✓ Tous les minerais vendus!");
            if (totalCoinsEarnings > 0) {
                messages.add("§7Coins gagnés: §a+" + NumberFormatter.format(totalCoinsEarnings) + "§a$");
            }
            if (totalTokensEarnings > 0) {
                messages.add("§7Tokens gagnés: §e+" + NumberFormatter.format(totalTokensEarnings) + " tokens");
            }

            for (String message : messages) {
                player.sendMessage(message);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

            openStorageMenu(player); // Rafraîchir le menu
        } else {
            player.sendMessage("§cAucun minerai vendable trouvé!");

            // Debug : afficher les 3 premiers minerais pour diagnostiquer
            if (storage.size() > 0) {
                player.sendMessage("§7Debug - Premiers minerais:");
                int count = 0;
                for (Map.Entry<Material, Long> entry : storage.entrySet()) {
                    if (count >= 3) break;

                    Material material = entry.getKey();
                    long sellPrice = plugin.getConfigManager().getSellPrice(material);
                    var blockValue = plugin.getConfigManager().getBlockValue(material);
                    long defaultPrice = getDefaultSellPrice(material);

                    player.sendMessage("§8- " + material.name() + ": sellPrice=" + sellPrice +
                            ", blockCoins=" + (blockValue != null ? blockValue.getCoins() : 0) +
                            ", default=" + defaultPrice);
                    count++;
                }
            }
        }
    }

    private void reopenMainMenu(Player player) {
        // Petit délai pour éviter les conflits d'inventaire
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getAutominerGUI().openMainMenu(player);
            }
        }, 2L);
    }
}