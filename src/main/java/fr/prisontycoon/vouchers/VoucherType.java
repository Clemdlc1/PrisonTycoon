package fr.prisontycoon.vouchers;

import fr.prisontycoon.utils.NumberFormatter;

/**
 * Types de vouchers disponibles avec leurs valeurs par tier
 */
public enum VoucherType {

    TOKENS("Tokens", "§b💎 Voucher Tokens", "§7Donne des tokens au joueur"),
    COINS("Coins", "§6💰 Voucher Coins", "§7Donne des coins au joueur"),
    EXPERIENCE("Experience", "§a⭐ Voucher Expérience", "§7Donne de l'expérience au joueur"),
    JOB_XP("JobXP", "§d🔨 Voucher XP Métier", "§7Donne de l'XP au métier actuel"),
    PRINTER_SLOT("PrinterSlot", "§6🖨️ Slot d'Imprimante", "§7Augmente votre limite d'imprimantes de +1");

    private final String displayName;
    private final String itemName;
    private final String description;

    VoucherType(String displayName, String itemName, String description) {
        this.displayName = displayName;
        this.itemName = itemName;
        this.description = description;
    }

    /**
     * Calcule la valeur pour un tier donné
     */
    public long getValue(int tier) {
        if (tier < 1 || tier > 10) return 0;

        return switch (this) {
            case TOKENS -> switch (tier) {
                case 1 -> 1000L;
                case 2 -> 5000L;
                case 3 -> 15000L;
                case 4 -> 50000L;
                case 5 -> 150000L;
                case 6 -> 500000L;
                case 7 -> 1500000L;
                case 8 -> 5000000L;
                case 9 -> 15000000L;
                case 10 -> 50000000L;
                default -> 0L;
            };
            case COINS -> switch (tier) {
                case 1 -> 5000L;
                case 2 -> 25000L;
                case 3 -> 75000L;
                case 4 -> 250000L;
                case 5 -> 750000L;
                case 6 -> 2500000L;
                case 7 -> 7500000L;
                case 8 -> 25000000L;
                case 9 -> 75000000L;
                case 10 -> 250000000L;
                default -> 0L;
            };
            case EXPERIENCE -> switch (tier) {
                case 1 -> 10000L;
                case 2 -> 50000L;
                case 3 -> 150000L;
                case 4 -> 500000L;
                case 5 -> 1500000L;
                case 6 -> 5000000L;
                case 7 -> 15000000L;
                case 8 -> 50000000L;
                case 9 -> 150000000L;
                case 10 -> 500000000L;
                default -> 0L;
            };
            case JOB_XP -> switch (tier) {
                case 1 -> 500L;
                case 2 -> 2500L;
                case 3 -> 7500L;
                case 4 -> 25000L;
                case 5 -> 75000L;
                case 6 -> 250000L;
                case 7 -> 750000L;
                case 8 -> 2500000L;
                case 9 -> 7500000L;
                case 10 -> 25000000L;
                default -> 0L;
            };
            case PRINTER_SLOT -> 1L; // chaque voucher donne +1 slot, peu importe le tier
        };
    }

    /**
     * Retourne la couleur selon le tier
     */
    public String getTierColor(int tier) {
        return switch (tier) {
            case 1, 2 -> "§7";         // Gris
            case 3, 4 -> "§a";         // Vert
            case 5, 6 -> "§b";         // Aqua
            case 7, 8 -> "§d";         // Rose
            case 9, 10 -> "§6";        // Orange
            default -> "§f";           // Blanc
        };
    }

    /**
     * Retourne le nom du tier
     */
    public String getTierName(int tier) {
        return switch (tier) {
            case 1, 2 -> "Commun";
            case 3, 4 -> "Rare";
            case 5, 6 -> "Épique";
            case 7, 8 -> "Légendaire";
            case 9, 10 -> "Mythique";
            default -> "Inconnu";
        };
    }

    /**
     * Retourne le nom complet du voucher
     */
    public String getFullName(int tier) {
        return getTierColor(tier) + itemName + " " + getTierName(tier) + " " + tier;
    }

    /**
     * Retourne la description avec la valeur
     */
    public String getValueDescription(int tier) {
        if (this == PRINTER_SLOT) {
            return "§7Effet: §a+1 slot d'imprimante";
        }
        long value = getValue(tier);
        return "§7Valeur: " + getTierColor(tier) + NumberFormatter.formatWithColor(value) + " " + displayName;
    }

    // Getters
    public String getDisplayName() {
        return displayName;
    }

    public String getItemName() {
        return itemName;
    }

    public String getDescription() {
        return description;
    }
}