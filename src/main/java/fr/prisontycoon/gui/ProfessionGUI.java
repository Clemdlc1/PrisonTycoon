package fr.prisontycoon.gui;

import fr.prisontycoon.PrisonTycoon;
import fr.prisontycoon.data.PlayerData;
import fr.prisontycoon.managers.ProfessionManager;
import fr.prisontycoon.utils.NumberFormatter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Interface graphique pour le système de métiers (AMÉLIORÉE)
 * - Talents et kit sur la même page
 * - Organisation en colonnes
 * - Sauvegarde du niveau kit
 * - Clics fonctionnels
 */
public class ProfessionGUI {

    // Slots du menu principal (27 slots)
    private static final int ACTIVE_PROFESSION_SLOT = 13; // Centre
    private static final int TALENTS_BUTTON_SLOT = 11; // Bouton talents/kits
    private static final int CHANGE_PROFESSION_SLOT = 14; // Changer métier
    private static final int REWARDS_SLOT = 15; // Récompenses
    private static final int HELP_SLOT = 18; // Aide
    private static final int CLOSE_SLOT = 26; // Fermer
    // Slots du menu talents/kits (54 slots) - DÉCALÉ D'UNE COLONNE À GAUCHE
    private static final int TALENT_1_COL = 2; // 2ème colonne (base)
    private static final int TALENT_2_COL = 21; // 3ème colonne (base)
    private static final int TALENT_3_COL = 22; // 4ème colonne (base)
    private static final int KIT_COL = 6; // 6ème colonne (base)
    private static final int INFO_SLOT = 49; // Centre pour infos
    // Navigation
    private static final int PREV_PAGE_SLOT = 48;
    private static final int NEXT_PAGE_SLOT = 50;
    private static final int BACK_SLOT = 45;
    private final PrisonTycoon plugin;
    private final NamespacedKey actionKey;
    private final NamespacedKey professionKey;
    private final NamespacedKey talentKey;
    private final NamespacedKey targetLevelKey;

    public ProfessionGUI(PrisonTycoon plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "profession_action");
        this.professionKey = new NamespacedKey(plugin, "profession_id");
        this.talentKey = new NamespacedKey(plugin, "talent_id");
        this.targetLevelKey = new NamespacedKey(plugin, "target_level");
    }

    /**
     * Ouvre le menu principal des métiers
     */
    public void openProfessionMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(27, "§e⚒ §lMétiers §e⚒");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.PROFESSION_MAIN, gui);

        fillWithGlass(gui);
        setupProfessionMenu(gui, player);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.2f);
    }

    /**
     * Configure le menu principal des métiers
     */
    private void setupProfessionMenu(Inventory gui, Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String activeProfession = playerData.getActiveProfession();

        // Métier actif au centre
        if (activeProfession != null) {
            gui.setItem(ACTIVE_PROFESSION_SLOT, createActiveProfessionDisplayItem(player, activeProfession));

            // Boutons de gestion autour du métier
            gui.setItem(TALENTS_BUTTON_SLOT, createTalentsKitsButton(activeProfession));
            gui.setItem(CHANGE_PROFESSION_SLOT, createChangeProfessionButton());
            gui.setItem(REWARDS_SLOT, createRewardsButton(player, activeProfession));
        } else {
            gui.setItem(ACTIVE_PROFESSION_SLOT, createChooseProfessionItem());
        }

        // Boutons informatifs et navigation
        gui.setItem(HELP_SLOT, createHelpItem());
        gui.setItem(CLOSE_SLOT, createCloseItem());
    }

    /**
     * NOUVEAU: Ouvre le menu talents & kits unifié avec pagination
     */
    public void openTalentsKitsMenu(Player player, String professionId, int page) {
        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(professionId);
        if (profession == null) return;

        String pageInfo = page == 0 ? " (Niv. 1-5)" : " (Niv. 6-10)";
        Inventory gui = plugin.getGUIManager().createInventory(54, "§5⭐ " + profession.displayName() + pageInfo);

        // CORRIGÉ : Enregistre le GUI avec le numéro de page actuel
        plugin.getGUIManager().registerOpenGUI(player, GUIType.PROFESSION_TALENTS, gui, Map.of("page", String.valueOf(page)));

        fillWithGlass(gui);
        setupTalentsKitsMenu(gui, player, profession, page);

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    /**
     * NOUVEAU: Configure le menu talents & kits avec pagination par niveaux
     */
    private void setupTalentsKitsMenu(Inventory gui, Player player, ProfessionManager.Profession profession, int page) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        List<ProfessionManager.ProfessionTalent> talents = profession.talents();

        // Info au centre
        gui.setItem(INFO_SLOT, createTalentsKitsInfoItem(player, profession.id(), page));

        // Calcul des niveaux pour cette page
        int startLevel = (page * 5) + 1; // Page 0: 1-5, Page 1: 6-10
        int endLevel = Math.min(startLevel + 4, 10); // Maximum niveau 10

        // Pour chaque ligne (niveau)
        for (int level = startLevel; level <= endLevel; level++) {
            int row = level - startLevel; // 0-4 pour les 5 lignes
            int baseSlot = 9 + (row * 9); // Ligne 2 = slot 18, ligne 3 = slot 27, etc.

            // Talents en colonnes (max 3 talents)
            for (int i = 0; i < Math.min(3, talents.size()); i++) {
                ProfessionManager.ProfessionTalent talent = talents.get(i);
                int slot = baseSlot + (TALENT_1_COL - 9) + i; // Décalé selon la colonne
                gui.setItem(slot, createLeveledTalentItem(player, profession.id(), talent, level));
            }

            // Kit en 6ème colonne
            int kitSlot = baseSlot + (KIT_COL - 9);
            gui.setItem(kitSlot, createLeveledKitItem(player, profession.id(), level));
        }

        // Navigation
        gui.setItem(BACK_SLOT, createBackButton());

        // Pagination
        if (page > 0) {
            gui.setItem(PREV_PAGE_SLOT, createPageButton("prev", profession.id()));
        }
        if (page < 1) { // Max 2 pages (0 et 1)
            gui.setItem(NEXT_PAGE_SLOT, createPageButton("next", profession.id()));
        }
    }

    /**
     * Crée un item talent pour un niveau spécifique - CORRIGÉ
     */
    private ItemStack createLeveledTalentItem(Player player, String professionId, ProfessionManager.ProfessionTalent talent, int targetLevel) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int professionLevel = playerData.getProfessionLevel(professionId);
        int currentTalentLevel = playerData.getTalentLevel(professionId, talent.id());

        Material material = getTalentMaterial(talent.id());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Détermine l'état du niveau
        boolean isActive = currentTalentLevel >= targetLevel;
        boolean hasPrerequisite = targetLevel == 1 || currentTalentLevel >= targetLevel - 1; // NOUVEAU
        boolean canUpgrade = professionLevel >= targetLevel && currentTalentLevel < targetLevel && hasPrerequisite; // MODIFIÉ
        boolean isMaxed = currentTalentLevel >= 10;

        // Nom avec couleur selon l'état
        String color = isActive ? "§a" : (canUpgrade ? "§e" : "§c");
        String status = isActive ? "✓" : (canUpgrade ? "⭘" : "✗");
        plugin.getGUIManager().applyName(meta,color + status + " §f" + talent.displayName() + " §7Niv." + targetLevel);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + talent.description());
        lore.add("");

        // Effet à ce niveau
        int value = talent.getValueAtLevel(targetLevel);
        String suffix = talent.id().contains("multiplier") ? "x" : "%";
        lore.add("§7Effet niveau " + targetLevel + ": §e+" + value + suffix);
        lore.add("");

        if (isActive) {
            lore.add("§a✅ Niveau déjà activé");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else if (canUpgrade) {
            long cost = calculateTalentCost(targetLevel);
            lore.add("§7Coût: §e" + NumberFormatter.format(cost) + " XP");
            lore.add("§e▶ Cliquez pour activer !");
        } else if (professionLevel < targetLevel) {
            lore.add("§cNiveau métier requis: " + targetLevel);
        } else if (!hasPrerequisite) { // NOUVEAU
            lore.add("§cNiveau " + (targetLevel - 1) + " requis d'abord");
        } else if (isMaxed) {
            lore.add("§cTalent déjà au maximum");
        }

        plugin.getGUIManager().applyLore(meta, lore);

        if (canUpgrade && !isActive) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "upgrade_talent_level");
            meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
            meta.getPersistentDataContainer().set(talentKey, PersistentDataType.STRING, talent.id());
            meta.getPersistentDataContainer().set(targetLevelKey, PersistentDataType.INTEGER, targetLevel);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un item kit pour un niveau spécifique - CORRIGÉ
     */
    private ItemStack createLeveledKitItem(Player player, String professionId, int targetLevel) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int professionLevel = playerData.getProfessionLevel(professionId);
        int currentKitLevel = playerData.getKitLevel(professionId);

        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        // Détermine l'état du niveau
        boolean isActive = currentKitLevel >= targetLevel;
        boolean hasPrerequisite = targetLevel == 1 || currentKitLevel >= targetLevel - 1; // NOUVEAU
        boolean canUpgrade = professionLevel >= targetLevel && currentKitLevel < targetLevel && hasPrerequisite; // MODIFIÉ
        boolean isMaxed = currentKitLevel >= 10;

        // Nom avec couleur selon l'état
        String color = isActive ? "§a" : (canUpgrade ? "§6" : "§c");
        String status = isActive ? "✓" : (canUpgrade ? "⭘" : "✗");
        plugin.getGUIManager().applyName(meta,color + status + " §f📦 Kit Métier §7Niv." + targetLevel);

        List<String> lore = new ArrayList<>();
        lore.add("§7Équipement et ressources améliorées");
        lore.add("");
        lore.add("§7Kit niveau " + targetLevel + ": §6Meilleur équipement");
        lore.add("");

        if (isActive) {
            lore.add("§a✅ Niveau déjà activé");
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        } else if (canUpgrade) {
            long cost = calculateKitCost(targetLevel);
            lore.add("§7Coût: §e" + NumberFormatter.format(cost) + " XP");
            lore.add("§6▶ Cliquez pour activer !");
        } else if (professionLevel < targetLevel) {
            lore.add("§cNiveau métier requis: " + targetLevel);
        } else { // NOUVEAU
            lore.add("§cKit niveau " + (targetLevel - 1) + " requis d'abord");
        }

        plugin.getGUIManager().applyLore(meta, lore);

        if (canUpgrade) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "upgrade_kit_level");
            meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
            meta.getPersistentDataContainer().set(targetLevelKey, PersistentDataType.INTEGER, targetLevel);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * NOUVEAU: Crée un bouton de pagination
     */
    private ItemStack createPageButton(String direction, String professionId) {
        Material material = direction.equals("prev") ? Material.ARROW : Material.SPECTRAL_ARROW;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String displayName = direction.equals("prev") ? "§7← §lPage Précédente" : "§7→ §lPage Suivante";
        String pageInfo = direction.equals("prev") ? "§7Niveaux 1-5" : "§7Niveaux 6-10";

        plugin.getGUIManager().applyName(meta,displayName);
        plugin.getGUIManager().applyLore(meta, Arrays.asList(pageInfo, "", "§e▶ Cliquez pour changer de page"));

        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, direction + "_page");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée le bouton d'accès aux talents & kits
     */
    private ItemStack createTalentsKitsButton(String professionId) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,"§5⭐ §lTalents & Kit");

        List<String> lore = new ArrayList<>();
        lore.add("§7Gérez vos talents et kit de métier");
        lore.add("");
        lore.add("§7• Améliorez vos capacités");
        lore.add("§7• Améliorez votre équipement");
        lore.add("§7• Dépensez de l'XP joueur");
        lore.add("");
        lore.add("§e▶ Cliquez pour ouvrir !");

        plugin.getGUIManager().applyLore(meta, lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "view_talents_kits");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * NOUVEAU: Crée l'item d'information sur les talents & kits avec info de page
     */
    private ItemStack createTalentsKitsInfoItem(Player player, String professionId, int page) {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        String pageInfo = page == 0 ? "Niveaux 1-5" : "Niveaux 6-10";
        plugin.getGUIManager().applyName(meta,"§e📖 §lTalents & Kit §7(" + pageInfo + ")");

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("§7Améliorez vos capacités et équipement");
        lore.add("");
        lore.add("§7Page actuelle: §e" + pageInfo);
        lore.add("§7Cliquez sur un niveau pour l'activer");
        lore.add("");
        lore.add("§7Coûts en XP joueur:");
        lore.add("§7• Talents: Coût exponentiel");
        lore.add("§7• Kit: Coût progressif");
        lore.add("");
        lore.add("§7Votre XP: §e" + NumberFormatter.format(playerData.getExperience()));

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * AMÉLIORÉ: Gère les clics dans les menus des métiers
     */
    public void handleProfessionMenuClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        if (clickedItem == null || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "view_talents_kits" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    openTalentsKitsMenu(player, professionId, 0);
                }
            }
            case "upgrade_talent_level" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                String talentId = meta.getPersistentDataContainer().get(talentKey, PersistentDataType.STRING);
                Integer targetLevel = meta.getPersistentDataContainer().get(targetLevelKey, PersistentDataType.INTEGER);

                if (professionId != null && talentId != null && targetLevel != null) {
                    if (plugin.getProfessionManager().activateTalent(player, talentId, targetLevel)) {
                        String pageStr = plugin.getGUIManager().getGUIData(player, "page");
                        int page = pageStr != null ? Integer.parseInt(pageStr) : 0;
                        openTalentsKitsMenu(player, professionId, page);
                    }
                }
            }
            case "upgrade_kit_level" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                Integer targetLevel = meta.getPersistentDataContainer().get(targetLevelKey, PersistentDataType.INTEGER);

                if (professionId != null && targetLevel != null) {
                    if (plugin.getProfessionManager().activateKit(player, targetLevel)) {
                        String pageStr = plugin.getGUIManager().getGUIData(player, "page");
                        int page = pageStr != null ? Integer.parseInt(pageStr) : 0;
                        openTalentsKitsMenu(player, professionId, page);
                    }
                }
            }
            case "prev_page" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    openTalentsKitsMenu(player, professionId, 0); // Page 1-5
                }
            }
            case "next_page" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    openTalentsKitsMenu(player, professionId, 1); // Page 6-10
                }
            }
            case "change_profession" -> openChangeProfessionMenu(player);
            case "choose_profession" -> openChooseProfessionMenu(player);
            case "select_profession" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    if (plugin.getProfessionManager().setActiveProfession(player, professionId)) {
                        openProfessionMenu(player);
                    }
                }
            }
            case "confirm_change" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    if (plugin.getProfessionManager().changeProfession(player, professionId)) {
                        openProfessionMenu(player);
                    }
                }
            }
            case "back_to_main" -> openProfessionMenu(player);
            case "open_rewards" -> {
                String professionId = meta.getPersistentDataContainer().get(professionKey, PersistentDataType.STRING);
                if (professionId != null) {
                    plugin.getProfessionRewardsGUI().openRewardsMenu(player, professionId);
                }
            }
            case "close" -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);
            }
        }
    }

    /**
     * Calcule le coût d'un talent (exponentiel)
     */
    private long calculateTalentCost(int level) {
        return (long) (1000 * Math.pow(2, level - 1));
    }

    /**
     * Calcule le coût d'un kit (progressif)
     */
    private long calculateKitCost(int level) {
        return (long) (2000 * Math.pow(1.8, level - 1));
    }

    // ===== MÉTHODES EXISTANTES CONSERVÉES =====

    private ItemStack createActiveProfessionDisplayItem(Player player, String professionId) {
        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(professionId);
        if (profession == null) return new ItemStack(Material.BARRIER);

        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int level = playerData.getProfessionLevel(professionId);
        int xp = playerData.getProfessionXP(professionId);
        int nextLevelXP = plugin.getProfessionManager().getXPForNextLevel(level);

        Material material = getProfessionMaterial(professionId);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,profession.displayName() + " §7(Actif)");

        List<String> lore = new ArrayList<>();
        lore.add("§7" + profession.description());
        lore.add("");
        lore.add("§7Niveau: §e" + level + "§7/§e10");
        lore.add("§7XP: §e" + NumberFormatter.format(xp) + "§7/§e" + (level < 10 ? NumberFormatter.format(nextLevelXP) : "MAX"));

        if (level < 10) {
            int progress = Math.min(20, (int) ((double) xp / nextLevelXP * 20));
            StringBuilder bar = new StringBuilder("§7[");
            for (int i = 0; i < 20; i++) {
                if (i < progress) {
                    bar.append("§a█");
                } else {
                    bar.append("§7░");
                }
            }
            bar.append("§7]");
            lore.add(bar.toString());
        }

        lore.add("");
        lore.add("§7Votre métier principal");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createChooseProfessionItem() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,"§e🔍 §lChoisir un Métier");

        List<String> lore = new ArrayList<>();
        lore.add("§7Vous n'avez pas encore de métier actif");
        lore.add("");
        lore.add("§e▶ Cliquez pour choisir !");

        plugin.getGUIManager().applyLore(meta, lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "choose_profession");
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createChangeProfessionButton() {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,"§c🔄 §lChanger de Métier");

        List<String> lore = new ArrayList<>();
        lore.add("§7Changez votre métier actif");
        lore.add("");
        lore.add("§c💸 Coût: §e5000 beacons");
        lore.add("§c⏰ Cooldown: §e24 heures");
        lore.add("");
        lore.add("§e▶ Cliquez pour changer !");

        plugin.getGUIManager().applyLore(meta, lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "change_profession");
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Crée le bouton d'accès aux récompenses
     */
    private ItemStack createRewardsButton(Player player, String professionId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int availableRewards = getAvailableRewardsCount(player, professionId);

        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,"§6🎁 §lRécompenses");

        List<String> lore = new ArrayList<>();
        lore.add("§7Réclamez vos récompenses de niveau");
        lore.add("");
        lore.add("§7Récompenses disponibles: §e" + availableRewards);
        lore.add("");
        lore.add("§e▶ Cliquez pour ouvrir !");

        plugin.getGUIManager().applyLore(meta, lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "open_rewards");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);

        // Enchantement si des récompenses sont disponibles
        if (availableRewards > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Compte les récompenses disponibles
     */
    private int getAvailableRewardsCount(Player player, String professionId) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        int professionLevel = playerData.getProfessionLevel(professionId);
        int available = 0;

        for (int level = 1; level <= professionLevel; level++) {
            if (!playerData.hasProfessionRewardClaimed(professionId, level)) {
                available++;
            }
        }

        return available;
    }

    private ItemStack createHelpItem() {
        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,"§e❓ §lAide");

        List<String> lore = new ArrayList<>();
        lore.add("§7Commandes utiles:");
        lore.add("§e/metier info §7- Infos sur votre métier");
        lore.add("§e/metier changemetier <métier> §7- Changer de métier");
        lore.add("§e/metier metierxp <nombre> §7- Admin: donner XP");
        lore.add("");
        lore.add("§7Débloquage: §eRang F §7requis");

        plugin.getGUIManager().applyLore(meta, lore);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createCloseItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,"§c✗ §lFermer");
        plugin.getGUIManager().applyLore(meta, List.of("§7Ferme ce menu"));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "close");
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,"§7← §lRetour");
        plugin.getGUIManager().applyLore(meta, List.of("§7Retour au menu métiers"));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "back_to_main");
        item.setItemMeta(meta);

        return item;
    }

    public void openChooseProfessionMenu(Player player) {
        Inventory gui = plugin.getGUIManager().createInventory(27, "§e⚒ §lChoisir un Métier §e⚒");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.PROFESSION_MAIN, gui);

        fillWithGlass(gui);

        // Les 3 métiers
        gui.setItem(11, createProfessionChoiceItem("mineur"));
        gui.setItem(13, createProfessionChoiceItem("commercant"));
        gui.setItem(15, createProfessionChoiceItem("guerrier"));

        // Bouton retour
        gui.setItem(22, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    public void openChangeProfessionMenu(Player player) {
        PlayerData playerData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String currentProfession = playerData.getActiveProfession();

        Inventory gui = plugin.getGUIManager().createInventory(27, "§c🔄 §lChanger de Métier §c🔄");
        plugin.getGUIManager().registerOpenGUI(player, GUIType.PROFESSION_MAIN, gui);

        fillWithGlass(gui);

        // Info au centre
        ItemStack info = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta infoMeta = info.getItemMeta();
        plugin.getGUIManager().applyName(infoMeta,"§e💡 §lInformations");

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Métier actuel: §e" + (currentProfession != null ?
                plugin.getProfessionManager().getProfession(currentProfession).displayName() : "Aucun"));
        infoLore.add("");
        infoLore.add("§c💸 Coût: §e5000 beacons");
        infoLore.add("§c⏰ Cooldown: §e24 heures");
        infoLore.add("");
        infoLore.add("§e💡 Votre progression est conservée !");

        plugin.getGUIManager().applyLore(infoMeta, infoLore);
        info.setItemMeta(infoMeta);
        gui.setItem(11, info);

        // Les 3 métiers avec confirmation
        gui.setItem(13, createProfessionChangeItem("mineur", currentProfession));
        gui.setItem(14, createProfessionChangeItem("commercant", currentProfession));
        gui.setItem(15, createProfessionChangeItem("guerrier", currentProfession));

        gui.setItem(22, createBackButton());

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    private ItemStack createProfessionChoiceItem(String professionId) {
        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(professionId);
        if (profession == null) return new ItemStack(Material.BARRIER);

        Material material = getProfessionMaterial(professionId);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        plugin.getGUIManager().applyName(meta,profession.displayName());

        List<String> lore = new ArrayList<>();
        lore.add("§7" + profession.description());
        lore.add("");
        lore.add("§e▶ Cliquez pour choisir !");
        lore.add("§7(Premier choix gratuit)");

        plugin.getGUIManager().applyLore(meta, lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "select_profession");
        meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
        item.setItemMeta(meta);

        return item;
    }

    private ItemStack createProfessionChangeItem(String professionId, String currentProfession) {
        ProfessionManager.Profession profession = plugin.getProfessionManager().getProfession(professionId);
        if (profession == null) return new ItemStack(Material.BARRIER);

        Material material = getProfessionMaterial(professionId);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        boolean isCurrent = professionId.equals(currentProfession);

        plugin.getGUIManager().applyName(meta,(isCurrent ? "§e" : "§a") + profession.displayName() +
                (isCurrent ? " §7(Actuel)" : ""));

        List<String> lore = new ArrayList<>();
        lore.add("§7" + profession.description());
        lore.add("");

        if (isCurrent) {
            lore.add("§7C'est votre métier actuel");
        } else {
            lore.add("§e▶ Cliquez pour changer !");
            lore.add("§c💸 Coût: 5000 beacons");
        }

        plugin.getGUIManager().applyLore(meta, lore);

        if (!isCurrent) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "confirm_change");
            meta.getPersistentDataContainer().set(professionKey, PersistentDataType.STRING, professionId);
        }

        item.setItemMeta(meta);

        return item;
    }

    private void fillWithGlass(Inventory gui) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        plugin.getGUIManager().applyName(meta,"§7");
        filler.setItemMeta(meta);

        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, filler);
            }
        }
    }

    private Material getProfessionMaterial(String professionId) {
        return switch (professionId) {
            case "mineur" -> Material.DIAMOND_PICKAXE;
            case "commercant" -> Material.EMERALD;
            case "guerrier" -> Material.DIAMOND_SWORD;
            default -> Material.COMPASS;
        };
    }

    private Material getTalentMaterial(String talentId) {
        return switch (talentId) {
            case "exp_greed", "token_greed", "money_greed" -> Material.EXPERIENCE_BOTTLE;
            case "negotiations", "vitrines_sup", "sell_boost" -> Material.GOLD_INGOT;
            case "soldes", "garde" -> Material.SHIELD;
            case "beacon_multiplier" -> Material.BEACON;
            default -> Material.ENCHANTED_BOOK;
        };
    }
}