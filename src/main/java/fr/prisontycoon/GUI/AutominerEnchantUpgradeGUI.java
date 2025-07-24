package fr.prisontycoon.GUI;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * GUI d'amélioration d'un enchantement spécifique d'automineur
 */
public class AutominerEnchantUpgradeGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey enchantKey;
    private final NamespacedKey slotKey;
    private final NamespacedKey amountKey;

    public AutominerEnchantUpgradeGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.enchantKey = new NamespacedKey(plugin, "enchant_name");
        this.slotKey = new NamespacedKey(plugin, "autominer_slot");
        this.amountKey = new NamespacedKey(plugin, "upgrade_amount");
    }

    /**
     * Ouvre le menu d'amélioration d'un enchantement spécifique
     */
    public void openUpgradeMenu(Player player, String enchantmentName, int autominerSlot) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        ItemStack autominer = (autominerSlot == 1) ?
                playerData.getActiveAutominerSlot1() :
                playerData.getActiveAutominerSlot2();

        if (autominer == null) {
            player.sendMessage("§cErreur: Automineur non trouvé!");
            return;
        }

        AutominerType type = plugin.getAutominerManager().getAutominerType(autominer);
        Map<String, Integer> enchantments = plugin.getAutominerManager().getAutominerEnchantments(autominer);

        int currentLevel = enchantments.getOrDefault(enchantmentName, 0);
        int maxLevel = type.getMaxEnchantmentLevel(enchantmentName);

        String title = "§6🔧 " + getEnchantmentDisplayName(enchantmentName) + " §6🔧";
        Inventory gui = Bukkit.createInventory(null, 27, title);

        // Remplissage décoratif
        fillBorders(gui);

        // Tête du joueur avec informations
        gui.setItem(4, createPlayerHead(player, enchantmentName, currentLevel, maxLevel));

        // Boutons d'amélioration
        createUpgradeButtons(gui, player, enchantmentName, autominerSlot, currentLevel, maxLevel, type);

        // Bouton retour
        gui.setItem(18, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.4f);
    }

    /**
     * Gère les clics dans le menu d'amélioration
     */
    public void handleUpgradeClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();

        // Bouton retour
        if (slot == 18) {
            // Retourner au menu d'enchantement de l'automineur
            int autominerSlot = 1; // Par défaut, on peut améliorer la logique plus tard
            PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            ItemStack autominer = playerData.getActiveAutominerSlot1();
            if (autominer == null) {
                autominer = playerData.getActiveAutominerSlot2();
                autominerSlot = 2;
            }

            if (autominer != null) {
                plugin.getAutominerEnchantGUI().openEnchantMenu(player, autominer, autominerSlot);
            } else {
                plugin.getAutominerGUI().openMainMenu(player);
            }
            return;
        }

        // Boutons d'amélioration
        if (meta.getPersistentDataContainer().has(amountKey, PersistentDataType.INTEGER)) {
            String enchantmentName = meta.getPersistentDataContainer().get(enchantKey, PersistentDataType.STRING);
            int autominerSlot = meta.getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
            int upgradeAmount = meta.getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);

            if (upgradeAmount == -1) {
                // Bouton MAX
                upgradeToMax(player, enchantmentName, autominerSlot);
            } else {
                // Amélioration normale
                upgradeEnchantment(player, enchantmentName, autominerSlot, upgradeAmount);
            }
        }
    }

    // ==================== MÉTHODES PRIVÉES ====================

    private void createUpgradeButtons(Inventory gui, Player player, String enchantmentName, int autominerSlot,
                                      int currentLevel, int maxLevel, AutominerType type) {
        int[] upgradeAmounts = {1, 5, 10, 25, 100, 250, 500};
        int[] slots = {10, 11, 12, 13, 14, 15, 16};

        for (int i = 0; i < upgradeAmounts.length; i++) {
            int amount = upgradeAmounts[i];
            gui.setItem(slots[i], createUpgradeButton(player, enchantmentName, autominerSlot,
                    amount, currentLevel, maxLevel, type));
        }

        // Bouton MAX
        if (currentLevel < maxLevel) {
            gui.setItem(22, createMaxButton(player, enchantmentName, autominerSlot, currentLevel, maxLevel, type));
        }
    }

    private ItemStack createUpgradeButton(Player player, String enchantmentName, int autominerSlot,
                                          int requestedLevels, int currentLevel, int maxLevel, AutominerType type) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long availableTokens = playerData.getTokens();

        // Vérifier les limites
        boolean levelMaxReached = currentLevel + requestedLevels > maxLevel;
        int actualLevels = Math.min(requestedLevels, maxLevel - currentLevel);

        // Calculer le coût total
        long totalCost = 0;
        for (int i = 1; i <= actualLevels; i++) {
            int targetLevel = currentLevel + i;
            totalCost += type.calculateUpgradeCost(enchantmentName, targetLevel - 1, getBaseTokenCost(enchantmentName));
        }

        boolean canAfford = availableTokens >= totalCost;

        // Déterminer l'apparence
        Material material;
        String color;

        if (levelMaxReached && actualLevels == 0) {
            material = Material.BARRIER;
            color = "§c";
        } else if (canAfford) {
            material = Material.GREEN_CONCRETE;
            color = "§a";
        } else {
            material = Material.RED_CONCRETE;
            color = "§c";
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color + "+" + requestedLevels + " Niveau" + (requestedLevels > 1 ? "x" : ""));

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        if (actualLevels > 0 && canAfford) {
            lore.add("§a✅ §lAMÉLIORATION POSSIBLE");
            lore.add("§7▸ Niveaux: §f" + currentLevel + " §7→ §a" + (currentLevel + actualLevels));
            lore.add("§7▸ Coût total: §6" + NumberFormatter.format(totalCost) + " tokens");
            lore.add("");
            lore.add("§b🔮 §lEFFETS APRÈS AMÉLIORATION");
            addEffectDescription(lore, enchantmentName, currentLevel, currentLevel + actualLevels);
        } else {
            lore.add("§c❌ §lAMÉLIORATION IMPOSSIBLE");
            if (levelMaxReached && actualLevels == 0) {
                lore.add("§7▸ §cNiveau maximum atteint!");
            } else if (actualLevels < requestedLevels) {
                lore.add("§7▸ §cDépasse le niveau maximum");
                lore.add("§7▸ Niveaux possibles: §e" + actualLevels);
            } else {
                lore.add("§7▸ §cPas assez de tokens");
                lore.add("§7▸ Coût: §6" + NumberFormatter.format(totalCost));
                lore.add("§7▸ Disponible: §c" + NumberFormatter.format(availableTokens));
                lore.add("§7▸ Manquant: §c" + NumberFormatter.format(totalCost - availableTokens));
            }
        }

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchantmentName);
        meta.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, autominerSlot);
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, requestedLevels);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createMaxButton(Player player, String enchantmentName, int autominerSlot,
                                      int currentLevel, int maxLevel, AutominerType type) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long availableTokens = playerData.getTokens();

        int maxAffordable = calculateMaxAffordableLevels(enchantmentName, currentLevel, maxLevel,
                availableTokens, type);

        ItemStack item = new ItemStack(maxAffordable > 0 ? Material.DIAMOND_BLOCK : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (maxAffordable > 0) {
            meta.setDisplayName("§b⭐ §lMAXIMUM POSSIBLE");

            List<String> lore = new ArrayList<>();
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            lore.add("§a✅ §lAMÉLIORATION MAXIMALE");
            lore.add("§7▸ Niveaux: §f" + currentLevel + " §7→ §a" + (currentLevel + maxAffordable));
            lore.add("§7▸ Niveaux achetés: §e+" + maxAffordable);

            // Calculer le coût total pour max affordable
            long totalCost = 0;
            for (int i = 1; i <= maxAffordable; i++) {
                totalCost += type.calculateUpgradeCost(enchantmentName, currentLevel + i - 1,
                        getBaseTokenCost(enchantmentName));
            }
            lore.add("§7▸ Coût total: §6" + NumberFormatter.format(totalCost) + " tokens");
            lore.add("");
            lore.add("§b🔮 §lEFFETS APRÈS AMÉLIORATION");
            addEffectDescription(lore, enchantmentName, currentLevel, currentLevel + maxAffordable);
            lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

            meta.setLore(lore);
        } else {
            meta.setDisplayName("§c❌ §lAUCUNE AMÉLIORATION POSSIBLE");
            meta.setLore(Arrays.asList(
                    "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                    "§c❌ §lPAS D'AMÉLIORATION POSSIBLE",
                    "§7▸ §cNiveau maximum atteint ou pas assez de tokens",
                    "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
            ));
        }

        meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.STRING, enchantmentName);
        meta.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, autominerSlot);
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, -1); // -1 pour MAX
        item.setItemMeta(meta);

        return item;
    }

    private void upgradeEnchantment(Player player, String enchantmentName, int autominerSlot, int levels) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        ItemStack autominer = (autominerSlot == 1) ?
                playerData.getActiveAutominerSlot1() :
                playerData.getActiveAutominerSlot2();

        if (autominer == null) {
            player.sendMessage("§cErreur: Automineur non trouvé!");
            return;
        }

        AutominerType type = plugin.getAutominerManager().getAutominerType(autominer);
        Map<String, Integer> enchantments = plugin.getAutominerManager().getAutominerEnchantments(autominer);

        int currentLevel = enchantments.getOrDefault(enchantmentName, 0);
        int maxLevel = type.getMaxEnchantmentLevel(enchantmentName);
        int actualLevels = Math.min(levels, maxLevel - currentLevel);

        if (actualLevels <= 0) {
            player.sendMessage("§cImpossible d'améliorer davantage cet enchantement!");
            return;
        }

        // Calculer le coût total
        long totalCost = 0;
        for (int i = 1; i <= actualLevels; i++) {
            totalCost += type.calculateUpgradeCost(enchantmentName, currentLevel + i - 1,
                    getBaseTokenCost(enchantmentName));
        }

        if (playerData.getTokens() < totalCost) {
            player.sendMessage("§cVous n'avez pas assez de tokens! Coût: " + NumberFormatter.format(totalCost));
            return;
        }

        // Effectuer l'amélioration
        playerData.removeTokens(totalCost);
        enchantments.put(enchantmentName, currentLevel + actualLevels);

        autominer = plugin.getAutominerManager().setAutominerEnchantments(autominer, enchantments);

        // Mettre à jour dans les données du joueur
        if (autominerSlot == 1) {
            playerData.setActiveAutominerSlot1(autominer);
        } else {
            playerData.setActiveAutominerSlot2(autominer);
        }

        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        player.sendMessage("§a✓ " + getEnchantmentDisplayName(enchantmentName) + " amélioré de " +
                actualLevels + " niveau" + (actualLevels > 1 ? "x" : "") + "!");
        player.sendMessage("§7Nouveau niveau: §f" + (currentLevel + actualLevels) +
                "§7/§f" + (maxLevel == Integer.MAX_VALUE ? "∞" : maxLevel));
        player.sendMessage("§7Coût: §6" + NumberFormatter.format(totalCost) + " tokens");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Rafraîchir le menu
        openUpgradeMenu(player, enchantmentName, autominerSlot);
    }

    private void upgradeToMax(Player player, String enchantmentName, int autominerSlot) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        ItemStack autominer = (autominerSlot == 1) ?
                playerData.getActiveAutominerSlot1() :
                playerData.getActiveAutominerSlot2();

        if (autominer == null) {
            player.sendMessage("§cErreur: Automineur non trouvé!");
            return;
        }

        AutominerType type = plugin.getAutominerManager().getAutominerType(autominer);
        Map<String, Integer> enchantments = plugin.getAutominerManager().getAutominerEnchantments(autominer);

        int currentLevel = enchantments.getOrDefault(enchantmentName, 0);
        int maxLevel = type.getMaxEnchantmentLevel(enchantmentName);

        int maxAffordable = calculateMaxAffordableLevels(enchantmentName, currentLevel, maxLevel,
                playerData.getTokens(), type);

        if (maxAffordable <= 0) {
            player.sendMessage("§cImpossible d'améliorer davantage cet enchantement!");
            return;
        }

        upgradeEnchantment(player, enchantmentName, autominerSlot, maxAffordable);
    }

    // ==================== UTILITAIRES ====================

    private int calculateMaxAffordableLevels(String enchantmentName, int currentLevel, int maxLevel,
                                             long availableTokens, AutominerType type) {
        if (currentLevel >= maxLevel) return 0;

        int maxLevels = 0;
        long remainingTokens = availableTokens;

        for (int level = currentLevel + 1; level <= maxLevel && remainingTokens > 0; level++) {
            long cost = type.calculateUpgradeCost(enchantmentName, level - 1, getBaseTokenCost(enchantmentName));
            if (remainingTokens >= cost) {
                remainingTokens -= cost;
                maxLevels++;
            } else {
                break;
            }
        }

        return maxLevels;
    }

    private void addEffectDescription(List<String> lore, String enchantmentName, int currentLevel, int newLevel) {
        switch (enchantmentName) {
            case "EFFICACITE" -> {
                lore.add("§7▸ Minage actuel: " + getEfficiencyDescription(currentLevel));
                lore.add("§7▸ Minage nouveau: §a" + getEfficiencyDescription(newLevel));
            }
            case "FORTUNE" -> {
                double currentMult = 1.0 + (currentLevel * 0.01);
                double newMult = 1.0 + (newLevel * 0.01);
                lore.add("§7▸ Multiplicateur actuel: §fx" + String.format("%.2f", currentMult));
                lore.add("§7▸ Multiplicateur nouveau: §a×" + String.format("%.2f", newMult));
                lore.add("§7▸ §eMultiplie vraiment les minerais obtenus");
            }
            case "TOKENGREED" -> {
                lore.add("§7▸ Bonus actuel: §e25% chance → +" + Math.max(1, Math.round(currentLevel * 0.5)) + " tokens");
                lore.add("§7▸ Bonus nouveau: §a25% chance → +" + Math.max(1, Math.round(newLevel * 0.5)) + " tokens");
                lore.add("§7▸ §eBonus indépendant de la valeur du bloc");
            }
            case "EXPGREED" -> {
                lore.add("§7▸ Bonus actuel: §a25% chance → +" + (currentLevel * 2) + " exp");
                lore.add("§7▸ Bonus nouveau: §a25% chance → +" + (newLevel * 2) + " exp");
                lore.add("§7▸ §eBonus indépendant de la valeur du bloc");
            }
            case "MONEYGREED" -> {
                lore.add("§7▸ Bonus actuel: §625% chance → +" + Math.max(1, currentLevel) + " coins");
                lore.add("§7▸ Bonus nouveau: §a25% chance → +" + Math.max(1, newLevel) + " coins");
                lore.add("§7▸ §eBonus indépendant de la valeur du bloc");
            }
            case "KEYGREED" -> {
                double currentChance = currentLevel * 0.001;
                double newChance = newLevel * 0.001;
                lore.add("§7▸ Chance actuelle: §d" + String.format("%.3f", currentChance) + "% par bloc");
                lore.add("§7▸ Chance nouvelle: §a" + String.format("%.3f", newChance) + "% par bloc");
            }
            case "FUELEFFICIENCY" -> {
                double currentReduction = Math.min(currentLevel / 100.0, 0.9) * 100;
                double newReduction = Math.min(newLevel / 100.0, 0.9) * 100;
                lore.add("§7▸ Réduction actuelle: §c-" + String.format("%.1f", currentReduction) + "%");
                lore.add("§7▸ Réduction nouvelle: §a-" + String.format("%.1f", newReduction) + "%");
                if (newLevel >= 100) {
                    lore.add("§7▸ §aNiveau 100 = réduction par 2");
                }
            }
            case "BEACONFINDER" -> {
                lore.add("§7▸ Chance actuelle: §e" + currentLevel + "% par bloc");
                lore.add("§7▸ Chance nouvelle: §a" + newLevel + "% par bloc");
                lore.add("§7▸ §eUniquement pour automineurs Beacon");
            }
        }
    }

    private String getEfficiencyDescription(int level) {
        if (level == 0) return "Aucun bonus";

        // Calculer la fréquence selon la nouvelle logique
        // Niveau 1 = 1 bloc/5min, niveau 1000 = 1 bloc/min, niveau 1500 = chance de 2 blocs
        double frequency = Math.max(1.0, 5.0 - (level / 250.0)); // 5min -> 1min sur 1000 niveaux

        if (level >= 1500) {
            double chanceSecondBlock = Math.min(100.0, (level - 1500) / 10.0); // 1% par 10 niveaux au-dessus de 1500
            return String.format("1 bloc/%.1fmin + %.1f%% 2ème bloc", frequency, chanceSecondBlock);
        } else {
            return String.format("1 bloc/%.1fmin", frequency);
        }
    }

    private String getEnchantmentDisplayName(String enchantmentName) {
        return switch (enchantmentName) {
            case "EFFICACITE" -> "Efficacité";
            case "FORTUNE" -> "Fortune";
            case "TOKENGREED" -> "Avidité de Tokens";
            case "EXPGREED" -> "Avidité d'Expérience";
            case "MONEYGREED" -> "Avidité d'Argent";
            case "KEYGREED" -> "Avidité de Clés";
            case "FUELEFFICIENCY" -> "Efficacité de Carburant";
            case "BEACONFINDER" -> "Détecteur de Beacons";
            default -> enchantmentName;
        };
    }

    private int getBaseTokenCost(String enchantmentName) {
        return switch (enchantmentName) {
            case "EFFICACITE" -> 100;
            case "FORTUNE" -> 200;
            case "TOKENGREED" -> 500;
            case "EXPGREED" -> 500;
            case "MONEYGREED" -> 500;
            case "KEYGREED" -> 1000;
            case "FUELEFFICIENCY" -> 750;
            case "BEACONFINDER" -> 2000;
            default -> 100;
        };
    }

    private ItemStack createPlayerHead(Player player, String enchantmentName, int currentLevel, int maxLevel) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(player);
        meta.setDisplayName("§6📊 " + getEnchantmentDisplayName(enchantmentName));

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("§6💰 §lÉCONOMIE ACTUELLE");
        lore.add("§7│ §6Coins: §e" + NumberFormatter.format(playerData.getCoins()));
        lore.add("§7│ §eTokens: §6" + NumberFormatter.format(playerData.getTokens()));
        lore.add("§7│ §aExpérience: §2" + NumberFormatter.format(playerData.getExperience()));
        lore.add("§7└ §7Enchantement: §f" + currentLevel + "§7/" +
                (maxLevel == Integer.MAX_VALUE ? "∞" : maxLevel));
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        meta.setLore(lore);
        head.setItemMeta(meta);

        return head;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§c« Retour");
        meta.setLore(Arrays.asList("§7Retourner au menu d'amélioration"));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorders(Inventory gui) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);

        // Remplir les bordures
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, filler);
                }
            }
        }
    }
}