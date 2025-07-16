package fr.prisoncore.prisoncore.prisonTycoon.events;

import fr.prisoncore.prisoncore.prisonTycoon.PrisonTycoon;
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
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire d'événements pour le chat amélioré
 */
public class ChatListener implements Listener {

    private final PrisonTycoon plugin;

    // Anti-spam
    private static final long SPAM_DELAY = 2000; // 2 secondes entre les messages
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
                player.sendMessage("§c🔇 Vous êtes muté!");
                player.sendMessage("§7Raison: §e" + muteData.getReason());
                if (!muteData.isPermanent()) {
                    long remaining = muteData.getRemainingTime();
                    player.sendMessage("§7Temps restant: §e" + formatDuration(remaining));
                } else {
                    player.sendMessage("§7Durée: §cPermanent");
                }
            } else {
                player.sendMessage("§c🔇 Vous êtes muté!");
            }
            return;
        }

        // Traite le message
        String processedMessage = processMessage(player, message);

        // Détermine le type de joueur et ses couleurs
        String[] playerInfo = getPlayerTypeAndColors(player);
        String playerType = playerInfo[0];
        String playerTypeColor = playerInfo[1];

        // Récupère le rang de mine et sa couleur
        String[] rankInfo = plugin.getMineManager().getRankAndColor(player);
        String mineRank = rankInfo[0];
        String mineRankColor = rankInfo[1];

        // Crée le message formaté
        TextComponent formattedMessage = createFormattedMessage(player, playerTypeColor, playerType,
                mineRankColor, mineRank, processedMessage);

        // Diffuse le message à tous les joueurs en ligne
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            broadcastMessage(formattedMessage, player);
        });

        // Log le message
        String rawLogMessage = "[" + playerType + "] " + player.getName() + ": " + message;
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
                String kickMessage = "§c§l=== BANNISSEMENT ===\n\n" +
                        "§cVous êtes banni du serveur\n" +
                        "§7Raison: §e" + banData.getReason() + "\n";

                if (!banData.isPermanent()) {
                    long remaining = banData.getRemainingTime();
                    kickMessage += "§7Temps restant: §e" + formatDuration(remaining) + "\n";
                } else {
                    kickMessage += "§7Durée: §cPermanent\n";
                }

                kickMessage += "§7Par: §e" + banData.getModerator();

                // Kick le joueur après un court délai
                String finalKickMessage = kickMessage;
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.kickPlayer(finalKickMessage);
                    }
                }, 5L);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastMessages.remove(uuid);
        lastMessageTimes.remove(uuid);
    }

    /**
     * Vérifie si un joueur peut envoyer un message
     */
    private boolean canPlayerChat(Player player, String message) {
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

        // Vérification du délai entre messages
        Long lastTime = lastMessageTimes.get(uuid);
        if (lastTime != null && (currentTime - lastTime) < SPAM_DELAY) {
            return true;
        }

        // Vérification de la répétition du même message
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
     * Traite le message (couleurs, [hand], [inv], etc.)
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

        return message;
    }

    /**
     * Détermine le type de joueur et ses couleurs
     */
    private String[] getPlayerTypeAndColors(Player player) {
        if (player.hasPermission("specialmine.admin")) {
            return new String[]{"ADMIN", "§4"};
        } else if (player.hasPermission("specialmine.vip") || plugin.getVipManager().isVip(player.getUniqueId())) {
            return new String[]{"VIP", "§e"};
        } else {
            return new String[]{"", "§7"};
        }
    }

    /**
     * Crée le message formaté avec support pour [hand] et [inv]
     */
    private TextComponent createFormattedMessage(Player player, String playerTypeColor, String playerType,
                                                 String mineRankColor, String mineRank, String processedMessage) {

        TextComponent finalMessage = new TextComponent();

        // Ajoute le préfixe du joueur
        TextComponent prefix = new TextComponent();

        if (!playerType.isEmpty()) {
            prefix.addExtra(new TextComponent(playerTypeColor + "[" + playerType + "] "));
        }
        prefix.addExtra(new TextComponent(mineRankColor + "[" + mineRank + "] "));
        prefix.addExtra(new TextComponent(playerTypeColor + player.getName() + " §f: "));

        finalMessage.addExtra(prefix);

        // Traite le message pour [hand] et [inv]
        String[] parts = processedMessage.split("\\[hand\\]|\\[inv\\]");
        String[] placeholders = extractPlaceholders(processedMessage);

        for (int i = 0; i < parts.length; i++) {
            // Ajoute la partie de texte normale
            if (!parts[i].isEmpty()) {
                finalMessage.addExtra(new TextComponent(parts[i]));
            }

            // Ajoute le placeholder si il existe
            if (i < placeholders.length) {
                if (placeholders[i].equals("[hand]")) {
                    finalMessage.addExtra(createHandComponent(player));
                } else if (placeholders[i].equals("[inv]")) {
                    finalMessage.addExtra(createInventoryComponent(player));
                }
            }
        }

        return finalMessage;
    }

    /**
     * Extrait les placeholders [hand] et [inv] du message
     */
    private String[] extractPlaceholders(String message) {
        java.util.List<String> placeholders = new java.util.ArrayList<>();
        int index = 0;

        while (index < message.length()) {
            int handIndex = message.indexOf("[hand]", index);
            int invIndex = message.indexOf("[inv]", index);

            int nextIndex = -1;
            String nextPlaceholder = null;

            if (handIndex != -1 && (invIndex == -1 || handIndex < invIndex)) {
                nextIndex = handIndex;
                nextPlaceholder = "[hand]";
            } else if (invIndex != -1) {
                nextIndex = invIndex;
                nextPlaceholder = "[inv]";
            }

            if (nextIndex == -1) break;

            placeholders.add(nextPlaceholder);
            index = nextIndex + nextPlaceholder.length();
        }

        return placeholders.toArray(new String[0]);
    }

    /**
     * Crée un composant pour afficher l'item en main
     */
    private TextComponent createHandComponent(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            TextComponent component = new TextComponent("§7[Aucun item]");
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponent[]{new TextComponent("§7Le joueur n'a aucun item en main")}));
            return component;
        }

        String itemName = getItemDisplayName(item);
        TextComponent component = new TextComponent("§e[" + itemName + "]");

        // Crée le texte de survol détaillé
        StringBuilder hoverText = new StringBuilder();
        hoverText.append("§e").append(itemName);

        if (item.getAmount() > 1) {
            hoverText.append(" §7x").append(item.getAmount());
        }

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasLore()) {
                hoverText.append("\n");
                for (String lore : meta.getLore()) {
                    hoverText.append("\n§7").append(lore);
                }
            }
        }

        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent(hoverText.toString())}));

        return component;
    }

    /**
     * Crée un composant cliquable pour afficher l'inventaire
     */
    private TextComponent createInventoryComponent(Player player) {
        TextComponent component = new TextComponent("§b[§l⚔ Inventaire§b]");

        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new TextComponent[]{new TextComponent("§bCliquez pour voir l'inventaire de " + player.getName())}));

        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/invsee " + player.getName()));

        return component;
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
        return "§7Statistiques du chat:\n" +
                "§e- Joueurs avec historique: §6" + lastMessages.size() + "\n" +
                "§e- Messages en cache anti-spam: §6" + lastMessageTimes.size();
    }
}