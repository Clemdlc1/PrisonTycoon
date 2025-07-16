package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Listener pour le système de chat personnalisé avec rangs et fonctionnalités avancées
 * Format: [Rank] Pseudo (couleur selon le rôle) : Message
 */
public class ChatListener implements Listener {

    private final PrisonTycoon plugin;

    // Anti-spam: stockage du dernier message et timestamp
    private final Map<UUID, String> lastMessages = new HashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new HashMap<>();

    // Délai anti-spam en millisecondes
    private static final long SPAM_DELAY = 1000; // 1 seconde

    // Pattern pour détecter les codes couleur non autorisés
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§[0-9a-fk-or]");

    public ChatListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Annule le format par défaut
        event.setCancelled(true);

        // Vérifications préliminaires
        if (!canPlayerChat(player, message)) {
            return;
        }

        // Traite le message
        String processedMessage = processMessage(player, message);

        // Détermine le rang et la couleur du joueur
        String[] rankInfo = plugin.getMineManager().getRankAndColor(player);
        String playerType = rankInfo[0];        // ADMIN/VIP/JOUEUR
        String playerTypeColor = rankInfo[2];   // Couleur du type
        String mineRank = rankInfo[3];          // Rang de mine (A, B, C...)
        String mineRankColor = rankInfo[4];     // Couleur du rang de mine

        // Formate le message final
        String formattedMessage = formatChatMessage(playerTypeColor, playerType, player.getName(), processedMessage, mineRank, mineRankColor);

        // Diffuse le message à tous les joueurs en ligne
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            broadcastMessage(formattedMessage, player);

            // Log dans la console
            plugin.getPluginLogger().info("[CHAT] " + player.getName() + " (" + playerType + " - Mine " + mineRank + "): " + message);
        });

        // Met à jour les données anti-spam
        updateSpamData(player, message);
    }

    /**
     * Vérifie si un joueur peut envoyer un message
     */
    private boolean canPlayerChat(Player player, String message) {
        // Vérification du mute (permission négative)
        if (!player.hasPermission("specialmine.chat")) {
            player.sendMessage("§c❌ Vous êtes actuellement muté et ne pouvez pas parler en chat.");
            return false;
        }

        // Vérification anti-spam
        if (isSpamming(player, message)) {
            player.sendMessage("§c⚠ Ralentissez ! Attendez avant d'envoyer un autre message.");
            return false;
        }

        // Vérification de la longueur du message
        if (message.length() > 256) {
            player.sendMessage("§c❌ Votre message est trop long (maximum 256 caractères).");
            return false;
        }

        // Vérification du message vide
        if (message.trim().isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * Vérifie si le joueur fait du spam
     */
    private boolean isSpamming(Player player, String message) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // CORRIGÉ : Vérification du délai entre messages (peu importe le contenu)
        Long lastTime = lastMessageTimes.get(uuid);
        if (lastTime != null && (currentTime - lastTime) < SPAM_DELAY) {
            return true;
        }

        // Vérification supplémentaire de la répétition du même message
        String lastMessage = lastMessages.get(uuid);
        if (lastMessage != null && lastMessage.equals(message)) {
            player.sendMessage("§c⚠ Vous ne pouvez pas envoyer le même message deux fois de suite.");
            return true;
        }

        return false;
    }

    /**
     * Met à jour les données anti-spam
     */
    private void updateSpamData(Player player, String message) {
        UUID uuid = player.getUniqueId();
        lastMessages.put(uuid, message);
        lastMessageTimes.put(uuid, System.currentTimeMillis());
    }

    /**
     * Traite le message (couleurs, filtres, etc.)
     */
    private String processMessage(Player player, String message) {
        // Retire les codes couleur non autorisés pour les joueurs normaux
        if (!player.hasPermission("specialmine.chat.colors")) {
            message = COLOR_CODE_PATTERN.matcher(message).replaceAll("");
        }

        // Autorise les codes couleur pour les VIP et Admin
        if (player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin")) {
            // Remplace les codes & par des codes § pour VIP/Admin
            message = message.replace("&", "§");
        }

        // Filtre les caractères spéciaux indésirables
        message = message.replace("§k", ""); // Retire l'effet magique toujours
        message = message.replace("§l", ""); // Retire le gras pour éviter l'abus
        message = message.replace("§o", ""); // Retire l'italique pour éviter l'abus

        return message;
    }

    /**
     * Diffuse le message à tous les joueurs avec des permissions spéciales
     */
    private void broadcastMessage(String formattedMessage, Player sender) {
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            // Tous les joueurs reçoivent le message par défaut
            boolean canSeeMessage = true;

            // Future fonctionnalité: channels privés ou ignore list
            // if (hasPlayerIgnored(onlinePlayer, sender)) {
            //     canSeeMessage = false;
            // }

            if (canSeeMessage) {
                onlinePlayer.sendMessage(formattedMessage);
            }
        }
    }


    /**
     * Formate le message du chat
     * @param playerTypeColor Couleur du type de joueur (ADMIN/VIP/JOUEUR)
     * @param playerType Type de joueur (ADMIN/VIP/JOUEUR)
     * @param playerName Nom du joueur
     * @param message Message du joueur
     * @param mineRank Rang de mine (A, B, C...)
     * @param mineRankColor Couleur du rang de mine
     * @return Message formaté
     */
    private String formatChatMessage(String playerTypeColor, String playerType, String playerName, String message, String mineRank, String mineRankColor) {
        StringBuilder formatted = new StringBuilder();

        // Format selon le type de joueur
        if ("ADMIN".equals(playerType)) {
            // [ADMIN(rouge)] [Currentrank] NOM
            formatted.append(playerTypeColor).append("[").append(playerType).append("] ");
            formatted.append(mineRankColor).append("[").append(mineRank).append("] ");
            formatted.append(playerName);
        } else if ("VIP".equals(playerType)) {
            // [VIP(jaune)] [Currentrank] NOM
            formatted.append(playerTypeColor).append("[").append(playerType).append("] ");
            formatted.append(mineRankColor).append("[").append(mineRank).append("] ");
            formatted.append(playerName);
        } else {
            // [Currentrank] NOM (joueurs normaux)
            formatted.append(mineRankColor).append("[").append(mineRank).append("] ");
            formatted.append(playerName);
        }

        formatted.append(" §f: ").append(message);
        return formatted.toString();
    }

    /**
     * Nettoie les données anti-spam lors de la déconnexion
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        lastMessages.remove(uuid);
        lastMessageTimes.remove(uuid);
    }

    /**
     * Commande pour muter/démuter un joueur (à utiliser avec un système de commandes)
     */
    public void mutePlayer(Player target, boolean muted) {
        // Cette méthode peut être appelée depuis une commande admin
        if (muted) {
            // Retire la permission de chat
            target.addAttachment(plugin, "specialmine.chat", false);
            target.sendMessage("§c🔇 Vous avez été muté par un administrateur.");
        } else {
            // Redonne la permission de chat
            target.addAttachment(plugin, "specialmine.chat", true);
            target.sendMessage("§a🔊 Vous avez été démuté par un administrateur.");
        }
    }

    /**
     * Obtient les statistiques du chat
     */
    public String getChatStats() {
        return "§7Statistiques du chat:\n" +
                "§e- Joueurs avec historique: §6" + lastMessages.size() + "\n" +
                "§e- Messages en cache anti-spam: §6" + lastMessageTimes.size();
    }
}