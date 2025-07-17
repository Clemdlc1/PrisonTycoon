package fr.prisontycoon.utils;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Gestionnaire pour l'enregistrement de tous les messages du chat
 */
public class ChatLogger {

    private final PrisonTycoon plugin;
    private final File logsFolder;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    // Queue pour l'écriture asynchrone des logs
    private final Queue<LogEntry> logQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean running = true;

    // Cache pour les statistiques
    private int totalMessagesLogged = 0;

    public ChatLogger(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.logsFolder = new File(plugin.getDataFolder(), "chat-logs");

        if (!logsFolder.exists()) {
            logsFolder.mkdirs();
            plugin.getPluginLogger().info("Dossier de logs créé: " + logsFolder.getName());
        }

        // Démarre le processus d'écriture asynchrone
        startLogWriter();
    }

    /**
     * Démarre le processus d'écriture asynchrone des logs
     */
    private void startLogWriter() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            while (!logQueue.isEmpty() && running) {
                LogEntry entry = logQueue.poll();
                if (entry != null) {
                    writeLogToFile(entry);
                }
            }
        }, 20L, 20L); // Toutes les secondes
    }

    /**
     * Enregistre un message de chat
     */
    public void logChatMessage(Player player, String message, String formattedMessage) {
        LogEntry entry = new LogEntry(
                System.currentTimeMillis(),
                LogType.CHAT,
                player.getName(),
                player.getUniqueId().toString(),
                message,
                formattedMessage
        );

        logQueue.offer(entry);
        totalMessagesLogged++;
    }

    /**
     * Enregistre une action administrative
     */
    public void logAdminAction(String admin, String action, String target, String details) {
        LogEntry entry = new LogEntry(
                System.currentTimeMillis(),
                LogType.ADMIN,
                admin,
                "N/A",
                action + " sur " + target,
                details
        );

        logQueue.offer(entry);
    }

    /**
     * Enregistre une commande
     */
    public void logCommand(Player player, String command) {
        LogEntry entry = new LogEntry(
                System.currentTimeMillis(),
                LogType.COMMAND,
                player.getName(),
                player.getUniqueId().toString(),
                command,
                "Commande exécutée"
        );

        logQueue.offer(entry);
    }

    /**
     * Écrit une entrée de log dans le fichier approprié
     */
    private void writeLogToFile(LogEntry entry) {
        try {
            String fileName = dateFormat.format(new Date(entry.timestamp)) + ".log";
            File logFile = new File(logsFolder, fileName);

            boolean isNewFile = !logFile.exists();

            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                if (isNewFile) {
                    writer.println("# Chat Log - " + dateFormat.format(new Date(entry.timestamp)));
                    writer.println("# Format: [TIME] [TYPE] [PLAYER] [UUID] MESSAGE");
                    writer.println("# ========================================");
                }

                String logLine = String.format("[%s] [%s] [%s] [%s] %s",
                        timeFormat.format(new Date(entry.timestamp)),
                        entry.type.name(),
                        entry.playerName,
                        entry.uuid,
                        entry.rawMessage
                );

                writer.println(logLine);

                // Pour les messages de chat, ajoute aussi le message formaté
                if (entry.type == LogType.CHAT && !entry.formattedMessage.equals(entry.rawMessage)) {
                    writer.println("    FORMATÉ: " + entry.formattedMessage);
                }
            }

        } catch (IOException e) {
            plugin.getPluginLogger().severe("Erreur lors de l'écriture du log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obtient le nombre total de messages loggés
     */
    public int getTotalMessagesLogged() {
        return totalMessagesLogged;
    }

    /**
     * Nettoie les anciens logs (garde seulement les X derniers jours)
     */
    public void cleanOldLogs(int daysToKeep) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File[] logFiles = logsFolder.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles == null) return;

            long cutoffTime = System.currentTimeMillis() - (daysToKeep * 24L * 60L * 60L * 1000L);
            int deletedCount = 0;

            for (File logFile : logFiles) {
                if (logFile.lastModified() < cutoffTime) {
                    if (logFile.delete()) {
                        deletedCount++;
                    }
                }
            }

            if (deletedCount > 0) {
                plugin.getPluginLogger().info("Nettoyage des logs: " + deletedCount + " fichiers supprimés (+ de " + daysToKeep + " jours)");
            }
        });
    }

    /**
     * Arrête le système de logs
     */
    public void shutdown() {
        running = false;

        // Écrit tous les logs restants
        while (!logQueue.isEmpty()) {
            LogEntry entry = logQueue.poll();
            if (entry != null) {
                writeLogToFile(entry);
            }
        }

        plugin.getPluginLogger().info("Système de logs arrêté, tous les logs ont été sauvegardés");
    }

    /**
     * Enum pour les types de logs
     */
    public enum LogType {
        CHAT, ADMIN, COMMAND
    }

    /**
     * Classe pour représenter une entrée de log
     */
    private static class LogEntry {
        final long timestamp;
        final LogType type;
        final String playerName;
        final String uuid;
        final String rawMessage;
        final String formattedMessage;

        LogEntry(long timestamp, LogType type, String playerName, String uuid, String rawMessage, String formattedMessage) {
            this.timestamp = timestamp;
            this.type = type;
            this.playerName = playerName;
            this.uuid = uuid;
            this.rawMessage = rawMessage;
            this.formattedMessage = formattedMessage;
        }
    }
}