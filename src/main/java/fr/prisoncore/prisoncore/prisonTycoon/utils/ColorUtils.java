package fr.prisoncore.prisoncore.prisonTycoon.utils;

/**
 * Utilitaires pour les couleurs et le formatage de texte
 */
class ColorUtils {

    // Codes couleur Minecraft
    public static final String BLACK = "§0";
    public static final String DARK_BLUE = "§1";
    public static final String DARK_GREEN = "§2";
    public static final String DARK_AQUA = "§3";
    public static final String DARK_RED = "§4";
    public static final String DARK_PURPLE = "§5";
    public static final String GOLD = "§6";
    public static final String GRAY = "§7";
    public static final String DARK_GRAY = "§8";
    public static final String BLUE = "§9";
    public static final String GREEN = "§a";
    public static final String AQUA = "§b";
    public static final String RED = "§c";
    public static final String LIGHT_PURPLE = "§d";
    public static final String YELLOW = "§e";
    public static final String WHITE = "§f";

    // Codes de formatage
    public static final String BOLD = "§l";
    public static final String ITALIC = "§o";
    public static final String UNDERLINE = "§n";
    public static final String STRIKETHROUGH = "§m";
    public static final String MAGIC = "§k";
    public static final String RESET = "§r";

    /**
     * Traduit les codes couleur alternatifs (&) en codes Minecraft (§)
     */
    public static String translateColors(String message) {
        if (message == null) return null;
        return message.replace('&', '§');
    }

    /**
     * Retire tous les codes couleur d'un message
     */
    public static String stripColors(String message) {
        if (message == null) return null;
        return message.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * Centre un texte dans une ligne de longueur donnée
     */
    public static String centerText(String text, int lineLength) {
        if (text == null) return "";

        String stripped = stripColors(text);
        if (stripped.length() >= lineLength) return text;

        int spaces = (lineLength - stripped.length()) / 2;
        return " ".repeat(spaces) + text;
    }

    /**
     * Crée une ligne de séparation
     */
    public static String createSeparator(String color, String character, int length) {
        return color + character.repeat(length);
    }

    /**
     * Applique un dégradé de couleur à un texte
     */
    public static String gradient(String text, String startColor, String endColor) {
        // Implémentation simple - peut être améliorée
        return startColor + text + endColor;
    }

    /**
     * Obtient une couleur selon un pourcentage (rouge -> jaune -> vert)
     */
    public static String getPercentageColor(double percentage) {
        if (percentage >= 75) return GREEN;
        if (percentage >= 50) return YELLOW;
        if (percentage >= 25) return GOLD;
        return RED;
    }

    /**
     * Colore un nombre selon sa valeur
     */
    public static String colorizeNumber(long number) {
        if (number > 1000000) return GREEN + NumberFormatter.format(number);
        if (number > 10000) return YELLOW + NumberFormatter.format(number);
        if (number > 1000) return GOLD + NumberFormatter.format(number);
        return WHITE + NumberFormatter.format(number);
    }

    /**
     * Formate un texte avec des couleurs aléatoires (effet magique)
     */
    public static String rainbow(String text) {
        String[] colors = {RED, GOLD, YELLOW, GREEN, AQUA, BLUE, LIGHT_PURPLE};
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != ' ') {
                result.append(colors[i % colors.length]);
            }
            result.append(c);
        }

        return result.toString();
    }
}