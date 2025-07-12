package fr.prisoncore.prisoncore.prisonTycoon.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * Classe utilitaire pour l'envoi de messages formatés à la console.
 *
 * Centralise le formatage des logs pour assurer une cohérence visuelle
 * dans la console du serveur, avec un préfixe et des couleurs distinctes
 * pour chaque type de message (information, succès, avertissement, erreur).
 *
 * @author PrisonCore
 */
public final class MessageUtil {

    // Le préfixe qui apparaîtra devant chaque message du plugin dans la console.
    private static final String PREFIX = "§8[§bPrisonTycoon§8] ";

    /**
     * Constructeur privé pour empêcher l'instanciation de cette classe utilitaire.
     */
    private MessageUtil() {
        // Cette classe ne doit pas être instanciée.
    }

    /**
     * Affiche un message d'information standard dans la console.
     * Couleur: Blanc/Gris par défaut.
     *
     * @param message Le message à afficher.
     */
    public static void logInfo(String message) {
        Bukkit.getLogger().info(PREFIX + ChatColor.WHITE + message);
    }

    /**
     * Affiche un message de succès dans la console.
     * Couleur: Vert.
     *
     * @param message Le message de succès à afficher.
     */
    public static void logSuccess(String message) {
        Bukkit.getLogger().info(PREFIX + ChatColor.GREEN + message);
    }

    /**
     * Affiche un message d'avertissement dans la console.
     * Utilisé pour les erreurs non critiques ou les problèmes de configuration mineurs.
     * Couleur: Jaune.
     *
     * @param message Le message d'avertissement à afficher.
     */
    public static void logWarning(String message) {
        Bukkit.getLogger().warning(PREFIX + ChatColor.YELLOW + message);
    }

    /**
     * Affiche un message d'erreur critique dans la console.
     * Utilisé pour les erreurs qui peuvent empêcher le bon fonctionnement du plugin.
     * Couleur: Rouge.
     *
     * @param message Le message d'erreur à afficher.
     */
    public static void logError(String message) {
        Bukkit.getLogger().severe(PREFIX + ChatColor.RED + message);
    }
}