package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.PlayerBoost;
import fr.prisontycoon.managers.GlobalBonusManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Interface graphique pour visualiser les boosts actifs
 * Version simplifiée utilisant la nouvelle API de GlobalBonusManager
 */
public class BoostGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;

    public BoostGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "gui_action");
    }

    /**
     * Ouvre le menu des boosts pour un joueur
     */
    public void openBoostMenu(Player player) {
        List<PlayerBoost> activeBoosts = plugin.getBoostManager().getActiveBoosts(player);

        // Calcule la taille de l'inventaire (minimum 27, maximum 54)
        int size = Math.max(27, Math.min(54, ((activeBoosts.size() + 8) / 9) * 9));

        Inventory gui = plugin.getGUIManager().createInventory(size, "§6⚡ Vos Boosts Actifs");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.BOOST_MENU, gui);

        // Ajoute les boosts actifs
        int slot = 0;
        for (PlayerBoost boost : activeBoosts) {
            if (slot >= size - 9) break; // Garde de la place pour les boutons

            ItemStack boostItem = createBoostDisplayItem(boost);
            gui.setItem(slot, boostItem);
            slot++;
        }

        // Si aucun boost actif
        if (activeBoosts.isEmpty()) {
            ItemStack noBoost = createNoBoostItem();
            gui.setItem(13, noBoost);
        }

        // Ligne de séparation
        for (int i = size - 9; i < size - 1; i++) {
            gui.setItem(i, createSeparatorItem());
        }

        // Boutons de contrôle
        gui.setItem(size - 9, createHelpButton());
        gui.setItem(size - 8, createRefreshButton());
        gui.setItem(size - 7, createTotalBonusButton(player));
        gui.setItem(size - 1, createCloseButton());

        player.openInventory(gui);
    }

    /**
     * Crée un item pour afficher un boost
     */
    private ItemStack createBoostDisplayItem(PlayerBoost boost) {
        ItemStack item = new ItemStack(boost.getType().getMaterial());
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, boost.getType().getFormattedName());

        // Calcule la barre de progression
        double progress = boost.getProgress();
        int filledBars = (int) (progress * 20);
        StringBuilder progressBar = new StringBuilder("§a");

        for (int i = 0; i < 20; i++) {
            if (i < filledBars) {
                progressBar.append("█");
            } else if (i == filledBars) {
                progressBar.append("§7");
            }
            progressBar.append("█");
        }

        List<String> lore = Arrays.asList(
                "",
                "§7Type: " + boost.getType().getFormattedName(),
                "§7Bonus: " + boost.getType().getColor() + "+" +
                        String.format("%.0f", boost.getBonusPercentage()) + "%",
                "",
                "§7Temps restant: " + boost.getFormattedTimeRemaining(),
                "§7Durée totale: §e" + (boost.getTotalDurationSeconds() / 60) + " minutes",
                "",
                "§7Progression:",
                progressBar.toString(),
                "§7" + String.format("%.1f", progress * 100) + "% écoulé",
                "",
                boost.isAdminBoost() ? "§c✦ Boost Admin Global" : "§a✦ Boost Personnel",
                "",
                "§8Les boosts s'appliquent automatiquement"
        );
        plugin.getGUIManager().applyLore(meta, lore);

        if (boost.isAdminBoost()) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item pour "aucun boost actif"
     */
    private ItemStack createNoBoostItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§c❌ Aucun boost actif");
        plugin.getGUIManager().applyLore(meta, Arrays.asList(
                "",
                "§7Vous n'avez actuellement aucun boost actif.",
                "",
                "§7Comment obtenir des boosts:",
                "§7• Utilisez des items boost avec §eclic droit",
                "§7• Attendez les boosts admin globaux",
                "§7• Participez aux événements spéciaux",
                "",
                "§8Les boosts augmentent vos gains automatiquement"
        ));

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton des bonus totaux
     */
    private ItemStack createTotalBonusButton(Player player) {
        ItemStack item = new ItemStack(Material.BEACON);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§6📊 Bonus Totaux Actifs");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Multiplicateurs de bonus actuels:");
        lore.add("");

        var globalBonusManager = plugin.getGlobalBonusManager();
        if (globalBonusManager != null) {
            // Afficher tous les bonus non nuls (positifs ou négatifs)
            for (GlobalBonusManager.BonusCategory category : GlobalBonusManager.BonusCategory.values()) {
                var details = globalBonusManager.getBonusSourcesDetails(player, category);
                if (Math.abs(details.getTotalBonus()) > 0.0001) {
                    lore.add(category.getColor() + category.getEmoji() + " " + category.getDisplayName() +
                            "§7: §f×" + String.format("%.2f", details.getTotalMultiplier()) +
                            " §7(" + (details.getTotalBonus() >= 0 ? "+" : "") + String.format("%.1f", details.getTotalBonus()) + "%)");
                }
            }

            if (lore.size() == 3) { // rien ajouté après l'en-tête
                lore.add("§7Aucun bonus actif actuellement");
            }
        } else {
            lore.add("§cErreur: GlobalBonusManager non disponible");
        }

        lore.add("");
        // Retiré: ligne générique 'Sources disponibles' et liste statique
        lore.add("§e▶ Cliquez pour les détails dans le chat");
        lore.add("§8Les bonus se cumulent automatiquement");

        plugin.getGUIManager().applyLore(meta, lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "total_bonus");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton de rafraîchissement
     */
    private ItemStack createRefreshButton() {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§a🔄 Actualiser");
        plugin.getGUIManager().applyLore(meta, Arrays.asList(
                "",
                "§7Actualise l'affichage des boosts",
                "",
                "§7Utilisez ce bouton si:",
                "§7• Un boost vient d'expirer",
                "§7• Un nouveau boost admin est actif",
                "§7• L'affichage semble incorrect",
                "",
                "§e▶ Cliquez pour actualiser"
        ));

        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "refresh");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton d'aide
     */
    private ItemStack createHelpButton() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§e📖 Aide - Système de Boosts");
        plugin.getGUIManager().applyLore(meta, Arrays.asList(
                "",
                "§7§lComment fonctionnent les boosts:",
                "",
                "§7• Les boosts augmentent vos gains",
                "§7• Maximum 1 boost par type actif",
                "§7• Les boosts admin s'appliquent à tous",
                "§7• Utilisez §eclic droit §7sur les items boost",
                "",
                "§7§lTypes de boosts disponibles:",
                "§b• Token Greed §7- Plus de tokens",
                "§6• Money Greed §7- Plus de coins",
                "§a• XP Greed §7- Plus d'expérience",
                "§e• Sell Boost §7- Meilleur prix de vente",
                "§9• Mineral Greed §7- Plus d'effet Fortune",
                "§d• Job XP §7- Plus d'XP métier",
                "§c• Global §7- Bonus sur tout",
                "",
                "§8Utilisez /boost pour voir vos boosts"
        ));

        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "help");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée l'item séparateur
     */
    private ItemStack createSeparatorItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        plugin.getGUIManager().applyName(meta, "§8");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton de fermeture
     */
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta, "§c✖ Fermer");
        plugin.getGUIManager().applyLore(meta, Arrays.asList(
                "",
                "§7Ferme ce menu",
                "",
                "§e▶ Cliquez pour fermer"
        ));

        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Gère les clics dans l'interface
     */
    public boolean handleClick(Player player, ItemStack clickedItem) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return false;
        }

        String action = clickedItem.getItemMeta().getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING);

        if (action == null) {
            return false;
        }

        switch (action) {
            case "close" -> {
                player.closeInventory();
                return true;
            }
            case "refresh" -> {
                openBoostMenu(player);
                player.sendMessage("§a🔄 Menu actualisé!");
                return true;
            }
            case "help" -> {
                player.closeInventory();
                player.sendMessage("§e📖 §lAide - Système de Boosts");
                player.sendMessage("§7Les boosts augmentent automatiquement vos gains!");
                player.sendMessage("§7Utilisez §e/boost §7pour voir vos boosts actifs.");
                player.sendMessage("§7Activez des items boost avec §eclic droit§7.");
                return true;
            }
            case "total_bonus" -> {
                player.closeInventory();
                showDetailedBonusInfo(player);
                return true;
            }
        }

        return false;
    }

    /**
     * Affiche les informations détaillées des bonus dans le chat avec HoverEvent
     * Version simplifiée utilisant la nouvelle API de GlobalBonusManager
     */
    private void showDetailedBonusInfo(Player player) {
        player.sendMessage("§6📊 §lBONUS TOTAUX DÉTAILLÉS");
        player.sendMessage("§7§m────────────────────────────────");

        var globalBonusManager = plugin.getGlobalBonusManager();
        if (globalBonusManager == null) {
            player.sendMessage("§cErreur: Système de bonus non disponible");
            return;
        }

        var activeBonuses = globalBonusManager.getAllActiveBonuses(player);

        if (activeBonuses.isEmpty()) {
            player.sendMessage("§7Aucun bonus actif actuellement.");
            player.sendMessage("§7Vos gains utilisent les valeurs de base.");
        } else {
            player.sendMessage("§7Multiplicateurs appliqués à vos gains:");
            player.sendMessage("§8(Survolez pour voir les sources détaillées)");
            player.sendMessage("");

            // Afficher toutes les catégories non nulles
            for (GlobalBonusManager.BonusCategory category : GlobalBonusManager.BonusCategory.values()) {
                var details = globalBonusManager.getBonusSourcesDetails(player, category);
                if (Math.abs(details.getTotalBonus()) <= 0.0001) continue;

                String arrow = details.getTotalBonus() > 0 ? "§a↗" : "§c↘";

                Component hover = createBonusHoverComponent(category, details);

                Component mainLine = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(category.getColor() + "▶ " + category.getDisplayName())
                        .hoverEvent(HoverEvent.showText(hover))
                        .decoration(TextDecoration.ITALIC, false);

                Component multLine = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize("  §7Multiplicateur: §f×" + String.format("%.3f", details.getTotalMultiplier()) +
                                " " + arrow + " §f" + (details.getTotalBonus() >= 0 ? "+" : "") + String.format("%.1f", details.getTotalBonus()) + "%")
                        .hoverEvent(HoverEvent.showText(hover))
                        .decoration(TextDecoration.ITALIC, false);

                // Envoyer directement les Components pour conserver les hovers
                player.sendMessage(mainLine);
                player.sendMessage(multLine);
            }

            player.sendMessage("");

            // Retiré: ligne 'Sources disponibles' et hover générique
        }

        player.sendMessage("§7§m────────────────────────────────");

        Component finalHover = createHelpHoverComponent();
        Component finalLine = Component.text("§7Ces bonus s'appliquent automatiquement à tous vos gains! §8[?]")
                .hoverEvent(HoverEvent.showText(finalHover))
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        // Envoyer directement le Component
        player.sendMessage(finalLine);
    }

    /**
     * Crée le texte de survol pour un bonus spécifique
     * SIMPLIFIÉ grâce à l'API de GlobalBonusManager!
     */
    private Component createBonusHoverComponent(GlobalBonusManager.BonusCategory category,
                                                GlobalBonusManager.BonusSourceDetails details) {
        StringBuilder sb = new StringBuilder();
        sb.append("§e§lSources du bonus ").append(category.getDisplayName()).append(":");
        sb.append("\n§7").append(category.getDescription());
        sb.append("\n");
        if (details.getCristalBonus() > 0) sb.append("\n§e⚡ Cristaux§7: ").append(details.getCristalBonus() >= 0 ? "+" : "").append(String.format("%.1f", details.getCristalBonus())).append("%");
        if (details.getProfessionBonus() > 0) sb.append("\n§d🔨 Talents Métiers§7: ").append(details.getProfessionBonus() >= 0 ? "+" : "").append(String.format("%.1f", details.getProfessionBonus())).append("%");
        if (details.getPrestigeBonus() > 0) sb.append("\n§5👑 Talents Prestige§7: ").append(details.getPrestigeBonus() >= 0 ? "+" : "").append(String.format("%.1f", details.getPrestigeBonus())).append("%");
        if (details.getTemporaryBoostBonus() > 0) sb.append("\n§b⚡ Boosts Temporaires§7: ").append(details.getTemporaryBoostBonus() >= 0 ? "+" : "").append(String.format("%.1f", details.getTemporaryBoostBonus())).append("%");
        if (details.getGangBonus() > 0) sb.append("\n§6🏰 Gang (Perm)§7: ").append(details.getGangBonus() >= 0 ? "+" : "").append(String.format("%.1f", details.getGangBonus())).append("%");
        if (details.getTemporaryGangBoostBonus() > 0) sb.append("\n§6🏰 Gang (Temp)§7: ").append(details.getTemporaryGangBoostBonus() >= 0 ? "+" : "").append(String.format("%.1f", details.getTemporaryGangBoostBonus())).append("%");
        if (details.getEnchantmentBonus() > 0) sb.append("\n§9✦ Enchantements§7: ").append(details.getEnchantmentBonus() >= 0 ? "+" : "").append(String.format("%.2f", details.getEnchantmentBonus())).append("%");
        if (details.getOverloadBonus() > 0) sb.append("\n§c🔥 Surcharge de Mine§7: ").append(details.getOverloadBonus() >= 0 ? "+" : "").append(String.format("%.1f", details.getOverloadBonus())).append("%");
        if (!details.getDetailedSources().isEmpty()) {
            sb.append("\n\n§8Détails:");
            for (var source : details.getDetailedSources().entrySet()) {
                sb.append("\n§8• ").append(source.getKey()).append(": ")
                  .append(source.getValue() >= 0 ? "+" : "")
                  .append(String.format("%.1f", source.getValue())).append("%");
            }
        }
        sb.append("\n\n§8Total: ").append(details.getTotalBonus() >= 0 ? "+" : "").append(String.format("%.1f", details.getTotalBonus())).append("%");
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(sb.toString()).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Crée le hover d'information générale
     */
    private Component createGeneralInfoHoverComponent() {
        String text = "§e§lExplication des sources de bonus:" +
                "\n\n§eCristaux:" +
                "\n§7• Bonus permanents basés sur le niveau des cristaux" +
                "\n§7• Se cumulent selon le type de cristal équipé" +
                "\n\n§dTalents Métiers:" +
                "\n§7• Bonus selon la progression dans les métiers" +
                "\n§7• Dépendent du métier actif et du niveau des talents" +
                "\n\n§5Talents Prestige:" +
                "\n§7• Bonus selon le niveau de prestige" +
                "\n§7• Débloques avec la progression de prestige" +
                "\n\n§bBoosts Temporaires:" +
                "\n§7• Bonus limités dans le temps" +
                "\n§7• Activés via des items ou par les admins";
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Crée le hover d'aide final
     */
    private Component createHelpHoverComponent() {
        String text = "§e§lComment ça marche:" +
                "\n\n§7• Les bonus se §acumulent§7 entre eux" +
                "\n§7• Ils s'appliquent §eautomatiquement§7 lors du minage" +
                "\n§7• Plus votre multiplicateur est élevé, plus vous gagnez!" +
                "\n\n§7Commandes utiles:" +
                "\n§e/boost §7- Gérer vos boosts temporaires" +
                "\n§e/cristal §7- Gérer vos cristaux" +
                "\n§e/metier §7- Voir votre progression métier";
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text).decoration(TextDecoration.ITALIC, false);
    }
}