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

    COMMUNE("Commune", "§f", 1),
    PEU_COMMUNE("Peu Commune", "§9", 2),
    RARE("Rare", "§5", 3),
    LEGENDAIRE("Légendaire", "§6", 4),
    CRISTAL("Cristal", "§d", 5);

    private final String displayName;
    private final String color;
    private final int tier;
    private final List<CrateReward> rewards;

    CrateType(String displayName, String color, int tier) {
        this.displayName = displayName;
        this.color = color;
        this.tier = tier;
        this.rewards = CrateRewardRegistry.getRewardsFor(this);
    }

    // ================================================================= //
    //                      MÉTHODES PRINCIPALES
    // ================================================================= //

    public CrateReward selectRandomReward() {
        double totalWeight = rewards.stream().mapToDouble(CrateReward::getProbability).sum();
        double randomValue = Math.random() * totalWeight;
        double currentWeight = 0.0;

        for (CrateReward reward : rewards) {
            currentWeight += reward.getProbability();
            if (randomValue <= currentWeight) {
                return reward;
            }
        }
        return rewards.get(0);
    }

    public List<CrateReward> getAllRewards() {
        return new ArrayList<>(rewards);
    }

    public ItemStack convertRewardToItem(CrateReward reward, PrisonTycoon plugin) {
        switch (reward.getType()) {
            case CONTAINER: {
                return plugin.getContainerManager().createContainer(reward.getContainerTier());
            }
            case KEY: {
                return createKey(reward.getKeyType(), reward.getRandomAmount());
            }
            case CRISTAL_VIERGE: {
                return plugin.getCristalManager().createCristalViergeApi(reward.getCristaltLevel());
            }
            case LIVRE_UNIQUE: {
                EnchantmentBookManager.EnchantmentBook book = plugin.getEnchantmentBookManager().getEnchantmentBook(reward.getBookType());
                return plugin.getEnchantmentBookManager().createPhysicalEnchantmentBook(book);
            }
            case AUTOMINER: {
                var autominerType = AutominerType.valueOf(reward.getAutominerType().toUpperCase());
                return plugin.getAutominerManager().createAutominer(autominerType);
            }
            case VOUCHER: {
                var voucherType = VoucherType.valueOf(reward.getVoucherType().toUpperCase());
                return plugin.getVoucherManager().createVoucher(voucherType, reward.getRandomAmount());
            }
            case BOOST: {
                var boostType = BoostType.valueOf(reward.getBoostType().toUpperCase());
                return plugin.getBoostManager().createBoostItem(boostType, reward.getBoostMultiplier(), reward.getBoostDuration());

            }
        }
        return null;
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
        CONTAINER, KEY, CRISTAL_VIERGE, VOUCHER, BOOST, LIVRE_UNIQUE, AUTOMINER // <-- CHANGEMENT ICI
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

            public CrateReward build() {
                return new CrateReward(this);
            }
        }
    }

    private static final class CrateRewardRegistry {

        public static List<CrateReward> getRewardsFor(CrateType crateType) {
            return switch (crateType) {
                case COMMUNE -> getCommuneRewards();
                case PEU_COMMUNE -> getPeuCommuneRewards();
                case RARE -> getRareRewards();
                case LEGENDAIRE -> getLegendaireRewards();
                case CRISTAL -> getCristalRewards();
            };
        }

        private static List<CrateReward> getCommuneRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 1, 15.0).containerTier(1).build(),
                    CrateReward.builder(KEY, 1, 1, 10.0).keyType("Commune").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 1, 2, 8.0).cristalLevel(5).build(), // <-- CHANGEMENT ICI
                    CrateReward.builder(VOUCHER, 1, 1, 5.0).voucherType("MONEY_BOOST_SMALL").build(),
                    CrateReward.builder(BOOST, 1, 1, 2.0).boost("MONEY", 1, 3600).build()
            );
        }

        private static List<CrateReward> getPeuCommuneRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 1, 20.0).containerTier(2).build(),
                    CrateReward.builder(KEY, 1, 2, 10.0).keyType("Commune").build(),
                    CrateReward.builder(KEY, 1, 1, 5.0).keyType("Peu Commune").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 2, 4, 6.0).cristalLevel(9).build(), // <-- CHANGEMENT ICI
                    CrateReward.builder(VOUCHER, 1, 1, 3.0).voucherType("TOKEN_BOOST_MEDIUM").build(),
                    CrateReward.builder(BOOST, 1, 1, 1.0).boost("TOKENS", 2, 7200).build()
            );
        }

        private static List<CrateReward> getRareRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 1, 18.0).containerTier(3).build(),
                    CrateReward.builder(KEY, 1, 2, 12.0).keyType("Peu Commune").build(),
                    CrateReward.builder(KEY, 1, 1, 8.0).keyType("Rare").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 4, 8, 10.0).cristalLevel(13).build(), // <-- CHANGEMENT ICI
                    CrateReward.builder(LIVRE_UNIQUE, 1, 1, 4.0).book("greed").build(),
                    CrateReward.builder(VOUCHER, 1, 1, 2.0).voucherType("XP_BOOST_LARGE").build(),
                    CrateReward.builder(BOOST, 1, 1, 1.0).boost("XP", 3, 10800).build()
            );
        }

        private static List<CrateReward> getLegendaireRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 2, 15.0).containerTier(4).build(),
                    CrateReward.builder(KEY, 1, 3, 15.0).keyType("Rare").build(),
                    CrateReward.builder(KEY, 1, 2, 10.0).keyType("Légendaire").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 8, 12, 12.0).cristalLevel(16).build(),
                    CrateReward.builder(LIVRE_UNIQUE, 1, 1, 6.0).book("combustion").build(),
                    CrateReward.builder(AUTOMINER, 1, 1, 3.0).autominer("iron").build(),
                    CrateReward.builder(BOOST, 1, 1, 1.0).boost("ALL", 2, 14400).build()
            );
        }

        private static List<CrateReward> getCristalRewards() {
            return List.of(
                    CrateReward.builder(CONTAINER, 1, 2, 12.0).containerTier(5).build(),
                    CrateReward.builder(KEY, 2, 5, 20.0).keyType("Légendaire").build(),
                    CrateReward.builder(KEY, 1, 3, 15.0).keyType("Cristal").build(),
                    CrateReward.builder(CRISTAL_VIERGE, 12, 20, 15.0).cristalLevel(19).build(),
                    CrateReward.builder(LIVRE_UNIQUE, 1, 1, 5.0).book("beaconbreaker").build(),
                    CrateReward.builder(AUTOMINER, 1, 1, 2.0).autominer("diamond").build(),
                    CrateReward.builder(BOOST, 1, 1, 1.0).boost("ALL", 5, 21600).build()
            );
        }
    }
}