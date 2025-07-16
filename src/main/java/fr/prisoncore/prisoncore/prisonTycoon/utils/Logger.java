package fr.prisoncore.prisoncore.prisonTycoon.utils;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logger personnalisé pour le plugin
 * <p>
 * Ajoute des couleurs et du formatage aux messages de log.
 */
public class Logger {

    private final PrisonTycoon plugin;
    private final boolean debugEnabled;
    private final SimpleDateFormat timeFormat;

    public Logger(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.debugEnabled = plugin.getConfig().getBoolean("debug", false);
        this.timeFormat = new SimpleDateFormat("HH:mm:ss");
    }

    /**
     * Log d'information
     */
    public void info(String message) {
        plugin.getLogger().info(stripColors(message));
    }

    /**
     * Log d'avertissement
     */
    public void warning(String message) {
        plugin.getLogger().warning(stripColors(message));
    }

    /**
     * Log d'erreur
     */
    public void severe(String message) {
        plugin.getLogger().severe(stripColors(message));
    }

    /**
     * Log de debug (seulement en mode debug)
     */
    public void debug(String message) {
        if (debugEnabled) {
            String timestamp = timeFormat.format(new Date());
            plugin.getLogger().info("[DEBUG " + timestamp + "] " + stripColors(message));
        }
    }

    /**
     * Log avec stack trace
     */
    public void severe(String message, Throwable throwable) {
        severe(message);
        throwable.printStackTrace();
    }

    /**
     * Log de performance
     */
    public void performance(String operation, long timeMs) {
        if (debugEnabled) {
            debug("Performance: " + operation + " took " + timeMs + "ms");
        }
    }

    /**
     * Retire les codes couleur pour les logs
     */
    private String stripColors(String message) {
        return message.replaceAll("§[0-9a-fk-or]", "");
    }

    /**
     * Log conditionnel
     */
    public void logIf(boolean condition, String message) {
        if (condition) {
            info(message);
        }
    }

    /**
     * Log avec niveau personnalisé
     */
    public void log(LogLevel level, String message) {
        switch (level) {
            case DEBUG -> debug(message);
            case INFO -> info(message);
            case WARNING -> warning(message);
            case SEVERE -> severe(message);
        }
    }

    public enum LogLevel {
        DEBUG, INFO, WARNING, SEVERE
    }
}
