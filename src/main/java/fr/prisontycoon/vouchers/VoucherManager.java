package fr.prisontycoon.vouchers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Gestionnaire du système de vouchers
 */
public class VoucherManager {

    private final PrisonTycoon plugin;
    private final NamespacedKey voucherTypeKey;
    private final NamespacedKey voucherTierKey;

    public VoucherManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.voucherTypeKey = new NamespacedKey(plugin, "voucher_type");
        this.voucherTierKey = new NamespacedKey(plugin, "voucher_tier");
    }

    /**
     * Crée un voucher d'un type et tier spécifique
     */
    public ItemStack createVoucher(VoucherType type, int tier) {
        if (tier < 1 || tier > 10) {
            throw new IllegalArgumentException("Le tier doit être entre 1 et 10");
        }

        Material material = switch (type) {
            case TOKENS -> Material.DIAMOND;
            case COINS -> Material.GOLD_INGOT;
            case EXPERIENCE -> Material.EXPERIENCE_BOTTLE;
            case JOB_XP -> Material.EMERALD;
            case PRINTER_SLOT -> Material.PAPER;
        };

        ItemStack voucher = new ItemStack(material);
        ItemMeta meta = voucher.getItemMeta();

        // Nom de l'item
        plugin.getGUIManager().applyName(meta, type.getFullName(tier));

        // Lore
        List<String> lore = Arrays.asList(
                "",
                type.getDescription(),
                type.getValueDescription(tier),
                "",
                "§e▶ Clic droit pour utiliser",
                "",
                "§8Voucher Tier " + tier
        );
        plugin.getGUIManager().applyLore(meta, lore);

        // Ajoute les données persistantes
        meta.getPersistentDataContainer().set(voucherTypeKey, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(voucherTierKey, PersistentDataType.INTEGER, tier);

        voucher.setItemMeta(meta);
        return voucher;
    }

    /**
     * Vérifie si un item est un voucher
     */
    public boolean isVoucher(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(voucherTypeKey, PersistentDataType.STRING);
    }

    /**
     * Obtient le type de voucher d'un item
     */
    public VoucherType getVoucherType(ItemStack item) {
        if (!isVoucher(item)) return null;

        String typeName = item.getItemMeta().getPersistentDataContainer()
                .get(voucherTypeKey, PersistentDataType.STRING);

        try {
            return VoucherType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Obtient le tier d'un voucher
     */
    public int getVoucherTier(ItemStack item) {
        if (!isVoucher(item)) return 0;

        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(voucherTierKey, PersistentDataType.INTEGER, 0);
    }

    /**
     * Utilise un voucher
     */
    public boolean useVoucher(Player player, ItemStack voucher) {
        if (!isVoucher(voucher)) {
            return false;
        }

        VoucherType type = getVoucherType(voucher);
        int tier = getVoucherTier(voucher);

        if (type == null || tier <= 0) {
            player.sendMessage("§cVoucher invalide!");
            return false;
        }

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long value = type.getValue(tier);

        // Applique la récompense selon le type
        switch (type) {
            case TOKENS -> {
                playerData.addTokens(value);
                player.sendMessage("§a✅ §lVoucher utilisé!");
                player.sendMessage("§7Vous avez reçu: §b" + type.getValueDescription(tier).substring(9));
            }
            case COINS -> {
                playerData.addCoins(value);
                player.sendMessage("§a✅ §lVoucher utilisé!");
                player.sendMessage("§7Vous avez reçu: §6" + type.getValueDescription(tier).substring(9));
            }
            case EXPERIENCE -> {
                playerData.addExperience(value);
                player.sendMessage("§a✅ §lVoucher utilisé!");
                player.sendMessage("§7Vous avez reçu: §a" + type.getValueDescription(tier).substring(9));
            }
            case JOB_XP -> {
                String activeProfession = playerData.getActiveProfession();
                if (activeProfession == null) {
                    player.sendMessage("§c❌ Vous devez avoir un métier actif pour utiliser ce voucher!");
                    return false;
                }

                plugin.getProfessionManager().addProfessionXP(player, activeProfession, (int) value);
                player.sendMessage("§a✅ §lVoucher utilisé!");
                player.sendMessage("§7Vous avez reçu: §d" + type.getValueDescription(tier).substring(9));
                player.sendMessage("§7Métier: §e" + activeProfession);
            }
            case PRINTER_SLOT -> {
                int current = playerData.getPrinterExtraSlots();
                if (current >= 100) {
                    player.sendMessage("§e⚠ Vous avez déjà 100 slots d'imprimantes (limite atteinte).");
                    return false;
                }
                playerData.setPrinterExtraSlots(Math.min(100, current + 1));
                plugin.getPlayerDataManager().markDirty(player.getUniqueId());
                plugin.getPlayerDataManager().saveSingleColumn(player.getUniqueId(), "printer_extra_slots");
                player.sendMessage("§a✅ §lSlot d'imprimante obtenu!");
                player.sendMessage("§7Votre limite d'imprimantes augmente de §a+1§7 (" + playerData.getPrinterExtraSlots() + "/100)");
            }
        }

        // Effets visuels et sonores
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);

        // Marque les données comme modifiées
        plugin.getPlayerDataManager().markDirty(player.getUniqueId());

        // Retire le voucher de l'inventaire
        if (voucher.getAmount() > 1) {
            voucher.setAmount(voucher.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }

        return true;
    }

    /**
     * Donne un voucher à un joueur (commande admin)
     */
    public boolean giveVoucher(Player player, VoucherType type, int tier, int amount) {
        if (tier < 1 || tier > 10) {
            return false;
        }

        if (amount <= 0 || amount > 64) {
            return false;
        }

        // Vérifie l'espace dans l'inventaire
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage("§c❌ Inventaire plein!");
            return false;
        }

        ItemStack voucher = createVoucher(type, tier);
        voucher.setAmount(amount);

        player.getInventory().addItem(voucher);
        player.sendMessage("§a✅ Vous avez reçu " + amount + "x " + type.getFullName(tier));
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);

        return true;
    }
}