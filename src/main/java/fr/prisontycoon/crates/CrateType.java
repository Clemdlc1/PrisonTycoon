package fr.prisontycoon.crates;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.autominers.AutominerType;
import fr.prisontycoon.boosts.BoostType;
import fr.prisontycoon.enchantments.EnchantmentBookManager;
import fr.prisontycoon.vouchers.VoucherType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static fr.prisontycoon.crates.CrateType.RewardType.*;

/**
 * Définit les types de crates et leurs récompenses avec probabilités.
 * L'intégralité de la configuration des récompenses est gérée dans ce fichier
 * via les classes internes statiques CrateReward et CrateRewardRegistry.
 */
public enum CrateType {

    VOTE("Vote", "§f", 0),
    COMMUNE("Commune", "§a", 1),          // Vert (LIME)
    PEU_COMMUNE("Peu Commune", "§9", 2),  // Bleu (BLUE)
    RARE("Rare", "§5", 3),                // Violet (PURPLE)
    LEGENDAIRE("Légendaire", "§6", 4),    // Orange (ORANGE)
    CRISTAL("Cristal", "§d", 5);          // Magenta/Rose (FUCHSIA)

    private final String displayName;
    private final String color;
    private final int tier;
    private List<CrateReward> rewards;

    CrateType(String displayName, String color, int tier) {
        this.displayName = displayName;
        this.color = color;
        this.tier = tier;
        this.rewards = null; // initialisation paresseuse pour éviter les cycles d'initialisation
    }

    // ================================================================= //
    //                      MÉTHODES PRINCIPALES
    // ================================================================= //

    public CrateReward selectRandomReward() {
        List<CrateReward> localRewards = getRewards();
        double totalWeight = localRewards.stream().mapToDouble(CrateReward::getProbability).sum();
        double randomValue = Math.random() * totalWeight;
        double currentWeight = 0.0;

        for (CrateReward reward : localRewards) {
            currentWeight += reward.getProbability();
            if (randomValue <= currentWeight) {
                return reward;
            }
        }
        return localRewards.getFirst();
    }

    public List<CrateReward> getAllRewards() {
        return new ArrayList<>(getRewards());
    }

    private List<CrateReward> getRewards() {
        if (this.rewards == null) {
            this.rewards = CrateRewardRegistry.getRewardsFor(this);
        }
        return this.rewards;
    }

    public ItemStack convertRewardToItem(CrateReward reward, PrisonTycoon plugin) {
        return switch (reward.getType()) {
            case CONTAINER -> plugin.getContainerManager().createContainer(reward.getContainerTier());
            case KEY -> createKey(reward.getKeyType(), reward.getRandomAmount());
            case CRISTAL_VIERGE -> plugin.getCristalManager().createCristalViergeApi(reward.getCristaltLevel());
            case LIVRE_UNIQUE -> {
                EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(reward.getBookType());
                yield plugin.getEnchantmentBookManager().createPhysicalEnchantmentBook(book);
            }
            case AUTOMINER -> {
                var autominerType = AutominerType.valueOf(reward.getAutominerType().toUpperCase());
                yield plugin.getAutominerManager().createAutominer(autominerType);
            }
            case VOUCHER -> {
                var voucherType = VoucherType.valueOf(reward.getVoucherType().toUpperCase());
                yield plugin.getVoucherManager().createVoucher(voucherType, reward.getRandomAmount());
            }
            case BOOST -> {
                var boostType = BoostType.valueOf(reward.getBoostType().toUpperCase());
                yield plugin.getBoostManager().createBoostItem(boostType, reward.getBoostMultiplier(), reward.getBoostDuration());
            }
            case FORGE_BLUEPRINT -> {
                var tier = reward.getBlueprintTier() != null ? reward.getBlueprintTier() : 1;
                yield plugin.getForgeManager().createBlueprint(fr.prisontycoon.managers.ForgeManager.ArmorTier.ofLevel(tier));
            }
        };
    }

    private ItemStack createKey(String keyType, int amount) {
        String keyColor = switch (keyType) {
            case "Cristal" -> "§d";
            case "Légendaire" -> "§6";
            case "Rare" -> "§5";
            case "Peu Commune" -> "§9";
            default -> "§f"; // "Commune"
        };

        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        var meta = key.getItemMeta();
        meta.setDisplayName(keyColor + "Clé " + keyType);
        meta.setLore(Arrays.asList("§7Clé de coffre " + keyColor + keyType, "§7Utilise cette clé pour ouvrir des coffres!"));
        key.setItemMeta(meta);
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    public int getTier() {
        return tier;
    }

    // ================================================================= //
    //                CLASSES ET ENUMS INTERNES
    // ================================================================= //

    public enum RewardType {
        CONTAINER, KEY, CRISTAL_VIERGE, VOUCHER, BOOST, LIVRE_UNIQUE, AUTOMINER, FORGE_BLUEPRINT
    }

    public static class CrateReward {
        private final RewardType type;
        private final int minAmount;
        private final int maxAmount;
        private final double probability;
        private final Integer containerTier;
        private final String keyType;
        private final String voucherType;
        private final String boostType;
        private final int boostMultiplier;
        private final int boostDuration;
        private final String bookType;
        private final String autominerType;
        private final int cristalLevel;
        private final Integer blueprintTier; // Tier du plan de forge

        private CrateReward(Builder builder) {
            this.type = builder.type;
            this.minAmount = builder.minAmount;
            this.maxAmount = builder.maxAmount;
            this.probability = builder.probability;
            this.containerTier = builder.containerTier;
            this.keyType = builder.keyType;
            this.voucherType = builder.voucherType;
            this.boostType = builder.boostType;
            this.boostMultiplier = builder.boostMultiplier;
            this.boostDuration = builder.boostDuration;
            this.bookType = builder.bookType;
            this.autominerType = builder.autominerType;
            this.cristalLevel = builder.cristalLevel;
            this.blueprintTier = builder.blueprintTier;
        }

        public static Builder builder(RewardType type, int minAmount, int maxAmount, double probability) {
            return new Builder(type, minAmount, maxAmount, probability);
        }

        public int getRandomAmount() {
            if (minAmount >= maxAmount) return minAmount;
            return minAmount + new Random().nextInt(maxAmount - minAmount + 1);
        }

        public RewardType getType() {
            return type;
        }

        public double getProbability() {
            return probability;
        }

        public int getContainerTier() {
            return containerTier != null ? containerTier : 1;
        }

        public String getKeyType() {
            return keyType;
        }

        public String getVoucherType() {
            return voucherType;
        }

        public String getBoostType() {
            return boostType;
        }

        public int getBoostMultiplier() {
            return boostMultiplier;
        }

        public int getBoostDuration() {
            return boostDuration;
        }

        public String getBookType() {
            return bookType;
        }

        public String getAutominerType() {
            return autominerType;
        }

        public int getCristaltLevel() {
            return cristalLevel;
        }

        public Integer getBlueprintTier() {
            return blueprintTier;
        }

        public static class Builder {
            private final RewardType type;
            private final int minAmount;
            private final int maxAmount;
            private final double probability;
            private Integer containerTier;
            private String keyType;
            private String voucherType;
            private String boostType;
            private int boostMultiplier;
            private int boostDuration;
            private String bookType;
            private String autominerType;
            private int cristalLevel;
            private Integer blueprintTier;

            private Builder(RewardType type, int minAmount, int maxAmount, double probability) {
                this.type = type;
                this.minAmount = minAmount;
                this.maxAmount = maxAmount;
                this.probability = probability;
            }

            public Builder containerTier(int tier) {
                this.containerTier = tier;
                return this;
            }

            public Builder keyType(String type) {
                this.keyType = type;
                return this;
            }

            public Builder voucherType(String type) {
                this.voucherType = type;
                return this;
            }

            public Builder book(String type) {
                this.bookType = type;
                return this;
            }

            public Builder autominer(String type) {
                this.autominerType = type;
                return this;
            }

            public Builder boost(String type, int multiplier, int duration) {
                this.boostType = type;
                this.boostMultiplier = multiplier;
                this.boostDuration = duration;
                return this;
            }

            public Builder cristalLevel(int level) {
                this.cristalLevel = level;
                return this;
            }

            public Builder blueprintTier(int tier) {
                this.blueprintTier = tier;
                return this;
            }

            public CrateReward build() {
                return new CrateReward(this);
            }
        }
    }

    private static final class CrateRewardRegistry {

        public static List<CrateReward> getRewardsFor(CrateType crateType) {
            return switch (crateType) {
                case VOTE -> getVoteRewards();
                case COMMUNE -> getCommuneRewards();
                case PEU_COMMUNE -> getPeuCommuneRewards();
                case RARE -> getRareRewards();
                case LEGENDAIRE -> getLegendaireRewards();
                case CRISTAL -> getCristalRewards();
            };
        }

        private static List<CrateReward> getVoteRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 1, 15.0).containerTier(1).build(),
                    CrateReward.builder(KEY, 1, 1, 10.0).keyType("Commune").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 1, 2, 8.0).cristalLevel(4).build(), // <-- CHANGEMENT ICI
                    CrateReward.builder(FORGE_BLUEPRINT, 1, 1, 4.0).blueprintTier(1).build(),
                    CrateReward.builder(VOUCHER, 1, 1, 5.0).voucherType("MONEY_BOOST_SMALL").build(),
                    CrateReward.builder(VOUCHER, 1, 1, 3.0).voucherType("PRINTER_SLOT").build(),
                    CrateReward.builder(BOOST, 1, 1, 2.0).boost("MONEY", 1, 1800).build()
            );
        }

        private static List<CrateReward> getCommuneRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 1, 15.0).containerTier(1).build(),
                    CrateReward.builder(KEY, 1, 1, 10.0).keyType("Commune").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 1, 2, 8.0).cristalLevel(5).build(), // <-- CHANGEMENT ICI
                    CrateReward.builder(FORGE_BLUEPRINT, 1, 1, 4.0).blueprintTier(2).build(),
                    CrateReward.builder(VOUCHER, 1, 1, 5.0).voucherType("MONEY_BOOST_SMALL").build(),
                    CrateReward.builder(VOUCHER, 1, 1, 4.0).voucherType("PRINTER_SLOT").build(),
                    CrateReward.builder(BOOST, 1, 1, 2.0).boost("MONEY", 1, 3600).build()
            );
        }

        private static List<CrateReward> getPeuCommuneRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 1, 20.0).containerTier(2).build(),
                    CrateReward.builder(KEY, 1, 2, 10.0).keyType("Commune").build(),
                    CrateReward.builder(KEY, 1, 1, 5.0).keyType("Peu Commune").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 2, 4, 6.0).cristalLevel(9).build(), // <-- CHANGEMENT ICI
                    CrateReward.builder(FORGE_BLUEPRINT, 1, 1, 3.0).blueprintTier(3).build(),
                    CrateReward.builder(VOUCHER, 1, 1, 3.0).voucherType("TOKEN_BOOST_MEDIUM").build(),
                    CrateReward.builder(VOUCHER, 1, 1, 5.0).voucherType("PRINTER_SLOT").build(),
                    CrateReward.builder(BOOST, 1, 1, 1.0).boost("TOKENS", 2, 7200).build()
            );
        }

        private static List<CrateReward> getRareRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 1, 18.0).containerTier(3).build(),
                    CrateReward.builder(KEY, 1, 2, 12.0).keyType("Peu Commune").build(),
                    CrateReward.builder(KEY, 1, 1, 8.0).keyType("Rare").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 4, 8, 10.0).cristalLevel(13).build(), // <-- CHANGEMENT ICI
                    CrateReward.builder(FORGE_BLUEPRINT, 1, 1, 2.5).blueprintTier(4).build(),
                    CrateReward.builder(LIVRE_UNIQUE, 1, 1, 4.0).book("greed").build(),
                    CrateReward.builder(VOUCHER, 1, 1, 2.0).voucherType("XP_BOOST_LARGE").build(),
                    CrateReward.builder(VOUCHER, 1, 1, 6.0).voucherType("PRINTER_SLOT").build(),
                    CrateReward.builder(BOOST, 1, 1, 1.0).boost("XP", 3, 10800).build()
            );
        }

        private static List<CrateReward> getLegendaireRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 2, 15.0).containerTier(4).build(),
                    CrateReward.builder(KEY, 1, 3, 15.0).keyType("Rare").build(),
                    CrateReward.builder(KEY, 1, 2, 10.0).keyType("Légendaire").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 8, 12, 12.0).cristalLevel(16).build(),
                    CrateReward.builder(FORGE_BLUEPRINT, 1, 1, 2.0).blueprintTier(5).build(),
                    CrateReward.builder(LIVRE_UNIQUE, 1, 1, 6.0).book("combustion").build(),
                    CrateReward.builder(AUTOMINER, 1, 1, 3.0).autominer("iron").build(),
                    CrateReward.builder(VOUCHER, 1, 1, 8.0).voucherType("PRINTER_SLOT").build(),
                    CrateReward.builder(BOOST, 1, 1, 1.0).boost("ALL", 2, 14400).build()
            );
        }

        private static List<CrateReward> getCristalRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 2, 12.0).containerTier(5).build(),
                    CrateReward.builder(KEY, 2, 5, 20.0).keyType("Légendaire").build(),
                    CrateReward.builder(KEY, 1, 3, 15.0).keyType("Cristal").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 12, 20, 15.0).cristalLevel(19).build(),
                    CrateReward.builder(FORGE_BLUEPRINT, 1, 1, 1.5).blueprintTier(6).build(),
                    CrateReward.builder(LIVRE_UNIQUE, 1, 1, 5.0).book("beaconbreaker").build(),
                    CrateReward.builder(AUTOMINER, 1, 1, 2.0).autominer("diamond").build(),
                    CrateReward.builder(VOUCHER, 1, 1, 10.0).voucherType("PRINTER_SLOT").build(),
                    CrateReward.builder(BOOST, 1, 1, 1.0).boost("ALL", 5, 21600).build()
            );
        }
    }
}