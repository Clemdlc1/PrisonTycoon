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
 * Gestionnaire de la Forge Amélioré avec noms RP et design immersif
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

    // ═══════════════════════════════════════════════════════════════
    // ENUM POUR LES EFFETS PAR TIER
    // ═══════════════════════════════════════════════════════════════

    public enum ArmorEffects {
        T1_PIECE(Map.of(
                BonusCategory.EXPERIENCE_BONUS, 2.0,
                BonusCategory.TOKEN_BONUS, 1.8,
                BonusCategory.MONEY_BONUS, 1.5,
                BonusCategory.SELL_BONUS, 1.0,
                BonusCategory.FORTUNE_BONUS, 1.2
        )),
        T2_PIECE(Map.of(
                BonusCategory.EXPERIENCE_BONUS, 3.0,
                BonusCategory.TOKEN_BONUS, 2.7,
                BonusCategory.MONEY_BONUS, 2.3,
                BonusCategory.SELL_BONUS, 1.5,
                BonusCategory.FORTUNE_BONUS, 1.8
        )),
        T3_PIECE(Map.of(
                BonusCategory.EXPERIENCE_BONUS, 4.4,
                BonusCategory.TOKEN_BONUS, 4.0,
                BonusCategory.MONEY_BONUS, 3.3,
                BonusCategory.SELL_BONUS, 2.2,
                BonusCategory.FORTUNE_BONUS, 2.6
        )),
        T4_PIECE(Map.of(
                BonusCategory.EXPERIENCE_BONUS, 6.0,
                BonusCategory.TOKEN_BONUS, 5.4,
                BonusCategory.MONEY_BONUS, 4.5,
                BonusCategory.SELL_BONUS, 3.0,
                BonusCategory.FORTUNE_BONUS, 3.6
        )),
        T5_PIECE(Map.of(
                BonusCategory.EXPERIENCE_BONUS, 8.4,
                BonusCategory.TOKEN_BONUS, 7.6,
                BonusCategory.MONEY_BONUS, 6.3,
                BonusCategory.SELL_BONUS, 4.2,
                BonusCategory.FORTUNE_BONUS, 5.0
        )),
        T6_PIECE(Map.of(
                BonusCategory.EXPERIENCE_BONUS, 12.0,
                BonusCategory.TOKEN_BONUS, 10.8,
                BonusCategory.MONEY_BONUS, 9.0,
                BonusCategory.SELL_BONUS, 6.0,
                BonusCategory.FORTUNE_BONUS, 7.2
        ));

        private final Map<BonusCategory, Double> bonuses;

        ArmorEffects(Map<BonusCategory, Double> bonuses) {
            this.bonuses = bonuses;
        }

        public Map<BonusCategory, Double> getBonuses() {
            return bonuses;
        }

        public static ArmorEffects forTier(int tier) {
            return switch (tier) {
                case 1 -> T1_PIECE;
                case 2 -> T2_PIECE;
                case 3 -> T3_PIECE;
                case 4 -> T4_PIECE;
                case 5 -> T5_PIECE;
                case 6 -> T6_PIECE;
                default -> T1_PIECE;
            };
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // TIERS AVEC COULEURS CORRIGÉES
    // ═══════════════════════════════════════════════════════════════

    public enum ArmorTier {
        T1(1, "§7⚔ Garde Apprenti", Color.fromRGB(0x8C7853), "§7Novice"),      // Brun-gris
        T2(2, "§6⚡ Forge-Guerre", Color.fromRGB(0xB87333), "§6Adepte"),        // Brun-cuivre
        T3(3, "§6🔥 Maître-Forgeron", Color.fromRGB(0xCD7F32), "§6Expert"),     // Bronze
        T4(4, "§e✦ Champion Doré", Color.fromRGB(0xF4C430), "§e§lMaître"),     // Or
        T5(5, "§e☀ Seigneur Solaire", Color.fromRGB(0xFFD700), "§e§lLégendaire"), // Or brillant
        T6(6, "§a👑 Archonte Éternel", Color.fromRGB(0x9AFF9A), "§a§lMythique"); // Vert pale

        private final int level;
        private final String displayName;
        private final Color leatherColor;
        private final String rankDisplay;

        ArmorTier(int level, String displayName, Color leatherColor, String rankDisplay) {
            this.level = level;
            this.displayName = displayName;
            this.leatherColor = leatherColor;
            this.rankDisplay = rankDisplay;
        }

        public int getLevel() { return level; }
        public String getDisplayName() { return displayName; }
        public Color getLeatherColor() { return leatherColor; }
        public String getRankDisplay() { return rankDisplay; }

        public String getRPName() {
            return switch (this.level) {
                case 1 -> "Garde Apprenti";
                case 2 -> "Forge-Guerre";
                case 3 -> "Maître-Forgeron";
                case 4 -> "Champion Doré";
                case 5 -> "Seigneur Solaire";
                case 6 -> "Archonte Éternel";
                default -> "Armure Mystérieuse";
            };
        }

        public String getIcon() {
            return switch (this.level) {
                case 1 -> "§7⚔";
                case 2 -> "§6⚡";
                case 3 -> "§6🔥";
                case 4 -> "§e✦";
                case 5 -> "§e☀";
                case 6 -> "§a👑";
                default -> "§7◆";
            };
        }

        public static ArmorTier ofLevel(int lvl) {
            for (ArmorTier t : values()) if (t.level == lvl) return t;
            return T1;
        }
    }

    public enum ArmorPiece {
        HELMET("Casque", Material.LEATHER_HELMET, "⛑"),
        CHESTPLATE("Plastron", Material.LEATHER_CHESTPLATE, "🛡"),
        LEGGINGS("Jambières", Material.LEATHER_LEGGINGS, "👖"),
        BOOTS("Bottes", Material.LEATHER_BOOTS, "👢");

        private final String displayName;
        private final Material material;
        private final String icon;

        ArmorPiece(String displayName, Material material, String icon) {
            this.displayName = displayName;
            this.material = material;
            this.icon = icon;
        }

        public String getDisplayName() { return displayName; }
        public Material getMaterial() { return material; }
        public String getIcon() { return icon; }
    }

    public enum FragmentType {
        COPPER("Fragment de Cuivre Pur", Material.COPPER_INGOT, "🔶"),
        ALLOY("Fragment d'Alliage Mystique", Material.IRON_INGOT, "⚙"),
        ESSENCE("Essence d'Armure Céleste", Material.LAPIS_LAZULI, "💠");

        private final String display;
        private final Material icon;
        private final String emoji;

        FragmentType(String display, Material icon, String emoji) {
            this.display = display;
            this.icon = icon;
            this.emoji = emoji;
        }

        public String getDisplay() { return display; }
        public Material getIcon() { return icon; }
        public String getEmoji() { return emoji; }
    }

    // ═══════════════════════════════════════════════════════════════
    // CRÉATION D'ITEMS
    // ═══════════════════════════════════════════════════════════════

    public ItemStack createArmorPiece(ArmorTier tier, ArmorPiece piece) {
        ItemStack item = new ItemStack(piece.getMaterial());
        ItemMeta baseMeta = item.getItemMeta();

        if (baseMeta instanceof LeatherArmorMeta meta) {
            meta.setColor(tier.getLeatherColor());

            String fullName = tier.getIcon() + " " + piece.getIcon() + " §l" +
                    piece.getDisplayName() + " " + tier.getRPName();
            plugin.getGUIManager().applyName(meta, fullName);

            meta.getPersistentDataContainer().set(keySetId, PersistentDataType.STRING, "legendary_forge");
            meta.getPersistentDataContainer().set(keyTier, PersistentDataType.INTEGER, tier.getLevel());
            meta.getPersistentDataContainer().set(keyPiece, PersistentDataType.STRING, piece.name());

            plugin.getGUIManager().applyLore(meta, buildPieceLore(tier, piece));
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<String> buildPieceLore(ArmorTier tier, ArmorPiece piece) {
        Map<BonusCategory, Double> pieceBonus = getPerPieceBonus(tier);
        List<String> lore = new ArrayList<>();

        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        lore.add("§7Rang: " + tier.getRankDisplay());
        lore.add("§7Type: §e" + piece.getDisplayName());
        lore.add("");

        lore.add("§6⚡ §lPouvoirs de cette pièce:");
        for (var entry : pieceBonus.entrySet()) {
            String icon = getBonusIcon(entry.getKey());
            String value = String.format(Locale.FRANCE, "%.1f", entry.getValue());
            lore.add("§7" + icon + " " + entry.getKey().getFormattedName() + ": §a+" + value + "%");
        }

        lore.add("");
        lore.add("§6✦ §lSet complet: §a+20% §7sur tous les bonus");
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        return lore;
    }

    public ItemStack createBlueprint(ArmorTier tier) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();

        plugin.getGUIManager().applyName(meta,
                "§d📜 §lPlan d'Armure " + tier.getIcon() + " " + tier.getRPName());

        plugin.getGUIManager().applyLore(meta, List.of(
                "§7Nécessaire pour forger des pièces",
                "§7de ce palier d'armure",
                "",
                "§8Obtenu dans les caisses"
        ));

        meta.getPersistentDataContainer().set(keyBlueprintTier, PersistentDataType.INTEGER, tier.getLevel());
        paper.setItemMeta(meta);
        return paper;
    }

    public ItemStack createFragment(FragmentType type, int amount) {
        ItemStack stack = new ItemStack(type.getIcon(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§b" + type.getEmoji() + " §l" + type.getDisplay());
        plugin.getGUIManager().applyLore(meta, List.of("§7Utilisé pour la forge d'armures"));

        meta.getPersistentDataContainer().set(keyFragmentType, PersistentDataType.STRING, type.name());
        stack.setItemMeta(meta);
        return stack;
    }

    // ═══════════════════════════════════════════════════════════════
    // SYSTÈME DE BONUS
    // ═══════════════════════════════════════════════════════════════

    public Map<BonusCategory, Double> getPerPieceBonus(ArmorTier tier) {
        return ArmorEffects.forTier(tier.getLevel()).getBonuses();
    }

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

        // Bonus de set complet: +20%
        boolean isFullSet = isArmorPiece(h) && isArmorPiece(c) && isArmorPiece(l) && isArmorPiece(b);
        if (isFullSet) {
            ArmorTier th = getTier(h), tc = getTier(c), tl = getTier(l), tb = getTier(b);
            if (th != null && th == tc && tc == tl && tl == tb) {
                for (BonusCategory cat : new ArrayList<>(sum.keySet())) {
                    sum.put(cat, sum.get(cat) * 1.2);
                }
            }
        }

        return sum;
    }

    // ═══════════════════════════════════════════════════════════════
    // SYSTÈME DE CRAFT
    // ═══════════════════════════════════════════════════════════════

    public record CraftCost(int blueprintTier,
                            Map<FragmentType, Integer> fragments,
                            boolean requirePreviousPiece,
                            int experienceCost) { }

    public CraftCost getCraftCost(ArmorTier tier, ArmorPiece piece) {
        int t = tier.getLevel();
        Map<FragmentType, Integer> frags = new EnumMap<>(FragmentType.class);

        switch (piece) {
            case HELMET -> {
                frags.put(FragmentType.COPPER, 8 + t * 3);
                if (t >= 3) frags.put(FragmentType.ALLOY, 2 + t);
                if (t >= 5) frags.put(FragmentType.ESSENCE, 1 + (t - 4));
            }
            case CHESTPLATE -> {
                frags.put(FragmentType.COPPER, 12 + t * 4);
                if (t >= 2) frags.put(FragmentType.ALLOY, 3 + t);
                if (t >= 4) frags.put(FragmentType.ESSENCE, 2 + (t - 3));
            }
            case LEGGINGS -> {
                frags.put(FragmentType.COPPER, 10 + t * 3);
                if (t >= 3) frags.put(FragmentType.ALLOY, 2 + t);
                if (t >= 5) frags.put(FragmentType.ESSENCE, 1 + (t - 4));
            }
            case BOOTS -> {
                frags.put(FragmentType.COPPER, 6 + t * 2);
                if (t >= 4) frags.put(FragmentType.ALLOY, 1 + t);
                if (t >= 6) frags.put(FragmentType.ESSENCE, 1);
            }
        }

        int expCost = switch (piece) {
            case HELMET -> 100 * t * t;
            case CHESTPLATE -> 150 * t * t;
            case LEGGINGS -> 120 * t * t;
            case BOOTS -> 80 * t * t;
        };

        return new CraftCost(t, frags, t > 1, expCost);
    }

    // ═══════════════════════════════════════════════════════════════
    // MÉTHODES DE DÉTECTION
    // ═══════════════════════════════════════════════════════════════

    public boolean isArmorPiece(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(keySetId, PersistentDataType.STRING) &&
                "legendary_forge".equals(meta.getPersistentDataContainer().get(keySetId, PersistentDataType.STRING));
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

    private String getBonusIcon(BonusCategory category) {
        return switch (category) {
            case EXPERIENCE_BONUS -> "📚";
            case TOKEN_BONUS -> "🪙";
            case MONEY_BONUS -> "💰";
            case SELL_BONUS -> "📈";
            case FORTUNE_BONUS -> "💎";
            default -> "⚡";
        };
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


