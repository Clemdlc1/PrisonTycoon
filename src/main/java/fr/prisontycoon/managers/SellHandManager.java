package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.data.TankData;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Gestionnaire des Sell Hands - Outils pour vendre le contenu des Tanks
 */
public class SellHandManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey sellHandKey;
    private final NamespacedKey sellHandTypeKey;
    private final NamespacedKey sellHandDurabilityKey;

    public SellHandManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.sellHandKey = new NamespacedKey(plugin, "sellhand");
        this.sellHandTypeKey = new NamespacedKey(plugin, "sellhand_type");
        this.sellHandDurabilityKey = new NamespacedKey(plugin, "sellhand_durability");
    }

    /**
     * Vérifie si l'item est un Sell Hand
     */
    public boolean isSellHand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(sellHandKey, PersistentDataType.BOOLEAN);
    }

    /**
     * Crée un nouveau Sell Hand
     */
    public ItemStack createSellHand(SellHandType type) {
        ItemStack sellHand = new ItemStack(Material.STICK);
        ItemMeta meta = sellHand.getItemMeta();

        // Données persistantes
        meta.getPersistentDataContainer().set(sellHandKey, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(sellHandTypeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(sellHandDurabilityKey, PersistentDataType.INTEGER, type.getMaxDurability());

        // Apparence
        plugin.getGUIManager().applyName(meta, type.getDisplayName());
        updateSellHandLore(meta, type, type.getMaxDurability());

        // Enchantement pour l'effet visuel
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        sellHand.setItemMeta(meta);
        return sellHand;
    }

    /**
     * Récupère le type d'un Sell Hand
     */
    public SellHandType getSellHandType(ItemStack item) {
        if (!isSellHand(item)) return null;

        ItemMeta meta = item.getItemMeta();
        String typeName = meta.getPersistentDataContainer().get(sellHandTypeKey, PersistentDataType.STRING);

        try {
            return SellHandType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Récupère la durabilité actuelle d'un Sell Hand
     */
    public int getSellHandDurability(ItemStack item) {
        if (!isSellHand(item)) return 0;

        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().getOrDefault(sellHandDurabilityKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Utilise un Sell Hand pour vendre le contenu d'un Tank
     */
    public boolean useSellHandOnTank(Player player, ItemStack sellHandItem, TankData tankData) {
        if (!isSellHand(sellHandItem)) {
            return false;
        }

        SellHandType type = getSellHandType(sellHandItem);
        if (type == null) return false;

        // Vérifier que le tank contient des items ou des billets
        boolean hasItems = !tankData.getContents().isEmpty();
        boolean hasBills = tankData.getBills() != null && !tankData.getBills().isEmpty();
        if (!hasItems && !hasBills) {
            player.sendMessage("§c❌ Le tank est vide!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        // Calculer la valeur totale avec le multiplicateur
        long totalValue = 0;
        int totalItems = 0;

        for (var entry : tankData.getContents().entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();

            long basePrice = plugin.getConfigManager().getSellPrice(material);
            if (basePrice > 0) {
                totalValue += basePrice * amount;
                totalItems += amount;
            }
        }

        // Ajouter la valeur des billets stockés
        for (var billEntry : tankData.getBills().entrySet()) {
            int tier = billEntry.getKey();
            int amount = billEntry.getValue();
            long billValue = plugin.getTankManager().getBillValue(tier);
            if (billValue > 0 && amount > 0) {
                totalValue += billValue * amount;
                totalItems += amount;
            }
        }

        if (totalValue == 0) {
            player.sendMessage("§c❌ Aucun item vendable dans le tank!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        // Appliquer le multiplicateur
        double multiplier = type.getMultiplier();
        double global = plugin.getGlobalBonusManager().getTotalBonusMultiplier(player, GlobalBonusManager.BonusCategory.SELL_BONUS);
        long finalValue = Math.round(totalValue * multiplier * global);

        // Effectuer la vente (items + billets)
        tankData.clearContents();
        tankData.clearBills();
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        playerData.addCoins(finalValue);

        // Messages de succès
        player.sendMessage("§a✓ §lVENTE AU SERVEUR:");
        player.sendMessage("§7▸ Items vendus: §b" + NumberFormatter.format(totalItems));
        player.sendMessage("§7▸ Valeur de base: §e" + NumberFormatter.format(totalValue) + "$");
        player.sendMessage("§7▸ Multiplicateur: §6" + String.format("%.1f", multiplier) + "x §7(" + type.getDisplayName() + "§7) §8X §a" + String.format("%.1f", global) + "x §7(Bonus Vente)");
        player.sendMessage("§7▸ §lGain final: §a" + NumberFormatter.format(finalValue) + "$");

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Utiliser la durabilité du Sell Hand
        int currentDurability = getSellHandDurability(sellHandItem);
        if (currentDurability <= 1) {
            // Le Sell Hand se brise
            player.getInventory().setItemInMainHand(null);
            player.sendMessage("§c💥 Votre " + type.getDisplayName() + " §cs'est brisé!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
        } else {
            // Réduire la durabilité
            ItemMeta meta = sellHandItem.getItemMeta();
            meta.getPersistentDataContainer().set(sellHandDurabilityKey, PersistentDataType.INTEGER, currentDurability - 1);
            updateSellHandLore(meta, type, currentDurability - 1);
            sellHandItem.setItemMeta(meta);

            // Message de durabilité si faible
            double durabilityPercent = (double) (currentDurability - 1) / type.getMaxDurability() * 100;
            if (durabilityPercent <= 25) {
                player.sendMessage("§7▸ Durabilité: " + (durabilityPercent <= 10 ? "§c" : "§e") +
                        String.format("%.1f%%", durabilityPercent));
            }
        }

        // Sauvegarder le tank
        plugin.getTankManager().saveTank(tankData);

        // Quêtes: utilisation d'un SellHand (compte les utilisations réussies)
        try {
            plugin.getQuestManager().addProgress(player, fr.prisontycoon.quests.QuestType.USE_SELLHAND, 1);
        } catch (Throwable ignored) {
        }

        return true;
    }

    /**
     * Met à jour le lore d'un Sell Hand avec les informations de durabilité
     */
    private void updateSellHandLore(ItemMeta meta, SellHandType type, int currentDurability) {
        double durabilityPercent = (double) currentDurability / type.getMaxDurability() * 100;
        String durabilityColor = durabilityPercent > 50 ? "§a" : durabilityPercent > 25 ? "§e" : "§c";

        List<String> lore = Arrays.asList(
                "§7Multiplicateur: §6" + String.format("%.1f", type.getMultiplier()) + "x",
                "§7Durabilité: " + durabilityColor + currentDurability + "§7/" + type.getMaxDurability() +
                        " §7(" + String.format("%.1f", durabilityPercent) + "%)",
                "",
                "§7Clic droit sur un Tank pour",
                "§7vendre tout son contenu au serveur",
                "",
                "§7Multiplicateur appliqué aux prix de vente"
        );
        plugin.getGUIManager().applyLore(meta, lore);
    }

    /**
     * Énumération des types de Sell Hand
     */
    public enum SellHandType {
        WOOD("§7🪵 Sell Hand en Bois", 0.8, 10),
        STONE("§8🪨 Sell Hand en Pierre", 0.85, 25),
        IRON("§f⚙ Sell Hand en Fer", 0.9, 50),
        GOLD("§6✨ Sell Hand en Or", 0.95, 75),
        DIAMOND("§b💎 Sell Hand en Diamant", 1.0, 100),
        NETHERITE("§4🔥 Sell Hand en Netherite", 1.1, 150),
        LEGENDARY("§6⭐ Sell Hand Légendaire", 1.25, 200);

        private final String displayName;
        private final double multiplier;
        private final int maxDurability;

        SellHandType(String displayName, double multiplier, int maxDurability) {
            this.displayName = displayName;
            this.multiplier = multiplier;
            this.maxDurability = maxDurability;
        }

        /**
         * Récupère le type de Sell Hand par son niveau (0-6)
         */
        public static SellHandType getByLevel(int level) {
            SellHandType[] types = values();
            if (level < 0 || level >= types.length) {
                return WOOD; // Par défaut
            }
            return types[level];
        }

        public String getDisplayName() {
            return displayName;
        }

        public double getMultiplier() {
            return multiplier;
        }

        public int getMaxDurability() {
            return maxDurability;
        }

        /**
         * Récupère le niveau du type (0-6)
         */
        public int getLevel() {
            return this.ordinal();
        }

        /**
         * Récupère le type suivant (upgrade)
         */
        public SellHandType getNext() {
            SellHandType[] types = values();
            int nextIndex = this.ordinal() + 1;
            if (nextIndex >= types.length) {
                return this; // Déjà au maximum
            }
            return types[nextIndex];
        }

        /**
         * Vérifie si ce type peut être amélioré
         */
        public boolean canUpgrade() {
            return this.ordinal() < values().length - 1;
        }
    }
}