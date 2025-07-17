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
        loadTotalMessages();
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
     * Affiche les logs à un administrateur
     */
    public void showLogs(CommandSender sender, String playerFilter, int page) {
        // Cette méthode charge et affiche les logs de manière paginée
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<String> logs = loadLogs(playerFilter, 20, page);

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    sender.sendMessage("§6§l📋 LOGS DU CHAT " + (playerFilter != null ? "- " + playerFilter.toUpperCase() : "") + " (Page " + page + ")");
                    sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

                    if (logs.isEmpty()) {
                        sender.sendMessage("§c❌ Aucun log trouvé.");
                    } else {
                        for (String log : logs) {
                            sender.sendMessage(log);
                        }
                    }

                    sender.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    sender.sendMessage("§7Utilisez §e/adminchat logs " + (playerFilter != null ? playerFilter + " " : "") + (page + 1) + " §7pour la page suivante");
                });

            } catch (Exception e) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§c❌ Erreur lors du chargement des logs: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Charge les logs depuis les fichiers
     */
    private List<String> loadLogs(String playerFilter, int limit, int page) {
        List<String> results = new ArrayList<>();
        int skip = (page - 1) * limit;
        int found = 0;
        int skipped = 0;

        // Récupère les fichiers de log triés par date (plus récent en premier)
        File[] logFiles = logsFolder.listFiles((dir, name) -> name.endsWith(".log"));
        if (logFiles == null) return results;

        Arrays.sort(logFiles, (f1, f2) -> f2.getName().compareTo(f1.getName()));

        for (File logFile : logFiles) {
            if (found >= limit) break;

            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                List<String> fileLines = new ArrayList<>();
                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.trim().isEmpty()) continue;
                    fileLines.add(line);
                }

                // Inverse l'ordre pour avoir les plus récents en premier
                Collections.reverse(fileLines);

                for (String logLine : fileLines) {
                    if (found >= limit) break;

                    // Filtre par joueur si nécessaire
                    if (playerFilter != null && !logLine.toLowerCase().contains(playerFilter.toLowerCase())) {
                        continue;
                    }

                    if (skipped < skip) {
                        skipped++;
                        continue;
                    }

                    // Formate la ligne pour l'affichage
                    String formattedLine = formatLogLineForDisplay(logLine);
                    if (formattedLine != null) {
                        results.add(formattedLine);
                        found++;
                    }
                }

            } catch (IOException e) {
                plugin.getPluginLogger().warning("Erreur lors de la lecture du fichier de log " + logFile.getName() + ": " + e.getMessage());
            }
        }

        return results;
    }

    /**
     * Formate une ligne de log pour l'affichage
     */
    private String formatLogLineForDisplay(String logLine) {
        try {
            // Parse le format: [TIME] [TYPE] [PLAYER] [UUID] MESSAGE
            if (!logLine.startsWith("[")) return null;

            int firstClose = logLine.indexOf("]");
            int secondOpen = logLine.indexOf("[", firstClose);
            int secondClose = logLine.indexOf("]", secondOpen);
            int thirdOpen = logLine.indexOf("[", secondClose);
            int thirdClose = logLine.indexOf("]", thirdOpen);
            int fourthOpen = logLine.indexOf("[", thirdClose);
            int fourthClose = logLine.indexOf("]", fourthOpen);

            if (firstClose == -1 || secondClose == -1 || thirdClose == -1 || fourthClose == -1) {
                return "§7" + logLine; // Retourne la ligne brute si le parsing échoue
            }

            String time = logLine.substring(1, firstClose);
            String type = logLine.substring(secondOpen + 1, secondClose);
            String player = logLine.substring(thirdOpen + 1, thirdClose);
            String message = logLine.substring(fourthClose + 2);

            // Colore selon le type
            String typeColor = switch (type.toUpperCase()) {
                case "CHAT" -> "§f";
                case "ADMIN" -> "§c";
                case "COMMAND" -> "§e";
                default -> "§7";
            };

            return "§8[§7" + time + "§8] " + typeColor + "[" + type + "] §b" + player + " §7: " + message;

        } catch (Exception e) {
            return "§7" + logLine; // Retourne la ligne brute en cas d'erreur
        }
    }

    /**
     * Charge le nombre total de messages loggés
     */
    private void loadTotalMessages() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            int count = 0;
            File[] logFiles = logsFolder.listFiles((dir, name) -> name.endsWith(".log"));

            if (logFiles != null) {
                for (File logFile : logFiles) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                        while (reader.readLine() != null) {
                            if (!reader.readLine().startsWith("#")) {
                                count++;
                            }
                        }
                    } catch (IOException e) {
                        // Ignore les erreurs de lecture
                    }
                }
            }

            totalMessagesLogged = count;
            plugin.getPluginLogger().info("Messages de chat chargés: " + totalMessagesLogged);
        });
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
     * Recharge le système de logs
     */
    public void reload() {
        loadTotalMessages();
        plugin.getPluginLogger().info("Système de logs rechargé");
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