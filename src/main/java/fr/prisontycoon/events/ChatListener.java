package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestionnaire d'événements pour le chat amélioré
 */
public class ChatListener implements Listener {

    // Anti-spam
    private static final long SPAM_DELAY = 2000; // 2 secondes entre les messages
    private final PrisonTycoon plugin;
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new ConcurrentHashMap<>();

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[hand\\]|\\[inv\\]");



    public ChatListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        if (plugin.getTankGUI().isPlayerAwaitingInput(player)) {
            return;
        }

        event.setCancelled(true);

        if (!canPlayerChat(player, message)) {
            return;
        }

        if (plugin.getModerationManager().isMuted(player.getUniqueId())) {
            var muteData = plugin.getModerationManager().getMuteData(player.getUniqueId());
            if (muteData != null) {
                player.sendMessage(Component.text("🔇 Vous êtes muté!", NamedTextColor.RED));
                player.sendMessage(Component.text("Raison: ", NamedTextColor.GRAY)
                        .append(Component.text(muteData.reason(), NamedTextColor.YELLOW)));
                if (!muteData.isPermanent()) {
                    player.sendMessage(Component.text("Temps restant: ", NamedTextColor.GRAY)
                            .append(Component.text(formatDuration(muteData.getRemainingTime()), NamedTextColor.YELLOW)));
                } else {
                    player.sendMessage(Component.text("Durée: ", NamedTextColor.GRAY)
                            .append(Component.text("Permanent", NamedTextColor.RED)));
                }
            } else {
                player.sendMessage(Component.text("🔇 Vous êtes muté!", NamedTextColor.RED));
            }
            return;
        }

        String processedMessage = processMessage(player, message);
        Component formattedMessage = createFormattedMessage(player, processedMessage);

        plugin.getServer().getScheduler().runTask(plugin, () -> broadcastMessage(formattedMessage, player));

        String rawLogMessage = getPlayerPrefix(player) + " " + player.getName() + ": " + message;
        String formattedLogMessage = PlainTextComponentSerializer.plainText().serialize(formattedMessage);
        plugin.getChatLogger().logChatMessage(player, rawLogMessage, formattedLogMessage);

        updateSpamData(player, message);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.getModerationManager().isBanned(player.getUniqueId())) {
            var banData = plugin.getModerationManager().getBanData(player.getUniqueId());
            if (banData != null) {
                Component kickMessage = Component.text()
                        .append(Component.text("=== BANNISSEMENT ===", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.newline()).append(Component.newline())
                        .append(Component.text("Vous êtes banni du serveur", NamedTextColor.RED))
                        .append(Component.newline())
                        .append(Component.text("Raison: ", NamedTextColor.GRAY).append(Component.text(banData.reason(), NamedTextColor.YELLOW)))
                        .append(Component.newline())
                        .build();

                if (!banData.isPermanent()) {
                    kickMessage = kickMessage.append(Component.text("Temps restant: ", NamedTextColor.GRAY)
                            .append(Component.text(formatDuration(banData.getRemainingTime()), NamedTextColor.YELLOW)));
                } else {
                    kickMessage = kickMessage.append(Component.text("Durée: ", NamedTextColor.GRAY)
                            .append(Component.text("PERMANENT", NamedTextColor.RED)));
                }

                // La méthode moderne pour kick un joueur utilise un Component
                player.kick(kickMessage);
                return;
            }
        }

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
        // Détermine le type de joueur et sa couleur de base
        String playerType;
        String playerTypeColor;

        if (player.hasPermission("specialmine.admin")) {
            playerType = "ADMIN";
            playerTypeColor = "§4";
        } else if (player.hasPermission("specialmine.vip")) {
            playerType = "VIP";
            playerTypeColor = "§e";
        } else {
            playerType = "JOUEUR";
            playerTypeColor = "§7";
        }

        int prestigeLevel = plugin.getPrestigeManager().getPrestigeLevel(player);
        String[] rankInfo = plugin.getMineManager().getRankAndColor(player);
        String mineRank = rankInfo[0].toUpperCase();
        String mineRankColor = rankInfo[1];

        StringBuilder prefix = new StringBuilder();
        prefix.append(playerTypeColor).append("[").append(playerType).append("]");

        if (prestigeLevel > 0) {
            String prestigeColor = getPrestigeColor(prestigeLevel);
            prefix.append(" ").append(prestigeColor).append("[P").append(prestigeLevel).append("]");
        }

        prefix.append(" ").append(mineRankColor).append("[").append(mineRank).append("]");
        return prefix.toString();
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
    private Component createFormattedMessage(Player player, String processedMessage) {
        TextComponent.Builder finalMessage = Component.text();

        String prefixString = getPlayerPrefix(player);
        finalMessage.append(LegacyComponentSerializer.legacySection().deserialize(prefixString + " "));
        finalMessage.append(Component.text(player.getName() + ": ", NamedTextColor.WHITE));

        boolean canUseSpecialPlaceholders = player.hasPermission("specialmine.chat.hand") || player.hasPermission("specialmine.chat.inv") || player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin");
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(processedMessage);
        int lastEnd = 0;

        while (matcher.find()) {
            String beforeText = processedMessage.substring(lastEnd, matcher.start());
            if (!beforeText.isEmpty()) {
                finalMessage.append(LegacyComponentSerializer.legacySection().deserialize(beforeText));
            }
            String placeholder = matcher.group();
            if (!canUseSpecialPlaceholders) {
                finalMessage.append(Component.text("[PERMISSION REQUISE]", NamedTextColor.RED));
            } else if (placeholder.equals("[hand]")) {
                finalMessage.append(createHandComponent(player.getInventory().getItemInMainHand()));
            } else if (placeholder.equals("[inv]")) {
                finalMessage.append(createInventoryComponent(player));
            }
            lastEnd = matcher.end();
        }

        String remainingText = processedMessage.substring(lastEnd);
        if (!remainingText.isEmpty()) {
            finalMessage.append(LegacyComponentSerializer.legacySection().deserialize(remainingText));
        }

        return finalMessage.build();
    }

    private Component createHandComponent(ItemStack handItem) {
        if (handItem == null || handItem.getType() == Material.AIR) {
            return Component.text("[Rien en main]", NamedTextColor.GRAY)
                    .hoverEvent(HoverEvent.showText(Component.text("Aucun objet en main", NamedTextColor.RED)));
        }

        // On récupère le nom de l'item comme avant
        Component itemName = handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName()
                ? LegacyComponentSerializer.legacySection().deserialize(handItem.getItemMeta().getDisplayName())
                : Component.translatable(handItem.getType());

        int amount = handItem.getAmount();

        // On construit le texte à afficher
        TextComponent.Builder displayBuilder = Component.text()
                .color(NamedTextColor.YELLOW)
                .append(Component.text("["))
                .append(itemName)
                .append(Component.text(amount > 1 ? " x" + amount : ""))
                .append(Component.text("]"));

        displayBuilder.hoverEvent(handItem);

        return displayBuilder.build();
    }

    /**
     * Crée le composant pour [inv]
     */
    private Component createInventoryComponent(Player player) {
        return Component.text("[📦 Inventaire]", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text()
                        .color(NamedTextColor.GRAY)
                        .append(Component.text("Cliquez pour voir l'inventaire de "))
                        .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                        .build()))
                .clickEvent(ClickEvent.runCommand("/invsee " + player.getName()));
    }

    /**
     * Vérifie si un joueur peut envoyer un message (anti-spam)
     */
    private boolean canPlayerChat(Player player, String message) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (lastMessageTimes.containsKey(uuid)) {
            long timeSinceLastMessage = currentTime - lastMessageTimes.get(uuid);
            if (timeSinceLastMessage < SPAM_DELAY) {
                long remainingTime = (SPAM_DELAY - timeSinceLastMessage) / 1000;
                player.sendMessage(Component.text("⏳ Attendez " + (remainingTime + 1) + " seconde(s) avant d'envoyer un autre message!", NamedTextColor.RED));
                return false;
            }
        }

        if (message.equalsIgnoreCase(lastMessages.get(uuid))) {
            player.sendMessage(Component.text("🔄 Vous ne pouvez pas envoyer le même message deux fois de suite!", NamedTextColor.RED));
            return false;
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

    private String processMessage(Player player, String message) {
        boolean canUseColors = player.hasPermission("specialmine.chat.colors") || player.hasPermission("specialmine.vip") || player.hasPermission("specialmine.admin");
        if (canUseColors) {
            message = ChatColor.translateAlternateColorCodes('&', message);
        }
        String stripped = ChatColor.stripColor(message).trim();
        if (stripped.equals("[hand]") || stripped.equals("[inv]") || (stripped.contains("[hand]") && stripped.contains("[inv]") && stripped.replace("[hand]", "").replace("[inv]", "").trim().isEmpty())) {
            message = "Mon stuff: [hand] [inv]";
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
    private void broadcastMessage(Component message, Player sender) {
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            onlinePlayer.sendMessage(message);
        }
        plugin.getLogger().info(PlainTextComponentSerializer.plainText().serialize(message));
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
}