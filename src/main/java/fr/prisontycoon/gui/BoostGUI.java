package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.boosts.PlayerBoost;
import fr.prisontycoon.managers.GlobalBonusManager;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
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

        Inventory gui = Bukkit.createInventory(null, size, "§6⚡ Vos Boosts Actifs");
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

        meta.setDisplayName(boost.getType().getFormattedName());

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
        meta.setLore(lore);

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

        meta.setDisplayName("§c❌ Aucun boost actif");
        meta.setLore(Arrays.asList(
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

        meta.setDisplayName("§6📊 Bonus Totaux Actifs");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Multiplicateurs de bonus actuels:");
        lore.add("");

        var globalBonusManager = plugin.getGlobalBonusManager();
        if (globalBonusManager != null) {
            var activeBonuses = globalBonusManager.getAllActiveBonuses(player);

            if (activeBonuses.isEmpty()) {
                lore.add("§7Aucun bonus actif actuellement");
            } else {
                for (var entry : activeBonuses.entrySet()) {
                    var category = entry.getKey();
                    var details = entry.getValue();

                    lore.add(category.getColor() + category.getEmoji() + " " + category.getDisplayName() +
                             "§7: §f×" + String.format("%.2f", details.getTotalMultiplier()) +
                             " §7(+" + String.format("%.1f", details.getTotalBonus()) + "%)");
                }
            }
        } else {
            lore.add("§cErreur: GlobalBonusManager non disponible");
        }

        lore.add("");
        lore.add("§7Ces multiplicateurs incluent:");
        lore.add("§7• Bonus des cristaux");
        lore.add("§7• Bonus des talents métiers");
        lore.add("§7• Bonus des talents prestige");
        lore.add("§b• Boosts temporaires");
        lore.add("");
        lore.add("§e▶ Cliquez pour les détails dans le chat");
        lore.add("§8Les bonus se cumulent automatiquement");

        meta.setLore(lore);
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

        meta.setDisplayName("§a🔄 Actualiser");
        meta.setLore(Arrays.asList(
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

        meta.setDisplayName("§e📖 Aide - Système de Boosts");
        meta.setLore(Arrays.asList(
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
        meta.setDisplayName("§8");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton de fermeture
     */
    private ItemStack createCloseButton() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§c✖ Fermer");
        meta.setLore(Arrays.asList(
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

            for (var entry : activeBonuses.entrySet()) {
                var category = entry.getKey();
                var details = entry.getValue();

                String arrow = details.getTotalBonus() > 0 ? "§a↗" : "§7→";

                // Crée le composant principal
                TextComponent mainComponent = new TextComponent(
                        category.getColor() + "▶ " + category.getDisplayName());

                // Crée le composant du multiplicateur
                TextComponent multiplierComponent = new TextComponent(
                        "  §7Multiplicateur: §f×" + String.format("%.3f", details.getTotalMultiplier()) +
                        " " + arrow + " §f+" + String.format("%.1f", details.getTotalBonus()) + "%");

                // Crée le texte de survol avec les sources détaillées (simplifié!)
                ComponentBuilder hoverText = createBonusHoverText(category, details);

                // Ajoute le hover event
                HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText.create());
                mainComponent.setHoverEvent(hoverEvent);
                multiplierComponent.setHoverEvent(hoverEvent);

                // Envoie les composants
                player.spigot().sendMessage(mainComponent);
                player.spigot().sendMessage(multiplierComponent);
            }

            player.sendMessage("");

            // Message d'information avec hover
            TextComponent infoComponent = new TextComponent(
                    "§7§lSources disponibles: §eCristaux §7| §dMétiers §7| §5Prestige §7| §bBoosts");
            ComponentBuilder infoHover = createGeneralInfoHover();
            infoComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, infoHover.create()));
            player.spigot().sendMessage(infoComponent);
        }

        player.sendMessage("§7§m────────────────────────────────");

        // Message final avec hover d'aide
        TextComponent finalComponent = new TextComponent(
                "§7Ces bonus s'appliquent automatiquement à tous vos gains! §8[?]");
        ComponentBuilder finalHover = createHelpHover();
        finalComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, finalHover.create()));
        player.spigot().sendMessage(finalComponent);
    }

    /**
     * Crée le texte de survol pour un bonus spécifique
     * SIMPLIFIÉ grâce à l'API de GlobalBonusManager!
     */
    private ComponentBuilder createBonusHoverText(GlobalBonusManager.BonusCategory category,
                                                  GlobalBonusManager.BonusSourceDetails details) {
        ComponentBuilder builder = new ComponentBuilder(
                "§e§lSources du bonus " + category.getDisplayName() + ":");

        builder.append("\n§7" + category.getDescription());
        builder.append("\n");

        // Utilise les données déjà calculées par GlobalBonusManager
        if (details.getCristalBonus() > 0) {
            builder.append("\n§e⚡ Cristaux§7: +" + String.format("%.1f", details.getCristalBonus()) + "%");
        }

        if (details.getProfessionBonus() > 0) {
            builder.append("\n§d🔨 Talents Métiers§7: +" + String.format("%.1f", details.getProfessionBonus()) + "%");
        }

        if (details.getPrestigeBonus() > 0) {
            builder.append("\n§5👑 Talents Prestige§7: +" + String.format("%.1f", details.getPrestigeBonus()) + "%");
        }

        if (details.getTemporaryBoostBonus() > 0) {
            builder.append("\n§b⚡ Boosts Temporaires§7: +" + String.format("%.1f", details.getTemporaryBoostBonus()) + "%");
        }

        // Affiche les sources détaillées si disponibles
        if (!details.getDetailedSources().isEmpty()) {
            builder.append("\n\n§8Détails:");
            for (var source : details.getDetailedSources().entrySet()) {
                builder.append("\n§8• " + source.getKey() + ": +" +
                               String.format("%.1f", source.getValue()) + "%");
            }
        }

        builder.append("\n\n§8Total: +" + String.format("%.1f", details.getTotalBonus()) + "%");

        return builder;
    }

    /**
     * Crée le hover d'information générale
     */
    private ComponentBuilder createGeneralInfoHover() {
        ComponentBuilder builder = new ComponentBuilder("§e§lExplication des sources de bonus:");
        builder.append("\n\n§eCristaux:");
        builder.append("\n§7• Bonus permanents basés sur le niveau des cristaux");
        builder.append("\n§7• Se cumulent selon le type de cristal équipé");
        builder.append("\n\n§dTalents Métiers:");
        builder.append("\n§7• Bonus selon la progression dans les métiers");
        builder.append("\n§7• Dépendent du métier actif et du niveau des talents");
        builder.append("\n\n§5Talents Prestige:");
        builder.append("\n§7• Bonus selon le niveau de prestige");
        builder.append("\n§7• Débloques avec la progression de prestige");
        builder.append("\n\n§bBoosts Temporaires:");
        builder.append("\n§7• Bonus limités dans le temps");
        builder.append("\n§7• Activés via des items ou par les admins");
        return builder;
    }

    /**
     * Crée le hover d'aide final
     */
    private ComponentBuilder createHelpHover() {
        ComponentBuilder builder = new ComponentBuilder("§e§lComment ça marche:");
        builder.append("\n\n§7• Les bonus se §acumulent§7 entre eux");
        builder.append("\n§7• Ils s'appliquent §eautomatiquement§7 lors du minage");
        builder.append("\n§7• Plus votre multiplicateur est élevé, plus vous gagnez!");
        builder.append("\n\n§7Commandes utiles:");
        builder.append("\n§e/boost §7- Gérer vos boosts temporaires");
        builder.append("\n§e/cristal §7- Gérer vos cristaux");
        builder.append("\n§e/metier §7- Voir votre progression métier");
        return builder;
    }
}