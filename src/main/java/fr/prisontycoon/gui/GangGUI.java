package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.Gang;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.gangs.GangRole;
import fr.prisontycoon.gangs.GangTalent;
import fr.prisontycoon.gangs.GangBoostType;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.BannerMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface graphique pour le système de gangs
 */
public class GangGUI {

    private final PrisonTycoon plugin;
    private final Map<UUID, String> openGuis = new ConcurrentHashMap<>();

    public GangGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre le menu principal des gangs
     */
    public void openMainMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        if (playerData.getGangId() == null) {
            openNoGangMenu(player);
        } else {
            Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
            if (gang != null) {
                openGangMenu(player, gang);
            } else {
                openNoGangMenu(player);
            }
        }
    }

    /**
     * Menu pour les joueurs sans gang
     */
    private void openNoGangMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6⭐ §lGANG - Menu Principal §6⭐");
        openGuis.put(player.getUniqueId(), "no_gang_menu");

        fillWithGlass(gui, DyeColor.GRAY);

        // Créer un gang
        ItemStack createGang = new ItemStack(Material.EMERALD);
        ItemMeta createMeta = createGang.getItemMeta();
        createMeta.setDisplayName("§a✅ §lCréer un Gang");
        List<String> createLore = new ArrayList<>();
        createLore.add("");
        createLore.add("§7Créez votre propre gang et");
        createLore.add("§7invitez vos amis à vous rejoindre!");
        createLore.add("");
        createLore.add("§e💰 Coût: §610,000 beacons");
        createLore.add("§e🏆 Rang minimum: §6G");
        createLore.add("");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (plugin.getMineManager().isRankSufficient(plugin.getMineManager().getCurrentRank(player), "g") && playerData.getBeacons() >= 10000) {
            createLore.add("§a▶ Cliquez pour créer!");
        } else {
            createLore.add("§c❌ Conditions non remplies");
            if (!plugin.getMineManager().isRankSufficient(plugin.getMineManager().getCurrentRank(player), "g")) {
                createLore.add("§c  - Rang insuffisant");
            }
            if (playerData.getBeacons() < 10000) {
                createLore.add("§c  - Beacons insuffisants");
            }
        }
        createMeta.setLore(createLore);
        createGang.setItemMeta(createMeta);
        gui.setItem(11, createGang);

        // Liste des gangs
        ItemStack listGangs = new ItemStack(Material.BOOK);
        ItemMeta listMeta = listGangs.getItemMeta();
        listMeta.setDisplayName("§e📚 §lListe des Gangs");
        List<String> listLore = new ArrayList<>();
        listLore.add("");
        listLore.add("§7Consultez la liste de tous");
        listLore.add("§7les gangs du serveur.");
        listLore.add("");
        listLore.add("§a▶ Cliquez pour voir!");
        listMeta.setLore(listLore);
        listGangs.setItemMeta(listMeta);
        gui.setItem(13, listGangs);

        // Invitations en attente
        ItemStack invitations = new ItemStack(Material.PAPER);
        ItemMeta inviteMeta = invitations.getItemMeta();
        inviteMeta.setDisplayName("§6📨 §lInvitations");
        List<String> inviteLore = new ArrayList<>();
        inviteLore.add("");

        if (playerData.getGangInvitation() != null) {
            Gang invitingGang = plugin.getGangManager().getGang(playerData.getGangInvitation());
            if (invitingGang != null) {
                inviteLore.add("§aVous avez une invitation de:");
                inviteLore.add("§e" + invitingGang.getName() + " §7[§e" + invitingGang.getTag() + "§7]");
                inviteLore.add("");
                inviteLore.add("§a▶ Clic gauche pour accepter");
                inviteLore.add("§c▶ Clic droit pour refuser");
            } else {
                inviteLore.add("§7Aucune invitation en attente");
                inviteLore.add("");
                inviteLore.add("§8Les invitations apparaîtront ici");
            }
        } else {
            inviteLore.add("§7Aucune invitation en attente");
            inviteLore.add("");
            inviteLore.add("§8Les invitations apparaîtront ici");
        }

        inviteMeta.setLore(inviteLore);
        invitations.setItemMeta(inviteMeta);
        gui.setItem(15, invitations);

        // Fermer
        gui.setItem(22, createCloseButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Menu principal pour les membres d'un gang
     */
    private void openGangMenu(Player player, Gang gang) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6☠ §l" + gang.getName() + " §7[§e" + gang.getTag() + "§7] §6☠");
        openGuis.put(player.getUniqueId(), "gang_menu");

        fillWithGlass(gui, DyeColor.YELLOW);

        GangRole playerRole = gang.getMemberRole(player.getUniqueId());

        // Informations du gang
        ItemStack gangInfo = new ItemStack(Material.WHITE_BANNER, 1, DyeColor.YELLOW.getWoolData());
        ItemMeta infoMeta = gangInfo.getItemMeta();
        infoMeta.setDisplayName("§e📋 §lInformations du Gang");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§7Nom: §e" + gang.getName());
        infoLore.add("§7Tag: §7[§e" + gang.getTag() + "§7]");
        infoLore.add("§7Niveau: §6" + gang.getLevel());
        infoLore.add("§7Membres: §a" + gang.getMembers().size() + "§7/§a" + gang.getMaxMembers());
        if (gang.getDescription() != null && !gang.getDescription().isEmpty()) {
            infoLore.add("§7Description: §f" + gang.getDescription());
        }
        infoLore.add("");
        infoLore.add("§7Votre rôle: " + playerRole.getDisplayName());
        infoLore.add("");
        infoLore.add("§a▶ Cliquez pour plus d'infos!");
        infoMeta.setLore(infoLore);
        gangInfo.setItemMeta(infoMeta);
        gui.setItem(4, gangInfo);

        // Membres
        ItemStack members = new ItemStack(Material.SKELETON_SKULL, 1, (short) 3);
        ItemMeta membersMeta = members.getItemMeta();
        membersMeta.setDisplayName("§b👥 §lMembres du Gang");
        List<String> membersLore = new ArrayList<>();
        membersLore.add("");
        membersLore.add("§7Consultez la liste des membres");
        membersLore.add("§7et gérez les permissions.");
        membersLore.add("");
        membersLore.add("§7Membres: §a" + gang.getMembers().size() + "§7/§a" + gang.getMaxMembers());
        membersLore.add("");
        membersLore.add("§a▶ Cliquez pour voir!");
        membersMeta.setLore(membersLore);
        members.setItemMeta(membersMeta);
        gui.setItem(10, members);

        // Banque du gang
        ItemStack bank = new ItemStack(Material.CHEST);
        ItemMeta bankMeta = bank.getItemMeta();
        bankMeta.setDisplayName("§6💰 §lBanque du Gang");
        List<String> bankLore = new ArrayList<>();
        bankLore.add("");
        bankLore.add("§7Solde: §e" + NumberFormatter.format(gang.getBankBalance()) + " coins");
        bankLore.add("");
        bankLore.add("§7La banque sert à financer:");
        bankLore.add("§7• Améliorations du gang");
        bankLore.add("§7• Achat de talents");
        bankLore.add("§7• Boosts temporaires");
        bankLore.add("");
        bankLore.add("§a▶ Cliquez pour déposer!");
        bankMeta.setLore(bankLore);
        bank.setItemMeta(bankMeta);
        gui.setItem(12, bank);

        // Améliorations (chef seulement)
        if (playerRole == GangRole.CHEF) {
            ItemStack upgrade = new ItemStack(Material.ANVIL);
            ItemMeta upgradeMeta = upgrade.getItemMeta();
            upgradeMeta.setDisplayName("§c⚡ §lAméliorations");
            List<String> upgradeLore = new ArrayList<>();
            upgradeLore.add("");
            upgradeLore.add("§7Niveau actuel: §6" + gang.getLevel());

            if (gang.getLevel() < 10) {
                long nextCost = plugin.getGangManager().getUpgradeCost(gang.getLevel() + 1);
                upgradeLore.add("§7Prochain niveau: §e" + NumberFormatter.format(nextCost) + " coins");
                upgradeLore.add("");
                upgradeLore.add("§aAvantages du niveau " + (gang.getLevel() + 1) + ":");
                upgradeLore.addAll(plugin.getGangManager().getLevelBenefits(gang.getLevel() + 1));
            } else {
                upgradeLore.add("§a✅ Niveau maximum atteint!");
            }

            upgradeLore.add("");
            upgradeLore.add("§a▶ Cliquez pour améliorer!");
            upgradeMeta.setLore(upgradeLore);
            upgrade.setItemMeta(upgradeMeta);
            gui.setItem(14, upgrade);
        }

        // Talents du gang
        ItemStack talents = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta talentsMeta = talents.getItemMeta();
        talentsMeta.setDisplayName("§5🎯 §lTalents du Gang");
        List<String> talentsLore = new ArrayList<>();
        talentsLore.add("");
        talentsLore.add("§7Achetez des talents permanents");
        talentsLore.add("§7qui affectent tous les membres.");
        talentsLore.add("");
        talentsLore.add("§7Talents actifs: §a" + gang.getTalents().size());
        talentsLore.add("");
        talentsLore.add("§a▶ Cliquez pour voir!");
        talentsMeta.setLore(talentsLore);
        talents.setItemMeta(talentsMeta);
        gui.setItem(16, talents);

        // Boutique du gang
        ItemStack shop = new ItemStack(Material.EMERALD);
        ItemMeta shopMeta = shop.getItemMeta();
        shopMeta.setDisplayName("§a🛒 §lBoutique du Gang");
        List<String> shopLore = new ArrayList<>();
        shopLore.add("");
        shopLore.add("§7Achetez des boosts temporaires");
        shopLore.add("§7et autres objets pour le gang.");
        shopLore.add("");
        shopLore.add("§a▶ Cliquez pour acheter!");
        shopMeta.setLore(shopLore);
        shop.setItemMeta(shopMeta);
        gui.setItem(28, shop);

        // Chat du gang
        ItemStack chat = new ItemStack(Material.PAPER);
        ItemMeta chatMeta = chat.getItemMeta();
        chatMeta.setDisplayName("§e💬 §lChat du Gang");
        List<String> chatLore = new ArrayList<>();
        chatLore.add("");
        chatLore.add("§7Communiquez avec les");
        chatLore.add("§7membres de votre gang.");
        chatLore.add("");
        chatLore.add("§7Commande: §e/g <message>");
        chatLore.add("");
        chatLore.add("§8Cliquez pour fermer le menu");
        chatMeta.setLore(chatLore);
        chat.setItemMeta(chatMeta);
        gui.setItem(30, chat);

        // Paramètres du gang (chef et officiers)
        if (playerRole == GangRole.CHEF || playerRole == GangRole.OFFICIER) {
            ItemStack settings = new ItemStack(Material.COMPARATOR);
            ItemMeta settingsMeta = settings.getItemMeta();
            settingsMeta.setDisplayName("§c⚙️ §lParamètres");
            List<String> settingsLore = new ArrayList<>();
            settingsLore.add("");
            settingsLore.add("§7Gérez les paramètres du gang:");
            settingsLore.add("§7• Description");
            settingsLore.add("§7• Bannière");
            if (playerRole == GangRole.CHEF) {
                settingsLore.add("§7• Renommer");
                settingsLore.add("§7• Dissoudre");
            }
            settingsLore.add("");
            settingsLore.add("§a▶ Cliquez pour gérer!");
            settingsMeta.setLore(settingsLore);
            settings.setItemMeta(settingsMeta);
            gui.setItem(32, settings);
        }

        // Quitter le gang (sauf chef)
        if (playerRole != GangRole.CHEF) {
            ItemStack leave = new ItemStack(Material.BARRIER);
            ItemMeta leaveMeta = leave.getItemMeta();
            leaveMeta.setDisplayName("§c❌ §lQuitter le Gang");
            List<String> leaveLore = new ArrayList<>();
            leaveLore.add("");
            leaveLore.add("§cQuitte définitivement ce gang.");
            leaveLore.add("");
            leaveLore.add("§c▶ Cliquez pour quitter!");
            leaveMeta.setLore(leaveLore);
            leave.setItemMeta(leaveMeta);
            gui.setItem(34, leave);
        }

        // Fermer
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Ouvre le menu des informations d'un gang
     */
    public void openGangInfo(Player player, Gang gang) {
        Inventory gui = Bukkit.createInventory(null, 54, "§6📋 §l" + gang.getName() + " - Informations");
        openGuis.put(player.getUniqueId(), "gang_info");

        fillWithGlass(gui, DyeColor.LIGHT_BLUE);

        // Bannière du gang au centre
        ItemStack banner = createGangBanner(gang);
        gui.setItem(4, banner);

        // Informations générales
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e📋 §lInformations Générales");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§7Nom: §e" + gang.getName());
        infoLore.add("§7Tag: §7[§e" + gang.getTag() + "§7]");
        infoLore.add("§7Niveau: §6" + gang.getLevel());
        infoLore.add("§7Créé le: §a" + formatDate(gang.getCreationDate()));
        infoLore.add("");
        if (gang.getDescription() != null && !gang.getDescription().isEmpty()) {
            infoLore.add("§7Description:");
            infoLore.add("§f" + gang.getDescription());
            infoLore.add("");
        }
        infoLore.add("§7Chef: §6" + getPlayerName(gang.getLeader()));
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(10, info);

        // Membres
        ItemStack members = new ItemStack(Material.SKELETON_SKULL, gang.getMembers().size(), (short) 3);
        ItemMeta membersMeta = members.getItemMeta();
        membersMeta.setDisplayName("§b👥 §lMembres §7(" + gang.getMembers().size() + "/" + gang.getMaxMembers() + ")");
        List<String> membersLore = new ArrayList<>();
        membersLore.add("");

        // Chef
        String leaderName = getPlayerName(gang.getLeader());
        membersLore.add("§6👑 " + leaderName + " §7(Chef)");

        // Officiers
        List<String> officers = new ArrayList<>();
        List<String> regularMembers = new ArrayList<>();

        for (UUID memberId : gang.getMembers().keySet()) {
            if (memberId.equals(gang.getLeader())) continue;

            GangRole role = gang.getMembers().get(memberId);
            String memberName = getPlayerName(memberId);

            if (role == GangRole.OFFICIER) {
                officers.add("§e⭐ " + memberName + " §7(Officier)");
            } else {
                regularMembers.add("§7• " + memberName + " §7(Membre)");
            }
        }

        membersLore.addAll(officers);
        membersLore.addAll(regularMembers);

        membersMeta.setLore(membersLore);
        members.setItemMeta(membersMeta);
        gui.setItem(12, members);

        // Statistiques
        ItemStack stats = new ItemStack(Material.DIAMOND);
        ItemMeta statsMeta = stats.getItemMeta();
        statsMeta.setDisplayName("§b📊 §lStatistiques");
        List<String> statsLore = new ArrayList<>();
        statsLore.add("");
        statsLore.add("§7Niveau: §6" + gang.getLevel());
        statsLore.add("§7Places membres: §a" + gang.getMaxMembers());

        // Bonus actuels
        if (gang.getLevel() >= 2) {
            int sellBonus = plugin.getGangManager().getSellBonus(gang.getLevel());
            if (sellBonus > 0) {
                statsLore.add("§7Bonus vente: §a+" + sellBonus + "%");
            }
        }

        if (gang.getLevel() == 5 || gang.getLevel() >= 8) {
            if (gang.getLevel() == 5) {
                statsLore.add("§7Parcelle de gang: §a✅");
            } else {
                statsLore.add("§7Extension parcelle: §a✅");
            }
        }

        if (gang.getLevel() == 10) {
            statsLore.add("§7Cosmétiques: §a✅");
        }

        statsMeta.setLore(statsLore);
        stats.setItemMeta(statsMeta);
        gui.setItem(14, stats);

        // Talents actifs
        ItemStack talents = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta talentsMeta = talents.getItemMeta();
        talentsMeta.setDisplayName("§5🎯 §lTalents Actifs");
        List<String> talentsLore = new ArrayList<>();
        talentsLore.add("");

        if (gang.getTalents().isEmpty()) {
            talentsLore.add("§7Aucun talent acheté");
        } else {
            for (Map.Entry<String, Integer> entry : gang.getTalents().entrySet()) {
                GangTalent talent = plugin.getGangManager().getTalent(entry.getKey());
                if (talent != null) {
                    talentsLore.add("§e• " + talent.getName() + " §7(Niveau " + entry.getValue() + ")");
                }
            }
        }

        talentsMeta.setLore(talentsLore);
        talents.setItemMeta(talentsMeta);
        gui.setItem(16, talents);

        // Retour
        gui.setItem(45, createBackButton());

        // Fermer
        gui.setItem(49, createCloseButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Ouvre la liste des gangs
     */
    public void openGangList(Player player) {
        openGangList(player, 0);
    }

    /**
     * Ouvre le menu d'amélioration du gang
     */
    public void openUpgradeMenu(Player player, Gang gang) {
        Inventory gui = Bukkit.createInventory(null, 36, "§c⚡ §lAméliorations - " + gang.getName());
        openGuis.put(player.getUniqueId(), "upgrade_menu");

        fillWithGlass(gui, DyeColor.RED);

        // Niveau actuel
        ItemStack currentLevel = new ItemStack(Material.DIAMOND, gang.getLevel());
        ItemMeta currentMeta = currentLevel.getItemMeta();
        currentMeta.setDisplayName("§6📈 §lNiveau Actuel: " + gang.getLevel());
        List<String> currentLore = new ArrayList<>();
        currentLore.add("");
        currentLore.add("§7Avantages actuels:");
        currentLore.addAll(plugin.getGangManager().getLevelBenefits(gang.getLevel()));
        currentMeta.setLore(currentLore);
        currentLevel.setItemMeta(currentMeta);
        gui.setItem(11, currentLevel);

        if (gang.getLevel() < 10) {
            // Prochain niveau
            ItemStack nextLevel = new ItemStack(Material.EMERALD, gang.getLevel() + 1);
            ItemMeta nextMeta = nextLevel.getItemMeta();
            nextMeta.setDisplayName("§a⬆ §lProchain Niveau: " + (gang.getLevel() + 1));
            List<String> nextLore = new ArrayList<>();
            nextLore.add("");

            long cost = plugin.getGangManager().getUpgradeCost(gang.getLevel() + 1);
            nextLore.add("§7Coût: §e" + NumberFormatter.format(cost) + " coins");
            nextLore.add("§7Banque: §e" + NumberFormatter.format(gang.getBankBalance()) + " coins");
            nextLore.add("");

            if (gang.getBankBalance() >= cost) {
                nextLore.add("§a✅ Fonds suffisants!");
                nextLore.add("");
                nextLore.add("§aNouveaux avantages:");
                nextLore.addAll(plugin.getGangManager().getLevelBenefits(gang.getLevel() + 1));
                nextLore.add("");
                nextLore.add("§a▶ Cliquez pour améliorer!");
            } else {
                nextLore.add("§c❌ Fonds insuffisants");
                nextLore.add("§7Manque: §c" + NumberFormatter.format(cost - gang.getBankBalance()) + " coins");
            }

            nextMeta.setLore(nextLore);
            nextLevel.setItemMeta(nextMeta);
            gui.setItem(15, nextLevel);
        } else {
            // Niveau maximum
            ItemStack maxLevel = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta maxMeta = maxLevel.getItemMeta();
            maxMeta.setDisplayName("§6👑 §lNiveau Maximum Atteint!");
            List<String> maxLore = new ArrayList<>();
            maxLore.add("");
            maxLore.add("§aFélicitations! Votre gang a");
            maxLore.add("§aatteint le niveau maximum.");
            maxLore.add("");
            maxLore.add("§7Tous les avantages sont débloqués:");
            maxLore.addAll(plugin.getGangManager().getLevelBenefits(10));
            maxMeta.setLore(maxLore);
            maxLevel.setItemMeta(maxMeta);
            gui.setItem(13, maxLevel);
        }

        // Retour
        gui.setItem(27, createBackButton());

        // Fermer
        gui.setItem(31, createCloseButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.0f);
    }

    /**
     * Ouvre la boutique du gang
     */
    public void openShop(Player player, Gang gang) {
        Inventory gui = Bukkit.createInventory(null, 54, "§a🛒 §lBoutique - " + gang.getName());
        openGuis.put(player.getUniqueId(), "gang_shop");

        fillWithGlass(gui, DyeColor.GREEN);

        // Boosts temporaires
        int slot = 10;
        for (GangBoostType boostType : GangBoostType.values()) {
            for (int tier = 1; tier <= 3; tier++) {
                ItemStack boostItem = createBoostItem(boostType, tier, gang, player);
                gui.setItem(slot++, boostItem);

                if (slot == 17) slot = 19; // Skip to next row
                if (slot == 26) slot = 28; // Skip to next row
                if (slot == 35) slot = 37; // Skip to next row
                if (slot >= 44) break; // Limite d'espace
            }
            if (slot >= 44) break;
        }

        // Bannière du gang
        if (gang.getLevel() >= 10) {
            ItemStack banner = createGangBanner(gang);
            ItemMeta bannerMeta = banner.getItemMeta();
            bannerMeta.setDisplayName("§6🏳️ §lBannière du Gang");
            List<String> bannerLore = new ArrayList<>();
            bannerLore.add("");
            bannerLore.add("§7Achetez la bannière officielle");
            bannerLore.add("§7de votre gang!");
            bannerLore.add("");
            bannerLore.add("§7Prix: §e1,000 beacons");
            bannerLore.add("");
            bannerLore.add("§a▶ Cliquez pour acheter!");
            bannerMeta.setLore(bannerLore);
            banner.setItemMeta(bannerMeta);
            gui.setItem(49, banner);
        }

        // Retour
        gui.setItem(45, createBackButton());

        // Fermer
        gui.setItem(53, createCloseButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Ouvre le créateur de bannière
     */
    public void openBannerCreator(Player player, Gang gang) {
        Inventory gui = Bukkit.createInventory(null, 27, "§6🏳️ §lCréateur de Bannière");
        openGuis.put(player.getUniqueId(), "banner_creator");

        fillWithGlass(gui, DyeColor.YELLOW);

        // Instructions
        ItemStack instructions = new ItemStack(Material.BOOK);
        ItemMeta instructionsMeta = instructions.getItemMeta();
        instructionsMeta.setDisplayName("§e📖 §lInstructions");
        List<String> instructionsLore = new ArrayList<>();
        instructionsLore.add("");
        instructionsLore.add("§71. Placez une bannière dans le slot central");
        instructionsLore.add("§72. Personnalisez-la avec des motifs");
        instructionsLore.add("§73. Cliquez sur 'Confirmer' pour l'enregistrer");
        instructionsLore.add("");
        instructionsLore.add("§aCette bannière représentera votre gang!");
        instructionsMeta.setLore(instructionsLore);
        instructions.setItemMeta(instructionsMeta);
        gui.setItem(4, instructions);

        // Slot pour la bannière (sera géré par des événements spéciaux)
        ItemStack placeholder = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1, DyeColor.GRAY.getWoolData());
        ItemMeta placeholderMeta = placeholder.getItemMeta();
        placeholderMeta.setDisplayName("§7Placez votre bannière ici");
        placeholder.setItemMeta(placeholderMeta);
        gui.setItem(13, placeholder);

        // Confirmer
        ItemStack confirm = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName("§a✅ §lConfirmer");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");
        confirmLore.add("§7Enregistre cette bannière");
        confirmLore.add("§7comme bannière officielle du gang.");
        confirmLore.add("");
        confirmLore.add("§a▶ Cliquez pour confirmer!");
        confirmMeta.setLore(confirmLore);
        confirm.setItemMeta(confirmMeta);
        gui.setItem(20, confirm);

        // Annuler
        ItemStack cancel = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName("§c❌ §lAnnuler");
        cancel.setItemMeta(cancelMeta);
        gui.setItem(24, cancel);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    private void handleNoGangMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            case 11 -> { // Créer un gang
                player.closeInventory();
                player.sendMessage("§e💡 Utilisez §a/gang create <nom> <tag> §epour créer votre gang!");
                player.sendMessage("§7Exemple: §a/gang create MonGang MG");
            }
            case 13 -> openGangList(player); // Liste des gangs
            case 15 -> { // Invitations
                PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (playerData.getGangInvitation() != null) {
                    if (clickType == ClickType.LEFT) {
                        player.closeInventory();
                        plugin.getGangManager().acceptInvite(player);
                    } else if (clickType == ClickType.RIGHT) {
                        player.closeInventory();
                        plugin.getGangManager().denyInvite(player);
                    }
                } else {
                    player.sendMessage("§c❌ Aucune invitation en attente.");
                }
            }
            case 22 -> player.closeInventory(); // Fermer
        }
    }

    private void handleMainGangMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) return;

        switch (slot) {
            case 4 -> openGangInfo(player, gang); // Informations
            case 10 -> openMembersMenu(player, gang); // Membres
            case 12 -> { // Banque
                player.closeInventory();
                player.sendMessage("§e💡 Utilisez §a/gang deposit <montant> §epour déposer dans la banque!");
            }
            case 14 -> openUpgradeMenu(player, gang); // Améliorations
            case 16 -> openTalentsMenu(player, gang); // Talents
            case 28 -> openShop(player, gang); // Boutique
            case 30 -> { // Chat
                player.closeInventory();
                player.sendMessage("§e💡 Utilisez §a/g <message> §epour parler dans le chat du gang!");
            }
            case 32 -> openSettingsMenu(player, gang); // Paramètres
            case 34 -> { // Quitter
                player.closeInventory();
                player.sendMessage("§c⚠️ Êtes-vous sûr de vouloir quitter le gang?");
                player.sendMessage("§cTapez §e/gang leave §cpour confirmer.");
            }
            case 49 -> player.closeInventory(); // Fermer
        }
    }

    // Méthodes utilitaires
    private ItemStack createGangBanner(Gang gang) {
        ItemStack banner = new ItemStack(Material.WHITE_BANNER, 1, DyeColor.YELLOW.getWoolData());
        BannerMeta bannerMeta = (BannerMeta) banner.getItemMeta();
        bannerMeta.setDisplayName("§6🏳️ §l" + gang.getName());

        // Si le gang a une bannière personnalisée, l'utiliser
        if (gang.getBannerPatterns() != null && !gang.getBannerPatterns().isEmpty()) {
            bannerMeta.setPatterns(gang.getBannerPatterns());
        }

        banner.setItemMeta(bannerMeta);
        return banner;
    }

    private ItemStack createGangListItem(Gang gang, Player viewer) {
        ItemStack item = new ItemStack(Material.WHITE_BANNER, 1, DyeColor.YELLOW.getWoolData());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName("§6🏰 §l" + gang.getName() + " §7[§e" + gang.getTag() + "§7]");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Niveau: §6" + gang.getLevel());
        lore.add("§7Membres: §a" + gang.getMembers().size() + "§7/§a" + gang.getMaxMembers());
        lore.add("§7Chef: §6" + getPlayerName(gang.getLeader()));

        if (gang.getDescription() != null && !gang.getDescription().isEmpty()) {
            lore.add("");
            lore.add("§7Description: §f" + gang.getDescription());
        }

        lore.add("");
        lore.add("§a▶ Cliquez pour plus d'infos!");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createBoostItem(GangBoostType boostType, int tier, Gang gang, Player player) {
        ItemStack item = new ItemStack(boostType.getMaterial());
        ItemMeta meta = item.getItemMeta();

        String[] multipliers = {"1.5x", "2x", "3x"};
        int[] durations = {30, 60, 180}; // minutes
        long[] costs = boostType.getCosts();

        meta.setDisplayName(boostType.getColor() + "⚡ §l" + boostType.getDisplayName() + " " + multipliers[tier - 1]);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Multiplicateur: " + boostType.getColor() + multipliers[tier - 1]);
        lore.add("§7Durée: §e" + durations[tier - 1] + " minutes");
        lore.add("§7Coût: §e" + NumberFormatter.format(costs[tier - 1]) + " beacons");
        lore.add("");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (playerData.getBeacons() >= costs[tier - 1]) {
            lore.add("§a▶ Cliquez pour acheter!");
        } else {
            lore.add("§c❌ Beacons insuffisants");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCloseButton() {
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.setDisplayName("§c❌ §lFermer");
        close.setItemMeta(closeMeta);
        return close;
    }

    private ItemStack createBackButton() {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§a⬅ §lRetour");
        back.setItemMeta(backMeta);
        return back;
    }

    private void fillWithGlass(Inventory gui, DyeColor color) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1, color.getWoolData());
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName("§7");
        glass.setItemMeta(glassMeta);

        // Remplir les bordures
        for (int i = 0; i < 9; i++) gui.setItem(i, glass); // Première ligne
        for (int i = gui.getSize() - 9; i < gui.getSize(); i++) gui.setItem(i, glass); // Dernière ligne
        for (int i = 9; i < gui.getSize() - 9; i += 9) { // Côtés
            gui.setItem(i, glass);
            if (i + 8 < gui.getSize()) gui.setItem(i + 8, glass);
        }
    }

    private String getPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        return plugin.getPlayerDataManager().getPlayerName(playerId);
    }

    private String formatDate(long timestamp) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy").format(new java.util.Date(timestamp));
    }

    public void closeGui(Player player) {
        openGuis.remove(player.getUniqueId());
    }
    // Voici les implémentations des méthodes manquantes pour GangGUI

    /**
     * Gère la sélection d'une bannière (appelée depuis GangListener)
     */
    public void handleBannerSelection(Player player, ItemStack selectedBanner) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());

        if (gang == null) {
            player.sendMessage("§c❌ Vous n'êtes dans aucun gang!");
            return;
        }

        GangRole playerRole = gang.getMemberRole(player.getUniqueId());
        if (playerRole != GangRole.CHEF) {
            player.sendMessage("§c❌ Seul le chef peut modifier la bannière du gang!");
            return;
        }

        if (selectedBanner.getType().name().contains("BANNER")) {
            // Ouvrir le créateur de bannière avec la bannière sélectionnée
            openBannerCreator(player, gang);
            player.sendMessage("§a✅ Bannière sélectionnée! Personnalisez-la et confirmez.");
        }
    }

    /**
     * Ouvre le menu des membres du gang
     */
    private void openMembersMenu(Player player, Gang gang) {
        Inventory gui = Bukkit.createInventory(null, 54, "§b👥 §l" + gang.getName() + " - Membres");
        openGuis.put(player.getUniqueId(), "members_menu");

        fillWithGlass(gui, DyeColor.BLUE);

        GangRole playerRole = gang.getMemberRole(player.getUniqueId());
        boolean canManage = (playerRole == GangRole.CHEF || playerRole == GangRole.OFFICIER);

        // Afficher les membres
        int slot = 10;
        for (Map.Entry<UUID, GangRole> entry : gang.getMembers().entrySet()) {
            if (slot >= 44) break; // Limite d'espace

            UUID memberId = entry.getKey();
            GangRole role = entry.getValue();

            ItemStack memberItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = memberItem.getItemMeta();

            String memberName = getPlayerName(memberId);
            meta.setDisplayName(role.getColor() + "👤 " + memberName);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§7Rôle: " + role.getDisplayName());
            lore.add("§7Statut: " + (Bukkit.getPlayer(memberId) != null ? "§aEn ligne" : "§cHors ligne"));

            if (canManage && role != GangRole.CHEF && !memberId.equals(player.getUniqueId())) {
                lore.add("");
                lore.add("§e⬆ Clic gauche: Promouvoir");
                lore.add("§e⬇ Clic droit: Rétrograder");
                lore.add("§c🗙 Shift+Clic: Expulser");
            }

            meta.setLore(lore);
            memberItem.setItemMeta(meta);
            gui.setItem(slot, memberItem);

            slot++;
            if (slot == 17) slot = 19; // Skip to next row
            if (slot == 26) slot = 28; // Skip to next row
            if (slot == 35) slot = 37; // Skip to next row
        }

        // Inviter un membre (si permissions)
        if (canManage) {
            ItemStack invite = new ItemStack(Material.EMERALD);
            ItemMeta inviteMeta = invite.getItemMeta();
            inviteMeta.setDisplayName("§a➕ §lInviter un Joueur");
            List<String> inviteLore = new ArrayList<>();
            inviteLore.add("");
            inviteLore.add("§7Invitez un nouveau membre");
            inviteLore.add("§7dans votre gang.");
            inviteLore.add("");
            inviteLore.add("§a▶ Cliquez pour fermer et utiliser /gang invite <joueur>");
            inviteMeta.setLore(inviteLore);
            invite.setItemMeta(inviteMeta);
            gui.setItem(49, invite);
        }

        // Retour et fermer
        gui.setItem(45, createBackButton());
        gui.setItem(53, createCloseButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Ouvre le menu des talents du gang
     */
    private void openTalentsMenu(Player player, Gang gang) {
        Inventory gui = Bukkit.createInventory(null, 54, "§5🎯 §l" + gang.getName() + " - Talents");
        openGuis.put(player.getUniqueId(), "talents_menu");

        fillWithGlass(gui, DyeColor.PURPLE);

        GangRole playerRole = gang.getMemberRole(player.getUniqueId());
        boolean canBuy = (playerRole == GangRole.CHEF || playerRole == GangRole.OFFICIER);

        // Afficher les talents par catégorie
        int slot = 10;

        // Talents SellBoost
        for (int i = 1; i <= 10; i++) {
            String talentId = "sell_boost_" + i;
            GangTalent talent = plugin.getGangManager().getTalent(talentId);
            if (talent != null) {
                ItemStack item = createTalentItem(talent, gang, canBuy);
                gui.setItem(slot++, item);
                if (slot == 17) slot = 19;
            }
        }

        // Talents Gang Collectif
        slot = 28;
        for (int i = 1; i <= 5; i++) {
            String talentId = "gang_collectif_" + i;
            GangTalent talent = plugin.getGangManager().getTalent(talentId);
            if (talent != null) {
                ItemStack item = createTalentItem(talent, gang, canBuy);
                gui.setItem(slot++, item);
            }
        }

        // Talents Beacon Multiplier
        slot = 37;
        for (int i = 1; i <= 5; i++) {
            String talentId = "beacon_multiplier_" + i;
            GangTalent talent = plugin.getGangManager().getTalent(talentId);
            if (talent != null) {
                ItemStack item = createTalentItem(talent, gang, canBuy);
                gui.setItem(slot++, item);
            }
        }

        // Informations
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName("§e📚 §lInformations");
        List<String> infoLore = new ArrayList<>();
        infoLore.add("");
        infoLore.add("§7Banque du gang: §6" + NumberFormatter.format(gang.getBankBalance()) + " coins");
        infoLore.add("");
        infoLore.add("§7Les talents améliorent tous les");
        infoLore.add("§7membres du gang de façon permanente.");
        infoMeta.setLore(infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(4, info);

        // Retour et fermer
        gui.setItem(45, createBackButton());
        gui.setItem(53, createCloseButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.0f);
    }

    /**
     * Crée un item représentant un talent
     */
    private ItemStack createTalentItem(GangTalent talent, Gang gang, boolean canBuy) {
        ItemStack item = new ItemStack(talent.getIconMaterial());
        ItemMeta meta = item.getItemMeta();

        boolean owned = gang.getTalents().containsKey(talent.getId());
        boolean canAfford = gang.getBankBalance() >= talent.getCost();
        boolean levelSufficient = gang.getLevel() >= talent.getRequiredGangLevel();

        if (owned) {
            meta.setDisplayName("§a✅ §l" + talent.getName());
        } else if (canAfford && levelSufficient && canBuy) {
            meta.setDisplayName("§e⭐ §l" + talent.getName());
        } else {
            meta.setDisplayName("§7❌ §l" + talent.getName());
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7" + talent.getDescription());
        lore.add("");
        lore.add("§7Coût: §6" + NumberFormatter.format(talent.getCost()) + " coins");
        lore.add("§7Niveau requis: §e" + talent.getRequiredGangLevel());
        lore.add("");

        if (owned) {
            lore.add("§a✅ Talent acheté!");
        } else if (!levelSufficient) {
            lore.add("§c❌ Niveau de gang insuffisant");
        } else if (!canAfford) {
            lore.add("§c❌ Fonds insuffisants");
        } else if (!canBuy) {
            lore.add("§c❌ Permissions insuffisantes");
        } else {
            lore.add("§a▶ Cliquez pour acheter!");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Ouvre le menu des paramètres du gang
     */
    private void openSettingsMenu(Player player, Gang gang) {
        Inventory gui = Bukkit.createInventory(null, 45, "§6⚙️ §l" + gang.getName() + " - Paramètres");
        openGuis.put(player.getUniqueId(), "settings_menu");

        fillWithGlass(gui, DyeColor.ORANGE);

        GangRole playerRole = gang.getMemberRole(player.getUniqueId());
        boolean isLeader = (playerRole == GangRole.CHEF);

        // Modifier description
        ItemStack description = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta descMeta = description.getItemMeta();
        descMeta.setDisplayName("§e📝 §lModifier la Description");
        List<String> descLore = new ArrayList<>();
        descLore.add("");
        descLore.add("§7Description actuelle:");
        if (gang.getDescription() != null && !gang.getDescription().isEmpty()) {
            descLore.add("§f" + gang.getDescription());
        } else {
            descLore.add("§7Aucune description");
        }
        descLore.add("");

        if (isLeader) {
            descLore.add("§a▶ Cliquez pour fermer et utiliser /gang description <texte>");
        } else {
            descLore.add("§c❌ Réservé au chef");
        }

        descMeta.setLore(descLore);
        description.setItemMeta(descMeta);
        gui.setItem(10, description);

        // Renommer le gang
        if (isLeader) {
            ItemStack rename = new ItemStack(Material.NAME_TAG);
            ItemMeta renameMeta = rename.getItemMeta();
            renameMeta.setDisplayName("§e✏️ §lRenommer le Gang");
            List<String> renameLore = new ArrayList<>();
            renameLore.add("");
            renameLore.add("§7Nom actuel: §e" + gang.getName());
            renameLore.add("");
            renameLore.add("§7Coût: §65,000 beacons");
            renameLore.add("");
            renameLore.add("§a▶ Cliquez pour fermer et utiliser /gang rename <nom>");
            renameMeta.setLore(renameLore);
            rename.setItemMeta(renameMeta);
            gui.setItem(12, rename);
        }

        // Créateur de bannière (niveau 10+)
        if (gang.getLevel() >= 10 && isLeader) {
            ItemStack bannerCreator = new ItemStack(Material.WHITE_BANNER);
            ItemMeta bannerMeta = bannerCreator.getItemMeta();
            bannerMeta.setDisplayName("§6🏳️ §lCréateur de Bannière");
            List<String> bannerLore = new ArrayList<>();
            bannerLore.add("");
            bannerLore.add("§7Créez une bannière personnalisée");
            bannerLore.add("§7pour représenter votre gang.");
            bannerLore.add("");
            bannerLore.add("§a▶ Cliquez pour ouvrir!");
            bannerMeta.setLore(bannerLore);
            bannerCreator.setItemMeta(bannerMeta);
            gui.setItem(14, bannerCreator);
        }

        // Dissoudre le gang
        if (isLeader) {
            ItemStack dissolve = new ItemStack(Material.TNT);
            ItemMeta dissolveMeta = dissolve.getItemMeta();
            dissolveMeta.setDisplayName("§c💥 §lDissoudre le Gang");
            List<String> dissolveLore = new ArrayList<>();
            dissolveLore.add("");
            dissolveLore.add("§c⚠️ ATTENTION: Cette action est");
            dissolveLore.add("§cirréversible et supprimera");
            dissolveLore.add("§cdéfinitivement le gang!");
            dissolveLore.add("");
            dissolveLore.add("§c▶ Cliquez pour fermer et utiliser /gang disband");
            dissolveMeta.setLore(dissolveLore);
            dissolve.setItemMeta(dissolveMeta);
            gui.setItem(32, dissolve);
        }

        // Transférer le leadership
        if (isLeader) {
            ItemStack transfer = new ItemStack(Material.GOLDEN_HELMET);
            ItemMeta transferMeta = transfer.getItemMeta();
            transferMeta.setDisplayName("§6👑 §lTransférer le Leadership");
            List<String> transferLore = new ArrayList<>();
            transferLore.add("");
            transferLore.add("§7Transférez le leadership du gang");
            transferLore.add("§7à un autre membre.");
            transferLore.add("");
            transferLore.add("§a▶ Cliquez pour fermer et utiliser /gang transfer <joueur>");
            transferMeta.setLore(transferLore);
            transfer.setItemMeta(transferMeta);
            gui.setItem(16, transfer);
        }

        // Retour et fermer
        gui.setItem(36, createBackButton());
        gui.setItem(44, createCloseButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.0f);
    }

    /**
     * Gère les clics dans le menu d'informations du gang
     */
    private void handleGangInfoClick(Player player, int slot, ItemStack item, ClickType clickType) {
        switch (slot) {
            case 45 -> openMainMenu(player); // Retour
            case 49 -> player.closeInventory(); // Fermer
            // Autres slots peuvent être ajoutés pour des fonctionnalités spécifiques
        }
    }

    /**
     * Gère les clics dans le menu d'amélioration
     */
    private void handleUpgradeMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) return;

        switch (slot) {
            case 13 -> { // Améliorer
                if (gang.getLevel() < 10) {
                    GangRole playerRole = gang.getMemberRole(player.getUniqueId());
                    if (playerRole == GangRole.CHEF) {
                        if (plugin.getGangManager().upgradeGang(gang)) {
                            player.sendMessage("§a✅ Gang amélioré au niveau " + gang.getLevel() + "!");
                            openUpgradeMenu(player, gang); // Rafraîchir
                        } else {
                            player.sendMessage("§c❌ Amélioration impossible (fonds insuffisants?)");
                        }
                    } else {
                        player.sendMessage("§c❌ Seul le chef peut améliorer le gang!");
                    }
                }
            }
            case 27 -> openMainMenu(player); // Retour
            case 31 -> player.closeInventory(); // Fermer
        }
    }

    /**
     * Gère les clics dans la boutique du gang
     */
    private void handleShopClick(Player player, int slot, ItemStack item, ClickType clickType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) return;

        // Vérifier si c'est un boost
        if (slot >= 10 && slot <= 43) {
            // Calculer quel boost c'est
            int boostIndex = getBoostIndexFromSlot(slot);
            if (boostIndex >= 0) {
                GangBoostType[] boostTypes = GangBoostType.values();
                int typeIndex = boostIndex / 3;
                int tier = (boostIndex % 3) + 1;

                if (typeIndex < boostTypes.length) {
                    GangBoostType boostType = boostTypes[typeIndex];
                    if (plugin.getGangManager().activateGangBoost(gang, player, boostType, tier)) {
                        player.sendMessage("§a✅ Boost " + boostType.getDisplayName() + " activé!");
                        openShop(player, gang); // Rafraîchir
                    }
                }
            }
        }

        // Bannière du gang
        if (slot == 49 && gang.getLevel() >= 10) {
            if (playerData.getBeacons() >= 1000) {
                playerData.removeBeacon(1000);

                // Donner la bannière au joueur
                ItemStack banner = createGangBanner(gang);
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(banner);
                    player.sendMessage("§a✅ Bannière du gang achetée!");
                } else {
                    player.sendMessage("§c❌ Inventaire plein!");
                    playerData.addBeacons(1000); // Rembourser
                }
            } else {
                player.sendMessage("§c❌ Vous n'avez pas assez de beacons!");
            }
        }

        // Boutons de navigation
        switch (slot) {
            case 45 -> openMainMenu(player); // Retour
            case 53 -> player.closeInventory(); // Fermer
        }
    }

    /**
     * Calcule l'index du boost basé sur le slot
     */
    private int getBoostIndexFromSlot(int slot) {
        // Mapping des slots vers les index de boost
        int[] boostSlots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

        for (int i = 0; i < boostSlots.length; i++) {
            if (boostSlots[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Gère les clics dans le créateur de bannière
     */
    private void handleBannerCreatorClick(Player player, int slot, ItemStack item, ClickType clickType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) return;

        switch (slot) {
            case 20 -> { // Confirmer
                // Récupérer la bannière du slot 13
                ItemStack banner = player.getOpenInventory().getItem(13);
                if (banner != null && banner.getType().name().contains("BANNER")) {
                    BannerMeta bannerMeta = (BannerMeta) banner.getItemMeta();
                    if (bannerMeta != null) {
                        gang.setBannerPatterns(bannerMeta.getPatterns());
                        plugin.getGangManager().saveGang(gang);

                        player.closeInventory();
                        player.sendMessage("§a✅ Bannière du gang enregistrée!");
                        gang.broadcast("§6🏳️ " + player.getName() + " a mis à jour la bannière du gang!", player);
                    }
                } else {
                    player.sendMessage("§c❌ Aucune bannière valide trouvée!");
                }
            }
            case 24 -> { // Annuler
                player.closeInventory();
                player.sendMessage("§c❌ Création de bannière annulée.");
            }
        }
    }

    /**
     * Gère les clics dans la liste des gangs
     */
    private void handleGangListClick(Player player, int slot, ItemStack item, ClickType clickType, int page) {
        if (item != null && item.getType() == Material.WHITE_BANNER) {
            // Récupérer le nom du gang depuis l'item
            if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                String displayName = item.getItemMeta().getDisplayName();
                String gangName = displayName.split(" ")[1]; // Extraire le nom

                Gang gang = plugin.getGangManager().getGangByName(gangName);
                if (gang != null) {
                    openGangInfo(player, gang);
                }
            }
        }

        // Navigation
        switch (slot) {
            case 45 -> { // Page précédente
                if (page > 0) {
                    openGangList(player, page - 1);
                }
            }
            case 53 -> { // Page suivante
                openGangList(player, page + 1);
            }
            case 49 -> openMainMenu(player); // Retour au menu principal
        }
    }

    /**
     * Implémentation complète de la liste des gangs avec pagination
     */
    private void openGangList(Player player, int page) {
        List<Gang> allGangs = plugin.getGangManager().getAllGangs();
        allGangs.sort((g1, g2) -> Integer.compare(g2.getLevel(), g1.getLevel())); // Trier par niveau décroissant

        int gangsPerPage = 28; // 4 rows of 7 items
        int totalPages = (int) Math.ceil((double) allGangs.size() / gangsPerPage);

        if (page >= totalPages) page = Math.max(0, totalPages - 1);
        if (page < 0) page = 0;

        Inventory gui = Bukkit.createInventory(null, 54, "§6📋 §lListe des Gangs §7(Page " + (page + 1) + "/" + Math.max(1, totalPages) + ")");
        openGuis.put(player.getUniqueId(), "gang_list:" + page);

        fillWithGlass(gui, DyeColor.ORANGE);

        // Afficher les gangs de cette page
        int startIndex = page * gangsPerPage;
        int endIndex = Math.min(startIndex + gangsPerPage, allGangs.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            Gang gang = allGangs.get(i);
            ItemStack gangItem = createGangListItem(gang, player);
            gui.setItem(slot, gangItem);

            slot++;
            if (slot == 17) slot = 19; // Skip to next row
            if (slot == 26) slot = 28; // Skip to next row
            if (slot == 35) slot = 37; // Skip to next row
            if (slot >= 44) break; // Safety check
        }

        // Navigation
        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.setDisplayName("§a⬅ §lPage Précédente");
            prevPage.setItemMeta(prevMeta);
            gui.setItem(45, prevPage);
        }

        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.setDisplayName("§a➡ §lPage Suivante");
            nextPage.setItemMeta(nextMeta);
            gui.setItem(53, nextPage);
        }

        // Retour au menu principal
        gui.setItem(49, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.0f);
    }

    /**
     * Gère les clics dans le menu des talents
     */
    private void handleTalentsMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) return;

        GangRole playerRole = gang.getMemberRole(player.getUniqueId());
        boolean canBuy = (playerRole == GangRole.CHEF || playerRole == GangRole.OFFICIER);

        if (!canBuy) {
            player.sendMessage("§c❌ Seuls le chef et les officiers peuvent acheter des talents!");
            return;
        }

        // Identifier le talent cliqué basé sur le slot
        String talentId = getTalentIdFromSlot(slot);
        if (talentId != null) {
            GangTalent talent = plugin.getGangManager().getTalent(talentId);
            if (talent != null && !gang.getTalents().containsKey(talentId)) {
                if (plugin.getGangManager().buyTalent(gang, player, talentId)) {
                    player.sendMessage("§a✅ Talent " + talent.getName() + " acheté!");
                    openTalentsMenu(player, gang); // Rafraîchir
                }
            }
        }

        // Boutons de navigation
        switch (slot) {
            case 45 -> openMainMenu(player); // Retour
            case 53 -> player.closeInventory(); // Fermer
        }
    }

    /**
     * Identifie le talent basé sur le slot cliqué
     */
    private String getTalentIdFromSlot(int slot) {
        // SellBoost talents (slots 10-16, 19-25)
        if ((slot >= 10 && slot <= 16) || (slot >= 19 && slot <= 25)) {
            int index;
            if (slot <= 16) {
                index = slot - 10;
            } else {
                index = slot - 19 + 7;
            }
            if (index < 10) {
                return "sell_boost_" + (index + 1);
            }
        }

        // Gang Collectif talents (slots 28-32)
        if (slot >= 28 && slot <= 32) {
            int index = slot - 28;
            if (index < 5) {
                return "gang_collectif_" + (index + 1);
            }
        }

        // Beacon Multiplier talents (slots 37-41)
        if (slot >= 37 && slot <= 41) {
            int index = slot - 37;
            if (index < 5) {
                return "beacon_multiplier_" + (index + 1);
            }
        }

        return null;
    }

    /**
     * Gère les clics dans le menu des membres
     */
    private void handleMembersMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) return;

        GangRole playerRole = gang.getMemberRole(player.getUniqueId());
        boolean canManage = (playerRole == GangRole.CHEF || playerRole == GangRole.OFFICIER);

        if (slot == 49 && canManage) { // Inviter un joueur
            player.closeInventory();
            player.sendMessage("§e💡 Utilisez §a/gang invite <joueur> §epour inviter un nouveau membre!");
            return;
        }

        // Gestion des membres
        if (item != null && item.getType() == Material.PLAYER_HEAD && canManage) {
            String memberName = extractPlayerNameFromItem(item);
            if (memberName != null) {
                UUID memberId = getPlayerUUIDByName(memberName);
                if (memberId != null && !memberId.equals(player.getUniqueId())) {
                    GangRole memberRole = gang.getMemberRole(memberId);

                    if (clickType == ClickType.LEFT) {
                        // Promouvoir
                        if (memberRole == GangRole.MEMBRE && playerRole == GangRole.CHEF) {
                            gang.setMemberRole(memberId, GangRole.OFFICIER);
                            plugin.getGangManager().saveGang(gang);
                            player.sendMessage("§a✅ " + memberName + " promu officier!");
                            openMembersMenu(player, gang); // Rafraîchir
                        }
                    } else if (clickType == ClickType.RIGHT) {
                        // Rétrograder
                        if (memberRole == GangRole.OFFICIER && playerRole == GangRole.CHEF) {
                            gang.setMemberRole(memberId, GangRole.MEMBRE);
                            plugin.getGangManager().saveGang(gang);
                            player.sendMessage("§a✅ " + memberName + " rétrogradé membre!");
                            openMembersMenu(player, gang); // Rafraîchir
                        }
                    } else if (clickType == ClickType.SHIFT_LEFT) {
                        // Expulser
                        if (memberRole != GangRole.CHEF) {
                            if (plugin.getGangManager().removePlayer(gang, player.getUniqueId())) {
                                player.sendMessage("§a✅ " + memberName + " expulsé du gang!");
                                openMembersMenu(player, gang); // Rafraîchir
                            }
                        }
                    }
                }
            }
        }

        // Boutons de navigation
        switch (slot) {
            case 45 -> openMainMenu(player); // Retour
            case 53 -> player.closeInventory(); // Fermer
        }
    }

    /**
     * Extrait le nom du joueur depuis l'ItemStack
     */
    private String extractPlayerNameFromItem(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = item.getItemMeta().getDisplayName();
            // Extraire le nom après l'emoji et l'espace
            String[] parts = displayName.split(" ");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return null;
    }

    /**
     * Gère les clics dans le menu des paramètres
     */
    private void handleSettingsMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Gang gang = plugin.getGangManager().getGang(playerData.getGangId());
        if (gang == null) return;

        GangRole playerRole = gang.getMemberRole(player.getUniqueId());
        boolean isLeader = (playerRole == GangRole.CHEF);

        switch (slot) {
            case 10 -> { // Modifier description
                if (isLeader) {
                    player.closeInventory();
                    player.sendMessage("§e💡 Utilisez §a/gang description <texte> §epour modifier la description!");
                } else {
                    player.sendMessage("§c❌ Seul le chef peut modifier la description!");
                }
            }
            case 12 -> { // Renommer
                if (isLeader) {
                    player.closeInventory();
                    player.sendMessage("§e💡 Utilisez §a/gang rename <nom> §epour renommer le gang!");
                    player.sendMessage("§7Coût: §65,000 beacons");
                } else {
                    player.sendMessage("§c❌ Seul le chef peut renommer le gang!");
                }
            }
            case 14 -> { // Créateur de bannière
                if (isLeader && gang.getLevel() >= 10) {
                    openBannerCreator(player, gang);
                } else if (!isLeader) {
                    player.sendMessage("§c❌ Seul le chef peut modifier la bannière!");
                } else {
                    player.sendMessage("§c❌ Niveau 10 requis pour cette fonctionnalité!");
                }
            }
            case 16 -> { // Transférer leadership
                if (isLeader) {
                    player.closeInventory();
                    player.sendMessage("§e💡 Utilisez §a/gang transfer <joueur> §epour transférer le leadership!");
                } else {
                    player.sendMessage("§c❌ Seul le chef peut transférer le leadership!");
                }
            }
            case 32 -> { // Dissoudre
                if (isLeader) {
                    player.closeInventory();
                    player.sendMessage("§c⚠️ Pour dissoudre le gang, utilisez §e/gang disband");
                    player.sendMessage("§c⚠️ Cette action est irréversible!");
                } else {
                    player.sendMessage("§c❌ Seul le chef peut dissoudre le gang!");
                }
            }
            case 36 -> openMainMenu(player); // Retour
            case 44 -> player.closeInventory(); // Fermer
        }
    }

    /**
     * Méthode utilitaire pour obtenir l'UUID d'un joueur par son nom
     */
    private UUID getPlayerUUIDByName(String name) {
        Player player = Bukkit.getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }
        return null;
    }

    /**
     * Mise à jour de la méthode principale handleGangMenuClick pour inclure tous les handlers
     */
    public void handleGangMenuClick(Player player, int slot, ItemStack item, ClickType clickType) {
        String guiType = openGuis.get(player.getUniqueId());
        if (guiType == null) return;

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        switch (guiType) {
            case "no_gang_menu" -> handleNoGangMenuClick(player, slot, item, clickType);
            case "gang_menu" -> handleMainGangMenuClick(player, slot, item, clickType);
            case "gang_info" -> handleGangInfoClick(player, slot, item, clickType);
            case "upgrade_menu" -> handleUpgradeMenuClick(player, slot, item, clickType);
            case "gang_shop" -> handleShopClick(player, slot, item, clickType);
            case "banner_creator" -> handleBannerCreatorClick(player, slot, item, clickType);
            case "talents_menu" -> handleTalentsMenuClick(player, slot, item, clickType);
            case "members_menu" -> handleMembersMenuClick(player, slot, item, clickType);
            case "settings_menu" -> handleSettingsMenuClick(player, slot, item, clickType);
            default -> {
                if (guiType.startsWith("gang_list:")) {
                    int page = Integer.parseInt(guiType.split(":")[1]);
                    handleGangListClick(player, slot, item, clickType, page);
                }
            }
        }
    }
}