package fr.prisoncore.prisoncore.prisonTycoon.utils;

import java.text.DecimalFormat;
import java.util.TreeMap;

/**
 * Utilitaire de formatage des nombres
 *
 * Formate les grands nombres avec les suffixes appropriés (K, M, B, T).
 * Utilisé pour l'affichage des monnaies et statistiques.
 */
public class NumberFormatter {

    private static final String[] SUFFIXES = {"", "K", "M", "B", "T", "Q", "Qi", "S", "Sp", "O", "N", "D"};
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#.##");

    // Map pour la conversion en chiffres romains
    private final static TreeMap<Integer, String> romanMap = new TreeMap<>();
    static {
        romanMap.put(1000, "M");
        romanMap.put(900, "CM");
        romanMap.put(500, "D");
        romanMap.put(400, "CD");
        romanMap.put(100, "C");
        romanMap.put(90, "XC");
        romanMap.put(50, "L");
        romanMap.put(40, "XL");
        romanMap.put(10, "X");
        romanMap.put(9, "IX");
        romanMap.put(5, "V");
        romanMap.put(4, "IV");
        romanMap.put(1, "I");
    }


    /**
     * Formate un nombre avec les suffixes appropriés
     *
     * @param number Le nombre à formater
     * @return Le nombre formaté avec suffixe si nécessaire
     */
    public static String format(long number) {
        if (number == 0) return "0";
        if (number < 0) return "-" + format(-number);
        if (number < 1000) return String.valueOf(number);

        int magnitude = 0;
        double value = number;

        while (value >= 1000 && magnitude < SUFFIXES.length - 1) {
            value /= 1000;
            magnitude++;
        }

        // Formatage avec précision appropriée
        String formatted;
        if (value >= 100) {
            formatted = String.format("%.0f", value);
        } else if (value >= 10) {
            formatted = String.format("%.1f", value);
        } else {
            formatted = String.format("%.2f", value);
        }

        // Supprime les zéros inutiles
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("\\.?0+$", "");
        }

        return formatted + SUFFIXES[magnitude];
    }

    /**
     * Formate un nombre avec couleur selon sa magnitude
     */
    public static String formatWithColor(long number) {
        String formatted = format(number);

        if (number >= 1_000_000_000_000L) return "§d" + formatted; // Trillion - Magenta
        if (number >= 1_000_000_000L) return "§c" + formatted;     // Billion - Rouge
        if (number >= 1_000_000L) return "§6" + formatted;         // Million - Or
        if (number >= 1_000L) return "§e" + formatted;             // Millier - Jaune

        return "§f" + formatted; // Moins de 1000 - Blanc
    }

    /**
     * Formate un nombre décimal
     */
    public static String format(double number) {
        if (number < 1000) {
            return DECIMAL_FORMAT.format(number);
        }
        return format((long) number);
    }

    /**
     * Parse une chaîne formatée en nombre
     */
    public static long parse(String formattedNumber) {
        if (formattedNumber == null || formattedNumber.trim().isEmpty()) {
            return 0;
        }

        formattedNumber = formattedNumber.trim().toUpperCase().replace(",", ".");

        // Gère les nombres négatifs
        boolean negative = formattedNumber.startsWith("-");
        if (negative) {
            formattedNumber = formattedNumber.substring(1);
        }

        // Extrait le suffixe
        long multiplier = 1;
        String numberPart = formattedNumber;

        for (int i = SUFFIXES.length - 1; i >= 1; i--) {
            if (formattedNumber.endsWith(SUFFIXES[i])) {
                multiplier = (long) Math.pow(1000, i);
                numberPart = formattedNumber.substring(0, formattedNumber.length() - SUFFIXES[i].length());
                break;
            }
        }

        try {
            double value = Double.parseDouble(numberPart);
            long result = Math.round(value * multiplier);
            return negative ? -result : result;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Formate un pourcentage
     */
    public static String formatPercentage(double percentage) {
        if (percentage < 0.001) {
            return String.format("%.4f%%", percentage);
        } else if (percentage < 0.01) {
            return String.format("%.3f%%", percentage);
        } else if (percentage < 1) {
            return String.format("%.2f%%", percentage);
        } else if (percentage < 10) {
            return String.format("%.1f%%", percentage);
        } else {
            return String.format("%.0f%%", percentage);
        }
    }

    /**
     * Formate une durée en millisecondes
     */
    public static String formatDuration(long durationMs) {
        if (durationMs < 1000) {
            return durationMs + "ms";
        }

        long seconds = durationMs / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }

        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes < 60) {
            if (seconds == 0) {
                return minutes + "min";
            }
            return String.format("%dmin %ds", minutes, seconds);
        }

        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours < 24) {
            if (minutes == 0) {
                return hours + "h";
            }
            return String.format("%dh %dmin", hours, minutes);
        }

        long days = hours / 24;
        hours = hours % 24;
        if (hours == 0) {
            return days + "j";
        }
        return String.format("%dj %dh", days, hours);
    }

    /**
     * Formate un nombre avec séparateurs de milliers
     */
    public static String formatWithSeparators(long number) {
        return String.format("%,d", number);
    }

    /**
     * Convertit un entier en sa représentation en chiffres romains.
     * Gère les nombres de 1 à 3999.
     *
     * @param number L'entier à convertir.
     * @return La chaîne de caractères en chiffres romains.
     */
    public static String formatRoman(int number) {
        if (number <= 0 || number >= 4000) {
            // Les chiffres romains ne gèrent traditionnellement pas le zéro, les négatifs ou les très grands nombres
            return String.valueOf(number);
        }
        // Trouve la plus grande clé <= au nombre
        int key =  romanMap.floorKey(number);
        if (number == key) {
            return romanMap.get(number);
        }
        // Appel récursif pour le reste du nombre
        return romanMap.get(key) + formatRoman(number - key);
    }

    /**
     * Vérifie si une chaîne est un nombre valide
     */
    public static boolean isValidNumber(String str) {
        if (str == null || str.trim().isEmpty()) return false;

        try {
            parse(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}