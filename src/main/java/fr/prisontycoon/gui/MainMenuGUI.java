package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.Gang;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.ProfessionManager;
import fr.prisontycoon.utils.NumberFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Menu principal général (navigation)
 * Utilise Adventure pour les lores et évite les APIs dépréciées.
 */
public class MainMenuGUI {

    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;

    public MainMenuGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "main_menu_action");
    }

    public void openMainMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(54, "§8• §6Menu Principal §8•");
        plugin.getGUIManager().fillBorders(gui);

        // Placeholder infos joueur (tête)
        gui.setItem(4, createPlayerInfo(player));

        // Ligne du haut (navigation principale)
        gui.setItem(20, createNavItem(
                Material.NETHER_STAR,
                "§e✦ §lWarps",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Accédez à tous les warps (mines, zones)",
                        "§7Inclut: §eMines§7, §dCrates§7, §6Banque§7, §bRecherche",
                        "",
                        "§e▶ Cliquez pour ouvrir",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "warp"));

        gui.setItem(21, createNavItem(
                Material.DIAMOND_PICKAXE,
                "§d⚒ §lMétiers",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Gérez votre métier, talents et kits",
                        "§7Progressez jusqu'au niveau §e10 §7par métier",
                        "",
                        "§e▶ Cliquez pour ouvrir",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "profession"));

        // Accès au menu des compagnons (Pets)
        gui.setItem(22, createNavItem(
                Material.WOLF_SPAWN_EGG,
                "§6🐾 §lCompagnons",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Gérez vos compagnons équipés et votre collection",
                        "§7Consultez les synergies et statistiques",
                        "",
                        "§e▶ Cliquez pour ouvrir",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "pets"));

        gui.setItem(23, createGangNavItem(player));

        gui.setItem(24, createNavItem(
                Material.BEACON,
                "§6⚡ §lBoosts",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Consultez et gérez vos boosts actifs",
                        "§7Effets temporaires et multiplicateurs",
                        "",
                        "§e▶ Cliquez pour ouvrir",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "boost"));

        // Ligne centrale
        gui.setItem(29, createNavItem(
                Material.HOPPER,
                "§b⛏ §lAutomineurs",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Gérez vos automineurs, stockage",
                        "§7carburant et condensation",
                        "",
                        "§e▶ Cliquez pour ouvrir",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "autominer"));

        gui.setItem(30, createNavItem(
                Material.ENCHANTED_BOOK,
                "§5📚 §lBoutique de Livres",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Achetez des livres d'enchantements",
                        "§7uniques et puissants",
                        "",
                        "§e▶ Cliquez pour ouvrir",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "bookshop"));

        gui.setItem(31, createNavItem(
                Material.EMERALD,
                "§a🛒 §lShop",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Accédez au magasin par catégories",
                        "§7PvP, Blocs, Outils, Redstone, etc.",
                        "",
                        "§e▶ Cliquez pour ouvrir",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "shop"));

        // Placeholders (implémentations futures)
        gui.setItem(32, createDisabledNavItem(
                Material.ENCHANTING_TABLE,
                "§b🔬 §lRecherche",
                List.of(
                        "§7Fonctionnalité à venir",
                        "§8Recherche & améliorations avancées"
                ),
                "recherche"));

        gui.setItem(33, createDisabledNavItem(
                Material.GRASS_BLOCK,
                "§a🏝 §lÎle",
                List.of(
                        "§7Fonctionnalité à venir",
                        "§8Gestion d'île et progression"
                ),
                "ile"));

        gui.setItem(41, createDisabledNavItem(
                Material.EMERALD,
                "§a🛒 §lBoutique",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Fonctionnalité à venir",
                        "§8Boutique globale & cosmétiques",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "boutique"));

        gui.setItem(42, createDisabledNavItem(
                Material.BOOK,
                "§e❓ §lTutoriel",
                List.of(
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                        "§7Fonctionnalité à venir",
                        "§8Guide rapide et astuces",
                        "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
                ),
                "tutoriel"));

        // Fermer
        gui.setItem(49, createNavItem(
                Material.BARRIER,
                "§c✖ §lFermer",
                List.of("§7Fermer ce menu"),
                "close"));

        plugin.getGUIManager().registerOpenGUI(player, GUIType.MAIN_MENU, gui);
        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.05f);
    }

    public void handleClick(Player player, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "warp" -> plugin.getWarpGUI().openWarpMenu(player);
            case "profession" -> plugin.getProfessionGUI().openProfessionMenu(player);
            case "gang" -> plugin.getGangGUI().openMainMenu(player);
            case "boost" -> plugin.getBoostGUI().openBoostMenu(player);
            case "autominer" -> plugin.getAutominerGUI().openMainMenu(player);
            case "bookshop" -> plugin.getBookShopGUI().openSelector(player);
            case "pets" -> plugin.getPetsMenuGUI().openPetsMenu(player);
            case "shop" -> plugin.getShopGUI().openMainShop(player);
            case "recherche", "ile", "boutique", "tutoriel" -> {
                player.closeInventory();
                player.sendMessage("§e🔮 Cette fonctionnalité arrive bientôt !");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            }
            case "close" -> player.closeInventory();
        }
    }

    // --- Helpers ---
    private ItemStack createNavItem(Material material, String displayName, List<String> loreLines, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        plugin.getGUIManager().applyName(meta, displayName);
        plugin.getGUIManager().applyLore(meta, loreLines);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDisabledNavItem(Material material, String displayName, List<String> loreLines, String action) {
        return createNavItem(material, displayName, loreLines, action);
    }

    private ItemStack createPlayerInfo(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        meta.setOwningPlayer(player);
        plugin.getGUIManager().applyName(meta, "§6📊 §l" + player.getName());

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        String[] rankAndColor = plugin.getMineManager().getRankAndColor(player);
        String rankUpper = rankAndColor[0] != null ? rankAndColor[0].toUpperCase() : "A";
        String rankColor = rankAndColor[1] != null ? rankAndColor[1] : "§7";
        lore.add("§7Rang: " + rankColor + rankUpper);
        lore.add("§7Prestige: §e" + data.getPrestigeDisplayName());

        String professionName = "Aucun";
        if (data.getActiveProfession() != null) {
            ProfessionManager.Profession prof = plugin.getProfessionManager().getProfession(data.getActiveProfession());
            if (prof != null) professionName = prof.displayName();
        }
        lore.add("§7Métier: §e" + professionName);

        // Gang
        String gangLine;
        Gang gang = null;
        if (data.getGangId() != null) {
            gang = plugin.getGangManager().getGang(data.getGangId());
        }
        if (gang != null) {
            gangLine = "§7Gang: §6" + gang.getName() + " §7[§e" + gang.getTag() + "§7]";
        } else {
            gangLine = "§7Gang: §7Aucun";
        }
        lore.add(gangLine);

        lore.add(" ");
        lore.add("§6💰 §lÉconomie");
        lore.add("§7Coins: §e" + NumberFormatter.formatWithColor(data.getCoins()));
        lore.add("§7Tokens: §e" + NumberFormatter.formatWithColor(data.getTokens()));
        lore.add("§7Expérience: §e" + NumberFormatter.formatWithColor(data.getExperience()));
        lore.add("§7Beacons: §e" + NumberFormatter.formatWithColor(data.getBeacons()));
        lore.add(" ");
        lore.add("§8Astuce: Utilisez ce menu pour naviguer partout !");

        plugin.getGUIManager().applyLore(meta, lore);
        head.setItemMeta(meta);
        return head;
    }

    private Component deserializeNoItalic(String legacy) {
        return LegacyComponentSerializer.legacySection().deserialize(legacy)
                .decoration(TextDecoration.ITALIC, false);
    }

    private List<Component> deserializeNoItalics(List<String> lines) {
        return lines.stream().map(this::deserializeNoItalic).toList();
    }

    private ItemStack createGangNavItem(Player player) {
        ItemStack item = new ItemStack(Material.WHITE_BANNER);
        ItemMeta baseMeta = item.getItemMeta();

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data.getGangId() != null) {
            Gang gang = plugin.getGangManager().getGang(data.getGangId());
            if (gang != null && baseMeta instanceof BannerMeta bannerMeta) {
                if (gang.getBannerPatterns() != null && !gang.getBannerPatterns().isEmpty()) {
                    bannerMeta.setPatterns(gang.getBannerPatterns());
                }
                bannerMeta.displayName(deserializeNoItalic("§6☠ §lGang"));

                List<String> lore = new ArrayList<>();
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                lore.add("§7Gérez votre gang, membres et talents");
                lore.add("§7Nom: §e" + gang.getName() + " §7[§e" + gang.getTag() + "§7]");
                lore.add(" ");
                lore.add("§e▶ Cliquez pour ouvrir");
                lore.add("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                bannerMeta.lore(deserializeNoItalics(lore));
                bannerMeta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "gang");
                item.setItemMeta(bannerMeta);
                return item;
            }
        }

        // Fallback (pas de gang ou pas de motifs)
        ItemMeta meta = item.getItemMeta();
        meta.displayName(deserializeNoItalic("§6☠ §lGang"));
        List<String> lore = List.of(
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "§7Gérez votre gang, membres et talents",
                "§7Aucun gang: §cRejoignez-en un!",
                "",
                "§e▶ Cliquez pour ouvrir",
                "§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"
        );
        meta.lore(deserializeNoItalics(lore));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "gang");
        item.setItemMeta(meta);
        return item;
    }
}


