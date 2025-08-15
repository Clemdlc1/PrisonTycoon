package fr.prisontycoon.data;

import org.bukkit.Material;
import java.math.BigInteger;

/**
 * Énumération des 50 tiers d'imprimantes à argent
 */
public enum PrinterTier {
    
    TIER_1(1, BigInteger.valueOf(1000L), Material.PAPER, "§fBillet de 1,000", "§7Valeur: §e1,000 coins", 30),
    TIER_2(2, BigInteger.valueOf(2500L), Material.PAPER, "§fBillet de 2,500", "§7Valeur: §e2,500 coins", 28),
    TIER_3(3, BigInteger.valueOf(5000L), Material.PAPER, "§fBillet de 5,000", "§7Valeur: §e5,000 coins", 26),
    TIER_4(4, BigInteger.valueOf(10000L), Material.PAPER, "§fBillet de 10,000", "§7Valeur: §e10,000 coins", 24),
    TIER_5(5, BigInteger.valueOf(25000L), Material.PAPER, "§fBillet de 25,000", "§7Valeur: §e25,000 coins", 22),
    TIER_6(6, BigInteger.valueOf(50000L), Material.PAPER, "§fBillet de 50,000", "§7Valeur: §e50,000 coins", 20),
    TIER_7(7, BigInteger.valueOf(100000L), Material.PAPER, "§fBillet de 100,000", "§7Valeur: §e100,000 coins", 18),
    TIER_8(8, BigInteger.valueOf(250000L), Material.PAPER, "§fBillet de 250,000", "§7Valeur: §e250,000 coins", 16),
    TIER_9(9, BigInteger.valueOf(500000L), Material.PAPER, "§fBillet de 500,000", "§7Valeur: §e500,000 coins", 14),
    TIER_10(10, BigInteger.valueOf(1000000L), Material.PAPER, "§fBillet de 1,000,000", "§7Valeur: §e1,000,000 coins", 12),
    TIER_11(11, BigInteger.valueOf(2500000L), Material.PAPER, "§fBillet de 2,500,000", "§7Valeur: §e2,500,000 coins", 11),
    TIER_12(12, BigInteger.valueOf(5000000L), Material.PAPER, "§fBillet de 5,000,000", "§7Valeur: §e5,000,000 coins", 10),
    TIER_13(13, BigInteger.valueOf(10000000L), Material.PAPER, "§fBillet de 10,000,000", "§7Valeur: §e10,000,000 coins", 9),
    TIER_14(14, BigInteger.valueOf(25000000L), Material.PAPER, "§fBillet de 25,000,000", "§7Valeur: §e25,000,000 coins", 8),
    TIER_15(15, BigInteger.valueOf(50000000L), Material.PAPER, "§fBillet de 50,000,000", "§7Valeur: §e50,000,000 coins", 7),
    TIER_16(16, BigInteger.valueOf(100000000L), Material.PAPER, "§fBillet de 100,000,000", "§7Valeur: §e100,000,000 coins", 6),
    TIER_17(17, BigInteger.valueOf(250000000L), Material.PAPER, "§fBillet de 250,000,000", "§7Valeur: §e250,000,000 coins", 5),
    TIER_18(18, BigInteger.valueOf(500000000L), Material.PAPER, "§fBillet de 500,000,000", "§7Valeur: §e500,000,000 coins", 4),
    TIER_19(19, BigInteger.valueOf(1000000000L), Material.PAPER, "§fBillet de 1,000,000,000", "§7Valeur: §e1,000,000,000 coins", 3),
    TIER_20(20, BigInteger.valueOf(2500000000L), Material.PAPER, "§fBillet de 2,500,000,000", "§7Valeur: §e2,500,000,000 coins", 2),
    TIER_21(21, BigInteger.valueOf(5000000000L), Material.PAPER, "§fBillet de 5,000,000,000", "§7Valeur: §e5,000,000,000 coins", 2),
    TIER_22(22, BigInteger.valueOf(10000000000L), Material.PAPER, "§fBillet de 10,000,000,000", "§7Valeur: §e10,000,000,000 coins", 2),
    TIER_23(23, BigInteger.valueOf(25000000000L), Material.PAPER, "§fBillet de 25,000,000,000", "§7Valeur: §e25,000,000,000 coins", 1),
    TIER_24(24, BigInteger.valueOf(50000000000L), Material.PAPER, "§fBillet de 50,000,000,000", "§7Valeur: §e50,000,000,000 coins", 1),
    TIER_25(25, BigInteger.valueOf(100000000000L), Material.PAPER, "§fBillet de 100,000,000,000", "§7Valeur: §e100,000,000,000 coins", 1),
    TIER_26(26, BigInteger.valueOf(250000000000L), Material.PAPER, "§fBillet de 250,000,000,000", "§7Valeur: §e250,000,000,000 coins", 1),
    TIER_27(27, BigInteger.valueOf(500000000000L), Material.PAPER, "§fBillet de 500,000,000,000", "§7Valeur: §e500,000,000,000 coins", 1),
    TIER_28(28, BigInteger.valueOf(1000000000000L), Material.PAPER, "§fBillet de 1,000,000,000,000", "§7Valeur: §e1,000,000,000,000 coins", 1),
    TIER_29(29, BigInteger.valueOf(2500000000000L), Material.PAPER, "§fBillet de 2,500,000,000,000", "§7Valeur: §e2,500,000,000,000 coins", 1),
    TIER_30(30, BigInteger.valueOf(5000000000000L), Material.PAPER, "§fBillet de 5,000,000,000,000", "§7Valeur: §e5,000,000,000,000 coins", 1),
    TIER_31(31, BigInteger.valueOf(10000000000000L), Material.PAPER, "§fBillet de 10,000,000,000,000", "§7Valeur: §e10,000,000,000,000 coins", 1),
    TIER_32(32, BigInteger.valueOf(25000000000000L), Material.PAPER, "§fBillet de 25,000,000,000,000", "§7Valeur: §e25,000,000,000,000 coins", 1),
    TIER_33(33, BigInteger.valueOf(50000000000000L), Material.PAPER, "§fBillet de 50,000,000,000,000", "§7Valeur: §e50,000,000,000,000 coins", 1),
    TIER_34(34, BigInteger.valueOf(100000000000000L), Material.PAPER, "§fBillet de 100,000,000,000,000", "§7Valeur: §e100,000,000,000,000 coins", 1),
    TIER_35(35, BigInteger.valueOf(250000000000000L), Material.PAPER, "§fBillet de 250,000,000,000,000", "§7Valeur: §e250,000,000,000,000 coins", 1),
    TIER_36(36, BigInteger.valueOf(500000000000000L), Material.PAPER, "§fBillet de 500,000,000,000,000", "§7Valeur: §e500,000,000,000,000 coins", 1),
    TIER_37(37, BigInteger.valueOf(1000000000000000L), Material.PAPER, "§fBillet de 1,000,000,000,000,000", "§7Valeur: §e1,000,000,000,000,000 coins", 1),
    TIER_38(38, BigInteger.valueOf(2500000000000000L), Material.PAPER, "§fBillet de 2,500,000,000,000,000", "§7Valeur: §e2,500,000,000,000,000 coins", 1),
    TIER_39(39, BigInteger.valueOf(5000000000000000L), Material.PAPER, "§fBillet de 5,000,000,000,000,000", "§7Valeur: §e5,000,000,000,000,000 coins", 1),
    TIER_40(40, BigInteger.valueOf(10000000000000000L), Material.PAPER, "§fBillet de 10,000,000,000,000,000", "§7Valeur: §e10,000,000,000,000,000 coins", 1),
    TIER_41(41, BigInteger.valueOf(25000000000000000L), Material.PAPER, "§fBillet de 25,000,000,000,000,000", "§7Valeur: §e25,000,000,000,000,000 coins", 1),
    TIER_42(42, BigInteger.valueOf(50000000000000000L), Material.PAPER, "§fBillet de 50,000,000,000,000,000", "§7Valeur: §e50,000,000,000,000,000 coins", 1),
    TIER_43(43, BigInteger.valueOf(100000000000000000L), Material.PAPER, "§fBillet de 100,000,000,000,000,000", "§7Valeur: §e100,000,000,000,000,000 coins", 1),
    TIER_44(44, BigInteger.valueOf(250000000000000000L), Material.PAPER, "§fBillet de 250,000,000,000,000,000", "§7Valeur: §e250,000,000,000,000,000 coins", 1),
    TIER_45(45, BigInteger.valueOf(500000000000000000L), Material.PAPER, "§fBillet de 500,000,000,000,000,000", "§7Valeur: §e500,000,000,000,000,000 coins", 1),
    TIER_46(46, BigInteger.valueOf(1000000000000000000L), Material.PAPER, "§fBillet de 1,000,000,000,000,000,000", "§7Valeur: §e1,000,000,000,000,000,000 coins", 1),
    TIER_47(47, BigInteger.valueOf(2500000000000000000L), Material.PAPER, "§fBillet de 2,500,000,000,000,000,000", "§7Valeur: §e2,500,000,000,000,000,000 coins", 1),
    TIER_48(48, BigInteger.valueOf(5000000000000000000L), Material.PAPER, "§fBillet de 5,000,000,000,000,000,000", "§7Valeur: §e5,000,000,000,000,000,000 coins", 1),
    TIER_49(49, new BigInteger("10000000000000000000"), Material.PAPER, "§fBillet de 10,000,000,000,000,000,000", "§7Valeur: §e10,000,000,000,000,000,000 coins", 1),
    TIER_50(50, new BigInteger("25000000000000000000"), Material.PAPER, "§fBillet de 25,000,000,000,000,000,000", "§7Valeur: §e25,000,000,000,000,000,000 coins", 1);
    
    private final int tier;
    private final BigInteger billValue;
    private final Material billMaterial;
    private final String billName;
    private final String billLore;
    private final int generationIntervalSeconds;
    
    PrinterTier(int tier, BigInteger billValue, Material billMaterial, String billName, String billLore, int generationIntervalSeconds) {
        this.tier = tier;
        this.billValue = billValue;
        this.billMaterial = billMaterial;
        this.billName = billName;
        this.billLore = billLore;
        this.generationIntervalSeconds = generationIntervalSeconds;
    }
    
    // Getters
    public int getTier() { return tier; }
    public BigInteger getBillValue() { return billValue; }
    public Material getBillMaterial() { return billMaterial; }
    public String getBillName() { return billName; }
    public String getBillLore() { return billLore; }
    public int getGenerationIntervalSeconds() { return generationIntervalSeconds; }
    
    /**
     * Obtient un tier par son numéro
     */
    public static PrinterTier getByTier(int tier) {
        for (PrinterTier printerTier : values()) {
            if (printerTier.tier == tier) {
                return printerTier;
            }
        }
        return null;
    }
    
    /**
     * Obtient le prix d'achat d'une imprimante de ce tier
     */
    public BigInteger getPurchasePrice() {
        return billValue.multiply(BigInteger.valueOf(100)); // Prix = 100x la valeur du billet
    }
}
