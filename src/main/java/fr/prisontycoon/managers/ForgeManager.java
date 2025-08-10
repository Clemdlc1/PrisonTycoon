package fr.prisontycoon.managers;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.managers.GlobalBonusManager.BonusCategory;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Gestionnaire de la Forge (armures de cuivre, plans, fragments, craft et bonus)
 */
public class ForgeManager {

    private final PrisonTycoon plugin;

    private final NamespacedKey keySetId;
    private final NamespacedKey keyTier;
    private final NamespacedKey keyPiece;
    private final NamespacedKey keyBlueprintTier;
    private final NamespacedKey keyFragmentType;

    public ForgeManager(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.keySetId = new NamespacedKey(plugin, "forge_set");
        this.keyTier = new NamespacedKey(plugin, "forge_tier");
        this.keyPiece = new NamespacedKey(plugin, "forge_piece");
        this.keyBlueprintTier = new NamespacedKey(plugin, "forge_blueprint_tier");
        this.keyFragmentType = new NamespacedKey(plugin, "forge_fragment_type");
    }

    // ============================
    // Définition des Tiers / Pièces / Fragments
    // ============================

    public enum ArmorTier {
        T1(1, "§7Cuivre T1", Color.fromRGB(0x8E5A28)),
        T2(2, "§6Cuivre T2", Color.fromRGB(0x9E622B)),
        T3(3, "§6Cuivre T3", Color.fromRGB(0xAD6B2E)),
        T4(4, "§eCuivre T4", Color.fromRGB(0xBD7331)),
        T5(5, "§eCuivre T5", Color.fromRGB(0xCE7C35)),
        T6(6, "§aCuivre T6", Color.fromRGB(0xDF8538));

        private final int level;
        private final String displayName;
        private final Color leatherColor;

        ArmorTier(int level, String displayName, Color leatherColor) {
            this.level = level;
            this.displayName = displayName;
            this.leatherColor = leatherColor;
        }

        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        public Color getLeatherColor() { return leatherColor; }

        public static ArmorTier ofLevel(int lvl) {
            for (ArmorTier t : values()) if (t.level == lvl) return t;
            return T1;
        }
    }

    public enum ArmorPiece {
        HELMET("Casque", Material.LEATHER_HELMET),
        CHESTPLATE("Plastron", Material.LEATHER_CHESTPLATE),
        LEGGINGS("Jambières", Material.LEATHER_LEGGINGS),
        BOOTS("Bottes", Material.LEATHER_BOOTS);

        private final String displayName;
        private final Material material;

        ArmorPiece(String displayName, Material material) {
            this.displayName = displayName;
            this.material = material;
        }
        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
    }

    public enum FragmentType {
        COPPER("Fragment de Cuivre", Material.COPPER_INGOT),
        ALLOY("Fragment d'Alliage", Material.IRON_INGOT),
        ESSENCE("Essence d'Armure", Material.LAPIS_LAZULI);

        private final String display;
        private final Material icon;
        FragmentType(String display, Material icon) { this.display = display; this.icon = icon; }
        public String getDisplay() { return display; }
        public Material getIcon() { return icon; }
    }

    // ============================
    // Création d'items (pièces, plans, fragments)
    // ============================

    public ItemStack createArmorPiece(ArmorTier tier, ArmorPiece piece) {
        ItemStack item = new ItemStack(piece.getMaterial());
        ItemMeta baseMeta = item.getItemMeta();
        if (baseMeta instanceof LeatherArmorMeta meta) {
            meta.setColor(tier.getLeatherColor());
            plugin.getGUIManager().applyName(meta, "§6" + piece.getDisplayName() + " " + tier.getDisplayName());
            meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, "copper");
            meta.getPersistentDataContainer().set(keyTier, PersistentDataType.INTEGER, tier.getLevel());
            meta.getPersistentDataContainer().set(keyPiece, PersistentDataType.STRING, piece.name());
            plugin.getGUIManager().applyLore(meta, buildPieceLore(tier));
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> buildPieceLore(ArmorTier tier) {
        Map<BonusCategory, Double> pieceBonus = getPerPieceBonus(tier);
        List<String> lore = new ArrayList<>();
        lore.add("§7Bonus de la pièce:");
        lore.add(formatBonusLine(BonusCategory.EXPERIENCE_BONUS, pieceBonus.get(BonusCategory.EXPERIENCE_BONUS)));
        lore.add(formatBonusLine(BonusCategory.TOKEN_BONUS, pieceBonus.get(BonusCategory.TOKEN_BONUS)));
        lore.add(formatBonusLine(BonusCategory.MONEY_BONUS, pieceBonus.get(BonusCategory.MONEY_BONUS)));
        lore.add(formatBonusLine(BonusCategory.SELL_BONUS, pieceBonus.get(BonusCategory.SELL_BONUS)));
        lore.add(formatBonusLine(BonusCategory.FORTUNE_BONUS, pieceBonus.get(BonusCategory.FORTUNE_BONUS)));
        lore.add(" ");
        lore.add("§8Set complet: §a+20% §7sur ces effets");
        return lore;
    }

    private String formatBonusLine(BonusCategory cat, Double percent) {
        if (percent == null || percent <= 0) return "§7- " + cat.getFormattedName() + ": §70%";
        return "§7- " + cat.getFormattedName() + ": §a+" + String.format(Locale.FRANCE, "%.1f", percent) + "%";
    }

    public ItemStack createBlueprint(ArmorTier tier) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§dPlan d'Armure §f" + tier.getDisplayName());
        plugin.getGUIManager().applyLore(meta, List.of(
                "§7Nécessaire pour forger des pièces",
                "§7de ce palier d'armure",
                " ",
                "§8Obtenu dans les caisses"
        ));
        meta.getPersistentDataContainer().set(keyBlueprintTier, PersistentDataType.INTEGER, tier.getLevel());
        paper.setItemMeta(meta);
        return paper;
    }

    public ItemStack createFragment(FragmentType type, int amount) {
        ItemStack stack = new ItemStack(type.getIcon(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§b" + type.getDisplay());
        plugin.getGUIManager().applyLore(meta, List.of("§7Utilisé pour la forge d'armures"));
        meta.getPersistentDataContainer().set(keyFragmentType, PersistentDataType.STRING, type.name());
        stack.setItemMeta(meta);
        return stack;
    }

    // ============================
    // Détection items
    // ============================

    public boolean isArmorPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(keySetId, PersistentDataType.STRING) &&
                "copper".equals(meta.getPersistentDataContainer().get(keySetId, PersistentDataType.STRING));
    }

    public ArmorPiece getPiece(ItemStack item) {
        if (!isArmorPiece(item)) return null;
        String p = item.getItemMeta().getPersistentDataContainer().get(keyPiece, PersistentDataType.STRING);
        try { return ArmorPiece.valueOf(p); } catch (Exception e) { return null; }
    }

    public ArmorTier getTier(ItemStack item) {
        if (!isArmorPiece(item)) return null;
        Integer lvl = item.getItemMeta().getPersistentDataContainer().get(keyTier, PersistentDataType.INTEGER);
        if (lvl == null) return null;
        return ArmorTier.ofLevel(lvl);
    }

    public boolean isBlueprint(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyBlueprintTier, PersistentDataType.INTEGER);
    }

    public ArmorTier getBlueprintTier(ItemStack item) {
        if (!isBlueprint(item)) return null;
        Integer lvl = item.getItemMeta().getPersistentDataContainer().get(keyBlueprintTier, PersistentDataType.INTEGER);
        return lvl == null ? null : ArmorTier.ofLevel(lvl);
    }

    public boolean isFragment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyFragmentType, PersistentDataType.STRING);
    }

    public FragmentType getFragmentType(ItemStack item) {
        if (!isFragment(item)) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(keyFragmentType, PersistentDataType.STRING);
        try { return FragmentType.valueOf(name); } catch (Exception e) { return null; }
    }

    // ============================
    // Bonus & calculs
    // ============================

    /** Retourne le bonus par pièce (en %) pour un tier donné. */
    public Map<BonusCategory, Double> getPerPieceBonus(ArmorTier tier) {
        double t = tier.getLevel();
        Map<BonusCategory, Double> map = new EnumMap<>(BonusCategory.class);
        map.put(BonusCategory.EXPERIENCE_BONUS, t * 1.0);
        map.put(BonusCategory.TOKEN_BONUS, t * 1.0);
        map.put(BonusCategory.MONEY_BONUS, t * 1.0);
        map.put(BonusCategory.SELL_BONUS, t * 0.5);
        map.put(BonusCategory.FORTUNE_BONUS, t * 1.0);
        return map;
    }

    /** Retourne la somme des bonus d'armure équipés (en %), avec +20% si set complet du même tier. */
    public Map<BonusCategory, Double> getEquippedArmorBonuses(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack h = inv.getHelmet();
        ItemStack c = inv.getChestplate();
        ItemStack l = inv.getLeggings();
        ItemStack b = inv.getBoots();

        List<ItemStack> pieces = new ArrayList<>();
        if (isArmorPiece(h)) pieces.add(h);
        if (isArmorPiece(c)) pieces.add(c);
        if (isArmorPiece(l)) pieces.add(l);
        if (isArmorPiece(b)) pieces.add(b);

        Map<BonusCategory, Double> sum = new EnumMap<>(BonusCategory.class);
        if (pieces.isEmpty()) return sum;

        ArmorTier setTier = null;
        for (ItemStack it : pieces) {
            ArmorTier t = getTier(it);
            if (t == null) continue;
            if (setTier == null) setTier = t;
            Map<BonusCategory, Double> perPiece = getPerPieceBonus(t);
            for (var e : perPiece.entrySet()) {
                sum.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }

        // Full set check: les 4 pièces présentes et du même tier
        boolean fullSet = isArmorPiece(h) && isArmorPiece(c) && isArmorPiece(l) && isArmorPiece(b);
        if (fullSet) {
            ArmorTier th = getTier(h), tc = getTier(c), tl = getTier(l), tb = getTier(b);
            if (th != null && th == tc && tc == tl && tl == tb) {
                // +20% d'effets sur la somme
                for (BonusCategory cat : new ArrayList<>(sum.keySet())) {
                    sum.put(cat, sum.get(cat) * 1.2);
                }
            }
        }
        return sum;
    }

    // ============================
    // Craft: coûts et exécution
    // ============================

    public record CraftCost(int blueprintTier,
                            Map<FragmentType, Integer> fragments,
                            boolean requirePreviousPiece,
                            int experienceCost) { }

    /** Détermine les coûts par pièce et par tier. */
    public CraftCost getCraftCost(ArmorTier tier, ArmorPiece piece) {
        int t = tier.getLevel();
        Map<FragmentType, Integer> frags = new EnumMap<>(FragmentType.class);
        switch (piece) {
            case HELMET -> {
                frags.put(FragmentType.COPPER, 6 + 2 * t);
                frags.put(FragmentType.ALLOY, 1 + (t >= 3 ? 1 : 0));
                frags.put(FragmentType.ESSENCE, t >= 4 ? 1 : 0);
            }
            case CHESTPLATE -> {
                frags.put(FragmentType.COPPER, 10 + 3 * t);
                frags.put(FragmentType.ALLOY, 2 + (t >= 3 ? 1 : 0));
                frags.put(FragmentType.ESSENCE, t >= 3 ? 1 : 0);
            }
            case LEGGINGS -> {
                frags.put(FragmentType.COPPER, 8 + 3 * t);
                frags.put(FragmentType.ALLOY, 2 + (t >= 4 ? 1 : 0));
                frags.put(FragmentType.ESSENCE, t >= 5 ? 1 : 0);
            }
            case BOOTS -> {
                frags.put(FragmentType.COPPER, 6 + 2 * t);
                frags.put(FragmentType.ALLOY, 1 + (t >= 5 ? 1 : 0));
            }
        }
        int xp = 100 * t * switch (piece) {
            case HELMET -> 2;
            case BOOTS -> 2;
            case LEGGINGS -> 3;
            case CHESTPLATE -> 4;
        };
        boolean requirePrev = tier != ArmorTier.T1; // T1 pas besoin de pièce précédente
        return new CraftCost(tier.getLevel(), frags, requirePrev, xp);
    }

    public boolean canCraft(Player player, ArmorTier tier, ArmorPiece piece) {
        CraftCost cost = getCraftCost(tier, piece);
        // 1) Plan
        if (!hasBlueprint(player, cost.blueprintTier)) return false;
        // 2) Fragments
        for (var e : cost.fragments.entrySet()) {
            if (!hasFragments(player, e.getKey(), e.getValue())) return false;
        }
        // 3) Pièce précédente si nécessaire
        if (cost.requirePreviousPiece) {
            ArmorTier prev = ArmorTier.ofLevel(tier.getLevel() - 1);
            if (!hasPreviousPiece(player, prev, piece)) return false;
        }
        // 4) EXP
        long exp = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getExperience();
        return exp >= cost.experienceCost;
    }

    public List<String> getMissingRequirements(Player player, ArmorTier tier, ArmorPiece piece) {
        CraftCost cost = getCraftCost(tier, piece);
        List<String> missing = new ArrayList<>();
        if (!hasBlueprint(player, cost.blueprintTier)) missing.add("§dPlan §7(T" + cost.blueprintTier + ")");
        for (var e : cost.fragments.entrySet()) {
            int have = countFragments(player, e.getKey());
            if (have < e.getValue()) missing.add("§b" + e.getKey().getDisplay() + " §7x" + e.getValue());
        }
        if (cost.requirePreviousPiece) {
            ArmorTier prev = ArmorTier.ofLevel(tier.getLevel() - 1);
            if (!hasPreviousPiece(player, prev, piece)) missing.add("§7" + piece.getDisplayName() + " §7(T" + prev.getLevel() + ")");
        }
        long exp = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getExperience();
        if (exp < cost.experienceCost) missing.add("§aXP §7(" + cost.experienceCost + ")");
        return missing;
    }

    public boolean craft(Player player, ArmorTier tier, ArmorPiece piece) {
        CraftCost cost = getCraftCost(tier, piece);
        if (!canCraft(player, tier, piece)) return false;

        // Consommation: plan
        consumeBlueprint(player, cost.blueprintTier);
        // Consommation: fragments
        for (var e : cost.fragments.entrySet()) consumeFragments(player, e.getKey(), e.getValue());
        // Consommation: pièce précédente
        if (cost.requirePreviousPiece) {
            ArmorTier prev = ArmorTier.ofLevel(tier.getLevel() - 1);
            consumePreviousPiece(player, prev, piece);
        }
        // Consommation: expérience
        var data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        data.removeExperience(cost.experienceCost);
        plugin.getEconomyManager().updateVanillaExpFromCustom(player, data.getExperience());

        // Donne la nouvelle pièce
        ItemStack item = createArmorPiece(tier, piece);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) player.getWorld().dropItemNaturally(player.getLocation(), item);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.1f);
        player.sendMessage("§a✓ §7Vous avez forgé: §6" + piece.getDisplayName() + " " + tier.getDisplayName());
        return true;
    }

    // ============================
    // Inventaire utils
    // ============================

    private boolean hasBlueprint(Player player, int tier) {
        return findBlueprintSlot(player, tier) != -1;
    }

    private int findBlueprintSlot(Player player, int tier) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (isBlueprint(it)) {
                ArmorTier t = getBlueprintTier(it);
                if (t != null && t.getLevel() == tier) return i;
            }
        }
        return -1;
    }

    private void consumeBlueprint(Player player, int tier) {
        int slot = findBlueprintSlot(player, tier);
        if (slot >= 0) {
            ItemStack it = player.getInventory().getItem(slot);
            if (it != null) {
                it.setAmount(it.getAmount() - 1);
                if (it.getAmount() <= 0) player.getInventory().setItem(slot, null);
            }
        }
    }

    private int countFragments(Player player, FragmentType type) {
        int count = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (isFragment(it) && getFragmentType(it) == type) count += it.getAmount();
        }
        return count;
    }

    private boolean hasFragments(Player player, FragmentType type, int required) {
        return countFragments(player, type) >= required;
    }

    private void consumeFragments(Player player, FragmentType type, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack it = contents[i];
            if (isFragment(it) && getFragmentType(it) == type) {
                int take = Math.min(remaining, it.getAmount());
                it.setAmount(it.getAmount() - take);
                if (it.getAmount() <= 0) contents[i] = null;
                remaining -= take;
            }
        }
        player.getInventory().setContents(contents);
    }

    private boolean hasPreviousPiece(Player player, ArmorTier prevTier, ArmorPiece piece) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (isArmorPiece(it) && getPiece(it) == piece) {
                ArmorTier t = getTier(it);
                if (t != null && t == prevTier) return true;
            }
        }
        return false;
    }

    private void consumePreviousPiece(Player player, ArmorTier prevTier, ArmorPiece piece) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (isArmorPiece(it) && getPiece(it) == piece) {
                ArmorTier t = getTier(it);
                if (t != null && t == prevTier) {
                    contents[i] = null;
                    break;
                }
            }
        }
        player.getInventory().setContents(contents);
    }
}


