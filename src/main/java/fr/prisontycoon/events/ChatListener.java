package fr.prisontycoon.events;

import fr.prisontycoon.PrisonTycoon;
import org.bukkit.Bukkit;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gestionnaire d'événements pour le chat amélioré avec Adventure API
 */
public class ChatListener implements Listener {

    // Anti-spam
    private static final long SPAM_DELAY = 2000; // 2 secondes entre les messages
	private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\[hand\\]|\\[inv\\]|\\[shop\\]");
	private static final long SHOP_TAG_COOLDOWN = 5 * 60 * 1000L; // 5 minutes
    private final PrisonTycoon plugin;
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMessageTimes = new ConcurrentHashMap<>();
	private final Map<UUID, Long> lastShopTagTimes = new ConcurrentHashMap<>();

    public ChatListener(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        // Convertir le Component en String pour la logique existante
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

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
                TextComponent.Builder kickMessageBuilder = Component.text()
                        .append(Component.text("=== BANNISSEMENT ===", NamedTextColor.RED, TextDecoration.BOLD))
                        .append(Component.newline()).append(Component.newline())
                        .append(Component.text("Vous êtes banni du serveur", NamedTextColor.RED))
                        .append(Component.newline())
                        .append(Component.text("Raison: ", NamedTextColor.GRAY).append(Component.text(banData.reason(), NamedTextColor.YELLOW)))
                        .append(Component.newline());

                if (!banData.isPermanent()) {
                    kickMessageBuilder.append(Component.text("Temps restant: ", NamedTextColor.GRAY)
                            .append(Component.text(formatDuration(banData.getRemainingTime()), NamedTextColor.YELLOW)));
                } else {
                    kickMessageBuilder.append(Component.text("Durée: ", NamedTextColor.GRAY)
                            .append(Component.text("PERMANENT", NamedTextColor.RED)));
                }

                // Utilisation moderne de kick avec Component
                player.kick(kickMessageBuilder.build());
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
     * Obtient le préfixe complet du joueur selon les nouvelles spécifications
     * Format: [TYPE] [P{niveau}] [RANG] pour chat/tab
     */
    public Component getPlayerPrefixComponent(Player player) {
        // Détermine le type de joueur et sa couleur de base
        String playerType;
        NamedTextColor playerTypeColor;

        if (player.hasPermission("specialmine.admin")) {
            playerType = "ADMIN";
            playerTypeColor = NamedTextColor.DARK_RED;
        } else if (player.hasPermission("specialmine.vip")) {
            playerType = "VIP";
            playerTypeColor = NamedTextColor.YELLOW;
        } else {
            playerType = "JOUEUR";
            playerTypeColor = NamedTextColor.GRAY;
        }

        int prestigeLevel = plugin.getPrestigeManager().getPrestigeLevel(player);
        String[] rankInfo = plugin.getMineManager().getRankAndColor(player);
        String mineRank = rankInfo[0].toUpperCase();
        NamedTextColor mineRankColor = convertLegacyColor(rankInfo[1]);

        TextComponent.Builder prefix = Component.text()
                .append(Component.text("[", playerTypeColor))
                .append(Component.text(playerType, playerTypeColor))
                .append(Component.text("]", playerTypeColor));

        if (prestigeLevel > 0) {
            NamedTextColor prestigeColor = getPrestigeColor(prestigeLevel);
            prefix.append(Component.text(" "))
                    .append(Component.text("[", prestigeColor))
                    .append(Component.text("P" + prestigeLevel, prestigeColor))
                    .append(Component.text("]", prestigeColor));
        }

        prefix.append(Component.text(" "))
                .append(Component.text("[", mineRankColor))
                .append(Component.text(mineRank, mineRankColor))
                .append(Component.text("]", mineRankColor));

        return prefix.build();
    }

    /**
     * Version legacy pour la compatibilité (logs, etc.)
     */
    public String getPlayerPrefix(Player player) {
        return PlainTextComponentSerializer.plainText().serialize(getPlayerPrefixComponent(player));
    }

    /**
     * Convertit une couleur legacy en NamedTextColor
     */
    private NamedTextColor convertLegacyColor(String legacyColor) {
        if (legacyColor == null || legacyColor.length() < 2) return NamedTextColor.WHITE;

        char colorCode = legacyColor.charAt(1);
        return switch (colorCode) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> NamedTextColor.WHITE;
        };
    }

    /**
     * Obtient la couleur selon le niveau de prestige
     */
    public NamedTextColor getPrestigeColor(int prestigeLevel) {
        if (prestigeLevel >= 50) return NamedTextColor.RED; // Rouge - Prestige légendaire
        if (prestigeLevel >= 40) return NamedTextColor.GOLD; // Orange - Prestige élevé
        if (prestigeLevel >= 30) return NamedTextColor.LIGHT_PURPLE; // Rose/Magenta - Haut prestige
        if (prestigeLevel >= 20) return NamedTextColor.AQUA; // Cyan - Prestige moyen-haut
        if (prestigeLevel >= 10) return NamedTextColor.GREEN; // Vert - Prestige moyen
        if (prestigeLevel >= 5) return NamedTextColor.BLUE;  // Bleu foncé - Bas prestige
        return NamedTextColor.WHITE; // Blanc - Prestige très bas (P1-P4)
    }

    /**
     * Crée le message formaté avec le nouveau système de préfixes
     */
    private Component createFormattedMessage(Player player, String processedMessage) {
        TextComponent.Builder finalMessage = Component.text();

        // Ajouter le préfixe
        finalMessage.append(getPlayerPrefixComponent(player))
                .append(Component.text(" "))
                .append(Component.text(player.getName() + ": ", NamedTextColor.WHITE));

        boolean canUseSpecialPlaceholders = player.hasPermission("specialmine.chat.hand") ||
                player.hasPermission("specialmine.chat.inv") ||
                player.hasPermission("specialmine.vip") ||
                player.hasPermission("specialmine.admin");

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(processedMessage);
        int lastEnd = 0;

		while (matcher.find()) {
            String beforeText = processedMessage.substring(lastEnd, matcher.start());
            if (!beforeText.isEmpty()) {
                finalMessage.append(LegacyComponentSerializer.legacySection().deserialize(beforeText));
            }
            String placeholder = matcher.group();
			if (placeholder.equals("[shop]")) {
				finalMessage.append(createShopComponent(player));
			} else if (!canUseSpecialPlaceholders) {
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

        // Récupération du nom de l'item
        Component itemName = handItem.hasItemMeta() && handItem.getItemMeta().hasDisplayName()
                ? LegacyComponentSerializer.legacySection().deserialize(handItem.getItemMeta().getDisplayName())
                : Component.translatable(handItem.getType());

        int amount = handItem.getAmount();

        // Construction du texte à afficher
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
        Component hoverText = Component.text()
                .color(NamedTextColor.GRAY)
                .append(Component.text("Cliquez pour voir l'inventaire de "))
                .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                .build();

        return Component.text("[📦 Inventaire]", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(hoverText))
                .clickEvent(ClickEvent.runCommand("/invsee " + player.getName()));
    }

    /**
     * Crée le composant pour [shop]
     */
    private Component createShopComponent(Player player) {
        // Cooldown par joueur
        long now = System.currentTimeMillis();
        Long last = lastShopTagTimes.get(player.getUniqueId());
        boolean onCooldown = last != null && (now - last) < SHOP_TAG_COOLDOWN;

        TextComponent.Builder shopText = Component.text()
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("[SHOP]"));

        String hover = buildShopHover(player);
        if (hover == null || hover.isEmpty()) {
            hover = "Clique pour te téléporter au shop";
        }

        // Hover toujours présent
        shopText.hoverEvent(HoverEvent.showText(Component.text(hover, NamedTextColor.YELLOW)));

        // Si le plugin PlayerShops est actif et pas de cooldown, on rend cliquable
        boolean playerShopsEnabled = plugin.isPlayerShopsAvailable();

        if (playerShopsEnabled && !onCooldown) {
            shopText.clickEvent(ClickEvent.runCommand("/shop tp " + player.getName()));
            lastShopTagTimes.put(player.getUniqueId(), now);
        } else if (onCooldown) {
            long remaining = (SHOP_TAG_COOLDOWN - (now - last)) / 1000;
            player.sendMessage(Component.text("⏳ Tu pourras renvoyer ton [SHOP] dans " + (remaining) + "s", NamedTextColor.RED));
        }

        return shopText.build();
    }

    private String buildShopHover(Player player) {
        try {
            var ps = this.plugin.getPlayerShopsPlugin();
            if (ps == null) return null;

            Class<?> pluginClass = ps.getClass();
            var getShopManager = pluginClass.getMethod("getShopManager");
            Object shopManager = getShopManager.invoke(ps);
            if (shopManager == null) return null;

            Class<?> shopManagerClass = shopManager.getClass();
            var getPlayerShop = shopManagerClass.getMethod("getPlayerShop", UUID.class);
            Object shop = getPlayerShop.invoke(shopManager, player.getUniqueId());
            if (shop == null) return "Aucun shop trouvé";

            String ownerName = (String) shop.getClass().getMethod("getOwnerName").invoke(shop);
            Object ad = null;
            try { ad = shop.getClass().getMethod("getAdvertisement").invoke(shop); } catch (NoSuchMethodException ignored) {}
            String customMsg = null;
            try { customMsg = (String) shop.getClass().getMethod("getCustomMessage").invoke(shop); } catch (NoSuchMethodException ignored) {}

            StringBuilder sb = new StringBuilder();
            sb.append("Shop de ").append(ownerName != null ? ownerName : player.getName());
            if (ad != null) {
                try {
                    Boolean active = (Boolean) ad.getClass().getMethod("isActive").invoke(ad);
                    String title = (String) ad.getClass().getMethod("getTitle").invoke(ad);
                    String desc = (String) ad.getClass().getMethod("getDescription").invoke(ad);
                    sb.append("\n");
                    if (title != null && !title.isEmpty()) sb.append(title).append("\n");
                    if (desc != null && !desc.isEmpty()) sb.append(desc).append("\n");
                    if (active != null && active) sb.append("(Annonce active)");
                } catch (Throwable ignored) {}
            } else if (customMsg != null && !customMsg.isEmpty()) {
                sb.append("\n").append(customMsg);
            }
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
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
        boolean canUseColors = player.hasPermission("specialmine.chat.colors") ||
                player.hasPermission("specialmine.vip") ||
                player.hasPermission("specialmine.admin");

        if (canUseColors) {
            // Garde la compatibilité avec les codes couleur legacy pour le traitement
            message = message.replace('&', '§');
        }
        return message;
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