package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire d'événements pour le chat amélioré
 */
public class ChatListener implements Listener {

    // Anti-spam
    private static final long SPAM_DELAY = 2000; // 2 secondes entre les messages
    private final PrisonTycoon plugin;
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new ConcurrentHashMap<>();

    public ChatListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Annule l'événement par défaut pour gérer le chat manuellement
        event.setCancelled(true);

        // Vérifie si le joueur peut envoyer un message
        if (!canPlayerChat(player, message)) {
            return;
        }

        // Vérifie si le joueur est muté
        if (plugin.getModerationManager().isMuted(player.getUniqueId())) {
            var muteData = plugin.getModerationManager().getMuteData(player.getUniqueId());
            if (muteData != null) {
                player.sendMessage(ChatColor.RED + "🔇 Vous êtes muté!");
                player.sendMessage(ChatColor.GRAY + "Raison: " + ChatColor.YELLOW + muteData.getReason());
                if (!muteData.isPermanent()) {
                    long remaining = muteData.getRemainingTime();
                    player.sendMessage(ChatColor.GRAY + "Temps restant: " + ChatColor.YELLOW + formatDuration(remaining));
                } else {
                    player.sendMessage(ChatColor.GRAY + "Durée: " + ChatColor.RED + "Permanent");
                }
            } else {
                player.sendMessage(ChatColor.RED + "🔇 Vous êtes muté!");
            }
            return;
        }

        // Traite le message
        String processedMessage = processMessage(player, message);

        // Crée le message formaté avec la nouvelle méthode commune
        TextComponent formattedMessage = createFormattedMessage(player, processedMessage);

        // Diffuse le message à tous les joueurs en ligne
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            broadcastMessage(formattedMessage, player);
        });

        // Log le message
        String rawLogMessage = getPlayerPrefix(player) + " " + player.getName() + ": " + message;
        String formattedLogMessage = ChatColor.stripColor(formattedMessage.toLegacyText());
        plugin.getChatLogger().logChatMessage(player, rawLogMessage, formattedLogMessage);

        // Met à jour les données anti-spam
        updateSpamData(player, message);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Vérifie si le joueur est banni
        if (plugin.getModerationManager().isBanned(player.getUniqueId())) {
            var banData = plugin.getModerationManager().getBanData(player.getUniqueId());
            if (banData != null) {
                String kickMessage = ChatColor.RED.toString() + ChatColor.BOLD + "=== BANNISSEMENT ===\n\n" +
                        ChatColor.RED + "Vous êtes banni du serveur\n" +
                        ChatColor.GRAY + "Raison: " + ChatColor.YELLOW + banData.getReason() + "\n";

                if (!banData.isPermanent()) {
                    long remaining = banData.getRemainingTime();
                    kickMessage += ChatColor.GRAY + "Temps restant: " + ChatColor.YELLOW + formatDuration(remaining);
                } else {
                    kickMessage += ChatColor.GRAY + "Durée: " + ChatColor.RED + "PERMANENT";
                }

                player.kickPlayer(kickMessage);
                return;
            }
        }

        // Nettoie les données du joueur s'il était en ligne
        lastMessages.remove(player.getUniqueId());
        lastMessageTimes.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nettoie les données anti-spam quand le joueur quitte
        UUID uuid = event.getPlayer().getUniqueId();
        lastMessages.remove(uuid);
        lastMessageTimes.remove(uuid);
    }

    /**
     * NOUVELLE MÉTHODE COMMUNE - Obtient le préfixe complet du joueur selon les nouvelles spécifications
     * Format: [TYPE] [P{niveau}] [RANG] pour chat/tab
     */
    public String getPlayerPrefix(Player player) {
        if (plugin.isLuckPermsEnabled()) {
            String prefix = plugin.getPermissionManager().getPrefix(player);
            if (prefix != null) {
                return prefix;
            }
        }
        // Détermine le type de joueur et sa couleur de base
        String playerType;
        String playerTypeColor;

        if (player.hasPermission("specialmine.admin")) {
            playerType = "ADMIN";
            playerTypeColor = "§4"; // Rouge foncé
        } else if (player.hasPermission("specialmine.vip")) {
            playerType = "VIP";
            playerTypeColor = "§e"; // Jaune
        } else {
            playerType = "JOUEUR";
            playerTypeColor = "§7"; // Gris
        }

        // Récupère le niveau de prestige
        int prestigeLevel = plugin.getPrestigeManager().getPrestigeLevel(player);

        // Récupère le rang de mine actuel
        String[] rankInfo = plugin.getMineManager().getRankAndColor(player);
        String mineRank = rankInfo[0].toUpperCase(); // A, B, C... Z
        String mineRankColor = rankInfo[1]; // Couleur du rang

        // Construit le préfixe selon les spécifications
        StringBuilder prefix = new StringBuilder();

        // [TYPE] en couleur du type de joueur
        prefix.append(playerTypeColor).append("[").append(playerType).append("]");

        // [P{niveau}] seulement si prestige > 0, couleur selon prestige
        if (prestigeLevel > 0) {
            String prestigeColor = getPrestigeColor(prestigeLevel);
            prefix.append(" ").append(prestigeColor).append("[P").append(prestigeLevel).append("]");
        }

        // [RANG] en couleur du rang de mine
        prefix.append(" ").append(mineRankColor).append("[").append(mineRank).append("]");

        return prefix.toString();
    }

    public String getPlayerSuffix(Player player) {
        if (plugin.isLuckPermsEnabled()) {
            String suffix = plugin.getPermissionManager().getSuffix(player);
            if (suffix != null) {
                return suffix;
            }
        }
        return "";
    }

    /**
     * NOUVELLE MÉTHODE COMMUNE - Obtient la couleur selon le niveau de prestige
     */
    public String getPrestigeColor(int prestigeLevel) {
        if (prestigeLevel >= 50) return "§c"; // Rouge - Prestige légendaire
        if (prestigeLevel >= 40) return "§6"; // Orange - Prestige élevé
        if (prestigeLevel >= 30) return "§d"; // Rose/Magenta - Haut prestige
        if (prestigeLevel >= 20) return "§b"; // Cyan - Prestige moyen-haut
        if (prestigeLevel >= 10) return "§a"; // Vert - Prestige moyen
        if (prestigeLevel >= 5) return "§9";  // Bleu foncé - Bas prestige
        return "§f"; // Blanc - Prestige très bas (P1-P4)
    }

    /**
     * Crée le message formaté avec le nouveau système de préfixes
     */
    private TextComponent createFormattedMessage(Player player, String processedMessage) {
        TextComponent finalMessage = new TextComponent();

        // Ajoute le préfixe complet du joueur
        String prefix = getPlayerPrefix(player);
        finalMessage.addExtra(new TextComponent(prefix + " "));

        // Ajoute le nom en blanc suivi de ": "
        finalMessage.addExtra(new TextComponent("§f" + player.getName()));

        // Ajoute le suffixe
        String suffix = getPlayerSuffix(player);
        if (!suffix.isEmpty()) {
            finalMessage.addExtra(new TextComponent(" " + suffix));
        }

        finalMessage.addExtra(new TextComponent(": "));

        // Traitement des placeholders [hand] et [inv]
        if (processedMessage.contains("[hand]") || processedMessage.contains("[inv]")) {
            // Vérifie les permissions pour les placeholders spéciaux
            boolean canUseSpecialPlaceholders = player.hasPermission("specialmine.chat.hand") ||
                    player.hasPermission("specialmine.chat.inv") ||
                    player.hasPermission("specialmine.vip") ||
                    player.hasPermission("specialmine.admin");

            if (!canUseSpecialPlaceholders) {
                // Si pas de permission, retire les placeholders
                processedMessage = processedMessage.replaceAll("\\[hand\\]", ChatColor.RED + "[PERMISSION REQUISE]");
                processedMessage = processedMessage.replaceAll("\\[inv\\]", ChatColor.RED + "[PERMISSION REQUISE]");
                finalMessage.addExtra(new TextComponent(processedMessage));
                return finalMessage;
            }

            // Traite le message partie par partie
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\[hand\\]|\\[inv\\]");
            java.util.regex.Matcher matcher = pattern.matcher(processedMessage);

            int lastEnd = 0;

            while (matcher.find()) {
                // Ajoute le texte avant le placeholder
                String beforePlaceholder = processedMessage.substring(lastEnd, matcher.start());
                if (!beforePlaceholder.isEmpty()) {
                    finalMessage.addExtra(new TextComponent(beforePlaceholder));
                }

                String placeholder = matcher.group();

                if (placeholder.equals("[hand]")) {
                    // Crée le composant pour [hand]
                    ItemStack handItem = player.getInventory().getItemInMainHand();
                    TextComponent handComponent = createHandComponent(handItem);
                    finalMessage.addExtra(handComponent);

                } else if (placeholder.equals("[inv]")) {
                    // Crée le composant pour [inv]
                    TextComponent invComponent = createInventoryComponent(player);
                    finalMessage.addExtra(invComponent);
                }

                lastEnd = matcher.end();
            }

            // Ajoute le texte restant après le dernier placeholder
            String remaining = processedMessage.substring(lastEnd);
            if (!remaining.isEmpty()) {
                finalMessage.addExtra(new TextComponent(remaining));
            }
        } else {
            // Pas de placeholder spécial, ajoute le message directement
            finalMessage.addExtra(new TextComponent(processedMessage));
        }

        return finalMessage;
    }

    /**
     * Crée le composant pour [hand]
     */
    private TextComponent createHandComponent(ItemStack handItem) {
        if (handItem == null || handItem.getType() == Material.AIR) {
            TextComponent emptyHand = new TextComponent("§7[Rien en main]");
            emptyHand.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponent[]{new TextComponent("§cAucun objet en main")}));
            return emptyHand;
        }

        String itemName = getItemDisplayName(handItem);
        int amount = handItem.getAmount();
        String displayText = amount > 1 ? itemName + " x" + amount : itemName;

        TextComponent handComponent = new TextComponent("§e[" + displayText + "]");

        // Crée le texte de hover détaillé
        StringBuilder hoverText = new StringBuilder();
        hoverText.append("§6").append(itemName);

        if (amount > 1) {
            hoverText.append(" §7x").append(amount);
        }

        hoverText.append("\n§7Type: §f").append(handItem.getType().name().toLowerCase().replace("_", " "));

        if (handItem.hasItemMeta() && handItem.getItemMeta().hasLore()) {
            hoverText.append("\n\n§7Description:");
            for (String lore : handItem.getItemMeta().getLore()) {
                hoverText.append("\n").append(lore);
            }
        }

        handComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM,
                new TextComponent[]{new TextComponent(hoverText.toString())}));

        return handComponent;
    }

    /**
     * Crée le composant pour [inv]
     */
    private TextComponent createInventoryComponent(Player player) {
        TextComponent invComponent = new TextComponent("§b[📦 Inventaire]");

        invComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent("§7Cliquez pour voir l'inventaire de §e" + player.getName())}));

        invComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/invsee " + player.getName()));

        return invComponent;
    }

    /**
     * Vérifie si un joueur peut envoyer un message (anti-spam)
     */
    private boolean canPlayerChat(Player player, String message) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Vérifie le délai anti-spam
        if (lastMessageTimes.containsKey(uuid)) {
            long timeSinceLastMessage = currentTime - lastMessageTimes.get(uuid);
            if (timeSinceLastMessage < SPAM_DELAY) {
                long remainingTime = (SPAM_DELAY - timeSinceLastMessage) / 1000;
                player.sendMessage(ChatColor.RED + "⏳ Attendez " + remainingTime + " seconde(s) avant d'envoyer un autre message!");
                return false;
            }
        }

        // Vérifie la répétition de messages
        if (lastMessages.containsKey(uuid)) {
            String lastMessage = lastMessages.get(uuid);
            if (lastMessage.equals(message)) {
                player.sendMessage(ChatColor.RED + "🔄 Vous ne pouvez pas envoyer le même message deux fois de suite!");
                return false;
            }
        }

        return true;
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
     * Traite le message (couleurs, validation, etc.)
     */
    private String processMessage(Player player, String message) {
        // Vérifie les permissions pour les couleurs
        boolean canUseColors = player.hasPermission("specialmine.chat.colors") ||
                player.hasPermission("specialmine.vip") ||
                player.hasPermission("specialmine.admin");

        // Applique les couleurs si autorisé
        if (canUseColors) {
            message = ChatColor.translateAlternateColorCodes('&', message);
        }

        // Vérifie si le message ne contient QUE des placeholders
        String trimmedMessage = message.trim();
        if (trimmedMessage.equals("[hand]") || trimmedMessage.equals("[inv]") ||
                (trimmedMessage.contains("[hand]") && trimmedMessage.contains("[inv]") &&
                        trimmedMessage.replaceAll("\\[hand\\]|\\[inv\\]", "").trim().isEmpty())) {

            // Ajoute un texte par défaut pour éviter un message vide
            if (trimmedMessage.equals("[hand]")) {
                message = "Regarde mon objet: [hand]";
            } else if (trimmedMessage.equals("[inv]")) {
                message = "Voir mon inventaire: [inv]";
            } else {
                message = "Mon stuff: [hand] [inv]";
            }

            // Applique les couleurs après modification
            if (canUseColors) {
                message = ChatColor.translateAlternateColorCodes('&', message);
            }
        }

        return message;
    }

    /**
     * Obtient le nom d'affichage d'un item
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }

        // Convertit le nom du matériau en nom lisible
        String materialName = item.getType().name().toLowerCase().replace("_", " ");
        return capitalizeWords(materialName);
    }

    /**
     * Met en forme un texte (première lettre en majuscule)
     */
    private String capitalizeWords(String text) {
        String[] words = text.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Diffuse le message à tous les joueurs
     */
    private void broadcastMessage(TextComponent message, Player sender) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.spigot().sendMessage(message);
        }

        // Log dans la console
        plugin.getPluginLogger().info(ChatColor.stripColor(message.toLegacyText()));
    }

    /**
     * Formate une durée en millisecondes en texte lisible
     */
    private String formatDuration(long milliseconds) {
        if (milliseconds <= 0) return "0s";

        long days = milliseconds / (24 * 60 * 60 * 1000);
        long hours = (milliseconds % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        long minutes = (milliseconds % (60 * 60 * 1000)) / (60 * 1000);
        long seconds = (milliseconds % (60 * 1000)) / 1000;

        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("j ");
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (seconds > 0 && days == 0) result.append(seconds).append("s ");

        return result.toString().trim();
    }

    /**
     * Obtient les statistiques du chat
     */
    public String getChatStats() {
        return ChatColor.GRAY + "Statistiques du chat:\n" +
                ChatColor.YELLOW + "- Joueurs avec historique: " + ChatColor.GOLD + lastMessages.size() + "\n" +
                ChatColor.YELLOW + "- Messages en cache anti-spam: " + ChatColor.GOLD + lastMessageTimes.size();
    }
}